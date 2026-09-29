package org.villageastra.client;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Real kennel wolf, two real carts and a deliberately large 1000-item order exercise level V. */
final class CaravanDogProbe {
 private CaravanDogProbe(){}
 private static UUID wolf;private static long lastMove;private static double lastProgress;private static boolean sawPair;private static volatile boolean finished;
 static boolean enabled(){return Boolean.getBoolean("villageastra.caravanDogSmoke");}
 static void setup(ServerLevel l,SettlementData.Entry home,Settlement.Building yard,SettlementData.Entry destination){
  if(!enabled())return;l.setDayTime(6000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,l.getServer());var s=home.settlement();var kennel=new Settlement.Building(UUID.randomUUID(),VillageWolves.TYPE,-40,0,30);
  var livestock=new Settlement.Building(UUID.randomUUID(),"livestock",-40,0,45);s.addBuilding(livestock);s.addBuilding(kennel);s.linkAnnex(kennel.id(),livestock.id());for(var cell:BuildingPlacement.layout(home,kennel,VillageWolves.TYPE).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  var spot=CartHitch.parking(l,BuildingPlacement.origin(home,yard));if(spot==null)throw new IllegalStateException("No dog cart parking");
  for(int i=0;i<2;i++){var free=CartHitch.parking(l,spot);if(free==null)throw new IllegalStateException("No second parking space");l.addFreshEntity(new CartEntity(l,free,s.id()));}
  var dogSpot=CartHitch.parking(l,spot);if(dogSpot==null)throw new IllegalStateException("No open ground for the dog");
  var dog=EntityType.WOLF.create(l);dog.setTame(true);dog.setOwnerUUID(l.getServer().getPlayerList().getPlayers().get(0).getUUID());dog.moveTo(dogSpot.getX()+.5,dogSpot.getY(),dogSpot.getZ()+.5,0,0);l.addFreshEntity(dog);
  if(!VillageWolves.enlist(l,home,dog,kennel))throw new IllegalStateException("Could not enlist real wolf");dog.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));wolf=dog.getUUID();
  var stock=(Container)l.getBlockEntity(LogisticsRoutes.position(home,Workshops.hall(home)));stock.clearContent();for(int i=0;i<22;i++)stock.setItem(i,new ItemStack(Items.BREAD,64));
  long now=SettlementData.get(l.getServer()).clock().ticks();Caravans.snapshot(l,home,now);var t=Caravans.propose(l,destination,home,now);
  if(t==null)throw new IllegalStateException("No real bread order");t.putInt("count",1000);Caravans.update(l.getServer(),t);
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_DOG fixture: actual kennel wolf {}, two carts, explicit 1000-bread order",wolf);
 }
 static int extraDelivered(ServerLevel l,CompoundTag parent){var t=CaravanDogs.child(l.getServer(),parent);return t==null?0:t.getInt("delivered");}
 static void observe(ServerLevel l,CompoundTag parent){
  if(!enabled())return;var t=CaravanDogs.child(l.getServer(),parent);if(t==null)return;
  var dog=l.getEntity(wolf);var driver=l.getEntity(parent.getUUID("caravaneer"));
  if(dog instanceof Wolf&&driver!=null&&parent.getDouble("progress")>20&&parent.getString("state").equals(Caravans.TRANSIT)){
   boolean own=!l.getEntitiesOfClass(CartEntity.class,driver.getBoundingBox().inflate(8),c->parent.getUUID("id").equals(c.trip())&&driver.getUUID().equals(c.puller())).isEmpty();
   boolean second=!l.getEntitiesOfClass(CartEntity.class,dog.getBoundingBox().inflate(8),c->t.getUUID("id").equals(c.trip())&&wolf.equals(c.puller())).isEmpty();
   if(own&&second&&dog.distanceToSqr(driver)<12*12){sawPair=true;org.villageastra.server.ProbeWarp.end("dog caravan camera: normal speed before the road capture");}
  }
  if(t.getInt("delivered")>0&&t.getLong("paid")!=(long)t.getInt("delivered")*t.getInt("price")/t.getInt("per"))throw new IllegalStateException("Dog cart payment mismatch");
  long now=SettlementData.get(l.getServer()).clock().ticks();double progress=parent.getDouble("progress")+t.getDouble("progress");
  if(lastMove==0||Math.abs(progress-lastProgress)>.25){lastMove=now;lastProgress=progress;}
  if(now-lastMove>2400)throw new IllegalStateException("Dog caravan stalled: "+diagnostic(l,dog,t)+" driver="+(driver==null?"none":driver.blockPosition()));
  finished=!parent.contains("cart")&&!t.contains("cart")&&sawPair&&t.getInt("delivered")>0&&parent.getInt("delivered")+t.getInt("delivered")==1000&&parent.getString("state").equals(Caravans.CLOSED)&&t.getString("state").equals(Caravans.CLOSED)&&dog instanceof Wolf&&dog.isAlive()&&!CaravanDogs.reserved(l.getServer(),wolf);
  if(finished){var home=SettlementData.get(l.getServer()).entry(parent.getUUID("source"));TouchLoad.force(l,BuildingPlacement.origin(home,Caravans.yard(home)));var parked=CaravanDogs.carts(l,home);
   finished=parked.size()==2&&!parked.get(0).getBoundingBox().intersects(parked.get(1).getBoundingBox());}
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_DOG progress pair={} state={} delivered={} body={} progress={}",sawPair,t.getString("state"),t.getInt("delivered"),dog==null?"none":dog.blockPosition(),t.getDouble("progress"));
 }
 private static String diagnostic(ServerLevel l,net.minecraft.world.entity.Entity dog,CompoundTag t){
  if(!(dog instanceof Wolf w))return "dog absent";
  return "dog="+w.blockPosition()+" goals="+w.goalSelector.getAvailableGoals().stream().filter(g->g.isRunning()).map(g->g.getGoal().getClass().getSimpleName()).toList()+" nav="+(w.getNavigation().getPath()==null?"none":w.getNavigation().getPath().getTarget()+"/"+w.getNavigation().getPath().canReach())+" carts="+l.getEntitiesOfClass(CartEntity.class,w.getBoundingBox().inflate(20),c->t.getUUID("id").equals(c.trip())).stream().map(c->c.blockPosition()+" blocked="+c.blocked()+" pulled="+w.getUUID().equals(c.puller())+" distance="+w.distanceToSqr(c)).toList();
 }
 static void restore(CompoundTag t){wolf=t.getUUID("caravaneer");}
 static boolean captureReady(){return !enabled()||sawPair;}
 static boolean finished(){if(!enabled())return true;if(!finished)return false;com.mojang.logging.LogUtils.getLogger().info("ASTRA_CARAVAN_DOG VERIFIED carts=2 followed=true delivered=1000 returned=true parked=2 reload={}",CaravanRestartProbe.reloading());return true;}
}
