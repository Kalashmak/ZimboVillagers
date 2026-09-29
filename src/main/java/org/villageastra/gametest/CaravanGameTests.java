package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-039: a contract moves real goods once, pays once, keeps one identity for the caravaneer and records losses on the road. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaravanGameTests {
 private record Pair(net.minecraft.server.level.ServerLevel l,SettlementData.Entry source,SettlementData.Entry destination,ResidentEntity caravaneer){}
 private static SettlementData.Entry village(GameTestHelper h,BlockPos local){
  var l=h.getLevel();var center=h.absolutePos(local);var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return e;
 }
 private static Pair pair(GameTestHelper h){
  var l=h.getLevel();var source=village(h,new BlockPos(2,3,2));var destination=village(h,new BlockPos(30,3,26));
  var s=source.settlement();var yard=new Settlement.Building(Settlement.childId(s.id(),"building/caravan"),"caravan",4,0,-8);s.addBuilding(yard);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,s.homes().iterator().next().id());s.assign(r.id(),Profession.CARAVANEER,yard.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);npc.moveTo(source.center().getX()+3.5,source.center().getY(),source.center().getZ()+3.5,0,0);l.addFreshEntity(npc);
  LogisticsRoutes.chest(l,source,Workshops.hall(source)).setItem(0,new ItemStack(Items.BREAD,64));return new Pair(l,source,destination,npc);
 }
 private static int bread(Pair p,SettlementData.Entry e){return LogisticsRoutes.chest(p.l,e,Workshops.hall(e)).countItem(Items.BREAD);}
 @GameTest(template="empty",timeoutTicks=200) public static void contractDeliversGoodsOnceAndPaysOnce(GameTestHelper h){
  var p=pair(h);var server=p.l.getServer();long now=1000;
  var snap=Caravans.snapshot(p.l,p.source,now);h.assertTrue(snap.getCompound("items").getInt("minecraft:bread")>0,"Source surplus snapshot: "+snap);
  var t=Caravans.propose(p.l,p.destination,p.source,now);h.assertTrue(t!=null&&t.getString("item").equals("minecraft:bread")&&t.getInt("count")==16&&t.getUUID("caravaneer").equals(p.caravaneer.getUUID()),"Destination bread demand matched to the source: "+t);
  h.assertTrue(Caravans.propose(p.l,p.destination,p.source,now+1)==null,"No duplicate contract for the same demand");
  var id=t.getUUID("id");Caravans.tick(server,now+=20);h.assertTrue(Caravans.contract(server,id).getString("state").equals(Caravans.ACCEPTED),"Accepted");
  Caravans.tick(server,now+=20);var c=Caravans.contract(server,id);
  h.assertTrue(c.getString("state").equals(Caravans.TRANSIT)&&bread(p,p.source)==48&&!p.caravaneer.isAlive(),"Goods secured once and the caravaneer's body left the source: "+c.getString("state")+" bread="+bread(p,p.source));
  var copy=VillageAstra.RESIDENT.get().create(p.l);copy.setUUID(p.caravaneer.getUUID());h.assertTrue(Caravans.stale(server,copy),"A saved body of the travelling caravaneer is stale and cannot rejoin");
  for(int i=0;i<40&&!Caravans.contract(server,id).getString("state").equals(Caravans.RETURNING);i++)Caravans.tick(server,now+=20);
  c=Caravans.contract(server,id);var ledger=TradeLedger.get(server);
  h.assertTrue(c.getString("state").equals(Caravans.RETURNING)&&bread(p,p.destination)==16&&c.getInt("delivered")==16&&c.getLong("paid")==4,"Delivered 16 bread for 4 coins: "+c);
  long sourceTreasury=ledger.treasury(p.source.settlement().id());h.assertTrue(sourceTreasury==4&&c.getLong("emitted")==4,"Seller paid once; the empty buyer treasury is covered by a recorded one-time emission");
  for(int i=0;i<40&&!Caravans.contract(server,id).getString("state").equals(Caravans.CLOSED);i++)Caravans.tick(server,now+=20);
  c=Caravans.contract(server,id);h.assertTrue(c.getString("state").equals(Caravans.CLOSED)&&ledger.treasury(p.source.settlement().id())==4&&bread(p,p.destination)==16&&c.getBoolean("needsEntity"),"Return trip closes without paying or delivering again");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void deathOnTheRoadDropsCargoAndRecordsLoss(GameTestHelper h){
  var p=pair(h);var server=p.l.getServer();long now=5000;Caravans.snapshot(p.l,p.source,now);var id=Caravans.propose(p.l,p.destination,p.source,now).getUUID("id");
  Caravans.tick(server,now+=20);Caravans.tick(server,now+=20);Caravans.tick(server,now+=20);Caravans.tick(server,now+=20);
  var t=Caravans.contract(server,id);var at=Caravans.position(p.l,t,t.getDouble("progress"));var body=Caravans.materialize(p.l,t,at);
  h.assertTrue(body!=null&&body.getUUID().equals(p.caravaneer.getUUID())&&!Caravans.stale(server,body)&&Caravans.contract(server,id).getBoolean("materialized"),"The travelling identity materializes once near an observer");
  body.kill();
  var c=Caravans.contract(server,id);int dropped=p.l.getEntitiesOfClass(ItemEntity.class,body.getBoundingBox().inflate(3),e->e.getItem().is(Items.BREAD)).stream().mapToInt(e->e.getItem().getCount()).sum();
  h.assertTrue(c.getInt("lost")==16&&dropped==16&&c.getList("cargo",10).isEmpty()&&c.getString("state").equals(Caravans.CLOSED)&&c.getCompound("stamps").contains(Caravans.LOST),"Cargo fell on the road and the loss is recorded: lost="+c.getInt("lost")+" dropped="+dropped);
  h.assertTrue(bread(p,p.destination)==0&&TradeLedger.get(server).treasury(p.source.settlement().id())==0,"No delivery credit and no payment for lost goods");
  h.assertTrue(!p.source.settlement().resident(p.caravaneer.getUUID()).alive(),"The caravaneer really died");
  for(var e:p.l.getEntitiesOfClass(ItemEntity.class,body.getBoundingBox().inflate(3)))e.discard();
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void migrationMovesTheSameResidentWithoutAClone(GameTestHelper h){
  var l=h.getLevel();var server=l.getServer();var source=village(h,new BlockPos(2,3,2));var destination=village(h,new BlockPos(30,3,26));long now=9000;
  var migrant=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);source.settlement().restoreResident(migrant);
  var body=VillageAstra.RESIDENT.get().create(l);body.bind(source.settlement().id(),migrant);body.setNoAi(true);body.moveTo(source.center().getX()+2.5,source.center().getY(),source.center().getZ()+2.5,0,0);l.addFreshEntity(body);
  var bed=destination.settlement().homes().iterator().next();
  var t=Caravans.proposeMigration(l,source,destination,now);h.assertTrue(t!=null&&t.getUUID("caravaneer").equals(migrant.id())&&t.getUUID("home").equals(bed.id()),"Homeless adult matched to a free bed: "+t);
  h.assertTrue(Caravans.proposeMigration(l,source,destination,now+1)==null,"The same resident is not sent twice");
  var id=t.getUUID("id");for(int i=0;i<40&&!Caravans.contract(server,id).getString("state").equals(Caravans.CLOSED);i++)Caravans.tick(server,now+=20);
  var c=Caravans.contract(server,id);var moved=destination.settlement().resident(migrant.id());
  h.assertTrue(c.getBoolean("transferred")&&source.settlement().resident(migrant.id())==null&&moved==migrant&&moved.educated()&&bed.id().equals(moved.home()),"The same resident with education and a bed now lives in the destination");
  h.assertTrue(!body.isAlive()&&Caravans.stale(server,body),"The old body cannot rejoin anywhere");
  h.succeed();
 }
}
