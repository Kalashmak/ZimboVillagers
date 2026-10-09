package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class PorterRouteGameTests {
 @GameTest(template="empty",batch="porter_safe_route",timeoutTicks=2600)
 public static void paidGrainUsesSafeStepsAndPhysicallyReachesHallExactlyOnce(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+786432,120,at.getZ());var held=PhysicalFixtureChunks.force(l,base,-4,34,-6,24);
  for(int x=-4;x<=34;x++)for(int z=-6;z<=24;z++)for(int y=-1;y<=9;y++){
   int top=x<=5&&z<=18?5:x==6&&z<16?2:x>=6&&x<=10&&z>=16&&z<=18?10-x:-1;
   l.setBlock(base.offset(x,y,z),(y<=top?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
  }
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var farm=new Settlement.Building(UUID.randomUUID(),"farm",1,5,-4);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",21,-1,-4);s.addBuilding(farm);s.addBuilding(hall);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  RestaurantFixture.hold(l,s.id(),base.offset(-4,-1,-6),base.offset(34,9,24));
  for(var b:s.buildings())l.setBlock(LogisticsRoutes.position(e,b),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var source=LogisticsRoutes.chest(l,e,farm);var dest=LogisticsRoutes.chest(l,e,hall);source.setItem(0,new ItemStack(Items.WHEAT,20));
  var npc=VillageAstra.RESIDENT.get().create(l);var r=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(r,home);s.assign(r.id(),Profession.PORTER,hall.id());npc.bind(s.id(),r);npc.setHealth(16);npc.moveTo(base.getX()+3.5,base.getY()+6,base.getZ()+.5,0,0);npc.setOnGround(true);
  npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  var route=new LogisticsRoutes.Route(farm,hall,new ItemStack(Items.WHEAT,20));PorterWork.step(npc,route,true);PorterWork.step(npc,route,true);
  var paid=PorterWork.inspect(l,npc.getUUID()).copy();h.assertTrue(paid.getString("stage").equals("deliver")&&source.isEmpty()&&PorterWork.cargo(l,paid).getCount()==20,"Actual finite grain parcel withdrawn once before walking");
  npc.goalSelector.addGoal(6,new PorterGoal(npc,true));
  Runnable clean=()->{npc.discard();SettlementData.get(l.getServer()).remove(s.id());RestaurantFixture.release(l,s.id());PhysicalFixtureChunks.release(l,held);};
  h.onEachTick(()->{
   var path=npc.getNavigation().getPath();if(path!=null)for(int i=1;i<path.getNodeCount();i++)if(Math.abs(path.getNode(i).y-path.getNode(i-1).y)>1){String why="Paid porter planned unsafe cliff step: "+path.getNode(i-1)+" -> "+path.getNode(i);clean.run();h.assertTrue(false,why);return;}
   if(npc.getHealth()!=16){String why="Paid porter lost health during route: "+npc.position()+" HP="+npc.getHealth();clean.run();h.assertTrue(false,why);return;}
   if(dest.countItem(Items.WHEAT)!=20)return;
   h.assertTrue(WorldJournal.exists(l,Settlement.childId(paid.getUUID("id"),"put"))&&PorterWork.inspect(l,npc.getUUID()).getString("stage").equals("complete"),"Original paid parcel has durable completed receipt");
   PorterWork.step(npc,route,true);h.assertTrue(source.isEmpty()&&dest.countItem(Items.WHEAT)==20,"Further ordinary step does not duplicate delivered grain");clean.run();h.succeed();
  });
  h.runAtTickTime(2550,()->{String why="Paid porter never completed safe route: "+npc.position()+" status="+npc.workStatus();clean.run();h.assertTrue(false,why);});
 }
}
