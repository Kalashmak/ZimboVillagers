package org.villageastra.world;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** The unpaid bill, shared by the builder's readiness check and physical withdrawals. */
public final class ConstructionFunding {
 private ConstructionFunding() {}
 public static Map<String,Integer> missing(CompoundTag state) {
  var result=new TreeMap<String,Integer>();var cost=state.getCompound("cost");
  for(var key:cost.getAllKeys())if(cost.getInt(key)>0)result.put(key,cost.getInt(key));
  for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)) {
   var stack=ItemStack.of((CompoundTag)raw);var key=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   result.computeIfPresent(key,(k,n)->n-stack.getCount());
  }
  result.values().removeIf(n->n<=0);return result;
 }
 /** Alphabetical order breaks ties only among materials actually available in the stock. */
 public static int slot(Container chest,Map<String,Integer> missing) {
  int chosen=-1;String first=null;
  for(int i=0;i<chest.getContainerSize();i++) {
   var stack=chest.getItem(i);if(stack.isEmpty())continue;
   var key=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
   if(missing.containsKey(key)&&(first==null||key.compareTo(first)<0)){chosen=i;first=key;}
  }
  return chosen;
 }
}
