package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-032: NPC mayor needs, site search and approval only when standing at the site. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class MayorPlannerGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void needsFollowHousingChildrenAndIdleAdults(GameTestHelper h){
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);
  var a=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,Profession.MAYOR,null,-1);s.admit(a,home.id());
  h.assertTrue(MayorPlanner.need(s)==null,"Free place, no children, no idle adults: nothing to build");
  var b=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(b,home.id());
  h.assertTrue("home".equals(MayorPlanner.need(s)),"Full housing asks for a house");
  var bigger=new Settlement.Home(UUID.randomUUID(),1,3,true);s.addHome(bigger);var child=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(child,bigger.id());
  h.assertTrue("school".equals(MayorPlanner.need(s)),"Children without a school ask for a school");
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"school",20,0,0));
  h.assertTrue("mill".equals(MayorPlanner.need(s)),"Idle adult asks for the first missing workplace");
  h.succeed();
 }
 @GameTest(template="empty",batch="mayor_site",timeoutTicks=200) public static void mayorApprovesOnlyAtTheSurveyedSite(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(24,3,20));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);s.addHome(home);var mayor=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,Profession.MAYOR,null,-1);s.admit(mayor,home.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-22;x<=22;x++)for(int z=-19;z<=19;z++){var p=center.offset(x,0,z);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=18;y++)l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);}
  BlockPos site=null;for(int pass=0;pass<20&&site==null;pass++)site=MayorPlanner.site(l,e,"home");
  var probe=BuildingOrders.survey(l,e,"home",0,center.offset(-14,0,-3));var heights=new StringBuilder();for(int r=12;r<=20;r+=4){int x=center.getX()+r,z=center.getZ();heights.append(l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1-center.getY()).append(",");}
  h.assertTrue(site!=null&&BuildingOrders.survey(l,e,"home",0,site).ok(),"A surveyed free site is found near the hall; explicit pad site: "+probe.reason()+" "+probe.conflicts().stream().limit(6).map(c->c.subtract(center).toShortString()).toList()+" heights "+heights);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),mayor);npc.setNoAi(true);npc.moveTo(center.getX()+.5,center.getY()+1,center.getZ()+.5,0,0);l.addFreshEntity(npc);
  h.assertTrue(MayorPlanner.plan(l,e)==null,"A housing shortage alone must not invent an unobserved timber source");
  var stock=LogisticsRoutes.position(e,Workshops.hall(e));l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  ((net.minecraft.world.Container)l.getBlockEntity(stock)).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG,8));
  MayorPlanner.Proposal chosen=null;for(int pass=0;pass<20&&chosen==null;pass++)chosen=MayorPlanner.plan(l,e);
  h.assertTrue(chosen!=null&&chosen.design().equals("home"),"Full housing gives a house proposal with a site");
  npc.teleportTo(chosen.site().getX()+30.5,chosen.site().getY()+1,chosen.site().getZ()+.5);
  h.assertTrue(MayorPlanner.approveAtSite(l,e,npc).equals("walking")&&!HallUpgradeGoal.exists(l,s.id()),"Far mayor cannot approve");
  npc.teleportTo(chosen.site().getX()+.5,chosen.site().getY()+1,chosen.site().getZ()-2.5);
  h.assertTrue(MayorPlanner.approveAtSite(l,e,npc).equals("approved")&&HallUpgradeGoal.pending(l,s.id()),"Mayor at the site approves the paid order");
  npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());
  h.succeed();
 }
}
