package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DeepRimGameTests {
 @GameTest(template="empty",batch="deep_rim",timeoutTicks=2000)
 public static void aStrandedCarrierClimbsAContinuousDeepWallWithoutChangingIt(GameTestHelper h){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+98304,160,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+4)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+4)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(cp.x,cp.z,true);held.add(cp);}l.getChunk(x,z);}
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=12;y++)l.setBlock(base.offset(x,y,z),(y==0||y<=8&&(x!=0||z!=0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  // The first reachable handhold is two blocks above the feet; the wall stays solid above it.
  for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int y=1;y<=2;y++)l.setBlock(base.relative(d).above(y),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);h.assertTrue(PitEscapeGoal.escape(npc)==null,"An ordinary short-pit goal keeps its old height limit");var goal=base.offset(3,9,0);var route=ResourceReturnRoute.plan(npc,goal);h.assertTrue(route!=null&&!route.canReach(),"The distant task has no ordinary route out");npc.getNavigation().moveTo(route,.8);h.assertTrue(PitEscapeGoal.escape(npc)!=null,"A stranded carrier can reach the first handhold and use the continuous eight-block wall");
  npc.goalSelector.removeAllGoals(g->!(g instanceof PitEscapeGoal));npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0&&npc.onGround())npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,goal),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Resident chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{h.assertTrue(npc.isAlive(),"Climb remains safe");if(npc.onGround()&&npc.getY()>=base.getY()+9){h.assertTrue(l.getBlockState(base.east().above(5)).is(Blocks.STONE)&&l.getBlockState(base.above(5)).isAir(),"Neither wall nor shaft changed");npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();}});
 }
 @GameTest(template="empty",batch="deep_rim_limits",timeoutTicks=200)
 public static void deepRecoveryRejectsBrokenWallsCeilingsAndUnboundedHeights(GameTestHelper h){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+102400,160,at.getZ());
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=0;y<=16;y++)l.setBlock(base.offset(x,y,z),(y==0||y<=8&&(x!=0||z!=0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,base.offset(2,9,0)),.8);
  for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)l.setBlock(base.relative(d).above(4),Blocks.AIR.defaultBlockState(),2);var rim=PitEscapeGoal.escape(npc);h.assertTrue(rim==null||rim.getY()<base.getY()+9,"A missing handhold cannot support a deep climb");
  for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)l.setBlock(base.relative(d).above(4),Blocks.STONE.defaultBlockState(),2);for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int y=1;y<=3;y++)l.setBlock(base.relative(d).above(y),Blocks.AIR.defaultBlockState(),2);var highGrip=PitEscapeGoal.escape(npc);h.assertTrue(highGrip==null||highGrip.getY()<base.getY()+9,"A first handhold three blocks above the feet remains unreachable");for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int y=1;y<=3;y++)l.setBlock(base.relative(d).above(y),Blocks.STONE.defaultBlockState(),2);
  l.setBlock(base.above(3),Blocks.STONE.defaultBlockState(),2);h.assertTrue(PitEscapeGoal.escape(npc)==null,"A ceiling blocks the climbing body");l.setBlock(base.above(3),Blocks.AIR.defaultBlockState(),2);
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(x!=0||z!=0)for(int y=9;y<=13;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);h.assertTrue(PitEscapeGoal.escape(npc)==null,"Deep recovery remains bounded to twelve blocks");h.succeed();
 }
}
