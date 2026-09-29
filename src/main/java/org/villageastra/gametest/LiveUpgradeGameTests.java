package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-115 audit: every ordered block operation must retain the old working tier and the identities/contents of existing inventories.
 * This does not assert that workers can walk through every intermediate room; that needs a separate live-client route check. */
@GameTestHolder(org.villageastra.VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LiveUpgradeGameTests {
 private record Stock(Container inventory,List<ItemStack> slots){}
 @GameTest(template="empty",batch="live_upgrade_smithy",timeoutTicks=600) public static void liveUpgradeSmithyKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"smithy");}
 @GameTest(template="empty",batch="live_upgrade_clinic",timeoutTicks=600) public static void liveUpgradeClinicKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"clinic");}
 @GameTest(template="empty",batch="live_upgrade_home",timeoutTicks=600) public static void liveUpgradeHomeKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"home");}
 @GameTest(template="empty",batch="live_upgrade_mine",timeoutTicks=600) public static void liveUpgradeMineKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"mine");}
 @GameTest(template="empty",batch="live_upgrade_forester",timeoutTicks=600) public static void liveUpgradeForesterKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"forester");}
 @GameTest(template="empty",batch="live_upgrade_warehouse",timeoutTicks=600) public static void liveUpgradeWarehouseKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"warehouse");}
 @GameTest(template="empty",batch="live_upgrade_restaurant",timeoutTicks=600) public static void liveUpgradeRestaurantKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"restaurant");}
 @GameTest(template="empty",batch="live_upgrade_laboratory",timeoutTicks=600) public static void liveUpgradeLaboratoryKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"laboratory");}
 @GameTest(template="empty",batch="live_upgrade_carpentry",timeoutTicks=600) public static void liveUpgradeCarpentryKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"carpentry");}
 @GameTest(template="empty",batch="live_upgrade_masonry",timeoutTicks=600) public static void liveUpgradeMasonryKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"masonry");}
 @GameTest(template="empty",batch="live_upgrade_mill",timeoutTicks=600) public static void liveUpgradeMillKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"mill");}
 @GameTest(template="empty",batch="live_upgrade_engineering",timeoutTicks=600) public static void liveUpgradeEngineeringKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"engineering");}
 @GameTest(template="empty",batch="live_upgrade_caravan",timeoutTicks=600) public static void liveUpgradeCaravanKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"caravan");}
 @GameTest(template="empty",batch="live_upgrade_school",timeoutTicks=600) public static void liveUpgradeSchoolKeepsWorkingKitAndStock(GameTestHelper h){audit(h,"school");}
 private static void audit(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,type.equals("mine")?90:4,2));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var id=UUID.randomUUID();var b=new Settlement.Building(id,type,17,0,4,0,2);s.addBuilding(b);var origin=BuildingPlacement.origin(e,b);var d=BuildingBlueprints.design(type);
  for(int x=-3;x<=d.width()+3;x++)for(int z=-3;z<=d.depth()+3;z++){l.setBlock(origin.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=28;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  if(type.equals("mine"))for(int x=-3;x<=d.width()+3;x++)for(int z=-3;z<=d.depth()+3;z++)for(int y=-12;y<0;y++)l.setBlock(origin.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  BuildingPlacement.layout(type+"@2",origin,0).forEach((p,st)->l.setBlock(p,st,2));
  var dependency=BuildingTiers.parent(type,BuildingTiers.max(type));
  if(dependency!=null){var parent=new Settlement.Building(UUID.randomUUID(),dependency.type(),75,90,0,0,dependency.level());s.addBuilding(parent);
   BuildingPlacement.layout(e,parent,BuildingTiers.layoutId(s,parent.type(),parent.level())).forEach((p,st)->l.setBlock(p,st,2));
   h.assertTrue(BuildingLevels.best(l,e,parent.type())>=dependency.level(),"Fixture contains the real parent equipment");}
  var research=BookResearch.inspect(l,e);var known=new ListTag();ResearchCatalog.NODES.keySet().forEach(k->known.add(StringTag.valueOf(k)));research.put("legacyDone",known);BookResearch.store(l,e,research);
  try{for(int old=2;old<BuildingTiers.max(type);old++){
   var current=s.buildings().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();var inventories=new LinkedHashMap<BlockPos,Stock>();
   for(var p:BuildingPlacement.layout(type+"@"+old,origin,0).keySet())if(l.getBlockEntity(p) instanceof Container c){
    c.setItem(0,l.getBlockState(p).is(Blocks.CHISELED_BOOKSHELF)?new ItemStack(Items.BOOK):new ItemStack(Items.DIAMOND,7));
    var slots=new ArrayList<ItemStack>();for(int slot=0;slot<c.getContainerSize();slot++)slots.add(c.getItem(slot).copy());inventories.put(p,new Stock(c,slots));
   }
   h.assertTrue(inventories.values().stream().anyMatch(stock->stock.slots().stream().anyMatch(stack->!stack.isEmpty())),"Fixture has actual stored items");
   h.assertTrue(BuildingTiers.level(l,e,current)==old,type+" starts at working level "+old);
   var survey=BuildingTiers.survey(l,e,current);var target=BuildingPlacement.layout(type+"@"+(old+1),origin,0);
   h.assertTrue(survey.ok(),type+" survey "+old+": "+survey.reason()+survey.conflicts().stream().limit(8).map(p->p.subtract(origin)+" "+l.getBlockState(p)+" -> "+target.get(p)).toList());int stepNumber=0;
   for(var raw:survey.state().getList("ops",Tag.TAG_COMPOUND)){
    var step=HallConstructionPlan.step((CompoundTag)raw);l.setBlock(step.pos(),step.after(),3);stepNumber++;
    h.assertTrue(BuildingTiers.level(l,e,current)==old,type+" old working level during "+old+"->"+(old+1)+" op="+stepNumber+" cell="+step.pos().subtract(origin));
    for(var inv:inventories.entrySet()){
     h.assertTrue(l.getBlockEntity(inv.getKey())==inv.getValue().inventory(),type+" inventory identity at "+inv.getKey().subtract(origin)+" op="+stepNumber);
     for(int slot=0;slot<inv.getValue().slots().size();slot++)h.assertTrue(ItemStack.matches(inv.getValue().slots().get(slot),inv.getValue().inventory().getItem(slot)),type+" stored items at "+inv.getKey().subtract(origin)+" slot="+slot+" op="+stepNumber);
    }
   }
   h.assertTrue(BuildingOrders.complete(l,e,survey.state()),type+" completed plan");
  }}finally{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ResearchKnobs.forget(s.id());BuildingLevels.forgetBest(s.id());}h.succeed();
 }
}
