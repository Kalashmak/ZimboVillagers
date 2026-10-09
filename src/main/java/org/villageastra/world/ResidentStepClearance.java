package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
/** A rising diagonal must leave room for the head across both sides of its corner. */
public final class ResidentStepClearance {
 private ResidentStepClearance(){}
 /** Walk onto cultivated ground at its own level instead of landing a drop on it. */
 public static boolean cropLanding(BlockGetter blocks,Node from,Node to){
  return to==null||to.y>=from.y||!blocks.getBlockState(to.asBlockPos().below()).is(net.minecraft.world.level.block.Blocks.FARMLAND);
 }
 public static boolean diagonalAscent(ResidentEntity resident,BlockGetter blocks,Node from,Node to){
  if(to==null||to.y<=from.y||to.x==from.x||to.z==from.z)return true;
  double half=resident.getBbWidth()/2D,height=resident.getBbHeight();
  var head=new AABB(Math.min(from.x,to.x)+.5-half,from.y+height,Math.min(from.z,to.z)+.5-half,
   Math.max(from.x,to.x)+.5+half,to.y+height,Math.max(from.z,to.z)+.5+half).deflate(.0001);
  for(var p:BlockPos.betweenClosed(BlockPos.containing(head.minX,head.minY,head.minZ),BlockPos.containing(head.maxX,head.maxY,head.maxZ)))
   for(var shape:blocks.getBlockState(p).getCollisionShape(blocks,p).toAabbs())if(head.intersects(shape.move(p)))return false;
  return true;
 }
}
