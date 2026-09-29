package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.villageastra.world.EscortGoal;
import org.villageastra.world.ResidentEntity;
/** AD-106: the probes' player leads a companion home the way a player would — along a route the companion itself can walk, a few steps
 *  ahead of it, waiting until it catches up — instead of hopping in a straight line onto whatever the height map says is ground there.
 *  The companion moves only on its own legs: a step longer than a walk between two samples fails the probe, and a walk that makes no
 *  headway after a few detours fails it with where and why, instead of looping until the timeout. */
final class EscortLeader {
 static final int SAMPLE=20,LEAD=6,CATCH_UP=5,REPLAN=200,STALL=1200,REPLANS=3;
 static final double MAX_STEP=8;
 private Path plan;private int planned=Integer.MIN_VALUE/2,bestAt,replans,stalls,waiting;
 private double best=Double.MAX_VALUE,maxStep;private Vec3 lastSeen;
 private final Set<String> reasons=new TreeSet<>();private String route="no route yet";
 EscortLeader(int now){bestAt=now;}
 String summary(){return String.format(Locale.ROOT,"walk: maxStep=%.1f replans=%d reasons=%s %s",maxStep,stalls,reasons,route);}
 /** Server thread, every SAMPLE ticks: null to go on, or why the walk home failed. */
 String step(ServerLevel l,ServerPlayer p,ResidentEntity c,BlockPos home,int tick){
  var here=c.position();
  if(lastSeen!=null&&!c.isPassenger()){double moved=here.distanceTo(lastSeen);maxStep=Math.max(maxStep,moved);
   if(moved>MAX_STEP)return String.format(Locale.ROOT,"The companion jumped %.1f blocks between two samples: from %s to %s",moved,lastSeen,here);}
  lastSeen=here;reasons.add(c.escortState()+"/"+c.escortReason());
  double d=Math.sqrt(c.distanceToSqr(home.getX()+.5,c.getY(),home.getZ()+.5));
  if(d<best-2){best=d;bestAt=tick;replans=0;}
  boolean lost="waiting".equals(c.escortState())&&("no_path".equals(c.escortReason())||"too_far".equals(c.escortReason()));
  waiting="waiting".equals(c.escortState())?waiting+1:0;
  if(tick-bestAt>STALL||waiting>=10){stalls++;
   if(++replans>REPLANS)return String.format(Locale.ROOT,"The companion found no way home: at=%s state=%s/%s best=%.1f %s player=%s",
    c.blockPosition().toShortString(),c.escortState(),c.escortReason(),best,route,p.blockPosition().toShortString());
   bestAt=tick;waiting=0;var detour=LandRandomPos.getPosTowards(c,16,7,Vec3.atBottomCenterOf(home));
   var alt=detour==null?null:dry(l,c.routeTo(BlockPos.containing(detour),1));if(alt!=null){plan=alt;planned=tick;}
   LogUtils.getLogger().warn("ASTRA_ESCORT re-plan {}/{} {} detour={}",replans,REPLANS,route,detour);}
  if(plan==null||tick-planned>=REPLAN){var fresh=dry(l,c.routeTo(home,3));if(fresh!=null){plan=fresh;planned=tick;}}
  if(plan==null){route="no dry route from "+c.blockPosition().toShortString();return null;}
  int at=0;float near=Float.MAX_VALUE;
  for(int i=0;i<plan.getNodeCount();i++){float s=plan.getNode(i).distanceToSqr(c.blockPosition());if(s<near){near=s;at=i;}}
  // The next dry node at least LEAD blocks from the companion: nearer than that it counts as already beside its player and would not move.
  int w=plan.getNodeCount()-1;
  for(int i=at+1;i<plan.getNodeCount();i++){var node=plan.getNode(i);if(!wet(l,node)&&Math.sqrt(c.distanceToSqr(node.x+.5,node.y,node.z+.5))>=LEAD){w=i;break;}}
  var n=plan.getNode(w);
  route="plan="+plan.getNodeCount()+(plan.canReach()?" reach":" partial")+" end="+plan.getEndNode().asBlockPos().toShortString()+" waypoint="+n.asBlockPos().toShortString();
  // A step ahead only once it has caught up, or back onto its own route when it says there is no way to the player: never to fetch it.
  if((c.distanceToSqr(p)<CATCH_UP*CATCH_UP||lost)&&p.blockPosition().distSqr(n.asBlockPos())>1)
   p.teleportTo(l,n.x+.5,n.y,n.z+.5,(float)(Math.atan2(home.getZ()-n.z,home.getX()-n.x)*180/Math.PI)-90,0);
  return null;
 }
 private static Path dry(ServerLevel l,Path p){
  if(p==null)return null;int run=0;
  for(int i=0;i<p.getNodeCount();i++){run=wet(l,p.getNode(i))?run+1:0;if(run>EscortGoal.SWIM_LIMIT)return null;}
  return p;
 }
 private static boolean wet(ServerLevel l,Node n){return l.getFluidState(n.asBlockPos()).is(FluidTags.WATER);}
}
