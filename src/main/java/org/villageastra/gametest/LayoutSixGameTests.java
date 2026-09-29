package org.villageastra.gametest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** AD-130/AD-131: layout 6 keeps a farm's final footprint (barn, eaves and stair tower, 22x38) and a forester's 15x21 lot free from level I;
 *  the starter village grows its farm west, clear of the forester, with the mine moved east; layout 5 keeps the lots it had. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LayoutSixGameTests {
 // These read the layout in memory only, but they still take plots of the shared run; a batch of their own keeps the default batch
 // (whose fixtures look for free ground beside their own plot) as it was before AD-130/AD-131 added them.
 private static final String BATCH="layout_six";
 private static Settlement.Building of(Settlement s,String type){return s.buildings().stream().filter(b->b.type().equals(type)).findFirst().orElseThrow();}
 private static boolean in(int[] box,Settlement.Building farm,int x,int z){return x>=farm.x()+box[0]&&x<=farm.x()+box[2]&&z>=farm.z()+box[1]&&z<=farm.z()+box[3];}
 /** Columns (relative to the origin) of the farmhouse's own blocks and its level-I field, the only things laid on the footprint at level I. */
 private static Set<Long> own(Settlement.Building farm){
  var out=new HashSet<Long>();for(var p:BuildingBlueprints.layout("farm",new BlockPos(farm.x(),0,farm.z())).keySet())out.add(BlockPos.asLong(p.getX(),0,p.getZ()));
  for(var c:FarmField.localColumns(FarmField.modules(1)))out.add(BlockPos.asLong(farm.x()+c.getX(),0,farm.z()+c.getZ()));return out;
 }
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void theStarterVillageKeepsTheFarmsWestFootprintFree(GameTestHelper h){
  var s=Settlement.initial(UUID.randomUUID());var farm=of(s,"farm");var reserve=OrganicLots.farmReserve(true);
  h.assertTrue(s.westField(farm.id())&&StarterVillage.BUILDINGS[6][0]==32,"The starter farm grows west and the mine stands at x 32");
  int paths=0;for(var p:StarterVillage.initialPaths(BlockPos.ZERO).keySet())if(in(reserve,farm,p.getX(),p.getZ()))paths++;
  h.assertTrue(paths==0,"No starter path runs over the farm's final footprint: "+paths);
  var mine=own(farm);int foreign=0;for(var p:StarterVillage.layout(BlockPos.ZERO).keySet())if(in(reserve,farm,p.getX(),p.getZ())&&!mine.contains(BlockPos.asLong(p.getX(),0,p.getZ())))foreign++;
  h.assertTrue(foreign==0,"Nothing but the farmhouse and its level-I field stands on the footprint: "+foreign);
  var forester=of(s,"forester");int lot=0;
  for(int x=0;x<OrganicLots.FORESTER_WIDTH;x++)for(int z=0;z<OrganicLots.FORESTER_DEPTH;z++){int wx=forester.x()+x,wz=forester.z()+z;
   if(in(reserve,farm,wx,wz))lot++;for(var b:s.buildings())if(b!=forester&&b!=farm){var d=BuildingBlueprints.design(b.type());if(wx>=b.x()&&wx<b.x()+d.width()&&wz>=b.z()&&wz<b.z()+d.depth())lot++;}}
  h.assertTrue(lot==0,"The forester's 15x21 lot is clear of the farm's footprint and of every other building: "+lot);
  h.succeed();
 }
 @GameTest(template="empty",batch=BATCH,timeoutTicks=200) public static void aLayoutSixVillageKeepsTheBarnAndHutLotsFree(GameTestHelper h){
  var origin=new BlockPos(0,70,0);
  for(var seed:List.of("astra-barn-a","astra-barn-b","astra-barn-c","astra-lots")){
   var s=Settlement.natural(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)),new int[7],OrganicLots.BARN_LOTS);var farm=of(s,"farm");var forester=of(s,"forester");
   var reserve=OrganicLots.farmReserve(false);
   h.assertTrue(!s.westField(farm.id())&&s.lotLayout()==OrganicLots.BARN_LOTS,seed+": a generated farm stands east on a layout-6 lot");
   var lot=new HashSet<>(NaturalVillage.lot(s,farm));int outside=0;for(var c:lot)if(!in(reserve,farm,c.getX(),c.getZ()))outside++;
   h.assertTrue(lot.size()==22*38&&outside==0,seed+": the farm's lot is its 22x38 footprint: "+lot.size()+" columns, "+outside+" outside");
   h.assertTrue(NaturalVillage.lot(s,forester).size()==OrganicLots.FORESTER_WIDTH*OrganicLots.FORESTER_DEPTH,seed+": the forester's lot is 15x21");
   var mine=own(farm);int foreign=0;for(var p:NaturalVillage.layout(origin,s).keySet()){int x=p.getX()-origin.getX(),z=p.getZ()-origin.getZ();if(in(reserve,farm,x,z)&&!mine.contains(BlockPos.asLong(x,0,z)))foreign++;}
   h.assertTrue(foreign==0,seed+": the generator lays only the farmhouse and module (0,0) on the footprint: "+foreign);
   int paved=0;for(var r:NaturalVillage.straightRoads(origin,s,p->70).keySet())if(in(reserve,farm,r.getX()-origin.getX(),r.getZ()-origin.getZ()))paved++;
   h.assertTrue(paved==0,seed+": no road runs over the farm's footprint: "+paved);
   int strip=0;for(var c:NaturalVillage.lots(origin,s).keySet())if(in(reserve,farm,c.getX(),c.getZ()))strip++;
   h.assertTrue(strip==0,seed+": the footprint is the farm's own ground, not a levelled strip: "+strip);
   var d=BuildingBlueprints.design("farm");
   h.assertTrue(NaturalVillage.farmClearing(s,farm).size()==22*38-d.width()*d.depth(),seed+": trees go from the whole footprint but the farmhouse");
   for(var b:s.buildings())for(var o:s.buildings())if(b!=o){var bl=new HashSet<>(NaturalVillage.lot(s,b));boolean clear=true;for(var c:NaturalVillage.lot(s,o))if(bl.contains(c))clear=false;
    h.assertTrue(clear,seed+": the lots of "+b.type()+" and "+o.type()+" do not overlap");}
  }
  h.succeed();
 }
 @GameTest(template="empty",batch=BATCH,timeoutTicks=100) public static void layoutFiveKeepsItsLots(GameTestHelper h){
  var s=Settlement.natural(UUID.nameUUIDFromBytes("astra-lots".getBytes(StandardCharsets.UTF_8)),new int[7],OrganicLots.FIELD_MODULES);var farm=of(s,"farm");
  var lot=NaturalVillage.lot(s,farm);int west=lot.stream().mapToInt(BlockPos::getX).min().orElseThrow()-farm.x(),east=lot.stream().mapToInt(BlockPos::getX).max().orElseThrow()-farm.x();
  int south=lot.stream().mapToInt(BlockPos::getZ).max().orElseThrow()-farm.z();
  h.assertTrue(west==-1&&east==7&&south==26&&lot.size()==9*27,"A layout-5 farm keeps its lot x -1..7, z 0..26: "+west+".."+east+", "+south);
  var forester=of(s,"forester");var d=BuildingBlueprints.design("forester");
  h.assertTrue(NaturalVillage.lot(s,forester).size()==d.width()*d.depth(),"A layout-5 forester keeps the lot of its design");
  h.assertTrue(NaturalVillage.farmClearing(s,farm).size()==9*18,"A layout-5 farm clears the trees over its level-II field only");
  var old=new Settlement(s.id());for(var b:s.buildings())old.addBuilding(b);
  h.assertTrue(NaturalVillage.lot(old,of(old,"farm")).size()==9*27,"A settlement saved before the layout was kept reads the old lots");
  h.succeed();
 }
}
