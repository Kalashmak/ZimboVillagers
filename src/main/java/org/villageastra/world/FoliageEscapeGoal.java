package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import org.villageastra.server.OwnershipEvents;

/** A short physical exit when a natural crown grows through a resident's body. */
public final class FoliageEscapeGoal extends Goal {
 private final ResidentEntity resident;
 private BlockPos target;
 private List<BlockPos> route=List.of();
 private int checks,labor,step,ticks;
 public FoliageEscapeGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private static boolean leaf(ServerLevel l,BlockPos p){var state=l.getBlockState(p);return state.getBlock() instanceof LeavesBlock&&!state.getValue(LeavesBlock.PERSISTENT)&&state.getFluidState().isEmpty()&&!OwnershipEvents.protectedBlock(l,p);}
 private static Iterable<BlockPos> cells(AABB body){return BlockPos.betweenClosed(BlockPos.containing(body.minX,body.minY,body.minZ),BlockPos.containing(body.maxX,body.maxY,body.maxZ));}
 private static BlockPos obstruction(ResidentEntity resident,AABB body){
  if(!(resident.level() instanceof ServerLevel l))return null;
  for(var p:cells(body))if(l.hasChunkAt(p)&&body.intersects(new AABB(p))&&leaf(l,p))return p.immutable();
  return null;
 }
 /** An episode only starts with a leaf inside the body, never the support or an adjacent tree. */
 public static BlockPos obstruction(ResidentEntity resident){return obstruction(resident,resident.getBoundingBox().deflate(.0001));}
 private Vec3 feetAt(BlockPos p){var shape=resident.level().getBlockState(p.below()).getCollisionShape(resident.level(),p.below());return new Vec3(p.getX()+.5,p.getY()-1+(shape.isEmpty()?1:shape.max(Direction.Axis.Y)),p.getZ()+.5);}
 private AABB bodyAt(BlockPos p){return resident.getBoundingBox().move(feetAt(p).subtract(resident.position())).deflate(.0001);}
 private boolean passable(BlockPos p){
  var l=(ServerLevel)resident.level();if(!l.hasChunkAt(p))return false;
  var support=l.getBlockState(p.below());if(!support.isFaceSturdy(l,p.below(),Direction.UP)&&!support.is(Blocks.DIRT_PATH)&&!support.is(Blocks.FARMLAND))return false;
  var ground=l.getBlockState(p.below());if(ground.is(Blocks.MAGMA_BLOCK)||ground.is(Blocks.CAMPFIRE)||ground.is(Blocks.SOUL_CAMPFIRE))return false;
  var body=bodyAt(p);for(var q:cells(body)){
   if(!l.hasChunkAt(q)||!l.getFluidState(q).isEmpty())return false;if(leaf(l,q))continue;
   for(var shape:l.getBlockState(q).getCollisionShape(l,q).toAabbs())if(body.intersects(shape.move(q)))return false;
  }return true;
 }
 private List<BlockPos> exit(){
  var start=new BlockPos(resident.getBlockX(),(int)Math.ceil(resident.getY()-.0001),resident.getBlockZ());var parents=new HashMap<BlockPos,BlockPos>();var todo=new ArrayDeque<BlockPos>();parents.put(start,start);todo.add(start);
  while(!todo.isEmpty()){
   var p=todo.removeFirst();if(!p.equals(start)&&obstruction(resident,bodyAt(p))==null){
    var result=new ArrayList<BlockPos>();for(var at=p;!at.equals(start);at=parents.get(at))result.add(at);result.add(start);Collections.reverse(result);return result;
   }
   for(var d:Direction.Plane.HORIZONTAL){var next=p.relative(d);if(Math.abs(next.getX()-start.getX())>4||Math.abs(next.getZ()-start.getZ())>4||parents.containsKey(next)||!passable(next))continue;parents.put(next,p);todo.add(next);}
  }return List.of();
 }
 private boolean available(){return resident.isAlive()&&resident.settlementId()!=null&&resident.onGround()&&!resident.isSleeping()&&!resident.isPassenger()&&!resident.isInWaterOrBubble();}
 @Override public boolean canUse(){if(++checks%10!=0||!available()||obstruction(resident)==null)return false;route=exit();return !route.isEmpty();}
 @Override public boolean canContinueToUse(){return available()&&step<route.size()&&ticks<300&&passable(route.get(step));}
 @Override public void start(){labor=0;step=0;ticks=0;target=null;resident.getNavigation().stop();resident.workStatus("clearing_foliage");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  ticks++;if(!canContinueToUse())return;resident.getNavigation().stop();var l=(ServerLevel)resident.level();var next=route.get(step);
  var leaf=obstruction(resident);if(leaf==null)leaf=obstruction(resident,bodyAt(next));
  if(leaf!=null){
   if(resident.getEyePosition().distanceToSqr(Vec3.atCenterOf(leaf))>16)return;
   if(!leaf.equals(target)){clearCracks();target=leaf;labor=0;}
   var state=l.getBlockState(target);int required=Math.max(1,(int)Math.ceil(state.getDestroySpeed(l,target)*30));
   resident.swing(InteractionHand.MAIN_HAND);l.destroyBlockProgress(resident.getId(),target,Math.min(9,++labor*10/required));
   if(labor>=required){clearCracks();l.destroyBlock(target,true,resident);target=null;labor=0;}return;
  }
  clearCracks();target=null;labor=0;
  var point=feetAt(next);if(resident.position().distanceToSqr(point)<.04){step++;return;}
  resident.getMoveControl().setWantedPosition(point.x,point.y,point.z,.8);
 }
 private void clearCracks(){if(target!=null)resident.level().destroyBlockProgress(resident.getId(),target,-1);}
 @Override public void stop(){clearCracks();target=null;route=List.of();labor=0;resident.getNavigation().stop();}
}
