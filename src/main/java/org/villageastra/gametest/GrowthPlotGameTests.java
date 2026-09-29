package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GrowthPlotGameTests {
 @GameTest(template="empty") public static void reservesKeepSixClearPassageCells(GameTestHelper h){
  var a=new GrowthPlots.Box(-6,-6,12,12);
  h.assertTrue(!a.separated(new GrowthPlots.Box(18,-6,36,12)),"Five clear cells are insufficient");
  h.assertTrue(a.separated(new GrowthPlots.Box(19,-6,37,12)),"Six clear cells remain between reserves");
  h.assertTrue(!a.separated(new GrowthPlots.Box(4,4,22,22)),"Diagonal overlap is refused");h.succeed();
 }
 @GameTest(template="empty") public static void npcPlotsReserveSpaceAroundExistingBuildings(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);s.addBuilding(b);var e=new SettlementData.Entry(s,h.getLevel().dimension().location().toString(),new BlockPos(-200,70,-200));
  h.assertTrue(!GrowthPlots.available(e,"home",e.center().offset(BuildingBlueprints.design("mine").width()+4,80,0),0),"A nearby house is refused even on another elevation");
  h.assertTrue(GrowthPlots.available(e,"home",e.center().offset(70,0,0),0),"A distant plot remains available");h.assertTrue(s.buildings().iterator().next().equals(b),"Existing buildings never move");h.succeed();
 }
 @GameTest(template="empty") public static void farmFieldsAndRotatedBlueprintCellsFitTheirReserve(GameTestHelper h){
  for(String type:List.of("farm","forester","mine","town_hall"))for(int turn=0;turn<4;turn++){
   var box=GrowthPlots.box(type,new BlockPos(-31,0,19),turn);
   for(var p:BuildingPlacement.layout(type,new BlockPos(-31,0,19),turn).keySet())h.assertTrue(p.getX()>=box.west()+6&&p.getX()<=box.east()-6&&p.getZ()>=box.north()+6&&p.getZ()<=box.south()-6,"Blueprint and exterior cells have expansion space: "+type+"/"+turn);
  }h.succeed();
 }
}
