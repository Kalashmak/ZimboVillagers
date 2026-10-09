package org.villageastra.gametest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaveExitSafetyGameTests {
 @GameTest(template="empty",batch="cave_exit_safety",timeoutTicks=200)
 public static void escapeExcavationPreservesFluidsFallingBlocksOresBuildingsAndUnloadedChunks(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+2621440,160,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-8,8,-8,8);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+3.5,160,base.getZ()+.5);
  for(var p:BlockPos.betweenClosed(base.offset(-2,-1,-2),base.offset(2,3,2)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  l.setBlock(base,Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(CaveExitPlan.diggable(npc,base),"Plain natural rock can be slowly cleared");
  h.assertTrue(MinerSpeed.breakTicks(l.getBlockState(base),l,base,net.minecraft.world.item.ItemStack.EMPTY)==150,"Bare-hand stone excavation pays the full vanilla 150 ticks");
  l.setBlock(base.east(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!CaveExitPlan.diggable(npc,base),"A water boundary cannot be opened");l.setBlock(base.east(),Blocks.AIR.defaultBlockState(),2);
  l.setBlock(base.above(),Blocks.GRAVEL.defaultBlockState(),2);h.assertTrue(!CaveExitPlan.diggable(npc,base),"Gravel above the work cannot be undermined");l.setBlock(base.above(),Blocks.AIR.defaultBlockState(),2);
  for(var forbidden:java.util.List.of(Blocks.BEDROCK,Blocks.CHEST,Blocks.OAK_PLANKS,Blocks.IRON_ORE,Blocks.GRAVEL)){
   l.setBlock(base,forbidden.defaultBlockState(),2);h.assertTrue(!CaveExitPlan.diggable(npc,base),"Escape does not destroy "+forbidden);
  }
  l.setBlock(base,Blocks.STONE.defaultBlockState(),2);
  l.setBlock(base.offset(-3,0,0),Blocks.STONE.defaultBlockState(),2);h.assertTrue(CaveExitPlan.diggable(npc,base.offset(-3,0,0)),"The buffer test starts with diggable real stone");
  var s=new Settlement(UUID.randomUUID());var entry=new SettlementData.Entry(s,l.dimension().location().toString(),base.below());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",0,0,0));SettlementData.get(l.getServer()).add(entry);
  try{
   h.assertTrue(!CaveExitPlan.diggable(npc,base),"Even natural stone inside a registered lot remains protected");
   h.assertTrue(!CaveExitPlan.diggable(npc,base.offset(-3,0,0)),"The three-block buffer is preserved");
   var unloaded=base.offset(512,0,0);h.assertTrue(!l.hasChunkAt(unloaded),"Remote safety fixture starts unloaded");h.assertTrue(!CaveExitPlan.diggable(npc,unloaded)&&!l.hasChunkAt(unloaded),"Safety query never loads remote terrain");
   h.assertTrue(l.getBlockState(base).is(Blocks.STONE),"Safety checks perform no excavation");
  }finally{npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);}
  h.succeed();
 }
}
