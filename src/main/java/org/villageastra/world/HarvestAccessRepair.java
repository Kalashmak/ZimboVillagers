package org.villageastra.world;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** Old quarry trips may replace their missing foothold with one real carried stone block. */
public final class HarvestAccessRepair {
 private HarvestAccessRepair(){}
 public static UUID operation(CompoundTag trip){return Settlement.childId(trip.getUUID("id"),"access_repair");}
 public static ListTag unpaidCargo(ServerLevel l,CompoundTag trip,ListTag cargo){
  if(!trip.hasUUID("id")||!trip.getBoolean("quarry")||trip.getBoolean("accessRepaired"))return cargo;
  var receipt=WorldJournal.recoverExisting(l,operation(trip));if(receipt==null)return cargo;
  var block=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),receipt.getCompound("after"));
  return ResourceWorkGoal.without(cargo,block.getBlock().asItem(),1);
 }
 public static boolean reconcile(ServerLevel l,SettlementData.Entry e,ResidentEntity worker,CompoundTag trip){
  if(!trip.getBoolean("quarry")||trip.getBoolean("accessRepaired")||!trip.hasUUID("id")||WorldJournal.recoverExisting(l,operation(trip))==null)return false;
  var cargo=unpaidCargo(l,trip,trip.getList("cargo",Tag.TAG_COMPOUND));trip.put("cargo",cargo);trip.putBoolean("accessRepaired",true);
  trip.putLongArray("deliveryTargets",MineStairWork.deliveries(l,e,worker.getUUID(),cargo));return true;
 }
 public static boolean restore(ServerLevel l,SettlementData.Entry e,ResidentEntity worker,CompoundTag trip){
  if(!trip.getBoolean("quarry")||!trip.getString("stage").equals("carry")||trip.getInt("delivered")!=0||trip.getBoolean("accessRepaired"))return false;
  if(WorldJournal.exists(l,Settlement.childId(trip.getUUID("id"),"deliver/0")))return false;
  if(reconcile(l,e,worker,trip))return true;
  var at=BlockPos.of(trip.getLong("target"));if(!l.hasChunkAt(at)||worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(at))>16||!l.getBlockState(at).isAir()||WorldJournal.recoverExisting(l,trip.getUUID("id"))==null)return false;
  for(var raw:trip.getList("cargo",Tag.TAG_COMPOUND)){
   var stack=ItemStack.of((CompoundTag)raw);if(stack.isEmpty()||!(stack.getItem() instanceof BlockItem item)||item.getBlock() instanceof FallingBlock)continue;
   var block=item.getBlock().defaultBlockState();if(!block.isCollisionShapeFullBlock(l,at)||!block.getFluidState().isEmpty()||worker.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(at)))continue;
   if(WorldJournal.place(l,operation(trip),at,Blocks.AIR.defaultBlockState(),block))return reconcile(l,e,worker,trip);
   return false;
  }return false;
 }
}
