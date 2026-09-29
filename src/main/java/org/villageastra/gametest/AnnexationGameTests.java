package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-044: a peaceful annexation is earned by real deliveries, confirmed in person, paid from existing coins and finished by exactly one transfer of ownership. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AnnexationGameTests {
 private record Pair(net.minecraft.server.level.ServerLevel l,SettlementData.Entry buyer,SettlementData.Entry target,ResidentEntity caravaneer){}
 private static SettlementData.Entry village(GameTestHelper h,BlockPos local){
  var l=h.getLevel();var center=h.absolutePos(local);var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return e;
 }
 private static Pair pair(GameTestHelper h){
  var l=h.getLevel();var buyer=village(h,new BlockPos(2,3,2));var target=village(h,new BlockPos(30,3,26));
  var yard=new Settlement.Building(Settlement.childId(buyer.settlement().id(),"building/caravan"),"caravan",4,0,-8);buyer.settlement().addBuilding(yard);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);buyer.settlement().admit(r,buyer.settlement().homes().iterator().next().id());buyer.settlement().assign(r.id(),Profession.CARAVANEER,yard.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(buyer.settlement().id(),buyer.settlement().resident(r.id()));npc.setNoAi(true);npc.moveTo(buyer.center().getX()+3.5,buyer.center().getY(),buyer.center().getZ()+3.5,0,0);l.addFreshEntity(npc);
  LogisticsRoutes.chest(l,buyer,Workshops.hall(buyer)).setItem(0,new ItemStack(Items.BREAD,64));
  return new Pair(l,buyer,target,npc);
 }
 /** Runs real caravans until the delivered value reaches the threshold. */
 private static long supply(Pair p,long now){
  var server=p.l.getServer();
  for(int run=0;run<6&&Annexation.supplied(server,p.buyer.settlement().id(),p.target.settlement().id(),now)<Annexation.SUPPLY_VALUE;run++){
   LogisticsRoutes.chest(p.l,p.buyer,Workshops.hall(p.buyer)).setItem(0,new ItemStack(Items.BREAD,64));
   LogisticsRoutes.chest(p.l,p.target,Workshops.hall(p.target)).clearContent();
   Caravans.snapshot(p.l,p.buyer,now);var contract=Caravans.propose(p.l,p.target,p.buyer,now);if(contract==null)break;
   for(int i=0;i<60&&!Caravans.contract(server,contract.getUUID("id")).getString("state").equals(Caravans.CLOSED);i++)Caravans.tick(server,now+=20);
  }
  return Annexation.supplied(server,p.buyer.settlement().id(),p.target.settlement().id(),now);
 }
 @GameTest(template="empty",timeoutTicks=300) public static void peacefulAnnexationNeedsSuppliesCoinsAndConsent(GameTestHelper h){
  var p=pair(h);var server=p.l.getServer();long now=1000;
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).equals("supplies"),"Without real deliveries there is no offer");
  long supplied=supply(p,now);now+=4000;
  h.assertTrue(supplied>=Annexation.SUPPLY_VALUE,"Real caravans delivered enough: "+supplied);
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).equals("coins"),"The buyer must really own the coins");
  var ledger=TradeLedger.get(server);long price=Annexation.price(p.target);ledger.addTreasury(p.buyer.settlement().id(),price);
  long treasury=ledger.treasury(p.buyer.settlement().id());
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).isEmpty()&&ledger.treasury(p.buyer.settlement().id())==treasury-price,"The price is reserved from existing coins");
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).equals("pending"),"One offer at a time");
  var mayor=FakePlayerFactory.get(p.l,new GameProfile(UUID.randomUUID(),"AnnexMayor"));mayor.setGameMode(GameType.SURVIVAL);p.target.settlement().appointPlayerMayor(mayor.getUUID());
  h.assertTrue(Annexation.answer(server,p.target,null,true,now).equals("not_mayor"),"Being offline is not consent");
  h.assertTrue(Annexation.answer(server,p.target,mayor,true,now).isEmpty(),"The mayor confirms in person");
  h.assertTrue(Annexation.transfer(server,p.target,now).isEmpty()&&Annexation.owner(server,p.target.settlement().id()).equals(p.buyer.settlement().id()),"Ownership transferred");
  h.assertTrue(Annexation.transfer(server,p.target,now).equals("done")&&ledger.treasury(p.buyer.settlement().id())==treasury-price,"The transfer happens once and the coins are spent, not returned");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void transferArchivesTheOldRatingAndMovesManagement(GameTestHelper h){
  var p=pair(h);var server=p.l.getServer();long now=20000;supply(p,now);now+=4000;
  var ledger=TradeLedger.get(server);
  var buyerMayor=FakePlayerFactory.get(p.l,new GameProfile(UUID.randomUUID(),"AnnexBuyer"));p.buyer.settlement().appointPlayerMayor(buyerMayor.getUUID());
  var property=PropertyLedger.get(server);property.gift(p.target.settlement().id(),buyerMayor.getUUID(),40);
  var old=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);p.target.settlement().admit(old,p.target.settlement().homes().iterator().next().id());
  p.target.settlement().assign(old.id(),Profession.MAYOR,p.target.settlement().buildings().iterator().next().id());
  ledger.addTreasury(p.buyer.settlement().id(),Annexation.price(p.target));
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).isEmpty(),"Offer made");
  h.assertTrue(Annexation.answer(server,p.target,null,true,now).isEmpty(),"An NPC-led settlement answers through its own mayor");
  h.assertTrue(Annexation.transfer(server,p.target,now).isEmpty(),"Transferred");
  h.assertTrue(p.target.settlement().resident(old.id()).profession()==null,"The former NPC mayor becomes an ordinary resident");
  h.assertTrue(buyerMayor.getUUID().equals(p.target.settlement().governance().playerMayor()),"Management moves to the buyer's mayor");
  h.assertTrue(property.roll(p.target.settlement().id()).account(buyerMayor.getUUID()).score()==0&&Annexation.record(server,p.target.settlement().id()).contains("archivedRoll"),"The old rating is archived, not mixed into the new one");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void expiredOfferRefundsExactlyOnce(GameTestHelper h){
  var p=pair(h);var server=p.l.getServer();long now=40000;supply(p,now);now+=4000;
  var ledger=TradeLedger.get(server);long price=Annexation.price(p.target);ledger.addTreasury(p.buyer.settlement().id(),price);long treasury=ledger.treasury(p.buyer.settlement().id());
  var mayor=FakePlayerFactory.get(p.l,new GameProfile(UUID.randomUUID(),"AnnexSlow"));p.target.settlement().appointPlayerMayor(mayor.getUUID());
  h.assertTrue(Annexation.offer(server,p.buyer,p.target,now).isEmpty()&&ledger.treasury(p.buyer.settlement().id())==treasury-price,"Coins reserved");
  Annexation.tick(server,p.target,now+Annexation.CONFIRM_TICKS+1);
  h.assertTrue(ledger.treasury(p.buyer.settlement().id())==treasury,"An expired offer returns the reserve");
  Annexation.tick(server,p.target,now+Annexation.CONFIRM_TICKS+2);
  h.assertTrue(ledger.treasury(p.buyer.settlement().id())==treasury,"The refund happens once");
  h.assertTrue(Annexation.transfer(server,p.target,now).equals("not_accepted"),"A cancelled offer transfers nothing");
  h.succeed();
 }
}
