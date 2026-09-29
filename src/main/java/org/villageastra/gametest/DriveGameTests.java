package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-055: the drive is a real water wheel and a real chain shaft; a driven station works without a worker, on its own stock, and stops the moment the shaft breaks. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DriveGameTests {
 private record Works(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building mill,Settlement.Building bakery){}
 private static Works works(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var mill=new Settlement.Building(Settlement.childId(s.id(),"building/mill"),"mill",8,0,0);s.addBuilding(mill);
  var bakery=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",18,0,0);s.addBuilding(bakery);
  for(int x=-2;x<32;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(mill,bakery)){
   var chestPos=LogisticsRoutes.position(e,b);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
   l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  }
  return new Works(l,e,mill,bakery);
 }
 /** Lays the shaft from the mill post to the bakery post along a straight line of chains. */
 private static void shaft(Works t){
  var from=Drive.postOf(t.e,t.mill);var to=Drive.postOf(t.e,t.bakery);
  for(int x=Math.min(from.getX(),to.getX());x<=Math.max(from.getX(),to.getX());x++)
   t.l.setBlock(new BlockPos(x,from.getY(),from.getZ()),Blocks.CHAIN.defaultBlockState(),3);
  for(int z=Math.min(from.getZ(),to.getZ());z<=Math.max(from.getZ(),to.getZ());z++)
   t.l.setBlock(new BlockPos(to.getX(),from.getY(),z),Blocks.CHAIN.defaultBlockState(),3);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void driveNeedsARealWheelAndAnUnbrokenShaft(GameTestHelper h){
  var t=works(h);
  h.assertTrue(!Drive.wheel(t.l,t.e),"Without a wheel there is no drive");
  var wheel=Drive.postOf(t.e,t.mill);
  t.l.setBlock(wheel,Blocks.CHAIN.defaultBlockState(),3);
  h.assertTrue(!Drive.wheel(t.l,t.e),"A wheel out of the water turns nothing");
  t.l.setBlock(wheel.below(),Blocks.WATER.defaultBlockState(),3);
  h.assertTrue(Drive.wheel(t.l,t.e),"A wheel standing in water turns");
  h.assertTrue(!Drive.driven(t.l,t.e,t.bakery),"Without a shaft the bakery is not driven");
  shaft(t);
  h.assertTrue(Drive.driven(t.l,t.e,t.bakery),"An unbroken chain shaft drives the bakery");
  h.assertTrue(Drive.stations(t.l,t.e).stream().anyMatch(b->b.id().equals(t.bakery.id())),"The driven station is listed");
  var middle=new BlockPos((Drive.postOf(t.e,t.mill).getX()+Drive.postOf(t.e,t.bakery).getX())/2,wheel.getY(),wheel.getZ());
  t.l.setBlock(middle,Blocks.AIR.defaultBlockState(),3);
  h.assertTrue(!Drive.driven(t.l,t.e,t.bakery),"One missing link stops the drive");
  SettlementData.get(h.getLevel().getServer()).remove(t.e.settlement().id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void drivenStationWorksWithoutHandsButNeverForFree(GameTestHelper h){
  var t=works(h);
  var wheel=Drive.postOf(t.e,t.mill);
  t.l.setBlock(wheel,Blocks.CHAIN.defaultBlockState(),3);t.l.setBlock(wheel.below(),Blocks.WATER.defaultBlockState(),3);
  shaft(t);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.bakery);chest.clearContent();
  var hall=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));hall.clearContent();
  // Empty station: the automaton turns the wheel and still produces nothing. Only this village's machines turn: the other tests'
  // villages keep their own clocks (a level-V field elsewhere would sow a cell on every turn).
  for(long now=0;now<Automation.PERIOD*8;now+=Automation.PERIOD)Automation.tick(t.l,t.e,now);
  int made=0;for(int slot=0;slot<chest.getContainerSize();slot++)made+=chest.getItem(slot).getCount();
  h.assertTrue(made==0,"A driven station with nothing in it makes nothing: "+made);
  // With real inputs the automaton does the work a baker would have done: bread is baked from flour, not from thin air.
  chest.setItem(0,new ItemStack(VillageAstra.FLOUR.get(),16));chest.setItem(1,new ItemStack(Items.COAL,8));
  boolean baked=false;
  for(long now=Automation.PERIOD*8;now<Automation.PERIOD*160&&!baked;now+=Automation.PERIOD){
   Automation.tick(t.l,t.e,now);
   baked=chest.countItem(Items.BREAD)>0;
  }
  h.assertTrue(baked,"The driven bakery really baked from its own stock");
  h.assertTrue(chest.countItem(VillageAstra.FLOUR.get())<16,"The flour was really spent: "+chest.countItem(VillageAstra.FLOUR.get()));
  SettlementData.get(h.getLevel().getServer()).remove(t.e.settlement().id());h.succeed();
 }
}
