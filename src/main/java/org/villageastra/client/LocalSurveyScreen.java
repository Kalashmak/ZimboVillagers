package org.villageastra.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.ArrayList;
import java.util.List;

/** P1 rendering probe: visible surface near the player, not the settlement atlas. */
public final class LocalSurveyScreen extends Screen {
    private record Cell(int x, int y, int z, BlockState state) {}
    private final List<Cell> cells = new ArrayList<>();
    private int rotation;
    private float scale = 16;
    private float panX, panY;
    private boolean fitted;
    public LocalSurveyScreen() {
        super(Component.translatable("screen.villageastra.survey"));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        BlockPos center = mc.player.blockPosition();
        // Exposed surface and facades only. Adjacent surface heights bound the visible vertical shell.
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            BlockPos column = center.offset(x,0,z);
            if (!mc.level.hasChunkAt(column)) continue;
            int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
            if (Math.abs(y - center.getY()) > 24) continue;
            int lowestVisible = y;
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos neighbour = column.relative(direction);
                if (mc.level.hasChunkAt(neighbour)) lowestVisible = Math.min(lowestVisible,
                        mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, neighbour.getX(), neighbour.getZ()));
            }
            for (int visibleY = Math.max(lowestVisible, y - 24); visibleY <= y; visibleY++) {
                BlockPos pos = new BlockPos(column.getX(), visibleY, column.getZ());
                cells.add(new Cell(x,visibleY - center.getY(),z,mc.level.getBlockState(pos)));
            }
        }
    }
    @Override protected void init() {
        if (!fitted) { scale = Math.max(3, Math.min(16, (height - 100) / 32F)); fitted = true; }
        addRenderableWidget(Button.builder(Component.translatable("screen.villageastra.rotate"), b -> rotation = (rotation + 1) % 4)
                .bounds(12, height - 28, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width - 112, height - 28, 100, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0,0,width,height,0xF0101720);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawWordWrap(font, Component.translatable("screen.villageastra.probe"), 12, 28, width-24, 0xE0C080);
        graphics.enableScissor(8,58,width-8,height-36);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2.0 + panX, height / 2.0 + panY, 120);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(30));
        pose.mulPose(Axis.YP.rotationDegrees(45 + rotation * 90));
        for (Cell cell : cells) {
            pose.pushPose(); pose.translate(cell.x, cell.y, cell.z);
            minecraft.getBlockRenderer().renderSingleBlock(cell.state, pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        graphics.flush();
        pose.popPose();
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        scale = Math.max(5, Math.min(32, scale + (float) delta)); return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0) { panX += (float) dx; panY += (float) dy; return true; }
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}
