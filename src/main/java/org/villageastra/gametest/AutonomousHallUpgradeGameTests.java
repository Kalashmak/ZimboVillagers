package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-464: an NPC mayor must use the paid early-hall survey instead of the generic hall_office refusal. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AutonomousHallUpgradeGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building lab,List<net.minecraft.world.level.ChunkPos> held){
  void close(){HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());BookResearch.clearCache();MayorPlanner.clear();PhysicalFixtureChunks.release(l,held);}
 }
 private static Town town(GameTestHelper h){return town(h,false);}
 private static Town town(GameTestHelper h,boolean castle){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var center=new BlockPos(at.getX()+1507328,130,at.getZ());int west=castle?-20:-3,north=castle?-28:-3,height=castle?40:18;var held=PhysicalFixtureChunks.force(l,center,west,78,north,32);for(var c:held)l.getChunk(c.x,c.z);
  for(int x=west;x<=78;x++)for(int z=north;z<=32;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<height;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());if(castle)s.lotLayout(OrganicLots.CASTLE_LOTS);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",castle?OrganicLots.CASTLE_X:0,0,castle?OrganicLots.CASTLE_Z:0);var lab=new Settlement.Building(UUID.randomUUID(),"laboratory",50,0,0);s.addBuilding(hall);s.addBuilding(lab);var home=new Settlement.Home(UUID.randomUUID(),1,4,true);s.addHome(home);
  s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,Profession.MAYOR,null,-1),home.id());var scientist=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(scientist,home.id());s.assign(scientist.id(),Profession.SCIENTIST,lab.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);ResearchV2Town.lay(l,e,hall,BuildingTiers.layoutId(s,"town_hall",1));ResearchV2Town.lay(l,e,lab,"laboratory");if(castle)HallStorage.ensure(l,e);BookResearch.clearCache();return new Town(l,e,s,lab,held);
 }
 private static void fill(net.minecraft.world.Container chest,Map<String,Integer> cost){
  chest.clearContent();int slot=0;for(var line:cost.entrySet()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(line.getKey()));int n=line.getValue();while(n>0){int put=Math.min(n,item.getMaxStackSize());chest.setItem(slot++,new ItemStack(item,put));n-=put;}}
 }
 private static void payTierOne(GameTestHelper h,Town t){
  var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));h.assertTrue(chest!=null,"Real hall stock exists");
  for(var node:ResearchCatalog.NODES.values())if(node.resourcePaid()){
   h.assertTrue(BookResearch.reason(t.e,BookResearch.inspect(t.l,t.e),node.id()).equals("available"),"Tier-I prerequisites open: "+node.id());var bill=new LinkedHashMap<String,Integer>();
   for(var c:node.resources()){var item=c.tag()==null?BuiltInRegistries.ITEM.get(new ResourceLocation(c.item())):BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,new ResourceLocation(c.tag()))).iterator().next().value();bill.merge(BuiltInRegistries.ITEM.getKey(item).toString(),c.count(),Integer::sum);}
   fill(chest,bill);h.assertTrue(BookResearch.payResources(t.l,t.e,node.id()),"Actual journal-backed resource payment: "+node.id());
  }
  chest.clearContent();h.assertTrue(BookResearch.inspect(t.l,t.e).getList("legacyDone",Tag.TAG_STRING).isEmpty(),"Primary proof has no granted legacy research");
 }
 private static void payHallTwo(GameTestHelper h,Town t){
  payTierOne(h,t);h.assertTrue(BookResearch.autoSelect(t.l,t.e).equals("town_hall.2"),"Actual NPC auto-selection opens only the next hall research at civilization I");
  int works=ResearchCatalog.get("town_hall.2").works();h.assertTrue(works==7&&ScienceWorks.advance(t.l,t.e,0)==0,"Actual scientist starts without free books");
  h.assertTrue(ScienceWorks.advance(t.l,t.e,ScienceBalance.WORK_TICKS*works)==works,"Seven complete work periods deposit seven journal-backed scientific works");
  for(int i=0;i<works;i++)h.assertTrue(BookResearch.consume(t.l,t.e,t.lab),"Actual laboratory spends its produced scientific work "+i);
  h.assertTrue(ResearchGate.has(t.l,t.e,"town_hall.2")&&t.s.civilization().level()==1&&LogisticsRoutes.chest(t.l,t.e,t.lab).countItem(VillageAstra.RESEARCH_VOLUME.get())==0,"Paid knowledge consumes all books without changing civilization");
 }
 @GameTest(template="empty",batch="autonomous_hall_upgrade_baseline",timeoutTicks=200)
 public static void paidHallTwoResearchCreatesAnExactAutonomousProject(GameTestHelper h){
  var t=town(h);try{payHallTwo(h,t);var quote=HallUpgradeGoal.preview(t.l,t.e);var cost=new LinkedHashMap<String,Integer>();quote.getCompound("cost").getAllKeys().forEach(k->cost.put(k,quote.getCompound("cost").getInt(k)));fill(LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)),cost);
   h.assertTrue(MayorPlanner.affordable(t.l,t.e,cost)&&!HallUpgradeGoal.pending(t.l,t.s.id()),"Exact surveyed bill is present and project slot is idle");MayorPlanner.develop(t.l,t.e);
   h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id()),"Paid next-hall research must create the autonomous physical hall-II project");var actual=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(actual.getInt("level")==2&&actual.get("ops").equals(quote.get("ops"))&&actual.get("cost").equals(quote.get("cost")),"Autonomous project uses the exact pure preview operations and cost");
   h.assertTrue(t.s.civilization().level()==1&&!actual.getBoolean("funded")&&actual.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Planning grants no civilization, funding or cargo");
  }finally{t.close();}h.succeed();
 }
 private static Map<String,Integer> bill(CompoundTag quote){var out=new LinkedHashMap<String,Integer>();quote.getCompound("cost").getAllKeys().forEach(k->out.put(k,quote.getCompound("cost").getInt(k)));return out;}
 private static void stocked(Town t){fill(LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)),bill(HallUpgradeGoal.preview(t.l,t.e)));}
 private static List<ItemStack> inventory(Town t){var c=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));var out=new ArrayList<ItemStack>();for(int i=0;i<c.getContainerSize();i++)out.add(c.getItem(i).copy());return out;}
 private static boolean sameInventory(Town t,List<ItemStack> before){var c=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));for(int i=0;i<c.getContainerSize();i++)if(!ItemStack.matches(before.get(i),c.getItem(i)))return false;return true;}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void noPaidHallResearchDoesNotCreateAnUpgrade(GameTestHelper h){var t=town(h);try{payTierOne(h,t);stocked(t);MayorPlanner.develop(t.l,t.e);h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id())&&!ResearchGate.has(t.l,t.e,"town_hall.2"),"A selected but unpaid hall research cannot queue physical work");}finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void aPlayerMayorRetainsEarlyHallAuthority(GameTestHelper h){var t=town(h);try{payHallTwo(h,t);stocked(t);t.s.appointPlayerMayor(UUID.randomUUID());var before=inventory(t);MayorPlanner.develop(t.l,t.e);h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id())&&sameInventory(t,before),"Player authority refuses autonomous hall orders without inventory changes");}finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void aQueuedProjectAndItsCommittedCargoRemainIntact(GameTestHelper h){var t=town(h);try{payHallTwo(h,t);stocked(t);var quote=HallUpgradeGoal.preview(t.l,t.e);var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE,2).save(new CompoundTag()));quote.put("cargo",cargo);HallUpgradeGoal.enqueue(t.l,t.e,quote);var before=HallUpgradeGoal.inspect(t.l,t.s.id());var items=inventory(t);MayorPlanner.develop(t.l,t.e);h.assertTrue(HallUpgradeGoal.inspect(t.l,t.s.id()).equals(before)&&sameInventory(t,items),"An existing project's identity, operations and entrusted cargo are retained");}finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void aChangedUpgradeVolumeIsNotOverwritten(GameTestHelper h){var t=town(h);try{payHallTwo(h,t);stocked(t);var quote=HallUpgradeGoal.preview(t.l,t.e);var op=quote.getList("ops",Tag.TAG_COMPOUND).getCompound(0);var at=BlockPos.of(op.getLong("pos"));t.l.setBlock(at,Blocks.BEDROCK.defaultBlockState(),2);var before=inventory(t);MayorPlanner.develop(t.l,t.e);h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id())&&t.l.getBlockState(at).is(Blocks.BEDROCK)&&sameInventory(t,before),"Changed terrain refuses the pure survey with no project or block replacement");}finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void anUnloadedHallVolumeIsNotLoadedByThePlanner(GameTestHelper h){var t=town(h);try{payHallTwo(h,t);var center=t.e.center().offset(262144,0,262144);var e=new SettlementData.Entry(t.s,t.e.dimension(),center);var plaque=BuildingSigns.position(e,Workshops.hall(e));h.assertTrue(plaque!=null&&!t.l.hasChunkAt(center)&&!t.l.hasChunkAt(plaque),"Both hypothetical hall center and plaque are genuinely unloaded");MayorPlanner.develop(t.l,e);h.assertTrue(!t.l.hasChunkAt(center)&&!t.l.hasChunkAt(plaque)&&!HallUpgradeGoal.pending(t.l,t.s.id()),"Neither center nor plaque chunk is loaded or ordered by the planner");}finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void anUnproducibleExactBillCannotBePromised(GameTestHelper h){var t=town(h);try{payHallTwo(h,t);var cost=bill(HallUpgradeGoal.preview(t.l,t.e));var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));chest.clearContent();h.assertTrue(cost.keySet().stream().anyMatch(k->!Workshops.producible(t.l,t.e,k))&&!MayorPlanner.affordable(t.l,t.e,cost),"At least one exact quoted material has no stock or village production chain");MayorPlanner.develop(t.l,t.e);h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id())&&chest.isEmpty(),"No unfundable hall quote or free materials appear");}finally{t.close();}h.succeed();}
 private static long payWorks(GameTestHelper h,Town t,String node,long now){
  var state=BookResearch.inspect(t.l,t.e);h.assertTrue(BookResearch.reason(t.e,state,node).equals("available"),"Real dependencies open "+node);state.putString("selected",node);BookResearch.store(t.l,t.e,state);int n=ResearchCatalog.get(node).works();long next=now+n*ScienceBalance.WORK_TICKS;
  h.assertTrue(ScienceWorks.advance(t.l,t.e,next)==n,"Full scientist work periods produce exactly "+n+" books for "+node);for(int i=0;i<n;i++)h.assertTrue(BookResearch.consume(t.l,t.e,t.lab),"Journal-backed book payment "+node+" #"+i);h.assertTrue(ResearchGate.has(t.l,t.e,node),"Actual paid research recorded: "+node);return next;
 }
 @GameTest(template="empty",batch="autonomous_hall_upgrade_guards",timeoutTicks=200)
 public static void hallThreeWaitsForScienceTwoAndThenUsesItsExactSurvey(GameTestHelper h){
  var t=town(h);try{payHallTwo(h,t);
   // Independent initial tier-II fixture; this does not claim to execute construction labor.
   t.s.civilization().completedHallUpgrade(2);var hall=Workshops.hall(t.e);ResearchV2Town.lay(t.l,t.e,hall,"town_hall_2");BuildingLevels.forgetBest(t.s.id());h.assertTrue(BuildingTiers.level(t.l,t.e,hall)==2,"The initial tier-II hall has its physical seal and shell");
   h.assertTrue(BookResearch.reason(t.e,BookResearch.inspect(t.l,t.e),"town_hall.3").equals("dependencies"),"Hall III requires genuinely paid Science II");stocked(t);MayorPlanner.develop(t.l,t.e);h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id()),"No hall III project precedes its research");
   long now=7*ScienceBalance.WORK_TICKS;now=payWorks(h,t,"research.2",now);payWorks(h,t,"town_hall.3",now);var quote=HallUpgradeGoal.preview(t.l,t.e);stocked(t);var before=inventory(t);MayorPlanner.develop(t.l,t.e);
   var actual=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id())&&actual.getInt("level")==3&&actual.get("cost").equals(quote.get("cost"))&&actual.get("ops").equals(quote.get("ops")),"Genuinely paid Science II and Hall III allow only the exact next survey");h.assertTrue(t.s.civilization().level()==2&&sameInventory(t,before)&&!actual.getBoolean("funded"),"The order changes neither civilization nor inventory nor funding");
  }finally{t.close();}h.succeed();
 }

 @GameTest(template="empty",batch="autonomous_hall_upgrade_castle",timeoutTicks=200)
 public static void thePaidCastleHallTwoUsesItsFullExactBill(GameTestHelper h){
  var t=town(h,true);try{payHallTwo(h,t);h.assertTrue(HallSite.castle(t.s)&&HallSite.stock(t.e).equals(HallSite.castleStock(t.e.center())),"Actual castle stock and lot policy");var quote=HallUpgradeGoal.preview(t.l,t.e);stocked(t);var cost=bill(quote);var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
   h.assertTrue(chest.getContainerSize()==108&&cost.entrySet().stream().allMatch(en->LogisticsRoutes.count(chest,stack->stack.is(BuiltInRegistries.ITEM.get(new ResourceLocation(en.getKey()))))==en.getValue()),"Every line of the exact castle quote is physically held in the real hall store");var before=inventory(t);MayorPlanner.develop(t.l,t.e);var actual=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id())&&actual.getInt("level")==2&&actual.get("ops").equals(quote.get("ops"))&&actual.get("cost").equals(quote.get("cost")),"NPC castle I-to-II uses its surveyed exact geometry and full price");h.assertTrue(t.s.civilization().level()==1&&sameInventory(t,before)&&!actual.getBoolean("funded"),"Planning performs no construction, payment or civilization upgrade");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_AUTONOMOUS_CASTLE from=1 to=2 ops={} materials={} fullbill=true paidworks=7",quote.getList("ops",Tag.TAG_COMPOUND).size(),BuildingTiers.count(cost));
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="autonomous_hall_upgrade_castle",timeoutTicks=200)
 public static void thePaidCastleHallThreeStillRequiresItsPhysicalSeal(GameTestHelper h){
  var t=town(h,true);try{payHallTwo(h,t);t.s.civilization().completedHallUpgrade(2);var hall=Workshops.hall(t.e);ResearchV2Town.lay(t.l,t.e,hall,BuildingTiers.layoutId(t.s,"town_hall",2));BuildingLevels.forgetBest(t.s.id());h.assertTrue(BuildingTiers.level(t.l,t.e,hall)==2,"Independent initial castle-II kit and seal actually stand");long now=7*ScienceBalance.WORK_TICKS;now=payWorks(h,t,"research.2",now);payWorks(h,t,"town_hall.3",now);
   var quote=HallUpgradeGoal.preview(t.l,t.e);stocked(t);var cell=BuildingTiers.coreCell(t.l,t.e,hall);var at=BuildingPlacement.at(t.e,hall,cell.getX(),cell.getY(),cell.getZ());var seal=t.l.getBlockState(at);h.assertTrue(Cores.isCoreOf(seal,"town_hall")&&Cores.grade(seal)==2,"Initial paid tier-II castle core has grade II");
   // All science is already paid; a legitimate generic lab quote is allowed, but the missing castle seal must refuse the hall.
   t.l.setBlock(at,Blocks.AIR.defaultBlockState(),2);var before=inventory(t);MayorPlanner.develop(t.l,t.e);h.assertTrue((!HallUpgradeGoal.pending(t.l,t.s.id())||BuildingOrders.isBuilding(HallUpgradeGoal.inspect(t.l,t.s.id())))&&sameInventory(t,before)&&t.l.getBlockState(at).isAir(),"Paid research and a full bill cannot bypass the missing physical castle seal");if(HallUpgradeGoal.pending(t.l,t.s.id())){var other=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(!other.getBoolean("funded")&&other.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Only an unstarted unrelated fixture quote can be cleaned up");HallUpgradeGoal.drop(t.l,t.s.id());}
   t.l.setBlock(at,seal,2);MayorPlanner.develop(t.l,t.e);var actual=HallUpgradeGoal.inspect(t.l,t.s.id());h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id())&&actual.getInt("level")==3&&actual.get("ops").equals(quote.get("ops"))&&actual.get("cost").equals(quote.get("cost")),"Restored existing seal allows the exact paid castle-II-to-III project");h.assertTrue(t.s.civilization().level()==2&&sameInventory(t,before)&&!actual.getBoolean("funded"),"Castle order grants neither labor, goods nor civilization");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_AUTONOMOUS_CASTLE from=2 to=3 ops={} materials={} fullbill=true paidworks=28 coreguard=true",quote.getList("ops",Tag.TAG_COMPOUND).size(),BuildingTiers.count(bill(quote)));
  }finally{t.close();}h.succeed();
 }


}
