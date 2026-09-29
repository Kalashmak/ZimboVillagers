package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** ISO-005/ISO-006: the estimate on the map is the builder's own plan — same operations, same cost, with the settlement stock counted against it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PlanGameTests {
 private record Fixture(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos site){}
 private static Fixture fixture(GameTestHelper h){
  var l=h.getLevel();var site=h.absolutePos(new BlockPos(4,3,4));var center=h.absolutePos(new BlockPos(40,3,30));
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++){
   for(int y=-3;y<0;y++)l.setBlock(site.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(site.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=20;y++)l.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
  }
  return new Fixture(l,s,e,site);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void estimateOnTheMapMatchesTheBuildersOwnPlan(GameTestHelper h){
  var f=fixture(h);
  var before=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
  for(int x=-1;x<16;x++)for(int z=-1;z<16;z++)for(int y=-3;y<=8;y++){var p=f.site.offset(x,y,z);before.put(p,f.l.getBlockState(p));}
  var estimate=Plans.estimate(f.l,f.e,"home",0,f.site);
  h.assertTrue(estimate.getBoolean("ok"),"A flat site plans without conflicts: "+estimate.getString("reason"));
  var survey=BuildingOrders.survey(f.l,f.e,"home",0,f.site);
  h.assertTrue(estimate.getInt("operations")==survey.state().getList("ops",Tag.TAG_COMPOUND).size(),"The estimate counts the builder's own operations");
  int items=0;for(var key:survey.state().getCompound("cost").getAllKeys())items+=survey.state().getCompound("cost").getInt(key);
  h.assertTrue(estimate.getInt("items")==items&&items>0,"Item total comes from the same cost: "+estimate.getInt("items")+" vs "+items);
  h.assertTrue(estimate.getInt("cells")>0&&estimate.getInt("cells")<=estimate.getInt("operations"),"Unique cells and operations are different numbers: "+estimate.getInt("cells")+"/"+estimate.getInt("operations"));
  h.assertTrue(estimate.getInt("place")>0&&estimate.getInt("temporary")>0&&estimate.getInt("clear")>=0,"Building, temporary and clearing work are shown apart: "+estimate.getInt("place")+"/"+estimate.getInt("temporary")+"/"+estimate.getInt("clear"));
  for(var p:before.keySet())h.assertTrue(f.l.getBlockState(p).equals(before.get(p)),"Planning changes no block at "+p.toShortString());
  h.assertTrue(!HallUpgradeGoal.exists(f.l,f.s.id()),"Planning queues nothing");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void estimateSeparatesStockFromShortage(GameTestHelper h){
  var f=fixture(h);
  var empty=Plans.estimate(f.l,f.e,"home",0,f.site);
  h.assertTrue(empty.getInt("shortage")==empty.getInt("items")&&!Plans.shortages(empty).isEmpty(),"Without stock everything is missing: "+empty.getInt("shortage")+"/"+empty.getInt("items"));
  var chest=LogisticsRoutes.chest(f.l,f.e,Workshops.hall(f.e));
  chest.setItem(0,new ItemStack(Items.COBBLESTONE,64));
  var stocked=Plans.estimate(f.l,f.e,"home",0,f.site);
  int free=0,need=0;for(var raw:stocked.getList("materials",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;if(row.getString("item").equals("minecraft:cobblestone")){free=row.getInt("free");need=row.getInt("need");}}
  h.assertTrue(need>0&&free==Math.min(64,need),"Free is what the hall really holds, never more than the plan needs: "+free+"/"+need);
  h.assertTrue(stocked.getInt("shortage")==empty.getInt("shortage")-free,"Shortage drops by exactly the stock: "+stocked.getInt("shortage")+" vs "+empty.getInt("shortage"));
  h.assertTrue(!Plans.shortages(stocked).containsKey("minecraft:cobblestone")||Plans.shortages(stocked).get("minecraft:cobblestone")>0,"Shortages list only what is really short");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void estimateShowsConflictsAndWithheldDesigns(GameTestHelper h){
  var f=fixture(h);
  f.l.setBlock(f.site.offset(2,1,2),Blocks.WATER.defaultBlockState(),2);f.l.setBlock(f.site.offset(4,2,4),Blocks.DIRT.defaultBlockState(),2);
  var blocked=Plans.estimate(f.l,f.e,"home",0,f.site);
  h.assertTrue(!blocked.getBoolean("ok")&&blocked.getString("reason").equals("conflicts")&&blocked.getInt("conflictCount")>=2,"Water and terrain are shown as conflicts: "+blocked.getInt("conflictCount"));
  h.assertTrue(blocked.getLongArray("conflicts").length>0&&blocked.getLongArray("conflicts").length<=Plans.MAX_CONFLICTS,"Conflict cells reach the map, capped: "+blocked.getLongArray("conflicts").length);
  // AD-084: the two-storey house is offered again — a live builder really finishes it — and the palette says so in its own plan.
  var storeyed=Plans.estimate(f.l,f.e,"home_2",0,f.site.offset(0,0,0));
  h.assertTrue(storeyed.getBoolean("offered")&&storeyed.getInt("operations")>0,"The two-storey house plans and is offered: "+storeyed.getInt("operations"));
  h.assertTrue(org.villageastra.world.BuildingOrders.WITHHELD.isEmpty(),"Nothing is withheld from the palette now");
  var footprint=Plans.footprint("home",f.site);
  h.assertTrue(footprint.length>0&&Arrays.stream(footprint).anyMatch(v->BlockPos.of(v).equals(f.site)),"The ghost outline starts at the marked cell");
  h.succeed();
 }

 /** ISO-003: the plan says what happens to every cell — kept, taken away, put up, or put up where something already stands. */
 @GameTest(template="empty",timeoutTicks=200) public static void theEstimateTellsApartWhatIsKeptPlacedAndReplaced(GameTestHelper h){
  var f=fixture(h);
  var plan=Plans.estimate(f.l,f.e,"home",0,f.site);
  h.assertTrue(plan.getBoolean("ok"),"The site plans: "+plan.getString("reason"));
  h.assertTrue(plan.getInt("place")>0&&plan.getInt("replace")>0,"A house on the ground both puts up and replaces: place="+plan.getInt("place")+" replace="+plan.getInt("replace"));
  h.assertTrue(plan.getLongArray("replaceCells").length==plan.getInt("replace"),"Every replaced cell reaches the map: "+plan.getLongArray("replaceCells").length);
  var future=new java.util.HashSet<Long>();var cells=plan.getIntArray("future");var origin=BlockPos.of(plan.getLong("origin"));
  for(int i=0;i+3<cells.length;i+=4)future.add(origin.offset(cells[i],cells[i+1],cells[i+2]).asLong());
  for(long raw:plan.getLongArray("replaceCells")){
   h.assertTrue(future.contains(raw),"A replaced cell is part of the future volume: "+BlockPos.of(raw).toShortString());
   h.assertTrue(!f.l.getBlockState(BlockPos.of(raw)).isAir(),"A replaced cell really has a block of its own now: "+BlockPos.of(raw).toShortString());
  }
  long onAir=future.stream().filter(raw->f.l.getBlockState(BlockPos.of(raw)).isAir()).count();
  h.assertTrue(onAir>0&&plan.getInt("replace")+onAir<=plan.getInt("place"),
    "What goes up on empty air is not called a replacement: air="+onAir+" replace="+plan.getInt("replace")+" place="+plan.getInt("place"));
  // A cell that already holds exactly what the design wants is kept, not touched.
  int kept=plan.getInt("keep");
  for(var cell:BuildingPlacement.layout("home",f.site,0).entrySet()){
   if(cell.getValue().isAir()||f.l.getBlockState(cell.getKey()).equals(cell.getValue()))continue;
   f.l.setBlock(cell.getKey(),cell.getValue(),3);break;
  }
  var again=Plans.estimate(f.l,f.e,"home",0,f.site);
  h.assertTrue(again.getInt("keep")>kept,"A cell that already stands as the design wants it is kept: "+again.getInt("keep")+" was "+kept);
  h.succeed();
 }
}
