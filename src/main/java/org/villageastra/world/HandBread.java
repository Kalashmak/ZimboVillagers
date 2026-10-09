package org.villageastra.world;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-104 P2, owner decision 1 of 2026-09-19: an idle adult of the village bakes bread by hand at the town hall, dearer in wheat and slower than
 *  the mill and bakery (5 wheat make 2 bread where the chain takes 4; 30 s a bread; no fuel), while the chain does not work and the pantries run
 *  low, or whenever a meal was missed. One durable job per village (data/astra-handbread, named by the village), written before its first
 *  withdrawal; every withdrawal and deposit goes through the journal under an id made from the job, so a crash, a death or a reload neither
 *  repeats nor loses one. The paid wheat belongs to the job, not to the baker's body: whoever bakes next finishes it. It is not a station recipe
 *  and publishes no want, so quests, pleas, caravans and trade never ask anybody for this wheat. */
public final class HandBread {
 /** A unit: WHEAT_PER_UNIT wheat into BREAD_PER_UNIT bread, LABOR_PER_BREAD labour ticks for each bread; a job is at most UNITS_PER_JOB units. */
 public static final int WHEAT_PER_UNIT,BREAD_PER_UNIT,LABOR_PER_BREAD,UNITS_PER_JOB,RESERVE_DAYS,LOCK_TICKS;
 /** Labour of one turn, which is also the shortest time between two turns: the hall's level adds nothing, bread by hand stays slow. */
 public static final int TURN=20;
 static{
  JsonObject o;try(var s=HandBread.class.getResourceAsStream("/data/villageastra/balance/hand_bread.json")){if(s==null)throw new IllegalStateException("Missing hand bread balance");o=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
  value(o,"schema",1,1);
  WHEAT_PER_UNIT=value(o,"wheat_per_unit",1,64);BREAD_PER_UNIT=value(o,"bread_per_unit",1,64);LABOR_PER_BREAD=20*value(o,"labor_seconds_per_bread",1,3600);
  UNITS_PER_JOB=value(o,"units_per_job",1,64);RESERVE_DAYS=value(o,"reserve_days",1,30);LOCK_TICKS=value(o,"lock_ticks",20,24000);
  // Owner decision 1: by hand costs more wheat than the mill and bakery (2 wheat a bread). A job's bread goes into the hall as one stack, in one slot.
  if(WHEAT_PER_UNIT<=2*BREAD_PER_UNIT||UNITS_PER_JOB*BREAD_PER_UNIT>64)throw new IllegalStateException("Inconsistent hand bread balance");
 }
 private static int value(JsonObject o,String key,int min,int max){if(!o.has(key))throw new IllegalStateException("Missing hand bread "+key);int v=o.get(key).getAsInt();if(v<min||v>max)throw new IllegalStateException("Invalid hand bread "+key);return v;}
 private HandBread(){}
 public static Path path(ServerLevel l,UUID settlement){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-handbread/"+settlement+".bin");}
 /** The village's job: stage idle|fund|work|output, id, units, wheat, bread, paid, withdrawals, source, labor, needLabor, output, rest, breadTries,
  *  restTries, lastTick; baked (all jobs) and lastBaker. */
 private static long INSPECT_READS;
 /** Number of verified record reads, for native sensing checks. */
 public static synchronized long inspectReads(){return INSPECT_READS;}
 private record BreadStamp(int tick,long revision,java.nio.file.attribute.FileTime modified,java.nio.file.attribute.FileTime created,long size,Object key){}
 private record BreadRead(BreadStamp stamp,CompoundTag tag){}
 private static final Map<MinecraftServer,Map<Path,BreadRead>> BREAD_READS=new WeakHashMap<>();
 /** AD468: only verified job sensing is shared in this actual server tick. Every authoritative write remains immediate. */
 public static synchronized CompoundTag inspect(ServerLevel l,UUID settlement){
  var p=path(l,settlement).toAbsolutePath().normalize();
  if(!Files.exists(p)){var cache=BREAD_READS.get(l.getServer());if(cache!=null)cache.remove(p);return new CompoundTag();}
  try{
   var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class);
   var stamp=new BreadStamp(l.getServer().getTickCount(),AtomicRecord.revision(p),a.lastModifiedTime(),a.creationTime(),a.size(),a.fileKey());
   var cache=BREAD_READS.computeIfAbsent(l.getServer(),server->new LinkedHashMap<Path,BreadRead>(16,.75F,true){
    @Override protected boolean removeEldestEntry(Map.Entry<Path,BreadRead> entry){return size()>256;}
   });
   var seen=cache.get(p);if(seen!=null&&seen.stamp().equals(stamp))return seen.tag().copy();
   INSPECT_READS++;var tag=NbtRecord.read(p);cache.put(p,new BreadRead(stamp,tag));return tag.copy();
  }catch(IOException ex){throw new IllegalStateException("Cannot read "+p,ex);}
 }
 // ---- the gate ---------------------------------------------------------------------------------
 /** A station of this type is staffed: its own worker may work there now (eligible, and not stopped by hunger), or its machinery turns it. */
 private static boolean staffed(ServerLevel l,SettlementData.Entry e,String type){
  var s=e.settlement();
  // AD-135: the mill beside the restaurant (mill_annex) is the village's mill.
  for(var b:s.buildings()){if(!org.villageastra.domain.CoreCatalog.canonical(org.villageastra.domain.AnnexTypes.workplace(b.type())).equals(type))continue;
   for(var r:s.residents()){var w=s.workplace(r.id());if(w!=null&&w.id().equals(b.id())&&Workshops.eligible(r,b)&&Population.mayWork(r))return true;}
   if(Machines.mechanized(l,e,b))return true;}
  return false;
 }
 /** The mill and the restaurant's kitchen (AD-139, was the bakery) both work: the village makes its bread the efficient way and nobody bakes by hand unless a meal was missed. */
 public static boolean chainStaffed(ServerLevel l,SettlementData.Entry e){return staffed(l,e,"mill")&&staffed(l,e,"restaurant");}
 /** Rations of RESERVE_DAYS days of meals for everybody alive, children too (they eat): 60 for six residents. */
 public static int reserveRations(SettlementData.Entry e){long alive=e.settlement().residents().stream().filter(Resident::alive).count();return (int)(alive*Population.MEAL_NUTRITION*24000L*RESERVE_DAYS/Population.MEAL_INTERVAL);}
 /** An NPC village with usable spare housing saves the real food needed for its
  * next child as well as its ordinary meal reserve. A full village keeps the
  * original daily target; a player's village keeps the player's food policy. */
 public static int foodTarget(SettlementData.Entry e){
  var s=e.settlement();int daily=reserveRations(e);
  if(s.governance().playerMayor()!=null||s.homes().stream().noneMatch(h->h.usable()&&s.occupancy(h.id())<h.capacity())
     ||s.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.home()!=null).count()<2)return daily;
  long alive=s.residents().stream().filter(Resident::alive).count();
  return Math.max(daily,Math.toIntExact((alive+1)*Population.BIRTH_FOOD));
 }
 /** The pantries hold less than their meal and available-housing target. */
 public static boolean lowOnFood(ServerLevel l,SettlementData.Entry e){return Population.storedNutrition(l,e)<foodTarget(e);}
 /** Somebody alive went without a meal: the emergency in which bread is baked by hand whatever the staffing — a driven chain without coal must not starve the village. */
 public static boolean missedMeal(SettlementData.Entry e){return e.settlement().residents().stream().anyMatch(r->r.alive()&&r.missedMeals()>0);}
 /** A new job may start (override 10): the hall chest is at hand, and the chain does not work while the pantries run low, or a meal was missed. */
 public static boolean open(ServerLevel l,SettlementData.Entry e){return hallChest(l,e)!=null&&(missedMeal(e)||lowOnFood(l,e)&&!chainStaffed(l,e));}
 // ---- wheat --------------------------------------------------------------------------------------
 /** Plain wheat only, so the wheat given back is exactly the wheat taken. */
 private static boolean wheat(ItemStack s){return s.is(Items.WHEAT)&&!s.hasTag();}
 private static OwnedChestEntity hallChest(ServerLevel l,SettlementData.Entry e){var hall=Workshops.hall(e);return hall==null?null:LogisticsRoutes.chest(l,e,hall);}
 /** Wheat a chest can spare: less the building's own keep, what an approved hall project counts on and what porters are already sent to fetch. */
 private static int spare(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var c=LogisticsRoutes.chest(l,e,b);if(c==null)return 0;var one=new ItemStack(Items.WHEAT);
  return Math.max(0,LogisticsRoutes.count(c,HandBread::wheat)-LogisticsRoutes.reserve(b,one)-LogisticsRoutes.constructionReserve(l,e,b,one)-PorterWork.reserved(l,e,b.id(),s->s.is(Items.WHEAT),false)-constructionWheat(l,e,b));
 }
 /** Keep the next otherwise-ready construction recipe's grain only while one
  * complete village meal is already in the pantry. Emergency baking always wins. */
 static int constructionWheat(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(!b.type().equals("town_hall")||missedMeal(e)||!HallUpgradeGoal.pending(l,e.settlement().id()))return 0;
  long meal=e.settlement().residents().stream().filter(Resident::alive).count()*Population.MEAL_NUTRITION;
  if(meal==0||Population.storedNutrition(l,e)<meal)return 0;
  var project=HallUpgradeGoal.headerView(l,e.settlement().id());if(project.getBoolean("funded")||e.settlement().governance().paused(HallConstructionPlan.projectId(project)))return 0;
  var chest=hallChest(l,e);if(chest==null)return 0;var free=HallReserve.view(l,e,chest);
  // A detached planning view answers whether grain alone would enable the product.
  // It never enters a job, journal or real inventory.
  var probe=new net.minecraft.world.SimpleContainer(free.getContainerSize()+1);
  for(int slot=0;slot<free.getContainerSize();slot++){var item=free.getItem(slot);if(!item.is(Items.WHEAT))probe.setItem(slot,item.copy());}
  probe.setItem(free.getContainerSize(),new ItemStack(Items.WHEAT,64));
  var spec=Workshops.spec(l,e,b);if(spec==null)return 0;
  for(var want:Workshops.wants(l,e))if(want.need()==LogisticsRoutes.NEED_BUILD&&want.destination().equals(b.id())){
   var job=Workshops.plan(l,spec,probe,List.of(want));if(job==null||job.outputs().stream().noneMatch(want::matches))continue;
   int needed=job.inputs().stream().filter(in->in.matches(new ItemStack(Items.WHEAT))).mapToInt(Workshops.Input::count).sum();
   if(needed>0)return needed;
  }return 0;
 }
 /** Where wheat is fetched from when the hall has none: warehouses (the stock of a larger village), then the farm chests, in settlement order. */
 private static List<Settlement.Building> stores(SettlementData.Entry e){var result=new ArrayList<Settlement.Building>();for(var type:List.of("warehouse","farm"))for(var b:e.settlement().buildings())if(b.type().equals(type))result.add(b);return result;}
 /** All the wheat a job could take now: the hall's first, then the other stores'. */
 public static int wheatAvailable(ServerLevel l,SettlementData.Entry e){int n=0;var hall=Workshops.hall(e);if(hall!=null)n+=spare(l,e,hall);for(var b:stores(e))n+=spare(l,e,b);return n;}
 // ---- the job ------------------------------------------------------------------------------------
 private static boolean busy(CompoundTag t){var stage=t.getString("stage");return stage.equals("fund")||stage.equals("work")||stage.equals("output");}
 /** A job is under way; it always finishes, gate or not. */
 public static boolean busy(ServerLevel l,SettlementData.Entry e){return busy(inspect(l,e.settlement().id()));}
 /** Worth taking an adult for: the hall chest is at hand, and a job waits to be finished or may start with wheat for a whole unit. */
 public static boolean actionable(ServerLevel l,SettlementData.Entry e){return hallChest(l,e)!=null&&(busy(l,e)||open(l,e)&&wheatAvailable(l,e)>=WHEAT_PER_UNIT);}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);}
 /** Where the baker stands for the next turn: the store the job fetches wheat from, otherwise the town-hall chest. */
 public static BlockPos where(ServerLevel l,SettlementData.Entry e){return where(e,inspect(l,e.settlement().id()));}
 public static BlockPos where(SettlementData.Entry e,CompoundTag t){
  if(t.getString("stage").equals("fund")&&t.hasUUID("source")){var store=building(e,t.getUUID("source"));if(store!=null)return LogisticsRoutes.position(e,store);}
  var hall=Workshops.hall(e);return hall==null?null:LogisticsRoutes.position(e,hall);
 }
 /** One turn of the village's hand bread, the baker standing at where(). Trusted service called by HandBreadGoal, or by tests with an explicit clock. */
 public static String advance(ServerLevel l,SettlementData.Entry e,UUID baker,long now){
  var hall=Workshops.hall(e);if(hall==null||hallChest(l,e)==null)return "workshop_missing_chest";
  var file=path(l,e.settlement().id());var t=inspect(l,e.settlement().id());
  if(!busy(t)){
   if(!open(l,e))return "hand_bread_idle";
   int units=Math.min(UNITS_PER_JOB,wheatAvailable(l,e)/WHEAT_PER_UNIT);if(units==0)return "hand_bread_missing_wheat";
   // The job is on disk before anything leaves a chest: its id names every withdrawal and deposit that follows.
   var job=new CompoundTag();job.putInt("schema",1);job.putUUID("settlement",e.settlement().id());job.putUUID("id",UUID.randomUUID());job.putString("stage","fund");
   job.putInt("units",units);job.putInt("wheat",units*WHEAT_PER_UNIT);job.putInt("bread",units*BREAD_PER_UNIT);job.putInt("paid",0);job.putInt("withdrawals",0);
   job.putLong("labor",0);job.putLong("needLabor",(long)units*BREAD_PER_UNIT*LABOR_PER_BREAD);job.putInt("output",0);job.putInt("rest",0);
   job.putLong("lastTick",now);job.putInt("baked",t.getInt("baked"));job.putUUID("lastBaker",baker);
   NbtRecord.write(file,job);return "hand_bread_funding";
  }
  long last=t.getLong("lastTick");if(now>=last&&now-last<TURN)return "hand_bread_working";
  t.putLong("lastTick",now);t.putUUID("lastBaker",baker);var id=t.getUUID("id");
  if(t.getString("stage").equals("fund"))return fund(l,e,hall,file,t,id);
  if(t.getString("stage").equals("work")){
   t.putLong("labor",Math.min(t.getLong("needLabor"),t.getLong("labor")+TURN));if(t.getLong("labor")>=t.getLong("needLabor"))t.putString("stage","output");
   NbtRecord.write(file,t);return "hand_bread_working";
  }
  // Each deposit has its own id, so a record rolled back or replayed after a crash never puts the bread or the wheat in twice.
  var pos=LogisticsRoutes.position(e,hall);int index=t.getInt("output");
  if(index==0&&t.getInt("bread")>0)return putAway(l,file,t,id,"bread",pos,new ItemStack(Items.BREAD,t.getInt("bread")),1);
  if(index<=1&&t.getInt("rest")>0)return putAway(l,file,t,id,"rest",pos,new ItemStack(Items.WHEAT,t.getInt("rest")),2);
  // Done in one write: the stage and the count of bread baked change together.
  t.putInt("output",2);t.putString("stage","idle");t.putInt("baked",t.getInt("baked")+t.getInt("bread"));NbtRecord.write(file,t);return "hand_bread_complete";
 }
 private static int taken(CompoundTag receipt){return receipt==null?0:ItemStack.of(receipt.getCompound("before")).getCount()-ItemStack.of(receipt.getCompound("after")).getCount();}
 private static void book(CompoundTag t,int wheat){t.putInt("paid",t.getInt("paid")+wheat);t.putInt("withdrawals",t.getInt("withdrawals")+1);}
 /** One output into the hall chest, then the next. A deposit recorded before a crash is replayed at the chest it names (the journal loads that chunk);
  *  if its slot changed before the replay (a meal ate from that bread stack, a porter filled that empty slot), it put nothing in and never can, so its
  *  id is passed over for good and the next turn deposits afresh — the job never waits on it at a chest with room. A chest really full keeps the id. */
 private static String putAway(ServerLevel l,Path file,CompoundTag t,UUID id,String what,BlockPos pos,ItemStack stack,int next){
  int tries=t.getInt(what+"Tries");var op=Settlement.childId(id,tries==0?what:what+"/"+tries);boolean recorded=WorldJournal.exists(l,op);
  if(recorded?WorldJournal.recoverExisting(l,op)!=null:WorldJournal.deposit(l,op,pos,stack)){t.putInt("output",next);NbtRecord.write(file,t);return "hand_bread_output";}
  if(recorded)t.putInt(what+"Tries",tries+1);
  NbtRecord.write(file,t);return recorded?"hand_bread_output":"hand_bread_output_full";
 }
 /** One withdrawal per turn, from the store the baker stands at; the hall comes first. */
 private static String fund(ServerLevel l,SettlementData.Entry e,Settlement.Building hall,Path file,CompoundTag t,UUID id){
  int missing=t.getInt("wheat")-t.getInt("paid");
  if(missing<=0){t.putString("stage","work");t.remove("source");NbtRecord.write(file,t);return "hand_bread_working";}
  var take=Settlement.childId(id,"wheat/"+t.getInt("withdrawals"));
  // A withdrawal recorded before a crash is settled first, at the chest it names (the journal loads that chunk); one that can no longer apply took
  // nothing, and its id is passed over for good, so it never applies later either.
  if(WorldJournal.exists(l,take)){book(t,taken(WorldJournal.recoverExisting(l,take)));NbtRecord.write(file,t);return "hand_bread_funding";}
  var source=t.hasUUID("source")?building(e,t.getUUID("source")):hall;if(source==null)source=hall;
  var pos=LogisticsRoutes.position(e,source);var chest=LogisticsRoutes.chest(l,e,source);
  if(chest==null&&!l.hasChunkAt(pos)){NbtRecord.write(file,t);return "hand_bread_fetching";}
  int spare=spare(l,e,source);
  if(chest!=null&&spare>0)for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(!wheat(s))continue;
   book(t,WorldJournal.takeAmount(l,take,pos,slot,s.copy(),Math.min(missing,Math.min(s.getCount(),spare))).getCount());NbtRecord.write(file,t);return "hand_bread_funding";}
  // Nothing to spare here: the hall first, then the first other store with wheat; the baker walks there before the next take.
  if(!source.equals(hall)&&spare(l,e,hall)>0){t.remove("source");NbtRecord.write(file,t);return "hand_bread_fetching";}
  for(var b:stores(e))if(!b.equals(source)&&spare(l,e,b)>0){t.putUUID("source",b.id());NbtRecord.write(file,t);return "hand_bread_fetching";}
  // No wheat anywhere (override 11): the whole units already paid for are baked and the rest goes back; a job that took nothing simply ends.
  int paid=t.getInt("paid");t.remove("source");
  if(paid==0){t.putString("stage","idle");NbtRecord.write(file,t);return "hand_bread_missing_wheat";}
  int units=paid/WHEAT_PER_UNIT;t.putInt("units",units);t.putInt("wheat",units*WHEAT_PER_UNIT);t.putInt("bread",units*BREAD_PER_UNIT);t.putInt("rest",paid-units*WHEAT_PER_UNIT);t.putLong("needLabor",(long)units*BREAD_PER_UNIT*LABOR_PER_BREAD);
  t.putString("stage",units>0?"work":"output");NbtRecord.write(file,t);return units>0?"hand_bread_working":"hand_bread_output";
 }
 // ---- one baker at a time --------------------------------------------------------------------------
 /** In memory only: after a restart any idle adult may take the job over, which is durable on its own. */
 private record Claim(UUID baker,long time){}
 private static final Map<MinecraftServer,Map<UUID,Claim>> CLAIMS=new WeakHashMap<>();
 private static final Map<MinecraftServer,Map<UUID,Long>> RETRY=new WeakHashMap<>();
 private static Map<UUID,Long> retries(ServerLevel l){return RETRY.computeIfAbsent(l.getServer(),k->new HashMap<>());}
 private static Map<UUID,Claim> claims(ServerLevel l){return CLAIMS.computeIfAbsent(l.getServer(),k->new HashMap<>());}
 /** Override 18: only a loaded, living resident of this very village, in its world, bakes — nobody walking out with a player or still rowing ashore
  *  after being let go, and no guest (no village, or not one of its residents). */
 public static boolean mayBake(ServerLevel l,UUID settlement,UUID baker){
  var e=SettlementData.get(l.getServer()).entry(settlement);var r=e==null?null:e.settlement().resident(baker);
  return r!=null&&r.alive()&&e.dimension().equals(l.dimension().location().toString())&&l.getEntity(baker) instanceof ResidentEntity npc&&npc.isAlive()&&settlement.equals(npc.settlementId())&&npc.escortPlayer()==null&&!npc.releasing();
 }
 /** Nobody refreshed it for LOCK_TICKS, or its holder may not bake any more: dead, not loaded in this level, walking out with a player, let go in a boat. */
 private static boolean stale(ServerLevel l,UUID settlement,Claim c,long now){return now-c.time()>LOCK_TICKS||!mayBake(l,settlement,c.baker());}
 /** This baker may take the village's hand bread: it may bake at all, and nobody holds it, it holds it itself, or the claim went stale. Times are game ticks. */
 public static boolean mayClaim(ServerLevel l,UUID settlement,UUID baker,long now){var c=claims(l).get(settlement);return now>=retries(l).getOrDefault(baker,Long.MIN_VALUE)&&mayBake(l,settlement,baker)&&(c==null||c.baker().equals(baker)||stale(l,settlement,c,now));}
 /** Takes or refreshes the claim; false while another baker holds it, or when this one may not bake. */
 public static boolean claim(ServerLevel l,UUID settlement,UUID baker,long now){if(!mayClaim(l,settlement,baker,now))return false;claims(l).put(settlement,new Claim(baker,now));return true;}
 public static boolean holds(ServerLevel l,UUID settlement,UUID baker,long now){var c=claims(l).get(settlement);return c!=null&&c.baker().equals(baker)&&!stale(l,settlement,c,now);}
 /** An unreachable baker releases only its temporary claim; all paid work remains the village's. */
 public static UUID claimedBaker(ServerLevel l,UUID settlement){var all=CLAIMS.get(l.getServer());var claim=all==null?null:all.get(settlement);return claim==null?null:claim.baker();}
 public static void defer(ServerLevel l,UUID settlement,UUID baker,long now,int ticks){
  release(l,settlement,baker);var retry=retries(l);retry.entrySet().removeIf(entry->entry.getValue()<=now);retry.put(baker,now+ticks);
 }
 public static void release(ServerLevel l,UUID settlement,UUID baker){var m=claims(l);var c=m.get(settlement);if(c!=null&&c.baker().equals(baker))m.remove(settlement);}
}
