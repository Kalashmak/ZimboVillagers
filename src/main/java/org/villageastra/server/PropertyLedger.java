package org.villageastra.server;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Common exact per-village reputation, including attributed theft and physical donations. */
public final class PropertyLedger extends SavedData {
    private record Key(UUID village,UUID player) {}
    private final Map<UUID,org.villageastra.domain.ElectionRoll> rolls=new LinkedHashMap<>();
    public org.villageastra.domain.ElectionRoll roll(UUID village){return rolls.computeIfAbsent(village,id->new org.villageastra.domain.ElectionRoll());}
    public void gift(UUID village,UUID player,int amount){roll(village).gift(player,amount);setDirty();}
    /** AD-111: somebody holds positive reputation in this village; never creates a roll. */
    public boolean anyPositive(UUID village){var r=rolls.get(village);return r!=null&&r.accounts().stream().anyMatch(a->a.score()>0);}
    public static PropertyLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PropertyLedger::load,()->{
            var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra_property.dat");
            if(java.nio.file.Files.exists(file) || java.nio.file.Files.exists(file.resolveSibling("villageastra_property.dat_old"))) throw new IllegalStateException("Refusing to replace corrupt property ledger");
            return new PropertyLedger();
        },"villageastra_property");
    }
    /** MULTI-006: the old rating of a transferred settlement is archived and its live roll starts empty. */
    public net.minecraft.nbt.CompoundTag reset(UUID village) {
        var archived=ElectionNbt.write(roll(village)); rolls.remove(village); setDirty(); return archived;
    }
    public void theft(UUID village,UUID player,int amount) {
        if(amount<=0) throw new IllegalArgumentException("Invalid theft amount");
        roll(village).theft(player,amount); setDirty();
    }
    public long stolen(UUID village,UUID player) { return roll(village).account(player).theft(); }
    public long reputationPenalty(UUID village,UUID player) { return -stolen(village,player); }
    public static PropertyLedger load(CompoundTag tag) {
        if(!tag.contains("schema",Tag.TAG_INT)||(tag.getInt("schema")!=1&&tag.getInt("schema")!=2))throw new IllegalArgumentException("Invalid property ledger");
        PropertyLedger data=new PropertyLedger();
        if(tag.getInt("schema")==2){for(var raw:ElectionNbt.list(tag,"villages")){var t=(CompoundTag)raw;if(data.rolls.putIfAbsent(t.getUUID("village"),ElectionNbt.read(t))!=null)throw new IllegalArgumentException("Duplicate village roll");}return data;}
        ElectionNbt.list(tag,"entries");var seen=new HashSet<Key>();
        for(Tag raw:tag.getList("entries",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)raw; long amount=t.getLong("stolen");
            if(!t.contains("stolen",Tag.TAG_LONG) || amount<=0 || !seen.add(new Key(t.getUUID("village"),t.getUUID("player"))))
                throw new IllegalArgumentException("Invalid duplicate property record");
            data.roll(t.getUUID("village")).theft(t.getUUID("player"),amount);
        }
        data.setDirty();return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema",2);ListTag entries=new ListTag();rolls.forEach((id,roll)->{var t=ElectionNbt.write(roll);t.putUUID("village",id);entries.add(t);});tag.put("villages",entries);return tag;
    }
}
