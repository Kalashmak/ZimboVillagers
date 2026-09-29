package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-138 V (spec §6): a pen goes out to graze only at a yard of level V with livestock.5, its feeder empty and none of its feed in the
 *  yard chest; the pasture is the village's grass, never a pen; a village's beast never tramples a field. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LivestockGrazingGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aHungryPenOfAYardVGrazesTheVillageGrass(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,2,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int n=s.civilization().level()+1;n<=5;n++)s.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",4,0,4);s.addBuilding(yard);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var sheep=EntityType.SHEEP.create(l);var stray=EntityType.SHEEP.create(l);
  try{
   var o=BuildingPlacement.origin(e,yard);
   for(int x=-2;x<20;x++)for(int z=-2;z<28;z++){l.setBlock(o.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);l.setBlock(o.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<8;y++)l.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(var cell:BuildingPlacement.layout(e,yard,BuildingTiers.layoutId("livestock",4)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   var p=LivestockPens.pen(1);
   h.assertTrue(!LivestockGrazing.allowed(l,e,yard),"A yard below V does not graze");
   s.raiseBuildingLevel(yard.id(),2);s.raiseBuildingLevel(yard.id(),3);s.raiseBuildingLevel(yard.id(),4);s.raiseBuildingLevel(yard.id(),5);yard=s.buildings().stream().filter(b->b.type().equals("livestock")).findFirst().orElseThrow();
   for(var cell:BuildingPlacement.layout(e,yard,BuildingTiers.layoutId("livestock",5)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   h.assertTrue(BuildingLevels.level(l,e,yard)==5,"The yard works at V: "+BuildingLevels.level(l,e,yard));
   h.assertTrue(!LivestockGrazing.allowed(l,e,yard),"Not without livestock.5");
   var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);done.add(StringTag.valueOf(LivestockGrazing.RESEARCH));record.put("legacyDone",done);BookResearch.store(l,e,record);ResearchKnobs.forget(s.id());
   h.assertTrue(LivestockGrazing.allowed(l,e,yard),"V and livestock.5: the yard may graze");
   var chest=LogisticsRoutes.chest(l,e,yard);
   h.assertTrue(chest!=null&&LivestockGrazing.hungry(l,e,yard,p),"Empty feeder, no wheat in the chest: pen 1 is hungry");
   chest.setItem(0,new ItemStack(Items.WHEAT,4));
   h.assertTrue(!LivestockGrazing.hungry(l,e,yard,p),"Wheat in the chest: the keeper fills the feeder, no grazing");
   chest.setItem(0,ItemStack.EMPTY);
   var inPen=LivestockPens.at(e,yard,new BlockPos(p.x()+3,1,p.z()+3));var beside=o.offset(-1,1,3);
   double reach=LivestockGrazing.reach(e);var farmed=new HashSet<BlockPos>();
   h.assertTrue(!LivestockGrazing.pasture(l,e,yard,inPen,reach,farmed),"A pen is no pasture");
   h.assertTrue(LivestockGrazing.pasture(l,e,yard,beside,reach,farmed),"The grass beside the yard is");
   farmed.add(beside.below());h.assertTrue(!LivestockGrazing.pasture(l,e,yard,beside,reach,farmed),"A farm's worked soil is not");
   // A village's beast never tramples a field; a wild one does.
   var soil=o.offset(-1,0,5);l.setBlock(soil,Blocks.FARMLAND.defaultBlockState(),2);
   sheep.moveTo(soil.getX()+.5,soil.getY()+1,soil.getZ()+.5);l.addFreshEntity(sheep);LivestockPens.tag(sheep,s.id(),yard,p);
   stray.moveTo(soil.getX()+.5,soil.getY()+1,soil.getZ()+.5);l.addFreshEntity(stray);
   h.assertTrue(!net.minecraftforge.common.ForgeHooks.onFarmlandTrample(l,soil,Blocks.DIRT.defaultBlockState(),1.5F,sheep),"The village's sheep leaves the field whole");
   h.assertTrue(net.minecraftforge.common.ForgeHooks.onFarmlandTrample(l,soil,Blocks.DIRT.defaultBlockState(),1.5F,stray),"A wild sheep still tramples it");
   h.assertTrue(sheep.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof GrazeGoal),"A pen beast has its grazing goal");
  }finally{sheep.discard();stray.discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
