package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** AD-148: the frozen level equipment (data/villageastra/architecture/equipment.json) keeps fitting the designs as they are redrawn for looks.
 *  <ul><li>every design with levels that is drawn once is in the table (a new one is added by EquipmentTableGameTests);</li>
 *  <li>every frozen cell is free in the level-I design — the core may already stand in it (the hall's seal), the finial may take the ridge cap;</li>
 *  <li>equipment a resident reached when the design was frozen is still reached from a door (the cell or a cell beside it);</li>
 *  <li>a frozen lamp hangs under a full block, and the finial has something under it.</li></ul>
 *  A design change that breaks one of these moves the building's working level in old worlds; redraw the design around the frozen cells. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FrozenEquipmentGameTests {
 private static final BlockState AIR=Blocks.AIR.defaultBlockState();
 private static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 private static boolean open(BlockState s){return s.isAir()||s.getBlock() instanceof DoorBlock;}
 @GameTest(template="empty",batch="frozen_equipment") public static void everyDesignWithLevelsIsFrozen(GameTestHelper h){
  var missing=new ArrayList<String>();
  for(var d:BuildingBlueprints.designs())if(EquipmentTableGameTests.freezable(d.id())&&!LevelArchitecture.frozen(d.id()))missing.add(d.id());
  h.assertTrue(missing.isEmpty(),"Designs with levels not in the frozen equipment table (run EquipmentTableGameTests on the build in the worlds): "+missing);
  h.succeed();
 }
 /** Cells of a frozen design whose equipment a resident reached when it was frozen (the table's "reach" flag). */
 private static Set<BlockPos> mustReach(String type){var out=new HashSet<BlockPos>();
  try(var s=FrozenEquipmentGameTests.class.getResourceAsStream("/data/villageastra/architecture/equipment.json")){
   var types=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("types");
   for(var e:types.getAsJsonObject(type).getAsJsonArray("equipment")){var o=e.getAsJsonObject();if(o.has("reach")&&o.get("reach").getAsBoolean())out.add(new BlockPos(o.get("x").getAsInt(),o.get("y").getAsInt(),o.get("z").getAsInt()));}
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  return out;}
 @GameTest(template="empty",batch="frozen_equipment") public static void frozenCellsStayFreeAndReachable(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var d:BuildingBlueprints.designs()){String type=d.id();if(!LevelArchitecture.frozen(type))continue;
   var raw=BuildingBlueprints.raw(type,BlockPos.ZERO);int w=d.width(),dep=d.depth();
   java.util.function.Function<BlockPos,BlockState> at=p->raw.getOrDefault(p,AIR);
   var equipment=LevelArchitecture.equipment(type);var cells=new HashSet<BlockPos>();for(var p:equipment)cells.add(p.local());
   var reach=EquipmentTableGameTests.reach(raw,w,dep);var mustReach=mustReach(type);
   for(var placed:equipment){var p=placed.local();var now=at.apply(p);var block=placed.state().getBlock();
    if(p.getX()<0||p.getX()>=w||p.getZ()<0||p.getZ()>=dep){problems.add(type+" "+p.toShortString()+" outside the lot");continue;}
    boolean finial=block==Blocks.STONE_BRICK_WALL&&placed.level()==5&&!cells.contains(p.below());
    if(finial){boolean cap=now.getBlock() instanceof SlabBlock||now.getBlock() instanceof StairBlock;
     if(!cap&&!(now.isAir()&&!at.apply(p.below()).isAir()))problems.add(type+" finial "+p.toShortString()+" has no ridge under it: "+now.getBlock()+" over "+at.apply(p.below()).getBlock());continue;}
    if(!now.isAir()&&!(block instanceof BuildingCoreBlock&&now.getBlock() instanceof BuildingCoreBlock)){problems.add(type+" level "+placed.level()+" "+block+" at "+p.toShortString()+" is taken by "+now.getBlock());continue;}
    // Equipment a resident reached in the design it was frozen from is still reached (a cell or one beside it walked to from a door).
    if(mustReach.contains(p)&&!EquipmentTableGameTests.near(reach,p))problems.add(type+" level "+placed.level()+" "+block+" at "+p.toShortString()+" can no longer be walked to from a door");}
   for(var lamp:LevelArchitecture.frozenLamps(type)){
    if(!at.apply(lamp).isAir())problems.add(type+" lamp "+lamp.toShortString()+" is taken by "+at.apply(lamp).getBlock());
    else if(!full(at.apply(lamp.above())))problems.add(type+" lamp "+lamp.toShortString()+" hangs under "+at.apply(lamp.above()).getBlock()+", not a full block");}}
  h.assertTrue(problems.isEmpty(),"Frozen equipment no longer fits its design ("+problems.size()+"): "+problems);
  h.succeed();
 }
}
