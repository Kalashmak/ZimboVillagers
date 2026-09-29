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
/** AD-058: the office tab of the buildings — every building in a list on the left (the hall first, a dot for its state, pips for its level),
 *  the chosen one's card on the right. Crop and map are icon buttons on the card's header; the upgrade is the one footer action. */
final class BuildingsPanel {
 static final int ROW_H=20,ICON=18;
 /** The building chosen from anywhere in the office (Overview, Levels); kept across tab switches and screens. */
 private static UUID chosen;private static boolean reveal;
 private final Layout layout;private final Scroll list=new Scroll(ROW_H);private final Scroll body=new Scroll(1);
 private final OfficeButton crop,map,upgrade,annex;private List<CompoundTag> rows=List.of();private Map<UUID,Component> titles=Map.of();private Rect cardArea;
 BuildingsPanel(Layout layout,Consumer<Button> add){
  // A new office starts on the first row, unless a link has just asked for a building.
  this.layout=layout;if(!reveal)chosen=null;
  crop=OfficeButton.of(BuildingCard.text("next_crop"),new ItemStack(Items.WHEAT_SEEDS),Tone.INFO,b->act(true));
  map=OfficeButton.of(office("buildings.on_map"),new ItemStack(Items.FILLED_MAP),Tone.INFO,b->{AtlasScreen.openTool(AtlasScreen.VIEW);Minecraft.getInstance().setScreen(new AtlasScreen());});
  upgrade=OfficeButton.of(BuildingCard.text("upgrade_to",""),new ItemStack(Items.EXPERIENCE_BOTTLE),Tone.ACTION,b->act(false));
  // AD-135: the annex of the building shown, while it is neither built nor queued; greyed with its reason until its site is free.
  annex=OfficeButton.of(Component.translatable("annex.villageastra.order"),new ItemStack(Items.CRAFTING_TABLE),Tone.ACTION,b->orderAnnex());
  crop.hint(crop.getMessage());map.hint(map.getMessage());
  add.accept(crop);add.accept(map);add.accept(upgrade);add.accept(annex);refresh();
 }
 /** The screen's old calls, kept until ConstructionScreen switches to the Layout ones (one line each there). */
 BuildingsPanel(int width,int height,Consumer<Button> add){this(new Layout(width,height),add);}
 void render(GuiGraphics g,Font f,int width){var m=Minecraft.getInstance();double s=m.getWindow().getGuiScale();render(g,f,layout,(int)(m.mouseHandler.xpos()/s),(int)(m.mouseHandler.ypos()/s));}
 private static Component office(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 /** Focuses a building (the Overview's and the Levels tab's links); the list scrolls to it on the next frame. */
 static void select(UUID id){chosen=id;reveal=true;}
 /** The building shown on the card: the chosen one, or the first row when nothing is chosen. */
 UUID selected(){var c=card();return c==null||!c.hasUUID("id")?null:c.getUUID("id");}
 Button upgradeButton(){return upgrade;}
 Button cropButton(){return crop;}
 Button mapButton(){return map;}
 Button annexButton(){return annex;}
 private void orderAnnex(){var t=ConstructionOverlay.snapshot();var card=card();if(card==null||!t.hasUUID("village"))return;var a=BuildingCard.annexToOrder(card);if(a!=null)BuildingCard.orderAnnex(t.getUUID("village"),t.getLong("epoch"),card,a);}
 /** The screen rectangle of a list row, or null when it is scrolled out of view (for the probe's click). */
 Rect rowRect(int index){int y=list.y(index);return y<0?null:new Rect(list.area().x(),y,list.rowW(),ROW_H-2);}
 /** The hall first, then the others grouped by type in the server's order; the names get an ordinal where a type repeats. */
 private void refresh(){
  var all=new ArrayList<CompoundTag>();for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND))all.add((CompoundTag)raw);
  var order=new HashMap<String,Integer>();for(var c:all)order.putIfAbsent(c.getString("type"),order.size());
  all.sort(Comparator.comparingInt((CompoundTag c)->c.getString("type").equals("town_hall")?-1:order.get(c.getString("type"))));
  rows=all;titles=BuildingCard.titles(all);
  var c=layout.content();int listW=listWidth(c,layout.size());list.bounds(new Rect(c.x(),c.y(),listW,c.h()),rows.size());cardArea=new Rect(c.x()+listW+8,c.y(),c.w()-listW-8,c.h());
  if(reveal){int i=index();if(i>=0)list.reveal(i);reveal=false;}
 }
 /** AD-124: the list takes less of a wider office: 40 % at XS, 42 % at S, 34 % at M, 28 % at L. */
 private static int listWidth(Rect c,OfficeGrid.Size size){return c.w()*switch(size){case XS->40;case S->42;case M->34;default->28;}/100;}
 /** Probe hook: the whole list is on screen and the card needs no scrolling. */
 boolean sparse(){return !list.overflows()&&!body.overflows();}
 private int index(){if(chosen!=null)for(int i=0;i<rows.size();i++)if(rows.get(i).hasUUID("id")&&rows.get(i).getUUID("id").equals(chosen))return i;return rows.isEmpty()?-1:0;}
 private CompoundTag card(){int i=index();return i<0?null:rows.get(i);}
 private Component title(CompoundTag c){return c.hasUUID("id")?titles.getOrDefault(c.getUUID("id"),BuildingCard.name(c)):BuildingCard.name(c);}
 private void act(boolean farm){
  var t=ConstructionOverlay.snapshot();var card=card();if(card==null||!t.hasUUID("village"))return;
  if(farm)BuildingCard.nextCrop(t.getUUID("village"),t.getLong("epoch"),t.getLong("revision"),card);else BuildingCard.upgrade(t.getUUID("village"),t.getLong("epoch"),card);
 }
 void tick(boolean visible){refresh();place(visible);}
 /** Places the buttons for the card shown now: crop and map at the right end of the card's header, the upgrade in the footer
  *  at its own width, or icon-only with its label in the tooltip when the footer is short; hidden only when not even that fits. */
 private void place(boolean visible){
  var t=ConstructionOverlay.snapshot();var card=card();boolean manage=t.getBoolean("canManage");
  int headerY=cardArea.y()-body.first();boolean header=visible&&card!=null&&headerY>=cardArea.y();int x=cardArea.right();
  map.visible=header&&t.getInt("atlasChunks")>0;if(map.visible){x-=ICON;map.at(new Rect(x,headerY,ICON,ICON));x-=2;}
  crop.visible=header&&BuildingCard.farm(card)&&!FarmFieldGrid.shown(card);if(crop.visible){x-=ICON;crop.at(new Rect(x,headerY,ICON,ICON));}
  crop.active=manage;crop.reason(ConstructionPanel.lockReason(t));
  // Hidden once the top level is reached, and for the hall, whose upgrade is ordered on the construction tab.
  var u=card==null?new CompoundTag():card.getCompound("upgrade");
  boolean show=visible&&card!=null&&BuildingCard.upgradable(card)&&!u.getString("refusal").equals("hall_office");
  var flow=layout.flow();
  upgrade.visible=show;
  if(show){upgrade.setMessage(BuildingCard.upgradeLabel(card));
   Component why=!manage?ConstructionPanel.lockReason(t):reason(u);upgrade.active=why==null;upgrade.reason(why);upgrade.hint(null);
   var r=flow.next(upgrade.prefWidth(Minecraft.getInstance().font));
   if(r==null){r=flow.next(ICON+2);upgrade.hint(upgrade.getMessage());}
   if(r==null)upgrade.visible=false;else upgrade.at(r);}
  // AD-135: the annex button after the upgrade, active only when its site is free and nothing else stops it.
  var a=visible&&card!=null?BuildingCard.annexToOrder(card):null;annex.visible=a!=null;
  if(a!=null){Component why=!manage?ConstructionPanel.lockReason(t):a.getString("state").equals("ready")?null:BuildingCard.annexState(a);
   annex.active=why==null;annex.reason(why);annex.hint(null);
   var r=flow.next(annex.prefWidth(Minecraft.getInstance().font));
   if(r==null){r=flow.next(ICON+2);annex.hint(annex.getMessage());}
   if(r==null)annex.visible=false;else annex.at(r);}
 }
 /** A refusal of the server that stops the building itself (siege, a busy crew, missing equipment): it outranks research and materials, as on the server. */
 private static boolean blocking(String why){return !why.isEmpty()&&!why.equals("research")&&!why.equals("materials")&&!why.equals("done");}
 /** Why the next level cannot be ordered now, or null when it can: a blocking refusal first, then research, then the hall's materials. */
 private static Component reason(CompoundTag u){
  String why=u.getString("refusal");if(blocking(why))return Component.translatable("upgrade.villageastra.refused."+why);
  var research=u.getList("research",Tag.TAG_STRING);if(!research.isEmpty())return office("reason.research",OfficeUi.research(research.getString(0)));
  if(u.getInt("lack")>0)return office("reason.materials",u.getInt("lack"));
  return why.equals("done")?office("reason.max"):why.isEmpty()?null:Component.translatable("upgrade.villageastra.refused."+why);
 }
 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  refresh();place(true);var t=ConstructionOverlay.snapshot();boolean manage=t.getBoolean("canManage");var la=list.area();
  if(rows.isEmpty()){OfficeUi.block("empty",l.content());OfficeUi.chip(g,f,la.x(),la.y(),l.content().w(),ItemStack.EMPTY,office("no_data"),Component.empty(),Tone.OFF);OfficeUi.endBlock();return;}
  // The card, scrolled by pixels when it is taller than the content; at L in two columns (people and work, then the level and the core).
  var card=card();BuildingCard.LINKS.clear();
  if(card!=null){int reserve=(map.visible?ICON+2:0)+(crop.visible?ICON+2:0);
   OfficeUi.clip(g,cardArea);int mark=OfficeUi.tips().mark();OfficeUi.block("card",cardArea);
   int cols=l.size()==OfficeGrid.Size.L&&cardArea.w()>=360?2:1;
   int bottom=BuildingCard.render(g,f,card,title(card),cardArea.x(),cardArea.y()-body.first(),cardArea.w()-4,mx,my,reserve,true,cols);
   OfficeUi.endBlock();OfficeUi.unclip(g);
   int height=bottom+body.first()-cardArea.y();body.bounds(cardArea,Math.max(cardArea.h(),height));
   if(body.overflows()){int x=cardArea.right()-3,th=Math.max(8,cardArea.h()*cardArea.h()/height),ty=cardArea.y()+(cardArea.h()-th)*body.first()/Math.max(1,height-cardArea.h());g.fill(x,cardArea.y(),x+3,cardArea.bottom(),OfficeUi.TRACK);g.fill(x,ty,x+3,ty+th,OfficeUi.MUTED);}
   // Tooltips of the card only where the card is on screen: parts scrolled out of view must not answer a hover above it (AD-124: trimmed, not dropped).
   OfficeUi.tips().limit(mark,cardArea);}
  // The list: icon, name, level pips (dropped at the smallest GUI: they stay in the tooltip) and the state dot.
  int sel=index();OfficeUi.clip(g,la);
  for(int i=0;i<rows.size();i++){int y=list.y(i);if(y<0)continue;var c=rows.get(i);int w=list.rowW();boolean hover=list.hit(mx,my)==i;
   OfficeUi.block("row "+i,new Rect(la.x(),y,w,ROW_H-2));
   OfficeUi.row(g,la.x(),y,w,ROW_H-2,i==sel,hover,BuildingCard.tone(c,manage));
   g.renderItem(OfficeUi.buildingIcon(c.getString("type")),la.x()+3,y+1);
   var u=c.getCompound("upgrade");boolean pips=c.contains("upgrade")&&!l.narrow();int pw=pips?u.getInt("max")*7-2:0;
   if(pips)OfficeUi.pips(g,la.x()+w-10-pw,y+6,u.getInt("level"),u.getInt("kept"),u.getInt("max"));
   var name=title(c);OfficeUi.label(g,f,name,la.x()+22,y+5,w-22-(pips?pw+14:10),i==sel?OfficeUi.TITLE:OfficeUi.TEXT);OfficeUi.endBlock();
   var lines=new ArrayList<Component>();lines.add(name);var way=BuildingCard.where(c);if(way!=null)lines.add(way);
   if(c.contains("upgrade"))lines.add(office("buildings.level",OfficeUi.roman(u.getInt("kept")),OfficeUi.roman(u.getInt("max"))));
   OfficeUi.tips().add(la.x(),y,w,ROW_H-2,lines);}
  OfficeUi.unclip(g);list.render(g);
  // A disabled upgrade names its reason beside the button, in the footer's free part.
  // AD-135: the annex button takes that part of the footer; the upgrade's reason stays in its tooltip then.
  if(card!=null&&upgrade.visible&&!annex.visible&&!upgrade.active&&upgrade.reason()!=null){int x=upgrade.getX()+upgrade.getWidth()+4,room=l.footerEnd()-x;
   if(room>24){var s=OfficeUi.fit(f,upgrade.reason().getString(),room-14);OfficeUi.tag(g,f,x,upgrade.getY()+3,Component.literal(s),reasonTone(card.getCompound("upgrade"),manage));OfficeUi.tips().add(new Rect(x,upgrade.getY(),room,18),upgrade.reason());}}
 }
 /** In the order of reason(): a danger or a stopped building red, a busy crew amber, a missing research or a viewer's lock grey, a shortage of materials amber. */
 private static Tone reasonTone(CompoundTag u,boolean manage){
  if(!manage)return ConstructionOverlay.snapshot().getString("lockReason").equals("unsafe")?Tone.BAD:Tone.OFF;
  String why=u.getString("refusal");if(blocking(why))return BuildingCard.refusalTone(why);
  if(!u.getList("research",Tag.TAG_STRING).isEmpty())return Tone.OFF;return u.getInt("lack")>0?Tone.WAIT:Tone.OFF;
 }
 /** Probe hook (AD-130): where the card is drawn. */
 Rect cardArea(){return cardArea;}
 /** AD-130: a right click on a field of the farm grid steps its crop back. */
 boolean clickRight(double mx,double my){return cardArea!=null&&cardArea.contains(mx,my)&&FarmFieldGrid.click(ConstructionOverlay.snapshot(),card(),mx,my,true);}
 boolean click(double mx,double my){
  if(cardArea!=null&&cardArea.contains(mx,my)&&FarmFieldGrid.click(ConstructionOverlay.snapshot(),card(),mx,my,false))return true;
  int i=list.hit(mx,my);if(i>=0&&i<rows.size()){if(rows.get(i).hasUUID("id"))chosen=rows.get(i).getUUID("id");body.bounds(cardArea,0);place(true);return true;}
  for(var link:List.copyOf(BuildingCard.LINKS))if(link.rect().contains(mx,my)&&cardArea.contains(mx,my)){link.run().run();return true;}
  return false;
 }
 /** The wheel scrolls the list under the mouse, or the card by 10 px. */
 boolean scroll(double mx,double my,double delta){
  if(list.wheel(mx,my,delta))return true;
  if(cardArea!=null&&cardArea.contains(mx,my)&&body.overflows()){for(int k=0;k<10;k++)body.wheel(mx,my,delta);place(true);return true;}
  return false;
 }
 /** Up and down walk the list. */
 boolean key(int key){
  if(rows.isEmpty()||key!=264&&key!=265)return false;int i=Math.max(0,Math.min(rows.size()-1,index()+(key==264?1:-1)));
  if(rows.get(i).hasUUID("id"))select(rows.get(i).getUUID("id"));body.bounds(cardArea,0);refresh();place(true);return true;
 }
 /** Probe hook: the card's header buttons stay inside the card and do not cover each other or the name. */
 List<String> layoutProblems(){
  var out=new ArrayList<String>();
  for(var b:List.of(crop,map))if(b.visible&&!cardArea.containsRect(Rect.of(b)))out.add("card button outside the card: "+b.getMessage().getString());
  if(upgrade.visible&&!layout.footer().containsRect(Rect.of(upgrade)))out.add("upgrade button outside the footer");
  if(annex.visible&&!layout.footer().containsRect(Rect.of(annex)))out.add("annex button outside the footer");
  if(annex.visible&&upgrade.visible&&Rect.of(annex).intersects(Rect.of(upgrade)))out.add("annex button covers the upgrade");
  return out;
 }
}
