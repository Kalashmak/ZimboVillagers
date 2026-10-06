package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
/** Directed crash/race fixture using real withdrawals, refunds and crafting. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkshopToolRecoveryGameTests {
 @GameTest(template="empty",batch="workshop_tool_recovery",timeoutTicks=200)
 public static void claimedAxeRefundsOldPaidLogThenCraftsReplacementWithoutDuplicatingRefund(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);
  try{
   var hall=t.hall();var chest=LogisticsRoutes.chest(t.l,t.e,hall);var pos=Workshops.station(t.e,hall);chest.clearContent();
   chest.setItem(0,new ItemStack(Items.OAK_LOG));chest.setItem(1,new ItemStack(Items.STONE_AXE));chest.setItem(2,new ItemStack(Items.OAK_PLANKS,3));chest.setItem(3,new ItemStack(Items.STICK,2));chest.setItem(4,new ItemStack(Items.COBBLESTONE,3));
   var wants=List.of(new Workshops.Want(Ingredient.of(Items.STRIPPED_OAK_LOG),1,hall.id()));long now=t.l.getGameTime();Workshops.advance(t.l,t.e,hall,now,wants);
   var original=Workshops.inspect(t.l,hall.id());h.assertTrue(original.getString("recipe").equals("custom:strip_oak_log"),"The actual stripping recipe is planned with a supplied axe");
   var id=original.getUUID("id");var paid=WorldJournal.takeAmount(t.l,Settlement.childId(id,"input/0"),pos,0,chest.getItem(0).copy(),1);
   var stacks=new ListTag();stacks.add(paid.save(new CompoundTag()));original.put("paid",stacks);original.putInt("withdrawals",1);NbtRecord.write(Workshops.path(t.l,hall.id()),original);
   var claimed=WorldJournal.takeAmount(t.l,UUID.randomUUID(),pos,1,chest.getItem(1).copy(),1);h.assertTrue(claimed.is(Items.STONE_AXE),"Another consumer physically claims the planned tool");
   Workshops.advance(t.l,t.e,hall,now+=20,wants);var refund=Workshops.inspect(t.l,hall.id());h.assertTrue(refund.getString("stage").equals("refund"),"Saved log-first order releases the station to produce its missing tool");
   Workshops.advance(t.l,t.e,hall,now+=20,wants);h.assertTrue(chest.countItem(Items.OAK_LOG)==1,"One paid log returns physically to stock");
   NbtRecord.write(Workshops.path(t.l,hall.id()),refund);Workshops.advance(t.l,t.e,hall,now+=20,wants);h.assertTrue(chest.countItem(Items.OAK_LOG)==1,"Old refund checkpoint replays the same receipt without duplicating the log");
   for(int step=0;step<2500&&chest.countItem(Items.STRIPPED_OAK_LOG)==0;step++)Workshops.advance(t.l,t.e,hall,now+=20,wants);
   h.assertTrue(chest.countItem(Items.STRIPPED_OAK_LOG)==1&&chest.countItem(Items.OAK_LOG)==0,"Replacement tool allows the same paid log to finish stripping");
   for(int step=0;step<5&&!Workshops.inspect(t.l,hall.id()).getString("stage").equals("idle");step++)Workshops.advance(t.l,t.e,hall,now+=20,wants);
   int tools=0;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(net.minecraft.tags.ItemTags.AXES)){h.assertTrue(chest.getItem(slot).getDamageValue()==1,"The newly crafted tool paid one actual use");tools+=chest.getItem(slot).getCount();}
   h.assertTrue(tools==1&&claimed.getDamageValue()==0,"Only the replacement axe returns; the claimed original is not recreated");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
