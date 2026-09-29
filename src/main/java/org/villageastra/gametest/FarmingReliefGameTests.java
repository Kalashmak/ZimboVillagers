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
}
