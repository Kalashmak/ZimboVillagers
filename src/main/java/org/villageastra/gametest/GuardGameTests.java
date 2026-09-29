package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-033: real equipment from the post chest, exactly-once withdrawals, threat detection and damage with wear. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GuardGameTests {
 private record Post(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building building,Resident resident,ResidentEntity npc){}
 private static Post post(GameTestHelper h,String type,Profession role){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,0,0,0);s.addBuilding(b);
  for(int x=-6;x<12;x++)for(int z=-6;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);s.addHome(home);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());if(role.military())r.trainMilitary();s.assign(r.id(),role,b.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);npc.setNoAi(true);npc.moveTo(center.getX()+2.5,center.getY()+1,center.getZ()+2.5,0,0);l.addFreshEntity(npc);
  return new Post(l,s,e,b,r,npc);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void guardTakesOneRealSwordAndFightsNearbyMonster(GameTestHelper h){
  var p=post(h,"guard_house",Profession.GUARD);var chest=LogisticsRoutes.chest(p.l,p.e,p.building);chest.setItem(0,new ItemStack(Items.IRON_SWORD,2));
  var first=GuardGoal.equip(p.l,p.e,p.building,p.resident.id(),false);var second=GuardGoal.equip(p.l,p.e,p.building,p.resident.id(),false);
  h.assertTrue(ItemStack.of(first.getCompound("weapon")).is(Items.IRON_SWORD)&&second.getInt("takes")==1&&chest.countItem(Items.IRON_SWORD)==1,"One sword taken once from the post chest");
  var zombie=EntityType.ZOMBIE.create(p.l);zombie.moveTo(p.npc.getX()+1.5,p.npc.getY(),p.npc.getZ(),0,0);zombie.setNoAi(true);zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));p.l.addFreshEntity(zombie);float health=zombie.getHealth();
  var goal=new GuardGoal(p.npc,true);for(int i=0;i<25;i++)goal.tick();
  h.assertTrue(zombie.getHealth()<health,"Guard hits the monster: "+health+" -> "+zombie.getHealth());
  h.assertTrue(ItemStack.of(GuardGoal.inspect(p.l,p.resident.id()).getCompound("weapon")).getDamageValue()>=1,"Every hit wears the sword");
  zombie.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void archerNeedsBowAndArrowsAndSpendsThem(GameTestHelper h){
  var p=post(h,"archery",Profession.ARCHER_GUARD);var chest=LogisticsRoutes.chest(p.l,p.e,p.building);chest.setItem(0,new ItemStack(Items.BOW));
  var record=GuardGoal.equip(p.l,p.e,p.building,p.resident.id(),true);
  h.assertTrue(ItemStack.of(record.getCompound("weapon")).is(Items.BOW)&&record.getInt("arrows")==0,"Bow without arrows");
  chest.setItem(1,new ItemStack(Items.ARROW,20));record=GuardGoal.equip(p.l,p.e,p.building,p.resident.id(),true);
  h.assertTrue(record.getInt("arrows")==GuardGoal.ARROWS_TAKEN&&chest.countItem(Items.ARROW)==20-GuardGoal.ARROWS_TAKEN,"A quiver of real arrows");
  var skeleton=EntityType.SKELETON.create(p.l);skeleton.moveTo(p.npc.getX()+6.5,p.npc.getY(),p.npc.getZ(),0,0);skeleton.setNoAi(true);skeleton.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));p.l.addFreshEntity(skeleton);
  var goal=new GuardGoal(p.npc,true);for(int i=0;i<35;i++)goal.tick();
  h.assertTrue(GuardGoal.inspect(p.l,p.resident.id()).getInt("arrows")<GuardGoal.ARROWS_TAKEN,"Archer spends an arrow on the monster");
  skeleton.discard();h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void farMonstersAndPeacefulMobsAreIgnored(GameTestHelper h){
  var p=post(h,"guard_house",Profession.GUARD);
  var cow=EntityType.COW.create(p.l);cow.moveTo(p.npc.getX()+2,p.npc.getY(),p.npc.getZ(),0,0);cow.setNoAi(true);p.l.addFreshEntity(cow);
  var zombie=EntityType.ZOMBIE.create(p.l);zombie.moveTo(p.e.center().getX()+GuardGoal.DEFEND_RADIUS+10,p.npc.getY(),p.e.center().getZ(),0,0);zombie.setNoAi(true);zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));p.l.addFreshEntity(zombie);
  h.assertTrue(GuardGoal.inspect(p.l,p.resident.id()).isEmpty(),"No equipment record before duty");
  var goal=new GuardGoal(p.npc,true);for(int i=0;i<25;i++)goal.tick();
  h.assertTrue(cow.getHealth()==cow.getMaxHealth()&&zombie.getHealth()==zombie.getMaxHealth(),"Only hostile mobs near the settlement are attacked");
  cow.discard();zombie.discard();h.succeed();
 }
}
