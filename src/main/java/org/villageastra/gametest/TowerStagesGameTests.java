package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Tower material projects: paid plan, journal execution, final registration; walking is checked by the client probe separately. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TowerStagesGameTests {
 private record Yard(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos site){}
 private static Yard yard(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,4,2));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var site=center.offset(16,0,8);
  for(int x=-3;x<=8;x++)for(int z=-3;z<=8;z++){l.setBlock(site.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=17;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  return new Yard(l,s,e,site);
 }
 private static void research(Yard t,int level){var r=BookResearch.inspect(t.l,t.e);var done=new ListTag();for(int n=1;n<=level;n++)done.add(StringTag.valueOf("defense."+n));r.put("legacyDone",done);BookResearch.store(t.l,t.e,r);ResearchKnobs.forget(t.s.id());}
 private static void clean(Yard t){HallUpgradeGoal.drop(t.l,t.s.id());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 private static void execute(GameTestHelper h,Yard t,CompoundTag state){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var origin=BlockPos.of(state.getLong("origin"));
  for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);h.assertTrue(BuildingOrders.reconcile(t.l,op,origin),"Operation before state "+i);
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   h.assertTrue(WorldJournal.place(t.l,Settlement.childId(state.getUUID("id"),"block/"+i),step.pos(),step.before(),step.after()),"Journal operation "+i+" "+step.pos());}
  h.assertTrue(BuildingOrders.complete(t.l,t.e,state),"Completed geometry registers its tier");
 }
 @GameTest(template="empty",batch="tower_materials") public static void towerStagesKeepStairsChestAndFiringPorts(GameTestHelper h){
  var stone=BuildingBlueprints.layout(Walls.TOWER,BlockPos.ZERO);
  for(int level:new int[]{2,3,4,5,6}){var cells=BuildingBlueprints.layout(Walls.TOWER+"@"+level,BlockPos.ZERO);
   h.assertTrue(cells.keySet().equals(stone.keySet()),"All levels retain the same cell geometry");
   h.assertTrue(cells.get(new BlockPos(1,1,4)).equals(stone.get(new BlockPos(1,1,4))),"Chest stays intact");
   h.assertTrue(cells.get(new BlockPos(0,2,0)).is(level<4?Blocks.SPRUCE_PLANKS:level==4?Blocks.COBBLESTONE:stone.get(new BlockPos(0,2,0)).getBlock()),"Distinct paid material at level "+level);
   for(var entry:stone.entrySet())if(entry.getValue().getBlock() instanceof StairBlock){var next=cells.get(entry.getKey());h.assertTrue(next.getBlock() instanceof StairBlock&&next.getValue(StairBlock.FACING)==entry.getValue().getValue(StairBlock.FACING)&&next.getValue(StairBlock.HALF)==entry.getValue().getValue(StairBlock.HALF),"Stair direction and headroom preserved");}
  }h.succeed();
 }
 @GameTest(template="empty",batch="tower_new",timeoutTicks=400) public static void towerStagesNewOrderUsesResearchAndRegistersBaseType(GameTestHelper h){
  var t=yard(h);try{research(t,2);var survey=BuildingOrders.survey(t.l,t.e,Walls.TOWER,0,t.site);
   h.assertTrue(survey.ok(),"Wooden tower can be surveyed: "+survey.reason()+survey.conflicts());var state=survey.state();
   h.assertTrue(state.getString("design").equals("wall_tower@2")&&state.getCompound("cost").getInt("minecraft:spruce_planks")>0,"Projection and cost use wood");
   execute(h,t,state);var b=t.s.buildings().stream().filter(x->x.id().equals(BuildingOrders.buildingId(state))).findFirst().orElseThrow();
   h.assertTrue(b.type().equals(Walls.TOWER)&&b.level()==2,"Registered tower keeps base type and physical level");
   research(t,5);h.assertTrue(BuildingSigns.target(t.l,BuildingSigns.position(t.e,b))!=null,"Sign follows physical level, not newly learned research");
  }finally{clean(t);}h.succeed();
 }
 @GameTest(template="empty",batch="tower_rebuild",timeoutTicks=400) public static void towerStagesRebuildRetainsIdentityChestAndWaitsForCompletion(GameTestHelper h){
  var t=yard(h);try{
   var b=new Settlement.Building(UUID.randomUUID(),Walls.TOWER,16,0,8,0,2);t.s.addBuilding(b);
   BuildingPlacement.layout("wall_tower@2",t.site,0).forEach((p,s)->t.l.setBlock(p,s,2));
   var chest=(Container)t.l.getBlockEntity(t.site.offset(1,1,4));chest.setItem(0,new ItemStack(Items.DIAMOND,7));
   for(int level:new int[]{4,5}){research(t,level);var currentBefore=t.s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();var check=BuildingOrders.survey(t.l,t.e,"wall_tower@"+level,0,t.site,currentBefore);h.assertTrue(check.ok(),"Upgrade survey "+level+": "+check.reason()+check.conflicts());h.assertTrue(TowerStages.follow(t.l,t.e).equals("tower_upgrade"),"Research queues a paid upgrade "+level);var state=HallUpgradeGoal.inspect(t.l,t.s.id());var id=state.getUUID("id");
    h.assertTrue(state.getUUID("building").equals(b.id())&&!state.getCompound("cost").isEmpty(),"Same building and nonzero bill");
    h.assertTrue(!BuildingOrders.complete(t.l,t.e,state),"Research alone cannot complete the unbuilt stage");
    TowerStages.follow(t.l,t.e);h.assertTrue(HallUpgradeGoal.inspect(t.l,t.s.id()).getUUID("id").equals(id),"No duplicate active project");
    execute(h,t,state);HallUpgradeGoal.drop(t.l,t.s.id());
    var current=t.s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();h.assertTrue(current.level()==level,"Tier granted only after geometry");
    h.assertTrue(chest==t.l.getBlockEntity(t.site.offset(1,1,4))&&chest.getItem(0).getCount()==7,"Inventory and its block entity survive");
   }
   research(t,2);h.assertTrue(TowerStages.follow(t.l,t.e).isEmpty(),"No downgrade after lower research");
  }finally{clean(t);}h.succeed();
 }
 @GameTest(template="empty",batch="tower_legacy") public static void towerStagesDoNotDowngradeLegacyStone(GameTestHelper h){
  var t=yard(h);try{t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),Walls.TOWER,16,0,8));research(t,4);
   h.assertTrue(TowerStages.follow(t.l,t.e).isEmpty()&&!HallUpgradeGoal.pending(t.l,t.s.id()),"Legacy level-I metadata describes existing stone, never a new wood upgrade");
  }finally{clean(t);}h.succeed();
 }
}
