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
import org.villageastra.world.*;
import org.villageastra.persistence.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmingReliefGameTests {
 @GameTest(template="empty",batch="farming_relief_emergency",timeoutTicks=200)
 public static void porterWithPaidParcelReplacesFarmerWhenAllRawWorkersAreIll(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   for(var r:s.residents()){var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(body),"Relief body registered");}
   var farmer=s.residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow();var donor=s.residents().stream().filter(r->r.profession()==Profession.PORTER).findFirst().orElseThrow();var builder=s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();var farm=s.workplace(farmer.id());var mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();var hall=Workshops.hall(e);
   for(var b:List.of(hall,mine))l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var source=LogisticsRoutes.chest(l,e,mine);source.setItem(0,new ItemStack(Items.COBBLESTONE,2));var stock=LogisticsRoutes.chest(l,e,hall);stock.clearContent();
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",2);project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);var route=LogisticsRoutes.workerRoute(l,e,mine);h.assertTrue(route!=null,"A real parcel is requested");var body=(ResidentEntity)l.getEntity(donor.id());var at=LogisticsRoutes.position(e,mine);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(body,route,false);PorterWork.step(body,route,false);h.assertTrue(source.isEmpty()&&PorterWork.cargo(l,PorterWork.inspect(l,donor.id())).getCount()==2,"Actual two-block parcel paid before reassignment");
   for(var r:s.residents())if(Set.of(Profession.FARMER,Profession.FORESTER,Profession.MINER).contains(r.profession()))r.fallIll();
   h.assertTrue(FarmingRelief.tick(l,e),"Healthy porter takes food work while all three raw workers are ill");h.assertTrue(donor.profession()==Profession.FARMER&&farm.equals(s.workplace(donor.id()))&&builder.profession()==Profession.BUILDER,"Construction keeps its sole worker when the porter can help");h.assertTrue(farmer.sick()&&farmer.profession()==null,"Illness was not cured by reassignment");var held=CargoCustody.inspect(l.getServer(),donor.id()).getList("items",Tag.TAG_COMPOUND);h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum()==2,"Both already paid blocks remain in custody");
   at=LogisticsRoutes.position(e,hall);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);for(int i=0;i<5;i++)CargoCustody.returnStep(body,true);h.assertTrue(!CargoCustody.pending(l.getServer(),donor.id())&&stock.countItem(Items.COBBLESTONE)==2,"Parcel returns exactly once before farming");var goal=new ResourceWorkGoal(body,true);h.assertTrue(goal.canUse(),"Former porter can begin ordinary farming");goal.stop();
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="farming_relief_emergency",timeoutTicks=200)
 public static void soleBuilderHelpsOnlyInFoodEmergencyAndReturnsPaidProjectCargo(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   for(var r:s.residents()){var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(body),"Emergency body registered");}
   var builder=s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();var body=(ResidentEntity)l.getEntity(builder.id());var hall=Workshops.hall(e);var at=LogisticsRoutes.position(e,hall);l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.COBBLESTONE,2));
   var id=UUID.randomUUID();var paid=WorldJournal.takeAmount(l,id,at,0,stock.getItem(0).copy(),2);h.assertTrue(paid.getCount()==2&&stock.isEmpty(),"Construction cargo withdrawn from the real chest");
   var ops=new ListTag();var done=new CompoundTag();done.putString("item","minecraft:oak_planks");done.putBoolean("done",true);ops.add(done);for(int i=0;i<2;i++){var op=new CompoundTag();op.putString("item","minecraft:cobblestone");ops.add(op);}var project=new CompoundTag();project.putUUID("id",id);project.putUUID("project",id);project.putUUID("worker",builder.id());project.putBoolean("funded",true);project.putInt("index",1);project.put("ops",ops);project.put("cost",new CompoundTag());var cargo=new ListTag();cargo.add(paid.save(new CompoundTag()));project.put("cargo",cargo);HallUpgradeGoal.enqueue(l,e,project);
   for(var r:s.residents())if(Set.of(Profession.FARMER,Profession.FORESTER,Profession.MINER,Profession.PORTER).contains(r.profession()))r.fallIll();
   stock.setItem(1,new ItemStack(Items.BREAD,64));h.assertTrue(!FarmingRelief.tick(l,e)&&builder.profession()==Profession.BUILDER,"A stocked village retains its sole busy builder");stock.setItem(1,ItemStack.EMPTY);
   h.assertTrue(FarmingRelief.tick(l,e)&&builder.profession()==Profession.FARMER,"Only remaining healthy eligible worker covers the empty food supply");var held=CargoCustody.inspect(l.getServer(),builder.id()).getList("items",Tag.TAG_COMPOUND);h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum()==2,"Paid construction blocks survive the emergency handover");
   body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);for(int i=0;i<5;i++)CargoCustody.returnStep(body,true);h.assertTrue(!CargoCustody.pending(l.getServer(),builder.id())&&stock.countItem(Items.COBBLESTONE)==2,"Both paid blocks return exactly once before farming");
   var reset=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(HallConstructionPlan.projectId(reset).equals(id)&&!reset.getBoolean("complete")&&!reset.getBoolean("funded")&&!reset.hasUUID("worker")&&reset.getInt("index")==1&&reset.getList("ops",Tag.TAG_COMPOUND).equals(ops)&&reset.getCompound("cost").getInt("minecraft:cobblestone")==2,"Same unfinished project retains completed operations and its outstanding two-block bill");
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="farm_expansion_staff",timeoutTicks=100)
 public static void newlyCompletedFarmUsesSpareBuilderAndKeepsTimberAndOreWorkers(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var extra=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(extra,home);s.assign(extra.id(),Profession.BUILDER,Workshops.hall(e).id());
  var farm=new Settlement.Building(UUID.randomUUID(),"farm",30,0,0);s.addBuilding(farm);
  try{
   for(var r:s.residents()){var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(body),"Staffing fixture body registered");}
   h.assertTrue(FarmingRelief.tick(l,e),"The completed extra farm receives a real spare worker");
   h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.BUILDER).count()==1,"One construction worker remains");
   h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.FORESTER).count()==1&&s.residents().stream().filter(r->r.profession()==Profession.MINER).count()==1,"Timber and ore supply continue");
   h.assertTrue(s.residents().stream().anyMatch(r->r.profession()==Profession.FARMER&&farm.equals(s.workplace(r.id()))),"The new farm is staffed rather than only increasing a forecast");
  }finally{for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="farming_relief",timeoutTicks=100)
 public static void healthyWorkerReplacesSickFarmerWithoutCuring(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));
  SettlementData.get(l.getServer()).add(e);
  l.setBlock(HallSite.stock(e),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  try{
   for(var r:s.residents()){var n=l.getEntity(r.id());if(n==null){var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);l.addFreshEntity(body);}var body=(ResidentEntity)l.getEntity(r.id());body.setNoAi(true);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);}
   var farmer=s.residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow();
   var donor=s.residents().stream().filter(r->r.profession()==Profession.FORESTER).findFirst().orElseThrow();
   var farm=s.workplace(farmer.id());
   var donorJob=s.workplace(donor.id());var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+donorJob.id()+".bin");
   var state=new CompoundTag();state.putInt("schema",2);state.putUUID("worker",donor.id());state.putUUID("operation",UUID.randomUUID());state.putString("stage","deliver");
   state.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));var cargo=new ListTag();cargo.add(new ItemStack(Items.BIRCH_LOG,3).save(new CompoundTag()));state.put("cargo",cargo);NbtRecord.write(file,state);
   h.assertTrue(!FarmingRelief.tick(l,e),"Healthy farm keeps its crew");
   farmer.fallIll();s.governance().appointPlayer(UUID.randomUUID());
   h.assertTrue(!FarmingRelief.tick(l,e),"Player keeps control of staffing");s.governance().appointNpc();
   h.assertTrue(FarmingRelief.tick(l,e),"NPC mayor appoints a healthy replacement");
   h.assertTrue(farmer.sick()&&farmer.profession()==null&&!Population.mayWork(farmer),"Ill farmer remains ill and rests");
   h.assertTrue(donor.profession()==Profession.FARMER&&farm.equals(s.workplace(donor.id()))&&Population.mayWork(donor),"Forester takes over food production");
   h.assertTrue(CargoCustody.pending(l.getServer(),donor.id()),"Old forestry cargo remains in custody before farming");
   var held=CargoCustody.inspect(l.getServer(),donor.id()).getList("items",Tag.TAG_COMPOUND);
   h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(Items.BIRCH_LOG)).mapToInt(ItemStack::getCount).sum()==3,"Three carried logs survive reassignment");
   Population.assign(e);h.assertTrue(farmer.profession()==null,"Labor office does not send the patient to another job");
   h.assertTrue(!FarmingRelief.tick(l,e),"Replacement remains assigned without repeated handover");
   var body=(ResidentEntity)l.getEntity(donor.id());var stock=LogisticsRoutes.chest(l,e,Workshops.hall(e));int logs=stock.countItem(Items.BIRCH_LOG);
   for(int i=0;i<5;i++)CargoCustody.returnStep(body,true);
   h.assertTrue(!CargoCustody.pending(l.getServer(),donor.id())&&stock.countItem(Items.BIRCH_LOG)==logs+3,"Former forestry cargo is deposited exactly once");
   var goal=new ResourceWorkGoal(body,true);
   h.assertTrue(goal.canUse(),"Replacement can start ordinary farming after returning its old cargo");goal.stop();
  }finally{for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 @GameTest(template="empty",batch="bulk_food_recovery",timeoutTicks=200)
 public static void hungryCourierClearsPaidBreadInsteadOfBeingReassignedToAnotherHungryFarm(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   for(var r:s.residents()){var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(e.center().getX()+2.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(body),"Body registered");}
   var hall=Workshops.hall(e);var mine=s.buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();for(var b:List.of(hall,mine))l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   var stock=LogisticsRoutes.chest(l,e,hall);stock.expandHall();stock.setItem(0,new ItemStack(Items.WHEAT,HandBread.WHEAT_PER_UNIT));
   var courier=s.residents().stream().filter(r->r.profession()==Profession.PORTER).findFirst().orElseThrow();var body=(ResidentEntity)l.getEntity(courier.id());
   for(int tick=0;tick<=HandBread.BREAD_PER_UNIT*HandBread.LABOR_PER_BREAD+100;tick+=20)HandBread.advance(l,e,courier.id(),tick);
   h.assertTrue(stock.countItem(Items.BREAD)>0,"Real grain became real bread");
   // A second paid unit waits behind the full stock.
   stock.clearContent();stock.setItem(0,new ItemStack(Items.WHEAT,HandBread.WHEAT_PER_UNIT));
   long now=10000;HandBread.advance(l,e,courier.id(),now);for(int turn=1;turn<=3;turn++)HandBread.advance(l,e,courier.id(),now+turn*20);
   for(int slot=0;slot<108;slot++)stock.setItem(slot,new ItemStack(Items.COBBLESTONE,64));stock.setItem(107,new ItemStack(Items.COBBLESTONE,8));
   for(int tick=80;tick<=HandBread.BREAD_PER_UNIT*HandBread.LABOR_PER_BREAD+200;tick+=20)HandBread.advance(l,e,courier.id(),now+tick);
   h.assertTrue(HandBread.inspect(l,s.id()).getString("stage").equals("output")&&stock.countItem(Items.BREAD)==0,"Actually paid and baked bread is blocked by full stock");
   for(var r:s.residents())if(!r.id().equals(courier.id()))r.fallIll();courier.missedMeal(1);courier.missedMeal(2);
   h.assertTrue(CargoCustody.mayStartFoodTransport(body),"Hungry courier can clear non-food bulk for paid emergency bread");
   FarmingRelief.tick(l,e);h.assertTrue(courier.profession()==Profession.PORTER,"Emergency food courier is kept instead of becoming another stopped farmer");
   var at=LogisticsRoutes.position(e,hall);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(body);PorterWork.step(body);
   h.assertTrue(stock.getItem(107).isEmpty()&&PorterWork.cargo(l,PorterWork.inspect(l,courier.id())).getCount()==8,"Courier really withdrew eight surplus blocks");
   h.assertTrue(CargoCustody.mayStartFoodTransport(body),"Hunger does not abandon the paid non-food recovery parcel");at=LogisticsRoutes.position(e,mine);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);PorterWork.step(body);
   h.assertTrue(LogisticsRoutes.chest(l,e,mine).countItem(Items.COBBLESTONE)==8,"Actual parcel reaches the mine exactly once");HandBread.advance(l,e,courier.id(),now+5000);h.assertTrue(stock.countItem(Items.BREAD)==HandBread.BREAD_PER_UNIT,"Original paid bread finally fits");
  }finally{for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }

}
