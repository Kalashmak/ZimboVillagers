package org.villageastra.world;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;
import org.villageastra.domain.Profession;
import org.villageastra.VillageAstra;

/** Frozen pre-AD117 migration source; do not evolve with new designs.
 * Actual block geometry shared by natural starter buildings and the complete design catalogue. */
public final class Legacy117BuildingBlueprints {
    public record Design(String id,int width,int depth,Block wall,Block timber,Block roof) {}
    private static final Map<String,Design> DESIGNS=new LinkedHashMap<>();
    static {
        add("home",7,7,Blocks.WHITE_TERRACOTTA,Blocks.STRIPPED_OAK_LOG,Blocks.SPRUCE_STAIRS);
        add("home_2",11,9,Blocks.WHITE_TERRACOTTA,Blocks.STRIPPED_DARK_OAK_LOG,Blocks.DARK_OAK_STAIRS);
        add("town_hall",7,7,Blocks.SMOOTH_SANDSTONE,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DARK_OAK_STAIRS);
        add("town_hall_2",7,7,Blocks.SMOOTH_SANDSTONE,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DARK_OAK_STAIRS);
        add("town_hall_3",7,7,Blocks.STONE_BRICKS,Blocks.STRIPPED_DARK_OAK_LOG,Blocks.DARK_OAK_STAIRS);
        add("farm",7,7,Blocks.OAK_PLANKS,Blocks.STRIPPED_SPRUCE_LOG,Blocks.SPRUCE_STAIRS);
        add("forester",9,19,Blocks.SPRUCE_PLANKS,Blocks.OAK_LOG,Blocks.DARK_OAK_STAIRS);
        add("quarry",7,7,Blocks.COBBLESTONE,Blocks.SPRUCE_LOG,Blocks.STONE_BRICK_STAIRS);
        add("mine",8,9,Blocks.STONE_BRICKS,Blocks.SPRUCE_LOG,Blocks.COBBLESTONE_STAIRS);
        add("guard_house",9,9,Blocks.STONE_BRICKS,Blocks.DARK_OAK_LOG,Blocks.STONE_BRICK_STAIRS);
        add("archery",11,9,Blocks.WHITE_TERRACOTTA,Blocks.OAK_LOG,Blocks.SPRUCE_STAIRS);
        add("school",11,11,Blocks.CALCITE,Blocks.STRIPPED_OAK_LOG,Blocks.BRICK_STAIRS);
        add("cartographer",9,11,Blocks.WHITE_TERRACOTTA,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DARK_OAK_STAIRS);
        add("smithy",11,9,Blocks.BRICKS,Blocks.DARK_OAK_LOG,Blocks.DEEPSLATE_BRICK_STAIRS);
        add("laboratory",11,11,Blocks.SMOOTH_SANDSTONE,Blocks.STRIPPED_BIRCH_LOG,Blocks.STONE_BRICK_STAIRS);
        add("expedition",9,11,Blocks.MUD_BRICKS,Blocks.OAK_LOG,Blocks.SPRUCE_STAIRS);
        add("warehouse",13,11,Blocks.BRICKS,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DARK_OAK_STAIRS);
        add("barracks",13,11,Blocks.STONE_BRICKS,Blocks.DARK_OAK_LOG,Blocks.DEEPSLATE_TILE_STAIRS);
        add("livestock",11,13,Blocks.RED_TERRACOTTA,Blocks.STRIPPED_OAK_LOG,Blocks.SPRUCE_STAIRS);
        add("mill",9,9,Blocks.CALCITE,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DARK_OAK_STAIRS);
        add("bakery",9,9,Blocks.BRICKS,Blocks.STRIPPED_OAK_LOG,Blocks.BRICK_STAIRS);
        add("masonry",11,9,Blocks.STONE_BRICKS,Blocks.STRIPPED_SPRUCE_LOG,Blocks.STONE_BRICK_STAIRS);
        add("carpentry",11,9,Blocks.BIRCH_PLANKS,Blocks.STRIPPED_DARK_OAK_LOG,Blocks.SPRUCE_STAIRS);
        add("caravan",13,11,Blocks.SMOOTH_SANDSTONE,Blocks.OAK_LOG,Blocks.RED_SANDSTONE_STAIRS);
        add("clinic",11,9,Blocks.CALCITE,Blocks.STRIPPED_BIRCH_LOG,Blocks.POLISHED_GRANITE_STAIRS);
        add("engineering",11,11,Blocks.LIGHT_GRAY_TERRACOTTA,Blocks.STRIPPED_SPRUCE_LOG,Blocks.DEEPSLATE_BRICK_STAIRS);
        add("siege_camp",11,11,Blocks.SPRUCE_PLANKS,Blocks.SPRUCE_LOG,Blocks.SPRUCE_STAIRS);
        // AD-094: an archer tower standing in the castle wall.
        add("wall_tower",5,5,Blocks.STONE_BRICKS,Blocks.STONE_BRICKS,Blocks.STONE_BRICK_STAIRS);
    }
    /** AD-074: how far below its lot a mine's drive starts — the depth of the built shaft. */
    public static final int SHAFT_DESCENT=6;
    private Legacy117BuildingBlueprints() {}
    private static void add(String id,int w,int d,Block wall,Block timber,Block roof) { DESIGNS.put(id,new Design(id,w,d,wall,timber,roof)); }
    public static Collection<Design> designs() { return Collections.unmodifiableCollection(DESIGNS.values()); }
    /** AD-073: "type@N" names level N of a design; its footprint is the design's own. */
    public static Design design(String id) { int at=id.indexOf('@');return Objects.requireNonNull(DESIGNS.get(at<0?id:id.substring(0,at)),"Unknown blueprint "+id); }
    public static String base(String id) { int at=id.indexOf('@');return at<0?id:id.substring(0,at); }
    public static int level(String id) { int at=id.indexOf('@');return at<0?1:Integer.parseInt(id.substring(at+1)); }
    public static Map<BlockPos,BlockState> layout(String id,BlockPos base) {
        if(id.indexOf('@')>=0){var blocks=layout(base(id),base);Legacy117Architecture.apply(base(id),level(id),base,blocks);return blocks;}
        Design d=design(id);int w=d.width(),l=d.depth(),mid=w/2;
        Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        for(int x=0;x<w;x++)for(int z=0;z<l;z++) {
            put(blocks,base,x,0,z,Blocks.COBBLESTONE);
            for(int y=1;y<=3;y++) {
                boolean edge=x==0||x==w-1||z==0||z==l-1;
                boolean post=(x==0||x==w-1)&&(z==0||z==l-1);
                put(blocks,base,x,y,z,post?d.timber():edge?d.wall():Blocks.AIR);
                if(edge&&!post&&y==2&&(x%3==1||z%3==1))put(blocks,base,x,y,z,Blocks.GLASS);
                if(edge&&y==3)put(blocks,base,x,y,z,d.timber());
            }
            put(blocks,base,x,4,z,Blocks.OAK_PLANKS);
            int height=5+Math.min(x,w-1-x);
            for(int y=5;y<height;y++)if(z==0||z==l-1)put(blocks,base,x,y,z,d.wall());
            if(x==mid)put(blocks,base,x,height,z,Blocks.SPRUCE_SLAB);
            else blocks.put(base.offset(x,height,z),d.roof().defaultBlockState().setValue(StairBlock.FACING,x<mid?Direction.EAST:Direction.WEST));
        }
        var door=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
        blocks.put(base.offset(mid,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER));
        blocks.put(base.offset(mid,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        put(blocks,base,mid,3,2,Blocks.LANTERN);
        put(blocks,base,1,1,4,VillageAstra.OWNED_CHEST.get());
        put(blocks,base,w-2,1,l-2,Blocks.CRAFTING_TABLE);
        if(id.equals("home")||id.equals("home_2")) {
            for(int x:new int[]{2,4})bed(blocks,base,x,3);
            if(id.equals("home_2"))for(int x:new int[]{6,8})bed(blocks,base,x,3);
            put(blocks,base,w-2,2,l-2,Blocks.POTTED_POPPY);
        }
        switch(id) {
            case "town_hall" -> { put(blocks,base,4,1,4,Blocks.CRAFTING_TABLE);put(blocks,base,2,1,5,Blocks.LECTERN);tower(blocks,base,mid,l/2,9); }
            case "farm" -> { for(int z=2;z<5;z++)put(blocks,base,5,1,z,Blocks.HAY_BLOCK);put(blocks,base,1,1,2,Blocks.COMPOSTER); }
            case "forester" -> { for(int z=2;z<5;z++)put(blocks,base,5,1,z,Blocks.OAK_LOG);put(blocks,base,1,1,2,Blocks.POTTED_SPRUCE_SAPLING); }
            case "mine","smithy","bakery" -> { chimney(blocks,base,w-2,l-2,10);put(blocks,base,4,1,4,id.equals("smithy")?Blocks.FURNACE:id.equals("bakery")?Blocks.SMOKER:Blocks.FURNACE);// AD-112: the smithy's anvil gave way to a floor grindstone (no iron block in any design).
                if(id.equals("smithy"))blocks.put(base.offset(2,1,2),Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE,AttachFace.FLOOR));else put(blocks,base,2,1,2,Blocks.BARREL); }
            case "school" -> { for(int x=2;x<w-2;x+=3)for(int z=3;z<l-2;z+=3)put(blocks,base,x,1,z,Blocks.LECTERN);shelf(blocks,base,w,l); }
            case "cartographer" -> { put(blocks,base,3,1,3,Blocks.CARTOGRAPHY_TABLE);shelf(blocks,base,w,l);tower(blocks,base,mid,l/2,11); }
            case "laboratory" -> { put(blocks,base,3,1,3,Blocks.LECTERN);put(blocks,base,6,1,3,Blocks.LANTERN);tower(blocks,base,mid,l/2,12); }
            case "warehouse" -> { for(int x=2;x<w-2;x+=3)for(int z=3;z<l-1;z+=3) {put(blocks,base,x,1,z,Blocks.BARREL);put(blocks,base,x,2,z,Blocks.BARREL);} }
            case "guard_house","barracks","siege_camp" -> { tower(blocks,base,mid,l/2,12);for(int x=2;x<w-2;x+=3)put(blocks,base,x,1,l-2,Blocks.SMITHING_TABLE); }
            case "archery" -> { for(int x=2;x<w-2;x+=3)put(blocks,base,x,2,l-1,Blocks.HAY_BLOCK);put(blocks,base,3,1,3,Blocks.FLETCHING_TABLE); }
            case "livestock" -> { for(int x=2;x<w-2;x++)put(blocks,base,x,1,l-3,x==mid?Blocks.OAK_FENCE_GATE:Blocks.OAK_FENCE);for(int x=2;x<4;x++)put(blocks,base,x,1,2,Blocks.HAY_BLOCK); }
            case "mill" -> { int y=10;for(int yy=5;yy<=y;yy++)put(blocks,base,mid,yy,1,Blocks.SPRUCE_LOG);for(int a=-3;a<=3;a++) {put(blocks,base,mid+a,y,0,Blocks.WHITE_WOOL);put(blocks,base,mid,y+a,0,Blocks.WHITE_WOOL);}put(blocks,base,mid,y,0,Blocks.OAK_LOG);put(blocks,base,3,1,3,Blocks.GRINDSTONE); }
            case "quarry" -> { put(blocks,base,3,1,3,Blocks.STONECUTTER);put(blocks,base,5,1,2,Blocks.BARREL);put(blocks,base,5,1,4,Blocks.GRINDSTONE); }
            case "masonry" -> {put(blocks,base,3,1,3,Blocks.STONECUTTER);for(int z=3;z<l-2;z++)put(blocks,base,w-2,1,z,Blocks.CHISELED_STONE_BRICKS);}
            case "carpentry" -> {put(blocks,base,3,1,3,Blocks.CRAFTING_TABLE);for(int z=3;z<l-2;z++)put(blocks,base,w-2,1,z,Blocks.STRIPPED_OAK_LOG);}
            case "clinic" -> { for(int x=2;x<w-2;x+=3)bed(blocks,base,x,5);put(blocks,base,mid,5,0,Blocks.RED_TERRACOTTA);put(blocks,base,mid,6,0,Blocks.RED_TERRACOTTA);put(blocks,base,mid-1,5,0,Blocks.RED_TERRACOTTA);put(blocks,base,mid+1,5,0,Blocks.RED_TERRACOTTA); }
            case "engineering" -> {put(blocks,base,3,1,3,Blocks.SMITHING_TABLE);put(blocks,base,6,1,3,Blocks.STONECUTTER);shelf(blocks,base,w,l);}
            case "caravan","expedition" -> {for(int x=2;x<w-2;x++)put(blocks,base,x,3,2,x%2==0?Blocks.ORANGE_WOOL:Blocks.WHITE_WOOL);for(int z=4;z<l-2;z++)put(blocks,base,w-2,1,z,Blocks.BARREL);}
            default -> {}
        }
        // Gable windows and framing break up broad blank facades.
        if(w>=9 && !id.equals("clinic")) {
            put(blocks,base,mid,5,0,Blocks.GLASS);put(blocks,base,mid,6,0,Blocks.GLASS);
            for(int x=mid-2;x<=mid+2;x++)if(x!=mid)put(blocks,base,x,5,0,d.timber());
        }
        if(Set.of("smithy","masonry","carpentry").contains(id)) {
            for(int z=2;z<l-2;z++)for(int y=1;y<=2;y++)put(blocks,base,w-1,y,z,Blocks.AIR);
            for(int z=2;z<l-1;z+=3)for(int y=1;y<=3;y++)put(blocks,base,w-1,y,z,d.timber());
        }
        if(id.equals("caravan")) {
            for(int x=0;x<w;x++)for(int z=0;z<l;z++) {
                for(int y=4;y<=14;y++)put(blocks,base,x,y,z,Blocks.AIR);
                int edge=Math.min(Math.min(x,w-1-x),Math.min(z,l-1-z));
                if(edge<=2) {
                    Direction facing=z<3?Direction.SOUTH:z>=l-3?Direction.NORTH:x<3?Direction.EAST:Direction.WEST;
                    blocks.put(base.offset(x,4+edge,z),d.roof().defaultBlockState().setValue(StairBlock.FACING,facing));
                } else {for(int y=1;y<=3;y++)put(blocks,base,x,y,z,Blocks.AIR);put(blocks,base,x,0,z,Blocks.STONE_BRICKS);}
            }
        }
        if(id.equals("archery")||id.equals("livestock")) {
            int yard=id.equals("archery")?4:7;
            for(int x=0;x<w;x++)for(int z=yard;z<l;z++) {
                for(int y=1;y<=14;y++)put(blocks,base,x,y,z,Blocks.AIR);
                put(blocks,base,x,0,z,Blocks.COARSE_DIRT);
                if(x==0||x==w-1||z==l-1)put(blocks,base,x,1,z,Blocks.OAK_FENCE);
            }
            if(id.equals("archery"))put(blocks,base,1,1,2,VillageAstra.OWNED_CHEST.get());
            if(id.equals("archery"))for(int x=2;x<w-2;x+=3) {put(blocks,base,x,1,l-2,Blocks.OAK_FENCE);put(blocks,base,x,2,l-2,Blocks.HAY_BLOCK);}
            else {for(int x=0;x<w;x++)put(blocks,base,x,1,yard,x==mid?Blocks.OAK_FENCE_GATE:Blocks.OAK_FENCE);put(blocks,base,2,1,l-2,Blocks.HAY_BLOCK);}
        }
        if(id.equals("guard_house")||id.equals("barracks")) {
            for(int x=0;x<w;x++)for(int z=0;z<l;z++) {
                for(int y=5;y<=14;y++)put(blocks,base,x,y,z,Blocks.AIR);
                put(blocks,base,x,4,z,Blocks.STONE_BRICKS);
                if((x==0||x==w-1||z==0||z==l-1)&&(x+z)%2==0)put(blocks,base,x,5,z,Blocks.STONE_BRICK_WALL);
            }
            if(id.equals("guard_house")) {
                tower(blocks,base,mid,l/2,9);
                for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})for(int y=5;y<7;y++)put(blocks,base,mid+dx,y,l/2+dz,Blocks.OAK_LOG);
            }
        }
        if(id.equals("siege_camp")) {
            blocks.clear();
            for(int x=0;x<w;x++)for(int z=0;z<l;z++) {
                put(blocks,base,x,0,z,Blocks.COARSE_DIRT);
                for(int y=1;y<=10;y++)put(blocks,base,x,y,z,Blocks.AIR);
                if(x==0||x==w-1||z==0||z==l-1)for(int y=1;y<=2;y++)put(blocks,base,x,y,z,Blocks.OAK_FENCE);
            }
            put(blocks,base,mid,1,0,Blocks.OAK_FENCE_GATE);put(blocks,base,mid,2,0,Blocks.AIR);
            for(int x=2;x<w-2;x++)for(int z=3;z<l-2;z++)put(blocks,base,x,3+Math.min(x-2,w-3-x),z,Blocks.BROWN_WOOL);
            for(int x:new int[]{2,w-3})for(int z:new int[]{3,l-3})for(int y=1;y<3;y++)put(blocks,base,x,y,z,Blocks.SPRUCE_FENCE);
            put(blocks,base,mid,1,4,Blocks.CAMPFIRE);put(blocks,base,1,1,4,VillageAstra.OWNED_CHEST.get());
        }
        Legacy117DistinctArchitecture.apply(id,base,blocks);
        return blocks;
    }
    /** Operator-only design preview; never used for ordinary funded construction. */
    public static void preview(net.minecraft.server.level.ServerLevel level,String id,BlockPos origin) {
        var cells=layout(id,origin);String validation=StarterVillage.validate(level,cells,origin.getY());
        if(!validation.equals("ok"))throw new IllegalStateException(validation);
        cells.forEach((pos,state)->level.setBlock(pos,state,2));
    }
    private static void put(Map<BlockPos,BlockState> map,BlockPos b,int x,int y,int z,Block block) {map.put(b.offset(x,y,z),block==Blocks.LANTERN ? block.defaultBlockState().setValue(LanternBlock.HANGING,true) : block.defaultBlockState());}
    private static void bed(Map<BlockPos,BlockState> map,BlockPos b,int x,int z) {
        var bed=Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        map.put(b.offset(x,1,z),bed.setValue(BedBlock.PART,BedPart.FOOT));map.put(b.offset(x,1,z+1),bed.setValue(BedBlock.PART,BedPart.HEAD));
    }
    private static void shelf(Map<BlockPos,BlockState> m,BlockPos b,int w,int l) {for(int x=1;x<w-1;x++)for(int y=1;y<=2;y++)put(m,b,x,y,l-2,Blocks.BOOKSHELF);}
    private static void chimney(Map<BlockPos,BlockState> m,BlockPos b,int x,int z,int top) {for(int y=1;y<=top;y++)put(m,b,x,y,z,Blocks.BRICKS);put(m,b,x,top+1,z,Blocks.COBBLESTONE_WALL);}
    private static void tower(Map<BlockPos,BlockState> m,BlockPos b,int x,int z,int top) {
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=7;y<=top;y++)
            put(m,b,x+dx,y,z+dz,y==top?Blocks.STONE_BRICK_SLAB:Math.abs(dx)==1&&Math.abs(dz)==1?Blocks.OAK_LOG:Blocks.AIR);
        put(m,b,x,top-1,z,Blocks.LANTERN);
    }
}
