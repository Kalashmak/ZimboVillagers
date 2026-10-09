package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DoorwayTrunkBoundaryGameTests {
 @GameTest(template="empty",batch="construction_trunk_boundaries",timeoutTicks=200)
 public static void builderPreservesStructuralLogsInTheFutureDoorway(GameTestHelper h){preserve(h,0);}
 @GameTest(template="empty",batch="construction_trunk_boundaries",timeoutTicks=200)
 public static void builderPreservesTrunksWithOnlyPersistentPlantedFoliage(GameTestHelper h){preserve(h,1);}
 @GameTest(template="empty",batch="construction_trunk_boundaries",timeoutTicks=200)
 public static void builderPreservesOtherBuildingsAtTheDoorApron(GameTestHelper h){preserve(h,2);}
 private static void preserve(GameTestHelper h,int mode){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+1048576+mode*512,120,at.getZ());
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=6;y++)l.setBlock(base.offset(x,y,z),(y==-1?Blocks.DIRT:Blocks.AIR).defaultBlockState(),2);
  var trunk=base.north();for(int y=0;y<=2;y++)l.setBlock(trunk.above(y),Blocks.OAK_LOG.defaultBlockState(),2);
  l.setBlock(trunk.above(4),Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,mode==1),2);
  var s=new Settlement(UUID.randomUUID());var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",-40,0,-40);s.addBuilding(hall);
  if(mode==2)s.addBuilding(new Settlement.Building(UUID.randomUUID(),"forester",0,0,-1));
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,1,true));var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.BUILDER,hall.id());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);SettlementData.get(l.getServer()).add(e);
  var id=UUID.randomUUID();var state=new CompoundTag();state.putUUID("id",id);state.putUUID("project",id);state.putString("kind","building");state.putString("design","home");state.putLong("origin",base.asLong());state.putBoolean("funded",true);
  var ops=new ListTag();var door=new CompoundTag();door.putLong("pos",base.above().asLong());door.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));door.put("after",NbtUtils.writeBlockState(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER)));ops.add(door);
  if(mode==0)for(int y=0;y<=2;y++){var structural=new CompoundTag();structural.putLong("pos",trunk.above(y).asLong());structural.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));structural.put("after",NbtUtils.writeBlockState(Blocks.OAK_LOG.defaultBlockState()));ops.add(structural);}
  state.put("ops",ops);npc.bind(s.id(),r);npc.moveTo(base.getX()+2.5,base.getY(),base.getZ()-1.5,0,0);npc.setOnGround(true);
  try{h.assertTrue(!new DoorwayClearance().tick(npc,state,id,BuildingOrders.REACH_SQ),"Protected trunk must not start clearance: mode="+mode);
   for(int y=0;y<=2;y++)h.assertTrue(l.getBlockState(trunk.above(y)).is(Blocks.OAK_LOG)&&!org.villageastra.persistence.WorldJournal.exists(l,Settlement.childId(id,"doorway-trunk/"+trunk.above(y).asLong())),"Protected log remains and no clearance receipt exists");
  }finally{npc.discard();SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
