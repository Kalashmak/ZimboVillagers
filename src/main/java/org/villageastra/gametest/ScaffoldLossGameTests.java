package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.persistence.NbtRecord;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A ledger/goal fixture for the observed missing temporary block, not a complete house-growth proof. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ScaffoldLossGameTests {
 private static CompoundTag op(BlockPos p,BlockState before,BlockState after){var t=new CompoundTag();t.putLong("pos",p.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));return t;}
 private record Loss(net.minecraft.server.level.ServerLevel level,UUID id,BlockPos pos,CompoundTag state){
  UUID removal(){return Settlement.childId(id,"block/1");}
  java.nio.file.Path file(){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-scaffold-loss/"+removal()+".bin");}
 }
 private static Loss loss(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,3,4));var stock=p.east(3);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.AIR.defaultBlockState(),2);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var chest=(net.minecraft.world.Container)l.getBlockEntity(stock);chest.clearContent();chest.setItem(0,new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get().asItem()));var id=UUID.randomUUID();
  h.assertTrue(WorldJournal.takeAmount(l,Settlement.childId(id,"fund/0"),stock,0,chest.getItem(0),1).getCount()==1&&chest.getItem(0).isEmpty(),"Finite stock pays exactly one scaffold");
  var air=Blocks.AIR.defaultBlockState();var scaffold=VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/0"),p,air,scaffold),"Original paid placement commits");
  var a=op(p,air,scaffold);a.putString("item","villageastra:timber_scaffold");a.putBoolean("done",true);var b=op(p,scaffold,air);b.putString("return","villageastra:timber_scaffold");var ops=new ListTag();ops.add(a);ops.add(b);
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("id",id);t.putUUID("project",id);t.putBoolean("funded",true);t.putInt("index",1);t.putInt("progress",1);t.put("ops",ops);t.put("cargo",new ListTag());l.setBlock(p,air,2);return new Loss(l,id,p,t);
 }
 private static int cargo(CompoundTag t){int n=0;for(var raw:t.getList("cargo",Tag.TAG_COMPOUND))n+=ItemStack.of((CompoundTag)raw).getCount();return n;}
 @GameTest(template="empty",batch="scaffold_loss_guards",timeoutTicks=200)
 public static void missingTemporaryRemovalRequiresItsExactPaidInstallationAndEmptyCell(GameTestHelper h){
  var f=loss(h);h.runAtTickTime(4,()->{
   for(int k=0;k<10;k++){
    var t=f.state.copy();var ops=t.getList("ops",Tag.TAG_COMPOUND);var removal=ops.getCompound(1);
    switch(k){case 0->t.putBoolean("funded",false);case 1->t.putBoolean("complete",true);case 2->t.putBoolean("relocate",true);case 3->ops.getCompound(0).putBoolean("done",false);case 4->t.putUUID("id",UUID.randomUUID());case 5->removal.putString("return","minecraft:stone");case 6->removal.putBoolean("dismantle",true);case 7->removal.putString("item","villageastra:timber_scaffold");case 8->removal.put("before",NbtUtils.writeBlockState(Blocks.LADDER.defaultBlockState()));case 9->ops.getCompound(0).putLong("pos",f.pos.east().asLong());}
    var before=t.copy();h.assertTrue(!ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID())&&t.equals(before),"Refused custody case "+k+" remains unchanged");
   }
   for(var block:List.of(Blocks.STONE,Blocks.WATER,VillageAstra.TIMBER_SCAFFOLD.get())){
    f.level.setBlock(f.pos,block.defaultBlockState(),2);var t=f.state.copy();h.assertTrue(!ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID())&&t.equals(f.state),"An occupied/wet/present cell is never treated as a loss");h.assertTrue(f.level.getBlockState(f.pos).getBlock()==block,"Refusal preserves the physical cell");
   }
   f.level.setBlock(f.pos,Blocks.AIR.defaultBlockState(),2);h.assertTrue(!java.nio.file.Files.exists(f.file())&&!WorldJournal.exists(f.level,f.removal()),"Refusals create no loss or mutation receipt");h.succeed();
  });
 }
 @GameTest(template="empty",batch="scaffold_loss_guards",timeoutTicks=200)
 public static void lossRequiresTheOriginalChunkReceiptAndAnUnchangedAccountingIdentity(GameTestHelper h){
  var f=loss(h);h.runAtTickTime(4,()->{
   var cap=org.villageastra.persistence.ChunkReceipts.of(f.level.getChunkAt(f.pos));var original=cap.serializeNBT();var missing=original.copy();var list=new ListTag();var paidId=Settlement.childId(f.id,"block/0");for(var raw:original.getList("applied",Tag.TAG_INT_ARRAY))if(!NbtUtils.loadUUID(raw).equals(paidId))list.add(raw.copy());missing.put("applied",list);cap.deserializeNBT(missing);
   try{var t=f.state.copy();h.assertTrue(!ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID())&&t.equals(f.state)&&!java.nio.file.Files.exists(f.file()),"A committed file alone cannot authorize loss when its original chunk receipt is absent");}finally{cap.deserializeNBT(original);}
   var wrong=new CompoundTag();wrong.putInt("schema",1);wrong.putUUID("id",f.removal());wrong.putUUID("placement",UUID.randomUUID());wrong.putLong("pos",f.pos.asLong());wrong.putString("dimension",f.level.dimension().location().toString());wrong.putString("item","villageastra:timber_scaffold");NbtRecord.write(f.file(),wrong);var t=f.state.copy();boolean refused=false;
   try{ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID());}catch(IllegalStateException ex){refused="Scaffold loss identity changed".equals(ex.getMessage());}
   h.assertTrue(refused&&t.equals(f.state)&&NbtRecord.read(f.file()).equals(wrong)&&!WorldJournal.exists(f.level,f.removal()),"A different accounting identity fails closed without a refund or overwrite");h.succeed();
  });
 }
 @GameTest(template="empty",batch="scaffold_loss_replay",timeoutTicks=200)
 public static void lossReplayAfterAccountingBeforeProjectSaveReturnsNoMaterials(GameTestHelper h){
  var f=loss(h);h.runAtTickTime(4,()->{
   var queue=f.level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/scaffold-loss-fixture/"+f.id+".bin");NbtRecord.write(queue,f.state);var inMemory=f.state.copy();
   h.assertTrue(ScaffoldLoss.finish(f.level,inMemory,1,UUID.randomUUID()),"Verified missing temporary block is accounted");var revision=org.villageastra.persistence.AtomicRecord.revision(f.file());
   // Model the durable window after loss accounting but before the queue save.
   var reloaded=NbtRecord.read(queue);h.assertTrue(ScaffoldLoss.finish(f.level,reloaded,1,UUID.randomUUID()),"Original unfinished queue safely replays its existing loss record");
   h.assertTrue(cargo(inMemory)==0&&cargo(reloaded)==0&&reloaded.getInt("lostScaffolds")==1&&reloaded.getInt("progress")==2,"Loss consumes no new material and returns no material on replay");
   h.assertTrue(org.villageastra.persistence.AtomicRecord.revision(f.file())==revision&&!WorldJournal.exists(f.level,f.removal()),"Replay does not rewrite loss accounting or fabricate a block receipt");NbtRecord.write(queue,reloaded);var committed=NbtRecord.read(queue);
   h.assertTrue(!ScaffoldLoss.finish(f.level,committed,1,UUID.randomUUID())&&committed.equals(reloaded),"Completed accounting is idempotent");h.assertTrue(HallConstructionPlan.read(committed).operations().size()==2,"Historical approved chain remains intact");h.succeed();
  });
 }
 @GameTest(template="empty",batch="scaffold_loss_replay",timeoutTicks=200)
 public static void aGenuinelyRecordedRemovalRecoversExactlyOneReturnedItem(GameTestHelper h){
  var f=loss(h);h.runAtTickTime(4,()->{
   var scaffold=VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();f.level.setBlock(f.pos,scaffold,2);h.assertTrue(WorldJournal.place(f.level,f.removal(),f.pos,scaffold,Blocks.AIR.defaultBlockState()),"Real removal is journalled before its project bookkeeping");
  });
  h.runAtTickTime(6,()->{
   h.assertTrue(WorldJournal.inspectCommitted(f.level,f.removal())!=null,"Real removal committed across the actual server boundary");var t=f.state.copy();h.assertTrue(ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID()),"Committed real removal recovers bookkeeping");
   h.assertTrue(cargo(t)==1&&!t.getList("ops",Tag.TAG_COMPOUND).getCompound(1).getBoolean("lostScaffold")&&!java.nio.file.Files.exists(f.file()),"Only a genuine removal returns exactly its one real scaffold");var before=t.copy();h.assertTrue(!ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID())&&t.equals(before),"Repeated recovery never returns a second item");h.succeed();
  });
 }
 @GameTest(template="empty",batch="scaffold_loss_guards",timeoutTicks=200)
 public static void aDifferentMutationReceiptCannotAuthorizeTemporaryRefundOrLoss(GameTestHelper h){
  var f=loss(h);h.runAtTickTime(4,()->{
   f.level.setBlock(f.pos,Blocks.STONE.defaultBlockState(),2);h.assertTrue(WorldJournal.place(f.level,f.removal(),f.pos,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState()),"Fixture creates a genuinely different journal intent");
  });
  h.runAtTickTime(6,()->{
   var receipt=WorldJournal.inspectCommitted(f.level,f.removal());var t=f.state.copy();h.assertTrue(!ScaffoldLoss.finish(f.level,t,1,UUID.randomUUID())&&t.equals(f.state)&&cargo(t)==0,"Different intent is refused without bookkeeping or materials");h.assertTrue(receipt.equals(WorldJournal.inspectCommitted(f.level,f.removal()))&&!java.nio.file.Files.exists(f.file()),"Foreign receipt is never rewritten");h.succeed();
  });
 }
 @GameTest(template="empty",batch="scaffold_loss",timeoutTicks=200)
 public static void actualBuilderAccountsForMissingPaidScaffoldWithoutReturningAnItem(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(5,3,5));
  for(int x=-4;x<=14;x++)for(int z=-4;z<=14;z++)for(int y=0;y<=6;y++)l.setBlock(base.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));s.addBuilding(new Settlement.Building(home,"home",10,0,10));
  var chestPos=LogisticsRoutes.position(e,hall);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();chest.setItem(0,new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get().asItem()));
  var id=UUID.randomUUID();var target=base.offset(5,1,0);var air=Blocks.AIR.defaultBlockState();var scaffold=VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();
  var material=WorldJournal.takeAmount(l,Settlement.childId(id,"fund/0"),chestPos,0,chest.getItem(0),1);h.assertTrue(material.getCount()==1&&chest.getItem(0).isEmpty(),"One finite scaffold is actually withdrawn");
  h.assertTrue(WorldJournal.place(l,Settlement.childId(id,"block/0"),target,air,scaffold),"Paid scaffold placement has a real committed journal receipt");
  var paid=op(target,air,scaffold);paid.putString("item","villageastra:timber_scaffold");paid.putBoolean("done",true);var removal=op(target,scaffold,air);removal.putString("return","villageastra:timber_scaffold");var ops=new ListTag();ops.add(paid);ops.add(removal);
  // Reproduce only the observed state. The cause of the real missing block is not established.
  l.setBlock(target,air,2);
  var t=new CompoundTag();t.putInt("schema",2);t.putUUID("id",id);t.putUUID("project",id);t.putString("kind","building");t.putString("design","home");t.putLong("origin",base.asLong());t.putLong("hatch",base.asLong());t.putBoolean("noHatch",true);t.putBoolean("funded",true);t.putInt("index",1);t.putInt("progress",1);t.putInt("withdrawals",1);t.put("ops",ops);t.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("villageastra:timber_scaffold",1);t.put("cost",cost);HallUpgradeGoal.enqueue(l,e,t);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,Profession.BUILDER,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(target.getX()-1.5,target.getY(),target.getZ()+.5,0,0);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  var goal=new HallUpgradeGoal(npc,true);
  h.runAtTickTime(4,()->{
   try{
    h.assertTrue(WorldJournal.inspectCommitted(l,Settlement.childId(id,"block/0"))!=null,"Original paid placement has committed before the measured builder decision");
    h.assertTrue(goal.canUse(),"Actual headless builder can start this paid project");goal.start();goal.tick();
    var saved=HallUpgradeGoal.inspect(l,s.id());var finished=saved.getList("ops",Tag.TAG_COMPOUND).getCompound(1);
    h.assertTrue(finished.getBoolean("done")&&finished.getBoolean("lostScaffold"),"Actual builder finishes the already empty temporary removal as a recorded loss: status="+npc.workStatus()+" op="+finished);
    h.assertTrue(chest.countItem(VillageAstra.TIMBER_SCAFFOLD.get().asItem())==0&&saved.getList("cargo",Tag.TAG_COMPOUND).stream().allMatch(raw->ItemStack.of((CompoundTag)raw).isEmpty()),"A missing block never creates a returned scaffold");
    h.assertTrue(HallConstructionPlan.read(saved).operations().size()==2,"Original approved operation chain remains readable");
   }finally{npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
  });
  h.runAtTickTime(6,()->{
   var removalId=Settlement.childId(id,"block/1");var loss=NbtRecord.read(l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-scaffold-loss/"+removalId+".bin"));h.assertTrue(loss.getUUID("id").equals(removalId)&&loss.getUUID("placement").equals(Settlement.childId(id,"block/0"))&&loss.getLong("pos")==target.asLong(),"Loss has a separate signed accounting record");h.assertTrue(!WorldJournal.exists(l,removalId),"No physical removal receipt is fabricated for an absent block");h.succeed();
  });
 }
}
