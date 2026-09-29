package org.villageastra.world;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.NbtRecord;
/** AD-119: conservative, journalled conversion of the pre-AD117 equipment layout. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class ArchitectureMigration {
 private ArchitectureMigration(){}
 private static MinecraftServer server;
 private static final Map<UUID,CompoundTag> RECORDS=new HashMap<>();
 private static final Map<UUID,String> NOTICES=new HashMap<>();
 public static void forget(){server=null;RECORDS.clear();NOTICES.clear();}
 public static Path path(ServerLevel l,UUID building){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-architecture/"+building+".bin");}
 private static CompoundTag record(ServerLevel l,Settlement.Building b){if(server!=l.getServer()){forget();server=l.getServer();}return RECORDS.computeIfAbsent(b.id(),id->Files.exists(path(l,id))?NbtRecord.read(path(l,id)):new CompoundTag());}
 private static void store(ServerLevel l,Settlement.Building b,CompoundTag t){NbtRecord.write(path(l,b.id()),t);RECORDS.put(b.id(),t);}
 private static boolean done(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var t=record(l,b);return t.getString("phase").equals("done")&&t.getInt("level")>=BuildingTiers.built(e,b);}
 private static CompoundTag completed(int level){var t=new CompoundTag();t.putInt("schema",1);t.putString("phase","done");t.putInt("level",level);return t;}
 /** No automatic repairs or fresh upgrades may interpret an unclassified old layout as missing materials. */
 public static boolean waiting(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return e.settlement().legacyArchitecture().contains(b.id())&&!done(l,e,b);}
 public static Map<BlockPos,BlockState> legacyLayout(String design){String base=BuildingBlueprints.base(design);var out=new LinkedHashMap<>(Legacy117BuildingBlueprints.layout(base,BlockPos.ZERO));if(BuildingBlueprints.level(design)>1)Legacy117Architecture.apply(base,BuildingBlueprints.level(design),BlockPos.ZERO,out);return out;}
 private record Shape(Map<BlockPos,BlockState> old,Map<BlockPos,BlockState> next,Map<BlockPos,BlockPos> moves,Set<BlockPos> changed){}
 private static final Map<String,Shape> SHAPES=new HashMap<>();
 private static Shape shape(String design){return SHAPES.computeIfAbsent(design,id->{var frozen=Legacy117BuildingBlueprints.design(id);var current=BuildingBlueprints.design(id);if(frozen.width()!=current.width()||frozen.depth()!=current.depth())throw new IllegalStateException("AD117 migration footprint changed: "+id);var old=legacyLayout(id);var next=BuildingBlueprints.layout(id,BlockPos.ZERO);var moves=new LinkedHashMap<BlockPos,BlockPos>();String base=BuildingBlueprints.base(id);int level=BuildingBlueprints.level(id);
  if(level>1){var a=Legacy117Architecture.equipment(base);var z=LevelArchitecture.equipment(base);if(a.size()!=z.size())throw new IllegalStateException("Migration equipment catalogue changed: "+id);
   for(int i=0;i<a.size();i++)if(a.get(i).level()<=level){if(a.get(i).state().getBlock()!=z.get(i).state().getBlock())throw new IllegalStateException("Migration equipment identity changed: "+id);moves.put(a.get(i).local(),z.get(i).local());}}
  // AD-122: a redesign may move the design's own stock chest (archery: (1,1,2) -> (1,1,4)); its contents go with it.
  var chest=VillageAstra.OWNED_CHEST.get();var was=chestCell(legacyLayout(base),chest);var now=chestCell(BuildingBlueprints.layout(base,BlockPos.ZERO),chest);
  if(was!=null&&now!=null&&!was.equals(now)&&!moves.containsKey(was))moves.put(was,now);
  var changed=new LinkedHashSet<BlockPos>();var all=new LinkedHashSet<>(old.keySet());all.addAll(next.keySet());for(var p:all)if(!at(old,p).equals(at(next,p)))changed.add(p);
  moves.forEach((a,z)->{if(!a.equals(z)){changed.add(a);changed.add(z);}});
  // AD-122: a container the redesign keeps in its cell but turns (a smoker, a barrel) keeps what is in it.
  for(var p:changed)if(!moves.containsKey(p)&&!moves.containsValue(p)&&at(old,p).hasBlockEntity()&&at(old,p).getBlock()==at(next,p).getBlock())moves.put(p,p);
  return new Shape(old,next,moves,changed);});}
 private static BlockPos chestCell(Map<BlockPos,BlockState> layout,net.minecraft.world.level.block.Block chest){for(var e:layout.entrySet())if(e.getValue().is(chest)&&e.getKey().getY()==1)return e.getKey();return null;}
 private static BlockState at(Map<BlockPos,BlockState> map,BlockPos p){return map.getOrDefault(p,Blocks.AIR.defaultBlockState());}
 private static BlockPos world(SettlementData.Entry e,Settlement.Building b,BlockPos p){return BuildingPlacement.at(e,b,p.getX(),p.getY(),p.getZ());}
 private static boolean matches(ServerLevel l,SettlementData.Entry e,Settlement.Building b,Shape shape,boolean old){for(var p:shape.changed()){var w=world(e,b,p);if(!l.hasChunkAt(w)||!BuildingRepairs.present(l.getBlockState(w),BuildingPlacement.state(at(old?shape.old():shape.next(),p),b.rotation())))return false;}return true;}
 /** While a legacy building waits for its project/chunks, read its actual equipment at the old coordinates. */
 public static boolean legacy(ServerLevel l,SettlementData.Entry e,Settlement.Building b){if(!waiting(l,e,b)||!BuildingTiers.upgradable(b.type()))return false;var s=shape(BuildingTiers.layoutId(b.type(),BuildingTiers.built(e,b)));return !s.changed().isEmpty()&&!matches(l,e,b,s,false)&&matches(l,e,b,s,true);}
 private static CompoundTag snapshot(ServerLevel l,BlockPos p){var t=new CompoundTag();t.put("state",NbtUtils.writeBlockState(l.getBlockState(p)));var be=l.getBlockEntity(p);if(be!=null)t.put("entity",be.saveWithFullMetadata());return t;}
 private static CompoundTag target(BlockState state){var t=new CompoundTag();t.put("state",NbtUtils.writeBlockState(state));return t;}
 /** Persist the entire target before touching a block. Active construction retains its original saved operations. */
 public static String prepare(ServerLevel l,SettlementData.Entry e,Settlement.Building b){if(!waiting(l,e,b))return "done";if(record(l,b).getString("phase").equals("prepared"))return "prepared";
  if(HallUpgradeGoal.pending(l,e.settlement().id()))return "project";
  int level=BuildingTiers.built(e,b);
  // AD-131/AD-138/AD-147: the forester's hut, the livestock yard and the warehouse (13x11 -> 23x17) have no frozen counterpart of their new
  // lots: nothing of theirs is converted (old worlds keep them as they stand, owner 2026-09-19).
  if(!BuildingTiers.upgradable(b.type())||b.type().equals(ForesterHut.TYPE)||b.type().equals("livestock")||b.type().equals(WarehouseStore.TYPE)){RECORDS.put(b.id(),completed(level));return "done";}var s=shape(BuildingTiers.layoutId(b.type(),level));
  if(s.changed().isEmpty()||matches(l,e,b,s,false)){RECORDS.put(b.id(),completed(level));return "done";}
  for(var p:s.changed())if(!l.hasChunkAt(world(e,b,p)))return "unloaded";
  if(!matches(l,e,b,s,true))return "conflict";
  // Smelting jobs can borrow a furnace from a different building and persist its exact position.
  // Let that job finish against its original block and receipts before moving the equipment.
  var changing=new HashSet<Long>();for(var p:s.changed())changing.add(world(e,b,p).asLong());
  for(var workshop:e.settlement().buildings()){var job=Workshops.inspect(l,workshop.id());if(!job.isEmpty()&&!job.getString("stage").equals("idle"))
   for(var key:List.of("furnace","installAt"))if(job.contains(key,Tag.TAG_LONG)&&changing.contains(job.getLong(key)))return "production";}
  for(var p:s.changed()){var w=world(e,b,p);if(l.getBlockState(w).hasBlockEntity()&&l.getBlockEntity(w)==null)return "conflict";}
  for(var p:s.changed())if(!at(s.next(),p).isAir()&&!l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(world(e,b,p))).isEmpty())return "occupied";
  var size=BuildingPlacement.size(b.type(),b.rotation());var origin=BuildingPlacement.origin(e,b);var area=new AABB(origin,origin.offset(size[0],24,size[1])).inflate(8);
  for(var p:l.players())if(area.contains(p.position())&&p.containerMenu!=p.inventoryMenu)p.closeContainer();
  // Persist the saved project, building level and player inventories before publishing the transfer intent.
  l.getServer().saveEverything(false,true,true);
  var targets=new LinkedHashMap<BlockPos,CompoundTag>();for(var p:s.changed())targets.put(p,target(BuildingPlacement.state(at(s.next(),p),b.rotation())));
  for(var move:s.moves().entrySet())if(s.changed().contains(move.getValue())){var snap=snapshot(l,world(e,b,move.getKey()));
   // A container turned in place takes its new state and keeps its contents.
   if(move.getKey().equals(move.getValue())){var t=targets.get(move.getValue());if(snap.contains("entity"))t.put("entity",snap.getCompound("entity"));}else targets.put(move.getValue(),snap);}
  var cells=new ListTag();for(var p:s.changed()){var op=new CompoundTag();op.putLong("pos",world(e,b,p).asLong());op.put("before",snapshot(l,world(e,b,p)));op.put("after",targets.get(p));cells.add(op);}
  var journal=new CompoundTag();journal.putInt("schema",1);journal.putString("phase","prepared");journal.putInt("level",level);journal.putUUID("village",e.settlement().id());journal.putUUID("building",b.id());journal.putString("dimension",e.dimension());journal.putString("type",b.type());journal.putLong("offset",new BlockPos(b.x(),b.y(),b.z()).asLong());journal.putInt("rotation",b.rotation());journal.put("cells",cells);store(l,b,journal);return "prepared";
 }
 /** The journal owns these cells until the target chunks are flushed. No drops, no item payment, no intermediate server tick. */
 public static boolean recover(ServerLevel l,Settlement.Building b){var t=record(l,b);if(!t.getString("phase").equals("prepared"))return false;var cells=t.getList("cells",Tag.TAG_COMPOUND);
  for(var raw:cells){var op=(CompoundTag)raw;var p=BlockPos.of(op.getLong("pos"));l.getChunkAt(p);l.removeBlockEntity(p);}
  for(var raw:cells){var op=(CompoundTag)raw;var p=BlockPos.of(op.getLong("pos"));var after=op.getCompound("after");var state=NbtUtils.readBlockState(l.holderLookup(Registries.BLOCK),after.getCompound("state"));l.setBlock(p,state,2|16);
   if(after.contains("entity")){var nbt=after.getCompound("entity").copy();nbt.putInt("x",p.getX());nbt.putInt("y",p.getY());nbt.putInt("z",p.getZ());var be=BlockEntity.loadStatic(p,state,nbt);if(be==null)throw new IllegalStateException("Cannot restore migration block entity at "+p);l.setBlockEntity(be);be.setChanged();l.sendBlockUpdated(p,state,state,2);}l.getChunkAt(p).setUnsaved(true);}
  l.getChunkSource().save(true);store(l,b,completed(t.getInt("level")));return true;
 }
 private static void finish(ServerLevel l,SettlementData.Entry e,Settlement.Building b){int kept=record(l,b).getInt("level");if(kept>BuildingTiers.built(e,b))BuildingTiers.completed(e,b,kept);e.settlement().finishArchitectureMigration(b.id());SettlementData.get(l.getServer()).setDirty();BuildingLevels.forgetBest(e.settlement().id());}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void started(net.minecraftforge.event.server.ServerStartedEvent event){
  // Recover journals independently of the saved building list: a crash may precede its metadata save.
  var folder=event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-architecture");if(!Files.isDirectory(folder))return;
  try(var files=Files.list(folder)){for(var file:files.filter(p->p.getFileName().toString().endsWith(".bin")).toList()){var t=NbtRecord.read(file);if(!t.getString("phase").equals("prepared"))continue;
   var l=event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(t.getString("dimension"))));if(l==null)throw new IllegalStateException("Missing migration dimension "+t.getString("dimension"));
   var p=BlockPos.of(t.getLong("offset"));var b=new Settlement.Building(t.getUUID("building"),t.getString("type"),p.getX(),p.getY(),p.getZ(),t.getInt("rotation"),t.getInt("level"));recover(l,b);
   var e=SettlementData.get(event.getServer()).entry(t.getUUID("village"));if(e!=null){var existing=e.settlement().buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElse(null);if(existing!=null)finish(l,e,existing);}
  }}catch(java.io.IOException ex){throw new IllegalStateException("Cannot recover architecture journals",ex);}
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.START||event.getServer().getTickCount()%20!=0)return;
  for(var e:SettlementData.get(event.getServer()).entries()){var l=event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(e.dimension())));if(l==null)continue;
   for(var b:e.settlement().buildings())if(e.settlement().legacyArchitecture().contains(b.id())){String status=prepare(l,e,b);if(status.equals("done")){finish(l,e,b);continue;}if(status.equals("prepared")){recover(l,b);finish(l,e,b);return;}
    if(status.equals("conflict")&&!status.equals(NOTICES.put(b.id(),status)))com.mojang.logging.LogUtils.getLogger().warn("ASTRA_MIGRATION conflict village={} building={}; existing blocks kept",e.settlement().id(),b.id());}}
 }
}
