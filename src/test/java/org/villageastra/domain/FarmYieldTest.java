package org.villageastra.domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-104: a field of 9x9 modules, each watered at its centre, feeds what the owner's targets ask, by vanilla's crop growth rule. */
class FarmYieldTest {
 /** Plots of modules tiled along z: every cell of a 9 x 9*count block but each module's centre. */
 private static List<int[]> modules(int count){var out=new ArrayList<int[]>();for(int x=0;x<9;x++)for(int z=0;z<9*count;z++)if(!(x==4&&z%9==4))out.add(new int[]{x,z});return out;}
 private static Map<Integer,Integer> odds(List<int[]> plots){
  var crop=new HashSet<Long>();for(var p:plots)crop.add(FarmYield.key(p[0],p[1]));
  var out=new TreeMap<Integer,Integer>();for(var p:plots)out.merge((int)Math.round(1/FarmYield.chance(FarmYield.growth(p[0],p[1],crop,crop,crop))),1,Integer::sum);return out;
 }
 @Test void oneModuleFeedsTheFirstLevel(){
  var plots=modules(1);assertEquals(80,plots.size());
  assertEquals(Map.of(6,48,7,28,9,4),odds(plots),"Inner plots and those by the water grow at 1/6, edges at 1/7, corners at 1/9");
  double wheat=FarmYield.wheatPerDay(plots,3);
  assertEquals(31.25,wheat,0.02);assertEquals(6,FarmYield.ratedResidents(wheat,5));assertEquals(7,(int)Math.floor(FarmYield.maxResidents(wheat)));
 }
 @Test void twoModulesFeedTheSecondLevel(){
  var plots=modules(2);assertEquals(160,plots.size());
  double wheat=FarmYield.wheatPerDay(plots,3);
  assertEquals(63.66,wheat,0.05);assertEquals(12,FarmYield.ratedResidents(wheat,5));
 }
 @Test void yieldFollowsTheRandomTickSpeed(){
  var plots=modules(1);double base=FarmYield.wheatPerDay(plots,3);
  assertEquals(base*2,FarmYield.wheatPerDay(plots,6),1e-9);assertEquals(base/3,FarmYield.wheatPerDay(plots,1),1e-9);assertEquals(0,FarmYield.wheatPerDay(plots,0),1e-9);
 }
 @Test void theOldFieldFedHalfAsMany(){
  // Before AD-104: x 0..6 but the water at x 3 (z 10..14), z 9..15, with farmland under x 3 at z 9 and 15.
  var plots=new ArrayList<int[]>();for(int x=0;x<=6;x++)for(int z=9;z<=15;z++)if(x!=3)plots.add(new int[]{x,z});
  double wheat=FarmYield.wheatPerDay(plots,Set.of(FarmYield.key(3,9),FarmYield.key(3,15)),3);
  assertEquals(15.41,wheat,0.02);assertEquals(3,FarmYield.ratedResidents(wheat,5));
 }
 /** AD-130: plots of a grid of cols x rows modules (the 2x2 of level II, the 2x3 of III and of each barn floor). */
 static List<int[]> grid(int cols,int rows){var out=new ArrayList<int[]>();for(int x=0;x<9*cols;x++)for(int z=0;z<9*rows;z++)if(!(x%9==4&&z%9==4))out.add(new int[]{x,z});return out;}
 @Test void theNewFieldsFeedTheirLevels(){
  assertEquals(129.54,FarmYield.wheatPerDay(grid(2,2),3),0.05,"II: a 2x2");assertEquals(25,FarmYield.ratedResidents(FarmYield.wheatPerDay(grid(2,2),3),5));
  double floor=FarmYield.wheatPerDay(grid(2,3),3);assertEquals(195.43,floor,0.05,"III: a 2x3");assertEquals(39,FarmYield.ratedResidents(floor,5));
  // A deck parts the floors: each grows on its own, so three floors grow three times one.
  assertEquals(586.3,3*floor,0.1,"V: three floors");assertEquals(117,FarmYield.ratedResidents(3*floor,5));assertEquals(78,FarmYield.ratedResidents(2*floor,5));
  assertEquals(193.28,FarmYield.wheatPerDay(grid(1,6),3),0.05,"The 1x6 the owner did not take");
 }
 @Test void aMixedFieldHalvesOnlyBesideTheSameCrop(){
  // Vanilla halves a plot's growth for the same crop on both axes or a diagonal. Two crops side by side change nothing at their seam (each
  // plot there still has its own crop along both axes); rows of two crops in turn are never halved, so they outgrow one crop.
  var all=grid(2,1);var left=new ArrayList<int[]>();var right=new ArrayList<int[]>();for(var p:all)(p[0]<9?left:right).add(p);
  double one=FarmYield.perDay(Map.of("wheat",all),3).get("wheat");
  assertEquals(one,FarmYield.wheatPerDay(all,3),1e-9,"One crop over both fields is the plain wheat rule");
  var halves=FarmYield.perDay(Map.of("wheat",left,"carrot",right),3);
  assertEquals(one,halves.get("wheat")+halves.get("carrot"),1e-6,"Two crops side by side grow as one");
  var even=new ArrayList<int[]>();var odd=new ArrayList<int[]>();for(var p:all)(p[1]%2==0?even:odd).add(p);
  var rows=FarmYield.perDay(Map.of("wheat",even,"carrot",odd),3);
  assertTrue(rows.get("wheat")+rows.get("carrot")>one*1.5,"Rows of two crops are not halved: "+rows+" vs "+one);
 }
 @Test void aLoneDryPlotGrowsSlowest(){
  // Own dry farmland: f=2, no neighbours, no halving → 1 in 13.
  assertEquals(1/13.0,FarmYield.chance(FarmYield.growth(0,0,Set.of(FarmYield.key(0,0)),Set.of(),Set.of(FarmYield.key(0,0)))),1e-12);
 }
}
