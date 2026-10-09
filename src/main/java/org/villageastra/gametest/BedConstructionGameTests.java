package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BedConstructionGameTests {
 private static CompoundTag op(BlockPos p,BlockState before,BlockState after){var t=new CompoundTag();t.putLong("pos",p.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));return t;}
 @GameTest(template="empty",batch="construction_beds",timeoutTicks=200)
 public static void bedsFollowAllStructureAndAreNeverIndependentHelperWork(GameTestHelper h){
  var l=h.getLevel();var base=h.absolutePos(new BlockPos(4,3,4));
  for(int x=-1;x<17;x++)for(int z=-1;z<17;z++)for(int y=-3;y<=20;y++)l.setBlock(base.offset(x,y,z),(y<=0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",36,0,26));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  try{
   var survey=BuildingOrders.survey(l,e,"home",0,base);h.assertTrue(survey.ok(),"Supported home survey succeeds: "+survey.reason());
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);int firstBed=ops.size(),lastOther=-1,beds=0;
   for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));if(step.after().getBlock() instanceof BedBlock){firstBed=Math.min(firstBed,i);beds++;h.assertTrue(!HallUpgradeGoal.helpable(l,ops.getCompound(i),10000),"A helper cannot bring a bed half ahead of structure");}else lastOther=i;}
   h.assertTrue(beds==4&&firstBed>lastOther,"Both complete beds follow every structural, access, door and sign operation");
   h.assertTrue(survey.state().getCompound("cost").getInt("minecraft:white_bed")==2,"Four halves still cost exactly two beds");
   var staged=new ListTag();var plannedBed=ops.getCompound(firstBed).copy();plannedBed.putInt("phase",2);staged.add(plannedBed);var work=op(base.above(),Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState());work.putInt("phase",2);staged.add(work);var later=work.copy();later.putInt("phase",3);staged.add(later);
   h.assertTrue(!BedConstruction.ready(l,staged,plannedBed),"Bed waits for unfinished work in its own phase");work.putBoolean("done",true);
   h.assertTrue(BedConstruction.ready(l,staged,plannedBed),"A later relocation demolition phase cannot deadlock the new bed");
   var foot=base.offset(0,1,-1);var head=foot.south();var a=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);var id=UUID.randomUUID();
   // This suspected support-only trigger did not reproduce the real loss.
   l.setBlock(head.below(),Blocks.AIR.defaultBlockState(),2);WorldJournal.place(l,Settlement.childId(id,"early-foot"),foot,Blocks.AIR.defaultBlockState(),a.setValue(BedBlock.PART,BedPart.FOOT));
   l.setBlock(head.below(),Blocks.STONE.defaultBlockState(),3);WorldJournal.place(l,Settlement.childId(id,"late-head"),head,Blocks.AIR.defaultBlockState(),a.setValue(BedBlock.PART,BedPart.HEAD));
   h.assertTrue(l.getBlockState(foot).equals(a.setValue(BedBlock.PART,BedPart.FOOT))&&l.getBlockState(head).equals(a.setValue(BedBlock.PART,BedPart.HEAD)),"Support-only update does not reproduce loss; both native halves remain");
  }finally{SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="construction_bed_replacement",timeoutTicks=1800)
 public static void finishedQueueBuysAndPhysicallyInstallsMissingHalfBeforeRegistering(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+196608,150,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-28,8,-9,8);
  for(int x=-28;x<=8;x++)for(int z=-9;z<=8;z++)for(int y=0;y<=7;y++)l.setBlock(base.offset(x,y,z),(y==0?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(-24,0,-7));SettlementData.get(l.getServer()).add(e);
  var chestPos=LogisticsRoutes.position(e,hall);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.clearContent();
  var foot=base.above();var head=foot.south();var a=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);var ops=new ListTag();
  var lower=op(foot,Blocks.AIR.defaultBlockState(),a.setValue(BedBlock.PART,BedPart.FOOT));lower.putString("item","minecraft:white_bed");lower.putBoolean("done",true);ops.add(lower);var upper=op(head,Blocks.AIR.defaultBlockState(),a.setValue(BedBlock.PART,BedPart.HEAD));upper.putBoolean("done",true);ops.add(upper);
  l.setBlock(head,a.setValue(BedBlock.PART,BedPart.HEAD),2);
  var state=new CompoundTag();var id=UUID.randomUUID();state.putUUID("id",id);state.putUUID("project",id);state.putInt("schema",2);state.putString("kind","building");state.putString("design","home");state.putLong("origin",base.asLong());state.putLong("hatch",base.offset(-100,0,-100).asLong());state.putBoolean("noHatch",true);state.putBoolean("funded",true);state.putInt("index",2);state.putInt("progress",2);state.put("ops",ops);state.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("minecraft:white_bed",1);state.put("cost",cost);
  var unready=state.copy();unready.putBoolean("funded",false);h.assertTrue(!BedConstruction.queuePaidMissingHalf(l,unready),"Unfunded work cannot queue replacement again");unready=state.copy();unready.putBoolean("complete",true);h.assertTrue(!BedConstruction.queuePaidMissingHalf(l,unready),"Completed buildings retain the normal repair system");unready=state.copy();unready.putBoolean("relocate",true);h.assertTrue(!BedConstruction.queuePaidMissingHalf(l,unready),"Relocation retains its own completion recovery");
  l.setBlock(head,Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.HEAD),2);h.assertTrue(!BedConstruction.queuePaidMissingHalf(l,state.copy()),"A different bed cannot authorize a replacement");l.setBlock(head,a.setValue(BedBlock.PART,BedPart.HEAD),2);
  l.setBlock(foot,Blocks.STONE.defaultBlockState(),2);h.assertTrue(!BedConstruction.queuePaidMissingHalf(l,state.copy())&&l.getBlockState(foot).is(Blocks.STONE),"A changed occupied cell is preserved");l.setBlock(foot,Blocks.AIR.defaultBlockState(),2);
  // The occupied-cell guard legitimately updates the native partner. Restore the
  // original missing-foot/present-head fixture before starting the real builder.
  l.setBlock(head,a.setValue(BedBlock.PART,BedPart.HEAD),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
  h.assertTrue(l.getBlockState(head).equals(a.setValue(BedBlock.PART,BedPart.HEAD)),"Remaining head is restored as the original fixture input");
  h.assertTrue(state.getList("ops",Tag.TAG_COMPOUND).size()==2&&l.getBlockState(foot).isAir(),"Rejected checks neither mutate the live queue nor place blocks");HallUpgradeGoal.enqueue(l,e,state);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),r);npc.moveTo(base.getX()+2.5,base.getY()+1,base.getZ()+.5,0,0);npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Builder chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  boolean[] supplied={false};
  h.onEachTick(()->{
   var saved=HallUpgradeGoal.inspect(l,s.id());
   if(!supplied[0]&&npc.tickCount>=100){
    h.assertTrue(!saved.getBoolean("funded")&&!saved.getBoolean("complete")&&saved.getList("ops",Tag.TAG_COMPOUND).size()==3,"Completed old queue must request one replacement operation");
    h.assertTrue(l.getBlockState(foot).isAir()&&stock.isEmpty(),"Empty stock cannot restore a paid half for free");
    h.assertTrue(saved.getCompound("cost").getInt("minecraft:white_bed")==1&&saved.getCompound("initialCost").getInt("minecraft:white_bed")==1&&saved.getInt("index")==2,"Original paid queue and its cost are retained; replacement needs another actual bed");
    stock.setItem(0,new ItemStack(Items.WHITE_BED));supplied[0]=true;
   }
   if(saved.getBoolean("complete")){
    h.assertTrue(supplied[0]&&stock.isEmpty()&&saved.getInt("withdrawals")==1,"Exactly one real replacement bed is withdrawn");
    h.assertTrue(l.getBlockState(foot).equals(a.setValue(BedBlock.PART,BedPart.FOOT))&&l.getBlockState(head).equals(a.setValue(BedBlock.PART,BedPart.HEAD)),"Both actual halves exist before registration");
    h.assertTrue(saved.getInt("progress")==3&&saved.getInt("index")==3&&WorldJournal.exists(l,Settlement.childId(id,"block/2")),"Replacement has a new durable operation; original indices remain done");
    h.assertTrue(s.buildings().stream().anyMatch(b->b.id().equals(BuildingOrders.buildingId(saved))),"Completed house is registered after actual repair");
    npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);h.succeed();
   }
  });
  h.runAtTickTime(1600,()->h.assertTrue(false,"Builder did not finish paid bed repair: "+npc.position()+" "+npc.workStatus()));
 }
}
