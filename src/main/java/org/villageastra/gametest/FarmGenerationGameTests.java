package org.villageastra.gametest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-104: a generated farm lays FarmField's 9x9 module — 80 plots of moist farmland round one water source, every one hydrated by the
 *  vanilla rule — and the lots of an organic village keep the farm's whole field box free of every other lot and road. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarmGenerationGameTests {
 private static Settlement.Building farm(Collection<Settlement.Building> buildings){return buildings.stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();}
 /** Vanilla FarmBlock.isNearWater: water within four blocks around the farmland, at its level or one above. */
 private static boolean nearWater(ServerLevel l,BlockPos soil){for(var p:BlockPos.betweenClosed(soil.offset(-4,0,-4),soil.offset(4,1,4)))if(l.getFluidState(p).is(FluidTags.WATER))return true;return false;}
 private static void drop(ServerLevel l,Settlement s){for(var r:s.residents()){var entity=l.getEntity(r.id());if(entity!=null)entity.discard();}SettlementData.get(l.getServer()).remove(s.id());}
 /** Whether a column relative to the village origin lies in this farm's field box (unturned, as generation lays it). */
 private static boolean inField(Settlement.Building farm,int[] box,int x,int z){return x>=farm.x()+box[0]&&x<=farm.x()+box[2]&&z>=farm.z()+box[1]&&z<=farm.z()+box[3];}
 @GameTest(template="empty",timeoutTicks=100) public static void aStarterFarmLaysEightyMoistPlotsRoundOneWaterSource(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));
  // A crop stands only in light (8 or more), and the test area lies deep in the test world's ground: the field is lit from above first,
  // as a meadow is, and gets a second for the light to spread before the village is laid.
  var field=origin.offset(StarterVillage.BUILDINGS[4][0],0,StarterVillage.BUILDINGS[4][1]);var lit=FarmField.box(FarmField.modules(1));
  for(int x=lit[0];x<=lit[2];x++)for(int z=lit[1];z<=lit[3];z++)l.setBlock(field.offset(x,FarmField.HEADROOM+2,z),Blocks.LIGHT.defaultBlockState(),2);
  h.runAfterDelay(20,()->{var s=StarterVillage.create(l,origin);
  try{
   var e=SettlementData.get(l.getServer()).entry(s.id());var farm=farm(s.buildings());var cells=FarmField.cells(e,farm);var water=FarmField.water(e,farm);
   h.assertTrue(cells.size()==80&&new HashSet<>(cells).size()==80,"The level-I field has 80 plots: "+cells.size());
   int moist=0,sown=0;var ages=new HashSet<Integer>();
   for(var c:cells){var soil=l.getBlockState(c.below());if(soil.is(Blocks.FARMLAND)&&soil.getValue(FarmBlock.MOISTURE)==FarmBlock.MAX_MOISTURE)moist++;
    var crop=l.getBlockState(c);if(crop.is(Blocks.WHEAT)){sown++;ages.add(crop.getValue(CropBlock.AGE));}}
   h.assertTrue(moist==80,"Every plot stands on moist farmland: "+moist);
   var first=cells.get(0);
   h.assertTrue(sown==80&&ages.size()>1&&!ages.contains(7),"Every plot is sown with wheat of mixed, unripe ages: "+sown+" "+ages+"; first plot "+l.getBlockState(first)+" light "+l.getRawBrightness(first,0)+" sky "+l.canSeeSky(first));
   h.assertTrue(water.size()==1&&l.getFluidState(water.get(0)).isSource()&&l.getFluidState(water.get(0)).is(FluidTags.WATER),"One water source for the one module: "+water);
   h.assertTrue(l.getBlockState(water.get(0).above()).equals(FarmField.COVER),"The water lies under a slab, so it neither freezes nor is stepped into: "+l.getBlockState(water.get(0).above()));
   double cx=cells.stream().mapToInt(BlockPos::getX).average().orElseThrow(),cz=cells.stream().mapToInt(BlockPos::getZ).average().orElseThrow();
   h.assertTrue(cx==water.get(0).getX()&&cz==water.get(0).getZ(),"The water is at the module's centre: "+cx+","+cz+" vs "+water.get(0).toShortString());
   var box=FarmField.box(FarmField.modules(e,farm));int wet=0;
   for(int x=box[0];x<=box[2];x++)for(int z=box[1];z<=box[3];z++)if(!l.getFluidState(BuildingPlacement.at(e,farm,x,0,z)).isEmpty())wet++;
   h.assertTrue(wet==1,"The field's ground holds no other water: "+wet);
   int paved=0;for(var p:StarterVillage.initialPaths(origin).keySet())if(FarmField.ground(e,farm,p))paved++;
   h.assertTrue(paved==0,"No starter path runs over the field: "+paved);
   // The farm card tells the field as it stands and what it grows by the world's own crop rule, against the level's target.
   var card=org.villageastra.server.BuildingCards.card(l,e,farm);int speed=l.getGameRules().getInt(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING);
   var plots=new ArrayList<int[]>();for(var p:FarmField.localCells(FarmField.modules(e,farm)))plots.add(new int[]{p.getX(),p.getZ()});
   h.assertTrue(card.getInt("fieldPlots")==80&&card.getInt("sown")==80&&card.getInt("hydrated")==80&&card.getInt("target")==6
    &&card.getInt("feeds")==FarmYield.ratedResidents(FarmYield.wheatPerDay(plots,speed),FarmField.RATING),"The farm card tells the field as it stands: "+card);
   h.assertTrue(FarmYield.ratedResidents(FarmYield.wheatPerDay(plots,3),FarmField.RATING)==6,"At the default random tick speed the level-I field is rated to feed six");
  }finally{drop(l,s);}
  h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=100) public static void everyStarterPlotIsHydratedByTheVanillaRule(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);
  try{
   var e=SettlementData.get(l.getServer()).entry(s.id());var cells=FarmField.cells(e,farm(s.buildings()));int near=0,kept=0;
   for(var c:cells){var soil=c.below();if(nearWater(l,soil))near++;
    // A dried plot next to water is wetted again by its random tick; one out of reach would dry further or turn to dirt.
    l.setBlock(soil,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,0),2);l.getBlockState(soil).randomTick(l,soil,l.getRandom());
    var after=l.getBlockState(soil);if(after.is(Blocks.FARMLAND)&&after.getValue(FarmBlock.MOISTURE)==FarmBlock.MAX_MOISTURE)kept++;}
   h.assertTrue(near==80,"Every plot has water within four blocks: "+near+" of "+cells.size());
   h.assertTrue(kept==80,"Every plot is hydrated by its random tick: "+kept+" of "+cells.size());
  }finally{drop(l,s);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void organicLotsAndRoadsKeepOffEveryFarmField(GameTestHelper h){
  var box=FarmField.box(FarmField.modules(1));
  int west=OrganicLots.west("farm");
  h.assertTrue(west<=box[0]&&west+OrganicLots.width("farm")-1>=box[2]&&OrganicLots.depth("farm")-1>=box[3],"The farm's organic lot holds its whole field box: "+Arrays.toString(box));
  for(int seed=0;seed<64;seed++){
   var id=UUID.nameUUIDFromBytes(("astra-farm-"+seed).getBytes(StandardCharsets.UTF_8));List<Settlement.Building> lots=null;
   try{lots=OrganicLots.buildings(id);}catch(IllegalStateException x){h.fail(seed+": "+x.getMessage());}
   var farm=farm(lots);
   for(var o:lots){if(o==farm)continue;int ox=o.x()+OrganicLots.west(o.type());boolean clear=true;
    for(int x=ox;x<ox+OrganicLots.width(o.type());x++)for(int z=o.z();z<o.z()+OrganicLots.depth(o.type());z++)if(inField(farm,box,x,z))clear=false;
    h.assertTrue(clear,seed+": the "+o.type()+" lot at "+o.x()+","+o.z()+" stays off the farm's field at "+farm.x()+","+farm.z());}
  }
  var origin=new BlockPos(0,70,0);
  for(var seed:List.of("astra-farm-a","astra-farm-b","astra-farm-c")){
   var s=Settlement.natural(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)),new int[7],OrganicLots.FIELD_MODULES);var farm=farm(s.buildings());
   var base=origin.offset(farm.x(),farm.y(),farm.z());var layout=NaturalVillage.layout(origin,s);int moist=0,sown=0;
   for(var p:FarmField.localCells(FarmField.modules(1))){var soil=layout.get(base.offset(p).below());var crop=layout.get(base.offset(p));
    if(soil!=null&&soil.is(Blocks.FARMLAND)&&soil.getValue(FarmBlock.MOISTURE)==FarmBlock.MAX_MOISTURE)moist++;if(crop!=null&&crop.is(Blocks.WHEAT))sown++;}
   var spring=layout.get(base.offset(FarmField.localWater(FarmField.modules(1).get(0))));var cover=layout.get(base.offset(FarmField.localWater(FarmField.modules(1).get(0))).above());
   h.assertTrue(moist==80&&sown==80&&spring!=null&&spring.is(Blocks.WATER)&&FarmField.COVER.equals(cover),seed+": the generated field is 80 sown, moist plots round its covered water: "+moist+" "+sown+" "+spring+" "+cover);
   int open=0;for(var c:FarmField.localColumns(FarmField.modules(1)))for(int y=2;y<=FarmField.HEADROOM;y++){var above=layout.get(base.offset(c).above(y));if(above!=null&&above.isAir())open++;}
   h.assertTrue(open==81*(FarmField.HEADROOM-1),seed+": the generator lays open air over every column of the field: "+open);
   for(var b:s.buildings()){if(b==farm)continue;var d=BuildingBlueprints.design(b.type());boolean clear=true;
    for(int x=0;x<d.width();x++)for(int z=0;z<d.depth();z++)if(inField(farm,box,b.x()+x,b.z()+z))clear=false;
    h.assertTrue(clear,seed+": the "+b.type()+" stands off the farm's field");}
   int paved=0;for(var r:NaturalVillage.straightRoads(origin,s,p->70).keySet())if(inField(farm,box,r.getX()-origin.getX(),r.getZ()-origin.getZ()))paved++;
   h.assertTrue(paved==0,seed+": no road runs over the farm's field: "+paved);
   int strip=0;for(var c:NaturalVillage.lots(origin,s).keySet())if(inField(farm,box,c.getX(),c.getZ()))strip++;
   h.assertTrue(strip==0,seed+": the field is the farm's own ground, not a levelled strip: "+strip);
   var territory=NaturalVillage.territory(s,List.of());int outside=0;
   for(int x=box[0];x<=box[2];x++)for(int z=box[1];z<=box[3];z++)if(!territory.contains(new BlockPos(farm.x()+x,0,farm.z()+z)))outside++;
   h.assertTrue(outside==0,seed+": the whole field is village territory: "+outside+" columns outside");
  }
  var legacy=Settlement.natural(UUID.nameUUIDFromBytes("astra-farm-legacy".getBytes(StandardCharsets.UTF_8)));var old=farm(legacy.buildings());int paved=0;
  for(var r:NaturalVillage.roads(BlockPos.ZERO,legacy).keySet())if(inField(old,box,r.getX(),r.getZ()))paved++;
  h.assertTrue(paved==0,"A first-layout village's roads keep off its farm's field too: "+paved);
  h.succeed();
 }
}
