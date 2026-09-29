package org.villageastra.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/** AD-126: a compact card of the construction in view, top left, out of the way of chat and hotbar: project and stage, progress and time,
 *  the next items, what is missing, what is in the way, a legend and the keys. Rows are dropped by priority when the window is small. */
public final class SchematicHud {
 private SchematicHud(){}
 public static final int MARGIN=6,ICON_ROW=18,TEXT_ROW=11;
 private record Row(int priority,int height,Draw draw){}
 @FunctionalInterface private interface Draw{void at(GuiGraphics g,int x,int y,int w);}
 /** The card's last rectangle, for the probe: x, y, width, height. */
 public static volatile int[] lastRect=new int[4];public static volatile int lastRows;public static volatile int draws;
 public static int width(int screen){return Math.max(150,Math.min(200,(int)(screen*.28)));}
 static void draw(GuiGraphics g,CompoundTag t){
  var mc=Minecraft.getInstance();var font=mc.font;int sw=mc.getWindow().getGuiScaledWidth(),sh=mc.getWindow().getGuiScaledHeight();
  int w=Math.min(width(sw),sw-2*MARGIN);boolean survey=t.getBoolean("survey"),draft=t.getBoolean("draft");
  // The estimate is a draft whatever stage the queue behind it reports.
  String stage=survey?"survey":draft?"draft":t.getString("stage");int accent=switch(stage){case "blocked"->0xFFFFA23A;case "pause"->0xFF9AA6B2;case "draft","survey"->0xFFE9CD92;default->0xFF7FB2FF;};
  var rows=new ArrayList<Row>();
  var icon=new ItemStack(t.getString("design").isEmpty()&&!t.getString("kind").equals("road")&&!survey?Items.BELL:Items.IRON_PICKAXE);
  var chip=Component.translatable(survey?"schematic.villageastra.stage.survey":"office.villageastra.stage."+(stage.isEmpty()?"work":stage));
  rows.add(new Row(0,ICON_ROW,(gg,x,y,cw)->{gg.renderItem(icon,x,y+1);gg.drawString(font,font.substrByWidth(ConstructionOverlay.projectName(t),cw-19).getString(),x+19,y+5,0xFFFFE2A8);}));
  // The stage chip, the share done and the time left on one line, the progress bar under it.
  int index=t.getInt("index"),total=Math.max(1,t.getInt("total"));int pct=Math.round((t.contains("progress")?t.getInt("progress"):index)*100f/total);
  rows.add(new Row(1,survey?TEXT_ROW+2:TEXT_ROW+6,(gg,x,y,cw)->{String right=survey?"":pct+"% "+Component.translatable("schematic.villageastra.minutes",t.getInt("minutes")).getString();int rw=font.width(right);
   int chipW=Math.min(font.width(chip)+5,cw-rw-3);gg.fill(x,y,x+chipW,y+11,(accent&0xFFFFFF)|0x44000000);gg.drawString(font,font.substrByWidth(chip,chipW-5).getString(),x+3,y+2,accent);
   if(!survey){gg.drawString(font,right,x+cw-rw,y+2,0xFFD4E2F0);gg.fill(x,y+13,x+cw,y+16,0xFF2A3342);gg.fill(x,y+13,x+Math.round(cw*Math.min(1f,index/(float)total)),y+16,draft?0xFFE9CD92:0xFF7CE08A);}}));
  if(!survey){
   var next=t.getList("upcoming",Tag.TAG_COMPOUND);
   if(!next.isEmpty())rows.add(new Row(5,ICON_ROW,(gg,x,y,cw)->{var label=Component.translatable("schematic.villageastra.next");gg.drawString(font,label,x,y+5,0xFFB8C4D4);int ix=x+font.width(label)+4;
    for(int i=0;i<Math.min(4,next.size())&&ix+16<=x+cw;i++){var line=next.getCompound(i);var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(line.getString("item"))));gg.renderItem(stack,ix,y+1);gg.renderItemDecorations(font,stack,ix,y+1,String.valueOf(line.getInt("count")));ix+=20;}}));}
  var missing=ConstructionOverlay.missingMaterials(t);
  if(!missing.isEmpty()){rows.add(new Row(3,TEXT_ROW,(gg,x,y,cw)->gg.drawString(font,Component.translatable("schematic.villageastra.missing"),x,y+1,0xFFFF8A8A)));
   int shown=Math.min(4,missing.size());
   for(int i=0;i<shown;i++){var m=missing.get(i);int p=4+i;rows.add(new Row(p,TEXT_ROW,(gg,x,y,cw)->{var item=BuiltInRegistries.ITEM.get(new ResourceLocation(m.getString("item")));String n="-"+m.getInt("missing");int nw=font.width(n);
    gg.pose().pushPose();gg.pose().translate(x,y,0);gg.pose().scale(.625f,.625f,1);gg.renderItem(new ItemStack(item),0,0);gg.pose().popPose();
    gg.drawString(font,font.substrByWidth(item.getDescription(),cw-nw-18).getString(),x+13,y+1,0xFFD4E2F0);gg.drawString(font,n,x+cw-nw,y+1,0xFFFF6B6B);}));}
   if(missing.size()>shown){int more=missing.size()-shown;rows.add(new Row(8,TEXT_ROW,(gg,x,y,cw)->gg.drawString(font,Component.translatable("schematic.villageastra.more",more),x+13,y+1,0xFF9AA6B2)));}}
  int conflicts=t.getInt("conflicts"),hidden=t.getInt("hidden");
  if(conflicts>0)rows.add(new Row(2,TEXT_ROW,(gg,x,y,cw)->{gg.fill(x,y+2,x+7,y+9,0xFFFFA23A);gg.drawString(font,Component.translatable("schematic.villageastra.conflicts",conflicts),x+10,y+1,0xFFFFB45A);}));
  if(hidden>0)rows.add(new Row(9,TEXT_ROW,(gg,x,y,cw)->gg.drawString(font,Component.translatable("schematic.villageastra.out_of_view",hidden),x,y+1,0xFF8A96A6)));
  rows.add(new Row(10,TEXT_ROW,(gg,x,y,cw)->{int lx=x;int[] colors={0xFF7FB2FF,0xFFFF4A5A,0xFFFFA23A,0xFFE8C97A};String[] keys={"place","remove","conflict","scaffold"};
   for(int i=0;i<4;i++){var label=Component.translatable("schematic.villageastra.legend."+keys[i]).getString();int lw=font.width(label);if(lx+9+lw>x+cw)break;gg.fill(lx,y+3,lx+6,y+9,colors[i]);gg.drawString(font,label,lx+8,y+2,0xFFB8C4D4);lx+=lw+14;}}));
  rows.add(new Row(11,TEXT_ROW,(gg,x,y,cw)->{var hint=Component.translatable("schematic.villageastra.hint",SchematicKeys.TOGGLE.getTranslatedKeyMessage(),SchematicKeys.LAYERS.getTranslatedKeyMessage(),SchematicKeys.modeName());
   gg.drawString(font,font.substrByWidth(hint,cw).getString(),x,y+1,0xFF8A96A6);}));
  // At most 45% of the screen: the least important rows go first.
  int max=(int)(sh*.45)-8;var kept=new ArrayList<>(rows);
  while(kept.size()>1&&kept.stream().mapToInt(Row::height).sum()>max){kept.remove(kept.stream().max(Comparator.comparingInt(Row::priority)).orElseThrow());}
  int h=kept.stream().mapToInt(Row::height).sum()+8;int x=MARGIN,y=MARGIN;
  g.fill(x,y,x+w,y+h,0xC8101827);g.fill(x,y,x+2,y+h,accent);
  int cy=y+4;for(var r:kept){r.draw.at(g,x+6,cy,w-10);cy+=r.height;}
  lastRect=new int[]{x,y,w,h};lastRows=kept.size();draws++;
 }
}
