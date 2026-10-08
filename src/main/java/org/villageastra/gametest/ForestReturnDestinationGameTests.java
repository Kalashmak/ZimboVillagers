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
public final class ForestReturnDestinationGameTests {
 @GameTest(template="empty",batch="forest_return_destination",timeoutTicks=6000)
 public static void crowdedHallReturnsRemainingSeedToHutAndReleasesSupplyAcrossReplay(GameTestHelper h){
  var l=h.getLevel();var anchor=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(anchor.getX()+983040,120,anchor.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-3,55,-3,16);
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(55,8,16)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var hut=new Settlement.Building(UUID.randomUUID(),"forester",20,0,0);s.addBuilding(hall);s.addBuilding(hut);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);var output=LogisticsRoutes.position(e,hut);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(output,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var own=LogisticsRoutes.chest(l,e,hut);
  var op=UUID.randomUUID();var cargo=new ListTag();int index=0;
  for(var item:List.of(new ItemStack(Items.BIRCH_LOG,5),new ItemStack(Items.BIRCH_SAPLING,4),new ItemStack(Items.STICK))){
   chest.setItem(index,item.copy());var paid=WorldJournal.takeAmount(l,Settlement.childId(op,"paid/"+index),stock,index,item,item.getCount());h.assertTrue(paid.getCount()==item.getCount(),"Cargo is really withdrawn");cargo.add(paid.save(new CompoundTag()));
   if(index==0)h.assertTrue(WorldJournal.deposit(l,Settlement.childId(op,"delivery/0"),stock,paid),"Earlier logs have already reached the hall");index++;
  }
  for(int slot=1;slot<chest.getContainerSize()-2;slot++)chest.setItem(slot,new ItemStack(Items.DIRT,64));
  for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.BIRCH_SAPLING,64));own.setItem(0,new ItemStack(Items.BIRCH_SAPLING,60));own.setItem(1,new ItemStack(Items.STICK,63));int seeds=own.countItem(Items.BIRCH_SAPLING);
  h.assertTrue(!LogisticsRoutes.surplusFits(chest,new ItemStack(Items.BIRCH_SAPLING,4)),"Two pantry slots stay reserved for essentials");
  var clay=base.offset(40,0,2);l.setBlock(clay,Blocks.CLAY.defaultBlockState(),2);var project=new CompoundTag();var projectId=UUID.randomUUID();project.putUUID("id",projectId);project.putUUID("project",projectId);project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:clay_ball",4);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FORESTER,hut.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition())&&l.isPositionEntityTicking(output),"Native entity chunks ready")).thenExecute(()->l.addFreshEntity(npc));
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("worker",npc.getUUID());t.putUUID("operation",op);t.putString("stage","deliver");t.putInt("delivered",1);t.putLong("forestDeliveryAt",stock.asLong());t.put("cargo",cargo);t.put("tool",new ItemStack(Items.STONE_AXE).save(new CompoundTag()));NbtRecord.write(MineWork.path(l,hut.id()),t);
  var goal=new ResourceWorkGoal[]{new ResourceWorkGoal(npc,true,()->0L)};npc.goalSelector.addGoal(6,goal[0]);npc.goalSelector.addGoal(5,new NaturalSupplyGoal(npc,true));boolean[] replayed={false};
  h.onEachTick(()->{
   h.assertTrue(!npc.isInWaterOrBubble(),"The storage fixture stays dry; surrounding world fluids must not alter the route");
   if(!replayed[0]&&WorldJournal.inspectCommitted(l,Settlement.childId(op,"delivery/1"))!=null){
    h.assertTrue(npc.distanceToSqr(output.getX()+1.5,output.getY(),output.getZ()+.5)<9,"Seed delivery follows real travel to the hut");
    npc.goalSelector.removeGoal(goal[0]);NbtRecord.write(MineWork.path(l,hut.id()),t.copy());goal[0]=new ResourceWorkGoal(npc,true,()->0L);npc.goalSelector.addGoal(6,goal[0]);replayed[0]=true;
   }
   h.assertTrue(chest.countItem(Items.BIRCH_SAPLING)==0&&chest.countItem(Items.BIRCH_LOG)==5,"Reserved pantry space and already delivered logs are preserved");
  });
  h.succeedWhen(()->{
   h.assertTrue(replayed[0]&&own.countItem(Items.BIRCH_SAPLING)==seeds+4&&own.countItem(Items.STICK)==64&&chest.countItem(Items.CLAY_BALL)==4,"Actual return, stale checkpoint replay, and subsequent clay delivery all finish: ticks="+npc.tickCount+" pos="+npc.position()+" status="+npc.workStatus()+" replay="+replayed[0]+" seedDelta="+(own.countItem(Items.BIRCH_SAPLING)-seeds)+" sticks="+own.countItem(Items.STICK)+" clay="+chest.countItem(Items.CLAY_BALL)+" ticking="+l.isPositionEntityTicking(npc.blockPosition())+" registered="+(l.getEntity(npc.getUUID())==npc)+" removed="+npc.isRemoved()+" goals="+npc.runningGoals()+" nav="+npc.getNavigation().getPath()+" navTarget="+npc.getNavigation().getTargetPos()+" navDone="+npc.getNavigation().isDone()+" forest="+NbtRecord.read(MineWork.path(l,hut.id()))+" natural="+NaturalSupplyGoal.inspect(l,npc.getUUID()));
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Both trips preserve health");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FOREST_RETURN VERIFIED bodyTicks={} saplings=4 stick=1 clay=4 staleReplay=true",npc.tickCount);
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());ForestWork.forget(hut.id());PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
}
