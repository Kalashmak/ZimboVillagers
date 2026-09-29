package org.villageastra.world;
import java.util.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.ProductionBalance;
/** Manual recipes from v0.7 balance.json; each output has a real input and productive labor. */
public enum ScienceRecipe {
 PAPER("paper"),WOOD_BINDING("wood_binding"),LEATHER_BINDING("leather_binding_alternative"),WRITING_TOOL("writing_tool"),VOLUME("research_volume");
 public final long ticks;
 private final String recipe;
 ScienceRecipe(String recipe){this.recipe=recipe;this.ticks=ProductionBalance.workTicks(recipe);}
 private int input(String id){return ProductionBalance.recipe(recipe,"inputs",id);}
 private int output(String id){return ProductionBalance.recipe(recipe,"outputs",id);}
 public record Input(java.util.function.Predicate<ItemStack> matches,int amount){}
 private static Input item(Item item,int amount){return new Input(s->s.is(item),amount);}
 public List<Input> inputs(){return switch(this){
  case PAPER->List.of(item(Items.SUGAR_CANE,input("sugar_cane")));
  case WOOD_BINDING->List.of(new Input(s->s.is(net.minecraft.tags.ItemTags.PLANKS),input("planks")));
  case LEATHER_BINDING->List.of(item(Items.LEATHER,input("leather")));
  case WRITING_TOOL->List.of(item(Items.STICK,input("stick")),item(Items.CHARCOAL,input("charcoal")));
  case VOLUME->List.of(item(Items.PAPER,input("paper")),item(VillageAstra.RESEARCH_BINDING.get(),input("research_binding")),new Input(s->s.is(VillageAstra.WRITING_TOOL.get())&&s.getDamageValue()>=0&&s.getDamageValue()+ProductionBalance.recipe(recipe,"tool_use","writing_tool")<=ProductionBalance.bookUses(),1));
 };}
 public List<ItemStack> outputs(List<ItemStack> paid){return switch(this){
  case PAPER->List.of(new ItemStack(Items.PAPER,output("paper")));
  case WOOD_BINDING,LEATHER_BINDING->List.of(new ItemStack(VillageAstra.RESEARCH_BINDING.get(),output("research_binding")));
  case WRITING_TOOL->List.of(new ItemStack(VillageAstra.WRITING_TOOL.get(),output("writing_tool")));
  case VOLUME->{var out=new ArrayList<ItemStack>();out.add(new ItemStack(VillageAstra.RESEARCH_VOLUME.get(),output("research_volume")));var tool=paid.stream().filter(s->s.is(VillageAstra.WRITING_TOOL.get())).findFirst().orElseThrow().copyWithCount(1);tool.setDamageValue(tool.getDamageValue()+ProductionBalance.recipe(recipe,"tool_use","writing_tool"));if(tool.getDamageValue()<ProductionBalance.bookUses())out.add(tool);yield List.copyOf(out);}
 };}
 public boolean supplied(Container chest){return inputs().stream().allMatch(in->{int n=0;for(int i=0;i<chest.getContainerSize();i++)if(in.matches.test(chest.getItem(i)))n+=chest.getItem(i).getCount();return n>=in.amount;});}
 public static ScienceRecipe choose(Container c){
  if(c.countItem(VillageAstra.RESEARCH_VOLUME.get())>=ProductionBalance.baseBooks())return null;
  if(VOLUME.supplied(c))return VOLUME;
  if(c.countItem(Items.PAPER)<VOLUME.input("paper")&&PAPER.supplied(c))return PAPER;
  if(c.countItem(VillageAstra.RESEARCH_BINDING.get())==0){if(WOOD_BINDING.supplied(c))return WOOD_BINDING;if(LEATHER_BINDING.supplied(c))return LEATHER_BINDING;}
  if(c.countItem(VillageAstra.WRITING_TOOL.get())==0&&WRITING_TOOL.supplied(c))return WRITING_TOOL;
  return null;
 }
}
