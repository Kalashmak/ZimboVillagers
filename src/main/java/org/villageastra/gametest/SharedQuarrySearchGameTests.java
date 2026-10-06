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
