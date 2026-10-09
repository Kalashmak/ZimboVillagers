package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmerCongestionGameTests {
 @GameTest(template="empty",batch="farmer_descent",timeoutTicks=900)
 public static void farmerWalksPastTrappedSheepAndDepositsItsOriginalHarvest(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+655360,110,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-5,36,-8,8);
  for(int x=-5;x<=36;x++)for(int z=-8;z<=8;z++)for(int y=-1;y<=4;y++)l.setBlock(base.offset(x,y,z),(y==-1?Blocks.COBBLESTONE:Blocks.AIR).defaultBlockState(),2);
  for(int x=-5;x<=0;x++)for(int z=-8;z<=8;z++)l.setBlock(base.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
  l.setBlock(base,Blocks.BARREL.defaultBlockState(),2);l.setBlock(base.offset(1,0,0),Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.NORTH),2);
  for(int y=0;y<=2;y++)l.setBlock(base.offset(2,y,0),Blocks.BIRCH_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.NORTH),2);
  for(int x=0;x<=2;x++)for(int y=0;y<=2;y++)l.setBlock(base.offset(x,y,1),(x==1?Blocks.GLASS:Blocks.BIRCH_LOG).defaultBlockState(),2);
  var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(l);sheep.setNoAi(true);sheep.moveTo(base.getX()+1.55,base.getY(),base.getZ()+.494371,0,0);l.addFreshEntity(sheep);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(24,-1,-4));
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var farm=new Settlement.Building(UUID.randomUUID(),"farm",0,0,0);
  s.addBuilding(hall);s.addBuilding(farm);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=BuildingPlacement.at(e,farm,1,1,4);l.setBlock(stock,Blocks.CHEST.defaultBlockState(),2);var chest=(Container)l.getBlockEntity(stock);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.FARMER,farm.id());npc.bind(s.id(),r);
  var work=MineWork.read(l,farm);work.putUUID("worker",r.id());work.putString("stage","deliver");
  var cargo=new ListTag();cargo.add(new ItemStack(Items.WHEAT,48).save(new CompoundTag()));work.put("cargo",cargo);
  var hoe=new ItemStack(Items.STONE_HOE);hoe.setDamageValue(119);work.put("tool",hoe.save(new CompoundTag()));MineWork.write(l,farm,work);
  npc.moveTo(base.getX()+.790219,base.getY()+1,base.getZ()+.499949,0,0);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->6000L));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Farmer chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();sheep.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(chest.countItem(Items.WHEAT)==48){
   var done=MineWork.read(l,farm);var returnedHoe=ItemStack.of(done.getCompound("tool"));
   h.assertTrue(returnedHoe.is(Items.STONE_HOE)&&returnedHoe.getDamageValue()==119&&done.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Exactly the existing harvest arrives; the worn hoe remains in the same ordinary work record");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth()&&sheep.getHealth()==sheep.getMaxHealth()&&done.getUUID("worker").equals(npc.getUUID())&&!CargoCustody.pending(l.getServer(),npc.getUUID()),"Both stay alive; no reassignment, custody shortcut or animal removal");clean.run();h.succeed();
  }});
  h.runAtTickTime(880,()->{var path=npc.getNavigation().getPath();String why="Farmer failed ordinary harvest delivery past sheep: "+npc.position()+" next="+(path==null?-1:path.getNextNodeIndex());clean.run();h.assertTrue(false,why);});
 }
}
