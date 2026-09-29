package org.villageastra.domain;
import java.util.*;
/** AD-104 P2: what a farm's field feeds a day, crop by crop and by the way the village eats it — the farm card's "which way the field feeds".
 *  Every food crop ages by the same chance per random tick as wheat on the same plots (CropBlock.getGrowthSpeed looks only at the soil and at
 *  the same crop around it), so FarmYield's wheat gives the harvests of any of them; only the steps to ripeness, the share of random ticks
 *  that try and what one harvest leaves once its plot is sown again differ. Residents are counted as Population.meal really eats: whole items
 *  until a meal has its rations (Rations.itemsPerMeal), so 3 beetroots (6 rations) make one meal of 5. Pure arithmetic, no Minecraft classes. */
public final class FoodYield {
 private FoodYield(){}
 /** A ripe crop's bonus drops without Fortune (vanilla loot tables, apply_bonus binomial_with_bonus_count: 3 draws at 4/7): the seeds of wheat
  *  and beetroot over their one, the carrots or potatoes over their two. */
 public static final double BONUS=3*4/7.0;
 /** The mill grinds 1 wheat into 1 flour and the bakery bakes 2 flour into 1 bread (balance/workshops.json): 2 wheat a bread. */
 public static final int CHAIN_WHEAT=2,CHAIN_BREAD=1;
 /** Random ticks the top of a sugar cane takes to grow one block (SugarCaneBlock: age 0..15, the 16th tick sets a new block on it). */
 public static final int CANE_TICKS=16;
 /** A hair up before every floor: level I's exact 31.25 wheat sums to 31.2499… in doubles, and the exact 5 residents it feeds by hand would read 4. */
 private static final double EPS=1e-6;
 /** The crops of FarmCrops, by the same names: steps to ripeness (CropBlock.MAX_AGE), the share of random ticks that try (BeetrootBlock passes
  *  2 in 3 on to CropBlock), the crop's own items a harvest leaves once the farmer has sown its plot again (a wheat or beetroot plot takes one of
  *  its seeds, a carrot or potato plot one of its carrots or potatoes; a potato's 2% poisonous potato is no food) and the item a meal eats of it. */
 public enum Crop{
  WHEAT(7,1,1,"minecraft:bread"),CARROT(7,1,1+BONUS,"minecraft:carrot"),POTATO(7,1,1+BONUS,"minecraft:potato"),BEETROOT(3,2/3.0,1,"minecraft:beetroot"),
  /** Grows a block at a time on the plots beside water (canePerDay) and is no food. */
  SUGAR_CANE(0,0,1,null);
  public final int steps;public final double share,perHarvest;
  /** Id of the item a village meal eats of this crop: bread for wheat (by hand or through the mill and bakery), none for cane. */
  public final String food;
  Crop(int steps,double share,double perHarvest,String food){this.steps=steps;this.share=share;this.perHarvest=perHarvest;this.food=food;}
 }
 /** Harvests a day of this crop on plots that ripen wheatPerDay wheat (FarmYield): each step takes the same chance of a random tick. */
 public static double harvestsPerDay(double wheatPerDay,Crop c){return c.steps==0?0:wheatPerDay*FarmYield.WHEAT_STEPS*c.share/c.steps;}
 /** The crop's own items a day, less what sows its plots again: wheat, carrots, potatoes or beetroots (cane: canePerDay). */
 public static double itemsPerDay(double wheatPerDay,Crop c){return harvestsPerDay(wheatPerDay,c)*c.perHarvest;}
 /** Plots (x,z) whose ground lies beside one of these water cells (x,z) — the only ones sugar cane grows on. */
 public static int besideWater(Collection<int[]> plots,Collection<int[]> water){
  var wet=new HashSet<Long>();for(var w:water)wet.add(FarmYield.key(w[0],w[1]));int n=0;
  for(var p:plots)if(wet.contains(FarmYield.key(p[0]-1,p[1]))||wet.contains(FarmYield.key(p[0]+1,p[1]))||wet.contains(FarmYield.key(p[0],p[1]-1))||wet.contains(FarmYield.key(p[0],p[1]+1)))n++;
  return n;
 }
 /** Sugar cane a day from this many columns, each cut back to its root whenever it has grown a block: one block per CANE_TICKS random ticks of its top. */
 public static double canePerDay(int columns,int randomTickSpeed){return Math.max(0,columns)*FarmYield.ticksPerDay(randomTickSpeed)/CANE_TICKS;}
 /** Bread a day from this wheat at wheatPer wheat for breadPer bread: the mill and bakery's 2 for 1, or bread by hand's dearer 5 for 2 (HandBread). */
 public static double bread(double wheat,int wheatPer,int breadPer){return wheatPer<=0||breadPer<=0?0:wheat*breadPer/wheatPer;}
 /** The card's spare over a bare living, as the P1 card rates: the rating's wheat a head (farm_fields.json rating_divisor, 5) over the 4 wheat
  *  the mill and bakery need to feed one (FarmYield.WHEAT_PER_RESIDENT) — a quarter. */
 public static double spare(int ratingDivisor){return (double)ratingDivisor/FarmYield.WHEAT_PER_RESIDENT;}
 /** How village meals eat: the rations of one meal (Population.MEAL_NUTRITION), meals a day (a day over Population.MEAL_INTERVAL) and the card's spare. */
 public record Meals(int nutrition,double perDay,double spare){
  public Meals{if(nutrition<1||!(perDay>0)||!(spare>=1))throw new IllegalArgumentException("Invalid meals");}
  /** What a daily supply of one food feeds, each item worth this many rations to a meal (Population.nutrition). */
  public Feeds feeds(double itemsPerDay,int ration){double fed=Rations.residentsFed(itemsPerDay,ration,nutrition,perDay);return new Feeds(fed,(int)Math.floor(fed/spare+EPS),(int)Math.floor(fed+EPS));}
 }
 /** Residents a food feeds as meals eat it; those it is rated to feed with the spare (the card's "feeds"), and the most with nothing spare ("up to"). */
 public record Feeds(double fed,int rated,int upTo){public static final Feeds NONE=new Feeds(0,0,0);}
}
