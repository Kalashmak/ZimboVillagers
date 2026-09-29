package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResourceLeaseGameTests {
 @GameTest(template="empty",batch="resource_lease",timeoutTicks=700)
 public static void expeditionLeaseTicksItsChunkAndExpiresWithoutPersistentForce(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO).offset(8192,0,0);var cp=new ChunkPos(p);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)l.getChunk(cp.x+x,cp.z+z);
  h.assertTrue(!TouchLoad.ticking(l,p),"A distant loaded chunk initially does not tick entities");
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);ResourceExpedition.hold(npc);
  h.startSequence().thenWaitUntil(()->h.assertTrue(TouchLoad.ticking(l,p),"Moving expedition lease enables entity ticks"))
   .thenExecute(()->h.assertTrue(!l.getForcedChunks().contains(cp.toLong()),"No persistent force-loaded chunk is created"))
   .thenIdle(160).thenWaitUntil(()->h.assertTrue(!TouchLoad.ticking(l,p),"Without a renewing worker the lease expires"))
   .thenSucceed();
 }
 @GameTest(template="empty",batch="resource_lease_resume",timeoutTicks=2400)
 public static void loadedSavedTripResumesWithoutWaitingForItsFirstEntityTick(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO).offset(24576,0,0);var cp=new ChunkPos(p);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)l.getChunk(cp.x+x,cp.z+z);
  var s=new org.villageastra.domain.Settlement(java.util.UUID.randomUUID());var home=java.util.UUID.randomUUID();s.addHome(new org.villageastra.domain.Settlement.Home(home,1,8,true));
  var e=new org.villageastra.server.SettlementData.Entry(s,l.dimension().location().toString(),p);org.villageastra.server.SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new org.villageastra.domain.Resident(npc.getUUID(),org.villageastra.domain.Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var trip=new net.minecraft.nbt.CompoundTag();trip.putUUID("id",java.util.UUID.randomUUID());trip.putString("stage","carry");var cargo=new net.minecraft.nbt.ListTag();cargo.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SAND,15).save(new net.minecraft.nbt.CompoundTag()));trip.put("cargo",cargo);org.villageastra.persistence.NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),trip);
  boolean[] resumed={false};h.onEachTick(()->{if(!resumed[0])ResourceExpedition.survey(npc,p);});
  h.assertTrue(!TouchLoad.ticking(l,p),"Saved body is loaded outside entity-ticking chunks");h.assertTrue(l.addFreshEntity(npc),"Restored body exists in the loaded chunk");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.getEntity(npc.getUUID())==npc,"Saved body is tracked in the read-only loaded chunk")).thenExecute(()->{
   h.assertTrue(npc.tickCount==0,"Its goal cannot enroll before recovery: "+npc.tickCount);ResourceExpedition.recoverLoaded(l,true);resumed[0]=true;
  }).thenWaitUntil(()->h.assertTrue(npc.tickCount>0,"Recovery starts entity ticks from the persisted active trip"))
   .thenExecute(()->{
    h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getList("cargo",net.minecraft.nbt.Tag.TAG_COMPOUND).equals(cargo),"Recovery leaves all fifteen carried items unchanged");
    h.assertTrue(!l.getForcedChunks().contains(cp.toLong()),"Recovery uses no persistent forced chunk");NaturalSupplyGoal.release(l,npc.getUUID());npc.discard();
   }).thenIdle(160).thenWaitUntil(()->h.assertTrue(!TouchLoad.ticking(l,p),"Completed trip lease expires"))
   .thenExecute(()->org.villageastra.server.SettlementData.get(l.getServer()).remove(s.id())).thenSucceed();
 }
}
