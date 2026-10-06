package org.villageastra.world;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** Requote only untouched NPC field covers; actual old timber stays paid cargo. */
public final class FarmFieldTimber {
 private FarmFieldTimber(){}
 public static boolean requote(ServerLevel l,SettlementData.Entry e){
  if(e.settlement().governance().playerMayor()!=null||!HallUpgradeGoal.pending(l,e.settlement().id()))return false;
  var head=HallUpgradeGoal.headerView(l,e.settlement().id());
  if(!BuildingOrders.isBuilding(head)||!head.getString("design").equals("farm")||head.getString("wood").isEmpty()||head.getString("wood").equals("oak")||head.getInt("fieldTimberRevision")>=328||head.getBoolean("funded")||head.getInt("index")!=0||head.hasUUID("building")||head.getBoolean("repair")||head.getBoolean("relocate")||head.getBoolean("upgrade"))return false;
  var original=HallUpgradeGoal.inspect(l,e.settlement().id());var ops=original.getList("ops",Tag.TAG_COMPOUND);var job=original.getUUID("id");
  for(int i=0;i<ops.size();i++)if(ops.getCompound(i).getBoolean("done")||WorldJournal.exists(l,Settlement.childId(job,"block/"+i)))return false;
  var replacement=original.copy();var cost=replacement.getCompound("cost");
  for(var raw:replacement.getList("ops",Tag.TAG_COMPOUND)){
   var op=(CompoundTag)raw;if(!op.getBoolean("field")||!op.getString("item").equals(FarmField.COVER_ITEM))continue;
   var before=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),op.getCompound("after"));if(!before.equals(FarmField.COVER))continue;
   var after=BuildingWood.replace(before,original.getString("wood"));if(after.equals(before))continue;
   String item=BuiltInRegistries.ITEM.getKey(after.getBlock().asItem()).toString();
   int remaining=cost.getInt(FarmField.COVER_ITEM)-1;if(remaining<0)return false;
   if(remaining==0)cost.remove(FarmField.COVER_ITEM);else cost.putInt(FarmField.COVER_ITEM,remaining);
   cost.putInt(item,cost.getInt(item)+1);op.putString("item",item);op.put("after",NbtUtils.writeBlockState(after));
  }
  replacement.putInt("fieldTimberRevision",328);
  return HallUpgradeGoal.reviseUnstarted(l,e.settlement().id(),original,replacement);
 }
}
