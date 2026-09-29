package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-115: NPC construction leaves room for future extensions and an independent public passage. */
public final class GrowthPlots {
 public static final int EXTENSION=6, PASSAGE=6;
 private GrowthPlots(){}
 public record Box(int west,int north,int east,int south){
  public boolean separated(Box other){return east+PASSAGE<other.west||other.east+PASSAGE<west||south+PASSAGE<other.north||other.south+PASSAGE<north;}
 }
 /** Include the actual blueprint (including exterior equipment), the farm lot, and rotation before adding reserves. */
 public static Box box(String type,BlockPos origin,int rotation){return box(type,origin,rotation,false);}
 /** AD-130/AD-131: a farm keeps its final footprint (barn, eaves and stair tower, on its side) and a forester its 15x21 lot from level I. */
 public static Box box(String type,BlockPos origin,int rotation,boolean westField){
  var design=BuildingBlueprints.design(type);if(design==null)throw new IllegalArgumentException("Unknown design "+type);
  int west=0,north=0,east=design.width()-1,south=design.depth()-1;
  for(var p:BuildingBlueprints.layout(type,BlockPos.ZERO).keySet()){west=Math.min(west,p.getX());north=Math.min(north,p.getZ());east=Math.max(east,p.getX());south=Math.max(south,p.getZ());}
  if(BuildingBlueprints.base(type).equals("farm")){var field=org.villageastra.domain.OrganicLots.farmReserve(westField);west=Math.min(west,field[0]);north=Math.min(north,field[1]);east=Math.max(east,field[2]);south=Math.max(south,field[3]);}
  if(BuildingBlueprints.base(type).equals("forester")){east=Math.max(east,org.villageastra.domain.OrganicLots.FORESTER_WIDTH-1);south=Math.max(south,org.villageastra.domain.OrganicLots.FORESTER_DEPTH-1);}
  var corners=List.of(BuildingPlacement.turn(west,0,north,design.width(),design.depth(),rotation),BuildingPlacement.turn(east,0,north,design.width(),design.depth(),rotation),BuildingPlacement.turn(west,0,south,design.width(),design.depth(),rotation),BuildingPlacement.turn(east,0,south,design.width(),design.depth(),rotation));
  return new Box(origin.getX()+corners.stream().mapToInt(BlockPos::getX).min().orElseThrow()-EXTENSION,origin.getZ()+corners.stream().mapToInt(BlockPos::getZ).min().orElseThrow()-EXTENSION,origin.getX()+corners.stream().mapToInt(BlockPos::getX).max().orElseThrow()+EXTENSION,origin.getZ()+corners.stream().mapToInt(BlockPos::getZ).max().orElseThrow()+EXTENSION);
 }
 /** What a standing building keeps: its box; AD-121: a castle village's hall its whole 37x37 castle lot. */
 public static Box reserved(SettlementData.Entry e,Settlement.Building b){
  if(b.type().equals("town_hall")&&HallSite.castle(e.settlement())){var o=BuildingPlacement.origin(e,b);int n=org.villageastra.domain.OrganicLots.CASTLE-1;
   return new Box(o.getX()-EXTENSION,o.getZ()-EXTENSION,o.getX()+n+EXTENSION,o.getZ()+n+EXTENSION);}
  return box(b.type(),BuildingPlacement.origin(e,b),b.rotation(),e.settlement().westField(b.id()));
 }
 public static boolean available(SettlementData.Entry e,String type,BlockPos origin,int rotation){
  var proposed=box(type,origin,rotation);
  for(var b:e.settlement().buildings())if(!proposed.separated(reserved(e,b)))return false;
  return true;
 }
}
