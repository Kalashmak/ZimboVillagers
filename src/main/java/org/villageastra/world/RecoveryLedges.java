package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
/** Bounded, per-body memory prevents climbing back onto an unchanged isolated foothold. */
final class RecoveryLedges {
 private RecoveryLedges(){}
 private static final Map<ResidentEntity,LinkedHashMap<BlockPos,List<BlockState>>> memory=new WeakHashMap<>();
 private static List<BlockState> surroundings(ResidentEntity r,BlockPos p){
  var states=new ArrayList<BlockState>();
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=7;y++)states.add(r.level().getBlockState(p.offset(x,y,z)));
  return states;
 }
 static void remember(ResidentEntity r,BlockPos p){
  var known=memory.computeIfAbsent(r,k->new LinkedHashMap<>());known.remove(p);known.put(p.immutable(),surroundings(r,p));
  while(known.size()>16)known.remove(known.keySet().iterator().next());
 }
 static boolean deadEnd(ResidentEntity r,BlockPos p){
  var known=memory.get(r);if(known==null)return false;var previous=known.get(p);if(previous==null)return false;
  if(previous.equals(surroundings(r,p)))return true;
  known.remove(p);return false;
 }
}
