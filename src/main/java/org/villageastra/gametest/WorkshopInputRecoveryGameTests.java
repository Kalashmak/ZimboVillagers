package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopInputRecoveryGameTests {
 @GameTest(template="empty",batch="workshop_input_recovery",timeoutTicks=200)
 public static void claimedPlanksReleasePartPaidChestToMakeItsOwnMissingInputs(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var hall=t.hall();var chest=LogisticsRoutes.chest(t.l,t.e,hall);var pos=Workshops.station(t.e,hall);chest.clearContent();
   chest.setItem(0,new ItemStack(Items.OAK_PLANKS,8));chest.setItem(1,new ItemStack(Items.OAK_LOG,2));
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.CHEST),1,hall.id()));long now=t.l.getGameTime();
   Workshops.advance(t.l,t.e,hall,now,wants);h.assertTrue(Workshops.inspect(t.l,hall.id()).getString("recipe").equals("minecraft:chest"),"The real planner sees enough planks for a chest");
   var claimed=WorldJournal.takeAmount(t.l,UUID.randomUUID(),pos,0,chest.getItem(0).copy(),5);
   Workshops.advance(t.l,t.e,hall,now+=20,wants);h.assertTrue(chest.countItem(Items.OAK_PLANKS)==0,"The original order actually pays the three remaining planks");
   Workshops.advance(t.l,t.e,hall,now+=20,wants);var refund=Workshops.inspect(t.l,hall.id());h.assertTrue(refund.getString("stage").equals("refund"),"Logs can supply missing planks after this station releases the paid chest job");
   Workshops.advance(t.l,t.e,hall,now+=20,wants);h.assertTrue(chest.countItem(Items.OAK_PLANKS)==3,"Three paid planks return to the real chest");
   NbtRecord.write(Workshops.path(t.l,hall.id()),refund);Workshops.advance(t.l,t.e,hall,now+=20,wants);h.assertTrue(chest.countItem(Items.OAK_PLANKS)==3,"Replaying the old refund checkpoint returns no extra planks");
   for(int turn=0;turn<2000&&chest.countItem(Items.CHEST)==0;turn++)Workshops.advance(t.l,t.e,hall,now+=20,wants);
   h.assertTrue(chest.countItem(Items.CHEST)==1&&chest.countItem(Items.OAK_LOG)==0&&chest.countItem(Items.OAK_PLANKS)==3&&claimed.getCount()==5,"Two real logs fund eight new planks; the finished chest, remainder and outside claim conserve every input");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
