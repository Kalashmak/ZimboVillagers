package org.villageastra.client;
/** AD-124: the office's geometry without Minecraft types, so JUnit can hold it to its rules. The frame is capped at 1000x600 GUI pixels
 *  and centred; every zone (header, tabs, content, footer, Done, '?', Atlas) is placed from the frame's corner, never from the screen's.
 *  Up to 1012x608 the frame starts at (6,4) and every zone lies where it always did. */
public final class OfficeGrid {
 private OfficeGrid(){}
 public static final int CAP_W=1000,CAP_H=600;
 public record Box(int x,int y,int w,int h){
  public int right(){return x+w;} public int bottom(){return y+h;}
  public boolean contains(Box b){return b.x>=x&&b.y>=y&&b.right()<=right()&&b.bottom()<=bottom();}
  public boolean intersects(Box b){return b.x<right()&&x<b.right()&&b.y<bottom()&&y<b.bottom();}
 }
 /** Size classes by the GUI width: XS < 360, S < 520, M < 800, L from 800. */
 public enum Size{XS,S,M,L;
  public static Size of(int width){return width<360?XS:width<520?S:width<800?M:L;}
  public boolean atLeast(Size s){return ordinal()>=s.ordinal();}
 }
 public static Size size(int width){return Size.of(width);}
 /** Tall screens (300 GUI pixels and more) give the Overview's tiles their trend and hint lines. */
 public static boolean tall(int height){return height>=300;}
 public static Box frame(int width,int height){int fw=Math.min(width-12,CAP_W),fh=Math.min(height-8,CAP_H);return new Box((width-fw)/2,(height-fh)/2,fw,fh);}
 public static Box header(int width,int height){var f=frame(width,height);return new Box(f.x+1,f.y+1,f.w-2,23);}
 public static Box tabs(int width,int height){var f=frame(width,height);return new Box(f.x+4,f.y+27,f.w-8,18);}
 public static Box content(int width,int height){var f=frame(width,height);return new Box(f.x+6,f.y+51,f.w-12,f.h-75);}
 public static Box footer(int width,int height){var f=frame(width,height);return new Box(f.x+6,f.bottom()-19,f.w-12,18);}
 public static Box done(int width,int height){var f=frame(width,height);return new Box(f.right()-58,f.bottom()-19,52,18);}
 public static Box help(int width,int height){var f=frame(width,height);return new Box(f.right()-80,f.bottom()-19,18,18);}
 public static Box atlas(int width,int height){var f=frame(width,height);return new Box(f.right()-66,f.y+4,62,18);}
 /** The footer's free part ends 84 px short of the frame's right edge, where '?' and Done stand. */
 public static int footerEnd(int width,int height){return frame(width,height).right()-84;}
 /** The line between the content and the footer. */
 public static int footerLine(int width,int height){return frame(width,height).bottom()-22;}
 /** Equal columns of at least minW (at most max), the gaps between them, the rest of the width given to the last: no hole, no overflow. */
 public static Box[] columns(Box area,int minW,int gap,int max){
  int n=Math.max(1,Math.min(Math.max(1,max),(area.w+gap)/Math.max(1,minW+gap)));int cw=Math.max(0,(area.w-(n-1)*gap)/n);var out=new Box[n];int x=area.x;
  for(int i=0;i<n;i++){int w=i==n-1?area.right()-x:cw;out[i]=new Box(x,area.y,Math.max(0,w),area.h);x+=cw+gap;}
  return out;
 }
 /** Side by side by weights, with a gap between. */
 public static Box[] split(Box area,int gap,int... weights){
  int total=0;for(int w:weights)total+=Math.max(0,w);var out=new Box[weights.length];int room=Math.max(0,area.w-gap*(weights.length-1)),x=area.x,used=0;
  for(int i=0;i<weights.length;i++){int w=i==weights.length-1?room-used:total<=0?0:room*Math.max(0,weights[i])/total;out[i]=new Box(x,area.y,w,area.h);x+=w+gap;used+=w;}
  return out;
 }
 /** One under another at the given heights; a height of 0 or less takes what is left. */
 public static Box[] stack(Box area,int gap,int... heights){
  int fixed=0,rest=0;for(int h:heights)if(h>0)fixed+=h;else rest++;int left=Math.max(0,area.h-fixed-gap*(heights.length-1));
  var out=new Box[heights.length];int y=area.y;
  for(int i=0;i<heights.length;i++){int h=heights[i]>0?heights[i]:rest==0?0:left/rest;h=Math.max(0,Math.min(h,area.bottom()-y));out[i]=new Box(area.x,y,area.w,h);y+=h+gap;}
  return out;
 }
}
