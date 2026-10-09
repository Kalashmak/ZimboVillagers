package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-136 (§2.2, CF7, owner answer 3): scientific works. The village's laboratory (its best one — one laboratory counts, CF15) has as many
 *  scientist places as its working level allows (1..6). Every place held by a living, educated scientist assigned to the laboratory writes one
 *  work every {@link ScienceBalance#WORK_TICKS} ticks of the village clock — the clock stands while the game is paused or nobody plays, and runs
 *  while the player is far away: a laboratory whose chest is unloaded catches up when it loads, as many works as its chest has room for.
 *  A place keeps its progress when its scientist changes. The record holds clock marks, not counters, so it is written only when a place
 *  changes hands or a work is put in the chest (CF10); every work goes in under its own journal id science/&lt;seat&gt;/&lt;n&gt; (no duplicate on replay).
 *  A schema-1 record of the old paper chain returns its paid materials and unwritten outputs to the laboratory chest first (CF4c). */
public final class ScienceWorks {
 private ScienceWorks(){}
 public static final int SCHEMA=2;
 public static Path path(ServerLevel l,UUID lab){return ScienceWorkshop.path(l,lab);}
 /** The laboratory whose places count: the highest kept level, then the oldest id. */
 public static Settlement.Building lab(SettlementData.Entry e){
  return e.settlement().buildings().stream().filter(b->b.type().equals("laboratory")).max(Comparator.<Settlement.Building>comparingInt(Settlement.Building::level).thenComparing(b->b.id().toString(),Comparator.reverseOrder())).orElse(null);}
 /** Living, educated scientists assigned to this laboratory, in a stable order. */
 static List<UUID> scientists(SettlementData.Entry e,Settlement.Building lab){var s=e.settlement();
  return s.residents().stream().filter(r->r.alive()&&r.educated()&&r.profession()==Profession.SCIENTIST&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(lab.id())).map(Resident::id).sorted().toList();}
 static CompoundTag read(ServerLevel l,Settlement.Building lab){var p=path(l,lab.id());if(!Files.exists(p))return null;var t=NbtRecord.read(p);
  if(t.getInt("schema")==SCHEMA&&(!t.hasUUID("building")||!t.getUUID("building").equals(lab.id())||!(t.get("seats") instanceof ListTag)))throw new IllegalStateException("Invalid science record");return t;}
 private static CompoundTag fresh(Settlement.Building lab,long now){var t=new CompoundTag();t.putInt("schema",SCHEMA);t.putUUID("building",lab.id());t.putInt("level",Math.max(1,lab.level()));t.put("seats",new ListTag());t.putLong("since",now);return t;}
 private static void write(ServerLevel l,Settlement.Building lab,CompoundTag t){NbtRecord.write(path(l,lab.id()),t);}
 /** The places of the record, grown to n (a new place starts empty and idle). */
 private static ListTag seats(CompoundTag t,int n){var seats=t.getList("seats",Tag.TAG_COMPOUND);while(seats.size()<n){var s=new CompoundTag();s.putLong("ticks",0);s.putLong("since",-1);s.putInt("n",0);seats.add(s);}t.put("seats",seats);return seats;}
 /** Scientist places the village's laboratory has at its working level now (0 without a laboratory). */
 public static int places(ServerLevel l,SettlementData.Entry e){var lab=lab(e);return lab==null?0:ScienceBalance.seats(Math.max(1,BuildingTiers.level(l,e,lab)));}
 /** Clock ticks a place has worked towards its next work at this moment. */
 static long progress(CompoundTag seat,long now){long ticks=seat.getLong("ticks");long since=seat.getLong("since");return since>=0&&now>since?ticks+(now-since):ticks;}
 /** The places held now: the laboratory's working level allows them and a scientist sits in each (read only, for the office). */
 public static int seated(ServerLevel l,SettlementData.Entry e){var lab=lab(e);if(lab==null)return 0;
  try{var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return Math.min((int)scientists(e,lab).stream().filter(w->!CargoCustody.pending(l.getServer(),w)).count(),ScienceBalance.seats(Math.max(1,lab.level())));
   int n=0;for(var raw:t.getList("seats",Tag.TAG_COMPOUND)){var seat=(CompoundTag)raw;if(seat.hasUUID("worker")&&!seat.getBoolean("custodyPaused")&&!CargoCustody.pending(l.getServer(),seat.getUUID("worker")))n++;}return n;}catch(RuntimeException ex){return 0;}}
 /** Status of a scientist's place, for the resident card: "science_writing", "science_chest_full" or "" (no place: the laboratory is full). */
 public static String status(ServerLevel l,SettlementData.Entry e,UUID worker){if(CargoCustody.pending(l.getServer(),worker))return "returning_cargo";var lab=lab(e);if(lab==null)return "";var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return "";
  for(var raw:t.getList("seats",Tag.TAG_COMPOUND)){var s=(CompoundTag)raw;if(s.hasUUID("worker")&&s.getUUID("worker").equals(worker))return s.getBoolean("custodyPaused")?"returning_cargo":s.getBoolean("full")?"science_chest_full":"science_writing";}return "";}
 /** AD-154: the index of the place a scientist holds (his desk, LabDesks), or -1 without one. */
 public static int seat(ServerLevel l,SettlementData.Entry e,UUID worker){if(CargoCustody.pending(l.getServer(),worker))return -1;var lab=lab(e);if(lab==null)return -1;
  try{var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return -1;var seats=t.getList("seats",Tag.TAG_COMPOUND);
   for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);if(s.hasUUID("worker")&&s.getUUID("worker").equals(worker)&&!s.getBoolean("custodyPaused"))return i;}}catch(RuntimeException ex){return -1;}
  return -1;}
 /** AD472: a durable cargo transfer pauses only its owner's scientific place at the signed village-clock boundary.
  * The hook follows custody's durable BEGIN; advance repairs a missed hook, including a return completed between passes. */
 public static void pauseForCustody(net.minecraft.server.MinecraftServer server,CompoundTag custody){
  var e=SettlementData.get(server).entry(custody.getUUID("settlement"));if(e==null)return;
  var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));if(l==null)return;
  var lab=lab(e);if(lab==null)return;var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return;
  boolean dirty=false;for(var raw:t.getList("seats",Tag.TAG_COMPOUND)){var s=(CompoundTag)raw;
   if(s.hasUUID("worker")&&s.getUUID("worker").equals(custody.getUUID("owner")))dirty|=custody(s,custody,SettlementData.get(server).clock().ticks());}
  if(dirty)write(l,lab,t);
 }
 /** A legacy transfer has no historical clock boundary: keep the old counter at first observation and mark that limitation durably. */
 private static boolean custody(CompoundTag seat,CompoundTag cargo,long now){
  if(!cargo.hasUUID("id"))return false;boolean dirty=false;
  if(!seat.hasUUID("custody")||!seat.getUUID("custody").equals(cargo.getUUID("id"))){
   boolean legacy=!cargo.contains("startedAt",Tag.TAG_LONG);long boundary=legacy?now:cargo.getLong("startedAt"),since=seat.getLong("since");fold(seat,boundary);
   seat.putUUID("custody",cargo.getUUID("id"));seat.putUUID("custodyOwner",cargo.getUUID("owner"));seat.putLong("custodyResumeFloor",since>=0?Math.max(boundary,since):boundary);seat.putBoolean("custodyPaused",true);
   if(legacy){seat.putBoolean("custodyLegacy",true);seat.putLong("custodyObservedAt",now);}else{seat.remove("custodyLegacy");seat.remove("custodyObservedAt");}dirty=true;
  }
  if(cargo.getBoolean("complete")&&seat.getBoolean("custodyPaused")){
   long completed=!seat.getBoolean("custodyLegacy")&&cargo.contains("completedAt",Tag.TAG_LONG)?cargo.getLong("completedAt"):now;
   seat.putLong("since",seat.hasUUID("worker")&&!cargo.getBoolean("dead")&&!seat.getBoolean("full")?Math.max(completed,seat.getLong("custodyResumeFloor")):-1);seat.putBoolean("custodyPaused",false);dirty=true;
  }
  return dirty;
 }
 private static boolean custody(net.minecraft.server.MinecraftServer server,CompoundTag seat,long now){
  var owner=seat.hasUUID("worker")?seat.getUUID("worker"):seat.hasUUID("custodyOwner")?seat.getUUID("custodyOwner"):null;
  return owner!=null&&custody(seat,CargoCustody.inspect(server,owner),now);
 }
 private static void clearCustody(CompoundTag seat){for(var key:List.of("custody","custodyOwner","custodyResumeFloor","custodyPaused","custodyLegacy","custodyObservedAt"))seat.remove(key);}
 /** Every 40 ticks for every village (ServerEvents): the places, the works due and the ordered level-I payments. */
 public static void tick(net.minecraft.server.MinecraftServer server,long now){
  for(var e:List.copyOf(SettlementData.get(server).entries())){
   var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));if(l==null)continue;
   try{advance(l,e,now);}catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("ASTRA_SCIENCE could not advance {}: {}",e.settlement().id(),ex.toString());}
   try{if(Files.exists(org.villageastra.server.BookResearch.path(l,e.settlement().id())))org.villageastra.server.BookResearch.payOrders(l,e);}catch(RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("ASTRA_RESEARCH could not pay orders {}: {}",e.settlement().id(),ex.toString());}
  }
 }
 /** Advances the laboratory of a village to the village clock {@code now}; returns the works put in its chest. Trusted service (tests pass the clock). */
 public static int advance(ServerLevel l,SettlementData.Entry e,long now){
  var lab=lab(e);if(lab==null||!e.dimension().equals(l.dimension().location().toString()))return 0;
  var t=read(l,lab);boolean dirty=false;
  if(t!=null&&t.getInt("schema")==1){if(!ScienceWorkshop.migrate(l,e,lab))return 0;t=null;}
  if(t==null){t=fresh(lab,now);dirty=true;}
  // The working level (core and equipment) is read while the laboratory stands loaded, and remembered for the time it is not.
  var chest=LogisticsRoutes.chest(l,e,lab);
  if(chest!=null){int level=Math.max(1,BuildingTiers.level(l,e,lab));if(level!=t.getInt("level")){t.putInt("level",level);dirty=true;}}
  int places=ScienceBalance.seats(t.getInt("level"));var seats=seats(t,places);
  var free=new ArrayList<>(scientists(e,lab));
  // A place keeps its scientist while he stays; the others take the free places in order.
  for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);dirty|=custody(l.getServer(),s,now);if(s.hasUUID("worker")){var w=s.getUUID("worker");if(i<places&&free.remove(w))continue;fold(s,now);s.remove("worker");dirty=true;}}
  // Existing owners retain their stopped durable place; pending new owners cannot occupy a free desk.
  free.removeIf(w->CargoCustody.pending(l.getServer(),w));
  for(int i=0;i<places&&!free.isEmpty();i++){var s=seats.getCompound(i);if(s.hasUUID("worker"))continue;clearCustody(s);s.putUUID("worker",free.remove(0));s.putLong("since",now);dirty=true;dirty|=custody(l.getServer(),s,now);}
  int written=0;
  for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);if(s.getBoolean("custodyPaused"))continue;long p=progress(s,now);if(p<ScienceBalance.WORK_TICKS)continue;
   if(chest==null)continue; // unloaded: the clock keeps counting and the works are written when the chest loads
   var pos=LogisticsRoutes.position(e,lab);boolean full=false;
   while(p>=ScienceBalance.WORK_TICKS){var id=Settlement.childId(lab.id(),"science/"+i+"/"+s.getInt("n"));
    if(!WorldJournal.deposit(l,id,pos,new ItemStack(VillageAstra.RESEARCH_VOLUME.get()))){full=true;break;}
    s.putInt("n",s.getInt("n")+1);p-=ScienceBalance.WORK_TICKS;written++;}
   // A full chest holds one work ready and loses the rest: the place waits, its counter stopped full, until the chest has room.
   long since=full||!s.hasUUID("worker")?-1:now;if(full)p=ScienceBalance.WORK_TICKS;
   if(s.getLong("ticks")==p&&s.getLong("since")==since&&s.getBoolean("full")==full)continue;
   s.putLong("ticks",p);s.putLong("since",since);if(full)s.putBoolean("full",true);else s.remove("full");dirty=true;}
  if(dirty)write(l,lab,t);
  return written;
 }
 /** Folds a place's running time into its counter and stops it (its scientist left). */
 private static void fold(CompoundTag s,long now){s.putLong("ticks",progress(s,now));s.putLong("since",-1);}
}
