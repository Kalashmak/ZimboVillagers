package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

/** Opt-in terminal upgrades: actual NPC funding, movement and placement. Prerequisites and finite stock are fixtures,
 * so these tests deliberately make no claim about natural resource acquisition or a fresh village's growth. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class TerminalBuilderGameTests {
 @GameTestGenerator public static Collection<TestFunction> terminalBuildings(){
  if(!System.getProperty("villageastra.gtOnly","").contains("terminal_build"))return List.of();
  var types=new TreeSet<>(BuildingOrders.ORDERABLE);types.addAll(List.of("farm","forester","mine","town_hall"));
  var tests=new ArrayList<TestFunction>();
  for(var type:types)if(BuildingTiers.upgradable(type))tests.add(new TestFunction("terminal_build_"+type,"terminal_build_"+type,
    VillageAstra.ID+":empty",360000,0,true,h->build(h,type)));
  return tests;
 }
 private static void build(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6+Integer.getInteger("villageastra.terminalFixtureOffset",0),3,6));var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.BARN_LOTS);
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  // Loading blocks outside the tiny template does not make their residents tick.
  // The expanded physical fixture needs its own entity-ticking chunk tickets.
  for(int x=(center.getX()-8)>>4;x<=(center.getX()+96)>>4;x++)for(int z=(center.getZ()-8)>>4;z<=(center.getZ()+65)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}
  }
  // Each type has a separate batch: extended fields and parent fixtures cannot overlap another live test.
  for(int x=-8;x<=96;x++)for(int z=-8;z<=65;z++){
   l.getChunk(center.offset(x,0,z));
   for(int y=-12;y<0;y++)l.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=28;y++)l.setBlock(center.offset(x,y,z),(x==-8||x==96||z==-8||z==65?Blocks.GLASS:Blocks.AIR).defaultBlockState(),2);
   // The shared GameTest world has real terrain above this underground fixture.
   // Falling gravel and water from outside it must not impersonate build failures.
   l.setBlock(center.offset(x,29,z),Blocks.GLASS.defaultBlockState(),2);
  }
  int top=BuildingTiers.max(type),before=top-1;
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0,0,type.equals("town_hall")?before:6);s.addBuilding(hall);
  s.civilization().completedHallUpgrade(2);s.civilization().completedHallUpgrade(3);
  for(int n=4;n<=hall.level();n++)s.civilization().completedHallUpgrade(n);
  var target=type.equals("town_hall")?hall:new Settlement.Building(UUID.randomUUID(),type,32,0,0,0,before);
  if(!type.equals("town_hall"))s.addBuilding(target);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);var data=SettlementData.get(l.getServer());data.add(e);
  ResidentEntity npc=null;
  try{
   var record=BookResearch.inspect(l,e);var learned=new ListTag();ResearchCatalog.NODES.keySet().forEach(id->learned.add(StringTag.valueOf(id)));record.put("legacyDone",learned);BookResearch.store(l,e,record);ResearchKnobs.forget(s.id());
   lay(l,e,target,BuildingTiers.layoutId(type,before));
   if(type.equals("farm")){
    s.raiseFieldLevel(target.id(),before);
    for(var cell:FarmField.layout(BlockPos.ZERO,FarmField.modules(before),42).entrySet())l.setBlock(BuildingPlacement.at(e,target,cell.getKey().getX(),cell.getKey().getY(),cell.getKey().getZ()),cell.getValue(),2);
    for(var cell:FarmBarn.layout(before).entrySet())l.setBlock(BuildingPlacement.at(e,target,cell.getKey().getX(),cell.getKey().getY(),cell.getKey().getZ()),cell.getValue(),2);
   }
   var parent=BuildingTiers.parent(type,top);
   if(parent!=null){var b=new Settlement.Building(UUID.randomUUID(),parent.type(),68,0,0,0,parent.level());s.addBuilding(b);lay(l,e,b,BuildingTiers.layoutId(b.type(),b.level()));}
   var stockPos=LogisticsRoutes.position(e,hall);l.setBlock(stockPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   var stock=LogisticsRoutes.chest(l,e,hall);h.assertTrue(stock!=null,"Hall stock exists");stock.expandHall();stock.clearContent();
   BuildingLevels.forgetBest(s.id());
   var why=BuildingTiers.order(l,e,target);h.assertTrue(why.isEmpty(),type+" final tier can be ordered: "+why);
   var state=HallUpgradeGoal.inspect(l,s.id());h.assertTrue(!state.getBoolean("funded"),"No injected project cargo");
   int slot=0;for(var id:new TreeSet<>(state.getCompound("cost").getAllKeys())){
    var item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));int amount=state.getCompound("cost").getInt(id);
    while(amount>0){int n=Math.min(amount,item.getMaxStackSize());h.assertTrue(slot<stock.getContainerSize(),"Finite fixture fits hall stock");stock.setItem(slot++,new ItemStack(item,n));amount-=n;}
   }
   var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));HallUpgradeGoal goal=null;var crewGoals=new HashMap<UUID,HallUpgradeGoal>();
   // Four members of the unlocked construction crew share the real paid project.
   for(int i=0;i<4;i++){
    var resident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,home);s.assign(resident.id(),Profession.BUILDER,hall.id());
    var member=VillageAstra.RESIDENT.get().create(l);member.bind(s.id(),resident);member.moveTo(center.getX()+27.5+i,center.getY()+1,center.getZ()-2.5,0,0);
    var work=new HallUpgradeGoal(member,true);crewGoals.put(member.getUUID(),work);member.onlyGoals(g->g instanceof FloatGoal||g instanceof ResidentDoorGoal||g instanceof DoorwayGoal||g instanceof SafeDescentGoal||g instanceof PitEscapeGoal,5,work);l.addFreshEntity(member);
    if(npc==null){npc=member;goal=work;}
   }
   var diagnostics=goal;
   var worker=npc;long started=l.getGameTime();int[] last={-1,0};var log=com.mojang.logging.LogUtils.getLogger();
   log.info("ZIMBO_TERMINAL_BUILD START type={} tier={} ops={} fixtureStock=true",type,top,state.getList("ops",Tag.TAG_COMPOUND).size());
   boolean[] verified={false},accessDumped={false};h.onEachTick(()->{if(verified[0])return;long elapsed=l.getGameTime()-started;if(elapsed%200!=0)return;var current=HallUpgradeGoal.inspect(l,s.id());
    int progress=current.getInt("progress")+current.getInt("withdrawals")+current.getInt("returns");if(progress!=last[0]){last[0]=progress;last[1]=(int)elapsed;}
    if(elapsed>=200)for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity member)
     h.assertTrue(l.isPositionEntityTicking(member.blockPosition()),"Every live builder in the extended fixture must tick; loading blocks alone is insufficient");
    if(elapsed%6000==0)log.info("ZIMBO_TERMINAL_BUILD PROGRESS type={} ticks={} operations={}/{} funded={}",type,elapsed,current.getInt("progress"),current.getList("ops",Tag.TAG_COMPOUND).size(),current.getBoolean("funded"));
    if(elapsed%6000==0)for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity member){var route=member.getNavigation().getPath();log.info("ZIMBO_TERMINAL_BUILD CREW type={} id={} at={} entityTicks={} ticking={} status={} goals={} route={} feet={} head={} below={}",type,r.id(),member.position().subtract(center.getX(),center.getY(),center.getZ()),member.tickCount,l.isPositionEntityTicking(member.blockPosition()),member.workStatus(),member.runningGoals(),route==null?"none":route.canReach()+"/"+route.getNextNodeIndex()+"/"+route.getNodeCount()+(route.isDone()?"":"/"+route.getNextNodePos()),l.getBlockState(member.blockPosition()),l.getBlockState(member.blockPosition().above()),l.getBlockState(member.blockPosition().below()));}
    if(type.equals("warehouse")&&!accessDumped[0]&&current.getInt("progress")>=1500){
     accessDumped[0]=true;
     for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity member){var work=crewGoals.get(r.id());
      log.info("ZIMBO_WAREHOUSE_ACCESS id={} op={} stand={} walk={} eye={} grounded={} collision={}",r.id(),work.opDiag,work.standDiag,work.walkDiag,member.getEyePosition(),member.onGround(),member.horizontalCollision);}
     for(int x=12;x<=17;x++)for(int z=1;z<=5;z++)for(int y=0;y<=3;y++){var p=BuildingPlacement.at(e,target,x,y,z);var bs=l.getBlockState(p);log.info("ZIMBO_WAREHOUSE_CELL local={},{},{} block={} shape={}",x,y,z,bs,bs.getCollisionShape(l,p));}
    }
    if(!current.getBoolean("complete")&&elapsed-last[1]>12000){
     current.putLong("fixtureCenter",center.asLong());
     org.villageastra.persistence.NbtRecord.write(l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/terminal-failures/"+type+"-"+s.id()+".bin"),current);
     log.error("ZIMBO_TERMINAL_BUILD STALLED type={} progress={} index={} status={} at={} op={} stand={}",type,progress,current.getInt("index"),worker.workStatus(),worker.blockPosition().subtract(center),diagnostics.opDiag,diagnostics.standDiag);verified[0]=true;cleanup(l,s,worker,forced);throw new GameTestAssertException(type+" terminal upgrade stalled");}
   });
   h.onEachTick(()->{
    if(verified[0])return;var current=HallUpgradeGoal.inspect(l,s.id());if(!current.getBoolean("complete"))return;verified[0]=true;
    var kept=s.buildings().stream().filter(b->b.id().equals(target.id())).findFirst().orElseThrow();BuildingLevels.forgetBest(s.id());
    h.assertTrue(kept.level()==top&&BuildingLevels.level(l,e,kept)==top,"Final tier actually works");
    h.assertTrue(TerminalProgress.missing(l,e,kept)==0,"Final design has missing blocks: "+TerminalProgress.missing(l,e,kept));
    h.assertTrue(current.getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(raw->ItemStack.of((CompoundTag)raw).isEmpty()),"No stranded construction cargo");
    log.info("ZIMBO_TERMINAL_BUILD VERIFIED type={} tier={} ticks={} physicalFunding=true geometry=true fixtureStock=true",type,top,l.getGameTime()-started);cleanup(l,s,worker,forced);h.succeed();
   });
  }catch(RuntimeException ex){cleanup(l,s,npc,forced);throw ex;}
 }
 private static void lay(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){for(var cell:BuildingPlacement.layout(e,b,design).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);}
 private static void cleanup(net.minecraft.server.level.ServerLevel l,Settlement s,ResidentEntity npc,List<net.minecraft.world.level.ChunkPos> forced){for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);if(npc!=null)npc.discard();for(var r:s.residents()){var entity=l.getEntity(r.id());if(entity!=null)entity.discard();}HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());ResearchKnobs.forget(s.id());}
}
