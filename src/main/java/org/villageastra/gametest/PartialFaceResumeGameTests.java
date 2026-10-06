package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Prepared completed face metadata; only the new loan, physical trip and delivery are exercised. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PartialFaceResumeGameTests {
 @GameTest(template="empty",batch="partial_face_resume",timeoutTicks=2400)
 public static void returnedMinerRevisitsKnownOreBeforeTheDistantSurveyCursor(GameTestHelper h){
  exercise(h,false);
 }
 @GameTest(template="empty",batch="partial_face_resume",timeoutTicks=200)
 public static void floodedRememberedFaceDoesNotBorrowOrMine(GameTestHelper h){exercise(h,true);}
 @GameTest(template="empty",batch="quarry_deposit_resume",timeoutTicks=2400)
 public static void completedStoneDepositStartsFreshPaidTripBeforeDistantSurvey(GameTestHelper h){exercise(h,false,true);}
 @GameTest(template="empty",batch="quarry_deposit_resume",timeoutTicks=400)
 public static void floodedCompletedStoneDepositNeverReplaysCargo(GameTestHelper h){exercise(h,true,true);}
 private static void exercise(GameTestHelper h,boolean flooded){exercise(h,flooded,false);}
 private static void exercise(GameTestHelper h,boolean flooded,boolean deposit){
  var rock=deposit?Blocks.ANDESITE:Blocks.COAL_ORE;var product=deposit?Items.ANDESITE:Items.COAL;int already=deposit?1:0;

  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+524288+(flooded?65536:0),120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+53)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-3;y<=6;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var ore=base.offset(25,1,0);l.setBlock(ore,rock.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",-40,0,-40);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(3);chest.setItem(0,pick);chest.setItem(1,new ItemStack(Items.COBBLESTONE,3));
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);
  work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt(deposit?"minecraft:andesite":"minecraft:coal",already+1);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var old=new CompoundTag();var previous=UUID.randomUUID();old.putUUID("id",previous);old.putBoolean("complete",true);old.putBoolean("quarry",true);old.putString("stage","carry");old.putLong("oreTarget",ore.asLong());old.putInt("faceDepth",3);old.putInt("surveyCursor",385*385-1);
  if(deposit){var previousBlock=ore.west();l.setBlock(previousBlock,rock.defaultBlockState(),2);WorldJournal.harvest(l,previous,previousBlock,rock.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));chest.setItem(2,new ItemStack(product));old.remove("oreTarget");old.remove("faceDepth");old.putLong("target",previousBlock.asLong());old.put("before",NbtUtils.writeBlockState(rock.defaultBlockState()));}
  NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),old);
  if(flooded)l.setBlock(ore.east(),Blocks.WATER.defaultBlockState(),2);
  var supply=new NaturalSupplyGoal(npc,true);
  if(flooded){
   h.assertTrue(!supply.canUse(),"An unsafe remembered face cannot start a new trip");
   Runnable check=()->{
    h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean(deposit?"quarryResumeChecked":"faceResumeChecked"),"The finite unsafe deposit check finishes without stranding the ordinary survey");
    h.assertTrue(l.getBlockState(ore).is(rock)&&chest.countItem(product)==already&&chest.getItem(0).getDamageValue()==3&&chest.countItem(Items.COBBLESTONE)==3,"No free harvest, borrowed tool, wear or replayed cargo");
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   };
   if(deposit){h.startSequence().thenWaitUntil(()->{
    h.assertTrue(!supply.canUse(),"A yielded flooded deposit still cannot allocate a trip");
    h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("quarryResumeChecked"),"Resumable sensing completes under its ordinary per-window budget");
   }).thenExecute(check).thenSucceed();}else{check.run();h.succeed();}return;
  }
  h.assertTrue(supply.canUse(),"Known unfinished ore must be considered before the distant saved survey cursor");
  var selected=NaturalSupplyGoal.inspect(l,npc.getUUID());var job=selected.getUUID("id");
  h.assertTrue(!job.equals(previous)&&BlockPos.of(selected.getLong("target")).equals(ore)&&selected.getString("stage").equals("tool"),"Resume starts a distinct, unpaid job, without replaying old cargo");
  h.assertTrue(chest.countItem(product)==already&&chest.getItem(0).getDamageValue()==3&&l.getBlockState(ore).is(rock),"Sensing neither borrows nor mines");
  npc.goalSelector.addGoal(1,supply);
  h.startSequence().thenWaitUntil(()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")&&chest.countItem(product)==already+1,"Actual new trip must mine and deliver one coal"))
   .thenExecute(()->{
    var finished=NaturalSupplyGoal.inspect(l,npc.getUUID());
    h.assertTrue(finished.getInt("labor")==200&&l.getBlockState(ore).isAir(),"Exactly 200 actual labor ticks and physical ore removal");
    h.assertTrue(chest.countItem(Items.COBBLESTONE)==3&&chest.countItem(Items.STONE_PICKAXE)==1,"Old cargo is not replayed and the new loan returns once");
    ItemStack returned=ItemStack.EMPTY;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.STONE_PICKAXE))returned=chest.getItem(slot);
    h.assertTrue(returned.getDamageValue()==4&&WorldJournal.recoverExisting(l,job)!=null,"The fresh loan pays one wear and has an actual harvest receipt");
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_PARTIAL_FACE VERIFIED old={} new={} labor={} wear=3->4 coal=1",previous,job,finished.getInt("labor"));
    if(deposit)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_DEPOSIT_RETURN VERIFIED bodyTicks={} oldAndesite=1 newAndesite=1 paidLabor=200 wear=3->4",npc.tickCount);
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   }).thenSucceed();
 }
}
