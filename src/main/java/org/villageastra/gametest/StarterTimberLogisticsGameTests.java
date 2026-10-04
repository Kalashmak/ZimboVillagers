package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StarterTimberLogisticsGameTests {
 @GameTest(template="empty",batch="starter_logistics")
 public static void waitingFurnaceRequestsRealWoodFuelFromForester(GameTestHelper h){
  var t=ResearchV2Town.town(h,"forester");
  try{
   var hall=Workshops.hall(t.e);var stock=LogisticsRoutes.chest(t.l,t.e,hall);stock.clearContent();stock.setItem(0,new ItemStack(Items.SAND));stock.setItem(1,new ItemStack(Items.STICK,2));
   t.l.setBlock(t.e.center().offset(4,1,4),net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState(),2);
   var wants=List.of(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(Items.GLASS),1,hall.id()));
   stock.setItem(1,ItemStack.EMPTY);Workshops.advance(t.l,t.e,hall,900,wants);
   h.assertTrue(Workshops.published(t.l,hall.id()).stream().anyMatch(in->in.matches(new ItemStack(Items.STICK))),"Idle smelting planning also requests wood fuel before a job can start");stock.setItem(1,new ItemStack(Items.STICK,2));
   for(int i=0;i<10&&!Workshops.inspect(t.l,hall.id()).getString("stage").equals("smelt_fuel");i++)Workshops.advance(t.l,t.e,hall,1000+i*20,wants);
   h.assertTrue(Workshops.inspect(t.l,hall.id()).getString("stage").equals("smelt_fuel"),"Real smelting job waits for fuel after inserting sand");
   for(int i=0;i<stock.getContainerSize();i++)if(stock.getItem(i).is(Items.STICK))stock.setItem(i,ItemStack.EMPTY);
   Workshops.advance(t.l,t.e,hall,1400,wants);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();chest.setItem(0,new ItemStack(Items.STICK,3));var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
   h.assertTrue(route!=null&&route.item().is(Items.STICK)&&route.destination().id().equals(hall.id()),"Published furnace demand can be supplied by real sticks without coal");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="starter_logistics")
 public static void starterHutExportsLastLogsForMineSupports(GameTestHelper h){
  var t=ResearchV2Town.town(h,"forester");
  try{
   var hall=Workshops.hall(t.e);LogisticsRoutes.chest(t.l,t.e,hall).clearContent();var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.clearContent();chest.setItem(0,new ItemStack(Items.OAK_LOG,8));
   var mine=new Settlement.Building(UUID.randomUUID(),"mine",40,0,0);t.s.addBuilding(mine);var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,mine.id());
   var state=MineWork.read(t.l,mine);state.putInt("step",4);state.putString("stage","support_fetch");MineWork.write(t.l,mine,state);
   var route=LogisticsRoutes.workerRoute(t.l,t.e,t.shop);
   h.assertTrue(route!=null&&route.source().id().equals(t.shop.id())&&route.destination().id().equals(hall.id())&&route.item().is(Items.OAK_LOG)&&route.item().getCount()==MineWork.beam(state).count(),"The forester can carry the mine's actual beam demand from the last eight logs: route="+route+" wants="+WorkerSupplies.wants(t.l,t.e)+" beam="+MineWork.beam(state).count()+" reserve="+LogisticsRoutes.reserve(t.shop,new ItemStack(Items.OAK_LOG)));
   h.assertTrue(chest.countItem(Items.OAK_LOG)==8,"Planning does not withdraw or duplicate timber");
   for(int level=1;level<=6;level++){var hut=new Settlement.Building(UUID.randomUUID(),"forester",0,0,0,0,level);h.assertTrue(LogisticsRoutes.reserve(hut,new ItemStack(Items.OAK_LOG))==(level>=ForestBalance.SAW_FROM?ForestBalance.LOG_KEEP:0),"Sawmill reserve applies only where the saw exists");}
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
