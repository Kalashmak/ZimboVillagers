package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class InterruptedSupplyGameTests {
 @GameTest(template="empty",batch="interrupted_supply",timeoutTicks=4800)
 public static void anInterruptedUnreachableUnpaidTripStillExpires(GameTestHelper h){
  var t=ResearchV2Town.town(h,null);var owner=UUID.randomUUID();var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_interrupted_supply_fixture",Comparator.naturalOrder());
  for(int x=(t.e.center().getX()-2)>>4;x<=(t.e.center().getX()+26)>>4;x++)for(int z=(t.e.center().getZ()-2)>>4;z<=(t.e.center().getZ()+14)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);t.l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);t.l.getChunk(x,z);}
  var pillar=t.e.center().offset(18,0,10);for(int y=0;y<8;y++)t.l.setBlock(pillar.above(y),Blocks.STONE.defaultBlockState(),2);var target=t.e.center().offset(22,0,10);var stand=target.east();t.l.setBlock(target,Blocks.SAND.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));npc.bind(t.s.id(),r);npc.moveTo(pillar.getX()+.5,pillar.getY()+8,pillar.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.assertTrue(HarvestAccess.standing(t.l,stand,target),"Real deposit has a dry supported harvesting stand");h.assertTrue(!HarvestAccess.reversible(npc.routeTo(stand,0,NaturalSupplyGoal.ROUTE_RANGE)),"The actual eight-block pillar has no safe reversible descent");
  var record=new CompoundTag();record.putUUID("id",UUID.randomUUID());record.putString("stage","dig");record.putLong("target",target.asLong());record.putLong("stand",stand.asLong());record.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));NbtRecord.write(NaturalSupplyGoal.path(t.l,npc.getUUID()),record);
  npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));npc.goalSelector.addGoal(4,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));}public boolean canUse(){return npc.tickCount%100>=80;}public boolean canContinueToUse(){return canUse();}public boolean requiresUpdateEveryTick(){return true;}public void tick(){npc.getNavigation().stop();}});
  h.startSequence().thenWaitUntil(()->h.assertTrue(t.l.isPositionEntityTicking(pillar),"Stranded body's chunk physically ticks")).thenExecute(()->h.assertTrue(t.l.addFreshEntity(npc),"Actual supplier registered"));
  h.onEachTick(()->{t.l.resetEmptyTime();h.assertTrue(npc.tickCount<4000,"Repeated higher-priority interruptions reset the stuck trip forever");h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Watchdog must not force a fall from the pillar");var current=NaturalSupplyGoal.inspect(t.l,npc.getUUID());if(!current.getBoolean("complete"))return;
   h.assertTrue(t.l.getBlockState(target).is(Blocks.SAND)&&current.getInt("labor")==0&&LogisticsRoutes.chest(t.l,t.e,t.hall()).isEmpty(),"Ending inaccessible unpaid work creates no harvest or phantom cargo");h.assertTrue(!WorldJournal.exists(t.l,record.getUUID("id")),"No harvest receipt for an untouched deposit");npc.discard();ResearchV2Town.done(t);for(var cp:held)t.l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();
  });
 }
}
