package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ShoreEscapeGameTests {
 @GameTest(template="empty",batch="shore_headroom",timeoutTicks=100)
 public static void floatingBodyNeedsMoreHeadroomThanAnIntegerWaterNode(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,12,5));var npc=VillageAstra.RESIDENT.get().create(l);var next=p.east();
  for(var at:List.of(p,next)){l.setBlock(at,Blocks.WATER.defaultBlockState(),2);l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at.above(2),Blocks.AIR.defaultBlockState(),2);}
  var path=new net.minecraft.world.level.pathfinder.Path(List.of(new net.minecraft.world.level.pathfinder.Node(p.getX(),p.getY(),p.getZ()),new net.minecraft.world.level.pathfinder.Node(next.getX(),next.getY(),next.getZ())),next,true);
  h.assertTrue(org.villageastra.world.ShoreEscapeGoal.clearSwimPath(npc,path),"Open surface allows the ordinary swimming body");
  l.setBlock(next.above(2),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!org.villageastra.world.ShoreEscapeGoal.clearSwimPath(npc,path),"A two-cell node corridor still clips a body floating above the integer node");
  h.succeed();
 }
 @GameTest(template="empty",batch="shore_boundaries",timeoutTicks=100)
 public static void shoreNeedsSolidDrySafeSupport(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,10,5));var npc=VillageAstra.RESIDENT.get().create(l);
  l.setBlock(p,Blocks.AIR.defaultBlockState(),2);l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);
  for(var floor:List.of(Blocks.AIR,Blocks.WATER,Blocks.MAGMA_BLOCK,Blocks.CACTUS)){l.setBlock(p.below(),floor.defaultBlockState(),2);h.assertTrue(!org.villageastra.world.ShoreEscapeGoal.dryBank(npc,p),"No landing on unsupported, wet or harmful ground: "+floor);}
  l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(org.villageastra.world.ShoreEscapeGoal.dryBank(npc,p),"A dry supported bank is valid");
  npc.moveTo(p.getX()+.95,p.getY(),p.getZ()+.95);npc.setOnGround(true);h.assertTrue(!org.villageastra.world.ShoreEscapeGoal.landed(npc,p),"Touching the edge is not a completed landing");
  npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(org.villageastra.world.ShoreEscapeGoal.landed(npc,p),"The grounded body centred on dry support has landed");
  l.setBlock(p.above(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!org.villageastra.world.ShoreEscapeGoal.dryBank(npc,p),"The body needs headroom");h.succeed();
 }
 @GameTest(template="empty",batch="shore_escape",timeoutTicks=3600)
 public static void stalledSwimmerFindsAnActualDryBankWithoutChangingTerrain(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="shore_cave",timeoutTicks=3600)
 public static void swimmerPrefersTheOpenBankToAShelfInsideTheFloodedCave(GameTestHelper h){run(h,true);}
 @GameTest(template="empty",batch="shore_overhang",timeoutTicks=3600)
 public static void swimmerLeavesLowOverhangBeforeApproachingDryBank(GameTestHelper h){run(h,false,true);}
 @GameTest(template="empty",batch="shore_jump_recovery",timeoutTicks=3600)
 public static void failedBankJumpCanTurnBackToOpenWater(GameTestHelper h){run(h,false,true,true);}
 private static void run(GameTestHelper h,boolean cave){run(h,cave,false);}
 private static void run(GameTestHelper h,boolean cave,boolean overhang){
  run(h,cave,overhang,false);
 }
 private static void run(GameTestHelper h,boolean cave,boolean overhang,boolean failedJump){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(overhang?49152:cave?45056:40960),160,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-14)>>4;x<=(base.getX()+14)>>4;x++)for(int z=(base.getZ()-14)>>4;z<=(base.getZ()+14)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-13;x<=13;x++)for(int z=-13;z<=13;z++)for(int y=-4;y<=7;y++)l.setBlock(base.offset(x,y,z),(y==-4||x<=-4&&y<=5||z>=8&&y<=0?Blocks.STONE:y<=0?Blocks.WATER:Blocks.AIR).defaultBlockState(),2);
  if(overhang)for(int z=-3;z<=3;z++)l.setBlock(base.offset(-3,2,z),Blocks.STONE.defaultBlockState(),2);
  if(cave)for(int x=-3;x<=0;x++)for(int z=8;z<=13;z++)l.setBlock(base.offset(x,4,z),Blocks.STONE.defaultBlockState(),2);
  var settlement=new org.villageastra.domain.Settlement(UUID.randomUUID());var home=UUID.randomUUID();settlement.addHome(new org.villageastra.domain.Settlement.Home(home,1,2,true));org.villageastra.server.SettlementData.get(l.getServer()).add(new org.villageastra.server.SettlementData.Entry(settlement,l.dimension().location().toString(),base));
  var npc=VillageAstra.RESIDENT.get().create(l);var resident=new org.villageastra.domain.Resident(npc.getUUID(),org.villageastra.domain.Resident.Life.ADULT,true,null,null,-1);settlement.admit(resident,home);npc.bind(settlement.id(),resident);npc.moveTo(base.getX()-2.5,base.getY()+.2,base.getZ()+.5);
  h.assertTrue(l.noCollision(npc),"Fixture starts with the whole body clear of the high bank");
  // The work route has exhausted its path against the high bank; ordinary floating alone cannot choose another shore.
  npc.goalSelector.removeAllGoals(g->!Set.of("FloatGoal","PitEscapeGoal","ShoreEscapeGoal").contains(g.getClass().getSimpleName()));npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public void tick(){if(failedJump)npc.getMoveControl().setWantedPosition(base.getX()-3.2,base.getY()+1,base.getZ()+.5,.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{h.assertTrue(npc.isAlive(),"Swimmer must stay alive");if(npc.getZ()>=base.getZ()+8&&npc.getY()>=base.getY()+1&&npc.onGround()&&!npc.isInWaterOrBubble()&&!npc.runningGoals().contains("ShoreEscapeGoal")){
   h.assertTrue(l.getBlockState(base.offset(-4,2,0)).is(Blocks.STONE)&&l.getBlockState(base).is(Blocks.WATER),"Recovery neither cuts the cliff nor fills the lake");
   h.assertTrue(!cave||l.canSeeSky(npc.blockPosition().above()),"The closer cave shelf must not displace a reachable open bank");
   npc.discard();org.villageastra.server.SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(3450,()->h.assertTrue(false,"Swimmer must reach the low bank with ordinary navigation: "+npc.position()+" tick="+npc.tickCount+" goals="+npc.runningGoals()));
 }
 @GameTest(template="empty",batch="shore_air_exit",timeoutTicks=1600)
 public static void submergedResidentLeavesACappedWaterColumnThroughSafeAir(GameTestHelper h){airColumn(h,0);}
 @GameTest(template="empty",batch="shore_air_exit",timeoutTicks=1600)
 public static void submergedResidentRejectsAnAirExitAboveADeepDrop(GameTestHelper h){airColumn(h,1);}
 @GameTest(template="empty",batch="shore_air_exit",timeoutTicks=1600)
 public static void submergedResidentRejectsAnAirExitBehindStone(GameTestHelper h){airColumn(h,2);}
 private static void airColumn(GameTestHelper h,int mode){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+176128,150,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-3,5,-3,3);
  for(int x=-3;x<=5;x++)for(int z=-3;z<=3;z++)for(int y=-2;y<=9;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int x=mode==2?2:1;x<=3;x++)for(int y=mode==1?-1:4;y<=7;y++)l.setBlock(base.offset(x,y,0),Blocks.AIR.defaultBlockState(),2);
  for(int y=0;y<=7;y++)l.setBlock(base.offset(0,y,0),Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,y==7?1:8),2);
  l.setBlock(base.offset(0,7,-1),Blocks.WATER.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+6.2,base.getZ()+.5);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.FloatGoal(npc));npc.goalSelector.addGoal(1,new org.villageastra.world.ShoreEscapeGoal(npc));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Capped waterfall chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(npc.tickCount==0)return;if(mode!=0&&npc.tickCount>=200){h.assertTrue(npc.isInWaterOrBubble()&&npc.getX()<base.getX()+1&&npc.getHealth()==npc.getMaxHealth(),"Unsafe air exit must not be selected");clean.run();h.succeed();return;}if(npc.getHealth()<npc.getMaxHealth()){String why="Water column recovery must happen before drowning: "+npc.position()+" bodyTicks="+npc.tickCount;clean.run();h.assertTrue(false,why);}if(npc.onGround()&&!npc.isInWaterOrBubble()&&npc.getX()>base.getX()+1){h.assertTrue(l.getBlockState(base.offset(0,8,0)).is(Blocks.STONE),"Escape preserves the capped roof");clean.run();h.succeed();}});
  h.runAtTickTime(1400,()->{String why="No physical air exit: "+npc.position()+" bodyTicks="+npc.tickCount;clean.run();h.assertTrue(false,why);});
 }

}
