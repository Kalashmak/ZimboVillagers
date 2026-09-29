package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** QUEST-005: what happens after a rescued person is really home. The delivery is done and paid at the arrival; the hour of waiting for a bed
 *  is a separate outcome that can only add to it. The hour runs on the settlement's own clock, which stands still while nobody plays; a guest
 *  who was not housed in time leaves alive, blaming nobody; nothing they do afterwards — leaving, coming back, being killed by somebody else,
 *  a house the mayor ordered and did not finish, a siege at the gate — takes the finished delivery or its pay back. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office){}
 /** A village with a hall, the expedition office and no spare bed: its one home holds its own two people. */
 private static Town town(GameTestHelper h,boolean bed){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-5;x<30;x++)for(int z=-5;z<24;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,bed?4:1,true);s.addHome(home);
  if(!bed)s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),home.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center,office);
 }
 private static ServerPlayer player(Town t,String name,int trust){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  if(trust>0)PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),trust);
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 private static long score(Town t,ServerPlayer p){return PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score();}
 private static int coins(ServerPlayer p){return p.getInventory().countItem(VillageAstra.ZINDBO.get());}
 private static CompoundTag quest(Town t,UUID id){return Quests.quest(t.l,t.s.id(),id);}
 private static void lead(Town t,long now){
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER,4));chest.setItem(1,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD,4));
  var at=new BlockPos(t.center.getX()+20,t.center.getY()+1,t.center.getZ()+16);
  if(Expeditions.report(t.l,t.e,t.office,at,0,now)==null)throw new GameTestAssertException("The flat clearing was not reported as a camp lead");
 }
 private static List<ResidentEntity> travellers(Town t,CompoundTag q){
  var out=new ArrayList<ResidentEntity>();
  for(var raw:Camps.camp(t.l,q.getUUID("camp")).getList("travellers",Tag.TAG_COMPOUND))if(t.l.getEntity(((CompoundTag)raw).getUUID("id")) instanceof ResidentEntity npc)out.add(npc);
  return out;
 }
 /** A rescue taken, everybody walked home and the card settled: the state every one of these tests starts from. */
 private static CompoundTag delivered(Town t,ServerPlayer owner,long now){
  lead(t,now);var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,now+100);
  if(q==null)throw new GameTestAssertException("No rescue was posted");
  if(!Quests.take(owner,t.s.id(),q.getUUID("id")).equals("ok"))throw new GameTestAssertException("The rescue was not taken");
  var people=travellers(t,q);
  for(int i=0;i<people.size();i++)people.get(i).moveTo(t.center.getX()+2.5+i,t.center.getY()+1,t.center.getZ()+2.5,0,0);
  Quests.escorted(t.l,t.e,q.getUUID("id"),owner,now+200);
  return quest(t,q.getUUID("id"));
 }
 private static ResidentEntity guestOf(Town t,CompoundTag q){
  for(var raw:q.getList("arrived",Tag.TAG_INT_ARRAY)){var npc=t.l.getEntity(NbtUtils.loadUUID(raw));
   if(npc instanceof ResidentEntity r&&Camps.guest(t.l,r.getUUID())!=null&&Camps.guest(t.l,r.getUUID()).getString("state").equals("waiting"))return r;}
  throw new GameTestAssertException("No guest is waiting for a bed");
 }

 // ---------------------------------------------------------------- T104: the hour runs out with no bed
 @GameTest(template="empty",timeoutTicks=200) public static void aGuestNotHousedInTheHourLeavesAliveAndTheDeliveryStaysPaid(GameTestHelper h){
  var t=town(h,false);var owner=player(t,"Escort",Quests.trust(Quests.RESCUE));
  var q=delivered(t,owner,400);var id=q.getUUID("id");
  h.assertTrue(q.getString("state").equals(Quests.DONE),"The delivery is done at the arrival: "+q.getString("state"));
  long paid=coins(owner),earned=score(t,owner);h.assertTrue(paid>0&&earned>Quests.trust(Quests.RESCUE),"It was paid: "+paid+" coins, "+earned+" reputation");
  var guest=guestOf(t,q);var record=Camps.guest(t.l,guest.getUUID());
  h.assertTrue(record.getLong("deadline")-record.getLong("arrived")==Camps.GUEST_WAIT,"The guest waits its hour: "+(record.getLong("deadline")-record.getLong("arrived")));
  h.assertTrue(Camps.tickGuest(t.l,guest,record.getLong("deadline")-1).equals("waiting"),"A minute before the hour it still waits");
  h.assertTrue(Camps.tickGuest(t.l,guest,record.getLong("deadline")).equals("departed")&&!guest.isAlive(),"The hour out, the guest goes away");
  h.assertTrue(!Camps.guest(t.l,guest.getUUID()).getBoolean("died"),"It left alive, it did not die");
  var after=quest(t,id);
  h.assertTrue(after.getString("state").equals(Quests.DONE)&&coins(owner)==paid&&score(t,owner)==earned,"The delivery stays done and paid, with no penalty for the leaving: "+after.getString("state")+" "+coins(owner)+" "+score(t,owner));
  h.assertTrue(t.s.resident(guest.getUUID())==null,"A guest who left was never a resident");
  h.succeed();
 }
 // ---------------------------------------------------------------- T106: the owner logs out after the delivery
 @GameTest(template="empty",timeoutTicks=200) public static void theHourStandsStillWhileNobodyPlaysAndTheDeliveryHoldsWithoutItsOwner(GameTestHelper h){
  var t=town(h,false);var owner=player(t,"Leaver",Quests.trust(Quests.RESCUE));
  var q=delivered(t,owner,400);var id=q.getUUID("id");long paid=coins(owner);
  var guest=guestOf(t,q);long arrived=Camps.guest(t.l,guest.getUUID()).getLong("arrived");
  // The owner logs out: the settlement's clock is what the hour is counted on, and that clock only runs while somebody plays (ServerEvents).
  long clock=SettlementData.get(t.l.getServer()).clock().ticks();
  h.assertTrue(Camps.tickGuest(t.l,guest,clock).equals("waiting"),"With the hour not out the guest waits on");
  h.assertTrue(Camps.guest(t.l,guest.getUUID()).getLong("arrived")==arrived,"The waiting is not restarted");
  var after=quest(t,id);
  h.assertTrue(after.getString("state").equals(Quests.DONE)&&coins(owner)==paid,"The delivery holds while its owner is away: "+after.getString("state"));
  h.assertTrue(Camps.tickGuest(t.l,guest,arrived+Camps.GUEST_WAIT).equals("departed"),"The hour is counted from the arrival, on the same clock");
  h.succeed();
 }
 // ---------------------------------------------------------------- T107: the delivered one crosses the border again
 @GameTest(template="empty",timeoutTicks=200) public static void aDeliveredPersonCrossingTheBorderAgainIsPaidNoTwice(GameTestHelper h){
  var t=town(h,false);var owner=player(t,"Twice",Quests.trust(Quests.RESCUE));
  var q=delivered(t,owner,400);var id=q.getUUID("id");long paid=coins(owner),earned=score(t,owner);
  var guest=guestOf(t,q);var record=Camps.guest(t.l,guest.getUUID());
  // Out of the village and back in again, twice: the same person, the same arrival.
  for(int i=0;i<2;i++){
   guest.moveTo(t.center.getX()+60,t.center.getY()+1,t.center.getZ()+60,0,0);
   Quests.escorted(t.l,t.e,id,owner,500+i*10);
   guest.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);
   Quests.escorted(t.l,t.e,id,owner,505+i*10);}
  h.assertTrue(coins(owner)==paid&&score(t,owner)==earned,"No second pay for the same person: "+coins(owner)+" "+score(t,owner));
  var again=Camps.guest(t.l,guest.getUUID());
  h.assertTrue(again.getLong("arrived")==record.getLong("arrived")&&again.getLong("deadline")==record.getLong("deadline"),"The hour was not started again");
  h.assertTrue(quest(t,id).getList("arrived",Tag.TAG_INT_ARRAY).size()==q.getList("arrived",Tag.TAG_INT_ARRAY).size(),"Nobody arrived twice");
  h.succeed();
 }
 // ---------------------------------------------------------------- T109: somebody else kills the guest
 @GameTest(template="empty",timeoutTicks=200) public static void aKillerOfADeliveredGuestPaysAndTheDeliveryStaysDone(GameTestHelper h){
  var t=town(h,false);var owner=player(t,"Deliverer",Quests.trust(Quests.RESCUE));var killer=player(t,"Murderer",0);
  var q=delivered(t,owner,400);var id=q.getUUID("id");long paid=coins(owner),earned=score(t,owner);
  var guest=guestOf(t,q);
  guest.hurt(t.l.damageSources().playerAttack(killer),1000f);
  h.assertTrue(!guest.isAlive(),"The guest is killed by another player");
  h.assertTrue(score(t,killer)==-Quests.KILL_REPUTATION,"The killer pays for it here: "+score(t,killer));
  var after=quest(t,id);
  h.assertTrue(after.getString("state").equals(Quests.DONE)&&coins(owner)==paid&&score(t,owner)==earned,"The finished delivery is not undone by the killing: "+after.getString("state")+" "+coins(owner)+" "+score(t,owner));
  var record=Camps.guest(t.l,guest.getUUID());
  h.assertTrue(record.getString("state").equals("departed")&&record.getBoolean("died"),"The admission ends with the guest's death: "+record);
  h.succeed();
 }
 // ---------------------------------------------------------------- T110: the mayor ordered a house and did not finish it
 @GameTest(template="empty",timeoutTicks=200) public static void aHouseOrderedButUnfinishedDoesNotHoldTheGuestNorUndoTheDelivery(GameTestHelper h){
  var t=town(h,false);var owner=player(t,"Hopeful",Quests.trust(Quests.RESCUE));
  var q=delivered(t,owner,400);var id=q.getUUID("id");long paid=coins(owner);
  var guest=guestOf(t,q);long deadline=Camps.guest(t.l,guest.getUUID()).getLong("deadline");
  // The mayor's house is ordered but still a building site: a home nobody can live in yet holds nobody's place.
  var unfinished=new Settlement.Home(Settlement.childId(t.s.id(),"home/ordered"),1,2,false);t.s.addHome(unfinished);
  h.assertTrue(Camps.tickGuest(t.l,guest,deadline-1).equals("waiting"),"An unfinished house is no bed");
  h.assertTrue(Camps.tickGuest(t.l,guest,deadline).equals("departed"),"The hour out, the guest leaves although a house was ordered");
  h.assertTrue(quest(t,id).getString("state").equals(Quests.DONE)&&coins(owner)==paid,"The delivery is not annulled: "+quest(t,id).getString("state"));
  // The same house finished in time would have housed the next guest.
  t.s.addHome(new Settlement.Home(Settlement.childId(t.s.id(),"home/built"),1,2,true));
  var second=town(h,false);var mover=player(second,"Second",Quests.trust(Quests.RESCUE));
  var q2=delivered(second,mover,4000);var guest2=guestOf(second,q2);
  second.s.addHome(new Settlement.Home(Settlement.childId(second.s.id(),"home/built"),1,2,true));
  h.assertTrue(Camps.tickGuest(second.l,guest2,4300).equals("housed")&&second.s.resident(guest2.getUUID())!=null,"A house really finished takes the guest in");
  h.succeed();
 }
 // ---------------------------------------------------------------- T157: the guest arrives while the village is besieged
 @GameTest(template="empty",timeoutTicks=200) public static void anArrivalCountsUnderSiegeAndTheHourRunsAllTheSame(GameTestHelper h){
  var t=town(h,true);var owner=player(t,"UnderFire",Quests.trust(Quests.RESCUE));
  // An army of another village holds the ring: nothing may be built here now.
  var army=new CompoundTag();army.putInt("schema",1);army.putUUID("id",UUID.randomUUID());army.putUUID("target",t.s.id());army.putUUID("attacker",UUID.randomUUID());
  army.putString("dimension",t.e.dimension());army.put("supply",new CompoundTag());army.put("soldiers",new net.minecraft.nbt.ListTag());
  army.putString("state",Sieges.BESIEGING);army.put("stamps",new CompoundTag());Sieges.save(t.l.getServer(),army);
  try{
  h.assertTrue(Sieges.besieged(t.l.getServer(),t.s.id()),"The village is besieged");
  var q=delivered(t,owner,400);
  h.assertTrue(q.getString("state").equals(Quests.DONE)&&coins(owner)>0,"The arrival counts and pays under siege too: "+q.getString("state"));
  var guest=guestOf(t,q);
  h.assertTrue(Camps.tickGuest(t.l,guest,500).equals("housed")&&t.s.resident(guest.getUUID())!=null,"A bed that already stands takes the guest in, siege or no siege");
  }finally{army.putString("state",Sieges.WITHDRAWN);Sieges.save(t.l.getServer(),army);}
  h.succeed();
 }
}
