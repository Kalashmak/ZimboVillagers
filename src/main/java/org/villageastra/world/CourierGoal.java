package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.server.SettlementData;
/** AD-139 §5.3: the restaurant's courier at work (Couriers.step every 10 ticks): load dishes at the restaurant's chest, carry them to the
 *  residents working away from the hall, serve each a portion, bring back what was not opened. Priority 6, with the other work. */
public final class CourierGoal extends Goal {
 private final ResidentEntity npc;private final java.util.function.LongSupplier clock;private int turn,waited;
 public CourierGoal(ResidentEntity npc){this(npc,null);}
 /** Tests: the village clock read from {@code clock} (the active clock stands still without players). */
 public CourierGoal(ResidentEntity npc,java.util.function.LongSupplier clock){this.npc=npc;this.clock=clock;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private boolean eligible(){
  if(!(npc.level() instanceof ServerLevel l)||!npc.isAlive()||npc.settlementId()==null||npc.escortPlayer()!=null)return false;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return false;
  var r=e.settlement().resident(npc.getUUID());return r!=null&&Population.mayWork(r)&&Couriers.courier(e,npc.getUUID());}
 @Override public boolean canUse(){return ++turn%20==0&&eligible();}
 @Override public boolean canContinueToUse(){return eligible();}
 @Override public void start(){waited=0;}
 @Override public void stop(){npc.getNavigation().stop();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){if(++turn%10==0)waited=Couriers.step(npc,waited,clock!=null?clock.getAsLong():SettlementData.get(((ServerLevel)npc.level()).getServer()).clock().ticks());}
}
