package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-158, the cartography ladder: what the levels of the cartographer's house add beyond the survey margin — at II he lights the dark
 *  corners of a village already wholly on his map, out of his own chest and only where nothing burns yet; at VI the atlas draws itself,
 *  paying its paper like any survey. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CartographyLadderGameTests {
 private record Office(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building house){}
 private static Office office(GameTestHelper h){return office(h,h.absolutePos(new BlockPos(8,3,8)));}
 private static Office office(GameTestHelper h,BlockPos center){
  var l=h.getLevel();var s=new Settlement(UUID.randomUUID());
  var house=new Settlement.Building(Settlement.childId(s.id(),"building/cartographer"),"cartographer",0,0,0);s.addBuilding(house);
  for(int x=-6;x<14;x++)for(int z=-6;z<14;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,6,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Office(l,s,e,house);
 }

 /** A house really standing at that level: the level is raised a step at a time and its design laid, because the ladder reads the
  *  building as it stands in the world, not as the record wishes it. */
 private static void raise(Office o,int level){
  for(int n=o.s.buildings().stream().filter(b->b.id().equals(o.house.id())).findFirst().map(b->BuildingTiers.built(o.e,b)).orElse(1)+1;n<=level;n++)o.s.raiseBuildingLevel(o.house.id(),n);
  var b=o.s.buildings().stream().filter(x->x.id().equals(o.house.id())).findFirst().orElseThrow();
  for(var cell:BuildingPlacement.layout(o.e,b,BuildingTiers.layoutId("cartographer",level)).entrySet())o.l.setBlock(cell.getKey(),cell.getValue(),3);
  // The laid design puts its own blocks where the fixture's chest stood: the house keeps a chest at its own logistics point.
  var chestAt=LogisticsRoutes.position(o.e,b);
  o.l.setBlock(chestAt,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  BuildingLevels.forgetBest(o.s.id());Atlas.forgetMargins();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theLitVillageIsALevelTwoCartographersDoing(GameTestHelper h){
  var o=office(h);
  try{
   // The knob itself: nothing at the first level, from the second on every level lights.
   h.assertTrue(CoreEffects.active("cartographer","lighting"),"The ladder's lighting knob is a knob of the game");
   h.assertTrue(CoreEffects.value("cartographer","lighting",1)==0,"A cartographer of the first level only draws");
   for(int level=2;level<=6;level++)h.assertTrue(CoreEffects.value("cartographer","lighting",level)==1,"From the second level on he lights: "+level);
   h.assertTrue(!CartographyLadder.lights(o.l,o.e),"A house of the first level does not light the village");
   raise(o,2);
   h.assertTrue(CartographyLadder.lights(o.l,o.e),"A house of the second does");
   // The map comes first: while a chunk of the area is unopened, there is nothing to light yet.
   h.assertTrue(!CartographyLadder.mapped(o.l,o.e),"The village is not on the map yet");
   // A dark corner of the village's own land, and a torch of the house's chest put into it.
   var chest=LogisticsRoutes.chest(o.l,o.e,CartographyLadder.house(o.l,o.e));chest.clearContent();
   // The shared world of a whole run keeps the lights of the tests before this one: the village's own patch is darkened first,
   // so what is being tested is the finder and not the neighbourhood.
   for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)for(int y=0;y<=4;y++){var at=o.e.center().offset(x,y,z);
    var st=o.l.getBlockState(at);
    if(st.is(Blocks.TORCH)||st.is(Blocks.WALL_TORCH)||st.is(Blocks.LANTERN)||st.is(Blocks.CAMPFIRE)||st.is(net.minecraft.tags.BlockTags.CANDLES))
     o.l.setBlock(at,Blocks.AIR.defaultBlockState(),3);}
   var spot=CartographyLadder.dark(o.l,o.e,o.e.center());
   if(spot==null){var probe=new BlockPos(o.e.center().getX()+8,o.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,o.e.center().getX()+8,o.e.center().getZ()+8),o.e.center().getZ()+8);
    h.fail("No dark corner: probe="+probe+" area="+Atlas.area(o.l,o.e).contains(new ChunkPos(probe))+" loaded="+o.l.hasChunkAt(probe)
     +" under="+o.l.getBlockState(probe.below())+" at="+o.l.getBlockState(probe)+" above="+o.l.getBlockState(probe.above())
     +" block="+o.l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,probe));}
   h.assertTrue(spot!=null&&Atlas.area(o.l,o.e).contains(new ChunkPos(spot)),"A dark corner inside the village: "+spot);
   h.assertTrue(!CartographyLadder.light(o.l,o.e,CartographyLadder.house(o.l,o.e),spot),"Without torches in the chest nothing is lit");
   chest.setItem(0,new ItemStack(Items.TORCH,2));
   h.assertTrue(CartographyLadder.light(o.l,o.e,CartographyLadder.house(o.l,o.e),spot),"With torches the corner gets one");
   h.assertTrue(o.l.getBlockState(spot).is(Blocks.TORCH)&&chest.countItem(Items.TORCH)==1,"A real torch, a real torch fewer in the chest");
   var again=CartographyLadder.dark(o.l,o.e,spot);
   // The rule is the box a torch really lights: the next corner lies outside it, in x, in z or in height.
   h.assertTrue(again==null||Math.abs(again.getX()-spot.getX())>CartographyLadder.LIT||Math.abs(again.getZ()-spot.getZ())>CartographyLadder.LIT
     ||Math.abs(again.getY()-spot.getY())>2,"A lit corner is not lit twice: "+again+" beside "+spot);
   h.assertTrue(o.l.getBlockState(spot).is(Blocks.TORCH),"and the torch put there stands");
   h.succeed();
  }finally{Atlas.forgetMargins();BuildingLevels.forgetBest(o.s.id());SettlementData.get(o.l.getServer()).remove(o.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theSecondCartographerFindsTheNeighbours(GameTestHelper h){
  // The discovery scenario has exactly its own neighbours, independent of prior test settlements.
  var o=office(h,new BlockPos(2_500_000,160,2_500_000));
  // A neighbour within the embassy's reach, with a name of its own, and nothing else between them.
  var l=o.l;var other=new Settlement(UUID.randomUUID());other.name("Соседово");
  other.addBuilding(new Settlement.Building(Settlement.childId(other.id(),"building/town_hall"),"town_hall",0,0,0));
  var neighbour=new SettlementData.Entry(other,l.dimension().location().toString(),o.e.center().offset(64,0,0));
  SettlementData.get(l.getServer()).add(neighbour);
  try{
   h.assertTrue(CoreEffects.active("cartographer","scouts"),"The second cartographer is a knob of the game");
   h.assertTrue(CoreEffects.value("cartographer","scouts",2)==0&&CoreEffects.value("cartographer","scouts",3)==1,"He comes with the third level");
   h.assertTrue(Staff.slots("cartographer",2)==1&&Staff.slots("cartographer",3)==2,"and the house keeps a second seat from the third level: "
     +Staff.slots("cartographer",2)+"/"+Staff.slots("cartographer",3));
   raise(o,2);
   h.assertTrue(CartographyLadder.scout(l,o.e)==null&&CartographyLadder.neighbours(l,o.s.id()).isEmpty(),"A house of the second level finds nobody");
   raise(o,3);
   h.assertTrue(CartographyLadder.scouts(l,o.e),"The third level goes looking");
   // The map comes first: an unmapped village sends nobody out to the neighbours.
   h.assertTrue(!CartographyLadder.mapped(l,o.e)&&CartographyLadder.scout(l,o.e)==null,"Its own land is not on the map yet");
   var chest=LogisticsRoutes.chest(l,o.e,CartographyLadder.house(l,o.e));for(int slot=0;slot<5;slot++)chest.setItem(slot,new ItemStack(Items.PAPER,64));
   for(var c:Atlas.area(l,o.e)){l.getChunk(c.x,c.z);Atlas.survey(l,o.e,CartographyLadder.house(l,o.e),o.s.id(),c,100);}
   h.assertTrue(CartographyLadder.mapped(l,o.e),"Now the whole village is on the map");
   var found=CartographyLadder.scout(l,o.e);
   h.assertTrue(other.id().equals(found)&&CartographyLadder.knows(l,o.s.id(),other.id()),"and the second cartographer finds the village next door: "+found);
   // A village found once is not found again: the whole run's world may hold others, but this one is written down once.
   var next=CartographyLadder.scout(l,o.e);
   h.assertTrue(!other.id().equals(next),"The same village is not found twice: "+next);
   h.assertTrue(CartographyLadder.neighbours(l,o.s.id()).stream().filter(other.id()::equals).count()==1,"and stands in the list once");
   h.succeed();
  }finally{Atlas.forgetMargins();BuildingLevels.forgetBest(o.s.id());
   SettlementData.get(l.getServer()).remove(other.id());SettlementData.get(l.getServer()).remove(o.s.id());}
 }

 @GameTest(template="empty",timeoutTicks=300) public static void theTrainedWolfWalksTheVillageAndLightsIt(GameTestHelper h){
  var o=office(h);var l=o.l;
  // A kennel of the village with one wolf in it: the cartographer trains what the yard keeps, he does not catch his own.
  var yard=new Settlement.Building(Settlement.childId(o.s.id(),"building/livestock"),"livestock",6,0,0);o.s.addBuilding(yard);
  var kennel=new Settlement.Building(Settlement.childId(o.s.id(),"building/kennel"),VillageWolves.TYPE,9,0,0);
  o.s.addBuilding(kennel);o.s.linkAnnex(kennel.id(),yard.id());
  var wolf=net.minecraft.world.entity.EntityType.WOLF.create(l);
  wolf.moveTo(o.e.center().getX()+2.5,o.e.center().getY()+1,o.e.center().getZ()+2.5,0,0);l.addFreshEntity(wolf);
  try{
   h.assertTrue(CoreEffects.active("cartographer","patrol"),"The trained wolf is a knob of the game");
   h.assertTrue(CoreEffects.value("cartographer","patrol",4)==0&&CoreEffects.value("cartographer","patrol",5)==1,"He trains one from the fifth level");
   h.assertTrue(VillageWolves.enlist(l,o.e,wolf,kennel),"The wolf is the village's own");
   wolf.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
   raise(o,4);
   h.assertTrue(CartographyLadder.train(l,o.e)==null,"A house of the fourth level trains nobody");
   raise(o,5);
   h.assertTrue(CartographyLadder.train(l,o.e)==wolf&&CartographyLadder.patrolWolf(l,o.e)==wolf,"At the fifth he trains the kennel's wolf");
   h.assertTrue(CartographyLadder.train(l,o.e)==wolf,"and one is enough");
   var chest=LogisticsRoutes.chest(l,o.e,CartographyLadder.house(l,o.e));
   // A yard of its own beside the house, clear of everything the laid design lights: that is where the patrol has work.
   var pad=o.e.center().offset(14,0,14);
   for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){var g=pad.offset(x,0,z);l.setBlock(g,Blocks.STONE.defaultBlockState(),3);
    for(int up=1;up<=4;up++)l.setBlock(g.above(up),Blocks.AIR.defaultBlockState(),3);}
   // Only round the pad, and never near the house: its level is read from the blocks that really stand in it, lanterns included.
   for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=0;y<=5;y++){var at=pad.offset(x,y,z);var st=l.getBlockState(at);
    if(st.is(Blocks.TORCH)||st.is(Blocks.WALL_TORCH)||st.is(Blocks.LANTERN)||st.is(Blocks.CAMPFIRE))l.setBlock(at,Blocks.AIR.defaultBlockState(),3);}
   wolf.moveTo(pad.getX()+.5,pad.getY()+1,pad.getZ()+.5,0,0);
   chest.clearContent();
   var empty=CartographyLadder.patrol(l,o.e,wolf);
   if(empty==null)h.fail("No corner for the patrol: pad="+pad+" area="+Atlas.area(l,o.e).contains(new ChunkPos(pad))
     +" under="+l.getBlockState(pad)+" at="+l.getBlockState(pad.above())+" light="+l.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,pad.above()));
   h.assertTrue(empty!=null,"The wolf is sent to a dark corner");
   wolf.moveTo(empty.getX()+.5,empty.getY(),empty.getZ()+.5,0,0);
   h.assertTrue(CartographyLadder.patrol(l,o.e,wolf)!=null&&!l.getBlockState(empty).is(Blocks.TORCH),"With no torches in the chest it carries none");
   chest.setItem(0,new ItemStack(Items.TORCH,4));
   // The wolf stands by that corner already: with torches in the chest this round really puts one there.
   var corner=CartographyLadder.patrol(l,o.e,wolf);
   h.assertTrue(corner!=null&&l.getBlockState(corner).is(Blocks.TORCH)&&chest.countItem(Items.TORCH)==3,
     "and standing by it puts the torch there: "+corner+" "+l.getBlockState(corner)+" torches="+chest.countItem(Items.TORCH));
   var next=CartographyLadder.patrol(l,o.e,wolf);
   h.assertTrue(next==null||!next.equals(corner),"and the next round goes to another corner: "+next);
   h.succeed();
  }finally{Atlas.forgetMargins();BuildingLevels.forgetBest(o.s.id());wolf.discard();SettlementData.get(l.getServer()).remove(o.s.id());}
 }

 @GameTest(template="empty",timeoutTicks=200) public static void atSixTheAtlasDrawsItself(GameTestHelper h){
  var o=office(h);
  try{
   h.assertTrue(CoreEffects.active("cartographer","auto"),"The self-drawing atlas is a knob of the game");
   for(int level=1;level<=5;level++)h.assertTrue(CoreEffects.value("cartographer","auto",level)==0,"Up to the fifth level somebody walks the chunks: "+level);
   h.assertTrue(CoreEffects.value("cartographer","auto",6)==1,"At the sixth the atlas draws itself");
   var chest=LogisticsRoutes.chest(o.l,o.e,CartographyLadder.house(o.l,o.e));chest.setItem(0,new ItemStack(Items.PAPER,2));
   var home=new ChunkPos(o.e.center());o.l.getChunk(home.x,home.z);
   raise(o,5);
   CartographyLadder.tick(o.l.getServer(),CartographyLadder.AUTO_EVERY);
   h.assertTrue(Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).isEmpty(),"A fifth-level house still sends somebody out");
   raise(o,6);
   h.assertTrue(CartographyLadder.draws(o.l,o.e),"The sixth level draws by itself");
   CartographyLadder.tick(o.l.getServer(),CartographyLadder.AUTO_EVERY);
   h.assertTrue(Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()==1&&chest.countItem(Items.PAPER)==1,
     "One chunk of the area is drawn and one paper is spent: "+Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()+" "+chest.countItem(Items.PAPER));
   CartographyLadder.tick(o.l.getServer(),CartographyLadder.AUTO_EVERY+1);
   h.assertTrue(Atlas.surveyed(Atlas.inspect(o.l,o.s.id())).size()==1,"and it draws on its own pass, not on every tick");
   h.succeed();
  }finally{Atlas.forgetMargins();BuildingLevels.forgetBest(o.s.id());SettlementData.get(o.l.getServer()).remove(o.s.id());}
 }
}
