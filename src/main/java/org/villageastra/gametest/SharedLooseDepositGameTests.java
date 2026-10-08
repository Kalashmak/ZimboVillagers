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

/** Sharing a committed lead never transfers old cargo or grants a remote harvest. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class SharedLooseDepositGameTests {
 @GameTest(template="empty",batch="shared_loose_deposit",timeoutTicks=2400)
 public static void newWorkerUsesFarmersFormerSandDiscoveryAndPaysOwnLabor(GameTestHelper h){exercise(h,0);}
 @GameTest(template="empty",batch="shared_loose_deposit",timeoutTicks=600)
 public static void jobMetadataWithoutCommittedHarvestIsNotShared(GameTestHelper h){exercise(h,1);}
 @GameTest(template="empty",batch="shared_loose_deposit",timeoutTicks=600)
 public static void anotherVillagesDiscoveryIsNotShared(GameTestHelper h){exercise(h,2);}
 @GameTest(template="empty",batch="shared_loose_deposit",timeoutTicks=600)
 public static void floodedColleagueDepositRemainsUntouched(GameTestHelper h){exercise(h,3);}
 @GameTest(template="empty",batch="shared_loose_deposit",timeoutTicks=600)
 public static void currentDemandOverridesColleaguesFormerSandJob(GameTestHelper h){exercise(h,4);}
 private static void exercise(GameTestHelper h,int mode){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+1179648+mode*65536,120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  int depositDistance=25;
  for(int x=(base.getX()-18)>>4;x<=(base.getX()+53)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+20)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-6;x<=depositDistance+11;x++)for(int z=-6;z<=6;z++)for(int y=-3;y<=6;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var ore=base.offset(depositDistance,1,0);l.setBlock(ore,Blocks.SAND.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",-40,0,-40);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=LogisticsRoutes.chest(l,e,hall);var pick=new ItemStack(Items.STONE_PICKAXE);pick.setDamageValue(3);chest.setItem(0,pick);chest.setItem(1,new ItemStack(Items.COBBLESTONE,3));
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);
  work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:sand",1);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);

  var colleague=UUID.randomUUID();if(mode!=2){var farmer=new Resident(colleague,Resident.Life.ADULT,true,null,null,-1);s.admit(farmer,home);var farm=new Settlement.Building(UUID.randomUUID(),"farm",-50,0,40);s.addBuilding(farm);s.assign(colleague,Profession.FARMER,farm.id());h.assertTrue(s.resident(colleague).profession()==Profession.FARMER,"Former gatherer now works as farmer");}
  var previous=UUID.randomUUID();var oldRecord=new CompoundTag();oldRecord.putUUID("id",previous);oldRecord.putBoolean("complete",true);oldRecord.putString("stage","carry");oldRecord.putLong("target",ore.west().asLong());oldRecord.put("before",NbtUtils.writeBlockState(Blocks.SAND.defaultBlockState()));oldRecord.putBoolean("quarry",false);
  l.setBlock(ore.west(),Blocks.SAND.defaultBlockState(),2);
  if(mode!=1)WorldJournal.harvest(l,previous,ore.west(),Blocks.SAND.defaultBlockState(),ItemStack.EMPTY);
  NbtRecord.write(NaturalSupplyGoal.path(l,colleague),oldRecord);
  var own=new CompoundTag();own.putInt("surveyRadius",320);own.putInt("surveyCursor",323000);own.putInt("looseSurveyCursor",323000);own.putInt("plantSurveyCursor",323000);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),own);
  if(mode==3)l.setBlock(ore.east(),Blocks.WATER.defaultBlockState(),2);
  if(mode==4){cost.remove("minecraft:sand");cost.putInt("minecraft:clay_ball",1);project.put("cost",cost);HallUpgradeGoal.store(l,s.id(),project);}
  var supply=new NaturalSupplyGoal(npc,true);var job=new UUID[1];
  if(mode!=0){npc.goalSelector.addGoal(1,supply);h.runAtTickTime(200,()->{
   h.assertTrue(!NaturalSupplyGoal.active(NaturalSupplyGoal.inspect(l,npc.getUUID()))&&chest.countItem(Items.SAND)==0&&l.getBlockState(ore).is(Blocks.SAND),"Unverified or foreign metadata cannot trigger remote work or cargo");
   h.assertTrue(NaturalSupplyGoal.inspect(l,colleague).equals(oldRecord),"Other worker metadata unchanged");
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();});return;}
  h.startSequence().thenWaitUntil(()->h.assertTrue(supply.canUse(),"Known colleague deposit must be found before distant survey"))
   .thenExecute(()->{var selected=NaturalSupplyGoal.inspect(l,npc.getUUID());job[0]=selected.getUUID("id");h.assertTrue(!job[0].equals(previous)&&BlockPos.of(selected.getLong("target")).equals(ore)&&selected.getString("stage").equals("dig"),"Start distinct unpaid job at remaining block");h.assertTrue(selected.getInt("surveyCursor")==323000&&chest.countItem(Items.SAND)==0&&l.getBlockState(ore).is(Blocks.SAND),"No full scan, replay or remote harvest");npc.goalSelector.addGoal(1,supply);})
   .thenWaitUntil(()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).getBoolean("complete")&&chest.countItem(Items.SAND)==1,"Native body harvests and delivers one remaining sand"))
   .thenExecute(()->{var finished=NaturalSupplyGoal.inspect(l,npc.getUUID());h.assertTrue(finished.getInt("labor")==200&&l.getBlockState(ore).isAir()&&WorldJournal.inspectCommitted(l,job[0])!=null,"Ordinary labor and committed physical harvest");h.assertTrue(NaturalSupplyGoal.inspect(l,colleague).equals(oldRecord)&&chest.countItem(Items.COBBLESTONE)==3&&chest.getItem(0).getDamageValue()==3,"Colleague state and tool stock unchanged");com.mojang.logging.LogUtils.getLogger().info("SHARED_LOOSE_PASS worker={} old={} new={} labor={} sand={}",npc.getUUID(),previous,job[0],finished.getInt("labor"),chest.countItem(Items.SAND));npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);}).thenSucceed();
 }
}
