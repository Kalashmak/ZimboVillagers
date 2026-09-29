package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Items;
import org.villageastra.server.SettlementData;
/** AD-164: a patrol holds MOVE above kennel wandering, but yields to its owner and an existing errand. */
public final class WolfPatrolGoal extends Goal {
 private final Wolf wolf;private BlockPos spot;private int retry,repath;
 public WolfPatrolGoal(Wolf wolf){this.wolf=wolf;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private SettlementData.Entry entry(){var id=VillageWolves.village(wolf);return id==null||!(wolf.level() instanceof ServerLevel l)?null:SettlementData.get(l.getServer()).entry(id);}
 private boolean available(SettlementData.Entry e){if(e==null)return false;var l=(ServerLevel)wolf.level();var owner=wolf.getOwner();
  return VillageWolves.ready(l,wolf)&&CartographyLadder.reserved(l,e,wolf)&&(owner==null||owner.level()!=l||owner.distanceToSqr(wolf)>WolfKennelGoal.OWNER_NEAR*WolfKennelGoal.OWNER_NEAR);}
 private boolean stocked(SettlementData.Entry e){var l=(ServerLevel)wolf.level();var house=CartographyLadder.house(l,e);var chest=house==null?null:LogisticsRoutes.chest(l,e,house);
  return chest!=null&&LogisticsRoutes.count(chest,s->s.is(Items.TORCH)&&!s.hasTag())>0;}
 @Override public boolean canUse(){if(wolf.tickCount<retry)return false;retry=wolf.tickCount+40;var e=entry();if(!available(e)||!stocked(e))return false;
  spot=CartographyLadder.dark((ServerLevel)wolf.level(),e,wolf.blockPosition());if(spot==null)return false;
  var path=wolf.getNavigation().createPath(spot,0);if(path==null||!path.canReach()){spot=null;return false;}return true;}
 @Override public boolean canContinueToUse(){return spot!=null&&available(entry());}
 @Override public void start(){repath=0;wolf.setInSittingPose(false);}
 @Override public void tick(){if(--repath>0)return;repath=10;var e=entry();if(!available(e)||!stocked(e)){spot=null;return;}
  var l=(ServerLevel)wolf.level();
  if(wolf.distanceToSqr(spot.getX()+.5,spot.getY(),spot.getZ()+.5)<=CartographyLadder.PAW*CartographyLadder.PAW){
   // Recheck darkness at arrival: another worker may have lit the destination meanwhile.
   var fresh=CartographyLadder.dark(l,e,spot);if(spot.equals(fresh))CartographyLadder.light(l,e,CartographyLadder.house(l,e),spot);spot=null;return;}
  if(!wolf.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,1.1)){spot=null;retry=wolf.tickCount+100;}
 }
 @Override public void stop(){wolf.getNavigation().stop();spot=null;}
}
