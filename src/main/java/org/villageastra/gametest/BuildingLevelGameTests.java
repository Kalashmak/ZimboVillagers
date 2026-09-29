package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-073: six levels of every building. The level is read from the equipment standing in it, every next level rebuilds more of the shell
 *  for more and dearer materials, the sixth takes a netherite block, and the builders raise it as an ordinary construction project. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingLevelGameTests {
 private record Shop(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building shop){}
 private static Shop shop(GameTestHelper h,String type){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var shop=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,10,0,0);s.addBuilding(shop);
  for(int x=-2;x<26;x++)for(int z=-2;z<14;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=0;y<16;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  // The workshop itself stands on its lot, as the builders would have left it.
  for(var cell:BuildingPlacement.layout(type,center.offset(shop.x(),shop.y(),shop.z()),0).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  return new Shop(l,e,shop);
 }
 private static void done(Shop t){
  SettlementData.get(t.l.getServer()).remove(t.e.settlement().id());
  try{java.nio.file.Files.deleteIfExists(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-upgrades/"+t.e.settlement().id()+".bin"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 /** Executes a queued project the way the builder does, without walking. */
 private static void execute(GameTestHelper h,Shop t,net.minecraft.nbt.CompoundTag state){
  var ops=state.getList("ops",Tag.TAG_COMPOUND);var id=state.getUUID("id");var origin=BlockPos.of(state.getLong("origin"));
  for(int i=0;i<ops.size();i++){
   var op=ops.getCompound(i);h.assertTrue(BuildingOrders.reconcile(t.l,op,origin),"No drift at operation "+i);
   var step=HallConstructionPlan.step(op);if(step.before().equals(step.after()))continue;
   h.assertTrue(WorldJournal.place(t.l,Settlement.childId(id,"block/"+i),step.pos(),step.before(),step.after()),"Operation "+i+" at "+step.pos()+" found "+t.l.getBlockState(step.pos()));
  }
  h.assertTrue(BuildingOrders.complete(t.l,t.e,state),"The level matches its design");
 }
 @GameTest(template="empty",timeoutTicks=200) public static void levelIsReadFromRealEquipmentAndSpeedsTheStationUp(GameTestHelper h){
  var t=shop(h,"restaurant");
  try{
   h.assertTrue(BuildingTiers.upgradable("restaurant")&&BuildingTiers.upgradable("town_hall")&&!BuildingTiers.upgradable("siege_camp"),"Settlement buildings have levels, a siege camp has none");
   h.assertTrue(BuildingLevels.level(t.l,t.e,t.shop)==1&&BuildingLevels.labor(t.l,t.e,t.shop)==20,"A new workshop stands at level one and does one turn of work");
   // The building is kept at level two, but nothing of the second level stands yet.
   t.e.settlement().raiseBuildingLevel(t.shop.id(),2);var kept=t.e.settlement().buildings().stream().filter(b->b.id().equals(t.shop.id())).findFirst().orElseThrow();
   h.assertTrue(BuildingTiers.built(t.e,kept)==2&&BuildingLevels.level(t.l,t.e,kept)==1,"Without its equipment the workshop still works at level one");
   for(var placed:BuildingLevels.equipment("restaurant",2))t.l.setBlock(BuildingPlacement.at(t.e,kept,placed.local().getX(),placed.local().getY(),placed.local().getZ()),placed.state(),3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==2,"With the equipment in place the workshop works at level two");
   h.assertTrue(BuildingLevels.labor(t.l,t.e,kept)>20,"Level two really does more work per turn: "+BuildingLevels.labor(t.l,t.e,kept));
   var one=BuildingLevels.equipment("restaurant",2).get(0);
   t.l.setBlock(BuildingPlacement.at(t.e,kept,one.local().getX(),one.local().getY(),one.local().getZ()),Blocks.AIR.defaultBlockState(),3);
   h.assertTrue(BuildingLevels.level(t.l,t.e,kept)==1,"Breaking one piece drops the level back: the level is the blocks, not a counter");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void everyNextLevelCostsMoreAndTheSixthTakesNetherite(GameTestHelper h){
  var problems=new ArrayList<String>();
  for(var type:List.of("home","restaurant","farm","warehouse","guard_house","school","mill","masonry","clinic","cartographer","mine","quarry","barracks","livestock","caravan","carpentry","smithy","laboratory","expedition","archery","engineering","forester","town_hall")){
   // The hall reaches II and III by its own historical projects (AD-018); the levels this catalogue adds start above them.
   int start=type.equals("town_hall")?4:2;int items=0;long worth=0;
   if(start>2){var before=BuildingTiers.cost(type,start-1);items=BuildingTiers.count(before);worth=BuildingTiers.worth(before);}
   for(int level=start;level<=BuildingTiers.MAX;level++){
    var cost=BuildingTiers.cost(type,level);int n=BuildingTiers.count(cost);long w=BuildingTiers.worth(cost);
    if(n<=items)problems.add(type+" level "+level+" costs "+n+" items, level "+(level-1)+" cost "+items);
    if(w<=worth)problems.add(type+" level "+level+" is worth "+w+", level "+(level-1)+" was "+worth);
    items=n;worth=w;
    if(level==BuildingTiers.MAX&&!type.equals("home")&&!cost.containsKey("villageastra:core_ring_6"))problems.add(type+" level six without the netherite ring");
   }
  }
  h.assertTrue(problems.isEmpty(),"Every next level is dearer and the sixth takes the netherite ring: "+problems);
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void theMayorOrdersALevelAndTheBuildersRaiseIt(GameTestHelper h){
  // AD-136: the carpentry's level II now needs its parent, not research; the restaurant's II still needs Baking II.
  var t=shop(h,"restaurant");
  try{
   var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"LevelMayor"));
   p.setPos(t.e.center().getX()+1,t.e.center().getY(),t.e.center().getZ()+1);
   h.assertTrue(BuildingUpgrades.order(p,t.e.settlement().id(),t.e.settlement().governance().epoch(),t.shop.id()).equals("mayor"),"A visitor orders no level");
   t.e.settlement().appointPlayerMayor(p.getUUID());long epoch=t.e.settlement().governance().epoch();
   var refused=BuildingUpgrades.order(p,t.e.settlement().id(),epoch,t.shop.id());
   h.assertTrue(refused.equals("research"),"Without the research of that level the order is refused, got: "+refused);
   // The research of the second level is done; the order goes on the builders' queue and the hall pays for it.
   var research=BookResearch.inspect(t.l,t.e);var done=research.getList("legacyDone",Tag.TAG_STRING);
   for(var id:BuildingTiers.research("restaurant",2))done.add(net.minecraft.nbt.StringTag.valueOf(id));
   research.put("legacyDone",done);BookResearch.store(t.l,t.e,research);
   // AD-136 (owner answer 2): no building above the hall - the hall is at II first.
   h.assertTrue(BuildingUpgrades.order(p,t.e.settlement().id(),epoch,t.shop.id()).equals("hall"),"With the research but a hall at I the order is refused: hall");
   t.e.settlement().civilization().completedHallUpgrade(2);
   var queued=BuildingUpgrades.order(p,t.e.settlement().id(),epoch,t.shop.id());h.assertTrue(queued.isEmpty(),"The mayor queues the level: "+queued);
   h.assertTrue(BuildingUpgrades.order(p,t.e.settlement().id(),epoch,t.shop.id()).equals("busy"),"Only one construction project at a time");
   var state=HallUpgradeGoal.inspect(t.l,t.e.settlement().id());
   h.assertTrue(state.getInt("upgradeLevel")==2&&!state.getList("ops",Tag.TAG_COMPOUND).isEmpty(),"The project is the second level of this building");
   execute(h,t,state);
   var raised=t.e.settlement().buildings().stream().filter(b->b.id().equals(t.shop.id())).findFirst().orElseThrow();
   h.assertTrue(raised.level()==2,"The finished project keeps the building at level two: "+raised.level());
   h.assertTrue(BuildingLevels.level(t.l,t.e,raised)==2,"Its equipment stands, so it works at level two");
   h.assertTrue(BuildingRepairs.damage(t.l,t.e,raised).isEmpty(),"A whole level-two building needs no repair");
   var loaded=SettlementData.load(SettlementData.get(t.l.getServer()).save(new net.minecraft.nbt.CompoundTag())).entry(t.e.settlement().id());
   h.assertTrue(loaded.settlement().buildings().stream().anyMatch(b->b.id().equals(t.shop.id())&&b.level()==2),"The level survives save and load");
  }finally{done(t);}
  h.succeed();
 }
}
