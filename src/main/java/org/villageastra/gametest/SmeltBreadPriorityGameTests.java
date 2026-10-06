package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Paid furnace fuel must reach its slot before the carrier starts hand bread. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SmeltBreadPriorityGameTests {
 @GameTest(template="empty",batch="smelt_bread_priority",timeoutTicks=6000)
 public static void carrierFinishesPaidSmeltingAndThenBakesWithoutLosingEitherCargo(GameTestHelper h){
  trip(h,false);
 }
 @GameTest(template="empty",batch="smelt_bread_priority",timeoutTicks=200)
 public static void hungryCarrierMayBakeWithoutSpendingOrDroppingPaidFuel(GameTestHelper h){trip(h,true);}
 private static void trip(GameTestHelper h,boolean hungry){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+786432,120,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+20)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+12)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(20,7,12)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=Workshops.station(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.SAND));chest.setItem(1,new ItemStack(Items.BIRCH_LOG));l.setBlock(base.offset(4,1,4),Blocks.FURNACE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MAYOR,hall.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  for(int i=0;i<12&&!Workshops.inspect(l,hall.id()).getString("stage").equals("smelt_put_fuel");i++)Workshops.advance(l,e,hall,1000+i*20,List.of(new Workshops.Want(Ingredient.of(Items.GLASS),1,hall.id())),npc.getUUID());
  var job=Workshops.inspect(l,hall.id());h.assertTrue(job.getString("stage").equals("smelt_put_fuel")&&ItemStack.of(job.getCompound("carried")).is(Items.BIRCH_LOG)&&chest.countItem(Items.BIRCH_LOG)==0,"Fixture fuel is really withdrawn and owned by this carrier");
  h.assertTrue(NaturalFurnace.claim(l,hall,job,npc.getUUID()),"Paid job belongs to the real carrier");chest.setItem(2,new ItemStack(Items.WHEAT,5));
  h.assertTrue(HandBread.actionable(l,e),"Bread is genuinely needed and the wheat is available");
  if(hungry){
   var r=s.resident(npc.getUUID());s.unassign(r.id());r.restoreNeeds(-1,-1,Population.HUNGRY,0);
   h.assertTrue(!Population.mayWork(r)&&HandBreadGoal.calls(npc,true,0L),"A hungry worker unable to finish normal work retains emergency bread eligibility");
   var retained=Workshops.inspect(l,hall.id());h.assertTrue(retained.getUUID("id").equals(job.getUUID("id"))&&ItemStack.of(retained.getCompound("carried")).is(Items.BIRCH_LOG)&&chest.countItem(Items.BIRCH_LOG)==0,"Eligibility neither spends nor abandons the paid fuel");
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();return;
  }
  h.assertTrue(!HandBreadGoal.calls(npc,true,0L),"Hand bread must not strand fuel already carried to the furnace");
  npc.goalSelector.addGoal(5,new WorkshopGoal(npc,true,l::getGameTime));npc.goalSelector.addGoal(6,new HandBreadGoal(npc,true,()->0L));
  var waited=new boolean[]{false};h.onEachTick(()->{
   var current=Workshops.inspect(l,hall.id());
   var furnace=(net.minecraft.world.level.block.entity.FurnaceBlockEntity)l.getBlockEntity(base.offset(4,1,4));
   if(current.getString("stage").equals("smelt_wait")&&l.getBlockState(base.offset(4,1,4)).getValue(net.minecraft.world.level.block.FurnaceBlock.LIT)&&!furnace.getItem(0).isEmpty()&&furnace.getItem(2).isEmpty()){
    h.assertTrue(HandBreadGoal.calls(npc,true,0L),"A burning furnace leaves its owner free to bake during the ordinary cook time");waited[0]=true;
   }
  });
  var id=job.getUUID("id");h.succeedWhen(()->{
   var smelt=Workshops.inspect(l,hall.id());var bread=HandBread.inspect(l,s.id());
   h.assertTrue(chest.countItem(Items.GLASS)==1&&chest.countItem(Items.BREAD)==2,"The same body must finish real furnace output and real bread: ticks="+npc.tickCount+" stage="+smelt.getString("stage")+" goals="+npc.runningGoals()+" bread="+bread.getString("stage")+" labor="+bread.getLong("labor"));
   h.assertTrue(id.equals(smelt.getUUID("id"))&&smelt.getString("stage").equals("idle")&&!smelt.contains("carried"),"The same paid furnace job completes and returns its cargo once");
   h.assertTrue(bread.getLong("labor")==bread.getLong("needLabor")&&chest.countItem(Items.WHEAT)==0&&chest.countItem(Items.SAND)==0&&chest.countItem(Items.BIRCH_LOG)==0,"Neither paid raw material nor fuel nor bread labor is duplicated");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Carrier remains healthy");
   h.assertTrue(waited[0],"Actual burning wait did not lock the owner out of hand bread");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SMELT_BREAD VERIFIED bodyTicks={} glass=1 bread=2 breadLabor={}",npc.tickCount,bread.getLong("labor"));
   npc.discard();HandBread.release(l,s.id(),npc.getUUID());SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
