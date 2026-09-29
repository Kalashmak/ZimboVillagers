package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 (spec §10.2, F5, F6): a working yard asks the porters for the feed of its pens, shears while it keeps sheep (no lead: the keeper
 *  drives new stock in, owner 2026-09-23), into its own chest; its feed and tools stay there; its wool, meat, leather and eggs go to the stock like a harvest. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LivestockSupplyGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building yard,OwnedChestEntity yardChest,OwnedChestEntity hallChest){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",12,0,0);s.addBuilding(yard);
  for(int x=-2;x<32;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(hall,yard))l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  // Pen 1's feeder where the plan puts it, so the yard works its first pen.
  l.setBlock(LivestockPens.at(e,yard,LivestockPens.pen(1).feeder()),VillageAstra.FEEDER.get().defaultBlockState(),2);
  var keeper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);var room=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(room);s.admit(keeper,room.id());s.assign(keeper.id(),Profession.LIVESTOCK_FARMER,yard.id());
  return new Town(l,s,e,yard,LogisticsRoutes.chest(l,e,yard),LogisticsRoutes.chest(l,e,hall));
 }
 private static void done(Town t){SettlementData.get(t.l.getServer()).remove(t.s.id());}
 private static boolean wants(Town t,net.minecraft.world.item.Item item){return WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.destination().equals(t.yard.id())&&w.ingredient().test(new ItemStack(item)));}
 @GameTest(template="empty",timeoutTicks=100) public static void theYardAsksForFeedAndShears(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(wants(t,Items.WHEAT)&&wants(t,Items.SHEARS),"An empty sheep pen: wheat, shears");
   h.assertTrue(!wants(t,Items.LEAD),"No lead: new stock is driven in");
   t.yardChest.setItem(0,new ItemStack(Items.WHEAT,LivestockPens.FEED_STOCK));t.yardChest.setItem(2,new ItemStack(Items.SHEARS));
   h.assertTrue(!wants(t,Items.WHEAT)&&!wants(t,Items.SHEARS),"Stocked, it asks for nothing");
   h.assertTrue(!wants(t,Items.CARROT),"No pig pen at level I: no carrots");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theYardKeepsItsFeedAndSendsItsProducts(GameTestHelper h){
  var t=town(h);
  try{
   t.yardChest.setItem(0,new ItemStack(Items.WHEAT,LivestockPens.FEED_STOCK));t.yardChest.setItem(1,new ItemStack(Items.WHITE_WOOL,12));
   h.assertTrue(LogisticsRoutes.reserve(t.yard,new ItemStack(Items.WHEAT))==LivestockPens.FEED_STOCK&&LogisticsRoutes.reserve(t.yard,new ItemStack(Items.SHEARS))==1&&LogisticsRoutes.reserve(t.yard,new ItemStack(Items.LEAD))==0,"Feed and shears stay in the yard, a lead does not");
   var route=LogisticsRoutes.choose(t.l,t.e);
   h.assertTrue(route!=null&&route.source().equals(t.yard)&&route.item().is(Items.WHITE_WOOL),"The wool goes to the stock: "+(route==null?"no route":route.item()));
   t.yardChest.setItem(1,ItemStack.EMPTY);var none=LogisticsRoutes.choose(t.l,t.e);
   h.assertTrue(none==null||!none.source().equals(t.yard),"The yard's feed is not swept away with it");
  }finally{done(t);}
  h.succeed();
 }
}
