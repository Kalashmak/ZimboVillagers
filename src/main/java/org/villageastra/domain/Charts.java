package org.villageastra.domain;
/** AD-080 and QUEST-002: where a quest's chart is drawn and how wide, as plain arithmetic. Vanilla snaps a map's centre to a grid of
 *  128·2^scale blocks and drops a mark more than 63 map pixels from that centre, so the chart is chosen by that rule and by nothing else:
 *  the smallest chart whose snapped square holds both the place and the village, else the smallest that holds the place, and for a place
 *  thousands of blocks from home — where no chart holds both — the widest chart there is, centred on the place itself. */
public final class Charts {
 public static final int WIDEST=4;
 private Charts(){}
 /** The centre vanilla snaps a chart of this scale to. */
 public static int snapped(int x,int scale){int size=128<<scale;return Math.floorDiv(x+64,size)*size+size/2-64;}
 /** Whether a point really falls on a chart of this scale snapped round that centre (a pixel inside the edge, where a mark still draws). */
 public static boolean onMap(int x,int z,int cx,int cz,int scale){return Math.abs(x-cx)<=62<<scale&&Math.abs(z-cz)<=62<<scale;}
 public static boolean onMap(int x,int z,int mx,int mz,int scale,boolean snap){return onMap(x,z,snap?snapped(mx,scale):mx,snap?snapped(mz,scale):mz,scale);}
 /** {x, z, scale} of the chart to draw for this place and this village. */
 public static int[] frame(int sx,int sz,int hx,int hz){
  int mx=Math.floorDiv(sx+hx,2),mz=Math.floorDiv(sz+hz,2);
  for(int s=0;s<=WIDEST;s++)if(onMap(sx,sz,mx,mz,s,true)&&onMap(hx,hz,mx,mz,s,true))return new int[]{mx,mz,s};
  for(int s=0;s<=WIDEST;s++)if(onMap(sx,sz,mx,mz,s,true))return new int[]{mx,mz,s};
  return new int[]{sx,sz,WIDEST};
 }
}
