package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmFoodPriorityGameTests {
 private static ResidentEntity farmer(ResearchV2Town.Town t){
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.FARMER,t.shop.id());npc.bind(t.s.id(),r);npc.setNoAi(true);t.l.addFreshEntity(npc);return npc;
 }
 private static List<BlockPos> field(ResearchV2Town.Town t){
  var plots=FarmField.cells(t.e,t.shop);for(var p:plots){t.l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);for(int y=0;y<3;y++)t.l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);t.l.setBlock(p.above(3),Blocks.SEA_LANTERN.defaultBlockState(),2);}var water=FarmField.water(t.e,t.shop).get(0);t.l.setBlock(water,Blocks.WATER.defaultBlockState(),2);t.l.setBlock(water.above(),FarmField.COVER,2);return plots;
 }
 private static CompoundTag state(ResidentEntity npc){var s=new CompoundTag();s.putInt("schema",1);s.putInt("width",1);s.putInt("height",3);s.putUUID("worker",npc.getUUID());s.putUUID("operation",UUID.randomUUID());s.putString("stage","choose");s.put("tool",new ItemStack(Items.STONE_HOE).save(new CompoundTag()));return s;}
 private static void at(ResidentEntity npc,BlockPos p){npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);npc.setOnGround(true);}
 @GameTest(template="empty",batch="farm_food_priority",timeoutTicks=200)
 public static void ripeFoodComesBeforeExpandingAnEmptyField(GameTestHelper h){var t=ResearchV2Town.town(h,"farm");var npc=farmer(t);field(t);h.runAfterDelay(12,()->{try{
  var plots=field(t);var ripe=plots.get(0);t.l.setBlock(ripe,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),2);var stock=LogisticsRoutes.chest(t.l,t.e,t.shop);stock.clearContent();stock.setItem(0,new ItemStack(Items.WHEAT_SEEDS,8));LogisticsRoutes.chest(t.l,t.e,t.hall()).clearContent();at(npc,ripe);MineWork.write(t.l,t.shop,state(npc));
  h.assertTrue(Blocks.WHEAT.defaultBlockState().canSurvive(t.l,plots.get(1)),"Optional fixture plot has real usable soil and propagated light");h.assertTrue(HandBread.open(t.l,t.e),"Real food stock is low while the seed bank is nonempty");var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Actual registered farmer starts");goal.tick();var chosen=MineWork.read(t.l,t.shop);h.assertTrue(chosen.getString("stage").equals("dig")&&BlockPos.of(chosen.getLong("target")).equals(ripe),"A mature real crop is harvested before collecting seeds for an optional empty plot");
  for(int n=0;n<4;n++)goal.tick();h.assertTrue(MineWork.read(t.l,t.shop).getString("stage").equals("replant"),"Real harvested plot keeps its ordinary immediate resowing");goal.tick();h.assertTrue(t.l.getBlockState(ripe).is(Blocks.WHEAT)&&t.l.getBlockState(ripe).getValue(CropBlock.AGE)==0,"Own harvested seed is spent on the original plot");h.assertTrue(ItemStack.of(MineWork.read(t.l,t.shop).getCompound("tool")).getDamageValue()==0,"Harvesting and resowing do not invent hoe wear");
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();});}
 @GameTest(template="empty",batch="farm_food_priority",timeoutTicks=200)
 public static void aPaidMealBatchReturnsBeforeOptionalSowingAndReplaysOnce(GameTestHelper h){var t=ResearchV2Town.town(h,"farm");var npc=farmer(t);field(t);h.runAfterDelay(12,()->{try{
  var plots=field(t);var empty=plots.get(0);var pos=LogisticsRoutes.position(t.e,t.shop);var stock=LogisticsRoutes.chest(t.l,t.e,t.shop);stock.clearContent();stock.setItem(0,new ItemStack(Items.WHEAT,5));stock.setItem(1,new ItemStack(Items.WHEAT_SEEDS,10));LogisticsRoutes.chest(t.l,t.e,t.hall()).clearContent();var paid=UUID.randomUUID();var cargo=new ListTag();cargo.add(WorldJournal.takeAmount(t.l,Settlement.childId(paid,"grain"),pos,0,stock.getItem(0).copy(),5).save(new CompoundTag()));cargo.add(WorldJournal.takeAmount(t.l,Settlement.childId(paid,"seed"),pos,1,stock.getItem(1).copy(),2).save(new CompoundTag()));var work=state(npc);work.put("cargo",cargo);work.putLong("target",empty.asLong());MineWork.write(t.l,t.shop,work);at(npc,empty);
  h.assertTrue(Blocks.WHEAT.defaultBlockState().canSurvive(t.l,empty),"Optional fixture plot is presently sowable");var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Farmer reloads an actual paid meal batch");goal.tick();var delivery=MineWork.read(t.l,t.shop);h.assertTrue(delivery.getString("stage").equals("deliver")&&delivery.getList("cargo",Tag.TAG_COMPOUND).equals(cargo),"Five paid grains go home before using spare seeds to expand the field");h.assertTrue(t.l.getBlockState(empty).isAir(),"Optional plot waits, no food or seeds are destroyed");at(npc,pos.offset(1,0,0));goal.tick();h.assertTrue(stock.countItem(Items.WHEAT)==5&&stock.countItem(Items.WHEAT_SEEDS)==10,"Real grain and both original seeds return to the actual farm chest");MineWork.write(t.l,t.shop,delivery);goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Delivery checkpoint resumes");goal.tick();h.assertTrue(stock.countItem(Items.WHEAT)==5&&stock.countItem(Items.WHEAT_SEEDS)==10,"Receipt replay never duplicates the meal or seed bank");
 }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();});}
}
