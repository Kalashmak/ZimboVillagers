package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReversibleRouteGameTests {
 @GameTest(template="empty",batch="reversible_route",timeoutTicks=1200)
 public static void expeditionWalksAroundCliffUsingExistingRamp(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX(),90,at.getZ());
  for(int x=-2;x<=16;x++)for(int z=-2;z<=12;z++){
   int height=x<=4?3:z>=8&&z<=10&&x<=7?7-x:0;
   for(int y=0;y<=7;y++)l.setBlock(base.offset(x,y,z),y<=height?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,94,base.getZ()+2.5);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var target=base.offset(12,1,2);var shortPath=npc.routeTo(target,0);
  h.assertTrue(shortPath!=null&&shortPath.canReach()&&!HarvestAccess.reversible(shortPath),"Ordinary shortest path jumps from the three-block cliff");
  var safe=npc.routeTo(target,0,NaturalSupplyGoal.ROUTE_RANGE);
  h.assertTrue(HarvestAccess.reversible(safe),"Expedition instead finds the longer existing ramp");
  h.assertTrue(!HarvestAccess.reversible(npc.routeTo(target,0)),"Temporary expedition policy does not change ordinary navigation");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->{l.addFreshEntity(npc);npc.getNavigation().moveTo(safe,.8);});
  h.onEachTick(()->{if(npc.isAddedToWorld()&&npc.distanceToSqr(target.getCenter())<1){npc.discard();h.succeed();}});
 }
}
