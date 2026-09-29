package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-157 (owner's Defence ladder VI): ballistas on the towers. A wall tower of a village of Defence VI with an archer posted on it looses
 *  a heavy bolt every COOLDOWN ticks from its fighting platform at the nearest hostile mob within REACH it can see: a real arrow of
 *  DAMAGE base damage (times its speed) that pierces PIERCE mobs and throws them back, aimed as a tower archer aims (GuardGoal.aim).
 *  Called by Walls.tick; there is no ballista model on the tower yet. */
public final class Ballistas {
 private Ballistas(){}
 public static final int LEVEL=6,COOLDOWN=60,REACH=64,PIERCE=3;public static final double DAMAGE=4;
 private static final Map<UUID,Long> SHOT=new HashMap<>();
 /** A firing opening beside the archer, below the roof and outside the central lantern's collision shape. */
 public static Vec3 mount(SettlementData.Entry e,Settlement.Building tower){return Vec3.atCenterOf(BuildingPlacement.at(e,tower,3,8,1)).add(0,.9,0);}
 /** Bolts loosed this call. */
 public static int tick(ServerLevel l,SettlementData.Entry e){
  if(Walls.defence(l,e)<LEVEL)return 0;int fired=0;long now=l.getGameTime();
  for(var b:e.settlement().buildings()){if(!b.type().equals(Walls.TOWER)||Walls.archers(e.settlement(),b)==0)continue;
   var last=SHOT.get(b.id());if(last!=null&&now-last<COOLDOWN)continue;
   var from=mount(e,b);var target=target(l,from);if(target==null)continue;
   fire(l,from,target);SHOT.put(b.id(),now);fired++;}
  return fired;
 }
 /** The nearest hostile mob within REACH of the mount that it can see (nothing solid between). */
 public static LivingEntity target(ServerLevel l,Vec3 from){
  LivingEntity best=null;double bestD=Double.MAX_VALUE;
  for(var mob:l.getEntitiesOfClass(LivingEntity.class,new AABB(from,from).inflate(REACH),m->m instanceof Enemy&&m.isAlive())){
   double d=mob.distanceToSqr(from);if(d>REACH*REACH||d>=bestD)continue;
   var hit=l.clip(new ClipContext(from,mob.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null));
   if(hit.getType()!=HitResult.Type.MISS&&hit.getLocation().distanceToSqr(from)<mob.getEyePosition().distanceToSqr(from)-1)continue;
   best=mob;bestD=d;}
  return best;
 }
 /** Looses one bolt from a place at a mob. */
 public static Arrow fire(ServerLevel l,Vec3 from,LivingEntity target){
  var bolt=new Arrow(l,from.x,from.y,from.z);bolt.setBaseDamage(DAMAGE);bolt.setPierceLevel((byte)PIERCE);bolt.setKnockback(2);bolt.pickup=AbstractArrow.Pickup.DISALLOWED;
  GuardGoal.aim(bolt,target.getX()-from.x,target.getY(0.33)-from.y,target.getZ()-from.z);l.addFreshEntity(bolt);return bolt;
 }
}
