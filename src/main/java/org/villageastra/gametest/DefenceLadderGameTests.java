package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** AD-157 (owner's Defence ladder): a tower archer's shot really carries: an arrow aimed by GuardGoal.aim from a tower top hits a
 *  zombie at its Defence V reach. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DefenceLadderGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aTowerArrowCarriesToTheFarReach(GameTestHelper h){
  var l=h.getLevel();var base=new BlockPos(9000,0,-7000);int far=GuardGoal.towerReach(5)-4;var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int cx=(base.getX()-8)>>4;cx<=(base.getX()+far+8)>>4;cx++)for(int cz=(base.getZ()-8)>>4;cz<=(base.getZ()+8)>>4;cz++){l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  int y=l.getMaxBuildHeight()-24; // Dry sky lane: surrounding generated water must not flow into the cleared flight corridor.
  for(int x=-2;x<=far+2;x++)for(int z=-2;z<=2;z++){l.setBlock(new BlockPos(base.getX()+x,y,base.getZ()+z),Blocks.STONE.defaultBlockState(),2);for(int k=1;k<14;k++)l.setBlock(new BlockPos(base.getX()+x,y+k,base.getZ()+z),Blocks.AIR.defaultBlockState(),2);}
  var zombie=EntityType.ZOMBIE.create(l);zombie.setNoAi(true);zombie.moveTo(base.getX()+far+.5,y+1,base.getZ()+.5,0,0);l.addFreshEntity(zombie);float full=zombie.getHealth();
  var arrow=new Arrow(l,base.getX()+.5,y+9,base.getZ()+.5);
  GuardGoal.aim(arrow,zombie.getX()-arrow.getX(),zombie.getY(0.33)-arrow.getY(),zombie.getZ()-arrow.getZ());l.addFreshEntity(arrow);
  h.succeedWhen(()->{if(zombie.getHealth()<full){zombie.discard();arrow.discard();for(var c:chunks)l.setChunkForced(c.x,c.z,false);return;}
   h.assertTrue(false,"The arrow has not hit the zombie "+far+" blocks off yet: arrow at "+arrow.blockPosition()+" inGround="+arrow.isRemoved());});
 }

 @GameTest(template="empty",timeoutTicks=200) public static void aBallistaBoltThrowsAZombieBackAndHurtsIt(GameTestHelper h){
  var l=h.getLevel();var base=new BlockPos(9000,0,-7100);int far=40;var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int cx=(base.getX()-8)>>4;cx<=(base.getX()+far+8)>>4;cx++)for(int cz=(base.getZ()-8)>>4;cz<=(base.getZ()+8)>>4;cz++){l.setChunkForced(cx,cz,true);l.getChunk(cx,cz);chunks.add(new net.minecraft.world.level.ChunkPos(cx,cz));}
  int y=l.getMaxBuildHeight()-24; // Same dry lane as the bow test; relative launch and target heights are unchanged.
  for(int x=-2;x<=far+6;x++)for(int z=-2;z<=2;z++){l.setBlock(new BlockPos(base.getX()+x,y,base.getZ()+z),Blocks.STONE.defaultBlockState(),2);for(int k=1;k<14;k++)l.setBlock(new BlockPos(base.getX()+x,y+k,base.getZ()+z),Blocks.AIR.defaultBlockState(),2);}
  var zombie=EntityType.ZOMBIE.create(l);zombie.setNoAi(true);zombie.moveTo(base.getX()+far+.5,y+1,base.getZ()+.5,0,0);l.addFreshEntity(zombie);float full=zombie.getHealth();
  var from=new net.minecraft.world.phys.Vec3(base.getX()+.5,y+9,base.getZ()+.5);
  var bolt=new net.minecraft.world.entity.projectile.Arrow[1];
  h.succeedWhen(()->{
   if(bolt[0]==null){var seen=Ballistas.target(l,from);h.assertTrue(seen==zombie,"The ballista sees the zombie "+far+" blocks off: "+seen);bolt[0]=Ballistas.fire(l,from,zombie);com.mojang.logging.LogUtils.getLogger().info("ASTRA_BALLISTA_TEST launch from={} target={} velocity={}",from,zombie.position(),bolt[0].getDeltaMovement());}
   if(zombie.getHealth()<=full-8){zombie.discard();bolt[0].discard();for(var c:chunks)l.setChunkForced(c.x,c.z,false);return;}
   h.assertTrue(false,"The bolt has not struck hard yet: health "+zombie.getHealth()+" bolt at "+bolt[0].blockPosition()+" wet="+bolt[0].isInWater()+" motion="+bolt[0].getDeltaMovement()+" target="+zombie.position()+" loaded="+(l.getEntity(zombie.getUUID())==zombie)+" block="+l.getBlockState(bolt[0].blockPosition()));});
 }
}
