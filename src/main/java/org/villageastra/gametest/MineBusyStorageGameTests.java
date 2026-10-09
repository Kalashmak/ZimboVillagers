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
public final class MineBusyStorageGameTests {
 @GameTest(template="empty",batch="mine_busy_storage",timeoutTicks=6000)
 public static void blockedStoneWaitsSafelyWhileMinerSuppliesClay(GameTestHelper h){
  var l=h.getLevel();var anchor=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(anchor.getX()+2228224,120,anchor.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-3,55,-3,16);
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(55,8,16)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var hut=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(hall);s.addBuilding(hut);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);var output=LogisticsRoutes.position(e,hut);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(output,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var own=LogisticsRoutes.chest(l,e,hut);
  var op=UUID.randomUUID();var cargo=new ListTag();var stone=new ItemStack(Items.ANDESITE,6);chest.setItem(0,stone.copy());
  cargo.add(WorldJournal.takeAmount(l,Settlement.childId(op,"paid/0"),stock,0,stone,6).save(new CompoundTag()));
  for(int slot=0;slot<chest.getContainerSize()-2;slot++)chest.setItem(slot,new ItemStack(Items.DIRT,64));chest.setItem(0,new ItemStack(Items.ANDESITE,61));
  for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.ANDESITE,64));own.setItem(0,new ItemStack(Items.ANDESITE,60));int storedStone=own.countItem(Items.ANDESITE);
  var clay=base.offset(40,0,2);l.setBlock(clay,Blocks.CLAY.defaultBlockState(),2);var project=new CompoundTag();var projectId=UUID.randomUUID();project.putUUID("id",projectId);project.putUUID("project",projectId);project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:clay_ball",4);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,hut.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition())&&l.isPositionEntityTicking(output),"Native entity chunks ready")).thenExecute(()->l.addFreshEntity(npc));
  var t=new CompoundTag();t.putInt("schema",1);t.putInt("width",3);t.putInt("height",5);t.putInt("descent",7);t.putInt("step",1);t.putBoolean("advanced",true);t.putUUID("worker",npc.getUUID());t.putUUID("operation",op);t.putString("stage","deliver");t.putInt("delivered",0);t.putString("status","output_full");t.put("cargo",cargo);t.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));NbtRecord.write(MineWork.path(l,hut.id()),t);
  npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->0L));var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,supply);boolean[] custody={false},freed={false};
  h.onEachTick(()->{
   h.assertTrue(!npc.isInWaterOrBubble(),"Storage fixture remains dry");
   if(!freed[0]){
    var held=NbtRecord.read(MineWork.path(l,hut.id()));
    h.assertTrue(held.getUUID("operation").equals(op)&&held.getInt("delivered")==0&&held.getList("cargo",Tag.TAG_COMPOUND).equals(cargo),"Paused stone job remains intact");
    h.assertTrue(!WorldJournal.exists(l,Settlement.childId(op,"delivery/0")),"No failed destination intent strands stone");
    var snapshot=JobCargo.snapshot(npc,true);int wood=0,balls=0,picks=0;
    for(var raw:snapshot.items()){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.ANDESITE))wood+=item.getCount();if(item.is(Items.CLAY_BALL))balls+=item.getCount();if(item.is(Items.STONE_PICKAXE))picks+=item.getCount();}
    h.assertTrue(wood==6&&picks==1,"Custody retains paused stone and original pick exactly once");if(balls==4)custody[0]=true;
    if(chest.countItem(Items.CLAY_BALL)==4){h.assertTrue(custody[0],"Actual clay cargo was observed before delivery");npc.goalSelector.removeGoal(supply);own.setItem(0,new ItemStack(Items.ANDESITE,58));freed[0]=true;}
   }
  });
  h.succeedWhen(()->{
   h.assertTrue(freed[0]&&WorldJournal.inspectCommitted(l,Settlement.childId(op,"delivery/0"))!=null&&own.countItem(Items.ANDESITE)==storedStone+4,"Real clay trip and resumed stone delivery finish: ticks="+npc.tickCount+" pos="+npc.position()+" status="+npc.workStatus()+" clay="+chest.countItem(Items.CLAY_BALL)+" mine="+NbtRecord.read(MineWork.path(l,hut.id()))+" natural="+NaturalSupplyGoal.inspect(l,npc.getUUID()));
   h.assertTrue(chest.countItem(Items.ANDESITE)==61&&chest.countItem(Items.CLAY_BALL)==4,"Pantry stone reserve and gathered clay conserved");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_MINE_BUSY_STORAGE VERIFIED bodyTicks={} andesite=6 clay=4 custody=true",npc.tickCount);
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
 @GameTest(template="empty",batch="mine_storage_guards",timeoutTicks=200)
 public static void onlyAFullUncommittedOwnDeliveryMayYield(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");ResidentEntity npc=null;
  try{
   var own=LogisticsRoutes.chest(t.l,t.e,t.shop);own.clearContent();
   npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);
   t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));
   var stock=LogisticsRoutes.position(t.e,t.hall());npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);
   var work=new CompoundTag();work.putInt("schema",1);work.putUUID("worker",npc.getUUID());work.putUUID("operation",UUID.randomUUID());work.putString("stage","deliver");work.putString("status","output_full");
   var cargo=new ListTag();cargo.add(new ItemStack(Items.ANDESITE,6).save(new CompoundTag()));work.put("cargo",cargo);MineWork.write(t.l,t.shop,work);
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"A stale full status must not abandon a delivery when its whole stack now fits");
   for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.ANDESITE,64));
   h.assertTrue(!NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&SurfaceQuarry.mayStart(t.l,t.e,npc),"An actually full loaded mine permits another needed resource trip while retaining its old load");
   int before=own.countItem(Items.ANDESITE);work.putString("status","walking");MineWork.write(t.l,t.shop,work);
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"An ordinary in-flight delivery still finishes first");
   work.putString("status","output_full");work.putUUID("worker",UUID.randomUUID());MineWork.write(t.l,t.shop,work);
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"Another worker's loaded job cannot authorize this miner's exception");
   work.putUUID("worker",npc.getUUID());work.putInt("delivered",1);MineWork.write(t.l,t.shop,work);
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"No remaining cargo means there is no blocked stack to pause");
   work.putInt("delivered",0);work.put("mineOre",new CompoundTag());MineWork.write(t.l,t.shop,work);
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"An active paid ore task must finish before yielding");
   work.remove("mineOre");MineWork.write(t.l,t.shop,work);own.setItem(0,new ItemStack(Items.ANDESITE,58));
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"Exactly enough newly freed capacity resumes the original whole-stack delivery");
   var receipt=Settlement.childId(work.getUUID("operation"),"delivery/0");
   h.assertTrue(WorldJournal.deposit(t.l,receipt,LogisticsRoutes.position(t.e,t.shop),new ItemStack(Items.ANDESITE,6)),"A real delivery receipt fills that slot");
   h.assertTrue(NaturalSupplyGoal.primaryResourcePending(t.l,t.e,npc)&&!SurfaceQuarry.mayStart(t.l,t.e,npc),"A recorded delivery belongs to its original destination until the mining job reconciles");
   h.assertTrue(own.countItem(Items.ANDESITE)==before,"All guard checks conserve the actual container contents and never replay the committed deposit");
  }finally{if(npc!=null)npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
