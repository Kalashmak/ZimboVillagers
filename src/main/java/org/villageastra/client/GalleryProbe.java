package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-071: the residents' houses as they stand in the world — each design is set down on a clear lawn beside the village and photographed
 *  without the interface. Only for looking at the architecture; nothing is registered to the settlement.
 *  Design review (owner 2026-09-24): a building is judged from every side and from inside, never from one far corner — every design gets
 *  the four facades straight on, the four corners from further off, a view from above and two views inside each storey (from opposite
 *  corners of the rooms, the camera a spectator so it may stand anywhere, brightness up). {@code -PgalleryViews=facades,corners,top,inside}
 *  picks a subset. */
final class GalleryProbe {
 private static final List<String> DESIGNS=System.getProperty("villageastra.galleryDesigns")!=null
   ?List.of(System.getProperty("villageastra.galleryDesigns").split(","))
   :Boolean.getBoolean("villageastra.galleryLevels")
   ?List.of("home","home@3","home@6","restaurant","restaurant@3","restaurant@6"):List.of("home","home_2");
 private static final Set<String> KINDS=Set.of(System.getProperty("villageastra.galleryViews","facades,corners,top,inside").split(","));
 // Round 1 review: designs stand 18 apart, so a neighbour does not fill the foreground of the next one's view.
 private static final int GAP=18;
 private static int phase,ticks,shot;private static volatile String failure;private static volatile boolean ready;private static volatile BlockPos lawn;
 private static double gamma=-1;
 static boolean enabled(){return Boolean.getBoolean("villageastra.gallerySmoke");}
 // AD-121 preview: castle_preview_N ids are 37x37 castle shells; each gets four corner views and a far view from the gate side.
 private static final int CASTLE_SPACING=150;private static final String[] CASTLE_VIEWS={"nw","ne","se","sw","far"};
 private static boolean castles(){return DESIGNS.stream().allMatch(CastleArchitecture::isPreview);}
 private static void castleFixture(net.minecraft.server.level.ServerLevel l){
  for(int i=0;i<DESIGNS.size();i++){var origin=lawn.offset(i*CASTLE_SPACING,0,0);
   for(int x=-26;x<64;x++)for(int z=-26;z<64;z++){var ground=origin.offset(x,0,z);for(int y=1;y<=4;y++)l.setBlock(ground.below(y),Blocks.DIRT.defaultBlockState(),2);
    l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<46;y++)l.setBlock(ground.above(y),Blocks.AIR.defaultBlockState(),2);}
   for(var cell:CastleArchitecture.shell(CastleArchitecture.previewLevel(DESIGNS.get(i)),origin).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}}
 private static void castleLook(Minecraft mc,int index,int view){double cx=lawn.getX()+index*CASTLE_SPACING+CastleArchitecture.LOT/2.0,cz=lawn.getZ()+CastleArchitecture.LOT/2.0,y=lawn.getY();
  // Levels I..III frame the hall and keep closely; IV..VI the whole lot from higher up so the courtyard shows.
  boolean near=CastleArchitecture.previewLevel(DESIGNS.get(index))<=3;double d=near?21:27,h=near?20:34;float pitch=near?15:28;
  if(near){cx+=1;cz+=2.5;}
  switch(view){case 0->look(mc,cx-d,y+h,cz-d,-45,pitch);case 1->look(mc,cx+d,y+h,cz-d,45,pitch);case 2->look(mc,cx+d,y+h,cz+d,135,pitch);case 3->look(mc,cx-d,y+h,cz+d,-135,pitch);default->look(mc,cx,y+20,cz-60,0,10);}}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-gallery-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_GALLERY screenshot {}",path);}

