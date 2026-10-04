package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
/** Entity-ticking tickets for physical fixtures extending outside their template. */
final class PhysicalFixtureChunks {
 private PhysicalFixtureChunks(){}
 static List<ChunkPos> force(ServerLevel l,BlockPos center,int minX,int maxX,int minZ,int maxZ){
  var added=new ArrayList<ChunkPos>();
  for(int x=(center.getX()+minX)>>4;x<=(center.getX()+maxX)>>4;x++)for(int z=(center.getZ()+minZ)>>4;z<=(center.getZ()+maxZ)>>4;z++){
   var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);added.add(cp);}
  }
  return added;
 }
 static void release(ServerLevel l,List<ChunkPos> added){for(var cp:added)l.setChunkForced(cp.x,cp.z,false);}
}
