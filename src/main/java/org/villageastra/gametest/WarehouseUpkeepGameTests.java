package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 review: the warehouse keeps working past its first trips - an upgrade (or a repair) leaves the store's page chests where they
 *  stand, a courier whose post is closing for the wolves takes nothing new, a stranded cart is claimed by the one courier that fetches it,
 *  and a living wolf whose cart is left behind brings the load home instead of dropping it on the road. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseUpkeepGameTests {
 /** The next level's survey does not stumble on the page chests of the level that stands (its plan keeps their cells free). */
 @GameTest(template="empty",timeoutTicks=200) public static void anUpgradeLeavesThePageChests(GameTestHelper h){
  var v=village(h,1);
  try{var b=v.kept();var pages=WarehouseStore.chests(1);
   for(int i=1;i<pages.size();i++)h.assertTrue(HallStorage.partAt(v.l(),WarehouseStore.at(v.e(),b,pages.get(i))),"Chest "+i+" of level I is a page of the store");
   var survey=BuildingTiers.survey(v.l(),v.e(),b);
   for(int i=1;i<pages.size();i++){var pos=WarehouseStore.at(v.e(),b,pages.get(i));
    h.assertTrue(!survey.conflicts().contains(pos),"The page chest "+i+" is no conflict of level II: "+survey.reason()+" "+survey.conflicts());
    for(var raw:survey.state().getList("ops",Tag.TAG_COMPOUND))h.assertTrue(((net.minecraft.nbt.CompoundTag)raw).getLong("pos")!=pos.asLong(),"No operation takes the page chest "+i+" away");}
   h.assertTrue(!"conflict".equals(survey.reason())&&!survey.state().isEmpty(),"Level II can be ordered: "+survey.reason()+" "+survey.conflicts());
  }finally{done(v);}
  h.succeed();
 }
 /** CF-M: while the wolves carry, the posts close; a courier still posted takes no new parcel or trip (it would be unposted with it). */
 @GameTest(template="empty",batch="warehouse_upkeep_relieved",timeoutTicks=200) public static void aRelievedCourierTakesNothingNew(GameTestHelper h){
  var v=village(h,6);research(v,6);VillageDogs.provide(new WarehouseWolvesGameTests.Kennel(1));
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  withCarts(h,v,1,40,()->{try{for(int i=0;i<4;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
   var courier=adult(v,"courier",Profession.PORTER,v.kept());var c=body(v,courier,new BlockPos(16,0,4));
   Warehouses.tick(v.l(),v.e(),1);
   h.assertTrue(CartWolves.relieved(v.s().id())&&Population.slots(v.s(),v.kept())==0,"The wolves carry: the courier posts close");
   WarehouseTrips.step(c);
   h.assertTrue(!PorterWork.active(PorterWork.inspect(v.l(),c.getUUID()))&&!WarehouseTrips.active(WarehouseTrips.inspect(v.l(),c.getUUID())),"The relieved courier took nothing: "+c.workStatus());
   Population.assign(v.e());
   h.assertTrue(!WarehouseTrips.courier(v.e(),courier.id()),"The labour office unposts it empty-handed");
  }finally{VillageDogs.provide(VillageWolves.DOGS);done(v);}
  h.succeed();});
 }
 /** CF-K: a courier going for a stranded cart claims it at once - a second courier does not go for the same cart. */
 @GameTest(template="empty",batch="warehouse_upkeep_stranded",timeoutTicks=400) public static void aStrandedCartIsClaimedOnce(GameTestHelper h){
  var v=village(h,2);research(v,2);var courier=adult(v,"courier",Profession.PORTER,v.kept());
  withCart(h,v,cart->{var far=v.center().offset(50,0,4);cart.moveTo(far.getX()+.5,far.getY(),far.getZ()+.5,0,0);
   h.assertTrue(WarehouseCarts.stranded(v.l(),v.e(),v.kept())==cart,"The cart stands far from its warehouse");
   var c=body(v,courier,new BlockPos(16,0,4));WarehouseTrips.step(c);var t=WarehouseTrips.inspect(v.l(),c.getUUID());
   h.assertTrue(WarehouseTrips.active(t)&&t.getString("stage").equals("fetch"),"The courier goes for the cart: "+t);
   h.assertTrue(cart.travelling()&&WarehouseCarts.stranded(v.l(),v.e(),v.kept())==null,"The cart is claimed by that trip");
  });
 }
 /** A stand-in kennel of one real wolf: errands move it at once unless it is held; near asks the world. */
 static final class RealKennel implements VillageDogs.Source{
  final UUID wolf;final Set<UUID> busy=new HashSet<>();boolean held;
  RealKennel(UUID wolf){this.wolf=wolf;}
  @Override public boolean pulls(){return true;}
  @Override public List<UUID> free(ServerLevel l,SettlementData.Entry e){return busy.contains(wolf)?List.of():List.of(wolf);}
  @Override public void follow(ServerLevel l,SettlementData.Entry e,UUID dog,ResidentEntity courier){busy.add(dog);}
  @Override public void release(ServerLevel l,SettlementData.Entry e,UUID dog){busy.remove(dog);}
  @Override public boolean harness(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){busy.add(dog);return true;}
  @Override public boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos to){if(!held&&l.getEntity(dog)!=null)l.getEntity(dog).teleportTo(to.getX()+.5,to.getY(),to.getZ()+.5);return true;}
  @Override public boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,BlockPos at,double r){var body=l.getEntity(dog);return body!=null&&body.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=r*r;}
 }
 /** §2.7 for a wolf of VI: alive, but far from its cart past LOST_TICKS (the hitch slipped) - the cart stays, the load goes home in the
  *  record and reaches the store; nothing is dropped on the ground and nothing is made or lost. */
 @GameTest(template="empty",batch="warehouse_upkeep_wolf",timeoutTicks=400) public static void aLivingWolfLeftByItsCartBringsTheLoadHome(GameTestHelper h){
  var v=village(h,6);research(v,6);
  var wolf=h.spawn(EntityType.WOLF,new BlockPos(20,4,6));wolf.setNoAi(true);wolf.setInvulnerable(true);var kennel=new RealKennel(wolf.getUUID());VillageDogs.provide(kennel);
  for(int i=0;i<6;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  withCarts(h,v,1,40,()->{
   try{int stock=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get()),mine=total(v.pit());var cart=WarehouseCarts.carts(v.l(),v.kept()).get(0);
    wolf.teleportTo(cart.getX(),cart.getY(),cart.getZ());
    WarehouseTrips.tickWolves(v.l(),v.e(),v.kept());var trip=WarehouseTrips.inspect(v.l(),wolf.getUUID());
    h.assertTrue(WarehouseTrips.active(trip)&&trip.getString("kind").equals("wolf"),"The wolf is on a trip: "+trip);var id=trip.getUUID("id");
    java.util.function.BooleanSupplier running=()->{var x=WarehouseTrips.inspect(v.l(),wolf.getUUID());return WarehouseTrips.active(x)&&x.getUUID("id").equals(id);};
    // Up to the mine's chest: the load is taken there; then the wolf is held far from its cart (the hitch slipped) for longer than LOST_TICKS.
    for(int i=0;i<10&&running.getAsBoolean()&&WarehouseTrips.custody(v.l(),WarehouseTrips.inspect(v.l(),wolf.getUUID())).isEmpty();i++)WarehouseTrips.tickWolves(v.l(),v.e(),v.kept());
    int held=WarehouseTrips.custody(v.l(),WarehouseTrips.inspect(v.l(),wolf.getUUID())).size();
    h.assertTrue(held>0,"The wolf's trip took its load at the mine");
    kennel.held=true;var at=wolf.position();
    for(int i=0;i<WarehouseTrips.LOST_TICKS/20+3&&running.getAsBoolean();i++){wolf.teleportTo(at.x,at.y,at.z);WarehouseTrips.tickWolves(v.l(),v.e(),v.kept());}
    int dropped=0;for(var it:v.l().getEntitiesOfClass(ItemEntity.class,new AABB(v.center()).inflate(64)))if(it.getItem().is(Items.COBBLESTONE))dropped+=it.getItem().getCount();
    h.assertTrue(dropped==0,"Nothing is dropped on the road: "+dropped);
    var left=WarehouseTrips.inspect(v.l(),wolf.getUUID());
    h.assertTrue(running.getAsBoolean()&&left.getString("stage").equals("return")&&!left.hasUUID("cart")&&!id.equals(cart.trip()),"The wolf left its cart and goes home with the load: "+left);
    h.assertTrue(WarehouseTrips.custody(v.l(),left).size()==held,"The load is still in the trip's record: "+WarehouseTrips.custody(v.l(),left).size()+" of "+held);
    kennel.held=false;
    for(int i=0;i<10&&running.getAsBoolean();i++)WarehouseTrips.tickWolves(v.l(),v.e(),v.kept());
    h.assertTrue(!running.getAsBoolean(),"The trip ended at the warehouse: "+WarehouseTrips.inspect(v.l(),wolf.getUUID()));
    int moved=total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get())-stock;
    h.assertTrue(moved>0&&mine-total(v.pit())==moved,"The load reached the store, nothing made or lost: "+moved+" moved, the mine gave "+(mine-total(v.pit())));
   }finally{WarehouseTrips.release(v.l(),wolf.getUUID());VillageDogs.provide(VillageWolves.DOGS);wolf.discard();done(v);}
   h.succeed();});
 }
}
