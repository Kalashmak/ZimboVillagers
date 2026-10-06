package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
/** Step onto the floor before routing from bedroom furniture through a low doorway. */
public final class BedExitGoal extends Goal {
 private final ResidentEntity resident;private BlockPos exit;private int ticks;
 public BedExitGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
 public static BlockPos landing(ResidentEntity r){
  if(r.isSleeping()||r.isPassenger()||r.isInWaterOrBubble())return null;
  var l=r.level();var foot=r.blockPosition();
  // Wake-up and failed navigation can leave residents on either raised bedroom surface.
  if(!raised(l.getBlockState(foot).getBlock())){
   if(raised(l.getBlockState(foot.below()).getBlock()))foot=foot.below();else return null;
  }
  var target=r.getNavigation().getTargetPos();
  // Leave the room toward its door even when the workplace lies on the other side of its wall.
  {double nearest=Double.MAX_VALUE;for(var p:BlockPos.betweenClosed(foot.offset(-6,-1,-6),foot.offset(6,1,6)))if(l.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.DoorBlock&&p.distSqr(foot)<nearest){nearest=p.distSqr(foot);target=p.immutable();}}
  BlockPos best=null;double score=Double.MAX_VALUE;
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){if(x==0&&z==0)continue;var p=foot.offset(x,0,z);
   if(!l.hasChunkAt(p)||!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty()||!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP))continue;
   if(!l.getBlockState(p).getCollisionShape(l,p).isEmpty()||!l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty())continue;
   if(l.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK))continue;
   var delta=Vec3.atBottomCenterOf(p).subtract(r.position());
   // Both the landing and the headroom while crossing the furniture edge must fit the actual body.
   if(!l.noCollision(r,r.getBoundingBox().move(delta)))continue;
   boolean clear=true;for(int step=1;step<=8;step++)if(!l.noCollision(r,r.getBoundingBox().move(delta.x*step/8,0,delta.z*step/8))){clear=false;break;}if(!clear)continue;
   double cost=target==null?p.distSqr(foot):p.distSqr(target);if(cost<score){score=cost;best=p;}
  }return best;
 }
 private static boolean raised(net.minecraft.world.level.block.Block b){return b instanceof BedBlock||b instanceof ChestBlock;}
 private boolean bedtime(){return resident.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof SleepGoal sleep&&sleep.readyToSleep());}
 @Override public boolean canUse(){if(bedtime())return false;exit=landing(resident);return exit!=null;}
 @Override public boolean canContinueToUse(){return exit!=null&&ticks<80&&!bedtime()&&!resident.isSleeping()&&resident.position().distanceToSqr(Vec3.atBottomCenterOf(exit))>.04;}
 @Override public void start(){ticks=0;resident.getNavigation().stop();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){ticks++;resident.getNavigation().stop();resident.getMoveControl().setWantedPosition(exit.getX()+.5,exit.getY(),exit.getZ()+.5,.8);resident.workStatus("walking");}
 @Override public void stop(){exit=null;resident.getNavigation().stop();}
}
