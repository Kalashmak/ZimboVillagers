package org.villageastra.persistence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** Write intent -> mutate with chunk receipt -> flush chunk -> commit; replay uses the same ID. */
public final class WorldJournal {
    private WorldJournal() {}
    private static int batchDepth;
    private static final java.util.List<Object[]> PENDING=new java.util.ArrayList<>();
    /** AD-111: every operation inside {@code body} shares one chunk flush and then writes its commit; server thread only. A crash before the flush
     *  replays each intent against its 'before' (no receipt) or finishes it (receipt, not committed); the ids are the same as without a batch. */
    public static <T> T batch(ServerLevel level,java.util.function.Supplier<T> body){
        if(!level.getServer().isSameThread()) throw new IllegalStateException("World journal must run on server thread");
        batchDepth++;Throwable failure=null;
        try{return body.get();}
        catch(Throwable error){failure=error;throw error;}
        finally{
            if(--batchDepth==0&&!PENDING.isEmpty()){
                try{
                    level.getChunkSource().save(true);crashBoundary(level,"after_chunk_flush");
                    for(var p:PENDING){var intent=(CompoundTag)p[1];intent.putBoolean("committed",true);write((Path)p[0],intent);}
                }catch(IOException|RuntimeException error){
                    var wrapped=error instanceof RuntimeException r?r:new IllegalStateException("Astra world journal blocked a batch commit",error);
                    if(failure!=null)failure.addSuppressed(wrapped);else throw wrapped;
                }finally{PENDING.clear();}
            }
        }
    }
    private static Path path(ServerLevel level,UUID id) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-journal").resolve(id+".bin");
    }
    private static CompoundTag read(Path file) throws IOException {
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(file))));
    }
    private static void write(Path file,CompoundTag record) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        NbtIo.write(record,new DataOutputStream(bytes));AtomicRecord.write(file,bytes.toByteArray());
    }
    public static ItemStack take(ServerLevel level,UUID id,BlockPos pos,int slot,ItemStack before) {
        // AD-137: a new withdrawal from the hall stock leaves the active project's reserve to its builder (a replay is never cut).
        if(!exists(level,id)&&org.villageastra.world.HallReserve.allow(level,id,pos,before,1)<1)return ItemStack.EMPTY;
        CompoundTag intent=base(level,id,pos,"take");intent.putInt("slot",slot);
        intent.put("before",before.save(new CompoundTag()));
        ItemStack after=before.copy();after.shrink(1);intent.put("after",after.save(new CompoundTag()));
        CompoundTag applied=execute(level,id,intent);
        return applied==null ? ItemStack.EMPTY : ItemStack.of(applied.getCompound("before")).copyWithCount(1);
    }
    public static ItemStack takeAmount(ServerLevel level,UUID id,BlockPos pos,int slot,ItemStack before,int amount){
        if(amount<=0||amount>before.getCount())throw new IllegalArgumentException("Invalid withdrawal amount");
        if(!exists(level,id)){amount=org.villageastra.world.HallReserve.allow(level,id,pos,before,amount);if(amount<=0)return ItemStack.EMPTY;}
        CompoundTag intent=base(level,id,pos,"take");intent.putInt("slot",slot);intent.put("before",before.save(new CompoundTag()));
        ItemStack after=before.copy();after.shrink(amount);intent.put("after",after.save(new CompoundTag()));
        return execute(level,id,intent)==null?ItemStack.EMPTY:before.copyWithCount(amount);
    }
    public static ItemStack recoverAmount(ServerLevel level,UUID id){
        if(!exists(level,id))return ItemStack.EMPTY;
        try{var intent=read(path(level,id));if(!intent.getString("kind").equals("take"))throw new IOException("Not a withdrawal");
            var applied=execute(level,id,intent);if(applied==null)return ItemStack.EMPTY;
            var before=ItemStack.of(applied.getCompound("before"));int count=before.getCount()-ItemStack.of(applied.getCompound("after")).getCount();
            if(count<=0)throw new IOException("Invalid cargo size");return before.copyWithCount(count);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    /** Replay an existing debit without selecting a new slot after a crash. */
    public static boolean exists(ServerLevel level,UUID id) { return Files.exists(path(level,id)); }
    public static ItemStack recoverTake(ServerLevel level,UUID id) {
        Path file=path(level,id); if(!Files.exists(file)) return ItemStack.EMPTY;
        try {
            CompoundTag intent=read(file);
            if(!intent.getString("kind").equals("take")) throw new IOException("Wrong operation kind");
            CompoundTag applied=execute(level,id,intent);
            return applied==null ? ItemStack.EMPTY : ItemStack.of(applied.getCompound("before")).copyWithCount(1);
        } catch(IOException e) { throw new IllegalStateException("Cannot recover debit "+id,e); }
    }
    public static boolean place(ServerLevel level,UUID id,BlockPos pos,BlockState before,BlockState after) {
        CompoundTag intent=base(level,id,pos,"block");intent.put("before",NbtUtils.writeBlockState(before));intent.put("after",NbtUtils.writeBlockState(after));
        if(before.is(net.minecraft.world.level.block.Blocks.OAK_WALL_SIGN))try{
            if(Files.exists(path(level,id))){var recorded=read(path(level,id));if(recorded.hasUUID("signBuilding"))intent.putUUID("signBuilding",recorded.getUUID("signBuilding"));}
            else{var sign=org.villageastra.world.BuildingSigns.target(level,pos);if(sign!=null)intent.putUUID("signBuilding",sign.building().id());}
        }catch(IOException ex){throw new IllegalStateException("Cannot recover sign ownership "+id,ex);}
        return execute(level,id,intent)!=null;
    }
    /** AD-125: takes one block of a building that moves apart without a drop or a neighbour cascade (flags 18). It commits only while the cell
     *  holds {@code before}; a block entity is allowed only when it is no container or an empty one, so nothing ever spills. */
    public static boolean dismantle(ServerLevel level,UUID id,BlockPos pos,BlockState before,BlockState after){
        CompoundTag intent=base(level,id,pos,"dismantle");intent.put("before",NbtUtils.writeBlockState(before));intent.put("after",NbtUtils.writeBlockState(after));
        return execute(level,id,intent)!=null;
    }
    /** Exact, pre-recorded loot of one finite natural block; no world item drops are spawned. */
    public static java.util.List<ItemStack> harvest(ServerLevel level,UUID id,BlockPos pos,BlockState before,ItemStack tool) {
        try {
            CompoundTag intent;
            if(Files.exists(path(level,id))) intent=read(path(level,id));
            else {
                intent=base(level,id,pos,"block");intent.put("before",NbtUtils.writeBlockState(before));
                intent.put("after",NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
                ListTag loot=new ListTag();
                for(ItemStack stack:net.minecraft.world.level.block.Block.getDrops(before,level,pos,null,null,tool))loot.add(stack.save(new CompoundTag()));
                intent.put("loot",loot);
            }
            if(!intent.contains("loot"))throw new IOException("Not a harvest receipt");
            CompoundTag applied=execute(level,id,intent);
            if(applied==null)return null;
            // Replaying an old harvest must not retire a newer plug installed in this cell.
            if(level.getBlockState(pos).isAir())org.villageastra.world.MinePlugs.harvested(level,pos);
            java.util.List<ItemStack> result=new java.util.ArrayList<>();
            for(Tag raw:applied.getList("loot",Tag.TAG_COMPOUND))result.add(ItemStack.of((CompoundTag)raw));
            return result;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static boolean deposit(ServerLevel level,UUID id,BlockPos pos,ItemStack stack) {
        try {
            if(Files.exists(path(level,id)))return execute(level,id,read(path(level,id)))!=null;
            if(!(level.getBlockEntity(pos) instanceof Container container))return false;
            for(int slot=0;slot<container.getContainerSize();slot++) {
                ItemStack before=container.getItem(slot);
                if(!before.isEmpty() && !ItemStack.isSameItemSameTags(before,stack))continue;
                int count=before.getCount()+stack.getCount();
                if(count>Math.min(container.getMaxStackSize(),stack.getMaxStackSize()))continue;
                CompoundTag intent=base(level,id,pos,"inventory");intent.putInt("slot",slot);
                intent.put("before",before.save(new CompoundTag()));intent.put("after",stack.copyWithCount(count).save(new CompoundTag()));
                return execute(level,id,intent)!=null;
            }
            return false;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    /** Read a committed receipt without replaying it or loading its dependency chunk. */
    public static CompoundTag inspectCommitted(ServerLevel level,UUID id){
        if(!exists(level,id))return null;
        try{var r=read(path(level,id));return r.getInt("schema")==1&&r.hasUUID("id")&&r.getUUID("id").equals(id)&&r.getString("dimension").equals(level.dimension().location().toString())&&r.getBoolean("committed")?r:null;}
        catch(IOException e){throw new IllegalStateException(e);}
    }
    /** Reconcile an already recorded operation; never selects new resources or a new intent. */
    public static CompoundTag recoverExisting(ServerLevel level,UUID id){
        if(!exists(level,id))return null;
        try{
            var receipt=read(path(level,id));
            if(receipt.getInt("schema")!=1||!receipt.getUUID("id").equals(id)||!receipt.getString("dimension").equals(level.dimension().location().toString()))throw new IOException("Invalid existing receipt");
            var pos=BlockPos.of(receipt.getLong("pos"));
            // Only an already recorded server operation can load a dependency chunk for reconciliation.
            if(!level.hasChunkAt(pos))level.getChunk(pos.getX()>>4,pos.getZ()>>4);
            return execute(level,id,receipt);
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    /** Container contents and the receipt live in the same chunk transaction. No free container item. */
    public static boolean dropCargo(ServerLevel level,UUID id,BlockPos pos,UUID settlement,ListTag contents){
        if(contents.size()>27)throw new IllegalArgumentException("Cargo container capacity exceeded");
        var intent=base(level,id,pos,"cargo");intent.putUUID("settlement",settlement);intent.put("contents",contents.copy());
        return execute(level,id,intent)!=null;
    }
    private static CompoundTag base(ServerLevel level,UUID id,BlockPos pos,String kind) {
        CompoundTag intent=new CompoundTag();intent.putInt("schema",1);intent.putUUID("id",id);
        intent.putString("dimension",level.dimension().location().toString());intent.putLong("pos",pos.asLong());intent.putString("kind",kind);return intent;
    }
    private static void crashBoundary(ServerLevel level,String boundary) {
        if (!boundary.equals(System.getProperty("villageastra.journalCrash"))) return;
        var root=level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!Boolean.getBoolean("villageastra.smoke") || !root.getFileName().toString().matches("astra-smoke-[0-9]+")
                || !Files.exists(root.resolve("data/journal-crash-probe.bin"))) throw new IllegalStateException("Crash injection requires an isolated harness world");
        System.out.println("ASTRA_CRASH_BOUNDARY "+boundary+" world="+root);System.out.flush();
        Runtime.getRuntime().halt(86);
    }
    /** Authenticated workstation transfer into a particular real inventory slot. */
    public static boolean putSlot(ServerLevel level,UUID id,BlockPos pos,int slot,ItemStack stack){
        try{if(Files.exists(path(level,id)))return execute(level,id,read(path(level,id)))!=null;
            if(!(level.getBlockEntity(pos) instanceof Container c)||slot<0||slot>=c.getContainerSize()||stack.isEmpty())return false;var before=c.getItem(slot);if(!before.isEmpty()&&!ItemStack.isSameItemSameTags(before,stack)||before.getCount()+stack.getCount()>stack.getMaxStackSize())return false;
            var t=base(level,id,pos,"inventory");t.putInt("slot",slot);t.put("before",before.save(new CompoundTag()));t.put("after",stack.copyWithCount(before.getCount()+stack.getCount()).save(new CompoundTag()));return execute(level,id,t)!=null;
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    /** Move paid cooking ticks into a real furnace once. Vanilla decrements the timer before cooking, hence its one-tick sentinel. */
    public static boolean furnaceCredit(ServerLevel level,UUID id,BlockPos pos,int ticks){
        try{if(Files.exists(path(level,id)))return execute(level,id,read(path(level,id)))!=null;var t=base(level,id,pos,"furnace_credit");t.putInt("ticks",ticks);return execute(level,id,t)!=null;}catch(IOException ex){throw new IllegalStateException(ex);}
    }
    private static CompoundTag execute(ServerLevel level,UUID id,CompoundTag proposed) {
        if(!level.getServer().isSameThread()) throw new IllegalStateException("World journal must run on server thread");
        try {
            Path file=path(level,id);CompoundTag intent;
            if(Files.exists(file)) {
                intent=read(file);
                var expected=proposed.copy();expected.remove("committed");var actual=intent.copy();actual.remove("committed");
                if(!actual.equals(expected)) throw new IOException("Operation ID reused with different intent");
            } else { intent=proposed.copy();write(file,intent); crashBoundary(level,"after_intent"); }
            if(intent.getInt("schema")!=1 || !intent.getUUID("id").equals(id)
                    || !intent.getString("dimension").equals(level.dimension().location().toString())) throw new IOException("Invalid journal identity");
            BlockPos pos=BlockPos.of(intent.getLong("pos"));
            if(!level.hasChunkAt(pos)) return null;
            var chunk=level.getChunkAt(pos);var receipts=ChunkReceipts.of(chunk);
            if(receipts.contains(id) && intent.getBoolean("committed")) return intent;
            if(!receipts.contains(id)) {
                if(intent.getBoolean("committed")) throw new IOException("Committed operation lost chunk receipt; refusing replay");
                if(intent.getString("kind").equals("take") || intent.getString("kind").equals("inventory")) {
                    if(!(level.getBlockEntity(pos) instanceof Container container)) return null;
                    int slot=intent.getInt("slot");
                    ItemStack before=ItemStack.of(intent.getCompound("before"));
                    if(slot<0 || slot>=container.getContainerSize() || (before.isEmpty() && intent.getString("kind").equals("take"))
                            || !ItemStack.matches(container.getItem(slot),before)) return null;
                    container.setItem(slot,ItemStack.of(intent.getCompound("after")));container.setChanged();
                } else if(intent.getString("kind").equals("furnace_credit")) {
                    if(!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace))return null;var data=furnace.saveWithoutMetadata();int ticks=intent.getInt("ticks");if(ticks<1||ticks>32766||data.getShort("BurnTime")>0)return null;data.putShort("BurnTime",(short)(ticks+1));furnace.load(data);furnace.setChanged();level.setBlock(pos,level.getBlockState(pos).setValue(net.minecraft.world.level.block.FurnaceBlock.LIT,true),3);
                } else if(intent.getString("kind").equals("block")) {
                    BlockState before=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),intent.getCompound("before"));
                    BlockState after=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),intent.getCompound("after"));
                    if(!level.getBlockState(pos).equals(before))return null;
                    if(level.getBlockEntity(pos)!=null){var sign=org.villageastra.world.BuildingSigns.target(level,pos);if(sign==null||!intent.hasUUID("signBuilding")||!sign.building().id().equals(intent.getUUID("signBuilding")))return null;}
                    if(!level.setBlock(pos,after,3))return null;
                } else if(intent.getString("kind").equals("dismantle")) {
                    BlockState before=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),intent.getCompound("before"));
                    BlockState after=NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),intent.getCompound("after"));
                    var entity=level.getBlockEntity(pos);
                    if(!level.getBlockState(pos).equals(before) || entity instanceof Container c && !c.isEmpty()) return null;
                    if(entity!=null)level.removeBlockEntity(pos);
                    if(!level.setBlock(pos,after,net.minecraft.world.level.block.Block.UPDATE_CLIENTS|net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE)) return null;
                } else if(intent.getString("kind").equals("cargo")) {
                    if(!level.getBlockState(pos).isAir()||level.getBlockEntity(pos)!=null)return null;
                    if(!level.setBlock(pos,org.villageastra.VillageAstra.CARGO_CHEST.get().defaultBlockState(),3))return null;
                    if(!(level.getBlockEntity(pos) instanceof org.villageastra.world.OwnedChestEntity container))throw new IOException("Cargo container missing");
                    var contents=intent.getList("contents",Tag.TAG_COMPOUND);
                    if(contents.size()>container.getContainerSize())throw new IOException("Cargo overflow");
                    for(int i=0;i<contents.size();i++)container.setItem(i,ItemStack.of(contents.getCompound(i)));
                    container.getPersistentData().putUUID("AstraSettlement",intent.getUUID("settlement"));
                    container.getPersistentData().putUUID("AstraCargo",id);container.setChanged();
                } else throw new IOException("Unknown world mutation");
                receipts.add(id);chunk.setUnsaved(true);
            }
            // AD-111: inside batch() the flush and the commit wait for the batch's end; a receipt without committed replays through the branch above.
            if(batchDepth>0){if(!intent.getBoolean("committed"))PENDING.add(new Object[]{file,intent});return intent;}
            // Conservative durability boundary outside a batch.
            level.getChunkSource().save(true);
            crashBoundary(level,"after_chunk_flush");
            if(!intent.getBoolean("committed")) {intent.putBoolean("committed",true);write(file,intent);}
            return intent;
        } catch(IOException error) { throw new IllegalStateException("Astra world journal blocked operation "+id,error); }
    }
}
