package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** A paid village job survives an unreachable baker; a second real body finishes it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class HandBreadHandoverGameTests {
 @GameTest(template="empty",batch="bread_handover",timeoutTicks=4800)
 public static void unreachableBakerHandsPaidBreadToAReachableResident(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+180224,90,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();var owner=UUID.randomUUID();var ticket=net.minecraft.server.level.TicketType.<UUID>create("zimbovillagers_bread_handover_fixture",Comparator.naturalOrder());
  for(int x=(base.getX()-3)>>4;x<=(base.getX()+20)>>4;x++)for(int z=(base.getZ()-3)>>4;z<=(base.getZ()+10)>>4;z++){var cp=new net.minecraft.world.level.ChunkPos(x,z);l.getChunkSource().addRegionTicket(ticket,cp,3,owner);held.add(cp);l.getChunk(x,z);}
  for(int x=((base.getX()-3)>>4)-2;x<=((base.getX()+20)>>4)+2;x++)for(int z=((base.getZ()-3)>>4)-2;z<=((base.getZ()+10)>>4)+2;z++)l.getChunk(x,z);
  for(var p:BlockPos.betweenClosed(base.offset(-3,0,-3),base.offset(20,12,10)))l.setBlock(p,p.getY()==base.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  for(int y=1;y<=8;y++)l.setBlock(base.offset(14,y,4),Blocks.STONE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);l.setBlock(LogisticsRoutes.position(e,hall),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.WHEAT,5));
  var actors=new ArrayList<ResidentEntity>();for(int n=0;n<2;n++){var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,false,n==0?Profession.BUILDER:Profession.MAYOR,null,-1);s.admit(r,home);npc.bind(s.id(),r);npc.moveTo(base.getX()+(n==0?14.5:2.5),base.getY()+(n==0?9:1),base.getZ()+4.5);npc.setOnGround(true);npc.onlyGoals(g->false,6,new HandBreadGoal(npc,true,()->0L));npc.targetSelector.removeAllGoals(g->true);actors.add(npc);}
  long now=l.getGameTime();for(int n=0;n<4&&!HandBread.inspect(l,s.id()).getString("stage").equals("work");n++)HandBread.advance(l,e,actors.get(0).getUUID(),now+n*HandBread.TURN);
  var paid=HandBread.inspect(l,s.id());h.assertTrue(paid.getString("stage").equals("work")&&paid.getLong("labor")==0&&paid.getInt("paid")==5,"Fixture has one paid job and no fictional baking labor");var job=paid.getUUID("id");boolean[] second={false};
  h.startSequence().thenWaitUntil(()->h.assertTrue(l.isPositionEntityTicking(base),"Both workers' chunks tick")).thenExecute(()->h.assertTrue(l.addFreshEntity(actors.get(0)),"Unreachable baker registered first"));
  h.onEachTick(()->{l.resetEmptyTime();var stranded=actors.get(0);if(!second[0]&&stranded.tickCount>=10){h.assertTrue(HandBread.holds(l,s.id(),stranded.getUUID(),l.getGameTime()),"Unreachable worker initially has the real claim");h.assertTrue(l.addFreshEntity(actors.get(1)),"Reachable baker registered second");second[0]=true;}
   var t=HandBread.inspect(l,s.id());h.assertTrue(job.equals(t.getUUID("id")),"Handover retains the paid village job");h.assertTrue(stranded.getHealth()==stranded.getMaxHealth(),"No forced fall off the pillar");h.assertTrue(stranded.tickCount<4000,"Unreachable baker keeps the claim forever: stage="+t.getString("stage")+" labor="+t.getLong("labor")+" goals="+stranded.runningGoals());
   if(t.getString("stage").equals("idle")){h.assertTrue(second[0]&&chest.countItem(Items.BREAD)==2&&chest.countItem(Items.WHEAT)==0,"Reachable body produces exactly two bread from the five already paid wheat");h.assertTrue(t.getLong("labor")==t.getLong("needLabor")&&actors.get(1).getUUID().equals(t.getUUID("lastBaker")),"Second baker physically pays all remaining labor");actors.forEach(ResidentEntity::discard);HandBread.release(l,s.id(),stranded.getUUID());HandBread.release(l,s.id(),actors.get(1).getUUID());SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.getChunkSource().removeRegionTicket(ticket,cp,3,owner);h.succeed();}
  });
 }
}
