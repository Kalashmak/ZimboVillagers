package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-060 in the real client: at dusk the two people of one house stand side by side in front of its single door and both go to bed at once —
 *  in the doorway one makes way for the other and nobody is left stuck; the guard stays up; at dawn both get up. */
final class NightProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready;
 private static volatile UUID first,second,guard;private static volatile BlockPos door;
 private static volatile boolean madeWay,firstAsleep,secondAsleep,guardAsleep,awake;private static volatile int stuckTicks;
 static boolean enabled(){return Boolean.getBoolean("villageastra.nightSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-night-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_NIGHT screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=entry(server);var s=e.settlement();var p=server.getPlayerList().getPlayers().get(0);
  // A house of the village with its door and beds standing, and the people who live in it.
  Settlement.Building house=null;BlockPos found=null;
  for(var b:s.buildings()){if(!b.type().equals("home"))continue;
   for(var cell:BuildingPlacement.layout(e,b,b.type()).entrySet())if(cell.getValue().getBlock() instanceof DoorBlock&&cell.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&l.getBlockState(cell.getKey()).getBlock() instanceof DoorBlock){house=b;found=cell.getKey();break;}
   if(house!=null)break;}
  if(house==null)throw new IllegalStateException("No house with a standing door");
  final var home=house;door=found;
  var people=new ArrayList<>(s.residents().stream().filter(r->r.alive()&&home.id().equals(r.home())).map(Resident::id).toList());
  var cap=s.homes().stream().filter(h->h.id().equals(home.id())).findFirst().orElseThrow().capacity();
  while(people.size()<2&&people.size()<cap){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,people.size()%2==0,null,null,-1);s.admit(r,home.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(door.getX()+.5,door.getY(),door.getZ()-3.5,0,0);l.addFreshEntity(npc);people.add(r.id());}
  if(people.size()<2)throw new IllegalStateException("The house holds fewer than two people: "+people.size());
  first=people.get(0);second=people.get(1);
  // Both stand three steps out in front of the door, side by side, so they reach it together.
  var facing=l.getBlockState(door).getValue(DoorBlock.FACING);var out=facing.getOpposite();var across=facing.getClockWise();
  var inside=BuildingPlacement.origin(e,house).offset(3,1,3);if(door.relative(out).distSqr(inside)<door.relative(facing).distSqr(inside)){out=facing;}
  int i=0;for(var id:List.of(first,second)){var spot=door.relative(out,3).relative(across,i==0?1:-1);i++;
   if(!(l.getEntity(id) instanceof ResidentEntity npc))throw new IllegalStateException("Resident not loaded: "+id);
   npc.teleportTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5);npc.getNavigation().stop();}
  // A guard of the village keeps watch at night.
  var post=new Settlement.Building(Settlement.childId(s.id(),"building/guard-night"),"guard_house",-20,0,20);s.addBuilding(post);
  var quarters=new Settlement.Home(Settlement.childId(s.id(),"home/guard-night"),1,1,true);s.addHome(quarters);
  var watch=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(watch,quarters.id());watch.trainMilitary();s.assign(watch.id(),Profession.GUARD,post.id());
  var guardNpc=VillageAstra.RESIDENT.get().create(l);guardNpc.bind(s.id(),s.resident(watch.id()));var gs=door.relative(out,5);guardNpc.moveTo(gs.getX()+.5,gs.getY(),gs.getZ()+.5,0,0);l.addFreshEntity(guardNpc);guard=watch.id();
  SettlementData.get(server).setDirty();
  var eye=door.relative(out,7).above(4);p.teleportTo(l,eye.getX()+.5,eye.getY(),eye.getZ()+.5,out.getOpposite().toYRot(),35);
  l.setDayTime(13000);
  LogUtils.getLogger().info("ASTRA_NIGHT fixture dusk, two people of the house at {} in front of its door at {}, a guard beside",house.type(),door.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(net.minecraft.server.MinecraftServer server){server.execute(()->{var l=server.overworld();
  if(l.getEntity(first) instanceof ResidentEntity a&&l.getEntity(second) instanceof ResidentEntity b&&l.getEntity(guard) instanceof ResidentEntity g){
   if(a.doorway()!=null&&a.doorway().waiting()||b.doorway()!=null&&b.doorway().waiting()||a.workStatus().equals("making_way")||b.workStatus().equals("making_way"))madeWay=true;
   firstAsleep=a.isSleeping();secondAsleep=b.isSleeping();guardAsleep=g.isSleeping();
   // Somebody pressed into the door frame: inside the door block and not moving.
   boolean pressed=(a.blockPosition().equals(door)&&a.getDeltaMovement().horizontalDistanceSqr()<1e-4)||(b.blockPosition().equals(door)&&b.getDeltaMovement().horizontalDistanceSqr()<1e-4);
   stuckTicks=pressed?stuckTicks+10:0;
   awake=!a.isSleeping()&&!b.isSleeping();
   progress="first="+a.workStatus()+"@"+a.blockPosition().toShortString()+" second="+b.workStatus()+"@"+b.blockPosition().toShortString()+" guard="+g.workStatus()+" madeWay="+madeWay+" asleep="+firstAsleep+"/"+secondAsleep;}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>6000)throw new IllegalStateException("Night timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%10==0){sample(server);
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_NIGHT progress {}",progress);
   if(stuckTicks>=200)throw new IllegalStateException("A resident is stuck in the doorway: "+progress);
   if(firstAsleep&&secondAsleep){capture(mc,"asleep");phase=2;ticks=0;}
   else if(ticks>2400)throw new IllegalStateException("The two did not both get to bed: "+progress);}
  else if(phase==2&&ticks>20){
   if(guardAsleep)throw new IllegalStateException("The guard went to bed: "+progress);
   server.execute(()->server.overworld().setDayTime(23500));phase=3;ticks=0;}
  else if(phase==3&&ticks%10==0){sample(server);
   if(awake){LogUtils.getLogger().info("ASTRA_NIGHT VERIFIED both people of one house went to bed through one door at once: madeWay={} stuck=false guard awake={} both got up at dawn={}; reload=false",madeWay,!guardAsleep,awake);
    mc.stop();phase=4;}
   else if(ticks>600)throw new IllegalStateException("Nobody got up at dawn: "+progress);}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_NIGHT FAILED",ex);mc.stop();}}
}
