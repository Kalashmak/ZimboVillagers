package org.villageastra.client;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
import org.villageastra.world.FarmCrops;
/** AD-130 (owner 2026-09-22): the farm card's grid of its 18 fields — three floors side by side, each two columns by three rows as seen from
 *  the street (the row by the farmhouse at the bottom). Every cell shows the seed of the crop its field grows; a left click moves it to the
 *  next opened crop, a right click to the one before; a field not built yet is grey with the level that brings it (its choice is kept for
 *  then), a built one its core or barn does not work yet dimmed. Below, "Sow all:" one button per opened crop sets every field. */
final class FarmFieldGrid {
 private FarmFieldGrid(){}
 static final int CELL=18,GAP=6;
 /** A clickable cell: field 1..18, or 0 with the crop for "sow all". */
 record Hit(Rect rect,int field,String crop){}
 static final List<Hit> HITS=new ArrayList<>();
 private static Component text(String key,Object... args){return Component.translatable("farm.villageastra."+key,args);}
 static ItemStack seed(String crop){var c=FarmCrops.from(crop.isEmpty()?"wheat":crop);return new ItemStack(c.seed);}
 static List<String> open(CompoundTag card){var out=new ArrayList<String>();for(var raw:card.getList("openCrops",Tag.TAG_STRING))out.add(raw.getAsString());if(out.isEmpty())out.add("wheat");return out;}
 /** Height the grid takes on a card of this width (0 when the card has no fields). */
 static boolean shown(CompoundTag card){return card.getList("fields",Tag.TAG_COMPOUND).size()==18;}
 /** Draws the grid and the "sow all" row; returns the y below them. */
 static int render(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office,int mx,int my){
  HITS.clear();if(!shown(card))return y;var fields=card.getList("fields",Tag.TAG_COMPOUND);var tips=OfficeUi.tips();
  int block=2*CELL,total=3*block+2*GAP;int x0=x+Math.max(0,(w-total)/2);
  for(int floor=0;floor<3;floor++){int bx=x0+floor*(block+GAP);
   OfficeUi.label(g,f,text("floor",floor+1),bx,y,block+GAP,OfficeUi.MUTED);}
  y+=10;
  for(int i=0;i<18;i++){var row=fields.getCompound(i);int n=row.getInt("n"),floor=(n-1)/6,k=(n-1)%6,mx_=k%2,mz=k/2;
   int cx=x0+floor*(block+GAP)+mx_*CELL,cy=y+(2-mz)*CELL;var r=new Rect(cx,cy,CELL,CELL);
   boolean built=row.getBoolean("built"),worked=row.getBoolean("worked");boolean hover=r.contains(mx,my);
   g.fill(cx,cy,cx+CELL-1,cy+CELL-1,hover?OfficeUi.ROW_HOVER:OfficeUi.CARD);
   g.renderItem(seed(row.getString("crop")),cx+1,cy+1);
   // Not built: grey veil and the level that lays it; built but not worked: a dimmer veil; a field's own choice: a gold corner.
   if(!built){g.fill(cx,cy,cx+CELL-1,cy+CELL-1,0xB0202A30);g.drawString(f,OfficeUi.roman(row.getInt("level")).getString(),cx+2,cy+CELL-9,OfficeUi.MUTED,false);}
   else if(!worked)g.fill(cx,cy,cx+CELL-1,cy+CELL-1,0x80202A30);
   if(row.getBoolean("own"))g.fill(cx+CELL-4,cy,cx+CELL-1,cy+3,OfficeUi.TITLE);
   HITS.add(new Hit(r,n,""));
   if(office){var lines=new ArrayList<Component>();var crop=Component.translatable("farm.villageastra."+row.getString("crop"));
    lines.add(text("field",n,text("floor_n",floor+1),crop));
    if(built)lines.add(text("field_plots",row.getInt("plots"),row.getInt("sown")));else lines.add(text("field_locked",OfficeUi.roman(row.getInt("level"))));
    if(built&&!worked)lines.add(text("field_idle"));
    if(row.getString("crop").equals("sugar_cane"))lines.add(text("cane_plots"));
    lines.add(text("field_click"));tips.add(r,lines.toArray(Component[]::new));}}
  y+=3*CELL+4;
  // Sow all: the label, then one button per opened crop.
  var label=text("all_fields");int lx=x;OfficeUi.label(g,f,label,lx,y+5,w,OfficeUi.TEXT);int bx=lx+f.width(label)+4;
  for(var crop:open(card)){if(bx+CELL>x+w)break;var r=new Rect(bx,y,CELL,CELL);boolean hover=r.contains(mx,my);
   g.fill(bx,y,bx+CELL-1,y+CELL-1,hover?OfficeUi.ROW_HOVER:OfficeUi.CARD);g.renderItem(seed(crop),bx+1,y+1);HITS.add(new Hit(r,0,crop));
   if(office)tips.add(r,text("all_to",Component.translatable("farm.villageastra."+crop)));bx+=CELL+2;}
  return y+CELL+4;
 }
 /** A click on the grid: the next (or, right, the previous) opened crop for one field, or all fields to one crop. False when missed. */
 static boolean click(CompoundTag snapshot,CompoundTag card,double mx,double my,boolean back){
  if(card==null||!snapshot.hasUUID("village")||!snapshot.getBoolean("canManage"))return false;
  for(var hit:HITS){if(!hit.rect().contains(mx,my))continue;
   String crop=hit.field()==0?hit.crop():step(card,hit.field(),back);if(crop==null)return true;
   ConstructionNetwork.sendFarm(new ConstructionNetwork.FarmOrder(snapshot.getUUID("village"),card.getUUID("id"),snapshot.getLong("epoch"),snapshot.getLong("revision"),hit.field(),crop));return true;}
  return false;
 }
 /** The crop after (or before) a field's crop among the opened ones; null when there is no other. */
 static String step(CompoundTag card,int field,boolean back){
  var open=open(card);var now=card.getList("fields",Tag.TAG_COMPOUND).getCompound(field-1).getString("crop");
  int at=open.indexOf(now);if(open.size()<2&&at>=0)return null;int next=at<0?0:Math.floorMod(at+(back?-1:1),open.size());
  return open.get(next).equals(now)?null:open.get(next);
 }
 /** Probe hook: the screen rectangle of a field's cell (1..18) or of the "sow all" button of a crop. */
 static Rect cell(int field,String crop){for(var hit:HITS)if(hit.field()==field&&(field>0||hit.crop().equals(crop)))return hit.rect();return null;}
}
