package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Ground beside a low crown, within the forester's existing four-block reach. */
public final class TreeAccess {
 private TreeAccess(){}
 public static BlockPos find(ResidentEntity worker,BlockPos foot,List<BlockPos> trunk){
  var l=worker.level();var choices=new ArrayList<BlockPos>();
  for(var p:BlockPos.betweenClosed(foot.offset(-3,-1,-3),foot.offset(3,1,3))){
   if(trunk.contains(p)||!l.hasChunkAt(p)||!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty())continue;
   if(!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)||l.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK))continue;
   var feet=Vec3.atBottomCenterOf(p);if(feet.add(0,worker.getEyeHeight(),0).distanceToSqr(Vec3.atCenterOf(foot))>16)continue;
   if(!l.noCollision(worker,worker.getBoundingBox().move(feet.subtract(worker.position()))))continue;
   choices.add(p.immutable());
  }
  choices.sort(Comparator.comparingDouble(p->worker.distanceToSqr(Vec3.atBottomCenterOf(p))));
  for(var p:choices){var path=worker.routeTo(p,0);if(path!=null&&path.canReach()&&path.getEndNode()!=null&&path.getEndNode().asBlockPos().equals(p))return p;}
  return null;
 }
}
