package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.*;
import org.villageastra.domain.MineDrive;
import org.villageastra.server.SettlementData;
/** A completed village gallery can split an otherwise unsearchable cave return into reachable legs. */
final class MineReturnWaypoints {
 private MineReturnWaypoints(){}
 static Path plan(ResidentEntity worker,SettlementData.Entry entry){
  if(!worker.onGround()||worker.isInWaterOrBubble())return null;
  var targets=new LinkedHashSet<BlockPos>();
  for(var mine:entry.settlement().buildings()){
   if(!mine.type().equals("mine"))continue;var origin=BuildingPlacement.origin(entry,mine);
   if(worker.getY()>=origin.getY()-2)continue;
   var area=entry.settlement().mineAreas().get(mine.id());if(area==null)continue;
   for(int step=0;step<=area.lastStep();step++)add(worker,targets,BuildingPlacement.at(entry,mine,3,1-step-area.descent(),7+step));
   for(var gallery:area.galleries())for(int run=0;run<gallery.length();run++){
    int x=gallery.side()==MineDrive.EAST?(area.width()==1?4:5)+run:(area.width()==1?2:1)-run;
    add(worker,targets,BuildingPlacement.at(entry,mine,x,-gallery.step()-area.descent(),7+gallery.step()));
   }
  }
  if(targets.isEmpty())return null;float water=worker.getPathfindingMalus(BlockPathTypes.WATER);
  try{
   worker.setPathfindingMalus(BlockPathTypes.WATER,-1F);var path=worker.routeToAny(targets,NaturalSupplyGoal.ROUTE_RANGE);
   return HarvestAccess.reversible(path)?new ResourceReturnRoute.ReturnPath(path):null;
  }finally{worker.setPathfindingMalus(BlockPathTypes.WATER,water);}
 }
 private static void add(ResidentEntity worker,Set<BlockPos> targets,BlockPos p){
  var l=worker.level();double distance=p.distSqr(worker.blockPosition());
  if(distance<=16||distance>NaturalSupplyGoal.ROUTE_RANGE*NaturalSupplyGoal.ROUTE_RANGE||!l.hasChunkAt(p))return;
  var floor=l.getBlockState(p.below());
  if(!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty()||!floor.getFluidState().isEmpty()
    ||!l.getBlockState(p).getCollisionShape(l,p).isEmpty()||!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()
    ||!floor.isFaceSturdy(l,p.below(),Direction.UP)||floor.is(Blocks.MAGMA_BLOCK)||floor.is(Blocks.CAMPFIRE)||floor.is(Blocks.SOUL_CAMPFIRE)||floor.is(Blocks.CACTUS))return;
  targets.add(p.immutable());
 }
}
