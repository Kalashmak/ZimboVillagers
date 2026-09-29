package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-150 «Серые в клетках»: the kennel's wolves are taken back from the catchers. The errand is asked for only by a village with a kennel
 *  and room in it; the camp really holds its cages with a grey in each; a wolf comes out only when its bars are broken, takes meat only
 *  from the one whose errand it is, and counts only when it really reached the kennel — where it becomes the village's, not a pet. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WolfRescueGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building yard,Settlement.Building kennel){}
 private static Town town(GameTestHelper h,boolean withKennel){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-6;x<16;x++)for(int z=-6;z<16;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",4,0,0);s.addBuilding(yard);
  // The keeper of the yard is the one who asks for the greys, so the village needs one.
  var keeper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
  s.admit(keeper,s.homes().iterator().next().id());s.assign(keeper.id(),Profession.LIVESTOCK_FARMER,yard.id());
  Settlement.Building kennel=null;
  if(withKennel){kennel=new Settlement.Building(Settlement.childId(s.id(),"building/kennel"),VillageWolves.TYPE,8,0,0);s.addBuilding(kennel);s.linkAnnex(kennel.id(),yard.id());}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center,yard,kennel);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  // The errand asks for trust the village gives for work done before it (AD-097): the test gives it outright.
  PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(WolfRescue.CAGES));
  p.setPos(t.center.getX()+.5,t.center.getY()+1,t.center.getZ()+.5);return p;
 }
 /** The camp built on the test's own ground, and the card that asks for it. */
 private static CompoundTag camp(GameTestHelper h,Town t,ServerPlayer p,long now){
  var q=WolfRescue.post(t.l,t.e,now);
  if(q==null)return null;
  // Ground of the test's own making: in the shared world of a whole run the land 40 blocks out is whatever
  // the tests before it left there, and a pad is built only on clear, level ground.
  var at=h.absolutePos(new BlockPos(6,3,40));
  for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++){var g=at.offset(x,0,z);
   t.l.setBlock(g.below(),Blocks.STONE.defaultBlockState(),2);
   for(int up=0;up<8;up++)t.l.setBlock(g.above(up),Blocks.AIR.defaultBlockState(),2);}
  for(var beast:t.l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(at).inflate(12)))beast.discard();
  h.assertTrue(org.villageastra.world.Chains.materialize(t.l,t.e,q.getUUID("id"),at,now),"The poachers' camp is built");
  return Quests.quest(t.l,t.s.id(),q.getUUID("id"));
 }
 private static CompoundTag site(Town t,CompoundTag q){return QuestSites.site(t.l,Quests.root(q));}
 private static void openCage(Town t,BlockPos middle){t.l.setBlock(middle.offset(1,0,0),Blocks.AIR.defaultBlockState(),3);}
 private static void clearBand(Town t,CompoundTag q){
  var camp=QuestSites.origin(site(t,q));
  for(var mob:t.l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(camp).inflate(24),
   m->m.getTags().contains(QuestSites.MOB)))mob.discard();}

 @GameTest(template="empty",timeoutTicks=200) public static void withoutAKennelWithRoomTheVillageAsksForNoGreys(GameTestHelper h){
  var t=town(h,false);
  try{
   h.assertTrue(WolfRescue.post(t.l,t.e,1000)==null,"A village with no kennel asks for no wolves");
   var with=town(h,true);
   try{
    var q=WolfRescue.post(with.l,with.e,1200);
    h.assertTrue(q!=null&&q.getInt("target")>0&&q.getInt("target")<=VillageWolves.CAPACITY,"A kennel with room asks: "+(q==null?"nothing":q.getInt("target")));
    h.assertTrue(q.hasUUID("giver"),"and it is the yard's keeper who asks, so it is offered in their own words (AD-149)");
    h.assertTrue(WolfRescue.post(with.l,with.e,1300)==null,"One such errand at a time");
   }finally{SettlementData.get(with.l.getServer()).remove(with.s.id());}
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theCampReallyHoldsItsCagesAndAGreyInEach(GameTestHelper h){
  var t=town(h,true);var p=player(t,"CageBreaker");
  try{
   var q=camp(h,t,p,2000);h.assertTrue(q!=null,"The card and its camp");
   var site=site(t,q);var cages=WolfSites.cages(site);
   h.assertTrue(cages.size()==q.getInt("target"),"A cage for every grey asked for: "+cages.size()+" of "+q.getInt("target"));
   h.assertTrue(WolfSites.caged(t.l,site).size()==cages.size(),"and a grey sitting in each: "+WolfSites.caged(t.l,site).size());
   for(var middle:cages)h.assertTrue(!WolfSites.opened(t.l,middle),"The bars are whole until somebody breaks them");
   h.assertTrue(WolfRescue.free(t.l,q).isEmpty(),"Nothing is free while the cages stand");
   openCage(t,cages.get(0));
   h.assertTrue(WolfSites.opened(t.l,cages.get(0)),"A broken bar is a way out");
   h.assertTrue(WolfRescue.free(t.l,q).size()==1,"and one grey is out: "+WolfRescue.free(t.l,q).size());
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theGreyIsTakenInAtTheKennelAndBecomesTheVillagesOwn(GameTestHelper h){
  var t=town(h,true);var p=player(t,"WolfFriend");
  try{
   var q=camp(h,t,p,3000);h.assertTrue(q!=null,"The card and its camp");
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   var site=site(t,q);clearBand(t,q);
   for(var middle:WolfSites.cages(site))openCage(t,middle);
   var greys=WolfRescue.free(t.l,q);h.assertTrue(!greys.isEmpty(),"The greys are out of their cages");
   var wolf=greys.get(0);
   // A grey standing at the kennel is still nothing to the village while it trusts nobody.
   var kennelAt=LogisticsRoutes.position(t.e,t.kennel);
   wolf.moveTo(kennelAt.getX()+.5,kennelAt.getY()+1,kennelAt.getZ()+.5,0,0);
   WolfRescue.step(t.l,t.e,Quests.quest(t.l,t.s.id(),q.getUUID("id")),p,3100);
   h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"A grey brought to the kennel is counted: "
     +Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress"));
   h.assertTrue(VillageWolves.wolves(t.l,t.e).contains(wolf.getUUID()),"and is written into the village's own pack");
   h.assertTrue(!wolf.isTame()&&wolf.getOwnerUUID()==null,"It is the village's wolf now, and nobody's pet");
   h.assertTrue(VillageWolves.village(wolf).equals(t.s.id()),"marked with its village");
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aGreyTakesMeatOnlyFromTheOneWhoseErrandItIs(GameTestHelper h){
  var t=town(h,true);var p=player(t,"Feeder");var other=player(t,"Stranger");
  try{
   var q=camp(h,t,p,4000);h.assertTrue(q!=null,"The card and its camp");
   var site=site(t,q);var middle=WolfSites.cages(site).get(0);
   var caged=WolfSites.caged(t.l,site).get(0);
   p.getInventory().add(new ItemStack(Items.BEEF,2));other.getInventory().add(new ItemStack(Items.BEEF,2));
   // Still behind bars: meat through the bars changes nothing.
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   feed(p,caged);
   h.assertTrue(!caged.isTame(),"A caged grey takes nothing");
   openCage(t,middle);
   feed(other,caged);
   h.assertTrue(!caged.isTame(),"and it is not the stranger's errand: "+caged.isTame());
   feed(p,caged);
   h.assertTrue(caged.isTame()&&p.getUUID().equals(caged.getOwnerUUID()),"but it takes meat from the one who asked for it");
   h.assertTrue(p.getInventory().countItem(Items.BEEF)==1,"and eats exactly one piece: "+p.getInventory().countItem(Items.BEEF));
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 private static void feed(ServerPlayer p,Wolf wolf){
  p.setPos(wolf.getX(),wolf.getY(),wolf.getZ()+1);
  var event=new net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract(p,net.minecraft.world.InteractionHand.MAIN_HAND,wolf);
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
 }
 @GameTest(template="empty",timeoutTicks=300) public static void withEveryGreyGoneTheErrandIsNotLeftHanging(GameTestHelper h){
  var t=town(h,true);var p=player(t,"Unlucky");
  try{
   var q=camp(h,t,p,5000);h.assertTrue(q!=null,"The card and its camp");
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   var site=site(t,q);clearBand(t,q);
   for(var middle:WolfSites.cages(site))openCage(t,middle);
   for(var wolf:WolfRescue.free(t.l,q))wolf.discard();
   WolfRescue.step(t.l,t.e,Quests.quest(t.l,t.s.id(),q.getUUID("id")),p,5100);
   var closed=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
   h.assertTrue(closed.getString("state").equals(Quests.FAILED)&&closed.getString("reason").equals("wolves_lost"),
     "With no grey left the errand is failed, and says why: "+closed.getString("state")+" "+closed.getString("reason"));
   h.succeed();
  }finally{SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
}
