package org.villageastra.world;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.VillageAstra;
import org.villageastra.domain.CoreCatalog;
/** AD-112: the registered core blocks. Fetched at call time (never in a static initialiser), so designs cached on first use see them. */
public final class Cores {
 private Cores(){}
 /** The core block a building type holds (the quarry's is the mine's), or null for a building without a core. */
 public static BuildingCoreBlock block(String buildingType){var t=CoreCatalog.coreType(buildingType);return t==null?null:VillageAstra.CORES.get(t).get();}
 /** The core of a building type at a grade (2..6), or null for a building without a core. */
 public static BlockState state(String buildingType,int grade){var b=block(buildingType);return b==null?null:b.defaultBlockState().setValue(BuildingCoreBlock.GRADE,Math.max(2,Math.min(6,grade)));}
 /** The grade of a core block state, or 0 when the state is not a core. */
 public static int grade(BlockState state){return state!=null&&state.getBlock() instanceof BuildingCoreBlock?state.getValue(BuildingCoreBlock.GRADE):0;}
 /** Whether this state is the core of that building type. */
 public static boolean isCoreOf(BlockState state,String buildingType){var b=block(buildingType);return b!=null&&state!=null&&state.is(b);}
}
