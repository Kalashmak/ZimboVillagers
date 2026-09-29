package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
/** AD-138 V: a pen beast out to graze — out through the open gate of its pen and from one patch of the village's grass to the next; home
 *  again through the gate to the middle of its pen at dusk or once there is feed (LivestockGrazing). A beast inside its pen with nowhere to
 *  go leaves the goal to its own kind's wandering. */
public final class GrazeGoal extends Goal {
 private final Animal a;private BlockPos spot,last;private int wait,still,balk;private final Random random=new Random();
 public GrazeGoal(Animal a){this.a=a;setFlags(EnumSet.of(Flag.MOVE));}
 private LivestockGrazing.Home home(){return a.level() instanceof ServerLevel l?LivestockGrazing.home(l,a):null;}
 private boolean fenced(LivestockGrazing.Home h){return LivestockPens.fenced(h.entry(),h.yard(),h.pen(),a.blockPosition());}
 private int checked;private boolean wanted;
 @Override public boolean canUse(){
  if(!a.isAlive()||a.isLeashed()||a.isInLove())return false;
  // The yard's state is read once a second, not on every tick of every beast.
  if(a.tickCount<checked)return wanted;checked=a.tickCount+20;
  var h=home();if(h==null)return wanted=false;var l=(ServerLevel)a.level();
  // Out: while its pen grazes. Home: whenever it is beyond its pen's fence and the pen does not graze — and, once home, it keeps to the
  // middle while the gate still stands open (probe graze-probe: home, it wandered out again through the open gate before the keeper
  // could shut it, and the herd never was all in).
  return wanted=LivestockGrazing.out(l,h.entry(),h.yard(),h.pen())||!fenced(h)||Gates.isOpen(l.getBlockState(LivestockPens.at(h.entry(),h.yard(),h.pen().gate())));
 }
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public void stop(){spot=null;a.getNavigation().stop();}
 @Override public void tick(){
  if(--wait>0)return;wait=20;var h=home();if(h==null)return;var l=(ServerLevel)a.level();var e=h.entry();var yard=h.yard();var p=h.pen();
  var gate=LivestockPens.at(e,yard,p.gate());var outside=LivestockPens.at(e,yard,p.outside());var middle=LivestockPens.at(e,yard,new BlockPos(p.x()+3,1,p.z()+3));
  if(!LivestockGrazing.out(l,e,yard,p)&&fenced(h)&&!a.blockPosition().equals(gate)){
   if(a.distanceToSqr(middle.getX()+.5,middle.getY(),middle.getZ()+.5)>4)a.getNavigation().moveTo(middle.getX()+.5,middle.getY(),middle.getZ()+.5,1.0);else a.getNavigation().stop();
   return;}
  if(!LivestockGrazing.out(l,e,yard,p)){
   // Home: to the lane before the gate, then in to the middle (the gate open — the keeper keeps it so while his herd is out).
   // Probe graze-probe4: a path "reached" a block short of the lane cell left a sheep standing two blocks off its gate for good — the
   // path is made exact, "at the gate" is two blocks, and ten seconds without a step there sets it a block on (as the keeper's drive does).
   boolean atGate=a.distanceToSqr(outside.getX()+.5,outside.getY(),outside.getZ()+.5)<4.5||a.blockPosition().equals(gate);
   var goal=atGate&&Gates.isOpen(l.getBlockState(gate))?(a.blockPosition().equals(gate)||a.blockPosition().equals(outside)?middle:outside):outside;
   if(atGate&&!a.blockPosition().equals(outside)&&!a.blockPosition().equals(gate))goal=outside;
   var path=a.getNavigation().createPath(goal,0);if(path!=null)a.getNavigation().moveTo(path,1.0);
   if(a.blockPosition().equals(last)&&a.distanceToSqr(outside.getX()+.5,outside.getY(),outside.getZ()+.5)<16){
    if(++balk>=10){balk=0;var step=a.blockPosition().relative(net.minecraft.core.Direction.getNearest(goal.getX()+.5-a.getX(),0,goal.getZ()+.5-a.getZ()));
     if(l.getBlockState(step).getCollisionShape(l,step).isEmpty()&&l.getBlockState(step.above()).getCollisionShape(l,step.above()).isEmpty())a.moveTo(step.getX()+.5,step.getY(),step.getZ()+.5,a.getYRot(),0);}}
   else balk=0;
   last=a.blockPosition();return;}
  if(fenced(h)){
   // Out of the pen once its gate is open.
   if(Gates.isOpen(l.getBlockState(gate)))a.getNavigation().moveTo(outside.getX()+.5,outside.getY(),outside.getZ()+.5,1.0);
   return;}
  // Grazing: a new patch when it got there or stood a while.
  if(spot==null||a.distanceToSqr(spot.getX()+.5,spot.getY(),spot.getZ()+.5)<2||++still>12){still=0;spot=LivestockGrazing.spot(l,e,yard,a.blockPosition(),random);}
  if(spot!=null&&a.getNavigation().isDone())a.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,.8);
 }
}
