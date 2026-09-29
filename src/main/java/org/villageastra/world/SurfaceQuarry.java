package org.villageastra.world;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.*;
import org.villageastra.server.*;

/** A blocked or exhausted shaft may send its miner to exposed, dry rock instead. */
public final class SurfaceQuarry {
 private SurfaceQuarry() {}
 public static boolean blocked(CompoundTag work){return work.getString("status").equals("missing_stair_stone")||work.getString("status").equals("fluid_boundary")||work.getString("status").equals("unsafe_ground")||work.getString("status").equals("mine_floor");}
 public static boolean rock(BlockState s){return s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES);}
 public static boolean safe(ServerLevel l,BlockPos p){
  var s=l.getBlockState(p);if(!rock(s)||l.getBlockEntity(p)!=null||OwnershipEvents.protectedBlock(l,p))return false;
  if(!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()||!l.getBlockState(p.above(2)).getCollisionShape(l,p.above(2)).isEmpty())return false;
  if(!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP))return false;
  for(var d:Direction.values())if(!l.getFluidState(p.relative(d)).isEmpty())return false;return true;
 }
 public static int tool(ServerLevel l,SettlementData.Entry e,BlockState rock){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return -1;
  for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(s.is(net.minecraft.tags.ItemTags.PICKAXES)&&s.isCorrectToolForDrops(rock)&&HallReserve.free(l,LogisticsRoutes.position(e,hall),s)>0)return slot;}return -1;
 }
 public static boolean mayStart(ServerLevel l,SettlementData.Entry e,ResidentEntity worker){
  var r=e.settlement().resident(worker.getUUID());var b=e.settlement().workplace(worker.getUUID());
  return r!=null&&r.profession()==Profession.MINER&&b!=null&&worker.getY()>=BuildingPlacement.origin(e,b).getY()&&blocked(MineWork.read(l,b));
 }
}
