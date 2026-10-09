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
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineSparePickGameTests {
 @GameTest(template="empty",batch="mine_spare_pick",timeoutTicks=400)
 public static void fullDeliveryRequestsASeparatePaidQuarryPick(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var stock=LogisticsRoutes.chest(t.l,t.e,t.hall());var own=LogisticsRoutes.chest(t.l,t.e,t.shop);stock.clearContent();own.clearContent();
  var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));
  var at=LogisticsRoutes.position(t.e,t.hall());npc.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5);npc.setNoAi(true);
  var op=UUID.randomUUID();stock.setItem(0,new ItemStack(Items.STONE_PICKAXE));stock.setItem(1,new ItemStack(Items.ANDESITE,6));
  var pick=WorldJournal.takeAmount(t.l,Settlement.childId(op,"tool"),at,0,stock.getItem(0).copy(),1);var cargo=new ListTag();cargo.add(WorldJournal.takeAmount(t.l,Settlement.childId(op,"paid/0"),at,1,stock.getItem(1).copy(),6).save(new CompoundTag()));
  var work=new CompoundTag();work.putInt("schema",1);work.putUUID("worker",npc.getUUID());work.putUUID("operation",op);work.putString("stage","deliver");work.putString("status","output_full");work.putInt("delivered",0);work.put("cargo",cargo);work.put("tool",pick.save(new CompoundTag()));MineWork.write(t.l,t.shop,work);
  for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.ANDESITE,64));
  h.startSequence().thenWaitUntil(()->h.assertTrue(t.l.isPositionEntityTicking(npc.blockPosition()),"Body chunk registered")).thenExecute(()->t.l.addFreshEntity(npc)).thenWaitUntil(()->h.assertTrue(t.l.getEntity(npc.getUUID())==npc,"Actual worker registered")).thenExecute(()->{
   try{
    h.assertTrue(NaturalSupplyGoal.miningDeliveryBlocked(t.l,t.e,npc,work),"Actual full own delivery can yield without losing cargo");
    h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.destination().equals(t.hall().id())&&w.matches(new ItemStack(Items.STONE_PICKAXE))),"A pick owned by paused mine cargo is unavailable to a separate quarry job: request a paid spare in the hall");
    h.assertTrue(MineWork.read(t.l,t.shop).equals(work)&&stock.countItem(Items.STONE_PICKAXE)==0,"Demand does not duplicate or release the old paid pick");
    stock.setItem(0,new ItemStack(Items.STONE_PICKAXE));h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().noneMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))),"An available spare prevents repeated orders");stock.clearContent();
    work.putString("status","walking");MineWork.write(t.l,t.shop,work);h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().noneMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))),"Normal deliveries need no spare");
    work.putString("status","output_full");MineWork.write(t.l,t.shop,work);own.setItem(0,new ItemStack(Items.ANDESITE,58));h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().noneMatch(w->w.matches(new ItemStack(Items.STONE_PICKAXE))),"Newly free destination capacity resumes existing work");
   }finally{npc.discard();ResearchV2Town.done(t);}
  }).thenSucceed();
 }
 @GameTest(template="empty",batch="mine_spare_pick_trip",timeoutTicks=24000)
 public static void paidSparePickIsCraftedBeforeMinerHarvestsIron(GameTestHelper h){
  var l=h.getLevel();var anchor=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(anchor.getX()+2490368,120,anchor.getZ());var chunks=PhysicalFixtureChunks.force(l,base,-3,55,-3,16);
  for(var p:BlockPos.betweenClosed(base.offset(-3,-1,-3),base.offset(55,8,16)))l.setBlock(p,p.getY()<=base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);var hut=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(hall);s.addBuilding(hut);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);var output=LogisticsRoutes.position(e,hut);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(output,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var own=LogisticsRoutes.chest(l,e,hut);
  var op=UUID.randomUUID();var cargo=new ListTag();var stone=new ItemStack(Items.ANDESITE,6);chest.setItem(0,stone.copy());
  cargo.add(WorldJournal.takeAmount(l,Settlement.childId(op,"paid/0"),stock,0,stone,6).save(new CompoundTag()));
  chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));var oldPick=WorldJournal.takeAmount(l,Settlement.childId(op,"tool"),stock,0,chest.getItem(0).copy(),1);chest.setItem(0,new ItemStack(Items.COBBLESTONE,3));chest.setItem(1,new ItemStack(Items.STICK,2));chest.setItem(2,new ItemStack(Items.BREAD,64));
  for(int slot=0;slot<own.getContainerSize();slot++)own.setItem(slot,new ItemStack(Items.ANDESITE,64));own.setItem(0,new ItemStack(Items.ANDESITE,60));int storedStone=own.countItem(Items.ANDESITE);
  var ore=base.offset(40,1,2);l.setBlock(ore,Blocks.IRON_ORE.defaultBlockState(),2);h.assertTrue(SurfaceQuarry.safe(l,ore),"Prepared ore lies outside protected structures on dry stable ground");var project=new CompoundTag();var projectId=UUID.randomUUID();project.putUUID("id",projectId);project.putUUID("project",projectId);project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:raw_iron",1);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,hut.id());npc.bind(s.id(),s.resident(r.id()));npc.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition())&&l.isPositionEntityTicking(output),"Native entity chunks ready")).thenExecute(()->l.addFreshEntity(npc));
  var t=new CompoundTag();t.putInt("schema",1);t.putInt("width",3);t.putInt("height",5);t.putInt("descent",7);t.putInt("step",1);t.putBoolean("advanced",true);t.putUUID("worker",npc.getUUID());t.putUUID("operation",op);t.putString("stage","deliver");t.putInt("delivered",0);t.putString("status","output_full");t.put("cargo",cargo);t.put("tool",oldPick.save(new CompoundTag()));NbtRecord.write(MineWork.path(l,hut.id()),t);
  var maker=VillageAstra.RESIDENT.get().create(l);var mr=new Resident(maker.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(mr,home);s.assign(mr.id(),Profession.MAYOR,hall.id());maker.bind(s.id(),s.resident(mr.id()));maker.moveTo(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);maker.setOnGround(true);maker.goalSelector.removeAllGoals(g->true);maker.targetSelector.removeAllGoals(g->true);maker.goalSelector.addGoal(6,new WorkshopGoal(maker,true,()->l.getGameTime()));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(maker.blockPosition()),"Hall body chunk ready")).thenExecute(()->l.addFreshEntity(maker));
  npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->0L));var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(5,supply);boolean[] custody={false},freed={false};CompoundTag[] craft={null},finished={null};
  h.onEachTick(()->{
   var batch=Workshops.inspect(l,hall.id());if(batch.getString("recipe").equals("minecraft:stone_pickaxe")&&batch.getString("stage").equals("work"))craft[0]=batch.copy();h.assertTrue(!npc.isInWaterOrBubble(),"Storage fixture remains dry");if(npc.tickCount%1200==0)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_SPARE_PICK_TRIP tick={} maker={} stockPick={} supply={} oreSafe={} pos={}",npc.tickCount,maker.workStatus(),chest.countItem(Items.STONE_PICKAXE),NaturalSupplyGoal.inspect(l,npc.getUUID()),SurfaceQuarry.safe(l,ore),npc.position());
   if(!freed[0]){
    var held=NbtRecord.read(MineWork.path(l,hut.id()));
    h.assertTrue(held.getUUID("operation").equals(op)&&held.getInt("delivered")==0&&held.getList("cargo",Tag.TAG_COMPOUND).equals(cargo),"Paused stone job remains intact");
    h.assertTrue(!WorldJournal.exists(l,Settlement.childId(op,"delivery/0")),"No failed destination intent strands stone");
    var snapshot=JobCargo.snapshot(npc,true);int wood=0,iron=0,picks=0;
    for(var raw:snapshot.items()){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.ANDESITE))wood+=item.getCount();if(item.is(Items.RAW_IRON))iron+=item.getCount();if(item.is(Items.STONE_PICKAXE))picks+=item.getCount();}
    h.assertTrue(wood==6&&picks>=1&&picks<=2,"Custody retains paused stone and original pick exactly once");if(iron>=1)custody[0]=true;
    if(chest.countItem(Items.RAW_IRON)>=1&&chest.countItem(Items.STONE_PICKAXE)==1&&NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")){finished[0]=NaturalSupplyGoal.inspect(l,npc.getUUID()).copy();h.assertTrue(custody[0],"Actual iron cargo was observed before delivery");npc.goalSelector.removeGoal(supply);own.setItem(0,new ItemStack(Items.ANDESITE,58));freed[0]=true;}
   }
  });
  h.succeedWhen(()->{
   h.assertTrue(freed[0]&&WorldJournal.inspectCommitted(l,Settlement.childId(op,"delivery/0"))!=null&&own.countItem(Items.ANDESITE)==storedStone+4,"Crafted spare and real iron trip and resumed stone delivery finish: ticks="+npc.tickCount+" pos="+npc.position()+" status="+npc.workStatus()+" iron="+chest.countItem(Items.RAW_IRON)+" mine="+NbtRecord.read(MineWork.path(l,hut.id()))+" natural="+NaturalSupplyGoal.inspect(l,npc.getUUID()));
   h.assertTrue(craft[0]!=null,"Actual paid stone-pick craft was observed before its output");int paidStone=0,paidSticks=0;for(var raw:craft[0].getList("paid",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.COBBLESTONE))paidStone+=item.getCount();if(item.is(Items.STICK))paidSticks+=item.getCount();}h.assertTrue(paidStone==3&&paidSticks==2,"Spare consumes the real recipe materials, regardless of later quarry stone");var receipt=WorldJournal.inspectCommitted(l,finished[0].getUUID("id"));h.assertTrue(receipt!=null&&receipt.getString("kind").equals("block")&&finished[0].getCompound("before").getString("Name").equals("minecraft:iron_ore")&&l.getBlockState(BlockPos.of(finished[0].getLong("target"))).is(Blocks.AIR)&&chest.countItem(Items.STONE_PICKAXE)==1&&MineWork.read(l,hut).getCompound("tool").equals(oldPick.save(new CompoundTag())),"Actual journal-confirmed ore, returned spare and original pick remain conserved; finish the entire natural load before resuming old work");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_MINE_SPARE_PICK VERIFIED bodyTicks={} andesite=6 ironAtLeast=1 paidStone=3 paidSticks=2 custody=true",npc.tickCount);
   npc.discard();maker.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,chunks);h.succeed();
  });
 }
}
