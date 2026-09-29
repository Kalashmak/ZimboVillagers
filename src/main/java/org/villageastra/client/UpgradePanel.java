package org.villageastra.client;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
/** AD-052/AD-073: the Levels tab of the office. One status chip (is the crew free, how many can be ordered now), then one row per building:
 *  pips of its levels, the hall's stock against the next level's cost, every requirement at once, and the order button at the right.
 *  AD-112: a second line names the core or ring the next level sets (have/need; grey with the research title while that level's
 *  research is missing) and what its working effects become, now → next.
 *  The row buttons follow the scrolled list before each frame, click and wheel turn, so a click never lands on another building. */
final class UpgradePanel {
 /** AD-124: a row is 38 px with the core line under it, 22 without (no empty second line). */
 static final int ROW_H=38,ROW_SHORT=22,BTN_W=56;
 /** A list of rows of different heights that scrolls a whole row per wheel step; only rows wholly inside the area are drawn. */
 static final class Rows {
  private Rect area=new Rect(0,0,0,0);private int[] tops=new int[1],heights=new int[0];private int first;
  void bounds(Rect area,int[] heights){this.area=area;this.heights=heights;tops=new int[heights.length+1];for(int i=0;i<heights.length;i++)tops[i+1]=tops[i]+heights[i];first=Math.max(0,Math.min(first,maxFirst()));}
  private int maxFirst(){int i=0;while(i<heights.length&&tops[heights.length]-tops[i]>area.h())i++;return i;}
  Rect area(){return area;} int first(){return first;} int height(int i){return i>=0&&i<heights.length?heights[i]:0;}
  boolean overflows(){return tops[heights.length]>area.h();}
  int rowW(){return area.w()-(overflows()?5:0);}
  int y(int i){if(i<first||i>=heights.length)return -1;int top=tops[i]-tops[first];return top+heights[i]>area.h()?-1:area.y()+top;}
  boolean wheel(double mx,double my,double delta){if(!area.contains(mx,my))return false;int before=first;first=Math.max(0,Math.min(maxFirst(),first-(int)Math.signum(delta)));return before!=first||overflows();}
  void reveal(int i){if(i<first)first=i;else while(first<i&&y(i)<0)first++;first=Math.max(0,Math.min(first,maxFirst()));}
  int hit(double mx,double my){if(!area.contains(mx,my)||mx>=area.x()+rowW())return -1;for(int i=first;i<heights.length;i++){int y=y(i);if(y<0)break;if(my>=y&&my<y+heights[i])return i;}return -1;}
  void render(GuiGraphics g){if(!overflows())return;int x=area.right()-3,total=tops[heights.length];g.fill(x,area.y(),x+3,area.bottom(),OfficeUi.TRACK);int th=Math.max(8,area.h()*area.h()/total),ty=area.y()+(area.h()-th)*tops[first]/Math.max(1,total-area.h());g.fill(x,Math.min(ty,area.bottom()-th),x+3,Math.min(ty,area.bottom()-th)+th,OfficeUi.MUTED);}
 }
 private static int[] heights(ListTag rows){var out=new int[rows.size()];for(int i=0;i<rows.size();i++){var r=rows.getCompound(i);out[i]=!done(r)&&!r.getCompound("core").getString("item").isEmpty()?ROW_H:ROW_SHORT;}return out;}
 /** Probe hook: every row is on screen. */
 boolean sparse(){return !list.overflows();}
 /** AD-112: what each row's core cell said in the last frame (text and tone), for the probe. */
 static final Map<UUID,Object[]> CORE_SHOWN=new HashMap<>();
 /** The panel of the office open now, for the static probe accessors. */
 private static UpgradePanel current;
 private record Hit(Rect r,Runnable run){}
 private final Layout layout;private final Rows list=new Rows();private final List<RowButton> pool=new ArrayList<>();
 private final UUID[] assigned;private final String[] reasons;private final List<Hit> hits=new ArrayList<>();private boolean shown;
 UpgradePanel(Layout l,Consumer<Button> add){
  layout=l;current=this;int n=Math.max(1,listArea(l).h()/ROW_SHORT+1);assigned=new UUID[n];reasons=new String[n];
  for(int i=0;i<n;i++){final int slot=i;var b=new RowButton(x->order(slot));b.visible=false;pool.add(b);add.accept(b);}
 }
 private static Component t(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 private static CompoundTag upgrades(){return ConstructionOverlay.snapshot().getCompound("upgrades");}
 private static ListTag rows(){return upgrades().getList("buildings",Tag.TAG_COMPOUND);}
 /** The list sits under the status chip. */
 private static Rect listArea(Layout l){var c=l.content();return new Rect(c.x(),c.y()+22,c.w(),Math.max(0,c.h()-22));}
 private static boolean done(CompoundTag r){return r.getInt("kept")>=r.getInt("max");}
 private static boolean mayor(){return upgrades().getBoolean("mayor");}
 private static boolean busy(){return upgrades().getBoolean("busy");}
 private static boolean ready(CompoundTag r){return !done(r)&&r.getString("refusal").isEmpty()&&r.getInt("lack")==0;}
 /** A research or site the crew cannot work around: nothing the mayor can press, so grey, never amber (amber is a shortfall or a wait). */
 private static Tone tone(CompoundTag r){
  if(done(r))return Tone.OK;String ref=r.getString("refusal");
  if(refusalTone(ref)==Tone.BAD)return Tone.BAD;
  // The server names only the first refusal (a busy crew hides missing research), so the research list is read on its own.
  if(ref.equals("research")||ref.equals("hall_office")||!r.getList("research",Tag.TAG_STRING).isEmpty())return Tone.OFF;
  if(r.getInt("lack")>0||ref.equals("busy")||busy())return Tone.WAIT;
  return mayor()?Tone.ACTION:Tone.OFF;
 }
 /** Red only for what blocks the building itself (siege, equipment, the site); a wait or a pointer elsewhere is not red. */
 private static Tone refusalTone(String ref){return ref.isEmpty()||ref.equals("research")||ref.equals("done")?Tone.OFF:ref.equals("hall_office")?Tone.INFO:ref.equals("materials")||ref.equals("busy")?Tone.WAIT:Tone.BAD;}
 private static Component refusal(String ref){return ref.equals("hall_office")?t("buildings.hall_office"):Component.translatable("upgrade.villageastra.refused."+ref);}
 /** 'Пекарня 2' when the village has more than one: numbered in the settlement's own order, as the Buildings tab counts them. */
 static Component name(CompoundTag s,CompoundTag r){
  var type=r.getString("type");var base=Component.translatable("building.villageastra."+type);if(!r.hasUUID("id"))return base;
  var ids=new ArrayList<UUID>();for(var raw:s.getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.getString("type").equals(type)&&c.hasUUID("id"))ids.add(c.getUUID("id"));}
  if(ids.isEmpty())for(var raw:rows()){var c=(CompoundTag)raw;if(c.getString("type").equals(type)&&c.hasUUID("id"))ids.add(c.getUUID("id"));}
  int at=ids.indexOf(r.getUUID("id"));return ids.size()>1&&at>=0?base.copy().append(" "+(at+1)):base;
 }
 /** Every reason the order is closed now, one per line: the button's tooltip. */
 private static Component reason(CompoundTag s,CompoundTag r){
  var lines=new ArrayList<Component>();String ref=r.getString("refusal");
  if(!mayor())lines.add(t(s.getString("lockReason").equals("unsafe")?"reason.unsafe":"reason.only_mayor"));
  var research=r.getList("research",Tag.TAG_STRING);
  if(!research.isEmpty()){var titles=Component.empty();for(int i=0;i<research.size();i++){if(i>0)titles.append(", ");titles.append(OfficeUi.research(research.getString(i)));}lines.add(t("reason.research",titles));}
  if(ref.equals("busy")||busy())lines.add(t("reason.crew_busy"));
  if(!ref.isEmpty()&&!ref.equals("busy")&&!ref.equals("research")&&!ref.equals("done"))lines.add(refusal(ref));
  if(r.getInt("lack")>0)lines.add(t("reason.materials",r.getInt("lack")));
  var out=Component.empty();for(int i=0;i<lines.size();i++){if(i>0)out.append("\n");out.append(lines.get(i));}return out;
 }
 private void order(int slot){
  var t=ConstructionOverlay.snapshot();var id=slot<assigned.length?assigned[slot]:null;if(id==null||!t.hasUUID("village"))return;
  ConstructionNetwork.sendUpgrade(new ConstructionNetwork.UpgradeOrder(t.getUUID("village"),t.getLong("epoch"),id));
 }
 private static void open(int section,String focus){if(Minecraft.getInstance().screen instanceof ConstructionScreen cs)cs.open(section,focus);}

