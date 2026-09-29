package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** A clinic V sends one paid medicine parcel with a real free kennel wolf, or hands it to a resident at its counter. */
public final class MedicineDelivery {
 private MedicineDelivery(){}
 public static final int LOST_TICKS=1200;
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-medicine/trips/"+village+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID village){if(!Files.exists(path(l,village)))return new CompoundTag();var t=NbtRecord.read(path(l,village));
  if(t.getInt("schema")!=1||!t.hasUUID("id")||!t.hasUUID("clinic")||!t.hasUUID("patient")||!Set.of("fetch","go","back","complete").contains(t.getString("stage")))throw new IllegalStateException("Invalid medicine delivery");return t;}
 private static void save(ServerLevel l,SettlementData.Entry e,CompoundTag t){NbtRecord.write(path(l,e.settlement().id()),t);}
 public static boolean active(CompoundTag t){return !t.isEmpty()&&!t.getString("stage").equals("complete");}
 public static boolean reservedWolf(ServerLevel l,SettlementData.Entry e,UUID wolf){var t=inspect(l,e.settlement().id());return active(t)&&!t.getBoolean("released")&&t.hasUUID("wolf")&&t.getUUID("wolf").equals(wolf);}
 private static UUID op(CompoundTag t,String verb){return Settlement.childId(t.getUUID("id"),"medicine/"+verb);}
 public static int reserved(ServerLevel l,SettlementData.Entry e,UUID building,java.util.function.Predicate<ItemStack> matches){var t=inspect(l,e.settlement().id());return active(t)&&t.getUUID("clinic").equals(building)&&t.getString("stage").equals("fetch")&&!WorldJournal.exists(l,op(t,"take"))&&matches.test(new ItemStack(VillageAstra.BANDAGE.get()))?1:0;}
 private static void finish(ServerLevel l,SettlementData.Entry e,CompoundTag t){t.putString("stage","complete");save(l,e,t);if(t.hasUUID("wolf")&&!t.getBoolean("released"))VillageDogs.release(l,e,t.getUUID("wolf"));}
 public static void tick(ServerLevel l,SettlementData.Entry e){var t=inspect(l,e.settlement().id());if(active(t)){step(l,e,t);return;}
  var clinic=Medicine.clinic(l,e);if(clinic==null||BuildingLevels.level(l,e,clinic)!=Medicine.MEDICINE)return;var chest=LogisticsRoutes.chest(l,e,clinic);if(chest==null||chest.countItem(VillageAstra.BANDAGE.get())==0)return;
  var at=LogisticsRoutes.position(e,clinic);var people=e.settlement().residents().stream().filter(r->r.alive()&&!MedicinePacks.carried(l,r.id())&&l.getEntity(r.id()) instanceof ResidentEntity n&&n.isAlive()).sorted(Comparator.comparing((Resident r)->!r.sick()).thenComparing(Resident::id)).toList();
  for(var r:people){var npc=(ResidentEntity)l.getEntity(r.id());boolean counter=npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=9;UUID wolf=null;
   if(!counter){if(!VillageDogs.pulls())continue;var free=VillageDogs.available(l,e);if(free.isEmpty())continue;wolf=free.get(0);if(!VillageDogs.send(l,e,wolf,at))continue;}
   t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("clinic",clinic.id());t.putUUID("patient",r.id());if(wolf!=null)t.putUUID("wolf",wolf);t.putString("stage","fetch");save(l,e,t);return;}
 }
 private static void step(ServerLevel l,SettlementData.Entry e,CompoundTag t){var patient=t.getUUID("patient");
  // Receipt first: a saved delivery replay never refills an already consumed pack or returns its bandage.
  if(MedicinePacks.received(l,patient,t.getUUID("id"))){finish(l,e,t);return;}
  var clinic=e.settlement().buildings().stream().filter(b->b.id().equals(t.getUUID("clinic"))).findFirst().orElse(null);
  var homeBuilding=clinic==null?Workshops.hall(e):clinic;
  if(clinic==null)t.putString("stage","back");
  if(homeBuilding==null){release(l,e,t);save(l,e,t);if(WorldJournal.recoverAmount(l,op(t,"take")).isEmpty())finish(l,e,t);return;}
  var home=LogisticsRoutes.position(e,homeBuilding);var r=e.settlement().resident(patient);var body=l.getEntity(patient);var npc=body instanceof ResidentEntity n&&n.isAlive()?n:null;
  var wolf=t.hasUUID("wolf")?t.getUUID("wolf"):null;var dog=wolf==null?null:l.getEntity(wolf);var stage=t.getString("stage");
  if(r==null||!r.alive()||MedicinePacks.carried(l,patient)||t.getInt("away")>=LOST_TICKS||dog!=null&&!dog.isAlive())stage="back";
  if(stage.equals("back")){t.putString("stage","back");release(l,e,t);save(l,e,t);var held=WorldJournal.recoverAmount(l,op(t,"take"));
   if(held.isEmpty()||WorldJournal.deposit(l,op(t,"back"),home,held))finish(l,e,t);return;}
  if(stage.equals("fetch")){boolean near=wolf!=null?VillageDogs.near(l,e,wolf,home,3):npc!=null&&npc.distanceToSqr(home.getX()+.5,home.getY(),home.getZ()+.5)<=9;
   if(!near){if(wolf!=null)VillageDogs.send(l,e,wolf,home);waitFor(l,e,t);return;}
   var held=WorldJournal.recoverAmount(l,op(t,"take"));if(held.isEmpty()&&!WorldJournal.exists(l,op(t,"take"))){var chest=LogisticsRoutes.chest(l,e,clinic);if(chest!=null)for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(VillageAstra.BANDAGE.get())){held=WorldJournal.takeAmount(l,op(t,"take"),home,slot,chest.getItem(slot).copy(),1);break;}}
   if(held.isEmpty()){finish(l,e,t);return;}t.putString("stage","go");t.putInt("away",0);save(l,e,t);return;}
  if(npc==null){waitFor(l,e,t);return;}
  boolean near=wolf!=null?VillageDogs.near(l,e,wolf,npc.blockPosition(),3):npc.distanceToSqr(home.getX()+.5,home.getY(),home.getZ()+.5)<=9;
  if(!near){if(wolf!=null)VillageDogs.send(l,e,wolf,npc.blockPosition());waitFor(l,e,t);return;}
  var held=WorldJournal.recoverAmount(l,op(t,"take"));if(!held.is(VillageAstra.BANDAGE.get())||held.getCount()!=1)throw new IllegalStateException("Unpaid medicine parcel");
  if(MedicinePacks.give(l,patient,t.getUUID("id")))finish(l,e,t);
 }
 private static void release(ServerLevel l,SettlementData.Entry e,CompoundTag t){if(t.hasUUID("wolf")&&!t.getBoolean("released")){t.putBoolean("released",true);save(l,e,t);VillageDogs.release(l,e,t.getUUID("wolf"));}}
 private static void waitFor(ServerLevel l,SettlementData.Entry e,CompoundTag t){t.putInt("away",t.getInt("away")+20);save(l,e,t);}
}
