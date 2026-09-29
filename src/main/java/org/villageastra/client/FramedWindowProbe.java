package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.villageastra.VillageAstra;
import org.villageastra.domain.FramedWindows;
import org.villageastra.server.SettlementData;
import org.villageastra.world.FramedWindowBlock;
/** AD-142 in the real client: a plaster wall of the village style (smooth sandstone between stripped dark oak posts and beam) with a dark oak
 *  door and a row of framed windows of all eleven woods beside it, a short return wall of windows turned along Z, and one window alone between
 *  two plaster blocks to be read from its side. Every wood's block model and item model must be real (not the missing model). Frames: front,
 *  angle, close by the door, side, from behind, and the hotbar with the items. Joined windows (owner 2026-09-24): a second wall with a dark oak
 *  window 3 wide and 2 high, an oak 2x2 and a spruce L, and the gallery's home laid from its design; frames of both straight on and at an angle. */
final class FramedWindowProbe {
 private static int phase,ticks;private static volatile String failure;private static volatile boolean ready;private static BlockPos lawn;
 private static final List<String> SHOTS=new ArrayList<>();private static volatile String facts="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.framedWindowProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-framed-window-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}SHOTS.add(suffix);LogUtils.getLogger().info("ASTRA_FRAMED_WINDOW screenshot {}",path);}
 private static volatile int homeWidth;
 /** The joins of a window by name in FramedWindowBlock's order, e.g. "up right up_right". */
 private static String joins(BlockState s){var out=new StringJoiner(" ");
  for(var p:List.of(FramedWindowBlock.UP,FramedWindowBlock.DOWN,FramedWindowBlock.LEFT,FramedWindowBlock.RIGHT,FramedWindowBlock.UP_LEFT,FramedWindowBlock.UP_RIGHT,FramedWindowBlock.DOWN_LEFT,FramedWindowBlock.DOWN_RIGHT))
   if(s.getValue(p))out.add(p.getName());return out.toString();}
 private static BlockState window(String wood,Direction.Axis axis){return VillageAstra.FRAMED_WINDOWS.get(wood).get().defaultBlockState().setValue(FramedWindowBlock.AXIS,axis);}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();var p=server.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);l.setWeatherParameters(6000,0,false,false);l.setDayTime(6000);
  lawn=e.center().offset(-60,0,-50);
  for(int x=-10;x<46;x++)for(int z=-16;z<14;z++){var g=lawn.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),2);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<20;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),2);}
  var plaster=Blocks.SMOOTH_SANDSTONE.defaultBlockState();var post=Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
  var beam=post.setValue(RotatedPillarBlock.AXIS,Direction.Axis.X);
  // The wall along X at z=0: posts at 0 and 14, a beam on top, the door at 7, the windows at eye height on both sides of it.
  for(int x=0;x<=14;x++)for(int y=1;y<=4;y++)l.setBlock(lawn.offset(x,y,0),x==0||x==14?post:y==4||x==7&&y==3?beam:plaster,2);
  var door=Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HINGE,DoorHingeSide.LEFT);
  l.setBlock(lawn.offset(7,1,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);l.setBlock(lawn.offset(7,2,0),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
  // Dark oak (the village's) on both sides of the door, the other ten woods outward.
  var order=new ArrayList<String>(FramedWindows.WOODS);order.remove("dark_oak");
  int[] cells={6,8,5,9,4,10,3,11,2,12,1,13};
  for(int i=0;i<cells.length;i++){String wood=i<2?"dark_oak":order.get(i-2);l.setBlock(lawn.offset(cells[i],2,0),window(wood,Direction.Axis.X),2);}
  // A return wall along Z in front and to the left, its windows turned along Z.
  for(int z=-6;z<=-2;z++)for(int y=1;y<=3;y++)l.setBlock(lawn.offset(-3,y,z),z==-6||z==-2?post:plaster,2);
  l.setBlock(lawn.offset(-3,2,-5),window("dark_oak",Direction.Axis.Z),2);l.setBlock(lawn.offset(-3,2,-4),window("oak",Direction.Axis.Z),2);l.setBlock(lawn.offset(-3,2,-3),window("spruce",Direction.Axis.Z),2);
  // One window alone between two plaster blocks, to be read from its side.
  l.setBlock(lawn.offset(20,1,-4),plaster,2);l.setBlock(lawn.offset(20,2,-4),window("dark_oak",Direction.Axis.X),2);l.setBlock(lawn.offset(20,3,-4),plaster,2);
  int placed=0;var woods=new TreeSet<String>();
  for(int x=-4;x<=21;x++)for(int z=-7;z<=1;z++)for(int y=1;y<=4;y++){var s=l.getBlockState(lawn.offset(x,y,z));if(s.getBlock() instanceof FramedWindowBlock w){placed++;woods.add(w.wood());}}
  // Owner 2026-09-24, joined windows: a plaster wall along X at z 8 with a dark oak window 3 wide and 2 high, an oak 2x2 and a spruce L,
  // each window set as a player sets it (joins read from the neighbours, neighbour updates on).
  for(int x=16;x<=26;x++)for(int y=1;y<=4;y++)l.setBlock(lawn.offset(x,y,8),x==16||x==26?post:y==4?beam:plaster,2);
  int[][] group={{17,2,0},{18,2,0},{19,2,0},{17,3,0},{18,3,0},{19,3,0},{21,2,1},{22,2,1},{21,3,1},{22,3,1},{24,2,2},{25,2,2},{24,3,2}};
  String[] groupWood={"dark_oak","oak","spruce"};
  for(var g:group){var at=lawn.offset(g[0],g[1],8);l.setBlock(at,FramedWindowBlock.connect(window(groupWood[g[2]],Direction.Axis.X),at,l::getBlockState),3);}
  int joined=0;var want=new String[]{"up right up_right","up left right up_left up_right","up left up_left","down right down_right","down left right down_left down_right","down left down_left",
   "up right up_right","up left up_left","down right down_right","down left down_left","up right","left","down"};
  var got=new ArrayList<String>();
  for(int i=0;i<group.length;i++){var j=joins(l.getBlockState(lawn.offset(group[i][0],group[i][1],8)));got.add(j);if(j.equals(want[i]))joined++;}
  // A house of the gallery (home, level I) laid from its design as the starter village lays it (flags 2): its windows as designed.
  var home=lawn.offset(30,0,-2);var design=org.villageastra.world.BuildingBlueprints.layout("home",home);design.forEach((pos,s)->l.setBlock(pos,s,2));
  int homeWindows=0,homeRight=0,homeJoined=0;
  for(var c:design.entrySet())if(c.getValue().getBlock() instanceof FramedWindowBlock){homeWindows++;if(l.getBlockState(c.getKey())==c.getValue())homeRight++;if(!joins(c.getValue()).isEmpty())homeJoined++;}
  homeWidth=org.villageastra.world.BuildingBlueprints.design("home").width();
  facts="placed="+placed+" woods="+woods.size()+" joined="+joined+"/"+group.length+" home="+homeRight+"/"+homeWindows+" homeJoined="+homeJoined;
  if(joined!=group.length)LogUtils.getLogger().info("ASTRA_FRAMED_WINDOW joins {}",got);
  p.setGameMode(GameType.SPECTATOR);ready=true;
  LogUtils.getLogger().info("ASTRA_FRAMED_WINDOW fixture wall at {}: {}",lawn.toShortString(),facts);
 }catch(Exception ex){failure=ex.toString();}});}
 /** Every wood's block model on both axes and its item model are the real ones, not the missing model. */
 private static String models(Minecraft mc){var missing=mc.getModelManager().getMissingModel();var bad=new ArrayList<String>();
  for(var wood:FramedWindows.WOODS){for(var axis:List.of(Direction.Axis.X,Direction.Axis.Z))if(mc.getBlockRenderer().getBlockModel(window(wood,axis))==missing)bad.add(wood+"/"+axis);
   if(mc.getItemRenderer().getModel(new ItemStack(VillageAstra.FRAMED_WINDOW_ITEMS.get(wood).get()),null,null,0)==missing)bad.add(wood+"/item");}
  return bad.isEmpty()?"":bad.toString();}
 private static void look(Minecraft mc,double x,double y,double z,float yaw,float pitch){var server=mc.getSingleplayerServer();
  server.execute(()->server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),lawn.getX()+x,lawn.getY()+y,lawn.getZ()+z,yaw,pitch));}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>4000)throw new IllegalStateException("Framed window timeout phase="+phase);
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready){var bad=models(mc);if(!bad.isEmpty())throw new IllegalStateException("Missing models: "+bad);
   if(!facts.matches("placed=16 woods=11 joined=13/13 home=(\\d+)/\\1 homeJoined=[1-9]\\d*"))throw new IllegalStateException("Fixture: "+facts);
   mc.options.hideGui=true;look(mc,7.5,1.0,-9.5,0,4);phase=2;ticks=0;}
  else if(phase==2&&ticks>100){capture(mc,"front");look(mc,-7.5,3.4,-12.5,-49,10);phase=3;ticks=0;}
  else if(phase==3&&ticks>60){capture(mc,"angle");look(mc,4.0,1.0,-3.5,-41,6);phase=4;ticks=0;}
  else if(phase==4&&ticks>60){capture(mc,"door-close");look(mc,24.5,1.0,-3.5,90,4);phase=5;ticks=0;}
  else if(phase==5&&ticks>60){capture(mc,"side");look(mc,7.5,1.0,7.5,180,4);phase=6;ticks=0;}
  else if(phase==6&&ticks>60){capture(mc,"back");look(mc,21.5,1.4,3.5,0,0);phase=60;ticks=0;}
  // Joined windows: the 3x2, the 2x2 and the L straight on and at an angle, then the home from the gallery close by, front and corner.
  else if(phase==60&&ticks>60){capture(mc,"joined");look(mc,15.5,1.4,3.0,-48,2);phase=61;ticks=0;}
  else if(phase==61&&ticks>60){capture(mc,"joined-angle");look(mc,30+homeWidth/2.0,3.5,-12.5,0,-15);phase=62;ticks=0;}
  else if(phase==62&&ticks>60){capture(mc,"home");look(mc,23.5,3.5,-9.5,-35,-12);phase=63;ticks=0;}
  else if(phase==63&&ticks>60){capture(mc,"home-angle");
   // The items: creative hotbar with nine woods, the camera on the wall again.
   var server=mc.getSingleplayerServer();server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);
    for(int i=0;i<9;i++)p.getInventory().setItem(i,new ItemStack(VillageAstra.FRAMED_WINDOW_ITEMS.get(FramedWindows.WOODS.get(i)).get()));p.getInventory().selected=5;});
   mc.options.hideGui=false;look(mc,7.5,1.0,-7.5,0,4);phase=7;ticks=0;}
  else if(phase==7&&ticks>60){capture(mc,"hotbar");
   LogUtils.getLogger().info("ASTRA_FRAMED_WINDOW VERIFIED {} models=11x3 frames={}; reload=false",facts,String.join(",",SHOTS));
   mc.stop();phase=8;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_FRAMED_WINDOW FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
