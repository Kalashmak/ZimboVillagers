package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
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
/** AD-088: a crop or sapling the village cannot plant yet is asked for by a quest; real seeds handed over at the stock open it once and pay
 *  once, and a sapling of a far country comes with a chart whose cross stands in that country. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CropQuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-4;x<12;x++)for(int z=-4;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,center);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aCropQuestOpensItsKindOnceTheSeedsLieInTheStock(GameTestHelper h){
  var t=town(h);var p=player(t,"CropBringer");
  h.assertTrue(!CropUnlocks.unlocked(t.l,t.e,"minecraft:carrot")&&CropUnlocks.unlocked(t.l,t.e,"minecraft:wheat_seeds"),"Wheat is known from the first day, carrots are not");
  var q=CropQuests.post(t.l,t.e,4800);
  h.assertTrue(q!=null&&q.getString("item").equals("minecraft:carrot")&&q.getInt("target")==16,"The first kind the village lacks is asked for: "+q);
  h.assertTrue(CropQuests.post(t.l,t.e,9600)==null,"One crop quest stands on the board at a time");
  h.assertTrue(q.getLong("coins")==Quests.coins(CropQuests.CROP)+Quests.value(Items.CARROT,16)&&q.getLong("reputation")==Quests.reputation(CropQuests.CROP),
   "It pays the time like trade does, and the carrots at the village price: "+q.getLong("coins")+"/"+q.getLong("reputation"));
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  p.getInventory().add(new ItemStack(Items.CARROT,10));
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"Part of the seeds handed over");
  h.assertTrue(!CropUnlocks.unlocked(t.l,t.e,"minecraft:carrot"),"Ten carrots of sixteen open nothing yet");
  p.getInventory().add(new ItemStack(Items.CARROT,8));
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"The rest handed over");
  var stock=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
  h.assertTrue(stock.countItem(Items.CARROT)==16&&p.getInventory().countItem(Items.CARROT)==2,"Exactly sixteen carrots moved into the stock: "+stock.countItem(Items.CARROT));
  h.assertTrue(CropUnlocks.unlocked(t.l,t.e,"minecraft:carrot"),"The village may now plant carrots");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),"Paid exactly once");
  var next=CropQuests.post(t.l,t.e,14400);
  h.assertTrue(next!=null&&next.getString("item").equals("minecraft:potato"),"The board moves on to the next kind: "+next);
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aSaplingQuestPointsAtItsOwnCountry(GameTestHelper h){
  var t=town(h);var p=player(t,"GroveSeeker");
  for(var id:List.of("minecraft:carrot","minecraft:potato","minecraft:beetroot_seeds","minecraft:sugar_cane"))CropUnlocks.unlock(t.l,t.e,id,"quest");
  var q=CropQuests.post(t.l,t.e,4800);
  h.assertTrue(q!=null&&q.getString("item").equals("minecraft:birch_sapling")&&q.getInt("target")==4,"With the crops open the saplings come next: "+q);
  if(q.contains("site")){
   var at=BlockPos.of(q.getLong("site"));var biome=t.l.getBiome(at);
   h.assertTrue(q.getString("kind").equals("grove")&&(biome.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST)||biome.is(net.minecraft.world.level.biome.Biomes.OLD_GROWTH_BIRCH_FOREST)),
    "The cross stands in a birch forest: "+biome.unwrapKey()+" at "+at.toShortString());
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   var chart=p.getInventory().items.stream().filter(x->x.is(Items.FILLED_MAP)).findFirst().orElse(ItemStack.EMPTY);
   h.assertTrue(!chart.isEmpty(),"The sapling quest hands out a chart");
  }
  h.succeed();
 }
}
