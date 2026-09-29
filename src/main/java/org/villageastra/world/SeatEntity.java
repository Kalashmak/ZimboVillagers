package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Furniture;
/** AD-143: the invisible seat of a chair (villageastra:seat). It stands on the chair's seat, carries one sitter turned the way the chair faces
 *  and lives only while it has one: the tick it is empty, or its chair is gone, it goes — so there is never a seat without a sitter or two seats
 *  on one chair ({@link #sit} reuses the chair's seat). A player stands up by sneaking. A resident sits only while a goal holds it: the goal
 *  renews its lease every tick ({@link #keep}); a lease not renewed for LEASE_TICKS (the goal ended in any way, the world was reloaded) stands
 *  the resident up, so a resident is never left riding a seat. Nothing is saved: a seat reloaded without its lease empties at once. */
public final class SeatEntity extends Entity {
 /** Ticks a resident's lease lasts without renewal. */
 public static final int LEASE_TICKS=40;
 /** From a sitter's feet to the underside of its thighs, drawn legs forward on the seat: a player is drawn at 15/16 of a resident's size. */
 static double hips(Entity p){return p.getBbHeight()*(p instanceof Player?0.325:0.35);}
 private final Map<UUID,Long> leases=new HashMap<>();
 private int empty;
 public SeatEntity(EntityType<? extends SeatEntity> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);setInvisible(true);}
 @Override protected void defineSynchedData(){}
 @Override protected void readAdditionalSaveData(CompoundTag t){}
 @Override protected void addAdditionalSaveData(CompoundTag t){}
 @Override public boolean isPickable(){return false;}
 @Override public boolean isPushable(){return false;}
 @Override public boolean isAttackable(){return false;}
 @Override public boolean shouldRenderAtSqrDistance(double d){return false;}
 /** The chair this seat stands on (its block: the seat is half a block over the chair's floor). */
 public BlockPos chair(){return BlockPos.containing(getX(),getY()-Furniture.SEAT_HEIGHT+0.01,getZ());}
 private Direction facing(){var s=level().getBlockState(chair());return s.getBlock() instanceof ChairBlock?s.getValue(ChairBlock.FACING):Direction.fromYRot(getYRot());}
 @Override public void tick(){
  super.tick();if(level().isClientSide)return;
  if(!(level().getBlockState(chair()).getBlock() instanceof ChairBlock)){ejectPassengers();discard();return;}
  if(getPassengers().isEmpty()){if(++empty>1)discard();return;}empty=0;
  long now=level().getGameTime();
  for(var p:List.copyOf(getPassengers()))if(!(p instanceof Player)){var until=leases.get(p.getUUID());if(until==null||now>until){leases.remove(p.getUUID());p.stopRiding();}}
 }
 @Override protected void removePassenger(Entity p){super.removePassenger(p);leases.remove(p.getUUID());}
 /** On both sides: the sitter is turned the way the chair faces as it sits down — a player's own view too (the client runs this when the
  *  server tells it the player sits), so he does not sit twisted, looking back over the chair. */
 @Override protected void addPassenger(Entity p){super.addPassenger(p);float yaw=getYRot();p.setYRot(yaw);p.yRotO=yaw;p.setYHeadRot(yaw);
  if(p instanceof LivingEntity le){le.setYBodyRot(yaw);le.yBodyRotO=yaw;le.yHeadRotO=yaw;}}
 /** The sitter's hips on the seat, its body turned the way the chair faces. */
 @Override protected void positionRider(Entity p,Entity.MoveFunction move){
  if(!hasPassenger(p))return;move.accept(p,getX(),getY()-hips(p),getZ());
  if(p instanceof LivingEntity le){le.setYBodyRot(getYRot());if(!(p instanceof Player)){le.setYRot(getYRot());}}
 }
 /** A seated player turns his head, not his body: at most a quarter and a half round from where the chair faces. */
 @Override public void onPassengerTurned(Entity p){
  if(p instanceof LivingEntity le)le.setYBodyRot(getYRot());
  float f=Mth.wrapDegrees(p.getYRot()-getYRot()),g=Mth.clamp(f,-120F,120F);p.yRotO+=g-f;p.setYRot(p.getYRot()+g-f);p.setYHeadRot(p.getYRot());
 }
 /** Standing up: in front of the chair, else beside it, else behind it, else on it. */
 @Override public Vec3 getDismountLocationForPassenger(LivingEntity p){
  var chair=chair();var front=facing();
  for(var d:List.of(front,front.getClockWise(),front.getCounterClockWise(),front.getOpposite())){
   var at=DismountHelper.findSafeDismountLocation(p.getType(),level(),chair.relative(d),true);if(at!=null)return at;}
  return super.getDismountLocationForPassenger(p);
 }
 // ---- the chairs' seats ---------------------------------------------------------------------------
 private static List<SeatEntity> seats(Level l,BlockPos chair){return l.getEntitiesOfClass(SeatEntity.class,new AABB(chair),s->s.isAlive()&&s.chair().equals(chair));}
 /** The seat on a chair, or null; a second seat found on it (never made by {@link #sit}) is removed. */
 public static SeatEntity at(Level l,BlockPos chair){var all=seats(l,chair);SeatEntity keep=null;
  for(var s:all){if(keep==null||!s.getPassengers().isEmpty()&&keep.getPassengers().isEmpty())keep=s;}
  for(var s:all)if(s!=keep&&!l.isClientSide){s.ejectPassengers();s.discard();}
  return keep;}
 /** Who sits on a chair, or null. */
 public static Entity sitter(Level l,BlockPos chair){var s=at(l,chair);return s==null?null:s.getFirstPassenger();}
 public static boolean occupied(Level l,BlockPos chair){return sitter(l,chair)!=null;}
 /** Sits {@code who} down on the chair at {@code chair}, turned the way it faces: the chair's seat (made when it has none). Null when there is
  *  no chair there or someone else sits on it; {@code who}'s own seat when it already sits there. A resident gets its first lease. */
 public static SeatEntity sit(ServerLevel l,BlockPos chair,Entity who){
  var state=l.getBlockState(chair);if(!(state.getBlock() instanceof ChairBlock)||!who.isAlive())return null;
  var seat=at(l,chair);
  if(seat!=null&&!seat.getPassengers().isEmpty()){if(seat.hasPassenger(who)){keep(who);return seat;}return null;}
  if(seat==null){seat=new SeatEntity(VillageAstra.SEAT.get(),l);float yaw=state.getValue(ChairBlock.FACING).toYRot();
   seat.moveTo(chair.getX()+.5,chair.getY()+Furniture.SEAT_HEIGHT,chair.getZ()+.5,yaw,0);if(!l.addFreshEntity(seat))return null;}
  if(who.isPassenger())who.stopRiding();
  if(!who.startRiding(seat,true)){if(seat.getPassengers().isEmpty())seat.discard();return null;}
  float yaw=seat.getYRot();who.setYRot(yaw);who.yRotO=yaw;who.setYHeadRot(yaw);if(who instanceof LivingEntity le){le.setYBodyRot(yaw);le.yBodyRotO=yaw;}
  keep(who);return seat;
 }
 /** A goal that keeps a resident seated renews its lease (players need none). */
 public static void keep(Entity who){if(who.getVehicle() instanceof SeatEntity s&&!(who instanceof Player))s.leases.put(who.getUUID(),who.level().getGameTime()+LEASE_TICKS);}
 /** Stands {@code who} up when it sits on a chair. */
 public static void stand(Entity who){if(who.getVehicle() instanceof SeatEntity)who.stopRiding();}
 public static boolean seated(Entity who){return who.getVehicle() instanceof SeatEntity;}
 /** The chair was broken or replaced: its sitter stands up and its seats go. */
 public static void clear(ServerLevel l,BlockPos chair){for(var s:seats(l,chair)){s.ejectPassengers();s.discard();}}
}
