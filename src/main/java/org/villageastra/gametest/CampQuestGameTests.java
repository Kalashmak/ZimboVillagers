package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-041: expeditions report real places, camps are really built there, cargo is handed over once and a rescue pays for the people who really arrived. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CampQuestGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-5;x<30;x++)for(int z=-5;z<24;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,center,office);
 }
 // AD-097: the travellers' friend of these tests has already earned the trust a rescue asks for.
 private static ServerPlayer player(Town t,String name){var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Quests.RESCUE));p.setPos(t.center.getX()+2,t.center.getY()+1,t.center.getZ()+2);return p;}
 private static BlockPos site(Town t){return t.center.offset(20,0,16);}
 private static void lead(Town t,long now){
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,4));chest.setItem(1,new ItemStack(Items.BREAD,4));
  var at=new BlockPos(site(t).getX(),t.center.getY()+1,site(t).getZ());
  var report=Expeditions.report(t.l,t.e,t.office,at,0,now);if(report==null)throw new GameTestAssertException("The flat clearing was not reported as a camp lead");
 }
 @GameTest(template="empty",timeoutTicks=140) public static void expeditionsReportRealPlacesForRealSupplies(GameTestHelper h){
  var t=town(h);var chest=LogisticsRoutes.chest(t.l,t.e,t.office);var at=new BlockPos(site(t).getX(),t.center.getY()+1,site(t).getZ());
  h.assertTrue(Expeditions.report(t.l,t.e,t.office,at,0,100)==null,"No supplies, no expedition report");
  chest.setItem(0,new ItemStack(Items.PAPER,2));chest.setItem(1,new ItemStack(Items.BREAD,2));
  h.assertTrue(Expeditions.inspect(t.l,at).equals(Expeditions.CAMP),"A real open clearing is a camp lead");
  var lead=Expeditions.report(t.l,t.e,t.office,at,0,200);
  h.assertTrue(lead!=null&&BlockPos.of(lead.getLong("pos")).equals(at)&&chest.countItem(Items.PAPER)==1&&chest.countItem(Items.BREAD)==1,"One paper and one ration per report: "+chest.countItem(Items.PAPER)+"/"+chest.countItem(Items.BREAD));
  h.assertTrue(Expeditions.report(t.l,t.e,t.office,at,0,300)!=null&&chest.countItem(Items.PAPER)==1,"Reporting the same sector again spends nothing");
  for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()+6,at.getZ()+dz),Blocks.STONE.defaultBlockState(),3);
  h.assertTrue(Expeditions.nextSector(t.l,t.s.id())==1,"The reported sector is not scouted again");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=140) public static void cargoQuestBuildsACampAndIsHandedOverOnce(GameTestHelper h){
  var t=town(h);lead(t,400);var p=player(t,"CampCarrier");
  var q=Quests.postDistant(t.l,t.e,Quests.CARGO,500);h.assertTrue(q!=null&&q.getInt("target")==16&&q.contains("site"),"Cargo quest points at the scouted place: "+q);
  var camp=Camps.camp(t.l,q.getUUID("camp"));var chestPos=BlockPos.of(camp.getLong("chest"));
  h.assertTrue(t.l.getBlockState(BlockPos.of(camp.getLong("pos")).above()).is(Blocks.CAMPFIRE)&&t.l.getBlockState(chestPos).is(Blocks.CHEST),"The camp is really built");
  var cargo=(net.minecraft.world.Container)t.l.getBlockEntity(chestPos);var item=cargo.getItem(0).copy();
  h.assertTrue(item.getCount()==16&&Camps.key(item).equals(q.getString("item")),"The cargo really lies in the camp chest: "+item);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  h.assertTrue(Quests.deliver(p,t.s.id(),q.getUUID("id")).equals("empty"),"Nothing is handed over without the cargo");
  cargo.setItem(0,ItemStack.EMPTY);p.getInventory().add(item.copy());
  p.setPos(t.center.getX()+40,t.center.getY()+1,t.center.getZ());
  h.assertTrue(Quests.deliver(p,t.s.id(),q.getUUID("id")).equals("far"),"The cargo is handed over at the village, not anywhere");
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+4.5);
  h.assertTrue(Quests.deliver(p,t.s.id(),q.getUUID("id")).equals("ok"),"Handed over");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));var hall=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&hall.countItem(item.getItem())==16&&p.getInventory().countItem(item.getItem())==0,"The goods really moved into the village stock");
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),"Reward paid exactly once");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=140) public static void rescuePaysForThePeopleWhoReallyArrivedAndGuestsWaitForABed(GameTestHelper h){
  var t=town(h);lead(t,600);var p=player(t,"CampRescuer");
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,700);h.assertTrue(q!=null&&q.getInt("target")==3,"Rescue quest for three travellers: "+q);
  var camp=Camps.camp(t.l,q.getUUID("camp"));var people=new ArrayList<UUID>();for(var raw:camp.getList("travellers",10))people.add(((net.minecraft.nbt.CompoundTag)raw).getUUID("id"));
  h.assertTrue(people.size()==3&&people.stream().allMatch(id->t.l.getEntity(id) instanceof ResidentEntity npc&&Camps.companion(npc,q.getUUID("id"))),"Three real travellers wait at the camp");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  for(int i=0;i<2;i++){var npc=(ResidentEntity)t.l.getEntity(people.get(i));npc.moveTo(t.center.getX()+2.5+i,t.center.getY()+1,t.center.getZ()+2.5,0,0);}
  Quests.companions(t.l,t.e,800);
  var partial=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(partial.getInt("progress")==2&&partial.getString("state").equals(Quests.TAKEN),"Only the travellers who really arrived count: "+partial.getInt("progress"));
  h.assertTrue(Camps.guest(t.l,people.get(0))!=null&&Camps.guest(t.l,people.get(0)).getString("state").equals("waiting"),"An arrived traveller waits for a bed, not for the reward");
  ((ResidentEntity)t.l.getEntity(people.get(2))).discard();
  Quests.companions(t.l,t.e,900);
  h.assertTrue(Quests.complete(p,t.s.id(),q.getUUID("id")),"A rescue with survivors can be closed");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")*2/3,"A partial rescue pays for two of three: "+p.getInventory().countItem(VillageAstra.ZINDBO.get()));
  var guest=(ResidentEntity)t.l.getEntity(people.get(0));
  h.assertTrue(Camps.tickGuest(t.l,guest,1000).equals("housed")&&t.s.resident(guest.getUUID())!=null&&guest.settlementId().equals(t.s.id()),"A free bed turns the guest into the same resident");
  var second=(ResidentEntity)t.l.getEntity(people.get(1));
  h.assertTrue(Camps.tickGuest(t.l,second,1100).equals("housed"),"The second arrival also gets a bed while the home has room");
  h.succeed();
 }
}
