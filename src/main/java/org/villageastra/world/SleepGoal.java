package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-060: at night every resident goes home and sleeps in their own bed; the guard keeps watch. At dawn they get up and go back to work. */
public final class SleepGoal extends Goal {
 /** The night the residents sleep through, in ticks of the day, and how far from home a resident still walks back to bed. */
 public static final long DUSK=12600,DAWN=23200;public static final int HOME_REACH=320;
 private final ResidentEntity resident;private final boolean withoutPlayers;private final java.util.function.LongSupplier clock;private BlockPos bed;private int repath;
 public SleepGoal(ResidentEntity resident){this(resident,false,()->resident.level().getDayTime());}
 /** A goal with its own clock and no need for players around: the tests drive the night without touching the shared world time. */
 public SleepGoal(ResidentEntity resident,boolean withoutPlayers,java.util.function.LongSupplier clock){this.resident=resident;this.withoutPlayers=withoutPlayers;this.clock=clock;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.JUMP));}
 public static boolean night(long dayTime){long t=Math.floorMod(dayTime,24000L);return t>=DUSK&&t<DAWN;}
 public static boolean night(Level l){return night(l.getDayTime());}
 private boolean night(){return night(clock.getAsLong());}
 /** Who stays awake at night: the guard and the archer on watch, and soldiers. */
 public static boolean keepsWatch(Resident r){return r.profession()==Profession.GUARD||r.profession()==Profession.ARCHER_GUARD||r.profession()==Profession.SOLDIER;}
 /** The bed of a resident: the head of the n-th bed of their home, n being their place among the people of that home.
  *  AD-123 (C6): the beds really standing in the house's footprint, not those of its first design, so the third and fourth resident
  *  of an upgraded house (and of a legacy one) find their own. */
 public static BlockPos bed(ServerLevel l,SettlementData.Entry e,Resident r){
  if(r.home()==null)return null;var s=e.settlement();
  var home=s.buildings().stream().filter(b->b.id().equals(r.home())).findFirst().orElse(null);if(home==null)return null;
  var heads=HousingLadder.beds(l,e,home,BedPart.HEAD);
  if(heads==null||heads.isEmpty())return null;
  var people=s.residents().stream().filter(x->x.alive()&&r.home().equals(x.home())).map(Resident::id).sorted().toList();
  int index=people.indexOf(r.id());if(index<0||index>=heads.size())return null;
  var head=heads.get(index);
  if(!l.hasChunkAt(head)||!(l.getBlockState(head).getBlock() instanceof BedBlock))return null;
  return head;
 }
 private SettlementData.Entry entry(){
  if(!(resident.level() instanceof ServerLevel l)||resident.settlementId()==null)return null;
  if(l.getServer().getPlayerCount()==0&&!withoutPlayers)return null;
  var e=SettlementData.get(l.getServer()).entry(resident.settlementId());
  return e==null||!e.dimension().equals(l.dimension().location().toString())?null:e;
 }
 /** Raised bedroom furniture must not interrupt the final step into this resident's own bed. */
 public boolean readyToSleep(){
  if(!night()||resident.isSleeping()||resident.escortPlayer()!=null)return false;
  var e=entry();if(e==null)return false;var r=e.settlement().resident(resident.getUUID());if(r==null||!r.alive()||keepsWatch(r))return false;
  var own=bed!=null?bed:bed((ServerLevel)resident.level(),e,r);if(own==null)return false;var block=resident.level().getBlockState(own);
  return block.getBlock() instanceof BedBlock&&!block.getValue(BedBlock.OCCUPIED)&&resident.position().distanceToSqr(own.getX()+.5,own.getY()+.5,own.getZ()+.5)<=4;
 }
 @Override public boolean canUse(){
  // AD-072: goals start (and slow goals tick) only every other tick, on a parity fixed per entity — a window of two ticks is never missed.
  if(!night()||resident.escortPlayer()!=null||resident.tickCount%20>1&&!withoutPlayers)return false;
  var e=entry();if(e==null)return false;var r=e.settlement().resident(resident.getUUID());
  if(r==null||!r.alive()||keepsWatch(r))return false;
  bed=bed((ServerLevel)resident.level(),e,r);
  return bed!=null&&resident.blockPosition().distSqr(bed)<=HOME_REACH*HOME_REACH;
 }
 @Override public boolean canContinueToUse(){
  return bed!=null&&night()&&resident.escortPlayer()==null&&resident.level().getBlockState(bed).getBlock() instanceof BedBlock;
 }
 @Override public void start(){repath=0;resident.workStatus("going_to_bed");}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  if(resident.isSleeping())return;
  // A bed somebody else is lying in is not taken from them.
  if(resident.level().getBlockState(bed).getValue(BedBlock.OCCUPIED)){resident.getNavigation().stop();return;}
  if(resident.position().distanceToSqr(bed.getX()+.5,bed.getY()+.5,bed.getZ()+.5)<=4){
   resident.getNavigation().stop();resident.startSleeping(bed);resident.workStatus("sleeping");return;}
  if(--repath<=0){
   var nav=resident.getNavigation();
   if(resident.blockPosition().distSqr(bed)>64*64){
    // A dispersed village can put a school beyond the ordinary navigation range.
    // Keep a usable route instead of repeating a long search every twenty ticks.
    repath=100;
    if(nav.isDone()||nav.getTargetPos()==null||!nav.getTargetPos().equals(bed)){
     var route=resident.routeTo(bed,1,HOME_REACH);
     if(route!=null&&route.canReach())nav.moveTo(route,.7);
    }
   }else{repath=20;nav.moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5,.7);}
  }
 }
 @Override public void stop(){
  if(resident.isSleeping())resident.stopSleeping();
  resident.getNavigation().stop();bed=null;
 }
}
