package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import static org.villageastra.gametest.RestaurantFixture.*;
/** AD-139 §10.1 (13–17): the restaurant's couriers — posts by level, a miner fed at his work, the dog and cart of level V (a stand-in dog
 *  source: the livestock work provides the real one), nothing made or lost on a round, and the hall and a courier never serving one meal. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CourierGameTests {
 /** 13. Courier posts 0/0/0/1/2/2 by the restaurant's level: the labour office opens exactly that many. */
 @GameTest(template="empty",timeoutTicks=200) public static void courierPostsFollowTheLevel(GameTestHelper h){
  for(int level=1;level<=6;level++){
   var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
   var rest=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",10,0,0);s.addBuilding(rest);
   var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,20,true);s.addHome(home);
   for(int i=0;i<8;i++)s.admit(new Resident(Settlement.childId(s.id(),"a/"+i),Resident.Life.ADULT,false,null,null,-1),home.id());
   for(int lv=2;lv<=level;lv++)s.raiseBuildingLevel(rest.id(),lv);
   var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(BlockPos.ZERO));Population.assign(e);
   int couriers=Couriers.posted(e,rest);
   h.assertTrue(couriers==Couriers.posts(level)&&couriers==CoreEffects.value("restaurant","couriers",level),"Level "+level+": "+couriers+" couriers, the table says "+CoreEffects.value("restaurant","couriers",level));
   h.assertTrue(s.residents().stream().anyMatch(r->r.profession()==Profession.PORTER&&!Dining.restaurant(s.workplace(r.id()))),"The stock keeps its own porter at level "+level);
  }
  h.succeed();
 }
 /** 14. A miner working 40 blocks from the restaurant (IV) is fed at his work by the courier: the courier loads bread at the restaurant, walks to
  *  him, serves a portion and brings the rest back; the miner never goes to the hall. */
 @GameTest(template="empty",batch="courier_meal",timeoutTicks=2400) public static void aCourierFeedsAMinerAtHisWork(GameTestHelper h){
  var v=village(h,4,"restaurant",60);put(v.kitchen(),new ItemStack(Items.BREAD,8));
  var mine=new Settlement.Building(Settlement.childId(v.s().id(),"building/mine"),"mine",50,0,6);v.s().addBuilding(mine);
  var miner=adult(v,"miner",Profession.MINER,mine);miner.ate(NOW-Population.MEAL_INTERVAL+600);var body=body(v,miner,new BlockPos(52,0,8));
  var courier=adult(v,"courier",Profession.PORTER,v.kept());courier.ate(NOW);var c=body(v,courier,new BlockPos(14,0,10));give(c,6,new CourierGoal(c,()->NOW));
  long due=miner.lastMeal()+Population.MEAL_INTERVAL;
  h.assertTrue(Dining.why(v.l(),v.e(),miner,v.kept()).equals("courier"),"The miner is the courier's, not the hall's: "+Dining.why(v.l(),v.e(),miner,v.kept()));
  h.succeedWhen(()->{
   h.assertTrue(miner.lastMeal()==due,"Not fed yet: courier "+c.workStatus()+" pos="+c.position()+" miner="+body.position()+" target="+c.getNavigation().getTargetPos());
   h.assertTrue(Dining.invite(v.l(),v.e(),miner.id())==null,"He was never invited to the hall");
   h.assertTrue(body.distanceToSqr(v.center().getX()+52.5,v.center().getY(),v.center().getZ()+8.5)<9,"He ate where he works");
   h.assertTrue(count(v.kitchen(),Items.BREAD)==7,"Once back, the courier returned what it did not open: "+count(v.kitchen(),Items.BREAD)+" "+c.workStatus());
   done(v);});
 }
 /** 15. Level V with Restaurant V: with a free dog of the village and a cart at the restaurant the courier takes the hitch and a load of 54
  *  portions within 96 blocks; without a dog it carries 16 by hand and says so. */
 @GameTest(template="empty",timeoutTicks=400) public static void aCourierWithADogPullsACart(GameTestHelper h){
  var v=village(h,5,"restaurant",60);research(v,5);put(v.kitchen(),new ItemStack(Items.BREAD,64));
  try{var courier=adult(v,"courier",Profession.PORTER,v.kept());courier.ate(NOW);var c=body(v,courier,new BlockPos(12,0,10));
   for(int i=0;i<24;i++){var r=adult(v,"far"+i,null,null);body(v,r,new BlockPos(34+i,0,12));}
   var b=v.kept();
   // AD-138 IV: the kennel wolves are the village's dogs; a village without a kennel wolf has none.
   h.assertTrue(VillageDogs.provided()&&Couriers.cartRefusal(v.l(),v.e(),b).equals("no_dog"),"Without a kennel wolf no village has a dog: "+Couriers.cartRefusal(v.l(),v.e(),b));
   Couriers.step(c,0,NOW);
   h.assertTrue("courier_no_dog".equals(c.workStatus()),"By hand, it says there is no dog: "+c.workStatus());
   h.assertTrue(Dining.tripOf(v.l(),v.e(),c.getUUID())!=null&&Dining.card(v.l(),v.e(),b).getCompound("couriers").getString("cart").equals("no_dog"),"The card says there is no free dog");
   var at=LogisticsRoutes.position(v.e(),b);var cart=new CartEntity(v.l(),at.offset(1,0,0),v.s().id());v.l().addFreshEntity(cart);
   var dog=UUID.randomUUID();VillageDogs.provide((l,e)->List.of(dog));
   try{h.assertTrue(Couriers.mayTakeCart(v.l(),v.e(),b)&&Couriers.cartRefusal(v.l(),v.e(),b).isEmpty(),"With a dog and a cart the courier may take it");
    var c2r=adult(v,"courier2",Profession.PORTER,b);c2r.ate(NOW);var c2=body(v,c2r,new BlockPos(12,0,10));
    Couriers.step(c2,0,NOW);c2.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);Couriers.step(c2,0,NOW);
    var t=Dining.tripOf(v.l(),v.e(),c2.getUUID());
    h.assertTrue(t!=null&&t.getBoolean("cart")&&t.hasUUID("cartId")&&t.getUUID("cartId").equals(cart.getUUID())&&cart.puller()!=null&&cart.puller().equals(c2.getUUID()),"The second courier took the hitch: "+t);
    h.assertTrue(t.getList("targets",10).size()<=Dining.CART_PORTIONS&&Couriers.mayPull(v.l(),v.e(),c2),"Its round is the cart's");
   }finally{VillageDogs.provide(VillageWolves.DOGS);}
   h.assertTrue(CartHitch.hitch(v.l(),v.e(),cart,c).equals("caravaneer")&&!Couriers.mayPull(v.l(),v.e(),c),"Without a dog a courier takes no hitch: "+CartHitch.hitch(v.l(),v.e(),cart,c));
  }finally{done(v);}
  h.succeed();
 }
 /** 16. Nothing is created or lost on a round: what the courier took is served or back in the chest, also when it dies on the way. */
 @GameTest(template="empty",timeoutTicks=400) public static void nothingIsCreatedOnACourierRoute(GameTestHelper h){
  var v=village(h,4,"restaurant",60);put(v.kitchen(),new ItemStack(Items.BREAD,10));
  try{var b=v.kept();var courier=adult(v,"courier",Profession.PORTER,b);courier.ate(NOW);var c=body(v,courier,new BlockPos(12,0,10));
   var eaters=new ArrayList<Resident>();for(int i=0;i<3;i++){var r=adult(v,"away"+i,null,null);body(v,r,new BlockPos(40+i,0,12));eaters.add(r);}
   var at=LogisticsRoutes.position(v.e(),b);Couriers.step(c,0,NOW);c.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);Couriers.step(c,0,NOW);
   var t=Dining.tripOf(v.l(),v.e(),c.getUUID());h.assertTrue(t!=null&&t.getString("stage").equals("deliver"),"Loaded: "+t);
   int taken=10-count(v.kitchen(),Items.BREAD);h.assertTrue(taken==3,"Three bread for three portions: "+taken);
   var first=eaters.get(0);var fb=(ResidentEntity)v.l().getEntity(first.id());c.moveTo(fb.getX()+1,fb.getY(),fb.getZ(),0,0);
   int waited=0;for(int i=0;i<20&&first.lastMeal()<NOW;i++)waited=Couriers.step(c,waited,NOW);
   h.assertTrue(first.lastMeal()==NOW,"The first resident is served");
   c.kill();v.s().residents().stream().filter(r->r.id().equals(courier.id())).findFirst().ifPresent(Resident::die);
   Dining.tick(v.l(),v.e(),NOW);
   h.assertTrue(count(v.kitchen(),Items.BREAD)==9&&Dining.tripOf(v.l(),v.e(),c.getUUID())==null,"The dead courier's load went back: one bread served, "+count(v.kitchen(),Items.BREAD)+" in the chest");
  }finally{done(v);}
  h.succeed();
 }
 /** 17. The hall and a courier never feed the same meal: an invited resident is no courier's target, and a resident on a round is not invited. */
 @GameTest(template="empty",timeoutTicks=200) public static void aCourierAndTheHallNeverFeedTheSameMeal(GameTestHelper h){
  var v=village(h,4,"restaurant",60);put(v.kitchen(),new ItemStack(Items.BREAD,16));
  try{var b=v.kept();var courier=adult(v,"courier",Profession.PORTER,b);courier.ate(NOW);var c=body(v,courier,new BlockPos(12,0,10));
   var guest=adult(v,"guest",null,null);body(v,guest,new BlockPos(18,0,11));
   long due=guest.lastMeal()+Population.MEAL_INTERVAL;
   // Standing by the hall with no work far off, the resident is the hall's; invited, it is nobody's target.
   h.assertTrue(Dining.meal(v.l(),v.e(),guest,NOW,due)==Dining.Outcome.WAIT&&Dining.invite(v.l(),v.e(),guest.id())!=null,"Invited: "+Dining.why(v.l(),v.e(),guest,b));
   h.assertTrue(Couriers.targets(v.l(),v.e(),b,NOW,Dining.COURIER_RADIUS).stream().noneMatch(r->r.id().equals(guest.id())),"An invited resident is no courier's target");
   var mine=new Settlement.Building(Settlement.childId(v.s().id(),"building/mine"),"mine",50,0,6);v.s().addBuilding(mine);
   var miner=adult(v,"miner",Profession.MINER,mine);body(v,miner,new BlockPos(50,0,8));
   Couriers.step(c,0,NOW);var t=Dining.tripOf(v.l(),v.e(),c.getUUID());
   h.assertTrue(t!=null&&t.getList("targets",10).stream().anyMatch(q->((net.minecraft.nbt.CompoundTag)q).getUUID("r").equals(miner.id())),"The miner is on the round");
   h.assertTrue(Dining.meal(v.l(),v.e(),miner,NOW,miner.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.WAIT&&Dining.invite(v.l(),v.e(),miner.id())==null,"A resident on a round waits for its courier and is not invited");
   h.assertTrue(Dining.meal(v.l(),v.e(),miner,NOW+Dining.RESERVE_TICKS+1,miner.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.PANTRY&&Dining.invite(v.l(),v.e(),miner.id())==null,"Once the round's hold lapses, the pantry feeds it");
  }finally{done(v);}
  h.succeed();
 }

 /** Review: the restaurant's chest is destroyed while a courier carries a load — the load goes to the pantry, not into limbo; and a round
  *  heading back no longer holds its unserved residents (they eat from the pantry at once). */
 @GameTest(template="empty",timeoutTicks=400) public static void aLoadWithoutItsChestGoesToThePantry(GameTestHelper h){
  var v=village(h,4,"restaurant",60);put(v.kitchen(),new ItemStack(Items.BREAD,10));
  try{var b=v.kept();var courier=adult(v,"courier",Profession.PORTER,b);courier.ate(NOW);var c=body(v,courier,new BlockPos(12,0,10));
   var eaters=new ArrayList<Resident>();for(int i=0;i<2;i++){var r=adult(v,"away"+i,null,null);body(v,r,new BlockPos(40+i,0,12));eaters.add(r);}
   var at=LogisticsRoutes.position(v.e(),b);Couriers.step(c,0,NOW);c.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);Couriers.step(c,0,NOW);
   var t=Dining.tripOf(v.l(),v.e(),c.getUUID());h.assertTrue(t!=null&&t.getString("stage").equals("deliver")&&count(v.kitchen(),Items.BREAD)==8,"Loaded two bread: "+t+" "+c.workStatus()+" chest "+count(v.kitchen(),Items.BREAD));
   var r0=eaters.get(0);h.assertTrue(Dining.meal(v.l(),v.e(),r0,NOW,r0.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.WAIT,"On the round, it waits for the courier");
   Dining.testDay(v.s().id(),14000L);Couriers.step(c,0,NOW);
   h.assertTrue("return".equals(Dining.tripOf(v.l(),v.e(),c.getUUID()).getString("stage")),"At dusk the round heads back");
   h.assertTrue(Dining.meal(v.l(),v.e(),r0,NOW,r0.lastMeal()+Population.MEAL_INTERVAL)==Dining.Outcome.PANTRY,"A round heading back holds nobody");
   v.l().removeBlock(at,false);
   c.kill();v.s().residents().stream().filter(r->r.id().equals(courier.id())).findFirst().ifPresent(Resident::die);
   Dining.tick(v.l(),v.e(),NOW);
   h.assertTrue(Dining.tripOf(v.l(),v.e(),c.getUUID())==null&&count(v.pantry(),Items.BREAD)==2,"The load is in the pantry: "+count(v.pantry(),Items.BREAD));
  }finally{done(v);}
  h.succeed();
 }
}
