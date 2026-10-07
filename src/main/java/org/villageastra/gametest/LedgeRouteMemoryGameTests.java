package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Native one-way cave approach must not lead a carrier back onto its recorded dead end. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LedgeRouteMemoryGameTests {
 private static final String[] LAYERS={
  ".....####/....#####/...######/..#######/..#######/..#######/.########",
  "........./........./..###..../..####.../.#####.../.######../###...###",
  ".....####/........./........./..####.../..####.../.#####.../###...###",
  "....#####/......##./........./........./..####.../.######../###..####",
  ".....####/....#####/.......#./........./..#....../..#######/.########",
  ".....####/....#####/...######/..#####.#/..######./..#######/.########",
  "...######/...######/..#######/..#######/.########/.########/#########",
  "...######/...######/..#######/..#######/.########/.########/#########"
 };
 @GameTest(template="empty",batch="ledge_memory_route",timeoutTicks=2200)
 public static void returnPlannerAvoidsTheLedgeJustLeftByControlledDescent(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+118784,120,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-9,13,-9,12);
  for(int x=-9;x<=13;x++)for(int z=-9;z<=12;z++)for(int y=-1;y<=35;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int y=0;y<LAYERS.length;y++){var rows=LAYERS[y].split("/");for(int z=0;z<rows.length;z++)for(int x=0;x<rows[z].length();x++)l.setBlock(base.offset(x,y,z),(rows[z].charAt(x)=='.'?Blocks.AIR:Blocks.STONE).defaultBlockState(),2);}
  var foot=base.offset(2,3,3);var target=base.offset(-8,33,-8);l.setBlock(target,Blocks.AIR.defaultBlockState(),2);l.setBlock(target.above(),Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  var initial=ResourceReturnRoute.plan(npc,target);h.assertTrue(initial!=null&&!initial.canReach()&&initial.getNodeCount()==1,"Observed roofed ledge has no ordinary exit");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0&&npc.onGround())npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Ledge chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(npc.tickCount>0&&npc.onGround()&&npc.getY()<=base.getY()+1.2){
   var route=ResourceReturnRoute.plan(npc,target);boolean repeats=false;if(route!=null)for(int i=1;i<route.getNodeCount();i++)if(route.getNodePos(i).equals(foot))repeats=true;
   h.assertTrue(!repeats,"Return route must not climb back onto the unchanged isolated ledge");
   var blocked=npc.routeTo(foot,0,64);h.assertTrue(blocked==null||!blocked.canReach(),"Unchanged recorded ledge remains excluded from expeditions");
   l.setBlock(foot.above(2),Blocks.AIR.defaultBlockState(),2);
   var reopened=npc.routeTo(foot,0,64);h.assertTrue(reopened!=null&&reopened.canReach(),"Changed headroom makes the ledge eligible again");
   clean.run();h.succeed();
  }});
  h.runAtTickTime(2000,()->{String why="No physical descent: "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals();clean.run();h.assertTrue(false,why);});
 }
}
