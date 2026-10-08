package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Prepared completed loose-trip metadata; the distinct new physical sand harvest and delivery are exercised. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LooseDepositResumeGameTests {
 @GameTest(template="empty",batch="loose_deposit_resume",timeoutTicks=2400)
 public static void returnedGathererRevisitsRemainingSandBeforeTheDistantSurveyCursor(GameTestHelper h){
  exercise(h,0);
 }
 @GameTest(template="empty",batch="loose_deposit_resume",timeoutTicks=200)
 public static void floodedRememberedSandDoesNotStartOrReplayCargo(GameTestHelper h){exercise(h,1);}
 @GameTest(template="empty",batch="loose_deposit_resume",timeoutTicks=200)
 public static void vanishedRememberedSandIsCheckedOnceWithoutMiningReplacement(GameTestHelper h){exercise(h,2);}
 @GameTest(template="empty",batch="loose_deposit_resume",timeoutTicks=200)
 public static void changedDemandDoesNotRestartOldSandTrip(GameTestHelper h){exercise(h,3);}
 @GameTest(template="empty",batch="loose_deposit_resume",timeoutTicks=2400)
 public static void inaccessibleFirstBlockDoesNotHideReachableDepositRemainder(GameTestHelper h){exercise(h,4);}
 @GameTest(template="empty",batch="supply_search_expansion",timeoutTicks=1200)
 public static void exhaustedNearbySurveyExpandsAndPersistsAnUnpaidOuterDeposit(GameTestHelper h){exercise(h,5);}
 @GameTest(template="empty",batch="supply_search_expansion",timeoutTicks=1200)
 public static void secondExhaustedRingFindsDepositWithinTheExistingRouteLimit(GameTestHelper h){exercise(h,6);}
 @GameTest(template="empty",batch="supply_search_expansion",timeoutTicks=1200)
 public static void exhaustedMaximumRingDoesNotWidenBeyondTheRouteLimit(GameTestHelper h){exercise(h,7);}
 @GameTest(template="empty",batch="loose_surface_priority",timeoutTicks=1200)
 public static void newSandDemandDoesNotWaitForDeepMineralSurveyToWrap(GameTestHelper h){exercise(h,8);}
 private static void exercise(GameTestHelper h,int mode){
  boolean flooded=mode==1,expansion=mode>=5&&mode<=7;
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+589824+mode*65536,120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  int depositDistance=expansion?193+(mode-5)*64:25;
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+(expansion?depositDistance+28:53))>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=depositDistance+11;x++)for(int z=-6;z<=6;z++)for(int y=-3;y<=6;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  // The first three equal-distance columns of the next ring are not the
  // prepared east corridor. Cap their random terrain so this expansion test
  // measures the intended fourth column, not unrelated generated sand banks.
  if(expansion)for(var p:List.of(base.offset(-depositDistance,0,0),base.offset(0,0,-depositDistance),base.offset(0,0,depositDistance))){
   l.getChunk(p.getX()>>4,p.getZ()>>4);for(int y=0;y<=12;y++)l.setBlock(p.above(y),Blocks.STONE.defaultBlockState(),2);
  }
  var ore=base.offset(depositDistance,1,0);l.setBlock(ore,Blocks.SAND.defaultBlockState(),2);
  var reachable=mode==4?base.offset(28,1,0):ore;
  if(mode==4){
   for(int x=22;x<=26;x++)for(int z=-2;z<=2;z++)for(int y=-3;y<=0;y++)l.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
   l.setBlock(ore.below(),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(reachable,Blocks.SAND.defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",-40,0,-40);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(3);chest.setItem(0,pick);chest.setItem(1,new ItemStack(Items.COBBLESTONE,3));
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);
  work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:sand",1);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var old=new CompoundTag();var previous=UUID.randomUUID();old.putUUID("id",previous);old.putBoolean("complete",true);old.putBoolean("quarry",false);old.putString("stage","carry");old.putLong("target",ore.west().asLong());old.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));old.putInt("surveyCursor",385*385-1);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),old);
  if(expansion){old.remove("target");old.remove("before");int radius=mode==5?192:mode==6?256:320;old.putInt("surveyRadius",radius);old.putInt("surveyCursor",(radius*2+1)*(radius*2+1)-1);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),old);}
  if(mode==8){old.remove("target");old.remove("before");old.putInt("surveyRadius",320);old.putInt("surveyCursor",300000);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),old);}
  if(flooded)l.setBlock(ore.east(),Blocks.WATER.defaultBlockState(),2);
  if(mode==2)l.setBlock(ore,Blocks.STONE.defaultBlockState(),2);
  if(mode==3){cost.remove("minecraft:sand");cost.putInt("minecraft:clay_ball",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);}
  var supply=new NaturalSupplyGoal(npc,true);
  if(mode==7){
   for(int tick=0;tick<400;tick+=20){npc.tickCount=tick;TouchLoad.resetTick();h.assertTrue(!supply.canUse(),"No survey may start the sole deposit outside its maximum range");}
   var scanned=NaturalSupplyGoal.inspect(l,npc.getUUID());
   h.assertTrue(scanned.getInt("surveyRadius")==320&&scanned.getInt("surveyCursor")>=0&&scanned.getInt("surveyCursor")<641*641&&!NaturalSupplyGoal.active(scanned),"Maximum ring wraps within its durable limit and starts no unpaid outer job");
   h.assertTrue(chest.countItem(Items.SAND)==0&&l.getBlockState(ore).is(Blocks.SAND),"Bounded sensing neither harvests nor delivers the outside deposit");
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();return;
  }
  if(expansion){
   int expectedRadius=mode==5?256:320;int prefix=mode==5?385*385:513*513;
   h.startSequence().thenWaitUntil(()->h.assertTrue(supply.canUse(),"Outer survey: ticks="+npc.tickCount+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())))
    .thenExecute(()->{
     var selected=NaturalSupplyGoal.inspect(l,npc.getUUID());
     h.assertTrue(BlockPos.of(selected.getLong("target")).equals(ore)&&selected.getInt("surveyRadius")==expectedRadius&&selected.getInt("surveyCursor")>=prefix,"Widening preserves the old search prefix and selects the outer deposit");
     var stand=BlockPos.of(selected.getLong("stand"));var nativePath=npc.routeTo(stand,0,NaturalSupplyGoal.ROUTE_RANGE);
     h.assertTrue(nativePath!=null&&nativePath.canReach()&&HarvestAccess.survivesExtraction(nativePath,ore),"An outer candidate still needs a real native reversible route");
     h.assertTrue(new NaturalSupplyGoal(npc,true).canUse()&&NaturalSupplyGoal.inspect(l,npc.getUUID()).getInt("surveyRadius")==expectedRadius,"Reload preserves the wider unpaid job");
     h.assertTrue(chest.countItem(Items.SAND)==0&&l.getBlockState(ore).is(Blocks.SAND)&&chest.getItem(0).getDamageValue()==3,"Sensing creates no cargo, free mining or tool wear");
     com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SEARCH_EXPANSION VERIFIED radius={} cursor={} target={} native=true unpaid=true",expectedRadius,selected.getInt("surveyCursor"),ore);
     npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
    }).thenSucceed();return;
  }
  if(mode!=0&&mode!=4&&mode!=8){
   h.assertTrue(!supply.canUse(),"Unsafe, vanished or unneeded remembered sand cannot start a new trip");
   h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("looseResumeChecked")== (mode!=3),"Only a deposit for current demand is checked, at most once per completed trip");
   h.assertTrue(l.getBlockState(ore).is(mode==2?Blocks.STONE:Blocks.SAND)&&chest.countItem(Items.SAND)==0&&chest.getItem(0).getDamageValue()==3&&chest.countItem(Items.COBBLESTONE)==3,"No free harvest, borrowed tool, wear or replayed cargo");
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();return;
  }
  var job=new UUID[1];var selectedTarget=new BlockPos[1];
  h.startSequence().thenWaitUntil(()->h.assertTrue(supply.canUse(),"Loose deposit search: ticks="+npc.tickCount+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())))
   .thenExecute(()->{
  var selected=NaturalSupplyGoal.inspect(l,npc.getUUID());job[0]=selected.getUUID("id");
  selectedTarget[0]=BlockPos.of(selected.getLong("target"));
  h.assertTrue(!job[0].equals(previous)&&(selectedTarget[0].equals(reachable)||mode==4&&selectedTarget[0].equals(ore))&&selected.getString("stage").equals("dig"),"Grouped sensing starts a distinct, unpaid job at an accessible block, without replaying old cargo");
  h.assertTrue(chest.countItem(Items.SAND)==0&&chest.getItem(0).getDamageValue()==3&&l.getBlockState(ore).is(Blocks.SAND),"Sensing neither borrows nor mines");
  npc.goalSelector.addGoal(1,supply);
   }).thenWaitUntil(()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")&&chest.countItem(Items.SAND)==(mode==4?2:1),"Actual new trip must mine and deliver its real deposit cargo"))
   .thenExecute(()->{
    var finished=NaturalSupplyGoal.inspect(l,npc.getUUID());
    h.assertTrue(finished.getInt("labor")==200&&l.getBlockState(selectedTarget[0]).isAir(),"Exactly 200 actual labor ticks and physical sand removal");
    if(mode==4)h.assertTrue(l.getBlockState(ore).isAir()&&l.getBlockState(reachable).isAir()&&finished.getInt("delivered")==2,"Existing bulk harvesting reaches both real blocks and delivers two once");
    h.assertTrue(chest.countItem(Items.COBBLESTONE)==3&&chest.countItem(Items.STONE_PICKAXE)==1,"Old cargo is not replayed and the new trip returns once");
    ItemStack returned=ItemStack.EMPTY;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.STONE_PICKAXE))returned=chest.getItem(slot);
    h.assertTrue(returned.getDamageValue()==3&&WorldJournal.recoverExisting(l,job[0])!=null,"The loose-material trip does not borrow or wear a pick and has an actual harvest receipt");
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_LOOSE_RESUME VERIFIED old={} new={} finalBlockLabor={} wear=3->3 sand={} mode={}",previous,job[0],finished.getInt("labor"),chest.countItem(Items.SAND),mode);
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   }).thenSucceed();
 }
}
