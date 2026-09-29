package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** QUEST-002 (freight): the village sends for the load of a caravan that never came home. It asks only about a place it has really heard
 *  of; the stash out there really holds what is asked for; and only what is carried home to the stock counts — with the goods in hand but
 *  the stock far away, nothing is taken. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarFreightGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building stock){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-6;x<16;x++)for(int z=-6;z<16;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0));
  var stock=new Settlement.Building(Settlement.childId(s.id(),"building/warehouse"),"warehouse",4,0,0);s.addBuilding(stock);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // The stock its cargo is handed over into.
  l.setBlock(LogisticsRoutes.position(e,stock),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  return new Town(l,s,e,center,stock);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Far.FREIGHT));
  p.setPos(t.center.getX()+.5,t.center.getY()+1,t.center.getZ()+.5);return p;
 }
 /** Word of a far place of this kind, written down the way Far.learn writes it. */
 private static BlockPos word(Town t,String kind,long now){
  var at=t.center.offset(2600,0,-1800);
  Expeditions.addLead(t.l,t.s.id(),at,Far.KIND,Far.FIRST_SECTOR,now);
  var record=Expeditions.record(t.l,t.s.id());
  for(var raw:record.getList("leads",Tag.TAG_COMPOUND)){var lead=(CompoundTag)raw;
   if(lead.getInt("sector")==Far.FIRST_SECTOR){lead.putString("far_kind",kind);lead.putInt("away",Far.away(t.e,at));}}
  Expeditions.save(t.l,t.s.id(),record);return at;
 }
 private static BlockPos stockAt(Town t){return LogisticsRoutes.position(t.e,t.stock);}

 @GameTest(template="empty",timeoutTicks=200) public static void theVillageSendsOnlyForALoadItHasHeardOf(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(Far.postFreight(t.l,t.e,1000)==null,"A village that has heard of nowhere sends for nothing");
   word(t,"far_badlands",1000);
   var q=Far.postFreight(t.l,t.e,1100);
   h.assertTrue(q!=null&&q.getString("template").equals(Far.FREIGHT),"With word of a place it sends for its load: "+q);
   h.assertTrue(q.getString("item").equals(BuiltInRegistries.ITEM.getKey(Items.GOLD_INGOT).toString()),"What that country's caravan carried: "+q.getString("item"));
   h.assertTrue(q.getInt("target")==(int)Quests.setting(Far.FREIGHT,"count"),"as much as the village asks for: "+q.getInt("target"));
   h.assertTrue(q.getLong("coins")>Quests.value(Items.GOLD_INGOT,q.getInt("target")),"paid for the goods and the road both: "+q.getLong("coins"));
   h.assertTrue(q.getBoolean("pending")&&q.getString("kind").equals("far_badlands"),"The crates are built out there, once somebody is there to see them");
   h.assertTrue(Far.postFreight(t.l,t.e,1200)==null,"One such errand at a time");
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theStashOutThereReallyHoldsTheLoad(GameTestHelper h){
  var t=town(h);var p=player(t,"Freighter");
  try{
   word(t,"far_wreck",2000);
   var q=Far.postFreight(t.l,t.e,2000);h.assertTrue(q!=null,"The card");
   var at=h.absolutePos(new BlockPos(6,3,40));
   for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){var g=at.offset(x,0,z);t.l.setBlock(g.below(),Blocks.STONE.defaultBlockState(),2);
    for(int up=0;up<8;up++)t.l.setBlock(g.above(up),Blocks.AIR.defaultBlockState(),2);}
   h.assertTrue(Chains.materialize(t.l,t.e,q.getUUID("id"),at,2010),"The stash is built");
   var site=QuestSites.site(t.l,Quests.root(q));
   h.assertTrue(site.getInt("load")==q.getInt("target"),"The crates hold what the card asks for: "+site.getInt("load"));
   int found=0;
   var origin=QuestSites.origin(site);
   for(int x=-6;x<=6;x++)for(int y=-2;y<=3;y++)for(int z=-6;z<=6;z++){
    if(t.l.getBlockEntity(origin.offset(x,y,z)) instanceof net.minecraft.world.Container box)
     for(int slot=0;slot<box.getContainerSize();slot++)if(box.getItem(slot).is(Items.COPPER_INGOT))found+=box.getItem(slot).getCount();}
   h.assertTrue(found==q.getInt("target"),"and they really stand there, in the crates: "+found+" of "+q.getInt("target"));
   var fresh=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
   h.assertTrue(!fresh.getBoolean("pending"),"The card knows its place is standing now");
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void onlyWhatIsCarriedHomeCounts(GameTestHelper h){
  var t=town(h);var p=player(t,"Carrier");
  try{
   word(t,"far_village",3000);
   var q=Far.postFreight(t.l,t.e,3000);h.assertTrue(q!=null,"The card");
   var id=q.getUUID("id");
   h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
   h.assertTrue(p.getInventory().items.stream().anyMatch(x->x.is(Items.FILLED_MAP)),"A chart of the place comes with it");
   var chest=stockAt(t);
   p.setPos(chest.getX()+.5,chest.getY(),chest.getZ()+1.5);
   h.assertTrue(Quests.handOver(p,t.s.id(),id).equals("empty"),"Nothing is handed over with empty hands");
   int target=q.getInt("target");
   p.getInventory().add(new ItemStack(Items.EMERALD,target));
   p.setPos(t.center.getX()+40.5,t.center.getY()+1,t.center.getZ()+40.5);
   h.assertTrue(Quests.handOver(p,t.s.id(),id).equals("far"),"The load is handed over at the stock, not wherever the player stands");
   p.setPos(chest.getX()+.5,chest.getY(),chest.getZ()+1.5);
   h.assertTrue(Quests.handOver(p,t.s.id(),id).equals("ok"),"At the stock it is taken");
   var done=Quests.quest(t.l,t.s.id(),id);
   h.assertTrue(done.getString("state").equals(Quests.DONE)&&done.getInt("progress")==target,"The whole load home finishes it: "+done.getString("state")+" "+done.getInt("progress"));
   h.assertTrue(LogisticsRoutes.chest(t.l,t.e,t.stock).countItem(Items.EMERALD)==target,"and the goods are really in the village's stock");
   h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==done.getLong("coins"),"paid what it promised: "+p.getInventory().countItem(VillageAstra.ZINDBO.get()));
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
}
