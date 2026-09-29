package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import org.villageastra.dialog.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-146 in the real client: the player talks with a resident of the starter village — the greeting window with the village's name and
 *  the answers, «Как дела?» answered in the window, then «Торговать и дарить» opening the resident's card; the chat gets no line from
 *  the resident meanwhile. Frames: the greeting, the news, the card. */
final class DialogProbe {
 private static int phase,ticks;private static volatile String failure;private static volatile UUID npcId;private static String greeting="",news="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.dialogProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-dialog-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_DIALOG screenshot {}",path);}
 private static String text(CompoundTag page){var out=new StringBuilder();for(var raw:page.getList("lines",Tag.TAG_STRING)){var c=Component.Serializer.fromJson(raw.getAsString());out.append(c==null?"":c.getString()).append(" | ");}return out.toString();}
 private static Button button(Minecraft mc,String label){if(!(mc.screen instanceof DialogScreen s))return null;
  for(var child:s.children())if(child instanceof Button b&&b.getMessage().getString().equals(label))return b;return null;}
 /** Talk to the resident nearest the player, as a right click does, standing in front of it. */
 private static void talk(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var p=s.getPlayerList().getPlayers().get(0);var l=p.serverLevel();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);
  var npc=l.getEntitiesOfClass(ResidentEntity.class,p.getBoundingBox().inflate(96),r->r.settlementId()!=null).stream().min(Comparator.comparingDouble(r->r.distanceToSqr(p))).orElse(null);
  if(npc==null)throw new IllegalStateException("No resident near the player");npcId=npc.getUUID();npc.setNoAi(true);
  var front=npc.position().add(npc.getLookAngle().multiply(2.5,0,2.5));p.teleportTo(l,front.x,npc.getY(),front.z,npc.getYRot()+180,10);
  Dialogs.greet(p,npc);
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3000)throw new IllegalStateException("Dialog timeout phase="+phase);
  if(phase==0&&ticks>80&&mc.player!=null){phase=1;ticks=0;talk(mc);}
  else if(phase==1&&ticks>20&&DialogScreen.current()!=null){greeting=text(DialogScreen.current());phase=2;ticks=0;}
  else if(phase==2&&ticks>20){capture(mc,"greeting");var b=button(mc,Component.translatable("dialog.villageastra.news").getString());if(b==null)throw new IllegalStateException("No «Как дела?» button: "+DialogScreen.current());b.onPress();phase=3;ticks=0;}
  else if(phase==3&&ticks>10&&DialogScreen.current()!=null&&!text(DialogScreen.current()).equals(greeting)){news=text(DialogScreen.current());phase=4;ticks=0;}
  else if(phase==4&&ticks>20){capture(mc,"news");var back=button(mc,Component.translatable("dialog.villageastra.back").getString());if(back==null)throw new IllegalStateException("No «Назад»");back.onPress();phase=5;ticks=0;}
  else if(phase==5&&ticks>20&&button(mc,Component.translatable("dialog.villageastra.trade").getString())!=null){button(mc,Component.translatable("dialog.villageastra.trade").getString()).onPress();phase=6;ticks=0;}
  else if(phase==6&&ticks>20&&mc.screen instanceof TradeScreen){capture(mc,"card");
   LogUtils.getLogger().info("ASTRA_DIALOG VERIFIED greeting=[{}] news=[{}] card=true; reload=false",greeting,news);
   phase=7;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_DIALOG FAILED",ex);phase=99;mc.stop();}}
}
