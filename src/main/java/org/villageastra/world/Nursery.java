package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-074: the nursery of a sawmill — the fenced plot behind it where the forester planted and felled. AD-131 replaced it by the forester's
 *  hut and its wild felling; only the frozen designs of AD-119 (Legacy117DistinctArchitecture) still draw its fence and saplings from here. */
public final class Nursery {
 /** Local cells of the lot: the lodge stands in front, the plot behind it; trees stand four blocks apart. */
 public static final int FRONT=7,STEP=4,MAX_COLS=2,MAX_ROWS=3,DEFAULT_COLS=2,DEFAULT_ROWS=2;
 private Nursery(){}
 public record Size(int cols,int rows){
  public Size{if(cols<1||cols>MAX_COLS||rows<1||rows>MAX_ROWS)throw new IllegalArgumentException("Invalid nursery size");}
  public int trees(){return cols*rows;}
 }
 public static final Size DEFAULT=new Size(DEFAULT_COLS,DEFAULT_ROWS);
 /** Where a tree of this plot stands, in local cells of the lot. */
 public static List<BlockPos> saplings(Size size){
  var out=new ArrayList<BlockPos>();
  for(int col=0;col<size.cols();col++)for(int row=0;row<size.rows();row++)out.add(new BlockPos(1+STEP*col,1,FRONT+2+STEP*row));
  return List.copyOf(out);
 }
 /** The plot itself in local cells: from the lodge back to the last row, as wide as its columns. */
 public static int[] plot(Size size){
  int x1=size.cols()==1?4:8,z1=FRONT+3+STEP*(size.rows()-1);
  return new int[]{0,FRONT,x1,z1};
 }
 public static boolean inside(Size size,BlockPos local){
  var box=plot(size);
  return local.getX()>=box[0]&&local.getX()<=box[2]&&local.getZ()>=box[1]&&local.getZ()<=box[3];
 }
 /** Cells of the fence around the plot, with its gate in the middle of the lodge side. */
 public static Map<BlockPos,BlockState> fence(Size size){
  var box=plot(size);var out=new LinkedHashMap<BlockPos,BlockState>();
  for(int x=box[0];x<=box[2];x++)for(int z=box[1];z<=box[3];z++){
   if(x!=box[0]&&x!=box[2]&&z!=box[1]&&z!=box[3])continue;
   out.put(new BlockPos(x,1,z),Blocks.OAK_FENCE.defaultBlockState());
  }
  out.put(new BlockPos((box[0]+box[2])/2,1,box[1]),Blocks.OAK_FENCE_GATE.defaultBlockState());
  // World generation suppresses neighbor updates: encode connections in the blueprint itself.
  out.replaceAll((pos,state)->{if(!(state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock))return state;for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL){var neighbor=out.get(pos.relative(dir));if(neighbor!=null)state=state.setValue(switch(dir){case NORTH->net.minecraft.world.level.block.FenceBlock.NORTH;case SOUTH->net.minecraft.world.level.block.FenceBlock.SOUTH;case EAST->net.minecraft.world.level.block.FenceBlock.EAST;default->net.minecraft.world.level.block.FenceBlock.WEST;},true);}return state;});
  return out;
 }
}
