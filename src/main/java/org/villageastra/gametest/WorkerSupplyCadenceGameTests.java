package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** Actual entity ticks and footsteps prove both goal-update parities deliver real stock. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WorkerSupplyCadenceGameTests {
 private static final net.minecraft.server.level.TicketType<UUID> TICKET=net.minecraft.server.level.TicketType.create("zimbovillagers_supply_cadence",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="worker_supply_cadence",timeoutTicks=1200)
 public static void bothEntityParitiesDeliverTheirActualMineOutput(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,0,4));var base=new BlockPos(p.getX(),64,p.getZ());var owner=UUID.randomUUID();var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+60)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+12)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(TICKET,cp,3,owner);chunks.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-3)>>4)-2;x<=((base.getX()+60)>>4)+2;x++)for(int z=((base.getZ()-3)>>4)-2;z<=((base.getZ()+12)>>4)+2;z++)l.getChunk(x,z);
  for(int x=-3;x<=60;x++)for(int z=-3;z<=12;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var bodies=new ArrayList<ResidentEntity>();var villages=new ArrayList<SettlementData.Entry>();var dests=new ArrayList<OwnedChestEntity>();
  for(int i=0;i<2;i++){
   var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(i*32,0,0));var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",16,0,0);s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);villages.add(e);
   for(var b:List.of(hall,mine))l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var source=LogisticsRoutes.chest(l,e,mine);var dest=LogisticsRoutes.chest(l,e,hall);source.setItem(0,new ItemStack(Items.COBBLESTONE,2));dests.add(dest);
   var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",2);project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);
   var npc=VillageAstra.RESIDENT.get().create(l);while(npc.getId()%2!=i){npc.discard();npc=VillageAstra.RESIDENT.get().create(l);}var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),r);var at=LogisticsRoutes.position(e,mine);npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);npc.onlyGoals(g->false,5,new WorkerSupplyGoal(npc,true));h.assertTrue(l.addFreshEntity(npc),"Real worker parity "+i+" registered");bodies.add(npc);
  }
  Runnable clean=()->{for(var n:bodies)n.discard();for(var e: villages){HallUpgradeGoal.drop(l,e.settlement().id());SettlementData.get(l.getServer()).remove(e.settlement().id());}for(var cp:chunks)l.getChunkSource().removeRegionTicket(TICKET,cp,3,owner);};
  h.onEachTick(()->{l.resetEmptyTime();if(dests.stream().allMatch(c->c.countItem(Items.COBBLESTONE)==2)){for(var n:bodies)h.assertTrue(n.tickCount>0&&n.getX()<LogisticsRoutes.position(villages.get(bodies.indexOf(n)),villages.get(bodies.indexOf(n)).settlement().workplace(n.getUUID())).getX()-8,"Each worker physically walked away from its mine");clean.run();h.succeed();}});
  h.runAtTickTime(1000,()->{String why="Stock did not arrive for both parities: "+bodies.stream().map(n->n.getId()+":"+n.tickCount+":"+n.position()+":"+n.workStatus()+":"+n.runningGoals()).toList()+" outputs="+dests.stream().map(c->c.countItem(Items.COBBLESTONE)).toList();clean.run();h.assertTrue(false,why);});
 }
}
