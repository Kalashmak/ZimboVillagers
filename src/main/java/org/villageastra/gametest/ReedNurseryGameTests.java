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
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ReedNurseryGameTests {
 @GameTest(template="empty",batch="reed_nursery",timeoutTicks=6000)
 public static void harvestedCaneIsCarriedAndPlantedOnceAcrossGoalReload(GameTestHelper h){trip(h,false);}
 @GameTest(template="empty",batch="reed_nursery_changed",timeoutTicks=6000)
 public static void occupiedShoreReturnsTheUnspentSeedToStock(GameTestHelper h){trip(h,true);}
 private static void trip(GameTestHelper h,boolean change){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(change?45056:40960),90,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+40)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+12)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);}
  for(int x=-4;x<=40;x++)for(int z=-4;z<=12;z++)for(int y=0;y<=5;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var cane=base.offset(35,1,2);l.setBlock(cane.below().south(),Blocks.WATER.defaultBlockState(),3);for(int y=0;y<3;y++)l.setBlock(cane.above(y),Blocks.SUGAR_CANE.defaultBlockState(),3);var target=cane.above(2);
  var water=base.offset(24,0,2);l.setBlock(water,Blocks.WATER.defaultBlockState(),3);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,base.getY()+1,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var stand=HarvestAccess.find(npc,target,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(stand!=null,"Real cane has a reachable top");var t=new CompoundTag();t.putUUID("id",UUID.randomUUID());t.putLong("target",target.asLong());t.putLong("stand",stand.asLong());t.put("before",NbtUtils.writeBlockState(l.getBlockState(target)));t.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),t);
  var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(npc,true)};npc.goalSelector.addGoal(5,goal[0]);boolean[] reloaded={false},changed={false};BlockPos[] planted={null};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Resident added"));
  h.onEachTick(()->{var state=NaturalSupplyGoal.inspect(l,npc.getUUID());
   if(state.contains("nurseryTarget")){planted[0]=BlockPos.of(state.getLong("nurseryTarget"));
    if(change&&!changed[0]){h.assertTrue(NaturalSupplyGoal.cargo(l,state).size()==1,"Unplanted cane remains in custody");l.setBlock(planted[0],Blocks.STONE.defaultBlockState(),3);changed[0]=true;}
    if(!change&&!reloaded[0]&&state.getInt("nurseryLabor")>=40){h.assertTrue(NaturalSupplyGoal.cargo(l,state).size()==1,"Labor does not consume the seed early");npc.goalSelector.removeGoal(goal[0]);goal[0]=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,goal[0]);reloaded[0]=true;}
   }
   if(NaturalSupplyGoal.active(state)||!state.getBoolean("complete"))return;
   h.assertTrue(planted[0]!=null,"Nearby shore was selected");h.assertTrue(l.getBlockState(cane).is(Blocks.SUGAR_CANE)&&l.getBlockState(cane.above()).is(Blocks.SUGAR_CANE)&&l.getBlockState(target).isAir(),"Wild growing base preserved, one real top harvested");
   h.assertTrue(l.getBlockState(water).is(Blocks.WATER),"No water or soil was replaced");
   if(change){h.assertTrue(changed[0]&&chest.countItem(Items.SUGAR_CANE)==1&&l.getBlockState(planted[0]).is(Blocks.STONE),"Changed shore preserved and exactly one seed returned");h.assertTrue(ReedNursery.planted(l,s.id()).isEmpty(),"Failed planting creates no registered plant");}
   else{
    h.assertTrue(reloaded[0]&&state.getInt("nurseryLabor")>=200&&npc.tickCount>=400,"Harvest and planting both paid real labor across reload");h.assertTrue(chest.countItem(Items.SUGAR_CANE)==0&&l.getBlockState(planted[0]).is(Blocks.SUGAR_CANE),"The harvested item became one living cane, not free stock");
    h.assertTrue(NaturalSupplyGoal.cargo(l,state).isEmpty(),"Custody excludes the planted item");state.putInt("delivered",0);state.putBoolean("complete",false);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),state);
    ReedNursery.tick(npc,e,state);ReedNursery.tick(npc,e,state);h.assertTrue(state.getInt("delivered")==1&&ReedNursery.planted(l,s.id()).size()==1&&NaturalSupplyGoal.cargo(l,state).isEmpty(),"Applied placement replays exactly once before the carry index was saved");NaturalSupplyGoal.release(l,npc.getUUID());
   }
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
  h.runAtTickTime(5800,()->h.assertTrue(false,"Nursery stalled at "+npc.position()+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
