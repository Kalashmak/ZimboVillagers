package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.Horse;
import org.villageastra.server.SettlementData;
/** The horse walks a navigable route, waits for its physical cart and returns to its yard after delivery. */
public final class CaravanHorseGoal extends Goal {
 private final Horse horse;private int repath,waiting;
 public CaravanHorseGoal(Horse horse){this.horse=horse;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
 @Override public boolean canUse(){return VillageHorses.village(horse)!=null&&!CaravanHorses.stale(horse)&&!horse.isVehicle()&&!horse.isLeashed();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var l=(ServerLevel)horse.level();var t=CaravanHorses.record(l.getServer(),horse.getUUID());
  if(t==null||!t.getBoolean("materialized")){
   if(--repath>0)return;repath=40;var e=SettlementData.get(l.getServer()).entry(VillageHorses.village(horse));var yard=e==null?null:Caravans.yard(e);if(yard==null)return;
   var origin=BuildingPlacement.origin(e,yard);var at=CartHitch.parking(l,origin);if(at!=null&&horse.blockPosition().distSqr(origin)>8*8)horse.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.8);else horse.getNavigation().stop();return;
  }
  var from=Caravans.from(t);var to=Caravans.to(t);double length=Math.max(1,Caravans.length(t));
  double along=((horse.getX()-from.getX())*(to.getX()-from.getX())+(horse.getZ()-from.getZ())*(to.getZ()-from.getZ()))/length;
  t.putDouble("progress",Math.max(t.getDouble("progress"),Math.max(0,Math.min(length,along))));
  var cart=l.getEntitiesOfClass(CartEntity.class,horse.getBoundingBox().inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())).stream().findFirst().orElse(null);
  if(t.contains("cart")&&cart==null){horse.getNavigation().stop();return;}
  if(cart!=null){if(cart.puller()==null&&horse.distanceToSqr(cart)<=CartEntity.REACH*CartEntity.REACH)cart.puller(horse.getUUID());
   if(horse.distanceToSqr(cart)>25||cart.blocked()){horse.getNavigation().stop();if(++waiting>100)horse.getNavigation().moveTo(cart,.8);if(waiting>400)waiting=0;return;}waiting=0;}
  if(CaravanDogs.nearGate(horse,to)){horse.getNavigation().stop();Caravans.arrive(l,t,SettlementData.get(l.getServer()).clock().ticks());return;}
  if(CaravanEscorts.waiting(l,t,horse)||CaravanDogs.waiting(l,t,horse)){horse.getNavigation().stop();return;}
  if(--repath>0)return;repath=20;
  for(boolean stairs:new boolean[]{false,true})for(int ahead=24;ahead>=4;ahead-=4)for(int side:new int[]{-1,1,0}){var point=Caravans.position(l,t,Math.min(length,t.getDouble("progress")+ahead));
   var at=Caravans.ground(l,point.offset((int)Math.round(-(to.getZ()-from.getZ())*4*side/length),0,(int)Math.round((to.getX()-from.getX())*4*side/length)));
   var path=horse.getNavigation().createPath(at,0);if(path==null||!path.canReach())continue;
   boolean safe=true;double height=horse.getY();if(cart!=null)for(int i=0;i<path.getNodeCount();i++){
    var below=path.getNode(i).asBlockPos().below();var state=l.getBlockState(below);var shape=state.getCollisionShape(l,below);
    if(shape.isEmpty()||!l.getFluidState(below).isEmpty()){safe=false;break;}double next=below.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
    if(height-next>1.25||next-height>cart.maxUpStep()+.01&&(!stairs||!(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock))){safe=false;break;}height=next;
   }
   if(safe){horse.getNavigation().moveTo(path,.85);return;}
  }horse.getNavigation().stop();
 }
 @Override public void stop(){horse.getNavigation().stop();}
}
