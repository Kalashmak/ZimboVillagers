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
/** Terrain beside a raised mine is lower than its lot but is not inside its shaft. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineApproachGameTests {
 @GameTest(template="empty",batch="mine_approach",timeoutTicks=200) public static void lowerGroundBesideTheMineUsesNormalNavigation(GameTestHelper h){
  for(int turn=0;turn<4;turn++)for(int x:new int[]{-1,7})check(h,turn,x,false);h.succeed();
 }
 @GameTest(template="empty",batch="mine_approach",timeoutTicks=200) public static void theActualShaftKeepsItsStepWalker(GameTestHelper h){
  for(int turn=0;turn<4;turn++)check(h,turn,3,true);h.succeed();
 }
 @GameTest(template="empty",batch="mine_approach",timeoutTicks=100)
 public static void deepReturnWaypointsRespectRotationAndAboveGroundJobs(GameTestHelper h){
  var l=h.getLevel();for(int turn=0;turn<4;turn++){
   var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(6,20,6)));var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0,turn);
   var npc=VillageAstra.RESIDENT.get().create(l);var state=new net.minecraft.nbt.CompoundTag();state.putInt("descent",7);state.putInt("step",26);state.putInt("floorStep",25);
   var destination=BuildingPlacement.at(e,mine,20,-30,32);var surface=BuildingPlacement.at(e,mine,20,1,32);npc.moveTo(surface.getX()+.5,surface.getY(),surface.getZ()+.5);
   var entry=BuildingPlacement.at(e,mine,2,1,1);h.assertTrue(MineApproach.waypoint(npc,e,mine,state,destination).equals(entry),"Surface return uses the entrance: rotation "+turn);
   h.assertTrue(MineApproach.waypoint(npc,e,mine,state,BuildingPlacement.at(e,mine,27,-7,7)).equals(entry),"The shallowest gallery still uses the entrance: rotation "+turn);
   var chest=BuildingPlacement.at(e,mine,1,1,4);h.assertTrue(MineApproach.waypoint(npc,e,mine,state,chest).equals(chest),"A surface delivery is not sent underground");
   npc.moveTo(entry.getX()+.5,entry.getY(),entry.getZ()+.5);h.assertTrue(MineApproach.waypoint(npc,e,mine,state,destination).equals(BuildingPlacement.at(e,mine,3,-5,6)),"Entrance leads to the first landing");
   var stair=BuildingPlacement.at(e,mine,3,-12,12);npc.moveTo(stair.getX()+.5,stair.getY(),stair.getZ()+.5);h.assertTrue(MineApproach.waypoint(npc,e,mine,state,destination).equals(BuildingPlacement.at(e,mine,3,-15,16)),"The next search spans only four completed rows");npc.discard();
  }h.succeed();
 }
 private static void check(GameTestHelper h,int turn,int x,boolean shaft){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(5,20,5)));SettlementData.get(l.getServer()).add(e);
  var mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();s.moveBuilding(mine.id(),mine.x(),mine.y(),mine.z(),turn);
  mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();
  var r=s.residents().stream().filter(v->v.profession()==Profession.MINER).findFirst().orElseThrow();var npc=VillageAstra.RESIDENT.get().create(l);
  try{
   npc.bind(s.id(),r);npc.setNoAi(true);var foot=BuildingPlacement.at(e,mine,x,-1,3);l.setBlock(foot.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(foot,Blocks.AIR.defaultBlockState(),3);l.setBlock(foot.above(),Blocks.AIR.defaultBlockState(),3);
   npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5,0,0);npc.setOnGround(true);l.addFreshEntity(npc);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Miner accepts its ordinary work");goal.tick();
   boolean hand=npc.workStatus().contains("the_shaft");h.assertTrue(hand==shaft,"turn="+turn+" localX="+x+" shaft="+shaft+" actual="+npc.workStatus());goal.stop();
  }finally{npc.discard();SettlementData.get(l.getServer()).remove(s.id());}
 }
}
