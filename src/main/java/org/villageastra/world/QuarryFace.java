package org.villageastra.world;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** A short paid working face, never a remote tunnel or an unsafe opening. */
public final class QuarryFace {
 public static final int MAX_OBSTRUCTIONS=3;
 public record Plan(BlockPos target,BlockPos stand){}
 private QuarryFace(){}
 public static boolean resource(net.minecraft.world.level.block.state.BlockState s){return s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)
   ||s.is(net.minecraft.world.level.block.Blocks.ANDESITE)||s.is(net.minecraft.world.level.block.Blocks.GRANITE)||s.is(net.minecraft.world.level.block.Blocks.DIORITE)||s.is(net.minecraft.world.level.block.Blocks.TUFF)
   ||s.is(net.minecraft.world.level.block.Blocks.SANDSTONE)||s.is(net.minecraft.world.level.block.Blocks.RED_SANDSTONE);}
 public static Plan find(ResidentEntity worker,BlockPos ore,Set<BlockPos> reserved){
  var l=(ServerLevel)worker.level();if(reserved.contains(ore)||!resource(l.getBlockState(ore))||!SurfaceQuarry.safe(l,ore))return null;
  var adjacent=new java.util.LinkedHashMap<BlockPos,Plan>();var shelves=new java.util.LinkedHashMap<BlockPos,Plan>();
  for(var offset:HarvestAccess.platformOffsets()){
   var feet=ore.offset(offset);if(!HarvestAccess.standing(l,feet,ore))continue;
   var eye=Vec3.atBottomCenterOf(feet).add(0,worker.getEyeHeight(),0);if(eye.distanceToSqr(Vec3.atCenterOf(ore))>16)continue;
   var hit=l.clip(new ClipContext(eye,Vec3.atCenterOf(ore),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,worker));if(hit.getType()!=HitResult.Type.BLOCK||hit.getBlockPos().equals(ore))continue;
   var rock=hit.getBlockPos();if(reserved.contains(rock)||!l.getBlockState(rock).is(BlockTags.BASE_STONE_OVERWORLD)||!SurfaceQuarry.safe(l,rock)||!HarvestAccess.standing(l,feet,rock)||!HarvestAccess.visible(worker,feet,rock))continue;
   var candidates=offset.getX()*offset.getX()+offset.getZ()*offset.getZ()==1?adjacent:shelves;
   candidates.put(feet.immutable(),new Plan(rock.immutable(),feet.immutable()));
  }
  // Preserve the original near-platform preference: a far shelf can expose a
  // different rock while leaving a narrow, poorly usable approach to the ore.
  for(var candidates:java.util.List.of(adjacent,shelves))while(!candidates.isEmpty()){
   var path=HarvestRouteCache.planAny(worker,candidates.keySet(),NaturalSupplyGoal.ROUTE_RANGE);if(path==null||!path.canReach())break;
   var selected=candidates.remove(path.getTarget());if(selected==null)break;
   if(HarvestAccess.survivesExtraction(path,selected.target())&&HarvestAccess.survivesExtraction(path,ore))return selected;
   // A reachable platform whose supporting route would be excavated must not hide another safe one.
  }return null;
 }
 public static Plan next(ResidentEntity worker,CompoundTag trip,Set<BlockPos> reserved){
  if(!trip.contains("oreTarget"))return null;var ore=BlockPos.of(trip.getLong("oreTarget"));var l=(ServerLevel)worker.level();
  if(reserved.contains(ore)||!resource(l.getBlockState(ore))||!SurfaceQuarry.safe(l,ore))return null;
  var stand=HarvestAccess.find(worker,ore,NaturalSupplyGoal.ROUTE_RANGE);if(stand!=null)return new Plan(ore,stand);
  return trip.getInt("faceDepth")<MAX_OBSTRUCTIONS?find(worker,ore,reserved):null;
 }
}
