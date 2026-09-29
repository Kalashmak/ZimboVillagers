package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import org.villageastra.server.SettlementData;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaravanEscortGameTests {
 private static ResidentEntity soldier(TradeLadderGameTests.Town town){
  var s=town.s();var id=UUID.randomUUID();var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,4,true));
  var barracks=new Settlement.Building(UUID.randomUUID(),"barracks",0,0,18);s.addBuilding(barracks);
  var r=new Resident(id,Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);r.trainMilitary();s.assign(id,Profession.SOLDIER,barracks.id());
  var npc=VillageAstra.RESIDENT.get().create(town.l());npc.bind(s.id(),r);npc.moveTo(town.e().center().getX()+.5,town.e().center().getY()+1,town.e().center().getZ()+.5,0,0);npc.setNoAi(true);town.l().addFreshEntity(npc);
  var gear=new CompoundTag();gear.put("weapon",new ItemStack(Items.IRON_SWORD).save(new CompoundTag()));GuardGoal.write(town.l(),id,gear);return npc;
 }
 private static CompoundTag leader(TradeLadderGameTests.Town town){
  var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putUUID("caravaneer",UUID.randomUUID());t.putUUID("source",town.s().id());t.putUUID("destination",UUID.randomUUID());
  t.putString("kind","trade");t.putString("state",Caravans.TRANSIT);t.putString("dimension",town.e().dimension());t.putLong("from",town.e().center().asLong());t.putLong("to",town.e().center().offset(80,0,0).asLong());
  t.put("cargo",new ListTag());Caravans.update(town.l().getServer(),t);return t;
 }
 private static java.util.List<CompoundTag> guards(TradeLadderGameTests.Town town,CompoundTag parent){return Caravans.contracts(town.l().getServer()).stream().filter(t->t.hasUUID("leaderTrip")&&t.getUUID("leaderTrip").equals(parent.getUUID("id"))).toList();}
 private static void clean(TradeLadderGameTests.Town town,CompoundTag parent){
  var s=town.l().getServer();for(var t:new ArrayList<>(Caravans.contracts(s)))if(t.getUUID("source").equals(town.s().id())){t.putString("state",Caravans.CLOSED);t.putBoolean("needsEntity",false);Caravans.update(s,t);}
  for(var r:town.s().residents())if(town.l().getEntity(r.id()) instanceof ResidentEntity npc)npc.discard();BuildingLevels.forgetBest(town.s().id());SettlementData.get(s).remove(town.s().id());
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanEscortStartsAtFourthLevelAndReservesOnlyFreeSoldiers(GameTestHelper h){
  var town=TradeLadderGameTests.town(h,new BlockPos(6,3,6),4);var parent=leader(town);
  try{
   var free=soldier(town);var companion=soldier(town);companion.escort(UUID.randomUUID());var wounded=soldier(town);wounded.setHealth(4);
   CaravanEscorts.recruit(town.l(),parent,100);var guards=guards(town,parent);
   h.assertTrue(guards.size()==1&&guards.get(0).getUUID("caravaneer").equals(free.getUUID()),"Only the available healthy soldier departs");
   h.assertTrue(CaravanEscorts.reserved(town.l().getServer(),free.getUUID()),"Army must respect this reservation");
   CaravanEscorts.recruit(town.l(),parent,101);h.assertTrue(guards(town,parent).size()==1,"Repeated dispatch must not recruit twice");
   h.assertTrue(town.s().resident(free.getUUID()).profession()==Profession.SOLDIER,"Original profession remains");h.succeed();
  }finally{clean(town,parent);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanEscortDoesNotUnlockAtThirdLevel(GameTestHelper h){
  var town=TradeLadderGameTests.town(h,new BlockPos(6,3,6),3);var parent=leader(town);
  try{var npc=soldier(town);CaravanEscorts.recruit(town.l(),parent,100);h.assertTrue(guards(town,parent).isEmpty()&&npc.isAlive(),"Level III keeps its soldier home");h.succeed();}finally{clean(town,parent);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanEscortReturnsAfterLeaderLossAndKeepsIdentityAndHealth(GameTestHelper h){
  var town=TradeLadderGameTests.town(h,new BlockPos(6,3,6),4);var parent=leader(town);
  try{
   var npc=soldier(town);npc.setHealth(16);var id=npc.getUUID();CaravanEscorts.recruit(town.l(),parent,100);var t=guards(town,parent).get(0);
   var body=Caravans.materialize(town.l(),t,town.e().center());h.assertTrue(body!=null&&body.getUUID().equals(id)&&body.getHealth()==16,"Same soldier and wounds after materialization");
   body.setHealth(11);Caravans.dematerialize(body);body.discard();body=Caravans.materialize(town.l(),t,town.e().center());
   h.assertTrue(body!=null&&body.getHealth()==11&&Caravans.stale(town.l().getServer(),npc),"Unload keeps health and rejects old body");
   t.putDouble("progress",23);parent.putString("state",Caravans.CLOSED);Caravans.update(town.l().getServer(),parent);CaravanEscorts.sync(town.l(),t);
   h.assertTrue(t.getString("state").equals(Caravans.RETURNING),"Leader lost: guard returns independently");
   Caravans.arrive(town.l(),t,200);h.assertTrue(t.getString("state").equals(Caravans.CLOSED)&&!CaravanEscorts.reserved(town.l().getServer(),id),"Home: guard is free, with no trade payment or cargo");
   h.assertTrue(town.s().resident(id).alive()&&town.s().workplace(id).type().equals("barracks"),"Home assignment preserved");h.succeed();
  }finally{clean(town,parent);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanEscortFightsAndItsDeathDoesNotLoseTheTradeCargo(GameTestHelper h){
  var town=TradeLadderGameTests.town(h,new BlockPos(6,3,6),4);var parent=leader(town);net.minecraft.world.entity.monster.Zombie zombie=null;
  try{
   var original=soldier(town);CaravanEscorts.recruit(town.l(),parent,100);var t=guards(town,parent).get(0);
   var npc=Caravans.materialize(town.l(),t,town.e().center());h.assertTrue(npc!=null,"Soldier materialized");npc.setNoAi(true);
   zombie=new net.minecraft.world.entity.monster.Zombie(town.l());zombie.setNoAi(true);zombie.moveTo(npc.getX()+1,npc.getY(),npc.getZ());town.l().addFreshEntity(zombie);
   float before=zombie.getHealth();new CaravanGoal(npc).tick();h.assertTrue(zombie.getHealth()<before,"Escort actually strikes a nearby monster");
   h.assertTrue(ItemStack.of(GuardGoal.inspect(town.l(),original.getUUID()).getCompound("weapon")).getDamageValue()==1,"Real weapon wears on hit");
   parent.getList("cargo",Tag.TAG_COMPOUND).add(new ItemStack(Items.BREAD,16).save(new CompoundTag()));Caravans.update(town.l().getServer(),parent);
   Caravans.died(npc,120);h.assertTrue(t.getString("state").equals(Caravans.CLOSED)&&parent.getList("cargo",Tag.TAG_COMPOUND).size()==1&&parent.getString("state").equals(Caravans.TRANSIT),"Only the fallen guard's trip closes; cargo stays with driver");h.succeed();
  }finally{if(zombie!=null)zombie.discard();clean(town,parent);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanEscortRespectsGatheringArmyAndReloadsItsReturnReservation(GameTestHelper h){
  var town=TradeLadderGameTests.town(h,new BlockPos(6,3,6),4);var parent=leader(town);var army=new CompoundTag();
  try{
   var busy=soldier(town);var free=soldier(town);army.putUUID("id",UUID.randomUUID());army.putUUID("attacker",town.s().id());army.putUUID("target",UUID.randomUUID());
   army.putString("dimension",town.e().dimension());army.put("stamps",new CompoundTag());army.put("supply",new CompoundTag());
   army.putString("state",Sieges.GATHERING);var list=new ListTag();list.add(NbtUtils.createUUID(busy.getUUID()));army.put("soldiers",list);Sieges.save(town.l().getServer(),army);
   h.assertTrue(Sieges.siegeOf(town.l().getServer(),army.getUUID("target"))!=null,"The reservation is a valid army record for campaign queries too");
   CaravanEscorts.recruit(town.l(),parent,100);var trips=guards(town,parent);h.assertTrue(trips.size()==1&&trips.get(0).getUUID("caravaneer").equals(free.getUUID()),"Gathering army keeps its soldier");
   var t=trips.get(0);parent.putDouble("progress",35);CaravanEscorts.background(town.l(),t);h.assertTrue(t.getDouble("progress")==35,"Unloaded escort follows leader progress");
   parent.putString("state",Caravans.CLOSED);Caravans.update(town.l().getServer(),parent);CaravanEscorts.sync(town.l(),t);Caravans.arrive(town.l(),t,200);
   h.assertTrue(t.getBoolean("needsEntity")&&CaravanEscorts.reserved(town.l().getServer(),free.getUUID()),"Return remains reserved until body appears home");
   var id=t.getUUID("id");Caravans.clear();var loaded=Caravans.contract(town.l().getServer(),id);h.assertTrue(loaded.getBoolean("needsEntity")&&CaravanEscorts.reserved(town.l().getServer(),free.getUUID()),"Reservation survives disk reload");h.succeed();
  }finally{army.putString("state",Sieges.CLOSED);if(army.hasUUID("id"))Sieges.save(town.l().getServer(),army);clean(town,parent);}
 }
}
