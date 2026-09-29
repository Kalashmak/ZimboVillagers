package org.villageastra.world;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** AD-121 preview: the town hall that grows into a castle on a fixed 37x37 lot, levels I..VI.
 *  Pure data with no Minecraft types, so the same plan can be dumped to a voxel JSON and checked outside the game
 *  (`java CastlePlan.java <outDir>`); {@link CastleArchitecture} turns the block-state strings into real states.
 *  Local lot coordinates: x east, z south, y=0 is the ground course (the lot itself is not paved), gate side z=0.
 *  Masses (x0..x1, z0..z1): hall infill 9..19,18..28 with posts proud at 8/20 and 17/29, north jetty z16, porch
 *  12..16,13..15, chimney 20..21,25..26; keep 21..29,15..23 (II, full height III); forebuilding 20..26,12..14 (III);
 *  curtain ring with outer faces x4/x32/z4/z32 (W,N 3 thick, E,S 2 thick), round d7 corner towers centred on the
 *  corners from IV (project 3); gatehouse 12..24,1..8 (V); D-towers d5 at (3,19),(33,19) and cones everywhere (VI).
 *  Kitchen 8..11,7..11 and well 13..15,9..11 (IV); granary 26..29,7..11 (V); arsenal 22..28,27..30 (VI).
 *  Each level adds; the parts a level rebuilds (hall roof and gables at II, crenels under a higher crown or a cone,
 *  keep talus under the forebuilding) are reported by {@link #main}. The hall never shares a block or a column
 *  span with a wall, tower or the keep: {@link #plan} refuses such a plan. */
public final class CastlePlan {
    public static final int LOT=37, MAX_LEVEL=6;
    /** Stands for the AD-112 core block; the game resolves it by level, the voxel dump draws chiseled stone. */
    public static final String SEAL="#seal";
    public record Cell(int x,int y,int z) {}
    private final Map<Cell,String> m=new LinkedHashMap<>();
    private final Map<Cell,String> owner=new HashMap<>();
    private final List<String> clips=new ArrayList<>();
    private final int level;
    private String part="yard";
    /** Owner review 2026-09-22: every roof of levels I..III (hall, porch, cupola, keep caps) is dark oak shingle from the forester and carpentry —
     *  deepslate lies below the floor of a level-I..II mine (AD-112: Y 32 / 8); from level IV every roof of the castle is relaid in deepslate tile. */
    private final String RS,RF,RC,RW;
    private CastlePlan(int level){this.level=level;boolean slate=level>=4;
        RS=slate?"deepslate_tile_stairs":"dark_oak_stairs";RF=slate?"deepslate_tiles":"dark_oak_planks";
        RC=slate?"deepslate_tile_slab[type=bottom]":"dark_oak_slab[type=bottom]";RW=slate?"deepslate_tile_wall":"stone_brick_wall[up=true]";}

    /** The full shell of the given level (1..6): every earlier level's blocks plus this level's additions. */
    public static Map<Cell,String> plan(int level) { return Collections.unmodifiableMap(build(level).m); }
    /** AD-121: the kit cells a level brings (its stations and, from II, the seal) - what a castle hall must have standing to work at it. */
    public static Map<Cell,String> kitAt(int level){
        var now=build(level);var before=level>1?build(level-1).m:Map.<Cell,String>of();var out=new LinkedHashMap<Cell,String>();
        for(var e:now.m.entrySet())if("kits".equals(now.owner.get(e.getKey()))&&!e.getValue().equals(before.get(e.getKey())))out.put(e.getKey(),e.getValue());
        return Collections.unmodifiableMap(out);}
    static CastlePlan build(int level) {
        if(level<1||level>MAX_LEVEL)throw new IllegalArgumentException("Castle level "+level);
        var p=new CastlePlan(level);
        p.part="hall";p.hall();
        if(level>=2){p.part="keep";p.keepLower();}
        if(level>=3){p.part="keep";p.keepUpper();p.part="fore";p.forebuilding();}
        if(level>=4){p.part="ward";p.ward();p.part="yard";p.wardYard();}
        if(level>=5){p.part="ward";p.curtain();p.part="yard";p.curtainYard();}
        if(level>=6){p.part="ward";p.fullCastle();p.part="yard";p.arsenal();}
        p.part="kits";p.kits();
        p.checkHall();
        if(!p.clips.isEmpty())throw new IllegalStateException("Castle level "+level+": hall inside a wall footprint "+p.clips.subList(0,Math.min(8,p.clips.size())));
        return p;
    }

    // ---------- primitives ----------
    private static boolean fort(String part){return part.equals("ward")||part.equals("keep");}
    private void set(int x,int y,int z,String s){
        if(x<0||x>=LOT||z<0||z>=LOT||y<0)throw new IllegalStateException("Outside the castle lot: "+x+","+y+","+z+" "+s);
        var c=new Cell(x,y,z);String was=m.get(c),by=owner.get(c);
        if(by!=null&&(was!=null&&!was.equals("air")||!s.equals("air"))&&(by.equals("hall")&&fort(part)||fort(by)&&part.equals("hall")))clips.add(part+" over "+by+" at "+c);
        if(by!=null&&by.equals("kits")&&!part.equals("kits"))throw new IllegalStateException("Functional cell rebuilt at "+c);
        m.put(c,s);owner.put(c,part);}
    private String get(int x,int y,int z){return m.get(new Cell(x,y,z));}
    private boolean empty(int x,int y,int z){String s=get(x,y,z);return s==null||s.equals("air");}
    private void setIfEmpty(int x,int y,int z,String s){if(empty(x,y,z))set(x,y,z,s);}
    private void box(int x0,int y0,int z0,int x1,int y1,int z1,String s){for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)set(x,y,z,s);}
    private static int hash(int x,int y,int z){int v=x*73856093^y*19349663^z*83492791;v^=v>>>13;v*=0x5bd1e995;v^=v>>>15;return (v&0x7fffffff)%100;}
    /** Castle masonry: weathered 2x2 clusters (cracked bricks, cobblestone, andesite) thick at the foot, thinning upward. */
    static String mas(int x,int y,int z){
        int r=hash(x>>1,y>>1,z>>1);
        if(y<=1)return r<25?"andesite":"cobblestone";
        // Round 1 review: only blocks the village makes or mines — no cracked stone brick; the weathering is cobble and andesite.
        if(y<=3)return r<26?"cobblestone":r<34?"andesite":"stone_bricks";
        if(y<=6)return r<13?"andesite":"stone_bricks";
        return "stone_bricks";}
    static String stair(String b,String facing,boolean top){return b+"[facing="+facing+",half="+(top?"top":"bottom")+",shape=straight]";}
    static String log(String b,char axis){return b+"[axis="+axis+"]";}
    /** A window or bars joined along the wall (alongX: east-west). AD-144 (owner 2026-09-23): glass is the dark oak framed window (AD-142) of
     *  the hall's frame; iron bars stay only where the castle means bars (the portcullis). */
    static String pane(String b,boolean alongX){
        if(b.equals("glass_pane"))return VillageStyle.window(org.villageastra.domain.FramedWindows.DEFAULT_WOOD,alongX);
        return b+"[east="+alongX+",north="+!alongX+",south="+!alongX+",west="+alongX+"]";}
    static String wallPost(){return "stone_brick_wall[up=true]";}
    private void door(int x,int y,int z,String facing){
        set(x,y,z,"dark_oak_door[facing="+facing+",half=lower,hinge=left,open=false]");
        set(x,y+1,z,"dark_oak_door[facing="+facing+",half=upper,hinge=left,open=false]");}
    /** A 1:1 crenellation two blocks high from y: merlon where (x+z) is even, crenel otherwise. */
    private void merlon(int x,int y,int z){boolean on=(x+z)%2==0;set(x,y,z,on?mas(x,y,z):"air");set(x,y+1,z,on?mas(x,y+1,z):"air");}
    private static boolean inDisc(int x,int z,int cx,int cz,double r2){int dx=x-cx,dz=z-cz;return dx*dx+dz*dz<=r2;}
    private static String inward(int dx,int dz){return Math.abs(dx)>=Math.abs(dz)?(dx<0?"east":"west"):(dz<0?"south":"north");}

    // ---------- I..II: timber town hall (medieval-timber on a cobble plinth) ----------
    static final int HX0=9,HX1=19,HZ0=18,HZ1=28,HCX=14; // infill planes
    static final int PX0=8,PX1=20,PZ0=17,PZ1=29,JZ=16;  // post planes, one block proud; north jetty plane
    static final String PLASTER="smooth_sandstone",POST="dark_oak_log",BEAM="stripped_dark_oak_log";
    private static boolean chimney(int x,int z){return x>=20&&x<=21&&z>=25&&z<=26;}
    private static boolean keepCol(int x,int z){return x>=KX0&&x<=KX1&&z>=KZ0&&z<=KZ1;}
    private static Set<Integer> ints(int... a){Set<Integer> s=new HashSet<>();for(int i:a)s.add(i);return s;}

    /** One timber storey on one face: sill at ys and plate at ys+5 on the outer plane with posts between them, one block
     *  proud of the plaster; the end bays carry a diagonal brace down from the corner post, the middle bays windows. */
    private void storey(int ys,boolean alongX,int outer,int inner,int a0,int a1,Set<Integer> posts,int doorAt){
        char ax=alongX?'x':'z';String lo=alongX?"west":"north",hi=alongX?"east":"south";
        for(int a=a0;a<=a1;a++){
            int ox=alongX?a:outer,oz=alongX?outer:a,ix=alongX?a:inner,iz=alongX?inner:a;
            boolean post=posts.contains(a),door=a==doorAt,lp=posts.contains(a-1),hp=posts.contains(a+1),chim=chimney(ox,oz);
            if(!chim)for(int r=0;r<=5;r++){int y=ys+r;String s;
                if(r==0)s=door?"air":log(BEAM,ax);
                else if(r==5)s=log(BEAM,ax);
                else if(post)s=log(POST,'y');
                else if(a==a0+1&&r==4||a==a0+2&&r==3)s=stair("dark_oak_stairs",lo,true);
                else if(a==a1-1&&r==4||a==a1-2&&r==3)s=stair("dark_oak_stairs",hi,true);
                else s="air";
                set(ox,y,oz,s);}
            if(a<=a0||a>=a1)continue;
            boolean window=!post&&!door&&!chim&&a>a0+2&&a<a1-2;
            for(int r=0;r<=5;r++){int y=ys+r;
                if(door&&r==0)continue;
                set(ix,y,iz,door&&r==1?log(BEAM,ax):window&&(r==1||r==2)?pane("glass_pane",alongX):PLASTER);}}}

    /** Framed gable in plane z from y0 up to under the roof of eave height `base`. */
    private void gable(int z,int y0,int base){
        int collar=base+4;
        for(int x=PX0;x<=PX1;x++){int h=base+Math.min(x-7,21-x);
            for(int y=y0;y<(x==HCX?h-1:h);y++)
                set(x,y,z,x==HCX||x==11||x==17?log(POST,'y'):y==collar?log(BEAM,'x'):(x==12||x==16)&&y==collar-2?pane("glass_pane",true):PLASTER);}}

    private void hall(){
        boolean two=level>=2;int base=two?13:7;
        // Foundation two blocks thick (infill and post planes), plank floor, cleared ground storey.
        for(int x=PX0;x<=PX1;x++)for(int z=PZ0;z<=PZ1;z++){
            boolean wall=x<=HX0||x>=HX1||z<=HZ0||z>=HZ1;
            set(x,0,z,wall?"cobblestone":"dark_oak_planks");
            if(wall)set(x,1,z,"cobblestone");else box(x,1,z,x,6,z,"air");}
        set(HCX,1,PZ0,"air");set(PX1,1,21,"air");
        // Ground storey: close posts on the long faces, a door bay north (x14) and east (z21).
        storey(2,true,PZ0,HZ0,PX0,PX1,ints(8,11,13,15,17,20),HCX);
        storey(2,true,PZ1,HZ1,PX0,PX1,ints(8,11,14,17,20),-1);
        storey(2,false,PX0,HX0,PZ0,PZ1,ints(17,20,23,26,29),-1);
        storey(2,false,PX1,HX1,PZ0,PZ1,ints(17,20,23,26,29),21);
        door(HCX,1,HZ0,"north");door(HX1,1,21,"east");
        // Tie beams of the great hall with hung lanterns.
        for(int z:new int[]{22,25})for(int x=HX0+1;x<HX1;x++)set(x,7,z,log(POST,'x'));
        set(HCX,6,22,"lantern[hanging=true]");set(HCX,6,25,"lantern[hanging=true]");
        // North jetty: brackets under the posts of the projecting gable (I) or upper storey (II).
        for(int x:new int[]{8,11,14,17,20})set(x,7,JZ,stair("dark_oak_stairs","south",true));
        if(two){
            storey(8,true,JZ,PZ0,PX0,PX1,ints(8,11,14,17,20),-1);
            storey(8,true,PZ1,HZ1,PX0,PX1,ints(8,11,14,17,20),-1);
            storey(8,false,PX0,HX0,JZ,PZ1,ints(16,20,23,26,29),-1);
            storey(8,false,PX1,HX1,JZ,PZ1,ints(16,20,23,26,29),-1);
            // Upper floor with a stairwell: straight flight rising west along z20, landing at x11.
            for(int x=HX0+1;x<HX1;x++)for(int z=HZ0;z<HZ1;z++)set(x,8,z,z==20&&x>=12?"air":"dark_oak_planks");
            for(int x=12;x<HX1;x++)box(x,9,20,x,11,20,"air");
            for(int i=0;i<7;i++){set(18-i,1+i,20,stair("dark_oak_stairs","west",false));for(int y=2+i;y<=4+i&&y<=6;y++)set(18-i,y,20,"air");}
            for(int z:new int[]{22,25})for(int x=HX0+1;x<HX1;x++)set(x,13,z,log(POST,'x'));
            set(12,12,22,"lantern[hanging=true]");set(16,12,25,"lantern[hanging=true]");
            gable(JZ,14,base);gable(PZ1,14,base);
        }else{
            for(int x=PX0;x<=PX1;x++)set(x,8,JZ,log(BEAM,'x'));
            gable(JZ,9,base);gable(PZ1,8,base);
        }
        // Gable roof 45 deg, ridge along z, one-block overhang; the east eave stops against the keep.
        for(int x=7;x<=21;x++){int h=base+Math.min(x-7,21-x);
            for(int z=15;z<=30;z++){if(two&&x==21&&keepCol(x,z)||chimney(x,z))continue;
                if(x==HCX){set(x,h-1,z,RF);set(x,h,z,RC);}
                else set(x,h,z,stair(RS,x<HCX?"east":"west",false));}}
        for(int z=15;z<=30;z++){set(7,base-1,z,"dark_oak_slab[type=top]");if(!(two&&keepCol(21,z))&&!chimney(21,z))set(21,base-1,z,"dark_oak_slab[type=top]");}
        // Ridge lantern turret.
        int r=base+7;
        // Round 2 review ("an open four-post frame under a pagoda roof"): a square belfry — corner posts, louvred openings of spruce
        // trapdoors on every face, a lamp inside — under one steep pyramid that overhangs it by a block, a wall-post finial.
        box(13,r,22,15,r,24,"dark_oak_planks");
        for(int x:new int[]{13,15})for(int z:new int[]{22,24})box(x,r+1,z,x,r+2,z,log(POST,'y'));
        for(int y=r+1;y<=r+2;y++){set(14,y,22,"spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");set(14,y,24,"spruce_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
            set(13,y,23,"spruce_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]");set(15,y,23,"spruce_trapdoor[facing=east,half=bottom,open=true,powered=false,waterlogged=false]");}
        set(14,r+1,23,"air");set(14,r+2,23,"lantern[hanging=true]");
        box(13,r+3,22,15,r+3,24,"dark_oak_planks");
        // Round 1 review: the cupola's cap overhangs its posts by one on every side (an outer ring of stairs a course lower), then the inner ring.
        for(int x=12;x<=16;x++)for(int z=21;z<=25;z++)if(x==12||x==16||z==21||z==25){int dx=x-14,dz=z-23;String f=inward(dx,dz);
            set(x,r+3,z,Math.abs(dx)==2&&Math.abs(dz)==2?RS+"[facing="+(dx<0?"east":"west")+",half=bottom,shape="+((dx<0)==(dz<0)?"outer_right":"outer_left")+"]":stair(RS,f,false));}
        for(int x=13;x<=15;x++)for(int z=22;z<=24;z++)if(x!=14||z!=23){set(x,r+4,z,stair(RS,inward(x-14,z-23),false));set(x,r+5,z,stair(RS,inward(x-14,z-23),false));}
        set(14,r+4,23,RF);set(14,r+5,23,RF);set(14,r+6,23,RF);set(14,r+7,23,RW);
        // Stone chimney breast on the east face, three above the roof.
        for(int x=20;x<=21;x++)for(int z=25;z<=26;z++){for(int y=1;y<=base+4;y++)set(x,y,z,y<=1?"cobblestone":mas(x,y,z));}
        // Round 2 review: a stepped foot against the wall and a capped head, not a plain column.
        for(int x=20;x<=21;x++)for(int z=25;z<=26;z++)set(x,base+5,z,"stone_brick_slab[type=bottom]");
        for(int x=20;x<=21;x++)set(x,base+4,24,stair("stone_brick_stairs","north",true));
        for(int z=25;z<=26;z++){set(22,0,z,"cobblestone");set(22,1,z,"cobblestone");set(22,2,z,stair("stone_brick_stairs","west",false));}
        // Plinth drip course.
        for(int z=PZ0;z<=PZ1;z++)set(7,1,z,stair("cobblestone_stairs","east",false));
        for(int x=PX0;x<=PX1;x++)set(x,1,30,stair("cobblestone_stairs","north",false));
        for(int z=27;z<=PZ1;z++)set(21,1,z,stair("cobblestone_stairs","west",false));
        for(int x=PX0;x<=PX1;x++)if(x<12||x>16)set(x,1,JZ,stair("cobblestone_stairs","south",false));
        // North porch 5x3 with its own gable, butting into the jetty.
        for(int x=12;x<=16;x++)for(int z=13;z<=16;z++)set(x,0,z,"stone_bricks");
        for(int x:new int[]{12,16}){box(x,1,13,x,3,13,log(POST,'y'));box(x,4,14,x,4,15,log(BEAM,'z'));}
        for(int x=12;x<=16;x++)set(x,4,13,log(BEAM,'x'));
        for(int x=11;x<=17;x++){int h=5+Math.min(x-11,17-x);
            for(int z=12;z<=16;z++){if(x==HCX){setIfEmpty(x,h-1,z,RF);setIfEmpty(x,h,z,RC);}else setIfEmpty(x,h,z,stair(RS,x<HCX?"east":"west",false));}
            if(x>=12&&x<=16)for(int y=5;y<h-(x==HCX?1:0);y++)set(x,y,13,x==HCX?log(POST,'y'):PLASTER);}
        set(HCX,6,14,"lantern[hanging=true]");
        // Approach: gate lane x17..19 and the forecourt, paved; two lantern posts off the lane.
        for(int x=17;x<=19;x++)for(int z=0;z<=11;z++)pave(x,z);
        for(int x=12;x<=19;x++)for(int z=12;z<=14;z++)pave(x,z);
        for(int[] l:new int[][]{{11,13},{20,9}}){box(l[0],1,l[1],l[0],2,l[1],"dark_oak_fence");set(l[0],3,l[1],"lantern[hanging=false]");}
    }
    private void pave(int x,int z){if(get(x,0,z)!=null)return;String was=part;part="yard";set(x,0,z,hash(x,0,z)<55?"polished_andesite":"cobblestone");part=was;}

    // ---------- II: tower house, the keep's first two storeys ----------
    static final int KX0=21,KX1=29,KZ0=15,KZ1=23;
    private static boolean keepOuter(int x,int z){return x==KX0||x==KX1||z==KZ0||z==KZ1;}
    private static boolean keepInner(int x,int z){return !keepOuter(x,z)&&(x==KX0+1||x==KX1-1||z==KZ0+1||z==KZ1-1);}
    private void keepWalls(int y0,int y1){
        for(int x=KX0;x<=KX1;x++)for(int z=KZ0;z<=KZ1;z++)for(int y=y0;y<=y1;y++){
            if(keepOuter(x,z))set(x,y,z,y%5==0?"polished_andesite":mas(x,y,z));
            else if(keepInner(x,z))set(x,y,z,mas(x,y,z));
            else if(y%5!=0)set(x,y,z,"air");
            else set(x,y,z,y==20?"smooth_stone":"dark_oak_planks");}}
    /** Windows of a keep storey; the west face looks onto the hall's roof until storey 4. */
    private void keepWindows(int storey){int y=storey*5-3; // y7..8, 12..13, 17..18
        for(int x:new int[]{23,27})for(int yy=y;yy<=y+1;yy++){set(x,yy,KZ0,"air");set(x,yy,KZ0+1,pane("glass_pane",true));set(x,yy,KZ1,"air");set(x,yy,KZ1-1,pane("glass_pane",true));}
        for(int z:new int[]{17,21})for(int yy=y;yy<=y+1;yy++){set(KX1,yy,z,"air");set(KX1-1,yy,z,pane("glass_pane",false));
            if(storey>=4){set(KX0,yy,z,"air");set(KX0+1,yy,z,pane("glass_pane",false));}}}
    /** Switchback stair in the keep's east bay: lane A x27 rises north, lane B x26 rises south, 3 air over every step. */
    private void keepStair(int storey){int b=(storey-1)*5;
        if(storey%2==1){
            if(storey>1)set(27,b+1,21,stair("stone_brick_stairs","east",false));
            for(int i=storey>1?1:0;i<5;i++)set(27,b+1+i,21-i,stair("stone_brick_stairs","north",false));
            for(int z=18;z<=20;z++)set(27,b+5,z,"air");}
        else{
            set(26,b+1,17,stair("stone_brick_stairs","west",false));
            for(int i=1;i<5;i++)set(26,b+1+i,17+i,stair("stone_brick_stairs","south",false));
            for(int z=18;z<=20;z++)set(26,b+5,z,"air");}}
    private void keepLower(){
        box(KX0,0,KZ0,KX1,0,KZ1,"cobblestone");
        // Round 2 review ("the tower is shorter than the hall"): the tower house rises three storeys, its crown five over the hall's eave;
        // level III builds on it.
        keepWalls(1,15);
        for(int x=KX0;x<=KX1;x++)for(int z=KZ0;z<=KZ1;z++)if(keepOuter(x,z)){set(x,16,z,mas(x,16,z));merlon(x,17,z);}
        keepWindows(2);keepWindows(3);
        for(int x:new int[]{23,27})for(int y=2;y<=3;y++){set(x,y,KZ0,"air");set(x,y,KZ1,"air");}
        for(int z:new int[]{17,21})for(int y=2;y<=3;y++)set(KX1,y,z,"air");
        // Ground door recessed in the north face; the hall's east door opens straight into the keep.
        set(25,1,KZ0,"air");set(25,2,KZ0,"air");door(25,1,KZ0+1,"north");set(25,3,KZ0,"chiseled_stone_bricks");
        for(int x=KX0;x<=KX0+1;x++){set(x,1,21,"air");set(x,2,21,"air");}set(KX0,3,21,"chiseled_stone_bricks");
        // Talus course on the three free faces.
        for(int x=KX0;x<=KX1;x++){if(x!=25)set(x,1,KZ0-1,stair("stone_brick_stairs","south",false));set(x,1,KZ1+1,stair("stone_brick_stairs","north",false));}
        for(int z=KZ0;z<=KZ1;z++)set(KX1+1,1,z,stair("stone_brick_stairs","west",false));
        pilasters(1,15);
        keepStair(1);keepStair(2);keepStair(3);set(24,14,19,"lantern[hanging=true]");
        set(23,1,17,"barrel[facing=up,open=false]");set(23,1,18,"barrel[facing=up,open=false]");
        set(24,4,19,"lantern[hanging=true]");set(24,9,19,"lantern[hanging=true]");
    }
    /** Buttress 3 wide, 1 deep, at the middle of the keep's south face (III ends it with a stair at y18). */
    private void pilasters(int y0,int y1){
        for(int i=0;i<3;i++){set(24+i,0,KZ1+1,"cobblestone");
            for(int y=y0;y<=y1;y++)set(24+i,y,KZ1+1,y%5==0?"polished_andesite":mas(24+i,y,KZ1+1));
            if(y0==1)set(24+i,1,KZ1+2,stair("stone_brick_stairs","north",false));}}

    // ---------- III: the keep to full height with corbelled corner turrets; forebuilding to the hall ----------
    private static final int[][] TURRETS={{20,14},{28,14},{20,22},{28,22}};
    private void keepUpper(){
        keepWalls(11,20);
        pilasters(11,17);
        for(int i=0;i<3;i++)set(24+i,18,KZ1+1,stair("stone_brick_stairs","north",false));
        keepWindows(3);keepWindows(4);keepStair(3);keepStair(4);
        set(24,14,19,"lantern[hanging=true]");set(24,19,19,"lantern[hanging=true]");
        for(int x=KX0;x<=KX1;x++)for(int z=KZ0;z<=KZ1;z++)if(keepOuter(x,z)){set(x,21,z,mas(x,21,z));merlon(x,22,z);}
        for(int[] t:TURRETS)turret(t[0],t[1],24);
        // Round 2 review: the parapet between the bartizans corbelled out on stair consoles from III on (machicolations).
        for(int i=23;i<=27;i++){machicolation(i,KZ0-1,"south",i,KZ0);machicolation(i,KZ1+1,"north",i,KZ1);}
        for(int i=17;i<=21;i++){machicolation(KX0-1,i,"east",KX0,i);machicolation(KX1+1,i,"west",KX1,i);}
        set(23,21,17,"lantern[hanging=false]");set(23,21,21,"lantern[hanging=false]");
    }
    /** 3x3 bartizan whose outer row and column overhang the keep by one on upside-down stair corbels. */
    private void turret(int x0,int z0,int top){
        for(int x=x0;x<x0+3;x++)for(int z=z0;z<z0+3;z++){
            boolean out=x<KX0||x>KX1||z<KZ0||z>KZ1;
            if(out)set(x,20,z,stair("stone_brick_stairs",x<KX0?"east":x>KX1?"west":z<KZ0?"south":"north",true));
            for(int y=21;y<=top;y++)set(x,y,z,mas(x,y,z));
            // Round 2 review ("a jagged crown"): the bartizan's parapet in the keep's own 1:1 rhythm, two high.
            boolean rim=x!=x0+1||z!=z0+1;
            if(rim)merlon(x,top+1,z);else{set(x,top+1,z,"air");set(x,top+2,z,"air");}}
        int cx=x0+1,cz=z0+1,ox=x0<KX0?x0:x0+2,oz=z0<KZ0?z0:z0+2;
        set(ox,22,cz,"air");set(ox,23,cz,"air");set(cx,22,oz,"air");set(cx,23,oz,"air");}
    /** Stair-house against the keep's north face: ground door west, a flight rising east to a first-floor keep door;
     *  its west wall runs on to the hall's jetty corner, so hall, forebuilding and keep read as one block. */
    private void forebuilding(){
        for(int x=20;x<=26;x++)for(int z=12;z<=14;z++){
            boolean wall=x==20||x==26||z==12;
            set(x,0,z,"cobblestone");
            if(wall){for(int y=1;y<=9;y++)set(x,y,z,y==5?"polished_andesite":mas(x,y,z));set(x,10,z,mas(x,10,z));merlon(x,11,z);}
            else{box(x,1,z,x,8,z,"air");set(x,9,z,"smooth_stone");}}
        for(int i=0;i<5;i++){set(21+i,1+i,13,stair("stone_brick_stairs","east",false));if(i<4)for(int y=1;y<=i;y++)set(21+i,y,13,mas(21+i,y,13));}
        set(25,5,14,"stone_bricks");
        box(25,6,KZ0,25,7,KZ0,"air");door(25,6,KZ0+1,"north");set(25,8,KZ0,"chiseled_stone_bricks");
        door(20,1,13,"west");set(20,3,13,"chiseled_stone_bricks");
        for(int x:new int[]{22,24})box(x,6,12,x,7,12,"air");box(20,6,14,20,7,14,"air");
        set(23,8,13,"lantern[hanging=true]");
        for(int z=15;z<=16;z++){set(20,0,z,"cobblestone");for(int y=1;y<=6;y++)set(20,y,z,mas(20,y,z));}
        set(20,7,15,"stone_brick_slab[type=bottom]");
        for(int x=20;x<=25;x++)talus(x,11,"south");
        for(int z=12;z<=14;z++)if(z!=13)talus(19,z,"east");
    }

    // ---------- IV: ward wall with round corner towers, arched gate, well and kitchen ----------
    private static boolean wallZone(int x,int z){return x>=4&&x<=32&&z>=4&&z<=32&&(x<=6||x>=31||z<=6||z>=31);}
    private static boolean wallFace(int x,int z){return x==4||x==32||z==4||z==32;}
    private static boolean inWard(int x,int z){return x>=4&&x<=32&&z>=4&&z<=32;}
    private static boolean gatehouse(int x,int z){return x>=12&&x<=24&&z>=1&&z<=8;}
    private static final int[][] CORNERS={{4,4},{32,4},{4,32},{32,32}};
    private static boolean cornerTower(int x,int z){for(int[] c:CORNERS)if(inDisc(x,z,c[0],c[1],12.25))return true;return false;}
    private void ward(){
        for(int x=4;x<=32;x++)for(int z=4;z<=32;z++){
            if(!wallZone(x,z)||x>=17&&x<=19&&z<=6)continue;
            boolean face=wallFace(x,z);
            if(get(x,0,z)==null)set(x,0,z,"cobblestone");
            for(int y=1;y<=6;y++)set(x,y,z,mas(x,y,z));
            if(face){set(x,7,z,"polished_andesite");merlon(x,8,z);
                int along=(x==4||x==32)?z:x;
                if(along%4==2&&along>=10&&along<=26&&!(z==4&&along>=11&&along<=25))for(int y=3;y<=4;y++)set(x,y,z,"air");}}
        // Gate: 3 wide, 4 high with a 5th in the arch, stair springers, keystone, fence-gate leaves.
        for(int x=17;x<=19;x++)for(int z=4;z<=6;z++){
            for(int y=1;y<=4;y++)set(x,y,z,"air");
            set(x,5,z,x==18?"air":(z==5?mas(x,5,z):stair("stone_brick_stairs",x==17?"west":"east",true)));
            set(x,6,z,x==18&&z!=5?"chiseled_stone_bricks":mas(x,6,z));
            if(z==4){set(x,7,z,"polished_andesite");merlon(x,8,z);}}
        for(int x=17;x<=19;x++)set(x,1,5,"dark_oak_fence_gate[facing=north,in_wall=false,open=false]");
        // Round 2 review ("a tiny gate with no depth"): from IV the gate is a gatehouse — two towers projecting three beyond the curtain, a
        // passage three wide and seven deep with arches at both ends and the portcullis; V raises it with the curtain.
        gate(10);
        // The corner towers stand a storey over the curtain's merlons.
        for(int[] c:CORNERS){tower(c[0],c[1],12.25,1,11,4);crown(c[0],c[1],12.25,12,true);}
        for(int i=4;i<=32;i++){talus(3,i,"east");talus(33,i,"west");talus(i,33,"north");if(i<11||i>25)talus(i,3,"south");}
    }
    private void wardYard(){
        wallStair(1,6);
        leanTo(8,11,false);
        set(9,1,8,"smoker[facing=south,lit=false]");set(10,1,8,"furnace[facing=south,lit=false]");set(9,1,10,"barrel[facing=up,open=false]");
        for(int x=13;x<=15;x++)for(int z=9;z<=11;z++){boolean c=x==14&&z==10;set(x,0,z,c?"air":"cobblestone");set(x,1,z,c?"air":"cobblestone");}
        for(int x:new int[]{13,15})for(int z:new int[]{9,11})box(x,2,z,x,3,z,wallPost());
        box(13,4,9,15,4,11,"dark_oak_slab[type=bottom]");set(14,3,10,"chain[axis=y]");set(14,2,10,"lantern[hanging=true]");
        for(int x=8;x<=16;x++)pave(x,12);
    }
    /** Flight up the inside of the west wall at x7, rising north (step k at z=17-k), solid under, 3 air over. */
    private void wallStair(int k0,int k1){for(int k=k0;k<=k1;k++){int z=17-k;set(7,0,z,"cobblestone");for(int y=1;y<k;y++)set(7,y,z,mas(7,y,z));set(7,k,z,stair("stone_brick_stairs","north",false));}}
    /** Timber lean-to against the north wall, x0..x1 by z7..11, shed roof falling south, door on the south side. */
    private void leanTo(int x0,int x1,boolean mirrored){
        for(int x=x0;x<=x1;x++)for(int z=7;z<=11;z++){
            boolean ring=x==x0||x==x1||z==7||z==11,corner=(x==x0||x==x1)&&(z==7||z==11);
            set(x,0,z,ring?"cobblestone":"dark_oak_planks");
            int roof=4+(11-z);
            for(int y=1;y<roof;y++){
                if(!ring){if(y<=3)set(x,y,z,"air");continue;}
                if(y>3&&z!=7&&x!=x0&&x!=x1)continue;
                set(x,y,z,y==1?"cobblestone":corner&&y<=3?log(POST,'y'):y==4&&z==11?log(BEAM,'x'):PLASTER);}}
        int roofX0=mirrored?x0-1:x0,roofX1=mirrored?x1:x1+1;
        for(int x=roofX0;x<=roofX1;x++){for(int z=7;z<=11;z++)setIfEmpty(x,4+(11-z),z,stair(RS,"north",false));setIfEmpty(x,3,12,stair(RS,"north",false));}
        door(mirrored?x0+1:x1-1,1,11,"south");
        set(mirrored?x0:x1,2,9,pane("glass_pane",false));}

    // ---------- V: curtain raised, towers raised, gatehouse, granary ----------
    private void curtain(){
        for(int x=4;x<=32;x++)for(int z=4;z<=32;z++){
            if(!wallZone(x,z)||gatehouse(x,z)||cornerTower(x,z))continue;
            if(wallFace(x,z)){for(int y=8;y<=11;y++)set(x,y,z,mas(x,y,z));merlon(x,12,z);}
            else for(int y=7;y<=10;y++)set(x,y,z,mas(x,y,z));}
        for(int[] c:CORNERS){tower(c[0],c[1],12.25,12,16,14);crown(c[0],c[1],12.25,17,true);}
        gate(14);
    }
    private void curtainYard(){
        wallStair(7,10);
        leanTo(26,29,true);
        set(28,1,8,"barrel[facing=up,open=false]");set(28,2,8,"barrel[facing=up,open=false]");set(28,1,10,"hay_block[axis=y]");
        set(28,1,9,"chest[facing=west,type=single,waterlogged=false]");set(27,1,8,"hay_block[axis=y]");
        for(int x=27;x<=30;x++)pave(x,12);
    }
    private void talus(int x,int z,String facing){if(x>=0&&x<LOT&&z>=0&&z<LOT&&get(x,1,z)==null)set(x,1,z,stair("stone_brick_stairs",facing,false));}
    /** Round tower body from y0 to y1: belts of polished andesite on the rim every 5, slits (slitY..+2) to the field. */
    private void tower(int cx,int cz,double r2,int y0,int y1,int slitY){
        int r=(int)Math.ceil(Math.sqrt(r2));
        for(int x=cx-r;x<=cx+r;x++)for(int z=cz-r;z<=cz+r;z++){
            if(!inDisc(x,z,cx,cz,r2))continue;
            if(y0==1)set(x,0,z,"cobblestone");
            boolean rim=!inDisc(x+1,z,cx,cz,r2)||!inDisc(x-1,z,cx,cz,r2)||!inDisc(x,z+1,cx,cz,r2)||!inDisc(x,z-1,cx,cz,r2);
            for(int y=y0;y<=y1;y++)set(x,y,z,rim&&y%5==0?"polished_andesite":mas(x,y,z));}
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int sx=cx+d[0]*r,sz=cz+d[1]*r;
            while(!inDisc(sx,sz,cx,cz,r2)){sx-=d[0];sz-=d[1];}
            if(!inWard(sx,sz))for(int y=slitY;y<=Math.min(slitY+2,y1);y++)set(sx,y,sz,"air");}
        if(y0==1)for(int x=cx-r-1;x<=cx+r+1;x++)for(int z=cz-r-1;z<=cz+r+1;z++){
            if(inDisc(x,z,cx,cz,r2)||inWard(x,z))continue;
            String f=inDisc(x+1,z,cx,cz,r2)?"east":inDisc(x-1,z,cx,cz,r2)?"west":inDisc(x,z+1,cx,cz,r2)?"south":inDisc(x,z-1,cx,cz,r2)?"north":null;
            if(f!=null)talus(x,z,f);}}
    /** Tower top at y: solid floor, rim with its belt, and (optionally) 2-high 1:1 merlons above the rim. */
    private void crown(int cx,int cz,double r2,int y,boolean merlons){
        int r=(int)Math.ceil(Math.sqrt(r2));
        for(int x=cx-r;x<=cx+r;x++)for(int z=cz-r;z<=cz+r;z++){
            if(!inDisc(x,z,cx,cz,r2))continue;
            boolean rim=!inDisc(x+1,z,cx,cz,r2)||!inDisc(x-1,z,cx,cz,r2)||!inDisc(x,z+1,cx,cz,r2)||!inDisc(x,z-1,cx,cz,r2);
            set(x,y,z,rim&&y%5==0?"polished_andesite":mas(x,y,z));
            if(!merlons)continue;
            if(rim)merlon(x,y+1,z);else{set(x,y+1,z,"air");set(x,y+2,z,"air");}}}
    /** Gatehouse: two towers projecting 3 beyond the curtain, a 7-deep passage 3 wide and 4-5 high with arches at
     *  both faces, a half-raised iron portcullis behind the outer arch, then the leaves of level IV. */
    private void gate(int top){
        for(int x=12;x<=24;x++)for(int z=1;z<=8;z++){
            boolean tower=x<=16||x>=20;
            if(tower){
                boolean face=x==12||x==16||x==20||x==24||z==1||z==8;
                if(get(x,0,z)==null)set(x,0,z,"cobblestone");
                for(int y=1;y<=top;y++)setIfEmpty(x,y,z,face&&y%5==0?"polished_andesite":mas(x,y,z));
                set(x,top+1,z,face?"polished_andesite":mas(x,top+1,z));
                if(face)merlon(x,top+2,z);else{set(x,top+2,z,"air");set(x,top+3,z,"air");}
            }else if(z>=2){
                boolean arch=z==2||z==8;
                for(int y=1;y<=4;y++)setIfEmpty(x,y,z,"air");
                if(x==18)setIfEmpty(x,5,z,"air");else setIfEmpty(x,5,z,arch?stair("stone_brick_stairs",x==17?"west":"east",true):mas(x,5,z));
                setIfEmpty(x,6,z,arch&&x==18?"chiseled_stone_bricks":mas(x,6,z));
                for(int y=7;y<=top-2;y++)setIfEmpty(x,y,z,mas(x,y,z));
                set(x,top-1,z,mas(x,top-1,z));
                if(arch)merlon(x,top,z);else{set(x,top,z,"air");set(x,top+1,z,"air");}}}
        for(int x=17;x<=19;x++){set(x,3,3,pane("iron_bars",true));set(x,4,3,pane("iron_bars",true));}
        set(18,5,3,pane("iron_bars",true));set(18,5,7,"lantern[hanging=true]");
        for(int x:new int[]{14,22})for(int y=6;y<=8;y++)set(x,y,1,"air");
        for(int y=6;y<=8;y++){set(12,y,3,"air");set(24,y,3,"air");}
        for(int x=12;x<=24;x++)if(x<=16||x>=20)talus(x,0,"south");
        for(int z=1;z<=3;z++){talus(11,z,"east");talus(25,z,"west");}
    }

    // ---------- VI: full castle ----------
    private void fullCastle(){
        // Half-round towers mid-way along west and east, projecting 3; the east wall-walk passes through.
        // Round 2 review ("a forest of equal cones"): the D-towers keep flat crenellated tops; cones only on the corner towers, the keep's
        // bartizans the highest points.
        for(int[] d:new int[][]{{3,19},{33,19}}){tower(d[0],d[1],5.0,1,14,4);crown(d[0],d[1],5.0,15,true);}
        for(int z=18;z<=20;z++)box(31,11,z,31,13,z,"air");
        // One roof family: slate cones on every round tower, slate spires on the gate towers and the keep turrets.
        // Round 1 review ("the cones are all one height"): the corner towers rise three courses more under a polished andesite trim ring,
        // so their cones stand over those of the D-towers and the gate.
        for(int[] c:CORNERS){tower(c[0],c[1],12.25,18,19,18);
            for(int x=c[0]-4;x<=c[0]+4;x++)for(int z=c[1]-4;z<=c[1]+4;z++){if(!inDisc(x,z,c[0],c[1],12.25))continue;
                boolean rim=!inDisc(x+1,z,c[0],c[1],12.25)||!inDisc(x-1,z,c[0],c[1],12.25)||!inDisc(x,z+1,c[0],c[1],12.25)||!inDisc(x,z-1,c[0],c[1],12.25);
                if(rim)set(x,19,z,"polished_andesite");}
            cone(c[0],c[1],4.5,20);}
        for(int[] t:TURRETS)hip(t[0],t[1],t[0]+2,t[1]+2,26);
    }
    private void arsenal(){
        for(int x=22;x<=28;x++)for(int z=27;z<=30;z++){
            boolean ring=x==22||x==28||z==27||z==30;set(x,0,z,ring?"cobblestone":"stone_bricks");
            int roof=4+(z-27);
            for(int y=1;y<roof;y++){if(!ring){if(y<=3)set(x,y,z,"air");continue;}if(y>3&&z!=30&&x!=22&&x!=28)continue;set(x,y,z,mas(x,y,z));}}
        for(int x=22;x<=29;x++){for(int z=27;z<=30;z++)set(x,4+(z-27),z,stair(RS,"south",false));set(x,3,26,stair(RS,"south",false));}
        door(25,1,27,"north");set(23,2,27,pane("glass_pane",true));set(27,2,27,pane("glass_pane",true));
        set(23,1,29,"smithing_table");set(24,1,29,"grindstone[face=floor,facing=north]");set(26,1,29,"fletching_table");set(27,1,29,"barrel[facing=up,open=false]");set(27,1,28,"barrel[facing=up,open=false]");
        for(int x=21;x<=30;x++)for(int z=25;z<=26;z++)pave(x,z);
    }
    /** Smooth 2:1 cone of slate over a round tower: eave ring `re` (one beyond the wall) on corbels at y0-1, then each
     *  ring two stairs high, two up for one in; spire of a tile wall and two fence posts. */
    private void cone(int cx,int cz,double re,int y0){
        int R=(int)Math.ceil(re),K=(int)Math.floor(re);
        for(int dx=-R;dx<=R;dx++)for(int dz=-R;dz<=R;dz++){
            double d=Math.sqrt(dx*dx+dz*dz);if(d>re+1e-9)continue;
            int x=cx+dx,z=cz+dz,k=(int)Math.floor(re-d+1e-9);String f=inward(dx,dz);
            if(dx==0&&dz==0){spire(x,z,y0,K);continue;}
            if(k==0){set(x,y0,z,stair(RS,f,false));if((x+z)%2==0&&empty(x,y0-2,z))setIfEmpty(x,y0-1,z,stair("stone_brick_stairs",f,true));continue;}
            ring(x,z,y0,k,f);}}
    /** Ring k of a slate roof: solid core, then two stairs on top of each other (2:1, a fine saw-tooth rather than steps). */
    private void ring(int x,int z,int y0,int k,String f){
        for(int y=y0;y<=y0+2*k-3;y++)set(x,y,z,RF);
        set(x,y0+2*k-2,z,stair(RS,f,false));set(x,y0+2*k-1,z,stair(RS,f,false));}
    private void spire(int x,int z,int y0,int K){
        for(int y=y0;y<=y0+2*K-2;y++)set(x,y,z,RF);
        set(x,y0+2*K-1,z,RW);set(x,y0+2*K,z,RW);}
    /** The same 2:1 slate roof over a rectangle x0..x1 by z0..z1: eave one wider, a ridge slab or a spire at the top. */
    private void hip(int x0,int z0,int x1,int z1,int y0){
        int ex0=x0-1,ez0=z0-1,ex1=x1+1,ez1=z1+1,K=Math.min((ex1-ex0)/2,(ez1-ez0)/2),tops=0;
        for(int x=ex0;x<=ex1;x++)for(int z=ez0;z<=ez1;z++)if(Math.min(Math.min(x-ex0,ex1-x),Math.min(z-ez0,ez1-z))==K)tops++;
        for(int x=ex0;x<=ex1;x++)for(int z=ez0;z<=ez1;z++){
            int ix=Math.min(x-ex0,ex1-x),iz=Math.min(z-ez0,ez1-z),k=Math.min(ix,iz);
            String f=ix<=iz?(x-ex0<=ex1-x?"east":"west"):(z-ez0<=ez1-z?"south":"north");
            if(k==K){if(tops==1){spire(x,z,y0,K);continue;}
                for(int y=y0;y<=y0+2*K-3;y++)set(x,y,z,RF);set(x,y0+2*K-2,z,RC);continue;}
            if(k==0){set(x,y0,z,stair(RS,f,false));if((x+z)%2==0&&!keepCol(x,z))setIfEmpty(x,y0-1,z,stair("stone_brick_stairs",f,true));continue;}
            ring(x,z,y0,k,f);}}
    /** Corbel at (x,19,z) every other cell, parapet on it with 2-high merlons, old crown behind it removed. */
    private void machicolation(int x,int z,String facing,int ix,int iz){
        set(x,19,z,(x+z)%2==0?stair("stone_brick_stairs",facing,true):"air");
        set(x,20,z,mas(x,20,z));set(x,21,z,mas(x,21,z));merlon(x,22,z);
        set(ix,21,iz,"air");set(ix,22,iz,"air");set(ix,23,iz,"air");}

    // ---------- fixed functional cells of the 11x11 core (y1), never rebuilt ----------
    private void kits(){
        set(10,1,22,"chest[facing=east,type=left,waterlogged=false]");set(10,1,23,"chest[facing=east,type=right,waterlogged=false]");
        set(10,1,25,"chest[facing=east,type=left,waterlogged=false]");set(10,1,26,"chest[facing=east,type=right,waterlogged=false]");
        set(14,1,25,"lectern[facing=north,has_book=false,powered=false]");set(12,1,25,"crafting_table");
        if(level>=2){set(14,1,23,SEAL);set(18,1,23,"lectern[facing=west,has_book=false,powered=false]");set(18,1,24,"bookshelf");}
        if(level>=3){set(18,1,25,"cartography_table");set(18,1,26,"bookshelf");set(18,1,27,"barrel[facing=up,open=false]");}
        if(level>=4){set(10,1,27,"composter");set(11,1,27,"chiseled_stone_bricks");set(11,2,27,"lantern[hanging=false]");set(12,1,27,"furnace[facing=north,lit=false]");}
        if(level>=5){set(13,1,27,"polished_diorite");set(14,1,27,"smooth_stone");set(14,2,27,"lantern[hanging=false]");set(15,1,27,"polished_granite");set(16,1,27,"furnace[facing=north,lit=false]");}
        if(level>=6){set(10,1,20,"lectern[facing=east,has_book=false,powered=false]");set(10,1,19,"chiseled_deepslate");set(10,2,19,"lantern[hanging=false]");
            set(11,1,19,"bookshelf");set(12,1,19,"deepslate_tiles");set(17,1,27,"polished_deepslate");}
    }
    /** No hall block may stand in, on or directly under a block of a wall, tower or the keep. */
    private void checkHall(){
        for(var e:m.entrySet()){var c=e.getKey();
            if(!"hall".equals(owner.get(c))||e.getValue().equals("air"))continue;
            for(int dy=-1;dy<=1;dy++){var n=new Cell(c.x(),c.y()+dy,c.z());String o=owner.get(n),s=m.get(n);
                if(dy!=0&&o!=null&&fort(o)&&s!=null&&!s.equals("air"))clips.add(o+" touches hall at "+n);}}}

    // ---------- offline check: `java CastlePlan.java <outDir>` writes castle_N.json for voxel.py and prints the diff ----------
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args.length>0?args[0]:".");CastlePlan prev=null;
        for(int n=1;n<=MAX_LEVEL;n++){var p=build(n);write(p.m,out.resolve("castle_"+n+".json"),"castle_"+n);
            int solid=0,hall=0;for(var e:p.m.entrySet())if(!e.getValue().equals("air")){solid++;if("hall".equals(p.owner.get(e.getKey())))hall++;}
            Map<String,Integer> rebuilt=new TreeMap<>();
            if(prev!=null)for(var e:prev.m.entrySet()){if(e.getValue().equals("air"))continue;String now=p.m.get(e.getKey());
                if(!e.getValue().equals(now)){String k=prev.owner.get(e.getKey())+"->"+p.owner.get(e.getKey())+(now==null||now.equals("air")?" removed":" replaced");rebuilt.merge(k,1,Integer::sum);}
                if("kits".equals(prev.owner.get(e.getKey()))&&!e.getValue().equals(now))throw new IllegalStateException("Functional cell changed at "+e.getKey());}
            System.out.println("level "+n+": solid="+solid+" hall="+hall+" clips=0 rebuilt="+rebuilt);prev=p;}
    }
    private static void write(Map<Cell,String> p,Path file,String name)throws Exception{
        int h=0;for(var c:p.keySet())h=Math.max(h,c.y()+1);
        Map<String,Character> legend=new LinkedHashMap<>();String pool="#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&'()*+,-/:;<=>?@[]^_`{|}~";
        char[][][] grid=new char[h][LOT][LOT];for(var a:grid)for(var r:a)Arrays.fill(r,' ');
        for(var e:p.entrySet()){String s=e.getValue().equals(SEAL)?"chiseled_stone_bricks":e.getValue();
            Character ch=legend.get(s);if(ch==null){int i=legend.size();ch=i<pool.length()?pool.charAt(i):(char)(0x100+i);legend.put(s,ch);}
            grid[e.getKey().y()][e.getKey().z()][e.getKey().x()]=ch;}
        var sb=new StringBuilder("{\"name\":\""+name+"\",\"legend\":{");boolean first=true;
        for(var e:legend.entrySet()){if(!first)sb.append(',');first=false;sb.append('"').append(esc(e.getValue())).append("\":\"").append(e.getKey()).append('"');}
        sb.append("},\"layers\":[");
        for(int y=0;y<h;y++){if(y>0)sb.append(',');sb.append('[');for(int z=0;z<LOT;z++){if(z>0)sb.append(',');sb.append('"');for(int x=0;x<LOT;x++)sb.append(esc(grid[y][z][x]));sb.append('"');}sb.append(']');}
        sb.append("]}");Files.writeString(file,sb.toString(),StandardCharsets.UTF_8);}
    private static String esc(char c){return c<128?String.valueOf(c):String.format("\\u%04x",(int)c);}
}
