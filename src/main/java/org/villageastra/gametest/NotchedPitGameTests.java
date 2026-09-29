package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NotchedPitGameTests {
 @GameTest(template="empty",batch="pit_notch",timeoutTicks=2000)
 public static void lowSideNotchDoesNotTrapResidentBelowAnOpenRim(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-5)>>4;x<=(foot.getX()+5)>>4;x++)for(int z=(foot.getZ()-5)>>4;z<=(foot.getZ()+5)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-2;y<=6;y++)l.setBlock(foot.offset(x,y,z),y<0||y<3&&(x!=0||z!=0)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(foot.north(),Blocks.AIR.defaultBlockState(),2);l.setBlock(foot.north().below(),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  l.setBlock(foot.above(2),Blocks.STONE.defaultBlockState(),2);h.assertTrue(PitEscapeGoal.escape(npc)==null,"A closed roof still prevents climbing through solid blocks");l.setBlock(foot.above(2),Blocks.AIR.defaultBlockState(),2);
  var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim!=null&&rim.getY()==foot.getY()+3,"A low side notch is not an exit and does not cancel the open rim");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new PitEscapeGoal(npc));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Waiting for entity chunk")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(rim))<.04&&npc.onGround()){
   npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(1800,()->h.assertTrue(false,"Resident did not physically leave the notched pit: "+npc.position()+" goals="+npc.runningGoals()));
 }
}