 /** Puts each pooled button on the visible row it belongs to and hides the rest; called before every frame, click and wheel turn. */
 void reposition(){
  var s=ConstructionOverlay.snapshot();var rows=rows();list.bounds(listArea(layout),heights(rows));Arrays.fill(assigned,null);
  for(int k=0;k<pool.size();k++){var b=pool.get(k);int i=list.first()+k,y=list.y(i);
   var r=shown&&y>=0&&i<rows.size()?rows.getCompound(i):null;
   if(r==null||!r.hasUUID("id")||done(r)){b.visible=false;continue;}
   assigned[k]=r.getUUID("id");b.setMessage(t("levels.up",OfficeUi.roman(r.getInt("next"))));
   b.active=mayor()&&!busy()&&ready(r);
   var why=b.active?"":reason(s,r).getString();if(!why.equals(reasons[k])){reasons[k]=why;b.reason(why.isEmpty()?null:Component.literal(why));}
   b.at(new Rect(list.area().x()+list.rowW()-BTN_W-2,y+1,BTN_W,18));b.visible=true;}
 }
 void tick(boolean visible){shown=visible;reposition();}
 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  var s=ConstructionOverlay.snapshot();reposition();hits.clear();CORE_SHOWN.clear();var c=l.content();
  if(!s.hasUUID("village")){OfficeUi.block("empty",c);OfficeUi.chip(g,f,c.x(),c.y(),c.w(),ItemStack.EMPTY,t("no_data"),Component.empty(),Tone.OFF);OfficeUi.endBlock();return;}
  var rows=rows();OfficeUi.block("status",new Rect(c.x(),c.y(),c.w(),18));status(g,f,s,rows,c);OfficeUi.endBlock();
  if(rows.isEmpty()){int y=c.y()+24;OfficeUi.label(g,f,t("levels.empty"),c.x(),y,c.w(),OfficeUi.MUTED);
   OfficeUi.label(g,f,t("help_hint"),c.x(),y+12,c.w(),OfficeUi.MUTED);return;}
  OfficeUi.clip(g,list.area());
  for(int i=0;i<rows.size();i++){int y=list.y(i);if(y<0)continue;var rr=new Rect(list.area().x(),y,list.rowW(),list.height(i)-2);OfficeUi.block("row "+i,rr);row(g,f,s,rows.getCompound(i),i,rr.x(),y,rr.w(),mx,my);OfficeUi.endBlock();}
  OfficeUi.unclip(g);list.render(g);
 }
 /** The one status line: the crew's state, and how many buildings the mayor can order now. */
 private void status(GuiGraphics g,Font f,CompoundTag s,ListTag rows,Rect c){
  int ready=0;for(int i=0;i<rows.size();i++)if(ready(rows.getCompound(i)))ready++;
  boolean project=s.hasUUID("id")&&!s.getBoolean("survey")&&!s.getBoolean("draft");
  Component what=!project?Component.empty():Component.translatable("building.villageastra."+(s.getString("kind").equals("building")?type(s.getString("design")):"town_hall"));
  var icon=new ItemStack(Items.IRON_SHOVEL);
  if(busy()){OfficeUi.chip(g,f,c.x(),c.y(),c.w(),icon,Component.translatable("upgrade.villageastra.busy"),what,Tone.WAIT);if(project)OfficeUi.tips().add(new Rect(c.x(),c.y(),c.w(),18),Component.translatable("upgrade.villageastra.busy"),what);}
  else if(ready>0&&mayor())OfficeUi.chip(g,f,c.x(),c.y(),c.w(),icon,Component.translatable("upgrade.villageastra.free"),t("need.upgrade",ready),Tone.ACTION);
  else OfficeUi.chip(g,f,c.x(),c.y(),c.w(),icon,Component.translatable("upgrade.villageastra.free"),Component.empty(),Tone.OK);
 }
 /** A level's design id ('bakery@3', 'town_hall_2') is not a lang key: its building type is. */
 static String type(String design){int at=design.indexOf('@');var t=at<0?design:design.substring(0,at);return t.startsWith("town_hall")?"town_hall":t;}
 /** One building: stripe and icon, name, level pips, stock bar, requirements; the button (a pooled widget) or the top-level tag at the right. */
 private void row(GuiGraphics g,Font f,CompoundTag s,CompoundTag r,int index,int x,int y,int w,int mx,int my){
  int h=list.height(index)-2;boolean done=done(r);Tone tone=tone(r);
  OfficeUi.row(g,x,y,w,h,false,list.hit(mx,my)==index,null);g.fill(x,y,x+2,y+h,tone.fg());
  g.renderItem(OfficeUi.buildingIcon(r.getString("type")),x+4,y+2);
  if(r.getBoolean("driven")){g.pose().pushPose();g.pose().translate(x+14,y+11,100);g.pose().scale(.5f,.5f,1);g.renderItem(new ItemStack(Items.PISTON),0,0);g.pose().popPose();}
  // AD-124: the name column follows the width (a fifth, 64..160 px).
  int nameW=Math.max(64,Math.min(160,w/5)),right=x+w-BTN_W-6;var name=name(s,r);
  OfficeUi.label(g,f,name,x+22,y+6,nameW,done?Tone.OK.fg():OfficeUi.TEXT);
  var nameTip=new ArrayList<Component>(List.of(name,t("buildings.level",r.getInt("kept"),r.getInt("max"))));
  if(r.getBoolean("driven"))nameTip.add(Component.translatable("upgrade.villageastra.driven"));
  nameTip.add(Component.translatable("hall.villageastra.tab_2").withStyle(z->z.withColor(OfficeUi.MUTED&0xFFFFFF)));
  // The first line only (the core line under it has tooltips of its own).
  var nameRect=new Rect(x,y,22+nameW,19);OfficeUi.tips().add(nameRect.x(),nameRect.y(),nameRect.w(),nameRect.h(),nameTip);
  if(r.hasUUID("id")){var id=r.getUUID("id").toString();hits.add(new Hit(nameRect,()->open(ConstructionScreen.BUILDINGS,id)));}
  int px=x+22+nameW+6,pw=OfficeUi.pips(g,px,y+8,r.getInt("level"),r.getInt("kept"),r.getInt("max"));
  OfficeUi.tips().add(px,y,pw,19,List.of(t("buildings.level",r.getInt("kept"),r.getInt("max"))));
  if(done){var tag=t("levels.done",OfficeUi.roman(r.getInt("max")));OfficeUi.tag(g,f,x+w-OfficeUi.tagWidth(f,tag)-4,y+5,tag,Tone.OK);return;}
  int bx=px+pw+8;cost(g,f,r,bx,y+5);
  // At the smallest GUI the requirements move into the button's tooltip (it names every reason); at S only the first shows, from M all.
  if(!layout.narrow())requirements(g,f,r,bx+68,y+5,right,layout.size()==OfficeGrid.Size.S?1:Integer.MAX_VALUE);
  core(g,f,r,x+22,y+21,x+w-4);
 }
 /** AD-112: the core or ring of the next level. Grey with 'нужно исследование: Земледелие III' while that level's research is missing
  *  (it cannot be set before it), amber while the hall lacks it, green when it is there; nothing for a building without a core. */
 static Component coreLabel(CompoundTag c){
  var research=c.getList("research",Tag.TAG_STRING);
  if(!research.isEmpty()){var n=org.villageastra.domain.ResearchCatalog.NODES.get(research.getString(0));
   return n==null?Component.translatable("upgrade.villageastra.research",OfficeUi.research(research.getString(0))):Component.translatable("core.villageastra.needs_research",Component.translatable("research.villageastra.branch."+n.branch()),OfficeUi.roman(n.tier()));}
  // The count first, so a shortened label still says it.
  return Component.literal(Math.min(c.getInt("have"),c.getInt("need"))+" / "+c.getInt("need")+" ").append(OfficeUi.icon(c.getString("item")).getHoverName());
 }
 static Tone coreTone(CompoundTag c){return !c.getList("research",Tag.TAG_STRING).isEmpty()?Tone.OFF:c.getInt("have")<c.getInt("need")?Tone.WAIT:Tone.OK;}
 /** The next level's effects as 'Участков поля: 1 → 2 (80 → 160 грядок)', joined; the farm adds 'построено X, работает Y' when they differ. */
 static Component effectLine(CompoundTag c){
  var out=Component.empty();int shown=0;
  for(var raw:c.getList("effects",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;if(!e.getBoolean("active"))continue;if(shown++>0)out.append(" · ");
   out.append(Component.translatable("core.villageastra.change",Component.translatable("core.villageastra.effect."+e.getString("id")),e.getInt("now"),e.getInt("next")));
   if(e.getString("id").equals("field")){int plots=org.villageastra.world.FarmField.MODULE*org.villageastra.world.FarmField.MODULE-1;out.append(" (").append(Component.translatable("core.villageastra.farm_plots",e.getInt("now")*plots,e.getInt("next")*plots)).append(")");}}
  if(shown==0)return Component.translatable("core.villageastra.inactive");
  if(c.contains("nowBuilt")&&c.getInt("nowBuilt")!=c.getInt("nowWorked"))out.append(" · ").append(Component.translatable("core.villageastra.farm_built",c.getInt("nowBuilt"),c.getInt("nowWorked")));
  return out;
 }
 private static void core(GuiGraphics g,Font f,CompoundTag r,int x,int y,int right){
  var c=r.getCompound("core");if(c.getString("item").isEmpty())return;var label=coreLabel(c);Tone tone=coreTone(c);
  g.pose().pushPose();g.pose().translate(x,y+1,100);g.pose().scale(.5f,.5f,1);g.renderItem(OfficeUi.icon(c.getString("item")),0,0);g.pose().popPose();
  int tx=x+10,room=Math.max(0,right-tx);var shown=OfficeUi.tagWidth(f,label)>room/2?Component.literal(OfficeUi.fit(f,label.getString(),Math.max(24,room/2-12))):label;
  int tw=OfficeUi.tag(g,f,tx,y,shown,tone);var tip=new ArrayList<Component>(List.of(OfficeUi.icon(c.getString("item")).getHoverName(),label));
  if(tone==Tone.OFF)tip.add(Component.translatable("core.villageastra.ring_research",OfficeUi.roman(c.getInt("gradeNeeded"))).withStyle(z->z.withColor(OfficeUi.MUTED&0xFFFFFF)));
  OfficeUi.tips().add(x,y,tw+10,11,tip);
  var line=effectLine(c);int ex=tx+tw+6;boolean active=!line.getString().equals(Component.translatable("core.villageastra.inactive").getString());
  if(right-ex>12){OfficeUi.drawText(g,f,OfficeUi.fit(f,line.getString(),right-ex),ex,y+2,active?OfficeUi.TEXT:Tone.OFF.fg());OfficeUi.tips().add(ex,y,right-ex,11,effectTip(c,line));}
  if(r.hasUUID("id"))CORE_SHOWN.put(r.getUUID("id"),new Object[]{label.getString(),tone,line.getString()});
 }
 private static List<Component> effectTip(CompoundTag c,Component line){
  var out=new ArrayList<Component>();out.add(line);
  for(var raw:c.getList("effects",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;if(!e.getBoolean("active"))out.add(Component.translatable("core.villageastra.effect.inactive",Component.translatable("core.villageastra.effect."+e.getString("id"))).withStyle(z->z.withColor(Tone.OFF.fg()&0xFFFFFF)));}
  return out;
 }
 /** The hall's stock against the next level: a bar 'have / need', the missing items in its tooltip. */
 private static void cost(GuiGraphics g,Font f,CompoundTag r,int x,int y){
  int items=r.getInt("items"),lack=r.getInt("lack");Tone tone=lack==0?Tone.OK:Tone.WAIT;
  OfficeUi.bar(g,f,x,y,60,items<=0?1:items-lack,Math.max(1,items),tone,Component.literal((items-lack)+" / "+items));
  var lines=new ArrayList<Component>();
  lines.add((lack>0?t("reason.materials",lack):t("construction.all_here")).copy().withStyle(z->z.withColor((lack>0?Tone.WAIT:Tone.OK).fg()&0xFFFFFF)));
  // AD-112: the core or ring first, then the rest (the server sorts the missing ones first).
  var core=r.getCompound("core");var rows=new ArrayList<CompoundTag>();for(var raw:r.getList("cost",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.getString("item").equals(core.getString("item")))rows.add(0,c);else rows.add(c);}
  int shown=0;for(var c:rows){if(shown++>=8)break;int have=c.getInt("have"),need=c.getInt("need");
   var line=OfficeUi.icon(c.getString("item")).getHoverName().copy().append("  "+Math.min(have,need)+" / "+need+(have<need?"  -"+(need-have):""));
   lines.add(line.withStyle(z->z.withColor((have<need?Tone.BAD.fg():OfficeUi.MUTED)&0xFFFFFF)));}
  OfficeUi.tips().add(x,y,60,11,lines);
 }
 private record Req(Component text,Tone tone,int section,String focus){}
 /** Everything the next level still waits for, side by side: research by title (grey, opens the tree), the refusal. */
 private void requirements(GuiGraphics g,Font f,CompoundTag r,int x,int y,int right,int most){
  var reqs=new ArrayList<Req>();String ref=r.getString("refusal");
  // AD-124: a research is named short, 'нужно: Земледелие II', and opens that node in the tree.
  for(var raw:r.getList("research",Tag.TAG_STRING)){var n=org.villageastra.domain.ResearchCatalog.NODES.get(raw.getAsString());
   reqs.add(new Req(n==null?OfficeUi.research(raw.getAsString()):t("v2.need_research",Component.translatable("research.villageastra.branch."+n.branch()),OfficeUi.roman(n.tier())),Tone.OFF,ConstructionScreen.RESEARCH,raw.getAsString()));}
  if(!ref.isEmpty()&&!ref.equals("research")&&!ref.equals("busy")&&!ref.equals("done"))reqs.add(new Req(refusal(ref),refusalTone(ref),ref.equals("hall_office")?ConstructionScreen.CONSTRUCTION:-1,null));
  if(reqs.size()>most){var all=new ArrayList<Component>();for(var q:reqs)all.add(q.text);var kept=new ArrayList<>(reqs.subList(0,most));reqs=kept;
   int more=all.size()-most;var plus="+"+more;int px=right-f.width(plus);if(px>x+24){OfficeUi.drawText(g,f,plus,px,y+2,OfficeUi.MUTED);OfficeUi.tips().add(px,y,f.width(plus),11,all);right=px-4;}}
  int ax=x;
  for(int i=0;i<reqs.size();i++){var q=reqs.get(i);int room=right-ax;var text=q.text;
   if(OfficeUi.tagWidth(f,text)>room){
    // Too wide: shorten it when a readable part fits, else count what is left.
    if(room>=48)text=Component.literal(OfficeUi.fit(f,text.getString(),room-12));
    else{var more="+"+(reqs.size()-i);if(f.width(more)<=room)OfficeUi.drawText(g,f,more,ax,y+2,OfficeUi.MUTED);
     var all=new ArrayList<Component>();for(int k=i;k<reqs.size();k++)all.add(reqs.get(k).text);OfficeUi.tips().add(ax,y,Math.max(0,room),11,all);break;}}
   int tw=OfficeUi.tag(g,f,ax,y,text,q.tone);var rect=new Rect(ax,y,tw,11);
   var tip=new ArrayList<Component>(List.of(q.text));
   if(q.section>=0){tip.add(Component.translatable("hall.villageastra.tab_"+q.section).withStyle(z->z.withColor(OfficeUi.MUTED&0xFFFFFF)));hits.add(new Hit(rect,()->open(q.section,q.focus)));}
   OfficeUi.tips().add(rect.x(),rect.y(),rect.w(),rect.h(),tip);ax+=tw+4;}
 }
 /** A requirement or a name opens its tab; a click elsewhere on a row does nothing (the button is a widget of its own). */
 boolean click(double mx,double my){reposition();for(var h:hits)if(h.r.contains(mx,my)&&list.area().contains(mx,my)){h.run.run();return true;}return false;}
 /** The click regions of the last frame belong to the old rows once the list moved, so they are dropped until the next frame. */
 boolean scroll(double mx,double my,double delta){reposition();int before=list.first();boolean moved=list.wheel(mx,my,delta);if(list.first()!=before)hits.clear();reposition();return moved;}
 /** The button of a building's row while that row is on screen, else null (top-level rows have a tag, not a button). */
 static Button buttonFor(UUID building){
  var p=current;if(p==null||building==null)return null;
  for(int k=0;k<p.pool.size();k++)if(building.equals(p.assigned[k])&&p.pool.get(k).visible)return p.pool.get(k);
  return null;
 }
 /** Scrolls the list so the building's row is on screen (the Overview's jump, the probe). */
 static void reveal(UUID building){
  var p=current;if(p==null||building==null)return;var rows=rows();
  for(int i=0;i<rows.size();i++){var r=rows.getCompound(i);if(r.hasUUID("id")&&r.getUUID("id").equals(building)){p.list.bounds(listArea(p.layout),heights(rows));p.list.reveal(i);break;}}
  p.reposition();
 }
 /** The probe's own checks of this tab: row buttons only on visible rows and inside the content. */
 List<String> layoutProblems(){
  var out=new ArrayList<String>();var c=layout.content();
  for(int k=0;k<pool.size();k++){var b=pool.get(k);if(!b.visible)continue;var br=Rect.of(b);
   if(!list.area().containsRect(br)||!c.containsRect(br))out.add("levels: a row button leaves the list "+br);
   if(assigned[k]==null||list.y(list.first()+k)<0)out.add("levels: a button shows on a hidden row "+br);}
  return out;
 }
 /** The order button with the pixel up arrow before its level (the font has no sharp arrow). */
 private static final class RowButton extends OfficeButton {
  RowButton(Button.OnPress press){super(Component.empty(),press);tone(Tone.ACTION);}
  @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){super.renderWidget(g,mx,my,partial);if(active)OfficeUi.arrow(g,getX()+5,getY()+(getHeight()-7)/2,0,OfficeUi.TEXT);}
 }
}
