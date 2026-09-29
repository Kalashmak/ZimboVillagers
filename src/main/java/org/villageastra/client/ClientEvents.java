package org.villageastra.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.ResidentEntity;

@Mod.EventBusSubscriber(modid = VillageAstra.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VillageAstra.OWNED_CHEST_ENTITY.get(),net.minecraft.client.renderer.blockentity.ChestRenderer::new);
        event.registerEntityRenderer(VillageAstra.RESIDENT.get(), ResidentRenderer::new);
        event.registerEntityRenderer(VillageAstra.CART.get(), CartRenderer::new);
        // AD-143: the seat on a chair is invisible — only its sitter shows.
        event.registerEntityRenderer(VillageAstra.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
    /** ROAD-008: the cart is what it is made of — a low bed of planks with the hold standing on it. */
    private static final class CartRenderer extends net.minecraft.client.renderer.entity.EntityRenderer<org.villageastra.world.CartEntity> {
        private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;
        CartRenderer(EntityRendererProvider.Context context){super(context);blocks=context.getBlockRenderDispatcher();}
        @Override public ResourceLocation getTextureLocation(org.villageastra.world.CartEntity cart){return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;}
        @Override public void render(org.villageastra.world.CartEntity cart,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
            pose.pushPose();
            pose.translate(-.5,0,-.5);pose.scale(1F,.25F,1F);
            blocks.renderSingleBlock(net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
            pose.pushPose();
            pose.translate(-.4,.25,-.4);pose.scale(.8F,.8F,.8F);
            blocks.renderSingleBlock(net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(),pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
            pose.popPose();
            super.render(cart,yaw,partial,pose,buffers,light);
        }
    }
    private static final class ResidentRenderer extends HumanoidMobRenderer<ResidentEntity, HumanoidModel<ResidentEntity>> {
        private static final java.util.List<ResourceLocation> TEXTURES=org.villageastra.domain.ResidentProfile.SKINS.stream()
                .map(name->new ResourceLocation("minecraft","textures/entity/player/wide/"+name+".png")).toList();
        ResidentRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
            addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
            addLayer(new net.minecraft.client.renderer.entity.layers.ItemInHandLayer<ResidentEntity,HumanoidModel<ResidentEntity>>(this,context.getItemInHandRenderer()){
                @Override public void render(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light,ResidentEntity resident,float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch){
                    pose.pushPose();
                    renderArmWithItem(resident,resident.displayedWorkItem(),net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,net.minecraft.world.entity.HumanoidArm.RIGHT,pose,buffers,light);
                    pose.popPose();
                }
            });
        }
        @Override public void render(ResidentEntity entity,float yaw,float partial,com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.MultiBufferSource buffers,int light){
            super.render(entity,yaw,partial,pose,buffers,light);ResidentLabels.render(entity,pose,buffers,light);
        }
        @Override public ResourceLocation getTextureLocation(ResidentEntity entity) { return TEXTURES.get(entity.skinVariant()); }
        /** Children are drawn smaller; their registry life state is mirrored in the synced entity flag. */
        @Override protected void scale(ResidentEntity entity,com.mojang.blaze3d.vertex.PoseStack pose,float partialTick) { if(entity.child())pose.scale(0.6F,0.6F,0.6F); }
    }
}
