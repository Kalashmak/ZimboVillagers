package org.villageastra.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Several loose blocks share a native sensing search; actual work remains individual. */
public final class LooseHarvestAccess {
 public record Choice(BlockPos target,BlockPos stand){}
 private LooseHarvestAccess(){}
 public static boolean loose(BlockState s){return s.is(BlockTags.SAND)||s.is(BlockTags.DIRT)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY);}
 public static Choice find(ResidentEntity worker,BlockPos original,Set<Item> wanted,Set<BlockPos> reserved,int range,int allowance){
  var l=(ServerLevel)worker.level();var material=l.getBlockState(original);
  if(!loose(material)||allowance<=0)return null;
  var blocks=new ArrayList<BlockPos>();
  for(var p:BlockPos.betweenClosed(original.offset(-2,-1,-2),original.offset(2,1,2)))
   if(!reserved.contains(p)&&l.hasChunkAt(p)&&l.getBlockState(p).is(material.getBlock())&&NaturalSupplyGoal.safe(l,p))blocks.add(p.immutable());
  blocks.sort(Comparator.comparingDouble(p->p.distSqr(original)));
  var adjacent=new LinkedHashMap<BlockPos,List<BlockPos>>();var shelves=new LinkedHashMap<BlockPos,List<BlockPos>>();
  int included=0;
  for(var target:blocks){
   if(Block.getDrops(l.getBlockState(target),l,target,null,worker,ItemStack.EMPTY).stream().noneMatch(s->wanted.contains(s.getItem())))continue;
   if(included++>=8)break;
   for(var offset:HarvestAccess.platformOffsets()){
    var feet=target.offset(offset);
    if(!HarvestAccess.standing(l,feet,target)||!HarvestAccess.visible(worker,feet,target))continue;
    var group=offset.getX()*offset.getX()+offset.getZ()*offset.getZ()==1?adjacent:shelves;
    group.computeIfAbsent(feet.immutable(),p->new ArrayList<>()).add(target);
   }
  }
  int queries=0;
  for(var group:List.of(adjacent,shelves))while(!group.isEmpty()&&queries<allowance){
   queries++;var path=HarvestRouteCache.planAny(worker,group.keySet(),range);
   if(path==null||!path.canReach())break;
   var feet=path.getTarget();var targets=group.remove(feet);if(targets==null)break;
   for(var target:targets)if(!reserved.contains(target)&&NaturalSupplyGoal.safe(l,target)
     &&HarvestAccess.survivesExtraction(path,target))return new Choice(target,feet);
  }
  return null;
 }
}
