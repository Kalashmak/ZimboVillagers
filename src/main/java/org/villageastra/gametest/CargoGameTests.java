package org.villageastra.gametest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
import org.villageastra.persistence.*;
import java.nio.file.*;
import java.util.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CargoGameTests {
 private record Fixture(ServerLevel level,BlockPos origin,Settlement village,ResidentEntity worker,Container stock,UUID building,Path file){}
 private static Fixture fixture(GameTestHelper h){
  var level=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var village=StarterVillage.create(level,origin);
  var resident=village.residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();var worker=(ResidentEntity)level.getEntity(resident.id());
  // Synchronous custody fixture, like ForestFixture: its spawned chunk may not expose entities yet.
  // Drive the same identity directly; these tests verify transactions, not entity loading/navigation.
  if(worker==null){worker=VillageAstra.RESIDENT.get().create(level);worker.bind(village.id(),resident);worker.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()+4.5);}
  worker.setNoAi(true);
  var building=village.workplace(resident.id()).id();var file=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+building+".bin");
  return new Fixture(level,origin,village,worker,(Container)level.getBlockEntity(origin.offset(1,1,4)),building,file);
 }
 private static int slot(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return i;throw new IllegalStateException("Missing fixture item");}
 private static CompoundTag held(Fixture f,boolean journalAhead){
  var id=UUID.randomUUID();var tool=WorldJournal.take(f.level,UUID.randomUUID(),f.origin.offset(1,1,4),slot(f.stock,Items.STONE_PICKAXE),new ItemStack(Items.STONE_PICKAXE));
  var target=f.origin.offset(34,3,19);f.level.setBlock(target,Blocks.STONE.defaultBlockState(),3);var loot=WorldJournal.harvest(f.level,id,target,Blocks.STONE.defaultBlockState(),tool);
  var s=new CompoundTag();s.putInt("schema",1);s.putInt("width",3);s.putInt("height",4);s.putUUID("worker",f.worker.getUUID());s.putUUID("operation",id);s.putString("stage",journalAhead?"dig":"deliver");
  if(!journalAhead)tool.setDamageValue(1);s.put("tool",tool.save(new CompoundTag()));var cargo=new ListTag();if(!journalAhead)for(var item:loot)cargo.add(item.save(new CompoundTag()));s.put("cargo",cargo);NbtRecord.write(f.file,s);return s;
 }
 private static Container deathContainer(Fixture f){var t=CargoCustody.inspect(f.level.getServer(),f.worker.getUUID());return (Container)f.level.getBlockEntity(BlockPos.of(t.getList("drops",Tag.TAG_COMPOUND).getCompound(0).getLong("pos")));}
 @GameTest(template="empty",timeoutTicks=200) public static void deathReconcilesHarvestAndNeverRefillsLoot(GameTestHelper h){
  var f=fixture(h);held(f,true);f.worker.hurt(f.level.damageSources().genericKill(),1000);CargoCustody.tick(f.level.getServer());
  var pile=deathContainer(f);h.assertTrue(pile.countItem(Items.COBBLESTONE)==1&&pile.countItem(Items.STONE_PICKAXE)==1,"Exactly the existing tool and harvested block remain in the world");
  h.assertTrue(pile.getItem(slot(pile,Items.STONE_PICKAXE)).getDamageValue()==1,"Pending harvest consumes tool durability once");
  h.assertTrue(f.stock.countItem(Items.STONE_PICKAXE)==0&&!f.village.resident(f.worker.getUUID()).alive(),"No refund and resident is dead");
  var reset=NbtRecord.read(f.file);h.assertTrue(reset.getInt("cell")==1&&!reset.hasUUID("worker")&&ItemStack.of(reset.getCompound("tool")).isEmpty(),"Successor receives next cell and no ghost tool");
  pile.clearContent();CargoCustody.onDeath(f.worker);CargoCustody.recover(f.level.getServer());h.assertTrue(pile.isEmpty(),"Repeated death/recovery never refills looted cargo");
  f.worker.discard();var stale=VillageAstra.RESIDENT.get().create(f.level);stale.setUUID(f.worker.getUUID());h.assertTrue(!f.level.addFreshEntity(stale),"Durable death rejects a stale saved entity");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void reassignmentReturnsCargoPhysicallyAndPausesWithoutPlayers(GameTestHelper h){
  var f=fixture(h);held(f,false);f.village.assign(f.worker.getUUID(),Profession.PORTER,Settlement.childId(f.village.id(),"building/town_hall"));
  f.worker.moveTo(f.origin.getX()+26.5,f.origin.getY()+1,f.origin.getZ()+18.5);h.assertTrue(CargoCustody.beginReturn(f.worker),"Old role creates a return order");
  CargoCustody.returnStep(f.worker,true);h.assertTrue(f.stock.countItem(Items.COBBLESTONE)==64&&f.stock.countItem(Items.STONE_PICKAXE)==0,"Cargo cannot teleport from workplace to stock");
  h.assertTrue(NbtRecord.read(f.file).getUUID("worker").equals(f.worker.getUUID()),"Source remains bound while return is pending");
  f.worker.moveTo(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+4.5);CargoCustody.returnStep(f.worker,false);h.assertTrue(f.stock.countItem(Items.STONE_PICKAXE)==0,"Inactive simulation performs no return");
  for(int i=0;i<5;i++)CargoCustody.returnStep(f.worker,true);
  h.assertTrue(f.stock.countItem(Items.STONE_PICKAXE)==1&&f.stock.countItem(Items.COBBLESTONE)==65,"Exact physical cargo returned");
  h.assertTrue(!CargoCustody.pending(f.level.getServer(),f.worker.getUUID())&&!NbtRecord.read(f.file).hasUUID("worker"),"Only completed return releases the workplace");
  CargoCustody.returnStep(f.worker,true);h.assertTrue(f.stock.countItem(Items.COBBLESTONE)==65,"Return replay cannot duplicate resources");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void deathDuringReturnDoesNotDropAlreadyDepositedTool(GameTestHelper h){
  var f=fixture(h);held(f,false);f.village.assign(f.worker.getUUID(),Profession.PORTER,Settlement.childId(f.village.id(),"building/town_hall"));CargoCustody.beginReturn(f.worker);
  var t=CargoCustody.inspect(f.level.getServer(),f.worker.getUUID());var first=ItemStack.of(t.getList("items",Tag.TAG_COMPOUND).getCompound(0));
  WorldJournal.deposit(f.level,Settlement.childId(t.getUUID("id"),"return/0"),f.origin.offset(1,1,4),first); // Receipt ahead of custody checkpoint.
  f.worker.hurt(f.level.damageSources().genericKill(),1000);CargoCustody.tick(f.level.getServer());var pile=deathContainer(f);
  h.assertTrue(pile.countItem(Items.STONE_PICKAXE)==0&&pile.countItem(Items.COBBLESTONE)==1&&f.stock.countItem(Items.STONE_PICKAXE)==1,"Deposited tool and undelivered ore have one owner each");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void hallDeathKeepsWorkButLosesItsFunding(GameTestHelper h){
  var f=fixture(h);var resident=f.village.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();var worker=(ResidentEntity)f.level.getEntity(resident.id());
  var entry=new org.villageastra.server.SettlementData.Entry(f.village,f.level.dimension().location().toString(),f.origin);HallUpgradeGoal.request(f.level,entry);
  var file=f.level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+f.village.id()+".bin");var state=NbtRecord.read(file);state.putUUID("worker",worker.getUUID());NbtRecord.write(file,state);
  f.stock.setItem(10,new ItemStack(Items.SPRUCE_PLANKS,3));WorldJournal.takeAmount(f.level,Settlement.childId(state.getUUID("id"),"fund/0"),f.origin.offset(1,1,4),10,new ItemStack(Items.SPRUCE_PLANKS,3),3);
  worker.hurt(f.level.damageSources().genericKill(),1000);CargoCustody.tick(f.level.getServer());var t=CargoCustody.inspect(f.level.getServer(),worker.getUUID());var pos=BlockPos.of(t.getList("drops",Tag.TAG_COMPOUND).getCompound(0).getLong("pos"));var pile=(Container)f.level.getBlockEntity(pos);
  var reset=NbtRecord.read(file);h.assertTrue(pile.countItem(Items.SPRUCE_PLANKS)==3&&f.stock.countItem(Items.SPRUCE_PLANKS)==0,"Funding becomes finite death cargo, not a refund");
  h.assertTrue(!reset.getBoolean("funded")&&reset.getList("cargo",Tag.TAG_COMPOUND).isEmpty()&&!reset.getUUID("id").equals(state.getUUID("id")),"Remaining project requires fresh physical funding and receipts");
  h.assertTrue(reset.getList("ops",Tag.TAG_COMPOUND).equals(state.getList("ops",Tag.TAG_COMPOUND)),"Construction geometry remains queued");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void completedBlockReceiptCannotBecomeDeathLoot(GameTestHelper h){
  var f=fixture(h);var resident=f.village.residents().stream().filter(r->r.profession()==Profession.BUILDER).findFirst().orElseThrow();var worker=(ResidentEntity)f.level.getEntity(resident.id());
  var source=f.origin.offset(1,1,4);var target=f.origin.offset(8,1,8);var job=new BlockWork(f.level.dimension().location().toString(),source,target,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState());worker.blockWork(job);
  worker.moveTo(source.getX()+1.5,source.getY(),source.getZ()+.5);job.step(worker,true);h.assertTrue(job.carried().getCount()==1,"Existing paid builder cargo");
  WorldJournal.place(f.level,Settlement.childId(job.id(),"place"),target,Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState());
  worker.hurt(f.level.damageSources().genericKill(),1000);CargoCustody.tick(f.level.getServer());var custody=CargoCustody.inspect(f.level.getServer(),worker.getUUID());
  h.assertTrue(custody.getList("items",Tag.TAG_COMPOUND).isEmpty()&&custody.getBoolean("complete"),"Placed material never drops from stale entity checkpoint");
  h.assertTrue(f.stock.countItem(Items.COBBLESTONE)==63&&f.level.getBlockState(target).is(Blocks.COBBLESTONE),"One debit and one actual block remain");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aNewJobCannotStartBeforeOldCargoReturn(GameTestHelper h){
  var f=fixture(h);held(f,false);f.village.assign(f.worker.getUUID(),Profession.PORTER,Settlement.childId(f.village.id(),"building/town_hall"));
  h.assertTrue(!CargoCustody.mayStartWork(f.worker)&&CargoCustody.pending(f.level.getServer(),f.worker.getUUID()),"Work admission itself seals old cargo without waiting for the return goal tick");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void cargoDeathInAnotherDimensionKeepsItsActualLocation(GameTestHelper h){
  var f=fixture(h);held(f,true);var nether=f.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(nether!=null,"Nether exists in test server");nether.getChunk(0,0);
  f.worker.discard();var actor=VillageAstra.RESIDENT.get().create(nether);actor.bind(f.village.id(),f.village.resident(f.worker.getUUID()));actor.moveTo(.5,200,.5);actor.setNoAi(true);nether.addFreshEntity(actor);
  f.village.assign(actor.getUUID(),Profession.PORTER,Settlement.childId(f.village.id(),"building/town_hall"));CargoCustody.beginReturn(actor);CargoCustody.returnStep(actor,true);
  h.assertTrue(f.stock.countItem(Items.STONE_PICKAXE)==0,"Cross-dimensional return never teleports inventory");
  actor.hurt(nether.damageSources().genericKill(),1000);CargoCustody.tick(nether.getServer());var custody=CargoCustody.inspect(nether.getServer(),actor.getUUID());
  var pos=BlockPos.of(custody.getList("drops",Tag.TAG_COMPOUND).getCompound(0).getLong("pos"));var pile=(Container)nether.getBlockEntity(pos);
  h.assertTrue(pile!=null&&pile.countItem(Items.STONE_PICKAXE)==1&&pile.countItem(Items.COBBLESTONE)==1&&pos.getY()==200,"Cargo is where the worker died; source receipts reconciled in Overworld");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aPartlyInstalledBeamKeepsOnlyUnplacedTimberInCargo(GameTestHelper h){
  var f=fixture(h);var state=held(f,false);WorldJournal.deposit(f.level,Settlement.childId(state.getUUID("operation"),"delivery/0"),f.origin.offset(33,1,16),new ItemStack(Items.COBBLESTONE));
  var id=UUID.randomUUID();WorldJournal.takeAmount(f.level,Settlement.childId(id,"timber/0"),f.origin.offset(1,1,4),slot(f.stock,Items.OAK_LOG),f.stock.getItem(slot(f.stock,Items.OAK_LOG)).copy(),3);
  state.putUUID("operation",id);state.putString("stage","support_place");state.putInt("step",4);state.putInt("cell",0);state.putInt("support_fetched",3);state.remove("cargo");NbtRecord.write(f.file,state);
  var beam=f.origin.offset(34,0,22);WorldJournal.place(f.level,Settlement.childId(id,"beam/0"),beam,Blocks.AIR.defaultBlockState(),Blocks.OAK_LOG.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X));
  f.worker.hurt(f.level.damageSources().genericKill(),1000);CargoCustody.tick(f.level.getServer());var pile=deathContainer(f);var reset=NbtRecord.read(f.file);
  h.assertTrue(f.stock.countItem(Items.OAK_LOG)==29&&pile.countItem(Items.OAK_LOG)==2&&f.level.getBlockState(beam).is(Blocks.OAK_LOG),"Three paid logs have exactly one physical location each");
  h.assertTrue(reset.getBoolean("resumeSupport")&&reset.getInt("support_placed")==1&&!reset.contains("support_fetched"),"Replacement must obtain two fresh logs, preserving the first beam piece");
  h.assertTrue(f.village.mineAreas().get(f.building).lastStep()==3,"The committed beam retains the underground claim");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void cargoContainerProvidesNoFreeChestItem(GameTestHelper h){
  var f=fixture(h);var pos=f.origin.offset(8,1,8);var contents=new ListTag();contents.add(new ItemStack(Items.IRON_INGOT,2).save(new CompoundTag()));var id=UUID.randomUUID();
  h.assertTrue(WorldJournal.dropCargo(f.level,id,pos,f.village.id(),contents),"Physical cargo placed");
  h.assertTrue(net.minecraft.world.level.block.Block.getDrops(f.level.getBlockState(pos),f.level,pos,f.level.getBlockEntity(pos)).isEmpty(),"Container has no item loot; only existing contents can fall");
  var c=(Container)f.level.getBlockEntity(pos);c.clearContent();f.level.removeBlock(pos,false);
  h.assertTrue(WorldJournal.dropCargo(f.level,id,pos,f.village.id(),contents)&&f.level.getBlockState(pos).isAir(),"Destroyed or looted cargo is not recreated by receipt replay");h.succeed();
 }
}
