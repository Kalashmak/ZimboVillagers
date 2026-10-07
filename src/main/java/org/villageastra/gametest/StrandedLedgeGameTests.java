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
   public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Ledge chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(npc.onGround()&&npc.position().distanceToSqr(Vec3.atBottomCenterOf(target))<1){
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Descent preserves health");npc.discard();PhysicalFixtureChunks.release(l,held);h.succeed();
  }});
  h.runAtTickTime(1600,()->{String why="Carrier stranded: "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals();npc.discard();PhysicalFixtureChunks.release(l,held);h.assertTrue(false,why);});
 }
}
