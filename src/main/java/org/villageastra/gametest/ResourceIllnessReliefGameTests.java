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
public final class ResourceIllnessReliefGameTests {
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void spareBuilderCoversSickMinerAfterActualCargoReturn(GameTestHelper h){check(h,true,false,true,false,false);}
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void healthyMinerKeepsPost(GameTestHelper h){check(h,false,false,true,false,false);}
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void playerMayorKeepsChosenStaff(GameTestHelper h){check(h,true,true,true,false,false);}
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void soleBuilderAndFoodWorkersStayAtWork(GameTestHelper h){check(h,true,false,false,false,false);}
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void spareBuilderFinishesActualSupplyTripBeforeChangingJobs(GameTestHelper h){check(h,true,false,true,true,false);}
 @GameTest(template="empty",batch="resource_illness_relief",timeoutTicks=200)
 public static void noOreDemandMeansNoReplacement(GameTestHelper h){check(h,true,false,true,false,true);}
 private static void check(GameTestHelper h,boolean sick,boolean player,boolean extra,boolean parcel,boolean noDemand){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   var hall=Workshops.hall(e);var lead=s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();
   var miner=s.residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();var mine=s.workplace(miner.id());if(sick)miner.fallIll();
   Resident helper=null;if(extra){var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));helper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(helper,home);s.assign(helper.id(),Profession.BUILDER,hall.id());}
   for(var r:s.residents()){var n=VillageAstra.RESIDENT.get().create(l);n.bind(s.id(),r);n.setNoAi(true);n.moveTo(e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+4.5);h.assertTrue(l.addFreshEntity(n),"Actual staffing body exists");}
   for(var b:List.of(hall,mine)){l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);LogisticsRoutes.chest(l,e,b).clearContent();}
   var cost=new CompoundTag();cost.putInt(noDemand?"minecraft:birch_planks":"minecraft:polished_andesite",3);var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));project.putUUID("worker",lead.id());project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);
   if(player)s.governance().appointPlayer(UUID.randomUUID());
   var source=LogisticsRoutes.chest(l,e,mine);source.setItem(0,new ItemStack(Items.COBBLESTONE,2));source.setItem(1,new ItemStack(Items.STONE_PICKAXE));
   var op=UUID.randomUUID();var oreCargo=WorldJournal.takeAmount(l,op,LogisticsRoutes.position(e,mine),0,source.getItem(0).copy(),2);var tool=WorldJournal.takeAmount(l,Settlement.childId(op,"tool"),LogisticsRoutes.position(e,mine),1,source.getItem(1).copy(),1);tool.setDamageValue(74);
   var work=new CompoundTag();work.putInt("schema",1);work.putUUID("operation",op);work.putUUID("worker",miner.id());work.putString("stage","choose");work.putInt("width",3);work.putInt("height",5);work.put("tool",tool.save(new CompoundTag()));var cargo=new ListTag();cargo.add(oreCargo.save(new CompoundTag()));work.put("cargo",cargo);
   var path=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+mine.id()+".bin");NbtRecord.write(path,work);
   if(noDemand){var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.COBBLESTONE,64));stock.setItem(1,new ItemStack(Items.STONE_PICKAXE));stock.setItem(2,new ItemStack(Items.STONE_AXE));stock.setItem(3,new ItemStack(Items.COAL,64));stock.setItem(4,new ItemStack(Items.BIRCH_LOG,64));h.assertTrue(MineProspecting.needed(l,e,mine).isEmpty(),"No construction or implicit replacement-tool order needs mined resources");}
   if(parcel){var trip=new CompoundTag();trip.putInt("schema",1);trip.putString("stage","deliver");trip.putUUID("id",UUID.randomUUID());trip.putUUID("worker",helper.id());trip.putUUID("settlement",s.id());trip.putUUID("assignment",hall.id());trip.putUUID("source",mine.id());trip.putUUID("destination",hall.id());trip.putBoolean("selfSupply",true);trip.put("item",new ItemStack(Items.SAND,2).save(new CompoundTag()));source.setItem(2,new ItemStack(Items.SAND,2));WorldJournal.takeAmount(l,Settlement.childId(trip.getUUID("id"),"take"),LogisticsRoutes.position(e,mine),2,source.getItem(2).copy(),2);NbtRecord.write(PorterWork.path(l,helper.id()),trip);}
   var before=new HashMap<UUID,Profession>();for(var r:s.residents())before.put(r.id(),r.profession());
   Population.assign(l,e,2);boolean replace=sick&&!player&&extra&&!parcel&&!noDemand;
   h.assertTrue(s.residents().stream().anyMatch(r->r.profession()==Profession.MINER&&!r.sick()&&mine.equals(s.workplace(r.id())))==(!sick||replace),"Actual unmet ore orders must have a healthy replacement when a spare builder exists");
   for(var r:s.residents())if(r!=miner&&r!=helper)h.assertTrue(r.profession()==before.get(r.id()),"Primary builder, forestry, farms, mayor and porter retain their posts");
   h.assertTrue(HallUpgradeGoal.inspect(l,s.id()).equals(project),"The exact construction invoice and lead remain intact");
   if(replace){
    h.assertTrue(miner.sick()&&miner.profession()==null&&helper.profession()==Profession.MINER,"Reassignment does not cure or invent residents");
    var held=CargoCustody.inspect(l.getServer(),miner.id()).getList("items",Tag.TAG_COMPOUND);
    h.assertTrue(held.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum()==2&&held.stream().map(t->ItemStack.of((CompoundTag)t)).anyMatch(i->i.is(Items.STONE_PICKAXE)&&i.getDamageValue()==74),"Actual withdrawn cargo and tool wear remain with the patient");
    var replacement=(ResidentEntity)l.getEntity(helper.id());h.assertTrue(!new ResourceWorkGoal(replacement,true,()->0L).canUse(),"New worker cannot take the old record before physical return");
    var patient=(ResidentEntity)l.getEntity(miner.id());var at=LogisticsRoutes.position(e,hall);patient.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);for(int i=0;i<6;i++)CargoCustody.returnStep(patient,true);
    var stock=LogisticsRoutes.chest(l,e,hall);h.assertTrue(!CargoCustody.pending(l.getServer(),miner.id())&&stock.countItem(Items.COBBLESTONE)==2&&stock.countItem(Items.STONE_PICKAXE)==1,"Paid goods physically return exactly once");
    h.assertTrue(!NbtRecord.read(path).hasUUID("worker"),"Old ownership is released only after return");for(int i=0;i<3;i++)CargoCustody.returnStep(patient,true);h.assertTrue(stock.countItem(Items.COBBLESTONE)==2,"Repeated return does not duplicate goods");
    Population.assign(l,e,2);h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.MINER).count()==1&&helper.profession()==Profession.MINER,"Repeated staffing is stable while the patient rests");
   }else h.assertTrue(miner.profession()==Profession.MINER&&!CargoCustody.pending(l.getServer(),miner.id())&&NbtRecord.read(path).equals(work),"Ineligible relief preserves actual mine ownership and cargo");
   if(parcel)h.assertTrue(helper.profession()==Profession.BUILDER&&PorterWork.active(PorterWork.inspect(l,helper.id()))&&PorterWork.cargo(l,PorterWork.inspect(l,helper.id())).getCount()==2,"Real withdrawn supply parcel stays with its builder until delivery");
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
