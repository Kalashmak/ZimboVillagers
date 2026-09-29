package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** ROAD-008: one simple cart, pulled by a caravaneer on foot. It has a real size and a real hold, it never passes through a wall and never
 *  teleports, its load is in exactly one place at a time, and it keeps that load across a save. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CartGameTests {
 private record Yard(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center){}
 private static Yard yard(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/caravan"),"caravan",14,0,0));
  for(int x=-2;x<24;x++)for(int z=-2;z<20;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Yard(l,e,s,center);
 }
 /** A caravaneer of this settlement, standing where it is put. */
 private static ResidentEntity caravaneer(Yard t,BlockPos at){
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
  var home=new Settlement.Home(Settlement.childId(t.s.id(),"home/"+at.getX()+"/"+at.getZ()),1,4,true);
  t.s.addHome(home);t.s.admit(r,home.id());
  var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
  t.s.assign(r.id(),Profession.CARAVANEER,yard.id());
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);
  npc.moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5,0,0);t.l.addFreshEntity(npc);return npc;
 }
 private static CartEntity cart(Yard t,BlockPos at){
  var cart=new CartEntity(t.l,at.above(),t.s.id());t.l.addFreshEntity(cart);return cart;
 }
 private static void done(Yard t){
  // The people and carts of a finished fixture leave with it, so nothing of it is left to die or roll in a later test.
  var box=new net.minecraft.world.phys.AABB(t.center).inflate(64);
  for(var npc:t.l.getEntitiesOfClass(ResidentEntity.class,box,x->t.s.id().equals(x.settlementId())))npc.discard();
  for(var cart:t.l.getEntitiesOfClass(CartEntity.class,box,x->t.s.id().equals(x.settlement())))cart.discard();
  SettlementData.get(t.l.getServer()).remove(t.s.id());}
 /** caravan-cart-client: the gate fell on the caravan yard, the caravaneer appeared on its roof and never reached the cart below. */
 @GameTest(template="empty",timeoutTicks=100) public static void aTripsGateIsOutsideTheVillagesBuildings(GameTestHelper h){
  var t=yard(h);
  try{
   // Test plots lie under the generated ground: the road out of the village is opened to the sky, so the gate sees its real ground.
   for(int x=0;x<=40;x++)for(int z=-1;z<=1;z++){t.l.setBlock(t.center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=1;y<=t.l.getMaxBuildHeight()-t.center.getY()-1;y++){var at=t.center.offset(x,y,z);if(!t.l.getBlockState(at).isAir())t.l.setBlock(at,Blocks.AIR.defaultBlockState(),2);}}
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,t.center.offset(Caravans.GATE,0,0)),"The yard stands where the gate used to be");
   var gate=Caravans.gate(t.l,t.e,t.center.offset(200,0,0));
   h.assertTrue(!OwnershipEvents.protectedBlock(t.l,gate.below())&&gate.getX()-t.center.getX()>Caravans.GATE,"The gate steps out past the yard: "+gate.subtract(t.center).toShortString());
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aCartIsPulledByItsOwnCaravaneerAndByNobodyElse(GameTestHelper h){
  var t=yard(h);
  try{
   var cart=cart(t,t.center.offset(6,0,6));
   var stranger=VillageAstra.RESIDENT.get().create(t.l);stranger.setNoAi(true);
   stranger.moveTo(t.center.getX()+6.5,t.center.getY()+1,t.center.getZ()+6.5,0,0);t.l.addFreshEntity(stranger);
   h.assertTrue(CartHitch.hitch(t.l,t.e,cart,stranger).equals("caravaneer"),"Somebody who is not the caravaneer takes no hitch");
   var driver=caravaneer(t,t.center.offset(6,0,7));
   h.assertTrue(CartHitch.hitch(t.l,t.e,cart,driver).isEmpty(),"The settlement's caravaneer takes the hitch");
   h.assertTrue(cart.puller().equals(driver.getUUID()),"And the cart knows who pulls it");
   var farAway=caravaneer(t,t.center.offset(20,0,18));
   h.assertTrue(CartHitch.hitch(t.l,t.e,cart,farAway).equals("taken"),"A cart that is already pulled is not taken from its puller");
   CartHitch.unhitch(cart);
   h.assertTrue(CartHitch.hitch(t.l,t.e,cart,farAway).equals("reach"),"And from across the village nobody reaches the hitch");
   stranger.discard();
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aCartFollowsItsPullerButNeverPassesAWall(GameTestHelper h){
  var t=yard(h);
  try{
   var start=t.center.offset(6,0,6);
   var cart=cart(t,start);var driver=caravaneer(t,t.center.offset(6,0,7));
   h.assertTrue(CartHitch.hitch(t.l,t.e,cart,driver).isEmpty(),"The caravaneer takes the hitch");
   // The caravaneer walks on down the lane; the cart is expected to roll after it.
   driver.moveTo(t.center.getX()+6.5,t.center.getY()+1,t.center.getZ()+12.5,0,0);
   for(int i=0;i<60;i++)cart.tick();
   h.assertTrue(cart.getZ()>start.getZ()+1.5,"The cart really rolls after its puller: z="+cart.getZ()+" from "+(start.getZ()+.5));
   h.assertTrue(!cart.blocked(),"Rolling freely it is not called blocked");
   // A wall of the village stands across the lane: the cart stops at it instead of passing through.
   double at=cart.getZ();
   for(int x=4;x<=9;x++)for(int y=1;y<=2;y++)t.l.setBlock(new BlockPos(t.center.getX()+x,t.center.getY()+y,(int)Math.floor(at)+2),Blocks.STONE_BRICKS.defaultBlockState(),3);
   driver.moveTo(t.center.getX()+6.5,t.center.getY()+1,(double)((int)Math.floor(at)+6),0,0);
   for(int i=0;i<80;i++)cart.tick();
   h.assertTrue(cart.getZ()<(int)Math.floor(at)+2,"The cart never passes the wall: z="+cart.getZ()+" wall at "+((int)Math.floor(at)+2));
   h.assertTrue(cart.blocked(),"A cart that cannot follow says so, so the trip waits instead of teleporting");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theLoadOfATripIsInExactlyOnePlace(GameTestHelper h){
  var t=yard(h);
  try{
   var cart=cart(t,t.center.offset(3,0,4));
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   hallChest.setItem(0,new ItemStack(Items.WHEAT,32));
   var hall=LogisticsRoutes.position(t.e,Workshops.hall(t.e));
   int loaded=CartHitch.load(t.l,cart,hall,0,12,UUID.randomUUID());
   h.assertTrue(loaded==12,"Twelve sheaves go into the hold: "+loaded);
   h.assertTrue(hallChest.countItem(Items.WHEAT)==20&&CartHitch.count(cart,Items.WHEAT)==12,
     "And they are in the cart, not in both places: chest="+hallChest.countItem(Items.WHEAT)+" cart="+CartHitch.count(cart,Items.WHEAT));
   var box=t.center.offset(8,1,4);t.l.setBlock(box.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
   t.l.setBlock(box,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   int unloaded=CartHitch.unload(t.l,cart,box,0,12,UUID.randomUUID());
   var far=(net.minecraft.world.Container)t.l.getBlockEntity(box);
   h.assertTrue(unloaded==12&&far.countItem(Items.WHEAT)==12&&CartHitch.count(cart,Items.WHEAT)==0,
     "The load arrives whole and leaves the hold empty: "+unloaded+" "+far.countItem(Items.WHEAT)+" "+CartHitch.count(cart,Items.WHEAT));
   h.assertTrue(hallChest.countItem(Items.WHEAT)+far.countItem(Items.WHEAT)==32,"Nothing is made or lost on the way: "+hallChest.countItem(Items.WHEAT)+"+"+far.countItem(Items.WHEAT));
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aCartKeepsItsLoadAndItsVillageAcrossASave(GameTestHelper h){
  var t=yard(h);
  try{
   var cart=cart(t,t.center.offset(5,0,9));
   CartHitch.put(cart,new ItemStack(Items.BREAD,7));
   var saved=new CompoundTag();cart.saveWithoutId(saved);
   var restored=new CartEntity(VillageAstra.CART.get(),t.l);restored.load(saved);
   h.assertTrue(CartHitch.count(restored,Items.BREAD)==7,"The hold survives a save: "+CartHitch.count(restored,Items.BREAD));
   h.assertTrue(t.s.id().equals(restored.settlement()),"And the cart still belongs to its village");
  }finally{done(t);}
  h.succeed();
 }

 /** Two villages a lane apart; the source has a caravan yard, a caravaneer and wheat in its hall. */
 private static CompoundTag trip(Yard from,Yard to,UUID caravaneer,int count){
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","trade");
  t.putUUID("source",from.s.id());t.putUUID("destination",to.s.id());t.putString("dimension",from.l.dimension().location().toString());
  t.putString("item","minecraft:wheat");t.putInt("count",count);
  var price=Trade.price(Items.WHEAT);t.putInt("price",price==null?1:price.coins());t.putInt("per",price==null?1:Math.max(1,price.per()));
  t.putUUID("caravaneer",caravaneer);t.put("cargo",new net.minecraft.nbt.ListTag());t.put("stamps",new CompoundTag());
  t.putInt("takes",0);t.putInt("drops",0);t.putInt("delivered",0);t.putInt("lost",0);
  t.putLong("from",from.center.asLong());t.putLong("to",to.center.asLong());t.putDouble("progress",0);t.putBoolean("materialized",false);
  t.putString("state",Caravans.ACCEPTED);return t;
 }
 private static Yard neighbour(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,34));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Yard(l,e,s,center);
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aCaravanTakesTheCartCarriesTwiceAsMuchAndBringsItHome(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   var cart=cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
   var driver=caravaneer(t,t.center.offset(12,0,4));
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   hallChest.setItem(0,new ItemStack(Items.WHEAT,64));hallChest.setItem(1,new ItemStack(Items.WHEAT,64));
   h.assertTrue(Caravans.freeCart(t.l,t.e)==cart,"The yard's cart is free for a trip");
   var run=trip(t,d,driver.getUUID(),128);
   int loaded=Caravans.secure(t.l,run,1);
   h.assertTrue(loaded==128,"With the cart the trip takes twice what one caravaneer carries: "+loaded);
   h.assertTrue(run.contains("cart")&&!cart.isAlive(),"The cart has left the yard with the trip, it is not also still standing there");
   h.assertTrue(hallChest.countItem(Items.WHEAT)==0,"And the wheat has left the hall: "+hallChest.countItem(Items.WHEAT));
   run.putString("state",Caravans.TRANSIT);Caravans.arrive(t.l,run,2);
   var theirs=LogisticsRoutes.chest(d.l,d.e,Workshops.hall(d.e));
   h.assertTrue(theirs.countItem(Items.WHEAT)==128,"All of it arrives: "+theirs.countItem(Items.WHEAT));
   h.assertTrue(run.getString("state").equals(Caravans.RETURNING)&&run.contains("cart"),"The cart travels home with the caravaneer");
   Caravans.arrive(t.l,run,3);
   var home=CartHitch.near(t.l,t.e,BuildingPlacement.origin(t.e,yard),16);
   h.assertTrue(home!=null&&home.isEmpty()&&!run.contains("cart"),"Home again, the cart stands at its yard, empty, and the record lets it go");
   h.assertTrue(t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(t.center).inflate(40),c->t.s.id().equals(c.settlement())).size()==1,
     "There is still exactly one cart");
   home.discard();
   // A second trip, with no cart in the yard, takes what one pair of hands carries.
   hallChest.setItem(0,new ItemStack(Items.WHEAT,64));hallChest.setItem(1,new ItemStack(Items.WHEAT,64));
   var onFoot=trip(t,d,driver.getUUID(),128);
   h.assertTrue(Caravans.secure(t.l,onFoot,4)==Caravans.CARRY&&!onFoot.contains("cart"),"On foot the load is one caravaneer's: "+onFoot.getInt("secured"));
  }finally{done(t);done(d);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=300) public static void onTheRoadTheCartWalksBehindItsCaravaneerAndNeverDoubles(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
   var driver=caravaneer(t,t.center.offset(12,0,4));
   LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)).setItem(0,new ItemStack(Items.WHEAT,64));
   var run=trip(t,d,driver.getUUID(),64);
   h.assertTrue(Caravans.secure(t.l,run,1)>0&&run.contains("cart"),"The trip leaves with the cart");
   run.putString("state",Caravans.TRANSIT);Caravans.update(t.l.getServer(),run);
   // A player comes near the road: the caravaneer walks there in the flesh, and the cart behind it.
   var npc=Caravans.materialize(t.l,run,t.center.offset(10,0,20));
   h.assertTrue(npc!=null,"The caravaneer walks on the road");
   var bodies=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(t.center).inflate(48),c->run.getUUID("id").equals(c.trip()));
   h.assertTrue(bodies.size()==1&&npc.getUUID().equals(bodies.get(0).puller()),"Exactly one cart walks behind it, hitched: "+bodies.size());
   var body=bodies.get(0);
   h.assertTrue(body.travelling()&&!Caravans.cartStale(body),"The cart on the road is the trip's own");
   h.assertTrue(run.contains("cart"),"The record still carries the cart: the body on the road is only what a player sees");
   // A stale body left in a chunk from an earlier sighting goes away by itself.
   var old=new CartEntity(VillageAstra.CART.get(),t.l);old.setPos(body.getX()+3,body.getY(),body.getZ());
   old.travel(run.getUUID("id"),body.tripEpoch()-1);t.l.addFreshEntity(old);old.tick();
   h.assertTrue(old.isRemoved(),"A body of an older sighting leaves instead of doubling the cart");
   // The player walks away: the caravaneer leaves the world, and the cart's body with it.
   npc.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);Caravans.dematerialize(npc);
   h.assertTrue(body.isRemoved()&&run.contains("cart"),"The cart's body goes with the caravaneer; the cart itself stays with the trip");
   run.putString("state",Caravans.CANCELLED);Caravans.update(t.l.getServer(),run);
  }finally{done(t);done(d);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=300) public static void aCaravaneerWaitsForACartLeftBehindAndGoesBackForIt(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
   var driver=caravaneer(t,t.center.offset(12,0,4));
   LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)).setItem(0,new ItemStack(Items.WHEAT,64));
   var run=trip(t,d,driver.getUUID(),64);
   h.assertTrue(Caravans.secure(t.l,run,1)>0&&run.contains("cart"),"The trip leaves with the cart");
   run.putString("state",Caravans.TRANSIT);Caravans.update(t.l.getServer(),run);
   var npc=Caravans.materialize(t.l,run,t.center.offset(10,0,20));
   var body=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(t.center).inflate(48),c->run.getUUID("id").equals(c.trip())).get(0);
   // The cart is stuck eight blocks back: the caravaneer does not walk on without it.
   body.setPos(npc.getX(),npc.getY(),npc.getZ()-8);
   var goal=new CaravanGoal(npc);goal.tick();
   double progress=run.getDouble("progress");
   h.assertTrue(npc.workStatus().equals("caravan_waiting_for_cart"),"The caravaneer waits for the cart: "+npc.workStatus());
   for(int i=0;i<120;i++)goal.tick();
   h.assertTrue(npc.workStatus().equals("caravan_fetching_cart"),"After a while it goes back for it: "+npc.workStatus());
   h.assertTrue(run.getDouble("progress")<=progress+.5,"And the trip does not count a step it has not made with the cart: "+run.getDouble("progress"));
   run.putString("state",Caravans.CANCELLED);Caravans.update(t.l.getServer(),run);
  }finally{done(t);done(d);}
  h.succeed();
 }

 @GameTest(template="empty",timeoutTicks=300) public static void onOrderATripLeavesAStuckCartAndGoesOnOnFoot(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
   var driver=caravaneer(t,t.center.offset(12,0,4));
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   hallChest.setItem(0,new ItemStack(Items.WHEAT,64));hallChest.setItem(1,new ItemStack(Items.WHEAT,64));
   var run=trip(t,d,driver.getUUID(),128);
   h.assertTrue(Caravans.secure(t.l,run,1)==128&&run.contains("cart"),"The trip leaves with the cart and 128 sheaves");
   run.putString("state",Caravans.TRANSIT);run.putDouble("progress",4);Caravans.update(t.l.getServer(),run);
   // The mayor sees the stuck cart: the caravaneer is walking the road near a player, and the cart behind it.
   var npc=Caravans.materialize(t.l,run,t.center.offset(10,0,12));
   h.assertTrue(npc!=null,"The caravaneer walks on the road");
   var road=npc.blockPosition();
   int left=Caravans.leaveCart(t.l,run);
   h.assertTrue(left==64,"What one pair of hands cannot carry stays in the cart: "+left+" at "+road.toShortString()+" holds "+t.l.getBlockState(road)+" over "+t.l.getBlockState(road.below()));
   int carried=0;for(var raw:run.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND))carried+=ItemStack.of((CompoundTag)raw).getCount();
   h.assertTrue(carried==Caravans.CARRY&&!run.contains("cart"),"The caravaneer goes on with one load and no cart: "+carried);
   var stand=BlockPos.of(run.getLong("cartLeftAt"));
   var parked=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(stand).inflate(8),c->t.s.id().equals(c.settlement()));
   h.assertTrue(parked.size()==1&&Caravans.offRoute(run,parked.get(0).blockPosition())>=2,"The cart is left at the roadside, not across the road: "+(parked.isEmpty()?"none":Caravans.offRoute(run,parked.get(0).blockPosition())));
   h.assertTrue(parked.size()==1&&!parked.get(0).travelling()&&CartHitch.count(parked.get(0),Items.WHEAT)==64,
     "The cart stands on the road, an ordinary cart of the village, with the rest of the load: "+parked.size());
   h.assertTrue(carried+CartHitch.count(parked.get(0),Items.WHEAT)==128,"Nothing is made or lost by the reloading");
   h.assertTrue(Caravans.leaveCart(t.l,run)==-1,"A trip that has no cart any more has none to leave");
   run.putString("state",Caravans.CANCELLED);Caravans.update(t.l.getServer(),run);
  }finally{done(t);done(d);}
  h.succeed();
 }

 /** A trip that left its cart on the road: the cart, a hold of 64 sheaves in it, and the trip closed — its caravaneer free again. */
 private static CompoundTag leftOnTheRoad(GameTestHelper h,Yard t,Yard d){
  var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
  cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
  var driver=caravaneer(t,t.center.offset(12,0,4));
  var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
  hallChest.setItem(0,new ItemStack(Items.WHEAT,64));hallChest.setItem(1,new ItemStack(Items.WHEAT,64));
  var run=trip(t,d,driver.getUUID(),128);
  h.assertTrue(Caravans.secure(t.l,run,1)==128&&run.contains("cart"),"The trip leaves with the cart and 128 sheaves");
  run.putString("state",Caravans.TRANSIT);run.putDouble("progress",4);Caravans.update(t.l.getServer(),run);
  var npc=Caravans.materialize(t.l,run,t.center.offset(10,0,12));
  h.assertTrue(npc!=null&&Caravans.leaveCart(t.l,run)==64,"The mayor has the stuck cart left with 64 sheaves in it");
  h.assertTrue(Caravans.proposeRecovery(t.l,t.e,2)==null,"While its caravaneer is still on the road nobody is free to fetch the cart");
  run.putString("state",Caravans.CLOSED);Caravans.update(t.l.getServer(),run);npc.discard();
  return run;
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aCartLeftOnTheRoadIsFetchedHomeWithItsLoad(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var run=leftOnTheRoad(h,t,d);var stand=BlockPos.of(run.getLong("cartLeftAt"));
   var fetch=Caravans.proposeRecovery(t.l,t.e,3);
   h.assertTrue(fetch!=null&&fetch.getString("kind").equals(Caravans.RECOVER)&&BlockPos.of(fetch.getLong("to")).equals(stand),"The free caravaneer is sent for the cart: "+fetch);
   h.assertTrue(Caravans.proposeRecovery(t.l,t.e,4)==null,"And only one");
   Caravans.dispatch(t.l,fetch,5);
   h.assertTrue(fetch.getString("state").equals(Caravans.TRANSIT),"The fetch trip sets out");
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,6);
   var still=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(stand).inflate(8),c->t.s.id().equals(c.settlement()));
   h.assertTrue(fetch.contains("cart")&&still.isEmpty()&&fetch.getString("state").equals(Caravans.RETURNING),"At the cart it is taken up, hold and all, and the caravaneer turns home: "+still.size()+" "+fetch.getString("state"));
   h.assertTrue(Caravans.contract(t.l.getServer(),run.getUUID("id")).getBoolean("cartFetched"),"The first trip knows its cart is on its way home");
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   h.assertTrue(hallChest.countItem(Items.WHEAT)==0,"Nothing is home before the cart");
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,7);
   h.assertTrue(fetch.getString("state").equals(Caravans.CLOSED)&&!fetch.contains("cart"),"Home: the fetch trip is over");
   h.assertTrue(hallChest.countItem(Items.WHEAT)==64,"The 64 sheaves the cart held are back in the stock: "+hallChest.countItem(Items.WHEAT));
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   var home=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(BuildingPlacement.origin(t.e,yard)).inflate(16),c->t.s.id().equals(c.settlement()));
   h.assertTrue(home.size()==1&&home.get(0).isEmpty()&&!home.get(0).travelling(),"And the empty cart stands at its yard again: "+home.size());
   h.assertTrue(Caravans.proposeRecovery(t.l,t.e,8)==null,"A fetched cart is not fetched again");
  }finally{done(t);done(d);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aFetchTripForACartThatIsGoneComesHomeEmptyHanded(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var run=leftOnTheRoad(h,t,d);var stand=BlockPos.of(run.getLong("cartLeftAt"));
   // A player broke the cart up on the road before anybody came for it.
   for(var c:t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(stand).inflate(8),c->t.s.id().equals(c.settlement())))c.discard();
   var fetch=Caravans.proposeRecovery(t.l,t.e,3);Caravans.dispatch(t.l,fetch,5);
   for(int i=0;i<Caravans.RECOVER_SEARCH-1;i++){fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,6+i);
    h.assertTrue(fetch.getString("state").equals(Caravans.TRANSIT),"The caravaneer looks for the cart a while: "+i);}
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,40);
   h.assertTrue(fetch.getString("state").equals(Caravans.RETURNING)&&!fetch.contains("cart"),"Then turns home without it");
   h.assertTrue(Caravans.contract(t.l.getServer(),run.getUUID("id")).getBoolean("cartGone"),"The first trip records the cart as gone");
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,41);
   h.assertTrue(fetch.getString("state").equals(Caravans.CLOSED)&&Caravans.proposeRecovery(t.l,t.e,42)==null,"And nobody is sent for it again");
  }finally{done(t);done(d);}
  h.succeed();
 }
 /** Review #4: the hold of a travelling body is a copy of the load its record owns — a hopper that emptied it would double the goods. */
 @GameTest(template="empty",timeoutTicks=100) public static void noHopperEmptiesOrFillsATravellingCartsBody(GameTestHelper h){
  var t=yard(h);var at=t.center.offset(20,1,16);
  try{
   t.l.setBlock(at,Blocks.HOPPER.defaultBlockState(),3);
   var hopper=(net.minecraft.world.level.block.entity.HopperBlockEntity)t.l.getBlockEntity(at);
   var parked=cart(t,at);CartHitch.put(parked,new ItemStack(Items.WHEAT,8));
   h.assertTrue(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(t.l,hopper)&&CartHitch.count(parked,Items.WHEAT)==7,"A hopper under a cart standing in the village unloads it like a chest: "+CartHitch.count(parked,Items.WHEAT));
   parked.discard();
   var body=new CartEntity(t.l,at.above(),t.s.id());CartHitch.put(body,new ItemStack(Items.WHEAT,8));body.travel(UUID.randomUUID(),1);t.l.addFreshEntity(body);
   var wheat=new ItemStack(Items.WHEAT);
   h.assertTrue(!body.canPlaceItem(0,wheat)&&!body.canTakeItem(hopper,0,wheat),"A cart on the road with its trip gives nothing to a hopper and takes nothing from one");
   h.assertTrue(!net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(t.l,hopper)&&CartHitch.count(body,Items.WHEAT)==8,"And the hopper under it takes nothing: "+CartHitch.count(body,Items.WHEAT));
  }finally{
   // The hopper goes with the fixture, emptied first so the sheaf it took is not dropped and it pulls nothing from a later test.
   if(t.l.getBlockEntity(at) instanceof net.minecraft.world.Container box)box.clearContent();t.l.setBlock(at,Blocks.AIR.defaultBlockState(),2);done(t);}
  h.succeed();
 }
 /** Review #15: an odd first stack must not let the second take run up to what the contract asks instead of what one pair of hands carries. */
 @GameTest(template="empty",timeoutTicks=200) public static void onFootATripNeverLoadsMoreThanOnePairOfHandsCarries(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var driver=caravaneer(t,t.center.offset(12,0,4));
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   hallChest.setItem(0,new ItemStack(Items.WHEAT,63));hallChest.setItem(1,new ItemStack(Items.WHEAT,64));
   h.assertTrue(Caravans.freeCart(t.l,t.e)==null,"There is no cart in the yard");
   var run=trip(t,d,driver.getUUID(),128);
   int loaded=Caravans.secure(t.l,run,1);
   int carried=0;for(var raw:run.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND))carried+=ItemStack.of((CompoundTag)raw).getCount();
   h.assertTrue(loaded==Caravans.CARRY&&carried==Caravans.CARRY&&!run.contains("cart"),"On foot the load is one caravaneer's, however the stock is stacked: "+loaded+" "+carried);
   h.assertTrue(hallChest.countItem(Items.WHEAT)==127-Caravans.CARRY,"And the rest stays in the hall: "+hallChest.countItem(Items.WHEAT));
   run.putString("state",Caravans.CANCELLED);Caravans.update(t.l.getServer(),run);
  }finally{done(t);done(d);}
  h.succeed();
 }
 /** Review #16: a trade trip whose caravaneer dies with the cart leaves the cart on the road, and the village sends for it like for one left on order. */
 @GameTest(template="empty",timeoutTicks=300) public static void aCartWhoseCaravaneerDiedOnTheRoadIsFetchedHome(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));
   var driver=caravaneer(t,t.center.offset(12,0,4));
   LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)).setItem(0,new ItemStack(Items.WHEAT,64));
   var run=trip(t,d,driver.getUUID(),64);
   h.assertTrue(Caravans.secure(t.l,run,1)>0&&run.contains("cart"),"The trip leaves with the cart");
   run.putString("state",Caravans.TRANSIT);run.putDouble("progress",4);Caravans.update(t.l.getServer(),run);
   var npc=Caravans.materialize(t.l,run,t.center.offset(10,0,12));
   h.assertTrue(npc!=null,"The caravaneer walks on the road");
   var spot=npc.blockPosition();
   // Killed on the road: the death handler ends the trip, then the village buries its resident.
   Caravans.died(npc,2);t.s.resident(driver.getUUID()).die();
   for(var drop:t.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(spot).inflate(3)))drop.discard();
   h.assertTrue(run.getString("state").equals(Caravans.CLOSED)&&!run.contains("cart"),"The trip is over and its record carries the cart no more");
   h.assertTrue(run.hasUUID("leftCart")&&run.contains("cartLeftAt")&&t.l.getEntity(run.getUUID("leftCart")) instanceof CartEntity fallen&&!fallen.travelling()&&fallen.blockPosition().asLong()==run.getLong("cartLeftAt"),
     "The cart stays on the road where it fell, and the record knows where");
   var other=caravaneer(t,t.center.offset(12,0,8));
   var fetch=Caravans.proposeRecovery(t.l,t.e,3);
   h.assertTrue(fetch!=null&&fetch.getString("kind").equals(Caravans.RECOVER)&&fetch.getUUID("fetchCart").equals(run.getUUID("leftCart"))&&fetch.getUUID("caravaneer").equals(other.getUUID()),"Another caravaneer of the village is sent for it: "+fetch);
   fetch.putString("state",Caravans.CANCELLED);Caravans.update(t.l.getServer(),fetch);
  }finally{done(t);done(d);}
  h.succeed();
 }
 /** Review #5: the stock chest has filled while the cart was away; what it cannot take waits in the cart's hold, not in a closed record. */
 @GameTest(template="empty",timeoutTicks=300) public static void whatAFullStockCannotTakeStaysInTheFetchedCartsHold(GameTestHelper h){
  var t=yard(h);var d=neighbour(h);
  try{
   leftOnTheRoad(h,t,d);
   var fetch=Caravans.proposeRecovery(t.l,t.e,3);Caravans.dispatch(t.l,fetch,5);
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,6);
   h.assertTrue(fetch.contains("cart")&&fetch.getString("state").equals(Caravans.RETURNING),"The cart is taken up, hold and all");
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   for(int i=0;i<hallChest.getContainerSize();i++)hallChest.setItem(i,new ItemStack(Items.COBBLESTONE,64));
   fetch.putDouble("progress",Caravans.length(fetch));Caravans.arrive(t.l,fetch,7);
   h.assertTrue(fetch.getString("state").equals(Caravans.CLOSED)&&fetch.getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty()&&!fetch.contains("cart"),"Home: the closed record keeps neither goods nor cart");
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   var home=t.l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(BuildingPlacement.origin(t.e,yard)).inflate(16),c->t.s.id().equals(c.settlement()));
   h.assertTrue(home.size()==1&&CartHitch.count(home.get(0),Items.WHEAT)==64,"The 64 sheaves the full stock could not take wait in the cart at its yard: "+home.size()+" "+(home.isEmpty()?0:CartHitch.count(home.get(0),Items.WHEAT)));
  }finally{done(t);done(d);}
  h.succeed();
 }

 /** A cart that came home with goods the full stock could not take is emptied into the stock as room appears, and is free again. */
 @GameTest(template="empty",timeoutTicks=100) public static void aCartHomeWithGoodsIsEmptiedIntoTheStock(GameTestHelper h){
  var t=yard(h);
  try{
   var yard=t.s.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   var cart=cart(t,BuildingPlacement.origin(t.e,yard).offset(2,0,2));CartHitch.put(cart,new ItemStack(Items.WHEAT,10));
   h.assertTrue(Caravans.freeCart(t.l,t.e)==null,"A cart with goods in its hold is not free for a trip");
   var hallChest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));int before=hallChest.countItem(Items.WHEAT);
   h.assertTrue(Caravans.emptyYardCarts(t.l,t.e,7)==10&&hallChest.countItem(Items.WHEAT)==before+10&&cart.isEmpty(),"Its hold goes to the stock: "+hallChest.countItem(Items.WHEAT));
   h.assertTrue(Caravans.freeCart(t.l,t.e)==cart,"And the cart is free again");
  }finally{done(t);}
  h.succeed();
 }
}
