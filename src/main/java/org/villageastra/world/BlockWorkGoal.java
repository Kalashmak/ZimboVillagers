package org.villageastra.world;

import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

public final class BlockWorkGoal extends Goal {
    private final ResidentEntity worker;
    public BlockWorkGoal(ResidentEntity worker) { this.worker=worker; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    @Override public boolean canUse() { return worker.blockWork()!=null && worker.blockWork().active() && worker.getServer().getPlayerCount()>0 && worker.settlementId()!=null && CargoCustody.mayStartWork(worker); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void tick() { worker.blockWork().step(worker,worker.getServer().getPlayerCount()>0); }
    @Override public void stop() { worker.getNavigation().stop(); }
}
