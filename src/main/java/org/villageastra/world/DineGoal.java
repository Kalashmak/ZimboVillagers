package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.server.SettlementData;
/** AD-139 §3.7: a resident invited to the restaurant walks to a free seat, sits at its table sit_ticks with the dish in hand and is served
 *  (Dining.serve: ate(due)). Priority 4: over work (6) and the hall project (5), under sleep (2), the raid shelter and panic (1). No seat free:
 *  it waits by the door. The seat is freed whenever the goal stops (a meal eaten, the night, a siege, death); an unfinished invitation simply
 *  expires into the pantry (Population.meal), so stopping never costs a meal. AD-143: on a chair the diner sits down — it rides the chair's
 *  seat (SeatEntity), renewing its lease every tick, and stands up when served or whenever the goal stops (dusk, a siege, danger preempting
 *  it); a chair broken under it stands it up and it takes another seat. On an older hall's stair it stands on the stair, turned to the table. */
public final class DineGoal extends Goal {
 private final ResidentEntity npc;private final java.util.function.LongSupplier clock;private int seat=-1,sat,turn,repath;
 public DineGoal(ResidentEntity npc){this(npc,null);}
 /** Tests: the village clock read from {@code clock} (the active clock stands still without players). */
 public DineGoal(ResidentEntity npc,java.util.function.LongSupplier clock){this.npc=npc;this.clock=clock;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private long now(ServerLevel l){return clock!=null?clock.getAsLong():SettlementData.get(l.getServer()).clock().ticks();}
 private SettlementData.Entry entry(){
  if(!(npc.level() instanceof ServerLevel l)||!npc.isAlive()||npc.settlementId()==null||npc.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());return e==null||!e.dimension().equals(l.dimension().location().toString())?null:e;}
 private Dining.Invite invite(SettlementData.Entry e){var l=(ServerLevel)npc.level();var i=Dining.invite(l,e,npc.getUUID());
  return i==null||now(l)>i.expires()||!Dining.day(l,e)||Sieges.besieged(l.getServer(),e.settlement().id())?null:i;}
 @Override public boolean canUse(){if(++turn%10!=0)return false;var e=entry();return e!=null&&invite(e)!=null;}
 @Override public boolean canContinueToUse(){var e=entry();return e!=null&&invite(e)!=null;}
 @Override public void start(){seat=-1;sat=0;repath=0;}
 @Override public void stop(){SeatEntity.stand(npc);var e=entry();if(e!=null)Dining.leave((ServerLevel)npc.level(),e,npc.getUUID());seat=-1;sat=0;npc.displayWorkItem(ItemStack.EMPTY);
  if(npc.workStatus().startsWith("dining"))npc.workStatus("");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var e=entry();if(e==null)return;var l=(ServerLevel)npc.level();var inv=invite(e);if(inv==null)return;
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(inv.building())).findFirst().orElse(null);if(b==null)return;
  // AD-143: a seat whose chair or table is gone (broken under the diner) is left; the diner takes another.
  if(seat>=0&&!Dining.standing(l,e,b,seat)){SeatEntity.stand(npc);Dining.leave(l,e,npc.getUUID());seat=-1;sat=0;repath=0;}
  if(seat<0&&(++repath%10==0||repath==1)){seat=Dining.takeSeat(l,e,b,npc.getUUID());
   if(seat<0){npc.workStatus("dining_waiting");var door=BuildingPlacement.at(e,b,BuildingBlueprints.doorX(b.type()),1,-1);if(npc.distanceToSqr(door.getX()+.5,door.getY(),door.getZ()+.5)>4)npc.getNavigation().moveTo(door.getX()+.5,door.getY(),door.getZ()+.5,.8);return;}}
  if(seat<0)return;
  var at=Dining.seatPos(e,b,seat);boolean chair=l.getBlockState(at).getBlock() instanceof ChairBlock;
  double dx=npc.getX()-(at.getX()+.5),dz=npc.getZ()-(at.getZ()+.5),dy=Math.abs(npc.getY()-at.getY());
  if(chair&&!SeatEntity.seated(npc)){
   // Beside the chair (a chair is no floor to walk onto): sit down on it; somebody else on it (a player) — another seat.
   if(dx*dx+dz*dz<2.6&&dy<=1.2){npc.getNavigation().stop();
    if(SeatEntity.sit(l,at,npc)==null){Dining.leave(l,e,npc.getUUID());seat=-1;repath=0;return;}}
   else{npc.workStatus("dining_walking");sat=0;if(++repath%20==1||npc.getNavigation().isDone())npc.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.8);return;}}
  else if(!chair&&(dx*dx+dz*dz>0.36||dy>1.2)){npc.workStatus("dining_walking");sat=0;if(++repath%20==1||npc.getNavigation().isDone())npc.getNavigation().moveTo(at.getX()+.5,at.getY()+.5,at.getZ()+.5,.8);
   // The last step onto the seat: a stair between a table and an aisle is an awkward target for the path finder.
   if(dx*dx+dz*dz<2.25&&dy<=1.2&&npc.getNavigation().isDone())npc.moveTo(at.getX()+.5,at.getY()+.5,at.getZ()+.5,npc.getYRot(),npc.getXRot());
   return;}
  if(chair)SeatEntity.keep(npc);
  npc.getNavigation().stop();var table=Dining.tablePos(e,b,seat);npc.getLookControl().setLookAt(table.getX()+.5,table.getY()+1,table.getZ()+.5);
  var dish=Dining.firstDish(l,e,b);npc.displayWorkItem(dish.isEmpty()?new ItemStack(Items.BREAD):dish);npc.workStatus("dining");
  if(++sat<Dining.SIT_TICKS)return;
  var r=e.settlement().resident(npc.getUUID());if(r!=null&&Dining.serve(l,e,b,r)){SeatEntity.stand(npc);npc.displayWorkItem(ItemStack.EMPTY);npc.workStatus("");}
 }
}
