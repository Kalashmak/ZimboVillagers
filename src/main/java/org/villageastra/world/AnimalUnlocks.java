package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
/** AD-140 (owner 2026-09-23: "every new animal in the livestock farms must appear through a unique and interesting quest"): the kinds of
 *  animal a village has really got hold of. A kind opens when its quest brings a founding pair into a pen of that kind, and it stays open.
 *  Villages that already kept animals when this rule came keep them: on the first run of a world every village with a yard is marked, and
 *  the kinds its yard holds the first time that ground is in the world are recorded as its own ("legacy"). Until then nothing is locked,
 *  so no village is shut out by a blind read. The record is per village, like the crops' (CropUnlocks, AD-093). */
public final class AnimalUnlocks {
 private AnimalUnlocks(){}
 public static final List<String> KINDS=List.of("sheep","cow","chicken","pig","wolf");
 private static Path dir(MinecraftServer server){return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-animal-unlocks");}
 private static Path path(MinecraftServer server,UUID village){return dir(server).resolve(village+".bin");}
 /** The record is read again only when its file changed: the keeper asks for every pen on every pass. */
 private record Read(long modified,CompoundTag tag){}
 private static final Map<Path,Read> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 private static CompoundTag read(MinecraftServer server,UUID village){var p=path(server,village);
  try{if(!Files.exists(p)){CACHE.remove(p);return new CompoundTag();}long m=Files.getLastModifiedTime(p).toMillis()^Files.size(p)<<40;var c=CACHE.get(p);
   if(c==null||c.modified()!=m){c=new Read(m,NbtRecord.read(p));CACHE.put(p,c);}return c.tag().copy();}
  catch(java.io.IOException ex){return NbtRecord.read(p);}}
 private static void write(MinecraftServer server,UUID village,CompoundTag t){t.putInt("schema",1);t.putUUID("village",village);var p=path(server,village);NbtRecord.write(p,t);CACHE.remove(p);}
 /** Whether the village may keep this kind: the keeper breeds and catches it, and no quest asks for it any more. */
 public static boolean unlocked(ServerLevel l,SettlementData.Entry e,String kind){
  // A kind no quest brings yet (pigs and wolves until theirs come) is not held back.
  if(AnimalQuests.template(kind)==null)return true;
  var t=read(l.getServer(),e.settlement().id());
  return t.getBoolean("legacy_pending")||t.getCompound("unlocked").contains(kind);
 }
 /** Opens a kind for the village; true only the first time. The source says how: "quest", "legacy" or "keeper" (an NPC village's own). */
 public static boolean unlock(ServerLevel l,SettlementData.Entry e,String kind,String source,UUID quest){
  if(!KINDS.contains(kind))throw new IllegalArgumentException("Not an animal of the yard: "+kind);
  var t=read(l.getServer(),e.settlement().id());var open=t.getCompound("unlocked");if(open.contains(kind))return false;
  var entry=new CompoundTag();entry.putString("source",source);entry.putLong("tick",SettlementData.get(l.getServer()).clock().ticks());if(quest!=null)entry.putUUID("quest",quest);
  open.put(kind,entry);t.put("unlocked",open);write(l.getServer(),e.settlement().id(),t);return true;
 }
 /** How a kind was opened ("" when it is not). */
 public static String source(ServerLevel l,SettlementData.Entry e,String kind){return read(l.getServer(),e.settlement().id()).getCompound("unlocked").getCompound(kind).getString("source");}
 /** Every kind of this list the village cannot keep yet, in its order. */
 public static List<String> locked(ServerLevel l,SettlementData.Entry e,List<String> kinds){var out=new ArrayList<String>();for(var k:kinds)if(!unlocked(l,e,k))out.add(k);return out;}
 /** A kind whose card failed or was called off is not asked for again before this tick; the next kind may be asked for meanwhile. */
 static void retry(ServerLevel l,SettlementData.Entry e,String kind,long until){var t=read(l.getServer(),e.settlement().id());var r=t.getCompound("retry");r.putLong(kind,until);t.put("retry",r);write(l.getServer(),e.settlement().id(),t);}
 static long retryAt(ServerLevel l,SettlementData.Entry e,String kind){return read(l.getServer(),e.settlement().id()).getCompound("retry").getLong(kind);}
 /** Counts one more card of this kind that ran out with nobody taking it; returns the count. */
 static int expired(ServerLevel l,SettlementData.Entry e,String kind){var t=read(l.getServer(),e.settlement().id());var x=t.getCompound("expired");int n=x.getInt(kind)+1;x.putInt(kind,n);t.put("expired",x);write(l.getServer(),e.settlement().id(),t);return n;}
 // ---------------------------------------------------------------- villages that kept animals before this rule
 /** Once per world: every village that already has a livestock yard is marked, so the kinds its yard holds are recorded as its own. */
 public static void migrateWorld(MinecraftServer server){
  var marker=dir(server).resolve("world.bin");if(Files.exists(marker))return;
  for(var e:SettlementData.get(server).entries())if(AnimalYard.yard(e)!=null){var t=read(server,e.settlement().id());if(t.getBoolean("migrated"))continue;t.putBoolean("legacy_pending",true);t.putBoolean("migrated",true);write(server,e.settlement().id(),t);}
  var m=new CompoundTag();m.putInt("schema",1);m.putBoolean("migrated",true);NbtRecord.write(marker,m);
 }
 static boolean legacyPending(ServerLevel l,SettlementData.Entry e){return read(l.getServer(),e.settlement().id()).getBoolean("legacy_pending");}
 /** The kinds the yard really held the first time its ground was in the world after the marking. */
 static void settleLegacy(ServerLevel l,SettlementData.Entry e,Collection<String> kinds){
  var t=read(l.getServer(),e.settlement().id());if(!t.getBoolean("legacy_pending"))return;t.remove("legacy_pending");write(l.getServer(),e.settlement().id(),t);
  for(var k:kinds)unlock(l,e,k,"legacy",null);
 }
 /** Test seam: a village marked as one of the villages that were here before the rule. */
 public static void markLegacy(ServerLevel l,SettlementData.Entry e){var t=read(l.getServer(),e.settlement().id());t.putBoolean("legacy_pending",true);t.putBoolean("migrated",true);write(l.getServer(),e.settlement().id(),t);}
}
