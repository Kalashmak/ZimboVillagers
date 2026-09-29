package org.villageastra.dialog;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.domain.Resident;
import org.villageastra.server.SettlementData;
import org.villageastra.world.ResidentEntity;
/** AD-146: what every resident says and answers — the greeting with the village's name, trade and gifts (the resident's card), «Как
 *  дела?» (what they do, their village, its research) and goodbye. */
public final class ResidentTalk {
 private ResidentTalk(){}
 public static final String TOPIC="resident";
 private static final int GREETINGS=4;
 static{Dialogs.register(TOPIC,ResidentTalk::choose);}
 /** Loads the class, registering its handler (called once at start). */
 public static void init(){}
 private static SettlementData.Entry entry(ServerPlayer p,ResidentEntity npc){return npc.settlementId()==null?null:SettlementData.get(p.server).entry(npc.settlementId());}
 /** The resident's trade, or «Гость» for one of no village. */
 static Component role(ServerPlayer p,ResidentEntity npc){
  var e=entry(p,npc);var r=e==null?null:e.settlement().resident(npc.getUUID());
  if(r==null)return Component.translatable("dialog.villageastra.role.guest");
  return r.profession()==null?Component.translatable("dialog.villageastra.role.none"):Component.translatable("profession.villageastra."+r.profession().id());
 }
 static DialogPage greeting(ServerPlayer p,ResidentEntity npc,List<DialogOption> options){
  var e=entry(p,npc);int which=Math.floorMod(npc.getUUID().hashCode()+(int)(p.serverLevel().getDayTime()/24000L),GREETINGS);
  Component line=e==null?Component.translatable("dialog.villageastra.greet.stranger"):Component.translatable("dialog.villageastra.greet."+which,e.settlement().name());
  return new DialogPage(npc.getUUID(),List.of(line),options,null);
 }
 /** Every resident's own answers: trade and gifts with one of a village, and «Как дела?». */
 static List<DialogOption> own(ServerPlayer p,ResidentEntity npc){
  var out=new ArrayList<DialogOption>();
  if(npc.settlementId()!=null)out.add(DialogOption.of(TOPIC+"/trade",Component.translatable("dialog.villageastra.trade")));
  out.add(DialogOption.of(TOPIC+"/news",Component.translatable("dialog.villageastra.news")));
  return out;
 }
 private static void choose(ServerPlayer p,ResidentEntity npc,String action,CompoundTag context){
  if(npc==null)return;
  switch(action){
   case "trade"->org.villageastra.server.ConstructionNetwork.openTrade(p,npc,"");
   case "news"->Dialogs.open(p,new DialogPage(npc.getUUID(),news(p,npc),List.of(DialogOption.of(TOPIC+"/back",Component.translatable("dialog.villageastra.back")),DialogOption.of("close/bye",Component.translatable("dialog.villageastra.bye"))),null));
   case "back"->Dialogs.greet(p,npc);
   default->{}
  }
 }
 /** «Как дела?»: what they are doing, how many live in the village, what it is learning. */
 public static List<Component> news(ServerPlayer p,ResidentEntity npc){
  var out=new ArrayList<Component>();
  if(!npc.workStatus().isEmpty())out.add(Component.translatable("dialog.villageastra.news.work",Component.translatable("work.villageastra."+npc.workStatus())));
  else out.add(Component.translatable("dialog.villageastra.news.idle"));
  var e=entry(p,npc);
  if(e!=null){out.add(Component.translatable("dialog.villageastra.news.village",e.settlement().name(),e.settlement().residents().stream().filter(Resident::alive).count()));
   var civ=e.settlement().civilization();
   out.add(civ.active().isEmpty()?Component.translatable("dialog.villageastra.news.no_research"):Component.translatable("dialog.villageastra.news.research",Component.translatable("research.villageastra."+civ.active())));}
  return out;
 }
}
