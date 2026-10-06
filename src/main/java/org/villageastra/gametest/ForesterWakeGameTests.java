package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** A read-only terrain snapshot of the naturally observed bedroom; only the test world is populated. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForesterWakeGameTests {
 @GameTest(template="empty",batch="forester_wake",timeoutTicks=2400)
 public static void aForesterLeavesTheObservedBedroomAfterWaking(GameTestHelper h){room(h,false);}
 @GameTest(template="empty",batch="forester_wake",timeoutTicks=2400)
 public static void aForesterAlreadyAtTheBedEdgeReachesTheVillagePath(GameTestHelper h){room(h,true);}
 private static void room(GameTestHelper h,boolean edge){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(edge?151552:147456),90,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();
  var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_observed_bed_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-8)>>4;x<=(base.getX()+8)>>4;x++)for(int z=(base.getZ()-8)>>4;z<=(base.getZ()+8)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-8)>>4)-2;x<=((base.getX()+8)>>4)+2;x++)for(int z=((base.getZ()-8)>>4)-2;z<=((base.getZ()+8)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-8,-3,-8),base.offset(8,8,8)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=ForesterWakeGameTests.class.getResourceAsStream("/data/villageastra/fixtures/forester_bed_20261005.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(Exception ex){throw new RuntimeException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+(edge?.2065874844:.5),base.getY()+.5625,base.getZ()+(edge?-.5379284114:.5));npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->!(g instanceof BedExitGoal)&&!(g instanceof ResidentDoorGoal));npc.targetSelector.removeAllGoals(g->true);var destination=base.offset(-1,0,-5);
  h.assertTrue(BedExitGoal.landing(npc)!=null,"The observed bed has a clear landing");
  npc.goalSelector.addGoal(5,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0)npc.getNavigation().moveTo(npc.routeTo(destination,0,NaturalSupplyGoal.ROUTE_RANGE),.8);}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Observed room actually ticks")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Awake resident registered"));
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.tickCount<1200,"Observed bed exit stalled: body="+npc.position()+" goals="+npc.runningGoals()+" landing="+BedExitGoal.landing(npc)+" ground="+npc.onGround()+" collision="+npc.horizontalCollision+" control="+npc.getMoveControl().getWantedX()+","+npc.getMoveControl().getWantedY()+","+npc.getMoveControl().getWantedZ());
   if(npc.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination))<.5&&npc.onGround()){
    h.assertTrue(l.getBlockState(base).is(Blocks.WHITE_BED)&&l.getBlockState(base.offset(-1,2,-2)).is(Blocks.STRIPPED_DARK_OAK_LOG),"Real bed and door lintel are intact");npc.discard();for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();
   }
  });
 }
}
