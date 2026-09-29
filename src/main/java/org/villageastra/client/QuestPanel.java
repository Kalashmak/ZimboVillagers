package org.villageastra.client;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
/** AD-040: the settlement notice board on the office kit. The reputation toward the office in the header; every quest a scrolling row:
 *  its title, a progress bar, reward and time left, and one status line (why it cannot be taken, where it leads, or its people as
 *  coloured tags). Everything longer lives in the row's tooltip, the rules behind '?'. Take and Deliver sit in the row; Abandon asks twice. */
final class QuestPanel {
 static final int ROW_H=67,POOL=6;
 private final Layout layout;private final Scroll list=new Scroll(ROW_H);
 private final List<OfficeButton> actions=new ArrayList<>(),deliveries=new ArrayList<>();private final UUID[] slot=new UUID[POOL];
 private Rect header=new Rect(0,0,0,0),reputation,guests;
 QuestPanel(Layout layout,Consumer<Button> add){
  this.layout=layout;
  for(int i=0;i<POOL;i++){final int s=i;
   var act=OfficeButton.of(text("take"),ItemStack.EMPTY,Tone.ACTION,b->order(slot[s],-1));
   var hand=OfficeButton.of(text("deliver"),new ItemStack(Items.CHEST),Tone.ACTION,b->order(slot[s],2));
   actions.add(act);deliveries.add(hand);add.accept(hand);add.accept(act);}
 }
 private static ListTag rows(){return ConstructionOverlay.snapshot().getList("quests",Tag.TAG_COMPOUND);}
 private static CompoundTag quest(UUID id){if(id==null)return null;for(var raw:rows()){var q=(CompoundTag)raw;if(q.hasUUID("id")&&q.getUUID("id").equals(id))return q;}return null;}
 /** Take (0), abandon (1) — whichever the quest's owner is — or hand the cargo over (2). */
 private static void order(UUID id,int action){
  var q=quest(id);var t=ConstructionOverlay.snapshot();if(q==null||!t.hasUUID("village"))return;
  ConstructionNetwork.sendQuest(new ConstructionNetwork.QuestOrder(t.getUUID("village"),id,action>=0?action:q.getBoolean("mine")?1:0));
 }
 private static Component text(String key,Object... values){return Component.translatable("quest.villageastra."+key,values);}
 static Component title(CompoundTag q){
  var template=Component.translatable("quest.villageastra.template."+q.getString("template"));
  var item=q.getString("item").isEmpty()?null:new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(q.getString("item")))).getHoverName();
  // AD-098: a request carries the name of the resident who asks; a rescue, the name of the relative waiting at home.
  if(q.getString("template").equals("plea"))return text("plea_title",q.getString("giver"),item,q.getInt("target"));
  var goal=item==null?text("goal_count",template,q.getInt("target")):text("goal_item",template,q.getInt("target"),item);
  // AD-099: a stage of a chain says which step of which trail it is.
  if(q.getInt("stages")>0)return text("chain_stage",goal,q.getInt("stage"),q.getInt("stages"));
  return q.getString("giver").isEmpty()?goal:text("title_kin",goal,q.getString("giver"));
 }
 /** AD-080: the place a quest leads to, which way it lies and the last word from it — the cross itself is on the chart. */
 private static Component place(CompoundTag q){
  var kind=q.getString("kind");
  var status=q.getString("status").isEmpty()?Component.empty():Component.translatable("quest.villageastra.status."+q.getString("status"),q.getInt("status_left"));
  return text("place",Component.translatable("quest.villageastra.place."+(kind.isEmpty()?"camp":kind)),
   Component.translatable("quest.villageastra.side."+q.getString("dir")),q.getInt("away"),status);
 }
 /** AD-106: one of the player's people out there — what it does, why it waits, where, how hurt, what danger and which boats are near. */
 private static Component companion(CompoundTag c){
  var line=Component.empty();
  if(!c.getBoolean("live"))line.append(text("companion_unseen")).append(" ");
  line.append(name(c)).append(": ");
  if(!c.getString("state").isEmpty())line.append(Component.translatable("quest.villageastra.escort.state."+c.getString("state")));
  if(!c.getString("reason").isEmpty())line.append(" ").append(Component.translatable("quest.villageastra.escort.reason."+c.getString("reason")));
  if(c.getString("state").equals("arrived"))return line;
  if(!c.getString("dim").isEmpty())line.append(" · ").append(text("companion_in",Component.translatable("quest.villageastra.escort.dim."+dim(c.getString("dim")))));
  else if(c.contains("away"))line.append(" · ").append(text("companion_where",c.getInt("away"),Component.translatable("quest.villageastra.side."+c.getString("dir"))));
  if(c.getFloat("max")>0)line.append(" · ").append(text("companion_hp",Math.round(c.getFloat("hp")),Math.round(c.getFloat("max"))));
  if(c.getInt("danger")>0)line.append(" · ").append(text("companion_danger",c.getInt("danger")));
  var boat=c.getString("boat");
  if(boat.equals("riding")||boat.equals("near")||c.getString("reason").equals("needs_boat"))line.append(" · ").append(text("companion_boat."+(boat.isEmpty()?"none":boat)));
  return line;
 }
 private static Component name(CompoundTag c){return c.getString("name").isEmpty()?text("companion_someone"):Component.literal(c.getString("name"));}
 private static String dim(String d){return d.equals("overworld")||d.equals("the_nether")||d.equals("the_end")?d:"other";}
 /** Wounded or in danger red, waiting amber, home or walking along green, not met yet grey. */
 private static Tone tone(CompoundTag c){
  var state=c.getString("state");
  if(state.equals("wounded")||c.getFloat("max")>0&&c.getFloat("hp")<=c.getFloat("max")/2||c.getInt("danger")>0)return Tone.BAD;
  if(state.equals("waiting"))return Tone.WAIT;
  if(!c.getBoolean("live")||state.isEmpty()||state.equals("unseen"))return Tone.OFF;
  return state.equals("boat")||state.equals("dimension")?Tone.INFO:Tone.OK;
 }
 private static String hint(String item){var path=item.substring(item.indexOf(':')+1);return path.endsWith("_sapling")||path.endsWith("_propagule")?"sapling":path;}
 /** Mine green, open to take blue, closed to this player grey. */
 private static Tone tone(CompoundTag q,boolean takeable){return q.getBoolean("mine")?Tone.OK:takeable?Tone.ACTION:Tone.OFF;}
 private static boolean takeable(CompoundTag q){return !q.getBoolean("taken")&&!q.getBoolean("locked")&&!q.getBoolean("reserved");}
 /** Why this player cannot take it, or null. AD-097/AD-099: too little trust, or a trail another player found. */
 private static Component refusal(CompoundTag q){
  if(q.getBoolean("mine"))return null;
  if(q.getBoolean("taken"))return text("busy");
  if(q.getBoolean("reserved"))return text("reserved");
  if(q.getBoolean("locked"))return text("trust_needed",q.getInt("trust"),q.getLong("score"));
  return null;
 }
 /** The status line: why it cannot be taken, else where it leads, else how a request or a crop is done. */
 private static Component status(CompoundTag q){
  var no=refusal(q);if(no!=null)return no;
  if(q.getString("template").equals("plea"))return text("plea_where",q.getString("giver"));
  if(!q.getString("dir").isEmpty())return place(q);
  // AD-088: a crop without a chart says where such a thing is found in the world.
  if(q.getString("template").equals("crop"))return text("crop_hint."+hint(q.getString("item")));
  return Component.empty();
 }

 /** Scrolls the quest into view and gives its Take/Abandon button (null when it is not on the board). */
 Button takeButton(UUID id){var l=rows();for(int i=0;i<l.size();i++)if(l.getCompound(i).hasUUID("id")&&l.getCompound(i).getUUID("id").equals(id)){arrange();list.reveal(i);arrange();for(int s=0;s<POOL;s++)if(id.equals(slot[s])&&actions.get(s).visible)return actions.get(s);}return null;}
 private boolean shown;
 void tick(boolean visible){shown=visible;arrange();}
 /** Places and arms every button for the current snapshot: on tick, before drawing and after a wheel scroll, so a click never lands on a stale row. */
 private void arrange(){
  var font=Minecraft.getInstance().font;var c=layout.content();var l=rows();
  var election=ConstructionOverlay.snapshot().getCompound("election");
  // The bar is as wide as its label, so the minimum is never cut; the header takes the rest.
  int rw=election.contains("minimum")?Math.min(c.w()/2,Math.max(96,font.width(text("rep_bar",election.getLong("score"),election.getLong("minimum")))+8)):0;
  reputation=rw>0?new Rect(c.right()-rw,c.y()+1,rw,11):null;header=new Rect(c.x(),c.y(),c.w()-(rw>0?rw+6:0),14);
  // QUEST-005: the guests waiting for a bed take a strip at the foot of the tab, the board the room above it.
  var waiting=ConstructionOverlay.snapshot().getCompound("guests").getList("list",Tag.TAG_COMPOUND);
  int strip=waiting.isEmpty()?0:14+Math.min(waiting.size(),3)*12;
  guests=waiting.isEmpty()?null:new Rect(c.x(),c.bottom()-strip,c.w(),strip);
  // Never more rows than there are button slots (the board holds at most max_open quests, fewer than POOL).
  list.bounds(new Rect(c.x(),c.y()+16,c.w(),Math.min(c.bottom()-c.y()-16-strip,POOL*ROW_H)),l.size());
  int aw=10+Math.max(font.width(text("take")),Math.max(font.width(text("abandon")),font.width(text("abandon_confirm"))));
  int dw=deliveries.get(0).prefWidth(font);boolean slim=layout.narrow();if(slim)dw=20;
  for(int i=0;i<POOL;i++){var act=actions.get(i);var hand=deliveries.get(i);int index=list.first()+i,ry=list.y(index);
   if(!shown||ry<0){act.visible=hand.visible=false;slot[i]=null;OfficeUi.disarm(List.of(act));continue;}
   var q=l.getCompound(index);UUID id=q.hasUUID("id")?q.getUUID("id"):null;if(!Objects.equals(id,slot[i]))OfficeUi.disarm(List.of(act));slot[i]=id;
   boolean mine=q.getBoolean("mine");act.visible=id!=null;
   act.setMessage(text(mine?"abandon":"take"));act.tone(mine?Tone.BAD:Tone.ACTION);act.confirm(mine?text("abandon_confirm"):null);
   act.active=mine||takeable(q);act.reason(refusal(q));
   int bx=list.area().x()+list.rowW()-2-aw;act.at(new Rect(bx,ry+2,aw,18));
   hand.visible=id!=null&&mine&&q.getBoolean("handover");hand.hint(slim?hand.getMessage():null);hand.at(new Rect(bx-4-dw,ry+2,dw,18));}
 }
 /** The left edge of the buttons of a row slot: text stops short of it. */
 private int end(int i,int x,int w){var hand=deliveries.get(i);var act=actions.get(i);return (hand.visible?hand.getX():act.visible?act.getX():x+w)-4;}

 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  arrange();var rows=rows();var election=ConstructionOverlay.snapshot().getCompound("election");
  OfficeUi.header(g,f,header.x(),header.y(),header.w(),OfficeUi.tabIcon(ConstructionScreen.QUESTS),text("board"));
  // AD-075: the reputation earned out there is the road to the office, so the board keeps the count in sight.
  if(reputation!=null){long score=election.getLong("score"),minimum=election.getLong("minimum");boolean ready=score>=minimum;
   OfficeUi.bar(g,f,reputation.x(),reputation.y(),reputation.w(),Math.min(score,minimum),Math.max(1,minimum),ready?Tone.ACTION:Tone.INFO,text("rep_bar",score,minimum));
   OfficeUi.tips().add(reputation,text(ready?"office_ready":"office_left",score,minimum,Math.max(0,minimum-score)));}
  var area=list.area();
  if(rows.isEmpty()){OfficeUi.drawText(g,f,OfficeUi.fit(f,text("empty").getString(),area.w()),area.x()+4,area.y()+6,OfficeUi.MUTED);return;}
  OfficeUi.clip(g,area);
  for(int i=0;i<POOL;i++){int index=list.first()+i,ry=list.y(index);if(ry<0)continue;row(g,f,rows.getCompound(index),area.x(),ry,list.rowW(),end(i,area.x(),list.rowW()),mx,my);}
  OfficeUi.unclip(g);list.render(g);
  guests(g,f);
 }
 /** QUEST-005: who has been brought home and is still waiting for a bed, and how long of their hour is left. The mayor gives them a house
  *  (ordered on the map like any other) before it runs out; after it they leave alive and the delivery stays done. */
 private void guests(GuiGraphics g,Font f){
  if(guests==null)return;var block=ConstructionOverlay.snapshot().getCompound("guests");var list=block.getList("list",Tag.TAG_COMPOUND);
  boolean bed=block.getBoolean("bed");
  OfficeUi.header(g,f,guests.x(),guests.y(),guests.w(),new ItemStack(Items.RED_BED),text("guests",list.size()));
  int y=guests.y()+14;
  for(int i=0;i<list.size()&&i<3;i++){var guest=list.getCompound(i);long left=guest.getLong("left");
   var name=guest.getString("name").isEmpty()?text("companion_someone"):Component.literal(guest.getString("name"));
   // The guest's hour is an hour of play, not three days of game time: it is counted in minutes.
   long minutes=(left+1199)/1200;
   var line=text("guest_waits",name,minutes);
   if(guest.getBoolean("here")&&guest.contains("away"))line=text("guest_waits_at",name,minutes,Component.translatable("quest.villageastra.side."+guest.getString("dir")),guest.getInt("away"));
   // A bed standing free is a guest about to move in; without one the hour is a warning, and its last minutes a red one.
   var tone=bed?Tone.OK:left<=2400?Tone.BAD:Tone.WAIT;
   OfficeUi.sign(g,guests.x()+1,y+2,tone);
   OfficeUi.drawText(g,f,OfficeUi.fit(f,line.getString(),guests.w()-10),guests.x()+9,y,tone.fg());y+=12;}
  OfficeUi.tips().add(guests,text(bed?"guests_bed":"guests_no_bed"));
 }
 private void row(GuiGraphics g,Font f,CompoundTag q,int x,int y,int w,int end,int mx,int my){
  boolean takeable=takeable(q);Tone tone=tone(q,takeable);int h=ROW_H-1;
  OfficeUi.row(g,x,y,w,h,false,new Rect(x,y,end-x,h).contains(mx,my),null);g.fill(x,y,x+2,y+h,tone.fg());
  int tx=x+6,room=end-tx;
  OfficeUi.drawText(g,f,OfficeUi.fit(f,title(q).getString(),room),tx,y+2,q.getBoolean("mine")?Tone.OK.fg():OfficeUi.TEXT);
  // Progress as a bar, then the reward and the time left.
  int progress=q.getInt("progress"),target=Math.max(1,q.getInt("target"));var count=Component.literal(progress+"/"+target);
  int bw=Math.min(Math.max(48,f.width(count)+8),Math.max(0,room));
  if(bw>=24)OfficeUi.bar(g,f,tx,y+12,bw,progress,target,progress>=target?Tone.OK:q.getBoolean("mine")?Tone.WAIT:Tone.INFO,count);
  long left=q.getLong("left");var reward=text("reward",q.getLong("coins"),q.getLong("reputation")).getString();var time=text("left",OfficeUi.duration(left)).getString();
  // The time left before the reward: a deadline is what the player must act on, the reward only fits into what room is left.
  int rx=tx+bw+6;if(rx+f.width(time)<=end)rx=OfficeUi.drawText(g,f,time,rx,y+14,left<2400?Tone.WAIT.fg():OfficeUi.MUTED)+6;
  if(end-rx>12)OfficeUi.drawText(g,f,OfficeUi.fit(f,reward,end-rx),rx,y+14,OfficeUi.TEXT);
  // The status line: the people out there as tags, then the rest of what there is to say in the room left.
  var tip=new ArrayList<Component>();tip.add(title(q));tip.add(text("progress",progress,q.getInt("target"),q.getLong("coins"),q.getLong("reputation"),(left+19)/20));
  var people=q.getList("people",Tag.TAG_COMPOUND);int sx=tx,sy=y+24;
  for(int j=0;j<people.size();j++){var c=people.getCompound(j);tip.add(companion(c));
   var label=Component.literal(OfficeUi.fit(f,name(c).getString()+": "+Component.translatable("quest.villageastra.escort.state."+(c.getString("state").isEmpty()?"none":c.getString("state"))).getString(),Math.max(0,end-sx-12)));
   // A tag goes in only with room left for the '+N' of those after it; otherwise '+N' stands for this one and the rest.
   int tw=OfficeUi.tagWidth(f,label),rest=people.size()-j-1,need=tw+(rest>0?4+OfficeUi.tagWidth(f,Component.literal("+"+rest)):0);
   if(label.getString().isEmpty()||sx+need>end){var more=Component.literal("+"+(rest+1));
    if(sx+OfficeUi.tagWidth(f,more)<=end)sx+=OfficeUi.tag(g,f,sx,sy-1,more,Tone.INFO)+4;
    for(int k=j+1;k<people.size();k++)tip.add(companion(people.getCompound(k)));break;}
   OfficeUi.tag(g,f,sx,sy-1,label,tone(c));sx+=tw+4;}
  var status=status(q);Tone statusTone=refusal(q)!=null?Tone.WAIT:!q.getString("dir").isEmpty()?Tone.OK:Tone.INFO;
  if(!status.getString().isEmpty()){tip.add(status);if(end-sx>24)OfficeUi.drawText(g,f,OfficeUi.fit(f,status.getString(),end-sx),sx,sy,statusTone==Tone.INFO?OfficeUi.MUTED:statusTone.fg());}
  var instructions=QuestInstructions.describe(q);tip.add(instructions);
  var wrapped=f.split(instructions,Math.max(20,w-12));for(int line=0;line<Math.min(3,wrapped.size());line++)g.drawString(f,wrapped.get(line),tx,y+36+line*9,OfficeUi.TEXT);
  OfficeUi.tips().add(x,y,Math.max(0,end-x),h,tip);
 }
 boolean click(double mx,double my){return false;}
 boolean scroll(double mx,double my,double delta){boolean moved=list.wheel(mx,my,delta);arrange();return moved;}
 /** Probe hook: every button of a visible row lies in the list inside content(), a hidden row has none. */
 List<String> layoutProblems(){
  var out=new ArrayList<String>();var c=layout.content();var area=list.area();
  for(int i=0;i<POOL;i++)for(var b:List.of(actions.get(i),deliveries.get(i))){if(!b.visible)continue;var r=Rect.of(b);
   if(!c.containsRect(r)||!area.containsRect(r))out.add("quest row button outside the list: '"+b.getMessage().getString()+"' ("+r.x()+","+r.y()+")");
   if(list.y(list.first()+i)<0)out.add("quest button of a hidden row is visible: "+i);}
  return out;
 }
}
