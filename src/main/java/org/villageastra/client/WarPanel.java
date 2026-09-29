package org.villageastra.client;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
import org.villageastra.world.Annexation;
/** AD-049: the war desk of the office. One chip row (own force, and the campaign as five steps), an incoming buyout offer with its answer,
 *  and every neighbour in a scrolling list of 22 px rows, each with its own campaign and buyout buttons that name why they are closed.
 *  The rules live in the '?' tooltip; coordinates only in tooltips. */
final class WarPanel {
 static final int ROW_H=22,POOL=12;
 private static final List<String> STEPS=List.of("gathering","marching","encircling","ready","besieging");
 private final Layout layout;private final Scroll list=new Scroll(ROW_H);
 private final List<OfficeButton> musters=new ArrayList<>(),buys=new ArrayList<>();private final UUID[] slot=new UUID[POOL];
 private final OfficeButton siege,accept,decline;private boolean shown;private Rect force=new Rect(0,0,0,0),banner;
 WarPanel(Layout layout,Consumer<Button> add){
  this.layout=layout;
  for(int i=0;i<POOL;i++){final int s=i;
   var muster=OfficeButton.of(office("war.muster"),ItemStack.EMPTY,Tone.ACTION,b->order(war(ConstructionOverlay.snapshot()).getBoolean("capture")?5:0,slot[s]));
   var buy=OfficeButton.of(office("war.buy"),ItemStack.EMPTY,Tone.ACTION,b->order(2,slot[s]));
   musters.add(muster);buys.add(buy);add.accept(muster);add.accept(buy);}
  siege=OfficeButton.of(text("siege"),new ItemStack(Items.TNT),Tone.ACTION,b->order(1,own()));
  accept=OfficeButton.of(text("accept"),new ItemStack(Items.GOLD_INGOT),Tone.ACTION,b->order(3,own()));
  decline=OfficeButton.of(text("decline"),new ItemStack(Items.BARRIER),Tone.BAD,b->order(4,own())).confirm(office("confirm"));
  add.accept(siege);add.accept(accept);add.accept(decline);
 }
 /** The old screen's constructor, kept until ConstructionScreen passes its Layout. */
 WarPanel(int width,Consumer<Button> add){this(new Layout(width,Minecraft.getInstance().getWindow().getGuiScaledHeight()),add);}
 private static Component text(String key,Object... values){return Component.translatable("war.villageastra."+key,values);}
 private static Component office(String key,Object... values){return Component.translatable("office.villageastra."+key,values);}
 private static CompoundTag war(CompoundTag t){return t.getCompound("war");}
 private static ListTag rows(CompoundTag t){return war(t).getList("neighbours",Tag.TAG_COMPOUND);}
 private static UUID own(){var t=ConstructionOverlay.snapshot();return t.hasUUID("village")?t.getUUID("village"):null;}
 private static void order(int action,UUID target){
  var t=ConstructionOverlay.snapshot();if(!t.hasUUID("village")||target==null)return;
  ConstructionNetwork.sendWar(new ConstructionNetwork.WarOrder(t.getUUID("village"),t.getLong("epoch"),action,target));
 }
 /** Where our own hall stands: the town hall card, else the player (who is at the office). */
 private static BlockPos home(CompoundTag t){
  for(var raw:t.getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.getString("type").equals("town_hall")&&c.contains("pos"))return BlockPos.of(c.getLong("pos"));}
  var p=Minecraft.getInstance().player;return p==null?BlockPos.ZERO:p.blockPosition();
 }
 private static Component lock(CompoundTag t){return office(t.getString("lockReason").equals("unsafe")?"reason.unsafe":"reason.only_mayor");}
 /** The campaign under way, or "none": a withdrawn, surrendered or closed army stays in the server's records but no longer binds the barracks. */
 private static String campaign(CompoundTag war){String s=war.getString("state");return STEPS.contains(s)?s:"none";}
 private static boolean pending(CompoundTag v){var o=v.getString("offer");return o.equals("offered")||o.equals("accepted")||o.equals("paid");}

 Button musterButton(UUID village){return rowButton(musters,village);}
 Button buyButton(UUID village){return rowButton(buys,village);}
 Button acceptButton(){return accept;}
 Button declineButton(){return decline;}
 Button siegeButton(){return siege;}
 /** Scrolls the neighbour into view and gives its button (null when it is not listed). */
 private Button rowButton(List<OfficeButton> pool,UUID village){if(village==null)return null;reveal(village);for(int i=0;i<POOL;i++)if(village.equals(slot[i])&&pool.get(i).visible)return pool.get(i);return null;}
 void reveal(UUID village){var l=rows(ConstructionOverlay.snapshot());for(int i=0;i<l.size();i++)if(l.getCompound(i).hasUUID("village")&&l.getCompound(i).getUUID("village").equals(village)){arrange();list.reveal(i);break;}arrange();}

 void tick(boolean visible){shown=visible;arrange();}
 /** Places and arms every button for the current snapshot: called on tick, before drawing and after a wheel scroll, so a click never lands on a stale row. */
 private void arrange(){
  var t=ConstructionOverlay.snapshot();var war=war(t);var font=Minecraft.getInstance().font;var c=layout.content();
  boolean village=shown&&t.hasUUID("village"),mayor=war.getBoolean("mayor"),barracks=war.getBoolean("barracks");String state=campaign(war);int soldiers=war.getInt("soldiers");long coins=war.getLong("treasury");
  // AD-124: the force chips wrap onto a second line when the row is short, instead of dropping their words.
  int y=c.y();int fh=forceWidth(font,war,layout.narrow())>c.w()?40:18;force=new Rect(c.x(),y,c.w(),fh);y+=fh+4;
  // An incoming offer waits for the mayor's answer: its own row with Accept and Decline.
  boolean offered=village&&war.getBoolean("offered");banner=null;accept.visible=decline.visible=offered;
  accept.active=decline.active=mayor;accept.reason(lock(t));decline.reason(lock(t));
  if(offered){int aw=accept.prefWidth(font),dw=decline.prefWidth(font);boolean slim=c.w()-aw-dw-8<140;if(slim){aw=dw=20;accept.hint(accept.getMessage());decline.hint(decline.getMessage());}else{accept.hint(null);decline.hint(null);}
   decline.at(new Rect(c.right()-dw,y,dw,18));accept.at(new Rect(c.right()-dw-4-aw,y,aw,18));banner=new Rect(c.x(),y,c.w()-aw-dw-8,18);y+=22;}
  else OfficeUi.disarm(List.of(decline));
  y+=14;var l=rows(t);list.bounds(new Rect(c.x(),y,c.w(),Math.min(c.bottom()-y,POOL*ROW_H)),l.size());
  // Row buttons follow the visible rows; the rest hide.
  int bw=Math.max(musters.get(0).prefWidth(font),buys.get(0).prefWidth(font));
  for(int i=0;i<POOL;i++){var m=musters.get(i);var b=buys.get(i);int index=list.first()+i,ry=list.y(index);
   if(!village||ry<0){m.visible=b.visible=false;slot[i]=null;continue;}
   var v=l.getCompound(index);slot[i]=v.hasUUID("village")?v.getUUID("village"):null;m.visible=b.visible=slot[i]!=null;
   int bx=list.area().x()+list.rowW()-2-bw;b.at(new Rect(bx,ry+1,bw,18));m.at(new Rect(bx-4-bw,ry+1,bw,18));
   boolean besieged=v.getBoolean("besieged");
   // AD-159 VI: the campaign becomes a capture the army carries out on its own.
   m.setMessage(office(war.getBoolean("capture")?"war.capture":"war.muster"));
   m.active=mayor&&barracks&&soldiers>0&&state.equals("none")&&!besieged;
   m.reason(!mayor?lock(t):!barracks?office("war.no_barracks"):soldiers<=0?office("war.no_soldiers"):!state.equals("none")?office("war.busy"):text("refused.war"));
   long price=v.getLong("price"),supplied=v.getLong("supplied");
   b.active=mayor&&!pending(v)&&!besieged&&supplied>=Annexation.SUPPLY_VALUE&&coins>=price;
   b.reason(!mayor?lock(t):pending(v)?(v.getBoolean("mine")?office("war.your_offer"):text("refused.offered")):besieged?text("refused.war"):supplied<Annexation.SUPPLY_VALUE?text("refused.supplies"):text("refused.coins"));}
  // The siege is ordered from the footer while a campaign is on its way or ready.
  siege.visible=village&&!state.equals("none")&&!state.equals("besieging");String ready=war.getString("ready");
  siege.active=mayor&&ready.isEmpty();siege.reason(!mayor?lock(t):text("refused."+(ready.isEmpty()?"not_ready":ready)));
  if(siege.visible){var flow=layout.flow();var r=flow.next(siege.prefWidth(font));siege.hint(null);if(r==null){r=flow.next(20);siege.hint(siege.getMessage());}siege.at(r);}
 }

 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  arrange();var t=ConstructionOverlay.snapshot();var war=war(t);var c=l.content();
  if(!t.hasUUID("village")){var s=OfficeUi.fit(f,Component.translatable("election.villageastra.absent").getString(),c.w());OfficeUi.drawText(g,f,s,c.x()+(c.w()-f.width(s))/2,c.y()+30,OfficeUi.MUTED);return;}
  var home=home(t);OfficeUi.block("forces",force);forces(g,f,war,home,l.narrow());OfficeUi.endBlock();
  if(banner!=null){long price=war.getLong("offerPrice");var offer=office("war.offer",price);OfficeUi.block("offer",banner);OfficeUi.chip(g,f,banner.x(),banner.y(),banner.w(),new ItemStack(Items.GOLD_INGOT),offer,Component.empty(),Tone.ACTION);
   var tip=new ArrayList<Component>();tip.add(text("incoming",price));if(war.contains("buyerCenter")){var at=BlockPos.of(war.getLong("buyerCenter"));tip.add(office("war.where",OfficeUi.direction(home,at),dist(home,at)));}OfficeUi.tips().add(banner.x(),banner.y(),banner.w(),18,tip);OfficeUi.endBlock();}
  var area=list.area();OfficeUi.header(g,f,area.x(),area.y()-14,area.w(),new ItemStack(Items.OAK_DOOR),office("war.neighbours"));
  var rows=rows(t);
  if(rows.isEmpty()){OfficeUi.drawText(g,f,OfficeUi.fit(f,text("alone").getString(),area.w()),area.x(),area.y()+4,OfficeUi.MUTED);return;}
  OfficeUi.clip(g,area);
  for(int i=0;i<POOL;i++){int index=list.first()+i,ry=list.y(index);if(ry<0)continue;OfficeUi.block("neighbour "+index,new Rect(area.x(),ry,list.rowW(),ROW_H-2));neighbour(g,f,rows.getCompound(index),war.getLong("treasury"),home,area.x(),ry,list.rowW(),musters.get(i).getX()-4,l.narrow(),mx,my);OfficeUi.endBlock();}
  OfficeUi.unclip(g);list.render(g);
 }
 private static int dist(BlockPos a,BlockPos b){return (int)Math.round(Math.sqrt(Math.pow(a.getX()-b.getX(),2)+Math.pow(a.getZ()-b.getZ(),2)));}
 /** The chip row: soldiers, barracks, treasury, and the campaign as five steps with its supplies in the tooltip. Grey means war is not possible yet. */
 /** The chips of the force row laid end to end: wider than the row means a second line. */
 private static int forceWidth(Font f,CompoundTag war,boolean narrow){
  int soldiers=war.getInt("soldiers");var n=Component.literal(String.valueOf(soldiers));var coins=Component.literal(String.valueOf(war.getLong("treasury")));
  int w=OfficeUi.chipWidth(f,new ItemStack(Items.IRON_SWORD),narrow?n:office("war.soldiers"),narrow?Component.empty():n,false)+4;
  w+=OfficeUi.chipWidth(f,new ItemStack(Items.SHIELD),narrow?Component.empty():office(war.getBoolean("barracks")?"war.barracks":"war.no_barracks"),Component.empty(),false)+4;
  w+=OfficeUi.chipWidth(f,new ItemStack(Items.GOLD_INGOT),narrow?coins:office("overview.treasury"),narrow?Component.empty():coins,false);
  String state=campaign(war);if(!state.equals("none"))w+=4+27+f.width(text("state."+state))+4+36;return w;
 }
 private void forces(GuiGraphics g,Font f,CompoundTag war,BlockPos home,boolean narrow){
  int x=force.x(),y=force.y(),soldiers=war.getInt("soldiers");long coins=war.getLong("treasury");boolean barracks=war.getBoolean("barracks");
  var soldiersText=office("war.soldiers");Component n=Component.literal(String.valueOf(soldiers));
  int w=OfficeUi.chip(g,f,x,y,0,new ItemStack(Items.IRON_SWORD),narrow?n:soldiersText,narrow?Component.empty():n,soldiers>0?Tone.OK:Tone.OFF);
  OfficeUi.tips().add(new Rect(x,y,w,18),soldiersText.copy().append(": "+soldiers));x+=w+4;
  var barracksText=office(barracks?"war.barracks":"war.no_barracks");
  int bw=OfficeUi.chipWidth(f,new ItemStack(Items.SHIELD),narrow?Component.empty():barracksText,Component.empty(),false);if(x+bw>force.right()&&force.h()>18){x=force.x();y+=22;}
  w=OfficeUi.chip(g,f,x,y,0,new ItemStack(Items.SHIELD),narrow?Component.empty():barracksText,Component.empty(),barracks?Tone.OK:Tone.OFF);OfficeUi.tips().add(new Rect(x,y,w,18),barracksText);x+=w+4;
  var treasury=office("overview.treasury");Component coinsText=Component.literal(String.valueOf(coins));
  int tw0=OfficeUi.chipWidth(f,new ItemStack(Items.GOLD_INGOT),narrow?coinsText:treasury,narrow?Component.empty():coinsText,false);if(x+tw0>force.right()&&force.h()>18&&y==force.y()){x=force.x();y+=22;}
  w=OfficeUi.chip(g,f,x,y,0,new ItemStack(Items.GOLD_INGOT),narrow?coinsText:treasury,narrow?Component.empty():coinsText,Tone.INFO);OfficeUi.tips().add(new Rect(x,y,w,18),treasury.copy().append(": "+coins));x+=w+4;
  String state=campaign(war);if(state.equals("none"))return;
  if(force.h()>18&&y==force.y()&&x+27+f.width(text("state."+state))+40>force.right()){x=force.x();y+=22;}
  int step=STEPS.indexOf(state);String ready=war.getString("ready");var name=text("state."+state);
  // Besieging is done (green); a siege the mayor may begin now is a decision (blue); ready but refused is blocked (red); on the way is waiting (amber).
  Tone tone=state.equals("besieging")?Tone.OK:ready.isEmpty()&&war.getBoolean("mayor")?Tone.ACTION:state.equals("ready")?Tone.BAD:Tone.WAIT;
  int room=force.right()-x,cw=Math.min(room,27+f.width(name)+4+36);if(cw<27+4+36)return;
  // The name is fitted short of the five dots, so the dots never cut a letter.
  OfficeUi.chip(g,f,x,y,cw,new ItemStack(Items.TNT),Component.literal(OfficeUi.fit(f,name.getString(),cw-27-4-38)),Component.empty(),tone);
  // Five steps: done green, the current one in the campaign's tone, the rest outlined grey.
  int dx=x+cw-4-33;g.fill(dx-2,y+1,x+cw-1,y+17,tone.bg());
  for(int i=0;i<STEPS.size();i++){int px=dx+i*7;if(step>=0&&i<step)g.fill(px,y+7,px+5,y+12,Tone.OK.fg());else if(i==step)g.fill(px,y+7,px+5,y+12,tone.fg());else g.renderOutline(px,y+7,5,5,Tone.OFF.fg());}
  var tip=new ArrayList<Component>();tip.add(office("war.muster").copy().append(": ").append(name));
  if(war.contains("campaignCenter")){var at=BlockPos.of(war.getLong("campaignCenter"));tip.add(office("war.where",OfficeUi.direction(home,at),dist(home,at)));}
  tip.add(office("war.supplies"));
  tip.add(Items.OAK_FENCE.getDescription().copy().append(": "+war.getInt("fences")));tip.add(Items.CAMPFIRE.getDescription().copy().append(": "+war.getInt("campfires")));
  tip.add(Items.TNT.getDescription().copy().append(": "+war.getInt("charges")));tip.add(Items.BREAD.getDescription().copy().append(": "+war.getInt("rations")));
  if(!ready.isEmpty()&&!state.equals("besieging"))tip.add(text("not_ready",text("refused."+ready)).copy().withStyle(s->s.withColor(Tone.BAD.fg()&0xFFFFFF)));
  OfficeUi.tips().add(x,y,cw,18,tip);
 }
 /** One neighbour: bearing and distance, residents, how we stand, and the buyout (our coins against its price); the rest in the tooltip. */
 private void neighbour(GuiGraphics g,Font f,CompoundTag v,long coins,BlockPos home,int x,int y,int w,int end,boolean narrow,int mx,int my){
  var at=BlockPos.of(v.getLong("center"));boolean besieged=v.getBoolean("besieged");int distance=v.getInt("distance");
  OfficeUi.row(g,x,y,w,ROW_H-2,false,new Rect(x,y,end-x,ROW_H-2).contains(mx,my),null);
  int cx=x+4;OfficeUi.arrow(g,cx,y+6,OfficeUi.bearingIndex(home,at),OfficeUi.TEXT);cx+=10;
  // Owner 2026-09-24: the neighbour by its name, where the row has room.
  var named=VillageNameText.shown(v.getString("name"));if(!named.isEmpty()&&cx+f.width(named)+60<=end)cx=OfficeUi.drawText(g,f,named,cx,y+6,OfficeUi.TEXT)+6;
  cx=OfficeUi.drawText(g,f,office("war.distance",distance),cx,y+6,OfficeUi.TEXT)+6;
  String people=String.valueOf(v.getInt("residents"));if(cx+17+f.width(people)<=end){g.renderItem(new ItemStack(Items.PLAYER_HEAD),cx,y+2);cx=OfficeUi.drawText(g,f,people,cx+17,y+6,OfficeUi.TEXT)+6;}
  // One tag: a siege first, then our own offer, then how the two villages stand.
  Component rel;Tone relTone;
  if(besieged){rel=text("state.besieging");relTone=Tone.BAD;}
  else if(v.getBoolean("mine")&&pending(v)){rel=office("war.your_offer");relTone=Tone.INFO;}
  else{String s=v.contains("standing")?v.getString("standing"):"neutral";rel=text("standing."+s);relTone=switch(s){case "hostile"->Tone.BAD;case "cold"->Tone.WAIT;case "warm","friendly"->Tone.OK;default->Tone.INFO;};}
  int tw=OfficeUi.tagWidth(f,rel);if(cx+tw<=end){OfficeUi.tag(g,f,cx,y+5,rel,relTone);cx+=tw+6;}
  // The buyout: our coins against the price; full and supplied is green, anything short amber.
  long price=v.getLong("price"),supplied=v.getLong("supplied");
  var label=Component.literal(Math.min(coins,price)+"/"+price);int bw=Math.max(56,f.width(label)+6);
  if(!narrow&&cx+bw<=end)OfficeUi.bar(g,f,cx,y+5,bw,Math.min(coins,price),Math.max(1,price),coins>=price&&supplied>=Annexation.SUPPLY_VALUE?Tone.OK:Tone.WAIT,label);
  var tip=new ArrayList<Component>();tip.add(office("war.where",OfficeUi.direction(home,at),distance));tip.add(office("war.coords",at.getX(),at.getZ()));
  if(v.contains("relation"))tip.add(text("relation",text("standing."+v.getString("standing")),v.getInt("relation")));
  tip.add(office("war.buyout",Math.min(coins,price),price));tip.add(text("neighbour_body",v.getInt("residents"),v.getInt("buildings"),price,supplied+" / "+Annexation.SUPPLY_VALUE));
  OfficeUi.tips().add(x,y,Math.max(0,end-x),ROW_H-2,tip);
 }
 boolean click(double mx,double my){return false;}
 boolean scroll(double mx,double my,double delta){boolean moved=list.wheel(mx,my,delta);arrange();return moved;}
 /** The old screen's call, kept until ConstructionScreen passes its Layout. */
 void render(GuiGraphics g,Font f,int width,int height){render(g,f,new Layout(width,height),-1,-1);}
 /** Probe hook: every row button of a visible row lies in the list inside content(), hidden rows have none. */
 List<String> layoutProblems(){
  var out=new ArrayList<String>();var c=layout.content();var area=list.area();
  for(int i=0;i<POOL;i++)for(var b:List.of(musters.get(i),buys.get(i))){if(!b.visible)continue;var r=Rect.of(b);
   if(!c.containsRect(r)||!area.containsRect(r))out.add("war row button outside the list: '"+b.getMessage().getString()+"' ("+r.x()+","+r.y()+")");
   if(list.y(list.first()+i)<0)out.add("war button of a hidden row is visible: "+i);}
  return out;
 }
}
