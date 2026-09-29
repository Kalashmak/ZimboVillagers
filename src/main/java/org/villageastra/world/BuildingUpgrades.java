package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.server.*;
/** AD-052/AD-073: the office view of building levels and the order of the next level. */
public final class BuildingUpgrades {
 private BuildingUpgrades(){}
 /** Every building of the settlement that has levels, with its level and what the next one takes. */
 public static void addView(ServerPlayer p,CompoundTag tag){
  if(!tag.hasUUID("village"))return;
  var e=SettlementData.get(p.server).entry(tag.getUUID("village"));if(e==null)return;
  var l=p.serverLevel();var list=new ListTag();
  var g=e.settlement().governance();
  boolean mayor=g.canManage(p.getUUID(),g.epoch())&&ManagementOrders.allowedContext(p,e);
  var rows=new ArrayList<CompoundTag>();
  for(var b:e.settlement().buildings()){
   if(!BuildingTiers.upgradable(b.type()))continue;
   var row=BuildingTiers.view(l,e,b);row.putUUID("id",b.id());row.putString("type",b.type());rows.add(row);
  }
  // What the mayor can act on comes first: ready to order, then short of materials, then the rest, lowest level first.
  rows.sort(java.util.Comparator.<CompoundTag>comparingInt(r->r.getString("refusal").isEmpty()?r.getInt("lack")>0?1:0:r.getString("refusal").equals("done")?3:2)
    .thenComparingInt(r->r.getInt("kept")).thenComparing(r->r.getString("type")));
  rows.forEach(list::add);
  var upgrades=new CompoundTag();upgrades.put("buildings",list);upgrades.putBoolean("mayor",mayor);
  upgrades.putBoolean("busy",HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.yields(l,e.settlement().id()));
  tag.put("upgrades",upgrades);
 }
 /** Orders the next level of one building; the builders rebuild it from the hall stock. */
 public static String order(ServerPlayer p,UUID village,long epoch,UUID building){
  var e=SettlementData.get(p.server).entry(village);if(e==null)return "village";
  var g=e.settlement().governance();
  if(!g.canManage(p.getUUID(),epoch)||!ManagementOrders.allowedContext(p,e))return "mayor";
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(building)).findFirst().orElse(null);
  if(b==null)return "building";
  return BuildingTiers.order(p.serverLevel(),e,b);
 }
}
