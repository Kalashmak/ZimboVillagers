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

/** An already paid trip, actual partial navigation, then a genuinely opened bridge. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PartialSupplyRouteGameTests {
 @GameTest(template="empty",batch="partial_supply_route",timeoutTicks=4000)
 public static void progressingPartialTripKeepsItsRouteAndReplansAtTheEnd(GameTestHelper h){
  exercise(h,true);
 }
 @GameTest(template="empty",batch="partial_supply_route",timeoutTicks=4000)
 public static void permanentlyBlockedPartialTripStopsAndReturnsItsUnusedLoan(GameTestHelper h){exercise(h,false);}
 @GameTest(template="empty",batch="partial_supply_route",timeoutTicks=8000)
 public static void stationaryRetryDetectsALateBridgeBeforeAbandoningThePaidTrip(GameTestHelper h){exercise(h,true,true);}
 private static void exercise(GameTestHelper h,boolean bridge){
  exercise(h,bridge,false);
 }
 private static void exercise(GameTestHelper h,boolean bridge,boolean late){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+655360+(late?131072:bridge?0:65536),120,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+183)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=165;x++)for(int z=-6;z<=6;z++)for(int y=-30;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0&&(x<=18||x>=25)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var ore=base.offset(160,1,0);var stand=ore.west();l.setBlock(ore,Blocks.COAL_ORE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var job=UUID.randomUUID();var borrowed=WorldJournal.takeAmount(l,Settlement.childId(job,"tool"),stock,0,chest.getItem(0).copy(),1);h.assertTrue(borrowed.is(Items.STONE_PICKAXE),"Prepared trip has one actual journalled loan");
  var state=new CompoundTag();state.putUUID("id",job);state.putBoolean("quarry",true);state.putString("stage","dig");state.putLong("target",ore.asLong());state.putLong("stand",stand.asLong());state.put("before",NbtUtils.writeBlockState(l.getBlockState(ore)));NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);
  var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(1,supply);
  h.runAtTickTime(100,()->{
   var path=npc.getNavigation().getPath();
   h.assertTrue(npc.getX()>base.getX()+8&&path!=null&&!path.canReach()&&!path.isDone(),"Body makes real progress along an unfinished partial route");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_PARTIAL_ROUTE plans={} bodyTicks={} x={} end={}",supply.routePlans(),npc.tickCount,npc.getX()-base.getX(),path.getEndNode());
   h.assertTrue(supply.routePlans()<=1,"Progressing partial route is retained rather than replanned every20ticks: plans="+supply.routePlans());
   if(bridge&&!late)for(int x=19;x<=24;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
  });
  if(late){
   h.runAtTickTime(2000,()->{
    h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getString("stage").equals("dig")&&npc.getX()<base.getX()+20&&chest.countItem(Items.COAL)==0,"Unreachable ore is still unworked after a long real wait");
    for(int x=19;x<=24;x++)for(int z=-6;z<=6;z++)l.setBlock(base.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   });
   h.runAtTickTime(2140,()->h.assertTrue(npc.getX()>base.getX()+20,"Stationary retry detects the actual late bridge and moves within its bounded interval"));
  }
  h.startSequence().thenWaitUntil(()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")&&chest.countItem(Items.COAL)==(bridge?1:0),"The real trip must finish after delivery or a bounded refusal"))
   .thenExecute(()->{
    h.assertTrue(supply.routePlans()>1&&l.getBlockState(ore).is(bridge?Blocks.AIR:Blocks.COAL_ORE)&&chest.countItem(Items.STONE_PICKAXE)==1,"Route is recomputed at the end and the original loan returns once");
    var finished=NaturalSupplyGoal.inspect(l,npc.getUUID());h.assertTrue(finished.getInt("labor")== (bridge?200:0)&&npc.getHealth()==npc.getMaxHealth(),"Actual paid labor, no damage or skipped work");
    if(!bridge){h.assertTrue(npc.tickCount>=2400,"The unchanged non-progress budget is actually paid");h.assertTrue(supply.routePlans()<=35,"A stationary dead end must not trigger the same expensive search every20ticks: plans="+supply.routePlans());for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.STONE_PICKAXE))h.assertTrue(chest.getItem(slot).getDamageValue()==0,"Unworked ore does not wear the returned pick");}
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_PARTIAL_ROUTE VERIFIED plans={} ticks={} coal={} labor={}",supply.routePlans(),npc.tickCount,bridge?1:0,finished.getInt("labor"));
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   }).thenSucceed();
 }
}
