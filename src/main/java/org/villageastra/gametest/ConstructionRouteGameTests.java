package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionRouteGameTests {
 @GameTest(template="empty",batch="construction_route",timeoutTicks=4000)
 public static void distantBuilderFindsWorkStandAndPhysicallyReturnsToSite(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+57344,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+144)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+4)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-4;x<=144;x++)for(int z=-4;z<=4;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var start=base.above();var target=base.offset(140,1,0);var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var stand=HallUpgradeGoal.stand(l,npc,target,new int[]{start.getY()});h.assertTrue(stand!=null,"Builder must find a work stand beyond the ordinary navigation range: "+HallUpgradeGoal.lastStand);
  var route=ConstructionRoutes.plan(npc,stand);h.assertTrue(HarvestAccess.reversible(route),"Site approach must be reachable along returnable steps");
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.getNavigation().isDone()||npc.tickCount%20==0)npc.getNavigation().moveTo(ConstructionRoutes.plan(npc,stand),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(start),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Builder added"));
  h.onEachTick(()->{if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(stand))>.5||!npc.onGround())return;
   h.assertTrue(l.getBlockState(base.offset(70,0,0)).is(Blocks.STONE)&&l.getBlockState(target).isAir(),"Route did not alter the terrain or place building blocks remotely");npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(3800,()->h.assertTrue(false,"Builder did not return to site: "+npc.position()));
 }
 @GameTest(template="empty",batch="construction_stand_candidates",timeoutTicks=500)
 public static void builderChecksReachableStandsBeyondTheFirstTwelve(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(0,12,0));
  for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=4;y++){
   boolean wall=(Math.abs(x)==3&&Math.abs(z)<=3||Math.abs(z)==3&&Math.abs(x)<=3)&&y>0;
   l.setBlock(base.offset(x,y,z),(y==0||wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()-5.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  var target=base.above();var stand=HallUpgradeGoal.stand(l,npc,target,new int[]{target.getY()});
  h.assertTrue(stand!=null,"Reachable exterior stand must not be hidden by the first twelve enclosed candidates: "+HallUpgradeGoal.lastStand);
  h.assertTrue(Math.abs(stand.getX()-base.getX())>3||Math.abs(stand.getZ()-base.getZ())>3,"Selected position must be outside sealed room");
  var path=ConstructionRoutes.plan(npc,stand);h.assertTrue(path!=null&&path.canReach(),"Selected stand has a real path");h.succeed();
 }
 @GameTest(template="empty",batch="construction_column_approach",timeoutTicks=200)
 public static void columnApproachAdvancesToTheColumnInsteadOfChoosingCurrentCell(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(6,12,6));
  for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var target=base.offset(4,1,0);for(int y=1;y<=3;y++)l.setBlock(target.above(y-1),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  var stand=HallUpgradeGoal.stand(l,npc,target,new int[]{target.getY()});
  h.assertTrue(stand!=null&&stand.distSqr(target)<=2,"A column approach must advance to its reachable near side instead of accepting the worker's current cell: "+stand);
  h.assertTrue(ConstructionRoutes.plan(npc,stand).canReach(),"Column approach remains physically reachable");h.succeed();
 }
}
