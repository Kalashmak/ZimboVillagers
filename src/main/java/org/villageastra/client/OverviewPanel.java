package org.villageastra.client;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.domain.ResearchCatalog;
/** The landing tab of the office: only what needs the player now, worst first, each line leading to the tab that fixes it;
 *  beside it the village at a glance. AD-124: the tiles (people, food, science, treasury, crew, hall) stack their parts and carry
 *  a trend of the last days and, on tall screens, what to do; the layout follows the size class (XS strip, S column, M 2x3, L 3x2 with
 *  the next step on top). */
final class OverviewPanel {
 static final int ROW_H=20,TILE_TALL=47,TILE_SHORT=23;
 private final BiConsumer<Integer,String> open;private final Scroll list=new Scroll(ROW_H);private List<OfficeAttention.Need> needs=List.of();
 private record Hit(Rect r,int section,String focus){}
 private final List<Hit> hits=new ArrayList<>();private boolean allShown=true;
 OverviewPanel(Layout l,BiConsumer<Integer,String> open){this.open=open;}
 private static Component t(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 List<OfficeAttention.Need> needs(){return needs;}
 /** The screen rectangle of an attention line, or null when it is scrolled out of view (for the probe's click). */
 Rect rowRect(int index){int y=list.y(index);return y<0?null:new Rect(list.area().x(),y,list.rowW(),ROW_H-2);}
 /** Probe hook: every line and tile is on screen, so a short tab is honest. */
 boolean sparse(){return allShown;}
 /** The attention list; below L the next step leads it when it is not in it yet (at L it has its own card). */
 private static List<OfficeAttention.Need> list(CompoundTag s,boolean card){
  var out=new ArrayList<>(OfficeAttention.needs(s));if(card)return out;var next=OfficeAttention.nextStep(s);
  if(next!=null&&out.stream().noneMatch(n->n.text().getString().equals(next.text().getString())))out.add(0,next);return out;
 }
 void tick(boolean visible){if(visible)needs=list(ConstructionOverlay.snapshot(),false);}
 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  var s=ConstructionOverlay.snapshot();var c=l.content();var size=l.size();hits.clear();allShown=true;
  boolean card=size==OfficeGrid.Size.L;needs=list(s,card);Rect left,right=null;
  if(card){var parts=c.splitV(18,6);nextStep(g,f,s,parts[0],mx,my);c=parts[1];}
  switch(size){
   case XS->{strip(g,f,s,c.x(),c.y(),c.w());left=new Rect(c.x(),c.y()+22,c.w(),c.h()-22);}
   case S->{var p=l.split(c,8,3,2);left=p[0];right=p[1];}
   case M->{var p=l.split(c,8,1,1);left=p[0];right=p[1];}
   default->{var p=l.split(c,8,5,7);left=p[0];right=p[1];}
  }
  int y=OfficeUi.header(g,f,left.x(),left.y(),left.w(),new ItemStack(Items.BELL),t("overview.attention"));
  // Without a village in the snapshot nothing is known: grey, never a green "all is well".
  if(!s.hasUUID("village")){OfficeUi.block("attention",new Rect(left.x(),y,left.w(),18));OfficeUi.chip(g,f,left.x(),y,left.w(),ItemStack.EMPTY,t("no_data"),Component.empty(),Tone.OFF);OfficeUi.endBlock();}
  else if(needs.isEmpty()){OfficeUi.block("attention",new Rect(left.x(),y,left.w(),18));OfficeUi.chip(g,f,left.x(),y,left.w(),ItemStack.EMPTY,t("overview.all_good"),Component.empty(),Tone.OK);OfficeUi.endBlock();
   if(y+33<=left.bottom())OfficeUi.label(g,f,t("help_hint"),left.x(),y+24,left.w(),OfficeUi.MUTED);}
  else{list.bounds(new Rect(left.x(),y,left.w(),left.bottom()-y),needs.size());if(list.overflows())allShown=false;OfficeUi.clip(g,list.area());
   for(int i=0;i<needs.size();i++){int ry=list.y(i);if(ry<0)continue;var n=needs.get(i);boolean hover=list.hit(mx,my)==i;
    OfficeUi.block("attention "+i,new Rect(left.x(),ry,list.rowW(),ROW_H-2));
    OfficeUi.chip(g,f,left.x(),ry,list.rowW(),n.icon(),n.text(),n.value(),n.tone(),true,hover);OfficeUi.endBlock();
    OfficeUi.tips().add(left.x(),ry,list.rowW(),18,List.of(n.text(),Component.translatable("hall.villageastra.tab_"+n.section()).withStyle(x->x.withColor(OfficeUi.MUTED&0xFFFFFF))));}
   OfficeUi.unclip(g);list.render(g);}
  if(right!=null)tiles(g,f,l,s,right,mx,my);
 }
 /** At L: the one next step as a card of its own over both columns (or a green 'nothing urgent'). */
 private void nextStep(GuiGraphics g,Font f,CompoundTag s,Rect r,int mx,int my){
  var n=OfficeAttention.nextStep(s);OfficeUi.block("next step",r);
  if(n==null)OfficeUi.chip(g,f,r.x(),r.y(),r.w(),new ItemStack(Items.BELL),t("v2.next.none"),Component.empty(),s.hasUUID("village")?Tone.OK:Tone.OFF);
  else{boolean hover=r.contains(mx,my);OfficeUi.chip(g,f,r.x(),r.y(),r.w(),n.icon(),t("v2.next.title").copy().append(n.text()),n.value(),n.tone(),true,hover);hits.add(new Hit(r,n.section(),n.focus()));
   OfficeUi.tips().add(r,n.text(),Component.translatable("hall.villageastra.tab_"+n.section()).withStyle(x->x.withColor(OfficeUi.MUTED&0xFFFFFF)));}
  OfficeUi.endBlock();
 }
 private record Placed(OfficeUi.Tile tile,int section,String focus){}
 /** The tiles: one column at S, 2x3 at M, 3x2 at L; a tile is as tall as its parts (shorter screens drop the trend and hint to the tooltip). */
 private void tiles(GuiGraphics g,Font f,Layout l,CompoundTag s,Rect r,int mx,int my){
  var all=tileList(s,l.tall());int cols=l.size()==OfficeGrid.Size.S?1:l.size()==OfficeGrid.Size.M?2:3,rows=(all.size()+cols-1)/cols,gap=3;
  var colRects=l.columns(r,40,gap+3,cols);int cellH=(r.h()-gap*(rows-1))/Math.max(1,rows);
  int h=Math.min(cellH,l.tall()?TILE_TALL:Math.max(TILE_SHORT,cellH));if(h<18){allShown=false;h=18;}
  for(int i=0;i<all.size();i++){int col=i%cols,row=i/cols;var cr=colRects[Math.min(col,colRects.length-1)];int y=r.y()+row*(h+gap);
   if(y+18>r.bottom()){allShown=false;continue;}
   var rect=new Rect(cr.x(),y,cr.w(),Math.min(h,r.bottom()-y));var p=all.get(i);boolean hover=rect.contains(mx,my)&&p.section>=0;
   OfficeUi.block("tile "+i,rect);OfficeUi.tile(g,f,rect,p.tile,p.section>=0,hover);OfficeUi.endBlock();
   if(p.section>=0)hits.add(new Hit(rect,p.section,p.focus));}
 }
 private static Component span(CompoundTag trend){return t("v2.trend_span",trend.getInt("span"),trend.getLong("since"));}
 private static OfficeUi.Trend trend(CompoundTag o,String key,boolean upGood){
  var tr=o.getCompound("trend");if(tr.getInt("span")<=0)return OfficeUi.noTrend();
  long d=tr.getLong(key);return new OfficeUi.Trend(d,upGood,span(tr),String.valueOf(Math.abs(d)));
 }
 private List<Placed> tileList(CompoundTag s,boolean tall){
  var o=s.getCompound("overview");boolean known=s.contains("overview");var out=new ArrayList<Placed>();
  // People: residents against beds, free beds as a tag.
  int people=o.getInt("residents"),beds=o.getInt("beds"),free=o.getInt("bedsFree");
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.PLAYER_HEAD),t("overview.residents"),Component.literal(known?people+" / "+beds:"–"),Tone.INFO,0,0,
   known?t("overview.beds_free",free):null,free>0?Tone.OK:Tone.WAIT,known?trend(o,"residents",true):null,tall&&known&&free==0?t("v2.hint.beds"):null,
   List.of(t("overview.residents"),t("overview.beds_free",free))),ConstructionScreen.BUILDINGS,""));
  // Food: days of meals in the pantries, a bar against three days, the daily need, the change in days.
  var food=food(s);long need=o.getLong("need");OfficeUi.Trend foodTrend=null;
  if(known){var tr=o.getCompound("trend");if(tr.getInt("span")<=0||!tr.getBoolean("hasFood"))foodTrend=OfficeUi.noTrend();else{long d=tr.getLong("food");foodTrend=new OfficeUi.Trend(d,true,span(tr),OfficeUi.days(Math.abs(d)/10.0).getString());}}
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.BREAD),t("overview.food_label"),food.value,food.tone,known?o.getLong("food"):0,known?Math.max(1,need*3):0,
   known?t("v2.food_need",need):null,Tone.INFO,foodTrend,tall&&food.tone!=Tone.OK&&food.tone!=Tone.OFF?t("v2.hint.food"):null,
   List.of(t("overview.food",food.value),Component.literal(o.getLong("food")+" / "+need))),ConstructionScreen.BUILDINGS,""));
  // Science: the target and its volumes, and when it will be done (or why it will not).
  var research=s.getCompound("research");String target=research.getString("selected");var node=target.isEmpty()?null:ResearchCatalog.NODES.get(target);
  int paid=node==null?0:research.getCompound("paid").getInt(target);var eta=eta(research,node,paid);
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.ENCHANTED_BOOK),node==null?t("overview.no_target"):OfficeUi.research(target),node==null?Component.empty():Component.literal(paid+" / "+node.works()),
   node==null?(s.getBoolean("canManage")?Tone.ACTION:Tone.OFF):Tone.INFO,paid,node==null?0:node.works(),eta==null?null:eta.text,eta==null?Tone.INFO:eta.tone,
   known?trend(o,"paid",true):null,tall&&node==null?t("v2.hint.research"):null,
   node==null?List.of(t("overview.no_target")):research.getInt("credit")>0?List.of(OfficeUi.research(target),t("research.volumes",paid,node.works()),t("v2.research.credit",research.getInt("credit"))):List.of(OfficeUi.research(target),t("research.volumes",paid,node.works()))),ConstructionScreen.RESEARCH,target));
  // Treasury: coins and their change.
  long coins=o.contains("coins")?o.getLong("coins"):s.getCompound("war").getLong("treasury");
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.GOLD_INGOT),t("overview.treasury"),Component.literal(String.valueOf(coins)),Tone.INFO,0,0,null,null,known?trend(o,"coins",true):null,null,
   List.of(t("overview.treasury"))),ConstructionScreen.WAR,""));
  // Crew: free, busy, or the project with its share done.
  var crew=crew(s);boolean project=s.hasUUID("id")&&!s.getBoolean("survey")&&!s.getBoolean("draft");int total=s.getInt("total"),index=s.getInt("index");
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.IRON_SHOVEL),crew.value,project&&total>0?Component.literal(index*100/total+" %"):Component.empty(),crew.tone,project?index:0,project?Math.max(1,total):0,
   null,null,null,tall&&!project&&crew.tone==Tone.OK?t("v2.next.plan"):null,List.of(crew.value)),ConstructionScreen.CONSTRUCTION,""));
  // The hall: its level and the first thing the next one waits for.
  var hall=hallRow(s);int level=o.contains("hallLevel")?o.getInt("hallLevel"):research.getInt("hall");
  Component tag=null;Tone tagTone=Tone.INFO;String focus="";int section=ConstructionScreen.LEVELS;
  if(hall!=null&&hall.getInt("kept")<hall.getInt("max")){var rs=hall.getList("research",Tag.TAG_STRING);
   if(!rs.isEmpty()){tag=t("v2.hall_research",OfficeUi.research(rs.getString(0)));tagTone=Tone.OFF;}
   else if(hall.getInt("lack")>0){tag=t("v2.hall_lack",hall.getInt("lack"));tagTone=Tone.WAIT;}
   else if(hall.getString("refusal").isEmpty()){tag=t("v2.hall_ready");tagTone=Tone.ACTION;}
   if(hall.hasUUID("id"))focus=hall.getUUID("id").toString();}
  var hallLabel=hall!=null&&hall.getInt("kept")<hall.getInt("max")?t("v2.hall_next",OfficeUi.roman(Math.max(1,level)),OfficeUi.roman(Math.max(1,level)+1)):t("v2.hall_level",OfficeUi.roman(Math.max(1,level)));
  var hallTips=new ArrayList<Component>();hallTips.add(hallLabel);if(tag!=null)hallTips.add(tag);
  out.add(new Placed(new OfficeUi.Tile(new ItemStack(Items.BELL),hallLabel,Component.empty(),Tone.INFO,0,0,tag,tagTone,null,null,hallTips),section,focus));
  return out;
 }
 private static CompoundTag hallRow(CompoundTag s){for(var raw:s.getCompound("upgrades").getList("buildings",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;if(r.getString("type").equals("town_hall"))return r;}return null;}
 record Eta(Component text,Tone tone){}
 /** '≈ 2,5 дн.' at the known pace; else why it will not move: no scientist (red), no volumes in the lab (amber), too little history. */
 static Eta eta(CompoundTag research,ResearchCatalog.Node node,int paid){
  if(node==null)return null;int left=Math.max(0,node.works()-paid);
  // AD-136: a level-I node is paid in resources, not written; works credited before the rework are spent first.
  if(node.resourcePaid())return null;left=Math.max(0,left-Math.max(0,research.getInt("credit")));
  if(research.contains("scientists")&&research.getInt("scientists")==0)return new Eta(t("v2.eta.no_scientist"),Tone.BAD);
  if(research.contains("labs")&&research.getInt("labs")==0)return new Eta(t("v2.eta.no_lab"),Tone.BAD);
  if(research.contains("labVolumes")&&research.getInt("labVolumes")==0&&left>0)return new Eta(t("v2.eta.no_volumes"),Tone.WAIT);
  // AD-136: the seated scientists write two works an hour each (one per 30 minutes of the village clock): «≈ N ч при M учёных».
  if(research.contains("worksPerHour")&&research.getDouble("worksPerHour")>0)return new Eta(t("v2.eta.hours",String.format(java.util.Locale.ROOT,"%.1f",left/research.getDouble("worksPerHour")),research.getInt("seated")),Tone.INFO);
  double rate=research.contains("rate")?research.getDouble("rate"):-1;
  if(rate>0)return new Eta(t("v2.eta.days",OfficeUi.days(left/rate)),Tone.INFO);
  return new Eta(t("v2.eta.unknown"),Tone.OFF);
 }
 private record Shown(Component value,Tone tone){}
 private static Shown food(CompoundTag s){
  if(!s.contains("overview"))return new Shown(t("no_data"),Tone.OFF);var o=s.getCompound("overview");double days=OfficeAttention.foodDays(o);
  if(days<0)return new Shown(Component.literal("–"),Tone.OFF);
  return new Shown(OfficeUi.days(days),o.getInt("missed")>0||days<1?Tone.BAD:days<2?Tone.WAIT:Tone.OK);
 }
 private static Shown crew(CompoundTag s){
  boolean project=s.hasUUID("id")&&!s.getBoolean("survey")&&!s.getBoolean("draft");
  if(!project&&!s.getCompound("upgrades").getBoolean("busy"))return new Shown(t("overview.crew_free"),Tone.OK);
  if(!project)return new Shown(t("overview.crew_busy"),Tone.WAIT);
  Component what=s.getString("kind").equals("building")?ConstructionOverlay.designName(s.getString("design")):Component.translatable("building.villageastra.town_hall");
  return new Shown(t("overview.crew_project",what),s.getString("stage").equals("blocked")?Tone.BAD:Tone.WAIT);
 }
 /** At the smallest GUI the tiles shrink to a strip of icons and numbers; their words move into tooltips. */
 private void strip(GuiGraphics g,Font f,CompoundTag s,int x,int y,int w){
  var o=s.getCompound("overview");var food=food(s);var crew=crew(s);int step=w/4;OfficeUi.block("strip",new Rect(x,y,w,18));
  cell(g,f,x,y,step,new ItemStack(Items.PLAYER_HEAD),s.contains("overview")?String.valueOf(o.getInt("residents")):"–",OfficeUi.TEXT,List.of(t("overview.residents"),t("overview.beds_free",o.getInt("bedsFree"))));
  cell(g,f,x+step,y,step,new ItemStack(Items.BREAD),food.value.getString(),food.tone.fg(),List.of(t("overview.food",food.value)));
  cell(g,f,x+2*step,y,step,new ItemStack(Items.GOLD_INGOT),String.valueOf(o.contains("coins")?o.getLong("coins"):s.getCompound("war").getLong("treasury")),OfficeUi.TEXT,List.of(t("overview.treasury")));
  g.renderItem(new ItemStack(Items.IRON_SHOVEL),x+3*step,y);OfficeUi.sign(g,x+3*step+19,y+6,crew.tone);OfficeUi.tips().add(x+3*step,y,26,16,List.of(crew.value));OfficeUi.endBlock();
 }
 private static void cell(GuiGraphics g,Font f,int x,int y,int w,ItemStack icon,String value,int colour,List<Component> tip){g.renderItem(icon,x,y);int end=OfficeUi.label(g,f,value,x+18,y+4,w-20,colour);OfficeUi.tips().add(x,y,Math.max(18,end-x),16,tip);}
 boolean click(double mx,double my){
  for(var h:hits)if(h.r.contains(mx,my)){open.accept(h.section,h.focus==null||h.focus.isEmpty()?null:h.focus);return true;}
  int i=needs.isEmpty()?-1:list.hit(mx,my);if(i<0||i>=needs.size())return false;var n=needs.get(i);open.accept(n.section(),n.focus());return true;
 }
 boolean scroll(double mx,double my,double delta){return list.wheel(mx,my,delta);}
}
