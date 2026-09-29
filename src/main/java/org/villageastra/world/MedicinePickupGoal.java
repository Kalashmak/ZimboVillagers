package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
/** At clinic V, residents without a dose collect one themselves when no free delivery wolf is available. */
public final class MedicinePickupGoal extends Goal {
 private final ResidentEntity resident;private BlockPos counter;
 public MedicinePickupGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean canUse(){if(!(resident.level() instanceof ServerLevel l)||!resident.isAlive()||resident.settlementId()==null||resident.escortPlayer()!=null||l.getServer().getPlayerCount()==0||CargoCustody.dead(l.getServer(),resident.getUUID())||CargoCustody.pending(l.getServer(),resident.getUUID()))return false;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive()||MedicinePacks.carried(l,r.id()))return false;
  var trip=MedicineDelivery.inspect(l,e.settlement().id());if(MedicineDelivery.active(trip)&&trip.getUUID("patient").equals(r.id())&&trip.hasUUID("wolf"))return false;
  if(VillageDogs.pulls()&&!VillageDogs.available(l,e).isEmpty())return false;var clinic=Medicine.clinic(l,e);if(clinic==null||BuildingLevels.level(l,e,clinic)!=Medicine.MEDICINE)return false;
  var chest=LogisticsRoutes.chest(l,e,clinic);if(chest==null||chest.countItem(VillageAstra.BANDAGE.get())==0&&!MedicineDelivery.active(trip))return false;counter=LogisticsRoutes.position(e,clinic);return true;
 }
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public void tick(){if(counter==null)return;resident.workStatus("patient_to_hospital");if(resident.distanceToSqr(counter.getX()+.5,counter.getY(),counter.getZ()+.5)>6.25){if(resident.tickCount%20<2)resident.getNavigation().moveTo(counter.getX()+1.5,counter.getY(),counter.getZ()+.5,.8);}else resident.getNavigation().stop();}
 @Override public void stop(){resident.getNavigation().stop();counter=null;}
}
