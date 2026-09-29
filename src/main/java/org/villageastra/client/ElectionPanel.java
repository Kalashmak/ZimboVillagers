package org.villageastra.client;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
/** The elections tab, also for a visitor: two cards (your reputation against the threshold, and the next election), one status chip
 *  that says what you can do, and the orders in the footer. The rules live in the '?' tooltip. */
final class ElectionPanel {
 private final Layout layout;private final OfficeButton candidate,resign;
 ElectionPanel(Layout layout,Consumer<Button> add){
  this.layout=layout;
  candidate=OfficeButton.of(text("enroll"),new ItemStack(Items.GOLDEN_HELMET),Tone.ACTION,b->{var t=view();send(t,t.getBoolean("candidate")?1:0);});
  resign=OfficeButton.of(text("resign"),new ItemStack(Items.BARRIER),Tone.BAD,b->send(view(),2)).confirm(office("confirm"));
  add.accept(candidate);add.accept(resign);
 }
 /** The old screen's constructor, kept until ConstructionScreen passes its Layout. */
 ElectionPanel(int height,Consumer<Button> add){this(new Layout(Minecraft.getInstance().getWindow().getGuiScaledWidth(),height),add);}
 Button candidateButton(){return candidate;}
 Button resignButton(){return resign;}
 static CompoundTag view(){return ConstructionOverlay.snapshot().getCompound("election");}
 private static Component text(String key,Object... values){return Component.translatable("election.villageastra."+key,values);}
 private static Component office(String key,Object... values){return Component.translatable("office.villageastra."+key,values);}
 private static void send(CompoundTag t,int action){if(t.hasUUID("village")&&t.getBoolean("safe"))ConstructionNetwork.sendElection(new ConstructionNetwork.ElectionOrder(t.getUUID("village"),t.getLong("epoch"),t.getLong("sequence"),action));}
 void tick(boolean visible){
  var t=view();boolean village=visible&&t.hasUUID("village"),safe=t.getBoolean("safe"),standing=t.getBoolean("candidate"),mayor=t.getBoolean("mayor");long score=t.getLong("score"),min=t.getLong("minimum");
  candidate.visible=village;resign.visible=village&&mayor;
  // Standing is blue (a decision waits); withdrawing is merely available, so it stays amber.
  candidate.setMessage(text(standing?"withdraw":"enroll"));candidate.tone(standing?Tone.WAIT:Tone.ACTION);
  candidate.active=safe&&(standing||score>=min);candidate.reason(!safe?office("reason.unsafe"):office("reason.need_rep",min));
  resign.active=safe&&mayor;resign.reason(office("reason.unsafe"));
  if(!resign.visible)resign.disarm();
  if(!village)return;
  var font=Minecraft.getInstance().font;var flow=layout.flow();place(flow,candidate,font);if(resign.visible)place(flow,resign,font);
 }
 /** A footer slot at the button's width, or an icon-only slot with the label in its tooltip when the row is full. */
 private static void place(Flow flow,OfficeButton b,Font f){var r=flow.next(b.prefWidth(f));b.hint(null);if(r==null){r=flow.next(20);b.hint(b.getMessage());}b.at(r);}
 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  var t=view();var c=l.content();
  if(!t.hasUUID("village")){var s=OfficeUi.fit(f,text("absent").getString(),c.w());OfficeUi.drawText(g,f,s,c.x()+(c.w()-f.width(s))/2,c.y()+30,OfficeUi.MUTED);return;}
  // AD-124: halves up to M, the reputation card 3/5 at L.
  var cols=c.splitH(l.size()==OfficeGrid.Size.L?(c.w()-8)*3/5:(c.w()-8)/2,8);OfficeUi.block("reputation",cols[0]);reputation(g,f,t,cols[0]);OfficeUi.endBlock();OfficeUi.block("election",cols[1]);election(g,f,t,cols[1]);OfficeUi.endBlock();
 }
 /** Card 1: your reputation against the threshold, what moved it, and what that lets you do. */
 private void reputation(GuiGraphics g,Font f,CompoundTag t,Rect r){
  int x=r.x(),w=r.w(),y=OfficeUi.header(g,f,x,r.y(),w,new ItemStack(Items.EMERALD),office("election.reputation"));
  long score=t.getLong("score"),min=Math.max(1,t.getLong("minimum")),max=Math.max(min,score*5/4);Tone tone=score>=min?Tone.OK:Tone.WAIT;
  OfficeUi.bar(g,f,x,y,w,score,max,tone,Component.literal(score+" / "+min));if(max>min)OfficeUi.marker(g,x,y,w,min,max);
  OfficeUi.tips().add(new Rect(x,y,w,11),office("election.reputation"),text("score",score,min));y+=15;
  // What moved the score: only the non-zero parts, wrapped to the card.
  int tx=x;boolean any=false;String[] keys={"gifts","theft","decay"};Tone[] tones={Tone.OK,Tone.BAD,Tone.OFF};
  for(int i=0;i<keys.length;i++){long v=t.getLong(keys[i]);if(v<=0)continue;
   var label=office("election."+keys[i],v);int tw=OfficeUi.tagWidth(f,label);if(tx>x&&tx+tw>x+w){tx=x;y+=13;}if(tw>w)continue;OfficeUi.tag(g,f,tx,y,label,tones[i]);tx+=tw+4;any=true;}
  if(any)y+=15;
  // One chip says where you stand and what you can do now.
  boolean mayor=t.getBoolean("mayor"),standing=t.getBoolean("candidate"),safe=t.getBoolean("safe");
  Component status;Tone st;ItemStack icon;
  if(mayor){status=office("election.you_mayor");st=Tone.OK;icon=new ItemStack(Items.GOLDEN_HELMET);}
  else if(standing){status=office("election.you_candidate");st=Tone.OK;icon=new ItemStack(Items.NAME_TAG);}
  else if(!safe){status=office("election.unsafe");st=Tone.BAD;icon=new ItemStack(Items.SKELETON_SKULL);}
  else if(score>=min){status=office("election.can_run");st=Tone.ACTION;icon=new ItemStack(Items.NAME_TAG);}
  else{status=office("election.need_more",min-score);st=Tone.WAIT;icon=new ItemStack(Items.EMERALD);}
  if(y+18<=r.bottom()){OfficeUi.chip(g,f,x,y,w,icon,status,Component.empty(),st);OfficeUi.tips().add(new Rect(x,y,w,18),status);}
 }
 /** Card 2: when the next election comes, against the whole term, who holds the office, and the last result. */
 private void election(GuiGraphics g,Font f,CompoundTag t,Rect r){
  int x=r.x(),w=r.w(),y=OfficeUi.header(g,f,x,r.y(),w,new ItemStack(Items.CLOCK),office("election.title"));
  long remaining=t.getLong("remaining"),period=t.contains("period")?Math.max(1,t.getLong("period")):24000;
  // A candidate with little time left waits for the count; for everyone else the clock is only information.
  Tone clock=t.getBoolean("candidate")&&remaining<=2400?Tone.WAIT:Tone.INFO;var next=office("election.next",OfficeUi.duration(remaining));
  OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.CLOCK),next,Component.empty(),clock);OfficeUi.tips().add(new Rect(x,y,w,18),next);y+=20;
  OfficeUi.bar(g,x,y,w,period-Math.min(period,remaining),period,Tone.INFO);y+=9;
  String holder=t.getString("holder");boolean none=holder.isEmpty()||holder.equals("—");var who=none?office("election.no_mayor"):office("election.holder",holder);
  OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.GOLDEN_HELMET),who,Component.empty(),none?Tone.OFF:Tone.INFO);OfficeUi.tips().add(new Rect(x,y,w,18),who);y+=22;
  if(t.getLong("lastElection")>0&&y+9<=r.bottom()){OfficeUi.drawText(g,f,OfficeUi.fit(f,office("election.last",t.getLong("lastScore")).getString(),w),x,y,OfficeUi.MUTED);y+=12;}
  if(t.contains("candidates")&&y+9<=r.bottom())OfficeUi.drawText(g,f,OfficeUi.fit(f,office("election.candidates",t.getInt("candidates")).getString(),w),x,y,OfficeUi.MUTED);
 }
 /** The old screen's call, kept until ConstructionScreen passes its Layout. */
 void render(GuiGraphics g,Font f,int width,int height){render(g,f,new Layout(width,height),-1,-1);}
 boolean click(double mx,double my){return false;}
 boolean scroll(double mx,double my,double delta){return false;}
 /** Nothing on this tab scrolls any more: the rules moved to '?'. */
 boolean scroll(double delta){return false;}
}
