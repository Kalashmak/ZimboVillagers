package org.villageastra.domain;
import java.util.*;
/** AD-104: what a field grows in a day, by the vanilla 1.20.1 rule of CropBlock, so the farm card shows the residents a field really feeds.
 *  Pure arithmetic over plot cells (x,z) that all grow the same crop on hydrated farmland; no Minecraft classes, so JUnit checks it. */
public final class FarmYield {
 private FarmYield(){}
 /** Age steps wheat takes from sowing to harvest (CropBlock.MAX_AGE). */
 public static final int WHEAT_STEPS=7;
 /** Game ticks in a day and blocks in a chunk section: randomTickSpeed blocks of each section get a random tick every game tick. */
 public static final int DAY=24000,SECTION=4096;
 /** Wheat one resident eats a day; the rating (FarmField.RATING, 5) adds a quarter spare on top. */
 public static final int WHEAT_PER_RESIDENT=4;
 /** One cell (x,z) as a set key. */
 public static long key(int x,int z){return ((long)x<<32)|(z&0xFFFFFFFFL);}
 /** CropBlock.getGrowthSpeed: 1, plus 3 for its own hydrated farmland (1 when dry) and a quarter of that for each of the 8 around it;
  *  halved when the same crop grows on a diagonal, or on both an x and a z neighbour (vanilla's rows-not-squares rule). */
 public static float growth(int x,int z,Set<Long> farmland,Set<Long> hydrated,Set<Long> crop){
  float f=1f;
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){long k=key(x+dx,z+dz);float g=farmland.contains(k)?hydrated.contains(k)?3f:1f:0f;if(dx!=0||dz!=0)g/=4f;f+=g;}
  boolean alongX=crop.contains(key(x-1,z))||crop.contains(key(x+1,z)),alongZ=crop.contains(key(x,z-1))||crop.contains(key(x,z+1));
  if(alongX&&alongZ)f/=2f;
  else if(crop.contains(key(x-1,z-1))||crop.contains(key(x+1,z-1))||crop.contains(key(x+1,z+1))||crop.contains(key(x-1,z+1)))f/=2f;
  return f;
 }
 /** Chance of one random tick to age the crop: 1 in (int)(25/f)+1, as CropBlock.randomTick draws it. */
 public static double chance(float growth){return 1.0/((int)(25f/growth)+1);}
 /** Random ticks one block gets in a day at this randomTickSpeed. */
 public static double ticksPerDay(int randomTickSpeed){return (double)DAY*Math.max(0,randomTickSpeed)/SECTION;}
 /** Wheat a day from these plots (one per ripe plant) when the plots are hydrated farmland, every plot grows wheat, and extra hydrated
  *  farmland (not sown) may lie around them; any other cell is water or ground. */
 public static double wheatPerDay(Collection<int[]> plots,Set<Long> extraHydrated,int randomTickSpeed){
  var crop=new HashSet<Long>();for(var p:plots)crop.add(key(p[0],p[1]));
  var farmland=new HashSet<Long>(crop);farmland.addAll(extraHydrated);
  double steps=0;for(var p:plots)steps+=chance(growth(p[0],p[1],farmland,farmland,crop));
  return ticksPerDay(randomTickSpeed)*steps/WHEAT_STEPS;
 }
 /** AD-130: wheat-equivalent growth a day of each crop on one floor — every plot of the floor is hydrated farmland, and a plot's growth is
  *  halved only by the same crop beside it (vanilla compares the block); FoodYield turns a crop's value into its harvests. */
 public static <K> Map<K,Double> perDay(Map<K,? extends Collection<int[]>> plots,int randomTickSpeed){
  var farmland=new HashSet<Long>();for(var list:plots.values())for(var p:list)farmland.add(key(p[0],p[1]));
  var out=new LinkedHashMap<K,Double>();
  for(var en:plots.entrySet()){var crop=new HashSet<Long>();for(var p:en.getValue())crop.add(key(p[0],p[1]));double steps=0;
   for(var p:en.getValue())steps+=chance(growth(p[0],p[1],farmland,farmland,crop));out.put(en.getKey(),ticksPerDay(randomTickSpeed)*steps/WHEAT_STEPS);}
  return out;
 }
 /** Wheat a day when every plot is hydrated farmland growing wheat and no other cell is farmland. */
 public static double wheatPerDay(List<int[]> plots,int randomTickSpeed){return wheatPerDay(plots,Set.of(),randomTickSpeed);}
 /** Residents a field is rated to feed: floor(wheat / divisor), the divisor keeping a spare (FarmField.RATING). */
 public static int ratedResidents(double wheat,int divisor){return divisor<=0?0:(int)Math.floor(wheat/divisor);}
 /** Residents the wheat could feed with nothing spare: four wheat a head a day. */
 public static double maxResidents(double wheat){return wheat/WHEAT_PER_RESIDENT;}
}
