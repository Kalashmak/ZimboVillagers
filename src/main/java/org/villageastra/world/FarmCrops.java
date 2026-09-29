package org.villageastra.world;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
/** Physical vanilla cultures; no timer output and no synthetic harvest. */
public enum FarmCrops {
 WHEAT(Items.WHEAT_SEEDS,Blocks.WHEAT),CARROT(Items.CARROT,Blocks.CARROTS),POTATO(Items.POTATO,Blocks.POTATOES),BEETROOT(Items.BEETROOT_SEEDS,Blocks.BEETROOTS),SUGAR_CANE(Items.SUGAR_CANE,Blocks.SUGAR_CANE);
 public final Item seed;public final Block block;
 FarmCrops(Item seed,Block block){this.seed=seed;this.block=block;}
 public String id(){return name().toLowerCase(java.util.Locale.ROOT);}
 public static FarmCrops from(String id){return valueOf(id.toUpperCase(java.util.Locale.ROOT));}
 public static FarmCrops pending(CompoundTag state){return state.contains("crop")?from(state.getString("crop")):WHEAT;}
 public boolean water(ServerLevel level,BlockPos soil){for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)if(level.getFluidState(soil.relative(d)).is(net.minecraft.tags.FluidTags.WATER))return true;return false;}
 public BlockState soil(){return this==SUGAR_CANE?Blocks.DIRT.defaultBlockState():Blocks.FARMLAND.defaultBlockState();}
 public boolean canPrepare(ServerLevel l,BlockPos p){var soil=l.getBlockState(p.below());return (soil.is(Blocks.DIRT)||soil.is(Blocks.GRASS_BLOCK)||soil.is(Blocks.FARMLAND))&&(this!=SUGAR_CANE||water(l,p.below()));}
 public static BlockPos harvest(ServerLevel l,BlockPos p){var b=l.getBlockState(p);if(b.getBlock() instanceof CropBlock crop&&crop.isMaxAge(b))return p;if(b.is(Blocks.SUGAR_CANE)&&l.getBlockState(p.above()).is(Blocks.SUGAR_CANE)){var top=p.above();while(top.getY()<p.getY()+3&&l.hasChunkAt(top.above())&&l.getBlockState(top.above()).is(Blocks.SUGAR_CANE))top=top.above();return l.getBlockState(top.above()).is(Blocks.SUGAR_CANE)?null:top;}return null;}
}
