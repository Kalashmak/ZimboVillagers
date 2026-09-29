package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BedExitGameTests {
 @GameTest(template="empty",batch="bed_exit",timeoutTicks=4000)
 public static void awakenedResidentStepsOffBedAndWalksUnderDoorLintel(GameTestHelper h){room(h,false);}
 @GameTest(template="empty",batch="bed_exit_builder",timeoutTicks=4000)
 public static void builderLeavesTowardDoorBeforeWalkingToDistantStock(GameTestHelper h){room(h,true);}
 private static void room(GameTestHelper h,boolean builder){
  int side=builder?1:-1;
  // Other movement fixtures extend beyond their empty template; keep each complete room separate.
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(6,0,6));var foot=new BlockPos(at.getX()+(builder?20480:16384),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-4)>>4;x<=(foot.getX()+4)>>4;x++)for(int z=(foot.getZ()-6)>>4;z<=(foot.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=4;x++)for(int z=-6;z<=4;z++)for(int y=-1;y<=4;y++){
   boolean wall=(Math.abs(x)==3||z==3||z==-2)&&z>=-2&&y<3;
   l.setBlock(foot.offset(x,y,z),y<0||y>=0&&wall?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  for(int x:new int[]{0,2*side})for(int z=-1;z<=0;z++)l.setBlock(foot.offset(x,0,z),Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,z==0?BedPart.HEAD:BedPart.FOOT),2);
  l.setBlock(foot.offset(-side,0,0),Blocks.OAK_PLANKS.defaultBlockState(),2);if(!builder)l.setBlock(foot.offset(-side,0,-1),Blocks.OAK_PLANKS.defaultBlockState(),2);
  l.setBlock(foot.offset(side,2,0),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true),2);
  var door=foot.offset(side,0,-2);l.setBlock(door,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);l.setBlock(door.above(),Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
  var wake=builder?foot.north():foot;var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(wake.getX()+.5,wake.getY()+.5625,wake.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof BedExitGoal)&&!(g instanceof ResidentDoorGoal));npc.targetSelector.removeAllGoals(g->true);
  var destination=foot.offset(builder?-4:-1,0,-5);npc.getNavigation().moveTo(npc.getNavigation().createPath(destination,0),.8);
  if(builder){h.assertTrue(destination.equals(npc.getNavigation().getTargetPos()),"The builder already has a distant stock route");h.assertTrue(foot.offset(1,0,-1).equals(BedExitGoal.landing(npc)),"Step toward the room door, not back toward the distant stock");}
  h.assertTrue(BedExitGoal.landing(npc)!=null,"The awake resident has a clear side step off the raised bed");
  npc.setOnGround(false);h.assertTrue(BedExitGoal.landing(npc)!=null,"A falling/jumping bed occupant cannot miss recovery merely by tick parity");npc.setOnGround(true);
  npc.startSleeping(foot);h.assertTrue(BedExitGoal.landing(npc)==null,"Sleep is not interrupted by the step-off goal");npc.stopSleeping();
  // Initial setup only: reproduce a vanilla wake-up point on the bed. No later position changes.
  npc.moveTo(wake.getX()+.5,wake.getY()+.5625,wake.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.routeTo(destination,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added to the loaded room"));
  h.onEachTick(()->{h.assertTrue(npc.tickCount<700,"Resident failed to cross the room within 700 entity ticks: "+npc.position()+" goals="+npc.runningGoals());if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination))<.5&&npc.onGround()){
   h.assertTrue(BedExitGoal.landing(npc)==null,"Ordinary ground movement does not restart bed recovery");h.assertTrue(l.getBlockState(foot).is(Blocks.WHITE_BED)&&l.getBlockState(door.above(2)).is(Blocks.STONE),"Bed and lintel remain intact");npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(3800,()->h.assertTrue(false,"Resident stuck after waking: "+npc.position()+" foot="+foot+" landing="+BedExitGoal.landing(npc)+" ticks="+npc.tickCount+" nav="+npc.getNavigation().getPath()+" goals="+npc.runningGoals()));
 }
}
