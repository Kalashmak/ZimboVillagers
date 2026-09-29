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
/** AD-066: the construction tab — what is being built, how far, what the crew still lacks, and the mayor's orders (pause, call off,
 *  confirm or discard an estimate). The legend of the world projection lives in the '?' tooltip. */
final class ConstructionPanel {
 static final int CELL_W=44,CELL_H=22;
 private static final List<String> PARTS=List.of("clear","foundation","scaffold_up","place","scaffold_down");
 private final Layout layout;private final Runnable openAtlas;private final Scroll grid=new Scroll(CELL_H);
 private final OfficeButton pause,callOff,confirm,discard;private Rect order;
 ConstructionPanel(Layout layout,Consumer<Button> add,Runnable openAtlas){
  this.layout=layout;this.openAtlas=openAtlas;
  pause=OfficeButton.of(text("pause"),new ItemStack(Items.CLOCK),Tone.ACTION,b->{var t=ConstructionOverlay.snapshot();if(t.getBoolean("canManage")&&t.hasUUID("village")&&t.hasUUID("id"))ConstructionNetwork.sendPause(new ConstructionNetwork.PauseOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision"),!t.getBoolean("paused")));});
  callOff=OfficeButton.of(text("call_off"),new ItemStack(Items.BARRIER),Tone.BAD,b->{var t=ConstructionOverlay.snapshot();if(t.getBoolean("canManage")&&t.hasUUID("village")&&t.hasUUID("id"))ConstructionNetwork.sendCancel(new ConstructionNetwork.CancelOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision")));}).confirm(office("confirm"));
  confirm=OfficeButton.of(office("construction.confirm"),new ItemStack(Items.PAPER),Tone.ACTION,b->draft(1));
  discard=OfficeButton.of(office("construction.cancel_draft"),new ItemStack(Items.BARRIER),Tone.BAD,b->draft(2)).confirm(office("confirm"));
  add.accept(pause);add.accept(callOff);add.accept(confirm);add.accept(discard);
 }
 private static Component text(String key,Object... args){return Component.translatable("construction.villageastra."+key,args);}
 private static Component office(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 /** The estimate goes through the existing draft channel: 1 confirms it, 2 discards it. */
 private static void draft(int action){var t=ConstructionOverlay.snapshot();if(t.getBoolean("canManage")&&t.hasUUID("village")&&t.hasUUID("id"))ConstructionNetwork.sendDraft(new ConstructionNetwork.DraftOrder(t.getUUID("village"),t.getLong("epoch"),t.getLong("revision"),action,t.getUUID("id")));}
 Button pauseButton(){return pause;}
 Button callOffButton(){return callOff;}
 Button confirmButton(){return confirm;}
 Button discardButton(){return discard;}
 static boolean project(CompoundTag t){return t.hasUUID("id")&&!t.getBoolean("survey");}
 static Component lockReason(CompoundTag t){return office(t.getString("lockReason").equals("unsafe")?"reason.unsafe":"reason.only_mayor");}
 void tick(boolean visible){
  var t=ConstructionOverlay.snapshot();boolean project=visible&&project(t),draft=t.getBoolean("draft"),manage=t.getBoolean("canManage");
  pause.visible=callOff.visible=project&&!draft;confirm.visible=discard.visible=project&&draft;
  // Blue only when the paused project waits for the mayor to resume it; pausing a running one is merely available.
  pause.setMessage(text(t.getBoolean("paused")?"resume":"pause"));pause.tone(t.getBoolean("paused")?Tone.ACTION:Tone.INFO);pause.active=manage;pause.reason(lockReason(t));
  callOff.active=manage&&t.getInt("index")==0;callOff.reason(!manage?lockReason(t):office("construction.calloff_late"));
  confirm.active=manage&&t.getBoolean("confirmable");confirm.reason(!manage?lockReason(t):Component.translatable("work.villageastra."+(t.getInt("conflicts")>0?"changed_target":t.getInt("hidden")>0?"needs_survey":"awaiting_confirmation")));
  discard.active=manage;discard.reason(lockReason(t));
  if(!project){OfficeUi.disarm(List.of(callOff,discard));return;}
  var font=Minecraft.getInstance().font;var flow=layout.flow();
  if(draft){place(flow,confirm,font,null);place(flow,discard,font,null);}else{place(flow,pause,font,null);place(flow,callOff,font,text("call_off_hint"));}
 }
 /** A footer slot at the button's own width, or an icon-only slot with the label in its tooltip when the row is full. */
 private static void place(Flow flow,OfficeButton b,Font f,Component hint){var r=flow.next(b.prefWidth(f));b.hint(hint);if(r==null){r=flow.next(20);b.hint(b.getMessage());}b.at(r);}
 void render(GuiGraphics g,Font f,Layout l,int mx,int my){
  var t=ConstructionOverlay.snapshot();var c=l.content();order=null;
  if(!project(t)){empty(g,f,t,c,mx,my);return;}
  // AD-124: one column at XS (the materials under the status), 11/20 at S, halves at M and L.
  boolean narrowCrew;Rect left,right;
  switch(l.size()){case XS->{left=new Rect(c.x(),c.y(),c.w(),statusHeight(c.w()));right=new Rect(c.x(),left.bottom()+4,c.w(),Math.max(0,c.bottom()-left.bottom()-4));}
   case S->{var cols=c.splitH(c.w()*11/20,8);left=cols[0];right=cols[1];}
   default->{var cols=l.split(c,8,1,1);left=cols[0];right=cols[1];}}
  narrowCrew=left.w()<180;
  OfficeUi.block("status",left);int x=left.x(),w=left.w(),y=left.y();
  boolean hall=!t.getString("kind").equals("building");String stage=t.getBoolean("draft")?"draft":t.getString("stage");Tone tone=stageTone(stage);
  // Line 1: what is being built, and in which stage.
  var icon=hall?OfficeUi.buildingIcon("town_hall"):OfficeUi.buildingIcon(t.getString("design"));g.renderItem(icon,x,y);
  var name=hall?text("hall",OfficeUi.roman(t.getInt("level"))):ConstructionOverlay.designName(t.getString("design"));
  var stageText=office("stage."+(stage.isEmpty()?"work":stage));int tagW=OfficeUi.tagWidth(f,stageText);
  tagW=Math.min(tagW,Math.max(12,w/2));
  int nameEnd=OfficeUi.label(g,f,name,x+20,y+4,w-20-tagW-4,OfficeUi.TEXT);
  OfficeUi.tips().add(x,y,Math.max(16,nameEnd-x),16,List.of(name,t.contains("width")?text("site",t.getInt("width"),t.getInt("depth"),t.getInt("y")):text("site_y",t.getInt("y"))));
  OfficeUi.tag(g,f,x+w-tagW,y+3,stageText,tone,tagW);y+=20;
  // Line 2: operations done of all (and the share), in the stage's tone.
  Tone barTone=stage.equals("pause")?Tone.WAIT:stage.equals("blocked")?Tone.BAD:Tone.OK;int index=t.contains("progress")?t.getInt("progress"):t.getInt("index"),ops=Math.max(1,t.getInt("total"));
  OfficeUi.bar(g,f,x,y,w,index,ops,barTone,l.size().atLeast(OfficeGrid.Size.M)?office("v2.progress",index,t.getInt("total"),index*100/ops):Component.literal(index+" / "+t.getInt("total")));y+=15;
  // Line 3: the five parts of the work as a stepper: done green, current outlined with its progress, the rest grey.
  var parts=t.getCompound("parts");int segW=(w-8)/5;boolean current=false;
  for(int i=0;i<PARTS.size();i++){var k=PARTS.get(i);var part=parts.getCompound(k);int done=part.getInt("done"),total=part.getInt("total"),sx=x+i*(segW+2);
   boolean finished=parts.contains(k)&&done>=total,now=!finished&&!current&&parts.contains(k);if(now)current=true;
   g.fill(sx,y,sx+segW,y+12,finished?Tone.OK.bg():now?OfficeUi.CARD:Tone.OFF.bg());
   if(now){g.renderOutline(sx,y,segW,12,OfficeUi.TITLE);OfficeUi.bar(g,sx+2,y+8,segW-4,done,Math.max(1,total),Tone.OK);}
   OfficeUi.sign(g,sx+(segW-5)/2,y+(now?1:3),finished?Tone.OK:now?Tone.INFO:Tone.OFF);
   OfficeUi.tips().add(sx,y,segW,12,List.of(text("part."+k),Component.literal(done+" / "+total)));}
  y+=16;
  // Line 4: why the crew does what it does, in its tone.
  var status=t.getString("status");if(!status.isEmpty()){var st=Component.translatable("work.villageastra."+status);OfficeUi.tag(g,f,x,y,st,statusTone(status),w);OfficeUi.tips().add(x,y,w,11,List.of(st));}
  y+=15;
  // Line 5: the crew and a rough time left: side by side from 180 px, else one under the other.
  int half=narrowCrew?w:(w-4)/2,builders=t.getInt("builders");var eta=office("construction.eta",OfficeUi.duration(t.getInt("minutes")*1200L));
  OfficeUi.chip(g,f,x,y,half,new ItemStack(Items.PLAYER_HEAD),office("construction.builders",builders),Component.empty(),builders==0&&!stage.equals("complete")?Tone.BAD:Tone.INFO);
  var names=new ArrayList<Component>();names.add(office("construction.builders",builders));for(var n:t.getList("crew",Tag.TAG_STRING))names.add(Component.literal(n.getAsString()));OfficeUi.tips().add(x,y,half,18,names);
  int ex=narrowCrew?x:x+half+4,ey=narrowCrew?y+22:y;
  OfficeUi.chip(g,f,ex,ey,half,new ItemStack(Items.CLOCK),Component.literal("~").append(OfficeUi.duration(t.getInt("minutes")*1200L)),Component.empty(),Tone.INFO);OfficeUi.tips().add(new Rect(ex,ey,half,18),eta);
  OfficeUi.endBlock();
  OfficeUi.block("materials",right);materials(g,f,t,right,mx,my);OfficeUi.endBlock();
 }
 /** The status column's height at a width: name, bar, stepper, status, and the two chips side by side or stacked. */
 static int statusHeight(int w){return 20+15+16+15+(w<180?40:18);}
 /** The materials the project still needs: a grid of cells, missing first, each showing only what is missing; details in the tooltip. */
 private void materials(GuiGraphics g,Font f,CompoundTag t,Rect r,int mx,int my){
  int y=OfficeUi.header(g,f,r.x(),r.y(),r.w(),new ItemStack(Items.CHEST),office("construction.materials"));
  var list=new ArrayList<CompoundTag>();for(var raw:t.getList("materials",Tag.TAG_COMPOUND)){var m=(CompoundTag)raw;if(m.getInt("missing")>0)list.add(m);}
  if(list.isEmpty()){OfficeUi.label(g,f,office("construction.all_here"),r.x(),y+2,r.w(),OfficeUi.MUTED);return;}
  list.sort(Comparator.comparingInt((CompoundTag m)->-m.getInt("missing")).thenComparing(m->m.getString("item")));
  int cols=Math.max(1,(r.w()-5)/CELL_W),rows=(list.size()+cols-1)/cols;grid.bounds(new Rect(r.x(),y,r.w(),r.bottom()-y),rows);
  OfficeUi.clip(g,grid.area());
  for(int i=0;i<list.size();i++){int ry=grid.y(i/cols);if(ry<0)continue;var m=list.get(i);int cx=r.x()+(i%cols)*CELL_W;var stack=OfficeUi.icon(m.getString("item"));
   int need=m.getInt("required"),held=m.getInt("held"),stock=m.getInt("stock"),missing=m.getInt("missing");
   var tip=new ArrayList<Component>(List.of(office("construction.cell",need,held,stock,missing)));
   // AD-137: the part of the stock kept for this project — the village's other work does not take it.
   if(m.getInt("reserved")>0)tip.add(office("construction.reserved",m.getInt("reserved")));
   OfficeUi.itemCell(g,f,stack,cx,ry,missing,held+stock,need,tip);
   // On its way (in a builder's cargo): a small grey dot beside the icon (an item draws above fills), never blue — blue means a decision waits for you.
   if(held>0)g.fill(cx+18,ry+14,cx+21,ry+17,OfficeUi.MUTED);}
  OfficeUi.unclip(g);grid.render(g);
 }
 private void empty(GuiGraphics g,Font f,CompoundTag t,Rect c,int mx,int my){
  int cx=c.x()+c.w()/2,y=c.y()+10;g.renderItem(new ItemStack(Items.SCAFFOLDING),cx-8,y);y+=22;OfficeUi.block("empty",c);
  var none=office("construction.none");OfficeUi.label(g,f,none,Math.max(c.x(),cx-f.width(none)/2),y,c.w(),OfficeUi.MUTED);y+=16;
  int w=Math.min(c.w(),220);var orderText=office("construction.order");Tone tone=t.getBoolean("canManage")?Tone.ACTION:Tone.INFO;
  order=new Rect(cx-w/2,y,w,18);OfficeUi.chip(g,f,order.x(),y,w,new ItemStack(Items.FILLED_MAP),orderText,Component.empty(),tone,true,order.contains(mx,my));y+=22;
  OfficeUi.chip(g,f,cx-w/2,y,w,new ItemStack(Items.IRON_SHOVEL),office("construction.mark"),Component.empty(),Tone.INFO);OfficeUi.tips().add(new Rect(cx-w/2,y,w,18),Component.translatable("hall.villageastra.guide"));y+=24;
  var hint=office("help_hint");if(y+9<=c.bottom())OfficeUi.label(g,f,hint,Math.max(c.x(),cx-f.width(hint)/2),y,c.w(),OfficeUi.MUTED);OfficeUi.endBlock();
 }
 static Tone stageTone(String stage){return switch(stage){case "draft"->Tone.ACTION;case "ready","pause"->Tone.WAIT;case "blocked"->Tone.BAD;default->Tone.OK;};}
 static Tone statusTone(String status){return status.equals("awaiting_confirmation")?Tone.ACTION:status.equals("changed_target")||status.equals("needs_survey")?Tone.BAD:status.contains("missing")||status.contains("paused")||status.startsWith("awaiting")?Tone.WAIT:Tone.OK;}
 boolean click(double mx,double my){if(order!=null&&order.contains(mx,my)){openAtlas.run();return true;}return false;}
 boolean scroll(double mx,double my,double delta){return grid.wheel(mx,my,delta);}
 /** The '?' of this tab: the colours of the projection in the world, then how to plan and approve it. */
 static List<Component> help(){
  return List.of(office("construction.help"),text("red").copy().withStyle(s->s.withColor(0xFF394B)),text("blue").copy().withStyle(s->s.withColor(0x398CFF)),text("blocked").copy().withStyle(s->s.withColor(0xFFB347)),Component.translatable("interaction.villageastra.world_plan"));
 }
}
