package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import org.villageastra.domain.OrganicLots;
/** One terrain-selected site per 1000-block sector; bounded search, cached without loading chunks. */
public final class SettlementSectors {
 public static final int SIZE=1000;
 public record Plan(UUID id,BlockPos origin,int[] elevations){public Plan{elevations=elevations.clone();}public int[] elevations(){return elevations.clone();}}
 private static final Map<RandomState,Map<Long,Optional<Plan>>> CACHE=new WeakHashMap<>();
 private SettlementSectors(){}
 public static int sector(int block){return Math.floorDiv(block,SIZE);}
 public static Plan forServer(long seed,int chunkX,int chunkZ){
  var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.overworld()==null)return null;
  var l=server.overworld();return plan(l.getChunkSource().getGenerator(),l.getChunkSource().randomState(),seed,l,sector(chunkX*16),sector(chunkZ*16));
 }
 public static synchronized Plan plan(ChunkGenerator generator,RandomState state,long seed,LevelHeightAccessor world,int sx,int sz){
  var entries=CACHE.computeIfAbsent(state,k->new LinkedHashMap<>());long key=net.minecraft.world.level.ChunkPos.asLong(sx,sz);
  if(entries.containsKey(key))return entries.get(key).orElse(null);
  var random=new Random(seed^((long)sx*341873128712L)^((long)sz*132897987541L));Plan found=null;
  for(int attempt=0;attempt<81&&found==null;attempt++){
   // Visit every interior 100-block tile, with a seeded permutation and modest jitter.
   int tile=(attempt*37+40)%81;
   int x=Math.floorDiv(sx*SIZE+100+(tile%9)*100+random.nextInt(33)-16,16)*16;
   int z=Math.floorDiv(sz*SIZE+100+(tile/9)*100+random.nextInt(33)-16,16)*16;
   var id=UUID.nameUUIDFromBytes((seed+":"+x+":"+z).getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var lots=OrganicLots.tryBuildings(id,OrganicLots.CURRENT);if(lots.isEmpty())continue;
   int[] heights=new int[7];int index=0;boolean valid=true;
   for(var b:lots.get()){
    int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE,door=Integer.MIN_VALUE;int west=OrganicLots.west(b.type()),width=OrganicLots.width(b.type()),depth=OrganicLots.depth(b.type());
    int slope=b.type().equals("farm")||b.type().equals("forester")?2:3;
    for(int dx:new int[]{0,width/2,width-1}){for(int dz:new int[]{0,depth/2,depth-1}){
     // AD-104: the lot starts west blocks from the building (the farm's field reaches one column past its farmhouse); the middle of
     // the farm's 9-wide lot is the middle of its 7-wide farmhouse, where the door is.
     int y=generator.getFirstOccupiedHeight(x+b.x()+west+dx,z+b.z()+dz,Heightmap.Types.WORLD_SURFACE_WG,world,state);low=Math.min(low,y);high=Math.max(high,y);
     if(dx==width/2&&dz==0)door=y;
     if(high-low>slope||low<generator.getSeaLevel()||high+18>=world.getMaxBuildHeight()){valid=false;break;}
    }if(!valid)break;}
    // AD-058: the floor takes the place of the ground block in front of the door (the generator's first occupied height), so the house is entered on foot.
    if(!valid)break;int floor=door;heights[index++]=floor;if(Math.abs(floor-heights[0])>16){valid=false;break;}
   }
   if(valid){var origin=new BlockPos(x,heights[0],z);for(int i=1;i<7;i++)heights[i]-=origin.getY();heights[0]=0;found=new Plan(id,origin,heights);}
  }
  if(entries.size()>=256)entries.remove(entries.keySet().iterator().next());entries.put(key,Optional.ofNullable(found));return found;
 }
}
