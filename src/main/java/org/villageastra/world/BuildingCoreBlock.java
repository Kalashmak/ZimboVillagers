package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
/** AD-112: the core of a work building. Its grade (II..VI) is the highest level the building may work at; each ring the builders set
 *  raises it by one. No block entity (the survey treats one as a conflict), no drops (a mined core is lost), it outlasts TNT and no
 *  piston moves it. Only the builders set it: the core item places nothing. */
public final class BuildingCoreBlock extends Block {
 public static final IntegerProperty GRADE=IntegerProperty.create("grade",2,6);
 private static final VoxelShape SHAPE=Block.box(2,0,2,14,16,14);
 private final String type;
 public BuildingCoreBlock(String type){
  super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(50F,1200F).requiresCorrectToolForDrops().noLootTable().pushReaction(PushReaction.BLOCK).noOcclusion().sound(SoundType.LODESTONE));
  this.type=type;registerDefaultState(stateDefinition.any().setValue(GRADE,2));
 }
 /** The core type (see CoreCatalog.TYPES). */
 public String type(){return type;}
 @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(GRADE);}
 @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
 @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
 /** AD-147 review: the pillar is not a full block, so vanilla pathfinding took its cell for open floor and routed a walker through it — the
  *  builder moving a level-VI warehouse pressed into the core in the middle of its aisle for good. Nobody walks through a core. */
 @SuppressWarnings("deprecation")
 @Override public boolean isPathfindable(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
}
