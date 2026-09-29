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
  try{var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return Math.min(scientists(e,lab).size(),ScienceBalance.seats(Math.max(1,lab.level())));
   int n=0;for(var raw:t.getList("seats",Tag.TAG_COMPOUND))if(((CompoundTag)raw).hasUUID("worker"))n++;return n;}catch(RuntimeException ex){return 0;}}
 /** Status of a scientist's place, for the resident card: "science_writing", "science_chest_full" or "" (no place: the laboratory is full). */
 public static String status(ServerLevel l,SettlementData.Entry e,UUID worker){var lab=lab(e);if(lab==null)return "";var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return "";
  for(var raw:t.getList("seats",Tag.TAG_COMPOUND)){var s=(CompoundTag)raw;if(s.hasUUID("worker")&&s.getUUID("worker").equals(worker))return s.getBoolean("full")?"science_chest_full":"science_writing";}return "";}
 /** AD-154: the index of the place a scientist holds (his desk, LabDesks), or -1 without one. */
 public static int seat(ServerLevel l,SettlementData.Entry e,UUID worker){var lab=lab(e);if(lab==null)return -1;
  try{var t=read(l,lab);if(t==null||t.getInt("schema")!=SCHEMA)return -1;var seats=t.getList("seats",Tag.TAG_COMPOUND);
   for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);if(s.hasUUID("worker")&&s.getUUID("worker").equals(worker))return i;}}catch(RuntimeException ex){return -1;}
  return -1;}
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
  for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);if(s.hasUUID("worker")){var w=s.getUUID("worker");if(i<places&&free.remove(w))continue;fold(s,now);s.remove("worker");dirty=true;}}
  for(int i=0;i<places&&!free.isEmpty();i++){var s=seats.getCompound(i);if(s.hasUUID("worker"))continue;s.putUUID("worker",free.remove(0));s.putLong("since",now);dirty=true;}
  int written=0;
  for(int i=0;i<seats.size();i++){var s=seats.getCompound(i);long p=progress(s,now);if(p<ScienceBalance.WORK_TICKS)continue;
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
