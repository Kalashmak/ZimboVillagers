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
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StockReedGameTests {
 @GameTest(template="empty",batch="stock_reeds",timeoutTicks=6000)
 public static void oneStoredCaneBecomesOnePaidLivingPlant(GameTestHelper h){check(h,0);}
 @GameTest(template="empty",batch="stock_reeds",timeoutTicks=6000)
 public static void paidSeedWithdrawalResumesWithoutTakingAnotherItem(GameTestHelper h){check(h,1);}
 @GameTest(template="empty",batch="stock_reeds",timeoutTicks=6000)
 public static void changedNurseryShoreReturnsTheStoredSeedOnce(GameTestHelper h){check(h,2);}
 @GameTest(template="empty",batch="stock_reeds_reserve",timeoutTicks=6000)
 public static void constructionReservedCaneIsNotTakenForNursery(GameTestHelper h){check(h,3);}
 private static void check(GameTestHelper h,int mode){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+81920+4096*mode,90,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+44)>>4;x++)for(int z=(base.getZ()-4)>>4;z<=(base.getZ()+12)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=44;x++)for(int z=-4;z<=12;z++)for(int y=0;y<=5;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var water=base.offset(24,0,2);l.setBlock(water,Blocks.WATER.defaultBlockState(),3);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var source=LogisticsRoutes.position(e,hall);l.setBlock(source,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.SUGAR_CANE));
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt(mode==3?"minecraft:sugar_cane":"minecraft:paper",mode==3?1:3);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,base.getY()+1,base.getZ()+4.5);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(npc,true)};npc.goalSelector.addGoal(5,goal[0]);boolean[] changed={false},reloaded={false};BlockPos[] planted={null};
  Runnable clean=()->{npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);};
  h.startSequence().thenWaitUntil(()->h.assertTrue(TouchLoad.ticking(l,npc.blockPosition()),"Native seed carrier chunk ready")).thenExecute(()->h.assertTrue(l.addFreshEntity(npc),"Seed carrier added"));
  h.onEachTick(()->{
   var t=NaturalSupplyGoal.inspect(l,npc.getUUID());
   if(mode==3){if(npc.tickCount<100)return;h.assertTrue(stock.countItem(Items.SUGAR_CANE)==1&&!t.getBoolean("seedFromStock")&&ReedNursery.planted(l,s.id()).isEmpty(),"Building reserve remains untouched");clean.run();h.succeed();return;}
   if(mode==1&&t.getBoolean("seedFromStock")&&t.getString("stage").equals("carry")&&!reloaded[0]){
    h.assertTrue(stock.countItem(Items.SUGAR_CANE)==0,"Seed was actually withdrawn before simulated save interruption");
    t.putString("stage","nursery_seed");t.remove("cargo");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),t);npc.goalSelector.removeGoal(goal[0]);goal[0]=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,goal[0]);reloaded[0]=true;return;
   }
   if(t.contains("nurseryTarget")){planted[0]=BlockPos.of(t.getLong("nurseryTarget"));if(mode==2&&!changed[0]){l.setBlock(planted[0],Blocks.STONE.defaultBlockState(),3);changed[0]=true;}}
   if(!t.getBoolean("seedFromStock")||!t.getBoolean("complete"))return;
   h.assertTrue(planted[0]!=null,"A real nursery site was selected");var payment=WorldJournal.recoverAmount(l,Settlement.childId(t.getUUID("id"),"nursery_seed"));h.assertTrue(payment.is(Items.SUGAR_CANE)&&payment.getCount()==1,"Exactly one stored seed was paid through the journal");
   if(mode==2)h.assertTrue(changed[0]&&stock.countItem(Items.SUGAR_CANE)==1&&ReedNursery.planted(l,s.id()).isEmpty()&&l.getBlockState(planted[0]).is(Blocks.STONE),"Changed shore returns one seed without overwriting the new block");
   else h.assertTrue(stock.countItem(Items.SUGAR_CANE)==0&&ReedNursery.planted(l,s.id()).size()==1&&l.getBlockState(planted[0]).is(Blocks.SUGAR_CANE)&&t.getInt("nurseryLabor")>=200&&npc.tickCount>=200&&(mode!=1||reloaded[0]),"One paid seed becomes one living plant after actual movement and labor");
   h.assertTrue(NaturalSupplyGoal.cargo(l,t).isEmpty()&&l.getBlockState(water).is(Blocks.WATER),"No duplicate cargo or artificial water");clean.run();h.succeed();
  });
  h.runAtTickTime(5800,()->h.assertTrue(false,"Stored seed unfinished: body="+npc.position()+" ticks="+npc.tickCount+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
 }
}
