package org.villageastra.world;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/** Harvest beside the block, on ground that survives extraction, without a one-way drop. */
public final class HarvestAccess {
 private HarvestAccess(){}
 private static final java.util.List<BlockPos> PLATFORMS;
 static{
  var cells=new java.util.ArrayList<BlockPos>();for(var d:Direction.Plane.HORIZONTAL)for(int y=-4;y<=2;y++)cells.add(BlockPos.ZERO.relative(d).above(y));
  var sides=new java.util.ArrayList<BlockPos>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(x*x+z*z>1&&x*x+z*z<=9)sides.add(new BlockPos(x,0,z));
  sides.sort(java.util.Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));for(var side:sides)for(int y=-4;y<=2;y++)cells.add(side.above(y));PLATFORMS=java.util.List.copyOf(cells);
 }
 public static java.util.List<BlockPos> platformOffsets(){return PLATFORMS;}
 public static boolean visible(ResidentEntity worker,BlockPos feet,BlockPos target){return visible(worker,net.minecraft.world.phys.Vec3.atBottomCenterOf(feet).add(0,worker.getEyeHeight(),0),target);}
 public static boolean visible(ResidentEntity worker,BlockPos target){return visible(worker,worker.getEyePosition(),target);}
 /** Small supported adjustment on the same platform, with room around its eye ray. */
 static net.minecraft.world.phys.Vec3 workPoint(ResidentEntity worker,BlockPos feet,BlockPos target){
  var center=net.minecraft.world.phys.Vec3.atBottomCenterOf(feet);var best=center;int clearance=-1;double nearest=Double.POSITIVE_INFINITY;
  for(double x:new double[]{0,-.125,.125})for(double z:new double[]{0,-.125,.125}){
   var aim=center.add(x,0,z);if(!worker.level().noCollision(worker,worker.getBoundingBox().move(aim.subtract(worker.position())))||!visible(worker,aim.add(0,worker.getEyeHeight(),0),target))continue;
   int clear=0;for(var margin:new net.minecraft.world.phys.Vec3[]{new net.minecraft.world.phys.Vec3(-.025,0,0),new net.minecraft.world.phys.Vec3(.025,0,0),new net.minecraft.world.phys.Vec3(0,0,-.025),new net.minecraft.world.phys.Vec3(0,0,.025)})if(visible(worker,aim.add(margin).add(0,worker.getEyeHeight(),0),target))clear++;
   double distance=worker.position().distanceToSqr(aim);if(clear>clearance||clear==clearance&&distance<nearest){clearance=clear;nearest=distance;best=aim;}
  }return best;
 }
 private static boolean visible(ResidentEntity worker,net.minecraft.world.phys.Vec3 eye,BlockPos target){
  var l=worker.level();if(eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16)return false;
  var hit=l.clip(new net.minecraft.world.level.ClipContext(eye,net.minecraft.world.phys.Vec3.atCenterOf(target),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,worker));
  return l.getBlockState(target).getCollisionShape(l,target).isEmpty()||hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(target);
 }
 private static boolean tallPlant(Level l,BlockPos target){var s=l.getBlockState(target);return s.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE)||s.is(net.minecraft.world.level.block.Blocks.CACTUS);}
 public static boolean standing(Level l,BlockPos feet,BlockPos target){
  boolean inReach=net.minecraft.world.phys.Vec3.atBottomCenterOf(feet).add(0,1.5,0).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))<=16;
  if(feet.below().equals(target)||!inReach||!l.hasChunkAt(feet)||l.getBlockState(feet.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK))return false;
  return l.getFluidState(feet).isEmpty()&&l.getFluidState(feet.above()).isEmpty()
   &&l.getBlockState(feet).getCollisionShape(l,feet).isEmpty()
   &&l.getBlockState(feet.above()).getCollisionShape(l,feet.above()).isEmpty()
   &&l.getBlockState(feet.below()).isFaceSturdy(l,feet.below(),Direction.UP);
 }
 public static boolean reversible(Path path){
  if(path==null||!path.canReach())return false;
  for(int i=1;i<path.getNodeCount();i++){
   var from=path.getNode(i-1);var to=path.getNode(i);
   if(Math.abs(to.x-from.x)>1||Math.abs(to.y-from.y)>1||Math.abs(to.z-from.z)>1)return false;
  }
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
  // Keep the original adjacent platforms first, then check nearby dry shelves.
  // Eye reach, ray visibility and a reversible physical route remain mandatory.
  var adjacent=new java.util.ArrayList<BlockPos>();var shelves=new java.util.ArrayList<BlockPos>();
  for(var offset:PLATFORMS){
   var feet=target.offset(offset);
   var l=worker.level();if(!standing(l,feet,target))continue;
   if(visible(worker,feet,target))(offset.getX()*offset.getX()+offset.getZ()*offset.getZ()==1?adjacent:shelves).add(feet.immutable());
  }
  for(var candidates:java.util.List.of(adjacent,shelves))while(!candidates.isEmpty()){
   // Retain the first valid platform when its original individual route works.
   var first=candidates.remove(0);if(survivesExtraction(HarvestRouteCache.plan(worker,first,range),target))return first;
   // Default-range callers keep their original navigation policy.
   if(range<=0)continue;
   if(candidates.isEmpty())break;
   var path=HarvestRouteCache.planAny(worker,new java.util.LinkedHashSet<>(candidates),range);
   if(path==null||!path.canReach())break;var chosen=path.getTarget();if(!candidates.remove(chosen))break;
   if(survivesExtraction(path,target))return chosen;
  }return null;
 }
}
