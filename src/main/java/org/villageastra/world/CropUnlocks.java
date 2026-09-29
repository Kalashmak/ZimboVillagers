package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.FarmPolicies;
import org.villageastra.server.SettlementData;
/** AD-093: a village grows only what it has really got hold of. Wheat and the oak are known from the start; every other crop of the farm
 *  and every other sapling of the nursery is opened by a quest that brings that seed or sapling to the hall (the quests themselves are
 *  the adventure side's, AD-088+). The record is per village and only ever grows: nothing once opened is taken away, and a crop a farm
 *  already grew before this rule came counts as opened. Ids are the item ids of what is planted. */
public final class CropUnlocks {
 private CropUnlocks(){}
 public static final List<String> CROPS=List.of("minecraft:wheat_seeds","minecraft:carrot","minecraft:potato","minecraft:beetroot_seeds","minecraft:sugar_cane");
 public static final List<String> SAPLINGS=List.of("minecraft:oak_sapling","minecraft:birch_sapling","minecraft:spruce_sapling","minecraft:jungle_sapling",
   "minecraft:acacia_sapling","minecraft:cherry_sapling","minecraft:mangrove_propagule");
 /** Known from the first day. */
 public static final Set<String> FREE=Set.of("minecraft:wheat_seeds","minecraft:oak_sapling");
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-unlocks/"+village+".bin");}
 /** AD-131: the record is read again only when its file changed (the forester's sapling demand asks every tick). */
 private record Read(long modified,CompoundTag tag){}
 private static final Map<Path,Read> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 private static CompoundTag read(ServerLevel l,UUID village){var p=path(l,village);
  try{if(!Files.exists(p)){CACHE.remove(p);return new CompoundTag();}long m=Files.getLastModifiedTime(p).toMillis()^Files.size(p)<<40;var c=CACHE.get(p);
   if(c==null||c.modified()!=m){c=new Read(m,NbtRecord.read(p));CACHE.put(p,c);}return c.tag().copy();}
  catch(java.io.IOException ex){return NbtRecord.read(p);}}
 /** The item id a farm crop is planted from. */
 public static String of(FarmCrops crop){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(crop.seed).toString();}
 public static boolean known(String id){return CROPS.contains(id)||SAPLINGS.contains(id);}
 /** Whether the village may plant this crop or sapling. */
 public static boolean unlocked(ServerLevel l,SettlementData.Entry e,String id){
  if(FREE.contains(id))return true;
  if(read(l,e.settlement().id()).getCompound("unlocked").contains(id))return true;
  // A crop some farm of the village was already growing before crops had to be opened stays its own — recorded, so switching the farm away keeps it.
  var policies=FarmPolicies.get(l.getServer());
  for(var b:e.settlement().buildings())if(b.type().equals("farm")&&of(policies.crop(e.settlement().id(),b.id())).equals(id)){record(l,e,id,"legacy");return true;}
  return false;
 }
 /** Opens a crop or sapling for the village; returns true only the first time. The source says how (for now always "quest"). */
 public static boolean unlock(ServerLevel l,SettlementData.Entry e,String id,String source){
  if(!known(id))throw new IllegalArgumentException("Not a crop or sapling of the village: "+id);
  if(unlocked(l,e,id))return false;
  record(l,e,id,source);return true;
 }
 private static void record(ServerLevel l,SettlementData.Entry e,String id,String source){
  var t=read(l,e.settlement().id());var open=t.getCompound("unlocked");
  var entry=new CompoundTag();entry.putString("source",source);entry.putLong("tick",SettlementData.get(l.getServer()).clock().ticks());
  open.put(id,entry);t.put("unlocked",open);t.putInt("schema",1);t.putUUID("village",e.settlement().id());
  NbtRecord.write(path(l,e.settlement().id()),t);CACHE.remove(path(l,e.settlement().id()));
 }
 /** Everything the village cannot plant yet, crops first, in a fixed order — what the quest board may still ask for. */
 public static List<String> locked(ServerLevel l,SettlementData.Entry e){
  var out=new ArrayList<String>();
  for(var id:CROPS)if(!unlocked(l,e,id))out.add(id);
  for(var id:SAPLINGS)if(!unlocked(l,e,id))out.add(id);
  return out;
 }
}
