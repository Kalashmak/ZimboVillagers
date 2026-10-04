package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** Material-funded, durable hall renovation. Development command queues work; it never places the building itself. */
public final class HallUpgradeGoal extends Goal {
 private final ResidentEntity worker;private CompoundTag state;private Path file;private BlockPos base,hallStock;private int labor,modeIndex=-1,stuckIndex=-1,stuckTicks;private boolean modeHigh;private BlockPos exitTo;private double lastX,lastZ;private int idleTicks,idleChecks;
 private BlockPos escapeStep,escapeRegion;private int escapeUntil;private final Set<BlockPos> escapeRefused=new HashSet<>();
 private boolean escapeFurniture(BlockPos goal){
  if(escapeRegion==null||escapeRegion.distSqr(worker.blockPosition())>16){escapeRegion=worker.blockPosition();escapeRefused.clear();}
  if(escapeStep!=null&&(worker.tickCount>=escapeUntil||worker.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(escapeStep))<.36)){escapeRefused.add(escapeStep);escapeStep=null;idleChecks=0;idleTicks=0;}
  if(escapeStep==null&&idleChecks>=6&&worker.onGround()&&!worker.onClimbable()){escapeStep=ConstructionEscape.raised(worker,goal,escapeRefused);escapeUntil=worker.tickCount+80;}
  if(escapeStep==null)return false;
  ConstructionEscape.walk(worker,escapeStep);walkDiag="physical furniture escape to "+escapeStep.toShortString();lastWalk=walkDiag;return true;
 }
 public HallUpgradeGoal(ResidentEntity worker){this(worker,false);}
 /** GameTests: a headless goal works without a player on the server (the GameTest server has none); everything else is the real builder. */
 private final boolean headless;
 public HallUpgradeGoal(ResidentEntity worker,boolean headless){this.worker=worker;this.headless=headless;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 /** AD-153: this builder's reach (squared) and bag by the village's construction research, read when the goal starts; helper: he works a
  *  project another builder of the hall leads (Construction I..VI: 2..10 builders share one project). */
 private double reachSq=BuildingOrders.REACH_SQ;private int bag=1;private boolean helper;
 /** AD-153: the builders of a village share one copy of the project in memory, so what one of them does the others see at once; a copy is
  *  read again when the file changed behind their back (a test, a probe, the mayor's orders, cargo custody). */
 private record Shared(CompoundTag state,java.nio.file.attribute.FileTime time){}
 private static final Map<Path,Shared> SHARED=new java.util.concurrent.ConcurrentHashMap<>();
 private static java.nio.file.attribute.FileTime time(Path p){try{return Files.getLastModifiedTime(p);}catch(IOException e){return null;}}
 private static CompoundTag shared(Path p){var t=time(p);var s=SHARED.get(p);if(s!=null&&t!=null&&t.equals(s.time()))return s.state();var state=read(p);SHARED.put(p,new Shared(state,t));return state;}
 /** AD-153: which builder works which operation now — an operation one of them has in hand is not taken by another (claims last two seconds
  *  and are renewed every tick of work, so a builder who stops lets his go). In memory, by project and operation. */
 private record Claim(UUID worker,long until){}
 private static final Map<String,Claim> CLAIMS=new java.util.concurrent.ConcurrentHashMap<>();
 private boolean claimedByOther(UUID project,int op,long now){var c=CLAIMS.get(project+"/"+op);return c!=null&&c.until()>=now&&!c.worker().equals(worker.getUUID());}
 private void claim(UUID project,int op,long now){CLAIMS.put(project+"/"+op,new Claim(worker.getUUID(),now+40));}
 /** GameTests: whether some builder holds this operation of this project now. */
 public static UUID claimant(UUID project,int op,long now){var c=CLAIMS.get(project+"/"+op);return c!=null&&c.until()>=now?c.worker():null;}
 /** The builders who may work the village's project now (Construction: 1..10), ordered by id, those out on a trail left out. */
 public static List<UUID> crew(ServerLevel l,SettlementData.Entry e){
  return e.settlement().residents().stream().filter(x->x.alive()&&x.profession()==Profession.BUILDER&&!Trails.onTrail(l,e,x.id())).map(Resident::id).sorted().limit(Math.max(1,ResearchKnobs.builders(l,e))).toList();}
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+village+".bin");}
 private static CompoundTag read(Path p){try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(p))));}catch(IOException e){throw new IllegalStateException(e);}}
 private static void write(Path p,CompoundTag t){try{var bytes=new ByteArrayOutputStream();NbtIo.write(t,new DataOutputStream(bytes));AtomicRecord.write(p,bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
 public static boolean recoverCompleted(ServerLevel level,SettlementData.Entry entry){
  Path file=path(level,entry.settlement().id());if(!Files.exists(file))return false;var t=read(file);
  // AD-125: a moved building is registered at its new place once; the record keys on "moved", not on "complete".
  if(t.getBoolean("relocate"))return Relocations.recover(level,entry,t);
  if(BuildingOrders.isBuilding(t))return t.getBoolean("complete")&&entry.settlement().buildings().stream().noneMatch(b->b.id().equals(BuildingOrders.buildingId(t)))&&BuildingOrders.complete(level,entry,t);
  if(t.getBoolean("complete")&&entry.settlement().civilization().level()==t.getInt("level")-1){entry.settlement().civilization().completedHallUpgrade(t.getInt("level"));return true;}return false;
 }
 public static boolean exists(ServerLevel level,UUID village){return Files.exists(path(level,village));}
 /** AD-125: headless runs (GameTests, probes) write the project back the way the builder's own save does. */
 public static void store(ServerLevel level,UUID village,CompoundTag state){write(path(level,village),state.copy());}
 /** Probe fixture: applies the first operations of a queued project directly and advances the record, so a client run can start near the interesting part. */
 public static int advanceForProbe(ServerLevel level,UUID village,int operations){
  var file=path(level,village);if(!Files.exists(file))return 0;var state=read(file);var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  int applied=0;
  for(int i=state.getInt("index");i<Math.min(ops.size(),operations);i++){
   var op=ops.getCompound(i);
   // Probe shortcut: the fixture only has to reproduce the world of the skipped operations, not their bookkeeping.
   if(!BuildingOrders.reconcile(level,op,origin)){var drift=HallConstructionPlan.step(op);level.setBlock(drift.pos(),drift.after(),3);state.putInt("index",i+1);applied++;continue;}
   var step=HallConstructionPlan.step(op);
   if(!step.before().equals(step.after())&&!org.villageastra.persistence.WorldJournal.place(level,Settlement.childId(id,"probe/"+i),step.pos(),step.before(),step.after()))break;
   state.putInt("index",i+1);applied++;}
  NbtRecord.write(file,state);return applied;
 }
 public static boolean pending(ServerLevel level,UUID village){var file=path(level,village);return Files.exists(file)&&!read(file).getBoolean("complete");}
 public static CompoundTag inspect(ServerLevel level,UUID village){return read(path(level,village)).copy();}
 /** AD-078: the crew's own housekeeping gives way — a repair nobody has started and nothing has been fetched for
  *  yields the settlement's single construction slot to what the mayor orders. */
 public static boolean yields(ServerLevel level,UUID village){
  if(!pending(level,village))return false;var t=inspect(level,village);
  return t.getBoolean("repair")&&t.getInt("index")==0&&t.getList("cargo",Tag.TAG_COMPOUND).isEmpty();
 }
 /** Why the queued project cannot be called off now, or empty. Only a project nobody has started can be: what is built stays built. */
 public static String cancellable(ServerLevel level,UUID village,UUID project){
  if(!pending(level,village))return "none";var t=inspect(level,village);
  if(!HallConstructionPlan.projectId(t).equals(project))return "project";
  if(t.getInt("index")>0||!t.getList("cargo",Tag.TAG_COMPOUND).isEmpty())return "started";
  return "";
 }
 /** Drops the queued project: no block changes, nothing is paid back, the crew simply has nothing queued again. */
 public static void drop(ServerLevel level,UUID village){try{Files.deleteIfExists(path(level,village));}catch(IOException ex){throw new IllegalStateException(ex);}}
 /** Frees the slot when the only thing in it is the crew's own repair. */
 public static boolean yield(ServerLevel level,UUID village){if(!yields(level,village))return false;drop(level,village);return true;}
 /** One active construction project per settlement: a hall upgrade or a field-ordered building (AD-028). */
 public static void enqueue(ServerLevel l,SettlementData.Entry e,CompoundTag state){if(pending(l,e.settlement().id()))throw new IllegalStateException("Construction project already queued");write(path(l,e.settlement().id()),state.copy());}
 public static void request(ServerLevel l,SettlementData.Entry e){
  write(path(l,e.settlement().id()),preview(l,e));
 }
 /** Pure survey: no block, inventory or execution queue is changed. */
 public static CompoundTag preview(ServerLevel l,SettlementData.Entry e){
  int level=e.settlement().civilization().level();if(level>=3)throw new IllegalStateException("Maximum hall level");
  Path file=path(l,e.settlement().id());if(Files.exists(file)&&!read(file).getBoolean("complete"))throw new IllegalStateException("Hall project already queued");
  // AD-121: a castle village's hall is the castle, laid from its lot's corner (HallSite.base); the 7x7 hall from the centre, as before.
  var s=e.settlement();boolean castle=HallSite.castle(s);var base=HallSite.base(e);
  var old=BuildingBlueprints.layout(BuildingTiers.layoutId(s,"town_hall",level),base);var next=BuildingBlueprints.layout(BuildingTiers.layoutId(s,"town_hall",level+1),base);
  var hall=Workshops.hall(e);var plaque=hall==null?null:BuildingSigns.position(e,hall);if(plaque!=null&&BuildingSigns.owned(l,e,hall,plaque))next.putIfAbsent(plaque,Blocks.AIR.defaultBlockState());
  ListTag operations=new ListTag();CompoundTag cost=new CompoundTag();
  var entries=new ArrayList<>(next.entrySet());
  // The castle: what is cleared first, then course by course up from the ground, nearest the seal first.
  if(castle)entries.sort(Comparator.<Map.Entry<BlockPos,BlockState>>comparingInt(x->x.getValue().isAir()?0:1).thenComparingInt(x->x.getKey().getY()).thenComparingDouble(x->x.getKey().distSqr(e.center())));
  else entries.sort(Comparator.<Map.Entry<BlockPos,BlockState>>comparingInt(x->
    x.getKey().getX()==e.center().getX()+5 && (x.getKey().getZ()==e.center().getZ()+5||x.getKey().getZ()==e.center().getZ()+6)
        &&x.getKey().getY()>e.center().getY()+4&&x.getKey().getY()<=e.center().getY()+(level+1)*4?0:x.getValue().isAir()?1:2)
    .thenComparingInt(x->x.getKey().getY()).thenComparingInt(x->x.getKey().getZ()==e.center().getZ()+6?0:1)
    .thenComparingDouble(x->x.getKey().distSqr(e.center().offset(5,5,5))));
  entries.sort(Comparator.comparingInt(x->x.getValue().getBlock() instanceof net.minecraft.world.level.block.SignBlock?1:0));
  for(var cell:entries){
   BlockPos pos=cell.getKey();if(!l.hasChunkAt(pos)||l.isOutsideBuildHeight(pos))throw new IllegalStateException("Unloaded upgrade volume");
   var before=l.getBlockState(pos);var after=cell.getValue();
   if(after.isAir()&&HallStorage.partAt(l,pos))continue;
   if(before.equals(after)||BuildingRepairs.present(before,after))continue;
   if(l.getBlockEntity(pos)!=null&&!BuildingSigns.owned(l,e,hall,pos)||!before.getFluidState().isEmpty()||!before.equals(old.getOrDefault(pos,Blocks.AIR.defaultBlockState())))throw new IllegalStateException("Hall is damaged or upgrade volume changed");
   // AD-112: the hall's core cell is a chain of one-item operations (the seal at II, ring III at III), like any building's core.
   List<BlockState[]> links=after.getBlock() instanceof BuildingCoreBlock?BuildingOrders.coreChain(before,after,BuildingTiers.MAX):List.<BlockState[]>of(new BlockState[]{before,after});
   for(var link:links){
    CompoundTag op=new CompoundTag();op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(link[0]));op.put("after",NbtUtils.writeBlockState(link[1]));
    // An upper door half or a bed's head comes with its other half (BuildingOrders.material): no item of its own (the castle's new doors).
    if(!link[1].isAir()&&"".equals(BuildingOrders.material(link[1]))){operations.add(op);continue;}
    if(!link[1].isAir()){
     var items=BuildingOrders.materials(link[0],link[1]);if(items==null||items.size()!=1||items.get(0).isEmpty())throw new IllegalStateException("Missing building material for "+link[1]+" at "+pos);
     String item=items.get(0);op.putString("item",item);cost.putInt(item,cost.getInt(item)+1);
    }
    operations.add(op);}
  }
  CompoundTag state=new CompoundTag();state.putInt("schema",1);state.putUUID("id",UUID.randomUUID());state.putUUID("project",state.getUUID("id"));state.putInt("level",level+1);state.put("ops",operations);state.put("cost",cost);state.put("cargo",new ListTag());
  return state;
 }
 public static boolean approve(ServerLevel l,SettlementData.Entry e,CompoundTag quoted){
  var current=preview(l,e);
  if(current.getInt("level")!=quoted.getInt("level")||!current.get("ops").equals(quoted.get("ops"))||!current.get("cost").equals(quoted.get("cost")))return false;
  write(path(l,e.settlement().id()),quoted.copy());return true;
 }
 private static final Map<UUID,long[]> WAITING=new HashMap<>();
 /** AD-094: the queued project waits for materials the hall does not have — then the crew does road and wall work meanwhile instead of
  *  standing at the hall. Looked at no more than once a second per village. */
 public static boolean waiting(ServerLevel l,SettlementData.Entry e){
  var id=e.settlement().id();long now=l.getGameTime();var seen=WAITING.get(id);if(seen!=null&&now-seen[0]>=0&&now-seen[0]<20)return seen[1]==1;
  var file=path(l,id);boolean waits=false;
  if(Files.exists(file)){var state=read(file);waits=!state.getBoolean("complete")&&!state.getBoolean("funded")&&fundingBlocked(l,e,state);}
  WAITING.put(id,new long[]{now,waits?1:0});return waits;
 }
 /** Yield only when no unpaid material can be collected; a committed withdrawal is always recovered first. */
 static boolean fundingBlocked(ServerLevel l,SettlementData.Entry e,CompoundTag state){
  if(!(l.getBlockEntity(HallSite.stock(e)) instanceof Container chest))return false;
  if(!WorldJournal.recoverAmount(l,Settlement.childId(state.getUUID("id"),"fund/"+state.getInt("withdrawals"))).isEmpty())return false;
  var missing=ConstructionFunding.missing(state);
  return !missing.isEmpty()&&ConstructionFunding.slot(chest,missing)<0;
 }
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public boolean canUse(){
  if(!(worker.level() instanceof ServerLevel l)||worker.settlementId()==null||worker.escortPlayer()!=null||!headless&&l.getServer().getPlayerCount()==0)return false;
  if(!CargoCustody.mayStartWork(worker))return false;
  var data=SettlementData.get(l.getServer());var e=data.entry(worker.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;
  var r=e.settlement().resident(worker.getUUID());if(r==null||!r.alive()||r.profession()!=Profession.BUILDER)return false;
  // AD-123: the builder out with the trail crew is not the one who takes up a hall project.
  if(Trails.onTrail(l,e,worker.getUUID())){worker.workStatus("on_trail");return false;}
  // AD-043: a besieged settlement starts and continues no construction work.
  if(Sieges.besieged(l.getServer(),e.settlement().id())){worker.workStatus("besieged");return false;}
  file=path(l,e.settlement().id());if(!Files.exists(file))return false;state=shared(file);base=HallSite.base(e);hallStock=HallSite.stock(e);if(e.settlement().governance().paused(HallConstructionPlan.projectId(state))){worker.workStatus("paused_by_mayor");return false;}
  reachSq=BuildingOrders.workReachSq(ResearchKnobs.reach(l,e));bag=Math.max(1,ResearchKnobs.bag(l,e));helper=false;
  if(state.hasUUID("worker")){if(!state.getUUID("worker").equals(worker.getUUID())){
   // AD-153: another builder leads the project; one of the hall's crew helps with its blocks once it is paid for. The lead alone funds it,
   // carries the leftovers back and registers the building; a move (AD-125) and the hall's own upgrade stay one builder's work.
   if(!BuildingOrders.isBuilding(state)||!state.getBoolean("funded")||state.getBoolean("complete")||state.getBoolean("relocate")
     ||state.getInt("index")>=state.getList("ops",Tag.TAG_COMPOUND).size()||!crew(l,e).contains(worker.getUUID()))return false;
   helper=true;return true;}}
  else {var first=e.settlement().residents().stream().filter(x->x.alive()&&x.profession()==Profession.BUILDER&&!Trails.onTrail(l,e,x.id())).map(Resident::id).sorted().findFirst();if(first.isEmpty()||!first.get().equals(worker.getUUID()))return false;state.putUUID("worker",worker.getUUID());save();}
  if(!state.getBoolean("complete")&&!state.getBoolean("funded")&&fundingBlocked(l,e,state)){worker.workStatus("missing_building_materials");return false;}
  if(state.getBoolean("complete")){if(!BuildingOrders.isBuilding(state)&&e.settlement().civilization().level()==state.getInt("level")-1){e.settlement().civilization().completedHallUpgrade(state.getInt("level"));data.setDirty();}return false;}
  return true;
 }
 @Override public boolean canContinueToUse(){
  // A project the mayor called off (AD-078), or that gave way to another, is let go at once: nobody works on from a stale copy of it.
  // The goal selector looks at a resident only every other tick, on its own parity, so the check takes a window of two ticks
  // (wall-client-05: with %20==0 it never came up for a builder of the other parity).
  if(worker.tickCount%20<2&&!current())return false;
  if(helper&&(!state.getBoolean("funded")||state.getInt("index")>=state.getList("ops",Tag.TAG_COMPOUND).size()))return false;
  if(!worker.isAlive()||worker.escortPlayer()!=null||state.getBoolean("complete")||!headless&&worker.getServer().getPlayerCount()==0||CargoCustody.pending(worker.getServer(),worker.getUUID()))return false;
  // AD-043: a siege stops the work already under way too, not only its start.
  if(Sieges.besieged(worker.getServer(),worker.settlementId())){worker.workStatus("besieged");return false;}
  var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(!(e!=null&&!e.settlement().governance().paused(HallConstructionPlan.projectId(state))&&e.dimension().equals(worker.level().dimension().location().toString())&&e.settlement().resident(worker.getUUID())!=null&&e.settlement().resident(worker.getUUID()).profession()==Profession.BUILDER))return false;
  // AD-094: funding stuck on an item the hall chest lacks lets the builder go, as canUse would, so RoadWorkGoal (it asks waiting) takes him meanwhile.
  if(worker.tickCount%20<2&&!state.getBoolean("funded")&&worker.level() instanceof ServerLevel l&&fundingBlocked(l,e,state)){worker.workStatus("missing_building_materials");return false;}
  return true;
 }
 @Override public void stop(){worker.displayWorkItem(ItemStack.EMPTY);}
 /** Whether the project this builder holds is still the one queued. */
 private boolean current(){return file!=null&&state!=null&&Files.exists(file)&&HallConstructionPlan.projectId(shared(file)).equals(HallConstructionPlan.projectId(state));}
 /** Writes the held project back — never over a project that was called off or replaced meanwhile. The shared copy (AD-153) is this one. */
 private void save(){if(current()){write(file,state);SHARED.put(file,new Shared(state,time(file)));}}
 private int held(String key){int count=0;for(Tag t:state.getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)t);if(BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(key))count+=item.getCount();}return count;}
 private void consume(String key){var cargo=state.getList("cargo",Tag.TAG_COMPOUND);for(int i=0;i<cargo.size();i++){var stack=ItemStack.of(cargo.getCompound(i));if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(key)&&!stack.isEmpty()){stack.shrink(1);cargo.set(i,stack.save(new CompoundTag()));return;}}throw new IllegalStateException("Missing funded material");}
 @Override public void tick(){
  // AD-153: the copy every builder of the project works on — a file changed behind the crew is read again once, for all of them.
  if(file!=null&&Files.exists(file)){var now=shared(file);if(now!=state&&HallConstructionPlan.projectId(now).equals(HallConstructionPlan.projectId(state)))state=now;}
  var l=(ServerLevel)worker.level();var id=state.getUUID("id");
  // Funding and returns use the same walking recovery as construction. Keep its progress window fresh
  // during those walks too; otherwise a previous project's idle state can keep pushing against navigation forever.
  if(BuildingOrders.isBuilding(state)&&worker.tickCount%40==0){idleTicks=Math.abs(worker.getX()-lastX)+Math.abs(worker.getZ()-lastZ)<1?100:0;idleChecks=idleTicks>0?idleChecks+1:0;lastX=worker.getX();lastZ=worker.getZ();}
  if(helper&&!state.getBoolean("funded"))return;
  if(!state.getBoolean("funded")){
   // relocate-fix: a top-up can fall due while the builder stands high in a scaffold column: he comes down through it first (the hall's
   // hatch ladder below is the hall upgrade's own way down, not a building's — a builder in a column walked at it and hung there).
   if(BuildingOrders.isBuilding(state)&&leaveColumn(l)){worker.workStatus("walking");return;}
   if(!BuildingOrders.isBuilding(state)&&worker.getY()>base.getY()+2){
    var ladder=base.offset(5,1,5);double dx=ladder.getX()+.5-worker.getX(),dz=ladder.getZ()+.5-worker.getZ();
    if(dx*dx+dz*dz>4)worker.getNavigation().moveTo(ladder.getX()+.5,worker.getY(),ladder.getZ()+.5,.8);
    else {worker.getNavigation().stop();worker.setDeltaMovement(Math.max(-.12,Math.min(.12,dx)),worker.onClimbable()?-.2:worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,dz)));}
    worker.workStatus("walking");return;
   }
   var source=hallStock;if(worker.distanceToSqr(source.getX()+1.5,source.getY(),source.getZ()+.5)>6.25){
    if(worker.onClimbable()&&!BuildingOrders.isBuilding(state)){
     double dx=source.getX()+1.5-worker.getX(),dz=source.getZ()+.5-worker.getZ(),length=Math.max(1,Math.sqrt(dx*dx+dz*dz));
     worker.getNavigation().stop();worker.setDeltaMovement(dx/length*.12,-.1,dz/length*.12);
    }else if(BuildingOrders.isBuilding(state))toChest(source);else worker.getNavigation().moveTo(source.getX()+1.5,source.getY(),source.getZ()+.5,.8);return;
   }
   // Nothing is taken for a project called off or replaced meanwhile: canContinueToUse may not come up on this tick's parity.
   worker.getNavigation().stop();if(worker.tickCount%20!=0||!current())return;
   // AD-153 (Construction II): the builder's bag takes three stacks a visit where the plain builder takes one.
   for(int load=0;load<bag;load++){
    var missing=ConstructionFunding.missing(state);
    if(missing.isEmpty()){state.putBoolean("funded",true);save();return;}
    UUID take=Settlement.childId(id,"fund/"+state.getInt("withdrawals"));ItemStack material=WorldJournal.recoverAmount(l,take);
    if(material.isEmpty()&&!WorldJournal.exists(l,take)&&l.getBlockEntity(source) instanceof Container c){int slot=ConstructionFunding.slot(c,missing);if(slot>=0){var stack=c.getItem(slot);material=WorldJournal.takeAmount(l,take,source,slot,stack.copy(),Math.min(stack.getCount(),missing.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())));}}
    if(material.isEmpty()){if(load==0)worker.workStatus("missing_building_materials");return;}
    state.getList("cargo",Tag.TAG_COMPOUND).add(material.save(new CompoundTag()));state.putInt("withdrawals",state.getInt("withdrawals")+1);save();
   }
   return;
  }
  if(BuildingOrders.isBuilding(state)){tickBuilding(l,id);return;}
  var operations=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");
  if(index>=operations.size()){
   var village=SettlementData.get(l.getServer()).entry(worker.settlementId());var expected=BuildingBlueprints.layout(village==null?"town_hall_"+state.getInt("level"):BuildingTiers.layoutId(village.settlement(),"town_hall",state.getInt("level")),base);
   for(var cell:expected.entrySet())if(!cell.getValue().isAir()&&!BuildingRepairs.present(l.getBlockState(cell.getKey()),cell.getValue())){worker.workStatus("changed_target");return;}
   state.putBoolean("complete",true);save();var data=SettlementData.get(l.getServer());var civ=data.entry(worker.settlementId()).settlement().civilization();if(civ.level()==state.getInt("level")-1)civ.completedHallUpgrade(state.getInt("level"));data.setDirty();worker.workStatus("construction_complete");return;
  }
  var op=operations.getCompound(index);worker.displayWorkItem(op.contains("item")?new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(op.getString("item")))):ItemStack.EMPTY);var planned=HallConstructionPlan.step(op);BlockPos target=planned.pos();
  if(worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16){
   int floor=base.getY()+1+4*Math.max(0,(target.getY()-base.getY()-1)/4);
   BlockPos ladder=base.offset(5,1,5);
   if(worker.getY()<floor-.1){
    if(worker.distanceToSqr(ladder.getX()+.5,worker.getY(),ladder.getZ()+.5)>4)worker.getNavigation().moveTo(ladder.getX()+.5,Math.max(ladder.getY(),worker.getY()),ladder.getZ()+.5,.8);
    else {
        worker.getNavigation().stop();double dx=ladder.getX()+.5-worker.getX(),dz=ladder.getZ()+.5-worker.getZ();
        worker.setDeltaMovement(Math.max(-.12,Math.min(.12,dx)),worker.onClimbable()?.2:worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,dz)));
    }
   }else if(worker.tickCount%10==0){
    BlockPos best=null;double distance=Double.MAX_VALUE;
    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
     BlockPos stand=new BlockPos(target.getX()+dx,floor,target.getZ()+dz);
     if(new net.minecraft.world.phys.Vec3(stand.getX()+.5,stand.getY()+worker.getEyeHeight(),stand.getZ()+.5).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))<=16 && l.getBlockState(stand).getCollisionShape(l,stand).isEmpty()&&l.getBlockState(stand.above()).getCollisionShape(l,stand.above()).isEmpty()&&!l.getBlockState(stand.below()).getCollisionShape(l,stand.below()).isEmpty()){
      double d=stand.distSqr(target)+.01*stand.distSqr(worker.blockPosition());
      var path=worker.getNavigation().createPath(stand,0);
      if(d<distance && (worker.onClimbable() || path!=null&&path.canReach())){best=stand;distance=d;}
     }
    }
    if(best!=null){
     if(worker.onClimbable()){
      double dx=best.getX()+.5-worker.getX(),dz=best.getZ()+.5-worker.getZ();double length=Math.max(1,Math.sqrt(dx*dx+dz*dz));
      worker.getNavigation().stop();worker.setDeltaMovement(dx/length*.15,0,dz/length*.15);
     }else worker.getNavigation().moveTo(best.getX()+.5,best.getY(),best.getZ()+.5,.8);
    }
   }
   worker.workStatus("walking");return;
  }
  var nextBlock=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),op.getCompound("after"));
  var shape=nextBlock.getCollisionShape(l,target);
  if(!shape.isEmpty()&&shape.bounds().move(target).intersects(worker.getBoundingBox())){
   for(var direction:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
    var stand=worker.blockPosition().relative(direction);
    if(l.getBlockState(stand).getCollisionShape(l,stand).isEmpty()&&l.getBlockState(stand.above()).getCollisionShape(l,stand.above()).isEmpty()&&!l.getBlockState(stand.below()).getCollisionShape(l,stand.below()).isEmpty()){
     worker.getNavigation().moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5,.8);worker.workStatus("clearing_work_position");return;
    }
   }
   worker.workStatus("needs_access");return;
  }
  worker.getNavigation().stop();if(worker.onClimbable())worker.setDeltaMovement(0,0,0);if(++labor<20)return;labor=0;
  var before=planned.before();var after=planned.after();
  if(!coreResearched(l,"town_hall",after)){worker.workStatus("core_research");return;}
  if(!WorldJournal.place(l,Settlement.childId(id,"block/"+index),target,before,after)){worker.workStatus("changed_target");return;}
  if(op.contains("item"))consume(op.getString("item"));state.putInt("index",index+1);save();worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
 }
 /** AD-112: whether a core state may be set now — not a core, or the research of every level up to its grade is done for its building type
  *  (for the hall, its civic levels). Read at most once a second per builder; in between the last answer holds. */
 private boolean coreResearched(ServerLevel l,String type,BlockState after){
  if(!(after.getBlock() instanceof BuildingCoreBlock))return true;
  if(!type.equals(researchedType)||worker.tickCount-researchedAt>=20||worker.tickCount<researchedAt){var e=SettlementData.get(l.getServer()).entry(worker.settlementId());researched=e==null?1:BuildingTiers.researchedGrade(l,e,type);researchedAt=worker.tickCount;researchedType=type;}
  return after.getValue(BuildingCoreBlock.GRADE)<=researched;
 }
 private int researched=1,researchedAt;private String researchedType;
 /** Nearest reachable position from which the target is within 4 blocks and which the new block will not occupy. */
 /** Diagnostics of the last stand search: candidate count, path results and the worker's own cell. */
 public static volatile String lastStand="";
 /** Diagnostics of the operation the builder is actually working on, which is not always the queue pointer. */
 public static volatile String lastOp="";
 /** This builder's own last operation and stand search (the statics above are shared by every builder on the server). */
 public volatile String opDiag="",standDiag="";
 private BlockPos stand(ServerLevel l,BlockPos target,int[] levels){var found=stand(l,worker,target,levels,reachSq);standDiag=lastStand;return found;}
 /** Work position for one operation: a standable cell in reach of the block, actually reachable by the worker's own pathfinding. */
 public static BlockPos stand(ServerLevel l,ResidentEntity worker,BlockPos target,int[] levels){return searchStand(l,worker,target,levels,BuildingOrders.REACH_SQ,false);}
 /** AD-153: the same at a longer reach (Construction IV/VI). */
 public static BlockPos stand(ServerLevel l,ResidentEntity worker,BlockPos target,int[] levels,double reachSq){return searchStand(l,worker,target,levels,reachSq,true);}
 private static BlockPos searchStand(ServerLevel l,ResidentEntity worker,BlockPos target,int[] levels,double reachSq,boolean nearbyWork){
  var candidates=new ArrayList<BlockPos>();int r=(int)Math.floor(Math.sqrt(reachSq))-1;
  for(int y:levels)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
   var stand=new BlockPos(target.getX()+dx,y,target.getZ()+dz);
   if(stand.equals(target)||stand.above().equals(target))continue;
   if(new net.minecraft.world.phys.Vec3(stand.getX()+.5,y+worker.getEyeHeight(),stand.getZ()+.5).distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>reachSq||!standable(l,stand))continue;
   candidates.add(stand);
  }
  // Every candidate already satisfies the physical reach limit. Prefer the
  // worker's nearest legal position, not the cell directly under a high roof:
  // that cell can require a long detour while one short step already suffices.
  candidates.sort(Comparator.comparingDouble(stand->nearbyWork?stand.distToCenterSqr(worker.getX(),worker.getY()+.5,worker.getZ())+.01*stand.distSqr(target):stand.distSqr(target)+.01*stand.distSqr(worker.blockPosition())));
  // Navigation only searches for a mob it considers grounded, and that flag is stale while goals run before movement.
  // Scaffold tops carry the worker too, so a cell inside a column is a standing position and not "airborne".
  var floor=worker.blockPosition().below();
  boolean standing=l.getBlockState(floor).isFaceSturdy(l,floor,Direction.UP)&&Math.abs(worker.getY()-worker.blockPosition().getY())<.2;
  if(!worker.onGround()&&standing)worker.setOnGround(true);
  if(!worker.onGround()){lastStand="airborne at "+worker.blockPosition().toShortString()+" climb="+worker.onClimbable();return null;}
  int paths=0,reach=0;
  for(int i=0;i<Math.min(12,candidates.size());i++){var path=ConstructionRoutes.plan(worker,candidates.get(i));if(path!=null)paths++;if(path!=null&&path.canReach()){lastStand="stand="+candidates.get(i).toShortString()+" from="+worker.blockPosition().toShortString()+" tried="+i;return candidates.get(i);}}
  lastStand="candidates="+candidates.size()+" paths="+paths+" reach="+reach+" ground="+worker.onGround()+" first="+(candidates.isEmpty()?"none":candidates.get(0).toShortString())+" at="+worker.blockPosition().toShortString()+"="+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(worker.blockPosition()).getBlock()).getPath()+" climb="+worker.onClimbable();
  return null;
 }
 /** A cell a worker can occupy: a scaffold only carries whoever comes from above, so from inside and below it is free space. */
 private static boolean free(ServerLevel l,BlockPos p){var s=l.getBlockState(p);return s.is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||s.getCollisionShape(l,p).isEmpty();}
 /** A straight step from the worker to the centre of a cell that crosses no solid block. */
 private static boolean clearLine(ServerLevel l,ResidentEntity worker,BlockPos cell){
  // Farmland and paths support the feet at 15/16 of a block. Sampling the
  // containing cell mistakes that supporting soil for a wall across the step.
  for(double t=.2;t<=1;t+=.2){var body=worker.getBoundingBox().move((cell.getX()+.5-worker.getX())*t,(cell.getY()-worker.getY())*t,(cell.getZ()+.5-worker.getZ())*t);
   if(!l.noCollision(worker,body))return false;}
  return true;
 }
 /** Ordinary short approach to a work stand, including when construction invalidated a cached route. */
 public static boolean stepToWorkStand(ServerLevel l,ResidentEntity worker,BlockPos best){
  double dx=best.getX()+.5-worker.getX(),dz=best.getZ()+.5-worker.getZ();
  if(dx*dx+dz*dz>2.25*2.25||Math.abs(best.getY()-worker.getY())>=.6||!standable(l,best)||crossesColumn(l,worker,best))return false;
  var next=best;
  if(!clearLine(l,worker,best)){
   // Path node acceptance can skip the last fraction of the current cell and
   // aim diagonally through a wall corner. Centre within this free cell first;
   // every part of the step still has to fit the resident's whole body.
   next=new BlockPos(worker.blockPosition().getX(),best.getY(),worker.blockPosition().getZ());
   double cx=next.getX()+.5-worker.getX(),cz=next.getZ()+.5-worker.getZ();
   if(cx*cx+cz*cz<.0025||!standable(l,next)||!clearLine(l,worker,next)||crossesColumn(l,worker,next))return false;
  }
  worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(next.getX()+.5,next.getY(),next.getZ()+.5,.8);return true;
 }
 /** relocate-fix: whether the straight step to a column's foot runs through another column on the way. A scaffold is free space for a line
  *  check, but walking into one lifts the worker up it (a climbable cell) — the builder pushed along the front lane of the AD-129 home into
  *  the column beside his own and hung in it. Such a step goes round by a route instead. */
 private static boolean crossesColumn(ServerLevel l,ResidentEntity worker,BlockPos feet){
  for(double t=.1;t<1;t+=.1){var mid=BlockPos.containing(worker.getX()+(feet.getX()+.5-worker.getX())*t,worker.getY()+.2,worker.getZ()+(feet.getZ()+.5-worker.getZ())*t);
   if(mid.getX()==feet.getX()&&mid.getZ()==feet.getZ())continue;
   if(l.getBlockState(mid).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return true;}
  return false;
 }
 /** One step of leaving a scaffold column towards a free cell; true once the worker stands outside the column. */
 public static boolean stepOut(ServerLevel l,ResidentEntity worker,BlockPos out){
  double dx=out.getX()+.5-worker.getX(),dz=out.getZ()+.5-worker.getZ(),length=Math.max(.0001,Math.sqrt(dx*dx+dz*dz));
  // relocate-fix: the whole step at once wherever the worker's own width fits, both halfway and at the end. By thirds of a block it stayed
  // inside the column for several ticks, and every tick in it it was set back on the column's axis (descend centres it) or carried up it
  // again by the step-up of its own walking against the neighbouring wall: the AD-129 home's builder never came out of a front column.
  var full=new net.minecraft.world.phys.Vec3(dx,0,dz);
  boolean whole=length<=1.9&&l.noCollision(worker,worker.getBoundingBox().move(full))&&l.noCollision(worker,worker.getBoundingBox().move(full.scale(.5)));
  var part=new net.minecraft.world.phys.Vec3(dx/length*Math.min(.3,length),0,dz/length*Math.min(.3,length));
  // relocate-fix: high up in a cell of the column the worker's head is in whatever stands beside it, one storey over the free cell it is
  // stepping out to (the stair over the barrel beside the AD-129 home's front column, turned). Holding it there — the step zeroes its fall
  // every tick — froze it for good. With the step blocked at this height it sinks to the floor of its own cell first and steps out from
  // there. It is never set back on the column's axis, so no sinking undoes a step already taken.
  var at=worker.blockPosition();
  if(!whole&&!l.noCollision(worker,worker.getBoundingBox().move(part))&&worker.getY()-at.getY()>.05){
   worker.getNavigation().stop();worker.setShiftKeyDown(true);worker.setDeltaMovement(0,-.15,0);return false;}
  // The step is a real move with collisions: a worker must never be pushed into a block and buried by the next operation.
  // Its own walking is aimed at the same cell, so the two do not cancel each other out.
  worker.getMoveControl().setWantedPosition(out.getX()+.5,out.getY(),out.getZ()+.5,1);
  worker.move(net.minecraft.world.entity.MoverType.SELF,whole?full:part);
  // The route itself is kept: the step only carries the worker out of the column, it does not cancel where the worker was going.
  worker.setDeltaMovement(0,0,0);worker.setShiftKeyDown(false);worker.setOnGround(true);
  return !l.getBlockState(worker.blockPosition()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get());
 }
 /** Free cell beside a scaffold column for a worker standing inside it, preferring the direction of the work. */
 public static BlockPos exit(ServerLevel l,ResidentEntity worker,BlockPos target){
  // relocate-fix: a cell right beside the column first. A diagonal step out scrapes the corner of whatever stands next to the column with
  // the worker's own width — the straight line between the two centres is free, the worker is not — and a horizontal collision inside a
  // climbable cell carries it back up the column (the crafting table beside the AD-129 home's column).
  var side=exit(l,worker,target,true);return side!=null?side:exit(l,worker,target,false);
 }
 private static BlockPos exit(ServerLevel l,ResidentEntity worker,BlockPos target,boolean beside){
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy>=-1;dy--){
   if(dx==0&&dz==0||beside&&Math.abs(dx)+Math.abs(dz)!=1)continue;var cell=from.offset(dx,dy,dz);
   if(l.getBlockState(cell).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||!standable(l,cell))continue;
   double value=cell.distSqr(target)+.1*(dx*dx+dz*dz);if(value>=score)continue;
   if(clearLine(l,worker,cell)){best=cell;score=value;}
  }
  return best;
 }
 private static boolean standable(ServerLevel l,BlockPos stand){
  // A scaffold cell carries no collision but pathfinding never enters it, so it is not a work position either.
  if(l.getBlockState(stand).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return false;
  // A column overhead does not stop anybody from working below it.
  var support=stand.below();var soil=l.getBlockState(support);
  return l.getBlockState(stand).getCollisionShape(l,stand).isEmpty()&&free(l,stand.above())
   &&(soil.isFaceSturdy(l,support,Direction.UP)||soil.is(Blocks.FARMLAND)||soil.is(Blocks.DIRT_PATH));
 }
 /** New building: ground and floor stands for the lower storey, the temporary hatch ladder for the attic, tools returned before registration. */
 /** Walks to a cell; a worker that stopped making progress (a bed, a slab, a corner where path following gives up) is pushed on by hand. */
 private void walkTo(BlockPos cell,double speed){
  if(escapeFurniture(cell))return;
  var nav=worker.getNavigation();
  // AD-147 review: navigation hands back the route it has for the same goal, however far the worker has been carried off it since (a step
  // out of a column, a push by hand): the builder moving a level-VI warehouse, set down three blocks from the route's next node behind a
  // grindstone, walked at that node for good. A route whose next node lies over two blocks off (level) is planned anew from where the
  // worker stands, once a second — replanning at every tick of such a gap (or of a height gap) kept the level-I warehouse's builder from ever
  // taking the step up to a column two blocks away.
  var held=nav.getPath();
  if(worker.tickCount%20==0&&held!=null&&!held.isDone()){var next=held.getNextNodePos();double hx=next.getX()+.5-worker.getX(),hz=next.getZ()+.5-worker.getZ();
   if(hx*hx+hz*hz>5)nav.stop();}
  if(nav.isDone()||worker.tickCount%10==0)nav.moveTo(ConstructionRoutes.plan(worker,cell),speed);
  if(idleTicks<=40)return;
  var route=nav.getPath();var step=cell;
  // The push always aims forward along the route: the node nearest the worker plus two, never a node it has already passed.
  if(route!=null&&route.getNodeCount()>0){
   int nearest=0;double best=Double.MAX_VALUE;
   for(int i=0;i<route.getNodeCount();i++){var node=route.getNode(i).asBlockPos();
    double value=node.distToCenterSqr(worker.getX(),worker.getY(),worker.getZ());
    if(value<best){best=value;nearest=i;}}
   step=route.getNode(Math.min(route.getNodeCount()-1,nearest+2)).asBlockPos();}
  // relocate-fix: without a route, or pushed against a wall, the push does not go straight at the cell through that wall (a builder in a
  // window hole on the plinth pressed into its post for good): it takes the free neighbouring cell, level or one down, nearest the goal.
  if(route==null||route.getNodeCount()==0||worker.horizontalCollision){var side=sidestep((ServerLevel)worker.level(),worker,cell);if(side!=null)step=side;}
  // A walker that has not got anywhere for six seconds stands on a ledge it cannot leave towards its goal (a plinth in a window bay under
  // the jetty deck): it steps down off it first, whichever way that is, and walks on from the floor.
  if(idleChecks>=3){var off=ledge((ServerLevel)worker.level(),worker,cell);if(off!=null)step=off;}
  // relocate-fix: after twelve seconds with nothing to step onto the worker steps off the edge, whatever is under the cell, and walks on
  // from where it lands. Half way through building a house its floor is a few lone blocks: the builder stood on one of them with a window
  // pane of the wall in its own cell, every cell beside it had air under it, and it pressed east into the pane for good (probe relocate,
  // phase 2 at 776/904). A block or two down onto the site is nothing to a builder; being wedged is.
  if(idleChecks>=6&&worker.horizontalCollision){var off=freeStep((ServerLevel)worker.level(),worker,cell);if(off==null)off=climbOut((ServerLevel)worker.level(),worker,cell);if(off!=null)step=off;}
  // relocate-fix: the push never goes at a scaffold cell. Pathfinding only shuns a column (DANGER_OTHER), it does not refuse one, so a route
  // round the house can still run through the column next door: pushed into it the worker was carried up, stepped out again on the side it
  // came from and started over (the AD-129 home's pair of front columns, with the barrel closing the way round). It takes the route's first
  // node past the column instead, or the free cell beside it.
  var sc=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  if(worker.level().getBlockState(step).is(sc)){
   BlockPos past=null;
   if(route!=null)for(int i=route.getNextNodeIndex();i<route.getNodeCount()&&past==null;i++){var node=route.getNode(i).asBlockPos();if(!worker.level().getBlockState(node).is(sc)&&!node.equals(worker.blockPosition()))past=node;}
   if(past==null)past=sidestep((ServerLevel)worker.level(),worker,cell);
   if(past!=null)step=past;
  }
  // The push by hand is the last thing between a wedged builder and a site that stops: what it aimed at, and what the route said, is read
  // off the failure message of a stalled run.
  walkDiag="to="+cell.toShortString()+" step="+step.toShortString()+" hcol="+worker.horizontalCollision+" checks="+idleChecks
   +" route="+(route==null?"none":route.getNextNodeIndex()+"/"+route.getNodeCount()+(route.getNodeCount()>0?" last="+route.getEndNode().asBlockPos().toShortString():"")+" reach="+route.canReach());
  lastWalk=walkDiag;
  push(step);
 }
 /** This builder's last push by hand (walkTo), for the failure message of a stalled run; the static is what the client probes read. */
 public volatile String walkDiag="";
 public static volatile String lastWalk="";
 /** relocate-fix: a push by hand does not jump, and a building's floor lies a block over the ground around it in a real village — the
  *  builder pressed into the edge of his own lot on the way to a column under the jetty and never got up on it. A push that walks into a
  *  step it could stand on gets the jump its own walking would have. */
 private void hop(double dx,double dz){
  if(!worker.onGround()||!worker.horizontalCollision)return;
  double length=Math.max(.0001,Math.sqrt(dx*dx+dz*dz));var l=(ServerLevel)worker.level();
  var ahead=BlockPos.containing(worker.getX()+dx/length*.7,worker.getY()+.1,worker.getZ()+dz/length*.7);
  if(l.getBlockState(ahead).getCollisionShape(l,ahead).isEmpty())return;
  if(free(l,ahead.above())&&free(l,ahead.above(2)))worker.setDeltaMovement(worker.getDeltaMovement().x,.42,worker.getDeltaMovement().z);
 }
 /** relocate-fix: the walk to the hall chest (the cell east of it) for funding and for the return. A route asked for every tick starts anew
  *  every tick and a path of one node counts as done at once (AD-046): the builder of the warehouse stood 2.3 blocks off the chest with all
  *  its leftovers for good. The route is kept, the last few blocks are direct movement. */
 private void toChest(BlockPos source){
  var cell=source.east();double dx=cell.getX()+.5-worker.getX(),dz=cell.getZ()+.5-worker.getZ();
  if(worker.getNavigation().isDone()&&dx*dx+dz*dz<12.25&&Math.abs(cell.getY()-worker.getY())<1.2)worker.getMoveControl().setWantedPosition(cell.getX()+.5,cell.getY(),cell.getZ()+.5,.8);
  else walkTo(cell,.8);
 }
 /** A builder in a scaffold column comes down through it and steps out beside its foot; false once it stands outside every column. */
 private boolean leaveColumn(ServerLevel l){return leaveColumn(l,BlockPos.of(state.getLong("origin")));}
 /** A builder in a scaffold column that is not his own sinks to its foot and steps out beside it, towards {@code target}; false once he
  *  stands outside every column. He is set down on the foot cell first: a step out from halfway up it put him back in the air, and the
  *  step and the sinking cancelled each other tick by tick (the AD-129 home's front columns beside the barrel). */
 private boolean leaveColumn(ServerLevel l,BlockPos target){
  var sc=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();var at=worker.blockPosition();
  boolean in=l.getBlockState(at).is(sc);
  if(l.getBlockState(at.below()).is(sc)&&(in||!worker.onGround()||Math.abs(worker.getY()-at.getY())<.2)){exitTo=null;descend();return true;}
  if(!in)return false;
  // relocate-fix: in the column's foot cell the worker steps out at once, whatever centimetres it still stands above the floor. Sinking
  // first set it back on the column's axis every tick (descend centres it), which undid the step out of the tick before: it swung between
  // the two for good (AD-129 home, home_2). Only with nowhere to step out to does it still sink to the foot and wait there.
  if(exitTo==null||!standable(l,exitTo))exitTo=exit(l,worker,target);
  if(exitTo==null){if(worker.getY()-at.getY()>.15){descend();return true;}return false;}
  if(stepOut(l,worker,exitTo))exitTo=null;return true;
 }
 /** relocate-fix: where a route that runs through a scaffold column goes on — its first node past the column. Aimed at the column cell itself,
  *  the step out took the worker back where he came from, the route led him in again, and he went to and fro at the column's side for good
  *  (the interior columns of the level-VI expedition). */
 private static BlockPos beyond(ServerLevel l,net.minecraft.world.level.pathfinder.Path route){
  var sc=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  for(int i=route.getNextNodeIndex();i<route.getNodeCount();i++){var node=route.getNode(i).asBlockPos();if(!l.getBlockState(node).is(sc))return node;}
  return route.getNextNodePos();
 }
 /** A standable cell one lower beside the worker, reached by a free step at its own height, nearest the goal; null when it stands on the floor. */
 static BlockPos ledge(ServerLevel l,ResidentEntity worker,BlockPos goal){
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)){var cell=from.relative(d).below();
   if(l.getBlockState(cell).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||!standable(l,cell)||!clearLine(l,worker,from.relative(d)))continue;
   double value=cell.distSqr(goal);if(value<score){score=value;best=cell;}}
  return best;
 }
 /** relocate-fix: a neighbouring cell the worker fits into — nothing to walk into at its feet or its head — nearest the goal, whatever
  *  stands under it. The last resort of a worker with nothing to step onto: it walks off and falls to the floor of the site. */
 static BlockPos freeStep(ServerLevel l,ResidentEntity worker,BlockPos goal){
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)){var cell=from.relative(d);
   if(l.getBlockState(cell).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))continue;
   if(!l.getBlockState(cell).getCollisionShape(l,cell).isEmpty()||!l.getBlockState(cell.above()).getCollisionShape(l,cell.above()).isEmpty())continue;
   double value=cell.distSqr(goal);if(value<score){score=value;best=cell;}}
  return best;
 }
 /** relocate-fix: a cell beside the worker it can get into at all — level, or one higher over something it can climb onto — nearest the
  *  goal. The last resort of a builder boxed in by what it has just set down: on the floor of an AD-129 home the chest and the two beds
  *  close a cell whose every side is taken, and no stand search accepts a bed or a chest to stand on, so nothing else ever offers a way
  *  out. Over them it is one step (a hop, as its own walking would take it) onto open floor. */
 static BlockPos climbOut(ServerLevel l,ResidentEntity worker,BlockPos goal){
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;var sc=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST))for(int dy=0;dy<=1;dy++){
   var side=from.relative(d);var cell=side.above(dy);
   if(l.getBlockState(cell).is(sc)||l.getBlockState(side).is(sc))continue;
   // One higher only over a block to climb on; level only where nothing stands in the way.
   boolean open=l.getBlockState(side).getCollisionShape(l,side).isEmpty();
   if(dy==1?open:!open)continue;
   if(!l.getBlockState(cell).getCollisionShape(l,cell).isEmpty()||!l.getBlockState(cell.above()).getCollisionShape(l,cell.above()).isEmpty())continue;
   double value=cell.distSqr(goal)+dy;if(value<score){score=value;best=cell;}}
  return best;
 }
 /** One push by hand towards a cell, with the jump the worker's own walking would give it at a step. */
 private void push(BlockPos step){
  double dx=step.getX()+.5-worker.getX(),dz=step.getZ()+.5-worker.getZ(),length=Math.max(.0001,Math.sqrt(dx*dx+dz*dz));
  // relocate-fix: after twenty seconds in which no push has moved the worker a block, it is wedged in a way no push can undo — in a window
  // bay on the plinth the glass pane it has just set takes its own cell, and its walking, its route and the push cancel each other out tick
  // by tick (the relocate probe, 775/904). It is then set down, at most once a second, in the very cell it was pushing towards: a step it
  // could have taken itself: the cell right beside its own (one side, at most one level up or down), checked free for its whole body, so
  // nothing can be passed through on the way. A village that stands still for ever is the worse failure.
  var side=step.subtract(worker.blockPosition());
  if(idleChecks>=10&&worker.tickCount%20==0&&Math.abs(side.getX())+Math.abs(side.getZ())==1&&Math.abs(side.getY())<=1){
   var level=(ServerLevel)worker.level();var box=worker.getBoundingBox().move(step.getX()+.5-worker.getX(),step.getY()-worker.getY(),step.getZ()+.5-worker.getZ());
   if(level.noCollision(worker,box)){worker.getNavigation().stop();worker.setPos(step.getX()+.5,step.getY(),step.getZ()+.5);
    worker.setDeltaMovement(0,0,0);worker.setOnGround(true);lastWalk="freed into "+step.toShortString();walkDiag=lastWalk;return;}}
  hop(dx,dz);worker.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(dx/length*.2,0,dz/length*.2));
 }
 /** A standable neighbour of the worker's cell (level or one lower, never a scaffold) reached by a straight free step, nearest the goal. */
 static BlockPos sidestep(ServerLevel l,ResidentEntity worker,BlockPos goal){
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST))for(int dy=0;dy>=-1;dy--){var cell=from.relative(d).above(dy);
   // A step down is checked at the worker's own height first (the ledge it steps off stays under it).
   if(l.getBlockState(cell).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())||!standable(l,cell)||!clearLine(l,worker,from.relative(d)))continue;
   double value=cell.distSqr(goal);if(value<score){score=value;best=cell;}}
  return best;
 }
 private void tickBuilding(ServerLevel l,UUID id){
  var operations=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");
  // Finished operations keep their place in the chain; the pointer only moves over them.
  while(index<operations.size()&&operations.getCompound(index).getBoolean("done"))index++;
  if(index!=state.getInt("index")){state.putInt("index",index);save();}
  var origin=BlockPos.of(state.getLong("origin"));var hatch=BlockPos.of(state.getLong("hatch"));int attic=origin.getY()+BuildingOrders.ATTIC;
  // AD-125 (C12): a moved building takes its new place as soon as phase 2 stands, before the old lot and the leftovers.
  boolean relocate=state.getBoolean("relocate");
  if(relocate&&!state.getBoolean("moved")&&(index>=operations.size()||operations.getCompound(index).getInt("phase")>=3)){
   var entry=SettlementData.get(l.getServer()).entry(worker.settlementId());
   // A lost cell of the copy is undone by complete (the pointer goes back to it): the record is saved so the builders redo it.
   if(entry==null||!Relocations.complete(l,entry,state)){save();worker.workStatus("changed_target");return;}
   state.putBoolean("moved",true);save();return;}
  double hx=hatch.getX()+.5-worker.getX(),hz=hatch.getZ()+.5-worker.getZ();boolean atHatch=hx*hx+hz*hz<.2;
  boolean up=!state.getBoolean("noHatch")&&(worker.getY()>=attic+.2||worker.getY()>=attic-.05&&!worker.onClimbable());
  if(index>=operations.size()){
   // AD-153: the lead carries the leftovers back and registers the building; a helper is done.
   if(helper){if(!leaveColumn(l))worker.workStatus("construction_complete");return;}
   if(up){climbDown(hx,hz,atHatch);return;}
   var cargo=state.getList("cargo",Tag.TAG_COMPOUND);int slot=-1;
   for(int i=0;i<cargo.size();i++)if(!ItemStack.of(cargo.getCompound(i)).isEmpty()){slot=i;break;}
   if(slot>=0){
    var source=hallStock;
    if(worker.distanceToSqr(source.getX()+1.5,source.getY(),source.getZ()+.5)>6.25){if(!leaveColumn(l))toChest(source);worker.workStatus("returning_tools");return;}
    worker.getNavigation().stop();if(worker.tickCount%20!=0)return;
    if(!WorldJournal.deposit(l,Settlement.childId(id,"return/"+state.getInt("returns")),source,ItemStack.of(cargo.getCompound(slot)))){
     var entry=relocate?SettlementData.get(l.getServer()).entry(worker.settlementId()):null;
     if(entry!=null&&Relocations.overflow(l,entry,state,slot)){save();return;}
     worker.workStatus("stock_full");return;}
    cargo.set(slot,ItemStack.EMPTY.save(new CompoundTag()));state.putInt("returns",state.getInt("returns")+1);save();return;
   }
   var entry=SettlementData.get(l.getServer()).entry(worker.settlementId());
   if(entry==null||!BuildingOrders.complete(l,entry,state)){worker.workStatus("changed_target");return;}
   state.putBoolean("complete",true);save();worker.displayWorkItem(ItemStack.EMPTY);worker.workStatus("construction_complete");return;
  }
  long gameTime=l.getGameTime();int chosen=-1,handy=-1;var eye=worker.getEyePosition();
  // AD-153: a helper works the storey the site is on — the level of the first unfinished operation and below.
  int layer=BlockPos.of(operations.getCompound(index).getLong("pos")).getY();
  for(int i=index;i<operations.size()&&(chosen<0||handy<0&&i<chosen+64);i++){
   if(!eligible(operations,index,i))break;
   var candidate=operations.getCompound(i);if(candidate.getBoolean("done")||candidate.getLong("retry")>gameTime||claimedByOther(id,i,gameTime))continue;
   if(helper&&!helpable(l,candidate,layer))continue;
   boolean blocked=false;
   // AD-069: a scaffold column is not taken down while any earlier operation is still to be worked from it — a deferred roof block must not
   // find its column already gone (home_2 stood under a column whose lower part had been dismantled out of turn).
   var removal=HallConstructionPlan.step(candidate);boolean dismantling=removal.before().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())&&removal.after().isAir();
   var column=BlockPos.of(candidate.getLong("pos"));
   // An elevated barn column is grown continuously. Skipping a deferred
   // segment would leave floating upper segments that nobody can climb to.
   if(candidate.getBoolean("barn")&&candidate.contains("stand")&&removal.after().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())
     &&!l.getBlockState(column.below()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))continue;
   for(int j=index;j<i&&!blocked;j++){var earlier=operations.getCompound(j);if(earlier.getBoolean("done"))continue;
    if(earlier.getLong("pos")==candidate.getLong("pos"))blocked=true;
    else if(dismantling&&earlier.contains("stand")){var stand=BlockPos.of(earlier.getLong("stand"));if(stand.getX()==column.getX()&&stand.getZ()==column.getZ())blocked=true;}}
   if(blocked)continue;
   if(chosen<0){chosen=i;continue;}
   // A house is not built by crossing it for every block: whatever the worker can already reach from where it stands is done first,
   // and only blocks that can stand on their own may be brought forward, so the order never breaks the structure.
   var cell=BlockPos.of(candidate.getLong("pos"));var wanted=HallConstructionPlan.step(candidate);
   // relocate-fix: never a door leaf. AD-069 puts the leaves last on purpose — the doorway is the builder's own way in and out while the
   // house stands open — and handy work walked straight past that: hanging a door from inside shut the builder in with its scaffold
   // columns outside, and the site waited on a cell it could no longer reach (home@1 at 858/914).
   boolean placing=!candidate.contains("return")&&!wanted.after().isAir()&&!wanted.before().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())&&!(wanted.after().getBlock() instanceof DoorBlock);
   // Everything that can be placed from where the worker already stands — on the ground or inside the column it occupies — is done before it walks away.
   // relocate-fix: a ground operation is handy only to a worker on the ground — one up in a scaffold column would sink for it, and the
   // next tick, lower, find the column's own operation first again: it bobbed at y2.5..2.9 of the column for good (AD-129 home_2).
   var candidateSite=candidate.contains("site")?BlockPos.of(candidate.getLong("site")):origin;
   boolean here=!candidate.contains("stand")?!aloft(candidateSite)&&!l.getBlockState(worker.blockPosition()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())&&eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(cell))<=reachSq
     :BlockPos.of(candidate.getLong("stand")).equals(worker.blockPosition());
   if(placing&&here&&wanted.after().canSurvive(l,cell)&&BuildingOrders.reconcile(l,candidate,BlockPos.of(state.getLong("origin"))))handy=i;
  }
  if(chosen<0&&!helper){
   // Everything left is on a retry pause: the earliest unfinished operation is taken up again at once instead of the site standing idle.
   for(int i=index;i<operations.size()&&chosen<0&&eligible(operations,index,i);i++)if(!operations.getCompound(i).getBoolean("done")&&!claimedByOther(id,i,gameTime)){chosen=i;operations.getCompound(i).putLong("retry",0);save();}
  }
  if(chosen<0){if(helper&&yieldIdlePlacement(l,operations))return;worker.workStatus(helper?"helping_waits":"waiting_for_access");return;}
  int current=handy>=0?handy:chosen;var op=operations.getCompound(current);claim(id,current,gameTime);working=current;
  // AD-125: an operation on the old lot is worked from that lot's own height; a committed change is only booked, a vanished old block skipped.
  var site=op.contains("site")?BlockPos.of(op.getLong("site")):origin;int siteAttic=site.getY()+BuildingOrders.ATTIC;
  if(relocate&&Relocations.settle(l,state,current,this::save)){stuckIndex=-1;return;}
  if(worker.tickCount%20==0)lastOp=current+"@"+BlockPos.of(op.getLong("pos")).toShortString()+(op.contains("stand")?" stand="+BlockPos.of(op.getLong("stand")).toShortString():" nostand")+" deferred="+op.getInt("deferred")+" stuck="+stuckTicks
   +" idle="+idleTicks+" zza="+String.format(java.util.Locale.ROOT,"%.2f",worker.zza)+" speed="+String.format(java.util.Locale.ROOT,"%.2f",worker.getSpeed())
   +" shift="+worker.isShiftKeyDown()+" climb="+worker.onClimbable()+" move="+String.format(java.util.Locale.ROOT,"%.2f,%.2f",worker.getDeltaMovement().x,worker.getDeltaMovement().z);
  if(worker.tickCount%20==0)opDiag=lastOp;
  // A cell nobody can reach right now waits its turn instead of stopping the whole site: it is retried whenever the rest of the site has moved on.
  if(stuckIndex!=current){stuckIndex=current;stuckTicks=0;}
  else if(++stuckTicks>300&&(!op.contains("deferredAt")||op.getInt("deferredAt")<state.getInt("progress"))){
   op.putInt("deferred",op.getInt("deferred")+1);op.putInt("deferredAt",state.getInt("progress"));op.putLong("retry",gameTime+2400);
   // Why it waits: the builder's status and cell when it gave the operation up this time (read in the logs and the tests' failure messages).
   op.putString("why",worker.workStatus()+"@"+String.format(java.util.Locale.ROOT,"%.2f,%.2f,%.2f",worker.getX(),worker.getY(),worker.getZ())+" climb="+worker.onClimbable()+" ground="+worker.onGround()+" in="+BuiltInRegistries.BLOCK.getKey(l.getBlockState(worker.blockPosition()).getBlock()).getPath()+" stand:"+standDiag);
   state.putInt("deferrals",state.getInt("deferrals")+1);Relocations.giveUp(state,op);save();
   stuckTicks=0;modeIndex=-1;worker.workStatus("deferred_operation");return;}
  // relocate-fix: a worker that has not moved at all for ten seconds on the same operation is wedged whatever the site's progress says (the
  // give-up above only counts when the rest of the site has moved on, and a wedged site never does): that operation waits its turn all the
  // same, without counting towards giving an old cell up, so the builder walks to other work instead of hanging on it. Placing a block is
  // twenty ticks of standing still, so no real work reaches this.
  else if(stuckTicks>400&&idleChecks>=5){op.putLong("retry",gameTime+2400);state.putInt("deferrals",state.getInt("deferrals")+1);save();
   stuckTicks=0;modeIndex=-1;exitTo=null;worker.workStatus("deferred_operation");return;}
  // AD-086: the site's own column stands where this block goes. The column comes down in an operation of the plan itself, so this one
  // gives way and is taken up again afterwards — otherwise the house stops for good at the first cell a scaffold occupies.
  var here=HallConstructionPlan.step(op);
  if(l.getBlockState(here.pos()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())
    &&!here.before().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())&&!here.after().is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())){
   op.putInt("deferred",op.getInt("deferred")+1);op.putInt("deferredAt",state.getInt("progress"));op.putLong("retry",gameTime+200);
   state.putInt("deferrals",state.getInt("deferrals")+1);save();stuckIndex=-1;stuckTicks=0;modeIndex=-1;worker.workStatus("column_in_the_way");return;
  }
  if(!op.getBoolean("dismantle")&&!BuildingOrders.reconcile(l,op,site)){worker.workStatus("changed_target");return;}
  var planned=HallConstructionPlan.step(op);var target=planned.pos();
  if(planned.before().equals(planned.after())){op.putBoolean("done",true);state.putInt("progress",state.getInt("progress")+1);save();return;}
  // AD-112 (owner, 2026-09-19): a core or ring goes in only once its level's research of this building's branch is done; until then that
  // cell waits (the rest of the site goes on) and the core or ring stays with the project, never set above what the village knows.
  // AD-125 (C5): a moved core goes back at the grade it stood at, whatever the research says today.
  if(planned.after().getBlock() instanceof BuildingCoreBlock core&&!keepsGrade(state,planned.after())&&!coreResearched(l,core.type(),planned.after())){
   op.putLong("retry",gameTime+200);stuckIndex=-1;stuckTicks=0;modeIndex=-1;worker.workStatus("core_research");return;}
  worker.displayWorkItem(op.contains("item")?new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(op.getString("item")))):ItemStack.EMPTY);
  boolean scaffoldHatch=state.getBoolean("scaffoldHatch");
  // Sneaking is kept while the worker has to sink through a scaffold column it is only passing by.
  boolean sinking=worker.onClimbable()&&!op.contains("stand");
  if(worker.isShiftKeyDown()&&!sinking&&!(scaffoldHatch&&atHatch&&worker.getY()>=attic-1))worker.setShiftKeyDown(false);
  // AD-030: an operation planned from a scaffold column is worked from inside that column.
  boolean columnWork=op.contains("stand");
  boolean barnWork=op.getBoolean("barn")&&!columnWork;
  // AD-046: a column that is not the one this operation wants holds the worker like a ladder — it is left by hand before anything else, whatever the operation.
  var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();var at=worker.blockPosition();
  boolean ownColumn=columnWork&&BlockPos.of(op.getLong("stand")).getX()==at.getX()&&BlockPos.of(op.getLong("stand")).getZ()==at.getZ();
  if(l.getBlockState(at).is(scaffold)&&!ownColumn){
   var route=worker.getNavigation().getPath();var to=route!=null&&!route.isDone()?beyond(l,route):target;
   // relocate-fix: a worker the column holds up comes out of it whatever else is true. One standing on the floor in the foot cell of a
   // column it is only walking through comes out only when there is a cell beside it nearer its goal: between the pair of front columns of
   // the AD-129 home every way out led backwards (the barrel and the hay close the row behind them), and the builder swung between the two
   // cells for good — while the column it wanted was the far one of the pair, straight on through the one it stood in.
   boolean held=!worker.onGround()||worker.getY()-at.getY()>.2||l.getBlockState(at.below()).is(scaffold);
   var out=held?null:exit(l,worker,to);
   if(held||out!=null&&out.distSqr(to)<at.distSqr(to)){
    if(leaveColumn(l,to)){worker.workStatus("stepping_out");return;}
    worker.workStatus("needs_access");return;
   }
  }
  exitTo=null;
  if(columnWork&&!column(op,siteAttic,hx,hz,atHatch,up))return;
  if(!columnWork&&!atHatch&&aloft(site)){descend();return;}
  // Work level is chosen once per operation: from the ground/floor when a lower stand reaches the cell, otherwise from the attic.
  if(modeIndex!=current){modeIndex=current;modeHigh=!barnWork&&!state.getBoolean("noHatch")&&target.getY()>=attic&&!BuildingOrders.lowReach(l,target,origin);}
  boolean high=modeHigh;
  // Lower storey: ground or floor level only (up to 2 foundation fills lower); never work from a half-built wall top.
  // The barn has real stair landings and field floors at 1, 6 and 11. Roof
  // dismantling precedes temporary columns and must be reachable from these
  // existing floors, rather than the farmhouse's unrelated attic or ground.
  int[] levels=barnWork?new int[]{site.getY()-2,site.getY()-1,site.getY(),site.getY()+1,site.getY()+1+FarmField.FLOOR_PITCH,site.getY()+1+2*FarmField.FLOOR_PITCH}
    :high?new int[]{attic}:new int[]{site.getY()-2,site.getY()-1,site.getY(),site.getY()+1};
  boolean elevated=!barnWork&&!high&&worker.onGround()&&!worker.onClimbable()&&worker.getY()>site.getY()+1.7;
  var shape=planned.after().getCollisionShape(l,target);
  boolean inTheWay=!shape.isEmpty()&&shape.bounds().move(target).intersects(worker.getBoundingBox());
  if(columnWork&&worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>reachSq){worker.workStatus("needs_access");return;}
  if(!columnWork&&(high!=up||elevated||inTheWay||worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>reachSq)){
   if(high&&!up){
    // Pathfinding ends beside a ladder cell; the last block to the hatch and the climb are direct movement.
    if(hx*hx+hz*hz>4&&!worker.onClimbable())worker.getNavigation().moveTo(hatch.getX()+.5,origin.getY()+1,hatch.getZ()+.5,.8);
    else{worker.getNavigation().stop();worker.setDeltaMovement(Math.max(-.1,Math.min(.1,hx)),atHatch||worker.onClimbable()?.2:worker.getDeltaMovement().y,Math.max(-.1,Math.min(.1,hz)));}
    worker.workStatus("climbing");return;
   }
   if(!high&&up){climbDown(hx,hz,atHatch);return;}
   boolean inColumn=l.getBlockState(worker.blockPosition()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get());
   if(worker.tickCount%10==0||!worker.onGround()||inColumn){
    var best=stand(l,target,levels);
    if(best!=null){
     worker.workStatus(inTheWay?"clearing_work_position":"walking");
     if(!worker.onGround()||worker.onClimbable()){double dx=best.getX()+.5-worker.getX(),dz=best.getZ()+.5-worker.getZ(),length=Math.max(1,Math.sqrt(dx*dx+dz*dz));double vy=!worker.onClimbable()?worker.getDeltaMovement().y:best.getY()>worker.getY()+.1?.12:best.getY()<worker.getY()-.1?-.15:0;
      if(worker.onClimbable()&&vy<0)worker.setShiftKeyDown(true);worker.getNavigation().stop();worker.setDeltaMovement(dx/length*.15,vy,dz/length*.15);}
     else{double dx=best.getX()+.5-worker.getX(),dz=best.getZ()+.5-worker.getZ();
      // Navigation accepts a path ending one block short of the stand; the final step to an adjacent stand is direct movement.
      // relocate-fix: and only when nothing stands between the two — a stand on the other side of a wall is walked to by a route.
      // Construction can obstruct a cached path's next node while the nearby
      // work stand still has a clear approach. Do not wait for that route to
      // finish; cancel it before ordinary movement takes the collision-checked step.
      if(!stepToWorkStand(l,worker,best))walkTo(best,.8);}
    }else if(up&&(worker.onClimbable()||!worker.onGround())){
     // Top of the ladder: step off towards the attic floor away from the supporting wall.
     var exit=hatch.relative(Direction.from2DDataValue(state.getInt("hatchFacing")));worker.setDeltaMovement(Math.max(-.15,Math.min(.15,(exit.getX()+.5-worker.getX())*.3)),Math.abs(hx)<.6&&Math.abs(hz)<.6&&worker.getY()<attic+.3?.1:worker.getDeltaMovement().y,Math.max(-.15,Math.min(.15,(exit.getZ()+.5-worker.getZ())*.3)));
    }else if(inColumn||worker.onClimbable()&&!worker.onGround()){
     // Pathfinding cannot start inside a scaffold column: sink to its foot and step out to a free neighbouring cell.
     var out=java.util.stream.Stream.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST).map(d->worker.blockPosition().relative(d)).filter(q->standable(l,q)&&!l.getBlockState(q).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())).min(Comparator.comparingDouble(q->q.distSqr(target))).orElse(null);
     boolean footed=worker.onGround();
     worker.getNavigation().stop();
     if(!footed){worker.setShiftKeyDown(true);worker.setDeltaMovement(0,-.15,0);worker.workStatus("stepping_out");}
     else if(out!=null){worker.setShiftKeyDown(false);
      double dx=out.getX()+.5-worker.getX(),dz=out.getZ()+.5-worker.getZ(),length=Math.max(.5,Math.sqrt(dx*dx+dz*dz));
      worker.setDeltaMovement(dx/length*.2,worker.getDeltaMovement().y,dz/length*.2);worker.workStatus("stepping_out");}
     else worker.workStatus("needs_access");
    }else if(!worker.onGround()){
     // Navigation refuses to search while the worker is off the ground: stop pushing and let it land.
     worker.getNavigation().stop();worker.setShiftKeyDown(false);worker.setDeltaMovement(0,worker.getDeltaMovement().y,0);worker.workStatus("landing");
    }else worker.workStatus("needs_access");
   }
   return;
  }
  worker.getNavigation().stop();if(worker.onClimbable()&&!columnWork)worker.setDeltaMovement(0,0,0);if(++labor<20)return;labor=0;
  // AD-153: no block is set on another resident (a fellow builder of the crew standing in the cell): the cell waits two seconds.
  if(!shape.isEmpty()&&!l.getEntitiesOfClass(ResidentEntity.class,shape.bounds().move(target),x->x!=worker&&x.isAlive()).isEmpty()){
   op.putLong("retry",gameTime+40);stuckIndex=-1;stuckTicks=0;modeIndex=-1;save();worker.workStatus("waiting_for_access");return;}
  if(relocate){var result=Relocations.effect(l,state,current,this::save);if(!result.isEmpty()){worker.workStatus(result);return;}worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return;}
  if(!WorldJournal.place(l,Settlement.childId(id,"block/"+current),target,planned.before(),planned.after())){worker.workStatus("changed_target");return;}
  if(op.contains("item"))consume(op.getString("item"));
  if(op.contains("return"))state.getList("cargo",Tag.TAG_COMPOUND).add(new ItemStack(BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(op.getString("return")))).save(new CompoundTag()));
  op.putBoolean("done",true);op.putUUID("by",worker.getUUID());state.putInt("progress",state.getInt("progress")+1);save();worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
 }
 /** An idle helper must not occupy a block another crew member still needs to place. */
 private boolean yieldIdlePlacement(ServerLevel l,ListTag operations){
  if(!pendingPlacement(l,operations,worker.getBoundingBox()))return false;
  var from=worker.blockPosition();BlockPos best=null;double score=Double.MAX_VALUE;
  for(int dy:new int[]{0,-1})for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
   var cell=from.offset(dx,dy,dz);if(cell.equals(from)||!l.hasChunkAt(cell)||!standable(l,cell))continue;
   var body=worker.getBoundingBox().move(cell.getX()+.5-worker.getX(),cell.getY()-worker.getY(),cell.getZ()+.5-worker.getZ());
   if(!l.noCollision(worker,body)||pendingPlacement(l,operations,body))continue;
   double value=cell.distToCenterSqr(worker.position())+.2*Math.abs(dy);if(value>=score)continue;
   if(!clearLine(l,worker,cell)){var path=ConstructionRoutes.plan(worker,cell);if(path==null||!path.canReach())continue;}
   best=cell;score=value;
  }
  if(best==null)return false;
  worker.workStatus("clearing_work_position");if(!stepToWorkStand(l,worker,best))walkTo(best,.8);return true;
 }
 private static boolean pendingPlacement(ServerLevel l,ListTag operations,net.minecraft.world.phys.AABB body){
  for(var raw:operations){var op=(CompoundTag)raw;if(op.getBoolean("done"))continue;var pos=BlockPos.of(op.getLong("pos"));
   // Decode only operations near this resident; large field plans contain thousands of remote cells.
   if(pos.getX()+2<body.minX||pos.getX()-1>body.maxX||pos.getY()+3<body.minY||pos.getY()-1>body.maxY||pos.getZ()+2<body.minZ||pos.getZ()-1>body.maxZ)continue;
   var step=HallConstructionPlan.step(op);var shape=step.after().getCollisionShape(l,step.pos());if(!shape.isEmpty()&&shape.bounds().move(step.pos()).intersects(body))return true;
  }
  return false;
 }
 /** AD-153: the operation this builder has in hand now (-1: none yet), for the tests and probes of a crew. */
 public volatile int working=-1;
 public boolean helper(){return helper;}
 /** AD-125 (C5): a moved core goes back up to the grade it stood at without asking the research again. */
 public static boolean keepsGrade(CompoundTag state,BlockState after){return state.getBoolean("relocate")&&after.getBlock() instanceof BuildingCoreBlock&&after.getValue(BuildingCoreBlock.GRADE)<=state.getInt("grade");}
 /** AD-125: operations are taken phase by phase — nothing of a later phase is done out of turn, as handy work or after a pause. */
 /** AD-153: an operation a helper may take — a block of the building or a clearing worked from the ground or the floor, on the storey the
  *  site is on or below it, that stands on its own; never a scaffold, work from a column, a door leaf, a core or a returned tool: those keep
  *  the lead's order (AD-030, AD-069, AD-112). */
 public static boolean helpable(ServerLevel l,CompoundTag op,int layer){
  if(op.contains("stand")||op.contains("return")||op.getBoolean("dismantle"))return false;
  var step=HallConstructionPlan.step(op);var sc=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  if(step.pos().getY()>layer||step.before().is(sc)||step.after().is(sc)||step.after().getBlock() instanceof DoorBlock||step.after().getBlock() instanceof BuildingCoreBlock)return false;
  return step.after().isAir()||step.after().canSurvive(l,step.pos());
 }
 public static boolean eligible(ListTag ops,int index,int i){return index>=ops.size()||i>=ops.size()||ops.getCompound(i).getInt("phase")<=ops.getCompound(index).getInt("phase");}
 private void climbDown(double hx,double hz,boolean atHatch){
  var hatch=BlockPos.of(state.getLong("hatch"));var exit=hatch.relative(Direction.from2DDataValue(state.getInt("hatchFacing")));int attic=BlockPos.of(state.getLong("origin")).getY()+BuildingOrders.ATTIC;
  if(state.getBoolean("scaffoldHatch")){
   // Scaffold hatch: walk onto its top, then descend through it (like sneaking on vanilla scaffolding).
   if(!atHatch&&worker.onGround()&&worker.getY()>=attic-.05&&hx*hx+hz*hz>2.25)worker.getNavigation().moveTo(hatch.getX()+.5,attic,hatch.getZ()+.5,.8);
   else{worker.getNavigation().stop();if(atHatch)worker.setShiftKeyDown(true);worker.setDeltaMovement(Math.max(-.12,Math.min(.12,hx)),worker.onClimbable()?-.15:worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,hz)));}
   worker.workStatus("climbing");return;
  }
  if(!atHatch&&worker.onGround()&&worker.getY()>=attic-.05&&worker.distanceToSqr(exit.getX()+.5,attic,exit.getZ()+.5)>1.5)worker.getNavigation().moveTo(exit.getX()+.5,attic,exit.getZ()+.5,.8);
  else{worker.getNavigation().stop();worker.setDeltaMovement(Math.max(-.12,Math.min(.12,hx)),worker.onClimbable()?-.15:worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,hz)));}
  worker.workStatus("climbing");
 }
 /** Inside or on top of a scaffold column above the floor level. */
 private boolean aloft(BlockPos origin){
  var l=worker.level();var scaffold=org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get();
  return worker.getY()>origin.getY()+1.7&&(l.getBlockState(worker.blockPosition()).is(scaffold)||l.getBlockState(worker.blockPosition().below()).is(scaffold));
 }
 /** Scaffold tops hold an entity from above; descending works like sneaking on vanilla scaffolding. */
 private void descend(){worker.getNavigation().stop();centre(worker);worker.setShiftKeyDown(true);worker.setDeltaMovement(0,-.15,0);worker.workStatus("climbing");}
 /** relocate-fix: inside a scaffold column the worker is set on the column's axis before it climbs or sinks. Off the axis its hitbox rests on
  *  whatever stands beside the column — the barrel under the jetty of the AD-129 home held the builder at y2 of a front column for good,
  *  sneaking or not, and pushing against it inside a climbable cell only lifted him higher. The move is taken only where it collides with nothing. */
 static void centre(ResidentEntity worker){
  var l=worker.level();var at=BlockPos.containing(worker.getX(),worker.getY()+.2,worker.getZ());
  if(!l.getBlockState(at).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return;
  double dx=at.getX()+.5-worker.getX(),dz=at.getZ()+.5-worker.getZ();if(dx*dx+dz*dz<1e-4)return;
  if(l.noCollision(worker,worker.getBoundingBox().move(dx,0,dz)))worker.setPos(at.getX()+.5,worker.getY(),at.getZ()+.5);
 }
 /** Moves the worker into the planned scaffold column and to the planned feet height; true when in position. */
 private boolean column(CompoundTag op,int attic,double hx,double hz,boolean atHatch,boolean up){
  var feet=BlockPos.of(op.getLong("stand"));int standBase=op.getInt("standBase");boolean atticColumn=standBase>=attic-1;
  double cx=feet.getX()+.5-worker.getX(),cz=feet.getZ()+.5-worker.getZ();
  // Inside the column footprint the worker is aligned to its axis so the hitbox cannot rest on neighbouring beds or walls.
  if(cx*cx+cz*cz<.5&&worker.getY()>=standBase-.5&&worker.level().getBlockState(BlockPos.containing(feet.getX()+.5,worker.getY(),feet.getZ()+.5)).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())){worker.setPos(feet.getX()+.5,worker.getY(),feet.getZ()+.5);cx=0;cz=0;}
  boolean near=cx*cx+cz*cz<.12;
  if(!(near&&(worker.onClimbable()||worker.level().getBlockState(worker.blockPosition()).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())))){
   if(atticColumn&&!up&&!(near&&worker.getY()>=attic-1)){
    var hatch=BlockPos.of(state.getLong("hatch"));var origin=BlockPos.of(state.getLong("origin"));
    if(hx*hx+hz*hz>4&&!worker.onClimbable())worker.getNavigation().moveTo(hatch.getX()+.5,origin.getY()+1,hatch.getZ()+.5,.8);
    else{worker.getNavigation().stop();worker.setDeltaMovement(Math.max(-.1,Math.min(.1,hx)),atHatch||worker.onClimbable()?.2:worker.getDeltaMovement().y,Math.max(-.1,Math.min(.1,hz)));}
    worker.workStatus("climbing");return false;
   }
   if(!atticColumn&&up){climbDown(hx,hz,atHatch);return false;}
   // Hanging in another column blocks every approach: sink to the floor first.
   if(!near&&!atHatch&&(aloft(BlockPos.of(state.getLong("origin")))||worker.onClimbable()&&!worker.onGround())){descend();return false;}
   var level=(ServerLevel)worker.level();
   if(escapeFurniture(new BlockPos(feet.getX(),standBase,feet.getZ()))){worker.workStatus("walking_to_scaffold");return false;}
   // A stair jump on the approach still belongs to navigation. Steering directly at a distant
   // column here cancels the detour every time the worker leaves the ground.
   if(!worker.onGround()&&!worker.onClimbable()&&cx*cx+cz*cz>2.25){worker.workStatus("walking_to_scaffold");return false;}
   // Standing right under the column foot: the worker steps up into it instead of walking around looking for an approach.
   var above=new BlockPos(feet.getX(),net.minecraft.util.Mth.floor(worker.getY())+1,feet.getZ());
   if(near&&worker.onGround()&&worker.getY()<above.getY()&&level.getBlockState(above).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get())){
    worker.getNavigation().stop();worker.setDeltaMovement(0,.42,0);worker.workStatus("climbing");return false;}
   // A path that ends short of the column (navigation done within 3.5 blocks of it) is finished by direct movement, but only across free ground.
   if(cx*cx+cz*cz>2.25&&worker.onGround()&&!(worker.getNavigation().isDone()&&cx*cx+cz*cz<12.25&&clearLine(level,worker,new BlockPos(feet.getX(),standBase,feet.getZ()))&&!crossesColumn(level,worker,feet))){
    // Scaffold columns are not walkable for pathfinding: the worker walks to a free cell beside the column and steps in by hand.
    var approach=stand(level,worker,new BlockPos(feet.getX(),standBase,feet.getZ()),new int[]{standBase,standBase-1,standBase+1});standDiag=lastStand;
    // relocate-fix: the best approach to the column is the very cell the worker stands in, and the column is still out of reach — it is
    // boxed in by what it has just set down (the chest and the two beds on the floor of an AD-129 home close a one-cell pocket, and a
    // stand search accepts none of them to stand on). Walking to where it already stands is no move at all: it climbs out over them.
    if(approach!=null&&approach.equals(worker.blockPosition())){
     var over=climbOut(level,worker,new BlockPos(feet.getX(),standBase,feet.getZ()));
     if(over==null){worker.getNavigation().stop();worker.setDeltaMovement(0,worker.getDeltaMovement().y,0);worker.workStatus("needs_access");return false;}
     worker.getNavigation().stop();push(over);walkDiag="boxed in at "+approach.toShortString()+" out over "+over.toShortString();lastWalk=walkDiag;
     worker.workStatus("walking_to_scaffold");return false;}
    if(approach!=null){double ax=approach.getX()+.5-worker.getX(),az=approach.getZ()+.5-worker.getZ();
     // Navigation reports a path of one node as finished while the worker is still a block short: that last step is direct movement.
     // relocate-fix: only across free ground. Without a line check the worker walked at a stand two blocks off straight through the wall
     // between them, and since this branch never asks navigation for a route, isDone() stayed true for ever: on a turned AD-129 home the
     // builder stood inside at the window while the foot of his column lay outside it, and pressed into the pane until the run gave up.
     if(ax*ax+az*az<=5.0625&&Math.abs(approach.getY()-worker.getY())<1.2&&worker.getNavigation().isDone()&&clearLine(level,worker,approach)&&!crossesColumn(level,worker,approach))worker.getMoveControl().setWantedPosition(approach.getX()+.5,approach.getY(),approach.getZ()+.5,.8);
     else walkTo(approach,.8);}
    // No way to the column at all: say so and let the operation wait its turn instead of pushing into a wall.
    else{worker.getNavigation().stop();worker.setDeltaMovement(0,worker.getDeltaMovement().y,0);worker.workStatus("needs_access");return false;}}
   // relocate-fix: a direct push that has got nowhere is pressing into a wall the line check did not see (the worker's own width, a corner,
   // a step too high to hop): it goes back to walking a route to the cell beside the column, and says so when there is none, so the
   // operation waits its turn instead of the whole site hanging on it (warehouse: 470 ticks a time against the plinth of its own wall).
   else if(worker.horizontalCollision&&idleTicks>0){
    var around=stand(level,worker,new BlockPos(feet.getX(),standBase,feet.getZ()),new int[]{standBase,standBase-1,standBase+1});standDiag=lastStand;
    if(around==null){worker.getNavigation().stop();worker.setDeltaMovement(0,worker.getDeltaMovement().y,0);worker.workStatus("needs_access");return false;}
    walkTo(around,.8);}
   else{worker.getNavigation().stop();hop(cx,cz);worker.setDeltaMovement(Math.max(-.12,Math.min(.12,cx*.5)),worker.getDeltaMovement().y,Math.max(-.12,Math.min(.12,cz*.5)));}
   worker.workStatus("walking_to_scaffold");return false;
  }
  // A low beam may stop the body just below the planned feet height. Work from the
  // actual column position when the target is already in reach and cannot hit the builder.
  // The plan is a route hint, not an extra height requirement for a reachable block.
  var step=HallConstructionPlan.step(op);var shape=step.after().getCollisionShape(worker.level(),step.pos());
  if(worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(step.pos()))<=reachSq
    &&(shape.isEmpty()||!shape.bounds().move(step.pos()).intersects(worker.getBoundingBox()))){
   worker.getNavigation().stop();worker.setDeltaMovement(cx*.3,0,cz*.3);return true;
  }
  double dy=feet.getY()-worker.getY();worker.getNavigation().stop();
  if(Math.abs(dy)>.25){centre(worker);if(dy<0)worker.setShiftKeyDown(true);worker.setDeltaMovement(cx*.3,dy>0?.2:-.15,cz*.3);worker.workStatus("climbing");return false;}
  worker.setDeltaMovement(cx*.3,dy*.5,cz*.3);return true;
 }
}
