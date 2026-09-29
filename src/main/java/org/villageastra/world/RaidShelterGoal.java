package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-064: during a raid the residents who do not fight (WAR-002) hurry into the town hall and stay inside until the raid is over. */
public final class RaidShelterGoal extends Goal {
 /** How near raiders must be to a resident or to the hall before the resident runs for cover. */
 public static final int ALARM=40;
 private final ResidentEntity resident;private BlockPos shelter;private int repath;
 public RaidShelterGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
 /** Inside the hall, away from its door. */
 public static BlockPos shelter(SettlementData.Entry e){
  var hall=Workshops.hall(e);if(hall==null)return null;
  // AD-121: inside the hall - a castle village's castle hall (HallSite).
  return HallSite.castle(e.settlement())?HallSite.shelter(e):BuildingPlacement.at(e,hall,3,1,4);
 }
 public static boolean fights(Resident r){return r.profession()==Profession.GUARD||r.profession()==Profession.ARCHER_GUARD||r.profession()==Profession.SOLDIER;}
 private boolean alarmed(){
  if(!(resident.level() instanceof ServerLevel l)||resident.settlementId()==null||resident.tickCount%10>1&&shelter==null)return false;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null)return false;
  shelter=needsShelter(l,e,resident);return shelter!=null;
 }
 /** Where a resident should hide right now, or null: fighters stay out, and only raiders near them or near the hall raise the alarm. */
 public static BlockPos needsShelter(ServerLevel l,SettlementData.Entry e,ResidentEntity resident){
  var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive()||fights(r)||resident.escortPlayer()!=null)return null;
  var raiders=Raids.raiders(l,e.settlement().id());if(raiders.isEmpty())return null;
  var hall=shelter(e);if(hall==null)return null;
  for(var mob:raiders)if(mob.distanceToSqr(resident)<=ALARM*ALARM||mob.blockPosition().distSqr(e.center())<=ALARM*ALARM)return hall;
  return null;
 }
 @Override public boolean canUse(){return alarmed();}
 @Override public boolean canContinueToUse(){return shelter!=null&&resident.level() instanceof ServerLevel l&&resident.settlementId()!=null&&Raids.active(l,resident.settlementId());}
 @Override public void start(){repath=0;if(resident.isSleeping())resident.stopSleeping();resident.workStatus("sheltering");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  if(resident.position().distanceToSqr(shelter.getX()+.5,shelter.getY(),shelter.getZ()+.5)<=2.25){resident.getNavigation().stop();return;}
  if(--repath<=0){repath=20;resident.getNavigation().moveTo(shelter.getX()+.5,shelter.getY(),shelter.getZ()+.5,1.0);}
 }
 @Override public void stop(){resident.getNavigation().stop();shelter=null;}
 public BlockPos target(){return shelter;}
}
