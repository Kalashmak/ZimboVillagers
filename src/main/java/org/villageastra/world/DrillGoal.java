package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.server.SettlementData;
/** AD-095: by day a recruit walks to the drill ground of the barracks; the time spent there is counted by Population, as school is. */
public final class DrillGoal extends Goal {
 private final ResidentEntity resident;
 public DrillGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE));}
 private net.minecraft.core.BlockPos ground(){
  if(!(resident.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0||resident.settlementId()==null||resident.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(resident.getUUID());
  if(r==null||!r.alive()||!r.recruit()||r.military()||l.getDayTime()%24000>=12000)return null;
  return Population.drillGround(e);
 }
 @Override public boolean canUse(){var g=ground();return g!=null&&resident.distanceToSqr(g.getX()+.5,g.getY(),g.getZ()+.5)>9;}
 @Override public boolean canContinueToUse(){var g=ground();return g!=null&&resident.distanceToSqr(g.getX()+.5,g.getY(),g.getZ()+.5)>4;}
 @Override public void tick(){var g=ground();if(g!=null&&resident.tickCount%20<2){resident.getNavigation().moveTo(g.getX()+.5,g.getY(),g.getZ()+1.5,.7);resident.workStatus("drilling");}}
 @Override public void stop(){resident.getNavigation().stop();}
}
