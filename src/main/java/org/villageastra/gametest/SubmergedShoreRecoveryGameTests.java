package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Copied observed shore; native movement and recovery goals, no live-world edits. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SubmergedShoreRecoveryGameTests {
 @GameTest(template="empty",batch="submerged_shore_recovery",timeoutTicks=6000)
 public static void submergedWorkerEscapesFarmOverhangBeforeDrowning(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="submerged_shore_recovery",timeoutTicks=6000)
 public static void submergedChildEscapesFarmOverhangBeforeDrowning(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean child){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(child?294912:262144),100,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_submerged_shore_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-28)>>4;x<=(base.getX()+28)>>4;x++)for(int z=(base.getZ()-28)>>4;z<=(base.getZ()+28)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(var p:BlockPos.betweenClosed(base.offset(-26,-4,-26),base.offset(26,12,26)))l.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  try(var in=SubmergedShoreRecoveryGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh4_submerged_field_20261007.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(Exception ex){throw new IllegalStateException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);if(child)npc.refreshLife(new org.villageastra.domain.Resident(npc.getUUID(),org.villageastra.domain.Resident.Life.CHILD,false,null,null,-1));npc.moveTo(base.getX()+.300000011921,base.getY()+.2,base.getZ()+.49);npc.setOnGround(false);var target=base.offset(15,2,-1);
  h.assertTrue(ShoreEscapeGoal.dryBank(npc,target),"Target is supported dry shore in the actual copied terrain");
  npc.onlyGoals(g->g instanceof PitEscapeGoal||g instanceof ShoreEscapeGoal||g instanceof FloatGoal||g instanceof ResidentDoorGoal||g instanceof SafeDescentGoal,5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){} // The observed body had stopped against the submerged overhang.
   public void stop(){npc.getNavigation().stop();}
  });npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Shore physically ticks")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Worker registered"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Bank recovery preserves health");if(!npc.onGround()||npc.isInWaterOrBubble()||npc.getAirSupply()<200)return;h.assertTrue(l.getBlockState(base).is(net.minecraft.world.level.block.Blocks.WATER)&&l.getBlockState(base.above(2)).is(net.minecraft.world.level.block.Blocks.FARMLAND),"Water and overhanging terrain remain intact");com.mojang.logging.LogUtils.getLogger().info("SUBMERGED_SHORE_PASS child={} bodyTicks={} position={}",child,npc.tickCount,npc.position());npc.discard();for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();});
  h.runAtTickTime(5600,()->h.assertTrue(false,"Worker still below the observed farm overhang: "+npc.position()+" goals="+npc.runningGoals()+" escape="+PitEscapeGoal.escape(npc)+" bodyTicks="+npc.tickCount));
 }
}
