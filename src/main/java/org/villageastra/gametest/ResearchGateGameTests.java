package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-077: the research tree really opens things — a design before it may be ordered, the surfaces, lamps and width of a road,
 *  and the machinery a building runs on at the top of its own ladder (AD-136). */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchGateGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<30;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<8;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,e,s,center);
 }
 /** Knowledge the settlement has already paid for. */
 private static void learn(Town t,String... nodes){
  var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);
  for(var node:nodes)done.add(StringTag.valueOf(node));
  record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);
 }
 private static void done(Town t){SettlementData.get(t.l.getServer()).remove(t.s.id());}
 @GameTest(template="empty",timeoutTicks=100) public static void aRoadTakesTheResearchOfItsSurfaceLampsAndWidth(GameTestHelper h){
  var t=town(h);
  try{
   int gravel=1<<2,paving=2<<2,lamps=1<<4,wide=5<<6;
   h.assertTrue(ResearchGate.forRoad(0).isEmpty(),"A dirt path of three blocks takes no research");
   h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,gravel).equals("research"),"Gravel takes its research");
   // AD-123: cobblestone is the owner's rung IV (×1.5), no longer Roads II (which is now the trails between villages).
   h.assertTrue(ResearchGate.forRoad(paving).contains("roads.4")&&!ResearchGate.forRoad(paving).contains("roads.2")&&ResearchGate.forRoad(lamps).contains("roads.3")&&ResearchGate.forRoad(wide).contains("roads.6"),
     "Paving, lamps and a wide road each name their own node: "+ResearchGate.forRoad(paving)+" "+ResearchGate.forRoad(lamps)+" "+ResearchGate.forRoad(wide));
   learn(t,"roads.1");
   h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,gravel).isEmpty(),"With gravel researched the order goes on");
   h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,paving).equals("research"),"Stone paving still waits for its own node");
   var a=t.center.offset(4,1,10);var b=t.center.offset(10,1,10);
   h.assertTrue(MapOrders.road(t.l,t.e,a,b,paving).equals("research"),"The map refuses a paved road before its research");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aBuildingTakesTheResearchItsCatalogueNames(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(ResearchGate.forDesign("barracks").contains("military.1"),"The catalogue names what barracks take: "+ResearchGate.forDesign("barracks"));
   h.assertTrue(ResearchGate.forDesign("home").isEmpty(),"A house takes no research");
   var site=t.center.offset(12,0,12);
   h.assertTrue(BuildingOrders.approve(t.l,t.e,"barracks",0,site).equals("research"),"Barracks are refused before their research");
   learn(t,"military.1");
   h.assertTrue(!BuildingOrders.approve(t.l,t.e,"barracks",0,site).equals("research"),"With the research done the order is judged on the site alone");
  }finally{done(t);}
  h.succeed();
 }
 /** AD-136 (D9): no mechanics branch - the machinery of a building is the top of its own ladder (balance/automation.json). A farm of a
  *  village laid before layout 6 runs its field cycle from its own level V (the owner's farm VI for a layout-6 village); at IV it needs hands. */
 @GameTest(template="empty",timeoutTicks=200) public static void theMachineryOfALevelFollowsTheBuildingsOwnBranch(GameTestHelper h){
  var t=town(h);
  try{
   var farm=new Settlement.Building(Settlement.childId(t.s.id(),"building/farm"),"farm",8,0,0);t.s.addBuilding(farm);
   h.assertTrue(FarmField.legacy(t.s)&&Automation.level("farm",true)==5,"An older village's farm runs its machine from V");
   for(int i=2;i<=4;i++)t.s.raiseBuildingLevel(farm.id(),i);
   var kept=t.s.buildings().stream().filter(x->x.id().equals(farm.id())).findFirst().orElseThrow();
   var chestPos=LogisticsRoutes.position(t.e,kept);
   t.l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);t.l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
   for(int i=2;i<=5;i++)for(var placed:BuildingLevels.equipment("farm",i))
    t.l.setBlock(BuildingPlacement.at(t.e,kept,placed.local().getX(),placed.local().getY(),placed.local().getZ()),placed.state(),3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==4&&!Machines.mechanized(t.l,t.e,kept),"A level-four farm still needs hands, whatever was studied");
   t.s.raiseBuildingLevel(farm.id(),5);var five=t.s.buildings().stream().filter(x->x.id().equals(farm.id())).findFirst().orElseThrow();
   h.assertTrue(BuildingLevels.level(t.l,t.e,five)==5&&Machines.mechanized(t.l,t.e,five)&&Automation.at(t.l,t.e,five).cycle(),"At its own level V the machinery turns, no mechanics research asked");
   var chest=LogisticsRoutes.chest(t.l,t.e,five);chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS,8));
   for(var cell:FarmWorkArea.cells(t.e)){t.l.setBlock(cell.below(),Blocks.FARMLAND.defaultBlockState(),3);t.l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);
    if((cell.getX()+cell.getZ())%3==0)t.l.setBlock(cell.above(2),Blocks.GLOWSTONE.defaultBlockState(),3);}
   h.startSequence().thenIdle(30).thenExecute(()->{
    h.assertTrue(Machines.tick(t.l,t.e,80,Workshops.wants(t.l,t.e))>0,"The field runs itself: why=["+Machines.lastReason+"]");
    done(t);
   }).thenSucceed();
  }catch(Throwable ex){done(t);throw ex;}
 }
}
