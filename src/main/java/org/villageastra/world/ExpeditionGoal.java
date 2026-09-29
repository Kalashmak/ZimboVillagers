package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-041: the expeditioner walks to an unexplored sector, looks at what is really there and reports a lead for real rations and paper. */
public final class ExpeditionGoal extends Goal {
 public static final int REACH=8;
 private final ResidentEntity scout;private final boolean withoutPlayers;private BlockPos target;private int sector=-1,surveying,repath;
 public ExpeditionGoal(ResidentEntity scout){this(scout,false);}
 public ExpeditionGoal(ResidentEntity scout,boolean withoutPlayers){this.scout=scout;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private record Duty(SettlementData.Entry entry,Settlement.Building office){}
 private Duty duty(){
  if(!(scout.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||scout.settlementId()==null||scout.escortPlayer()!=null||!scout.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(scout.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(scout.getUUID());var b=e.settlement().workplace(scout.getUUID());
  return r!=null&&r.alive()&&r.educated()&&r.profession()==Profession.EXPEDITIONER&&b!=null&&b.type().equals("expedition")?new Duty(e,b):null;
 }
 @Override public boolean canUse(){
  var d=duty();if(d==null||!CargoCustody.mayStartWork(scout))return false;var l=(ServerLevel)scout.level();
  var chest=LogisticsRoutes.chest(l,d.entry(),d.office());if(chest==null||chest.countItem(Items.PAPER)==0||chest.countItem(Items.BREAD)==0){scout.workStatus("expedition_missing_supplies");return false;}
  sector=Expeditions.nextSector(l,d.entry().settlement().id());if(sector<0){scout.workStatus("expedition_done");return false;}
  target=Expeditions.target(l,d.entry(),sector);return true;
 }
 @Override public boolean canContinueToUse(){return target!=null&&duty()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var d=duty();if(d==null||target==null)return;var l=(ServerLevel)scout.level();
  if(!l.hasChunkAt(target)){scout.workStatus("expedition_unloaded");target=null;return;}
  double dx=scout.getX()-target.getX()-.5,dz=scout.getZ()-target.getZ()-.5;
  if(dx*dx+dz*dz<=REACH*REACH){
   scout.getNavigation().stop();scout.displayWorkItem(new ItemStack(Items.COMPASS));scout.workStatus("expedition_surveying");
   if(++surveying>=Expeditions.SURVEY_TICKS){var now=SettlementData.get(l.getServer()).clock().ticks();
    var lead=Expeditions.report(l,d.entry(),d.office(),scout.blockPosition(),sector,now);
    if(lead!=null)scout.swing(net.minecraft.world.InteractionHand.MAIN_HAND);else scout.workStatus("expedition_nothing");target=null;surveying=0;}
   return;}
  surveying=0;if(--repath<=0){repath=40;if(!scout.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.8)&&scout.getNavigation().isDone()){scout.workStatus("expedition_blocked");target=null;return;}}
  scout.workStatus("expedition_walking");
 }
 @Override public void stop(){scout.getNavigation().stop();scout.displayWorkItem(ItemStack.EMPTY);target=null;surveying=0;}
}
