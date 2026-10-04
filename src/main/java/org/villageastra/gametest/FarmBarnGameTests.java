package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-130: the farm's barn. A layout-6 farm raised I..VI by its own upgrade projects (the builders' surveys, carried out block by block without
 *  walking) lays 1/4/6/12/18/18 fields, the barn from IV with its decks, lanterns and stair tower, and its machinery at VI. Every test lays its
 *  own ground far past the template (the barn is 22x38 and 27 high), so each runs in a batch of its own. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmBarnGameTests {
 record Yard(ServerLevel l,SettlementData.Entry e,Settlement s,Settlement.Building farm){}
 /** A layout-6 village: a hall and a farm at (20,0,0) of the centre on open ground (stone under grass, air to y 32). */
 static Yard yard(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,2,6));var s=new Settlement(UUID.randomUUID());s.lotLayout(OrganicLots.BARN_LOTS);
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-4;x<=44;x++)for(int z=-3;z<=41;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<=32;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var b=new Settlement.Building(Settlement.childId(s.id(),"building/farm"),"farm",20,0,0);s.addBuilding(b);
  for(var cell:BuildingPlacement.layout("farm",BuildingPlacement.origin(e,b),0).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  for(var cell:FarmField.layout(BuildingPlacement.origin(e,b),FarmField.modules(1),1L).entrySet())l.setBlock(cell.getKey(),cell.getValue(),2);
  // Every farm level's research: the cores go in at their grade, the VI machine runs by the farm's own ladder (AD-136).
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(int level=2;level<=6;level++)for(var id:BuildingTiers.research("farm",level))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);
  return new Yard(l,e,s,b);
 }
 static Settlement.Building farm(Yard t){return t.s.buildings().stream().filter(x->x.id().equals(t.farm.id())).findFirst().orElseThrow();}
 static void done(ServerLevel l,Settlement s){HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());}
 /** The farm's next upgrade project, carried out as the builders would (each operation's block set in order) and registered. */
 static CompoundTag upgrade(GameTestHelper h,Yard t){
  var survey=BuildingTiers.survey(t.l,t.e,farm(t));h.assertTrue(survey.reason().isEmpty()&&survey.conflicts().isEmpty(),"The level-"+(farm(t).level()+1)+" project can be laid: "+survey.reason()+" "+FarmBarn.describe(t.l,t.e,farm(t),survey.conflicts()));
  var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);
  for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));t.l.setBlock(step.pos(),step.after(),2);}
  h.assertTrue(BuildingOrders.complete(t.l,t.e,survey.state()),"The level-"+(farm(t).level()+1)+" project matches its design, its field and its barn");
  return survey.state();
 }
 /** Upgrades to a level, with the whole farm research done (the level's core rides in with its project). */
 static void raise(GameTestHelper h,Yard t,int level){while(farm(t).level()<level)upgrade(h,t);}
 static List<BlockPos> lanterns(Yard t,int floorsBelow){var out=new ArrayList<BlockPos>();for(var m:FarmField.modules(6))if(FarmField.floor(m)<floorsBelow)for(var c:FarmField.localLanterns(m))out.add(FarmBarn.at(t.e,farm(t),c));return out;}
 static List<BlockPos> plots(Yard t,int floor){var out=new ArrayList<BlockPos>();for(var m:FarmField.modules(6))if(FarmField.floor(m)==floor)for(var p:FarmField.localCells(List.of(m)))out.add(BuildingPlacement.at(t.e,farm(t),p.getX(),p.getY(),p.getZ()));return out;}

 /** Fields per level 1/4/6/12/18/18 (the core's "field"), floors 1/1/1/2/3/3; the IV project lays the walls, both decks, six fields on the
  *  first deck with their dirt and slabs, 48 lanterns and the stair to floor 1; every plot of the two floors then has block light 9 or more
  *  (no sky reaches them), and a floor's water stands on its deck without running down. */
 @GameTest(template="empty",batch="farm_barn_iv",timeoutTicks=600) public static void theBarnLaysItsFloorsWithTheirLanterns(GameTestHelper h){
  var t=yard(h);
  try{
   for(int level=1;level<=6;level++){h.assertTrue(FarmField.modules(level).size()==CoreEffects.value("farm","field",level),"Fields at level "+level);
    h.assertTrue(FarmField.floors(FarmField.modules(level)).size()==CoreEffects.value("farm","floors",level),"Floors at level "+level);}
   raise(h,t,3);
   h.assertTrue(t.s.fieldLevel(t.farm.id())==3&&FarmField.cells(t.e,farm(t)).size()==480,"III lays the 2x3: "+FarmField.cells(t.e,farm(t)).size());
   var four=upgrade(h,t);var cost=four.getCompound("cost");
   var barn=FarmBarn.addedCost(4);
   h.assertTrue(barn.get("minecraft:lantern")==48&&barn.get("minecraft:dirt")==480&&cost.getInt("minecraft:lantern")>=48&&cost.getInt("minecraft:dirt")>=480,"IV's barn takes 48 lanterns and 480 dirt, and the project charges them: "+cost.getInt("minecraft:lantern")+"/"+cost.getInt("minecraft:dirt"));
   h.assertTrue(FarmField.cells(t.e,farm(t)).size()==960&&FarmBarn.laid(t.s,farm(t))==4,"IV lays 960 plots and its barn");
   int lit=0;for(var p:lanterns(t,2))if(t.l.getBlockState(p).is(Blocks.LANTERN))lit++;h.assertTrue(lit==48,"48 lanterns hang over floors 0 and 1: "+lit);
   var up=FarmField.modules(4).stream().filter(m->FarmField.floor(m)==1).findFirst().orElseThrow();var w=FarmField.localWater(up);
   var water=BuildingPlacement.at(t.e,farm(t),w.getX(),w.getY(),w.getZ());
   h.assertTrue(t.l.getFluidState(water).isSource()&&t.l.getBlockState(water.below()).is(Blocks.DARK_OAK_PLANKS)&&t.l.getFluidState(water.below(2)).isEmpty(),"Floor 1's water stands on the deck and runs nowhere");
   // Cane beside the water has three blocks to grow in under the deck above.
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var cane=water.relative(d).above();for(int y=0;y<3;y++)h.assertTrue(t.l.getBlockState(cane.above(y)).isAir(),"Room for cane at "+cane.above(y));}
  }catch(RuntimeException ex){done(t.l,t.s);throw ex;}
  h.runAfterDelay(40,()->{
   try{
    int dark=0;BlockPos worst=null;for(int f=0;f<=1;f++)for(var p:plots(t,f)){int light=t.l.getBrightness(LightLayer.BLOCK,p);if(light<9){dark++;worst=p;}}
    h.assertTrue(dark==0,"Every plot of floors 0 and 1 has block light 9 or more: "+dark+" dark, e.g. "+worst);
    h.assertTrue(BuildingTiers.level(t.l,t.e,farm(t))==4&&FarmField.worked(t.l,t.e,farm(t)).size()==12,"The farm works IV on 12 fields: "+BuildingTiers.level(t.l,t.e,farm(t)));
   }finally{done(t.l,t.s);}
   h.succeed();});
 }
 /** AD-130: the barn's scaffold columns stand on the farm's own plots. A farmer sowing such a plot while the rest of the barn goes up neither
  *  stops the operation that puts the column up (the builder takes the drift) nor keeps the project from completing. */
 @GameTest(template="empty",batch="farm_barn_sown",timeoutTicks=600) public static void aPlotSownUnderAColumnDoesNotStopTheBarn(GameTestHelper h){
  var t=yard(h);
  try{
   raise(h,t,3);
   var survey=BuildingTiers.survey(t.l,t.e,farm(t));h.assertTrue(survey.reason().isEmpty()&&survey.conflicts().isEmpty(),"The level-IV project can be laid: "+survey.reason());
   var ops=survey.state().getList("ops",Tag.TAG_COMPOUND);var scaffold=VillageAstra.TIMBER_SCAFFOLD.get();
   int up=-1,down=-1;BlockPos column=null;var touched=new HashSet<BlockPos>();
   for(int i=0;i<ops.size();i++){var step=HallConstructionPlan.step(ops.getCompound(i));
    // The first column cell on a plot no earlier operation touches: nothing was cleared there, so only the column's own work flags it.
    if(up<0&&step.after().is(scaffold)&&FarmField.contains(t.e,farm(t),step.pos())&&!touched.contains(step.pos())){up=i;column=step.pos();}
    if(column!=null&&i>up&&step.pos().equals(column)&&step.before().is(scaffold)&&step.after().isAir())down=i;
    touched.add(step.pos());}
   h.assertTrue(up>=0&&down>up&&down+1<ops.size(),"A column of the barn stands on an untouched plot of the field: up="+up+" down="+down);
   h.assertTrue(ops.getCompound(down).getBoolean("field")&&!ops.getCompound(up).getBoolean("field"),"The cell the column leaves is field work");
   var wheat=Blocks.WHEAT.defaultBlockState();
   for(int i=0;i<ops.size();i++){
    // Sown by the farmer just before the column goes up, and again once it has come down.
    if(i==up||i==down+1)t.l.setBlock(column,wheat,2);
    var op=ops.getCompound(i);
    if(i==up)h.assertTrue(BuildingOrders.reconcile(t.l,op,BuildingPlacement.origin(t.e,farm(t))),"The builder takes the crop that grew where the column goes");
    var step=HallConstructionPlan.step(op);t.l.setBlock(step.pos(),step.after(),2);}
   h.assertTrue(t.l.getBlockState(column).is(Blocks.WHEAT),"The plot is sown again after the column came down");
   h.assertTrue(BuildingOrders.complete(t.l,t.e,survey.state()),"And the level-IV project still completes");
   h.assertTrue(farm(t).level()==4&&FarmBarn.laid(t.s,farm(t))==4,"The farm is kept at IV with its barn");
  }finally{done(t.l,t.s);}
  h.succeed();
 }
 /** V relays the roof on the raised walls and lays the third floor (72 lanterns in all, 1440 plots); a lantern of floor 2 knocked down drops
  *  the farm to IV (a half-lit barn does not work its floor); VI works only with all 12 machinery blocks standing; the barn's own cost by level. */
 @GameTest(template="empty",batch="farm_barn_v",timeoutTicks=900) public static void theUpperLevelsRelayTheRoofAndNeedTheirMachinery(GameTestHelper h){
  var t=yard(h);
  try{
   // The barn is paid with its level: IV and V lay the most (their floors), VI its machinery; the card shows it with the farmhouse's cost.
   h.assertTrue(FarmBarn.addedCost(4).getOrDefault("minecraft:lantern",0)==48&&FarmBarn.addedCost(5).getOrDefault("minecraft:lantern",0)==24&&FarmBarn.addedCost(6).getOrDefault("minecraft:grindstone",0)==3,"The barn's lanterns 48/24 and the VI machinery");
   raise(h,t,4);var roofIV=FarmBarn.at(t.e,farm(t),new BlockPos(2,FarmBarn.roofY(4,2),20));h.assertTrue(t.l.getBlockState(roofIV).is(Blocks.DEEPSLATE_BRICKS),"IV's ridge in deepslate brick");
   upgrade(h,t);
   h.assertTrue(t.l.getBlockState(roofIV).isAir()&&t.l.getBlockState(FarmBarn.at(t.e,farm(t),new BlockPos(2,FarmBarn.roofY(5,2),20))).is(Blocks.DEEPSLATE_TILES),"V relays the roof five blocks up in tile");
   h.assertTrue(FarmField.cells(t.e,farm(t)).size()==1440,"V lays 1440 plots");
   int lit=0;for(var p:lanterns(t,3))if(t.l.getBlockState(p).is(Blocks.LANTERN))lit++;h.assertTrue(lit==72,"72 lanterns: "+lit);
   h.assertTrue(BuildingTiers.level(t.l,t.e,farm(t))==5&&FarmField.worked(t.l,t.e,farm(t)).size()==18,"V works 18 fields");
   var lamp=lanterns(t,3).get(lanterns(t,3).size()-1);t.l.setBlock(lamp,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(BuildingTiers.level(t.l,t.e,farm(t))==4&&FarmField.worked(t.l,t.e,farm(t)).size()==12,"Without a lantern of floor 2 the farm works IV");
   t.l.setBlock(lamp,Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING,true),2);
   h.assertTrue(OwnershipEvents.protectedBlock(t.l,lamp)&&OwnershipEvents.protectedBlock(t.l,FarmBarn.at(t.e,farm(t),new BlockPos(5,9,20))),"A lantern and a deck of the barn are the settlement's");
   upgrade(h,t);
   h.assertTrue(BuildingTiers.level(t.l,t.e,farm(t))==6,"VI works with its machinery");
   h.assertTrue(TerminalProgress.missing(t.l,t.e,farm(t))==0,"VI restores every deck, soil, roof and column cap: "+TerminalProgress.missing(t.l,t.e,farm(t)));
   var bin=FarmBarn.at(t.e,farm(t),FarmBarn.bin(2));t.l.setBlock(bin,Blocks.AIR.defaultBlockState(),2);
   h.assertTrue(BuildingTiers.level(t.l,t.e,farm(t))==5,"A seed bin missing caps the farm at V");
   h.assertTrue(FarmField.charges(t.l,t.e,farm(t))==54,"A siege brings 3 charges for each of 18 fields");
   for(var a:Sieges.aims(t.l,t.e,farm(t)))h.assertTrue(FarmField.contains(t.e,farm(t),a),"A siege aim lies on a plot of its floor: "+a);
  }finally{done(t.l,t.s);}
  h.succeed();
 }
}
