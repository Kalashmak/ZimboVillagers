package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ShallowPitGameTests {
 @GameTest(template="empty",batch="shore_safety")
 public static void gathererLeavesWaterBarriersAndFallingColumnsIntact(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(5,4,5));
  for(var block:List.of(Blocks.SAND,Blocks.GRAVEL)){
   l.setBlock(at,block.defaultBlockState(),2);l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);
   l.setBlock(at.east(),Blocks.WATER.defaultBlockState(),2);
   h.assertTrue(!NaturalSupplyGoal.safe(l,at),"Do not open a side water barrier: "+block);
   l.setBlock(at.east(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at.above(),Blocks.WATER.defaultBlockState(),2);
   h.assertTrue(!NaturalSupplyGoal.safe(l,at),"Do not mine under water: "+block);
   l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at.below(),Blocks.WATER.defaultBlockState(),2);
   h.assertTrue(NaturalSupplyGoal.safe(l,at),"Water below a dry shore block does not flow upward");
   l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
  }
  // Shallow clay has a separate dry-bank harvest policy; sand and gravel still cannot open water barriers.
  l.setBlock(at,Blocks.CLAY.defaultBlockState(),2);l.setBlock(at.above(),Blocks.WATER.defaultBlockState(),2);l.setBlock(at.above(2),Blocks.AIR.defaultBlockState(),2);
  h.assertTrue(NaturalSupplyGoal.safe(l,at),"One shallow water layer permits surveying clay; physical dry-bank access is checked separately");
  l.setBlock(at.above(2),Blocks.WATER.defaultBlockState(),2);
  h.assertTrue(!NaturalSupplyGoal.safe(l,at),"Deep underwater clay remains unavailable");
  l.setBlock(at.above(2),Blocks.AIR.defaultBlockState(),2);
  l.setBlock(at,Blocks.SAND.defaultBlockState(),2);l.setBlock(at.above(),Blocks.SAND.defaultBlockState(),2);
  h.assertTrue(!NaturalSupplyGoal.safe(l,at)&&NaturalSupplyGoal.safe(l,at.above()),"Cut a loose column from its top without causing untracked falling blocks");h.succeed();
 }
 @GameTest(template="empty",batch="shallow_pit",timeoutTicks=2000)
 public static void residentClimbsOutOfShallowFloodedPitWithoutChangingWaterOrTerrain(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(5,5,5));var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-4)>>4;x<=(foot.getX()+4)>>4;x++)for(int z=(foot.getZ()-4)>>4;z<=(foot.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=6;y++){
   boolean inner=x>=0&&x<=1&&z>=0&&z<=1;
   l.setBlock(foot.offset(x,y,z),y<0||!inner&&y<4?Blocks.STONE.defaultBlockState():inner&&y<3?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  MovementTestEnclosure.seal(l,foot,4,7);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  h.assertTrue(PitEscapeGoal.escape(npc)==null,"Deep water remains ordinary swimming, not dry-wall recovery");
  for(int x=0;x<=1;x++)for(int z=0;z<=1;z++)for(int y=1;y<=2;y++)l.setBlock(foot.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
  npc.goalSelector.removeAllGoals(g->!(g instanceof PitEscapeGoal)&&!(g instanceof FloatGoal));npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(npc.getY()>=foot.getY()+4&&npc.onGround()){
   h.assertTrue(l.getBlockState(foot).is(Blocks.WATER)&&l.getBlockState(foot.below()).is(Blocks.STONE)&&l.getBlockState(foot.west().above(3)).is(Blocks.STONE),"Escape did not drain water or alter terrain: water="+l.getBlockState(foot)+" floor="+l.getBlockState(foot.below())+" rim="+l.getBlockState(foot.west().above(3)));
   npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(1800,()->h.assertTrue(false,"Shallow pit recovery stalled at "+npc.position()+" goals="+npc.runningGoals()));
 }
}
