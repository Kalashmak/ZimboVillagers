package org.villageastra.world;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** Shallow clay can be reached from a dry bank without entering the water. */
public final class ClayBankHarvest {
 private ClayBankHarvest(){}
 /** Geometry only: protection, roots, falling blocks and lava remain caller guards. */
 public static boolean shallow(Level l,BlockPos p){
  if(!l.hasChunkAt(p))return false;
  if(!l.getBlockState(p).is(Blocks.CLAY)||!l.getFluidState(p).isEmpty())return false;
  var above=l.getFluidState(p.above());
  if(!above.isEmpty()&&!above.is(FluidTags.WATER))return false;
  if(above.is(FluidTags.WATER)&&!l.getFluidState(p.above(2)).isEmpty())return false;
  for(var d:Direction.values())if(d!=Direction.DOWN&&l.getFluidState(p.relative(d)).is(FluidTags.WATER))return true;
  return false;
 }
 public static int floodY(Level l,BlockPos p){return p.getY()+(l.getFluidState(p.above()).is(FluidTags.WATER)?1:0);}
 public static boolean dryStand(Level l,BlockPos feet,BlockPos p){return !shallow(l,p)||feet.getY()>floodY(l,p);}
 public static boolean dryBody(ResidentEntity worker,BlockPos p){
  return worker.onGround()&&!worker.isInWaterOrBubble()&&!worker.isUnderWater()
      &&worker.getY()>=floodY(worker.level(),p)+1&&HarvestAccess.visible(worker,p);
 }
}
