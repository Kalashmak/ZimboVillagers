package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
/** AD-053: a miner with a claimed quarry walks to the next block of it and really takes that block out, one at a time. */
public final class QuarryGoal extends Goal {
 /** AD-122: the quarry and a clearing are worked with an iron pick (their drops are an iron pick's), at a player's speed with it. */
 private static final net.minecraft.world.item.ItemStack PICK=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
 private final ResidentEntity worker;private BlockPos target,cracked;private int labor;private boolean excavating;
 public QuarryGoal(ResidentEntity worker){this.worker=worker;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private SettlementData.Entry entry(){
  if(worker.settlementId()==null||worker.level().isClientSide)return null;
  return SettlementData.get(worker.level().getServer()).entry(worker.settlementId());
 }
 private org.villageastra.domain.Settlement.Building workplace(SettlementData.Entry e){
  var r=e.settlement().resident(worker.getUUID());if(r==null||r.profession()!=Profession.MINER)return null;
  return e.settlement().workplace(r.id());
 }
 @Override public boolean canUse(){
  var e=entry();if(e==null)return false;
  var mine=workplace(e);if(mine==null)return false;
  if(!CargoCustody.mayStartWork(worker))return false;
  var level=(net.minecraft.server.level.ServerLevel)worker.level();
  target=next(level,e);return target!=null;
 }
 /** AD-057: stone and ore of a clearing ordered on the map come before the quarry — the settlement is waiting on that land. */
 private BlockPos next(net.minecraft.server.level.ServerLevel level,SettlementData.Entry e){
  // AD-070: under a siege the miners dig out no ordered clearing — that is construction work; the quarry, a production, goes on.
  var dig=Sieges.besieged(level.getServer(),e.settlement().id())?null:Excavation.next(level,e);excavating=dig!=null;if(dig!=null)return dig;
  // AD-058: a built quarry opens its chunk the first time a miner looks for work.
  if(Quarry.building(e)!=null)Quarry.open(level,e);
  return Quarry.next(level,e);
 }
 @Override public boolean canContinueToUse(){return target!=null&&canUse();}
 @Override public void stop(){if(cracked!=null&&worker.level() instanceof net.minecraft.server.level.ServerLevel l)MinerSpeed.clear(l,worker,cracked);cracked=null;target=null;labor=0;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var e=entry();if(e==null||target==null)return;
  var level=(net.minecraft.server.level.ServerLevel)worker.level();
  var mine=workplace(e);if(mine==null)return;
  double reach=worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target));
  if(reach>BuildingOrders.REACH_SQ){
   if(worker.tickCount%10==0||worker.getNavigation().isDone())
    worker.getNavigation().moveTo(target.getX()+.5,target.getY()+1,target.getZ()+.5,.8);
   worker.workStatus(excavating?"walking_to_clearing":"walking_to_quarry");return;
  }
  worker.getNavigation().stop();worker.workStatus(excavating?"clearing_stone":"quarrying");
  var rock=level.getBlockState(target);int ticks=Math.min(MinerSpeed.breakTicks(rock,level,target,PICK),200);
  // AD-122 (owner): the swing, the growing crack and the hit sound of a player at the block; its break sound and particles when it is out.
  if(cracked!=null&&!cracked.equals(target))MinerSpeed.clear(level,worker,cracked);cracked=target;
  MinerSpeed.progress(level,worker,target,rock,++labor,ticks);
  if(labor<ticks)return;labor=0;
  String result=excavating?Excavation.dig(level,e,mine,target):Quarry.dig(level,e,mine,target);
  if(result.isEmpty())MinerSpeed.broken(level,worker,target,rock);else MinerSpeed.clear(level,worker,target);cracked=null;
  if(!result.isEmpty())worker.workStatus(result.equals("stock_full")?"stock_full":"quarry_blocked");
  target=next(level,e);
 }
}
