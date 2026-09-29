package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.villageastra.server.SettlementData;
/** AD-029: the assigned worker walks to the workshop chest and advances the durable job only while standing there. */
public final class WorkshopGoal extends Goal {
 private final ResidentEntity worker;private final boolean withoutPlayers;private int lastCheck=-100;private boolean useful,yielded;
 public WorkshopGoal(ResidentEntity worker){this(worker,false);}
 /** For GameTests, which have no player online. */
 public WorkshopGoal(ResidentEntity worker,boolean withoutPlayers){this.worker=worker;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private org.villageastra.domain.Settlement.Building workplace(SettlementData.Entry e){var b=e.settlement().workplace(worker.getUUID());var r=e.settlement().resident(worker.getUUID());return b==null&&r!=null&&r.life()==org.villageastra.domain.Resident.Life.ADULT&&r.profession()==null?Workshops.hall(e):b;}
 private SettlementData.Entry entry(){
  if(!(worker.level() instanceof ServerLevel l)||!withoutPlayers&&worker.getServer().getPlayerCount()==0||worker.settlementId()==null||worker.escortPlayer()!=null||!worker.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(worker.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var b=workplace(e);return Workshops.eligible(e.settlement().resident(worker.getUUID()),b)?e:null;
 }
 /** Useful when a job is in progress or the settlement wants something this workshop can make now. */
 private boolean work(){
  var e=entry();if(e==null)return false;var l=(ServerLevel)worker.level();var b=workplace(e);
  if(bakes(l,e,b))return false;
  var t=Workshops.inspect(l,b.id());if(t.getBoolean("physicalSmelt")&&!t.getString("stage").equals("idle")&&t.hasUUID("worker")&&!t.getUUID("worker").equals(worker.getUUID()))return false;if(!t.isEmpty()&&!t.getString("stage").equals("idle"))return true;
  // AD-104 P2: the station's fuel bank counts, so a bakery whose bank covers the bake starts without a fuel item in the chest.
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return false;var wants=Workshops.wants(l,e);
  if(Workshops.plan(l,e,b,chest,wants)!=null)return true;
  // AD-104 P2: a station with nothing to make now still says what it lacks, so the porters bring it. Its needs were written only by a job
  // turn, and a turn needs something to make: a new bakery with an empty chest waited for flour it never asked for. Idle, the turn only
  // records the needs (the station's record is idle, and no job can be planned from this chest).
  if(t.isEmpty()||t.getString("stage").equals("idle"))Workshops.advance(l,e,b,SettlementData.get(l.getServer()).clock().ticks(),wants);
  return false;
 }
 /** The hall's simple crafting gives way while the village's hand bread wants this worker (HandBreadGoal.calls). */
 private boolean bakes(ServerLevel l,SettlementData.Entry e,org.villageastra.domain.Settlement.Building b){var hall=Workshops.hall(e);return hall!=null&&hall.id().equals(b.id())&&HandBreadGoal.calls(worker,withoutPlayers);}
 @Override public boolean canUse(){if(worker.tickCount-lastCheck<40)return useful;lastCheck=worker.tickCount;useful=CargoCustody.mayStartWork(worker)&&work();return useful;}
 @Override public boolean canContinueToUse(){
  if(yielded||entry()==null||CargoCustody.pending(worker.getServer(),worker.getUUID()))return false;
  // The goal selector looks at a resident only every other tick, on its own parity: a window of two ticks (as HallUpgradeGoal's check).
  if(worker.tickCount%40<2){var e=entry();var l=(ServerLevel)worker.level();if(bakes(l,e,workplace(e)))return false;}
  return true;
 }
 @Override public void start(){yielded=false;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var e=entry();if(e==null)return;var l=(ServerLevel)worker.level();var b=workplace(e);var job=Workshops.inspect(l,b.id());if(!NaturalFurnace.claim(l,b,job,worker.getUUID())){
   // Another worker holds this smelting job: this one lets the goal go instead of standing in it for good (it kept a second hall worker
   // "walking" all day, out of reach of every other goal of priority 6, hand bread included). canUse skips such a job (work()).
   worker.getNavigation().stop();yielded=true;useful=false;lastCheck=worker.tickCount;return;}var pos=NaturalFurnace.workPosition(l,e,b,job);
  if(job.getBoolean("physicalSmelt"))worker.displayWorkItem(ItemStack.of(job.getCompound("carried")));
  // Renovation may put a temporary wall between the worker and a nearby chest/furnace.
  // Being within arm's reach is not permission to take or deliver materials through that wall.
  var sight=l.clip(new net.minecraft.world.level.ClipContext(worker.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,worker));
  if(worker.distanceToSqr(pos.getX()+1.5,pos.getY(),pos.getZ()+.5)>6.25||sight.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&!sight.getBlockPos().equals(pos)){worker.getNavigation().moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5,.8);worker.workStatus("walking");return;}
  worker.getNavigation().stop();if(worker.tickCount%20!=0)return;
  var status=Workshops.advance(l,e,b,SettlementData.get(l.getServer()).clock().ticks(),Workshops.wants(l,e));worker.workStatus(status);
  var t=Workshops.inspect(l,b.id());worker.displayWorkItem(t.getBoolean("physicalSmelt")?ItemStack.of(t.getCompound("carried")):t.contains("outputs")&&!t.getString("stage").equals("idle")?ItemStack.of(t.getList("outputs",10).getCompound(0)):ItemStack.EMPTY);
  if(status.equals("workshop_idle")||status.equals("workshop_missing_inputs")){useful=false;lastCheck=worker.tickCount;}
 }
 @Override public boolean isInterruptable(){return true;}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(ItemStack.EMPTY);}
}
