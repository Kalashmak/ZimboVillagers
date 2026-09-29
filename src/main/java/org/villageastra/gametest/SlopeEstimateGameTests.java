package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** OWNER_REQUEST 9.9: on a slope the estimate on the map is the builder's own plan, and moving the floor up or down really changes what
 *  is cut away and what is filled in — a higher floor cuts less of the rise and fills more of the hollow. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SlopeEstimateGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void movingTheFloorChangesCutAndFillAsTheMapShowsIt(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/engineering"),"engineering",8,0,0);s.addBuilding(office);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true);s.addHome(home);
  for(int x=-2;x<16;x++)for(int z=-2;z<16;z++){for(int y=-4;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=10;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  // The slope: a rise of two blocks on one side of the plot and a hollow of two on the other.
  for(int x=0;x<3;x++)for(int z=0;z<7;z++)for(int y=1;y<=2;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),3);
  for(int x=4;x<7;x++)for(int z=0;z<4;z++){l.setBlock(site.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);l.setBlock(site.offset(x,-1,z),Blocks.AIR.defaultBlockState(),3);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.ENGINEER,office.id());
  try{
   var low=Terraces.plan(l,e,"home",site);var high=Terraces.plan(l,e,"home",site.above());
   h.assertTrue(low!=null&&high!=null,"The engineer designs the earthworks at both floor heights");
   h.assertTrue(high.getInt("cut")<low.getInt("cut"),"A floor one block higher cuts less of the rise: "+high.getInt("cut")+" < "+low.getInt("cut"));
   h.assertTrue(high.getInt("fill")>low.getInt("fill"),"And fills more of the hollow: "+high.getInt("fill")+" > "+low.getInt("fill"));
   // The map's estimate at each height is the very plan the builders would work, not a separate guess.
   var shownLow=Plans.estimate(l,e,"home",0,site);var shownHigh=Plans.estimate(l,e,"home",0,site.above());
   h.assertTrue(shownLow.getInt("cut")==low.getInt("cut")&&shownLow.getInt("fill")==low.getInt("fill"),
     "The estimate on the map at the lower floor is the builders' plan: "+shownLow.getInt("cut")+"/"+shownLow.getInt("fill"));
   h.assertTrue(shownHigh.getInt("cut")==high.getInt("cut")&&shownHigh.getInt("fill")==high.getInt("fill"),
     "And at the higher floor as well: "+shownHigh.getInt("cut")+"/"+shownHigh.getInt("fill"));
   h.assertTrue(shownHigh.getInt("operations")!=shownLow.getInt("operations"),"The building plan itself changes with the floor too: "
     +shownLow.getInt("operations")+" / "+shownHigh.getInt("operations"));
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
