package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-101: villages of their own mayors develop alongside the player's — they choose their studies and raise their buildings from their
 *  own stock, exactly by the rules a player's village follows; a village with a player mayor decides nothing by itself. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class NpcDevelopmentGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void aVillageOfItsOwnMayorStudiesAndRaisesItsBuildings(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  try{
   h.assertTrue(s.governance().playerMayor()==null,"The village has its own mayor");
   HallUpgradeGoal.drop(l,s.id());
   var result=MayorPlanner.develop(l,e);
   var selected=BookResearch.inspect(l,e).getString("selected");
   // AD-136 (CF3): it first pays a level-I node whose price lies in the stock, otherwise it gives the laboratory a level II-VI target.
   String node=result.startsWith("research:")?result.substring(9):"";
   h.assertTrue(!node.isEmpty()&&(org.villageastra.domain.ResearchCatalog.get(node).tier()==1?BookResearch.completed(e,BookResearch.inspect(l,e)).contains(node):selected.equals(node)),"It chooses its next study by itself: "+result+" target "+selected);
   // The whole cost of the farm's second level lies in the hall and its research is done: the mayor orders it.
   var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();
   // The land of level II is open ground, as the village leaves it (as FarmLevelGameTests prepares it): the new modules reach west past the
   // test's cleared plot, where the GameTest world is solid deepslate — whether the field survey met rock depended on the test's slot.
   for(var m:FarmField.added(2,s.westField(farm.id()),FarmField.legacy(s)))for(var c:FarmField.localColumns(List.of(m))){var g=BuildingPlacement.at(e,farm,c.getX(),0,c.getZ());
    l.setBlock(g.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);l.setBlock(g,net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=1;y<=FarmField.HEADROOM+8;y++)l.setBlock(g.above(y),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
   var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);
   for(var id:BuildingTiers.research("farm",2))done.add(StringTag.valueOf(id));record.put("legacyDone",done);BookResearch.store(l,e,record);
   var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));int slot=0;
   var cost=BuildingTiers.survey(l,e,farm).state().getCompound("cost");
   for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=cost.getInt(key);
    while(left>0){while(slot<hall.getContainerSize()&&!hall.getItem(slot).isEmpty())slot++;if(slot>=hall.getContainerSize())break;
     int n=Math.min(item.getMaxStackSize(),left);hall.setItem(slot,new ItemStack(item,n));left-=n;}}
   HallUpgradeGoal.drop(l,s.id());
   // AD-136 (owner answer 2): no building above the hall - the hall stands at II for the farm's II.
   if(s.civilization().level()<2)s.civilization().completedHallUpgrade(2);
   var raised=MayorPlanner.develop(l,e);
   h.assertTrue(raised.startsWith("level:")&&HallUpgradeGoal.pending(l,s.id())&&HallUpgradeGoal.inspect(l,s.id()).getInt("upgradeLevel")==2,
     "It orders the next level of a building it can pay for: "+raised+" (farm II field: "+FarmField.plan(l,e,farm,2).reason()+")");
   HallUpgradeGoal.drop(l,s.id());
   // A village with a player mayor waits for the player.
   s.appointPlayerMayor(UUID.randomUUID());
   var before=BookResearch.inspect(l,e).getString("selected");
   h.assertTrue(MayorPlanner.develop(l,e).isEmpty()&&!HallUpgradeGoal.pending(l,s.id())&&BookResearch.inspect(l,e).getString("selected").equals(before),
     "A player's village decides nothing by itself");
  }finally{HallUpgradeGoal.drop(l,s.id());}
  h.succeed();
 }
}

