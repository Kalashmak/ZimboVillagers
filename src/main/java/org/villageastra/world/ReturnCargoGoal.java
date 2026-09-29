package org.villageastra.world;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;
/** Reassignment cannot teleport the old worker's physical cargo to the next owner. */
public final class ReturnCargoGoal extends Goal {
 private final ResidentEntity worker;private int lastCheck=-100;
 public ReturnCargoGoal(ResidentEntity worker){this.worker=worker;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 @Override public boolean canUse(){
  if(worker.getServer()==null||worker.getServer().getPlayerCount()==0||worker.settlementId()==null||worker.escortPlayer()!=null||CargoCustody.dead(worker.getServer(),worker.getUUID()))return false;
  if(worker.tickCount-lastCheck<20)return false;lastCheck=worker.tickCount;return !CargoCustody.mayStartWork(worker)&&CargoCustody.pending(worker.getServer(),worker.getUUID());
 }
 @Override public boolean canContinueToUse(){return worker.isAlive()&&worker.escortPlayer()==null&&worker.getServer().getPlayerCount()>0&&CargoCustody.pending(worker.getServer(),worker.getUUID());}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){if(worker.tickCount%20==0)CargoCustody.returnStep(worker,worker.getServer().getPlayerCount()>0);}
 @Override public void stop(){worker.displayWorkItem(net.minecraft.world.item.ItemStack.EMPTY);worker.getNavigation().stop();}
}
