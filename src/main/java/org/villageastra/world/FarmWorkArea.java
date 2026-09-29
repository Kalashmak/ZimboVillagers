package org.villageastra.world;
import net.minecraft.core.BlockPos;
import org.villageastra.server.SettlementData;
import java.util.*;
/** Only the registered settlement's real field cells; arbitrary nearby player crops are excluded. */
public final class FarmWorkArea {
 private FarmWorkArea(){}
 // AD-104: the field is FarmField's modules, turned with their farm; this is only the farmer's name for it.
 public static List<BlockPos> cells(SettlementData.Entry entry){return FarmField.cells(entry);}
 /** AD-112: the plots the farmers work — each farm's worked modules (its core's level within the land laid). */
 public static List<BlockPos> cells(net.minecraft.server.level.ServerLevel level,SettlementData.Entry entry){return FarmField.workedCells(level,entry);}
 /** AD-130: the plots one farmer works — his own farm's worked fields, his share of them by rank (FarmField.share); every farm's when he is
  *  posted at none. */
 public static List<BlockPos> cells(net.minecraft.server.level.ServerLevel level,SettlementData.Entry entry,java.util.UUID farmer){
  var farm=entry.settlement().workplace(farmer);
  return farm==null||!farm.type().equals("farm")?FarmField.workedCells(level,entry):FarmField.workedCells(level,entry,farm,farmer);
 }
}
