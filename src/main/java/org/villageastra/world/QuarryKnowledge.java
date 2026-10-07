package org.villageastra.world;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.persistence.NbtRecord;
/** Rebuildable knowledge of actual past mineral harvests, never a stock or route cache. */
public final class QuarryKnowledge {
 private QuarryKnowledge(){}
 public static final int CAPACITY=512,READS_PER_WINDOW=32;
 public record Site(UUID receipt,String dimension,BlockPos pos,BlockState material){}
 public record Stats(int sites,long read,boolean complete){}
 private record Batch(List<CompoundTag> records,boolean complete){}
 private static final class Index {
  final List<Site> sites=new ArrayList<>();DirectoryStream<Path> directory;Iterator<Path> files;
  final ExecutorService reader=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"ZimboVillagers mineral history reader");thread.setDaemon(true);return thread;});
  CompletableFuture<Batch> pending;Batch batch;int cursor;long read,lastWindow=Long.MIN_VALUE;boolean complete;
  /** Only this private reader touches the iterator; no world, registry or server access. */
  synchronized Batch readBatch(Path path){
   try{
    if(files==null){if(!Files.isDirectory(path))return new Batch(List.of(),true);directory=Files.newDirectoryStream(path,"*.bin");files=directory.iterator();}
    var records=new ArrayList<CompoundTag>();
    for(int n=0;n<READS_PER_WINDOW;n++){
     if(!files.hasNext()){directory.close();directory=null;files=null;return new Batch(records,true);}
     records.add(NbtRecord.read(files.next()));
    }
    return new Batch(records,false);
   }catch(IOException e){throw new IllegalStateException("Cannot read mineral discovery history",e);}
  }
  synchronized void close(){if(directory!=null)try{directory.close();directory=null;files=null;}catch(IOException e){throw new IllegalStateException(e);}}
 }
 private static final Map<MinecraftServer,Index> INDEXES=new WeakHashMap<>();
 private static Index index(MinecraftServer server){return INDEXES.computeIfAbsent(server,k->new Index());}
 /** The reader prepares at most one32record batch. Only the server applies knowledge,
  * within the original shared32records/2ms allowance once per20server ticks. */
 public static void poll(ServerLevel level){
  var server=level.getServer();var index=index(server);long now=server.getTickCount();
  if(index.complete||index.lastWindow!=Long.MIN_VALUE&&now>=index.lastWindow&&now-index.lastWindow<20)return;index.lastWindow=now;
  if(index.pending!=null){if(!index.pending.isDone())return;index.batch=index.pending.join();index.pending=null;index.cursor=0;}
  if(index.batch!=null){
   long until=System.nanoTime()+2_000_000L;
   for(int n=0;n<READS_PER_WINDOW&&index.cursor<index.batch.records().size()&&System.nanoTime()<until;n++){
    remember(server,index.batch.records().get(index.cursor++));index.read++;
   }
   if(index.cursor<index.batch.records().size())return;
   boolean done=index.batch.complete();index.batch=null;index.cursor=0;if(done){index.complete=true;return;}
  }
  var path=server.getWorldPath(LevelResource.ROOT).resolve("data/astra-journal");
  index.pending=CompletableFuture.supplyAsync(()->index.readBatch(path),index.reader);
 }
 /** Caller supplies a checksum-verified journal record; incomplete or non-harvest operations teach nothing. */
 public static void remember(MinecraftServer server,CompoundTag receipt){
  if(receipt.getInt("schema")!=1||!receipt.getBoolean("committed")||!receipt.hasUUID("id")||!receipt.getString("kind").equals("block")
   ||receipt.getString("dimension").isEmpty()||receipt.getList("loot",Tag.TAG_COMPOUND).isEmpty())return;
  var material=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),receipt.getCompound("before"));
  if(!QuarryFace.resource(material))return;var site=new Site(receipt.getUUID("id"),receipt.getString("dimension"),BlockPos.of(receipt.getLong("pos")),material);var sites=index(server).sites;
  for(int i=0;i<sites.size();i++){var old=sites.get(i);if(old.receipt().equals(site.receipt()))return;
   if(old.dimension().equals(site.dimension())&&old.material().getBlock()==material.getBlock()&&old.pos().distSqr(site.pos())<=64){sites.set(i,site);return;}}
  if(sites.size()==CAPACITY)sites.remove(0);sites.add(site);
 }
 /** Continue only an already requested recovery while workers sleep or finish another task.
  * poll retains the same shared time/file quota, including calls from worker goals. */
 public static void tick(MinecraftServer server){if(INDEXES.containsKey(server))poll(server.overworld());}
 public static List<Site> sites(ServerLevel level){return index(level.getServer()).sites.stream().filter(s->s.dimension().equals(level.dimension().location().toString())).toList();}
 public static Stats stats(MinecraftServer server){var i=index(server);return new Stats(i.sites.size(),i.read,i.complete);}
 public static void clear(MinecraftServer server){var i=INDEXES.remove(server);if(i!=null){i.reader.shutdownNow();i.close();}}
}
