package org.villageastra.dialog;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-149 (owner 2026-09-24: the fewest notes, the most talk): errands are given out and handed back by the people who ask for them.
 *  <p>Whoever asked — the resident whose need it is, or the mayor for the village's own needs — offers the errand in the conversation
 *  window in their own words: what they need, what it means, where it lies, what it pays and how long it stands. The player answers with a
 *  button; the same resident hears how it goes, takes what was brought and hears it given up. The board in the office stays the village's
 *  summary; the mark over a resident (AD-116) already shows who has something to ask, and the same rule decides it here. */
public final class QuestTalk {
 private QuestTalk(){}
 public static final String TOPIC="quest";
 /** How many errands one resident speaks of in one conversation. */
 private static final int MOST=4;
 static{Dialogs.register(TOPIC,QuestTalk::choose);Dialogs.addTopic(QuestTalk::options);}
 /** Loads the class, registering its topic and its handler (called once at start). */
 public static void init(){}
 private static Component text(String key,Object... values){return Component.translatable("dialog.villageastra.quest."+key,values);}
 /** A resident holding a captive, a hurt companion or a blocked shaft says so in the words that already belong to it. */
 private static final Set<String> HELD=Set.of("caged","chief","injured","trapped");
 /** Why the board said no, or what came of handing over — the same reasons the office gives, said by the person who asked. */
 private static Component answer(String result){return Component.translatable((HELD.contains(result)?"quest.villageastra.hold.":"quest.villageastra.result.")+result);}

