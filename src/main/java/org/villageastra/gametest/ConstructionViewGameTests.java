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
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionViewGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void viewFollowsPaidQueueWithoutWorldMutation(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var village=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(village.id());HallUpgradeGoal.request(l,e);
  var state=HallUpgradeGoal.inspect(l,village.id());var original=state.copy();var plan=HallConstructionPlan.read(state);var view=ConstructionViews.nearby(l,origin);
  h.assertTrue(plan.operations().size()==view.getInt("total")&&view.getList("cells",Tag.TAG_COMPOUND).size()==plan.operations().size(),"View is the executor queue, not a replacement blueprint");
  h.assertTrue(view.getInt("conflicts")==0&&view.getInt("hidden")==0,"Initial approved project is consistent");
  for(var raw:view.getList("materials",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;h.assertTrue(plan.materials().get(row.getString("item"))==row.getInt("required"),"Estimate and physical queue agree");}
  h.assertTrue(original.equals(HallUpgradeGoal.inspect(l,village.id())),"Viewing never modifies funding or work state");
  h.assertTrue(!ConstructionViews.nearby(l,origin.offset(100,0,0)).hasUUID("id"),"Distant project not disclosed");
  var op=state.getList("ops",Tag.TAG_COMPOUND).getCompound(0);var step=HallConstructionPlan.step(op);l.setBlock(step.pos(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
  var changed=ConstructionViews.nearby(l,origin);h.assertTrue(changed.getInt("conflicts")==1&&l.getBlockState(step.pos()).is(Blocks.GOLD_BLOCK),"External block is a conflict, never authorized demolition");
  l.setBlock(step.pos(),step.before(),3);h.assertTrue(WorldJournal.place(l,plan.operations().get(0).id(),step.pos(),step.before(),step.after()),"Existing execution journal applies exact step");state.putInt("index",1);
  var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+village.id()+".bin");NbtRecord.write(file,state);
  var next=ConstructionViews.nearby(l,origin);h.assertTrue(next.getList("cells",Tag.TAG_COMPOUND).size()==view.getList("cells",Tag.TAG_COMPOUND).size()-1&&next.getInt("index")==1,"Completed operation disappears from both view and estimate");
  state.putBoolean("complete",true);NbtRecord.write(file,state);h.assertTrue(!ConstructionViews.nearby(l,origin).hasUUID("id"),"Complete project has no ghost");h.succeed();
 }

 /** OWNER_REQUEST 9.10–9.11: bushes stand on the plot of an ordered house. Once the crew has cleared them the same order goes on — the
  *  ghost shows the rest of the work where they stood, nothing has to be placed again, and only what is left is drawn. */
 @GameTest(template="empty",timeoutTicks=200) public static void afterClearingTheGhostShowsTheRestWithoutANewOrder(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new org.villageastra.domain.Settlement(UUID.randomUUID());
  s.addBuilding(new org.villageastra.domain.Settlement.Building(org.villageastra.domain.Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<30;x++)for(int z=-2;z<26;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var site=center.offset(12,0,10);
  var leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
  var bushes=List.of(site.offset(2,1,2),site.offset(3,1,3),site.offset(2,2,2),site.offset(4,1,2));for(var b:bushes)l.setBlock(b,leaves,2);
  try{
   var reason=BuildingOrders.approve(l,e,"home",0,site);h.assertTrue(reason.isEmpty(),"The house is ordered over the bushes: "+reason);
   var state=HallUpgradeGoal.inspect(l,s.id());var id=HallConstructionPlan.projectId(state);var ops=state.getList("ops",Tag.TAG_COMPOUND);
   int lastBush=-1;for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));if(bushes.contains(step.pos())&&step.before().is(Blocks.OAK_LEAVES))lastBush=i;}
   h.assertTrue(lastBush>=0,"Clearing the bushes is work of the plan");
   // OWNER_REQUEST 9.16: the order and its ghost put nothing into the world — every cell of the plan still holds what it held, so the
   // ghost has no collision, light, drop or redstone of its own.
   // (Only the first operation of a cell is compared with the world: a later one starts from what the earlier ones leave there.)
   var firstSeen=new HashSet<Long>();
   for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));if(!firstSeen.add(step.pos().asLong()))continue;var now=l.getBlockState(step.pos());
    h.assertTrue(now.getBlock()==step.before().getBlock(),"The order placed nothing at "+step.pos().subtract(site).toShortString()+": "+now+" (plan starts from "+step.before()+")");}
   var before=ConstructionViews.nearby(l,site);
   h.assertTrue(before.getInt("conflicts")==0,"Bushes the plan clears are no conflict: "+before.getInt("conflicts"));
   HallUpgradeGoal.advanceForProbe(l,s.id(),lastBush+1);
   h.assertTrue(bushes.stream().noneMatch(b->l.getBlockState(b).is(Blocks.OAK_LEAVES)),"The crew has cleared them");
   var after=ConstructionViews.nearby(l,site);
   h.assertTrue(after.hasUUID("id")&&after.getUUID("id").equals(id)&&after.getInt("conflicts")==0,"The same order goes on with nothing in the way: "+after.getInt("conflicts"));
   var cells=after.getList("cells",Tag.TAG_COMPOUND);
   h.assertTrue(cells.size()+after.getInt("hidden")==ops.size()-(lastBush+1),"Only the work still to do is drawn: "+cells.size()+"+"+after.getInt("hidden")+" of "+(ops.size()-lastBush-1));
   for(var raw:cells){var c=(CompoundTag)raw;h.assertTrue(!c.getBoolean("blocked"),"No remaining cell is marked blocked: "+BlockPos.of(c.getLong("pos")).subtract(site).toShortString());}
   h.assertTrue(!cells.isEmpty(),"The ghost of the house is still there after the clearing, without a new order");
  }finally{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
