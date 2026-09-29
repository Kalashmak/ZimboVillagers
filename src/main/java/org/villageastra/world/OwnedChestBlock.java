package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.villageastra.VillageAstra;

/** A single physical chest; ownership cannot be merged with a neighbouring container. */
public final class OwnedChestBlock extends ChestBlock {
    public OwnedChestBlock() { this(false); }
    public OwnedChestBlock(boolean cargo) { super(cargo?BlockBehaviour.Properties.copy(Blocks.CHEST).noLootTable():BlockBehaviour.Properties.copy(Blocks.CHEST),()->VillageAstra.OWNED_CHEST_ENTITY.get()); }
    @Override public net.minecraft.world.MenuProvider getMenuProvider(BlockState state,Level level,BlockPos pos){var hall=HallStorage.menu(level,pos);return hall!=null?hall:super.getMenuProvider(state,level,pos);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new OwnedChestEntity(pos,state); }
    /** AD-147: a broken page of a store drops the master's slots it shows (dropContents empties those stacks in place); the master is marked
     *  changed so that a master in another chunk is saved without them - nothing is dropped and kept at once. */
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        var master=!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof OwnedChestEntity part?HallStorage.master(part):null;
        super.onRemove(state,level,pos,next,moving);
        if(master!=null)master.setChanged();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(TYPE,ChestType.SINGLE);
    }
    /** Level-V kit (AD-148): a lantern stands on the building's chest. A vanilla chest top (the lid at 14 px) supports nothing, so a
     *  neighbour update dropped the lantern and the building lost level V; the lid carries a small block as a crate would (the top face
     *  is sturdy for a centre support — a lantern, a candle, a torch — not for a full face). Only the support shape changes. */
    private static final net.minecraft.world.phys.shapes.VoxelShape LID=Block.box(1,14,1,15,16,15);
    @Override public net.minecraft.world.phys.shapes.VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){
        return net.minecraft.world.phys.shapes.Shapes.or(super.getBlockSupportShape(state,level,pos),LID);
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState other,LevelAccessor level,BlockPos pos,BlockPos neighbor) {
        if(state.getValue(TYPE)!=ChestType.SINGLE)return super.updateShape(state,side,other,level,pos,neighbor);
        return super.updateShape(state,side,other,level,pos,neighbor).setValue(TYPE,ChestType.SINGLE);
    }
}
