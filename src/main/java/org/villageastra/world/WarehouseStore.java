package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-147: the warehouse's cells on its 23x17 lot (VillageStyle "warehouse@N", one plan for every world, CF-A). The door (6,1,1), the stock
 *  chest (1,1,4) with its pair (1,1,3) and the core (6,1,9) stand in the same cells at every level; the pages of the store (double chests,
 *  VillageStyle.WAREHOUSE_PAGES) keep their cells from their level on, so the master at (1,1,4) holds 54 slots a page: 108, 162, 324, 432,
 *  486 and 648 (core effect "storage"). The cart bays: B1 inside the barn's gate (II), B2 inside the wing's gate (IV), B3 in the east lane
 *  by the harness room (V). Pure geometry; the stock is WarehouseStorage, the couriers and carts WarehouseTrips, the sorting WarehouseSort. */
public final class WarehouseStore {
 private WarehouseStore(){}
 public static final String TYPE="warehouse";
 public static final BlockPos DOOR=new BlockPos(6,1,1),CHEST=new BlockPos(1,1,4),
  CORE=new BlockPos(VillageStyle.WAREHOUSE_CORE[0],VillageStyle.WAREHOUSE_CORE[1],VillageStyle.WAREHOUSE_CORE[2]);
 public static final BlockPos B1=new BlockPos(2,1,2),B2=new BlockPos(16,1,2),B3=new BlockPos(21,1,14);
 public static final int PAGE=54;
 static{
  var first=VillageStyle.WAREHOUSE_PAGES[0];
  if(first[1]!=CHEST.getX()||first[2]!=CHEST.getY()||first[3]!=CHEST.getZ())throw new IllegalStateException("The store's first page is not the stock chest");
  if(VillageStyle.WAREHOUSE_PAGES.length%2!=0)throw new IllegalStateException("A page of the store is two chests");
 }
 /** Pages of the store at a level: 2, 3, 6, 8, 9, 12. */
 public static int pages(int level){int n=0;for(var c:VillageStyle.WAREHOUSE_PAGES)if(c[0]<=level)n++;return n/2;}
 /** Slots of the store at a level (the master's size once the level stands). */
 public static int slots(int level){return pages(level)*PAGE;}
 /** The chest cells of the pages of a level, in the master's order (two a page; the first is the master (1,1,4)). */
 public static List<BlockPos> chests(int level){var out=new ArrayList<BlockPos>();for(var c:VillageStyle.WAREHOUSE_PAGES)if(c[0]<=level)out.add(new BlockPos(c[1],c[2],c[3]));return List.copyOf(out);}
 /** The plan string of a chest cell (its facing and half), for placing it the way the plan does. */
 static String chestSpec(int index){return VillageStyle.pageChest(VillageStyle.WAREHOUSE_PAGES[index]);}
 /** The cart bays of a level: none at I, B1 from II, B2 from IV, B3 from V. */
 public static List<BlockPos> bays(int level){var out=new ArrayList<BlockPos>();if(level>=2)out.add(B1);if(level>=4)out.add(B2);if(level>=5)out.add(B3);return List.copyOf(out);}
 private static volatile List<LevelArchitecture.Placed> KIT;
 /** Every station of levels II..VI with its level, in local cells. */
 public static List<LevelArchitecture.Placed> kits(){
  var k=KIT;if(k!=null)return k;var out=new ArrayList<LevelArchitecture.Placed>();
  for(int i=0;i<VillageStyle.WAREHOUSE_KIT.length;i++){var c=VillageStyle.WAREHOUSE_KIT[i];out.add(new LevelArchitecture.Placed(new BlockPos(c[1],c[2],c[3]),DistinctArchitecture.state(VillageStyle.WAREHOUSE_KIT_STATES[i]),c[0]));}
  return KIT=List.copyOf(out);
 }
 /** The equipment LevelArchitecture lays and BuildingTiers reads the working level from: the stations and the core of each level at its grade. */
 public static List<LevelArchitecture.Placed> equipment(){
  var out=new ArrayList<LevelArchitecture.Placed>();var core=Cores.block(TYPE);
  for(int level=2;level<=LevelArchitecture.MAX;level++){final int at=level;kits().stream().filter(p->p.level()==at).forEach(out::add);
   if(core!=null)out.add(new LevelArchitecture.Placed(CORE,core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,level),level));}
  return List.copyOf(out);
 }
 /** The block ids of level N's stations (the kits.warehouse of levels.json must list the same). */
 public static List<String> kitItems(int level){return kits().stream().filter(p->p.level()==level).map(p->BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).toString()).sorted().toList();}
 public static BlockPos at(SettlementData.Entry e,Settlement.Building b,BlockPos local){return BuildingPlacement.at(e,b,local.getX(),local.getY(),local.getZ());}
 public static boolean is(Settlement.Building b){return b!=null&&b.type().equals(TYPE);}
 /** The village's warehouse (the first one), or null. */
 public static Settlement.Building of(SettlementData.Entry e){return e.settlement().buildings().stream().filter(WarehouseStore::is).findFirst().orElse(null);}
}
