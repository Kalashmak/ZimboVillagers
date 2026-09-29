package org.villageastra.gametest;
import java.util.*;
import com.mojang.logging.LogUtils;
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
/** DOOR-JAMBS (owner 2026-09-23, AD-144): every design the village builds or previews — each catalogue design at each of its levels, every
 *  annex, the town hall's own blueprints, the castle preview I..VI, the farm's barn and the starter village as it is laid out — keeps full
 *  blocks at the jambs of every door: in the wall plane left and right of both door halves stands no fence, fence gate, glass pane, iron bars,
 *  wall or trapdoor (such a block by a door reads as a hole between the door and a window). And the current designs glaze with the framed window
 *  (AD-142), not plain glass panes; iron bars stay only where a design means bars (arrow slits, a portcullis), never by a door. The frozen
 *  designs of old worlds (Legacy117*, ArchitectureMigration) are not walked: they stay as they were built. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DoorJambGameTests {
 /** A block that needs a connection to look whole: it may not stand at a door's jamb. */
 static boolean connecting(BlockState s){var b=s.getBlock();
  return b instanceof FenceBlock||b instanceof FenceGateBlock||b instanceof IronBarsBlock||b instanceof WallBlock||b instanceof TrapDoorBlock;}
 /** A jamb: a full block that is not a connecting one, or the other leaf of a double door. */
 static boolean jamb(BlockState s){return s.getBlock() instanceof DoorBlock||!connecting(s)&&!s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 /** Every walked layout by name, lot corner at the origin. */
 static Map<String,Map<BlockPos,BlockState>> layouts(){
  var out=new LinkedHashMap<String,Map<BlockPos,BlockState>>();
  for(var d:BuildingBlueprints.designs()){var type=d.id();if(type.startsWith("town_hall"))continue;
   int top=BuildingTiers.upgradable(type)?BuildingTiers.max(type):1;
   for(int level=1;level<=top;level++){var id=BuildingTiers.layoutId(type,level);out.put(id,BuildingBlueprints.layout(id,BlockPos.ZERO));}}
  for(int level=1;level<=BuildingTiers.MAX;level++){var id=BuildingTiers.layoutId("town_hall",level);out.put(id,BuildingBlueprints.layout(id,BlockPos.ZERO));}
  for(int level=1;level<=6;level++)out.put(CastleArchitecture.PREVIEW_PREFIX+level,CastleArchitecture.shell(level,BlockPos.ZERO));
  for(int level=FarmField.BARN_FROM;level<=6;level++)out.put("farm_barn@"+level,FarmBarn.layout(level));
  out.put("starter_village",StarterVillage.layout(BlockPos.ZERO));
  return out;
 }
 /** Every door of a layout whose jamb (left or right of either half, in the wall plane) is not a full block: "cell: block". */
 static List<String> jambs(Map<BlockPos,BlockState> layout){
  var out=new ArrayList<String>();
  for(var en:layout.entrySet()){var s=en.getValue();if(!(s.getBlock() instanceof DoorBlock)||s.getValue(DoorBlock.HALF)!=DoubleBlockHalf.LOWER)continue;
   var side=s.getValue(DoorBlock.FACING).getClockWise();
   for(int y=0;y<=1;y++)for(var d:List.of(side,side.getOpposite())){var p=en.getKey().above(y).relative(d);var n=layout.getOrDefault(p,Blocks.AIR.defaultBlockState());
    if(!jamb(n))out.add("door "+en.getKey().toShortString()+" jamb "+p.toShortString()+" "+(connecting(n)?"CONNECTING ":"")+n);}}
  return out;
 }
 /** Plain glass panes (any colour) of a layout; iron bars are not glass. */
 static List<String> panes(Map<BlockPos,BlockState> layout){
  var out=new ArrayList<String>();
  for(var en:layout.entrySet()){var s=en.getValue();if(s.getBlock() instanceof IronBarsBlock&&!s.is(Blocks.IRON_BARS))out.add(en.getKey().toShortString());}
  return out;
 }
 @GameTest(template="empty",timeoutTicks=400) public static void everyDoorHasFullJambs(GameTestHelper h){
  var problems=new ArrayList<String>();int doors=0,designs=0;
  for(var en:layouts().entrySet()){designs++;
   for(var s:en.getValue().values())if(s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER)doors++;
   var bad=jambs(en.getValue());if(!bad.isEmpty()){problems.add(en.getKey()+" x"+bad.size()+" "+bad);LogUtils.getLogger().info("ASTRA_DOOR_JAMBS {} {}",en.getKey(),bad);}}
  LogUtils.getLogger().info("ASTRA_DOOR_JAMBS walked {} layouts, {} doors, {} with bad jambs",designs,doors,problems.size());
  h.assertTrue(designs>=150&&doors>=150,"Every design and level is walked: "+designs+" layouts, "+doors+" doors");
  h.assertTrue(problems.isEmpty(),"Door jambs are full blocks: "+problems);
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void currentDesignsGlazeWithFramedWindows(GameTestHelper h){
  var problems=new ArrayList<String>();int windows=0;
  for(var en:layouts().entrySet()){
   for(var s:en.getValue().values())if(s.getBlock() instanceof FramedWindowBlock)windows++;
   var bad=panes(en.getValue());if(!bad.isEmpty()){problems.add(en.getKey()+" x"+bad.size());LogUtils.getLogger().info("ASTRA_DOOR_JAMBS panes {} {}",en.getKey(),bad);}}
  h.assertTrue(problems.isEmpty(),"No plain glass pane in a current design: "+problems);
  h.assertTrue(windows>=500,"The designs are glazed with framed windows: "+windows);
  h.succeed();
 }
 /** The window's frame follows the wall: a framed window set in a wall along x has its axis x (and one in a wall along z, axis z), so its
  *  glass faces out of the house, not along the wall. */
 @GameTest(template="empty",timeoutTicks=400) public static void framedWindowsFaceOutOfTheirWalls(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var en:layouts().entrySet()){var m=en.getValue();
   for(var c:m.entrySet()){var s=c.getValue();if(!(s.getBlock() instanceof FramedWindowBlock))continue;var p=c.getKey();
    boolean x=solid(m,p.east())||solid(m,p.west()),z=solid(m,p.north())||solid(m,p.south());
    var axis=s.getValue(FramedWindowBlock.AXIS);
    // A window between walls on one axis only must lie along that axis; a corner or a free-standing one is not judged.
    if(x!=z&&(x?Direction.Axis.X:Direction.Axis.Z)!=axis)problems.add(en.getKey()+" "+p.toShortString()+" "+axis);}}
  h.assertTrue(problems.isEmpty(),"Framed windows lie along their walls: "+problems);
  h.succeed();
 }
 /** A village with its forester's wood and panes in the hall (what the glass panes of the designs needed before) makes every framed window
  *  the designs take — at level I, in each level's estimate, in the barn's and the annexes' — so an NPC mayor may still order every level
  *  (MayorPlanner.affordable: every item short is Workshops.producible). */
 @GameTest(template="empty",timeoutTicks=300) public static void theVillageMakesEveryWindowTheDesignsTake(GameTestHelper h){
  var windows=new TreeSet<String>();
  for(var m:layouts().values())for(var s:m.values())if(s.getBlock() instanceof FramedWindowBlock)windows.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
  for(var d:BuildingBlueprints.designs())if(BuildingTiers.upgradable(d.id()))for(int level=2;level<=BuildingTiers.max(d.id());level++)
   for(var item:BuildingTiers.cost(d.id(),level).keySet())if(org.villageastra.domain.FramedWindows.wood(item)!=null)windows.add(item);
  for(int level=FarmField.BARN_FROM;level<=6;level++)for(var item:FarmBarn.addedCost(level).keySet())if(org.villageastra.domain.FramedWindows.wood(item)!=null)windows.add(item);
  h.assertTrue(windows.containsAll(List.of(org.villageastra.domain.FramedWindows.id("dark_oak"),org.villageastra.domain.FramedWindows.id("spruce"))),"Dark oak and spruce windows: "+windows);
  var t=ResearchV2Town.town(h,null);
  try{
   t.s.addBuilding(new org.villageastra.domain.Settlement.Building(org.villageastra.domain.Settlement.childId(t.s.id(),"record/forester"),ForesterHut.TYPE,-60,0,40));
   LogisticsRoutes.chest(t.l,t.e,t.hall()).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_PANE,16));
   for(var id:windows){h.assertTrue(Workshops.producible(t.l,t.e,id),"The village makes "+id);
    h.assertTrue(MayorPlanner.affordable(t.l,t.e,Map.of(id,8)),"An estimate short of "+id+" may still be ordered");}
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }
 private static boolean solid(Map<BlockPos,BlockState> m,BlockPos p){var s=m.get(p);return s!=null&&!s.isAir()&&(s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO)||s.getBlock() instanceof IronBarsBlock);}
}
