package org.villageastra.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** AD-121 preview: the town hall's castle shell for levels I..VI on the fixed 37x37 lot ({@link CastlePlan}).
 *  PREVIEW ONLY: nothing in the settlement, catalogue, research, layouts or construction uses it; only the gallery
 *  probe places the ids {@code castle_preview_1..6} so the owner can judge the look before any gameplay wiring. */
public final class CastleArchitecture {
    public static final String PREVIEW_PREFIX="castle_preview_";
    public static final int LOT=CastlePlan.LOT;
    private static final Map<String,BlockState> STATES=new ConcurrentHashMap<>();
    private CastleArchitecture() {}

    /** Blocks of the level's shell in world positions, lot corner at {@code base} (y of base = ground course), no rotation. */
    public static Map<BlockPos,BlockState> shell(int level,BlockPos base) {
        Map<BlockPos,BlockState> out=new LinkedHashMap<>();
        for(var e:CastlePlan.plan(level).entrySet()){var c=e.getKey();out.put(base.offset(c.x(),c.y(),c.z()),state(e.getValue(),level));}
        return FramedWindowBlock.join(out);
    }
    public static boolean isPreview(String id) { return id.startsWith(PREVIEW_PREFIX); }
    public static int previewLevel(String id) { return Integer.parseInt(id.substring(PREVIEW_PREFIX.length())); }

    private static BlockState state(String spec,int level) {
        if(spec.equals(CastlePlan.SEAL)){var core=Cores.state("town_hall",level);return core!=null?core:Blocks.CHISELED_STONE_BRICKS.defaultBlockState();}
        var s=STATES.computeIfAbsent(spec,CastleArchitecture::parse);
        // AD-121: the kit's chests are the village's own (its stock and its pages), as every hall chest.
        if(s.is(Blocks.CHEST))return org.villageastra.VillageAstra.OWNED_CHEST.get().defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,s.getValue(net.minecraft.world.level.block.ChestBlock.FACING)).setValue(net.minecraft.world.level.block.ChestBlock.TYPE,s.getValue(net.minecraft.world.level.block.ChestBlock.TYPE));
        return s;
    }
    private static BlockState parse(String spec) {
        try{return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),spec,false).blockState();}
        catch(CommandSyntaxException ex){throw new IllegalArgumentException("Castle block state "+spec,ex);}
    }
}
