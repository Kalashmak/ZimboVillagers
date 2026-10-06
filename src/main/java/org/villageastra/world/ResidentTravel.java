package org.villageastra.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Path;

/** A brisk run along a clear, supported flat route; careful walking near steps and work. */
public final class ResidentTravel {
 private ResidentTravel() {}
 public static double speed(ResidentEntity worker, Path path, double requested) {
  if (path == null || !path.canReach() || !worker.onGround() || worker.isSleeping()
      || worker.isInWaterOrBubble() || worker.isPassenger()
      || path.getNodeCount() - path.getNextNodeIndex() < 6) return requested;
  int y = path.getNodePos(path.getNextNodeIndex()).getY();
  for (int i = path.getNextNodeIndex(); i < Math.min(path.getNodeCount(), path.getNextNodeIndex()+8); i++) {
   var p = path.getNodePos(i);
   if (p.getY()!=y || !worker.level().hasChunkAt(p)
       || !worker.level().getFluidState(p).isEmpty()
       || !worker.level().getBlockState(p).getCollisionShape(worker.level(),p).isEmpty()
       || !worker.level().getBlockState(p.above()).getCollisionShape(worker.level(),p.above()).isEmpty()) return requested;
   var below=p.below();var support=worker.level().getBlockState(below);
   // A dirt path has a broad, stable top 1/16 below the navigation node.
   // Compare the actual walking surface; the body's containing cell is lower on roads.
   if(!support.isFaceSturdy(worker.level(),below,Direction.UP)
       &&!support.is(net.minecraft.world.level.block.Blocks.DIRT_PATH))return requested;
   var shape=support.getCollisionShape(worker.level(),below);
   if(shape.isEmpty()||Math.abs(below.getY()+shape.max(Direction.Axis.Y)-worker.getY())>.125)return requested;
  }
  return requested * 1.35;
 }
}
