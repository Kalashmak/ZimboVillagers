package org.villageastra.server;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.persistence.NbtRecord;
/** One-time completion after adjacent decoration chunks finish. Never continuously repairs player edits. */
public final class InitialRoads {
 private record Pending(ServerLevel level,UUID id,long[] cells){}
 private static final Map<UUID,Pending> PENDING=new HashMap<>();
 private InitialRoads(){}
 public static void clear(){PENDING.clear();}
 private static Path path(ServerLevel l,UUID id){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-initial-roads/"+id+".bin");}
 public static void queue(ServerLevel l,UUID id,long[] cells){
  if(cells.length==0||cells.length>4096||PENDING.containsKey(id))return;
  var file=path(l,id);var tag=Files.exists(file)?NbtRecord.read(file):new CompoundTag();if(tag.getBoolean("complete"))return;
  if(!tag.contains("cells")){tag.putLongArray("cells",cells);tag.putBoolean("complete",false);NbtRecord.write(file,tag);}
  PENDING.put(id,new Pending(l,id,tag.getLongArray("cells")));
 }
 public static boolean repairCell(ServerLevel l,BlockPos pos){
  var floor=l.getBlockState(pos);
  if(!floor.is(Blocks.DIRT_PATH)&&!floor.is(Blocks.DIRT)&&!floor.is(Blocks.GRASS_BLOCK)&&!floor.is(Blocks.PODZOL)&&!floor.is(Blocks.COARSE_DIRT))return false;
  for(int y=1;y<=2;y++){var s=l.getBlockState(pos.above(y));if(!s.isAir()&&!s.is(BlockTags.LEAVES)&&!s.is(BlockTags.LOGS)&&!s.canBeReplaced())return false;}
  for(int y=1;y<=2;y++)l.setBlock(pos.above(y),Blocks.AIR.defaultBlockState(),3);
  l.setBlock(pos,Blocks.DIRT_PATH.defaultBlockState(),3);return true;
 }
 public static void tick(net.minecraft.server.MinecraftServer server){
  for(var job:List.copyOf(PENDING.values())){
   if(job.level.getServer()!=server)continue;var chunks=new HashSet<Long>();
   // AD-063: two chunks around every road — a tree decorated in a chunk further out can still reach into the village strip, so the clearing waits for it.
   for(long cell:job.cells){var p=BlockPos.of(cell);int x=p.getX()>>4,z=p.getZ()>>4;for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)chunks.add(net.minecraft.world.level.ChunkPos.asLong(x+dx,z+dz));}
   if(chunks.stream().anyMatch(c->!job.level.getChunkSource().hasChunk(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c))))continue;
   int skipped=0;for(long cell:job.cells)if(!repairCell(job.level,BlockPos.of(cell)))skipped++;
   // AD-063: the trees that grew on the village territory during decoration are taken away whole.
   var village=SettlementData.get(server).entry(job.id);int trees=village==null?0:org.villageastra.world.VillageClearing.clear(job.level,village,job.cells);
   if(Boolean.getBoolean("villageastra.naturalSmoke")){com.mojang.logging.LogUtils.getLogger().info("ASTRA_VILLAGE_CLEARING removed {} tree blocks from the village territory, left {}",trees,village==null?-1:org.villageastra.world.VillageClearing.remaining(job.level,village,job.cells));
    if(village!=null)for(var b:village.settlement().buildings()){var d=org.villageastra.world.BuildingBlueprints.design(b.type());var door=org.villageastra.world.BuildingPlacement.at(village,b,org.villageastra.world.BuildingBlueprints.doorX(b.type()),1,0);
     com.mojang.logging.LogUtils.getLogger().info("ASTRA_VILLAGE door {} at {} holds {}",b.type(),door.toShortString(),job.level.getBlockState(door).getBlock());}}
   // Persist the modified chunks before the completion marker so a restart can safely repeat the pass.
   job.level.getChunkSource().save(true);
   var tag=NbtRecord.read(path(job.level,job.id));tag.putBoolean("complete",true);tag.putInt("skipped",skipped);NbtRecord.write(path(job.level,job.id),tag);PENDING.remove(job.id);
   if(Boolean.getBoolean("villageastra.naturalSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_INITIAL_ROADS VERIFIED {} cells finalized after decoration; skipped={}",job.cells.length,skipped);
  }
 }
}
