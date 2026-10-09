package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DailyReturnAirborneGameTests {
 private record Trip(net.minecraft.server.level.ServerLevel level,Settlement settlement,ResidentEntity npc,Goal goal,BlockPos target,List<net.minecraft.world.level.ChunkPos> held){
  void close(){npc.discard();SettlementData.get(level.getServer()).remove(settlement.id());PhysicalFixtureChunks.release(level,held);}
 }
 private static Trip trip(GameTestHelper h,boolean sleep,boolean stairs){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+950272+(sleep?65536:0)+(stairs?131072:0),120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,36,-6,40);for(var chunk:held)l.getChunk(chunk.x,chunk.z);
  for(int x=-4;x<=36;x++)for(int z=-6;z<=40;z++)for(int y=-1;y<=9;y++){
   int top=!stairs?0:x<=5&&z<=34?5:x==6&&z<32?2:x>=6&&x<=10&&z>=32&&z<=34?10-x:0;
   l.setBlock(base.offset(x,y,z),(y<=top?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",22,0,0));SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);if(!sleep)r.fallIll();npc.bind(s.id(),r);npc.moveTo(base.getX()+2.5,base.getY()+(stairs?6:1),base.getZ()+.5,0,0);npc.setOnGround(true);
  BlockPos target;Goal goal;
  if(sleep){target=base.offset(25,1,4);l.setBlock(target,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.HEAD),2);l.setBlock(target.south(),Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART,BedPart.FOOT),2);goal=new SleepGoal(npc,true,()->14000L);}
  else{target=HomeNeighborhood.anchor(npc);goal=new PatientGoal(npc);}
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  return new Trip(l,s,npc,goal,target,held);
 }
 @GameTest(template="empty",batch="daily_return_airborne",timeoutTicks=100)
 public static void patientRepathDuringDryAirborneKeepsItsVerifiedReturn(GameTestHelper h){airborne(h,false);}
 @GameTest(template="empty",batch="daily_return_airborne",timeoutTicks=100)
 public static void nearbySleepRepathDuringDryAirborneKeepsItsVerifiedReturn(GameTestHelper h){airborne(h,true);}
 private static void airborne(GameTestHelper h,boolean sleep){
  var t=trip(h,sleep,false);try{
   h.assertTrue(t.goal.canUse(),"Own destination activates actual daily goal");t.goal.start();
   var route=ResourceReturnRoute.plan(t.npc,t.target);h.assertTrue(route!=null&&route.canReach(),"Grounded return is complete and safe");t.npc.getNavigation().moveTo(route,.6);
   // Isolate the documented native airborne planner boundary, as return_jump does.
   t.npc.setOnGround(false);t.npc.tickCount=20;h.assertTrue(ResourceReturnRoute.plan(t.npc,t.target)==null,"Native ground planner cannot issue a dry airborne route");
   t.goal.tick();h.assertTrue(t.npc.getNavigation().getPath()==route&&!t.npc.getNavigation().isDone(),"Actual daily goal must keep the verified route instead of moveTo(null) during a jump");
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="daily_return_airborne",timeoutTicks=100)
 public static void patientDoesNotKeepStaleWetWrongTargetPartialOrFinishedReturns(GameTestHelper h)throws Exception{guards(h,false);}
 @GameTest(template="empty",batch="daily_return_airborne",timeoutTicks=100)
 public static void sleepDoesNotKeepStaleWetWrongTargetPartialOrFinishedReturns(GameTestHelper h)throws Exception{guards(h,true);}
 private static Path wrapped(Path source,BlockPos target,boolean reached)throws Exception{
  var nodes=new ArrayList<net.minecraft.world.level.pathfinder.Node>();for(int i=0;i<source.getNodeCount();i++)nodes.add(source.getNode(i));
  var ctor=Class.forName("org.villageastra.world.ResourceReturnRoute$ReturnPath").getDeclaredConstructor(Path.class);ctor.setAccessible(true);return (Path)ctor.newInstance(new Path(nodes,target,reached));
 }
 private static void decision(Trip t){t.goal.start();t.npc.tickCount=20;t.goal.tick();}
 private static void guards(GameTestHelper h,boolean sleep)throws Exception{
  var t=trip(h,sleep,false);try{
   h.assertTrue(t.goal.canUse(),"Actual goal owns destination");var route=ResourceReturnRoute.plan(t.npc,t.target);h.assertTrue(route!=null&&route.canReach(),"Fixture route complete");
   for(int kind=0;kind<3;kind++){
    var held=wrapped(route,kind==0?t.target.east(3):t.target,kind!=1);if(kind==2)held.setNextNodeIndex(held.getNodeCount());t.npc.getNavigation().moveTo(held,.6);t.npc.setOnGround(false);decision(t);
    h.assertTrue(t.npc.getNavigation().getPath()!=held,"Airborne decision refuses "+(kind==0?"wrong target":kind==1?"partial route":"completed route"));t.npc.setOnGround(true);
   }
   for(int kind=0;kind<3;kind++){
    var held=wrapped(route,t.target,true);t.npc.getNavigation().moveTo(held,.6);var next=held.getNextNodePos();var changed=kind==0?next:kind==1?next.below():next.above();var original=t.level.getBlockState(changed);
    t.level.setBlock(changed,(kind==0?Blocks.WATER:kind==1?Blocks.AIR:Blocks.STONE).defaultBlockState(),2);t.npc.setOnGround(false);decision(t);
    h.assertTrue(t.npc.getNavigation().getPath()!=held,"Airborne decision refuses "+(kind==0?"wet landing":kind==1?"removed floor":"blocked headroom"));t.level.setBlock(changed,original,2);t.npc.setOnGround(true);
   }
   var held=wrapped(route,t.target,true);t.npc.getNavigation().moveTo(held,.6);var obstruction=held.getNodePos(2);t.level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),2);t.npc.setOnGround(true);decision(t);
   var replanned=t.npc.getNavigation().getPath();h.assertTrue(replanned!=held,"Grounded terrain change forces native route revalidation");
   if(replanned!=null)for(int i=replanned.getNextNodeIndex();i<replanned.getNodeCount();i++)h.assertTrue(!replanned.getNodePos(i).equals(obstruction),"Fresh route avoids changed obstacle");
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="daily_return_airborne_physical",timeoutTicks=2400)
 public static void patientPhysicallyWalksTheReversibleStairsWithoutHealthChanges(GameTestHelper h){physical(h,false);}
 @GameTest(template="empty",batch="daily_return_airborne_physical",timeoutTicks=2400)
 public static void sleepPhysicallyWalksTheReversibleStairsToItsActualBedHead(GameTestHelper h){physical(h,true);}
 private static void physical(GameTestHelper h,boolean sleep){
  var t=trip(h,sleep,true);var npc=t.npc;float health=npc.getHealth();var r=t.settlement.resident(npc.getUUID());var life=r.life();boolean[] done={false};int[] airborneDecisions={0},jumpRequests={0};
  h.assertTrue(t.goal.canUse(),"Physical daily goal activates at its starting platform");npc.goalSelector.addGoal(5,t.goal);h.onEachTick(()->{if(done[0])return;if(!npc.isAddedToWorld()&&t.level.isPositionEntityTicking(npc.blockPosition())){
   h.assertTrue(t.level.getEntity(npc.getUUID())==null,"Fixture UUID has no competing body before add");boolean added=t.level.addFreshEntity(npc);
   h.assertTrue(added&&t.level.getEntity(npc.getUUID())==npc,"Native add succeeds and canonical body is the measured fixture");
  }});
  Runnable clean=()->{done[0]=true;t.close();};
  h.onEachTick(()->{
   if(done[0])return;var nav=npc.getNavigation();var path=nav.getPath();
   if(!sleep&&jumpRequests[0]==0&&npc.onGround()&&!npc.isInWaterOrBubble()&&npc.tickCount%20==18
     &&path!=null&&path.getTarget().equals(t.target)&&path.canReach()&&!nav.isDone()&&HarvestAccess.reversible(path)
     &&ShoreEscapeGoal.dryBank(npc,npc.blockPosition())){
    boolean supported=true;for(int i=path.getNextNodeIndex();i<path.getNodeCount();i++)if(!ShoreEscapeGoal.dryBank(npc,path.getNodePos(i))){supported=false;break;}
    if(supported){npc.getJumpControl().jump();jumpRequests[0]++;}
   }
   if(!npc.onGround()&&!npc.isInWaterOrBubble()&&!npc.isSleeping()&&(sleep?npc.getX()>=t.target.getX()-19:jumpRequests[0]>0)&&path!=null&&!nav.isDone()&&path.canReach()){
    // Force the ordinary decision boundary during a real physics-driven step, without moving the body.
    // Sleep's timer is restarted; Patient's real JumpControl overlaps its ordinary tick parity.
    if(sleep){t.goal.start();t.goal.tick();}else if(npc.tickCount%20<2)t.goal.tick();
    if(sleep||npc.tickCount%20<2){airborneDecisions[0]++;h.assertTrue(nav.getPath()==path&&!nav.isDone(),"Actual airborne stair decision keeps its supported return");}
   }
   h.assertTrue(npc.isAlive()&&npc.getHealth()==health&&r.life()==life,"Native stairs change neither health nor life stage");
   boolean reached=sleep?npc.isSleeping()||"sleeping".equals(npc.workStatus()):npc.distanceToSqr(t.target.getX()+.5,t.target.getY(),t.target.getZ()+.5)<=16&&nav.isDone();
   if(!reached)return;h.assertTrue(airborneDecisions[0]>0,"At least one actual airborne stair decision exercised the guard");
   if(sleep)h.assertTrue(t.level.getBlockState(t.target).getValue(BedBlock.PART)==BedPart.HEAD&&npc.distanceToSqr(t.target.getCenter())<=4,"Own real bed HEAD reached physically");
   else h.assertTrue(r.sick()&&jumpRequests[0]>0,"Physical patient uses a phase-aligned native jump and grants no healing");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_DAILY_RETURN_AIRBORNE sleep={} decisions={} health={} physical=true jumpRequests={}",sleep,airborneDecisions[0],npc.getHealth(),jumpRequests[0]);clean.run();h.succeed();
  });
  h.runAtTickTime(2300,()->{if(done[0])return;String why="Daily return stalled: sleep="+sleep+" pos="+npc.position()+" goals="+npc.runningGoals()+" airborneDecisions="+airborneDecisions[0]+" tick="+npc.tickCount+" ticking="+t.level.isPositionEntityTicking(npc.blockPosition())+" added="+npc.isAddedToWorld()+" sick="+r.sick()+" canUse="+t.goal.canUse()+" forced="+t.level.getForcedChunks().contains(new net.minecraft.world.level.ChunkPos(npc.blockPosition()).toLong())+" canonical="+t.level.getEntity(npc.getUUID());clean.run();h.assertTrue(false,why);});
 }
}
