package org.villageastra.client;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Optional inspection of a saved diagnostic copy; never a natural-growth acceptance test. */
final class GrowthQuarryAudit {
    private static volatile boolean busy, done;
    private static volatile String failure;
    private static int cursor, waited;
    private static boolean announced;
    private static final java.util.List<BlockPos> reachable = new java.util.ArrayList<>();
    private static final Map<String, Integer> counts = new TreeMap<>();

    static boolean enabled() { return Boolean.getBoolean("villageastra.growthQuarryAudit"); }

    static void tick(Minecraft mc) {
        if (failure != null) {
            LogUtils.getLogger().error("ASTRA_GROWTH_QUARRY_AUDIT FAILED {}", failure);
            mc.stop();
            return;
        }
        if (done) { mc.stop(); return; }
        if (busy) return;
        busy = true;
        mc.getSingleplayerServer().execute(() -> {
            try { sample(mc); }
            catch (Exception ex) { failure = ex.toString(); }
            finally { busy = false; }
        });
    }

    private static void count(String reason) { counts.merge(reason, 1, Integer::sum); }

    private static void sample(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        var entry = SettlementData.get(server).entries().iterator().next();
        var person = entry.settlement().residents().stream()
                .filter(r -> r.alive() && r.profession() == Profession.MINER).findFirst().orElseThrow();
        var worker = (ResidentEntity) level.getEntity(person.id());
        if (worker == null) return;
        if (!announced) {
            announced = true;
            LogUtils.getLogger().info("ASTRA_GROWTH_QUARRY_AUDIT START auditOnly=true center={} miner={} status={}",
                    entry.center(), worker.blockPosition(), worker.workStatus());
        }
        // A sleeping body's path is not evidence about a working miner's access.
        if (cursor == 0 && (!worker.onGround() || worker.isSleeping()
                || !worker.workStatus().equals("missing_stair_stone"))) {
            if (++waited > 6000) throw new IllegalStateException("Miner did not return to the blocked stair; no quarry verdict");
            return;
        }
        int radius = NaturalSupplyGoal.SEARCH_RADIUS, width = 2 * radius + 1;
        int end = Math.min(width * width, cursor + 512);
        for (; cursor < end; cursor++) {
            int x = entry.center().getX() + cursor % width - radius;
            int z = entry.center().getZ() + cursor / width - radius;
            // Inspect existing saved terrain. This may load chunks in the diagnostic copy.
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            var target = new BlockPos(x, y, z);
            if (!level.getBlockState(target).is(Blocks.STONE)) continue;
            count("surface_stone");
            if (!SurfaceQuarry.safe(level, target)) { count("unsafe_or_protected"); continue; }
            count("safe_stone");
            if (SurfaceQuarry.tool(level, entry, level.getBlockState(target)) < 0) { count("no_stock_pick"); continue; }
            var stand = HarvestAccess.find(worker, target, NaturalSupplyGoal.ROUTE_RANGE);
            if (stand == null) { count("no_reversible_route"); continue; }
            count("reachable");
            reachable.add(target);
            LogUtils.getLogger().info("ASTRA_GROWTH_QUARRY_AUDIT reachable target={} stand={} miner={}",
                    target, stand, worker.blockPosition());
        }
        if (cursor == width * width) {
            var building = entry.settlement().workplace(person.id());
            var work = MineWork.read(level, building);
            var chest = LogisticsRoutes.chest(level, entry, building);
            var stock = LogisticsRoutes.position(entry, building);
            if (worker.isSleeping() || !worker.onGround()
                    || !worker.workStatus().equals("missing_stair_stone")
                    || worker.blockPosition().distSqr(stock) > 16) {
                if (++waited > 12000) throw new IllegalStateException("No stable blocked-mine origin for recheck");
                return;
            }
            int mineReachable = 0;
            for (var target : reachable) {
                var stand = HarvestAccess.find(worker, target, NaturalSupplyGoal.ROUTE_RANGE);
                if (stand != null) mineReachable++;
                LogUtils.getLogger().info("ASTRA_GROWTH_QUARRY_AUDIT RECHECK target={} stand={} origin={}",
                        target, stand, worker.blockPosition());
            }
            LogUtils.getLogger().info("ASTRA_GROWTH_QUARRY_AUDIT ORIGIN_RESULT candidates={} reachable={} origin={}",
                    reachable.size(), mineReachable, worker.blockPosition());
            LogUtils.getLogger().info("ASTRA_GROWTH_QUARRY_AUDIT COMPLETE columns={} counts={} miner={} status={} stairItem={} stairStep={} placed={} missing={} ownCobblestone={} ownSandstone={} auditOnly=true",
                    cursor, counts, worker.blockPosition(), worker.workStatus(), work.getString("stairItem"),
                    work.getInt("stairStep"), work.getInt("stairPlaced"), MineStairWork.missing(level, entry, building, work),
                    chest == null ? -1 : chest.countItem(Items.COBBLESTONE), chest == null ? -1 : chest.countItem(Items.SANDSTONE));
            done = true;
        }
    }
}
