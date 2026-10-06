package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SupplyProgressGameTests {
 @GameTest(template="empty",batch="supply_progress",timeoutTicks=24000)
 public static void slowButAdvancingWorkerFinishesLongTripInsteadOfReturningEmpty(GameTestHelper h){trip(h,false,false);}
 @GameTest(template="empty",batch="supply_stalled",timeoutTicks=5000)
 public static void workerWithoutAnyProgressStillAbandonsAnUnharvestedTarget(GameTestHelper h){trip(h,true,false);}
 @GameTest(template="empty",batch="supply_route_reuse",timeoutTicks=24000)
 public static void carrierKeepsAdvancingRouteButDetoursAroundANewPhysicalWall(GameTestHelper h){trip(h,false,true);}
 private static void trip(GameTestHelper h,boolean stuck,boolean efficient){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(stuck?69632:65536),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+165)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-2;x<=165;x++)for(int z=-2;z<=10;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var target=base.offset(160,1,2);l.setBlock(target,Blocks.SAND.defaultBlockState(),2);var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.setOnGround(true);npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(stuck?0:.12);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var stand=HarvestAccess.find(npc,target,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(stand!=null,"Distant target has a real route");var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putLong("target",target.asLong());t.putLong("stand",stand.asLong());t.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));t.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),t);var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,supply);boolean[] wall={false};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added"));
  h.onEachTick(()->{if(efficient&&!wall[0]&&npc.getX()>base.getX()+40){wall[0]=true;for(int z=-2;z<=8;z++)for(int y=1;y<=3;y++)l.setBlock(base.offset(80,y,z),Blocks.STONE.defaultBlockState(),3);}
   var state=NaturalSupplyGoal.inspect(l,npc.getUUID());if(!state.getBoolean("complete"))return;
   h.assertTrue(npc.tickCount>=2400,"Exercise a trip longer than the old total-travel limit");
   if(stuck)h.assertTrue(chest.countItem(Items.SAND)==0&&l.getBlockState(target).is(Blocks.SAND),"Stalled worker stops without remote harvesting or invented cargo");
   else h.assertTrue(chest.countItem(Items.SAND)==1&&l.getBlockState(target).isAir(),"Moving worker must finish the real harvest and delivery, not return empty at "+npc.position()+" ticks="+npc.tickCount);
   // At least 95% fewer explicit plans than a fresh search every 20 body ticks;
   // leave room for legitimate retries while the new wall invalidates the route.
   if(efficient){h.assertTrue(wall[0]&&npc.getHealth()==npc.getMaxHealth(),"Actual worker safely detours around the newly built wall");h.assertTrue(supply.routePlans()<=Math.max(2,npc.tickCount/400),"Advancing trip should retain its route instead of repeatedly exploring the whole terrain: plans="+supply.routePlans()+" ticks="+npc.tickCount);com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SUPPLY_ROUTE plans={} bodyTicks={} wall={} realSand={}",supply.routePlans(),npc.tickCount,wall[0],chest.countItem(Items.SAND));}
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(stuck?4800:23800,()->h.assertTrue(false,"Supply trip stalled at "+npc.position()+" ticks="+npc.tickCount+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
