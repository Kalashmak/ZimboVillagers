package org.villageastra.world;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;

/** Village fuel stock excludes seed stock, tools and finished building components. */
public final class WorkshopFuel {
 private WorkshopFuel(){}
 public static net.minecraft.world.item.crafting.Ingredient demand(){return net.minecraft.world.item.crafting.Ingredient.fromJson(com.google.gson.JsonParser.parseString("[{\"item\":\"minecraft:coal\"},{\"item\":\"minecraft:charcoal\"},{\"item\":\"minecraft:coal_block\"},{\"item\":\"minecraft:dried_kelp_block\"},{\"item\":\"minecraft:stick\"},{\"item\":\"minecraft:bamboo\"},{\"tag\":\"minecraft:logs\"},{\"tag\":\"minecraft:planks\"}]"));}
 public static int ticks(ItemStack stack){
  if(stack.isEmpty()||stack.hasCraftingRemainingItem())return 0;
  boolean raw=stack.is(ItemTags.LOGS)||stack.is(ItemTags.PLANKS)||stack.is(Items.STICK)||stack.is(Items.BAMBOO);
  boolean dedicated=stack.is(Items.COAL)||stack.is(Items.CHARCOAL)||stack.is(Items.COAL_BLOCK)||stack.is(Items.DRIED_KELP_BLOCK);
  return raw||dedicated?net.minecraftforge.common.ForgeHooks.getBurnTime(stack,RecipeType.SMELTING):0;
 }
}
