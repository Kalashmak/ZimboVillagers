package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FurnaceFuelGameTests {
 @GameTest(template="empty",batch="furnace_fuel") public static void availableCoalPreservesCraftingSticks(GameTestHelper h){check(h,Items.COAL);}
 @GameTest(template="empty",batch="furnace_fuel") public static void availableCharcoalPreservesCraftingSticks(GameTestHelper h){check(h,Items.CHARCOAL);}
 @GameTest(template="empty",batch="furnace_fuel") public static void woodFuelStillWorksWithoutCoal(GameTestHelper h){check(h,null);}
 private static void check(GameTestHelper h,Item fuel){var l=h.getLevel();var at=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(b);var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);SettlementData.get(l.getServer()).add(e);
  var pos=Workshops.station(e,b);l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,b);stock.setItem(0,new ItemStack(Items.SAND));stock.setItem(1,new ItemStack(Items.STICK,2));if(fuel!=null)stock.setItem(2,new ItemStack(fuel));l.setBlock(at.offset(4,1,4),Blocks.FURNACE.defaultBlockState(),2);
  try{for(int i=0;i<12&&!Workshops.inspect(l,b.id()).getString("stage").equals("smelt_put_fuel");i++)Workshops.advance(l,e,b,1000+i*20,List.of(new Workshops.Want(Ingredient.of(Items.GLASS),1,b.id())));
   var state=Workshops.inspect(l,b.id());h.assertTrue(state.getString("stage").equals("smelt_put_fuel"),"Physical worker has taken fuel");
   h.assertTrue(ItemStack.of(state.getCompound("carried")).is(fuel==null?Items.STICK:fuel),"Available dedicated fuel must be chosen before crafting sticks");
   h.assertTrue(stock.countItem(Items.STICK)==(fuel==null?1:2),"Recipe sticks retained when coal is available; fallback still pays one stick");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
