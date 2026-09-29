package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;

/** Each generation chunk owns its blocks, chest and entities; no SavedData writes on generation threads. */
public final class SettlementPiece extends StructurePiece {
    private final BlockPos origin;
    private final boolean natural;
    private final boolean terrainLots;
    private final int layoutVersion;
    private final java.util.UUID generatedId;
    private volatile int[] elevations;
    private java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> terrainRoads;
    private synchronized java.util.Map<BlockPos,net.minecraft.world.level.block.state.BlockState> roads(WorldGenLevel world,ChunkGenerator generator,Settlement settlement){
        if(terrainRoads==null){java.util.function.ToIntFunction<BlockPos> height=p->generator.getFirstOccupiedHeight(p.getX(),p.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,world,world.getLevel().getChunkSource().randomState());terrainRoads=NaturalVillage.generatedRoads(layoutVersion,origin,settlement,height);}
        return terrainRoads;
    }
    private static int half(){return org.villageastra.domain.OrganicLots.reach(org.villageastra.domain.OrganicLots.CURRENT)+7;}
    public SettlementPiece(BlockPos origin) {
        this(origin,null,null);
    }
    public SettlementPiece(BlockPos origin,java.util.UUID id,int[] heights) {
        // AD-121: the piece reaches the lots' reach and a margin of 7 (54 = 47+7; a castle village's lots reach 64).
        super(VillageAstra.SETTLEMENT_PIECE.get(), 0, new BoundingBox(origin.getX()-half(),origin.getY()-36,origin.getZ()-half(),
                origin.getX()+half(),origin.getY()+40,origin.getZ()+half()));
        this.origin = origin;this.natural=true;this.terrainLots=true;this.layoutVersion=org.villageastra.domain.OrganicLots.CURRENT;
        this.generatedId=id;this.elevations=heights==null?null:heights.clone();
    }
    public SettlementPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(VillageAstra.SETTLEMENT_PIECE.get(), tag);
        origin = BlockPos.of(tag.getLong("AstraOrigin"));natural=tag.getBoolean("AstraNatural");terrainLots=tag.getBoolean("AstraTerrainLots");
        elevations=tag.contains("AstraElevations")?tag.getIntArray("AstraElevations"):null;
        layoutVersion=tag.contains("AstraLayoutVersion")?tag.getInt("AstraLayoutVersion"):1;
        generatedId=tag.hasUUID("AstraGeneratedId")?tag.getUUID("AstraGeneratedId"):null;
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putLong("AstraOrigin", origin.asLong());tag.putBoolean("AstraNatural",natural);tag.putBoolean("AstraTerrainLots",terrainLots);tag.putInt("AstraLayoutVersion",layoutVersion);if(elevations!=null)tag.putIntArray("AstraElevations",elevations);
        if(generatedId!=null)tag.putUUID("AstraGeneratedId",generatedId);
    }
    @Override public void postProcess(WorldGenLevel world, StructureManager structures, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        var id = generatedId==null?StarterVillage.id(world.getLevel(), origin):generatedId;
        if(elevations==null){
            int[] values=new int[7];int index=0;
            for(var b:Settlement.natural(id,new int[7],layoutVersion).buildings()){
                if(terrainLots&&index>0){
                    var design=BuildingBlueprints.design(b.type());
                    // AD-058: level with the ground at the door, as the sector plan does.
                    int door=generator.getFirstOccupiedHeight(origin.getX()+b.x()+BuildingBlueprints.doorX(b.type()),origin.getZ()+b.z(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,world,world.getLevel().getChunkSource().randomState());
                    values[index]=door-origin.getY();
                }index++;
            }elevations=values;
        }
        var settlement=natural?Settlement.natural(id,elevations,layoutVersion):Settlement.initial(id);
        java.util.Set<BlockPos> foundations=new java.util.HashSet<>();
        // AD-104: a farm stands on its farmhouse and the whole box of its field, so the field's farmland and water rest on earth.
        for(var b:settlement.buildings())for(var column:NaturalVillage.lot(settlement,b))foundations.add(origin.offset(column.getX(),b.y(),column.getZ()));
        // AD-063: the strip around every lot is levelled to the floor before the buildings stand, so no door opens into a bank of earth.
        if(natural&&layoutVersion>=4)NaturalVillage.lots(origin,settlement).forEach((column,floor)->{
            int x=origin.getX()+column.getX(),z=origin.getZ()+column.getZ();
            var top=new BlockPos(x,floor,z);if(!chunk.isInside(top))return;
            int ground=generator.getFirstOccupiedHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,world,world.getLevel().getChunkSource().randomState());
            for(int y=floor+1;y<=Math.max(ground,floor+2);y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int y=ground+1;y<floor;y++)world.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            world.setBlock(top,Blocks.GRASS_BLOCK.defaultBlockState(),2);
        });
        var layout = natural?NaturalVillage.layout(origin,settlement):StarterVillage.layout(origin);
        layout.forEach((pos, state) -> {
            if (!chunk.isInside(pos)) return;
            world.setBlock(pos, state, 2);
            if (foundations.contains(pos)) StructureFoundations.support(world,pos,layout);
        });
        // AD-104: the trees the world grew here before the village stand over a farm's field no more — trunk, crown and drift — so the wheat
        // grows under open sky (in shade a crop falls off at the first touch). Trees of chunks decorated later go with the village clearing.
        // AD-112: over the field its builders will lay too (level II), so a level-II farm finds its new module open; AD-130: from layout 6 over
        // the barn's whole footprint, up to its roof.
        if(natural)for(var b:settlement.buildings())if(b.type().equals("farm"))for(var column:NaturalVillage.farmClearing(settlement,b)){
            var ground=origin.offset(b.x()+column.getX(),b.y(),b.z()+column.getZ());if(!chunk.isInside(ground))continue;
            for(int y=FarmField.HEADROOM+1;y<=40;y++){var at=ground.above(y);var found=world.getBlockState(at);
                if(VillageClearing.tree(found)||found.is(Blocks.SNOW))world.setBlock(at,Blocks.AIR.defaultBlockState(),2);}
        }
        (natural?java.util.Map.<BlockPos,net.minecraft.world.level.block.state.BlockState>of():StarterVillage.approaches(origin)).forEach((pos,state) -> {
            if(chunk.isInside(pos))world.setBlock(pos,state,2);
        });
        var roads=layoutVersion>=2?roads(world,generator,settlement):natural?NaturalVillage.conformRoads(NaturalVillage.roads(origin,settlement),origin,settlement,
            pos->generator.getFirstOccupiedHeight(pos.getX(),pos.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,world,world.getLevel().getChunkSource().randomState())):StarterVillage.initialPaths(origin);
        roads.forEach((pos,state) -> {
            if(!chunk.isInside(pos))return;
            int ground=generator.getFirstOccupiedHeight(pos.getX(),pos.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,world,world.getLevel().getChunkSource().randomState());
            NaturalVillage.placeInitialRoad(world,pos,state,ground);
        });
        for(var building:settlement.buildings()){
            BlockPos chestPos=origin.offset(building.x()+1,building.y()+1,building.z()+4);
            if(chunk.isInside(chestPos)&&world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest)StarterVillage.stockChest(chest,id,building.type().equals("town_hall"));
        }
        int index = 0;
        for (Resident resident : settlement.residents()) {
            var home=settlement.buildings().stream().filter(b->b.id().equals(resident.home())).findFirst().orElseThrow();
            var cell=HousingLadder.spawnCell(home.type(),index);BlockPos pos=origin.offset(home.x()+cell.getX(),home.y()+cell.getY(),home.z()+cell.getZ());
            index++;
            if (!chunk.isInside(pos)) continue;
            var entity = VillageAstra.RESIDENT.get().create(world.getLevel());
            if (entity == null) throw new IllegalStateException("Cannot generate resident");
            entity.bind(id, resident);
            entity.bootstrapOrigin(origin);entity.bootstrapNatural(natural);entity.bootstrapElevations(elevations);
            entity.bootstrapLayoutVersion(layoutVersion);
            if(layoutVersion>=2)entity.bootstrapRoads(roads.keySet().stream().mapToLong(BlockPos::asLong).toArray());
            entity.moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            world.addFreshEntity(entity);
        }
    }
}
