package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.*;
/** Prefer a complete dry return; an incomplete route must not send a carrier back into water. */
public final class ResourceReturnRoute {
 private ResourceReturnRoute(){}
 /** The active navigator must retain expedition range and return safety when terrain changes. */
 static final class ReturnPath extends Path {
  ReturnPath(Path source){super(java.util.stream.IntStream.range(0,source.getNodeCount()).mapToObj(source::getNode).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new)),source.getTarget(),source.canReach());}
 }
 private static Path kept(Path path){return path==null?null:new ReturnPath(path);}
 // Deliveries already accept distance squared <= 5; one adjacent node remains inside that reach.
 // The nominal stand can contain a lectern or another work block beneath the hall ceiling.
 public static Path plan(ResidentEntity worker,BlockPos target){
  Path dry=null;
  if(worker.onGround()&&!worker.isInWaterOrBubble()){
   float water=worker.getPathfindingMalus(BlockPathTypes.WATER);
   try{worker.setPathfindingMalus(BlockPathTypes.WATER,-1F);dry=worker.routeTo(target,1,NaturalSupplyGoal.ROUTE_RANGE);}
   finally{worker.setPathfindingMalus(BlockPathTypes.WATER,water);}
   if(dry!=null&&dry.canReach())return kept(dry);
  }
  var ordinary=worker.routeTo(target,1,NaturalSupplyGoal.ROUTE_RANGE);
  if(Boolean.getBoolean("villageastra.firstHouseSmoke")&&worker.tickCount%1200==0)com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE returnPlan pos={} dryReached={} dryEnd={} wetReached={} wetEnd={} wetSafe={}",worker.blockPosition(),dry!=null&&dry.canReach(),dry==null?null:dry.getEndNode(),ordinary!=null&&ordinary.canReach(),ordinary==null?null:ordinary.getEndNode(),ordinary!=null&&ShoreEscapeGoal.clearSwimPath(worker,ordinary));
  if(ordinary!=null&&ordinary.canReach()&&ShoreEscapeGoal.clearSwimPath(worker,ordinary))return kept(ordinary);
  // A dry partial route may reach a ledge where the existing pit recovery can help.
  // In water, wait for the shore recovery rather than walking a known dead end forever.
  return kept(dry);
 }
}
