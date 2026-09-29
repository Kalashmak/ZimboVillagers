package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 §2 (CF-F..CF-K): the carts of the warehouse's couriers - a courier of II with Logistics II carries its parcel and five stacks in
 *  the warehouse's cart, exactly once each; without the research it walks; a cart comes from the warehouse's chest once; caravans and the
 *  restaurant never take it; a death on the way drops the whole custody once; the trip's legs are reserved against a second courier. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseCartGameTests {
 static void output(Village v){for(int i=0;i<4;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));for(int i=0;i<2;i++)put(v.pit(),new ItemStack(Items.RAW_IRON,64));put(v.pit(),new ItemStack(Items.COAL,64));}
 static int all(Village v){return total(v.pit())+total(v.stock())-count(v.stock(),VillageAstra.CART_ITEM.get());}
 /** II with Logistics II: the courier takes the cart, loads its parcel and five stacks at the mine, and puts all of it into the store. */
 @GameTest(template="empty",batch="warehouse_cart",timeoutTicks=400) public static void aCourierOfTwoCarriesFiveStacksInItsCart(GameTestHelper h){
  var v=village(h,2);research(v,2);output(v);var courier=adult(v,"courier",Profession.PORTER,v.kept());
  withCart(h,v,cart->{h.assertTrue(v.kept().id().equals(cart.home()),"The warehouse set its cart down from its chest");
   int before=all(v),stock=total(v.stock());
   var c=body(v,courier,new BlockPos(16,0,4));
   WarehouseTrips.step(c);var t=WarehouseTrips.inspect(v.l(),c.getUUID());
   h.assertTrue(WarehouseTrips.active(t),"A trip with the cart: "+c.workStatus());
   var legs=t.getList("legs",Tag.TAG_COMPOUND);int hand=0,carts=0;for(int i=0;i<legs.size();i++){if(legs.getCompound(i).getInt("cart")==0)hand++;else carts++;}
   h.assertTrue(hand==1&&carts==CoreEffects.value("warehouse","cart_stacks",2)&&carts==5,"One parcel and five cart stacks: "+hand+"+"+carts);
   h.assertTrue(ItemStack.of(legs.getCompound(0).getCompound("item")).getCount()==LogisticsRoutes.load(2),"The parcel is the level's load");
   int planned=planned(t);
   h.assertTrue(PorterWork.reserved(v.l(),v.e(),v.mine().id(),s->true,false)==planned,"The mine's stacks are reserved for the trip: "+PorterWork.reserved(v.l(),v.e(),v.mine().id(),s->true,false)+" of "+planned);
   h.assertTrue(drive(v,c,80),"The trip ends: "+WarehouseTrips.inspect(v.l(),c.getUUID())+" "+c.workStatus());
   int moved=total(v.stock())-stock;
   h.assertTrue(moved==planned&&planned>LogisticsRoutes.load(2)+4*64,"Exactly the parcel and five stacks reached the store: "+moved+" of "+planned);
   h.assertTrue(all(v)==before,"Nothing made or lost: "+all(v)+" of "+before);
   h.assertTrue(!cart.travelling()&&cart.puller()==null&&cart.shown()==0,"The cart is home and free");
  });
 }
 /** Without Logistics II a courier of a level-II warehouse walks with its parcel (PorterWork), and the warehouse orders no cart. */
 @GameTest(template="empty",timeoutTicks=200) public static void withoutLogisticsTwoTheCourierWalks(GameTestHelper h){
  var v=village(h,2);research(v,1);
  try{output(v);var courier=adult(v,"courier",Profession.PORTER,v.kept());var c=body(v,courier,new BlockPos(16,0,4));
   WarehouseTrips.step(c);
   h.assertTrue(!WarehouseTrips.active(WarehouseTrips.inspect(v.l(),c.getUUID()))&&PorterWork.active(PorterWork.inspect(v.l(),c.getUUID())),"A hand parcel, no trip");
   h.assertTrue(WarehouseCarts.needed(v.l(),v.e(),v.kept())==0&&WarehouseCarts.wants(v.l(),v.e()).isEmpty(),"No cart is ordered");
  }finally{done(v);}
  h.succeed();
 }
 /** A cart item in the chest becomes the warehouse's cart once: a second pass sets nothing down, the item is gone, no cart is asked for. */
 @GameTest(template="empty",batch="warehouse_cart_once",timeoutTicks=200) public static void aCartIsSetDownOnce(GameTestHelper h){
  var v=village(h,2);research(v,2);adult(v,"courier",Profession.PORTER,v.kept());
  boolean asked=WarehouseCarts.needed(v.l(),v.e(),v.kept())==1&&WarehouseCarts.wants(v.l(),v.e()).size()==1;if(!asked)done(v);
  h.assertTrue(asked,"One courier: one cart asked for");
  put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  withCart(h,v,cart->{
   WarehouseCarts.ensure(v.l(),v.e(),v.kept());WarehouseCarts.ensure(v.l(),v.e(),v.kept());
   h.assertTrue(WarehouseCarts.carts(v.l(),v.kept()).size()==1&&WarehouseCarts.have(v.l(),v.kept().id())==1,"One cart stands: "+WarehouseCarts.records(v.l(),v.kept().id()));
   h.assertTrue(count(v.stock(),VillageAstra.CART_ITEM.get())==1,"One cart item was used, the other stays");
   h.assertTrue(WarehouseCarts.wants(v.l(),v.e()).isEmpty(),"No more carts asked for");
   // A player's cart by the warehouse is not the warehouse's.
   var own=new CartEntity(v.l(),LogisticsRoutes.position(v.e(),v.kept()).offset(3,0,3),v.s().id());v.l().addFreshEntity(own);
   h.assertTrue(own.home()==null&&WarehouseCarts.carts(v.l(),v.kept()).size()==1,"A player's cart is never taken on");own.discard();
   // The broken cart is lost and a new one is asked for.
   WarehouseCarts.carts(v.l(),v.kept()).get(0).kill();
   h.assertTrue(WarehouseCarts.have(v.l(),v.kept().id())==0,"A broken cart is lost");
  });
 }
 /** CF-J: a caravan, the restaurant or the hall only see the village's other carts: the warehouse's parked cart hides none of them. */
 @GameTest(template="empty",batch="warehouse_cart_others",timeoutTicks=200) public static void othersDoNotTakeTheWarehouseCart(GameTestHelper h){
  var v=village(h,2);research(v,2);adult(v,"courier",Profession.PORTER,v.kept());
  withCart(h,v,cart->{var at=cart.blockPosition();
   h.assertTrue(CartHitch.near(v.l(),v.e(),at,16)==null,"A caravan or the restaurant finds no free cart here");
   h.assertTrue(CartHitch.near(v.l(),v.e(),at,16,c->true)==cart,"The warehouse's cart stands there");
   var other=new CartEntity(v.l(),at.offset(6,0,0),v.s().id());v.l().addFreshEntity(other);
   h.assertTrue(CartHitch.near(v.l(),v.e(),at,16)==other,"The other cart is found although the warehouse's is nearer");
   var caravaneer=adult(v,"caravaneer",null,null);var body=body(v,caravaneer,new BlockPos(16,0,4));body.moveTo(cart.getX(),cart.getY(),cart.getZ());
   h.assertTrue(!CartHitch.hitch(v.l(),v.e(),cart,body).isEmpty(),"Nobody but its courier takes its hitch");
   other.discard();
  });
 }
 /** A courier killed with a loaded cart drops the whole custody once, where it fell; the trip ends and the cart stays, free. */
 @GameTest(template="empty",batch="warehouse_death",timeoutTicks=300) public static void aDeathOnTheWayDropsTheLoadOnce(GameTestHelper h){
  var v=village(h,2);research(v,2);output(v);var courier=adult(v,"courier",Profession.PORTER,v.kept());
  withCart(h,v,cart->{int before=all(v);
   var c=body(v,courier,new BlockPos(16,0,4));
   for(int i=0;i<30;i++){drive(v,c,1);var t=WarehouseTrips.inspect(v.l(),c.getUUID());if(WarehouseTrips.custody(v.l(),t).size()==6)break;}
   var t=WarehouseTrips.inspect(v.l(),c.getUUID());var custody=WarehouseTrips.custody(v.l(),t);int held=custody.stream().mapToInt(ItemStack::getCount).sum();
   h.assertTrue(custody.size()==6,"Loaded at the mine: "+custody.size()+" stacks, "+c.workStatus());
   c.hurt(v.l().damageSources().genericKill(),1000);CargoCustody.tick(v.l().getServer());
   var state=CargoCustody.inspect(v.l().getServer(),c.getUUID());var pos=BlockPos.of(state.getList("drops",10).getCompound(0).getLong("pos"));
   var pile=(net.minecraft.world.Container)v.l().getBlockEntity(pos);
   h.assertTrue(pile!=null&&total(pile)==held,"The whole custody in one drop: "+(pile==null?-1:total(pile))+" of "+held);
   h.assertTrue(all(v)+total(pile)==before,"Nothing made or lost");
   h.assertTrue(!WarehouseTrips.active(WarehouseTrips.inspect(v.l(),c.getUUID()))&&!cart.travelling()&&cart.puller()==null,"The trip ended, the cart is free");
   pile.clearContent();
  });
 }
 /** §2.7 (CF-K): a cart that cannot roll on for 200 ticks turns the courier home without it: the building it did not reach is marked for a
  *  day (its legs go by hand), never the warehouse itself; the cart stays, free, where it stuck; nothing taken is lost. */
 @GameTest(template="empty",batch="warehouse_stuck",timeoutTicks=400) public static void aStuckCartTurnsTheCourierHome(GameTestHelper h){
  var v=village(h,2);research(v,2);output(v);var courier=adult(v,"courier",Profession.PORTER,v.kept());put(v.stock(),new ItemStack(VillageAstra.CART_ITEM.get()));
  withCarts(h,v,1,40,()->{
   var cart=WarehouseCarts.carts(v.l(),v.kept()).get(0);var c=body(v,courier,new BlockPos(16,0,4));
   // Hitched where the cart stands (its bay, where the entities tick), not carried off by the test's teleports.
   WarehouseTrips.step(c);c.moveTo(cart.getX()+1,cart.getY(),cart.getZ(),0,0);WarehouseTrips.step(c);var t=WarehouseTrips.inspect(v.l(),c.getUUID());
   if(!t.getString("stage").equals("go")){done(v);h.fail("The courier did not set out with its cart: "+t.getString("stage"));return;}
   // The cart boxed in stone where it stands; its courier five blocks on, still holding the hitch.
   // Out in the warehouse's yard (not over its chests): a ring two blocks out (the cart is wider than a block) and a roof over it.
   var yard=WarehouseStore.at(v.e(),v.kept(),new BlockPos(8,1,13));cart.moveTo(yard.getX()+.5,yard.getY(),yard.getZ()+.5,0,0);
   var at=cart.blockPosition();for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=2;dy++)if(Math.abs(dx)==2||Math.abs(dz)==2||dy==2)v.l().setBlock(at.offset(dx,dy,dz),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
   c.moveTo(at.getX()+5.5,at.getY(),at.getZ()+.5,0,0);
   var seen=new net.minecraft.world.phys.Vec3[1];h.runAfterDelay(55,()->seen[0]=cart.position());
   h.runAfterDelay(80,()->{
    try{h.assertTrue(cart.blocked(),"(at 55: "+seen[0]+") "+"The boxed cart does not roll on: at "+cart.position()+" from "+at+" puller "+cart.puller()+" courier "+c.position()+" ticks "+cart.tickCount+" travelling "+cart.travelling());
     for(int i=0;i<WarehouseTrips.BLOCKED_TICKS/20+1;i++)WarehouseTrips.step(c);
     var r=WarehouseTrips.inspect(v.l(),c.getUUID());
     h.assertTrue(r.getString("stage").equals("return")&&!r.hasUUID("cart"),"The courier turns home without the cart: "+r.getString("stage"));
     h.assertTrue(WarehouseTrips.noCart(v.l(),v.mine().id())&&!WarehouseTrips.noCart(v.l(),v.kept().id()),"The mine is marked for a day, the warehouse never");
     h.assertTrue(!cart.travelling()&&cart.puller()==null&&cart.isAlive(),"The cart stays where it stuck, free");
     int before=total(v.pit())+total(v.stock());
     h.assertTrue(drive(v,c,40),"The trip ends at home");
     h.assertTrue(total(v.pit())+total(v.stock())==before,"Nothing lost: "+(total(v.pit())+total(v.stock()))+" of "+before);
    }finally{done(v);}
    h.succeed();});
  });
 }
 /** A crash after a take (the index read again from the records): the trip goes on from its record, every leg exactly once. */
 @GameTest(template="empty",batch="warehouse_resume",timeoutTicks=400) public static void aTripResumesFromItsRecord(GameTestHelper h){
  var v=village(h,2);research(v,2);output(v);var courier=adult(v,"courier",Profession.PORTER,v.kept());
  withCart(h,v,cart->{int before=all(v),stock=total(v.stock());
   var c=body(v,courier,new BlockPos(16,0,4));
   for(int i=0;i<30;i++){drive(v,c,1);if(WarehouseTrips.custody(v.l(),WarehouseTrips.inspect(v.l(),c.getUUID())).size()==6)break;}
   var was=WarehouseTrips.inspect(v.l(),c.getUUID());int planned=planned(was),held=WarehouseTrips.custody(v.l(),was).size();
   WarehouseTrips.forget(v.l().getServer());
   var t=WarehouseTrips.inspect(v.l(),c.getUUID());h.assertTrue(WarehouseTrips.active(t)&&WarehouseTrips.custody(v.l(),t).size()==6,"The record holds the trip and its custody: held "+held+", read "+t);
   h.assertTrue(drive(v,c,80),"The trip ends");
   h.assertTrue(total(v.stock())-stock==planned&&all(v)==before,"Every leg exactly once: "+(total(v.stock())-stock)+" of "+planned);
  });
 }
}
