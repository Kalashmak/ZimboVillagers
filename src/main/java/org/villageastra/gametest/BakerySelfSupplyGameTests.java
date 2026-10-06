package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;

/** Prepared pantry inputs expose self-hauling starvation without changing natural world stocks. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BakerySelfSupplyGameTests {
 private static ResidentEntity baker(ResearchV2Town.Town t){var npc=VillageAstra.RESIDENT.get().create(t.l);
  var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BAKER,t.shop.id());
  npc.bind(t.s.id(),r);npc.setNoAi(true);var at=BuildingPlacement.origin(t.e,t.shop);npc.moveTo(at.getX()+2.5,at.getY()+1,at.getZ()+2.5);t.l.addFreshEntity(npc);return npc;}
 private static void stock(ResearchV2Town.Town t){var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.clearContent();hall.setItem(0,new ItemStack(Items.BREAD,8));hall.setItem(1,new ItemStack(Items.COAL));LogisticsRoutes.chest(t.l,t.e,t.shop).clearContent();}
 @GameTest(template="empty",batch="bakery_self_supply",timeoutTicks=100)
 public static void bakerFetchesFlourBeforeExistingFinishedBread(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");var npc=baker(t);try{
  stock(t);LogisticsRoutes.chest(t.l,t.e,t.hall()).setItem(2,new ItemStack(VillageAstra.FLOUR.get(),4));
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route!=null&&route.item().is(VillageAstra.FLOUR.get()),"Maker fetches its flour instead of endlessly relocating finished bread: "+route);
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="bakery_self_supply",timeoutTicks=100)
 public static void deliveredFlourRevealsFuelDespiteStalePublishedShortage(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");var npc=baker(t);try{
  stock(t);Workshops.advance(t.l,t.e,t.shop,20,List.of(new Workshops.Want(Ingredient.of(Items.BREAD),8,t.shop.id())));
  h.assertTrue(Workshops.published(t.l,t.shop.id()).stream().anyMatch(in->in.matches(new ItemStack(VillageAstra.FLOUR.get()))),"Initial idle recipe requests flour");
  LogisticsRoutes.chest(t.l,t.e,t.shop).setItem(0,new ItemStack(VillageAstra.FLOUR.get(),4));
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route!=null&&route.item().is(Items.COAL),"Real current oven fuel shortage wins over stale flour and finished bread: "+route);
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="bakery_self_supply",timeoutTicks=100)
 public static void readyBakerStopsSelfHaulingAndCanStartPaidProduction(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");var npc=baker(t);try{
  stock(t);var own=LogisticsRoutes.chest(t.l,t.e,t.shop);own.setItem(0,new ItemStack(VillageAstra.FLOUR.get(),4));own.setItem(1,new ItemStack(Items.COAL));
  h.assertTrue(!WorkerSupplies.available(npc,true),"An idle baker with paid-for inputs lets its workshop goal run instead of hauling the hall's bread");
  var job=Workshops.plan(t.l,t.e,t.shop,own,Workshops.wants(t.l,t.e));h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.BREAD))&&job.fuelTicks()>0&&job.labor()>0,"Its actual recipe still consumes flour/fuel and ordinary labor");
  h.assertTrue(own.countItem(VillageAstra.FLOUR.get())==4&&own.countItem(Items.COAL)==1,"Planning grants or consumes nothing");
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="bakery_self_supply",timeoutTicks=100)
 public static void readyKitchenStillExportsRealBreadAboveDiningReserve(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");var npc=baker(t);try{
  stock(t);LogisticsRoutes.chest(t.l,t.e,t.hall()).setItem(0,ItemStack.EMPTY);var own=LogisticsRoutes.chest(t.l,t.e,t.shop);
  own.setItem(0,new ItemStack(VillageAstra.FLOUR.get(),4));own.setItem(1,new ItemStack(Items.COAL));own.setItem(2,new ItemStack(Items.BREAD,Dining.KEEP_ITEMS+4));
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
  h.assertTrue(route!=null&&route.source().id().equals(t.shop.id())&&route.item().is(Items.BREAD)&&route.item().getCount()==4,"Ready production must not trap finished surplus in a full kitchen: "+route);
  h.assertTrue(WorkerSupplies.available(npc,true),"Surplus delivery remains available before starting another batch");
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="bakery_self_supply",timeoutTicks=100)
 public static void paidFuelBankDoesNotRequestUnneededCoal(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");var npc=baker(t);try{
  stock(t);var idle=new net.minecraft.nbt.CompoundTag();idle.putString("stage","idle");idle.putInt("fuelBank",1600);
  org.villageastra.persistence.NbtRecord.write(Workshops.path(t.l,t.shop.id()),idle);
  var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);h.assertTrue(route==null||!route.item().is(Items.COAL),"Paid oven fuel is counted while fetching unavailable flour: "+route);
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();}

}
