package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-153 (Construction V): the builder's wolf. A road or wall builder out of reach of the hall who needs its next load sends a free kennel wolf
 *  of the village (VillageDogs send/near, the same errand the warehouse's wolves run) to the hall chest; there the load is taken from the chest
 *  through the journal (Roads.load, the builder's bag included), and the wolf carries it back to where the builder waits. A wolf that does not
 *  get there in a minute is let go and the builder walks himself for a while. In memory: a restart drops the errand, the load already taken
 *  is in the project's cargo either way. */
public final class BuilderWolf {
 private BuilderWolf(){}
 /** Nearer than this to the hall chest the builder walks himself; the errand runs at most this many ticks; a failed one rests the wolf's help. */
 public static final int NEAR_HALL=12,TIMEOUT=1200,REST=1200;
 private static final class Errand{final UUID dog,village;final long started;boolean loaded;Errand(UUID dog,UUID village,long started){this.dog=dog;this.village=village;this.started=started;}}
 private static final Map<UUID,Errand> ERRANDS=new java.util.concurrent.ConcurrentHashMap<>();
 private static final Map<UUID,Long> RESTING=new java.util.concurrent.ConcurrentHashMap<>();
 /** The wolf running this builder's errand now, or null. */
 public static UUID dog(UUID builder){var x=ERRANDS.get(builder);return x==null?null:x.dog;}
 /** One tick of the errand: the builder's status while the wolf fetches or brings ("road_wolf_fetching", "road_wolf_bringing"), or empty
  *  when the builder goes on himself (no research, no free wolf, near the hall, nothing needed, or the wolf has just brought the load). */
 public static String step(ServerLevel l,SettlementData.Entry e,Settlement.Building hall,CompoundTag project,UUID builder,BlockPos at,boolean needs){
  long now=l.getGameTime();var x=ERRANDS.get(builder);var chest=LogisticsRoutes.position(e,hall);
  if(x==null){
   if(!needs||now<RESTING.getOrDefault(builder,0L)||!ResearchKnobs.builderWolf(l,e)||at.distSqr(chest)<=NEAR_HALL*NEAR_HALL)return "";
   var free=VillageDogs.available(l,e);if(free.isEmpty())return "";
   var dog=free.get(0);if(!VillageDogs.send(l,e,dog,chest))return "";
   ERRANDS.put(builder,new Errand(dog,e.settlement().id(),now));return "road_wolf_fetching";
  }
  if(now-x.started>TIMEOUT||!x.loaded&&!needs){drop(l,e,builder);if(now-x.started>TIMEOUT)RESTING.put(builder,now+REST);return "";}
  if(!x.loaded){
   if(!VillageDogs.near(l,e,x.dog,chest,3))return "road_wolf_fetching";
   if(Roads.load(l,e,hall,project)==0){drop(l,e,builder);RESTING.put(builder,now+REST);return "";}
   x.loaded=true;VillageDogs.send(l,e,x.dog,at);return "road_wolf_bringing";
  }
  if(VillageDogs.near(l,e,x.dog,at,3)){drop(l,e,builder);return "";}
  if(now%20==0)VillageDogs.send(l,e,x.dog,at);
  return "road_wolf_bringing";
 }
 private static void drop(ServerLevel l,SettlementData.Entry e,UUID builder){var x=ERRANDS.remove(builder);if(x!=null)VillageDogs.release(l,e,x.dog);}
 /** Lets this builder's wolf go (the builder stopped his road work). */
 public static void drop(ServerLevel l,UUID builder){var x=ERRANDS.get(builder);if(x==null)return;var e=SettlementData.get(l.getServer()).entry(x.village);
  ERRANDS.remove(builder);if(e!=null)VillageDogs.release(l,e,x.dog);}
 /** Tests: forgets every errand and rest. */
 public static void clear(){ERRANDS.clear();RESTING.clear();}
}
