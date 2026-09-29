package org.villageastra.client;
import org.junit.jupiter.api.Test;
import org.villageastra.client.OfficeGrid.Box;
import org.villageastra.client.OfficeGrid.Size;
import static org.junit.jupiter.api.Assertions.*;
/** AD-124: the office geometry at every size the players use: zones inside the frame and apart, the frame capped and centred,
 *  the size classes, columns without holes or overflow, and the old content rectangle kept where QuestPanel relies on it. */
class OfficeGridTest {
 private static final int[][] SIZES={{320,240},{360,240},{427,240},{480,270},{519,300},{520,300},{640,360},{800,450},{960,540},{1280,720},{1920,1080}};
 @Test void zonesLieInsideTheFrameAndApart(){
  for(var s:SIZES){int w=s[0],h=s[1];var f=OfficeGrid.frame(w,h);String at=w+"x"+h;
   Box[] zones={OfficeGrid.header(w,h),OfficeGrid.tabs(w,h),OfficeGrid.content(w,h),OfficeGrid.footer(w,h)};
   for(var z:zones){assertTrue(f.contains(z),at+" zone "+z+" outside "+f);assertTrue(z.w()>0&&z.h()>0,at+" empty zone "+z);}
   for(int i=0;i<zones.length;i++)for(int j=i+1;j<zones.length;j++)assertFalse(zones[i].intersects(zones[j]),at+" zones overlap "+zones[i]+" "+zones[j]);
   var footer=OfficeGrid.footer(w,h);var done=OfficeGrid.done(w,h);var help=OfficeGrid.help(w,h);
   assertTrue(footer.contains(done)&&footer.contains(help),at+" Done/? outside the footer");assertFalse(done.intersects(help),at+" Done over ?");
   assertTrue(OfficeGrid.footerEnd(w,h)<=help.x(),at+" footerEnd right of '?'");
   assertTrue(OfficeGrid.header(w,h).contains(OfficeGrid.atlas(w,h)),at+" Atlas outside the header");
   assertTrue(f.w()<=OfficeGrid.CAP_W&&f.h()<=OfficeGrid.CAP_H,at+" frame over the cap "+f);
   assertTrue(Math.abs(f.x()-(w-f.right()))<=1&&Math.abs(f.y()-(h-f.bottom()))<=1,at+" frame not centred "+f);
   assertEquals(w<360?Size.XS:w<520?Size.S:w<800?Size.M:Size.L,OfficeGrid.size(w),at+" size class");}
 }
 @Test void theSmallGuisKeepTheContentQuestPanelKnows(){
  for(var s:new int[][]{{320,240},{427,240}}){var c=OfficeGrid.content(s[0],s[1]);assertEquals(new Box(12,55,s[0]-24,s[1]-83),c,"content at "+s[0]+"x"+s[1]);
   assertEquals(new Box(12,s[1]-23,s[0]-24,18),OfficeGrid.footer(s[0],s[1]));assertEquals(new Box(s[0]-64,s[1]-23,52,18),OfficeGrid.done(s[0],s[1]));assertEquals(s[0]-90,OfficeGrid.footerEnd(s[0],s[1]));}
 }
 @Test void aCappedFrameMovesEveryZoneByItsOffset(){
  // 1280x720: the frame is 1000x600 at (140,60); every zone lies where it would at 1012x608, moved by (134,56).
  var f=OfficeGrid.frame(1280,720);assertEquals(new Box(140,60,1000,600),f);int dx=f.x()-6,dy=f.y()-4;
  assertEquals(shift(OfficeGrid.header(1012,608),dx,dy),OfficeGrid.header(1280,720));assertEquals(shift(OfficeGrid.tabs(1012,608),dx,dy),OfficeGrid.tabs(1280,720));
  assertEquals(shift(OfficeGrid.content(1012,608),dx,dy),OfficeGrid.content(1280,720));assertEquals(shift(OfficeGrid.footer(1012,608),dx,dy),OfficeGrid.footer(1280,720));
  assertEquals(shift(OfficeGrid.done(1012,608),dx,dy),OfficeGrid.done(1280,720));assertEquals(shift(OfficeGrid.help(1012,608),dx,dy),OfficeGrid.help(1280,720));
  assertEquals(shift(OfficeGrid.atlas(1012,608),dx,dy),OfficeGrid.atlas(1280,720));assertEquals(OfficeGrid.footerEnd(1012,608)+dx,OfficeGrid.footerEnd(1280,720));
 }
 private static Box shift(Box b,int dx,int dy){return new Box(b.x()+dx,b.y()+dy,b.w(),b.h());}
 @Test void columnsFillTheContentWithoutHolesOrOverlap(){
  for(var s:SIZES){var c=OfficeGrid.content(s[0],s[1]);for(int min:new int[]{40,90,150,400})for(int max=1;max<=4;max++){
   var cols=OfficeGrid.columns(c,min,6,max);assertTrue(cols.length>=1&&cols.length<=max);assertEquals(c.x(),cols[0].x());assertEquals(c.right(),cols[cols.length-1].right(),"no hole at the right");
   for(int i=0;i<cols.length;i++){assertTrue(c.contains(cols[i]),"column inside the content");if(i>0){assertFalse(cols[i-1].intersects(cols[i]));assertEquals(cols[i-1].right()+6,cols[i].x(),"one gap between columns");}}
   if(cols.length>1)for(var col:cols)assertTrue(col.w()>=min,"a column of at least "+min);}}
 }
 @Test void splitsAndStacksShareTheArea(){
  var a=new Box(10,20,300,100);var parts=OfficeGrid.split(a,8,3,2);assertEquals(10,parts[0].x());assertEquals(a.right(),parts[1].right());assertEquals(parts[0].right()+8,parts[1].x());assertEquals(175,parts[0].w());
  var rows=OfficeGrid.stack(a,4,18,0,18);assertEquals(20,rows[0].y());assertEquals(18,rows[0].h());assertEquals(a.bottom(),rows[2].bottom());assertEquals(56,rows[1].h());
 }
}
