package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** What a worker who drops out leaves behind: the cargo it really carried and a reset that asks only for what is still to do. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class JobCargoGameTests {
 private static Path root(ServerLevel l){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);}
 private static ResidentEntity worker(ServerLevel l,Settlement s,Profession role){return (ResidentEntity)l.getEntity(s.residents().stream().filter(r->r.profession()==role).findFirst().orElseThrow().id());}
 private static CompoundTag op(String item,boolean done){var t=new CompoundTag();t.putString("item",item);if(done)t.putBoolean("done",true);return t;}
 private static CompoundTag project(UUID id,UUID worker,ListTag ops){var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",id);t.putUUID("project",id);t.putUUID("worker",worker);t.put("ops",ops);t.put("cost",new CompoundTag());t.put("cargo",new ListTag());return t;}
 private static int count(ListTag items,Item item){return items.stream().map(t->ItemStack.of((CompoundTag)t)).filter(i->i.is(item)).mapToInt(ItemStack::getCount).sum();}
 private static void done(ServerLevel l,Settlement s){
  HallUpgradeGoal.drop(l,s.id());
  for(var b:s.buildings())try{Files.deleteIfExists(root(l).resolve("data/astra-work/"+b.id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  for(var r:s.residents()){var npc=l.getEntity(r.id());if(npc!=null)npc.discard();}
  SettlementData.get(l.getServer()).remove(s.id());
 }
 /** Review #2: blocks of a field-ordered building placed out of turn are done, and a replacement builder is not funded for them again. */
 @GameTest(template="empty",timeoutTicks=200) public static void aReplacementBuilderPaysOnlyForBlocksStillToPlace(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var builder=worker(l,s,Profession.BUILDER);var target=origin.offset(8,1,8);var before=l.getBlockState(target);
  try{
   // Operations 0 and 2 were placed out of turn; operation 1 is placed too, but the record fell behind its block receipt.
   var id=UUID.randomUUID();var ops=new ListTag();
   ops.add(op("minecraft:oak_planks",true));ops.add(op("minecraft:oak_planks",false));ops.add(op("minecraft:cobblestone",true));ops.add(op("minecraft:cobblestone",false));ops.add(op("minecraft:oak_planks",false));
   var state=project(id,builder.getUUID(),ops);state.putBoolean("funded",true);
   var cargo=new ListTag();cargo.add(new ItemStack(Items.OAK_PLANKS,2).save(new CompoundTag()));cargo.add(new ItemStack(Items.COBBLESTONE,1).save(new CompoundTag()));state.put("cargo",cargo);
   NbtRecord.write(root(l).resolve("data/astra-upgrades/"+s.id()+".bin"),state);
   h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/1"),target,before,net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()),"Operation 1 has its block receipt");
   var snap=JobCargo.snapshot(builder,true);
   h.assertTrue(snap.jobs().size()==1&&snap.jobs().getCompound(0).getString("kind").equals("hall"),"The project is the builder's only job");
   var reset=snap.jobs().getCompound(0).getCompound("reset");var cost=reset.getCompound("cost");
   h.assertTrue(reset.getInt("index")==2,"The pointer moves over the done operation and the one with a receipt: "+reset.getInt("index"));
   h.assertTrue(cost.size()==2&&cost.getInt("minecraft:oak_planks")==1&&cost.getInt("minecraft:cobblestone")==1,"Only operations 3 and 4 are funded again: "+cost);
   h.assertTrue(count(snap.items(),Items.OAK_PLANKS)==1&&count(snap.items(),Items.COBBLESTONE)==1,"The carried material, less the plank operation 1 used, is the cargo: "+snap.items());
   h.assertTrue(HallConstructionPlan.projectId(reset).equals(id)&&!reset.getUUID("id").equals(id)&&!reset.hasUUID("worker")&&!reset.getBoolean("funded")&&reset.getList("ops",Tag.TAG_COMPOUND).equals(ops),"The same project waits for fresh funding, its done operations kept");
  }finally{l.setBlock(target,before,3);done(l,s);}
  h.succeed();
 }
 /** Review #10: a held reset is written back only over its own unfinished project, never over one called off, replaced or finished meanwhile. */
 @GameTest(template="empty",timeoutTicks=200) public static void aHeldResetIsWrittenOnlyOverItsOwnProject(GameTestHelper h){
  var l=h.getLevel();var s=StarterVillage.create(l,h.absolutePos(new BlockPos(2,3,2)));var builder=worker(l,s,Profession.BUILDER);var file=root(l).resolve("data/astra-upgrades/"+s.id()+".bin");
  try{
   var id=UUID.randomUUID();var ops=new ListTag();ops.add(op("minecraft:oak_planks",false));var held=project(id,builder.getUUID(),ops);NbtRecord.write(file,held);
   var jobs=JobCargo.snapshot(builder,true).jobs();h.assertTrue(jobs.size()==1&&jobs.getCompound(0).getString("kind").equals("hall"),"The builder holds the project");
   // AD-078: the mayor called it off and ordered something else while the cargo was out.
   var other=project(UUID.randomUUID(),UUID.randomUUID(),ops.copy());NbtRecord.write(file,other);JobCargo.release(l,builder.getUUID(),jobs);
   h.assertTrue(NbtRecord.read(file).equals(other),"Another project in the slot is left as it is");
   var finished=held.copy();finished.putBoolean("complete",true);NbtRecord.write(file,finished);JobCargo.release(l,builder.getUUID(),jobs);
   h.assertTrue(NbtRecord.read(file).equals(finished),"A finished project is not reopened");
   HallUpgradeGoal.drop(l,s.id());JobCargo.release(l,builder.getUUID(),jobs);
   h.assertTrue(!Files.exists(file),"A dropped project does not come back");
   NbtRecord.write(file,held);JobCargo.release(l,builder.getUUID(),jobs);var reset=NbtRecord.read(file);
   h.assertTrue(HallConstructionPlan.projectId(reset).equals(id)&&!reset.getUUID("id").equals(id)&&!reset.hasUUID("worker"),"Its own project gets the reset");
  }finally{done(l,s);}
  h.succeed();
 }
 /** Review #7: a forester who drops out on the way to plant carries the sapling its take receipt holds, not an oak one. */
 @GameTest(template="empty",timeoutTicks=200) public static void aForesterCarriesTheSaplingItTook(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var forester=worker(l,s,Profession.FORESTER);
  var stock=origin.offset(1,1,4);var chest=(Container)l.getBlockEntity(stock);var file=root(l).resolve("data/astra-work/"+s.workplace(forester.getUUID()).id()+".bin");
  try{
   chest.setItem(20,new ItemStack(Items.BIRCH_SAPLING,3));var id=UUID.randomUUID();
   h.assertTrue(WorldJournal.take(l,id,stock,20,chest.getItem(20).copy()).is(Items.BIRCH_SAPLING),"The forester took a birch sapling");
   // AD-131: a sapling for a bare foot is taken under the operation's own take (sapling) into his load.
   var op=UUID.randomUUID();h.assertTrue(WorldJournal.take(l,Settlement.childId(op,"sapling"),stock,20,chest.getItem(20).copy()).is(Items.BIRCH_SAPLING),"And one more for a bare foot");
   var state=new CompoundTag();state.putInt("schema",2);state.putUUID("worker",forester.getUUID());state.putUUID("operation",op);state.putString("stage","sapling");state.putString("species","minecraft:birch_sapling");state.putLong("target",origin.offset(20,1,20).asLong());NbtRecord.write(file,state);
   var snap=JobCargo.snapshot(forester,true);
   h.assertTrue(count(snap.items(),Items.BIRCH_SAPLING)==1&&count(snap.items(),Items.OAK_SAPLING)==0&&chest.countItem(Items.BIRCH_SAPLING)==1,"The sapling his take holds is the one carried, debited once: "+snap.items());
   h.assertTrue(snap.jobs().size()==1,"The hut is the forester's only job");
   var reset=snap.jobs().getCompound(0).getCompound("reset");
   h.assertTrue(reset.getString("stage").equals("tool")&&!reset.contains("species")&&!reset.hasUUID("worker"),"The successor starts over and takes a sapling of its own");
  }finally{chest.setItem(20,ItemStack.EMPTY);done(l,s);}
  h.succeed();
 }
}
