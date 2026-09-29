package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.server.SettlementData;
/** AD-152: a sick resident walks to the hospital (the clinic's ward) and stays there until it is cured; with the clinic's medicine of V and VI
 *  the sick stay where they are. */
public final class PatientGoal extends Goal {
 private final ResidentEntity resident;
 public PatientGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE));}
 private net.minecraft.core.BlockPos ward(){
  if(!(resident.level() instanceof ServerLevel l)||resident.settlementId()==null||resident.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null)return null;
  var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive())return null;
  if(!r.sick()){var clinic=Medicine.clinic(l,e);if(resident.getHealth()>resident.getMaxHealth()-DoctorGoal.INJURED||clinic==null||BuildingLevels.level(l,e,clinic)>=Medicine.VISITS)return null;}
  return Medicine.ward(l,e);
 }
 @Override public boolean canUse(){var w=ward();return w!=null&&resident.distanceToSqr(w.getX()+.5,w.getY(),w.getZ()+.5)>4*4;}
 @Override public boolean canContinueToUse(){var w=ward();return w!=null&&resident.distanceToSqr(w.getX()+.5,w.getY(),w.getZ()+.5)>2*2;}
 @Override public void tick(){var w=ward();if(w!=null&&resident.tickCount%20<2){resident.getNavigation().moveTo(w.getX()+.5,w.getY(),w.getZ()+.5,.6);resident.workStatus("patient_to_hospital");}}
 @Override public void stop(){resident.getNavigation().stop();}
}
