package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResourceStaffingGameTests {
 @GameTest(template="empty",batch="resource_staffing",timeoutTicks=200)
 public static void twoFarmersLeaveOneRawWorkerWhoReturnsPaidTimberBeforeMining(GameTestHelper h){scene(h,false,false,false,false);}
 @GameTest(template="empty",batch="resource_staffing",timeoutTicks=200)
 public static void aRealTimberShortageKeepsTheOnlyForester(GameTestHelper h){scene(h,true,false,false,false);}
 @GameTest(template="empty",batch="resource_staffing",timeoutTicks=200)
 public static void theOnlyMinerCanCoverAnActualTimberShortage(GameTestHelper h){scene(h,true,true,false,false);}
 @GameTest(template="empty",batch="resource_staffing",timeoutTicks=200)
 public static void aPaidSaplingIsReallyPlantedBeforeTheForesterChangesJobs(GameTestHelper h){scene(h,false,false,true,false);}
 @GameTest(template="empty",batch="resource_staffing",timeoutTicks=200)
 public static void anyPlanksOrderUsesExistingBirchInsteadOfDemandingOakAndSwitchingTheMiner(GameTestHelper h){scene(h,false,false,false,true);}
 private static void scene(GameTestHelper h,boolean timberShortage,boolean minerFirst,boolean renewal,boolean alternative){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   var donor=s.residents().stream().filter(r->r.profession()==Profession.FORESTER).findFirst().orElseThrow();var oldMiner=s.residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();
   var mine=s.workplace(oldMiner.id());var forest=s.workplace(donor.id());var hall=Workshops.hall(e);var farm=new Settlement.Building(UUID.randomUUID(),"farm",-48,0,-48);s.addBuilding(farm);s.unassign(oldMiner.id());s.assign(oldMiner.id(),Profession.FARMER,farm.id());
   if(minerFirst){s.unassign(donor.id());s.assign(donor.id(),Profession.MINER,mine.id());}
   for(var r:s.residents()){var n=VillageAstra.RESIDENT.get().create(l);n.bind(s.id(),r);n.setNoAi(true);n.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(n),"Real staffing body registered");}
   for(var b:List.of(hall,forest,mine)){l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);LogisticsRoutes.chest(l,e,b).clearContent();}
   var log=alternative?Items.BIRCH_LOG:Items.OAK_LOG;
   var stock=LogisticsRoutes.chest(l,e,hall);var source=LogisticsRoutes.chest(l,e,forest);if(!timberShortage){stock.setItem(0,new ItemStack(log,64));source.setItem(0,new ItemStack(log,64));}
   stock.setItem(1,new ItemStack(Items.STONE_PICKAXE));stock.setItem(2,new ItemStack(Items.BREAD,32));
   var cost=new CompoundTag();cost.putInt("minecraft:polished_andesite",3);if(timberShortage)cost.putInt("minecraft:oak_planks",64);var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);
   h.assertTrue(MineProspecting.needed(l,e,mine).contains(Items.ANDESITE),"Actual approved project lacks mined andesite");
   var body=(ResidentEntity)l.getEntity(donor.id());var paid=ItemStack.EMPTY;var axe=ItemStack.EMPTY;
   if(!timberShortage){var operation=UUID.randomUUID();paid=WorldJournal.takeAmount(l,operation,LogisticsRoutes.position(e,forest),0,source.getItem(0).copy(),2);source.setItem(3,new ItemStack(Items.STONE_AXE));axe=WorldJournal.takeAmount(l,Settlement.childId(operation,"tool"),LogisticsRoutes.position(e,forest),3,source.getItem(3).copy(),1);axe.setDamageValue(7);
    var work=new CompoundTag();work.putUUID("operation",operation);work.putUUID("worker",donor.id());work.putString("stage","deliver");work.put("tool",axe.save(new CompoundTag()));var cargo=new ListTag();cargo.add(paid.save(new CompoundTag()));work.put("cargo",cargo);NbtRecord.write(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+forest.id()+".bin"),work);
   }
   if(renewal){
    var path=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+forest.id()+".bin");var work=NbtRecord.read(path);var root=e.center().offset(2,1,0);l.setBlock(root.below(),net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),2);l.setBlock(root,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
    source.setItem(4,new ItemStack(Items.OAK_SAPLING));var seed=WorldJournal.takeAmount(l,UUID.randomUUID(),LogisticsRoutes.position(e,forest),4,source.getItem(4).copy(),1);var cargo=work.getList("cargo",Tag.TAG_COMPOUND);cargo.add(seed.save(new CompoundTag()));work.put("cargo",cargo);work.putString("stage","replant");work.putString("species","minecraft:oak_sapling");work.putLongArray("plantCells",new long[]{root.asLong()});work.putBoolean("fromBare",true);work.putInt("schema",2);NbtRecord.write(path,work);
    Population.assign(l,e,1);h.assertTrue(donor.profession()==Profession.FORESTER&&!CargoCustody.pending(l.getServer(),donor.id()),"The actual paid renewal job finishes before reassignment");
    body.moveTo(root.getX()+1.5,root.getY(),root.getZ()+.5);h.assertTrue(l.getEntity(donor.id())==body,"Prepared renewal body stays registered in the accessible fixture chunk");var goal=new ResourceWorkGoal(body,true,()->0L);h.assertTrue(goal.canUse(),"Ordinary forester goal resumes its own paid planting");goal.start();goal.tick();goal.stop();h.assertTrue(l.getBlockState(root).is(net.minecraft.world.level.block.Blocks.OAK_SAPLING),"A real sapling was placed by the normal work goal");h.assertTrue(source.countItem(Items.OAK_SAPLING)==0,"The planted sapling was actually withdrawn from its chest");
   }
   if(alternative){
    var request=new CompoundTag();request.putString("stage","idle");var needs=new ListTag();var raw=new CompoundTag();raw.putString("ingredient","{\"tag\":\"minecraft:planks\"}");raw.putInt("count",1);needs.add(raw);request.put("needs",needs);NbtRecord.write(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-workshop/"+hall.id()+".bin"),request);
    h.assertTrue(Workshops.wants(l,e).stream().anyMatch(w->w.matches(new ItemStack(Items.BIRCH_PLANKS))&&w.matches(new ItemStack(Items.OAK_PLANKS))),"The actual published order accepts either birch or oak planks");h.assertTrue(stock.countItem(Items.OAK_LOG)==0&&source.countItem(Items.OAK_LOG)==0,"There is deliberately no oak; genuine birch is the alternative raw source");
   }
   Population.assign(l,e,1);
   if(timberShortage){h.assertTrue(donor.profession()==Profession.FORESTER&&forest.equals(s.workplace(donor.id())),"A raw worker covers the actual timber shortage before ore orders");}
   else{
    h.assertTrue(donor.profession()==Profession.MINER&&mine.equals(s.workplace(donor.id())),"Stocked timber must not strand the only raw worker in forestry while the approved house needs andesite; role="+donor.profession()+" visible="+(l.getEntity(donor.id())==body));
    h.assertTrue(CargoCustody.pending(l.getServer(),donor.id()),"Paid timber and worn axe enter custody before a different resource job");var held=CargoCustody.inspect(l.getServer(),donor.id()).getList("items",Tag.TAG_COMPOUND);h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(log)).mapToInt(ItemStack::getCount).sum()==2,"Both paid logs survive the reassignment");h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).anyMatch(i->i.is(Items.STONE_AXE)&&i.getDamageValue()==7),"Actual tool wear survives reassignment");
    var at=LogisticsRoutes.position(e,hall);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);for(int i=0;i<8;i++)CargoCustody.returnStep(body,true);h.assertTrue(!CargoCustody.pending(l.getServer(),donor.id())&&stock.countItem(log)==66,"Paid logs returned exactly once before mining");if(renewal)h.assertTrue(stock.countItem(Items.OAK_SAPLING)==0,"The planted sapling is not returned again as cargo");h.assertTrue(source.countItem(log)==62,"Withdrawal was not undone or duplicated");Population.assign(l,e,1);h.assertTrue(donor.profession()==Profession.MINER,"Stable unmet ore order does not switch back to forestry");
   }
   h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.FARMER).count()==2&&oldMiner.profession()==Profession.FARMER,"Both food workers retain their jobs");h.assertTrue(s.residents().stream().allMatch(Resident::alive)&&s.residents().size()==6,"No invented workers or deaths");
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
