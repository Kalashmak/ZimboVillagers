package org.villageastra.client;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.Rect;
import org.villageastra.client.OfficeUi.Tone;
/** AD-131 §5: the forester hut's rows of a building card — how far he fells and how many trees stand in reach, today's felling (and planting
 *  from II, with the bare feet waiting), the mix of kinds he planted and what the hut asks for (III), the sawmill (IV) and the courtyard grove (VI). */
final class ForestCard {
 private ForestCard(){}
 private static Component text(String key,Object... args){return BuildingCard.text(key,args);}
 static boolean shown(CompoundTag card){return card.contains("forest");}
 private static Component name(String item){return OfficeUi.icon(item).getHoverName();}
 static int render(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office){
  var t=card.getCompound("forest");var tips=OfficeUi.tips();int lv=t.getInt("level");
  var reach=t.getInt("inReach")<0?text("forest_reach_searching",t.getInt("radius")):text("forest_reach",t.getInt("radius"),t.getInt("inReach"));
  var felled=lv>=2?text("forest_today_planted",t.getInt("felled"),t.getInt("planted"),t.getInt("bare")):text("forest_today",t.getInt("felled"));
  OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.IRON_AXE),reach,Component.empty(),t.getInt("inReach")==0?Tone.WAIT:Tone.INFO);
  if(office){var lines=new ArrayList<Component>();lines.add(reach);lines.add(felled);if(!t.getString("status").isEmpty())lines.add(Component.translatable("work.villageastra."+t.getString("status")));tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
  y+=22;OfficeUi.tag(g,f,x,y,felled,t.getInt("bare")>0?Tone.WAIT:Tone.OFF,w);if(office)tips.add(new Rect(x,y,w,11),felled);y+=14;
  if(t.contains("mix")){var mix=Component.empty();int n=0;for(var raw:t.getList("mix",Tag.TAG_COMPOUND)){var m=(CompoundTag)raw;if(n++>0)mix.append(", ");mix.append(name(m.getString("item"))).append(" ×"+m.getInt("count"));}
   var line=text("forest_mix",n==0?text("forest_mix_none"):mix);OfficeUi.tag(g,f,x,y,line,Tone.OFF,w);if(office)tips.add(new Rect(x,y,w,11),line);y+=14;
   var asks=t.getList("asks",Tag.TAG_COMPOUND);
   if(!asks.isEmpty()){var ask=Component.empty();int k=0;for(var raw:asks){var a=(CompoundTag)raw;if(k++>0)ask.append(", ");ask.append(name(a.getString("item"))).append(" ×"+a.getInt("count"));if(a.getBoolean("quest"))ask.append(" ").append(text("forest_quest"));}
    var line2=text("forest_asks",ask);var icon=OfficeUi.icon(asks.getCompound(0).getString("item"));
    OfficeUi.chip(g,f,x,y+2,w,icon,line2,Component.empty(),Tone.WAIT);if(office)tips.add(new Rect(x,y+2,w,18),line2);y+=22;}}
  if(t.contains("sawOn")){var saw=text("forest_saw",t.getInt("planksPerLog"),t.getInt("sawn"));var state=t.getBoolean("sawOn")?text("forest_saw_on"):text("forest_saw_off");
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.STONECUTTER),saw,state,t.getBoolean("sawOn")?Tone.OK:Tone.OFF);
   if(office){var lines=new ArrayList<Component>();lines.add(saw);lines.add(state);if(!t.getString("sawStatus").isEmpty())lines.add(Component.translatable("work.villageastra."+t.getString("sawStatus")));tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
   y+=22;}
  if(t.contains("grove")){var gr=t.getCompound("grove");long next=gr.getLong("nextSeconds");
   var line=text("forest_grove",gr.getInt("growing"),gr.getInt("grown"),next<0?"—":String.valueOf(next),gr.getInt("logsToday"));
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.OAK_SAPLING),line,Component.empty(),gr.getString("groveStatus").isEmpty()?Tone.OK:Tone.WAIT);
   if(office){var lines=new ArrayList<Component>();lines.add(line);lines.add(text("forest_grove_total",gr.getInt("trees"),gr.getInt("logs")));if(!gr.getString("groveStatus").isEmpty())lines.add(Component.translatable("work.villageastra."+gr.getString("groveStatus")));tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
   y+=22;}
  return y;
 }
 static boolean saw(CompoundTag card){return card.contains("forest")&&card.getCompound("forest").contains("sawOn");}
 static boolean speciesChoice(CompoundTag card){return card.contains("forest")&&card.getCompound("forest").getInt("kinds")>1;}
}
