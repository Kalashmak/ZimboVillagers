package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FoodConstructionGameTests {
 @GameTest(template="empty",batch="farm_seed_supply",timeoutTicks=200)
 public static void newManualFarmReceivesOnlyRealSurplusSeedAndOldFarmKeepsItsReserve(GameTestHelper h){
  var t=ResearchV2Town.town(h,"farm");
  try{
   var old=LogisticsRoutes.chest(t.l,t.e,t.shop);old.clearContent();old.setItem(0,new ItemStack(Items.WHEAT_SEEDS,32));
   var farm=new Settlement.Building(UUID.randomUUID(),"farm",55,0,0);t.s.addBuilding(farm);
   var position=LogisticsRoutes.position(t.e,farm);t.l.getChunkAt(position);t.l.setBlock(position,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var fresh=LogisticsRoutes.chest(t.l,t.e,farm);
   var request=Workshops.wants(t.l,t.e).stream().filter(w->w.destination().equals(farm.id())&&w.matches(new ItemStack(Items.WHEAT_SEEDS))).findFirst();
   h.assertTrue(request.isPresent()&&request.get().count()==8,"A level-I farmer asks for eight real seeds before sowing bare plots");
   var route=LogisticsRoutes.choose(t.l,t.e);h.assertTrue(route!=null&&route.source().equals(t.shop)&&route.destination().equals(farm)&&route.item().is(Items.WHEAT_SEEDS)&&route.item().getCount()==8,"The porter finds actual seed in the established farm");
   var operation=UUID.randomUUID();var held=org.villageastra.persistence.WorldJournal.takeAmount(t.l,operation,LogisticsRoutes.position(t.e,t.shop),0,old.getItem(0).copy(),8);
   var delivery=Settlement.childId(operation,"deliver");h.assertTrue(org.villageastra.persistence.WorldJournal.deposit(t.l,delivery,position,held),"Real seed fits the new chest");
   org.villageastra.persistence.WorldJournal.deposit(t.l,delivery,position,held);
   h.assertTrue(old.countItem(Items.WHEAT_SEEDS)==24&&fresh.countItem(Items.WHEAT_SEEDS)==8,"Delivery replay duplicates nothing and the first farm keeps its seeds");
   old.setItem(0,new ItemStack(Items.WHEAT_SEEDS,8));fresh.clearContent();h.assertTrue(LogisticsRoutes.choose(t.l,t.e)==null,"No porter takes the last eight seeds or creates replacement seed");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="food_construction",timeoutTicks=200)
 public static void paidSchoolWaitsIntactWhileRealFarmAndFirstFieldAreOrdered(GameTestHelper h){
  var t=ResearchV2Town.town(h,"farm");var id=t.s.id();
  try{
   var home=Settlement.childId(id,"home");for(int i=0;i<8;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,i==0?Profession.MAYOR:null,null,-1),home);
   ResearchV2Town.learn(t,ResearchGate.forDesign("farm").toArray(String[]::new));
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.setItem(0,new ItemStack(Items.OAK_LOG,8));
   var school=new CompoundTag();var project=UUID.randomUUID();school.putUUID("id",project);school.putUUID("project",project);school.putString("kind","building");school.putString("design","school");school.putLong("origin",t.e.center().offset(70,0,0).asLong());
   var cost=new CompoundTag();cost.putInt("minecraft:bookshelf",7);school.put("cost",cost);chest.setItem(1,new ItemStack(Items.OAK_PLANKS,2));var paid=org.villageastra.persistence.WorldJournal.takeAmount(t.l,Settlement.childId(project,"fund/0"),LogisticsRoutes.position(t.e,t.hall()),1,chest.getItem(1).copy(),2);var cargo=new ListTag();cargo.add(paid.save(new CompoundTag()));school.put("cargo",cargo);school.putInt("withdrawals",1);school.put("ops",new ListTag());HallUpgradeGoal.store(t.l,id,school);
   h.assertTrue(FoodConstruction.maySuspend(t.l,t.e),"An unfunded school cannot prevent food expansion");
   t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"Only an independent NPC village changes its construction priority");t.s.appointNpcMayor();
   h.assertTrue(!FoodConstruction.survey(t.l,t.e,BlockPos.of(school.getLong("origin"))).ok(),"The waiting school keeps its growth space");
   var site=t.e.center().offset(-70,0,40);
   for(int x=-3;x<30;x++)for(int z=-3;z<40;z++){
    var p=site.offset(x,0,z);t.l.getChunkAt(p);t.l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=1;y<20;y++)t.l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
   }
   var survey=FoodConstruction.survey(t.l,t.e,site);h.assertTrue(survey.ok(),"A farm has a payable, clear site and field: "+survey.reason()+" "+survey.conflicts().stream().limit(3).toList());
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);int springs=0,fields=0;
   for(var raw:ops){var op=(CompoundTag)raw;if(op.getBoolean("field")){fields++;if(op.getCompound("after").getString("Name").equals("minecraft:water"))springs++;}}
   h.assertTrue(fields>0&&springs==1&&survey.state().getCompound("cost").getInt(FarmField.COVER_ITEM)>0,"The initial field is in ordinary paid construction, including its source cover");
   h.assertTrue(FoodConstruction.approve(t.l,t.e,site).isEmpty(),"Food rescue enters the durable construction queue");
   var active=HallUpgradeGoal.inspect(t.l,id);h.assertTrue(active.getString("design").equals("farm")&&active.getCompound("waitingProject").equals(school),"Exact school identity, cargo, withdrawals and operations survive atomically");
   h.assertTrue(chest.countItem(Items.OAK_PLANKS)==0&&org.villageastra.persistence.WorldJournal.recoverAmount(t.l,Settlement.childId(project,"fund/0")).getCount()==2,"Real funding receipt and empty source survive the handover");
   h.assertTrue(HallUpgradeGoal.cancellable(t.l,id,HallConstructionPlan.projectId(active)).equals("started"),"Cancellation cannot discard the waiting school cargo");
   h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e)&&!FoodConstruction.resume(t.l,t.e),"No nested rescue and no early restoration");
   h.assertTrue(!HallUpgradeGoal.headerView(t.l,id).contains("waitingProject"),"Lightweight decisions do not copy the waiting plan");
   // Directed recovery fixture: registration and completion are both necessary.
   active.putBoolean("complete",true);HallUpgradeGoal.store(t.l,id,active);
   h.assertTrue(HallUpgradeGoal.pending(t.l,id),"Completed rescue still holds the slot until restoration");
   h.assertTrue(!FoodConstruction.resume(t.l,t.e),"An unregistered farmhouse cannot return the slot");
   var offset=site.subtract(t.e.center());t.s.addBuilding(new Settlement.Building(BuildingOrders.buildingId(active),"farm",offset.getX(),offset.getY(),offset.getZ()));
   h.assertTrue(FoodConstruction.resume(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,id).equals(school),"Reloaded completion restores the exact old project, once");
   h.assertTrue(!FoodConstruction.resume(t.l,t.e),"A second recovery cannot duplicate old cargo");
   school.putBoolean("funded",true);HallUpgradeGoal.store(t.l,id,school);h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"Work already ready to build is never interrupted");
  }finally{HallUpgradeGoal.drop(t.l,id);ResearchV2Town.done(t);}h.succeed();
 }
}
