package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FlowingShoreGameTests {
 private static final String[] LAYERS={"###########/###########/###########/###########/###########/###########/###########/###########/###########/###########/###########","###########/###########/#.#########/###########/###########/###########/###########/###########/###########/###########/###########","###########/.##########/...########/##..#######/####18#####/###########/###########/###########/###########/###########/###########","###########/..#########/....#######/#....######/###..3#####/#####21####/######8####/###########/###########/###########/###########","###########/..#########/....#######/......#####/###....####/####...####/######21###/#######8###/###########/###########/###########","###########/.##########/...########/.....######/#.....#####/###.....###/#####....##/#######21##/########8##/###########/###########","#...#######/###########/..#########/....#######/##....#####/###....####/####.....##/######....#/########21#/#########8#/###########","#....######/###########/.##########/#..########/###..######/####...####/#####...###/######....#/#######..../#########8./###########","#....######/###########/###########/..#########/###.#######/####..#####/#####..####/######...##/######....#/########.1./#########8."};
 @GameTest(template="empty",batch="shore_flow_cycle",timeoutTicks=3200)
 public static void returningSwimmerLeavesRepeatedShortWaterAndDryHops(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+167936,150,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-3,13,-3,13);
  for(int x=-3;x<=13;x++)for(int z=-3;z<=13;z++)for(int y=-1;y<=10;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int y=0;y<LAYERS.length;y++){var rows=LAYERS[y].split("/");for(int z=0;z<11;z++)for(int x=0;x<11;x++){char c=rows[z].charAt(x);l.setBlock(base.offset(x,y,z),c=='.'?Blocks.AIR.defaultBlockState():c=='#'?Blocks.STONE.defaultBlockState():Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,c-'0'),2);}}
  // Feed the captured flowing cascade at its upper boundary; the lower geometry is unchanged.
  l.setBlock(base.offset(9,8,10),Blocks.WATER.defaultBlockState(),3);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+4.3,base.getY()+2.1,base.getZ()+4.5);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(0,new FloatGoal(npc));npc.goalSelector.addGoal(1,new ShoreEscapeGoal(npc));
  var target=base.offset(8,5,6);
  npc.goalSelector.addGoal(5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0&&(npc.onGround()||npc.isInWaterOrBubble()))npc.getNavigation().moveTo(ResourceReturnRoute.plan(npc,target),.8);}public void stop(){npc.getNavigation().stop();}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Flowing cave ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();PhysicalFixtureChunks.release(l,held);};boolean[] recovered={false};
  h.onEachTick(()->{if(npc.runningGoals().contains("ShoreEscapeGoal"))recovered[0]=true;if(recovered[0]&&npc.onGround()&&!npc.isInWaterOrBubble()&&!npc.runningGoals().contains("ShoreEscapeGoal")){h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Physical bank recovery preserves health");clean.run();h.succeed();}});
  h.runAtTickTime(3000,()->{String why="Flowing-water cycle continues: "+npc.position().subtract(base.getX(),base.getY(),base.getZ())+" bodyTicks="+npc.tickCount+" recovery="+recovered[0];clean.run();h.assertTrue(false,why);});
 }
}
