package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CropLandingGameTests {
 @GameTest(template="empty",batch="crop_landing",timeoutTicks=3000)
 public static void ordinaryResidentTakesGroundRouteInsteadOfDroppingOntoFarmland(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+2359296,120,at.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-5,40,-6,18);
  for(var p:BlockPos.betweenClosed(base.offset(-5,0,-6),base.offset(40,8,18)))l.setBlock(p,p.getY()==base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(int x=0;x<20;x++)for(int z=3;z<=7;z++)l.setBlock(base.offset(x,2,z),Blocks.BIRCH_PLANKS.defaultBlockState(),2);
  var field=new ArrayList<BlockPos>();for(int x=20;x<=30;x++)for(int z=-2;z<=12;z++){var p=base.offset(x,0,z);l.setBlock(p,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);field.add(p);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.moveTo(base.getX()+.5,base.getY()+3,base.getZ()+5.5);npc.setOnGround(true);var target=base.offset(24,1,5);var path=npc.routeTo(target,0);
  h.assertTrue(path!=null&&path.canReach(),"A real alternative ground approach reaches the field");
  for(int i=1;i<path.getNodeCount();i++){
   var from=path.getNodePos(i-1);var to=path.getNodePos(i);
   h.assertTrue(to.getY()>=from.getY()||!l.getBlockState(to.below()).is(Blocks.FARMLAND),"Native path must not land a drop onto cultivated soil: from="+from+" to="+to);
  }
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Native entity chunks ready")).thenExecute(()->{l.addFreshEntity(npc);npc.getNavigation().moveTo(path,.8);});
  h.succeedWhen(()->{
   h.assertTrue(npc.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)<4,"Actual body finishes the ground detour: ticks="+npc.tickCount+" pos="+npc.position());
   for(var p:field)h.assertTrue(l.getBlockState(p).is(Blocks.FARMLAND),"Native walk preserves every prepared field soil without restoring terrain");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Ground detour preserves health");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_CROP_LANDING VERIFIED bodyTicks={} fieldSoils={} nodes={}",npc.tickCount,field.size(),path.getNodeCount());npc.discard();PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
}
