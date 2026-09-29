package org.villageastra.world;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.villageastra.domain.Profession;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** ROAD-008: who pulls the cart, and how its load is moved. A caravaneer takes the hitch of its own settlement's cart and pulls it on foot;
 *  every item that goes in or out of the hold is moved through the world journal, so the load of a trip is in exactly one place. */
public final class CartHitch {
 private CartHitch(){}
 /** The settlement's cart nearest to a spot, or none. AD-147 (CF-J): a warehouse's own cart (home) is never one of these - a caravan, the
  *  restaurant or the hall only ever see the village's other carts, and a parked warehouse cart does not hide them. */
 public static CartEntity near(ServerLevel l,SettlementData.Entry e,BlockPos at,double radius){return near(l,e,at,radius,c->c.home()==null);}
 /** The settlement's cart nearest to a spot that {@code filter} takes, or none. */
 public static CartEntity near(ServerLevel l,SettlementData.Entry e,BlockPos at,double radius,java.util.function.Predicate<CartEntity> filter){
  return l.getEntitiesOfClass(CartEntity.class,new AABB(at).inflate(radius),c->e.settlement().id().equals(c.settlement())&&filter.test(c))
    .stream().min((a,b)->Double.compare(a.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5),b.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5))).orElse(null);
 }
 /** Takes the hitch: the caravaneer of this settlement, standing at the cart, and no second puller. */
 public static String hitch(ServerLevel l,SettlementData.Entry e,CartEntity cart,ResidentEntity worker){
  if(cart==null||worker==null||!cart.isAlive())return "cart";
  if(cart.settlement()!=null&&!cart.settlement().equals(e.settlement().id()))return "another_village";
  var resident=e.settlement().resident(worker.getUUID());
  // AD-139 §5.5: a restaurant's courier of level V with a dog of the village takes the hitch too.
  // AD-147: a warehouse's cart is pulled only by a courier of that warehouse (a caravaneer or the restaurant's courier never takes it).
  if(cart.home()!=null){if(!WarehouseTrips.mayPull(l,e,worker,cart))return "warehouse";}
  else if(resident==null||!resident.alive()||resident.profession()!=Profession.CARAVANEER&&!Couriers.mayPull(l,e,worker))return "caravaneer";
  if(cart.puller()!=null&&!cart.puller().equals(worker.getUUID())&&l.getEntity(cart.puller()) instanceof ResidentEntity other&&other.isAlive())return "taken";
  if(worker.distanceToSqr(cart)>CartEntity.REACH*CartEntity.REACH)return "reach";
  cart.settlement(e.settlement().id());cart.puller(worker.getUUID());return "";
 }
 public static void unhitch(CartEntity cart){if(cart!=null)cart.puller(null);}
 /** Items of a container into the hold of the cart: taken once, put down once, never copied. */
 public static int load(ServerLevel l,CartEntity cart,BlockPos from,int slot,int count,UUID operation){
  if(!(l.getBlockEntity(from) instanceof Container source)||cart==null)return 0;
  var stack=source.getItem(slot);if(stack.isEmpty()||count<=0)return 0;
  var taken=WorldJournal.takeAmount(l,operation,from,slot,stack.copy(),Math.min(count,stack.getCount()));
  if(taken.isEmpty())return 0;
  var left=put(cart,taken);
  if(!left.isEmpty()){
   // Nothing is lost when the hold is full: what does not fit goes straight back where it came from.
   var back=source.getItem(slot);
   if(back.isEmpty())source.setItem(slot,left);else back.grow(left.getCount());
   source.setChanged();
  }
  return taken.getCount()-left.getCount();
 }
 /** The load of the cart into a container: the same move the other way round. */
 public static int unload(ServerLevel l,CartEntity cart,BlockPos to,int slot,int count,UUID operation){
  if(cart==null||!(l.getBlockEntity(to) instanceof Container target))return 0;
  var stack=cart.getItem(slot);if(stack.isEmpty()||count<=0)return 0;
  int moved=Math.min(count,stack.getCount());
  var carried=stack.copyWithCount(moved);
  if(!WorldJournal.deposit(l,operation,to,carried))return 0;
  stack.shrink(moved);cart.setItem(slot,stack.isEmpty()?ItemStack.EMPTY:stack);
  return moved;
 }
 /** How much of one kind the cart holds. */
 public static int count(CartEntity cart,net.minecraft.world.item.Item item){
  int n=0;for(int i=0;i<cart.getContainerSize();i++)if(cart.getItem(i).is(item))n+=cart.getItem(i).getCount();return n;
 }
 /** Puts a stack into the hold and gives back whatever did not fit. */
 public static ItemStack put(CartEntity cart,ItemStack stack){
  var left=stack.copy();
  for(int i=0;i<cart.getContainerSize()&&!left.isEmpty();i++){
   var slot=cart.getItem(i);
   if(slot.isEmpty()){cart.setItem(i,left.copy());left=ItemStack.EMPTY;break;}
   if(!ItemStack.isSameItemSameTags(slot,left))continue;
   int room=Math.min(slot.getMaxStackSize(),cart.getMaxStackSize())-slot.getCount();
   if(room<=0)continue;
   int moved=Math.min(room,left.getCount());slot.grow(moved);left.shrink(moved);cart.setItem(i,slot);
  }
  return left;
 }
 /** The cart of a settlement that stands at the hall, ready for a trip. */
 public static CartEntity atHall(ServerLevel l,SettlementData.Entry e){
  var hall=Workshops.hall(e);if(hall==null)return null;
  return near(l,e,LogisticsRoutes.position(e,hall),12);
 }
 /** A free spot for a cart near a place: two cells of air over sturdy ground, the nearest one within a few blocks. */
 public static BlockPos parking(ServerLevel l,BlockPos near){
  for(int r=0;r<=6;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
   if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
   for(int dy=4;dy>=-8;dy--){var at=near.offset(dx,dy,dz);if(!l.hasChunkAt(at))continue;
    if(l.getBlockState(at).isAir()&&l.getBlockState(at.above()).isAir()&&l.getBlockState(at.below()).isFaceSturdy(l,at.below(),net.minecraft.core.Direction.UP)&&parkingRoom(l,at))return at;}
  }
  return null;
 }
 private static boolean parkingRoom(ServerLevel l,BlockPos at){
  var size=org.villageastra.VillageAstra.CART.get().getDimensions();double half=size.width/2.0;
  var box=new AABB(at.getX()+.5-half,at.getY(),at.getZ()+.5-half,at.getX()+.5+half,at.getY()+size.height,at.getZ()+.5+half).inflate(.02,0,.02);
  return !l.getBlockCollisions(null,box).iterator().hasNext()&&l.getEntitiesOfClass(CartEntity.class,box,CartEntity::isAlive).isEmpty();
 }
 /** A trip that cannot go on: the way is too narrow or blocked for the cart behind the caravaneer. */
 public static boolean waiting(CartEntity cart){return cart!=null&&cart.blocked();}
 /** The building a cart belongs with: its settlement's hall, for the record of a trip. */
 public static Settlement.Building home(SettlementData.Entry e){return Workshops.hall(e);}
}
