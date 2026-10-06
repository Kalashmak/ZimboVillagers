package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineReturnGameTests {
 private static final net.minecraft.server.level.TicketType<UUID> RETURN_TICKET=net.minecraft.server.level.TicketType.create("zimbovillagers_mine_return",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="mine_return",timeoutTicks=2400)
 public static void surfaceWorkerReturnsThroughEntranceAndMinesDeepGallery(GameTestHelper h){
  returning(h,25,false);
 }
 @GameTest(template="empty",batch="mine_return_shallow",timeoutTicks=2400)
 public static void surfaceWorkerReturnsThroughEntranceAndMinesFirstGallery(GameTestHelper h){
  returning(h,0,false);
 }
 @GameTest(template="empty",batch="mine_return_between",timeoutTicks=2400)
 public static void workerLeavesLowerBranchBeforeReturningToFirstGallery(GameTestHelper h){
  returning(h,0,true);
 }
 private static void returning(GameTestHelper h,int floor,boolean lowerBranch){
  var l=h.getLevel();var corner=h.absolutePos(new BlockPos(5,0,5));var base=new BlockPos(corner.getX(),64,corner.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();var ticketOwner=UUID.randomUUID();
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+25)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+36)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(RETURN_TICKET,cp,3,ticketOwner);forced.add(cp);l.getChunk(x,z);
  }
  // Entity-ticking readiness also depends on the surrounding full chunks.
  // Complete their loading before the accelerated test's tick deadline starts.
  for(int x=((base.getX()-3)>>4)-2;x<=((base.getX()+25)>>4)+2;x++)for(int z=((base.getZ()-3)>>4)-2;z<=((base.getZ()+36)>>4)+2;z++)l.getChunk(x,z);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);s.addBuilding(mine);s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",-20,0,0));
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  for(int x=-2;x<=24;x++)for(int z=-2;z<=35;z++)for(int y=-34;y<=12;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  BuildingPlacement.layout("mine",base,0).forEach((p,b)->l.setBlock(p,b,2));
  var state=MineWork.read(l,mine);state.putInt("width",3);state.putInt("height",5);state.putInt("descent",7);state.putInt("floorStep",25);state.putInt("step",26);state.putInt("cell",1);state.putInt("side",0);state.putInt("run",16);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());state.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
  if(floor<25){state.putInt("prospectFloor",floor);state.putInt("prospectLimit",25);state.putInt("floorStep",floor);state.putInt("step",floor+1);state.putInt("extentStep",25);state.putInt("stairAudit",26);}
  var shape=MineWork.shape(state);
  for(int step=0;step<=25;step++){
   for(int cell=0;cell<15;cell++){var at=MineDrive.next(new MineDrive.Drive(step,cell,0,0),25,shape).cell();l.setBlock(MineWork.at(e,mine,at),Blocks.AIR.defaultBlockState(),2);}
   for(var cell:MineDrive.stairs(step,shape))l.setBlock(MineWork.at(e,mine,cell),Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH),2);
  }
  for(int x=5;x<=21;x++)for(int y=-7-floor;y<=-3-floor;y++)l.setBlock(base.offset(x,y,7+floor),Blocks.AIR.defaultBlockState(),2);
  var target=base.offset(21,-4-floor,7+floor);l.setBlock(target,Blocks.STONE.defaultBlockState(),2);MineWork.write(l,mine,state);s.noteMine(mine.id(),26,3,5,7);
  if(lowerBranch){for(int x=5;x<=21;x++)for(int y=-19;y<=-15;y++)l.setBlock(base.offset(x,y,19),Blocks.AIR.defaultBlockState(),2);s.noteMine(mine.id(),new MineArea.Gallery(12,MineDrive.EAST,17));}
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(r.id()));
  npc.moveTo(base.getX()+20.5,base.getY()+1,base.getZ()+7.5+floor);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);
  if(lowerBranch)npc.moveTo(base.getX()+20.5,base.getY()-19,base.getZ()+19.5);
  npc.goalSelector.addGoal(3,new ResidentDoorGoal(npc));npc.goalSelector.addGoal(1,new SafeDescentGoal(npc));npc.goalSelector.addGoal(1,new PitEscapeGoal(npc));npc.goalSelector.addGoal(6,new ResourceWorkGoal(npc,true,()->6000));
  // Own tickets cannot be released by a neighbouring fixture's force-load
  // cleanup. Verify actual entity ticks before testing the physical journey.
  h.assertTrue(l.addFreshEntity(npc),"The returning worker is registered");
  h.startSequence().thenWaitUntil(()->h.assertTrue(npc.tickCount>0,"Waiting for the registered worker to tick"));
  h.onEachTick(()->{l.resetEmptyTime();if(l.getBlockState(target).isAir()){
   h.assertTrue(npc.getY()<base.getY()-5-floor,"The worker physically descended before mining");npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.getChunkSource().removeRegionTicket(RETURN_TICKET,cp,3,ticketOwner);h.succeed();
  }});
  h.runAtTickTime(2200,()->h.assertTrue(false,"Worker did not return to its deep face: "+npc.position()+" ticks="+npc.tickCount+" registered="+(l.getEntity(npc.getUUID())==npc)+" ticking="+l.isPositionEntityTicking(npc.blockPosition())+" removed="+npc.isRemoved()+" noAi="+npc.isNoAi()+" status="+npc.workStatus()+" goals="+npc.runningGoals()+" path="+(npc.getNavigation().getPath()==null?null:npc.getNavigation().getPath().getTarget())));
 }
}
