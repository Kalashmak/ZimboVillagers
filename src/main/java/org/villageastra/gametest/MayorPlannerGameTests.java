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
 @GameTest(template="empty",batch="school_growth_priority",timeoutTicks=100)
 public static void schoolDoesNotLoseItsPlaceWhenBirthsFillTheNewHouse(GameTestHelper h){
  var s=Settlement.initial(UUID.randomUUID());
  h.assertTrue("home".equals(MayorPlanner.need(s)),"The original six adults still need their first additional home");
  var home=new Settlement.Home(UUID.randomUUID(),1,2,true);s.addHome(home);
  var first=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(first,home.id());
  h.assertTrue("school".equals(MayorPlanner.need(s)),"The first real child needs a school");
  var second=new Resident(UUID.randomUUID(),Resident.Life.CHILD,false,null,null,-1);s.admit(second,home.id());
  h.assertTrue("school".equals(MayorPlanner.need(s)),"Filling the last free bed must not discard the school in favor of another growth house");
  first.growUp();second.growUp();
  h.assertTrue("school".equals(MayorPlanner.need(s)),"Growing up before construction must not erase the community's education shortage");
  s.addBuilding(new Settlement.Building(UUID.randomUUID(),"school",20,0,0));
  h.assertTrue("home".equals(MayorPlanner.need(s)),"A completed school returns the full village to housing growth");h.succeed();
 }
 @GameTest(template="empty",batch="food_capacity",timeoutTicks=200)
 public static void growingVillageOrdersFoodBeforeMoreHousingAndCountsOnlyExistingFields(GameTestHelper h){
  var t=ResearchV2Town.town(h,"farm");
  try{
   var home=Settlement.childId(t.s.id(),"home");for(int i=0;i<8;i++)t.s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1),home);
   ResearchV2Town.learn(t,ResearchGate.forDesign("farm").toArray(String[]::new));
   h.assertTrue(MayorPlanner.foodShortage(t.l,t.e)&&MayorPlanner.need(t.l,t.e).equals("farm"),"Eight residents must receive another ordinary paid farm before the next house");
   t.s.appointPlayerMayor(UUID.randomUUID());h.assertTrue(!MayorPlanner.need(t.l,t.e).equals("farm"),"A player mayor keeps project choice");t.s.appointNpcMayor();
   ResearchV2Town.raise(t,2);h.assertTrue(MayorPlanner.foodShortage(t.l,t.e),"Upgrading only the farmhouse cannot count fields not registered as laid");
   t.s.raiseFieldLevel(t.shop.id(),2);
   h.assertTrue(!MayorPlanner.foodShortage(t.l,t.e)&&MayorPlanner.need(t.l,t.e).equals("home"),"The existing four fields can feed eight residents, so housing becomes the next shortage again");
  }finally{ResearchV2Town.done(t);}h.succeed();
 }
 @GameTest(template="empty",batch="food_capacity_margin",timeoutTicks=200)
 public static void starterFoodForecastKeepsTheExistingFarmRatingReserve(GameTestHelper h){
  var l=h.getLevel();var s=Settlement.initial(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),h.absolutePos(new BlockPos(6,3,6)));SettlementData.get(l.getServer()).add(e);
  try{
   var farm=s.buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();ResearchV2Town.lay(l,e,farm,"farm");h.assertTrue(s.residents().stream().filter(Resident::alive).count()==6&&s.homes().stream().noneMatch(home->s.occupancy(home.id())<home.capacity()),"Six residents with full original housing");
   h.assertTrue(MayorPlanner.foodShortage(l,e),"Perfect uninterrupted growth barely feeds six; the existing quarter reserve must trigger a real additional field");
   var second=new Settlement.Building(UUID.randomUUID(),"farm",35,0,35);s.addBuilding(second);ResearchV2Town.lay(l,e,second,"farm");h.assertTrue(!MayorPlanner.foodShortage(l,e),"Two existing ordinary farms cover six with the same reserve");
  }finally{SettlementData.get(l.getServer()).remove(s.id());BuildingLevels.forgetBest(s.id());}h.succeed();
 }
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
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(24,3,20));int startX=at.getX()+655360,top=120;
  for(int x=-52;x<=52;x++)for(int z=-45;z<=45;z++)top=Math.max(top,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,startX+x,at.getZ()+z)+16);
  var center=new BlockPos(startX,top,at.getZ());var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var home=new Settlement.Home(UUID.randomUUID(),1,1,true);s.addHome(home);var mayor=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,Profession.MAYOR,null,-1);s.admit(mayor,home.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(int x=-52;x<=52;x++)for(int z=-45;z<=45;z++){var p=center.offset(x,0,z);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<=18;y++)l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);}
  h.assertTrue(center.equals(MayorPlanner.siteGround(l,center)),"Isolated prepared plot is the exposed surface; neighboring test roofs cannot cover it");
  BlockPos site=null;for(int pass=0;pass<20&&site==null;pass++)site=MayorPlanner.site(l,e,"home");
  var probe=BuildingOrders.survey(l,e,"home",0,center.offset(-34,0,-3));h.assertTrue(GrowthPlots.available(e,"home",center.offset(-34,0,-3),0)&&probe.ok(),"Prepared plot includes a surveyed house beyond the hall's actual expansion reserve");var heights=new StringBuilder();for(int r=12;r<=20;r+=4){int x=center.getX()+r,z=center.getZ();heights.append(l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1-center.getY()).append(",");}
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
