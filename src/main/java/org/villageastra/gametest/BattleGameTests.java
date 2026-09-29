package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-065: an army in the field and the defenders of its target fight each other for real; an army with no soldier left withdraws. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BattleGameTests {
 private record Field(net.minecraft.server.level.ServerLevel l,SettlementData.Entry attacker,SettlementData.Entry target,ResidentEntity soldier,ResidentEntity guard,ResidentEntity farmer,CompoundTag army){}
 private static SettlementData.Entry village(GameTestHelper h,BlockPos at,String... buildings){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  int dx=0;for(var type:buildings){s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,dx,0,0));dx+=8;}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);SettlementData.get(l.getServer()).add(e);return e;
 }
 private static ResidentEntity person(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Profession profession,String building,BlockPos at){
  var s=e.settlement();var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,s.homes().iterator().next().id());
  // AD-095: a guard or a soldier of a fixture has been through the drill ground.
  if(profession!=null&&profession.military())r.trainMilitary();
  if(profession!=null){var b=s.buildings().stream().filter(x->x.type().equals(building)).findFirst().orElseThrow();s.assign(r.id(),profession,b.id());}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);npc.setNoAi(true);l.addFreshEntity(npc);return npc;
 }
 private static Field field(GameTestHelper h,String state){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(6,3,6));
  for(int x=-4;x<30;x++)for(int z=-4;z<20;z++){l.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<3;y++)l.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var attacker=village(h,base,"town_hall","barracks");var target=village(h,base.offset(20,0,0),"town_hall","guard_house");
  var soldier=person(l,attacker,Profession.SOLDIER,"barracks",base.offset(14,0,10));
  var guard=person(l,target,Profession.GUARD,"guard_house",base.offset(17,0,10));
  var farmer=person(l,target,null,null,base.offset(15,0,12));
  var army=new CompoundTag();army.putInt("schema",1);army.putUUID("id",UUID.randomUUID());army.putUUID("attacker",attacker.settlement().id());army.putUUID("target",target.settlement().id());
  army.putString("dimension",attacker.dimension());army.put("stamps",new CompoundTag());army.put("supply",new CompoundTag());
  var list=new ListTag();list.add(NbtUtils.createUUID(soldier.getUUID()));army.put("soldiers",list);army.putString("state",state);
  Sieges.save(l.getServer(),army);
  return new Field(l,attacker,target,soldier,guard,farmer,army);
 }
 private static void done(Field f){
  f.army.putString("state",Sieges.CLOSED);Sieges.save(f.l.getServer(),f.army);
  f.soldier.discard();f.guard.discard();f.farmer.discard();
  SettlementData.get(f.l.getServer()).remove(f.attacker.settlement().id());SettlementData.get(f.l.getServer()).remove(f.target.settlement().id());
 }
 @GameTest(template="empty",timeoutTicks=100) public static void anArmyAndTheDefendersOfItsTargetFight(GameTestHelper h){
  var f=field(h,Sieges.MARCHING);
  try{
   if(!Battle.allowed(f.l)){h.assertTrue(Battle.foe(f.l,f.army,f.soldier)==null,"On peaceful difficulty nobody fights");h.succeed();return;}
   h.assertTrue(f.guard.equals(Battle.foe(f.l,Battle.armyOf(f.l,f.soldier.getUUID()),f.soldier)),"The soldier goes for the guard, not the farmer beside him");
   h.assertTrue(f.soldier.equals(Battle.invader(f.l,f.target,f.guard,GuardGoal.SIGHT)),"The guard sees the soldier of the army marching on his village");
   h.assertTrue(Battle.invader(f.l,f.attacker,f.soldier,GuardGoal.SIGHT)==null,"A soldier is nobody's invader at home");
   h.assertTrue(!f.guard.panicsWhenHurt()&&!f.soldier.panicsWhenHurt()&&f.farmer.panicsWhenHurt(),"A struck guard or soldier stands and fights; a farmer runs");
   float before=f.guard.getHealth();
   h.assertTrue(Battle.strike(f.l,f.soldier,f.guard,ItemStack.EMPTY)&&f.guard.getHealth()<before,"A bare-handed blow really hurts");
   float guardBefore=f.soldier.getHealth();
   h.assertTrue(Battle.strike(f.l,f.guard,f.soldier,new ItemStack(Items.IRON_SWORD))&&guardBefore-f.soldier.getHealth()>before-f.guard.getHealth()-.01F,"A sword strikes harder than a fist");
  }finally{done(f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aSoldierInTheFieldWalksUpToTheGuardAndStrikes(GameTestHelper h){
  var f=field(h,Sieges.MARCHING);
  if(!Battle.allowed(f.l)){done(f);h.succeed();return;}
  // The soldier's own campaign routine does the fighting: it walks over and strikes, no test hand moves it.
  f.soldier.setNoAi(false);f.soldier.moveTo(f.guard.getX()-8,f.guard.getY(),f.guard.getZ(),0,0);
  var goal=new SoldierGoal(f.soldier,true);float full=f.guard.getHealth();
  h.onEachTick(goal::tick);
  h.succeedWhen(()->{
   h.assertTrue(f.guard.getHealth()<full,"The guard has been struck: "+f.guard.getHealth()+" at distance "+f.soldier.distanceTo(f.guard));
   h.assertTrue(f.soldier.workStatus().equals("soldier_fighting"),"The soldier is fighting: "+f.soldier.workStatus());
   done(f);
  });
 }
 @GameTest(template="empty",timeoutTicks=100) public static void anArmyWithNoSoldierLeftWithdraws(GameTestHelper h){
  var f=field(h,Sieges.MARCHING);
  try{
   h.assertTrue(Battle.living(f.l,f.army)==1,"The army has its soldier");
   f.attacker.settlement().resident(f.soldier.getUUID()).die();
   h.assertTrue(Battle.living(f.l,f.army)==0,"The fallen soldier is not counted");
   h.assertTrue(Sieges.tick(f.l,f.army,1000).equals(Sieges.WITHDRAWN)&&f.army.getString("reason").equals("no_soldiers"),"With no soldier left the army withdraws instead of hanging on");
   h.assertTrue(Battle.armyOf(f.l,f.soldier.getUUID())==null,"A withdrawn army is out of the field");
  }finally{done(f);}
  h.succeed();
 }
}