 private static SettlementData.Entry entry(ServerPlayer p,ResidentEntity npc){
  return npc==null||npc.settlementId()==null?null:SettlementData.get(p.server).entry(npc.settlementId());
 }
 /** AD-116's own rule: an errand is this resident's to speak of when they asked for it, and the village's own needs are the mayor's. */
 private static boolean theirs(SettlementData.Entry e,ResidentEntity npc,CompoundTag q){
  var r=e.settlement().resident(npc.getUUID());if(r==null||!r.alive())return false;
  return q.hasUUID("giver")?q.getUUID("giver").equals(r.id()):r.profession()==Profession.MAYOR;
 }
 /** This resident's errands in that state, at most MOST of them; a taken one only when it is this player's. */
 private static List<CompoundTag> cards(ServerPlayer p,SettlementData.Entry e,ResidentEntity npc,String state){
  var out=new ArrayList<CompoundTag>();long now=SettlementData.get(p.server).clock().ticks();
  for(var raw:Quests.board(p.serverLevel(),e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;
   if(!q.getString("state").equals(state)||q.getLong("deadline")<=now||!theirs(e,npc,q))continue;
   if(state.equals(Quests.TAKEN)&&(!q.hasUUID("owner")||!q.getUUID("owner").equals(p.getUUID())))continue;
   out.add(q);if(out.size()>=MOST)break;}
  return out;
 }
 /** The answers this resident adds to their greeting: what they have to ask, and what this player already took from them. */
 private static List<DialogOption> options(ServerPlayer p,ResidentEntity npc){
  var e=entry(p,npc);if(e==null)return List.of();
  var out=new ArrayList<DialogOption>();
  int offers=cards(p,e,npc,Quests.OPEN).size(),taken=cards(p,e,npc,Quests.TAKEN).size();
  if(offers>0)out.add(DialogOption.of(TOPIC+"/offers",text("ask",offers)));
  if(taken>0)out.add(DialogOption.of(TOPIC+"/mine",text("report",taken)));
  return out;
 }

 /** The page carries the cards it speaks of, so a button always answers about the card the player was shown. */
 private static CompoundTag context(List<CompoundTag> cards){
  var context=new CompoundTag();var ids=new ListTag();
  for(var q:cards)ids.add(NbtUtils.createUUID(q.getUUID("id")));
  context.put("ids",ids);return context;
 }
 private static UUID id(CompoundTag context,String action){
  int at;try{at=Integer.parseInt(action.substring(action.indexOf('_')+1));}catch(RuntimeException ex){return null;}
  var ids=context.getList("ids",Tag.TAG_INT_ARRAY);
  return at<0||at>=ids.size()?null:NbtUtils.loadUUID(ids.get(at));
 }
 private static int index(List<CompoundTag> cards,UUID id){
  if(id==null)return -1;
  for(int i=0;i<cards.size();i++)if(cards.get(i).getUUID("id").equals(id))return i;
  return -1;
 }

 // ---------------------------------------------------------------- what a resident says about an errand
 /** What is asked for, in the words the board uses for it. */
 private static Component goal(CompoundTag q){
  var template=Component.translatable("quest.villageastra.template."+q.getString("template"));
  if(q.getString("item").isEmpty())return Component.translatable("quest.villageastra.goal_count",template,q.getInt("target"));
  var item=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(q.getString("item")))).getHoverName();
  return Component.translatable("quest.villageastra.goal_item",template,q.getInt("target"),item);
 }
 /** What the errand asks of the player, by the kind of errand it is (the same words the quests tab gives). */
 private static Component what(CompoundTag q){
  var template=q.getString("template");
  var kind=switch(template){case "supply"->"supply";case "donation"->"donation";case "clearing","defence","lair","beast"->"combat";
   case "bring","rescue"->"escort";case "escort"->"caravan";case "plea"->"plea";
   case "flock","sinkhole","brood","sow","tidings","cages","freight"->template;
   default->Adventures.adventure(template)?template:Quests.handsOver(q)?"handover":"explore";};
  return Component.translatable("quest.villageastra.instructions."+kind);
 }
 /** Where it leads, when it leads anywhere: which way and how far from where the player stands. */
 private static Component where(ServerPlayer p,CompoundTag q){
  if(!q.contains("site"))return null;
  var site=BlockPos.of(q.getLong("site"));var kind=q.getString("kind");
  return Component.translatable("quest.villageastra.place",Component.translatable("quest.villageastra.place."+(kind.isEmpty()?"camp":kind)),
   Component.translatable("quest.villageastra.side."+Adventures.bearing(p.blockPosition(),site)),Adventures.away(p.blockPosition(),site),Component.empty());
 }
 private static long minutes(ServerPlayer p,CompoundTag q){
  return Math.max(0,q.getLong("deadline")-SettlementData.get(p.server).clock().ticks())/1200;
 }
 /** The errand as its asker tells it. */
 private static List<Component> words(ServerPlayer p,ResidentEntity npc,SettlementData.Entry e,CompoundTag q){
  var out=new ArrayList<Component>();
  var r=e.settlement().resident(npc.getUUID());
  out.add(text(!q.hasUUID("giver")&&r!=null&&r.profession()==Profession.MAYOR?"intro.village":"intro.own"));
  out.add(goal(q));out.add(what(q));
  var where=where(p,q);if(where!=null)out.add(where);
  out.add(text("pay",q.getLong("coins"),q.getLong("reputation")));
  out.add(text("stands",minutes(p,q)));
  return out;
 }

 // ---------------------------------------------------------------- the pages
 private static void offers(ServerPlayer p,ResidentEntity npc){
  var e=entry(p,npc);if(e==null)return;var cards=cards(p,e,npc,Quests.OPEN);
  if(cards.isEmpty()){Dialogs.say(p,npc,text("nothing"));return;}
  if(cards.size()==1){card(p,npc,e,cards,0);return;}
  var options=new ArrayList<DialogOption>();
  for(int i=0;i<cards.size();i++)options.add(DialogOption.of(TOPIC+"/show_"+i,goal(cards.get(i))));
  options.add(DialogOption.of(TOPIC+"/back",text("later")));
  Dialogs.open(p,new DialogPage(npc.getUUID(),List.of(text("pick")),options,context(cards)));
 }
 /** One errand, with the answer that takes it — or the reason it cannot be taken, on the button that stays grey. */
 private static void card(ServerPlayer p,ResidentEntity npc,SettlementData.Entry e,List<CompoundTag> cards,int at){
  var q=cards.get(at);var refusal=Quests.takeRefusal(p,e.settlement().id(),q);
  var options=new ArrayList<DialogOption>();
  options.add(new DialogOption(TOPIC+"/take_"+at,text("take"),refusal.isEmpty(),refusal.isEmpty()?null:answer(refusal)));
  if(cards.size()>1)options.add(DialogOption.of(TOPIC+"/offers",text("others")));
  options.add(DialogOption.of(TOPIC+"/back",text("later")));
  Dialogs.open(p,new DialogPage(npc.getUUID(),words(p,npc,e,q),options,context(cards)));
 }
 /** What this player took from this resident. */
 private static void mine(ServerPlayer p,ResidentEntity npc){
  var e=entry(p,npc);if(e==null)return;var cards=cards(p,e,npc,Quests.TAKEN);
  if(cards.isEmpty()){Dialogs.say(p,npc,text("nothing_taken"));return;}
  if(cards.size()==1){held(p,npc,e,cards,0);return;}
  var options=new ArrayList<DialogOption>();
  for(int i=0;i<cards.size();i++)options.add(DialogOption.of(TOPIC+"/held_"+i,goal(cards.get(i))));
  options.add(DialogOption.of(TOPIC+"/back",text("later")));
  Dialogs.open(p,new DialogPage(npc.getUUID(),List.of(text("which")),options,context(cards)));
 }
 /** One errand in hand: how far along it is, and the answers that hand it over or give it up. */
 private static void held(ServerPlayer p,ResidentEntity npc,SettlementData.Entry e,List<CompoundTag> cards,int at){
  var q=cards.get(at);var lines=new ArrayList<Component>();
  lines.add(goal(q));
  lines.add(text("done",q.getInt("progress"),Math.max(1,q.getInt("target"))));
  var where=where(p,q);if(where!=null)lines.add(where);
  if(!q.getString("status").isEmpty())lines.add(Component.translatable("quest.villageastra.status."+q.getString("status"),q.getInt("status_left")));
  lines.add(text("stands",minutes(p,q)));
  var options=new ArrayList<DialogOption>();
  if(Quests.handsOver(q))options.add(DialogOption.of(TOPIC+"/hand_"+at,text("hand")));
  options.add(DialogOption.of(TOPIC+"/drop_"+at,text("drop")));
  if(cards.size()>1)options.add(DialogOption.of(TOPIC+"/mine",text("others")));
  options.add(DialogOption.of(TOPIC+"/back",text("later")));
  Dialogs.open(p,new DialogPage(npc.getUUID(),lines,options,context(cards)));
 }

 private static void choose(ServerPlayer p,ResidentEntity npc,String action,CompoundTag context){
  if(npc==null)return;var e=entry(p,npc);if(e==null)return;var village=e.settlement().id();
  if(action.equals("offers")){offers(p,npc);return;}
  if(action.equals("mine")){mine(p,npc);return;}
  if(action.equals("back")){Dialogs.greet(p,npc);return;}
  if(action.startsWith("show_")||action.startsWith("held_")){
   boolean open=action.startsWith("show_");
   var cards=cards(p,e,npc,open?Quests.OPEN:Quests.TAKEN);int at=index(cards,id(context,action));
   if(at<0){Dialogs.say(p,npc,text("gone"));return;}
   if(open)card(p,npc,e,cards,at);else held(p,npc,e,cards,at);return;}
  var id=id(context,action);
  if(id==null){Dialogs.say(p,npc,text("gone"));return;}
  if(action.startsWith("take_")){
   // Whatever the client sent, the errand is taken by the rules of the board itself, and the resident says what came of it.
   var said=Quests.take(p,village,id);
   Dialogs.say(p,npc,said.equals("ok")?text("agreed"):answer(said));return;}
  if(action.startsWith("hand_")){
   var said=Quests.handOver(p,village,id);
   Dialogs.say(p,npc,said.equals("ok")?text("thanks"):answer(said));return;}
  if(action.startsWith("drop_")){
   var said=Quests.abandon(p,village,id);
   Dialogs.say(p,npc,said.equals("done")?text("thanks"):said.equals("ok")?text("dropped"):answer(said));return;}
 }
 /** For tests and probes: the errands this resident would speak of now. */
 public static List<CompoundTag> speaksOf(ServerPlayer p,ResidentEntity npc,String state){
  var e=entry(p,npc);return e==null?List.of():cards(p,e,npc,state);
 }
}
