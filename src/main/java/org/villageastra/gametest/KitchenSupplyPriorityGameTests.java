package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;

/** Prepared real stocks test scheduling, never grant natural-world food or skip its clock. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class KitchenSupplyPriorityGameTests {
 private static Resident baker(ResearchV2Town.Town t){
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
  t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.BAKER,t.shop.id());return r;
 }
 private static List<Workshops.Want> wants(ResearchV2Town.Town t){return List.of(
  new Workshops.Want(Ingredient.of(Items.STONE_BRICK_WALL),2,t.hall().id(),LogisticsRoutes.NEED_BUILD),
  new Workshops.Want(Ingredient.of(Items.BREAD),8,t.hall().id(),LogisticsRoutes.NEED_FOOD));}
 private static void stock(ResearchV2Town.Town t){
  var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.clearContent();hall.setItem(0,new ItemStack(Items.STONE,2));hall.setItem(1,new ItemStack(Items.WHEAT,16));
  var kitchen=LogisticsRoutes.chest(t.l,t.e,t.shop);kitchen.clearContent();kitchen.setItem(0,new ItemStack(Items.COAL));
  Workshops.advance(t.l,t.e,t.shop,20,List.of(new Workshops.Want(Ingredient.of(Items.BREAD),8,t.shop.id(),LogisticsRoutes.NEED_FOOD)));
 }
 @GameTest(template="empty",batch="kitchen_supply_priority",timeoutTicks=100)
 public static void hungryVillageFeedsStaffedKitchenBeforeAnotherBuildingBatch(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  baker(t);stock(t);var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());
  h.assertTrue(Workshops.published(t.l,t.shop.id()).stream().anyMatch(in->in.matches(new ItemStack(VillageAstra.FLOUR.get()))),"The real idle bakery has published its missing flour");
  var job=Workshops.plan(t.l,t.e,t.hall(),hall,wants(t));
  h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(VillageAstra.FLOUR.get())),"A funded wall cannot monopolize the hall while its staffed bakery lacks flour: "+job);
  h.assertTrue(hall.countItem(Items.WHEAT)==16&&hall.countItem(Items.STONE)==2,"Sensing must not spend inputs");
  Workshops.advance(t.l,t.e,t.hall(),20,wants(t));var paid=Workshops.inspect(t.l,t.hall().id());
  h.assertTrue(paid.getString("recipe").equals("custom:flour")&&paid.getLong("needLabor")>0,"Actual job execution agrees with the food planning and keeps ordinary labor: "+paid);
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="kitchen_supply_priority",timeoutTicks=100)
 public static void oneCoveredMealRoundRestoresBuildingPriority(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  baker(t);stock(t);var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.setItem(2,new ItemStack(Items.BREAD,2));
  var job=Workshops.plan(t.l,t.e,t.hall(),hall,wants(t));h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.STONE_BRICK_WALL)),"Existing food restores construction priority: "+job);
 }finally{ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="kitchen_supply_priority",timeoutTicks=100)
 public static void sickUnstaffedKitchenDoesNotDivertConstruction(GameTestHelper h){var t=ResearchV2Town.town(h,"restaurant");try{
  var r=baker(t);stock(t);r.fallIll();var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());
  var job=Workshops.plan(t.l,t.e,t.hall(),hall,wants(t));h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.STONE_BRICK_WALL)),"A kitchen unable to bake does not preempt construction: "+job);
 }finally{ResearchV2Town.done(t);}h.succeed();}
}
