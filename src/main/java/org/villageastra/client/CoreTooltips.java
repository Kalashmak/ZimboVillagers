package org.villageastra.client;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.CoreCatalog;
import org.villageastra.domain.ResearchCatalog;
/** AD-112 (owner, 2026-09-19): a core or ring is set only after the research of that building's branch for its level. The item's own
 *  tooltip says so in general; with the office's last snapshot of the village this adds, grey, the research each building still waits
 *  for before this core or ring can go in ('нужно исследование: Земледелие III — Ферма'). Client only; nothing is sent. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class CoreTooltips {
 private CoreTooltips(){}
 @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
  var id=BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString();if(!CoreCatalog.isCore(id)&&!CoreCatalog.isRing(id))return;
  event.getToolTip().addAll(lines(ConstructionOverlay.snapshot(),id));
 }
 /** One grey line per building whose next level sets this item while its research is missing; the same research is named once. */
 static List<Component> lines(CompoundTag snapshot,String item){
  var out=new ArrayList<Component>();var seen=new HashSet<String>();
  for(var raw:snapshot.getCompound("upgrades").getList("buildings",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;var c=r.getCompound("core");
   if(!c.getString("item").equals(item))continue;var research=c.getList("research",Tag.TAG_STRING);if(research.isEmpty())continue;var rid=research.getString(0);if(!seen.add(rid+"/"+r.getString("type")))continue;
   var n=ResearchCatalog.NODES.get(rid);if(n==null)continue;
   out.add(Component.translatable("core.villageastra.needs_research",Component.translatable("research.villageastra.branch."+n.branch()),OfficeUi.roman(n.tier()))
    .append(" — ").append(Component.translatable("building.villageastra."+r.getString("type"))).withStyle(z->z.withColor(OfficeUi.Tone.OFF.fg()&0xFFFFFF)));}
  return out;
 }
}
