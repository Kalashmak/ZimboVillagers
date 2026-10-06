package org.villageastra.world;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** An untouched old farm order needs empty storage, not ninety grains before it can grow food. */
public final class FarmStockProject {
 private FarmStockProject(){}
 public static boolean requote(ServerLevel l,SettlementData.Entry e){
  if(e.settlement().governance().playerMayor()!=null||!HallUpgradeGoal.pending(l,e.settlement().id()))return false;
  // Ordinary planner passes need only the small current header. Once migrated,
  // even a large queued farm must not copy all operations and the waiting house.
  var header=HallUpgradeGoal.headerView(l,e.settlement().id());
  if(!BuildingOrders.isBuilding(header)||!header.getString("design").equals("farm")||header.getInt("farmStorageRevision")>=286||header.getBoolean("funded")||header.getInt("index")!=0||header.hasUUID("building")||header.getBoolean("repair")||header.getBoolean("relocate")||header.getBoolean("upgrade"))return false;
  var state=HallUpgradeGoal.inspect(l,e.settlement().id());
  if(!BuildingOrders.isBuilding(state)||!state.getString("design").equals("farm")||state.getBoolean("funded")||state.getInt("index")!=0||state.hasUUID("building")||state.getBoolean("repair")||state.getBoolean("relocate")||state.getBoolean("upgrade"))return false;
  var work=state.getUUID("id");var ops=state.getList("ops",Tag.TAG_COMPOUND);int changed=0;
  for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);if(op.getBoolean("done")||WorldJournal.exists(l,Settlement.childId(work,"block/"+i)))return false;if(op.getCompound("after").getString("Name").equals("minecraft:hay_block")){if(!op.getString("item").equals("minecraft:hay_block"))return false;changed++;}}
  if(changed==0||state.getCompound("cost").getInt("minecraft:hay_block")!=changed)return false;
  var replacement=state.copy();for(var raw:replacement.getList("ops",Tag.TAG_COMPOUND)){var op=(CompoundTag)raw;if(op.getCompound("after").getString("Name").equals("minecraft:hay_block")){op.put("after",NbtUtils.writeBlockState(Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING,net.minecraft.core.Direction.UP)));op.putString("item","minecraft:barrel");}}
  var cost=replacement.getCompound("cost");cost.remove("minecraft:hay_block");cost.putInt("minecraft:barrel",cost.getInt("minecraft:barrel")+changed);replacement.putInt("farmStorageRevision",286);
  // Paid hay remains in its original cargo and is returned through the ordinary
  // completion journal. No receipt, waiting project or withdrawal counter changes.
  return HallUpgradeGoal.reviseUnstarted(l,e.settlement().id(),state,replacement);
 }
}
