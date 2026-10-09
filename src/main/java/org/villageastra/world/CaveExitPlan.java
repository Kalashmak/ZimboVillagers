package org.villageastra.world;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.server.OwnershipEvents;

/** A bounded search for a supported escape stair, excavating only ordinary natural rock and soil. */
public final class CaveExitPlan {
 public record Step(BlockPos from,BlockPos to,List<BlockPos> dig){}
 private record Node(BlockPos at,Node parent,Step step,int cost){}
 private final ResidentEntity worker;
 private final BlockPos origin,target;
 private final PriorityQueue<Node> open;
 private final Map<BlockPos,Integer> costs=new HashMap<>();
 private int examined;
 private List<Step> result;
 private boolean finished;
 public CaveExitPlan(ResidentEntity worker,BlockPos target){
  this.worker=worker;this.origin=worker.blockPosition();this.target=target.immutable();
  open=new PriorityQueue<>(Comparator.comparingDouble(n->n.cost()+Math.max(0,target.getY()-n.at().getY())*35+n.at().distSqr(target)*.002));
  open.add(new Node(origin,null,null,0));costs.put(origin,0);
 }
 public boolean finished(){return finished;}
 public List<Step> result(){return result;}
 @Override public String toString(){return "CaveExitPlan{origin="+origin+", examined="+examined+", queued="+open.size()+", finished="+finished+", steps="+(result==null?0:result.size())+"}";}
 private boolean clear(BlockPos p){var l=worker.level();return l.hasChunkAt(p)&&l.getFluidState(p).isEmpty()&&l.getBlockState(p).getCollisionShape(l,p).isEmpty();}
 private boolean support(BlockPos p){var l=worker.level();if(!l.hasChunkAt(p))return false;var s=l.getBlockState(p);return s.getFluidState().isEmpty()&&s.isFaceSturdy(l,p,Direction.UP)&&!s.is(Blocks.MAGMA_BLOCK)&&!s.is(Blocks.CAMPFIRE)&&!s.is(Blocks.SOUL_CAMPFIRE);}
 /** Recheck at the actual strike too: never breach water, falling gravel, a container or a protected lot. */
 public static boolean diggable(ResidentEntity worker,BlockPos p){
  if(!(worker.level() instanceof ServerLevel l)||!l.hasChunkAt(p)||l.getBlockEntity(p)!=null||OwnershipEvents.disallowedPlacement(l,p)||Roads.cell(l,p)!=null)return false;
  var s=l.getBlockState(p);
  if(!(s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.DIRT)||s.is(Blocks.CLAY)||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK)||s.is(Blocks.SMOOTH_BASALT))||s.getDestroySpeed(l,p)<0||!s.getFluidState().isEmpty()||s.getBlock() instanceof FallingBlock)return false;
  for(var d:Direction.values()){var n=p.relative(d);if(!l.hasChunkAt(n)||!l.getFluidState(n).isEmpty())return false;}
  return !(l.getBlockState(p.above()).getBlock() instanceof FallingBlock);
 }
 private Step edge(Node node,BlockPos next){
  if(worker.level().isOutsideBuildHeight(next.above())||!worker.level().getWorldBorder().isWithinBounds(next)||Math.abs(next.getX()-origin.getX())>64||Math.abs(next.getZ()-origin.getZ())>64||next.getY()>origin.getY()+64||next.getY()<origin.getY()||!support(next.below()))return null;
  var dig=new LinkedHashSet<BlockPos>();
  // A one-block ascent also needs room above the old feet during the jump.
  var cells=new ArrayList<BlockPos>();if(next.getY()>node.at().getY())cells.add(node.at().above(2));cells.add(next.above());cells.add(next);
  for(var p:cells)if(!clear(p)){if(!diggable(worker,p))return null;dig.add(p.immutable());}
  // No step may excavate the support of any previous part of this same stair.
  for(Node prior=node;prior!=null;prior=prior.parent()){
   if(dig.contains(prior.at().below()))return null;
   if(prior.step()!=null&&prior.step().dig().contains(next.below()))return null;
  }
  return new Step(node.at(),next.immutable(),List.copyOf(dig));
 }
 public void advance(int budget){
  if(finished)return;
  var l=(ServerLevel)worker.level();
  for(int i=0;i<budget;i++){
   if(open.isEmpty()||examined++>=20000){finished=true;return;}
   var node=open.remove();if(costs.getOrDefault(node.at(),Integer.MAX_VALUE)!=node.cost())continue;
   var p=node.at();
   if(p.getY()>=target.getY()-2&&p.getY()>=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getX(),p.getZ())&&clear(p)&&clear(p.above())){
    var steps=new ArrayList<Step>();for(Node n=node;n.parent()!=null;n=n.parent())steps.add(n.step());Collections.reverse(steps);result=List.copyOf(steps);finished=true;return;
   }
   for(var d:Direction.Plane.HORIZONTAL)for(int dy=0;dy<=1;dy++){
    var next=p.relative(d).above(dy);var step=edge(node,next);if(step==null)continue;
    int cost=node.cost()+1+step.dig().size()*50;
    if(cost>=costs.getOrDefault(next,Integer.MAX_VALUE))continue;
    costs.put(next,cost);open.add(new Node(next,node,step,cost));
   }
  }
 }
}
