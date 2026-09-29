package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-056: the cartographer writes whole columns as block states — through the crown to the real ground and sixteen layers below it, on one floor per chunk. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AtlasColumnGameTests {
 /** Decoded column of one cell: its top y and every state id from there down. */
 private record Column(int top,int[] states){int bottom(){return top-states.length+1;}}
 private static Column column(CompoundTag entry,ChunkPos chunk,BlockPos at){
  int cell=(at.getX()-chunk.getMinBlockX())*16+(at.getZ()-chunk.getMinBlockZ());
  var index=entry.getIntArray("index");var data=entry.getIntArray("columns");int from=index[cell],runs=data[from];
  var states=new ArrayList<Integer>();
  for(int r=0;r<runs;r++)for(int i=0;i<data[from+2+r*2];i++)states.add(data[from+1+r*2]);
  return new Column(entry.getIntArray("heights")[cell],states.stream().mapToInt(Integer::intValue).toArray());
 }
 private static void openSky(net.minecraft.server.level.ServerLevel l,BlockPos from){
  int top=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,from.getX(),from.getZ());
  for(int y=from.getY();y<top;y++)l.setBlock(new BlockPos(from.getX(),y,from.getZ()),Blocks.AIR.defaultBlockState(),2);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void columnsKeepStatesAndReachUnderTheGround(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/cartographer"),"cartographer",0,0,0);s.addBuilding(office);
  for(int x=-4;x<12;x++)for(int z=-4;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chest=LogisticsRoutes.chest(l,e,office);chest.setItem(0,new ItemStack(Items.PAPER,1));
  // A tree: three logs under one leaf. A stair turned to the east beside it.
  var trunk=center.offset(3,1,3);
  for(int y=0;y<3;y++)l.setBlock(trunk.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
  var crown=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);l.setBlock(trunk.above(3),crown,2);
  var stair=Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST);var stairAt=((trunk.getX()+2)>>4)==(trunk.getX()>>4)?trunk.offset(2,0,0):trunk.offset(-2,0,0);l.setBlock(stairAt,stair,2);
  // Test structures stand deep under the natural surface: the two examined cells get an open shaft to the sky, as they would have on the surface.
  openSky(l,trunk.above(4));openSky(l,stairAt.above());
  var chunk=new ChunkPos(trunk);
  h.assertTrue(Atlas.survey(l,e,office,UUID.randomUUID(),chunk,100),"The chunk is surveyed for its paper");
  CompoundTag entry=null;for(var raw:Atlas.inspect(l,s.id()).getList("chunks",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getLong("pos")==chunk.toLong())entry=(CompoundTag)raw;
  h.assertTrue(entry!=null&&entry.getBoolean("states"),"The survey is stored as block states");
  var tree=column(entry,chunk,trunk);
  h.assertTrue(tree.top()==trunk.getY()+3&&tree.states()[0]==Block.getId(crown),"The column starts at the crown: top="+tree.top()+" crown="+trunk.above(3).toShortString());
  h.assertTrue(tree.states()[1]==Block.getId(Blocks.OAK_LOG.defaultBlockState())&&tree.states()[3]==Block.getId(Blocks.OAK_LOG.defaultBlockState()),"The trunk under the crown is written");
  h.assertTrue(tree.states()[4]==Block.getId(Blocks.STONE.defaultBlockState()),"The ground under the tree is written");
  int ground=center.getY();
  h.assertTrue(tree.bottom()<=Math.max(l.getMinBuildHeight(),ground-Atlas.BELOW_GROUND),"The column reaches sixteen layers under the ground: bottom="+tree.bottom()+" ground="+ground);
  var steps=column(entry,chunk,stairAt);
  h.assertTrue(steps.states()[0]==Block.getId(stair),"A stair keeps the way it faces on the map");
  // One floor for the whole chunk: every column of it ends on the same level unless the column would be deeper than the limit.
  var bottoms=new TreeSet<Integer>();
  for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){var c=column(entry,chunk,new BlockPos(chunk.getMinBlockX()+dx,0,chunk.getMinBlockZ()+dz));if(c.states().length<Atlas.MAX_COLUMN)bottoms.add(c.bottom());}
  h.assertTrue(bottoms.size()==1,"Every column of the chunk ends on one floor: "+bottoms);
  SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
