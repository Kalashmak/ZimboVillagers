package org.villageastra.gametest;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StarterTimberGameTests {
 @GameTest(template="empty",batch="starter_timber",timeoutTicks=300)
 public static void exhaustedForestQuotesStockedWoodWithoutConsumingIt(GameTestHelper h){
  var f=ForestFixture.create(h,1,192);
  try{
   var hall=LogisticsRoutes.chest(f.l,f.e,Workshops.hall(f.e));hall.clearContent();f.chestBlock().clearContent();
   hall.setItem(0,new ItemStack(Items.OAK_LOG,14));f.chestBlock().setItem(0,new ItemStack(Items.OAK_SAPLING,2));
   String wood="";for(int i=0;i<150&&wood.isEmpty();i++)wood=BuildingWood.choose(f.l,f.e,"home");
   h.assertTrue(wood.equals("oak"),"No wild tree: quote real stocked oak, not the legacy dark-oak house: "+wood);
   h.assertTrue(hall.countItem(Items.OAK_LOG)==14&&f.chestBlock().countItem(Items.OAK_SAPLING)==2,"Survey does not spend or create stock");
   var layout=BuildingWood.apply(BuildingBlueprints.layout("home",net.minecraft.core.BlockPos.ZERO),wood);
   h.assertTrue(layout.values().stream().noneMatch(s->{var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();return id.contains("dark_oak")||id.contains("spruce");}),"Whole bill uses the supplied species");
   hall.clearContent();f.chestBlock().clearContent();
   h.assertTrue(BuildingWood.choose(f.l,f.e,"home").isEmpty(),"No invented source in an empty forest and empty stores");
   h.assertTrue(MayorPlanner.plan(f.l,f.e)==null,"NPC cannot freeze an unsupported legacy timber bill");
   f.chestBlock().setItem(0,new ItemStack(Items.DARK_OAK_SAPLING));
   h.assertTrue(BuildingWood.choose(f.l,f.e,"home").isEmpty(),"A lone dark-oak sapling is not a growable source");
  }finally{f.done();}h.succeed();
 }
}
