package org.villageastra.world;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-160 IV: guards are ordinary residents travelling on subordinate, cargo-free contracts. */
public final class CaravanEscorts {
 private CaravanEscorts(){}
 public static final String KIND="escort";
 public static final int LIMIT=2;
 public static boolean escort(CompoundTag t){return KIND.equals(t.getString("kind"));}
 public static boolean waiting(ServerLevel l,CompoundTag leader,net.minecraft.world.entity.Entity driver){
  if(!leader.getString("state").equals(Caravans.TRANSIT))return false;
  for(var t:Caravans.contracts(l.getServer()))if(escort(t)&&Caravans.open(t)&&t.getUUID("leaderTrip").equals(leader.getUUID("id"))
   &&l.getEntity(t.getUUID("caravaneer")) instanceof ResidentEntity guard&&guard.isAlive()&&driver.distanceToSqr(guard)>64)return true;
  return false;
 }
 /** Reservations include gathering armies and a return whose body has not appeared at home yet. */
 public static boolean reserved(MinecraftServer s,UUID id){
  for(var t:Caravans.contracts(s))if(t.getUUID("caravaneer").equals(id)&&(Caravans.open(t)||t.getBoolean("needsEntity")))return true;
  for(var a:Sieges.armies(s))if(!Set.of(Sieges.CLOSED,Sieges.WITHDRAWN).contains(a.getString("state")))
   for(var raw:a.getList("soldiers",Tag.TAG_INT_ARRAY))if(NbtUtils.loadUUID(raw).equals(id))return true;
  return false;
 }
 public static boolean free(ServerLevel l,SettlementData.Entry e,UUID id){
  var r=e.settlement().resident(id);var body=l.getEntity(id);
  return r!=null&&r.alive()&&Population.mayWork(r)&&!reserved(l.getServer(),id)
   &&body instanceof ResidentEntity npc&&npc.isAlive()&&npc.getHealth()>=npc.getMaxHealth()*.75F
   &&npc.escortPlayer()==null&&!npc.getPersistentData().hasUUID(Camps.QUEST_TAG)&&CargoCustody.mayStartWork(npc)
   &&npc.blockPosition().distSqr(e.center())<=96*96;
 }
 /** Reserve at most two armed soldiers. Re-entering dispatch never recruits a second group. */
 public static void recruit(ServerLevel l,CompoundTag leader,long now){
  if(!leader.getString("kind").equals("trade")||leader.getBoolean("escortChecked"))return;
  var s=l.getServer();var e=SettlementData.get(s).entry(leader.getUUID("source"));
  leader.putBoolean("escortChecked",true);Caravans.update(s,leader);
  if(e==null||TradeLadder.level(l,e)<4)return;
  int n=0;
  for(var r:Sieges.soldiers(e)){
   if(n>=LIMIT)break;if(!free(l,e,r.id()))continue;
   var gear=GuardGoal.equip(l,e,e.settlement().workplace(r.id()),r.id(),false);var weapon=ItemStack.of(gear.getCompound("weapon"));
   if(weapon.isEmpty()||weapon.getItem() instanceof net.minecraft.world.item.BowItem&&gear.getInt("arrows")<=0)continue;
   var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(leader.getUUID("id"),"escort/"+r.id()));
   t.putString("kind",KIND);t.putUUID("leaderTrip",leader.getUUID("id"));t.putUUID("caravaneer",r.id());
   for(var key:List.of("source","destination"))t.putUUID(key,leader.getUUID(key));
   t.putString("dimension",leader.getString("dimension"));t.putLong("from",leader.getLong("from"));t.putLong("to",leader.getLong("to"));
   t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putString("state",Caravans.SECURED);
   Caravans.update(s,t);Caravans.dispatch(l,t,now);n++;
  }
 }
 public static void remember(CompoundTag t,ResidentEntity npc){if(escort(t)&&npc!=null)t.putFloat("escortHealth",npc.getHealth());}
 public static void restore(CompoundTag t,ResidentEntity npc){if(escort(t)){
  if(t.contains("escortHealth"))npc.setHealth(Math.max(1,Math.min(npc.getMaxHealth(),t.getFloat("escortHealth"))));
  Army.wear(npc,GuardGoal.inspect((ServerLevel)npc.level(),npc.getUUID()));
 }}
 /** A missing/dead leader sends the guard home from its current route point, not from the far gate. */
 public static void sync(ServerLevel l,CompoundTag t){
  if(!escort(t)||!t.getString("state").equals(Caravans.TRANSIT))return;
  var parent=Caravans.contract(l.getServer(),t.getUUID("leaderTrip"));
  if(parent!=null&&parent.getString("state").equals(Caravans.TRANSIT))return;
  var at=Caravans.position(l,t,t.getDouble("progress"));
  if(l.getEntity(t.getUUID("caravaneer")) instanceof ResidentEntity npc)at=npc.blockPosition();
  t.putLong("to",at.asLong());t.putBoolean("home",true);t.putDouble("progress",0);t.putString("state",Caravans.RETURNING);Caravans.update(l.getServer(),t);
 }
 /** Background guards remain at the leader's progress; only their return advances independently. */
 public static boolean background(ServerLevel l,CompoundTag t){
  if(!escort(t)||!t.getString("state").equals(Caravans.TRANSIT))return false;
  var p=Caravans.contract(l.getServer(),t.getUUID("leaderTrip"));
  if(p!=null)t.putDouble("progress",p.getDouble("progress"));Caravans.update(l.getServer(),t);return true;
 }
 public static boolean arrive(ServerLevel l,CompoundTag t){
  if(!escort(t))return false;
  if(t.getString("state").equals(Caravans.RETURNING)){
   t.putString("state",Caravans.CLOSED);t.putBoolean("needsEntity",!t.getBoolean("materialized"));Caravans.update(l.getServer(),t);
  }
  return true;
 }
}
