package org.villageastra.gametest;
import java.util.*;
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
public final class MinePlugGameTests {
 private static ResearchV2Town.Town town(GameTestHelper h){
  var t=ResearchV2Town.town(h,"mine");var data=org.villageastra.server.SettlementData.get(t.l.getServer());data.remove(t.s.id());
  var e=new org.villageastra.server.SettlementData.Entry(t.s,t.e.dimension(),t.e.center().above(16));data.add(e);ResearchV2Town.lay(t.l,e,t.shop,"mine");
  t.l.setBlock(e.center().offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);return new ResearchV2Town.Town(t.l,e,t.s,t.shop);
 }
 private static BlockPos spot(ResearchV2Town.Town t){t.s.noteMine(t.shop.id(),1,3,5,7);return BuildingPlacement.at(t.e,t.shop,4,-8,8);}
 private static void fill(ResearchV2Town.Town t,BlockPos p,UUID id){t.l.setBlock(p,Blocks.WATER.defaultBlockState(),2);if(!WorldJournal.place(t.l,id,p,Blocks.WATER.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState()))throw new IllegalStateException("Fixture fill failed");}
 @GameTest(template="empty",batch="mine_plugs",timeoutTicks=200)
 public static void provenanceSurvivesReloadAndEndsAfterHarvest(GameTestHelper h){
  var t=town(h);var p=spot(t);var id=UUID.randomUUID();
  try{
   MinePlugs.plan(t.l,t.shop,p,id);h.assertTrue(!MinePlugs.owns(t.l,t.shop,p),"An unpaid/uncommitted intent is not ownership");fill(t,p,id);MinePlugs.reload(t.l);
   h.assertTrue(MinePlugs.owns(t.l,t.shop,p),"Committed plug survives an index reload");
   var other=new Settlement.Building(UUID.randomUUID(),"mine",t.shop.x(),t.shop.y(),t.shop.z());h.assertTrue(!MinePlugs.owns(t.l,other,p),"Another mine cannot claim this receipt");
   var harvest=UUID.randomUUID();var loot=WorldJournal.harvest(t.l,harvest,p,Blocks.COBBLESTONE.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));h.assertTrue(loot!=null&&loot.stream().mapToInt(ItemStack::getCount).sum()==1,"One normal paid block yields one mined block");
   t.l.setBlock(p,Blocks.COBBLESTONE.defaultBlockState(),2);MinePlugs.reload(t.l);h.assertTrue(!MinePlugs.owns(t.l,t.shop,p),"Old receipt does not authorize replacement cobblestone after harvest");
   var replacement=UUID.randomUUID();MinePlugs.plan(t.l,t.shop,p,replacement);fill(t,p,replacement);
   WorldJournal.harvest(t.l,harvest,p,Blocks.COBBLESTONE.defaultBlockState(),new ItemStack(Items.STONE_PICKAXE));MinePlugs.reload(t.l);
   h.assertTrue(MinePlugs.owns(t.l,t.shop,p)&&t.l.getBlockState(p).is(Blocks.COBBLESTONE),"Replaying a previous harvest leaves a newly paid plug and its provenance intact");
   var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(t.l,p,t.l.getBlockState(p),net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(t.l));event.setCanceled(true);MinePlugs.broken(event);
   h.assertTrue(MinePlugs.owns(t.l,t.shop,p),"Cancelled player action retains provenance");event.setCanceled(false);MinePlugs.broken(event);MinePlugs.reload(t.l);
   h.assertTrue(!MinePlugs.owns(t.l,t.shop,p),"Permitted player replacement cannot inherit old provenance");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_plugs",timeoutTicks=200)
 public static void legacyImportRequiresWaterReceiptAndClaimedDrive(GameTestHelper h){
  var t=town(h);var p=spot(t);var outside=p.offset(30,0,0);
  try{
   fill(t,p,UUID.randomUUID());fill(t,p.south(),UUID.randomUUID());fill(t,outside,UUID.randomUUID());t.l.setBlock(p.west(),Blocks.COBBLESTONE.defaultBlockState(),2);
   h.assertTrue(MinePlugs.owns(t.l,t.shop,p),"AD-171 committed water fill in the existing drive is recovered");
   h.assertTrue(MinePlugs.owns(t.l,t.shop,p.south()),"A paid plug in the next face beside the claimed drive is recovered too");
   h.assertTrue(!MinePlugs.owns(t.l,t.shop,p.west())&&!MinePlugs.owns(t.l,t.shop,outside),"Plain construction and fills outside this drive remain forbidden");
   MinePlugs.reload(t.l);h.assertTrue(MinePlugs.owns(t.l,t.shop,p),"Migration persists without rescanning history");
   t.l.setBlock(p,Blocks.COBBLED_DEEPSLATE.defaultBlockState(),2);h.assertTrue(!MinePlugs.owns(t.l,t.shop,p),"Receipt must match the actual block");
   var offset=p.south().subtract(t.e.center());t.s.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",offset.getX(),offset.getY(),offset.getZ()));
   h.assertTrue(!MinePlugs.owns(t.l,t.shop,p.south()),"Even a recorded plug cannot bypass another building's protection");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="mine_plugs",timeoutTicks=200)
 public static void normalGoalExcavatesItsRecoveredPlug(GameTestHelper h){excavate(h,true);}
 @GameTest(template="empty",batch="mine_dripstone",timeoutTicks=200)
 public static void normalGoalExcavatesNaturalDripstoneAndKeepsItsLoot(GameTestHelper h){excavate(h,false);}
 @GameTest(template="empty",batch="mine_dripstone",timeoutTicks=100)
 public static void recognizingCaveRockDoesNotPermitBuildingsOrFluids(GameTestHelper h){
  h.assertTrue(MineWork.diggable(Blocks.DRIPSTONE_BLOCK.defaultBlockState()),"Natural cave rock is mineable");
  for(var block:List.of(Blocks.CHEST,Blocks.OAK_LOG,Blocks.STONE_BRICKS,Blocks.COBBLESTONE,Blocks.WATER,Blocks.LAVA,Blocks.BEDROCK))
   h.assertTrue(!MineWork.diggable(block.defaultBlockState()),"Unowned structural blocks and fluids stay forbidden: "+block);
  h.succeed();
 }
 private static void excavate(GameTestHelper h,boolean plug){
  var t=town(h);var p=spot(t);var npc=VillageAstra.RESIDENT.get().create(t.l);
  try{
   for(int x=-2;x<=2;x++)for(int y=-1;y<=4;y++)for(int z=-2;z<=2;z++)t.l.setBlock(p.offset(x,y,z),y==-1?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
   if(plug)fill(t,p,UUID.randomUUID());else t.l.setBlock(p,Blocks.DRIPSTONE_BLOCK.defaultBlockState(),2);var state=MineWork.read(t.l,t.shop);state.putInt("step",1);state.putInt("cell",14);state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));for(var cell:MineDrive.stairs(0,MineWork.shape(state)))t.l.setBlock(MineWork.at(t.e,t.shop,cell),Blocks.COBBLESTONE_STAIRS.defaultBlockState(),2);MineWork.write(t.l,t.shop,state);
   var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,Settlement.childId(t.s.id(),"home"));t.s.assign(r.id(),Profession.MINER,t.shop.id());npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);t.l.addFreshEntity(npc);
   var access=BuildingPlacement.at(t.e,t.shop,3,-7,7);t.l.setBlock(access.below(),Blocks.STONE.defaultBlockState(),2);npc.moveTo(access.getX()+.5,access.getY(),access.getZ()+.5);npc.setOnGround(true);
   var goal=new ResourceWorkGoal(npc,true,()->6000L);h.assertTrue(goal.canUse(),"Normal miner resumes");goal.tick();
   h.assertTrue(MineWork.read(t.l,t.shop).getString("stage").equals("dig"),"Owned plug or natural cave rock is selected, not unsafe_ground");
   for(int n=0;n<120&&!t.l.getBlockState(p).isAir();n++)goal.tick();
   h.assertTrue(t.l.getBlockState(p).isAir(),"Actual mining goal removes the block with a pickaxe");
   h.assertTrue(!MinePlugs.owns(t.l,t.shop,p),"Consumed provenance is retired");
   var done=MineWork.read(t.l,t.shop);var expected=plug?Items.COBBLESTONE:Items.DRIPSTONE_BLOCK;
   h.assertTrue(done.getList("cargo",Tag.TAG_COMPOUND).stream().map(raw->ItemStack.of((CompoundTag)raw)).filter(stack->stack.is(expected)).mapToInt(ItemStack::getCount).sum()==1,"Exactly one actual block is carried");
   h.assertTrue(ItemStack.of(done.getCompound("tool")).getDamageValue()==1,"Mining consumes one unit of tool durability");
  }finally{npc.discard();ResearchV2Town.done(t);}h.succeed();
 }
}
