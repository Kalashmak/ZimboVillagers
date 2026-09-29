package org.villageastra.world;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Finite bootstrap stock from one resource, not a recurring production source. */
public final class InitialStock {
    private record Definition(List<Entry> items) {}
    private record Entry(String item, int count) {}
    private static final Definition DEFINITION = read();
    private InitialStock() {}
    private static Definition read() {
        var stream = InitialStock.class.getResourceAsStream("/data/villageastra/balance/initial_stock.json");
        if (stream == null) throw new IllegalStateException("Missing initial stock definition");
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var definition = new Gson().fromJson(reader, Definition.class);
            if (definition.items == null || definition.items.size() > 27) throw new IllegalStateException("Invalid stock definition");
            return definition;
        } catch (java.io.IOException ex) { throw new IllegalStateException("Could not read initial stock", ex); }
    }
    public static void fill(net.minecraft.world.Container container) {
        int slot = 0;
        for (Entry entry : DEFINITION.items) {
            var key = ResourceLocation.tryParse(entry.item);
            if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) throw new IllegalStateException("Unknown stock item: " + entry.item);
            var item = ForgeRegistries.ITEMS.getValue(key);
            if (entry.count <= 0 || entry.count > item.getMaxStackSize()) throw new IllegalStateException("Invalid stock count: " + entry.item);
            container.setItem(slot++, new ItemStack(item,entry.count));
        }
    }
}
