package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Work the adjacent exposed vein with the same paid tool before returning home. */
public final class OreBulkHarvest {
 private OreBulkHarvest(){}
 public static BulkHarvest.Next next(ResidentEntity worker,BlockPos previous,BlockState material,ItemStack pick,int held,Set<BlockPos> reserved){
  if(pick.isEmpty()||pick.isEnchanted()||!pick.isCorrectToolForDrops(material)
      ||pick.getDamageValue()>=pick.getMaxDamage()
      ||!(material.is(BlockTags.IRON_ORES)||material.is(BlockTags.COAL_ORES)||material.is(BlockTags.COPPER_ORES)||material.is(BlockTags.GOLD_ORES)))return null;
  // Unenchanted copper can drop five pieces; leave room for its largest yield.
  if(held+(material.is(BlockTags.COPPER_ORES)?5:1)>BulkHarvest.CAPACITY)return null;
  var l=(ServerLevel)worker.level();var candidates=new ArrayList<BlockPos>();
  for(var p:BlockPos.betweenClosed(previous.offset(-4,-2,-4),previous.offset(4,2,4)))
   if(!reserved.contains(p)&&l.hasChunkAt(p)&&l.getBlockState(p).is(material.getBlock())&&SurfaceQuarry.safe(l,p))candidates.add(p.immutable());
  candidates.sort(Comparator.comparingDouble(p->p.distSqr(worker.blockPosition())));
  for(var p:candidates){var stand=HarvestAccess.find(worker,p,NaturalSupplyGoal.ROUTE_RANGE);if(stand!=null)return new BulkHarvest.Next(p,stand);}
  return null;
 }
}
