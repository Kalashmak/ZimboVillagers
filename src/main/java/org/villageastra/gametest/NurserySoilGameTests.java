package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NurserySoilGameTests {
 @GameTest(template="empty",batch="nursery_soil") public static void desertPlantingRequiresActualSoilAndPreservesDisplacedSand(GameTestHelper h){paid(h,false);}
 @GameTest(template="empty",batch="nursery_soil") public static void preparedSoilRecoversAfterCrashWithoutDuplicateCargo(GameTestHelper h){paid(h,true);}
 private static void paid(GameTestHelper h,boolean crash){var f=ForestFixture.create(h,1,192);
  try{
   BlockPos foot=null;for(int x=-4;x<=20&&foot==null;x++)for(int z=3;z<=10&&foot==null;z++){var p=f.wood(x,z);if(ForestRenewal.safe(f.l,p))foot=p;}
   h.assertTrue(foot!=null,"Unprotected planting site");f.l.setBlock(foot.below(),Blocks.SAND.defaultBlockState(),2);
   h.assertTrue(!ForestRenewal.safe(f.l,foot)&&ForestRenewal.sandy(f.l,foot),"Sand needs paid soil before planting");
   var hall=(net.minecraft.world.Container)f.l.getBlockEntity(f.hall());hall.clearContent();
   var t=new CompoundTag();t.putUUID("operation",UUID.randomUUID());t.putUUID("worker",f.forester.getUUID());t.putString("stage","nursery_soil");t.putLong("target",foot.asLong());t.put("soilBefore",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));t.put("cargo",new ListTag());
   h.assertTrue(!NurserySoil.fetch(f.l,f.e,t,f.hall())&&!NurserySoil.prepare(f.l,t),"No free soil or sand");
   hall.setItem(0,new ItemStack(Items.DIRT,2));
   var beforeTake=t.copy();h.assertTrue(NurserySoil.fetch(f.l,f.e,t,f.hall()),"One soil item fetched");
   h.assertTrue(ForestFixture.count(NurserySoil.cargo(f.l,beforeTake),Items.DIRT)==1,"Uncheckpointed take belongs to the resident");
   h.assertTrue(hall.countItem(Items.DIRT)==1,"One dirt stayed in stock");
   if(crash){
    var op=Settlement.childId(t.getUUID("operation"),"nursery/place");h.assertTrue(WorldJournal.place(f.l,op,foot.below(),Blocks.SAND.defaultBlockState(),Blocks.DIRT.defaultBlockState()),"Paid placement committed before worker save");
    h.assertTrue(ForestFixture.count(NurserySoil.cargo(f.l,t),Items.DIRT)==0&&ForestFixture.count(NurserySoil.cargo(f.l,t),Items.SAND)==1,"Custody charges installed dirt and retains removed sand");
    f.write(t);var custody=JobCargo.snapshot(f.forester,true);
    h.assertTrue(ForestFixture.count(custody.items(),Items.DIRT)==0&&ForestFixture.count(custody.items(),Items.SAND)==1,"Actual death custody sees the reconciled soil operation");
   }
   h.assertTrue(NurserySoil.prepare(f.l,t),"Soil prepared or recovered");
   h.assertTrue(ForestRenewal.safe(f.l,foot)&&ForestFixture.count(t.getList("cargo",Tag.TAG_COMPOUND),Items.DIRT)==0&&ForestFixture.count(t.getList("cargo",Tag.TAG_COMPOUND),Items.SAND)==1,"Real replacement, exact payment and returned sand");
   h.assertTrue(!NurserySoil.prepare(f.l,t)&&ForestFixture.count(NurserySoil.cargo(f.l,t),Items.SAND)==1,"No duplicate reconciliation");
   h.assertTrue(f.l.getBlockState(foot).isAir()&&t.getString("stage").equals("sapling"),"Preparation queues a paid sapling, never creates a free tree");
  }finally{f.done();}h.succeed();
 }
 @GameTest(template="empty",batch="nursery_soil",timeoutTicks=300)
 public static void exhaustedSandyForestQueuesSoilDemandAndPlantsPaidSapling(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{
   for(int x=-8;x<=24;x++)for(int z=0;z<=14;z++)f.l.setBlock(f.wood(x,z).below(),Blocks.SAND.defaultBlockState(),2);
   var start=f.wood(-6,0);f.forester.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);f.forester.setOnGround(true);
   var t=new CompoundTag();boolean planned=false;
   for(int i=0;i<32&&!planned;i++)planned=ForestRenewal.plan(f.l,f.e,f.hut(),f.forester,t,Set.of(Items.OAK_SAPLING));
   h.assertTrue(planned&&NurserySoil.active(t),"Exhausted sandy work area plans paid soil after a full fertile-site scan: "+t);
   t.putInt("schema",2);t.putInt("width",1);t.putInt("height",3);t.putInt("descent",0);t.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));f.write(t);
   var hall=(net.minecraft.world.Container)f.l.getBlockEntity(f.hall());hall.clearContent();f.chestBlock().clearContent();f.chestBlock().setItem(0,new ItemStack(Items.OAK_SAPLING));
   h.assertTrue(WorkerSupplies.wants(f.l,f.e).stream().anyMatch(w->w.matches(new ItemStack(Items.DIRT))),"Real soil demand reaches village suppliers");
   hall.setItem(0,new ItemStack(Items.DIRT));var target=BlockPos.of(t.getLong("target"));var goal=new ResourceWorkGoal(f.forester,true,()->6000L);h.assertTrue(goal.canUse(),"Forester resumes soil stage");
   for(int i=0;i<35&&!f.l.getBlockState(target).is(Blocks.OAK_SAPLING);i++){
    var state=f.record();var stage=state.getString("stage");var at=stage.equals("nursery_soil")?(state.getBoolean("soilHeld")?target:f.hall().east()):stage.equals("sapling")?f.chest().east():target.north();
    f.forester.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);f.forester.setOnGround(true);goal.tick();
   }
   h.assertTrue(f.l.getBlockState(target).is(Blocks.OAK_SAPLING)&&f.l.getBlockState(target.below()).is(Blocks.DIRT),"Actual resource goal prepared soil and planted the carried sapling");
   h.assertTrue(hall.countItem(Items.DIRT)==0&&f.chestBlock().countItem(Items.OAK_SAPLING)==0&&ForestFixture.count(f.record().getList("cargo",Tag.TAG_COMPOUND),Items.SAND)==1,"One dirt and one sapling spent, one sand carried");
  }finally{f.done();}h.succeed();
 }
 @GameTest(template="empty",batch="nursery_soil") public static void soilPreparationRejectsProtectedLotsAndOccupiedSites(GameTestHelper h){var f=ForestFixture.create(h,1,192);
  try{var p=f.door();h.assertTrue(!ForestRenewal.sandy(f.l,p),"Building remains protected");var outside=f.wood(-6,3);f.l.setBlock(outside.below(),Blocks.SAND.defaultBlockState(),2);f.l.setBlock(outside,Blocks.OAK_PLANKS.defaultBlockState(),2);h.assertTrue(!ForestRenewal.sandy(f.l,outside),"Existing structure cannot be cleared for soil");}finally{f.done();}h.succeed();
 }
}
