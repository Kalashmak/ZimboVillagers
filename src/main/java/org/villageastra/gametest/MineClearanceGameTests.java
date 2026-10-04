package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineClearanceGameTests {
 @GameTest(template="empty",batch="mine_clearance",timeoutTicks=200)
 public static void minerClearsRefilledCompletedStairBeforeResumingFace(GameTestHelper h){
  run(h,"clear");
 }
 @GameTest(template="empty",batch="mine_clearance",timeoutTicks=200)
 public static void fallenSandReceiptRecoversCargoAndToolOnceAfterCrash(GameTestHelper h){run(h,"crash");}
 @GameTest(template="empty",batch="mine_clearance",timeoutTicks=200)
 public static void maintenanceLeavesWallsWetSandAndUnexcavatedTerrainAlone(GameTestHelper h){run(h,"limits");}
 @GameTest(template="empty",batch="mine_clearance",timeoutTicks=200)
 public static void pickBrokenByMaintenanceCannotDestroyTheQueuedStone(GameTestHelper h){run(h,"broken");}
 @GameTest(template="empty",batch="gallery_clearance",timeoutTicks=200)
 public static void aCompletedGalleryIsClearedBeforeItsNextFace(GameTestHelper h){gallery(h,"clear");}
 @GameTest(template="empty",batch="gallery_clearance",timeoutTicks=200)
 public static void galleryMaintenanceDoesNotExcavateTheUnfinishedColumnOrWetWall(GameTestHelper h){gallery(h,"limits");}
 @GameTest(template="empty",batch="gallery_clearance",timeoutTicks=200)
 public static void galleryMaintenanceReceiptRecoversTheSameCargoOnce(GameTestHelper h){gallery(h,"crash");}
 private static void gallery(GameTestHelper h,String mode){var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());var e=new SettlementData.Entry(old.s,old.e.dimension(),old.e.center().above(80));data.add(e);ResearchV2Town.lay(old.l,e,old.shop,"mine");var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{var s=MineWork.read(t.l,t.shop);s.putInt("step",1);s.putInt("floorStep",0);s.putInt("run",3);s.putInt("side",MineDrive.EAST);s.putInt("cell",0);s.putInt("width",3);s.putInt("height",5);s.putInt("descent",7);s.putString("mineStage","EAST");s.putString("stage","dig");s.putUUID("operation",UUID.randomUUID());s.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
   t.s.noteMine(t.shop.id(),4,3,5,7);t.s.noteMine(t.shop.id(),new MineArea.Gallery(0,MineDrive.EAST,4));
   for(int x=2;x<=9;x++)for(int z=6;z<=8;z++)for(int y=-8;y<=-1;y++)t.l.setBlock(BuildingPlacement.at(t.e,t.shop,x,y,z),(y==-8||z!=7||x>=8?Blocks.SANDSTONE:Blocks.AIR).defaultBlockState(),2);
   var target=BuildingPlacement.at(t.e,t.shop,8,-3,7);s.putLong("target",target.asLong());s.put("before",NbtUtils.writeBlockState(Blocks.SANDSTONE.defaultBlockState()));var obstruction=BuildingPlacement.at(t.e,t.shop,5,-6,7);t.l.setBlock(obstruction.below(),Blocks.SANDSTONE.defaultBlockState(),2);t.l.setBlock(obstruction,Blocks.SAND.defaultBlockState(),2);
   var stand=BuildingPlacement.at(t.e,t.shop,4,-7,7);npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);npc.setOnGround(true);
   if(mode.equals("limits")){t.l.setBlock(obstruction,Blocks.AIR.defaultBlockState(),2);t.l.setBlock(target,Blocks.SAND.defaultBlockState(),2);h.assertTrue(!MineClearance.tick(npc,t.e,t.shop,s),"A claimed but unfinished column is not maintenance");t.l.setBlock(obstruction,Blocks.SAND.defaultBlockState(),2);t.l.setBlock(obstruction.north(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!MineClearance.tick(npc,t.e,t.shop,s),"Gallery clearance does not open water boundaries");h.succeed();return;}
   h.assertTrue(MineClearance.tick(npc,t.e,t.shop,s)&&MineClearance.active(s),"Fallen sand in a completed gallery begins real maintenance");
   if(mode.equals("crash")){var job=s.getCompound("mineClearance");h.assertTrue(org.villageastra.persistence.WorldJournal.harvest(t.l,job.getUUID("id"),obstruction,Blocks.SAND.defaultBlockState(),ItemStack.of(s.getCompound("tool")))!=null,"Harvest committed before worker checkpoint");MineClearance.reconcile(t.l,s);MineClearance.reconcile(t.l,s);}else for(int i=0;i<80&&MineClearance.active(s);i++)MineClearance.tick(npc,t.e,t.shop,s);
   h.assertTrue(t.l.getBlockState(obstruction).isAir()&&t.l.getBlockState(target).is(Blocks.SANDSTONE),"Only the fallen obstruction is removed");h.assertTrue(ForestFixture.count(s.getList("cargo",Tag.TAG_COMPOUND),Items.SAND)==1&&ItemStack.of(s.getCompound("tool")).getDamageValue()==1,"One real sand and one wear, including replay");h.assertTrue(s.getInt("run")==3&&s.getInt("cell")==0,"Maintenance does not advance excavation");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
 private static void run(GameTestHelper h,String mode){
  var old=ResearchV2Town.town(h,"mine");var data=SettlementData.get(old.l.getServer());data.remove(old.s.id());
  var e=new SettlementData.Entry(old.s,old.e.dimension(),old.e.center().above(80));data.add(e);ResearchV2Town.lay(old.l,e,old.shop,"mine");
  var t=new ResearchV2Town.Town(old.l,e,old.s,old.shop);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   var s=MineWork.read(t.l,t.shop);s.putInt("step",3);s.putInt("extentStep",2);s.putInt("cell",10);s.putInt("width",3);s.putInt("height",5);s.putInt("descent",7);
   for(int row=0;row<=3;row++)for(int x=2;x<=4;x++)for(int dy=0;dy<5;dy++)t.l.setBlock(BuildingPlacement.at(e,t.shop,x,-row-7+dy,7+row),(dy==0?Blocks.SANDSTONE:Blocks.AIR).defaultBlockState(),2);
   var target=BuildingPlacement.at(e,t.shop,3,-9,10);t.l.setBlock(target,Blocks.SANDSTONE.defaultBlockState(),2);
   var obstruction=BuildingPlacement.at(e,t.shop,3,-8,9);t.l.setBlock(obstruction,Blocks.SAND.defaultBlockState(),2);
   s.putUUID("operation",UUID.randomUUID());s.putString("stage","dig");s.putLong("target",target.asLong());s.put("before",NbtUtils.writeBlockState(Blocks.SANDSTONE.defaultBlockState()));s.putIntArray("access",new int[]{3,-9,9});s.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));MineWork.write(t.l,t.shop,s);
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
   if(mode.equals("broken")){var tool=ItemStack.of(s.getCompound("tool"));tool.setDamageValue(tool.getMaxDamage()-1);s.put("tool",tool.save(new CompoundTag()));MineWork.write(t.l,t.shop,s);}
   var stand=BuildingPlacement.at(e,t.shop,3,-6,7);npc.moveTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);npc.setOnGround(true);
   if(mode.equals("limits")){
    t.l.setBlock(obstruction,Blocks.SANDSTONE.defaultBlockState(),2);
    h.assertTrue(!MineClearance.tick(npc,e,t.shop,s),"Maintenance cannot mine structural stone");
    t.l.setBlock(obstruction,Blocks.SAND.defaultBlockState(),2);t.l.setBlock(obstruction.east(),Blocks.WATER.defaultBlockState(),2);
    h.assertTrue(!MineClearance.tick(npc,e,t.shop,s),"Water-adjacent sand stays sealed");t.l.setBlock(obstruction.east(),Blocks.AIR.defaultBlockState(),2);
    var screen=stand.south();for(int y=-3;y<=2;y++)t.l.setBlock(screen.above(y),Blocks.STONE_BRICKS.defaultBlockState(),2);
    h.assertTrue(!MineClearance.tick(npc,e,t.shop,s),"Cannot clear through a solid wall");for(int y=-3;y<=2;y++)t.l.setBlock(screen.above(y),Blocks.AIR.defaultBlockState(),2);
    t.l.setBlock(obstruction,Blocks.AIR.defaultBlockState(),2);t.l.setBlock(target,Blocks.SAND.defaultBlockState(),2);
    npc.moveTo(target.getX()+.5,target.getY()+2,target.getZ()-1.5);
    h.assertTrue(!MineClearance.tick(npc,e,t.shop,s),"Unexcavated current row belongs to the normal drive");
    h.succeed();return;
   }
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Miner resumes saved face");
   if(mode.equals("crash")){
    goal.tick();var prepared=MineWork.read(t.l,t.shop);h.assertTrue(MineClearance.active(prepared),"Maintenance intent is durable before breaking");
    var job=prepared.getCompound("mineClearance");h.assertTrue(org.villageastra.persistence.WorldJournal.harvest(t.l,job.getUUID("id"),obstruction,Blocks.SAND.defaultBlockState(),ItemStack.of(prepared.getCompound("tool")))!=null,"Committed harvest before worker checkpoint");
    var custody=JobCargo.snapshot(npc,true);h.assertTrue(ForestFixture.count(custody.items(),Items.SAND)==1,"Death custody recovers the real fallen sand");
    goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Reload pending maintenance");goal.tick();
    var recovered=MineWork.read(t.l,t.shop);MineClearance.reconcile(t.l,recovered);MineWork.write(t.l,t.shop,recovered);
    h.assertTrue(recovered.getInt("step")==3&&recovered.getInt("cell")==10,"Maintenance does not advance the pending excavation face");
    h.assertTrue(ItemStack.of(recovered.getCompound("tool")).getDamageValue()==1,"One durable harvest, one wear after replay");
   }else for(int tick=0;tick<80;tick++){npc.tickCount++;goal.tick();}
   if(mode.equals("broken")){
    npc.moveTo(obstruction.getX()+.5,obstruction.getY(),obstruction.getZ()+.5);npc.setOnGround(true);
    for(int tick=0;tick<200;tick++){npc.tickCount++;goal.tick();}
   }
   var after=MineWork.read(t.l,t.shop);h.assertTrue(t.l.getBlockState(obstruction).isAir(),"Fallen sand must not permanently seal a completed stair");
   h.assertTrue(ForestFixture.count(after.getList("cargo",Tag.TAG_COMPOUND),Items.SAND)==1,"Cleared sand becomes one real carried item");
   if(mode.equals("broken"))h.assertTrue(ItemStack.of(after.getCompound("tool")).isEmpty()&&t.l.getBlockState(target).is(Blocks.SANDSTONE),"Broken pick requests replacement without destroying the queued resource");
   else h.assertTrue(ItemStack.of(after.getCompound("tool")).getDamageValue()>=1,"The actual pick pays wear");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
