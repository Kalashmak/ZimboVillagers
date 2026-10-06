package org.villageastra.gametest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HallCommuteGameTests {
 @GameTest(template="empty",batch="hall_commute",timeoutTicks=6000)
 public static void unassignedWorkerFinishesClaimedSmeltingBeforeWalkingHome(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+163840,120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+164)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+16)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=164;x++)for(int z=-4;z<=16;z++)for(int y=0;y<=5;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var settlement=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(settlement,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);settlement.addBuilding(hall);
  var home=new Settlement.Building(UUID.randomUUID(),"home",144,0,0);settlement.addBuilding(home);settlement.addHome(new Settlement.Home(home.id(),1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=Workshops.station(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.SANDSTONE,2));chest.setItem(1,new ItemStack(Items.COAL));chest.setItem(2,new ItemStack(Items.BREAD,64));
  var furnace=base.offset(4,1,4);l.setBlock(furnace,Blocks.FURNACE.defaultBlockState(),2);
  var worker=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(worker.getUUID(),Resident.Life.ADULT,true,null,null,-1);
  settlement.admit(resident,home.id());worker.bind(settlement.id(),resident);worker.moveTo(base.getX()+66.5,base.getY()+1,base.getZ()+5.5);worker.setOnGround(true);
  worker.goalSelector.removeAllGoals(g->true);worker.targetSelector.removeAllGoals(g->true);
  var wants=List.of(new Workshops.Want(Ingredient.of(Items.SMOOTH_SANDSTONE),2,hall.id()));
  h.assertTrue(Workshops.advance(l,e,hall,SettlementData.get(l.getServer()).clock().ticks(),wants).equals("workshop_funding"),"Actual funded recipe is planned");
  var job=Workshops.inspect(l,hall.id());h.assertTrue(job.getBoolean("physicalSmelt")&&NaturalFurnace.claim(l,hall,job,worker.getUUID()),"Worker owns the actual smelting job");
  worker.goalSelector.addGoal(5,new HomeNeighborhood(worker));worker.goalSelector.addGoal(6,new WorkshopGoal(worker,true));h.assertTrue(l.addFreshEntity(worker),"Physical worker registered");
  var delivered=new boolean[]{false};var lastBodyTick=new int[]{worker.tickCount};
  h.onEachTick(()->{
   // This server has no players. Give its service clock one tick only when
   // this real body has ticked, keeping walking and furnace work physical.
   if(worker.tickCount>lastBodyTick[0]){SettlementData.get(l.getServer()).clock().advance(true,false);lastBodyTick[0]=worker.tickCount;}
   l.resetEmptyTime();var state=Workshops.inspect(l,hall.id());
   if(chest.countItem(Items.SMOOTH_SANDSTONE)==2&&state.getString("stage").equals("idle")){
    h.assertTrue(chest.countItem(Items.SANDSTONE)==0&&chest.countItem(Items.COAL)==0,"Real raw stone and fuel paid once");
    h.assertTrue(worker.getHealth()==worker.getMaxHealth()&&worker.tickCount>=400,"Actual walking and vanilla furnace ticks, full health");
    delivered[0]=true;
   }
   if(delivered[0]&&worker.blockPosition().distSqr(HomeNeighborhood.anchor(worker))<=16*16){
    h.assertTrue(!HomeNeighborhood.recovery(worker),"The genuinely idle worker returns to the home neighborhood after finishing");
    worker.discard();SettlementData.get(l.getServer()).remove(settlement.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
   }
  });
  h.runAtTickTime(5800,()->h.assertTrue(false,"Claimed smelting commute stalled: pos="+worker.position()+" goals="+worker.runningGoals()+" delivered="+delivered[0]+" state="+Workshops.inspect(l,hall.id())));
 }
}
