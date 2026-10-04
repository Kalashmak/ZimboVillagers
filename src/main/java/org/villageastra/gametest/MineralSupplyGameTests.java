package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineralSupplyGameTests {
 @GameTest(template="empty",batch="mineral_supply",timeoutTicks=200)
 public static void minedSandstoneReachesTheHallBeforeCraftingMoreFromSand(GameTestHelper h){
  var t=town(h,"mine");var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   source.setItem(0,new ItemStack(Items.SANDSTONE,40));source.setItem(1,new ItemStack(Items.STONE_PICKAXE));
   dest.setItem(0,new ItemStack(Items.SAND,32));dest.setItem(1,new ItemStack(Items.COAL,8));
   var p=new CompoundTag();p.putUUID("id",UUID.randomUUID());p.putString("design","home");p.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("minecraft:smooth_sandstone",35);p.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),p);
   var route=LogisticsRoutes.choose(t.l,t.e);
   h.assertTrue(route!=null&&route.source().id().equals(t.shop.id())&&route.item().is(Items.SANDSTONE),"The hall requests the actual mined sandstone instead of leaving it at the mine");
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.PORTER,t.hall().id());npc.bind(t.s.id(),t.s.resident(r.id()));
   PorterWork.step(npc);var planned=PorterWork.inspect(t.l,npc.getUUID()).copy();
   var pos=LogisticsRoutes.position(t.e,t.shop);npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);PorterWork.step(npc);
   h.assertTrue(source.countItem(Items.SANDSTONE)==24&&dest.countItem(Items.SANDSTONE)==0,"Sixteen real blocks leave the mine but have not arrived remotely");
   pos=LogisticsRoutes.position(t.e,t.hall());npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);PorterWork.step(npc);
   org.villageastra.persistence.NbtRecord.write(PorterWork.path(t.l,npc.getUUID()),planned);PorterWork.step(npc);
   h.assertTrue(dest.countItem(Items.SANDSTONE)==16&&source.countItem(Items.SANDSTONE)==24&&source.countItem(Items.STONE_PICKAXE)==1,"Receipts replay without duplicate sandstone or exporting the miner's pick");
   var job=Workshops.plan(t.l,t.e,t.hall(),HallReserve.view(t.l,t.e,dest),Workshops.wants(t.l,t.e));
   h.assertTrue(job!=null&&job.outputs().stream().anyMatch(s->s.is(Items.SMOOTH_SANDSTONE)),"Supplied stone goes directly to smelting rather than the sand crafting recipe");
   h.assertTrue(dest.countItem(Items.SAND)==32,"Delivery and planning do not spend sand");
  }finally{npc.discard();HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="construction_inputs",timeoutTicks=200)
 public static void busyWorkshopStillReceivesOreForTheActiveHouseBeforeSurplusStone(GameTestHelper h){
  var t=town(h,"mine");try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   source.setItem(0,new ItemStack(Items.COBBLESTONE,64));source.setItem(1,new ItemStack(Items.RAW_IRON,2));dest.setItem(0,new ItemStack(Items.COAL,8));dest.setItem(1,new ItemStack(Items.TORCH,2));
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:lantern",2);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);
   var busy=new CompoundTag();busy.putString("stage","work");busy.putString("recipe","custom:straw_bedding");org.villageastra.persistence.NbtRecord.write(Workshops.path(t.l,t.hall().id()),busy);
   h.assertTrue(Workshops.published(t.l,t.hall().id()).isEmpty(),"A different paid job publishes no ore demand");
   var route=LogisticsRoutes.choose(t.l,t.e);h.assertTrue(route!=null&&route.source().id().equals(t.shop.id())&&route.item().is(Items.RAW_IRON)&&route.item().getCount()>=1&&route.item().getCount()<=2,"The house's next batch ore shortage outranks ordinary surplus stone while the workshop is busy: "+route);
   var warehouse=LogisticsRoutes.byNeed(t.l,t.e,t.hall(),null,16,null);h.assertTrue(warehouse!=null&&warehouse.item().is(Items.RAW_IRON),"The warehouse dispatch obeys the same dependency priority");
   dest.setItem(2,new ItemStack(Items.RAW_IRON,2));route=LogisticsRoutes.choose(t.l,t.e);h.assertTrue(route!=null&&route.item().is(Items.COBBLESTONE),"Satisfied ingredients stop being construction demand; normal output delivery resumes");
   h.assertTrue(source.countItem(Items.RAW_IRON)==2&&dest.countItem(Items.RAW_IRON)==2,"Planning never transfers or creates resources");
  }finally{HallUpgradeGoal.drop(t.l,t.s.id());ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mineral_products",timeoutTicks=200)
 public static void mineralExportsIncludeGeologyButNotToolsOrLivestockFeed(GameTestHelper h){
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);var yard=new Settlement.Building(UUID.randomUUID(),"livestock",0,0,0);
  for(var item:List.of(Items.SANDSTONE,Items.RED_SANDSTONE,Items.SAND,Items.GRAVEL,Items.ANDESITE,Items.DIORITE,Items.GRANITE,Items.FLINT))h.assertTrue(LogisticsRoutes.product(mine,new ItemStack(item)),"Mine exports its actual excavation product: "+item);
  h.assertTrue(!LogisticsRoutes.product(mine,new ItemStack(Items.STONE_PICKAXE))&&!LogisticsRoutes.product(yard,new ItemStack(Items.WHEAT)),"Tools and the yard's feed are not surplus mineral cargo");h.succeed();
 }
}
