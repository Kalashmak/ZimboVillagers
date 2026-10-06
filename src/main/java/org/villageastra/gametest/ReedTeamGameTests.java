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
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReedTeamGameTests {
 @GameTest(template="empty",batch="reed_team",timeoutTicks=4000)
 public static void twoSuppliersReserveDifferentRealHarvestsAcrossGoalReload(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+163840,90,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_reed_team_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+38)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-2)>>4)-2;x<=((base.getX()+38)>>4)+2;x++)for(int z=((base.getZ()-2)>>4)-2;z<=((base.getZ()+10)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-2,0,-2),base.offset(38,4,10)))l.setBlock(p,p.getY()==base.getY()?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var roots=new ArrayList<BlockPos>();for(int n=0;n<ReedNursery.CAPACITY;n++){var root=base.offset(24+n,1,2);roots.add(root);l.setBlock(root.below().south(),Blocks.WATER.defaultBlockState(),3);l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);ReedNursery.record(l,s.id(),root);}for(int n=0;n<2;n++)l.setBlock(roots.get(n).above(),Blocks.SUGAR_CANE.defaultBlockState(),3);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:sugar_cane",2);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var actors=new ArrayList<ResidentEntity>();var goals=new ArrayList<NaturalSupplyGoal>();
  for(int n=0;n<2;n++){var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5+n,91,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);var goal=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,goal);actors.add(npc);goals.add(goal);}
  boolean[] checked={false},reloaded={false};var assignments=new ArrayList<UUID>();
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Team's chunks actually tick")).thenExecute(()->actors.forEach(npc->h.assertTrue(l.addFreshEntity(npc),"Supplier added")));
  h.onEachTick(()->{l.resetEmptyTime();var records=actors.stream().map(npc->NaturalSupplyGoal.inspect(l,npc.getUUID())).toList();if(records.stream().anyMatch(t->!t.hasUUID("id")))return;
   if(!checked[0]){h.assertTrue(records.get(0).getLong("target")!=records.get(1).getLong("target"),"Two workers must not walk to the same already assigned cane top");records.forEach(t->assignments.add(t.getUUID("id")));checked[0]=true;}
   if(!reloaded[0]&&actors.stream().allMatch(npc->npc.tickCount>=50)){for(int n=0;n<2;n++){actors.get(n).goalSelector.removeGoal(goals.get(n));var resumed=new NaturalSupplyGoal(actors.get(n),true);actors.get(n).goalSelector.addGoal(5,resumed);goals.set(n,resumed);}reloaded[0]=true;}
   for(int n=0;n<2;n++)h.assertTrue(records.get(n).getUUID("id").equals(assignments.get(n)),"Assigned harvest survives goal reload");
   if(records.stream().anyMatch(t->!t.getBoolean("complete")))return;
   h.assertTrue(reloaded[0]&&chest.countItem(Items.SUGAR_CANE)==2,"Two paid physical trips supply exactly two items after reload");for(var t:records)h.assertTrue(t.getInt("labor")>=200&&WorldJournal.recoverExisting(l,t.getUUID("id"))!=null,"Each harvest has its own labor and real receipt");h.assertTrue(roots.stream().allMatch(p->l.getBlockState(p).is(Blocks.SUGAR_CANE)),"All growing bases preserved");
   actors.forEach(ResidentEntity::discard);HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();
  });
 }
}
