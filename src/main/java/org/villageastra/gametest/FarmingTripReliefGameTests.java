package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmingTripReliefGameTests {
 @GameTest(template="empty",batch="farming_trip_relief",timeoutTicks=100)
 public static void fedVillageKeepsActiveTripWhileAnotherFarmWorks(GameTestHelper h){check(h,true,true);}
 @GameTest(template="empty",batch="farming_trip_relief",timeoutTicks=100)
 public static void foodShortageStillInterruptsTripForFarming(GameTestHelper h){check(h,false,true);}
 @GameTest(template="empty",batch="farming_trip_relief",timeoutTicks=100)
 public static void lastStoppedFarmStillReceivesImmediateRelief(GameTestHelper h){check(h,true,false);}
 private static void check(GameTestHelper h,boolean fed,boolean otherFarm){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   var donor=s.residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();
   var originalFarmer=s.residents().stream().filter(r->r.profession()==Profession.FARMER).findFirst().orElseThrow();
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var patient=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(patient,home);
   var farm=new Settlement.Building(UUID.randomUUID(),"farm",32,0,0);s.addBuilding(farm);s.assign(patient.id(),Profession.FARMER,farm.id());patient.fallIll();
   for(var r:s.residents()){
    if(!r.id().equals(donor.id())&&r.profession()!=Profession.PORTER&&(!otherFarm||!r.id().equals(originalFarmer.id())))r.fallIll();
    var body=VillageAstra.RESIDENT.get().create(l);body.bind(s.id(),r);body.setNoAi(true);body.moveTo(e.center().getX()+.5,e.center().getY()+1,e.center().getZ()+.5);h.assertTrue(l.addFreshEntity(body),"Real staffing body registered");
   }
   var hall=Workshops.hall(e);l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.clearContent();if(fed)stock.setItem(0,new ItemStack(Items.BREAD,64));
   h.assertTrue((Population.storedNutrition(l,e)>=HandBread.reserveRations(e))==fed,"Fixture has the specified actual meal reserve");
   var trip=new CompoundTag();trip.putUUID("id",UUID.randomUUID());trip.putString("stage","dig");trip.putLong("target",e.center().east(40).asLong());NbtRecord.write(NaturalSupplyGoal.path(l,donor.id()),trip);
   boolean changed=FarmingRelief.tick(l,e),preserve=fed&&otherFarm;
   h.assertTrue(preserve?!changed&&donor.profession()==Profession.MINER:changed&&donor.profession()==Profession.FARMER,"Only a stocked village with another healthy farm may finish the current trip first: fed="+fed+" other="+otherFarm+" changed="+changed+" role="+donor.profession()+" nutrition="+Population.storedNutrition(l,e)+" reserve="+HandBread.reserveRations(e));
   if(!preserve){CargoCustody.beginReturn((ResidentEntity)l.getEntity(donor.id()));CargoCustody.returnStep((ResidentEntity)l.getEntity(donor.id()),true);}
   h.assertTrue(preserve?NaturalSupplyGoal.inspect(l,donor.id()).equals(trip):!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,donor.id())),"Nonurgent relief preserves the exact trip; emergency uses ordinary custody handover");
  }finally{for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
