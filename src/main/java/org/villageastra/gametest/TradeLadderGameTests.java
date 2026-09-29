package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-160 II (external trade): a village trades at a distance of its own accord only with one it has really met (AD-158) and that keeps
 *  a trade centre of its own. A mayor's own contract is not touched by the rule, and a world without the ladder's knob trades as before. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TradeLadderGameTests {
 record Town(ServerLevel l,Settlement s,SettlementData.Entry e){}
 /** A village with a trade centre of its own at that level, laid so the world really shows it. */
 static Town town(GameTestHelper h,BlockPos at,int centre){
  var l=h.getLevel();var center=h.absolutePos(at);var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-4;x<12;x++)for(int z=-4;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  if(centre>0){
   var yard=new Settlement.Building(Settlement.childId(s.id(),"building/caravan"),"caravan",4,0,0);s.addBuilding(yard);
   for(int n=2;n<=centre;n++)s.raiseBuildingLevel(yard.id(),n);
   var b=s.buildings().stream().filter(x->x.id().equals(yard.id())).findFirst().orElseThrow();
   for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("caravan",centre)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   BuildingLevels.forgetBest(s.id());}
  return new Town(l,s,e);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theThirdLevelCarriesFiveStacksATrip(GameTestHelper h){
  var home=town(h,new BlockPos(6,3,6),3);var l=home.l;
  try{
   // The owner's ladder puts five stacks on the third level; the levels round it keep their own order.
   h.assertTrue(CoreEffects.value("caravan","carry",3)==5*64,"Five stacks a trip at the third level: "+CoreEffects.value("caravan","carry",3));
   int[] want={64,128,320,384,448,512};
   for(int level=1;level<=6;level++)h.assertTrue(CoreEffects.value("caravan","carry",level)==want[level-1],
     "The load of level "+level+" is "+want[level-1]+", not "+CoreEffects.value("caravan","carry",level));
   for(int level=2;level<=6;level++)h.assertTrue(CoreEffects.value("caravan","carry",level)>CoreEffects.value("caravan","carry",level-1),"and every level carries more than the one below");
   h.assertTrue(Caravans.carry(l,home.e)==5*64,"A village whose centre is of the third level really carries five stacks: "+Caravans.carry(l,home.e));
   h.succeed();
  }finally{BuildingLevels.forgetBest(home.s.id());SettlementData.get(l.getServer()).remove(home.s.id());}
 }

 @GameTest(template="empty",timeoutTicks=200) public static void remoteTradeAsksForAVillageMetWithACentreOfItsOwn(GameTestHelper h){
  var home=town(h,new BlockPos(6,3,6),2);var far=town(h,new BlockPos(6,3,40),2);var bare=town(h,new BlockPos(40,3,6),0);
  var l=home.l;
  try{
   h.assertTrue(CoreEffects.active("caravan","remote"),"Trade at a distance is a knob of the game");
   h.assertTrue(CoreEffects.value("caravan","remote",1)==0&&CoreEffects.value("caravan","remote",2)==1,"It comes with the second level of the centre");
   h.assertTrue(TradeLadder.level(l,home.e)>=2&&TradeLadder.remote(l,home.e),"A centre of the second level trades at a distance: "+TradeLadder.level(l,home.e));
   // Met nobody yet: its own caravans go nowhere, however good the other village is.
   h.assertTrue(!TradeLadder.mayTrade(l,home.e,far.e),"A village it has not met is no partner of its own accord");
   CartographyLadder.found(l,home.s.id(),far.s.id());
   h.assertTrue(TradeLadder.mayTrade(l,home.e,far.e),"One its cartographers found is");
   // A meeting the other way round counts too: whoever walked to whom, they have met.
   CartographyLadder.found(l,bare.s.id(),home.s.id());
   h.assertTrue(!TradeLadder.mayTrade(l,home.e,bare.e),"but a village with no centre of its own is not in this trade");
   h.assertTrue(!TradeLadder.mayTrade(l,bare.e,home.e),"and one with no centre asks for none either");
   h.succeed();
  }finally{BuildingLevels.forgetBest(home.s.id());BuildingLevels.forgetBest(far.s.id());
   for(var t:List.of(home,far,bare))SettlementData.get(l.getServer()).remove(t.s.id());}
 }
}
