package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** AD-122: a village miner breaks a block exactly as fast as a player holding the same tool — vanilla's Player.getDestroySpeed and
 *  BlockBehaviour.getDestroyProgress, without the player's own modifiers (haste, fatigue, water, air). Per tick a block gains
 *  speed / hardness / 30 with a tool that harvests it (the tool's tier speed, plus efficiency² + 1), or / 100 without; it breaks on the
 *  tick its progress reaches one. A stone pickaxe takes 12 ticks for stone and 23 for deepslate, an iron one 8 for stone. */
public final class MinerSpeed {
    private MinerSpeed(){}
    /** Ticks to break this block with this tool; at least 1, Integer.MAX_VALUE for an unbreakable block. */
    public static int breakTicks(BlockState state,BlockGetter level,BlockPos pos,ItemStack tool){
        float hardness=state.getDestroySpeed(level,pos);
        if(hardness<0)return Integer.MAX_VALUE;
        if(hardness==0)return 1;
        float speed=tool.getDestroySpeed(state);
        if(speed>1){int efficiency=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY,tool);if(efficiency>0)speed+=efficiency*efficiency+1;}
        boolean harvests=!state.requiresCorrectToolForDrops()||tool.isCorrectToolForDrops(state);
        float progress=speed/hardness/(harvests?30f:100f);
        return Math.max(1,(int)Math.ceil(1/progress-1e-6));
    }
    /** AD-122 (owner): a worker at a block looks like a player at it — the arm swings (LivingEntity.swing keeps vanilla's cadence), the
     *  crack (destroy stage 0..9) grows over the break time and is sent when it changes, and the block's hit sound plays every 4 ticks. */
    public static void progress(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.LivingEntity who,BlockPos pos,BlockState state,int labor,int total){
        who.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        int stage=stage(labor,total);if(labor<=1||stage!=stage(labor-1,total))level.destroyBlockProgress(who.getId(),pos,stage);
        if(labor%4==1&&!state.isAir()){var sound=state.getSoundType();level.playSound(null,pos,sound.getHitSound(),net.minecraft.sounds.SoundSource.BLOCKS,(sound.getVolume()+1)/8f,sound.getPitch()*.5f);}
    }
    /** The crack stage a player's client shows at this share of the break time. */
    public static int stage(int labor,int total){return total<=0?9:Math.max(0,Math.min(9,(int)((long)labor*10/total)));}
    /** The block is out: its crack goes, and the break sound and particles of vanilla's level event 2001 play. */
    public static void broken(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.LivingEntity who,BlockPos pos,BlockState state){
        level.destroyBlockProgress(who.getId(),pos,-1);if(!state.isAir())level.levelEvent(2001,pos,net.minecraft.world.level.block.Block.getId(state));
    }
    /** The worker left the block unbroken: its crack goes. */
    public static void clear(net.minecraft.server.level.ServerLevel level,net.minecraft.world.entity.LivingEntity who,BlockPos pos){level.destroyBlockProgress(who.getId(),pos,-1);}
}
