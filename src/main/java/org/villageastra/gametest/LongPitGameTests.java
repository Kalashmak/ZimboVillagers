package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LongPitGameTests {
 @GameTest(template="empty",batch="long_pit",timeoutTicks=2000)
 public static void stalledWorkerClimbsTheRimOfALongDryTrench(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-14)>>4;x<=(foot.getX()+14)>>4;x++)for(int z=(foot.getZ()-4)>>4;z<=(foot.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-14;x<=14;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=7;y++)l.setBlock(foot.offset(x,y,z),y<0||y<5&&(Math.abs(x)>=13||Math.abs(z)>=2)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var passage=foot.east(10);var ordinary=npc.getNavigation().createPath(passage,0);h.assertTrue(ordinary!=null&&ordinary.canReach(),"A long corridor still offers ordinary horizontal travel");npc.getNavigation().moveTo(ordinary,.8);
  h.assertTrue(PitEscapeGoal.escape(npc)==null,"A reachable ordinary task does not trigger climbing in a long corridor");
  var task=foot.south(3).above(5);npc.getNavigation().moveTo(task.getX()+.5,task.getY(),task.getZ()+.5,.8);var path=npc.getNavigation().getPath();
  h.assertTrue(path!=null&&!path.canReach(),"The real navigation cannot reach the workstation above this trench");
  var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim!=null&&rim.getY()==foot.getY()+5,"A stalled task may use the verified rim even though the dry floor extends beyond four blocks");
  npc.goalSelector.addGoal(1,new PitEscapeGoal(npc));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(npc.getY()>=foot.getY()+5&&npc.onGround()){
   h.assertTrue(l.getBlockState(foot.below()).is(Blocks.STONE)&&l.getBlockState(foot.south(2).above(4)).is(Blocks.STONE),"Recovery changed no terrain");npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(1800,()->h.assertTrue(false,"Worker remained in the trench: "+npc.position()+" goals="+npc.runningGoals()));
 }
}
