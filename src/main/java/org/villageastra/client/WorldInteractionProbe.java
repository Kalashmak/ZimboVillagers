package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Real client interaction packets, including server-opened UI and in-world draft confirmation. */
final class WorldInteractionProbe {
 private static int phase,ticks;private static volatile BlockPos origin;private static volatile String failure;private static java.util.UUID proposal;
 static boolean enabled(){return Boolean.getBoolean("villageastra.worldInteractionSmoke");}
 private static void use(Minecraft mc,BlockPos pos){mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_WORLD_INTERACTION screenshot {}",path);
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>1800)throw new IllegalStateException("Interaction timeout phase="+phase);
  var view=ConstructionOverlay.snapshot();
  if(phase==0){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
   var server=mc.getSingleplayerServer();var e=SettlementData.get(server).entries().iterator().next();var p=server.getPlayerList().getPlayers().get(0);
   e.settlement().appointPlayerMayor(p.getUUID());for(var r:e.settlement().residents())((ResidentEntity)server.overworld().getEntity(r.id())).setNoAi(true);
   origin=e.center();p.teleportTo(server.overworld(),origin.getX()+3.5,origin.getY()+1,origin.getZ()+3.5,0,15);ConstructionNetwork.send(p);
  }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&origin!=null&&ticks>30){use(mc,origin.offset(2,1,4));phase=2;ticks=0;}
  else if(phase==2&&mc.screen instanceof ConstructionScreen&&ticks>30){capture(mc,"block-management");mc.setScreen(null);use(mc,origin.offset(4,1,4));phase=3;ticks=0;}
  else if(phase==3&&view.getBoolean("draft")&&view.getBoolean("confirmable")&&ticks>30){
   if(mc.screen!=null||ConstructionOverlay.geometry().isEmpty())throw new IllegalStateException("Draft must remain in world with geometry");proposal=view.getUUID("id");
   mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();if(HallUpgradeGoal.exists(server.overworld(),view.getUUID("village")))throw new IllegalStateException("Unapproved draft created work");server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),origin.getX()+11,origin.getY()+7,origin.getZ()-9,40,25);}catch(Exception ex){failure=ex.toString();}});phase=4;ticks=0;
  }else if(phase==4&&ticks>40){capture(mc,"world-projection");mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),origin.getX()+3.5,origin.getY()+1,origin.getZ()+3.5,0,15);});phase=5;ticks=0;
  }else if(phase==5&&ticks>30){mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));use(mc,origin.offset(2,1,4));mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));phase=6;ticks=0;
  }else if(phase==6&&view.hasUUID("id")&&!view.getBoolean("draft")){
   if(!proposal.equals(view.getUUID("id")))throw new IllegalStateException("Wrong approved draft");
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_WORLD_INTERACTION VERIFIED block-opened management; real use-item packets; private world projection without screen or queue; separate lectern approval preserved proposal ID");phase=7;
   mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().saveEverything(false,true,true);mc.execute(mc::stop);});
  }
 }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("ASTRA_WORLD_INTERACTION FAILED",ex);mc.stop();}}
}
