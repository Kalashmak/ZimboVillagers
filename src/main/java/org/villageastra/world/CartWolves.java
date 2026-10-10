package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-147 §5: the warehouse's wolves, behind VillageDogs - the one seam with the livestock work (AD-138: the kennel, its quest and its level).
 *  Owner rule: a village's wolves appear only after their quest, their building and their research level; the source (the kennel registry)
 *  guarantees that, this class only asks it. Until the livestock work provides its source there are no wolves: {@link #available} is empty,
 *  a level-V courier pulls one cart and the card marks the wolf as planned; at VI the couriers keep carrying ("no wolves - the couriers
 *  work", owner default Q4). V: a courier on a trip takes a free wolf harnessed to a second cart that follows it (+5 stacks). VI: every free
 *  wolf (up to {@link #TEAMS}) pulls a cart of its own on trips the warehouse plans itself (WarehouseTrips kind "wolf"); the couriers are
 *  relieved of their posts only while wolves carry and no courier holds a load (CF-M). */
public final class CartWolves {
 private CartWolves(){}
 public static final String HARNESS_RESEARCH="logistics.5",ALONE_RESEARCH="logistics.6";
 /** The most wolf carts a warehouse of VI runs at once (the kennel's four wolves). */
 public static final int TEAMS=4;
 /** Level V with Logistics V: a courier may take a wolf and a second cart. */
 public static boolean gate(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingLevels.level(l,e,b)>=5&&ResearchKnobs.done(l,e).contains(HARNESS_RESEARCH);}
 /** The village's free wolves a cart of this warehouse may take now; empty below V, without Logistics V or without a source. */
 public static List<UUID> available(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return gate(l,e,b)&&VillageDogs.pulls()?VillageDogs.available(l,e):List.of();}
 /** Why the warehouse's wolves do not pull now, or empty: "level" (below V or its research), "planned" (no village has wolves yet), "no_wolf". */
 public static String refusal(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(!gate(l,e,b))return "level";if(!VillageDogs.pulls())return "planned";
  return VillageDogs.available(l,e).isEmpty()&&WarehouseTrips.wolves(l,e,b)==0?"no_wolf":"";}
 /** VI with Logistics VI and a source of wolves: the warehouse sends its wolves alone. */
 public static boolean alone(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingLevels.level(l,e,b)>=6&&ResearchKnobs.done(l,e).contains(ALONE_RESEARCH)&&VillageDogs.pulls();}
 /** VI: the wolf teams of the warehouse - wolves on its trips now and free ones, at most TEAMS; 0 below VI. */
 public static int teams(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return alone(l,e,b)?Math.min(TEAMS,WarehouseTrips.wolves(l,e,b)+VillageDogs.available(l,e).size()):0;}
 private static final Map<UUID,Boolean> RELIEVED=new java.util.concurrent.ConcurrentHashMap<>();
 /** Whether the village's warehouse couriers are relieved (Population.slots of its warehouse is 0): read by the labour office. */
 public static boolean relieved(UUID settlement){return RELIEVED.getOrDefault(settlement,false);}
 /** Warehouses.tick: an established wolf trip, or a ready wolf already at its usable hitch, replaces the courier.
  *  A free wolf alone is only capacity; no courier is unposted while its parcel or trip remains active (CF-M). */
 static void update(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  boolean service=alone(l,e,b)&&(WarehouseTrips.wolfService(l,e,b)||ready(l,e,WarehouseCarts.free(l,e,b,null))||ready(l,e,WarehouseCarts.stranded(l,e,b)));
  RELIEVED.put(e.settlement().id(),service&&!WarehouseTrips.couriersBusy(l,e,b));}
 private static boolean ready(ServerLevel l,SettlementData.Entry e,CartEntity cart){return cart!=null&&VillageDogs.available(l,e).stream().anyMatch(wolf->VillageDogs.pickupReady(l,e,wolf,cart));}
 /** Tests: forget a village's flag. */
 public static void forget(UUID settlement){RELIEVED.remove(settlement);}
}
