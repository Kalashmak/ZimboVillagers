package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SupplySurveyBudgetGameTests {
 @GameTest(template="empty",batch="supply_survey_budget",timeoutTicks=100)
 public static void deferredChunkResumesNextTickWithoutRestartingTheSurveyAllowance(GameTestHelper h){
  var l=h.getLevel();var base=new BlockPos(98319,90,98319);var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new net.minecraft.world.item.ItemStack(Items.BREAD,16));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:sand",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+2,91,base.getZ()+4);var goal=new NaturalSupplyGoal(npc,true);
  h.assertTrue(!l.hasChunkAt(base),"Survey begins in a genuinely unloaded adjacent chunk");
  TouchLoad.exhaust(l.getServer());h.assertTrue(!goal.canUse(),"An exhausted shared load budget must defer the scan");
  h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyCursor")==0&&!l.hasChunkAt(base),"Deferral skips no column and loads nothing beyond the budget");
  // Controlled NPC clock and renewed shared quota: isolate retry scheduling from physical walking.
  npc.tickCount=1;TouchLoad.resetTick();goal.canUse();
  h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyCursor")>0,"Retry must use the renewed next-tick quota, not sleep another 20 ticks");
  for(int tick=2;tick<20;tick++){npc.tickCount=tick;TouchLoad.resetTick();goal.canUse();}
  h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyCursor")==1024,"Retries share one allowance: at most 1024 columns per 20 NPC ticks");
  h.assertTrue(chest.countItem(Items.SAND)==0,"Survey scheduling cannot create harvested goods");HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());h.succeed();
 }
}
