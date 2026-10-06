package org.villageastra.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.ReedNursery;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReedRootGameTests {
 @GameTest(template="empty",batch="reed_roots",timeoutTicks=100)
 public static void aLivingCaneTopIsNotASeparatePlantingSite(GameTestHelper h){var t=ResearchV2Town.town(h,null);try{
  var root=t.e.center().offset(20,0,2);t.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);t.l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);t.l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);
  h.assertTrue(Blocks.SUGAR_CANE.defaultBlockState().canSurvive(t.l,root.above()),"Vanilla permits extending a cane column");
  h.assertTrue(!ReedNursery.safe(t.l,root.above()),"Cultivation must preserve harvest rather than replanting it on an existing root");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="reed_roots",timeoutTicks=100)
 public static void legacyUpperSegmentsDoNotCountAsIndependentRoots(GameTestHelper h){var t=ResearchV2Town.town(h,null);try{
  var root=t.e.center().offset(20,0,2);t.l.setBlock(root.below(),Blocks.SAND.defaultBlockState(),3);t.l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);t.l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);t.l.setBlock(root.above(),Blocks.SUGAR_CANE.defaultBlockState(),3);
  var old=new CompoundTag();old.putLongArray("plants",new long[]{root.asLong(),root.above().asLong()});var file=t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-reeds/"+t.s.id()+".bin");NbtRecord.write(file,old);
  h.assertTrue(ReedNursery.planted(t.l,t.s.id()).equals(java.util.List.of(root)),"A saved upper segment is not a second growing base");
  ReedNursery.record(t.l,t.s.id(),root);h.assertTrue(NbtRecord.read(file).getLongArray("plants").length==1,"Next ordinary ledger save removes the invalid entry");
  h.assertTrue(t.l.getBlockState(root).is(Blocks.SUGAR_CANE)&&t.l.getBlockState(root.above()).is(Blocks.SUGAR_CANE),"Ledger repair neither removes nor adds world blocks");
 }finally{ResearchV2Town.done(t);}h.succeed();}
}
