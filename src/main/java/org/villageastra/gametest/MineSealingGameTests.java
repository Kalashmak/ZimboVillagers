package org.villageastra.gametest;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineSealingGameTests {
 private static BlockPos face(ResearchV2Town.Town t){return prepare(t,t.e.center().offset(65,0,0));}
 private static BlockPos prepare(ResearchV2Town.Town t,BlockPos p){
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){t.l.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)t.l.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  t.l.setBlock(p,Blocks.STONE.defaultBlockState(),2);t.l.setBlock(p.east(),Blocks.WATER.defaultBlockState(),2);return p;
 }
 private static ResidentEntity worker(ResearchV2Town.Town t){var npc=VillageAstra.RESIDENT.get().create(t.l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);return npc;}
 private static void at(ResidentEntity npc,BlockPos p){npc.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);npc.setOnGround(true);}
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void normalMiningSelectsWaterSealingBeforeDigging(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var npc=worker(t);
  try{
   var state=MineWork.read(t.l,t.shop);state.putInt("descent",0);state.putInt("width",1);state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
   var next=MineWork.next(t.l,t.e,t.shop,state);var p=prepare(t,MineWork.at(t.e,t.shop,next.cell()));
   var cargo=new ListTag();cargo.add(new ItemStack(Items.DIRT).save(new CompoundTag()));state.put("cargo",cargo);MineWork.write(t.l,t.shop,state);
   at(npc,p.south());var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"A normal miner resumes this drive");goal.tick();
   var selected=MineWork.read(t.l,t.shop);h.assertTrue(MineSealing.active(selected)&&selected.getString("stage").equals("seal_place"),"The production goal chooses its carried plug before trying to excavate beside water");
   h.assertTrue(t.l.getBlockState(p).is(Blocks.STONE)&&t.l.getBlockState(p.east()).is(Blocks.WATER),"Choosing an operation changes no blocks or cargo");
   goal.stop();h.assertTrue(new ResourceWorkGoal(npc,true,()->6000L).canUse(),"The real goal reloads seal_place from disk after interruption");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void sealingRequiresTheActualWorkLanding(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var npc=worker(t);var p=face(t);var access=p.south();
  try{
   var state=MineWork.read(t.l,t.shop);state.putInt("descent",0);
   var local=BuildingPlacement.local(t.e,t.shop,access);state.putIntArray("access",new int[]{local.getX(),local.getY(),local.getZ()});
   var cargo=new ListTag();cargo.add(new ItemStack(Items.DIRT).save(new CompoundTag()));state.put("cargo",cargo);
   h.assertTrue(MineSealing.begin(t.l,t.shop,state,p),"Select a paid plug");MineWork.write(t.l,t.shop,state);
   at(npc,access.south());var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Resume placement");goal.tick();
   h.assertTrue(!MineWork.read(t.l,t.shop).contains("sealReady")&&npc.getMoveControl().hasWanted(),"Inside the old arrival radius the miner still takes the final stride instead of stopping");
   at(npc,access);goal.tick();h.assertTrue(MineWork.read(t.l,t.shop).contains("sealReady"),"Work starts only on the actual landing");
   goal.stop();state=MineWork.read(t.l,t.shop);state.remove("sealReady");MineWork.write(t.l,t.shop,state);
   t.l.setBlock(access,Blocks.COBBLESTONE_STAIRS.defaultBlockState(),2);at(npc,access.above());
   var resumed=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(resumed.canUse(),"Resume on a finished stair");resumed.tick();
   h.assertTrue(MineWork.read(t.l,t.shop).contains("sealReady"),"A stair's top is the landing, not its occupied block interior");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void waterAppearingDuringApproachStopsTheStrike(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var npc=worker(t);var p=face(t);
  try{
   var state=MineWork.read(t.l,t.shop);state.putString("stage","dig");state.putLong("target",p.asLong());state.put("before",NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));state.putInt("labor",1000);MineWork.write(t.l,t.shop,state);
   at(npc,p.south());var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Previously selected mining work loads");goal.tick();
   var stopped=MineWork.read(t.l,t.shop);h.assertTrue(stopped.getString("stage").equals("choose")&&stopped.getString("status").equals("fluid_boundary")&&t.l.getBlockState(p).is(Blocks.STONE),"New neighboring water cancels the strike and re-plans the face");
   h.assertTrue(ItemStack.of(stopped.getCompound("tool")).getDamageValue()==0&&stopped.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Cancelled strike neither spends tool durability nor invents stone");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void sealFetchPlacementAndCrashCustodyArePaid(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var npc=worker(t);var p=face(t);var stock=LogisticsRoutes.position(t.e,t.hall());var chest=LogisticsRoutes.chest(t.l,t.e,t.hall());chest.clearContent();var state=MineWork.read(t.l,t.shop);state.putUUID("worker",npc.getUUID());
  try{
   h.assertTrue(MineSealing.begin(t.l,t.shop,state,p),"A dry stone face beside pure water can be sealed");MineWork.write(t.l,t.shop,state);
   h.assertTrue(new ResourceWorkGoal(npc,true).canUse(),"The real goal reloads seal_fetch from disk");
   h.assertTrue(WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.matches(new ItemStack(Items.DIRT))),"The missing plug is a real supply request");
   var preTake=state.copy();var prePlace=new AtomicReference<CompoundTag>();Runnable save=()->{MineWork.write(t.l,t.shop,state);if(state.contains("sealItem")&&!state.getBoolean("sealPlaced"))prePlace.set(state.copy());};
   java.util.function.LongConsumer tick=now->MineSealing.tick(t.l,t.shop,state,npc,stock,stock.east(),p.south(),q->npc.distanceToSqr(q.getX()+.5,q.getY(),q.getZ()+.5)<1,save,x->{},now);
   at(npc,stock.east());tick.accept(0);h.assertTrue(t.l.getBlockState(p.east()).is(Blocks.WATER)&&state.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"No free plug when stock is empty");
   chest.setItem(0,new ItemStack(Items.DIRT));tick.accept(1);
   h.assertTrue(chest.countItem(Items.DIRT)==0&&state.getList("cargo",Tag.TAG_COMPOUND).size()==1,"A real dirt block leaves the stock");
   MineSealing.reconcile(t.l,preTake);MineSealing.reconcile(t.l,preTake);h.assertTrue(preTake.getList("cargo",Tag.TAG_COMPOUND).size()==1,"Take recovery never doubles the parcel");
   MineWork.write(t.l,t.shop,preTake);h.assertTrue(JobCargo.snapshot(npc,true).items().stream().map(x->ItemStack.of((CompoundTag)x)).filter(x->x.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum()==1,"Death before placement returns the withdrawn dirt");save.run();
   at(npc,p.south());tick.accept(2);h.assertTrue(t.l.getBlockState(p.east()).is(Blocks.WATER),"Physical work takes time");tick.accept(42);
   h.assertTrue(t.l.getBlockState(p.east()).is(Blocks.DIRT)&&t.l.getBlockState(p).is(Blocks.STONE)&&state.getString("stage").equals("choose"),"Paid plug leaves the dry face ready for normal mining");
   var crash=prePlace.get();h.assertTrue(crash!=null,"Captured the real pre-placement checkpoint");MineWork.write(t.l,t.shop,crash);
   var dropped=JobCargo.snapshot(npc,true);h.assertTrue(dropped.items().stream().map(x->ItemStack.of((CompoundTag)x)).noneMatch(x->x.is(Items.DIRT)),"A placed plug is not duplicated in death cargo");
   MineSealing.reconcile(t.l,crash);MineSealing.reconcile(t.l,crash);h.assertTrue(crash.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Placement recovery pays exactly once");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void lowerWaterHasBoundedPlacementReach(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var npc=worker(t);var p=face(t);var state=MineWork.read(t.l,t.shop);
  try{
   var cargo=new ListTag();cargo.add(new ItemStack(Items.DIRT).save(new CompoundTag()));state.put("cargo",cargo);h.assertTrue(MineSealing.begin(t.l,t.shop,state,p),"Select a real plug");
   java.util.function.LongConsumer tick=now->MineSealing.tick(t.l,t.shop,state,npc,p,p,p,q->true,()->{},x->{},now);
   at(npc,p.south().above(4));tick.accept(0);h.assertTrue(!state.contains("sealReady"),"Water beyond 4.5 blocks cannot be placed remotely");
   at(npc,p.south().above(3));double reach=npc.getEyePosition().distanceTo(p.east().getCenter());h.assertTrue(reach>4&&reach<=4.5,"This lower face reproduces the former four-block boundary");
   tick.accept(1);tick.accept(41);h.assertTrue(t.l.getBlockState(p.east()).is(Blocks.DIRT)&&state.getList("cargo",Tag.TAG_COMPOUND).isEmpty(),"Reachable lower water is sealed with exactly the carried block");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_sealing",timeoutTicks=200)
 public static void sealingRejectsLavaFloodedFacesAndOtherLots(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var p=face(t);
  try{
   h.assertTrue(MineSealing.candidate(t.l,t.shop,p).equals(p.east()),"Pure neighboring water is considered");
   t.l.setBlock(p.west(),Blocks.LAVA.defaultBlockState(),2);h.assertTrue(MineSealing.candidate(t.l,t.shop,p)==null,"Lava still blocks the entire operation");t.l.setBlock(p.west(),Blocks.AIR.defaultBlockState(),2);
   t.l.setBlock(p,Blocks.WATER.defaultBlockState(),2);h.assertTrue(MineSealing.candidate(t.l,t.shop,p)==null,"No digging or draining a flooded face");t.l.setBlock(p,Blocks.STONE.defaultBlockState(),2);
   var offset=p.east().subtract(t.e.center());t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",offset.getX(),offset.getY(),offset.getZ()));
   h.assertTrue(MineSealing.candidate(t.l,t.shop,p)==null,"Another building's protected area stays untouched");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
}
