package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BulkSupplyGameTests {
 private static int count(ListTag list){int n=0;for(var raw:list)n+=ItemStack.of((CompoundTag)raw).getCount();return n;}
 @GameTest(template="empty",batch="bulk_supply",timeoutTicks=9000)
 public static void sandTripFillsBoundedLoadWithRealLaborAndSurvivesGoalReload(GameTestHelper h){trip(h,Blocks.SAND,18,16,1,true);}
 @GameTest(template="empty",batch="bulk_supply_partial",timeoutTicks=9000)
 public static void exhaustedSmallDepositReturnsWithoutWaitingForAFullBag(GameTestHelper h){trip(h,Blocks.SAND,3,3,1,false);}
 @GameTest(template="empty",batch="bulk_supply_clay",timeoutTicks=9000)
 public static void clayLimitCountsFourRealDropsPerBlock(GameTestHelper h){trip(h,Blocks.CLAY,5,16,4,true);}
 private static void trip(GameTestHelper h,net.minecraft.world.level.block.Block material,int blocks,int expected,int perBlock,boolean reload){
  var output=material==Blocks.CLAY?Items.CLAY_BALL:Items.SAND;
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(material==Blocks.CLAY?36864:blocks==3?32768:28672),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+25)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+9)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=25;x++)for(int z=-2;z<=9;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var deposits=new ArrayList<BlockPos>();for(int i=0;i<blocks;i++){var p=base.offset(20+i/6,1,i%6);deposits.add(p);l.setBlock(p,material.defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,base.getY()+1,base.getZ()+4.5);npc.setOnGround(true);
  var target=deposits.get(0);var stand=HarvestAccess.find(npc,target,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(stand!=null,"Deposit has a real reversible route");
  var trip=new CompoundTag();trip.putUUID("id",UUID.randomUUID());trip.putLong("target",target.asLong());trip.putLong("stand",stand.asLong());trip.put("before",NbtUtils.writeBlockState(material.defaultBlockState()));trip.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),trip);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output).toString(),blocks*perBlock);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(npc,true)};npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,goal[0]);boolean[] reloaded={false};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{var t=NaturalSupplyGoal.inspect(l,npc.getUUID());int bag=count(t.getList("bag",Tag.TAG_COMPOUND));
   if(reload&&!reloaded[0]&&bag>=4){h.assertTrue(count(NaturalSupplyGoal.cargo(l,t))==bag,"Custody sees every already harvested item exactly once");npc.goalSelector.removeGoal(goal[0]);goal[0]=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,goal[0]);reloaded[0]=true;}
   if(t.getString("stage").equals("carry")){h.assertTrue(count(t.getList("cargo",Tag.TAG_COMPOUND))==expected,"First return must carry "+expected+" real items, got "+count(t.getList("cargo",Tag.TAG_COMPOUND))+" at "+npc.position());h.assertTrue(npc.tickCount>=expected/perBlock*200,"Every block paid its ordinary digging labor");}
   h.assertTrue(chest.countItem(output)<=expected,"Reload cannot duplicate harvested sand");
   if(chest.countItem(output)==expected&&!NaturalSupplyGoal.active(t)){
    h.assertTrue(!reload||reloaded[0],"Batch resumed from its persisted bag");h.assertTrue(deposits.stream().filter(p->l.getBlockState(p).is(material)).count()==blocks-expected/perBlock,"Only paid real blocks removed; excess deposit left in world");h.assertTrue(deposits.stream().allMatch(p->l.getBlockState(p.below()).is(Blocks.STONE)),"Solid work floor preserved");
    npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
   }
  });
  h.runAtTickTime(8800,()->h.assertTrue(false,"Bulk trip stalled at "+npc.position()+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
