package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DoorwayClearanceGameTests {
 @GameTest(template="empty",batch="construction_leaf_door",timeoutTicks=2400)
 public static void builderClearsSurveyedLeavesBeforeEnteringRoofScaffolds(GameTestHelper h){run(h,0);}
 @GameTest(template="empty",batch="construction_leaf_door",timeoutTicks=2400)
 public static void builderDoesNotClearStoneReplacingSurveyedDoorLeaves(GameTestHelper h){run(h,1);}
 @GameTest(template="empty",batch="construction_leaf_door",timeoutTicks=2400)
 public static void builderDoesNotClearUnsurveyedDoorLeaves(GameTestHelper h){run(h,2);}
 @GameTest(template="empty",batch="construction_leaf_roof",timeoutTicks=2400)
 public static void builderReachesInteriorColumnAndPlacesPaidRoofAfterClearingDoorway(GameTestHelper h){run(h,3);}
 @GameTest(template="empty",batch="construction_leaf_apron",timeoutTicks=2400)
 public static void builderClearsNaturalLeafApproachBetweenEntrancePillars(GameTestHelper h){run(h,4);}
 private static void run(GameTestHelper h,int boundary){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+180224,160,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-28,8,-9,8);
  for(int x=-28;x<=8;x++)for(int z=-9;z<=8;z++)for(int y=0;y<=11;y++){
   boolean wall=(Math.abs(x)==3&&Math.abs(z)<=3||Math.abs(z)==3&&Math.abs(x)<=3)&&y>0&&y<=5;
   l.setBlock(base.offset(x,y,z),(y==0||wall?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var door=base.offset(0,1,-3);var leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
  l.setBlock(door,boundary==1?Blocks.STONE.defaultBlockState():leaves,2);l.setBlock(door.above(),boundary==1?Blocks.STONE.defaultBlockState():leaves,2);
  if(boundary==4){for(int y=1;y<=3;y++){for(int x:new int[]{-2,2})l.setBlock(base.offset(x,y,-4),Blocks.STONE.defaultBlockState(),2);for(int x=-2;x<=2;x++)l.setBlock(base.offset(x,y,-5),Blocks.OAK_LEAVES.defaultBlockState(),2);}}
  if(boundary==4){l.setBlock(base.offset(1,1,-6),leaves,2);l.setBlock(base.offset(-1,1,-6),Blocks.OAK_LOG.defaultBlockState(),2);}
  for(int y=1;y<=6;y++)l.setBlock(base.offset(0,y,0),VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(-24,0,-7));SettlementData.get(l.getServer()).add(e);
  var stockPos=LogisticsRoutes.position(e,hall);l.setBlock(stockPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var stock=LogisticsRoutes.chest(l,e,hall);stock.setItem(0,new ItemStack(Items.COBBLESTONE));stock.setItem(1,new ItemStack(Items.OAK_DOOR));
  var ops=new ListTag();var roof=op(base.offset(1,8,0),Blocks.AIR.defaultBlockState(),Blocks.COBBLESTONE.defaultBlockState());roof.putString("item","minecraft:cobblestone");roof.putLong("stand",base.offset(0,6,0).asLong());roof.putInt("standBase",base.getY()+1);ops.add(roof);
  var surveyed=boundary==2?Blocks.AIR.defaultBlockState():leaves;var lower=op(door,surveyed,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,net.minecraft.core.Direction.SOUTH));lower.putString("item","minecraft:oak_door");ops.add(lower);ops.add(op(door.above(),surveyed,Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,net.minecraft.core.Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER)));
  var project=new CompoundTag();var id=UUID.randomUUID();project.putInt("schema",2);project.putString("kind","building");project.putUUID("id",id);project.putUUID("project",id);project.putString("design","home");project.putLong("origin",base.asLong());project.putLong("hatch",base.asLong());project.putBoolean("noHatch",true);project.put("ops",ops);project.put("cargo",new ListTag());var cost=new CompoundTag();cost.putInt("minecraft:cobblestone",1);cost.putInt("minecraft:oak_door",1);project.put("cost",cost);HallUpgradeGoal.enqueue(l,e,project);
  var npc=VillageAstra.RESIDENT.get().create(l);var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(resident,home);s.assign(resident.id(),Profession.BUILDER,hall.id());npc.bind(s.id(),resident);npc.moveTo(base.getX()+(boundary==4?-3.5:.5),base.getY()+1,base.getZ()-5.5);npc.onlyGoals(g->false,5,new HallUpgradeGoal(npc,true));
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(npc.blockPosition()),"Builder chunk ready")).thenExecute(()->l.addFreshEntity(npc));
  h.onEachTick(()->{if(boundary==0&&npc.tickCount>0&&npc.tickCount%200==0){var hit=l.clip(new net.minecraft.world.level.ClipContext(npc.getEyePosition(),door.above().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,npc));com.mojang.logging.LogUtils.getLogger().info("DOOR_CLEAR_TEST pos={} ground={} eye={} hit={} lower={} upper={} goals={}",npc.position(),npc.onGround(),npc.getEyePosition(),hit.getBlockPos(),l.getBlockState(door),l.getBlockState(door.above()),npc.runningGoals());}if((boundary==1||boundary==2)&&npc.tickCount>=200){h.assertTrue(l.getBlockState(door).is(boundary==1?Blocks.STONE:Blocks.OAK_LEAVES)&&l.getBlockState(door.above()).is(boundary==1?Blocks.STONE:Blocks.OAK_LEAVES),"Clearance must preserve changed stone and unsurveyed leaves");npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);h.succeed();return;}if(l.getBlockState(door).isAir()&&l.getBlockState(door.above()).isAir()&&npc.getZ()>base.getZ()-2&&npc.getZ()<base.getZ()+3&&(boundary!=3||l.getBlockState(base.offset(1,8,0)).is(Blocks.COBBLESTONE))){h.assertTrue(l.getBlockState(door.west()).is(Blocks.STONE),"Only surveyed doorway leaves are cleared");h.assertTrue(npc.getHealth()==npc.getMaxHealth(),"Builder enters safely");if(boundary==4){h.assertTrue(l.getBlockState(base.offset(1,1,-6)).equals(leaves)&&l.getBlockState(base.offset(-1,1,-6)).is(Blocks.OAK_LOG),"Entrance clearance preserves persistent foliage and logs");h.assertTrue(l.getBlockState(base.offset(-2,1,-4)).is(Blocks.STONE)&&l.getBlockState(base.offset(2,1,-4)).is(Blocks.STONE),"Entrance pillars remain intact");}var saved=HallUpgradeGoal.inspect(l,s.id());int paidDoors=0;for(var raw:saved.getList("cargo",Tag.TAG_COMPOUND)){var item=ItemStack.of((CompoundTag)raw);if(item.is(Items.OAK_DOOR))paidDoors+=item.getCount();}h.assertTrue(paidDoors==1&&saved.getCompound("cost").getInt("minecraft:oak_door")==1&&!saved.getList("ops",Tag.TAG_COMPOUND).getCompound(1).getBoolean("done"),"Door remains paid and uninstalled after clearance");h.assertTrue(org.villageastra.persistence.WorldJournal.exists(l,Settlement.childId(id,"doorway-clear/1"))&&org.villageastra.persistence.WorldJournal.exists(l,Settlement.childId(id,"doorway-clear/2")),"Both leaf removals have durable operation identities");h.assertTrue(boundary==4||!new DoorwayClearance().tick(npc,saved,id,BuildingOrders.REACH_SQ),"Reloaded clearance does not repeat already removed leaves");npc.discard();SettlementData.get(l.getServer()).remove(s.id());PhysicalFixtureChunks.release(l,held);h.succeed();}});
  h.runAtTickTime(2200,()->h.assertTrue(false,"Builder did not clear and physically enter doorway: "+npc.position()+" ticks="+npc.tickCount+" "+npc.workStatus()));
 }
 private static CompoundTag op(BlockPos p,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after){var t=new CompoundTag();t.putLong("pos",p.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));return t;}
}
