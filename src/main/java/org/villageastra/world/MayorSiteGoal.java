package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
/** AD-032: the NPC mayor personally walks to the planned site and approves the order there (JOB-001). */
public final class MayorSiteGoal extends Goal {
 private final ResidentEntity mayor;private final boolean withoutPlayers;
 public MayorSiteGoal(ResidentEntity mayor){this(mayor,false);}
 public MayorSiteGoal(ResidentEntity mayor,boolean withoutPlayers){this.mayor=mayor;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE));}
 private MayorPlanner.Proposal proposal(){
  if(!(mayor.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||mayor.settlementId()==null||mayor.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(mayor.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(mayor.getUUID());if(r==null||!r.alive()||r.profession()!=Profession.MAYOR||e.settlement().governance().playerMayor()!=null)return null;
  return MayorPlanner.proposal(e.settlement().id());
 }
 @Override public boolean canUse(){return proposal()!=null;}
 @Override public boolean canContinueToUse(){return proposal()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var p=proposal();if(p==null||mayor.tickCount%20!=0)return;var l=(ServerLevel)mayor.level();var e=SettlementData.get(l.getServer()).entry(mayor.settlementId());
  if(l.hasChunkAt(p.site())&&!p.site().equals(MayorPlanner.siteGround(l,p.site()))){MayorPlanner.abandonSite(e.settlement().id(),p);mayor.workStatus("mayor_rejected_ground");return;}
  var status=MayorPlanner.approveAtSite(l,e,mayor);mayor.workStatus("mayor_"+status);
  if(status.equals("walking")){
   var path=mayor.routeTo(p.site().above(),0);
   if(!HarvestAccess.reversible(path)){MayorPlanner.abandonSite(e.settlement().id(),p);mayor.getNavigation().stop();mayor.workStatus("mayor_unreachable_site");return;}
   mayor.getNavigation().moveTo(path,.8);
  }
 }
 @Override public void stop(){mayor.getNavigation().stop();}
}
