package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.Tags;
import org.villageastra.server.MayorSurvey;
import org.villageastra.server.OwnershipEvents;
import org.villageastra.server.SettlementData;
/** AD-057: orders the mayor gives on the settlement map — a house, a road, or clearing a piece of land down to a level.
 *  Builders take away soil, wood and whatever was built; stone and ore are left to the miners. Nothing is dug deeper than
 *  DIG_BELOW blocks under the lowest foundation of the settlement. */
public final class MapOrders {
 public static final int HOUSE=0,ROAD=1,DEMOLISH=2,WALL=3;
 /** AD-125: a building moved to a new place — a is the new origin, b the old one, the variant its quarter turns. */
 public static final int RELOCATE=4;
 /** Widest side of one clearing, the most blocks one order may hold, and how far under the lowest foundation anything may be dug. */
 public static final int SPAN=32,MAX_CELLS=6000,DIG_BELOW=10;
 private MapOrders(){}
 /** Lowest foundation of the settlement: the hall ground and every building. */
 public static int lowest(SettlementData.Entry e){int y=e.center().getY();for(var b:e.settlement().buildings())y=Math.min(y,e.center().getY()+b.y());return y;}
 /** Nothing ordered from the map is dug below this level. */
 public static int floor(SettlementData.Entry e){return lowest(e)-DIG_BELOW;}
 /** Stone and ore: only a miner takes these out of the world, a builder never does. */
 public static boolean minersOnly(BlockState s){
  return s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.BASE_STONE_NETHER)||s.is(Tags.Blocks.STONE)||s.is(Tags.Blocks.ORES)||s.is(Tags.Blocks.COBBLESTONE)
   ||s.is(Tags.Blocks.SANDSTONE)||s.is(Tags.Blocks.OBSIDIAN)||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK)||s.is(Blocks.SMOOTH_BASALT)||s.is(Blocks.AMETHYST_BLOCK);
 }
 /** What a clearing would take: builder blocks and miner blocks top first, what it has to leave, and why it cannot be ordered at all. */
 public record Clearing(String reason,List<BlockPos> builders,List<BlockPos> miners,int kept,int deep,int level,int floor){
  static Clearing refused(String reason,int floor){return new Clearing(reason,List.of(),List.of(),0,0,0,floor);}
 }
 /** AD-123: the atlas area grows with the best cartographer's level, and village roads may be laid anywhere in it. */
 private static boolean inside(ServerLevel l,SettlementData.Entry e,BlockPos a){return Atlas.area(l,e).contains(new ChunkPos(a));}
 /** Cells a clearing must leave alone: buildings of the settlement, registered roads, containers, unbreakable blocks, scaffolds and land protected for somebody else. */
 private static boolean kept(ServerLevel l,SettlementData.Entry e,BlockPos pos,BlockState state){
  if(l.getBlockEntity(pos)!=null||state.getBlock().defaultDestroyTime()<0||state.is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return true;
  if(OwnershipEvents.disallowedPlacement(l,pos)||Roads.cell(l,pos)!=null)return true;
  for(var b:e.settlement().buildings()){var size=BuildingPlacement.size(b.type(),b.rotation());var at=e.center().offset(b.x(),b.y(),b.z());
   int w=size[0],depth=size[1];
   if(pos.getX()>=at.getX()&&pos.getX()<at.getX()+w&&pos.getZ()>=at.getZ()&&pos.getZ()<at.getZ()+depth&&pos.getY()>=at.getY()-1)return true;}
  return false;
 }
 public static Clearing clearing(ServerLevel l,SettlementData.Entry e,BlockPos a,BlockPos b,int level){
  int floor=floor(e);
  if(!inside(l,e,a)||!inside(l,e,b))return Clearing.refused("outside",floor);
  int x0=Math.min(a.getX(),b.getX()),x1=Math.max(a.getX(),b.getX()),z0=Math.min(a.getZ(),b.getZ()),z1=Math.max(a.getZ(),b.getZ());
  if(x1-x0+1>SPAN||z1-z0+1>SPAN)return Clearing.refused("size",floor);
  // The protection: whatever level is asked for, nothing below the floor is taken.
  int keepAbove=Math.max(level,floor-1),deep=Math.max(0,floor-1-level);
  var builders=new ArrayList<BlockPos>();var miners=new ArrayList<BlockPos>();int kept=0;
  for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
   if(!l.hasChunkAt(new BlockPos(x,0,z)))return Clearing.refused("unloaded",floor);
   int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
   for(int y=top;y>keepAbove;y--){var pos=new BlockPos(x,y,z);var state=l.getBlockState(pos);
    if(state.isAir()||state.getBlock() instanceof LiquidBlock)continue;
    if(kept(l,e,pos,state)){kept++;continue;}
    (minersOnly(state)?miners:builders).add(pos);
    if(builders.size()+miners.size()>MAX_CELLS)return Clearing.refused("too_many",floor);}
  }
  Comparator<BlockPos> order=Comparator.<BlockPos>comparingInt(p->-p.getY()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
  builders.sort(order);miners.sort(order);
  return new Clearing("",builders,miners,kept,deep,keepAbove,floor);
 }
 /** Clears the marked land: one crew project for the builders and one excavation for the miners, both paid out in real drops. */
 public static String demolish(ServerLevel l,SettlementData.Entry e,BlockPos a,BlockPos b,int level){
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  var c=clearing(l,e,a,b,level);if(!c.reason().isEmpty())return c.reason();
  var id=e.settlement().id();
  if(c.builders().isEmpty()&&c.miners().isEmpty())return "nothing";
  if(!c.builders().isEmpty()&&Roads.active(l,id))return "busy_builders";
  if(!c.miners().isEmpty()&&Excavation.pending(l,id))return "busy_miners";
  if(!c.builders().isEmpty()&&!Roads.order(l,e,Roads.clearing(l,e,c.builders())))return "busy_builders";
  if(!c.miners().isEmpty())Excavation.order(l,e,c.miners());
  return "";
 }
 /** Route 0..2, surface 0..3, lamps, fence and a width of one to five blocks. */
 public static boolean valid(int variant){return variant>=0&&variant<512&&((variant>>6)&7)<=5&&!((variant&3)==MayorSurvey.AREA&&((variant>>5)&1)==1);}
 /** Road cells of a route and the ones that stand in its way. */
 public static Map<BlockPos,BlockState> route(ServerLevel l,BlockPos a,BlockPos b,int variant){
  return MayorSurvey.road(a,b,variant&3,MayorSurvey.width(variant),pos->l.hasChunkAt(pos)?l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())-1:Integer.MIN_VALUE);
 }
 public static List<BlockPos> blocked(ServerLevel l,Map<BlockPos,BlockState> cells){
  var out=new ArrayList<BlockPos>();for(var cell:cells.keySet())if(MayorSurvey.blocked(l,cell,true))out.add(cell);return out;
 }
 /** Lays a road as a builder project: prepared path, gravel, cobble or paving, with lanterns and a fence when asked. */
 public static String road(ServerLevel l,SettlementData.Entry e,BlockPos a,BlockPos b,int variant){
  if(!valid(variant))return "variant";
  // AD-070: a siege stops every construction order, whichever tool gives it.
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  // AD-077: the surface, the lamps, the fence and a road wider than three blocks each take their research.
  var research=ResearchGate.roadRefusal(l,e,variant);if(!research.isEmpty())return research;
  if(!inside(l,e,a)||!inside(l,e,b))return "outside";
  boolean area=(variant&3)==MayorSurvey.AREA;
  var line=MayorSurvey.line(a,b,area?0:variant&3);if(line.isEmpty())return "length";
  var cells=route(l,a,b,variant);if(cells.isEmpty())return area?"size":"unloaded";
  if(!blocked(l,cells).isEmpty())return "blocked";
  if(Roads.active(l,e.settlement().id()))return "busy_builders";
  var project=plan(l,e,a,b,variant,line,cells);
  return Roads.order(l,e,project)?"":"busy_builders";
 }
 /** The builder project of a road or a paved yard; a yard gets its lamps around its edge instead of beside a centre line. */
 private static CompoundTag plan(ServerLevel l,SettlementData.Entry e,BlockPos a,BlockPos b,int variant,List<BlockPos> line,Map<BlockPos,BlockState> cells){
  boolean area=(variant&3)==MayorSurvey.AREA,light=((variant>>4)&1)==1;
  var project=Roads.plan(l,e,line,cells,(variant>>2)&3,light&&!area,((variant>>5)&1)==1&&!area);
  if(area&&light){
   int x0=Math.min(a.getX(),b.getX())-1,x1=Math.max(a.getX(),b.getX())+1,z0=Math.min(a.getZ(),b.getZ())-1,z1=Math.max(a.getZ(),b.getZ())+1;
   var ring=new ArrayList<BlockPos>();
   for(int x=x0;x<=x1;x+=Roads.LAMP_SPACING){ring.add(new BlockPos(x,0,z0));ring.add(new BlockPos(x,0,z1));}
   for(int z=z0+Roads.LAMP_SPACING;z<z1;z+=Roads.LAMP_SPACING){ring.add(new BlockPos(x0,0,z));ring.add(new BlockPos(x1,0,z));}
   ring.add(new BlockPos(x1,0,z0));ring.add(new BlockPos(x1,0,z1));
   Roads.addLamps(l,project,ring,cells.keySet());
  }
  return project;
 }
 public static String house(ServerLevel l,SettlementData.Entry e,String design,BlockPos origin){return house(l,e,design,origin,0);}
 /** AD-068: the house turned by quarter turns clockwise. */
 public static String house(ServerLevel l,SettlementData.Entry e,String design,BlockPos origin,int turns){
  if(turns<0||turns>3)return "rotation";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  if(!inside(l,e,origin))return "outside";
  if(BuildingBlueprints.designs().stream().noneMatch(d->d.id().equals(design))||!BuildingOrders.ORDERABLE.contains(design))return "design";
  return BuildingOrders.approve(l,e,design,turns,origin);
 }
 /** A dry run for the map: what the order would do, without changing anything. */
 public static CompoundTag preview(ServerLevel l,SettlementData.Entry e,int tool,BlockPos a,BlockPos b,int variant,int level){
  if(tool==RELOCATE){var r=Relocations.preview(l,e,b,a,variant);r.putInt("tool",tool);return r;}
  var t=new CompoundTag();t.putInt("tool",tool);t.putInt("floor",floor(e));t.putInt("lowest",lowest(e));
  // AD-094: the castle wall — variant is the shape (0 square, 1 round), level its radius from the hall.
  // AD-127: variant 2 is the wall fitted to the village, level its headroom; the ring itself is sent for the map to draw.
  if(tool==WALL){var shape=variant==2?Walls.Shape.FITTED:variant==1?Walls.Shape.ROUND:Walls.Shape.SQUARE;var plan=shape==Walls.Shape.FITTED?Walls.previewFitted(l,e,level):Walls.plan(l,e,shape,level);
   var refusal=Walls.refusal(l,e);t.putString("reason",!refusal.isEmpty()?refusal:plan.reason());t.putInt("radius",level);t.putInt("shape",variant);
   t.putInt("cells",plan.blocks().size());t.putInt("gates",plan.gates().size());t.putInt("towers",plan.towers().size());
   t.putLongArray("gateCells",plan.gates().stream().mapToLong(BlockPos::asLong).toArray());t.putLongArray("towerCells",plan.towers().stream().mapToLong(BlockPos::asLong).toArray());
   t.putBoolean("towerResearch",ResearchGate.has(l,e,"defense.2"));t.putBoolean("busyBuilders",Roads.active(l,e.settlement().id()));
   t.putLongArray("ringCells",plan.ring().stream().mapToLong(BlockPos::asLong).toArray());
   if(shape==Walls.Shape.FITTED){var old=Walls.record(l.getServer(),e.settlement().id());
    // Only what still stands comes down: the count the map shows is the stone the crew will really carry back.
    var down=plan.retire().stream().filter(p->Walls.palette(l.getBlockState(p))).toList();
    t.putLongArray("retireCells",down.stream().limit(4096).mapToLong(BlockPos::asLong).toArray());t.putInt("retire",down.size());
    t.putLongArray("pushedCells",plan.pushed().stream().limit(256).mapToLong(BlockPos::asLong).toArray());
    t.putBoolean("outgrown",old!=null&&Walls.outgrown(e,old));t.putInt("perimeter",plan.ring().size());
    var radii=WallOutline.fromX10(plan.radiiX10());if(radii.length>0){t.putInt("rmin",(int)Math.floor(WallOutline.min(radii)));t.putInt("rmax",(int)Math.ceil(WallOutline.max(radii)));}}
   return t;}
  if(tool==DEMOLISH){var c=clearing(l,e,a,b,level);t.putString("reason",c.reason());t.putInt("builders",c.builders().size());t.putInt("miners",c.miners().size());
   t.putInt("kept",c.kept());t.putInt("deep",c.deep());t.putInt("level",c.level());
   t.putBoolean("busyBuilders",Roads.active(l,e.settlement().id()));t.putBoolean("busyMiners",Excavation.pending(l,e.settlement().id()));
   t.putBoolean("miner",Excavation.miner(e)!=null);
   t.putLongArray("minerCells",c.miners().stream().limit(Plans.MAX_VOLUME).mapToLong(BlockPos::asLong).toArray());
   t.putLongArray("builderCells",c.builders().stream().limit(Plans.MAX_VOLUME).mapToLong(BlockPos::asLong).toArray());}
  else if(tool==ROAD){
   var cells=!valid(variant)?Map.<BlockPos,BlockState>of():route(l,a,b,variant);
   var stuck=blocked(l,cells);t.putInt("cells",cells.size());t.putLongArray("blocked",stuck.stream().limit(256).mapToLong(BlockPos::asLong).toArray());
   t.putLongArray("roadCells",cells.keySet().stream().mapToLong(BlockPos::asLong).toArray());
   t.putString("reason",!inside(l,e,a)||!inside(l,e,b)?"outside":cells.isEmpty()?"length":!stuck.isEmpty()?"blocked":"");
   t.putBoolean("busyBuilders",Roads.active(l,e.settlement().id()));
   if(cells.size()>0&&stuck.isEmpty()){var plan=plan(l,e,a,b,variant,MayorSurvey.line(a,b,(variant&3)==MayorSurvey.AREA?0:variant&3),cells);
    int items=0;for(var key:plan.getCompound("cost").getAllKeys())items+=plan.getCompound("cost").getInt(key);t.putInt("items",items);t.putInt("operations",plan.getList("ops",Tag.TAG_COMPOUND).size());}}
  // AD-070: the dry run does not get round the siege either — it says so instead of promising work.
  t.putBoolean("besieged",Sieges.besieged(l.getServer(),e.settlement().id()));
  if(t.getBoolean("besieged"))t.putString("reason","besieged");
  return t;
 }
}
