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

/** A paid last sapling must not pin a forester at a full workplace chest. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestOverflowGameTests {
 @GameTest(template="empty",batch="forest_overflow",timeoutTicks=6000)
 public static void lastPaidSaplingReturnsToHallAcrossReloadAndForesterGathersDemandedClay(GameTestHelper h){run(h,false);}
 @GameTest(template="empty",batch="forest_overflow_full",timeoutTicks=6000)
 public static void bothFullChestsRetainPaidCargoUntilHallHasRoom(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean fullHall){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+917504+(fullHall?32768:0),120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+53)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+16)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(53,8,16)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var hut=new Settlement.Building(UUID.randomUUID(),"forester",20,0,0);s.addBuilding(hall);s.addBuilding(hut);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);var output=LogisticsRoutes.position(e,hut);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(output,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var own=LogisticsRoutes.chest(l,e,hut);
  own.setItem(0,new ItemStack(Items.BIRCH_LOG,58));own.setItem(1,new ItemStack(Items.STICK,62));for(int i=2;i<own.getContainerSize();i++)own.setItem(i,new ItemStack(Items.BIRCH_SAPLING,64));
  var cargo=new ListTag();var op=UUID.randomUUID();int index=0;
  for(var item:List.of(new ItemStack(Items.BIRCH_LOG,6),new ItemStack(Items.STICK,2),new ItemStack(Items.BIRCH_SAPLING))){
   chest.setItem(index,item.copy());var paid=WorldJournal.takeAmount(l,Settlement.childId(op,"fixture/paid/"+index),stock,index,item,item.getCount());h.assertTrue(paid.getCount()==item.getCount(),"Every carried item is actually withdrawn");cargo.add(paid.save(new CompoundTag()));
   if(index<2)h.assertTrue(WorldJournal.deposit(l,Settlement.childId(op,"delivery/"+index),output,paid),"Earlier logs and sticks really reached the workplace");index++;
  }
  if(fullHall)for(int slot=0;slot<chest.getContainerSize();slot++)chest.setItem(slot,new ItemStack(Items.DIRT,64));
  var tree=base.offset(45,1,8);l.setBlock(tree.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=0;y<3;y++)l.setBlock(tree.above(y),Blocks.BIRCH_LOG.defaultBlockState(),2);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)l.setBlock(tree.offset(x,3,z),Blocks.BIRCH_LEAVES.defaultBlockState(),2);
  var clay=base.offset(40,0,2);l.setBlock(clay,Blocks.CLAY.defaultBlockState(),2);var project=new CompoundTag();var projectId=UUID.randomUUID();project.putUUID("id",projectId);project.putUUID("project",projectId);project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:clay_ball",4);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FORESTER,hut.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(output.getX()+1.5,output.getY(),output.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("worker",npc.getUUID());t.putUUID("operation",op);t.putString("stage","deliver");t.putInt("delivered",2);t.put("cargo",cargo);t.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));NbtRecord.write(MineWork.path(l,hut.id()),t);
  h.assertTrue(HarvestAccess.find(npc,clay,320)!=null&&own.countItem(Items.BIRCH_LOG)==64&&own.countItem(Items.STICK)==64,"Real clay route and earlier paid delivery are valid");
  var resource=new ResourceWorkGoal[]{new ResourceWorkGoal(npc,true,()->0L)};npc.goalSelector.addGoal(6,resource[0]);npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));var reloaded=new boolean[]{false};
  h.runAtTickTime(240,()->{
   if(fullHall){
    var current=NbtRecord.read(MineWork.path(l,hut.id()));
    h.assertTrue(current.getInt("delivered")==2&&current.getString("stage").equals("deliver")&&!current.contains("forestDeliveryAt")&&!WorldJournal.exists(l,Settlement.childId(op,"delivery/2")),"Two full chests retain the unpaid last seed without an intent or a new destination");
    chest.setItem(0,ItemStack.EMPTY);chest.setItem(1,ItemStack.EMPTY);
   }else h.assertTrue(chest.countItem(Items.BIRCH_SAPLING)==1,"The paid last sapling must physically leave the full workplace");
  });
  h.runAtTickTime(480,()->{
   if(fullHall){
    var current=NbtRecord.read(MineWork.path(l,hut.id()));
    h.assertTrue(current.getInt("delivered")==2&&!current.contains("forestDeliveryAt")&&!WorldJournal.exists(l,Settlement.childId(op,"delivery/2")),"Forest overflow must leave the last food slots free and retain its paid seed");
    h.assertTrue(LogisticsRoutes.fits(chest,List.of(new ItemStack(Items.WHEAT,16),new ItemStack(Items.BREAD,4))),"Food can still enter the protected pantry space");
    // Supply may already have delivered clay during the blocked timber wait.
    // Free only fixture filler; allow one clay slot plus the timber slot above the 12-slot reserve.
    int empty=0;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).isEmpty())empty++;
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FOREST_FULL_RELEASE clayBefore={} emptyBefore={}",chest.countItem(Items.CLAY_BALL),empty);
    for(int slot=0;slot<chest.getContainerSize()&&empty<14;slot++)if(chest.getItem(slot).is(Items.DIRT)){chest.setItem(slot,ItemStack.EMPTY);empty++;}
   }
  });
  h.onEachTick(()->{
   var current=NbtRecord.read(MineWork.path(l,hut.id()));
   if(current.contains("forestDeliveryAt")&&!reloaded[0]&&npc.distanceToSqr(stock.getX()+1.5,stock.getY(),stock.getZ()+.5)>9){
    var snap=JobCargo.snapshot(npc,true);
    h.assertTrue(ForestFixture.count(snap.items(),Items.BIRCH_SAPLING)==1&&ForestFixture.count(snap.items(),Items.BIRCH_LOG)==0&&ForestFixture.count(snap.items(),Items.STICK)==0,"Interrupted worker owns only the last seed, not earlier delivered goods");
    h.assertTrue(!snap.jobs().getCompound(0).getCompound("reset").contains("forestDeliveryAt"),"Released work clears the previous destination");
    npc.goalSelector.removeGoal(resource[0]);resource[0]=new ResourceWorkGoal(npc,true,()->0L);npc.goalSelector.addGoal(6,resource[0]);reloaded[0]=true;
   }
   if(chest.countItem(Items.BIRCH_SAPLING)>0)h.assertTrue(WorldJournal.inspectCommitted(l,Settlement.childId(op,"delivery/2"))!=null,"Returned seed has its original delivery receipt");
   h.assertTrue(l.getBlockState(tree).is(Blocks.BIRCH_LOG),"A full seed store must not start another unpaid felling job");
  });
  h.succeedWhen(()->{
   h.assertTrue(chest.countItem(Items.BIRCH_SAPLING)==1&&chest.countItem(Items.CLAY_BALL)==4,"Forester must finish the old paid cargo and physically deliver new demanded clay");
   h.assertTrue(reloaded[0]&&own.countItem(Items.BIRCH_LOG)==64&&own.countItem(Items.STICK)==64&&chest.countItem(Items.BIRCH_LOG)==0&&chest.countItem(Items.STICK)==0,"Reload neither replays previously delivered logs/sticks nor drops the paid seed");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Both real trips preserve worker health");com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FOREST_OVERFLOW VERIFIED bodyTicks={} sapling=1 clay=4 reload=true fullHall={}",npc.tickCount,fullHall);
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ForestWork.forget(hut.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
