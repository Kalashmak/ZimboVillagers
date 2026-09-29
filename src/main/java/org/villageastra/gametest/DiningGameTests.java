package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import static org.villageastra.gametest.RestaurantFixture.*;
/** AD-139 §10.1 (1–8): the restaurant's dining hall — seats read from the world, a resident who really walks in and eats at a table, the full
 *  hall, the night, the far resident, the leftover of a dish, the lost invitation that never costs a meal, the record after a reload, and an
 *  old world's bakery that works as a restaurant without a hall. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DiningGameTests {
 /** 1. Seats 4/8/8/12/12/16 by the level, counted in the world: a seat whose stair is broken is one fewer. */
 @GameTest(template="empty",timeoutTicks=400) public static void seatsFollowTheRestaurantLevel(GameTestHelper h){
  var v=village(h,1);
  try{for(int level=1;level<=6;level++){var b=raise(v,level);
    h.assertTrue(BuildingLevels.level(v.l(),v.e(),b)==level,"The restaurant raised to "+level+" works at "+BuildingLevels.level(v.l(),v.e(),b));
    int seats=Dining.seats(v.l(),v.e(),b);h.assertTrue(seats==CoreEffects.value("restaurant","seats",level)&&seats==Dining.designSeats(level),"Level "+level+" seats "+seats+", the table says "+CoreEffects.value("restaurant","seats",level));}
   var b=v.kept();v.l().setBlock(Dining.seatPos(v.e(),b,0),Blocks.AIR.defaultBlockState(),2);Dining.forgetSeats();
   h.assertTrue(Dining.seats(v.l(),v.e(),b)==15,"A broken seat is one fewer: "+Dining.seats(v.l(),v.e(),b));
   h.assertTrue(Dining.card(v.l(),v.e(),b).getInt("seats")==15,"The card shows the seats that stand");
  }finally{done(v);}
  h.succeed();
 }
 /** 2. Invited at noon, a hungry resident walks in to a free seat, sits sit_ticks with the dish, and eats: its meal is taken from the restaurant's
  *  chest, the pantry is not touched. */
 @GameTest(template="empty",timeoutTicks=1600) public static void aHungryResidentWalksToTheRestaurantAndEats(GameTestHelper h){
  var v=village(h,1);var b=v.kept();put(v.kitchen(),new ItemStack(Items.BREAD,4));put(v.pantry(),new ItemStack(Items.BREAD,4));
  var r=adult(v,"diner",null,null);var npc=body(v,r,new BlockPos(3,0,10));give(npc,4,new DineGoal(npc,()->NOW));
  long due=r.lastMeal()+Population.MEAL_INTERVAL;
  h.assertTrue(Dining.meal(v.l(),v.e(),r,NOW,due)==Dining.Outcome.WAIT&&Dining.invite(v.l(),v.e(),r.id())!=null,"The hungry resident is invited to the hall: "+Dining.why(v.l(),v.e(),r,b));
  boolean[] seated={false};
  h.onEachTick(()->{if("dining".equals(npc.workStatus()))seated[0]=true;});
  h.succeedWhen(()->{
   h.assertTrue(r.lastMeal()==due,"Not eaten yet ("+npc.workStatus()+")");
   h.assertTrue(seated[0],"It sat at a table before it ate");
   h.assertTrue(count(v.kitchen(),Items.BREAD)==3&&count(v.pantry(),Items.BREAD)==4,"One bread left the restaurant's chest, none the pantry: kitchen="+count(v.kitchen(),Items.BREAD)+" pantry="+count(v.pantry(),Items.BREAD));
   h.assertTrue(Dining.invite(v.l(),v.e(),r.id())==null&&Dining.taken(v.l(),v.e(),b)==0,"The invitation is done and the seat free");
   done(v);});
 }
 /** 3. Four seats and six hungry: four are invited, two wait for a seat — and eat from the pantry once wait_ticks are over; nobody misses a meal. */
 @GameTest(template="empty",timeoutTicks=200) public static void aFullRoomSendsTheOverflowToThePantry(GameTestHelper h){
  var v=village(h,1);var b=v.kept();put(v.kitchen(),new ItemStack(Items.BREAD,16));put(v.pantry(),new ItemStack(Items.BREAD,16));
  try{var all=new ArrayList<Resident>();for(int i=0;i<6;i++){var r=adult(v,"full"+i,null,null);body(v,r,new BlockPos(2+i,0,10));all.add(r);}
   long due=all.get(0).lastMeal()+Population.MEAL_INTERVAL;int invited=0,waiting=0;
   for(var r:all){var o=Dining.meal(v.l(),v.e(),r,NOW,due);if(Dining.invite(v.l(),v.e(),r.id())!=null)invited++;else if(o==Dining.Outcome.WAIT)waiting++;}
   h.assertTrue(invited==4&&waiting==2,"Four seats invite four, two wait: invited="+invited+" waiting="+waiting);
   long later=due+Dining.WAIT_TICKS;
   for(var r:all)if(Dining.invite(v.l(),v.e(),r.id())==null)h.assertTrue(Population.meal(v.l(),v.e(),r,later)&&r.lastMeal()==due&&r.missedMeals()==0,"After wait_ticks the overflow eats from the pantry with its own due");
   h.assertTrue(count(v.pantry(),Items.BREAD)==14,"Two meals came out of the pantry: "+count(v.pantry(),Items.BREAD));
  }finally{done(v);}
  h.succeed();
 }
 /** 4. At night the hall is closed: no invitation, the pantry feeds as before. */
 @GameTest(template="empty",timeoutTicks=200) public static void atNightEveryoneEatsFromThePantry(GameTestHelper h){
  var v=village(h,1);put(v.kitchen(),new ItemStack(Items.BREAD,8));put(v.pantry(),new ItemStack(Items.BREAD,8));
  try{Dining.testDay(v.s().id(),14000L);var r=adult(v,"night",null,null);body(v,r,new BlockPos(3,0,10));long due=r.lastMeal()+Population.MEAL_INTERVAL;
   h.assertTrue(Dining.why(v.l(),v.e(),r,v.kept()).equals("closed_night")&&Dining.meal(v.l(),v.e(),r,NOW,due)==Dining.Outcome.PANTRY,"The hall is closed at night");
   h.assertTrue(Population.meal(v.l(),v.e(),r,NOW)&&r.lastMeal()==due&&count(v.pantry(),Items.BREAD)==7&&count(v.kitchen(),Items.BREAD)==8,"The pantry fed it, the restaurant's chest is untouched");
  }finally{done(v);}
  h.succeed();
 }
 /** 5. A resident 80 blocks from the hall eats from the pantry. */
 @GameTest(template="empty",timeoutTicks=200) public static void aFarResidentEatsFromThePantry(GameTestHelper h){
  var v=village(h,1,"restaurant",100);put(v.kitchen(),new ItemStack(Items.BREAD,8));put(v.pantry(),new ItemStack(Items.BREAD,8));
  try{var r=adult(v,"far",null,null);body(v,r,new BlockPos(95,0,6));long due=r.lastMeal()+Population.MEAL_INTERVAL;
   h.assertTrue(Dining.why(v.l(),v.e(),r,v.kept()).equals("too_far"),"Too far from the hall: "+Dining.why(v.l(),v.e(),r,v.kept()));
   h.assertTrue(Dining.meal(v.l(),v.e(),r,NOW,due)==Dining.Outcome.PANTRY&&Population.meal(v.l(),v.e(),r,NOW)&&r.lastMeal()==due&&count(v.pantry(),Items.BREAD)==7,"It ate from the pantry");
  }finally{done(v);}
  h.succeed();
 }
 /** 6. Cooked beef (8 rations): the first diner eats 5 and leaves 3, the second eats those 3 and opens a new piece, the third eats from its
  *  leftover — three meals out of two pieces, where the pantry takes three. */
 @GameTest(template="empty",timeoutTicks=200) public static void theLeftoverOfADishFeedsTheNextDiner(GameTestHelper h){
  var v=village(h,2);var b=v.kept();put(v.kitchen(),new ItemStack(Items.COOKED_BEEF,5));
  try{int[] left={3,6,1};
   for(int i=0;i<3;i++){var r=adult(v,"beef"+i,null,null);body(v,r,new BlockPos(3+i,0,10));long due=r.lastMeal()+Population.MEAL_INTERVAL;
    h.assertTrue(Dining.meal(v.l(),v.e(),r,NOW,due)==Dining.Outcome.WAIT,"Diner "+i+" is invited: "+Dining.why(v.l(),v.e(),r,b));
    h.assertTrue(Dining.serve(v.l(),v.e(),b,r)&&r.lastMeal()==due,"Diner "+i+" is served at its due");
    h.assertTrue(Dining.leftover(v.l(),v.e(),b)==left[i],"Leftover after diner "+i+": "+Dining.leftover(v.l(),v.e(),b));}
   h.assertTrue(count(v.kitchen(),Items.COOKED_BEEF)==3,"Two pieces fed three: "+count(v.kitchen(),Items.COOKED_BEEF));
   h.assertTrue(Portions.pantryItems(3,8,Population.MEAL_NUTRITION)==3,"The pantry would have taken three");
  }finally{done(v);}
  h.succeed();
 }
 /** 7. An invitation that expires (the resident never got there) is eaten from the pantry at the same due; no meal is missed. */
 @GameTest(template="empty",timeoutTicks=200) public static void aLostInviteNeverCostsAMeal(GameTestHelper h){
  var v=village(h,1);put(v.kitchen(),new ItemStack(Items.BREAD,8));put(v.pantry(),new ItemStack(Items.BREAD,8));
  try{var r=adult(v,"lost",null,null);body(v,r,new BlockPos(3,0,10));long due=r.lastMeal()+Population.MEAL_INTERVAL;
   h.assertTrue(!Population.meal(v.l(),v.e(),r,NOW)&&Dining.invite(v.l(),v.e(),r.id())!=null,"Invited, it does not eat from the pantry yet");
   h.assertTrue(!Population.meal(v.l(),v.e(),r,NOW+Dining.WAIT_TICKS)&&r.lastMeal()<due,"While the invitation lives, the meal waits");
   h.assertTrue(Population.meal(v.l(),v.e(),r,NOW+Dining.WAIT_TICKS+1)&&r.lastMeal()==due&&r.missedMeals()==0&&Dining.invite(v.l(),v.e(),r.id())==null,"Expired, the pantry feeds it with the same due");
   h.assertTrue(count(v.pantry(),Items.BREAD)==7&&count(v.kitchen(),Items.BREAD)==8,"From the pantry, not the hall");
  }finally{done(v);}
  h.succeed();
 }
 /** 8. The record survives a reload: the invitation and the leftover are read back; a seat taken before is free again (no ghost seat). */
 @GameTest(template="empty",timeoutTicks=200) public static void theDiningRecordSurvivesAReload(GameTestHelper h){
  var v=village(h,2);var b=v.kept();put(v.kitchen(),new ItemStack(Items.COOKED_BEEF,4));
  try{var first=adult(v,"a",null,null);body(v,first,new BlockPos(3,0,10));Dining.meal(v.l(),v.e(),first,NOW,first.lastMeal()+Population.MEAL_INTERVAL);Dining.serve(v.l(),v.e(),b,first);
   var r=adult(v,"b",null,null);body(v,r,new BlockPos(4,0,10));Dining.meal(v.l(),v.e(),r,NOW,r.lastMeal()+Population.MEAL_INTERVAL);
   int seat=Dining.takeSeat(v.l(),v.e(),b,r.id());h.assertTrue(seat>=0&&Dining.taken(v.l(),v.e(),b)==1&&Dining.leftover(v.l(),v.e(),b)==3,"A seat is taken and 3 rations are left over");
   Dining.forget();
   h.assertTrue(Dining.invite(v.l(),v.e(),r.id())!=null&&Dining.leftover(v.l(),v.e(),b)==3,"The invitation and the leftover are read back");
   h.assertTrue(Dining.taken(v.l(),v.e(),b)==0,"The seat is free after the reload");
   h.assertTrue(java.nio.file.Files.exists(Dining.path(v.l(),v.s().id())),"The record is data/astra-dining/<village>.bin");
  }finally{done(v);}
  h.succeed();
 }
 /** AD-139 §1.2: a bakery of a world made before the restaurant works as one — the cook's kitchen, its core and machine — but has no hall:
  *  no seats, nobody is invited, the pantry feeds everybody. */
 @GameTest(template="empty",timeoutTicks=200) public static void anOldBakeryWorksAsARestaurantWithoutAHall(GameTestHelper h){
  var v=village(h,1,"bakery",26);var b=v.kept();put(v.kitchen(),new ItemStack(Items.BREAD,8));put(v.pantry(),new ItemStack(Items.BREAD,8));
  try{h.assertTrue(Dining.restaurant(b)&&Workshops.spec("bakery")==Workshops.spec("restaurant")&&Workshops.spec("bakery").profession()==Profession.BAKER,"The old bakery is the restaurant's kitchen");
   h.assertTrue(CoreCatalog.coreType("bakery").equals("bakery")&&CoreCatalog.coreType("restaurant").equals("bakery")&&CoreCatalog.coreId("bakery").equals("villageastra:core_bakery")&&CoreCatalog.coreId("restaurant").equals("villageastra:core_bakery"),"Its core keeps the id core_bakery");
   h.assertTrue(Automation.level("bakery",false)==6&&Automation.level("restaurant",false)==6,"Its machine is the restaurant's");
   var r=adult(v,"old",null,null);body(v,r,new BlockPos(3,0,10));
   h.assertTrue(Dining.seats(v.l(),v.e(),b)==0&&Dining.why(v.l(),v.e(),r,b).equals("no_seats")&&Dining.meal(v.l(),v.e(),r,NOW,r.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.PANTRY,"No hall in the old design: "+Dining.why(v.l(),v.e(),r,b));
   h.assertTrue(Population.meal(v.l(),v.e(),r,NOW)&&count(v.pantry(),Items.BREAD)==7,"The pantry feeds as before");
   h.assertTrue(Dining.keep(b,new ItemStack(Items.BREAD))==0,"Without a hall it keeps no dishes from the stock");
  }finally{done(v);}
  h.succeed();
 }

 /** Review: the hall's housekeeping runs on the village's own turn (Population.tick, every 20 ticks of the village clock) whatever the phase of
  *  the level's game time — at dusk the leftover of the open dish cools and the card counts it. */
 @GameTest(template="empty",timeoutTicks=200) public static void theHallsHousekeepingRunsOnThePopulationTurn(GameTestHelper h){
  var v=village(h,2);var b=v.kept();put(v.kitchen(),new ItemStack(Items.COOKED_BEEF,2));
  var r=adult(v,"cool",null,null);body(v,r,new BlockPos(3,0,10));
  Dining.meal(v.l(),v.e(),r,NOW,r.lastMeal()+Population.MEAL_INTERVAL);
  h.assertTrue(Dining.serve(v.l(),v.e(),b,r)&&Dining.leftover(v.l(),v.e(),b)==3,"Beef leaves 3 rations over");
  Dining.testDay(v.s().id(),14000L);
  Runnable dusk=()->{try{Population.tick(v.l().getServer(),SettlementData.get(v.l().getServer()),v.e());
    h.assertTrue(Dining.leftover(v.l(),v.e(),b)==0&&Dining.card(v.l(),v.e(),b).getInt("cooled")==3,"One population turn at dusk cools the leftover: left "+Dining.leftover(v.l(),v.e(),b));
   }finally{done(v);}h.succeed();};
  // A turn where the level's game time is not a multiple of 20: the old gate on it skipped the housekeeping there.
  if(v.l().getGameTime()%20==0)h.runAfterDelay(1,dusk);else dusk.run();
 }
 /** Review: an invitation ends at dusk (the hall closes) — the meal goes to the pantry at once, not after wait_ticks. */
 @GameTest(template="empty",timeoutTicks=200) public static void anInvitationEndsWhenTheHallCloses(GameTestHelper h){
  var v=village(h,1);put(v.kitchen(),new ItemStack(Items.BREAD,8));put(v.pantry(),new ItemStack(Items.BREAD,8));
  try{var r=adult(v,"dusk",null,null);body(v,r,new BlockPos(3,0,10));long due=r.lastMeal()+Population.MEAL_INTERVAL;
   h.assertTrue(!Population.meal(v.l(),v.e(),r,NOW)&&Dining.invite(v.l(),v.e(),r.id())!=null,"Invited by day");
   Dining.testDay(v.s().id(),13000L);
   h.assertTrue(Population.meal(v.l(),v.e(),r,NOW+20)&&r.lastMeal()==due&&r.missedMeals()==0&&Dining.invite(v.l(),v.e(),r.id())==null,"At dusk the pantry feeds it at once, same due");
   h.assertTrue(count(v.pantry(),Items.BREAD)==7,"From the pantry: "+count(v.pantry(),Items.BREAD));
  }finally{done(v);}
  h.succeed();
 }
 /** Review: the food of the diners already invited is theirs — one bread invites one diner; the second eats from the pantry now instead of
  *  sitting at an empty table until its invitation lapses. */
 @GameTest(template="empty",timeoutTicks=200) public static void theHallInvitesNoMoreDinersThanItsFood(GameTestHelper h){
  var v=village(h,1);var b=v.kept();put(v.kitchen(),new ItemStack(Items.BREAD,1));put(v.pantry(),new ItemStack(Items.BREAD,4));
  try{var a=adult(v,"first",null,null);body(v,a,new BlockPos(3,0,10));var c=adult(v,"second",null,null);body(v,c,new BlockPos(4,0,10));
   h.assertTrue(Dining.meal(v.l(),v.e(),a,NOW,a.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.WAIT,"The first is invited");
   h.assertTrue(Dining.why(v.l(),v.e(),c,b).equals("no_food")&&Population.meal(v.l(),v.e(),c,NOW)&&count(v.pantry(),Items.BREAD)==3,"The second is not: "+Dining.why(v.l(),v.e(),c,b));
   h.assertTrue(Dining.serve(v.l(),v.e(),b,a)&&count(v.kitchen(),Items.BREAD)==0,"The first is served its bread");
  }finally{done(v);}
  h.succeed();
 }
 /** Review: the pantries are empty, the restaurant keeps its dishes for the hall — a meal the hall cannot serve (the night) is eaten from the
  *  restaurant's dishes, never from the cook's raw meat, and nobody misses it. */
 @GameTest(template="empty",timeoutTicks=200) public static void anEmptyPantryFallsBackToTheRestaurantsDishes(GameTestHelper h){
  var v=village(h,2);put(v.kitchen(),new ItemStack(Items.BEEF,8));put(v.kitchen(),new ItemStack(Items.BREAD,2));
  try{Dining.testDay(v.s().id(),14000L);var r=adult(v,"night",null,null);body(v,r,new BlockPos(3,0,10));long due=r.lastMeal()+Population.MEAL_INTERVAL;
   h.assertTrue(Population.meal(v.l(),v.e(),r,NOW)&&r.lastMeal()==due&&r.missedMeals()==0,"Fed at night with an empty pantry: missed "+r.missedMeals());
   h.assertTrue(count(v.kitchen(),Items.BREAD)==1&&count(v.kitchen(),Items.BEEF)==8,"A bread from the restaurant, the raw beef stays the cook's: bread "+count(v.kitchen(),Items.BREAD)+" beef "+count(v.kitchen(),Items.BEEF));
   h.assertTrue(Dining.keep(v.kept(),new ItemStack(Items.BREAD))==Dining.KEEP_ITEMS,"A restaurant with a hall keeps its dishes");
  }finally{done(v);}
  h.succeed();
 }
}
