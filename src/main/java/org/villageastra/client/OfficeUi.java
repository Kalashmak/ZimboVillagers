package org.villageastra.client;
import java.util.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.domain.ResearchCatalog;
/** The town hall office kit: the one place that draws chips, bars, pips, item cells, badges, rows, scrollbars and tooltips.
 *  Colour always means the same thing (green fine, amber waiting or short, red blocked or danger, blue a decision waits for you, grey unavailable),
 *  and every coloured thing also carries a pixel sign, so no legend is needed and colour-blind players can still read it. */
public final class OfficeUi {
 private OfficeUi(){}
 public static final int FRAME=0xFFB89B65,BODY=0xFF202A30,HEADER=0xFF30443F,CARD=0xFF28343B,ROW_HOVER=0xFF32424A,ROW_SELECTED=0xFF3A4A3E,TITLE=0xFFE9CD92,TEXT=0xFFE6EEF0,MUTED=0xFF8FA2A8,TRACK=0xFF344761,SPECIAL=0xFFC39BEA,DIM=0xC010171C;
 public enum Tone {
  OK(0xFF6FCF6A,0xFF1F3A24,0xFF3FA046,'✔',2),WAIT(0xFFF0B429,0xFF3D3016,0xFFC98F12,'⏳',3),BAD(0xFFFF5C4D,0xFF421E1C,0xFFD2412F,'✖',5),
  ACTION(0xFF5AB0FF,0xFF16304A,0xFF398CFF,'➜',4),OFF(0xFF6E7B80,0xFF2A3136,0xFF4A5560,'–',0),INFO(TEXT,CARD,0xFF398CFF,'•',1);
  private final int fg,bg,bar,rank;private final char sign;
  Tone(int fg,int bg,int bar,char sign,int rank){this.fg=fg;this.bg=bg;this.bar=bar;this.sign=sign;this.rank=rank;}
  public int fg(){return fg;} public int bg(){return bg;} public int bar(){return bar;} public char sign(){return sign;}
  /** Badge order: danger first, then a decision waiting, then a shortfall. */
  public static Tone worst(Tone a,Tone b){return a==null?b:b==null?a:a.rank>=b.rank?a:b;}
  int rank(){return rank;}
 }
 public record Rect(int x,int y,int w,int h){
  public int right(){return x+w;} public int bottom(){return y+h;}
  public boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
  public boolean containsRect(Rect r){return r.x>=x&&r.y>=y&&r.right()<=right()&&r.bottom()<=bottom();}
  public boolean intersects(Rect r){return r.x<right()&&x<r.right()&&r.y<bottom()&&y<r.bottom();}
  public Rect inset(int d){return new Rect(x+d,y+d,Math.max(0,w-2*d),Math.max(0,h-2*d));}
  public Rect intersect(Rect r){int nx=Math.max(x,r.x),ny=Math.max(y,r.y);return new Rect(nx,ny,Math.max(0,Math.min(right(),r.right())-nx),Math.max(0,Math.min(bottom(),r.bottom())-ny));}
  public Rect[] splitH(int leftW,int gap){return new Rect[]{new Rect(x,y,leftW,h),new Rect(x+leftW+gap,y,Math.max(0,w-leftW-gap),h)};}
  public Rect[] splitV(int topH,int gap){return new Rect[]{new Rect(x,y,w,topH),new Rect(x,y+topH+gap,w,Math.max(0,h-topH-gap))};}
  static Rect of(AbstractWidget b){return new Rect(b.getX(),b.getY(),b.getWidth(),b.getHeight());}
 }
 /** One frame and one grid for every office tab: panels never pick absolute y values. AD-124: the geometry is OfficeGrid's —
  *  a frame capped at 1000x600 and centred, size classes XS/S/M/L, and columns, splits and stacks for the panels. */
 public static final class Layout {
  public final int width,height;
  public Layout(int width,int height){this.width=width;this.height=height;}
  static Rect rect(OfficeGrid.Box b){return new Rect(b.x(),b.y(),b.w(),b.h());}
  static OfficeGrid.Box box(Rect r){return new OfficeGrid.Box(r.x(),r.y(),r.w(),r.h());}
  public Rect frame(){return rect(OfficeGrid.frame(width,height));}
  public Rect header(){return rect(OfficeGrid.header(width,height));}
  public Rect tabs(){return rect(OfficeGrid.tabs(width,height));}
  public Rect content(){return rect(OfficeGrid.content(width,height));}
  public Rect footer(){return rect(OfficeGrid.footer(width,height));}
  /** Done and '?' close the footer on the right, inside it (the footer ends 6 px inside the frame, as the content does). */
  public Rect done(){return rect(OfficeGrid.done(width,height));}
  public Rect help(){return rect(OfficeGrid.help(width,height));}
  public Rect atlas(){return rect(OfficeGrid.atlas(width,height));}
  /** The part of the footer panels may use: from the left edge to 84 px short of the frame's right, where '?' and Done stand. */
  public int footerEnd(){return OfficeGrid.footerEnd(width,height);}
  /** Equal slots left to right; null when the slot would reach the help and Done buttons, so the caller shows the action in its row instead. */
  public Rect footerSlot(int index,int slotW){var f=footer();int x=f.x()+index*(slotW+4);return x+slotW>footerEnd()?null:new Rect(x,f.y(),slotW,18);}
  /** Slots of different widths, placed one after another. */
  public Flow flow(){return new Flow(this);}
  /** Only the smallest class (XS, under 360 wide) is narrow; at 240 high nothing stacks vertically, secondary blocks go into tooltips. */
  public boolean narrow(){return size()==OfficeGrid.Size.XS;}
  public OfficeGrid.Size size(){return OfficeGrid.size(width);}
  public boolean tall(){return OfficeGrid.tall(height);}
  public Rect[] columns(Rect area,int minW,int gap,int max){var b=OfficeGrid.columns(box(area),minW,gap,max);var out=new Rect[b.length];for(int i=0;i<b.length;i++)out[i]=rect(b[i]);return out;}
  public Rect[] split(Rect area,int gap,int... weights){var b=OfficeGrid.split(box(area),gap,weights);var out=new Rect[b.length];for(int i=0;i<b.length;i++)out[i]=rect(b[i]);return out;}
  public Rect[] stack(Rect area,int gap,int... heights){var b=OfficeGrid.stack(box(area),gap,heights);var out=new Rect[b.length];for(int i=0;i<b.length;i++)out[i]=rect(b[i]);return out;}
 }
 public static final class Flow {
  private final Layout l;private int x;
  Flow(Layout l){this.l=l;x=l.footer().x();}
  public Rect next(int w){if(x+w>l.footerEnd())return null;var r=new Rect(x,l.footer().y(),w,18);x+=w+4;return r;}
 }

