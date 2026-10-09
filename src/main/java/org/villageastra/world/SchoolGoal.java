package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-031: the teacher stays at the school station; during the day children walk there. Attendance is counted by Population. */
public final class SchoolGoal extends Goal {
 private final ResidentEntity resident;private final boolean test;private int routeRequests;
 public SchoolGoal(ResidentEntity resident){this(resident,false);}
 public SchoolGoal(ResidentEntity resident,boolean test){this.resident=resident;this.test=test;setFlags(EnumSet.of(Flag.MOVE));}
 public int routeRequests(){return routeRequests;}
 private net.minecraft.core.BlockPos station(){
  if(!(resident.level() instanceof ServerLevel l)||!test&&l.getServer().getPlayerCount()==0||resident.settlementId()==null||resident.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive())return null;
  if(r.profession()==Profession.TEACHER){var b=e.settlement().workplace(r.id());return b!=null&&b.type().equals("school")&&Population.mayWork(r)&&!CargoCustody.pending(l.getServer(),r.id())?LogisticsRoutes.position(e,b):null;}
  return childStation(l,e,r);
 }
 /** A daytime journey to a physically present teacher is a child's duty, including outside its home neighborhood. */
 public static net.minecraft.core.BlockPos childStation(ServerLevel l,SettlementData.Entry e,Resident r){
  return !r.educated()&&l.getDayTime()%24000<12000&&(r.life()==Resident.Life.CHILD||Population.continuingSeat(l,e,r))?Population.schoolStation(l,e):null;
 }
 private boolean adultStudent(){
  if(!(resident.level() instanceof ServerLevel l)||resident.settlementId()==null)return false;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());var r=e==null?null:e.settlement().resident(resident.getUUID());
  return r!=null&&r.life()==Resident.Life.ADULT&&r.profession()!=Profession.TEACHER;
 }
 @Override public boolean canUse(){var s=station();return s!=null&&(adultStudent()||resident.distanceToSqr(s.getX()+.5,s.getY(),s.getZ()+.5)>9);}
 @Override public boolean canContinueToUse(){var s=station();return s!=null&&(adultStudent()||resident.distanceToSqr(s.getX()+.5,s.getY(),s.getZ()+.5)>4);}
 @Override public void tick(){var s=station();if(s!=null&&adultStudent()&&resident.distanceToSqr(s.getX()+.5,s.getY(),s.getZ()+.5)<=4){resident.getNavigation().stop();return;}if(s!=null&&resident.tickCount%20<2){routeRequests++;resident.getNavigation().moveTo(s.getX()+.5,s.getY(),s.getZ()+.5,.7);}}
 @Override public void stop(){resident.getNavigation().stop();}
}
