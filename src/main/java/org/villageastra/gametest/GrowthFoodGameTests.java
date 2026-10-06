package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GrowthFoodGameTests {
 @GameTest(template="empty",batch="growth_food",timeoutTicks=200)
 public static void spareHousingKeepsBakingPastDailyReserveUsingRealGrain(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var home=Settlement.childId(t.s.id(),"home");
   for(int i=0;i<6;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),home);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();chest.setItem(0,new ItemStack(Items.BREAD,13));chest.setItem(1,new ItemStack(Items.WHEAT,10));
   h.assertTrue(HandBread.reserveRations(t.e)==60&&Population.storedNutrition(t.l,t.e)==65&&HandBread.foodTarget(t.e)==70&&HandBread.open(t.l,t.e),"A spare home must not stall at thirteen bread below the unchanged seventy-ration birth gate");
   long now=t.l.getGameTime();var baker=t.s.residents().iterator().next().id();
   for(int turn=0;turn<300&&chest.countItem(Items.BREAD)<17;turn++)HandBread.advance(t.l,t.e,baker,now+=20);
   h.assertTrue(chest.countItem(Items.BREAD)==17&&chest.countItem(Items.WHEAT)==0&&Population.storedNutrition(t.l,t.e)>=70,"Ten real wheat and normal labour make the next four bread without a food grant");
   for(int turn=0;turn<5&&HandBread.busy(t.l,t.e);turn++)HandBread.advance(t.l,t.e,baker,now+=20);
   h.assertTrue(!HandBread.open(t.l,t.e),"Baking stops once the real growth reserve is available");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="growth_food_policy",timeoutTicks=200)
 public static void fullHousingAndPlayerGovernmentKeepOrdinaryDailyTarget(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var home=Settlement.childId(t.s.id(),"home");for(int i=0;i<6;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),home);
   t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(HandBread.foodTarget(t.e)==60,"A player mayor keeps the ordinary food policy");
   // A separate full home fixture avoids changing already admitted residents.
   t.s.appointNpcMayor();for(int i=0;i<2;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),home);
   h.assertTrue(HandBread.foodTarget(t.e)==HandBread.reserveRations(t.e)&&HandBread.foodTarget(t.e)==80,"No usable spare home means only the unchanged daily reserve");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
