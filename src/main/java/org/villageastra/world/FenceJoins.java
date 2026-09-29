package org.villageastra.world;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Design review 2026-09-24 ("a fence rail stands loose against the wall it touches"): a design is written with the fence and pane sides its
 *  author thought of, while a builder places blocks with neighbour updates, so the house in the world joins its fences, bars and panes to every
 *  fence and sturdy face beside them. This pass gives the laid design the joins the game computes (FenceBlock.connectsTo, IronBarsBlock.attachsTo
 *  against the design's own neighbours), so the design, the world built from it, a structure laid without updates and the repairs agree.
 *  Walls keep their drawn sides (their low/tall rule depends on what stands over them). */
public final class FenceJoins {
 private FenceJoins(){}
 private static net.minecraft.world.level.block.state.properties.BooleanProperty side(Direction d){
  return switch(d){case NORTH->CrossCollisionBlock.NORTH;case EAST->CrossCollisionBlock.EAST;case SOUTH->CrossCollisionBlock.SOUTH;default->CrossCollisionBlock.WEST;};}
 private static boolean sturdy(BlockState s,Direction face){return !s.isAir()&&s.isFaceSturdy(EmptyBlockGetter.INSTANCE,BlockPos.ZERO,face);}
 /** The design's fence, bars, pane or wall already stands whatever its sides (they follow neighbours the design does not hold, a fence of the
  *  next lot or the ground): builders are not sent to set it again (BuildingOrders) as repairs are not (BuildingRepairs.present). */
 public static boolean sameJoinable(BlockState now,BlockState design){var b=design.getBlock();
  return now.getBlock()==b&&(b instanceof FenceBlock||b instanceof IronBarsBlock||b instanceof WallBlock);}
 /** Joins every fence, iron bar and glass pane of the design to its neighbours in the design. Returns the same map. */
 public static <M extends Map<BlockPos,BlockState>> M join(M design){
  var air=Blocks.AIR.defaultBlockState();
  for(var cell:design.entrySet()){var s=cell.getValue();var b=s.getBlock();
   if(!(b instanceof FenceBlock)&&!(b instanceof IronBarsBlock))continue;var p=cell.getKey();var out=s;
   for(var dir:Direction.Plane.HORIZONTAL){var n=design.getOrDefault(p.relative(dir),air);var face=dir.getOpposite();
    boolean j=b instanceof FenceBlock f?f.connectsTo(n,sturdy(n,face),face):((IronBarsBlock)b).attachsTo(n,sturdy(n,face));
    out=out.setValue(side(dir),j);}
   if(out!=s)cell.setValue(out);}
  return design;
 }
}
