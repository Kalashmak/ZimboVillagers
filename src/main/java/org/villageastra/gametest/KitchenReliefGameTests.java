package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class KitchenReliefGameTests {
 @GameTest(template="empty",batch="kitchen_relief",timeoutTicks=100)
 public static void sickCookGetsReplacementWithoutTakingProjectLeader(GameTestHelper h){check(h,true,false,true);}
 @GameTest(template="empty",batch="kitchen_relief",timeoutTicks=100)
 public static void healthyCookKeepsPost(GameTestHelper h){check(h,false,false,true);}
 @GameTest(template="empty",batch="kitchen_relief",timeoutTicks=100)
 public static void playerMayorKeepsChosenKitchenStaff(GameTestHelper h){check(h,true,true,true);}
 @GameTest(template="empty",batch="kitchen_relief",timeoutTicks=100)
 public static void soleBuilderAndRawWorkersKeepTheirPosts(GameTestHelper h){check(h,true,false,false);}
 @GameTest(template="empty",batch="kitchen_relief_cargo",timeoutTicks=100)
 public static void illCooksWithdrawnFuelReturnsExactlyOnceBeforeJobHandover(GameTestHelper h){check(h,true,false,true,true);}
 private static void check(GameTestHelper h,boolean sick,boolean player,boolean extra){
  check(h,sick,player,extra,false);
 }
 private static void check(GameTestHelper h,boolean sick,boolean player,boolean extra,boolean cargo){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(2,3,2)));SettlementData.get(l.getServer()).add(e);
  try{
   var hall=Workshops.hall(e);var lead=s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();
   var kitchen=new Settlement.Building(UUID.randomUUID(),"restaurant",40,0,40);s.addBuilding(kitchen);
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));
   var cook=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(cook,home);s.assign(cook.id(),Profession.BAKER,kitchen.id());if(sick)cook.fallIll();
   Resident helper=null;if(extra){helper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(helper,home);s.assign(helper.id(),Profession.BUILDER,hall.id());}
   for(var r:s.residents()){var n=VillageAstra.RESIDENT.get().create(l);n.bind(s.id(),r);n.setNoAi(true);n.moveTo(e.center().getX()+.5,e.center().getY()+1,e.center().getZ()+.5);h.assertTrue(l.addFreshEntity(n),"Actual staffing body exists");}
   if(player)s.governance().appointPlayer(UUID.randomUUID());
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());project.putUUID("project",project.getUUID("id"));project.putUUID("worker",lead.id());project.putBoolean("funded",true);project.putInt("index",7);HallUpgradeGoal.enqueue(l,e,project);
   UUID smelt=UUID.randomUUID();
   if(cargo){
    var source=LogisticsRoutes.position(e,kitchen);l.setBlock(source,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,kitchen);chest.setItem(0,new ItemStack(Items.COAL));
    var taken=WorldJournal.takeAmount(l,Settlement.childId(smelt,"smelt/fuel/0"),source,0,chest.getItem(0).copy(),1);h.assertTrue(taken.getCount()==1&&chest.isEmpty(),"Fuel actually left the kitchen chest");
    var furnace=source.east(2);l.setBlock(furnace,Blocks.FURNACE.defaultBlockState(),2);l.getBlockEntity(furnace).getPersistentData().putUUID("AstraSmeltJob",smelt);
    var job=new CompoundTag();job.putInt("schema",1);job.putUUID("id",smelt);job.putUUID("building",kitchen.id());job.putUUID("worker",cook.id());job.putBoolean("physicalSmelt",true);job.putString("stage","smelt_put_fuel");job.putLong("furnace",furnace.asLong());job.put("carried",taken.save(new CompoundTag()));NbtRecord.write(Workshops.path(l,kitchen.id()),job);
    l.setBlock(HallSite.stock(e),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   }
   var before=new HashMap<UUID,Profession>();for(var r:s.residents())before.put(r.id(),r.profession());
   Population.assign(l,e,2);boolean needed=sick&&!player&&extra;
   h.assertTrue(s.residents().stream().anyMatch(r->r.profession()==Profession.BAKER&&!r.sick()&&kitchen.equals(s.workplace(r.id())))==(!sick||needed),"An ill cook must not idle the kitchen while an additional healthy builder is available");
   if(needed){h.assertTrue(helper.profession()==Profession.BAKER&&cook.sick()&&cook.profession()==null,"Replacement takes the kitchen without curing the patient");}
   for(var r:s.residents())if(r!=helper&&r!=cook)h.assertTrue(r.profession()==before.get(r.id()),"Food relief preserves the mayor, project leader, farms, transport and raw supply");
   h.assertTrue(lead.profession()==Profession.BUILDER&&HallUpgradeGoal.inspect(l,s.id()).equals(project),"Exact paid construction state remains unchanged");
   if(cargo){
    var body=(ResidentEntity)l.getEntity(cook.id());var held=CargoCustody.inspect(l.getServer(),cook.id()).getList("items",10);
    h.assertTrue(held.size()==1&&ItemStack.of(held.getCompound(0)).is(Items.COAL)&&ItemStack.of(held.getCompound(0)).getCount()==1,"The sick carrier retains exactly the paid fuel");
    var at=HallSite.stock(e);body.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);for(int i=0;i<4;i++)CargoCustody.returnStep(body,true);
    var reset=Workshops.inspect(l,kitchen.id());h.assertTrue(!CargoCustody.pending(l.getServer(),cook.id())&&LogisticsRoutes.chest(l,e,hall).countItem(Items.COAL)==1,"Fuel physically returns once before the old job is released");
    h.assertTrue(reset.getUUID("id").equals(smelt)&&reset.getString("stage").equals("smelt_fuel")&&!reset.hasUUID("worker")&&!reset.contains("carried")&&reset.getInt("fuels")==1,"Same furnace job resumes with a new withdrawal operation and no duplicated carried fuel");
   }
   Population.assign(l,e,2);h.assertTrue(s.residents().stream().filter(r->r.profession()==Profession.BAKER).count()==1,"Repeated staffing retains one cook");
  }finally{HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var n=l.getEntity(r.id());if(n!=null)n.discard();}SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
}
