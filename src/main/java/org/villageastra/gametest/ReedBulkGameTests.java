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
public final class ReedBulkGameTests {
 @GameTest(template="empty",batch="reed_bulk",timeoutTicks=6000)
 public static void nurseryTopsShareOnePaidTripAcrossGoalReload(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
  var base=new BlockPos(origin.getX()+147456,120,origin.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+36)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+12)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=36;x++)for(int z=-4;z<=12;z++)for(int y=0;y<=5;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var settlement=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(settlement,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);settlement.addBuilding(hall);
  var home=UUID.randomUUID();settlement.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var roots=new ArrayList<BlockPos>();var tops=new ArrayList<BlockPos>();
  for(int i=0;i<8;i++){
   var root=base.offset(i<4?25:10,1,(i%4)*2);roots.add(root);
   l.setBlock(root.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(root.below().east(),Blocks.WATER.defaultBlockState(),3);
   l.setBlock(root,Blocks.SUGAR_CANE.defaultBlockState(),3);ReedNursery.record(l,settlement.id(),root);
   if(i<4){l.setBlock(root.above(),Blocks.SUGAR_CANE.defaultBlockState(),3);tops.add(root.above());}
   else l.setBlock(root.above(),Blocks.STONE.defaultBlockState(),3);
  }
  var worker=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(worker.getUUID(),Resident.Life.ADULT,true,null,null,-1);
  settlement.admit(resident,home);worker.bind(settlement.id(),resident);worker.moveTo(base.getX()+3.5,base.getY()+1,base.getZ()+4.5);worker.setOnGround(true);
  worker.goalSelector.removeAllGoals(g->true);worker.targetSelector.removeAllGoals(g->true);
  var first=tops.get(0);var stand=HarvestAccess.find(worker,first,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(stand!=null,"Actual tops have a reversible route");
  var state=new CompoundTag();state.putUUID("id",UUID.randomUUID());state.putLong("target",first.asLong());state.putLong("stand",stand.asLong());state.put("before",NbtUtils.writeBlockState(l.getBlockState(first)));state.putString("stage","dig");
  NbtRecord.write(NaturalSupplyGoal.path(l,worker.getUUID()),state);
  var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(worker,true)};worker.goalSelector.addGoal(5,goal[0]);h.assertTrue(l.addFreshEntity(worker),"Actual supplier registered");
  var reloaded=new boolean[]{false};var jobs=new HashSet<UUID>();
  h.onEachTick(()->{
   l.resetEmptyTime();var current=NaturalSupplyGoal.inspect(l,worker.getUUID());if(current.hasUUID("id"))jobs.add(current.getUUID("id"));
   int bag=current.getList("bag",Tag.TAG_COMPOUND).stream().mapToInt(raw->ItemStack.of((CompoundTag)raw).getCount()).sum();
   if(!reloaded[0]&&bag>=2){h.assertTrue(NaturalSupplyGoal.cargo(l,current).size()==bag,"Journal-confirmed bag survives reload without duplicates");worker.goalSelector.removeGoal(goal[0]);goal[0]=new NaturalSupplyGoal(worker,true);worker.goalSelector.addGoal(5,goal[0]);reloaded[0]=true;}
   h.assertTrue(chest.countItem(Items.SUGAR_CANE)<=4,"No extra cane enters stock");
   if(current.getString("stage").equals("carry"))h.assertTrue(current.getList("cargo",Tag.TAG_COMPOUND).size()==4,"One return carries four real tops");
   if(!current.getBoolean("complete"))return;
   h.assertTrue(reloaded[0]&&worker.tickCount>=800,"All four blocks paid ordinary labor across goal reload");
   h.assertTrue(jobs.size()==4&&chest.countItem(Items.SUGAR_CANE)==4,"Four separate harvest jobs deliver exactly four cane");
   h.assertTrue(roots.stream().allMatch(p->l.getBlockState(p).is(Blocks.SUGAR_CANE)),"All eight growing bases survive");
   h.assertTrue(tops.stream().allMatch(p->l.getBlockState(p).isAir()),"Only actual mature tops removed");
   h.assertTrue(ReedNursery.planted(l,settlement.id()).size()==8&&NaturalSupplyGoal.cargo(l,current).isEmpty(),"No seed loss or unpaid cargo after delivery");
   worker.discard();SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(5800,()->h.assertTrue(false,"Nursery trip stalled: "+worker.position()+" state="+NaturalSupplyGoal.inspect(l,worker.getUUID())));
 }
}
