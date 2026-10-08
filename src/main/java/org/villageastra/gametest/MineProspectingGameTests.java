package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineProspectingGameTests {
 @GameTest(template="empty",batch="mine_floor_exchange",timeoutTicks=1800)
 public static void minerPaysStoneAndExchangesFloorOreWithoutOpeningSupport(GameTestHelper h){floorExchange(h,false);}
 @GameTest(template="empty",batch="mine_flat_floor_exchange",timeoutTicks=1800)
 public static void minerExchangesIronBelowTheSameGalleryFromAnAdjacentStand(GameTestHelper h){floorExchange(h,true);}
 @GameTest(template="empty",batch="mine_distant_floor_exchange",timeoutTicks=5000)
 public static void minerReachesPaidFloorOreAtTheEndOfALongGallery(GameTestHelper h){floorExchange(h,true,true);}
 private static void floorExchange(GameTestHelper h,boolean sameGallery){floorExchange(h,sameGallery,false);}
 @GameTest(template="empty",batch="mine_floor_route_reload",timeoutTicks=5000)
 public static void floorOrePlanningRechecksAnUnloadedGapInTheCompletedRoute(GameTestHelper h){floorExchange(h,true,true,true);}
 private static void floorExchange(GameTestHelper h,boolean sameGallery,boolean distant){floorExchange(h,sameGallery,distant,false);}
 @GameTest(template="empty",batch="mine_side_floor_exchange",timeoutTicks=1800)
 public static void minerReplacesExposedSideFoundationWithoutOpeningAPit(GameTestHelper h){floorExchange(h,true,false,false,true);}
 private static void floorExchange(GameTestHelper h,boolean sameGallery,boolean distant,boolean gap){floorExchange(h,sameGallery,distant,gap,false);}
 private static void floorExchange(GameTestHelper h,boolean sameGallery,boolean distant,boolean gap,boolean lateral){
  int end=distant?198:16,oreX=distant?186:6,length=distant?192:6;
  var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());
  var e=new SettlementData.Entry(old.s,old.e.dimension(),new BlockPos(old.e.center().getX()*8+(gap?2097152:0),96,old.e.center().getZ()*8));data.add(e);
  var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);ResearchV2Town.lay(t.l,e,t.shop,"mine");t.l.setBlock(e.center().offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var base=BuildingPlacement.origin(e,t.shop);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=base.getX()>>4;x<=(base.getX()+end)>>4;x++)for(int z=(base.getZ()+3)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);t.l.getChunkSource().addRegionTicket(ORE_TICKET,cp,3,t.s.id());chunks.add(cp);}
  for(int x=(base.getX()>>4)-2;x<=((base.getX()+end)>>4)+2;x++)for(int z=((base.getZ()+3)>>4)-2;z<=((base.getZ()+10)>>4)+2;z++)t.l.getChunk(x,z);
  for(int x=0;x<=end;x++)for(int z=3;z<=10;z++)for(int y=-2;y<=4;y++)t.l.setBlock(BuildingPlacement.at(e,t.shop,x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int x=2;x<=(distant?196:10);x++)for(int z=4;z<=8;z++){int foot=z<=5?1:z<=7?0:-1;for(int y=foot;y<=4;y++)t.l.setBlock(BuildingPlacement.at(e,t.shop,x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var stock=LogisticsRoutes.position(e,t.shop);t.l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=(net.minecraft.world.Container)t.l.getBlockEntity(stock);chest.setItem(0,new ItemStack(Items.COBBLESTONE,2));
  var ore=BuildingPlacement.at(e,t.shop,oreX,sameGallery?-2:-1,lateral?9:sameGallery?8:7);t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);if(lateral){t.l.setBlock(ore.above(),Blocks.AIR.defaultBlockState(),2);t.l.setBlock(ore.above(2),Blocks.AIR.defaultBlockState(),2);}
  t.s.noteMine(t.shop.id(),32,3,5,0);t.s.noteMine(t.shop.id(),new MineArea.Gallery(0,MineDrive.EAST,length));t.s.noteMine(t.shop.id(),new MineArea.Gallery(1,MineDrive.EAST,length));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:lantern",2);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);
  var hall=LogisticsRoutes.chest(t.l,e,Workshops.hall(e));hall.setItem(0,new ItemStack(Items.COAL,8));hall.setItem(1,new ItemStack(Items.STICK,8));
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));
  var start=BuildingPlacement.at(e,t.shop,10,-1,8);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);npc.setNoAi(true);
  var state=MineWork.read(t.l,t.shop);state.putInt("width",3);state.putInt("height",5);state.putInt("descent",0);state.putInt("step",33);state.putInt("floorStep",32);state.putInt("extentStep",32);state.putInt("stairAudit",33);state.putInt("side",MineDrive.DONE);state.putString("stage","choose");state.putUUID("worker",npc.getUUID());state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
  if(gap){
   var missing=base.offset(96,-1,8);
   for(var cp:chunks)if(cp.x>=(base.getX()+48)>>4&&cp.x<=(base.getX()+144)>>4)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,t.s.id());
   h.startSequence().thenWaitUntil(()->h.assertTrue(!t.l.hasChunkAt(missing),"Released middle route chunks unload naturally"))
    .thenExecute(()->h.assertTrue(!HarvestAccess.reversible(npc.routeTo(BuildingPlacement.at(e,t.shop,oreX,-1,8),0,NaturalSupplyGoal.ROUTE_RANGE)),"An unloaded corridor has no complete native route"))
    .thenWaitUntil(()->h.assertTrue(t.l.getGameTime()%100==0&&MineOreWork.begin(npc,e,t.shop,state),"Mine planner must eventually recheck a real route after a chunk gap"))
    .thenExecute(()->{
     h.assertTrue(t.l.hasChunkAt(missing)&&HarvestAccess.reversible(npc.routeTo(MineOreWork.stand(state),0,NaturalSupplyGoal.ROUTE_RANGE)),"The loaded corridor has a reversible native route");
     h.assertTrue(chest.countItem(Items.COBBLESTONE)==2&&t.l.getBlockState(ore).is(Blocks.IRON_ORE)&&ItemStack.of(state.getCompound("tool")).getDamageValue()==0,"Planning neither spends stone nor mines ore");
     com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_MINE_ROUTE_RELOAD VERIFIED unloadedGap={} target={} stoneUnspent=2",missing,ore);
     npc.discard();for(var cp:chunks)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,t.s.id());ResearchV2Town.done(t);
    }).thenSucceed();return;
  }
  chest.clearContent();h.assertTrue(!MineOreWork.begin(npc,e,t.shop,state),"No floor job without actual replacement stone");chest.setItem(0,new ItemStack(Items.COBBLESTONE,2));
  state.put("tool",new ItemStack(Items.WOODEN_PICKAXE).save(new CompoundTag()));h.assertTrue(!MineOreWork.begin(npc,e,t.shop,state),"Insufficient pick never selects floor iron");state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
  t.l.setBlock(ore.north(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!MineOreWork.begin(npc,e,t.shop,state),"Paid stone does not permit exposing water");t.l.setBlock(ore.north(),Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(MineOreWork.begin(npc,e,t.shop,state),"Reachable floor iron must become a paid, support-preserving mining job; needed="+MineProspecting.needed(t.l,e,t.shop)+" path="+HarvestAccess.reversible(npc.routeTo(BuildingPlacement.at(e,t.shop,oreX,-1,8),0,128))+" chest="+LogisticsRoutes.chest(t.l,e,t.shop));
  var operation=state.getCompound("mineOre").getUUID("id");var unpaid=state.copy();MineWork.write(t.l,t.shop,state);
  npc.setNoAi(false);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->6000L));t.l.addFreshEntity(npc);
  var checkpoints=new CompoundTag[1];h.onEachTick(()->{
   t.l.resetEmptyTime();
   h.assertTrue(t.l.getBlockState(ore).is(Blocks.IRON_ORE)||t.l.getBlockState(ore).is(Blocks.COBBLESTONE),"The shared gallery support never becomes air");
   var saved=MineWork.read(t.l,t.shop);if(saved.contains("mineOre")&&saved.getCompound("mineOre").getBoolean("floorTaken")){if(checkpoints[0]==null){
    var debitAhead=unpaid.copy();MineOreWork.reconcile(t.l,debitAhead);MineOreWork.reconcile(t.l,debitAhead);h.assertTrue(ForestFixture.count(debitAhead.getList("cargo",Tag.TAG_COMPOUND),Items.COBBLESTONE)==1,"Stone receipt ahead of the checkpoint is recovered once");
    var cancelled=debitAhead.copy();MineOreWork.clear(t.l,npc,cancelled);h.assertTrue(!MineOreWork.active(cancelled)&&ForestFixture.count(cancelled.getList("cargo",Tag.TAG_COMPOUND),Items.COBBLESTONE)==1,"An interrupted job retains its actual unplaced stone");
    var custody=JobCargo.snapshot(npc,true);h.assertTrue(ForestFixture.count(custody.items(),Items.COBBLESTONE)==1&&ForestFixture.count(custody.items(),Items.RAW_IRON)==0,"Death custody includes paid stone before exchange");
   }checkpoints[0]=saved.copy();}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(org.villageastra.persistence.WorldJournal.inspectCommitted(t.l,operation)!=null,"Native miner must fetch stone, walk back and exchange the floor"))
   .thenExecute(()->{
    var after=MineWork.read(t.l,t.shop);h.assertTrue(npc.tickCount>10&&checkpoints[0]!=null,"Real body travel and a paid checkpoint precede replacement");
    h.assertTrue(chest.countItem(Items.COBBLESTONE)==1&&ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.COBBLESTONE)==0&&ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.RAW_IRON)==1,"Exactly one stone paid, one raw iron carried");
    h.assertTrue(ItemStack.of(after.getCompound("tool")).getDamageValue()==1&&npc.getHealth()==npc.getMaxHealth(),"Actual mining wears the pick once and preserves health");
    var replay=checkpoints[0].copy();MineOreWork.reconcile(t.l,replay);MineOreWork.reconcile(t.l,replay);h.assertTrue(ForestFixture.count(replay.getList("cargo",Tag.TAG_COMPOUND),Items.COBBLESTONE)==0&&ForestFixture.count(replay.getList("cargo",Tag.TAG_COMPOUND),Items.RAW_IRON)==1,"Journal-ahead replay consumes the paid stone once and returns one ore");
    var custody=JobCargo.snapshot(npc,true);h.assertTrue(ForestFixture.count(custody.items(),Items.COBBLESTONE)==0&&ForestFixture.count(custody.items(),Items.RAW_IRON)==1,"Death custody after replacement contains ore and no spent stone");
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FLOOR_EXCHANGE VERIFIED bodyTicks={} ore={} stonePaid=1 iron=1 pickWear=1",npc.tickCount,ore);npc.discard();for(var cp:chunks)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,t.s.id());ResearchV2Town.done(t);
   }).thenSucceed();
  h.runAtTickTime(distant?4900:1700,()->{var saved=MineWork.read(t.l,t.shop);var detail="Floor trip stalled: pos="+npc.position()+" bodyTicks="+npc.tickCount+" status="+npc.workStatus()+" ticking="+t.l.isPositionEntityTicking(npc.blockPosition())+" state="+saved; npc.discard();for(var cp:chunks)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,t.s.id());ResearchV2Town.done(t);h.assertTrue(false,detail);});
 }

 @GameTest(template="empty",batch="mine_extension",timeoutTicks=2400)
 public static void exhaustedMineExtendsItsPaidWorkingFaceToRealOre(GameTestHelper h){extension(h,1);}
 @GameTest(template="empty",batch="mine_extended_survey",timeoutTicks=2400)
 public static void unmetOreDemandContinuesBeyondFourSurveySections(GameTestHelper h){extension(h,4);}
 @GameTest(template="empty",batch="mine_twelve_section_survey",timeoutTicks=2400)
 public static void unmetOreDemandCanContinueBeyondEightCompletedSections(GameTestHelper h){extension(h,8);}
 private static void extension(GameTestHelper h,int sections){
  var f=town(h,true);var t=f.town;var state=f.state;int floor=state.getInt("floorStep"),y=-floor-state.getInt("descent"),z=7+floor;
  int initial=CoreEffects.mine().galleryLength()*sections;
  var base=BuildingPlacement.origin(t.e,t.shop);var forced=PhysicalFixtureChunks.force(t.l,base,0,initial+31,0,z+3);
  for(int x=2;x<=initial+30;x++)for(int dz=-2;dz<=2;dz++)for(int dy=-1;dy<=6;dy++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y+dy,z+dz),Blocks.STONE.defaultBlockState(),2);
  for(int x=4;x<5+initial;x++)for(int dy=0;dy<5;dy++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y+dy,z),Blocks.AIR.defaultBlockState(),2);
  t.s.noteMine(t.shop.id(),new MineArea.Gallery(floor,MineDrive.EAST,initial));
  state.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,floor).toArray());state.putString("stage","choose");
  var ore=BuildingPlacement.at(t.e,t.shop,6+initial,y+1,z);t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);
  h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,state),"Unmet real ore demand resumes a fully worked gallery instead of idling forever");
  h.assertTrue(state.getInt("floorStep")==floor&&state.getInt("run")==initial,"Existing depth and paid gallery endpoint are retained");
  MineWork.write(t.l,t.shop,state);var saved=MineWork.read(t.l,t.shop);var next=MineWork.next(t.l,t.e,t.shop,saved);
  h.assertTrue(next.stage()==MineDrive.Stage.EAST&&next.cell().x()==5+initial,"Reload resumes the next unmined column");
  var start=BuildingPlacement.at(t.e,t.shop,3+initial,y,z);f.npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);f.npc.setNoAi(false);f.npc.onlyGoals(g->false,6,new ResourceWorkGoal(f.npc,true,()->6000));boolean[] started={false};
  h.onEachTick(()->{
   if(!started[0]){if(TouchLoad.ticking(t.l,f.npc.blockPosition()))started[0]=t.l.addFreshEntity(f.npc);return;}
   var work=MineWork.read(t.l,t.shop);int iron=ForestFixture.count(work.getList("cargo",Tag.TAG_COMPOUND),Items.RAW_IRON);
   if(iron==0)return;
   h.assertTrue(iron==1&&t.l.getBlockState(ore).isAir(),"Native mining yields exactly the real ore block");
   h.assertTrue(ItemStack.of(work.getCompound("tool")).getDamageValue()>=5&&f.npc.tickCount>20,"Stone and ore require ordinary tool wear and body ticks");
   h.assertTrue(t.s.mineAreas().get(t.shop.id()).lastStep()==floor&&t.s.mineAreas().get(t.shop.id()).galleries().stream().anyMatch(g->g.step()==floor&&g.side()==MineDrive.EAST&&g.length()>initial),"Only actually excavated extension is claimed, without deeper stairs");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_MINE_EXTENSION VERIFIED bodyTicks={} iron={} work={}",f.npc.tickCount,iron,work);
   f.npc.discard();PhysicalFixtureChunks.release(t.l,forced);ResearchV2Town.done(t);h.succeed();
  });
  h.runAtTickTime(2300,()->h.assertTrue(false,"Extension stalled: "+f.npc.position()+" "+f.npc.workStatus()+" "+MineWork.read(t.l,t.shop)));
 }

 @GameTest(template="empty",batch="mine_extension_guards",timeoutTicks=200)
 public static void extensionRespectsStockWaterSupportAndSurveyBounds(GameTestHelper h){
  var f=town(h,true);var t=f.town;try{
   var state=f.state;int floor=state.getInt("floorStep"),y=-floor-state.getInt("descent"),z=7+floor,n=CoreEffects.mine().galleryLength();
   state.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,floor).toArray());state.putString("stage","choose");
   var face=BuildingPlacement.at(t.e,t.shop,5+n,y+4,z);var support=face.below(5);t.l.getChunk(face.getX()>>4,face.getZ()>>4);
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-5;dy<=1;dy++)t.l.setBlock(face.offset(dx,dy,dz),Blocks.STONE.defaultBlockState(),2);
   t.s.noteMine(t.shop.id(),new MineArea.Gallery(floor,MineDrive.EAST,n-1));
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"An unfinished or previously blocked section is not extended");
   t.s.noteMine(t.shop.id(),new MineArea.Gallery(floor,MineDrive.EAST,n));t.l.setBlock(face.north(),Blocks.WATER.defaultBlockState(),2);
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"A wet face is never opened for more ore");t.l.setBlock(face.north(),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(support,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"A new section needs actual floor support");t.l.setBlock(support,Blocks.STONE.defaultBlockState(),2);
   var other=new Settlement(UUID.randomUUID());other.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",0,0,0));var data=SettlementData.get(t.l.getServer());data.add(new SettlementData.Entry(other,t.e.dimension(),face));
   try{h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"A longer gallery cannot cut another building");}finally{data.remove(other.id());}
   var chest=LogisticsRoutes.chest(t.l,t.e,t.shop);chest.setItem(0,new ItemStack(Items.RAW_IRON,64));
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"Ore already awaiting delivery prevents unnecessary excavation");chest.clearContent();
   h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,state),"The same dry supported face with unmet demand is eligible");
   var remoteMine=new Settlement.Building(t.shop.id(),"mine",t.shop.x()+300,t.shop.y(),t.shop.z());
   var remoteFace=face.offset(300,0,0);t.l.getChunk(remoteFace.getX()>>4,remoteFace.getZ()>>4);
   for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int dy=-5;dy<=1;dy++)t.l.setBlock(remoteFace.offset(dx,dy,dz),Blocks.STONE.defaultBlockState(),2);
   h.assertTrue(!MineProspecting.needed(t.l,t.e,remoteMine).isEmpty(),"Remote-face refusal still has a real ore order");
   h.assertTrue(!MineProspecting.begin(t.l,t.e,remoteMine,state.copy()),"A whole added section must remain inside supported worker territory");
   var ending=state.copy();ending.putInt("run",ending.getInt("prospectLength")-1);ending.putInt("cell",4);ending.putString("mineStage","EAST");MineWork.step(ending);
   h.assertTrue(ending.getInt("side")==MineDrive.DONE,"Finishing a section never replays the opposite paid gallery");
   t.s.noteMine(t.shop.id(),new MineArea.Gallery(floor,MineDrive.EAST,MineArea.maxGalleryLength()));
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"Bounded survey footprint is never exceeded");
   h.assertTrue(t.l.getBlockState(face).is(Blocks.STONE)&&ItemStack.of(state.getCompound("tool")).getDamageValue()==0,"Planning alone never mines or pays resources");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 private record Town(ResearchV2Town.Town town,ResidentEntity npc,CompoundTag state){}
 private static Town town(GameTestHelper h){return town(h,false);}
 private static Town town(GameTestHelper h,boolean isolated){
  var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());var e=new SettlementData.Entry(old.s,old.e.dimension(),new BlockPos(old.e.center().getX()*(isolated?4:1),64,old.e.center().getZ()*(isolated?4:1)));data.add(e);
  var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);ResearchV2Town.lay(t.l,e,t.shop,"mine");t.l.setBlock(e.center().offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);
  var state=MineWork.read(t.l,t.shop);int floor=MineWork.floorStep(t.l,e,t.shop,state);state.putInt("floorStep",floor);state.putInt("extentStep",floor);state.putInt("stairAudit",floor+1);state.putInt("step",floor+1);state.putInt("side",MineDrive.DONE);state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));t.s.noteMine(t.shop.id(),floor,3,5,7);MineWork.write(t.l,t.shop,state);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:lantern",3);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);
  var hall=LogisticsRoutes.chest(t.l,e,Workshops.hall(e));hall.clearContent();hall.setItem(0,new ItemStack(Items.COAL,8));hall.setItem(1,new ItemStack(Items.STICK,8));
  return new Town(t,npc,state);
 }
 private static String oreDiagnostic(Town f){var t=f.town;var p=BuildingPlacement.at(t.e,t.shop,6,-5,8);var stand=BuildingPlacement.at(t.e,t.shop,6,-7,7);return " needed="+MineProspecting.needed(t.l,t.e,t.shop)+" chest="+LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e))+" ore="+t.l.getBlockState(p)+" stand="+t.l.getBlockState(stand)+" floor="+t.l.getBlockState(stand.below())+" protectedOther="+OwnershipEvents.protectedBlock(t.l,p,b->!b.id().equals(t.shop.id()))+" chunk="+t.l.hasChunkAt(p)+" standChunk="+t.l.hasChunkAt(stand)+" eye="+f.npc.getEyeHeight()+" child="+f.npc.child()+" tag="+t.l.getBlockState(p).is(net.minecraft.tags.BlockTags.IRON_ORES)+" above="+t.l.getBlockState(p.above())+" fluids="+java.util.Arrays.stream(Direction.values()).map(d->d+":"+t.l.getFluidState(p.relative(d))).toList()+" stage="+f.state.getString("stage")+" tool="+f.state.getCompound("tool");}
 private static final Map<UUID,List<net.minecraft.world.level.ChunkPos>> ORE_CHUNKS=new HashMap<>();
 private static void doneOre(Town f){for(var cp:ORE_CHUNKS.getOrDefault(f.town.s.id(),List.of()))f.town.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,f.town.s.id());ORE_CHUNKS.remove(f.town.s.id());f.npc.discard();ResearchV2Town.done(f.town);}
 private static void oreGallery(Town f){var t=f.town;
  var base=BuildingPlacement.origin(t.e,t.shop);var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-12)>>4;x<=(base.getX()+16)>>4;x++)for(int z=base.getZ()>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);t.l.getChunkSource().addRegionTicket(ORE_TICKET,cp,3,t.s.id());chunks.add(cp);t.l.getChunk(x,z);}ORE_CHUNKS.put(t.s.id(),chunks);
  for(int x=((base.getX()-12)>>4)-2;x<=((base.getX()+16)>>4)+2;x++)for(int z=(base.getZ()>>4)-2;z<=((base.getZ()+10)>>4)+2;z++)t.l.getChunk(x,z);

  // This elevated fixture shares a server world with earlier batches; remove their neighbouring fluid residue before laying its sealed dry gallery.
  for(int x=0;x<=16;x++)for(int z=5;z<=10;z++)for(int y=-9;y<=0;y++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int x=2;x<=9;x++)for(int z=6;z<=8;z++)for(int y=-8;y<=-2;y++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y,z),(y==-8||z!=7?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  t.s.noteMine(t.shop.id(),new MineArea.Gallery(0,MineDrive.EAST,4));f.state.putString("stage","choose");var stand=BuildingPlacement.at(t.e,t.shop,5,-7,7);f.npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);f.npc.setOnGround(true);
 }
 private static final net.minecraft.server.level.TicketType<UUID> ORE_TICKET=net.minecraft.server.level.TicketType.create("zimbovillagers_ore_fixture",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="zz_exposed_ore_physical",timeoutTicks=1200)
 public static void minerPhysicallyWalksToTheRecordedWallOreBeforeBreakingIt(GameTestHelper h){
  physicalWallResource(h,false);
 }
 @GameTest(template="empty",batch="zz_exposed_stone_physical",timeoutTicks=1200)
 public static void minerPhysicallyHarvestsNeededAndesiteFromTheGalleryWall(GameTestHelper h){
  physicalWallResource(h,true);
 }
 @GameTest(template="empty",batch="mine_precise_arrival",timeoutTicks=1200)
 public static void minerCompletesTheLastStrideAfterAnApproximateRouteStops(GameTestHelper h){
  physicalWallResource(h,false,true);
 }
 private static void physicalWallResource(GameTestHelper h,boolean andesite){
  physicalWallResource(h,andesite,false);
 }
 @GameTest(template="empty",batch="mine_high_wall_ore",timeoutTicks=1200)
 public static void minerUsesTheVisibleFaceOfHighWallOreWhenItsCenterIsOccluded(GameTestHelper h){physicalWallResource(h,false,false,true);}
 private static void physicalWallResource(GameTestHelper h,boolean andesite,boolean finalStride){physicalWallResource(h,andesite,finalStride,false);}
 private static void physicalWallResource(GameTestHelper h,boolean andesite,boolean finalStride,boolean highWall){
  var f=town(h,true);var t=f.town;oreGallery(f);var owned=UUID.randomUUID();var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();var base=BuildingPlacement.origin(t.e,t.shop);
  if(andesite){var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:polished_andesite",3);project.put("cost",cost);HallUpgradeGoal.store(t.l,t.s.id(),project);}
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+16)>>4;x++)for(int z=(base.getZ()+4)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);t.l.getChunkSource().addRegionTicket(ORE_TICKET,cp,3,owned);chunks.add(cp);}
  for(int x=((base.getX()-3)>>4)-2;x<=((base.getX()+16)>>4)+2;x++)for(int z=((base.getZ()+4)>>4)-2;z<=((base.getZ()+10)>>4)+2;z++)t.l.getChunk(x,z);
  for(int x=9;x<=14;x++)for(int y=-8;y<=-3;y++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y,7),(y==-8?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var rock=andesite?Blocks.ANDESITE:Blocks.IRON_ORE;var loot=andesite?Items.ANDESITE:Items.RAW_IRON;
  var ore=BuildingPlacement.at(t.e,t.shop,6,highWall?-3:-5,8);t.l.setBlock(ore,rock.defaultBlockState(),2);var start=BuildingPlacement.at(t.e,t.shop,14,-7,7);f.npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);
  if(highWall){
   var eye=net.minecraft.world.phys.Vec3.atBottomCenterOf(BuildingPlacement.at(t.e,t.shop,6,-7,7)).add(0,f.npc.getEyeHeight(),0);
   var hit=t.l.clip(new net.minecraft.world.level.ClipContext(eye,net.minecraft.world.phys.Vec3.atCenterOf(ore),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,f.npc));
   h.assertTrue(!hit.getBlockPos().equals(ore)&&t.l.getBlockState(ore.north()).isAir(),"The high ore has an exposed near face even though stone below blocks its center");
  }
  h.assertTrue(MineOreWork.begin(f.npc,t.e,t.shop,f.state),"A known visible wall face is recorded before its physical trip"+oreDiagnostic(f));
  if(finalStride){var stand=BuildingPlacement.at(t.e,t.shop,6,-7,7);f.state.getCompound("mineOre").putLong("stand",stand.asLong());f.npc.moveTo(stand.getX()+.5+1.567347876206,stand.getY(),stand.getZ()+.5);
   // Reproduce the observed completed approximate path retained by the native
   // navigator. A fresh navigator can walk farther and masks this retry state.
   var stopped=f.npc.getNavigation().createPath(stand,1);h.assertTrue(stopped!=null&&stopped.canReach()&&!stopped.getEndNode().asBlockPos().equals(stand),"Prepared native route ends adjacent to the exact workstation");
   f.npc.getNavigation().moveTo(stopped,.8);stopped.setNextNodeIndex(stopped.getNodeCount());
  }
  var initialPosition=f.npc.position();
  f.state.putUUID("worker",f.npc.getUUID());MineWork.write(t.l,t.shop,f.state);
  f.npc.setNoAi(false);f.npc.goalSelector.removeAllGoals(g->true);f.npc.targetSelector.removeAllGoals(g->true);f.npc.goalSelector.addGoal(6,new ResourceWorkGoal(f.npc,true,()->6000));h.assertTrue(t.l.addFreshEntity(f.npc),"Physical ore worker registers: removed="+f.npc.isRemoved()+" existing="+t.l.getEntity(f.npc.getUUID())+" villageExists="+(SettlementData.get(t.l.getServer()).entry(t.s.id())!=null));
  h.onEachTick(()->{t.l.resetEmptyTime();if(t.l.getBlockState(ore).isAir()){
   var after=MineWork.read(t.l,t.shop);h.assertTrue(f.npc.tickCount>0&&f.npc.distanceToSqr(initialPosition)>(finalStride?1:16),"The worker physically walks to the ore before mining");h.assertTrue(ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),loot)==1,"Physical excavation retains its real cargo");h.assertTrue(ItemStack.of(after.getCompound("tool")).getDamageValue()==1,"The actual wall harvest wears the paid pick once");h.assertTrue(t.l.getBlockState(MineOreWork.stand(f.state).below()).is(Blocks.STONE),"Mining the wall retains the supporting gallery floor");if(highWall)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HIGH_WALL_ORE VERIFIED bodyTicks={} iron=1 pickWear=1",f.npc.tickCount);f.npc.discard();for(var cp:chunks)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,owned);doneOre(f);h.succeed();
  }});
  h.runAtTickTime(1100,()->{var diagnostic=MineWork.read(t.l,t.shop);var details="Ore trip stalled: pos="+f.npc.position()+" ownTicks="+f.npc.tickCount+" status="+f.npc.workStatus()+" ticking="+t.l.isPositionEntityTicking(f.npc.blockPosition())+" goals="+f.npc.runningGoals()+" failure="+diagnostic.getString("oreFailure")+" job="+diagnostic.getCompound("mineOre")+" path="+(f.npc.getNavigation().getPath()==null?null:f.npc.getNavigation().getPath().getTarget());f.npc.discard();for(var cp:chunks)t.l.getChunkSource().removeRegionTicket(ORE_TICKET,cp,3,owned);doneOre(f);h.assertTrue(false,details);});
 }
 @GameTest(template="empty",batch="exposed_ore",timeoutTicks=100)
 public static void minerTakesVisibleWallOreAndResumesTheSameDriveAfterReload(GameTestHelper h){
  var f=town(h,true);var t=f.town;oreGallery(f);h.runAfterDelay(5,()->{try{var ore=BuildingPlacement.at(t.e,t.shop,6,-5,8);t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);var old=MineWork.drive(f.state);var original=f.state.getUUID("operation");
   h.assertTrue(MineOreWork.begin(f.npc,t.e,t.shop,f.state),"Actual unmet iron demand selects its exposed gallery wall"+oreDiagnostic(f));h.assertTrue(MineOreWork.target(f.state).equals(ore),"Only the visible actual ore is selected");MineWork.write(t.l,t.shop,f.state);var state=MineWork.read(t.l,t.shop);var stand=MineOreWork.stand(state);f.npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);
   for(int i=0;i<90&&MineOreWork.active(state);i++)MineOreWork.tick(f.npc,state);
   h.assertTrue(!MineOreWork.active(state)&&t.l.getBlockState(ore).isAir(),"The real ore is excavated using ordinary break time");h.assertTrue(ForestFixture.count(state.getList("cargo",Tag.TAG_COMPOUND),Items.RAW_IRON)==1&&ItemStack.of(state.getCompound("tool")).getDamageValue()==1,"One mined raw iron and one tool wear");h.assertTrue(MineWork.drive(state).equals(old)&&state.getUUID("operation").equals(original),"The primary drive and its operation have not advanced");
  }finally{doneOre(f);}h.succeed();});
 }
 @GameTest(template="empty",batch="exposed_ore",timeoutTicks=100)
 public static void wallOreReceiptRecoversCargoOnceIncludingDeathCustody(GameTestHelper h){
  var f=town(h,true);var t=f.town;oreGallery(f);h.runAfterDelay(5,()->{try{var ore=BuildingPlacement.at(t.e,t.shop,6,-5,8);t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);h.assertTrue(MineOreWork.begin(f.npc,t.e,t.shop,f.state),"Actual exposed ore starts a durable job"+oreDiagnostic(f));f.state.putUUID("worker",f.npc.getUUID());MineWork.write(t.l,t.shop,f.state);var job=f.state.getCompound("mineOre");
   h.assertTrue(org.villageastra.persistence.WorldJournal.harvest(t.l,job.getUUID("id"),ore,Blocks.IRON_ORE.defaultBlockState(),ItemStack.of(job.getCompound("tool")))!=null,"Prepared fixture commits the actual harvest before its job checkpoint");
   var custody=JobCargo.snapshot(f.npc,true);h.assertTrue(ForestFixture.count(custody.items(),Items.RAW_IRON)==1,"Death custody recovers the single real journal-ahead ore");MineOreWork.reconcile(t.l,f.state);MineOreWork.reconcile(t.l,f.state);h.assertTrue(ForestFixture.count(f.state.getList("cargo",Tag.TAG_COMPOUND),Items.RAW_IRON)==1&&ItemStack.of(f.state.getCompound("tool")).getDamageValue()==1,"Replaying the receipt never repeats cargo or tool wear");
  }finally{doneOre(f);}h.succeed();});
 }
 @GameTest(template="empty",batch="exposed_ore",timeoutTicks=100)
 public static void exposedOreLeavesFloorsWetBoundariesUnseenRockAndWrongToolsAlone(GameTestHelper h){
  var f=town(h,true);var t=f.town;oreGallery(f);h.runAfterDelay(5,()->{try{var floor=BuildingPlacement.at(t.e,t.shop,6,-8,7);var ore=BuildingPlacement.at(t.e,t.shop,6,-5,8);t.l.setBlock(floor,Blocks.IRON_ORE.defaultBlockState(),2);h.assertTrue(!MineOreWork.begin(f.npc,t.e,t.shop,f.state),"The gallery floor cannot be excavated for ore");t.l.setBlock(ore,Blocks.DIAMOND_ORE.defaultBlockState(),2);h.assertTrue(!MineOreWork.begin(f.npc,t.e,t.shop,f.state),"Unneeded ore and insufficient stone tool cannot be selected");t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);t.l.setBlock(ore.south(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!MineOreWork.begin(f.npc,t.e,t.shop,f.state),"Water boundaries stay sealed");t.l.setBlock(ore.south(),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(ore.north(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!MineOreWork.begin(f.npc,t.e,t.shop,f.state),"Hidden ore behind an intact wall is unknown");t.l.setBlock(ore.north(),Blocks.AIR.defaultBlockState(),2);h.assertTrue(MineOreWork.begin(f.npc,t.e,t.shop,f.state),"A newly exposed dry ore face becomes available");t.l.setBlock(ore.south(),Blocks.WATER.defaultBlockState(),2);var stand=MineOreWork.stand(f.state);f.npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);MineOreWork.tick(f.npc,f.state);h.assertTrue(!MineOreWork.active(f.state)&&t.l.getBlockState(ore).is(Blocks.IRON_ORE),"Moving water cancels the uncommitted job without cutting the ore");
  }finally{doneOre(f);}h.succeed();});
 }
 @GameTest(template="empty",batch="mine_priority",timeoutTicks=100)
 public static void unmetOreKeepsUsableMinerOnUnsurveyedDriveWithoutChangingRecord(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   f.state.putString("stage","choose");MineWork.write(t.l,t.shop,f.state);var before=MineWork.read(t.l,t.shop);
   h.assertTrue(NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"Unsurveyed existing landings and actual iron demand take priority over a new surface trip");
   h.assertTrue(before.equals(MineWork.read(t.l,t.shop)),"Priority observation does not alter excavation or allocate ore");
   h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,f.state),"The ordinary planner can start the remaining real branch");MineWork.write(t.l,t.shop,f.state);
   h.assertTrue(NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"A runnable branch keeps the ore priority");
   f.state.putString("stage","dig");f.state.put("before",NbtUtils.writeBlockState(Blocks.IRON_ORE.defaultBlockState()));MineWork.write(t.l,t.shop,f.state);
   h.assertTrue(NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"An actual iron face is runnable with its stone pick");
   f.state.put("before",NbtUtils.writeBlockState(Blocks.DIAMOND_ORE.defaultBlockState()));MineWork.write(t.l,t.shop,f.state);
   h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"A tool tier obstacle leaves surface supply available");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_priority",timeoutTicks=100)
 public static void miningPriorityYieldsForStoredOreMissingToolsUnsafeOrExhaustedRows(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   f.state.putString("stage","choose");MineWork.write(t.l,t.shop,f.state);
   var own=LogisticsRoutes.chest(t.l,t.e,t.shop);own.setItem(0,new ItemStack(Items.RAW_IRON,8));
   h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"Ore awaiting the porter is not an unmet mining demand");own.clearContent();
   f.state.remove("tool");MineWork.write(t.l,t.shop,f.state);h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"A missing pick must not pin mining");
   var worn=new ItemStack(Items.STONE_PICKAXE);worn.setDamageValue(worn.getMaxDamage());f.state.put("tool",worn.save(new CompoundTag()));MineWork.write(t.l,t.shop,f.state);h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"A worn pick must not pin mining");
   f.state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));f.state.putString("status","unsafe_ground");MineWork.write(t.l,t.shop,f.state);h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"Safety obstacles leave another supply source available");
   f.state.remove("status");f.state.putString("stage","upgrade_tool");MineWork.write(t.l,t.shop,f.state);h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"Tool replacement can require surface materials");
   f.state.putString("stage","choose");f.state.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,f.state.getInt("floorStep")).toArray());MineWork.write(t.l,t.shop,f.state);
   h.assertTrue(!NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"All exhausted unlocked rows yield to surface supply");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_priority",timeoutTicks=100)
 public static void anExistingNaturalTripFinishesBeforeMiningPriority(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   f.state.putString("stage","choose");MineWork.write(t.l,t.shop,f.state);
   var trip=new CompoundTag();trip.putUUID("id",UUID.randomUUID());trip.putString("stage","dig");trip.putLong("target",t.e.center().asLong());trip.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));
   org.villageastra.persistence.NbtRecord.write(NaturalSupplyGoal.path(t.l,f.npc.getUUID()),trip);
   h.assertTrue(NaturalSupplyGoal.miningPriority(t.l,t.e,f.npc),"Fixture has an actual mining priority");
   h.assertTrue(new NaturalSupplyGoal(f.npc,true).canUse(),"The already recorded surface trip resumes instead of abandoning its custody");
   h.assertTrue(trip.equals(NaturalSupplyGoal.inspect(t.l,f.npc.getUUID())),"Checking the priority cannot drop or replace the existing trip");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=200)
 public static void missingOreStartsShallowerBranchAndActuallyMinesItAfterReload(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,f.state),"An exhausted mine with an unfunded iron order starts prospecting");
   h.assertTrue(f.state.getInt("floorStep")==18&&f.state.getInt("extentStep")==25,"A new branch uses existing shallower stairs and preserves the deepest claim");
   MineWork.write(t.l,t.shop,f.state);var saved=MineWork.read(t.l,t.shop);var next=MineWork.next(t.l,t.e,t.shop,saved);h.assertTrue(next.stage()==MineDrive.Stage.EAST&&saved.getInt("floorStep")==18,"Reload retains its survey floor");
   var target=MineWork.at(t.e,t.shop,next.cell());var access=MineWork.at(t.e,t.shop,next.stand());
   for(int x=-2;x<=2;x++)for(int y=-1;y<=5;y++)for(int z=-2;z<=2;z++)t.l.setBlock(access.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   t.l.setBlock(target,Blocks.IRON_ORE.defaultBlockState(),2);f.npc.moveTo(access.getX()+.5,access.getY(),access.getZ()+.5);f.npc.setOnGround(true);
   var goal=new ResourceWorkGoal(f.npc,true,()->6000);h.assertTrue(goal.canUse(),"The real mining goal resumes the survey");goal.tick();for(int i=0;i<120&&!t.l.getBlockState(target).isAir();i++)goal.tick();
   var done=MineWork.read(t.l,t.shop);h.assertTrue(t.l.getBlockState(target).isAir(),"The normal goal excavates the actual new ore face");h.assertTrue(done.getList("cargo",Tag.TAG_COMPOUND).stream().map(raw->ItemStack.of((CompoundTag)raw)).filter(stack->stack.is(Items.RAW_IRON)).mapToInt(ItemStack::getCount).sum()==1,"The mined ore is carried, not generated by the planner");
   h.assertTrue(t.s.mineAreas().get(t.shop.id()).lastStep()==25&&t.s.mineAreas().get(t.shop.id()).galleries().stream().anyMatch(g->g.step()==18),"New gallery protection retains the old deepest staircase");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=200)
 public static void oreAlreadyInMineChestDoesNotTriggerUnneededProspecting(GameTestHelper h){
  var f=town(h);var t=f.town;try{LogisticsRoutes.chest(t.l,t.e,t.shop).setItem(0,new ItemStack(Items.RAW_IRON,8));h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,f.state),"Stored ore waiting for its porter satisfies the real shortage");}finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=200)
 public static void exhaustedSurveyFloorsNeverRepeatAcrossCheckpoints(GameTestHelper h){
  var f=town(h);var t=f.town;try{var state=f.state;
   for(int expected:new int[]{18,12,6,0}){h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,state),"A remaining shallower survey is available");h.assertTrue(state.getInt("floorStep")==expected,"Survey floors advance once in order");state.putInt("side",MineDrive.DONE);MineWork.write(t.l,t.shop,state);state=MineWork.read(t.l,t.shop);}
   var seen=new HashSet<Integer>(Set.of(0,6,12,18,25));
   while(MineProspecting.begin(t.l,t.e,t.shop,state)){int floor=state.getInt("floorStep");h.assertTrue(floor>=0&&floor<=25&&seen.add(floor),"Refined surveys do not repeat or exceed the unlocked depth");state.putInt("side",MineDrive.DONE);MineWork.write(t.l,t.shop,state);state=MineWork.read(t.l,t.shop);}
   h.assertTrue(seen.size()==26,"All skipped existing landings were surveyed before exhaustion");
   h.assertTrue(!MineProspecting.begin(t.l,t.e,t.shop,state),"Exhausted existing stairs do not repeat old branches or go deeper");
   var area=new MineArea(90,3,5,7);for(int floor=0;floor<=84;floor+=6)for(int side=0;side<2;side++)area=area.with(new MineArea.Gallery(floor,side,4));
   h.assertTrue(area.galleries().size()==30&&area.contains(5,-84-7,7+84,0)&&area.contains(5,-7,7,0),"Many survey branches retain both early and deep protection");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="fine_prospect",timeoutTicks=200)
 public static void unmetOreDemandSurveysEverySkippedUnlockedFloorOnce(GameTestHelper h){
  var f=town(h);var t=f.town;try{var state=f.state;state.putIntArray("surveyedFloors",new int[]{0,6,12,18,25});var seen=new HashSet<Integer>(Set.of(0,6,12,18,25));
   for(int floor=24;floor>=0;floor--){if(seen.contains(floor))continue;
    h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,state),"Missing ore still requires the skipped floor "+floor);
    h.assertTrue(state.getInt("floorStep")==floor&&seen.add(floor),"Fine survey stays within unlocked stairs and never repeats a row");
    MineWork.write(t.l,t.shop,state);state=MineWork.read(t.l,t.shop);h.assertTrue(MineWork.next(t.l,t.e,t.shop,state).stage()==MineDrive.Stage.EAST,"The saved row resumes as a normal mining drive");state.putInt("side",MineDrive.DONE);
   }
   h.assertTrue(seen.size()==26&&!MineProspecting.begin(t.l,t.e,t.shop,state),"Only a genuinely exhausted set of all unlocked floors ends the bounded search");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=100)
 public static void visibleNeededOreContinuesBetweenCoarseSurveyRows(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   t.s.noteMine(t.shop.id(),new MineArea.Gallery(12,MineDrive.WEST,14));var area=t.s.mineAreas().get(t.shop.id());
   var open=BuildingPlacement.at(t.e,t.shop,-10,-18,19);var ore=BuildingPlacement.at(t.e,t.shop,-10,-18,20);
   t.l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);t.l.setBlock(open,Blocks.STONE.defaultBlockState(),2);
   var visited=Set.of(0,6,12,18,25);var demand=Set.of(Items.RAW_IRON);
   h.assertTrue(MineOutcrops.floor(t.l,t.e,t.shop,area,25,visited,demand)==-1,"Unexposed ore is not visible through the gallery wall");
   t.l.setBlock(open,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(MineOutcrops.floor(t.l,t.e,t.shop,area,25,visited,Set.of(Items.RAW_COPPER))==-1,"Only needed raw materials direct the search");
   h.assertTrue(MineOutcrops.floor(t.l,t.e,t.shop,area,12,visited,demand)==-1,"Visible ore never bypasses the working depth limit");
   h.assertTrue(MineOutcrops.floor(t.l,t.e,t.shop,new MineArea(25,3,5,7),25,visited,demand)==-1,"Unexplored caves are not known galleries");
   f.state.putIntArray("surveyedFloors",new int[]{0,6,12,18,25});
   h.assertTrue(MineProspecting.begin(t.l,t.e,t.shop,f.state)&&f.state.getInt("floorStep")==13,"An exposed iron vein starts the skipped row after coarse prospecting is exhausted");
   MineWork.write(t.l,t.shop,f.state);var saved=MineWork.read(t.l,t.shop);h.assertTrue(MineWork.next(t.l,t.e,t.shop,saved).stage()==MineDrive.Stage.EAST&&saved.getInt("floorStep")==13,"Normal excavation resumes this ore-directed branch after reload");
   h.assertTrue(MineOutcrops.floor(t.l,t.e,t.shop,area,25,Set.of(0,6,12,13,18,25),demand)==-1,"Already surveyed rows are not restarted for the same ore");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=100)
 public static void aGalleryAboveAnOpenCaveTurnsBeforeMining(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   var state=f.state;state.putInt("prospectFloor",0);state.putInt("prospectLimit",25);state.putInt("floorStep",0);state.putInt("step",1);state.putInt("side",MineDrive.EAST);state.putInt("cell",0);state.putInt("run",0);state.putString("stage","choose");
   var next=MineWork.next(t.l,t.e,t.shop,state);var target=MineWork.at(t.e,t.shop,next.cell());var floor=BuildingPlacement.at(t.e,t.shop,next.cell().x(),-8,next.cell().z());t.l.setBlock(target,Blocks.STONE.defaultBlockState(),2);t.l.setBlock(floor,Blocks.AIR.defaultBlockState(),2);MineWork.write(t.l,t.shop,state);
   var goal=new ResourceWorkGoal(f.npc,true,()->6000);h.assertTrue(goal.canUse(),"Normal miner resumes the gallery");goal.tick();
   var saved=MineWork.read(t.l,t.shop);h.assertTrue(saved.getInt("side")==MineDrive.WEST&&t.l.getBlockState(target).is(Blocks.STONE),"A missing floor turns the drive before cutting or walking into the cave");
   t.l.setBlock(floor,Blocks.STONE.defaultBlockState(),2);h.assertTrue(MineWork.galleryFloor(t.l,t.e,t.shop,state,next.cell()),"A real dry floor permits normal mining");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=100)
 public static void abandonedUnsupportedBeamReturnsOnlyUnplacedPaidTimber(GameTestHelper h){
  var f=town(h);var t=f.town;try{
   var state=f.state;state.putInt("floorStep",0);state.putInt("step",1);state.putInt("side",0);state.putInt("run",3);state.putInt("cell",4);state.putString("stage","support_place");
   var next=MineDrive.next(MineWork.drive(state),0,MineWork.shape(state));MineWork.chose(state,next);h.assertTrue(next.beam()!=null,"Fixture is a real beam column");state.put("supportTimber",new ListTag());MineTimber.add(state,new ItemStack(Items.BIRCH_LOG));state.putInt("support_fetched",1);
   var foot=MineWork.at(t.e,t.shop,MineWork.beamStand(state));t.l.setBlock(foot.below(),Blocks.AIR.defaultBlockState(),2);var beam=MineWork.at(t.e,t.shop,next.beam().cells().get(0));t.l.setBlock(beam,Blocks.AIR.defaultBlockState(),2);MineWork.write(t.l,t.shop,state);
   var goal=new ResourceWorkGoal(f.npc,true,()->6000);h.assertTrue(goal.canUse(),"Saved beam resumes");goal.tick();var saved=MineWork.read(t.l,t.shop);
   h.assertTrue(saved.getString("stage").equals("choose")&&!saved.contains("beam")&&saved.getList("cargo",Tag.TAG_COMPOUND).stream().map(raw->ItemStack.of((CompoundTag)raw)).filter(st->st.is(Items.BIRCH_LOG)).mapToInt(ItemStack::getCount).sum()==1,"The actual paid log returns to ordinary carried cargo");
   h.assertTrue(saved.getList("cargo",Tag.TAG_COMPOUND).stream().map(raw->ItemStack.of((CompoundTag)raw)).mapToInt(ItemStack::getCount).sum()==1,"No other species or extra logs are added");
   h.assertTrue(t.l.getBlockState(beam).isAir(),"No beam is placed remotely over the cave");
   h.assertTrue(org.villageastra.persistence.WorldJournal.place(t.l,MineWork.beamId(state,state.getUUID("operation"),0),beam,Blocks.AIR.defaultBlockState(),Blocks.BIRCH_LOG.defaultBlockState()),"Fixture commits placement before a stale checkpoint");
   MineTimber.cancel(t.l,state);h.assertTrue(state.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Journal-ahead placed wood is not duplicated by cancellation");
  }finally{f.npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_prospect",timeoutTicks=100)
 public static void deeperUpgradeResumesDeepestStairAndNeverBypassesDepthLimit(GameTestHelper h){
  var t=new CompoundTag();t.putInt("prospectFloor",18);t.putInt("prospectLimit",25);t.putInt("extentStep",25);t.putInt("step",19);t.putInt("cell",3);t.putInt("side",1);t.putInt("run",6);t.putInt("galleryOf",18);t.putInt("prospectLength",48);t.putBoolean("prospectExtension",true);
  h.assertTrue(MineProspecting.activeFloor(t,12)==12,"A reduced working level still limits the survey");
  h.assertTrue(MineProspecting.activeFloor(t,40)==40&&t.getInt("step")==26&&t.getInt("cell")==0&&!t.contains("prospectFloor")&&!t.contains("galleryOf")&&!t.contains("prospectLength")&&!t.contains("prospectExtension"),"A higher level resumes below the deepest existing staircase");h.succeed();
 }
}
