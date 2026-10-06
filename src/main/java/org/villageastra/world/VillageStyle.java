package org.villageastra.world;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.IntUnaryOperator;

/** AD-121/122: the standard village look, taken from the approved castle-preview town hall ({@link CastlePlan#hall}): a cobblestone plinth, a dark
 *  oak frame (sill, posts, plate, braces) on the outside of smooth sandstone plaster, 45-degree dark oak shingle roofs (deepslate from level IV) whose overhang comes from setting
 *  walls and gables one block into the lot, framed gables with a king post, stone chimneys and hung lanterns.
 *  Pure data — block-state strings in lot cells (x across the door face, z back from it, y up; y=0 the floor) — so {@code java VillageStyle.java <dir>}
 *  dumps every design for voxel.py; DistinctArchitecture turns the strings into states. Every cell carries a role that LevelArchitecture rebuilds by:
 *  plinth, infill (plaster), roof, deck. Functional cells (door, beds, stock chest, stations) are written last, in the cells the older designs used. */
public final class VillageStyle {
    public record Cell(int x,int y,int z) {}
    /** Stands for the settlement's owned stock chest. */
    public static final String CHEST="#chest";
    public static final char PLINTH='p',INFILL='i',ROOF='r',DECK='d',FRAME='f',STONE='s',KIT='k',OTHER='o';
    static final String PLASTER="smooth_sandstone",POST="dark_oak_log",BEAM="stripped_dark_oak_log",PLANKS="dark_oak_planks",
        // Owner review 2026-09-22: a level-I roof takes only what the village makes at that stage (forester and carpentry), not deepslate, which a
        // level-I mine (floor Y 32) cannot reach — dark oak shingle; LevelArchitecture lays the slate from level IV.
        TILE="dark_oak_stairs",TILES="dark_oak_planks",CAP="dark_oak_slab[type=bottom]",
        // Round 2 review: barge boards and rakes in spruce, so the verge reads apart from the dark oak roof and frame.
        TRIM="spruce_stairs";
    private static final Map<String,int[]> LOTS=new LinkedHashMap<>();
    static { LOTS.put("home",new int[]{7,7});LOTS.put("home_2",new int[]{11,9});LOTS.put("clinic",new int[]{11,9});LOTS.put("cartographer",new int[]{9,11});
        LOTS.put("bakery",new int[]{9,9});LOTS.put("restaurant",new int[]{11,9});LOTS.put("smithy",new int[]{11,9});LOTS.put("carpentry",new int[]{11,9});LOTS.put("mill",new int[]{9,9});LOTS.put("engineering",new int[]{11,11});
        LOTS.put("mine",new int[]{8,9});LOTS.put("quarry",new int[]{7,7});LOTS.put("masonry",new int[]{11,9});LOTS.put("warehouse",new int[]{23,17});LOTS.put("laboratory",new int[]{11,11});
        LOTS.put("farm",new int[]{7,7});LOTS.put("forester",new int[]{15,21});LOTS.put("livestock",new int[]{17,25});LOTS.put("expedition",new int[]{9,11});LOTS.put("caravan",new int[]{13,11});
        LOTS.put("guard_house",new int[]{9,9});LOTS.put("barracks",new int[]{13,11});LOTS.put("archery",new int[]{11,9});LOTS.put("wall_tower",new int[]{5,5});LOTS.put("siege_camp",new int[]{11,11});
        LOTS.put("school",new int[]{11,11});
        // AD-135: annexes, each on a lot of its own beside its building (balance/annexes.json names the site).
        LOTS.put("carpentry_annex",new int[]{5,6});LOTS.put("masonry_annex",new int[]{5,7});LOTS.put("mill_annex",new int[]{9,9});LOTS.put("kennel_annex",new int[]{5,7});LOTS.put("dog_academy_annex",new int[]{5,7}); }
    private static final Map<String,VillageStyle> PLANS=new java.util.concurrent.ConcurrentHashMap<>();
    /** AD-143 / design review 2026-09-24: furniture set into cells of a design that were empty before, per design (see {@link #lateFurniture}).
     *  Each design registers its own cells with {@link #late} in a static block right after the set it declares, so the designs stay apart. */
    private static final Map<String,Set<Cell>> LATE=new HashMap<>();
    private static void late(String type,Set<Cell> cells){var all=new HashSet<>(LATE.getOrDefault(type,Set.of()));all.addAll(cells);LATE.put(type,Set.copyOf(all));}

    private final Map<Cell,String> m=new LinkedHashMap<>();
    private final Map<Cell,Character> roles=new HashMap<>();
    private final String id;private final int w,d;private char role=OTHER;
    /** AD-131: the level a levelled design ("forester@N") is drawn at; every other design is drawn once, at level I. */
    private final int level;
    /** The infill the framed walls and gables take: plaster, or boards on a working building's gables. */
    private String fill=PLASTER;
    /** AD-144: the wood of the design's window frames. */
    private String frame=org.villageastra.domain.FramedWindows.DEFAULT_WOOD;
    /** The roof covering roof() and crossRoof() lay: dark oak shingle by default, spruce shingle on a working shed. */
    private String rs=TILE,rf=TILES,rc=CAP;
    private VillageStyle(String id){this.id=id;var lot=LOTS.get(base(id));w=lot[0];d=lot[1];level=id.indexOf('@')<0?1:Integer.parseInt(id.substring(id.indexOf('@')+1));}
    /** AD-131: the designs drawn anew for every level ("type@N" is its own plan, not level I rebuilt by LevelArchitecture). */
    public static final Set<String> LEVELLED=Set.of("forester","livestock","warehouse");
    /** AD-147: the levelled designs whose plans draw the materials of every level themselves: LevelArchitecture lays only their stations and core. */
    public static final Set<String> OWN_MATERIALS=Set.of("warehouse");
    private static String base(String id){int at=id.indexOf('@');return at<0?id:id.substring(0,at);}
    /** A village-style design; "forester@N" (N 1..6) names a level of a levelled design. */
    public static boolean has(String id){int at=id.indexOf('@');if(at<0)return LOTS.containsKey(id);
        if(!LEVELLED.contains(id.substring(0,at)))return false;try{int n=Integer.parseInt(id.substring(at+1));return n>=1&&n<=6;}catch(NumberFormatException ex){return false;}}
    public static Set<String> ids(){return LOTS.keySet();}
    public static int[] lot(String id){return LOTS.get(base(id)).clone();}
    /** Every cell of the design, air included (a builder clears the lot volume to the top of the roof). */
    public static Map<Cell,String> plan(String id){return Collections.unmodifiableMap(of(id).m);}
    public static Map<Cell,Character> roles(String id){return Collections.unmodifiableMap(of(id).roles);}
    private static VillageStyle of(String id){
        if(!has(id))throw new IllegalArgumentException("Not a village-style design: "+id);
        return PLANS.computeIfAbsent(id.endsWith("@1")?base(id):id,VillageStyle::build);
    }
    private static VillageStyle build(String id){
        var p=new VillageStyle(id);
        switch(base(id)){case "home"->p.home();case "home_2"->p.home2();case "clinic"->p.clinic();case "cartographer"->p.cartographer();
            case "bakery"->p.bakery();case "restaurant"->p.restaurant();case "smithy"->p.smithy();case "carpentry"->p.carpentry();case "mill"->p.mill();case "engineering"->p.engineering();
            case "mine"->p.mine();case "quarry"->p.quarry();case "masonry"->p.masonry();case "warehouse"->p.warehouse(p.level);case "laboratory"->p.laboratory();
            case "farm"->p.farm();case "forester"->p.forester(p.level);case "livestock"->p.livestock(p.level);case "expedition"->p.expedition();case "caravan"->p.caravan();
            case "guard_house"->p.guardHouse();case "barracks"->p.barracks();case "archery"->p.archery();case "wall_tower"->p.wallTower();case "siege_camp"->p.siegeCamp();case "school"->p.school();case "carpentry_annex"->p.carpentryAnnex();case "masonry_annex"->p.masonryAnnex();case "mill_annex"->p.millAnnex();case "kennel_annex"->p.kennelAnnex();case "dog_academy_annex"->p.dogAcademyAnnex();default->throw new IllegalArgumentException(id);}
        p.fillAir();return p;
    }

    // ---------- primitives ----------
    private void set(int x,int y,int z,String s){
        if(x<0||x>=w||z<0||z>=d||y<-8)throw new IllegalStateException(id+": outside the lot at "+x+","+y+","+z+" "+s);
        var c=new Cell(x,y,z);if(roles.get(c)!=null&&roles.get(c)==KIT&&role!=KIT)throw new IllegalStateException(id+": functional cell rebuilt at "+c);
        m.put(c,s);roles.put(c,role);}
    private boolean empty(int x,int y,int z){String s=m.get(new Cell(x,y,z));return s==null||s.equals("air");}
    private void setIfEmpty(int x,int y,int z,String s){if(empty(x,y,z))set(x,y,z,s);}
    private void as(char r){role=r;}
    /** Air in every lot cell above the floor up to the top of the design, so the builder clears the volume it stands in. */
    private void fillAir(){int top=0;for(var c:m.keySet())top=Math.max(top,c.y());
        var was=role;role=OTHER;for(int x=0;x<w;x++)for(int z=0;z<d;z++){
            // AD-131 (check fix 2): the level-VI forester's courtyard holds no cell above its ground — no air either — so no builder, repair or
            // relocation ever clears the grove's trees.
            if(base(id).equals("forester")&&level>=6&&forestCourtyard(x,z))continue;
            for(int y=1;y<=top+1;y++){var c=new Cell(x,y,z);if(!m.containsKey(c)){m.put(c,"air");roles.put(c,OTHER);}}}role=was;}
    static String stair(String b,String facing,boolean top){return b+"[facing="+facing+",half="+(top?"top":"bottom")+",shape=straight]";}
    static String log(String b,char axis){return b+"[axis="+axis+"]";}
    /** AD-144 (owner 2026-09-23): a window is the framed window of a wood (AD-142) — full to building rules, so it needs no joining and never
     *  leaves a gap by a door the way a glass pane did. Its axis is the line of the wall it stands in. */
    static String window(String wood,boolean alongX){return org.villageastra.domain.FramedWindows.id(wood)+"[axis="+(alongX?"x":"z")+"]";}
    /** The design's window in a wall along x (or z): framed in its frame wood — dark oak, the village's timber — or in spruce in a boarded
     *  (spruce) infill and wherever a design sets its frame to spruce (a spruce log cabin, the mill's spruce body). */
    private String pane(boolean alongX){return window(fill.startsWith("spruce")?"spruce":frame,alongX);}
    static String hung(){return "lantern[hanging=true]";}
    private static int hash(int x,int y,int z){int v=x*73856093^y*19349663^z*83492791;v^=v>>>13;v*=0x5bd1e995;v^=v>>>15;return (v&0x7fffffff)%100;}
    /** Village masonry (workshop blocks only): 2x2 clusters, cobble-heavy at the foot, dressed above, a polished andesite course every fifth row. */
    static String mas(int x,int y,int z,boolean cobble){
        // Round 1 review ("the cobble patches look random"): rubble only in the plinth course, dressed stone brick above it, a polished andesite
        // string course every fifth row.
        String s;
        if(y>0&&y%5==0)s="polished_andesite";
        else if(y<=1)s="cobblestone";
        else s="stone_bricks";
        return !cobble&&s.equals("cobblestone")?"stone_bricks":s;}

    /** One framed storey on one face. Rows ys..yp: a sill at ys (if {@code sill}), posts between, the plate at yp. Two-plane walls keep the frame in
     *  the outer plane with air between its members and the plaster one block in (castle {@code storey}); one-plane walls hold plaster and panes in
     *  the frame's own plane. {@code in} is the step from the outer plane into the house. Pattern, one char per cell from a0: P post, S plaster,
     *  W window (two panes from wy), T an open shutter beside it (two-plane walls), w window set one block in, D doorway (door or lane, lintel over it), O left open, C left alone (chimney),
     *  1/2 brace falling from the low-side post (top / second post row), 4/3 the same from the high side, x X y a St Andrew's cross in three cells.
     *  A plinth block (or null) goes under the storey in y=1. */
    private void face(boolean alongX,int outer,int in,boolean two,int a0,String pattern,int ys,int yp,boolean sill,int wy,String plinth){
        int inner=two?outer+in:outer,n=pattern.length();char ax=alongX?'x':'z';
        String lo=alongX?"west":"north",hi=alongX?"east":"south",beam=log(BEAM,ax);
        int top=yp-1,second=yp-2,first=sill?ys+1:ys;
        for(int i=0;i<n;i++){char c=pattern.charAt(i);if(c=='C'||c=='O')continue;int a=a0+i;boolean end=i==0||i==n-1;
            int ox=alongX?a:outer,oz=alongX?outer:a,ix=alongX?a:inner,iz=alongX?inner:a;
            if(plinth!=null){as(PLINTH);if(c!='D'){set(ox,1,oz,plinth);if(two&&!end)set(ix,1,iz,plinth);}else if(two)set(ix,1,iz,"air");}
            for(int y=ys;y<=yp;y++){
                boolean win=(c=='W'||c=='w')&&(y==wy||y==wy+1);String fill;
                if(two)fill="air";else fill=c=='W'&&win?pane(alongX):c=='w'&&win?"air":this.fill;
                String s;char r=FRAME;
                if(y==yp)s=beam;
                else if(c=='D'&&y<=2){if(!two&&y<=2)continue;s="air";r=OTHER;}
                else if(c=='D'&&y==3)s=beam;
                else if(y==ys&&sill)s=beam;
                else if(c=='P')s=log(POST,'y');
                else if(c=='1'&&y==top||c=='2'&&y==second)s=stair("dark_oak_stairs",lo,true);
                else if(c=='4'&&y==top||c=='3'&&y==second)s=stair("dark_oak_stairs",hi,true);
                else if(c=='x'&&y==top)s=stair("dark_oak_stairs",lo,true);
                else if(c=='x'&&y==first)s=stair("dark_oak_stairs",lo,false);
                else if(c=='y'&&y==top)s=stair("dark_oak_stairs",hi,true);
                else if(c=='y'&&y==first)s=stair("dark_oak_stairs",hi,false);
                else if(c=='X'&&y==(first+top)/2)s=log(POST,'y');
                else if(c=='T'&&(y==wy||y==wy+1)){s="spruce_trapdoor[facing="+(alongX?(in>0?"north":"south"):(in>0?"west":"east"))+",half=bottom,open=true,powered=false,waterlogged=false]";r=OTHER;}
                else{s=fill;r=s.equals(this.fill)?INFILL:OTHER;}
                as(r);set(ox,y,oz,s);
                // Round 2 review ("ragged L-shaped holes in the infill"): a brace stair in a one-plane wall shows the room through the notch
                // under it, so plaster stands behind it wherever that leaves two free blocks over the floor — the notch reads as cream.
                if(!two&&"1234xy".indexOf(c)>=0&&s.startsWith("dark_oak_stairs"))back(alongX?a:outer+in,y,alongX?outer+in:a);
                if(c=='w'&&win){as(OTHER);set(alongX?a:outer+in,y,alongX?outer+in:a,pane(alongX));}
                // Round 1 review: a window set one block in gets a stair sill in the course under it.
                if(c=='w'&&!two&&y==wy-1&&y>ys&&y<yp){as(FRAME);set(ox,y,oz,stair("spruce_stairs",alongX?(in>0?"south":"north"):(in>0?"east":"west"),true));}
                // A shutter in a one-plane wall lies against plaster set one block in behind it.
                if(c=='T'&&!two&&(y==wy||y==wy+1)){as(INFILL);set(alongX?a:outer+in,y,alongX?outer+in:a,this.fill);}
                if(!two||end)continue;
                // Inner plane: plaster, panes in window cells, a lane and a lintel in the doorway; its lowest row does not replace a plate below.
                String t;char q=INFILL;
                if(c=='D'&&y<=2){t="air";q=OTHER;}
                else if(c=='D'&&y==3){t=beam;q=FRAME;}
                else if(c=='W'&&win){t=pane(alongX);q=OTHER;}
                else t=this.fill;
                as(q);if(y==ys)setIfEmpty(ix,y,iz,t);else set(ix,y,iz,t);}}
    }
    /** Plaster behind a brace (inside the lot), when the floor under that cell is at least three blocks down. */
    private void back(int x,int y,int z){
        if(x<0||x>=w||z<0||z>=d||!empty(x,y,z))return;
        int f=y-1;while(f>=0&&empty(x,f,z))f--;
        if(y-f<3)return;var was=role;as(INFILL);set(x,y,z,fill);as(was);}
    /** Framed gable in the plane p from y0 up to under the roof, whose stair at a stands at h(a) (the ridge column holds tiles below its cap): plaster, a
     *  king post in the ridge column, a collar beam in row `collar`, panes at the listed {a,y}. */
    private void gable(boolean alongX,int p,int a0,int a1,int y0,IntUnaryOperator h,int ridge,int collar,int[]... panes){
        for(int a=a0;a<=a1;a++){int topY=h.applyAsInt(a)-1-(a==ridge?1:0);
            for(int y=y0;y<=topY;y++){int x=alongX?a:p,z=alongX?p:a;String s;char r;
                if(a==ridge){s=log(POST,'y');r=FRAME;}else if(y==collar){s=log(BEAM,alongX?'x':'z');r=FRAME;}else{s=fill;r=INFILL;}
                for(int[] q:panes)if(q[0]==a&&q[1]==y){s=pane(alongX);r=OTHER;}
                as(r);set(x,y,z,s);}}
    }
    /** 45-degree gable roof over a0..a1 (across the ridge) by b0..b1 (along it); the ridge column c holds tiles under a cap slab, the other stairs face it. */
    private void roof(boolean ridgeAlongZ,int a0,int a1,int b0,int b1,int eave,java.util.function.BiPredicate<Integer,Integer> skip){
        // An even span has no ridge column: its two middle stairs meet back to back.
        int c=(a0+a1)/2;boolean even=((a1-a0)&1)==1;as(ROOF);
        for(int a=a0;a<=a1;a++){int h=eave+Math.min(a-a0,a1-a);
            for(int b=b0;b<=b1;b++){int x=ridgeAlongZ?a:b,z=ridgeAlongZ?b:a;if(skip.test(x,z))continue;
                if(a==c&&!even){set(x,h-1,z,rf);set(x,h,z,rc);}
                else set(x,h,z,stair(rs,(even?a<=c:a<c)?(ridgeAlongZ?"east":"south"):(ridgeAlongZ?"west":"north"),false));}}
    }
    /** Steep (2:1) gable roof over an odd span a0..a1 by b0..b1: the eave stair at `eave`, then in each column a tile under a stair two blocks higher
     *  than the column outside it, the ridge column tiles under a cap. {@link #steep} gives the lowest roof cell of a column for {@link #gable}. */
    private void roof2(boolean ridgeAlongZ,int a0,int a1,int b0,int b1,int eave,java.util.function.BiPredicate<Integer,Integer> skip){
        int c=(a0+a1)/2;as(ROOF);
        for(int a=a0;a<=a1;a++){int k=Math.min(a-a0,a1-a);String f=a<c?(ridgeAlongZ?"east":"south"):(ridgeAlongZ?"west":"north");
            for(int b=b0;b<=b1;b++){int x=ridgeAlongZ?a:b,z=ridgeAlongZ?b:a;if(skip.test(x,z))continue;
                if(a==c){set(x,eave+2*k-1,z,rf);set(x,eave+2*k,z,rc);}
                else if(k==0)set(x,eave,z,stair(rs,f,false));
                else{set(x,eave+2*k-1,z,rf);set(x,eave+2*k,z,stair(rs,f,false));}}}
    }
    /** The lowest roof cell of column a under {@link #roof2} (the ridge column one higher, as {@link #gable} expects of a ridge). */
    private static IntUnaryOperator steep(int a0,int a1,int eave){int c=(a0+a1)/2;return a->{int k=Math.min(a-a0,a1-a);return k==0?eave:a==c?eave+2*k:eave+2*k-1;};}
    /** A cross gable's roof from the door face (z=0) back into a main roof whose stair at z stands at mainH(z): ridge along z at the middle of x0..x1. */
    private void crossRoof(int x0,int x1,int eave,IntUnaryOperator mainH){
        int c=(x0+x1)/2;as(ROOF);
        for(int x=x0;x<=x1;x++){int h=eave+Math.min(x-x0,x1-x);
            for(int z=0;z<d&&mainH.applyAsInt(z)<h;z++){
                if(x==c){set(x,h-1,z,rf);set(x,h,z,rc);}else set(x,h,z,stair(rs,x<c?"east":"west",false));}}
    }
    /** Upside-down dark oak stairs under the verge of a gable end, facing out: the barge board. */
    private void verge(boolean alongX,int p,int y,int a,String facing){as(FRAME);set(alongX?a:p,y,alongX?p:a,stair(TRIM,facing,true));backIn(alongX,p,y,a);}
    /** Round 2 review ("holes under the barge boards"): plaster one block in behind a barge board in a flush gable, so its notch shows cream, not the attic. */
    private void backIn(boolean alongX,int p,int y,int a){int step=2*p<(alongX?d:w)-1?1:-1;int x=alongX?a:p+step,z=alongX?p+step:a;var was=role;back(x,y,z);as(was);}
    /** A closed gable's barge board in its own plane p: the top cell of every column under the verge (the ridge column skipped) in spruce planks,
     *  so the triangle reads as one edged panel, not a sawtooth of stairs over a recess (AD-122 review: "gable ends are ragged"). */
    private void barge(boolean alongX,int p,int a0,int a1,int ridge,IntUnaryOperator h){
        // Round 1 review ("ragged roof ends"): the barge board is a rake of upside-down stairs under the end course, not planks, so the verge reads
        // as one smooth 45-degree band.
        as(FRAME);for(int a=a0;a<=a1;a++){if(a==ridge||2*a==a0+a1)continue;int y=h.applyAsInt(a)-1,x=alongX?a:p,z=alongX?p:a;boolean low=2*a<a0+a1;
            set(x,y,z,stair(TRIM,alongX?(low?"west":"east"):(low?"north":"south"),true));backIn(alongX,p,y,a);as(FRAME);}}
    /** Rake under the end course of a 45-degree roof over a0..a1 in the plane p: an upside-down dark oak stair under every stair of the course,
     *  facing away from the ridge (the ridge column of an odd span skipped). Over a flush gable it takes the gable's top cells; under an overhanging
     *  end course it fills the air, so the overhang has a soffit and the verge a clean edge. The eave columns only where nothing stands. */
    private void rake(boolean alongX,int p,int a0,int a1,IntUnaryOperator h){
        as(FRAME);for(int a=a0;a<=a1;a++){int y=h.applyAsInt(a)-1,x=alongX?a:p,z=alongX?p:a;
            // The apex under the ridge tile: a spruce block closes the gap between the two rakes.
            if(2*a==a0+a1){setIfEmpty(x,y-1,z,"spruce_planks");continue;}
            // Round 2 review ("the roof sags out of line at the gable"): no rake under the eave stairs at the ends, so the end course runs
            // straight out to the corner; the rake itself in spruce, a barge board lighter than the dark oak shingle it edges.
            if(a==a0||a==a1)continue;
            set(x,y,z,stair(TRIM,alongX?(2*a<a0+a1?"west":"east"):(2*a<a0+a1?"north":"south"),true));}}
    /** A pent roof (pentice) of roof stairs along the ledge in front of a set-in wall, facing the wall, where nothing stands. */
    private void pentice(boolean alongX,int p,int a0,int a1,int y,String facing){
        as(ROOF);for(int a=a0;a<=a1;a++)setIfEmpty(alongX?a:p,y,alongX?p:a,stair(rs,facing,false));}
    private static String trapdoor(String wood,String facing,boolean top,boolean open){return wood+"_trapdoor[facing="+facing+",half="+(top?"top":"bottom")+",open="+open+",powered=false,waterlogged=false]";}
    private static String fence(String wood,boolean e,boolean n,boolean s,boolean w){return wood+"_fence[east="+e+",north="+n+",south="+s+",waterlogged=false,west="+w+"]";}
    private static String wallPost(boolean e,boolean n,boolean s,boolean w){return "stone_brick_wall[east="+(e?"low":"none")+",north="+(n?"low":"none")+",south="+(s?"low":"none")+",up=true,waterlogged=false,west="+(w?"low":"none")+"]";}
    /** A stone chimney stack from y0 to top with a slab cap. */
    private void chimney(int x,int z,int y0,int top,boolean cobble){as(STONE);for(int y=y0;y<=top;y++)set(x,y,z,mas(x,y,z,cobble));set(x,top+1,z,"stone_brick_slab[type=bottom]");}
    private void beamRun(boolean alongX,int y,int fixed,int a0,int a1,String block){as(FRAME);for(int a=a0;a<=a1;a++)set(alongX?a:fixed,y,alongX?fixed:a,log(block,alongX?'x':'z'));}
    /** probe-fix-01: a lantern hung in the lintel cell over a door leaves a pocket two blocks high on the plinth beside the door (under the deck at
     *  y4), where a resident jumping the jamb got wedged with its head under the lantern for good (the builder of the starter home stood there for
     *  half an hour). The lintel over a door is always a beam; the lanterns hang in front of it. */
    private void door(int x,int z){as(KIT);set(x,1,z,"oak_door[facing=south,half=lower,hinge=left,open=false]");set(x,2,z,"oak_door[facing=south,half=upper,hinge=left,open=false]");
        if(hung().equals(m.get(new Cell(x,3,z)))){as(FRAME);set(x,3,z,log(BEAM,'x'));}}
    private void bed(int x,int z){as(KIT);set(x,1,z,"white_bed[facing=south,part=foot]");set(x,1,z+1,"white_bed[facing=south,part=head]");}
    private void lanternPost(int x,int z){as(OTHER);set(x,1,z,"dark_oak_fence");set(x,2,z,"dark_oak_fence");set(x,3,z,"lantern[hanging=false]");}

    // ---------- home: 7x7 two-storey town cottage, gable to the street ----------
    /** AD-122 review: a narrow two-storey timber house with its tall gable to the street, the upper storey on the plank deck at y4 (the house's solid
     *  ceiling layer for BuildingIntegrity), posts at the corners and on the door axis only, braced end panels, a closed gable flush with the wall
     *  (king post, collar, attic window) edged by barge boards under the verge, a stone chimney up the back. The floor stays 49 cobblestone
     *  (the catalogue test counts the 7x7 foundation); the plinth is stone brick; every interior floor cell stays free for equipment. */
    private void home(){
        // Round 1 review: a two-storey town house under a true 45-degree stair roof, its street gable one block in under the overhanging end
        // course (a raked soffit under it), the upper storey jettied one block over the ground storey on stair brackets, the door set in under
        // the jetty (the lane (3,1,0..1) open), upper windows set one block in on stair sills between open shutters.
        as(DECK);for(int x=0;x<7;x++)for(int z=0;z<7;z++)set(x,0,z,"cobblestone");
        IntUnaryOperator h=x->10+Math.min(x,6-x);
        roof(true,0,6,0,6,10,(x,z)->x==5&&z==6);
        // Ground storey: the front two blocks in, sides and back on the lot edge.
        // AD-143 (owner rule 2026-09-23): posts either side of the door — no glass pane beside it; the windows move out to the corners.
        face(true,2,1,false,0,"PWPDPWP",2,4,false,2,"stone_bricks");
        face(false,0,1,false,2,"PWSWP",2,4,false,2,"stone_bricks");
        face(false,6,-1,false,2,"PWSWP",2,4,false,2,"stone_bricks");
        // Design review 2026-09-24 ("the back front is worse than the street one"): a window either side of the back post on both storeys,
        // aligned with the street windows, the stack beside the east one.
        face(true,6,-1,false,0,"PSWPWCP",2,4,false,2,"stone_bricks");
        // The jetty: its sill one block over the ground storey on brackets, the deck (the house's solid ceiling layer at y4).
        beamRun(true,4,1,0,6,BEAM);as(DECK);for(int x=1;x<=5;x++)for(int z=2;z<=5;z++)set(x,4,z,PLANKS);
        for(int x:new int[]{0,2,4,6})brace(x,3,1,"south");
        // Upper storey, four courses: knee braces, windows set one block in on sills with shutters (the front), panes in the plane elsewhere.
        face(true,1,1,false,0,"PTwPwTP",5,9,false,6,null);
        face(false,0,1,false,1,"P1WPWP",5,9,false,6,null);
        face(false,6,-1,false,1,"P1WPWP",5,9,false,6,null);
        face(true,6,-1,false,0,"P1WPWCP",5,9,false,6,null);
        // Gables: the street one one block in under the overhanging end course with its raked soffit, the back one closed in the wall plane.
        gable(true,1,1,5,10,h,3,-1);rake(true,0,0,6,h);
        gable(true,6,1,5,10,h,3,-1,new int[]{3,10});barge(true,6,1,5,3,h);
        beamRun(true,10,3,1,5,POST);
        chimney(5,6,1,15,false);
        // Before the house: a table with a chair under the jetty (AD-143: in the cells of the old stair bench), a barrel and straw (the house
        // keeps its one potted flower inside).
        as(OTHER);set(3,9,3,hung());set(3,3,2,hung());set(3,3,1,hung());set(5,1,1,"barrel[facing=up,open=false]");set(6,1,1,"hay_block[axis=y]");
        set(1,1,1,table(org.villageastra.domain.Furniture.HOME_WOOD,false,false,false,false));set(0,1,1,chair(org.villageastra.domain.Furniture.HOME_WOOD,"east"));set(3,1,0,"air");
        door(3,2);bed(2,3);bed(4,3);as(KIT);set(1,1,4,CHEST);set(5,1,5,"crafting_table");set(5,2,5,"potted_poppy");
        as(OTHER);for(var e:HOME_FURNITURE.entrySet())set(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue());
    }
    /** Design review 2026-09-24 (lateFurniture): a lamp over the aisle under the deck and a pot in the free corner by the east bed; the upper
     *  storey, which no level-I kit uses, a reading room — a window seat under each set-in street window between two bookshelves. The bed
     *  approach (3,1,5) of level III, the chest's side (1,1,3) and the middle of the upper storey (3,5,3) stay free — the level analysis keeps
     *  that cell for a core (a house has none), and furniture there would move every kit upstairs. */
    private static final Map<Cell,String> HOME_FURNITURE=Map.of(new Cell(3,3,4),hung(),new Cell(5,1,3),"potted_poppy",
        new Cell(1,5,2),"bookshelf",new Cell(5,5,2),"bookshelf",new Cell(2,5,2),stair("spruce_stairs","north",false),new Cell(4,5,2),stair("spruce_stairs","north",false));
    static{late("home",HOME_FURNITURE.keySet());}

