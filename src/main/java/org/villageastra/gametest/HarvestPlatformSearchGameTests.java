package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Real native planning and world geometry; no physical harvest trip is simulated. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HarvestPlatformSearchGameTests {
 @GameTest(template="empty",batch="harvest_platform_search",timeoutTicks=200)
 public static void unreachableHarvestShelvesShareBoundedSearchesAndKeepFirstAccessiblePlatform(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+442368,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-6)>>4;x<=(base.getX()+36)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+6)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0&&(x<=12||x>=21)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);var target=base.offset(25,1,0);l.setBlock(target,Blocks.SAND.defaultBlockState(),2);
  try{
   int eligible=0;for(var offset:HarvestAccess.platformOffsets()){var feet=target.offset(offset);if(HarvestAccess.standing(l,feet,target)&&HarvestAccess.visible(npc,feet,target))eligible++;}
   h.assertTrue(eligible>=16,"The fixture has many genuinely supported visible platforms");
   try(var survey=HarvestRouteCache.survey(npc)){
    h.assertTrue(HarvestAccess.find(npc,target,128)==null,"Native paths must reject every platform across the unsupported gap");
   }
   var stats=HarvestRouteCache.stats(npc);com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HARVEST_PLATFORMS VERIFIED eligible={} nativePlans={} reusedMisses={}",eligible,stats.plans(),stats.hits());
   h.assertTrue(stats.plans()<=4,"At most four native searches for all unreachable platforms; eligible="+eligible+" plans="+stats.plans());
   for(int x=13;x<=20;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   BlockPos expected=null;for(var offset:HarvestAccess.platformOffsets()){var feet=target.offset(offset);if(HarvestAccess.standing(l,feet,target)&&HarvestAccess.visible(npc,feet,target)&&HarvestAccess.survivesExtraction(npc.routeTo(feet,0,128),target)){expected=feet;break;}}
   var actual=HarvestAccess.find(npc,target,128);h.assertTrue(expected!=null&&expected.equals(actual),"Outside sensing sees the real bridge immediately and keeps the original first usable platform");
   h.assertTrue(HarvestAccess.visible(npc,actual,target)&&HarvestAccess.survivesExtraction(npc.routeTo(actual,0,128),target),"Chosen path retains actual LOS and survives extraction");
  }finally{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}
  h.succeed();
 }
}
