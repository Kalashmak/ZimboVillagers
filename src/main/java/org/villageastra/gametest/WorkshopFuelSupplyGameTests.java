package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** AD479: a fuel shortage is burn time, not a count of coal-sized items. */
@GameTestHolder("villageastra") @PrefixGameTestTemplate(false)
public final class WorkshopFuelSupplyGameTests {
 private static void stock(ResearchV2Town.Town t,Item fuel,int amount,int sticks){
  var source=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();if(amount>0)source.setItem(0,new ItemStack(fuel,amount));
  var kitchen=LogisticsRoutes.chest(t.l,t.e,t.shop);kitchen.clearContent();if(sticks>0)kitchen.setItem(0,new ItemStack(Items.STICK,sticks));
 }
 private static void check(GameTestHelper h,Item fuel,int expected){
  var t=ResearchV2Town.town(h,"restaurant");try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();source.setItem(0,new ItemStack(fuel,64));
   var kitchen=LogisticsRoutes.chest(t.l,t.e,t.shop);kitchen.clearContent();kitchen.setItem(0,new ItemStack(Items.STICK,12));
   var need=Workshops.needs(t.l,t.e,t.shop,kitchen,Workshops.wants(t.l,t.e));
   h.assertTrue(need.stream().anyMatch(n->n.matches(new ItemStack(Items.COAL))),"The real next kitchen batch needs oven fuel");
   var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
   h.assertTrue(route!=null&&route.item().is(fuel)&&route.item().getCount()==expected,
    "The missing400 burn ticks use the selected real fuel, not a coal count: "+route);
   h.assertTrue(source.countItem(fuel)==64&&kitchen.countItem(Items.STICK)==12,"Read-only planning never spends stock");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void sticksCoverTheActualMissingBurnTime(GameTestHelper h){check(h,Items.STICK,4);}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void bambooCoversTheActualMissingBurnTime(GameTestHelper h){check(h,Items.BAMBOO,8);}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void coalStillNeedsOnlyOneItem(GameTestHelper h){check(h,Items.COAL,1);}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void existingFuelAndPaidBankSurvivePublicationAndLegacyRead(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.STICK,64,3);var saved=new CompoundTag();saved.putString("stage","idle");saved.putInt("fuelBank",900);NbtRecord.write(Workshops.path(t.l,t.shop.id()),saved);
  Workshops.advance(t.l,t.e,t.shop,20,List.of(new Workshops.Want(Ingredient.of(Items.BREAD),8,t.shop.id())));
  var fuel=Workshops.published(t.l,t.shop.id()).stream().filter(n->n.matches(new ItemStack(Items.COAL))).findFirst().orElseThrow();
  h.assertTrue(fuel.fuelTicks()==400,"Saved and freshly read shortage retains400 ticks after real stock plus paid bank");
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route!=null&&route.item().is(Items.STICK)&&route.item().getCount()==4,"Bank and mixed real fuel are not fetched twice");
  saved=NbtRecord.read(Workshops.path(t.l,t.shop.id()));for(var raw:saved.getList("needs",Tag.TAG_COMPOUND))((CompoundTag)raw).remove("fuelTicks");NbtRecord.write(Workshops.path(t.l,t.shop.id()),saved);
  h.assertTrue(Workshops.published(t.l,t.shop.id()).stream().allMatch(n->n.fuelTicks()==0),"Legacy records without burn metadata remain ordinary counted inputs");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void aLargeBambooGapRespectsTheExistingCarryLimit(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.BAMBOO,64,0);var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
  h.assertTrue(route!=null&&route.item().is(Items.BAMBOO)&&route.item().getCount()==LogisticsRoutes.LOAD,"Full1600-tick shortage is split into the existing16-item loads");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 private static UUID parcel(ResearchV2Town.Town t,UUID source,UUID destination,Item item,int count){
  var id=UUID.randomUUID();var r=new Resident(id,Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(id,Profession.PORTER,t.hall().id());
  var p=new CompoundTag();p.putInt("schema",1);p.putUUID("id",UUID.randomUUID());p.putUUID("worker",id);p.putUUID("settlement",t.s.id());p.putUUID("assignment",t.hall().id());p.putUUID("source",source);p.putUUID("destination",destination);p.putString("stage","fetch");p.put("item",new ItemStack(item,count).save(new CompoundTag()));NbtRecord.write(PorterWork.path(t.l,id),p);return id;
 }
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void aPendingDifferentFuelParcelPreventsDuplicateFunding(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.STICK,64,12);LogisticsRoutes.chest(t.l,t.e,t.hall()).setItem(1,new ItemStack(Items.COAL));
  var carrier=parcel(t,t.hall().id(),t.shop.id(),Items.COAL,1);var before=PorterWork.inspect(t.l,carrier).copy();var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
  h.assertTrue(route==null||WorkshopFuel.ticks(route.item())==0,"Wait for the already reserved coal before sending another kind of fuel");
  h.assertTrue(PorterWork.inspect(t.l,carrier).equals(before),"Planning never rewrites another worker's reservation");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void anOutgoingReservationKeepsItsOriginalSticks(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.STICK,4,12);var mine=new Settlement.Building(UUID.randomUUID(),"mine",30,0,0);t.s.addBuilding(mine);parcel(t,t.hall().id(),mine.id(),Items.STICK,3);
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route!=null&&route.item().is(Items.STICK)&&route.item().getCount()==1,"Only the unreserved stick can join a new parcel");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void aFullKitchenCannotAcceptTheSelectedCoal(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.COAL,64,12);var kitchen=LogisticsRoutes.chest(t.l,t.e,t.shop);for(int i=1;i<kitchen.getContainerSize();i++)kitchen.setItem(i,new ItemStack(Items.DIRT,64));
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route==null||!route.item().is(Items.COAL),"Fuel still requires real destination space");
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_supply",timeoutTicks=100)
 public static void approvedConstructionStillOwnsItsReservedFuelItems(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.STICK,4,12);var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:stick",4);project.put("cost",cost);HallUpgradeGoal.enqueue(t.l,t.e,project);
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route==null||!route.item().is(Items.STICK),"Construction stock remains reserved before fuel hauling");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_construction_bank",timeoutTicks=100)
 public static void constructionIngredientsCountTheAlreadyPaidHallFuel(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  stock(t,Items.STICK,0,4);var idle=new CompoundTag();idle.putString("stage","idle");idle.putInt("fuelBank",200);NbtRecord.write(Workshops.path(t.l,t.hall().id()),idle);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:glass",1);project.put("cost",cost);HallUpgradeGoal.enqueue(t.l,t.e,project);
  var route=LogisticsRoutes.choose(t.l,t.e);h.assertTrue(route==null||!route.item().is(Items.STICK),"An unfunded glass order already has its200 fuel ticks; do not deliver two unnecessary sticks: "+route);
  h.assertTrue(Workshops.inspect(t.l,t.hall().id()).getInt("fuelBank")==200&&LogisticsRoutes.chest(t.l,t.e,t.shop).countItem(Items.STICK)==4,"Sensing spends neither the paid bank nor the real source fuel");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="workshop_fuel_physical",timeoutTicks=1400)
 public static void bakerPhysicallyDeliversTheFiniteFourStickBatchOnce(GameTestHelper h){
  // The test floor is underground: keep generated gravel above the cleared fixture from falling into the route.
  var roof=h.absolutePos(new BlockPos(6,19,6));for(var p:BlockPos.betweenClosed(roof.offset(-4,0,-4),roof.offset(30,0,18)))h.getLevel().setBlock(p,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
  var t=ResearchV2Town.town(h,"restaurant");stock(t,Items.STICK,4,12);var origin=t.e.center();var held=PhysicalFixtureChunks.force(t.l,origin,-4,30,-4,18);
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BAKER,t.shop.id());npc.bind(t.s.id(),r);
  npc.moveTo(origin.getX()+2.5,origin.getY(),origin.getZ()+4.5);npc.onlyGoals(g->g instanceof ResidentDoorGoal,5,new WorkerSupplyGoal(npc,true));SettlementData.get(t.l.getServer()).remove(t.s.id());
  CompoundTag[] trip={null};double[] start={0};float hp=npc.getHealth();
  h.startSequence().thenWaitUntil(()->h.assertTrue(t.l.isPositionEntityTicking(npc.blockPosition())&&t.l.isPositionEntityTicking(LogisticsRoutes.position(t.e,t.shop)),"Both physical body and kitchen chunks tick before admission")).thenExecute(()->{
   SettlementData.get(t.l.getServer()).add(t.e);h.assertTrue(t.l.addFreshEntity(npc)&&t.l.getEntity(npc.getUUID())==npc,"The actual baker is admitted once");start[0]=npc.getX();
  }).thenWaitUntil(()->{
   var p=PorterWork.inspect(t.l,npc.getUUID());if(p.hasUUID("id"))trip[0]=p.copy();
   h.assertTrue(LogisticsRoutes.chest(t.l,t.e,t.shop).countItem(Items.STICK)==16&&!PorterWork.active(p),"The baker must walk and deposit all four real sticks: "+npc.position()+" "+npc.workStatus()+" "+p.getString("stage"));
  }).thenExecute(()->{try{
   var p=trip[0];h.assertTrue(p!=null&&npc.tickCount>20&&npc.getX()>start[0]+5&&npc.getHealth()==hp,"A healthy native body physically crossed the route");
   h.assertTrue(ItemStack.of(p.getCompound("item")).getCount()==4&&LogisticsRoutes.chest(t.l,t.e,t.hall()).countItem(Items.STICK)==0,"The original finite four-item parcel was spent at its source");
   var take=WorldJournal.inspectCommitted(t.l,Settlement.childId(p.getUUID("id"),"take"));var put=WorldJournal.inspectCommitted(t.l,Settlement.childId(p.getUUID("id"),"put"));
   h.assertTrue(take!=null&&put!=null&&take.getLong("pos")==LogisticsRoutes.position(t.e,t.hall()).asLong()&&put.getLong("pos")==LogisticsRoutes.position(t.e,t.shop).asLong(),"Both original receipts commit at the real chests");
   for(int i=0;i<5;i++)WorldJournal.recoverExisting(t.l,Settlement.childId(p.getUUID("id"),"put"));
   h.assertTrue(LogisticsRoutes.chest(t.l,t.e,t.shop).countItem(Items.STICK)==16&&LogisticsRoutes.chest(t.l,t.e,t.hall()).countItem(Items.STICK)==0,"Five receipt replays create no extra fuel");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FUEL_BATCH physical=true bodyTicks={} sticks=4 source=0 destination=16 replay=5",npc.tickCount);
  }finally{npc.discard();ResearchV2Town.done(t);PhysicalFixtureChunks.release(t.l,held);}}).thenSucceed();
 }
}
