package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Real native planner, isolated sensing fixture; does not claim a physical work trip. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HarvestSensingGameTests {
 @GameTest(template="empty",batch="harvest_sensing",timeoutTicks=100)
 public static void failedSurveyRoutesAreBriefAndNeverReuseSuccessfulOrWorkingPaths(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+344064,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-6)>>4;x<=(base.getX()+36)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+6)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0&&(x<=12||x>=21)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);
  var target=base.offset(24,1,2);var reachable=base.offset(8,1,2);
  for(int i=0;i<8;i++)h.assertTrue(!HarvestAccess.reversible(npc.routeTo(target,0,64)),"Actual native route cannot cross the unsupported gap");
  try(var survey=HarvestRouteCache.survey(npc)){
   for(int i=0;i<8;i++)h.assertTrue(!HarvestAccess.reversible(HarvestRouteCache.plan(npc,target,64)),"Cached sensing still rejects the unreachable target");
   h.assertTrue(HarvestRouteCache.stats(npc).plans()==1&&HarvestRouteCache.stats(npc).hits()==7,"Eight repeated misses require one actual native plan");
   HarvestRouteCache.plan(npc,target,128);h.assertTrue(HarvestRouteCache.stats(npc).plans()==2,"Different route range has its own native check");
   for(int i=0;i<2;i++)h.assertTrue(HarvestAccess.reversible(HarvestRouteCache.plan(npc,reachable,64)),"Reachable path remains genuinely successful");
   h.assertTrue(HarvestRouteCache.stats(npc).plans()==4,"Successful paths are never retained");
  }
  npc.moveTo(base.getX()+3.5,121,base.getZ()+2.5);
  try(var survey=HarvestRouteCache.survey(npc)){HarvestRouteCache.plan(npc,target,64);}
  h.assertTrue(HarvestRouteCache.stats(npc).plans()==5,"Moving body invalidates failed routes");
  try(var survey=HarvestRouteCache.survey(npc)){throw new IllegalStateException("scope fixture");}catch(IllegalStateException expected){}
  for(int x=13;x<=20;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(HarvestAccess.reversible(HarvestRouteCache.plan(npc,target,64)),"Exception closes scope: actual work immediately uses the newly opened bridge");
  try(var survey=HarvestRouteCache.survey(npc)){h.assertTrue(HarvestRouteCache.plan(npc,target,64)==null,"Only sensing can briefly defer a previously failed candidate");}
  long sensedAt=l.getGameTime();
  h.runAtTickTime(24,()->{
   h.assertTrue(l.getGameTime()-sensedAt>=HarvestRouteCache.RETRY_TICKS,"Expiry pays ordinary server ticks");
   try(var survey=HarvestRouteCache.survey(npc)){h.assertTrue(HarvestAccess.reversible(HarvestRouteCache.plan(npc,target,64)),"Expired miss sees the genuinely open bridge");}
   h.assertTrue(HarvestRouteCache.stats(npc).plans()==6,"Expired miss is checked again by the native planner");
   npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
