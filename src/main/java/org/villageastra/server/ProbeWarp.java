package org.villageastra.server;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.villageastra.VillageAstra;

/** Dev-only tick warp for long client probes (like Carpet's tick warp): with -Dvillageastra.smoke=true and -Dvillageastra.probeWarp=N the
 *  integrated server starts its next tick after 50/N ms instead of 50 ms, and with N=0 as soon as the last one is done. Game logic, game time
 *  and every tick-counted rule stay exactly as they are; only the wall-clock wait between ticks goes. MSPT (work per tick) is not changed.
 *  Never active in normal play or on a dedicated server. In the GameTest server only with -PgtWarp or for a batch that asks for it (batchWarp). */
@Mod.EventBusSubscriber(modid = VillageAstra.ID)
public final class ProbeWarp {
    private static final String FLAG = "villageastra.probeWarp";
    private static Field nextTickTime, delayedTasksTime;
    private static boolean announced, broken;
    private static volatile boolean ended;
    private static long sampleTick = -1, sampleMillis;
    private ProbeWarp() {}

    /** The warp factor this JVM was started with: 1 (off) unless a probe asked for more; 0 means no wait at all. */
    public static int factor() {
        if (!Boolean.getBoolean("villageastra.smoke")) return 1;
        String raw = System.getProperty(FLAG);
        if (raw == null || raw.isBlank()) return 1;
        try { return Math.max(0, Integer.parseInt(raw.trim())); } catch (NumberFormatException ex) { return 1; }
    }

    /** Set by a GameTest batch of long, tick-counted runs (the style builder batch) for its own duration: 0 = no wait between ticks. */
    public static volatile int batchWarp = 1;
    /** Dev-only: {@code runGameTestServer -PgtWarp=N} runs the GameTest server N times faster (0 = no wait); GameTests count ticks, not
     *  wall-clock time, so their outcome does not change. Off (1) without the flag, and never outside the GameTest server. */
    static int gameTestFactor() {
        String raw = System.getProperty("villageastra.gtWarp");
        if (raw == null || raw.isBlank()) return batchWarp;
        try { return Math.max(0, Integer.parseInt(raw.trim())); } catch (NumberFormatException ex) { return 1; }
    }

    /** The probe's waiting is over: the rest (camera, frames, checks against the clock) runs at normal speed. Any thread. */
    public static void end(String why) {
        if (ended || factor() == 1) return;
        ended = true;
        LogUtils.getLogger().info("ASTRA_WARP ended: {}", why);
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || broken || ended) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.isDedicatedServer()) return;
        int factor = server instanceof net.minecraft.gametest.framework.GameTestServer ? gameTestFactor() : factor();
        if (factor == 1) return;
        try {
            if (nextTickTime == null) nextTickTime = ObfuscationReflectionHelper.findField(MinecraftServer.class, "f_129726_");
            long now = Util.getMillis(), next = nextTickTime.getLong(server);
            // The loop has already moved the deadline 50 ms on for the tick that just ran: at N times speed it moves 50/N ms.
            long target = factor == 0 ? now : next - 50L + 50L / factor;
            nextTickTime.setLong(server, Math.max(target, now));
            // runServer replaces the task deadline with now+50 after END. A queued task
            // runs in waitUntilNextTick, after that assignment; shorten this second wait
            // too. Do not skip tickServer, change game clocks or advance any worker.
            if (delayedTasksTime == null) delayedTasksTime = ObfuscationReflectionHelper.findField(MinecraftServer.class, "f_129727_");
            long deadline = Math.max(target, now);
            server.tell(new TickTask(server.getTickCount(), () -> {
                if (ended || broken) return;
                try {
                    delayedTasksTime.setLong(server, deadline);
                } catch (IllegalAccessException ex) {
                    broken = true;
                    LogUtils.getLogger().error("ASTRA_WARP task wait disabled: {}", ex.toString());
                }
            }));
        } catch (RuntimeException | IllegalAccessException ex) {
            broken = true;
            LogUtils.getLogger().error("ASTRA_WARP disabled: {}", ex.toString());
            return;
        }
        long tick = server.getTickCount(), millis = Util.getMillis();
        if (!announced) {
            announced = true; sampleTick = tick; sampleMillis = millis;
            LogUtils.getLogger().info("ASTRA_WARP active factor={} (0 = no wait between ticks); dev-only, {}", factor,
                    server instanceof net.minecraft.gametest.framework.GameTestServer ? "GameTest server" : "integrated server");
        } else if (tick - sampleTick >= 1200) {
            double seconds = Math.max(1, millis - sampleMillis) / 1000.0;
            LogUtils.getLogger().info("ASTRA_WARP rate {} ticks in {}s = {}x real time, mspt={}", tick - sampleTick,
                    String.format(java.util.Locale.ROOT, "%.1f", seconds), String.format(java.util.Locale.ROOT, "%.1f", (tick - sampleTick) / seconds / 20.0),
                    String.format(java.util.Locale.ROOT, "%.2f", server.getAverageTickTime()));
            sampleTick = tick; sampleMillis = millis;
        }
    }
}
