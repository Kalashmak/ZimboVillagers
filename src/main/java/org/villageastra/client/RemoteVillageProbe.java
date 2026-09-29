package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Resident;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-111, "remote pantries": the starter village is left alone for a whole day with the player 2000 blocks away. Its residents keep eating the
 *  hall's real bread through touch-loaded border chunks — no missed meal, nobody dies, exactly two meals each, the bread down by exactly those
 *  meals — while nothing in the village ticks (its crops stay as they were). The spawn point moves with the player first (R2), so the spawn
 *  chunks no longer hold the village loaded. Every 1200 active ticks an ASTRA_REMOTE sample line carries the MSPT and the touch statistics. */
final class RemoteVillageProbe {
 /** Active ticks the village lives unloaded; active ticks to wait for the hall chunk to unload; client ticks before the probe gives up. */
 private static final long RUN=24000,UNLOAD_WAIT=2400,TIMEOUT=40000,SAMPLE=1200;
 /** Bread the hall starts with; how far east the player and the spawn point go; the fixture's reputation gift that makes the village relevant. */
 private static final int BREAD=32,AWAY=2000,GIFT=10;private static final double MSPT=50;
 private static int ticks,phase;private static volatile int stage;private static volatile boolean verified;private static volatile String failure,verdict="",progress="";
 private static volatile long start=-1,teleported=-1,unloaded=-1,end=-1,logged=-1;
 private static long bread0,rations0;private static int msptCount,unloadedSamples;private static double msptTotal;
 private static final Map<UUID,Long> MEAL0=new HashMap<>();private static final Map<BlockPos,BlockState> CROPS0=new HashMap<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.remoteVillageSmoke");}
 private static SettlementData.Entry entry(MinecraftServer server){return SettlementData.get(server).entries().iterator().next();}
 private static BlockPos hallChest(SettlementData.Entry e){return LogisticsRoutes.position(e,Workshops.hall(e));}
 /** Server thread, from the harness: the hall's food is 32 bread and nothing else, every pantry holds no other food, and the player's gift
  *  makes the village relevant (owner decision 3), so its pantry is touched rather than frozen. */
 static void setup(MinecraftServer server){
  var l=server.overworld();var data=SettlementData.get(server);var e=entry(server);
  if(Workshops.hall(e)==null)throw new IllegalStateException("The village has no hall");
  for(var pos:Population.pantryPositions(e))if(l.getBlockEntity(pos) instanceof Container c){for(int slot=0;slot<c.getContainerSize();slot++)if(Population.nutrition(c.getItem(slot))>0)c.setItem(slot,ItemStack.EMPTY);c.setChanged();}
  if(!(l.getBlockEntity(hallChest(e)) instanceof Container hall))throw new IllegalStateException("The hall chest is missing");
  int free=-1;for(int slot=hall.getContainerSize()-1;slot>=0;slot--)if(hall.getItem(slot).isEmpty())free=slot;
  if(free<0)throw new IllegalStateException("No room for the fixture's bread in the hall");
  hall.setItem(free,new ItemStack(Items.BREAD,BREAD));hall.setChanged();
  var player=server.getPlayerList().getPlayers().get(0);PropertyLedger.get(server).gift(e.settlement().id(),player.getUUID(),GIFT);TouchLoad.forget(e.settlement().id());
  if(!TouchLoad.relevant(server,e))throw new IllegalStateException("The gift did not make the village relevant");
  l.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(3,server);start=data.clock().ticks();
  LogUtils.getLogger().info("ASTRA_REMOTE fixture: {} bread in the hall, {} pantries, reputation {} for the player, relevant=true, budget {} loads a tick, ticket {} ticks",
   BREAD,Population.pantryPositions(e).size(),GIFT,TouchLoad.BUDGET,TouchLoad.TICKET_TICKS);
 }
 /** Bread and rations in every pantry; a pantry whose chunk is not loaded is loaded plainly first (only for the final count). */
 private static long[] food(ServerLevel l,SettlementData.Entry e){long bread=0,rations=0;
  for(var pos:Population.pantryPositions(e)){if(!l.hasChunkAt(pos))l.getChunk(pos.getX()>>4,pos.getZ()>>4);
   if(l.getBlockEntity(pos) instanceof Container c)for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.is(Items.BREAD))bread+=s.getCount();rations+=(long)Population.nutrition(s)*s.getCount();}}
  return new long[]{bread,rations};}
 /** The states of the farm's growing crops in chunks that do not tick; a chunk that is not loaded is loaded at border level to be read. */
 private static Map<BlockPos,BlockState> crops(ServerLevel l,SettlementData.Entry e){var out=new HashMap<BlockPos,BlockState>();
  for(var cell:FarmField.cells(e)){if(TouchLoad.ticking(l,cell))continue;if(!l.hasChunkAt(cell))l.getChunk(cell.getX()>>4,cell.getZ()>>4);
   for(var at:List.of(cell,cell.above())){var s=l.getBlockState(at);if(s.getBlock() instanceof CropBlock crop&&!crop.isMaxAge(s)){out.put(at,s);break;}}}
  return out;}
 private static String missed(Collection<Resident> rs){var b=new StringBuilder();for(var r:rs)if(MEAL0.containsKey(r.id()))b.append(r.alive()?r.missedMeals():"dead").append(',');return b.length()==0?"":b.substring(0,b.length()-1);}
 /** One look on the server thread; the stage moves on from here only. */
 private static void look(MinecraftServer server){server.execute(()->{try{
  if(verified||failure!=null)return;
  var l=server.overworld();var data=SettlementData.get(server);var e=entry(server);var s=e.settlement();long now=data.clock().ticks();var hallPos=hallChest(e);
  if(stage==0){
   // Everybody has eaten once, so each has a due tick of its own; then the baseline, the spawn point and the player go east.
   if(now-start<40||s.residents().stream().anyMatch(r->r.alive()&&r.lastMeal()<0))return;
   for(var r:s.residents())if(r.alive()){if(r.missedMeals()>0){failure="A resident had missed meals before the player left: "+r.id();return;}MEAL0.put(r.id(),r.lastMeal());}
   var f=food(l,e);bread0=f[0];rations0=f[1];TouchLoad.resetStats();
   var dest=e.center().offset(AWAY,10,0);l.setDefaultSpawnPos(dest,0);
   var player=server.getPlayerList().getPlayers().get(0);player.teleportTo(l,dest.getX()+.5,dest.getY(),dest.getZ()+.5,0,20);
   teleported=now;stage=1;
   LogUtils.getLogger().info("ASTRA_REMOTE away: {} residents fed at {}, bread {} rations {}, player and spawn point moved to {}",MEAL0.size(),MEAL0.values(),bread0,rations0,dest.toShortString());
  }else if(stage==1){
   if(l.hasChunkAt(hallPos)){if(now-teleported>UNLOAD_WAIT)failure="The hall chunk was still loaded "+(now-teleported)+" active ticks after the player left";return;}
   CROPS0.putAll(crops(l,e));unloaded=now;logged=now;
   end=Math.max(now+RUN,MEAL0.values().stream().mapToLong(Long::longValue).max().orElse(now)+2*Population.MEAL_INTERVAL+200);stage=2;
   LogUtils.getLogger().info("ASTRA_REMOTE unloaded: the hall chunk unloaded {} active ticks after the player left; {} growing crops recorded; running until {}",now-teleported,CROPS0.size(),end);
  }else if(stage==2){
   var alive=s.residents().stream().filter(r->MEAL0.containsKey(r.id())).toList();
   boolean hallLoaded=l.hasChunkAt(hallPos),hallTicking=TouchLoad.ticking(l,hallPos);
   if(!hallLoaded)unloadedSamples++;
   double mspt=server.getAverageTickTime();msptTotal+=mspt;msptCount++;var st=TouchLoad.stats();
   progress=String.format(Locale.ROOT,"active=%d/%d mspt=%.2f loads=%d deferred=%d maxTouchMs=%.2f hallLoaded=%b hallTicking=%b missed=[%s]",
    now-unloaded,end-unloaded,mspt,st.loads(),st.deferred(),st.maxNanos()/1e6,hallLoaded,hallTicking,missed(alive));
   // On the active-tick schedule, not from the last look: a warped probe (ProbeWarp) looks every few hundred ticks and still samples every 1200.
   if(now-logged>=SAMPLE){logged+=SAMPLE*((now-logged)/SAMPLE);LogUtils.getLogger().info("ASTRA_REMOTE sample {}",progress);}
   if(alive.stream().anyMatch(r->!r.alive())){failure="A resident died while the player was away: "+progress;return;}
   if(alive.stream().anyMatch(r->r.missedMeals()>0)){failure="A meal was missed while the player was away: "+progress;return;}
   if(hallTicking){failure="The hall chunk ticks with the player 2000 blocks away: "+progress;return;}
   if(now<end)return;
   int dues=0;for(var r:alive){long d=r.lastMeal()-MEAL0.get(r.id());if(d!=2*Population.MEAL_INTERVAL){failure="Resident "+r.id()+" ate "+d+" ticks of meals, not two: "+progress;return;}dues+=2;}
   var f=food(l,e);var c1=crops(l,e);int changed=0;for(var x:CROPS0.entrySet()){var age=c1.get(x.getKey());if(age!=null&&!age.equals(x.getValue()))changed++;}
   double avg=msptTotal/Math.max(1,msptCount);
   verdict=String.format(Locale.ROOT,"residents=%d dues=%d bread=%d->%d rations=%d->%d loads=%d deferred=%d maxTouchMs=%.2f avgTouchMs=%.2f avgMspt=%.2f samples=%d unloadedSamples=%d crops=%d grown=%d reload=false",
    alive.size(),dues,bread0,f[0],rations0,f[1],st.loads(),st.deferred(),st.maxNanos()/1e6,st.loads()==0?0:st.totalNanos()/1e6/st.loads(),avg,msptCount,unloadedSamples,CROPS0.size(),changed);
   if(rations0-f[1]!=(long)dues*Population.MEAL_NUTRITION)failure="The pantries lost "+(rations0-f[1])+" rations for "+dues+" meals: "+verdict;
   else if(bread0-f[0]!=dues)failure="The bread fell by "+(bread0-f[0])+" for "+dues+" meals: "+verdict;
   else if(changed>0)failure=changed+" crops grew in a chunk that does not tick: "+verdict;
   else if(unloadedSamples==0)failure="No sample saw the hall chunk unloaded: "+verdict;
   else if(st.loads()<1)failure="No pantry chunk was ever touch-loaded: "+verdict;
   else if(avg>=MSPT)failure="The server averaged "+avg+" ms a tick: "+verdict;
   else verified=true;
  }
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){if(phase>=2)return;try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>TIMEOUT)throw new IllegalStateException("Remote village timeout at stage "+stage+": "+progress);
  var server=mc.getSingleplayerServer();if(server!=null&&ticks%20==0)look(server);
  if(verified){LogUtils.getLogger().info("ASTRA_REMOTE VERIFIED {}",verdict);mc.setScreen(null);mc.stop();phase=2;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_REMOTE FAILED {}",ex.getMessage(),ex);phase=9;mc.stop();}}
}
