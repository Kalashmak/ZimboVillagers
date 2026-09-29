package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Design review 2026-09-24 (FenceJoins): a laid design carries the fence and pane joins the game gives a built house, leaves the framed windows'
 *  own joins alone, and a fence, pane or wall standing with other sides is the design's — no order or repair sets it again. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FenceJoinGameTests {
 /** A small scene: a fence rail against a stone block and a gate, a pane in a wall gap, bars against a fence, a lone fence post, a framed window
  *  beside the pane. */
 private static Map<BlockPos,BlockState> scene(){var m=new LinkedHashMap<BlockPos,BlockState>();
  m.put(new BlockPos(1,1,1),Blocks.STONE_BRICKS.defaultBlockState());m.put(new BlockPos(2,1,1),Blocks.SPRUCE_FENCE.defaultBlockState());
  m.put(new BlockPos(3,1,1),Blocks.SPRUCE_FENCE.defaultBlockState());
  m.put(new BlockPos(4,1,1),Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING,net.minecraft.core.Direction.NORTH));
  m.put(new BlockPos(1,1,3),Blocks.STONE_BRICKS.defaultBlockState());m.put(new BlockPos(2,1,3),Blocks.GLASS_PANE.defaultBlockState());
  m.put(new BlockPos(3,1,3),Blocks.STONE_BRICKS.defaultBlockState());m.put(new BlockPos(2,1,4),Blocks.IRON_BARS.defaultBlockState());
  m.put(new BlockPos(2,1,5),Blocks.DARK_OAK_FENCE.defaultBlockState());m.put(new BlockPos(5,1,5),Blocks.DARK_OAK_FENCE.defaultBlockState());
  m.put(new BlockPos(2,2,3),VillageAstra.FRAMED_WINDOWS.get("dark_oak").get().defaultBlockState());
  m.put(new BlockPos(3,2,3),VillageAstra.FRAMED_WINDOWS.get("dark_oak").get().defaultBlockState());
  return m;}
 @GameTest(template="empty",batch="fence_join") public static void aLaidDesignJoinsAsTheBuiltHouse(GameTestHelper h){
  var design=scene();
  // The world: each block set with neighbour updates, as a builder sets it.
  for(var e:design.entrySet())h.getLevel().setBlock(h.absolutePos(e.getKey()),e.getValue(),3);
  var windows=new HashMap<BlockPos,BlockState>();for(var e:design.entrySet())if(e.getValue().getBlock() instanceof FramedWindowBlock)windows.put(e.getKey(),e.getValue());
  FenceJoins.join(design);
  for(var e:design.entrySet()){var b=e.getValue().getBlock();if(!(b instanceof FenceBlock)&&!(b instanceof IronBarsBlock))continue;
   // The game's own joins for the block standing among its neighbours (what a neighbour update settles on).
   var at=h.absolutePos(e.getKey());var world=Block.updateFromNeighbourShapes(h.getLevel().getBlockState(at),h.getLevel(),at);
   h.assertTrue(world.equals(e.getValue()),"At "+e.getKey().toShortString()+" the design has "+e.getValue()+" but the built house "+world);}
  for(var e:windows.entrySet())h.assertTrue(design.get(e.getKey())==e.getValue(),"FenceJoins left the framed window at "+e.getKey().toShortString()+" alone");
  h.assertTrue(design.get(new BlockPos(2,1,1)).getValue(FenceBlock.WEST)&&design.get(new BlockPos(3,1,1)).getValue(FenceBlock.EAST),"The rail joins the stone and the gate");
  h.assertTrue(!design.get(new BlockPos(5,1,5)).getValue(FenceBlock.NORTH)&&!design.get(new BlockPos(5,1,5)).getValue(FenceBlock.WEST),"A lone post stays a post");
  h.succeed();
 }
 @GameTest(template="empty",batch="fence_join") public static void otherSidesAreStillTheDesignsBlock(GameTestHelper h){
  var post=Blocks.SPRUCE_FENCE.defaultBlockState();var rail=post.setValue(FenceBlock.EAST,true).setValue(FenceBlock.WEST,true);
  var pane=Blocks.GLASS_PANE.defaultBlockState();var wall=Blocks.STONE_BRICK_WALL.defaultBlockState();
  h.assertTrue(FenceJoins.sameJoinable(rail,post)&&FenceJoins.sameJoinable(post,rail)&&FenceJoins.sameJoinable(pane.setValue(IronBarsBlock.NORTH,true),pane)
   &&FenceJoins.sameJoinable(wall.setValue(WallBlock.UP,false),wall),"A fence, pane or wall with other sides is the design's (orders)");
  h.assertTrue(!FenceJoins.sameJoinable(Blocks.OAK_FENCE.defaultBlockState(),post)&&!FenceJoins.sameJoinable(Blocks.IRON_BARS.defaultBlockState(),pane)
   &&!FenceJoins.sameJoinable(Blocks.STONE_BRICKS.defaultBlockState(),Blocks.STONE_BRICKS.defaultBlockState()),"Another block is not, and a full block is compared as before");
  h.assertTrue(BuildingRepairs.present(rail,post)&&BuildingRepairs.present(pane.setValue(IronBarsBlock.EAST,true),pane),"Repairs take it as standing");
  h.succeed();
 }
}
