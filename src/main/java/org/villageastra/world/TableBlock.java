package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.*;
import org.villageastra.domain.Furniture;
/** AD-143 (owner 2026-09-23): a table of one wood — a board three pixels thick at the top of the block over square legs. NORTH, EAST, SOUTH,
 *  WEST say which sides join another table (of any wood): joined tables read as one board, with legs only at its outer corners. The board's
 *  top face is sturdy, so a lantern, a candle or a carpet stands on it. Decorative: it holds nothing. */
public final class TableBlock extends Block {
 public static final BooleanProperty NORTH=BlockStateProperties.NORTH,EAST=BlockStateProperties.EAST,SOUTH=BlockStateProperties.SOUTH,WEST=BlockStateProperties.WEST;
 /** The board: y 13..16. */
 public static final int TOP=13;
 private static final VoxelShape BOARD=Block.box(0,TOP,0,16,16,16);
 /** Legs at the corners north-west, north-east, south-east, south-west (Furniture.legs). */
 private static final VoxelShape[] LEG={Block.box(1,0,1,4,TOP,4),Block.box(12,0,1,15,TOP,4),Block.box(12,0,12,15,TOP,15),Block.box(1,0,12,4,TOP,15)};
 private static final VoxelShape[] SHAPES=new VoxelShape[16];
 static{for(int i=0;i<16;i++){var legs=Furniture.legs((i&1)!=0,(i&2)!=0,(i&4)!=0,(i&8)!=0);VoxelShape s=BOARD;for(int k=0;k<4;k++)if(legs[k])s=Shapes.or(s,LEG[k]);SHAPES[i]=s.optimize();}}
 private final String wood;
 public TableBlock(String wood){
  super(BlockBehaviour.Properties.copy(BuiltInRegistries.BLOCK.get(new ResourceLocation(Furniture.planks(wood)))).noOcclusion()
   .isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false).isValidSpawn((s,l,p,type)->false));
  this.wood=wood;registerDefaultState(stateDefinition.any().setValue(NORTH,false).setValue(EAST,false).setValue(SOUTH,false).setValue(WEST,false));
 }
 public String wood(){return wood;}
 public static BooleanProperty side(Direction d){return switch(d){case NORTH->NORTH;case EAST->EAST;case SOUTH->SOUTH;case WEST->WEST;default->throw new IllegalArgumentException(d.toString());};}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(NORTH,EAST,SOUTH,WEST);}
 private static int index(BlockState s){return (s.getValue(NORTH)?1:0)|(s.getValue(EAST)?2:0)|(s.getValue(SOUTH)?4:0)|(s.getValue(WEST)?8:0);}
 /** The state joined to every table beside it. */
 public BlockState joined(BlockGetter l,BlockPos p){var s=defaultBlockState();for(var d:Direction.Plane.HORIZONTAL)s=s.setValue(side(d),l.getBlockState(p.relative(d)).getBlock() instanceof TableBlock);return s;}
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return joined(c.getLevel(),c.getClickedPos());}
 @Override public BlockState updateShape(BlockState s,Direction d,BlockState other,LevelAccessor l,BlockPos p,BlockPos o){
  return d.getAxis().isHorizontal()?s.setValue(side(d),other.getBlock() instanceof TableBlock):s;}
 @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[index(s)];}
 @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES[index(s)];}
 /** The board carries what stands on it: its whole top face is support. */
 @Override public VoxelShape getBlockSupportShape(BlockState s,BlockGetter l,BlockPos p){return SHAPES[index(s)];}
 @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,PathComputationType t){return false;}
 @Override public BlockState rotate(BlockState s,Rotation r){
  var out=s;for(var d:Direction.Plane.HORIZONTAL)out=out.setValue(side(r.rotate(d)),s.getValue(side(d)));return out;}
 @Override public BlockState mirror(BlockState s,Mirror m){
  var out=s;for(var d:Direction.Plane.HORIZONTAL)out=out.setValue(side(m.mirror(d)),s.getValue(side(d)));return out;}
}
