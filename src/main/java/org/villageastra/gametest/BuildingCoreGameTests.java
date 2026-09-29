package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-112: building cores. Every work building holds one core from level II and one ring more for every level above; the core's grade caps
 *  the level the building works at, the builders set the core and its rings as a chain of one-item operations, the village's own workshops
 *  make them, and (owner, 2026-09-19) nothing sets a grade whose research of the building's branch is not done. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingCoreGameTests {
 private static final List<String> TYPES=List.of("restaurant","farm","warehouse","guard_house","school","mill","masonry","clinic","cartographer","mine","quarry","barracks","livestock","caravan","carpentry","smithy","laboratory","expedition","archery","engineering","forester","town_hall");
 private record Shop(ServerLevel l,SettlementData.Entry e,Settlement.Building shop){}
 /** A hall with its chest and one workshop of this type standing on its lot at level I (as in BuildingLevelGameTests). */
 private static Shop shop(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var shop=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,10,0,0);s.addBuilding(shop);
  for(int x=-2;x<26;x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  lay(l,e,shop,type);
  return new Shop(l,e,shop);
 }
 private static void lay(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(design,BuildingPlacement.origin(e,b),b.rotation()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);}
 private static Settlement.Building kept(Shop t){return t.e.settlement().buildings().stream().filter(b->b.id().equals(t.shop.id())).findFirst().orElseThrow();}
 /** Raises the workshop to a kept level with that level's whole design standing. */
 private static Settlement.Building raise(Shop t,int level){for(int n=kept(t).level()+1;n<=level;n++)t.e.settlement().raiseBuildingLevel(t.shop.id(),n);var b=kept(t);lay(t.l,t.e,b,BuildingTiers.layoutId(b.type(),level));return b;}
 private static BlockPos core(Shop t){var c=LevelArchitecture.core(t.shop.type());return BuildingPlacement.at(t.e,t.shop,c.getX(),c.getY(),c.getZ());}
 /** The research of the building's levels II..upTo is done. */
 private static void research(Shop t,String type,int upTo){
  var research=BookResearch.inspect(t.l,t.e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int level=2;level<=upTo;level++)for(var id:BuildingTiers.research(type,level))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(t.l,t.e,research);
 }
 private static OwnedChestEntity chest(Shop t){return LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));}
 private static void done(Shop t){
  SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());
  try{java.nio.file.Files.deleteIfExists(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+t.e.settlement().id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** Executes a project the way the builder does, without walking; with take, the builder first funds it from the hall chest. */
 private static void execute(GameTestHelper h,Shop t,CompoundTag state,boolean take){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  if(take){var chest=chest(t);var cost=state.getCompound("cost");int n=0;
   for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=cost.getInt(key);
    for(int slot=0;slot<chest.getContainerSize()&&left>0;slot++){var stack=chest.getItem(slot);if(!stack.is(item))continue;int k=Math.min(left,stack.getCount());
     h.assertTrue(!WorldJournal.takeAmount(t.l,Settlement.childId(id,"fund/"+n++),chest.getBlockPos(),slot,stack.copy(),k).isEmpty(),"The builder takes "+key);left-=k;}
    h.assertTrue(left==0,"The hall holds all of "+key);}}
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);h.assertTrue(BuildingOrders.reconcile(t.l,op,origin),"No drift at operation "+i+": "+op+" local "+HallConstructionPlan.step(op).pos().subtract(origin)+" now "+t.l.getBlockState(HallConstructionPlan.step(op).pos()));
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   h.assertTrue(WorldJournal.place(t.l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()),"Operation "+i+" at "+step.pos()+" found "+t.l.getBlockState(step.pos()));
  }
  h.assertTrue(BuildingOrders.complete(t.l,t.e,state),"The project matches its design");
 }
 /** The operations of a project on one cell, in order. */
 private static List<CompoundTag> at(CompoundTag state,BlockPos pos){var out=new ArrayList<CompoundTag>();for(var raw:state.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getLong("pos")==pos.asLong())out.add((CompoundTag)raw);return out;}
 private static int grade(CompoundTag op){return Cores.grade(HallConstructionPlan.step(op).after());}

 /** #1: every core and ring recipe resolves, and makes the right item. */
 @GameTest(template="empty",timeoutTicks=100) public static void everyCoreAndRingRecipeMakesItsItem(GameTestHelper h){
  var l=h.getLevel();var problems=new ArrayList<String>();var wanted=new LinkedHashMap<String,Item>();
  for(var type:CoreCatalog.TYPES)wanted.put(CoreCatalog.path(type),VillageAstra.CORE_ITEMS.get(type).get());
  for(int g=CoreCatalog.FIRST_RING;g<=CoreCatalog.LAST_RING;g++)wanted.put("core_ring_"+g,VillageAstra.CORE_RINGS.get(g).get());
  for(var entry:wanted.entrySet()){var recipe=l.getRecipeManager().byKey(new ResourceLocation(VillageAstra.ID,entry.getKey()));
   if(recipe.isEmpty())problems.add(entry.getKey()+" has no recipe");else if(!recipe.get().getResultItem(l.registryAccess()).is(entry.getValue()))problems.add(entry.getKey()+" makes "+recipe.get().getResultItem(l.registryAccess()));}
  h.assertTrue(wanted.size()==25&&problems.isEmpty(),"25 recipes make their core or ring: "+problems);
  h.assertTrue(VillageAstra.CORE_ITEMS.values().stream().allMatch(i->i.get() instanceof CoreItem),"Every core item is a CoreItem, which places nothing");
  h.succeed();
 }
 /** #2: a core outlasts TNT beside it, drops nothing when mined, and no piston moves it. */
 @GameTest(template="empty",timeoutTicks=100) public static void aCoreOutlastsTntDropsNothingAndStaysPut(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(20,4,20));var core=Cores.state("smithy",4);
  l.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);l.setBlock(pos,core,3);
  l.explode(null,pos.getX()+1.5,pos.getY()+.5,pos.getZ()+.5,4F,Level.ExplosionInteraction.TNT);
  h.assertTrue(l.getBlockState(pos).equals(core),"TNT next to the core leaves it standing: "+l.getBlockState(pos));
  h.assertTrue(Block.getDrops(core,l,pos,null).isEmpty(),"A mined core drops nothing");
  h.assertTrue(core.getPistonPushReaction()==PushReaction.BLOCK,"A piston cannot push a core");
  l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.succeed();
 }
 /** #3: level II of every work building costs its core, level N ≥ III its ring N; houses take none. */
 @GameTest(template="empty",timeoutTicks=300) public static void everyLevelTakesItsCoreOrRing(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var type:TYPES){
   try{LevelArchitecture.core(type);}catch(IllegalStateException ex){problems.add(ex.getMessage());continue;}
   for(int level=type.equals("town_hall")?4:2;level<=BuildingTiers.MAX;level++){var cost=BuildingTiers.cost(type,level);String want=level==2?CoreCatalog.coreId(type):CoreCatalog.ringId(level);
    if(cost.getOrDefault(want,0)!=1)problems.add(type+" "+level+" lacks "+want+": "+cost.keySet());}
   if(!BuildingLevels.equipment(type,2).stream().anyMatch(p->Cores.isCoreOf(p.state(),type)))problems.add(type+" level II equipment lacks its core");
   var worth=new StringBuilder();for(int level=2;level<=BuildingTiers.MAX;level++)worth.append(" ").append(level).append(":").append(BuildingTiers.count(BuildingTiers.cost(type,level))).append("/").append(BuildingTiers.worth(BuildingTiers.cost(type,level)));
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_CORE_COST {}{}",type,worth);}
  for(var type:List.of("home","home_2"))for(int level=2;level<=BuildingTiers.MAX;level++)
   if(BuildingTiers.cost(type,level).keySet().stream().anyMatch(k->k.startsWith("villageastra:core")))problems.add(type+" "+level+" takes a core");
  h.assertTrue(problems.isEmpty(),"Every level takes its core or ring: "+problems);h.succeed();
 }
 private static final Set<String> FORBIDDEN=Set.of("iron_block","gold_block","diamond_block","emerald_block","netherite_block","lapis_block","redstone_block","anvil","chipped_anvil","damaged_anvil","enchanting_table","lodestone","bell","jukebox","observer","redstone_lamp","brewing_stand","hopper","cauldron","water_cauldron","lava_cauldron","powder_snow_cauldron","blast_furnace","dispenser","piston","sticky_piston","piston_head","lightning_rod","note_block","target");
 /** #4: no design at any level holds a precious, redstone or Nether block. */
 @GameTest(template="empty",timeoutTicks=600) public static void noDesignHoldsAForbiddenBlock(GameTestHelper h){
  var problems=new TreeSet<String>();
  for(var d:BuildingBlueprints.designs()){var ids=new ArrayList<String>();ids.add(d.id());
   if(BuildingTiers.upgradable(d.id())&&!d.id().startsWith("town_hall_"))for(int level=2;level<=BuildingTiers.MAX;level++)ids.add(BuildingTiers.layoutId(d.id(),level));
   for(var id:ids)for(var s:BuildingBlueprints.layout(id,BlockPos.ZERO).values()){var name=BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    if(FORBIDDEN.contains(name)||name.contains("copper")||name.contains("blackstone"))problems.add(id+":"+name);}}
  h.assertTrue(problems.isEmpty(),"No design holds a forbidden block: "+problems);h.succeed();
 }
 /** #5: the grade rule — a ring raises the standing core one grade, a lost core comes back as a chain of four, and a higher grade counts. */
 @GameTest(template="empty",timeoutTicks=300) public static void theSurveyChargesTheCoreAndOneRingPerGrade(GameTestHelper h){
  var t=shop(h,"restaurant");
  try{
   research(t,"restaurant",5);var b=raise(t,2);var pos=core(t);
   h.assertTrue(t.l.getBlockState(pos).equals(Cores.state("restaurant",2)),"Level II stands with its core at grade II: "+t.l.getBlockState(pos));
   var survey=BuildingTiers.survey(t.l,t.e,b);var ops=at(survey.state(),pos);
   h.assertTrue(survey.ok()&&ops.size()==1&&ops.get(0).getString("item").equals(CoreCatalog.ringId(3))&&grade(ops.get(0))==3,"Level III sets ring III on the standing core: "+ops);
   h.assertTrue(survey.state().getCompound("cost").getInt(CoreCatalog.ringId(3))==1&&!survey.state().getCompound("cost").contains(CoreCatalog.coreId("restaurant")),"Its cost is the ring, not a new core");
   b=raise(t,5);t.l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
   var repair=BuildingRepairs.plan(t.l,t.e,b);h.assertTrue(repair.state()!=null,"A lost core is repaired: "+repair.reason());
   var chain=at(repair.state(),pos);var cost=repair.state().getCompound("cost");
   h.assertTrue(chain.size()==4&&grade(chain.get(0))==2&&grade(chain.get(1))==3&&grade(chain.get(2))==4&&grade(chain.get(3))==5,"The lost core comes back as a chain of four grades: "+chain);
   h.assertTrue(chain.get(0).getString("item").equals(CoreCatalog.coreId("restaurant"))&&chain.get(1).getString("item").equals(CoreCatalog.ringId(3))&&chain.get(2).getString("item").equals(CoreCatalog.ringId(4))&&chain.get(3).getString("item").equals(CoreCatalog.ringId(5)),"One item per operation: core, rings III, IV, V");
   h.assertTrue(cost.getInt(CoreCatalog.coreId("restaurant"))==1&&cost.getInt(CoreCatalog.ringId(3))==1&&cost.getInt(CoreCatalog.ringId(4))==1&&cost.getInt(CoreCatalog.ringId(5))==1,"The repair costs all four: "+cost);
   // complete accepts a core above the design's grade.
   var state=new CompoundTag();var op=new CompoundTag();op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));op.put("after",NbtUtils.writeBlockState(Cores.state("restaurant",3)));
   var list=new ListTag();list.add(op);state.put("ops",list);state.putUUID("building",b.id());state.putLong("origin",BuildingPlacement.origin(t.e,b).asLong());state.putString("design","restaurant");
   t.l.setBlock(pos,Cores.state("restaurant",4),3);h.assertTrue(BuildingOrders.complete(t.l,t.e,state),"A core of grade IV stands for a design of grade III");
   t.l.setBlock(pos,Cores.state("restaurant",2),3);h.assertTrue(!BuildingOrders.complete(t.l,t.e,state),"A core of grade II does not");
   h.assertTrue(BuildingOrders.materials(Cores.state("restaurant",2),Cores.state("restaurant",5)).equals(List.of(CoreCatalog.ringId(3),CoreCatalog.ringId(4),CoreCatalog.ringId(5)))
     &&BuildingOrders.materials(Blocks.AIR.defaultBlockState(),Cores.state("restaurant",2)).equals(List.of(CoreCatalog.coreId("restaurant")))
     &&BuildingOrders.materials(Cores.state("mill",4),Cores.state("restaurant",3)).equals(List.of(CoreCatalog.coreId("restaurant"),CoreCatalog.ringId(3))),"materials: rings over a standing core, the core over anything else");
  }finally{done(t);}
  h.succeed();
 }
 /** #6: the core's grade caps the working level. */
 @GameTest(template="empty",timeoutTicks=200) public static void theCoreGradeCapsTheWorkingLevel(GameTestHelper h){
  var t=shop(h,"restaurant");
  try{
   var b=raise(t,3);var pos=core(t);
   h.assertTrue(BuildingLevels.level(t.l,t.e,b)==3,"Level III with its core at grade III works at III");
   t.l.setBlock(pos,Cores.state("restaurant",2),3);h.assertTrue(BuildingLevels.level(t.l,t.e,b)==2,"Full equipment but a grade-II core: works at II");
   t.l.setBlock(pos,Cores.state("restaurant",3),3);h.assertTrue(BuildingLevels.level(t.l,t.e,b)==3,"The ring back: III");
   t.l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);h.assertTrue(BuildingLevels.level(t.l,t.e,b)==1,"No core: I");
   t.l.setBlock(pos,Cores.state("mill",3),3);h.assertTrue(BuildingLevels.level(t.l,t.e,b)==1,"Another building's core counts for nothing");
  }finally{done(t);}
  h.succeed();
 }
 /** #7: the hall holds its seal from II; its own projects charge the seal for II and ring III for III, and its level IV takes ring IV. */
 @GameTest(template="empty",timeoutTicks=300) public static void theHallHoldsItsSealAndItsProjectsChargeIt(GameTestHelper h){
  var l=h.getLevel();var two=BuildingBlueprints.layout("town_hall_2",BlockPos.ZERO);var three=BuildingBlueprints.layout("town_hall_3",BlockPos.ZERO);var cell=new BlockPos(3,1,3);
  h.assertTrue(two.get(cell).equals(Cores.state("town_hall",2))&&three.get(cell).equals(Cores.state("town_hall",3)),"The hall's seal stands at (3,1,3): "+two.get(cell)+" / "+three.get(cell));
  h.assertTrue(LevelArchitecture.core("town_hall").equals(cell),"The hall's core cell is its seal's: "+LevelArchitecture.core("town_hall"));
  h.assertTrue(BuildingTiers.cost("town_hall",4).getOrDefault(CoreCatalog.ringId(4),0)==1,"Hall IV takes ring IV");
  var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var e=SettlementData.get(l.getServer()).entry(s.id());
  try{
   var second=HallUpgradeGoal.preview(l,e);var cost=second.getCompound("cost");
   h.assertTrue(cost.getInt(CoreCatalog.coreId("town_hall"))==1&&!cost.contains(CoreCatalog.ringId(3)),"Hall II charges the seal: "+cost);
   for(var c:BuildingBlueprints.layout("town_hall_2",e.center()).entrySet())if(!l.getBlockState(c.getKey()).equals(c.getValue()))l.setBlock(c.getKey(),c.getValue(),2);
   s.civilization().completedHallUpgrade(2);
   var third=HallUpgradeGoal.preview(l,e);cost=third.getCompound("cost");
   h.assertTrue(cost.getInt(CoreCatalog.ringId(3))==1&&!cost.contains(CoreCatalog.coreId("town_hall")),"Hall III charges ring III, not a new seal: "+cost);
  }finally{
   for(var r:s.residents()){var npc=l.getEntity(r.id());if(npc!=null)npc.discard();}
   HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** #8: the builders work the chain — they take the core and three rings from the hall and leave grade V; a builder dropping out in the middle
  *  leaves a project that still asks for the rings not yet set. */
 @GameTest(template="empty",timeoutTicks=300) public static void theBuildersWorkTheChainAndARestartKeepsItsRings(GameTestHelper h){
  var t=shop(h,"restaurant");
  try{
   research(t,"restaurant",5);var b=raise(t,5);var pos=core(t);t.l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
   var chest=chest(t);chest.setItem(0,new ItemStack(VillageAstra.CORE_ITEMS.get(CoreCatalog.coreType("restaurant")).get()));for(int g=3;g<=5;g++)chest.setItem(g-2,new ItemStack(VillageAstra.CORE_RINGS.get(g).get()));
   var state=BuildingRepairs.plan(t.l,t.e,b).state();h.assertTrue(state!=null,"The lost core is planned");
   // A builder who drops out after the core and ring III: the rest of the chain is still to be paid.
   var ops=state.getList("ops",Tag.TAG_COMPOUND).copy();var resumed=state.copy();int set=0;
   for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);if(op.getLong("pos")==pos.asLong()&&set<2){op.putBoolean("done",true);set++;}}
   var left=new CompoundTag();for(int i=0;i<ops.size();i++){var op=ops.getCompound(i);if(!op.getBoolean("done")&&!op.getString("item").isEmpty())left.putInt(op.getString("item"),left.getInt(op.getString("item"))+1);}
   h.assertTrue(left.getInt(CoreCatalog.ringId(4))==1&&left.getInt(CoreCatalog.ringId(5))==1&&!left.contains(CoreCatalog.ringId(3))&&!left.contains(CoreCatalog.coreId("restaurant")),"Rings IV and V are still to be paid, the core and ring III are not: "+left);
   execute(h,t,state,true);
   h.assertTrue(t.l.getBlockState(pos).equals(Cores.state("restaurant",5)),"The chain ends at grade V: "+t.l.getBlockState(pos));
   h.assertTrue(LogisticsRoutes.count(chest,s->s.getItem() instanceof CoreItem||s.getItem() instanceof CoreItem.CoreRingItem)==0,"The hall gave the core and all three rings");
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept(t))==5,"The bakery works at V again");
  }finally{done(t);}
  h.succeed();
 }
 /** #8b: a JobCargo restart in the middle of a chain asks only for the rings not yet set. */
 @GameTest(template="empty",timeoutTicks=200) public static void aRestartInTheMiddleOfAChainAsksForTheRemainingRings(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);
  var builder=(ResidentEntity)l.getEntity(s.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow().id());
  var file=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+s.id()+".bin");
  try{
   var id=UUID.randomUUID();var ops=new ListTag();var pos=origin.offset(30,1,30);BlockState at=Blocks.AIR.defaultBlockState();
   for(int g=2;g<=5;g++){var next=Cores.state("restaurant",g);var op=new CompoundTag();op.putLong("pos",pos.asLong());op.put("before",NbtUtils.writeBlockState(at));op.put("after",NbtUtils.writeBlockState(next));op.putString("item",g==2?CoreCatalog.coreId("restaurant"):CoreCatalog.ringId(g));if(g<=3)op.putBoolean("done",true);ops.add(op);at=next;}
   var state=new CompoundTag();state.putInt("schema",2);state.putString("kind","building");state.putUUID("id",id);state.putUUID("project",id);state.putUUID("worker",builder.getUUID());state.put("ops",ops);state.put("cost",new CompoundTag());state.putBoolean("funded",true);
   var cargo=new ListTag();cargo.add(new ItemStack(VillageAstra.CORE_RINGS.get(4).get()).save(new CompoundTag()));cargo.add(new ItemStack(VillageAstra.CORE_RINGS.get(5).get()).save(new CompoundTag()));state.put("cargo",cargo);
   NbtRecord.write(file,state);
   var snap=JobCargo.snapshot(builder,true);h.assertTrue(snap.jobs().size()==1,"The builder holds the chain");
   var reset=snap.jobs().getCompound(0).getCompound("reset");var cost=reset.getCompound("cost");
   h.assertTrue(cost.size()==2&&cost.getInt(CoreCatalog.ringId(4))==1&&cost.getInt(CoreCatalog.ringId(5))==1,"The reset still asks for rings IV and V: "+cost);
   h.assertTrue(reset.getInt("index")==2&&reset.getList("ops",Tag.TAG_COMPOUND).equals(ops),"The chain keeps its done links");
  }finally{
   HallUpgradeGoal.drop(l,s.id());for(var r:s.residents()){var npc=l.getEntity(r.id());if(npc!=null)npc.discard();}SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 /** #9: the economy — a pending project asks for the core and the builder can craft it, the smith crafts ring III, a mayor of its own orders
  *  a level when only a core it can make is missing, and ring VI is makeable only with a netherite block in hand. */
 @GameTest(template="empty",timeoutTicks=300) public static void theVillageMakesItsCoresAndRings(GameTestHelper h){
  var t=shop(h,"carpentry");
  try{
   var s=t.e.settlement();for(var type:List.of("forester","mine","smithy","masonry","farm"))s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,-60,0,10*List.of("forester","mine","smithy","masonry","farm").indexOf(type)));
   var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);project.put("ops",new ListTag());project.put("cargo",new ListTag());
   var cost=new CompoundTag();cost.putInt(CoreCatalog.coreId("farm"),1);project.put("cost",cost);HallUpgradeGoal.enqueue(t.l,t.e,project);
   var farmCore=VillageAstra.CORE_ITEMS.get("farm").get();
   var wants=Workshops.wants(t.l,t.e).stream().filter(w->w.ingredient().test(new ItemStack(farmCore))).toList();
   h.assertTrue(wants.size()==1&&wants.get(0).count()==1,"The pending project asks the village for the farm core");
   var inputs=new SimpleContainer(27);inputs.addItem(new ItemStack(Items.WHEAT_SEEDS,4));inputs.addItem(new ItemStack(Items.HAY_BLOCK,2));inputs.addItem(new ItemStack(Items.WHEAT,2));inputs.addItem(new ItemStack(Items.COMPOSTER,1));
   var job=Workshops.plan(t.l,Workshops.spec("town_hall"),inputs,wants);
   h.assertTrue(job!=null&&job.outputs().stream().anyMatch(o->o.is(farmCore)),"The builder crafts the farm core from the delivered inputs: "+job);
   HallUpgradeGoal.drop(t.l,s.id());
   var ring=VillageAstra.CORE_RINGS.get(3).get();var metal=new SimpleContainer(27);metal.addItem(new ItemStack(Items.COPPER_INGOT,8));metal.addItem(new ItemStack(Items.IRON_INGOT,1));
   var forge=Workshops.plan(t.l,Workshops.spec("smithy"),metal,List.of(new Workshops.Want(Ingredient.of(ring),1,s.id())));
   h.assertTrue(forge!=null&&forge.outputs().stream().anyMatch(o->o.is(ring)),"The smith crafts ring III: "+forge);
   // A mayor of its own: the whole cost but the core lies in the hall.
   var chest=chest(t);int slot=0;var next=BuildingTiers.cost("carpentry",2);
   for(var entry:next.entrySet()){if(CoreCatalog.isCore(entry.getKey()))continue;int n=entry.getValue();var item=BuiltInRegistries.ITEM.get(new ResourceLocation(entry.getKey()));
    while(n>0){h.assertTrue(slot<chest.getContainerSize(),"The hall chest holds the fixture");int k=Math.min(n,item.getMaxStackSize());chest.setItem(slot++,new ItemStack(item,k));n-=k;}}
   h.assertTrue(next.containsKey(CoreCatalog.coreId("carpentry"))&&Workshops.makeable(t.l,t.e,CoreCatalog.coreId("carpentry")),"The village can make the carpentry core itself");
   h.assertTrue(MayorPlanner.affordable(t.l,t.e,t.shop),"Only a core the village makes is missing: the mayor may order the level");
   // AD-137 (addendum, owner 2026-09-23): an ordinary item short that the village makes no longer holds the order back (MayorOrderGameTests).
   var gone=BuiltInRegistries.ITEM.getKey(chest.getItem(0).getItem()).toString();chest.setItem(0,ItemStack.EMPTY);
   h.assertTrue(Workshops.producible(t.l,t.e,gone)&&MayorPlanner.affordable(t.l,t.e,t.shop),"An ordinary item short that the village makes ("+gone+"): the order still goes");
   h.assertTrue(!Workshops.makeable(t.l,t.e,CoreCatalog.ringId(6)),"Without a netherite block ring VI is not makeable");
   chest.setItem(0,new ItemStack(Items.NETHERITE_BLOCK));h.assertTrue(Workshops.makeable(t.l,t.e,CoreCatalog.ringId(6)),"With one in the hall it is");
  }finally{done(t);}
  h.succeed();
 }
 /** #10: the laboratory's office opens at its lectern. */
 @GameTest(template="empty",timeoutTicks=100) public static void theLabOfficeOpensAtTheLectern(GameTestHelper h){
  var t=shop(h,"laboratory");
  try{var pos=BuildingPlacement.at(t.e,t.shop,3,1,3);h.assertTrue(t.l.getBlockState(pos).is(Blocks.LECTERN),"The lab's desk is a lectern: "+t.l.getBlockState(pos));
   h.assertTrue(BuildingInteractions.station(t.l,pos)==3,"It opens the laboratory office");}
  finally{done(t);}
  h.succeed();
 }
 private static boolean open(BlockState s){return s.isAir()||s.getBlock() instanceof DoorBlock||s.getBlock() instanceof WallSignBlock&&s.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty();}
 private static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 private static Set<BlockPos> reach(Map<BlockPos,BlockState> m,int w,int d,BlockPos blocked){
  var air=Blocks.AIR.defaultBlockState();var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();
  java.util.function.Predicate<BlockPos> walk=p->p.getX()>=0&&p.getX()<w&&p.getZ()>=0&&p.getZ()<d&&!p.equals(blocked)&&open(m.getOrDefault(p,air))&&open(m.getOrDefault(p.above(),air))&&full(m.getOrDefault(p.below(),air));
  for(var c:m.entrySet())if(c.getValue().getBlock() instanceof DoorBlock&&c.getValue().getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER&&walk.test(c.getKey())&&seen.add(c.getKey()))queue.add(c.getKey());
  while(!queue.isEmpty()){var p=queue.poll();for(var dir:Direction.Plane.HORIZONTAL){var q=p.relative(dir);if(walk.test(q)&&seen.add(q))queue.add(q);}}
  return seen;
 }
 /** #11: the core cell of every design blocks no way from the doors to the rest of its floor. */
 @GameTest(template="empty",timeoutTicks=300) public static void theCoreCellKeepsTheWayFromTheDoor(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var type:TYPES){var design=type.equals("town_hall")?"town_hall_3":type;var d=BuildingBlueprints.design(design);var m=BuildingBlueprints.layout(design,BlockPos.ZERO);var core=LevelArchitecture.core(type);
   var free=new HashMap<>(m);free.remove(core);var before=reach(free,d.width(),d.depth(),null);var after=reach(m,d.width(),d.depth(),core);
   // A station is whatever stands inside the walls beside a floor cell the doors lead to: it must still have such a cell beside it.
   for(var c:m.entrySet()){var p=c.getKey();if(c.getValue().isAir()||p.equals(core)||p.getY()<1||p.getX()<1||p.getX()>d.width()-2||p.getZ()<1||p.getZ()>d.depth()-2)continue;
    if(Direction.Plane.HORIZONTAL.stream().anyMatch(dir->before.contains(p.relative(dir)))&&Direction.Plane.HORIZONTAL.stream().noneMatch(dir->after.contains(p.relative(dir))))problems.add(type+" core "+core.toShortString()+" cuts off "+BuiltInRegistries.BLOCK.getKey(c.getValue().getBlock()).getPath()+" at "+p.toShortString());}}
  h.assertTrue(problems.isEmpty(),"The core never cuts a station off from the doors: "+problems);h.succeed();
 }
 /** Owner, 2026-09-19: ring IV in the hall but no research of the branch's level IV — the order is refused and nothing sets grade IV;
  *  with the research done the level is ordered and built. */
 @GameTest(template="empty",timeoutTicks=300) public static void aRingWaitsForItsBranchResearch(GameTestHelper h){
  // AD-139: an old world's bakery (the restaurant's alias, its own 9x9 design) — the ring gate reads the restaurant's branch (baking.N).
  // The helper execute() below lays ops as planned, without the builder's handling of scaffold caps through hanging plaster, which the
  // restaurant's 11x9 front has; a live builder lays the restaurant whole (StyleBuilderGameTests restaurant@1).
  var t=shop(h,"bakery");
  try{
   research(t,"restaurant",3);var b=raise(t,3);var pos=core(t);chest(t).setItem(0,new ItemStack(VillageAstra.CORE_RINGS.get(4).get()));
   h.assertTrue(BuildingTiers.researchedGrade(t.l,t.e,"bakery")==3,"The bakery's branch is researched to III");
   h.assertTrue(BuildingTiers.order(t.l,t.e,b).equals("research"),"Ring IV in the hall, but without baking.4 the order is refused");
   // Kept at IV (a level given without its research): the builders' repair still sets nothing above grade III.
   t.e.settlement().raiseBuildingLevel(b.id(),4);var four=kept(t);var repair=BuildingRepairs.plan(t.l,t.e,four);
   h.assertTrue(repair.state()==null||at(repair.state(),pos).stream().noneMatch(op->grade(op)>3),"No operation sets grade IV without its research");
   research(t,"restaurant",4);
   var raw=repair.state()==null?null:BuildingRepairs.plan(t.l,t.e,four).state();
   h.assertTrue(raw!=null&&at(raw,pos).stream().anyMatch(op->grade(op)==4),"With baking.4 done the ring goes in");
   execute(h,t,raw,false);h.assertTrue(t.l.getBlockState(pos).equals(Cores.state("restaurant",4)),"Grade IV stands once researched");
  }finally{done(t);}
  h.succeed();
 }
}
