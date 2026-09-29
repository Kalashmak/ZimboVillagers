package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.*;
/** AD-138 (owner 2026-09-22: "a feeder must be added to the mod as its own block"): the wooden trough of a pen. What it holds is its FEED
 *  state, 0..4, one step for two real items of the pen's feed — no block entity (a block entity is a conflict to every survey and every
 *  construction step, BuildingOrders), so a builder, a repair and a move treat it as any block of the yard. It breaks by hand and drops itself. */
public final class FeederBlock extends Block {
 /** Steps of feed in the trough: two items each, full at four. */
 public static final IntegerProperty FEED=IntegerProperty.create("feed",0,4);
 public static final int ITEMS_PER_STEP=2,MAX=4;
 // The trough: a plank bottom and four walls, eight pixels high, open in the middle.
 private static final VoxelShape SHAPE=Shapes.join(Block.box(0,0,0,16,8,16),Block.box(2,2,2,14,8,14),BooleanOp.ONLY_FIRST);
 public FeederBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK).ignitedByLava());
  registerDefaultState(stateDefinition.any().setValue(FEED,0));}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FEED);}
 @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
 /** As high as a fence to walk into (probe livestock-14: standing on a low trough by the fence, the herd jumped out of the pen). */
 private static final VoxelShape COLLISION=Block.box(0,0,0,16,24,16);
 @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return COLLISION;}
 /** Items of feed a trough in this state holds. */
 public static int items(BlockState s){return s.getBlock() instanceof FeederBlock?s.getValue(FEED)*ITEMS_PER_STEP:0;}
}
