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
public final class RecipeCycleGameTests {
 private static Workshops.Job plan(GameTestHelper h,Item target,ItemStack... stock){return Workshops.plan(h.getLevel(),Workshops.spec("smithy",2),new SimpleContainer(stock),List.of(new Workshops.Want(Ingredient.of(target),1,UUID.randomUUID())));}
 @GameTest(template="empty",batch="recipe_cycle") public static void missingIronIsSmeltedInsteadOfRecyclingExistingIngot(GameTestHelper h){
  var job=plan(h,Items.IRON_PICKAXE,new ItemStack(Items.IRON_INGOT),new ItemStack(Items.RAW_IRON,2),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL));
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT))&&job.inputs().stream().anyMatch(i->i.matches(new ItemStack(Items.RAW_IRON))),"The next step adds missing iron from raw ore, not ingot -> nugget -> ingot");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void shortageWithoutOreDoesNotStartAnIngotNuggetLoop(GameTestHelper h){
  var job=plan(h,Items.IRON_PICKAXE,new ItemStack(Items.IRON_INGOT),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL));
  h.assertTrue(job==null,"One ingot cannot become three by reversible recipes; publish the shortage instead");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void existingNuggetsCanSupplyTheMissingIngot(GameTestHelper h){
  var job=plan(h,Items.IRON_PICKAXE,new ItemStack(Items.IRON_INGOT,2),new ItemStack(Items.IRON_NUGGET,9),new ItemStack(Items.STICK,2));
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT))&&job.inputs().stream().anyMatch(i->i.matches(new ItemStack(Items.IRON_NUGGET))),"Existing nuggets are a legitimate source of new iron");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void requestedNuggetsStillCraftFromAnIngot(GameTestHelper h){
  var job=plan(h,Items.IRON_NUGGET,new ItemStack(Items.IRON_INGOT));
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_NUGGET)),"Direct demand for nuggets still permits the conversion");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void cyclicRecipesDoNotHideTheOreShortage(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL));
  var needs=Workshops.needs(h.getLevel(),Workshops.spec("smithy",2),stock,List.of(new Workshops.Want(Ingredient.of(Items.IRON_PICKAXE),1,UUID.randomUUID())));
  h.assertTrue(needs.stream().anyMatch(in->in.matches(new ItemStack(Items.RAW_IRON))),"The published demand requests real ore instead of claiming the reversible cycle is supplied: "+needs.stream().map(in->in.ingredient().toJson()+" x"+in.count()).toList());h.succeed();
 }
}
