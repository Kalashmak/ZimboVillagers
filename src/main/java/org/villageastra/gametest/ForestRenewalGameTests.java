package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestRenewalGameTests {
 @GameTest(template="empty",batch="forest_renewal",timeoutTicks=300)
 public static void exhaustedLevelOnePlantsPaidSaplingAndRecoversPlacement(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{
   f.chestBlock().clearContent();f.chestBlock().setItem(0,new ItemStack(Items.BIRCH_SAPLING,2));
   var t=new CompoundTag();t.putInt("schema",2);t.putInt("width",1);t.putInt("height",3);t.putInt("descent",0);t.putUUID("operation",UUID.randomUUID());t.putString("stage","choose");t.putString("felledKind","minecraft:birch_sapling");t.put("tool",new ItemStack(Items.IRON_AXE).save(new CompoundTag()));f.write(t);
   var goal=new ResourceWorkGoal(f.forester,true,()->6000L);h.assertTrue(goal.canUse(),"Level-I forester resumes");var prepared=f.drive(goal,350,r->r.getString("stage").equals("replant"));
   h.assertTrue(prepared.getString("stage").equals("replant"),"No planting: "+prepared+" sample="+f.wood(0,0)+" safe="+ForestRenewal.safe(f.l,f.wood(0,0))+" sky="+f.l.canSeeSky(f.wood(0,0))+" height="+f.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,f.wood(0,0).getX(),f.wood(0,0).getZ())+" route="+HarvestAccess.find(f.forester,f.wood(0,0)));
   var p=net.minecraft.core.BlockPos.of(prepared.getLong("target"));h.assertTrue(ForestRenewal.safe(f.l,p),"Planting leaves the protected lots and existing blocks alone");
   h.assertTrue(f.chestBlock().countItem(Items.BIRCH_SAPLING)==1&&ForestFixture.count(prepared.getList("cargo",Tag.TAG_COMPOUND),Items.BIRCH_SAPLING)==1,"Exactly one sapling leaves the chest");
   f.drive(goal,1,r->false);h.assertTrue(f.l.getBlockState(p).is(Blocks.BIRCH_SAPLING),"The real goal plants a sapling, not a free tree");
   f.write(prepared);goal=new ResourceWorkGoal(f.forester,true,()->6000L);h.assertTrue(goal.canUse(),"Placement before record save recovers");var after=f.drive(goal,1,r->false);
   h.assertTrue(ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.BIRCH_SAPLING)==0&&f.chestBlock().countItem(Items.BIRCH_SAPLING)==1,"Replay charges the carried sapling once without another withdrawal");
   h.assertTrue(org.villageastra.server.ForestPlantings.get(f.l.getServer()).forester(f.l,p)!=null,"Growth remains marked as village forestry");
  }finally{f.done();}h.succeed();
 }
}
