package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.WarehouseFixture.*;
/** AD-147 §1.3 (CF-C, CF-D, CF-E): the warehouse's courier decides by need - food before the producers' output, a producer's chest at least
 *  half full before food, the fullest chest first, and the hall's stock never carried off to the warehouse. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarehouseNeedGameTests {
 /** Bread for the pantry (food, class 3) goes before the mine's cobble (output, class 6); a mine chest over half full goes before the bread. */
 @GameTest(template="empty",timeoutTicks=200) public static void foodBeforeOutputAndAFullChestFirst(GameTestHelper h){
  var v=village(h,1);
  try{var courier=adult(v,"courier",Profession.PORTER,v.kept());var at=LogisticsRoutes.position(v.e(),v.kept());
   // A second producer (a farm) holds bread: the pantry's want is a food need from there.
   var farm=new Settlement.Building(Settlement.childId(v.s().id(),"building/farm"),"farm",44,0,14);v.s().addBuilding(farm);
   var fp=LogisticsRoutes.position(v.e(),farm);v.l().setBlock(fp,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   put(LogisticsRoutes.chest(v.l(),v.e(),farm),new ItemStack(Items.BREAD,32));
   for(int i=0;i<3;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
   var r=LogisticsRoutes.choose(v.l(),v.e(),v.kept(),at);
   h.assertTrue(r!=null&&r.item().is(Items.BREAD)&&r.destination().id().equals(v.kept().id()),"Food first: "+r);
   for(int i=0;i<12;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
   h.assertTrue(LogisticsRoutes.fill(v.pit())>=LogisticsRoutes.FULL,"The mine's chest is over half full");
   r=LogisticsRoutes.choose(v.l(),v.e(),v.kept(),at);
   h.assertTrue(r!=null&&r.source().id().equals(v.mine().id())&&r.item().is(Items.COBBLESTONE),"A full producer's chest before the food: "+r);
  }finally{done(v);}
  h.succeed();
 }
 /** Of two producers' chests the fuller is emptied first. */
 @GameTest(template="empty",timeoutTicks=200) public static void theFullestChestFirst(GameTestHelper h){
  var v=village(h,1);
  try{adult(v,"courier",Profession.PORTER,v.kept());var at=LogisticsRoutes.position(v.e(),v.kept());
   var farm=new Settlement.Building(Settlement.childId(v.s().id(),"building/farm"),"farm",44,0,14);v.s().addBuilding(farm);
   var fp=LogisticsRoutes.position(v.e(),farm);v.l().setBlock(fp,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var fc=LogisticsRoutes.chest(v.l(),v.e(),farm);
   for(int i=0;i<2;i++)put(v.pit(),new ItemStack(Items.COBBLESTONE,64));
   for(int i=0;i<9;i++)put(fc,new ItemStack(Items.WHEAT,64));
   var r=LogisticsRoutes.choose(v.l(),v.e(),v.kept(),at);
   h.assertTrue(r!=null&&r.source().id().equals(farm.id())&&r.item().is(Items.WHEAT),"The fuller chest (the farm's) first: "+r);
  }finally{done(v);}
  h.succeed();
 }
 /** CF-E (owner default Q2): what lies in the hall is never carried to the warehouse. */
 @GameTest(template="empty",timeoutTicks=200) public static void theHallStockStaysAtTheHall(GameTestHelper h){
  var v=village(h,3);
  try{adult(v,"courier",Profession.PORTER,v.kept());var at=LogisticsRoutes.position(v.e(),v.kept());
   for(int i=0;i<20;i++)put(v.pantry(),new ItemStack(i%2==0?Items.COBBLESTONE:Items.OAK_LOG,64));
   var r=LogisticsRoutes.choose(v.l(),v.e(),v.kept(),at);
   h.assertTrue(r==null||!(r.source().id().equals(v.hall().id())&&r.destination().id().equals(v.kept().id())),"No overflow from the hall: "+r);
  }finally{done(v);}
  h.succeed();
 }
}
