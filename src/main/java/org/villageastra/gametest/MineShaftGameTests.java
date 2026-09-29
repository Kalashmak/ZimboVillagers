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
/** AD-074: the mine goes down — a stepped shaft from its doorway to the bottom, and the drive carries on from there. A drive begun before
 *  the shaft existed keeps the ground it was dug at. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineShaftGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void theShaftStepsDownToTheDriveMouth(GameTestHelper h){
  var layout=BuildingBlueprints.layout("mine",BlockPos.ZERO);
  for(int z=1;z<=6;z++){
   var step=layout.get(new BlockPos(3,-z,z));
   h.assertTrue(step!=null&&step.is(Blocks.STONE_BRICK_STAIRS),"Step "+z+" of the shaft is a laid stone tread: "+step);
   h.assertTrue(layout.getOrDefault(new BlockPos(3,-z+1,z),Blocks.AIR.defaultBlockState()).isAir(),"The shaft is open above step "+z);
   h.assertTrue(layout.get(new BlockPos(2,-z,z))!=null&&layout.get(new BlockPos(5,-z,z))!=null,"The shaft is lined on both sides at step "+z);
   h.assertTrue(!layout.containsKey(new BlockPos(1,1,z))||layout.get(new BlockPos(1,1,z)).isAir()||z==1||z==4||z==6,
     "The aisle from the door to the chest stays walkable at "+z+": "+layout.get(new BlockPos(1,1,z)));
  }
  h.assertTrue(BuildingBlueprints.SHAFT_DESCENT==6,"The drive starts six blocks below the lot");
  h.assertTrue(layout.entrySet().stream().anyMatch(c->c.getValue().getBlock() instanceof net.minecraft.world.level.block.DoorBlock),"The mine has a door the road reaches");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theDriveIsProtectedWhereItReallyRuns(GameTestHelper h){
  var deep=new MineArea(3,3,4,BuildingBlueprints.SHAFT_DESCENT);
  h.assertTrue(deep.contains(3,-BuildingBlueprints.SHAFT_DESCENT,7,0),"The mouth of a shafted drive is its own ground");
  h.assertTrue(!deep.contains(3,0,7,0),"At the surface a shafted drive claims nothing");
  var legacy=new MineArea(3,3,4,0);
  h.assertTrue(legacy.contains(3,0,7,0)&&!legacy.contains(3,-BuildingBlueprints.SHAFT_DESCENT,7,0),"A drive dug before the shaft keeps the ground it was dug at");
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,6,6));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var mine=new Settlement.Building(Settlement.childId(s.id(),"building/mine"),"mine",8,0,0);s.addBuilding(mine);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   h.assertTrue(OwnershipEvents.protectedBlock(l,BuildingPlacement.at(e,mine,3,-3,3)),"A step of the shaft is the settlement's own");
   s.noteMine(mine.id(),2,3,4,BuildingBlueprints.SHAFT_DESCENT);
   h.assertTrue(OwnershipEvents.protectedBlock(l,BuildingPlacement.at(e,mine,3,-BuildingBlueprints.SHAFT_DESCENT,7)),"The worked drive below the shaft is protected");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
