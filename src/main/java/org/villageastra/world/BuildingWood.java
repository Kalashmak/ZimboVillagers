package org.villageastra.world;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.villageastra.server.SettlementData;

/** A new home's timber is chosen from real forestry trees, then frozen in its project and building record. */
public final class BuildingWood {
 private BuildingWood() {}
 private static final List<String> KINDS=List.of("dark_oak","spruce","birch","jungle","acacia","cherry","oak");
 public static String choose(ServerLevel l,SettlementData.Entry e,String design) {
  if(!BuildingOrders.HOUSING.contains(BuildingBlueprints.base(design)))return "";
  for(var hut:e.settlement().buildings())if(hut.type().equals("forester")){
   var tree=ForestWork.next(l,e,hut,BuildingLevels.level(l,e,hut),ForesterHut.door(e,hut),0,l.getGameTime());
   if(tree!=null&&tree.sapling()!=null){var key=BuiltInRegistries.ITEM.getKey(tree.sapling()).getPath();return key.substring(0,key.length()-"_sapling".length());}
  }
  // No observed supply: keep the quoted design rather than inventing a timber source.
  return "";
 }
 public static Map<BlockPos,BlockState> apply(Map<BlockPos,BlockState> source,String wood) {
  if(wood.isEmpty())return source;
  if(!KINDS.contains(wood))throw new IllegalArgumentException("Unknown building wood: "+wood);
  var out=new LinkedHashMap<BlockPos,BlockState>();source.forEach((pos,state)->out.put(pos,replace(state,wood)));return out;
 }
 public static BlockState replace(BlockState before,String wood) {
  if(wood.isEmpty())return before;
  var key=BuiltInRegistries.BLOCK.getKey(before.getBlock());var name=key.getPath();String stem=name.startsWith("stripped_")?"stripped_":"";
  for(var kind:KINDS)if(name.startsWith(stem+kind+"_")){
   var next=new ResourceLocation(key.getNamespace(),stem+wood+name.substring(stem.length()+kind.length()));
   if(!BuiltInRegistries.BLOCK.containsKey(next))return before;
   var after=BuiltInRegistries.BLOCK.get(next).defaultBlockState();
   for(var property:before.getProperties())if(after.hasProperty(property))after=copy(before,after,property);
   return after;
  }
  return before;
 }
 private static <T extends Comparable<T>> BlockState copy(BlockState from,BlockState to,Property<T> property){return to.setValue(property,from.getValue(property));}
}
