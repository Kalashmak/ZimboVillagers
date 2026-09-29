package org.villageastra.world;
import net.minecraft.core.BlockPos;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-121 (docs/plans/hall-castle-spec.md): the cells of a village's town hall that other systems use, in one place. The 7x7 hall keeps
 *  every cell where it always was (the stock chest at (1,1,4) from the village centre, the hall's origin). A castle village
 *  (lotLayout >= OrganicLots.CASTLE_LOTS) has its centre on the castle's seal (CastlePlan (14,1,23)): the castle lot's corner is
 *  CASTLE_X,0,CASTLE_Z from the centre and the stock is the first double chest of the castle's kit (10,1,22). */
public final class HallSite {
 private HallSite(){}
 /** The hall's stock chest in the 7x7 design (x,y,z from the hall's origin). */
 public static final int STOCK_X=1,STOCK_Y=1,STOCK_Z=4;
 /** The castle lot's north-west corner from the village centre (the centre stands on the seal), and its stock chest in CastlePlan. */
 public static final int CASTLE_X=OrganicLots.CASTLE_X,CASTLE_Z=OrganicLots.CASTLE_Z,CASTLE_STOCK_X=10,CASTLE_STOCK_Y=1,CASTLE_STOCK_Z=22;
 /** Whether this village's hall is the castle (new worlds from CASTLE_LOTS; old saves keep the 7x7 hall). */
 public static boolean castle(Settlement s){return s.lotLayout()>=OrganicLots.CASTLE_LOTS;}
 /** The hall's stock chest for a 7x7 hall whose origin is {@code base} (the village centre). */
 public static BlockPos stock(BlockPos base){return base.offset(STOCK_X,STOCK_Y,STOCK_Z);}
 /** The castle's stock chest for a village centred at {@code center}. */
 public static BlockPos castleStock(BlockPos center){return center.offset(CASTLE_X+CASTLE_STOCK_X,CASTLE_STOCK_Y,CASTLE_Z+CASTLE_STOCK_Z);}
 /** Where the village's hall design is laid from: the centre for the 7x7 hall, the castle lot's corner for a castle. */
 public static BlockPos base(SettlementData.Entry e){return castle(e.settlement())?e.center().offset(CASTLE_X,0,CASTLE_Z):e.center();}
 /** Where the residents shelter in a raid: inside the hall - the 7x7 hall's (3,1,4), the castle hall's floor beside the seal (13,1,21). */
 public static BlockPos shelter(SettlementData.Entry e){return castle(e.settlement())?e.center().offset(-1,1,-2):e.center().offset(3,1,4);}
 /** Where the homeless sleep: the 7x7 hall's (3,1,2), in the castle hall (15,1,20). */
 public static BlockPos homeless(SettlementData.Entry e){return castle(e.settlement())?e.center().offset(1,1,-3):e.center().offset(3,1,2);}
 /** The village's hall stock chest. */
 public static BlockPos stock(SettlementData.Entry e){return castle(e.settlement())?castleStock(e.center()):stock(e.center());}
}
