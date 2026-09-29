package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
/** Route selection shared by a builder's work-position checks and physical travel. */
public final class ConstructionRoutes {
 private static final int RANGE=320;
 private ConstructionRoutes(){}
 public static Path plan(ResidentEntity worker,BlockPos target){
  var ordinary=worker.getNavigation().createPath(target,0);
  if(ordinary!=null&&ordinary.canReach()||worker.blockPosition().distSqr(target)>RANGE*RANGE)return ordinary;
  var expanded=worker.routeTo(target,0,RANGE);
  return expanded!=null&&expanded.canReach()?expanded:ordinary;
 }
}
