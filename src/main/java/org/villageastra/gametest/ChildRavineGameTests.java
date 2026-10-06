package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Read-only terrain copied from the natural growth run, with a new child at the observed cell. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ChildRavineGameTests {
 @GameTest(template="empty",batch="child_observed_ravine",timeoutTicks=4800)
 public static void aChildReturnsFromTheObservedRavineToItsActualHomeBed(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+184320,100,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-21)>>4;x<=(base.getX()+12)>>4;x++)for(int z=(base.getZ()-14)>>4;z<=(base.getZ()+21)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-21;x<=12;x++)for(int z=-14;z<=21;z++)for(int y=-6;y<=13;y++)l.setBlock(base.offset(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  try(var in=ChildRavineGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh3_child_ravine_20261006.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(java.io.IOException|com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalStateException(ex);}
  var s=new Settlement(UUID.randomUUID());var home=UUID.randomUUID();s.addBuilding(new Settlement.Building(home,"home",-14,6,1,0,1,"birch"));s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var child=VillageAstra.RESIDENT.get().create(l);var r=new Resident(child.getUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(r,home);child.bind(s.id(),r);child.moveTo(base.getX()+.5,base.getY(),base.getZ()+.5);child.setOnGround(true);
  h.assertTrue(l.noCollision(child,child.getBoundingBox())&&!l.getBlockState(base.below()).getCollisionShape(l,base.below()).isEmpty(),"Observed child cell is supported and clear");
  h.assertTrue(SleepGoal.bed(l,e,r)!=null,"Copied home has the child's actual bed");
  child.onlyGoals(g->g instanceof PitEscapeGoal||g instanceof SafeDescentGoal||g instanceof BedExitGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal,2,new SleepGoal(child,true,()->14000L));child.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Ravine physically ticks")).thenExecute(()->h.assertTrue(l.addFreshEntity(child),"Actual child registered"));
  h.onEachTick(()->{
   h.assertTrue(child.isAlive()&&child.getHealth()==child.getMaxHealth(),"Home return damaged the child: "+child.position());
   if(child.isSleeping()||"sleeping".equals(child.workStatus())){child.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();}
  });
  h.runAtTickTime(4400,()->h.assertTrue(false,"Child remains below home: "+child.position()+" goals="+child.runningGoals()+" escape="+PitEscapeGoal.escape(child)));
 }
}
