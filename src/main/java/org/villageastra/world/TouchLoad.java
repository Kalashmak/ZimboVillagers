package org.villageastra.world;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.villageastra.server.SettlementData;
/** AD-111: a pantry chunk loaded for a moment at border level (region ticket distance 0 = level 33: block entities readable, nothing ticks), under a
 *  per-tick budget from balance/population.json. Callers touch before they read, so the existing chest code runs unchanged in the same tick.
 *  A touched chunk counts as loaded for hasChunkAt, so gates that need a living, ticking village use {@link #ticking} instead. */
public final class TouchLoad {
 public enum Touch{OK,DEFERRED,FAILED}
 public record Stats(long loads,long deferred,long maxNanos,long totalNanos){}
 public static final int BUDGET,TICKET_TICKS;
 /** How long a relevance answer and a failure warning hold, in active (resp. server) ticks. */
 public static final long RELEVANCE_TICKS=1200,WARN_TICKS=1200;
 static{
  JsonObject o;try(var s=TouchLoad.class.getResourceAsStream("/data/villageastra/balance/population.json")){if(s==null)throw new IllegalStateException("Missing population balance");o=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
  BUDGET=positive(o,"touch_loads_per_tick");TICKET_TICKS=positive(o,"touch_ticket_ticks");
 }
 private static int positive(JsonObject o,String key){int v=o.get(key).getAsInt();if(v<1)throw new IllegalStateException("Invalid "+key);return v;}
 private static final TicketType<ChunkPos> TICKET=TicketType.create("villageastra_touch",Comparator.comparingLong(ChunkPos::toLong),TICKET_TICKS);
 private static int used=0;private static long usedTick=Long.MIN_VALUE;
 private static long loads,deferred,maxNanos,totalNanos;
 private static final Map<UUID,long[]> RELEVANT=new HashMap<>();
 private static final Map<Long,Long> WARNED=new HashMap<>();
 private TouchLoad(){}
 private static void roll(MinecraftServer s){long t=s.getTickCount();if(t!=usedTick){usedTick=t;used=0;}}
 /** Loads every chunk of these positions that is not loaded yet, or refuses (DEFERRED) when this tick's budget is spent; a caller never reads a refusal as 'empty'. */
 public static Touch ensureAll(ServerLevel l,Collection<BlockPos> ps){
  var missing=new LinkedHashSet<ChunkPos>();for(var p:ps)if(!l.hasChunkAt(p))missing.add(new ChunkPos(p));
  if(missing.isEmpty())return Touch.OK;
  roll(l.getServer());int cost=missing.size();
  if(used+cost>BUDGET&&used!=0){deferred++;return Touch.DEFERRED;}
  used+=cost;
  for(var cp:missing){
   try{load(l,cp);}
   catch(RuntimeException error){warn(l,cp,error);return Touch.FAILED;}
  }
  for(var p:ps)upgrade(l,p);
  return Touch.OK;
 }
 public static Touch ensure(ServerLevel l,BlockPos p){return ensureAll(l,List.of(p));}
 /** An unconditional touch (caravan secure, unload and parking): counted in the budget and the stats, never refused. */
 public static void force(ServerLevel l,BlockPos p){
  if(l.hasChunkAt(p))return;roll(l.getServer());used++;load(l,new ChunkPos(p));upgrade(l,p);
 }
 private static void load(ServerLevel l,ChunkPos cp){
  long t0=System.nanoTime();l.getChunkSource().addRegionTicket(TICKET,cp,0,cp);l.getChunk(cp.x,cp.z);
  long spent=System.nanoTime()-t0;loads++;totalNanos+=spent;maxNanos=Math.max(maxNanos,spent);
 }
 /** AD-111 (M2): an old save's owned chest is upgraded in the touch tick; the ChunkEvent.Load path only does it on a later task. */
 private static void upgrade(ServerLevel l,BlockPos p){try{if(l.hasChunkAt(p))org.villageastra.server.OwnershipEvents.upgradeLegacyChest(l,p);}catch(RuntimeException error){warn(l,new ChunkPos(p),error);}}
 private static void warn(ServerLevel l,ChunkPos cp,RuntimeException error){
  long now=l.getServer().getTickCount();var last=WARNED.get(cp.toLong());
  if(last==null||now-last>=WARN_TICKS){WARNED.put(cp.toLong(),now);LogUtils.getLogger().warn("ZimboVillagers could not touch-load chunk {} in {}: {}",cp,l.dimension().location(),error.toString());}
 }
 /** True where entities and blocks tick: what a gate needs when it means 'a player is near', since a touched chunk is loaded but asleep. */
 public static boolean ticking(ServerLevel l,BlockPos p){return l.isPositionEntityTicking(p);}
 /** Owner decision 3: a village matters to the player when a player is its mayor, a player has positive reputation there, or it was annexed by a village a player runs. */
 public static boolean relevant(MinecraftServer s,SettlementData.Entry e){
  var id=e.settlement().id();long now=SettlementData.get(s).clock().ticks();var c=RELEVANT.get(id);
  if(c!=null&&c[0]<=now&&now-c[0]<RELEVANCE_TICKS)return c[1]!=0;
  boolean r=e.settlement().governance().playerMayor()!=null||org.villageastra.server.PropertyLedger.get(s).anyPositive(id);
  if(!r){var owner=Annexation.owner(s,id);var o=owner==null?null:SettlementData.get(s).entry(owner);r=o!=null&&o.settlement().governance().playerMayor()!=null;}
  RELEVANT.put(id,new long[]{now,r?1:0});return r;
 }
 public static Stats stats(){return new Stats(loads,deferred,maxNanos,totalNanos);}
 public static void resetStats(){loads=0;deferred=0;maxNanos=0;totalNanos=0;}
 /** Tests only: this tick's budget is spent. */
 public static void exhaust(MinecraftServer s){roll(s);used=BUDGET;}
 /** Tests only: this tick's budget is whole again. */
 public static void resetTick(){used=0;}
 public static void forget(UUID village){RELEVANT.remove(village);}
 public static void clear(){used=0;usedTick=Long.MIN_VALUE;RELEVANT.clear();WARNED.clear();resetStats();}
}
