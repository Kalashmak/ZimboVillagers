package org.villageastra.client;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A real level VI yard, a lent horse and a cart: delivery must work with no caravaneer assignment. */
final class CaravanHorseProbe {
 private CaravanHorseProbe(){}
 private static UUID id,owner;private static boolean followed;private static volatile boolean done;private static long lastMove;private static double lastProgress;
 static boolean enabled(){return Boolean.getBoolean("villageastra.caravanHorseSmoke");}
 static void setup(ServerLevel l,SettlementData.Entry home,Settlement.Building yard){
  if(!enabled())return;l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());
  for(var r:home.settlement().residents())if(r.profession()==Profession.CARAVANEER){home.settlement().unassign(r.id());if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);}
  var at=CartHitch.parking(l,BuildingPlacement.origin(home,yard));if(at==null)throw new IllegalStateException("No horse cart parking");l.addFreshEntity(new CartEntity(l,at,home.settlement().id()));
  var spot=CartHitch.parking(l,at);if(spot==null)throw new IllegalStateException("No horse space");
  var horse=EntityType.HORSE.create(l);horse.setTamed(true);owner=l.getServer().getPlayerList().getPlayers().get(0).getUUID();horse.setOwnerUUID(owner);horse.equipSaddle(null);
  horse.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,0,0);l.addFreshEntity(horse);id=horse.getUUID();
  if(!VillageHorses.enlist(l,home,horse,owner))throw new IllegalStateException("Could not lend real horse");
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_HORSE fixture owner={} horse={} workers=0",owner,id);
 }
 static void observe(ServerLevel l,CompoundTag t){
  if(!enabled())return;if(!CaravanHorses.isHorse(t)||!id.equals(t.getUUID("caravaneer")))throw new IllegalStateException("Trip did not use assigned horse");
  var home=SettlementData.get(l.getServer()).entry(t.getUUID("source"));if(home.settlement().residents().stream().anyMatch(r->r.profession()==Profession.CARAVANEER))throw new IllegalStateException("Worker still assigned");
  var body=l.getEntity(id);if(body instanceof Horse horse&&t.getDouble("progress")>12&&t.getString("state").equals(Caravans.TRANSIT)){
   if(!l.getEntitiesOfClass(CartEntity.class,horse.getBoundingBox().inflate(8),c->t.getUUID("id").equals(c.trip())&&id.equals(c.puller())).isEmpty()){followed=true;org.villageastra.server.ProbeWarp.end("horse caravan camera");}
  }
  long now=SettlementData.get(l.getServer()).clock().ticks();if(lastMove==0||Math.abs(lastProgress-t.getDouble("progress"))>.25){lastMove=now;lastProgress=t.getDouble("progress");}
  if(now-lastMove>2400)throw new IllegalStateException("Horse stalled at "+(body==null?"absent":body.blockPosition())+" progress="+t.getDouble("progress")+" carts="+(body==null?"none":l.getEntitiesOfClass(CartEntity.class,body.getBoundingBox().inflate(20),c->t.getUUID("id").equals(c.trip())).stream().map(c->c.position()+" blocked="+c.blocked()+" puller="+c.puller()).toList()));
  done=followed&&t.getInt("delivered")>0&&t.getString("state").equals(Caravans.CLOSED)&&!t.contains("cart")&&!CaravanHorses.reserved(l.getServer(),id)&&body instanceof Horse h&&h.isAlive()&&owner.equals(h.getOwnerUUID())&&h.isSaddled();
  if(done){TouchLoad.force(l,BuildingPlacement.origin(home,Caravans.yard(home)));done=CaravanDogs.carts(l,home).size()==1;}
 }
 static void restore(CompoundTag t){id=t.getUUID("caravaneer");owner=t.getCompound("horse").getUUID("Owner");}
 static boolean captureReady(){return !enabled()||followed;}
 static boolean finished(){if(!enabled())return true;if(!done)return false;com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_HORSE VERIFIED workers=0 followed=true delivered=true returned=true parked=1 identity=true reload={}",CaravanRestartProbe.reloading());return true;}
}
