package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-060: residents sleep at night in their own beds while the guard keeps watch, and two residents meeting in a doorway both get through. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResidentNightGameTests {
 private record House(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building home,BlockPos base){}
 private static House house(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home-night"),"home",12,0,0);s.addBuilding(home);
  s.addHome(new Settlement.Home(home.id(),1,2,true));
  var base=center.offset(12,0,0);
  for(var cell:BuildingBlueprints.layout("home",base).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new House(l,e,home,base);
 }
 private static ResidentEntity resident(House t,Profession profession,BlockPos at){
  var s=t.e.settlement();var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,t.home.id());
  if(profession!=null&&profession.military())r.trainMilitary();
  if(profession!=null)s.assign(r.id(),profession,profession==Profession.GUARD?"guard_house":profession.workplace(),0);
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);t.l.addFreshEntity(npc);return npc;
 }
 @GameTest(template="empty",timeoutTicks=200) public static void residentsSleepInTheirOwnBedsAndTheGuardKeepsWatch(GameTestHelper h){
  var t=house(h);long[] time={18000};
  var farmer=resident(t,null,t.base.offset(2,1,5));
  var bed=SleepGoal.bed(t.l,t.e,t.e.settlement().resident(farmer.getUUID()));
  h.assertTrue(bed!=null&&t.l.getBlockState(bed).getBlock() instanceof BedBlock,"The resident has a real bed in their home: "+bed);
  var goal=new SleepGoal(farmer,true,()->time[0]);
  h.assertTrue(goal.canUse(),"At night the resident goes to bed");
  goal.start();goal.tick();
  h.assertTrue(farmer.isSleeping()&&t.l.getBlockState(bed).getValue(BedBlock.OCCUPIED),"Beside the bed the resident lies down in it");
  time[0]=1000;
  h.assertTrue(!goal.canContinueToUse(),"At dawn the night is over");
  goal.stop();
  h.assertTrue(!farmer.isSleeping()&&!t.l.getBlockState(bed).getValue(BedBlock.OCCUPIED),"The resident gets up and the bed is free");
  time[0]=1000;h.assertTrue(!new SleepGoal(farmer,true,()->time[0]).canUse(),"Nobody goes to bed by day");
  // The watch does not sleep.
  var guardRecord=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,Profession.GUARD,null,-1);
  h.assertTrue(SleepGoal.keepsWatch(guardRecord)&&!SleepGoal.keepsWatch(t.e.settlement().resident(farmer.getUUID())),"Guards keep watch, farmers sleep");
  h.assertTrue(SleepGoal.night(13000)&&SleepGoal.night(23000)&&!SleepGoal.night(12000)&&!SleepGoal.night(23500),"Night is dusk to dawn");
  farmer.discard();SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=600) public static void twoResidentsMeetingInADoorwayBothGetThrough(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,1,4));
  // An open floor split by a wall with one door in it.
  for(int x=0;x<13;x++)for(int z=0;z<11;z++){l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  for(int x=0;x<13;x++)for(int y=0;y<3;y++)l.setBlock(origin.offset(x,y,5),Blocks.STONE_BRICKS.defaultBlockState(),2);
  var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
  l.setBlock(origin.offset(6,0,5),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);l.setBlock(origin.offset(6,1,5),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
  var north=VillageAstra.RESIDENT.get().create(l);north.moveTo(origin.getX()+6.5,origin.getY(),origin.getZ()+1.5,0,0);l.addFreshEntity(north);
  var south=VillageAstra.RESIDENT.get().create(l);south.moveTo(origin.getX()+6.5,origin.getY(),origin.getZ()+9.5,180,0);l.addFreshEntity(south);
  var goalNorth=origin.offset(6,0,9);var goalSouth=origin.offset(6,0,1);boolean[] yielded={false};
  h.onEachTick(()->{
   if(north.doorway().waiting()||south.doorway().waiting())yielded[0]=true;
   if(h.getTick()%10!=0)return;
   // Each keeps heading for the far side, as a working resident would; the one making way is left to its own goal.
   if(!north.doorway().waiting()&&north.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(goalNorth))>1)north.getNavigation().moveTo(goalNorth.getX()+.5,goalNorth.getY(),goalNorth.getZ()+.5,.8);
   if(!south.doorway().waiting()&&south.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(goalSouth))>1)south.getNavigation().moveTo(goalSouth.getX()+.5,goalSouth.getY(),goalSouth.getZ()+.5,.8);
  });
  h.succeedWhen(()->{
   h.assertTrue(north.getZ()>origin.getZ()+7&&south.getZ()<origin.getZ()+3,"Both are through the door: north z="+(north.getZ()-origin.getZ())+" south z="+(south.getZ()-origin.getZ()));
   north.discard();south.discard();
  });
 }
 @GameTest(template="empty",timeoutTicks=100) public static void residentsDoNotShoveEachOther(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(3,1,3));
  for(int x=-2;x<3;x++)for(int z=-2;z<3;z++){l.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<3;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var a=VillageAstra.RESIDENT.get().create(l);a.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);a.setNoAi(true);l.addFreshEntity(a);
  var b=VillageAstra.RESIDENT.get().create(l);b.moveTo(at.getX()+.6,at.getY(),at.getZ()+.5,0,0);b.setNoAi(true);l.addFreshEntity(b);
  h.runAfterDelay(40,()->{
   // Standing in the same doorway spot they stay where they are instead of shoving each other into the frame.
   h.assertTrue(a.distanceTo(b)<.4F,"Two residents in one spot do not push each other apart: "+a.distanceTo(b));
   a.discard();b.discard();h.succeed();
  });
 }
}
