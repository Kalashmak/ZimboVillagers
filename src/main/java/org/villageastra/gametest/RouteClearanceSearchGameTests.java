package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Floating-body checks are local to one synchronous path search. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RouteClearanceSearchGameTests {
 @GameTest(template="empty",batch="route_clearance_search",timeoutTicks=100)
 public static void anotherSearchRechecksChangedWaterHeadroom(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(origin.getX()+258048,160,origin.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-2,12,-2,2);
  try{
   for(var p:BlockPos.betweenClosed(base.offset(-1,-1,-2),base.offset(10,5,2)))l.setBlock(p,Blocks.STONE.defaultBlockState(),2);
   for(int x=0;x<=9;x++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,0),y==0&&x>=3&&x<=6?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   var worker=VillageAstra.RESIDENT.get().create(l);worker.moveTo(base.getX()+1.5,base.getY(),base.getZ()+.5);worker.setOnGround(true);
   var target=base.offset(8,0,0);var open=worker.routeTo(target,0,24);
   h.assertTrue(open!=null&&open.canReach()&&ShoreEscapeGoal.clearSwimPath(worker,open),"Open water has a safe floating-body path");
   for(int x=3;x<=6;x++)l.setBlock(base.offset(x,2,0),Blocks.STONE.defaultBlockState(),2);
   var capped=worker.routeTo(target,0,24);
   h.assertTrue(capped==null||!capped.canReach(),"A subsequent search rejects new low water headroom");
   for(int x=3;x<=6;x++)l.setBlock(base.offset(x,2,0),Blocks.AIR.defaultBlockState(),2);
   var reopened=worker.routeTo(target,0,24);
   h.assertTrue(reopened!=null&&reopened.canReach()&&ShoreEscapeGoal.clearSwimPath(worker,reopened),"A later search sees restored headroom instead of a stale rejection");
  }finally{PhysicalFixtureChunks.release(l,held);}
  h.succeed();
 }
}
