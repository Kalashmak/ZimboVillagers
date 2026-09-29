package org.villageastra.client;
import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.villageastra.client.OfficeUi.*;
/** Shared steps of the office probes: open a tab, find a button by its label, click a widget the way a player does,
 *  and the layout rules every office tab must keep. */
final class OfficeProbes {
 private OfficeProbes(){}
 static void open(Minecraft mc,int section){mc.setScreen(new ConstructionScreen(section));}
 static Button button(Screen s,Component label){String want=label.getString();for(var c:s.children())if(c instanceof Button b&&b.visible&&b.getMessage().getString().equals(want))return b;return null;}
 /** Clicks the widget's centre through the screen and reports whether it was handled (an inactive button is not). */
 static boolean click(Screen s,AbstractWidget w){double x=w.getX()+w.getWidth()/2.0,y=w.getY()+w.getHeight()/2.0;boolean handled=s.mouseClicked(x,y,0);s.mouseReleased(x,y,0);return handled;}
 static void clickOrFail(Screen s,AbstractWidget w,String what){if(w==null||!w.visible)throw new IllegalStateException(what+" is not shown");if(!click(s,w))throw new IllegalStateException(what+" did not take the click");}
 private static final List<Pattern> RAW=List.of(Pattern.compile("^[a-z_]+\\.[0-9]+$"),Pattern.compile("\\b[a-z_]+\\.[1-9]\\b"),Pattern.compile("villageastra\\."),Pattern.compile("minecraft:"));
 /** The raw ids and unresolved keys a player must never read. */
 static String rawId(String s){for(var p:RAW)if(p.matcher(s).find())return s;return null;}
 private static String r(Rect r){return "("+r.x()+","+r.y()+" "+r.w()+"x"+r.h()+")";}
 private static String name(AbstractWidget w){return "'"+w.getMessage().getString()+"'"+r(Rect.of(w));}
 /** The layout rules of an office tab (the PROBE section of the design). An empty list means the tab is sound. */
 static List<String> layoutProblems(Screen s){
  var out=new ArrayList<String>();var l=new Layout(s.width,s.height);var inner=l.frame().inset(1);var hd=l.header();var tabRow=l.tabs();var content=l.content();var footer=l.footer();
  var reserved=new Rect(l.footerEnd(),footer.y(),footer.right()-l.footerEnd(),footer.h());
  if(OfficeUi.frames==0||OfficeUi.frameScreen!=s)out.add("no office frame drawn by "+s.getClass().getSimpleName());
  var widgets=new ArrayList<AbstractWidget>();var tabs=new ArrayList<TabButton>();
  for(var c:s.children())if(c instanceof AbstractWidget w&&w.visible){if(w instanceof TabButton t)tabs.add(t);
   widgets.add(w);}
  for(var w:widgets){var b=Rect.of(w);boolean corner=b.equals(l.done())||b.equals(l.help());
   if(!inner.containsRect(b))out.add("outside the frame: "+name(w));
   if(!hd.containsRect(b)&&!tabRow.containsRect(b)&&!content.containsRect(b)&&!footer.containsRect(b))out.add("outside header/tabs/content/footer: "+name(w));
   if(!corner&&b.intersects(reserved))out.add("covers the Done/? corner: "+name(w));
   if(b.bottom()>content.bottom()&&!footer.containsRect(b))out.add("below the content outside the footer: "+name(w));
   var raw=rawId(w.getMessage().getString());if(raw!=null)out.add("raw id in a widget: "+raw);}
  for(int i=0;i<widgets.size();i++)for(int j=i+1;j<widgets.size();j++)if(Rect.of(widgets.get(i)).intersects(Rect.of(widgets.get(j))))out.add("widgets overlap: "+name(widgets.get(i))+" and "+name(widgets.get(j)));
  for(var t:OfficeUi.DRAWN_TEXT){
   if(!inner.containsRect(t))out.add("text outside the frame "+r(t));
   if(!hd.containsRect(t)&&!tabRow.containsRect(t)&&!content.containsRect(t)&&!footer.containsRect(t))out.add("text outside header/tabs/content/footer "+r(t)+" '"+OfficeUi.DRAWN_STRINGS.get(OfficeUi.DRAWN_TEXT.indexOf(t))+"'");
   for(var w:widgets)if(Rect.of(w).intersects(t))out.add("text under a widget "+r(t)+" "+name(w));}
  for(var str:OfficeUi.DRAWN_STRINGS){var raw=rawId(str);if(raw!=null)out.add("raw id on screen: "+raw);}
  // The tab row: eight tabs in the display order, labelled as before, inside the row, apart, the active one showing its label.
  if(tabs.size()!=ConstructionScreen.TAB_ORDER.length)out.add("tabs: "+tabs.size()+" instead of "+ConstructionScreen.TAB_ORDER.length);
  for(int i=0;i<tabs.size();i++){var t=tabs.get(i);
   if(i<ConstructionScreen.TAB_ORDER.length&&t.section()!=ConstructionScreen.TAB_ORDER[i])out.add("tab "+i+" is section "+t.section());
   if(!t.getMessage().getString().equals(Component.translatable("hall.villageastra.tab_"+t.section()).getString()))out.add("tab label changed: "+name(t));
   if(!tabRow.containsRect(Rect.of(t)))out.add("tab outside the row: "+name(t));
   if(t.selected()&&!t.showsLabel())out.add("the active tab hides its label: "+name(t));}
  if(tabs.stream().filter(TabButton::selected).count()!=1)out.add("active tabs: "+tabs.stream().filter(TabButton::selected).count());
  // Badges sit on the tabs, inside the header band and the tab row, and say what OfficeAttention says.
  var band=new Rect(hd.x(),hd.y(),hd.w(),tabRow.bottom()-hd.y());var expected=OfficeAttention.byTab(OfficeAttention.needs(ConstructionOverlay.snapshot()));var seen=new HashSet<Integer>();
  for(var b:OfficeUi.BADGES){seen.add(b.section());if(!band.containsRect(b.rect()))out.add("badge outside the tab band: tab "+b.section()+" "+r(b.rect()));
   var e=expected.get(b.section());if(e==null||e.count()!=b.count()||e.tone()!=b.tone())out.add("badge of tab "+b.section()+" is "+b.count()+"/"+b.tone()+" but attention says "+e);}
  for(var e:expected.entrySet())if(e.getValue().tone()!=Tone.OK&&e.getValue().tone()!=Tone.OFF&&!seen.contains(e.getKey())&&e.getKey()!=ConstructionScreen.OVERVIEW)out.add("missing badge on tab "+e.getKey()+": "+e.getValue());
  // A panel or screen may add its own checks (ResearchScreen: canvas, details and label column; the list panels: row buttons).
  out.addAll(hook(s));if(s instanceof ConstructionScreen cs)out.addAll(hook(cs.panel(cs.section())));
  // AD-124: the rules of the adaptive office. The Quests tab is another group's code: its findings are reported as known-foreign, not hidden.
  int section=s instanceof ConstructionScreen cs?cs.section():s instanceof ResearchScreen?ConstructionScreen.RESEARCH:-1;
  var v2=adaptiveProblems(s,l,widgets,section);
  if(section==ConstructionScreen.QUESTS)for(var p:v2)out.add("known-foreign: "+p);else out.addAll(v2);
  return out;
 }
 /** Remarks that are not problems (a tab honestly short of content), for the probe's log. */
 static final List<String> NOTES=new ArrayList<>();
 private static List<String> adaptiveProblems(Screen s,Layout l,List<AbstractWidget> widgets,int section){
  var out=new ArrayList<String>();NOTES.clear();var content=l.content();
  // Text never overlaps text (the shadow of a line is the same rectangle).
  var text=OfficeUi.DRAWN_TEXT;
  for(int i=0;i<text.size();i++)for(int j=i+1;j<text.size();j++)if(text.get(i).intersects(text.get(j))&&!text.get(i).equals(text.get(j)))out.add("text overlaps text "+r(text.get(i))+" '"+OfficeUi.DRAWN_STRINGS.get(i)+"' and "+r(text.get(j))+" '"+OfficeUi.DRAWN_STRINGS.get(j)+"'");
  // Content boxes of different blocks never overlap, and each lies inside its block.
  var boxes=OfficeUi.BOXES;
  for(int i=0;i<boxes.size();i++){var a=boxes.get(i);
   if(a.block()>=0&&a.block()<OfficeUi.BLOCK_RECTS.size()&&!OfficeUi.BLOCK_RECTS.get(a.block()).containsRect(a.rect()))out.add("box outside its block: "+a.what()+" "+r(a.rect())+" in "+OfficeUi.BLOCK_NAMES.get(a.block())+" "+r(OfficeUi.BLOCK_RECTS.get(a.block())));
   if(a.layer()==1&&a.block()<0&&content.containsRect(a.rect())&&section!=ConstructionScreen.QUESTS)out.add("box without block: "+a.what()+" "+r(a.rect()));
   if(a.layer()!=1||a.block()<0)continue;
   for(int j=i+1;j<boxes.size();j++){var b=boxes.get(j);if(b.layer()==1&&b.block()>=0&&b.block()!=a.block()&&a.rect().intersects(b.rect()))out.add("box overlaps box: "+a.what()+" "+r(a.rect())+" of "+OfficeUi.BLOCK_NAMES.get(a.block())+" and "+b.what()+" "+r(b.rect())+" of "+OfficeUi.BLOCK_NAMES.get(b.block()));}}
  // A line shortened with '…' is always under a tooltip with its full text.
  for(var c:OfficeUi.CUT){boolean tip=OfficeUi.LAST_TIPS.stream().anyMatch(t->t.containsRect(c));
   if(!tip)for(var w:widgets)if(w.getTooltip()!=null&&Rect.of(w).containsRect(c))tip=true;
   if(!tip)out.add("cut without tooltip "+r(c));}
  // The frame: capped and centred.
  var want=l.frame();var fr=OfficeUi.drawnFrame;
  if(fr==null||!fr.equals(want))out.add("frame not centred: drawn "+(fr==null?"none":r(fr))+" expected "+r(want));
  if(want.w()>OfficeGrid.CAP_W||want.h()>OfficeGrid.CAP_H)out.add("frame wider than the cap "+r(want));
  // List tabs use the height they are given at M and L, unless all their content is already shown.
  if(l.size().atLeast(OfficeGrid.Size.M)&&(section==ConstructionScreen.OVERVIEW||section==ConstructionScreen.BUILDINGS||section==ConstructionScreen.LEVELS||section==ConstructionScreen.RESEARCH)){
   int bottom=content.y();for(var t:text)if(content.containsRect(t))bottom=Math.max(bottom,t.bottom());for(var b:boxes)if(content.containsRect(b.rect()))bottom=Math.max(bottom,b.rect().bottom());
   int used=(bottom-content.y())*100/Math.max(1,content.h());Object panel=s instanceof ConstructionScreen cs?cs.panel(section):s instanceof ResearchScreen rs?rs.panel():null;
   if(used<60){if(sparse(panel))NOTES.add("sparse tab "+section+": "+used+"% of the content height, all of it shown");else out.add("unused space: tab "+section+" uses "+used+"% of the content height");}}
  return out;
 }
 /** Optional boolean sparse() of a panel: all its content is already shown, so a short tab is honest. */
 private static boolean sparse(Object o){if(o==null)return false;try{var m=o.getClass().getDeclaredMethod("sparse");m.setAccessible(true);return (Boolean)m.invoke(o);}catch(ReflectiveOperationException ex){return false;}}
 /** Optional List<String> layoutProblems() of a screen or panel, found by name so a panel can add it without touching the probe. */
 @SuppressWarnings("unchecked") static List<String> hook(Object o){
  if(o==null)return List.of();
  try{var m=o.getClass().getDeclaredMethod("layoutProblems");m.setAccessible(true);return (List<String>)m.invoke(o);}catch(NoSuchMethodException ex){return List.of();}
  catch(ReflectiveOperationException ex){return List.of("layoutProblems() of "+o.getClass().getSimpleName()+" failed: "+ex.getCause());}
 }
 /** Calls a panel's own accessor by name (the probe reaches the accessors other groups add to their panels). */
 static Object call(Object o,String method,Object... args)throws ReflectiveOperationException{
  for(var m:o.getClass().getDeclaredMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(o,args);}
  throw new NoSuchMethodException(o.getClass().getSimpleName()+"."+method+" with "+args.length+" arguments");
 }
}
