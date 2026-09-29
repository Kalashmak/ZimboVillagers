package org.villageastra.world;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-147 §3.3: the warehouse's store is real chests. The stock chest (1,1,4) is the master: it holds 54 slots for every page of the working
 *  level (108 at I .. 648 at VI, core effect "storage"), and every page is a double chest in the plan's cells (set down here; the plan builds only the master) whose two halves show 27 of the master's
 *  slots each (HallStorage's parts: AstraHallMaster/Page/Offset). The journal, the couriers, the pantries, research payments and caravans see
 *  one container at (1,1,4), as before. A page is linked once the level stands and while its chests are empty or already linked; the store grows
 *  page by page up to the first page that cannot be linked (a player's own chest in its cell), so no slot of the master is ever out of sight.
 *  The master only grows (OwnedChestEntity.expand), keeping every item in its slot. */
public final class WarehouseStorage {
 private WarehouseStorage(){}
 /** Every warehouse of a settlement (ServerEvents, every 20 ticks with players). */
 public static void ensure(ServerLevel l,SettlementData.Entry e){if(!e.dimension().equals(l.dimension().location().toString()))return;for(var b:e.settlement().buildings())if(WarehouseStore.is(b))ensure(l,e,b);}
 /** Links the pages of the working level and grows the master to them; returns the pages the store has now (0: its master is not there). */
 public static int ensure(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  // A level being built is linked once its project is over (the builders may be laying a page's chests).
  if(HallUpgradeGoal.pending(l,e.settlement().id()))return pages(l,e,b);
  var cells=WarehouseStore.chests(Math.max(1,BuildingLevels.level(l,e,b)));var anchor=WarehouseStore.at(e,b,cells.get(0));
  if(!l.hasChunkAt(anchor)||!(l.getBlockEntity(anchor) instanceof OwnedChestEntity master))return 0;
  int linked=0;
  for(int page=0;page*2+1<cells.size();page++){boolean ok=true;
   for(int half=0;half<2&&ok;half++){int i=page*2+half;var pos=WarehouseStore.at(e,b,cells.get(i));
    if(!l.hasChunkAt(pos)){ok=false;break;}
    // The plan builds only the stock chest: a free page cell gets its chest here (as the hall's pages, HallStorage.ensure).
    if(i>0&&l.getBlockState(pos).isAir())continue;
    if(!l.getBlockState(pos).is(VillageAstra.OWNED_CHEST.get())||!(l.getBlockEntity(pos) instanceof OwnedChestEntity chest)){ok=false;break;}
    var data=chest.getPersistentData();
    if(data.contains("AstraHallMaster")){if(data.getLong("AstraHallMaster")!=anchor.asLong()||data.getInt("AstraHallOffset")!=i*27)ok=false;continue;}
    if(i>0&&!chest.isEmpty())ok=false;}
   if(!ok)break;
   for(int half=0;half<2;half++){int i=page*2+half;var pos=WarehouseStore.at(e,b,cells.get(i));
    var state=BuildingPlacement.state(DistinctArchitecture.chest(WarehouseStore.chestSpec(i)),b.rotation());
    if(!l.getBlockState(pos).equals(state))l.setBlock(pos,state,2);
    if(l.getBlockEntity(pos) instanceof OwnedChestEntity chest){var data=chest.getPersistentData();
     if(!data.contains("AstraHallMaster")){data.putLong("AstraHallMaster",anchor.asLong());data.putInt("AstraHallPage",page);data.putInt("AstraHallOffset",i*27);data.putUUID("AstraSettlement",e.settlement().id());chest.setChanged();}}}
   linked=page+1;}
  if(linked>0)master.expand(linked*WarehouseStore.PAGE);
  return master.getContainerSize()/WarehouseStore.PAGE;
 }
 /** Pages the store holds now: its master's slots by 54 (0 without a master, an unlinked old chest of 27 counts as none). */
 public static int pages(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var c=LogisticsRoutes.chest(l,e,b);return c==null?0:c.getContainerSize()/WarehouseStore.PAGE;}
 /** Slots the store of this level holds (core effect "storage"). */
 public static int slots(int level){return WarehouseStore.slots(level);}
}
