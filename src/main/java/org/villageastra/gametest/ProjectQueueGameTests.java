package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-078: one construction project at a time, but the crew's own housekeeping gives way — a repair nobody has started yields
 *  to what the mayor orders, and any project nobody has started can be called off. What is already built is never called off. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ProjectQueueGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<40;x++)for(int z=-2;z<30;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<10;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,e,s,center);
 }
 /** A building that really stands in the world, block for block as its design has it. */
 private static Settlement.Building raise(Town t,String type,int dx,int dz){
  var b=new Settlement.Building(Settlement.childId(t.s.id(),"building/"+type),type,dx,0,dz);t.s.addBuilding(b);
  for(var cell:BuildingPlacement.layout(t.e,b,type).entrySet())t.l.setBlock(cell.getKey(),cell.getValue(),3);
  return b;
 }
 private static void learn(Town t,String type,int level){
  var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);
  for(var id:BuildingTiers.research(type,level))done.add(StringTag.valueOf(id));
  record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);
  // AD-136 (owner answer 2): no building above the hall - the hall stands at that level too.
  while(t.s.civilization().level()<level)t.s.civilization().completedHallUpgrade(t.s.civilization().level()+1);
 }
 /** Knocks a few blocks out of a building, the way an explosion would. */
 private static void damage(Town t,Settlement.Building b){
  int broken=0;
  for(var cell:BuildingPlacement.layout(t.e,b,b.type()).entrySet()){
   if(cell.getValue().isAir()||cell.getKey().getY()<=t.center.getY()+1)continue;
   t.l.setBlock(cell.getKey(),Blocks.AIR.defaultBlockState(),3);if(++broken>=6)return;}
 }
 private static void done(Town t){
  HallUpgradeGoal.drop(t.l,t.s.id());SettlementData.get(t.l.getServer()).remove(t.s.id());
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theMayorsOrderOutranksARepairNobodyHasStarted(GameTestHelper h){
  var t=town(h);
  try{
   var bakery=raise(t,"restaurant",12,0);var farm=raise(t,"farm",24,0);learn(t,"farm",2);
   damage(t,bakery);
   h.assertTrue(BuildingRepairs.check(t.l.getServer(),t.e).equals("queued"),"The crew queues the damaged bakery by itself");
   h.assertTrue(HallUpgradeGoal.yields(t.l,t.s.id()),"A repair nobody has started gives way");
   h.assertTrue(BuildingTiers.refusal(t.l,t.e,farm).isEmpty(),"So the office does not call the crew busy: "+BuildingTiers.refusal(t.l,t.e,farm));
   var ordered=BuildingTiers.order(t.l,t.e,farm);h.assertTrue(ordered.isEmpty(),"The mayor orders the next level of the farm: "+ordered);
   var project=HallUpgradeGoal.inspect(t.l,t.s.id());
   h.assertTrue(project.getInt("upgradeLevel")==2&&project.getUUID("building").equals(farm.id())&&!project.getBoolean("repair"),
     "What is queued now is the ordered level, not the repair: "+project.getInt("upgradeLevel")+" repair="+project.getBoolean("repair"));
   h.assertTrue(!HallUpgradeGoal.yields(t.l,t.s.id())&&BuildingTiers.refusal(t.l,t.e,bakery).equals("busy"),
     "The mayor's own project does not give way in its turn");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void whatTheCrewHasStartedIsNeitherPushedAsideNorCalledOff(GameTestHelper h){
  var t=town(h);
  try{
   var bakery=raise(t,"restaurant",12,0);var farm=raise(t,"farm",24,0);learn(t,"farm",2);
   damage(t,bakery);
   h.assertTrue(BuildingRepairs.check(t.l.getServer(),t.e).equals("queued"),"The repair of the bakery is queued");
   var id=HallConstructionPlan.projectId(HallUpgradeGoal.inspect(t.l,t.s.id()));
   h.assertTrue(HallUpgradeGoal.cancellable(t.l,t.s.id(),id).isEmpty(),"Before it is started it can be called off");
   h.assertTrue(HallUpgradeGoal.cancellable(t.l,t.s.id(),UUID.randomUUID()).equals("project"),"Only the project that is really queued can be called off");
   HallUpgradeGoal.advanceForProbe(t.l,t.s.id(),1);
   h.assertTrue(HallUpgradeGoal.cancellable(t.l,t.s.id(),id).equals("started"),"Once a block of it stands it is only paused, never called off");
   h.assertTrue(!HallUpgradeGoal.yields(t.l,t.s.id())&&BuildingTiers.refusal(t.l,t.e,farm).equals("busy"),"And it keeps the slot against the mayor's order");
   var refused=BuildingOrders.approve(t.l,t.e,"home",0,t.center.offset(34,0,14));h.assertTrue(refused.equals("busy"),"A new building waits for the crew too: "+refused);
   HallUpgradeGoal.drop(t.l,t.s.id());
   h.assertTrue(!HallUpgradeGoal.pending(t.l,t.s.id())&&HallUpgradeGoal.cancellable(t.l,t.s.id(),id).equals("none"),"With the slot free nothing is queued at all");
   var last=BuildingTiers.order(t.l,t.e,farm);h.assertTrue(last.isEmpty(),"And the mayor's order takes it: "+last);
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aRepairWaitingForMaterialsLeavesTheCrewToTheRoads(GameTestHelper h){
  var t=town(h);var bakery=raise(t,"restaurant",14,0);
  damage(t,bakery);
  h.assertTrue(BuildingRepairs.check(t.l.getServer(),t.e).equals("queued"),"The repair of the bakery is queued");
  h.assertTrue(HallUpgradeGoal.pending(t.l,t.s.id())&&HallUpgradeGoal.waiting(t.l,t.e),"With nothing of it at the hall the repair waits, and the crew may pave meanwhile");
  var cost=HallUpgradeGoal.inspect(t.l,t.s.id()).getCompound("cost");var chest=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));int slot=0;
  for(var key:cost.getAllKeys())chest.setItem(slot++,new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(key)),cost.getInt(key)));
  h.runAfterDelay(25,()->{
   h.assertTrue(!HallUpgradeGoal.waiting(t.l,t.e),"Once its materials are at the hall the repair is the crew's work again");
   HallUpgradeGoal.drop(t.l,t.s.id());SettlementData.get(t.l.getServer()).remove(t.s.id());h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=200) public static void onlyTheMayorInTheHallCallsAProjectOff(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var data=SettlementData.get(l.getServer());
  HallUpgradeGoal.request(l,data.entry(s.id()));var project=HallConstructionPlan.projectId(HallUpgradeGoal.inspect(l,s.id()));
  var mayor=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CallOffMayor"));
  mayor.setPos(origin.getX(),origin.getY()+1,origin.getZ());
  var visitor=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"CallOffVisitor"));
  visitor.setPos(mayor.position());
  s.appointPlayerMayor(mayor.getUUID());long epoch=s.governance().epoch();
  h.assertTrue(ManagementOrders.cancel(visitor,s.id(),project,epoch,0).equals("mayor"),"A visitor calls nothing off");
  h.assertTrue(ManagementOrders.cancel(mayor,s.id(),UUID.randomUUID(),epoch,0).equals("project"),"Nor does a window that names another project");
  h.assertTrue(ManagementOrders.cancel(mayor,s.id(),project,epoch,7).equals("mayor"),"Nor a stale revision");
  h.assertTrue(HallUpgradeGoal.pending(l,s.id())&&s.governance().revision()==0,"Every refusal leaves the queue and the office as they were");
  h.assertTrue(ManagementOrders.cancel(mayor,s.id(),project,epoch,0).isEmpty(),"The mayor standing in the hall calls it off");
  h.assertTrue(!HallUpgradeGoal.pending(l,s.id())&&s.governance().revision()==1,"The crew is free again and the order is recorded");
  h.assertTrue(ManagementOrders.cancel(mayor,s.id(),project,epoch,1).equals("none"),"With nothing queued there is nothing to call off");
  h.succeed();
 }
}
