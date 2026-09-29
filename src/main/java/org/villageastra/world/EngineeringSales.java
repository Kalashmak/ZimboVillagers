package org.villageastra.world;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-156 (owner's Engineering ladder III): with Engineering III the hall learns the redstone mechanisms (workshops.json town_hall
 *  research_outputs "engineering.3") and the village puts them up for sale: an engineering office keeps SALE_STOCK of each in its chest,
 *  wanted again when it has none left, and its engineer sells them (trade.json engineer "sells"); what the office holds is the village's
 *  surplus for caravans too (a want exists only while the chest is empty, so nothing on the shelf is kept back). */
public final class EngineeringSales {
 private EngineeringSales(){}
 public static final String NODE="engineering.3",OFFICE="engineering";
 public static final int SALE_STOCK=2;
 /** The mechanisms the village makes and sells (the hall's Engineering III list). */
 public static List<Item> goods(){var out=new ArrayList<Item>();for(var id:Workshops.hallCrafts(NODE))if(!id.startsWith("#"))out.add(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));return out;}
 public static Settlement.Building office(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.type().equals(OFFICE)).findFirst().orElse(null);}
 /** The mechanisms the office's shelf lacks (none of the kind in its chest nor on the way), to the office. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e){
  var b=office(e);if(b==null||!ResearchKnobs.done(l,e).contains(NODE))return List.of();var c=LogisticsRoutes.chest(l,e,b);if(c==null)return List.of();var out=new ArrayList<Workshops.Want>();
  for(var item:goods())if(LogisticsRoutes.count(c,s->s.is(item))==0&&PorterWork.reserved(l,e,b.id(),s->s.is(item),true)==0)out.add(new Workshops.Want(Ingredient.of(item),SALE_STOCK,b.id()));
  return out;
 }
}
