package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Real surplus transport must unblock paid bread without adding storage or deleting grain. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GrainOverflowGameTests {
 @GameTest(template="empty",batch="grain_overflow",timeoutTicks=6000)
 public static void fullHallReturnsSurplusGrainToFarmAndPaidBreadFinishes(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="grain_overflow_hungry",timeoutTicks=6000)
 public static void hungryPorterStillReturnsSurplusGrainAndUnblocksPaidBread(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean hungry){
  var l=h.getLevel();var start=h.absolutePos(BlockPos.ZERO);var at=new BlockPos(start.getX()+1048576+(hungry?32768:0),120,start.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(at.getX()-3)>>4;x<=(at.getX()+30)>>4;x++)for(int z=(at.getZ()-3)>>4;z<=(at.getZ()+8)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);}
  for(var p:BlockPos.betweenClosed(at.offset(-3,-1,-3),at.offset(30,5,8)))l.setBlock(p,p.getY()<=at.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),at);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",20,0,0);s.addBuilding(hall);s.addBuilding(farm);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  for(var b:s.buildings())l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.expandHall();var source=LogisticsRoutes.chest(l,e,farm);
  for(int i=0;i<stock.getContainerSize();i++)stock.setItem(i,new ItemStack(i<40?Items.WHEAT:Items.DIRT,64));source.setItem(0,new ItemStack(Items.WHEAT_SEEDS,8));
  var porter=worker(l,s,home,hall,Profession.PORTER);var baker=worker(l,s,home,hall,Profession.MAYOR);var pos=LogisticsRoutes.position(e,hall);porter.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+.5);baker.moveTo(pos.getX()+1.5,pos.getY(),pos.getZ()+1.5);
  baker.goalSelector.addGoal(5,new HandBreadGoal(baker,true,()->6000L));
  if(hungry){s.resident(porter.getUUID()).restoreNeeds(0,0,Population.HUNGRY+1,0);s.resident(baker.getUUID()).restoreNeeds(0,0,Population.HUNGRY+1,0);}
  var courierGoal=new PorterGoal(porter,true);porter.goalSelector.addGoal(6,courierGoal);
  var checked=new boolean[]{false};h.onEachTick(()->{if(hungry&&!checked[0]&&HandBread.inspect(l,s.id()).getString("stage").equals("output")){var r=s.resident(porter.getUUID());h.assertTrue(!CargoCustody.mayStartWork(porter),"Hunger still blocks the ordinary work gate");r.restoreSick(true);h.assertTrue(!courierGoal.canUse(),"Illness still blocks emergency food transport");r.restoreSick(false);h.assertTrue(courierGoal.canUse(),"Healthy hungry courier can solve the verified ready-food blockage");checked[0]=true;}});
  h.onEachTick(()->{if(HandBread.inspect(l,s.id()).getInt("baked")>=4)baker.goalSelector.removeAllGoals(g->g instanceof HandBreadGoal);});
  h.runAtTickTime(3200,()->{if(hungry&&source.countItem(Items.WHEAT)==0){var paid=HandBread.inspect(l,s.id());h.assertTrue(paid.getString("stage").equals("output")&&paid.getInt("paid")==10&&paid.getLong("labor")==2400,"Actual paid bread is ready before emergency transport is expected");}h.assertTrue(source.countItem(Items.WHEAT)>0,"A full hall must start real surplus transport to a farm");});
  h.succeedWhen(()->{
   var bread=HandBread.inspect(l,s.id());h.assertTrue(bread.getInt("baked")==4&&!HandBread.busy(l,e)&&stock.countItem(Items.BREAD)==4,"Real paid bread and all 2400 labor ticks must finish despite initially full hall");
   h.assertTrue(source.countItem(Items.WHEAT)>=64&&stock.countItem(Items.WHEAT)+source.countItem(Items.WHEAT)==2550,"Only ten paid wheat became four bread; remaining grain reached real chests");
   h.assertTrue(!hungry||checked[0],"Hungry emergency preserves ordinary work and illness guards");
   h.assertTrue(stock.getContainerSize()==108&&source.getContainerSize()==27&&porter.getHealth()==porter.getMaxHealth()&&baker.getHealth()==baker.getMaxHealth(),"No capacity increase or unsafe body movement");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_GRAIN_OVERFLOW VERIFIED bodyTicks={} bread=4 wheat={} farmWheat={} hungry={}",porter.tickCount,stock.countItem(Items.WHEAT)+source.countItem(Items.WHEAT),source.countItem(Items.WHEAT),hungry);porter.discard();baker.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
 private static ResidentEntity worker(net.minecraft.server.level.ServerLevel l,Settlement s,UUID home,Settlement.Building hall,Profession profession){
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),profession,hall.id());npc.bind(s.id(),s.resident(r.id()));npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.setOnGround(true);l.addFreshEntity(npc);return npc;
 }
}
