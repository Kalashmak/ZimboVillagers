package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class UnstartedSmeltReliefGameTests {
 @GameTest(template="empty",batch="unstarted_smelt_relief",timeoutTicks=2400)
 public static void presentColleagueFinishesTheSameUntouchedJobWhileItsOwnerIsStranded(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="unstarted_smelt_relief",timeoutTicks=600)
 public static void reliefRejectsFreshJobsAbsentHelpersAndAnyPaidCustody(GameTestHelper h){check(h,true);}
 /** Use the explicit fixture clock when supported; the old production remains a runnable baseline. */
 private static boolean available(net.minecraft.server.level.ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker,long now){
  try{return (boolean)NaturalFurnace.class.getMethod("availableTo",net.minecraft.server.level.ServerLevel.class,Settlement.Building.class,CompoundTag.class,UUID.class,long.class).invoke(null,l,b,t,worker,now);}
  catch(NoSuchMethodException old){return NaturalFurnace.availableTo(l,b,t,worker);}
  catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
 }
 private static boolean claim(net.minecraft.server.level.ServerLevel l,Settlement.Building b,CompoundTag t,UUID worker,long now){
  try{return (boolean)NaturalFurnace.class.getMethod("claim",net.minecraft.server.level.ServerLevel.class,Settlement.Building.class,CompoundTag.class,UUID.class,long.class).invoke(null,l,b,t,worker,now);}
  catch(NoSuchMethodException old){return NaturalFurnace.claim(l,b,t,worker);}
  catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
 }
 private static void check(GameTestHelper h,boolean guards){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+1376256+(guards?65536:0),120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,48,-8,8);
  for(int x=-4;x<=48;x++)for(int z=-8;z<=8;z++)for(int y=-10;y<=4;y++){
   boolean hollow=x>=24&&x<=40&&Math.abs(z)<=3;l.setBlock(base.offset(x,y,z),(y<=(hollow?-9:0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var station=Workshops.station(e,hall);l.setBlock(station,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.SAND));stock.setItem(1,new ItemStack(Items.COAL));l.setBlock(base.offset(4,1,4),Blocks.FURNACE.defaultBlockState(),2);
  var bodies=new ArrayList<ResidentEntity>();
  for(int i=0;i<2;i++){
   var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),s.resident(r.id()));
   npc.moveTo(i==0?base.getX()+32.5:station.getX()+1.5,i==0?112:station.getY(),i==0?base.getZ()+.5:station.getZ()+.5);npc.setOnGround(true);npc.onlyGoals(g->false,0,new net.minecraft.world.entity.ai.goal.Goal(){public boolean canUse(){return false;}});h.assertTrue(l.addFreshEntity(npc),"Physical colleague registered");bodies.add(npc);
  }
  var old=bodies.get(0);var helper=bodies.get(1);var wants=List.of(new Workshops.Want(Ingredient.of(Items.GLASS),1,hall.id()));
  Workshops.advance(l,e,hall,0,wants,old.getUUID());var planned=Workshops.inspect(l,hall.id());h.assertTrue(planned.getString("stage").equals("smelt_raw")&&planned.getInt("withdrawals")==0&&stock.countItem(Items.SAND)==1,"Original job is planned without taking any input");var id=planned.getUUID("id");h.assertTrue(NaturalFurnace.claim(l,hall,planned,old.getUUID()),"Original worker claims its job");var job=Workshops.inspect(l,hall.id());
  var path=ResourceReturnRoute.plan(old,station.offset(1,0,0));h.assertTrue(path!=null&&!path.canReach(),"Owner is really stranded below the workshop");old.getNavigation().moveTo(path,.8);
  h.assertTrue(!available(l,hall,job,helper.getUUID(),2399),"A normal fresh commute keeps its owner");
  Runnable clean=()->{for(var body:bodies)body.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  if(guards){
   h.startSequence().thenWaitUntil(()->h.assertTrue(old.tickCount>0&&helper.tickCount>0&&helper.onGround(),"Guard bodies have physically settled")).thenExecute(()->{
   h.assertTrue(available(l,hall,job,helper.getUUID(),2400),"Present colleague can begin an old untouched job");
   var carried=job.copy();carried.put("carried",new ItemStack(Items.SAND).save(new CompoundTag()));h.assertTrue(!available(l,hall,carried,helper.getUUID(),2400),"Never transfer goods already in hands");
   for(var key:List.of("withdrawals","fuels","output","labor")){var taken=job.copy();taken.putInt(key,1);h.assertTrue(!available(l,hall,taken,helper.getUUID(),2400),"Never transfer a started boundary: "+key);}
   var paid=job.copy();var items=new ListTag();items.add(new ItemStack(Items.SAND).save(new CompoundTag()));paid.put("paid",items);h.assertTrue(!available(l,hall,paid,helper.getUUID(),2400),"Never transfer paid input");
   var furnace=job.copy();furnace.putLong("furnace",base.offset(4,1,4).asLong());h.assertTrue(!available(l,hall,furnace,helper.getUUID(),2400),"Never assume a furnace is untouched");
   helper.moveTo(base.getX()+15.5,121,base.getZ()+.5);h.assertTrue(!available(l,hall,job,helper.getUUID(),2400),"A distant replacement cannot lock the job");helper.moveTo(station.getX()+1.5,station.getY(),station.getZ()+.5);helper.setOnGround(true);
   s.resident(helper.getUUID()).fallIll();h.assertTrue(!available(l,hall,job,helper.getUUID(),2400),"Replacement must be healthy");s.resident(helper.getUUID()).cure();
   old.getNavigation().stop();h.assertTrue(!available(l,hall,job,helper.getUUID(),2400),"Missing path is no proof of a stranded worker");old.getNavigation().moveTo(path,.8);
   var complete=old.routeTo(old.blockPosition(),0);h.assertTrue(complete!=null&&complete.canReach(),"Owner has a real complete local route");old.getNavigation().moveTo(complete,.8);h.assertTrue(!available(l,hall,job,helper.getUUID(),2400),"A complete owner route cannot be taken over");old.getNavigation().moveTo(path,.8);
   helper.moveTo(station.getX()+2.5,station.getY(),station.getZ()+.5);helper.setOnGround(true);var wall=station.offset(1,1,0);l.setBlock(wall,Blocks.STONE.defaultBlockState(),2);h.assertTrue(!available(l,hall,job,helper.getUUID(),2400),"Replacement cannot begin through a wall");l.setBlock(wall,Blocks.AIR.defaultBlockState(),2);helper.moveTo(station.getX()+1.5,station.getY(),station.getZ()+.5);helper.setOnGround(true);
   h.assertTrue(available(l,hall,job,helper.getUUID(),2400),"Untouched ready job is transferable before its real withdrawal");
   var taken=WorldJournal.takeAmount(l,Settlement.childId(id,"smelt/raw/0"),station,0,stock.getItem(0).copy(),1);h.assertTrue(taken.is(Items.SAND)&&stock.countItem(Items.SAND)==0,"A real pre-save withdrawal receipt exists");h.assertTrue(!claim(l,hall,job,helper.getUUID(),2400),"Pre-save receipt preserves original custody");h.assertTrue(Workshops.inspect(l,hall.id()).getUUID("worker").equals(old.getUUID()),"Rejected handoffs do not mutate assignment");clean.run();h.succeed();});return;
  }
  helper.onlyGoals(g->false,6,new WorkshopGoal(helper,true,()->2400+l.getGameTime()));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(old.blockPosition())&&l.isPositionEntityTicking(helper.blockPosition()),"Both physical chunks ready"));
  boolean[] successorObserved={false};h.onEachTick(()->{var current=Workshops.inspect(l,hall.id());if(current.hasUUID("worker")&&current.getUUID("worker").equals(helper.getUUID())&&current.hasUUID("id")&&current.getUUID("id").equals(id))successorObserved[0]=true;});
  h.succeedWhen(()->{var current=Workshops.inspect(l,hall.id());h.assertTrue(stock.countItem(Items.GLASS)==1,"Present worker physically finishes original job: "+current.getString("stage"));h.assertTrue(current.hasUUID("id")&&current.getUUID("id").equals(id)&&successorObserved[0]&&!current.hasUUID("worker")&&current.getString("stage").equals("idle"),"Same job changes owner once and completes");h.assertTrue(stock.countItem(Items.SAND)==0&&stock.countItem(Items.COAL)==0&&old.getHealth()==old.getMaxHealth()&&helper.getHealth()==helper.getMaxHealth(),"Real input and fuel consumed without hurting either body");h.assertTrue(WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/output"))!=null&&WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/deliver"))!=null,"Original output and delivery receipts preserved");h.assertTrue(JobCargo.snapshot(old,true).items().isEmpty(),"Former owner cannot reclaim successor's output");clean.run();h.succeed();});
 }
}
