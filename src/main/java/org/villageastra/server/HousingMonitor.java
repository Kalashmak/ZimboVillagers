package org.villageastra.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.villageastra.domain.Settlement;
import org.villageastra.world.StarterVillage;

/** Bounded integrity checks; unknown/unloaded is not destroyed. No player repair recognition. */
public final class HousingMonitor {
    private HousingMonitor() {}
    public static void inspect(MinecraftServer server, SettlementData data, SettlementData.Entry entry) {
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(entry.dimension())));
        if (level == null) return;
        // Every usable home with registered housing geometry, including field-built houses (AD-028).
        for (var home : java.util.List.copyOf(entry.settlement().homes())) {
            if (!home.usable()) continue;
            var building=entry.settlement().buildings().stream().filter(b->b.id().equals(home.id())).findFirst().orElse(null);
            if (building==null || !(building.type().equals("home") || building.type().equals("home_2"))) continue;
            // AD-125: a home taken apart to be moved keeps its dwellers; it is checked again at its new place.
            if (org.villageastra.world.Relocations.moving(level,entry,home.id())) continue;
            BlockPos base=entry.center().offset(building.x(),building.y(),building.z());
            // AD-123 (H3): the beds the home really holds people for, not the count of its level design.
            if (BuildingIntegrity.home(level,base,org.villageastra.world.BuildingTiers.layoutId(building.type(),building.level()),building.rotation(),home.capacity())==BuildingIntegrity.Result.DAMAGED) {
                entry.settlement().damageHome(home.id(), data.clock().ticks());
                data.setDirty();
            }
        }
    }
}
