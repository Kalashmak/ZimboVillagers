package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.CoreEffects;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
/** AD-131 §3.7: the courtyard grove of a level-VI hut — six cells planted from the hut chest, grown into compact trees GROW_TICKS later, felled
 *  in turn by the one automatic saw into the chest, without the forester or a player. The clock is passed to each turn. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestGroveGameTests {
 private static void stock(Container c,int saplings){for(int i=0;i<c.getContainerSize();i++)c.setItem(i,ItemStack.EMPTY);c.setItem(0,new ItemStack(Items.OAK_SAPLING,saplings));}
 private static List<BlockPos> cells(ForestFixture t){return ForesterHut.grove().stream().map(p->ForesterHut.at(t.e,t.hut(),p)).toList();}
 /** Every turn of the grove from {@code from} to {@code to} (the machines' pace, GROVE_TURN ticks). */
 private static void run(ForestFixture t,long from,long to){for(long now=from;now<=to;now+=ForestBalance.GROVE_TURN)ForestryMachines.grove(t.l,t.e,t.hut(),now);}
 private static ForestFixture six(GameTestHelper h){var t=ForestFixture.create(h,6);
  // The forester is away from the courtyard (the grove needs nobody), the cells stand on the plan's dirt.
  if(t.forester!=null)t.forester.teleportTo(t.wood(-6,0).getX(),t.wood(-6,0).getY(),t.wood(-6,0).getZ());
  for(var c:cells(t)){t.l.setBlock(c.below(),Blocks.DIRT.defaultBlockState(),2);for(int y=0;y<10;y++)t.l.setBlock(c.above(y),Blocks.AIR.defaultBlockState(),2);}
  return t;}
 @GameTest(template="empty",batch="grove_grows",timeoutTicks=300) public static void theCourtyardGroveGrowsAndIsFelledWithoutAnyone(GameTestHelper h){
  var t=six(h);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.hut())==6&&CoreEffects.value("forester","grove",6)==6&&CoreEffects.value("forester","grove",5)==0,"The hut works at VI with its six grove places");
   var c=t.chestBlock();stock(c,6);
   run(t,0,0);
   h.assertTrue(cells(t).stream().allMatch(p->t.l.getBlockState(p).is(Blocks.OAK_SAPLING))&&c.countItem(Items.OAK_SAPLING)==0,"All six cells are planted from the chest");
   run(t,20,ForestBalance.GROW_TICKS-20);
   h.assertTrue(cells(t).stream().allMatch(p->t.l.getBlockState(p).is(Blocks.OAK_SAPLING)),"Nothing grows before its time");
   run(t,ForestBalance.GROW_TICKS,ForestBalance.GROW_TICKS);
   h.assertTrue(cells(t).stream().allMatch(p->t.l.getBlockState(p).is(BlockTags.LOGS)),"At "+ForestBalance.GROW_TICKS+" ticks every cell holds a tree");
   run(t,ForestBalance.GROW_TICKS+20,ForestBalance.GROW_TICKS+1200);
   var g=ForestryMachines.inspect(t.l,t.hutId);
   h.assertTrue(g.getInt("groveTrees")>=6&&c.countItem(Items.OAK_LOG)>=6*4,"The saw felled the six trees into the chest: "+g.getInt("groveTrees")+" trees, "+c.countItem(Items.OAK_LOG)+" logs");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="grove_only",timeoutTicks=300) public static void onlyTheGroveCellsGrowFaster(GameTestHelper h){
  var t=six(h);
  try{
   var c=t.chestBlock();stock(c,6);var outside=t.wood(4,4);t.l.setBlock(outside,Blocks.OAK_SAPLING.defaultBlockState(),2);
   var wall=ForesterHut.at(t.e,t.hut(),new BlockPos(7,1,22));t.l.setBlock(wall.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);t.l.setBlock(wall,Blocks.OAK_SAPLING.defaultBlockState(),2);
   run(t,0,ForestBalance.GROW_TICKS);
   h.assertTrue(cells(t).stream().allMatch(p->t.l.getBlockState(p).is(BlockTags.LOGS)),"The grove has grown");
   h.assertTrue(t.l.getBlockState(outside).is(Blocks.OAK_SAPLING)&&t.l.getBlockState(wall).is(Blocks.OAK_SAPLING),"A sapling outside the courtyard, even behind its wall, is untouched");
   h.assertTrue(ForestryMachines.groveCell(t.l,cells(t).get(0))&&!ForestryMachines.groveCell(t.l,outside),"Only the six cells are the grove's");
  }finally{t.done();}
  h.succeed();
 }
 /** A felling whose record was lost (a stop after the journal batch, before the record was written) replays under the same ids: the chest
  *  gets the tree once. */
 @GameTest(template="empty",batch="grove_replay",timeoutTicks=300) public static void theGroveReplaysAfterAStop(GameTestHelper h){
  var t=six(h);
  try{
   var c=t.chestBlock();stock(c,6);run(t,0,ForestBalance.GROW_TICKS);
   long now=ForestBalance.GROW_TICKS+20;CompoundTag before=null;
   for(;now<ForestBalance.GROW_TICKS+2000;now+=ForestBalance.GROVE_TURN){var r=ForestryMachines.inspect(t.l,t.hutId);ForestryMachines.grove(t.l,t.e,t.hut(),now);if(ForestryMachines.inspect(t.l,t.hutId).getInt("groveTrees")==1){before=r;break;}}
   h.assertTrue(before!=null,"The first tree was felled");
   int logs=c.countItem(Items.OAK_LOG),saplings=c.countItem(Items.OAK_SAPLING),sticks=c.countItem(Items.STICK);
   NbtRecord.write(ForestryMachines.path(t.l,t.hutId),before);
   ForestryMachines.grove(t.l,t.e,t.hut(),now);
   h.assertTrue(c.countItem(Items.OAK_LOG)==logs&&c.countItem(Items.OAK_SAPLING)<=saplings&&c.countItem(Items.STICK)==sticks,"The replay brings nothing twice: "+c.countItem(Items.OAK_LOG)+" logs (was "+logs+")");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="grove_full",timeoutTicks=300) public static void aFullChestStopsTheGroveSaw(GameTestHelper h){
  var t=six(h);
  try{
   var c=t.chestBlock();stock(c,6);run(t,0,ForestBalance.GROW_TICKS);
   for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty())c.setItem(i,new ItemStack(Items.COBBLESTONE,64));
   run(t,ForestBalance.GROW_TICKS+20,ForestBalance.GROW_TICKS+600);
   var g=ForestryMachines.inspect(t.l,t.hutId);
   h.assertTrue(g.getInt("groveTrees")==0&&cells(t).stream().allMatch(p->t.l.getBlockState(p).is(BlockTags.LOGS)),"With no room in the chest the saw stands: "+g.getInt("groveTrees"));
   h.assertTrue(g.getString("sawStatusGrove").equals("the_chest_is_full"),"And says why: "+g.getString("sawStatusGrove"));
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="grove_rate",timeoutTicks=300) public static void theGroveYieldsItsRate(GameTestHelper h){
  var t=six(h);
  try{
   var c=t.chestBlock();stock(c,64);run(t,0,2400);
   var g=ForestryMachines.inspect(t.l,t.hutId);
   h.assertTrue(g.getInt("groveTrees")>=12,"At least 12 trees in 2400 ticks of turns: "+g.getInt("groveTrees")+" trees, "+g.getInt("groveLogs")+" logs");
  }finally{t.done();}
  h.succeed();
 }
}
