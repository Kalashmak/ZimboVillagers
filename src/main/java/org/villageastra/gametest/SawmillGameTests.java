package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-131 §3.6: the forester's hut saws from IV — one log of its chest into six planks of its kind, by itself, exactly once a turn; it keeps
 *  eight logs, stops when the hall has its planks, leaves a log the village wants as a log, and the mayor may stop it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SawmillGameTests {
 private static void clear(Container c){for(int i=0;i<c.getContainerSize();i++)c.setItem(i,ItemStack.EMPTY);}
 private static void onlyPlanksAtHall(ForestFixture t,int planks){var hall=(Container)t.l.getBlockEntity(t.hall());for(int i=0;i<hall.getContainerSize();i++)if(hall.getItem(i).is(ItemTags.PLANKS))hall.setItem(i,ItemStack.EMPTY);
  for(int i=0;i<hall.getContainerSize()&&planks>0;i++)if(hall.getItem(i).isEmpty()){int n=Math.min(64,planks);hall.setItem(i,new ItemStack(Items.SPRUCE_PLANKS,n));planks-=n;}}
 @GameTest(template="empty",batch="saw_six",timeoutTicks=200) public static void aLevelFourHutSawsOneLogIntoSixPlanksOfItsKind(GameTestHelper h){
  var t=ForestFixture.create(h,4);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.hut())==4,"The hut works at IV");var c=t.chestBlock();clear(c);onlyPlanksAtHall(t,0);c.setItem(0,new ItemStack(Items.BIRCH_LOG,10));
   h.assertTrue(ForestryMachines.saw(t.l,t.e,t.hut(),4,40,List.of()),"The saw cuts at its turn");
   h.assertTrue(c.countItem(Items.BIRCH_LOG)==9&&c.countItem(Items.BIRCH_PLANKS)==6,"One birch log became six birch planks: "+c.countItem(Items.BIRCH_LOG)+" logs, "+c.countItem(Items.BIRCH_PLANKS)+" planks");
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,40,List.of())&&c.countItem(Items.BIRCH_LOG)==9&&c.countItem(Items.BIRCH_PLANKS)==6,"The same turn again cuts nothing more");
   h.assertTrue(ForestryMachines.saw(t.l,t.e,t.hut(),4,80,List.of())&&c.countItem(Items.BIRCH_PLANKS)==12,"The next turn cuts the next log");
   h.assertTrue(CoreEffects.value("forester","planks",4)==ForestBalance.PLANKS_PER_LOG&&CoreEffects.value("forester","planks",3)==4,"Six planks a log from IV, vanilla's four before");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="saw_reserve",timeoutTicks=200) public static void theSawKeepsTheLogReserve(GameTestHelper h){
  var t=ForestFixture.create(h,4);
  try{
   var c=t.chestBlock();clear(c);onlyPlanksAtHall(t,0);c.setItem(0,new ItemStack(Items.OAK_LOG,ForestBalance.LOG_KEEP));
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,40,List.of())&&c.countItem(Items.OAK_LOG)==ForestBalance.LOG_KEEP,"Eight oak logs are kept");
   c.setItem(0,new ItemStack(Items.OAK_LOG,ForestBalance.LOG_KEEP+1));
   h.assertTrue(ForestryMachines.saw(t.l,t.e,t.hut(),4,80,List.of())&&c.countItem(Items.OAK_LOG)==ForestBalance.LOG_KEEP,"The ninth is sawn");
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,120,List.of()),"And then the saw waits");
   c.setItem(1,new ItemStack(Items.SPRUCE_LOG,ForestBalance.LOG_KEEP+1));
   var wantOak=List.of(new Workshops.Want(Ingredient.of(Items.SPRUCE_LOG),4,t.hutId));
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,160,wantOak)&&c.countItem(Items.SPRUCE_LOG)==ForestBalance.LOG_KEEP+1,"A log the village wants as a log stays a log");
   h.assertTrue(ForestryMachines.saw(t.l,t.e,t.hut(),4,200,List.of())&&c.countItem(Items.SPRUCE_LOG)==ForestBalance.LOG_KEEP&&c.countItem(Items.SPRUCE_PLANKS)==6,"Once nobody wants it, the spruce is sawn apart from the oak");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="saw_target",timeoutTicks=200) public static void theSawStopsAtThePlankTarget(GameTestHelper h){
  var t=ForestFixture.create(h,4);
  try{
   var c=t.chestBlock();clear(c);c.setItem(0,new ItemStack(Items.OAK_LOG,30));onlyPlanksAtHall(t,ForestBalance.PLANK_TARGET);
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,40,List.of())&&c.countItem(Items.OAK_LOG)==30,"The hall has its "+ForestBalance.PLANK_TARGET+" planks: the logs stay logs");
   onlyPlanksAtHall(t,ForestBalance.PLANK_TARGET-1);
   h.assertTrue(ForestryMachines.saw(t.l,t.e,t.hut(),4,80,List.of()),"One plank short, it saws again");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="saw_below",timeoutTicks=200) public static void belowLevelFourThereIsNoSaw(GameTestHelper h){
  var t=ForestFixture.create(h,3);
  try{
   var c=t.chestBlock();clear(c);onlyPlanksAtHall(t,0);c.setItem(0,new ItemStack(Items.OAK_LOG,30));
   for(long now=0;now<=400;now++)ForestryMachines.tick(t.l,t.e,now,List.of());
   h.assertTrue(c.countItem(Items.OAK_LOG)==30&&c.countItem(Items.OAK_PLANKS)==0,"A hut of III cuts nothing: "+c.countItem(Items.OAK_PLANKS));
   h.assertTrue(ForestBalance.sawPeriod(3)==0&&ForestBalance.sawPeriod(4)==40&&ForestBalance.sawPeriod(5)==30&&ForestBalance.sawPeriod(6)==20,"The saw's pace: none, then a log every 2, 1.5 and 1 s");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="saw_mayor",timeoutTicks=200) public static void theMayorStopsTheSaw(GameTestHelper h){
  var t=ForestFixture.create(h,4);
  var mayor=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),"SawMayor"));mayor.setPos(t.origin.getX(),t.origin.getY()+1,t.origin.getZ());t.s.appointPlayerMayor(mayor.getUUID());
  try{
   var c=t.chestBlock();clear(c);onlyPlanksAtHall(t,0);c.setItem(0,new ItemStack(Items.OAK_LOG,30));var g=t.s.governance();
   h.assertTrue(ForestPolicies.orderSaw(mayor,t.s.id(),t.hutId,g.epoch(),g.revision()).isEmpty()&&!ForestPolicies.get(t.l.getServer()).sawOn(t.e,t.hut()),"The mayor stops the saw");
   h.assertTrue(!ForestryMachines.saw(t.l,t.e,t.hut(),4,40,List.of())&&c.countItem(Items.OAK_LOG)==30,"A stopped saw cuts nothing");
   h.assertTrue(ForestPolicies.orderSaw(mayor,t.s.id(),t.hutId,g.epoch()+1,g.revision()).equals("mayor"),"An order from an old term is refused");
   h.assertTrue(ForestPolicies.orderSaw(mayor,t.s.id(),t.hutId,g.epoch(),g.revision()).isEmpty()&&ForestryMachines.saw(t.l,t.e,t.hut(),4,80,List.of()),"Started again, it cuts");
   h.assertTrue(BuildingCards.card(t.l,t.e,t.hut()).getCompound("forest").getBoolean("sawOn"),"The card shows the saw running");
  }finally{ForestPolicies.get(t.l.getServer()).saw(t.s.id(),t.hutId,true);t.done();}
  h.succeed();
 }
}
