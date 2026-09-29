package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** AD-148 (level-V kit): a standing lantern of the level equipment keeps standing when its neighbour below is updated. The level-V kit is a
 *  lantern on the building's chest; a vanilla chest's lid supports nothing, so the lantern dropped at the first neighbour update and
 *  BuildingTiers.level lost level V. The owned chest's lid now carries it (OwnedChestBlock.getBlockSupportShape). */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EquipmentSupportGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void standingLanternsOfTheLevelsStayOnWhatTheyStandOn(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));var below=pos.below();
  l.setBlock(below.below(),Blocks.STONE.defaultBlockState(),2);
  var lantern=Blocks.LANTERN.defaultBlockState();
  // The mechanism of the bug: a vanilla chest's lid does not hold a lantern; the owned chest's does.
  l.setBlock(below,Blocks.CHEST.defaultBlockState(),2);
  h.assertFalse(lantern.canSurvive(l,pos),"A vanilla chest does not hold a lantern (the level-V bug)");
  var problems=new ArrayList<String>();int lanterns=0;
  for(var d:BuildingBlueprints.designs()){var type=d.id();if(!BuildingTiers.upgradable(type)||!LevelArchitecture.hasLevels(type))continue;
   var cells=new HashSet<BlockPos>();for(var p:LevelArchitecture.equipment(type))cells.add(p.local());
   for(int level=2;level<=BuildingTiers.max(type);level++){
    var layout=BuildingBlueprints.layout(BuildingTiers.layoutId(type,level),BlockPos.ZERO);
    for(var en:layout.entrySet()){var s=en.getValue();if(!(s.getBlock() instanceof LanternBlock)||s.getValue(LanternBlock.HANGING)||!cells.contains(en.getKey()))continue;
     lanterns++;var under=layout.getOrDefault(en.getKey().below(),Blocks.AIR.defaultBlockState());
     l.setBlock(below,under,2);l.setBlock(pos,s,2);
     BlockState after=s.updateShape(Direction.DOWN,l.getBlockState(below),l,pos,below);
     if(after.isAir()||!s.canSurvive(l,pos))problems.add(type+"@"+level+" "+en.getKey().toShortString()+" on "+under.getBlock());
     l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);l.setBlock(below,Blocks.AIR.defaultBlockState(),2);}}}
  h.assertTrue(lanterns>=20,"The levels' standing lanterns are walked: "+lanterns);
  h.assertTrue(problems.isEmpty(),"Every standing lantern of the level equipment stays on its support ("+problems.size()+"): "+problems);
  h.succeed();
 }
 /** Every level of a frozen design still holds its frozen equipment and lamps (nothing of the level's own rebuild — beds, tables, floors —
  *  lands on them), each lamp under a full block, and equipment a resident reached when frozen is walked to in that level's design. */
 @GameTest(template="empty",timeoutTicks=300) public static void everyLevelOfAFrozenDesignHoldsItsEquipment(GameTestHelper h){
  var problems=new ArrayList<String>();var air=Blocks.AIR.defaultBlockState();
  for(var d:BuildingBlueprints.designs()){var type=d.id();if(!LevelArchitecture.frozen(type))continue;
   // The hall is read by BuildingTiers from town_hall_3's equipment from level IV on (II and III are its own projects, AD-018).
   boolean hall=type.startsWith("town_hall");if(hall&&!type.equals("town_hall_3"))continue;
   var equipment=LevelArchitecture.equipment(type);var cells=new HashSet<BlockPos>();for(var p:equipment)cells.add(p.local());
   var reachRaw=EquipmentTableGameTests.reach(BuildingBlueprints.raw(type,BlockPos.ZERO),d.width(),d.depth());
   for(int level=hall?4:2;level<=(hall?BuildingTiers.MAX:BuildingTiers.max(type));level++){
    var layout=BuildingBlueprints.layout(BuildingTiers.layoutId(hall?"town_hall":type,level),BlockPos.ZERO);
    var placedCells=new HashSet<BlockPos>();for(var p:equipment)if(p.level()<=level)placedCells.add(p.local());
    for(var p:equipment){if(p.level()>level)continue;var now=layout.getOrDefault(p.local(),air);
     if(now.getBlock()!=p.state().getBlock())problems.add(type+"@"+level+" "+p.state().getBlock()+" at "+p.local().toShortString()+" replaced by "+now.getBlock());}
    // A lamp cell that a level's own equipment takes later (the quarry's level-IV grindstone, frozen so on master) gives way to it.
    for(var lamp:LevelArchitecture.frozenLamps(type)){if(placedCells.contains(lamp))continue;var now=layout.getOrDefault(lamp,air);
     if(!(now.getBlock() instanceof LanternBlock))problems.add(type+"@"+level+" lamp "+lamp.toShortString()+" replaced by "+now.getBlock());
     else if(!EquipmentTableGameTests.full(layout.getOrDefault(lamp.above(),air)))problems.add(type+"@"+level+" lamp "+lamp.toShortString()+" under "+layout.getOrDefault(lamp.above(),air).getBlock());}
    // The level's design without its equipment: the walls, floors and furniture of that level, as a resident walks it.
    var design=new HashMap<>(layout);for(var c:cells)design.put(c,air);for(var lamp:LevelArchitecture.frozenLamps(type))design.put(lamp,air);
    var reach=EquipmentTableGameTests.reach(design,d.width(),d.depth());
    for(var p:equipment)if(p.level()<=level&&EquipmentTableGameTests.near(reachRaw,p.local())&&!EquipmentTableGameTests.near(reach,p.local()))
     problems.add(type+"@"+level+" "+p.state().getBlock()+" at "+p.local().toShortString()+" is no longer walked to");}}
  h.assertTrue(problems.isEmpty(),"Every level of a frozen design holds its equipment ("+problems.size()+"): "+problems);
  h.succeed();
 }
}
