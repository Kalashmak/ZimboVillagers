package org.villageastra.world;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Harvest beside the block, on ground that survives extraction, without a one-way drop. */
public final class HarvestAccess {
 private HarvestAccess(){}
 private static boolean tallPlant(Level l,BlockPos target){var s=l.getBlockState(target);return s.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)||s.is(net.minecraft.world.level.block.Blocks.CACTUS);}
 public static boolean standing(Level l,BlockPos feet,BlockPos target){
  boolean inReach=feet.distSqr(target.above())<=4||tallPlant(l,target)&&net.minecraft.world.phys.Vec3.atBottomCenterOf(feet).add(0,1.5,0).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))<=4;
  if(feet.below().equals(target)||!inReach||!l.hasChunkAt(feet)||l.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK))return false;
  return l.getFluidState(feet).isEmpty()&&l.getFluidState(feet.above()).isEmpty()
   &&l.getBlockState(feet).getCollisionShape(l,feet).isEmpty()
   &&l.getBlockState(feet.above()).getCollisionShape(l,feet.above()).isEmpty()
   &&l.getBlockState(feet.below()).isFaceSturdy(l,feet.below(),Direction.UP);
 }
 public static boolean reversible(Path path){
  if(path==null||!path.canReach())return false;
  for(int i=1;i<path.getNodeCount();i++)if(Math.abs(path.getNode(i).y-path.getNode(i-1).y)>1)return false;
  return true;
 }
 public static boolean survivesExtraction(Path path,BlockPos target){
  if(!reversible(path))return false;
  for(int i=0;i<path.getNodeCount();i++)if(path.getNode(i).asBlockPos().below().equals(target))return false;
  return true;
 }
 public static BlockPos find(ResidentEntity worker,BlockPos target){
  return find(worker,target,0);
 }
 public static BlockPos find(ResidentEntity worker,BlockPos target,int range){
  for(var d:Direction.Plane.HORIZONTAL)for(int y=tallPlant(worker.level(),target)?-2:0;y<=2;y++){
   var feet=target.relative(d).above(y);
   if(standing(worker.level(),feet,target)&&survivesExtraction(range>0?worker.routeTo(feet,0,range):worker.routeTo(feet,0),target))return feet;
  }return null;
 }
}
