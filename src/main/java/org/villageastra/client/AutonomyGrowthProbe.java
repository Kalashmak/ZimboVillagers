package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.GameType;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Observes a naturally generated NPC village. No stock, terrain, research, resident, clock or goal injection.
 * A stationary creative observer keeps vanilla random ticks active; spectator would stop crop growth.
 * An empty server intentionally pauses simulation. */
final class AutonomyGrowthProbe {
 private static boolean started;private static volatile boolean busy,done;private static volatile String failure;
 private static UUID village;private static long since,last=-1200;private static int ticks;
 private static final Set<Profession> witnessed=EnumSet.noneOf(Profession.class);
 private static Set<UUID> initialBuildings;
 private static final Set<String> milestones=new HashSet<>();
 private static void milestone(String name,long elapsed){if(milestones.add(name))LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH milestone={} active={}",name,elapsed);}
 static boolean enabled(){return Boolean.getBoolean("villageastra.autonomyGrowthSmoke");}
 static void tick(Minecraft mc){
  if(failure!=null){LogUtils.getLogger().error("ASTRA_AUTONOMY_GROWTH INCOMPLETE {}",failure);mc.stop();return;}
  if(done){LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH VERIFIED fullProgression=true noPlayerSupplies=true");mc.stop();return;}
  if(++ticks%10!=0||busy)return;busy=true;
  mc.getSingleplayerServer().execute(()->{try{sample(mc);}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }
 private static java.nio.file.Path observation(net.minecraft.server.MinecraftServer server) {
  return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/zimbovillagers-probes/growth.bin");
 }
 private static void saveObservation(net.minecraft.server.MinecraftServer server) {
  var t=new net.minecraft.nbt.CompoundTag();t.putUUID("village",village);t.putLong("since",since);
  var ids=new net.minecraft.nbt.ListTag();for(var id:initialBuildings)ids.add(net.minecraft.nbt.NbtUtils.createUUID(id));t.put("initialBuildings",ids);
  var marks=new net.minecraft.nbt.ListTag();for(var m:milestones)marks.add(net.minecraft.nbt.StringTag.valueOf(m));t.put("milestones",marks);
  var roles=new net.minecraft.nbt.ListTag();for(var r:witnessed)roles.add(net.minecraft.nbt.StringTag.valueOf(r.name()));t.put("witnessed",roles);
  org.villageastra.persistence.NbtRecord.write(observation(server),t);
 }
 private static void sample(Minecraft mc){
  var server=mc.getSingleplayerServer();var l=server.overworld();var data=SettlementData.get(server);
  if(!started){if(data.entries().isEmpty())return;var player=server.getPlayerList().getPlayers().get(0);
   var e=data.entries().stream().min(Comparator.comparingDouble(x->player.distanceToSqr(x.center().getCenter()))).orElseThrow();village=e.settlement().id();since=data.clock().ticks();started=true;player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;
   boolean resume=java.nio.file.Files.exists(observation(server));
   if(resume){var saved=org.villageastra.persistence.NbtRecord.read(observation(server));
    if(!saved.getUUID("village").equals(village))throw new IllegalStateException("Observation belongs to another settlement");
    since=saved.getLong("since");initialBuildings=new HashSet<>();
    for(var id:saved.getList("initialBuildings",Tag.TAG_INT_ARRAY))initialBuildings.add(net.minecraft.nbt.NbtUtils.loadUUID(id));
    for(var mark:saved.getList("milestones",Tag.TAG_STRING))milestones.add(mark.getAsString());
    for(var role:saved.getList("witnessed",Tag.TAG_STRING))witnessed.add(Profession.valueOf(role.getAsString()));
    LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH RESUME village={} observedTicks={} milestones={}",village,data.clock().ticks()-since,milestones);
   }else{
    if(e.settlement().civilization().level()!=1||e.settlement().residents().size()!=6||e.settlement().buildings().size()!=7||!BookResearch.completed(e,BookResearch.inspect(l,e)).isEmpty())throw new IllegalStateException("Not a fresh starter village");
    initialBuildings=new HashSet<>();e.settlement().buildings().forEach(b->initialBuildings.add(b.id()));saveObservation(server);
   }
   if(!resume)LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH START village={} center={} seed={} initialResidents={} initialBuildings={} mode=peaceful observerOnly=true",village,e.center(),l.getSeed(),e.settlement().residents().size(),e.settlement().buildings().size());
  }
  var e=data.entry(village);var s=e.settlement();long elapsed=data.clock().ticks()-since;if(elapsed-last<1200)return;last=elapsed;
  if(s.governance().playerMayor()!=null)throw new IllegalStateException("Player became mayor");
  if(s.residents().stream().noneMatch(Resident::alive)||s.residents().stream().anyMatch(r->!r.alive()&&r.missedMeals()>=Population.DEATH))throw new IllegalStateException("Settlement lost residents to starvation; preserve this world as failed evidence");
  var roles=new TreeMap<String,Integer>();var statuses=new TreeMap<String,String>();for(var r:s.residents())if(r.alive()){
   if(r.profession()!=null)witnessed.add(r.profession());
   String role=r.profession()==null?r.life().name():r.profession().name();roles.merge(role,1,Integer::sum);
   if(l.getEntity(r.id()) instanceof ResidentEntity npc)statuses.merge(role,npc.workStatus()+"@"+npc.blockPosition().toShortString(),(a,b)->a+";"+b);
  }
  var buildings=new TreeMap<String,Integer>();s.buildings().forEach(b->buildings.merge(b.type(),b.level(),Math::max));
  var project=HallUpgradeGoal.exists(l,village)?HallUpgradeGoal.inspect(l,village):new net.minecraft.nbt.CompoundTag();int ops=0;for(var raw:project.getList("ops",Tag.TAG_COMPOUND))if(((net.minecraft.nbt.CompoundTag)raw).getBoolean("done"))ops++;
  var stock=new TreeMap<String,Integer>();var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  if(chest!=null)for(int i=0;i<chest.getContainerSize();i++){var item=chest.getItem(i);if(!item.isEmpty())stock.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getCount(),Integer::sum);}
  var research=BookResearch.inspect(l,e);var completed=BookResearch.completed(e,research);
  if(!completed.isEmpty())milestone("first_research",elapsed);
  if(s.residents().stream().filter(Resident::alive).count()>6)milestone("population_growth",elapsed);
  if(s.civilization().level()>1)milestone("civilization_"+s.civilization().level(),elapsed);
  if(!milestones.contains("first_house"))for(var b:s.buildings())if(!initialBuildings.contains(b.id())&&s.homes().stream().anyMatch(home->home.id().equals(b.id())&&home.usable())){
   var layout=BuildingPlacement.layout(e,b,b.level()==1?b.type():b.type()+"@"+b.level());
   if(!layout.isEmpty()&&layout.entrySet().stream().filter(cell->!cell.getValue().isAir()).allMatch(cell->l.hasChunkAt(cell.getKey())&&BuildingRepairs.present(l.getBlockState(cell.getKey()),cell.getValue())))milestone("first_house",elapsed);
  }
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH progress active={} civ={} roles={} buildings={} research={}/{} selected={} project={} funded={} complete={} ops={}/{} stock={} status={}",elapsed,s.civilization().level(),roles,buildings,completed.size(),ResearchCatalog.NODES.size(),research.getString("selected"),project.getString("design"),project.getBoolean("funded"),project.getBoolean("complete"),ops,project.getList("ops",Tag.TAG_COMPOUND).size(),stock,statuses);
  // Once funded, material removed from cargo belongs to the placed geometry.
  // It is spent, not a new unpaid shortage. This is an observation only.
  var deficit=project.getBoolean("funded")||project.getBoolean("complete")?Collections.<String,Integer>emptyMap():ConstructionFunding.missing(project);int required=project.getCompound("cost").getAllKeys().stream().mapToInt(k->project.getCompound("cost").getInt(k)).sum();
  int missing=deficit.values().stream().mapToInt(Integer::intValue).sum();var meals=new TreeMap<String,Integer>();
  for(var r:s.residents())if(r.alive()&&r.missedMeals()>0)meals.put(r.id().toString(),r.missedMeals());
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH supply paid={}/{} deficit={} missedMeals={}",Math.max(0,required-missing),required,deficit,meals);
  var types=new HashSet<String>();s.buildings().forEach(b->types.add(CoreCatalog.canonical(AnnexTypes.workplace(b.type()))));
  var absent=CoreCatalog.TYPES.stream().map(CoreCatalog::canonical).filter(type->!types.contains(type)).toList();
  var demands=Workshops.wants(l,e).stream().limit(12).map(w->w.count()+"x"+w.ingredient().toJson()).toList();
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH audit absent={} witnessed={} demand={}",absent,witnessed,demands);
  for(var r:s.residents())if(r.alive()&&r.profession()==null&&l.getEntity(r.id()) instanceof ResidentEntity npc){
   var path=npc.getNavigation().getPath();
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH idle id={} ticks={} entityTicking={} pos={} goals={} target={} reached={} home={}",r.id(),npc.tickCount,l.isPositionEntityTicking(npc.blockPosition()),npc.blockPosition(),npc.runningGoals(),path==null?null:path.getTarget(),path!=null&&path.canReach(),HomeNeighborhood.anchor(npc));
  }
  if(elapsed%6000<1300){
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH planning civic={} wanted={} proposal={} schoolRefusal={}",MayorPlanner.need(l,e),MayorPlanner.wanted(l,e),MayorPlanner.proposal(village),ResearchGate.designRefusal(l,e,"school"));
   var baking=HandBread.inspect(l,village);var baker=HandBread.claimedBaker(l,village);var body=baker==null?null:l.getEntity(baker);var npc=body instanceof ResidentEntity resident?resident:null;var route=npc==null?null:npc.getNavigation().getPath();
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH baking stage={} labor={}/{} lastTick={} claimed={} goals={} pos={} target={} reached={}",baking.getString("stage"),baking.getLong("labor"),baking.getLong("needLabor"),baking.getLong("lastTick"),baker,npc==null?null:npc.runningGoals(),npc==null?null:npc.position(),route==null?null:route.getTarget(),route!=null&&route.canReach());
  }
  if(elapsed%6000<1300)for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc&&r.profession()==Profession.MINER){
   var b=s.workplace(r.id());if(b==null)continue;var local=BuildingPlacement.local(e,b,npc.blockPosition());
   var blocks=new ArrayList<String>();for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var p=npc.blockPosition().relative(d);blocks.add(d+"="+l.getBlockState(p)+"/"+l.getBlockState(p.above()));}
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH miner local={} turn={} ground={} collision={} neighbors={}",local,b.rotation(),npc.onGround(),npc.horizontalCollision,blocks);
   var mining=MineWork.read(l,b);
   var sensing=HarvestRouteCache.stats(npc);LogUtils.getLogger().info("ZIMBOVILLAGERS_HARVEST_SENSING actor={} plans={} reusedMisses={} entries={}",npc.getUUID(),sensing.plans(),sensing.hits(),sensing.entries());
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH mineSupply stage={} status={} needed={} priority={} floor={} tool={}",mining.getString("stage"),mining.getString("status"),MineProspecting.needed(l,e,b),NaturalSupplyGoal.miningPriority(l,e,npc),mining.getInt("floorStep"),mining.getCompound("tool"));
  }
  if(elapsed%6000<1300)for(var r:s.residents())if(NaturalSupplyGoal.eligible(r)&&l.getEntity(r.id()) instanceof ResidentEntity npc){
   var trip=NaturalSupplyGoal.inspect(l,r.id());if(!NaturalSupplyGoal.active(trip))continue;
   var route=npc.getNavigation().getPath();var nodes=new ArrayList<Object>();
   if(route!=null)for(int i=route.getNextNodeIndex();i<Math.min(route.getNodeCount(),route.getNextNodeIndex()+4);i++)nodes.add(route.getNodePos(i));
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH trip id={} ticks={} goals={} pos={} sleeping={} ground={} collision={} stage={} target={} stand={} navigation={} reached={} next={} bedExit={}",r.id(),npc.tickCount,npc.runningGoals(),npc.position(),npc.isSleeping(),npc.onGround(),npc.horizontalCollision,trip.getString("stage"),net.minecraft.core.BlockPos.of(trip.getLong("target")),net.minecraft.core.BlockPos.of(trip.getLong("stand")),route==null?null:route.getTarget(),route!=null&&route.canReach(),nodes,BedExitGoal.landing(npc));
   if(npc.runningGoals().contains("BedExitGoal"))LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH furniture id={} motion={} speed={} attribute={} pose={} control={} wanted={},{},{}",r.id(),npc.getDeltaMovement(),npc.getSpeed(),npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED),npc.getPose(),npc.getMoveControl().hasWanted(),npc.getMoveControl().getWantedX(),npc.getMoveControl().getWantedY(),npc.getMoveControl().getWantedZ());
  }
  // Existing buildings alone are insufficient: a hall at VI with missing services is not completion.
  var terminal=TerminalProgress.blockers(l,e);
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH terminal blockers={}",terminal);
  done=elapsed>0&&milestones.contains("first_house")&&s.residents().stream().filter(Resident::alive).count()>6&&absent.isEmpty()&&witnessed.containsAll(EnumSet.allOf(Profession.class))&&s.civilization().level()==6&&completed.containsAll(ResearchCatalog.NODES.keySet())&&!HallUpgradeGoal.pending(l,village)&&terminal.isEmpty();
  saveObservation(server);
  if(!done&&elapsed>=Long.getLong("villageastra.autonomyGrowthTicks",72000L))failure="Observation limit reached before full progression; active="+elapsed+" civ="+s.civilization().level()+" research="+completed.size()+" roles="+roles;
 }
}
