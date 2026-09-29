package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 §5 (CF-L, CF-M): the warehouse's couriers by level (1/1/1/2/2/2) and its wolves behind VillageDogs - without the livestock work's
 *  source the wolves are planned and the couriers carry; with a stand-in source a wolf pulls a second cart behind the courier at V, and at
 *  VI the wolves carry alone while the couriers' posts close. The stand-in is always removed (the kennel source VillageWolves.DOGS is put back). */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseWolvesGameTests {
 /** A stand-in kennel: free wolves until one is taken, every errand done at once. */
 static class Kennel implements VillageDogs.Source{
  final List<UUID> wolves=new ArrayList<>();final Set<UUID> busy=new HashSet<>();
  Kennel(int n){for(int i=0;i<n;i++)wolves.add(UUID.randomUUID());}
  @Override public boolean pulls(){return true;}
  @Override public List<UUID> free(ServerLevel l,SettlementData.Entry e){return wolves.stream().filter(w->!busy.contains(w)).toList();}
  @Override public void follow(ServerLevel l,SettlementData.Entry e,UUID dog,ResidentEntity courier){busy.add(dog);}
  @Override public void release(ServerLevel l,SettlementData.Entry e,UUID dog){busy.remove(dog);}
  @Override public boolean harness(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){busy.add(dog);return true;}
  @Override public boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos to){return true;}
  @Override public boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos at,double r){return true;}
 }
 /** Courier posts 1/1/1/2/2/2 by the warehouse's level: the labour office opens exactly that many; the core's couriers read the same row. */
 @GameTest(template="empty",timeoutTicks=200) public static void warehouseCourierPostsFollowTheLevel(GameTestHelper h){
  for(int level=1;level<=6;level++){
   var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
   var store=new Settlement.Building(Settlement.childId(s.id(),"building/warehouse"),WarehouseStore.TYPE,12,0,0);s.addBuilding(store);
   var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,20,true);s.addHome(home);
   for(int i=0;i<8;i++)s.admit(new Resident(Settlement.childId(s.id(),"a/"+i),Resident.Life.ADULT,false,null,null,-1),home.id());
   for(int lv=2;lv<=level;lv++)s.raiseBuildingLevel(store.id(),lv);
   var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),h.absolutePos(BlockPos.ZERO));Population.assign(e);
   var kept=s.buildings().stream().filter(WarehouseStore::is).findFirst().orElseThrow();int couriers=WarehouseTrips.posted(e,kept);
   h.assertTrue(couriers==Staff.slots(WarehouseStore.TYPE,level)&&couriers==CoreEffects.value("warehouse","couriers",level),"Level "+level+": "+couriers+" couriers, the table says "+CoreEffects.value("warehouse","couriers",level));
   h.assertTrue(s.residents().stream().noneMatch(r->r.profession()==Profession.PORTER&&s.workplace(r.id()).type().equals("town_hall")),"No porter is left at the hall at "+level);
  }
  h.succeed();
 }
 /** Without a source whose wolves pull carts (a stand-in that does not harness) no warehouse has wolves: V says planned, the courier pulls one cart, VI keeps its couriers. */
 @GameTest(template="empty",batch="warehouse_wolves_none",timeoutTicks=200) public static void withoutWolvesTheyArePlanned(GameTestHelper h){
  var v=village(h,6);research(v,6);VillageDogs.provide(new Kennel(1){@Override public boolean pulls(){return false;}});
  try{h.assertTrue(!VillageDogs.pulls(),"A stand-in source whose wolves do not pull carts is installed (the kennel's own wolves do pull)");
   adult(v,"courier",Profession.PORTER,v.kept());
   h.assertTrue(CartWolves.refusal(v.l(),v.e(),v.kept()).equals("planned")&&CartWolves.teams(v.l(),v.e(),v.kept())==0,"The wolves are planned");
   Warehouses.tick(v.l(),v.e(),1);
   h.assertTrue(!CartWolves.relieved(v.s().id())&&Population.slots(v.s(),v.kept())==2,"VI without wolves keeps its two couriers");
   h.assertTrue(WarehouseTrips.card(v.l(),v.e(),v.kept()).getString("wolves").equals("planned"),"The card marks the wolves planned");
  }finally{VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.succeed();
 }
 /** V with a stand-in wolf: the courier's trip takes a second cart the wolf pulls, five more stacks; all of it reaches the store. */
 @GameTest(template="empty",batch="warehouse_wolves_5",timeoutTicks=400) public static void atFiveAWolfPullsASecondCart(GameTestHelper h){
  var v=village(h,5);research(v,5);var kennel=new Kennel(1);VillageDogs.provide(kennel);
  for(int i=0;i<12;i++)put(v.pit(),new ItemStack(i%3==0?Items.RAW_IRON:Items.COBBLESTONE,64));
  var courier=adult(v,"courier",Profession.PORTER,v.kept());
  boolean two=WarehouseCarts.needed(v.l(),v.e(),v.kept())==2;if(!two){VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.assertTrue(two,"A courier and its wolf: two carts");
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  // The test is alone in its batch, so the stand-in kennel stays installed while the warehouse waits for its bays (done() removes it).
  withCarts(h,v,2,40,()->atFive(h,v,kennel,courier));
 }
 private static void atFive(GameTestHelper h,Village v,Kennel kennel,Resident courier){
  try{
   int stock=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get()),mine=total(v.pit());
   var c=body(v,courier,new BlockPos(16,0,4));WarehouseTrips.step(c);var t=WarehouseTrips.inspect(v.l(),c.getUUID());
   int second=0;var legs=t.getList("legs",Tag.TAG_COMPOUND);for(int i=0;i<legs.size();i++)if(legs.getCompound(i).getInt("cart")==2)second++;
   h.assertTrue(t.hasUUID("wolf")&&t.hasUUID("cart2")&&second==5,"The wolf's cart takes five stacks: "+second+" "+t);int planned=planned(t);
   h.assertTrue(drive(v,c,90),"The trip ends: "+c.workStatus());
   int moved=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get())-stock;
   h.assertTrue(moved==planned&&legs.size()==11&&mine-total(v.pit())==moved,"Parcel, five and five stacks: "+moved+" of "+planned);
   h.assertTrue(kennel.busy.isEmpty(),"The wolf went back to the kennel");
  }finally{VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.succeed();
 }
 /** The livestock work's contract: a harnessed wolf (any living puller, not only a villager) draws its cart behind it, and
  *  VillageDogs.release takes the hitch off that wolf's cart even without a kennel source. */
 @GameTest(template="empty",timeoutTicks=200) public static void aWolfPullsItsCartAndReleaseUnhitches(GameTestHelper h){
  var wolf=h.spawn(net.minecraft.world.entity.EntityType.WOLF,new BlockPos(2,1,2));wolf.setNoAi(true);
  var cart=h.spawn(VillageAstra.CART.get(),new BlockPos(2,1,4));cart.puller(wolf.getUUID());
  var start=cart.position();wolf.teleportTo(wolf.getX(),wolf.getY(),h.absolutePos(new BlockPos(2,1,9)).getZ()+.5);
  h.runAfterDelay(40,()->{
   h.assertTrue(cart.puller()!=null&&cart.position().distanceTo(start)>1.5,"The cart rolled after the wolf: "+cart.position()+" from "+start);
   VillageDogs.release(h.getLevel(),null,wolf.getUUID());
   h.assertTrue(cart.puller()==null,"Release took the hitch off the wolf's cart");
   var other=h.spawn(VillageAstra.CART.get(),new BlockPos(4,1,4));var stranger=UUID.randomUUID();other.puller(stranger);
   VillageDogs.release(h.getLevel(),null,wolf.getUUID());
   h.assertTrue(stranger.equals(other.puller()),"Another puller's cart keeps its hitch");
   h.succeed();});
 }
 /** VI with a stand-in wolf: the warehouse sends it alone with a cart (no courier), the couriers' posts close, and its load reaches the store. */
 @GameTest(template="empty",batch="warehouse_wolves_6",timeoutTicks=400) public static void atSixTheWolvesCarryAlone(GameTestHelper h){
  var v=village(h,6);research(v,6);var kennel=new Kennel(1);VillageDogs.provide(kennel);
  for(int i=0;i<8;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
  boolean team=CartWolves.teams(v.l(),v.e(),v.kept())==1&&CartWolves.refusal(v.l(),v.e(),v.kept()).isEmpty();if(!team){VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.assertTrue(team,"One wolf team");
  // The test is alone in its batch: the stand-in kennel stays while the warehouse waits for its bay's entities.
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));withCarts(h,v,1,40,()->atSix(h,v,kennel));
 }
 private static void atSix(GameTestHelper h,Village v,Kennel kennel){
  try{
   int stock=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get()),mine=total(v.pit());
   Warehouses.tick(v.l(),v.e(),1);
   h.assertTrue(CartWolves.relieved(v.s().id())&&Population.slots(v.s(),v.kept())==0,"The couriers' posts close while the wolves carry");
   var wolf=kennel.wolves.get(0);
   h.assertTrue(WarehouseTrips.active(WarehouseTrips.inspect(v.l(),wolf))&&WarehouseTrips.inspect(v.l(),wolf).getString("kind").equals("wolf"),"The wolf is on a trip of its own");
   var trip=WarehouseTrips.inspect(v.l(),wolf);int planned=planned(trip),legs=trip.getList("legs",Tag.TAG_COMPOUND).size();
   var id=trip.getUUID("id");java.util.function.BooleanSupplier running=()->{var x=WarehouseTrips.inspect(v.l(),wolf);return WarehouseTrips.active(x)&&x.getUUID("id").equals(id);};
   for(int i=0;i<20&&running.getAsBoolean();i++)WarehouseTrips.tickWolves(v.l(),v.e(),v.kept());
   int moved=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get())-stock;
   h.assertTrue(!running.getAsBoolean(),"The wolf's trip ended");WarehouseTrips.release(v.l(),wolf);
   h.assertTrue(legs==5&&moved==planned&&mine-total(v.pit())==moved,"Five stacks by the wolf's cart: "+moved+" of "+planned+" in "+legs);
  }finally{VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.succeed();
 }
}