 // ---- probe recorders: only filled for the office UI probe, so an ordinary game pays nothing.
 static final boolean RECORD=Boolean.getBoolean("villageastra.officeUiSmoke")||Boolean.getBoolean("villageastra.researchFactsSmoke")||Boolean.getBoolean("villageastra.coreSmoke")||Boolean.getBoolean("villageastra.farmLevelsSmoke");
 public record Drawn(int section,int count,Tone tone,Rect rect){}
 public static final List<Rect> DRAWN_TEXT=new ArrayList<>();
 public static final List<String> DRAWN_STRINGS=new ArrayList<>();
 public static final List<Drawn> BADGES=new ArrayList<>();
 /** AD-124: the boxes drawn (chips, tags, bars, item cells, pips, tree cells) with the block they belong to (-1 none) and their layer
  *  (0 a card behind, 1 content); the lines shortened with '…' (each must lie under a tooltip); the tooltip regions of the last frame;
  *  the blocks a panel marked (a tile, a row, a card) and the frame drawn. The probe's layout rules read them. */
 public record Boxed(Rect rect,int block,int layer,String what){}
 public static final List<Boxed> BOXES=new ArrayList<>();
 public static final List<Rect> CUT=new ArrayList<>(),LAST_TIPS=new ArrayList<>(),BLOCK_RECTS=new ArrayList<>();
 public static final List<String> BLOCK_NAMES=new ArrayList<>();
 public static Rect drawnFrame;
 private static int block=-1;
 /** Frames drawn by frame(), and the screen that drew the last one: a screen that never called it is not on the kit. */
 public static int frames;public static Object frameScreen;
 private static final Deque<Rect> CLIPS=new ArrayDeque<>();
 private static Rect clipped(Rect r){var clip=CLIPS.peek();if(clip!=null)r=r.intersect(clip);return r.w()<=0||r.h()<=0?null:r;}
 private static void record(String s,int x,int y,int w){if(!RECORD||s.isEmpty())return;var r=clipped(new Rect(x,y,w,9));if(r==null)return;DRAWN_TEXT.add(r);DRAWN_STRINGS.add(s);}
 /** Marks the start of a logical block of a panel (a tile, a list row, a card): content boxes of different blocks must never overlap. */
 public static void block(String name,Rect r){if(!RECORD)return;BLOCK_NAMES.add(name);BLOCK_RECTS.add(r);block=BLOCK_RECTS.size()-1;}
 public static void endBlock(){block=-1;}
 static void box(Rect r,int layer,String what){if(!RECORD)return;var c=clipped(r);if(c!=null)BOXES.add(new Boxed(c,block,layer,what));}
 private static void cut(Rect r,List<Component> full){
  if(RECORD){var c=clipped(r);if(c!=null)CUT.add(c);}
  for(var have:TIPS.regions)if(have.r.containsRect(r))return;TIPS.add(r.x(),r.y(),r.w(),r.h(),full);
 }
 /** AD-124: a line of text at most w wide; when it has to be shortened with '…' the full text becomes its tooltip. Returns x plus the width drawn. */
 public static int label(GuiGraphics g,Font f,Component t,int x,int y,int w,int colour){return label(g,f,t.getString(),x,y,w,colour,List.of(t));}
 public static int label(GuiGraphics g,Font f,String s,int x,int y,int w,int colour){return label(g,f,s,x,y,w,colour,List.of(Component.literal(s)));}
 public static int label(GuiGraphics g,Font f,String s,int x,int y,int w,int colour,List<Component> full){
  String shown=fit(f,s,Math.max(0,w));int end=shown.isEmpty()?x:drawText(g,f,shown,x,y,colour);
  if(!shown.equals(s)&&!s.isEmpty())cut(new Rect(x,y-1,Math.max(1,Math.max(end-x,Math.min(w,8))),11),full);
  return end;
 }
 /** Every panel draws its text through here, so the probe sees where each string went. Returns x plus the width. */
 public static int drawText(GuiGraphics g,Font f,Component t,int x,int y,int colour){int w=f.width(t);g.drawString(f,t,x,y,colour,false);record(t.getString(),x,y,w);return x+w;}
 public static int drawText(GuiGraphics g,Font f,String t,int x,int y,int colour){int w=f.width(t);g.drawString(f,t,x,y,colour,false);record(t,x,y,w);return x+w;}
 static int drawShadow(GuiGraphics g,Font f,String t,int x,int y,int colour){int w=f.width(t);g.drawString(f,t,x,y,colour,true);record(t,x,y,w);return x+w;}
 /** A clipped area (scissor) that the text recorder also knows about. */
 public static void clip(GuiGraphics g,Rect r){var c=CLIPS.peek();CLIPS.push(c==null?r:c.intersect(r));g.enableScissor(r.x(),r.y(),r.right(),r.bottom());}
 public static void unclip(GuiGraphics g){if(!CLIPS.isEmpty())CLIPS.pop();g.disableScissor();}

