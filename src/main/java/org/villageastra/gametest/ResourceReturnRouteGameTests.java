package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.pathfinder.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResourceReturnRouteGameTests {
 @GameTest(template="empty",batch="return_recompute",timeoutTicks=200)
 public static void terrainUpdatesPreserveTheLongReturnRouteAndItsPolicy(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+73728,160,at.getZ());
  var held=new ArrayList<net.minecraft.world.level.ChunkPos>();for(int x=(base.getX()-2)>>4;x<=(base.getX()+184)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+3)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(cp.x,cp.z,true);held.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=184;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);var end=base.offset(180,1,0);var nav=npc.getNavigation();
  nav.createPath(end,0);var route=ResourceReturnRoute.plan(npc,end);h.assertTrue(route!=null&&route.canReach(),"The 178-block expedition route is complete");
  var plain=new Path(java.util.stream.IntStream.range(0,route.getNodeCount()).mapToObj(route::getNode).collect(java.util.stream.Collectors.toCollection(ArrayList::new)),end,true);
  nav.moveTo(plain,.8);nav.moveTo(route,.8);
  h.startSequence().thenIdle(25).thenExecute(()->{try{var fresh=ResourceReturnRoute.plan(npc,end);h.assertTrue(fresh!=null&&fresh.canReach(),"The fixture retains the entire loaded route before recomputation");nav.recomputePath();h.assertTrue(nav.getPath()!=null&&nav.getPath().canReach()&&nav.getPath().getTarget().equals(end),"A terrain update must not replace the full expedition return with a shorter incomplete ordinary route");}finally{for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}}).thenSucceed();
 }
 @GameTest(template="empty",batch="return_station",timeoutTicks=2400)
 public static void deliveryReturnsBesideAnOccupiedWorkstation(GameTestHelper h){var l=h.getLevel();var base=pond(h,77824);var start=base.offset(2,1,12);var end=base.offset(22,1,12);
  l.setBlock(end,Blocks.LECTERN.defaultBlockState(),2);for(int x=20;x<=24;x++)for(int z=10;z<=14;z++)l.setBlock(base.offset(x,3,z),Blocks.STONE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);var route=ResourceReturnRoute.plan(npc,end);
  h.assertTrue(route!=null&&route.canReach(),"Delivery must reach a usable position beside a solid lectern under the town hall ceiling");
  h.assertTrue(route.getEndNode().asBlockPos().distSqr(end)<=5,"The final stand is within the existing delivery reach");h.succeed();
 }
 @GameTest(template="empty",batch="return_jump",timeoutTicks=200)
 public static void aTerrainUpdateDuringAJumpKeepsTheExistingReturnRoute(GameTestHelper h){var l=h.getLevel();var base=pond(h,81920);var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+12.5);npc.setOnGround(true);var end=base.offset(22,1,12);var route=ResourceReturnRoute.plan(npc,end);h.assertTrue(route!=null&&route.canReach(),"A grounded worker has a complete route");npc.getNavigation().moveTo(route,.8);
  h.startSequence().thenIdle(25).thenExecute(()->{npc.setOnGround(false);npc.getNavigation().recomputePath();h.assertTrue(npc.getNavigation().getPath()==route,"A temporary airborne phase must keep the already planned return instead of erasing it");npc.setOnGround(true);}).thenSucceed();
 }
 @GameTest(template="empty",batch="return_slope",timeoutTicks=300)
 public static void aPartialCaveRouteStillHasOnlyReturnableSteps(GameTestHelper h){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+86016,160,at.getZ());
  try(var in=ResourceReturnRouteGameTests.class.getResourceAsStream("/data/villageastra/fixtures/return_slope.json")){var columns=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:columns){var col=raw.getAsJsonArray();int x=col.get(0).getAsInt(),z=col.get(1).getAsInt();for(var r:col.get(2).getAsJsonArray()){var run=r.getAsJsonArray();var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("minecraft",run.get(2).getAsString()));for(int y=run.get(0).getAsInt();y<run.get(1).getAsInt();y++)l.setBlock(base.offset(x,y,z),block.defaultBlockState(),2);}}
  }catch(java.io.IOException ex){throw new RuntimeException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+17.575,base.getY()+8,base.getZ()+6.165);npc.setOnGround(true);var target=base.offset(-172,13,60);var route=ResourceReturnRoute.plan(npc,target);
  h.assertTrue(route!=null,"The cave has a partial dry route");for(int i=1;i<route.getNodeCount();i++)h.assertTrue(Math.abs(route.getNode(i).y-route.getNode(i-1).y)<=1,"Every partial return step must remain climbable, got "+route.getNodePos(i-1)+" -> "+route.getNodePos(i));h.succeed();
 }
 @GameTest(template="empty",batch="return_water_roof",timeoutTicks=300)
 public static void thePlannerFindsTheOpenWaterDetourAroundALowCaveRoof(GameTestHelper h){var l=h.getLevel();var base=pond(h,94208);
  for(int x=-2;x<=26;x++)for(int z=-2;z<=10;z++)if(x==-2||x==26||z==-2||z==10)for(int y=0;y<=6;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int x=7;x<=17;x++)for(int z=-1;z<=9;z++)for(int y=-2;y<=0;y++)l.setBlock(base.offset(x,y,z),Blocks.WATER.defaultBlockState(),2);
  for(int x=7;x<=17;x++)for(int z=3;z<=5;z++)l.setBlock(base.offset(x,2,z),Blocks.STONE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+4.5);npc.setOnGround(true);var route=ResourceReturnRoute.plan(npc,base.offset(22,1,4));
  h.assertTrue(route!=null&&route.canReach()&&ShoreEscapeGoal.clearSwimPath(npc,route),"Search must find the safe open-water alternative, not reject the shortest roof collision and give up");h.succeed();
 }
 private static BlockPos pond(GameTestHelper h,int offset){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+offset,160,at.getZ());
  for(int x=-2;x<=26;x++)for(int z=-2;z<=14;z++)for(int y=-3;y<=8;y++)l.setBlock(base.offset(x,y,z),(y==-3||y<=0&&(x<7||x>17||z>8||z<0)?Blocks.STONE:y<=0?Blocks.WATER:Blocks.AIR).defaultBlockState(),2);return base;
 }
 private static boolean dry(net.minecraft.server.level.ServerLevel l,Path path){if(path==null)return false;for(int i=0;i<path.getNodeCount();i++){var p=path.getNodePos(i);if(!l.getFluidState(p).isEmpty()||!l.getFluidState(p.below()).isEmpty())return false;}return true;}
 @GameTest(template="empty",batch="return_dry",timeoutTicks=2400)
 public static void carrierReturnsAroundThePondUsingRealNavigation(GameTestHelper h){var l=h.getLevel();var base=pond(h,61440);var start=base.offset(2,1,4);var end=base.offset(22,1,4);var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+26)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+14)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(cp.x,cp.z,true);held.add(cp);}l.getChunk(x,z);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);var before=npc.getNavigation().getPath();float malus=npc.getPathfindingMalus(BlockPathTypes.WATER);var path=ResourceReturnRoute.plan(npc,end);
  h.assertTrue(path!=null&&path.canReach()&&dry(l,path),"A complete dry detour takes precedence over a swim");h.assertTrue(npc.getPathfindingMalus(BlockPathTypes.WATER)==malus&&npc.getNavigation().getPath()==before,"Planning restores water preferences and leaves active navigation alone");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,end),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(start),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{h.assertTrue(npc.isAlive()&&!npc.isInWaterOrBubble(),"The returning carrier stays alive and on the dry detour");if(npc.distanceToSqr(end.getCenter())<2&&npc.onGround()){npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();}});
 }
 @GameTest(template="empty",batch="return_partial",timeoutTicks=200)
 public static void anIncompleteReturnNeverPromisesAnotherSwimIntoTheDeadEnd(GameTestHelper h){var l=h.getLevel();var base=pond(h,65536);var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+4.5);npc.setOnGround(true);var end=base.offset(22,1,4);
  for(var p:BlockPos.betweenClosed(end.offset(-1,0,-1),end.offset(1,5,1)))l.setBlock(p,Blocks.STONE.defaultBlockState(),2);
  var path=ResourceReturnRoute.plan(npc,end);h.assertTrue(path==null||!path.canReach()&&dry(l,path),"No partial water route is accepted as the way home");h.succeed();
 }
 @GameTest(template="empty",batch="return_bubbles",timeoutTicks=200)
 public static void downwardMagmaColumnsAreNotSafeWalkingWater(GameTestHelper h){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+69632,160,at.getZ());
  for(int x=-2;x<=20;x++)for(int z=-3;z<=3;z++)for(int y=-3;y<=6;y++)l.setBlock(base.offset(x,y,z),(y==-3||z!=0&&y<=5||y<=0&&(x<6||x>14)?Blocks.STONE:y<=0?Blocks.WATER:Blocks.AIR).defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);var end=base.offset(18,1,0);
  for(boolean down:new boolean[]{true,false}){for(int x=8;x<=12;x++){l.setBlock(base.offset(x,-3,0),(down?Blocks.MAGMA_BLOCK:Blocks.SOUL_SAND).defaultBlockState(),2);for(int y=-2;y<=0;y++)l.setBlock(base.offset(x,y,0),Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BubbleColumnBlock.DRAG_DOWN,down),2);}
   var path=npc.routeTo(end,0,48);h.assertTrue(down?path==null||!path.canReach():path!=null&&path.canReach(),"Downward columns are excluded without banning upward water: down="+down);
  }h.succeed();
 }
 @GameTest(template="empty",batch="return_waterlogged_obstacle",timeoutTicks=2400)
 public static void returnSearchAvoidsUnsafeNodeBeforeRejectingTheWholeRoute(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+159744,150,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-3,16,-3,24);
  for(int x=-3;x<=16;x++)for(int z=-3;z<=24;z++)for(int y=-3;y<=5;y++){
   boolean passage=z==0&&x>=0&&x<=13||z>=0&&z<=20&&(x==3||x==7)||z==20&&x>=3&&x<=7;
   l.setBlock(base.offset(x,y,z),(passage&&y>=1&&y<=3?Blocks.AIR:Blocks.STONE).defaultBlockState(),2);
  }
  for(int x=9;x<=10;x++)for(int y=-2;y<=0;y++)l.setBlock(base.offset(x,y,0),Blocks.WATER.defaultBlockState(),2);
  var stem=Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION,net.minecraft.core.Direction.UP).setValue(PointedDripstoneBlock.THICKNESS,net.minecraft.world.level.block.state.properties.DripstoneThickness.FRUSTUM).setValue(PointedDripstoneBlock.WATERLOGGED,true);
  l.setBlock(base.offset(5,0,0),stem,2);l.setBlock(base.offset(5,1,0),stem.setValue(PointedDripstoneBlock.THICKNESS,net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP).setValue(PointedDripstoneBlock.WATERLOGGED,false),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);var target=base.offset(13,1,0);
  var route=ResourceReturnRoute.plan(npc,target);h.assertTrue(route!=null&&route.canReach()&&ShoreEscapeGoal.clearSwimPath(npc,route),"Return search must choose the longer safe passage around the waterlogged obstacle");
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new FloatGoal(npc));npc.goalSelector.addGoal(1,new ShoreEscapeGoal(npc));
  npc.goalSelector.addGoal(5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0&&(npc.onGround()||npc.isInWaterOrBubble()))npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);}
   public void stop(){npc.getNavigation().stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Obstacle corridor chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};
  boolean[] detoured={false};
  h.onEachTick(()->{if(npc.getZ()>base.getZ()+18)detoured[0]=true;if(npc.onGround()&&npc.distanceToSqr(target.getCenter())<2){h.assertTrue(detoured[0]&&npc.getHealth()==npc.getMaxHealth(),"Body completes safe detour and water crossing with full health");clean.run();h.succeed();}});
  h.runAtTickTime(2200,()->{String why="No safe physical return: "+npc.position()+" ticks="+npc.tickCount;clean.run();h.assertTrue(false,why);});
 }

}
