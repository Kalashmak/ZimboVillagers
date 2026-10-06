package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.Workshops;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopPlanningGameTests {
 private static final class CountedStock extends SimpleContainer {int reads;CountedStock(){super(108);}@Override public ItemStack getItem(int slot){reads++;return super.getItem(slot);}}
 @GameTest(template="empty",batch="workshop_planning_stock",timeoutTicks=100)
 public static void recipePlanningReadsActualStockOnceAndRefreshesAfterEveryMutation(GameTestHelper h){
  var c=new CountedStock();c.setItem(107,new ItemStack(Items.OAK_LOG));var wants=List.of(new Workshops.Want(Ingredient.of(Items.OAK_PLANKS),4,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,wants);h.assertTrue(job!=null&&c.reads==108,"One real inventory read, independent of recursive recipe checks: "+c.reads);h.assertTrue(job.inputs().size()==1&&job.inputs().get(0).matches(new ItemStack(Items.OAK_LOG)),"Recipe still requires the real input log");
  c.clearContent();c.reads=0;h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,wants)==null&&c.reads==108,"Next decision sees withdrawn materials without any stale count cache");
  c.setItem(103,new ItemStack(Items.BIRCH_LOG));c.reads=0;h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,wants)==null,"A different wood cannot counterfeit the requested oak planks");h.succeed();
 }
 @GameTest(template="empty",batch="workshop_planning_stock",timeoutTicks=100)
 public static void manyUnsatisfiedRecipesDoNotRescanEveryEmptyRealSlot(GameTestHelper h){
  var c=new CountedStock();var wants=new ArrayList<Workshops.Want>();for(var item:List.of(Items.STONE_AXE,Items.STONE_PICKAXE,Items.CHEST,Items.CRAFTING_TABLE,Items.OAK_PLANKS,Items.OAK_STAIRS,Items.OAK_FENCE,Items.TORCH,Items.LANTERN))wants.add(new Workshops.Want(Ingredient.of(item),32,UUID.randomUUID()));
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,wants)==null&&c.reads==108,"Whole unsupplied recipe graph reads108 real slots once: "+c.reads);h.succeed();
 }
}
