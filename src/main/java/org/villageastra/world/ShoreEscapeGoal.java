package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
/** A stalled swimmer takes a short, reachable bank before retrying the distant work route. */
public final class ShoreEscapeGoal extends Goal {
 private final ResidentEntity resident;private Vec3 anchor;private int sampled=-20,still,attempt=-200,elapsed;private BlockPos bank;private Path path;private Vec3 swimExit;private final Map<BlockPos,Integer> recentBanks=new LinkedHashMap<>(),unreachableBanks=new LinkedHashMap<>();
 public ShoreEscapeGoal(ResidentEntity resident){this.resident=resident;setFlags(EnumSet.of(Flag.MOVE));}
 public static boolean dryBank(ResidentEntity r,BlockPos p){var l=r.level();
  return l.hasChunkAt(p)&&l.getFluidState(p).isEmpty()&&l.getFluidState(p.above()).isEmpty()&&l.getFluidState(p.below()).isEmpty()
   &&l.getBlockState(p).getCollisionShape(l,p).isEmpty()&&l.getBlockState(p.above()).getCollisionShape(l,p.above()).isEmpty()
   &&l.getBlockState(p.below()).isFaceSturdy(l,p.below(),Direction.UP)&&!l.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK)&&!l.getBlockState(p.below()).is(Blocks.CACTUS);
 }
 private boolean atWater(){return resident.isInWaterOrBubble()||!resident.onGround()&&resident.level().getFluidState(resident.blockPosition().below()).is(FluidTags.WATER);}
 /** Water nodes are integral; the floating body rises above them and needs headroom under overhangs. */
 public static boolean clearWaterNode(ResidentEntity r,BlockPos p){
  var l=r.level();if(!l.getFluidState(p).is(FluidTags.WATER)&&l.getFluidState(p.below()).is(FluidTags.WATER))p=p.below();
  var state=l.getBlockState(p);if(state.is(Blocks.BUBBLE_COLUMN)&&state.getValue(net.minecraft.world.level.block.BubbleColumnBlock.DRAG_DOWN))return false;
  if(!l.getFluidState(p).is(FluidTags.WATER))return true;
  for(int up=0;up<4&&l.getFluidState(p.above()).is(FluidTags.WATER);up++)p=p.above();
  if(l.getFluidState(p.above()).is(FluidTags.WATER))return false;
  double half=r.getBbWidth()/2D;var box=new net.minecraft.world.phys.AABB(p.getX()+.5-half,p.getY()+.7,p.getZ()+.5-half,p.getX()+.5+half,p.getY()+.7+r.getBbHeight(),p.getZ()+.5+half);
  return !l.getBlockCollisions(r,box).iterator().hasNext();
 }
 public static boolean clearSwimPath(ResidentEntity r,Path path){for(int i=1;i<path.getNodeCount();i++)if(!clearWaterNode(r,path.getNodePos(i)))return false;return true;}
 /** Leave a low overhang horizontally before planning a floating route to shore.
  * Every sampled body box must be clear and remain over water; this never cuts terrain or lifts a body through a ceiling. */
 private Vec3 openWater(){
  var l=resident.level();var foot=resident.blockPosition();
  if(clearWaterNode(resident,foot))return null;
  Vec3 best=null;double cost=Double.MAX_VALUE;
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
   var p=foot.offset(x,0,z);if(x*x+z*z>16||!l.hasChunkAt(p)||!l.getFluidState(p).is(FluidTags.WATER))continue;
   var goal=new Vec3(p.getX()+.5,resident.getY(),p.getZ()+.5);double distance=goal.distanceToSqr(resident.position());if(distance<.25||distance>=cost)continue;
   if(!clearWaterNode(resident,p))continue;
   boolean clear=true;int steps=(int)Math.ceil(Math.sqrt(distance)/.15);
   for(int i=1;i<=steps;i++){
    var delta=goal.subtract(resident.position()).scale(i/(double)steps);var point=resident.position().add(delta);var cell=BlockPos.containing(point);
    if(!l.hasChunkAt(cell)||!l.getFluidState(cell).is(FluidTags.WATER)||l.getBlockState(cell).is(Blocks.BUBBLE_COLUMN)||l.getBlockCollisions(resident,resident.getBoundingBox().move(delta)).iterator().hasNext()){clear=false;break;}
   }
   if(clear){best=goal;cost=distance;}
  }return best;
 }
 @Override public boolean canUse(){
  if(!atWater()||resident.isPassenger()||resident.isSleeping()){anchor=null;still=0;return false;}
  if(resident.tickCount-sampled<20)return false;sampled=resident.tickCount;var now=resident.position();
  if(anchor==null||now.multiply(1,0,1).distanceToSqr(anchor.multiply(1,0,1))>.5625){anchor=now;still=0;}else still+=20;
  boolean submerged=resident.isEyeInFluid(FluidTags.WATER),urgent=submerged&&resident.getAirSupply()<220;
  // A roof can keep ordinary floating below the surface. Leave sideways while
  // there is still air, instead of requiring an already dry head to start recovery.
  if(still<(urgent?0:200)||resident.tickCount-attempt<(urgent?20:200))return false;attempt=resident.tickCount;
  swimExit=openWater();if(swimExit!=null){bank=null;path=null;return true;}
  if(submerged)return false; // With no verified open column, ordinary swimming retains control.
  return chooseBank();
 }
 private boolean chooseBank(){
  var now=resident.position();var foot=resident.blockPosition();var candidates=new ArrayList<BlockPos>();
  for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)if(x*x+z*z<=24*24)for(int y=-1;y<=4;y++){var p=foot.offset(x,y,z);if(dryBank(resident,p))candidates.add(p);}
  // A dry shelf inside a flooded cave is often another dead end. Prefer a bank open to the surface.
  candidates.sort(Comparator.comparingDouble(p->p.distToCenterSqr(now)+Math.max(0,p.getY()-foot.getY()-1)*10000D+(resident.level().canSeeSky(p.above())?0:100000D)));
  recentBanks.values().removeIf(t->resident.tickCount-t>4800);
  // Continue a bounded search instead of retrying the same inaccessible shelves forever.
  unreachableBanks.values().removeIf(t->resident.tickCount-t>2400);
  int tried=0;for(var p:candidates){
   if(unreachableBanks.containsKey(p)||recentBanks.keySet().stream().anyMatch(b->b.distSqr(p)<=16))continue;
   if(++tried>32)break;var found=resident.routeTo(p,0,48);
   if(found!=null&&found.canReach()&&clearSwimPath(resident,found)){bank=p;path=found;return true;}
   unreachableBanks.put(p,resident.tickCount);while(unreachableBanks.size()>512)unreachableBanks.remove(unreachableBanks.keySet().iterator().next());
  }
  return false;
 }
 @Override public void start(){elapsed=0;if(swimExit!=null)resident.getNavigation().stop();else resident.getNavigation().moveTo(path,.8);}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 public static boolean landed(ResidentEntity r,BlockPos bank){return !r.isInWaterOrBubble()&&r.onGround()&&dryBank(r,bank)&&r.distanceToSqr(Vec3.atBottomCenterOf(bank))<=.04;}
 @Override public boolean canContinueToUse(){if(swimExit!=null)return elapsed<2400&&atWater()&&!resident.isPassenger()&&!resident.isSleeping();return bank!=null&&elapsed<2400&&!resident.isPassenger()&&!resident.isSleeping()&&dryBank(resident,bank)&&!landed(resident,bank);}
 @Override public void tick(){elapsed++;resident.workStatus("walking");
  if(swimExit!=null){resident.getMoveControl().setWantedPosition(swimExit.x,resident.getY(),swimExit.z,.8);if(elapsed%20==0&&resident.position().multiply(1,0,1).distanceToSqr(swimExit.multiply(1,0,1))<.01&&clearWaterNode(resident,resident.blockPosition())&&chooseBank()){swimExit=null;resident.getNavigation().moveTo(path,.8);}return;}
  // Navigation can finish its last node with the body still in water beside the bank.
  if(resident.distanceToSqr(Vec3.atBottomCenterOf(bank))<4){resident.getMoveControl().setWantedPosition(bank.getX()+.5,bank.getY(),bank.getZ()+.5,.8);}
  else if(elapsed%40==0){var next=resident.routeTo(bank,0,48);if(next!=null&&next.canReach()&&clearSwimPath(resident,next))resident.getNavigation().moveTo(next,.8);}
 }
 @Override public void stop(){if(bank!=null){if(Boolean.getBoolean("villageastra.firstHouseSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE shoreExit pos={} bank={} elapsed={} landed={}",resident.position(),bank,elapsed,landed(resident,bank));recentBanks.put(bank,resident.tickCount);while(recentBanks.size()>8)recentBanks.remove(recentBanks.keySet().iterator().next());}resident.getNavigation().stop();bank=null;path=null;swimExit=null;anchor=null;still=0;}
}
