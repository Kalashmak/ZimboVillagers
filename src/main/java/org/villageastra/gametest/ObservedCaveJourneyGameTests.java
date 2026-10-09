package org.villageastra.gametest;

import java.util.*;
import java.util.zip.GZIPInputStream;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ObservedCaveJourneyGameTests {
 @GameTest(template="empty",batch="observed_cave_journey",timeoutTicks=50000)
 public static void observedMayorReturnsThroughTheWholeSavedCave(GameTestHelper h) throws Exception {
  journey(h,2162688);
 }
 @GameTest(template="empty",batch="observed_cave_journey_negative",timeoutTicks=50000)
 public static void sameSavedCaveReturnsInNegativeWorldCoordinates(GameTestHelper h) throws Exception {
  journey(h,-2162688);
 }
 private static void journey(GameTestHelper h,int displacement) throws Exception {
  JsonObject fixture;
  try(var in=new GZIPInputStream(ObservedCaveJourneyGameTests.class.getResourceAsStream("/data/villageastra/fixtures/fresh5_cave_north_20261009.json.gz"))){
   fixture=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  }
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+displacement,160,at.getZ());
  var bounds=fixture.getAsJsonArray("bounds");var min=bounds.get(0).getAsJsonArray();var max=bounds.get(1).getAsJsonArray();
  var held=PhysicalFixtureChunks.force(l,base,min.get(0).getAsInt()-1,max.get(0).getAsInt()+1,min.get(2).getAsInt()-1,max.get(2).getAsInt()+1);
  var palette=fixture.getAsJsonArray("palette");
  var states=new net.minecraft.world.level.block.state.BlockState[palette.size()];
  var containers=new ArrayList<BlockPos>();
  for(int i=0;i<states.length;i++)states[i]=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),palette.get(i).getAsString(),false).blockState();
  for(var raw:fixture.getAsJsonArray("runs")){
   var r=raw.getAsJsonArray();var state=states[r.get(4).getAsInt()];
   for(int y=0;y<r.get(3).getAsInt();y++){
    var p=base.offset(r.get(0).getAsInt(),r.get(1).getAsInt()+y,r.get(2).getAsInt());l.setBlock(p,state,2);
    if(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock&&l.getBlockEntity(p) instanceof net.minecraft.world.Container)containers.add(p);
   }
  }
  var s=new Settlement(UUID.randomUUID());var center=offset(base,fixture.getAsJsonArray("center"));
  var entry=new SettlementData.Entry(s,l.dimension().location().toString(),center);
  var buildings=new ArrayList<Settlement.Building>();
  for(var raw:fixture.getAsJsonArray("buildings")){
   var b=raw.getAsJsonObject();var p=offset(center,b.getAsJsonArray("offset"));
   var building=new Settlement.Building(UUID.randomUUID(),b.get("type").getAsString(),p.getX()-center.getX(),p.getY()-center.getY(),p.getZ()-center.getZ(),b.get("rotation").getAsInt());
   s.addBuilding(building);buildings.add(building);
   if(building.type().equals("home"))s.addHome(new Settlement.Home(building.id(),1,2,true));
  }
  SettlementData.get(l.getServer()).add(entry);
  var npc=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);
  s.admit(resident,buildings.get(fixture.get("homeIndex").getAsInt()).id());s.assign(resident.id(),Profession.MAYOR,buildings.get(0).id());npc.bind(s.id(),resident);
  var start=fixture.getAsJsonArray("start");npc.moveTo(base.getX()+start.get(0).getAsDouble(),base.getY()+start.get(1).getAsDouble(),base.getZ()+start.get(2).getAsDouble());npc.setOnGround(true);
  float health=fixture.get("health").getAsFloat();npc.setHealth(health);
  h.assertTrue(base.offset(32,40,27).equals(HomeNeighborhood.anchor(npc)),"Saved building offsets are relative to settlement center; observed home anchor matches");
  h.assertTrue(l.noCollision(npc,npc.getBoundingBox()),"Actual observed body fits saved cave");
  var remember=Class.forName("org.villageastra.world.RecoveryLedges").getDeclaredMethod("remember",ResidentEntity.class,BlockPos.class);remember.setAccessible(true);
  for(var raw:fixture.getAsJsonArray("remembered"))remember.invoke(null,npc,offset(base,raw.getAsJsonArray()));
  var target=offset(base,fixture.getAsJsonArray("target"));
  npc.onlyGoals(g->g instanceof CaveEscapeGoal||g instanceof PitEscapeGoal||g instanceof SafeDescentGoal||g instanceof ResidentDoorGoal||g instanceof BedExitGoal||g instanceof DoorwayGoal||g instanceof SolidEscapeGoal||g instanceof FoliageEscapeGoal||g instanceof ShoreEscapeGoal||g instanceof net.minecraft.world.entity.ai.goal.FloatGoal,5,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
   public boolean canUse(){return true;}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.onGround()&&(npc.tickCount%20==0||npc.getNavigation().isDone())){var path=ResourceReturnRoute.plan(npc,target);if(path!=null)npc.getNavigation().moveTo(path,1);}}
   public void stop(){npc.getNavigation().stop();}
  });
  boolean[] done={false};Runnable clean=()->{done[0]=true;npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  var excavation=new LinkedHashMap<UUID,Set<BlockPos>>();
  Object[] lastPlan={null};
  Object[] heldSearch={null};Vec3[] heldPosition={null};int[] searchTicks={0};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Full cave fixture chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Real body registered"));
  h.onEachTick(()->{
   if(done[0])return;
   if(!npc.isAlive()||npc.getHealth()!=health){var why=npc.position()+" health="+npc.getHealth();clean.run();throw new GameTestAssertException("Full cave return injured or healed body: "+why);}
   captureExcavation(npc,excavation,lastPlan);
   try{
    var active=npc.goalSelector.getRunningGoals().map(g->g.getGoal()).filter(g->g instanceof CaveEscapeGoal).findFirst().orElse(null);
    if(active!=null){var field=CaveEscapeGoal.class.getDeclaredField("search");field.setAccessible(true);var search=field.get(active);
     if(search!=null){if(search!=heldSearch[0]){heldSearch[0]=search;heldPosition[0]=npc.position();}searchTicks[0]++;
      h.assertTrue(npc.position().distanceToSqr(heldPosition[0])<=1,"Incremental search owns movement and retains its starting tread (ordinary momentum may settle)");}
    }
   }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
   if(npc.tickCount>0&&npc.tickCount%1000==0){var path=npc.getNavigation().getPath();com.mojang.logging.LogUtils.getLogger().info("OBSERVED_CAVE_JOURNEY ticks={} relative={} goals={} pathReached={} pathEnd={} escape={}",npc.tickCount,npc.position().subtract(Vec3.atLowerCornerOf(base)),npc.runningGoals(),path!=null&&path.canReach(),path==null?null:path.getEndNode(),caveState(npc));}
   if(npc.distanceToSqr(Vec3.atBottomCenterOf(target))>6.25)return;
   int mined=0;
   for(var job:excavation.entrySet())for(var p:job.getValue()){
    var receipt=org.villageastra.persistence.WorldJournal.inspectCommitted(l,Settlement.childId(job.getKey(),"block/"+p.asLong()));if(receipt==null)continue;
    h.assertTrue(receipt.getString("kind").equals("block")&&receipt.getLong("pos")==p.asLong()&&!receipt.contains("loot"),"Escape has committed physical excavation receipts and no manufactured loot");
    h.assertTrue(l.getBlockState(p).isAir(),"Every confirmed excavation really removed its block");mined++;
   }
   h.assertTrue(mined>0,"The sealed cave was opened by real excavation");
   h.assertTrue(searchTicks[0]>=10,"The actual body owns the incremental search phase before excavation");
   for(var p:containers)if(l.getBlockEntity(p) instanceof net.minecraft.world.Container container)h.assertTrue(container.isEmpty(),"Escape does not grant resources into any copied stock");
   com.mojang.logging.LogUtils.getLogger().info("OBSERVED_CAVE_RETURN_COMPLETE bodyTicks={} health={} excavated={} stockGrants=0",npc.tickCount,npc.getHealth(),mined);
   clean.run();h.succeed();
  });
  h.runAtTickTime(48000,()->{if(done[0])return;var why=npc.position().subtract(Vec3.atLowerCornerOf(base))+" goals="+npc.runningGoals()+" bodyTicks="+npc.tickCount;clean.run();throw new GameTestAssertException("Actual full cave journey did not reach town hall: "+why);});
 }
 private static BlockPos offset(BlockPos base,JsonArray p){return base.offset(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());}
 private static void captureExcavation(ResidentEntity npc,Map<UUID,Set<BlockPos>> excavation,Object[] last){
  if(!npc.getPersistentData().hasUUID("caveEscapeJob"))return;
  try{
   var goal=npc.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof CaveEscapeGoal).findFirst().orElse(null);if(goal==null)return;
   var f=CaveEscapeGoal.class.getDeclaredField("steps");f.setAccessible(true);var plan=f.get(goal);if(!(plan instanceof List<?> list)||plan==last[0])return;last[0]=plan;
   var cells=excavation.computeIfAbsent(npc.getPersistentData().getUUID("caveEscapeJob"),k->new HashSet<>());
   for(var step:list)cells.addAll(((CaveExitPlan.Step)step).dig());
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
 private static String caveState(ResidentEntity npc){
  try{
   var goal=npc.goalSelector.getAvailableGoals().stream().map(g->g.getGoal()).filter(g->g instanceof CaveEscapeGoal).findFirst().orElse(null);if(goal==null)return "absent";
   var result=new StringBuilder();int index=0;Object steps=null;
   for(var name:List.of("index","walking","labor","retryAt","steps","search")){var f=CaveEscapeGoal.class.getDeclaredField(name);f.setAccessible(true);var value=f.get(goal);if(name.equals("index"))index=(int)value;if(name.equals("steps")){steps=value;result.append(" steps=").append(value==null?0:((List<?>)value).size());}else result.append(' ').append(name).append('=').append(value);}
   if(steps instanceof List<?> list&&index<list.size())result.append(" current=").append(list.get(index));return result.toString();
  }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
 }
}
