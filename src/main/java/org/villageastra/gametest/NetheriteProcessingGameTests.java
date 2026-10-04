package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NetheriteProcessingGameTests {
 @GameTest(template="empty",batch="netherite_processing",timeoutTicks=200)
 public static void smithyProcessesThirtySixRealDebrisIntoOneNetheriteBlock(GameTestHelper h){var t=ResearchV2Town.town(h,"smithy");
  try{
   var b=ResearchV2Town.raise(t,5);var stock=LogisticsRoutes.chest(t.l,t.e,b);stock.clearContent();
   stock.setItem(0,new ItemStack(Items.ANCIENT_DEBRIS,36));stock.setItem(1,new ItemStack(Items.GOLD_INGOT,36));stock.setItem(2,new ItemStack(Items.COAL,64));
   h.assertTrue(Workshops.makeable(t.l,t.e,"minecraft:netherite_block"),"Actual debris held in the smithy can fund the vanilla recipe chain");
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.NETHERITE_BLOCK),1,b.id()));long now=1000;boolean smelted=false;
   for(int step=0;step<2000&&stock.countItem(Items.NETHERITE_BLOCK)==0;step++,now+=20){
    Workshops.advance(t.l,t.e,b,now,wants);var job=Workshops.inspect(t.l,b.id());
    if(job.getBoolean("physicalSmelt")&&job.contains("furnace")){
     var pos=BlockPos.of(job.getLong("furnace"));if(t.l.getBlockEntity(pos) instanceof FurnaceBlockEntity f){smelted=true;for(int tick=0;tick<20;tick++)AbstractFurnaceBlockEntity.serverTick(t.l,pos,t.l.getBlockState(pos),f);}
    }
   }
   h.assertTrue(smelted&&stock.countItem(Items.NETHERITE_BLOCK)==1,"Physical furnace and workshop finish one block: "+Workshops.inspect(t.l,b.id()));
   h.assertTrue(stock.countItem(Items.ANCIENT_DEBRIS)==0&&stock.countItem(Items.GOLD_INGOT)==0&&stock.countItem(Items.NETHERITE_SCRAP)==0&&stock.countItem(Items.NETHERITE_INGOT)==0,"36 debris and 36 gold consumed, no copied intermediate");
   h.assertTrue(stock.countItem(Items.COAL)<64,"Real furnace consumed real fuel");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="netherite_processing")
 public static void absentDebrisRemainsAnHonestBlockerAndSmithyFourCannotProcessIt(GameTestHelper h){var t=ResearchV2Town.town(h,"smithy");
  try{
   h.assertTrue(!Workshops.spec("smithy",4).outputItems().contains(Items.NETHERITE_SCRAP)&&Workshops.spec("smithy",5).outputItems().contains(Items.NETHERITE_SCRAP),"Processing opens at working level V");
   var b=ResearchV2Town.raise(t,5);var stock=LogisticsRoutes.chest(t.l,t.e,b);stock.clearContent();
   h.assertTrue(!Workshops.makeable(t.l,t.e,"minecraft:netherite_block"),"No fictional Nether supply capability");
   stock.setItem(0,new ItemStack(Items.NETHERITE_INGOT));stock.setItem(1,new ItemStack(Items.GOLD_INGOT,36));stock.setItem(2,new ItemStack(Items.COAL));
   h.assertTrue(!Workshops.makeable(t.l,t.e,"minecraft:netherite_block"),"One finite ingot does not justify a whole block project");
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.NETHERITE_BLOCK),1,b.id()));
   h.assertTrue(Workshops.plan(t.l,t.e,b,stock,wants)==null,"One ingot cannot become nine by decompression cycles");
   h.assertTrue(Workshops.needs(t.l,t.e,Workshops.spec(t.l,t.e,b),stock,wants).stream().anyMatch(n->n.matches(new ItemStack(Items.ANCIENT_DEBRIS))),"Missing real debris is published as demand");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
