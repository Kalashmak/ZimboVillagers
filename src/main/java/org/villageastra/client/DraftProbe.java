package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Physical desk/lectern approval followed by the paid physical-builder probe. */
final class DraftProbe {
 private static net.minecraft.core.BlockPos origin;private static int phase,ticks;private static java.util.UUID token;private static volatile boolean checked;private static volatile String failure;
 static boolean enabled(){return Boolean.getBoolean("villageastra.draftSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){var e=SettlementData.get(server).entries().iterator().next();var l=server.overworld();origin=e.center();for(var r:e.settlement().residents())((ResidentEntity)l.getEntity(r.id())).setNoAi(true);e.settlement().appointPlayerMayor(server.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(server).setDirty();var cost=HallUpgradeGoal.preview(l,e).getCompound("cost");var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int slot=12;for(var key:cost.getAllKeys()){int left=cost.getInt(key);while(left>0){if(slot>=chest.getContainerSize())throw new IllegalStateException("Fixture capacity");int n=Math.min(64,left);chest.setItem(slot++,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(key)),n));left-=n;}}}
 private static void click(Minecraft mc){
  mc.setScreen(null);boolean approve=ConstructionOverlay.snapshot().getBoolean("draft");
  if(approve)mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
  var pos=origin.offset(approve?2:4,1,4);
  mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.NORTH,pos,false));
  if(approve)mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
 }

 static void tick(Minecraft mc){try{if(failure!=null)throw new IllegalStateException(failure);if(phase==4){ConstructionProbe.tick(mc);return;}if(++ticks>2400)throw new IllegalStateException("Draft timeout phase="+phase);var t=ConstructionOverlay.snapshot();
  if(phase==0&&t.getBoolean("canManage")){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=SettlementData.get(s).entries().iterator().next();s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+3.5,e.center().getY()+1,e.center().getZ()+3.5,0,20);});phase=10;ticks=0;}
  else if(phase==10&&ticks>30){click(mc);phase=1;ticks=0;}
  else if(phase==1&&t.getBoolean("draft")&&t.getBoolean("confirmable")){token=t.getUUID("id");mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var e=SettlementData.get(s).entries().iterator().next();if(HallUpgradeGoal.exists(s.overworld(),e.settlement().id()))throw new IllegalStateException("Preview already created execution work");checked=true;}catch(Exception ex){failure=ex.toString();}});phase=2;ticks=0;}
  else if(phase==2&&checked&&ticks>40){var p=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-draft.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(p);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_DRAFT screenshot {}",p);click(mc);phase=3;ticks=0;}
  else if(phase==3&&t.hasUUID("id")&&!t.getBoolean("draft")){if(!t.getUUID("id").equals(token))throw new IllegalStateException("Approved a different proposal");com.mojang.logging.LogUtils.getLogger().info("ASTRA_DRAFT VERIFIED actual block C2S estimate/approval; no queue before confirmation; identical project ID; physical builder verification follows");phase=4;}
 }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().error("ASTRA_DRAFT FAILED",ex);mc.stop();}}
}
