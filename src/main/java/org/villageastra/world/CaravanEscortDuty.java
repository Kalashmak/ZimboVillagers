package org.villageastra.world;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
/** The materialized guard follows the actual driver and fights nearby monsters without chasing away from the convoy. */
final class CaravanEscortDuty {
 private CaravanEscortDuty(){}
 static boolean tick(ResidentEntity npc,CompoundTag t){
  if(!CaravanEscorts.escort(t))return false;
  var l=(ServerLevel)npc.level();CaravanEscorts.sync(l,t);
  var p=Caravans.contract(l.getServer(),t.getUUID("leaderTrip"));
  var driver=p==null?null:l.getEntity(p.getUUID("caravaneer"));
  var anchor=driver!=null&&driver.isAlive()?driver:npc;
  var gear=GuardGoal.inspect(l,npc.getUUID());var weapon=ItemStack.of(gear.getCompound("weapon"));
  Army.wear(npc,gear);npc.displayWorkItem(weapon);
  LivingEntity foe=l.getEntitiesOfClass(LivingEntity.class,anchor.getBoundingBox().inflate(10),
   x->x instanceof Enemy&&x.isAlive()&&npc.distanceToSqr(x)<=16*16&&npc.hasLineOfSight(x)).stream()
   .min(java.util.Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
  if(foe!=null&&!weapon.isEmpty()){
   npc.getLookControl().setLookAt(foe,30,30);
   boolean bow=weapon.getItem() instanceof BowItem&&gear.getInt("arrows")>0;
   if(bow||npc.distanceToSqr(foe)<=9){
    npc.getNavigation().stop();
    if(npc.tickCount%(bow?30:20)==0){
     boolean used;
     if(bow){var arrow=new Arrow(l,npc);GuardGoal.aim(arrow,foe.getX()-npc.getX(),foe.getY(.33)-arrow.getY(),foe.getZ()-npc.getZ());
      arrow.pickup=net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;used=l.addFreshEntity(arrow);if(used)gear.putInt("arrows",gear.getInt("arrows")-1);
     }else used=Battle.strike(l,npc,foe,weapon);
     if(used){weapon.setDamageValue(weapon.getDamageValue()+1);if(weapon.getDamageValue()>=weapon.getMaxDamage())gear.remove("weapon");else gear.put("weapon",weapon.save(new CompoundTag()));GuardGoal.write(l,npc.getUUID(),gear);}
    }
   }else if(npc.tickCount%10==0)npc.getNavigation().moveTo(foe,.85);
   npc.workStatus("guard_fighting");return true;
  }
  if(!t.getString("state").equals(Caravans.TRANSIT))return false;
  if(driver!=null&&driver.isAlive()){
   double distance=npc.distanceToSqr(driver);
   if(distance>9){if(npc.tickCount%10==0)npc.getNavigation().moveTo(driver,.85);}else npc.getNavigation().stop();
   // The real position remains authoritative if the leader dies or its chunk unloads.
   var a=Caravans.from(t);var b=Caravans.to(t);double along=((npc.getX()-a.getX())*(b.getX()-a.getX())+(npc.getZ()-a.getZ())*(b.getZ()-a.getZ()))/Math.max(1,Caravans.length(t));
   t.putDouble("progress",Math.max(0,Math.min(Caravans.length(t),along)));
  }else if(p!=null&&npc.tickCount%20==0){var at=Caravans.position(l,p,p.getDouble("progress"));npc.getNavigation().moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,.85);}
  if(npc.tickCount%20==0){CaravanEscorts.remember(t,npc);Caravans.update(l.getServer(),t);}
  npc.workStatus("caravan_travelling");return true;
 }
}
