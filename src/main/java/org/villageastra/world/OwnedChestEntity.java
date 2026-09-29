package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.*;
import org.villageastra.VillageAstra;

/** NPCs use the real Container; unknown automation can insert but cannot extract. */
public final class OwnedChestEntity extends ChestBlockEntity implements WorldlyContainer {
    private int hallSlot(int slot){return getPersistentData().getInt("AstraHallOffset")+slot;}
    @Override public ItemStack getItem(int slot){var master=HallStorage.master(this);return master==null?super.getItem(slot):master.getItem(hallSlot(slot));}
    @Override public void setItem(int slot,ItemStack stack){var master=HallStorage.master(this);if(master==null)super.setItem(slot,stack);else master.setItem(hallSlot(slot),stack);}
    @Override public ItemStack removeItem(int slot,int amount){var master=HallStorage.master(this);return master==null?super.removeItem(slot,amount):master.removeItem(hallSlot(slot),amount);}
    @Override public ItemStack removeItemNoUpdate(int slot){var master=HallStorage.master(this);return master==null?super.removeItemNoUpdate(slot):master.removeItemNoUpdate(hallSlot(slot));}
    /** AD-147: the slots of a store master (the hall's 108, a warehouse's 54 a page); 0 for an ordinary chest of 27. */
    private int storeSlots;
    @Override public int getContainerSize(){return storeSlots>0?storeSlots:27;}
    /** The hall's store of two pages (HallStorage). */
    public void expandHall(){expand(108);}
    /** AD-147: grows the master to this many slots, keeping every item in its slot; it never shrinks. */
    public void expand(int slots){if(slots<=getContainerSize())return;var old=getItems();var expanded=net.minecraft.core.NonNullList.withSize(slots,ItemStack.EMPTY);for(int i=0;i<old.size();i++)expanded.set(i,old.get(i));storeSlots=slots;setItems(expanded);setChanged();}
    /** An older save names the hall's store by "AstraHallStorage" (108 slots); AD-147 saves the size as "AstraStoreSlots". */
    @Override public void load(net.minecraft.nbt.CompoundTag tag){storeSlots=tag.contains("AstraStoreSlots")?tag.getInt("AstraStoreSlots"):tag.getBoolean("AstraHallStorage")?108:0;super.load(tag);}
    @Override protected void saveAdditional(net.minecraft.nbt.CompoundTag tag){super.saveAdditional(tag);tag.putBoolean("AstraHallStorage",storeSlots>0);if(storeSlots>0)tag.putInt("AstraStoreSlots",storeSlots);}
    private LazyOptional<IItemHandler> automation=createAutomation();
    private LazyOptional<IItemHandler> createAutomation() { return LazyOptional.of(()->new net.minecraftforge.items.wrapper.InvWrapper(this) {
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { return ItemStack.EMPTY; }
    }); }
    public OwnedChestEntity(BlockPos pos,BlockState state) { super(VillageAstra.OWNED_CHEST_ENTITY.get(),pos,state); }
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory) { return storeSlots>0?new OwnedChestMenu(id,inventory,this,0):new OwnedChestMenu(id,inventory,this); }
    @Override public int[] getSlotsForFace(Direction direction) { return java.util.stream.IntStream.range(0,getContainerSize()).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction direction) { return true; }
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction direction) { return false; }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap,Direction side) {
        return cap==ForgeCapabilities.ITEM_HANDLER ? automation.cast() : super.getCapability(cap,side);
    }
    @Override public void reviveCaps() { super.reviveCaps();automation=createAutomation(); }
    @Override public void invalidateCaps() { super.invalidateCaps(); automation.invalidate(); }
}
