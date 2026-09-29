package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import org.villageastra.server.SettlementData;
/** AD-039: a materialized caravaneer walks the contract route; progress is its real position projected onto the route and never jumps ahead. */
public final class CaravanGoal extends Goal {
 private final ResidentEntity npc;private int repath,idle,waiting;private double last;
 public CaravanGoal(ResidentEntity npc){this.npc=npc;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
 private net.minecraft.nbt.CompoundTag trip(){
  if(!(npc.level() instanceof ServerLevel l)||!npc.isAlive())return null;var t=Caravans.trip(l.getServer(),npc.getUUID());
  if(t==null||!t.getBoolean("materialized")||npc.getPersistentData().getLong(Caravans.EPOCH)!=Caravans.epoch(l.getServer(),npc.getUUID()))return null;
  var state=t.getString("state");return state.equals(Caravans.TRANSIT)||state.equals(Caravans.RETURNING)?t:null;
 }
 @Override public boolean canUse(){return trip()!=null;}
 @Override public boolean canContinueToUse(){return trip()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var t=trip();if(t==null)return;if(CaravanEscortDuty.tick(npc,t))return;var l=(ServerLevel)npc.level();var from=Caravans.from(t);var to=Caravans.to(t);double len=Math.max(1,Caravans.length(t));
  double along=((npc.getX()-from.getX())*(to.getX()-from.getX())+(npc.getZ()-from.getZ())*(to.getZ()-from.getZ()))/len;along=Math.max(0,Math.min(len,along));
  if(along>t.getDouble("progress")+.5){t.putDouble("progress",along);if(npc.tickCount%20==0)Caravans.update(l.getServer(),t);}
  boolean cargo=!t.getList("cargo",10).isEmpty();npc.displayWorkItem(cargo?ItemStack.of(t.getList("cargo",10).getCompound(0)):ItemStack.EMPTY);
  double dx=to.getX()+.5-npc.getX(),dz=to.getZ()+.5-npc.getZ();
  // AD-103: a fetch trip's search at the cart is counted in seconds, as the record counts it, not in goal ticks.
  if(dx*dx+dz*dz<=Caravans.ARRIVE*Caravans.ARRIVE){npc.getNavigation().stop();if(!t.getString("kind").equals(Caravans.RECOVER)||npc.tickCount%20==0)Caravans.arrive(l,t,SettlementData.get(l.getServer()).clock().ticks());npc.workStatus("caravan_arrived");return;}
  if(CaravanEscorts.waiting(l,t,npc)){npc.getNavigation().stop();return;}
  if(CaravanDogs.waiting(l,t,npc)){npc.getNavigation().stop();npc.workStatus("caravan_waiting_for_cart");return;}
  // ROAD-008 (AD-085): the caravaneer does not walk away from its cart. A cart left behind or stuck at a narrow place is waited for;
  // after a while the caravaneer goes back to it and leads it on by another line; a hitch that slipped is taken up again.
  var cart=l.getEntitiesOfClass(CartEntity.class,npc.getBoundingBox().inflate(16),c->t.getUUID("id").equals(c.trip())).stream().findFirst().orElse(null);
  if(cart!=null){
   if(cart.puller()==null&&npc.distanceToSqr(cart)<=CartEntity.REACH*CartEntity.REACH)cart.puller(npc.getUUID());
   boolean behind=npc.distanceToSqr(cart)>5*5;
   if(behind||cart.blocked()){
    waiting++;npc.getNavigation().stop();
    if(waiting>100){npc.getNavigation().moveTo(cart.getX(),cart.getY(),cart.getZ(),.6);npc.workStatus("caravan_fetching_cart");}
    else npc.workStatus("caravan_waiting_for_cart");
    if(waiting>400)waiting=0;
    return;}
   waiting=0;
  }
  if(--repath<=0){repath=20;
   // Something standing on the road — a cart left there, a beast — is walked round: after five seconds without a step forward the
   // caravaneer steps aside, to one side and then the other, and takes the road again beyond it.
   if(idle>=100){var a=Caravans.from(t);var b=Caravans.to(t);double rx=b.getX()-a.getX(),rz=b.getZ()-a.getZ(),n=Math.max(1,Math.sqrt(rx*rx+rz*rz));int side=(idle/100)%2==0?1:-1;
    var aside=npc.blockPosition().offset((int)Math.round(-rz/n*3*side+rx/n*3),0,(int)Math.round(rx/n*3*side+rz/n*3));var detour=npc.getNavigation().createPath(aside,0);
    if(detour!=null&&detour.canReach()){npc.getNavigation().moveTo(detour,.7);if(t.getDouble("progress")-last<.5)idle+=20;else idle=0;last=t.getDouble("progress");
     npc.workStatus("caravan_going_round");return;}}
   // The farthest route point that navigation can actually reach; a blocked caravan waits and tries again instead of teleporting.
   net.minecraft.world.level.pathfinder.Path best=null;
   for(int ahead=32;ahead>=8;ahead-=8){var target=Caravans.position(l,t,Math.min(len,t.getDouble("progress")+ahead));var path=npc.getNavigation().createPath(target,0);if(path!=null&&path.canReach()){best=path;break;}}
   if(best!=null)npc.getNavigation().moveTo(best,.7);
   else{var target=Caravans.position(l,t,Math.min(len,t.getDouble("progress")+8));npc.getMoveControl().setWantedPosition(target.getX()+.5,target.getY(),target.getZ()+.5,.7);}
   if(t.getDouble("progress")-last<.5)idle+=20;else idle=0;last=t.getDouble("progress");}
  npc.workStatus(idle>=600?"caravan_blocked":cargo?"caravan_travelling":"caravan_returning");
 }
 @Override public void stop(){npc.getNavigation().stop();npc.displayWorkItem(ItemStack.EMPTY);}
}
