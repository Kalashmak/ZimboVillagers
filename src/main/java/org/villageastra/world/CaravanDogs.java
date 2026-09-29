package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-160 V: the second cart has its own cargo journal, pulled by one real kennel wolf.
 * Its subordinate contract uses the normal delivery, payment, roadside recovery and identity epochs. */
public final class CaravanDogs {
 private CaravanDogs(){}
 public static final String KIND="dog_cart";
 public static boolean isDog(CompoundTag t){return KIND.equals(t.getString("kind"));}
 public static boolean reserved(MinecraftServer s,UUID dog){return record(s,dog)!=null;}
 public static CompoundTag record(MinecraftServer s,UUID dog){return Caravans.contracts(s).stream().filter(t->isDog(t)&&t.getUUID("caravaneer").equals(dog)&&(!t.getString("state").equals(Caravans.CLOSED)&&!t.getString("state").equals(Caravans.CANCELLED)||t.getBoolean("needsEntity"))).findFirst().orElse(null);}
 public static CompoundTag child(MinecraftServer s,CompoundTag parent){return parent.hasUUID("dogTrip")?Caravans.contract(s,parent.getUUID("dogTrip")):null;}
 /** No dog, warehouse cart or cart already travelling can be invented by a level upgrade. */
 public static List<CartEntity> carts(ServerLevel l,SettlementData.Entry e){
  var yard=Caravans.yard(e);if(yard==null)return List.of();var at=BuildingPlacement.origin(e,yard);
  return l.getEntitiesOfClass(CartEntity.class,new AABB(at).inflate(16),c->c.isAlive()&&c.home()==null&&!c.travelling()&&c.isEmpty()&&c.puller()==null&&e.settlement().id().equals(c.settlement()));
 }
 public static int extraCapacity(ServerLevel l,SettlementData.Entry e){return TradeLadder.level(l,e)>=5&&carts(l,e).size()>=2&&VillageWolves.freeWolf(l,e)!=null?Caravans.carry(l,e):0;}
 public static void recruit(ServerLevel l,CompoundTag parent,long now){
  if(!parent.getString("kind").equals("trade")||parent.getBoolean("dogChecked"))return;
  parent.putBoolean("dogChecked",true);var s=l.getServer();Caravans.update(s,parent);
  var e=SettlementData.get(s).entry(parent.getUUID("source"));int count=parent.getInt("count")-parent.getInt("secured");
  if(e==null||count<=0||!parent.contains("cart")||TradeLadder.level(l,e)<5)return;
  var dog=VillageWolves.freeWolf(l,e);var carts=carts(l,e);if(dog==null||carts.isEmpty())return;
  var t=new CompoundTag();for(var key:List.of("source","destination","dimension","item","price","per","from","to"))if(parent.contains(key))t.put(key,parent.get(key).copy());
  t.putInt("schema",1);t.putUUID("id",Settlement.childId(parent.getUUID("id"),KIND));t.putString("kind",KIND);t.putUUID("leaderTrip",parent.getUUID("id"));t.putUUID("caravaneer",dog.getUUID());
  t.putInt("count",Math.min(count,Caravans.carry(l,e)));t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putString("state",Caravans.ACCEPTED);
  Caravans.stowCart(t,carts.get(0));remember(t,dog);Caravans.update(s,t);parent.putUUID("dogTrip",t.getUUID("id"));Caravans.update(s,parent);
  if(Caravans.secure(l,t,now)<=0){t.putString("state",Caravans.CANCELLED);Caravans.parkCart(l,t,e);Caravans.update(s,t);return;}
  dematerialize(l,t,dog);t.putString("state",Caravans.TRANSIT);Caravans.update(s,t);
 }
 private static void remember(CompoundTag t,Wolf dog){var tag=new CompoundTag();dog.saveWithoutId(tag);t.put("wolf",tag);}
 public static boolean stale(Wolf dog){return dog.level() instanceof ServerLevel l&&dog.getPersistentData().getLong(Caravans.EPOCH)<Caravans.epoch(l.getServer(),dog.getUUID());}
 public static Wolf materialize(ServerLevel l,CompoundTag t,BlockPos at){
  if(!t.contains("wolf"))return null;var id=t.getUUID("caravaneer");if(l.getEntity(id) instanceof Wolf existing){
   if(!stale(existing)&&t.getBoolean("materialized"))return existing;existing.discard();}
  var dog=net.minecraft.world.entity.EntityType.WOLF.create(l);if(dog==null)return null;var saved=t.getCompound("wolf");dog.load(saved);dog.setUUID(id);
  // Vanilla Wolf.setTame during NBT load heals to full; a trip is not a free treatment.
  if(saved.contains("Health"))dog.setHealth(Math.min(dog.getMaxHealth(),saved.getFloat("Health")));
  dog.getPersistentData().putLong(Caravans.EPOCH,Caravans.bump(l.getServer(),id));dog.setOrderedToSit(false);dog.setInSittingPose(false);
  var from=Caravans.from(t);var to=Caravans.to(t);double len=Math.max(1,Caravans.length(t));
  var spot=Caravans.ground(l,at.offset((int)Math.round(-(to.getZ()-from.getZ())*3/len),0,(int)Math.round((to.getX()-from.getX())*3/len)));
  dog.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);
  if(!l.addFreshEntity(dog))return null;t.putBoolean("materialized",true);
  if(t.contains("cart")&&Caravans.cartBody(l,t,dog,spot)==null){dematerialize(l,t,dog);return null;}
  Caravans.update(l.getServer(),t);return dog;
 }
 public static void unload(Wolf dog){
  if(!(dog.level() instanceof ServerLevel l)||stale(dog))return;var t=record(l.getServer(),dog.getUUID());if(t!=null&&t.getBoolean("materialized"))dematerialize(l,t,dog);
 }
 private static void dematerialize(ServerLevel l,CompoundTag t,Wolf dog){
  remember(t,dog);t.putBoolean("materialized",false);Caravans.bump(l.getServer(),dog.getUUID());t.putLong("cartEpoch",t.getLong("cartEpoch")+1);
  for(var c:l.getEntitiesOfClass(CartEntity.class,dog.getBoundingBox().inflate(CartEntity.LOST),c->t.getUUID("id").equals(c.trip())))c.discard();
  dog.discard();Caravans.update(l.getServer(),t);
 }
 public static void died(Wolf dog,long now){
  if(!(dog.level() instanceof ServerLevel l)||stale(dog))return;var t=record(l.getServer(),dog.getUUID());if(t==null)return;
  Caravans.died(dog,t,now);Caravans.bump(l.getServer(),dog.getUUID());t.remove("wolf");Caravans.update(l.getServer(),t);
 }
 /** The leader waits for the visible second load before leaving the destination. */
 static boolean nearGate(net.minecraft.world.entity.Entity body,BlockPos gate){double x=gate.getX()+.5-body.getX(),z=gate.getZ()+.5-body.getZ();return x*x+z*z<=Caravans.ARRIVE*Caravans.ARRIVE;}
 public static boolean waiting(ServerLevel l,CompoundTag parent,net.minecraft.world.entity.Entity driver){
  var t=child(l.getServer(),parent);if(t==null||!Caravans.open(t))return false;
  var dog=l.getEntity(t.getUUID("caravaneer"));return dog instanceof Wolf&&dog.isAlive()&&driver.distanceToSqr(dog)>8*8;
 }
 /** A chunk boundary must not let an abstract leader outrun a dog the player can still see. */
 public static boolean visibleDog(ServerLevel l,CompoundTag parent){
  var t=child(l.getServer(),parent);if(t==null||!t.getBoolean("materialized")||!Caravans.open(t))return false;
  if(l.getEntity(t.getUUID("caravaneer")) instanceof Wolf dog&&visible(l,dog.blockPosition())){
   Caravans.materialize(l,parent,Caravans.position(l,parent,parent.getDouble("progress")));return true;
  }return false;
 }
 public static boolean deliverTogether(ServerLevel l,CompoundTag parent,long now){
  var t=child(l.getServer(),parent);if(t==null||!t.getString("state").equals(Caravans.TRANSIT))return true;
  var dog=l.getEntity(t.getUUID("caravaneer"));var to=Caravans.to(t);
  if(t.getBoolean("materialized")&&(dog==null||!nearGate(dog,to)))return false;
  Caravans.arrive(l,t,now);return true;
 }
 /** On losing the leader, return from the actual location with this cart's cargo intact. */
 private static void returnHome(ServerLevel l,CompoundTag t){
  var dog=l.getEntity(t.getUUID("caravaneer"));var at=dog!=null?dog.blockPosition():Caravans.position(l,t,t.getDouble("progress"));
  var home=BlockPos.of(t.getLong("from"));t.putLong("to",at.asLong());t.putLong("from",home.asLong());t.putBoolean("home",true);t.putDouble("progress",0);t.putString("state",Caravans.RETURNING);Caravans.update(l.getServer(),t);
 }
 public static boolean tick(ServerLevel l,CompoundTag t,long now){
  if(!isDog(t))return false;var s=l.getServer();var state=t.getString("state");
  if(state.equals(Caravans.CANCELLED))return true;
  if(state.equals(Caravans.ACCEPTED)||state.equals(Caravans.SECURED)){
   if(state.equals(Caravans.ACCEPTED)&&Caravans.secure(l,t,now)<=0)return true;
   if(l.getEntity(t.getUUID("caravaneer")) instanceof Wolf dog)dematerialize(l,t,dog);
   t.putString("state",Caravans.TRANSIT);Caravans.update(s,t);state=Caravans.TRANSIT;
  }
  if(state.equals(Caravans.CLOSED)){
   if(t.getBoolean("needsEntity")){var at=BlockPos.of(t.getLong("from"));if(visible(l,at)&&materialize(l,t,at)!=null){t.putBoolean("needsEntity",false);Caravans.update(s,t);}}
   return true;
  }
  var parent=Caravans.contract(s,t.getUUID("leaderTrip"));
  if(state.equals(Caravans.TRANSIT)&&(parent==null||!parent.getString("state").equals(Caravans.TRANSIT))){returnHome(l,t);state=Caravans.RETURNING;}
  if(t.getBoolean("materialized")){
   if(l.getEntity(t.getUUID("caravaneer")) instanceof Wolf dog){
    if(state.equals(Caravans.TRANSIT)&&parent!=null&&!parent.getBoolean("materialized")&&!visible(l,dog.blockPosition()))dematerialize(l,t,dog);
    else{remember(t,dog);Caravans.update(s,t);return true;}}
   t.putBoolean("materialized",false);Caravans.update(s,t);
  }
  var at=Caravans.position(l,t,t.getDouble("progress"));if(visible(l,at)){materialize(l,t,at);return true;}
  if(state.equals(Caravans.TRANSIT)){t.putDouble("progress",Math.min(Caravans.length(t),parent.getDouble("progress")));Caravans.update(s,t);}
  else if(state.equals(Caravans.RETURNING)){t.putDouble("progress",Math.min(Caravans.length(t),t.getDouble("progress")+Caravans.SPEED*20));if(t.getDouble("progress")>=Caravans.length(t))Caravans.arrive(l,t,now);else Caravans.update(s,t);}
  return true;
 }
 private static boolean visible(ServerLevel l,BlockPos at){return l.hasChunkAt(at)&&!l.getPlayers(p->p.blockPosition().distSqr(at)<Caravans.MATERIALIZE_RADIUS*Caravans.MATERIALIZE_RADIUS).isEmpty();}
}
