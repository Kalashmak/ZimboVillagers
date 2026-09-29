package org.villageastra.world;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import org.villageastra.server.SettlementData;
/** A harnessed caravan dog walks; vanilla pet teleportation must never move a loaded cart. */
public final class CaravanDogGoal extends Goal {
 private final Wolf dog;private int repath,waiting;
 public CaravanDogGoal(Wolf dog){this.dog=dog;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
 private CompoundTag trip(){return dog.level() instanceof ServerLevel l&&!CaravanDogs.stale(dog)?CaravanDogs.record(l.getServer(),dog.getUUID()):null;}
 @Override public boolean canUse(){var t=trip();return t!=null&&t.getBoolean("materialized")&&!t.getString("state").equals(Caravans.CLOSED);}
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var t=trip();if(t==null)return;var l=(ServerLevel)dog.level();dog.setTarget(null);dog.setInSittingPose(false);
  var from=Caravans.from(t);var to=Caravans.to(t);double length=Math.max(1,Caravans.length(t));
  double along=((dog.getX()-from.getX())*(to.getX()-from.getX())+(dog.getZ()-from.getZ())*(to.getZ()-from.getZ()))/length;
  t.putDouble("progress",Math.max(t.getDouble("progress"),Math.max(0,Math.min(length,along))));
  var cart=l.getEntitiesOfClass(CartEntity.class,dog.getBoundingBox().inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())).stream().findFirst().orElse(null);
  if(cart!=null){if(cart.puller()==null&&dog.distanceToSqr(cart)<=CartEntity.REACH*CartEntity.REACH)cart.puller(dog.getUUID());
   if(dog.distanceToSqr(cart)>25||cart.blocked()){dog.getNavigation().stop();if(++waiting>100)dog.getNavigation().moveTo(cart,1);if(waiting>400)waiting=0;return;}waiting=0;}
  if(CaravanDogs.nearGate(dog,to)){dog.getNavigation().stop();Caravans.arrive(l,t,SettlementData.get(l.getServer()).clock().ticks());return;}
  if(--repath>0)return;repath=10;
  var parent=Caravans.contract(l.getServer(),t.getUUID("leaderTrip"));
  if(t.getString("state").equals(Caravans.TRANSIT)||parent!=null&&parent.getString("state").equals(Caravans.RETURNING)&&l.getEntity(parent.getUUID("caravaneer"))!=null){
   var driver=parent==null?null:l.getEntity(parent.getUUID("caravaneer"));if(driver==null){dog.getNavigation().stop();return;}
   if(CaravanDogs.nearGate(driver,to))drive(l,cart,to.getX()+.5,to.getY(),to.getZ()+.5);
   else{double dx=(to.getX()-from.getX())/length,dz=(to.getZ()-from.getZ())/length;
    // The opposite verge is preferable to jumping onto a house foundation with a wheeled cart.
    for(int side:new int[]{1,-1,0}){double x=driver.getX()-dz*3*side-dx*3,z=driver.getZ()+dx*3*side-dz*3;
     if(drive(l,cart,x,driver.getY(),z))break;}}
  }else{
   for(int ahead=24;ahead>=4;ahead-=4){var next=Caravans.position(l,t,Math.min(length,t.getDouble("progress")+ahead));if(drive(l,cart,next.getX()+.5,next.getY(),next.getZ()+.5))break;}
  }
 }
 private boolean drive(ServerLevel l,CartEntity cart,double x,double y,double z){
  var path=dog.getNavigation().createPath(net.minecraft.core.BlockPos.containing(x,y,z),0);if(path==null||!path.canReach())return false;
  if(cart!=null){double height=dog.getY();for(int i=0;i<path.getNodeCount();i++){
   var below=path.getNode(i).asBlockPos().below();var state=l.getBlockState(below);var shape=state.getCollisionShape(l,below);
   if(shape.isEmpty()||!l.getFluidState(below).isEmpty())return false;
   double next=below.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
   if(next-height>cart.maxUpStep()+.01&&!(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock))return false;height=next;
  }}
  if(dog.distanceToSqr(x,y,z)<=2.25)dog.getNavigation().stop();else dog.getNavigation().moveTo(path,1.1);return true;
 }
 @Override public void stop(){dog.getNavigation().stop();}
}
