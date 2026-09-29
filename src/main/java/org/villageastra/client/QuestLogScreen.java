package org.villageastra.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
/** The player's own errands, wherever they stand: the quests tab of the inventory. The board of a village speaks for that village; this speaks
 *  for the player — every errand they have taken, under the village that asked for it, with how far along it is, how long is left and which
 *  way its place lies from where they are now. One is opened to read what it asks and where the cross on its chart stands. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class QuestLogScreen extends Screen {
 private static CompoundTag log=new CompoundTag();private static long asked;
 /** The answer from the server; the tab draws whatever came last. */
 @SubscribeEvent public static void received(ConstructionNetwork.QuestLogReceived event){log=event.tag.copy();}
 /** Asked for at most once a second: the tab is opened and closed often, the errands change slowly. */
 public static void ask(){var mc=Minecraft.getInstance();if(mc.level==null)return;long now=mc.level.getGameTime();if(now-asked<20&&!log.isEmpty())return;asked=now;ConstructionNetwork.askQuestLog();}
 public static CompoundTag snapshot(){return log.copy();}
 public static int count(){int n=0;for(var raw:log.getList("villages",Tag.TAG_COMPOUND))n+=((CompoundTag)raw).getList("quests",Tag.TAG_COMPOUND).size();return n;}

 private static final int ROW=30,HEAD=14;
 private final Screen parent;private final Scroll list=new Scroll(ROW);private Layout layout;private OfficeButton done;
 private final List<Object[]> rows=new ArrayList<>();private UUID open;
 public QuestLogScreen(Screen parent){super(Component.translatable("quest.villageastra.log_title"));this.parent=parent;}
 @Override protected void init(){
  layout=new Layout(width,height);
  done=addRenderableWidget(OfficeButton.of(Component.translatable("gui.done"),ItemStack.EMPTY,Tone.INFO,b->onClose()).at(layout.done()));
  ask();
 }
 @Override public void tick(){ask();}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 /** One line per village and one per errand of it, in the order the server sent them. */
 private void gather(){
  rows.clear();
  for(var raw:log.getList("villages",Tag.TAG_COMPOUND)){var village=(CompoundTag)raw;
   rows.add(new Object[]{"village",village});
   for(var q:village.getList("quests",Tag.TAG_COMPOUND))rows.add(new Object[]{"quest",q,village});}
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  renderBackground(g);gather();
  OfficeUi.frame(g,font,layout,new ItemStack(Items.WRITABLE_BOOK),getTitle(),Component.translatable("quest.villageastra.log_mine",count()),count()>0?Tone.ACTION:Tone.OFF);
  var c=layout.content();
  if(rows.isEmpty()){OfficeUi.drawText(g,font,OfficeUi.fit(font,Component.translatable("quest.villageastra.log_empty").getString(),c.w()),c.x()+4,c.y()+8,OfficeUi.MUTED);
   super.render(g,mx,my,partial);OfficeUi.tips().render(g,font,mx,my,this);return;}
  list.bounds(c,rows.size());
  OfficeUi.clip(g,c);
  for(int i=list.first();i<rows.size();i++){int y=list.y(i);if(y<0)continue;var row=rows.get(i);
   if(row[0].equals("village"))village(g,(CompoundTag)row[1],c.x(),y,list.rowW());
   else quest(g,(CompoundTag)row[1],c.x(),y,list.rowW(),mx,my);}
  OfficeUi.unclip(g);list.render(g);
  super.render(g,mx,my,partial);OfficeUi.tips().render(g,font,mx,my,this);
 }
 /** The village that asked: its own name (AD-145), and after it which way it lies from the player and how far. */
 private void village(GuiGraphics g,CompoundTag village,int x,int y,int w){
  var side=Component.translatable("quest.villageastra.side."+village.getString("dir"));
  var where=village.getString("name").isEmpty()?Component.translatable("quest.villageastra.log_village",side,village.getInt("away"))
   :Component.translatable("quest.villageastra.log_village_named",village.getString("name"),side,village.getInt("away"));
  OfficeUi.header(g,font,x,y+4,w,new ItemStack(Items.BELL),where);
  var score=Component.translatable("quest.villageastra.log_trust",village.getLong("score"));
  OfficeUi.drawText(g,font,score,x+w-font.width(score)-2,y+6,OfficeUi.MUTED);
 }
 private void quest(GuiGraphics g,CompoundTag q,int x,int y,int w,int mx,int my){
  boolean hover=new Rect(x,y,w,ROW-2).contains(mx,my);boolean shown=q.getUUID("id").equals(open);
  int progress=q.getInt("progress"),target=Math.max(1,q.getInt("target"));boolean done=progress>=q.getInt("target");
  var tone=done?Tone.OK:q.getLong("left")<2400?Tone.BAD:Tone.ACTION;
  OfficeUi.row(g,x,y,w,ROW-2,shown,hover,null);g.fill(x,y,x+2,y+ROW-2,tone.fg());
  OfficeUi.drawText(g,font,OfficeUi.fit(font,QuestPanel.title(q).getString(),w-12),x+6,y+3,OfficeUi.TEXT);
  int bx=x+6,bw=Math.min(72,w/3);
  OfficeUi.bar(g,font,bx,y+14,bw,progress,target,done?Tone.OK:Tone.WAIT,Component.literal(progress+"/"+q.getInt("target")));
  int tx=bx+bw+6;
  var left=Component.translatable("quest.villageastra.left",OfficeUi.duration(q.getLong("left")));
  if(tx+font.width(left)<=x+w-4)tx=OfficeUi.drawText(g,font,left,tx,y+16,q.getLong("left")<2400?Tone.BAD.fg():OfficeUi.MUTED)+8;
  if(q.contains("away")){var place=Component.translatable("quest.villageastra.place",Component.translatable("quest.villageastra.place."+(q.getString("kind").isEmpty()?"camp":q.getString("kind"))),
    Component.translatable("quest.villageastra.side."+q.getString("dir")),q.getInt("away"),Component.empty());
   if(tx+20<x+w)OfficeUi.drawText(g,font,OfficeUi.fit(font,place.getString(),x+w-4-tx),tx,y+16,OfficeUi.MUTED);}
  var tip=new ArrayList<Component>();tip.add(QuestPanel.title(q));
  tip.add(Component.translatable("quest.villageastra.instructions."+instructions(q)));
  if(q.getBoolean("handover"))tip.add(Component.translatable("quest.villageastra.log_handover"));
  if(!q.getString("status").isEmpty())tip.add(Component.translatable("quest.villageastra.status."+q.getString("status"),q.getInt("status_left")));
  tip.add(Component.translatable("quest.villageastra.reward",q.getLong("coins"),q.getLong("reputation")));
  if(q.contains("away"))tip.add(Component.translatable("quest.villageastra.log_chart"));
  OfficeUi.tips().add(x,y,w,ROW-2,tip);
 }
 private static String instructions(CompoundTag q){
  var type=q.getString("template");
  return switch(type){case "supply"->"supply";case "donation"->"donation";case "clearing","defence","lair","beast"->"combat";case "bring","rescue"->"escort";
   case "captive","lost","collapse","beacon","embassy","ruin","survey","relic","sanctuary","crypt","warcamp","flock","sinkhole","brood","sow","tidings","cages","freight"->type;
   case "escort"->"caravan";case "plea"->"plea";default->q.getBoolean("handover")?"handover":"explore";};
 }
 @Override public boolean mouseClicked(double x,double y,int button){
  if(super.mouseClicked(x,y,button))return true;
  int i=list.hit(x,y);
  if(i>=0&&i<rows.size()&&rows.get(i)[0].equals("quest")){var q=(CompoundTag)rows.get(i)[1];open=q.getUUID("id").equals(open)?null:q.getUUID("id");return true;}
  return false;
 }
 @Override public boolean mouseScrolled(double x,double y,double delta){return list.wheel(x,y,delta)||super.mouseScrolled(x,y,delta);}
}
