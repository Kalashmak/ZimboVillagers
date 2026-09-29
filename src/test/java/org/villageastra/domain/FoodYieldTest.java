package org.villageastra.domain;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-104 P2: what one level-I module feeds a day, crop by crop and by the way the village eats it, counted as meals really eat (whole items
 *  until a meal has its 5 rations; two meals a day), rated with the P1 card's quarter spare: rated = floor(fed / 1.25), up to = floor(fed). */
class FoodYieldTest {
 /** The level-I module: every cell of a 9x9 block but its centre, the water. */
 private static final List<int[]> MODULE=module();private static final List<int[]> WATER=List.of(new int[]{4,4});
 private static List<int[]> module(){var out=new ArrayList<int[]>();for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(!(x==4&&z==4))out.add(new int[]{x,z});return out;}
 /** Meals of 5 rations, two a day, and the spare of rating_divisor 5 over the 4 wheat a head. */
 private static final FoodYield.Meals MEALS=new FoodYield.Meals(5,2,FoodYield.spare(5));
 private static int ration(FoodYield.Crop c){return Rations.value(Rations.load(),c.food,0);}
 private static void feeds(FoodYield.Feeds f,double fed,int rated,int upTo,String what){assertEquals(fed,f.fed(),0.005,what);assertEquals(rated,f.rated(),what+": rated");assertEquals(upTo,f.upTo(),what+": up to");}
 @Test void wheatFeedsFiveByHandAndSixThroughTheMillAndBakery(){
  double wheat=FarmYield.wheatPerDay(MODULE,3);
  assertEquals(31.25,FoodYield.harvestsPerDay(wheat,FoodYield.Crop.WHEAT),0.02);assertEquals(31.25,FoodYield.itemsPerDay(wheat,FoodYield.Crop.WHEAT),0.02);
  // By hand 5 wheat make 2 bread: 12.5 bread feed 6.25 with nothing spare — rated 5, as the exact 5.0 it is (the doubles sum to 4.99999…).
  feeds(MEALS.feeds(FoodYield.bread(wheat,5,2),ration(FoodYield.Crop.WHEAT)),6.25,5,6,"Hand bread");
  // The mill and bakery take 2 wheat a bread: 15.625 bread feed 7.81 — the P1 card's own rating of the field, floor(wheat / 5), up to floor(wheat / 4).
  var chain=MEALS.feeds(FoodYield.bread(wheat,FoodYield.CHAIN_WHEAT,FoodYield.CHAIN_BREAD),ration(FoodYield.Crop.WHEAT));feeds(chain,7.81,6,7,"Mill and bakery");
  assertEquals(FarmYield.ratedResidents(wheat,5),chain.rated());assertEquals((int)Math.floor(FarmYield.maxResidents(wheat)),chain.upTo());
 }
 @Test void carrotsAndPotatoesFeedAsTheyAreHarvested(){
  double wheat=FarmYield.wheatPerDay(MODULE,3);
  for(var c:List.of(FoodYield.Crop.CARROT,FoodYield.Crop.POTATO)){
   // 31.25 harvests of 2..5 (mean 3.71), one sown again: 84.82 a day; a meal takes 5 of them, so they feed 8.48.
   assertEquals(31.25,FoodYield.harvestsPerDay(wheat,c),0.02,c.name());assertEquals(84.82,FoodYield.itemsPerDay(wheat,c),0.02,c.name());
   feeds(MEALS.feeds(FoodYield.itemsPerDay(wheat,c),ration(c)),8.48,6,8,c.name());
  }
 }
 @Test void aBeetrootMealTakesThree(){
  double wheat=FarmYield.wheatPerDay(MODULE,3);var c=FoodYield.Crop.BEETROOT;
  // 3 steps on 2 in 3 random ticks: 48.61 harvests of one beetroot. A meal takes 3 (6 rations), so they feed 8.10, not the 9.72 their rations would say.
  assertEquals(48.61,FoodYield.harvestsPerDay(wheat,c),0.02);assertEquals(48.61,FoodYield.itemsPerDay(wheat,c),0.02);
  feeds(MEALS.feeds(FoodYield.itemsPerDay(wheat,c),ration(c)),8.10,6,8,"Beetroot");
 }
 @Test void caneGrowsBesideTheWaterAndFeedsNobody(){
  var c=FoodYield.Crop.SUGAR_CANE;assertNull(c.food,"Cane is no meal");assertEquals(0,FoodYield.harvestsPerDay(FarmYield.wheatPerDay(MODULE,3),c));
  // Only the four plots beside the module's water hold cane; each top grows a block every 16 random ticks: 4 × 17.58 / 16 a day.
  assertEquals(4,FoodYield.besideWater(MODULE,WATER));assertEquals(0,FoodYield.besideWater(MODULE,List.of()));
  assertEquals(4.39,FoodYield.canePerDay(FoodYield.besideWater(MODULE,WATER),3),0.005);
  feeds(FoodYield.Feeds.NONE,0,0,0,"Cane");
 }
 @Test void everythingFollowsTheRandomTickSpeed(){
  for(var c:FoodYield.Crop.values()){double base=FoodYield.itemsPerDay(FarmYield.wheatPerDay(MODULE,3),c);
   assertEquals(base*2,FoodYield.itemsPerDay(FarmYield.wheatPerDay(MODULE,6),c),1e-9,c.name());assertEquals(0,FoodYield.itemsPerDay(FarmYield.wheatPerDay(MODULE,0),c),1e-9,c.name());}
  assertEquals(FoodYield.canePerDay(4,3)*2,FoodYield.canePerDay(4,6),1e-9);
  // Twice the speed: twice the bread by hand, 12.5 fed — rated 10, up to 12.
  feeds(MEALS.feeds(FoodYield.bread(FarmYield.wheatPerDay(MODULE,6),5,2),ration(FoodYield.Crop.WHEAT)),12.5,10,12,"Hand bread at speed 6");
  feeds(MEALS.feeds(FoodYield.bread(FarmYield.wheatPerDay(MODULE,0),5,2),ration(FoodYield.Crop.WHEAT)),0,0,0,"No random ticks");
 }
 @Test void mealsMakeSense(){
  assertEquals(1.25,FoodYield.spare(5),1e-12);assertEquals(0,FoodYield.bread(31.25,0,1));
  assertThrows(IllegalArgumentException.class,()->new FoodYield.Meals(0,2,1.25));assertThrows(IllegalArgumentException.class,()->new FoodYield.Meals(5,0,1.25));
  assertThrows(IllegalArgumentException.class,()->new FoodYield.Meals(5,2,0.8),"A spare below 1 would rate more residents than the food feeds");
  assertEquals(FoodYield.Feeds.NONE.rated(),MEALS.feeds(100,0).rated(),"Food worth no rations feeds nobody");
 }
 // ---- the shipped balance -----------------------------------------------------------------------------
 private static JsonObject balance(String name){try(var s=FoodYieldTest.class.getResourceAsStream("/data/villageastra/balance/"+name)){assertNotNull(s,name);return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new UncheckedIOException(e);}}
 private static JsonObject recipe(JsonObject workshops,String building,String id){for(var raw:workshops.getAsJsonObject("workshops").getAsJsonObject(building).getAsJsonArray("custom"))if(raw.getAsJsonObject().get("id").getAsString().equals(id))return raw.getAsJsonObject();return fail(building+" makes no "+id);}
 private static double per(JsonObject recipe,String in,String out){
  var i=recipe.getAsJsonArray("inputs");var o=recipe.getAsJsonArray("outputs");assertEquals(1,i.size());assertEquals(1,o.size());
  assertEquals(in,i.get(0).getAsJsonObject().get("item").getAsString());assertEquals(out,o.get(0).getAsJsonObject().get("item").getAsString());
  return i.get(0).getAsJsonObject().get("count").getAsDouble()/o.get(0).getAsJsonObject().get("count").getAsDouble();
 }
 @Test void theShippedBalanceFeedsSixByHandAndSevenWithTheChain(){
  // The card's meals come from population.json and its spare from farm_fields.json; its ways of bread from hand_bread.json and workshops.json.
  var population=balance("population.json");var meals=new FoodYield.Meals(population.get("meal_nutrition").getAsInt(),(double)FarmYield.DAY/population.get("meal_interval_ticks").getAsLong(),FoodYield.spare(balance("farm_fields.json").get("rating_divisor").getAsInt()));
  assertEquals(MEALS,meals,"Meals of 5 rations, two a day, a quarter spare");
  var workshops=balance("workshops.json");
  assertEquals((double)FoodYield.CHAIN_WHEAT/FoodYield.CHAIN_BREAD,per(recipe(workshops,"mill","flour"),"minecraft:wheat","villageastra:flour")*per(recipe(workshops,"restaurant","bread"),"villageastra:flour","minecraft:bread"),1e-12,"The chain's wheat a bread is the mill's and the restaurant kitchen's recipes (AD-139)");
  var hand=balance("hand_bread.json");int wheatPer=hand.get("wheat_per_unit").getAsInt(),breadPer=hand.get("bread_per_unit").getAsInt();
  assertTrue((double)wheatPer/breadPer>(double)FoodYield.CHAIN_WHEAT/FoodYield.CHAIN_BREAD,"Owner decision 1: bread by hand takes more wheat than the mill and bakery");
  // Owner: a level-I farm fully feeds 6 — by hand with nothing spare (5, up to 6), with a mill and bakery with a spare (6, up to 7).
  double wheat=FarmYield.wheatPerDay(MODULE,3);
  feeds(meals.feeds(FoodYield.bread(wheat,wheatPer,breadPer),ration(FoodYield.Crop.WHEAT)),6.25,5,6,"Shipped hand bread");
  feeds(meals.feeds(FoodYield.bread(wheat,FoodYield.CHAIN_WHEAT,FoodYield.CHAIN_BREAD),ration(FoodYield.Crop.WHEAT)),7.81,6,7,"Shipped mill and bakery");
  for(var c:FoodYield.Crop.values())if(c.food!=null)assertTrue(ration(c)>0,c.food+" is in the rations table");
 }
}
