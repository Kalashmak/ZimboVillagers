package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.QuarryKnowledge;
/** Discovery is derived from committed harvests; no stock or terrain is replayed by rebuilding it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuarryKnowledgeGameTests {
 @GameTest(template="empty",batch="quarry_knowledge_maintenance",timeoutTicks=400)
 public static void requestedRecoveryContinuesWithoutAWorkerGoal(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,2,5)).offset(1048576,0,0);l.setBlock(p,Blocks.ANDESITE.defaultBlockState(),2);var id=UUID.randomUUID();
  WorldJournal.harvest(l,id,p,Blocks.ANDESITE.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));
  QuarryKnowledge.clear(l.getServer());QuarryKnowledge.stats(l.getServer());
  // No worker and no poll/tick calls in this fixture: the actual server event owns recovery.
  h.startSequence().thenWaitUntil(()->h.assertTrue(QuarryKnowledge.sites(l).stream().anyMatch(site->site.receipt().equals(id)),"Requested knowledge recovers while no worker searches"))
   .thenExecute(()->{h.assertTrue(l.getBlockState(p).isAir(),"Passive knowledge maintenance never changes old terrain");QuarryKnowledge.clear(l.getServer());}).thenSucceed();
 }
 @GameTest(template="empty",batch="quarry_knowledge_index",timeoutTicks=2400)
 public static void rebuildConsumesBoundedJournalWindowsWithoutReplayingHarvest(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,2,5));l.setBlock(p,Blocks.ANDESITE.defaultBlockState(),2);var id=UUID.randomUUID();
  WorldJournal.harvest(l,id,p,Blocks.ANDESITE.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));
  QuarryKnowledge.clear(l.getServer());var observed=new boolean[]{false};
  h.startSequence().thenWaitUntil(()->{
   var before=QuarryKnowledge.stats(l.getServer());QuarryKnowledge.poll(l);var after=QuarryKnowledge.stats(l.getServer());
   h.assertTrue(after.read()-before.read()<=QuarryKnowledge.READS_PER_WINDOW,"One window reads at most its bounded file allowance");
   QuarryKnowledge.tick(l.getServer());QuarryKnowledge.poll(l);h.assertTrue(QuarryKnowledge.stats(l.getServer()).read()==after.read(),"Server maintenance and worker calls share the same window allowance");
   observed[0]|=QuarryKnowledge.sites(l).stream().anyMatch(s->s.receipt().equals(id));
   h.assertTrue(observed[0],"Incremental recovery must discover the actual committed mineral receipt");
  }).thenExecute(()->{
   h.assertTrue(l.getBlockState(p).isAir(),"Knowledge rebuilding never restores or harvests the old block");
   var record=WorldJournal.recoverExisting(l,id);h.assertTrue(record.getBoolean("committed")&&record.getList("loot",Tag.TAG_COMPOUND).size()==1,"The original finite receipt remains authoritative");
   var n=QuarryKnowledge.stats(l.getServer()).sites();var incomplete=record.copy();incomplete.putUUID("id",UUID.randomUUID());incomplete.putBoolean("committed",false);incomplete.putLong("pos",p.east(40).asLong());QuarryKnowledge.remember(l.getServer(),incomplete);
   var placement=record.copy();placement.putUUID("id",UUID.randomUUID());placement.putLong("pos",p.east(80).asLong());placement.put("loot",new ListTag());QuarryKnowledge.remember(l.getServer(),placement);
   h.assertTrue(QuarryKnowledge.stats(l.getServer()).sites()==n,"Uncommitted intent and empty-loot placement teach no mineral site");
   QuarryKnowledge.clear(l.getServer());
  }).thenSucceed();
 }
}
