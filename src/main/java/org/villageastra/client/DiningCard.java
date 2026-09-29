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
/** AD-139 §7.3: the restaurant's rows of a building card — the hall (seats taken of standing, waiting, or why it is closed), the day's meals
 *  and the leftover (what cooled), the kitchen by level, the couriers and the cart (the dog is planned until the livestock work brings dogs). */
final class DiningCard {
 private DiningCard(){}
 static Component text(String key,Object... args){return Component.translatable("dining.villageastra.card."+key,args);}
 static boolean shown(CompoundTag card){return card.contains("dining");}
 static int render(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office){
  var t=card.getCompound("dining");var tips=OfficeUi.tips();String state=t.getString("state");
  var hall=state.equals("open")?text("hall",t.getInt("taken"),t.getInt("seats"),t.getInt("invited")):text("hall_"+state,t.getInt("seats"));
  OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.SPRUCE_STAIRS),hall,Component.empty(),state.equals("open")?Tone.OK:state.equals("closed_night")?Tone.OFF:Tone.WAIT);
  if(office){var lines=new ArrayList<Component>();lines.add(hall);lines.add(text("feeds",t.getInt("feeds")));if(t.getInt("seats")<t.getInt("designSeats"))lines.add(text("seats_missing",t.getInt("seats"),t.getInt("designSeats")));tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
  y+=22;
  var served=text("served",t.getInt("served"),t.getInt("leftover"),t.getInt("cooled"));OfficeUi.tag(g,f,x,y,served,Tone.OFF,w);if(office)tips.add(new Rect(x,y,w,11),served,text("portions"));y+=14;
  var kitchen=t.getBoolean("automatic")?text("kitchen_auto"):t.getBoolean("greatOven")?text("kitchen_oven"):t.getBoolean("meat")?text("kitchen_meat"):text("kitchen_bread");
  OfficeUi.tag(g,f,x,y,kitchen,Tone.INFO,w);if(office)tips.add(new Rect(x,y,w,11),kitchen,text("dishes",t.getInt("dishes")));y+=14;
  var c=t.getCompound("couriers");int posts=c.getInt("posts");
  if(posts>0){var line=text("couriers",c.getInt("posted"),posts);var cart=c.getString("cart");
   var cartLine=cart.isEmpty()?text("cart_ready"):cart.equals("level")?Component.empty():text("cart_"+cart);
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.BREAD),line,cartLine,c.getInt("posted")<posts?Tone.WAIT:Tone.OK);
   if(office){var lines=new ArrayList<Component>();lines.add(line);for(var raw:c.getList("list",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var st=q.getString("status");
     lines.add(text("courier",q.getString("name"),st.isEmpty()?Component.literal("—"):Component.translatable("work.villageastra."+st),q.getInt("carrying"),q.getInt("targets")));}
    if(!cart.equals("level"))lines.add(cartLine);tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
   y+=22;}
  return y;
 }
}
