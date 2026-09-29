package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
import org.villageastra.persistence.*;
/** AD-126: the fields the construction schematic reads from the executor's queue — each cell's operation, the builder's target and the
 *  next items — are read-only additions to the same view (A07-VIS-002) and fit one packet. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SchematicGameTests {
 private record Fixture(SettlementData.Entry entry,BlockPos site){}
 /** A bare settlement and a flat grassy field, as in ConstructionViewGameTests; a house is ordered on it. */
 private static Fixture order(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new org.villageastra.domain.Settlement(UUID.randomUUID());
  s.addBuilding(new org.villageastra.domain.Settlement.Building(org.villageastra.domain.Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<30;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var site=center.offset(12,0,10);var reason=BuildingOrders.approve(l,e,"home",0,site);
  if(!reason.isEmpty())throw new GameTestAssertException("The house is ordered: "+reason);
  return new Fixture(e,site);
 }
 private static void cleanup(GameTestHelper h,Fixture f){HallUpgradeGoal.drop(h.getLevel(),f.entry.settlement().id());SettlementData.get(h.getLevel().getServer()).remove(f.entry.settlement().id());}
 private static java.nio.file.Path file(GameTestHelper h,UUID village){return h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+village+".bin");}
 /** Every cell of the plan still holds what the queue starts from there: the view put nothing into the world. */
 private static void untouched(GameTestHelper h,ListTag ops){var seen=new HashSet<Long>();
  for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));if(!seen.add(step.pos().asLong()))continue;
   h.assertTrue(h.getLevel().getBlockState(step.pos()).getBlock()==step.before().getBlock(),"The view placed nothing at "+step.pos());}}

 @GameTest(template="empty",timeoutTicks=200) public static void viewNamesOperationsAndTheBuilderTarget(GameTestHelper h){
  var f=order(h);var l=h.getLevel();
  try{var state=HallUpgradeGoal.inspect(l,f.entry.settlement().id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");
   var view=ConstructionViews.nearby(l,f.site);var cells=view.getList("cells",Tag.TAG_COMPOUND);
   h.assertTrue(!cells.isEmpty(),"The ordered house has a view");int last=-1;
   for(var raw:cells){var c=(CompoundTag)raw;h.assertTrue(c.contains("op",Tag.TAG_INT),"Every cell names its operation");h.assertTrue(c.getInt("op")>last,"Operations strictly ascend: "+c.getInt("op")+" after "+last);last=c.getInt("op");
    h.assertTrue(HallConstructionPlan.step(ops.getCompound(c.getInt("op"))).pos().asLong()==c.getLong("pos"),"The op is the cell's own operation");}
   h.assertTrue(cells.getCompound(0).getInt("op")==index,"The first cell is the builder's next operation");
   h.assertTrue(view.contains("target",Tag.TAG_LONG)&&view.getLong("target")==HallConstructionPlan.step(ops.getCompound(index)).pos().asLong(),"The target is the position of ops[index]");
   h.assertTrue(state.equals(HallUpgradeGoal.inspect(l,f.entry.settlement().id())),"Viewing never changes the queue");untouched(h,ops);
  }finally{cleanup(h,f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void targetSkipsWorkDoneOutOfTurn(GameTestHelper h){
  var f=order(h);var l=h.getLevel();
  try{var id=f.entry.settlement().id();var state=HallUpgradeGoal.inspect(l,id);var ops=state.getList("ops",Tag.TAG_COMPOUND);int index=state.getInt("index");
   ops.getCompound(index).putBoolean("done",true);NbtRecord.write(file(h,id),state);
   int next=index+1;while(next<ops.size()&&ops.getCompound(next).getBoolean("done"))next++;
   h.assertTrue(next<ops.size(),"The house has more than one operation");
   var view=ConstructionViews.nearby(l,f.site);var cells=view.getList("cells",Tag.TAG_COMPOUND);
   h.assertTrue(view.getLong("target")==HallConstructionPlan.step(ops.getCompound(next)).pos().asLong(),"The target is the next operation not done (the builder's pointer, HallUpgradeGoal)");
   h.assertTrue(cells.getCompound(0).getInt("op")==next,"That operation is the first cell: "+cells.getCompound(0).getInt("op")+" vs "+next);
  }finally{cleanup(h,f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void upcomingListsTheNextItemsInOrder(GameTestHelper h){
  var f=order(h);var l=h.getLevel();
  try{var state=HallUpgradeGoal.inspect(l,f.entry.settlement().id());var ops=state.getList("ops",Tag.TAG_COMPOUND);
   var expected=new LinkedHashMap<String,Integer>();int seen=0,items=0;
   for(int i=state.getInt("index");i<ops.size()&&seen<32;i++){if(ops.getCompound(i).getBoolean("done"))continue;seen++;var item=HallConstructionPlan.step(ops.getCompound(i)).item();
    if(item.isEmpty())continue;items++;if(expected.containsKey(item)||expected.size()<6)expected.merge(item,1,Integer::sum);}
   var upcoming=ConstructionViews.nearby(l,f.site).getList("upcoming",Tag.TAG_COMPOUND);
   h.assertTrue(upcoming.size()<=6&&upcoming.size()==expected.size(),"At most six rows: "+upcoming.size()+" vs "+expected.size());
   int i=0,sum=0;for(var e:expected.entrySet()){var row=upcoming.getCompound(i++);sum+=row.getInt("count");
    h.assertTrue(row.getString("item").equals(e.getKey())&&row.getInt("count")==e.getValue(),"Row "+i+" in order of first appearance: "+row+" vs "+e);}
   h.assertTrue(expected.size()<6?sum==items:sum<=items,"The counts add up to the item operations among the next 32: "+sum+" of "+items);
   h.assertTrue(items>0,"A house needs items");
  }finally{cleanup(h,f);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void draftViewCarriesTheSameFields(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var village=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(village.id());
  try{var state=HallUpgradeGoal.preview(l,e);var ops=state.getList("ops",Tag.TAG_COMPOUND);
   var view=ConstructionViews.project(l,e,origin,state);var cells=view.getList("cells",Tag.TAG_COMPOUND);
   h.assertTrue(!cells.isEmpty()&&cells.stream().allMatch(c->((CompoundTag)c).contains("op",Tag.TAG_INT)),"Estimate cells name their operations");
   h.assertTrue(view.contains("target",Tag.TAG_LONG)&&view.getLong("target")==HallConstructionPlan.step(ops.getCompound(0)).pos().asLong(),"The estimate has the builder's first step as target");
   h.assertTrue(view.contains("upcoming",Tag.TAG_LIST),"The estimate lists the next items");
   h.assertTrue(!HallUpgradeGoal.pending(l,village.id()),"An estimate queues nothing");untouched(h,ops);
  }finally{HallUpgradeGoal.drop(l,village.id());SettlementData.get(l.getServer()).remove(village.id());}
  h.succeed();
 }
 /** C6: an upper bound of the biggest view — the largest cell times the cell limit, the rest of the view, and 64 KB for the choices,
  *  research, quests and office lines ConstructionNetwork adds to the same tag — stays under the 1 MB packet. */
 @GameTest(template="empty",timeoutTicks=200) public static void largestViewFitsOnePacket(GameTestHelper h){
  var f=order(h);var l=h.getLevel();
  try{var view=ConstructionViews.nearby(l,f.site);var cells=view.getList("cells",Tag.TAG_COMPOUND);
   h.assertTrue(cells.size()<=ConstructionViews.MAX_CELLS,"The cell limit holds");
   int biggest=0;for(var raw:cells)biggest=Math.max(biggest,bytes((CompoundTag)raw));
   var rest=view.copy();rest.remove("cells");long bound=(long)biggest*ConstructionViews.MAX_CELLS+bytes(rest)+65536;
   h.assertTrue(biggest>0&&bound<1_000_000,"Largest view "+bound+" bytes (cell "+biggest+" × "+ConstructionViews.MAX_CELLS+")");
  }finally{cleanup(h,f);}
  h.succeed();
 }
 private static int bytes(CompoundTag t){try{var out=new java.io.ByteArrayOutputStream();NbtIo.write(t,new java.io.DataOutputStream(out));return out.size();}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
}
