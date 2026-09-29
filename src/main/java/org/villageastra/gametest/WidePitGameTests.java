package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WidePitGameTests {
 @GameTest(template="empty",batch="wide_pit",timeoutTicks=500) public static void residentClimbsOutOfAWideDryPit(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=5;y++)l.setBlock(foot.offset(x,y,z),(y<0||y<3&&(Math.abs(x)>1||Math.abs(z)>1))?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);l.addFreshEntity(npc);
  npc.goalSelector.getAvailableGoals().forEach(g->g.stop());npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var goal=new PitEscapeGoal(npc);h.assertTrue(PitEscapeGoal.escape(npc)!=null,"An open cell inside a 3x3 pit is not an escape corridor");
  // Only bypass the stuck-detection delay; climbing, collision and movement are real entity ticks.
  for(int i=0;i<240;i++)if(goal.canUse()){goal.start();break;}
  boolean[] stopped={false};h.onEachTick(()->{if(npc.getY()>=foot.getY()+3&&npc.onGround()){goal.stop();npc.discard();h.succeed();return;}
   if(!stopped[0]){if(goal.canContinueToUse())goal.tick();else{goal.stop();stopped[0]=true;}}
  });
  h.runAtTickTime(400,()->h.assertTrue(npc.isRemoved(),"Climber failed to reach the ledge: pos="+npc.position()+" foot="+foot+" ground="+npc.onGround()+" navigationDone="+npc.getNavigation().isDone()));
 }
 @GameTest(template="empty",batch="wide_pit",timeoutTicks=100) public static void anOpenCorridorKeepsOrdinaryNavigation(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(6,5,6));
  for(int x=-5;x<=5;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++)l.setBlock(foot.offset(x,y,z),y<0||z!=0&&y<2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);
  h.assertTrue(PitEscapeGoal.escape(npc)==null,"A passage reaching outside the bounded search must remain normal navigation");npc.discard();h.succeed();
 }
}
