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
/** AD-147 §6.4: the warehouse's rows of a building card - the store (slots used of all, its pages), the couriers (posted of posts, each
 *  one's status and load in the tooltip; none at VI while wolves carry), the carts (have of needed, ordered, or why none comes), the wolves
 *  (planned until the livestock work brings them) and the sorting of VI. */
final class WarehouseCard {
 private WarehouseCard(){}
 static Component text(String key,Object... args){return Component.translatable("warehouse.villageastra.card."+key,args);}
 static boolean shown(CompoundTag card){return card.contains("warehouse");}
 static int render(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office){
  var t=card.getCompound("warehouse");var tips=OfficeUi.tips();int level=t.getInt("level");
  // The store: slots used of all; amber while a page of the level is not linked yet (a chest missing, or a player's chest in its cell).
  int slots=t.getInt("slots"),used=t.getInt("used");var store=text("store",used,slots,t.getInt("pages"));
  boolean short_=slots<t.getInt("levelSlots");
  OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.CHEST),store,short_?text("store_short",t.getInt("levelSlots")):Component.empty(),short_||slots>0&&used*10>=slots*9?Tone.WAIT:Tone.OK);
  if(office)tips.add(new Rect(x,y+2,w,18),store,text("store_one"),short_?text("store_short",t.getInt("levelSlots")):Component.empty());
  y+=22;
  // The couriers.
  int posts=t.getInt("posts"),posted=t.getInt("posted");
  var couriers=t.getBoolean("relieved")?text("couriers_relieved",t.getInt("wolfTrips")):text("couriers",posted,posts);
  var load=t.getInt("stacks")>0?text("load_cart",t.getInt("load"),t.getInt("stacks")):text("load_hand",t.getInt("load"));
  OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.BARREL),couriers,load,posted<posts?Tone.WAIT:Tone.OK);
  if(office){var lines=new ArrayList<Component>();lines.add(couriers);lines.add(load);lines.add(text("by_need"));
   for(var raw:t.getList("list",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var st=q.getString("status");
    lines.add(text(q.getBoolean("cart")?"courier_cart":"courier",q.getString("name"),st.isEmpty()?Component.literal("—"):Component.translatable("work.villageastra."+st),q.getInt("carrying")));}
   tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
  y+=22;
  // The carts (from II).
  String carts=t.getString("carts");
  if(!carts.equals("level")){var line=carts.equals("ok")?text("carts_ok",t.getInt("cartsHave"),t.getInt("cartsNeeded")):text("carts_"+carts,t.getInt("cartsHave"),t.getInt("cartsNeeded"));
   OfficeUi.tag(g,f,x,y,line,carts.equals("ok")?Tone.OK:carts.equals("no_maker")?Tone.BAD:Tone.WAIT,w);if(office)tips.add(new Rect(x,y,w,11),line,text("carts_rule"));y+=14;}
  // The wolves (from V): planned until the village has wolves.
  if(level>=5){String wolves=t.getString("wolves");var line=wolves.isEmpty()?text("wolves_ok",t.getInt("wolfTrips")):text("wolves_"+wolves);
   OfficeUi.tag(g,f,x,y,line,wolves.isEmpty()?Tone.OK:wolves.equals("planned")?Tone.OFF:Tone.WAIT,w);if(office)tips.add(new Rect(x,y,w,11),line,text(level>=6?"wolves_rule_6":"wolves_rule_5"));y+=14;}
  // The sorting (VI).
  if(t.getBoolean("sorting")){var line=text("sorting",t.getInt("categories"),t.getInt("unsorted"));OfficeUi.tag(g,f,x,y,line,Tone.INFO,w);if(office)tips.add(new Rect(x,y,w,11),line,text("sorting_pages"));y+=14;}
  return y;
 }
}
