package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-112 (AD-104 P4): a farm works the modules its core's level allows within the land its builders have laid. Land (Settlement.fieldLevel)
 *  grows only with a farm project that carried field work; the farmer, the machine and a siege see the worked modules. These tests
 *  reach past the 48x32x40 template, so they run in their own batch and lay their own ground (grass at y 0, air up to HEADROOM+8).
 *  AD-130: the yard is a layout-6 village (the table 1/4/6/12/18/18); the level-V machine keeps the AD-104 table of an older village. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmLevelGameTests {
 private static final String BATCH="cores_farm";
 private record Yard(ServerLevel l,SettlementData.Entry e,Settlement s){}
 /** A hall with its chest and open ground from x -6..36, z -2..33 of the centre: stone under grass, air above the headroom. */
 private static Yard yard(GameTestHelper h){
  return yard(h,OrganicLots.BARN_LOTS);
 }
 private static Yard yard(GameTestHelper h,int layout){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());s.lotLayout(layout);
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  ground(l,center,-6,36,-2,33);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Yard(l,e,s);
 }
 private static void ground(ServerLevel l,BlockPos at,int x0,int x1,int z0,int z1){
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){l.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(at.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=FarmField.HEADROOM+8;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
 }
 /** A farm standing on its lot at level I, its farmhouse laid. */
 private static Settlement.Building farm(Yard t,String key,int dx,int dz){
  var b=new Settlement.Building(Settlement.childId(t.s.id(),"building/"+key),"farm",dx,0,dz);t.s.addBuilding(b);lay(t,b,"farm");return kept(t,b);
 }
 private static void lay(Yard t,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(design,BuildingPlacement.origin(t.e,b),b.rotation()).entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),2);}
 private static Settlement.Building kept(Yard t,Settlement.Building b){return t.s.buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();}
 /** Kept at a level by the settlement alone, with that level's design (equipment and core) standing: the land stays as it was. */
 private static Settlement.Building raise(Yard t,Settlement.Building b,int level){for(int n=kept(t,b).level()+1;n<=level;n++)t.s.raiseBuildingLevel(b.id(),n);var k=kept(t,b);lay(t,k,BuildingTiers.layoutId("farm",level));return k;}
 private static BlockPos core(Yard t,Settlement.Building b){var c=LevelArchitecture.core("farm");return BuildingPlacement.at(t.e,b,c.getX(),c.getY(),c.getZ());}
 private static BlockPos at(Yard t,Settlement.Building b,BlockPos local){return BuildingPlacement.at(t.e,b,local.getX(),local.getY(),local.getZ());}
 private static void research(ServerLevel l,SettlementData.Entry e,int upTo){
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int level=2;level<=upTo;level++)for(var id:BuildingTiers.research("farm",level))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);
 }
 private static void done(ServerLevel l,Settlement s){
  HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());
  try{java.nio.file.Files.deleteIfExists(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** Carries a surveyed project out the way the builders do, without walking, and registers it. */
 private static void execute(GameTestHelper h,ServerLevel l,SettlementData.Entry e,CompoundTag state){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);h.assertTrue(BuildingOrders.reconcile(l,op,origin),"No drift at operation "+i+": "+op);
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()),"Operation "+i+" at "+step.pos()+" found "+l.getBlockState(step.pos()));
  }
  h.assertTrue(BuildingOrders.complete(l,e,state),"The project matches its design and its field");
 }
 private static List<CompoundTag> fieldOps(CompoundTag state){var out=new ArrayList<CompoundTag>();for(var raw:state.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("field"))out.add((CompoundTag)raw);return out;}

 /** #1, #2: built vs worked. A farm raised by the settlement alone keeps its one module whatever its core; a farm-II project lays the 2x2
  *  (AD-130) — their water and slabs, nothing in module I, three oak slabs — and then works 320 plots with its core, 80 without. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=300) public static void aFarmWorksTheModulesItsCoreAllows(GameTestHelper h){
  var t=yard(h);
  try{
   for(int level=1;level<=6;level++)h.assertTrue(FarmField.modules(level,false).size()==CoreEffects.value("farm","field",level)&&FarmField.modules(level,true).size()==FarmField.modules(level,false).size(),"The field of level "+level+" has the modules its core promises");
   var kept=raise(t,farm(t,"farm/raised",30,0),2);
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==2&&t.s.fieldLevel(kept.id())==1,"Raised by the settlement alone: works at II, the land is still level I's");
   h.assertTrue(FarmField.worked(t.l,t.e,kept).size()==1&&FarmField.workedCells(t.l,t.e,kept).size()==80,"No land was laid: one module, 80 plots, at core grade II");
   t.l.setBlock(core(t,kept),Cores.state("farm",6),3);
   h.assertTrue(FarmField.workedCells(t.l,t.e,kept).size()==80,"Nor at any higher grade");
   var b=farm(t,"farm/project",12,0);
   h.assertTrue(FarmField.workedCells(t.l,t.e,b).size()==80&&BuildingLevels.level(t.l,t.e,b)==1,"A farm of level I works its one module");
   research(t.l,t.e,2);var survey=BuildingTiers.survey(t.l,t.e,b);
   h.assertTrue(survey.ok(),"The farm-II project can be laid: "+survey.reason()+" "+survey.conflicts());
   var field=fieldOps(survey.state());var added=FarmField.added(2,false).get(0);var water=at(t,b,FarmField.localWater(added));var surveyed=b;
   h.assertTrue(field.stream().noneMatch(op->FarmField.inModules(FarmField.modules(1),BuildingPlacement.local(t.e,surveyed,BlockPos.of(op.getLong("pos"))))),"The project lays nothing in module I");
   h.assertTrue(field.stream().anyMatch(op->op.getLong("pos")==water.asLong()&&HallConstructionPlan.step(op).after().is(Blocks.WATER)),"Module (1,0) gets its water: "+field.size()+" field operations");
   var paid=field.stream().filter(op->!op.getString("item").isEmpty()).toList();
   h.assertTrue(paid.size()==3&&paid.stream().anyMatch(op->op.getLong("pos")==water.above().asLong())&&paid.stream().allMatch(op->op.getString("item").equals(FarmField.COVER_ITEM))&&FarmField.COVER_ITEM.equals("minecraft:oak_slab"),"The field costs three oak slabs, one over each new water: "+paid);
   h.assertTrue(FarmField.addedCost(2).equals(Map.of(FarmField.COVER_ITEM,3))&&survey.state().getCompound("cost").getInt(FarmField.COVER_ITEM)>=3,"The estimate charges exactly those slabs: "+survey.state().getCompound("cost"));
   execute(h,t.l,t.e,survey.state());
   b=kept(t,b);
   h.assertTrue(b.level()==2&&t.s.fieldLevel(b.id())==2&&!t.s.westField(b.id()),"The project raised the farm and its land to II, east");
   h.assertTrue(t.l.getBlockState(core(t,b)).equals(Cores.state("farm",2))&&BuildingLevels.level(t.l,t.e,b)==2,"The builders set the farm core: works at II");
   h.assertTrue(FarmField.cells(t.e,b).size()==320&&FarmField.worked(t.l,t.e,b).size()==4&&FarmField.workedCells(t.l,t.e,b).size()==320,"With its core the farm works all four modules, 320 plots");
   h.assertTrue(t.l.getFluidState(water).isSource()&&t.l.getBlockState(water.above()).equals(FarmField.COVER),"The new module's water stands under its slab");
   var plot=at(t,b,FarmField.corner(added).offset(1,1,1));
   h.assertTrue(FarmField.contains(t.e,b,plot)&&FarmCrops.WHEAT.canPrepare(t.l,plot)&&Math.abs(plot.getX()-water.getX())<=4&&Math.abs(plot.getZ()-water.getZ())<=4,"A new plot can be tilled and lies by its water");
   t.l.setBlock(core(t,b),Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,b)==1&&FarmField.workedCells(t.l,t.e,b).size()==80&&FarmField.cells(t.e,b).size()==320,"Without its core it works one module of the four it has");
   t.l.setBlock(core(t,b),Cores.state("farm",2),3);
   h.assertTrue(FarmField.workedCells(t.l,t.e,b).size()==320,"The core back: 320 plots again");
  }finally{done(t.l,t.s);}
  h.succeed();
 }
 /** #3: a chest (and so any building's block) on the new modules of both sides refuses the level with "field". */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aBlockOnTheNewPlotsRefusesTheLevel(GameTestHelper h){
  var t=yard(h);
  try{
   var b=farm(t,"farm",12,0);research(t.l,t.e,2);
   // AD-136 (owner answer 2): no building above the hall, so the hall stands at II for the farm's II.
   t.s.civilization().completedHallUpgrade(2);
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,b).isEmpty(),"The open field may be ordered: "+BuildingTiers.refusal(t.l,t.e,b));
   // AD-130: II leaves the farmhouse's column, so a chest on the east plots turns the field west; one on the west plots too refuses it.
   var in=at(t,b,FarmField.corner(FarmField.added(2,false).get(0)).offset(2,1,6));t.l.setBlock(in,Blocks.CHEST.defaultBlockState(),3);
   var turned=FarmField.plan(t.l,t.e,b,2);
   h.assertTrue(turned.ok()?turned.west()&&BuildingTiers.refusal(t.l,t.e,b).isEmpty():BuildingTiers.refusal(t.l,t.e,b).equals("field"),"A chest on the east plots: the field turns west, or is refused where the west is taken: "+turned.reason());
   var out=at(t,b,FarmField.corner(FarmField.added(2,true).get(0)).offset(2,1,6));t.l.setBlock(out,Blocks.CHEST.defaultBlockState(),3);
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,b).equals("field"),"Chests on both sides' plots: refused with field, not "+BuildingTiers.refusal(t.l,t.e,b));
   h.assertTrue(BuildingTiers.order(t.l,t.e,b).equals("field")&&!HallUpgradeGoal.pending(t.l,t.s.id()),"The order is refused and nothing is queued");
   t.l.setBlock(in,Blocks.AIR.defaultBlockState(),3);t.l.setBlock(out,Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,b).isEmpty(),"Taken away: the level may be ordered again");
  }finally{done(t.l,t.s);}
  h.succeed();
 }
 /** #4: the starter farm's level III would go east into the forester, so it turns west; its west modules are its land from then on
  *  (AD-130: the second column of the 2x3, (1,2), mirrored past the farmhouse). */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void theStarterFarmTurnsWestAtLevelThree(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(10,3,1));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  try{
   var b=s.buildings().stream().filter(x->x.type().equals("farm")).findFirst().orElseThrow();var farm=BuildingPlacement.origin(e,b);
   // The west of the farm is open ground (the village leaves it free up to VI): grass under open air.
   for(var m:FarmField.added(3,true))for(var c:FarmField.localColumns(List.of(m))){var g=BuildingPlacement.at(e,b,c.getX(),0,c.getZ());
    l.setBlock(g.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=FarmField.HEADROOM+8;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),2);}
   // Kept at II with its design and its land of level II (as a farm-II project leaves it).
   s.raiseBuildingLevel(b.id(),2);b=s.buildings().stream().filter(x->x.type().equals("farm")).findFirst().orElseThrow();
   for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId("farm",2),farm,b.rotation()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
   s.raiseFieldLevel(b.id(),2);
   var east=FarmField.survey(l,e,b,3,false);
   h.assertTrue(east.reason().equals("field"),"East of the farm the forester's lot is in the way: "+east.reason());
   var plan=FarmField.plan(l,e,b,3);
   h.assertTrue(plan.ok()&&plan.west(),"The survey takes the west side: "+plan.reason()+" "+plan.conflicts());
   var survey=BuildingTiers.survey(l,e,b);
   h.assertTrue(survey.ok()&&survey.state().getBoolean("fieldWest"),"The farm-III project lays the west modules: "+survey.reason()+" "+survey.conflicts());
   execute(h,l,e,survey.state());
   b=s.buildings().stream().filter(x->x.type().equals("farm")).findFirst().orElseThrow();
   h.assertTrue(s.westField(b.id())&&s.fieldLevel(b.id())==3&&b.level()==3,"The farm keeps its west side and land of level III");
   var west=FarmField.added(3,true).get(1);var plot=BuildingPlacement.at(e,b,FarmField.corner(west).getX()+1,1,FarmField.corner(west).getZ()+1);
   h.assertTrue(BuildingPlacement.local(e,b,plot).getX()<0,"The module lies west of the farmhouse");
   h.assertTrue(FarmField.contains(e,b,plot)&&FarmField.ground(e,b,plot.below()),"A west plot is the farm's plot and its ground the farm's ground");
   h.assertTrue(OwnershipEvents.disallowedPlacement(l,plot)&&OwnershipEvents.protectedBlock(l,plot.below()),"Nobody builds on the west field or takes its ground");
   h.assertTrue(FarmField.water(e,b).contains(BuildingPlacement.at(e,b,FarmField.localWater(west).getX(),0,FarmField.localWater(west).getZ())),"The west module's water is the farm's");
   var eastPlot=BuildingPlacement.at(e,b,FarmField.corner(FarmField.added(3,false).get(1)).getX()+1,1,FarmField.corner(FarmField.added(3,false).get(1)).getZ()+1);
   h.assertTrue(!FarmField.contains(e,b,eastPlot),"The east modules are not its field");
  }finally{
   for(var r:s.residents()){var npc=l.getEntity(r.id());if(npc!=null)npc.discard();}done(l,s);}
  h.succeed();
 }
 /** #5: on either side, the field of every level feeds at least the residents the owner set for it (FarmYield, at randomTickSpeed 3, every
  *  floor on its own) — exactly the core's "feeds" (AD-130). */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void everyLevelOfTheFieldFeedsItsTarget(GameTestHelper h){
  for(boolean west:new boolean[]{false,true})for(int level=1;level<=6;level++){
   var mods=FarmField.modules(level,west);int feeds=FarmYield.ratedResidents(FarmField.wheatPerDay(mods,3),FarmField.RATING);
   h.assertTrue(FarmField.localCells(mods).size()==80*mods.size(),"Every module has its 80 plots at level "+level);
   h.assertTrue(feeds>=FarmField.target(level),"Level "+level+(west?" west":" east")+" feeds "+feeds+" of its target "+FarmField.target(level));
   h.assertTrue(feeds==CoreEffects.value("farm","feeds",level)&&feeds==FarmField.feeds(level),"What it feeds is the core's promise at level "+level+": "+feeds);
  }
  h.succeed();
 }
 /** #6: a siege of a farm working at III (six modules, AD-130) carries 18 charges and aims only at plots of the field; a core of grade II
  *  leaves four modules, 12 charges. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aSiegeCountsTheModulesTheFarmWorks(GameTestHelper h){
  var t=yard(h);
  try{
   var b=raise(t,farm(t,"farm",12,0),3);t.s.raiseFieldLevel(b.id(),3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,b)==3&&FarmField.worked(t.l,t.e,b).size()==6,"The farm works six modules at III");
   h.assertTrue(FarmField.charges(t.l,t.e,b)==18&&FarmField.workingSoil(t.l,t.e,b)==6*FarmField.MODULE_SOIL,"18 charges, and the soil of six modules: "+FarmField.charges(t.l,t.e,b));
   var aims=Sieges.aims(t.l,t.e,b);var water=new HashSet<BlockPos>();for(var w:FarmField.water(t.e,b))water.add(w.above());
   h.assertTrue(!aims.isEmpty()&&aims.equals(FarmField.aims(t.l,t.e,b)),"The siege aims where the field says");
   for(var a:aims)h.assertTrue(FarmField.contains(t.e,b,a)&&!water.contains(a),"An aim is a plot, never over water: "+a);
   t.l.setBlock(core(t,b),Cores.state("farm",2),3);
   h.assertTrue(FarmField.charges(t.l,t.e,b)==12&&Sieges.aims(t.l,t.e,b).size()==aims.size()*4/6,"A core of grade II: the farm works four modules, 12 charges");
  }finally{done(t.l,t.s);}
  h.succeed();
 }
 /** #7: a level-V farm of a village laid before layout 6 (AD-104 table) whose land is level II: its machine sows the worked module II. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=300) public static void theMachineOfALevelFiveFarmSowsItsSecondModule(GameTestHelper h){
  var t=yard(h,OrganicLots.FIELD_MODULES);
  var research=BookResearch.inspect(t.l,t.e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  research.put("legacyDone",done);BookResearch.store(t.l,t.e,research);// AD-136: no mechanics branch; the machine is the farm's own level V.
  var b=new Settlement.Building(Settlement.childId(t.s.id(),"building/farm"),"farm",12,0,0);t.s.addBuilding(b);
  for(int i=2;i<=5;i++)t.s.raiseBuildingLevel(b.id(),i);var farm=kept(t,b);t.s.raiseFieldLevel(farm.id(),2);
  var pos=LogisticsRoutes.position(t.e,farm);t.l.setBlock(pos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);t.l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  for(int i=2;i<=5;i++)for(var placed:BuildingLevels.equipment("farm",i))t.l.setBlock(BuildingPlacement.at(t.e,farm,placed.local().getX(),placed.local().getY(),placed.local().getZ()),BuildingPlacement.state(placed.state(),farm.rotation()),3);
  var second=new HashSet<BlockPos>();for(var p:FarmField.localCells(FarmField.added(2,false,true)))second.add(at(t,farm,p));
  // Module I stays grass (no crop takes to it); module II is farmland under lamps — crops need light, and the test world lies deep underground.
  for(var cell:second){t.l.setBlock(cell.below(),Blocks.FARMLAND.defaultBlockState(),3);t.l.setBlock(cell,Blocks.AIR.defaultBlockState(),3);if((cell.getX()+cell.getZ())%3==0)t.l.setBlock(cell.above(2),Blocks.GLOWSTONE.defaultBlockState(),3);}
  LogisticsRoutes.chest(t.l,t.e,farm).setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));
  h.startSequence().thenIdle(30).thenExecute(()->{
   try{
    h.assertTrue(BuildingLevels.level(t.l,t.e,farm)==5&&FarmField.workedCells(t.l,t.e,farm).size()==160,"The machine's farm works at V over its two modules: "+BuildingLevels.level(t.l,t.e,farm));
    int worked=Machines.tick(t.l,t.e,40,Workshops.wants(t.l,t.e));
    h.assertTrue(worked>0,"The machine takes its turn: why=["+Machines.lastReason+"]");
    int sown=0;for(var cell:second)if(t.l.getBlockState(cell).getBlock() instanceof CropBlock)sown++;
    int first=0;for(var cell:FarmField.localCells(FarmField.modules(1)))if(t.l.getBlockState(at(t,farm,cell)).getBlock() instanceof CropBlock)first++;
    h.assertTrue(sown==1&&first==0,"It sowed one plot of module II and none of module I: "+sown+"/"+first);
    // AD-130 (old worlds): such a farm has no 18 fields on three floors — its card shows no grid and keeps its one farmer.
    var card=BuildingCards.card(t.l,t.e,farm);
    h.assertTrue(card.getList("fields",Tag.TAG_COMPOUND).isEmpty()&&card.contains("crop"),"The card of an old farm keeps the one crop and draws no grid of 18 fields");
    h.assertTrue(BuildingCards.slots(t.s,farm)==1&&Population.slots(t.s,farm.withLevel(6))==1,"And one place of work at every level");
   }finally{done(t.l,t.s);}
  }).thenSucceed();
 }
}
