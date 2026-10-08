package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LocalWorkerLeaseGameTests {
 private static final TicketType<UUID> CENTER=TicketType.create("zimbovillagers_worker_fixture",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="local_worker_lease",timeoutTicks=1000)
 public static void assignedCookAtLoadedEdgeResumesAndReleasesWhenVillageSleeps(GameTestHelper h){check(h,true);}
 @GameTest(template="empty",batch="local_worker_lease",timeoutTicks=1000)
 public static void distantCookDoesNotWakeAnUnvisitedVillage(GameTestHelper h){check(h,false);}
 private static void check(GameTestHelper h,boolean active){
  var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO).offset(active?1_503_000:1_504_000,20,0);var edge=base.offset(144,0,0);var center=new ChunkPos(base);var cp=new ChunkPos(edge);var owner=UUID.randomUUID();
  if(active)l.getChunkSource().addRegionTicket(CENTER,center,3,owner);
  l.getChunkSource().addRegionTicket(CENTER,cp,0,owner);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)l.getChunk(cp.x+x,cp.z+z);
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++)l.setBlock(edge.offset(x,y,z),(y<0?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var kitchen=new Settlement.Building(UUID.randomUUID(),"restaurant",144,0,0);s.addBuilding(kitchen);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BAKER,kitchen.id());npc.bind(s.id(),r);npc.moveTo(edge.getX()+.5,edge.getY(),edge.getZ()+.5);npc.setNoGravity(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);h.assertTrue(l.addFreshEntity(npc),"Loaded saved worker body exists");
  h.startSequence().thenWaitUntil(()->{
   h.assertTrue(l.getEntity(npc.getUUID())==npc,"Body tracked by the native entity manager");
   if(active)h.assertTrue(TouchLoad.ticking(l,base),"Visited center ready");
  }).thenExecute(()->{
   h.assertTrue(npc.tickCount==0&&!TouchLoad.ticking(l,edge),"Worker genuinely cannot enroll itself through its first tick");
   ResourceExpedition.recoverLoaded(l,true);
  }).thenIdle(240).thenExecute(()->{
   boolean resumed=npc.tickCount>10&&TouchLoad.ticking(l,edge);
   h.assertTrue(active?resumed:npc.tickCount==0,"Only the visited village resumes its assigned worker: ticks="+npc.tickCount);
   h.assertTrue(r.profession()==Profession.BAKER&&s.workplace(r.id())!=null&&s.workplace(r.id()).id().equals(kitchen.id()),"No reassignment needed to resume the cook: role="+r.profession()+" workplace="+s.workplace(r.id()));
   h.assertTrue(!l.getForcedChunks().contains(cp.toLong()),"No permanent forced chunk");
   l.getChunkSource().removeRegionTicket(CENTER,center,3,owner);
  }).thenIdle(200).thenWaitUntil(()->h.assertTrue(!TouchLoad.ticking(l,edge),"Worker lease expires after the village center sleeps"))
   .thenExecute(()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());l.getChunkSource().removeRegionTicket(CENTER,cp,0,owner);}).thenSucceed();
 }
}
