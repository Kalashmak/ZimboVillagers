package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MinerWakeGameTests {
 @GameTest(template="empty",batch="miner_wake",timeoutTicks=2400)
 public static void aMinerLeavesTheActualFresh3BedroomAfterWaking(GameTestHelper h){room(h,false);}
 @GameTest(template="empty",batch="miner_wake",timeoutTicks=2400)
 public static void aMinerBetweenTheObservedBedsReachesTheVillagePath(GameTestHelper h){room(h,true);}
 private static void room(GameTestHelper h,boolean floor){
  var l=h.getLevel();var base=new BlockPos(-524288-2411,95,-524288-2370+(floor?4096:0));var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_fresh3_bed_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-12)>>4;x<=(base.getX()+12)>>4;x++)for(int z=(base.getZ()-12)>>4;z<=(base.getZ()+12)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-12)>>4)-2;x<=((base.getX()+12)>>4)+2;x++)for(int z=((base.getZ()-12)>>4)-2;z<=((base.getZ()+12)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-12,-3,-12),base.offset(12,9,12)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=MinerWakeGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh3_miner_bed_20261006.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();for(var raw:cells){var c=raw.getAsJsonArray();var s=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),s,2);}
  }catch(Exception ex){throw new RuntimeException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+(floor?.3641279333:1.5),base.getY()+(floor?0:.5625),base.getZ()+(floor?.5360545765:1.5));npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof BedExitGoal)&&!(g instanceof ResidentDoorGoal));npc.targetSelector.removeAllGoals(g->true);var destination=base.north(5);
  h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Actual starting body fits the observed room");
  h.assertTrue(!l.getBlockState(destination.below()).getCollisionShape(l,destination.below()).isEmpty(),"Destination is the actual village path with physical support, including its lower top");
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.routeTo(destination,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Bedroom physically ticks")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Awake body registered"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.tickCount<1800,"Observed bedroom exit stalled: "+npc.position()+" goals="+npc.runningGoals()+" landing="+BedExitGoal.landing(npc));h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"No damage leaving the bedroom");
   if(npc.onGround()&&npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination))<.5){npc.discard();for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();}
  });
 }
}
