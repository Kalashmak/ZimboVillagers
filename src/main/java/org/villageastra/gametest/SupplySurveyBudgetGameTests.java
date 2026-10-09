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
 public static void deferredChunkKeepsColumnUntilNextAvailableSurveyWindow(GameTestHelper h){
  var l=h.getLevel();var base=new BlockPos(98319,90,98319);var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new net.minecraft.world.item.ItemStack(Items.BREAD,16));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:sand",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+2,91,base.getZ()+4);var goal=new NaturalSupplyGoal(npc,true);
  // The hall sits across a chunk boundary. Its terrain generation can asynchronously
  // promote that immediate neighbour; select an unloaded column beyond its generation halo.
  var columns=new ArrayList<BlockPos>();
  for(int x=-NaturalSupplyGoal.SEARCH_RADIUS;x<=NaturalSupplyGoal.SEARCH_RADIUS;x++)for(int z=-NaturalSupplyGoal.SEARCH_RADIUS;z<=NaturalSupplyGoal.SEARCH_RADIUS;z++)columns.add(new BlockPos(x,0,z));
  columns.sort(Comparator.comparingDouble(pos->pos.distSqr(BlockPos.ZERO)));
  int selected=-1;for(int i=0;i<columns.size();i++){var offset=columns.get(i);if(Math.max(Math.abs(offset.getX()),Math.abs(offset.getZ()))>=160&&!l.hasChunkAt(base.offset(offset))){selected=i;break;}}
  h.assertTrue(selected>=0,"Fixture has a genuinely unloaded distant survey column");
  final int original=selected;final var deferredColumn=base.offset(columns.get(original));
  var seed=new CompoundTag();seed.putInt("surveyCursor",original);org.villageastra.persistence.NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),seed);
  h.assertTrue(!l.hasChunkAt(deferredColumn),"Survey begins at the genuinely unloaded selected column");
  TouchLoad.exhaust(l.getServer());h.assertTrue(!goal.canUse(),"An exhausted shared load budget must defer the scan");
  com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SURVEY_DEFERRAL cursor={} chunkLoaded={} touchStats={}",NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyCursor"),l.hasChunkAt(deferredColumn),TouchLoad.stats());
  h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyCursor")==original&&!l.hasChunkAt(deferredColumn),"Deferral skips no column and loads nothing beyond the budget");
  // Controlled NPC clock and renewed shared quota: isolate retry scheduling from physical walking.
  for(int tick=1;tick<20;tick++){npc.tickCount=tick;TouchLoad.resetTick();goal.canUse();}
  var scan=NaturalSupplyGoal.inspect(l,npc.getUUID());
  h.assertTrue(Math.floorMod(scan.getInt("surveyCursor")-original,columns.size())<=1024,"Retries share the column and wall-time allowances, never reset the 1024-column quota");
  npc.tickCount=20;TouchLoad.resetTick();goal.canUse();
  // A bounded elapsed window may yield before its first block under shared server load.
  h.startSequence().thenWaitUntil(()->{
   npc.tickCount+=20;TouchLoad.resetTick();goal.canUse();var current=NaturalSupplyGoal.inspect(l,npc.getUUID());
   h.assertTrue(Math.floorMod(current.getInt("surveyCursor")-original,columns.size())<=1024,"Renewed window retains its bounded column allowance");
   h.assertTrue(chest.countItem(Items.SAND)==0,"Survey scheduling never creates harvested goods");
   if(!l.hasChunkAt(deferredColumn))h.assertTrue(current.getInt("surveyCursor")==original,"Every elapsed deferral preserves the original unloaded column");
   h.assertTrue(l.hasChunkAt(deferredColumn),"Renewed bounded windows eventually load the original deferred column without skipping it");
   h.assertTrue(current.getInt("surveyCursor")!=original||current.contains("surveyY"),"Same loaded column eventually completes or saves exact unexamined depth");
  }).thenExecute(()->{HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}).thenSucceed();
 }
}
