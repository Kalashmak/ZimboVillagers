package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
/** Owner 2026-09-19, AD-131: the forester fells a whole tree from its foot. Standing at the trunk he chops the lowest log for a long while —
 *  three seconds a log of the tree at level I — and then the whole tree, its branches and its crown come down into his load, each log and
 *  leaf under its own journal entry in one batch. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForesterGameTests {
 /** A wild oak of six logs with a branch off its fifth; returns its logs, foot first. */
 private static List<BlockPos> oak(ForestFixture t){var logs=new ArrayList<>(ForestWork.wildOak(t.l,t.wood(4,1),6));var branch=logs.get(4).east();t.l.setBlock(branch,Blocks.OAK_LOG.defaultBlockState(),2);logs.add(branch);return logs;}
 @GameTest(template="empty",batch="forester_whole",timeoutTicks=400) public static void aForesterFellsTheWholeTreeFromItsFoot(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=oak(t);int before=t.chestBlock().countItem(Items.OAK_LOG);
   var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   int[] chops={0};CompoundTag[] felled={null};
   t.drive(goal,600,r->{if(r.getString("stage").equals("dig")&&r.getInt("labor")>0)chops[0]++;if(felled[0]==null&&r.getString("stage").equals("deliver"))felled[0]=r;return felled[0]!=null&&r.getString("stage").equals("choose");});
   h.assertTrue(felled[0]!=null,"The tree came down");
   h.assertTrue(chops[0]>=ResourceWorkGoal.treeLabor(logs.size())/ResourceWorkGoal.fellingLabor(1)-1,"Felling a tree of "+logs.size()+" logs takes a long chop: "+chops[0]+" calls");
   h.assertTrue(logs.stream().allMatch(p->t.l.getBlockState(p).isAir()),"Every log of the tree is down, the branch too");
   h.assertTrue(ForestFixture.count(felled[0].getList("cargo",Tag.TAG_COMPOUND),Items.OAK_LOG)==logs.size(),"He carries all "+logs.size()+" logs home: "+felled[0].getList("cargo",Tag.TAG_COMPOUND));
   h.assertTrue(ItemStack.of(felled[0].getCompound("tool")).getDamageValue()==logs.size(),"One axe point a log: "+ItemStack.of(felled[0].getCompound("tool")));
   h.assertTrue(t.chestBlock().countItem(Items.OAK_LOG)==before+logs.size(),"The logs are in the hut's chest: "+t.chestBlock().countItem(Items.OAK_LOG));
  }finally{t.done();}
  h.succeed();
 }
 /** A forester who drops out in the middle of felling carries exactly the logs and the leaves' loot that came down, once, and the axe wear the logs cost. */
 @GameTest(template="empty",batch="forester_stopped",timeoutTicks=200) public static void aForesterStoppedMidFellingCarriesTheLogsThatFell(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=oak(t);var tree=ForestWork.candidate(t.l,t.e,t.hut(),logs.get(0),32).tree();h.assertTrue(tree!=null,"The oak is his");
   var id=UUID.randomUUID();var states=new ListTag();for(var p:tree.logs())states.add(NbtUtils.writeBlockState(t.l.getBlockState(p)));var axe=new ItemStack(Items.STONE_AXE);
   for(int i=0;i<3;i++)h.assertTrue(WorldJournal.harvest(t.l,Settlement.childId(id,"log/"+i),tree.logs().get(i),Blocks.OAK_LOG.defaultBlockState(),axe)!=null,"Log "+i+" came down before the stop");
   var leafLoot=WorldJournal.harvest(t.l,Settlement.childId(id,"leaf/0"),tree.leaves().get(0),t.l.getBlockState(tree.leaves().get(0)),axe);
   var state=new CompoundTag();state.putInt("schema",2);state.putUUID("worker",t.forester.getUUID());state.putUUID("operation",id);state.putString("stage","dig");state.putLong("target",tree.foot().asLong());
   state.put("tool",axe.save(new CompoundTag()));state.putLongArray("tree",tree.logs().stream().mapToLong(BlockPos::asLong).toArray());state.put("treeBefore",states);
   state.putLongArray("treeLeaves",tree.leaves().stream().mapToLong(BlockPos::asLong).toArray());state.putInt("labor",ResourceWorkGoal.treeLabor(tree.logs().size()));
   t.write(state);
   var snap=JobCargo.snapshot(t.forester,true);
   h.assertTrue(ForestFixture.count(snap.items(),Items.OAK_LOG)==3,"Only the three logs that fell are carried: "+snap.items());
   for(var s:leafLoot)if(!s.is(Items.OAK_LOG))h.assertTrue(ForestFixture.count(snap.items(),s.getItem())>=s.getCount(),"And the leaf's loot: "+s);
   var tool=snap.items().stream().map(x->ItemStack.of((CompoundTag)x)).filter(i->i.is(Items.STONE_AXE)).findFirst().orElseThrow();
   h.assertTrue(tool.getDamageValue()==3,"And they wore the axe three points: "+tool);
   h.assertTrue(tree.logs().subList(3,tree.logs().size()).stream().allMatch(p->t.l.getBlockState(p).is(Blocks.OAK_LOG)),"The rest of the tree still stands for the next forester");
   var reset=snap.jobs().getCompound(0).getCompound("reset");
   h.assertTrue(reset.getString("stage").equals("tool")&&!reset.contains("tree")&&!reset.contains("treeLeaves"),"The successor starts over: "+reset);
  }finally{t.done();}
  h.succeed();
 }
}
