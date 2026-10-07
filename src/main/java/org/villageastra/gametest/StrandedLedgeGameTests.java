package org.villageastra.gametest;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** An upper destination can require leaving a roofed ledge downwards first. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StrandedLedgeGameTests {
 @GameTest(template="empty",batch="stranded_ledge",timeoutTicks=1800)
 public static void carrierDescendsRoofedLedgeThenWalksUpToDestination(GameTestHelper h){
  run(h,false);
 }
 @GameTest(template="empty",batch="ledge_cycle",timeoutTicks=2400)
 public static void recoveryDoesNotClimbBackOntoKnownDeadEnd(GameTestHelper h){
  run(h,true);
 }
 private static void run(GameTestHelper h,boolean basin){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(8,4,8));
  var held=PhysicalFixtureChunks.force(l,base,-5,15,-5,5);
  for(int x=-5;x<=15;x++)for(int z=-5;z<=5;z++)for(int y=0;y<=12;y++){
   boolean floor=y==0,ledge=x==0&&z==0&&y<=5;
   boolean roof=x>=-1&&x<=1&&z>=-1&&z<=1&&y==8;
   boolean stairs=x>=5&&x<=12&&Math.abs(z)<=1&&y<=x-4;
   boolean rim=basin&&(Math.abs(x)>=3||Math.abs(z)>=3)&&y<=3;
   l.setBlock(base.offset(x,y,z),(floor||ledge||roof||stairs||rim?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+6,base.getZ()+.5);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(0,new PitEscapeGoal(npc));npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));
  var target=base.offset(12,9,0);
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.getNavigation().createPath(target,0),.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Ledge chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(npc.onGround()&&npc.position().distanceToSqr(Vec3.atBottomCenterOf(target))<1){
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Descent preserves health");npc.discard();PhysicalFixtureChunks.release(l,held);h.succeed();
  }});
  h.runAtTickTime(1600,()->{String why="Carrier stranded: "+npc.position()+" target="+target+" base="+base+" ticks="+npc.tickCount+" goals="+npc.runningGoals()+" path="+npc.getNavigation().getPath();npc.discard();PhysicalFixtureChunks.release(l,held);h.assertTrue(false,why);});
 }
 @GameTest(template="empty",batch="shallow_dead_end",timeoutTicks=1600)
 public static void failedUpperRouteCanLeaveOnASingleDownwardStep(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+143360,150,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-4,12,-3,3);
  for(int x=-4;x<=12;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=11;y++){
   int floor=x<=2?5:Math.max(0,7-x);
   boolean solid=y<=floor||z!=0||x<0||x>8||y>=8;
   l.setBlock(base.offset(x,y,z),(solid?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+6,base.getZ()+.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));var target=base.offset(-10,10,0);
  // Retained partial routes may contain the approach node as well as their reached end.
  java.util.function.BiConsumer<Boolean,Boolean> install=(reachable,done)->{
   var a=base.offset(1,6,0);var b=base.offset(0,6,0);
   var path=new net.minecraft.world.level.pathfinder.Path(java.util.List.of(new net.minecraft.world.level.pathfinder.Node(a.getX(),a.getY(),a.getZ()),new net.minecraft.world.level.pathfinder.Node(b.getX(),b.getY(),b.getZ())),target,reachable);
   npc.getNavigation().stop();npc.getNavigation().moveTo(path,.8);
   npc.getNavigation().getPath().setNextNodeIndex(done?2:0);
  };
  install.accept(false,false);h.assertTrue(SafeDescentGoal.landing(npc,target)==null,"Unfinished partial route keeps control");
  install.accept(true,true);h.assertTrue(SafeDescentGoal.landing(npc,target)==null,"Reachable route never triggers upper-goal retreat");
  install.accept(false,true);h.assertTrue(SafeDescentGoal.landing(npc,target)!=null,"Reached two-node dead end permits a safe single step");
  npc.getNavigation().stop();
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0&&npc.onGround())npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Downward corridor chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(npc.onGround()&&npc.getY()<=base.getY()+5){h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"One-block retreat preserves health");clean.run();h.succeed();}});
  h.runAtTickTime(1400,()->{String why="Carrier ignores single downward step: "+npc.position()+" ticks="+npc.tickCount;clean.run();h.assertTrue(false,why);});
 }

}
