package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
/** A resident stuck in a small dry pit can reach its wall and climb to a safe ledge, without changing terrain. */
public final class PitEscapeGoal extends Goal {
 private final ResidentEntity resident;private BlockPos exit,anchor;private Vec3 last;private int still,checks,escapeTicks,escapeLimit;
 private boolean atAnchor;
 private record Route(BlockPos anchor,BlockPos exit){}
 public PitEscapeGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
 /** Only ankle/waist-deep water on a solid floor with an open dry column above, never a submerged ascent. */
 private static boolean shallow(ResidentEntity r){
  var l=r.level();var p=r.blockPosition();for(int down=0;down<2&&l.getBlockState(p.below()).getCollisionShape(l,p.below()).isEmpty();down++)p=p.below();
  return r.getY()-p.getY()<1.5&&l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)&&l.getFluidState(p.above()).isEmpty()&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)&&!r.isEyeInFluid(net.minecraft.tags.FluidTags.WATER);
 }
 public static BlockPos escape(ResidentEntity r){var route=route(r);return route==null?null:route.exit();}
 /** Finished architectural floors use their doors, stairs and controlled descent.
  * They are not natural pits: climbing their walls strands residents on the roof. */
 private static boolean buildingFloor(ResidentEntity r){
  if(r.settlementId()==null||!(r.level() instanceof net.minecraft.server.level.ServerLevel l))return false;
  var e=org.villageastra.server.SettlementData.get(l.getServer()).entry(r.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;
  var foot=r.blockPosition();for(var b:e.settlement().buildings()){
   if(b.type().equals("mine"))continue;var origin=BuildingPlacement.origin(e,b);var size=BuildingPlacement.size(b.type(),b.rotation());
   if(foot.getY()>origin.getY()&&foot.getX()>=origin.getX()&&foot.getX()<origin.getX()+size[0]&&foot.getZ()>=origin.getZ()&&foot.getZ()<origin.getZ()+size[1])return true;
  }return false;
 }
 private static Route route(ResidentEntity r){
  if(r.isInWaterOrBubble()&&!shallow(r)||r.isPassenger()||r.isSleeping()||buildingFloor(r))return null;var l=r.level();var foot=r.blockPosition();
  // Goal checks can always coincide with the airborne phase of futile repeated jumps.
  // Recognise the same dry floor under a normal jump, never a fall from a high cliff.
  if(!r.onGround()){
   for(int down=0;down<2&&l.getBlockState(foot.below()).getCollisionShape(l,foot.below()).isEmpty();down++)foot=foot.below();
   if(r.getY()-foot.getY()>1.5||!l.getBlockState(foot.below()).isFaceSturdy(l,foot.below(),Direction.UP)||!l.getFluidState(foot).isEmpty()&&!shallow(r))return null;
  }
  // An open corridor is navigation's job. A bounded flood fill distinguishes it from a
  // 2x2/3x3 pit, where an open neighboring cell used to prevent escape forever.
  var nav=r.getNavigation();var target=nav.getTargetPos();var path=nav.getPath();
  // An intermediate floor on a controlled descent is not a pit to climb out of.
  // Prefer its next safe downward leg; otherwise this goal undoes the descent.
  var lower=SafeDescentGoal.destination(r);if(lower!=null&&SafeDescentGoal.landing(r,lower)!=null)return null;
  boolean expedition=path instanceof ResourceReturnRoute.ReturnPath&&!path.canReach();int radius=expedition?24:4;
  boolean open=false;var queue=new ArrayDeque<BlockPos>();var floor=new LinkedHashSet<BlockPos>();queue.add(foot);floor.add(foot);
  while(!queue.isEmpty()&&floor.size()<2048){var at=queue.removeFirst();for(var d:Direction.Plane.HORIZONTAL)for(int dy=expedition?-1:0;dy<=(expedition?1:0);dy++){var p=at.relative(d).above(dy);
   if(!l.hasChunkAt(p)||!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty())continue;
   if(!l.getBlockState(p).getCollisionShape(l,p).isEmpty()||!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty())continue;
   if(!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)||l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK))continue;
   // The body needs the same headroom as an ordinary one-block jump or descent.
   var head=dy>0?at.above(2):dy<0?p.above(2):p.above();if(!l.getBlockState(head).getCollisionShape(l,head).isEmpty())continue;
   if(Math.abs(p.getX()-foot.getX())>radius||Math.abs(p.getZ()-foot.getZ())>radius||Math.abs(p.getY()-foot.getY())>radius){open=true;continue;}
   if(floor.add(p))queue.addLast(p);
  }}
  // Ordinary corridors remain navigation's job. A failed expedition can inspect connected
  // terraced floor; deep recovery additionally requires an uninterrupted solid climbing wall.
  if(open&&(target==null||r.distanceToSqr(Vec3.atBottomCenterOf(target))<=9||path!=null&&path.canReach()))return null;
  Route best=null;for(var from:floor)for(int y=1;y<=(expedition?12:6);y++){var up=from.above(y);if(!l.getBlockState(up).getCollisionShape(l,up).isEmpty()||!l.getBlockState(up.above()).getCollisionShape(l,up.above()).isEmpty()||!l.getFluidState(up).isEmpty())break;
   for(var d:Direction.Plane.HORIZONTAL){var p=up.relative(d);if(!l.hasChunkAt(p)||!l.getFluidState(p).isEmpty()||!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP))continue;
    if(l.getBlockState(p).isAir()&&l.getBlockState(p.above()).isAir()&&!l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)){
     if(y>6&&!continuousWall(r,from,p,d))continue;
     // A recent controlled descent already proved this unchanged ledge a dead end.
     // An explicit nearby destination may still legitimately require that very ledge.
     if(RecoveryLedges.deadEnd(r,p)&&(target==null||target.distSqr(p)>4))continue;
     // A reachable nearby ledge does not prove that the stalled long-distance route uses it.
     // The bounded floor and stillness checks already distinguish recovery from ordinary travel.
     // An intermediate low shelf may still be inside the same basin. Prefer the highest
     // verified rim; a higher expedition rim requires continuous handholds along its wall.
     if(p.getY()>foot.getY()&&(best==null||p.getY()>best.exit().getY()))best=new Route(from,p);
    }}}
  return best;
 }
 /** Deeper recovery is wall climbing, never levitation toward an unsupported high shelf. */
 private static boolean continuousWall(ResidentEntity r,BlockPos from,BlockPos exit,Direction side){var l=r.level();
  boolean gripped=false;for(int y=from.getY();y<exit.getY();y++){var wall=new BlockPos(exit.getX(),y,exit.getZ());var state=l.getBlockState(wall);
   if(!state.getFluidState().isEmpty()||state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)||state.is(net.minecraft.world.level.block.Blocks.CACTUS))return false;
   if(!state.isFaceSturdy(l,wall,side.getOpposite())){if(!gripped&&y<from.getY()+2)continue;return false;}gripped=true;
  }return gripped;
 }
 @Override public boolean canUse(){if(++checks%20!=0)return false;var now=resident.position();
  // Repeated jumps against a wall are motion, but not progress out of a pit. Keep one horizontal
  // anchor until the resident actually leaves it; small collision nudges must not reset the timer.
  if(last!=null&&now.multiply(1,0,1).distanceToSqr(last.multiply(1,0,1))<.5625)still+=20;else{still=0;last=now;}
  if(still<200)return false;var route=route(resident);if(route==null)return false;anchor=route.anchor();exit=route.exit();return true;}
 @Override public boolean canContinueToUse(){
  if(exit==null||escapeTicks>=escapeLimit||resident.isInWaterOrBubble()&&!shallow(resident))return false;
  if(resident.distanceToSqr(Vec3.atBottomCenterOf(exit))>.04)return true;
  // A low shelf under an overhang may still be inside the hollow. Releasing MOVE
  // here lets the same failed bed/work path send the body straight back down.
  // Continue only to another verified, strictly higher ledge; ordinary corridors,
  // architectural floors and all the existing fluid/headroom rules still refuse it.
  var next=route(resident);if(next==null||next.exit().getY()<=exit.getY())return false;
  anchor=next.anchor();exit=next.exit();atAnchor=false;escapeLimit=Math.min(2400,escapeLimit+160);return true;
 }
 @Override public void start(){escapeTicks=0;atAnchor=false;escapeLimit=resident.distanceToSqr(Vec3.atBottomCenterOf(anchor))>16?1200:160;resident.getNavigation().stop();resident.workStatus("escaping_pit");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){escapeTicks++;
  double horizontal=resident.position().multiply(1,0,1).distanceToSqr(Vec3.atBottomCenterOf(anchor).multiply(1,0,1));
  // Flow can hold a wading body against the edge of this otherwise clear column.
  // It need not defeat the current to hit the exact centre: ascent is safe once
  // its whole width fits the already verified column. Dry alignment stays exact.
  double margin=(1D-resident.getBbWidth())/2D;
  boolean wadingAligned=shallow(resident)
   &&Math.abs(resident.getX()-(anchor.getX()+.5))<=margin+1E-7
   &&Math.abs(resident.getZ()-(anchor.getZ()+.5))<=margin+1E-7;
  if(!atAnchor&&(horizontal>.01&&!wadingAligned||Math.abs(resident.getY()-anchor.getY())>1.5)){
   // Navigation considers a waypoint reached before the body is at its centre. Finish the
   // last stride explicitly so the upward motion starts under the verified clear column.
   if(horizontal<2.25){resident.getNavigation().stop();resident.getMoveControl().setWantedPosition(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,.8);}
   else{var path=resident.routeToRecoveryAnchor(anchor);if(path!=null&&path.canReach())resident.getNavigation().moveTo(path,.8);}return;
  }
  atAnchor=true;
  // Cross fully onto the ledge before ordinary navigation resumes. At its edge the node
  // evaluator still starts in the hollow and can immediately send the resident back down.
  resident.getNavigation().stop();var delta=Vec3.atBottomCenterOf(exit).subtract(resident.position());
  // Once the whole body is over its support, let gravity finish the landing.
  // Reapplying ascent just above the floor can otherwise hover beyond the arrival
  // tolerance until the recovery timeout, holding every ordinary work goal idle.
  double landingMargin=Math.min(.2,(1D-resident.getBbWidth())/2D);
  if(resident.getY()>=exit.getY()&&Math.abs(delta.x)<landingMargin&&Math.abs(delta.z)<landingMargin)
   resident.setDeltaMovement(delta.x*.18,Math.min(0,resident.getDeltaMovement().y),delta.z*.18);
  else if(resident.getY()<exit.getY()+.05)resident.setDeltaMovement(0,.18,0);
  else resident.setDeltaMovement(delta.x*.18,0,delta.z*.18);
  resident.fallDistance=0;}
 @Override public void stop(){exit=null;anchor=null;still=0;resident.setDeltaMovement(Vec3.ZERO);}
}
