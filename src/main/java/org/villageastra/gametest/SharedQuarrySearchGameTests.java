package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Native multi-target planning across a real gap, without a physical work-trip claim. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SharedQuarrySearchGameTests {
 @GameTest(template="empty",batch="survey_range_bound",timeoutTicks=200)
 public static void outOfRangeSurveySkipsNativeWorkButKeepsMixedTargetsAndBodyMovement(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+655360,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+205)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=205;x++)for(int z=-2;z<=4;z++)for(int y=-1;y<=6;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);
  var far=base.offset(200,1,2);var edge=base.offset(129,1,2);
  try{
   h.assertTrue(!HarvestAccess.reversible(npc.routeTo(far,0,128)),"The real native planner cannot reach this platform within its radius");
   try(var survey=HarvestRouteCache.survey(npc)){
    h.assertTrue(HarvestRouteCache.plan(npc,far,128)==null&&HarvestRouteCache.planAny(npc,Set.of(far,far.south()),128)==null,"Guaranteed distant sensing targets should be rejected without native search");
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==0,"Out-of-range sensing spends no native plan allowance");
    var path=HarvestRouteCache.planAny(npc,Set.of(far,edge),128);h.assertTrue(HarvestAccess.reversible(path)&&path.getTarget().equals(edge),"A far target must not hide the genuinely reachable radius-edge target");
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==1,"Mixed sensing uses one fresh native query");
   }
   h.assertTrue(!HarvestAccess.reversible(HarvestRouteCache.plan(npc,far,128)),"Actual work planning outside the sensing scope retains its native policy");
   npc.moveTo(base.getX()+80.5,121,base.getZ()+2.5);npc.setOnGround(true);
   try(var survey=HarvestRouteCache.survey(npc)){
    var path=HarvestRouteCache.plan(npc,far,128);h.assertTrue(HarvestAccess.reversible(path)&&path.getTarget().equals(far),"The same target is considered afresh after the body moves into range");
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==2,"Distance refusal never becomes a persistent failed route");
   }
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SURVEY_RANGE VERIFIED distantPlans=0 mixedPlans=1 movedPlans=1 edgeDistance=127");
  }finally{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}h.succeed();
 }
 @GameTest(template="empty",batch="quarry_shared_search",timeoutTicks=200)
 public static void severalPlatformsShareOneSearchAndAnUnreachableNearTargetDoesNotHideTheFarOne(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+393216,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-74)>>4;x<=(base.getX()+36)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+6)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-74;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0&&(x<=12||x>=21)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   var targets=new LinkedHashSet<BlockPos>();for(int x=24;x<32;x++)targets.add(base.offset(x,1,2));
   try(var survey=HarvestRouteCache.survey(npc)){
    for(var feet:targets)h.assertTrue(!HarvestAccess.reversible(HarvestRouteCache.plan(npc,feet,128)),"Every individual native search rejects the unsupported gap");
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==8,"Eight distinct platforms require eight individual native searches");
    for(int i=0;i<8;i++)h.assertTrue(!HarvestAccess.reversible(HarvestRouteCache.planAny(npc,targets,128)),"Combined search still rejects the entire unreachable island");
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==9&&HarvestRouteCache.stats(npc).hits()==7,"Same platform set uses one combined native search and seven brief misses");
    var mixed=new LinkedHashSet<>(targets);var far=base.offset(-60,1,2);mixed.add(far);
    for(int i=0;i<2;i++){
     var path=HarvestRouteCache.planAny(npc,mixed,128);
     h.assertTrue(HarvestAccess.reversible(path)&&path.getTarget().equals(far),"Nearby unreachable targets do not hide the farther real reversible platform");
    }
    h.assertTrue(HarvestRouteCache.stats(npc).plans()==11,"Successful combined paths are not retained");
   }
   for(int x=13;x<=20;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   h.assertTrue(HarvestAccess.reversible(HarvestRouteCache.planAny(npc,targets,128)),"Working search sees a newly opened bridge immediately outside sensing");
  }finally{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}
  h.succeed();
 }
}
