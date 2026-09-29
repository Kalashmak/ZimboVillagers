package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.villageastra.server.SettlementData;
/** AD-157: physical tower materials follow paid building projects, never an instantaneous research repaint. */
public final class TowerStages {
 private TowerStages(){}
 public static int desired(ServerLevel l,SettlementData.Entry e){int d=Walls.defence(l,e);return d>=5?5:d>=4?4:2;}
 public static String design(ServerLevel l,SettlementData.Entry e){return Walls.TOWER+"@"+desired(l,e);}
 /** Bare/level-I towers are legacy stone towers. Their saved material must never regress. */
 public static Map<BlockPos,BlockState> palette(int level,BlockPos origin,Map<BlockPos,BlockState> cells){
  if(level<=1||level>=5)return cells;
  boolean wood=level<4;
  cells.replaceAll((pos,s)->{
   if(pos.getY()==origin.getY())return s; // A timber tower still stands on its stone footing.
   Block b=s.getBlock(),next=null;
   if(b==Blocks.STONE_BRICK_STAIRS||b==Blocks.COBBLESTONE_STAIRS||b==Blocks.ANDESITE_STAIRS)next=wood?Blocks.SPRUCE_STAIRS:Blocks.COBBLESTONE_STAIRS;
   else if(b==Blocks.STONE_BRICK_SLAB||b==Blocks.COBBLESTONE_SLAB||b==Blocks.ANDESITE_SLAB)next=wood?Blocks.SPRUCE_SLAB:Blocks.COBBLESTONE_SLAB;
   else if(b==Blocks.STONE_BRICKS||b==Blocks.CHISELED_STONE_BRICKS||b==Blocks.CRACKED_STONE_BRICKS||b==Blocks.MOSSY_STONE_BRICKS||b==Blocks.COBBLESTONE||b==Blocks.MOSSY_COBBLESTONE||b==Blocks.POLISHED_ANDESITE||b==Blocks.ANDESITE||b==Blocks.STONE)next=wood?Blocks.SPRUCE_PLANKS:Blocks.COBBLESTONE;
   if(next==null)return s;
   var out=next.defaultBlockState();for(var p:s.getProperties())if(out.hasProperty(p))out=copy(out,s,p);return out;
  });return cells;
 }
 private static <T extends Comparable<T>> BlockState copy(BlockState to,BlockState from,Property<T> p){return to.setValue(p,from.getValue(p));}
 /** One ordinary paid upgrade at a time, including manually placed registered towers without a wall record. */
 public static String follow(ServerLevel l,SettlementData.Entry e){
  if(HallUpgradeGoal.pending(l,e.settlement().id())||Sieges.besieged(l.getServer(),e.settlement().id()))return "";
  int target=desired(l,e);
  for(var b:e.settlement().buildings()){
   if(!b.type().equals(Walls.TOWER)||b.level()<=1||b.level()>=target)continue;
   var survey=BuildingOrders.survey(l,e,Walls.TOWER+"@"+target,b.rotation(),BuildingPlacement.origin(e,b),b);
   if(!survey.ok())continue;
   var state=survey.state();state.putBoolean("upgrade",true);state.remove("repair");state.putInt("upgradeLevel",target);
   HallUpgradeGoal.enqueue(l,e,state);SettlementData.get(l.getServer()).setDirty();return "tower_upgrade";
  }return "";
 }
}
