package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Resident;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ChildPitGameTests {
 @GameTest(template="empty",batch="child_dry_pit",timeoutTicks=1600)
 public static void aChildPhysicallyClimbsOutOfADryPitWithoutChangingTerrain(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO);var foot=new BlockPos(p.getX()+180224,160,p.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(foot.getX()-4)>>4;x<=(foot.getX()+4)>>4;x++)for(int z=(foot.getZ()-4)>>4;z<=(foot.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=8;y++)l.setBlock(foot.offset(x,y,z),(y<0||y<6&&(Math.abs(x)>=2||Math.abs(z)>=2)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var child=VillageAstra.RESIDENT.get().create(l);child.refreshLife(new Resident(child.getUUID(),Resident.Life.CHILD,false,null,null,-1));child.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);child.setOnGround(true);
  h.assertTrue(child.child()&&PitEscapeGoal.escape(child)!=null,"A child must be able to use the same verified dry-pit rim as an adult");
  child.goalSelector.removeAllGoals(g->!(g instanceof PitEscapeGoal));child.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(foot),"Child chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(child),"Actual child registered"));
  h.onEachTick(()->{
   h.assertTrue(child.isAlive()&&child.getHealth()==child.getMaxHealth(),"Recovery must preserve the child's health");
   if(child.onGround()&&child.getY()>=foot.getY()+6){
    for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-1;y<=8;y++)h.assertTrue(l.getBlockState(foot.offset(x,y,z)).is(y<0||y<6&&(Math.abs(x)>=2||Math.abs(z)>=2)?Blocks.STONE:Blocks.AIR),"Recovery changed terrain");
    child.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
   }
  });
  h.runAtTickTime(1500,()->h.assertTrue(false,"Child remained below the rim: "+child.position()+" goals="+child.runningGoals()));
 }
}
