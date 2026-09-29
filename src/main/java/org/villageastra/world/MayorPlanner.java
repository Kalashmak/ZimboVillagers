package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.server.BookResearch;
/** AD-032: an NPC mayor chooses the next project from real shortages, walks to a surveyed site and approves the paid order there. */
public final class MayorPlanner {
 public static final long PLAN_INTERVAL=2400;public static final int SURVEYS_PER_PASS=24,MAX_RADIUS=120;
 /** A chosen site waiting for the mayor to arrive. */
 public record Proposal(String design,BlockPos site,String reason){}
 private static final Map<UUID,Proposal> PROPOSALS=new HashMap<>();
 private static final Map<UUID,Integer> CURSOR=new HashMap<>();
 private MayorPlanner(){}
 public static void clear(){PROPOSALS.clear();CURSOR.clear();WALL_TRIED.clear();}
 public static Proposal proposal(UUID settlement){return PROPOSALS.get(settlement);}
 private static boolean npcMayor(Settlement s){return s.governance().playerMayor()==null&&s.residents().stream().anyMatch(r->r.alive()&&r.profession()==Profession.MAYOR);}
 private static boolean has(Settlement s,String type){return s.buildings().stream().anyMatch(b->org.villageastra.domain.CoreCatalog.canonical(AnnexTypes.workplace(b.type())).equals(type));}
 /** Next design from shortages, in order: housing, school for children, food processing, materials, storage. */
 public static String need(Settlement s){return need(s,type->true);}
 /** The shortages, skipping a design the village may not order yet (AD-136, CF13: the school, the warehouse and the workshops need their research). */
 private static String need(Settlement s,java.util.function.Predicate<String> orderable){
  long free=s.homes().stream().filter(Settlement.Home::usable).mapToLong(h->h.capacity()-s.occupancy(h.id())).sum();
  boolean homeless=s.residents().stream().anyMatch(r->r.alive()&&r.home()==null);
  boolean children=s.residents().stream().anyMatch(r->r.alive()&&r.life()==Resident.Life.CHILD);
  if(free==0||homeless)return s.civilization().level()>=2?"home_2":"home";
  if(children&&!has(s,"school")&&orderable.test("school"))return "school";
  boolean idle=s.residents().stream().anyMatch(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.profession()==null);
  if(idle)for(var type:List.of("mill","restaurant","carpentry","masonry","warehouse","smithy"))if(!has(s,type)&&orderable.test(type))return type;
  return null;
 }
 /** AD-123 (H4, C7): the same shortages, but the big house only once its research (Housing II) is done; otherwise the standard house,
  *  so an NPC village without the research keeps growing instead of stalling on a refused design. AD-136 (CF13): a design its research
  *  still refuses is passed over, so the school of a village without Education I never holds up everything else. */
 public static String need(ServerLevel l,SettlementData.Entry e){var n=need(e.settlement(),type->!Set.of("mill","carpentry","masonry").contains(type)&&ResearchGate.designRefusal(l,e,type).isEmpty());return "home_2".equals(n)?HousingLadder.houseFor(l,e):n;}
 /** AD-101: what a village of its own mayor builds next — the shortages first, then the watch and the barracks once it is large enough
  *  and has the research for them, so an NPC village grows the way a played one does. */
 public static String wanted(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();var civic=need(l,e);if(civic!=null)return civic;
  // The old list stopped after six workshops, so a village never even proposed its laboratory.
  if(s.residents().stream().anyMatch(r->r.alive()&&r.life()==Resident.Life.ADULT&&r.profession()==null))
   for(var type:List.of("livestock","laboratory","clinic","cartographer","engineering","caravan","expedition","archery"))
    if(!has(s,type)&&ResearchGate.designRefusal(l,e,type).isEmpty())return type;
  long adults=s.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT).count();
  if(adults>=GUARD_AT&&!has(s,"guard_house")&&ResearchGate.designRefusal(l,e,"guard_house").isEmpty())return "guard_house";
  if(adults>=BARRACKS_AT&&!has(s,"barracks")&&ResearchGate.designRefusal(l,e,"barracks").isEmpty())return "barracks";
  return null;
 }
 public static final int GUARD_AT=8,BARRACKS_AT=12;
 /** AD-094: a village of its own mayor walls itself in from this many adults on, looking for a ring at most this often. */
 public static final int WALL_AT=12;public static final long WALL_EVERY=12000;
 private static final Map<UUID,Long> WALL_TRIED=new HashMap<>();
 /** AD-101: with nothing to build, a village of its own mayor studies and raises its buildings as a player's would: it picks its next
  *  research, and it orders the next level of a building whose research is done and whose estimate it has or can make (affordable). */
 public static String develop(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(!npcMayor(s))return "";
  var studied=BookResearch.autoSelect(l,e);
  if(HallUpgradeGoal.pending(l,s.id()))return studied.isEmpty()?"":"research:"+studied;
  for(var parent:List.copyOf(s.buildings()))for(var k:Annexes.of(parent.type())){
   if(!Annexes.refusal(l,e,parent,k).isEmpty())continue;var survey=Annexes.survey(l,e,parent,k);if(!survey.ok())continue;
   var cost=new LinkedHashMap<String,Integer>();var price=survey.state().getCompound("cost");price.getAllKeys().forEach(key->cost.put(key,price.getInt(key)));
   if(affordable(l,e,cost)&&Annexes.order(l,e,parent,k).isEmpty())return "annex:"+k.type();
  }
  for(var b:List.copyOf(s.buildings())){
   if(!BuildingTiers.upgradable(b.type())||!BuildingTiers.refusal(l,e,b).isEmpty())continue;
   if(!affordable(l,e,b))continue;
   if(BuildingTiers.order(l,e,b).isEmpty())return "level:"+b.type();
  }
  var wall=wall(l,e);if(!wall.isEmpty())return "wall:"+wall;
  return studied.isEmpty()?"":"research:"+studied;
 }
 /** AD-112, AD-137 (addendum, owner 2026-09-23): a building's next level may be ordered when every item of its estimate either lies in the
  *  hall or is one the village can make more of from what it brings in (Workshops.producible, recursive: the workshops and annexes it has
  *  and the hall's crafts, down to the raw output of its farm, forester's hut, mine and pen). The order reserves what lies there (HallReserve)
  *  and the shortfall becomes workshop demand (Workshops.wants). An item only a workshop the village lacks makes, or a raw resource nobody
  *  brings in (netherite: ring VI without a player's block), still refuses the order, so a village of its own stops at level V. */
 public static boolean affordable(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return affordable(l,e,BuildingTiers.cost(b.type(),BuildingTiers.built(e,b)+1,b.wood()));}
 public static boolean affordable(ServerLevel l,SettlementData.Entry e,Map<String,Integer> cost){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return false;
  for(var entry:cost.entrySet()){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(entry.getKey()));
   if(LogisticsRoutes.count(chest,s->s.is(item))>=entry.getValue())continue;
   if(!Workshops.producible(l,e,entry.getKey()))return false;}
  return true;
 }
 /** AD-137 (addendum): the single project slot of a village of its own waits for items its workshops still have to make. A project that
  *  gains nothing towards its estimate (items the builder holds or that lie in the hall, operations done) for STALL_DAYS is reported in
  *  the office — never called off silently. Kept per village in data/astra-stall (project, what was covered, since when). */
 public static final long STALL_DAYS=3,DAY=24000,WATCH_EVERY=1200;
 private static java.nio.file.Path stallFile(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-stall/"+village+".bin");}
 /** What the queued project holds towards its estimate: items carried or lying in the hall (each at most what it needs) and operations done. */
 static int covered(ServerLevel l,SettlementData.Entry e,net.minecraft.nbt.CompoundTag state){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);var cost=state.getCompound("cost");int n=state.getInt("index");
  for(var key:cost.getAllKeys()){var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key));int have=0;
   for(var raw:state.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND)){var st=net.minecraft.world.item.ItemStack.of((net.minecraft.nbt.CompoundTag)raw);if(st.is(item))have+=st.getCount();}
   if(chest!=null)have+=LogisticsRoutes.count(chest,st->st.is(item));n+=Math.min(have,cost.getInt(key));}
  return n;
 }
 /** Notes the headway of the village's unfunded project; forgets it once the project is funded, done or gone. */
 public static void watch(ServerLevel l,SettlementData.Entry e,long now){
  var id=e.settlement().id();var file=stallFile(l,id);
  try{
   if(!HallUpgradeGoal.pending(l,id)){java.nio.file.Files.deleteIfExists(file);return;}
   var state=HallUpgradeGoal.inspect(l,id);if(state.getBoolean("funded")||!state.hasUUID("id")){java.nio.file.Files.deleteIfExists(file);return;}
   var project=HallConstructionPlan.projectId(state);int covered=covered(l,e,state);
   var seen=java.nio.file.Files.exists(file)?org.villageastra.persistence.NbtRecord.read(file):null;
   if(seen!=null&&seen.hasUUID("project")&&seen.getUUID("project").equals(project)&&covered<=seen.getInt("covered")){
    // Something taken back out is no headway either: the mark only follows it down, so that bringing it again is not counted as new.
    if(covered<seen.getInt("covered")){seen.putInt("covered",covered);org.villageastra.persistence.NbtRecord.write(file,seen);}return;}
   var t=new net.minecraft.nbt.CompoundTag();t.putUUID("project",project);t.putInt("covered",covered);t.putLong("since",now);
   java.nio.file.Files.createDirectories(file.getParent());org.villageastra.persistence.NbtRecord.write(file,t);
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** Whole days this project of a village of its own has gained nothing, once that reaches STALL_DAYS; otherwise 0. */
 public static long stalledDays(ServerLevel l,SettlementData.Entry e,net.minecraft.nbt.CompoundTag state,long now){
  if(!npcMayor(e.settlement())||state.getBoolean("funded")||state.getBoolean("complete")||!state.hasUUID("id"))return 0;
  var file=stallFile(l,e.settlement().id());if(!java.nio.file.Files.exists(file))return 0;var seen=org.villageastra.persistence.NbtRecord.read(file);
  if(!seen.hasUUID("project")||!seen.getUUID("project").equals(HallConstructionPlan.projectId(state)))return 0;
  long days=(now-seen.getLong("since"))/DAY;return days>=STALL_DAYS?days:0;
 }
 /** AD-094: a grown village that knows guard posts orders its castle wall — the smallest ring round the hall that stands clear, round
  *  before square. Returns the ring ordered ("round24"), or empty. */
 public static String wall(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(!npcMayor(s)||!Walls.refusal(l,e).isEmpty()||Roads.active(l,s.id()))return "";
  if(s.residents().stream().filter(r->r.alive()&&r.life()==Resident.Life.ADULT).count()<WALL_AT)return "";
  // AD-127: a walled village's wall follows it — a fitted one grows, an old square or round one the village outgrew is replaced.
  if(Walls.record(l.getServer(),s.id())!=null){var followed=Walls.follow(l,e,false);return followed.startsWith("extend:")?followed:"";}
  long now=l.getGameTime();var last=WALL_TRIED.get(s.id());if(last!=null&&now-last<WALL_EVERY)return "";WALL_TRIED.put(s.id(),now);
  // AD-127: a village with buildings round its hall is walled in fitted to them; a bare hall keeps the AD-094 smallest ring.
  if(s.buildings().stream().anyMatch(b->!b.type().equals("town_hall")&&!b.type().equals(Walls.TOWER))&&Walls.order(l,e,Walls.Shape.FITTED,Walls.FIT.headroom()).isEmpty())return "fitted";
  for(int r=Walls.MIN_RADIUS;r<=48;r+=4)for(var shape:List.of(Walls.Shape.ROUND,Walls.Shape.SQUARE)){
   if(!Walls.plan(l,e,shape,r).reason().isEmpty())continue;
   return Walls.order(l,e,shape,r).isEmpty()?shape.name().toLowerCase(Locale.ROOT)+r:"";}
  return "";
 }
 /** Nearest free surveyed site on a spiral of ground positions around the hall; a bounded number of surveys per pass. */
 public static BlockPos site(ServerLevel l,SettlementData.Entry e,String design){
  var candidates=new ArrayList<BlockPos>();
  for(int r=12;r<=MAX_RADIUS;r+=4)for(int a=0;a<360;a+=Math.max(10,360/(r/2))){
   int x=e.center().getX()+(int)Math.round(r*Math.cos(Math.toRadians(a))),z=e.center().getZ()+(int)Math.round(r*Math.sin(Math.toRadians(a)));
   candidates.add(new BlockPos(x,0,z));
  }
  int start=CURSOR.getOrDefault(e.settlement().id(),0)%candidates.size();
  for(int i=0;i<Math.min(SURVEYS_PER_PASS,candidates.size());i++){
   var c=candidates.get((start+i)%candidates.size());if(!l.hasChunkAt(c))continue;
   BlockPos ground=null;
   // Walk down from above the hall level: the first sturdy block with two free cells above is the site ground (tree crowns and roofs excluded by the free cells).
   for(int y=e.center().getY()+6;y>=e.center().getY()-6&&ground==null;y--){var p=new BlockPos(c.getX(),y,c.getZ());if(l.getBlockState(p).isFaceSturdy(l,p,net.minecraft.core.Direction.UP)&&l.getBlockState(p.above()).canBeReplaced()&&l.getBlockState(p.above(2)).canBeReplaced()&&l.getFluidState(p.above()).isEmpty())ground=p;}
   if(ground==null||!GrowthPlots.available(e,design,ground,0))continue;
   if(BuildingOrders.survey(l,e,design,0,ground).ok()){CURSOR.put(e.settlement().id(),(start+i)%candidates.size());return ground;}
  }
  CURSOR.put(e.settlement().id(),(start+SURVEYS_PER_PASS)%candidates.size());
  return null;
 }
 /** Planner pass for NPC-mayor settlements without an active project. */
 public static void tick(MinecraftServer server,SettlementData data,SettlementData.Entry e){
  var s=e.settlement();if(!npcMayor(s))return;var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));if(l==null)return;
  // An unfunded building must not freeze the independent research/material queue.
  if(data.clock().ticks()%PLAN_INTERVAL<20)BookResearch.autoSelect(l,e);
  if(HallUpgradeGoal.pending(l,s.id())){PROPOSALS.remove(s.id());if(data.clock().ticks()%WATCH_EVERY<20)watch(l,e,l.getGameTime());return;}
  var current=PROPOSALS.get(s.id());if(current!=null&&!current.design().equals(wanted(l,e)))PROPOSALS.remove(s.id());
  if(PROPOSALS.containsKey(s.id())||data.clock().ticks()%PLAN_INTERVAL>=20)return;
  if(plan(l,e)==null)develop(l,e);
 }
 /** One bounded planning pass: the current need and, if found, a surveyed site. */
 public static Proposal plan(ServerLevel l,SettlementData.Entry e){
  var s=e.settlement();if(!npcMayor(s)||HallUpgradeGoal.pending(l,s.id()))return null;
  var design=wanted(l,e);if(design==null)return null;var site=site(l,e,design);if(site==null)return null;
  var proposal=new Proposal(design,site,"shortage");PROPOSALS.put(s.id(),proposal);return proposal;
 }
 /** Called by the mayor standing at the site: re-survey and approve through the same paid queue as a player order. */
 public static String approveAtSite(ServerLevel l,SettlementData.Entry e,ResidentEntity mayor){
  var p=PROPOSALS.get(e.settlement().id());if(p==null)return "no_proposal";
  if(mayor.distanceToSqr(p.site().getX()+.5,p.site().getY()+1,p.site().getZ()+.5)>BuildingOrders.SITE_DISTANCE*BuildingOrders.SITE_DISTANCE)return "walking";
  if(!GrowthPlots.available(e,p.design(),p.site(),0)){PROPOSALS.remove(e.settlement().id());return "rejected_growth_space";}
  var reason=BuildingOrders.approve(l,e,p.design(),0,p.site());PROPOSALS.remove(e.settlement().id());
  if(reason.isEmpty()){SettlementData.get(l.getServer()).setDirty();return "approved";}
  return "rejected_"+reason;
 }
}
