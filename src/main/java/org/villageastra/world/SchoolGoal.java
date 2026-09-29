package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-031: the teacher stays at the school station; during the day children walk there. Attendance is counted by Population. */
public final class SchoolGoal extends Goal {
 private final ResidentEntity resident;
 public SchoolGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE));}
 private net.minecraft.core.BlockPos station(){
  if(!(resident.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0||resident.settlementId()==null||resident.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive())return null;
  if(r.profession()==Profession.TEACHER){var b=e.settlement().workplace(r.id());return b!=null&&b.type().equals("school")&&Population.mayWork(r)?LogisticsRoutes.position(e,b):null;}
  if(r.life()!=Resident.Life.CHILD||r.educated()||l.getDayTime()%24000>=12000)return null;
  return Population.schoolStation(l,e);
 }
 @Override public boolean canUse(){var s=station();return s!=null&&resident.distanceToSqr(s.getX()+.5,s.getY(),s.getZ()+.5)>9;}
 @Override public boolean canContinueToUse(){var s=station();return s!=null&&resident.distanceToSqr(s.getX()+.5,s.getY(),s.getZ()+.5)>4;}
 @Override public void tick(){var s=station();if(s!=null&&resident.tickCount%20<2)resident.getNavigation().moveTo(s.getX()+.5,s.getY(),s.getZ()+.5,.7);}
 @Override public void stop(){resident.getNavigation().stop();}
}
