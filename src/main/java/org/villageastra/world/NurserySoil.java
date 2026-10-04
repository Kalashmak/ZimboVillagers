package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** One paid soil cell for emergency replanting on sand; the displaced sand stays in the load. */
public final class NurserySoil {
 private NurserySoil(){}
 public static boolean active(CompoundTag t){return t.getString("stage").equals("nursery_soil");}
 private static UUID id(CompoundTag t,String step){return Settlement.childId(t.getUUID("operation"),"nursery/"+step);}
 public static ListTag cargo(ServerLevel l,CompoundTag t){
  var out=t.getList("cargo",Tag.TAG_COMPOUND).copy();
  if(!active(t))return out;
  if(!t.getBoolean("soilHeld")){var got=WorldJournal.recoverTake(l,id(t,"take"));if(!got.isEmpty())out.add(got.save(new CompoundTag()));}
  var placed=WorldJournal.recoverExisting(l,id(t,"place"));
  if(placed!=null){out=ResourceWorkGoal.without(out,Items.DIRT,1);var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),placed.getCompound("before"));out.add(new ItemStack(before.getBlock().asItem()).save(new CompoundTag()));}
  return out;
 }
 public static boolean fetch(ServerLevel l,SettlementData.Entry e,CompoundTag t,BlockPos source){
  if(t.getBoolean("soilHeld"))return true;
  var got=WorldJournal.recoverTake(l,id(t,"take"));
  if(got.isEmpty()&&!WorldJournal.exists(l,id(t,"take"))&&l.getBlockEntity(source) instanceof Container c){
   var available=HallReserve.view(l,e,c);
   for(int i=0;i<c.getContainerSize();i++)if(available.getItem(i).is(Items.DIRT)){got=WorldJournal.take(l,id(t,"take"),source,i,c.getItem(i).copy());break;}
  }
  if(got.isEmpty())return false;
  var load=t.getList("cargo",Tag.TAG_COMPOUND);load.add(got.save(new CompoundTag()));t.put("cargo",load);t.putBoolean("soilHeld",true);return true;
 }
 public static boolean prepare(ServerLevel l,CompoundTag t){
  if(!active(t))return false;
  var at=BlockPos.of(t.getLong("target"));var receipt=WorldJournal.recoverExisting(l,id(t,"place"));
  if(receipt==null){
   if(!ForestRenewal.sandy(l,at)||ResourceWorkGoal.count(t.getList("cargo",Tag.TAG_COMPOUND),Items.DIRT)<1)return false;
   var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("soilBefore"));
   if(!WorldJournal.place(l,id(t,"place"),at.below(),before,Blocks.DIRT.defaultBlockState()))return false;
  }
  t.put("cargo",cargo(l,t));t.remove("soilHeld");t.remove("soilBefore");t.remove("soilLabor");t.putString("stage","sapling");return true;
 }
 public static boolean committed(ServerLevel l,CompoundTag t){return WorldJournal.recoverExisting(l,id(t,"place"))!=null;}
}
