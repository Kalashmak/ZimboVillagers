package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-095: a guard, an archer or a soldier is a resident with military training, earned by drilling at the barracks as a child earns its
 *  schooling at the school. Nobody is handed a weapon untrained. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MilitaryTrainingGameTests {
 private record Camp(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center){}
 private static Camp camp(GameTestHelper h,boolean barracks){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/guard_house"),"guard_house",10,0,0));
  if(barracks)s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/barracks"),"barracks",20,0,0));
  for(int x=-2;x<32;x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // A builder and a porter already work, so the next opening the labor office looks at is the watch.
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,6,true);s.addHome(home);
  var hall=s.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElseThrow();
  for(var role:List.of(Profession.BUILDER,Profession.PORTER)){var w=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(w,home.id());s.assign(w.id(),role,hall.id());}
  return new Camp(l,e,s,center);
 }
 private static Resident adult(Camp c){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);c.s.admit(r,c.s.homes().iterator().next().id());return r;}
 private static void done(Camp c){SettlementData.get(c.l.getServer()).remove(c.s.id());}
 @GameTest(template="empty",timeoutTicks=200) public static void nobodyIsHandedAWeaponUntrained(GameTestHelper h){
  var c=camp(h,true);
  try{
   var r=adult(c);var post=c.s.buildings().stream().filter(b->b.type().equals("guard_house")).findFirst().orElseThrow();
   boolean refused=false;try{c.s.assign(r.id(),Profession.GUARD,post.id());}catch(IllegalStateException ex){refused=true;}
   h.assertTrue(refused&&c.s.resident(r.id()).profession()==null,"An untrained adult cannot be made a guard");
   Population.assign(c.e);
   var now=c.s.resident(r.id());
   h.assertTrue(now.profession()!=Profession.GUARD&&now.recruit(),"The labor office sends it to the drill ground instead: profession="+now.profession()+" recruit="+now.recruit());
  }finally{done(c);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aTrainedAdultTakesAMilitaryPostBeforeOtherWork(GameTestHelper h){
  var c=camp(h,true);
  try{
   // A farm without a farmer comes first for anybody else; a trained adult goes to the watch it drilled for.
   c.s.addBuilding(new Settlement.Building(Settlement.childId(c.s.id(),"building/farm"),"farm",0,0,20));
   var trained=adult(c);trained.trainMilitary();Population.assign(c.e);
   h.assertTrue(c.s.resident(trained.id()).profession()==Profession.GUARD,"The trained adult is posted to the watch: "+c.s.resident(trained.id()).profession());
   var other=adult(c);Population.assign(c.e);
   h.assertTrue(c.s.resident(other.id()).profession()==Profession.FARMER,"An untrained one takes the farm: "+c.s.resident(other.id()).profession());
  }finally{done(c);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void withoutABarracksThereIsNoOneToTrainAndNoGuard(GameTestHelper h){
  var c=camp(h,false);
  try{
   var r=adult(c);Population.assign(c.e);
   var now=c.s.resident(r.id());
   h.assertTrue(!now.recruit()&&now.profession()!=Profession.GUARD,"No barracks, no drill and no guard: "+now.profession());
  }finally{done(c);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aRecruitThatCameToAVillageWithoutABarracksTakesOrdinaryWork(GameTestHelper h){
  var c=camp(h,false);
  try{
   // A recruit that migrated here mid-drill: no drill ground, so it is discharged to the farm and keeps its drill time.
   c.s.addBuilding(new Settlement.Building(Settlement.childId(c.s.id(),"building/farm"),"farm",0,0,20));
   var r=adult(c);r.enlist();r.drill(100);h.assertTrue(r.recruit()&&!r.military(),"The fixture is an untrained recruit");
   Population.assign(c.e);
   var now=c.s.resident(r.id());
   h.assertTrue(!now.recruit()&&!now.military()&&now.profession()==Profession.FARMER,"Discharged to ordinary work: recruit="+now.recruit()+" profession="+now.profession());
   h.assertTrue(now.drillTicks()==100,"Its drill time is kept: "+now.drillTicks());
  }finally{done(c);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void drillAtTheBarracksMakesAGuard(GameTestHelper h){
  var c=camp(h,true);
  try{
   var r=adult(c);Population.assign(c.e);
   h.assertTrue(c.s.resident(r.id()).recruit(),"The adult is a recruit");
   var ground=Population.drillGround(c.e);
   var npc=VillageAstra.RESIDENT.get().create(c.l);npc.bind(c.s.id(),c.s.resident(r.id()));npc.setNoAi(true);
   npc.moveTo(ground.getX()+.5,ground.getY(),ground.getZ()+1.5,0,0);c.l.addFreshEntity(npc);
   // At night nobody drills.
   Population.drill(c.l,c.e,false);
   h.assertTrue(c.s.resident(r.id()).drillTicks()==0,"Night counts for nothing");
   // Away from the drill ground nobody drills either.
   npc.moveTo(ground.getX()+30.5,ground.getY(),ground.getZ()+.5,0,0);Population.drill(c.l,c.e,true);
   h.assertTrue(c.s.resident(r.id()).drillTicks()==0,"Only time at the drill ground counts");
   npc.moveTo(ground.getX()+.5,ground.getY(),ground.getZ()+1.5,0,0);
   for(long t=0;t<Population.DRILL_REQUIRED;t+=20)Population.drill(c.l,c.e,true);
   var trained=c.s.resident(r.id());
   h.assertTrue(trained.military()&&!trained.recruit(),"Enough days at the drill ground train it: "+trained.drillTicks());
   Population.assign(c.e);
   h.assertTrue(c.s.resident(r.id()).profession()==Profession.GUARD,"And the watch takes the trained one: "+c.s.resident(r.id()).profession());
   npc.discard();
  }finally{done(c);}
  h.succeed();
 }
}
