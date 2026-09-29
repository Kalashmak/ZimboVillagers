package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-136 (spec 1.3, 6.2, CF1, CF3): level I of a branch is paid in resources from the hall's (and warehouses') chests in one journal batch,
 *  never in part; short stock is the reason «resources»; an ordered payment brings porters and pays itself; an NPC mayor pays Engineering I
 *  first and gives its laboratory only a level II-VI target. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchResourcesGameTests {
 private static boolean done(Town t,String id){return BookResearch.completed(t.e,BookResearch.inspect(t.l,t.e)).contains(id);}
 @GameTest(template="empty",timeoutTicks=200) public static void aLevelOneNodeIsPaidInResourcesAtOnce(GameTestHelper h){
  var t=town(h,null);
  try{var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.OAK_LOG,20));c.setItem(1,new ItemStack(Items.BIRCH_LOG,20));c.setItem(2,new ItemStack(Items.CRAFTING_TABLE,2));
   var node=ResearchCatalog.get("engineering.1");h.assertTrue(node.works()==0&&node.resources().size()==2,"Engineering I costs resources, not works: "+node.resources());
   h.assertTrue(BookResearch.reason(t.l,t.e,BookResearch.inspect(t.l,t.e),"engineering.1").equals("available"),"The price lies in the hall chest");
   h.assertTrue(BookResearch.payResources(t.l,t.e,"engineering.1"),"Paid");
   int logs=c.countItem(Items.OAK_LOG)+c.countItem(Items.BIRCH_LOG);
   h.assertTrue(logs==8&&c.countItem(Items.CRAFTING_TABLE)==1,"32 logs of any wood and one table left the chest: logs "+logs+" tables "+c.countItem(Items.CRAFTING_TABLE));
   var record=BookResearch.inspect(t.l,t.e);
   h.assertTrue(done(t,"engineering.1")&&!record.contains("pay")&&!record.getCompound("paid").contains("engineering.1"),"Done as a resource payment, nothing left open: "+record);
   h.assertTrue(!BookResearch.payResources(t.l,t.e,"engineering.1")&&c.countItem(Items.CRAFTING_TABLE)==1,"Paid once only");
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void shortStockIsTheReasonResourcesAndAnOrderPaysItself(GameTestHelper h){
  var t=town(h,null);
  try{var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();c.setItem(0,new ItemStack(Items.OAK_LOG,10));
   var record=BookResearch.inspect(t.l,t.e);
   h.assertTrue(BookResearch.reason(t.l,t.e,record,"engineering.1").equals("resources"),"10 of 32 logs and no table: the reason is resources");
   h.assertTrue(!BookResearch.payResources(t.l,t.e,"engineering.1")&&c.countItem(Items.OAK_LOG)==10,"No part payment: the chest keeps its 10 logs");
   // The mayor's order: the porters are asked for what is missing (22 logs, a table) to the hall.
   var orders=record.getList("resourceOrders",Tag.TAG_STRING);orders.add(StringTag.valueOf("engineering.1"));record.put("resourceOrders",orders);BookResearch.store(t.l,t.e,record);
   var wants=BookResearch.wants(t.l,t.e);int logs=0,tables=0;
   for(var w:wants){if(w.matches(new ItemStack(Items.SPRUCE_LOG)))logs+=w.count();if(w.matches(new ItemStack(Items.CRAFTING_TABLE)))tables+=w.count();}
   h.assertTrue(logs==22&&tables==1,"The porters bring the shortfall: logs "+logs+" tables "+tables+" of "+wants);
   h.assertTrue(BookResearch.payOrders(t.l,t.e)==0&&!done(t,"engineering.1"),"Not yet");
   c.setItem(1,new ItemStack(Items.SPRUCE_LOG,22));c.setItem(2,new ItemStack(Items.CRAFTING_TABLE));
   h.assertTrue(BookResearch.payOrders(t.l,t.e)==1&&done(t,"engineering.1"),"With all of it in the chest the order pays itself");
   h.assertTrue(BookResearch.inspect(t.l,t.e).getList("resourceOrders",Tag.TAG_STRING).isEmpty()&&c.countItem(Items.OAK_LOG)+c.countItem(Items.SPRUCE_LOG)==0,"The order is gone, so is the price");
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theMayorPaysEngineeringOneFirst(GameTestHelper h){
  var t=town(h,null);
  try{var c=LogisticsRoutes.chest(t.l,t.e,t.hall());c.clearContent();
   // Both prices lie in the chest: Engineering I (32 logs, a table) and Science I (32 planks, 16 torches, a table).
   c.setItem(0,new ItemStack(Items.OAK_LOG,32));c.setItem(1,new ItemStack(Items.CRAFTING_TABLE,2));c.setItem(2,new ItemStack(Items.OAK_PLANKS,32));c.setItem(3,new ItemStack(Items.TORCH,16));
   h.assertTrue(t.s.governance().playerMayor()==null,"An NPC village");
   h.assertTrue(BookResearch.autoSelect(t.l,t.e).equals("engineering.1")&&done(t,"engineering.1"),"The mayor pays Engineering I first");
   h.assertTrue(BookResearch.autoSelect(t.l,t.e).equals("research.1")&&done(t,"research.1"),"Then Science I, the next of his list whose price lies in the stock");
   String target=BookResearch.autoSelect(t.l,t.e);var record=BookResearch.inspect(t.l,t.e);
   h.assertTrue(!target.isEmpty()&&ResearchCatalog.get(target).tier()>=2&&record.getString("selected").equals(target),"The laboratory's target is a level II-VI node: "+target);
   h.assertTrue(ResearchCatalog.NODES.values().stream().filter(n->n.tier()==1).noneMatch(n->n.id().equals(record.getString("selected"))),"Never a level-I node (CF3)");
  }finally{ResearchV2Town.done(t);}
  h.succeed();
 }
}
