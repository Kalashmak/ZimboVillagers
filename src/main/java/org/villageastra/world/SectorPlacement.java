package org.villageastra.world;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.*;
import org.villageastra.VillageAstra;
/** Keeps vanilla /locate's random-spread fast path while choosing a real site in each kilometre sector. */
public final class SectorPlacement extends RandomSpreadStructurePlacement {
 public static final Codec<SectorPlacement> CODEC=Codec.unit(SectorPlacement::new);
 public SectorPlacement(){super(63,0,RandomSpreadType.LINEAR,10387312);}
 @Override public ChunkPos getPotentialStructureChunk(long seed,int x,int z){
  var p=SettlementSectors.forServer(seed,x,z);return p==null?new ChunkPos(Math.floorDiv(SettlementSectors.sector(x*16)*1000+500,16),Math.floorDiv(SettlementSectors.sector(z*16)*1000+500,16)):new ChunkPos(p.origin());
 }
 @Override protected boolean isPlacementChunk(ChunkGeneratorStructureState state,int x,int z){var p=SettlementSectors.forServer(state.getLevelSeed(),x,z);return p!=null&&(p.origin().getX()>>4)==x&&(p.origin().getZ()>>4)==z;}
 @Override public StructurePlacementType<?> type(){return VillageAstra.SECTOR_PLACEMENT.get();}
}
