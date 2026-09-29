package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Receipt-backed ownership, separate from disposable worker cargo and excavation cursors. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=org.villageastra.VillageAstra.ID)
public final class MinePlugs {
 private MinePlugs(){}
 private static final Map<ServerLevel,CompoundTag> CACHE=new WeakHashMap<>();
 private static Path path(ServerLevel l){var d=l.dimension().location();return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-mine-plugs/"+d.getNamespace()+"/"+d.getPath()+".bin");}
 private static CompoundTag data(ServerLevel l){return CACHE.computeIfAbsent(l,k->Files.exists(path(k))?NbtRecord.read(path(k)):new CompoundTag());}
 private static void save(ServerLevel l){NbtRecord.write(path(l),data(l));}
 public static void reload(ServerLevel l){CACHE.remove(l);}
 private static String key(BlockPos p){return "p"+p.asLong();}
 private static boolean fill(CompoundTag r){
  var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),r.getCompound("before"));
  var after=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),r.getCompound("after"));
  return r.getInt("schema")==1&&r.getBoolean("committed")&&r.getString("kind").equals("block")&&!r.contains("loot")&&before.is(Blocks.WATER)&&MineSealing.material(new net.minecraft.world.item.ItemStack(after.getBlock()));
 }
 /** Before placement: an interrupted intent alone never grants permission to mine. */
 public static void plan(ServerLevel l,Settlement.Building mine,BlockPos p,UUID receipt){
  var proof=new CompoundTag();proof.putUUID("mine",mine.id());proof.putUUID("receipt",receipt);data(l).put(key(p),proof);save(l);
 }
 /** Import old, committed water fills in a claimed drive and its immediate boundary, outside blueprint structures. */
 private static void legacy(ServerLevel l,Settlement.Building mine){
  var root=data(l);String marker="imported/v2/"+mine.id();if(root.getBoolean(marker))return;
  var e=SettlementData.get(l.getServer()).entries().stream().filter(x->x.settlement().buildings().stream().anyMatch(b->b.id().equals(mine.id()))).findFirst().orElse(null);
  if(e==null)return;var area=e.settlement().mineAreas().get(mine.id());if(area==null)return;
  var blueprint=BuildingPlacement.layout(e,mine,"mine");var dir=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-journal");
  if(Files.isDirectory(dir))try(var files=Files.list(dir)){
   for(var file:files.filter(p->p.toString().endsWith(".bin")).toList()){
    var r=NbtRecord.read(file);if(!r.getString("dimension").equals(l.dimension().location().toString())||!fill(r))continue;
    var p=BlockPos.of(r.getLong("pos"));var local=BuildingPlacement.local(e,mine,p);
    boolean beside=area.contains(local.getX(),local.getY(),local.getZ(),0)||Arrays.stream(net.minecraft.core.Direction.values()).map(local::relative).anyMatch(q->area.contains(q.getX(),q.getY(),q.getZ(),0));
    if(root.contains(key(p))||!beside||blueprint.containsKey(p)||OwnershipEvents.protectedBlock(l,p,b->!b.id().equals(mine.id())))continue;
    var proof=new CompoundTag();proof.putUUID("mine",mine.id());proof.putUUID("receipt",r.getUUID("id"));root.put(key(p),proof);
   }
  }catch(java.io.IOException ex){throw new IllegalStateException("Cannot recover mine plugs",ex);}
  root.putBoolean(marker,true);save(l);
 }
 public static boolean owns(ServerLevel l,Settlement.Building mine,BlockPos p){
  legacy(l,mine);var proof=data(l).getCompound(key(p));if(!proof.hasUUID("mine")||!mine.id().equals(proof.getUUID("mine"))||!proof.hasUUID("receipt")||OwnershipEvents.protectedBlock(l,p,b->!b.id().equals(mine.id())))return false;
  var r=WorldJournal.inspectCommitted(l,proof.getUUID("receipt"));if(r==null||!fill(r)||r.getLong("pos")!=p.asLong())return false;
  return l.getBlockState(p).equals(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),r.getCompound("after")));
 }
 /** Keep a tombstone so legacy import cannot resurrect a mined plug. */
 public static void harvested(ServerLevel l,BlockPos p){if(!Files.exists(path(l))&&!CACHE.containsKey(l))return;var root=data(l);if(root.contains(key(p))&&!root.getCompound(key(p)).isEmpty()){root.put(key(p),new CompoundTag());save(l);}}
 @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
 public static void broken(net.minecraftforge.event.level.BlockEvent.BreakEvent e){if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel l)harvested(l,e.getPos());}
 @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
 public static void placed(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e){if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel l){
  if(e instanceof net.minecraftforge.event.level.BlockEvent.EntityMultiPlaceEvent m)for(var s:m.getReplacedBlockSnapshots())harvested(l,s.getPos());else harvested(l,e.getPos());
 }}
}
