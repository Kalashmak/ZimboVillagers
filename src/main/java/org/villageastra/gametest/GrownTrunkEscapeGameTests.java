package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.ResidentEntity;
/** A real trunk grows through an idle body, matching the natural farmer's death obstruction. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GrownTrunkEscapeGameTests {
 @GameTest(template="empty",batch="grown_trunk_escape",timeoutTicks=600)
 public static void idleResidentPhysicallyLeavesAGrownTrunkWithoutRemovingItOrSuppressingDamage(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var foot=new BlockPos(at.getX()+196608,160,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_grown_trunk_fixture",Comparator.naturalOrder());
  for(int x=(foot.getX()-4)>>4;x<=(foot.getX()+4)>>4;x++)for(int z=(foot.getZ()-4)>>4;z<=(foot.getZ()+4)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(var p:BlockPos.betweenClosed(foot.offset(-3,-1,-3),foot.offset(3,5,3)))l.setBlock(p,p.getY()==foot.getY()-1?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.52,foot.getY(),foot.getZ()+.48);npc.setOnGround(true);
  npc.onlyGoals(g->g.getClass().getSimpleName().equals("SolidEscapeGoal")||g.getClass().getSimpleName().equals("FoliageEscapeGoal"),5,new Goal(){{setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}});npc.targetSelector.removeAllGoals(g->true);
  boolean[] grown={false};h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Physical worker chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Idle worker loaded")).thenIdle(20).thenExecute(()->{for(int y=0;y<4;y++)l.setBlock(foot.above(y),Blocks.BIRCH_LOG.defaultBlockState(),3);grown[0]=true;h.assertTrue(npc.isInWall(),"Observed birch trunk intersects the real body's head");});
  h.onEachTick(()->{l.resetEmptyTime();h.assertTrue(npc.isAlive(),"Idle resident suffocated after a trunk grew through its body");if(!grown[0]||npc.tickCount<30||npc.isInWall()||!npc.onGround()||!l.noCollision(npc,npc.getBoundingBox()))return;
   for(int y=0;y<4;y++)h.assertTrue(l.getBlockState(foot.above(y)).is(Blocks.BIRCH_LOG),"Recovery must not remove or harvest the trunk");h.assertTrue(l.getBlockState(foot.below()).is(Blocks.DIRT),"Supporting ground remains intact");h.assertTrue(npc.getHealth()>=npc.getMaxHealth()-2,"Physical recovery must be prompt, without disabling suffocation damage");npc.discard();for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();});
 }
}
