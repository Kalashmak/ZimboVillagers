package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PlantGroundGameTests {
 @GameTest(template="empty",batch="plant_ground")
 public static void gatherersPreserveGrowingPlantsButCanReuseAbandonedSoil(GameTestHelper h){
  var f=ForestFixture.create(h,1,192);
  try{
   BlockPos foot=null;
   for(int x=-4;x<=20&&foot==null;x++)for(int z=3;z<=10&&foot==null;z++){var p=f.wood(x,z);if(ForestRenewal.safe(f.l,p))foot=p;}
   h.assertTrue(foot!=null,"Unprotected planting ground");
   var ground=foot.below();f.l.setBlock(ground,Blocks.DIRT.defaultBlockState(),2);
   h.assertTrue(NaturalSupplyGoal.safe(f.l,ground),"Unused soil remains harvestable");
   for(var plant:new net.minecraft.world.level.block.Block[]{Blocks.OAK_SAPLING,Blocks.BIRCH_SAPLING,Blocks.OAK_LOG,Blocks.SUGAR_CANE,Blocks.CACTUS}){
    f.l.setBlock(foot,plant.defaultBlockState(),2);
    h.assertTrue(!NaturalSupplyGoal.safe(f.l,ground),"Do not undermine a renewable resource: "+plant);
   }
   f.l.setBlock(foot,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(NaturalSupplyGoal.safe(f.l,ground),"Removed plants do not leave permanent invisible reservations");
  }finally{f.done();}h.succeed();
 }
}
