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
/** AD-467: strict terminal validation fixtures; initial VI layouts are not natural village progression. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TerminalAnnexGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building parent,Settlement.Building annex,Settlement.Building lab,List<net.minecraft.world.level.ChunkPos> held){
  void close(){HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());BookResearch.clearCache();PhysicalFixtureChunks.release(l,held);}
 }
 private static Town town(GameTestHelper h,String type,int turn,boolean linked){return town(h,type,turn,linked,6);}
 private static Town town(GameTestHelper h,String type,int turn,boolean linked,int parentLevel){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var center=new BlockPos(at.getX()+2097152+128*AnnexTypes.types().stream().sorted().toList().indexOf(type),160,at.getZ());var held=PhysicalFixtureChunks.force(l,center,-10,150,-10,50);for(var c:held)l.getChunk(c.x,c.z);
  var s=new Settlement(UUID.randomUUID());s.restoreCivilization(Civilization.restore(6,List.of(),"",0));var k=Annexes.kind(type);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0,0,6);var lab=new Settlement.Building(UUID.randomUUID(),"laboratory",45,0,0,0,6);var parent=new Settlement.Building(UUID.randomUUID(),k.parent(),90,0,0,turn,parentLevel);s.addBuilding(hall);s.addBuilding(lab);s.addBuilding(parent);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var o=Annexes.origin(e,parent,k).subtract(center);var annex=new Settlement.Building(UUID.randomUUID(),type,o.getX(),o.getY(),o.getZ(),turn);s.addBuilding(annex);if(linked)s.linkAnnex(annex.id(),parent.id());
  // Full initial physical layouts, including actual workshop stations; no fake chests.
  for(var b:s.buildings()){String design=AnnexTypes.annex(b.type())?b.type():BuildingTiers.layoutId(s,b.type(),b.level());for(var cell:BuildingPlacement.layout(e,b,design).entrySet()){if(cell.getKey().getY()==center.getY())l.setBlock(cell.getKey().below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(cell.getKey(),BuildingOrders.payable(cell.getValue()),2);}}
  // Repeat placement so multipart fixture blocks finish with their canonical neighbours.
  for(var b:s.buildings())for(var cell:BuildingPlacement.layout(e,b,AnnexTypes.annex(b.type())?b.type():BuildingTiers.layoutId(s,b.type(),b.level())).entrySet())l.setBlock(cell.getKey(),BuildingOrders.payable(cell.getValue()),2);
  var home=new Settlement.Home(UUID.randomUUID(),1,4,true);s.addHome(home);var scientist=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(scientist,home.id());s.assign(scientist.id(),Profession.SCIENTIST,lab.id());SettlementData.get(l.getServer()).add(e);BookResearch.clearCache();return new Town(l,e,s,parent,annex,lab,held);
 }
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void fixedAnnexValidationPreservesTheExistingDynamicStatePolicy(GameTestHelper h){var t=town(h,"masonry_annex",1,true);try{
  var furnace=BuildingPlacement.at(t.e,t.annex,3,1,5);var state=t.l.getBlockState(furnace);h.assertTrue(state.is(Blocks.FURNACE),"Actual fixed masonry furnace stands");t.l.setBlock(furnace,state.setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT,true),2);
  h.assertTrue(!TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Lit working furnace is the same permanent equipment under existing present policy");
 }finally{t.close();}h.succeed();}
 private static String key(Settlement.Building b){return b.type()+"/"+b.id();}
 private static void fill(net.minecraft.world.Container stock,Map<String,Integer> bill){stock.clearContent();int slot=0;for(var line:bill.entrySet()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(line.getKey()));int remaining=line.getValue();while(remaining>0){int n=Math.min(remaining,item.getMaxStackSize());stock.setItem(slot++,new ItemStack(item,n));remaining-=n;}}}
 private static long pay(GameTestHelper h,Town t,String id,long now){
  if(ResearchGate.has(t.l,t.e,id))return now;var node=ResearchCatalog.get(id);for(var dependency:node.requires())now=pay(h,t,dependency,now);var state=BookResearch.inspect(t.l,t.e);h.assertTrue(BookResearch.reason(t.e,state,id).equals("available"),"Actual dependencies open "+id);
  if(node.resourcePaid()){var cost=new LinkedHashMap<String,Integer>();for(var c:node.resources()){var item=c.tag()==null?BuiltInRegistries.ITEM.get(new ResourceLocation(c.item())):BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM,new ResourceLocation(c.tag()))).iterator().next().value();cost.merge(BuiltInRegistries.ITEM.getKey(item).toString(),c.count(),Integer::sum);}var stock=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));fill(stock,cost);h.assertTrue(BookResearch.payResources(t.l,t.e,id),"Actual resource payment "+id);stock.clearContent();}
  else{state.putString("selected",id);BookResearch.store(t.l,t.e,state);now+=node.works()*ScienceBalance.WORK_TICKS;h.assertTrue(ScienceWorks.advance(t.l,t.e,now)==node.works(),"Actual scientist writes all work periods for "+id);for(int i=0;i<node.works();i++)h.assertTrue(BookResearch.consume(t.l,t.e,t.lab),"Actual scientific book consumed for "+id);}
  h.assertTrue(ResearchGate.has(t.l,t.e,id),"Paid node recorded "+id);return now;
 }
 private static void paid(GameTestHelper h,Town t){h.assertTrue(ScienceWorks.advance(t.l,t.e,0)==0,"Scientist begins without granted volumes");long now=pay(h,t,Annexes.kind(t.annex.type()).research(),0);h.assertTrue(BookResearch.inspect(t.l,t.e).getList("legacyDone",Tag.TAG_STRING).isEmpty(),"No legacy research grants");h.assertTrue(LogisticsRoutes.chest(t.l,t.e,t.lab).countItem(VillageAstra.RESEARCH_VOLUME.get())==0,"All actually written volumes spent");com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_TERMINAL_ANNEX paid={} scienceClock={} civ=6 initialLayouts=true naturalMAX=false",Annexes.kind(t.annex.type()).research(),now);}
 @GameTest(template="empty",batch="terminal_annex_baseline",timeoutTicks=200)
 public static void destroyedRegisteredRequiredAnnexCannotPassTerminalAcceptance(GameTestHelper h){
  var t=town(h,"carpentry_annex",1,true);try{paid(h,t);h.assertTrue(t.s.civilization().level()==6&&BuildingLevels.level(t.l,t.e,t.parent)==6&&TerminalProgress.missing(t.l,t.e,t.parent)==0,"Real required parent stands at terminal VI");h.assertTrue(TerminalProgress.blockers(t.l,t.e).isEmpty(),"Complete initial hall, lab, parent and fixed annex pass geometry");
   var chest=LogisticsRoutes.position(t.e,t.annex);h.assertTrue(t.l.getBlockEntity(chest) instanceof OwnedChestEntity,"Actual blueprint stock exists");t.l.setBlock(chest,Blocks.AIR.defaultBlockState(),2);var station=BuildingPlacement.at(t.e,t.annex,3,1,4);t.l.setBlock(station,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(t.s.annexParent(t.annex.id()).equals(t.parent.id())&&CoreCatalog.canonical(AnnexTypes.workplace(t.annex.type())).equals("carpentry"),"Destroyed required workshop still counts in the registry presence predicate");h.assertTrue(TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Destroyed registered required annex must be named in terminal blockers");
  }finally{t.close();}h.succeed();
 }
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void allFiveFixedAnnexesNeedTheirRealStructureAndEquipment(GameTestHelper h){
  for(var type:AnnexTypes.types().stream().sorted().toList()){var t=town(h,type,2,true);try{h.assertTrue(!BuildingTiers.upgradable(type)&&t.annex.level()==1&&!TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Intact fixed annex works without an invented VI tier: "+type);var layout=BuildingPlacement.layout(t.e,t.annex,type);var roof=layout.entrySet().stream().filter(c->c.getKey().getY()>t.e.center().getY()+1&&!c.getValue().isAir()).max(Comparator.comparingInt(c->c.getKey().getY())).orElseThrow();var before=t.l.getBlockState(roof.getKey());t.l.setBlock(roof.getKey(),Blocks.AIR.defaultBlockState(),2);h.assertTrue(TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Missing actual fixed structural cell blocks "+type);t.l.setBlock(roof.getKey(),before,2);var stock=LogisticsRoutes.position(t.e,t.annex);t.l.setBlock(stock,Blocks.AIR.defaultBlockState(),2);h.assertTrue(TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Missing actual equipment stock blocks "+type);}finally{t.close();}}h.succeed();
 }
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void aParentWhoseWorkingEquipmentIsMissingCannotSupportAcceptance(GameTestHelper h){var t=town(h,"carpentry_annex",0,true);try{var cell=BuildingTiers.coreCell(t.l,t.e,t.parent);var at=BuildingPlacement.at(t.e,t.parent,cell.getX(),cell.getY(),cell.getZ());h.assertTrue(BuildingLevels.level(t.l,t.e,t.parent)==6,"Parent initially works at VI");t.l.setBlock(at,Blocks.AIR.defaultBlockState(),2);h.assertTrue(BuildingLevels.level(t.l,t.e,t.parent)<Annexes.kind(t.annex.type()).parentLevel()&&TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Kept VI does not bypass damaged physical parent");}finally{t.close();}var u=town(h,"mill_annex",0,true,1);try{h.assertTrue(TerminalProgress.blockers(u.l,u.e).get(key(u.annex)).equals("annex_parent_level"),"Real parent kept below the canonical construction requirement is rejected");}finally{u.close();}h.succeed();}
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void aFixedAnnexMustRetainItsActualParentSiteAndTurn(GameTestHelper h){var t=town(h,"mill_annex",1,true);try{h.assertTrue(!TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Correct rotated parent site accepted");h.assertTrue(t.s.moveBuilding(t.annex.id(),t.annex.x()+1,t.annex.y(),t.annex.z(),t.annex.rotation()),"Fixture shifts registered site");h.assertTrue(TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Wrong linked site refused");t.s.moveBuilding(t.annex.id(),t.annex.x(),t.annex.y(),t.annex.z(),(t.annex.rotation()+1)%4);h.assertTrue(TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Wrong linked turn refused");}finally{t.close();}var u=town(h,"masonry_annex",0,false);try{h.assertTrue(TerminalProgress.blockers(u.l,u.e).containsKey(key(u.annex)),"Physical annex without parent link refused");u.s.linkAnnex(u.annex.id(),u.lab.id());h.assertTrue(TerminalProgress.blockers(u.l,u.e).get(key(u.annex)).equals("annex_parent"),"Wrong parent type cannot support the actual annex");}finally{u.close();}h.succeed();}
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void terminalAnnexInspectionDoesNotLoadChunksOrMutatePaidState(GameTestHelper h){var t=town(h,"carpentry_annex",3,true);try{paid(h,t);var state=BookResearch.inspect(t.l,t.e);var chest=LogisticsRoutes.chest(t.l,t.e,t.annex);chest.setItem(0,new ItemStack(Items.OAK_PLANKS,13));var inventory=chest.getItem(0).copy();boolean pending=HallUpgradeGoal.pending(t.l,t.s.id());for(int i=0;i<3;i++)TerminalProgress.blockers(t.l,t.e);h.assertTrue(ItemStack.matches(inventory,chest.getItem(0))&&state.equals(BookResearch.inspect(t.l,t.e))&&pending==HallUpgradeGoal.pending(t.l,t.s.id()),"Repeated validation spends no stock/research and creates no project");
   var far=new SettlementData.Entry(t.s,t.e.dimension(),t.e.center().offset(262144,0,262144));var cells=BuildingPlacement.layout(far,t.annex,t.annex.type()).keySet();h.assertTrue(cells.stream().noneMatch(t.l::hasChunkAt),"All hypothetical annex chunks start unloaded");h.assertTrue(TerminalProgress.missing(t.l,far,t.annex)>0&&TerminalProgress.blockers(t.l,far).containsKey(key(t.annex)),"Unloaded physical annex cannot pass");h.assertTrue(cells.stream().noneMatch(t.l::hasChunkAt),"Validator does not force annex chunk loads");
  }finally{t.close();}h.succeed();}
 @GameTest(template="empty",batch="terminal_annex_guards",timeoutTicks=200)
 public static void aQueuedRealParentUpgradeRetainsItsWorkingFixedAnnex(GameTestHelper h){
  var t=town(h,"carpentry_annex",0,true,3);try{paid(h,t);h.assertTrue(BuildingLevels.level(t.l,t.e,t.parent)==3&&!TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex)),"Parent's real working III supports its fixed annex while parent VI remains incomplete");var survey=BuildingOrders.survey(t.l,t.e,BuildingTiers.layoutId(t.parent.type(),4),t.parent.rotation(),BuildingPlacement.origin(t.e,t.parent),t.parent);
   h.assertTrue(survey.ok(),"Actual parent III-to-IV survey is valid: "+survey.reason());HallUpgradeGoal.enqueue(t.l,t.e,survey.state());var project=HallUpgradeGoal.inspect(t.l,t.s.id());var stock=LogisticsRoutes.chest(t.l,t.e,t.annex);stock.setItem(0,new ItemStack(Items.SPRUCE_PLANKS,11));var items=stock.getItem(0).copy();var research=BookResearch.inspect(t.l,t.e);
   h.assertTrue(!TerminalProgress.blockers(t.l,t.e).containsKey(key(t.annex))&&TerminalProgress.blockers(t.l,t.e).containsKey(key(t.parent)),"Active legitimate parent upgrade preserves current annex work but is not terminal completion");h.assertTrue(project.equals(HallUpgradeGoal.inspect(t.l,t.s.id()))&&research.equals(BookResearch.inspect(t.l,t.e))&&ItemStack.matches(items,stock.getItem(0))&&BuildingLevels.level(t.l,t.e,t.parent)==3,"Validation commits no upgrade, inventory payment, research or project alteration");
  }finally{t.close();}h.succeed();
 }
}
