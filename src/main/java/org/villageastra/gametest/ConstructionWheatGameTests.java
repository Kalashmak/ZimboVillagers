package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ConstructionWheatGameTests {
 private static ResearchV2Town.Town town(GameTestHelper h){
  var t=ResearchV2Town.town(h,"farm");var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.clearContent();LogisticsRoutes.chest(t.l,t.e,t.shop).clearContent();
  for(int i=0;i<6;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),Settlement.childId(t.s.id(),"home"));
  hall.setItem(0,new ItemStack(Items.BREAD,6));hall.setItem(1,new ItemStack(Items.OAK_PLANKS,6));hall.setItem(2,new ItemStack(Items.WHEAT,8));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:white_bed",1);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);return t;
 }
 @GameTest(template="empty",batch="construction_wheat",timeoutTicks=200)
 public static void fedVillageAccumulatesOnlyTheNextBuildingRecipeWheat(GameTestHelper h){var t=town(h);try{
  var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==0,"Eight grains accumulate toward the bed while the next meal is covered");
  var other=Workshops.plan(t.l,t.e,t.hall(),HallReserve.view(t.l,t.e,hall),Workshops.wants(t.l,t.e));h.assertTrue(other==null||other.outputs().stream().anyMatch(st->st.is(Items.WHITE_BED))||other.inputs().stream().noneMatch(in->in.matches(new ItemStack(Items.WHEAT))),"The ordinary hall must not mill the same held grain for bread either");
  hall.setItem(2,new ItemStack(Items.WHEAT,12));h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==0,"Reaching the recipe cost does not release it back to bread");
  var job=Workshops.plan(t.l,t.e,t.hall(),HallReserve.view(t.l,t.e,hall),Workshops.wants(t.l,t.e));h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.WHITE_BED)),"The ordinary workshop can now fund the actual bed");
  hall.setItem(2,new ItemStack(Items.WHEAT,17));LogisticsRoutes.chest(t.l,t.e,t.shop).setItem(0,new ItemStack(Items.WHEAT,5));h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==10,"Only twelve hall grains are held; surplus and farm harvest still bake bread");
  h.assertTrue(hall.countItem(Items.WHEAT)==17,"Read-only planning neither spends nor grants wheat");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="construction_wheat",timeoutTicks=200)
 public static void foodShortageOrMissedMealReleasesAllConstructionWheat(GameTestHelper h){var t=town(h);try{
  var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.setItem(0,new ItemStack(Items.BREAD,5));h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==8,"Insufficient next meal takes priority over a bed");
  hall.setItem(0,new ItemStack(Items.BREAD,64));t.s.residents().iterator().next().restoreNeeds(0,0,1,0);h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==8,"Any missed meal releases the grain even with a nominal food stock");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
 @GameTest(template="empty",batch="construction_wheat",timeoutTicks=200)
 public static void fundedOrOtherwiseUnreadyConstructionHoldsNoBreadWheat(GameTestHelper h){var t=town(h);try{
  var hall=LogisticsRoutes.chest(t.l,t.e,t.hall());hall.setItem(1,ItemStack.EMPTY);h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==8,"Missing other ingredients must not hold grain indefinitely");
  hall.setItem(1,new ItemStack(Items.OAK_PLANKS,6));var p=HallUpgradeGoal.inspect(t.l,t.s.id());p.putBoolean("funded",true);HallUpgradeGoal.store(t.l,t.s.id(),p);h.assertTrue(HandBread.wheatAvailable(t.l,t.e)==8,"A paid project does not reserve a second bed's grain");
 }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();}
}
