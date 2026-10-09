package org.villageastra.world;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.server.SettlementData;

/** Bounded asynchronous lookup of an existing saved body, without creating entities or terrain. */
final class SavedMinerLookup {
 private SavedMinerLookup(){}
 private static final int BATCH=16;
 private static final class Search {
  final UUID village;final BlockPos center;final List<ChunkPos> chunks;
  final Map<ChunkPos,CompletableFuture<Optional<CompoundTag>>> pending=new LinkedHashMap<>();
  int cursor;long nextPoll,finished=-1;BlockPos found;
  Search(SettlementData.Entry e){
   village=e.settlement().id();center=e.center();var cp=new ChunkPos(center);var list=new ArrayList<ChunkPos>();
   int reach=(HomeNeighborhood.RECOVERY_REACH+15)/16+1;
   for(int x=-reach;x<=reach;x++)for(int z=-reach;z<=reach;z++)list.add(new ChunkPos(cp.x+x,cp.z+z));
   list.sort(Comparator.comparingDouble(p->{double x=p.getMiddleBlockX()-center.getX(),z=p.getMiddleBlockZ()-center.getZ();return x*x+z*z;}));
   chunks=List.copyOf(list);
  }
 }
 private static final Map<ServerLevel,Map<UUID,Search>> searches=new WeakHashMap<>();
 private static long budgetTick=Long.MIN_VALUE;private static int queries;
 static void forget(ServerLevel l,UUID actor){var map=searches.get(l);if(map!=null)map.remove(actor);}
 static void clear(){searches.clear();budgetTick=Long.MIN_VALUE;queries=0;}
 /** Caller has already checked a visited village and the miner's live authoritative work claim. */
 static BlockPos find(ServerLevel l,SettlementData.Entry e,UUID actor){
  if(!(l.entityManager.permanentStorage instanceof EntityStorage storage))return null;
  long now=l.getServer().getTickCount();var map=searches.computeIfAbsent(l,k->new HashMap<>());var s=map.get(actor);
  if(s==null||!s.village.equals(e.settlement().id())||!s.center.equals(e.center())||s.finished>=0&&now-s.finished>=1200){s=new Search(e);map.put(actor,s);}
  if(s.found!=null)return s.found;if(now<s.nextPoll)return null;s.nextPoll=now+20;
  for(var it=s.pending.entrySet().iterator();it.hasNext();){var request=it.next();var future=request.getValue();
   if(!future.isDone())continue;it.remove();if(future.isCompletedExceptionally()||future.isCancelled())continue;
   var tag=future.getNow(Optional.empty());if(tag.isEmpty())continue;
   for(var raw:tag.get().getList("Entities",Tag.TAG_COMPOUND)){
    var body=(CompoundTag)raw;
    if(!body.getString("id").equals("villageastra:resident")||!body.hasUUID("UUID")||!body.getUUID("UUID").equals(actor)
      ||!body.hasUUID("AstraSettlement")||!body.getUUID("AstraSettlement").equals(s.village)
      ||body.hasUUID("AstraEscortPlayer")||body.getFloat("Health")<=0)continue;
    var pos=body.getList("Pos",Tag.TAG_DOUBLE);if(pos.size()!=3)continue;
    double x=pos.getDouble(0),y=pos.getDouble(1),z=pos.getDouble(2);
    if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30_000_000||Math.abs(z)>30_000_000
      ||y<l.getMinBuildHeight()||y>=l.getMaxBuildHeight())continue;
    var at=BlockPos.containing(x,y,z);
    if(!new ChunkPos(at).equals(request.getKey())||at.distSqr(s.center)>HomeNeighborhood.RECOVERY_REACH*HomeNeighborhood.RECOVERY_REACH)continue;
    s.found=at;s.pending.clear();return at;
   }
  }
  if(budgetTick!=now){budgetTick=now;queries=0;}
  var folder=DimensionType.getStorageFolder(l.dimension(),l.getServer().getWorldPath(LevelResource.ROOT)).resolve("entities");
  // Native reads must not create an empty region file in previously unvisited territory.
  int considered=0;
  while(s.cursor<s.chunks.size()&&s.pending.size()<BATCH&&queries<BATCH&&considered++<64){
   var cp=s.chunks.get(s.cursor++);
   if(!Files.isRegularFile(folder.resolve("r."+cp.getRegionX()+"."+cp.getRegionZ()+".mca")))continue;
   s.pending.put(cp,storage.worker.loadAsync(cp));queries++;
  }
  if(s.cursor==s.chunks.size()&&s.pending.isEmpty()&&s.finished<0)s.finished=now;
  return null;
 }
}
