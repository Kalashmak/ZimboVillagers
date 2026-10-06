package org.villageastra.world;
import java.util.EnumSet;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-104 P2, owner decision 1: an idle adult bakes the village's bread by hand at the town hall, by day (HandBread). "Idle" is the goal order: this
 *  is the last goal of priority 6, so it starts only when no work goal of this resident holds its legs, and sleep, shelter or the hall project
 *  (lower numbers) take the resident back at once. One baker per village holds HandBread's claim; the job itself is the village's and durable. */
public final class HandBreadGoal extends Goal {
 private final ResidentEntity worker;private final boolean withoutPlayers;private final LongSupplier dayTime;
 public static final int STALLED_TRAVEL=1200,RETRY_TRAVEL=1200;
 private int lastCheck=-100,repath,travel;private double bestDistance=Double.POSITIVE_INFINITY;private boolean done,fetching;private BlockPos target;
 public HandBreadGoal(ResidentEntity worker){this(worker,false,()->worker.level().getDayTime());}
 /** Tests: no players needed, their own day clock, a turn on every tick() and the game time as the job's clock (the active clock stands still without players). */
 public HandBreadGoal(ResidentEntity worker,boolean withoutPlayers,LongSupplier dayTime){this.worker=worker;this.withoutPlayers=withoutPlayers;this.dayTime=dayTime;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 /** Who may bake: an adult of this village, by day — not the farmer (his field is the wheat), not the watch or the army, nobody walking out with a
  *  player, still rowing ashore after being let go (override 18) or with a load to hand back, and no guest without a village. Hunger does not stop
  *  it: this is the village's emergency food (PROF-109). */
 private SettlementData.Entry entry(){return entry(worker,withoutPlayers,dayTime.getAsLong());}
 private static SettlementData.Entry entry(ResidentEntity worker,boolean withoutPlayers,long dayTime){
  if(!(worker.level() instanceof ServerLevel l)||!worker.isAlive()||worker.settlementId()==null||worker.escortPlayer()!=null||worker.releasing())return null;
  if(l.getServer().getPlayerCount()==0&&!withoutPlayers||SleepGoal.night(dayTime))return null;
  var e=SettlementData.get(l.getServer()).entry(worker.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(worker.getUUID());
  if(r==null||!r.alive()||r.life()!=Resident.Life.ADULT||r.profession()==Profession.FARMER||r.profession()!=null&&r.profession().military())return null;
  // A teacher at the school and a recruit at the drill ground work by standing there, so neither is idle then — unless a meal was already missed.
  if((r.profession()==Profession.TEACHER||r.recruit())&&!HandBread.missedMeal(e))return null;
  if(Population.mayWork(r)&&NaturalFurnace.finishing(worker,e))return null;
  return CargoCustody.pending(l.getServer(),worker.getUUID())||CargoCustody.dead(l.getServer(),worker.getUUID())?null:e;
 }
 /** The village's hand bread wants this resident now: it may bake (by day, an adult of the village, not the farmer or the army), it may take the
  *  claim, and a job waits to be finished or may start. The hall's own crafting (the mayor's, the builder's and the unemployed adults' simple
  *  work, AD-003) gives way to it — that work only fills the time while materials are missing, and a paid job left in the hall must not
  *  starve the village (probe-fix-01: the style's bigger projects kept both adults crafting all day, the job stayed in "work" and a meal was missed). */
 public static boolean calls(ResidentEntity worker,boolean withoutPlayers){return calls(worker,withoutPlayers,worker.level().getDayTime());}
 /** Tests: with their own day clock. */
 public static boolean calls(ResidentEntity worker,boolean withoutPlayers,long dayTime){
  var e=entry(worker,withoutPlayers,dayTime);if(e==null)return false;var l=(ServerLevel)worker.level();
  return HandBread.mayClaim(l,e.settlement().id(),worker.getUUID(),l.getGameTime())&&HandBread.actionable(l,e);
 }
 @Override public boolean canUse(){
  if(!withoutPlayers&&worker.tickCount-lastCheck<40)return false;lastCheck=worker.tickCount;
  var e=entry();if(e==null)return false;var l=(ServerLevel)worker.level();
  return HandBread.mayClaim(l,e.settlement().id(),worker.getUUID(),l.getGameTime())&&HandBread.actionable(l,e);
 }
 @Override public boolean canContinueToUse(){if(done)return false;var e=entry();if(e==null)return false;var l=(ServerLevel)worker.level();return HandBread.holds(l,e.settlement().id(),worker.getUUID(),l.getGameTime());}
 @Override public void start(){done=false;target=null;repath=0;travel=0;bestDistance=Double.POSITIVE_INFINITY;var e=entry();if(e!=null){var l=(ServerLevel)worker.level();HandBread.claim(l,e.settlement().id(),worker.getUUID(),l.getGameTime());}}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  if(done)return;var e=entry();if(e==null)return;var l=(ServerLevel)worker.level();var id=e.settlement().id();
  // Override 11: the claim is refreshed on every tick, walking included, so a long walk never lets a second adult take the job over meanwhile.
  if(!HandBread.claim(l,id,worker.getUUID(),l.getGameTime())){done=true;return;}
  if(target==null&&!aim(l,e)){done=true;return;}
  double distance=worker.distanceToSqr(target.getX()+1.5,target.getY(),target.getZ()+.5);
  if(distance>6.25){
   double remaining=Math.sqrt(distance);if(remaining+.5<bestDistance){bestDistance=remaining;travel=0;}
   if(++travel>STALLED_TRAVEL){HandBread.defer(l,id,worker.getUUID(),l.getGameTime(),RETRY_TRAVEL);done=true;worker.getNavigation().stop();worker.workStatus("needs_access");return;}
   if(--repath<=0){repath=20;worker.getNavigation().moveTo(target.getX()+1.5,target.getY(),target.getZ()+.5,.8);}
   worker.workStatus(fetching?"hand_bread_fetching":"walking");return;}
  worker.getNavigation().stop();if(!withoutPlayers&&worker.tickCount%20!=0)return;
  // The job runs on the active clock, like every workshop's; a test world has no players, so its goal counts the game time instead.
  long now=withoutPlayers?l.getGameTime():SettlementData.get(l.getServer()).clock().ticks();
  var hall=Workshops.hall(e);if(hall!=null&&target.equals(LogisticsRoutes.position(e,hall))&&HallPacking.advance(l,e,now)){worker.workStatus("working");return;}
  var status=HandBread.advance(l,e,worker.getUUID(),now);worker.workStatus(status);
  var was=target;if(!aim(l,e)){done=true;return;}if(!target.equals(was)){repath=0;travel=0;bestDistance=Double.POSITIVE_INFINITY;}
  switch(status){
   // Override 11: a full hall chest lets the baker go too, so other work of priority 6 is not held up behind it.
   case "hand_bread_idle","hand_bread_missing_wheat","hand_bread_output_full","workshop_missing_chest"->done=true;
   case "hand_bread_complete"->done=!HandBread.open(l,e);
   default->{}
  }
 }
 /** The next place to stand, and what the baker is seen carrying: wheat until the baking begins, then bread until it is put away. */
 private boolean aim(ServerLevel l,SettlementData.Entry e){
  var t=HandBread.inspect(l,e.settlement().id());target=HandBread.where(e,t);if(target==null)return false;
  var stage=t.getString("stage");fetching=stage.equals("fund")&&t.hasUUID("source");
  boolean wheat=stage.equals("fund")||stage.equals("work")&&t.getLong("labor")==0,bread=stage.equals("work")||stage.equals("output");
  worker.displayWorkItem(wheat?new ItemStack(Items.WHEAT):bread?new ItemStack(Items.BREAD):ItemStack.EMPTY);return true;
 }
 @Override public void stop(){
  worker.getNavigation().stop();worker.displayWorkItem(ItemStack.EMPTY);target=null;
  if(worker.level() instanceof ServerLevel l&&worker.settlementId()!=null)HandBread.release(l,worker.settlementId(),worker.getUUID());
 }
}
