package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
import static org.villageastra.gametest.ResearchV2Town.*;
/** AD-137 (addendum, owner 2026-09-23): an NPC mayor orders a level whose estimate the village has or can make — what lies in the hall is
 *  reserved and the shortfall becomes workshop demand — and still refuses one that needs a workshop it lacks or a raw resource nobody brings
 *  in. A project that gains nothing for days is reported in the office, never called off silently. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorOrderGameTests {
 private static Settlement.Building record(Town t,String type,int x){var b=new Settlement.Building(Settlement.childId(t.s.id(),"record/"+type),type,x,0,40);t.s.addBuilding(b);return b;}
 /** The livestock yard's real estimate of level II with one item more that only one building of the village can bring about. In master no
  *  item is a workshop's alone: the hall's slow crafts (AD-114) run every vanilla crafting, smelting and stonecutting recipe and every
  *  workshop's custom one (the carpentry's stripped logs, the bakery's cooked meat included), so a missing producer can only be a building
  *  that brings the raw resource in. The feeder of AD-138 is not in master yet; redstone (raw from the mine only) stands in for it: the
  *  same rule — ordered when its one producer stands, refused when it does not. */
 @GameTest(template="empty",timeoutTicks=400) public static void theMayorOrdersTheYardOnlyWhenTheProducerOfItsMissingItemStands(GameTestHelper h){
  String type="livestock",item="minecraft:redstone";int level=2;
  var t=town(h,type);
  try{
   var b=t.kept();
   // A village that brings in wood, crops and (its own yard) animals: no mine.
   record(t,ForesterHut.TYPE,-60);record(t,"farm",-20);
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.expandHall();chest=LogisticsRoutes.chest(t.l,t.e,t.hall());
   var cost=new TreeMap<String,Integer>(BuildingTiers.cost(type,level));int slot=0;
   for(var entry:cost.entrySet()){var it=BuiltInRegistries.ITEM.get(new ResourceLocation(entry.getKey()));int n=entry.getValue();
    while(n>0){h.assertTrue(slot<chest.getContainerSize(),"The hall chest holds the fixture");int k=Math.min(n,it.getMaxStackSize());chest.setItem(slot++,new ItemStack(it,k));n-=k;}}
   h.assertTrue(MayorPlanner.affordable(t.l,t.e,b),"The yard's whole estimate of level II lies in the hall: ordered");
   cost.put(item,4);
   h.assertTrue(!Workshops.producible(t.l,t.e,item),"Without the mine nobody brings in "+item);
   h.assertTrue(!MayorPlanner.affordable(t.l,t.e,cost),"Only the missing mine brings in "+item+": no order of the yard");
   record(t,"mine",-40);
   h.assertTrue(Workshops.producible(t.l,t.e,item),"The mine brings in "+item);
   h.assertTrue(MayorPlanner.affordable(t.l,t.e,cost),"With the mine the mayor orders the yard short of "+item);
   // Everything a missing workshop makes, the hall makes too (slowly): with a mine, a lantern needs no smithy.
   h.assertTrue(Workshops.producible(t.l,t.e,"minecraft:lantern"),"The hall forges a lantern from the mine's iron and the forester's charcoal");
   // The order: what lies in the hall is kept for it, the missing item becomes the village's demand.
   var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);
   project.put("ops",new ListTag());project.put("cargo",new ListTag());var tag=new CompoundTag();cost.forEach(tag::putInt);project.put("cost",tag);HallUpgradeGoal.enqueue(t.l,t.e,project);
   var wanted=BuiltInRegistries.ITEM.get(new ResourceLocation(item));
   h.assertTrue(Workshops.wants(t.l,t.e).stream().anyMatch(w->w.ingredient().test(new ItemStack(wanted))&&w.count()==cost.get(item)),"The shortfall is demand: "+cost.get(item)+" "+item);
   var other=cost.keySet().stream().filter(k->!k.equals(item)).findFirst().orElseThrow();
   h.assertTrue(HallReserve.held(t.l,t.e,BuiltInRegistries.ITEM.get(new ResourceLocation(other)))==cost.get(other),"What lies in the hall is reserved: "+other);
   HallUpgradeGoal.drop(t.l,t.s.id());
   // A raw resource nobody brings in still refuses: netherite, and ring VI made of it even with a smithy.
   record(t,"smithy",20);
   h.assertTrue(!MayorPlanner.affordable(t.l,t.e,Map.of("minecraft:netherite_block",1)),"Nobody brings in netherite");
   h.assertTrue(!MayorPlanner.affordable(t.l,t.e,Map.of(CoreCatalog.ringId(6),1)),"Ring VI needs netherite: no order");
  }finally{HallUpgradeGoal.drop(t.l,t.s.id());done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aProjectThatGainsNothingForDaysIsReportedInTheOffice(GameTestHelper h){
  var t=town(h,null);
  try{
   var mayor=new Resident(Settlement.childId(t.s.id(),"mayor"),Resident.Life.ADULT,true,null,null,-1);t.s.admit(mayor,Settlement.childId(t.s.id(),"home"));
   t.s.assign(mayor.id(),Profession.MAYOR,t.hall().id());
   var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();
   var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);
   project.put("ops",new ListTag());project.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("minecraft:barrel",4);project.put("cost",cost);HallUpgradeGoal.enqueue(t.l,t.e,project);
   long now=t.l.getGameTime(),day=MayorPlanner.DAY;
   MayorPlanner.watch(t.l,t.e,now-4*day);MayorPlanner.watch(t.l,t.e,now);
   var state=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now-2*day)==0,"Two days without headway are not reported yet");
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now)==4,"Four days without headway are: "+MayorPlanner.stalledDays(t.l,t.e,state,now));
   var card=ConstructionViews.project(t.l,t.e,t.e.center(),state);
   h.assertTrue(card.getLong("stalledDays")==4,"The office card names the stall: "+card.getLong("stalledDays"));
   h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id()),"The project is not called off");
   // A barrel brought to the hall is headway: the count starts again.
   chest.setItem(0,new ItemStack(Items.BARREL));MayorPlanner.watch(t.l,t.e,now);
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now+2*day)==0,"Headway resets the stall");
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now+3*day)==3,"And it counts again from there");
   // A player's village is not the NPC mayor's: nothing is reported for it.
   t.s.unassign(mayor.id());
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now+5*day)==0,"Without an NPC mayor there is no mayor's stall");
   // Gone: the record goes too.
   HallUpgradeGoal.drop(t.l,t.s.id());MayorPlanner.watch(t.l,t.e,now);t.s.assign(mayor.id(),Profession.MAYOR,t.hall().id());
   h.assertTrue(MayorPlanner.stalledDays(t.l,t.e,state,now+5*day)==0,"A dropped project leaves no stall");
  }finally{HallUpgradeGoal.drop(t.l,t.s.id());MayorPlanner.watch(t.l,t.e,t.l.getGameTime());done(t);}
  h.succeed();
 }
}
