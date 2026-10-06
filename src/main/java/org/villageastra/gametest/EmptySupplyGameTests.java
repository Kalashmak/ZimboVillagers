package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EmptySupplyGameTests {
 @GameTest(template="empty",batch="empty_supply",timeoutTicks=1000)
 public static void aSavedEmptyReturnEndsWithoutWalkingToTheHall(GameTestHelper h){trip(h,false);}
 @GameTest(template="empty",batch="empty_supply",timeoutTicks=1000)
 public static void anUnsuccessfulQuarryStillWalksItsPaidPickBackExactlyOnce(GameTestHelper h){trip(h,true);}
 private static void trip(GameTestHelper h,boolean tool){
  var t=ResearchV2Town.town(h,null);var owner=UUID.randomUUID();var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_empty_trip_fixture",Comparator.naturalOrder());
  for(int x=(t.e.center().getX()-2)>>4;x<=(t.e.center().getX()+26)>>4;x++)for(int z=(t.e.center().getZ()-2)>>4;z<=(t.e.center().getZ()+14)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);t.l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);t.l.getChunk(x,z);}
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));npc.bind(t.s.id(),r);var start=t.e.center().offset(22,0,10);npc.moveTo(start.getX()+.5,start.getY(),start.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());var record=new CompoundTag();record.putUUID("id",UUID.randomUUID());record.putString("stage","carry");record.putLong("target",start.asLong());var cargo=new ListTag();
  if(tool){var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(73);chest.setItem(0,pick);var paid=WorldJournal.takeAmount(t.l,Settlement.childId(record.getUUID("id"),"tool"),LogisticsRoutes.position(t.e,t.hall()),0,pick,1);h.assertTrue(chest.countItem(Items.STONE_PICKAXE)==0&&paid.getDamageValue()==73,"The fixture tool actually left stock");cargo.add(paid.save(new CompoundTag()));record.putBoolean("quarry",true);}
  record.put("cargo",cargo);NbtRecord.write(NaturalSupplyGoal.path(t.l,npc.getUUID()),record);var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(npc,true)};npc.goalSelector.addGoal(5,goal[0]);boolean[] replay={false};
  h.startSequence().thenWaitUntil(()->h.assertTrue(t.l.isPositionEntityTicking(start),"Supplier's chunk actually ticks")).thenExecute(()->h.assertTrue(t.l.addFreshEntity(npc),"Supplier registered"));
  h.onEachTick(()->{t.l.resetEmptyTime();if(npc.tickCount==0)return;var current=NaturalSupplyGoal.inspect(t.l,npc.getUUID());
   if(!tool)h.assertTrue(npc.tickCount<=4||current.getBoolean("complete"),"Empty saved return unnecessarily keeps travelling at "+npc.position());
   if(!current.getBoolean("complete"))return;
   if(tool&&!replay[0]){h.assertTrue(chest.countItem(Items.STONE_PICKAXE)==1,"Paid pick physically reached the hall");current.putBoolean("complete",false);current.putInt("delivered",0);NbtRecord.write(NaturalSupplyGoal.path(t.l,npc.getUUID()),current);npc.goalSelector.removeGoal(goal[0]);goal[0]=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,goal[0]);replay[0]=true;return;}
   if(tool){h.assertTrue(replay[0]&&npc.position().distanceToSqr(start.getCenter())>100&&chest.countItem(Items.STONE_PICKAXE)==1,"Real travel and receipt replay return exactly one tool");h.assertTrue(chest.getItem(0).getDamageValue()==73,"Tool wear preserved");}
   else{h.assertTrue(npc.position().distanceToSqr(start.getCenter())<1&&chest.isEmpty(),"Empty completion does not invent cargo or send the body to stock");h.assertTrue(!WorldJournal.exists(t.l,Settlement.childId(record.getUUID("id"),"deliver/0")),"No fictional deposit receipt is written");}
   npc.discard();ResearchV2Town.done(t);for(var cp:held)t.l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();
  });
 }
}
