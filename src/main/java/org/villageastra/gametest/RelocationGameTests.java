package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-125: a building moved through the atlas — the old one taken apart into the project's cargo, the same one built at the new place,
 *  the contents of its chests and furnaces carried slot for slot, the record moved once. The runs below use the builder's own steps
 *  (Relocations.settle/effect/complete) without walking. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RelocationGameTests {
 private record F(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building b){}
 /** A hall (its chest only) and one building standing on its lot twelve blocks east of the hall, on flat grass over stone. */
 private static F fixture(GameTestHelper h,String type,int level,int rot){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<=44;x++)for(int z=-2;z<=36;z++){for(int y=-3;y<0;y++)l.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=22;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var b=new Settlement.Building(UUID.randomUUID(),type,12,0,0,rot,level);s.addBuilding(b);
  for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId(type,level),BuildingPlacement.origin(e,b),rot).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  if(BuildingOrders.HOUSING.contains(type))s.addHome(new Settlement.Home(b.id(),1,BuildingOrders.capacity(type),true));
  return new F(l,s,e,center,b);
 }
 private static BlockPos from(F f){return BuildingPlacement.origin(f.e,f.b);}
 private static BlockPos to(F f){return from(f).offset(0,0,16);}
 private static Settlement.Building now(F f){return f.s.buildings().stream().filter(x->x.id().equals(f.b.id())).findFirst().orElseThrow();}
 private static void done(F f){
  SettlementData.get(f.l.getServer()).remove(f.s.id());Relocations.forget(f.s.id());
  try{java.nio.file.Files.deleteIfExists(f.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+f.s.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 private static String key(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
 /** The ledger of one run: what the hall gave (funding), and what the cargo held at the end. */
 private static final class Run{final Map<String,Integer> funded=new HashMap<>();final List<ItemStack> leftovers=new ArrayList<>();int topUps;}
 /** The hall plays its part: every short item of the project's cost goes into the cargo, as the builder's funding walk would bring it. */
 private static void fund(CompoundTag state,Run run){
  var cost=state.getCompound("cost");
  for(var k:cost.getAllKeys()){int need=cost.getInt(k)-Relocations.held(state,k);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(k));
   while(need>0){int n=Math.min(need,item.getMaxStackSize());state.getList("cargo",Tag.TAG_COMPOUND).add(new ItemStack(item,n).save(new CompoundTag()));run.funded.merge(k,n,Integer::sum);need-=n;}}
  state.putBoolean("funded",true);
 }
 /** Runs the queued move to its end with the builder's own steps; the hook sees every operation first. */
 private static Run run(F f,CompoundTag state,java.util.function.IntConsumer hook,boolean keepCargo){
  var run=new Run();Runnable save=()->HallUpgradeGoal.store(f.l,f.s.id(),state);int steps=0;
  while(true){
   var ops=state.getList("ops",Tag.TAG_COMPOUND);int index=0;while(index<ops.size()&&ops.getCompound(index).getBoolean("done"))index++;
   if(!state.getBoolean("moved")&&(index>=ops.size()||ops.getCompound(index).getInt("phase")>=3)){
    if(!Relocations.complete(f.l,f.e,state))throw new IllegalStateException("Registration refused before operation "+index);state.putBoolean("moved",true);save.run();}
   if(index>=ops.size())break;
   if(!state.getBoolean("funded"))fund(state,run);
   hook.accept(index);
   var result=Relocations.effect(f.l,state,index,save);
   if(result.equals("missing_building_materials")){run.topUps++;continue;}
   if(!result.isEmpty()){var p=BlockPos.of(ops.getCompound(index).getLong("pos"));throw new IllegalStateException("Operation "+index+" "+result+" at "+p.toShortString()+" found "+f.l.getBlockState(p)+" planned "+ops.getCompound(index));}
   if(++steps>20000)throw new IllegalStateException("The move does not end");
  }
  for(var raw:state.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(!s.isEmpty())run.leftovers.add(s);}
  if(!keepCargo){state.put("cargo",new ListTag());if(!BuildingOrders.complete(f.l,f.e,state))throw new IllegalStateException("Completion refused");state.putBoolean("complete",true);save.run();}
  return run;
 }
 /** Every item counted once: leftover = funded + returns − consumed (+ contents of furniture the design has no place for), per item. */
 private static String ledger(CompoundTag state,Run run){
  var expected=new HashMap<String,Integer>(run.funded);
  for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(!t.getBoolean("done")||t.getBoolean("skipped"))continue;
   if(t.contains("item"))expected.merge(t.getString("item"),-1,Integer::sum);if(t.contains("return"))expected.merge(t.getString("return"),1,Integer::sum);}
  for(var raw:state.getList("stored",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var s=ItemStack.of(t.getCompound("item"));if(t.getBoolean("cargo"))expected.merge(key(s),s.getCount(),Integer::sum);}
  var actual=new HashMap<String,Integer>();for(var s:run.leftovers)actual.merge(key(s),s.getCount(),Integer::sum);
  expected.values().removeIf(v->v==0);return expected.equals(actual)?"":"expected "+new TreeMap<>(expected)+" found "+new TreeMap<>(actual);
 }
 /** Items lying on the fixture's own ground (the flat patch it laid, both lots on it), described for the failure message. */
 private static String drops(F f){var box=new AABB(f.center.offset(-2,-3,-2),f.center.offset(45,23,37));
  return f.l.getEntitiesOfClass(ItemEntity.class,box).stream().map(x->x.getItem()+"@"+x.blockPosition().subtract(f.center).toShortString()).toList().toString();}
 private static boolean noDrops(F f){return drops(f).equals("[]");}

 @GameTest(template="empty",timeoutTicks=200) public static void previewShowsNetCostAndBothSites(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var before=new ArrayList<BlockState>();for(int x=0;x<7;x++)for(int z=0;z<24;z++)for(int y=0;y<9;y++)before.add(f.l.getBlockState(from(f).offset(x,y,z)));
   var p=Relocations.preview(f.l,f.e,from(f),to(f),1);
   h.assertTrue(p.getBoolean("ok"),"A free lot sixteen blocks away takes the house: "+p.getString("reason")+" conflicts="+p.getInt("conflictCount")+" old="+p.getLongArray("oldConflicts").length);
   int full=0;var survey=BuildingOrders.survey(f.l,f.e,"home",1,to(f));for(var k:survey.state().getCompound("cost").getAllKeys())full+=survey.state().getCompound("cost").getInt(k);
   h.assertTrue(p.getInt("reused")>0&&p.getInt("items")<full,"The move pays only what it cannot reuse: reused="+p.getInt("reused")+" net="+p.getInt("items")+" new house="+full);
   var old=p.getLongArray("oldCells");h.assertTrue(old.length>50,"The old house is shown as taken apart: "+old.length);
   for(long raw:old){var c=BlockPos.of(raw).subtract(from(f));h.assertTrue(c.getX()>=0&&c.getX()<7&&c.getZ()>=0&&c.getZ()<7,"Old cells stay on the old lot: "+c);}
   h.assertTrue(p.getIntArray("future").length>=4*50&&p.getInt("seconds")>0&&p.getInt("downtime")>0&&p.getInt("downtime")<=p.getInt("seconds"),"Future volume and time: "+p.getIntArray("future").length/4+" "+p.getInt("seconds")+"s");
   var after=new ArrayList<BlockState>();for(int x=0;x<7;x++)for(int z=0;z<24;z++)for(int y=0;y<9;y++)after.add(f.l.getBlockState(from(f).offset(x,y,z)));
   h.assertTrue(before.equals(after)&&!HallUpgradeGoal.exists(f.l,f.s.id()),"The dry run changes no block and queues nothing");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void relocatedHomeKeepsIdLevelAndResidents(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);f.s.admit(r,f.b.id());var data=SettlementData.get(f.l.getServer());
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());int[] evicted={0};
   h.assertTrue(OwnershipEvents.disallowedPlacement(f.l,to(f).offset(3,1,3)),"The new lot is reserved while the move is pending");
   var run=run(f,state,i->{HousingMonitor.inspect(f.l.getServer(),data,f.e);if(!f.s.homes().stream().allMatch(Settlement.Home::usable)||!f.b.id().equals(r.home()))evicted[0]++;},false);
   h.assertTrue(evicted[0]==0,"Nobody is evicted while the house is apart: "+evicted[0]);
   h.assertTrue(BuildingOrders.complete(f.l,f.e,state)&&BuildingOrders.complete(f.l,f.e,state)&&f.s.buildings().size()==2,"Completion is idempotent");
   var b=now(f);var offset=to(f).subtract(f.center);
   h.assertTrue(b.x()==offset.getX()&&b.z()==offset.getZ()&&b.rotation()==1&&b.level()==1&&b.type().equals("home"),"Same building at the new place and turn: "+b);
   h.assertTrue(f.b.id().equals(r.home())&&f.s.homes().stream().allMatch(Settlement.Home::usable),"The dweller keeps the home");
   h.assertTrue(BuildingIntegrity.home(f.l,to(f),"home",1,BuildingOrders.capacity("home"))==BuildingIntegrity.Result.USABLE,"The house stands whole at its new place");
   var design=BuildingPlacement.layout("home",from(f),0);int left=0,floor=0,dirt=0;
   for(var cell:design.entrySet()){int y=cell.getKey().getY()-from(f).getY();var st=f.l.getBlockState(cell.getKey());
    if(y>=1&&!cell.getValue().isAir()&&st.getBlock()==cell.getValue().getBlock())left++;
    if(y==0&&!cell.getValue().isAir()){floor++;if(st.is(Blocks.DIRT))dirt++;}}
   h.assertTrue(left==0&&dirt==floor&&floor>0,"The old lot holds nothing of the house and its floor is dirt: left="+left+" dirt="+dirt+"/"+floor);
   h.assertTrue(OwnershipEvents.disallowedPlacement(f.l,to(f).offset(3,1,3))&&!OwnershipEvents.disallowedPlacement(f.l,from(f).offset(3,1,3)),"Protection moved with the house");
   h.assertTrue(noDrops(f),"Nothing was dropped on the ground: "+drops(f));
   var loaded=SettlementData.load(data.save(new CompoundTag())).entry(f.s.id()).settlement().buildings().stream().filter(x->x.id().equals(f.b.id())).findFirst().orElseThrow();
   h.assertTrue(loaded.x()==offset.getX()&&loaded.z()==offset.getZ()&&loaded.rotation()==1,"The new place survives a save and load");
   var ledger=ledger(state,run);h.assertTrue(ledger.isEmpty(),"Every item is counted once: "+ledger);
   h.succeed();
  }finally{done(f);}
 }
 /** Containers of a building's design, found in the world: design cell → the container. */
 private static Map<BlockPos,BlockPos> containers(F f,Settlement.Building b){
  var out=new LinkedHashMap<BlockPos,BlockPos>();var w=BuildingBlueprints.design(b.type()).width();var d=BuildingBlueprints.design(b.type()).depth();var o=BuildingPlacement.origin(f.e,b);
  for(var cell:BuildingPlacement.layout(BuildingTiers.layoutId(b.type(),b.level()),o,b.rotation()).entrySet())if(Relocations.container(cell.getValue())&&f.l.getBlockEntity(cell.getKey()) instanceof Container)
   out.put(BuildingPlacement.unturn(cell.getKey().subtract(o),w,d,b.rotation()),cell.getKey());
  return out;
 }
 /** The building's own stock chest (the design's (1,1,4)), not whichever container of the design comes first (a village-style home also has a barrel). */
 private static BlockPos stock(F f,Settlement.Building b){var p=containers(f,b).get(new BlockPos(1,1,4));if(p==null)throw new IllegalStateException("No stock chest at (1,1,4) of "+b.type());return p;}
 private static int count(F f,Collection<BlockPos> where,Item item){int n=0;for(var p:where)if(f.l.getBlockEntity(p) instanceof Container c)for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 @GameTest(template="empty",timeoutTicks=300) public static void containerContentsMoveSlotForSlot(GameTestHelper h){
  var f=fixture(h,"smithy",1,0);
  try{
   var found=containers(f,f.b);h.assertTrue(found.size()>=2,"The smithy has a chest and a furnace: "+found.keySet());
   var expected=new HashMap<BlockPos,Map<Integer,ItemStack>>();Item[] kinds={Items.DIAMOND,Items.APPLE,Items.RAW_IRON,Items.COAL,Items.IRON_INGOT,Items.EMERALD};int k=0;
   for(var c:found.entrySet()){var box=(Container)f.l.getBlockEntity(c.getValue());var slots=new HashMap<Integer,ItemStack>();
    for(int slot:new int[]{0,box.getContainerSize()-1,box.getContainerSize()/2}){if(slots.containsKey(slot))continue;var s=new ItemStack(kinds[k++%kinds.length],3+k);box.setItem(slot,s.copy());slots.put(slot,s);}
    expected.put(c.getKey(),slots);}
   int diamonds=count(f,found.values(),Items.DIAMOND)+count(f,List.of(f.center.offset(1,1,4)),Items.DIAMOND),apples=count(f,found.values(),Items.APPLE)+count(f,List.of(f.center.offset(1,1,4)),Items.APPLE);
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var run=run(f,state,i->{},false);
   var moved=now(f);var there=containers(f,moved);
   for(var c:expected.entrySet()){var at=there.get(c.getKey());h.assertTrue(at!=null,"The container of design cell "+c.getKey()+" stands again");var box=(Container)f.l.getBlockEntity(at);
    for(var s:c.getValue().entrySet())h.assertTrue(ItemStack.matches(box.getItem(s.getKey()),s.getValue()),"Slot "+s.getKey()+" of "+c.getKey()+" holds "+s.getValue()+": "+box.getItem(s.getKey()));}
   int d2=count(f,there.values(),Items.DIAMOND),a2=count(f,there.values(),Items.APPLE);for(var s:run.leftovers){if(s.is(Items.DIAMOND))d2+=s.getCount();if(s.is(Items.APPLE))a2+=s.getCount();}
   h.assertTrue(d2==diamonds&&a2==apples,"Contents are conserved exactly: diamonds "+d2+"/"+diamonds+" apples "+a2+"/"+apples);
   h.assertTrue(noDrops(f),"Nothing was dropped on the ground: "+drops(f));
   var ledger=ledger(state,run);h.assertTrue(ledger.isEmpty(),"Every item is counted once: "+ledger);
   h.succeed();
  }finally{done(f);}
 }
 private static int grade(F f,Settlement.Building b){var o=BuildingPlacement.origin(f.e,b);var size=BuildingPlacement.size(b.type(),b.rotation());int g=0;
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++)for(int y=0;y<16;y++){var s=f.l.getBlockState(o.offset(x,y,z));if(Cores.isCoreOf(s,b.type()))g=Math.max(g,Cores.grade(s));}return g;}
 @GameTest(template="empty",timeoutTicks=300) public static void levelAndCoreGradeKept(GameTestHelper h){
  var f=fixture(h,"carpentry",3,1);
  try{
   int level=BuildingTiers.level(f.l,f.e,f.b),grade=grade(f,f.b);h.assertTrue(grade==3,"The level-III carpentry stands with a grade-III core: "+grade);
   var core=org.villageastra.domain.CoreCatalog.NS+":"+org.villageastra.domain.CoreCatalog.path("carpentry");var ring=org.villageastra.domain.CoreCatalog.ringId(3);
   var why=Relocations.order(f.l,f.e,from(f),to(f),3);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var cost=state.getCompound("cost");
   h.assertTrue(!cost.contains(core)&&!cost.contains(ring)&&state.getInt("grade")==3&&state.getInt("keptLevel")==3,"The core and its ring come from the old building: "+cost);
   var run=run(f,state,i->{},false);var moved=now(f);
   h.assertTrue(moved.rotation()==3&&moved.level()==3&&grade(f,moved)==3,"The new carpentry stands at level III with a grade-III core: "+moved+" grade "+grade(f,moved));
   h.assertTrue(BuildingTiers.level(f.l,f.e,moved)==level,"It works at the same level: "+BuildingTiers.level(f.l,f.e,moved)+" before "+level);
   h.assertTrue(run.leftovers.stream().noneMatch(s->key(s).equals(core)||key(s).equals(ring)),"No core or ring is left over: "+run.leftovers);
   h.assertTrue(noDrops(f),"Nothing was dropped on the ground: "+drops(f));
   var ledger=ledger(state,run);h.assertTrue(ledger.isEmpty(),"Every item is counted once: "+ledger);
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void coreAboveResearchStillMoves(GameTestHelper h){
  var f=fixture(h,"carpentry",3,0);
  try{
   h.assertTrue(BuildingTiers.researchedGrade(f.l,f.e,"carpentry")<3,"The village has not researched grade III (AD-123 reworked the tree)");
   var p=Relocations.plan(f.l,f.e,f.b,to(f),0);h.assertTrue(p.ok(),"The move is planned: "+p.reason());
   int set=0;for(var raw:p.state().getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getInt("phase")!=2)continue;var after=HallConstructionPlan.step(t).after();
    if(after.getBlock() instanceof BuildingCoreBlock){h.assertTrue(HallUpgradeGoal.keepsGrade(p.state(),after),"The builder does not wait for research below the standing grade: "+after);set=Math.max(set,after.getValue(BuildingCoreBlock.GRADE));}}
   h.assertTrue(set==3,"The copy's core is planned back up to grade III: "+set);
   var other=p.state().copy();other.putBoolean("relocate",false);
   h.assertTrue(!HallUpgradeGoal.keepsGrade(other,org.villageastra.world.Cores.state("carpentry",3)),"An ordinary project still waits for the research");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void refusalsSayWhy(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var hall=f.s.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElseThrow();
   h.assertTrue(Relocations.quick(f.l,f.e,hall,false,false).equals("hall"),"The hall stays");
   for(var pair:List.of(List.of("farm","field"),List.of("forester","grove"),List.of("mine","shaft"),List.of("wall_tower","wall"),List.of("livestock","animals"),List.of("quarry","pit"))){
    var b=new Settlement.Building(UUID.randomUUID(),pair.get(0),-40,0,-40);f.s.addBuilding(b);
    h.assertTrue(Relocations.quick(f.l,f.e,b,false,false).equals(pair.get(1)),pair.get(0)+" is refused as "+pair.get(1)+": "+Relocations.quick(f.l,f.e,b,false,false));}
   h.assertTrue(Relocations.quick(f.l,f.e,f.b,true,false).equals("besieged")&&Relocations.quick(f.l,f.e,f.b,false,true).equals("busy"),"A siege and a busy crew refuse the move");
   h.assertTrue(Relocations.plan(f.l,f.e,f.b,f.center.offset(4000,0,0),0).reason().equals("outside"),"Outside the atlas area");
   h.assertTrue(Relocations.plan(f.l,f.e,f.b,from(f),0).reason().equals("same_place"),"The same place and turn");
   var close=Relocations.plan(f.l,f.e,f.b,from(f).offset(3,0,0),0);h.assertTrue(close.reason().equals("too_close")&&!close.conflicts().isEmpty(),"A place on the old building's buffer: "+close.reason());
   h.assertTrue(Relocations.plan(f.l,f.e,f.b,to(f),4).reason().equals("rotation"),"Rotation 4");
   h.assertTrue(Relocations.order(f.l,f.e,from(f).offset(1,0,1),to(f),0).equals("building"),"No building at the given old place");
   h.assertTrue(!f.s.governance().canManage(UUID.randomUUID(),f.s.governance().epoch()),"Only the mayor gives map orders (the network handler answers «mayor»)");
   f.s.markLegacyArchitecture(f.b.id());h.assertTrue(Relocations.refusal(f.l,f.e,f.b).equals("legacy"),"Old architecture waits for its rebuild: "+Relocations.refusal(f.l,f.e,f.b));f.s.finishArchitectureMigration(f.b.id());
   var project=BuildingOrders.survey(f.l,f.e,"home",0,to(f)).state();HallUpgradeGoal.enqueue(f.l,f.e,project);
   h.assertTrue(Relocations.plan(f.l,f.e,f.b,to(f),0).reason().equals("busy"),"Another project holds the crew");HallUpgradeGoal.drop(f.l,f.s.id());
   int solid=0;for(var cell:BuildingPlacement.layout("home",from(f),0).values())if(!cell.isAir())solid++;int breaks=(int)Math.ceil(solid*(1-Relocations.WHOLE))+5;
   int broken=0;for(var cell:BuildingPlacement.layout("home",from(f),0).entrySet())if(!cell.getValue().isAir()&&cell.getKey().getY()>from(f).getY()&&broken<breaks){f.l.setBlock(cell.getKey(),Blocks.AIR.defaultBlockState(),2);broken++;}
   h.assertTrue(Relocations.refusal(f.l,f.e,f.b).equals("outdated"),"A damaged house is repaired first: "+Relocations.refusal(f.l,f.e,f.b));
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void quarryAndStarterRefused(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var quarry=new Settlement.Building(UUID.randomUUID(),"quarry",-40,0,-40);f.s.addBuilding(quarry);
   h.assertTrue(Relocations.refusal(f.l,f.e,quarry).equals("pit")&&!Relocations.MOVABLE.contains("quarry"),"A quarry keeps its pit");
   var starter=new Settlement.Building(Settlement.childId(f.s.id(),"house/0"),"home",10,0,0);
   h.assertTrue(Relocations.quick(f.l,f.e,starter,false,false).isEmpty(),"Not registered yet: nothing to protect");
   f.s.addBuilding(starter);h.assertTrue(Relocations.quick(f.l,f.e,starter,false,false).equals("starter"),"The starter house holds the old village's protection");
   var view=Relocations.view(f.l,f.e).getCompound("refusals");h.assertTrue(view.getString(starter.id().toString()).equals("starter")&&view.getString(quarry.id().toString()).equals("pit")&&!view.contains(f.b.id().toString()),"The atlas carries the cheap reasons");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void crashReplayIsExactlyOnce(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var chest=containers(f,f.b);h.assertTrue(!chest.isEmpty(),"The house has a chest");var box=(Container)f.l.getBlockEntity(chest.values().iterator().next());
   box.setItem(0,new ItemStack(Items.DIAMOND,5));box.setItem(7,new ItemStack(Items.BREAD,9));
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   final CompoundTag[] state={HallUpgradeGoal.inspect(f.l,f.s.id())};var ops=state[0].getList("ops",Tag.TAG_COMPOUND);int pack=-1,plain=-1;
   for(int i=0;i<ops.size();i++){var t=ops.getCompound(i);if(pack<0&&t.getBoolean("pack"))pack=i;if(plain<0&&t.getBoolean("dismantle")&&t.contains("return")&&!t.getBoolean("pack"))plain=i;}
   h.assertTrue(pack>=0&&plain>=0&&pack!=plain,"Both kinds of operation are planned");
   var first=new Run();fund(state[0],first);Runnable save=()->HallUpgradeGoal.store(f.l,f.s.id(),state[0]);save.run();
   // The crash: the operation commits in the world, and the project record written before it is what the server finds again.
   for(int at:new int[]{Math.min(pack,plain),Math.max(pack,plain)}){
    for(int i=0;i<at;i++){if(state[0].getList("ops",Tag.TAG_COMPOUND).getCompound(i).getBoolean("done"))continue;var r=Relocations.effect(f.l,state[0],i,save);h.assertTrue(r.isEmpty(),"Operation "+i+" before the crash: "+r);}
    var before=state[0].copy();var r=Relocations.effect(f.l,state[0],at,save);
    h.assertTrue(r.isEmpty()&&state[0].getList("ops",Tag.TAG_COMPOUND).getCompound(at).getBoolean("done"),"The crashing operation commits: "+r);
    state[0]=before;save.run();h.assertTrue(!state[0].getList("ops",Tag.TAG_COMPOUND).getCompound(at).getBoolean("done"),"The record forgot the operation");}
   var run=run(f,state[0],i->{},false);first.funded.forEach((k,v)->run.funded.merge(k,v,Integer::sum));
   int packed=0;for(var raw:state[0].getList("stored",Tag.TAG_COMPOUND))packed+=ItemStack.of(((CompoundTag)raw).getCompound("item")).getCount();
   h.assertTrue(packed==14&&state[0].getList("stored",Tag.TAG_COMPOUND).size()==2,"The chest was packed once: "+state[0].getList("stored",Tag.TAG_COMPOUND));
   var there=containers(f,now(f));var nb=(Container)f.l.getBlockEntity(there.values().iterator().next());
   h.assertTrue(nb.getItem(0).is(Items.DIAMOND)&&nb.getItem(0).getCount()==5&&nb.getItem(7).is(Items.BREAD)&&nb.getItem(7).getCount()==9,"The contents are back once: "+nb.getItem(0)+" "+nb.getItem(7));
   var ledger=ledger(state[0],run);h.assertTrue(ledger.isEmpty(),"The return of the replayed block is counted once: "+ledger);
   // recoverCompleted: a record that says "moved" while the settlement still has the building at the old place moves it, once.
   var offset=from(f).subtract(f.center);f.s.moveBuilding(f.b.id(),offset.getX(),offset.getY(),offset.getZ(),0);HallUpgradeGoal.store(f.l,f.s.id(),state[0]);
   h.assertTrue(HallUpgradeGoal.recoverCompleted(f.l,f.e)&&now(f).rotation()==1,"Recovery moves the record once");
   h.assertTrue(!HallUpgradeGoal.recoverCompleted(f.l,f.e),"A second recovery changes nothing");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void driftAndTopUp(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var ops=state.getList("ops",Tag.TAG_COMPOUND);var cost=state.getCompound("cost");int gone=-1;
   // A wall block the copy needs and that the quote counted on getting back: a player takes it before the builders do.
   for(int i=0;i<ops.size()&&gone<0;i++){var t=ops.getCompound(i);if(t.getInt("phase")==1&&t.getBoolean("dismantle")&&t.contains("return")&&!cost.contains(t.getString("return"))&&HallConstructionPlan.step(t).before().isCollisionShapeFullBlock(f.l,BlockPos.of(t.getLong("pos"))))gone=i;}
   h.assertTrue(gone>=0,"A fully reused wall block is planned");var cell=BlockPos.of(ops.getCompound(gone).getLong("pos"));var item=ops.getCompound(gone).getString("return");
   f.l.setBlock(cell,Blocks.AIR.defaultBlockState(),2);
   var run=run(f,state,i->{},false);var op=state.getList("ops",Tag.TAG_COMPOUND).getCompound(gone);
   h.assertTrue(op.getBoolean("done")&&op.getBoolean("skipped"),"The taken block is skipped, not waited for");
   h.assertTrue(run.topUps>0&&run.funded.getOrDefault(item,0)>0,"The build lacked "+item+" and drew it from the hall: topUps="+run.topUps+" funded="+run.funded);
   h.assertTrue(now(f).rotation()==1,"The move still ends");
   var ledger=ledger(state,run);h.assertTrue(ledger.isEmpty(),"Every item is counted once: "+ledger);
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void phaseBarrierHoldsHandyWork(GameTestHelper h){
  var ops=new ListTag();for(int phase:new int[]{1,1,2,2}){var t=new CompoundTag();t.putInt("phase",phase);ops.add(t);}
  ops.getCompound(1).putLong("retry",Long.MAX_VALUE);
  h.assertTrue(HallUpgradeGoal.eligible(ops,0,1)&&!HallUpgradeGoal.eligible(ops,0,2)&&!HallUpgradeGoal.eligible(ops,1,3),"A building operation waits while the old building is still being taken apart");
  var plain=new ListTag();for(int i=0;i<3;i++)plain.add(new CompoundTag());
  h.assertTrue(HallUpgradeGoal.eligible(plain,0,2)&&HallUpgradeGoal.eligible(ops,2,3),"Projects without phases are unchanged");
  var f=fixture(h,"home",1,0);
  try{
   var p=Relocations.plan(f.l,f.e,f.b,to(f),1);h.assertTrue(p.ok(),"Planned: "+p.reason());var list=p.state().getList("ops",Tag.TAG_COMPOUND);int first=-1,last=-1;
   for(int i=0;i<list.size();i++){int phase=list.getCompound(i).getInt("phase");if(phase==1&&first<0)first=i;if(i>0)h.assertTrue(phase>=list.getCompound(i-1).getInt("phase"),"Phases are in order");if(phase==2)last=i;}
   h.assertTrue(first>=0&&!HallUpgradeGoal.eligible(list,first,last),"No phase-2 cell is handy work during the dismantling");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void atlasCarriesRelocation(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var farm=new Settlement.Building(UUID.randomUUID(),"farm",-40,0,-40);f.s.addBuilding(farm);
   var view=Atlas.view(f.l,f.e,-1,0,0).getCompound("relocation");
   h.assertTrue(view.getCompound("refusals").getString(farm.id().toString()).equals("field")&&!view.contains("active"),"The farm is refused on the map, no move yet");

   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var r0=new Run();fund(state,r0);Runnable save=()->HallUpgradeGoal.store(f.l,f.s.id(),state);
   for(int i=0;i<10;i++)h.assertTrue(Relocations.effect(f.l,state,i,save).isEmpty(),"Operation "+i);
   var active=Atlas.view(f.l,f.e,-1,0,0).getCompound("relocation").getCompound("active");
   h.assertTrue(active.getUUID("building").equals(f.b.id())&&active.getInt("done")==10&&active.getInt("total")==state.getList("ops",Tag.TAG_COMPOUND).size()&&active.getInt("phase")<=1&&active.getLong("origin")==to(f).asLong(),"The map shows the move with its phase and progress: "+active);
   h.assertTrue(Atlas.view(f.l,f.e,-1,0,0).getCompound("relocation").getCompound("refusals").getString(f.b.id().toString()).equals("busy"),"While it moves nothing else moves");
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void litFurnaceAndOpenDoorDrift(GameTestHelper h){
  var f=fixture(h,"smithy",1,0);
  try{
   BlockPos furnace=null,door=null;
   for(var cell:BuildingPlacement.layout("smithy",from(f),0).entrySet()){var s=f.l.getBlockState(cell.getKey());
    if(furnace==null&&s.getBlock() instanceof AbstractFurnaceBlock)furnace=cell.getKey();if(door==null&&s.getBlock() instanceof DoorBlock&&s.getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)door=cell.getKey();}
   h.assertTrue(furnace!=null&&door!=null,"The smithy has a furnace and a door");
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   // After the order the furnace is lit with its input and fuel, and a resident opens the door: state changes, not other blocks.
   ((Container)f.l.getBlockEntity(furnace)).setItem(0,new ItemStack(Items.RAW_IRON,4));((Container)f.l.getBlockEntity(furnace)).setItem(1,new ItemStack(Items.COAL,2));
   f.l.setBlock(furnace,f.l.getBlockState(furnace).setValue(AbstractFurnaceBlock.LIT,true),2);
   f.l.setBlock(door,f.l.getBlockState(door).setValue(DoorBlock.OPEN,true),2);f.l.setBlock(door.above(),f.l.getBlockState(door.above()).setValue(DoorBlock.OPEN,true),2);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var run=run(f,state,i->{},false);
   int skipped=0;for(var raw:state.getList("ops",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getBoolean("skipped"))skipped++;
   h.assertTrue(skipped==0,"No cell was skipped for a lit furnace or an open door: "+skipped);
   h.assertTrue(f.l.getBlockState(furnace).isAir()&&f.l.getBlockState(door).isAir(),"The furnace and the door left the old lot");
   var there=containers(f,now(f));int iron=count(f,there.values(),Items.RAW_IRON),coal=count(f,there.values(),Items.COAL);
   for(var s:run.leftovers){if(s.is(Items.RAW_IRON))iron+=s.getCount();if(s.is(Items.COAL))coal+=s.getCount();}
   h.assertTrue(iron==4&&coal==2,"The furnace's contents came along: iron "+iron+" coal "+coal);
   h.assertTrue(noDrops(f),"Nothing was dropped on the ground: "+drops(f));
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void refilledSlotIsRepacked(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var chestPos=stock(f,f.b);var box=(Container)f.l.getBlockEntity(chestPos);box.setItem(3,new ItemStack(Items.DIAMOND,2));
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int pack=-1;for(int i=0;i<ops.size()&&pack<0;i++)if(ops.getCompound(i).getBoolean("pack")&&ops.getCompound(i).getLong("pos")==chestPos.asLong())pack=i;
   final int at=pack;boolean[] refilled={false};Runnable save=()->HallUpgradeGoal.store(f.l,f.s.id(),state);
   var run=run(f,state,i->{
    if(i!=at||refilled[0])return;
    // The chest is packed, then a worker puts something back into the emptied slot before the removal commits.
    Relocations.pack(f.l,state,i,save);h.assertTrue(((Container)f.l.getBlockEntity(chestPos)).isEmpty(),"Packed");
    ((Container)f.l.getBlockEntity(chestPos)).setItem(3,new ItemStack(Items.EMERALD,6));refilled[0]=true;
    h.assertTrue(Relocations.effect(f.l,state,i,save).equals("changed_target")&&!f.l.getBlockState(chestPos).isAir(),"A refilled chest is not removed (it would spill)");},false);
   h.assertTrue(refilled[0]&&state.getList("ops",Tag.TAG_COMPOUND).getCompound(pack).getInt("attempt")==1,"The removal was tried again after packing once more");
   var there=containers(f,now(f)).values();int diamonds=count(f,there,Items.DIAMOND),emeralds=count(f,there,Items.EMERALD);
   for(var s:run.leftovers){if(s.is(Items.DIAMOND))diamonds+=s.getCount();if(s.is(Items.EMERALD))emeralds+=s.getCount();}
   h.assertTrue(diamonds==2&&emeralds==6&&noDrops(f),"Both stacks moved and nothing spilled: diamonds "+diamonds+" emeralds "+emeralds+" drops "+drops(f));
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void fullHallStillRegisters(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var hall=(Container)f.l.getBlockEntity(f.center.offset(1,1,4));for(int i=0;i<hall.getContainerSize();i++)hall.setItem(i,new ItemStack(Items.STONE,64));
   var own=(Container)f.l.getBlockEntity(stock(f,f.b));
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var run=run(f,state,i->{},true);
   h.assertTrue(state.getBoolean("moved")&&now(f).rotation()==1&&!run.leftovers.isEmpty(),"The house is registered at its new place before the leftovers are carried: "+run.leftovers.size());
   // The new house's own chest takes what the full hall cannot; once it is full too, a minute later one cargo chest beside the hall.
   var mine=(Container)f.l.getBlockEntity(stock(f,now(f)));for(int i=0;i<mine.getContainerSize();i++)mine.setItem(i,new ItemStack(Items.STONE,64));
   int slot=0;var cargo=state.getList("cargo",Tag.TAG_COMPOUND);while(ItemStack.of(cargo.getCompound(slot)).isEmpty())slot++;
   h.assertTrue(Relocations.overflow(f.l,f.e,state,slot)&&state.contains("fullSince")&&!Relocations.overflow(f.l,f.e,state,slot),"A full hall waits a minute first");
   state.putLong("fullSince",f.l.getGameTime()-1200);
   h.assertTrue(Relocations.overflow(f.l,f.e,state,slot),"Then the leftovers go into one cargo chest");
   boolean empty=true;for(var raw:state.getList("cargo",Tag.TAG_COMPOUND))if(!ItemStack.of((CompoundTag)raw).isEmpty())empty=false;
   boolean chest=false;for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(f.l.getBlockState(f.center.offset(1+dx,1,4+dz)).is(VillageAstra.CARGO_CHEST.get()))chest=true;
   h.assertTrue(empty&&chest&&noDrops(f),"Nothing is lost or dropped: cargo empty="+empty+" chest="+chest+" drops "+drops(f));
   for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){var p=f.center.offset(1+dx,1,4+dz);if(f.l.getBlockState(p).is(VillageAstra.CARGO_CHEST.get())){((Container)f.l.getBlockEntity(p)).clearContent();f.l.setBlock(p,Blocks.AIR.defaultBlockState(),2);}}
   own.clearContent();
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void smeltingJobHoldsTheMove(GameTestHelper h){
  var f=fixture(h,"smithy",1,0);var file=Workshops.path(f.l,f.b.id());
  try{
   BlockPos furnace=null;for(var cell:BuildingPlacement.layout("smithy",from(f),0).entrySet())if(f.l.getBlockState(cell.getKey()).getBlock() instanceof AbstractFurnaceBlock)furnace=cell.getKey();
   h.assertTrue(furnace!=null&&Relocations.refusal(f.l,f.e,f.b).isEmpty(),"The smithy may move while its furnace is idle");
   var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putString("stage","smelt_wait");job.putLong("furnace",furnace.asLong());NbtRecord.write(file,job);
   h.assertTrue(Relocations.refusal(f.l,f.e,f.b).equals("working"),"A smelting job holds the exact furnace: the move waits for it (C11)");
   job.putString("stage","idle");NbtRecord.write(file,job);h.assertTrue(Relocations.refusal(f.l,f.e,f.b).isEmpty(),"An idle job does not");
   h.succeed();
  }finally{try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}done(f);}
 }

 /** Runs the move's operations one by one up to (not including) {@code end}, the hall paying what the cargo lacks, as the builder does. */
 private static void upTo(GameTestHelper h,CompoundTag state,F f,int end,Run run){Runnable save=()->HallUpgradeGoal.store(f.l,f.s.id(),state);var ops=state.getList("ops",Tag.TAG_COMPOUND);
  for(int i=0,guard=0;i<end;i++){if(ops.getCompound(i).getBoolean("done"))continue;if(!state.getBoolean("funded"))fund(state,run);var r=Relocations.effect(f.l,state,i,save);
   if(r.equals("missing_building_materials")&&++guard<200){run.topUps++;i--;continue;}h.assertTrue(r.isEmpty(),"Operation "+i+": "+r);}}
 @GameTest(template="empty",timeoutTicks=300) public static void lostCellOfTheCopyIsBuiltAgain(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int lot=ops.size();
   for(int i=0;i<ops.size()&&lot==ops.size();i++)if(ops.getCompound(i).getInt("phase")==3)lot=i;
   upTo(h,state,f,lot,new Run());
   // A creeper takes a wall block of the finished copy before the builders register it: the cell is built again, not waited for forever.
   int lost=-1;for(int i=lot-1;i>=0&&lost<0;i--){var t=ops.getCompound(i);var st=HallConstructionPlan.step(t);
    if(t.getInt("phase")!=2||!t.contains("item")||t.contains("return")||!st.after().isCollisionShapeFullBlock(f.l,st.pos())||st.after().hasBlockEntity())continue;
    boolean later=false;for(int j=i+1;j<ops.size();j++)if(ops.getCompound(j).getLong("pos")==t.getLong("pos"))later=true;if(!later)lost=i;}
   h.assertTrue(lost>=0,"A wall block of the copy is planned");var cell=BlockPos.of(ops.getCompound(lost).getLong("pos"));var wall=HallConstructionPlan.step(ops.getCompound(lost)).after();
   f.l.setBlock(cell,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(!Relocations.complete(f.l,f.e,state)&&now(f).x()==f.b.x()&&now(f).z()==f.b.z(),"A copy with a hole is not registered");
   var op=ops.getCompound(lost);
   h.assertTrue(!op.getBoolean("done")&&op.getInt("attempt")==1&&state.getInt("index")<=lost,"The lost cell's operation goes again under a new id: "+op);
   var rest=run(f,state,i->{},false);
   h.assertTrue(BuildingRepairs.present(f.l.getBlockState(cell),wall)&&now(f).rotation()==1,"The cell stands again and the house is registered at its new place: "+f.l.getBlockState(cell));
   h.assertTrue(noDrops(f),"Nothing was dropped on the ground: "+drops(f));
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void leftoverChestIsBookedOnceAfterACrash(GameTestHelper h){
  var f=fixture(h,"home",1,0);
  try{
   var hall=(Container)f.l.getBlockEntity(f.center.offset(1,1,4));for(int i=0;i<hall.getContainerSize();i++)hall.setItem(i,new ItemStack(Items.STONE,64));
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());run(f,state,i->{},true);
   var own=(Container)f.l.getBlockEntity(stock(f,now(f)));for(int i=0;i<own.getContainerSize();i++)own.setItem(i,new ItemStack(Items.STONE,64));
   int slot=0;var cargo=state.getList("cargo",Tag.TAG_COMPOUND);while(ItemStack.of(cargo.getCompound(slot)).isEmpty())slot++;
   state.putLong("fullSince",f.l.getGameTime()-1200);var before=state.copy();
   h.assertTrue(Relocations.overflow(f.l,f.e,state,slot),"The leftovers go into one cargo chest");
   // The crash: the chest stands in the world, the record is the one written before it. The spot it took is not free any more.
   h.assertTrue(Relocations.overflow(f.l,f.e,before,slot),"The chest already put down is booked, not put down a second time");
   int chests=0;for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(f.l.getBlockState(f.center.offset(1+dx,1,4+dz)).is(VillageAstra.CARGO_CHEST.get()))chests++;
   boolean empty=true;for(var raw:before.getList("cargo",Tag.TAG_COMPOUND))if(!ItemStack.of((CompoundTag)raw).isEmpty())empty=false;
   h.assertTrue(chests==1&&empty&&before.getInt("leftovers")==1,"One chest, the cargo emptied once: chests="+chests+" empty="+empty+" leftovers="+before.getInt("leftovers"));
   for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){var p=f.center.offset(1+dx,1,4+dz);if(f.l.getBlockState(p).is(VillageAstra.CARGO_CHEST.get())){((Container)f.l.getBlockEntity(p)).clearContent();f.l.setBlock(p,Blocks.AIR.defaultBlockState(),2);}}
   own.clearContent();hall.clearContent();
   h.succeed();
  }finally{done(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void smeltingJobKeepsItsFurnaceDuringTheMove(GameTestHelper h){
  var f=fixture(h,"smithy",1,0);var file=Workshops.path(f.l,f.b.id());
  try{
   var size=BuildingPlacement.size("smithy",0);var o=from(f);
   java.util.function.Predicate<BlockPos> inside=p->p!=null&&p.getX()>=o.getX()&&p.getX()<o.getX()+size[0]&&p.getZ()>=o.getZ()&&p.getZ()<o.getZ()+size[1];
   var free=NaturalFurnace.position(f.l,f.e,new CompoundTag());h.assertTrue(inside.test(free),"Before the order a job may take the smithy's furnace: "+free);
   var why=Relocations.order(f.l,f.e,from(f),to(f),1);h.assertTrue(why.isEmpty(),"The move is ordered: "+why);
   h.assertTrue(!inside.test(NaturalFurnace.position(f.l,f.e,new CompoundTag())),"No new job takes a furnace of the building being moved");
   // A job that took the furnace just before the order: the furnace is not taken apart under it.
   var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putString("stage","smelt_wait");job.putLong("furnace",free.asLong());NbtRecord.write(file,job);
   var state=HallUpgradeGoal.inspect(f.l,f.s.id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int at=-1;
   for(int i=0;i<ops.size()&&at<0;i++)if(ops.getCompound(i).getBoolean("pack")&&ops.getCompound(i).getLong("pos")==free.asLong())at=i;
   h.assertTrue(at>=0,"The furnace is packed and taken apart");var run=new Run();upTo(h,state,f,at,run);
   h.assertTrue(Relocations.effect(f.l,state,at,()->HallUpgradeGoal.store(f.l,f.s.id(),state)).equals("relocation_furnace_busy")&&f.l.getBlockState(free).getBlock() instanceof AbstractFurnaceBlock,"The furnace waits for its job");
   job.putString("stage","idle");NbtRecord.write(file,job);
   run(f,state,i->{},false);h.assertTrue(now(f).rotation()==1&&f.l.getBlockState(free).isAir(),"After the job the move goes on to its end");
   h.succeed();
  }finally{try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}done(f);}
 }
}
