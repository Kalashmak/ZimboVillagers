package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.FramedWindows;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-142 (owner 2026-09-23): the framed window of every wood — registered with its item, crafted from sticks round a pane (dark oak) and
 *  recoloured eight at a time round a log or planks of any wood, a full wall cell to building rules that lets light through, dropping the
 *  same wood, and made and priced by the village. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FramedWindowGameTests {
 private static Item item(String id){return BuiltInRegistries.ITEM.get(new ResourceLocation(id));}
 private static Block block(String wood){return VillageAstra.FRAMED_WINDOWS.get(wood).get();}
 /** What the crafting grid makes of these nine cells (null: empty), or EMPTY when no recipe matches. */
 private static ItemStack craft(GameTestHelper h,Item... cells){var grid=NonNullList.withSize(9,ItemStack.EMPTY);for(int i=0;i<9;i++)if(cells[i]!=null)grid.set(i,new ItemStack(cells[i]));
  var box=new TransientCraftingContainer(null,3,3,grid);var l=h.getLevel();
  return l.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,box,l).map(r->r.assemble(box,l.registryAccess())).orElse(ItemStack.EMPTY);}
 @GameTest(template="empty") public static void everyWoodIsRegisteredWithItsItem(GameTestHelper h){
  h.assertTrue(FramedWindows.WOODS.size()==11&&VillageAstra.FRAMED_WINDOWS.size()==11,"Eleven woods");
  for(var wood:FramedWindows.WOODS){var b=block(wood);
   h.assertTrue(BuiltInRegistries.BLOCK.getKey(b).toString().equals(FramedWindows.id(wood)),"Block id of "+wood);
   h.assertTrue(b instanceof FramedWindowBlock w&&w.wood().equals(wood),"The block knows its wood: "+wood);
   var it=item(FramedWindows.id(wood));h.assertTrue(it instanceof BlockItem bi&&bi.getBlock()==b,"Item of "+wood+" places its block");
   h.assertTrue(new ItemStack(it).is(net.minecraft.tags.ItemTags.create(new ResourceLocation(FramedWindows.TAG))),"Item tag holds "+wood);
   h.assertTrue(b.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE),"An axe cuts "+wood);}
  h.succeed();
 }
 @GameTest(template="empty") public static void eightSticksRoundAPaneMakeTheDarkOakWindow(GameTestHelper h){
  var s=Items.STICK;var out=craft(h,s,s,s,s,Items.GLASS_PANE,s,s,s,s);
  h.assertTrue(out.is(item(FramedWindows.id(FramedWindows.DEFAULT_WOOD)))&&out.getCount()==1,"Sticks round a pane: one dark oak window, got "+out);
  h.assertTrue(craft(h,s,s,s,s,Items.GLASS,s,s,s,s).isEmpty(),"A glass block is no pane");
  h.succeed();
 }
 /** Eight windows of mixed woods round the planks (and round a log) of each wood: eight windows of that wood. */
 @GameTest(template="empty") public static void eightWindowsRoundAWoodBecomeThatWood(GameTestHelper h){
  var logs=Map.of("bamboo","minecraft:bamboo_block","crimson","minecraft:crimson_stem","warped","minecraft:warped_stem");
  for(var wood:FramedWindows.WOODS){var want=item(FramedWindows.id(wood));
   var w=new Item[8];for(int i=0;i<8;i++)w[i]=item(FramedWindows.id(FramedWindows.WOODS.get((FramedWindows.WOODS.indexOf(wood)+1+i)%11)));
   for(var centre:List.of("minecraft:"+wood+"_planks",logs.getOrDefault(wood,"minecraft:"+wood+"_log"))){var c=item(centre);h.assertTrue(c!=Items.AIR,"Known "+centre);
    var out=craft(h,w[0],w[1],w[2],w[3],c,w[4],w[5],w[6],w[7]);
    h.assertTrue(out.is(want)&&out.getCount()==8,"Eight windows round "+centre+" make eight "+wood+" windows, got "+out);}}
  h.succeed();
 }
 @GameTest(template="empty") public static void aWindowIsAFullWallCellThatLetsLightThrough(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(4,4,4));
  for(var wood:FramedWindows.WOODS)for(var axis:List.of(Direction.Axis.X,Direction.Axis.Z)){
   var s=block(wood).defaultBlockState().setValue(FramedWindowBlock.AXIS,axis);l.setBlock(pos,s,3);s=l.getBlockState(pos);
   h.assertTrue(s.isCollisionShapeFullBlock(l,pos)&&Block.isShapeFullBlock(s.getShape(l,pos)),"A full collision and selection cube: "+s);
   for(var d:Direction.values())h.assertTrue(s.isFaceSturdy(l,pos,d),"Sturdy "+d+" face: "+s);
   h.assertTrue(s.isSolid()&&!s.isSuffocating(l,pos),"A solid cell nobody suffocates in: "+s);
   h.assertTrue(s.getLightBlock(l,pos)==0&&s.propagatesSkylightDown(l,pos)&&!s.isSolidRender(l,pos),"Light passes: "+s);
   // Culling: the frame's four sides are whole, the pane's two faces are not.
   var along=axis==Direction.Axis.X?Direction.EAST:Direction.SOUTH;var across=along.getClockWise();
   h.assertTrue(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.block(),s.getFaceOcclusionShape(l,pos,across),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST),"The pane's face does not hide what is behind it: "+s);
   h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.block(),s.getFaceOcclusionShape(l,pos,along),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST)
    &&!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(net.minecraft.world.phys.shapes.Shapes.block(),s.getFaceOcclusionShape(l,pos,Direction.UP),net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST),"The frame's sides are whole: "+s);}
  l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
  // Real light: a glowstone outside a closed stone room lights it through a window in its wall; a stone in the same place leaves it dark.
  var room=new BlockPos(10,4,10);var dark=new BlockPos(20,4,10);
  for(var c:List.of(room,dark)){for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)h.setBlock(c.offset(x,y,z),x==0&&y==0&&z==0?Blocks.AIR:Blocks.STONE);
   h.setBlock(c.offset(0,0,-2),Blocks.GLOWSTONE);}
  h.setBlock(room.offset(0,0,-1),block("dark_oak").defaultBlockState().setValue(FramedWindowBlock.AXIS,Direction.Axis.X));
  h.succeedWhen(()->{int lit=l.getBrightness(LightLayer.BLOCK,h.absolutePos(room)),shut=l.getBrightness(LightLayer.BLOCK,h.absolutePos(dark));
   h.assertTrue(lit==13&&shut==0,"Glowstone 15, window 14, room 13 (the stone room: 0): lit="+lit+" shut="+shut);});
 }
 /** Set into a gap of a wall it follows the wall; anywhere else it faces the player; a quarter turn of a structure swaps its axis. */
 @GameTest(template="empty") public static void aWindowFollowsTheWallItIsSetInto(GameTestHelper h){
  var player=h.makeMockPlayer();player.setYRot(0);var stack=new ItemStack(item(FramedWindows.id("oak")));
  var free=new BlockPos(4,2,4);var gap=new BlockPos(8,2,4);h.setBlock(gap.north(),Blocks.STONE);h.setBlock(gap.south(),Blocks.STONE);
  java.util.function.Function<BlockPos,BlockState> place=rel->{var at=h.absolutePos(rel);var ctx=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(at),Direction.UP,at,false));
   return block("oak").getStateForPlacement(ctx);};
  h.assertTrue(place.apply(free).getValue(FramedWindowBlock.AXIS)==Direction.Axis.X,"Facing south, a free window's pane faces the player (along X)");
  h.assertTrue(place.apply(gap).getValue(FramedWindowBlock.AXIS)==Direction.Axis.Z,"Between stone north and south, the window runs along Z");
  var x=block("oak").defaultBlockState().setValue(FramedWindowBlock.AXIS,Direction.Axis.X);
  h.assertTrue(x.rotate(Rotation.CLOCKWISE_90).getValue(FramedWindowBlock.AXIS)==Direction.Axis.Z&&x.rotate(Rotation.CLOCKWISE_180).getValue(FramedWindowBlock.AXIS)==Direction.Axis.X,"Rotation swaps the axis on a quarter turn");
  h.succeed();
 }
 @GameTest(template="empty") public static void aBrokenWindowDropsItsOwnWood(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(4,2,4));
  for(var wood:FramedWindows.WOODS){var s=block(wood).defaultBlockState();l.setBlock(pos,s,3);
   var drops=Block.getDrops(s,l,pos,null);
   h.assertTrue(drops.size()==1&&drops.get(0).is(item(FramedWindows.id(wood)))&&drops.get(0).getCount()==1,"A "+wood+" window drops itself: "+drops);}
  l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
  h.succeed();
 }
 /** The village makes every wood's window from its own wood and a pane (bamboo only from bamboo it holds: nobody grows it), and every one
  *  has a price in the level estimate. */
 @GameTest(template="empty",timeoutTicks=200) public static void theVillageMakesAndPricesEveryWindow(GameTestHelper h){
  var t=town(h,null);
  try{
   var dark=FramedWindows.id("dark_oak");
   h.assertTrue(!Workshops.producible(t.l,t.e,dark),"Without wood or glass the village makes no window");
   t.s.addBuilding(new org.villageastra.domain.Settlement.Building(org.villageastra.domain.Settlement.childId(t.s.id(),"record/forester"),ForesterHut.TYPE,-60,0,40));
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.setItem(0,new ItemStack(Items.GLASS_PANE,16));
   for(var wood:FramedWindows.WOODS){var id=FramedWindows.id(wood);
    h.assertTrue(BuildingTiers.value(id)==5,"The estimate prices "+id);
    if(!wood.equals("bamboo"))h.assertTrue(Workshops.producible(t.l,t.e,id),"The forester's wood and the hall's panes make "+id);}
   h.assertTrue(!Workshops.producible(t.l,t.e,FramedWindows.id("bamboo")),"Nobody grows bamboo");
   chest.setItem(1,new ItemStack(Items.BAMBOO_BLOCK,1));
   h.assertTrue(Workshops.producible(t.l,t.e,FramedWindows.id("bamboo")),"A bamboo block in the hall recolours windows to bamboo");
   // The carpentry makes them too, and Engineering II teaches the hall to make them at the builder's pace.
   h.assertTrue(Workshops.spec("carpentry").outputTags().contains(net.minecraft.tags.ItemTags.create(new ResourceLocation(FramedWindows.TAG))),"The carpentry makes framed windows");
   h.assertTrue(Workshops.hallCrafts("engineering.2").contains("#"+FramedWindows.TAG),"Engineering II teaches the hall framed windows");
  }finally{done(t);}
  h.succeed();
 }
}
