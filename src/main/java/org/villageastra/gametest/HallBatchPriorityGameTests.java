package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;

/** The real server clock advances a paid food job before a newly needed pick. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallBatchPriorityGameTests {
 @GameTest(template="empty",batch="mill_batch_intact",timeoutTicks=10000)
 public static void dedicatedMillRetainsItsFullPaidBatchAndOrdinaryLabor(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mill");var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();chest.setItem(0,new ItemStack(Items.WHEAT,64));
  var wants=List.of(new Workshops.Want(Ingredient.of(VillageAstra.FLOUR.get()),16,t.shop.id()));long[] began={-1},labor={0};boolean[] finished={false};
  h.onEachTick(()->{
   if(finished[0]||t.l.getGameTime()%20!=0)return;long now=t.l.getGameTime();Workshops.advance(t.l,t.e,t.shop,now,wants);var job=Workshops.inspect(t.l,t.shop.id());
   if(began[0]<0&&job.getString("stage").equals("work")){began[0]=now;labor[0]=job.getLong("needLabor");}
   if(chest.countItem(VillageAstra.FLOUR.get())>0){
    h.assertTrue(chest.countItem(VillageAstra.FLOUR.get())==16&&chest.countItem(Items.WHEAT)==48,"The dedicated mill retains its whole real sixteen-unit recipe, paying one wheat per flour");
    h.assertTrue(began[0]>=0&&labor[0]>0&&now-began[0]>=labor[0],"Its actual server ticks paid all ordinary milling labor");finished[0]=true;ResearchV2Town.done(t);h.succeed();
   }
  });
  h.runAtTickTime(9000,()->{if(!finished[0]){var job=Workshops.inspect(t.l,t.shop.id());ResearchV2Town.done(t);h.assertTrue(false,"Dedicated mill did not finish its paid batch: "+job);}});
 }
 @GameTest(template="empty",batch="hall_batch_priority",timeoutTicks=25000)
 public static void hallReconsidersUrgentToolsBeforeCommittingAnotherLongFoodBatch(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
  t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());
  var mining=MineWork.read(t.l,t.shop);mining.putString("stage","choose");mining.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.shop,mining);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();
  var own=LogisticsRoutes.chest(t.l,t.e,t.shop);if(own!=null)own.clearContent();
  chest.setItem(0,new ItemStack(Items.WHEAT,64));chest.setItem(1,new ItemStack(Items.COBBLESTONE,3));chest.setItem(2,new ItemStack(Items.STICK,2));
  var food=List.of(new Workshops.Want(Ingredient.of(VillageAstra.FLOUR.get()),16,t.hall().id()));
  long[] began={-1};boolean[] finished={false};
  h.onEachTick(()->{
   if(finished[0]||t.l.getGameTime()%20!=0)return;
   long now=t.l.getGameTime();
   Workshops.advance(t.l,t.e,t.hall(),now,began[0]<0?food:WorkerSupplies.wants(t.l,t.e));
   var job=Workshops.inspect(t.l,t.hall().id());
   if(began[0]<0&&job.getString("stage").equals("work")){
    began[0]=now;
    // Prepared phase change: a tool becomes unavailable after food was paid for.
    // The food job must finish normally, retaining all its inputs and labor.
    mining.putString("stage","tool");mining.put("tool",ItemStack.EMPTY.save(new CompoundTag()));MineWork.write(t.l,t.shop,mining);
   }
   if(chest.countItem(Items.STONE_PICKAXE)==1){
    int flour=chest.countItem(VillageAstra.FLOUR.get());
    h.assertTrue(flour>0&&flour<16,"The committed food batch finished before the urgent pick, without monopolizing the whole order");
    h.assertTrue(chest.countItem(Items.WHEAT)==64-flour&&chest.countItem(Items.COBBLESTONE)==0&&chest.countItem(Items.STICK)==0,"Every flour and the single replacement pick consumed their exact real inputs");
    h.assertTrue(now-began[0]>=3600L*flour+3600,"Food and tool keep their full ordinary labor per unit on the real server clock");
    h.assertTrue(!ToolSupplyReserve.needed(t.l,t.e),"The paid pick actually satisfies the worker's supply need");
    finished[0]=true;ResearchV2Town.done(t);h.succeed();
   }
  });
  h.runAtTickTime(24000,()->{if(!finished[0]){var job=Workshops.inspect(t.l,t.hall().id());ResearchV2Town.done(t);h.assertTrue(false,"Urgent pick is still blocked behind paid food: "+job);}});
 }
}
