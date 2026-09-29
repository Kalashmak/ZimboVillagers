package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
/** AD-060: two residents meeting in a doorway do not push each other into the frame. The one already in the doorway, or else the one nearer to it,
 *  goes first; the other steps aside off the passage, waits until the doorway is free and then goes on. */
public final class DoorwayGoal extends Goal {
 /** How near a door counts as meeting in it, and the longest a resident waits before trying again. */
 static final double NEAR=3.2,IN_DOOR=1.1;static final int PATIENCE=120;
 private final ResidentEntity resident;private BlockPos door;private ResidentEntity other;private BlockPos aside;private int waited;
 public DoorwayGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE));}
 /** A door on the next few nodes of a resident's path, or the door they stand in. */
 static BlockPos doorOn(ResidentEntity mob){
  var path=mob.getNavigation().getPath();
  if(path!=null&&!path.isDone())
   for(int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+4);i++){
    var node=path.getNodePos(i);
    for(var pos:List.of(node,node.above()))if(door(mob,pos))return lower(mob,pos);}
  var at=mob.blockPosition();
  for(var pos:List.of(at,at.above()))if(door(mob,pos))return lower(mob,pos);
  return null;
 }
 private static boolean door(ResidentEntity mob,BlockPos pos){return mob.level().getBlockState(pos).getBlock() instanceof DoorBlock;}
 private static BlockPos lower(ResidentEntity mob,BlockPos pos){var s=mob.level().getBlockState(pos);return s.getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER?pos.below():pos;}
 private static double distance(ResidentEntity mob,BlockPos door){return mob.position().distanceTo(Vec3.atBottomCenterOf(door));}
 /** The same order seen from both sides: in the doorway first, then nearer, then the smaller id. */
 static boolean goesFirst(ResidentEntity a,ResidentEntity b,BlockPos door){
  double da=distance(a,door),db=distance(b,door);
  boolean ina=da<=IN_DOOR,inb=db<=IN_DOOR;
  if(ina!=inb)return ina;
  if(Math.abs(da-db)>.25)return da<db;
  return a.getUUID().compareTo(b.getUUID())<0;
 }
 @Override public boolean canUse(){
  if(resident.tickCount%5!=0||resident.isSleeping())return false;
  door=doorOn(resident);if(door==null||distance(resident,door)>NEAR)return false;
  other=null;
  for(var near:resident.level().getEntitiesOfClass(ResidentEntity.class,resident.getBoundingBox().inflate(NEAR+1))){
   if(near==resident||!near.isAlive()||distance(near,door)>NEAR)continue;
   var theirs=doorOn(near);if(theirs==null||!theirs.equals(door))continue;
   if(goesFirst(near,resident,door)){other=near;break;}
  }
  if(other==null)return false;
  aside=aside();return aside!=null;
 }
 /** A free spot beside the passage on this resident's own side of the door, out of the way of the one coming through. */
 private BlockPos aside(){
  var facing=resident.level().getBlockState(door).getValue(DoorBlock.FACING);var across=facing.getClockWise();
  var toMe=resident.position().subtract(Vec3.atBottomCenterOf(door));
  int side=toMe.x*facing.getStepX()+toMe.z*facing.getStepZ()>=0?1:-1;
  BlockPos best=null;double bestDistance=Double.MAX_VALUE;
  for(int out=1;out<=3;out++)for(int lateral:new int[]{2,-2,3,-3,1,-1}){
   var spot=door.relative(facing,side*out).relative(across,lateral);
   if(!standable(spot))continue;
   double d=spot.distSqr(resident.blockPosition());if(d<bestDistance){bestDistance=d;best=spot;}
  }
  return best;
 }
 private boolean standable(BlockPos pos){
  var l=resident.level();
  return l.getBlockState(pos).getCollisionShape(l,pos).isEmpty()&&l.getBlockState(pos.above()).getCollisionShape(l,pos.above()).isEmpty()
   &&l.getBlockState(pos.below()).isFaceSturdy(l,pos.below(),Direction.UP);
 }
 @Override public void start(){waited=0;resident.getNavigation().moveTo(aside.getX()+.5,aside.getY(),aside.getZ()+.5,.8);resident.workStatus("making_way");}
 @Override public boolean canContinueToUse(){
  if(other==null||!other.isAlive()||++waited>PATIENCE)return false;
  // The way is free once the other one has gone through and left the doorway behind.
  return distance(other,door)<=NEAR&&door.equals(doorOn(other));
 }
 @Override public void tick(){
  if(resident.blockPosition().distSqr(aside)<=1)resident.getNavigation().stop();
  else if(waited%20==0)resident.getNavigation().moveTo(aside.getX()+.5,aside.getY(),aside.getZ()+.5,.8);
  resident.getLookControl().setLookAt(other);
 }
 @Override public void stop(){resident.getNavigation().stop();other=null;aside=null;door=null;}
 /** For the probe and tests: whether this resident is standing aside right now. */
 public boolean waiting(){return other!=null;}
}
