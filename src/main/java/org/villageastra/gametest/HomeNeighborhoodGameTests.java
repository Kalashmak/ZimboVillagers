package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HomeNeighborhoodGameTests {
 private static final TicketType<UUID> TICKET=TicketType.create("zimbovillagers_home_test",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="home_neighborhood",timeoutTicks=2400)
 public static void idleResidentAtSleepingChunkEdgeWalksHomeInsteadOfStayingFrozen(GameTestHelper h){
  var l=h.getLevel();var corner=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(corner.getX()+196608,90,corner.getZ());
  var owner=UUID.randomUUID();var center=new ChunkPos(base);var far=new ChunkPos(base.offset(144,0,0));
  l.getChunkSource().addRegionTicket(TICKET,center,3,owner);l.getChunkSource().addRegionTicket(TICKET,far,0,owner);
  for(int x=(base.getX()-32)>>4;x<=(base.getX()+176)>>4;x++)for(int z=(base.getZ()-32)>>4;z<=(base.getZ()+32)>>4;z++)l.getChunk(x,z);
  for(int x=-3;x<=150;x++)for(int z=-3;z<=14;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var home=new Settlement.Building(UUID.randomUUID(),"home",0,0,0);s.addBuilding(home);s.addHome(new Settlement.Home(home.id(),1,2,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());npc.bind(s.id(),r);
  npc.moveTo(base.getX()+144.5,91,base.getZ()+5.5);npc.onlyGoals(g->false,5,new HomeNeighborhood(npc));npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(6,new net.minecraft.world.entity.ai.goal.Goal(){
   {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return true;}
  });
  h.assertTrue(l.addFreshEntity(npc),"Idle body registered in the loaded edge chunk");
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Home chunk ready"))
   .thenWaitUntil(()->h.assertTrue(l.getEntity(npc.getUUID())==npc,"Edge body tracked after asynchronous chunk registration")).thenExecute(()->{
   h.assertTrue(!l.isPositionEntityTicking(npc.blockPosition())&&npc.tickCount==0,"The distant body starts loaded but genuinely unticking");
   h.assertTrue(HomeNeighborhood.recovery(npc),"Only the nearby active village permits the bounded idle return lease");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HOME_RETURN started registered={} recovery={} centerTicking={}",l.getEntity(npc.getUUID())==npc,HomeNeighborhood.recovery(npc),l.isPositionEntityTicking(base));ResourceExpedition.recoverLoaded(l,true);
  });
  h.onEachTick(()->{l.resetEmptyTime();var anchor=HomeNeighborhood.anchor(npc);if(npc.tickCount>0&&npc.blockPosition().distSqr(anchor)<=16*16){
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"The physical return is safe");h.assertTrue(npc.hasRestriction(),"Subsequent idle strolls remain near home");
   h.assertTrue(!HomeNeighborhood.recovery(npc),"The return lease ends inside the home neighborhood");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_HOME_RETURN VERIFIED actualTicks={} travelled={} pos={}",npc.tickCount,base.getX()+144.5-npc.getX(),npc.blockPosition());
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());l.getChunkSource().removeRegionTicket(TICKET,center,3,owner);l.getChunkSource().removeRegionTicket(TICKET,far,0,owner);h.succeed();
  }});
  h.runAtTickTime(2200,()->h.assertTrue(false,"Idle return stalled: ticks="+npc.tickCount+" pos="+npc.blockPosition()+" goals="+npc.runningGoals()+" ticking="+l.isPositionEntityTicking(npc.blockPosition())+" centerTicking="+l.isPositionEntityTicking(base)+" registered="+(l.getEntity(npc.getUUID())==npc)));
 }
 @GameTest(template="empty",batch="home_neighborhood_policy",timeoutTicks=100)
 public static void escortAndDistantVillageDoNotAcquireAnIdleReturnLease(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var home=new Settlement.Building(UUID.randomUUID(),"home",0,0,0);t.s.addBuilding(home);t.s.addHome(new Settlement.Home(home.id(),1,2,true));
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.CHILD,false,null,null,-1);t.s.admit(r,home.id());npc.bind(t.s.id(),r);
  npc.moveTo(t.e.center().getX()+400.5,t.e.center().getY()+1,t.e.center().getZ()+.5);
  h.assertTrue(!HomeNeighborhood.recovery(npc),"A remote body does not force-tick the world");
  r.growUp();npc.refreshLife(r);var anchor=HomeNeighborhood.anchor(npc);npc.moveTo(anchor.getX()+60.5,anchor.getY(),anchor.getZ()+.5);
  h.assertTrue(!new HomeNeighborhood(npc).canUse(),"An unassigned adult's ordinary sixty-block hall commute must not be interrupted by its idle walking boundary");
  HomeNeighborhood.restrict(npc);h.assertTrue(npc.hasRestriction(),"Home establishes the idle walking boundary");npc.escort(UUID.randomUUID());HomeNeighborhood.restrict(npc);
  h.assertTrue(!npc.hasRestriction()&&!HomeNeighborhood.recovery(npc),"A player companion is free to follow the player");ResearchV2Town.done(t);npc.discard();h.succeed();
 }
}
