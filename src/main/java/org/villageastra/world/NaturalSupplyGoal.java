package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** Demand-driven trips to actual exposed natural deposits. The worker carries the harvested loot back. */
public final class NaturalSupplyGoal extends Goal {
 private final ResidentEntity worker;private final boolean test;private int check=-100,cursor,travel,surveyLeft;private CompoundTag state;private boolean cursorLoaded,surveyPending;private double bestDistance=Double.POSITIVE_INFINITY;private UUID travelJob;
 private int demandCheck=-100;private Set<Item> cachedDemand=Set.of();private boolean cachedMiningPriority;
 private int routePlans;
 private int lastRouteAttempt=-100;private BlockPos lastRouteTarget;private net.minecraft.world.phys.Vec3 lastRouteOrigin;
 private int surveyY=Integer.MAX_VALUE;
 private int surveyRadius=SEARCH_RADIUS;
 private long surveyPlanStart,surveySpentNanos;
 // Finish an individual ore/platform query, then yield before another costly block.
 // Native planning itself is not interrupted halfway through a query.
 private static final int SURVEY_PLANS=4;
 /** Diagnostics: explicit expedition plans, excluding navigation's terrain-change recomputations. */
 public int routePlans(){return routePlans;}
 public static final int SEARCH_RADIUS=192,ROUTE_RANGE=320;
 private static final List<BlockPos> SEARCH=new ArrayList<>();
 static{for(int x=-SEARCH_RADIUS;x<=SEARCH_RADIUS;x++)for(int z=-SEARCH_RADIUS;z<=SEARCH_RADIUS;z++)SEARCH.add(new BlockPos(x,0,z));SEARCH.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));}
 private static final Map<Integer,List<BlockPos>> SEARCH_AREAS=new HashMap<>();
 static{
  SEARCH_AREAS.put(SEARCH_RADIUS,SEARCH);
  for(int radius=SEARCH_RADIUS+64;radius<=ROUTE_RANGE;radius+=64){
   var ring=new ArrayList<BlockPos>();
   for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(Math.max(Math.abs(x),Math.abs(z))>radius-64)ring.add(new BlockPos(x,0,z));
   ring.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));
   var all=new ArrayList<>(SEARCH_AREAS.get(radius-64));all.addAll(ring);SEARCH_AREAS.put(radius,List.copyOf(all));
  }
 }
 private List<BlockPos> searchArea(){return SEARCH_AREAS.get(surveyRadius);}
 /** Exhaust the nearby area before widening; the old column order remains a prefix. */
 private void advanceSearchArea(){
  if(cursor>=searchArea().size()&&surveyRadius<ROUTE_RANGE)surveyRadius+=64;
  cursor=Math.floorMod(cursor,searchArea().size());
 }
 private BlockPos nextColumn(){advanceSearchArea();return searchArea().get(cursor++);}
 public NaturalSupplyGoal(ResidentEntity w){this(w,false);}public NaturalSupplyGoal(ResidentEntity w,boolean test){worker=w;this.test=test;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 public static Path path(ServerLevel l,UUID id){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-natural/"+id+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID id){var p=path(l,id);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 public static boolean active(CompoundTag t){return t.hasUUID("id")&&!t.getBoolean("complete");}
 public static boolean quarryActive(ServerLevel l,UUID worker){var t=inspect(l,worker);return active(t)&&t.getBoolean("quarry");}
 public static boolean eligible(Resident r){return r!=null&&r.alive()&&r.life()==Resident.Life.ADULT&&(r.profession()==Profession.MINER||r.profession()==Profession.FORESTER||r.profession()==null);}
 /** Durable in-flight targets coordinate real workers, including after a goal reload.
  * Finished, sick and unloaded workers cannot strand an otherwise usable deposit. */
 public static Set<BlockPos> reservedTargets(ServerLevel l,SettlementData.Entry e,UUID excluded){
  var targets=new HashSet<BlockPos>();
  for(var r:e.settlement().residents())if(!r.id().equals(excluded)&&eligible(r)&&Population.mayWork(r)
    &&l.getEntity(r.id()) instanceof ResidentEntity body&&e.settlement().id().equals(body.settlementId())){
   var t=inspect(l,r.id());if(active(t)&&Set.of("dig","tool").contains(t.getString("stage"))&&t.contains("target")){targets.add(BlockPos.of(t.getLong("target")));if(t.contains("oreTarget"))targets.add(BlockPos.of(t.getLong("oreTarget")));}
  }return targets;
 }
 private SettlementData.Entry entry(){if(!(worker.level() instanceof ServerLevel)||worker.settlementId()==null||worker.escortPlayer()!=null)return null;var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());return e!=null&&e.dimension().equals(worker.level().dimension().location().toString())&&eligible(e.settlement().resident(worker.getUUID()))?e:null;}
 private void save(){advanceSearchArea();state.putInt("surveyRadius",surveyRadius);state.putInt("surveyCursor",cursor);if(surveyY==Integer.MAX_VALUE)state.remove("surveyY");else state.putInt("surveyY",surveyY);NbtRecord.write(path((ServerLevel)worker.level(),worker.getUUID()),state);}
 private void saveSurvey(){advanceSearchArea();int next=cursor;if(state.getInt("surveyRadius")!=surveyRadius||!state.contains("surveyCursor")||state.getInt("surveyCursor")!=next||(state.contains("surveyY")?state.getInt("surveyY"):Integer.MAX_VALUE)!=surveyY)save();}
 /** AD-131 (check fix 7): no logs Р Р†Р вЂљРІР‚Сњ wood is the forester's, felled a whole wild tree at a time (ForestWork), never a log out of a crown. */
 public static boolean natural(BlockState s){return !s.requiresCorrectToolForDrops()&&(s.is(BlockTags.SAND)||s.is(BlockTags.DIRT)||s.is(BlockTags.FLOWERS)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.SUGAR_CANE)||s.is(Blocks.CACTUS));}
 /** Capability of this gatherer, not a promise that the biome contains the deposit. */
 public static boolean provides(Item item){if(item==Items.CLAY_BALL||item==Items.FLINT)return true;if(!(item instanceof BlockItem block)||item==Items.GRASS_BLOCK||item==Items.PODZOL||item==Items.MYCELIUM||item==Items.ROOTED_DIRT)return false;return natural(block.getBlock().defaultBlockState());}
 /** Cheap rejection of unrelated vanilla soil before protection, fluids and
  * loot previews. Custom blocks retain the full dynamic loot check below. */
 private static boolean mayYield(BlockState s,Set<Item> wanted){
  var block=s.getBlock();
  if(wanted.contains(block.asItem()))return true;
  if(s.is(Blocks.GRAVEL)&&wanted.contains(Items.FLINT)||s.is(Blocks.CLAY)&&wanted.contains(Items.CLAY_BALL))return true;
  if((s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.PODZOL)||s.is(Blocks.MYCELIUM))&&wanted.contains(Items.DIRT))return true;
  return !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
 }
 public static boolean safe(ServerLevel l,BlockPos p){var s=l.getBlockState(p);if(!natural(s)||l.getBlockEntity(p)!=null||OwnershipEvents.protectedBlock(l,p)||!s.getFluidState().isEmpty())return false;
  // Taking the soil would uproot the village's next harvest outside protected building lots.
  var above=l.getBlockState(p.above());
  if((s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY))&&(above.is(BlockTags.SAPLINGS)||above.is(BlockTags.LOGS)||above.is(Blocks.MANGROVE_PROPAGULE)||above.is(Blocks.SUGAR_CANE)||above.is(Blocks.CACTUS)))return false;
  // Keep the growing base, and cut only the top so neighbour updates cannot spill unbooked loot.
  if((s.is(Blocks.SUGAR_CANE)||s.is(Blocks.CACTUS))&&(!l.getBlockState(p.below()).is(s.getBlock())||l.getBlockState(p.above()).is(s.getBlock())))return false;
  if(s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)){
   if(l.getBlockState(p.above()).getBlock() instanceof FallingBlock)return false;
   boolean bankClay=s.is(Blocks.CLAY)&&ClayBankHarvest.shallow(l,p);
   for(var d:Direction.values())if(d!=Direction.DOWN&&l.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.WATER)&&!bankClay)return false;
  }
  for(var d:Direction.values())if(l.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.LAVA))return false;return l.getFluidState(p.above()).isEmpty()||l.getFluidState(p.above(2)).isEmpty();}
 /** Shared with custody recovery: only not-yet-deposited, journal-confirmed goods belong to this trip. */
 private static UUID toolOperation(CompoundTag t){return Settlement.childId(t.hasUUID("toolLoan")?t.getUUID("toolLoan"):t.getUUID("id"),"tool");}
 /** The original loan stays authoritative across adjacent ore jobs; only confirmed blocks wear it. */
 private static ItemStack quarryTool(ServerLevel l,CompoundTag t,boolean harvested){var tool=WorldJournal.recoverAmount(l,toolOperation(t));if(!tool.isEmpty())tool.setDamageValue(tool.getDamageValue()+t.getInt("toolDamage")+(harvested?1:0));return tool;}
 public static ListTag cargo(ServerLevel l,CompoundTag t){var list=t.getList("cargo",Tag.TAG_COMPOUND).copy();if(t.getString("stage").equals("dig")||t.getString("stage").equals("tool")){
  list=t.getList("bag",Tag.TAG_COMPOUND).copy();var receipt=WorldJournal.recoverExisting(l,t.getUUID("id"));if(receipt!=null)list.addAll(receipt.getList("loot",Tag.TAG_COMPOUND).copy());
  if(t.getBoolean("quarry")){var tool=quarryTool(l,t,receipt!=null);if(!tool.isEmpty()&&tool.getDamageValue()<tool.getMaxDamage())list.add(tool.save(new CompoundTag()));}
 }list=HarvestAccessRepair.unpaidCargo(l,t,list);var result=new ListTag();for(int i=0;i<list.size();i++)if(WorldJournal.recoverExisting(l,Settlement.childId(t.getUUID("id"),"deliver/"+i))==null)result.add(list.getCompound(i).copy());return result;}
 public static void release(ServerLevel l,UUID worker){var t=inspect(l,worker);t.putBoolean("complete",true);t.remove("cargo");t.remove("bag");NbtRecord.write(path(l,worker),t);}
 public static Set<Item> demand(ServerLevel l,SettlementData.Entry e){var result=new HashSet<Item>();var wants=Workshops.wants(l,e);for(var w:wants)for(var s:w.ingredient().getItems())result.add(s.getItem());var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest!=null)for(var want:wants.stream().limit(24).toList())for(var need:Workshops.needs(l,e,Workshops.spec("town_hall"),HallReserve.view(l,e,chest),List.of(want)))for(var s:need.ingredient().getItems())result.add(s.getItem());if(result.contains(Items.COAL)||result.contains(Items.CHARCOAL))for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)if(item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.LOGS))result.add(item);return result;}
 /** Do not abandon a tree, renewal, or paid timber delivery for an unrelated raw-material trip. */
 public static boolean primaryForestryPending(ServerLevel l,SettlementData.Entry e,ResidentEntity worker){
  var person=e.settlement().resident(worker.getUUID());var b=e.settlement().workplace(worker.getUUID());
  if(person==null||person.profession()!=Profession.FORESTER||b==null)return false;
  var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+b.id()+".bin");
  if(!Files.exists(file))return false;var work=NbtRecord.read(file);
  if(!work.getList("cargo",Tag.TAG_COMPOUND).isEmpty())return true;
  String stage=work.getString("stage"),status=work.getString("status");
  return Set.of("dig","deliver","sapling","replant","nursery_soil").contains(stage)
      ||stage.equals("choose")&&!Set.of("no_trees_in_reach","seeking_trees","evening","output_full").contains(status);
 }
 public static boolean primaryResourcePending(ServerLevel l,SettlementData.Entry e,ResidentEntity worker){
  if(primaryForestryPending(l,e,worker))return true;
  var person=e.settlement().resident(worker.getUUID());var b=e.settlement().workplace(worker.getUUID());
  if(person==null||person.profession()!=Profession.MINER||b==null)return false;
  var file=MineWork.path(l,b.id());if(!Files.exists(file))return false;var work=NbtRecord.read(file);
  // Do not pin every mining stage: missing materials or a better pick may require
  // a surface trip. Only delivery and already funded beam placement finish first.
  return MineOreWork.active(work)||work.getString("stage").equals("deliver")
      ||work.getString("stage").equals("support_place")&&!MineTimber.stored(work).isEmpty();
 }
 /** An unmet ore order keeps an equipped miner on a remaining drive, but never
  * prevents a surface trip for tools, unsafe ground or an exhausted mine. Pure observation. */
 public static boolean miningPriority(ServerLevel l,SettlementData.Entry e,ResidentEntity worker){
  var person=e.settlement().resident(worker.getUUID());var mine=e.settlement().workplace(worker.getUUID());
  if(person==null||person.profession()!=Profession.MINER||mine==null||!Files.exists(MineWork.path(l,mine.id())))return false;
  var t=MineWork.read(l,mine);String stage=t.getString("stage");
  if(!Set.of("choose","dig").contains(stage)||Set.of("unsafe_ground","tool_tier").contains(t.getString("status")))return false;
  var pick=ItemStack.of(t.getCompound("tool"));
  if(!(pick.getItem() instanceof PickaxeItem)||pick.isDamaged()&&pick.getDamageValue()>=pick.getMaxDamage())return false;
  if(stage.equals("dig")){var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("before"));if(before.requiresCorrectToolForDrops()&&!pick.isCorrectToolForDrops(before))return false;}
  var ores=Set.of(Items.RAW_IRON,Items.RAW_COPPER,Items.RAW_GOLD,Items.COAL,Items.DIAMOND,Items.REDSTONE,Items.LAPIS_LAZULI,Items.EMERALD);
  if(MineProspecting.needed(l,e,mine).stream().noneMatch(ores::contains))return false;
  if(!MineWork.next(l,e,mine,t.copy()).floor())return true;
  var area=e.settlement().mineAreas().get(mine.id());if(area==null||area.galleries().size()>MineArea.MAX_GALLERIES-2)return false;
  var visited=new HashSet<Integer>();for(int floor:t.getIntArray("surveyedFloors"))visited.add(floor);visited.add(t.getInt("floorStep"));for(var gallery:area.galleries())visited.add(gallery.step());
  for(int floor=0;floor<=Math.min(MineWork.floorStep(l,e,mine,t),area.lastStep());floor++)if(!visited.contains(floor))return true;
  return false;
 }
 // Deferred loading, elapsed budget and costly queries resume the exact same survey depth.
 @Override public boolean canUse(){var e=entry();if(e==null||!test&&worker.getServer().getPlayerCount()==0||!CargoCustody.mayStartWork(worker))return false;if(worker.tickCount-check>=20){check=worker.tickCount;surveyLeft=1024;surveyPlanStart=HarvestRouteCache.stats(worker).plans();surveySpentNanos=0;}else if(!surveyPending)return false;surveyPending=false;var l=(ServerLevel)worker.level();state=inspect(l,worker.getUUID());if(!cursorLoaded){int storedRadius=state.getInt("surveyRadius");surveyRadius=SEARCH_AREAS.containsKey(storedRadius)?storedRadius:SEARCH_RADIUS;cursor=Math.floorMod(state.getInt("surveyCursor"),searchArea().size());surveyY=state.contains("surveyY")?state.getInt("surveyY"):Integer.MAX_VALUE;cursorLoaded=true;}if(active(state))return true;if(primaryResourcePending(l,e,worker))return false;if(worker.tickCount-demandCheck>=100){cachedDemand=demand(l,e);cachedMiningPriority=miningPriority(l,e,worker);demandCheck=worker.tickCount;}if(cachedMiningPriority)return false;var wanted=cachedDemand;if(wanted.isEmpty())return false;var reserved=reservedTargets(l,e,worker.getUUID());if(wanted.contains(Items.SUGAR_CANE)){var ready=ReedNursery.mature(worker,e,reserved);if(ready!=null)return begin(ready.target(),ready.stand(),l.getBlockState(ready.target()),false);}
  boolean quarry=SurfaceQuarry.mayStart(l,e,worker);var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  // Exposed building stone in a reachable cave is a quarry demand too; keep the
  // same shaft floor, native route, safety, paid tool and per-block labor guards.
  var quarryDemand=new HashSet<Item>(wanted);quarryDemand.retainAll(Set.of(Items.RAW_IRON,Items.RAW_COPPER,Items.RAW_GOLD,Items.COAL,Items.DIAMOND,Items.REDSTONE,Items.LAPIS_LAZULI,Items.EMERALD,Items.ANDESITE,Items.GRANITE,Items.DIORITE,Items.TUFF,Items.SANDSTONE,Items.RED_SANDSTONE));
  boolean deepQuarry=quarry&&!quarryDemand.isEmpty();
  var quarryMine=deepQuarry?e.settlement().workplace(worker.getUUID()):null;
  int quarryFloor=quarryMine==null?l.getMinBuildHeight():MineWork.floorY(l,e,quarryMine,BuildingTiers.level(l,e,quarryMine));
  // Pending continuation shares one allowance across the whole 20-tick window;
  // another selector pass must not immediately replenish an exhausted budget.
  long surveyStarted=System.nanoTime(),surveyDeadline=surveyStarted+Math.max(0L,5_000_000L-surveySpentNanos),surveyPlans=surveyPlanStart;
  try(var sensing=HarvestRouteCache.survey(worker)){
  if(System.nanoTime()<surveyDeadline&&HarvestRouteCache.stats(worker).plans()-surveyPlans<SURVEY_PLANS
    &&knownLoose(l,e,wanted,reserved,surveyDeadline,surveyPlans))return true;
  if(quarry&&nearbyQuarry(l,e,quarryDemand,reserved,surveyDeadline,surveyPlans))return true;
  if(System.nanoTime()<surveyDeadline&&HarvestRouteCache.stats(worker).plans()-surveyPlans<SURVEY_PLANS
    &&quarry&&resumeFace(l,e,quarryDemand,reserved))return true;
  if(System.nanoTime()<surveyDeadline&&HarvestRouteCache.stats(worker).plans()-surveyPlans<SURVEY_PLANS
    &&quarry&&resumeQuarryDeposit(l,e,quarryDemand,reserved,surveyDeadline,surveyPlans))return true;
  if(System.nanoTime()<surveyDeadline&&HarvestRouteCache.stats(worker).plans()-surveyPlans<SURVEY_PLANS
    &&quarry&&knownQuarry(l,e,quarryDemand,reserved,surveyDeadline,surveyPlans))return true;
  if(System.nanoTime()<surveyDeadline&&HarvestRouteCache.stats(worker).plans()-surveyPlans<SURVEY_PLANS
    &&resumeLoose(l,wanted,reserved,surveyDeadline,surveyPlans))return true;
  for(;surveyLeft>0;surveyLeft--){if(System.nanoTime()>=surveyDeadline){surveyPending=true;break;}var offset=nextColumn();var column=e.center().offset(offset);if(!l.hasChunkAt(column)){var touch=TouchLoad.ensure(l,column);if(touch!=TouchLoad.Touch.OK){cursor--;surveyPending=touch==TouchLoad.Touch.DEFERRED;saveSurvey();return false;}}int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());int start=Math.min(Math.min(l.getMaxBuildHeight()-1,top+3),surveyY);surveyY=Integer.MAX_VALUE;for(int y=start;y>=Math.max(l.getMinBuildHeight(),deepQuarry?quarryFloor:top-8);y--){if(System.nanoTime()>=surveyDeadline||HarvestRouteCache.stats(worker).plans()-surveyPlans>=SURVEY_PLANS)return pauseSurvey(y);var pos=new BlockPos(column.getX(),y,column.getZ());if(reserved.contains(pos))continue;var before=l.getBlockState(pos);if(y<top-8&&!MineOutcrops.wanted(before,quarryDemand))continue;boolean quarryYield=quarry&&(MineOutcrops.wanted(before,wanted)||before.is(Blocks.STONE)&&wanted.contains(Items.COBBLESTONE)||before.is(Blocks.DEEPSLATE)&&wanted.contains(Items.COBBLED_DEEPSLATE));if(!mayYield(before,wanted)&&!quarryYield)continue;if(Boolean.getBoolean("villageastra.firstHouseSmoke")&&before.is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.above()).is(Blocks.SUGAR_CANE))com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE plantCandidate pos={} wanted={} safe={}",pos,wanted.contains(Items.SUGAR_CANE),safe(l,pos));boolean stone=quarry&&SurfaceQuarry.safe(l,pos);int slot=stone?SurfaceQuarry.tool(l,e,before):-1;if(stone&&slot<0||!stone&&!safe(l,pos))continue;var pick=stone?chest.getItem(slot):ItemStack.EMPTY;var loot=Block.getDrops(before,l,pos,null,worker,pick);if(loot.stream().noneMatch(s->wanted.contains(s.getItem())))continue;
    if(!ResourceExpedition.survey(worker,pos))return pauseSurvey(y);BlockPos stand;if(!stone&&LooseHarvestAccess.loose(before)){var choice=LooseHarvestAccess.find(worker,pos,wanted,reserved,ROUTE_RANGE,(int)Math.max(0,SURVEY_PLANS-(HarvestRouteCache.stats(worker).plans()-surveyPlans)));if(choice==null)stand=null;else{pos=choice.target();before=l.getBlockState(pos);stand=choice.stand();}}else stand=HarvestAccess.find(worker,pos,ROUTE_RANGE);if(Boolean.getBoolean("villageastra.firstHouseSmoke")&&before.is(Blocks.SUGAR_CANE))com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE plantAccess pos={} from={} stand={}",pos,worker.blockPosition(),stand);
    if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke")&&stone&&MineOutcrops.wanted(before,quarryDemand))com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_CAVE_SUPPLY actor={} ore={} from={} stand={} floor={}",worker.getUUID(),pos,worker.blockPosition(),stand,quarryFloor);
    if(stand==null){if(stone&&MineOutcrops.wanted(before,quarryDemand)){var face=QuarryFace.find(worker,pos,reserved);if(face!=null){begin(face.target(),face.stand(),l.getBlockState(face.target()),true);state.putLong("oreTarget",pos.asLong());state.putInt("faceDepth",1);save();if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke"))com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_QUARRY_FACE actor={} ore={} rock={} stand={} depth=1",worker.getUUID(),pos,face.target(),face.stand());return true;}}continue;}return begin(pos,stand,before,stone);}}
  saveSurvey();return false;
  }finally{surveySpentNanos+=System.nanoTime()-surveyStarted;}
 }
 // Yield between blocks, never skip an unexamined depth when a native query is costly.
 /** A delivered loose-material trip can leave another small load at its known
  * deposit. Check that neighbourhood once before the full village survey. Each
  * candidate still needs fresh safety, loot and reversible native-route checks;
  * a successful revisit starts a new unpaid job, never replays delivered cargo. */
 private boolean resumeLoose(ServerLevel l,Set<Item> wanted,Set<BlockPos> reserved,long deadline,long plans){
  if(!state.getBoolean("complete")||state.getBoolean("quarry")||state.getBoolean("looseResumeChecked")
    ||!state.contains("target")||!state.contains("before"))return false;
  var material=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
  if(!(material.is(BlockTags.SAND)||material.is(BlockTags.DIRT)||material.is(Blocks.GRAVEL)||material.is(Blocks.CLAY))||!mayYield(material,wanted))return false;
  var previous=BlockPos.of(state.getLong("target"));
  if(!l.hasChunkAt(previous)||!ResourceExpedition.survey(worker,previous))return false;
  var candidates=new ArrayList<BlockPos>();
  for(var p:BlockPos.betweenClosed(previous.offset(-4,-2,-4),previous.offset(4,2,4)))
   if(!reserved.contains(p)&&l.hasChunkAt(p)&&l.getBlockState(p).is(material.getBlock())&&safe(l,p))candidates.add(p.immutable());
  candidates.sort(Comparator.comparingDouble(p->p.distSqr(previous)));
  for(int index=state.getInt("looseResumeCursor");index<candidates.size();index++){
   if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS){state.putInt("looseResumeCursor",index);save();return false;}
   var p=candidates.get(index);
   var before=l.getBlockState(p);
   if(Block.getDrops(before,l,p,null,worker,ItemStack.EMPTY).stream().noneMatch(s->wanted.contains(s.getItem())))continue;
   var choice=LooseHarvestAccess.find(worker,p,wanted,reserved,ROUTE_RANGE,
     (int)Math.max(0,SURVEY_PLANS-(HarvestRouteCache.stats(worker).plans()-plans)));
   if(choice!=null)return begin(choice.target(),choice.stand(),l.getBlockState(choice.target()),false);
  }
  state.putBoolean("looseResumeChecked",true);state.remove("looseResumeCursor");save();
  return false;
 }
 private UUID sharedLooseOwner,sharedLooseReceipt;private int sharedLooseCell,sharedLooseResident;private long sharedLooseRetry;
 private final Map<UUID,Long> sharedLooseMisses=new HashMap<>();
 /** A colleague's committed loose harvest is a lead, not stock or a reserved route.
  * Job metadata alone never proves a deposit. Terrain, demand, safety and travel are checked anew. */
 private boolean knownLoose(ServerLevel l,SettlementData.Entry e,Set<Item> wanted,Set<BlockPos> reserved,long deadline,long plans){
  long now=l.getGameTime();if(now<sharedLooseRetry)return false;
  var residents=e.settlement().residents().stream().map(Resident::id).filter(id->!id.equals(worker.getUUID())).sorted().toList();
  sharedLooseMisses.entrySet().removeIf(x->x.getValue()<=now);
  for(int visited=0;visited<residents.size();visited++){
   if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS){surveyPending=true;return false;}
   if(sharedLooseResident>=residents.size())sharedLooseResident=0;var owner=residents.get(sharedLooseResident);
   var old=inspect(l,owner);
   if(!old.hasUUID("id")||!old.getBoolean("complete")||old.getBoolean("quarry")||!old.contains("target")||!old.contains("before")){sharedLooseResident++;continue;}
   var id=old.getUUID("id");if(sharedLooseMisses.containsKey(id)){sharedLooseResident++;continue;}
   var material=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),old.getCompound("before"));var previous=BlockPos.of(old.getLong("target"));
   if(!LooseHarvestAccess.loose(material)||!mayYield(material,wanted)||Math.abs(previous.getX()-e.center().getX())>ROUTE_RANGE||Math.abs(previous.getZ()-e.center().getZ())>ROUTE_RANGE){sharedLooseResident++;continue;}
   var receipt=WorldJournal.inspectCommitted(l,id);
   if(receipt==null||!receipt.getString("kind").equals("block")||receipt.getLong("pos")!=previous.asLong()||!receipt.getCompound("before").equals(old.getCompound("before"))){sharedLooseResident++;continue;}
   if(!id.equals(sharedLooseReceipt)||!owner.equals(sharedLooseOwner)){sharedLooseOwner=owner;sharedLooseReceipt=id;sharedLooseCell=0;}
   if(!ResourceExpedition.survey(worker,previous))return false;
   var positions=new ArrayList<BlockPos>();for(var p:BlockPos.betweenClosed(previous.offset(-4,-2,-4),previous.offset(4,2,4)))positions.add(p.immutable());positions.sort(Comparator.comparingDouble(p->p.distSqr(previous)));
   for(;sharedLooseCell<positions.size();sharedLooseCell++){
    if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS){surveyPending=true;return false;}
    var pos=positions.get(sharedLooseCell);if(!l.hasChunkAt(pos)||reserved.contains(pos))continue;var before=l.getBlockState(pos);
    if(!before.is(material.getBlock())||!safe(l,pos)||Block.getDrops(before,l,pos,null,worker,ItemStack.EMPTY).stream().noneMatch(stack->wanted.contains(stack.getItem())))continue;
    var choice=LooseHarvestAccess.find(worker,pos,wanted,reserved,ROUTE_RANGE,(int)Math.max(0,SURVEY_PLANS-(HarvestRouteCache.stats(worker).plans()-plans)));
    if(choice!=null){sharedLooseOwner=null;sharedLooseReceipt=null;sharedLooseCell=0;return begin(choice.target(),choice.stand(),l.getBlockState(choice.target()),false);}
   }
   sharedLooseMisses.put(id,now+1200);while(sharedLooseMisses.size()>128)sharedLooseMisses.remove(sharedLooseMisses.keySet().iterator().next());sharedLooseResident++;sharedLooseOwner=null;sharedLooseReceipt=null;sharedLooseCell=0;
  }
  sharedLooseRetry=now+100;return false;
 }
 /** Consider a completed short face once before the general survey. This starts
  * a new loan and job; the previous cargo and receipts remain completed. */
 private int localQuarryCheck=-1000;
 /** Notice demanded rock beside the body before distant cave sensing consumes
  * the window. Read only a small loaded neighbourhood, at most once per100 body
  * ticks, sharing the existing elapsed/native budget and extraction guards. */
 private boolean nearbyQuarry(ServerLevel l,SettlementData.Entry e,Set<Item> wanted,Set<BlockPos> reserved,long deadline,long plans){
  if(worker.tickCount-localQuarryCheck<100)return false;
  localQuarryCheck=worker.tickCount;var feet=worker.blockPosition();
  for(int radius=0;radius<=2;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
   if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
   for(int dy=-1;dy<=1;dy++){
    if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS)return false;
    var pos=feet.offset(dx,dy,dz);if(!l.hasChunkAt(pos)||reserved.contains(pos))continue;var before=l.getBlockState(pos);
    if(!MineOutcrops.wanted(before,wanted)||!SurfaceQuarry.safe(l,pos)||SurfaceQuarry.tool(l,e,before)<0)continue;
    if(!ResourceExpedition.survey(worker,pos))continue;var stand=HarvestAccess.find(worker,pos,ROUTE_RANGE);
    if(stand!=null)return begin(pos,stand,before,true);
   }
  }return false;
 }
 private boolean resumeFace(ServerLevel l,SettlementData.Entry e,Set<Item> wanted,Set<BlockPos> reserved){
  if(!state.getBoolean("complete")||!state.getBoolean("quarry")||!state.contains("oreTarget")||state.getBoolean("faceResumeChecked"))return false;
  var ore=BlockPos.of(state.getLong("oreTarget"));
  if(!l.hasChunkAt(ore)||!ResourceExpedition.survey(worker,ore))return false;
  state.putBoolean("faceResumeChecked",true);save();
  var before=l.getBlockState(ore);
  if(reserved.contains(ore)||!MineOutcrops.wanted(before,wanted)||!SurfaceQuarry.safe(l,ore)||SurfaceQuarry.tool(l,e,before)<0)return false;
  var stand=HarvestAccess.find(worker,ore,ROUTE_RANGE);
  if(stand!=null)return begin(ore,stand,before,true);
  var face=QuarryFace.find(worker,ore,reserved);if(face==null)return false;
  if(SurfaceQuarry.tool(l,e,l.getBlockState(face.target()))<0)return false;
  begin(face.target(),face.stand(),l.getBlockState(face.target()),true);
  state.putLong("oreTarget",ore.asLong());state.putInt("faceDepth",1);save();return true;
 }
 /** A completed, journal-confirmed mineral trip remembers its last deposit.
  * Recheck a finite nearby volume before the distant survey, without reusing
  * the old cargo, loan or route. Each new trip borrows and pays normally. */
 private boolean resumeQuarryDeposit(ServerLevel l,SettlementData.Entry e,Set<Item> wanted,Set<BlockPos> reserved,long deadline,long plans){
  if(!state.getBoolean("complete")||!state.getBoolean("quarry")||state.getBoolean("quarryResumeChecked")
    ||!state.hasUUID("id")||!state.contains("target")||!state.contains("before"))return false;
  var material=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
  if(!QuarryFace.resource(material)||!MineOutcrops.wanted(material,wanted)||SurfaceQuarry.tool(l,e,material)<0)return false;
  var receipt=WorldJournal.recoverExisting(l,state.getUUID("id"));var previous=BlockPos.of(state.getLong("target"));
  if(receipt==null||!receipt.getString("kind").equals("block")||receipt.getLong("pos")!=previous.asLong()
    ||!receipt.getCompound("before").equals(state.getCompound("before")))return false;
  if(!l.hasChunkAt(previous)||!ResourceExpedition.survey(worker,previous))return false;
  // Stable geometry order makes a yielded cursor meaningful after body movement
  // or reload; absent/changed/unsafe blocks are still checked again as encountered.
  var positions=new ArrayList<BlockPos>();for(var p:BlockPos.betweenClosed(previous.offset(-4,-2,-4),previous.offset(4,2,4)))positions.add(p.immutable());
  positions.sort(Comparator.comparingDouble(p->p.distSqr(previous)));
  for(int index=state.getInt("quarryResumeCursor");index<positions.size();index++){
   if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS){state.putInt("quarryResumeCursor",index);surveyPending=true;save();return false;}
   var p=positions.get(index);if(reserved.contains(p)||!l.hasChunkAt(p)||!l.getBlockState(p).is(material.getBlock())||!SurfaceQuarry.safe(l,p))continue;
   var stand=HarvestAccess.find(worker,p,ROUTE_RANGE);if(stand!=null)return begin(p,stand,l.getBlockState(p),true);
  }
  state.putBoolean("quarryResumeChecked",true);state.remove("quarryResumeCursor");save();return false;
 }
 private UUID knownSite;private int knownCell;private long knownRetry;private final Map<UUID,Long> knownMisses=new HashMap<>();
 /** Shared remembered deposits survive unrelated trips. Actual terrain, demand,
  * paid tool availability and fresh native navigation still decide each job. */
 private boolean knownQuarry(ServerLevel l,SettlementData.Entry e,Set<Item> wanted,Set<BlockPos> reserved,long deadline,long plans){
  QuarryKnowledge.poll(l);if(System.nanoTime()>=deadline||l.getGameTime()<knownRetry)return false;
  var mine=e.settlement().workplace(worker.getUUID());if(mine==null)return false;int floor=MineWork.floorY(l,e,mine,BuildingTiers.level(l,e,mine));
  var sites=QuarryKnowledge.sites(l).stream().filter(s->MineOutcrops.wanted(s.material(),wanted)
   &&Math.abs(s.pos().getX()-e.center().getX())<=ROUTE_RANGE&&Math.abs(s.pos().getZ()-e.center().getZ())<=ROUTE_RANGE
   &&s.pos().getY()>=floor)
   .sorted(Comparator.comparingDouble(s->s.pos().distSqr(worker.blockPosition()))).toList();
  if(knownSite!=null&&sites.stream().noneMatch(s->s.receipt().equals(knownSite))){knownSite=null;knownCell=0;}
  long now=l.getGameTime();knownMisses.entrySet().removeIf(x->x.getValue()<=now);
  for(var site:sites){
   if(knownMisses.containsKey(site.receipt())||knownSite!=null&&!knownSite.equals(site.receipt())||SurfaceQuarry.tool(l,e,site.material())<0)continue;
   if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS)return false;
   if(!ResourceExpedition.survey(worker,site.pos()))return false;
   knownSite=site.receipt();var positions=new ArrayList<BlockPos>();for(var p:BlockPos.betweenClosed(site.pos().offset(-4,-2,-4),site.pos().offset(4,2,4)))positions.add(p.immutable());positions.sort(Comparator.comparingDouble(p->p.distSqr(site.pos())));
   for(;knownCell<positions.size();knownCell++){
    if(System.nanoTime()>=deadline||HarvestRouteCache.stats(worker).plans()-plans>=SURVEY_PLANS){surveyPending=true;return false;}
    var p=positions.get(knownCell);if(p.getY()<floor||!l.hasChunkAt(p))continue;var before=l.getBlockState(p);if(reserved.contains(p)||!before.is(site.material().getBlock())||!MineOutcrops.wanted(before,wanted)||!SurfaceQuarry.safe(l,p))continue;
    var stand=HarvestAccess.find(worker,p,ROUTE_RANGE);if(stand!=null){knownSite=null;knownCell=0;return begin(p,stand,before,true);}
   }
   knownMisses.put(site.receipt(),now+1200);knownSite=null;knownCell=0;
  }
  knownRetry=now+100;return false;
 }
 private boolean pauseSurvey(int y){cursor--;surveyY=y;surveyPending=true;saveSurvey();return false;}
 private boolean begin(BlockPos pos,BlockPos stand,BlockState before,boolean stone){state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.putLong("target",pos.asLong());state.putLong("stand",stand.asLong());state.put("before",NbtUtils.writeBlockState(before));state.putBoolean("quarry",stone);state.putString("stage",stone?"tool":"dig");save();return true;}
 @Override public boolean canContinueToUse(){return entry()!=null&&(test||worker.getServer().getPlayerCount()>0)&&active(state)&&!CargoCustody.pending(worker.getServer(),worker.getUUID());}
 // A long reachable trip must not expire while making new progress toward its destination.
 private void resetTravel(){travel=0;bestDistance=Double.POSITIVE_INFINITY;}
 private boolean needsRoute(BlockPos destination){
  var route=worker.getNavigation().getPath();
  // A partial route can still carry the body toward the current job. Keep that
  // progress until its end; navigation still reacts to changed terrain, and a
  // hundred ticks without progress, a sleep detour or a new target replans.
  return route==null||route.isDone()||!destination.equals(route.getTarget())||travel>=100;
 }
 /** A stationary failure waits at most five seconds before another full search.
  * A changed job or displaced body is reconsidered at the normal next cadence. */
 private boolean readyToPlan(BlockPos destination){
  return travel<100||lastRouteTarget==null||!destination.equals(lastRouteTarget)
    ||lastRouteOrigin==null||worker.position().distanceToSqr(lastRouteOrigin)>.25
    ||worker.tickCount-lastRouteAttempt>=100;
 }
 @Override public void start(){
  // Sleep and furniture recovery can interrupt the same inaccessible trip indefinitely.
  // Keep its actual non-progress budget; only a different job starts a new attempt.
  var job=state.getUUID("id");if(!job.equals(travelJob)){resetTravel();travelJob=job;}ReedNursery.resume(state);
 }
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){var e=entry();if(e==null)return;ResourceExpedition.follow(worker,test);if(ReedNursery.tick(worker,e,state))return;var l=(ServerLevel)worker.level();if(HarvestAccessRepair.reconcile(l,e,worker,state))save();if(state.getString("stage").equals("carry")&&state.getList("cargo",Tag.TAG_COMPOUND).isEmpty()){state.putBoolean("complete",true);save();worker.getNavigation().stop();worker.workStatus("logistics_idle");return;}var hall=Workshops.hall(e);if(hall==null)return;var target=BlockPos.of(state.getLong("target"));var stock=LogisticsRoutes.position(e,hall);var deliveries=state.getLongArray("deliveryTargets");int delivered=state.getInt("delivered");if(state.getString("stage").equals("carry")&&delivered<deliveries.length)stock=BlockPos.of(deliveries[delivered]);boolean digging=state.getString("stage").equals("dig");var to=digging?(state.contains("stand")?BlockPos.of(state.getLong("stand")):target.above()):stock.east();if(!digging){var carried=state.getList("cargo",Tag.TAG_COMPOUND);int next=state.getInt("delivered");worker.displayWorkItem(next<carried.size()?ItemStack.of(carried.getCompound(next)):ItemStack.EMPTY);}
  double distance=worker.distanceToSqr(to.getX()+.5,to.getY(),to.getZ()+.5);boolean precise=digging&&state.contains("stand");
  // A high ledge may hide a lower ore from a body within the normal .4-block
  // arrival margin. Finish the physical approach before spending its labor.
  boolean refine=precise&&distance<2.25&&(state.getBoolean("quarry")||ClayBankHarvest.shallow(l,target))&&!WorldJournal.exists(l,state.getUUID("id"))&&!HarvestAccess.visible(worker,target);
  if(refine||distance>(precise?.16:5)){double remaining=Math.sqrt(distance);if(remaining+.5<bestDistance){bestDistance=remaining;travel=0;}if(precise&&distance<2.25){worker.getNavigation().stop();var aim=refine&&distance<.04?HarvestAccess.workPoint(worker,to,target):net.minecraft.world.phys.Vec3.atBottomCenterOf(to);worker.getMoveControl().setWantedPosition(aim.x,aim.y,aim.z,refine?Math.min(.8,Math.max(.15,remaining*1.5)):.8);}else if(worker.tickCount%20==0&&(worker.onGround()||worker.isInWaterOrBubble())&&ResourceExpedition.survey(worker,to)){if(needsRoute(to)&&readyToPlan(to)){lastRouteAttempt=worker.tickCount;lastRouteTarget=to.immutable();lastRouteOrigin=worker.position();routePlans++;var route=digging?worker.routeTo(to,0,ROUTE_RANGE):ResourceReturnRoute.plan(worker,to);if(route==null)worker.getNavigation().stop();else worker.getNavigation().moveTo(route,.8);}}worker.workStatus(digging?"seeking_materials":"delivering");if(!digging&&travel>1200&&(worker.getNavigation().getPath()==null||!worker.getNavigation().getPath().canReach())&&HarvestAccessRepair.restore(l,e,worker,state)){save();resetTravel();worker.workStatus("delivering");return;}if(++travel>2400&&digging&&!WorldJournal.exists(l,state.getUUID("id"))){if(Boolean.getBoolean("villageastra.firstHouseSmoke")){var route=worker.getNavigation().getPath();var nodes=new ArrayList<Object>();if(route!=null)for(int i=route.getNextNodeIndex();i<Math.min(route.getNodeCount(),route.getNextNodeIndex()+4);i++)nodes.add(route.getNodePos(i));com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE tripTimeout pos={} goal={} distance={} ground={} reachable={} next={}",worker.position(),to,distance,worker.onGround(),route!=null&&route.canReach(),nodes);}returnCargo(l);}return;}
  worker.getNavigation().stop();if(worker.tickCount%20!=0)return;
  if(state.getString("stage").equals("tool")){var id=toolOperation(state);var pick=WorldJournal.recoverAmount(l,id);var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));int slot=SurfaceQuarry.tool(l,e,before);var chest=LogisticsRoutes.chest(l,e,hall);
   if(pick.isEmpty()&&!WorldJournal.exists(l,id)&&slot>=0)pick=WorldJournal.takeAmount(l,id,stock,slot,chest.getItem(slot).copy(),1);
   if(pick.isEmpty()){state.putBoolean("complete",true);save();return;}state.putString("stage","dig");resetTravel();save();return;}
  if(digging){if(!WorldJournal.exists(l,state.getUUID("id"))&&ClayBankHarvest.shallow(l,target)&&!ClayBankHarvest.dryBody(worker,target)){returnCargo(l);return;}if(state.contains("stand")&&!HarvestAccess.standing(l,to,target)&&!WorldJournal.exists(l,state.getUUID("id"))){returnCargo(l);return;}if(!WorldJournal.exists(l,state.getUUID("id"))&&!(state.getBoolean("quarry")?SurfaceQuarry.safe(l,target):safe(l,target))){returnCargo(l);return;}worker.workStatus("gathering_materials");state.putInt("labor",state.getInt("labor")+20);if(state.getInt("labor")<200){save();return;}var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));var pick=state.getBoolean("quarry")?quarryTool(l,state,false):ItemStack.EMPTY;
   if(state.getBoolean("quarry")&&(pick.isEmpty()||pick.getDamageValue()>=pick.getMaxDamage()||!pick.isCorrectToolForDrops(before)||!WorldJournal.exists(l,state.getUUID("id"))&&!HarvestAccess.visible(worker,target))){
    if(Boolean.getBoolean("villageastra.autonomyGrowthSmoke"))com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_QUARRY_REJECT actor={} job={} target={} stand={} pos={} eye={} pick={} damage={} correctTool={} actualVisible={} plannedVisible={} receipt={}",worker.getUUID(),state.getUUID("id"),target,to,worker.position(),worker.getEyePosition(),pick.getItem(),pick.getDamageValue(),pick.isCorrectToolForDrops(before),HarvestAccess.visible(worker,target),HarvestAccess.visible(worker,to,target),WorldJournal.exists(l,state.getUUID("id")));
    returnCargo(l);return;
   }
   WorldJournal.harvest(l,state.getUUID("id"),target,before,pick);finishHarvest(l,target,before);return;}
  var cargo=state.getList("cargo",Tag.TAG_COMPOUND);int index=state.getInt("delivered");if(index<cargo.size()){var stack=ItemStack.of(cargo.getCompound(index));worker.displayWorkItem(stack);if(!WorldJournal.deposit(l,Settlement.childId(state.getUUID("id"),"deliver/"+index),stock,stack)){worker.workStatus("output_full");return;}state.putInt("delivered",index+1);save();}else{state.putBoolean("complete",true);save();}
 }
 private void finishHarvest(ServerLevel l,BlockPos target,BlockState before){
  var held=cargo(l,state);boolean quarry=state.getBoolean("quarry");int amount=0;var pick=ItemStack.EMPTY;var bag=new ListTag();
  for(var raw:held){var stack=ItemStack.of((CompoundTag)raw);if(quarry&&stack.getItem() instanceof PickaxeItem){pick=stack;continue;}amount+=stack.getCount();bag.add(raw.copy());}
  var reserved=reservedTargets(l,entry(),worker.getUUID());boolean harvested=WorldJournal.recoverExisting(l,state.getUUID("id"))!=null;
  if(quarry&&harvested)QuarryKnowledge.remember(l.getServer(),WorldJournal.recoverExisting(l,state.getUUID("id")));
  var face=quarry&&harvested&&!pick.isEmpty()?QuarryFace.next(worker,state,reserved):null;
  var next=face!=null?new BulkHarvest.Next(face.target(),face.stand()):quarry?(harvested?OreBulkHarvest.next(worker,target,before,pick,amount,reserved):null):BulkHarvest.next(worker,target,before,amount,reserved);
  if(next==null){returnCargo(l);return;}
  // One atomic record moves the confirmed old receipt into the bag and starts a fresh harvest id.
  // Before this save recovery reads the old receipt; after it recovery reads the bag, never both.
  var previous=state;state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.put("bag",quarry?bag:held);state.putLong("target",next.target().asLong());state.putLong("stand",next.stand().asLong());state.put("before",NbtUtils.writeBlockState(l.getBlockState(next.target())));state.putString("stage","dig");
  if(quarry){state.putBoolean("quarry",true);state.putUUID("toolLoan",previous.hasUUID("toolLoan")?previous.getUUID("toolLoan"):previous.getUUID("id"));state.putInt("toolDamage",previous.getInt("toolDamage")+1);}
  if(face!=null&&!face.target().equals(BlockPos.of(previous.getLong("oreTarget")))){state.putLong("oreTarget",previous.getLong("oreTarget"));state.putInt("faceDepth",previous.getInt("faceDepth")+1);}
  resetTravel();save();
 }
 private void returnCargo(ServerLevel l){var held=cargo(l,state);state.put("cargo",held);state.remove("bag");var e=entry();if(e!=null)state.putLongArray("deliveryTargets",MineStairWork.deliveries(l,e,worker.getUUID(),held));state.putString("stage","carry");state.putInt("delivered",0);resetTravel();save();}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(ItemStack.EMPTY);}
}
