package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-143: a resident with nothing to do sits down on a chair of its own home for a while — now and then by day, more often in the evening
 *  before dusk. Priority 7, the idle stroll's: any work (6), the restaurant (4), sleep (2), panic or a raid (1) takes the resident up at once
 *  (the goal stops and it stands up), so resting never keeps it from anything. It sits on a free chair only (never takes one from a player or a
 *  neighbour), rides the chair's seat while it renews the lease, and gives up a chair it cannot reach. */
public final class HomeRestGoal extends Goal {
 /** From this hour of the day the resident rests more often; the rest ends at dusk (SleepGoal.DUSK). */
 public static final long EVENING=10500;
 /** Ticks between two looks for a chair, the rest's length, the walk it gives up after, how far from home it still walks back. */
 public static final int CHECK=100,REST_MIN=300,REST_MAX=700,WALK_MAX=400,REACH=24;
 private final ResidentEntity npc;private final java.util.function.LongSupplier day;private BlockPos chair;private int wait,left,walked,repath;
 /** GameTests: rest at the first look, whatever the chance, for {@code eager} ticks. */
 private int eager;
 public HomeRestGoal(ResidentEntity npc){this(npc,()->npc.level().getDayTime());}
 /** Tests: the time of day read from {@code day}. */
 public HomeRestGoal(ResidentEntity npc,java.util.function.LongSupplier day){this.npc=npc;this.day=day;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));wait=npc.getRandom().nextInt(CHECK);}
 public HomeRestGoal eager(int ticks){eager=ticks;wait=CHECK-1;return this;}
 private SettlementData.Entry entry(){
  if(!(npc.level() instanceof ServerLevel l)||!npc.isAlive()||npc.settlementId()==null||npc.escortPlayer()!=null)return null;
  var e=SettlementData.get(l.getServer()).entry(npc.settlementId());return e==null||!e.dimension().equals(l.dimension().location().toString())?null:e;}
 private boolean hour(){return !SleepGoal.night(Math.floorMod(day.getAsLong(),24000L));}
 @Override public boolean canUse(){
  if(++wait<CHECK)return false;wait=0;
  if(npc.isPassenger()||npc.isSleeping()||npc.isInWater()||!hour())return false;
  long t=Math.floorMod(day.getAsLong(),24000L);
  if(eager==0&&npc.getRandom().nextInt(t>=EVENING?3:15)!=0)return false;
  var e=entry();if(e==null||Sieges.besieged(npc.getServer(),e.settlement().id()))return false;
  var r=e.settlement().resident(npc.getUUID());if(r==null||!r.alive()||r.home()==null)return false;
  var home=e.settlement().buildings().stream().filter(b->b.id().equals(r.home())).findFirst().orElse(null);if(home==null)return false;
  chair=freeChair((ServerLevel)npc.level(),e,home);
  return chair!=null&&npc.blockPosition().distSqr(chair)<=REACH*REACH;
 }
 /** A free chair of a home, the nearest to the resident first; null when it has none. */
 BlockPos freeChair(ServerLevel l,SettlementData.Entry e,Settlement.Building home){
  var design=BuildingBlueprints.design(home.type());if(design==null)return null;BlockPos best=null;double d=Double.MAX_VALUE;
  for(int x=0;x<design.width();x++)for(int z=0;z<design.depth();z++)for(int y=1;y<=9;y++){var p=BuildingPlacement.at(e,home,x,y,z);
   if(!l.hasChunkAt(p)||!(l.getBlockState(p).getBlock() instanceof ChairBlock)||SeatEntity.occupied(l,p))continue;
   double dd=npc.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5);if(dd<d){d=dd;best=p;}}
  return best;}
 @Override public boolean canContinueToUse(){
  if(chair==null||left<=0||walked>WALK_MAX||!hour()||!(npc.level().getBlockState(chair).getBlock() instanceof ChairBlock))return false;
  var who=SeatEntity.sitter(npc.level(),chair);if(who!=null&&who!=npc)return false;
  var e=entry();return e!=null&&!Sieges.besieged(npc.getServer(),e.settlement().id());
 }
 @Override public void start(){left=eager>0?eager:REST_MIN+npc.getRandom().nextInt(REST_MAX-REST_MIN+1);walked=0;repath=0;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  if(chair==null)return;
  if(SeatEntity.seated(npc)){SeatEntity.keep(npc);left--;npc.workStatus("resting");return;}
  walked++;double dx=npc.getX()-(chair.getX()+.5),dz=npc.getZ()-(chair.getZ()+.5),dy=Math.abs(npc.getY()-chair.getY());
  if(dx*dx+dz*dz<2.6&&dy<=1.2){npc.getNavigation().stop();if(SeatEntity.sit((ServerLevel)npc.level(),chair,npc)==null)chair=null;return;}
  if(--repath<=0||npc.getNavigation().isDone()){repath=20;npc.getNavigation().moveTo(chair.getX()+.5,chair.getY(),chair.getZ()+.5,.6);}
 }
 @Override public void stop(){SeatEntity.stand(npc);npc.getNavigation().stop();chair=null;left=0;if("resting".equals(npc.workStatus()))npc.workStatus("");}
}
