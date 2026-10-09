package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.server.SettlementData;
/** Idle walks stay near home; a returning resident still walks every block. */
public final class HomeNeighborhood extends Goal {
 public static final int RADIUS=24,RETURN_DISTANCE=96,RECOVERY_REACH=320;
 private final ResidentEntity npc;private BlockPos target;private int repath;
 public HomeNeighborhood(ResidentEntity npc){this.npc=npc;setFlags(EnumSet.of(Flag.MOVE));}
 public static BlockPos anchor(ResidentEntity npc){
  if(!(npc.level() instanceof ServerLevel l)||npc.settlementId()==null||npc.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());var r=e==null?null:e.settlement().resident(npc.getUUID());
  if(r==null||!r.alive()||r.home()==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var home=e.settlement().buildings().stream().filter(b->b.id().equals(r.home())).findFirst().orElse(null);
  if(home==null)return null;var size=BuildingPlacement.size(home.type(),home.rotation());
  return BuildingPlacement.origin(e,home).offset(size[0]/2,1,size[1]/2);
 }
 public static void restrict(ResidentEntity npc){var home=anchor(npc);if(home==null)npc.clearRestriction();else npc.restrictTo(home,RADIUS);}
 /** Unassigned adults still have real jobs: gathering, baking or helping the hall.
  * A home boundary must not interrupt those trips or a previously claimed furnace. */
 private static boolean onDuty(ResidentEntity npc){
  if(!(npc.level() instanceof ServerLevel l)||npc.settlementId()==null)return false;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());var r=e==null?null:e.settlement().resident(npc.getUUID());
  if(r==null)return false;
  if(SchoolGoal.childStation(l,e,r)!=null)return true;
  if(r.life()!=org.villageastra.domain.Resident.Life.ADULT||!Population.mayWork(r))return false;
  if(NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))||npc.getUUID().equals(HandBread.claimedBaker(l,e.settlement().id())))return true;
  var hall=Workshops.hall(e);if(hall==null||!Workshops.eligible(r,hall))return false;
  var job=Workshops.inspect(l,hall.id());
  return !job.isEmpty()&&!job.getString("stage").equals("idle")
   &&(!job.getBoolean("physicalSmelt")||job.hasUUID("worker")&&job.getUUID("worker").equals(npc.getUUID()));
 }
 /** Children/unassigned adults near a currently ticking village can keep this bounded lease.
  * It also keeps an unassigned hall commuter ticking; the goal itself yields to work.
  * Working expeditions have their own leases; distant villages remain asleep. */
 public static boolean recovery(ResidentEntity npc){
  var home=anchor(npc);if(home==null||!(npc.level() instanceof ServerLevel l))return false;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());var r=e.settlement().resident(npc.getUUID());
  double distance=npc.blockPosition().distSqr(home);
  return r.profession()==null&&distance>RADIUS*RADIUS&&distance<=RECOVERY_REACH*RECOVERY_REACH&&TouchLoad.ticking(l,e.center());
 }
 @Override public boolean canUse(){
  if(npc.isSleeping()||npc.escortPlayer()!=null||npc.tickCount%20>1)return false;
  target=anchor(npc);return target!=null&&npc.blockPosition().distSqr(target)>RETURN_DISTANCE*RETURN_DISTANCE&&recovery(npc)&&!onDuty(npc);
 }
 @Override public boolean canContinueToUse(){return target!=null&&npc.escortPlayer()==null&&!npc.isSleeping()&&!onDuty(npc)&&npc.blockPosition().distSqr(target)>16*16;}
 @Override public void start(){repath=0;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  if(--repath>0)return;repath=40;
  if(!ResourceExpedition.survey(npc,target)){repath=10;return;}
  npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);
 }
 @Override public void stop(){npc.getNavigation().stop();target=null;}
}
