package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-065: an army in the field fights the defenders of the settlement it marches on. Its soldiers go for the guards, archers and soldiers of the target;
 *  those defend against them as against any raider. Blows are real, losses are for good, and on peaceful difficulty nobody fights (WAR-005). */
public final class Battle {
 /** How far a soldier looks for a defender, the reach of a blow and the pause between blows. */
 public static final int SIGHT=16,REACH_SQ=9,COOLDOWN=20;
 private Battle(){}
 public static boolean allowed(ServerLevel l){return Raids.allowed(l.getDifficulty());}
 /** States in which an army is in the field. */
 static boolean inField(CompoundTag army){var s=army.getString("state");return !s.equals(Sieges.CLOSED)&&!s.equals(Sieges.WITHDRAWN)&&!s.equals(Sieges.GATHERING);}
 /** The army a resident marches in, or null. */
 public static CompoundTag armyOf(ServerLevel l,UUID resident){
  for(var t:Sieges.armies(l.getServer())){if(!inField(t))continue;
   for(var raw:t.getList("soldiers",Tag.TAG_INT_ARRAY))if(NbtUtils.loadUUID(raw).equals(resident))return t;}
  return null;
 }
 /** A resident who defends a settlement: its guards, archers and soldiers at home. */
 public static boolean defender(SettlementData.Entry e,UUID resident){
  var r=e.settlement().resident(resident);if(r==null||!r.alive())return false;
  return r.profession()==Profession.GUARD||r.profession()==Profession.ARCHER_GUARD||r.profession()==Profession.SOLDIER;
 }
 /** The nearest defender of the army's target for this soldier to fight. */
 public static ResidentEntity foe(ServerLevel l,CompoundTag army,ResidentEntity soldier){
  if(!allowed(l)||army==null||!inField(army))return null;
  var target=SettlementData.get(l.getServer()).entry(army.getUUID("target"));if(target==null)return null;
  ResidentEntity best=null;double bestDistance=SIGHT*SIGHT;
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,soldier.getBoundingBox().inflate(SIGHT),n->n.isAlive()&&target.settlement().id().equals(n.settlementId()))){
   if(!defender(target,npc.getUUID())||armyOf(l,npc.getUUID())!=null)continue;
   double d=npc.distanceToSqr(soldier);if(d<bestDistance){bestDistance=d;best=npc;}}
  return best;
 }
 /** Soldiers of armies marching on this settlement, near one of its defenders. */
 public static ResidentEntity invader(ServerLevel l,SettlementData.Entry home,ResidentEntity defender,int sight){
  if(!allowed(l))return null;
  ResidentEntity best=null;double bestDistance=(double)sight*sight;
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,defender.getBoundingBox().inflate(sight),n->n.isAlive()&&n!=defender)){
   var army=armyOf(l,npc.getUUID());if(army==null||!army.getUUID("target").equals(home.settlement().id()))continue;
   double d=npc.distanceToSqr(defender);if(d<bestDistance){bestDistance=d;best=npc;}}
  return best;
 }
 /** One blow: bare hands do one heart's worth, a weapon adds its own damage. */
 public static boolean strike(ServerLevel l,ResidentEntity attacker,LivingEntity victim,ItemStack weapon){
  float damage=1F;
  for(var modifier:weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))damage+=(float)modifier.getAmount();
  // AD-159 IV: a berserker's axe strikes harder.
  if(weapon.getItem() instanceof net.minecraft.world.item.AxeItem&&attacker.settlementId()!=null){var e=org.villageastra.server.SettlementData.get(l.getServer()).entry(attacker.settlementId());
   if(e!=null&&Army.speciality(l,e,attacker.getUUID())==Army.Speciality.BERSERKER)damage*=Army.BERSERK;}
  attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
  return victim.hurt(l.damageSources().mobAttack(attacker),damage);
 }
 /** Living soldiers of an army. */
 public static int living(ServerLevel l,CompoundTag army){
  var attacker=SettlementData.get(l.getServer()).entry(army.getUUID("attacker"));if(attacker==null)return 0;int n=0;
  for(var raw:army.getList("soldiers",Tag.TAG_INT_ARRAY)){var r=attacker.settlement().resident(NbtUtils.loadUUID(raw));if(r!=null&&r.alive())n++;}
  return n;
 }
}
