package org.villageastra.gametest;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import static org.villageastra.world.FramedWindowBlock.*;
/** Owner 2026-09-24: framed windows (AD-142) of one wood and axis side by side in one plane join into one big window — the frame bar between
 *  them is glass, the outline stays closed (a corner is glass only when the window across it joins too). Other woods, the other axis and a
 *  window behind do not join; breaking one gives its neighbours their frame back; the designs are laid joined (placement sets blocks without
 *  neighbour updates) and come out joined in the world whatever the order; a repair does not count joins as damage. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WindowJoinGameTests {
 private static final List<BooleanProperty> JOINS=List.of(UP,DOWN,LEFT,RIGHT,UP_LEFT,UP_RIGHT,DOWN_LEFT,DOWN_RIGHT);
 private static Block block(String wood){return VillageAstra.FRAMED_WINDOWS.get(wood).get();}
 /** A window set as a player would (its joins read from the neighbours), with neighbour updates. */
 private static void put(GameTestHelper h,int x,int y,int z,String wood,Direction.Axis axis){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,y,z));
  l.setBlock(p,connect(block(wood).defaultBlockState().setValue(AXIS,axis),p,l::getBlockState),3);}
 private static BlockState at(GameTestHelper h,int x,int y,int z){return h.getLevel().getBlockState(h.absolutePos(new BlockPos(x,y,z)));}
 /** The joins of a state by name, e.g. "up right up_right"; "" for a lone window. */
 static String named(BlockState s){var out=new StringJoiner(" ");for(var p:JOINS)if(s.getValue(p))out.add(p.getName());return out.toString();}
 private static void expect(GameTestHelper h,int x,int y,int z,String want){var s=at(h,x,y,z);
  h.assertTrue(s.getBlock() instanceof FramedWindowBlock&&named(s).equals(want),"Window "+x+","+y+","+z+" joins ["+want+"], has ["+(s.getBlock() instanceof FramedWindowBlock?named(s):s.toString())+"]");}
 /** A window laid as a design lays it: its joins given, no neighbour update of its own (flags 2). */
 private static void lay(GameTestHelper h,int x,int y,int z,String wood,BooleanProperty... joins){var s=block(wood).defaultBlockState();for(var p:joins)s=s.setValue(p,true);
  h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),s,Block.UPDATE_CLIENTS);}
 private static boolean wholeFace(GameTestHelper h,int x,int y,int z,Direction d){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,y,z));
  return !Shapes.joinIsNotEmpty(Shapes.block(),l.getBlockState(p).getFaceOcclusionShape(l,p,d),BooleanOp.ONLY_FIRST);}

 @GameTest(template="empty") public static void twoWindowsSideBySideJoin(GameTestHelper h){
  put(h,2,2,2,"oak",Direction.Axis.X);put(h,3,2,2,"oak",Direction.Axis.X);
  expect(h,2,2,2,"right");expect(h,3,2,2,"left");
  h.assertTrue(!wholeFace(h,2,2,2,Direction.EAST)&&!wholeFace(h,3,2,2,Direction.WEST),"The shared side is glass, not a frame: the neighbour behind it is not culled");
  h.assertTrue(wholeFace(h,2,2,2,Direction.WEST)&&wholeFace(h,2,2,2,Direction.UP)&&wholeFace(h,3,2,2,Direction.EAST),"The outer sides stay whole frame");
  // Along Z: LEFT is north.
  put(h,6,2,2,"oak",Direction.Axis.Z);put(h,6,2,3,"oak",Direction.Axis.Z);
  expect(h,6,2,2,"right");expect(h,6,2,3,"left");
  // A player adding a third one to the row: placement reads the axis from the facing and joins it.
  var player=h.makeMockPlayer();player.setYRot(0);var stack=new ItemStack(VillageAstra.FRAMED_WINDOW_ITEMS.get("oak").get());var p=h.absolutePos(new BlockPos(4,2,2));
  var placed=block("oak").getStateForPlacement(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(p),Direction.UP,p,false)));
  h.assertTrue(placed.getValue(AXIS)==Direction.Axis.X&&named(placed).equals("left"),"A placed window joins the row: "+placed);
  h.getLevel().setBlock(p,placed,3);expect(h,3,2,2,"left right");
  // Still a full wall cell and light passes.
  var l=h.getLevel();var mid=h.absolutePos(new BlockPos(3,2,2));var s=l.getBlockState(mid);
  h.assertTrue(s.isCollisionShapeFullBlock(l,mid)&&Block.isShapeFullBlock(s.getShape(l,mid))&&s.getLightBlock(l,mid)==0&&s.propagatesSkylightDown(l,mid),"A joined window is still a full cell that lets light through");
  h.succeed();
 }
 /** A 2x2 and then a 3x2: every inner bar is glass; a corner is glass only where the four windows round it meet — also when the window across
  *  the corner is set last (no shape update reaches a diagonal). */
 @GameTest(template="empty") public static void aGroupJoinsIntoOneBigWindow(GameTestHelper h){
  var x=Direction.Axis.X;
  // The bottom-left one set by hand, its right and upper neighbours laid from a design already joined, the top-right one last: no neighbour of
  // the bottom-left changes then, only the window across its corner tells it.
  put(h,2,2,2,"dark_oak",x);lay(h,3,2,2,"dark_oak",UP,LEFT,UP_LEFT);lay(h,2,3,2,"dark_oak",DOWN,RIGHT,DOWN_RIGHT);
  expect(h,2,2,2,"up right");
  lay(h,3,3,2,"dark_oak",DOWN,LEFT,DOWN_LEFT);
  expect(h,2,2,2,"up right up_right");expect(h,3,2,2,"up left up_left");expect(h,2,3,2,"down right down_right");expect(h,3,3,2,"down left down_left");
  put(h,4,2,2,"dark_oak",x);put(h,4,3,2,"dark_oak",x);
  expect(h,3,2,2,"up left right up_left up_right");expect(h,3,3,2,"down left right down_left down_right");
  expect(h,4,2,2,"up left up_left");expect(h,4,3,2,"down left down_left");
  // An L (no window across the inner corner): that corner stays frame, so the outline is closed.
  put(h,8,2,2,"dark_oak",x);put(h,9,2,2,"dark_oak",x);put(h,8,3,2,"dark_oak",x);
  expect(h,8,2,2,"up right");expect(h,9,2,2,"left");expect(h,8,3,2,"down");
  h.succeed();
 }
 @GameTest(template="empty") public static void otherWoodsAxesAndDepthDoNotJoin(GameTestHelper h){
  put(h,2,2,2,"oak",Direction.Axis.X);put(h,3,2,2,"spruce",Direction.Axis.X);
  put(h,2,3,2,"oak",Direction.Axis.Z);put(h,2,2,3,"oak",Direction.Axis.X);put(h,2,1,2,"oak",Direction.Axis.Z);
  for(var c:List.of(new int[]{2,2,2},new int[]{3,2,2},new int[]{2,3,2},new int[]{2,2,3},new int[]{2,1,2}))expect(h,c[0],c[1],c[2],"");
  h.assertTrue(!joins(block("oak").defaultBlockState(),block("birch").defaultBlockState())&&sameWindow(block("oak").defaultBlockState(),block("oak").defaultBlockState().setValue(UP,true))
   &&!sameWindow(block("oak").defaultBlockState(),block("oak").defaultBlockState().setValue(AXIS,Direction.Axis.Z)),"One wood, one axis");
  h.succeed();
 }
 @GameTest(template="empty") public static void breakingOneGivesTheNeighboursTheirFrameBack(GameTestHelper h){
  var x=Direction.Axis.X;var l=h.getLevel();
  put(h,2,2,2,"birch",x);put(h,3,2,2,"birch",x);put(h,2,3,2,"birch",x);put(h,3,3,2,"birch",x);
  expect(h,2,2,2,"up right up_right");
  l.destroyBlock(h.absolutePos(new BlockPos(3,3,2)),false);
  expect(h,2,2,2,"up right");expect(h,3,2,2,"left");expect(h,2,3,2,"down");
  l.destroyBlock(h.absolutePos(new BlockPos(3,2,2)),false);
  expect(h,2,2,2,"up");
  l.destroyBlock(h.absolutePos(new BlockPos(2,3,2)),false);
  expect(h,2,2,2,"");
  h.assertTrue(wholeFace(h,2,2,2,Direction.EAST)&&wholeFace(h,2,2,2,Direction.UP),"A lone window has its whole frame again");
  h.succeed();
 }
 /** Every current design, level, castle preview, barn and the starter village is laid with its windows joined as they stand; turning a design
  *  a quarter, a half or three quarters and mirroring the barn keeps the joins right. */
 @GameTest(template="empty",timeoutTicks=600) public static void designsAreLaidJoined(GameTestHelper h){
  var problems=new ArrayList<String>();int windows=0,joined=0;var air=Blocks.AIR.defaultBlockState();
  var layouts=DoorJambGameTests.layouts();
  for(var en:layouts.entrySet()){var m=en.getValue();
   for(var c:m.entrySet()){var s=c.getValue();if(!(s.getBlock() instanceof FramedWindowBlock))continue;windows++;if(!named(s).isEmpty())joined++;
    var want=connect(s,c.getKey(),p->m.getOrDefault(p,air));if(want!=s)problems.add(en.getKey()+" "+c.getKey().toShortString()+" ["+named(s)+"] want ["+named(want)+"]");}}
  for(var d:BuildingBlueprints.designs()){var type=d.id();if(type.startsWith("town_hall"))continue;int top=BuildingTiers.upgradable(type)?BuildingTiers.max(type):1;
   for(int turns=1;turns<=3;turns++){var m=BuildingPlacement.layout(BuildingTiers.layoutId(type,top),BlockPos.ZERO,turns);
    for(var c:m.entrySet()){var s=c.getValue();if(!(s.getBlock() instanceof FramedWindowBlock))continue;
     var want=connect(s,c.getKey(),p->m.getOrDefault(p,air));if(want!=s)problems.add(type+"@"+top+" turned "+turns+" "+c.getKey().toShortString()+" ["+named(s)+"] want ["+named(want)+"]");}}}
  for(int level=FarmField.BARN_FROM;level<=6;level++){var m=FarmBarn.layout(level,true);
   for(var c:m.entrySet()){var s=c.getValue();if(s.getBlock() instanceof FramedWindowBlock&&connect(s,c.getKey(),p->m.getOrDefault(p,air))!=s)problems.add("barn west@"+level+" "+c.getKey().toShortString());}}
  LogUtils.getLogger().info("ASTRA_WINDOW_JOIN designs: {} layouts, {} windows, {} joined, {} wrong {}",layouts.size(),windows,joined,problems.size(),problems.stream().limit(10).toList());
  h.assertTrue(problems.isEmpty(),"Designs carry their joins: "+problems.size()+" "+problems.stream().limit(10).toList());
  h.assertTrue(windows>=500&&joined>0,"The designs have windows side by side to join: "+windows+" windows, "+joined+" joined");
  h.succeed();
 }
 /** The smallest design with joined windows, laid like the starter village (flags 2, the design's order) and then its windows set again one by
  *  one like builders (flags 3, backwards): the world ends with exactly the design's joins. */
 @GameTest(template="empty",timeoutTicks=400) public static void aBuiltDesignComesOutJoined(GameTestHelper h){
  String pick=null;int area=Integer.MAX_VALUE;
  for(var d:BuildingBlueprints.designs()){var type=d.id();if(type.startsWith("town_hall"))continue;int top=BuildingTiers.upgradable(type)?BuildingTiers.max(type):1;
   for(int level=1;level<=top;level++){var id=BuildingTiers.layoutId(type,level);
    if(d.width()*d.depth()<area&&BuildingBlueprints.layout(id,BlockPos.ZERO).values().stream().anyMatch(s->s.getBlock() instanceof FramedWindowBlock&&!named(s).isEmpty())){pick=id;area=d.width()*d.depth();}}}
  h.assertTrue(pick!=null,"A design with joined windows");
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(2,2,2));var design=BuildingBlueprints.layout(pick,base);
  design.forEach((p,s)->l.setBlock(p,s,2));
  var wrong=new ArrayList<String>();var windows=new ArrayList<BlockPos>();
  for(var c:design.entrySet())if(c.getValue().getBlock() instanceof FramedWindowBlock){windows.add(c.getKey());if(l.getBlockState(c.getKey())!=c.getValue())wrong.add("laid "+c.getKey().toShortString()+" ["+named(l.getBlockState(c.getKey()))+"] want ["+named(c.getValue())+"]");}
  for(var p:windows)l.setBlock(p,Blocks.AIR.defaultBlockState(),3);
  Collections.reverse(windows);for(var p:windows)l.setBlock(p,design.get(p),3);
  for(var p:windows)if(l.getBlockState(p)!=design.get(p))wrong.add("rebuilt "+p.toShortString()+" ["+named(l.getBlockState(p))+"] want ["+named(design.get(p))+"]");
  LogUtils.getLogger().info("ASTRA_WINDOW_JOIN built {}: {} windows, {} wrong",pick,windows.size(),wrong.size());
  h.assertTrue(wrong.isEmpty(),pick+" comes out as designed: "+wrong);
  h.succeed();
 }
 /** A repair compares the window, not its joins: a house whose windows lost or gained joins (a neighbour window broken, an old world's
  *  window laid before joins) has nothing to repair. */
 @GameTest(template="empty",timeoutTicks=200) public static void aRepairDoesNotCountJoins(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Building(Settlement.childId(s.id(),"building/home-windows"),"home",14,0,0);s.addBuilding(home);
  var base=center.offset(14,0,0);
  for(int x=-1;x<9;x++)for(int z=-1;z<9;z++)for(int y=-3;y<0;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  var design=BuildingBlueprints.layout("home",base);
  for(var cell:design.entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   h.assertTrue(BuildingRepairs.damage(l,e,home).isEmpty(),"An intact home has nothing to repair");
   int windows=0;boolean all=false;
   for(var cell:design.entrySet()){var st=cell.getValue();if(!(st.getBlock() instanceof FramedWindowBlock))continue;windows++;all=!all;
    var other=st.getBlock().defaultBlockState().setValue(AXIS,st.getValue(AXIS));if(all)for(var p:JOINS)other=other.setValue(p,true);
    l.setBlock(cell.getKey(),other,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
    h.assertTrue(BuildingRepairs.present(other,st)&&BuildingRepairs.present(st,other),"A window is present whatever its joins");}
   h.assertTrue(windows>0,"The home has windows");
   var damage=BuildingRepairs.damage(l,e,home);
   h.assertTrue(damage.isEmpty(),"Joins are no damage: "+damage);
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
