package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-123: the owner's road ladder — gravel I, cobblestone ×1.5 with Roads IV, stone bricks ×2 with Roads VI; repairs keep a cell's own tier
 *  without research; the machines' road repair follows Mechanics V. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RoadLadderGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  for(int x=-4;x<24;x++)for(int z=-4;z<18;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,hall,center);
 }
 private static void learn(Town t,String... nodes){var record=BookResearch.inspect(t.l,t.e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var n:nodes)done.add(StringTag.valueOf(n));record.put("legacyDone",done);BookResearch.store(t.l,t.e,record);ResearchKnobs.forget(t.s.id());}
 private static void done(Town t){SettlementData.get(t.l.getServer()).remove(t.s.id());
  try{java.nio.file.Files.deleteIfExists(Roads.projectPath(t.l,t.s.id()));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
 private static final int GRAVEL=1<<2,COBBLE=2<<2,BRICKS=3<<2;
 @GameTest(template="empty",timeoutTicks=100) public static void cobbleNeedsRoadsFour(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(ResearchGate.forRoad(COBBLE).equals(List.of("roads.1","roads.4")),"Cobblestone names gravel and Roads IV: "+ResearchGate.forRoad(COBBLE));
   learn(t,"roads.1","roads.2","roads.3");
   h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,GRAVEL).isEmpty()&&ResearchGate.roadRefusal(t.l,t.e,COBBLE).equals("research"),"Roads I–III do not open cobblestone");
   learn(t,"roads.4");h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,COBBLE).isEmpty(),"Roads IV opens cobblestone");
  }finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void bricksNeedRoadsSix(GameTestHelper h){
  var t=town(h);
  try{
   h.assertTrue(ResearchGate.forRoad(BRICKS).contains("roads.6")&&!ResearchGate.forRoad(BRICKS).contains("roads.4"),"Stone bricks name Roads VI: "+ResearchGate.forRoad(BRICKS));
   learn(t,"roads.1","roads.2","roads.3","roads.4","roads.5");h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,BRICKS).equals("research"),"Roads V does not open stone bricks");
   learn(t,"roads.6");h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,BRICKS).isEmpty(),"Roads VI opens stone bricks");
  }finally{done(t);}
  h.succeed();
 }
 /** An old world with roads.2 and cobblestone keeps repairing it: the upkeep round asks no research. */
 @GameTest(template="empty",timeoutTicks=100) public static void repairIgnoresSurfaceGate(GameTestHelper h){
  var t=town(h);
  try{
   var pos=t.center.offset(6,0,10);t.l.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),3);Roads.register(t.l,t.s.id(),pos);Roads.cell(t.l,pos).wear=Roads.REPAIR_WEAR+5;
   h.assertTrue(ResearchGate.roadRefusal(t.l,t.e,BRICKS).equals("research"),"New stone-brick roads are refused");
   var round=Roads.maintenance(t.l,t.e);
   h.assertTrue(round!=null&&round.getString("kind").equals("repair")&&round.getCompound("cost").getInt("minecraft:stone_bricks")==1,"The repair of the existing stone bricks is planned without research: "+round);
  }finally{done(t);}
  h.succeed();
 }
 private static double speed(GameTestHelper h,Town t,Block surface,int wear,EntityType<? extends net.minecraft.world.entity.Mob> type){
  var pos=t.center.offset(8,0,8);t.l.setBlock(pos,surface.defaultBlockState(),3);Roads.register(t.l,t.s.id(),pos);Roads.cell(t.l,pos).wear=wear;
  var mob=type.create(t.l);mob.setNoAi(true);mob.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0);mob.setOnGround(true);t.l.addFreshEntity(mob);
  double base=mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);Roads.living(mob);double k=mob.getAttributeValue(Attributes.MOVEMENT_SPEED)/base;mob.discard();return k;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void cobbleSpeedIsOnePointFive(GameTestHelper h){
  var t=town(h);try{double k=speed(h,t,Blocks.COBBLESTONE,0,EntityType.PIG);h.assertTrue(Math.abs(k-1.5)<1e-6,"An animal on fresh cobblestone: ×"+k);
   double worn=speed(h,t,Blocks.COBBLESTONE,Roads.MAX_WEAR,EntityType.PIG);h.assertTrue(Math.abs(worn-1.25)<1e-6,"Worn cobblestone: ×"+worn);}finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void bricksSpeedIsTwo(GameTestHelper h){
  var t=town(h);try{double k=speed(h,t,Blocks.STONE_BRICKS,0,EntityType.PIG);h.assertTrue(Math.abs(k-2.0)<1e-6,"An animal on fresh stone bricks: ×"+k);
   double z=speed(h,t,Blocks.STONE_BRICKS,0,EntityType.ZOMBIE);h.assertTrue(Math.abs(z-(1+Roads.HOSTILE_CAP))<1e-6,"A zombie is capped at the pre-rework best bonus: ×"+z);}finally{done(t);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void wornBricksAreOnePointFive(GameTestHelper h){
  var t=town(h);try{double k=speed(h,t,Blocks.STONE_BRICKS,Roads.MAX_WEAR,EntityType.PIG);h.assertTrue(Math.abs(k-1.5)<1e-6,"Fully worn stone bricks: ×"+k);}finally{done(t);}
  h.succeed();
 }
 /** The machines of a level-five hall repair the roads with Mechanics V; Roads V alone no longer does it. */
 @GameTest(template="empty",timeoutTicks=100) public static void roadRepairFollowsMechanicsFive(GameTestHelper h){
  var t=town(h);var worn=t.center.offset(3,0,12);
  try{
   for(int i=2;i<=5;i++)t.s.raiseBuildingLevel(t.hall.id(),i);var hall=Workshops.hall(t.e);
   for(var placed:LevelArchitecture.equipment("town_hall_3"))if(placed.level()<=5)
    t.l.setBlock(BuildingPlacement.at(t.e,hall,placed.local().getX(),placed.local().getY(),placed.local().getZ()),BuildingPlacement.state(placed.state(),hall.rotation()),18);
   h.assertTrue(BuildingLevels.level(t.l,t.e,hall)==5,"The hall works at level five");
   t.l.setBlock(worn,Blocks.DIRT_PATH.defaultBlockState(),3);Roads.register(t.l,t.s.id(),worn);Roads.cell(t.l,worn).wear=Roads.REPAIR_WEAR+5;
   learn(t,"roads.5");Machines.tick(t.l,t.e,40,List.of());
   h.assertTrue(Roads.project(t.l,t.s.id())==null,"Roads V alone starts no machine repair");
   learn(t,"roads.6");Machines.tick(t.l,t.e,80,List.of());
   h.assertTrue(Roads.project(t.l,t.s.id())!=null&&Roads.project(t.l,t.s.id()).getString("kind").equals("repair"),"Roads VI (AD-136, was Mechanics V) plans the machines' repair round");
  }finally{done(t);}
  h.succeed();
 }
}
