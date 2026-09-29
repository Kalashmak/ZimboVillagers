package org.villageastra.server;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.villageastra.world.HallUpgradeGoal;
/** Every request rechecks current authority, range, life, dimension and both revisions. */
public final class ManagementOrders {
 private ManagementOrders(){}
 public static boolean allowedContext(ServerPlayer actor,SettlementData.Entry e){return e!=null&&actor.isAlive()&&!actor.isSpectator()&&e.dimension().equals(actor.serverLevel().dimension().location().toString())&&e.center().distSqr(actor.blockPosition())<=ConstructionViews.RADIUS*ConstructionViews.RADIUS&&!(actor.getLastHurtByMobTimestamp()>0&&actor.tickCount-actor.getLastHurtByMobTimestamp()<100);}
 public static boolean pause(ServerPlayer actor,UUID settlement,UUID project,long epoch,long revision,boolean value){
  var data=SettlementData.get(actor.server);var e=data.entry(settlement);
  if(!allowedContext(actor,e))return false;
  if(!e.settlement().governance().canManage(actor.getUUID(),epoch)||!HallUpgradeGoal.pending(actor.serverLevel(),settlement))return false;
  if(!org.villageastra.world.HallConstructionPlan.projectId(HallUpgradeGoal.inspect(actor.serverLevel(),settlement)).equals(project))return false;
  if(!e.settlement().governance().setPaused(actor.getUUID(),epoch,revision,project,value))return false;data.setDirty();return true;
 }
 /** AD-085 (ROAD-008, T215): the mayor orders a trip of the village to leave its cart where it stands and go on on foot. */
 public static String leaveCart(ServerPlayer actor,UUID settlement,UUID trip,long epoch,long revision){
  var data=SettlementData.get(actor.server);var e=data.entry(settlement);
  if(!allowedContext(actor,e))return "mayor";
  var g=e.settlement().governance();
  if(!g.canManage(actor.getUUID(),epoch)||g.revision()!=revision)return "mayor";
  var t=org.villageastra.world.Caravans.contract(actor.server,trip);
  if(t==null||!t.getUUID("source").equals(settlement))return "trip";
  var l=actor.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(t.getString("dimension"))));
  if(l==null)return "trip";
  if(!g.recordOrder(actor.getUUID(),epoch,revision))return "mayor";
  int left=org.villageastra.world.Caravans.leaveCart(l,t);
  if(left<0)return "no_cart";
  data.setDirty();return "";
 }
 /** AD-094: the mayor sends a trained archer up a tower of the castle wall. */
 public static String postArcher(ServerPlayer actor,UUID settlement,UUID tower,long epoch,long revision){
  var data=SettlementData.get(actor.server);var e=data.entry(settlement);
  if(!allowedContext(actor,e))return "mayor";
  var g=e.settlement().governance();
  if(!g.canManage(actor.getUUID(),epoch)||g.revision()!=revision)return "mayor";
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(tower)).findFirst().orElse(null);if(b==null)return "building";
  var reason=org.villageastra.world.Walls.postRefusal(e.settlement(),b);if(!reason.isEmpty())return reason;
  if(!g.recordOrder(actor.getUUID(),epoch,revision))return "mayor";
  reason=org.villageastra.world.Walls.post(e.settlement(),b);data.setDirty();return reason;
 }
 /** AD-078: the mayor calls off a project the crew has not started, so the settlement can order something else instead. */
 public static String cancel(ServerPlayer actor,UUID settlement,UUID project,long epoch,long revision){
  var data=SettlementData.get(actor.server);var e=data.entry(settlement);
  if(!allowedContext(actor,e))return "mayor";
  var g=e.settlement().governance();
  if(!g.canManage(actor.getUUID(),epoch)||g.revision()!=revision)return "mayor";
  var reason=HallUpgradeGoal.cancellable(actor.serverLevel(),settlement,project);if(!reason.isEmpty())return reason;
  // Calling off is an order like any other: it takes the revision, and a paused project loses its pause with it.
  if(g.paused(project)){if(!g.setPaused(actor.getUUID(),epoch,revision,project,false))return "mayor";}
  else if(!g.recordOrder(actor.getUUID(),epoch,revision))return "mayor";
  HallUpgradeGoal.drop(actor.serverLevel(),settlement);data.setDirty();return "";
 }
}
