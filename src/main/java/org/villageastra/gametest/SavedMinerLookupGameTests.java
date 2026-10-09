package org.villageastra.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SavedMinerLookupGameTests {
 private static final TicketType<UUID> FIXTURE=TicketType.create("zimbovillagers_saved_miner_fixture",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="saved_miner_lookup",timeoutTicks=2400)
 public static void savedMinerAwayFromItsMineRouteReloadsAtItsActualPosition(GameTestHelper h){check(h,0);}
 @GameTest(template="empty",batch="saved_miner_lookup",timeoutTicks=1600)
 public static void savedEscortedMinerIsNotRecoveredAsVillageLabor(GameTestHelper h){check(h,1);}
 @GameTest(template="empty",batch="saved_miner_lookup",timeoutTicks=1600)
 public static void savedBodyOfAnotherSettlementIsNotRecovered(GameTestHelper h){check(h,2);}
 private static void check(GameTestHelper h,int mode){
  var l=h.getLevel();var base=h.absolutePos(BlockPos.ZERO).offset(1_550_000+mode*2000,20,0);var edge=base.offset(0,0,144);
  var center=new ChunkPos(base);var cp=new ChunkPos(edge);var ticket=UUID.randomUUID();
  l.getChunkSource().addRegionTicket(FIXTURE,center,3,ticket);
  l.getChunkSource().addRegionTicket(FIXTURE,cp,0,ticket);
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)l.getChunk(cp.x+x,cp.z+z);
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=2;y++)l.setBlock(edge.offset(x,y,z),(y<0?net.minecraft.world.level.block.Blocks.STONE:net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);s.addBuilding(mine);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);
  s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),r);npc.moveTo(edge.getX()+.5,edge.getY(),edge.getZ()+.5);
  if(mode==1)npc.escort(UUID.randomUUID());if(mode==2)npc.bind(UUID.randomUUID(),r);
  npc.setNoAi(true);npc.setNoGravity(true);npc.setHealth(17);var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(66);npc.setItemSlot(EquipmentSlot.MAINHAND,pick);
  var work=new CompoundTag();work.putUUID("worker",r.id());work.putString("stage","dig");work.putString("status","walking");
  work.putIntArray("access",new int[]{-144,0,0});work.putLong("target",edge.above().asLong());work.put("tool",pick.save(new CompoundTag()));work.putInt("run",148);MineWork.write(l,mine,work);
  h.assertTrue(l.addFreshEntity(npc),"Actual saved mine worker is tracked before unloading");
  var restored=new ResidentEntity[1];boolean[] recovering={false};
  h.onEachTick(()->{if(recovering[0])ResourceExpedition.recoverLoaded(l,true);});
  var sequence=h.startSequence().thenWaitUntil(()->{
   h.assertTrue(l.getEntity(r.id())==npc,"Initial native body tracked");h.assertTrue(TouchLoad.ticking(l,base),"Visited village center ticks");
  }).thenExecute(()->{
   h.assertTrue(!TouchLoad.ticking(l,edge),"Worker starts in a loaded but non-ticking edge chunk");
   l.getChunkSource().removeRegionTicket(FIXTURE,cp,0,ticket);
  }).thenWaitUntil(()->h.assertTrue(npc.isRemoved()&&l.getEntity(r.id())==null,"Native entity manager really unloaded and saved this body"))
   .thenExecute(()->recovering[0]=true);
  if(mode==0)sequence.thenWaitUntil(()->{
   h.assertTrue(l.getEntity(r.id()) instanceof ResidentEntity,"Existing saved body returns through native chunk loading");restored[0]=(ResidentEntity)l.getEntity(r.id());
   h.assertTrue(TouchLoad.ticking(l,restored[0].blockPosition()),"Restored body receives ordinary entity ticks");
  }).thenExecute(()->{
   h.assertTrue(restored[0]!=npc&&restored[0].getUUID().equals(r.id()),"Native persistence reloaded the original UUID, without reviving the old object");
   h.assertTrue(restored[0].position().distanceToSqr(edge.getCenter().subtract(0,.5,0))<.001,"Recovery did not teleport the saved body");
   h.assertTrue(restored[0].getHealth()==17&&restored[0].getMainHandItem().getDamageValue()==66,"Health and actual worn pick were preserved");
   h.assertTrue(MineWork.read(l,mine).equals(work),"Work ownership, tool, geometry and labor were not changed");
   h.assertTrue(!l.getForcedChunks().contains(cp.toLong()),"Recovery does not permanently force a chunk");
   com.mojang.logging.LogUtils.getLogger().info("ZimboVillagers saved miner restored: actor={} health={} pickDamage={} position={}",restored[0].getUUID(),restored[0].getHealth(),restored[0].getMainHandItem().getDamageValue(),restored[0].position());
   r.die();recovering[0]=false;restored[0].discard();l.getChunkSource().removeRegionTicket(FIXTURE,center,3,ticket);
  }).thenIdle(600).thenWaitUntil(()->h.assertTrue(!TouchLoad.ticking(l,edge),"Temporary recovery loading expires"));
  else sequence.thenIdle(900).thenExecute(()->{
   h.assertTrue(l.getEntity(r.id())==null&&!TouchLoad.ticking(l,edge),"Invalid or unvisited work does not wake the absent resident");
   h.assertTrue(MineWork.read(l,mine).equals(work),"Refusal leaves the authoritative work record unchanged");recovering[0]=false;
  });
  sequence.thenExecute(()->{l.getChunkSource().removeRegionTicket(FIXTURE,center,3,ticket);SettlementData.get(l.getServer()).remove(s.id());}).thenSucceed();
 }
}
