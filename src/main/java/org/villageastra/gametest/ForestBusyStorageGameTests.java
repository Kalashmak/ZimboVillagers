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
public final class ForestBusyStorageGameTests {
 @GameTest(template="empty",batch="forest_busy_storage",timeoutTicks=6000)
 public static void blockedTimberWaitsSafelyWhileForesterSuppliesClay(GameTestHelper h){
  var l=h.getLevel();var anchor=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(anchor.getX()+1015808,120,anchor.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-3,55,-3,16);
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(55,8,16)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var hut=new Settlement.Building(UUID.randomUUID(),"forester",20,0,0);s.addBuilding(hall);s.addBuilding(hut);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);var output=LogisticsRoutes.position(e,hut);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(output,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var own=LogisticsRoutes.chest(l,e,hut);
  var op=UUID.randomUUID();var cargo=new ListTag();var logs=new ItemStack(Items.BIRCH_LOG,6);chest.setItem(0,logs.copy());
  cargo.add(WorldJournal.takeAmount(l,Settlement.childId(op,"paid/0"),stock,0,logs,6).save(new CompoundTag()));
  for(int slot=0;slot<chest.getContainerSize()-2;slot++)chest.setItem(slot,new ItemStack(Items.DIRT,64));chest.setItem(0,new ItemStack(Items.BIRCH_LOG,61));
  for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.BIRCH_LOG,64));own.setItem(0,new ItemStack(Items.BIRCH_LOG,60));int timber=own.countItem(Items.BIRCH_LOG);
  var clay=base.offset(40,0,2);l.setBlock(clay,Blocks.CLAY.defaultBlockState(),2);var project=new CompoundTag();var projectId=UUID.randomUUID();project.putUUID("id",projectId);project.putUUID("project",projectId);project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:clay_ball",4);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FORESTER,hut.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition())&&l.isPositionEntityTicking(output),"Native entity chunks ready")).thenExecute(()->l.addFreshEntity(npc));
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("worker",npc.getUUID());t.putUUID("operation",op);t.putString("stage","deliver");t.putInt("delivered",0);t.putString("status","output_full");t.put("cargo",cargo);t.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));NbtRecord.write(MineWork.path(l,hut.id()),t);
  npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->0L));var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,supply);boolean[] custody={false},freed={false};
  h.onEachTick(()->{
   h.assertTrue(!npc.isInWaterOrBubble(),"Storage fixture remains dry");
   if(!freed[0]){
    var held=NbtRecord.read(MineWork.path(l,hut.id()));
    h.assertTrue(held.getUUID("operation").equals(op)&&held.getInt("delivered")==0&&held.getList("cargo",Tag.TAG_COMPOUND).equals(cargo),"Paused timber job remains intact");
    h.assertTrue(!WorldJournal.exists(l,Settlement.childId(op,"delivery/0")),"No failed destination intent strands timber");
    var snapshot=JobCargo.snapshot(npc,true);int wood=0,balls=0,axes=0;
    for(var raw:snapshot.items()){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.BIRCH_LOG))wood+=item.getCount();if(item.is(Items.CLAY_BALL))balls+=item.getCount();if(item.is(Items.STONE_AXE))axes+=item.getCount();}
    h.assertTrue(wood==6&&axes==1,"Custody retains paused logs and original axe exactly once");if(balls==4)custody[0]=true;
    if(chest.countItem(Items.CLAY_BALL)==4){h.assertTrue(custody[0],"Actual clay cargo was observed before delivery");npc.goalSelector.removeGoal(supply);own.setItem(0,new ItemStack(Items.BIRCH_LOG,58));freed[0]=true;}
   }
  });
  h.succeedWhen(()->{
   h.assertTrue(freed[0]&&WorldJournal.inspectCommitted(l,Settlement.childId(op,"delivery/0"))!=null&&own.countItem(Items.BIRCH_LOG)==timber+4,"Real clay trip and resumed timber delivery finish: ticks="+npc.tickCount+" pos="+npc.position()+" status="+npc.workStatus()+" clay="+chest.countItem(Items.CLAY_BALL)+" forest="+NbtRecord.read(MineWork.path(l,hut.id()))+" natural="+NaturalSupplyGoal.inspect(l,npc.getUUID()));
   h.assertTrue(chest.countItem(Items.BIRCH_LOG)==61&&chest.countItem(Items.CLAY_BALL)==4,"Pantry timber reserve and gathered clay conserved");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FOREST_BUSY_STORAGE VERIFIED bodyTicks={} logs=6 clay=4 custody=true",npc.tickCount);
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ForestWork.forget(hut.id());PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
}
