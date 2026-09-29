package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** QUEST-002: the chart of a place thousands of blocks from home. Vanilla snaps a map's centre to a grid of 128·2^scale blocks and drops a
 *  mark more than 63 map pixels from that centre, so a far place needs the widest chart, centred on itself; a near one keeps the chart that
 *  holds the village too, which is what tells the player which way home is. */
class FarChartTest {
 private static int[] frame(int sx,int sz){return Charts.frame(sx,sz,0,0);}
 /** The rule vanilla itself applies when it draws the mark. */
 private static boolean onChart(int x,int z,int[] frame){
  int size=128<<frame[2],cx=Math.floorDiv(frame[0]+64,size)*size+size/2-64,cz=Math.floorDiv(frame[1]+64,size)*size+size/2-64;
  return Math.abs(x-cx)<=62<<frame[2]&&Math.abs(z-cz)<=62<<frame[2];
 }
 @Test void aPlaceNearHomeSharesItsChartWithTheVillage(){
  var f=frame(300,200);
  assertTrue(f[2]<=2,"a near place needs no wide chart: "+f[2]);
  assertTrue(onChart(300,200,f),"the cross is on the chart");
  assertTrue(onChart(0,0,f),"and so is the village, so the way home can be read from it");
 }
 @Test void aPlaceThousandsOfBlocksAwayStillCarriesItsCross(){
  for(int[] site:new int[][]{{3000,0},{-4200,1500},{1200,-5200},{5200,5200}}){
   var f=frame(site[0],site[1]);
   assertEquals(4,f[2],"the widest chart for "+site[0]+","+site[1]);
   assertEquals(site[0],f[0]);assertEquals(site[1],f[1]);
   assertTrue(onChart(site[0],site[1],f),"the cross is on the chart of "+site[0]+","+site[1]);
  }
 }
 @Test void theFarthestChartHoldsEveryPlaceExceptTheOuterRingOfItsGridSquare(){
  // The centre is snapped to the grid, so the place lies up to half a square (1024 blocks) from it, while vanilla draws no cross past
  // 62 pixels (992 blocks). The few places in that outer ring cannot be charted at all — which is why a village never writes one down
  // (Far.probe keeps looking instead of promising a chart it cannot draw).
  int size=128<<Charts.WIDEST,z=-3000,cz=Math.floorDiv(z+64,size)*size+size/2-64,beyond=0;
  assertTrue(Math.abs(z-cz)<=62<<Charts.WIDEST,"the row itself is on the chart");
  for(int dx=0;dx<size;dx++){int x=4000+dx,cx=Math.floorDiv(x+64,size)*size+size/2-64;
   boolean on=onChart(x,z,frame(x,z));
   assertEquals(Math.abs(x-cx)<=62<<Charts.WIDEST,on,"the cross of "+x);
   if(!on)beyond++;}
  assertEquals(63,beyond,"only the outer ring of the square lies beyond the widest chart: "+beyond);
 }
}
