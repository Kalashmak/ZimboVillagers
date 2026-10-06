package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
public final class PorterGoal extends Goal {
 private final ResidentEntity worker;private final boolean withoutPlayers;
 public PorterGoal(ResidentEntity worker){this(worker,false);}
 public PorterGoal(ResidentEntity worker,boolean withoutPlayers){this.worker=worker;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean canUse(){return PorterWork.eligible(worker,withoutPlayers)&&CargoCustody.mayStartFoodTransport(worker);}
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 // AD-147: a warehouse's courier plans by need and takes a cart from II (WarehouseTrips); the hall's porter walks as before.
 @Override public void tick(){if(worker.tickCount%20!=0)return;if(WarehouseTrips.courier(worker))WarehouseTrips.step(worker);else PorterWork.step(worker);}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(net.minecraft.world.item.ItemStack.EMPTY);}
}
