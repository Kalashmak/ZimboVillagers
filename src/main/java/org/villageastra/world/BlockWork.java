package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** P1 funded block-work probe; one entity owns both job and carried ItemStack. */
public final class BlockWork {
    public enum Stage { FETCH, DELIVER, WORK, COMPLETE, CONFLICT }
    private final UUID id;
    private final String dimension;
    private final BlockPos source;
    private final BlockPos target;
    private final BlockState before;
    private final BlockState after;
    private Stage stage;
    private ItemStack carried;
    private int labor;
    private UUID claimedWorker;

    public BlockWork(String dimension, BlockPos source, BlockPos target, BlockState before, BlockState after) {
        this(UUID.randomUUID(),dimension,source,target,before,after,Stage.FETCH,ItemStack.EMPTY,0);
        if (!before.isAir() || after.isAir() || after.hasBlockEntity() || after.getBlock().asItem() == net.minecraft.world.item.Items.AIR)
            throw new IllegalArgumentException("Probe requires an empty target and a simple placeable block");
    }
    private BlockWork(UUID id, String dimension, BlockPos source, BlockPos target, BlockState before, BlockState after, Stage stage, ItemStack carried, int labor) {
        this.id=id; this.dimension=dimension; this.source=source.immutable(); this.target=target.immutable(); this.before=before;
        this.after=after; this.stage=stage; this.carried=carried; this.labor=labor;
    }
    public UUID id() { return id; }
    public Stage stage() { return stage; }
    public ItemStack carried() { return carried; }
    public boolean active() { return stage != Stage.COMPLETE && stage != Stage.CONFLICT; }
    public void step(ResidentEntity worker, boolean simulationActive) {
        if (!simulationActive || !active() || !(worker.level() instanceof ServerLevel level)) return;
        if (!level.dimension().location().toString().equals(dimension)) return;
        if(org.villageastra.persistence.WorkClaim.retired(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-journal"),id)){carried=ItemStack.EMPTY;stage=Stage.CONFLICT;return;}
        if(claimedWorker==null) {
            org.villageastra.persistence.WorkClaim.acquire(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data/astra-journal"),id,worker.getUUID());
            claimedWorker=worker.getUUID();
        } else if(!claimedWorker.equals(worker.getUUID())) throw new IllegalStateException("Work owner mismatch");
        // A chest is solid: asking navigation to stand inside it can produce an incomplete path outside the wall.
        BlockPos goal = stage == Stage.FETCH ? source.east() : target;
        if (!level.hasChunkAt(goal)) return;
        double reach = stage == Stage.FETCH ? 2.25 : 9;
        if (worker.distanceToSqr(goal.getX()+0.5,goal.getY()+0.5,goal.getZ()+0.5) > reach) {
            if (worker.tickCount % 10 == 0) worker.getNavigation().moveTo(goal.getX()+0.5,goal.getY(),goal.getZ()+0.5,0.8);
            return;
        }
        worker.getNavigation().stop();
        if (stage == Stage.FETCH) {
            carried=org.villageastra.persistence.WorldJournal.recoverTake(level,org.villageastra.domain.Settlement.childId(id,"fetch"));
            if(!carried.isEmpty()) { stage=Stage.DELIVER;return; }
            if(org.villageastra.persistence.WorldJournal.exists(level,org.villageastra.domain.Settlement.childId(id,"fetch"))) { stage=Stage.CONFLICT;return; }
            if (!(level.getBlockEntity(source) instanceof Container container)) return;
            for (int slot=0; slot<container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.is(after.getBlock().asItem())) {
                    carried = org.villageastra.persistence.WorldJournal.take(level,org.villageastra.domain.Settlement.childId(id,"fetch"),source,slot,stack.copy());
                    container.setChanged();
                    if (!carried.isEmpty()) stage=Stage.DELIVER;
                    return;
                }
            }
        } else if (stage == Stage.DELIVER) stage=Stage.WORK;
        else if (stage == Stage.WORK) {
            if (++labor < 20) return;
            if (!carried.is(after.getBlock().asItem()) || carried.getCount()!=1) { stage=Stage.CONFLICT; return; }
            if (!org.villageastra.persistence.WorldJournal.place(level,org.villageastra.domain.Settlement.childId(id,"place"),target,before,after)) { stage=Stage.CONFLICT; return; }
            carried=ItemStack.EMPTY;
            stage=Stage.COMPLETE;
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
    public CompoundTag save() {
        CompoundTag tag=new CompoundTag();
        tag.putInt("schema",1); tag.putUUID("id",id); tag.putLong("source",source.asLong()); tag.putLong("target",target.asLong());
        tag.putString("dimension",dimension);
        tag.put("before",NbtUtils.writeBlockState(before)); tag.put("after",NbtUtils.writeBlockState(after));
        tag.putString("stage",stage.name()); tag.put("carried",carried.save(new CompoundTag())); tag.putInt("labor",labor);
        return tag;
    }
    public static BlockWork load(CompoundTag tag) {
        if (tag.getInt("schema")!=1 || tag.getInt("labor")<0 || net.minecraft.resources.ResourceLocation.tryParse(tag.getString("dimension"))==null)
            throw new IllegalArgumentException("Invalid block work save");
        return new BlockWork(tag.getUUID("id"),tag.getString("dimension"),BlockPos.of(tag.getLong("source")),BlockPos.of(tag.getLong("target")),
                NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag.getCompound("before")),
                NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag.getCompound("after")),
                Stage.valueOf(tag.getString("stage")),ItemStack.of(tag.getCompound("carried")),tag.getInt("labor"));
    }
}
