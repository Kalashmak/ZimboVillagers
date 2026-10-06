package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ToolReserveGameTests {
 @GameTest(template="empty",batch="science_tool_buffer",timeoutTicks=200)
 public static void scienceLeavesThePostedForestersLastAxeForActualWork(GameTestHelper h){
  var t=ResearchV2Town.town(h,"forester");try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.FORESTER,t.shop.id());
   var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.OAK_LOG,32));c.setItem(1,new ItemStack(Items.STONE_AXE));
   h.assertTrue(!BookResearch.payResources(t.l,t.e,"forestry.1")&&c.countItem(Items.OAK_LOG)==32&&c.countItem(Items.STONE_AXE)==1,"Research cannot consume the sole axe before the forester has even started a record");
   c.setItem(2,new ItemStack(Items.STONE_AXE));h.assertTrue(BookResearch.payResources(t.l,t.e,"forestry.1")&&c.countItem(Items.STONE_AXE)==1&&c.countItem(Items.OAK_LOG)==0,"An actual spare axe pays science while the worker retains one");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="science_tool_buffer",timeoutTicks=200)
 public static void scienceKeepsRepairInputsBeforeTheWorkRecordExists(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.COBBLESTONE,64));c.setItem(1,new ItemStack(Items.OAK_LOG,4));
   h.assertTrue(ToolSupplyReserve.needed(t.l,t.e),"A posted miner without a work file still needs a pick");h.assertTrue(BookResearch.have(t.l,t.e,new ResearchCatalog.Cost("minecraft:cobblestone",null,64))==61,"Science keeps three real stones for maintenance");h.assertTrue(BookResearch.have(t.l,t.e,new ResearchCatalog.Cost(null,"minecraft:logs",4))==3,"Science keeps a real log for repair");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="science_tool_buffer",timeoutTicks=200)
 public static void scienceKeepsTheOreCapablePickWhenTheMinerHoldsWood(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());
   var work=MineWork.read(t.l,t.shop);work.put("tool",new ItemStack(Items.WOODEN_PICKAXE).save(new CompoundTag()));work.put("requiredToolState",NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState()));MineWork.write(t.l,t.shop,work);
   var own=LogisticsRoutes.chest(t.l,t.e,t.shop);if(own!=null){own.clearContent();own.setItem(0,new ItemStack(Items.WOODEN_PICKAXE));}
   h.assertTrue(ToolSupplyReserve.researchQuantity(t.l,t.e,Items.STONE_PICKAXE)==1,"Wood in hand or own chest cannot replace the sole pick that can harvest iron");
   h.assertTrue(ToolSupplyReserve.researchQuantity(t.l,t.e,Items.WOODEN_PICKAXE)==0,"An inadequate wooden spare is not protected for iron");
   var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.STONE_PICKAXE));h.assertTrue(BookResearch.have(t.l,t.e,new ResearchCatalog.Cost("minecraft:stone_pickaxe",null,1))==0,"Actual research availability excludes the required stone pick");
   work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.shop,work);h.assertTrue(ToolSupplyReserve.researchQuantity(t.l,t.e,Items.STONE_PICKAXE)==0,"A usable held stone pick releases a spare for science");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="tool_reserve",timeoutTicks=200)
 public static void brokenPickIsPaidBeforeHouseTakesItsOnlyPlanks(GameTestHelper h){check(h,false,false);}
 @GameTest(template="empty",batch="tool_reserve",timeoutTicks=200)
 public static void repairMakesPlanksFromReservedLogWithoutInventingThem(GameTestHelper h){check(h,true,false);}
 @GameTest(template="empty",batch="tool_reserve",timeoutTicks=200)
 public static void oreUpgradeUsesThreeReservedStonesInsteadOfAnotherWoodenPick(GameTestHelper h){check(h,false,true);}
 private static void check(GameTestHelper h,boolean fromLog,boolean upgrade){
  var t=ResearchV2Town.town(h,"mine");
  try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());
   var work=MineWork.read(t.l,t.shop);work.putString("stage","tool");work.putString("status","missing_tool");work.put("tool",ItemStack.EMPTY.save(new CompoundTag()));if(upgrade){work.put("tool",new ItemStack(Items.WOODEN_PICKAXE).save(new CompoundTag()));work.put("requiredToolState",net.minecraft.nbt.NbtUtils.writeBlockState(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState()));}MineWork.write(t.l,t.shop,work);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());var own=LogisticsRoutes.chest(t.l,t.e,t.shop);if(own!=null)own.clearContent();chest.clearContent();
   chest.setItem(0,new ItemStack(upgrade?Items.COBBLESTONE:fromLog?Items.OAK_LOG:Items.OAK_PLANKS,fromLog?1:3));chest.setItem(1,new ItemStack(Items.STICK,2));
   var project=new CompoundTag();project.putInt("schema",1);project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));project.put("ops",new ListTag());project.put("cargo",new ListTag());
   var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",95);cost.putInt("minecraft:oak_log",54);cost.putInt("minecraft:oak_planks",28);cost.putInt("minecraft:stick",42);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);
   h.assertTrue(ToolSupplyReserve.needed(t.l,t.e),"A real assigned miner has no usable pick");
   h.assertTrue(ConstructionFunding.slot(HallReserve.buildView(t.l,t.e,chest),ConstructionFunding.missing(project))<0,"Readiness does not spin on maintenance-only materials");
   var before=chest.getItem(0).copy();var pos=LogisticsRoutes.position(t.e,t.hall());
   h.assertTrue(WorldJournal.takeAmount(t.l,Settlement.childId(project.getUUID("id"),"fund/0"),pos,0,before,before.getCount()).isEmpty(),"Builder cannot take the last repair inputs");
   h.assertTrue(WorldJournal.takeAmount(t.l,UUID.randomUUID(),pos,0,before,before.getCount()).isEmpty(),"An unrelated consumer cannot bypass construction protection");
   var output=upgrade?Items.STONE_PICKAXE:Items.WOODEN_PICKAXE;var wants=upgrade?WorkerSupplies.wants(t.l,t.e):List.of(new Workshops.Want(Ingredient.of(output),1,t.shop.id()));
   long now=t.l.getGameTime();boolean reloadSeen=false;
   for(int i=0;i<900&&chest.countItem(output)==0;i++){
    Workshops.advance(t.l,t.e,t.hall(),now+20L*i,wants);
    var job=Workshops.inspect(t.l,t.hall().id());if(job.getString("stage").equals("work")){
     h.assertTrue(job.getBoolean("toolRepair"),"Paid maintenance survives re-reading its durable job");reloadSeen=true;
    }
   }
   h.assertTrue(reloadSeen&&chest.countItem(output)==1,"A pick is produced through the real paid recipe");
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==0&&chest.countItem(Items.STICK)==0&&chest.countItem(Items.OAK_LOG)==0&&chest.countItem(Items.OAK_PLANKS)==(fromLog?1:0),"Exact conservation: three planks and two sticks spent; one log made four planks");
   h.assertTrue(!ToolSupplyReserve.needed(t.l,t.e),"Usable stock pick releases the buffer");
   h.assertTrue(!HallUpgradeGoal.inspect(t.l,t.s.id()).getBoolean("funded"),"Repair never silently completes or forgives the house bill");
  }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
}
