package org.villageastra.world;
import java.util.*;
import java.nio.file.*;
import java.util.function.Predicate;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** One physical parcel per resident; the journal owns exactly-once inventory transitions. */
public final class PorterWork {
 private PorterWork(){}
 public static Path path(ServerLevel l,UUID worker){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-porter/"+worker+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID worker){if(!Files.exists(path(l,worker)))return new CompoundTag();var t=NbtRecord.read(path(l,worker));if(t.getInt("schema")!=1||!t.getUUID("worker").equals(worker)||!t.hasUUID("settlement")||!t.hasUUID("assignment")||!t.hasUUID("id")||!t.hasUUID("source")||!t.hasUUID("destination")||!Set.of("fetch","deliver","complete").contains(t.getString("stage")))throw new IllegalStateException("Invalid porter job");var item=ItemStack.of(t.getCompound("item"));if(item.isEmpty()||item.getCount()>LogisticsRoutes.MAX_LOAD||item.getCount()>item.getMaxStackSize())throw new IllegalStateException("Invalid porter parcel");return t;}
 public static boolean active(CompoundTag t){return !t.isEmpty()&&!t.getString("stage").equals("complete");}
 private static UUID operation(CompoundTag t,String kind){return Settlement.childId(t.getUUID("id"),kind);}
 public static int reserved(ServerLevel l,SettlementData.Entry e,UUID building,Predicate<ItemStack> matches,boolean incoming){int n=0;for(var r:e.settlement().residents()){var t=inspect(l,r.id());if(!active(t)||!t.getUUID(incoming?"destination":"source").equals(building))continue;if(!incoming&&(!t.getString("stage").equals("fetch")||WorldJournal.exists(l,operation(t,"take"))))continue;if(incoming&&WorldJournal.exists(l,operation(t,"put")))continue;var item=ItemStack.of(t.getCompound("item"));if(matches.test(item))n+=item.getCount();}
  // AD-147 (CF-H): and the legs of the warehouse's trips not yet taken / delivered - its couriers' and its wolves'.
  return n+(incoming?0:MedicineDelivery.reserved(l,e,building,matches))+WarehouseTrips.reserved(l,e,building,matches,incoming)+SmithyDelivery.reserved(l,e,building,matches,incoming);}
 public static ItemStack cargo(ServerLevel l,CompoundTag t){if(!active(t))return ItemStack.EMPTY;if(WorldJournal.recoverExisting(l,operation(t,"put"))!=null)return ItemStack.EMPTY;return WorldJournal.recoverAmount(l,operation(t,"take"));}
 public static void release(ServerLevel l,UUID worker){var t=inspect(l,worker);if(!t.isEmpty()){t.putString("stage","complete");NbtRecord.write(path(l,worker),t);}}
 private static boolean near(ResidentEntity w,net.minecraft.core.BlockPos p){return w.distanceToSqr(p.getX()+1.5,p.getY(),p.getZ()+.5)<=6.25;}
 private static void approach(ResidentEntity w,net.minecraft.core.BlockPos p){w.getNavigation().moveTo(p.getX()+1.5,p.getY(),p.getZ()+.5,.8);}
 public static boolean eligible(ResidentEntity w){if(!(w.level() instanceof ServerLevel l)||!w.isAlive()||w.settlementId()==null||w.escortPlayer()!=null||l.getServer().getPlayerCount()==0)return false;var e=SettlementData.get(l.getServer()).entry(w.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;var r=e.settlement().resident(w.getUUID());var b=e.settlement().workplace(w.getUUID());return r!=null&&r.alive()&&r.profession()==Profession.PORTER&&b!=null&&(Set.of("town_hall","warehouse").contains(b.type())||SmithyDelivery.post(b));}
 /** Trusted physical step, called only after production eligibility/custody checks. */
 public static void step(ResidentEntity w){step(w,null,false);}
 public static void step(ResidentEntity w,LogisticsRoutes.Route selected,boolean selfSupply){var l=(ServerLevel)w.level();var e=SettlementData.get(l.getServer()).entry(w.settlementId());var assignment=e.settlement().workplace(w.getUUID());if(assignment==null)return;var t=inspect(l,w.getUUID());
  if(!active(t)){var route=selfSupply?selected:LogisticsRoutes.choose(l,e,assignment,w.blockPosition());if(route==null){w.displayWorkItem(ItemStack.EMPTY);w.workStatus("logistics_idle");return;}t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("worker",w.getUUID());t.putUUID("settlement",e.settlement().id());t.putUUID("assignment",assignment.id());t.putUUID("source",route.source().id());t.putUUID("destination",route.destination().id());t.put("item",route.item().save(new CompoundTag()));t.putString("stage","fetch");if(selfSupply)t.putBoolean("selfSupply",true);NbtRecord.write(path(l,w.getUUID()),t);return;}
  if(!t.getUUID("assignment").equals(assignment.id())||!t.getUUID("settlement").equals(e.settlement().id()))return;
  if(WorldJournal.exists(l,operation(t,"put"))){WorldJournal.recoverExisting(l,operation(t,"put"));release(l,w.getUUID());w.displayWorkItem(ItemStack.EMPTY);return;}
  if(WorldJournal.exists(l,operation(t,"take"))){var taken=WorldJournal.recoverAmount(l,operation(t,"take"));if(!ItemStack.matches(taken,ItemStack.of(t.getCompound("item"))))throw new IllegalStateException("Mismatched porter input");if(t.getString("stage").equals("fetch")){t.putString("stage","deliver");NbtRecord.write(path(l,w.getUUID()),t);}}
  else if(t.getString("stage").equals("deliver"))throw new IllegalStateException("Unpaid porter parcel");
  var target=t.getUUID(t.getString("stage").equals("fetch")?"source":"destination");var b=e.settlement().buildings().stream().filter(x->x.id().equals(target)).findFirst();
  if(b.isEmpty()){w.workStatus("logistics_missing_chest");return;}var pos=LogisticsRoutes.position(e,b.get());var c=LogisticsRoutes.chest(l,e,b.get());if(c==null){w.workStatus("logistics_missing_chest");return;}var item=ItemStack.of(t.getCompound("item"));
  if(t.getString("stage").equals("fetch")&&!WorldJournal.exists(l,operation(t,"take"))){
   var destination=t.getUUID("destination");var dest=e.settlement().buildings().stream().filter(x->x.id().equals(destination)).findFirst().orElse(null);
   if(dest!=null&&l.hasChunkAt(LogisticsRoutes.position(e,dest))){var receiving=LogisticsRoutes.chest(l,e,dest);
    if(receiving!=null&&!LogisticsRoutes.fits(receiving,java.util.List.of(item))){release(l,w.getUUID());w.workStatus("logistics_supply_changed");return;}
   }
  }
  if(!near(w,pos)){w.workStatus(t.getString("stage").equals("fetch")?"logistics_fetching":"logistics_delivering");w.displayWorkItem(t.getString("stage").equals("deliver")?item:ItemStack.EMPTY);approach(w,pos);return;}w.getNavigation().stop();
  if(t.getString("stage").equals("fetch")){var held=WorldJournal.recoverAmount(l,operation(t,"take"));if(held.isEmpty()&&!WorldJournal.exists(l,operation(t,"take"))){for(int slot=0;slot<c.getContainerSize();slot++)if(ItemStack.isSameItemSameTags(item,c.getItem(slot))&&c.getItem(slot).getCount()>=item.getCount()&&LogisticsRoutes.count(c,s->ItemStack.isSameItemSameTags(s,item))-item.getCount()>=LogisticsRoutes.reserve(b.get(),item)){held=WorldJournal.takeAmount(l,operation(t,"take"),pos,slot,c.getItem(slot).copy(),item.getCount());break;}}
   if(held.isEmpty()){release(l,w.getUUID());w.workStatus("logistics_supply_changed");return;}if(!ItemStack.matches(item,held))throw new IllegalStateException("Mismatched porter receipt");t.putString("stage","deliver");NbtRecord.write(path(l,w.getUUID()),t);w.displayWorkItem(held);return;}
  if(!WorldJournal.deposit(l,operation(t,"put"),pos,item)){w.workStatus("output_full");return;}release(l,w.getUUID());w.displayWorkItem(ItemStack.EMPTY);w.workStatus("logistics_delivered");
 }
}