    // ---------- home_2: 11x9 two-storey house, eaves to the street ----------
    /** Ground floor set one block back under a jettied upper storey on brackets, its plank deck at y4 (the house's solid ceiling layer); braces in the
     *  end bays of the upper front and the sides, a cross gable over the door, the chimney up the east gable past the ridge, a straight stair of four
     *  treads along the back wall with three blocks of air over each. */
    private void home2(){
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=9&&z>=2&&z<=7;set(x,0,z,in?PLANKS:z<=1&&x>=4&&x<=6?"stone_bricks":"cobblestone");}
        IntUnaryOperator h=z->10+Math.min(z,8-z),g=x->10+Math.min(x-2,8-x);
        roof(false,0,8,0,10,10,(x,z)->x==10&&z==4);
        crossRoof(2,8,10,h);
        // Design review 2026-09-24: the two main-roof stairs in the valleys of the cross gable in the inner shape the game gives them.
        as(ROOF);set(4,12,2,rs+"[facing=south,half=bottom,shape=inner_left]");set(6,12,2,rs+"[facing=south,half=bottom,shape=inner_right]");
        // Ground storey: front one block in under the jetty, sides and back on the lot edge.
        face(true,1,1,false,0,"P1SWPDPWS4P",2,4,false,2,"cobblestone");
        face(false,0,1,false,1,"PSWPSWSP",2,4,false,2,"cobblestone");
        face(false,10,-1,false,1,"PSWCSWSP",2,4,false,2,"cobblestone");
        face(true,8,-1,false,0,"P1WSPSPSW4P",2,4,false,2,"cobblestone");
        // The porch bay under the jetty holds the door.
        as(PLINTH);set(4,1,0,"cobblestone");set(6,1,0,"cobblestone");
        as(FRAME);for(int y=2;y<=3;y++){set(4,y,0,log(POST,'y'));set(6,y,0,log(POST,'y'));}set(5,3,0,log(BEAM,'x'));
        // Upper floor deck with the stairwell; the street storey jettied on brackets, the sides flush with the ground storey (round 1 review: the
        // band ran round all four sides), windows set one block in between open shutters.
        as(DECK);for(int x=1;x<=9;x++)for(int z=1;z<=7;z++)if(!(z==7&&x>=2&&x<=4))set(x,4,z,PLANKS);
        beamRun(true,4,0,0,10,BEAM);
        face(true,0,1,false,0,"P12PwPwP34P",5,9,false,6,null);
        face(false,0,1,false,0,"P12wPw34P",5,9,false,6,null);
        face(false,10,-1,false,0,"P12wCw34P",5,9,false,6,null);
        face(true,8,-1,false,0,"P12WPSPW34P",5,9,false,6,null);
        for(int x:new int[]{0,2,8,10})brace(x,3,0,"south");
        as(OTHER);for(int x:new int[]{0,1,2,3,7,8,9,10})set(x,1,0,stair("cobblestone_stairs","south",false));
        // Gables one block in over the plates, the end courses overhanging on a rake with a pentice on the ledge; the cross gable over the door.
        gable(false,1,1,7,10,h,4,12,new int[]{3,11},new int[]{5,11});rake(false,0,0,8,h);pentice(false,0,1,7,10,"east");
        gable(false,9,1,7,10,h,4,12,new int[]{3,11},new int[]{5,11});rake(false,10,0,8,h);pentice(false,10,1,7,10,"west");
        gable(true,0,3,7,10,g,5,-1,new int[]{4,10},new int[]{6,10});barge(true,0,3,7,5,g);
        chimney(10,4,1,16,true);
        // The stair: four treads rising east along the back wall, each with three blocks of air over it, the space under them boarded.
        as(OTHER);for(int i=0;i<4;i++){set(2+i,1+i,7,stair("dark_oak_stairs","east",false));for(int y=1;y<1+i;y++)set(2+i,y,7,PLANKS);}
        for(int x=2;x<=4;x++)set(x,5,6,x==2?"dark_oak_fence[east=true]":x==3?"dark_oak_fence[east=true,west=true]":"dark_oak_fence[west=true]");
        beamRun(true,10,4,2,8,POST);as(OTHER);set(5,3,2,hung());set(5,9,4,hung());set(3,3,0,hung());set(7,3,0,hung());
        door(5,0);for(int x:new int[]{2,4,6,8})bed(x,3);as(KIT);set(1,1,4,CHEST);set(9,1,7,"crafting_table");set(9,2,7,"potted_poppy");
        // AD-143: the family table in the middle of the upper storey, off the way from the stair to the beds: a table with a chair either side.
        // Every free cell of the ground floor is a kit's by level VI (LevelArchitecture), so the table stands upstairs.
        as(OTHER);for(var c:HOME_2_FURNITURE)set(c.x(),c.y(),c.z(),c.x()==HOME_2_TABLE.x()?table(org.villageastra.domain.Furniture.HOME_2_WOOD,false,false,false,false)
            :chair(org.villageastra.domain.Furniture.HOME_2_WOOD,c.x()<HOME_2_TABLE.x()?"east":"west"));
        as(OTHER);for(var e:HOME_2_DECOR.entrySet())set(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue());
    }
    /** Design review 2026-09-24 (lateFurniture). The street windows of the upper storey are set one block in between posts, so their panes
     *  touched the house only by an edge: plaster reveals behind the posts (x 3, 5, 7) at the window rows and a plaster lintel over the
     *  windows up to the roof (else a one-block ledge ran along the wall top). Upstairs bookshelves in the free
     *  cells against the side walls between the windows and a pot in the east corner; downstairs two lamps under the deck over the way to the
     *  stair. The way up, the level beds, their approaches and the spawn cells (2|4,1,2) stay free. */
    private static final Map<Cell,String> HOME_2_DECOR=decorAndShell(Map.of(new Cell(1,5,2),"bookshelf",new Cell(1,5,4),"bookshelf",new Cell(9,5,2),"bookshelf",
        new Cell(9,5,4),"bookshelf",new Cell(9,5,1),"potted_poppy",new Cell(3,3,5),hung(),new Cell(7,3,5),hung()),new int[][]{{3,6,1},{3,7,1},{5,6,1},{5,7,1},{7,6,1},{7,7,1},{2,8,1},{3,8,1},{4,8,1},{5,8,1},{6,8,1},{7,8,1},{8,8,1},
        {1,9,1},{2,9,1},{3,9,1},{4,9,1},{5,9,1},{6,9,1},{7,9,1},{8,9,1},{9,9,1}},PLASTER);
    static{late("home_2",HOME_2_DECOR.keySet());}
    /** Furniture cells plus the given cells in one block (a shell patch kept out of the level analysis like the furniture). */
    private static Map<Cell,String> decorAndShell(Map<Cell,String> furniture,int[][] cells,String block){var out=new LinkedHashMap<>(furniture);for(var c:cells)out.put(new Cell(c[0],c[1],c[2]),block);return Collections.unmodifiableMap(out);}
    /** AD-143: the big house's table and the chairs west and east of it (lateFurniture). */
    private static final Cell HOME_2_TABLE=new Cell(5,5,4);
    private static final Set<Cell> HOME_2_FURNITURE=Set.of(HOME_2_TABLE,new Cell(4,5,4),new Cell(6,5,4));
    static{late("home_2",HOME_2_FURNITURE);}

    // ---------- design review 2026-09-24, group C (school, clinic, cartographer, laboratory, expedition, caravan) ----------
    /** Furniture and decor as data, {x,y,z,state} per cell. Every cell was air in every level before it, so the set goes to lateFurniture. */
    private static Set<Cell> decorCellsC(Object[][] d){var out=new HashSet<Cell>();for(var o:d)out.add(new Cell((int)o[0],(int)o[1],(int)o[2]));return Set.copyOf(out);}
    private void decorC(Object[][] d){var was=role;as(OTHER);for(var o:d)set((int)o[0],(int)o[1],(int)o[2],(String)o[3]);as(was);}
    /** The shape the game gives a roof stair where two courses meet (valley or hip), set explicitly: designs are placed without neighbour updates. */
    private void cornerC(int x,int y,int z,String shape){var c=new Cell(x,y,z);var s=m.get(c);
        if(s==null||!s.contains("shape=straight"))throw new IllegalStateException(id+": no straight stair at "+c);
        var was=role;as(roles.get(c));set(x,y,z,s.replace("shape=straight","shape="+shape));as(was);}

    // ---------- school: 11x11 schoolhouse, eaves to the street ----------
    /** Round 1 review (the flat terracotta roof and the pale walls broke the set): one classroom hall in the village frame — a cobble plinth,
     *  dark oak posts and braces, cream plaster, deep-set windows on stair sills — under a 45-degree dark oak roof that overhangs the street and
     *  the back (walls one block in) and the east gable (a raked soffit under the end course); an entrance porch with its own small gable on two
     *  posts, and on the ridge over the door an open bell-cote with a lantern under a little hip. The lecterns (2|8,1,3|6), the stock chest and
     *  the book wall stay where the teacher and the classes use them. */
    private void school(){
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<11;z++){boolean in=x>=1&&x<=8&&z>=2&&z<=8;set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=z->6+Math.min(z,10-z),g=x->6+Math.min(x-2,8-x);
        roof(false,0,10,0,10,6,(x,z)->false);
        crossRoof(2,8,6,h);cornerC(4,8,2,"inner_left");cornerC(6,8,2,"inner_right");
        face(true,1,1,false,0,"P1wPwDwPw4P",2,5,false,3,"cobblestone");
        face(true,9,-1,false,0,"PSWSPSPSWSP",2,5,false,3,"cobblestone");
        face(false,0,1,false,2,"PwSPSwP",2,5,false,3,"cobblestone");
        face(false,9,-1,false,2,"PWSPSWP",2,5,false,3,"cobblestone");
        // The gables: the west one closed in the wall plane, the east one one block in under the overhanging end course.
        // Design review 2026-09-24: no pane in the west king post (the east gable has none; a pane between two post lengths read as a hole).
        gable(false,0,1,9,6,h,5,9,new int[]{3,7},new int[]{7,7});barge(false,0,1,9,5,h);
        gable(false,9,1,9,6,h,5,9,new int[]{3,7},new int[]{7,7});rake(false,10,0,10,h);
        // The porch: two posts on pads carrying a tie beam under its gable, a step and lamps.
        as(PLINTH);set(3,1,0,"cobblestone");set(7,1,0,"cobblestone");posts(3,0,2,4);posts(7,0,2,4);beamRun(true,5,0,3,7,BEAM);
        brace(4,4,0,"west");brace(6,4,0,"east");
        gable(true,0,3,7,6,g,5,-1,new int[]{4,7},new int[]{6,7});barge(true,0,3,7,5,g);
        as(OTHER);set(5,4,0,hung());set(5,4,1,"air");set(5,0,0,"stone_bricks");
        // Tie beams across the classroom, lamps under them; the book wall along the back.
        // Design review 2026-09-24: the beams run on over the front and back plates (they touched them only at an edge), the lamp over the door
        // lane hangs on a chain from the roof (SCHOOL_DECOR).
        for(int x:new int[]{3,7})beamRun(false,6,x,2,8,POST);
        as(OTHER);set(3,5,5,hung());set(7,5,5,hung());set(5,5,3,hung());
        // The deep-set front and west windows: their panes stood loose one block in, held at an edge; stripped timber jambs (SCHOOL_DECOR) line
        // them on the inside, the brace's backing beside the corner pane turns to a jamb too, a spruce board runs down the aisle.
        as(FRAME);set(1,4,2,log(BEAM,'y'));as(DECK);for(int z=2;z<=7;z++)set(5,0,z,"spruce_planks");
        // The bell-cote over the door: a plank deck on the ridge, four posts, a lantern hung in it, a little hip roof.
        as(FRAME);for(int x=4;x<=6;x++)for(int z=4;z<=6;z++)set(x,11,z,PLANKS);
        as(OTHER);for(int x:new int[]{4,6})for(int z:new int[]{4,6})for(int y=12;y<=13;y++)set(x,y,z,log(POST,'y'));
        // Round 2 review: the bell-cote under a hip that overhangs its posts by one block, a finial on the apex.
        hip45(3,3,7,7,14);as(OTHER);set(5,13,5,hung());
        for(int y=12;y<=13;y++){set(5,y,4,trapdoor("spruce","north",false,true));set(5,y,6,trapdoor("spruce","south",false,true));set(4,y,5,trapdoor("spruce","west",false,true));set(6,y,5,trapdoor("spruce","east",false,true));}
        set(5,12,5,"air");as(FRAME);set(5,14,5,PLANKS);as(OTHER);set(5,16,5,"stone_brick_wall[up=true]");
        door(5,1);as(KIT);set(1,1,4,CHEST);
        for(int[] c:new int[][]{{2,3},{2,6},{8,3},{8,6}})set(c[0],1,c[1],"lectern[facing=north,has_book=false,powered=false]");
        for(int x=3;x<=7;x++)for(int y=1;y<=2;y++)set(x,y,8,"bookshelf");
        decorC(SCHOOL_DECOR);
    }
    /** Design review 2026-09-24: the school's late cells. The classroom: a reading table with a pot and a pupil's chair before the book wall, a
     *  blackboard on the book wall (the four lecterns face it; the cells over its ends stay air, the level lamp's column), stripped timber jambs
     *  beside the deep-set windows, the tie beams' ends on the plates, the door lane's lamp on a chain. Under the eaves: a table and chairs (east),
     *  a bench and firewood (back). Every cell stays out of the kits' floor cells and of the ways to them (lateFurniture). */
    private static final Object[][] SCHOOL_DECOR;
    static{var d=new ArrayList<Object[]>();String lin=log(BEAM,'y');
        // The jambs rise to the plate, so no ledge is left over the panes.
        for(int y=3;y<=5;y++){for(int x:new int[]{3,5,7})d.add(new Object[]{x,y,2,lin});for(int z:new int[]{4,6,8})d.add(new Object[]{1,y,z,lin});}
        d.add(new Object[]{1,3,2,lin});d.add(new Object[]{1,5,2,lin});
        for(int x:new int[]{3,7})for(int z:new int[]{1,9})d.add(new Object[]{x,6,z,log(POST,'z')});
        for(int y=6;y<=8;y++)d.add(new Object[]{5,y,3,"chain[axis=y]"});
        String sp="spruce";
        d.addAll(List.of(new Object[]{5,1,6,table(sp,true,false,false,false)},new Object[]{6,1,6,table(sp,false,false,false,true)},new Object[]{6,1,5,chair(sp,"south")},
            new Object[]{6,2,6,"potted_poppy"},
            new Object[]{10,1,4,chair(sp,"south")},new Object[]{10,1,5,table(sp,false,false,false,false)},new Object[]{10,1,6,chair(sp,"north")},
            new Object[]{3,1,10,chair(sp,"south")},new Object[]{4,1,10,chair(sp,"south")},new Object[]{5,1,10,"potted_oak_sapling"},
            new Object[]{7,1,10,"spruce_log[axis=x]"},new Object[]{8,1,10,"spruce_log[axis=x]"},new Object[]{9,1,10,"spruce_log[axis=x]"},new Object[]{8,2,10,"spruce_log[axis=x]"}));
        for(int x=4;x<=6;x++)for(int y=3;y<=4;y++)d.add(new Object[]{x,y,8,"dark_oak_planks"});
        SCHOOL_DECOR=d.toArray(new Object[0][]);}
    static{late("school",decorCellsC(SCHOOL_DECOR));}

    // ---------- clinic: 11x9 infirmary, eaves to the street ----------
    /** Light, plaster-heavy hall: eaves overhang both long sides (walls one block in), a two-plane front with braces in its end bays, an entrance
     *  bay with its own gable carrying a pale birch cross on dark boards, lantern posts at the front corners, a chimney at the back. */
    private void clinic(){
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=9&&z>=3&&z<=6,porch=x>=4&&x<=6&&z<=2;set(x,0,z,in?PLANKS:porch?"stone_bricks":"cobblestone");}
        IntUnaryOperator h=z->5+Math.min(z,8-z);
        roof(false,0,8,0,10,5,(x,z)->x==2&&z==7||z==0&&x>=3&&x<=7);
        crossRoof(3,7,7,h);cornerC(4,8,3,"inner_left");cornerC(6,8,3,"inner_right");
        face(true,1,1,true,0,"P12POOOP34P",2,5,true,3,"cobblestone");
        beamRun(true,5,1,4,6,BEAM);as(INFILL);for(int x=4;x<=6;x++)set(x,5,2,PLASTER);
        face(true,0,1,false,3,"PWDWP",2,5,false,3,"cobblestone");
        face(false,0,1,false,1,"PSWPWSP",2,5,false,3,"cobblestone");
        face(false,10,-1,false,1,"PSWPWSP",2,5,false,3,"cobblestone");
        face(true,7,-1,false,0,"PSCSPWPSWSP",2,5,false,3,"cobblestone");
        // The entrance gable (round 1 review: the pale cross read as painted on): a red brick cross filling the gable field in its raked frame.
        IntUnaryOperator g=x->7+Math.min(x-3,7-x);
        gable(true,0,3,7,6,g,5,-1);barge(true,0,3,7,5,g);
        // Round 2 review ("the brick cross reads as damage on the slope"): the entrance gable field stays plain plaster.
        // Gables one block in over the plates; the end courses overhang them on a rake, a pentice on the ledge.
        gable(false,1,1,7,6,h,4,-1,new int[]{3,6},new int[]{5,6});rake(false,0,0,8,h);pentice(false,0,1,7,6,"east");
        gable(false,9,1,7,6,h,4,-1,new int[]{3,6},new int[]{5,6});rake(false,10,0,8,h);pentice(false,10,1,7,6,"west");
        as(FRAME);for(int x=0;x<=10;x++){if(x<3||x>7)set(x,4,0,"dark_oak_slab[type=top]");set(x,4,8,"dark_oak_slab[type=top]");}
        as(OTHER);for(int x=0;x<=10;x++)set(x,1,8,stair("cobblestone_stairs","north",false));
        // The herb beds either side of the porch: potted herbs and a composter each side.
        set(1,1,0,"potted_poppy");set(9,1,0,"potted_dandelion");for(int x:new int[]{2,8})set(x,1,0,"composter");
        lanternPost(0,0);lanternPost(10,0);
        // Design review 2026-09-24: the lamp posts join the plinth and the corner post behind them (lanternPost leaves a fence unjoined).
        as(OTHER);for(int x:new int[]{0,10})for(int y=1;y<=2;y++)set(x,y,0,fence("dark_oak",false,false,true,false));
        chimney(2,7,1,10,true);
        beamRun(false,5,3,3,6,POST);beamRun(false,5,7,3,6,POST);as(OTHER);set(3,4,4,hung());set(7,4,4,hung());set(5,4,1,hung());
        door(5,0);for(int x:new int[]{2,5,8})bed(x,5);as(KIT);set(1,1,4,CHEST);set(9,1,6,"crafting_table");
        // Design review 2026-09-24: a birch walk along the foot of the beds from the entrance.
        as(DECK);set(5,0,3,"birch_planks");for(int x=2;x<=8;x++)set(x,0,4,"birch_planks");
        decorC(CLINIC_DECOR);
    }
    /** Design review 2026-09-24: the ward's late cells — a medicine table in the corner by the stock, birch night tables with a lamp and a pot
     *  beside the beds (their feet and the stock's east side stay free), a shelf with a pot over each bed three blocks up (two free over the
     *  pillow). (No lamps under the back eave: a top slab gives a hanging lantern no hold, the build could never finish.) */
    private static final Object[][] CLINIC_DECOR={{1,1,3,table("birch",false,false,false,false)},{1,2,3,"potted_poppy"},
        {1,1,5,table("birch",false,false,false,false)},{1,2,5,"lantern[hanging=false]"},{6,1,5,table("birch",false,false,false,false)},{6,2,5,"potted_dandelion"},
        {2,4,6,trapdoor("birch","north",true,false)},{5,4,6,trapdoor("birch","north",true,false)},{8,4,6,trapdoor("birch","north",true,false)},
        {2,5,6,"potted_spruce_sapling"},{5,5,6,"potted_poppy"},{8,5,6,"potted_oak_sapling"}};
    static{late("clinic",decorCellsC(CLINIC_DECOR));}

    // ---------- cartographer: 9x11 house with a corner observatory tower ----------
    /** Narrow house, gable to the street, a two-plane front with knee braces at the door posts and paired windows; its street gable stands in the
     *  frame plane, a block before the plaster (a jettied gable), closed and edged. At the back right corner a square stone observatory tower rises
     *  from the ground past the ridge (AD-122 review: not out of the middle of the roof), windows to every side high up, a slate cap with a spire. */
    private void cartographer(){
        as(DECK);for(int x=0;x<9;x++)for(int z=0;z<11;z++){boolean in=x>=1&&x<=7&&z>=2&&z<=9;set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=x->6+Math.min(x,8-x);
        java.util.function.BiPredicate<Integer,Integer> tower=(x,z)->x>=6&&z>=8;
        roof(true,0,8,0,10,6,(x,z)->tower.test(x,z));
        face(true,0,1,true,0,"PW4PDP1WP",2,5,true,3,"cobblestone");
        face(false,0,1,false,0,"PSTwTPTwTSP",2,5,false,3,"cobblestone");
        face(false,8,-1,false,0,"PSWSPSWSCCC",2,5,false,3,"cobblestone");
        face(true,10,-1,false,0,"PSWSPSCCC",2,5,false,3,"cobblestone");
        // The street gable in the frame plane over the plate, the back one in the wall plane; both closed under the verge.
        gable(true,0,1,7,6,h,4,8,new int[]{3,6},new int[]{5,6},new int[]{3,7},new int[]{5,7});barge(true,0,1,7,4,h);
        as(FRAME);for(int x:new int[]{1,7})set(x,5,0,stair("dark_oak_stairs","north",true));
        gable(true,10,1,5,6,h,4,8,new int[]{3,7});
        // Design review 2026-09-24 ("the back gable's verge is a sawtooth"): barge boards under the back verge as on the street gable, by hand —
        // barge() takes a span centred on its ridge and this gable stops at the tower; their backing plaster is late (CARTOGRAPHER_DECOR).
        for(int x:new int[]{1,2,3,5})verge(true,10,h.applyAsInt(x)-1,x,x<4?"west":"east");
        beamRun(true,5,2,1,7,POST);beamRun(true,5,6,1,5,POST);
        // The tower: masonry from the ground to y13 over the back right corner, a doorway from the room, windows high up to every side, a slate cap.
        as(STONE);for(int y=1;y<=13;y++)for(int x=6;x<=8;x++)for(int z=8;z<=10;z++){
            if(x==7&&z==9){as(OTHER);set(x,y,z,y==7?PLANKS:"air");as(STONE);continue;}
            set(x,y,z,y==13?"polished_andesite":y==1?(hash(x,y,z)<70?"cobblestone":"stone_bricks"):mas(x,y,z,true));}
        as(OTHER);set(7,1,8,"air");set(7,2,8,"air");
        // The lookout storey open to every side under stair arches, a polished andesite course under the cap.
        for(int y=10;y<=11;y++){as(OTHER);set(7,y,8,"air");set(7,y,10,"air");set(6,y,9,"air");set(8,y,9,"air");}
        as(STONE);set(7,12,8,stair("stone_brick_stairs","north",true));set(7,12,10,stair("stone_brick_stairs","south",true));set(6,12,9,stair("stone_brick_stairs","west",true));set(8,12,9,stair("stone_brick_stairs","east",true));
        pane(8,4,9,false);pane(8,5,9,false);
        as(ROOF);for(int x=6;x<=8;x++)for(int z=8;z<=10;z++){if(x==7&&z==9){set(x,14,z,TILES);continue;}
            String f=x==6?"east":x==8?"west":z==8?"south":"north";String shape=x!=7&&z!=9?(x==6?(z==8?",shape=outer_right":",shape=outer_left"):(z==8?",shape=outer_left":",shape=outer_right")):"";
            set(x,14,z,shape.isEmpty()?stair(TILE,f,false):TILE+"[facing="+f+",half=bottom"+shape+"]");}
        as(OTHER);set(7,15,9,"stone_brick_wall[up=true]");set(7,13,9,PLANKS);set(7,12,9,hung());
        set(4,8,1,hung());set(4,4,2,"chain[axis=y]");set(4,3,2,hung());set(7,6,9,hung());
        door(4,0);as(KIT);set(1,1,4,CHEST);set(3,1,3,"cartography_table");for(int x=1;x<=5;x++)for(int y=1;y<=2;y++)set(x,y,9,"bookshelf");
        // Design review 2026-09-24: the corner pane of the two-plane front joins the plaster of the side wall behind it; a spruce board from the
        // door to the chart table.
        as(OTHER);for(int y=3;y<=4;y++)set(1,y,1,pane(true));
        as(DECK);for(int z=2;z<=7;z++)set(4,0,z,"spruce_planks");
        decorC(CARTOGRAPHER_DECOR);
    }
    /** Design review 2026-09-24: the map room's late cells — an oak chart table with a lamp and a chair between the map table and the book wall,
     *  a tall shelf of books by the stock (its east side free), rolled charts in the loft over the entrance, a lamp in the frame bay over the door
     *  (a dark pocket before), and the plaster behind the new barge boards of the back gable. */
    private static final Object[][] CARTOGRAPHER_DECOR={{3,1,6,table("oak",true,false,false,false)},{4,1,6,table("oak",false,false,false,true)},{4,2,6,"lantern[hanging=false]"},
        {3,1,5,chair("oak","south")},{1,1,5,"bookshelf"},{1,2,5,"bookshelf"},{1,6,1,"bookshelf"},{2,6,1,"bookshelf"},{6,6,1,"bookshelf"},{7,6,1,"bookshelf"},
        {4,4,0,hung()},{1,6,9,PLASTER},{2,7,9,PLASTER},{3,8,9,PLASTER},{5,8,9,PLASTER}};
    static{late("cartographer",decorCellsC(CARTOGRAPHER_DECOR));}
    // ---------- batch 2: craft shops with a hearth or a drive. Walls stay on the west and front lot edges: the drive post (1,1,1) and the stock
    // chest (1,1,4) are floor cells inside every workshop, so the overhang comes from gables set one block in and from pent roofs. ----------
    /** Masonry from y0 to y1 along one face (a..b in the other axis): the lowest course is the plinth, the rest the wall levels re-dress. */
    private void stone(boolean alongX,int p,int a0,int a1,int y0,int y1,boolean cobble){
        for(int a=a0;a<=a1;a++)for(int y=y0;y<=y1;y++){int x=alongX?a:p,z=alongX?p:a;as(y==1?PLINTH:INFILL);
            // Quoins at the lot corners: polished andesite on every other course.
            boolean quoin=(x==0||x==w-1)&&(z==0||z==d-1)&&y>1&&y%5!=0&&y%2==0;
            set(x,y,z,quoin?"polished_andesite":mas(x,y,z,cobble));}}
    /** Barge boards under the verge of a gable end in the plane p, from a0 to a1 (the ridge column skipped), facing out on each side of it. */
    private void verges(boolean alongX,int p,int a0,int a1,int ridge,IntUnaryOperator h,String lo,String hi){
        for(int a=a0;a<=a1;a++)if(a!=ridge&&empty(alongX?a:p,h.applyAsInt(a)-1,alongX?p:a))verge(alongX,p,h.applyAsInt(a)-1,a,a<ridge?lo:hi);}
    private void pane(int x,int y,int z,boolean alongX){as(OTHER);set(x,y,z,pane(alongX));}

    // ---------- bakery: 9x9 shop, gable to the street ----------
    /** A timber shop on a cobble plinth: two recessed shop windows with board counters either side of the door, a closed street gable in the wall plane
     *  (king post, collar, a pair of windows, barge boards). Its sign is the bread oven (AD-122 review): a stone brick bump along the west wall with
     *  the fire door of a furnace in its face, under a brick stack that rises up the eave to the height of the ridge (the builders reach its cap from a scaffold inside). */
    private void bakery(){
        // Round 1 review: a shop front set one block in under the overhanging street gable — posts at the corners, a plate, a pentice awning over
        // two wide shop windows with barrels and straw in front — the rake under both verges, and the oven as a brick mass in the west wall with
        // its fire door, the stack rising from it on stepped shoulders.
        as(DECK);for(int x=0;x<9;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=7&&z>=1&&z<=7&&!(z==1&&x>=2&&x<=6);set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=x->5+Math.min(x,8-x);
        roof(true,0,8,0,8,5,(x,z)->x==0&&z>=5&&z<=7||x==1&&z==6);
        // Two cheeks on the street line (the drive post (1,1,1) stays a floor cell behind the west one), the shop front one block in between them.
        face(true,0,1,false,1,"P",2,4,false,2,"cobblestone");face(true,0,1,false,7,"P",2,4,false,2,"cobblestone");
        // Design review 2026-09-24 (DOOR-JAMBS, owner rule 2026-09-23): posts either side of the door — no glass pane beside it; a shop window
        // either side of them.
        face(true,1,1,false,2,"WPDPW",2,4,false,2,"cobblestone");
        face(true,8,-1,false,0,"PSWSPSWSP",2,4,false,2,"cobblestone");
        face(false,0,1,false,0,"P1WSPCCCP",2,4,false,2,"cobblestone");
        face(false,8,-1,false,0,"P1WSPSW4P",2,4,false,2,"cobblestone");
        // The shop porch: the lintel carrying the overhang, the awning over it, the goods.
        beamRun(true,4,0,2,6,BEAM);brace(2,3,0,"west");brace(6,3,0,"east");
        // Design review 2026-09-24: one barrel under the west shop window (two stood in front of the glass), a pot on it.
        as(OTHER);set(2,1,0,"barrel[facing=up,open=false]");set(2,2,0,"potted_poppy");set(3,1,0,"spruce_slab[type=top,waterlogged=false]");set(5,1,0,"spruce_slab[type=top,waterlogged=false]");set(6,1,0,"barrel[facing=up,open=false]");
        beamRun(true,4,4,1,7,POST);
        // Round 2 review ("a cavity at the gable, glass under the stairs"): the street gable closed in the plane of the end course over the shop
        // lintel, a pair of windows and a barge board; the shop front stays one block in under it.
        gable(true,0,1,7,5,h,4,7,new int[]{3,7},new int[]{5,7});barge(true,0,1,7,4,h);
        // Studs either side of the king post frame the gable's windows (level VI dresses them with the other upper posts).
        posts(2,0,5,6);posts(6,0,5,6);
        gable(true,8,1,7,5,h,4,7,new int[]{3,6},new int[]{5,6});barge(true,8,1,7,4,h);
        // The oven: a brick mass in the west wall with the fire door, the stack on stepped shoulders past the ridge; the hearth inside.
        // Round 2 review ("the stack stands a block off the wall, far too tall"): the oven mass in the west wall steps in on brick shoulders to a
        // stack that rises against the wall through the slope to two blocks over the ridge, capped.
        as(STONE);for(int z=5;z<=7;z++)for(int y=1;y<=4;y++)set(0,y,z,z==6&&y==1?"furnace[facing=west,lit=false]":"bricks");
        // Design review 2026-09-24: the shoulders meet the eave stairs behind them in inner corners (the shape the game gives them on an update),
        // which closes the notch between shoulder and roof.
        set(0,5,5,"brick_stairs[facing=south,half=bottom,shape=inner_left]");set(0,5,7,"brick_stairs[facing=north,half=bottom,shape=inner_right]");set(0,5,6,"bricks");
        set(0,6,6,"brick_stairs[facing=east,half=bottom,shape=straight]");
        for(int y=1;y<=11;y++)set(1,y,6,"bricks");set(1,12,6,"brick_slab[type=bottom]");
        as(OTHER);set(4,7,2,hung());set(2,3,4,hung());set(6,3,4,hung());set(4,3,0,hung());
        door(4,1);as(KIT);set(1,1,4,CHEST);set(4,1,4,"smoker[facing=south,lit=false]");set(2,1,2,"barrel[facing=up,open=false]");
        as(OTHER);for(var e:BAKERY_FURNITURE.entrySet())set(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue());
    }
    /** Design review 2026-09-24 (lateFurniture): every free floor cell of the shop is a walk to some level's kit, so the furnishing goes over
     *  head height and only over walks no kit ever takes (a shelf over a kit would shut the way onto it): board shelves with a pot on the west
     *  wall, one of them against the oven, and wheat (straw bales) stored on the tie beam over the two lamps. */
    private static final Map<Cell,String> BAKERY_FURNITURE=Map.of(new Cell(1,3,3),"spruce_slab[type=top]",new Cell(1,3,5),"spruce_slab[type=top]",
        new Cell(1,4,3),"potted_poppy",new Cell(1,4,5),"potted_poppy",new Cell(2,5,4),"hay_block[axis=x]",new Cell(6,5,4),"hay_block[axis=x]");
    static{late("bakery",BAKERY_FURNITURE.keySet());}

    // ---------- restaurant: 11x9 dining house, gable to the street (AD-139) ----------
    /** AD-139: the eight table groups of the restaurant hall, in the order the levels lay them (balance/dining.json seat_groups: I 2, II 4, IV 6,
     *  VI 8): the table's cell {x,z}; its two seats stand west and east of it, a spruce chair each facing the table (AD-143; halls built
     *  before it keep their stairs). Four tables of four seats: two by the west windows, two by the east ones, a centre aisle from the door to the hearth, a cross aisle at z4
     *  past the stock chest (1,1,4). Level I draws the first {@link #RESTAURANT_FIRST}; the others stay air and no equipment takes them. */
    public static final int[][] RESTAURANT_TABLES={{3,2},{3,3},{7,2},{7,3},{3,5},{3,6},{7,5},{7,6}};
    public static final int RESTAURANT_FIRST=2;
    /** Whether a group's table joins the table north of it (its pair: z 2+3 and 5+6), else the one south of it. */
    public static boolean restaurantJoinsNorth(int group){int z=RESTAURANT_TABLES[group][1];return z==3||z==6;}
    /** AD-143: the table of a group, a spruce table joined to the other table of its pair. */
    public static String restaurantTable(int group){boolean north=restaurantJoinsNorth(group);return table(org.villageastra.domain.Furniture.RESTAURANT_WOOD,false,north,!north,false);}
    /** AD-143: a seat of a group, a spruce chair facing the table: the one west of it faces east, the one east of it west. */
    public static String restaurantSeat(boolean west){return chair(org.villageastra.domain.Furniture.RESTAURANT_WOOD,west?"east":"west");}
    /** AD-143: a chair of a wood facing the way its sitter looks, and a table of a wood joined on the sides named. */
    static String chair(String wood,String facing){return org.villageastra.domain.Furniture.chairId(wood)+"[facing="+facing+"]";}
    static String table(String wood,boolean e,boolean n,boolean s,boolean w){return org.villageastra.domain.Furniture.tableId(wood)+"[east="+e+",north="+n+",south="+s+",west="+w+"]";}
    /** AD-143: furniture set into cells of a design that were empty before it (the big house's table and chairs). LevelArchitecture sees these
     *  cells as the air they were and never gives them to equipment, so the kits of houses standing in old worlds stay where they are. The
     *  furniture that took the place of older furniture (the restaurant's groups, the cottage's porch bench) is not listed: its cells were
     *  never free. */
    public static Set<Cell> lateFurniture(String id){return LATE.getOrDefault(base(id),Set.of());}
    /** AD-139: floor cells of the restaurant no level's equipment may take: every table and seat of the eight groups, the aisles the diners
     *  walk and the drive post (1,1,1). The kitchen row (z7) and the front corners keep the room for the kits. */
    public static Set<Cell> kept(String id){
        if(!base(id).equals("restaurant"))return Set.of();var out=new HashSet<Cell>();
        for(var t:RESTAURANT_TABLES)for(int dx=-1;dx<=1;dx++)out.add(new Cell(t[0]+dx,1,t[1]));
        for(int z=2;z<=6;z++){out.add(new Cell(5,1,z));out.add(new Cell(9,1,z));if(z!=4)out.add(new Cell(1,1,z));}
        for(int x=2;x<=9;x++)out.add(new Cell(x,1,4));out.add(new Cell(1,1,1));
        return out;
    }
    /** A dining house on the lot of the clinic: its tall street gable over a porch set one block in, shop windows either side of the door,
     *  the side walls framed and windowed by the tables, a brick hearth in the back wall facing the door down the centre aisle with its stack
     *  through the back gable, tie beams over the table rows carrying the table lanterns, the kitchen along the back wall. */
    private void restaurant(){
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=9&&z>=1&&z<=7&&!(z==1&&x>=3&&x<=7);set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=x->5+Math.min(x,10-x);
        roof(true,0,10,0,8,5,(x,z)->x==5&&z==8);
        // Side walls on the lot edge, windowed beside the tables; the cheeks either side of the porch; the shop front one block in.
        // No brace in the front bay: its plaster would hang in the air over the drive post (1,1,1) and a scaffold would be planned through it.
        face(false,0,1,false,0,"PSWSPSW4P",2,4,false,2,"cobblestone");
        face(false,10,-1,false,0,"PSWSPSW4P",2,4,false,2,"cobblestone");
        face(true,0,1,false,1,"WP",2,4,false,2,"cobblestone");face(true,0,1,false,8,"PW",2,4,false,2,"cobblestone");
        // AD-143 (owner rule 2026-09-23): posts either side of the door — no glass pane beside it.
        face(true,1,1,false,3,"WPDPW",2,4,false,2,"cobblestone");
        face(true,8,-1,false,1,"PWSCCCSWP",2,4,false,2,"cobblestone");
        // The porch: the lintel carrying the street gable, knee braces, lanterns, goods either side of the door.
        beamRun(true,4,0,3,7,BEAM);brace(3,3,0,"west");brace(7,3,0,"east");
        as(OTHER);set(4,3,0,hung());set(6,3,0,hung());set(3,1,0,"barrel[facing=up,open=false]");set(7,1,0,"barrel[facing=up,open=false]");
        gable(true,0,1,9,5,h,5,7,new int[]{3,6},new int[]{7,6},new int[]{4,8},new int[]{6,8});barge(true,0,1,9,5,h);
        posts(3,0,5,6);posts(7,0,5,6);
        gable(true,8,1,9,5,h,5,7,new int[]{3,6},new int[]{7,6});barge(true,8,1,9,5,h);
        // The hearth: a brick mass in the middle of the back wall, its fire facing the door, the stack up through the back gable past the ridge.
        as(STONE);for(int x=4;x<=6;x++)for(int y=1;y<=4;y++)set(x,y,8,x==5&&y==1?"furnace[facing=north,lit=true]":"bricks");
        set(4,5,8,"brick_stairs[facing=east,half=bottom,shape=straight]");set(6,5,8,"brick_stairs[facing=west,half=bottom,shape=straight]");
        for(int y=5;y<=12;y++)set(5,y,8,"bricks");set(5,13,8,"brick_slab[type=bottom]");
        // Tie beams over the table rows; a lantern over every table.
        beamRun(true,4,3,1,9,POST);beamRun(true,4,6,1,9,POST);
        as(OTHER);for(int x:new int[]{3,7})for(int z:new int[]{3,6})set(x,3,z,hung());
        // The kitchen along the back wall: the cook's smoker, a flour barrel, the dresser.
        as(OTHER);set(1,1,7,"smoker[facing=north,lit=false]");set(2,1,7,"barrel[facing=up,open=false]");set(3,1,7,"crafting_table");
        door(5,1);as(KIT);set(1,1,4,CHEST);
        for(int g=0;g<RESTAURANT_FIRST;g++){int x=RESTAURANT_TABLES[g][0],z=RESTAURANT_TABLES[g][1];
            set(x,1,z,restaurantTable(g));set(x-1,1,z,restaurantSeat(true));set(x+1,1,z,restaurantSeat(false));}
        as(OTHER);for(var e:RESTAURANT_FURNITURE.entrySet())set(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue());
    }
    /** Design review 2026-09-24 (lateFurniture): every free floor cell of the hall is a table, a seat or an aisle, so the furnishing goes over
     *  head height, only over aisles no kit ever takes (a shelf over the kitchen row, or over the west aisle that from level V is reached only
     *  over the chest, would shut that way): a board shelf with a pot on the east wall by the back tables, and the kitchen's straw stored on
     *  the tie beams. */
    private static final Map<Cell,String> RESTAURANT_FURNITURE=Map.of(new Cell(9,3,5),"spruce_slab[type=top]",new Cell(9,4,5),"potted_poppy",new Cell(2,5,3),"hay_block[axis=x]",new Cell(8,5,3),"hay_block[axis=x]",
        new Cell(2,5,6),"hay_block[axis=x]",new Cell(8,5,6),"hay_block[axis=x]");
    static{late("restaurant",RESTAURANT_FURNITURE.keySet());}

    /** Design review 2026-09-24: a design's late furniture as {x,y,z,state} quadruples, in the order they are set. */
    private static Map<Cell,String> decor(Object... q){var out=new LinkedHashMap<Cell,String>();
        for(int i=0;i<q.length;i+=4)if(out.put(new Cell((Integer)q[i],(Integer)q[i+1],(Integer)q[i+2]),(String)q[i+3])!=null)throw new IllegalStateException("Furniture cell twice at "+q[i]+","+q[i+1]+","+q[i+2]);
        return Collections.unmodifiableMap(out);}
    /** Sets late furniture, each piece into a cell the design left empty: over the shell or a station it is a mistake, not an overwrite. */
    private void furnish(Map<Cell,String> decor){as(OTHER);for(var e:decor.entrySet()){var c=e.getKey();
        if(!empty(c.x(),c.y(),c.z()))throw new IllegalStateException(id+": furniture over "+m.get(c)+" at "+c);set(c.x(),c.y(),c.z(),e.getValue());}}

    // ---------- smithy: 11x9 forge, eaves to the street ----------
    /** A masonry ground storey under a timber plate, framed gables one block in so the slate roof overhangs them, the east side open to the yard
     *  between two posts with knee braces, and inside the forge: a brick hearth under a hood and its 2x1 brick stack three blocks over the ridge. */
    private void smithy(){
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=9&&z>=1&&z<=7;set(x,0,z,in?"stone_bricks":"cobblestone");}
        IntUnaryOperator h=z->6+Math.min(z,8-z);
        roof(false,0,8,0,10,6,(x,z)->(x==7||x==8)&&z==7);
        stone(true,0,0,10,1,3,true);stone(true,8,0,10,1,3,true);stone(false,0,1,7,1,3,true);stone(false,10,1,7,1,3,true);
        // A framed band of plaster between the masonry and the plate.
        face(true,0,1,false,0,"P1SPSPSPS4P",4,5,false,9,null);face(true,8,-1,false,0,"P1SPSPSPS4P",4,5,false,9,null);
        face(false,0,1,false,0,"P1SPSPS4P",4,5,false,9,null);face(false,10,-1,false,0,"PSOOOOOSP",4,5,false,9,null);
        // The open east bay: two posts, the beam over them, knee braces.
        as(FRAME);for(int z:new int[]{2,6})for(int y=1;y<=3;y++)set(10,y,z,log(POST,'y'));beamRun(false,4,10,2,6,BEAM);
        as(OTHER);for(int z=3;z<=5;z++)for(int y=1;y<=3;y++)set(10,y,z,"air");
        as(FRAME);set(10,3,3,stair("dark_oak_stairs","north",true));set(10,3,5,stair("dark_oak_stairs","south",true));
        beamRun(true,5,0,0,10,BEAM);beamRun(true,5,8,0,10,BEAM);beamRun(false,5,0,1,7,BEAM);beamRun(false,5,10,1,7,BEAM);
        // Door with a timber lintel, windows in the wall plane. Design review 2026-09-24: the front window's pane stood one block inside the
        // room with nothing either side of it (it hung by an edge); it is glazed in the stone now, a board sill inside under it (the sill keeps
        // the cell over (2,1,1) filled as the pane did, so no level's equipment moves).
        as(OTHER);for(int y=2;y<=3;y++){pane(2,y,0,true);pane(0,y,2,false);pane(0,y,6,false);pane(3,y,8,true);pane(5,y,8,true);}
        set(2,2,1,"spruce_slab[type=bottom,waterlogged=false]");
        beamRun(true,3,0,4,6,BEAM);
        beamRun(false,5,1,1,7,BEAM);beamRun(false,5,9,1,7,BEAM);
        gable(false,1,1,7,6,h,4,7,new int[]{3,6},new int[]{5,6});rake(false,0,0,8,h);pentice(false,0,1,7,6,"east");
        gable(false,9,1,7,6,h,4,7,new int[]{3,6},new int[]{5,6});rake(false,10,0,8,h);pentice(false,10,1,7,6,"west");
        // The forge: hearth, hood and stack.
        as(STONE);for(int x=7;x<=8;x++){for(int y=1;y<=13;y++)set(x,y,7,"bricks");set(x,14,7,"brick_slab[type=bottom]");set(x,1,6,"bricks");set(x,3,6,"brick_stairs[facing=south,half=top,shape=straight]");}
        // Round 1 review: the forge opens to the street — a bay three wide between dressed stone piers under a timber lintel, the hearth's fire
        // door and the smith's table seen through it.
        as(OTHER);for(int x=7;x<=9;x++)for(int y=1;y<=3;y++)set(x,y,0,"air");
        as(STONE);for(int y=1;y<=3;y++){set(6,y,0,y==3?"chiseled_stone_bricks":"stone_bricks");set(10,y,0,y==3?"chiseled_stone_bricks":"stone_bricks");}
        beamRun(true,4,0,7,9,BEAM);
        as(OTHER);set(8,2,1,"air");set(8,3,1,"air");set(7,1,6,"furnace[facing=north,lit=true]");set(9,1,2,"smithing_table");set(8,3,0,hung());
        beamRun(false,5,3,1,7,POST);beamRun(false,5,6,1,7,POST);
        as(OTHER);set(0,8,4,hung());set(10,8,4,hung());set(3,4,5,hung());set(6,4,3,hung());set(9,1,6,"barrel[facing=up,open=false]");
        // Design review 2026-09-24: a cobble apron before the hearth (the floor course, relaid by the levels as before).
        as(DECK);for(int x=6;x<=9;x++)for(int z=5;z<=6;z++)set(x,0,z,"cobblestone");
        door(5,0);as(KIT);set(1,1,4,CHEST);set(4,1,4,"furnace[facing=north,lit=false]");set(2,1,2,"grindstone[face=floor,facing=north]");
        furnish(SMITHY_FURNITURE);
    }
    /** Design review 2026-09-24 (lateFurniture): the smith's shelves, off the floor every level's equipment and aisles use — between the west
     *  windows a shelf over the stock chest with two crucibles and a lamp, along the back wall a shelf at bench height over the level benches
     *  (the windows left clear) with crucibles and a lamp, a mat inside the door. */
    private static final Map<Cell,String> SMITHY_FURNITURE=decor(
        1,3,3,trapdoor("spruce","east",true,false),1,3,4,trapdoor("spruce","east",true,false),1,3,5,trapdoor("spruce","east",true,false),
        1,4,3,"flower_pot",1,4,4,"lantern[hanging=false]",1,4,5,"flower_pot",
        1,2,7,trapdoor("spruce","north",true,false),2,2,7,trapdoor("spruce","north",true,false),3,2,7,trapdoor("spruce","north",true,false),
        4,2,7,trapdoor("spruce","north",true,false),6,2,7,trapdoor("spruce","north",true,false),
        1,3,7,"flower_pot",2,3,7,"lantern[hanging=false]",4,3,7,"flower_pot",6,3,7,"flower_pot",
        5,1,1,"brown_carpet");
    static{late("smithy",SMITHY_FURNITURE.keySet());}

    // ---------- carpentry: 11x9 joinery, a saltbox roof falling to the street ----------
    /** A low front and a tall back under one long spruce shingle slope to the street and a short one behind (a saltbox), plastered below and
     *  boarded above, the gables one block in; along the east side an open gallery under a pent roof, the log pile under it. */
    private void carpentry(){
        // Round 1 review ("the one-sided roof dwarfs the walls"): a balanced 45-degree spruce shingle gable over the joinery, its front one block
        // in under the eave, the west gable one block in over the plate under an overhanging end course (soffit and pentice), the open east
        // gallery under its pent roof with the log pile and the sawhorse; a plank stack and barrels before the door.
        // Design review 2026-09-24: a spruce field inside a dark oak border (the floor course, relaid by the levels from III as before).
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=9&&z>=2&&z<=7,field=x>=2&&x<=8&&z>=3&&z<=6;set(x,0,z,field?"spruce_planks":in?PLANKS:"cobblestone");}
        // Round 2 review: one roof material for the set — dark oak shingle, spruce barge boards.
        IntUnaryOperator h=z->5+Math.min(z,8-z);
        roof(false,0,8,0,10,5,(x,z)->false);
        // AD-144: the windows either side of the door flush in the wall (a window set one block in left an empty jamb by the door).
        face(true,1,1,false,0,"P12PWDWP34P",2,4,false,2,"cobblestone");
        // AD-148: the level-II lamp hangs at (4,3,1) under the plate (a frozen cell): each window by the door is one pane high in the wall
        // plane (a full jamb beside the door), over it a lamp niche under the plate, backed with plaster one block in.
        for(int x:new int[]{4,6}){as(OTHER);set(x,3,1,"air");as(INFILL);set(x,3,2,PLASTER);}
        face(false,0,1,false,1,"PWSPSWSP",2,4,false,2,"cobblestone");
        // The pane beside the plaster set behind the front brace joins it.
        as(OTHER);set(0,3,2,pane(false));
        face(true,8,-1,false,0,"P1WSPSPSW4P",2,4,false,2,"cobblestone");
        // The east gallery: posts, the plate on them, knee braces, a pent roof over it.
        as(FRAME);for(int z:new int[]{0,4,8})for(int y=1;y<=3;y++)set(10,y,z,log(POST,'y'));beamRun(false,4,10,0,8,BEAM);
        as(PLINTH);for(int z:new int[]{0,4,8})set(10,0,z,"cobblestone");
        as(FRAME);for(int z:new int[]{1,5})set(10,3,z,stair("dark_oak_stairs","north",true));for(int z:new int[]{3,7})set(10,3,z,stair("dark_oak_stairs","south",true));
        // Design review 2026-09-24: the front wall's corner post stands in the gallery line at z1, so it runs up whole to the plate there
        // (a knee brace had cut it at y3).
        set(10,3,1,log(POST,'y'));
        // Boarded gables on tie beams: the west one set in, the east one over the gallery; the soffit, the pentice and the verges.
        fill="spruce_planks";
        beamRun(false,4,1,1,7,BEAM);beamRun(false,4,9,1,7,BEAM);beamRun(false,4,0,1,7,BEAM);
        gable(false,1,1,7,5,h,4,7,new int[]{3,6},new int[]{5,6});rake(false,0,0,8,h);pentice(false,0,1,7,5,"east");
        gable(false,9,1,7,5,h,4,7,new int[]{3,6},new int[]{5,6});
        fill=PLASTER;
        verges(false,10,1,7,4,h,"north","south");
        beamRun(true,5,3,2,7,POST);beamRun(true,5,6,2,7,POST);
        // Design review 2026-09-24: the gallery's lantern hung in place of its plate over the middle post, from nothing; the plate runs whole
        // and the gallery's lamp hangs under it over the sawhorse (lateFurniture).
        as(OTHER);set(3,4,3,hung());set(7,4,6,hung());set(5,4,0,hung());
        // Under the gallery: the log pile (stripped logs, end grain to the street) and a sawhorse, its board on a block and a leg (lateFurniture;
        // a fence leg beside the level bench at (9,1,2) would join it from level IV on).
        for(int z=5;z<=7;z++){set(10,1,z,"stripped_oak_log[axis=z]");if(z>=6)set(10,2,z,"stripped_oak_log[axis=z]");}set(10,3,7,"stripped_spruce_log[axis=z]");
        set(10,1,2,log("spruce_log",'y'));set(10,1,3,fence("spruce",false,true,true,true));
        // Before the door: a stack of boards, barrels, a chopping stump.
        for(int x=1;x<=2;x++){set(x,1,0,log("stripped_spruce_log",'x'));set(x,2,0,"spruce_slab[type=bottom,waterlogged=false]");}
        set(8,1,0,"barrel[facing=up,open=false]");set(9,1,0,"barrel[facing=up,open=false]");set(9,2,0,"barrel[facing=up,open=false]");set(7,1,0,"stripped_oak_log[axis=y]");
        door(5,1);as(KIT);set(1,1,4,CHEST);set(3,1,3,"crafting_table");set(9,1,7,"crafting_table");for(int z=3;z<=6;z++)set(9,1,z,"stripped_oak_log[axis=y]");
        furnish(CARPENTRY_FURNITURE);
        slate();
    }
    /** Design review 2026-09-24 (lateFurniture): the joiner's shelves, off the floor the levels' benches and aisles use — along the back wall a
     *  shelf at bench height between the windows with glue pots and a lamp, on the west wall a shelf under the tie beam over the stock chest,
     *  a mat inside the door; under the gallery the sawhorse's board and a lamp hung over it under the plate. */
    private static final Map<Cell,String> CARPENTRY_FURNITURE=decor(
        3,2,7,trapdoor("spruce","north",true,false),4,2,7,trapdoor("spruce","north",true,false),5,2,7,trapdoor("spruce","north",true,false),
        6,2,7,trapdoor("spruce","north",true,false),7,2,7,trapdoor("spruce","north",true,false),
        3,3,7,"flower_pot",5,3,7,"lantern[hanging=false]",7,3,7,"flower_pot",
        1,3,3,trapdoor("spruce","east",true,false),1,3,4,trapdoor("spruce","east",true,false),1,3,5,trapdoor("spruce","east",true,false),
        5,1,2,"brown_carpet",
        10,2,2,"spruce_slab[type=bottom,waterlogged=false]",10,2,3,"spruce_slab[type=bottom,waterlogged=false]",10,3,2,hung());
    static{late("carpentry",CARPENTRY_FURNITURE.keySet());}

    // ---------- mill: 9x9 tower mill ----------
    /** A masonry base with a sloped stone drip, a framed tower one block in above it under a slate roof, and on the street face four
     *  sails in a cross — dark spars with pale birch lattice — round a hub on the axle through the front plate; the grindstone below, a shaft in the tower. */
    private void mill(){
        as(DECK);for(int x=0;x<9;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=7&&z>=1&&z<=7;set(x,0,z,in?PLANKS:"cobblestone");}
        stone(true,0,0,8,1,4,true);stone(true,8,0,8,1,4,true);stone(false,0,1,7,1,4,true);stone(false,8,1,7,1,4,true);
        as(DECK);for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)set(x,4,z,PLANKS);
        for(int y=2;y<=3;y++){pane(2,y,0,true);pane(6,y,0,true);pane(0,y,2,false);pane(0,y,6,false);pane(8,y,2,false);pane(8,y,6,false);pane(4,y,8,true);}
        beamRun(true,3,0,3,5,BEAM);
        IntUnaryOperator h=x->11+Math.min(x,8-x);
        roof(true,0,8,1,8,11,(x,z)->false);
        face(true,1,1,false,1,"PSWPWSP",5,11,true,7,null);
        face(true,7,-1,false,1,"PSWPWSP",5,11,true,7,null);
        face(false,1,1,false,1,"P12P34P",5,11,true,7,null);
        face(false,7,-1,false,1,"P12P34P",5,11,true,7,null);
        gable(true,1,1,7,12,h,4,-1);gable(true,7,1,7,12,h,4,-1,new int[]{3,12},new int[]{5,12});
        verges(true,8,1,7,4,h,"west","east");
        as(FRAME);for(int z=1;z<=8;z++){set(0,10,z,"dark_oak_slab[type=top]");set(8,10,z,"dark_oak_slab[type=top]");}
        // Hub, axle and shaft; the sails.
        as(OTHER);set(4,10,0,log(POST,'z'));set(4,10,1,log("spruce_log",'z'));set(4,10,2,log("spruce_log",'z'));for(int y=5;y<=9;y++)set(4,y,2,log("spruce_log",'y'));
        // Four sails in a cross, each a spar with a birch lattice on its trailing side, turning the same way round.
        int[][] arms={{0,1,1,0},{1,0,0,-1},{0,-1,-1,0},{-1,0,0,1}};
        // Round 1 review ("flat pale panels, not sails"): each sail a stripped spar with a bare lattice of spruce fence rails on its trailing
        // side and a cross bar at its tip, as a mill stands at rest with its cloths furled.
        for(var a:arms)for(int i=1;i<=4;i++){int x=4+a[0]*i,y=10+a[1]*i;set(x,y,0,log(BEAM,a[0]==0?'y':'x'));
            if(i<2)continue;int lx=x+a[2],ly=y+a[3];boolean horizontal=a[1]==0;
            // A lattice cell joins its neighbours along the arm (a horizontal arm's rails run east-west) and the spar beside it.
            boolean e=horizontal?(i<4||a[0]<0)&&(i>2||a[0]>0):a[2]<0,wv=horizontal?(i<4||a[0]>0)&&(i>2||a[0]<0):a[2]>0;
            set(lx,ly,0,fence("spruce",e,false,false,wv));}
        // The stone drip round the base, clear of the sail tips.
        as(STONE);for(int i=0;i<=8;i++){if(empty(i,5,0))set(i,5,0,i==0||i==8?"stone_brick_slab[type=bottom]":stair("stone_brick_stairs","south",false));
            set(i,5,8,i==0||i==8?"stone_brick_slab[type=bottom]":stair("stone_brick_stairs","north",false));}
        for(int z=1;z<=7;z++){set(0,5,z,stair("stone_brick_stairs","east",false));set(8,5,z,stair("stone_brick_stairs","west",false));}
        as(OTHER);set(4,13,4,hung());set(2,3,5,hung());set(6,3,3,hung());
        door(4,0);as(KIT);set(1,1,4,CHEST);set(3,1,3,"grindstone[face=floor,facing=north]");set(7,1,7,"crafting_table");
    }
    /** Whether a fence rail in the cell beside (x,y,z) joins it: another fence or a whole block (not a stair, slab, pane, lamp or door). */
    private boolean fenceJoins(int x,int y,int z){String s=m.get(new Cell(x,y,z));if(s==null||s.equals("air"))return false;if(s.contains("fence"))return true;
        for(var part:new String[]{"stairs","slab","pane","lantern","door","_wall","chain","bed","carpet","flower_pot","potted"})if(s.contains(part))return false;
        return true;}

    // ---------- engineering: 11x11 works hall, eaves to the street ----------
    /** A masonry hall under a timber plate and a slate roof whose ridge carries a glazed clerestory monitor with its own roof; framed gables one
     *  block in at both ends with lanterns under the verges, paired windows in the front, tie beams inside. */
    private void engineering(){
        // Design review 2026-09-24: a spruce field inside a dark oak border (the floor course, relaid by the levels from III as before).
        as(DECK);for(int x=0;x<11;x++)for(int z=0;z<11;z++){boolean in=x>=1&&x<=9&&z>=1&&z<=9,field=x>=2&&x<=8&&z>=2&&z<=8;set(x,0,z,field?"spruce_planks":in?PLANKS:"cobblestone");}
        IntUnaryOperator h=z->6+Math.min(z,10-z),dh=x->8+Math.min(x-3,7-x);
        roof(false,0,10,0,10,6,(x,z)->false);
        stone(true,0,0,10,1,4,true);stone(true,10,0,10,1,4,true);stone(false,0,1,9,1,4,true);stone(false,10,1,9,1,4,true);
        beamRun(true,5,0,0,10,BEAM);beamRun(true,5,10,0,10,BEAM);beamRun(false,5,0,1,9,BEAM);beamRun(false,5,10,1,9,BEAM);
        // Round 2 review ("glass flush with a flat stone wall"): the street windows set one block in on a stone brick sill.
        for(int x:new int[]{2,3,7,8}){as(STONE);set(x,1,0,stair("stone_brick_stairs","south",true));}
        // Design review 2026-09-24: the side windows in the same pairs on both sides (they differed), a stone head inside over each street pair
        // (the panes set one block in hung by an edge), and the back wall glazed over the bookshelves in the street pairs' bays.
        for(int y=2;y<=3;y++){for(int x:new int[]{2,3,7,8}){as(OTHER);set(x,y,0,"air");pane(x,y,1,true);}for(int z:new int[]{2,3,7,8}){pane(0,y,z,false);pane(10,y,z,false);}}
        for(int x:new int[]{2,3,7,8}){as(STONE);set(x,4,1,"stone_bricks");for(int y=3;y<=4;y++)pane(x,y,10,true);}
        arch(5,3,0);
        // Gables closed in the wall planes over the plate, barge boards under the verges.
        gable(false,0,1,9,6,h,5,8,new int[]{3,6},new int[]{7,6});barge(false,0,1,9,5,h);
        gable(false,10,1,9,6,h,5,8,new int[]{3,6},new int[]{7,6});barge(false,10,1,9,5,h);
        // AD-122 review: a dormer on the street slope under the main ridge, its own 45-degree gable in the wall plane, a hoist beam out of its peak
        // with the chain and hook block of the works' crane.
        crossRoof(3,7,8,h);
        // Design review 2026-09-24: the main roof's stairs in the valleys beside the dormer's ridge turn inner corners, as the game shapes them.
        as(ROOF);set(4,9,3,rs+"[facing=south,half=bottom,shape=inner_left]");set(6,9,3,rs+"[facing=south,half=bottom,shape=inner_right]");
        face(true,0,1,false,3,"PWOWP",6,8,false,6,null);
        as(OTHER);for(int y=6;y<=7;y++)set(5,y,0,"spruce_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
        for(int x:new int[]{3,7})set(x,8,0,stair(TILE,x==3?"east":"west",false));
        gable(true,0,4,6,8,dh,5,-1);
        as(FRAME);set(5,9,0,log(BEAM,'z'));set(5,9,1,log(BEAM,'z'));
        beamRun(false,5,3,1,9,POST);beamRun(false,5,7,1,9,POST);
        as(OTHER);set(3,4,5,hung());set(7,4,5,hung());set(5,9,5,hung());
        // Round 1 review: the works' sign on the facade — two gear wheels (grindstones) on the piers.
        set(1,3,0,"grindstone[face=wall,facing=north]");set(9,3,0,"grindstone[face=wall,facing=north]");
        door(5,0);as(KIT);set(1,1,4,CHEST);set(3,1,3,"smithing_table");set(6,1,3,"stonecutter[facing=north]");
        for(int x=1;x<=9;x++)for(int y=1;y<=2;y++)set(x,y,9,"bookshelf");
        furnish(ENGINEERING_FURNITURE);
    }
    /** Design review 2026-09-24 (lateFurniture): the works' drawing office, off the floor the levels' benches and aisles use — a drawing desk
     *  and chair under each front side window (the cell north of it stays the way to the level bench in the corner), lamps hung from the
     *  tie beams over the desks, a drawing board on each side wall between its windows, ferns and a lamp on the bookshelves between the
     *  back windows, a mat inside the door. */
    private static final Map<Cell,String> ENGINEERING_FURNITURE=decor(
        // AD-148: the desks stand clear of the frozen equipment cells of the front bays (z3), back in the hall either side of the aisle.
        2,1,6,table("dark_oak",false,false,false,false),2,1,7,chair("dark_oak","north"),
        8,1,6,table("dark_oak",false,false,false,false),8,1,7,chair("dark_oak","north"),
        3,4,3,hung(),7,4,3,hung(),
        4,3,9,"potted_fern",5,3,9,"lantern[hanging=false]",6,3,9,"potted_fern",
        1,2,5,trapdoor("spruce","east",false,true),1,3,5,trapdoor("spruce","east",true,true),9,2,5,trapdoor("spruce","west",false,true),9,3,5,trapdoor("spruce","west",true,true),
        5,1,1,"brown_carpet");
    static{late("engineering",ENGINEERING_FURNITURE.keySet());}

    // ---------- batch 3: stone and extraction. Masonry (mas) ground storeys under timber and slate; the west and front walls stay on the lot
    // edge wherever the stock chest (1,1,4) and the door (w/2,1,0) stand, the overhang comes from gables set in and from open bays. ----------
    private static boolean disc(int x,int z,int cx,int cz,double r2){int dx=x-cx,dz=z-cz;return dx*dx+dz*dz<=r2+1e-9;}
    private static String inward(int dx,int dz){return Math.abs(dx)>=Math.abs(dz)?(dx<0?"east":"west"):(dz<0?"south":"north");}
    /** Upside-down dark oak stair beside a post, facing it: a knee brace. */
    private void brace(int x,int y,int z,String facing){as(FRAME);set(x,y,z,stair("dark_oak_stairs",facing,true));}
    private void posts(int x,int z,int y0,int y1){as(FRAME);for(int y=y0;y<=y1;y++)set(x,y,z,log(POST,'y'));}
    /** A low stone brick wall along the given cells at y1, each joined to its neighbours in the run, a pier where the run turns or ends. */
    private void lowWall(List<int[]> run){
        var at=new HashSet<Long>();for(var c:run)at.add(((long)c[0]<<32)|c[1]);
        as(OTHER);for(var c:run){boolean e=at.contains(((long)(c[0]+1)<<32)|c[1]),wv=at.contains(((long)(c[0]-1)<<32)|c[1]),s=at.contains(((long)c[0]<<32)|(c[1]+1)),n=at.contains(((long)c[0]<<32)|(c[1]-1));
            boolean up=!(e&&wv&&!n&&!s)&&!(n&&s&&!e&&!wv);
            set(c[0],1,c[1],"stone_brick_wall[east="+(e?"low":"none")+",north="+(n?"low":"none")+",south="+(s?"low":"none")+",up="+up+",waterlogged=false,west="+(wv?"low":"none")+"]");}
    }
    /** Castle {@code cone}: a 2:1 slate cone over a round tower, the eave ring one beyond the wall on alternate stone corbels, a spire on top. */
    private void cone(int cx,int cz,double re,int y0){
        int R=(int)Math.ceil(re),K=(int)Math.floor(re);
        for(int dx=-R;dx<=R;dx++)for(int dz=-R;dz<=R;dz++){
            double r=Math.sqrt(dx*dx+dz*dz);if(r>re+1e-9)continue;
            int x=cx+dx,z=cz+dz,k=(int)Math.floor(re-r+1e-9);String f=inward(dx,dz);as(ROOF);
            if(dx==0&&dz==0){for(int y=y0;y<=y0+2*K-2;y++)set(x,y,z,TILES);as(OTHER);set(x,y0+2*K-1,z,"stone_brick_wall[up=true]");continue;}
            if(k==0){set(x,y0,z,stair(TILE,f,false));if((x+z)%2==0&&empty(x,y0-2,z)){as(FRAME);setIfEmpty(x,y0-1,z,stair("stone_brick_stairs",f,true));}continue;}
            for(int y=y0;y<=y0+2*k-3;y++)set(x,y,z,TILES);set(x,y0+2*k-2,z,stair(TILE,f,false));set(x,y0+2*k-1,z,stair(TILE,f,false));}
    }

    // ---------- mine: 8x9 pithead, gable to the street ----------
    /** AD-074/079 underground unchanged — the stepped shaft in the middle two columns down to six below the lot and the open mouth of the drive —
     *  under a masonry pithead with a timber plate and a slate roof whose gables stand one block in; through the ridge rises the dark oak headframe
     *  on four legs with knee braces, its own little slate roof and the hoist ropes, and inside the old hoist beams, chains and lamps over the shaft.
     *  The furnace stands in the west wall with its flue up the eave; the double door opens on the shaft; the aisle at x=1 stays free to the chest. */
    private void mine(){
        as(DECK);for(int x=0;x<8;x++)for(int z=0;z<9;z++)set(x,0,z,"cobblestone");
        // The shaft and the drive mouth, cell for cell as before (MineWork keeps its lining, MineShaftGameTests reads the treads).
        as(OTHER);String tread=stair("stone_brick_stairs","north",false);
        for(int z=1;z<=6;z++){for(int x=3;x<=4;x++){set(x,-z,z,tread);for(int y=-z+1;y<=0;y++)set(x,y,z,"air");}
            for(int x:new int[]{2,5})for(int y=-z;y<=-1;y++)set(x,y,z,"stone_bricks");
            for(int x=1;x<=5;x++)set(x,-z-1,z,"stone_bricks");}
        for(int z=7;z<=8;z++)for(int x=2;x<=4;x++){set(x,-6,z,"stone_bricks");for(int y=-5;y<=0;y++)set(x,y,z,"air");}
        // Masonry ground storey, the plate on it, a timber door frame round the double door.
        stone(true,0,0,7,1,3,true);stone(true,8,0,7,1,3,true);stone(false,0,1,7,1,3,true);stone(false,7,1,7,1,3,true);
        beamRun(true,4,0,0,7,BEAM);beamRun(true,4,8,0,7,BEAM);beamRun(false,4,0,1,7,BEAM);beamRun(false,4,7,1,7,BEAM);
        posts(2,0,1,2);posts(5,0,1,2);beamRun(true,3,0,2,5,BEAM);
        for(int y=2;y<=3;y++){pane(1,y,0,true);pane(6,y,0,true);pane(0,y,6,false);pane(7,y,2,false);pane(7,y,6,false);pane(1,y,8,true);pane(6,y,8,true);}
        // Slate roof over the whole lot, gables one block in on tie beams, barge boards at both ends.
        IntUnaryOperator h=x->5+Math.min(x,7-x);
        roof(true,0,7,0,8,5,(x,z)->false);
        // AD-122 review: the gables closed in the wall planes over the plate (no recess under the verge), two posts and a pair of windows each, barge boards.
        // Round 1 review: the street gable one block in over the plate under the overhanging end course (soffit and pentice), a collar beam
        // over its windows; the back gable closed in the wall plane.
        beamRun(true,4,1,1,6,BEAM);gable(true,1,1,6,5,h,-1,7,new int[]{3,6},new int[]{4,6});posts(2,1,5,6);posts(5,1,5,6);rake(true,0,0,7,h);pentice(true,0,1,6,5,"south");
        gable(true,8,1,6,5,h,-1,7,new int[]{3,6},new int[]{4,6});posts(2,8,5,6);posts(5,8,5,6);barge(true,8,1,6,-1,h);
        // Round 1 review: no flue stack through the slope (it read as a stray pillar); the furnace draws through the roof.
        // Tie beams across the shaft, the hoist beams on them, their chains and lamps; the lamps of the aisles.
        beamRun(true,4,2,1,6,BEAM);beamRun(true,4,5,1,6,BEAM);beamRun(false,5,3,2,5,BEAM);beamRun(false,5,4,2,5,BEAM);
        as(OTHER);for(int x=3;x<=4;x++){for(int y=3;y<=4;y++)set(x,y,3,"chain[axis=y]");set(x,2,3,hung());}
        // Design review 2026-09-24: no lamp at (3,6,0) — under the upside-down rake stair it had no face to hang from and fell at the first update.
        set(1,3,2,hung());set(6,3,2,hung());
        // Round 1 review ("a squashed spire"): the headframe is an open braced tower of four dark oak legs through the roof, tie beams in two tiers
        // with knee braces, a head beam across the shaft carrying two sheaves (grindstones hung from it) and the hoist chains, slab caps on the legs.
        // Round 2 review ("the tower pokes through a patchy roof"): the headframe's four legs stand from the floor to a boarded winding house that
        // closes the roof round them (the roof ends against its walls), an open sheave stage over it with the two wheels on the head beam and
        // the ropes, and a gable cap of its own one block wider than the legs on every side.
        for(int x:new int[]{2,5})for(int z:new int[]{2,5})posts(x,z,1,13);
        for(int x=2;x<=5;x++)for(int z=2;z<=5;z++){boolean ring=x==2||x==5||z==2||z==5,corner=(x==2||x==5)&&(z==2||z==5);if(!ring||corner)continue;
            as(FRAME);for(int y=h.applyAsInt(x)+1;y<=9;y++)set(x,y,z,"spruce_planks");set(x,10,z,log(BEAM,x==2||x==5?'z':'x'));}
        pane(3,9,2,true);pane(4,9,2,true);
        // The sheave stage: open between the plate (y12) and the head beams (y15); the wheels hang from the head beam across the shaft.
        for(int z:new int[]{2,5}){brace(3,12,z,"west");brace(4,12,z,"east");}
        beamRun(true,13,3,2,5,BEAM);beamRun(true,13,2,3,4,BEAM);beamRun(true,13,5,3,4,BEAM);
        as(OTHER);for(int x=3;x<=4;x++){set(x,12,3,"grindstone[face=ceiling,facing=north]");set(x,11,3,"chain[axis=y]");}
        as(DECK);for(int x=3;x<=4;x++)set(x,10,4,PLANKS);
        // The cap: a 45-degree gable over x1..6 by z1..6 from y16, its ridge across the shaft, boarded gables.
        roof(false,1,6,1,6,14,(x,z)->false);
        as(FRAME);for(int x:new int[]{2,5})for(int z=2;z<=5;z++)for(int y=14;y<14+Math.min(z-1,6-z);y++)set(x,y,z,"spruce_planks");
        // A lamp on each side of the double door, the portal lintel on stair shoulders.
        as(FRAME);set(2,3,0,stair("dark_oak_stairs","west",true));set(5,3,0,stair("dark_oak_stairs","east",true));
        // The double door, hinged on the outer sides; the stock chest; the furnace in the west wall facing the aisle.
        as(KIT);set(3,1,0,"oak_door[facing=south,half=lower,hinge=right,open=false,powered=false]");set(3,2,0,"oak_door[facing=south,half=upper,hinge=right,open=false,powered=false]");
        set(4,1,0,"oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]");set(4,2,0,"oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]");
        set(1,1,4,CHEST);set(0,1,2,"furnace[facing=east,lit=false]");
        as(OTHER);MINE_FURNITURE.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
    }
    /** Design review 2026-09-24: the pithead's office in the few cells no level's kit takes — the overman's table with its lamp and a stack
     *  of pit props either side of the stock chest (its step east of it kept), a shelf of rock samples along the back wall over the drive
     *  mouth and a shelf of props on the east wall, both at y3 over the kits' heads. */
    private static final Map<Cell,String> MINE_FURNITURE=new LinkedHashMap<>();
    static{var f=MINE_FURNITURE;String shelf="spruce_slab[type=top,waterlogged=false]",prop="oak_log[axis=z]";
        // (the aisle from the door to the stock chest stays free: MineShaftGameTests)
        for(int x=2;x<=5;x++)f.put(new Cell(x,3,7),shelf);
        f.put(new Cell(2,4,7),"cobblestone");f.put(new Cell(3,4,7),"stone");f.put(new Cell(5,4,7),"cobblestone_slab[type=bottom,waterlogged=false]");
        for(int z=3;z<=5;z++)f.put(new Cell(6,3,z),shelf);
        f.put(new Cell(6,4,3),prop);f.put(new Cell(6,4,4),prop);
        late("mine",f.keySet());}

    // ---------- quarry: 7x7 stone lodge, eaves to the street ----------
    /** A masonry lodge under a timber plate and a spruce shingle roof whose gables stand one block in (plastered, king post, lanterns under the
     *  ridge); the east side is an open rack on three posts with knee braces holding the cut stone — the trade's sign. A plank loft at y4. */
    private void quarry(){
        // Round 1 review: the lodge's front one block in under the eave, where the yard of the trade lies — rough and dressed stone in piles, a
        // block hanging on its hoist chain; the west gable one block in under an overhanging raked course; a cobble plinth course and dressed
        // stone brick above it; the open east rack on braced posts.
        for(int x=0;x<7;x++)for(int z=0;z<7;z++){boolean in=x>=1&&x<=5&&z>=2&&z<=5;as(z==0?OTHER:DECK);set(x,0,z,in?"stone_bricks":"cobblestone");}
        // Round 2 review: one roof material for the set — dark oak shingle, spruce barge boards.
        IntUnaryOperator h=z->5+Math.min(z,6-z);
        roof(false,0,6,0,6,5,(x,z)->false);
        stone(true,1,0,5,1,3,true);stone(true,6,0,5,1,3,true);stone(false,0,2,5,1,3,true);
        // Door under an arched head, windows in the stone.
        arch(3,3,1);
        for(int y=2;y<=3;y++){pane(1,y,1,true);pane(5,y,1,true);pane(0,y,3,false);pane(2,y,6,true);pane(4,y,6,true);}
        // The open east rack: posts, knee braces, stacks of dressed and rough stone.
        for(int z:new int[]{1,3,6})posts(6,z,1,3);
        brace(6,3,2,"north");brace(6,3,4,"north");brace(6,3,5,"south");
        as(OTHER);set(6,1,2,"stone_bricks");set(6,2,2,"stone_brick_slab[type=bottom]");set(6,1,4,"cobblestone");set(6,1,5,"stone_bricks");set(6,2,5,"cobblestone_slab[type=bottom]");
        // Plate and loft deck; the gables over the plates, the west one set in under the overhanging raked course.
        beamRun(true,4,1,0,6,BEAM);beamRun(true,4,6,0,6,BEAM);beamRun(false,4,0,2,5,BEAM);beamRun(false,4,6,2,5,BEAM);
        as(DECK);for(int x=1;x<=5;x++)for(int z=2;z<=5;z++)set(x,4,z,PLANKS);
        gable(false,1,1,5,5,h,3,-1,new int[]{2,6},new int[]{4,6});rake(false,0,0,6,h);pentice(false,0,1,5,5,"east");
        gable(false,6,1,5,5,h,3,-1,new int[]{2,6},new int[]{4,6});barge(false,6,1,5,3,h);
        // The yard under the eave: rough stone west of the door, dressed stone east of it, a block on the hoist chain.
        // Round 2 review ("the stone by the door reads as debris"): cut stone stacked square on both sides — blocks under a course of slabs.
        as(OTHER);set(0,1,0,"stone_bricks");set(0,2,0,"stone_brick_slab[type=bottom,waterlogged=false]");set(1,1,0,"stone_brick_slab[type=bottom,waterlogged=false]");
        set(5,1,0,"stone_bricks");set(6,1,0,"stone_bricks");set(6,2,0,"stone_brick_slab[type=bottom,waterlogged=false]");set(5,2,0,"stone_brick_slab[type=bottom,waterlogged=false]");
        set(4,4,0,"chain[axis=y,waterlogged=false]");set(4,3,0,"chain[axis=y,waterlogged=false]");set(4,2,0,"stone_bricks");
        set(3,3,2,hung());set(2,4,0,hung());
        door(3,1);as(KIT);set(1,1,4,CHEST);set(3,1,3,"stonecutter[facing=north]");set(5,1,2,"barrel[facing=up,open=false]");
        set(5,1,4,"grindstone[face=floor,facing=north]");set(5,1,5,"crafting_table");
        as(OTHER);QUARRY_FURNITURE.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
        rs=TILE;rf=TILES;rc=CAP;
    }
    /** Design review 2026-09-24: the mason's tally table under the west window and a dressed block on its stack either side of the stock
     *  chest (its step east kept); in the loft over the plates, spare timber and two bales of straw for packing the stone. */
    private static final Map<Cell,String> QUARRY_FURNITURE=new LinkedHashMap<>();
    static{var f=QUARRY_FURNITURE;
        f.put(new Cell(1,1,3),table("spruce",false,false,false,false));f.put(new Cell(1,2,3),"potted_fern");
        f.put(new Cell(1,1,5),"stone_bricks");f.put(new Cell(1,2,5),"stone_brick_slab[type=bottom,waterlogged=false]");
        f.put(new Cell(2,5,1),"oak_log[axis=x]");f.put(new Cell(3,5,1),"oak_log[axis=x]");f.put(new Cell(3,5,5),"hay_block[axis=x]");f.put(new Cell(4,5,5),"hay_block[axis=x]");
        late("quarry",f.keySet());}

    // ---------- masonry: 11x9 mason's lodge and yard ----------
    /** A mason's lodge along the street — masonry ground storey with an arched door, a framed plaster upper storey with braces, slate roof, a
     *  cross gable over the door up to the ridge — open at the back on a timber arcade to the walled yard, where a gantry crane hangs a dressed
     *  block on a chain over the stacks. The dressed-stone row (9,1,3..6), the stonecutter and the table stay where the mason works them. */
    private void masonry(){
        for(int x=0;x<11;x++)for(int z=0;z<9;z++){boolean lodge=x>=1&&x<=9&&z>=1&&z<=4,yard=x>=1&&x<=9&&z>=5&&z<=7;
            as(lodge?DECK:OTHER);set(x,0,z,lodge?PLANKS:yard?(hash(x,0,z)<60?"cobblestone":"stone_bricks"):"cobblestone");}
        stone(true,0,0,10,1,3,true);stone(false,0,1,4,1,3,true);stone(false,10,1,4,1,3,true);
        // The framed upper storey on the masonry: sill, posts, braces, windows, plate.
        beamRun(true,4,0,0,10,BEAM);beamRun(false,4,0,1,4,BEAM);beamRun(false,4,10,1,4,BEAM);
        // Round 1 review: the upper windows set one block in between open shutters. Design review 2026-09-24: the lodge is one tall hall, so a
        // pane set one block in stood in the air over it (the middle one held by nothing but an edge); the upper windows are glazed in the wall.
        face(true,0,1,false,0,"P1WSPWPSW4P",5,7,false,5,null);
        face(false,0,1,false,0,"PSWSP",5,7,false,5,null);face(false,10,-1,false,0,"PSWSP",5,7,false,5,null);
        face(true,4,-1,false,0,"PSWSPSPSWSP",5,7,false,5,null);
        // Round 2 review ("a flat-topped, lumpy roof"): the lodge is five deep, so its roof is a steep 2:1 pitch, the cross gable a 45-degree
        // one under the main ridge.
        IntUnaryOperator main=steep(0,4,8),cross=x->8+Math.min(x-3,7-x);
        roof2(false,0,4,0,10,8,(x,z)->false);
        gable(false,0,0,4,8,main,2,-1);gable(false,10,0,4,8,main,2,-1);
        // The cross gable over the door (round 1 review: one ridge and one cross gable, not two peaks): its ridge at the main ridge's height,
        // the gable edged by barge boards.
        crossRoof(3,7,8,main);
        gable(true,0,4,6,8,cross,5,-1,new int[]{5,8});barge(true,0,4,6,5,cross);
        // The arched door: springers and a chiseled keystone; windows in the stone.
        as(FRAME);set(4,3,0,stair("stone_brick_stairs","west",true));set(6,3,0,stair("stone_brick_stairs","east",true));set(5,3,0,"chiseled_stone_bricks");
        for(int y=2;y<=3;y++){pane(2,y,0,true);pane(8,y,0,true);pane(0,y,2,false);pane(10,y,2,false);}
        // The arcade to the yard: posts, the upper storey's sill over them, knee braces.
        posts(3,4,1,3);posts(7,4,1,3);beamRun(true,4,4,1,9,BEAM);
        brace(1,3,4,"west");brace(2,3,4,"east");brace(4,3,4,"west");brace(6,3,4,"east");brace(8,3,4,"west");brace(9,3,4,"east");
        // The yard: a low wall round it, the gantry crane with a block on its chain, stacks of stone.
        var run=new ArrayList<int[]>();for(int z=5;z<=8;z++){run.add(new int[]{0,z});run.add(new int[]{10,z});}for(int x=1;x<=9;x++)run.add(new int[]{x,8});lowWall(run);
        posts(2,6,1,6);posts(8,6,1,6);beamRun(true,7,6,1,9,BEAM);brace(1,6,6,"east");brace(3,6,6,"west");brace(7,6,6,"east");brace(9,6,6,"west");
        as(OTHER);set(5,6,6,"chain[axis=y]");set(5,5,6,"chain[axis=y]");set(5,4,6,"stone_bricks");
        set(1,1,6,"cobblestone");set(1,1,7,"stone_bricks");set(1,2,7,"stone_brick_slab[type=bottom]");set(4,1,7,"stone_brick_slab[type=bottom]");
        // Design review 2026-09-24: the hall's three lanterns hung from the open roof space; each hangs from a collar tie across the roof now.
        for(int x:new int[]{2,5,8}){as(FRAME);set(x,9,2,log(POST,'z'));as(OTHER);set(x,8,2,hung());}
        door(5,0);as(KIT);set(1,1,4,CHEST);set(3,1,3,"stonecutter[facing=north]");for(int z=3;z<=6;z++)set(9,1,z,"chiseled_stone_bricks");set(9,1,7,"crafting_table");
        furnish(MASONRY_FURNITURE);
    }
    /** Design review 2026-09-24 (lateFurniture): the lodge's fittings, off the floor the levels' benches and aisles use — shelves either side
     *  of the arched door showing dressed work between two lamps, tracing boards on the end walls either side of their windows, a lamp under
     *  the upper storey's sill in the arcade, a mat inside the door (the yard's back row stays the way to the level benches). */
    private static final Map<Cell,String> MASONRY_FURNITURE=decor(
        3,3,1,trapdoor("spruce","south",true,false),4,3,1,trapdoor("spruce","south",true,false),6,3,1,trapdoor("spruce","south",true,false),
        7,3,1,trapdoor("spruce","south",true,false),
        3,4,1,"lantern[hanging=false]",4,4,1,"chiseled_stone_bricks",6,4,1,"chiseled_stone_bricks",7,4,1,"lantern[hanging=false]",
        1,2,1,trapdoor("spruce","east",false,true),1,3,1,trapdoor("spruce","east",true,true),1,2,3,trapdoor("spruce","east",false,true),1,3,3,trapdoor("spruce","east",true,true),
        9,2,1,trapdoor("spruce","west",false,true),9,3,1,trapdoor("spruce","west",true,true),9,2,3,trapdoor("spruce","west",false,true),9,3,3,trapdoor("spruce","west",true,true),
        5,3,4,hung(),5,1,1,"brown_carpet");
    static{late("masonry",MASONRY_FURNITURE.keySet());}

    // ---------- warehouse (AD-147): the store I..VI on its 23x17 lot, gable to the street ----------
    /** AD-147 (owner's logistics ladder, 2026-09-23: "the warehouse grows with every level; at the last one it is in the style of the town-hall
     *  castle"). Every level is a plan of its own on the same 23x17 lot, the door (6,1,1), the stock chest (1,1,4) with its pair (1,1,3) and the
     *  core (6,1,9) in the same cells; the pages of the store (WAREHOUSE_PAGES) keep their cells from their level on.
     *  I - a timber-framed barn x0..12, z1..10 on a cobble plinth, plaster in a dark oak frame, a 45-degree dark oak shingle roof gabled to the
     *  street over a cobble apron, the hay-loft door in the street gable; the rest of the lot a yard. II - the cart bay: a gate three wide in the
     *  street wall by the stock chest (bay B1), a stone brick plinth, and a lean-to shed on posts against the east wall (logs, straw, barrels).
     *  III - a framed upper storey jettied over the street on a beam and brackets under a raised roof, a stair up the east wall, the loft hatch
     *  of the hoist in the street gable under its chain and beam; brick nogging in the ground storey. IV - the shed rebuilt as a two-storey
     *  east wing x13..19 with a gable of its own to the street, its cart gate (bay B2), doorways to the barn on both floors; roofs in deepslate
     *  brick. V - the harness room behind the wing (two wolf stalls with straw, the feed barrel, chains, a wide opening to the east lane);
     *  the ground storeys in stone brick, roofs in deepslate tile. VI - the castle: every wall in the hall's masonry (a cobble and andesite
     *  foot, stone brick, a polished andesite course, chiseled quoins), arched gates, slit windows, the wing and the harness room under
     *  crenellated parapets, a round tower d5 on the street-east corner under a deepslate tile cone, and a mark of its category in the wall
     *  behind every page of the sorted store. The materials of a level are drawn here (VillageStyle.OWN_MATERIALS): LevelArchitecture adds
     *  only the stations and the core (WarehouseStore). */
    static final int WAREHOUSE_W=23,WAREHOUSE_D=17;
    /** The chest cells of the store's pages in the master's order {level, x, y, z, facing (0 east, 1 west, 2 north), right (1) or left (0)}:
     *  two cells a page (a double chest); page 0 is the stock chest (1,1,4) with (1,1,3). Pages by level: 2, 3, 6, 8, 9, 12. */
    static final int[][] WAREHOUSE_PAGES={
        {1,1,1,4,0,1},{1,1,1,3,0,0},{1,1,1,7,0,1},{1,1,1,6,0,0},
        {2,4,1,9,2,1},{2,3,1,9,2,0},
        {3,1,6,4,0,1},{3,1,6,3,0,0},{3,1,6,7,0,1},{3,1,6,6,0,0},{3,4,6,9,2,1},{3,3,6,9,2,0},
        {4,14,1,6,0,1},{4,14,1,5,0,0},{4,14,1,9,0,1},{4,14,1,8,0,0},
        {5,14,6,6,0,1},{5,14,6,5,0,0},
        {6,14,6,9,0,1},{6,14,6,8,0,0},{6,18,6,5,1,1},{6,18,6,6,1,0},{6,18,6,8,1,1},{6,18,6,9,1,0}};
    private static final String[] PAGE_FACING={"east","west","north"};
    /** A chest cell of a page: the stock chest's token with the facing and the half of its double chest. */
    static String pageChest(int[] c){return CHEST+"[facing="+PAGE_FACING[c[4]]+",type="+(c[5]==1?"right":"left")+"]";}
    /** The store's stations {level,x,y,z} of levels II..VI (states in WAREHOUSE_KIT_STATES): II two barrels by the back wall, III two barrels
     *  and the clerk's lectern upstairs, IV the wing's barrels and grindstone, V the stalls' straw and the feed barrel, VI the sorting desk
     *  (cartography table) and its lectern in the upper wing. */
    static final int[][] WAREHOUSE_KIT={{2,8,1,9},{2,9,1,9},{3,8,6,9},{3,9,6,9},{3,6,6,9},{4,18,1,10},{4,18,1,11},{4,17,1,11},{5,14,1,15},{5,18,1,15},{5,16,1,15},{6,16,6,10},{6,16,6,11}};
    static final String[] WAREHOUSE_KIT_STATES={"barrel[facing=up,open=false]","barrel[facing=up,open=false]","barrel[facing=up,open=false]","barrel[facing=up,open=false]",
        "lectern[facing=south,has_book=false,powered=false]","barrel[facing=up,open=false]","barrel[facing=up,open=false]","grindstone[face=floor,facing=north]",
        "hay_block[axis=y]","hay_block[axis=y]","barrel[facing=up,open=false]","cartography_table","lectern[facing=north,has_book=false,powered=false]"};
    /** The core cell of the store (AD-112), at the end of the aisle from the door. */
    static final int[] WAREHOUSE_CORE={6,1,9};
    /** VI: the category a page of the sorted store holds (WarehouseSort) and the block that marks it in the wall behind the page. */
    static final String[] WAREHOUSE_MARKS={"barrel[facing=up,open=false]","barrel[facing=up,open=false]","chiseled_stone_bricks","chiseled_stone_bricks","stripped_oak_log[axis=y]",
        "hay_block[axis=y]","hay_block[axis=y]","smooth_stone","smithing_table","white_wool","bookshelf","bookshelf"};
    /** The floor of a level: planks to II, stone brick with chiseled from III, chiseled and polished andesite at IV, deepslate brick at V, deepslate tile at VI. */
    private static String storeFloor(int level,int x,int z){boolean mark=(x+z)%2==0,accent=x%3==1&&z%3==1;
        return switch(level){case 1,2->PLANKS;case 3->mark?"stone_bricks":"chiseled_stone_bricks";case 4->mark?"chiseled_stone_bricks":"polished_andesite";
            case 5->mark?"deepslate_bricks":"polished_deepslate";default->accent?"chiseled_deepslate":"deepslate_tiles";};}
    private void warehouse(int level){
        // The roof covering of the level: dark oak shingle to III (no deepslate before a level-IV mine), deepslate brick at IV, deepslate tile from V.
        if(level>=5){rs="deepslate_tile_stairs";rf="deepslate_tiles";rc="deepslate_tile_slab[type=bottom]";}
        else if(level==4){rs="deepslate_brick_stairs";rf="deepslate_bricks";rc="deepslate_brick_slab[type=bottom]";}
        else slate();
        // The yard: grass, a gravel way from the street to the door and the bays, straw and logs by the barn.
        as(OTHER);for(int x=0;x<WAREHOUSE_W;x++)for(int z=0;z<WAREHOUSE_D;z++)set(x,0,z,"grass_block[snowy=false]");
        for(int z=11;z<=16;z++)set(6,0,z,"coarse_dirt");
        if(level>=6)storeCastle();
        else{storeBarn(level);if(level==2||level==3)storeShed();if(level>=4)storeWing(level);if(level>=5)storeHarness(level);}
        storeYard(level);
        storeFittings(level);
        // The shed's barrel remains part of the wing/castle wall: retain its inventory during III -> IV and later upgrades.
        if(level>=2){as(OTHER);set(13,1,8,"barrel[facing=up,open=false]");}
        slate();fill=PLASTER;
    }
    /** I..V: the barn x0..12, z1..10 - one storey to II, the jettied upper storey from III. */
    private void storeBarn(int level){
        boolean upper=level>=3;String plinth=level==1?"cobblestone":level==5?"polished_andesite":"stone_bricks";
        as(DECK);for(int x=0;x<=12;x++)for(int z=1;z<=10;z++){boolean in=x>=1&&x<=11&&z>=2&&z<=9;set(x,0,z,in?storeFloor(level,x,z):"cobblestone");}
        as(OTHER);for(int x=0;x<=12;x++)set(x,0,0,"cobblestone");
        // The ground storey: plaster in the dark oak frame, brick nogging from III, stone brick at V.
        fill=level>=5?"stone_bricks":level>=3?"bricks":PLASTER;
        face(true,1,1,false,0,level>=2?"POOOPSDSP1W4P":"P1W4PSDSP1W4P",2,5,false,2,plinth);
        face(true,10,-1,false,0,"P1W4PSWSP1W4P",2,5,false,2,plinth);
        face(false,0,1,false,1,"PSWPSSPWSP",2,5,false,2,plinth);
        face(false,12,-1,false,1,level>=4?"PSDPSSPSSP":"PSWPSSPWSP",2,5,false,2,plinth);
        fill=PLASTER;
        // II: the cart bay, three wide under a lintel and the plate, by the stock chest.
        if(level>=2){beamRun(true,4,1,1,3,BEAM);beamRun(true,5,1,1,3,BEAM);as(DECK);for(int x=1;x<=3;x++)set(x,0,1,"spruce_planks");}
        if(!upper){
            // I..II: tie beams with lamps, the roof from y6 over the whole barn, the street gable one block in under the overhanging end course.
            beamRun(true,5,4,1,11,BEAM);beamRun(true,5,7,1,11,BEAM);
            IntUnaryOperator h=x->6+Math.min(x,12-x);
            roof(true,0,12,0,10,6,(x,z)->false);
            fill="spruce_planks";gable(true,1,1,11,6,h,6,9,new int[]{3,7},new int[]{9,7});gable(true,10,1,11,6,h,6,9,new int[]{4,7},new int[]{8,7});fill=PLASTER;
            rake(true,0,0,12,h);barge(true,10,1,11,6,h);
            // The hay-loft door over the street door, open on straw.
            as(OTHER);for(int y=7;y<=8;y++)set(6,y,1,trapdoor("spruce","north",false,true));
            as(DECK);for(int x=5;x<=7;x++)for(int z=2;z<=3;z++)set(x,6,z,PLANKS);as(OTHER);set(6,7,2,"hay_block[axis=x]");
            set(3,4,4,hung());set(9,4,4,hung());set(3,4,7,hung());set(9,4,7,hung());
            return;}
        // III..V: the upper storey jettied over the street on a beam and knee brackets; the deck and a ceiling under the attic.
        beamRun(true,5,0,0,12,BEAM);for(int x:new int[]{0,4,8,12})brace(x,4,0,"south");
        as(DECK);for(int x=1;x<=11;x++)for(int z=2;z<=9;z++)if(!(x==11&&z>=5&&z<=7))set(x,5,z,level>=5?"spruce_planks":PLANKS);
        // V: the upper storey's panels in brick nogging as well (its ground storey is stone by then).
        fill=level>=5?"bricks":PLASTER;
        face(true,0,1,false,0,"P1W4PSWSP1W4P",6,9,false,7,null);
        face(true,10,-1,false,0,"PSWSPSWSPSWSP",6,9,false,7,null);
        face(false,0,1,false,0,"PSWSPSSPSWP",6,9,false,7,null);
        face(false,12,-1,false,0,"PSWSPSSPSWP",6,9,false,7,null);
        fill=PLASTER;
        as(DECK);for(int x=1;x<=11;x++)for(int z=1;z<=9;z++)set(x,9,z,PLANKS);
        // The stair up the east wall (3 air over every step), the well railed on the upper floor.
        as(OTHER);for(int i=0;i<4;i++){int z=8-i,y=1+i;set(11,y,z,stair("spruce_stairs","north",false));for(int s=1;s<y;s++)set(11,s,z,"spruce_planks");}
        for(int z=5;z<=7;z++)set(10,6,z,fence("spruce",false,z>5,z<7,false));
        IntUnaryOperator h=x->10+Math.min(x,12-x);
        roof(true,0,12,0,10,10,(x,z)->false);
        fill="spruce_planks";gable(true,0,1,11,10,h,6,-1,new int[]{3,11},new int[]{9,11});gable(true,10,1,11,10,h,6,-1,new int[]{4,11},new int[]{8,11});fill=PLASTER;
        barge(true,0,1,11,6,h);barge(true,10,1,11,6,h);
        // The hoist: the loft hatch in the street gable, the chain under the beam that carries the pulley.
        as(OTHER);for(int y=10;y<=11;y++)set(6,y,0,trapdoor("spruce","north",false,true));set(6,12,0,"chain[axis=y,waterlogged=false]");
        as(FRAME);set(6,13,0,log(POST,'z'));
        as(OTHER);set(3,4,4,hung());set(9,4,4,hung());set(3,4,7,hung());set(8,4,7,hung());set(3,8,4,hung());set(9,8,4,hung());set(3,8,7,hung());set(8,8,7,hung());set(2,4,0,hung());set(10,4,0,hung());
    }
    /** II..III: the lean-to shed on posts against the barn's east wall, its roof falling east; logs, straw and barrels under it. */
    private void storeShed(){
        as(DECK);for(int x=13;x<=16;x++)for(int z=2;z<=9;z++)set(x,0,z,"cobblestone");
        for(int z:new int[]{2,9})posts(16,z,1,2);beamRun(false,3,16,2,9,BEAM);
        as(ROOF);for(int z=2;z<=9;z++){set(13,5,z,stair(rs,"west",false));set(14,5,z,stair(rs,"west",false));set(15,4,z,stair(rs,"west",false));set(16,4,z,stair(rs,"west",false));}
        as(OTHER);for(int z=3;z<=5;z++){set(13,1,z,"oak_log[axis=z]");set(14,1,z,"oak_log[axis=z]");set(13,2,z,"spruce_log[axis=z]");}
        set(13,1,7,"hay_block[axis=y]");set(14,1,7,"hay_block[axis=z]");set(13,2,7,"hay_block[axis=x]");set(13,1,8,"barrel[facing=up,open=false]");set(14,4,6,hung());
    }
    /** IV..V: the east wing x13..19, z1..12 in two framed storeys under its own gable to the street, the cart gate (B2), doorways to the barn. */
    private void storeWing(int level){
        String plinth=level>=5?"polished_andesite":"stone_bricks";
        as(DECK);for(int x=13;x<=19;x++)for(int z=1;z<=12;z++){boolean in=x>=14&&x<=18&&z>=2&&z<=11;set(x,0,z,in?storeFloor(level,x,z):"cobblestone");}
        as(OTHER);for(int x=13;x<=19;x++)set(x,0,0,"cobblestone");
        fill=level>=5?"stone_bricks":"bricks";
        face(true,1,1,false,13,"PSOOOSP",2,5,false,2,plinth);
        face(true,12,-1,false,13,level>=5?"PSSDSSP":"PSWSWSP",2,5,false,2,plinth);
        face(false,19,-1,false,1,"PSWSPSSPSWSP",2,5,false,2,plinth);
        face(false,13,1,false,1,"PSDPSSSPSSSP",2,5,false,2,plinth);
        fill=PLASTER;
        beamRun(true,4,1,15,17,BEAM);beamRun(true,5,1,15,17,BEAM);as(DECK);for(int x=15;x<=17;x++)set(x,0,1,"spruce_planks");
        as(DECK);for(int x=14;x<=18;x++)for(int z=2;z<=11;z++)set(x,5,z,level>=5?"spruce_planks":PLANKS);
        fill=level>=5?"bricks":PLASTER;
        face(true,1,1,false,13,"PSWSWSP",6,9,false,7,null);
        face(true,12,-1,false,13,"PSWSWSP",6,9,false,7,null);
        face(false,19,-1,false,1,"PSWSPSSPSWSP",6,9,false,7,null);
        face(false,13,1,false,1,"PSSPSSSPSSSP",6,9,false,7,null);
        fill=PLASTER;
        as(OTHER);for(int y=6;y<=7;y++){set(12,y,3,"air");set(13,y,3,"air");}
        as(FRAME);set(12,8,3,log(BEAM,'x'));set(13,8,3,log(BEAM,'x'));
        as(DECK);for(int x=14;x<=18;x++)for(int z=2;z<=11;z++)set(x,9,z,PLANKS);
        IntUnaryOperator h=x->10+Math.min(x-13,19-x);
        roof(true,13,19,0,12,10,(x,z)->false);
        fill="spruce_planks";gable(true,1,14,18,10,h,16,-1,new int[]{15,11},new int[]{17,11});gable(true,12,14,18,10,h,16,-1);fill=PLASTER;
        rake(true,0,13,19,h);barge(true,12,14,18,16,h);
        as(OTHER);set(16,4,4,hung());set(16,4,8,hung());set(16,8,4,hung());set(16,8,8,hung());set(16,4,0,hung());
    }
    /** V: the harness room behind the wing: two wolf stalls with straw and a feeder, harness chains, the opening to the east lane. */
    private void storeHarness(int level){
        as(DECK);for(int x=13;x<=19;x++)for(int z=13;z<=16;z++){boolean in=x>=14&&x<=18&&z>=13&&z<=15;set(x,0,z,in?"spruce_planks":"cobblestone");}
        fill="stone_bricks";
        face(false,13,1,false,13,"SWSP",2,4,false,2,"polished_andesite");
        face(false,19,-1,false,13,"SOOP",2,4,false,2,"polished_andesite");
        face(true,16,-1,false,13,"PSWSWSP",2,4,false,2,"polished_andesite");
        fill=PLASTER;
        beamRun(false,4,19,14,15,BEAM);as(DECK);for(int z=14;z<=15;z++)set(19,0,z,"spruce_planks");
        // A pent roof falling south from the wing's back wall, its ends closed in plaster.
        as(ROOF);for(int x=13;x<=19;x++){set(x,8,13,stair(rs,"north",false));set(x,7,14,stair(rs,"north",false));set(x,6,15,stair(rs,"north",false));set(x,5,16,stair(rs,"north",false));}
        for(int x:new int[]{13,19}){as(INFILL);for(int y=5;y<=7;y++)set(x,y,13,PLASTER);for(int y=5;y<=6;y++)set(x,y,14,PLASTER);set(x,5,15,PLASTER);}
        beamRun(true,5,14,14,18,BEAM);
        as(OTHER);set(15,4,14,"chain[axis=y,waterlogged=false]");set(17,4,14,"chain[axis=y,waterlogged=false]");set(16,4,14,hung());
        set(14,1,14,"brown_carpet");set(18,1,14,"brown_carpet");set(15,1,15,"spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");set(17,1,15,"spruce_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]");
    }
    /** Castle masonry of the store (VI): the hall's clusters - cobblestone and andesite at the foot, stone brick above. */
    private static String castle(int x,int y,int z){int r=hash(x>>1,y>>1,z>>1);
        if(y<=1)return r<30?"andesite":"cobblestone";if(y<=3)return r<22?"cobblestone":r<30?"andesite":"stone_bricks";return "stone_bricks";}
    /** A castle wall from y0 to y1 along one face: the masonry, a polished andesite course at y5, chiseled quoins on every other course at its ends. */
    private void keepWall(boolean alongX,int p,int a0,int a1,int y0,int y1){
        for(int a=a0;a<=a1;a++)for(int y=y0;y<=y1;y++){int x=alongX?a:p,z=alongX?p:a;boolean end=a==a0||a==a1;as(y==1?PLINTH:INFILL);
            set(x,y,z,y==5?"polished_andesite":end&&y%2==0?"chiseled_stone_bricks":castle(x,y,z));}}
    /** A merlon row 1:1 two high on top of a castle wall. */
    private void crenel(boolean alongX,int p,int a0,int a1,int y){as(STONE);for(int a=a0;a<=a1;a++){int x=alongX?a:p,z=alongX?p:a;set(x,y,z,"stone_bricks");if((a-a0)%2==0)set(x,y+1,z,"stone_bricks");}}
    /** A slit window two high with panes in a castle face. */
    private void slitPane(int x,int y0,int z,boolean alongX){as(OTHER);for(int y=y0;y<=y0+1;y++)set(x,y,z,pane(alongX));}
    /** VI: the castle store - the barn and the wing in masonry, the harness room, the round tower on the street-east corner. */
    private void storeCastle(){
        // The barn: two storeys of masonry to the plate (y9), the arched cart bay and door, the big gable roof in deepslate tile.
        as(DECK);for(int x=0;x<=12;x++)for(int z=1;z<=10;z++){boolean in=x>=1&&x<=11&&z>=2&&z<=9;set(x,0,z,in?storeFloor(6,x,z):"cobblestone");}
        as(OTHER);for(int x=0;x<=12;x++)set(x,0,0,"cobblestone");
        keepWall(true,1,0,12,1,9);keepWall(true,10,0,12,1,9);keepWall(false,0,2,9,1,9);keepWall(false,12,2,9,1,9);
        as(OTHER);for(int x=1;x<=3;x++)for(int y=1;y<=3;y++)set(x,y,1,"air");
        as(FRAME);set(1,3,1,stair("stone_brick_stairs","west",true));set(3,3,1,stair("stone_brick_stairs","east",true));set(2,4,1,"chiseled_stone_bricks");
        as(DECK);for(int x=1;x<=3;x++)set(x,0,1,"spruce_planks");
        arch(6,3,1);
        for(int x:new int[]{9,11}){slitPane(x,2,1,true);slitPane(x,7,1,true);}slitPane(2,7,1,true);
        for(int x:new int[]{2,8,10}){slitPane(x,2,10,true);slitPane(x,7,10,true);}
        for(int z:new int[]{5,8}){slitPane(0,2,z,false);slitPane(0,7,z,false);}slitPane(12,7,8,false);slitPane(12,2,8,false);
        as(OTHER);for(int y=1;y<=2;y++)set(12,y,3,"air");for(int y=6;y<=7;y++)set(12,y,3,"air");
        as(DECK);for(int x=1;x<=11;x++)for(int z=2;z<=9;z++)if(!(x==11&&z>=5&&z<=7))set(x,5,z,"spruce_planks");
        for(int x=1;x<=11;x++)for(int z=2;z<=9;z++)set(x,9,z,PLANKS);
        as(OTHER);for(int i=0;i<4;i++){int z=8-i,y=1+i;set(11,y,z,stair("stone_brick_stairs","north",false));for(int s=1;s<y;s++)set(11,s,z,"stone_bricks");}
        for(int z=5;z<=7;z++)set(10,6,z,fence("dark_oak",false,z>5,z<7,false));
        IntUnaryOperator h=x->10+Math.min(x,12-x);
        roof(true,0,12,0,10,10,(x,z)->false);
        fill="stone_bricks";gable(true,1,1,11,10,h,6,-1,new int[]{6,12});gable(true,10,1,11,10,h,6,-1,new int[]{6,12});fill=PLASTER;
        // Stone gables: no king post in the masonry, the ridge column laid in stone round its window.
        as(INFILL);for(int z:new int[]{1,10})for(int y=10;y<=14;y++)if(y!=12)set(6,y,z,"stone_bricks");
        rake(true,0,0,12,h);barge(true,10,1,11,6,h);
        as(OTHER);set(3,4,4,hung());set(9,4,4,hung());set(3,4,7,hung());set(8,4,7,hung());set(3,8,4,hung());set(9,8,4,hung());set(3,8,7,hung());set(8,8,7,hung());
        // The wing: masonry to y10 under a flat roof with a crenellated parapet; the arched cart gate (B2).
        as(DECK);for(int x=13;x<=19;x++)for(int z=1;z<=12;z++){boolean in=x>=14&&x<=18&&z>=2&&z<=11;set(x,0,z,in?storeFloor(6,x,z):"cobblestone");}
        as(OTHER);for(int x=13;x<=19;x++)set(x,0,0,"cobblestone");
        keepWall(true,1,13,19,1,10);keepWall(true,12,13,19,1,10);keepWall(false,13,2,11,1,10);keepWall(false,19,2,11,1,10);
        as(OTHER);for(int x=15;x<=17;x++)for(int y=1;y<=3;y++)set(x,y,1,"air");
        as(FRAME);set(15,3,1,stair("stone_brick_stairs","west",true));set(17,3,1,stair("stone_brick_stairs","east",true));set(16,4,1,"chiseled_stone_bricks");
        as(DECK);for(int x=15;x<=17;x++)set(x,0,1,"spruce_planks");
        as(OTHER);for(int y=1;y<=2;y++){set(13,y,3,"air");set(16,y,12,"air");}for(int y=6;y<=7;y++)set(13,y,3,"air");
        slitPane(15,7,1,true);slitPane(17,7,1,true);for(int x:new int[]{15,17}){slitPane(x,2,12,true);slitPane(x,7,12,true);}
        for(int z:new int[]{7,10}){slitPane(19,2,z,false);slitPane(19,7,z,false);}
        as(DECK);for(int x=14;x<=18;x++)for(int z=2;z<=11;z++){set(x,5,z,"spruce_planks");set(x,10,z,"stone_bricks");}
        crenel(true,1,13,19,11);crenel(true,12,13,19,11);crenel(false,13,2,11,11);crenel(false,19,2,11,11);
        as(OTHER);set(16,4,5,hung());set(16,4,9,hung());set(16,9,5,hung());set(16,9,9,hung());
        // The harness room: masonry under a flat roof and a parapet, the stalls, the opening to the east lane.
        as(DECK);for(int x=13;x<=19;x++)for(int z=13;z<=16;z++){boolean in=x>=14&&x<=18&&z>=13&&z<=15;set(x,0,z,in?storeFloor(6,x,z):"cobblestone");}
        keepWall(false,13,13,16,1,5);keepWall(false,19,13,16,1,5);keepWall(true,16,13,19,1,5);
        as(OTHER);for(int z=14;z<=15;z++)for(int y=1;y<=3;y++)set(19,y,z,"air");
        as(FRAME);set(19,3,14,stair("stone_brick_stairs","north",true));set(19,3,15,stair("stone_brick_stairs","south",true));
        slitPane(16,2,16,true);
        as(DECK);for(int x=14;x<=18;x++)for(int z=13;z<=15;z++)set(x,6,z,"stone_bricks");
        as(STONE);for(int x=13;x<=19;x++)set(x,6,16,"stone_bricks");for(int z=13;z<=15;z++){set(13,6,z,"stone_bricks");set(19,6,z,"stone_bricks");}
        crenel(true,16,13,19,7);crenel(false,13,13,15,7);crenel(false,19,13,15,7);
        as(OTHER);set(15,5,14,"chain[axis=y,waterlogged=false]");set(17,5,14,"chain[axis=y,waterlogged=false]");set(16,5,14,hung());
        set(14,1,14,"brown_carpet");set(18,1,14,"brown_carpet");
        // The round tower (d5) on the street-east corner: masonry to y16, floors at y5 and y10, slits, a deepslate tile cone on its rim.
        final int cx=20,cz=2;final double r2=5.0;
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){if(!disc(x,z,cx,cz,r2))continue;
            boolean rim=!disc(x+1,z,cx,cz,r2)||!disc(x-1,z,cx,cz,r2)||!disc(x,z+1,cx,cz,r2)||!disc(x,z-1,cx,cz,r2);
            as(DECK);set(x,0,z,"cobblestone");
            for(int y=1;y<=16;y++){if(rim){as(y==1?PLINTH:INFILL);set(x,y,z,y==5||y==11?"polished_andesite":castle(x,y,z));}else{as(OTHER);set(x,y,z,"air");}}
            if(!rim){as(DECK);set(x,5,z,PLANKS);set(x,10,z,PLANKS);}}
        as(OTHER);for(int y=1;y<=2;y++)set(18,y,2,"air");
        for(int y:new int[]{3,8,13}){set(cx,y,cz-2,pane(true));set(cx+2,y,cz,pane(false));}
        set(cx,4,cz,hung());set(cx,9,cz,hung());set(cx,15,cz,hung());
        towerCone(cx,cz,17);
        // The sorted store's labels: the mark of every page's category stands on its chests (WarehouseSort).
        for(int i=0;i<WAREHOUSE_PAGES.length;i++){var c=WAREHOUSE_PAGES[i];as(OTHER);set(c[1],c[2]+1,c[3],WAREHOUSE_MARKS[i/2]);}
    }
    /** The cone of a d5 tower in the level's roof covering, sitting on its rim (the lot leaves no room for an eave beyond it): the rim ring of
     *  stairs, the inner ring a course higher on tiles, the middle column of tiles under a slab cap as its tip. */
    private void towerCone(int cx,int cz,int y0){
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){int r=dx*dx+dz*dz;if(r>5)continue;int x=cx+dx,z=cz+dz;String f=inward(dx,dz);as(ROOF);
            if(r==0){for(int y=y0;y<=y0+1;y++)set(x,y,z,rf);set(x,y0+2,z,rc);}
            else if(r<=2){set(x,y0,z,rf);set(x,y0+1,z,stair(rs,f,false));}
            else set(x,y0,z,stair(rs,f,false));}
    }
    /** The yard of a level: straw and a log pile at I..III, a lantern post by the lane from IV, a trodden way to the back. */
    private void storeYard(int level){
        as(OTHER);
        if(level<=3){set(19,1,3,"oak_log[axis=z]");set(19,1,4,"oak_log[axis=z]");set(20,1,3,"oak_log[axis=z]");set(20,1,4,"oak_log[axis=z]");set(19,2,3,"stripped_oak_log[axis=z]");
            set(20,1,8,"hay_block[axis=y]");set(20,1,9,"hay_block[axis=x]");set(20,2,8,"hay_block[axis=z]");}
        for(int z=0;z<=16;z++)if(level<6||z>=5)set(21,0,z,"coarse_dirt");
        lanternPost(22,level>=6?7:1);
        set(10,1,13,"hay_block[axis=y]");set(11,1,13,"hay_block[axis=x]");set(2,1,14,"barrel[facing=up,open=false]");set(3,1,14,"barrel[facing=up,open=false]");
    }
    /** The functional cells of every level: the door, the page chests of the level, the stations of II..level kept free, the core cell. */
    private void storeFittings(int level){
        door(6,1);
        // Only the stock chest (1,1,4) is built: the other page chests of the level (their cells kept free here) are set down and linked by
        // WarehouseStorage once the level stands, as the hall's pages are (one physical chest a design, SettlementGameTests).
        as(KIT);for(int i=0;i<WAREHOUSE_PAGES.length;i++){var c=WAREHOUSE_PAGES[i];if(c[0]<=level)set(c[1],c[2],c[3],i==0?CHEST:"air");}
        for(var k:WAREHOUSE_KIT)if(k[0]<=level)set(k[1],k[2],k[3],"air");
        set(WAREHOUSE_CORE[0],WAREHOUSE_CORE[1],WAREHOUSE_CORE[2],"air");
    }

    // ---------- laboratory: 11x11 hall with a round tower ----------
    /** A two-plane framed hall along the street (paired windows, knee braces at the door posts), gables one block in; engaged in its back wall a
     *  round masonry tower (castle D-tower, d5) with 1x2 windows to every side, an upper floor, and a 2:1 slate cone on corbels with a spire. Behind
     *  the hall, a walled garden. The lectern and the scientist's desk cell (2,1,4) stay; the table moves from the yard corner into the hall.
     *  AD-154: every scientist place has a desk of its own (LabDesks: the lectern, the desk row x6..9, level VI's lectern). */
    private void laboratory(){
        final int cx=7,cz=7;final double r2=5.0;
        for(int x=0;x<11;x++)for(int z=0;z<11;z++){boolean hall=x>=1&&x<=9&&z>=1&&z<=5,tower=disc(x,z,cx,cz,r2);
            as(hall||tower?DECK:OTHER);set(x,0,z,hall?PLANKS:tower?"stone_bricks":"cobblestone");}
        IntUnaryOperator h=z->6+Math.min(z,6-z);
        roof(false,0,6,0,10,6,(x,z)->disc(x,z,cx,cz,r2));
        face(true,0,1,true,0,"PWW4PDP1WWP",2,5,true,3,"cobblestone");
        face(false,0,1,false,0,"PSWPWSP",2,5,false,3,"cobblestone");
        face(false,10,-1,false,0,"PSWPWSP",2,5,false,3,"cobblestone");
        face(true,6,-1,false,0,"PSWSPCCCCCP",2,5,false,3,"cobblestone");
        beamRun(false,5,1,1,5,BEAM);beamRun(false,5,9,1,5,BEAM);
        // Round 1 review: the gables one block in over the plates, the end courses overhanging them on a raked soffit, a pentice on the ledge.
        gable(false,1,1,5,6,h,3,-1,new int[]{2,6},new int[]{4,6});gable(false,9,1,5,6,h,3,-1,new int[]{2,6},new int[]{4,6});
        rake(false,0,0,6,h);pentice(false,0,1,5,6,"east");rake(false,10,0,6,h);pentice(false,10,1,5,6,"west");
        beamRun(false,5,3,1,5,POST);
        // Design review 2026-09-24: a second tie beam at x7, from the front plate to the tower (late but its plate end), so the hall's east lamp
        // hangs from timber, not from air, and the beams stand either side of the door.
        as(FRAME);set(7,5,1,log(POST,'z'));
        // The tower: masonry ring to y12, a door from the hall, windows low and high, an upper floor, the cone.
        for(int x=cx-2;x<=cx+2;x++)for(int z=cz-2;z<=cz+2;z++){if(!disc(x,z,cx,cz,r2))continue;
            boolean rim=!disc(x+1,z,cx,cz,r2)||!disc(x-1,z,cx,cz,r2)||!disc(x,z+1,cx,cz,r2)||!disc(x,z-1,cx,cz,r2);
            for(int y=1;y<=12;y++){if(rim){as(y==1?PLINTH:INFILL);set(x,y,z,mas(x,y,z,true));}else{as(OTHER);set(x,y,z,"air");}}
            if(!rim){as(DECK);set(x,8,z,PLANKS);}}
        as(OTHER);for(int y=1;y<=2;y++)set(cx,y,cz-2,"air");
        // Tall narrow lancets high up to every side (AD-122 review), a low one to the garden.
        for(int y:new int[]{3,4}){pane(cx,y,cz+2,true);pane(cx+2,y,cz,false);}
        for(int y=8;y<=11;y++){pane(cx,y,cz+2,true);pane(cx-2,y,cz,false);pane(cx+2,y,cz,false);pane(cx,y,cz-2,true);}
        cone(cx,cz,Math.sqrt(5)+1,13);
        // Design review 2026-09-24: the corner stairs of the cone's rings in the shape the game gives them.
        for(int[] c:new int[][]{{5,13,5,1},{9,13,9,1},{6,13,9,1},{6,14,9,1},{8,13,5,1},{8,14,5,1},{5,13,9,0},{9,13,5,0},{6,13,5,0},{6,14,5,0},{8,13,9,0},{8,14,9,0}})
            cornerC(c[0],c[1],c[2],c[3]==1?"outer_right":"outer_left");
        // The upper lancets start at the tower's deck: their inner side joins it.
        as(OTHER);set(7,8,5,pane(true));set(7,8,9,pane(true));
        set(5,8,7,pane(false));set(9,8,7,pane(false));
        // The walled garden behind the hall.
        var run=new ArrayList<int[]>();for(int z=7;z<=10;z++){run.add(new int[]{0,z});run.add(new int[]{10,z});}for(int x=1;x<=9;x++)run.add(new int[]{x,10});lowWall(run);
        lanternPost(2,8);
        as(OTHER);set(3,4,3,hung());set(7,4,2,hung());set(cx,7,cz,hung());set(cx,12,cz,hung());set(6,1,3,"lantern[hanging=false]");
        door(5,0);as(KIT);set(1,1,4,CHEST);set(3,1,3,"lectern[facing=north,has_book=false,powered=false]");set(9,1,5,"crafting_table");
        // Design review 2026-09-24: the lamp that stood on the floor stands on a desk now; a spruce board from the door to the lectern.
        as(OTHER);set(6,1,3,table("dark_oak",true,false,false,false));as(DECK);for(int z=1;z<=4;z++)set(5,0,z,"spruce_planks");
        decorC(LABORATORY_DECOR);
    }
    /** Design review 2026-09-24: the laboratory's late cells — the lamp on the desk, a work bench of two tables with a stool and a pot by the
     *  east windows, tall book cases either side of the entrance and in the corner by the stock (its east side free), the east tie beam, a lamp
     *  in the frame bay over the door, and the study in the tower's upper room (book cases, a table with a pot, a chair) seen through its lancets. */
    private static final Object[][] LABORATORY_DECOR={{6,2,3,"lantern[hanging=false]"},{8,1,3,table("dark_oak",true,false,false,true)},{9,1,3,table("dark_oak",false,false,false,true)},
        // AD-148: one book case by the entrance only, so the frozen cells (3|9,1,2) keep their way from the door. AD-154: the chair west of the
        // bench became a table — the desk row x6..9 holds four of the six scientists' desks (LabDesks).
        {7,1,3,table("dark_oak",true,false,false,true)},{9,2,3,"potted_dandelion"},{1,1,5,"bookshelf"},{1,2,5,"bookshelf"},{2,1,2,"bookshelf"},{2,2,2,"bookshelf"},
        {7,5,2,log(POST,'z')},{7,5,3,log(POST,'z')},{7,5,4,log(POST,'z')},{5,4,0,hung()},
        {6,9,6,"bookshelf"},{8,9,6,"bookshelf"},{7,9,7,table("dark_oak",false,false,false,false)},{7,10,7,"potted_poppy"},{7,9,8,chair("dark_oak","north")}};
    static{late("laboratory",decorCellsC(LABORATORY_DECOR));}

    // ---------- batch 4: farmstead and yards. Working buildings under spruce shingle (farm, sawmill, byre) or slate (office, caravanserai); walls
    // stay on the lot edge where the door, the stock chest and the stations stand, the overhang comes from gables set in, hips and catslides.
    // Equipment of levels II..VI goes to covered floor cells with two free cells over them, so every deck up in a roof is kept full or capped. ----------
    private void shingle(){rs="spruce_stairs";rf="spruce_planks";rc="spruce_slab[type=bottom]";}
    private void slate(){rs=TILE;rf=TILES;rc=CAP;}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    /** Fence at y1 along a run, each post joined to the rest of the run and to a solid block or a gate in line beside it (unnamed sides stay false). */
    private void fences(List<int[]> run,String block){
        var at=new HashSet<Long>();for(var c:run)at.add(key(c[0],c[1]));
        String[] side={"east","north","south","west"};int[][] step={{1,0},{0,-1},{0,1},{-1,0}};
        as(OTHER);for(var c:run){var sides=new ArrayList<String>();
            for(int i=0;i<4;i++){int x=c[0]+step[i][0],z=c[1]+step[i][1];if(at.contains(key(x,z))||joins(x,z,step[i][0]!=0))sides.add(side[i]+"=true");}
            set(c[0],1,c[1],sides.isEmpty()?block:block+"["+String.join(",",sides)+"]");}
    }
    private boolean joins(int x,int z,boolean alongX){String s=m.get(new Cell(x,1,z));if(s==null||s.equals("air"))return false;
        if(s.contains("fence_gate"))return alongX==(s.contains("facing=north")||s.contains("facing=south"));
        for(var part:new String[]{"stairs","slab","pane","lantern","door","_wall","chain","fence","bed","composter",CHEST})if(s.contains(part))return false;
        return true;}
    private static final String POLE="dark_oak_fence";

    // ---------- farm: 7x7 barn, gable to the street under a gambrel shingle roof ----------
    /** AD-122 review: a barn, not a hipped box — a spruce shingle gambrel (steep lower slopes, shallow upper ones) over closed gables in the wall
     *  planes, a barn door three wide and three high in the street gable (the house door in the middle, board leaves standing open either side), the
     *  hay loft over it with its hatch open on the hay. The composter, the hay, the table and the stock chest stay where they were. */
    private void farm(){
        // Round 1 review: one dark oak roof over a dark oak frame (the spruce read lighter than the frame), a 45-degree stair roof whose street end
        // overhangs the barn front on a rake, the gable framed (king post, collar, the loft hatch) instead of plaster fins; under the overhang the
        // farm's goods — straw, a composter, pumpkins — either side of the barn door.
        as(DECK);for(int x=0;x<7;x++)for(int z=0;z<7;z++){boolean in=x>=1&&x<=5&&z>=2&&z<=5;set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=x->6+Math.min(x,6-x);
        roof(true,0,6,0,6,6,(x,z)->false);
        // The barn front one block in: the barn door three wide (the house door in the middle, board leaves open either side) under a lintel.
        face(true,1,1,false,0,"PWOOOWP",2,5,false,2,"cobblestone");
        as(PLINTH);set(2,1,1,"cobblestone");set(4,1,1,"cobblestone");
        as(FRAME);beamRun(true,4,1,2,4,BEAM);beamRun(true,5,1,2,4,BEAM);
        // Style merge (farm probe): the leaves stand swung open across the jambs (panels at the outer edges of the opening), not in the wall plane,
        // where an open trapdoor reads to a pathfinder as a way in but its panel closes the cell — the farmer stepped onto the plinth and stuck.
        // AD-144 (owner 2026-09-23: nothing that needs joining by a door): the leaves gave way to dark oak jamb posts either side of the door on
        // the plinth, the house door between them under a transom beam (a bare cell over the door read as a hole in the barn front).
        as(FRAME);for(int x:new int[]{2,4})for(int y=2;y<=3;y++)set(x,y,1,log(POST,'y'));set(3,3,1,log(BEAM,'x'));
        as(OTHER);for(int x:new int[]{2,4})for(int y=1;y<=3;y++)set(x,y,0,trapdoor("spruce","north",false,true));
        face(true,6,-1,false,0,"PSWPWSP",2,5,false,2,"cobblestone");
        // All-sides review ("the side walls read as windows stepping down a stair"): both long sides the same — a window at each end, plaster between.
        face(false,0,1,false,1,"PWSSWP",2,5,false,2,"cobblestone");
        face(false,6,-1,false,1,"PWSSWP",2,5,false,2,"cobblestone");
        // Gables: the street one one block in under the overhanging end course, a pentice hood over the barn door, the loft hatch over it.
        // Round 2 review ("the roof steps unevenly at the gable"): the street gable jettied one block over the barn front on a beam and two
        // brackets, closed in the plane of the end course under a barge board, the loft hatch in it open on the hay.
        beamRun(true,5,0,0,6,BEAM);brace(0,4,0,"south");brace(6,4,0,"south");
        gable(true,0,1,5,6,h,3,-1,new int[]{2,7},new int[]{4,7});barge(true,0,1,5,3,h);
        gable(true,6,1,5,6,h,3,-1,new int[]{2,6},new int[]{4,6});barge(true,6,1,5,3,h);
        as(OTHER);set(3,6,0,trapdoor("spruce","north",false,true));set(3,6,1,"barrel[facing=up,open=false]");
        for(int x=1;x<=5;x++)set(x,5,2,PLANKS);
        beamRun(true,6,4,1,5,POST);
        // The goods under the overhang.
        // Round 2 review ("clutter piled against the door"): the way to the barn door clear, storage at each corner post only.
        as(OTHER);set(0,1,0,"barrel[facing=up,open=false]");set(6,1,0,"barrel[facing=up,open=false]");
        set(3,4,0,hung());set(3,4,4,hung());
        door(3,1);as(KIT);set(1,1,4,CHEST);set(1,1,2,"composter");for(int z=2;z<=4;z++)set(5,1,z,"barrel[facing=up,open=false]");set(5,1,5,"crafting_table");
        as(OTHER);for(var e:FARM_FURNITURE.entrySet())set(e.getKey().x(),e.getKey().y(),e.getKey().z(),e.getValue());
    }
    /** Design review 2026-09-24 (lateFurniture): the lamp over the barn floor hung from the tie beam by a fence post (it hung in the air), a
     *  stack of empty grain barrels in the free back corner and both ends of the loft. */
    private static final Map<Cell,String> FARM_FURNITURE=Map.of(new Cell(3,5,4),fence("dark_oak",false,false,false,false),new Cell(1,1,5),"barrel[facing=up,open=false]",
        new Cell(1,2,5),"barrel[facing=up,open=false]",new Cell(1,6,2),"barrel[facing=up,open=false]",new Cell(5,6,2),"barrel[facing=up,open=false]");
    static{late("farm",FARM_FURNITURE.keySet());}

    // ---------- forester (AD-131): the forester's hut I..VI on its 15x21 lot, eaves to the street ----------
    /** AD-131: every level is a plan of its own on the same lot, the door (4,1,0), the stock chest (1,1,4) and the core (5,1,3) in the same cells.
     *  I - a log cabin 9x6 of spruce logs laid along each wall on a cobble plinth, dark oak corner posts, boarded gables under spruce barge boards
     *  and a dark oak shingle roof ridged along the street, both eaves on the walls (design review 2026-09-24); a woodpile along the street and a chopping stump.
     *  II - a lean-to woodshed on posts against its east gable (x9..12). III - a lower sapling room behind it (x1..7, z6..9) with a back door.
     *  IV - the shed rebuilt as an open sawmill hall on braced posts (x9..14, z0..9) under its own gable, the saw on a log carriage in it.
     *  V - a framed and plastered upper storey on the cabin under a raised roof, windows in its gables; a covered gallery (x13..14, z10..14).
     *  VI - a closed courtyard: the west cloister (x0..1), the east gallery run on to z18 and the drying hall at the back (z19..20), all under pent
     *  roofs no higher than y5; the courtyard x2..12, z10..18 holds nothing above its ground (ForesterHut.courtyard), its six grove cells on dirt.
     *  Stations of a level are air cells named KIT here and filled by LevelArchitecture from ForesterHut.kit, so they stay free in every plan
     *  from their level on. LevelArchitecture lays the level's materials by the roles (plinth, deck, roof, plaster, frame) as for every design. */
    private void forester(int level){
        slate();
        // Ground: grass over the whole lot; the courtyard's grove cells on bare dirt, a gravel walk between its rows (VI).
        as(OTHER);for(int x=0;x<15;x++)for(int z=0;z<21;z++){String g="grass_block[snowy=false]";
            if(level>=6&&forestCourtyard(x,z)){if(forestGrove(x,z))g="dirt";else if(z==14)g="gravel";}
            set(x,0,z,g);}
        boolean upper=level>=5;int top=upper?4:3;
        // The cabin's floor: planks inside, cobblestone under the walls.
        as(DECK);for(int x=0;x<=8;x++)for(int z=0;z<=5;z++)set(x,0,z,x>=1&&x<=7&&z>=1&&z<=4?"spruce_planks":"cobblestone");
        // Plinth course and log walls, the logs laid along each wall; dark oak posts at the corners.
        as(PLINTH);for(int x=0;x<=8;x++){if(x!=4)set(x,1,0,"cobblestone");if(x!=4||level<3)set(x,1,5,"cobblestone");}
        for(int z=1;z<=4;z++){set(0,1,z,"cobblestone");set(8,1,z,"cobblestone");}
        // Design review 2026-09-24 ("the ridge and the eaves must sit square on their own volume"): the cabin is six deep (z0..5), so its roof
        // is an even span over exactly those six rows, both eaves on the walls and both walls one height — the old odd span ridged at z3 hung
        // one course over the yard behind and none over the street, and its gables stood half a block off the walls under them. The side
        // windows go in pairs on the axis of the six-deep walls, the back one gets the mate of the front pair.
        as(FRAME);for(int x=1;x<=7;x++){for(int y=2;y<=top;y++){set(x,y,0,log("spruce_log",'x'));set(x,y,5,log("spruce_log",'x'));}}
        for(int z=1;z<=4;z++)for(int y=2;y<=4;y++){set(0,y,z,log("spruce_log",'z'));set(8,y,z,log("spruce_log",'z'));}
        for(int[] c:new int[][]{{0,0},{8,0},{0,5},{8,5}})posts(c[0],c[1],2,c[1]==0?top:4);
        // AD-144: the log cabin's windows framed in spruce, the wood of its walls; design review 2026-09-24: a pair to every face.
        frame="spruce";as(OTHER);set(2,2,0,pane(true));set(6,2,0,pane(true));set(2,2,5,pane(true));set(6,2,5,pane(true));
        for(int z=2;z<=3;z++){set(0,2,z,pane(false));set(8,2,z,pane(false));}frame=org.villageastra.domain.FramedWindows.DEFAULT_WOOD;
        if(!upper){
            roof(false,0,5,0,8,4,(x,z)->false);IntUnaryOperator h=z->4+Math.min(z,5-z);
            fill="spruce_planks";gable(false,0,1,4,5,h,-1,-1);gable(false,8,1,4,5,h,-1,-1);
            barge(false,0,1,4,-1,h);barge(false,8,1,4,-1,h);fill=PLASTER;
            beamRun(false,4,2,1,4,BEAM);beamRun(false,4,6,1,4,BEAM);
            as(OTHER);set(4,5,3,hung());
        }else{
            // V: the cabin walls to their plate (y4), a plank deck, a framed plastered storey on it and the roof raised over it; the storey's
            // four walls one height (plate y8), its windows on one course (y6..7), a pair on the axis of each gable wall.
            as(FRAME);for(int x=1;x<=7;x++){set(x,4,0,log("spruce_log",'x'));set(x,4,5,log("spruce_log",'x'));}
            as(DECK);for(int x=0;x<=8;x++)for(int z=0;z<=5;z++)set(x,5,z,"spruce_planks");
            face(true,0,1,false,0,"PSWSPSWSP",6,8,false,6,null);face(true,5,-1,false,0,"PSWSPSWSP",6,8,false,6,null);
            face(false,0,1,false,1,"SWWS",6,8,false,6,null);face(false,8,-1,false,1,"SWWS",6,8,false,6,null);
            roof(false,0,5,0,8,9,(x,z)->false);IntUnaryOperator h=z->9+Math.min(z,5-z);
            fill="spruce_planks";gable(false,0,1,4,9,h,-1,-1);gable(false,8,1,4,9,h,-1,-1);
            barge(false,0,1,4,-1,h);barge(false,8,1,4,-1,h);fill=PLASTER;
            as(OTHER);set(4,4,3,hung());set(4,10,3,hung());
            FORESTER_LOFT.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
        }
        // I: the woodpile along the street (bark logs, stripped ones on top) and the chopping stump.
        as(OTHER);for(int x=11;x<=13;x++)set(x,1,0,"oak_log[axis=x]");for(int x=11;x<=12;x++)set(x,2,0,"stripped_oak_log[axis=x]");
        set(10,1,2,"stripped_oak_log[axis=y]");set(10,2,2,"oak_slab[type=bottom,waterlogged=false]");
        if(level==2||level==3){
            // II: the lean-to woodshed on two braced posts, its pent roof falling to the east; logs stacked under it.
            as(DECK);for(int x=9;x<=12;x++)for(int z=0;z<=5;z++)if(x!=10||z!=2)set(x,0,z,"cobblestone");
            posts(12,0,1,3);posts(12,5,1,3);beamRun(false,4,12,0,5,BEAM);brace(12,3,1,"north");brace(12,3,4,"south");
            as(ROOF);for(int z=0;z<=5;z++){set(12,5,z,stair(rs,"west",false));set(11,5,z,stair(rs,"west",false));set(10,5,z,rf);set(10,6,z,stair(rs,"west",false));set(9,5,z,rf);set(9,6,z,stair(rs,"west",false));}
            as(OTHER);for(int z=3;z<=4;z++){set(10,1,z,"oak_log[axis=z]");set(10,2,z,"spruce_log[axis=z]");}
        }
        // II on: saplings in pots at the corners of the shed (the sawmill's from IV).
        if(level>=2){as(OTHER);set(9,1,0,"potted_oak_sapling");set(9,1,5,"potted_spruce_sapling");}
        if(level>=3){
            // III: the sapling room behind the cabin, lower than it under its own pent roof, entered by a back door.
            as(DECK);for(int x=1;x<=7;x++)for(int z=6;z<=9;z++)set(x,0,z,x>=2&&x<=6&&z<=8?"spruce_planks":"cobblestone");
            as(PLINTH);for(int z=6;z<=9;z++){set(1,1,z,"cobblestone");set(7,1,z,"cobblestone");}for(int x=2;x<=6;x++)set(x,1,9,"cobblestone");
            as(FRAME);for(int z=6;z<=8;z++)for(int y=2;y<=3;y++){set(1,y,z,log("spruce_log",'z'));set(7,y,z,log("spruce_log",'z'));}
            for(int x=2;x<=6;x++)set(x,2,9,log("spruce_log",'x'));posts(1,9,2,2);posts(7,9,2,2);
            as(OTHER);frame="spruce";set(5,2,9,pane(true));frame=org.villageastra.domain.FramedWindows.DEFAULT_WOOD;
            // Design review: the pent's first course (z6) its own at every level now that the cabin's eave stops on its back wall; from IV it
            // runs on over x8, closing the slot open to the sky between the sapling room and the sawmill.
            as(ROOF);for(int x=1;x<=(level>=4?8:7);x++){set(x,4,6,stair(rs,"north",false));set(x,4,7,stair(rs,"north",false));set(x,4,8,stair(rs,"north",false));set(x,3,9,stair(rs,"north",false));}
            door(4,5);
            as(OTHER);FORESTER_NURSERY.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
        }
        if(level>=4){
            // IV: the sawmill hall on braced posts under its own gable ridged back from the street; the saw's log carriage on a plank deck.
            as(DECK);for(int x=9;x<=14;x++)for(int z=0;z<=9;z++){if(x==10&&z==2)continue;set(x,0,z,x>=10&&x<=12&&z>=1&&z<=7?"spruce_planks":"cobblestone");}
            for(int z:new int[]{0,3,6,9})posts(14,z,1,3);posts(9,6,1,3);posts(9,9,1,3);
            beamRun(false,4,14,0,9,BEAM);beamRun(false,4,9,0,9,BEAM);beamRun(true,4,0,10,13,BEAM);beamRun(true,4,9,10,13,BEAM);
            for(int z:new int[]{1,4,7})brace(14,3,z,"north");for(int z:new int[]{2,5,8})brace(14,3,z,"south");brace(9,3,7,"north");brace(9,3,8,"south");
            roof(true,9,14,0,9,5,(x,z)->false);IntUnaryOperator hh=x->5+Math.min(x-9,14-x);
            fill="spruce_planks";gable(true,0,10,13,5,hh,-1,-1);gable(true,9,10,13,5,hh,-1,-1);fill=PLASTER;
            barge(true,0,10,13,-1,hh);
            // The back gable's barge boards without plaster behind them: behind z9 lies the yard (the courtyard of VI), not the hall.
            as(FRAME);for(int x=10;x<=13;x++)set(x,hh.applyAsInt(x)-1,9,stair(TRIM,x<=11?"west":"east",true));
            as(OTHER);for(int z:new int[]{2,3,5,6})set(11,1,z,"stripped_oak_log[axis=z]");
            set(12,1,8,"spruce_planks");set(13,1,8,"spruce_planks");set(13,2,8,"spruce_planks");set(11,6,4,hung());
        }
        if(level>=5){
            // V: the covered gallery back from the sawmill (VI runs it on to the drying hall): a framed wall on the lot edge, posts to the yard.
            int end=level>=6?18:14;
            as(DECK);for(int x=13;x<=14;x++)for(int z=10;z<=end;z++)set(x,0,z,"cobblestone");
            as(PLINTH);for(int z=10;z<=end;z++)set(14,1,z,"cobblestone");
            for(int z=10;z<=end;z++)for(int y=2;y<=3;y++){boolean post=(z-10)%4==0||z==end;as(post?FRAME:INFILL);set(14,y,z,post?log(POST,'y'):PLASTER);}
            beamRun(false,4,14,10,end,BEAM);posts(13,10,1,3);if(level<6)posts(13,14,1,3);else posts(13,18,1,3);
            as(ROOF);for(int z=10;z<=end;z++){set(14,5,z,stair(rs,"east",false));set(13,4,z,stair(rs,"east",false));}
        }
        if(level>=6){
            // VI: the west cloister and the drying hall close the courtyard; a wall between the sapling room and the sawmill closes its north side.
            as(DECK);for(int x=0;x<=1;x++)for(int z=10;z<=18;z++)set(x,0,z,"cobblestone");for(int x=0;x<=14;x++)for(int z=19;z<=20;z++)set(x,0,z,"cobblestone");
            as(PLINTH);for(int z=6;z<=18;z++)set(0,1,z,"cobblestone");for(int x=0;x<=14;x++)set(x,1,20,"cobblestone");for(int z=6;z<=9;z++)set(8,1,z,"cobblestone");
            for(int z=6;z<=18;z++)for(int y=2;y<=3;y++){boolean post=z%4==2||z==18;as(post?FRAME:INFILL);set(0,y,z,post?log(POST,'y'):PLASTER);}
            for(int x=0;x<=14;x++)for(int y=2;y<=3;y++){boolean post=x%4==0||x==14;as(post?FRAME:INFILL);set(x,y,20,post?log(POST,'y'):PLASTER);}
            // The x8 wall under the pent: to y3 under its course at y4, its end post at z9 to y2 under the pent's eave (as the sapling room's).
            for(int z=6;z<=9;z++)for(int y=2;y<=(z==9?2:3);y++){boolean post=z==6||z==9;as(post?FRAME:INFILL);set(8,y,z,post?log(POST,'y'):PLASTER);}
            as(OTHER);set(0,2,12,pane(false));set(0,2,16,pane(false));set(5,2,20,pane(true));set(9,2,20,pane(true));
            beamRun(false,4,0,6,18,BEAM);beamRun(true,4,20,0,14,BEAM);for(int z:new int[]{10,14,18})posts(1,z,1,3);for(int x:new int[]{2,7,12})posts(x,19,1,3);
            as(ROOF);for(int z=10;z<=18;z++){set(0,5,z,stair(rs,"west",false));set(1,4,z,stair(rs,"west",false));}
            for(int z=6;z<=9;z++)set(0,5,z,stair(rs,"west",false));
            for(int x=0;x<=14;x++){set(x,5,20,stair(rs,"south",false));set(x,4,19,stair(rs,"south",false));}
            as(OTHER);set(5,3,19,hung());set(1,3,12,hung());
            FORESTER_DRYING.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
        }
        as(OTHER);FORESTER_CABIN.forEach((c,s)->set(c.x(),c.y(),c.z(),s));
        door(4,0);as(KIT);set(1,1,4,CHEST);set(7,1,1,"crafting_table");
        // The stations of levels II..N, filled by LevelArchitecture: kept air here so nothing else takes their cells.
        for(var k:FORESTER_KIT)if(k[0]<=level)set(k[1],k[2],k[3],"air");
    }
    /** Design review 2026-09-24: the hut furnished by its use, in cells no station of any level takes (FORESTER_KIT) and off the ways to the
     *  chest, the stations, the core (5,1,3) and the back door (x4 aisle). The cabin (every level): the family table under the paired west
     *  windows with four chairs, a lamp and a potted sapling on it; firewood along the east wall under a shelf of saplings; a runner from
     *  the door to the back door. */
    private static final Map<Cell,String> FORESTER_CABIN=new LinkedHashMap<>();
    /** V..VI: the loft over the cabin — a seed and sapling store (straw, timber, a potting bench with pots) seen through its windows. */
    private static final Map<Cell,String> FORESTER_LOFT=new LinkedHashMap<>();
    /** III..VI: the sapling room — a potting bench under its back window with saplings on it, a stool at it, pots waiting on the floor. */
    private static final Map<Cell,String> FORESTER_NURSERY=new LinkedHashMap<>();
    /** VI: the drying hall — timber stacked to season in two bays between its posts, clear of its windows. */
    private static final Map<Cell,String> FORESTER_DRYING=new LinkedHashMap<>();
    static{String w="spruce",shelf="spruce_slab[type=top,waterlogged=false]";
        var c=FORESTER_CABIN;
        c.put(new Cell(2,1,2),table(w,false,false,true,false));c.put(new Cell(2,1,3),table(w,false,true,false,false));
        // Chairs on the west side only: the east ones were reached past the core's cell alone (BuildingCoreGameTests).
        for(int z=2;z<=3;z++)c.put(new Cell(1,1,z),chair(w,"east"));
        c.put(new Cell(2,2,2),"potted_spruce_sapling");c.put(new Cell(2,2,3),"lantern[hanging=false,waterlogged=false]");
        for(int z=2;z<=3;z++){c.put(new Cell(7,1,z),"oak_log[axis=z]");c.put(new Cell(7,3,z),shelf);}
        c.put(new Cell(7,4,2),"potted_oak_sapling");c.put(new Cell(7,4,3),"potted_birch_sapling");
        // The rug either side of the core's cell only: a rug beside it would be a station the core cuts off (BuildingCoreGameTests).
        for(int z:new int[]{2,4})c.put(new Cell(4,1,z),"brown_carpet");
        var l=FORESTER_LOFT;
        l.put(new Cell(1,6,1),"hay_block[axis=y]");l.put(new Cell(1,6,4),"hay_block[axis=y]");l.put(new Cell(1,7,4),"hay_block[axis=y]");
        l.put(new Cell(7,6,1),"oak_log[axis=z]");l.put(new Cell(7,6,4),"oak_log[axis=z]");l.put(new Cell(7,7,4),"stripped_oak_log[axis=z]");
        for(int x=3;x<=5;x++)l.put(new Cell(x,6,4),table(w,x<5,false,false,x>3));
        l.put(new Cell(3,7,4),"potted_oak_sapling");l.put(new Cell(5,7,4),"potted_spruce_sapling");
        var n=FORESTER_NURSERY;
        n.put(new Cell(4,1,8),table(w,true,false,false,false));n.put(new Cell(5,1,8),table(w,false,false,false,true));
        n.put(new Cell(4,2,8),"potted_birch_sapling");n.put(new Cell(5,2,8),"potted_oak_sapling");n.put(new Cell(4,1,7),chair(w,"south"));
        n.put(new Cell(2,1,6),"potted_spruce_sapling");n.put(new Cell(6,1,6),"potted_dark_oak_sapling");
        var d=FORESTER_DRYING;
        for(int x:new int[]{3,4,5,9,10,11})d.put(new Cell(x,1,19),"oak_log[axis=x]");
        for(int x:new int[]{3,4,10,11})d.put(new Cell(x,2,19),"stripped_oak_log[axis=x]");
        for(var m:List.of(c,l,n,d))late("forester",m.keySet());}
    // ---------- carpentry annex (AD-135): the carpenter's lean-to against the west gable of the forester's hut ----------
    /** AD-135: an annex keeps no level and stands beside every level of its building, so it takes only what the hut keeps at every level: its
     *  west gable (hut x 0, z 0..5: a spruce log wall on a plinth, the same from I to VI), spruce logs laid along the walls on a cobble plinth,
     *  dark oak corner posts, a dark oak shingle pent roof falling west away from the gable (the mirror of the level-II woodshed on the east
     *  gable). x 4 lies against the hut's gable and stays open between its posts; the street side (z 0) is open under a beam. Inside: the
     *  stock chest at (1,1,4) (the workshop station), a crafting table, a log pile by the entrance and a lantern under the roof. Only what the
     *  village has before its carpentry stands: no stripped spruce (only the carpentry strips it — the lean-to must not wait for itself). */
    private void carpentryAnnex(){
        slate();
        as(DECK);for(int x=0;x<=4;x++)for(int z=0;z<=5;z++)set(x,0,z,x>=1&&z>=1&&z<=4?"spruce_planks":"cobblestone");
        as(PLINTH);for(int z=1;z<=4;z++)set(0,1,z,"cobblestone");for(int x=1;x<=3;x++)set(x,1,5,"cobblestone");
        as(FRAME);for(int z=1;z<=4;z++)for(int y=2;y<=3;y++)set(0,y,z,log("spruce_log",'z'));for(int x=1;x<=3;x++)for(int y=2;y<=3;y++)set(x,y,5,log("spruce_log",'x'));
        for(int[] c:new int[][]{{0,0},{0,5},{4,0},{4,5}})posts(c[0],c[1],1,3);
        beamRun(false,4,0,0,5,BEAM);beamRun(false,4,4,0,5,BEAM);beamRun(true,4,0,1,3,BEAM);beamRun(true,4,5,1,3,BEAM);
        // AD-144: framed in spruce, the wood of the lean-to's log walls.
        frame="spruce";as(OTHER);set(0,2,2,pane(false));set(2,2,5,pane(true));
        as(ROOF);for(int z=0;z<=5;z++){set(0,5,z,stair(rs,"east",false));set(1,5,z,stair(rs,"east",false));
            for(int x=2;x<=4;x++){set(x,5,z,rf);set(x,6,z,stair(rs,"east",false));}}
        as(OTHER);set(2,4,2,hung());set(1,1,1,"spruce_log[axis=z]");set(1,1,2,"spruce_log[axis=z]");set(1,2,1,"spruce_log[axis=z]");
        as(KIT);set(1,1,4,CHEST);set(3,1,4,"crafting_table");
    }
    // ---------- masonry annex (AD-135): the stonecutter's lean-to against the east wall of the mine ----------
    /** AD-135: the mine's east wall (mine x 7, z 1..7: its masonry ground storey with two windows) is the same closed wall at every level, and
     *  its west wall holds the furnace the mine installs (FurnaceEquipment, (0,1,2)), so the stonecutter's shed stands on the east side. It is
     *  laid in the mine's own masonry (mas: a rubble course, dressed stone above) on dark oak corner posts, its back wall full height, its outer
     *  side two courses high and open above for the dust, under a dark oak shingle pent roof falling east away from the mine (the mine's eave
     *  is at y5, the shed's high side meets it). x 0 lies against the mine and stays open between its posts; the street side (z 0) is open.
     *  Inside: the stock chest at (1,1,4) (the workshop station), a stonecutter, a furnace (the village's smelting borrows any furnace),
     *  a block of stone on the bench and a lantern. */
    private void masonryAnnex(){
        slate();
        as(DECK);for(int x=0;x<=4;x++)for(int z=0;z<=6;z++)set(x,0,z,"cobblestone");
        for(int z=1;z<=5;z++)for(int y=1;y<=2;y++){as(y==1?PLINTH:INFILL);set(4,y,z,mas(4,y,z,true));}
        for(int x=1;x<=3;x++)for(int y=1;y<=3;y++){as(y==1?PLINTH:INFILL);set(x,y,6,mas(x,y,6,true));}
        for(int[] c:new int[][]{{0,0},{0,6},{4,0},{4,6}})posts(c[0],c[1],1,3);posts(4,3,3,3);
        as(ROOF);for(int z=0;z<=6;z++){set(4,4,z,stair(rs,"west",false));set(3,4,z,stair(rs,"west",false));
            for(int x=0;x<=2;x++){set(x,4,z,rf);set(x,5,z,stair(rs,"west",false));}}
        as(OTHER);set(1,3,5,hung());set(3,1,1,"stone_bricks");set(3,2,1,"stone_brick_slab[type=bottom]");
        as(KIT);set(1,1,4,CHEST);set(1,1,2,"stonecutter[facing=east]");set(3,1,5,"furnace[facing=west,lit=false]");
    }
    // ---------- mill annex (AD-135): a tower mill beside the restaurant ----------
    /** AD-135, restaurant II + milling.2 (owner 2026-09-23: "milling = annex to the restaurant at its level 2"; then "the mill must be tall,
     *  like a real mill"): a tower mill standing east of the restaurant, joined to its east wall (x 10, z 0..8, closed at every level, eave
     *  y5) by a low covered passage. The tower is 5x5 (x 2..6, z 2..6): a stone-brick base to y5 under a cornice, a spruce body framed in
     *  dark oak to y11, a dark oak cap stepped to its ridge at y14. On its street face (z 1) four sails, four blocks a spar, turn round a hub
     *  at y9 (tips y5..y13, x 0..8), each with a spruce lattice on its trailing side — the sails of the old mill building. The door is under
     *  the sails. The passage (x 0..1, z 2..6), open to the street, holds the stock chest at (1,1,4) (the workshop station) and opens into the tower; in the tower
     *  the grindstone, flour barrels, a wheat bale, a loft at y6 with a lantern under it. */
    private void millAnnex(){
        slate();
        as(DECK);for(int x=0;x<=6;x++)for(int z=2;z<=6;z++)set(x,0,z,x>=3&&x<=5&&z>=3&&z<=5||x==1&&z>=3&&z<=5?"spruce_planks":"cobblestone");
        for(int x=2;x<=6;x++)set(x,0,1,"cobblestone");
        // The tower: stone to the cornice, then the framed spruce body; its corners dark oak posts the whole height.
        for(int y=1;y<=11;y++)for(int x=2;x<=6;x++)for(int z=2;z<=6;z++){boolean edge=x==2||x==6||z==2||z==6;if(!edge)continue;
            boolean corner=(x==2||x==6)&&(z==2||z==6);
            if(corner){as(FRAME);set(x,y,z,log(POST,'y'));continue;}
            if(y==1){as(PLINTH);set(x,y,z,"cobblestone");}else if(y<=5){as(STONE);set(x,y,z,"stone_bricks");}
            else if(y==6||y==11){as(FRAME);set(x,y,z,log(BEAM,x==2||x==6?'z':'x'));}else{as(INFILL);set(x,y,z,"spruce_planks");}}
        // The cornice over the stone base.
        as(STONE);for(int x=1;x<=7;x++)for(int z=1;z<=7;z++){boolean ring=x==1||x==7||z==1||z==7;if(!ring||z==1&&x>=3&&x<=5)continue;
            if(x==1&&z>=2&&z<=6)continue;
            String f=x==1?"east":x==7?"west":z==1?"south":"north";set(x,5,z,x==1||x==7?(z==1||z==7?"stone_brick_slab[type=bottom]":stair("stone_brick_stairs",f,true)):z==1||z==7?stair("stone_brick_stairs",f,true):"stone_brick_slab[type=bottom]");}
        // Windows: two up the body on every side but the street one, one low beside the door. AD-144: framed in spruce, the body's wood.
        frame="spruce";as(OTHER);for(int y:new int[]{3,8}){set(6,y,4,pane(false));set(4,y,6,pane(true));}set(3,3,2,pane(true));set(5,3,2,pane(true));set(4,8,2,pane(true));
        // The opening into the passage and the door under the sails.
        set(2,1,4,"air");set(2,2,4,"air");door(4,2);
        // The loft and its lantern.
        as(DECK);for(int x=3;x<=5;x++)for(int z=3;z<=5;z++)if(!(x==5&&z==5))set(x,6,z,"spruce_planks");
        as(OTHER);set(4,5,4,hung());
        // The cap: a stepped dark oak roof to the ridge.
        as(ROOF);for(int x=2;x<=6;x++)for(int z=2;z<=6;z++){boolean edge=x==2||x==6||z==2||z==6;
            if(edge)set(x,12,z,stair(rs,x==2?"east":x==6?"west":z==2?"south":"north",false));else set(x,12,z,rf);}
        for(int x=3;x<=5;x++)for(int z=3;z<=5;z++){boolean edge=x==3||x==5||z==3||z==5;set(x,13,z,edge?stair(rs,x==3?"east":x==5?"west":z==3?"south":"north",false):rf);}
        set(4,14,4,rc);
        // The sails: hub and axle through the street face, four spars, a lattice on each spar's trailing side.
        as(FRAME);set(4,9,2,log(POST,'z'));set(4,9,1,log(POST,'z'));
        int[][] arms={{0,1,1},{1,0,-1},{0,-1,-1},{-1,0,1}};
        for(var a:arms)for(int i=1;i<=4;i++){int x=4+a[0]*i,y=9+a[1]*i;set(x,y,1,log(BEAM,a[0]==0?'y':'x'));
            if(i>=2){int lx=a[0]==0?x+a[2]:x,ly=a[0]==0?y:y+a[2];as(OTHER);set(lx,ly,1,fence("spruce",a[0]==0&&lx<x,false,false,a[0]==0&&lx>x));as(FRAME);}}
        // The passage to the restaurant under a slab roof at the restaurant's eave: open to the street under a beam (probe mill-probe8: closed,
        // the crew could not get in to lay it), a plank back wall.
        as(FRAME);beamRun(true,4,2,0,1,BEAM);
        as(PLINTH);for(int x=0;x<=1;x++)set(x,1,6,"cobblestone");
        as(INFILL);for(int x=0;x<=1;x++)for(int y=2;y<=4;y++)set(x,y,6,"spruce_planks");
        as(ROOF);for(int x=0;x<=1;x++)for(int z=2;z<=6;z++)set(x,5,z,"dark_oak_slab[type=bottom]");
        as(OTHER);set(5,1,5,"barrel[facing=up,open=false]");set(5,2,5,"barrel[facing=up,open=false]");set(3,1,5,"hay_block[axis=y]");set(1,4,4,hung());
        as(KIT);set(1,1,4,CHEST);set(4,1,4,"grindstone[face=floor,facing=north]");
    }
    /** A stair of a stepped square cap ring lo..hi facing into it, the corners in the outer shape the game computes for them. */
    private String capStair(int x,int z,int lo,int hi){String f=x==lo?"east":x==hi?"west":z==lo?"south":"north";boolean corner=(x==lo||x==hi)&&(z==lo||z==hi);
        return rs+"[facing="+f+",half=bottom,shape="+(corner?(x==lo)==(z==lo)?"outer_right":"outer_left":"straight")+"]";}
    /** Whether a full block already stands in the cell (a wall a fence or pane beside it joins). */
    private boolean solidAt(int x,int y,int z){String s=m.get(new Cell(x,y,z));if(s==null)return false;String n=s.split("\\[")[0];
        return !n.equals("air")&&!n.contains("pane")&&!n.contains("stairs")&&!n.contains("slab")&&!n.contains("fence")&&!n.contains("trapdoor")&&!n.contains("door")&&!n.contains("lantern");}
    // ---------- dog academy annex (AD-159 V): the soldiers' dogs' training yard beside the barracks ----------
    /** AD-159 V (owner: "every soldier gets a dog helper and the barracks gain a dog academy annex"): an open yard west of the barracks,
     *  a coarse dirt floor ringed by a spruce fence with a gate on the street side, a hurdle across the middle (two posts under a top slab),
     *  two straw targets at the back, the academy's chest at (1,1,4) and a lantern on the east post. */
    private void dogAcademyAnnex(){
        as(DECK);for(int x=0;x<=4;x++)for(int z=0;z<=6;z++)set(x,0,z,(x+z)%2==0?"coarse_dirt":"dirt");
        as(FRAME);for(int x=0;x<=4;x++)for(int z=0;z<=6;z++){boolean edge=x==0||x==4||z==0||z==6;if(!edge||x==2&&z==0)continue;
            set(x,1,z,fence("spruce",x<4&&(z==0||z==6),z>0&&(x==0||x==4),z<6&&(x==0||x==4),x>0&&(z==0||z==6)));}
        as(OTHER);set(2,1,0,"spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
        set(1,1,3,fence("spruce",false,false,false,false));set(3,1,3,fence("spruce",false,false,false,false));
        set(1,2,3,"spruce_slab[type=bottom,waterlogged=false]");set(2,2,3,"spruce_slab[type=bottom,waterlogged=false]");set(3,2,3,"spruce_slab[type=bottom,waterlogged=false]");
        set(1,1,5,"hay_block[axis=y]");set(3,1,5,"hay_block[axis=y]");set(4,2,3,"lantern[hanging=false,waterlogged=false]");
        as(KIT);set(1,1,4,CHEST);
    }
    // ---------- kennel annex (AD-138 IV): the wolves' lean-to against the west gable of the byre ----------
    /** AD-138 IV (owner 2026-09-22: "a wolf kennel as an annex to the yard if there is room"): the byre's west wall (yard x 0, z 0..6: its
     *  framed and windowed side on the plinth) is the same at every level, so the kennel leans on it as the carpenter's lean-to leans on the
     *  forester's gable: cobble plinth, spruce log walls, dark oak corner posts, a dark oak shingle pent roof falling west away from the byre.
     *  The street side (z 0) is open under a beam, x 4 against the byre open between its posts. Inside: the stock chest at (1,1,4) — the
     *  kennel's meat bin, the keeper fills it —, three straw beds along the back, a trough sunk in the floor by the entrance and a lantern. Four wolves
     *  (VillageWolves.CAPACITY). */
    private void kennelAnnex(){
        slate();
        as(DECK);for(int x=0;x<=4;x++)for(int z=0;z<=6;z++)set(x,0,z,x>=1&&x<=3&&z>=1&&z<=5?"spruce_planks":"cobblestone");
        as(PLINTH);for(int z=1;z<=5;z++)set(0,1,z,"cobblestone");for(int x=1;x<=3;x++)set(x,1,6,"cobblestone");
        as(FRAME);for(int z=1;z<=5;z++)for(int y=2;y<=3;y++)set(0,y,z,log("spruce_log",'z'));for(int x=1;x<=3;x++)for(int y=2;y<=3;y++)set(x,y,6,log("spruce_log",'x'));
        for(int[] c:new int[][]{{0,0},{0,6},{4,0},{4,6}})posts(c[0],c[1],1,3);
        beamRun(false,4,0,0,6,BEAM);beamRun(false,4,4,0,6,BEAM);beamRun(true,4,0,1,3,BEAM);beamRun(true,4,6,1,3,BEAM);
        // AD-142 (owner): a window in its frame, not a bare pane.
        as(OTHER);set(0,2,3,"villageastra:spruce_framed_window[axis=z]");
        as(ROOF);for(int z=0;z<=6;z++){set(0,5,z,stair(rs,"east",false));set(1,5,z,stair(rs,"east",false));
            for(int x=2;x<=4;x++){set(x,5,z,rf);set(x,6,z,stair(rs,"east",false));}}
        as(OTHER);set(2,4,3,hung());set(1,1,5,"hay_block[axis=y]");set(2,1,5,"hay_block[axis=y]");set(3,1,5,"hay_block[axis=y]");
        // The wolves' water: a trough sunk in the floor, as in the pens (a water cauldron is no block a crew may buy — design check).
        as(OTHER);set(3,0,1,"spruce_slab[type=bottom,waterlogged=true]");
        as(KIT);set(1,1,4,CHEST);
    }
    /** AD-131: the forester hut's stations {level,x,y,z} of levels II..VI and their states (one source: ForesterHut reads them, the test checks
     *  them against levels.json kits.forester). II: composter, barrel; III: two barrels, a lectern; IV: the saw (stonecutter), a plank barrel, a
     *  grindstone; V: a chest, a lantern, a drying furnace in the gallery; VI: the automatic saw and its two deepslate pads. */
    static final int[][] FORESTER_KIT={{2,1,1,1},{2,7,1,4},{3,2,1,8},{3,3,1,8},{3,6,1,8},{4,11,1,4},{4,13,1,2},{4,13,1,6},{5,13,1,11},{5,13,1,12},{5,13,1,13},{6,13,1,14},{6,13,1,15},{6,13,1,16}};
    static final String[] FORESTER_KIT_STATES={"composter[level=0]","barrel[facing=up,open=false]","barrel[facing=up,open=false]","barrel[facing=up,open=false]",
        "lectern[facing=north,has_book=false,powered=false]","stonecutter[facing=north]","barrel[facing=up,open=false]","grindstone[face=floor,facing=north]",
        "chest[facing=west,type=single,waterlogged=false]","lantern[hanging=false,waterlogged=false]","furnace[facing=west,lit=false]","stonecutter[facing=west]",
        "chiseled_deepslate","polished_deepslate"};
    /** AD-131: the level-VI courtyard of the forester's hut, x2..12, z10..18, and its six grove cells (balance/forester.json grove.cells). */
    static boolean forestCourtyard(int x,int z){return x>=2&&x<=12&&z>=10&&z<=18;}
    static final int[][] FOREST_GROVE={{4,12},{7,12},{10,12},{4,16},{7,16},{10,16}};
    static boolean forestGrove(int x,int z){for(var g:FOREST_GROVE)if(g[0]==x&&g[1]==z)return true;return false;}

    // ---------- livestock: 11x13 byre and pen, eaves to the street ----------
    /** A low plastered byre in a braced dark oak frame on a cobble plinth under a spruce shingle catslide: the back slope runs on past the open back
     *  of the byre and over the pen fence as a shelter along it. Boarded gables one block in. A stone walk runs from the door to the pen gate; the pen
     *  is fenced in dark oak, its gate where it was, hay in the byre and in the pen, a lamp on the far corner post. */
    // ---------- livestock (AD-138): the yard I..VI on its 17x25 lot, the byre of AD-129 and one to four pens behind it ----------
    /** AD-138 (owner 2026-09-22): the byre (x0..10, z0..6) is the AD-129 one at every level, its door (5,0) and stock chest (1,1,4) in the
     *  same cells; behind it a walk two wide along its open back (z7..8), a lane three wide down the middle (x7..9) and a cross walk two
     *  wide (z16..17), trodden dirt, on a 17x25 lot: on ways of one block between posts and fences the keeper caught a corner and stuck
     *  (probes livestock-5..11 and livestock-probes-a). The pens of
     *  LivestockPens (balance/livestock.json: 1 at I, 2 at II, 3 and 4 at III) are 5x5 inside a 7x7 dark oak fence whose gate opens on the
     *  lane; each has its feeder and a two-slab water trough against the fence on the walk side (the keeper fills them from the walk), a
     *  straw bale and a lantern post. A pen's ground is grass, on which sheep grow their wool back (spec F10), with a trodden cell inside its
     *  gate. V (grazing) puts lanterns on the lane's corner posts and a gate across the lane's back end. East of the byre (x11..14) straw
     *  stacks. Levels IV and VI change only the byre's equipment (balance/levels.json kits.livestock). */
    private void livestock(int level){
        // Round 2 review: one roof material for the set - dark oak shingle, spruce barge boards.
        as(OTHER);for(int x=0;x<17;x++)for(int z=0;z<25;z++){if(x<=10&&z<=6)continue;boolean walk=z==7||z==8||z==16||z==17||x>=7&&x<=9&&z>8;set(x,0,z,walk?"coarse_dirt":"grass_block[snowy=false]");}
        as(DECK);for(int x=0;x<=10;x++)for(int z=0;z<=6;z++)set(x,0,z,"cobblestone");
        as(OTHER);for(int z=3;z<=6;z++)set(5,0,z,"stone_brick_slab[type=top,waterlogged=false]");
        // Round 1 review: a barn door three wide (the house door between board leaves open to the jambs) under a log lintel.
        face(true,0,1,false,0,"P1WPOOOPW4P",2,5,false,2,"cobblestone");
        // AD-144 (owner 2026-09-23): the board leaves stand shut in spruce planks either side of the door, no open trapdoor beside it.
        as(FRAME);beamRun(true,4,0,4,6,BEAM);as(OTHER);for(int x:new int[]{4,6})for(int y=1;y<=3;y++)set(x,y,0,"spruce_planks");as(FRAME);set(5,3,0,log(BEAM,'x'));as(OTHER);
        face(false,0,1,false,0,"PSWPWSP",2,5,false,2,"cobblestone");
        face(false,10,-1,false,0,"PSWPWSP",2,5,false,2,"cobblestone");
        // The open back of the byre: posts, the plate, knee braces clear of the walk.
        posts(3,6,1,4);posts(7,6,1,4);beamRun(true,5,6,0,10,BEAM);
        brace(1,4,6,"west");brace(2,4,6,"east");brace(4,4,6,"west");brace(6,4,6,"east");brace(8,4,6,"west");brace(9,4,6,"east");
        // Catslide: three courses up to the ridge in front, four down behind it, the last over the walk.
        IntUnaryOperator h=z->z<=3?6+z:12-z;
        as(ROOF);for(int x=0;x<11;x++)for(int z=0;z<=7;z++){if(z==3){set(x,8,z,rf);set(x,9,z,rc);}else set(x,h.applyAsInt(z),z,stair(rs,z<3?"south":"north",false));}
        beamRun(false,5,1,1,5,BEAM);beamRun(false,5,9,1,5,BEAM);
        // AD-122 review: boarded gables one block in over the plates, barge boards; in the west one the hay loft door open on a hay block.
        fill="spruce_planks";gable(false,1,1,6,6,h,3,-1,new int[]{2,7});gable(false,9,1,6,6,h,3,-1,new int[]{2,7},new int[]{4,7});fill=PLASTER;
        barge(false,0,1,5,3,h);barge(false,10,1,5,3,h);pentice(false,0,1,6,6,"east");pentice(false,10,1,6,6,"west");
        as(OTHER);for(int y=6;y<=7;y++)set(1,y,3,"spruce_trapdoor[facing=west,half=bottom,open=true,powered=false,waterlogged=false]");
        for(int z=2;z<=4;z++)set(2,5,z,"spruce_planks");set(2,6,3,"hay_block[axis=z]");set(2,6,2,"hay_block[axis=z]");
        beamRun(true,6,3,2,8,POST);as(OTHER);set(3,5,3,hung());set(7,5,3,hung());
        // The yard corner east of the byre: straw stacked for the pens.
        as(OTHER);set(12,1,4,"hay_block[axis=y]");set(13,1,4,"hay_block[axis=y]");set(12,2,4,"hay_block[axis=x]");set(13,1,5,"hay_block[axis=z]");
        for(int[] p:YARD_PENS)if(p[3]<=level)pen(p[0],p[1],p[2]==1);
        if(level>=5){
            // V: lanterns on the lane's corner posts, a gate across the lane's back end.
            as(OTHER);for(int[] c:new int[][]{{6,9},{10,9},{6,18},{10,18}}){set(c[0],2,c[1],POLE);set(c[0],3,c[1],"lantern[hanging=false,waterlogged=false]");}
            fences(List.of(new int[]{7,24},new int[]{9,24}),"dark_oak_fence");set(8,1,24,"dark_oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]");
        }
        door(5,0);as(KIT);set(1,1,4,CHEST);set(2,1,2,"hay_block[axis=y]");set(3,1,2,"hay_block[axis=y]");
        slate();
    }
    /** AD-138: the pens of the yard {fence x, fence z, gate east (1) or west (0), yard level} - one source with balance/livestock.json
     *  (LivestockPens checks they agree). */
    static final int[][] YARD_PENS={{0,9,1,1},{10,9,0,2},{0,18,1,3},{10,18,0,3}};
    /** One pen: the 7x7 fence with its gate on the lane, the feeder and the trough on the walk side, a bale, a lantern post. */
    private void pen(int px,int pz,boolean gateEast){
        int gx=gateEast?px+6:px,gz=pz+3;var run=new ArrayList<int[]>();
        for(int x=px;x<=px+6;x++){run.add(new int[]{x,pz});run.add(new int[]{x,pz+6});}
        for(int z=pz+1;z<=pz+5;z++){if(!(z==gz&&px==gx))run.add(new int[]{px,z});if(!(z==gz&&px+6==gx))run.add(new int[]{px+6,z});}
        as(OTHER);set(gx,1,gz,"dark_oak_fence_gate[facing="+(gateEast?"east":"west")+",in_wall=false,open=false,powered=false]");
        fences(run,"dark_oak_fence");
        set(gateEast?gx-1:gx+1,0,gz,"coarse_dirt");
        as(KIT);set(px+2,1,pz+1,"villageastra:feeder[feed=0]");
        // The trough sunk in the ground (y0): a waterlogged slab standing above ground spills over the whole pen once a builder sets it with
        // its neighbours updated (probe livestock-4); at ground level the water has nowhere to run.
        as(OTHER);set(px+4,0,pz+1,"spruce_slab[type=bottom,waterlogged=true]");set(px+5,0,pz+1,"spruce_slab[type=bottom,waterlogged=true]");
        // No straw bale inside: a full block by the fence is a step the herd jumps out over (probe livestock-14).
        set(px+6,2,pz+6,POLE);set(px+6,3,pz+6,"lantern[hanging=false,waterlogged=false]");
    }

    // ---------- expedition: 9x11 outfitters' hall, eaves to the street, a lookout on the ridge ----------
    /** A plastered hall in a braced dark oak frame under a tall slate roof whose boarded gables stand one block in; the east side opens on braced
     *  posts over the barrels of supplies; on the ridge a lookout — a slab platform on the ridge (a slab, so no equipment is set up there), four
     *  posts, a lantern, a 2:1 slate cap and a pole — with the office's board sign to the street. Lamps hang on chains from the ridge. */
    private void expedition(){
        // Round 1 review: the front one block in under the eave, the gables one block in over the plates under overhanging end courses with a
        // raked soffit and a pentice; the lookout stands on four posts carried down through the roof to the tie beams, a plank floor on the
        // ridge, a railing, its own roof one block over it and a flag; at the door the outfitters' goods — a cart of barrels, a hitching rail.
        as(DECK);for(int x=0;x<9;x++)for(int z=0;z<11;z++){boolean in=x>=1&&x<=7&&z>=2&&z<=9;set(x,0,z,in?PLANKS:"cobblestone");}
        IntUnaryOperator h=z->6+Math.min(z,10-z);
        roof(false,0,10,0,8,6,(x,z)->false);
        face(true,1,1,false,0,"PW4PDP1WP",2,5,false,2,"cobblestone");
        face(true,10,-1,false,0,"PSWPSPWSP",2,5,false,2,"cobblestone");
        face(false,0,1,false,1,"PWPWPWPSWP",2,5,false,2,"cobblestone");
        face(false,8,-1,false,1,"PWPOOPOOSP",2,5,false,2,"cobblestone");
        // The open supply bays on the east side: plate and knee braces between the posts.
        beamRun(false,5,8,4,5,BEAM);beamRun(false,5,8,7,8,BEAM);
        brace(8,4,4,"north");brace(8,4,5,"south");brace(8,4,7,"north");brace(8,4,8,"south");
        beamRun(false,5,1,1,9,BEAM);beamRun(false,5,7,1,9,BEAM);
        fill="spruce_planks";
        gable(false,1,1,9,6,h,5,8,new int[]{3,7},new int[]{7,7},new int[]{4,9},new int[]{6,9});
        gable(false,7,1,9,6,h,5,8,new int[]{3,7},new int[]{7,7},new int[]{4,9},new int[]{6,9});
        fill=PLASTER;
        rake(false,0,0,10,h);pentice(false,0,1,9,6,"east");rake(false,8,0,10,h);pentice(false,8,1,9,6,"west");
        // The lookout: posts from the tie beams through the roof, a plank floor on the ridge, posts, a railing, its own roof, a flag.
        for(int x:new int[]{3,5})for(int z:new int[]{4,6}){posts(x,z,6,10);posts(x,z,12,15);}
        as(FRAME);for(int x=3;x<=5;x++)for(int z=4;z<=6;z++)set(x,11,z,PLANKS);
        as(OTHER);set(4,12,4,fence("dark_oak",true,false,false,true));set(4,12,6,fence("dark_oak",true,false,false,true));
        set(3,12,5,fence("dark_oak",false,true,true,false));set(5,12,5,fence("dark_oak",false,true,true,false));
        brace(4,15,4,"west");brace(4,15,6,"west");
        as(OTHER);set(4,15,5,hung());
        roof(true,2,6,3,7,15,(x,z)->false);
        as(OTHER);set(4,17,5,POLE);set(4,18,5,POLE);set(4,19,5,POLE);set(4,19,4,trapdoor("spruce","north",false,true));set(4,18,4,trapdoor("spruce","north",false,true));
        for(int x:new int[]{3,5}){for(int y=8;y<=9;y++)set(x,y,5,"chain[axis=y,waterlogged=false]");set(x,7,5,hung());}
        // At the door: a cart of barrels, a hitching rail, a lamp.
        as(OTHER);set(6,1,0,"barrel[facing=up,open=false]");set(7,1,0,"barrel[facing=up,open=false]");set(7,2,0,"barrel[facing=up,open=false]");set(8,1,0,"hay_block[axis=y]");
        set(1,1,0,fence("dark_oak",true,false,false,false));set(2,1,0,fence("dark_oak",false,false,false,true));set(4,4,0,hung());
        // Design review 2026-09-24: the door lamp hangs on a chain from the eave (it hung in the air under (4,5,0)).
        as(OTHER);set(4,5,0,"chain[axis=y,waterlogged=false]");
        door(4,1);as(KIT);set(1,1,4,CHEST);for(int z=4;z<=8;z++)set(7,1,z,"barrel[facing=up,open=false]");set(7,1,9,"crafting_table");
    }
    static{late("expedition",Set.of(new Cell(4,5,0)));}

    // ---------- caravan: 13x11 coaching hall, eaves to the street ----------
    /** AD-122 review: one long hall under a full 45-degree slate gable roof (the flat ring of lean-tos broke the style): tall framed walls on a cobble
     *  plinth, closed gables at both ends in the wall planes with barge boards, and in the middle of the street front a masonry gate bay — a carriage
     *  arch three wide and three high with stone brick springers and a chiseled keystone, board leaves shut either side of a wicket door — under a cross
     *  gable. Inside, a cobbled court under tie beams with lamps, the hitching rail, the supply barrels along the east wall. */
    private void caravan(){
        // Round 1 review ("the stone patch looks pasted on"): the hall's front one block in under the eave, and in its middle a masonry gatehouse
        // standing forward to the street line — a carriage arch three wide on stone springers with a chiseled keystone, polished quoins, the
        // timber cross gable over it; the gables one block in over the plates under overhanging end courses; hitching rails and a cart of
        // barrels before the front.
        for(int x=0;x<13;x++)for(int z=0;z<11;z++){int e=Math.min(Math.min(x,12-x),Math.min(z,10-z));
            if(e>=3){as(OTHER);set(x,0,z,hash(x,0,z)<55?"cobblestone":"stone_bricks");}else{as(DECK);set(x,0,z,e==0?"cobblestone":PLANKS);}}
        IntUnaryOperator h=z->7+Math.min(z,10-z),g=x->7+Math.min(x-4,8-x);
        roof(false,0,10,0,12,7,(x,z)->false);
        face(true,1,1,false,0,"P1WPCCCCCPW4P",2,6,false,3,"cobblestone");
        face(true,10,-1,false,0,"PSWPSWPWSPWSP",2,6,false,3,"cobblestone");
        face(false,0,1,false,1,"PWSPSWSP4P",2,6,false,3,"cobblestone");
        face(false,12,-1,false,1,"PWSPSWSP4P",2,6,false,3,"cobblestone");
        // The gatehouse: masonry on the street line and its cheeks back to the hall, the carriage arch, the leaves, a board transom over the door.
        stone(true,0,4,8,1,6,true);stone(true,1,4,4,1,6,true);stone(true,1,8,8,1,6,true);
        as(STONE);for(int y=2;y<=6;y+=2){set(4,y,0,"polished_andesite");set(8,y,0,"polished_andesite");}
        as(OTHER);for(int x=5;x<=7;x++)for(int z=0;z<=1;z++)for(int y=1;y<=3;y++)set(x,y,z,"air");
        for(int x=5;x<=7;x++)for(int y=4;y<=6;y++){as(STONE);set(x,y,1,"stone_bricks");}
        // AD-144 (owner 2026-09-23): the carriage arch is shut by board leaves in spruce planks either side of the door (a wicket in the
        // gate), so its jambs are full; the leaves stand in the arch's plane and behind it.
        as(OTHER);for(int x:new int[]{5,7})for(int y=1;y<=3;y++){set(x,y,0,"spruce_planks");set(x,y,1,"spruce_planks");}
        set(6,3,0,"spruce_planks");arch(6,4,0);as(OTHER);set(6,3,1,hung());
        // The gables, one block in over the plates, framed; the soffits and pentices of the overhangs; the cross gable over the gate.
        beamRun(false,6,1,1,9,BEAM);beamRun(false,6,11,1,9,BEAM);
        // Design review 2026-09-24: no pane in the west king post (the east gable has none).
        gable(false,1,1,9,7,h,5,9,new int[]{3,8},new int[]{7,8});rake(false,0,0,10,h);pentice(false,0,1,9,7,"east");
        gable(false,11,1,9,7,h,5,9,new int[]{3,8},new int[]{7,8});rake(false,12,0,10,h);pentice(false,12,1,9,7,"west");
        crossRoof(4,8,7,h);cornerC(5,8,1,"inner_left");cornerC(7,8,1,"inner_right");
        gable(true,0,5,7,7,g,6,-1);
        // Inside: tie beams across the hall with lamps under them, the hitching rail, lamp posts by the gate.
        for(int x:new int[]{3,6,9})beamRun(false,7,x,1,9,POST);
        lanternPost(3,3);lanternPost(9,3);
        var rail=new ArrayList<int[]>();for(int x=5;x<=7;x++)rail.add(new int[]{x,7});fences(rail,"dark_oak_fence");
        as(OTHER);set(3,5,5,hung());set(9,5,5,hung());set(6,5,8,hung());
        // Before the front: hitching rails either side of the gatehouse, a cart of barrels on a slab bed, straw.
        var hitch=new ArrayList<int[]>();for(int x=1;x<=3;x++)hitch.add(new int[]{x,0});fences(hitch,"dark_oak_fence");
        as(OTHER);set(10,1,0,"spruce_slab[type=top,waterlogged=false]");set(11,1,0,"spruce_slab[type=top,waterlogged=false]");set(10,2,0,"barrel[facing=up,open=false]");set(11,2,0,"barrel[facing=up,open=false]");
        set(9,1,0,trapdoor("spruce","west",false,true));set(12,1,0,"hay_block[axis=y]");
        door(6,0);as(KIT);set(1,1,4,CHEST);for(int z=4;z<=8;z++)set(11,1,z,"barrel[facing=up,open=false]");set(11,1,9,"crafting_table");
        decorC(CARAVAN_DECOR);
    }
    /** Design review 2026-09-24: the coaching hall's late cells. The carriage arch is shut by two board leaves either side of the door, a wicket
     *  in the gate (the door stood alone in a three-wide opening, air at its jambs; the battens inside stay). The court: two drivers' tables of
     *  two with a pot and two chairs each either side of the core, a table and a chair either side of the gate, fodder in the corner by the
     *  hitching rail, a lamp and a pot on the supply barrels, the tie beams' lamps on chains (they hung in the air). The aisle from the gate to
     *  the core, the rows either side of it and the barrels' east side stay free. */
    private static final Object[][] CARAVAN_DECOR;
    static{var d=new ArrayList<Object[]>();String oak="oak";
        for(int x:new int[]{5,7})for(int y=1;y<=3;y++)d.add(new Object[]{x,y,0,"spruce_planks"});
        for(int[] c:new int[][]{{3,6,5},{9,6,5},{6,6,8}})d.add(new Object[]{c[0],c[1],c[2],"chain[axis=y]"});
        for(int x:new int[]{4,8}){d.add(new Object[]{x,1,5,table(oak,false,false,true,false)});d.add(new Object[]{x,1,6,table(oak,false,true,false,false)});
            d.add(new Object[]{x-1,1,5,chair(oak,"east")});d.add(new Object[]{x+1,1,5,chair(oak,"west")});d.add(new Object[]{x,1,2,table(oak,false,false,false,false)});d.add(new Object[]{x,2,2,"flower_pot"});}
        d.addAll(List.of(new Object[]{4,2,6,"potted_poppy"},new Object[]{8,2,6,"potted_dandelion"},new Object[]{5,1,2,chair(oak,"west")},new Object[]{7,1,2,chair(oak,"east")},
            new Object[]{2,1,7,"hay_block[axis=y]"},new Object[]{2,1,8,"hay_block[axis=y]"},new Object[]{2,2,8,"hay_block[axis=y]"},
            new Object[]{11,2,5,"lantern[hanging=false]"},new Object[]{11,2,7,"potted_oak_sapling"}));
        CARAVAN_DECOR=d.toArray(new Object[0][]);}
    static{late("caravan",decorCellsC(CARAVAN_DECOR));}

    // ---------- batch 5: military. The castle's own vocabulary on village lots: masonry storeys, crenels 1:1 two high, arched gates with a chiseled
    // keystone, barred slits, stone piers and corbels under 2:1 slate; timber and plaster only above the stone. Door (w/2,1,0), stock chest (1,1,4). ----------
    private static final String BARS_X="iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]",BARS_Z="iron_bars[east=false,north=true,south=true,waterlogged=false,west=false]";
    /** A barred slit two high in a masonry face. */
    private void slit(int x,int y0,int z,boolean alongX){as(OTHER);for(int y=y0;y<=y0+1;y++)set(x,y,z,alongX?BARS_X:BARS_Z);}
    /** Arched head over an opening in a face along x: stone brick springers either side, a chiseled keystone in the middle, in row y. */
    private void arch(int x,int y,int z){as(FRAME);set(x-1,y,z,stair("stone_brick_stairs","west",true));set(x+1,y,z,stair("stone_brick_stairs","east",true));set(x,y,z,"chiseled_stone_bricks");}

    // ---------- guard_house: 9x9 watch house ----------
    /** A masonry guard room (a polished andesite course at its head, barred slits, the door under an arched head) carrying a timber watch room one
     *  block in; round it stone piers at the corners and mid-sides rise to the corbels of a 2:1 slate pyramid with a spire, so the roof overhangs
     *  the watch room on every side on a board soffit, and lamps stand on the low parapet between the piers. The stock chest, the two smithing tables and the table stay where the guards use them. */
    private void guardHouse(){
        as(DECK);for(int x=0;x<9;x++)for(int z=0;z<9;z++){boolean in=x>=1&&x<=7&&z>=1&&z<=7;set(x,0,z,in?PLANKS:"cobblestone");}
        stone(true,0,0,8,1,5,true);stone(true,8,0,8,1,5,true);stone(false,0,1,7,1,5,true);stone(false,8,1,7,1,5,true);
        arch(4,3,0);
        slit(2,2,0,true);slit(6,2,0,true);slit(2,2,8,true);slit(6,2,8,true);slit(0,2,2,false);slit(0,2,6,false);slit(8,2,2,false);slit(8,2,6,false);
        as(DECK);for(int x=1;x<=7;x++)for(int z=1;z<=7;z++)set(x,5,z,PLANKS);
        // The piers of the wall head at the corners and mid-sides, up to the corbels; a low parapet between them with lamps on it.
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){if(!(x==0||x==8||z==0||z==8))continue;
            if(x%4==0&&z%4==0){stone(true,z,x,x,6,8,false);continue;}
            boolean alongX=z==0||z==8,lamp=false;as(OTHER);
            set(x,6,z,"stone_brick_wall[east="+(alongX?"low":"none")+",north="+(alongX?"none":"low")+",south="+(alongX?"none":"low")+",up="+lamp+",waterlogged=false,west="+(alongX?"low":"none")+"]");
            if(lamp)set(x,7,z,"lantern[hanging=false,waterlogged=false]");}
        // The watch room: sill, posts, braces, windows, plate.
        face(true,1,1,false,1,"P1WPW4P",6,9,true,7,null);face(true,7,-1,false,1,"P1WPW4P",6,9,true,7,null);
        face(false,1,1,false,1,"PSWPWSP",6,9,true,7,null);face(false,7,-1,false,1,"PSWPWSP",6,9,true,7,null);
        // Round 1 review: a 45-degree dark oak hip (not the stepped 2:1 slate pyramid) whose eave runs one block past the watch room over the
        // piers, a board soffit between them, chiseled caps on the piers, a finial on the apex.
        hip45(0,0,8,8,10);
        as(FRAME);for(int x=0;x<9;x++)for(int z=0;z<9;z++)if(x==0||x==8||z==0||z==8)set(x,9,z,x%4==0&&z%4==0?"chiseled_stone_bricks":"dark_oak_slab[type=top,waterlogged=false]");
        as(OTHER);set(4,14,4,"stone_brick_wall[up=true]");
        // A corbel table under the andesite course (every other stone an upside-down stair, its notch outside) and a battered foot.
        for(int x=0;x<9;x++)for(int z=0;z<9;z++){boolean edge=x==0||x==8||z==0||z==8,corner=(x==0||x==8)&&(z==0||z==8);if(!edge||corner)continue;
            String in=z==0?"south":z==8?"north":x==0?"east":"west";int along=z==0||z==8?x:z;
            if(along%2==1&&empty(x,4,z)==false&&!m.get(new Cell(x,4,z)).startsWith("iron_bars")){as(STONE);set(x,4,z,stair("stone_brick_stairs",in,true));}
            // AD-144: the door's jambs keep their full masonry (a battered stair there left a notch beside the door).
            if(!(Math.abs(x-4)<=1&&z==0)){as(PLINTH);set(x,1,z,stair("stone_brick_stairs",in,false));}}
        as(OTHER);set(4,4,4,hung());
        // Design review 2026-09-24 ("a lamp hangs in the air", "the watch room cannot be reached"): a tie beam across the hip at y10 with a king
        // post to the apex carries the watch room's lamp; a straight stair of four treads rises west along z3 from the guard room through an
        // opening in the deck, boarded under, railed above.
        as(FRAME);for(var c:GUARD_TRUSS)set(c.x(),c.y(),c.z(),c.y()==10?log(BEAM,'x'):c.x()==4?log(POST,'y'):stair("dark_oak_stairs",c.x()<4?"east":"west",true));
        as(OTHER);set(4,9,4,hung());
        for(int i=0;i<4;i++){set(6-i,1+i,3,stair("spruce_stairs","west",false));for(int y=1;y<1+i;y++)set(6-i,y,3,"spruce_planks");}
        for(int x=3;x<=5;x++)set(x,5,3,"air");
        // Guard room: a duty table by the door, a spear rack against the boarded stair, a straw pallet in the back corner; watch room: the rail
        // round the stairwell, the watch table with two chairs, a spear rack and a potted fern.
        String wood=org.villageastra.domain.Furniture.HOME_WOOD;
        set(2,1,2,table(wood,false,false,false,false));set(3,1,2,chair(wood,"west"));
        set(5,1,2,fence("spruce",false,false,true,false));set(5,2,2,trapdoor("spruce","north",true,false));set(1,1,5,"hay_block[axis=x]");
        set(3,6,4,fence("dark_oak",true,false,false,false));set(4,6,4,fence("dark_oak",true,false,false,true));set(5,6,4,fence("dark_oak",false,false,false,true));
        set(4,6,5,table(wood,false,false,false,false));set(3,6,5,chair(wood,"east"));set(5,6,5,chair(wood,"west"));
        set(6,6,6,fence("spruce",true,false,true,false));set(6,7,6,trapdoor("spruce","north",true,false));set(2,6,6,"potted_fern");
        door(4,0);as(KIT);set(1,1,4,CHEST);set(2,1,7,"smithing_table");set(5,1,7,"smithing_table");set(7,1,7,"crafting_table");
    }
    /** Design review 2026-09-24: the watch room's tie beam, king post and its two struts (cells air before, so lateFurniture). */
    private static final Set<Cell> GUARD_TRUSS=Set.of(new Cell(2,10,4),new Cell(3,10,4),new Cell(4,10,4),new Cell(5,10,4),new Cell(6,10,4),new Cell(4,11,4),new Cell(4,12,4),new Cell(3,11,4),new Cell(5,11,4));
    /** Design review 2026-09-24: every guard house cell that was air before — the truss, the stair and its boarding, the furniture. */
    private static final Set<Cell> GUARD_FURNITURE;
    static{var s=new HashSet<>(GUARD_TRUSS);for(int i=0;i<4;i++)for(int y=1;y<=1+i;y++)s.add(new Cell(6-i,y,3));
        for(int[] c:new int[][]{{2,1,2},{3,1,2},{5,1,2},{5,2,2},{1,1,5},{3,6,4},{4,6,4},{5,6,4},{4,6,5},{3,6,5},{5,6,5},{6,6,6},{6,7,6},{2,6,6}})s.add(new Cell(c[0],c[1],c[2]));
        GUARD_FURNITURE=Set.copyOf(s);late("guard_house",GUARD_FURNITURE);}

    // ---------- barracks: 13x11 fortified hall ----------
    /** AD-122 review (the old open wing read as a ruin): one closed masonry block, its walls the same height all round (to y6) under merlons 1:1 two
     *  high; the arched gate between two corner turrets that rise a storey higher with a polished andesite course and merlons of their own; behind
     *  the front parapet a wall walk, and over the drill hall a 45-degree slate gable roof whose ends rise as stone gables in the side walls.
     *  Barred slits three high light the hall. The door, the stock chest, the smithing tables and the table stay where the guards use them. */
    private void barracks(){
        for(int x=0;x<13;x++)for(int z=0;z<11;z++){boolean court=x>=3&&x<=9&&z>=3&&z<=8;
            if(court){as(OTHER);set(x,0,z,x==3||x==9||z==3||z==8?"stone_bricks":"smooth_stone");}
            else{as(DECK);set(x,0,z,x==0||x==12||z==0||z==10?"cobblestone":PLANKS);}}
        // The curtain: masonry on every lot edge to y6, merlons over it at even cells (the side walls carry the gables instead).
        stone(true,0,0,12,1,6,true);stone(true,10,0,12,1,6,true);stone(false,0,1,9,1,6,true);stone(false,12,1,9,1,6,true);
        for(int x=3;x<=9;x+=2){stone(true,0,x,x,7,8,false);stone(true,10,x,x,7,8,false);}
        // The corner turrets at the front: to y9, a polished andesite course at y8, merlons on their corners, a lamp on their deck.
        for(int t0:new int[]{0,10}){
            for(int x=t0;x<=t0+2;x++)for(int z=0;z<=2;z++){if(x==t0+1&&z==1)continue;stone(true,z,x,x,7,9,true);as(STONE);set(x,8,z,"polished_andesite");}
            as(DECK);set(t0+1,9,1,PLANKS);
            for(int x:new int[]{t0,t0+2})for(int z:new int[]{0,2})stone(true,z,x,x,10,11,false);
            as(OTHER);set(t0+1,10,1,"lantern[hanging=false,waterlogged=false]");
            for(int y=1;y<=2;y++)set(t0+1,y,2,"air");}
        // The gate: arched, the door in it; slits three high along the hall.
        arch(6,3,0);
        for(int x:new int[]{4,8}){slit3(x,2,0,true);slit3(x,2,10,true);}for(int z:new int[]{4,7}){slit3(0,2,z,false);slit3(12,2,z,false);}
        for(int t0:new int[]{1,11})slit3(t0,5,0,true);
        // The wall walk behind the front parapet, a lamp over the gate.
        as(DECK);for(int x=3;x<=9;x++)for(int z=1;z<=2;z++)set(x,6,z,PLANKS);

        // Round 1 review ("the roof peeks unevenly over the crenels"): the drill hall under one 45-degree hip inside the curtain, merlons 1:1 on
        // the side walls as on the front and back, a battered foot round the curtain and a portcullis slot over the gate.
        // Round 2 review ("a roofless box"): one 45-degree gable roof over the whole hall, ridge along the street, its eaves behind the front and
        // back parapets and its ends in stone gables that rise with the slope under a coping of stone brick stairs, so the roof reads over the
        // crenels from the street.
        roof2(false,1,9,1,11,7,(x,z)->(x<=2||x>=10)&&z<=2);
        for(int x:new int[]{0,12})for(int z=3;z<=9;z++){int k=Math.min(z-1,9-z),top=7+2*k;as(STONE);
            for(int y=7;y<top;y++)set(x,y,z,mas(x,y,z,false));
            set(x,top,z,z==5?"stone_brick_slab[type=bottom,waterlogged=false]":stair("stone_brick_stairs",z<5?"south":"north",false));}
        for(int x=0;x<13;x++)for(int z=0;z<11;z++){boolean edge=x==0||x==12||z==0||z==10,corner=(x==0||x==12)&&(z==0||z==10);
            // AD-144: the door's jambs keep their full masonry.
            if(!edge||corner||Math.abs(x-6)<=1&&z==0||z<=2&&(x<=2||x>=10))continue;
            String c=m.get(new Cell(x,1,z));if(c==null||c.startsWith("iron_bars"))continue;
            as(PLINTH);set(x,1,z,stair("stone_brick_stairs",z==0?"south":z==10?"north":x==0?"east":"west",false));}
        // Round 2 review: no strip of bars over the gate; a chiseled course over its arch.
        as(STONE);for(int x=5;x<=7;x++)set(x,4,0,"chiseled_stone_bricks");
        // Design review 2026-09-24 ("a grey box, its roof hiding behind the crenels"): over the gate a stepped frontispiece rises out of the
        // street face — x4|8 to y9, x5|7 to y10, the middle to y11 under a chiseled cap and a wall-post finial, coped with slabs — with a barred
        // lookout; the side walls take three framed windows two high under stone brick hoods instead of the barred slits, so the hall reads as a
        // house for people. Lot-edge cells only: no level's equipment moves (tools/kit_diff.py).
        for(int x=4;x<=8;x++){int top=11-Math.abs(x-6);as(STONE);for(int y=7;y<=top;y++)set(x,y,0,mas(x,y,0,false));
            if(x!=6)set(x,top+1,0,"stone_brick_slab[type=bottom,waterlogged=false]");}
        set(6,12,0,"chiseled_stone_bricks");as(OTHER);set(6,13,0,wallPost(false,false,false,false));slit(6,8,0,true);
        for(int x:new int[]{0,12}){String hood=stair("stone_brick_stairs",x==0?"east":"west",true);
            for(int z:new int[]{4,7})for(int y=2;y<=4;y++){as(STONE);set(x,y,z,mas(x,y,z,false));}
            for(int z:new int[]{3,5,7}){as(OTHER);set(x,2,z,pane(false));set(x,3,z,pane(false));as(STONE);set(x,4,z,hood);}}
        // Inside: posts under a tie beam down the hall, lamps.
        for(int x:new int[]{3,9})posts(x,3,1,5);beamRun(true,6,3,3,9,BEAM);for(int x:new int[]{3,6,9})beamRun(false,7,x,4,8,POST);
        // Design review 2026-09-24: the lamps on dark oak log posts (a fence post stood unjoined beside the kits of levels III..VI).
        for(int x:new int[]{3,9}){as(OTHER);set(x,1,8,log(POST,'y'));set(x,2,8,log(POST,'y'));set(x,3,8,"lantern[hanging=false,waterlogged=false]");}
        as(OTHER);set(6,6,5,hung());set(3,6,7,hung());set(9,6,7,hung());
        // Design review 2026-09-24 ("an empty hall"): the mess table west of the gate under a lamp, the armoury's spear racks along the front
        // wall east of it, four bedrolls (a white head and a red foot of carpet) in the side aisles, two straw dummies on posts in the drill
        // hall, the spare straw stacked in the loft over the tie beam.
        String wood=org.villageastra.domain.Furniture.HOME_WOOD;as(OTHER);
        set(3,1,2,table(wood,true,false,false,false));set(4,1,2,table(wood,false,false,false,true));
        set(3,1,1,chair(wood,"south"));set(4,1,1,chair(wood,"south"));set(2,1,2,chair(wood,"east"));set(5,1,2,chair(wood,"west"));
        for(int x=8;x<=9;x++){set(x,1,1,fence("spruce",x<9,true,false,x>8));set(x,2,1,trapdoor("spruce","south",true,false));}
        for(int[] b:new int[][]{{10,3},{10,6},{1,2},{2,6}}){set(b[0],1,b[1],"white_carpet");set(b[0],1,b[1]+1,"red_carpet");}
        for(int x:new int[]{5,7}){set(x,1,7,log(POST,'y'));set(x,2,7,"hay_block[axis=y]");}
        for(int x:new int[]{4,6,8})set(x,7,3,"hay_block[axis=x]");
        set(4,5,2,hung());set(9,5,2,hung());
        door(6,0);as(KIT);set(1,1,4,CHEST);for(int x:new int[]{2,5,8})set(x,1,9,"smithing_table");set(11,1,9,"crafting_table");
    }
    /** Design review 2026-09-24: the barracks' furniture, in cells that were air on every level (lateFurniture). */
    private static final Set<Cell> BARRACKS_FURNITURE;
    static{var s=new HashSet<Cell>();
        for(int[] c:new int[][]{{3,1,2},{4,1,2},{3,1,1},{4,1,1},{2,1,2},{5,1,2},{8,1,1},{9,1,1},{8,2,1},{9,2,1},
            {10,1,3},{10,1,4},{10,1,6},{10,1,7},{1,1,2},{1,1,3},{2,1,6},{2,1,7},{5,1,7},{5,2,7},{7,1,7},{7,2,7},{4,7,3},{6,7,3},{8,7,3},{4,5,2},{9,5,2}})s.add(new Cell(c[0],c[1],c[2]));
        BARRACKS_FURNITURE=Set.copyOf(s);late("barracks",BARRACKS_FURNITURE);}
    /** A barred slit three high in a masonry face. */
    private void slit3(int x,int y0,int z,boolean alongX){as(OTHER);for(int y=y0;y<=y0+2;y++)set(x,y,z,alongX?BARS_X:BARS_Z);}

    // ---------- archery: 11x9 shooting gallery and range, eaves to the street ----------
    /** A framed pavilion on a cobble plinth under a spruce shingle roof whose gables stand one block in; its back is an open gallery on braced posts
     *  facing the range, the roof overhanging a stone shooting line. The range behind: straw butts on posts, a dark oak fence with stone lamp piers at
     *  the far corners. The stock chest stands at (1,1,4) in the gallery, where the guards' chest is looked for (it stood in (1,1,2) before). */
    private void archery(){
        // Round 2 review: one roof material for the set — dark oak shingle, spruce barge boards.
        for(int x=0;x<11;x++)for(int z=0;z<9;z++){
            if(z<=4){as(DECK);set(x,0,z,x>=1&&x<=9&&z>=1?PLANKS:"cobblestone");}
            else{as(OTHER);set(x,0,z,z==5&&x>=1&&x<=9?"stone_brick_slab[type=top,waterlogged=false]":"coarse_dirt");}}
        // Round 1 review: the front one block in under the eave, an entrance porch with its own gable on two posts, the gables closed and edged.
        face(true,1,1,false,0,"P1WPSDSPW4P",2,5,false,2,"cobblestone");
        face(false,0,1,false,1,"PWSP",2,5,false,2,"cobblestone");face(false,10,-1,false,1,"PWSP",2,5,false,2,"cobblestone");
        // The gallery: posts, the beam the roof rests on, knee braces; a tie beam across the middle.
        posts(3,4,1,5);posts(7,4,1,5);beamRun(true,6,4,0,10,BEAM);
        brace(1,5,4,"west");brace(2,5,4,"east");brace(4,5,4,"west");brace(6,5,4,"east");brace(8,5,4,"west");brace(9,5,4,"east");
        beamRun(false,6,5,1,3,POST);
        IntUnaryOperator h=z->6+Math.min(z,5-z);
        roof(false,0,5,0,10,6,(x,z)->false);
        // Gables one block in on tie beams, barge boards at the lot edge.
        // AD-122 review: the gables closed in the wall planes over the plates, barge boards along the verges.
        for(int x:new int[]{0,10}){gable(false,x,1,4,6,h,-1,-1);barge(false,x,1,4,-1,h);}
        IntUnaryOperator g=x->6+Math.min(x-3,7-x);crossRoof(3,7,6,h);
        as(PLINTH);set(4,1,0,"cobblestone");set(6,1,0,"cobblestone");posts(4,0,2,4);posts(6,0,2,4);beamRun(true,5,0,4,6,BEAM);
        gable(true,0,4,6,6,g,5,-1);as(FRAME);set(4,6,0,stair("dark_oak_stairs","west",true));set(6,6,0,stair("dark_oak_stairs","east",true));
        as(OTHER);set(5,4,0,hung());set(5,1,0,"air");set(5,2,0,"air");set(5,3,0,"air");
        // Under the eave: the archers' bench, a rack of bows (fence and trapdoor), straw for the butts.
        set(1,1,0,stair("spruce_stairs","north",false));set(2,1,0,stair("spruce_stairs","north",false));
        set(8,1,0,fence("spruce",true,false,true,false));set(8,2,0,trapdoor("spruce","north",true,false));set(9,1,0,"hay_block[axis=y]");set(10,1,0,"barrel[facing=up,open=false]");
        // The range: butts, the fence with lamp piers at the far corners.
        // Design review 2026-09-24 ("loose fences"): the butts on dark oak posts (a fence post beside a kit of the yard would stand unjoined).
        as(OTHER);for(int x:new int[]{2,5,8}){set(x,1,7,log(POST,'y'));set(x,2,7,"hay_block[axis=y]");}
        for(int x:new int[]{0,10}){set(x,1,8,"stone_bricks");set(x,2,8,"stone_brick_wall[up=true]");set(x,3,8,"lantern[hanging=false,waterlogged=false]");}
        var run=new ArrayList<int[]>();for(int z=5;z<=7;z++){run.add(new int[]{0,z});run.add(new int[]{10,z});}for(int x=1;x<=9;x++)run.add(new int[]{x,8});fences(run,"dark_oak_fence");
        as(OTHER);set(5,5,4,hung());set(5,5,2,hung());
        // Design review 2026-09-24: the range fence joined to the butt posts; the valley stairs where the porch gable meets the main roof take the
        // inner corner the game gives them; a stool in the fletcher's corner, two chairs on the shooting line.
        as(OTHER);for(int x:new int[]{2,5,8})set(x,1,8,"dark_oak_fence[east=true,north=true,south=false,waterlogged=false,west=true]");
        as(ROOF);set(4,7,1,"dark_oak_stairs[facing=south,half=bottom,shape=inner_left]");set(6,7,1,"dark_oak_stairs[facing=south,half=bottom,shape=inner_right]");
        as(OTHER);set(1,1,3,chair(org.villageastra.domain.Furniture.HOME_WOOD,"east"));
        set(2,1,5,chair(org.villageastra.domain.Furniture.HOME_WOOD,"south"));set(8,1,5,chair(org.villageastra.domain.Furniture.HOME_WOOD,"south"));
        door(5,1);as(KIT);set(1,1,4,CHEST);set(3,1,3,"fletching_table");
        slate();
    }
    static{late("archery",Set.of(new Cell(1,1,3),new Cell(2,1,5),new Cell(8,1,5)));}

    // ---------- wall_tower: 5x5 archer tower in the castle wall ----------
    /** AD-094 geometry cell for cell — the spiral of seven treads round the pillar with three blocks of air over each, the platform at y7, the archers'
     *  stands (3,8,3) and (3,8,1), the door and its lane, the chest in the back wall — in castle masonry: cobble-heavy foot, a polished andesite course
     *  at y5, barred slits, merlons two high on the corners and low wall merlons mid-side by the stands, the lamp on a post over the pillar. */
    private void wallTower(){
        as(DECK);for(int x=0;x<5;x++)for(int z=0;z<5;z++)set(x,0,z,"cobblestone");
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){boolean edge=x==0||x==4||z==0||z==4;
            if(edge)stone(true,z,x,x,1,7,true);else{as(OTHER);set(x,7,z,"stone_bricks");}}
        as(OTHER);for(int y=1;y<=6;y++)set(2,y,2,"stone_bricks");
        // The treads {x,z,y}, clockwise from the door, each facing the way up, and the headroom over them (the platform opens above the last).
        int[][] treads={{3,1,1},{3,2,2},{3,3,3},{2,3,4},{1,3,5},{1,2,6},{1,1,7}};String[] up={"east","south","south","west","west","north","north"};
        for(int i=0;i<treads.length;i++){var t=treads[i];set(t[0],t[2],t[1],stair("stone_brick_stairs",up[i],false));
            for(int y=t[2]+1;y<=Math.min(7,t[2]+3);y++)set(t[0],y,t[1],"air");}
        slit(2,3,4,true);slit(0,5,2,false);slit(4,5,2,false);slit(0,2,2,false);slit(4,2,2,false);
        // AD-122 review: a battered foot — the lowest course of every face a stone brick stair falling outward (the door's cell left alone).
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){boolean edge=x==0||x==4||z==0||z==4,corner=(x==0||x==4)&&(z==0||z==4);if(!edge||corner||Math.abs(x-2)<=1&&z==0)continue;// AD-144: full jambs by the door
            as(PLINTH);set(x,1,z,stair("stone_brick_stairs",z==0?"south":z==4?"north":x==0?"east":"west",false));}
        // Merlons 1:1: two high on the corners, a low wall merlon in the middle of each side, where the archers look out.
        // Round 1 review: merlons in an even 1:1 rhythm, all two high (chiseled heads on the corners), over a corbel table of upside-down stairs;
        // a barred slit in every face, the door under an arched head.
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){if(!(x==0||x==4||z==0||z==4)||(x+z)%2!=0)continue;
            as(STONE);set(x,8,z,"stone_bricks");set(x,9,z,x%4==0&&z%4==0?"chiseled_stone_bricks":"stone_brick_slab[type=bottom,waterlogged=false]");}
        for(int x=0;x<5;x++)for(int z=0;z<5;z++){boolean edge=x==0||x==4||z==0||z==4,corner=(x==0||x==4)&&(z==0||z==4);int along=z==0||z==4?x:z;
            if(!edge||corner||along%2==0)continue;String c=m.get(new Cell(x,6,z));if(c==null||c.startsWith("iron_bars"))continue;
            as(STONE);set(x,6,z,stair("stone_brick_stairs",z==0?"south":z==4?"north":x==0?"east":"west",true));}
        slit(2,4,0,true);arch(2,3,0);
        as(OTHER);set(2,8,2,"dark_oak_fence");set(2,9,2,"lantern[hanging=false,waterlogged=false]");
        // Round 2 review ("a roofless box"): a 45-degree hip over the fighting platform, borne on the merlons, the crenels open under it.
        hip45(0,0,4,4,10);
        // Design review 2026-09-24: under the spiral, out of the way to the chest, a spear rack (a post under a shelf) and a bale of straw.
        as(OTHER);set(2,1,3,fence("spruce",true,true,false,false));set(2,2,3,trapdoor("spruce","north",true,false));set(3,1,3,"hay_block[axis=y]");
        door(2,0);as(OTHER);set(2,1,1,"air");set(2,2,1,"air");as(KIT);set(1,1,4,CHEST);
    }
    static{late("wall_tower",Set.of(new Cell(2,1,3),new Cell(2,2,3),new Cell(3,1,3)));}

    // ---------- siege_camp: 11x11 field camp ----------
    /** A spruce palisade, jagged at the top, round a field camp; the gate a dark oak fence gate between taller posts under a lintel. Inside, the fire
     *  on a cobble hearth between log benches, and behind it the tent: spruce fence poles under a spruce shingle ridge tent capped in dark oak, its
     *  back closed in spruce boards, barrels and hay in it. A standard with a board shield by the gate, a lamp post, barrels by the stock chest. */
    private void siegeCamp(){
        // Design review 2026-09-24 (in game "a flat wall of logs with sticks poking over it"): a stockade of sharpened spruce stakes three and four
        // high in a steady rhythm between tall dark oak posts at the corners; in its north-east corner a watch tower — four log posts, stakes on
        // its outer faces, a railed deck at y5 reached by a ladder, a little spruce hip over it with a lamp; the gate between tall posts under a
        // lintel with the standard over it; inside the command tent of white canvas with a red ridge at the back, a small tent by the west
        // stakes, the fire between log benches, the ballista, the weapon rack and the stores. No levels and no kits: the stock chest (1,1,4) and
        // the gate lane are the camp's only fixed cells.
        as(OTHER);for(int x=0;x<11;x++)for(int z=0;z<11;z++)set(x,0,z,x==5&&z<=5||x>=4&&x<=6&&z>=4&&z<=6?"gravel":"coarse_dirt");
        for(int x=0;x<11;x++)for(int z=0;z<11;z++){if(!(x==0||x==10||z==0||z==10)||x==5&&z==0||x>=8&&z<=2)continue;
            boolean corner=(x==0||x==10)&&(z==0||z==10),gatepost=z==0&&(x==4||x==6);
            if(corner||gatepost){for(int y=1;y<=5;y++)set(x,y,z,log(POST,'y'));set(x,6,z,POLE);continue;}
            int along=z==0||z==10?x:z,top=3+(along&1);for(int y=1;y<=top;y++)set(x,y,z,log("spruce_log",'y'));set(x,top+1,z,"spruce_fence");}
        // The gate: a fence gate between the posts under a lintel, the standard over it (a pole with a red and a white cloth).
        set(5,1,0,"dark_oak_fence_gate[facing=south,in_wall=false,open=false,powered=false]");for(int x=4;x<=6;x++)set(x,5,0,log(BEAM,'x'));
        set(5,6,0,POLE);for(int y=7;y<=8;y++)set(5,y,0,fence("dark_oak",false,false,true,false));set(5,8,1,"red_wool");set(5,7,1,"white_wool");
        // The watch tower over x8..10, z0..2.
        for(int x:new int[]{8,10})for(int z:new int[]{0,2})for(int y=1;y<=8;y++)set(x,y,z,log(POST,'y'));
        for(int y=1;y<=4;y++){set(9,y,0,log("spruce_log",'y'));set(10,y,1,log("spruce_log",'y'));set(9,y,2,log("spruce_log",'y'));}
        as(DECK);for(int x=8;x<=10;x++)for(int z=0;z<=2;z++)if(!((x==8||x==10)&&(z==0||z==2)))set(x,5,z,PLANKS);
        as(OTHER);set(9,6,0,"dark_oak_fence");set(10,6,1,"dark_oak_fence");set(8,6,1,"dark_oak_fence");
        for(int y=1;y<=5;y++)set(9,y,3,"ladder[facing=south,waterlogged=false]");
        shingle();hip45(8,0,10,2,9);slate();as(OTHER);set(9,8,1,hung());
        // The command tent over x3..7, z7..9: white canvas stepped up to a red ridge, carpet on the steps so the slope reads smooth, its back
        // closed, the flaps at the front open; a pennant on a pole through the ridge, straw and stores inside under a lamp.
        for(int z=7;z<=9;z++){set(3,1,z,"white_wool");set(7,1,z,"white_wool");set(4,2,z,"white_wool");set(6,2,z,"white_wool");set(5,3,z,"red_wool");
            set(3,2,z,"white_carpet");set(7,2,z,"white_carpet");set(4,3,z,"white_carpet");set(6,3,z,"white_carpet");set(5,4,z,"red_carpet");}
        set(4,1,9,"white_wool");set(5,1,9,"white_wool");set(6,1,9,"white_wool");set(5,2,9,"white_wool");
        set(4,1,7,trapdoor("spruce","west",false,true));set(6,1,7,trapdoor("spruce","east",false,true));
        set(5,4,8,POLE);set(5,5,8,POLE);set(5,6,8,"red_wool");
        set(4,1,8,"hay_block[axis=y]");set(6,1,8,"barrel[facing=up,open=false]");set(5,2,7,hung());
        // A small tent by the west stakes (x1..3, z1..2).
        for(int z=1;z<=2;z++){set(1,1,z,"white_wool");set(3,1,z,"white_wool");set(2,2,z,"white_wool");set(1,2,z,"white_carpet");set(3,2,z,"white_carpet");set(2,3,z,"white_carpet");}
        set(2,1,2,"white_wool");set(2,1,1,trapdoor("spruce","north",false,true));
        // The fire between log benches before the command tent.
        set(5,1,5,"campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");set(3,1,5,"spruce_log[axis=z]");set(7,1,5,"spruce_log[axis=z]");
        // The ballista at the back of the west side: a slab carriage, a log stock across it, fence bow arms, a chain for the string, its
        // throwing arm raised with the sling cup at its head.
        // All-sides review: a row off the back stakes and on a log post, so its bow arms join nothing but the stock.
        for(int z=6;z<=8;z++)set(1,1,z,"spruce_slab[type=top,waterlogged=false]");
        set(1,2,7,log("stripped_spruce_log",'x'));set(2,2,7,log("stripped_spruce_log",'x'));set(2,1,7,log("stripped_spruce_log",'y'));
        set(2,2,6,fence("spruce",false,false,true,false));set(2,2,8,fence("spruce",false,true,false,false));set(1,3,7,"chain[axis=x,waterlogged=false]");
        for(int y=3;y<=5;y++)set(2,y,7,log("stripped_spruce_log",'y'));set(2,6,7,"spruce_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        // A weapon rack along the east stakes: fence posts with trapdoor shelves and a barrel of bolts; a lamp post by the fire; stores by the chest.
        for(int z=5;z<=7;z++){set(9,1,z,"spruce_fence");set(9,2,z,trapdoor("spruce","west",true,false));}set(9,1,8,"barrel[facing=up,open=false]");
        lanternPost(7,3);
        set(1,1,3,"barrel[facing=up,open=false]");set(1,1,5,"barrel[facing=up,open=false]");set(1,2,5,"barrel[facing=up,open=false]");
        as(KIT);set(1,1,4,CHEST);
        joinFences();
    }
    /** Design review 2026-09-24 ("41 loose fences"): every bare fence of the camp takes the sides the game would join after an update — to a fence
     *  beside it, to a gate in line, to a full block (log, planks, straw, barrel) — so the palisade spikes, poles and racks stand joined in a
     *  world generated without updates. */
    private void joinFences(){
        String[] side={"east","north","south","west"};int[][] step={{1,0},{0,-1},{0,1},{-1,0}};var bare=new ArrayList<Cell>();
        for(var e:m.entrySet())if(e.getValue().endsWith("_fence"))bare.add(e.getKey());
        for(var c:bare){var was=roles.get(c);var sb=new StringBuilder(m.get(c)).append('[');
            for(int i=0;i<4;i++){String n=m.get(new Cell(c.x()+step[i][0],c.y(),c.z()+step[i][1]));boolean j=false;
                if(n!=null&&!n.equals("air")){if(n.contains("fence_gate"))j=(step[i][0]!=0)==(n.contains("facing=north")||n.contains("facing=south"));
                    else if(n.contains("_fence"))j=true;
                    else j=(n.contains("_log")||n.contains("_planks")||n.startsWith("hay_block")||n.startsWith("barrel")||n.equals("cobblestone"))&&!n.contains("stairs")&&!n.contains("slab");}
                sb.append(side[i]).append('=').append(j).append(',');}
            as(was);set(c.x(),c.y(),c.z(),sb.append("waterlogged=false]").toString());}
        as(OTHER);}


    /** 2:1 slate pyramid over walls x0..x1 by z0..z1 (castle {@code hip}): eave one block out on stone brackets, rings two stairs high, a spire. */
    private void pyramid(int x0,int z0,int x1,int z1,int y0){
        int ex0=x0-1,ez0=z0-1,ex1=x1+1,ez1=z1+1,K=Math.min((ex1-ex0)/2,(ez1-ez0)/2);
        for(int x=ex0;x<=ex1;x++)for(int z=ez0;z<=ez1;z++){
            int ix=Math.min(x-ex0,ex1-x),iz=Math.min(z-ez0,ez1-z),k=Math.min(ix,iz);
            String f=ix<=iz?(x-ex0<=ex1-x?"east":"west"):(z-ez0<=ez1-z?"south":"north");
            as(ROOF);
            if(k==K){for(int y=y0;y<=y0+2*K-2;y++)set(x,y,z,TILES);as(OTHER);set(x,y0+2*K-1,z,"stone_brick_wall[up=true]");set(x,y0+2*K,z,"dark_oak_fence");set(x,y0+2*K+1,z,"dark_oak_fence");continue;}
            if(k==0){set(x,y0,z,stair(TILE,f,false));if((x+z)%2==0){as(FRAME);setIfEmpty(x,y0-1,z,stair("stone_brick_stairs",f,true));}continue;}
            for(int y=y0;y<=y0+2*k-3;y++)set(x,y,z,TILES);set(x,y0+2*k-2,z,stair(TILE,f,false));set(x,y0+2*k-1,z,stair(TILE,f,false));}
    }

    /** Round 1 review: a 45-degree hip of roof stairs over x0..x1 by z0..z1 (odd spans) from the eave course y0, outer corner stairs on the hips,
     *  the ridge (or the apex of a square) in tiles under a cap slab — no stepped full blocks. */
    private void hip45(int x0,int z0,int x1,int z1,int y0){
        int K=Math.min((x1-x0)/2,(z1-z0)/2);as(ROOF);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){
            int ix=Math.min(x-x0,x1-x),iz=Math.min(z-z0,z1-z),k=Math.min(ix,iz);boolean lx=x-x0<=x1-x,lz=z-z0<=z1-z;
            if(k==K){if(K>0)set(x,y0+K-1,z,rf);set(x,y0+K,z,rc);continue;}
            String st;
            if(ix==iz)st=rs+"[facing="+(lx?"east":"west")+",half=bottom,shape="+(lx==lz?"outer_right":"outer_left")+"]";
            else if(ix<iz)st=stair(rs,lx?"east":"west",false);
            else st=stair(rs,lz?"south":"north",false);
            set(x,y0+k,z,st);if(k>0)setIfEmpty(x,y0+k-1,z,rf);}
    }

    // ---------- offline check: `java VillageStyle.java <outDir>` writes <id>.json for voxel.py ----------
    public static void main(String[] args)throws Exception{
        Path out=Path.of(args.length>0?args[0]:".");
        var all=new ArrayList<>(ids());for(int n=2;n<=6;n++){all.add("forester@"+n);all.add("warehouse@"+n);}
        for(var id:all){var p=of(id);write(p,out.resolve(id.replace('@','_')+".json"));
            Map<Character,Integer> byRole=new TreeMap<>();for(var e:p.m.entrySet())if(!e.getValue().equals("air"))byRole.merge(p.roles.get(e.getKey()),1,Integer::sum);
            System.out.println(id+" "+p.w+"x"+p.d+" roles="+byRole);}
    }
    private static void write(VillageStyle p,Path file)throws Exception{
        int h=0;for(var c:p.m.keySet())h=Math.max(h,c.y()+1);
        Map<String,Character> legend=new LinkedHashMap<>();String pool="#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&'()*+,-/:;<=>?@[]^_`{|}~";
        char[][][] grid=new char[h][p.d][p.w];for(var a:grid)for(var r:a)Arrays.fill(r,' ');
        for(var e:p.m.entrySet()){if(e.getKey().y()<0)continue;String s=e.getValue().equals(CHEST)?"chest[facing=north]":e.getValue().startsWith(CHEST)?e.getValue().substring(1):e.getValue();
            Character ch=legend.get(s);if(ch==null){ch=pool.charAt(legend.size());legend.put(s,ch);}
            grid[e.getKey().y()][e.getKey().z()][e.getKey().x()]=ch;}
        var sb=new StringBuilder("{\"name\":\""+p.id+"\",\"legend\":{");boolean first=true;
        for(var e:legend.entrySet()){if(!first)sb.append(',');first=false;sb.append('"').append(e.getValue()).append("\":\"").append(e.getKey()).append('"');}
        sb.append("},\"layers\":[");
        for(int y=0;y<h;y++){if(y>0)sb.append(',');sb.append('[');for(int z=0;z<p.d;z++){if(z>0)sb.append(',');sb.append('"');for(int x=0;x<p.w;x++){char c=grid[y][z][x];sb.append(c=='"'||c=='\\'?"\\"+c:String.valueOf(c));}sb.append('"');}sb.append(']');}
        sb.append("]}");Files.writeString(file,sb.toString(),StandardCharsets.UTF_8);}
}
