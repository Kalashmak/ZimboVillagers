package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-111 (AD-104 P3): a far village's pantry is read from its real chest, touch-loaded at border level for a moment under a per-tick budget.
 *  Every fixture stands thousands of blocks from the test grid, each test on its own rows of chunks, and waits until vanilla has really let
 *  its chunks go; the shared budget is reset right before each asserted call and exhausted only inside one synchronous block. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RemotePantryGameTests {
 /** Outside the complete regression grid and its expedition fixtures; each test takes its own dz, 512 blocks from the next. */
 private static final int FAR_X=1_048_576,UNLOAD_WAIT=200;
 private record Far(ServerLevel l,SettlementData.Entry e,Settlement.Building hall,Settlement.Building house,BlockPos chest){
  UUID id(){return e.settlement().id();}
  OwnedChestEntity pantry(){return LogisticsRoutes.chest(l,e,hall);}
  int bread(){var c=pantry();return c==null?-1:c.countItem(Items.BREAD);}
  Resident first(){return e.settlement().residents().iterator().next();}
  boolean loaded(){return l.hasChunkAt(chest);}
  BlockPos spawn(){return BuildingPlacement.at(e,house,2,1,2);}
  /** Every place a test may touch: the pantry, the hall centre, the house a child would be born into, and what the caravan pass reads. */
  List<BlockPos> points(){var ps=new ArrayList<BlockPos>(List.of(chest,e.center(),spawn()));ps.addAll(Caravans.touchPoints(e));return ps;}
  void close(){
   var server=l.getServer();
   for(var t:List.copyOf(Caravans.contracts(server))){boolean mine=(t.hasUUID("source")&&t.getUUID("source").equals(id()))||(t.hasUUID("destination")&&t.getUUID("destination").equals(id()));
    if(mine&&!t.getString("state").equals(Caravans.CLOSED)){t.putString("state",Caravans.CLOSED);t.putBoolean("needsEntity",false);Caravans.update(server,t);}}
   // A raid record a test wrote for this village goes with it (and the record cache, which only mirrors the files).
   try{if(java.nio.file.Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve("data/astra-raids/"+id()+".bin")))Raids.clear();}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
   SettlementData.get(server).remove(id());TouchLoad.forget(id());
  }
 }
 private static Far far(GameTestHelper h,int dx,int dz,int adults,boolean relevant){
  var l=h.getLevel();var center=new BlockPos(FAR_X+dx,h.absolutePos(new BlockPos(2,3,2)).getY(),dz);var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var house=new Settlement.Building(Settlement.childId(s.id(),"building/home"),"home",6,0,0);s.addBuilding(house);var home=new Settlement.Home(house.id(),1,adults+2,true);s.addHome(home);
  for(int i=0;i<adults;i++){var r=new Resident(Settlement.childId(s.id(),"adult/"+i),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());s.resident(r.id()).ate(0);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var chest=LogisticsRoutes.position(e,hall);
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setBlock(chest.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
  // A chest left by an earlier run in this world is emptied first, so replacing it drops nothing and the new one starts empty and unowned.
  if(l.getBlockEntity(chest) instanceof Container old)old.clearContent();
  l.setBlock(chest,Blocks.AIR.defaultBlockState(),2);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  SettlementData.get(l.getServer()).add(e);
  if(relevant)PropertyLedger.get(l.getServer()).gift(s.id(),UUID.randomUUID(),10);TouchLoad.forget(s.id());
  return new Far(l,e,hall,house,chest);
 }
 /** Runs {@code then} once no chunk of these villages is loaded; fails loudly rather than passing a test that never saw a far pantry. */
 private static void whenUnloaded(GameTestHelper h,List<Far> fs,Runnable then){poll(h,fs,then,0);}
 private static void poll(GameTestHelper h,List<Far> fs,Runnable then,int waited){
  var held=fs.stream().flatMap(f->f.points().stream().filter(f.l::hasChunkAt)).toList();
  if(held.isEmpty()){guard(fs,then);return;}
  if(waited>=UNLOAD_WAIT){close(fs);throw new GameTestAssertException("A far chunk stayed loaded for "+UNLOAD_WAIT+" ticks, so nothing remote could be tested: "+held);}
  h.runAfterDelay(1,()->poll(h,fs,then,waited+1));
 }
 private static void guard(List<Far> fs,Runnable body){try{body.run();}catch(RuntimeException|Error failure){close(fs);throw failure;}}
 private static void close(List<Far> fs){for(var f:fs)f.close();}
 private static boolean committed(ServerLevel l,UUID id){var p=l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-journal").resolve(id+".bin");return java.nio.file.Files.exists(p)&&NbtRecord.read(p).getBoolean("committed");}
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void farVillageEatsItsRealBreadOnceAtTheDueTick(GameTestHelper h){
  var one=far(h,0,0,1,true);var six=far(h,0,512,6,true);var fs=List.of(one,six);
  one.pantry().setItem(0,new ItemStack(Items.BREAD,10));six.pantry().setItem(0,new ItemStack(Items.BREAD,10));
  whenUnloaded(h,fs,()->{
   var l=one.l;long due=Population.MEAL_INTERVAL;var r=one.first();
   TouchLoad.resetTick();long loads=TouchLoad.stats().loads();Population.serve(l,one.e,due);long spent=TouchLoad.stats().loads()-loads;
   var cp=new ChunkPos(one.chest);
   h.assertTrue(one.loaded()&&!l.shouldTickBlocksAt(cp.toLong())&&!TouchLoad.ticking(l,one.chest),"The far pantry chunk is loaded for the meal and stays asleep (border level)");
   h.assertTrue(spent==1,"One pantry costs one chunk load: "+spent);
   h.assertTrue(one.bread()==9&&r.lastMeal()==due&&r.missedMeals()==0,"The far resident ate one real loaf at its due tick: bread="+one.bread()+" lastMeal="+r.lastMeal()+" missed="+r.missedMeals());
   TouchLoad.resetTick();Population.serve(l,one.e,due);
   h.assertTrue(one.bread()==9&&r.lastMeal()==due,"A second call in the same tick eats nothing: bread="+one.bread());
   TouchLoad.resetTick();Population.serve(l,six.e,due);
   h.assertTrue(six.bread()==4&&six.e.settlement().residents().stream().allMatch(x->x.lastMeal()==due&&x.missedMeals()==0),"Six far residents eat six loaves in one batch: bread="+six.bread());
   for(var x:six.e.settlement().residents()){var id=Settlement.childId(x.id(),"meal/"+due+"/0");
    h.assertTrue(committed(l,id),"Each meal of the batch is committed after its one flush: "+id);
    h.assertTrue(WorldJournal.recoverAmount(l,id).getCount()==1,"Replaying a committed meal returns its one loaf: "+id);}
   h.assertTrue(six.bread()==4,"Replaying the batch takes nothing more: bread="+six.bread());
   close(fs);h.succeed();
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void spentBudgetDefersTheMealToItsDueTick(GameTestHelper h){
  var f=far(h,0,1024,1,true);var fs=List.of(f);f.pantry().setItem(0,new ItemStack(Items.BREAD,10));
  whenUnloaded(h,fs,()->{
   var l=f.l;var r=f.first();long due=Population.MEAL_INTERVAL;
   // One synchronous block: exhaust, the refused call and the stats delta, then the budget is whole again for every other test in this tick.
   TouchLoad.exhaust(l.getServer());var before=TouchLoad.stats();Population.serve(l,f.e,due+600);var after=TouchLoad.stats();TouchLoad.resetTick();
   h.assertTrue(after.deferred()-before.deferred()==1&&after.loads()==before.loads()&&!f.loaded(),"A spent budget refuses the touch and loads nothing: deferred="+(after.deferred()-before.deferred()));
   h.assertTrue(r.lastMeal()==0&&r.missedMeals()==0,"The refused meal waits: nothing eaten, nothing missed, lastMeal="+r.lastMeal());
   h.runAfterDelay(1,()->guard(fs,()->{
    TouchLoad.resetTick();Population.serve(l,f.e,due+620);
    h.assertTrue(f.bread()==9&&r.lastMeal()==due&&r.missedMeals()==0,"Next tick the deferred meal is eaten at its own due tick: bread="+f.bread()+" lastMeal="+r.lastMeal());
    close(fs);h.succeed();
   }));
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void emptyRemotePantryStillStarves(GameTestHelper h){
  var f=far(h,0,1536,1,true);var fs=List.of(f);
  whenUnloaded(h,fs,()->{
   var l=f.l;var r=f.first();
   for(int k=1;k<=Population.DEATH;k++){
    TouchLoad.resetTick();Population.serve(l,f.e,k*Population.MEAL_INTERVAL);
    h.assertTrue(f.loaded()&&f.bread()==0,"The far pantry was really read and is empty: meal "+k);
    h.assertTrue(r.missedMeals()==k&&r.lastMeal()==k*Population.MEAL_INTERVAL,"An empty far pantry is a real missed meal: meal "+k+" missed="+r.missedMeals());
   }
   h.assertTrue(!r.alive(),"After "+Population.DEATH+" missed meals the far resident dies as a record");
   close(fs);h.succeed();
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=100) public static void deadResidentsBodyNeverRejoins(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true);s.addHome(home);
  var dead=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);var living=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(dead,home.id());s.admit(living,home.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var ghost=VillageAstra.RESIDENT.get().create(l);ghost.bind(s.id(),s.resident(dead.id()));ghost.setNoAi(true);ghost.moveTo(center.getX()+.5,center.getY(),center.getZ()+.5,0,0);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(living.id()));npc.setNoAi(true);npc.moveTo(center.getX()+2.5,center.getY(),center.getZ()+.5,0,0);
  try{
   s.resident(dead.id()).die();
   h.assertTrue(!l.addFreshEntity(ghost)&&l.getEntity(dead.id())==null,"The body of a resident who died as a record never joins the world");
   h.assertTrue(l.addFreshEntity(npc)&&l.getEntity(living.id())==npc,"A living resident's body still joins");
  }finally{ghost.discard();npc.discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void unrelatedFarVillageNeitherEatsNorStarves(GameTestHelper h){
  var f=far(h,0,2048,2,false);var fs=List.of(f);f.pantry().setItem(0,new ItemStack(Items.BREAD,10));
  var rs=List.copyOf(f.e.settlement().residents());var hungry=rs.get(0);var fed=rs.get(1);hungry.missedMeal(0);hungry.missedMeal(0);
  whenUnloaded(h,fs,()->{
   var l=f.l;long meal=Population.MEAL_INTERVAL;
   var before=TouchLoad.stats();
   for(int k=1;k<=3;k++){TouchLoad.resetTick();Population.serve(l,f.e,k*meal);}
   var after=TouchLoad.stats();
   h.assertTrue(after.loads()==before.loads()&&!f.loaded(),"A far village nobody cares about is never loaded for meals: loads="+(after.loads()-before.loads()));
   h.assertTrue(hungry.missedMeals()==2&&fed.missedMeals()==0,"Frozen: nobody misses a meal or recovers: "+hungry.missedMeals()+"/"+fed.missedMeals());
   h.assertTrue(hungry.lastMeal()==3*meal&&fed.lastMeal()==3*meal&&hungry.alive()&&fed.alive(),"Each due passes without a backlog: "+hungry.lastMeal()+"/"+fed.lastMeal());
   TouchLoad.resetTick();h.assertTrue(TouchLoad.ensure(l,f.chest)==TouchLoad.Touch.OK&&f.bread()==10,"Its pantry still holds all ten loaves: "+f.bread());
   close(fs);h.succeed();
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=700) public static void farBesiegedTargetWithBreadIsNotStarving(GameTestHelper h){
  var f=far(h,0,2560,2,true);var fs=List.of(f);f.pantry().setItem(0,new ItemStack(Items.BREAD,64));f.pantry().setItem(1,new ItemStack(Items.BREAD,36));
  whenUnloaded(h,fs,()->{
   var l=f.l;
   TouchLoad.resetTick();var fed=Sieges.starving(l,f.e);
   h.assertTrue(Boolean.FALSE.equals(fed)&&f.loaded()&&f.bread()==100,"A far besieged village with bread in its unloaded hall is not starving: "+fed);
   f.pantry().clearContent();
   TouchLoad.resetTick();var empty=Sieges.starving(l,f.e);
   h.assertTrue(Boolean.TRUE.equals(empty),"Emptied, it is starving: "+empty);
   whenUnloaded(h,fs,()->{
    TouchLoad.exhaust(l.getServer());var refused=Sieges.starving(l,f.e);TouchLoad.resetTick();
    h.assertTrue(refused==null&&!f.loaded(),"With the budget spent the hunger is unknown (null), never an unloaded pantry read as empty: "+refused);
    close(fs);h.succeed();
   });
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void twoFarVillagesTradeFromRealStock(GameTestHelper h){
  var source=far(h,0,3072,0,true);var destination=far(h,48,3072,0,true);var fs=List.of(source,destination);
  var s=source.e.settlement();var yard=new Settlement.Building(Settlement.childId(s.id(),"building/caravan"),"caravan",4,0,-8);s.addBuilding(yard);
  // AD-160 II: automatic remote partners have working trade centres and a recorded meeting.
  for(var f:fs){var village=f.e.settlement();var centre=f==source?yard:new Settlement.Building(Settlement.childId(village.id(),"building/caravan"),"caravan",4,0,-8);
   if(f!=source)village.addBuilding(centre);village.raiseBuildingLevel(centre.id(),2);
   var built=village.buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   for(var cell:BuildingPlacement.layout(f.e,built,BuildingTiers.layoutId("caravan",2)).entrySet())f.l.setBlock(cell.getKey(),cell.getValue(),3);
   BuildingLevels.forgetBest(village.id());}
  CartographyLadder.found(source.l,source.id(),destination.id());
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,s.homes().iterator().next().id());s.assign(r.id(),Profession.CARAVANEER,yard.id());
  source.pantry().setItem(0,new ItemStack(Items.BREAD,64));
  whenUnloaded(h,fs,()->{
   var l=source.l;var server=l.getServer();long now=7_200_000L;
   // R3: the two villages' own pass after a touch this test asserts; the global Caravans.pass would propose for every village in the test world.
   var points=new ArrayList<BlockPos>(Caravans.touchPoints(source.e));points.addAll(Caravans.touchPoints(destination.e));
   TouchLoad.resetTick();h.assertTrue(TouchLoad.ensureAll(l,points)==TouchLoad.Touch.OK,"Both far villages' chests are touched in one call");
   Caravans.passVillage(l,source.e,now);Caravans.passVillage(l,destination.e,now);
   var t=Caravans.contracts(server).stream().filter(k->k.hasUUID("destination")&&k.getUUID("destination").equals(destination.id())).findFirst().orElse(null);
   h.assertTrue(t!=null&&t.getString("item").equals("minecraft:bread")&&t.getInt("count")==16&&t.getUUID("source").equals(source.id())&&t.getUUID("caravaneer").equals(r.id()),"The far destination's bread demand is matched to the far source's real stock: "+t);
   var id=t.getUUID("id");
   // The trip leaves through the contract's own steps (secure, dispatch); Caravans.tick would advance every contract of the test world.
   TouchLoad.resetTick();int secured=Caravans.secure(l,t,now+20);Caravans.dispatch(l,Caravans.contract(server,id),now+20);
   var c=Caravans.contract(server,id);
   h.assertTrue(secured==16&&source.bread()==48&&c.getString("state").equals(Caravans.TRANSIT),"Sixteen real loaves left the far source's hall: secured="+secured+" bread="+source.bread()+" state="+c.getString("state"));
   close(fs);h.succeed();
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void aTouchedVillageKeepsItsActiveRaid(GameTestHelper h){
  var f=far(h,0,3584,2,true);var fs=List.of(f);var l=f.l;var server=l.getServer();
  var file=server.getWorldPath(LevelResource.ROOT).resolve("data/astra-raids/"+f.id()+".bin");
  // A wave that was under way when the players left: its raiders are in chunks nobody loads, so they are simply not found.
  var t=new CompoundTag();t.putInt("schema",1);t.putInt("waves",1);var active=new CompoundTag();active.putString("kind",Raids.Kind.MONSTERS.name());active.putLong("started",1);
  var mobs=new ListTag();for(int i=0;i<3;i++)mobs.add(NbtUtils.createUUID(UUID.randomUUID()));active.put("mobs",mobs);active.putInt("spawned",3);active.putInt("residents",2);active.putDouble("side",0);t.put("active",active);NbtRecord.write(file,t);
  whenUnloaded(h,fs,()->{
   var center=f.e.center();
   TouchLoad.resetTick();h.assertTrue(TouchLoad.ensure(l,center)==TouchLoad.Touch.OK&&l.hasChunkAt(center)&&!TouchLoad.ticking(l,center),"The hall chunk is touched: loaded, not ticking");
   // This village's own raid clock only: the global Raids.tick would update or schedule the raids of every other test's ticking village.
   Raids.tick(server,f.e,2);
   var record=Raids.record(l,f.id());
   h.assertTrue(record.contains("active")&&!record.contains("last")&&record.getCompound("active").getLong("started")==1,"The raid of a touched, sleeping village is neither closed as repelled nor rescheduled: "+record);
   close(fs);h.succeed();
  });
 }
 @GameTest(template="empty",batch="remote_pantry",timeoutTicks=400) public static void aTouchedVillageHasNoBirth(GameTestHelper h){
  var f=far(h,0,4096,2,true);var fs=List.of(f);f.pantry().setItem(0,new ItemStack(Items.BREAD,32));
  whenUnloaded(h,fs,()->{
   var l=f.l;var spawn=f.spawn();var ps=new ArrayList<BlockPos>(Population.pantryPositions(f.e));ps.add(spawn);
   TouchLoad.resetTick();h.assertTrue(TouchLoad.ensureAll(l,ps)==TouchLoad.Touch.OK&&l.hasChunkAt(spawn)&&!TouchLoad.ticking(l,spawn),"The pantry and the house are touched: loaded, not ticking");
   h.assertTrue(Population.storedNutrition(l,f.e)>=Population.BIRTH_FOOD*3L,"The touched pantry holds a birth's reserve, so only the sleeping chunk can refuse the child: "+Population.storedNutrition(l,f.e));
   int before=f.e.settlement().residents().size();
   h.assertTrue(Population.birth(l,f.e,Population.BIRTH_INTERVAL)==null&&f.e.settlement().residents().size()==before&&f.e.settlement().lastBirth()<0,"No child is born into a touched village whose entities do not tick");
   close(fs);h.succeed();
  });
 }
}
