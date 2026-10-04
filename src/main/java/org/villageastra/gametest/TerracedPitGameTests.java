package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TerracedPitGameTests {
 @GameTest(template="empty",batch="terraced_pit",timeoutTicks=4000)
 public static void aStalledCarrierWalksTheConnectedStepsBeforeClimbingOut(GameTestHelper h){var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+90112,160,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+18)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+3)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(cp.x,cp.z,true);held.add(cp);}l.getChunk(x,z);}
  for(int x=-3;x<=18;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=11;y++){int floor=x<0||Math.abs(z)>1?10:x>=12?6:x>=9?2:x>=6?1:0;boolean roof=x>=0&&x<9&&y==5;l.setBlock(base.offset(x,y,z),(y<=floor||roof?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);var destination=base.offset(-2,7,0);npc.goalSelector.removeAllGoals(g->!(g instanceof PitEscapeGoal));npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}public void tick(){if(npc.tickCount%20==0&&npc.onGround()){var route=ResourceReturnRoute.plan(npc,destination);if(route!=null)npc.getNavigation().moveTo(route,.8);}}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Resident chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{h.assertTrue(npc.isAlive(),"Recovery remains safe");if(npc.onGround()&&npc.getY()>=base.getY()+7){h.assertTrue(l.getBlockState(base.offset(4,5,0)).is(Blocks.STONE),"Recovery leaves the cave ceiling intact");npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();}});
 }
}
