package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-154 (roadmap 0.10, owner ladder «Наука»): every scientist place of the laboratory has a desk of its own, and the scientist who holds
 *  place i (ScienceWorks) works at desk i — not all of them at one cell. Place i opens at level i+1 (ScienceBalance.seats), so desk i stands in
 *  the design from level i+1 at the latest: the lectern and the desk row x6..9 from level I, level VI's lectern (frozen equipment) from VI.
 *  The desks are furniture and equipment that already stand, not new equipment: the frozen table (AD-148) gates a building's working level,
 *  and a desk added to it would lower every laboratory already built. Each desk has a free floor cell to stand on (never equipment, never a
 *  door lane — tools/free_cells.py). A laboratory of the old shape (AD-119) or a broken desk sends its scientist to the old desk cell (2,1,4). */
public final class LabDesks {
 private LabDesks(){}
 /** A desk: the block worked at, the free cell stood on (local to the laboratory design) and the first level whose layout holds it. */
 public record Desk(BlockPos block,BlockPos stand,int level){}
 public static final List<Desk> DESKS=List.of(
  new Desk(new BlockPos(3,1,3),new BlockPos(4,1,4),1),   // the lectern
  new Desk(new BlockPos(6,1,3),new BlockPos(5,1,4),1),   // the desk with the lamp
  new Desk(new BlockPos(7,1,3),new BlockPos(7,1,4),1),   // the bench: three places along the row
  new Desk(new BlockPos(8,1,3),new BlockPos(7,1,2),1),
  new Desk(new BlockPos(9,1,3),new BlockPos(8,1,2),1),
  new Desk(new BlockPos(2,1,5),new BlockPos(2,1,4),6));  // level VI's lectern
 /** Where every scientist stood before AD-154 (the old shape, a broken desk, no place): beside the first lectern. */
 public static final Desk OLD=new Desk(new BlockPos(3,1,3),new BlockPos(2,1,4),1);
 public static boolean isDesk(BlockState s){return s.is(Blocks.LECTERN)||s.getBlock() instanceof TableBlock;}
 /** Desks the laboratory design holds at a level (its layout as a builder lays it). */
 public static int designDesks(int level){var layout=BuildingPlacement.layout(BuildingTiers.layoutId("laboratory",level),BlockPos.ZERO,0);int n=0;
  for(var d:DESKS)if(isDesk(layout.getOrDefault(d.block(),Blocks.AIR.defaultBlockState())))n++;return n;}
 /** The world cells of the desk of place {@code seat} in this laboratory (the old desk cell when it has none standing). */
 public static Desk at(ServerLevel l,SettlementData.Entry e,Settlement.Building lab,int seat){
  if(seat>=0&&seat<DESKS.size()&&!BuildingTiers.legacyShape(l,e,lab)){var d=DESKS.get(seat);var block=world(e,lab,d.block());
   if(!l.hasChunkAt(block)||isDesk(l.getBlockState(block)))return new Desk(block,world(e,lab,d.stand()),d.level());}
  return new Desk(world(e,lab,OLD.block()),world(e,lab,OLD.stand()),1);}
 /** The desk of a scientist: that of the place he holds in the village's laboratory. */
 public static Desk of(ServerLevel l,SettlementData.Entry e,Settlement.Building lab,UUID worker){return at(l,e,lab,ScienceWorks.seat(l,e,worker));}
 private static BlockPos world(SettlementData.Entry e,Settlement.Building lab,BlockPos local){return BuildingPlacement.at(e,lab,local.getX(),local.getY(),local.getZ());}
}
