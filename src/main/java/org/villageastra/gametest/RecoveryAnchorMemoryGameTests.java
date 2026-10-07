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
public final class RecoveryAnchorMemoryGameTests {
 @GameTest(template="empty",batch="recovery_anchor_memory",timeoutTicks=2400)
 public static void verifiedClimbCanCrossRememberedFloorToReachItsAnchor(GameTestHelper h)throws Exception{
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+126976,180,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-3,10,-4,4);
  for(int x=-3;x<=10;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=8;y++){
   boolean corridor=x>=0&&x<=4&&z==0;
   boolean solid=y==0||!corridor&&y<=4||corridor&&x<=2&&y==3;
   l.setBlock(base.offset(x,y,z),(solid?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  // Fixture begins with the same learned floor history as the observed carrier.
  var remember=Class.forName("org.villageastra.world.RecoveryLedges").getDeclaredMethod("remember",ResidentEntity.class,BlockPos.class);remember.setAccessible(true);
  remember.invoke(null,npc,base.offset(2,1,0));remember.invoke(null,npc,base.offset(3,1,0));
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new PitEscapeGoal(npc));var target=base.offset(8,5,0);
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0&&npc.onGround())npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Recovery corridor chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};
  boolean[] refreshed={false};
  h.onEachTick(()->{
   if(!refreshed[0]&&npc.onGround()&&npc.runningGoals().contains("PitEscapeGoal")&&npc.getNavigation().getPath()!=null){
    npc.getNavigation().recomputePath();var next=npc.getNavigation().getPath();
    h.assertTrue(next!=null&&next.canReach(),"Native reactive repath retains the verified recovery transit");refreshed[0]=true;
   }
   if(npc.onGround()&&npc.getY()>=base.getY()+5){h.assertTrue(refreshed[0]&&npc.getHealth()==npc.getMaxHealth(),"Verified climb after native repath preserves health");clean.run();h.succeed();}
  });
  h.runAtTickTime(2200,()->{String why="Recovery cannot reach its verified anchor: "+npc.position()+" ticks="+npc.tickCount+" goals="+npc.runningGoals();clean.run();h.assertTrue(false,why);});
 }
}
