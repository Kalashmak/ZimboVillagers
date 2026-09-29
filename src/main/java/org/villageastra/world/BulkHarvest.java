package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** A small load of nearby loose material; each block still needs its own walk, labor and journal receipt. */
public final class BulkHarvest {
 public static final int CAPACITY=16;
 public record Next(BlockPos target,BlockPos stand){}
 private BulkHarvest(){}
 public static Next next(ResidentEntity worker,BlockPos previous,BlockState material,int held){
  if(!(material.is(BlockTags.SAND)||material.is(Blocks.GRAVEL)||material.is(Blocks.CLAY))||held+(material.is(Blocks.CLAY)?4:1)>CAPACITY)return null;
  var l=(ServerLevel)worker.level();var candidates=new ArrayList<BlockPos>();
  for(var p:BlockPos.betweenClosed(previous.offset(-4,-2,-4),previous.offset(4,2,4)))if(l.hasChunkAt(p)&&l.getBlockState(p).is(material.getBlock())&&NaturalSupplyGoal.safe(l,p))candidates.add(p.immutable());
  candidates.sort(Comparator.comparingDouble(p->p.distSqr(worker.blockPosition())));
  for(var p:candidates){var stand=HarvestAccess.find(worker,p,NaturalSupplyGoal.ROUTE_RANGE);if(stand!=null)return new Next(p,stand);}
  return null;
 }
}
