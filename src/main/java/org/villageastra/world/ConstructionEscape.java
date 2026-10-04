package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** A physical step onto low furniture when a half-built room has closed the walking route. */
public final class ConstructionEscape {
 private ConstructionEscape(){}
 public static BlockPos raised(ResidentEntity worker,BlockPos goal,java.util.Set<BlockPos> refused){
  var l=(ServerLevel)worker.level();var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
   if(x==0&&z==0)continue;var foot=from.offset(x,1,z);
   if(refused.contains(foot))continue;
   if(!l.hasChunkAt(foot)||l.getBlockState(foot.below()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))continue;
   var support=l.getBlockState(foot.below()).getCollisionShape(l,foot.below());if(support.isEmpty())continue;
   var body=worker.getBoundingBox().move(foot.getX()+.5-worker.getX(),foot.getY()-worker.getY(),foot.getZ()+.5-worker.getZ());
   if(!l.noCollision(worker,body)||!l.getFluidState(foot).isEmpty())continue;
   double cost=foot.distSqr(goal);if(cost<score){best=foot;score=cost;}
  }
  return best;
 }
 public static void walk(ResidentEntity worker,BlockPos step){
  worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(step.getX()+.5,step.getY(),step.getZ()+.5,.8);
  if(worker.onGround()&&worker.getY()<step.getY()-.1)worker.getJumpControl().jump();
 }
}
