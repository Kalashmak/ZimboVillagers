package org.villageastra.world;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.SettlementData;
/** AD-147: the warehouse's own housekeeping, every 20 ticks of the village clock while players are on (ServerEvents): the pages of its store
 *  (WarehouseStorage), its carts every 100 ticks (WarehouseCarts: the records, a cart set down from its chest, CF-I), the trips of its wolves
 *  at VI (WarehouseTrips, CF-L) and whether its couriers are relieved (CartWolves, CF-M). The couriers' own trips run in PorterGoal. */
public final class Warehouses {
 private Warehouses(){}
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  if(!e.dimension().equals(l.dimension().location().toString()))return;
  for(var b:java.util.List.copyOf(e.settlement().buildings())){if(!WarehouseStore.is(b))continue;
   WarehouseStorage.ensure(l,e,b);
   if(!l.hasChunkAt(LogisticsRoutes.position(e,b)))continue;
   if(now%100<20)WarehouseCarts.ensure(l,e,b);
   WarehouseTrips.tickWolves(l,e,b);
   CartWolves.update(l,e,b);}
 }
}
