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
 private static void sample(Minecraft mc){
  var server=mc.getSingleplayerServer();var l=server.overworld();var data=SettlementData.get(server);
  if(!started){if(data.entries().isEmpty())return;var player=server.getPlayerList().getPlayers().get(0);
   var e=data.entries().stream().min(Comparator.comparingDouble(x->player.distanceToSqr(x.center().getCenter()))).orElseThrow();village=e.settlement().id();since=data.clock().ticks();started=true;player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;
   if(e.settlement().civilization().level()!=1||e.settlement().residents().size()!=6||e.settlement().buildings().size()!=7||!BookResearch.completed(e,BookResearch.inspect(l,e)).isEmpty())throw new IllegalStateException("Not a fresh starter village");
   initialBuildings=new HashSet<>();e.settlement().buildings().forEach(b->initialBuildings.add(b.id()));
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH START village={} center={} seed={} initialResidents={} initialBuildings={} mode=peaceful observerOnly=true",village,e.center(),l.getSeed(),e.settlement().residents().size(),e.settlement().buildings().size());
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
  var types=new HashSet<String>();s.buildings().forEach(b->types.add(CoreCatalog.canonical(AnnexTypes.workplace(b.type()))));
  var absent=CoreCatalog.TYPES.stream().map(CoreCatalog::canonical).filter(type->!types.contains(type)).toList();
  var demands=Workshops.wants(l,e).stream().limit(12).map(w->w.count()+"x"+w.ingredient().toJson()).toList();
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH audit absent={} witnessed={} demand={}",absent,witnessed,demands);
  if(elapsed%6000<1300)for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc&&r.profession()==Profession.MINER){
   var b=s.workplace(r.id());if(b==null)continue;var local=BuildingPlacement.local(e,b,npc.blockPosition());
   var blocks=new ArrayList<String>();for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var p=npc.blockPosition().relative(d);blocks.add(d+"="+l.getBlockState(p)+"/"+l.getBlockState(p.above()));}
   LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH miner local={} turn={} ground={} collision={} neighbors={}",local,b.rotation(),npc.onGround(),npc.horizontalCollision,blocks);
  }
  // Existing buildings alone are insufficient: a hall at VI with missing services is not completion.
  var terminal=TerminalProgress.blockers(l,e);
  LogUtils.getLogger().info("ASTRA_AUTONOMY_GROWTH terminal blockers={}",terminal);
  done=elapsed>0&&milestones.contains("first_house")&&s.residents().stream().filter(Resident::alive).count()>6&&absent.isEmpty()&&witnessed.containsAll(EnumSet.allOf(Profession.class))&&s.civilization().level()==6&&completed.containsAll(ResearchCatalog.NODES.keySet())&&!HallUpgradeGoal.pending(l,village)&&terminal.isEmpty();
  if(!done&&elapsed>=Long.getLong("villageastra.autonomyGrowthTicks",72000L))failure="Observation limit reached before full progression; active="+elapsed+" civ="+s.civilization().level()+" research="+completed.size()+" roles="+roles;
 }
}
