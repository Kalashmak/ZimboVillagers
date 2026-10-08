package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SandBankRouteGameTests {
 @GameTest(template="empty",batch="sand_bank_route",timeoutTicks=2400)
 public static void carrierWalksPastObservedBankCorner(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+126976,90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-9)>>4;x<=(base.getX()+9)>>4;x++)for(int z=(base.getZ()-9)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(var p:BlockPos.betweenClosed(base.offset(-9,-6,-9),base.offset(9,10,10)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=SandBankRouteGameTests.class.getResourceAsStream("/data/villageastra/fixtures/sand_bank_route_20261008.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(Exception ex){throw new RuntimeException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.6772150745746,base.getY(),base.getZ()+.6895097492456);npc.setOnGround(true);npc.onlyGoals(g->false,6,new net.minecraft.world.entity.ai.goal.Goal(){public boolean canUse(){return false;}});npc.targetSelector.removeAllGoals(g->true);var target=base.offset(2,1,-5);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Carrier chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Carrier added"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Return must avoid injury");
   if(npc.tickCount%20==0){var path=npc.routeTo(target,0,64);if(path!=null&&path.canReach())npc.getNavigation().moveTo(path,.8);}
   if(npc.distanceToSqr(target.getCenter())<2){System.out.println("SAND_BANK_ROUTE bodyTicks="+npc.tickCount);npc.discard();for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();return;}
   h.assertTrue(npc.tickCount<2000,"Observed corner blocks the physical body: "+npc.position()+" path="+npc.getNavigation().getPath());
  });
 }
}
