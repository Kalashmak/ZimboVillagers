package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmSeedTravelGameTests {
 @GameTest(template="empty",batch="farm_seed_travel",timeoutTicks=6000)
 public static void farmerWalksBackWithSeedThenTillsAndResowsLastTrampledPlot(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+2293760,130,at.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-6,65,-6,35);
  for(var p:BlockPos.betweenClosed(base.offset(-6,-1,-6),base.offset(65,10,35)))l.setBlock(p,p.getY()<base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",20,0,0);s.addBuilding(hall);s.addBuilding(farm);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);ResearchV2Town.lay(l,e,farm,"farm");
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FARMER,farm.id());npc.bind(s.id(),s.resident(r.id()));npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var plots=FarmWorkArea.cells(l,e,npc.getUUID());h.assertTrue(plots.size()==80,"One complete real field");
  for(var p:plots){l.setBlock(p.below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7),2);for(int y=0;y<3;y++)l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);l.setBlock(p.above(3),Blocks.SEA_LANTERN.defaultBlockState(),2);l.setBlock(p,Blocks.WHEAT.defaultBlockState(),2);}
  var water=FarmField.water(e,farm).get(0);l.setBlock(water,Blocks.WATER.defaultBlockState(),2);l.setBlock(water.above(),FarmField.COVER,2);
  var target=plots.get(0);l.setBlock(target,Blocks.AIR.defaultBlockState(),2);l.setBlock(target.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);
  var pos=LogisticsRoutes.position(e,farm);var chest=LogisticsRoutes.chest(l,e,farm);chest.clearContent();chest.setItem(0,new ItemStack(Items.WHEAT_SEEDS));var id=UUID.randomUUID();h.assertTrue(WorldJournal.takeAmount(l,id,pos,0,chest.getItem(0).copy(),1).getCount()==1,"Original borrowed seed is paid");
  var state=new CompoundTag();state.putInt("schema",1);state.putInt("width",1);state.putInt("height",3);state.putUUID("worker",npc.getUUID());state.putUUID("operation",id);state.putString("stage","plant");state.putString("crop","wheat");state.putLong("target",target.asLong());state.putLong("plantSource",pos.asLong());state.put("tool",new ItemStack(Items.STONE_HOE).save(new CompoundTag()));MineWork.write(l,farm,state);
  npc.moveTo(base.getX()+60.5,base.getY(),base.getZ()+4.5);npc.setOnGround(true);h.assertTrue(npc.distanceToSqr(pos.getX(),pos.getY(),pos.getZ())>600,"Native walk starts away from storage");npc.goalSelector.addGoal(3,new ResidentDoorGoal(npc));npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->6000L));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition())&&l.isPositionEntityTicking(pos),"Native ticking chunks ready")).thenExecute(()->l.addFreshEntity(npc));
  h.succeedWhen(()->{
   var work=MineWork.read(l,farm);h.assertTrue(WorldJournal.inspectCommitted(l,Settlement.childId(id,"plant_return"))!=null&&l.getBlockState(target).is(Blocks.WHEAT),"Actual walk, refund, till and resow finish: ticks="+npc.tickCount+" pos="+npc.position()+" work="+work);
   h.assertTrue(ItemStack.of(work.getCompound("tool")).getDamageValue()==1,"One actual till wears the hoe once");
   h.assertTrue(chest.countItem(Items.WHEAT_SEEDS)==0&&npc.getHealth()==npc.getMaxHealth(),"Single returned seed is spent on the real crop without harm");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_FARM_SEED_TRAVEL VERIFIED bodyTicks={} return=1 till=1 planted=1",npc.tickCount);
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
}
