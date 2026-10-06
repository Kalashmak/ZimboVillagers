package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Briefly remember failed sensing routes; never cache a successful path or authoritative work state. */
public final class HarvestRouteCache {
 public static final int RETRY_TICKS=20,CAPACITY=64;
 private record Key(Object feet,int range){}
 private static final class Memo {
  Vec3 origin;Object dimension;double width,height;long time,plans,hits;
  final LinkedHashMap<Key,Long> misses=new LinkedHashMap<>(16,.75F,true);
 }
 private static final Map<ResidentEntity,Memo> MEMOS=new WeakHashMap<>();
 private static final ThreadLocal<ResidentEntity> ACTIVE=new ThreadLocal<>();
 public record Stats(long plans,long hits,int entries){}
 public static final class Survey implements AutoCloseable {
  private final ResidentEntity previous;
  private Survey(ResidentEntity worker){previous=ACTIVE.get();ACTIVE.set(worker);}
  @Override public void close(){if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);}
 }
 private HarvestRouteCache(){}
 public static Survey survey(ResidentEntity worker){return new Survey(worker);}
 /** Only initial sensing may combine platforms; working routes retain their original choice. */
 public static boolean sensing(ResidentEntity worker){return ACTIVE.get()==worker;}
 public static Stats stats(ResidentEntity worker){var m=MEMOS.get(worker);return m==null?new Stats(0,0,0):new Stats(m.plans,m.hits,m.misses.size());}
 public static Path plan(ResidentEntity worker,BlockPos feet,int range){
  if(ACTIVE.get()!=worker||!(worker.level() instanceof ServerLevel l))return nativePlan(worker,feet,range);
  if(!withinSurveyRange(worker,feet,range))return null;
  return remember(worker,l,new Key(feet.immutable(),range),()->nativePlan(worker,feet,range));
 }
 public static Path planAny(ResidentEntity worker,Set<BlockPos> feet,int range){
  if(feet.isEmpty())return null;
  var targets=Set.copyOf(feet);
  if(ACTIVE.get()!=worker||!(worker.level() instanceof ServerLevel l))return worker.routeToAny(targets,range);
  targets=targets.stream().filter(p->withinSurveyRange(worker,p,range)).collect(java.util.stream.Collectors.toUnmodifiableSet());
  if(targets.isEmpty())return null;
  var reachableTargets=targets;
  return remember(worker,l,new Key(reachableTargets,range),()->worker.routeToAny(reachableTargets,range));
 }
 /** A generous horizontal lower bound only; native planning still decides all
  * possible routes. Do not retain a refusal when the body moves nearer, and
  * leave actual work/partial-route planning outside Survey unchanged. */
 private static boolean withinSurveyRange(ResidentEntity worker,BlockPos feet,int range){
  if(range<=0)return true;
  var from=worker.blockPosition();double dx=(double)feet.getX()-from.getX(),dz=(double)feet.getZ()-from.getZ();double bound=(double)range+4;
  return dx*dx+dz*dz<=bound*bound;
 }
 private static Path remember(ResidentEntity worker,ServerLevel l,Key key,java.util.function.Supplier<Path> nativeQuery){
  long now=l.getGameTime();var m=MEMOS.computeIfAbsent(worker,w->new Memo());
  if(m.origin==null||m.dimension!=l.dimension()||now<m.time||worker.position().distanceToSqr(m.origin)>.25||m.width!=worker.getBbWidth()||m.height!=worker.getBbHeight()){
   m.misses.clear();m.origin=worker.position();m.dimension=l.dimension();m.width=worker.getBbWidth();m.height=worker.getBbHeight();
  }
  m.time=now;m.misses.values().removeIf(until->until<=now);
  if(m.misses.containsKey(key)){m.hits++;return null;}
  m.plans++;var path=nativeQuery.get();
  // A stale negative can only defer a sensing candidate for one second.
  // Actual excavation/return calls run outside Survey and always plan afresh.
  if(path==null||!path.canReach()){
   m.misses.put(key,now+RETRY_TICKS);while(m.misses.size()>CAPACITY)m.misses.remove(m.misses.keySet().iterator().next());
  }
  return path;
 }
 private static Path nativePlan(ResidentEntity worker,BlockPos feet,int range){return range>0?worker.routeTo(feet,0,range):worker.routeTo(feet,0);}
}
