package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
/** Reuse finite returned scaffolds for high natural clearance without changing old operation IDs. */
public final class CanopyAccess {
 private CanopyAccess(){}
 public static boolean append(ServerLevel l,ResidentEntity worker,CompoundTag state,CompoundTag op,double reachSq){
  if(!state.getBoolean("funded")||!BuildingOrders.isBuilding(state)||state.getBoolean("relocate")||op.contains("stand")||op.getBoolean("canopyAccess"))return false;
  var step=HallConstructionPlan.step(op);if(!step.before().is(BlockTags.LEAVES)||!step.after().isAir()||!l.getBlockState(step.pos()).is(BlockTags.LEAVES))return false;
  int stock=0;for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(VillageAstra.TIMBER_SCAFFOLD.get().asItem()))stock+=item.getCount();}
  if(stock==0||!worker.onGround()||op.getLong("canopySurveyAfter")>l.getGameTime())return false;
  op.putLong("canopySurveyAfter",l.getGameTime()+200);
  var origin=BlockPos.of(state.getLong("origin"));var ops=state.getList("ops",Tag.TAG_COMPOUND);var occupied=new HashSet<BlockPos>();
  // A temporary column cannot replace permanent geometry or a different planned column.
  for(var raw:ops){var planned=HallConstructionPlan.step((CompoundTag)raw);if(!planned.after().isAir())occupied.add(planned.pos());}
  var bases=new ArrayList<BlockPos>();
  for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)for(int y=origin.getY()-2;y<=origin.getY()+1;y++){
   var base=step.pos().offset(dx,0,dz).atY(y);var below=base.below();
   if(!l.hasChunkAt(base)||!l.getBlockState(below).isFaceSturdy(l,below,Direction.UP)||!l.getBlockState(base).isAir()||!l.getBlockState(base.above()).isAir())continue;
   bases.add(base);
  }
  bases.sort(Comparator.comparingDouble(p->p.distToCenterSqr(worker.position())+.1*p.distSqr(step.pos())));
  int queries=0;
  for(var base:bases){
   int top=base.getY();while(top<step.pos().getY()&&new net.minecraft.world.phys.Vec3(base.getX()+.5,top+worker.getEyeHeight(),base.getZ()+.5).distanceToSqr(step.pos().getCenter())>reachSq)top++;
   int count=top-base.getY()+1;if(count<2||count>stock||count>8)continue;
   boolean clear=true;for(int y=base.getY();y<=top+1;y++){var p=base.atY(y);if(occupied.contains(p)||org.villageastra.server.OwnershipEvents.disallowedPlacement(l,p,BuildingOrders.buildingId(state))||!l.getBlockState(p).isAir()||l.getBlockEntity(p)!=null){clear=false;break;}}
   if(!clear)continue;if(++queries>4)return false;
   var route=ConstructionRoutes.plan(worker,base);if(route==null||!route.canReach())continue;
   var scaffold=VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();
   for(int y=base.getY();y<=top;y++){
    var add=operation(base.atY(y),Blocks.AIR.defaultBlockState(),scaffold);add.putString("item","villageastra:timber_scaffold");add.putInt("phase",-1);add.putBoolean("barn",true);
    if(y>=base.getY()+2){add.putLong("stand",base.atY(y-2).asLong());add.putInt("standBase",base.getY());}ops.add(add);
   }
   for(int y=top;y>=base.getY();y--){var remove=operation(base.atY(y),scaffold,Blocks.AIR.defaultBlockState());remove.putString("return","villageastra:timber_scaffold");ops.add(remove);}
   op.putLong("stand",base.atY(top).asLong());op.putInt("standBase",base.getY());op.putBoolean("canopyAccess",true);op.putLong("retry",l.getGameTime()+2400);return true;
  }
  return false;
 }
 private static CompoundTag operation(BlockPos pos,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after){var op=new CompoundTag();op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(before));op.put("after",NbtUtils.writeBlockState(after));return op;}
}
