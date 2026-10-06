package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
/** Prompt physical exit from a solid block grown through the body; never removes terrain or suppresses damage. */
public final class SolidEscapeGoal extends Goal {
 private final ResidentEntity resident;private Vec3 exit;private int ticks;
 public SolidEscapeGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
 private Vec3 exit(){
  var l=resident.level();var foot=new BlockPos(resident.getBlockX(),(int)Math.ceil(resident.getY()-.0001),resident.getBlockZ());Vec3 best=null;
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
   if(dx==0&&dz==0)continue;var p=foot.offset(dx,0,dz);var below=p.below();if(!l.hasChunkAt(p))continue;var ground=l.getBlockState(below);
   if(!ground.isFaceSturdy(l,below,Direction.UP)&&!ground.is(Blocks.DIRT_PATH)&&!ground.is(Blocks.FARMLAND))continue;
   if(ground.is(Blocks.MAGMA_BLOCK)||ground.is(Blocks.CAMPFIRE)||ground.is(Blocks.SOUL_CAMPFIRE)||!l.getFluidState(below).isEmpty())continue;
   var shape=ground.getCollisionShape(l,below);if(shape.isEmpty())continue;var point=new Vec3(p.getX()+.5,below.getY()+shape.max(Direction.Axis.Y),p.getZ()+.5);
   if(Math.abs(point.y-resident.getY())>.125||!l.getFluidState(p).isEmpty()||!l.getFluidState(p.above()).isEmpty())continue;
   var body=resident.getBoundingBox().move(point.subtract(resident.position()));if(!l.noCollision(resident,body))continue;
   if(best==null||resident.position().distanceToSqr(point)<resident.position().distanceToSqr(best))best=point;
  }return best;
 }
 @Override public boolean canUse(){if(!resident.isInWall()||resident.isPassenger()||resident.isSleeping()||resident.isInWaterOrBubble())return false;exit=exit();return exit!=null;}
 @Override public boolean canContinueToUse(){return exit!=null&&ticks<100&&!resident.isPassenger()&&!resident.isSleeping()&&(resident.isInWall()||!resident.level().noCollision(resident,resident.getBoundingBox()));}
 @Override public void start(){ticks=0;resident.getNavigation().stop();resident.workStatus("escaping_solid_block");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){ticks++;resident.getNavigation().stop();resident.getMoveControl().setWantedPosition(exit.x,exit.y,exit.z,.8);}
 @Override public void stop(){exit=null;resident.getNavigation().stop();}
}
