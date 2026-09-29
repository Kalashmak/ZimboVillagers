package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.SettlementData;
public final class WorkerSupplyGoal extends Goal {
 private final ResidentEntity worker;
 public WorkerSupplyGoal(ResidentEntity w){worker=w;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean canUse(){return worker.tickCount%40==0&&CargoCustody.mayStartWork(worker)&&WorkerSupplies.available(worker);}
 @Override public boolean canContinueToUse(){return WorkerSupplies.available(worker)&&!CargoCustody.pending(worker.getServer(),worker.getUUID());}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){if(worker.tickCount%20!=0)return;var l=(ServerLevel)worker.level();var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());PorterWork.step(worker,LogisticsRoutes.workerRoute(l,e,e.settlement().workplace(worker.getUUID())),true);}
 @Override public void stop(){worker.getNavigation().stop();worker.displayWorkItem(net.minecraft.world.item.ItemStack.EMPTY);}
}
