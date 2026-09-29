package org.villageastra.world;
import java.util.*;
/** AD-127: the outline of a wall fitted to its village, pure geometry with no world in it. The ring is a profile of radii, one per
 *  sector round the hall (72 sectors of 5 degrees), each far enough out that every sample of the village's extent keeps the margin
 *  from it, plus a headroom for growth. The profile is then raised until it is convex with a real turn at every vertex, so the ring is
 *  round and never runs straight along a face of the village. Radii are kept on a 0.1 grid, so a profile read back from its record
 *  rasterises to exactly the same columns. */
public final class WallOutline {
 private WallOutline(){}
 /** Balance of the fitted wall (balance/walls.json). */
 public record Config(int sectors,int margin,int headroom,int trigger,int maxRadius,int obstaclePush,int pushStep,int cliffStep,int minRadius,int shallowWater,int extendEvery,double minTurnDeg){
  public Config{
   if(sectors<16||sectors>360||360%sectors!=0)throw new IllegalStateException("Wall sectors must divide 360 and be 16..360: "+sectors);
   if(margin<1||headroom<0||headroom>24||trigger<0||trigger>headroom+margin)throw new IllegalStateException("Wall margin/headroom/trigger out of range");
   if(minRadius<8||maxRadius<minRadius||maxRadius>128)throw new IllegalStateException("Wall radii out of range");
   if(pushStep<1||obstaclePush<pushStep||cliffStep<1||shallowWater<0||extendEvery<20)throw new IllegalStateException("Wall obstacle rules out of range");
   if(minTurnDeg<=0||minTurnDeg>=360.0/sectors)throw new IllegalStateException("Wall least turn must be above 0 and below one sector");
  }
  public double minTurn(){return Math.toRadians(minTurnDeg);}
  /** The widest radius of curvature the rounding allows at a radius: one sector's arc over the least turn. */
  public double curvature(double radius){return radius*(360.0/sectors)/minTurnDeg;}
 }
 private static final double EPS=1e-9;
 /** A radius on the 0.1 grid, never below the value given. */
 public static double quantize(double r){return Math.ceil(r*10-1e-6)/10.0;}
 public static double angle(int k,int sectors){return 2*Math.PI*k/sectors;}
 /** The perimeter of a box of block columns, sampled every two blocks with its corners. */
 public static List<int[]> perimeter(int west,int north,int east,int south){
  var out=new ArrayList<int[]>();
  for(int x=west;;x=Math.min(east,x+2)){out.add(new int[]{x,north});out.add(new int[]{x,south});if(x==east)break;}
  for(int z=north;;z=Math.min(south,z+2)){out.add(new int[]{west,z});out.add(new int[]{east,z});if(z==south)break;}
  return out;
 }
 /** How far out the ring must stand in each sector so that every sample keeps the margin: the sample's reach toward the sector's
  *  bearing, over the half-sector chord factor, plus the margin and one column of rounding. */
 public static double[] need(int cx,int cz,List<int[]> samples,int sectors,int margin,int minRadius){
  var out=new double[sectors];Arrays.fill(out,minRadius);double step=2*Math.PI/sectors,chord=Math.cos(step/2);
  for(var p:samples){double dx=p[0]-cx,dz=p[1]-cz,d=Math.hypot(dx,dz);
   if(d<EPS){for(int k=0;k<sectors;k++)out[k]=Math.max(out[k],margin);continue;}
   double theta=Math.atan2(dz,dx),window=Math.asin(Math.min(1,2/d))+step;
   for(int k=0;k<sectors;k++){double delta=Math.abs(Math.IEEEremainder(angle(k,sectors)-theta,2*Math.PI));if(delta>window)continue;
    out[k]=Math.max(out[k],(d*Math.cos(Math.min(delta,Math.PI/2))+margin)/chord+1);}
  }
  return out;
 }
 /** The heading turn at a vertex of radius r between neighbours of radii a (one sector back) and b (one sector on), in radians. */
 public static double turn(double a,double r,double b,double step){
  double ax=a*Math.cos(step),az=-a*Math.sin(step),bx=b*Math.cos(step),bz=b*Math.sin(step);
  double ux=r-ax,uz=-az,wx=bx-r,wz=bz;return Math.atan2(ux*wz-uz*wx,ux*wx+uz*wz);
 }
 /** Raised, never lowered, until the heading turns by at least minTurn at every vertex: the least such profile above the given one.
  *  Raising a neighbour only lessens a vertex's turn, and a circle turns by a whole sector at every vertex, so with minTurn below one
  *  sector the circle of the largest radius satisfies the rule and nothing grows past the largest radius given. A flat face of the
  *  village is bent into an arc; the ring is convex and round. */
 public static double[] round(double[] start,double minTurn){
  int n=start.length;var r=new double[n];for(int k=0;k<n;k++)r[k]=quantize(start[k]);
  double step=2*Math.PI/n;if(minTurn>=step)throw new IllegalArgumentException("The least turn must be below one sector");
  for(boolean changed=true;changed;){changed=false;
   for(int k=0;k<n;k++){double a=r[(k+n-1)%n],b=r[(k+1)%n];if(turn(a,r[k],b,step)>=minTurn-EPS)continue;
    double lo=r[k],hi=Math.max(a,b);while(turn(a,hi,b,step)<minTurn)hi*=1.5;
    for(int i=0;i<50;i++){double mid=(lo+hi)/2;if(turn(a,mid,b,step)>=minTurn)hi=mid;else lo=mid;}
    double q=quantize(hi);if(q>r[k]+EPS){r[k]=q;changed=true;}}}
  return r;
 }
 /** A new ring: the need, the headroom, then rounded. */
 public static double[] fresh(double[] need,int headroom,double minTurn){var r=new double[need.length];for(int k=0;k<r.length;k++)r[k]=need[k]+headroom;return round(r,minTurn);}
 /** The village has outgrown its ring when in some sector it needs more than the ring less the trigger. */
 public static boolean outgrown(double[] old,double[] need,int trigger){for(int k=0;k<old.length;k++)if(need[k]>old[k]-trigger+EPS)return true;return false;}
 /** Below this a sector's rise does not move its vertex off its column: the old vertex is kept. */
 public static final double SETTLE=0.5;
 /** The ring after growth: only the outgrown sectors move out (to need plus headroom), the rest stay; then rounded, raise-only. The
  *  rounding bends the arcs beside the new bulge; where it would lift a vertex by less than half a column the old vertex stays, so the
  *  far side of the ring keeps every column it stands on. */
 public static double[] grow(double[] old,double[] need,int headroom,int trigger,double minTurn){
  var r=old.clone();for(int k=0;k<r.length;k++)if(need[k]>old[k]-trigger+EPS)r[k]=Math.max(old[k],need[k]+headroom);r=round(r,minTurn);
  for(int k=0;k<r.length;k++)if(r[k]-old[k]<SETTLE&&!(need[k]>old[k]-trigger+EPS))r[k]=old[k];
  return r;}
 /** The longest straight run a digital circle of this radius of curvature may have: the arc stays within one column of a row over
  *  2·sqrt(2ρ) columns. */
 public static int straightLimit(double curvature){return (int)Math.ceil(2*Math.sqrt(2*curvature))+1;}
 public static int[] toX10(double[] r){var out=new int[r.length];for(int k=0;k<r.length;k++)out[k]=(int)Math.round(r[k]*10);return out;}
 public static double[] fromX10(int[] r){var out=new double[r.length];for(int k=0;k<r.length;k++)out[k]=r[k]/10.0;return out;}
 public static double max(double[] r){double m=0;for(double x:r)m=Math.max(m,x);return m;}
 public static double min(double[] r){double m=Double.MAX_VALUE;for(double x:r)m=Math.min(m,x);return m;}
 /** Profiles of the AD-094 shapes, to judge whether a village has outgrown an old wall. */
 public static double[] circle(int radius,int sectors){var r=new double[sectors];Arrays.fill(r,radius);return r;}
 public static double[] square(int radius,int sectors){var r=new double[sectors];for(int k=0;k<sectors;k++){double t=angle(k,sectors);r[k]=radius/Math.max(Math.abs(Math.cos(t)),Math.abs(Math.sin(t)));}return r;}
 /** The vertex column of a sector. */
 public static int[] vertex(int cx,int cz,double r,int k,int sectors){double t=angle(k,sectors);return new int[]{(int)Math.floor(cx+r*Math.cos(t)+0.5),(int)Math.floor(cz+r*Math.sin(t)+0.5)};}
 /** The sector a column's bearing is nearest to, and the two whose vertices bound it. */
 public static int sector(int cx,int cz,int x,int z,int sectors){double t=Math.atan2(z-cz,x-cx);if(t<0)t+=2*Math.PI;return (int)Math.round(t/(2*Math.PI/sectors))%sectors;}
 public static int sectorBelow(int cx,int cz,int x,int z,int sectors){double t=Math.atan2(z-cz,x-cx);if(t<0)t+=2*Math.PI;return ((int)Math.floor(t/(2*Math.PI/sectors)))%sectors;}
 /** The ring's columns: vertex to vertex by four-connected lines (a step that would go diagonally goes first to the side of the
  *  centre), once each, starting at the northernmost column and going clockwise (north, east, south, west). */
 public static List<int[]> raster(int cx,int cz,double[] r){
  int n=r.length;var out=new ArrayList<int[]>();var seen=new HashSet<Long>();
  for(int k=0;k<n;k++){var a=vertex(cx,cz,r[k],k,n);var b=vertex(cx,cz,r[(k+1)%n],(k+1)%n,n);line(cx,cz,a,b,out,seen);}
  int start=0;for(int i=1;i<out.size();i++){var p=out.get(i);var s=out.get(start);if(p[1]<s[1]||p[1]==s[1]&&p[0]<s[0])start=i;}
  var rotated=new ArrayList<int[]>(out.size());for(int i=0;i<out.size();i++)rotated.add(out.get((start+i)%out.size()));
  return rotated;
 }
 private static void line(int cx,int cz,int[] a,int[] b,List<int[]> out,Set<Long> seen){
  int x=a[0],z=a[1],nx=Math.abs(b[0]-a[0]),nz=Math.abs(b[1]-a[1]),sx=Integer.signum(b[0]-a[0]),sz=Integer.signum(b[1]-a[1]);add(out,seen,x,z);
  for(int ix=0,iz=0;ix<nx||iz<nz;){
   long lx=(long)(1+2*ix)*nz,lz=(long)(1+2*iz)*nx;boolean stepX;
   if(ix>=nx)stepX=false;else if(iz>=nz)stepX=true;else if(lx!=lz)stepX=lx<lz;
   else{long ax=sq(x+sx-cx)+sq(z-cz),az=sq(x-cx)+sq(z+sz-cz);stepX=ax<=az;}
   if(stepX){x+=sx;ix++;}else{z+=sz;iz++;}add(out,seen,x,z);}
 }
 private static long sq(long v){return v*v;}
 private static void add(List<int[]> out,Set<Long> seen,int x,int z){if(seen.add(((long)x<<32)^(z&0xffffffffL)))out.add(new int[]{x,z});}
 /** The longest run of consecutive ring columns along one row or one file. */
 public static int straightRun(List<int[]> ring){
  int n=ring.size(),best=0;if(n==0)return 0;
  for(int axis=0;axis<2;axis++){int other=1-axis;
   int start=0;while(start<n&&ring.get(start)[other]==ring.get((start+n-1)%n)[other])start++;if(start==n)return n;
   int run=1;for(int i=1;i<=n;i++){var p=ring.get((start+i)%n);var q=ring.get((start+i-1)%n);
    if(p[other]==q[other]&&Math.abs(p[axis]-q[axis])==1)run++;else{best=Math.max(best,run);run=1;}}
   best=Math.max(best,run);}
  return best;
 }
}
