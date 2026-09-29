package org.villageastra.client;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.*;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.client.ResearchTreeModel.Filter;
import org.villageastra.client.ResearchTreeModel.State;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.domain.ResearchCatalog.Node;
import org.villageastra.server.ConstructionNetwork;
import org.villageastra.world.BuildingTiers;
/** AD-062, AD-124: the research of the settlement as a grid inside the office frame — one row per branch (grouped: economy, crafts,
 *  knowledge, settlement, defence), one column per step. The wheel scrolls the rows, Shift+wheel the columns, Ctrl+wheel the density
 *  of the cells (icon, tile, card); dragging moves the grid. A toolbar filters by group and state, jumps to the target and to the next
 *  node that can be studied; search counts its matches and Enter walks them. On the right the details of the node looked at: what it
 *  needs, what it opens, its effects (AD-123 facts) and what to do next. Keys: arrows, PgUp/PgDn, Home/End, Enter, Q, N, T, 0..5, / . */
final class ResearchPanel {
 /** Cell width and height per density (icon, tile, card); a column is a cell and 10 px, a row a cell and 4 px. */
 static final int[][] CELL={{22,22},{56,22},{100,26}};
 static final int STEPS=12,PAD=6;
 /** The node looked at survives reopening the office; a focus asked for by another tab wins once. Group, state filter and a chosen density too. */
 private static String remembered,pending,group;private static Filter filter=Filter.ALL;private static Integer densityChosen;
 private final Layout layout;private final Font font;private final OfficeGrid.Size size;private final boolean merged;
 private final Rect content,strip,bar,canvas,labels,steps,nodes,side,details;
 private final EditBox search;private final OfficeButton choose,pause,queue,dequeue,toTarget,next,filterButton,groupCycle;private final List<OfficeButton> groupButtons=new ArrayList<>();
 private Rect targetChip,counter;private boolean compact;
 private String selected,hovered;private int density;private double scrollX,scrollY,wheelX,wheelY;
 private List<Node> matches=List.of();private Set<String> matchIds;private int matchIndex;
 private List<String> rows=List.of();private String rowsKey="";
 private int detailScroll,detailHeight;private final List<Link> links=new ArrayList<>();private final List<Rect> labelRects=new ArrayList<>();
 private double pressX,pressY;private boolean pressed,panning,dragV,dragH;private long lastClick;private String lastClickId;
 private record Link(Rect r,Runnable run){}
 ResearchPanel(Font font,Layout l,Consumer<AbstractWidget> add){
  this.font=font;layout=l;content=l.content();size=l.size();merged=!l.tall()||size==OfficeGrid.Size.XS;
  boolean wide=size.atLeast(OfficeGrid.Size.M);
  int dw=switch(size){case XS->content.w()*34/100;case S->Math.max(150,content.w()/3);case M->content.w()*38/100;default->Math.min(340,content.w()*36/100);};
  Rect left;
  if(wide){strip=merged?null:new Rect(content.x(),content.y(),content.w(),18);int top=merged?content.y():content.y()+22;bar=new Rect(content.x(),top,content.w(),18);
   var below=new Rect(content.x(),bar.bottom()+4,content.w(),content.bottom()-bar.bottom()-4);var cols=below.splitH(below.w()-dw-4,4);left=cols[0];side=cols[1];details=side;}
  else{var cols=content.splitH(content.w()-dw-4,4);left=cols[0];side=cols[1];details=new Rect(side.x(),side.y()+22,side.w(),side.h()-22);
   strip=merged?null:new Rect(left.x(),left.y(),left.w(),18);bar=new Rect(left.x(),merged?left.y():left.y()+22,left.w(),18);left=new Rect(left.x(),bar.bottom()+4,left.w(),left.bottom()-bar.bottom()-4);}
  canvas=left;int lw=labelWidth(font);
  labels=new Rect(canvas.x(),canvas.y()+STEPS,lw,canvas.h()-STEPS);steps=new Rect(canvas.x()+lw,canvas.y(),canvas.w()-lw,STEPS);nodes=new Rect(canvas.x()+lw,canvas.y()+STEPS,canvas.w()-lw,canvas.h()-STEPS);
  // The search: in the details column below M (as before), at the right end of the toolbar from M. EditBox draws its border one pixel outside.
  int sw=wide?Math.min(170,Math.max(90,bar.w()/4)):side.w();int cw=font.width("99/99")+6;
  Rect sr=wide?new Rect(bar.right()-sw,bar.y(),sw,18):new Rect(side.x(),side.y(),side.w(),18);
  counter=new Rect(sr.right()-cw,sr.y(),cw,18);
  search=new EditBox(font,sr.x()+1,sr.y()+1,sr.w()-cw-3,16,text("search"));search.setMaxLength(64);// EditBox draws its hint unclipped: a narrow box (XS) gets it shortened, the full text in the tooltip.
  String hint=text("search").getString(),shown=OfficeUi.fit(font,hint,search.getInnerWidth());searchHint=shown;search.setHint(Component.literal(shown).withStyle(Style.EMPTY.withColor(OfficeUi.MUTED&0xFFFFFF)));
  if(!shown.equals(hint))search.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("search")));
  search.setResponder(this::searched);add.accept(search);
  choose=OfficeButton.of(text("choose"),new ItemStack(Items.TARGET),Tone.ACTION,b->order(selected,tier1(selected)?3:0));
  pause=OfficeButton.of(text("pause"),new ItemStack(Items.CLOCK),Tone.WAIT,b->order("",0));
  queue=OfficeButton.of(text("enqueue"),new ItemStack(Items.PAPER),Tone.ACTION,b->order(selected,1));
  dequeue=OfficeButton.of(text("dequeue"),new ItemStack(Items.BARRIER),Tone.INFO,b->order(selected,tier1(selected)?4:2));
  for(var b:List.of(choose,pause,queue,dequeue))add.accept(b);
  // The toolbar: groups (labels, icons or one cycling button, whatever fits), the state filter, 'to the target', 'next to study'.
  toTarget=OfficeButton.of(text("to_target"),new ItemStack(Items.TARGET),Tone.INFO,b->toTarget());
  next=OfficeButton.of(text("next_available"),new ItemStack(Items.SPYGLASS),Tone.INFO,b->nextAvailable(1));
  filterButton=OfficeButton.of(filterLabel(filter,0),filterIcon(filter),Tone.INFO,b->{filter=filter.next();rowsKey="";keepSelectionVisible();});
  groupCycle=OfficeButton.of(groupLabel(group),groupIcon(group),Tone.INFO,b->{int i=group==null?0:ResearchTreeModel.GROUP_IDS.indexOf(group)+1;setGroup(i>=ResearchTreeModel.GROUP_IDS.size()?null:ResearchTreeModel.GROUP_IDS.get(i));});
  groupButtons.add(OfficeButton.of(groupLabel(null),groupIcon(null),Tone.INFO,b->setGroup(null)));
  for(var g:ResearchTreeModel.GROUP_IDS)groupButtons.add(OfficeButton.of(groupLabel(g),groupIcon(g),Tone.INFO,b->setGroup(g)));
  add.accept(toTarget);add.accept(next);add.accept(filterButton);add.accept(groupCycle);groupButtons.forEach(add);
  layoutBar();placeFooter();
  selected=pending!=null&&ResearchCatalog.NODES.containsKey(pending)?pending:remembered;
  density=defaultDensity();
  // A remembered or asked-for node opens in view, not wherever the scroll starts.
  if(selected!=null)focusNode(selected);pending=null;
 }
 private static Component text(String key,Object...args){return Component.translatable("research.villageastra."+key,args);}
 /** AD-136: a level-I node is paid in resources (action 3, cancelled by 4), never chosen as the laboratory's target. */
 private static boolean tier1(String id){var n=id==null?null:ResearchCatalog.NODES.get(id);return n!=null&&n.resourcePaid();}
 private static Component office(String key,Object...args){return Component.translatable("office.villageastra.research."+key,args);}
 private static Component v2(String key,Object...args){return Component.translatable("office.villageastra.v2.research."+key,args);}
 private static Component branch(String b){return Component.translatable("research.villageastra.branch."+b);}
 private static Component groupLabel(String g){return text("group."+(g==null?"all":g));}
 private static ItemStack groupIcon(String g){return new ItemStack(g==null?Items.BOOKSHELF:switch(g){case "food"->Items.WHEAT;case "resources"->Items.IRON_PICKAXE;case "knowledge"->Items.WRITABLE_BOOK;case "city"->Items.BELL;default->Items.SHIELD;});}
 private static Component filterLabel(Filter f,int count){return text("filter."+f.name().toLowerCase(Locale.ROOT),count);}
 private static ItemStack filterIcon(Filter f){return new ItemStack(switch(f){case ALL->Items.MAP;case ACTIONABLE->Items.LIME_DYE;case DONE->Items.EMERALD;case BLOCKED->Items.IRON_BARS;});}
 /** The label column: an icon and '3/6' at XS, icon and name at S (a fifth of the content at most), icon, name and six pips from M (30 %). */
 private int labelWidth(Font f){
  int names=0;for(var b:ResearchTreeModel.BRANCHES)names=Math.max(names,f.width(branch(b)));
  return switch(size){case XS->Math.min(20+f.width("6/6")+4,content.w()/5);case S->Math.min(18+names+6+SIDE_INDENT,content.w()/5);default->Math.min(18+names+6+SIDE_INDENT+44,content.w()*30/100);};
 }
 private static CompoundTag state(){return ConstructionOverlay.snapshot().getCompound("research");}
 /** The facts of the tree from the snapshot's research{}. */
 static ResearchTreeModel.Facts facts(CompoundTag t){
  var done=new HashSet<String>();for(var raw:t.getList("completed",Tag.TAG_STRING))done.add(raw.getAsString());int hall=t.getInt("hall");
  var queue=new ArrayList<String>();for(var raw:t.getList("queue",Tag.TAG_STRING))queue.add(raw.getAsString());
  var paid=new HashMap<String,Integer>();var p=t.getCompound("paid");for(var k:p.getAllKeys())paid.put(k,p.getInt(k));
  // AD-136: the level-I nodes ordered paid, what the stock holds of each open level-I price, and the works credited before the rework.
  var orders=new ArrayList<String>();for(var raw:t.getList("resourceOrders",Tag.TAG_STRING))orders.add(raw.getAsString());
  var stock=new HashMap<String,List<int[]>>();var st=t.getCompound("stock");
  for(var k:st.getAllKeys()){var lines=new ArrayList<int[]>();for(var raw:st.getList(k,Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;lines.add(new int[]{r.getInt("have"),r.getInt("need")});}stock.put(k,lines);}
  return new ResearchTreeModel.Facts(done,hall,BuildingTiers.hallResearch(hall+1),t.getString("selected"),queue,paid,orders,stock,t.getInt("credit"));
 }
 /** The office colour of a state: studied green, can be chosen blue, waits for the hall amber, needs other research grey, the target gold. */
 static Tone tone(State s){return switch(s){case DONE->Tone.OK;case AVAILABLE,PARTIAL,QUEUED,ORDERED->Tone.ACTION;case TARGET->Tone.INFO;case HALL->Tone.WAIT;default->Tone.OFF;};}
 private Component stateText(ResearchTreeModel.Facts f,Node n,State s){return switch(s){
  case DONE->text("completed");case TARGET->v2("state.target");case QUEUED->v2("state.queued",f.queue().indexOf(n.id())+1);
  case ORDERED->v2("state.ordered");
  case AVAILABLE->n.resourcePaid()?v2(f.ready(n.id())?"state.pay_ready":"state.pay_short"):text("available");case PARTIAL->v2("state.partial",f.paid(n.id()),n.works());case HALL->office("need_hall",OfficeUi.roman(n.tier()));
  default->office("first",titles(n.requires().stream().filter(id->!f.done().contains(id)).toList()));};}
 private static String titles(List<String> ids){return String.join(", ",ids.stream().map(id->OfficeUi.research(id).getString()).toList());}
 private static void order(String id,int action){var t=ConstructionOverlay.snapshot();if(t.hasUUID("village")&&t.getBoolean("canManage"))ConstructionNetwork.sendResearch(new ConstructionNetwork.ResearchOrder(t.getUUID("village"),t.getLong("epoch"),t.getLong("revision"),id==null?"":id,action));}
 /** The branch's own object: what a settlement of that craft works with. */
 static Item icon(String branch){
  return switch(branch){
   case "agriculture"->Items.WHEAT;case "forestry"->Items.OAK_SAPLING;case "mining"->Items.IRON_PICKAXE;case "livestock"->Items.LEAD;
   case "milling"->Items.GRINDSTONE;case "baking"->Items.BREAD;case "masonry"->Items.STONE_BRICKS;case "carpentry"->Items.CRAFTING_TABLE;
   case "metallurgy"->Items.IRON_INGOT;case "education"->Items.LECTERN;case "research"->Items.WRITABLE_BOOK;case "cartography"->Items.FILLED_MAP;
   case "construction"->Items.BRICKS;case "engineering"->Items.PISTON;case "logistics"->Items.CHEST;case "caravans"->Items.SADDLE;
   case "medicine"->Items.GLISTERING_MELON_SLICE;case "defense"->Items.SHIELD;case "military"->Items.IRON_SWORD;
   case "town_hall"->Items.BELL;case "housing"->Items.RED_BED;case "roads"->Items.DIRT_PATH;default->Items.BOOK;};
 }
 /** Another tab asks for a technology (Overview, Buildings, Levels): the next panel selects it and brings it into view. */
 static void focus(String id){if(id!=null&&ResearchCatalog.NODES.containsKey(id)){pending=id;remembered=id;}}
 private static String title(Node n){return OfficeUi.research(n.id()).getString();}

 // ---- the grid's geometry: rows of the shown branches, six columns, scrolled by pixels.
 private int cellW(){return CELL[density][0];} private int cellH(){return CELL[density][1];}
 private int colW(){return cellW()+10;} private int rowH(){return cellH()+4;}
 private int contentW(){return PAD+6*colW();} private int contentH(){return 3+rows.size()*rowH();}
 private boolean overflowX(){return contentW()>nodes.w();} private boolean overflowY(){return contentH()>nodes.h()-(overflowX()?4:0);}
 private int viewW(){return nodes.w()-(overflowY()?5:0);} private int viewH(){return nodes.h()-(overflowX()?5:0);}
 private void clamp(){scrollX=Math.max(0,Math.min(scrollX,Math.max(0,contentW()-viewW())));scrollY=Math.max(0,Math.min(scrollY,Math.max(0,contentH()-viewH())));}
 private int cellX(int tier){return (int)Math.round(nodes.x()+PAD+(tier-1)*colW()-scrollX);}
 private int cellY(int row){return (int)Math.round(nodes.y()+3+row*rowH()-scrollY);}
 /** The density the class asks for (XS/S icons, M tiles, L cards), lowered until six columns fit; a density the player chose wins. */
 private int defaultDensity(){
  if(densityChosen!=null)return Math.max(0,Math.min(2,densityChosen));int want=size==OfficeGrid.Size.L?2:size==OfficeGrid.Size.M?1:0;
  for(int d=want;d>0;d--)if(PAD+6*(CELL[d][0]+10)<=nodes.w()-5)return d;return 0;
 }
 /** Hit test in O(1): the row and column under the mouse, then whether it is on the cell and not in the gap. */
 private Node at(double mx,double my){
  if(!new Rect(nodes.x(),nodes.y(),viewW(),viewH()).contains(mx,my))return null;
  double rx=mx-nodes.x()-PAD+scrollX,ry=my-nodes.y()-3+scrollY;if(rx<0||ry<0)return null;int col=(int)(rx/colW()),row=(int)(ry/rowH());
  if(col>5||row>=rows.size()||rx-col*colW()>=cellW()||ry-row*rowH()>=cellH())return null;return ResearchTreeModel.at(rows.get(row),col+1);
 }
 /** The smallest scroll that shows the node's cell whole. */
 private void reveal(String id){
  var n=ResearchCatalog.NODES.get(id);if(n==null)return;int row=rows.indexOf(n.branch());if(row<0)return;
  double left=PAD+(n.tier()-1)*colW(),top=3+row*rowH();
  if(left-4<scrollX)scrollX=left-4;else if(left+cellW()+4>scrollX+viewW())scrollX=left+cellW()+4-viewW();
  if(top-3<scrollY)scrollY=top-3;else if(top+cellH()+3>scrollY+viewH())scrollY=top+cellH()+3-viewH();clamp();
 }
 /** Selects a node and brings it into view, first clearing the group and state filter (not the search) when they hide it. */
 private void focusNode(String id){
  var n=ResearchCatalog.NODES.get(id);if(n==null)return;selected=remembered=id;detailScroll=0;refreshRows();
  if(!rows.contains(n.branch())){group=null;filter=Filter.ALL;rowsKey="";refreshRows();}
  if(!rows.contains(n.branch())){search.setValue("");refreshRows();}
  reveal(id);
 }
 private void setGroup(String g){group=g;rowsKey="";keepSelectionVisible();}
 private void keepSelectionVisible(){refreshRows();clamp();if(selected!=null){var n=ResearchCatalog.NODES.get(selected);if(n!=null&&rows.contains(n.branch()))reveal(selected);}}
 /** The rows shown, rebuilt only when the filter, the search or the research facts change. */
 private void refreshRows(){
  var t=state();String key=(group==null?"*":group)+"|"+filter+"|"+(matchIds==null?"":String.join(",",matchIds))+"|"+t.getList("completed",Tag.TAG_STRING)+t.getString("selected")+t.getList("queue",Tag.TAG_STRING)+t.getCompound("paid")+t.getInt("hall");
  if(key.equals(rowsKey))return;rowsKey=key;rows=ResearchTreeModel.rows(group,filter,matchIds,facts(t));clamp();
 }
 private void searched(String value){
  matches=ResearchTreeModel.search(value,ResearchPanel::title);matchIds=value.trim().isEmpty()?null:new HashSet<>(matches.stream().map(Node::id).toList());matchIndex=0;rowsKey="";refreshRows();
  if(!matches.isEmpty())showMatch(matches.get(0).id());
 }
 /** Enter in the search: the next match (round the end). */
 void nextMatch(){if(matches.isEmpty())return;matchIndex=(matchIndex+1)%matches.size();showMatch(matches.get(matchIndex).id());}
 /** A match is selected and shown: a group or state filter that hides its row gives way (the counter counts every match). */
 private void showMatch(String id){
  var n=ResearchCatalog.NODES.get(id);selected=remembered=id;detailScroll=0;refreshRows();
  if(n!=null&&!rows.contains(n.branch())){group=null;filter=Filter.ALL;rowsKey="";refreshRows();}
  reveal(id);
 }
 void toTarget(){var t=state().getString("selected");if(ResearchCatalog.NODES.containsKey(t))focusNode(t);}
 void nextAvailable(int dir){refreshRows();var id=ResearchTreeModel.nextAvailable(selected(state()),rows,facts(state()),dir,filter,matchIds);if(id!=null){selected=remembered=id;detailScroll=0;reveal(id);}}
 /** Without a remembered node: the current target, else the first technology that can be chosen now, so the player sees where to look. */
 private String selected(CompoundTag t){
  if(selected!=null&&ResearchCatalog.NODES.containsKey(selected))return selected;
  String target=t.getString("selected");if(ResearchCatalog.NODES.containsKey(target)){selected=target;refreshRows();reveal(target);return selected;}
  if(!t.contains("completed"))return "research.1";
  var f=facts(t);for(var n:ResearchCatalog.NODES.values())if(ResearchTreeModel.reason(f,n).equals("available")){selected=n.id();refreshRows();reveal(n.id());return selected;}
  return selected="research.1";
 }

 // ---- accessors for the probes
 String selected(){return selected(state());}
 int[] frameCenter(String id){focusNode(id);var n=ResearchCatalog.get(id);int row=rows.indexOf(n.branch());return new int[]{cellX(n.tier())+cellW()/2,cellY(row)+cellH()/2};}
 OfficeButton choose(){return choose;} OfficeButton pause(){return pause;} OfficeButton queue(){return queue;} OfficeButton dequeue(){return dequeue;} EditBox search(){return search;}
 OfficeButton filterButton(){return filterButton;} OfficeButton nextButton(){return next;} OfficeButton targetButton(){return toTarget;}
 Rect canvas(){return canvas;} Rect details(){return side;} Rect labelColumn(){return labels;} Rect grid(){return nodes;}
 List<String> visibleBranches(){refreshRows();return List.copyOf(rows);}
 int density(){return density;} int matchCount(){return matches.size();} int matchIndex(){return matchIndex;} List<String> matchList(){return matches.stream().map(Node::id).toList();}
 /** The first row wholly on screen, and how many rows are wholly on screen. */
 int firstRow(){return (int)Math.ceil(scrollY/rowH());}
 int rowsShown(){int n=0;for(int r=0;r<rows.size();r++){int y=cellY(r);if(y>=nodes.y()&&y+cellH()<=nodes.y()+viewH())n++;}return n;}
 boolean cellVisible(String id){var n=ResearchCatalog.NODES.get(id);if(n==null)return false;int row=rows.indexOf(n.branch());if(row<0)return false;int x=cellX(n.tier()),y=cellY(row);
  return x>=nodes.x()&&x+cellW()<=nodes.x()+viewW()&&y>=nodes.y()&&y+cellH()<=nodes.y()+viewH();}
 /** AD-133 probe hooks: how many short rows the card drew, how many fact lines stayed behind «Подробнее», whether it is open,
  *  and whether the collapsed card needs the scrollbar at all. */
 int cardRows(){return cardRows;} int cardHidden(){return cardHidden;} boolean cardExpanded(){return expanded;}
 boolean cardOverflow(){return detailHeight>details.h();} int cardHeight(){return detailHeight;} Rect cardArea(){return details;}
 static void collapseCard(){expanded=false;}
 State stateOf(String id){return ResearchTreeModel.state(facts(state()),ResearchCatalog.get(id));}
 /** The probe's reset between passes: all groups, every state, no search, the density of the class. */
 void resetView(){group=null;filter=Filter.ALL;densityChosen=null;search.setValue("");density=defaultDensity();rowsKey="";refreshRows();scrollX=scrollY=0;}
 void setGroupForProbe(String g){setGroup(g);}
 /** Probe hook: every row and column is on screen (a group filter can make the tree honestly short). */
 boolean sparse(){refreshRows();return !overflowX()&&!overflowY();}
 /** The panel's own layout rules: bars, grid and details apart, all inside the content; the search where the class puts it; branch
  *  names inside their column; the column at most a fifth of the content below M and 30 % from M. */
 List<String> layoutProblems(){
  var out=new ArrayList<String>();var parts=new ArrayList<Rect>();var names=new ArrayList<String>();
  if(strip!=null){parts.add(strip);names.add("summary");}parts.add(bar);names.add("toolbar");parts.add(canvas);names.add("canvas");parts.add(side);names.add("details");
  for(int i=0;i<parts.size();i++){if(!content.containsRect(parts.get(i)))out.add("research "+names.get(i)+" outside the content");for(int j=i+1;j<parts.size();j++)if(parts.get(i).intersects(parts.get(j)))out.add("research "+names.get(i)+" overlaps "+names.get(j));}
  for(var b:new OfficeButton[]{choose,pause,queue,dequeue})if(b.visible){var r=Rect.of(b);for(int i=0;i<parts.size();i++)if(r.intersects(parts.get(i)))out.add("research button '"+b.getMessage().getString()+"' overlaps the "+names.get(i));if(r.y()<content.bottom())out.add("research button above the footer");}
  var sr=new Rect(search.getX()-1,search.getY()-1,search.getWidth()+2,search.getHeight()+2);
  if(size.atLeast(OfficeGrid.Size.M)?!bar.containsRect(sr):!side.containsRect(sr))out.add("search box outside the "+(size.atLeast(OfficeGrid.Size.M)?"toolbar":"details column"));
  if(font.width(searchHint)>search.getInnerWidth())out.add("search hint wider than the search box");
  for(var b:toolbar())if(b.visible&&!bar.containsRect(Rect.of(b)))out.add("toolbar button outside the toolbar: "+b.getMessage().getString());
  for(var r:labelRects)if(!labels.containsRect(r))out.add("branch label ("+r.x()+","+r.y()+" "+r.w()+"x"+r.h()+") exceeds the label column");
  if(size.atLeast(OfficeGrid.Size.M)?labels.w()>content.w()*30/100:labels.w()>content.w()/5)out.add("label column wider than "+(size.atLeast(OfficeGrid.Size.M)?"30 %":"a fifth")+" of the content");
  return out;
 }
 private List<OfficeButton> toolbar(){var out=new ArrayList<OfficeButton>(groupButtons);out.add(groupCycle);out.add(filterButton);out.add(toTarget);out.add(next);return out;}

 // ---- toolbar and footer
 /** Groups as labelled buttons when they fit, else as icons, else one cycling button; the filter shows its count when there is room. */
 private void layoutBar(){
  int x=bar.x(),end=size.atLeast(OfficeGrid.Size.M)?search.getX()-6:bar.right();
  if(merged){targetChip=new Rect(x,bar.y(),Math.min(64,Math.max(40,bar.w()/5)),18);x=targetChip.right()+4;}else targetChip=null;
  int fixed=3*(18+2);int labels=0;for(var b:groupButtons)labels+=b.prefWidth(font)+2;int icons=groupButtons.size()*20;
  int room=end-x-fixed;barMode=labels<=room?0:icons<=room?1:2;
  for(var b:groupButtons){if(barMode==2){b.at(new Rect(x,bar.y(),18,18));continue;}int w=barMode==0?b.prefWidth(font):18;b.at(new Rect(x,bar.y(),w,18));b.hint(barMode==0?null:b.getMessage());x+=w+2;}
  groupCycle.at(new Rect(x,bar.y(),18,18));if(barMode==2)x+=20;
  int left=end-x-2*20;int fw=filterButton.prefWidth(font);boolean fl=fw<=left;filterButton.at(new Rect(x,bar.y(),fl?fw:18,18));filterButton.hint(fl?null:filterButton.getMessage());x+=(fl?fw:18)+2;
  toTarget.at(new Rect(x,bar.y(),18,18));toTarget.hint(text("to_target"));x+=20;next.at(new Rect(x,bar.y(),18,18));next.hint(text("next_available"));
  filterLabelled=fl;
 }
 /** 0 labelled group buttons, 1 icon group buttons, 2 one cycling group button. */
 private int barMode;private boolean filterLabelled;
 private void barVisible(boolean visible){for(var b:groupButtons)b.visible=visible&&barMode<2;groupCycle.visible=visible&&barMode==2;filterButton.visible=toTarget.visible=next.visible=visible;}
 /** Labelled buttons when all fit left of '?' and Done, otherwise icon buttons with the label in the tooltip. */
 private void placeFooter(){
  var shown=new ArrayList<OfficeButton>(List.of(choose,pause,queue));if(dequeue.visible)shown.add(dequeue);
  var flow=layout.flow();var s=new Rect[shown.size()];boolean ok=true;for(int i=0;i<s.length;i++){s[i]=flow.next(shown.get(i).prefWidth(font));if(s[i]==null)ok=false;}
  compact=!ok;if(compact){flow=layout.flow();for(int i=0;i<s.length;i++)s[i]=flow.next(22);}
  for(int i=0;i<s.length;i++){var b=shown.get(i);b.at(s[i]);if(s[i]==null)b.visible=false;}
 }
 private Component reasonOf(CompoundTag snapshot){return Component.translatable(snapshot.getString("lockReason").equals("unsafe")?"office.villageastra.reason.unsafe":"office.villageastra.reason.only_mayor");}
 /** An inactive button names why; an icon button keeps its label in front of the reason. Set only when the text changes, so the tooltip does not restart. */
 private void why(OfficeButton b,Component label,Component r){
  Component want=r==null?null:compact?label.copy().append(": ").append(r):r;var have=b.reason();
  if(want==null?have!=null:have==null||!have.getString().equals(want.getString()))b.reason(want);if(compact)b.hint(label);else b.hint(null);
 }
 void tick(boolean visible){
  search.visible=visible;search.tick();
  var snapshot=ConstructionOverlay.snapshot();var t=state();var f=facts(t);boolean manage=snapshot.getBoolean("canManage");
  String id=selected(t),target=t.getString("selected");var n=ResearchCatalog.get(id);String r=ResearchTreeModel.reason(f,n);
  boolean tier1=n.resourcePaid(),ordered=tier1&&f.orders().contains(id);boolean inQueue=f.queue().contains(id);dequeue.visible=visible&&(inQueue||ordered);
  // AD-136: on a level-I node the first button pays it in resources (or orders it paid once the porters bring them), the last cancels that order.
  Component chooseLabel=tier1?v2("pay"):text("choose"),dequeueLabel=tier1?v2("cancel_order"):text("dequeue");
  if(!chooseLabel.getString().equals(choose.getMessage().getString())){choose.setMessage(chooseLabel);choose.icon(new ItemStack(tier1?Items.CHEST:Items.TARGET));}
  if(!dequeueLabel.getString().equals(dequeue.getMessage().getString()))dequeue.setMessage(dequeueLabel);
  for(var b:List.of(choose,pause,queue))b.visible=visible;placeFooter();
  choose.active=manage&&r.equals("available")&&!id.equals(target)&&!ordered&&(!tier1||f.ready(id)||f.orders().size()<org.villageastra.domain.ScienceBalance.RESOURCE_ORDERS);
  var chooseWhy=!manage?reasonOf(snapshot):!r.equals("available")?stateText(f,n,ResearchTreeModel.state(f,n)):id.equals(target)?text("target",OfficeUi.research(id)):ordered?v2("state.ordered"):!choose.active?v2("orders_full",org.villageastra.domain.ScienceBalance.RESOURCE_ORDERS):null;why(choose,chooseLabel,chooseWhy);
  pause.active=manage&&!target.isEmpty();
  why(pause,text("pause"),!manage?reasonOf(snapshot):target.isEmpty()?text("target",text("none")):null);
  // The server refuses a node already in the queue: the reason then lists the queue.
  queue.active=choose.active&&!tier1&&f.done().contains("research.1")&&f.queue().size()<org.villageastra.world.ResearchEffects.QUEUE&&!inQueue;
  why(queue,text("enqueue"),tier1?v2("tier1_no_queue"):!choose.active?chooseWhy:!f.done().contains("research.1")?Component.translatable("office.villageastra.reason.research",OfficeUi.research("research.1")):inQueue?text("queue",titles(f.queue())):f.queue().size()>=8?text("queue",f.queue().size()+" / 8"):null);
  dequeue.active=manage&&(inQueue||ordered);why(dequeue,dequeueLabel,!manage?reasonOf(snapshot):null);
  // Toolbar state: the chosen group green, the filter with its count.
  for(int i=0;i<groupButtons.size();i++){String g=i==0?null:ResearchTreeModel.GROUP_IDS.get(i-1);groupButtons.get(i).tone(Objects.equals(g,group)?Tone.OK:Tone.INFO);}
  groupCycle.setMessage(groupLabel(group));groupCycle.icon(groupIcon(group));groupCycle.tone(group==null?Tone.INFO:Tone.OK);groupCycle.hint(groupLabel(group));
  int count=0;for(var node:ResearchCatalog.NODES.values())if(filter!=Filter.ALL&&filter.accepts(ResearchTreeModel.state(f,node)))count++;if(filter==Filter.ALL)count=ResearchCatalog.NODES.size();
  var label=filterLabel(filter,count);if(!label.getString().equals(filterButton.getMessage().getString())){filterButton.setMessage(label);filterButton.icon(filterIcon(filter));layoutBar();}
  filterButton.tone(filter==Filter.ALL?Tone.INFO:Tone.OK);if(!filterLabelled)filterButton.hint(label.copy().append("\n").append(text("filter.cycle")));
  toTarget.active=ResearchCatalog.NODES.containsKey(target);toTarget.reason(text("target",text("none")));
  barVisible(visible);
 }

 // ---- input
 boolean click(double mx,double my,int button){
  if(button!=0)return false;
  if(targetChip!=null&&targetChip.contains(mx,my)){toTarget();return true;}
  if(counter.contains(mx,my)&&!matches.isEmpty()){nextMatch();return true;}
  for(var l:List.copyOf(links))if(l.r.contains(mx,my)){l.run.run();return true;}
  // Scrollbars: grab the thumb (or jump to it) and drag.
  if(overflowY()&&new Rect(nodes.x()+viewW(),nodes.y(),5,viewH()).contains(mx,my)){dragV=true;dragScrollbarV(my);return true;}
  if(overflowX()&&new Rect(nodes.x(),nodes.y()+viewH(),viewW(),5).contains(mx,my)){dragH=true;dragScrollbarH(mx);return true;}
  if(!nodes.contains(mx,my))return false;
  // The press selects at once (the probes click and read the selection); moving 3 px turns it into a drag of the grid.
  pressed=true;panning=false;pressX=mx;pressY=my;var n=at(mx,my);
  if(n!=null){long now=Util.getMillis();boolean twice=n.id().equals(lastClickId)&&now-lastClick<=250;selected=remembered=n.id();detailScroll=0;lastClick=now;lastClickId=n.id();
   if(twice){tick(true);if(choose.active)choose.onPress();lastClickId=null;}}
  return true;
 }
 private void dragScrollbarV(double my){double f=(my-nodes.y())/(double)Math.max(1,viewH());scrollY=f*contentH()-viewH()/2.0;clamp();}
 private void dragScrollbarH(double mx){double f=(mx-nodes.x())/(double)Math.max(1,viewW());scrollX=f*contentW()-viewW()/2.0;clamp();}
 void release(){pressed=panning=dragV=dragH=false;}
 boolean drag(double mx,double my,double dx,double dy){
  if(dragV){dragScrollbarV(my);return true;}if(dragH){dragScrollbarH(mx);return true;}
  if(!pressed)return false;if(!panning&&Math.abs(mx-pressX)+Math.abs(my-pressY)>3)panning=true;
  if(panning){scrollX-=dx;scrollY-=dy;clamp();}return true;
 }
 /** The wheel scrolls rows (Shift: columns, Ctrl: the density about the node under the mouse), or the details. Fractions add up. */
 boolean scroll(double mx,double my,double delta,boolean shift,boolean ctrl){
  if(side.contains(mx,my)&&!search.isMouseOver(mx,my)){detailScroll=Math.max(0,Math.min(Math.max(0,detailHeight-details.h()),detailScroll-(int)Math.round(delta*10)));return true;}
  if(!canvas.contains(mx,my)&&!bar.contains(mx,my))return false;
  if(ctrl){wheelY+=delta;if(Math.abs(wheelY)<1)return true;int step=(int)Math.signum(wheelY);wheelY=0;int nd=Math.max(0,Math.min(2,density+step));if(nd==density)return true;
   double ax=Math.max(0,mx-nodes.x()-PAD+scrollX)/colW(),ay=Math.max(0,my-nodes.y()-3+scrollY)/rowH();density=nd;densityChosen=nd;
   scrollX=ax*colW()-(mx-nodes.x()-PAD);scrollY=ay*rowH()-(my-nodes.y()-3);clamp();return true;}
  if(shift){wheelX+=delta;while(Math.abs(wheelX)>=1){int s=(int)Math.signum(wheelX);scrollX-=s*colW();wheelX-=s;}clamp();return true;}
  wheelY+=delta;while(Math.abs(wheelY)>=1){int s=(int)Math.signum(wheelY);scrollY=Math.round(scrollY/rowH()-s)*(double)rowH();wheelY-=s;}clamp();return true;
 }
 /** The tree's keys (the screen hands them over only when the search is not being typed in). */
 boolean key(int key,int mods){
  refreshRows();String id=selected(state());boolean shift=(mods&1)!=0;
  switch(key){
   case 262->{move(id,1,0);return true;} case 263->{move(id,-1,0);return true;}
   case 264->{move(id,0,1);return true;} case 265->{move(id,0,-1);return true;}
   case 267->{move(id,0,Math.max(1,rowsShown()));return true;} case 266->{move(id,0,-Math.max(1,rowsShown()));return true;}
   case 268->{move(id,0,-rows.size());return true;} case 269->{move(id,0,rows.size());return true;}
   case 257,335->{tick(true);if(choose.active)choose.onPress();return true;}
   case 81->{tick(true);if(queue.active)queue.onPress();return true;}
   case 78->{nextAvailable(shift?-1:1);return true;}
   case 84->{toTarget();return true;}
   case 48->{setGroup(null);return true;}
   default->{if(key>=49&&key<=48+ResearchTreeModel.GROUP_IDS.size()){setGroup(ResearchTreeModel.GROUP_IDS.get(key-49));return true;}return false;}
  }
 }
 private void move(String id,int dx,int dy){var to=ResearchTreeModel.move(id,dx,dy,rows);if(to!=null){selected=remembered=to;detailScroll=0;reveal(to);}}
 boolean searchFocused(){return search.isFocused();}
 /** Esc in the search: clear it and let go of the keyboard. */
 void leaveSearch(){search.setValue("");search.setFocused(false);}

 // ---- drawing
 private static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int colour){
  // An elbow like the advancement screen: out of the prerequisite, across at the middle, into the technology.
  int mid=(x0+x1)/2;
  g.fill(Math.min(x0,mid),y0,Math.max(x0,mid)+1,y0+1,colour);
  g.fill(mid,Math.min(y0,y1),mid+1,Math.max(y0,y1)+1,colour);
  g.fill(Math.min(mid,x1),y1,Math.max(mid,x1)+1,y1+1,colour);
 }
 private static final String[] BELL={".#.","###","#.#"};
 void render(GuiGraphics g,Font f,int mx,int my){
  var t=state();var facts=facts(t);refreshRows();links.clear();String id=selected(t);int hall=t.getInt("hall");
  if(strip!=null){OfficeUi.block("summary",strip);summary(g,f,t,mx,my);OfficeUi.endBlock();}
  if(targetChip!=null){OfficeUi.block("target",targetChip);compactTarget(g,f,t,mx,my);OfficeUi.endBlock();}
  if(!matches.isEmpty()||!search.getValue().trim().isEmpty()){String c=matches.isEmpty()?"0":(matchIndex+1)+"/"+matches.size();OfficeUi.drawText(g,f,c,counter.right()-f.width(c)-2,counter.y()+5,matches.isEmpty()?Tone.BAD.fg():OfficeUi.MUTED);
   OfficeUi.tips().add(counter,v2("matches",matches.size()));}
  g.fill(canvas.x(),canvas.y(),canvas.right(),canvas.bottom(),0xFF101820);g.fill(labels.x(),labels.y(),labels.right(),labels.bottom(),OfficeUi.CARD);g.fill(steps.x(),steps.y(),steps.right(),steps.bottom(),0xFF151E25);
  hovered=null;var hover=at(mx,my);if(hover!=null)hovered=hover.id();
  var focus=ResearchCatalog.get(hovered!=null?hovered:id);
  var view=new Rect(nodes.x(),nodes.y(),viewW(),viewH());
  if(rows.isEmpty()){OfficeUi.block("grid",view);OfficeUi.label(g,f,v2("no_rows"),view.x()+4,view.y()+6,view.w()-8,OfficeUi.MUTED);OfficeUi.endBlock();}
  OfficeUi.clip(g,view);OfficeUi.block("grid",view);
  int first=Math.max(0,(int)(scrollY/rowH())-1),last=Math.min(rows.size()-1,(int)((scrollY+viewH())/rowH())+1);int w=cellW(),h=cellH();
  // Lines inside a branch always, between neighbouring steps; lines from other branches only for the node looked at.
  for(int r=first;r<=last;r++){int y=cellY(r)+h/2;for(int tier=1;tier<6;tier++){var a=ResearchTreeModel.at(rows.get(r),tier);if(a==null||ResearchTreeModel.at(rows.get(r),tier+1)==null)continue;int x=cellX(tier)+w;g.fill(x,y,x+10,y+1,facts.done().contains(a.id())?0xFF3E7A46:0xFF3A444E);}}
  // AD-136: a side branch hangs under its parent: a line down from the parent's node of the same level into the side node.
  for(int r=0;r<rows.size();r++){var b=rows.get(r);if(!ResearchTreeModel.side(b))continue;int pr=rows.indexOf(ResearchTreeModel.parent(b));if(pr<0)continue;
   var sn=ResearchTreeModel.at(b,ResearchTreeModel.from(b));var pn=ResearchTreeModel.at(ResearchTreeModel.parent(b),sn.tier());if(pn==null)continue;int x=cellX(sn.tier())+w/2;
   g.fill(x,cellY(pr)+h,x+1,cellY(r),facts.done().contains(pn.id())?0xFF3E7A46:0xFF4A6A8A);}
  int fr=rows.indexOf(focus.branch());
  for(var need:focus.requires()){var from=ResearchCatalog.get(need);if(from.branch().equals(focus.branch()))continue;int rr=rows.indexOf(from.branch());
   if(rr>=0&&fr>=0)line(g,cellX(from.tier())+w,cellY(rr)+h/2,cellX(focus.tier()),cellY(fr)+h/2,facts.done().contains(need)?Tone.OK.fg():Tone.OFF.fg());}
  for(var need:ResearchTreeModel.unlocks(focus.id())){var to=ResearchCatalog.get(need);if(to.branch().equals(focus.branch()))continue;int rr=rows.indexOf(to.branch());
   if(rr>=0&&fr>=0)line(g,cellX(focus.tier())+w,cellY(fr)+h/2,cellX(to.tier()),cellY(rr)+h/2,0xFF4A6A8A);}
  noLevel.clear();
  for(int r=first;r<=last;r++)for(int tier=1;tier<=6;tier++){var n=ResearchTreeModel.at(rows.get(r),tier);int x=cellX(tier),y=cellY(r);
   if(x+w<view.x()||x>view.right()||y+h<view.y()||y>view.bottom())continue;
   if(n==null){if(ResearchTreeModel.noLevel(rows.get(r),tier))emptyCell(g,f,rows.get(r),tier,x,y,w,h);continue;}
   cell(g,f,facts,n,x,y,w,h,n.id().equals(id),n==hover,hall);}
  // A requirement in a row the filter hides: an arrow at the node's left edge says it is above or below.
  if(fr>=0)for(var need:focus.requires()){var from=ResearchCatalog.get(need);if(rows.contains(from.branch()))continue;int x=cellX(focus.tier())-9,y=cellY(fr)+(h-7)/2;
   int dir=ResearchTreeModel.BRANCHES.indexOf(from.branch())<ResearchTreeModel.BRANCHES.indexOf(focus.branch())?0:4;OfficeUi.arrow(g,x,y,dir,Tone.OFF.fg());OfficeUi.tips().add(x,y,7,7,List.of(v2("hidden_requirement",OfficeUi.research(need))));break;}
  OfficeUi.endBlock();OfficeUi.unclip(g);
  scrollbars(g);
  // Branch names stay in their column while the grid moves beside them; a cut name shows in full under the mouse.
  labelRects.clear();OfficeUi.clip(g,labels);OfficeUi.block("labels",labels);
  for(int r=first;r<=last;r++){int y=cellY(r);if(y+h<labels.y()||y>labels.bottom())continue;branchLabel(g,f,facts,rows.get(r),y,h);}
  OfficeUi.endBlock();OfficeUi.unclip(g);
  // Step numbers over the columns: a step past the hall's reach is grey; a line marks where the hall's reach ends.
  OfficeUi.clip(g,steps);
  for(int i=1;i<=6;i++){String step=OfficeUi.roman(i).getString();int cx=cellX(i)+w/2,x=cx-f.width(step)/2;
   if(x>=steps.x()&&x+f.width(step)<=steps.right()){OfficeUi.drawText(g,f,step,x,steps.y()+2,i<=Math.max(1,hall)?OfficeUi.TITLE:OfficeUi.MUTED);
    OfficeUi.tips().add(cellX(i),steps.y(),w,STEPS,List.of(i<=Math.max(1,hall)?v2("step_open",OfficeUi.roman(i)):v2("step_hall",OfficeUi.roman(i),OfficeUi.roman(i))));}}
  OfficeUi.unclip(g);
  int bx=cellX(Math.max(1,Math.min(6,hall)))+w+5;if(hall<6&&bx>nodes.x()&&bx<nodes.x()+viewW()){OfficeUi.clip(g,view);g.fill(bx,nodes.y(),bx+1,nodes.y()+viewH(),0x80E9CD92);OfficeUi.unclip(g);}
  // The details column always shows the selected technology, the one the footer buttons act on; a hovered one has its tooltip.
  details(g,f,t,facts,ResearchCatalog.get(id),mx,my);
 }
 private void scrollbars(GuiGraphics g){
  if(overflowY()){int x=nodes.x()+viewW()+1,th=Math.max(8,viewH()*viewH()/Math.max(1,contentH())),ty=nodes.y()+(int)((viewH()-th)*scrollY/Math.max(1,contentH()-viewH()));g.fill(x,nodes.y(),x+3,nodes.y()+viewH(),OfficeUi.TRACK);g.fill(x,ty,x+3,ty+th,OfficeUi.MUTED);}
  if(overflowX()){int y=nodes.y()+viewH()+1,tw=Math.max(8,viewW()*viewW()/Math.max(1,contentW())),tx=nodes.x()+(int)((viewW()-tw)*scrollX/Math.max(1,contentW()-viewW()));g.fill(nodes.x(),y,nodes.x()+viewW(),y+3,OfficeUi.TRACK);g.fill(tx,y,tx+tw,y+3,OfficeUi.MUTED);}
 }
 /** The visible part of a label line: a row half scrolled out is cut by the scissor, so only its width can exceed the column. */
 /** The hint the search box draws (unclipped by EditBox), for the layout rule that it fits the box. */
 private String searchHint="";
 private void labelRect(int x,int y,int w){int top=Math.max(y,labels.y()),bottom=Math.min(y+9,labels.bottom());if(bottom>top)labelRects.add(new Rect(x,top,w,bottom-top));}
 /** One branch's label: the icon and the studied count at XS, the name at S, the name and six pips from M. */
 private void branchLabel(GuiGraphics g,Font f,ResearchTreeModel.Facts facts,String b,int y,int h){
  boolean sideRow=ResearchTreeModel.side(b);int steps=ResearchTreeModel.size(b);
  int x=labels.x()+2+(sideRow&&size!=OfficeGrid.Size.XS?SIDE_INDENT:0),ty=y+(h-8)/2;int studied=0;for(int i=1;i<=6;i++){var n=ResearchTreeModel.at(b,i);if(n!=null&&facts.done().contains(n.id()))studied++;}
  var name=branch(b);var tip=new ArrayList<Component>(List.of(name,v2("branch_count",studied,steps)));
  if(sideRow)tip.add(v2("side_branch",branch(ResearchTreeModel.parent(b)),OfficeUi.roman(ResearchTreeModel.from(b))));
  tip.add(text("group."+ResearchTreeModel.groupOf(b)).copy().withStyle(s->s.withColor(OfficeUi.MUTED&0xFFFFFF)));
  // AD-136: a side branch's label hangs off its parent's with a small elbow.
  if(sideRow&&x>labels.x()+2){int ex=labels.x()+5;g.fill(ex,y-2,ex+1,y+h/2+1,OfficeUi.MUTED);g.fill(ex,y+h/2,x-1,y+h/2+1,OfficeUi.MUTED);}
  OfficeUi.icon12(g,new ItemStack(icon(b)),x,y+(h-12)/2);int tx=x+14;
  if(size==OfficeGrid.Size.XS){int end=OfficeUi.drawText(g,f,studied+"/"+steps,tx,ty,studied==steps?Tone.OK.fg():OfficeUi.MUTED);labelRect(tx,ty,end-tx);OfficeUi.tips().add(x,y,labels.w()-4,h,tip);return;}
  int pipsW=size.atLeast(OfficeGrid.Size.M)?40:0,room=labels.right()-tx-4-(pipsW>0?pipsW+4:0);
  int end=OfficeUi.label(g,f,name,tx,ty,room,OfficeUi.TEXT);labelRect(tx,ty,Math.max(0,end-tx));
  if(pipsW>0)OfficeUi.pips(g,labels.right()-4-pipsW,y+(h-5)/2,studied,studied,steps);
  OfficeUi.tips().add(x,y,labels.w()-4,h,tip);
 }
 /** AD-136: a column a main branch has no level in (engineering VI: level VI of every branch needs Engineering V; the town hall I: the
  *  starting hall) - an empty dashed place with its reason, so the grid keeps its six columns. */
 private final List<String> noLevel=new ArrayList<>();
 private void emptyCell(GuiGraphics g,Font f,String b,int tier,int x,int y,int w,int h){
  for(int i=0;i<w;i+=4){g.fill(x+i,y,x+Math.min(w,i+2),y+1,0xFF3A444E);g.fill(x+i,y+h-1,x+Math.min(w,i+2),y+h,0xFF3A444E);}
  for(int i=0;i<h;i+=4){g.fill(x,y+i,x+1,y+Math.min(h,i+2),0xFF3A444E);g.fill(x+w-1,y+i,x+w,y+Math.min(h,i+2),0xFF3A444E);}
  OfficeUi.drawText(g,f,"-",x+(w-f.width("-"))/2,y+(h-8)/2,OfficeUi.MUTED);noLevel.add(b+"."+tier);
  String key=b.equals("engineering")||b.equals("town_hall")?"no_level."+b:"no_level";
  OfficeUi.tips().add(new Rect(x,y,w,h),branch(b).copy().append(" "+OfficeUi.roman(tier).getString()),v2(key,OfficeUi.roman(tier)));
 }
 /** Probe hook: the empty no-level places drawn in the last frame ("engineering.6"). */
 List<String> noLevelCells(){return List.copyOf(noLevel);}
 /** Probe hook: the last column (level VI) brought into view, the rows kept where they are. */
 void scrollToLastColumn(){scrollX=Math.max(0,contentW()-viewW());clamp();}
 private static final int SIDE_INDENT=8;
 /** A node's cell: the frame and fill of its state, the branch icon, a sign in the corner, volumes paid as a bar, the queue number,
  *  a bell when the next hall level needs it; the density adds the step numeral (tile) or the short title and volumes (card). */
 private void cell(GuiGraphics g,Font f,ResearchTreeModel.Facts facts,Node n,int x,int y,int w,int h,boolean sel,boolean hover,int hall){
  var s=ResearchTreeModel.state(facts,n);Tone tone=tone(s);boolean lit=ResearchTreeModel.lit(facts,n,filter,matchIds);
  // AD-136: a level-I node's bar is its price in the stock (items there / items needed), a level II-VI node's the works paid.
  int[] prog=n.resourcePaid()?facts.resources(n.id()):new int[]{facts.paid(n.id()),n.works()};int paid=prog[0],price=prog[1];
  if(sel)g.renderOutline(x-3,y-3,w+6,h+6,OfficeUi.TEXT);
  if(s==State.TARGET){g.renderOutline(x-2,y-2,w+4,h+4,OfficeUi.TITLE);}
  g.fill(x,y,x+w,y+h,s==State.DONE?Tone.OK.bg():hover?OfficeUi.ROW_HOVER:0xFF1A232C);g.renderOutline(x,y,w,h,s==State.TARGET?OfficeUi.TITLE:tone.fg());OfficeUi.box(new Rect(x,y,w,h),1,"cell");
  g.renderItem(new ItemStack(icon(n.branch())),x+3,y+(h-16)/2);
  g.pose().pushPose();g.pose().translate(0,0,200);
  if(s==State.LOCKED)g.fill(x+1,y+1,x+w-1,y+h-1,0x90202A30);
  // The sign in the corner, so the state reads without the colour.
  Tone sign=switch(s){case DONE->Tone.OK;case HALL,ORDERED->Tone.WAIT;case LOCKED->Tone.OFF;case AVAILABLE,PARTIAL->Tone.ACTION;default->null;};
  if(sign!=null){g.fill(x+w-8,y+1,x+w-1,y+8,0xFF1A232C);OfficeUi.sign(g,x+w-7,y+2,sign);}
  if(s==State.QUEUED){String q=String.valueOf(facts.queue().indexOf(n.id())+1);g.fill(x+1,y+1,x+3+f.width(q),y+10,Tone.ACTION.bg());OfficeUi.drawText(g,f,q,x+2,y+1,Tone.ACTION.fg());}
  if(facts.hallNext().contains(n.id())&&s!=State.DONE){for(int r=0;r<3;r++)for(int c=0;c<3;c++)if(BELL[r].charAt(c)=='#')g.fill(x+w-7+c,y+h-5+r,x+w-6+c,y+h-4+r,OfficeUi.TITLE);}
  if(density==0&&paid>0&&s!=State.DONE){int bar=(w-4)*Math.min(paid,price)/Math.max(1,price);g.fill(x+2,y+h-3,x+2+bar,y+h-1,Tone.INFO.bar());}
  if(density==1){OfficeUi.drawText(g,f,OfficeUi.roman(n.tier()).getString(),x+21,y+3,lit?OfficeUi.TEXT:OfficeUi.MUTED);
   if(s!=State.DONE){int bw=w-30;g.fill(x+21,y+15,x+21+bw,y+18,OfficeUi.TRACK);int fill=bw*Math.min(paid,price)/Math.max(1,price);if(fill>0)g.fill(x+21,y+15,x+21+fill,y+18,Tone.INFO.bar());}}
  if(density==2){var full=OfficeUi.research(n.id()).getString();int dash=full.indexOf(" — ");String sub=dash<0?full:full.substring(dash+3);
   String count=s==State.DONE?"":paid+"/"+price;int countX=x+w-9-f.width(count);
   OfficeUi.label(g,f,sub,x+21,y+4,(count.isEmpty()?x+w-9:countX-3)-(x+21),lit?OfficeUi.TEXT:OfficeUi.MUTED,List.of(OfficeUi.research(n.id())));
   if(!count.isEmpty())OfficeUi.drawText(g,f,count,countX,y+4,OfficeUi.MUTED);
   if(s!=State.DONE){int bw=w-30;g.fill(x+21,y+17,x+21+bw,y+20,OfficeUi.TRACK);int fill=bw*Math.min(paid,price)/Math.max(1,price);if(fill>0)g.fill(x+21,y+17,x+21+fill,y+20,Tone.INFO.bar());}}
  if(!lit)g.fill(x,y,x+w,y+h,0xB0101820);
  g.pose().popPose();
  if(hover){var tip=new ArrayList<Component>();tip.add(OfficeUi.research(n.id()));tip.add(stateText(facts,n,s).copy().withStyle(Style.EMPTY.withColor(tone.fg()&0xFFFFFF)));
   if(s!=State.DONE)tip.add(n.resourcePaid()?v2("resources_sum",paid,price):v2("works",paid,price));if(facts.hallNext().contains(n.id()))tip.add(v2("hall_mark",OfficeUi.roman(hall+1)));OfficeUi.tips().add(new Rect(x,y,w,h),tip.toArray(Component[]::new));}
 }
 /** One line that answers 'what are we studying': the target with its volumes and ETA, then the queue, or a blue 'choose a target'. */
 private void summary(GuiGraphics g,Font f,CompoundTag t,int mx,int my){
  var snapshot=ConstructionOverlay.snapshot();String target=t.getString("selected");var queued=t.getList("queue",Tag.TAG_STRING);var tip=new ArrayList<Component>();
  if(!ResearchCatalog.NODES.containsKey(target)){
   Tone tone=snapshot.getBoolean("canManage")?Tone.ACTION:Tone.OFF;OfficeUi.chip(g,f,strip.x(),strip.y(),Math.min(strip.w(),f.width(office("choose"))+21),ItemStack.EMPTY,office("choose"),Component.empty(),tone);
   tip.add(office("choose"));
  }else{
   var n=ResearchCatalog.get(target);int paid=t.getCompound("paid").getInt(target);var now=office("now",OfficeUi.research(target));var eta=OverviewPanel.eta(t,n,paid);
   int etaW=eta==null?0:Math.min(OfficeUi.tagWidth(f,eta.text()),Math.max(0,strip.w()/4));
   int cw=Math.min(strip.w()*11/20-etaW,27+f.width(now)+12);var chip=new Rect(strip.x(),strip.y(),Math.max(40,cw),18);
   OfficeUi.chip(g,f,chip.x(),chip.y(),chip.w(),new ItemStack(icon(n.branch())),now,Component.empty(),Tone.INFO,true,chip.contains(mx,my));links.add(new Link(chip,this::toTarget));
   int x=chip.right()+4,bw=Math.min(80,strip.right()-x);if(bw>=30){OfficeUi.bar(g,f,x,strip.y()+4,bw,paid,n.works(),Tone.INFO,Component.literal(paid+" / "+n.works()));x+=bw+4;}
   if(eta!=null&&etaW>=24&&x+etaW<=strip.right()){x+=OfficeUi.tag(g,f,x,strip.y()+4,eta.text(),eta.tone(),etaW)+4;}
   tip.add(now);tip.add(office("volumes",paid,n.works()));if(eta!=null)tip.add(eta.text());
   for(int i=0;i<queued.size();i++){
    var label=Component.literal((i+1)+". "+OfficeUi.fit(f,OfficeUi.research(queued.getString(i)).getString(),56));
    var more=office("queue_more",queued.size()-i);boolean last=i==queued.size()-1;
    // At most three queue tags, and always room for the '+n' tag after the ones shown.
    if(i<3&&x+OfficeUi.tagWidth(f,label)+(last?0:4+OfficeUi.tagWidth(f,more))<=strip.right()){x+=OfficeUi.tag(g,f,x,strip.y()+4,label,Tone.INFO)+4;continue;}
    if(x+OfficeUi.tagWidth(f,more)<=strip.right())OfficeUi.tag(g,f,x,strip.y()+4,more,Tone.INFO);break;
   }
  }
  if(!queued.isEmpty())tip.add(text("queue",titles(queued.stream().map(Tag::getAsString).toList())));
  String legacy=t.getString("legacyActive");if(!legacy.isEmpty())tip.add(text("legacy",Component.translatable("research.villageastra."+legacy),t.getLong("legacyProgress")));
  OfficeUi.tips().add(strip.x(),strip.y(),strip.w(),18,tip);
 }
 /** On short screens the target is an icon and a bar at the toolbar's start; its name, volumes, ETA and queue are in the tooltip. */
 private void compactTarget(GuiGraphics g,Font f,CompoundTag t,int mx,int my){
  var r=targetChip;String target=t.getString("selected");var tip=new ArrayList<Component>();boolean hover=r.contains(mx,my);
  if(!ResearchCatalog.NODES.containsKey(target)){Tone tone=ConstructionOverlay.snapshot().getBoolean("canManage")?Tone.ACTION:Tone.OFF;OfficeUi.chip(g,f,r.x(),r.y(),r.w(),new ItemStack(Items.TARGET),Component.empty(),Component.empty(),tone);tip.add(office("choose"));}
  else{var n=ResearchCatalog.get(target);int paid=t.getCompound("paid").getInt(target);
   g.fill(r.x(),r.y(),r.right(),r.bottom(),hover?OfficeUi.ROW_HOVER:Tone.INFO.bg());g.renderOutline(r.x(),r.y(),r.w(),r.h(),OfficeUi.TITLE);OfficeUi.box(r,1,"target");
   g.renderItem(new ItemStack(icon(n.branch())),r.x()+1,r.y()+1);int bw=r.w()-22;OfficeUi.bar(g,r.x()+19,r.y()+7,bw,4,paid,n.works(),Tone.INFO);
   var eta=OverviewPanel.eta(t,n,paid);tip.add(office("now",OfficeUi.research(target)));tip.add(office("volumes",paid,n.works()));if(eta!=null)tip.add(eta.text());}
  var queued=t.getList("queue",Tag.TAG_STRING);if(!queued.isEmpty())tip.add(text("queue",titles(queued.stream().map(Tag::getAsString).toList())));
  OfficeUi.tips().add(r,tip.toArray(Component[]::new));
 }
 /** AD-133: the card of the technology looked at — one title line, the state as a coloured chip with the volumes, the step and what it
  *  needs as small chips, and up to four short rows «было → стало». Every row keeps its whole sentence (with where it applies) in its
  *  tooltip, and «Подробнее» opens the full text — all the effect lines, what it needs and what it opens — inside the same card. */
 private String effectsOf;private List<Component> effectRows=List.of();private List<org.villageastra.world.ResearchEffects.Unlock> unlockRows=List.of();
 private static boolean expanded;private int cardRows,cardHidden;
 private void details(GuiGraphics g,Font f,CompoundTag t,ResearchTreeModel.Facts facts,Node n,int mx,int my){
  var r=details;g.fill(r.x(),r.y(),r.right(),r.bottom(),OfficeUi.CARD);OfficeUi.clip(g,r);OfficeUi.block("details",r);int mark=OfficeUi.tips().mark();
  if(!n.id().equals(effectsOf)){effectsOf=n.id();effectRows=org.villageastra.world.ResearchEffects.describe(n.id());unlockRows=org.villageastra.world.ResearchEffects.unlocks(n.id());expanded=false;detailScroll=0;}
  var s=ResearchTreeModel.state(facts,n);Tone tone=tone(s);int x=r.x()+3,w=r.w()-9,top=r.y()+3,y=top-detailScroll,paid=facts.paid(n.id());var title=OfficeUi.research(n.id());
  var whole=List.of(title,branch(n.branch()).copy().append(" · ").append(OfficeUi.roman(n.tier())));
  // 1. The name on one line beside the branch icon; the whole name, its branch and step stay in the tooltip.
  g.renderItem(new ItemStack(icon(n.branch())),x,y);OfficeUi.label(g,f,title.getString(),x+20,y+4,w-20,OfficeUi.TITLE,whole);
  OfficeUi.tips().add(x,y,w,18,whole);y+=20;
  // 2. The state as a coloured chip, the cost as its value; the reason, the volumes and the ETA in the tooltip.
  var stateLine=stateText(facts,n,s);boolean done=s==State.DONE;int[] res=facts.resources(n.id());
  // AD-136: level I shows its price in resources (items in the stock / needed), levels II-VI the scientific works paid.
  Component value=n.resourcePaid()?(done?v2("paid_resources"):Component.literal(res[0]+"/"+res[1])):Component.literal((done?n.works():paid)+"/"+n.works());
  OfficeUi.chip(g,f,x,y,w,new ItemStack(n.resourcePaid()?Items.CHEST:org.villageastra.VillageAstra.RESEARCH_VOLUME.get()),stateLine,value,tone);
  var tip=new ArrayList<Component>();tip.add(stateLine);tip.add(n.resourcePaid()?v2("resources_sum",done?res[1]:res[0],res[1]):v2("works",done?n.works():paid,n.works()));
  if(!n.resourcePaid()&&!done&&facts.credit()>0)tip.add(v2("credit",facts.credit()));
  if(s==State.TARGET){var eta=OverviewPanel.eta(t,n,paid);if(eta!=null)tip.add(eta.text());}
  OfficeUi.tips().add(new Rect(x,y,w,18),tip.toArray(Component[]::new));y+=20;
  // 2b. AD-136: a level-I price line by line: the item, what the hall and warehouse hold of it and what is needed.
  int resRows=0;resourceLines=0;if(n.resourcePaid()&&!done){resRows=resourceRows(g,f,t,n,x,y,w);y+=resRows*11+2;}
  // 3. The step and what the research needs, as small chips on one line.
  boolean chips=r.h()>=130;if(chips)y=chipRow(g,f,facts,n,x,y,w);
  // 4. The effects as short rows; how many fit is what the column has room for, four at most.
  var brief=ResearchCard.rows(n.id());int room=Math.max(1,(r.h()-83-(chips?13:0)-resRows*11-(resRows>0?2:0))/11);int shown=Math.min(brief.size(),Math.min(4,room));
  cardRows=shown;cardHidden=Math.max(0,effectRows.size()-shown);
  // AD-136 (§4.3): the grey «Будет: ...» line of an INTERIM or PLANNED card is never pushed behind «Подробнее».
  var rowsShown=new ArrayList<ResearchCard.Row>(brief.subList(0,shown));
  for(var row:brief)if(ResearchCard.future(row)&&!rowsShown.contains(row)&&shown>0){rowsShown.set(shown-1,row);break;}
  cardFuture=rowsShown.stream().anyMatch(ResearchCard::future);
  for(var row:rowsShown)y=effectRow(g,f,row,x,y,w);
  // 5. «Подробнее»: the full text of the card, without leaving it.
  var more=new Rect(x,y,w,18);boolean over=more.contains(mx,my);
  OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.BOOK),v2(expanded?"less":"more"),!expanded&&cardHidden>0?Component.literal("+"+cardHidden):Component.empty(),Tone.INFO,true,over);
  if(more.intersect(r).h()>0)links.add(new Link(more.intersect(r),()->{expanded=!expanded;detailScroll=0;}));
  y+=20;
  if(expanded){
   if(!n.requires().isEmpty()){y=OfficeUi.header(g,f,x,y,w,null,v2("requires"));y=nodeTags(g,f,facts,n.requires(),x,y,w,mx,my);}
   var opens=ResearchTreeModel.unlocks(n.id());
   if(!opens.isEmpty()||!unlockRows.isEmpty()){y=OfficeUi.header(g,f,x,y,w,null,v2("opens"));if(!opens.isEmpty())y=nodeTags(g,f,facts,opens,x,y,w,mx,my);
    int tx=x;for(var u:unlockRows){var label=v2("building_level",Component.translatable("building.villageastra."+u.type()),OfficeUi.roman(u.level()));int tw=Math.min(OfficeUi.tagWidth(f,label),w);
     if(tx>x&&tx+tw>x+w){tx=x;y+=13;}OfficeUi.tag(g,f,tx,y,label,Tone.INFO,w);tx+=tw+3;}if(!unlockRows.isEmpty())y+=14;}
   y=OfficeUi.header(g,f,x,y,w,null,text("effects"));
   for(var e:effectRows)for(var part:f.getSplitter().splitLines("• "+e.getString(),w,Style.EMPTY)){OfficeUi.drawText(g,f,part.getString(),x,y,OfficeUi.MUTED);y+=10;}
   y+=3;}
  // 6. What to do next, as one line that leads there; in a tall column it stands at the foot of the card, not in the middle of the space.
  var todo=todo(t,facts,n,s);
  if(todo!=null&&!expanded&&detailScroll==0&&r.bottom()-21-y>24)y=r.bottom()-21;
  if(todo!=null){var rect=new Rect(x,y,w,18);boolean link=todo.run!=null;OfficeUi.chip(g,f,x,y,w,todo.icon,todo.text,Component.empty(),todo.tone,link,link&&rect.contains(mx,my));
   if(link&&rect.intersect(r).h()>0)links.add(new Link(rect.intersect(r),todo.run));y+=20;}
  detailHeight=y+detailScroll-top+3;detailScroll=Math.max(0,Math.min(detailScroll,Math.max(0,detailHeight-r.h())));
  OfficeUi.endBlock();OfficeUi.unclip(g);
  // Tooltips only where the details are on screen: parts scrolled away never answer a hover over the footer.
  OfficeUi.tips().limit(mark,r);
  if(detailHeight>r.h()){int bx=r.right()-3,th=Math.max(8,r.h()*r.h()/detailHeight),ty=r.y()+(r.h()-th)*detailScroll/Math.max(1,detailHeight-r.h());g.fill(bx,r.y(),bx+3,r.bottom(),OfficeUi.TRACK);g.fill(bx,ty,bx+3,ty+th,OfficeUi.MUTED);}
 }
 /** One short row: the tone's sign, what changes, and right-aligned «было → стало» with the unit. The whole sentence is the row's tooltip. */
 private int effectRow(GuiGraphics g,Font f,ResearchCard.Row row,int x,int y,int w){
  OfficeUi.sign(g,x,y+2,row.tone());int labelColour=row.tone()==Tone.OFF?OfficeUi.MUTED:OfficeUi.TEXT;
  String to=row.to()==null?"":row.to(),unit=row.unit()==null||to.isEmpty()?"":" "+row.unit().getString(),from=row.from();
  int vw=to.isEmpty()?0:f.width(to)+f.width(unit)+(from==null?0:f.width(from)+10);
  OfficeUi.label(g,f,row.label().getString(),x+8,y,Math.max(8,w-12-vw),labelColour,List.of(row.full()));
  int vx=x+w-vw;
  if(!to.isEmpty()){
   if(from!=null){vx=OfficeUi.drawText(g,f,from,vx,y,OfficeUi.MUTED)+1;OfficeUi.arrow(g,vx,y+1,2,OfficeUi.MUTED);vx+=9;}
   vx=OfficeUi.drawText(g,f,to,vx,y,row.tone()==Tone.OK?Tone.OK.fg():OfficeUi.TEXT);
   if(!unit.isEmpty())OfficeUi.drawText(g,f,unit,vx,y,OfficeUi.MUTED);}
  OfficeUi.tips().add(x,y-1,w,11,List.of(row.full()));
  return y+11;
 }
 /** The step and what the research needs as small chips on one line; the rest becomes '+N' with the whole list in its tooltip. */
 private int chipRow(GuiGraphics g,Font f,ResearchTreeModel.Facts facts,Node n,int x,int y,int w){
  int tx=x,plus=OfficeUi.tagWidth(f,Component.literal("+99"))+3;
  var step=v2("step",OfficeUi.roman(n.tier()));int sw=Math.min(OfficeUi.tagWidth(f,step),w);OfficeUi.tag(g,f,tx,y,step,Tone.INFO,sw);tx+=sw+3;
  var left=new ArrayList<String>();
  for(var need:n.requires()){var label=OfficeUi.research(need);int tw=Math.min(OfficeUi.tagWidth(f,label),Math.max(24,w/2));
   if(!left.isEmpty()||tx+tw>x+w-plus){left.add(need);continue;}
   var rect=new Rect(tx,y,tw,11);OfficeUi.tag(g,f,tx,y,label,tone(ResearchTreeModel.state(facts,ResearchCatalog.get(need))),tw);
   if(rect.intersect(details).h()>0)links.add(new Link(rect.intersect(details),()->focusNode(need)));
   tx+=tw+3;}
  if(!left.isEmpty()&&tx+plus<=x+w){var label=Component.literal("+"+left.size());int tw=OfficeUi.tagWidth(f,label);
   OfficeUi.tag(g,f,tx,y,label,Tone.OFF,tw);OfficeUi.tips().add(tx,y,tw,11,List.of(office("first",titles(left))));}
  return y+13;
 }
 private int nodeTags(GuiGraphics g,Font f,ResearchTreeModel.Facts facts,List<String> ids,int x,int y,int w,int mx,int my){
  int tx=x;for(var need:ids){var node=ResearchCatalog.get(need);var s=ResearchTreeModel.state(facts,node);var label=OfficeUi.research(need);int tw=Math.min(OfficeUi.tagWidth(f,label),w);
   if(tx>x&&tx+tw>x+w){tx=x;y+=13;}var rect=new Rect(tx,y,tw,11);OfficeUi.tag(g,f,tx,y,label,tone(s),w);
   if(rect.intersect(details).h()>0){var visible=rect.intersect(details);links.add(new Link(visible,()->focusNode(need)));}tx+=tw+3;}
  return y+14;
 }
 /** AD-136: the rows of a level-I price; how many were drawn. */
 private int resourceRows(GuiGraphics g,Font f,CompoundTag t,Node n,int x,int y,int w){
  var lines=t.getCompound("stock").getList(n.id(),Tag.TAG_COMPOUND);int i=0;
  for(var cost:n.resources()){int have=0;for(var raw:lines){var r=(CompoundTag)raw;if(r.getString("key").equals(cost.key()))have=r.getInt("have");}
   boolean ok=have>=cost.count();var name=costName(cost);String num=have+"/"+cost.count();int nw=f.width(num),ry=y+i*11;
   OfficeUi.icon12(g,costIcon(cost),x,ry-2);OfficeUi.label(g,f,name.getString(),x+14,ry,Math.max(8,w-14-nw-10),OfficeUi.TEXT);
   OfficeUi.sign(g,x+w-nw-8,ry+2,ok?Tone.OK:Tone.WAIT);OfficeUi.drawText(g,f,num,x+w-nw,ry,ok?Tone.OK.fg():Tone.WAIT.fg());
   OfficeUi.tips().add(x,ry-1,w,11,List.of(name.copy().append(": "+num)));i++;}
  resourceLines=i;return i;}
 /** The icon of a line of a level-I price: the item itself, or a typical member of the tag. */
 static ItemStack costIcon(ResearchCatalog.Cost c){
  if(c.tag()!=null)return new ItemStack(switch(c.tag()){case "minecraft:logs"->Items.OAK_LOG;case "minecraft:planks"->Items.OAK_PLANKS;case "minecraft:coals"->Items.COAL;case "minecraft:wooden_fences"->Items.OAK_FENCE;default->Items.BARREL;});
  return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(c.item())));}
 /** Its name: the item's own, or the tag's («Брёвна (любые)»). */
 static Component costName(ResearchCatalog.Cost c){return c.tag()!=null?v2("tag."+c.tag().substring(c.tag().indexOf(':')+1)):costIcon(c).getHoverName();}
 /** Probe hooks (AD-136): the price lines the card drew for a level-I node, and whether the grey «Будет: ...» row is on the card. */
 private int resourceLines;private boolean cardFuture;
 int resourceLines(){return resourceLines;} boolean cardFuture(){return cardFuture;}
 private record Todo(ItemStack icon,Component text,Tone tone,Runnable run){}
 private Todo todo(CompoundTag t,ResearchTreeModel.Facts facts,Node n,State s){
  return switch(s){
   case HALL->new Todo(new ItemStack(Items.BELL),v2("todo.hall",OfficeUi.roman(n.tier())),Tone.WAIT,()->open(ConstructionScreen.LEVELS));
   case LOCKED->{var first=ResearchTreeModel.firstMissing(facts,n);yield first==null?null:new Todo(new ItemStack(icon(ResearchCatalog.get(first).branch())),v2("todo.first",OfficeUi.research(first)),Tone.OFF,()->focusNode(first));}
   case AVAILABLE,PARTIAL->n.resourcePaid()?new Todo(new ItemStack(Items.CHEST),v2(facts.ready(n.id())?"todo.pay_ready":"todo.pay_order"),Tone.ACTION,null):new Todo(new ItemStack(Items.TARGET),v2("todo.available"),Tone.ACTION,null);
   case ORDERED->new Todo(new ItemStack(Items.CHEST),v2("todo.ordered"),Tone.WAIT,null);
   case QUEUED->new Todo(new ItemStack(Items.PAPER),v2("todo.queued",facts.queue().indexOf(n.id())+1),Tone.INFO,null);
   case TARGET->{var eta=OverviewPanel.eta(t,n,facts.paid(n.id()));yield new Todo(new ItemStack(Items.BREWING_STAND),eta!=null&&eta.tone()!=Tone.INFO&&eta.tone()!=Tone.OFF?eta.text():v2("todo.target"),eta==null?Tone.INFO:eta.tone()==Tone.OFF?Tone.INFO:eta.tone(),null);}
   case DONE->new Todo(new ItemStack(Items.EMERALD),v2("todo.done"),Tone.OK,null);
  };
 }
 private static void open(int section){var mc=net.minecraft.client.Minecraft.getInstance();mc.setScreen(new ConstructionScreen(section));}
}
