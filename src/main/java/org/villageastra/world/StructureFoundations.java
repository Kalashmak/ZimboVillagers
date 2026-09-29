package org.villageastra.world;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
/** Ground support for generated buildings, shared with the generation regression test. */
public final class StructureFoundations {
 private StructureFoundations(){}
 public static void support(LevelAccessor world,BlockPos floor,Map<BlockPos,BlockState> design){
  // Plans are placed bottom-up. Filling below an air cell at ground level used to bury
  // the already excavated stair; designed underground rooms must never be foundations.
  if(design.getOrDefault(floor,Blocks.AIR.defaultBlockState()).isAir())return;
  for(int down=1;down<=20;down++){
   var support=floor.below(down);if(design.containsKey(support)||!world.getBlockState(support).canBeReplaced())break;
   world.setBlock(support,Blocks.DIRT.defaultBlockState(),2);
  }
 }
}
