package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;

/** Acceptance checks must reject unloaded or mostly missing buildings, unlike a repair request's threshold. */
public final class TerminalProgress {
 private TerminalProgress(){}
 public static int missing(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  String design=BuildingTiers.layoutId(e.settlement(),b.type(),BuildingTiers.max(b.type()));
  var layout=new LinkedHashMap<>(BuildingPlacement.layout(e,b,design));
  if(b.type().equals("farm")&&!FarmField.legacy(e.settlement()))for(var cell:FarmBarn.layout(BuildingTiers.max(b.type()),e.settlement().westField(b.id())).entrySet())
   layout.put(BuildingPlacement.at(e,b,cell.getKey().getX(),cell.getKey().getY(),cell.getKey().getZ()),cell.getValue());
  int count=0;
  var drive=b.type().equals("mine")?e.settlement().mineAreas().get(b.id()):null;
  for(var cell:layout.entrySet()){
   BlockState expected=cell.getValue();
   if(expected.isAir()||expected.is(BlockTags.SAPLINGS)||expected.getBlock() instanceof CropBlock)continue;
   if(drive!=null){var local=BuildingPlacement.local(e,b,cell.getKey());if(local.getZ()>=7&&drive.contains(local.getX(),local.getY(),local.getZ(),0))continue;}
   if(!l.hasChunkAt(cell.getKey())||!BuildingRepairs.present(l.getBlockState(cell.getKey()),BuildingOrders.payable(expected)))count++;
  }
  if(b.type().equals("farm")){
   var modules=FarmField.modules(BuildingTiers.max(b.type()),e.settlement().westField(b.id()),FarmField.legacy(e.settlement()));
   for(var module:modules){var water=FarmField.localWater(module);var at=BuildingPlacement.at(e,b,water.getX(),water.getY(),water.getZ());
    if(!l.hasChunkAt(at)||!l.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)||!l.getFluidState(at).isSource())count++;
    if(!l.hasChunkAt(at.above())||!BuildingRepairs.present(l.getBlockState(at.above()),FarmField.COVER))count++;
   }
   for(var local:FarmField.localColumns(modules)){
    if(modules.stream().anyMatch(m->FarmField.localWater(m).equals(local)))continue;
    var at=BuildingPlacement.at(e,b,local.getX(),local.getY(),local.getZ());
    if(!l.hasChunkAt(at)||!(l.getBlockState(at).is(BlockTags.DIRT)||l.getBlockState(at).is(net.minecraft.world.level.block.Blocks.FARMLAND)))count++;
   }
  }
  return count;
 }
 public static Map<String,String> blockers(ServerLevel l,SettlementData.Entry e){
  var result=new TreeMap<String,String>();
  for(var b:e.settlement().buildings())if(BuildingTiers.upgradable(b.type())){
   int max=BuildingTiers.max(b.type()),working=BuildingLevels.level(l,e,b);String key=b.type()+"/"+b.id();
   if(BuildingTiers.built(e,b)<max||working<max){result.put(key,"built="+BuildingTiers.built(e,b)+" working="+working+" required="+max);continue;}
   int missing=missing(l,e,b);if(missing>0)result.put(key,"missing="+missing);
  }
  return result;
 }
}
