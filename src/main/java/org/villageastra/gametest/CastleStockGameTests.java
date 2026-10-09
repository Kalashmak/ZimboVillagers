package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-466: native castle stock addresses and four physical kit chests; no replacement furniture or receipt migration. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CastleStockGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,List<net.minecraft.world.level.ChunkPos> held){
  Settlement.Building hall(){return Workshops.hall(e);}BlockPos stock(){return HallSite.stock(e);}List<BlockPos> pages(){var p=stock();return List.of(p,p.south(),p.south(3),p.south(4));}
  OwnedChestEntity chest(BlockPos p){return (OwnedChestEntity)l.getBlockEntity(p);}
  void close(){HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);}
 }
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var center=new BlockPos(at.getX()+1900544,130,at.getZ());var held=PhysicalFixtureChunks.force(l,center,-20,26,-28,18);for(var c:held)l.getChunk(c.x,c.z);
  for(int x=-20;x<=26;x++)for(int z=-28;z<=18;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<40;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.CASTLE_LOTS);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",OrganicLots.CASTLE_X,0,OrganicLots.CASTLE_Z);s.addBuilding(hall);var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);ResearchV2Town.lay(l,e,hall,BuildingTiers.layoutId(s,"town_hall",1));return new Town(l,e,s,held);
 }
 @GameTest(template="empty",batch="castle_stock_lookup_baseline",timeoutTicks=200)
 public static void castleLogisticsLookupMustUseThePhysicalKitStock(GameTestHelper h){
  var t=town(h);try{var stock=t.stock();h.assertTrue(HallSite.castle(t.s)&&stock.equals(HallSite.castleStock(t.e.center()))&&t.l.getBlockEntity(stock) instanceof OwnedChestEntity,"True CastlePlan physically owns its real castle stock chest");h.assertTrue(LogisticsRoutes.position(t.e,t.hall()).equals(stock)&&LogisticsRoutes.chest(t.l,t.e,t.hall())==t.chest(stock),"Castle logistics addresses its physical kit stock");}finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="castle_stock_guards",timeoutTicks=200)
 public static void allFourNativeKitChestsShareExactlyTheirOwnPages(GameTestHelper h){
  var t=town(h);try{var states=t.pages().stream().map(t.l::getBlockState).toList();var master=t.chest(t.stock());master.setItem(0,new ItemStack(Items.DIAMOND,3));HallStorage.ensure(t.l,t.e);h.assertTrue(master.getContainerSize()==108,"Actual castle master grows to exactly two pages");
   for(int i=0;i<4;i++){var p=t.pages().get(i);var part=t.chest(p);var meta=part.getPersistentData();h.assertTrue(t.l.getBlockState(p).equals(states.get(i))&&meta.getLong("AstraHallMaster")==t.stock().asLong()&&meta.getInt("AstraHallPage")==i/2&&meta.getInt("AstraHallOffset")==i*27&&meta.getUUID("AstraSettlement").equals(t.s.id()),"Native chest state, owner and page offset retained: "+i);if(i>0){part.setItem(0,new ItemStack(Items.EMERALD,i));h.assertTrue(master.getItem(i*27).getCount()==i&&master.getItem(i*27).is(Items.EMERALD),"Physical page forwards exactly its own native slots");}}
   HallStorage.ensure(t.l,t.e);HallStorage.ensure(t.l,t.e);h.assertTrue(master.getItem(0).is(Items.DIAMOND)&&master.getItem(0).getCount()==3&&master.getItem(81).getCount()==3&&master.getContainerSize()==108,"Repeated ensure changes neither stock nor alias slots");for(int i=0;i<4;i++)h.assertTrue(t.l.getBlockState(t.pages().get(i)).equals(states.get(i)),"Idempotent ensure never replaces castle furniture");
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="castle_stock_guards",timeoutTicks=200)
 public static void anOccupiedSecondaryChestIsNeverMergedOrDiscarded(GameTestHelper h){
  var t=town(h);try{var master=t.chest(t.stock());var other=t.chest(t.pages().get(2));master.setItem(0,new ItemStack(Items.DIAMOND,2));other.setItem(0,new ItemStack(Items.EMERALD,5));HallStorage.ensure(t.l,t.e);h.assertTrue(master.getContainerSize()==27&&master.getItem(0).getCount()==2&&other.getItem(0).getCount()==5&&!other.getPersistentData().contains("AstraHallMaster"),"Occupied separate stock refuses installation without merging, replacing or losing either inventory");}finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="castle_stock_guards",timeoutTicks=200)
 public static void anActiveProjectKeepsItsStockAddressAndFurniture(GameTestHelper h){
  var t=town(h);try{var quote=HallUpgradeGoal.preview(t.l,t.e);HallUpgradeGoal.enqueue(t.l,t.e,quote);var master=t.chest(t.stock());master.setItem(0,new ItemStack(Items.DIAMOND,4));var states=t.pages().stream().map(t.l::getBlockState).toList();HallStorage.ensure(t.l,t.e);h.assertTrue(master.getContainerSize()==27&&master.getItem(0).getCount()==4&&HallUpgradeGoal.inspect(t.l,t.s.id()).equals(quote),"Installation never modifies an active project's stock or execution record");for(int i=0;i<4;i++)h.assertTrue(t.l.getBlockState(t.pages().get(i)).equals(states.get(i))&&!t.chest(t.pages().get(i)).getPersistentData().contains("AstraHallMaster"),"Active project leaves every native kit chest intact");}finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="castle_stock_guards",timeoutTicks=200)
 public static void missingOrBlockedKitChestsAreNeverCreatedForFree(GameTestHelper h){
  var t=town(h);try{var master=t.chest(t.stock());master.setItem(0,new ItemStack(Items.DIAMOND,6));var missing=t.pages().get(3);t.l.setBlock(missing,Blocks.AIR.defaultBlockState(),2);HallStorage.ensure(t.l,t.e);h.assertTrue(master.getContainerSize()==27&&t.l.getBlockState(missing).isAir()&&master.getItem(0).getCount()==6,"Missing native kit refuses installation without free furniture");t.l.setBlock(missing,Blocks.BEDROCK.defaultBlockState(),2);HallStorage.ensure(t.l,t.e);h.assertTrue(master.getContainerSize()==27&&t.l.getBlockState(missing).is(Blocks.BEDROCK)&&master.getItem(0).getCount()==6,"Blocked native kit refuses installation without overwriting terrain");}finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="castle_stock_guards",timeoutTicks=200)
 public static void theLegacyHallAddressAndFourPageLayoutStayTheSame(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{var pages=List.of(center.offset(1,1,4),center.offset(1,1,3),center.offset(5,1,2),center.offset(5,1,3));for(var p:pages)l.setBlock(p,Blocks.AIR.defaultBlockState(),2);l.setBlock(pages.get(0),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var master=(OwnedChestEntity)l.getBlockEntity(pages.get(0));master.setItem(0,new ItemStack(Items.DIAMOND,7));HallStorage.ensure(l,e);h.assertTrue(!HallSite.castle(s)&&LogisticsRoutes.position(e,hall).equals(pages.get(0))&&LogisticsRoutes.chest(l,e,hall)==master&&master.getContainerSize()==108&&master.getItem(0).getCount()==7,"Legacy address, original inventory and two-page capacity unchanged");for(int i=0;i<4;i++){var c=(OwnedChestEntity)l.getBlockEntity(pages.get(i));h.assertTrue(c!=null&&c.getPersistentData().getInt("AstraHallOffset")==i*27,"Legacy page cell and offset unchanged: "+i);}}
  finally{SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
