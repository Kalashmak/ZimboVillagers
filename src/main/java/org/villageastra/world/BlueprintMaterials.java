package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;
import org.villageastra.VillageAstra;

/** Material requirements of the model only; excavation, access and delivery are separate work. */
public final class BlueprintMaterials {
    private BlueprintMaterials() {}
    public static Map<String,Long> count(String design) {
        var cells=BuildingBlueprints.layout(design,BlockPos.ZERO);
        var total=new TreeMap<String,Long>();
        cells.forEach((pos,state)->cost(cells,pos,state).forEach((item,n)->total.merge(item,n,Math::addExact)));
        return Collections.unmodifiableMap(total);
    }
    private static Map<String,Long> cost(Map<BlockPos,BlockState> cells,BlockPos pos,BlockState state) {
        if(state.isAir()) return Map.of();
        Block block=state.getBlock();
        if(block instanceof DoorBlock) {
            boolean lower=state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER;
            BlockState partner=cells.get(lower?pos.above():pos.below());
            if(partner==null || partner.getBlock()!=block || partner.getValue(DoorBlock.HALF)==state.getValue(DoorBlock.HALF))
                throw new IllegalArgumentException("Incomplete door at "+pos);
            if(!lower)return Map.of();
        }
        if(block instanceof BedBlock) {
            boolean foot=state.getValue(BedBlock.PART)==BedPart.FOOT;
            var facing=state.getValue(BedBlock.FACING);
            BlockState partner=cells.get(pos.relative(foot?facing:facing.getOpposite()));
            if(partner==null || partner.getBlock()!=block || partner.getValue(BedBlock.PART)==state.getValue(BedBlock.PART)
                    || partner.getValue(BedBlock.FACING)!=facing)throw new IllegalArgumentException("Incomplete bed at "+pos);
            if(!foot)return Map.of();
        }
        if(block==VillageAstra.OWNED_CHEST.get())return Map.of("minecraft:chest",1L);
        if(block instanceof FlowerPotBlock pot && pot.getContent()!=Blocks.AIR)
            return Map.of("minecraft:flower_pot",1L,BuiltInRegistries.ITEM.getKey(pot.getContent().asItem()).toString(),1L);
        if(block.asItem()==Items.AIR)throw new IllegalArgumentException("Material mapping missing for "+block);
        long count=block instanceof SlabBlock && state.getValue(SlabBlock.TYPE)==SlabType.DOUBLE ? 2L : 1L;
        return Map.of(BuiltInRegistries.ITEM.getKey(block.asItem()).toString(),count);
    }
}
