package org.villageastra.world;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** Adapt a legacy, untouched NPC order, retaining real surplus for the builder's return. */
public final class LocalBuildingTimber {
 private LocalBuildingTimber(){}
 private static String item(String key,String wood){
  var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));
  if(!(item instanceof BlockItem block))return key;
  var replacement=BuildingWood.replace(block.getBlock().defaultBlockState(),wood).getBlock().asItem();
  return replacement==Items.AIR?key:BuiltInRegistries.ITEM.getKey(replacement).toString();
 }
 public static boolean requote(ServerLevel l,SettlementData.Entry e){
  if(e.settlement().governance().playerMayor()!=null||!HallUpgradeGoal.pending(l,e.settlement().id()))return false;
  var state=HallUpgradeGoal.inspect(l,e.settlement().id());
  if(!BuildingOrders.isBuilding(state)||state.getBoolean("funded")||state.getInt("index")!=0
     ||state.hasUUID("building")||state.getBoolean("repair")||state.getBoolean("relocate")||!state.getString("wood").isEmpty())return false;
  String wood=BuildingWood.choose(l,e,state.getString("design"));if(wood.isEmpty())return false;
  var cost=new CompoundTag();var oldCost=state.getCompound("cost");
  for(var key:oldCost.getAllKeys()){String next=item(key,wood);cost.putInt(next,cost.getInt(next)+oldCost.getInt(key));}
  // Already paid timber keeps its actual species in cargo. Funding counts only
  // the new bill; normal project completion returns any unused paid stacks.
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var work=state.getUUID("id");
  for(int i=0;i<ops.size();i++)if(ops.getCompound(i).getBoolean("done")||WorldJournal.exists(l,Settlement.childId(work,"block/"+i)))return false;
  var replacement=state.copy();var changed=replacement.getList("ops",Tag.TAG_COMPOUND);
  for(var raw:changed){var op=(CompoundTag)raw;var before=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),op.getCompound("after"));op.put("after",NbtUtils.writeBlockState(BuildingWood.replace(before,wood)));if(op.contains("item"))op.putString("item",item(op.getString("item"),wood));}
  replacement.putString("wood",wood);replacement.put("cost",cost);
  // No world, inventory or receipt is changed. The original work id and funding
  // counter survive, so resident-held views and confirmed withdrawals continue.
  return HallUpgradeGoal.reviseUnstarted(l,e.settlement().id(),state,replacement);
 }
}
