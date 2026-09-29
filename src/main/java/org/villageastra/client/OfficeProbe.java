package org.villageastra.client;
import java.util.*;
import java.nio.file.Path;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import org.villageastra.server.*;
import org.villageastra.world.*;
import org.villageastra.domain.*;
final class OfficeProbe {
 private static int phase,ticks,heldIndex;private static boolean done;private static ConstructionNetwork.PauseOrder replay;private static net.minecraft.client.gui.screens.Screen watched;
 static boolean enabled(){return Boolean.getBoolean("villageastra.officeSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){ConstructionProbe.setup(server);var e=SettlementData.get(server).entries().iterator().next();e.settlement().appointPlayerMayor(server.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(server).setDirty();}
 /** The construction tab's pause button, clicked where the player sees it; an inactive button does not take the click. */
 private static boolean click(Minecraft mc){if(!(mc.screen instanceof ConstructionScreen s)||s.section()!=ConstructionScreen.CONSTRUCTION)OfficeProbes.open(mc,ConstructionScreen.CONSTRUCTION);var screen=(ConstructionScreen)mc.screen;screen.tick();return screen.pauseButton().visible&&OfficeProbes.click(screen,screen.pauseButton());}
 private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
 static void tick(Minecraft mc){if(done)return;ticks++;try{
  if(ticks>3600)throw new IllegalStateException("Office probe timeout phase="+phase);var t=ConstructionOverlay.snapshot();if(!t.hasUUID("id"))return;
  boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");if(ticks%200==0)LogUtils.getLogger().info("ASTRA_OFFICE progress phase={} index={} paused={} canManage={}",phase,t.getInt("index"),t.getBoolean("paused"),t.getBoolean("canManage"));
  if(phase==0){require(t.getBoolean("canManage")!=reload,"Wrong persisted office authority");OfficeProbes.open(mc,ConstructionScreen.CONSTRUCTION);phase=1;ticks=0;}
  else if(phase==1&&ticks>40){
   if(reload){require(t.getBoolean("paused"),"Reload lost saved order");heldIndex=t.getInt("index");watched=mc.screen;replay=new ConstructionNetwork.PauseOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision"),false);ConstructionNetwork.sendPause(replay);phase=7;ticks=0;}
   else {require(click(mc),"Mayor pause button disabled");phase=2;ticks=0;}
  }else if(phase==2&&t.getBoolean("paused")){
   mc.getSingleplayerServer().execute(()->{var e=SettlementData.get(mc.getSingleplayerServer()).entries().iterator().next();for(var r:e.settlement().residents())if(r.profession()==Profession.BUILDER)((ResidentEntity)mc.getSingleplayerServer().overworld().getEntity(r.id())).setNoAi(false);});phase=3;ticks=0;
  }else if(phase==3&&ticks>100){require(t.getInt("index")==0,"Paused live worker advanced work");require(click(mc),"Mayor resume button disabled");phase=4;ticks=0;}
  else if(phase==4&&!t.getBoolean("paused")&&t.getInt("index")>0){require(click(mc),"Mayor second pause disabled");phase=5;ticks=0;}
  else if(phase==5&&t.getBoolean("paused")){
   heldIndex=t.getInt("index");watched=mc.screen;replay=new ConstructionNetwork.PauseOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision"),false);
   mc.getSingleplayerServer().execute(()->{var data=SettlementData.get(mc.getSingleplayerServer());data.entries().iterator().next().settlement().appointPlayerMayor(UUID.fromString("294550bc-59cf-4d58-a72f-56c8e4f39b58"));data.setDirty();ConstructionNetwork.send(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));});phase=6;ticks=0;
  }else if(phase==6&&!t.getBoolean("canManage")){require(mc.screen==watched,"Office-loss window was replaced");require(!click(mc),"Revoked window still has an active management button");ConstructionNetwork.sendPause(replay);phase=7;ticks=0;}
  else if(phase==7&&ticks>160){
   require(!t.getBoolean("canManage")&&t.getBoolean("paused")&&t.getInt("index")==heldIndex,"Revoked/visitor packet changed saved work");
   var dest=Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-office-"+(reload?"reload":"revoked")+".png");try(var frame=Screenshot.takeScreenshot(mc.getMainRenderTarget())){frame.writeToFile(dest);}done=true;
   mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var e=SettlementData.get(server).entries().iterator().next();require(e.settlement().governance().paused(replay.project()),"Server did not retain pause");require(!e.settlement().governance().canManage(server.getPlayerList().getPlayers().get(0).getUUID(),replay.epoch()),"Old authority retained");require(HallUpgradeGoal.inspect(server.overworld(),e.settlement().id()).getInt("index")==heldIndex,"Server worker advanced while paused");server.saveEverything(false,true,true);LogUtils.getLogger().info("ASTRA_OFFICE VERIFIED GUI pause/resume, physical builder, revoked and forged packet rejected, office/order saved; index={} reload={}",heldIndex,reload);}catch(Exception ex){LogUtils.getLogger().error("ASTRA_OFFICE FAILED",ex);}mc.execute(mc::stop);});
  }
 }catch(Exception ex){done=true;LogUtils.getLogger().error("ASTRA_OFFICE FAILED",ex);mc.stop();}}
}
