package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-043: a soldier of a campaign marches to the target, builds its camp and fence sections by hand, blows up working farms and then holds its sector. */
public final class SoldierGoal extends Goal {
 public static final int REACH=4,LABOR=20,MEAL=6000;
 private final ResidentEntity soldier;private final boolean withoutPlayers;private BlockPos task;private int labor,repath,cooldown;private long lastMeal;private ResidentEntity foe;
 public SoldierGoal(ResidentEntity soldier){this(soldier,false);}
 public SoldierGoal(ResidentEntity soldier,boolean withoutPlayers){this.soldier=soldier;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private CompoundTag army(){
  if(!(soldier.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||soldier.escortPlayer()!=null||!soldier.isAlive())return null;
  for(var t:Sieges.armies(l.getServer())){
   var state=t.getString("state");if(state.equals(Sieges.CLOSED)||state.equals(Sieges.WITHDRAWN)||state.equals(Sieges.GATHERING))continue;
   for(var raw:t.getList("soldiers",Tag.TAG_INT_ARRAY))if(NbtUtils.loadUUID(raw).equals(soldier.getUUID()))return t;}
  return null;
 }
 @Override public boolean canUse(){return army()!=null&&CargoCustody.mayStartWork(soldier);}
 @Override public boolean canContinueToUse(){return army()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 /** The next real piece of work for this soldier: an unbuilt camp, an open ring cell or a working farm. */
 private BlockPos next(ServerLevel l,CompoundTag army,SettlementData.Entry target){
  var perimeter=Sieges.perimeter(l,target);
  if(army.getInt("builtCamps")<army.getInt("camps")){
   int step=Math.max(1,perimeter.size()/Math.max(1,army.getInt("camps")));
   for(int i=0;i<perimeter.size();i+=step){var at=perimeter.get(i);if(l.hasChunkAt(at)&&l.getBlockState(at).isAir())return at;}
  }
  BlockPos best=null;double bestDistance=Double.MAX_VALUE;
  for(var at:perimeter){if(!l.hasChunkAt(at)||!l.getBlockState(at).isAir())continue;double d=soldier.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5);if(d<bestDistance){best=at;bestDistance=d;}}
  return best;
 }
 @Override public void tick(){
  var army=army();if(army==null)return;var l=(ServerLevel)soldier.level();var data=SettlementData.get(l.getServer());
  var target=data.entry(army.getUUID("target"));if(target==null)return;long now=data.clock().ticks();
  // AD-065: a defender of the target within sight comes before any work of the campaign.
  if(cooldown>0)cooldown--;
  // AD-159: the soldier wears the armour his record holds.
  if(soldier.tickCount%40==0)Army.wear(soldier,GuardGoal.inspect(l,soldier.getUUID()));
  // AD-159 V: the soldier's dog goes with him and goes for his foe.
  if(soldier.tickCount%40==0||foe!=null&&soldier.tickCount%10==0){var home=soldier.settlementId()==null?null:data.entry(soldier.settlementId());if(home!=null)Army.dog(l,home,soldier,foe);}
  if(soldier.tickCount%10==0||foe!=null&&!foe.isAlive())foe=Battle.foe(l,army,soldier);
  if(foe!=null&&foe.isAlive()){
   var record=GuardGoal.inspect(l,soldier.getUUID());var weapon=record.contains("weapon")?ItemStack.of(record.getCompound("weapon")):ItemStack.EMPTY;
   soldier.displayWorkItem(weapon);soldier.getLookControl().setLookAt(foe,30,30);
   // AD-159 IV: an archer shoots from where he can see the foe within SHOT_REACH while he has arrows.
   if(weapon.getItem() instanceof BowItem&&record.getInt("arrows")>0&&soldier.distanceToSqr(foe)<=Army.SHOT_REACH*Army.SHOT_REACH&&soldier.hasLineOfSight(foe)){
    soldier.getNavigation().stop();if(cooldown<=0){var arrow=new net.minecraft.world.entity.projectile.Arrow(l,soldier);GuardGoal.aim(arrow,foe.getX()-soldier.getX(),foe.getY(0.33)-arrow.getY(),foe.getZ()-soldier.getZ());
     arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;l.addFreshEntity(arrow);record.putInt("arrows",record.getInt("arrows")-1);GuardGoal.write(l,soldier.getUUID(),record);soldier.swing(net.minecraft.world.InteractionHand.MAIN_HAND);cooldown=GuardGoal.RANGED_COOLDOWN;}}
   else if(soldier.distanceToSqr(foe)>Battle.REACH_SQ){if(--repath<=0){repath=10;soldier.getNavigation().moveTo(foe,1.0);}}
   else{soldier.getNavigation().stop();if(cooldown<=0){Battle.strike(l,soldier,foe,weapon);cooldown=Battle.COOLDOWN;}}
   soldier.workStatus("soldier_fighting");return;}
  // Field rations are real: the campaign stock feeds its soldiers.
  if(now-lastMeal>MEAL&&Sieges.supply(army,"bread")>0){var supply=army.getCompound("supply");supply.putInt("bread",supply.getInt("bread")-1);army.put("supply",supply);Sieges.save(l.getServer(),army);lastMeal=now;soldier.workStatus("soldier_eating");return;}
  if(army.getString("state").equals(Sieges.BESIEGING)||army.getString("state").equals(Sieges.SURRENDERED)){
   var post=Sieges.perimeter(l,target);if(post.isEmpty())return;var hold=post.get(Math.floorMod(soldier.getUUID().hashCode(),post.size()));
   if(soldier.distanceToSqr(hold.getX()+.5,hold.getY(),hold.getZ()+.5)>36){if(--repath<=0){repath=40;soldier.getNavigation().moveTo(hold.getX()+.5,hold.getY(),hold.getZ()+.5,.8);}soldier.workStatus("soldier_marching");}
   else{soldier.getNavigation().stop();soldier.workStatus("soldier_holding");}
   return;}
  var farms=Sieges.workingFarms(l,target);
  if(army.getInt("placed")>=army.getInt("fences")&&!farms.isEmpty()){
   // AD-104: the demolition party goes to the field's first aim, a plot beside its water, not into the water.
   var farm=farms.get(0);var at=Sieges.aims(l,target,farm).get(0);
   if(soldier.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>64){if(--repath<=0){repath=40;soldier.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.9);}soldier.workStatus("soldier_marching");return;}
   soldier.getNavigation().stop();if(++labor<LABOR)return;labor=0;
   if(Sieges.blowUpFarm(l,army,target,farm))soldier.workStatus("soldier_demolition");else soldier.workStatus("soldier_missing_charges");
   return;}
  if(task==null||!l.getBlockState(task).isAir())task=next(l,army,target);
  if(task==null){soldier.getNavigation().stop();soldier.workStatus("soldier_holding");return;}
  if(soldier.distanceToSqr(task.getX()+.5,task.getY(),task.getZ()+.5)>REACH*REACH){
   if(--repath<=0){repath=40;soldier.getNavigation().moveTo(task.getX()+.5,task.getY(),task.getZ()+.5,.85);}
   soldier.workStatus("soldier_marching");return;}
  soldier.getNavigation().stop();soldier.displayWorkItem(new ItemStack(army.getInt("builtCamps")<army.getInt("camps")?Items.CAMPFIRE:Items.OAK_FENCE));
  if(++labor<LABOR)return;labor=0;
  boolean done=army.getInt("builtCamps")<army.getInt("camps")?Sieges.placeCamp(l,army,soldier,task):Sieges.placeSection(l,army,soldier,task);
  soldier.workStatus(done?"soldier_building":"soldier_missing_materials");task=null;
 }
 @Override public void stop(){if(soldier.level() instanceof ServerLevel l&&soldier.settlementId()!=null){var home=SettlementData.get(l.getServer()).entry(soldier.settlementId());if(home!=null)Army.release(l,home,soldier.getUUID());}
  soldier.getNavigation().stop();soldier.displayWorkItem(ItemStack.EMPTY);task=null;labor=0;}
}
