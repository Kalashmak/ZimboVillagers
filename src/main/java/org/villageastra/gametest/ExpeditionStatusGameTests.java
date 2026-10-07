package org.villageastra.gametest;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ExpeditionStatusGameTests {
 @GameTest(template="empty",batch="expedition_status",timeoutTicks=900)
 public static void readyExpeditionResumesAfterDelayedLowerStatus(GameTestHelper h){check(h,true);}
 @GameTest(template="empty",batch="expedition_status",timeoutTicks=900)
 public static void completedExpeditionDoesNotReviveTrackedChunk(GameTestHelper h){check(h,false);}
 private static void check(GameTestHelper h,boolean active){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO).offset(active?1_501_024:1_501_536,20,0);var cp=new ChunkPos(p);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)l.getChunk(cp.x+x,cp.z+z);
  var s=new Settlement(UUID.randomUUID());var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));
  SettlementData.get(l.getServer()).add(new SettlementData.Entry(s,l.dimension().location().toString(),p));
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);
  npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);npc.setNoGravity(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var trip=new CompoundTag();trip.putUUID("id",UUID.randomUUID());trip.putString("stage","carry");var cargo=new ListTag();cargo.add(new ItemStack(Items.SAND,16).save(new CompoundTag()));trip.put("cargo",cargo);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),trip);
  ResourceExpedition.follow(npc,true);h.assertTrue(l.addFreshEntity(npc),"Real expedition body added");
  int[] before={0};
  h.startSequence().thenWaitUntil(()->h.assertTrue(npc.tickCount>5&&TouchLoad.ticking(l,p),"Native entity ticking has started"))
   .thenExecute(()->{
    var map=l.getChunkSource().chunkMap;var holder=map.getVisibleChunkIfPresent(cp.toLong());
    h.assertTrue(holder!=null&&holder.getEntityTickingChunkFuture().getNow(null)!=null&&holder.getEntityTickingChunkFuture().getNow(null).left().isPresent(),"Holder has a fully ready native entity chunk");
    // Reproduce the captured disagreement; no body tick is called by the fixture.
    map.onFullChunkStatusChange(cp,FullChunkStatus.BLOCK_TICKING);before[0]=npc.tickCount;
    h.assertTrue(!TouchLoad.ticking(l,p),"Delayed lower status suspends native entity ticks despite the live lease");
    if(!active){trip.putBoolean("complete",true);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),trip);}
   }).thenIdle(240).thenExecute(()->{
    boolean resumed=npc.tickCount>before[0]+10&&TouchLoad.ticking(l,p);
    boolean unchanged=NaturalSupplyGoal.inspect(l,npc.getUUID()).getList("cargo",Tag.TAG_COMPOUND).equals(cargo);
    boolean forced=l.getForcedChunks().contains(cp.toLong());
    var detail="bodyBefore="+before[0]+" bodyAfter="+npc.tickCount+" ticking="+TouchLoad.ticking(l,p);
    NaturalSupplyGoal.release(l,npc.getUUID());npc.discard();SettlementData.get(l.getServer()).remove(s.id());
    h.assertTrue(active?resumed:npc.tickCount==before[0],"Only an active expedition restores native ticks: "+detail);
    h.assertTrue(unchanged&&!forced,"Cargo unchanged and no permanent forced chunks");
   }).thenIdle(160).thenWaitUntil(()->h.assertTrue(!TouchLoad.ticking(l,p),"Finished expedition still releases its chunk")).thenSucceed();
 }
}
