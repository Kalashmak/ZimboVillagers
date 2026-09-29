package org.villageastra.world;
import java.util.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
/** The laboratory's record file. Until AD-136 it held the paper chain of the desk (paper, binding, writing tool → volume, schema 1); since AD-136
 *  scientific works are written by the village clock ({@link ScienceWorks}, schema 2 in the same file). This class keeps the path and the one-time
 *  return of a schema-1 record: its paid materials and its outputs not yet put in the chest go back to the laboratory chest (CF4c). */
public final class ScienceWorkshop {
 private ScienceWorkshop(){}
 public static Path path(ServerLevel l,UUID building){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-science/"+building+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID building){var p=path(l,building);return Files.exists(p)?NbtRecord.read(p):new CompoundTag();}
 private static List<ItemStack> paid(CompoundTag t){var items=new ArrayList<ItemStack>();for(var raw:t.getList("paid",Tag.TAG_COMPOUND))items.add(ItemStack.of((CompoundTag)raw));return items;}
 /** What a schema-1 record still owes the chest: its paid inputs while funding or working, the outputs not yet deposited once it finished. */
 static List<ItemStack> owed(CompoundTag t){
  String stage=t.getString("stage");var paid=paid(t);
  if(stage.equals("fund")||stage.equals("work"))return paid.stream().filter(s->!s.isEmpty()).toList();
  if(stage.equals("output")){ScienceRecipe recipe;try{recipe=ScienceRecipe.valueOf(t.getString("recipe"));}catch(IllegalArgumentException ex){return List.of();}
   var outputs=recipe.outputs(paid);int from=Math.max(0,t.getInt("output"));return from>=outputs.size()?List.of():outputs.subList(from,outputs.size());}
  return List.of();
 }
 /** Returns a schema-1 record's items to the laboratory chest, each under its own journal id (science/&lt;lab&gt;/migrate/i), and removes the
  *  record. False (and the record stays) while the chest is unloaded or full: the next pass tries again, never twice for the same item. */
 public static boolean migrate(ServerLevel l,SettlementData.Entry e,Settlement.Building lab){
  var t=inspect(l,lab.id());if(t.getInt("schema")!=1)return true;
  var chest=LogisticsRoutes.chest(l,e,lab);if(chest==null)return false;var pos=LogisticsRoutes.position(e,lab);
  var items=owed(t);
  for(int i=0;i<items.size();i++)if(!WorldJournal.deposit(l,Settlement.childId(lab.id(),"science/migrate/"+i),pos,items.get(i)))return false;
  try{Files.deleteIfExists(path(l,lab.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  return true;
 }
}
