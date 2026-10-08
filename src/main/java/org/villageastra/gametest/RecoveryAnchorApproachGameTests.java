package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RecoveryAnchorApproachGameTests {
 @GameTest(template="empty",batch="recovery_anchor_approach",timeoutTicks=2200)
 public static void pitRecoveryPhysicallyReachesTheObservedAnchorWithoutCuttingOutItsNativeJump(GameTestHelper h)throws Exception{
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+589824,160,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-12,26,-8,20);
  for(var p:BlockPos.betweenClosed(base.offset(-10,-6,-6),base.offset(24,8,17)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=RecoveryAnchorApproachGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh5_recovery_step_20261009.json")){
   var cells=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:cells){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.952713980458,base.getY(),base.getZ()+.114878240065);npc.setOnGround(true);h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Observed cave body fits its real starting position");
  var remember=Class.forName("org.villageastra.world.RecoveryLedges").getDeclaredMethod("remember",ResidentEntity.class,BlockPos.class);remember.setAccessible(true);
  for(var p:List.of(base.offset(1,1,1),base.offset(21,0,8),base.offset(20,0,7),base.offset(19,0,7),base.offset(18,0,8),base.offset(20,0,3),base.offset(19,0,4),base.offset(18,0,3)))remember.invoke(null,npc,p);
  var planner=ResidentEntity.class.getDeclaredMethod("routeToRecoveryAnchor",BlockPos.class);planner.setAccessible(true);var target=base.offset(-5,0,1);var first=(net.minecraft.world.level.pathfinder.Path)planner.invoke(npc,target);h.assertTrue(first!=null&&first.canReach(),"Copied anchor has a complete native route");
  var pit=new PitEscapeGoal(npc);
  for(var named:Map.of("anchor",target,"exit",base.offset(-5,1,2)).entrySet()){var f=PitEscapeGoal.class.getDeclaredField(named.getKey());f.setAccessible(true);f.set(pit,named.getValue());}
  npc.onlyGoals(g->false,0,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void start(){pit.start();}public void tick(){pit.tick();}public void stop(){pit.stop();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Cave fixture chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Actual moving body registered"));
  boolean[] done={false};Runnable clean=()->{done[0]=true;npc.discard();PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(done[0])return;h.assertTrue(npc.isAlive()&&npc.getHealth()==npc.getMaxHealth(),"Recovery approach preserves health");if(npc.position().multiply(1,0,1).distanceToSqr(Vec3.atBottomCenterOf(target).multiply(1,0,1))>.04||npc.getY()<target.getY()-.1)return;
   h.assertTrue(l.getBlockState(base.offset(0,4,0)).is(Blocks.STONE),"Observed overhang remains intact");clean.run();h.succeed();});
  h.runAtTickTime(2000,()->{if(done[0])return;var why=npc.position()+" native="+npc.getNavigation().getPath();clean.run();throw new GameTestAssertException("Complete recovery path stalls under copied overhang: "+why);});
 }
}
