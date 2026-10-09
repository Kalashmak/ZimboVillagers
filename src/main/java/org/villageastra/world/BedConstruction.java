package org.villageastra.world;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Assemble beds after structural work; a lost half needs a fresh, paid replacement. */
public final class BedConstruction {
 private BedConstruction(){}
 public static boolean ready(ServerLevel l,ListTag operations,CompoundTag candidate){
  var step=HallConstructionPlan.step(candidate);if(!(step.after().getBlock() instanceof BedBlock))return true;
  for(var raw:operations){var op=(CompoundTag)raw;
   if(op.getInt("phase")<=candidate.getInt("phase")&&!op.getBoolean("done")&&!(HallConstructionPlan.step(op).after().getBlock() instanceof BedBlock))return false;
  }
  var other=partner(step.pos(),step.after());
  for(var p:List.of(step.pos(),other)){
   if(!l.hasChunkAt(p)||!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP))return false;
  }
  return true;
 }
 private static BlockPos partner(BlockPos p,BlockState s){
  var direction=s.getValue(BedBlock.FACING);
  return p.relative(s.getValue(BedBlock.PART)==BedPart.FOOT?direction:direction.getOpposite());
 }
 /** Only a missing half with its exact planned partner still present. No block is placed here. */
 public static boolean queuePaidMissingHalf(ServerLevel l,CompoundTag state){
  if(state.getBoolean("complete")||!state.getBoolean("funded")||state.getBoolean("relocate")||!state.getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(raw->net.minecraft.world.item.ItemStack.of((CompoundTag)raw).isEmpty()))return false;
  var operations=state.getList("ops",Tag.TAG_COMPOUND);var last=new LinkedHashMap<BlockPos,BlockState>();
  for(var raw:operations){var op=(CompoundTag)raw;if(!op.getBoolean("done"))return false;var step=HallConstructionPlan.step(op);last.put(step.pos(),step.after());}
  for(var cell:last.entrySet()){
   var after=cell.getValue();if(!(after.getBlock() instanceof BedBlock))continue;
   var p=cell.getKey();var other=partner(p,after);var expected=last.get(other);
   if(expected==null||expected.getBlock()!=after.getBlock()||expected.getValue(BedBlock.PART)==after.getValue(BedBlock.PART)||expected.getValue(BedBlock.FACING)!=after.getValue(BedBlock.FACING))continue;
   if(!l.hasChunkAt(p)||!l.hasChunkAt(other)||!l.getBlockState(p).isAir()||!l.getFluidState(p).isEmpty())continue;
   var actual=l.getBlockState(other);
   if(actual.getBlock()!=expected.getBlock()||actual.getValue(BedBlock.PART)!=expected.getValue(BedBlock.PART)||actual.getValue(BedBlock.FACING)!=expected.getValue(BedBlock.FACING))continue;
   if(!l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)||!l.getBlockState(other.below()).isFaceSturdy(l,other.below(),Direction.UP))continue;
   var item=BuiltInRegistries.ITEM.getKey(after.getBlock().asItem()).toString();
   var replacement=new CompoundTag();replacement.putLong("pos",p.asLong());replacement.put("before",NbtUtils.writeBlockState(l.getBlockState(p)));replacement.put("after",NbtUtils.writeBlockState(after));replacement.putString("item",item);replacement.putInt("phase",2);replacement.putBoolean("bedReplacement",true);operations.add(replacement);
   if(!state.contains("initialCost"))state.put("initialCost",state.getCompound("cost").copy());
   var cost=new CompoundTag();cost.putInt(item,1);state.put("cost",cost);state.putBoolean("funded",false);state.putInt("bedReplacements",state.getInt("bedReplacements")+1);
   return true;
  }
  return false;
 }
}
