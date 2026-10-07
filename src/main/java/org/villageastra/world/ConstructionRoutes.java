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
 /** Choose a reachable work position with shared local and, if needed, expedition searches. */
 public static Path planAny(ResidentEntity worker,java.util.Set<BlockPos> targets){
  if(targets.isEmpty())return null;
  var nearby=worker.routeToAny(targets,64);
  if(nearby!=null&&nearby.canReach())return nearby;
  var bounded=new java.util.LinkedHashSet<BlockPos>();
  for(var target:targets)if(worker.blockPosition().distSqr(target)<=RANGE*RANGE)bounded.add(target);
  if(bounded.isEmpty())return nearby;
  var expanded=worker.routeToAny(bounded,RANGE);
  return expanded!=null&&expanded.canReach()?expanded:nearby;
 }
}