 // ---- pixel signs and arrows: drawn with fill(), not unifont glyphs, so they stay sharp at every GUI scale.
 private static final String[][] SIGNS={
  {".....","....#","...#.","#.#..",".#..."},      // OK: check
  {"#####",".#.#.","..#..",".#.#.","#####"},      // WAIT: hourglass
  {"#...#",".#.#.","..#..",".#.#.","#...#"},      // BAD: cross
  {"..#..","...#.","#####","...#.","..#.."},      // ACTION: arrow
  {".###.",".#.#.","#####","##.##","#####"},      // OFF: lock
  {".....",".###.",".###.",".###.","....."}};     // INFO: dot
 private static final String[] ARROW_N={"...#...","..###..",".#.#.#.","#..#..#","...#...","...#...","...#..."},
  ARROW_NE={"..#####","....###","...#.##","..#...#",".#.....","#......","......."};
 private static final String[][] ARROWS=new String[8][];
 static{ARROWS[0]=ARROW_N;ARROWS[1]=ARROW_NE;for(int i=2;i<8;i++)ARROWS[i]=rotate(ARROWS[i-2]);}
 private static String[] rotate(String[] p){int n=p.length;var out=new String[n];for(int r=0;r<n;r++){var b=new StringBuilder();for(int c=0;c<n;c++)b.append(p[n-1-c].charAt(r));out[r]=b.toString();}return out;}
 private static void pattern(GuiGraphics g,String[] p,int x,int y,int colour){for(int r=0;r<p.length;r++)for(int c=0;c<p[r].length();c++)if(p[r].charAt(c)=='#')g.fill(x+c,y+r,x+c+1,y+r+1,colour);}
 /** The 5x5 sign of a tone. Returns its width. */
 public static int sign(GuiGraphics g,int x,int y,Tone t){pattern(g,SIGNS[t.ordinal()],x,y,t.fg());return 5;}
 /** A 7x7 direction arrow, 0 = north, clockwise. Returns its width. */
 public static int arrow(GuiGraphics g,int x,int y,int dir,int colour){pattern(g,ARROWS[Math.floorMod(dir,8)],x,y,colour);return 7;}
 /** A '›' drawn in pixels: the row opens something. */
 static void chevron(GuiGraphics g,int x,int y,int colour){pattern(g,new String[]{"#..",".#.","..#",".#.","#.."},x,y,colour);}
 public static int bearingIndex(BlockPos from,BlockPos to){double a=Math.atan2(to.getX()-from.getX(),-(to.getZ()-from.getZ()));return Math.floorMod((int)Math.round(a/(Math.PI/4)),8);}
 /** The arrow glyph of a bearing, only for tooltips and chat; screens draw arrow(). */
 public static String bearing(BlockPos from,BlockPos to){return String.valueOf("↑↗→↘↓↙←↖".charAt(bearingIndex(from,to)));}
 public static Component direction(BlockPos from,BlockPos to){return Component.translatable("office.villageastra.dir."+new String[]{"n","ne","e","se","s","sw","w","nw"}[bearingIndex(from,to)]);}

