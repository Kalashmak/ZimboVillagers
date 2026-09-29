package org.villageastra.persistence;

import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import java.util.*;

/** Receipts share the affected chunk's serialization, including after its chest is destroyed. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class ChunkReceipts implements ICapabilitySerializable<CompoundTag> {
    public static final Capability<ChunkReceipts> CAP=CapabilityManager.get(new CapabilityToken<>(){});
    private final Set<UUID> applied=new HashSet<>();
    private final LazyOptional<ChunkReceipts> self=LazyOptional.of(()->this);
    public boolean contains(UUID id) { return applied.contains(id); }
    public void add(UUID id) { applied.add(id); }
    public static ChunkReceipts of(LevelChunk chunk) {
        return chunk.getCapability(CAP).orElseThrow(()->new IllegalStateException("Missing chunk receipt capability"));
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        var receipts=new ChunkReceipts();
        event.addCapability(new ResourceLocation(VillageAstra.ID,"receipts"),receipts);
        event.addListener(receipts.self::invalidate);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) { return cap==CAP ? self.cast() : LazyOptional.empty(); }
    @Override public CompoundTag serializeNBT() {
        CompoundTag tag=new CompoundTag();tag.putInt("schema",1); ListTag ids=new ListTag();
        applied.stream().sorted().forEach(id->ids.add(NbtUtils.createUUID(id)));tag.put("applied",ids);return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        if(tag.getInt("schema")!=1 || !(tag.get("applied") instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType()!=Tag.TAG_INT_ARRAY)) throw new IllegalArgumentException("Invalid chunk receipts");
        applied.clear();
        for(Tag value:tag.getList("applied",Tag.TAG_INT_ARRAY)) if(!applied.add(NbtUtils.loadUUID(value))) throw new IllegalArgumentException("Duplicate chunk receipt");
    }
}
