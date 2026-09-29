package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Readiness follows the same available-material selection as the physical funding step. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallFundingGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aLaterMaterialInTheChestFeedsTheNextWithdrawal(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<40;x++)for(int z=-2;z<30;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<10;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var data=SettlementData.get(l.getServer());data.add(e);
  Runnable done=()->{HallUpgradeGoal.drop(l,s.id());data.remove(s.id());};
  try{
   // A bakery that really stands, with a few blocks knocked out of it the way an explosion would.
   var bakery=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",14,0,0);s.addBuilding(bakery);
   var layout=BuildingPlacement.layout(e,bakery,"restaurant");
   for(var cell:layout.entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   int broken=0;for(var cell:layout.entrySet()){if(cell.getValue().isAir()||cell.getKey().getY()<=center.getY()+1)continue;l.setBlock(cell.getKey(),Blocks.AIR.defaultBlockState(),3);if(++broken>=6)break;}
   h.assertTrue(BuildingRepairs.check(l.getServer(),e).equals("queued"),"The repair of the bakery is queued");
   var cost=HallUpgradeGoal.inspect(l,s.id()).getCompound("cost");var keys=cost.getAllKeys().stream().sorted().toList();
   h.assertTrue(keys.size()>=2,"The repair needs more than one material: "+keys);
   var chest=LogisticsRoutes.chest(l,e,Workshops.hall(e));h.assertTrue(chest!=null,"The hall has its chest");
   String first=keys.get(0),last=keys.get(keys.size()-1);
   chest.setItem(0,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(last)),cost.getInt(last)));
   // A fresh village: waiting() has nothing cached for it yet.
   h.assertTrue(!HallUpgradeGoal.waiting(l,e),"The builder can collect "+last+" while "+first+" is still missing");
   chest.setItem(1,new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(first)),cost.getInt(first)));
   h.runAfterDelay(25,()->{
    try{h.assertTrue(!HallUpgradeGoal.waiting(l,e),"With "+first+" at the hall too the funding step can take it: the repair is the crew's work again");}
    finally{done.run();}
    h.succeed();});
  }catch(RuntimeException ex){done.run();throw ex;}
 }
}
