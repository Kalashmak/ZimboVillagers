package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** The weapon has to see through the actual tower, including its lamp, crenels and roof. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BallistaMountGameTests {
 @GameTest(template="empty",timeoutTicks=200,batch="ballista_mount") public static void ballistaMountSeesTargetFromRealTower(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(20,4,20));var s=new Settlement(UUID.randomUUID());
  var added=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(center.getX()-14)>>4;x<=(center.getX()+18)>>4;x++)for(int z=(center.getZ()-14)>>4;z<=(center.getZ()+18)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);added.add(cp);}l.getChunk(x,z);
  }
  // Load all four firing lanes before adding entities; block access alone does not make them entity-ticking.
  for(int x=-14;x<=18;x++)for(int z=-14;z<=18;z++)for(int y=1;y<=16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);
  h.succeedWhen(()->{
  for(int turn=0;turn<4;turn++){
   var tower=new Settlement.Building(UUID.randomUUID(),Walls.TOWER,0,0,0,turn);
   h.assertTrue(l.isPositionEntityTicking(BuildingPlacement.at(e,tower,12,1,1)),"Waiting for target chunk at turn "+turn);
  }
  for(int turn=0;turn<4;turn++){var tower=new Settlement.Building(UUID.randomUUID(),Walls.TOWER,0,0,0,turn);
  BuildingPlacement.layout(e,tower,Walls.TOWER).forEach((p,state)->l.setBlock(p,state,2));var from=Ballistas.mount(e,tower);
  h.assertTrue(l.getBlockState(BlockPos.containing(from)).getCollisionShape(l,BlockPos.containing(from)).isEmpty(),"Bolt starts in free space at turn "+turn);
  var at=BuildingPlacement.at(e,tower,12,1,1);var target=EntityType.ZOMBIE.create(l);target.setNoAi(true);target.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(target);
  try{h.assertTrue(Ballistas.target(l,from)==target,"The real tower must not enclose the bolt origin: turn="+turn+" loaded="+(l.getEntity(target.getUUID())==target)+" target="+at+" hit="+l.clip(new net.minecraft.world.level.ClipContext(from,target.getEyePosition(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,null)).getBlockPos());}
  finally{target.discard();}}
  for(var cp:added)l.setChunkForced(cp.x,cp.z,false);
  });
 }
}
