package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-034: an educated doctor walks to an injured resident and heals with a real bandage from the clinic chest; no revival. */
public final class DoctorGoal extends Goal {
 public static final float HEAL=8F,INJURED=4F;public static final int RADIUS=48;
 private final ResidentEntity doctor;private final boolean withoutPlayers;private ResidentEntity patient;
 public DoctorGoal(ResidentEntity doctor){this(doctor,false);}
 public DoctorGoal(ResidentEntity doctor,boolean withoutPlayers){this.doctor=doctor;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private SettlementData.Entry entry(){
  if(!(doctor.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||doctor.settlementId()==null||doctor.escortPlayer()!=null||!doctor.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(doctor.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(doctor.getUUID());var b=e.settlement().workplace(doctor.getUUID());
  return r!=null&&r.alive()&&r.educated()&&r.profession()==Profession.DOCTOR&&b!=null&&b.type().equals("clinic")?e:null;
 }
 /** AD-112: how far from the centre a doctor of a clinic at this working level goes to a patient (RADIUS at level I). */
 public static int radius(int level){return CoreEffects.value("clinic","radius",level);}
 /** The radius of this doctor's clinic at its working level; a doctor without a clinic keeps RADIUS. */
 public static int radius(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic){return clinic!=null&&clinic.type().equals("clinic")?radius(BuildingLevels.level(l,e,clinic)):RADIUS;}
 static ResidentEntity injured(ServerLevel l,SettlementData.Entry e,ResidentEntity doctor){
  ResidentEntity best=null;double bestDistance=Double.MAX_VALUE;long radius=radius(l,e,e.settlement().workplace(doctor.getUUID()));
  // AD-152: from the clinic's visits level the medics also walk to the sick at home.
  var clinic=e.settlement().workplace(doctor.getUUID());boolean visits=clinic!=null&&BuildingLevels.level(l,e,clinic)>=Medicine.VISITS;
  for(var r:e.settlement().residents()){if(!r.alive()||!(l.getEntity(r.id()) instanceof ResidentEntity npc)||npc.getHealth()>npc.getMaxHealth()-INJURED&&!(visits&&r.sick()))continue;
   if(!visits){var at=LogisticsRoutes.position(e,clinic);if(npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>Medicine.WARD*Medicine.WARD)continue;}
   if(npc.blockPosition().distSqr(e.center())>radius*radius)continue;double d=npc.distanceToSqr(doctor);if(d<bestDistance){best=npc;bestDistance=d;}}
  return best;
 }
 /** One healing act: exactly one bandage leaves the clinic chest per treatment id. */
 public static boolean treat(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic,ResidentEntity patient,long now){
  var chest=LogisticsRoutes.chest(l,e,clinic);if(chest==null)return false;var id=Settlement.childId(patient.getUUID(),"treatment/"+now);
  boolean fresh=!WorldJournal.exists(l,id);var got=WorldJournal.recoverAmount(l,id);
  if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(VillageAstra.BANDAGE.get())){got=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,clinic),slot,chest.getItem(slot).copy(),1);break;}
  if(got.isEmpty())return false;
  // A replayed treatment id never heals twice; a crash between debit and healing loses the effect, not a bandage copy.
  if(fresh&&patient.isAlive()){patient.heal(HEAL);var r=e.settlement().resident(patient.getUUID());if(r!=null)r.cure();}return true;
 }
 /** AD-152 (probe med-probe: an idle medic went to bake bread by hand at the hall while the sick lay in the ward): while the village has sick
  *  and its hospital cures in beds, the medic keeps to the clinic's station. */
 private boolean ward;
 private boolean wardNeeded(ServerLevel l,SettlementData.Entry e){
  var clinic=e.settlement().workplace(doctor.getUUID());if(clinic==null||BuildingLevels.level(l,e,clinic)>=Medicine.MEDICINE)return false;
  return e.settlement().residents().stream().anyMatch(r->r.alive()&&r.sick());
 }
 @Override public boolean canUse(){var e=entry();if(e==null||!CargoCustody.mayStartWork(doctor))return false;var l=(ServerLevel)doctor.level();patient=injured(l,e,doctor);
  ward=patient==null&&wardNeeded(l,e);return patient!=null||ward;}
 @Override public boolean canContinueToUse(){var e=entry();if(e!=null&&ward){var l=(ServerLevel)doctor.level();var p=injured(l,e,doctor);if(p!=null){patient=p;ward=false;return true;}return wardNeeded(l,e);}
  if(e==null||patient==null||!patient.isAlive())return false;var r=e.settlement().resident(patient.getUUID());
  return patient.getHealth()<=patient.getMaxHealth()-INJURED||r!=null&&r.sick();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var e=entry();if(e==null)return;var l=(ServerLevel)doctor.level();
  if(ward){var at=LogisticsRoutes.position(e,e.settlement().workplace(doctor.getUUID()));
   if(doctor.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>2.5*2.5){if(doctor.tickCount%20==0)doctor.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.8);}else doctor.getNavigation().stop();
   doctor.workStatus("doctor_ward");return;}
  if(patient==null)return;doctor.getLookControl().setLookAt(patient,30,30);
  if(doctor.distanceToSqr(patient)>6.25){doctor.getNavigation().moveTo(patient,.9);doctor.workStatus("doctor_walking");return;}
  doctor.getNavigation().stop();if(doctor.tickCount%40!=0)return;
  var clinic=e.settlement().workplace(doctor.getUUID());
  if(treat(l,e,clinic,patient,SettlementData.get(l.getServer()).clock().ticks())){doctor.swing(net.minecraft.world.InteractionHand.MAIN_HAND);doctor.displayWorkItem(new ItemStack(VillageAstra.BANDAGE.get()));doctor.workStatus("doctor_treating");}
  else doctor.workStatus("doctor_missing_bandages");
 }
 @Override public void stop(){doctor.getNavigation().stop();doctor.displayWorkItem(ItemStack.EMPTY);patient=null;ward=false;}
}
