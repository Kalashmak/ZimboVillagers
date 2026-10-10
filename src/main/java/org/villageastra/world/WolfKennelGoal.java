package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Wolf;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-138 IV (spec §9.1): a village's wolf lives at its kennel. By day it keeps to the yard — the lane and the walks between the pens,
 *  never inside a pen —, trotting to a new spot now and then; at night it lies on its straw in the kennel (the bed of its place in the
 *  registry: three bales along the back, the fourth on the floor by the bin). The wolf stays its player's pet: while its owner is within
 *  {@link #OWNER_NEAR} blocks, or it was ordered to sit, this goal lets it be, so it follows or sits as any tame wolf; with the owner away it
 *  stays home instead of being pulled to him (this goal holds the move flag the vanilla follow-owner goal needs). */
public final class WolfKennelGoal extends Goal {
 public static final int OWNER_NEAR=16;
 /** Where the wolves lie in the kennel's own cells (x,y,z): on the three bales at the back, then on the planks by the bin. */
 public static final int[][] BEDS={{1,2,5},{2,2,5},{3,2,5},{2,1,3}};
 private final Wolf wolf;private BlockPos spot,shut;private int wait,penned,lastSentRepath;
 /** AD-144 (dj-merge-probes-kennel): the byre's barn door has shut board leaves beside it now, so a wolf in the byre or on the walks behind
  *  it goes round the byre to the kennel's street front — a way longer than a vanilla wolf's search (16 blocks walked, 256 nodes): it gave up
  *  inside the byre by its west wall. The way home is searched with a wider reach ({@link #homeward}); nothing else of the wolf changes. */
 private static final UUID HOME_RANGE=UUID.fromString("5b0d2f64-7c1e-4b8a-9a55-3f1c2e7d8a44");
 public WolfKennelGoal(Wolf wolf){this.wolf=wolf;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));wolf.getNavigation().setMaxVisitedNodesMultiplier(4F);}
 /** A path to the kennel searched 48 blocks deep: the range is lent only while the path is made, so the wolf's targeting keeps its own. */
 private void homeward(BlockPos to){var range=wolf.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
  if(range!=null&&range.getModifier(HOME_RANGE)==null)range.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HOME_RANGE,"Astra kennel way home",32,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
  try{wolf.getNavigation().moveTo(to.getX()+.5,to.getY(),to.getZ()+.5,1.0);}finally{if(range!=null)range.removeModifier(HOME_RANGE);}}
 private Settlement.Building yardOr(){var e=entry();var k=e==null?null:VillageWolves.kennel(e);if(k==null)return null;var id=e.settlement().annexParent(k.id());return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElse(null);}
 private SettlementData.Entry entry(){var v=VillageWolves.village(wolf);return v==null||!(wolf.level() instanceof ServerLevel l)?null:SettlementData.get(l.getServer()).entry(v);}
 private boolean ownerNear(){var o=wolf.getOwner();return o!=null&&o.level()==wolf.level()&&o.distanceToSqr(wolf)<=OWNER_NEAR*OWNER_NEAR;}
 @Override public boolean canUse(){
  // AD-139: a dog sent along with a courier's cart goes with it, whoever is near; AD-138 VI: a wolf sent to cull goes about it.
  if(wolf.isAlive()&&(VillageWolves.courier(wolf)!=null||VillageWolves.cullOrder(wolf)!=null||VillageWolves.sentTo(wolf)!=null)&&!wolf.isOrderedToSit())return true;
  // A wolf with something to fight (its owner's attacker, a mob in the yard) fights as any tame wolf; the kennel waits.
  if(!wolf.isAlive()||wolf.isOrderedToSit()||wolf.isLeashed()||wolf.isPassenger()||wolf.getTarget()!=null||ownerNear())return false;
  var e=entry();return e!=null&&VillageWolves.kennel(e)!=null;
 }
 @Override public boolean canContinueToUse(){return canUse();}
 @Override public void stop(){wolf.getNavigation().stop();wolf.setInSittingPose(false);spot=null;}
 /** The bed of this wolf: its place in the registry (a wolf not yet written in takes the last). */
 public static int bed(List<UUID> wolves,UUID wolf){int i=wolves.indexOf(wolf);return i<0?BEDS.length-1:Math.min(i,BEDS.length-1);}
 /** A spot of the yard a wolf may stand on by day: the lane (x7..9) behind the byre or the walks (z7..8, z16..17), never a pen. */
 public static BlockPos daySpot(SettlementData.Entry e,Settlement.Building yard,Random r){
  int pick=r.nextInt(3);int x,z;
  if(pick==0){x=7+r.nextInt(3);z=9+r.nextInt(15);}else if(pick==1){x=r.nextInt(17);z=7+r.nextInt(2);}else{x=r.nextInt(17);z=16+r.nextInt(2);}
  return LivestockPens.at(e,yard,new BlockPos(x,1,z));
 }
 private final Random random=new Random();
 /** The yard's beast out beyond its pen's fence nearest this wolf, within 48 blocks; null when the herd is home. */
 private net.minecraft.world.entity.animal.Animal straggler(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  return l.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,wolf.getBoundingBox().inflate(48),x->x.isAlive()&&!(x instanceof Wolf)
    &&yard.id().equals(x.getPersistentData().hasUUID(LivestockPens.YARD_TAG)?x.getPersistentData().getUUID(LivestockPens.YARD_TAG):null)
    &&LivestockPens.pen(x.getPersistentData().getInt(LivestockPens.PEN_TAG))!=null&&!LivestockPens.fenced(e,yard,LivestockPens.pen(x.getPersistentData().getInt(LivestockPens.PEN_TAG)),x.blockPosition()))
   .stream().min(Comparator.comparingDouble(x->x.distanceToSqr(wolf))).orElse(null);
 }
 @Override public void tick(){
  // AD-141/AD-165: a delivery uses the same extended path search as the kennel return; vanilla range can stall before a distant clinic.
  var sent=VillageWolves.sentTo(wolf);
  if(sent!=null){wolf.setInSittingPose(false);
   // Ordinary goal ticks may always fall on odd body ticks; elapsed time preserves the ten-body-tick replan interval.
   if(wolf.distanceToSqr(sent.getX()+.5,sent.getY(),sent.getZ()+.5)>1.5*1.5){
    if(wolf.tickCount-lastSentRepath>=10){lastSentRepath=wolf.tickCount;homeward(sent);}
   }else wolf.getNavigation().stop();return;}
  // AD-139: beside its courier, a trot behind the cart; far behind (the courier went through a door or round a corner) it is by his side.
  var with=VillageWolves.courier(wolf);
  if(with!=null){var c=((ServerLevel)wolf.level()).getEntity(with);
   if(c==null||!c.isAlive()){if(--wait<=0){wait=20;var e0=entry();if(e0!=null)VillageDogs.release((ServerLevel)wolf.level(),e0,wolf.getUUID());}return;}
   wolf.setInSittingPose(false);wolf.getLookControl().setLookAt(c);double d=wolf.distanceToSqr(c);
   if(d>24*24){wolf.getNavigation().stop();wolf.moveTo(c.getX(),c.getY(),c.getZ(),wolf.getYRot(),0);}
   else if(d>3*3){if(wolf.tickCount%10==0)wolf.getNavigation().moveTo(c,1.2);}else wolf.getNavigation().stop();
   return;}
  // AD-138 VI: the cull — to the lane before the beast's pen, in through its gate (the yard's machinery opens it), the kill, and out again
  // (probe autoyard-probe: from outside the fence the wolf never came within reach of a beast at the far side of the pen); the gate is shut
  // behind it once it is out. A beast gone, the order ends.
  var order=VillageWolves.cullOrder(wolf);var l0=(ServerLevel)wolf.level();
  if(order!=null){
   if(!(l0.getEntity(order) instanceof net.minecraft.world.entity.animal.Animal beast)||!beast.isAlive()){VillageWolves.culled(wolf);return;}
   var h=LivestockGrazing.home(l0,beast);
   wolf.setInSittingPose(false);wolf.getLookControl().setLookAt(beast);
   if(h!=null&&!LivestockPens.fenced(h.entry(),h.yard(),h.pen(),wolf.blockPosition())){
    var gate=LivestockPens.at(h.entry(),h.yard(),h.pen().gate());var out=LivestockPens.at(h.entry(),h.yard(),h.pen().outside());
    if(!Gates.isOpen(l0.getBlockState(gate))){if(Gates.reaches(wolf,gate)){Gates.open(l0,gate,wolf);shut=gate;}else if(wolf.tickCount%10==0)wolf.getNavigation().moveTo(out.getX()+.5,out.getY(),out.getZ()+.5,1.2);return;}
    shut=gate;}
   if(wolf.distanceToSqr(beast)<=2.2*2.2){beast.hurt(l0.damageSources().mobAttack(wolf),1000F);wolf.swing(net.minecraft.world.InteractionHand.MAIN_HAND);VillageWolves.culled(wolf);}
   else if(wolf.tickCount%10==0)wolf.getNavigation().moveTo(beast.getX(),beast.getY(),beast.getZ(),1.2);
   return;}
  // Out of the pen after a cull: the gate it came in by is shut behind it.
  if(shut!=null&&wolf.tickCount%10==0){var ye=entry();var yy=yardOr();
   if(ye==null||yy==null||!Gates.isOpen(l0.getBlockState(shut)))shut=null;
   else if(wolf.distanceToSqr(shut.getX()+.5,shut.getY(),shut.getZ()+.5)>2.5&&LivestockPens.penAt(ye,yy,wolf.blockPosition())==null&&!wolf.blockPosition().equals(shut)){Gates.close(l0,shut,wolf);shut=null;}}
  if(--wait>0)return;wait=20;
  var e=entry();if(e==null)return;var kennel=VillageWolves.kennel(e);if(kennel==null)return;
  var yardId=e.settlement().annexParent(kennel.id());var yard=e.settlement().buildings().stream().filter(b->b.id().equals(yardId)).findFirst().orElse(null);if(yard==null)return;
  var l=(ServerLevel)wolf.level();
  // Slipped into a pen while the keeper had its gate open (probe wolves-probe3): out through the gate when it is open; ten seconds at a
  // shut gate and the wolf is let out onto the lane before it, as the keeper would.
  var pen=LivestockPens.penAt(e,yard,wolf.blockPosition());
  if(pen!=null){var gate=LivestockPens.at(e,yard,pen.gate());var in=LivestockPens.at(e,yard,pen.inside());var out=LivestockPens.at(e,yard,pen.outside());
   wolf.setInSittingPose(false);
   if(Gates.isOpen(l.getBlockState(gate))){penned=0;wolf.getNavigation().moveTo(out.getX()+.5,out.getY(),out.getZ()+.5,1.0);}
   else if(wolf.distanceToSqr(in.getX()+.5,in.getY(),in.getZ()+.5)>2.25)wolf.getNavigation().moveTo(in.getX()+.5,in.getY(),in.getZ()+.5,1.0);
   else if(++penned>=10){penned=0;wolf.getNavigation().stop();wolf.moveTo(out.getX()+.5,out.getY(),out.getZ()+.5,wolf.getYRot(),0);}
   return;}
  penned=0;
  if(l.isNight()){
   // AD-138 V: at dusk the wolves run behind the beasts still out on the pasture, two blocks back on the far side from their gate,
   // so they go home the sooner (each walks home itself — GrazeGoal); then to bed.
   if(LivestockGrazing.allowed(l,e,yard)){var straggler=straggler(l,e,yard);
    if(straggler!=null){var h=LivestockGrazing.home(l,straggler);var to=LivestockPens.at(e,yard,h.pen().outside());
     double dx=straggler.getX()-(to.getX()+.5),dz=straggler.getZ()-(to.getZ()+.5),n=Math.max(.001,Math.sqrt(dx*dx+dz*dz));
     wolf.setInSittingPose(false);wolf.getNavigation().moveTo(straggler.getX()+dx/n*2,straggler.getY(),straggler.getZ()+dz/n*2,1.2);return;}}
   var b=BEDS[bed(VillageWolves.wolves(l,e),wolf.getUUID())];var bed=BuildingPlacement.at(e,kennel,b[0],b[1],b[2]);
   // Round by the open front first (probe wolves-probe: from the yard behind, the wolves stood at the kennel's back wall, a step from
   // their straw): until it is under the roof, the wolf goes to the entrance on the street side, then to its bed.
   var local=BuildingPlacement.local(e,kennel,wolf.blockPosition());boolean under=local.getX()>=1&&local.getX()<=3&&local.getZ()>=0&&local.getZ()<=5;
   var go=under?bed:BuildingPlacement.at(e,kennel,2,1,0);
   if(wolf.distanceToSqr(bed.getX()+.5,bed.getY(),bed.getZ()+.5)>1.2){wolf.setInSittingPose(false);homeward(go);}
   // In its bed: today's meal from the bin first (the keeper's porters fill it), then it lies down.
   else{wolf.getNavigation().stop();VillageWolves.eat(l,e,wolf);wolf.setInSittingPose(true);}
   return;
  }
  wolf.setInSittingPose(false);
  // By day: a new spot of the yard when it got to the last (or every half minute), trotting.
  if(spot==null||wolf.distanceToSqr(spot.getX()+.5,spot.getY(),spot.getZ()+.5)<2.5||random.nextInt(30)==0)spot=daySpot(e,yard,random);
  if(wolf.getNavigation().isDone())wolf.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,.9);
 }
}
