package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import org.villageastra.persistence.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-122 (owner, 2026-09-21): "the miner must be as fast as a player with a pick of his level". A village miner breaks a block in
 *  the ticks a player holding the same pick needs — stone pick: stone 12, deepslate 23; iron pick: stone 8 — and the tool still wears
 *  one point a block. Each call of the goal in these tests is one tick at the face. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MinerSpeedGameTests {
 private static final String BATCH="miner_speed";
 /** The vanilla numbers themselves: speed / hardness / 30 per tick with the right tool, / 100 with a bare hand on stone. */
 @GameTest(template="empty",batch=BATCH) public static void theBreakTimeIsAPlayersWithTheSameTool(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(1,2,1));
  h.assertTrue(MinerSpeed.breakTicks(Blocks.STONE.defaultBlockState(),l,at,new ItemStack(Items.STONE_PICKAXE))==12,"Stone with a stone pick: 12 ticks");
  h.assertTrue(MinerSpeed.breakTicks(Blocks.DEEPSLATE.defaultBlockState(),l,at,new ItemStack(Items.STONE_PICKAXE))==23,"Deepslate with a stone pick: 23 ticks");
  h.assertTrue(MinerSpeed.breakTicks(Blocks.STONE.defaultBlockState(),l,at,new ItemStack(Items.IRON_PICKAXE))==8,"Stone with an iron pick: 8 ticks");
  h.assertTrue(MinerSpeed.breakTicks(Blocks.IRON_ORE.defaultBlockState(),l,at,new ItemStack(Items.STONE_PICKAXE))==23,"Iron ore (hardness 3) with a stone pick: 23 ticks");
  h.assertTrue(MinerSpeed.breakTicks(Blocks.STONE.defaultBlockState(),l,at,ItemStack.EMPTY)==150,"Stone by hand, without the right tool: 150 ticks");
  h.assertTrue(MinerSpeed.breakTicks(Blocks.BEDROCK.defaultBlockState(),l,at,new ItemStack(Items.NETHERITE_PICKAXE))==Integer.MAX_VALUE,"Bedrock never breaks");
  h.succeed();
 }
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aStonePickCutsStoneInTwelveTicks(GameTestHelper h){dig(h,Blocks.STONE,Items.STONE_PICKAXE,12);}
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aStonePickCutsDeepslateInTwentyThreeTicks(GameTestHelper h){dig(h,Blocks.DEEPSLATE,Items.STONE_PICKAXE,23);}
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void anIronPickCutsStoneInEightTicks(GameTestHelper h){dig(h,Blocks.STONE,Items.IRON_PICKAXE,8);}
 /** A miner standing at his face with the block before him: the goal's calls until the block is out and in the batch he carries. */
 private static void dig(GameTestHelper h,Block rock,Item pick,int expected){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,20,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<24;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var mineId=Settlement.childId(s.id(),"building/mine");s.addBuilding(new Settlement.Building(mineId,"mine",14,0,0));
  var mine=s.buildings().stream().filter(b->b.id().equals(mineId)).findFirst().orElseThrow();
  var chest=LogisticsRoutes.position(e,mine);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  ResidentEntity npc=null;
  try{
   s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
   var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,s.homes().iterator().next().id());s.assign(r.id(),Profession.MINER,mine.id());
   var feet=center.offset(20,1,9);var target=feet.east().above();l.setBlock(target,rock.defaultBlockState(),3);
   npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);
   npc.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);l.addFreshEntity(npc);
   var local=BuildingPlacement.local(e,mine,feet);
   var record=new CompoundTag();record.putInt("schema",1);record.putInt("width",3);record.putInt("height",4);record.putInt("descent",0);
   record.putString("stage","dig");record.putUUID("operation",UUID.randomUUID());record.put("tool",new ItemStack(pick).save(new CompoundTag()));
   record.putLong("target",target.asLong());record.put("before",NbtUtils.writeBlockState(rock.defaultBlockState()));record.putInt("labor",0);
   record.putIntArray("access",new int[]{local.getX(),local.getY(),local.getZ()});
   MineWork.write(l,mine,record);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);
   h.assertTrue(goal.canUse(),"The miner takes up his face");
   int calls=0;
   while(calls<400&&MineWork.read(l,mine).getString("stage").equals("dig")){goal.tick();calls++;
    if(calls<expected)h.assertTrue(l.getBlockState(target).is(rock),"The block still stands after "+calls+" ticks: "+npc.workStatus());}
   var after=MineWork.read(l,mine);
   h.assertTrue(calls==expected&&l.getBlockState(target).isAir(),"The "+rock+" broke after "+calls+" ticks, a player's "+expected+"; stage="+after.getString("stage")+" status="+npc.workStatus());
   h.assertTrue(after.getString("stage").equals("choose")&&after.getList("cargo",Tag.TAG_COMPOUND).size()==1&&ItemStack.of(after.getCompound("tool")).getDamageValue()==1,"He carries the block on to the next cell and the pick wore one point: "+after);
  }finally{
   if(npc!=null)npc.discard();SettlementData.get(l.getServer()).remove(s.id());
   try{Files.deleteIfExists(MineWork.path(l,mine.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  }
  h.succeed();
 }

 /** A mine at level I with its chest, a miner assigned and a stone pick in his drive record, which starts at the top of the stair
  *  (descent 0: no shaft built, the miner is stood at each face by the test, NoAI). The first cells of the drive are stone. */
 private record Pit(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building mine,ResidentEntity npc,List<BlockPos> cells,List<int[]> drive){}
 private static Pit pit(GameTestHelper h,int stone){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,20,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<24;x++)for(int z=-2;z<12;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<6;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var mineId=Settlement.childId(s.id(),"building/mine");s.addBuilding(new Settlement.Building(mineId,"mine",14,0,0));
  var mine=s.buildings().stream().filter(b->b.id().equals(mineId)).findFirst().orElseThrow();
  var chest=LogisticsRoutes.position(e,mine);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,s.homes().iterator().next().id());s.assign(r.id(),Profession.MINER,mine.id());
  var record=MineWork.read(l,mine);record.putInt("descent",0);record.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
  // The drive's own cells: the first ones stone, the next few open, whatever the lot had there.
  var sim=record.copy();var cells=new ArrayList<BlockPos>();var drive=new ArrayList<int[]>();
  for(int i=0;i<stone+6;i++){var next=MineWork.next(l,e,mine,sim);var at=MineWork.at(e,mine,next.cell());
   l.setBlock(at,(i<stone?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);if(i<stone)cells.add(at);MineWork.chose(sim,next);MineWork.step(sim);drive.add(new int[]{sim.getInt("step"),sim.getInt("cell")});}
  MineWork.write(l,mine,record);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);
  var feet=chest.east();npc.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);l.addFreshEntity(npc);
  return new Pit(l,e,s,mine,npc,cells,drive);
 }
 private static void done(Pit t){
  if(t.npc.isAlive())t.npc.discard();SettlementData.get(t.l.getServer()).remove(t.s.id());
  try{Files.deleteIfExists(MineWork.path(t.l,t.mine.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 private static Container chest(Pit t){return (Container)t.l.getBlockEntity(LogisticsRoutes.position(t.e,t.mine));}
 private static int carried(CompoundTag record){int n=0;for(Tag raw:record.getList("cargo",Tag.TAG_COMPOUND))n+=ItemStack.of((CompoundTag)raw).getCount();return n;}
 /** One call of the goal, with the miner stood where the stage needs him: at the face of the chosen cell, or beside the mine chest. */
 private static void call(Pit t,ResourceWorkGoal goal){
  var r=MineWork.read(t.l,t.mine);BlockPos feet=null;
  if(r.getString("stage").equals("dig")){var a=MineWork.access(r);if(a!=null)feet=BuildingPlacement.at(t.e,t.mine,a.x(),a.y(),a.z());}
  else if(r.getString("stage").equals("deliver"))feet=LogisticsRoutes.position(t.e,t.mine).east();
  else if(r.getString("stage").equals("stair")){var st=MineDrive.next(new MineDrive.Drive(r.getInt("stairStep"),0,MineDrive.EAST,0),Integer.MAX_VALUE/2,MineWork.shape(r)).stand();feet=BuildingPlacement.at(t.e,t.mine,st.x(),st.y(),st.z());}
  if(feet!=null&&t.npc.distanceToSqr(feet.getX()+.5,feet.getY()+.5,feet.getZ()+.5)>1)t.npc.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);
  goal.tick();
 }
 /** AD-122: the miner no longer walks to the chest with every block. He digs the consecutive cells of his drive with the blocks in hand and
  *  takes them to the chest in one visit — here when the evening comes; the drive moved on a cell with each block. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void aMinerDigsConsecutiveCellsBeforeOneDelivery(GameTestHelper h){
  int n=6;var t=pit(h,n);
  try{
   long[] clock={6000L};var goal=new ResourceWorkGoal(t.npc,true,()->clock[0]);
   h.assertTrue(goal.canUse(),"The miner takes up his drive");
   int deliveries=0,calls=0;String last="";
   while(calls++<3000){var r=MineWork.read(t.l,t.mine);String stage=r.getString("stage");
    if(stage.equals("deliver")&&!last.equals("deliver"))deliveries++;last=stage;
    if(stage.equals("choose")&&carried(r)>=n)break;
    call(t,goal);}
   var r=MineWork.read(t.l,t.mine);
   h.assertTrue(deliveries==0&&chest(t).isEmpty(),"No trip to the chest while he digs "+n+" cells: "+deliveries+" deliveries, chest "+chest(t).countItem(Items.COBBLESTONE));
   h.assertTrue(carried(r)==n&&ItemStack.of(r.getList("cargo",Tag.TAG_COMPOUND).getCompound(0)).is(Items.COBBLESTONE),"He carries the "+n+" cobblestone of the "+n+" cells: "+r.get("cargo"));
   h.assertTrue(ItemStack.of(r.getCompound("tool")).getDamageValue()==n,"The pick wore a point a block: "+r.getCompound("tool"));
   for(var cell:t.cells)h.assertTrue(t.l.getBlockState(cell).isAir(),"Cell "+cell.toShortString()+" is dug");
   h.assertTrue(r.getInt("step")==t.drive.get(n-1)[0]&&r.getInt("cell")==t.drive.get(n-1)[1],"The drive stands past the "+n+" cells: step "+r.getInt("step")+" cell "+r.getInt("cell"));
   // Dusk comes: the batch goes to the chest in one visit.
   clock[0]=SleepGoal.DUSK-100;
   while(calls++<3000){r=MineWork.read(t.l,t.mine);String stage=r.getString("stage");
    if(stage.equals("deliver")&&!last.equals("deliver"))deliveries++;
    if(last.equals("deliver")&&!stage.equals("deliver"))break;last=stage;
    call(t,goal);}
   r=MineWork.read(t.l,t.mine);
   h.assertTrue(deliveries==1&&chest(t).countItem(Items.COBBLESTONE)==n&&carried(r)==0,"One visit brings all "+n+": "+deliveries+" deliveries, chest "+chest(t).countItem(Items.COBBLESTONE)+", still carried "+carried(r));
   h.assertTrue(r.getInt("step")==t.drive.get(n-1)[0]&&r.getInt("cell")==t.drive.get(n-1)[1]&&!r.contains("advanced"),"The delivery does not move the drive again: "+r);
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void aMinerKilledMidBatchLeavesTheDugBlocksOnce(GameTestHelper h){killed(h,false);}
 /** The same with the fourth block harvested in the journal and the record not written yet (a crash between the two): it counts once. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void aMinerKilledWithAHarvestAheadOfHisRecordLeavesItOnce(GameTestHelper h){killed(h,true);}
 private static void killed(GameTestHelper h,boolean ahead){
  int dug=3;var t=pit(h,dug+1);
  try{
   var goal=new ResourceWorkGoal(t.npc,true,()->6000L);h.assertTrue(goal.canUse(),"The miner takes up his drive");
   int calls=0;CompoundTag r;
   // He digs three cells and stands at the fourth, half through it.
   while(calls++<3000){r=MineWork.read(t.l,t.mine);if(carried(r)==dug&&r.getString("stage").equals("dig")&&r.getInt("labor")>2)break;call(t,goal);}
   r=MineWork.read(t.l,t.mine);
   h.assertTrue(carried(r)==dug&&r.getString("stage").equals("dig")&&chest(t).isEmpty(),"Three blocks carried, the fourth begun: "+r);
   int expected=dug;
   if(ahead){var target=BlockPos.of(r.getLong("target"));var loot=WorldJournal.harvest(t.l,r.getUUID("operation"),target,Blocks.STONE.defaultBlockState(),ItemStack.of(r.getCompound("tool")));
    h.assertTrue(loot!=null&&t.l.getBlockState(target).isAir(),"The fourth block is out by the journal");expected++;}
   t.npc.hurt(t.l.damageSources().genericKill(),1000);CargoCustody.tick(t.l.getServer());
   var drops=CargoCustody.inspect(t.l.getServer(),t.npc.getUUID()).getList("drops",Tag.TAG_COMPOUND);
   h.assertTrue(!drops.isEmpty(),"His death leaves a pile");
   var pile=(Container)t.l.getBlockEntity(BlockPos.of(drops.getCompound(0).getLong("pos")));
   int pick=-1;for(int i=0;i<pile.getContainerSize();i++)if(pile.getItem(i).is(Items.STONE_PICKAXE))pick=pile.getItem(i).getDamageValue();
   h.assertTrue(pile.countItem(Items.COBBLESTONE)==expected&&pick==expected,"Exactly the "+expected+" dug blocks and the pick worn "+expected+" remain: cobblestone "+pile.countItem(Items.COBBLESTONE)+", pick "+pick);
   h.assertTrue(chest(t).isEmpty()&&!t.s.resident(t.npc.getUUID()).alive(),"Nothing reached the chest, the miner is dead");
   var reset=MineWork.read(t.l,t.mine);
   h.assertTrue(!reset.hasUUID("worker")&&reset.getList("cargo",Tag.TAG_COMPOUND).isEmpty()&&reset.getInt("step")==t.drive.get(expected-1)[0]&&reset.getInt("cell")==t.drive.get(expected-1)[1],"The successor starts past the "+expected+" dug cells, with nothing carried: "+reset);
   pile.clearContent();CargoCustody.onDeath(t.npc);CargoCustody.recover(t.l.getServer());
   h.assertTrue(pile.isEmpty()&&chest(t).isEmpty(),"A repeated death or recovery never books the batch again");
  }finally{done(t);}
  h.succeed();
 }

 /** The stair blocks of a stair step, in the world. */
 private static List<BlockPos> stairs(Pit t,int step){var out=new ArrayList<BlockPos>();for(var c:MineDrive.stairs(step,MineWork.shape(MineWork.read(t.l,t.mine))))out.add(BuildingPlacement.at(t.e,t.mine,c.x(),c.y(),c.z()));return out;}
 /** AD-122 (owner, 2026-09-21): a drive opened now is 5 high — one block taller — and a finished stair step gets stairs in its bottom cells,
  *  cut from the cobblestone he carries (one a stair) and facing up towards the mouth, so the shaft is walked without a jump. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void aFinishedStepGetsStairsUnderAFiveHighRoof(GameTestHelper h){
  var t=pit(h,16);
  try{
   var first=MineWork.read(t.l,t.mine);var shape=MineWork.shape(first);
   h.assertTrue(first.getInt("height")==5&&MineWork.HEIGHT==5&&shape.galleryHeight()==5,"A new drive and its galleries are 5 high: "+first.getInt("height")+"/"+shape.galleryHeight());
   var goal=new ResourceWorkGoal(t.npc,true,()->6000L);h.assertTrue(goal.canUse(),"The miner takes up his drive");
   int calls=0;CompoundTag r=first;
   while(calls++<4000){r=MineWork.read(t.l,t.mine);if(r.getInt("step")==1&&r.getString("stage").equals("choose")&&!r.contains("stairStep"))break;call(t,goal);}
   r=MineWork.read(t.l,t.mine);
   var set=stairs(t,0);h.assertTrue(set.size()==3,"Three stairs across the 3-wide step");
   var mine=t.s.buildings().stream().filter(b->b.id().equals(t.mine.id())).findFirst().orElseThrow();
   var facing=BuildingPlacement.state(Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.NORTH),mine.rotation()).getValue(net.minecraft.world.level.block.StairBlock.FACING);
   for(var at:set)h.assertTrue(t.l.getBlockState(at).is(Blocks.COBBLESTONE_STAIRS)&&t.l.getBlockState(at).getValue(net.minecraft.world.level.block.StairBlock.FACING)==facing,"A cobblestone stair rising to the mouth at "+at.toShortString()+": "+t.l.getBlockState(at));
   h.assertTrue(carried(r)==15-3&&chest(t).isEmpty(),"The step gave 15 cobblestone, 3 went into its stairs: carried "+carried(r));
   // Headroom: above each stair the 4 cells of the 5-high step are open; the protection holds the stairs like the rest of the drive.
   for(var at:set)for(int up=1;up<=4;up++)h.assertTrue(t.l.getBlockState(at.above(up)).isAir(),"Open above the stair at "+at.above(up).toShortString());
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,set.get(1)),"The stairs are the settlement's");
   h.assertTrue(r.getInt("step")==1&&r.getInt("cell")==0&&!r.contains("stairPlaced")&&!r.contains("stairItem"),"The drive goes on at step 1: "+r);
  }finally{done(t);}
  h.succeed();
 }
 /** A miner killed while he sets the stairs, the second one set by the journal ahead of his record: the batch less exactly the two stairs set
  *  is left, and the third stair stays due for his successor. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=400) public static void aMinerKilledAtTheStairsPaysEachStairOnce(GameTestHelper h){
  var t=pit(h,15);
  try{
   var goal=new ResourceWorkGoal(t.npc,true,()->6000L);h.assertTrue(goal.canUse(),"The miner takes up his drive");
   int calls=0;CompoundTag r;
   while(calls++<4000){r=MineWork.read(t.l,t.mine);if(r.getString("stage").equals("stair")&&r.getInt("stairPlaced")==1)break;call(t,goal);}
   r=MineWork.read(t.l,t.mine);var set=stairs(t,0);
   h.assertTrue(r.getString("stage").equals("stair")&&carried(r)==14&&t.l.getBlockState(set.get(0)).is(Blocks.COBBLESTONE_STAIRS),"One stair set and paid: "+r);
   h.assertTrue(WorldJournal.place(t.l,Settlement.childId(r.getUUID("operation"),"stair/1"),set.get(1),Blocks.AIR.defaultBlockState(),t.l.getBlockState(set.get(0))),"The second stair is set by the journal");
   t.npc.hurt(t.l.damageSources().genericKill(),1000);CargoCustody.tick(t.l.getServer());
   var drops=CargoCustody.inspect(t.l.getServer(),t.npc.getUUID()).getList("drops",Tag.TAG_COMPOUND);h.assertTrue(!drops.isEmpty(),"His death leaves a pile");
   var pile=(Container)t.l.getBlockEntity(BlockPos.of(drops.getCompound(0).getLong("pos")));
   h.assertTrue(pile.countItem(Items.COBBLESTONE)==13,"15 dug, 2 in stairs: 13 cobblestone remain, not "+pile.countItem(Items.COBBLESTONE));
   var reset=MineWork.read(t.l,t.mine);
   h.assertTrue(reset.contains("stairStep")&&reset.getInt("stairStep")==0&&!reset.hasUUID("worker")&&reset.getList("cargo",Tag.TAG_COMPOUND).isEmpty()&&t.l.getBlockState(set.get(2)).isAir(),"The third stair stays due for the successor: "+reset);
   pile.clearContent();CargoCustody.onDeath(t.npc);CargoCustody.recover(t.l.getServer());
   h.assertTrue(pile.isEmpty(),"A repeated death or recovery never books the batch again");
  }finally{done(t);}
  h.succeed();
 }

 /** AD-122 (owner): a beam sits above the walking headroom — three open cells over every stair, also where a climber steps to the stair above
  *  (his head over the lower step's column reaches three cells over its stair) — and so do the lights; the stair and gallery lights come every
  *  6 steps (about 8 blocks down the diagonal) and every 8 columns, never in a beam's cell. */
 @GameTest(template="empty",batch=BATCH) public static void beamsAndLightsHangAboveTheHeadroomAtTheirSpacing(GameTestHelper h){
  var s=MineDrive.Shape.of(3,MineWork.HEIGHT,MineWork.DRIVE_DESCENT);int w=s.width(),beams=0,lastLight=-1;
  for(int step=0;step<30;step++){
   var stair=MineDrive.stairs(step,s);h.assertTrue(stair.size()==w,"Stairs across the step "+step);int floor=stair.get(0).y();
   var beam=MineDrive.next(new MineDrive.Drive(step,w*s.height()-1,MineDrive.EAST,0),Integer.MAX_VALUE/2,s).beam();
   if(beam!=null){beams++;for(var c:beam.cells())h.assertTrue(c.y()>=floor+4&&c.y()<=floor+s.height()-1,"Step "+step+": the beam at "+c+" is above the three open cells over its stair at y "+floor);}
   var light=MineDrive.stairLight(step,s,ResourceWorkGoal.LIGHT_STEPS);
   if(light!=null){h.assertTrue(light.cell().y()==floor+4&&light.cell().z()==7+step,"Step "+step+": the light hangs in the top cell "+light.cell());
    h.assertTrue(beam==null,"Step "+step+": never in a beam step");
    if(lastLight>=0)h.assertTrue(step-lastLight==ResourceWorkGoal.LIGHT_STEPS&&ResourceWorkGoal.LIGHT_STEPS*Math.sqrt(2)>=8,"A light every "+ResourceWorkGoal.LIGHT_STEPS+" steps: "+lastLight+" -> "+step);lastLight=step;
    h.assertTrue(light.stand().equals(new MineDrive.Cell(3,floor,7+step)),"He hangs it standing on the step's own stair: "+light.stand());}
  }
  h.assertTrue(beams>=7&&lastLight>=20,"Beams and lights all down the stair: "+beams+" beams, last light at step "+lastLight);
  int f=12,prev=-1,gh=s.galleryHeight();h.assertTrue(gh==5,"The galleries are 5 high");
  for(int r=0;r<CoreEffects.mine().galleryLength();r++){var d=new MineDrive.Drive(f+1,gh-1,MineDrive.EAST,r);var light=MineDrive.galleryLight(d,f,s,ResourceWorkGoal.LIGHT_COLUMNS);
   var beam=MineDrive.next(d,f,s).beam();
   if(beam!=null)h.assertTrue(beam.cells().get(0).y()==-f-s.descent()+gh-1,"A gallery beam is its top cell, 4 over the floor");
   if(light==null)continue;h.assertTrue(beam==null&&light.cell().y()==-f-s.descent()+gh-1,"Gallery column "+r+": the light hangs in the top cell, not a beam's");
   if(prev>=0)h.assertTrue(r-prev==ResourceWorkGoal.LIGHT_COLUMNS,"A gallery light every "+ResourceWorkGoal.LIGHT_COLUMNS+" columns: "+prev+" -> "+r);prev=r;}
  h.assertTrue(prev>=0,"The gallery has lights");
  h.succeed();
 }
 /** AD-122 (owner): the dug flight carries on the built one — the first stair of the drive lies one block below the last tread of the mine's
  *  own shaft, right after it, and the built mouth floor it replaces is inside the drive (dug, claimed, protected, not the builders' to restore). */
 @GameTest(template="empty",batch=BATCH) public static void theFirstDugStepContinuesTheBuiltStair(GameTestHelper h){
  var layout=BuildingBlueprints.layout("mine",BlockPos.ZERO);var s=MineDrive.Shape.of(3,MineWork.HEIGHT,MineWork.DRIVE_DESCENT);
  int lastTread=Integer.MAX_VALUE;for(var c:layout.entrySet())if(c.getKey().getX()==3&&c.getKey().getZ()==6&&c.getValue().is(Blocks.STONE_BRICK_STAIRS))lastTread=c.getKey().getY();
  h.assertTrue(lastTread==-BuildingBlueprints.SHAFT_DESCENT,"The built shaft's last tread at z 6: "+lastTread);
  var first=MineDrive.stairs(0,s).get(1);
  h.assertTrue(first.z()==7&&first.y()==lastTread-1,"The first dug stair continues the flight one block lower: "+first+" after the tread at y "+lastTread);
  var stand=MineDrive.next(MineDrive.Drive.START,Integer.MAX_VALUE/2,s).stand();
  h.assertTrue(stand.equals(new MineDrive.Cell(3,lastTread,6)),"He digs the first step standing on the last built tread: "+stand);
  var mouth=layout.get(new BlockPos(3,-BuildingBlueprints.SHAFT_DESCENT,7));
  h.assertTrue(mouth!=null&&mouth.is(Blocks.STONE_BRICKS)&&-BuildingBlueprints.SHAFT_DESCENT<=s.height()-1-s.descent()&&-BuildingBlueprints.SHAFT_DESCENT>first.y(),"The built mouth floor lies inside the first step's cells, above its stair");
  h.assertTrue(new MineArea(0,3,MineWork.HEIGHT,MineWork.DRIVE_DESCENT).contains(3,-BuildingBlueprints.SHAFT_DESCENT,7,0)&&new MineArea(0,3,MineWork.HEIGHT,MineWork.DRIVE_DESCENT).contains(3,first.y(),7,0),"The dug mouth floor and the stair are the drive's claim");
  var legacy=MineDrive.Shape.of(3,4,BuildingBlueprints.SHAFT_DESCENT);
  h.assertTrue(MineDrive.stairs(0,legacy).isEmpty()&&MineDrive.stairLight(2,legacy,ResourceWorkGoal.LIGHT_STEPS)==null,"A drive begun before keeps its shape: no stairs, no lights");
  h.succeed();
 }
 /** AD-122 (owner): the miner hangs the light due at a finished step from the lanterns he carries, one journalled placement, paid once; with none
  *  in hand nor in the hall the mine asks for one (WorkerSupplies — the office and his marker show it) and he digs on. */
 @GameTest(template="empty",batch=BATCH,timeoutTicks=600) public static void aMinerHangsALanternEverySixStepsAndAsksForMore(GameTestHelper h){
  var t=pit(h,45);
  try{
   var r=MineWork.read(t.l,t.mine);r.putInt("lightsHeld",2);r.putString("lightItem","minecraft:lantern");MineWork.write(t.l,t.mine,r);
   // The drive here opens at the lot (descent 0, open sky above): a roof over the first steps, as rock is over a real drive.
   for(int step=0;step<4;step++)for(int x=2;x<=4;x++)t.l.setBlock(BuildingPlacement.at(t.e,t.mine,x,MineWork.HEIGHT-step,7+step),Blocks.STONE.defaultBlockState(),2);
   var goal=new ResourceWorkGoal(t.npc,true,()->6000L);h.assertTrue(goal.canUse(),"The miner takes up his drive");
   int calls=0;
   while(calls++<8000){r=MineWork.read(t.l,t.mine);if(r.getInt("step")==3&&r.getString("stage").equals("choose")&&!r.contains("stairStep")&&r.getIntArray("lightsDue").length==0)break;call2(t,goal);}
   r=MineWork.read(t.l,t.mine);var light=MineDrive.stairLight(2,MineWork.shape(r),ResourceWorkGoal.LIGHT_STEPS);
   var at=BuildingPlacement.at(t.e,t.mine,light.cell().x(),light.cell().y(),light.cell().z());
   h.assertTrue(t.l.getBlockState(at).is(Blocks.LANTERN)&&t.l.getBlockState(at).getValue(net.minecraft.world.level.block.LanternBlock.HANGING),"A lantern hangs over step 2 at "+at.toShortString()+": "+t.l.getBlockState(at));
   h.assertTrue(r.getInt("lightsHeld")==1&&r.getIntArray("lightsDue").length==0,"One lantern spent, none due: "+r);
   for(int step:new int[]{0,1,3})for(int x=2;x<=4;x++){var top=BuildingPlacement.at(t.e,t.mine,x,MineWork.HEIGHT-1-step,7+step);h.assertTrue(!t.l.getBlockState(top).is(Blocks.LANTERN),"No light over step "+step);}
   for(var c:MineDrive.stairs(2,MineWork.shape(r)))for(int up=1;up<=3;up++)h.assertTrue(t.l.getBlockState(BuildingPlacement.at(t.e,t.mine,c.x(),c.y()+up,c.z())).isAir(),"The headroom over step 2 stays open");
   // None in hand, none in the hall, one due: the mine asks for a light.
   r.putInt("lightsHeld",0);r.remove("lightItem");r.putIntArray("lightsDue",new int[]{light.cell().x(),light.cell().y(),light.cell().z(),light.stand().x(),light.stand().y(),light.stand().z(),1,0});MineWork.write(t.l,t.mine,r);
   var wants=WorkerSupplies.wants(t.l,t.e,t.mine.id());
   h.assertTrue(wants.stream().anyMatch(w->w.matches(new ItemStack(Items.LANTERN))&&w.matches(new ItemStack(Items.TORCH))),"The mine asks the hall for a lantern or torches: "+wants.size()+" wants");
  }finally{done(t);}
  h.succeed();
 }
 /** call() with the light stage: he stands where he hangs it. */
 private static void call2(Pit t,ResourceWorkGoal goal){
  var r=MineWork.read(t.l,t.mine);
  if(r.getString("stage").equals("light")){var d=r.getIntArray("lightsDue");if(d.length>=8){var feet=BuildingPlacement.at(t.e,t.mine,d[3],d[4],d[5]);t.npc.moveTo(feet.getX()+.5,feet.getY()+1,feet.getZ()+.5,0,0);}goal.tick();return;}
  if(r.getString("stage").equals("support_fetch")||r.getString("stage").equals("tool")){var hall=t.e.center().offset(1,1,4).east();t.npc.moveTo(hall.getX()+.5,hall.getY(),hall.getZ()+.5,0,0);goal.tick();return;}
  if(r.getString("stage").equals("support_place")){var st=MineWork.beamStand(r);var feet=BuildingPlacement.at(t.e,t.mine,st.x(),st.y(),st.z());t.npc.moveTo(feet.getX()+.5,feet.getY()+1,feet.getZ()+.5,0,0);goal.tick();return;}
  call(t,goal);
 }
}
