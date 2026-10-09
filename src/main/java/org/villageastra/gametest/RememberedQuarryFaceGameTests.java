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

/** Remembered iron requires a short physical, paid opening after a previous trip. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RememberedQuarryFaceGameTests {
 @GameTest(template="empty",batch="remembered_quarry_face",timeoutTicks=3600)
 public static void completedIronDepositPreparesPaidFaceBeforeDistantSurvey(GameTestHelper h){exercise(h,false,false);}
 @GameTest(template="empty",batch="remembered_quarry_face",timeoutTicks=3600)
 public static void unrelatedSandTripRetainsPaidAccessToHiddenIron(GameTestHelper h){exercise(h,false,true);}
 @GameTest(template="empty",batch="remembered_quarry_face",timeoutTicks=500)
 public static void floodedCompletedIronCannotStartFace(GameTestHelper h){exercise(h,true,false);}
 @GameTest(template="empty",batch="remembered_quarry_face",timeoutTicks=500)
 public static void floodedSharedIronCannotStartFace(GameTestHelper h){exercise(h,true,true);}
 private static void exercise(GameTestHelper h,boolean flooded,boolean shared){
  boolean deposit=true;
  var rock=Blocks.IRON_ORE;var product=Items.RAW_IRON;int already=1;

  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+524288+(flooded?65536:0)+(shared?1048576:0),120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+53)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=36;x++)for(int z=-6;z<=6;z++)for(int y=-3;y<=6;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var ore=base.offset(25,1,0);for(var p:BlockPos.betweenClosed(ore.offset(-1,0,-1),ore.offset(1,2,1)))l.setBlock(p,Blocks.STONE.defaultBlockState(),2);l.setBlock(ore,rock.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",-40,0,-40);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(3);chest.setItem(0,pick);chest.setItem(1,new ItemStack(Items.COBBLESTONE,3));
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);
  work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:raw_iron",already+1);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var old=new CompoundTag();var previous=UUID.randomUUID();old.putUUID("id",previous);old.putBoolean("complete",true);old.putBoolean("quarry",true);old.putString("stage","carry");old.putLong("oreTarget",ore.asLong());old.putInt("faceDepth",3);old.putInt("surveyCursor",385*385-1);
  if(deposit){var previousBlock=ore.west(4);l.setBlock(previousBlock,rock.defaultBlockState(),2);WorldJournal.harvest(l,previous,previousBlock,rock.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));chest.setItem(2,new ItemStack(product));old.remove("oreTarget");old.remove("faceDepth");old.putLong("target",previousBlock.asLong());old.put("before",NbtUtils.writeBlockState(rock.defaultBlockState()));}
  if(shared){QuarryKnowledge.clear(l.getServer());QuarryKnowledge.remember(l.getServer(),WorldJournal.recoverExisting(l,previous));old.putUUID("id",UUID.randomUUID());old.putBoolean("quarry",false);old.putLong("target",base.offset(-4,1,-4).asLong());old.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));}
  NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),old);
  if(flooded)l.setBlock(ore.east(),Blocks.WATER.defaultBlockState(),2);
  var supply=new NaturalSupplyGoal(npc,true);
  if(flooded){
   h.assertTrue(!supply.canUse(),"An unsafe remembered face cannot start a new trip");
   Runnable check=()->{
    if(!shared)h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean(deposit?"quarryResumeChecked":"faceResumeChecked"),"The finite unsafe deposit check finishes without stranding the ordinary survey");
    h.assertTrue(l.getBlockState(ore).is(rock)&&chest.countItem(product)==already&&chest.getItem(0).getDamageValue()==3&&chest.countItem(Items.COBBLESTONE)==3,"No free harvest, borrowed tool, wear or replayed cargo");
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   };
   if(deposit&&!shared){h.startSequence().thenWaitUntil(()->{
    h.assertTrue(!supply.canUse(),"A yielded flooded deposit still cannot allocate a trip");
    h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("quarryResumeChecked"),"Resumable sensing completes under its ordinary per-window budget");
   }).thenExecute(check).thenSucceed();}else{check.run();h.succeed();}return;
  }
  h.assertTrue(HarvestAccess.find(npc,ore,NaturalSupplyGoal.ROUTE_RANGE)==null,"Hidden ore has no direct extraction platform");
  h.assertTrue(QuarryFace.find(npc,ore,Set.of())!=null,"A safe short face is physically reachable");
  h.startSequence().thenWaitUntil(()->h.assertTrue(supply.canUse(),"Remembered hidden ore must prepare its paid face before the distant survey"))
   .thenExecute(()->{

  var selected=NaturalSupplyGoal.inspect(l,npc.getUUID());var job=selected.getUUID("id");
  h.assertTrue(!job.equals(previous)&&selected.getLong("oreTarget")==ore.asLong()&&selected.getInt("faceDepth")==1&&l.getBlockState(BlockPos.of(selected.getLong("target"))).is(Blocks.STONE)&&selected.getString("stage").equals("tool"),"Resume starts a distinct, unpaid job, without replaying old cargo");
  h.assertTrue(chest.countItem(product)==already&&chest.getItem(0).getDamageValue()==3&&l.getBlockState(ore).is(rock),"Sensing neither borrows nor mines");
  npc.goalSelector.addGoal(1,supply);
  }).thenWaitUntil(()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")&&chest.countItem(product)==already+1,"Actual new trip must mine and deliver one raw iron"))
   .thenExecute(()->{
    var finished=NaturalSupplyGoal.inspect(l,npc.getUUID());
    h.assertTrue(finished.getInt("labor")==200&&l.getBlockState(ore).isAir(),"Exactly 200 actual labor ticks and physical ore removal");
    h.assertTrue(chest.countItem(Items.COBBLESTONE)>3&&chest.countItem(Items.COBBLESTONE)<=6&&chest.countItem(Items.STONE_PICKAXE)==1,"Old cargo is not replayed and the new loan returns once");
    ItemStack returned=ItemStack.EMPTY;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.STONE_PICKAXE))returned=chest.getItem(slot);
    h.assertTrue(returned.getDamageValue()==4+chest.countItem(Items.COBBLESTONE)-3&&WorldJournal.inspectCommitted(l,finished.getUUID("id"))!=null,"The fresh loan pays one wear per obstruction and ore, with an actual harvest receipt");
    com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_REMEMBERED_FACE VERIFIED shared={} labor={} wear={} obstructionStone={} rawIron=1",shared,finished.getInt("labor"),returned.getDamageValue(),chest.countItem(Items.COBBLESTONE)-3);
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);
   }).thenSucceed();
 }
}
