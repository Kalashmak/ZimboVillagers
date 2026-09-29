package org.villageastra.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.server.ResidentMarkerNetwork;
import org.villageastra.world.ResidentEntity;
/** Read-only client projection. A new level/connection or stale snapshot cannot leave another player's quest markers behind. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class ResidentLabels {
 public record Label(String profession,List<ItemStack> needs,boolean quest){}
 private static final Map<UUID,Label> LABELS=new HashMap<>();private static net.minecraft.client.multiplayer.ClientLevel level;private static Object connection;private static long received;
 private ResidentLabels(){}
 @SubscribeEvent public static void receive(ResidentMarkerNetwork.Received event){var mc=Minecraft.getInstance();LABELS.clear();level=mc.level;connection=mc.getConnection();if(level==null||!level.dimension().location().toString().equals(event.data.getString("dimension")))return;received=level.getGameTime();
  for(var raw:event.data.getList("residents",Tag.TAG_COMPOUND)){var row=(net.minecraft.nbt.CompoundTag)raw;var items=new ArrayList<ItemStack>();for(var item:row.getList("items",Tag.TAG_STRING)){var key=ResourceLocation.tryParse(item.getAsString());if(key!=null){var kind=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key);if(kind!=Items.AIR)items.add(new ItemStack(kind));}}LABELS.put(row.getUUID("id"),new Label(row.getString("profession"),List.copyOf(items),row.getBoolean("quest")));}
 }
 @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){LABELS.clear();level=null;connection=null;}
 public static Label label(UUID id){var mc=Minecraft.getInstance();return mc.level==null||mc.level!=level||mc.getConnection()!=connection||level.getGameTime()-received>100?null:LABELS.get(id);}
 public static void render(ResidentEntity npc,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
  var mc=Minecraft.getInstance();var data=label(npc.getUUID());if(data==null||mc.player==null||mc.options.hideGui||npc.isInvisibleTo(mc.player)||npc.distanceToSqr(mc.player)>32*32)return;
  pose.pushPose();pose.translate(0,npc.getBbHeight()+.79,0);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
  pose.pushPose();pose.scale(-.025F,-.025F,.025F);var text=Component.translatable("profession.villageastra."+data.profession());mc.font.drawInBatch(text,-mc.font.width(text)/2F,0,0xBFE4FF,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x66000000,light);pose.popPose();
  // Cycle pages of three genuine item models, with the quest mark in a separate slot.
  int count=Math.min(3,data.needs().size()),page=data.needs().isEmpty()?0:(npc.tickCount/40*3)%data.needs().size();int slots=count+(data.quest()?1:0);
  for(int i=0;i<count;i++){pose.pushPose();pose.translate((i-(slots-1)/2.0)*.46,.43,0);pose.scale(.5F,.5F,.5F);mc.getItemRenderer().renderStatic(data.needs().get((page+i)%data.needs().size()),ItemDisplayContext.FIXED,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,pose,buffers,npc.level(),npc.getId());pose.popPose();}
  if(data.quest()){pose.pushPose();pose.translate((count-(slots-1)/2.0)*.46,.52,0);pose.scale(-.04F,-.04F,.04F);mc.font.drawInBatch("!",-mc.font.width("!")/2F,0,0xFFE16B,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x66000000,light);pose.popPose();}
  pose.popPose();
 }
}
