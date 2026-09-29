package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-036: zindbo are minted once per confirmed useful purchase, donations beat sales, resale loops earn nothing, and empty cards say why. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TradeGameTests {
 private record Market(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Market market(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  for(int x=-8;x<20;x++)for(int z=-8;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Market(l,s,e,center);
 }
 /** A workplace at x offset dx with its owned chest, and one resident of the profession working there. */
 private static ResidentEntity worker(GameTestHelper h,Market m,String type,Profession role,int dx,Resident.Life life){
  var b=new Settlement.Building(Settlement.childId(m.s.id(),"building/"+type),type,dx,0,0);m.s.addBuilding(b);m.l.setBlock(m.center.offset(dx+1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var r=new Resident(UUID.randomUUID(),life,true,null,null,-1);m.s.admit(r,m.s.homes().iterator().next().id());if(role!=null)m.s.assign(r.id(),role,b.id());
  var npc=VillageAstra.RESIDENT.get().create(m.l);npc.bind(m.s.id(),m.s.resident(r.id()));npc.setNoAi(true);npc.moveTo(m.center.getX()+dx+2.5,m.center.getY()+1,m.center.getZ()+2.5,0,0);m.l.addFreshEntity(npc);return npc;
 }
 private static Container chest(Market m,ResidentEntity npc){return LogisticsRoutes.chest(m.l,m.e,m.s.workplace(npc.getUUID()));}
 private static ServerPlayer player(Market m,ResidentEntity npc,String name){var p=FakePlayerFactory.get(m.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(npc.getX()+1,npc.getY(),npc.getZ());return p;}
 private static int coins(ServerPlayer p){return p.getInventory().countItem(VillageAstra.ZINDBO.get());}
 private static long reputation(Market m,ServerPlayer p){return PropertyLedger.get(m.l.getServer()).roll(m.s.id()).account(p.getUUID()).score();}
 private static int accepted(ServerPlayer p,ResidentEntity npc,Item item){return Trade.offer(p,npc).buys().stream().filter(x->x.item()==item).mapToInt(Trade.Line::count).sum();}
 @GameTest(template="empty",timeoutTicks=100) public static void saleIsPaidOnceAndDonationEarnsMoreReputation(GameTestHelper h){
  var m=market(h);var miller=worker(h,m,"mill",Profession.MILLER,0,Resident.Life.ADULT);var seller=player(m,miller,"TradeSeller");var donor=player(m,miller,"TradeDonor");
  seller.getInventory().add(new ItemStack(Items.WHEAT,64));donor.getInventory().add(new ItemStack(Items.WHEAT,16));
  h.assertTrue(accepted(seller,miller,Items.WHEAT)==32,"Miller accepts only its 32 wheat stock target: "+accepted(seller,miller,Items.WHEAT));
  var token=UUID.randomUUID();h.assertTrue(Trade.order(seller,token,miller.getUUID(),Trade.SELL,"minecraft:wheat",64).equals("stale"),"More than the useful amount is not silently accepted");
  h.assertTrue(Trade.order(seller,token,miller.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("ok"),"Useful sale confirmed");
  h.assertTrue(Trade.order(seller,token,miller.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("replay")&&coins(seller)==1&&seller.getInventory().countItem(Items.WHEAT)==48&&chest(m,miller).countItem(Items.WHEAT)==16,"Replayed token neither mints coins nor moves wheat twice");
  long saleReputation=reputation(m,seller);
  h.assertTrue(Trade.order(donor,UUID.randomUUID(),miller.getUUID(),Trade.DONATE,"minecraft:wheat",16).equals("ok")&&coins(donor)==0,"Donation confirmed without coins");
  h.assertTrue(reputation(m,donor)>saleReputation&&saleReputation>0,"Donation of the same useful amount earns more reputation: "+reputation(m,donor)+" > "+saleReputation);
  int left=accepted(seller,miller,Items.WHEAT);String late=Trade.order(seller,UUID.randomUUID(),miller.getUUID(),Trade.DONATE,"minecraft:wheat",16);
  h.assertTrue(left==0&&(late.equals("stale")||late.equals("refused:no_goods"))&&reputation(m,seller)==saleReputation,"Unneeded donations give no reputation: accepted="+left+" result="+late+" rep="+reputation(m,seller)+"/"+saleReputation+" chest="+chest(m,miller).countItem(Items.WHEAT)+" wants="+Workshops.wants(m.l,m.e));
  var deal=TradeLedger.get(m.l.getServer()).deal(token);
  h.assertTrue(deal.getInt("count")==16&&deal.getInt("price")==1&&deal.getInt("per")==16&&deal.getInt("quota")==32&&deal.getLong("paid")==1&&deal.getInt("stock")==16&&deal.getLong("reputation")==saleReputation&&deal.getUUID("village").equals(m.s.id()),"Deal records id, parties, item, count, price, quota, stock and reputation");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void purchaseEarnsReputationButResaleDoesNotEarnItAgain(GameTestHelper h){
  var m=market(h);var miller=worker(h,m,"mill",Profession.MILLER,0,Resident.Life.ADULT);var baker=worker(h,m,"restaurant",Profession.BAKER,8,Resident.Life.ADULT);
  chest(m,miller).setItem(0,new ItemStack(VillageAstra.FLOUR.get(),64));var p=player(m,miller,"TradeLooper");p.getInventory().add(new ItemStack(VillageAstra.ZINDBO.get(),10));
  var offer=Trade.offer(p,miller).sells().stream().filter(x->x.item()==VillageAstra.FLOUR.get()).findFirst().orElseThrow();
  h.assertTrue(Trade.order(p,UUID.randomUUID(),miller.getUUID(),Trade.BUY,"villageastra:flour",16).equals("ok")&&coins(p)==10-(int)Trade.cost(offer,16)&&p.getInventory().countItem(VillageAstra.FLOUR.get())==16,"Bought 16 flour for "+Trade.cost(offer,16));
  p.setPos(baker.getX()+1,baker.getY(),baker.getZ());
  h.assertTrue(Trade.order(p,UUID.randomUUID(),baker.getUUID(),Trade.SELL,"villageastra:flour",16).equals("ok"),"Baker really needs flour");
  h.assertTrue(coins(p)<10&&reputation(m,p)==Trade.cost(offer,16),"Purchase earns its paid value, resale adds no duplicate reward: "+reputation(m,p));
  h.assertTrue(Trade.order(p,UUID.randomUUID(),baker.getUUID(),Trade.BUY,"minecraft:bread",1).equals("stale"),"Nothing is sold that the workplace does not hold");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void fullInventoryKeepsCoinsOwedAndValuablesHaveAQuota(GameTestHelper h){
  var m=market(h);var mayor=worker(h,m,"town_hall",Profession.MAYOR,0,Resident.Life.ADULT);var p=player(m,mayor,"TradeFull");
  p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,10));for(int i=1;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.DIRT,64));
  h.assertTrue(accepted(p,mayor,Items.DIAMOND)==8,"Diamonds have a finite target of 8");
  h.assertTrue(Trade.order(p,UUID.randomUUID(),mayor.getUUID(),Trade.SELL,"minecraft:diamond",9).equals("stale"),"Beyond the valuables quota is refused");
  h.assertTrue(Trade.order(p,UUID.randomUUID(),mayor.getUUID(),Trade.SELL,"minecraft:diamond",8).equals("ok"),"Quota sale confirmed");
  var ledger=TradeLedger.get(m.l.getServer());long owed=ledger.owed(p.getUUID());
  h.assertTrue(coins(p)+owed==128&&owed>0,"Coins that do not fit are owed, not lost: held "+coins(p)+" owed "+owed);
  h.assertTrue(accepted(p,mayor,Items.DIAMOND)==0,"Quota filled");
  p.getInventory().setItem(5,ItemStack.EMPTY);p.getInventory().setItem(6,ItemStack.EMPTY);var claim=UUID.randomUUID();
  h.assertTrue(Trade.order(p,claim,mayor.getUUID(),Trade.CLAIM,"minecraft:air",0).equals("ok")&&Trade.order(p,claim,mayor.getUUID(),Trade.CLAIM,"minecraft:air",0).equals("replay")&&coins(p)==128&&ledger.owed(p.getUUID())==0,"Claim pays the debt exactly once");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void emptyCardsExplainWhy(GameTestHelper h){
  var m=market(h);var child=worker(h,m,"farm",null,0,Resident.Life.CHILD);var idle=worker(h,m,"forester",null,6,Resident.Life.ADULT);var farmer=worker(h,m,"mine",Profession.MINER,12,Resident.Life.ADULT);
  var p=player(m,child,"TradeReasons");
  h.assertTrue(Trade.offer(p,child).reason().equals("child")&&Trade.view(p,child).getString("reason").equals("child"),"Child card has a reason: "+Trade.offer(p,child).reason());
  p.setPos(idle.getX()+1,idle.getY(),idle.getZ());h.assertTrue(Trade.offer(p,idle).reason().equals("unemployed"),"Unemployed card has a reason");
  p.setPos(farmer.getX()+1,farmer.getY(),farmer.getZ());h.assertTrue(Trade.offer(p,farmer).reason().isEmpty()&&!Trade.offer(p,farmer).buys().isEmpty(),"Miner buys pickaxes and props");
  p.setGameMode(GameType.CREATIVE);h.assertTrue(!Trade.offer(p,farmer).reason().equals("creative"),"Creative players may use the same finite market");p.setGameMode(GameType.SURVIVAL);
  PropertyLedger.get(m.l.getServer()).theft(m.s.id(),p.getUUID(),4);
  h.assertTrue(Trade.offer(p,farmer).reason().equals("distrust")&&Trade.order(p,UUID.randomUUID(),farmer.getUUID(),Trade.SELL,"minecraft:torch",32).equals("refused:distrust"),"Negative reputation refuses trade");
  var worn=new ItemStack(Items.IRON_HOE);worn.setDamageValue(3);var named=new ItemStack(Items.WHEAT);named.setHoverName(net.minecraft.network.chat.Component.literal("x"));
  h.assertTrue(Trade.plain(new ItemStack(Items.IRON_HOE))&&Trade.plain(new ItemStack(Items.WHEAT))&&!Trade.plain(worn)&&!Trade.plain(named),"Fresh tools are plain goods; worn or renamed items are not");
  h.succeed();
 }
 /** OWNER_REQUEST 9.3: two people working in one workshop sell from its one chest — the same stock is never sold twice. */
 @GameTest(template="empty",timeoutTicks=100) public static void twoWorkersOfOneWorkshopNeverSellOneStockTwice(GameTestHelper h){
  var m=market(h);var first=worker(h,m,"mill",Profession.MILLER,0,Resident.Life.ADULT);
  var mill=m.s.workplace(first.getUUID());var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);m.s.admit(r,m.s.homes().iterator().next().id());m.s.assign(r.id(),Profession.MILLER,mill.id());
  var second=VillageAstra.RESIDENT.get().create(m.l);second.bind(m.s.id(),m.s.resident(r.id()));second.setNoAi(true);second.moveTo(first.getX()+1,first.getY(),first.getZ(),0,0);m.l.addFreshEntity(second);
  chest(m,first).setItem(0,new ItemStack(VillageAstra.FLOUR.get(),16));
  var p=player(m,first,"TradeTwoCounters");p.getInventory().add(new ItemStack(VillageAstra.ZINDBO.get(),30));
  int fromFirst=Trade.offer(p,first).sells().stream().filter(x->x.item()==VillageAstra.FLOUR.get()).mapToInt(Trade.Line::count).sum();
  int fromSecond=Trade.offer(p,second).sells().stream().filter(x->x.item()==VillageAstra.FLOUR.get()).mapToInt(Trade.Line::count).sum();
  h.assertTrue(fromFirst>0&&fromFirst==fromSecond,"Both millers offer the same one stock: "+fromFirst+"/"+fromSecond);
  h.assertTrue(Trade.order(p,UUID.randomUUID(),first.getUUID(),Trade.BUY,"villageastra:flour",fromFirst).equals("ok"),"The first miller sells the stock");
  int coins=coins(p),flour=p.getInventory().countItem(VillageAstra.FLOUR.get());
  String again=Trade.order(p,UUID.randomUUID(),second.getUUID(),Trade.BUY,"villageastra:flour",fromFirst);
  h.assertTrue(!again.equals("ok")&&coins(p)==coins&&p.getInventory().countItem(VillageAstra.FLOUR.get())==flour&&chest(m,first).countItem(VillageAstra.FLOUR.get())==16-fromFirst,"The second miller cannot sell what is already sold: "+again);
  h.succeed();
 }
}
