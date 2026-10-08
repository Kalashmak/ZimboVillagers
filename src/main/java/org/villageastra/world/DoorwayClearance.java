package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
/** Clear surveyed natural leaves before entering a site whose door is deliberately installed last. */
public final class DoorwayClearance {
 private BlockPos approach,clearing;private UUID project;private final List<Integer> doors=new ArrayList<>();private int labor;
 public boolean tick(ResidentEntity worker,CompoundTag state,UUID id,double reachSq){
  if(!state.getBoolean("funded")||state.getBoolean("relocate")||!BuildingOrders.isBuilding(state))return false;
  var level=(ServerLevel)worker.level();var ops=state.getList("ops",Tag.TAG_COMPOUND);
  if(!id.equals(project)){project=id;doors.clear();labor=0;clearing=null;approach=null;
   for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);if(op.getCompound("after").getString("Name").endsWith("_door")){var step=HallConstructionPlan.step(op);if(step.after().getBlock() instanceof DoorBlock&&step.before().is(BlockTags.LEAVES))doors.add(i);}}
   doors.sort(Comparator.comparingInt((Integer i)->BlockPos.of(ops.getCompound(i).getLong("pos")).getY()).reversed());
  }
  var work=new LinkedHashMap<BlockPos,String>();
  for(int index:doors){var op=ops.getCompound(index);if(op.getBoolean("done"))continue;var step=HallConstructionPlan.step(op);var target=step.pos();
   if(!level.hasChunkAt(target))continue;
   if(step.after().getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER){
    var outward=step.after().getValue(DoorBlock.FACING).getOpposite();var side=outward.getClockWise();
    // Only the narrow entrance apron, never logs, persistent planted leaves or structural blocks.
    for(int distance=1;distance<=3;distance++)for(int across=-3;across<=3;across++)for(int y=1;y>=-1;y--){
     var p=target.relative(outward,distance).relative(side,across).above(y);if(!level.hasChunkAt(p))continue;var now=level.getBlockState(p);
     if(now.is(BlockTags.LEAVES)&&now.hasProperty(LeavesBlock.PERSISTENT)&&!now.getValue(LeavesBlock.PERSISTENT)&&level.getBlockEntity(p)==null
       &&!org.villageastra.server.OwnershipEvents.disallowedPlacement(level,p,BuildingOrders.buildingId(state)))work.put(p,"doorway-apron/"+p.asLong());
    }
   }
   if(level.getBlockState(target).is(BlockTags.LEAVES)&&level.getBlockEntity(target)==null)work.put(target,"doorway-clear/"+index);
  }
  if(!work.isEmpty()){
   var chosen=work.entrySet().stream().filter(e->worker.onGround()&&worker.getEyePosition().distanceToSqr(e.getKey().getCenter())<=reachSq&&visible(level,worker,worker.getEyePosition(),e.getKey())).findFirst().orElse(null);
   if(chosen!=null){
    if(!chosen.getKey().equals(clearing)){clearing=chosen.getKey();labor=0;}
    worker.getNavigation().stop();worker.workStatus("working");if(++labor<20)return true;labor=0;
    if(WorldJournal.place(level,Settlement.childId(id,chosen.getValue()),clearing,level.getBlockState(clearing),Blocks.AIR.defaultBlockState()))worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    approach=null;return true;
   }
   labor=0;worker.workStatus("walking");if(approach!=null&&ShoreEscapeGoal.dryBank(worker,approach)&&HallUpgradeGoal.stepToWorkStand(level,worker,approach))return true;if(worker.tickCount%20!=0)return true;
   var targets=new LinkedHashSet<BlockPos>();var origin=BlockPos.of(state.getLong("origin"));
   for(var target:work.keySet())for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=origin.getY()-2;y<=origin.getY()+1;y++){
    var p=new BlockPos(target.getX()+x,y,target.getZ()+z);var eye=new Vec3(p.getX()+.5,y+worker.getEyeHeight(),p.getZ()+.5);
    if(eye.distanceToSqr(target.getCenter())<=reachSq&&ShoreEscapeGoal.dryBank(worker,p)&&visible(level,worker,eye,target))targets.add(p);
   }
   var path=ConstructionRoutes.planAny(worker,targets);if(path!=null&&path.canReach()){approach=path.getTarget();worker.getNavigation().moveTo(path,.8);}else worker.workStatus("needs_access");return true;
  }
  clearing=null;approach=null;labor=0;return false;
 }
 private static boolean visible(ServerLevel level,ResidentEntity worker,Vec3 eye,BlockPos target){var hit=level.clip(new ClipContext(eye,target.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,worker));return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(target);}
}
