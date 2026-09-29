package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** QUEST-003 (AD-106): a companion in the live game. A lake too wide to swim: it waits for a boat and never steps into the water; given one it
 *  rows across and lands beside its player; its player goes through a real nether portal and it follows through the same portal after
 *  them, with nobody left in the overworld to keep that ground ticking but its own note. Every step is its own. */
final class EscortProbe {
 private static int phase,ticks;
 private static volatile String failure,progress="";
 private static volatile UUID companion;
 private static volatile BlockPos origin,cell;
 private static volatile Boat boat;
 private static volatile boolean ready,waited,rowed,landed,noted,crossed;
 private static volatile double rowedBy,landedAt,followAt,maxStep,boatStart;
 private static volatile Vec3 last;private static volatile Object lastVehicle;private static volatile int sinceMount=99;
 static boolean enabled(){return Boolean.getBoolean("villageastra.escortSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-escort-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}
  LogUtils.getLogger().info("ASTRA_ESCORT screenshot {}",path);
 }
 private static ServerPlayer player(MinecraftServer s){return s.getPlayerList().getPlayers().get(0);}
 private static ResidentEntity find(MinecraftServer s){
  for(var l:s.getAllLevels())if(l.getEntity(companion) instanceof ResidentEntity r)return r;
  return null;
 }
 private static void put(ServerLevel l,int x,int y,int z,BlockState state){l.setBlockAndUpdate(origin.offset(x,y,z),state);}
 private static void fill(ServerLevel l,int x0,int y0,int z0,int x1,int y1,int z1,BlockState s){for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)put(l,x,y,z,s);}
 /** The same walled lake as the GameTests: land west to x5, water two deep to x16, land east; feet on land at y5 above the origin. */
 private static void build(ServerLevel l){
  var stone=Blocks.STONE.defaultBlockState();
  fill(l,1,2,2,29,14,14,Blocks.AIR.defaultBlockState());fill(l,1,1,2,29,2,14,stone);
  for(int y=2;y<=6;y++){fill(l,1,y,2,29,y,2,stone);fill(l,1,y,14,29,y,14,stone);fill(l,1,y,2,1,y,14,stone);fill(l,29,y,2,29,y,14,stone);}
  fill(l,2,3,3,5,4,13,stone);fill(l,6,3,3,16,4,13,Blocks.WATER.defaultBlockState());fill(l,17,3,3,28,4,13,stone);
  // A nether portal on the east bank, its frame standing on the ground.
  for(int x=0;x<4;x++)for(int y=0;y<5;y++)put(l,22+x,4+y,8,x==0||x==3||y==0||y==4?Blocks.OBSIDIAN.defaultBlockState():Blocks.AIR.defaultBlockState());
  for(int x=1;x<3;x++)for(int y=1;y<4;y++)put(l,22+x,4+y,8,Blocks.NETHER_PORTAL.defaultBlockState());
  cell=origin.offset(23,5,8);
 }
 private static void step(ResidentEntity c){
  var here=c.position();
  if(lastVehicle!=c.getVehicle())sinceMount=0;else sinceMount++;
  if(last!=null&&c.level().dimension()==Level.OVERWORLD&&sinceMount>2){double moved=here.distanceTo(last);maxStep=Math.max(maxStep,moved);
   if(moved>8)failure=String.format(Locale.ROOT,"The companion jumped %.1f blocks: %s -> %s",moved,last,here);}
  last=c.level().dimension()==Level.OVERWORLD?here:null;lastVehicle=c.getVehicle();
 }
 /** T67: the same world opened again after the game was closed. The companion who went through the portal is one entity, still in the world it
  *  crossed into, still its player's, with its escort saved — not lost, not left behind in the overworld and not copied into both. */
 /** The ground where the companion stood and the ground round the portal it came from, brought into the world and held there. */
 private static void wake(Minecraft mc)throws Exception{
  var server=mc.getSingleplayerServer();var line=java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("astra-escort-companion.txt")).trim().split(" ");
  var at=new BlockPos(Integer.parseInt(line[3]),Integer.parseInt(line[4]),Integer.parseInt(line[5]));
  var portal=new BlockPos(Integer.parseInt(line[6]),0,Integer.parseInt(line[7]));
  server.execute(()->{try{
   for(var l:server.getAllLevels()){var about=l.dimension()==Level.NETHER?at:portal;
    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setChunkForced((about.getX()>>4)+x,(about.getZ()>>4)+z,true);}
   LogUtils.getLogger().info("ASTRA_ESCORT the ground of {} and of the portal is brought back into the world",at.toShortString());
  }catch(Exception ex){failure=ex.toString();}});
 }
 private static void afterReload(Minecraft mc)throws Exception{
  var server=mc.getSingleplayerServer();var line=java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("astra-escort-companion.txt")).trim().split(" ");
  var id=UUID.fromString(line[0]);var crossedInto=line[1];
  server.execute(()->{try{
   var found=new java.util.ArrayList<String>();ResidentEntity one=null;
   for(var l:server.getAllLevels())for(var e:l.getEntities().getAll())if(e instanceof ResidentEntity r&&r.getUUID().equals(id)){found.add(l.dimension().location().toString());one=r;}
   if(found.size()!=1)throw new IllegalStateException("The companion is "+(found.isEmpty()?"lost":"in two worlds at once: "+found));
   if(!found.get(0).equals(crossedInto))throw new IllegalStateException("The companion is in "+found.get(0)+", not in the world it crossed into ("+crossedInto+")");
   if(one.escortPlayer()==null||!one.escortPlayer().toString().equals(line[2]))throw new IllegalStateException("The companion forgot whose it is: "+one.escortPlayer());
   LogUtils.getLogger().info("ASTRA_ESCORT VERIFIED after the game was closed and this world opened again: one companion {} in {}, still {}'s, state {}/{}; reload=true",
    id,found.get(0),one.escortPlayer(),one.escortState(),one.escortReason());
   mc.stop();
  }catch(Exception ex){failure=ex.toString();}});
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(Boolean.getBoolean("villageastra.reloadSmoke")){
   // The ground is brought in first, and only a good while later is the world asked where the companion is: chunks and their entities come
   // back on their own time, and an entity still on its way in is neither lost nor here.
   if(++ticks==100)wake(mc);else if(ticks==400)afterReload(mc);else if(ticks>1200)throw new IllegalStateException("The reloaded world never answered for the companion");return;}
  if(++ticks>6000)throw new IllegalStateException("Escort timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>60){phase=1;ticks=0;server.execute(()->{try{
    var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();var p=player(server);
    server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
    l.setDayTime(1000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
    for(var x:e.settlement().residents())if(l.getEntity(x.id()) instanceof ResidentEntity other)other.setNoAi(true);
    int x=e.center().getX()+60,z=e.center().getZ();
    origin=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-2,z-8);build(l);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    var stand=Vec3.atBottomCenterOf(origin.offset(27,5,11));p.teleportTo(l,stand.x,stand.y,stand.z,90,20);
    var c=VillageAstra.RESIDENT.get().create(l);var at=Vec3.atBottomCenterOf(origin.offset(3,5,8));c.moveTo(at.x,at.y,at.z,0,0);c.escort(p.getUUID());l.addFreshEntity(c);companion=c.getUUID();
    LogUtils.getLogger().info("ASTRA_ESCORT fixture: a walled lake of eleven blocks of water, a nether portal on the far bank, the companion on the near bank");ready=true;
   }catch(Exception ex){failure=ex.toString();}});}
  // The lake is too wide to swim: the companion waits for a boat and never steps into the water.
  else if(phase==1&&ready&&ticks%10==0){server.execute(()->{var c=find(server);if(c==null){failure="The companion is gone";return;}step(c);
    if(c.isInWater()&&!c.isPassenger())failure="The companion stepped into water too wide to swim";
    progress=c.escortState()+"/"+c.escortReason()+" at "+c.blockPosition().toShortString();
    if(c.escortState().equals("waiting")&&c.escortReason().equals("needs_boat"))waited=true;});
   if(waited){capture(mc,"needs-boat");phase=2;ticks=0;LogUtils.getLogger().info("ASTRA_ESCORT the companion waits for a boat without swimming: {}",progress);}
   else if(ticks>400)throw new IllegalStateException("The companion did not ask for a boat: "+progress);}
  // A boat is set down beside it: it boards, rows across and lands next to its player.
  else if(phase==2&&ticks==10){server.execute(()->{var l=server.overworld();var at=Vec3.atBottomCenterOf(origin.offset(6,4,8)).add(0.7,0.5,0);boat=new Boat(l,at.x,at.y,at.z);l.addFreshEntity(boat);boatStart=boat.getX();});}
  else if(phase==2&&ticks>10&&ticks%10==0){server.execute(()->{var c=find(server);var p=player(server);if(c==null||boat==null){failure="The companion or its boat is gone";return;}step(c);
    if(c.getVehicle()==boat&&!rowed){rowed=true;LogUtils.getLogger().info("ASTRA_ESCORT the companion boards the boat");}
    if(rowed&&!c.isPassenger()&&c.distanceTo(p)<3&&!c.isInWater()){landed=true;landedAt=c.distanceTo(p);rowedBy=boat.getX()-boatStart;}
    progress=c.escortState()+"/"+c.escortReason()+" riding="+c.isPassenger()+" gap="+String.format(Locale.ROOT,"%.1f",c.distanceTo(p))+" boat="+boat.blockPosition().toShortString();});
   if(landed){if(rowedBy<8)throw new IllegalStateException("The boat did not really cross: "+rowedBy);
    capture(mc,"landed");LogUtils.getLogger().info("ASTRA_ESCORT the companion rowed {} blocks and landed {} from its player",String.format(Locale.ROOT,"%.1f",rowedBy),String.format(Locale.ROOT,"%.1f",landedAt));phase=3;ticks=0;}
   else if(ticks>1200)throw new IllegalStateException("The companion did not row across and land: "+progress);}
  // The player steps into the portal and is carried to the Nether; the companion walks into the same portal after them.
  else if(phase==3&&ticks==10){server.execute(()->{var p=player(server);var at=Vec3.atBottomCenterOf(cell);p.teleportTo(server.overworld(),at.x,at.y,at.z,0,0);});}
  else if(phase==3&&ticks>10&&ticks%10==0){server.execute(()->{var c=find(server);var p=player(server);if(c==null){failure="The companion is gone";return;}
    if(c.level().dimension()==Level.OVERWORLD){step(c);if(c.portalCell()!=null&&c.escortState().equals("dimension"))noted=true;}
    if(c.level().dimension()==Level.NETHER&&p.level()==c.level()){crossed=true;
     if(c.distanceTo(p)<4&&c.escortState().equals("following")&&c.portalCell()==null){followAt=c.distanceTo(p);}}
    progress=c.level().dimension().location()+" "+c.escortState()+"/"+c.escortReason()+" player="+p.level().dimension().location()+" noted="+noted;});
   if(crossed&&followAt>0){capture(mc,"nether");
    if(!noted)throw new IllegalStateException("The companion crossed without the note of its player's portal");
    // QUEST-003/T67: who crossed and where, kept for the run that loads this world again after the game is closed.
    try{var him=find(server);var where=him==null?cell:him.blockPosition();java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("astra-escort-companion.txt"),
     companion+" "+net.minecraft.world.level.Level.NETHER.location()+" "+player(server).getUUID()+" "+where.getX()+" "+where.getY()+" "+where.getZ()+" "+cell.getX()+" "+cell.getZ());}catch(Exception ignored){}
    LogUtils.getLogger().info("ASTRA_ESCORT VERIFIED needs_boat without swimming -> boat rowed {} -> landed {} from the player; portal {} recorded; crossed and follows in the nether at {}; maxStep={} reload=false",
     String.format(Locale.ROOT,"%.1f",rowedBy),String.format(Locale.ROOT,"%.1f",landedAt),cell.toShortString(),String.format(Locale.ROOT,"%.1f",followAt),String.format(Locale.ROOT,"%.1f",maxStep));
    mc.stop();phase=4;}
   else if(ticks>2400)throw new IllegalStateException("The companion did not follow through the portal: "+progress);}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ESCORT FAILED",ex);Minecraft.getInstance().stop();}}
}
