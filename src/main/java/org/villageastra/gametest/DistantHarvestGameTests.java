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
public final class DistantHarvestGameTests {
 @GameTest(template="empty",batch="distant_harvest",timeoutTicks=4000)
 public static void quarryWorkerWalksBeyondFollowRangeAndBringsLootAndToolHome(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(6,0,6));var base=new BlockPos(origin.getX(),90,origin.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-28)>>4;x<=(base.getX()+100)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+7)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-27;x<=99;x++)for(int z=-3;z<=6;z++)for(int y=0;y<=4;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",-24,0,0);s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var target=base.offset(96,0,0);l.setBlock(target.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(target,Blocks.ANDESITE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(base.getX()-23.5,base.getY()+1,base.getZ()+.5);npc.setOnGround(true);
  h.assertTrue(HarvestAccess.find(npc,target)==null,"Ordinary 96-block follow range cannot approve this 120-block expedition");var stand=HarvestAccess.find(npc,target,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(stand!=null,"A bounded expedition can verify the same safe distant stand");
  // Start after surveying: all travel to the chest, deposit, mining and return below are real entity ticks.
  var trip=new CompoundTag();trip.putUUID("id",UUID.randomUUID());trip.putLong("target",target.asLong());trip.putLong("stand",stand.asLong());trip.put("before",NbtUtils.writeBlockState(Blocks.ANDESITE.defaultBlockState()));trip.putBoolean("quarry",true);trip.putString("stage","tool");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),trip);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Waiting for entity chunk")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(chest.countItem(Items.ANDESITE)==1&&chest.countItem(Items.STONE_PICKAXE)==1&&!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))){
   h.assertTrue(l.getBlockState(target).isAir(),"The actual distant rock was removed");boolean worn=false;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(Items.STONE_PICKAXE)&&chest.getItem(i).getDamageValue()==1)worn=true;h.assertTrue(worn,"Exactly one tool use returned to the hall");npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
  h.runAtTickTime(3800,()->h.assertTrue(false,"Distant quarry round trip stalled: "+npc.position()+" status="+npc.workStatus()+" trip="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
