package org.villageastra.world;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import java.util.*;

/** Deterministic irregular lots and a connected initial road network routed around every lot. */
public final class NaturalVillage {
 private NaturalVillage(){}
 public static Map<BlockPos,BlockState> layout(BlockPos origin,Settlement s){
  Map<BlockPos,BlockState> map=new LinkedHashMap<>();
  for(var b:s.buildings()){
   BlockPos base=origin.offset(b.x(),b.y(),b.z());map.putAll(BuildingBlueprints.layout(b.type(),base));
   // AD-104: the farm's field is FarmField's level-I module, its wheat seeded by the village and the farm's place, so every chunk lays the same field.
   if(b.type().equals("farm"))map.putAll(FarmField.layout(base,FarmField.modules(1),fieldSeed(s,base)));
  }
  return map;
 }
 /** AD-104: what seeds the ages of a generated field's wheat: the village and where its farm stands. */
 static long fieldSeed(Settlement s,BlockPos base){return s.id().getMostSignificantBits()^base.asLong();}
 public static Map<BlockPos,BlockState> roads(BlockPos origin,Settlement s){
  Set<BlockPos> blocked=new HashSet<>();List<BlockPos> entrances=new ArrayList<>();
  for(var b:s.buildings()){
   var d=BuildingBlueprints.design(b.type());blocked.addAll(lot(s,b));
   entrances.add(new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1));
  }
  Set<BlockPos> network=new LinkedHashSet<>();network.add(entrances.get(0));
  for(BlockPos start:entrances){
   if(network.contains(start))continue;
   Queue<BlockPos> queue=new ArrayDeque<>();Map<BlockPos,BlockPos> previous=new HashMap<>();queue.add(start);previous.put(start,start);BlockPos reached=null;
   while(!queue.isEmpty()){
    BlockPos p=queue.remove();if(network.contains(p)){reached=p;break;}
    for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
     BlockPos n=p.relative(direction);if(n.getX()<-52||n.getX()>52||n.getZ()<-52||n.getZ()>52||blocked.contains(n)||previous.containsKey(n))continue;
     previous.put(n,p);queue.add(n);
    }
   }
   if(reached==null)throw new IllegalStateException("Unreachable natural village entrance");
   for(BlockPos p=reached;;p=previous.get(p)){network.add(p);if(p.equals(start))break;}
  }
  Set<BlockPos> widened=new LinkedHashSet<>(network);for(BlockPos p:network)for(var n:List.of(p.east(),p.south()))if(!blocked.contains(n))widened.add(n);
  Map<BlockPos,BlockState> map=new LinkedHashMap<>();for(BlockPos p:widened)map.put(origin.offset(p),Blocks.DIRT_PATH.defaultBlockState());return map;
 }
 /** Grade the connected road surface toward terrain, retaining walkable single-block transitions at entrances. */
 /** The initial roads a piece of this layout version lays — straight roads over graded ground from version 4, terrain roads in version 3,
  *  the first ones before — so the generator and every check of it read the same roads. */
 public static Map<BlockPos,BlockState> generatedRoads(int version,BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  return version>=4?straightRoads(origin,s,terrain):version>=3?terrainRoads(origin,s,terrain):legacyTerrainRoads(origin,s,terrain);
 }
 public static Map<BlockPos,BlockState> legacyTerrainRoads(BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  Set<BlockPos> blocked=new HashSet<>();List<BlockPos> entrances=new ArrayList<>();
  for(var b:s.buildings()){
   var d=BuildingBlueprints.design(b.type());blocked.addAll(legacyLot(s,b));
   entrances.add(new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1));
  }
  record Visit(BlockPos pos,int cost,long order){}
  Set<BlockPos> network=new LinkedHashSet<>();network.add(entrances.get(0));Map<BlockPos,Integer> heights=new HashMap<>();long sequence=0;
  // Route on a four-block terrain sample; grade every final road column exactly below.
  java.util.function.ToIntFunction<BlockPos> height=p->{var sample=new BlockPos(Math.floorDiv(p.getX(),4)*4,0,Math.floorDiv(p.getZ(),4)*4);return heights.computeIfAbsent(sample,q->terrain.applyAsInt(origin.offset(q)));};
  for(var start:entrances){
   if(network.contains(start))continue;
   var queue=new PriorityQueue<Visit>(Comparator.comparingInt(Visit::cost).thenComparingLong(Visit::order));
   Map<BlockPos,Integer> distance=new HashMap<>();Map<BlockPos,BlockPos> previous=new HashMap<>();
   queue.add(new Visit(start,0,sequence++));distance.put(start,0);BlockPos end=null;
   while(!queue.isEmpty()){
    var visit=queue.remove();var p=visit.pos();if(visit.cost()!=distance.get(p))continue;if(network.contains(p)){end=p;break;}
    int y=height.applyAsInt(p);
    for(var d:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
     var n=p.relative(d);if(Math.abs(n.getX())>org.villageastra.domain.OrganicLots.reach(s.lotLayout())+1||Math.abs(n.getZ())>org.villageastra.domain.OrganicLots.reach(s.lotLayout())+1||blocked.contains(n))continue;
     int rise=Math.abs(y-height.applyAsInt(n));
     int jitter=Math.floorMod(n.getX()*7349+n.getZ()*9151+(int)s.id().getLeastSignificantBits(),7);
     int cost=visit.cost()+20+rise*rise*12+jitter;
     if(cost>=distance.getOrDefault(n,Integer.MAX_VALUE))continue;
     distance.put(n,cost);previous.put(n,p);queue.add(new Visit(n,cost,sequence++));
    }
   }
   if(end==null)throw new IllegalStateException("Unreachable terrain road");
   for(var p=end;;p=previous.get(p)){network.add(p);if(p.equals(start))break;}
  }
  var widened=new LinkedHashSet<>(network);for(var p:network)for(var n:List.of(p.east(),p.south()))if(!blocked.contains(n))widened.add(n);
  Map<BlockPos,BlockState> flat=new LinkedHashMap<>();for(var p:widened)flat.put(origin.offset(p),Blocks.DIRT_PATH.defaultBlockState());
  return conformRoads(flat,origin,s,terrain);
 }
 public static Map<BlockPos,BlockState> terrainRoads(BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  Set<BlockPos> blocked=new HashSet<>();List<BlockPos> entrances=new ArrayList<>();
  for(var b:s.buildings()){
   var d=BuildingBlueprints.design(b.type());blocked.addAll(legacyLot(s,b));
   entrances.add(new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-2));
  }
  record Visit(BlockPos pos,int cost,long order){}
  Set<BlockPos> network=new LinkedHashSet<>();network.add(entrances.get(0));Map<BlockPos,Integer> heights=new HashMap<>();long sequence=0;
  // Route on a four-block terrain sample; grade every final road column exactly below.
  java.util.function.ToIntFunction<BlockPos> height=p->{var sample=new BlockPos(Math.floorDiv(p.getX(),4)*4,0,Math.floorDiv(p.getZ(),4)*4);return heights.computeIfAbsent(sample,q->terrain.applyAsInt(origin.offset(q)));};
  for(var start:entrances){
   if(network.contains(start))continue;
   var queue=new PriorityQueue<Visit>(Comparator.comparingInt(Visit::cost).thenComparingLong(Visit::order));
   Map<BlockPos,Integer> distance=new HashMap<>();Map<BlockPos,BlockPos> previous=new HashMap<>();
   queue.add(new Visit(start,0,sequence++));distance.put(start,0);BlockPos end=null;
   while(!queue.isEmpty()){
    var visit=queue.remove();var p=visit.pos();if(visit.cost()!=distance.get(p))continue;if(network.contains(p)){end=p;break;}
    int y=height.applyAsInt(p);
    for(var d:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
     var n=p.relative(d);if(Math.abs(n.getX())>org.villageastra.domain.OrganicLots.reach(s.lotLayout())+1||Math.abs(n.getZ())>org.villageastra.domain.OrganicLots.reach(s.lotLayout())+1||!roadClear(n,blocked))continue;
     int rise=Math.abs(y-height.applyAsInt(n));
     int jitter=Math.floorMod(n.getX()*7349+n.getZ()*9151+(int)s.id().getLeastSignificantBits(),7);
     int cost=visit.cost()+20+rise*rise*12+jitter;
     if(cost>=distance.getOrDefault(n,Integer.MAX_VALUE))continue;
     distance.put(n,cost);previous.put(n,p);queue.add(new Visit(n,cost,sequence++));
    }
   }
   if(end==null)throw new IllegalStateException("Unreachable terrain road");
   for(var p=end;;p=previous.get(p)){network.add(p);if(p.equals(start))break;}
  }
  var widened=new LinkedHashSet<BlockPos>();for(var p:network)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)widened.add(p.offset(dx,0,dz));
  Map<BlockPos,BlockState> flat=new LinkedHashMap<>();for(var p:widened)flat.put(origin.offset(p),Blocks.DIRT_PATH.defaultBlockState());
  return conformRoads(flat,origin,s,terrain);
 }
 private static boolean roadClear(BlockPos center,Set<BlockPos> blocked){
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(blocked.contains(center.offset(x,0,z)))return false;
  return true;
 }
 /** Grade the connected road surface toward terrain, retaining walkable single-block transitions at entrances. */
 public static Map<BlockPos,BlockState> conformRoads(Map<BlockPos,BlockState> flat,BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  Map<BlockPos,Integer> heights=new LinkedHashMap<>();Map<BlockPos,Integer> ceilings=new HashMap<>();
  for(BlockPos pos:flat.keySet())heights.put(pos,terrain.applyAsInt(pos));
  for(var b:s.buildings()){
   var d=BuildingBlueprints.design(b.type());var entrance=origin.offset(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1);
   if(heights.containsKey(entrance)){heights.put(entrance,origin.getY()+b.y());ceilings.put(entrance,origin.getY()+b.y());}
  }
  Queue<BlockPos> limits=new ArrayDeque<>(ceilings.keySet());
  while(!limits.isEmpty()){
   BlockPos p=limits.remove();int h=ceilings.get(p);
   for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
    BlockPos n=p.relative(direction);
    if(heights.containsKey(n)&&ceilings.getOrDefault(n,Integer.MAX_VALUE)>h+1){ceilings.put(n,h+1);limits.add(n);}
   }
  }
  heights.replaceAll((p,y)->Math.min(y,ceilings.getOrDefault(p,y)));
  Queue<BlockPos> queue=new ArrayDeque<>(heights.keySet());
  while(!queue.isEmpty()){
   BlockPos p=queue.remove();int h=heights.get(p);
   for(var direction:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST)){
    BlockPos n=p.relative(direction);Integer old=heights.get(n);
    if(old!=null&&old<h-1){heights.put(n,h-1);queue.add(n);}
   }
  }
  Map<BlockPos,BlockState> result=new LinkedHashMap<>();heights.forEach((p,y)->result.put(new BlockPos(p.getX(),y,p.getZ()),Blocks.DIRT_PATH.defaultBlockState()));return result;
 }
 public static void placeInitialRoad(net.minecraft.world.level.LevelAccessor world,BlockPos pos,BlockState state,int ground){
  // Dirt paths schedule a conversion when covered: clear headroom before installing the surface.
  for(int y=pos.getY()+1;y<=Math.max(pos.getY()+2,ground+2);y++)world.setBlock(new BlockPos(pos.getX(),y,pos.getZ()),Blocks.AIR.defaultBlockState(),2);
  world.setBlock(pos,state,2);
  for(int down=1;down<=20;down++){
   BlockPos support=pos.below(down);if(!world.getBlockState(support).canBeReplaced())break;
   world.setBlock(support,Blocks.DIRT.defaultBlockState(),2);
  }
 }
 /** AD-063, AD-104: a building's lot on the ground: its design; a farm's is its farmhouse with the whole box of the level-I field it is
  *  generated with (x -1..7, z 9..17); AD-112: with the field it keeps free for its builders (level II: z to 26), so x -1..7, z 0..26 of
  *  the farm. AD-130/AD-131: a settlement of layout OrganicLots.BARN_LOTS keeps a farm's final footprint (OrganicLots.farmReserve, on its
  *  side) and a forester's 15x21 lot. Older layouts keep the lots they were generated with, whatever the field table and the designs
  *  become. The lot starts lotWest blocks from the building's x. */
 private static final int[] FIELD_II={-1,9,7,26},FIELD_I={-1,9,7,17};
 private static boolean barn(Settlement s){return s.lotLayout()>=org.villageastra.domain.OrganicLots.BARN_LOTS;}
 static int lotWest(Settlement s,Settlement.Building b){
  if(!b.type().equals("farm"))return 0;
  return barn(s)?Math.min(0,org.villageastra.domain.OrganicLots.farmReserve(s.westField(b.id()))[0]):Math.min(0,FIELD_II[0]);
 }
 static int lotWidth(Settlement s,Settlement.Building b){
  int w=BuildingBlueprints.design(b.type()).width();
  if(b.type().equals("farm")){int east=barn(s)?org.villageastra.domain.OrganicLots.farmReserve(s.westField(b.id()))[2]:FIELD_II[2];return Math.max(w-1,east)-lotWest(s,b)+1;}
  return barn(s)&&b.type().equals("forester")?Math.max(w,org.villageastra.domain.OrganicLots.FORESTER_WIDTH):w;
 }
 static int lotDepth(Settlement s,Settlement.Building b){
  int d=BuildingBlueprints.design(b.type()).depth();
  if(b.type().equals("farm"))return Math.max(d-1,barn(s)?org.villageastra.domain.OrganicLots.farmReserve(false)[3]:FIELD_II[3])+1;
  return barn(s)&&b.type().equals("forester")?Math.max(d,org.villageastra.domain.OrganicLots.FORESTER_DEPTH):d;
 }
 /** Columns of a building's lot, relative to the origin at y 0. */
 public static List<BlockPos> lot(Settlement s,Settlement.Building b){var out=new ArrayList<BlockPos>();int w=lotWidth(s,b),d=lotDepth(s,b),west=lotWest(s,b);for(int x=0;x<w;x++)for(int z=0;z<d;z++)out.add(new BlockPos(b.x()+west+x,0,b.z()+z));return out;}
 /** The lot a layout of version 3 and older was made with: the farm's holds only its level-I field (z to 17), so its legacy terrain roads
  *  keep the passages they had before AD-112 reserved the level-II field; the forester's holds the 9x17 those layouts drew (AD-131 gives the
  *  hut 15x21 from BARN_LOTS on — an older village keeps the passages between its lots, and its hut may stand over its own road). */
 static List<BlockPos> legacyLot(Settlement s,Settlement.Building b){
  if(b.type().equals(ForesterHut.TYPE)&&!barn(s)){var out=new ArrayList<BlockPos>();int w=org.villageastra.domain.OrganicLots.width(b.type(),s.lotLayout()),d=org.villageastra.domain.OrganicLots.depth(b.type(),s.lotLayout());
   for(int x=0;x<w;x++)for(int z=0;z<d;z++)out.add(new BlockPos(b.x()+x,0,b.z()+z));return out;}
  if(!b.type().equals("farm")||barn(s))return lot(s,b);int d=Math.max(BuildingBlueprints.design(b.type()).depth()-1,FIELD_I[3])+1;var out=new ArrayList<BlockPos>();for(int x=0;x<lotWidth(s,b);x++)for(int z=0;z<d;z++)out.add(new BlockPos(b.x()+lotWest(s,b)+x,0,b.z()+z));return out;}
 /** AD-104/AD-112/AD-130: farm-local columns (y 0) kept clear of trees over a farm: the level-II field of the older layouts (x -1..7,
  *  z 9..26); from BARN_LOTS on its whole final footprint on its side but the farmhouse's own columns, so the barn finds open air. */
 public static List<BlockPos> farmClearing(Settlement s,Settlement.Building b){
  var out=new ArrayList<BlockPos>();
  if(!barn(s)){for(int x=FIELD_II[0];x<=FIELD_II[2];x++)for(int z=FIELD_II[1];z<=FIELD_II[3];z++)out.add(new BlockPos(x,0,z));return out;}
  var box=org.villageastra.domain.OrganicLots.farmReserve(s.westField(b.id()));var d=BuildingBlueprints.design(b.type());
  for(int x=box[0];x<=box[2];x++)for(int z=box[1];z<=box[3];z++)if(x<0||x>=d.width()||z<0||z>=d.depth())out.add(new BlockPos(x,0,z));
  return out;
 }
 /** Protection strip kept flat around a lot and cleared of trees around roads and lots. */
 public static final int STRIP=3;
 /** Entrance cell in front of a door, where the road meets the floor: the middle of the design, not of a wider lot. */
 static BlockPos door(Settlement.Building b){return new BlockPos(b.x()+BuildingBlueprints.doorX(b.type()),0,b.z()-1);}
 /** Centre lines of the roads: from the hall outwards, each building joins the nearest point of the roads already laid,
  *  by the way with the fewest turns and the gentlest climb. Positions are relative to the origin at y 0. */
 public static List<List<BlockPos>> network(BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  Set<BlockPos> blocked=new HashSet<>();List<BlockPos> entrances=new ArrayList<>();
  for(var b:s.buildings()){
   blocked.addAll(lot(s,b));
   entrances.add(door(b).north());
  }
  var level=smoothGround(origin,terrain);
  var hall=entrances.get(0);var order=new ArrayList<>(entrances.subList(1,entrances.size()));
  order.sort(Comparator.comparingInt(p->Math.abs(p.getX()-hall.getX())+Math.abs(p.getZ()-hall.getZ())));
  Set<BlockPos> network=new LinkedHashSet<>();network.add(hall);var paths=new ArrayList<List<BlockPos>>();
  var directions=List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST);
  record Visit(BlockPos pos,int dir,int cost,long order){}
  long sequence=0;
  for(var start:order){
   if(network.contains(start))continue;
   var queue=new PriorityQueue<Visit>(Comparator.comparingInt(Visit::cost).thenComparingLong(Visit::order));
   Map<Long,Integer> best=new HashMap<>();Map<Long,Long> previous=new HashMap<>();
   long first=state(start,4);queue.add(new Visit(start,4,0,sequence++));best.put(first,0);Long end=null;
   while(!queue.isEmpty()){
    var v=queue.remove();long key=state(v.pos(),v.dir());if(v.cost()!=best.get(key))continue;
    if(network.contains(v.pos())){end=key;break;}
    int here=level.applyAsInt(v.pos());
    for(int d=0;d<4;d++){
     var n=v.pos().relative(directions.get(d));
     if(Math.abs(n.getX())>52||Math.abs(n.getZ())>52||!roadClear(n,blocked))continue;
     int rise=Math.abs(here-level.applyAsInt(n));
     int cost=v.cost()+10+(v.dir()!=4&&v.dir()!=d?35:0)+rise*6;
     long next=state(n,d);
     if(cost>=best.getOrDefault(next,Integer.MAX_VALUE))continue;
     best.put(next,cost);previous.put(next,key);queue.add(new Visit(n,d,cost,sequence++));
    }
   }
   if(end==null)throw new IllegalStateException("Unreachable village entrance");
   var path=new ArrayList<BlockPos>();
   for(Long k=end;k!=null;k=previous.get(k)){var p=unstate(k);if(path.isEmpty()||!path.get(path.size()-1).equals(p))path.add(p);}
   Collections.reverse(path);network.addAll(path);paths.add(path);
  }
  return paths;
 }
 private static long state(BlockPos p,int dir){return ((long)(p.getX()+512)<<22|(long)(p.getZ()+512)<<11)<<3|dir;}
 private static BlockPos unstate(long k){long v=k>>3;return new BlockPos((int)(v>>22)-512,0,(int)((v>>11)&2047)-512);}
 /** The ground under a column, averaged over seven by seven blocks: roads and yards follow the lie of the land, not every bump. */
 static java.util.function.ToIntFunction<BlockPos> smoothGround(BlockPos origin,java.util.function.ToIntFunction<BlockPos> terrain){
  Map<Long,Integer> ground=new HashMap<>();Map<Long,Integer> smooth=new HashMap<>();
  java.util.function.ToIntFunction<BlockPos> top=p->ground.computeIfAbsent(BlockPos.asLong(p.getX(),0,p.getZ()),k->terrain.applyAsInt(origin.offset(p.getX(),0,p.getZ())));
  return p->smooth.computeIfAbsent(BlockPos.asLong(p.getX(),0,p.getZ()),k->{int sum=0;for(int dx=-3;dx<=3;dx+=2)for(int dz=-3;dz<=3;dz+=2)sum+=top.applyAsInt(new BlockPos(p.getX()+dx,0,p.getZ()+dz));return Math.round(sum/16F);});
 }
 /** AD-063: the roads of a generated village, three blocks wide, on the smoothed ground; at a door the road is level with the floor,
  *  and between two neighbouring road blocks the surface never changes by more than one block. */
 public static Map<BlockPos,BlockState> straightRoads(BlockPos origin,Settlement s,java.util.function.ToIntFunction<BlockPos> terrain){
  var level=smoothGround(origin,terrain);
  Map<BlockPos,Integer> heights=new LinkedHashMap<>();
  for(var path:network(origin,s,terrain))for(var p:path)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var c=p.offset(dx,0,dz);heights.putIfAbsent(c,level.applyAsInt(c));}
  // The hall entrance is part of the network even when no path starts there.
  for(var b:s.buildings()){var at=door(b).north();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var c=at.offset(dx,0,dz);heights.putIfAbsent(c,level.applyAsInt(c));}}
  Map<BlockPos,Integer> pins=new HashMap<>();
  for(var b:s.buildings()){var d=door(b);if(heights.containsKey(d))pins.put(d,origin.getY()+b.y());}
  var graded=grade(heights,pins);
  Map<BlockPos,BlockState> result=new LinkedHashMap<>();
  graded.forEach((p,y)->result.put(new BlockPos(origin.getX()+p.getX(),y,origin.getZ()+p.getZ()),Blocks.DIRT_PATH.defaultBlockState()));
  return result;
 }
 /** Pinned cells keep their height; every other cell moves as little as it must so neighbours differ by at most one block. */
 static Map<BlockPos,Integer> grade(Map<BlockPos,Integer> heights,Map<BlockPos,Integer> pins){
  var h=new LinkedHashMap<>(heights);h.putAll(pins);
  var directions=List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST);
  // Upper bound from every pin and every cell, then lower bound: after both passes no step is higher than one block.
  Map<BlockPos,Integer> ceiling=new HashMap<>(pins);Queue<BlockPos> queue=new ArrayDeque<>(pins.keySet());
  while(!queue.isEmpty()){var p=queue.remove();int c=ceiling.get(p);
   for(var d:directions){var n=p.relative(d);if(!h.containsKey(n)||pins.containsKey(n))continue;if(ceiling.getOrDefault(n,Integer.MAX_VALUE)>c+1){ceiling.put(n,c+1);queue.add(n);}}}
  Map<BlockPos,Integer> floor=new HashMap<>(pins);queue=new ArrayDeque<>(pins.keySet());
  while(!queue.isEmpty()){var p=queue.remove();int f=floor.get(p);
   for(var d:directions){var n=p.relative(d);if(!h.containsKey(n)||pins.containsKey(n))continue;if(floor.getOrDefault(n,Integer.MIN_VALUE)<f-1){floor.put(n,f-1);queue.add(n);}}}
  h.replaceAll((p,y)->pins.containsKey(p)?y:Math.max(floor.getOrDefault(p,Integer.MIN_VALUE),Math.min(ceiling.getOrDefault(p,Integer.MAX_VALUE),y)));
  boolean changed=true;
  for(int round=0;round<64&&changed;round++){changed=false;
   for(var p:h.keySet()){if(pins.containsKey(p))continue;int y=h.get(p);
    for(var d:directions){Integer n=h.get(p.relative(d));if(n==null)continue;
     if(y>n+1){y=n+1;changed=true;}else if(y<n-1){y=n-1;changed=true;}}
    h.put(p,y);}}
  return h;
 }
 /** AD-063: the strip around every lot is levelled to the building's floor, so a door always opens onto flat ground; relative columns → floor y.
  *  A column between two lots belongs to the nearer one; columns inside any lot are the building's own. */
 public static Map<BlockPos,Integer> lots(BlockPos origin,Settlement s){
  Map<BlockPos,Integer> out=new LinkedHashMap<>();Map<BlockPos,Integer> distance=new HashMap<>();Set<BlockPos> inside=new HashSet<>();
  for(var b:s.buildings())inside.addAll(lot(s,b));
  for(var b:s.buildings()){int w=lotWidth(s,b),d=lotDepth(s,b);
   for(int x=-STRIP;x<w+STRIP;x++)for(int z=-STRIP;z<d+STRIP;z++){
    var c=new BlockPos(b.x()+lotWest(s,b)+x,0,b.z()+z);if(inside.contains(c))continue;
    int gap=Math.max(Math.max(-x,x-(w-1)),Math.max(-z,z-(d-1)));
    if(gap<distance.getOrDefault(c,Integer.MAX_VALUE)){distance.put(c,gap);out.put(c,origin.getY()+b.y());}
   }}
  return out;
 }
 /** Columns of the village territory: every lot and its strip, every road block and its strip. Relative to the origin. */
 public static Set<BlockPos> territory(Settlement s,Collection<BlockPos> roadColumns){
  var out=new HashSet<BlockPos>();
  for(var b:s.buildings())for(int x=-STRIP;x<lotWidth(s,b)+STRIP;x++)for(int z=-STRIP;z<lotDepth(s,b)+STRIP;z++)out.add(new BlockPos(b.x()+lotWest(s,b)+x,0,b.z()+z));
  for(var r:roadColumns)for(int x=-STRIP;x<=STRIP;x++)for(int z=-STRIP;z<=STRIP;z++)out.add(new BlockPos(r.getX()+x,0,r.getZ()+z));
  return out;
 }
}
