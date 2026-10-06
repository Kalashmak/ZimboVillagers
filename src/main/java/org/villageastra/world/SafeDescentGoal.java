package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
/** A stalled resident can climb down a dry, clear edge toward a lower work destination. */
public final class SafeDescentGoal extends Goal {
 private final ResidentEntity resident;private Vec3 last;private int checks,still,ticks,phase;private Route route;
 private record Route(BlockPos anchor,BlockPos edge,BlockPos landing){}
 public SafeDescentGoal(ResidentEntity r){resident=r;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
 private static boolean open(ResidentEntity r,BlockPos p){var l=r.level();return l.hasChunkAt(p)&&l.getFluidState(p).isEmpty()&&l.getBlockState(p).getCollisionShape(l,p).isEmpty();}
 private static boolean stand(ResidentEntity r,BlockPos p){var l=r.level();return open(r,p)&&open(r,p.above())&&(l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)||l.getBlockState(p.below()).getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&l.getFluidState(p.below()).isEmpty())&&!l.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK)&&!l.getBlockState(p.below()).is(Blocks.CAMPFIRE)&&!l.getBlockState(p.below()).is(Blocks.SOUL_CAMPFIRE);}
 private static Route find(ResidentEntity r,BlockPos destination){
  if(!r.onGround()||r.isInWaterOrBubble()||r.isPassenger()||r.isSleeping()||r.child()||destination.getY()>r.getY()-2)return null;
  var foot=r.blockPosition();Route best=null;double score=Double.MAX_VALUE;
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
   var anchor=foot.offset(x,0,z);if(!stand(r,anchor))continue;
   for(var d:Direction.Plane.HORIZONTAL){var edge=anchor.relative(d);if(!open(r,edge)||!open(r,edge.above()))continue;
    for(int drop=1;drop<=6;drop++){var below=edge.below(drop);if(!open(r,below))break;
     if(drop<2||!stand(r,below))continue;
     double cost=below.distSqr(destination)+anchor.distSqr(foot);if(cost<score&&HarvestAccess.reversible(r.routeTo(anchor,0))){best=new Route(anchor,edge,below);score=cost;}break;
    }
   }
  }return best;
 }
 /** Read-only safety query, also used by movement tests. */
 public static BlockPos landing(ResidentEntity r,BlockPos destination){var found=find(r,destination);return found==null?null:found.landing();}
 /** A builder with no reachable work stand stops navigation entirely. Its paid
  * site's floor remains the recovery destination even without a navigation path. */
 static BlockPos destination(ResidentEntity r){
  var path=r.getNavigation().getPath();if(path!=null)return path.getTarget();
  if(!r.builder()||!r.workStatus().equals("needs_access")||r.settlementId()==null||!(r.level() instanceof net.minecraft.server.level.ServerLevel l)||!HallUpgradeGoal.exists(l,r.settlementId()))return null;
  var project=HallUpgradeGoal.headerView(l,r.settlementId());
  return BuildingOrders.isBuilding(project)&&project.getBoolean("funded")&&!project.getBoolean("complete")?BlockPos.of(project.getLong("origin")).above():null;
 }
 @Override public boolean canUse(){
  if(++checks%20!=0)return false;var now=resident.position();
  if(last!=null&&now.multiply(1,0,1).distanceToSqr(last.multiply(1,0,1))<.5625)still+=20;else{still=0;last=now;}
  if(still<200)return false;var target=destination(resident);if(target==null)return false;route=find(resident,target);return route!=null;
 }
 @Override public void start(){ticks=0;phase=0;resident.getNavigation().stop();resident.workStatus("escaping_pit");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public boolean canContinueToUse(){return route!=null&&ticks<240&&!resident.isInWaterOrBubble()&&!(resident.onGround()&&resident.position().distanceToSqr(Vec3.atBottomCenterOf(route.landing()))<.16);}
 @Override public void tick(){
  ticks++;var at=resident.position();var anchor=Vec3.atBottomCenterOf(route.anchor());
  if(phase==0){if(at.distanceToSqr(anchor)>.04){if(at.distanceToSqr(anchor)<2.25){resident.getNavigation().stop();resident.getMoveControl().setWantedPosition(anchor.x,anchor.y,anchor.z,.8);}else if(ticks%10==0)resident.getNavigation().moveTo(anchor.x,anchor.y,anchor.z,.8);return;}phase=1;}
  resident.getNavigation().stop();resident.getMoveControl().setWantedPosition(at.x,at.y,at.z,0);
  var edge=Vec3.atBottomCenterOf(route.edge());var delta=edge.subtract(at);double horizontal=delta.x*delta.x+delta.z*delta.z;
  if(phase==1&&horizontal<.04)phase=2;
  resident.setDeltaMovement(delta.x*.18,phase==2?-.18:0,delta.z*.18);resident.fallDistance=0;
 }
 @Override public void stop(){route=null;still=0;resident.getNavigation().stop();resident.setDeltaMovement(Vec3.ZERO);resident.fallDistance=0;}
}
