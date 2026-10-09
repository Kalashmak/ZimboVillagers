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
public final class CarrierDescentGameTests {
 @GameTest(template="empty",batch="carrier_descent",timeoutTicks=900)
 public static void reassignedCarrierStepsOffBarrelAndReturnsItsExistingCargo(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+557056,110,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-5,36,-8,8);
  for(int x=-5;x<=36;x++)for(int z=-8;z<=8;z++)for(int y=-1;y<=4;y++)l.setBlock(base.offset(x,y,z),(y==-1?Blocks.COBBLESTONE:Blocks.AIR).defaultBlockState(),2);
  for(int x=-5;x<=0;x++)for(int z=-8;z<=8;z++)l.setBlock(base.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
  l.setBlock(base,Blocks.BARREL.defaultBlockState(),2);l.setBlock(base.offset(1,0,0),Blocks.BIRCH_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.NORTH),2);
  for(int y=0;y<=2;y++)l.setBlock(base.offset(2,y,0),Blocks.BIRCH_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.NORTH),2);
  for(int x=0;x<=2;x++)for(int y=0;y<=2;y++)l.setBlock(base.offset(x,y,1),(x==1?Blocks.GLASS:Blocks.BIRCH_LOG).defaultBlockState(),2);
  var sheep=net.minecraft.world.entity.EntityType.SHEEP.create(l);sheep.setNoAi(true);sheep.moveTo(base.getX()+1.55,base.getY(),base.getZ()+.494371,0,0);l.addFreshEntity(sheep);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(24,0,-4));
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var mine=new Settlement.Building(UUID.randomUUID(),"mine",8,0,0);
  s.addBuilding(hall);s.addBuilding(mine);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=HallSite.stock(e);l.setBlock(stock,Blocks.CHEST.defaultBlockState(),2);var chest=(Container)l.getBlockEntity(stock);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),r);
  var work=MineWork.read(l,mine);work.putUUID("worker",r.id());work.putString("stage","deliver");work.putBoolean("advanced",true);
  var cargo=new ListTag();cargo.add(new ItemStack(Items.COBBLESTONE,10).save(new CompoundTag()));work.put("cargo",cargo);
  var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(12);work.put("tool",pick.save(new CompoundTag()));MineWork.write(l,mine,work);
  s.assign(r.id(),Profession.PORTER,hall.id());npc.moveTo(base.getX()+.790219,base.getY()+1,base.getZ()+.499949,0,0);npc.setOnGround(true);
  h.assertTrue(CargoCustody.beginReturn(npc),"Existing cargo enters custody after reassignment");npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  npc.goalSelector.addGoal(4,new Goal(){
   {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}public boolean canUse(){return CargoCustody.pending(l.getServer(),npc.getUUID());}public boolean requiresUpdateEveryTick(){return true;}
   public void tick(){if(npc.tickCount%20==0)CargoCustody.returnStep(npc,true);}
  });
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Carrier chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  Runnable clean=()->{npc.discard();sheep.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{if(!CargoCustody.pending(l.getServer(),npc.getUUID())){
   h.assertTrue(chest.countItem(Items.COBBLESTONE)==10&&chest.countItem(Items.STONE_PICKAXE)==1,"Exactly the existing held cargo is physically returned");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth()&&sheep.getHealth()==sheep.getMaxHealth()&&!MineWork.read(l,mine).hasUUID("worker"),"One-block descent is safe and releases old job only after return");clean.run();h.succeed();
  }});
  h.runAtTickTime(880,()->{var path=npc.getNavigation().getPath();String why="Carrier failed barrel descent: "+npc.position()+" next="+(path==null?-1:path.getNextNodeIndex());clean.run();h.assertTrue(false,why);});
 }
 @GameTest(template="empty",batch="carrier_route_scope",timeoutTicks=100)
 public static void occupiedLandingIsExcludedOnlyFromItsOwnNativeSearch(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()-589824,100,at.getZ());
  var held=PhysicalFixtureChunks.force(l,base,-2,12,-3,3);
  try{
   for(int x=-2;x<=12;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=3;y++)l.setBlock(base.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY()+1,base.getZ()+.5,0,0);npc.setOnGround(true);
   var landing=base.offset(4,1,0);var end=base.offset(10,1,0);var before=npc.getNavigation().getPath();float water=npc.getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER);
   var detour=ResourceReturnRoute.plan(npc,end,Set.of(landing));h.assertTrue(detour!=null&&detour.canReach(),"Native planner finds safe alternative around occupied landing");
   for(int i=0;i<detour.getNodeCount();i++)h.assertTrue(!detour.getNodePos(i).equals(landing),"Scoped search excludes observed landing");
   h.assertTrue(npc.getNavigation().getPath()==before&&npc.getPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER)==water,"Planning leaves active navigation and water preferences untouched");
   var fresh=npc.routeTo(landing,0,NaturalSupplyGoal.ROUTE_RANGE);h.assertTrue(fresh!=null&&fresh.canReach()&&fresh.getEndNode().asBlockPos().equals(landing),"Next ordinary search can reach the now unoccupied landing");
  }finally{PhysicalFixtureChunks.release(l,held);}
  h.succeed();
 }
}
