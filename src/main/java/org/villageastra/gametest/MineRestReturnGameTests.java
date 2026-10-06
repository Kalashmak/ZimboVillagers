package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** A patient must physically leave the real shaft before home rest can heal them. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MineRestReturnGameTests {
 private static final net.minecraft.server.level.TicketType<UUID> TICKET=net.minecraft.server.level.TicketType.create("zimbovillagers_mine_rest",Comparator.<UUID>naturalOrder());
 @GameTest(template="empty",batch="mine_rest_return",timeoutTicks=1800)
 public static void sickMinerLeavesHisShaftToRecoverAtHome(GameTestHelper h){
  var l=h.getLevel();var corner=h.absolutePos(new BlockPos(5,0,5));var base=new BlockPos(corner.getX(),64,corner.getZ());var owner=UUID.randomUUID();var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-24)>>4;x<=(base.getX()+12)>>4;x++)for(int z=(base.getZ()-6)>>4;z<=(base.getZ()+12)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(TICKET,cp,3,owner);forced.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-24)>>4)-2;x<=((base.getX()+12)>>4)+2;x++)for(int z=((base.getZ()-6)>>4)-2;z<=((base.getZ()+12)>>4)+2;z++)l.getChunk(x,z);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var mine=new Settlement.Building(UUID.randomUUID(),"mine",0,0,0);var home=new Settlement.Building(UUID.randomUUID(),"home",-20,0,0);s.addBuilding(mine);s.addBuilding(home);s.addHome(new Settlement.Home(home.id(),1,2,true));SettlementData.get(l.getServer()).add(e);
  for(int x=-24;x<=12;x++)for(int z=-6;z<=12;z++)for(int y=-10;y<=10;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  BuildingPlacement.layout("mine",base,0).forEach((p,b)->l.setBlock(p,b,2));
  var work=MineWork.read(l,mine);work.putInt("descent",7);work.putInt("step",1);work.putInt("extentStep",1);MineWork.write(l,mine,work);s.noteMine(mine.id(),1,3,5,7);
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());s.assign(r.id(),Profession.MINER,mine.id());r.fallIll();npc.bind(s.id(),r);npc.moveTo(base.getX()+3.5,base.getY()-5,base.getZ()+6.5);npc.onlyGoals(g->g instanceof SafeDescentGoal||g instanceof PitEscapeGoal||g instanceof ResidentDoorGoal,5,new PatientGoal(npc));h.assertTrue(l.addFreshEntity(npc),"Actual sick miner registered");var anchor=HomeNeighborhood.anchor(npc);
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.getChunkSource().removeRegionTicket(TICKET,cp,3,owner);};
  h.onEachTick(()->{l.resetEmptyTime();if(npc.tickCount>0&&npc.blockPosition().distSqr(anchor)<=16&&npc.getY()>=base.getY()){h.assertTrue(r.sick(),"Return did not manufacture a cure");clean.run();h.succeed();}});
  h.runAtTickTime(1600,()->{String why="Sick miner did not reach home: "+npc.position()+" home="+anchor+" ticks="+npc.tickCount+" status="+npc.workStatus()+" path="+(npc.getNavigation().getPath()==null?null:npc.getNavigation().getPath().getTarget());clean.run();h.assertTrue(false,why);});
 }
}
