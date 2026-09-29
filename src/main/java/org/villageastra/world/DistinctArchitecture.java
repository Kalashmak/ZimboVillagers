package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.villageastra.VillageAstra;
import java.util.*;

/** Purpose-specific silhouettes and usable working yards. Coordinates are relative to each lot. */
public final class DistinctArchitecture {
    private DistinctArchitecture() {}
    private static void p(Map<BlockPos,BlockState> m,BlockPos b,int x,int y,int z,Block block){m.put(b.offset(x,y,z),block.defaultBlockState());}
    private static void clear(Map<BlockPos,BlockState> m,BlockPos b,int w,int d,int h){m.clear();for(int x=0;x<w;x++)for(int z=0;z<d;z++)for(int y=0;y<=h;y++)p(m,b,x,y,z,y==0?Blocks.COBBLESTONE:Blocks.AIR);}
    public static void apply(String id,BlockPos b,Map<BlockPos,BlockState> m){
        var d=BuildingBlueprints.design(id);int w=d.width(),depth=d.depth();
        // AD-131: the forester's hut has no nursery any more; "forester@N" is a plan of its own for each level.
        if(VillageStyle.has(id)){villageStyle(id,b,m,w,depth);return;}
        if(id.startsWith("town_hall")) {
            int floors=id.equals("town_hall_3")?3:id.equals("town_hall_2")?2:1;
            clear(m,b,7,7,17);
            for(int y=1;y<=floors*4;y++)for(int x=0;x<7;x++)for(int z=0;z<7;z++) {
                boolean edge=x==0||x==6||z==0||z==6;
                if(y%4==0)p(m,b,x,y,z,Blocks.SPRUCE_PLANKS);
                else if(edge)p(m,b,x,y,z,(x==0||x==6)&&(z==0||z==6)?Blocks.STRIPPED_SPRUCE_LOG:y%4==2?Blocks.GLASS:Blocks.SMOOTH_SANDSTONE);
            }
            for(int x=0;x<7;x++)for(int z=0;z<7;z++) {
                int y=floors*4+1+Math.min(x,6-x);
                m.put(b.offset(x,y,z),Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,x<=3?Direction.EAST:Direction.WEST));
            }
            // Raised roof cap leaves two blocks of headroom over the ladder opening.
            p(m,b,5,floors*4+2,5,Blocks.AIR);
            p(m,b,5,floors*4+3,5,Blocks.DARK_OAK_SLAB);
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
            m.put(b.offset(3,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));m.put(b.offset(3,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            // Ladder and floor openings provide real access to every upper floor.
            for(int y=1;y<=floors*4;y++)m.put(b.offset(5,y,5),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
            p(m,b,1,1,4,VillageAstra.OWNED_CHEST.get());p(m,b,2,1,4,Blocks.LECTERN);
            p(m,b,4,1,4,Blocks.CRAFTING_TABLE);
            for(int floor=0;floor<floors;floor++){p(m,b,1,floor*4+1,1,Blocks.LANTERN);if(floor>0)p(m,b,2,floor*4+1,4,Blocks.BOOKSHELF);}
            if(floors>1)for(int x=1;x<=5;x++)p(m,b,x,floors*4+2,0,Blocks.RED_WOOL);
            // AD-112: the town seal, the hall's core, at the grade of its storeys; off the door lane and the ladder route.
            if(floors>=2)m.put(b.offset(3,1,3),Cores.state("town_hall",floors));
        } else if(id.equals("school")) {
            // Flat classroom wings around a light-filled inner patio.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
                if(x<3||x>w-4||z<3||z>depth-4)p(m,b,x,4,z,Blocks.BRICK_SLAB);
                else {for(int y=1;y<=3;y++)p(m,b,x,y,z,Blocks.AIR);p(m,b,x,0,z,Blocks.MOSS_BLOCK);}
            }
        } else if(id.equals("home")) {
            cottage(m,b,w,depth,1);
        } else if(id.equals("home_2")) {
            // A real second storey instead of a stretched single-storey cottage; the ladder inside leads up to it.
            cottage(m,b,w,depth,2);
            for(int y=1;y<=7;y++)m.put(b.offset(1,y,depth-2),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
            m.put(b.offset(w/2,7,depth/2),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
    }
 private static final Map<String,BlockState> STATES=new java.util.concurrent.ConcurrentHashMap<>();
 /** AD-122: a village-style design ({@link VillageStyle}) replaces the catalogue box whole, bottom course first; its door, beds, stock chest and
  *  stations stand in the cells the earlier design used. */
 private static void villageStyle(String id,BlockPos b,Map<BlockPos,BlockState> m,int w,int depth){
  var lot=VillageStyle.lot(id);if(lot[0]!=w||lot[1]!=depth)throw new IllegalStateException("Village style "+id+" drawn for "+lot[0]+"x"+lot[1]+", lot is "+w+"x"+depth);
  m.clear();
  var cells=new ArrayList<>(VillageStyle.plan(id).entrySet());cells.sort(Comparator.comparingInt(e->e.getKey().y()));
  for(var e:cells){var c=e.getKey();String spec=e.getValue();
   m.put(b.offset(c.x(),c.y(),c.z()),spec.equals(VillageStyle.CHEST)?VillageAstra.OWNED_CHEST.get().defaultBlockState():spec.startsWith(VillageStyle.CHEST)?chest(spec):STATES.computeIfAbsent(spec,DistinctArchitecture::parse));}
 }
 /** AD-147: a stock chest with its facing and the half of its double chest ("#chest[facing=east,type=left]": a page of the store). */
 static BlockState chest(String spec){var s=state(spec.substring(1));
  return VillageAstra.OWNED_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING,s.getValue(ChestBlock.FACING)).setValue(ChestBlock.TYPE,s.getValue(ChestBlock.TYPE));
 }
 /** A village-style block-state string as a state (cached). */
 static BlockState state(String spec){return STATES.computeIfAbsent(spec,DistinctArchitecture::parse);}
 private static BlockState parse(String spec){
  try{return net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),spec,false).blockState();}
  catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){throw new IllegalArgumentException("Village style block state "+spec,ex);}
 }
 /** AD-071: the residents' houses as timber-framed cottages — a stone brick plinth, pale birch walls in a dark spruce frame with posts at the corners,
  *  beside the door and in the middle of each wall, two-block pane windows, a log plate round every ceiling, and a dark oak roof trimmed in spruce
  *  with a window and a king post in each gable and a brick chimney. Everything stays inside the lot; the door, beds, chest, table, lamp and floors
  *  stay where housing, roads and the stock expect them, and every block is one the village's workshops make. */
 private static void cottage(Map<BlockPos,BlockState> m,BlockPos b,int w,int d,int storeys){
  int mid=w/2,midZ=d/2,roof=4*storeys+1;boolean ridgeAlongX=w>d;
  for(int x=0;x<w;x++)for(int z=0;z<d;z++)for(int y=roof;y<=roof+Math.max(w,d);y++)p(m,b,x,y,z,Blocks.AIR);
  var post=Blocks.SPRUCE_LOG.defaultBlockState();
  for(int storey=0;storey<storeys;storey++){int floor=1+4*storey;
   for(int x=0;x<w;x++)for(int z=0;z<d;z++){
    boolean alongX=z==0||z==d-1,alongZ=x==0||x==w-1;
    if(!alongX&&!alongZ){if(storey>0){for(int y=floor;y<floor+3;y++)p(m,b,x,y,z,Blocks.AIR);p(m,b,x,floor+3,z,Blocks.SPRUCE_PLANKS);}continue;}
    boolean corner=alongX&&alongZ;int along=alongX?x:z,length=alongX?w:d;boolean front=z==0;
    char cell=corner?'P':wall(along,length,front,storey);
    for(int row=0;row<4;row++){var pos=b.offset(x,floor+row,z);var now=m.get(pos);
     if(now!=null&&now.getBlock() instanceof DoorBlock)continue;
     BlockState state;
     if(cell=='P')state=post;
     else if(row==3||cell=='D')state=Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,alongX?Direction.Axis.X:Direction.Axis.Z);
     else if(row==0&&storey==0)state=Blocks.STONE_BRICKS.defaultBlockState();
     else if(cell=='W'&&row>0)state=pane(alongX);
     else state=Blocks.BIRCH_PLANKS.defaultBlockState();
     m.put(pos,state);}
   }
  }
  // Gable roof: rising across the narrow side, trimmed in spruce at both gable ends, with a ridge on top.
  int span=ridgeAlongX?d:w,centre=ridgeAlongX?midZ:mid,ends=ridgeAlongX?w:d;
  for(int across=0;across<span;across++){int h=roof+Math.min(across,span-1-across);
   for(int end=0;end<ends;end++){
    int x=ridgeAlongX?end:across,z=ridgeAlongX?across:end;boolean trim=end==0||end==ends-1;
    if(across==centre)p(m,b,x,h,z,trim?Blocks.SPRUCE_SLAB:Blocks.DARK_OAK_SLAB);
    else{var facing=ridgeAlongX?(across<centre?Direction.SOUTH:Direction.NORTH):(across<centre?Direction.EAST:Direction.WEST);
     m.put(b.offset(x,h,z),(trim?Blocks.SPRUCE_STAIRS:Blocks.DARK_OAK_STAIRS).defaultBlockState().setValue(StairBlock.FACING,facing));}
    if(trim)for(int y=roof;y<h;y++){boolean king=across==centre;int step=y-roof;
     p(m,b,x,y,z,Blocks.BIRCH_PLANKS);
     if(king&&step<2)m.put(b.offset(x,y,z),pane(!ridgeAlongX));
     else if(king&&step==2)p(m,b,x,y,z,Blocks.SPRUCE_LOG);}
   }}
  // Chimney over the back corner of the house.
  int cx=w-2,cz=d-2,top=roof+(ridgeAlongX?Math.min(cz,d-1-cz):Math.min(cx,w-1-cx))+2;
  for(int y=roof;y<=top;y++)p(m,b,cx,y,cz,Blocks.BRICKS);
  p(m,b,cx,top+1,cz,Blocks.COBBLESTONE_WALL);
 }
 /** One wall cell along a wall of the given length: P post, W window, S solid wall, D lintel over the door; the front has posts beside the door. */
 private static char wall(int at,int length,boolean front,int storey){
  int mid=length/2;
  if(front){
   if(at==mid-1||at==mid+1)return 'P';
   if(at==mid)return storey==0?'D':'W';
   return at==(mid-1)/2||at==length-1-(mid-1)/2?'W':'S';
  }
  if(at==mid)return 'P';
  return at==(mid+1)/2||at==length-1-(mid+1)/2?'W':'S';
 }
 /** AD-144: the cottage's window is the spruce framed window (AD-142) of its spruce frame, set along the wall it stands in. */
 private static BlockState pane(boolean alongX){
  return VillageAstra.FRAMED_WINDOWS.get("spruce").get().defaultBlockState().setValue(FramedWindowBlock.AXIS,alongX?Direction.Axis.X:Direction.Axis.Z);
 }
}
