package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PlantHarvestGameTests {
 @GameTest(template="empty",batch="plant_harvest",timeoutTicks=1600)
 public static void gathererFindsCaneTopAndReturnsItWithoutBreakingBase(GameTestHelper h){harvest(h,40);}
 @GameTest(template="empty",batch="plant_expedition",timeoutTicks=6000)
 public static void paperDemandSendsGathererBeyondLocalAreaWithoutForcedDestination(GameTestHelper h){harvest(h,160);}
 @GameTest(template="empty",batch="plant_survey_resume",timeoutTicks=6000)
 public static void paperSurveyContinuesAcrossGoalReloadsAndStillDelivers(GameTestHelper h){harvest(h,160,true);}
 @GameTest(template="empty",batch="bookshelf_expedition",timeoutTicks=6000)
 public static void bookshelfDemandResolvesThroughBindingAndPaperToWildCane(GameTestHelper h){harvest(h,160,false,true);}
 private static void harvest(GameTestHelper h,int distance){harvest(h,distance,false);}
 private static void harvest(GameTestHelper h,int distance,boolean reloadSurvey){harvest(h,distance,reloadSurvey,false);}
 private static void harvest(GameTestHelper h,int distance,boolean reloadSurvey,boolean shelf){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO).offset(shelf?49152:distance>96?(reloadSurvey?12288:4096):0,0,0);int height=90;
  for(int x=(at.getX()-2)>>4;x<=(at.getX()+distance+4)>>4;x++)for(int z=(at.getZ()-2)>>4;z<=(at.getZ()+9)>>4;z++)l.getChunk(x,z);
  for(int x=-2;x<=distance+4;x++)for(int z=-2;z<=9;z++)height=Math.max(height,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX()+x,at.getZ()+z)+2);
  var base=new BlockPos(at.getX(),height,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()>>4)-1;x<=(base.getX()>>4)+1;x++)for(int z=(base.getZ()>>4)-1;z<=(base.getZ()>>4)+1;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=distance+4;x++)for(int z=-2;z<=9;z++)for(int y=0;y<=6;y++)l.setBlock(base.offset(x,y,z),y==0?Blocks.DIRT.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);if(shelf){s.addBuilding(new Settlement.Building(UUID.randomUUID(),"farm",-48,0,0));s.addBuilding(new Settlement.Building(UUID.randomUUID(),"forester",-96,0,0));}
  l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);
  var cane=base.offset(distance,1,2);l.setBlock(cane.below().south(),Blocks.WATER.defaultBlockState(),3);for(int y=0;y<3;y++)l.setBlock(cane.above(y),Blocks.SUGAR_CANE.defaultBlockState(),3);var target=cane.above(2);
  h.assertTrue(l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,cane.getX(),cane.getZ())<target.getY(),"Cane top is above the ground heightmap");
  h.assertTrue(NaturalSupplyGoal.safe(l,target),"Top is a safe, unprotected harvest target");
  // This extraction fixture already has its eight young local plants; its next harvest belongs in stock.
  for(int i=0;i<ReedNursery.CAPACITY;i++){var young=base.offset(20+i,1,7);l.setBlock(young.below().south(),Blocks.WATER.defaultBlockState(),3);l.setBlock(young,Blocks.SUGAR_CANE.defaultBlockState(),3);ReedNursery.record(l,s.id(),young);}
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,base.getY()+1,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var project=new CompoundTag();project.putUUID("id",UUID.randomUUID());var cost=new CompoundTag();cost.putInt(shelf?"minecraft:bookshelf":distance>96?"minecraft:paper":"minecraft:sugar_cane",shelf?2:distance>96?3:1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);
  var needs=Workshops.needs(l,e,Workshops.spec("town_hall"),chest,Workshops.wants(l,e));
  if(distance>96)h.assertTrue(needs.stream().anyMatch(n->n.ingredient().test(new net.minecraft.world.item.ItemStack(Items.SUGAR_CANE))),"Paper demand resolves to cane: "+needs);
  h.assertTrue(HarvestAccess.find(npc,target,NaturalSupplyGoal.ROUTE_RANGE)!=null,"Real plant has a reversible route before surveying");
  var goal=new NaturalSupplyGoal[]{new NaturalSupplyGoal(npc,true)};
  if(reloadSurvey)h.runAtTickTime(10,()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).contains("surveyCursor"),"Survey progress is durable before it finds any resource"));
  if(distance>96)h.runAtTickTime(5900,()->h.assertTrue(false,"Expedition stalled at "+npc.position()+" ticking="+TouchLoad.ticking(l,npc.blockPosition())+" trip="+NaturalSupplyGoal.inspect(l,npc.getUUID())));
  h.startSequence().thenWaitUntil(()->{if(reloadSurvey)goal[0]=new NaturalSupplyGoal(npc,true);npc.tickCount+=100;h.assertTrue(goal[0].canUse(),"Survey still searching, resident role="+r.profession());}).thenExecute(()->{
   var trip=NaturalSupplyGoal.inspect(l,npc.getUUID());h.assertTrue(BlockPos.of(trip.getLong("target")).equals(target),"Demand-driven survey selected the real cane top: "+trip);npc.goalSelector.addGoal(5,goal[0]);
   if(distance>96)h.assertTrue(!l.getForcedChunks().contains(new net.minecraft.world.level.ChunkPos(target).toLong()),"Destination is not force-loaded by the fixture");
  }).thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Entity chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{
   if(chest.countItem(Items.SUGAR_CANE)==1&&!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))){
   h.assertTrue(l.getBlockState(cane).is(Blocks.SUGAR_CANE)&&l.getBlockState(cane.above()).is(Blocks.SUGAR_CANE)&&l.getBlockState(target).isAir(),"Only the paid harvest's top is cut; growing base remains");
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  }});
 }
}
