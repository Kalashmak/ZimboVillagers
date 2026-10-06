package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallPackingGameTests {
 @GameTest(template="empty",batch="hall_packing",timeoutTicks=100)
 public static void depositsMergeMatchingStacksBeforeUsingAnEmptySlot(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();for(int i=1;i<c.getContainerSize();i++)c.setItem(i,new ItemStack(Items.DIRT,64));c.setItem(5,new ItemStack(Items.COBBLESTONE,32));
   h.assertTrue(LogisticsRoutes.fits(c,List.of(new ItemStack(Items.COBBLESTONE,16),new ItemStack(Items.BREAD))),"Planning preserves the empty slot for a new food type");var op=UUID.randomUUID();h.assertTrue(WorldJournal.deposit(t.l,op,LogisticsRoutes.position(t.e,t.hall()),new ItemStack(Items.COBBLESTONE,16)),"Actual journal deposit succeeds");h.assertTrue(c.getItem(0).isEmpty()&&c.getItem(5).getCount()==48,"Matching material is merged before an earlier empty slot");h.assertTrue(WorldJournal.deposit(t.l,op,LogisticsRoutes.position(t.e,t.hall()),new ItemStack(Items.COBBLESTONE,16))&&c.getItem(5).getCount()==48,"Replaying the same deposit never repeats it");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 private static OwnedChestEntity fill(ResearchV2Town.Town t){var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.expandHall();for(int i=0;i<108;i++)c.setItem(i,new ItemStack(Items.DIRT,64));c.setItem(0,new ItemStack(Items.COBBLESTONE,32));c.setItem(107,new ItemStack(Items.COBBLESTONE,32));return c;}
 @GameTest(template="empty",batch="hall_packing",timeoutTicks=100)
 public static void fullHallPacksRealStacksAndKeepsTheVacatedSlotForBread(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{var c=fill(t);h.assertTrue(HallPacking.advance(t.l,t.e,0),"A full real hall records a sort intent");h.assertTrue(c.getItem(107).getCount()==32,"Intent alone changes no inventory");HallPacking.advance(t.l,t.e,20);h.assertTrue(c.getItem(107).isEmpty()&&ItemStack.of(HallPacking.inspect(t.l,t.s.id()).getCompound("held")).getCount()==32,"The withdrawn real stack belongs to the durable communal job");HallPacking.advance(t.l,t.e,40);h.assertTrue(c.getItem(0).getCount()==64&&c.getItem(107).isEmpty()&&!HallPacking.inspect(t.l,t.s.id()).contains("held"),"Packing preserves all64 stones and frees a slot");h.assertTrue(WorldJournal.deposit(t.l,UUID.randomUUID(),LogisticsRoutes.position(t.e,t.hall()),new ItemStack(Items.BREAD,3)),"A blocked food output now has a real slot");h.assertTrue(LogisticsRoutes.count(c,s->s.is(Items.COBBLESTONE))==64,"No stone was discarded");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="hall_packing",timeoutTicks=100)
 public static void sortingRecoversJournalAheadDebitsAndCreditsWithoutDuplication(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{var c=fill(t);HallPacking.advance(t.l,t.e,0);var job=HallPacking.inspect(t.l,t.s.id());var id=job.getUUID("id");var pos=LogisticsRoutes.position(t.e,t.hall());h.assertTrue(WorldJournal.takeAmount(t.l,Settlement.childId(id,"take"),pos,107,new ItemStack(Items.COBBLESTONE,32),32).getCount()==32,"Fixture commits the physical debit before its checkpoint");HallPacking.advance(t.l,t.e,20);h.assertTrue(ItemStack.of(HallPacking.inspect(t.l,t.s.id()).getCompound("held")).getCount()==32,"Reload recovers the exact debit");h.assertTrue(WorldJournal.deposit(t.l,Settlement.childId(id,"put/0"),pos,new ItemStack(Items.COBBLESTONE,32)),"Fixture commits the credit before its checkpoint");HallPacking.advance(t.l,t.e,40);HallPacking.advance(t.l,t.e,40);h.assertTrue(LogisticsRoutes.count(c,s->s.is(Items.COBBLESTONE))==64&&HallPacking.inspect(t.l,t.s.id()).getString("stage").equals("idle"),"Reload and a second worker do not repeat either operation");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="hall_packing",timeoutTicks=100)
 public static void changedSourceIsNotTakenAndAFullChangedDestinationRetainsHeldGoods(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);try{var c=fill(t);HallPacking.advance(t.l,t.e,0);c.setItem(107,new ItemStack(Items.GRAVEL,32));HallPacking.advance(t.l,t.e,20);h.assertTrue(c.getItem(107).is(Items.GRAVEL)&&!HallPacking.inspect(t.l,t.s.id()).contains("held"),"Source compare-and-swap never takes a replacement item");c.setItem(107,new ItemStack(Items.COBBLESTONE,32));HallPacking.advance(t.l,t.e,120);HallPacking.advance(t.l,t.e,140);c.setItem(0,new ItemStack(Items.DIRT,64));c.setItem(107,new ItemStack(Items.DIRT,64));HallPacking.advance(t.l,t.e,160);h.assertTrue(ItemStack.of(HallPacking.inspect(t.l,t.s.id()).getCompound("held")).getCount()==32,"A full changed chest cannot discard the held stack");c.setItem(107,ItemStack.EMPTY);HallPacking.advance(t.l,t.e,180);h.assertTrue(c.getItem(107).is(Items.COBBLESTONE)&&c.getItem(107).getCount()==32&&!HallPacking.inspect(t.l,t.s.id()).contains("held"),"A later real slot accepts the same held goods");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
