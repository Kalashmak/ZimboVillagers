package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.SettlementData;
public final class WorkerSupplyGoal extends Goal {
 private final ResidentEntity worker;private int nextCheck;private final boolean withoutPlayers;
 public WorkerSupplyGoal(ResidentEntity w){this(w,false);}
 /** Physical GameTests run the same route without an online observer. */
 public WorkerSupplyGoal(ResidentEntity w,boolean withoutPlayers){worker=w;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 // Vanilla starts goals on alternating entity ticks. An exact modulo phase can
 // therefore be missed forever; elapsed checks work on either entity parity.
 @Override public boolean canUse(){if(worker.tickCount<nextCheck)return false;nextCheck=worker.tickCount+20;return CargoCustody.mayStartWork(worker)&&WorkerSupplies.available(worker,withoutPlayers);}
 @Override public boolean canContinueToUse(){return WorkerSupplies.available(worker,withoutPlayers)&&!CargoCustody.pending(worker.getServer(),worker.getUUID());}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){if(worker.tickCount%20!=0)return;var l=(ServerLevel)worker.level();var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());PorterWork.step(worker,LogisticsRoutes.workerRoute(l,e,e.settlement().workplace(worker.getUUID())),true);}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(net.minecraft.world.item.ItemStack.EMPTY);}
}
