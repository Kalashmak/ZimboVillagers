package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
/** AD-138: the one way residents open and close fence gates of any wood (the pens of the livestock yard, a farm's field gate). The vanilla
 *  path finder never plans through a closed gate, so a worker walks to the gate, opens it, passes and closes it behind him; nothing else
 *  in the mod opens a gate. A gate is a block of the building like any other: opening it changes only its OPEN state. */
public final class Gates {
 private Gates(){}
 /** How close a worker stands to reach a gate (as a player's hand). */
 public static final double REACH_SQ=4.5*4.5;
 public static boolean gate(BlockState s){return s.getBlock() instanceof FenceGateBlock;}
 public static boolean isOpen(BlockState s){return gate(s)&&s.getValue(FenceGateBlock.OPEN);}
 public static boolean reaches(Mob m,BlockPos gate){return m.distanceToSqr(gate.getX()+.5,gate.getY()+.5,gate.getZ()+.5)<=REACH_SQ;}
 /** Opens the gate at pos with its vanilla sound and game event; true when it stands open afterwards (false: no gate there). */
 public static boolean open(ServerLevel l,BlockPos pos,Mob by){return set(l,pos,by,true);}
 /** Closes it again; true when it stands closed afterwards. */
 public static boolean close(ServerLevel l,BlockPos pos,Mob by){return set(l,pos,by,false);}
 private static boolean set(ServerLevel l,BlockPos pos,Mob by,boolean open){
  var s=l.getBlockState(pos);if(!gate(s))return false;if(s.getValue(FenceGateBlock.OPEN)==open)return true;
  l.setBlock(pos,s.setValue(FenceGateBlock.OPEN,open),10);
  l.playSound(null,pos,open?net.minecraft.sounds.SoundEvents.FENCE_GATE_OPEN:net.minecraft.sounds.SoundEvents.FENCE_GATE_CLOSE,SoundSource.BLOCKS,1F,l.random.nextFloat()*.1F+.9F);
  l.gameEvent(by,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
  return true;
 }
}
