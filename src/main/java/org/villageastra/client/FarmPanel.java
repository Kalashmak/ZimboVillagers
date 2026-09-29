package org.villageastra.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.*;
import net.minecraft.core.BlockPos;
import org.villageastra.world.FarmCrops;
import org.villageastra.server.ConstructionNetwork;
final class FarmPanel {
 private final Button building,crop;private int selected;
 FarmPanel(int height,java.util.function.Consumer<Button> add){building=Button.builder(text("next_farm"),b->selected++).bounds(22,height-54,150,20).build();crop=Button.builder(text("next_crop"),b->{var t=ConstructionOverlay.snapshot();var rows=t.getList("farms",Tag.TAG_COMPOUND);if(rows.isEmpty()||!t.getBoolean("canManage"))return;var row=rows.getCompound(selected%rows.size());var next=FarmCrops.values()[(FarmCrops.from(row.getString("crop")).ordinal()+1)%FarmCrops.values().length];ConstructionNetwork.sendFarm(new ConstructionNetwork.FarmOrder(t.getUUID("village"),row.getUUID("building"),t.getLong("epoch"),t.getLong("revision"),next.id()));}).bounds(180,height-54,150,20).build();add.accept(building);add.accept(crop);}
 private static Component text(String key,Object... args){return Component.translatable("farm.villageastra."+key,args);}
 void tick(boolean visible){var t=ConstructionOverlay.snapshot();int size=t.getList("farms",Tag.TAG_COMPOUND).size();selected=size==0?0:selected%size;building.visible=crop.visible=visible;building.active=size>1;crop.active=size>0&&t.getBoolean("canManage");}
 void render(GuiGraphics g,Font f,int width){var t=ConstructionOverlay.snapshot();var rows=t.getList("farms",Tag.TAG_COMPOUND);if(rows.isEmpty()){g.drawString(f,text("empty"),22,86,0xFFFFFF);return;}var row=rows.getCompound(selected%rows.size());var p=BlockPos.of(row.getLong("pos"));g.drawString(f,text("selected",selected+1,rows.size(),p.getX(),p.getY(),p.getZ()),22,86,0xFFFFFF);g.drawString(f,text("crop",text(row.getString("crop"))),22,108,0xFFD18A);g.drawWordWrap(f,text("rules"),22,132,width-44,0xC8DAEC);}
}
