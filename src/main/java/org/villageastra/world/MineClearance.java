package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import org.villageastra.domain.Settlement;
import org.villageastra.domain.MineDrive;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** Reopen completed stair and gallery cells refilled by falling sand or gravel; never advance the drive for maintenance. */
public final class MineClearance {
 private MineClearance(){}
 private static final String KEY="mineClearance";
 public static boolean active(CompoundTag state){return state.contains(KEY,Tag.TAG_COMPOUND);}
 public static void reconcile(ServerLevel l,CompoundTag state){
  if(!active(state))return;var job=state.getCompound(KEY);var receipt=WorldJournal.recoverExisting(l,job.getUUID("id"));if(receipt==null)return;
  // Append, including during a partly delivered load: old delivery indices and receipts remain unchanged.
  var cargo=state.getList("cargo",Tag.TAG_COMPOUND).copy();cargo.addAll(receipt.getList("loot",Tag.TAG_COMPOUND).copy());state.put("cargo",cargo);
  var tool=ItemStack.of(job.getCompound("tool"));if(!tool.isEmpty()&&tool.isDamageableItem()){
   tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;
  }
  state.put("tool",tool.save(new CompoundTag()));if(!job.getBoolean("gallery"))state.putInt("stairAudit",Math.min(state.getInt("stairAudit"),job.getInt("row")));state.remove(KEY);
 }
 private static boolean safe(ServerLevel l,BlockPos p){
  if(!l.hasChunkAt(p)||l.getBlockEntity(p)!=null)return false;var block=l.getBlockState(p);
  if(!block.is(Blocks.SAND)&&!block.is(Blocks.RED_SAND)&&!block.is(Blocks.GRAVEL))return false;
  for(var direction:Direction.values())if(!l.getFluidState(p.relative(direction)).isEmpty())return false;
  return block.getFluidState().isEmpty();
 }
 private static boolean reachable(ResidentEntity npc,BlockPos p){
  if(p.getY()<npc.getY()&&Math.abs(p.getX()+.5-npc.getX())<.8&&Math.abs(p.getZ()+.5-npc.getZ())<.8)return false;
  var eye=npc.getEyePosition();var center=Vec3.atCenterOf(p);if(eye.distanceToSqr(center)>16)return false;
  var hit=npc.level().clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(p);
 }
 public static boolean tick(ResidentEntity npc,SettlementData.Entry e,Settlement.Building mine,CompoundTag state){
  var l=(ServerLevel)npc.level();
  if(active(state)){
   var job=state.getCompound(KEY);var p=BlockPos.of(job.getLong("pos"));
   if(WorldJournal.exists(l,job.getUUID("id"))){reconcile(l,state);if(active(state)&&!l.getBlockState(p).equals(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),job.getCompound("before"))))state.remove(KEY);l.destroyBlockProgress(npc.getId(),p,-1);return true;}
   var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),job.getCompound("before"));
   if(!l.getBlockState(p).equals(before)||!safe(l,p)||!reachable(npc,p)){l.destroyBlockProgress(npc.getId(),p,-1);state.remove(KEY);return true;}
   npc.getNavigation().stop();var tool=ItemStack.of(job.getCompound("tool"));int labor=job.getInt("labor")+1;job.putInt("labor",labor);
   int ticks=MinerSpeed.breakTicks(before,l,p,tool);MinerSpeed.progress(l,npc,p,before,labor,ticks);
   if(labor>=ticks&&WorldJournal.harvest(l,job.getUUID("id"),p,before,tool)!=null){MinerSpeed.broken(l,npc,p,before);reconcile(l,state);}
   state.putString("status","clearing_mine_access");npc.workStatus("clearing_mine_access");return true;
  }
  if(!Set.of("choose","dig","deliver","stair").contains(state.getString("stage")))return false;
  if(state.getString("stage").equals("dig")&&WorldJournal.exists(l,state.getUUID("operation")))return false;
  var here=BuildingPlacement.local(e,mine,npc.blockPosition());int descent=state.getInt("descent"),end=Math.min(state.getInt("step"),state.contains("floorStep")?state.getInt("floorStep")+1:state.getInt("step"));
  // Claims include walls and the current partly excavated column. Only the drive's
  // completed columns (run) belong to maintenance, never the pending face or its roof.
  if(MineWork.gallery(state)&&state.getInt("run")>0){var area=e.settlement().mineAreas().get(mine.id());int floor=state.getInt("floorStep"),side=state.getInt("side");
   if(area!=null)for(var gallery:area.galleries())if(gallery.step()==floor&&gallery.side()==side){int columns=Math.min(gallery.length(),state.getInt("run"));var shape=MineWork.shape(state);int edge=shape.width()==1?3:side==MineDrive.EAST?4:2;
    for(int y=shape.galleryHeight()-1;y>=0;y--)for(int column=0;column<columns;column++){int x=edge+(side==MineDrive.EAST?column+1:-column-1);var p=BuildingPlacement.at(e,mine,x,-floor-descent+y,7+floor);
     if(!safe(l,p)||!reachable(npc,p))continue;var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putLong("pos",p.asLong());job.putBoolean("gallery",true);job.put("before",NbtUtils.writeBlockState(l.getBlockState(p)));job.put("tool",state.getCompound("tool").copy());state.put(KEY,job);return true;
    }
   }
  }
  if(here.getY()>2-descent||here.getX()<0||here.getX()>6)return false;
  int left=state.getInt("width")==1?3:2,right=state.getInt("width")==1?3:4;
  // Only previously completed rows within physical reach. The current face, walls, roof and unvisited terrain are excluded.
  for(int row=Math.max(0,here.getZ()-10);row<Math.min(end,here.getZ()-3);row++)for(int y=state.getInt("height")-1;y>=0;y--)for(int x=left;x<=right;x++){
   var p=BuildingPlacement.at(e,mine,x,-row-descent+y,7+row);
   if(!safe(l,p)||!reachable(npc,p))continue;
   var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putLong("pos",p.asLong());job.putInt("row",row);job.put("before",NbtUtils.writeBlockState(l.getBlockState(p)));job.put("tool",state.getCompound("tool").copy());state.put(KEY,job);return true;
  }return false;
 }
}
