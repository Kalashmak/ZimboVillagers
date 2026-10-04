package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OwnerBugsGameTests {
 @GameTest(template="empty") public static void nurseryFenceHasConnectionsBeforeWorldPlacement(GameTestHelper h){
  var fence=Nursery.fence(Nursery.DEFAULT);var corner=fence.get(new BlockPos(0,1,7));h.assertTrue(corner.getValue(FenceBlock.EAST)&&corner.getValue(FenceBlock.SOUTH),"Generated corner already connects both arms");var besideGate=fence.get(new BlockPos(3,1,7));h.assertTrue(besideGate.getValue(FenceBlock.EAST)&&besideGate.getValue(FenceBlock.WEST),"Fence connects gate and adjacent section");h.succeed();
 }
 @GameTest(template="empty") public static void hallStorageRetainsOldStacksAndLastSlotAfterReload(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));l.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=(OwnedChestEntity)l.getBlockEntity(pos);c.setItem(26,new ItemStack(Items.DIAMOND,3));c.expandHall();c.setItem(107,new ItemStack(Items.BREAD,17));var tag=c.saveWithoutMetadata();
  var loaded=new OwnedChestEntity(pos,c.getBlockState());loaded.load(tag);h.assertTrue(loaded.getContainerSize()==108&&loaded.getItem(26).getCount()==3&&loaded.getItem(107).getCount()==17,"All four chest inventories survive NBT load without moving old slots");
  h.assertTrue(org.villageastra.persistence.WorldJournal.deposit(l,UUID.randomUUID(),pos,new ItemStack(Items.SAND,64)),"Existing journal address accepts expanded stock");h.succeed();
 }
 @GameTest(template="empty") public static void hallExpansionCreatesTwoPhysicalDoubleChests(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,3,2));StarterVillage.create(l,pos);var e=SettlementData.get(l.getServer()).entries().stream().filter(v->v.center().equals(pos)).findFirst().orElseThrow();HallStorage.ensure(l,e);
  var c=(OwnedChestEntity)l.getBlockEntity(pos.offset(1,1,4));h.assertTrue(c.getContainerSize()==108,"Original registered stock sees both doubles");for(var p:List.of(pos.offset(1,1,4),pos.offset(1,1,3),pos.offset(5,1,2),pos.offset(5,1,3)))h.assertTrue(l.getBlockState(p).getValue(ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&HallStorage.menu(l,p)!=null,"Every physical half opens its stock page");
  var plan=HallUpgradeGoal.preview(l,e);for(var raw:plan.getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND)){var op=(net.minecraft.nbt.CompoundTag)raw;h.assertTrue(!HallStorage.partAt(l,BlockPos.of(op.getLong("pos"))),"Upgrading the hall preserves both stock pages");}h.succeed();
 }
 @GameTest(template="empty") public static void mineMouthFloorIsSkippedButPlayerBlocksAreNot(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));
  for(int turn=0;turn<4;turn++){var s=new org.villageastra.domain.Settlement(UUID.randomUUID());var b=new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"mine",0,0,0,turn);s.addBuilding(b);var e=new SettlementData.Entry(s,l.dimension().location().toString(),origin);var floor=BuildingPlacement.at(e,b,2,-6,7);l.setBlock(floor,Blocks.STONE_BRICKS.defaultBlockState(),2);
   h.assertTrue(MineWork.builtLining(l,e,b,floor),"Excavation skips its built floor at turn "+turn);l.setBlock(floor,Blocks.GOLD_BLOCK.defaultBlockState(),2);h.assertTrue(!MineWork.builtLining(l,e,b,floor)&&!MineWork.diggable(l.getBlockState(floor)),"Player gold remains protected from excavation");}
  h.assertTrue(MineWork.diggable(Blocks.CLAY.defaultBlockState())&&MineWork.diggable(Blocks.SAND.defaultBlockState()),"Miner accepts natural clay and sand");h.succeed();
 }
 @GameTest(template="empty") public static void bootstrapPlansGlassFromNaturalSand(GameTestHelper h){
  bootstrap(h,Items.GLASS);
 }
 @GameTest(template="empty") public static void bootstrapPlansBricksFromNaturalClay(GameTestHelper h){bootstrap(h,Items.BRICKS);}
 private static void bootstrap(GameTestHelper h,Item target){
  var l=h.getLevel();var stock=new net.minecraft.world.SimpleContainer(108);stock.setItem(0,new ItemStack(target==Items.GLASS?Items.SAND:Items.CLAY_BALL,64));stock.setItem(1,new ItemStack(Items.COAL,16));stock.setItem(2,new ItemStack(Items.COBBLESTONE,64));var want=List.of(new Workshops.Want(Ingredient.of(target),1,UUID.randomUUID()));var spec=Workshops.spec("town_hall");int fuelBank=0;
  for(int step=0;step<128&&!stock.hasAnyOf(Set.of(target));step++){var job=Workshops.plan(l,spec,stock,want);h.assertTrue(job!=null,"Complete input chain can start from natural raw materials and fuel: "+target);h.assertTrue(job.labor()>=3600,"Bootstrap production takes minutes, not instant grants");for(var in:job.inputs()){int need=in.count();for(int i=0;i<108&&need>0;i++)if(in.matches(stock.getItem(i))){int take=Math.min(need,stock.getItem(i).getCount());stock.removeItem(i,take);need-=take;}h.assertTrue(need==0,"Every intermediate consumes actual ingredients");}while(fuelBank<job.fuelTicks()){var fuel=stock.removeItem(1,1);h.assertTrue(!fuel.isEmpty(),"The chain pays for its fuel");fuelBank+=net.minecraftforge.common.ForgeHooks.getBurnTime(fuel,net.minecraft.world.item.crafting.RecipeType.SMELTING);}fuelBank-=job.fuelTicks();for(var out:job.outputs())stock.addItem(out.copy());}
  h.assertTrue(stock.hasAnyOf(Set.of(target))&&stock.countItem(target==Items.GLASS?Items.SAND:Items.CLAY_BALL)<64&&stock.countItem(Items.COAL)<16,"Chain finishes and spends natural inputs and fuel: "+target);h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=700) public static void trappedResidentActuallyClimbsOut(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(4,3,4));for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-1;y<=4;y++)l.setBlock(foot.offset(x,y,z),y<=1&&(x!=0||z!=0||y==-1)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);l.addFreshEntity(npc);h.succeedWhen(()->h.assertTrue(npc.getY()>=foot.getY()+1.9&&Math.abs(npc.getX()-foot.getX()-.5)+Math.abs(npc.getZ()-foot.getZ()-.5)>.7,"Resident climbed onto the ledge without teleporting or removing terrain"));
 }
 @GameTest(template="empty") public static void sleepingResidentLoadedDuringDayWakesUp(GameTestHelper h){
  var l=h.getLevel();long previous=l.getDayTime();l.setDayTime(1000);
  try{h.assertTrue(!SleepGoal.night(l),"Fixture is daytime");var pos=h.absolutePos(new BlockPos(4,3,4));l.setBlock(pos,Blocks.RED_BED.defaultBlockState(),2);var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(pos.getX(),pos.getY(),pos.getZ());npc.startSleeping(pos);var tag=new net.minecraft.nbt.CompoundTag();npc.saveWithoutId(tag);var restored=VillageAstra.RESIDENT.get().create(l);restored.load(tag);restored.setNoAi(true);restored.aiStep();h.assertTrue(!restored.isSleeping()&&restored.getPose()==net.minecraft.world.entity.Pose.STANDING,"Restored sleeper resets both bed state and synced pose at daytime");}
  finally{l.setDayTime(previous);}h.succeed();
 }
 @GameTest(template="empty") public static void dryPitHasAnEscapeButOrdinaryCorridorDoesNot(GameTestHelper h){
  var l=h.getLevel();var foot=h.absolutePos(new BlockPos(4,3,4));for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<=3;y++)l.setBlock(foot.offset(x,y,z),y<=1&&(x!=0||z!=0||y==-1)?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(foot.getX()+.5,foot.getY(),foot.getZ()+.5);npc.setOnGround(true);h.assertTrue(PitEscapeGoal.escape(npc)!=null,"Two-block pit has a clear ledge");
  // A one-block notch is not a walkable passage. Lay the complete two-high,
  // supported corridor beyond the bounded pit survey instead of relying on neighboring terrain.
  for(int n=1;n<=6;n++){var p=foot.north(n);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.AIR.defaultBlockState(),2);l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),2);}
  h.assertTrue(PitEscapeGoal.escape(npc)==null,"An open passage stays under normal navigation");npc.discard();h.succeed();
 }
}
