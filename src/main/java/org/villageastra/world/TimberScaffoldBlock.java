package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.*;
/** AD-030: temporary construction scaffold. Climbable inside, a standing platform on top, no drops (the builder returns the item). */
public final class TimberScaffoldBlock extends Block {
 private static final VoxelShape TOP=Block.box(0,14,0,16,16,16);
 public TimberScaffoldBlock(){super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.4F).sound(SoundType.SCAFFOLDING).noOcclusion().dynamicShape());}
 @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.block();}
 /** Solid top only for entities above it that are not descending, like vanilla scaffolding; empty inside so the column can be climbed. */
 @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
  // relocate-fix: asked without an entity — CollisionContext.empty(), which MoveControl uses for "is the walker buried in a block?" — the
  // answer is "nothing here". That context's isAbove() says yes to every shape, so a builder walking inside its own scaffold column was told
  // to jump every tick and rode the column up instead of sinking out of it (AD-132; a turned AD-129 home hung at 848/904).
  if(context==CollisionContext.empty())return Shapes.empty();
  return context.isAbove(Shapes.block(),pos,true)&&!context.isDescending()?TOP:Shapes.empty();
 }
 @Override public VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return TOP;}
 @Override public VoxelShape getVisualShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Shapes.empty();}
 @Override public boolean propagatesSkylightDown(BlockState state,BlockGetter level,BlockPos pos){return true;}
 /** Pathfinding avoids scaffold columns instead of climbing them, but may still pass when a column stands in the only doorway. */
 @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState state,BlockGetter level,BlockPos pos,net.minecraft.world.entity.Mob mob){return net.minecraft.world.level.pathfinder.BlockPathTypes.DANGER_OTHER;}
}