 // ---- frame and building blocks
 static void icon12(GuiGraphics g,ItemStack icon,int x,int y){if(icon==null||icon.isEmpty())return;g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.75f,.75f,1);g.renderItem(icon,0,0);g.pose().popPose();}
 /** Draws the office frame and returns the x where the role tag ends (the header's summary may follow it). */
 public static int frame(GuiGraphics g,Font f,Layout l,ItemStack icon,Component title,Component role,Tone roleTone){
  DRAWN_TEXT.clear();DRAWN_STRINGS.clear();BADGES.clear();CLIPS.clear();TIPS.clear();BOXES.clear();CUT.clear();BLOCK_RECTS.clear();BLOCK_NAMES.clear();block=-1;
  frames++;frameScreen=Minecraft.getInstance().screen;
  int w=l.width,h=l.height;var fr=l.frame();var hd=l.header();drawnFrame=fr;int x=fr.x()+26,y=fr.y();
  g.fill(0,0,w,h,DIM);g.fill(fr.x(),fr.y(),fr.right(),fr.bottom(),FRAME);g.fill(fr.x()+1,fr.y()+1,fr.right()-1,fr.bottom()-1,BODY);g.fill(hd.x(),hd.y(),hd.right(),hd.bottom(),HEADER);
  if(icon!=null&&!icon.isEmpty())g.renderItem(icon,fr.x()+6,y+4);
  label(g,f,title,x,y+3,l.atlas().x()-8-x,TITLE);
  int end=x;if(role!=null&&!role.getString().isEmpty())end=x+tag(g,f,x,y+13,role,roleTone,l.atlas().x()-8-x);
  g.fill(fr.x()+1,OfficeGrid.footerLine(w,h),fr.right()-1,OfficeGrid.footerLine(w,h)+1,HEADER);
  return end;
 }
 public static int header(GuiGraphics g,Font f,int x,int y,int w,ItemStack icon,Component text){
  int tx=x;if(icon!=null&&!icon.isEmpty()){icon12(g,icon,x,y);tx=x+14;}
  int end=label(g,f,text,tx,y+2,x+w-tx,TITLE);if(end+4<x+w)g.fill(end+4,y+6,x+w,y+7,HEADER);return y+14;
 }
 /** The width chip() would take for this content when asked for its own width (w=0). */
 public static int chipWidth(Font f,ItemStack icon,Component text,Component value,boolean link){
  boolean hasIcon=icon!=null&&!icon.isEmpty();String v=value==null?"":value.getString();return 13+(hasIcon?14:0)+f.width(text)+(v.isEmpty()?0:f.width(v)+6)+4+(link?8:0);
 }
 public static int chip(GuiGraphics g,Font f,int x,int y,ItemStack icon,Component text,Tone tone){return chip(g,f,x,y,0,icon,text,Component.empty(),tone);}
 public static int chip(GuiGraphics g,Font f,int x,int y,int w,ItemStack icon,Component text,Component value,Tone tone){return chip(g,f,x,y,w,icon,text,value,tone,false,false);}
 /** An 18 px status row: stripe, sign, icon, text and a right-aligned value in the tone's colour. A link chip shows '›' and brightens under the mouse. */
 public static int chip(GuiGraphics g,Font f,int x,int y,int w,ItemStack icon,Component text,Component value,Tone tone,boolean link,boolean hover){
  boolean hasIcon=icon!=null&&!icon.isEmpty();String v=value==null?"":value.getString();int vw=v.isEmpty()?0:f.width(v)+6;
  int lead=13+(hasIcon?14:0),tail=4+(link?8:0);if(w<=0)w=lead+f.width(text)+vw+tail;
  g.fill(x,y,x+w,y+18,hover?brighten(tone.bg()):tone.bg());g.fill(x,y,x+3,y+18,tone.fg());if(hover)g.renderOutline(x,y,w,18,tone.fg());box(new Rect(x,y,w,18),1,"chip");
  sign(g,x+6,y+7,tone);if(hasIcon)icon12(g,icon,x+13,y+3);
  // AD-124: the value (a number) is never cut; the label gives way first, and a shortened label keeps its full text as a tooltip.
  int room=w-lead-vw-tail;String full=text.getString();
  if(room>4)label(g,f,full,x+lead,y+5,room,TEXT,List.of(text));else if(!full.isEmpty())cut(new Rect(x,y,w,18),List.of(text));
  if(!v.isEmpty())drawText(g,f,v,x+w-tail-f.width(v),y+5,tone.fg());
  if(link)chevron(g,x+w-7,y+7,hover?TEXT:MUTED);
  return w;
 }
 private static int brighten(int c){return (c&0xFF000000)|Math.min(0xFF,((c>>16)&0xFF)+16)<<16|Math.min(0xFF,((c>>8)&0xFF)+16)<<8|Math.min(0xFF,(c&0xFF)+16);}
 public static int tagWidth(Font f,Component text){return 12+f.width(text);}
 /** An 11 px pill with the tone's sign: stages, reasons, roles and deltas. */
 public static int tag(GuiGraphics g,Font f,int x,int y,Component text,Tone tone){return tag(g,f,x,y,text,tone,Integer.MAX_VALUE);}
 /** A tag at most maxW wide: a longer text is shortened with '…' and shown whole in its tooltip. */
 public static int tag(GuiGraphics g,Font f,int x,int y,Component text,Tone tone,int maxW){
  int w=Math.min(tagWidth(f,text),Math.max(12,maxW));g.fill(x,y,x+w,y+11,tone.bg());g.renderOutline(x,y,w,11,tone.fg());sign(g,x+3,y+3,tone);box(new Rect(x,y,w,11),1,"tag");
  label(g,f,text.getString(),x+10,y+2,w-12,tone.fg(),List.of(text));return w;
 }
 public static void bar(GuiGraphics g,int x,int y,int w,long value,long max,Tone tone){bar(g,x,y,w,5,value,max,tone);}
 public static void bar(GuiGraphics g,int x,int y,int w,int h,long value,long max,Tone tone){g.fill(x,y,x+w,y+h,TRACK);int fill=fill(w,value,max);if(fill>0)g.fill(x,y,x+fill,y+h,tone.bar());box(new Rect(x,y,w,h),1,"bar");}
 public static void bar(GuiGraphics g,Font f,int x,int y,int w,long value,long max,Tone tone,Component label){
  g.fill(x,y,x+w,y+11,TRACK);int fill=fill(w,value,max);if(fill>0)g.fill(x,y,x+fill,y+11,tone.bar());box(new Rect(x,y,w,11),1,"bar");
  if(label!=null){String full=label.getString(),s=fit(f,full,w-4);drawShadow(g,f,s,x+(w-f.width(s))/2,y+2,TEXT);if(!s.equals(full))cut(new Rect(x,y,w,11),List.of(label));}
 }
 private static int fill(int w,long value,long max){return max<=0?0:(int)Math.max(0,Math.min(w,w*Math.max(0,value)/max));}
 public static void marker(GuiGraphics g,int x,int y,int w,long at,long max){if(max<=0)return;int mx=x+(int)Math.min(w-1,w*Math.max(0,at)/max);g.fill(mx,y-1,mx+1,y+12,TITLE);}
 /** Level pips: working levels green, built but not working amber, the rest outlined grey. */
 public static int pips(GuiGraphics g,int x,int y,int level,int kept,int max){
  for(int i=0;i<max;i++){int px=x+i*7;if(i<level)g.fill(px,y,px+5,y+5,Tone.OK.fg());else if(i<kept)g.fill(px,y,px+5,y+5,Tone.WAIT.fg());else g.renderOutline(px,y,5,5,Tone.OFF.fg());}
  if(max>0)box(new Rect(x,y,max*7-2,5),1,"pips");
  return Math.max(0,max*7-2);
 }
 private static final String[] ROMAN={"0","I","II","III","IV","V","VI","VII","VIII","IX","X"};
 public static Component roman(int n){return Component.literal(n>=0&&n<ROMAN.length?ROMAN[n]:String.valueOf(n));}
 public static int itemCount(GuiGraphics g,Font f,ItemStack stack,int x,int y,int count,Tone tone){
  g.renderItem(stack,x,y);int end=drawText(g,f,String.valueOf(count),x+18,y+4,tone.fg());tips().add(x,y,end-x,16,List.of(stack.getHoverName()));return end-x;
 }
 /** An item cell that shows only what is missing (a red '-12', ASCII: the pixel font has no U+2212) or a green check; the whole count is in the tooltip. */
 public static int itemNeed(GuiGraphics g,Font f,ItemStack stack,int x,int y,int have,int need){
  return itemCell(g,f,stack,x,y,Math.max(0,need-have),have,need,List.of(Component.literal(have+" / "+need)));
 }
 public static int itemCell(GuiGraphics g,Font f,ItemStack stack,int x,int y,int missing,long have,long need,List<Component> details){
  g.renderItem(stack,x,y);g.fill(x,y+17,x+16,y+19,TRACK);int bw=fill(16,Math.min(have,need),need);if(bw>0)g.fill(x,y+17,x+bw,y+19,missing>0?Tone.BAD.bar():Tone.OK.bar());
  int end;if(missing>0)end=drawText(g,f,"-"+missing,x+18,y+4,Tone.BAD.fg());else end=x+18+sign(g,x+18,y+6,Tone.OK);
  var lines=new ArrayList<Component>();lines.add(stack.getHoverName());lines.addAll(details);tips().add(x,y,Math.max(20,end-x),19,lines);box(new Rect(x,y,Math.max(16,end-x),19),1,"item");return end-x;
 }
 /** AD-124: a change over the last days — a pixel arrow (up, down, or '=' for none) and the signed number, green when the change is good.
  *  Without history a grey '–' with 'no data yet'. Returns the width. */
 /** delta's sign picks the arrow and colour; text is its size as shown ('25', '1,5 дн.'); tip names the span. */
 public record Trend(long delta,boolean upIsGood,Component tip,String text){}
 private static String trendText(Trend t){return (t.delta()>0?"+":t.delta()<0?"-":"")+t.text();}
 /** Without history: a grey '–' that says so in its tooltip. */
 public static Trend noTrend(){return new Trend(0,true,null,null);}
 public static int trend(GuiGraphics g,Font f,int x,int y,Trend t){
  if(t==null||t.text()==null){int end=drawText(g,f,"–",x,y+1,Tone.OFF.fg());tips().add(x,y,end-x+1,10,List.of(Component.translatable("office.villageastra.v2.trend_none")));return end-x;}
  int colour=t.delta()==0?MUTED:(t.delta()>0)==t.upIsGood()?Tone.OK.fg():Tone.BAD.fg();int cx=x;
  if(t.delta()==0)cx=drawText(g,f,"=",cx,y+1,colour)+1;else{arrow(g,cx,y+1,t.delta()>0?0:4,colour);cx+=8;}
  cx=drawText(g,f,trendText(t),cx,y+1,colour);
  if(t.tip()!=null)tips().add(x,y,cx-x,10,List.of(t.tip()));return cx-x;
 }
 public static int trendWidth(Font f,Trend t){return t==null||t.text()==null?f.width("–"):(t.delta()==0?f.width("=")+1:8)+f.width(trendText(t));}
 /** AD-124: one KPI tile of the Overview. Its parts stack from the top and each takes only what it draws: the chip (icon, label, value),
  *  a thin bar, a line with a tag and the trend, and a muted hint. A part that no longer fits the tile's height goes into the tile's tooltip. */
 public record Tile(ItemStack icon,Component label,Component value,Tone tone,long barValue,long barMax,Component tag,Tone tagTone,Trend trend,Component hint,List<Component> tips){}
 public static int tile(GuiGraphics g,Font f,Rect r,Tile t,boolean link,boolean hover){
  var more=new ArrayList<Component>(t.tips());
  g.fill(r.x(),r.y(),r.right(),r.bottom(),CARD);box(r,0,"tile");
  // A short tile has no line for the trend: the change then follows the value in the chip ('120 +25').
  int afterBar=r.y()+18+(t.barMax()>0&&r.y()+23<=r.bottom()?5:0);boolean lineFits=afterBar+13<=r.bottom();
  var value=t.value();if(!lineFits&&t.trend()!=null&&t.trend().text()!=null){value=(value==null?Component.empty():value.copy()).append(" "+trendText(t.trend()));if(t.trend().tip()!=null)more.add(t.trend().tip());}
  chip(g,f,r.x(),r.y(),r.w(),t.icon(),t.label(),value,t.tone(),link,hover);int y=r.y()+18;
  if(t.barMax()>0){if(y+5<=r.bottom()){bar(g,r.x()+3,y+1,r.w()-6,4,t.barValue(),t.barMax(),t.tone()==Tone.OFF||t.tone()==Tone.INFO?Tone.OK:t.tone());y+=5;}else more.add(Component.literal(t.barValue()+" / "+t.barMax()));}
  if(t.tag()!=null||t.trend()!=null){
   if(y+13<=r.bottom()){int tw=t.trend()==null?0:trendWidth(f,t.trend());int room=r.w()-6-(tw>0?tw+6:0);
    if(t.tag()!=null&&room>=16)tag(g,f,r.x()+3,y+2,t.tag(),t.tagTone()==null?Tone.INFO:t.tagTone(),room);else if(t.tag()!=null)more.add(t.tag());
    if(t.trend()!=null)trend(g,f,r.right()-3-tw,y+2,t.trend());y+=13;}
   else if(t.tag()!=null)more.add(t.tag());}
  if(t.hint()!=null){if(y+11<=r.bottom()){label(g,f,t.hint(),r.x()+3,y+2,r.w()-6,MUTED);y+=11;}else more.add(t.hint());}
  if(!more.isEmpty()){more.add(0,t.value()==null||t.value().getString().isEmpty()?t.label():t.label().copy().append(": ").append(t.value()));tips().add(r.x(),r.y(),r.w(),18,more);}
  return y-r.y();
 }
 /** A count or dot on a tab's top-right corner (inside its width, so the last tab's badge stays in the frame): the worst tone of the tab's attention items. Fine and unavailable are never drawn. */
 public static void badge(GuiGraphics g,Font f,AbstractWidget tab,int count,Tone tone){
  if(tone==null||tone==Tone.OK||tone==Tone.OFF)return;
  String s=count>0?String.valueOf(count):"";int w=count<=0?5:count>9?f.width(s)+3:8,h=count<=0?5:8,x=tab.getX()+tab.getWidth()-w-1,y=tab.getY()-3;
  g.pose().pushPose();g.pose().translate(0,0,300);
  g.fill(x-1,y-1,x+w+1,y+h+1,BODY);g.fill(x,y,x+w,y+h,tone.bar());if(!s.isEmpty())g.drawString(f,s,x+(w-f.width(s)+1)/2,y+1,0xFFFFFFFF,false);
  g.pose().popPose();
  if(RECORD)BADGES.add(new Drawn(tab instanceof TabButton t?t.section:-1,count,tone,new Rect(x-1,y-1,w+2,h+2)));
 }
 public static void row(GuiGraphics g,int x,int y,int w,int h,boolean selected,boolean hover,Tone status){
  g.fill(x,y,x+w,y+h,selected?ROW_SELECTED:hover?ROW_HOVER:CARD);if(selected)g.fill(x,y,x+2,y+h,FRAME);box(new Rect(x,y,w,h),0,"row");
  if(status!=null&&status!=Tone.OFF&&status!=Tone.INFO)g.fill(x+w-7,y+(h-4)/2,x+w-3,y+(h-4)/2+4,status.fg());
 }

 /** A list that scrolls by whole rows with the wheel and shows a thin scrollbar only when it overflows. */
 public static final class Scroll {
  private final int rowH;private Rect area=new Rect(0,0,0,0);private int rows,first;
  public Scroll(int rowH){this.rowH=rowH;}
  public void bounds(Rect area,int rows){this.area=area;this.rows=rows;first=Math.max(0,Math.min(first,rows-visible()));}
  public Rect area(){return area;}
  public int rowH(){return rowH;}
  public int first(){return first;}
  public int visible(){return Math.max(1,area.h()/rowH);}
  public boolean overflows(){return rows>visible();}
  /** The width a row may use: the scrollbar takes the right edge when it is drawn. */
  public int rowW(){return area.w()-(overflows()?5:0);}
  public int y(int index){return index<first||index>=first+visible()||index>=rows?-1:area.y()+(index-first)*rowH;}
  public boolean wheel(double mx,double my,double delta){if(!area.contains(mx,my))return false;int before=first;first=Math.max(0,Math.min(rows-visible(),first-(int)Math.signum(delta)));return before!=first||overflows();}
  public void render(GuiGraphics g){if(!overflows())return;int x=area.right()-3;g.fill(x,area.y(),x+3,area.bottom(),TRACK);int th=Math.max(8,area.h()*visible()/rows),ty=area.y()+(area.h()-th)*first/Math.max(1,rows-visible());g.fill(x,ty,x+3,ty+th,MUTED);}
  public int hit(double mx,double my){if(!area.contains(mx,my)||mx>=area.x()+rowW())return -1;int i=first+(int)((my-area.y())/rowH);return i<rows&&i<first+visible()?i:-1;}
  public void reveal(int index){if(index<first)first=index;else if(index>=first+visible())first=index-visible()+1;first=Math.max(0,Math.min(first,Math.max(0,rows-visible())));}
 }

 /** Tooltip regions collected while a frame draws, shown once at its end. */
 public static final class Tips {
  private record Region(Rect r,List<Component> lines){}
  private final List<Region> regions=new ArrayList<>();
  public void add(int x,int y,int w,int h,List<Component> lines){if(lines!=null&&!lines.isEmpty())regions.add(new Region(new Rect(x,y,w,h),lines));}
  public void add(Rect r,Component... lines){add(r.x(),r.y(),r.w(),r.h(),List.of(lines));}
  public void clear(){regions.clear();}
  /** AD-124: the count of regions so far; limit(mark,area) then trims the regions added since to a visible area (a scrolled card). */
  public int mark(){return regions.size();}
  public void limit(int from,Rect area){for(int i=regions.size()-1;i>=from&&i>=0;i--){var r=regions.get(i);var in=r.r.intersect(area);if(in.w()<=0||in.h()<=0)regions.remove(i);else regions.set(i,new Region(in,r.lines));}}
  /** Shows the region under the mouse and empties the list: a screen outside the office frame (the Atlas card) never piles regions up. */
  public void render(GuiGraphics g,Font f,int mx,int my){
   if(RECORD){LAST_TIPS.clear();for(var r:regions)LAST_TIPS.add(r.r);}
   Region hit=null;for(var r:regions)if(r.r.contains(mx,my))hit=r;regions.clear();if(hit==null)return;
   var lines=new ArrayList<FormattedCharSequence>();for(var c:hit.lines)lines.addAll(f.split(c,200));g.renderTooltip(f,lines,mx,my);
  }
  /** A vanilla widget tooltip wins: two tooltips never show at once. */
  public void render(GuiGraphics g,Font f,int mx,int my,Screen s){if(RECORD){LAST_TIPS.clear();for(var r:regions)LAST_TIPS.add(r.r);}for(var c:s.children())if(c instanceof AbstractWidget w&&w.visible&&w.isHovered()&&w.getTooltip()!=null){regions.clear();return;}render(g,f,mx,my);}
 }
 private static final Tips TIPS=new Tips();
 public static Tips tips(){return TIPS;}

 /** A flat 18 px office button. Its message stays the label (narration, probes); an inactive one names its reason in the tooltip,
  *  and a confirm button needs a second click within 3 s. */
 public static class OfficeButton extends Button {
  private ItemStack icon=ItemStack.EMPTY;private Tone tone=Tone.INFO;private Component reason,hint,ask;private long armedUntil;private Component shownTip;
  protected OfficeButton(Component label,Button.OnPress press){super(0,0,60,18,label,press,DEFAULT_NARRATION);}
  public static OfficeButton of(Component label,ItemStack icon,Tone tone,Button.OnPress press){var b=new OfficeButton(label,press);b.icon=icon==null?ItemStack.EMPTY:icon;b.tone=tone;return b;}
  public OfficeButton tone(Tone t){tone=t;return this;}
  public Tone tone(){return tone;}
  public OfficeButton icon(ItemStack i){icon=i==null?ItemStack.EMPTY:i;return this;}
  public OfficeButton reason(Component whyDisabled){reason=whyDisabled;return this;}
  public Component reason(){return reason;}
  public OfficeButton hint(Component h){hint=h;return this;}
  public OfficeButton confirm(Component ask){this.ask=ask;return this;}
  public boolean armed(){return ask!=null&&Util.getMillis()<armedUntil;}
  public void disarm(){armedUntil=0;}
  public OfficeButton at(Rect r){if(r==null){visible=false;return this;}setX(r.x());setY(r.y());setWidth(r.w());height=r.h();return this;}
  public int prefWidth(Font f){return 10+(icon.isEmpty()?0:14)+Math.max(f.width(getMessage()),ask==null?0:f.width(ask));}
  @Override public void onPress(){if(ask!=null&&!armed()){armedUntil=Util.getMillis()+3000;return;}disarm();super.onPress();}
  @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
   var tip=active?hint:reason!=null?reason:hint;if(tip!=shownTip){shownTip=tip;setTooltip(tip==null?null:Tooltip.create(tip));}
   boolean arm=armed();Tone t=!active?Tone.OFF:arm?Tone.BAD:tone;var f=Minecraft.getInstance().font;int x=getX(),y=getY(),w=getWidth(),h=getHeight();
   g.fill(x,y,x+w,y+h,active&&isHoveredOrFocused()?brighten(t.bg()):t.bg());g.renderOutline(x,y,w,h,active&&isHoveredOrFocused()?brighten(t.fg()):t.fg());
   int lx=x+4;if(!icon.isEmpty()){icon12(g,icon,x+3,y+(h-12)/2);lx=x+17;}else if(!active&&reason!=null){sign(g,x+4,y+(h-5)/2,Tone.OFF);lx=x+11;}
   var label=arm?ask:getMessage();String s=fit(f,label.getString(),x+w-3-lx);int tw=f.width(s);int tx=icon.isEmpty()&&(active||reason==null)?x+(w-tw)/2:lx+Math.max(0,(x+w-3-lx-tw)/2);
   g.drawString(f,s,tx,y+(h-8)/2,active?TEXT:Tone.OFF.fg(),false);
  }
 }
 /** A tab of the office: label-only, icon with the active tab's label, or icon-only with the label in its tooltip. */
 public static final class TabButton extends Button {
  final int section;private final ItemStack icon;private boolean active,showLabel=true,showIcon;private Component shownTip;
  public TabButton(int section,Component label,ItemStack icon,Button.OnPress p){super(0,0,22,18,label,p,DEFAULT_NARRATION);this.section=section;this.icon=icon;}
  public int section(){return section;}
  public boolean selected(){return active;}
  public boolean showsLabel(){return showLabel;}
  public void state(boolean active,boolean showLabel){state(active,showLabel,!showLabel);}
  public void state(boolean active,boolean showLabel,boolean showIcon){this.active=active;this.showLabel=showLabel;this.showIcon=showIcon;}
  public int prefWidth(Font f,boolean showLabel){return prefWidth(f,showLabel,!showLabel);}
  int prefWidth(Font f,boolean label,boolean icon){return label&&icon?22+f.width(getMessage())+6:label?f.width(getMessage())+10:22;}
  @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
   var tipText=Component.translatable("office.villageastra.tab_tip."+section);Component tip=showLabel?tipText:getMessage().copy().append(" — ").append(tipText);
   if(tip!=shownTip&&!tip.equals(shownTip)){shownTip=tip;setTooltip(Tooltip.create(tip));}
   var f=Minecraft.getInstance().font;int x=getX(),y=getY(),w=getWidth(),h=getHeight();
   g.fill(x,y,x+w,y+h,active?BODY:isHoveredOrFocused()?ROW_HOVER:CARD);if(active){g.fill(x,y+h,x+w,y+h+2,TITLE);g.renderOutline(x,y,w,h,HEADER);}
   int lx=x+(w-f.width(getMessage()))/2;if(showIcon){g.renderItem(icon,x+3,y+1);lx=x+22;}
   if(showLabel)g.drawString(f,fit(f,getMessage().getString(),x+w-lx-2),lx,y+5,active?TITLE:TEXT,false);
  }
 }
 /** All labels when they fit; else icons with the active tab's label; else icons only. */
 public static void layoutTabs(List<TabButton> tabs,Font f,Rect row,int active){
  int gap=2,gaps=gap*Math.max(0,tabs.size()-1),labels=gaps,mixed=gaps;
  for(var t:tabs){labels+=t.prefWidth(f,true,false);mixed+=t.section==active?t.prefWidth(f,true,true):22;}
  int mode=labels<=row.w()?0:mixed<=row.w()?1:2,extra=mode==0?(row.w()-labels)/Math.max(1,tabs.size()):0,x=row.x();
  for(var t:tabs){boolean on=t.section==active;boolean label=mode==0||mode==1&&on,icon=mode>0;t.state(on,label,icon);
   int w=mode==0?t.prefWidth(f,true,false)+extra:mode==1&&on?t.prefWidth(f,true,true):22;t.setX(x);t.setY(row.y());t.setWidth(w);x+=w+gap;}
 }

 // ---- icons and words
 public static ItemStack icon(String itemId){try{var item=BuiltInRegistries.ITEM.get(new ResourceLocation(itemId));return new ItemStack(item);}catch(RuntimeException ex){return new ItemStack(Items.BARRIER);}}
 private static final Map<String,net.minecraft.world.item.Item> BUILDINGS=Map.ofEntries(
  Map.entry("town_hall",Items.BELL),Map.entry("home",Items.RED_BED),Map.entry("house",Items.RED_BED),Map.entry("farm",Items.WHEAT),Map.entry("forester",Items.OAK_SAPLING),
  Map.entry("mine",Items.IRON_PICKAXE),Map.entry("quarry",Items.STONE_PICKAXE),Map.entry("mill",Items.GRINDSTONE),Map.entry("bakery",Items.BREAD),Map.entry("restaurant",Items.BREAD),Map.entry("barracks",Items.IRON_SWORD),
  Map.entry("guard_house",Items.SHIELD),Map.entry("archery",Items.TARGET),Map.entry("wall_tower",Items.BOW),Map.entry("school",Items.BOOK),Map.entry("laboratory",Items.BREWING_STAND),
  Map.entry("cartographer",Items.CARTOGRAPHY_TABLE),Map.entry("smithy",Items.ANVIL),Map.entry("expedition",Items.COMPASS),Map.entry("warehouse",Items.BARREL),Map.entry("livestock",Items.LEAD),
  Map.entry("masonry",Items.STONECUTTER),Map.entry("carpentry",Items.CRAFTING_TABLE),Map.entry("carpentry_annex",Items.CRAFTING_TABLE),Map.entry("mill_annex",Items.GRINDSTONE),Map.entry("kennel_annex",Items.BONE),Map.entry("masonry_annex",Items.STONECUTTER),Map.entry("caravan",Items.CHEST_MINECART),Map.entry("clinic",Items.GLISTERING_MELON_SLICE),
  Map.entry("engineering",Items.PISTON),Map.entry("siege_camp",Items.TNT));
 public static ItemStack buildingIcon(String type){return new ItemStack(BUILDINGS.getOrDefault(type,Items.OAK_PLANKS));}
 /** Tab icons: the hall's bell for the overview, a bottle of experience for levels (an anvil read as 'smithy'). */
 public static ItemStack tabIcon(int section){return new ItemStack(switch(section){case 0->Items.SCAFFOLDING;case 1->Items.GOLDEN_HELMET;case 2->Items.OAK_DOOR;case 3->Items.ENCHANTED_BOOK;case 4->Items.WRITABLE_BOOK;case 5->Items.IRON_SWORD;case 6->Items.EXPERIENCE_BOTTLE;default->Items.BELL;});}
 static boolean russian(){var mc=Minecraft.getInstance();return mc!=null&&mc.getLanguageManager()!=null&&mc.getLanguageManager().getSelected().startsWith("ru");}
 /** A research node by its title in the player's language; a raw id only as a last resort, greyed. */
 public static Component research(String id){var n=ResearchCatalog.NODES.get(id);if(n==null)return Component.literal(id).withStyle(s->s.withColor(Tone.OFF.fg()&0xFFFFFF));return org.villageastra.world.ResearchEffects.title(id);}
 static String decimal(double v){var s=String.valueOf(Math.round(v*10)/10.0);if(s.endsWith(".0"))s=s.substring(0,s.length()-2);return russian()?s.replace('.',','):s;}
 /** 45 с / 12 мин / 1,5 дн. — a day is 24000 ticks, a minute 1200. */
 public static Component duration(long ticks){ticks=Math.max(0,ticks);
  if(ticks<1200)return Component.translatable("office.villageastra.time_s",(ticks+19)/20);
  if(ticks<24000)return Component.translatable("office.villageastra.time_m",(ticks+1199)/1200);
  return Component.translatable("office.villageastra.time_d",decimal(ticks/24000.0));}
 public static Component days(double days){return Component.translatable("office.villageastra.time_d",decimal(days));}
 public static String fit(Font f,String s,int w){if(f.width(s)<=w)return s;if(w<=f.width("…"))return "";return f.plainSubstrByWidth(s,w-f.width("…"))+"…";}
 /** Disarms every confirm button of a screen: on a tab switch, init and close a stale armed button must never fire. */
 public static void disarm(Iterable<?> widgets){for(var w:widgets)if(w instanceof OfficeButton b)b.disarm();}
}
