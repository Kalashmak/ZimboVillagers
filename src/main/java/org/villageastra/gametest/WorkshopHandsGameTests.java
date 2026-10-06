package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** Directed service clock and declared worker records; physical attendance remains WorkshopGoal's contract. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopHandsGameTests {
 @GameTest(template="empty",batch="workshop_hands",timeoutTicks=200)
 public static void twoHallWorkersSharePaidLaborWithoutDoubleCountingOneWorker(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var a=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);var b=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
   var home=Settlement.childId(t.s.id(),"home");t.s.admit(a,home);t.s.admit(b,home);t.s.assign(a.id(),Profession.MAYOR,t.hall().id());t.s.assign(b.id(),Profession.BUILDER,t.hall().id());
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();chest.setItem(0,new ItemStack(Items.OAK_LOG));
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.OAK_PLANKS),4,t.hall().id()));long start=t.l.getGameTime(),finished=-1;
   for(int step=0;step<250;step++){
    long now=start+20L*step;Workshops.advance(t.l,t.e,t.hall(),now,wants,a.id());
    long once=Workshops.inspect(t.l,t.hall().id()).getLong("labor");Workshops.advance(t.l,t.e,t.hall(),now,wants,a.id());
    h.assertTrue(Workshops.inspect(t.l,t.hall().id()).getLong("labor")==once,"Repeated call by the same worker grants no extra labor");
    Workshops.advance(t.l,t.e,t.hall(),now,wants,b.id());
    if(chest.countItem(Items.OAK_PLANKS)==4){finished=now-start;break;}
   }
   h.assertTrue(finished>=1800&&finished<2200,"Two real worker records pay the 3600 labor in roughly half the service time: "+finished);
   h.assertTrue(chest.countItem(Items.OAK_LOG)==0&&chest.countItem(Items.OAK_PLANKS)==4,"One real log funds exactly four planks, regardless of helpers");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_WORKSHOP_HANDS VERIFIED elapsed={} labor=3600 inputs=1_log outputs=4_planks directedClock=true",finished);
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
