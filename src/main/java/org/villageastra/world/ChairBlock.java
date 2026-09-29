package org.villageastra.world;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.villageastra.domain.Furniture;
/** AD-143 (owner 2026-09-23): a chair of one wood — four legs, a seat half a block high, a backrest behind the sitter. FACING is the way the
 *  sitter looks: set towards the player who places it, so the back stands away from him. A player who uses an empty chair sits on it (an
 *  invisible {@link SeatEntity} carries him; sneaking stands him up); a chair someone sits on refuses; breaking a chair stands its sitter up
 *  and removes the seat. Residents sit through {@link SeatEntity#sit} too (the restaurant's diners, a resident resting at home). */
public final class ChairBlock extends Block {
 public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
 private static final Map<Direction,VoxelShape> SHAPES=new EnumMap<>(Direction.class);
 static{
  // Drawn for a chair facing north (the back at the south): the legs, the seat, the back posts and the rails between them.
  double[][] north={{2,0,2,4,6,4},{12,0,2,14,6,4},{2,6,2,14,8,14},{2,0,12,4,16,14},{12,0,12,14,16,14},{4,8,12,12,15,14}};
  for(var d:Direction.Plane.HORIZONTAL){VoxelShape s=Shapes.empty();for(var b:north)s=Shapes.or(s,box(d,b));SHAPES.put(d,s.optimize());}
 }
 /** A box drawn for the north-facing chair, turned to face d (a quarter turn clockwise per step from north). */
 static VoxelShape box(Direction d,double[] b){
  double x0=b[0],z0=b[2],x1=b[3],z1=b[5];
  int turns=(d.get2DDataValue()-Direction.NORTH.get2DDataValue()+4)%4;
  for(int i=0;i<turns;i++){double nx0=16-z1,nx1=16-z0,nz0=x0,nz1=x1;x0=nx0;x1=nx1;z0=nz0;z1=nz1;}
  return Block.box(x0,b[1],z0,x1,b[4],z1);
 }
 private final String wood;
 public ChairBlock(String wood){
  super(BlockBehaviour.Properties.copy(BuiltInRegistries.BLOCK.get(new ResourceLocation(Furniture.planks(wood)))).noOcclusion()
   .isRedstoneConductor((s,l,p)->false).isSuffocating((s,l,p)->false).isViewBlocking((s,l,p)->false).isValidSpawn((s,l,p,type)->false));
  this.wood=wood;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
 }
 public String wood(){return wood;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
 /** Facing the player who places it: he sits down looking back the way he came. */
 @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
 @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES.get(s.getValue(FACING));}
 @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return SHAPES.get(s.getValue(FACING));}
 /** Residents walk round a chair, never through it. */
 @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,PathComputationType t){return false;}
 @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
 @Override public BlockState mirror(BlockState s,Mirror m){return s.rotate(m.getRotation(s.getValue(FACING)));}
 @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
  if(player.isShiftKeyDown()||player.isPassenger())return InteractionResult.PASS;
  if(l.isClientSide)return InteractionResult.SUCCESS;
  if(player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)>9)return InteractionResult.PASS;
  if(SeatEntity.sit((ServerLevel)l,p,player)==null){player.displayClientMessage(Component.translatable("message.villageastra.chair_taken"),true);return InteractionResult.CONSUME;}
  return InteractionResult.CONSUME;
 }
 /** Broken or replaced: whoever sits stands up and the seat goes. */
 @SuppressWarnings("deprecation")
 @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState now,boolean moving){
  if(!s.is(now.getBlock())&&l instanceof ServerLevel sl)SeatEntity.clear(sl,p);
  super.onRemove(s,l,p,now,moving);
 }
}
