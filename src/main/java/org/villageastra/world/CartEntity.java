package org.villageastra.world;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.villageastra.VillageAstra;
/** ROAD-008: one simple cart, pulled by a caravaneer on foot. It has a size, a cargo hold and a route, it is never carried through a wall
 *  and never teleported: when the way is too narrow or blocked it simply stops, and the puller waits or goes around. The cargo of a trip
 *  is in exactly one place — this hold, the carrier, or a container; never in two. */
public class CartEntity extends Entity implements Container {
 public static final int SLOTS=27;
 /** How far behind the puller the cart is drawn, how far the hitch reaches, and how far it may be from the puller before the hitch slips. */
 public static final double FOLLOW=1.6,REACH=6.0,LOST=12.0;
 private static final EntityDataAccessor<Boolean> LOADED=SynchedEntityData.defineId(CartEntity.class,EntityDataSerializers.BOOLEAN);
 /** AD-147: how many stacks a warehouse trip's record holds in this cart - what a nearby player sees on it (the hold stays empty). */
 private static final EntityDataAccessor<Integer> SHOWN=SynchedEntityData.defineId(CartEntity.class,EntityDataSerializers.INT);
 private final NonNullList<ItemStack> hold=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
 private UUID settlement,puller,trip,home;private long tripEpoch;private int stuck;
 public CartEntity(EntityType<? extends CartEntity> type,Level level){super(type,level);}
 public CartEntity(Level level,BlockPos at,UUID settlement){
  this(VillageAstra.CART.get(),level);this.settlement=settlement;setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
 }
 public UUID settlement(){return settlement;}
 /** AD-147 §2.3: the warehouse a cart belongs to (only its couriers and wolves take it; caravans and the restaurant never), or null. */
 public UUID home(){return home;}
 public void home(UUID warehouse){home=warehouse;}
 /** AD-147: the stacks of a warehouse trip this cart is seen carrying (0..10); a view only, the trip's record owns the load (CF-F). */
 public int shown(){return entityData.get(SHOWN);}
 public void show(int stacks){entityData.set(SHOWN,Math.max(0,stacks));entityData.set(LOADED,!isEmpty()||stacks>0);}
 public void settlement(UUID id){settlement=id;}
 public UUID puller(){return puller;}
 /** The caravaneer that has taken the hitch, or nobody. A cart is pulled by one villager at a time. */
 public void puller(UUID id){puller=id;stuck=0;}
 /** AD-147: every loaded cart hitched to this puller (a villager or a harnessed wolf) drops the hitch and stops where it stands.
  *  A puller that is not loaded needs nothing: its cart already lets go in {@link #tick} when it cannot see it. */
 public static void unhitch(net.minecraft.server.level.ServerLevel l,UUID who){
  if(l==null||who==null)return;var body=l.getEntity(who);if(body==null)return;
  for(var cart:l.getEntitiesOfClass(CartEntity.class,body.getBoundingBox().inflate(LOST+2),c->who.equals(c.puller()))){cart.puller(null);cart.setDeltaMovement(0,cart.getDeltaMovement().y,0);}
 }
 /** A cart on the road with a caravan: the trip's record is its truth, this body is only what a nearby player sees. */
 public boolean travelling(){return trip!=null;}
 public UUID trip(){return trip;}
 public long tripEpoch(){return tripEpoch;}
 public void travel(UUID trip,long epoch){this.trip=trip;this.tripEpoch=epoch;}
 /** The cart has not moved although its puller walked on: the way is too narrow or blocked for it. */
 public boolean blocked(){return stuck>=20;}
 @Override protected void defineSynchedData(){entityData.define(LOADED,false);entityData.define(SHOWN,0);}
 @Override protected void readAdditionalSaveData(CompoundTag tag){
  hold.clear();ContainerHelper.loadAllItems(tag,hold);
  settlement=tag.hasUUID("settlement")?tag.getUUID("settlement"):null;
  puller=tag.hasUUID("puller")?tag.getUUID("puller"):null;
  trip=tag.hasUUID("trip")?tag.getUUID("trip"):null;tripEpoch=tag.getLong("tripEpoch");
  home=tag.hasUUID("home")?tag.getUUID("home"):null;entityData.set(SHOWN,tag.getInt("shown"));
  entityData.set(LOADED,!isEmpty()||shown()>0);
 }
 @Override protected void addAdditionalSaveData(CompoundTag tag){
  ContainerHelper.saveAllItems(tag,hold);
  if(settlement!=null)tag.putUUID("settlement",settlement);
  if(puller!=null)tag.putUUID("puller",puller);
  if(trip!=null){tag.putUUID("trip",trip);tag.putLong("tripEpoch",tripEpoch);}
  if(home!=null)tag.putUUID("home",home);if(shown()>0)tag.putInt("shown",shown());
 }
 /** AD-147 (CF-I): a warehouse's cart that is broken or killed tells its warehouse, which orders a new one; unloading tells nothing. */
 @Override public void remove(RemovalReason reason){
  if(home!=null&&reason.shouldDestroy()&&level() instanceof ServerLevel l)WarehouseCarts.lost(l,this);
  super.remove(reason);
 }
 @Override public boolean isPickable(){return !isRemoved();}
 @Override public boolean isPushable(){return false;}
 /** Wheels can roll over path edges and slabs, but cannot climb a full-block wall. */
 @Override public float maxUpStep(){return .6F;}
 @Override public boolean canBeCollidedWith(){return true;}
 @Override public InteractionResult interact(Player player,InteractionHand hand){
  if(travelling())return InteractionResult.FAIL;
  if(level().isClientSide)return InteractionResult.SUCCESS;
  player.openMenu(new net.minecraft.world.SimpleMenuProvider(
    (id,inventory,who)->net.minecraft.world.inventory.ChestMenu.threeRows(id,inventory,this),
    net.minecraft.network.chat.Component.translatable("entity.villageastra.cart")));
  return InteractionResult.CONSUME;
 }
 /** The cart rolls after its puller and stops at whatever it cannot pass. Nothing about it moves by itself. */
 @Override public void tick(){
  super.tick();
  if(level().isClientSide){setPos(getX(),getY(),getZ());return;}
  // A travelling copy the trip no longer vouches for is a stale body of the same cart: it leaves instead of doubling the cart.
  // AD-147: a warehouse's cart is a real, local cart: a trip that no longer vouches for it only stops showing its load (never a discard).
  if(travelling()&&home!=null){if(!WarehouseTrips.vouches(this)){travel(null,0);show(0);}}
  else if(travelling()&&Caravans.cartStale(this)){discard();return;}
  var before=position();
  var driver=puller==null?null:((ServerLevel)level()).getEntity(puller);
  boolean asked=false;
  // AD-147: a warehouse's cart may be pulled by a harnessed wolf as well as by a resident.
  if(driver instanceof net.minecraft.world.entity.LivingEntity worker&&worker.isAlive()&&distanceToSqr(worker)<=LOST*LOST){
   var behind=worker.position().subtract(worker.getLookAngle().normalize().scale(FOLLOW));
   var to=new Vec3(behind.x-getX(),0,behind.z-getZ());
   double length=to.length();
   asked=length>0.4;
   if(asked){var step=to.normalize().scale(Math.min(.14,length*.35));setDeltaMovement(step.x,getDeltaMovement().y,step.z);}
   else setDeltaMovement(0,getDeltaMovement().y,0);
  }else{puller=null;setDeltaMovement(0,getDeltaMovement().y,0);}
  if(!isNoGravity())setDeltaMovement(getDeltaMovement().add(0,-0.08,0));
  move(MoverType.SELF,getDeltaMovement());
  setDeltaMovement(getDeltaMovement().multiply(.7,.98,.7));
  if(onGround())setDeltaMovement(getDeltaMovement().multiply(1,0,1));
  // A cart that is asked to roll on and does not is one the way does not fit: it says so instead of sliding through the wall.
  if(asked&&position().distanceToSqr(before)<1.0E-4)stuck++;else stuck=0;
  entityData.set(LOADED,!isEmpty()||shown()>0);
 }
 // ---- the hold ---------------------------------------------------------------------------
 @Override public int getContainerSize(){return SLOTS;}
 @Override public boolean isEmpty(){for(var s:hold)if(!s.isEmpty())return false;return true;}
 @Override public ItemStack getItem(int slot){return hold.get(slot);}
 @Override public ItemStack removeItem(int slot,int count){var s=ContainerHelper.removeItem(hold,slot,count);setChanged();return s;}
 @Override public ItemStack removeItemNoUpdate(int slot){return ContainerHelper.takeItem(hold,slot);}
 @Override public void setItem(int slot,ItemStack stack){hold.set(slot,stack);setChanged();}
 @Override public void setChanged(){if(!level().isClientSide)entityData.set(LOADED,!isEmpty()||shown()>0);}
 /** Whether the cart is seen loaded (a hold with anything in it, or a warehouse trip's load). */
 public boolean loaded(){return entityData.get(LOADED);}
 @Override public boolean stillValid(Player player){return !isRemoved()&&player.distanceToSqr(this)<=64;}
 @Override public void clearContent(){hold.clear();setChanged();}
 // A travelling body's hold is only a copy of the load its trip's record owns: a hopper neither empties it nor fills it.
 @Override public boolean canPlaceItem(int slot,ItemStack stack){return !travelling();}
 @Override public boolean canTakeItem(Container target,int slot,ItemStack stack){return !travelling();}
 /** Whatever the cart holds stays with it: a broken cart puts its load down on the spot, it never disappears. */
 @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
  if(level().isClientSide||isRemoved()||travelling()||!(source.getEntity() instanceof Player player)||!player.isCreative()&&!player.getAbilities().mayBuild)return false;
  for(var stack:hold)if(!stack.isEmpty())level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(),getX(),getY()+.5,getZ(),stack.copy()));
  hold.clear();
  level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level(),getX(),getY()+.5,getZ(),new ItemStack(VillageAstra.CART_ITEM.get())));
  level().playSound(null,blockPosition(),SoundEvents.WOOD_BREAK,SoundSource.BLOCKS,1F,1F);
  discard();return true;
 }
 @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){
  return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
 }
}
