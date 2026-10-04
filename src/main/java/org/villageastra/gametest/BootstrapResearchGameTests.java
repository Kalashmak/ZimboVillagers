package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.server.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** The mayor's ordered craft training must not wait behind the work it would improve. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BootstrapResearchGameTests {
 private static void project(Town t,boolean reservedLogs){
  var p=new CompoundTag();p.putUUID("id",UUID.randomUUID());p.putString("design","home");p.put("cargo",new ListTag());
  var cost=new CompoundTag();cost.putInt("minecraft:stripped_oak_log",43);if(reservedLogs)cost.putInt("minecraft:oak_log",32);p.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),p);
  var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.OAK_LOG,32));c.setItem(1,new ItemStack(Items.OAK_PLANKS,4));c.setItem(2,new ItemStack(Items.STONE_AXE));
 }
 private static void order(Town t){var r=BookResearch.inspect(t.l,t.e);var orders=new ListTag();orders.add(StringTag.valueOf("engineering.1"));r.put("resourceOrders",orders);BookResearch.store(t.l,t.e,r);}
 private static Workshops.Job plan(Town t){return Workshops.plan(t.l,t.e,t.hall(),HallReserve.view(t.l,t.e,LogisticsRoutes.chest(t.l,t.e,t.hall())),Workshops.wants(t.l,t.e));}
 private static void cleanup(Town t){HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}
 @GameTest(template="empty",batch="bootstrap_research",timeoutTicks=200)
 public static void orderedHallCraftsArePaidBeforeRepeatingSlowBuildingWork(GameTestHelper h){
  var t=town(h,null);try{project(t,false);order(t);var job=plan(t);
   h.assertTrue(job!=null&&job.recipe().equals("minecraft:crafting_table"),"Ordered training should prepare its table before 43 slow log jobs; got "+(job==null?"none":job.recipe()));
   h.assertTrue(Workshops.wants(t.l,t.e).stream().filter(w->w.matches(new ItemStack(Items.CRAFTING_TABLE))).count()==1,"Promoted research demand is not duplicated");
   var c=LogisticsRoutes.chest(t.l,t.e,t.hall());long now=1000;
   for(int i=0;i<220&&c.countItem(Items.CRAFTING_TABLE)==0;i++)Workshops.advance(t.l,t.e,t.hall(),now+=20,Workshops.wants(t.l,t.e));
   h.assertTrue(c.countItem(Items.CRAFTING_TABLE)==1&&c.countItem(Items.OAK_PLANKS)==0&&c.countItem(Items.OAK_LOG)==32,"Table consumes four real planks and preserves the full research price");
   h.assertTrue(BookResearch.payOrders(t.l,t.e)==1&&c.countItem(Items.CRAFTING_TABLE)==0&&c.countItem(Items.OAK_LOG)==0,"Training consumes the actual table and 32 logs, once");
   // This fixture completes and reads research in one server tick; ordinary play refreshes the per-tick view next tick.
   ResearchKnobs.forget(t.s.id());
   h.assertTrue(Workshops.hallFast(t.l,t.e,new ItemStack(Items.STRIPPED_OAK_LOG)),"Only paid training unlocks the existing faster craftsmanship");
  }finally{cleanup(t);}h.succeed();
 }
 @GameTest(template="empty",batch="bootstrap_research",timeoutTicks=200)
 public static void unrequestedTrainingDoesNotDisplaceConstruction(GameTestHelper h){
  var t=town(h,null);try{project(t,false);var job=plan(t);h.assertTrue(job!=null&&job.recipe().equals("custom:strip_oak_log"),"No research order means ordinary building production");}finally{cleanup(t);}h.succeed();
 }
 @GameTest(template="empty",batch="bootstrap_research",timeoutTicks=200)
 public static void craftTrainingCannotSpendLogsReservedForTheHouse(GameTestHelper h){
  var t=town(h,null);try{project(t,true);order(t);var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.setItem(3,new ItemStack(Items.CRAFTING_TABLE));
   h.assertTrue(BookResearch.payOrders(t.l,t.e)==0&&c.countItem(Items.OAK_LOG)==32&&c.countItem(Items.CRAFTING_TABLE)==1,"No research payment can take construction's reserved logs");
  }finally{cleanup(t);}h.succeed();
 }
}
