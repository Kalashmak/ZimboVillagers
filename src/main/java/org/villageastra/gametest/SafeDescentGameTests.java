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
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SafeDescentGameTests {
 private static BlockPos loft(GameTestHelper h,int height){var l=h.getLevel();var base=h.absolutePos(new BlockPos(7,4,7));
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=height+3;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():y==height&&Math.abs(x)<=2&&Math.abs(z)<=1?Blocks.DARK_OAK_PLANKS.defaultBlockState():Blocks.AIR.defaultBlockState(),2);return base;}
 @GameTest(template="empty",batch="safe_descent",timeoutTicks=1800)
 public static void workerClimbsDownLoftAndReachesLowerJobWithoutDamage(GameTestHelper h){
  var base=loft(h,5);var l=h.getLevel();var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+6,base.getZ()+.5);l.addFreshEntity(npc);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));var target=base.offset(5,1,0);
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.onEachTick(()->{if(npc.onGround()&&npc.position().distanceToSqr(Vec3.atBottomCenterOf(target))<1){h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Controlled descent does not inflict fall damage");npc.discard();h.succeed();}});
  h.runAtTickTime(1600,()->h.assertTrue(false,"Worker did not reach its lower job: "+npc.position()+" goals="+npc.runningGoals()));
 }
 @GameTest(template="empty",batch="safe_descent_limits",timeoutTicks=100)
 public static void descentRejectsWetLandingsAndExcessiveHeight(GameTestHelper h){
  var base=loft(h,5);var l=h.getLevel();var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+6,base.getZ()+.5);npc.setOnGround(true);var target=base.offset(5,1,0);
  h.assertTrue(SafeDescentGoal.landing(npc,target)!=null,"The dry loft has a reachable descent edge");
  for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,1,z),Blocks.WATER.defaultBlockState(),2);
  h.assertTrue(SafeDescentGoal.landing(npc,target)==null,"Descent does not choose a flooded landing");
  loft(h,8);npc.moveTo(base.getX()+.5,base.getY()+9,base.getZ()+.5);npc.setOnGround(true);
  h.assertTrue(SafeDescentGoal.landing(npc,target)==null,"A drop exceeding six blocks is rejected");npc.discard();h.succeed();
 }
}
