package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** Existing mines acquire their newly required furnace from physical stock, even while a construction project is waiting. */
public final class FurnaceEquipment {
 private FurnaceEquipment(){}
 private static UUID op(CompoundTag t,String key){return Settlement.childId(t.getUUID("id"),"equipment/"+key);}
 public static BlockPos site(ServerLevel l,SettlementData.Entry e){for(var mine:e.settlement().buildings())if(mine.type().equals("mine")){var p=BuildingPlacement.at(e,mine,0,1,2);if(l.hasChunkAt(p)&&(l.getBlockState(p).isAir()||l.getBlockState(p).is(Blocks.COBBLESTONE))&&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),net.minecraft.core.Direction.UP))return p;}return null;}
 private static String save(ServerLevel l,Settlement.Building b,CompoundTag t,String status){NbtRecord.write(Workshops.path(l,b.id()),t);return status;}
 public static String advance(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var stage=t.getString("stage");var stock=Workshops.station(e,b);var c=LogisticsRoutes.chest(l,e,b);if(c==null)return "workshop_missing_chest";
  if(!stage.startsWith("equip_")){var p=site(l,e);if(p==null)return "workshop_missing_furnace";t.putLong("installAt",p.asLong());t.put("installBefore",NbtUtils.writeBlockState(l.getBlockState(p)));t.putString("stage","equip_take");return save(l,b,t,"workshop_funding");}
  if(stage.equals("equip_take")){
   var carried=ItemStack.of(t.getCompound("carried"));var id=op(t,"take/"+t.getInt("equipmentTakes"));var got=WorldJournal.recoverAmount(l,id);
   if(got.isEmpty())outer:for(var item:List.of(Items.FURNACE,Items.COBBLESTONE))for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(item)&&(carried.isEmpty()||carried.is(item))){got=WorldJournal.takeAmount(l,id,stock,slot,c.getItem(slot).copy(),Math.min(c.getItem(slot).getCount(),item==Items.FURNACE?1:8-carried.getCount()));break outer;}
   if(got.isEmpty()){var needs=new ListTag();var in=new CompoundTag();in.putString("ingredient",Ingredient.of(Items.COBBLESTONE).toJson().toString());in.putInt("count",8-carried.getCount());needs.add(in);t.put("needs",needs);return save(l,b,t,"workshop_missing_inputs");}
   if(carried.isEmpty())carried=got.copy();else carried.grow(got.getCount());t.put("carried",carried.save(new CompoundTag()));t.putInt("equipmentTakes",t.getInt("equipmentTakes")+1);t.remove("needs");
   if(carried.is(Items.FURNACE))t.putString("stage","equip_place");else if(carried.getCount()==8){t.putString("stage","equip_craft");t.putInt("equipmentLabor",0);}return save(l,b,t,"workshop_funding");
  }
  if(stage.equals("equip_craft")){int labor=t.getInt("equipmentLabor")+20;t.putInt("equipmentLabor",labor);if(labor>=3600){t.put("carried",new ItemStack(Items.FURNACE).save(new CompoundTag()));t.putString("stage","equip_place");}return save(l,b,t,"workshop_working");}
  if(stage.equals("equip_place")){var p=BlockPos.of(t.getLong("installAt"));var id=op(t,"place/"+t.getInt("equipmentTakes"));var furnace=Blocks.FURNACE.defaultBlockState();for(var mine:e.settlement().buildings())if(mine.type().equals("mine")&&BuildingPlacement.at(e,mine,0,1,2).equals(p))furnace=BuildingPlacement.layout(BuildingRepairs.design(e,mine),BuildingPlacement.origin(e,mine),mine.rotation()).get(p);
   if(furnace==null||!furnace.is(Blocks.FURNACE))return "workshop_missing_furnace";
   var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("installBefore"));
   if(WorldJournal.recoverExisting(l,id)==null&&(!l.getBlockState(p).equals(before)||!WorldJournal.place(l,id,p,before,furnace)))return "workshop_missing_furnace";
   t.remove("carried");t.remove("installAt");t.remove("installBefore");t.remove("needs");t.remove("furnace");t.putString("stage","smelt_raw");return save(l,b,t,"workshop_funding");
  }throw new IllegalStateException("Unknown equipment stage "+stage);
 }
 public static CompoundTag custody(ServerLevel l,CompoundTag original,ListTag held){var t=original.copy();var carried=ItemStack.of(t.getCompound("carried"));var stage=t.getString("stage");
  if(stage.equals("equip_take")){var pending=WorldJournal.recoverAmount(l,op(t,"take/"+t.getInt("equipmentTakes")));if(!pending.isEmpty()){if(carried.isEmpty())carried=pending;else carried.grow(pending.getCount());t.putInt("equipmentTakes",t.getInt("equipmentTakes")+1);}}
  if(stage.equals("equip_place")&&WorldJournal.recoverExisting(l,op(t,"place/"+t.getInt("equipmentTakes")))!=null)carried=ItemStack.EMPTY;
  if(!carried.isEmpty())held.add(carried.save(new CompoundTag()));t.remove("carried");t.remove("worker");t.remove("installAt");t.remove("installBefore");t.putString("stage","smelt_raw");return t;
 }
}
