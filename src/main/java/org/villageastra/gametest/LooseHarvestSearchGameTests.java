package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Native sensing fixture; bridge placement is prepared and no physical delivery is claimed. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LooseHarvestSearchGameTests {
 @GameTest(template="empty",batch="loose_harvest_search",timeoutTicks=200)
 public static void looseClusterSharesSearchAndFreshWorkingQuerySeesBridge(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+851968,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-6)>>4;x<=(base.getX()+36)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+6)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0&&(x<=12||x>=21)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var targets=new LinkedHashSet<BlockPos>();for(int x=24;x<=26;x++)for(int z=-1;z<=1;z++){var p=base.offset(x,0,z);l.setBlock(p,Blocks.SAND.defaultBlockState(),2);targets.add(p);}
  var origin=base.offset(25,0,0);var npc=VillageAstra.RESIDENT.get().create(l);npc.setNoAi(true);npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   long individual,grouped;
   try(var survey=HarvestRouteCache.survey(npc)){
    for(var p:targets)h.assertTrue(HarvestAccess.find(npc,p,128)==null,"Individual routes must reject the unsupported gap");
    individual=HarvestRouteCache.stats(npc).plans();h.assertTrue(individual>=9,"Distinct blocks required repeated native searches");
    long before=individual;h.assertTrue(LooseHarvestAccess.find(npc,origin,Set.of(Items.SAND),Set.of(),128,4)==null,"Combined sensing must still reject the gap");
    grouped=HarvestRouteCache.stats(npc).plans()-before;h.assertTrue(grouped>0&&grouped<=2,"One neighbourhood requires at most two grouped native queries");
   }
   for(int x=13;x<=20;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   var choice=LooseHarvestAccess.find(npc,origin,Set.of(Items.SAND),Set.of(),128,4);
   h.assertTrue(choice!=null&&targets.contains(choice.target())&&HarvestAccess.standing(l,choice.stand(),choice.target())&&HarvestAccess.visible(npc,choice.stand(),choice.target()),"Fresh working query must see bridge and choose a real safe block");
   var route=npc.routeTo(choice.stand(),0,128);h.assertTrue(HarvestAccess.survivesExtraction(route,choice.target()),"Selected platform remains reversible after its selected block is removed");
   h.assertTrue(LooseHarvestAccess.find(npc,origin,Set.of(Items.CLAY_BALL),Set.of(),128,4)==null,"Unneeded sand cannot start an order");
   h.assertTrue(LooseHarvestAccess.find(npc,origin,Set.of(Items.SAND),targets,128,4)==null,"Reserved targets cannot be selected");
   h.assertTrue(targets.stream().allMatch(p->l.getBlockState(p).is(Blocks.SAND)),"Sensing removes no material");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_LOOSE_SEARCH VERIFIED individual={} grouped={} blocks=9 freshBridge=true",individual,grouped);
  }finally{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}
  h.succeed();
 }
}
