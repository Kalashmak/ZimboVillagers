package org.villageastra.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;

/** A deep, long-failed return may slowly open a supported stair instead of circling a sealed cave. */
public final class CaveEscapeGoal extends Goal {
 private record Failure(int since,BlockPos target,double distanceAtProgress){}
 private static final Map<ResidentEntity,Failure> failed=new WeakHashMap<>();
 private final ResidentEntity worker;
 private CaveExitPlan search;
 private List<CaveExitPlan.Step> steps;
 private int index,labor,repath,retryAt,nextCheck,searchAt;
 private BlockPos target,cracked;
 private UUID job;
 private boolean done,walking;
 public CaveEscapeGoal(ResidentEntity worker){this.worker=worker;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
 static void observe(ResidentEntity worker,BlockPos target,boolean reached){
  if(reached){failed.remove(worker);return;}
  if(worker.settlementId()==null||target.getY()<worker.getY()+12)return;
  var old=failed.get(worker);
  double distance=worker.position().distanceTo(Vec3.atBottomCenterOf(target));
  // A traveller making real progress still uses navigation. Circling back to the
  // same nearest point cannot renew this deadline indefinitely.
  boolean progress=old!=null&&old.target().equals(target)&&distance<=old.distanceAtProgress()-4;
  int since=old==null||progress?worker.tickCount:old.since();
  double checkpoint=old==null||progress||!old.target().equals(target)?distance:old.distanceAtProgress();
  failed.put(worker,new Failure(since,target.immutable(),checkpoint));
 }
 private boolean bodyReady(){return worker.isAlive()&&!worker.child()&&!worker.isSleeping()&&!worker.isPassenger()&&!worker.isInWaterOrBubble()&&!worker.isOnFire()&&!worker.isFreezing()&&worker.getLastHurtByMob()==null;}
 @Override public boolean canUse(){
  if(worker.tickCount<nextCheck||worker.tickCount<retryAt)return false;
  nextCheck=worker.tickCount+20;
  if(!bodyReady()||!worker.onGround())return false;
  var failure=failed.get(worker);if(failure==null||worker.tickCount-failure.since()<2400)return false;
  target=failure.target();
  return true;
 }
 @Override public void start(){
  index=0;labor=0;repath=0;done=false;walking=false;worker.getNavigation().stop();
  // Search while owning MOVE: a competing return/pit goal must not carry the
  // body away from the starting tread while this incremental plan is computed.
  steps=null;search=new CaveExitPlan(worker,target);searchAt=worker.tickCount;
  com.mojang.logging.LogUtils.getLogger().debug("CAVE_ESCAPE_START body={} ticks={} pos={} plan={}",worker.getUUID(),worker.tickCount,worker.position(),search);
  var data=worker.getPersistentData();
  if(!data.hasUUID("caveEscapeJob"))data.putUUID("caveEscapeJob",UUID.randomUUID());
  job=data.getUUID("caveEscapeJob");worker.workStatus("clearing_stone");
 }
 @Override public boolean canContinueToUse(){return !done&&(search!=null||steps!=null&&index<steps.size())&&bodyReady();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 private void clearCrack(){if(cracked!=null&&worker.level() instanceof ServerLevel l)MinerSpeed.clear(l,worker,cracked);cracked=null;labor=0;}
 private boolean supported(net.minecraft.world.phys.AABB box){
  var l=worker.level();int y=net.minecraft.util.Mth.floor(box.minY-.01);boolean found=false;
  for(int x=net.minecraft.util.Mth.floor(box.minX+1E-7);x<=net.minecraft.util.Mth.floor(box.maxX-1E-7);x++)for(int z=net.minecraft.util.Mth.floor(box.minZ+1E-7);z<=net.minecraft.util.Mth.floor(box.maxZ-1E-7);z++){
   var floor=new BlockPos(x,y,z);if(!l.hasChunkAt(floor)||!l.getFluidState(floor).isEmpty()||!l.getFluidState(floor.above()).isEmpty()||!l.getFluidState(floor.above(2)).isEmpty())return false;
   var state=l.getBlockState(floor);if(state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.CAMPFIRE)||state.is(Blocks.SOUL_CAMPFIRE))return false;
   var shape=state.getCollisionShape(l,floor);
   if(!shape.isEmpty()&&(state.isFaceSturdy(l,floor,net.minecraft.core.Direction.UP)||state.is(Blocks.DIRT_PATH))&&Math.abs(y+shape.max(net.minecraft.core.Direction.Axis.Y)-box.minY)<=.125)found=true;
  }return found;
 }
 /** The native navigator can call a short corner waypoint reached before the body reaches its centre. */
 private boolean align(BlockPos at){
  var destination=Vec3.atBottomCenterOf(at);var delta=destination.subtract(worker.position());var l=worker.level();
  if(!worker.onGround()||Math.abs(delta.y)>.125||delta.lengthSqr()>2.25)return false;
  for(int i=1;i<=8;i++){
   var shift=delta.scale(i/8D);var p=BlockPos.containing(worker.position().add(shift));
   var box=worker.getBoundingBox().move(shift);
   if(!l.hasChunkAt(p)||!supported(box)||!l.noCollision(worker,box))return false;
  }
  worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(destination.x,destination.y,destination.z,.8);return true;
 }
 @Override public void tick(){
  if(search!=null){
   worker.getNavigation().stop();
   if(worker.tickCount<searchAt)return;searchAt=worker.tickCount+20;
   search.advance(128);
   if(!search.finished())return;
   com.mojang.logging.LogUtils.getLogger().debug("CAVE_ESCAPE_SEARCH body={} ticks={} pos={} plan={}",worker.getUUID(),worker.tickCount,worker.position(),search);
   steps=search.result();search=null;
   if(steps==null||steps.isEmpty()){retryAt=worker.tickCount+1200;done=true;}
   return;
  }
  if(!(worker.level() instanceof ServerLevel l)||steps==null||index>=steps.size()){done=true;return;}
  if(worker.onGround()&&worker.tickCount%40==0){
   var ordinary=worker.routeTo(target,1,NaturalSupplyGoal.ROUTE_RANGE);
   if(ordinary!=null&&ordinary.canReach()){done=true;failed.remove(worker);worker.getPersistentData().remove("caveEscapeJob");return;}
  }
  var step=steps.get(index);
  if(worker.position().distanceToSqr(Vec3.atBottomCenterOf(step.to()))<.25&&worker.onGround()){
   clearCrack();index++;repath=0;walking=false;if(index>=steps.size())done=true;return;
  }
  // Every excavation is made from the previous intact tread, with a real eye ray.
  if(!walking&&worker.position().distanceToSqr(Vec3.atBottomCenterOf(step.from()))>.36){
   if(align(step.from()))return;
   if(--repath<=0){repath=20;var path=worker.routeToRecoveryAnchor(step.from());if(path==null||!path.canReach()){done=true;return;}worker.getNavigation().moveTo(path,.8);}return;
  }
  for(var p:step.dig())if(!l.getBlockState(p).getCollisionShape(l,p).isEmpty()){
   if(walking){done=true;return;}
   if(!CaveExitPlan.diggable(worker,p)||p.equals(worker.blockPosition().below())||!HarvestAccess.visible(worker,p)||!l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(p).expandTowards(0,2,0),other->other!=worker&&other.isAlive()).isEmpty()){done=true;return;}
   worker.getNavigation().stop();worker.getLookControl().setLookAt(Vec3.atCenterOf(p));
   if(!p.equals(cracked)){clearCrack();cracked=p;}
   var before=l.getBlockState(p);int needed=MinerSpeed.breakTicks(before,l,p,ItemStack.EMPTY);
   MinerSpeed.progress(l,worker,p,before,++labor,needed);
   if(labor<needed)return;
   // Emergency debris is discarded: no correct tool, no invented quarry loot or stock deposit.
   if(!WorldJournal.place(l,Settlement.childId(job,"block/"+p.asLong()),p,before,Blocks.AIR.defaultBlockState())){done=true;return;}
   MinerSpeed.broken(l,worker,p,before);clearCrack();return;
  }
  clearCrack();walking=true;
  if(align(step.to()))return;
  if(--repath<=0){repath=20;var path=worker.routeToRecoveryAnchor(step.to());if(path==null||!path.canReach()){done=true;return;}worker.getNavigation().moveTo(path,.8);}
 }
 @Override public void stop(){com.mojang.logging.LogUtils.getLogger().debug("CAVE_ESCAPE_STOP body={} ticks={} pos={} done={} index={} steps={} ready={}",worker.getUUID(),worker.tickCount,worker.position(),done,index,steps==null?0:steps.size(),bodyReady());clearCrack();worker.getNavigation().stop();steps=null;search=null;}
}
