package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Explicit development fixture. All occupied cells are checked before the first mutation. */
public final class StarterVillage {
    private StarterVillage() {}
    public static final int[][] BUILDINGS = Settlement.initialBuildings(new UUID(0,0)).stream()
            .map(b -> new int[]{b.x(), b.z()}).toArray(int[][]::new);
    public static Map<BlockPos, BlockState> layout(BlockPos origin) {
        Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        String[] types={"town_hall","home","home","home","farm","forester","mine"};
        for(int i=0;i<BUILDINGS.length;i++)cells.putAll(BuildingBlueprints.layout(types[i],origin.offset(BUILDINGS[i][0],0,BUILDINGS[i][1])));
        // Farmland belongs to the farmer, fenced tree plot to the forester.
        // AD-104: the field is FarmField's level-I module behind the farmhouse, its wheat seeded by where the farm stands; AD-130: the farm
        // grows west (Settlement.initial), so its final footprint stays clear of the forester's lot.
        for(int i=0;i<BUILDINGS.length;i++)if(types[i].equals("farm")){var base=origin.offset(BUILDINGS[i][0],0,BUILDINGS[i][1]);cells.putAll(FarmField.layout(base,FarmField.modules(1),base.asLong()));}
        return cells;
    }
    /** Generated approach on slopes; distinct from the empty-site developer fixture. */
    public static Map<BlockPos,BlockState> approaches(BlockPos origin) {
        Map<BlockPos,BlockState> result=new LinkedHashMap<>();
        for (int[] building:BUILDINGS) for (int step=0;step<5;step++) {
            BlockPos pos=origin.offset(building[0]+3,-step,building[1]-1-step);
            result.put(pos,Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING,Direction.SOUTH));
            result.put(pos.above(),Blocks.AIR.defaultBlockState());
            result.put(pos.above(2),Blocks.AIR.defaultBlockState());
        }
        return result;
    }
    public static Map<BlockPos,BlockState> initialPaths(BlockPos origin) {
        Map<BlockPos,BlockState> paths=new LinkedHashMap<>();
        // AD-131: the street runs on to the mine at x 32; AD-130: the lane between the houses stops at the street, clear of the farm's
        // final footprint west of the forester (x -12..9 from z 12); the back street reaches west to the door of the barn's stair tower (x -6).
        for(int x=0;x<=39;x++)for(int z:new int[]{-2,-1})paths.put(origin.offset(x,0,z),Blocks.DIRT_PATH.defaultBlockState());
        for(int x=-9;x<=39;x++)for(int z:new int[]{10,11})paths.put(origin.offset(x,0,z),Blocks.DIRT_PATH.defaultBlockState());
        for(int x:new int[]{8,9})for(int z=-2;z<=11;z++)paths.put(origin.offset(x,0,z),Blocks.DIRT_PATH.defaultBlockState());
        return paths;
    }
    public static void stockChest(ChestBlockEntity chest, UUID id, boolean townHall) {
        chest.getPersistentData().putUUID("AstraSettlement", id);
        if (townHall) {
            InitialStock.fill(chest);
        }
        chest.setChanged();
    }
    public static UUID id(ServerLevel level, BlockPos origin) {
        return UUID.nameUUIDFromBytes((level.dimension().location() + ":" + origin.asLong()).getBytes(StandardCharsets.UTF_8));
    }
    public static String validate(ServerLevel level, Map<BlockPos, BlockState> cells) { return validate(level,cells,Integer.MIN_VALUE); }
    /** AD-074: cells below the lot floor are dug out (a mine shaft), so natural ground there is not an obstacle. */
    public static String validate(ServerLevel level, Map<BlockPos, BlockState> cells, int floor) {
        for (BlockPos pos : cells.keySet()) {
            // A mine shaft is dug into the ground below the lot: whatever stands there is what the shaft goes through.
            if (pos.getY() < floor) continue;
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return "bounds";
            if (!level.hasChunkAt(pos)) return "unloaded";
            BlockState before = level.getBlockState(pos);
            if (!before.isAir() && !before.is(Blocks.GRASS) && !before.is(Blocks.TALL_GRASS)) return "occupied";
            if (level.getBlockEntity(pos) != null || !before.getFluidState().isEmpty()) return "occupied";
        }
        return "ok";
    }
    public static Settlement create(ServerLevel level, BlockPos origin) {
        SettlementData data = SettlementData.get(level.getServer());
        UUID id = id(level, origin);
        if (data.entry(id) != null) return data.entry(id).settlement();
        Map<BlockPos, BlockState> cells = layout(origin);
        String result = validate(level, cells, origin.getY());
        if (!result.equals("ok")) throw new IllegalStateException(result);
        Settlement settlement = Settlement.initial(id);
        List<ResidentEntity> people = new ArrayList<>();
        int index = 0;
        for (Resident r : settlement.residents()) {
            ResidentEntity entity = VillageAstra.RESIDENT.get().create(level);
            if (entity == null) throw new IllegalStateException("entity");
            entity.bind(id, r);
            int house = 1 + index / 2;
            var cell = HousingLadder.spawnCell("home", index);
            BlockPos spawn = origin.offset(BUILDINGS[house][0] + cell.getX(), cell.getY(), BUILDINGS[house][1] + cell.getZ());
            entity.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            if (level.getEntity(r.id()) != null) throw new IllegalStateException("identity");
            people.add(entity); index++;
        }
        // Validated development fixture only. Ordinary building uses material-funded work orders.
        cells.forEach((pos, state) -> level.setBlock(pos, state, 2));
        for (int i = 0; i < BUILDINGS.length; i++) {
            BlockPos chestPos = origin.offset(BUILDINGS[i][0] + 1,1,BUILDINGS[i][1] + 4);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                stockChest(chest, id, i == 0);
            }
        }
        data.add(new SettlementData.Entry(settlement, level.dimension().location().toString(), origin));
        for (ResidentEntity entity : people) {
            if (!level.addFreshEntity(entity)) throw new IllegalStateException("entity_spawn");
        }
        return settlement;
    }
}