 /** One camera position of the review: where, which way, and whether it is an inside view (brightness up). */
 private record View(String name,double x,double y,double z,float yaw,float pitch,boolean inside){}
 private static final List<View> VIEWS=new ArrayList<>();
 /** Storeys of a design: floor heights where at least four cells are air over a solid block under a roof; two views each. */
 static List<Integer> floors(Map<BlockPos,BlockState> m){
  var solid=new HashSet<BlockPos>();var top=new HashMap<Long,Integer>();
  for(var e:m.entrySet())if(!e.getValue().isAir()){var p=e.getKey();solid.add(p);top.merge(((long)p.getX()<<32)^(p.getZ()&0xffffffffL),p.getY(),Math::max);}
  var count=new TreeMap<Integer,Integer>();
  for(var p:solid){var up=p.above();if(solid.contains(up)||solid.contains(up.above()))continue;
   if(top.getOrDefault(((long)p.getX()<<32)^(p.getZ()&0xffffffffL),-99)>p.getY()+2)count.merge(p.getY()+1,1,Integer::sum);}
  var out=new ArrayList<Integer>();for(var e:count.entrySet())if(e.getValue()>=4&&(out.isEmpty()||e.getKey()-out.get(out.size()-1)>=3))out.add(e.getKey());
  return out;
 }
 /** The review's camera positions for a design standing with its lot corner at {@code base}. */
 private static void plan(String design,BlockPos base){
  var d=BuildingBlueprints.design(design);var m=BuildingBlueprints.layout(design,BlockPos.ZERO);
  int top=0;for(var e:m.entrySet())if(!e.getValue().isAir())top=Math.max(top,e.getKey().getY());
  double w=d.width(),dep=d.depth(),cx=base.getX()+w/2,cz=base.getZ()+dep/2,gy=base.getY();String id=design.replace('@','-');
  // Straight on: far enough that the whole face and roof fit a 70-degree view, eye at a third of the height.
  if(KINDS.contains("facades")){double r=Math.max(Math.max(w,dep)*.9,top*1.05)+4,h=gy+Math.max(2.5,top*.38);
   VIEWS.add(new View(id+"-south",cx,h,base.getZ()+dep+r,180,8,false));VIEWS.add(new View(id+"-north",cx,h,base.getZ()-r,0,8,false));
   VIEWS.add(new View(id+"-east",base.getX()+w+r,h,cz,90,8,false));VIEWS.add(new View(id+"-west",base.getX()-r,h,cz,-90,8,false));}
  // First run of the review: the corners from 0.85x the span stood too far off (the building a quarter of the frame); closer now.
  if(KINDS.contains("corners")){double r=Math.max(w,dep)*.6+top*.5+3,h=gy+top*.5+r*.36;
   VIEWS.add(new View(id+"-nw",cx-r*.7,h,cz-r*.7,-45,24,false));VIEWS.add(new View(id+"-ne",cx+r*.7,h,cz-r*.7,45,24,false));
   VIEWS.add(new View(id+"-se",cx+r*.7,h,cz+r*.7,135,24,false));VIEWS.add(new View(id+"-sw",cx-r*.7,h,cz+r*.7,-135,24,false));}
  if(KINDS.contains("top"))VIEWS.add(new View(id+"-top",cx,gy+top+Math.max(w,dep)*1.3+4,cz+.01,180,90,false));
  if(KINDS.contains("inside"))for(int y:floors(m)){
   // Cells a person could stand in on this storey: air there and above, a solid floor under, a roof somewhere over.
   var cells=new ArrayList<BlockPos>();
   for(var e:m.entrySet()){var p=e.getKey();if(p.getY()!=y||!e.getValue().isAir())continue;var air=Blocks.AIR.defaultBlockState();
    if(!m.getOrDefault(p.above(),air).isAir()||m.getOrDefault(p.below(),air).isAir())continue;
    boolean roof=false;for(int k=2;k<12&&!roof;k++)roof=!m.getOrDefault(p.above(k),air).isAir();
    // Inside: a wall within eight blocks on every side at eye height (a cell under an eave or a jetty is outside).
    boolean shut=true;for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL){boolean wall=false;for(int k=1;k<=8&&!wall;k++)wall=!m.getOrDefault(p.above().relative(dir,k),air).isAir();shut&=wall;}
    if(roof&&shut)cells.add(p);}
   if(cells.size()<4)continue;
   double mx=cells.stream().mapToInt(BlockPos::getX).average().orElse(0),mz=cells.stream().mapToInt(BlockPos::getZ).average().orElse(0);
   var a=cells.stream().max(Comparator.comparingDouble(p->Math.pow(p.getX()-mx,2)+Math.pow(p.getZ()-mz,2))).orElseThrow();
   var b=cells.stream().max(Comparator.comparingDouble(p->Math.pow(p.getX()-a.getX(),2)+Math.pow(p.getZ()-a.getZ(),2))).orElseThrow();
   for(var p:List.of(a,b)){double px=base.getX()+p.getX()+.5,pz=base.getZ()+p.getZ()+.5,py=gy+y+.1;
    // Look at the far end of the storey across its middle, slightly down.
    var q=p==a?b:a;double tx=base.getX()+q.getX()+.5,tz=base.getZ()+q.getZ()+.5;float yaw=(float)Math.toDegrees(Math.atan2(-(tx-px),tz-pz));
    VIEWS.add(new View(id+"-inside-y"+y+(p==a?"a":"b"),px,py,pz,yaw,14,true));}}
 }
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();
  l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server);l.setDayTime(5000);l.setWeatherParameters(1000000,0,false,false);
  lawn=e.center().offset(-60,0,castles()?140:40);
  if(castles()){castleFixture(l);LogUtils.getLogger().info("ASTRA_GALLERY fixture {} on a lawn at {}",DESIGNS,lawn.toShortString());ready=true;return;}
  // A spectator camera goes through walls, so the inside views can stand anywhere and nothing is trodden on.
  server.getPlayerList().getPlayers().get(0).setGameMode(GameType.SPECTATOR);
  // The lawn runs past the last design, whatever the list, and clears the air to above the tallest roof.
  int span=6;for(var design:DESIGNS)span+=BuildingBlueprints.design(design).width()+GAP;
  for(int x=-30;x<Math.max(44,span)+24;x++)for(int z=-36;z<50;z++){var ground=lawn.offset(x,0,z);l.setBlock(ground.below(),Blocks.DIRT.defaultBlockState(),2);l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<40;y++)l.setBlock(ground.above(y),Blocks.AIR.defaultBlockState(),2);}
  int dx=0;for(var design:DESIGNS){var at=lawn.offset(dx,1,0);
   // Placed with neighbour updates, as a builder places them, so panes and stairs join like in a built house.
   for(var cell:BuildingBlueprints.layout(design,at.below()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
   // Round 1 review ("the farm's trade does not read"): a farm stands in the world with its field behind it — the first 9x9 module of wheat
   // round its water source, as FarmField lays it out.
   if(BuildingBlueprints.base(design).equals("farm"))for(var m:FarmField.modules(BuildingBlueprints.level(design))){if(FarmField.floor(m)>0)continue;
    for(var cell:FarmField.localCells(List.of(m))){l.setBlock(at.below().offset(cell.getX(),0,cell.getZ()),Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7),2);
     l.setBlock(at.below().offset(cell.getX(),1,cell.getZ()),Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE,7),2);}
    var w=FarmField.localWater(m);l.setBlock(at.below().offset(w.getX(),0,w.getZ()),Blocks.WATER.defaultBlockState(),2);}
   plan(design,at.below());
   dx+=BuildingBlueprints.design(design).width()+GAP;}
  LogUtils.getLogger().info("ASTRA_GALLERY fixture {} on a lawn at {}; {} views",DESIGNS,lawn.toShortString(),VIEWS.size());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void look(Minecraft mc,double x,double y,double z,float yaw,float pitch){var server=mc.getSingleplayerServer();
  // A long gallery outlasts any clear spell: every view is taken at the same noon light without rain.
  server.execute(()->{var l=server.overworld();l.setDayTime(5000);l.setWeatherParameters(1000000,0,false,false);
   var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(l,x,y,z,yaw,pitch);});}
 private static void bright(Minecraft mc,boolean on){if(gamma<0)gamma=mc.options.gamma().get();mc.options.gamma().set(on?1.0:gamma);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>4000)throw new IllegalStateException("Gallery timeout phase="+phase);
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);mc.options.hideGui=true;}
  else if(phase==1&&ready&&ticks>60&&castles()){castleLook(mc,shot/CASTLE_VIEWS.length,shot%CASTLE_VIEWS.length);phase=2;ticks=0;}
  else if(phase==2&&ticks>120&&castles()){
   capture(mc,DESIGNS.get(shot/CASTLE_VIEWS.length)+"-"+CASTLE_VIEWS[shot%CASTLE_VIEWS.length]);shot++;
   if(shot<DESIGNS.size()*CASTLE_VIEWS.length){phase=1;ticks=0;}
   else{LogUtils.getLogger().info("ASTRA_GALLERY VERIFIED {} views of {}; reload=false",shot,DESIGNS);mc.options.hideGui=false;mc.stop();phase=3;}}
  else if(castles()){}
  else if(phase==1&&ready&&ticks>40){
   if(VIEWS.isEmpty())throw new IllegalStateException("No views planned for "+DESIGNS);
   var v=VIEWS.get(shot);bright(mc,v.inside());look(mc,v.x(),v.y(),v.z(),v.yaw(),v.pitch());phase=2;ticks=0;}
  else if(phase==2&&ticks>70){
   capture(mc,VIEWS.get(shot).name());shot++;
   if(shot<VIEWS.size()){phase=1;ticks=0;}
   else{bright(mc,false);LogUtils.getLogger().info("ASTRA_GALLERY VERIFIED {} views of {}; reload=false",shot,DESIGNS);mc.options.hideGui=false;mc.stop();phase=3;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_GALLERY FAILED",ex);mc.stop();}}
}
