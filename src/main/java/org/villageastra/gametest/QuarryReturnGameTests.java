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
public final class QuarryReturnGameTests {
 @GameTest(template="empty",batch="quarry_return",timeoutTicks=3000)
 public static void minerLeavesObservedQuarryHollowAndResumesReturnPath(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  var hold=java.util.UUID.randomUUID();RestaurantFixture.hold(l,hold,foot.offset(-6,-1,-6),foot.offset(6,11,6));
  // Solid surface heights from the actual stalled quarry, x=-2374..-2365, z=-2384..-2375.
  int[][] heights={{66,66,66,66,66,66,66,66,66,66},{66,66,66,65,64,61,62,62,61,61},{66,66,65,64,63,61,62,61,61,61},{66,65,65,64,63,60,62,62,61,61},{66,65,65,64,60,61,62,62,62,66},{66,65,65,64,64,62,63,66,66,66},{66,65,65,65,66,66,66,66,66,66},{66,65,65,66,66,66,66,66,66,66},{66,66,65,66,66,66,66,66,66,66},{66,66,65,66,66,66,66,66,66,66}};
  for(int x=-4;x<=5;x++)for(int z=-4;z<=5;z++)for(int y=-1;y<=9;y++)l.setBlock(foot.offset(x,y,z),y<=heights[z+4][x+4]-61?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  MovementTestEnclosure.seal(l,foot,6,11);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.447,foot.getY(),foot.getZ()+.617);l.addFreshEntity(npc);
  // Pit recovery yields to a safe downward leg. Keep that leg's real goal,
  // otherwise this fixture removes the only recovery which the pit query permits.
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new PitEscapeGoal(npc));npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));
  float health=npc.getHealth();
  var destination=foot.offset(-4,6,0);
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}
   public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0){var current=npc.getNavigation().getPath();if(current!=null&&current.canReach()&&!current.isDone()&&current.getTarget().equals(destination))return;var path=ResourceReturnRoute.plan(npc,destination);if(path!=null)npc.getNavigation().moveTo(path,.8);}}
   public void stop(){npc.getNavigation().stop();}
  });
  h.onEachTick(()->{if(npc.position().distanceToSqr(Vec3.atBottomCenterOf(destination))<1&&npc.onGround()){h.assertTrue(npc.getHealth()==health,"Recovery must preserve health");npc.discard();RestaurantFixture.release(l,hold);h.succeed();}});
  h.runAtTickTime(2800,()->{var observed=npc.position()+" bodyTicks="+npc.tickCount+" path="+npc.getNavigation().getPath();boolean removed=npc.isRemoved();npc.discard();RestaurantFixture.release(l,hold);h.assertTrue(removed,"The return path must leave the whole hollow, not cycle through its lower shelf: "+observed);});
 }
}
