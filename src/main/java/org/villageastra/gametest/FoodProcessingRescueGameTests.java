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
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

/** Paid work remains intact when real fields outgrow the single hand baker. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FoodProcessingRescueGameTests {
 @GameTest(template="empty",batch="food_processing_rescue",timeoutTicks=200)
 public static void grainSurplusNeedsKitchenBeforeWaitingUnfundedGuardHouse(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var id=t.s.id();
  try{
   var home=Settlement.childId(id,"home");var extra=UUID.randomUUID();t.s.addHome(new Settlement.Home(extra,1,4,true));
   for(int i=0;i<11;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,i==0?Profession.MAYOR:null,null,-1),i<8?home:extra);
   for(int i=0;i<3;i++)t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),"farm",35+i*35,0,35));
   t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),"school",35,0,-40));
   ResearchV2Town.learn(t,"agriculture.1","baking.1","education.1");
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();chest.setItem(0,new ItemStack(Items.OAK_LOG,8));chest.setItem(1,new ItemStack(Items.OAK_PLANKS,2));chest.setItem(2,new ItemStack(Items.WHEAT,64));
   var pending=new CompoundTag();var project=UUID.randomUUID();pending.putUUID("id",project);pending.putUUID("project",project);pending.putString("kind","building");pending.putString("design","guard_house");pending.putLong("origin",t.e.center().offset(70,0,-40).asLong());
   var cost=new CompoundTag();cost.putInt("minecraft:polished_andesite",48);pending.put("cost",cost);
   var paid=WorldJournal.takeAmount(t.l,Settlement.childId(project,"fund/0"),LogisticsRoutes.position(t.e,t.hall()),1,chest.getItem(1).copy(),2);var cargo=new ListTag();cargo.add(paid.save(new CompoundTag()));pending.put("cargo",cargo);pending.putInt("withdrawals",1);pending.put("ops",new ListTag());HallUpgradeGoal.store(t.l,id,pending);
   h.assertTrue(!MayorPlanner.foodShortage(t.l,t.e),"Three already worked fields cover real crop demand; another field is not the bottleneck");
   h.assertTrue("restaurant".equals(MayorPlanner.need(t.l,t.e)),"An unlocked kitchen with an available adult is the food-processing project");
   h.assertTrue(FoodConstruction.maySuspend(t.l,t.e),"Enough grain must not hide the single hand baker throughput limit behind an unfunded guard house");
   t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"Player government retains its project");t.s.appointNpcMayor();
   var site=t.e.center().offset(-70,0,40);
   for(int x=-3;x<30;x++)for(int z=-3;z<40;z++){
    var p=site.offset(x,0,z);t.l.getChunkAt(p);t.l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=1;y<20;y++)t.l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
   }
   h.assertTrue(FoodConstruction.approve(t.l,t.e,site).isEmpty(),"Paid kitchen enters the normal construction queue");
   var active=HallUpgradeGoal.inspect(t.l,id);
   h.assertTrue(active.getString("design").equals("restaurant")&&active.getCompound("waitingProject").equals(pending),"Guard identity, paid cargo and receipts survive exactly");
   h.assertTrue(!active.getBoolean("funded")&&active.getCompound("cost").size()>0&&active.getList("ops",Tag.TAG_COMPOUND).size()>0,"No free restaurant or instant completion");
   h.assertTrue(chest.countItem(Items.OAK_PLANKS)==0&&WorldJournal.recoverAmount(t.l,Settlement.childId(project,"fund/0")).getCount()==2,"No returned or duplicated waiting materials");
   h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e)&&!FoodConstruction.resume(t.l,t.e),"No nested rescue or premature restoration");
   active.putBoolean("complete",true);HallUpgradeGoal.store(t.l,id,active);h.assertTrue(!FoodConstruction.resume(t.l,t.e),"Completion alone without registration is insufficient");
   var offset=site.subtract(t.e.center());t.s.addBuilding(new Settlement.Building(BuildingOrders.buildingId(active),"restaurant",offset.getX(),offset.getY(),offset.getZ()));
   h.assertTrue(FoodConstruction.resume(t.l,t.e)&&HallUpgradeGoal.inspect(t.l,id).equals(pending),"Registered completion restores the exact paid guard project once");
   h.assertTrue(!FoodConstruction.resume(t.l,t.e),"No second restoration");
   pending.putBoolean("funded",true);HallUpgradeGoal.store(t.l,id,pending);h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"A fully funded construction is never interrupted");
   pending.putBoolean("funded",false);pending.putInt("index",1);HallUpgradeGoal.store(t.l,id,pending);h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"Physical work already started is not interrupted");
   pending.putInt("index",0);pending.putBoolean("repair",true);HallUpgradeGoal.store(t.l,id,pending);h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"A repair remains its own durable job");
   pending.remove("repair");pending.putBoolean("relocate",true);HallUpgradeGoal.store(t.l,id,pending);h.assertTrue(!FoodConstruction.maySuspend(t.l,t.e),"A relocation is not suspended as a new building");
  }finally{HallUpgradeGoal.drop(t.l,id);ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="food_processing_rescue",timeoutTicks=200)
 public static void kitchenPriorityKeepsRecipeResearchAndGovernmentBounds(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var id=t.s.id();
  try{
   var home=Settlement.childId(id,"home");var extra=UUID.randomUUID();t.s.addHome(new Settlement.Home(extra,1,4,true));
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();chest.setItem(0,new ItemStack(Items.WHEAT,64));
   for(int i=0;i<6;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,i==0?Profession.MAYOR:null,null,-1),home);
   h.assertTrue(!MayorPlanner.processingShortage(t.l,t.e),"Six residents and the next child remain below the unchanged manual daytime upper bound");
   for(int i=6;i<11;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),i<8?home:extra);
   var pending=new CompoundTag();var project=UUID.randomUUID();pending.putUUID("id",project);pending.putUUID("project",project);pending.putString("kind","building");pending.putString("design","guard_house");pending.putLong("origin",t.e.center().offset(70,0,0).asLong());pending.put("ops",new ListTag());pending.put("cargo",new ListTag());pending.put("cost",new CompoundTag());HallUpgradeGoal.store(t.l,id,pending);
   h.assertTrue(MayorPlanner.processingShortage(t.l,t.e)&&!FoodConstruction.maySuspend(t.l,t.e),"Need does not bypass locked restaurant research");
   ResearchV2Town.learn(t,"baking.1");h.assertTrue(FoodConstruction.maySuspend(t.l,t.e),"Unlocked kitchen can address the actual daytime capacity deficit");
   chest.clearContent();h.assertTrue(!MayorPlanner.processingShortage(t.l,t.e),"Absent grain needs a supply chain, not a misleading kitchen order");chest.setItem(0,new ItemStack(Items.WHEAT,64));
   t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!MayorPlanner.processingShortage(t.l,t.e),"Player government retains its own priorities");t.s.appointNpcMayor();
   t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),"restaurant",40,0,40));h.assertTrue(!MayorPlanner.processingShortage(t.l,t.e),"Existing kitchen is staffed and supplied rather than duplicated");
   h.assertTrue(HandBread.LABOR_PER_BREAD==600&&HandBread.WHEAT_PER_UNIT==5&&HandBread.BREAD_PER_UNIT==2,"Owner manual recipe and labor are unchanged");
  }finally{HallUpgradeGoal.drop(t.l,id);ResearchV2Town.done(t);}h.succeed();
 }
}
