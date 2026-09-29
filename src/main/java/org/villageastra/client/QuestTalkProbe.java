package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import org.villageastra.dialog.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-149 in the real client: a resident of the starter village asks for an errand of their own in the conversation window and the player
 *  takes it there. The resident's need is put on the board with their name on it; a right click opens the greeting, «Есть работа для меня?»
 *  opens what they ask for in their own words, «Берусь» takes it — and the board itself then says the errand is that player's.
 *  Frames: the greeting with the answer, the errand as its asker tells it, the resident's thanks. */
final class QuestTalkProbe {
 private static int phase,ticks;private static volatile String failure;
 private static volatile UUID quest;private static volatile String offer="",state="",taken="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.questTalkProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).getParent().getFileName()+"-questtalk-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_QUESTTALK screenshot {}",path);}
 private static String text(CompoundTag page){var out=new StringBuilder();
  for(var raw:page.getList("lines",Tag.TAG_STRING)){var c=Component.Serializer.fromJson(raw.getAsString());out.append(c==null?"":c.getString()).append(" | ");}
  return out.toString().trim();}
 private static Button button(Minecraft mc,String label){if(!(mc.screen instanceof DialogScreen s))return null;
  for(var child:s.children())if(child instanceof Button b&&b.getMessage().getString().equals(label))return b;return null;}

 /** The nearest resident is given a need of their own, and the player is put in front of them, as a right click leaves them. */
 private static void ask(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var p=server.getPlayerList().getPlayers().get(0);var l=p.serverLevel();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);l.setDayTime(6000);
  var npc=l.getEntitiesOfClass(ResidentEntity.class,p.getBoundingBox().inflate(96),r->r.settlementId()!=null).stream()
   .min(Comparator.comparingDouble(r->r.distanceToSqr(p))).orElse(null);
  if(npc==null)throw new IllegalStateException("No resident near the player");
  var e=SettlementData.get(server).entry(npc.settlementId());if(e==null)throw new IllegalStateException("The resident has no village");
  var r=e.settlement().resident(npc.getUUID());if(r==null)throw new IllegalStateException("The resident is not of their own village");
  long now=SettlementData.get(server).clock().ticks();
  var q=Quests.blank(e.settlement().id(),Quests.SUPPLY,now,now+Quests.deadline(Quests.SUPPLY));
  // Paid the way the board pays for supplies: the goods at the village's price on top of the time the errand takes.
  q.putInt("target",4);q.putString("item","minecraft:bread");
  q.putLong("coins",Quests.coins(Quests.SUPPLY)+Quests.value(net.minecraft.world.item.Items.BREAD,4));
  q.putLong("reputation",Math.max(1,Quests.reputation(Quests.SUPPLY)));
  q.putUUID("giver",r.id());q.putString("giver_name",r.profile().name());
  Quests.store(l,e.settlement().id(),q);quest=q.getUUID("id");
  npc.setNoAi(true);var front=npc.position().add(npc.getLookAngle().multiply(2.5,0,2.5));
  p.teleportTo(l,front.x,npc.getY(),front.z,npc.getYRot()+180,10);
  Dialogs.greet(p,npc);
 }catch(Exception ex){failure=ex.toString();}});}
 /** What the board itself says about the errand once the player has answered in the window. */
 private static void board(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var p=server.getPlayerList().getPlayers().get(0);var l=p.serverLevel();
  for(var e:SettlementData.get(server).entries()){var q=Quests.quest(l,e.settlement().id(),quest);if(q==null)continue;
   state=q.getString("state");taken=q.hasUUID("owner")&&q.getUUID("owner").equals(p.getUUID())?"mine":"other";}
 }catch(Exception ex){failure=ex.toString();}});}

 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>3000)throw new IllegalStateException("Quest talk timeout phase="+phase);
  var askLabel=Component.translatable("dialog.villageastra.quest.ask",1).getString();
  var takeLabel=Component.translatable("dialog.villageastra.quest.take").getString();
  if(phase==0&&ticks>80&&mc.player!=null){phase=1;ticks=0;ask(mc);}
  else if(phase==1&&ticks>20&&DialogScreen.current()!=null){phase=2;ticks=0;}
  else if(phase==2&&ticks>20){capture(mc,"greeting");
   var b=button(mc,askLabel);if(b==null)throw new IllegalStateException("The resident does not ask for work: "+text(DialogScreen.current()));
   b.onPress();phase=3;ticks=0;}
  else if(phase==3&&ticks>20&&button(mc,takeLabel)!=null){offer=text(DialogScreen.current());phase=4;ticks=0;}
  else if(phase==4&&ticks>20){capture(mc,"offer");button(mc,takeLabel).onPress();phase=5;ticks=0;}
  else if(phase==5&&ticks>20&&DialogScreen.current()!=null&&!text(DialogScreen.current()).equals(offer)){phase=6;ticks=0;board(mc);}
  else if(phase==6&&ticks>20){capture(mc,"agreed");
   if(!state.equals(Quests.TAKEN)||!taken.equals("mine"))throw new IllegalStateException("The board did not take it: state="+state+" owner="+taken);
   LogUtils.getLogger().info("ASTRA_QUESTTALK VERIFIED offer=[{}] state={} owner={}",offer,state,taken);
   phase=7;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_QUESTTALK FAILED",ex);phase=99;mc.stop();}}
}
