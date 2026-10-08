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
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RemoteFurnaceClaimGameTests {
 @GameTest(template="empty",batch="remote_furnace_claim",timeoutTicks=1800)
 public static void strandedMayorCannotReserveANewFurnaceOrderBeforeThePresentWorkerArrives(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="remote_furnace_claim",timeoutTicks=600)
 public static void aWallBlocksOwnershipOfAnUntouchedOrderEvenWithinWorkingDistance(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean blocked){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+1769472+(blocked?65536:0),120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,48,-8,8);
  for(int x=-4;x<=48;x++)for(int z=-8;z<=8;z++)for(int y=-10;y<=4;y++){boolean hollow=x>=24&&x<=40&&Math.abs(z)<=3;l.setBlock(base.offset(x,y,z),(y<=(hollow?-9:0)?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var station=Workshops.station(e,hall);l.setBlock(station,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.SAND));stock.setItem(1,new ItemStack(Items.COAL));l.setBlock(base.offset(4,1,4),Blocks.FURNACE.defaultBlockState(),2);
  var bodies=new ArrayList<ResidentEntity>();for(int i=0;i<2;i++){var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),i==0?Profession.MAYOR:Profession.BUILDER,hall.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(i==0&&!blocked?base.getX()+32.5:station.getX()+(blocked?2.5:1.5),i==0&&!blocked?112:station.getY(),i==0&&!blocked?base.getZ()+.5:station.getZ()+.5);npc.setOnGround(true);npc.onlyGoals(g->false,0,new net.minecraft.world.entity.ai.goal.Goal(){public boolean canUse(){return false;}});bodies.add(npc);}
  var remote=bodies.get(0);var helper=bodies.get(1);Workshops.advance(l,e,hall,0,List.of(new Workshops.Want(Ingredient.of(Items.GLASS),1,hall.id())));var planned=Workshops.inspect(l,hall.id());var id=planned.getUUID("id");h.assertTrue(planned.getString("stage").equals("smelt_raw")&&!planned.hasUUID("worker"),"A real new order has not been claimed or funded");
  boolean[] done={false},helperOwned={false};Runnable clean=()->{done[0]=true;for(var body:bodies)body.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);};
  if(blocked){
   var wall=station.offset(1,1,0);l.setBlock(wall,Blocks.STONE.defaultBlockState(),2);var goal=new WorkshopGoal(remote,true,l::getGameTime);
   h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(remote.blockPosition()),"Blocked work chunk ready")).thenExecute(()->l.addFreshEntity(remote)).thenWaitUntil(()->h.assertTrue(remote.tickCount>0&&remote.onGround(),"Body actually stands beside the wall")).thenExecute(()->{
    goal.tick();var current=Workshops.inspect(l,hall.id());boolean unowned=!current.hasUUID("worker");h.assertTrue(stock.countItem(Items.SAND)==1&&stock.countItem(Items.COAL)==1&&!WorldJournal.exists(l,Settlement.childId(id,"smelt/raw/0")),"Wall preserves untouched stock and receipts");clean.run();h.assertTrue(unowned,"Blocked sight cannot claim an order before arrival");h.succeed();});return;
  }
  remote.onlyGoals(g->false,6,new WorkshopGoal(remote,true,l::getGameTime));helper.onlyGoals(g->false,6,new WorkshopGoal(helper,true,l::getGameTime));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(remote.blockPosition())&&l.isPositionEntityTicking(helper.blockPosition()),"Both work chunks ready")).thenExecute(()->l.addFreshEntity(remote)).thenWaitUntil(()->h.assertTrue(remote.tickCount>=40,"Remote worker attempts its normal commute first")).thenExecute(()->{
   var current=Workshops.inspect(l,hall.id());if(current.hasUUID("worker")){var owner=current.getUUID("worker");clean.run();throw new GameTestAssertException("Remote body locked a new order without reaching stock: "+owner);}
   h.assertTrue(l.addFreshEntity(helper),"Present colleague joins through normal entity registration");});
  h.onEachTick(()->{if(done[0])return;var current=Workshops.inspect(l,hall.id());if(current.hasUUID("worker")&&current.getUUID("worker").equals(helper.getUUID()))helperOwned[0]=true;});
  h.succeedWhen(()->{h.assertTrue(!done[0]&&stock.countItem(Items.GLASS)==1,"Present colleague must finish the original order without relief timeout");var current=Workshops.inspect(l,hall.id());h.assertTrue(helperOwned[0]&&current.hasUUID("id")&&current.getUUID("id").equals(id)&&current.getString("stage").equals("idle")&&!current.hasUUID("worker"),"Original job completes with normal released custody");h.assertTrue(stock.countItem(Items.SAND)==0&&stock.countItem(Items.COAL)==0&&remote.getHealth()==remote.getMaxHealth()&&helper.getHealth()==helper.getMaxHealth(),"Actual input and fuel are used without damage");h.assertTrue(WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/output"))!=null&&WorldJournal.recoverExisting(l,Settlement.childId(id,"smelt/deliver"))!=null&&JobCargo.snapshot(remote,true).items().isEmpty(),"Same receipts and no former-owner output replay");clean.run();h.succeed();});
 }
}
