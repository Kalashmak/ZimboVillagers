package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestFoodGameTests {
 @GameTest(template="empty",batch="forest_food",timeoutTicks=200)
 public static void gatheredApplesReachPantryOnceWithoutExportingSaplings(GameTestHelper h){
  var t=ResearchV2Town.town(h,"forester");var npc=VillageAstra.RESIDENT.get().create(t.l);try{
   var source=LogisticsRoutes.chest(t.l,t.e,t.shop);var dest=LogisticsRoutes.chest(t.l,t.e,t.hall());source.clearContent();dest.clearContent();
   source.setItem(0,new ItemStack(Items.OAK_SAPLING,32));source.setItem(1,new ItemStack(Items.APPLE,20));
   var route=LogisticsRoutes.choose(t.l,t.e);h.assertTrue(route!=null&&route.source().id().equals(t.shop.id())&&route.item().is(Items.APPLE),"Gathered forest food must not be left outside the pantry");
   var warehouse=LogisticsRoutes.byNeed(t.l,t.e,t.hall(),null,16,null);h.assertTrue(warehouse!=null&&warehouse.item().is(Items.APPLE),"Warehouse couriers collect forest food too");
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.PORTER,t.hall().id());npc.bind(t.s.id(),t.s.resident(r.id()));
   PorterWork.step(npc);var planned=PorterWork.inspect(t.l,npc.getUUID()).copy();
   var pos=LogisticsRoutes.position(t.e,t.shop);npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);PorterWork.step(npc);
   h.assertTrue(source.countItem(Items.APPLE)==4&&dest.countItem(Items.APPLE)==0&&Population.storedNutrition(t.l,t.e)==0,"Only real pickup spends apples; cargo does not feed a remote pantry");
   pos=LogisticsRoutes.position(t.e,t.hall());npc.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);PorterWork.step(npc);
   org.villageastra.persistence.NbtRecord.write(PorterWork.path(t.l,npc.getUUID()),planned);PorterWork.step(npc);
   h.assertTrue(dest.countItem(Items.APPLE)==16&&source.countItem(Items.APPLE)==4&&source.countItem(Items.OAK_SAPLING)==32,"Delivery replay conserves apples and leaves planting stock at the forester");
   h.assertTrue(Population.storedNutrition(t.l,t.e)==16*Population.nutrition(new ItemStack(Items.APPLE)),"Delivered apples contribute ordinary meal rations");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
