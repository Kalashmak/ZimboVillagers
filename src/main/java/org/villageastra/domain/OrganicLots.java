package org.villageastra.domain;
import java.util.*;
/** Versioned, reproducible lots around the central hall. No world or client dependencies. */
public final class OrganicLots {
 private OrganicLots(){}
 /** AD-104: layouts from this version on give the farm the lot of its 9x9 field; a piece saved with an older version keeps the positions
  *  it was generated with, so a village whose chunks are generated across the update stays one village. */
 public static final int FIELD_MODULES=5;
 /** AD-130/AD-131: from this version on a farm's lot is the final footprint of its barn (FARM_RESERVE) and a forester's the 15x21 lot of
  *  its level-VI hut, both kept free from level I; an allocation that cannot place them redraws with a salted seed. */
 public static final int BARN_LOTS=6;
 /** The layout new villages are generated with. */
 /** AD-121 (docs/plans/hall-castle-spec.md): the castle town hall (CastlePlan, 37x37) - not CURRENT until its levels are wired. */
 public static final int CASTLE_LOTS=7;
 /** AD-121: the castle's lot and where its north-west corner stands from the village centre (the centre is on the seal, HallSite). */
 public static final int CASTLE=37,CASTLE_X=-14,CASTLE_Z=-23;
 /** How far from the centre every cell of a lot may lie: 47 (the generation piece of AD-104), 64 for a castle village (its piece is
  *  as much wider, SettlementPiece). */
 public static int reach(int version){return version>=CASTLE_LOTS?64:47;}
 public static final int CURRENT=BARN_LOTS;
 /** AD-130: farm-local bounds {minX,minZ,maxX,maxZ} of a farm's final footprint on its east side: barn, eaves and stair tower over 2x3
  *  fields on three floors (22x38). A farm turned west mirrors it about its 7-wide farmhouse: x -> 6-x. */
 private static final int[] FARM_RESERVE={-3,0,18,37};
 /** AD-131: the forester's lot from BARN_LOTS on, the hut of level VI with its courtyard. */
 public static final int FORESTER_WIDTH=15,FORESTER_DEPTH=21;
 /** Salted redraws a BARN_LOTS allocation may take before it gives up. */
 public static final int REDRAWS=8;
 /** AD-130: the farm's final footprint, farm-local {minX,minZ,maxX,maxZ}, on its side. */
 public static int[] farmReserve(boolean west){return west?new int[]{6-FARM_RESERVE[2],FARM_RESERVE[1],6-FARM_RESERVE[0],FARM_RESERVE[3]}:FARM_RESERVE.clone();}
 public static List<Settlement.Building> buildings(UUID id){return buildings(id,CURRENT);}
 public static List<Settlement.Building> buildings(UUID id,int version){
  return tryBuildings(id,version).orElseThrow(()->new IllegalStateException("Cannot allocate organic lot: "+id+" layout="+version));
 }
 /** A bounded survey may reject a candidate whose existing layout draws do not fit. */
 public static Optional<List<Settlement.Building>> tryBuildings(UUID id,int version){
  // Version FIELD_MODULES and older never redraw: their pieces keep the positions they were generated with.
  for(int salt=0;salt<=(version>=BARN_LOTS?REDRAWS:0);salt++){var lots=allocate(id,version,salt);if(lots!=null)return Optional.of(lots);}
  return Optional.empty();
 }
 /** How many salted redraws the allocation of this village takes (0 = the first draw fits), or -1 when none does. */
 public static int redraws(UUID id,int version){for(int salt=0;salt<=(version>=BARN_LOTS?REDRAWS:0);salt++)if(allocate(id,version,salt)!=null)return salt;return -1;}
 private static List<Settlement.Building> allocate(UUID id,int version,int salt){
  var original=Settlement.initialBuildings(id);var result=new ArrayList<Settlement.Building>();var hall=original.get(0);
  // AD-121: the castle's lot corner stands CASTLE_X,CASTLE_Z from the centre.
  result.add(version>=CASTLE_LOTS?new Settlement.Building(hall.id(),hall.type(),CASTLE_X,0,CASTLE_Z):hall);
  long seed=id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),17);if(salt>0)seed^=salt*0x9E3779B97F4A7C15L;
  var random=new Random(seed);
  var order=new ArrayList<>(original.subList(1,7));Collections.shuffle(order,random);
  double phase=random.nextDouble()*Math.PI*2;
  for(int i=0;i<order.size();i++){
   var b=order.get(i);boolean placed=false;
   for(int attempt=0;attempt<1000;attempt++){
    double angle=phase+i*Math.PI/3+(random.nextDouble()-.5)*.55;
    // AD-121: a castle village rings its castle (its middle 4,-5 from the centre) further out, inside its wider piece.
    boolean castle=version>=CASTLE_LOTS;double cx=castle?CASTLE_X+CASTLE/2.0:3,cz=castle?CASTLE_Z+CASTLE/2.0:3;
    double radius=castle?30+random.nextDouble()*16:25+random.nextDouble()*12;
    int x=(int)Math.round(cx+Math.cos(angle)*radius-width(b.type(),version)/2.0-west(b.type(),version));
    int z=(int)Math.round(cz+Math.sin(angle)*radius-depth(b.type(),version)/2.0);
    var candidate=new Settlement.Building(b.id(),b.type(),x,0,z);
    if(version>=FIELD_MODULES&&!inside(candidate,version)||result.stream().anyMatch(other->overlaps(candidate,other,version)))continue;
    result.add(candidate);placed=true;break;
   }
   if(!placed)return null;
  }
  // Preserve the identity order used by height arrays, residents and old saves.
  return original.stream().map(b->result.stream().filter(n->n.id().equals(b.id())).findFirst().orElseThrow()).toList();
 }
 /** The lot of the current layout; a lot starts west(type) blocks from its building's x. */
 public static int west(String type){return west(type,CURRENT);}
 public static int width(String type){return width(type,CURRENT);}
 public static int depth(String type){return depth(type,CURRENT);}
 /** A lot by layout version. Before AD-104 (version FIELD_MODULES-1 and older) the farm's was its 7-wide farmhouse and a field 16 deep;
  *  AD-104/AD-112 (FIELD_MODULES): the farmhouse and its level-II field, x -1..7, z 0..26; BARN_LOTS: FARM_RESERVE (22x38) and the
  *  forester's 15x21, which is 9x17 before it. */
 public static int west(String type,int version){return !type.equals("farm")||version<FIELD_MODULES?0:version>=BARN_LOTS?FARM_RESERVE[0]:-1;}
 public static int width(String type,int version){
  if(version>=CASTLE_LOTS&&type.equals("town_hall"))return CASTLE;
  if(version>=BARN_LOTS&&type.equals("farm"))return FARM_RESERVE[2]-FARM_RESERVE[0]+1;
  if(version>=BARN_LOTS&&type.equals("forester"))return FORESTER_WIDTH;
  return type.equals("forester")||version>=FIELD_MODULES&&type.equals("farm")?9:7;
 }
 public static int depth(String type,int version){
  if(version>=CASTLE_LOTS&&type.equals("town_hall"))return CASTLE;
  if(version>=BARN_LOTS&&type.equals("farm"))return FARM_RESERVE[3]-FARM_RESERVE[1]+1;
  if(version>=BARN_LOTS&&type.equals("forester"))return FORESTER_DEPTH;
  return type.equals("forester")?17:type.equals("farm")?version>=FIELD_MODULES?27:16:7;
 }
 /** AD-104: every cell of a lot lies within reach(version) (47; 64 for a castle village) blocks of the hall, so its levelled strip and the roads round it stay inside the piece. */
 static boolean inside(Settlement.Building a,int version){int x=a.x()+west(a.type(),version),r=reach(version);return x>=-r&&x+width(a.type(),version)-1<=r&&a.z()>=-r&&a.z()+depth(a.type(),version)-1<=r;}
 public static boolean overlaps(Settlement.Building a,Settlement.Building b){return overlaps(a,b,CURRENT);}
 public static boolean overlaps(Settlement.Building a,Settlement.Building b,int version){
  int ax=a.x()+west(a.type(),version),bx=b.x()+west(b.type(),version);
  return ax<bx+width(b.type(),version)+5&&ax+width(a.type(),version)+5>bx
   &&a.z()<b.z()+depth(b.type(),version)+5&&a.z()+depth(a.type(),version)+5>b.z();
 }
}
