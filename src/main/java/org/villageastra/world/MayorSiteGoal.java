package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
/** AD-032: the NPC mayor personally walks to the planned site and approves the order there (JOB-001). */
public final class MayorSiteGoal extends Goal {
 private final ResidentEntity mayor;
 public MayorSiteGoal(ResidentEntity mayor){this.mayor=mayor;setFlags(EnumSet.of(Flag.MOVE));}
 private MayorPlanner.Proposal proposal(){
  if(!(mayor.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0||mayor.settlementId()==null||mayor.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(mayor.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(mayor.getUUID());if(r==null||!r.alive()||r.profession()!=Profession.MAYOR||e.settlement().governance().playerMayor()!=null)return null;
  return MayorPlanner.proposal(e.settlement().id());
 }
 @Override public boolean canUse(){return proposal()!=null;}
 @Override public boolean canContinueToUse(){return proposal()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var p=proposal();if(p==null||mayor.tickCount%20!=0)return;var l=(ServerLevel)mayor.level();var e=SettlementData.get(l.getServer()).entry(mayor.settlementId());
  var status=MayorPlanner.approveAtSite(l,e,mayor);mayor.workStatus("mayor_"+status);
  if(status.equals("walking"))mayor.getNavigation().moveTo(p.site().getX()+.5,p.site().getY()+1,p.site().getZ()+.5,.8);
 }
 @Override public void stop(){mayor.getNavigation().stop();}
}
