package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.world.*;
import org.villageastra.server.SettlementData;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaravanDogGameTests {
 private record Fixture(TradeLadderGameTests.Town town,Wolf dog,CompoundTag parent,Container stock){}
 private static Fixture fixture(GameTestHelper h,int level){
  var v=TradeLadderGameTests.town(h,new BlockPos(6,3,6),level);var l=v.l();var e=v.e();
  var livestock=new Settlement.Building(UUID.randomUUID(),"livestock",0,0,25);v.s().addBuilding(livestock);var kennel=new Settlement.Building(UUID.randomUUID(),VillageWolves.TYPE,0,0,18);v.s().addBuilding(kennel);v.s().linkAnnex(kennel.id(),livestock.id());
  var dog=EntityType.WOLF.create(l);dog.setTame(true);dog.setOwnerUUID(UUID.randomUUID());dog.moveTo(e.center().getX()+.5,e.center().getY()+1,e.center().getZ()+.5);l.addFreshEntity(dog);
  h.assertTrue(VillageWolves.enlist(l,e,dog,kennel),"Real wolf registered in the kennel");dog.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
  for(int i=0;i<2;i++){var at=CartHitch.parking(l,BuildingPlacement.origin(e,Caravans.yard(e)).offset(i*3,0,0));h.assertTrue(at!=null,"Parking exists");l.addFreshEntity(new CartEntity(l,at,v.s().id()));}
  var pos=LogisticsRoutes.position(e,Workshops.hall(e));l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);var stock=(Container)l.getBlockEntity(pos);
  for(int i=0;i<22;i++)stock.setItem(i,new ItemStack(Items.BREAD,64));
  var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putUUID("caravaneer",UUID.randomUUID());t.putUUID("source",v.s().id());t.putUUID("destination",v.s().id());
  t.putString("kind","trade");t.putString("state",Caravans.ACCEPTED);t.putString("dimension",e.dimension());t.putString("item","minecraft:bread");t.putInt("count",1000);t.putInt("price",1);t.putInt("per",1);
  t.putLong("from",e.center().above().asLong());t.putLong("to",e.center().offset(80,1,0).asLong());t.put("cargo",new ListTag());Caravans.update(l.getServer(),t);return new Fixture(v,dog,t,stock);
 }
 private static CompoundTag launch(Fixture f){var l=f.town.l();if(Caravans.secure(l,f.parent,100)<=0)throw new IllegalStateException("Fixture did not load real stock");Caravans.dispatch(l,f.parent,100);return CaravanDogs.child(l.getServer(),f.parent);}
 private static int count(Container c){int n=0;for(int i=0;i<c.getContainerSize();i++)n+=c.getItem(i).getCount();return n;}
 private static int cargo(CompoundTag t){int n=0;for(var raw:t.getList("cargo",10))n+=ItemStack.of((CompoundTag)raw).getCount();return n;}
 private static BlockPos road(Fixture f){
  var l=f.town.l();var at=f.town.e().center().offset(-10,1,-10);
  for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){var p=at.offset(x,0,z);int top=Math.max(p.getY()+4,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ()));
   l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);for(int y=p.getY();y<=top;y++)l.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);}
  return at;
 }
 private static void clean(Fixture f){var l=f.town.l();for(var t:new ArrayList<>(Caravans.contracts(l.getServer())))if(t.getUUID("source").equals(f.town.s().id())){t.putString("state",Caravans.CLOSED);t.putBoolean("needsEntity",false);Caravans.update(l.getServer(),t);}
  if(l.getEntity(f.dog.getUUID())!=null)l.getEntity(f.dog.getUUID()).discard();for(var c:l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(f.town.e().center()).inflate(120),c->f.town.s().id().equals(c.settlement()))){c.home(null);c.discard();}
  SettlementData.get(l.getServer()).remove(f.town.s().id());BuildingLevels.forgetBest(f.town.s().id());}
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogFifthLevelTakesRealSecondCartAndExactAdditionalCargo(GameTestHelper h){
  var f=fixture(h,5);try{
   h.assertTrue(CaravanDogs.extraCapacity(f.town.l(),f.town.e())==448,"Level V has one extra load with two carts and a fed dog");var t=launch(f);
   h.assertTrue(t!=null&&cargo(f.parent)==896&&cargo(t)==104&&count(f.stock)==408,"The two loads debit exactly 1000 real items: parent="+cargo(f.parent)+" child="+(t==null?"null":cargo(t))+" stock="+count(f.stock));
   h.assertTrue(t.getUUID("caravaneer").equals(f.dog.getUUID())&&CaravanDogs.reserved(f.town.l().getServer(),f.dog.getUUID()),"Same wolf reserved");
   Caravans.dispatch(f.town.l(),f.parent,101);h.assertTrue(count(f.stock)==408&&cargo(t)==104,"Repeated dispatch must not take another dog, cart or load");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogFourthLevelAndUnavailableDogsKeepOnlyOneCart(GameTestHelper h){
  var f=fixture(h,4);try{h.assertTrue(CaravanDogs.extraCapacity(f.town.l(),f.town.e())==0&&launch(f)==null,"Level IV cannot take a dog cart");h.succeed();}finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogHungryOrSittingOrWorkingWolfIsNotRecruited(GameTestHelper h){
  var f=fixture(h,5);try{
   f.dog.getPersistentData().remove(VillageWolves.FED);h.assertTrue(CaravanDogs.extraCapacity(f.town.l(),f.town.e())==0,"Hungry wolf stays home");
   f.dog.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(f.town.l()));f.dog.setOrderedToSit(true);h.assertTrue(CaravanDogs.extraCapacity(f.town.l(),f.town.e())==0,"Sitting pet stays home");f.dog.setOrderedToSit(false);
   VillageWolves.sendDog(f.town.l(),f.town.e(),f.dog.getUUID(),f.town.e().center());h.assertTrue(launch(f)==null,"Existing errand retains its wolf");h.succeed();
  }finally{VillageDogs.release(f.town.l(),f.town.e(),f.dog.getUUID());clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogCannotBorrowAWarehouseOrLoadedCart(GameTestHelper h){
  var f=fixture(h,5);try{var l=f.town.l();var carts=CaravanDogs.carts(l,f.town.e());var busy=carts.get(0);busy.setItem(0,new ItemStack(Items.STONE));
   h.assertTrue(Caravans.freeCart(l,f.town.e())==carts.get(1)&&CaravanDogs.extraCapacity(l,f.town.e())==0,"A loaded nearest cart does not hide the other free cart, but it cannot be the second cart");
   busy.setItem(0,ItemStack.EMPTY);busy.home(UUID.randomUUID());h.assertTrue(launch(f)==null,"The warehouse's cart stays with its warehouse");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogReloadPreservesIdentityWoundsAndExclusiveReservation(GameTestHelper h){
  var f=fixture(h,5);try{f.dog.setHealth(13);var t=launch(f);var l=f.town.l();var dog=CaravanDogs.materialize(l,t,road(f));
   h.assertTrue(dog!=null&&dog.getHealth()==13&&dog.getOwnerUUID().equals(f.dog.getOwnerUUID()),"Wounds, owner and identity persist: actual="+(dog==null?"null":dog.getHealth()+"/"+dog.getMaxHealth()+" owner="+dog.getOwnerUUID())+" savedHealth="+t.getCompound("wolf").getFloat("Health")+" owner="+f.dog.getOwnerUUID());
   h.assertTrue(VillageWolves.freeWolf(l,f.town.e())==null&&VillageWolves.DOGS.free(l,f.town.e()).isEmpty(),"Other village jobs cannot take a visible caravan dog");
   dog.setHealth(9);CaravanDogs.unload(dog);var id=t.getUUID("id");Caravans.clear();t=Caravans.contract(l.getServer(),id);
   h.assertTrue(CaravanDogs.reserved(l.getServer(),dog.getUUID())&&CaravanDogs.stale(dog),"Disk reload retains reservation and invalidates old wolf body");
   var restored=CaravanDogs.materialize(l,t,road(f));h.assertTrue(restored!=null&&restored.getUUID().equals(dog.getUUID())&&restored.getHealth()==9,"One original wounded wolf returns");
   h.assertTrue(l.getEntitiesOfClass(CartEntity.class,restored.getBoundingBox().inflate(16),c->tId(c,id)).size()==1,"Only one live second cart");h.succeed();
  }finally{clean(f);}
 }
 private static boolean tId(CartEntity c,UUID id){return id.equals(c.trip())&&!Caravans.cartStale(c);}
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogDeathKeepsLeadersCargoAndLeavesRecoverableCart(GameTestHelper h){
  var f=fixture(h,5);try{var t=launch(f);var l=f.town.l();var dog=CaravanDogs.materialize(l,t,road(f));CaravanDogs.died(dog,200);
   h.assertTrue(cargo(f.parent)==896&&f.parent.getString("state").equals(Caravans.TRANSIT),"Leader and its goods survive");
   h.assertTrue(t.getInt("lost")==104&&cargo(t)==0&&t.hasUUID("leftCart")&&!CaravanDogs.reserved(l.getServer(),dog.getUUID()),"Only dog cargo drops; cart remains for normal recovery");
   CaravanDogs.died(dog,201);h.assertTrue(t.getInt("lost")==104,"Repeated death notification cannot drop goods twice");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogLeaderLossReturnsItsCargoAndBothCartsCanBeLeft(GameTestHelper h){
  var f=fixture(h,5);try{var t=launch(f);var l=f.town.l();
   // A real open roadside, outside the centre's roofs and the previous batch's structure blocks.
   var road=road(f);
   for(var trip:List.of(f.parent,t)){trip.putLong("from",road.asLong());trip.putLong("to",road.offset(80,0,0).asLong());Caravans.update(l.getServer(),trip);}
   h.assertTrue(CartHitch.parking(l,Caravans.position(l,f.parent,0))!=null,"Fixture has a place to leave the carts");int left=Caravans.leaveCart(l,t);
   h.assertTrue(left==552&&cargo(f.parent)==448&&cargo(t)==0&&f.parent.hasUUID("leftCart")&&t.hasUUID("leftCart"),"Mayor leaves both carts, keeping only the leader's hand load: left="+left+" parent="+cargo(f.parent)+" child="+cargo(t));
   h.assertTrue(!f.parent.getUUID("leftCart").equals(t.getUUID("leftCart")),"Two separately recoverable physical carts");
   f.parent.putString("state",Caravans.CLOSED);Caravans.update(l.getServer(),f.parent);CaravanDogs.tick(l,t,200);
   h.assertTrue(t.getString("state").equals(Caravans.RETURNING)||t.getString("state").equals(Caravans.CLOSED),"Dog returns after losing its leader");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogReturnsUndeliveredLoadAfterLeaderDeath(GameTestHelper h){
  var f=fixture(h,5);try{var t=launch(f);var l=f.town.l();t.putDouble("progress",30);f.parent.putString("state",Caravans.CLOSED);Caravans.update(l.getServer(),f.parent);CaravanDogs.tick(l,t,200);
   h.assertTrue(t.getString("state").equals(Caravans.RETURNING)&&cargo(t)==104&&t.getInt("delivered")==0,"A surviving dog brings its own cargo home without selling it");
   Caravans.arrive(l,t,201);h.assertTrue(count(f.stock)==512&&cargo(t)==0&&t.getBoolean("needsEntity"),"Goods returned once; dog reserved until it appears home");
   Caravans.arrive(l,t,202);h.assertTrue(count(f.stock)==512,"Repeated arrival does not duplicate returned goods");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",batch="trade_dog_lane",timeoutTicks=300) public static void caravanDogGoesAroundARaisedFoundationWithItsCart(GameTestHelper h){
  var f=fixture(h,5);var l=f.town.l();var t=launch(f);var road=road(f);
  for(int x=-3;x<=24;x++)for(int z=-6;z<=6;z++){
   l.setBlock(road.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<8;y++)l.setBlock(road.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   if(x>=5&&z>=0)l.setBlock(road.offset(x,0,z),Blocks.COBBLESTONE.defaultBlockState(),2);
  }
  for(var trip:List.of(f.parent,t)){trip.putLong("from",road.asLong());trip.putLong("to",road.offset(80,0,0).asLong());Caravans.update(l.getServer(),trip);}
  var driver=EntityType.VILLAGER.create(l);driver.setUUID(f.parent.getUUID("caravaneer"));driver.setNoAi(true);driver.moveTo(road.getX()+12.5,road.getY(),road.getZ()-1.5,0,0);l.addFreshEntity(driver);
  var dog=CaravanDogs.materialize(l,t,road);h.assertTrue(dog!=null,"Wolf and cart materialize on the road");
  h.runAfterDelay(180,()->{try{
   h.assertTrue(dog.getX()>road.getX()+7&&dog.getZ()<road.getZ(),"The dog takes the flat verge, not the raised house foundation: "+dog.blockPosition());
   var carts=l.getEntitiesOfClass(CartEntity.class,dog.getBoundingBox().inflate(8),c->t.getUUID("id").equals(c.trip()));
   h.assertTrue(carts.size()==1&&carts.get(0).getX()>road.getX()+5,"Its real cart follows past the foundation");h.succeed();
  }finally{driver.discard();clean(f);}});
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogUsesTheSameFractionalArrivalRadiusAsTheLeader(GameTestHelper h){
  var f=fixture(h,5);try{var t=launch(f);var l=f.town.l();var dog=CaravanDogs.materialize(l,t,road(f));var gate=BlockPos.of(t.getLong("to"));
   h.assertTrue(dog!=null,"Real dog materialized");dog.moveTo(gate.getX()-6.1,gate.getY(),gate.getZ()+.5);
   h.assertTrue(!CaravanDogs.deliverTogether(l,f.parent,200)&&t.getInt("delivered")==0,"A dog outside the arrival radius cannot deliver early");
   dog.moveTo(gate.getX()-5.1,gate.getY(),gate.getZ()-.1);
   h.assertTrue(CaravanDogs.deliverTogether(l,f.parent,201)&&t.getInt("delivered")==104,"The same continuous six-block radius as the leader; flooring x and z must not deadlock the pair");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanDogParksTwoSeparateCartsAfterTheRoundTrip(GameTestHelper h){
  var f=fixture(h,5);try{var t=launch(f);var l=f.town.l();Caravans.arrive(l,f.parent,200);Caravans.arrive(l,f.parent,201);Caravans.arrive(l,t,202);
   var carts=CaravanDogs.carts(l,f.town.e());h.assertTrue(carts.size()==2,"Both real carts are back at the yard");
   h.assertTrue(!carts.get(0).getBoundingBox().intersects(carts.get(1).getBoundingBox()),"Returned carts must not occupy the same parking space");
   h.assertTrue(count(f.stock)==1408&&cargo(f.parent)==0&&cargo(t)==0,"Round trip keeps exactly the original goods");h.succeed();
  }finally{clean(f);}
 }
}
