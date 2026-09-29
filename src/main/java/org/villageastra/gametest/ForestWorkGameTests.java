package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-131 §3: the forester of a hut I..VI fells the wild trees round it — whole, with their crowns, in one journal batch — and from II plants
 *  their feet again, from III a mix of kinds, from V three trees a trip. Trees with persistent leaves, touching planks, planted by a player,
 *  on any village's ground, too large or too far are never his. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ForestWorkGameTests {
 private static boolean down(ForestFixture t,List<BlockPos> cells){return cells.stream().allMatch(p->t.l.getBlockState(p).isAir());}
 /** Felled: every log gone, and on the foot the air of a hut of level I or the sapling a hut of II and up plants there at once. */
 private static boolean felled(ForestFixture t,List<BlockPos> tree){return tree.stream().allMatch(p->t.l.getBlockState(p).isAir()||p.equals(tree.get(0))&&t.l.getBlockState(p).getBlock() instanceof SaplingBlock);}
 @GameTest(template="empty",batch="fw_aleveloneforesterfellsthenearestnaturaltree",timeoutTicks=400) public static void aLevelOneForesterFellsTheNearestNaturalTree(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var near=ForestWork.wildOak(t.l,t.wood(4,1),5);var far=ForestWork.wildOak(t.l,t.wood(12,3),5);
   var nearLeaves=t.leavesAround(near);int before=t.chestBlock().countItem(Items.OAK_LOG);
   var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   CompoundTag[] felled={null};
   var end=t.drive(goal,600,r->{if(felled[0]==null&&r.getString("stage").equals("deliver"))felled[0]=r;return felled[0]!=null&&r.getString("stage").equals("choose");});
   h.assertTrue(felled[0]!=null,"A tree came down: "+end);
   h.assertTrue(down(t,near)&&down(t,nearLeaves),"The nearest tree is down whole, its crown with it");
   h.assertTrue(far.stream().allMatch(p->t.l.getBlockState(p).is(Blocks.OAK_LOG)),"The farther one still stands");
   var id=felled[0].getUUID("lastHarvest");
   h.assertTrue(WorldJournal.exists(t.l,Settlement.childId(id,"log/0"))&&WorldJournal.exists(t.l,Settlement.childId(id,"leaf/0")),"Logs and leaves came down through the journal");
   h.assertTrue(ForestFixture.count(felled[0].getList("cargo",Tag.TAG_COMPOUND),Items.OAK_LOG)==near.size(),"He carries its "+near.size()+" logs");
   h.assertTrue(t.chestBlock().countItem(Items.OAK_LOG)==before+near.size(),"They are in the hut chest (its turned cell): "+t.chestBlock().countItem(Items.OAK_LOG));
   h.assertTrue(t.l.getBlockState(near.get(0)).isAir(),"At I nothing is planted on the foot");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_heleavesaplayerplantedtreestanding",timeoutTicks=200) public static void heLeavesAPlayerPlantedTreeStanding(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);ForestPlantings.get(t.l.getServer()).recordPlayer(t.l,logs.get(0));
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),logs.get(0),32).equals("player"),"A tree grown from a player's sapling is not his: "+ForestWork.check(t.l,t.e,t.hut(),logs.get(0),32));
   var other=ForestWork.wildOak(t.l,t.wood(10,2),5);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),other.get(0),32).isEmpty(),"A wild one beside it is");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_timberwithoutnaturalleavesisnotatree",timeoutTicks=200) public static void timberWithoutNaturalLeavesIsNotATree(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var foot=t.wood(4,1);for(int y=0;y<5;y++)t.l.setBlock(foot.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),foot,32).equals("not_wild"),"Bare timber is no tree: "+ForestWork.check(t.l,t.e,t.hut(),foot,32));
   for(var p:BlockPos.betweenClosed(foot.offset(-1,3,-1),foot.offset(1,4,1)))if(t.l.getBlockState(p).isAir())t.l.setBlock(p,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true),2);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),foot,32).equals("not_wild"),"Nor is one crowned with placed (persistent) leaves");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_atreetouchingplanksisleftalone",timeoutTicks=200) public static void aTreeTouchingPlanksIsLeftAlone(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),logs.get(0),32).isEmpty(),"A wild oak is his");
   t.l.setBlock(logs.get(1).east(),Blocks.OAK_PLANKS.defaultBlockState(),2);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),logs.get(0),32).equals("built"),"A plank against its trunk makes it somebody's: "+ForestWork.check(t.l,t.e,t.hut(),logs.get(0),32));
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_heneverfellsonprotectedground",timeoutTicks=200) public static void heNeverFellsOnProtectedGround(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   // In the hut's own lot (its courtyard ground), within three blocks of it, and on the farm's field.
   var inLot=ForesterHut.at(t.e,t.hut(),new BlockPos(7,1,15));t.l.setBlock(inLot.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);var a=ForestWork.wildOak(t.l,inLot,5);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),a.get(0),32).equals("protected"),"A tree on the hut's lot is the village's: "+ForestWork.check(t.l,t.e,t.hut(),a.get(0),32));
   var edge=ForesterHut.at(t.e,t.hut(),new BlockPos(7,1,22));t.l.setBlock(edge.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);var b=ForestWork.wildOak(t.l,edge,5);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),b.get(0),32).equals("protected"),"So is one within three blocks of a lot: "+ForestWork.check(t.l,t.e,t.hut(),b.get(0),32));
   var farm=t.s.buildings().stream().filter(x->x.type().equals("farm")).findFirst().orElseThrow();var plot=FarmField.cells(t.e,farm).get(0);
   t.l.setBlock(plot.below(),Blocks.DIRT.defaultBlockState(),2);var c=ForestWork.wildOak(t.l,plot,5);
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,c.get(0))&&!ForestWork.check(t.l,t.e,t.hut(),c.get(0),64).isEmpty(),"And one on the farm's field: "+ForestWork.check(t.l,t.e,t.hut(),c.get(0),64));
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_atreebeyondtheradiusisnothis",timeoutTicks=200) public static void aTreeBeyondTheRadiusIsNotHis(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   h.assertTrue(ForestBalance.radius(1)==32&&ForestBalance.radius(5)==48&&CoreEffects.value("forester","radius",1)==32&&CoreEffects.value("forester","radius",5)==48,"Reach 32 at I, 48 at V, as the core table says");
   // 38 blocks behind the door: beyond I's reach, within V's.
   var foot=new BlockPos(t.door().getX(),t.origin.getY()+1,t.door().getZ()+38);t.l.setBlock(foot.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);var logs=ForestWork.wildOak(t.l,foot,5);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),logs.get(0),ForestBalance.radius(1)).equals("radius"),"Too far for a hut of level I");
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),logs.get(0),ForestBalance.radius(5)).isEmpty(),"Within reach of a hut of level V");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_agianttreeisskippedwhole",timeoutTicks=200) public static void aGiantTreeIsSkippedWhole(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var foot=t.wood(4,2);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=0;y<8;y++)t.l.setBlock(foot.offset(dx,y,dz),Blocks.SPRUCE_LOG.defaultBlockState(),2);
   for(var p:BlockPos.betweenClosed(foot.offset(-2,6,-2),foot.offset(2,9,2)))if(t.l.getBlockState(p).isAir())t.l.setBlock(p,Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE,1),2);
   h.assertTrue(ForestWork.tree(t.l,foot)==null,"A tree of 72 logs is over the limit of "+ForestBalance.MAX_LOGS);
   h.assertTrue(ForestWork.check(t.l,t.e,t.hut(),foot,32).equals("giant"),"And left standing whole: "+ForestWork.check(t.l,t.e,t.hut(),foot,32));
  }finally{t.done();}
  h.succeed();
 }
 /** A felling stopped after some logs and leaves came down replays under the same ids: every log and leaf is taken once, and the load holds
  *  exactly the receipts' loot. */
 @GameTest(template="empty",batch="fw_astoppedfellingtakeseachlogandleafonce",timeoutTicks=300) public static void aStoppedFellingTakesEachLogAndLeafOnce(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var tree=ForestWork.candidate(t.l,t.e,t.hut(),logs.get(0),32).tree();h.assertTrue(tree!=null,"The oak is his");
   var id=UUID.randomUUID();var axe=new ItemStack(Items.STONE_AXE);var states=new ListTag();for(var p:tree.logs())states.add(NbtUtils.writeBlockState(t.l.getBlockState(p)));
   for(int i=0;i<2;i++)WorldJournal.harvest(t.l,Settlement.childId(id,"log/"+i),tree.logs().get(i),t.l.getBlockState(tree.logs().get(i)),axe);
   for(int j=0;j<3;j++)WorldJournal.harvest(t.l,Settlement.childId(id,"leaf/"+j),tree.leaves().get(j),t.l.getBlockState(tree.leaves().get(j)),axe);
   var r=new CompoundTag();r.putInt("schema",2);r.putUUID("worker",t.forester.getUUID());r.putUUID("operation",id);r.putString("stage","dig");r.putLong("target",tree.foot().asLong());
   r.put("tool",axe.save(new CompoundTag()));r.putLongArray("tree",tree.logs().stream().mapToLong(BlockPos::asLong).toArray());r.put("treeBefore",states);
   r.putLongArray("treeLeaves",tree.leaves().stream().mapToLong(BlockPos::asLong).toArray());r.putLongArray("base",new long[]{tree.foot().asLong()});r.putInt("labor",ResourceWorkGoal.treeLabor(tree.logs().size()));
   t.write(r);
   var snap=JobCargo.snapshot(t.forester,false);
   h.assertTrue(snap.jobs().isEmpty(),"A forester still at his work keeps his job");
   var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"He takes it up again");
   var after=t.drive(goal,20,x->!x.getString("stage").equals("dig"));
   int expectLogs=0;var expect=new HashMap<Item,Integer>();
   for(int i=0;i<tree.logs().size();i++){var rc=WorldJournal.recoverExisting(t.l,Settlement.childId(id,"log/"+i));h.assertTrue(rc!=null,"Log "+i+" has one receipt");for(var raw:rc.getList("loot",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);expect.merge(s.getItem(),s.getCount(),Integer::sum);}}
   for(int j=0;j<tree.leaves().size();j++){var rc=WorldJournal.recoverExisting(t.l,Settlement.childId(id,"leaf/"+j));h.assertTrue(rc!=null,"Leaf "+j+" has one receipt");for(var raw:rc.getList("loot",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);expect.merge(s.getItem(),s.getCount(),Integer::sum);}}
   var cargo=after.getList("cargo",Tag.TAG_COMPOUND);
   for(var e:expect.entrySet())h.assertTrue(ForestFixture.count(cargo,e.getKey())==e.getValue(),"The load holds the receipts' "+e.getKey()+" once: "+ForestFixture.count(cargo,e.getKey())+" of "+e.getValue());
   h.assertTrue(ForestFixture.count(cargo,Items.OAK_LOG)==tree.logs().size(),"All "+tree.logs().size()+" logs, none twice");
   h.assertTrue(down(t,tree.logs())&&down(t,tree.leaves()),"The whole tree and its crown are down");
   // The record never booked the wear of the two logs cut before the stop: the replay wears the axe once for every log of the tree.
   h.assertTrue(ItemStack.of(after.getCompound("tool")).getDamageValue()==tree.logs().size(),"One axe point a log, each once: "+ItemStack.of(after.getCompound("tool")).getDamageValue());
  }finally{t.done();}
  h.succeed();
 }
 /** AD-131: an axe with fewer points left than the tree has logs still brings the whole tree down and breaks on it — a trunk left half
  *  standing would have no foot on soil and no crown, and no forester would ever find it again. */
 @GameTest(template="empty",batch="fw_awornaxestillbringsthetreedown",timeoutTicks=300) public static void aWornAxeStillBringsTheWholeTreeDown(GameTestHelper h){
  var t=ForestFixture.create(h,1);
  try{
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var tree=ForestWork.candidate(t.l,t.e,t.hut(),logs.get(0),32).tree();h.assertTrue(tree!=null,"The oak is his");
   var axe=new ItemStack(Items.STONE_AXE);axe.setDamageValue(axe.getMaxDamage()-2);
   var id=UUID.randomUUID();var states=new ListTag();for(var p:tree.logs())states.add(NbtUtils.writeBlockState(t.l.getBlockState(p)));
   var r=new CompoundTag();r.putInt("schema",2);r.putUUID("worker",t.forester.getUUID());r.putUUID("operation",id);r.putString("stage","dig");r.putLong("target",tree.foot().asLong());
   r.put("tool",axe.save(new CompoundTag()));r.putLongArray("tree",tree.logs().stream().mapToLong(BlockPos::asLong).toArray());r.put("treeBefore",states);
   r.putLongArray("treeLeaves",tree.leaves().stream().mapToLong(BlockPos::asLong).toArray());r.putLongArray("base",new long[]{tree.foot().asLong()});r.putInt("labor",ResourceWorkGoal.treeLabor(tree.logs().size()));
   t.write(r);
   var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up the chop");
   var after=t.drive(goal,20,x->!x.getString("stage").equals("dig"));
   h.assertTrue(down(t,tree.logs()),"Every log of the tree is down, not only the two the axe had points for");
   h.assertTrue(ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.OAK_LOG)==tree.logs().size(),"He carries all "+tree.logs().size()+" logs");
   h.assertTrue(ItemStack.of(after.getCompound("tool")).isEmpty(),"And the axe broke on the tree: "+ItemStack.of(after.getCompound("tool")));
  }finally{t.done();}
  h.succeed();
 }
 /** Felling at II: the crown's leaves are taken off first here, so the loot is fixed — his load holds one oak sapling, as a crown gives it. */
 @GameTest(template="empty",batch="fw_atleveltwoheplantsthefootfromthecrown",timeoutTicks=400) public static void atLevelTwoHePlantsTheFootFromTheCrown(GameTestHelper h){
  var t=ForestFixture.create(h,2);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.hut())==2,"The hut works at II");
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   var r=t.drive(goal,200,x->x.getString("stage").equals("dig"));h.assertTrue(r.getString("stage").equals("dig"),"He picks the oak: "+r);
   for(var raw:r.getLongArray("treeLeaves"))t.l.setBlock(BlockPos.of(raw),Blocks.AIR.defaultBlockState(),2);
   var cargo=new ListTag();cargo.add(new ItemStack(Items.OAK_SAPLING).save(new CompoundTag()));r.put("cargo",cargo);t.write(r);h.assertTrue(goal.canUse(),"He reads his record again");
   var end=t.drive(goal,300,x->x.getString("stage").equals("choose")&&x.getInt("felledToday")>0||x.getString("stage").equals("deliver"));
   h.assertTrue(t.l.getBlockState(logs.get(0)).is(Blocks.OAK_SAPLING),"An oak sapling stands on the foot: "+t.l.getBlockState(logs.get(0)));
   h.assertTrue(ForestFixture.count(end.getList("cargo",Tag.TAG_COMPOUND),Items.OAK_SAPLING)==0,"The sapling left his load");
   var p=ForestPlantings.get(t.l.getServer()).forester(t.l,logs.get(0));h.assertTrue(p!=null&&p.building().equals(t.hutId),"The planting is noted as his hut's");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_withoutsaplingshefellsonandplantsthebarefootlater",timeoutTicks=600) public static void withoutSaplingsHeFellsOnAndPlantsTheBareFootLater(GameTestHelper h){
  var t=ForestFixture.create(h,2);
  try{
   for(var c:List.of((net.minecraft.world.Container)t.l.getBlockEntity(t.hall()),t.chestBlock()))for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(ItemTags.SAPLINGS))c.setItem(i,ItemStack.EMPTY);
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   var r=t.drive(goal,200,x->x.getString("stage").equals("dig"));
   for(var raw:r.getLongArray("treeLeaves"))t.l.setBlock(BlockPos.of(raw),Blocks.AIR.defaultBlockState(),2);
   var after=t.drive(goal,300,x->x.getList("bare",Tag.TAG_COMPOUND).size()>0);
   h.assertTrue(after.getList("bare",Tag.TAG_COMPOUND).size()==1&&t.l.getBlockState(logs.get(0)).isAir(),"The tree came down and its foot is remembered bare: "+after.getList("bare",Tag.TAG_COMPOUND)+" "+after.getString("stage")+"/"+after.getString("status")+" chosen "+r.getString("stage"));
   h.assertTrue(after.getString("status").equals("felled_without_sapling")||after.getString("stage").equals("deliver"),"Felling was not held up: "+after.getString("stage"));
   t.drive(goal,200,x->x.getString("stage").equals("choose"));
   t.chestBlock().setItem(10,new ItemStack(Items.OAK_SAPLING,2));
   var end=t.drive(goal,300,x->x.getList("bare",Tag.TAG_COMPOUND).isEmpty());
   h.assertTrue(end.getList("bare",Tag.TAG_COMPOUND).isEmpty()&&t.l.getBlockState(logs.get(0)).is(Blocks.OAK_SAPLING),"Once a sapling is at hand the bare foot is planted first: "+t.l.getBlockState(logs.get(0)));
   h.assertTrue(t.chestBlock().countItem(Items.OAK_SAPLING)==1,"From the hut chest, once");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_atlevelthreeheplantsthekindleastplantedaround",timeoutTicks=400) public static void atLevelThreeHePlantsTheKindLeastPlantedAround(GameTestHelper h){
  var t=ForestFixture.create(h,3);
  try{
   CropUnlocks.unlock(t.l,t.e,"minecraft:birch_sapling","quest");CropUnlocks.unlock(t.l,t.e,"minecraft:spruce_sapling","quest");
   // Two oaks and a birch planted by this hut before, round it; no spruce yet.
   var planted=ForestPlantings.get(t.l.getServer());int k=0;
   for(var kind:List.of(Blocks.OAK_SAPLING,Blocks.OAK_SAPLING,Blocks.BIRCH_SAPLING)){var at=t.wood(-6+3*k,12);t.l.setBlock(at,kind.defaultBlockState(),2);planted.recordForester(t.l,at,t.s.id(),t.hutId,net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind.asItem()).toString(),0);k++;}
   var logs=ForestWork.wildOak(t.l,t.wood(4,1),5);var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   var r=t.drive(goal,200,x->x.getString("stage").equals("dig"));
   for(var raw:r.getLongArray("treeLeaves"))t.l.setBlock(BlockPos.of(raw),Blocks.AIR.defaultBlockState(),2);
   var cargo=new ListTag();for(var it:List.of(Items.OAK_SAPLING,Items.BIRCH_SAPLING,Items.SPRUCE_SAPLING))cargo.add(new ItemStack(it).save(new CompoundTag()));r.put("cargo",cargo);t.write(r);h.assertTrue(goal.canUse(),"He reads his record again");
   t.drive(goal,300,x->x.getInt("plantedToday")>0);
   h.assertTrue(t.l.getBlockState(logs.get(0)).is(Blocks.SPRUCE_SAPLING),"The spruce, least planted round the hut, goes on the oak's foot: "+t.l.getBlockState(logs.get(0)));
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_atlevelthreethehutasksforsaplings",timeoutTicks=300) public static void atLevelThreeTheHutAsksForSaplings(GameTestHelper h){
  var t=ForestFixture.create(h,2);
  try{
   java.util.function.Supplier<List<Workshops.Want>> saplings=()->WorkerSupplies.wants(t.l,t.e,t.hutId).stream().filter(w->w.ingredient().getItems().length>0&&w.ingredient().getItems()[0].is(ItemTags.SAPLINGS)).toList();
   h.assertTrue(saplings.get().isEmpty()&&!CropQuests.saplingsAsked(t.l,t.e),"At II the hut asks for none");
   t.level(3);
   var wants=saplings.get();
   h.assertTrue(wants.size()==1&&wants.get(0).ingredient().getItems()[0].is(Items.OAK_SAPLING)&&wants.get(0).count()==ForestBalance.SAPLING_STOCK&&wants.get(0).destination().equals(t.hutId),"At III it asks for "+ForestBalance.SAPLING_STOCK+" of the one opened kind: "+wants.size());
   CropUnlocks.unlock(t.l,t.e,"minecraft:birch_sapling","quest");
   h.assertTrue(saplings.get().size()==2,"And for every kind opened");
   h.assertTrue(CropQuests.saplingsAsked(t.l,t.e),"The board may ask for a new kind now");
   var first=CropQuests.post(t.l,t.e,0);var second=CropQuests.post(t.l,t.e,0);
   h.assertTrue(first!=null&&CropUnlocks.CROPS.contains(first.getString("item")),"The farm's crop quest keeps its slot: "+(first==null?null:first.getString("item")));
   h.assertTrue(second!=null&&second.getString("item").equals("minecraft:spruce_sapling"),"And a quest for the next sapling stands beside it: "+(second==null?null:second.getString("item")));
   h.assertTrue(CropQuests.saplingQuest(t.l,t.e).equals("minecraft:spruce_sapling")&&BuildingCards.card(t.l,t.e,t.hut()).getCompound("forest").getList("asks",Tag.TAG_COMPOUND).size()==3,"The card shows the asks and the quest");
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_atlevelfivehecarriesthreetreesatrip",timeoutTicks=900) public static void atLevelFiveHeCarriesThreeTreesATrip(GameTestHelper h){
  var t=ForestFixture.create(h,5);
  try{
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.hut())==5,"The hut works at V");
   var trees=List.of(ForestWork.wildOak(t.l,t.wood(0,2),5),ForestWork.wildOak(t.l,t.wood(6,2),5),ForestWork.wildOak(t.l,t.wood(12,2),5));int before=t.chestBlock().countItem(Items.OAK_LOG);
   var goal=new ResourceWorkGoal(t.forester,true,()->6000L);h.assertTrue(goal.canUse(),"The forester takes up his work");
   CompoundTag[] trip={null};
   t.drive(goal,900,r->{if(trip[0]==null&&r.getString("stage").equals("deliver"))trip[0]=r;return trip[0]!=null&&r.getString("stage").equals("choose");});
   h.assertTrue(trip[0]!=null&&trip[0].getInt("trees")==3,"One trip holds three trees: "+(trip[0]==null?null:trip[0].getInt("trees")));
   h.assertTrue(trees.stream().allMatch(tr->felled(t,tr)),"All three are down (a hut of V plants their feet again): "
    +trees.stream().map(tr->tr.get(0)+"="+t.l.getBlockState(tr.get(0))).toList());
   h.assertTrue(t.chestBlock().countItem(Items.OAK_LOG)==before+15,"Their 15 logs are in the chest after one visit: "+t.chestBlock().countItem(Items.OAK_LOG));
  }finally{t.done();}
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_fellinglabourfollowsthelevel") public static void fellingLabourFollowsTheLevel(GameTestHelper h){
  int[] want={20,30,40,50,100,120};
  for(int lv=1;lv<=6;lv++)h.assertTrue(ResourceWorkGoal.fellingLabor(lv)==want[lv-1]&&CoreEffects.value("forester","felling",lv)==want[lv-1],"Felling labour at "+lv+": "+ResourceWorkGoal.fellingLabor(lv));
  h.assertTrue(ResourceWorkGoal.fellingLabor(5)==2*ResourceWorkGoal.fellingLabor(4)&&ForestBalance.walkSpeed(5)>ForestBalance.walkSpeed(4)&&ForestBalance.treesPerTrip(5)==3,"V doubles IV, walks faster and takes three trees");
  h.succeed();
 }
 @GameTest(template="empty",batch="fw_naturalsupplynevertakesalogofaplayertree") public static void naturalSupplyNeverTakesALogOfAPlayerTree(GameTestHelper h){
  h.assertTrue(!NaturalSupplyGoal.natural(Blocks.OAK_LOG.defaultBlockState())&&!NaturalSupplyGoal.provides(Items.OAK_LOG)&&!NaturalSupplyGoal.provides(Items.SPRUCE_LOG),"A gatherer never cuts a log: wood is the forester's, a whole wild tree at a time");
  h.assertTrue(NaturalSupplyGoal.natural(Blocks.SAND.defaultBlockState()),"Sand is still his");
  h.succeed();
 }
}
