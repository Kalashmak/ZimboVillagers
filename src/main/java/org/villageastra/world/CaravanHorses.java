package org.villageastra.world;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
/** AD-160 VI: a horse is the contract's carrier, never a resident or a free replacement body. */
public final class CaravanHorses {
 private CaravanHorses(){}
 public static boolean isHorse(CompoundTag t){return t.getBoolean("horseTrip");}
 public static CompoundTag record(MinecraftServer s,UUID id){return Caravans.contracts(s).stream().filter(t->isHorse(t)&&t.getUUID("caravaneer").equals(id)&&(Caravans.open(t)||t.getBoolean("needsEntity"))).findFirst().orElse(null);}
 public static boolean reserved(MinecraftServer s,UUID id){return record(s,id)!=null;}
 public static void remember(CompoundTag t,Horse h){var tag=new CompoundTag();h.saveWithoutId(tag);t.put("horse",tag);}
 public static boolean prepare(ServerLevel l,CompoundTag t){
  if(!isHorse(t))return true;
  // A persisted acquisition owns both animal snapshot and cart; resume journalled loading after a crash.
  if(t.contains("horse")&&t.contains("cart"))return true;var h=l.getEntity(t.getUUID("caravaneer"));var source=org.villageastra.server.SettlementData.get(l.getServer()).entry(t.getUUID("source"));
  if(!(h instanceof Horse horse)||source==null||TradeLadder.level(l,source)<6||!VillageHorses.ready(horse)||!source.settlement().id().equals(VillageHorses.village(horse))||(!t.contains("cart")&&Caravans.freeCart(l,source)==null)){
   t.putString("state",Caravans.CANCELLED);Caravans.update(l.getServer(),t);return false;}
  remember(t,horse);Caravans.update(l.getServer(),t);return true;
 }
 public static void depart(ServerLevel l,CompoundTag t){if(isHorse(t)&&l.getEntity(t.getUUID("caravaneer")) instanceof Horse h){remember(t,h);h.discard();}}
 public static boolean stale(Horse h){return h.level() instanceof ServerLevel l&&h.getPersistentData().getLong(Caravans.EPOCH)<Caravans.epoch(l.getServer(),h.getUUID());}
 public static Horse materialize(ServerLevel l,CompoundTag t,BlockPos at){
  if(!t.contains("horse"))return null;var id=t.getUUID("caravaneer");
  if(l.getEntity(id) instanceof Horse old){if(!stale(old)&&t.getBoolean("materialized"))return old;old.discard();}
  var h=EntityType.HORSE.create(l);if(h==null)return null;h.load(t.getCompound("horse"));h.setUUID(id);
  h.getPersistentData().putLong(Caravans.EPOCH,Caravans.bump(l.getServer(),id));at=Caravans.ground(l,at);h.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  if(!l.addFreshEntity(h))return null;t.putBoolean("materialized",true);
  if(t.contains("cart")&&Caravans.cartBody(l,t,h,at)==null){unload(l,t,h);return null;}
  Caravans.update(l.getServer(),t);return h;
 }
 public static void unload(Horse h){if(!(h.level() instanceof ServerLevel l)||stale(h))return;var t=record(l.getServer(),h.getUUID());if(t!=null&&t.getBoolean("materialized"))unload(l,t,h);}
 private static void unload(ServerLevel l,CompoundTag t,Horse h){
  remember(t,h);t.putBoolean("materialized",false);Caravans.bump(l.getServer(),h.getUUID());t.putLong("cartEpoch",t.getLong("cartEpoch")+1);
  for(var c:l.getEntitiesOfClass(CartEntity.class,h.getBoundingBox().inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())))c.discard();
  h.discard();Caravans.update(l.getServer(),t);
 }
 public static void died(Horse h,long now){if(!(h.level() instanceof ServerLevel l)||stale(h))return;var t=record(l.getServer(),h.getUUID());if(t==null)return;Caravans.died(h,t,now);Caravans.bump(l.getServer(),h.getUUID());t.remove("horse");Caravans.update(l.getServer(),t);}
 public static boolean tick(ServerLevel l,CompoundTag t,long now){
  if(!isHorse(t))return false;String state=t.getString("state");
  if(state.equals(Caravans.CLOSED)){if(t.getBoolean("needsEntity")){var at=BlockPos.of(t.getLong("from"));if(visible(l,at)&&materialize(l,t,at)!=null){t.putBoolean("needsEntity",false);Caravans.update(l.getServer(),t);}}return true;}
  if(!state.equals(Caravans.TRANSIT)&&!state.equals(Caravans.RETURNING))return false;
  if(t.getBoolean("materialized")){if(l.getEntity(t.getUUID("caravaneer")) instanceof Horse h){remember(t,h);Caravans.update(l.getServer(),t);return true;}t.putBoolean("materialized",false);Caravans.update(l.getServer(),t);}
  var at=Caravans.position(l,t,t.getDouble("progress"));if(visible(l,at)){materialize(l,t,at);return true;}
  if(CaravanDogs.visibleDog(l,t))return true;
  t.putDouble("progress",Math.min(Caravans.length(t),t.getDouble("progress")+Caravans.SPEED*20*(1+t.getDouble("routeBonus"))));
  if(t.getDouble("progress")>=Caravans.length(t))Caravans.arrive(l,t,now);else Caravans.update(l.getServer(),t);return true;
 }
 private static boolean visible(ServerLevel l,BlockPos at){return l.hasChunkAt(at)&&!l.getPlayers(p->p.blockPosition().distSqr(at)<Caravans.MATERIALIZE_RADIUS*Caravans.MATERIALIZE_RADIUS).isEmpty();}
}
