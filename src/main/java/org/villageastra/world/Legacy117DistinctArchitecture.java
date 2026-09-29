package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import org.villageastra.VillageAstra;
import java.util.*;

/** Frozen pre-AD117 migration source; do not evolve with new designs.
 * Purpose-specific silhouettes and usable working yards. Coordinates are relative to each lot. */
public final class Legacy117DistinctArchitecture {
    private Legacy117DistinctArchitecture() {}
    private static void p(Map<BlockPos,BlockState> m,BlockPos b,int x,int y,int z,Block block){m.put(b.offset(x,y,z),block.defaultBlockState());}
    private static void clear(Map<BlockPos,BlockState> m,BlockPos b,int w,int d,int h){m.clear();for(int x=0;x<w;x++)for(int z=0;z<d;z++)for(int y=0;y<=h;y++)p(m,b,x,y,z,y==0?Blocks.COBBLESTONE:Blocks.AIR);}
    public static void apply(String id,BlockPos b,Map<BlockPos,BlockState> m){
        var d=Legacy117BuildingBlueprints.design(id);int w=d.width(),depth=d.depth();
        if(id.equals("wall_tower")) {
            // AD-094: a five-by-five stone tower in the castle wall. A spiral stair of seven treads round a central pillar climbs to a
            // platform at the height of the wall walk, with crenellations round it; the archers stand up there. Stairs, not a ladder:
            // a resident walks up treads it can plan, which it cannot do with a ladder.
            clear(m,b,5,5,10);
            for(int x=0;x<5;x++)for(int z=0;z<5;z++){
                boolean edge=x==0||x==4||z==0||z==4;
                if(edge)for(int y=1;y<=7;y++)p(m,b,x,y,z,Blocks.STONE_BRICKS);
                else p(m,b,x,7,z,Blocks.STONE_BRICKS);
                if(edge&&((x+z)&1)==0)p(m,b,x,8,z,Blocks.STONE_BRICK_WALL);
            }
            for(int y=1;y<=6;y++)p(m,b,2,y,2,Blocks.STONE_BRICKS);
            // The treads, clockwise from the door: each one a block higher, facing the way up.
            int[][] treads={{3,1,1},{3,2,2},{3,3,3},{2,3,4},{1,3,5},{1,2,6},{1,1,7}};
            Direction[] up={Direction.EAST,Direction.SOUTH,Direction.SOUTH,Direction.WEST,Direction.WEST,Direction.NORTH,Direction.NORTH};
            for(int i=0;i<treads.length;i++){var t=treads[i];
                m.put(b.offset(t[0],t[2],t[1]),Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,up[i]));
                // Headroom over the treads: three blocks, since a resident stepping up to the next tread needs its whole height over
                // the one it stands on (wall-client-04: a platform one block lower held the archer under it). The platform is open
                // above the last ones.
                for(int y=t[2]+1;y<=Math.min(7,t[2]+3);y++)if(!(t[0]==2&&t[1]==2))m.put(b.offset(t[0],y,t[1]),Blocks.AIR.defaultBlockState());
            }
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
            m.put(b.offset(2,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
            m.put(b.offset(2,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            p(m,b,2,1,1,Blocks.AIR);p(m,b,2,2,1,Blocks.AIR);
            p(m,b,1,1,4,VillageAstra.OWNED_CHEST.get());
            p(m,b,2,9,2,Blocks.LANTERN);
            return;
        }
        if(id.equals("forester")) {
            // AD-074: a sawmill with its own nursery — an open timber lodge with the saw and the log piles in front,
            // and behind it the fenced plot where the forester plants and fells. The mayor sets how large the plot is.
            clear(m,b,9,19,14);
            for(int x=0;x<9;x++)for(int z=0;z<7;z++) {
                p(m,b,x,0,z,Blocks.SPRUCE_PLANKS);
                boolean edge=x==0||x==8||z==0||z==6;
                // The back wall is open opposite the nursery gate: the gate stood against a solid wall and nobody could reach the plot.
                if(edge&&!(z==0&&x>=3&&x<=5)&&!(z==6&&x==4))for(int y=1;y<=2;y++)p(m,b,x,y,z,x==0||x==8?Blocks.SPRUCE_PLANKS:Blocks.SPRUCE_LOG);
            }
            for(int x:new int[]{0,8})for(int z:new int[]{0,6})for(int y=1;y<=3;y++)p(m,b,x,y,z,Blocks.OAK_LOG);
            for(int x=0;x<9;x++)for(int z=0;z<7;z++) {
                int y=4+Math.min(Math.min(x,8-x),1);
                m.put(b.offset(x,y,z),Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,x<4?Direction.EAST:Direction.WEST));
                if(x==4)p(m,b,x,y,z,Blocks.DARK_OAK_SLAB);
            }
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
            m.put(b.offset(4,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
            m.put(b.offset(4,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            p(m,b,1,1,4,VillageAstra.OWNED_CHEST.get());p(m,b,6,1,2,Blocks.STONECUTTER);p(m,b,6,1,4,Blocks.CRAFTING_TABLE);
            for(int z=2;z<=4;z++){p(m,b,7,1,z,Blocks.OAK_LOG);p(m,b,7,2,z,Blocks.STRIPPED_OAK_LOG);}
            p(m,b,2,3,1,Blocks.LANTERN);
            // The plot: grass, the fence of the size the village starts with, and its saplings.
            for(int x=0;x<9;x++)for(int z=Nursery.FRONT;z<19;z++){p(m,b,x,0,z,Blocks.GRASS_BLOCK);for(int y=1;y<=12;y++)p(m,b,x,y,z,Blocks.AIR);}
            Nursery.fence(Nursery.DEFAULT).forEach((local,state)->m.put(b.offset(local),state));
            for(var sapling:Nursery.saplings(Nursery.DEFAULT))p(m,b,sapling.getX(),sapling.getY(),sapling.getZ(),Blocks.OAK_SAPLING);
        } else if(id.equals("mine")) {
            // AD-074: the mine really goes down — a stepped shaft from its doorway to six blocks below the lot, lined in stone brick under a
            // headframe with its hoist, with covered stores on both sides; the miner's drive carries on from the bottom of the shaft.
            // Owner 2026-09-19: symmetric about its double door — eight wide, the shaft and the door in the middle two columns, a headframe post
            // against each side wall (the east side used to run two blocks past its post).
            clear(m,b,8,9,14);
            for(int z=0;z<9;z++)for(int x=0;x<8;x++) {
                boolean shaft=x>=3&&x<=4&&z>=1&&z<=6;
                if(!shaft)p(m,b,x,4,z,Blocks.STONE_BRICK_SLAB);
                boolean edge=x==0||x==7||z==0||z==8;
                if(edge&&!(z==8&&x>=3&&x<=4))for(int y=1;y<=3;y++)p(m,b,x,y,z,x==0||x==7?Blocks.COBBLESTONE:Blocks.STONE_BRICKS);
            }
            // AD-079: the trench is a real staircase — a stone-brick stair for every block inward, so the miner walks down and back up
            // without a single one-block jump; a bare step left it standing on the ledge with no route out.
            var tread=Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH);
            for(int z=1;z<=6;z++) {
                for(int x=3;x<=4;x++) {
                    m.put(b.offset(x,-z,z),tread);
                    for(int y=-z+1;y<=0;y++)p(m,b,x,y,z,Blocks.AIR);
                }
                for(int x:new int[]{2,5})for(int y=-z;y<=-1;y++)p(m,b,x,y,z,Blocks.STONE_BRICKS);
                for(int x=1;x<=5;x++)p(m,b,x,-z-1,z,Blocks.STONE_BRICKS);
            }
            // AD-079: the trench keeps the western aisle of the lot free, or the mine walls in its own chest: through the door the
            // miner walks the aisle to the chest, and only the trench itself goes down.
            // Beyond the last step the drive goes on: its mouth is left open in the far wall.
            for(int z=7;z<=8;z++)for(int x=2;x<=4;x++){p(m,b,x,-6,z,Blocks.STONE_BRICKS);for(int y=-5;y<=0;y++)p(m,b,x,y,z,Blocks.AIR);}
            // A double door over the shaft, its leaves hinged on the outer sides so they open as a pair.
            var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
            for(int x=3;x<=4;x++){var leaf=door.setValue(DoorBlock.HINGE,x==3?net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT:net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT);
             m.put(b.offset(x,1,0),leaf.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));m.put(b.offset(x,2,0),leaf.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));}
            // Headframe over the shaft with its hoist chain and lamp.
            for(int z:new int[]{1,6})for(int x:new int[]{1,6})for(int y=1;y<=5;y++)p(m,b,x,y,z,Blocks.SPRUCE_LOG);
            for(int z:new int[]{1,6})for(int x=1;x<=6;x++)m.put(b.offset(x,5,z),Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
            // A hoist over each half of the shaft: two beams, two chains, two lamps.
            for(int x=3;x<=4;x++){for(int z=2;z<=5;z++)m.put(b.offset(x,5,z),Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z));
             for(int y=3;y<=4;y++)p(m,b,x,y,3,Blocks.CHAIN);m.put(b.offset(x,2,3),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));}
            p(m,b,1,1,4,VillageAstra.OWNED_CHEST.get());
            // A furnace in the west wall leaves the full aisle to the shaft and stock open.
            m.put(b.offset(0,1,2),Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING,Direction.EAST));
            p(m,b,1,3,2,Blocks.LANTERN);p(m,b,6,3,2,Blocks.LANTERN);
        } else if(id.startsWith("town_hall")) {
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
        } else if(id.equals("warehouse")||id.equals("engineering")) {
            // Low industrial hall; stepped clerestory instead of another triangular house roof.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
                p(m,b,x,4,z,id.equals("warehouse")?Blocks.SPRUCE_PLANKS:Blocks.STONE_BRICKS);
                if(x>=3&&x<w-3){p(m,b,x,5,z,Blocks.GLASS);p(m,b,x,6,z,Blocks.DEEPSLATE_TILE_SLAB);}
            }
        } else if(id.equals("laboratory")) {
            // Octagonal observatory with an andesite dome, keeping the equipment below.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++)for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++)p(m,b,x,4,z,Blocks.SMOOTH_SANDSTONE);
            int c=w/2;
            for(int y=4;y<=10;y++)for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                int r=Math.max(Math.abs(x-c),Math.abs(z-c));int radius=y<8?4:10-y+1;
                if(r==radius)p(m,b,x,y,z,y<8?Blocks.GLASS:Blocks.POLISHED_ANDESITE);
                if(y==10&&r<radius)p(m,b,x,y,z,Blocks.STONE_BRICK_SLAB);
            }
        } else if(id.equals("school")) {
            // Flat classroom wings around a light-filled inner patio.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
                if(x<3||x>w-4||z<3||z>depth-4)p(m,b,x,4,z,Blocks.BRICK_SLAB);
                else {for(int y=1;y<=3;y++)p(m,b,x,y,z,Blocks.AIR);p(m,b,x,0,z,Blocks.MOSS_BLOCK);}
            }
        } else if(id.equals("carpentry")||id.equals("expedition")) {
            // Asymmetric lean-to: tall storage wall and a low open working eave.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
                int top=4+x/3;p(m,b,x,top,z,Blocks.SPRUCE_PLANKS);
                if(z==0||z==depth-1||x==w-1)for(int y=4;y<top;y++)p(m,b,x,y,z,y==top-1&&x>1&&x<w-2?Blocks.GLASS:Blocks.SPRUCE_PLANKS);
            }
        } else if(id.equals("masonry")) {
            // Stone yard with a small covered tool bay and stacks under the open sky.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++) {
                for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
                if(z<3)p(m,b,x,4,z,Blocks.STONE_BRICK_SLAB);
                else if(x==0||x==w-1||z==depth-1){p(m,b,x,2,z,Blocks.AIR);p(m,b,x,3,z,Blocks.AIR);}
            }
        } else if(id.equals("bakery")) {
            // Warm shopfront awning, an oven chimney, and a low hipped roof.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++)for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++){int edge=Math.min(Math.min(x,w-1-x),Math.min(z,depth-1-z));p(m,b,x,4+edge,z,Blocks.BRICKS);}
            for(int x=1;x<w-1;x++)p(m,b,x,3,0,x%2==0?Blocks.RED_WOOL:Blocks.WHITE_WOOL);
            for(int y=1;y<=10;y++)p(m,b,w-2,y,depth-2,Blocks.BRICKS);
        } else if(id.equals("barracks")) {
            // U-shaped quarters surround a parade court; the front gate remains covered.
            for(int x=3;x<w-3;x++)for(int z=3;z<depth-2;z++){
                for(int y=1;y<15;y++)p(m,b,x,y,z,Blocks.AIR);p(m,b,x,0,z,Blocks.SMOOTH_STONE);
            }
        } else if(id.equals("home")) {
            cottage(m,b,w,depth,1);
        } else if(id.equals("home_2")) {
            // A real second storey instead of a stretched single-storey cottage; the ladder inside leads up to it.
            cottage(m,b,w,depth,2);
            for(int y=1;y<=7;y++)m.put(b.offset(1,y,depth-2),Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
            m.put(b.offset(w/2,7,depth/2),Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        } else if(id.equals("farm")) {
            // Low thatched silhouette, distinct from the stone workshops.
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++)for(int y=4;y<15;y++)p(m,b,x,y,z,Blocks.AIR);
            for(int x=0;x<w;x++)for(int z=0;z<depth;z++)p(m,b,x,4+Math.min(Math.min(x,w-1-x),Math.min(z,depth-1-z)),z,Blocks.HAY_BLOCK);
        } else if(id.equals("mill")) {
            // Diagonal sails, rather than a cross resembling the clinic sign.
            for(int x=0;x<w;x++)for(int y=7;y<=13;y++)p(m,b,x,y,0,Blocks.AIR);
            for(int a=-3;a<=3;a++){p(m,b,w/2+a,10+a,0,Blocks.WHITE_WOOL);p(m,b,w/2+a,10-a,0,Blocks.WHITE_WOOL);}
            p(m,b,w/2,10,0,Blocks.OAK_LOG);
        }
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
 /** A glass pane already joined to the wall it stands in, so a village generated without block updates shows a window and not a post. */
 private static BlockState pane(boolean alongX){
  var pane=Blocks.GLASS_PANE.defaultBlockState();
  return alongX?pane.setValue(CrossCollisionBlock.EAST,true).setValue(CrossCollisionBlock.WEST,true):pane.setValue(CrossCollisionBlock.NORTH,true).setValue(CrossCollisionBlock.SOUTH,true);
 }
}
