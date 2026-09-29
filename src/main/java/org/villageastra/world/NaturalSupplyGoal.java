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
 private final ResidentEntity worker;private final boolean test;private int check=-100,cursor,travel,surveyLeft;private CompoundTag state;private boolean cursorLoaded,surveyPending;private double bestDistance=Double.POSITIVE_INFINITY;
 public static final int SEARCH_RADIUS=192,ROUTE_RANGE=320;
 private static final List<BlockPos> SEARCH=new ArrayList<>();
 static{for(int x=-SEARCH_RADIUS;x<=SEARCH_RADIUS;x++)for(int z=-SEARCH_RADIUS;z<=SEARCH_RADIUS;z++)SEARCH.add(new BlockPos(x,0,z));SEARCH.sort(Comparator.comparingDouble(p->p.distSqr(BlockPos.ZERO)));}
 public NaturalSupplyGoal(ResidentEntity w){this(w,false);}public NaturalSupplyGoal(ResidentEntity w,boolean test){worker=w;this.test=test;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 public static Path path(ServerLevel l,UUID id){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-natural/"+id+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID id){var p=path(l,id);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 public static boolean active(CompoundTag t){return t.hasUUID("id")&&!t.getBoolean("complete");}
 public static boolean quarryActive(ServerLevel l,UUID worker){var t=inspect(l,worker);return active(t)&&t.getBoolean("quarry");}
 public static boolean eligible(Resident r){return r!=null&&r.alive()&&r.life()==Resident.Life.ADULT&&(r.profession()==Profession.MINER||r.profession()==Profession.FORESTER||r.profession()==null);}
 private SettlementData.Entry entry(){if(!(worker.level() instanceof ServerLevel)||worker.settlementId()==null||worker.escortPlayer()!=null)return null;var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());return e!=null&&e.dimension().equals(worker.level().dimension().location().toString())&&eligible(e.settlement().resident(worker.getUUID()))?e:null;}
 private void save(){cursor=Math.floorMod(cursor,SEARCH.size());state.putInt("surveyCursor",cursor);NbtRecord.write(path((ServerLevel)worker.level(),worker.getUUID()),state);}
 private void saveSurvey(){int next=Math.floorMod(cursor,SEARCH.size());if(!state.contains("surveyCursor")||state.getInt("surveyCursor")!=next)save();}
 /** AD-131 (check fix 7): no logs — wood is the forester's, felled a whole wild tree at a time (ForestWork), never a log out of a crown. */
 public static boolean natural(BlockState s){return !s.requiresCorrectToolForDrops()&&(s.is(BlockTags.SAND)||s.is(BlockTags.DIRT)||s.is(BlockTags.FLOWERS)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.SUGAR_CANE)||s.is(Blocks.CACTUS));}
 /** Capability of this gatherer, not a promise that the biome contains the deposit. */
 public static boolean provides(Item item){if(item==Items.CLAY_BALL||item==Items.FLINT)return true;if(!(item instanceof BlockItem block)||item==Items.GRASS_BLOCK||item==Items.PODZOL||item==Items.MYCELIUM||item==Items.ROOTED_DIRT)return false;return natural(block.getBlock().defaultBlockState());}
 public static boolean safe(ServerLevel l,BlockPos p){var s=l.getBlockState(p);if(!natural(s)||l.getBlockEntity(p)!=null||OwnershipEvents.protectedBlock(l,p)||!s.getFluidState().isEmpty())return false;
  // Keep the growing base, and cut only the top so neighbour updates cannot spill unbooked loot.
  if((s.is(Blocks.SUGAR_CANE)||s.is(Blocks.CACTUS))&&(!l.getBlockState(p.below()).is(s.getBlock())||l.getBlockState(p.above()).is(s.getBlock())))return false;
  if(s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)){
   if(l.getBlockState(p.above()).getBlock() instanceof FallingBlock)return false;
   for(var d:Direction.values())if(d!=Direction.DOWN&&l.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.WATER))return false;
  }
  for(var d:Direction.values())if(l.getFluidState(p.relative(d)).is(net.minecraft.tags.FluidTags.LAVA))return false;return l.getFluidState(p.above()).isEmpty()||l.getFluidState(p.above(2)).isEmpty();}
 /** Shared with custody recovery: only not-yet-deposited, journal-confirmed goods belong to this trip. */
 public static ListTag cargo(ServerLevel l,CompoundTag t){var list=t.getList("cargo",Tag.TAG_COMPOUND).copy();if(t.getString("stage").equals("dig")||t.getString("stage").equals("tool")){
  list=t.getList("bag",Tag.TAG_COMPOUND).copy();var receipt=WorldJournal.recoverExisting(l,t.getUUID("id"));if(receipt!=null)list.addAll(receipt.getList("loot",Tag.TAG_COMPOUND).copy());
  if(t.getBoolean("quarry")){var tool=WorldJournal.recoverAmount(l,Settlement.childId(t.getUUID("id"),"tool"));if(!tool.isEmpty()){if(receipt!=null)tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()<tool.getMaxDamage())list.add(tool.save(new CompoundTag()));}}
 }var result=new ListTag();for(int i=0;i<list.size();i++)if(WorldJournal.recoverExisting(l,Settlement.childId(t.getUUID("id"),"deliver/"+i))==null)result.add(list.getCompound(i).copy());return result;}
 public static void release(ServerLevel l,UUID worker){var t=inspect(l,worker);t.putBoolean("complete",true);t.remove("cargo");t.remove("bag");NbtRecord.write(path(l,worker),t);}
 public static Set<Item> demand(ServerLevel l,SettlementData.Entry e){var result=new HashSet<Item>();var wants=Workshops.wants(l,e);for(var w:wants)for(var s:w.ingredient().getItems())result.add(s.getItem());var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest!=null)for(var want:wants.stream().limit(24).toList())for(var need:Workshops.needs(l,e,Workshops.spec("town_hall"),HallReserve.view(l,e,chest),List.of(want)))for(var s:need.ingredient().getItems())result.add(s.getItem());if(result.contains(Items.COAL)||result.contains(Items.CHARCOAL))for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)if(item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.LOGS))result.add(item);return result;}
 // Deferred chunk loading resumes the same bounded survey, rather than waiting for a fresh window.
 @Override public boolean canUse(){var e=entry();if(e==null||!test&&worker.getServer().getPlayerCount()==0||!CargoCustody.mayStartWork(worker))return false;if(worker.tickCount-check>=100){check=worker.tickCount;surveyLeft=128;}else if(!surveyPending)return false;surveyPending=false;var l=(ServerLevel)worker.level();state=inspect(l,worker.getUUID());if(!cursorLoaded){cursor=Math.floorMod(state.getInt("surveyCursor"),SEARCH.size());cursorLoaded=true;}if(active(state))return true;var wanted=demand(l,e);if(wanted.isEmpty())return false;if(wanted.contains(Items.SUGAR_CANE)){var ready=ReedNursery.mature(worker,e);if(ready!=null)return begin(ready.target(),ready.stand(),l.getBlockState(ready.target()),false);}
  boolean quarry=SurfaceQuarry.mayStart(l,e,worker);var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);
  for(;surveyLeft>0;surveyLeft--){var offset=SEARCH.get(cursor++%SEARCH.size());var column=e.center().offset(offset);if(!l.hasChunkAt(column)){var touch=TouchLoad.ensure(l,column);if(touch!=TouchLoad.Touch.OK){cursor--;surveyPending=touch==TouchLoad.Touch.DEFERRED;saveSurvey();return false;}}int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());for(int y=Math.min(l.getMaxBuildHeight()-1,top+3);y>=Math.max(l.getMinBuildHeight(),top-8);y--){var pos=new BlockPos(column.getX(),y,column.getZ());var before=l.getBlockState(pos);if(Boolean.getBoolean("villageastra.firstHouseSmoke")&&before.is(Blocks.SUGAR_CANE)&&!l.getBlockState(pos.above()).is(Blocks.SUGAR_CANE))com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE plantCandidate pos={} wanted={} safe={}",pos,wanted.contains(Items.SUGAR_CANE),safe(l,pos));boolean stone=quarry&&SurfaceQuarry.safe(l,pos);int slot=stone?SurfaceQuarry.tool(l,e,before):-1;if(stone&&slot<0||!stone&&!safe(l,pos))continue;var pick=stone?chest.getItem(slot):ItemStack.EMPTY;var loot=Block.getDrops(before,l,pos,null,worker,pick);if(loot.stream().noneMatch(s->wanted.contains(s.getItem())))continue;
    if(!ResourceExpedition.survey(worker,pos)){cursor--;surveyPending=true;saveSurvey();return false;}var stand=HarvestAccess.find(worker,pos,ROUTE_RANGE);if(Boolean.getBoolean("villageastra.firstHouseSmoke")&&before.is(Blocks.SUGAR_CANE))com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE plantAccess pos={} from={} stand={}",pos,worker.blockPosition(),stand);if(stand==null)continue;return begin(pos,stand,before,stone);}}
  saveSurvey();return false;
 }
 private boolean begin(BlockPos pos,BlockPos stand,BlockState before,boolean stone){state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.putLong("target",pos.asLong());state.putLong("stand",stand.asLong());state.put("before",NbtUtils.writeBlockState(before));state.putBoolean("quarry",stone);state.putString("stage",stone?"tool":"dig");save();return true;}
 @Override public boolean canContinueToUse(){return entry()!=null&&(test||worker.getServer().getPlayerCount()>0)&&active(state)&&!CargoCustody.pending(worker.getServer(),worker.getUUID());}
 // A long reachable trip must not expire while making new progress toward its destination.
 private void resetTravel(){travel=0;bestDistance=Double.POSITIVE_INFINITY;}
 @Override public void start(){resetTravel();ReedNursery.resume(state);}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){var e=entry();if(e==null)return;ResourceExpedition.follow(worker,test);if(ReedNursery.tick(worker,e,state))return;var l=(ServerLevel)worker.level();var hall=Workshops.hall(e);if(hall==null)return;var target=BlockPos.of(state.getLong("target"));var stock=LogisticsRoutes.position(e,hall);var deliveries=state.getLongArray("deliveryTargets");int delivered=state.getInt("delivered");if(state.getString("stage").equals("carry")&&delivered<deliveries.length)stock=BlockPos.of(deliveries[delivered]);boolean digging=state.getString("stage").equals("dig");var to=digging?(state.contains("stand")?BlockPos.of(state.getLong("stand")):target.above()):stock.east();if(!digging){var carried=state.getList("cargo",Tag.TAG_COMPOUND);int next=state.getInt("delivered");worker.displayWorkItem(next<carried.size()?ItemStack.of(carried.getCompound(next)):ItemStack.EMPTY);}
  double distance=worker.distanceToSqr(to.getX()+.5,to.getY(),to.getZ()+.5);boolean precise=digging&&state.contains("stand");
  if(distance>(precise?.16:5)){double remaining=Math.sqrt(distance);if(remaining+.5<bestDistance){bestDistance=remaining;travel=0;}if(precise&&distance<2.25){worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(to.getX()+.5,to.getY(),to.getZ()+.5,.8);}else if(worker.tickCount%20==0&&ResourceExpedition.survey(worker,to))worker.getNavigation().moveTo(worker.routeTo(to,0,ROUTE_RANGE),.8);worker.workStatus(digging?"seeking_materials":"delivering");if(++travel>2400&&digging&&!WorldJournal.exists(l,state.getUUID("id"))){if(Boolean.getBoolean("villageastra.firstHouseSmoke")){var route=worker.getNavigation().getPath();var nodes=new ArrayList<Object>();if(route!=null)for(int i=route.getNextNodeIndex();i<Math.min(route.getNodeCount(),route.getNextNodeIndex()+4);i++)nodes.add(route.getNodePos(i));com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE tripTimeout pos={} goal={} distance={} ground={} reachable={} next={}",worker.position(),to,distance,worker.onGround(),route!=null&&route.canReach(),nodes);}returnCargo(l);}return;}
  worker.getNavigation().stop();if(worker.tickCount%20!=0)return;
  if(state.getString("stage").equals("tool")){var id=Settlement.childId(state.getUUID("id"),"tool");var pick=WorldJournal.recoverAmount(l,id);var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));int slot=SurfaceQuarry.tool(l,e,before);var chest=LogisticsRoutes.chest(l,e,hall);
   if(pick.isEmpty()&&!WorldJournal.exists(l,id)&&slot>=0)pick=WorldJournal.takeAmount(l,id,stock,slot,chest.getItem(slot).copy(),1);
   if(pick.isEmpty()){state.putBoolean("complete",true);save();return;}state.putString("stage","dig");resetTravel();save();return;}
  if(digging){if(state.contains("stand")&&!HarvestAccess.standing(l,to,target)&&!WorldJournal.exists(l,state.getUUID("id"))){returnCargo(l);return;}if(!WorldJournal.exists(l,state.getUUID("id"))&&!(state.getBoolean("quarry")?SurfaceQuarry.safe(l,target):safe(l,target))){returnCargo(l);return;}worker.workStatus("gathering_materials");state.putInt("labor",state.getInt("labor")+20);if(state.getInt("labor")<200){save();return;}var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));var pick=state.getBoolean("quarry")?WorldJournal.recoverAmount(l,Settlement.childId(state.getUUID("id"),"tool")):ItemStack.EMPTY;
   if(state.getBoolean("quarry")&&(pick.isEmpty()||!pick.isCorrectToolForDrops(before))){returnCargo(l);return;}
   WorldJournal.harvest(l,state.getUUID("id"),target,before,pick);finishHarvest(l,target,before);return;}
  var cargo=state.getList("cargo",Tag.TAG_COMPOUND);int index=state.getInt("delivered");if(index<cargo.size()){var stack=ItemStack.of(cargo.getCompound(index));worker.displayWorkItem(stack);if(!WorldJournal.deposit(l,Settlement.childId(state.getUUID("id"),"deliver/"+index),stock,stack)){worker.workStatus("output_full");return;}state.putInt("delivered",index+1);save();}else{state.putBoolean("complete",true);save();}
 }
 private void finishHarvest(ServerLevel l,BlockPos target,BlockState before){
  var held=cargo(l,state);int amount=0;for(var raw:held)amount+=ItemStack.of((CompoundTag)raw).getCount();
  var next=state.getBoolean("quarry")?null:BulkHarvest.next(worker,target,before,amount);
  if(next==null){returnCargo(l);return;}
  // One atomic record moves the confirmed old receipt into the bag and starts a fresh harvest id.
  // Before this save recovery reads the old receipt; after it recovery reads the bag, never both.
  state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.put("bag",held);state.putLong("target",next.target().asLong());state.putLong("stand",next.stand().asLong());state.put("before",NbtUtils.writeBlockState(l.getBlockState(next.target())));state.putString("stage","dig");resetTravel();save();
 }
 private void returnCargo(ServerLevel l){var held=cargo(l,state);state.put("cargo",held);state.remove("bag");var e=entry();if(e!=null)state.putLongArray("deliveryTargets",MineStairWork.deliveries(l,e,worker.getUUID(),held));state.putString("stage","carry");state.putInt("delivered",0);resetTravel();save();}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(ItemStack.EMPTY);}
}
