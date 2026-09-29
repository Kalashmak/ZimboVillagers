package org.villageastra.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.villageastra.VillageAstra;
import java.util.Optional;

/** Generated only through Minecraft's structure pipeline; never scans/deletes existing villages. */
public final class SettlementStructure extends Structure {
    public static final Codec<SettlementStructure> CODEC = simpleCodec(SettlementStructure::new);
    public SettlementStructure(StructureSettings settings) { super(settings); }
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var plan=SettlementSectors.plan(context.chunkGenerator(),context.randomState(),context.seed(),context.heightAccessor(),SettlementSectors.sector(context.chunkPos().getMinBlockX()),SettlementSectors.sector(context.chunkPos().getMinBlockZ()));
        if(plan==null||!new net.minecraft.world.level.ChunkPos(plan.origin()).equals(context.chunkPos()))return Optional.empty();
        return Optional.of(new GenerationStub(plan.origin(),pieces->pieces.addPiece(new SettlementPiece(plan.origin(),plan.id(),plan.elevations()))));
    }
    @Override public StructureType<?> type() { return VillageAstra.SETTLEMENT_STRUCTURE.get(); }
}
