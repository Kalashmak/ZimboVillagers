package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Prepared stock, then ordinary server ticks, exact payment and one completed crafting batch. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallCraftBatchGameTests {
 @GameTest(template="empty",batch="hall_craft_full",timeoutTicks=22000)
 public static void fourIntermediateSandstoneKeepEveryInputAndEveryLaborTick(GameTestHelper h){craft(h,16,4);}
 @GameTest(template="empty",batch="hall_craft_partial",timeoutTicks=16000)
 public static void twoAffordableSandstoneDoNotWaitForFour(GameTestHelper h){craft(h,8,2);}
 private static void craft(GameTestHelper h,int sand,int expected){
  var t=ResearchV2Town.town(h,null);var hall=t.hall();var chest=LogisticsRoutes.chest(t.l,t.e,hall);chest.clearContent();chest.setItem(0,new ItemStack(Items.SAND,sand));chest.setItem(1,new ItemStack(Items.COAL));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.SMOOTH_SANDSTONE),16,hall.id()));
  var planned=Workshops.plan(t.l,Workshops.spec("town_hall"),chest,wants);
  h.assertTrue(planned!=null&&planned.recipe().equals("minecraft:sandstone")&&planned.units()==expected,"Plan the affordable intermediate batch before smelting: "+planned);
  h.assertTrue(planned.labor()==3600L*expected&&planned.inputs().get(0).count()==4*expected,"No discount to ordinary labor or sand");
  long[] began={-1};boolean[] finished={false};UUID[] id={null};
  h.onEachTick(()->{
   if(finished[0]||t.l.getGameTime()%20!=0)return;long now=t.l.getGameTime();Workshops.advance(t.l,t.e,hall,now,wants);var job=Workshops.inspect(t.l,hall.id());
   if(job.getString("stage").equals("work")&&began[0]<0){began[0]=now;id[0]=job.getUUID("id");}
   if(chest.countItem(Items.SANDSTONE)>0&&job.getString("stage").equals("idle")){
    h.assertTrue(chest.countItem(Items.SANDSTONE)==expected&&chest.countItem(Items.SAND)==0&&chest.countItem(Items.COAL)==1,"The first batch alone delivered all paid sandstone; crafting burns no fuel");
    h.assertTrue(began[0]>=0&&now-began[0]>=3600L*expected&&job.getLong("labor")==3600L*expected,"Actual server ticks paid all original labor");
    h.assertTrue(job.getString("stage").equals("idle")&&id[0].equals(job.getUUID("id")),"The same paid job completed once");
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HALL_CRAFT VERIFIED units={} sand={} labor={} elapsed={}",expected,sand,job.getLong("labor"),now-began[0]);finished[0]=true;ResearchV2Town.done(t);h.succeed();
   }
  });
 }
 @GameTest(template="empty",batch="hall_craft_guards",timeoutTicks=100)
 public static void toolsAndDedicatedCraftingKeepTheirStackAndBatchLimits(GameTestHelper h){
  var stock=new net.minecraft.world.SimpleContainer(new ItemStack(Items.COBBLESTONE,12),new ItemStack(Items.STICK,8));
  var pick=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,List.of(new Workshops.Want(Ingredient.of(Items.STONE_PICKAXE),4,UUID.randomUUID())));
  h.assertTrue(pick!=null&&pick.units()==1&&pick.outputs().get(0).getCount()==1,"Unstackable tools remain one per paid job");
  var wood=new net.minecraft.world.SimpleContainer(new ItemStack(Items.BIRCH_LOG,16));var planks=Workshops.plan(h.getLevel(),Workshops.spec("carpentry"),wood,List.of(new Workshops.Want(Ingredient.of(Items.BIRCH_PLANKS),64,UUID.randomUUID())));
  h.assertTrue(planks!=null&&planks.units()==1,"Dedicated vanilla crafting retains its published batch");h.succeed();
 }
 @GameTest(template="empty",batch="hall_craft_conversion",timeoutTicks=100)
 public static void affordableBatchAlsoKeepsAnotherProductsCompletedIngredient(GameTestHelper h){
  var stock=new net.minecraft.world.SimpleContainer(new ItemStack(Items.IRON_NUGGET,40));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),64,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),1,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT))&&job.units()==3&&job.inputs().get(0).count()==27,"Use three affordable conversions while keeping eight lantern nuggets: "+job);h.succeed();
 }

}
