package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** A sick owner leaves paid clay in a cold furnace, with no fuel in its hands. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SmeltFuelReliefGameTests {
 @GameTest(template="empty",batch="smelt_fuel_relief",timeoutTicks=2400)
 public static void healthyColleaguePhysicallyRefuelsSickOwnersPaidClay(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="smelt_fuel_relief",timeoutTicks=800)
 public static void refuelReliefKeepsUnrecordedPaidFuelAndRejectsUnsafeFurnaces(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean guards){
  var l=h.getLevel();var p=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(p.getX()+2883584+(guards?32768:0),120,p.getZ());
  var chunks=PhysicalFixtureChunks.force(l,base,-3,20,-3,14);for(var cp:chunks)l.getChunk(cp.x,cp.z);
  for(var cell:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(20,6,14)))l.setBlock(cell,cell.getY()<=120?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));
  var station=Workshops.station(e,hall);l.setBlock(station,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.CLAY_BALL,4));stock.setItem(1,new ItemStack(Items.COAL));var at=base.offset(4,1,4);l.setBlock(at,Blocks.FURNACE.defaultBlockState(),2);
  var bodies=new ArrayList<ResidentEntity>();for(int i=0;i<2;i++){
   var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+1.5+i*12,121,base.getZ()+2.5);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);bodies.add(npc);
  }
  var old=bodies.get(0);var helper=bodies.get(1);var wants=List.of(new Workshops.Want(Ingredient.of(Items.BRICK),4,hall.id()));UUID[] jobId={null};boolean[] successorSeen={false};
  Runnable clean=()->{for(var npc:bodies)npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,chunks);};
  h.startSequence().thenWaitUntil(()->h.assertTrue(bodies.stream().allMatch(n->l.isPositionEntityTicking(n.blockPosition())),"Both actual fixture chunks are entity-ticking"))
   .thenExecute(()->{SettlementData.get(l.getServer()).add(e);for(var npc:bodies)h.assertTrue(l.getEntity(npc.getUUID())==null&&l.addFreshEntity(npc)&&l.getEntity(npc.getUUID())==npc,"Canonical real body admitted after chunk readiness");})
   .thenWaitUntil(()->h.assertTrue(bodies.stream().allMatch(n->n.tickCount>0&&n.onGround()&&l.getEntity(n.getUUID())==n),"Admitted physical bodies have actually ticked and settled"))
   .thenExecute(()->{
    try{
     for(int i=0;i<14;i++){var current=Workshops.inspect(l,hall.id());if(current.getString("stage").equals("smelt_fuel"))break;Workshops.advance(l,e,hall,1000+i*20,wants,old.getUUID());NaturalFurnace.claim(l,hall,Workshops.inspect(l,hall.id()),old.getUUID());}
     var job=Workshops.inspect(l,hall.id());jobId[0]=job.getUUID("id");var f=(FurnaceBlockEntity)l.getBlockEntity(at);
     h.assertTrue(job.getString("stage").equals("smelt_fuel")&&job.getUUID("worker").equals(old.getUUID())&&!job.contains("carried")&&f.getItem(0).is(Items.CLAY_BALL)&&f.getItem(0).getCount()==4&&f.getItem(1).isEmpty()&&f.getItem(2).isEmpty()&&stock.countItem(Items.CLAY_BALL)==0&&stock.countItem(Items.COAL)==1,"Four paid clay are in the real cold furnace; coal remains in stock and no item is carried");
     h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Healthy owner retains its unfinished job");s.resident(old.getUUID()).fallIll();
     h.assertTrue(!WorldJournal.exists(l,Settlement.childId(jobId[0],"smelt/fuel/0")),"No pre-save fuel withdrawal exists");
     h.assertTrue(NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Healthy colleague must be able to refuel sick owner's paid clay with empty hands");
     if(guards){
      var carried=job.copy();carried.put("carried",new ItemStack(Items.COAL).save(new CompoundTag()));h.assertTrue(!NaturalFurnace.availableTo(l,hall,carried,helper.getUUID()),"Saved paid fuel stays exclusive");
      s.resident(helper.getUUID()).fallIll();h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Sick helper cannot take over");s.resident(helper.getUUID()).cure();
      f.getPersistentData().putUUID("AstraSmeltJob",UUID.randomUUID());h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Foreign furnace marker is refused");f.getPersistentData().putUUID("AstraSmeltJob",jobId[0]);
      f.setItem(0,ItemStack.EMPTY);h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Missing paid raw material is refused");f.setItem(0,new ItemStack(Items.SAND,4));h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Foreign raw material is refused");f.setItem(0,new ItemStack(Items.CLAY_BALL,4));
      f.setItem(1,new ItemStack(Items.COAL));h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Already fueled furnace is refused");f.setItem(1,ItemStack.EMPTY);
      l.setBlock(at,f.getBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.LIT,true),2);h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Burning furnace is refused");l.setBlock(at,f.getBlockState().setValue(net.minecraft.world.level.block.FurnaceBlock.LIT,false),2);
      f.setItem(2,new ItemStack(Items.BRICK,4));h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Finished output needs collection, not a new fuel claim");f.setItem(2,new ItemStack(Items.GLASS));h.assertTrue(!NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Foreign output is refused");f.setItem(0,new ItemStack(Items.CLAY_BALL,3));f.setItem(2,new ItemStack(Items.BRICK));h.assertTrue(NaturalFurnace.availableTo(l,hall,job,helper.getUUID()),"Partially cooked original batch may be refueled");f.setItem(0,new ItemStack(Items.CLAY_BALL,4));f.setItem(2,ItemStack.EMPTY);
      var unloaded=job.copy();var far=base.offset(65536,0,0);h.assertTrue(!l.hasChunkAt(far),"Control chunk is actually unloaded");unloaded.putLong("furnace",far.asLong());h.assertTrue(!NaturalFurnace.availableTo(l,hall,unloaded,helper.getUUID())&&!l.hasChunkAt(far),"Observation does not load another furnace chunk");
      var before=Workshops.inspect(l,hall.id());var paid=WorldJournal.takeAmount(l,Settlement.childId(jobId[0],"smelt/fuel/0"),station,1,stock.getItem(1).copy(),1);h.assertTrue(paid.is(Items.COAL)&&paid.getCount()==1&&stock.countItem(Items.COAL)==0&&before.equals(Workshops.inspect(l,hall.id())),"Real fuel debit commits before the unchanged job records carried fuel");
      h.assertTrue(!NaturalFurnace.claim(l,hall,job,helper.getUUID())&&before.equals(Workshops.inspect(l,hall.id())),"Pre-save receipt cannot transfer ownership or rewrite the job");var held=new ListTag();var reset=NaturalFurnace.custody(l,job,held);h.assertTrue(held.size()==1&&ItemStack.of(held.getCompound(0)).is(Items.COAL)&&ItemStack.of(held.getCompound(0)).getCount()==1&&reset.getInt("fuels")==1&&!reset.contains("worker"),"Original custody recovers exactly one real paid coal");
      com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SMELT_FUEL_RELIEF_GUARDS paidCoal=1 stockCoal=0 clayInFurnace=4 unchangedJob=true bodyTicks={}",helper.tickCount);clean.run();h.succeed();return;
     }
     helper.goalSelector.addGoal(6,new WorkshopGoal(helper,true,l::getGameTime));
    }catch(GameTestAssertException ex){clean.run();throw ex;}
   })
   .thenWaitUntil(()->{if(guards)return;var job=Workshops.inspect(l,hall.id());if(job.hasUUID("worker")&&job.getUUID("worker").equals(helper.getUUID()))successorSeen[0]=true;h.assertTrue(stock.countItem(Items.BRICK)==4,"Healthy body must physically refuel, collect and deliver original four bricks: "+job.getString("stage"));})
   .thenExecute(()->{if(guards)return;var job=Workshops.inspect(l,hall.id());h.assertTrue(successorSeen[0]&&job.getUUID("id").equals(jobId[0])&&job.getString("stage").equals("idle")&&!job.contains("carried"),"Same paid job completes under its successor");h.assertTrue(stock.countItem(Items.CLAY_BALL)==0&&stock.countItem(Items.COAL)==0&&s.resident(old.getUUID()).sick()&&helper.getHealth()==helper.getMaxHealth(),"No donated input, fuel, cure or damage");h.assertTrue(WorldJournal.inspectCommitted(l,Settlement.childId(jobId[0],"smelt/output"))!=null&&WorldJournal.inspectCommitted(l,Settlement.childId(jobId[0],"smelt/deliver"))!=null,"Original physical output and delivery receipts committed");var cargo=JobCargo.snapshot(old,true);h.assertTrue(cargo.items().isEmpty()&&cargo.jobs().isEmpty(),"Former owner cannot reclaim successor output");com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SMELT_FUEL_RELIEF bodyTicks={} bricks=4 clay=0 coal=0 sameJob={} oldSick=true",helper.tickCount,jobId[0]);clean.run();}).thenSucceed();
 }
}
