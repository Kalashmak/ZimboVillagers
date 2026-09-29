package org.villageastra.world;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-131: the forester hut's six plans as pure data (VillageStyle "forester@N"), without the game: one 15x21 lot, the door and the stock chest in
 *  the same cells, the stations of a level kept free in every plan from it on, a growing plan, the courtyard of VI empty above its ground, and
 *  its grove the six cells of balance/forester.json. */
class ForesterHutTest {
 private static Map<VillageStyle.Cell,String> plan(int level){return VillageStyle.plan(level==1?"forester":"forester@"+level);}
 @Test void everyLevelIsAPlanOfItsOwnOnTheSameLot(){
  assertArrayEquals(new int[]{15,21},VillageStyle.lot("forester"));assertTrue(VillageStyle.has("forester@6"));assertFalse(VillageStyle.has("forester@7"));assertFalse(VillageStyle.has("home@2"));
  assertEquals(org.villageastra.domain.OrganicLots.FORESTER_WIDTH,15);assertEquals(org.villageastra.domain.OrganicLots.FORESTER_DEPTH,21);
  for(int level=1;level<=6;level++){var m=plan(level);
   for(var c:m.keySet())assertTrue(c.x()>=0&&c.x()<15&&c.z()>=0&&c.z()<21,"level "+level+" in its lot: "+c);
   assertTrue(m.get(new VillageStyle.Cell(4,1,0)).startsWith("oak_door")&&m.get(new VillageStyle.Cell(4,2,0)).startsWith("oak_door"),"level "+level+": the door at (4,1,0)");
   assertEquals(VillageStyle.CHEST,m.get(new VillageStyle.Cell(1,1,4)),"level "+level+": the stock chest");
   assertEquals("air",m.get(new VillageStyle.Cell(5,1,3)),"level "+level+": the core cell is kept");
   for(var k:VillageStyle.FORESTER_KIT)if(k[0]<=level)assertEquals("air",m.get(new VillageStyle.Cell(k[1],k[2],k[3])),"level "+level+": station of "+k[0]+" at "+Arrays.toString(k)+" is kept free");
  }
 }
 @Test void theHutGrowsLevelByLevel(){
  int last=0;
  for(int level=1;level<=6;level++){var built=new HashSet<Long>();int blocks=0;
   for(var e:plan(level).entrySet())if(!e.getValue().equals("air")&&e.getKey().y()>=1){built.add(((long)e.getKey().x()<<32)|e.getKey().z());blocks++;}
   assertTrue(blocks>last,"level "+level+" has more to it than the one before: "+blocks+" > "+last);last=blocks;}
 }
 @Test void theCourtyardOfSixHoldsNothingAboveItsGround(){
  var six=plan(6);
  for(var c:six.keySet())if(c.y()>=1)assertFalse(VillageStyle.forestCourtyard(c.x(),c.z()),"no cell over the courtyard, air neither: "+c);
  for(var g:VillageStyle.FOREST_GROVE){assertTrue(VillageStyle.forestCourtyard(g[0],g[1]));assertEquals("dirt",six.get(new VillageStyle.Cell(g[0],0,g[1])),"grove cell on dirt");}
  for(int level=1;level<=5;level++)assertTrue(plan(level).keySet().stream().anyMatch(c->c.y()>=1&&VillageStyle.forestCourtyard(c.x(),c.z())),"below VI the yard is cleared like any lot (air cells)");
  var grove=ForestBalance.GROVE;assertEquals(6,grove.size());for(int i=0;i<6;i++)assertArrayEquals(VillageStyle.FOREST_GROVE[i],grove.get(i),"balance/forester.json names the plan's grove cells");
 }
 @Test void noDeepslateInThePlansOfTheFirstLevels(){
  for(int level=1;level<=3;level++)for(var s:plan(level).values())assertFalse(s.contains("deepslate"),"level "+level+": "+s);
 }
 @Test void theBalanceIsTheSpec(){
  assertArrayEquals(new int[]{32,32,40,40,48,48},java.util.stream.IntStream.rangeClosed(1,6).map(ForestBalance::radius).toArray());
  assertArrayEquals(new int[]{1,1,1,1,3,3},java.util.stream.IntStream.rangeClosed(1,6).map(ForestBalance::treesPerTrip).toArray());
  assertArrayEquals(new int[]{0,0,0,40,30,20},java.util.stream.IntStream.rangeClosed(1,6).map(ForestBalance::sawPeriod).toArray());
  assertEquals(6,ForestBalance.PLANKS_PER_LOG);assertEquals(8,ForestBalance.LOG_KEEP);assertEquals(128,ForestBalance.PLANK_TARGET);assertEquals(800,ForestBalance.GROW_TICKS);assertEquals(64,ForestBalance.MAX_LOGS);
 }
}
