package org.villageastra.world;
import net.minecraft.world.level.pathfinder.Path;

/** Retain the request when a separate planner lends its path to the active navigator. */
final class ResidentPlannedRoute extends Path {
 final int accuracy,range;
 final float exploration;
 private ResidentPlannedRoute(Path source,int accuracy,int range,float exploration){
  super(java.util.stream.IntStream.range(0,source.getNodeCount()).mapToObj(source::getNode).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)),source.getTarget(),source.canReach());
  this.accuracy=accuracy;this.range=range;this.exploration=exploration;
 }
 static Path keep(Path source,int accuracy,int range,float exploration){return source==null?null:new ResidentPlannedRoute(source,accuracy,range,exploration);}
 Path refresh(ResidentEntity worker){return range==0?worker.routeTo(getTarget(),accuracy):worker.routeTo(getTarget(),accuracy,range,exploration);}
 static boolean samePolicy(Path a,Path b){
  if(a==null||b==null)return a==b;
  if(a.getClass()!=b.getClass()||!a.getTarget().equals(b.getTarget()))return false;
  return !(a instanceof ResidentPlannedRoute first)||b instanceof ResidentPlannedRoute second&&first.accuracy==second.accuracy&&first.range==second.range&&first.exploration==second.exploration;
 }
}
