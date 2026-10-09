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
public final class WorkshopRecyclingGameTests {
 private static List<Workshops.Want> chain(){return List.of(new Workshops.Want(Ingredient.of(Items.CHAIN),1,UUID.randomUUID()));}
 private static SimpleContainer stock(int iron){return new SimpleContainer(new ItemStack(Items.IRON_INGOT,iron),new ItemStack(Items.IRON_NUGGET),new ItemStack(Items.STICK,2),new ItemStack(Items.COAL,4));}
 @GameTest(template="empty",batch="workshop_recycling",timeoutTicks=100)
 public static void chainShortageDoesNotManufactureANewShovelToBurn(GameTestHelper h){
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock(1),chain());
  h.assertTrue(job==null,"The held chain ingot is not spent on new recycling equipment: "+job);h.succeed();
 }
 @GameTest(template="empty",batch="workshop_recycling",timeoutTicks=100)
 public static void chainPublishesOreForItsReservedIngotAndMissingNuggets(GameTestHelper h){
  var needs=Workshops.needs(h.getLevel(),Workshops.spec("town_hall"),stock(1),chain());
  h.assertTrue(needs.stream().anyMatch(in->in.matches(new ItemStack(Items.RAW_IRON))&&in.count()>0),"Gatherers see the extra raw iron, without counting the same ingot twice: "+needs);h.succeed();
 }
 @GameTest(template="empty",batch="workshop_recycling",timeoutTicks=100)
 public static void extraIngotProducesNineNuggetsBeforeThePaidChain(GameTestHelper h){
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),stock(2),chain());
  h.assertTrue(job!=null&&job.outputs().size()==1&&job.outputs().get(0).is(Items.IRON_NUGGET)&&job.outputs().get(0).getCount()==9,"An extra ingot uses the lossless nine-nugget recipe: "+job);
  h.assertTrue(job.inputs().size()==1&&job.inputs().get(0).matches(new ItemStack(Items.IRON_INGOT))&&job.inputs().get(0).count()==1&&job.fuelTicks()==0,"The unchanged real recipe costs one ingot and no smelting fuel");h.succeed();
 }
 @GameTest(template="empty",batch="workshop_recycling",timeoutTicks=100)
 public static void alreadyStockedWornShovelStillHasItsRealRecyclingRecipe(GameTestHelper h){
  var shovel=new ItemStack(Items.IRON_SHOVEL);shovel.setDamageValue(249);var c=new SimpleContainer(shovel,new ItemStack(Items.COAL,4));
  var job=Workshops.plan(h.getLevel(),Workshops.spec("town_hall"),c,List.of(new Workshops.Want(Ingredient.of(Items.IRON_NUGGET),1,UUID.randomUUID())));
  h.assertTrue(job!=null&&job.recipe().equals("minecraft:iron_nugget_from_smelting")&&job.outputs().get(0).is(Items.IRON_NUGGET)&&job.outputs().get(0).getCount()==1&&job.fuelTicks()>0,"Existing worn equipment can still be recycled with its actual cost and yield: "+job);h.succeed();
 }
}
