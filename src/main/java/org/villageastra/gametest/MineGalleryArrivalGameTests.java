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
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineGalleryArrivalGameTests {
 private static final net.minecraft.server.level.TicketType<UUID> TICKET=net.minecraft.server.level.TicketType.create("zimbovillagers_gallery_arrival",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="mine_gallery_arrival",timeoutTicks=2800)
 public static void minerLeavesLowerTreadForHigherSideGallery(GameTestHelper h){
  var l=h.getLevel();var corner=h.absolutePos(new BlockPos(5,0,5));var base=new BlockPos(corner.getX(),80,corner.getZ());var owner=UUID.randomUUID();var chunks=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-48)>>4;x<=(base.getX()+8)>>4;x++)for(int z=(base.getZ()+14)>>4;z<=(base.getZ()+24)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(TICKET,cp,3,owner);chunks.add(cp);}
  for(int x=((base.getX()-48)>>4)-2;x<=((base.getX()+8)>>4)+2;x++)for(int z=((base.getZ()+14)>>4)-2;z<=((base.getZ()+24)>>4)+2;z++)l.getChunk(x,z);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);s.addBuilding(mine);s.noteMine(mine.id(),25,3,5,7);
  for(int row=11;row<=14;row++)s.noteMine(mine.id(),new MineArea.Gallery(row,MineDrive.WEST,48));SettlementData.get(l.getServer()).add(e);
  for(int x=-48;x<=8;x++)for(int z=14;z<=24;z++)for(int y=-25;y<=-10;y++)l.setBlock(base.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
  for(int row=8;row<=17;row++){
   int z=7+row,y=-7-row;
   for(int x=2;x<=4;x++){l.setBlock(base.offset(x,y,z),Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH),2);for(int up=1;up<=4;up++)l.setBlock(base.offset(x,y+up,z),Blocks.AIR.defaultBlockState(),2);}
   if(row>=11&&row<=14)for(int x=-46;x<=1;x++)for(int up=0;up<3;up++)l.setBlock(base.offset(x,y+up,z),Blocks.AIR.defaultBlockState(),2);
  }
  var ore=base.offset(-43,-18,18);var stand=base.offset(-43,-19,19);l.setBlock(ore,Blocks.COAL_ORE.defaultBlockState(),2);
  var body=VillageAstra.RESIDENT.get().create(l);var r=new Resident(body.getUUID(),Resident.Life.ADULT,false,null,null,-1);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,1,true));s.admit(r,home);s.assign(r.id(),Profession.MINER,mine.id());body.bind(s.id(),r);
  var stock=LogisticsRoutes.position(e,mine);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,mine);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));var tool=WorldJournal.takeAmount(l,UUID.randomUUID(),stock,0,chest.getItem(0).copy(),1);
  var state=MineWork.read(l,mine);state.putString("stage","choose");state.putInt("descent",7);state.putInt("step",13);state.putInt("floorStep",12);state.putInt("extentStep",25);state.putUUID("worker",r.id());state.put("tool",tool.save(new CompoundTag()));
  var job=new CompoundTag();job.putUUID("id",UUID.randomUUID());job.putLong("pos",ore.asLong());job.putLong("stand",stand.asLong());job.putLong("lastProgress",l.getGameTime());job.putDouble("distance",Double.POSITIVE_INFINITY);job.put("tool",tool.save(new CompoundTag()));job.put("before",NbtUtils.writeBlockState(Blocks.COAL_ORE.defaultBlockState()));state.put("mineOre",job);MineWork.write(l,mine,state);
  body.moveTo(base.getX()+1.7,base.getY()-19,base.getZ()+20.5);body.onlyGoals(g->false,6,new ResourceWorkGoal(body,true,()->6000));h.assertTrue(l.addFreshEntity(body),"Actual worker starts at the lower gallery junction");
  Runnable clean=()->{body.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:chunks)l.getChunkSource().removeRegionTicket(TICKET,cp,3,owner);};
  h.onEachTick(()->{l.resetEmptyTime();if(l.getBlockState(ore).isAir()){var done=MineWork.read(l,mine);h.assertTrue(body.tickCount>0&&body.distanceToSqr(startPosition(base))>900,"Worker physically reaches the distant upper gallery");h.assertTrue(ForestFixture.count(done.getList("cargo",Tag.TAG_COMPOUND),Items.COAL)==1&&ItemStack.of(done.getCompound("tool")).getDamageValue()==1,"Exactly one real coal and one point of paid pick wear");com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_GALLERY_ARRIVAL physicalTicks={} pos={}",body.tickCount,body.position());clean.run();h.succeed();}});
  h.runAtTickTime(2600,()->{String why="Gallery approach stalled: body="+body.position()+" ticks="+body.tickCount+" status="+body.workStatus()+" job="+MineWork.read(l,mine).getCompound("mineOre");clean.run();h.assertTrue(false,why);});
 }
 private static net.minecraft.world.phys.Vec3 startPosition(BlockPos base){return new net.minecraft.world.phys.Vec3(base.getX()+1.7,base.getY()-19,base.getZ()+20.5);}
}
