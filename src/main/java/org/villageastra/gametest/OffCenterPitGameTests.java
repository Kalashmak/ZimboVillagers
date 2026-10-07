package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OffCenterPitGameTests {
 @GameTest(template="empty",batch="pit_off_center",timeoutTicks=2000)
 public static void bodyClearsOverhangBeforeClimbing(GameTestHelper h){
  run(h,false);
 }
 @GameTest(template="empty",batch="pit_settle",timeoutTicks=2000)
 public static void bodySettlesOnReachedLedgeWithoutWaitingForRecoveryTimeout(GameTestHelper h){
  run(h,true);
 }
 private static void run(GameTestHelper h,boolean timely){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-5)>>4;x<=(foot.getX()+5)>>4;x++)for(int z=(foot.getZ()-5)>>4;z<=(foot.getZ()+5)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  int height=timely?5:4;
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=7;y++)l.setBlock(foot.offset(x,y,z),y<0||y<height&&(x!=0||z!=0)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(foot.south(),Blocks.AIR.defaultBlockState(),2);l.setBlock(foot.south().above(),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.8);npc.setOnGround(true);
  var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim!=null&&rim.getY()==foot.getY()+height,"Clear column has a safe upper rim beside a low overhang");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new PitEscapeGoal(npc));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Waiting for entity chunk")).thenExecute(()->l.addFreshEntity(npc));
  int[] started={-1};
  h.onEachTick(()->{
   if(started[0]<0&&npc.runningGoals().contains("PitEscapeGoal"))started[0]=npc.tickCount;
   if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(rim))<.04&&npc.onGround()){
    h.assertTrue(!timely||started[0]>=0&&npc.tickCount-started[0]<100,"Reached ledge must settle without waiting for recovery timeout: "+(npc.tickCount-started[0])+" body ticks");
    npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
   }
  });
  h.runAtTickTime(1800,()->h.assertTrue(false,"Body must align below the clear column, not press into adjacent overhang: "+npc.position()+" goals="+npc.runningGoals()));
 }
}
