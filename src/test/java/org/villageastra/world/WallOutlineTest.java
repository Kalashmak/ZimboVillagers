package org.villageastra.world;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-127: the fitted wall's outline — closed, clear of every sample by the margin, round, growing only where the village grew, and
 *  the same for the same village. */
class WallOutlineTest {
 private static final int SECTORS=72,MARGIN=4,HEADROOM=8,TRIGGER=2,MIN=16,MAX=96;private static final double TURN=Math.toRadians(3.5);
 private static List<int[]> village(int[]... boxes){var out=new ArrayList<int[]>();out.add(new int[]{0,0});for(var b:boxes)out.addAll(WallOutline.perimeter(b[0],b[1],b[2],b[3]));return out;}
 private static double[] need(List<int[]> samples){return WallOutline.need(0,0,samples,SECTORS,MARGIN,MIN);}
 private static double[] ring(List<int[]> samples){return WallOutline.fresh(need(samples),HEADROOM,TURN);}
 private static long key(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
 /** The inside, flooded from the centre side by side; null when it reaches the outside. */
 private static Set<Long> inside(List<int[]> ring){
  var wall=new HashSet<Long>();int edge=0;for(var p:ring){wall.add(key(p[0],p[1]));edge=Math.max(edge,Math.max(Math.abs(p[0]),Math.abs(p[1])));}edge+=3;
  var seen=new HashSet<Long>();var queue=new ArrayDeque<int[]>();queue.add(new int[]{0,0});seen.add(key(0,0));
  while(!queue.isEmpty()){var at=queue.poll();if(Math.abs(at[0])>=edge||Math.abs(at[1])>=edge)return null;
   for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int x=at[0]+d[0],z=at[1]+d[1];long k=key(x,z);if(wall.contains(k)||!seen.add(k))continue;queue.add(new int[]{x,z});}}
  return seen;
 }
 private static int[][] grid(){var out=new ArrayList<int[]>();for(int i=-1;i<=1;i++)for(int j=-1;j<=1;j++){int x=i*16,z=j*16;out.add(new int[]{x-5,z-5,x+4,z+4});}return out.toArray(int[][]::new);}
 private static final int[][] HALL={{-8,-8,8,8}};
 private static final int[][] L={{-8,-8,8,8},{12,-8,28,8},{32,-8,48,8},{-8,12,8,28},{-8,32,8,48}};
 private static final int[][] STRIP={{-60,-10,60,10}};
 private static void clear(List<int[]> samples,List<int[]> ring,String what){
  var in=inside(ring);assertNotNull(in,what+": the ring is closed");
  for(var s:samples){assertTrue(in.contains(key(s[0],s[1])),what+": sample "+Arrays.toString(s)+" lies inside");
   double best=Double.MAX_VALUE;for(var c:ring)best=Math.min(best,Math.hypot(c[0]-s[0],c[1]-s[1]));
   assertTrue(best>=MARGIN,what+": sample "+Arrays.toString(s)+" keeps the margin, "+best);}
 }
 @Test void theRingIsClosedAndClearOfEveryBuilding(){
  for(var entry:Map.of("hall",HALL,"grid",grid(),"L",L).entrySet()){var samples=village(entry.getValue());var r=ring(samples);
   assertTrue(WallOutline.max(r)<=MAX,entry.getKey()+" fits: "+WallOutline.max(r));clear(samples,WallOutline.raster(0,0,r),entry.getKey());}
  var strip=ring(village(STRIP));
  if(WallOutline.max(strip)<=MAX)clear(village(STRIP),WallOutline.raster(0,0,strip),"strip");
  else assertTrue(WallOutline.max(strip)>MAX,"A long strip is too large for a wall");
 }
 @Test void theRingIsRound(){
  var samples=village(grid());var r=ring(samples);var ring=WallOutline.raster(0,0,r);
  int limit=WallOutline.straightLimit(WallOutline.max(r)*5/3.5);
  assertTrue(WallOutline.straightRun(ring)<=limit,"No straight run longer than an arc of the widest curvature allows: "+WallOutline.straightRun(ring)+" > "+limit+" at radius "+WallOutline.max(r));
  for(var v:List.of(HALL,grid(),L)){var p=ring(village(v));assertTrue(WallOutline.max(p)/WallOutline.min(p)<=2.2,"Round, not stretched: "+WallOutline.max(p)+"/"+WallOutline.min(p));
   for(int k=0;k<SECTORS;k++){double[] a=point(p,k-1),b=point(p,k),c=point(p,k+1);
    double turn=Math.toDegrees(Math.abs(Math.IEEEremainder(Math.atan2(c[1]-b[1],c[0]-b[0])-Math.atan2(b[1]-a[1],b[0]-a[0]),2*Math.PI)));
    assertTrue(turn>=3.5-1e-6&&turn<=25,"The heading turns at every vertex, never sharply: "+turn+" at "+k);}}
 }
 private static double[] point(double[] r,int k){k=(k+r.length)%r.length;double t=WallOutline.angle(k,r.length);return new double[]{r[k]*Math.cos(t),r[k]*Math.sin(t)};}
 @Test void aVillageWithOnlyItsCentreGetsACircle(){
  var r=ring(List.of(new int[]{0,0}));var ring=WallOutline.raster(0,0,r);
  for(var c:ring)assertTrue(Math.abs(Math.hypot(c[0],c[1])-(MIN+HEADROOM))<=1.0,"Every column lies on the circle: "+Arrays.toString(c));
  assertNotNull(inside(ring));
 }
 @Test void roundingNeverRaisesPastTheLargestRadius(){
  var random=new Random(127);
  for(int n=0;n<200;n++){var r0=new double[SECTORS];for(int k=0;k<SECTORS;k++)r0[k]=MIN+random.nextDouble()*40;var r=WallOutline.round(r0,TURN);
   assertTrue(WallOutline.max(r)<=WallOutline.quantize(WallOutline.max(r0))+1e-9,"Bounded by the largest radius");
   for(int k=0;k<SECTORS;k++)assertTrue(r[k]>=r0[k]-1e-9,"Never lowered");}
 }
 @Test void theRingGrowsOnlyWhereTheVillageGrew(){
  var before=village(L);var old=ring(before);var oldRing=WallOutline.raster(0,0,old);
  var after=new ArrayList<>(before);after.addAll(WallOutline.perimeter(56,-8,72,8));
  var n=need(after);assertTrue(WallOutline.outgrown(old,n,TRIGGER),"A house beyond the ring outgrows it");
  assertFalse(WallOutline.outgrown(old,need(before),TRIGGER),"The village it was drawn for does not");
  var grown=WallOutline.grow(old,n,HEADROOM,TRIGGER,TURN);var changed=new boolean[SECTORS];
  for(int k=0;k<SECTORS;k++){assertTrue(grown[k]>=old[k]-1e-9,"Never lowered at "+k);changed[k]=Math.abs(grown[k]-old[k])>1e-9;
   if(changed[k]){double bearing=Math.toDegrees(WallOutline.angle(k,SECTORS));assertTrue(bearing<135||bearing>225,"The side away from the growth stays where it is: sector "+k+" old "+Arrays.toString(old)+" new "+Arrays.toString(grown));}}
  assertTrue(changed[0],"The east sector moved out");
  var grownRing=WallOutline.raster(0,0,grown);var cells=new HashSet<Long>();for(var c:grownRing)cells.add(key(c[0],c[1]));
  var west=new HashSet<Long>();for(var c:oldRing){int k=WallOutline.sectorBelow(0,0,c[0],c[1],SECTORS);if(!changed[k]&&!changed[(k+1)%SECTORS]&&!changed[(k+SECTORS-1)%SECTORS]&&!changed[(k+2)%SECTORS])west.add(key(c[0],c[1]));}
  assertTrue(west.size()>oldRing.size()/4&&cells.containsAll(west),"The unaffected stretch stays column for column: "+west.size());
  clear(after,grownRing,"grown");
 }
 @Test void theSameVillageGivesTheSameRing(){
  var a=WallOutline.raster(0,0,ring(village(L)));var b=WallOutline.raster(0,0,ring(village(L)));
  assertEquals(a.size(),b.size());for(int i=0;i<a.size();i++)assertArrayEquals(a.get(i),b.get(i),"Same order at "+i);
  var fromRecord=WallOutline.raster(0,0,WallOutline.fromX10(WallOutline.toX10(ring(village(L)))));
  for(int i=0;i<a.size();i++)assertArrayEquals(a.get(i),fromRecord.get(i),"A profile read back from its record gives the same ring");
 }
}
