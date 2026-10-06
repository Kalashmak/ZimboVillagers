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
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void accumulatedNuggetsWaitForTheirMissingTorchInsteadOfBeingRecompressed(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_NUGGET,10),new ItemStack(Items.COAL),new ItemStack(Items.STICK));var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),1,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.TORCH)),"Accumulated final-product nuggets need their missing torch before reversible recompression: "+(job==null?null:job.recipe()));h.succeed();
 }
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void excessNuggetsStillCompleteTheBarsWithoutTakingTheLanternIngredient(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_INGOT,5),new ItemStack(Items.IRON_NUGGET,17),new ItemStack(Items.COAL),new ItemStack(Items.STICK));var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),1,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT))&&job.inputs().stream().anyMatch(in->in.matches(new ItemStack(Items.IRON_NUGGET))&&in.count()==9),"Seventeen nuggets can pay nine toward bars and leave eight for the lantern");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void missingTorchMaterialsDoNotLicenseReversibleWorkWithoutFinalProgress(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_NUGGET,9));var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),1,UUID.randomUUID()));
  h.assertTrue(Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants)==null,"Missing real torch supplies leave accumulated nuggets intact");h.assertTrue(stock.countItem(Items.IRON_NUGGET)==9,"Planning never reserves by consuming stock");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void ordinaryWoodConversionStillMakesAnotherRequestsMissingTool(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.BIRCH_LOG),new ItemStack(Items.COBBLESTONE,3));var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,List.of(new Workshops.Want(Ingredient.of(Items.STRIPPED_BIRCH_LOG),1,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.STONE_PICKAXE),1,UUID.randomUUID())));
  h.assertTrue(job!=null&&job.inputs().stream().anyMatch(in->in.matches(new ItemStack(Items.BIRCH_LOG)))&&job.outputs().stream().anyMatch(s->s.is(Items.BIRCH_PLANKS)||s.is(Items.STICK)),"Ordinary irreversible wood conversion may use a log held for stripping to prepare another missing tool: "+(job==null?null:job.recipe()));h.succeed();
 }
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void anExistingRealIronBlockCanStillSupplyARequestedPick(GameTestHelper h){
  var job=plan(h,Items.IRON_PICKAXE,new ItemStack(Items.IRON_BLOCK),new ItemStack(Items.STICK,2));h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT)&&s.getCount()==9),"A real block is a legitimate nine-ingot source for the requested tool");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_conversion_reserve") public static void aLowerDemandClassDoesNotHoldNuggetsAgainstHigherClassBars(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_NUGGET,10),new ItemStack(Items.COAL),new ItemStack(Items.STICK));var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID(),org.villageastra.world.LogisticsRoutes.NEED_SUPPLY),new Workshops.Want(Ingredient.of(Items.LANTERN),1,UUID.randomUUID(),org.villageastra.world.LogisticsRoutes.NEED_FOOD));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT)),"Protect only the current demand class, retaining published class priority");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void fundedLanternWinsOverUndoingItsNuggetsForUnfundedBars(GameTestHelper h){
  var stock=new net.minecraft.world.SimpleContainer(9);stock.setItem(0,new ItemStack(Items.IRON_NUGGET,9));stock.setItem(1,new ItemStack(Items.TORCH));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),2,UUID.randomUUID()));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.LANTERN)),"A funded final lantern must precede turning its nine nuggets back into one ingot for still-unfunded bars: "+(job==null?null:job.recipe()));
  h.assertTrue(stock.countItem(Items.IRON_NUGGET)==9&&stock.countItem(Items.TORCH)==1,"Planning never consumes actual stock");h.succeed();
 }
 private static Workshops.Job plan(GameTestHelper h,Item target,ItemStack... stock){return Workshops.plan(h.getLevel(),Workshops.spec("smithy",2),new SimpleContainer(stock),List.of(new Workshops.Want(Ingredient.of(target),1,UUID.randomUUID())));}
 @GameTest(template="empty",batch="recipe_cycle") public static void higherDemandClassStillPrecedesALowerFundedProduct(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.RAW_IRON,2),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_PICKAXE),1,UUID.randomUUID(),org.villageastra.world.LogisticsRoutes.NEED_SUPPLY),new Workshops.Want(Ingredient.of(Items.TORCH),4,UUID.randomUUID(),org.villageastra.world.LogisticsRoutes.NEED_FOOD));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.IRON_INGOT)),"Higher demand's real ore intermediate still precedes lower funded torches");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void aUsefulConversionIsFollowedByItsFinalProductInsteadOfBeingUndone(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.TORCH));var wants=List.of(new Workshops.Want(Ingredient.of(Items.IRON_BARS),16,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.LANTERN),2,UUID.randomUUID()));
  var first=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(first!=null&&first.outputs().stream().anyMatch(s->s.is(Items.IRON_NUGGET)),"One real ingot can fund a useful nugget conversion");
  stock.setItem(0,new ItemStack(Items.IRON_NUGGET,9));var next=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);h.assertTrue(next!=null&&next.outputs().stream().anyMatch(s->s.is(Items.LANTERN)),"Prepared converted stock funds a final lantern without reversing it");h.succeed();
 }
 @GameTest(template="empty",batch="recipe_cycle") public static void aPartiallyFundedFinalBatchPrecedesAnotherBillsIntermediate(GameTestHelper h){
  var stock=new SimpleContainer(new ItemStack(VillageAstra.FLOUR.get(),4),new ItemStack(Items.COAL),new ItemStack(Items.CLAY_BALL,4));
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.BRICKS),64,UUID.randomUUID()),new Workshops.Want(Ingredient.of(Items.BREAD),8,UUID.randomUUID()));var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock,wants);
  h.assertTrue(job!=null&&job.outputs().stream().filter(s->s.is(Items.BREAD)).mapToInt(ItemStack::getCount).sum()==2,"Two whole paid bread units precede making intermediate bricks for an unfunded larger bill");h.succeed();
 }
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
