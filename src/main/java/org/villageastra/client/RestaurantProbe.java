package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-139 in the real client (-PrestaurantSmoke, verifier mechanics --mode restaurant): a restaurant on a lawn beside the harness village.
 *  Level I stands (frame), is raised to II; six hungry adults with their whole village life walk in by day, sit at the tables and eat from the
 *  restaurant's chest (frame of the hall with at least three seated, told by their "dining" status, not by pixels). Raised to IV with a courier:
 *  a miner working 40 blocks away is fed at his work (frame of the courier on the way, the bread in hand). Raised to VI (frame). The baking.4
 *  card is read for its lines (no «труд» left). Every level stands as its design reads (BuildingTiers.level) and the hall's seats are counted. */
final class RestaurantProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,step;
 private static volatile Settlement.Building shop;private static volatile BlockPos lawn;private static final List<UUID> DINERS=new ArrayList<>();
 private static volatile UUID courier,miner;private static volatile int seatedMax,seatedNow,served,seats,servedBefore;private static volatile boolean minerFed,courierAway;private static volatile long minerDue;
 private static final List<Integer> LEVELS=new ArrayList<>();private static volatile String cardFacts="",researchFacts="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.restaurantSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-restaurant-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RESTAURANT screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building kept(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(shop.id())).findFirst().orElseThrow();}
 /** The restaurant raised to this level: its design laid (tables, equipment and core of the level) and the level kept. */
 private static void raise(net.minecraft.server.MinecraftServer server,int level){server.execute(()->{try{var l=server.overworld();var e=entry(server);
  for(int n=kept(e).level()+1;n<=level;n++)e.settlement().raiseBuildingLevel(shop.id(),n);var b=kept(e);
  for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("restaurant",level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  BuildingLevels.forgetBest(e.settlement().id());Dining.forgetSeats();int read=BuildingTiers.level(l,e,b);
  if(read!=level)throw new IllegalStateException("The restaurant laid at "+level+" reads as "+read);LEVELS.add(level);
  SettlementData.get(server).setDirty();step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static Resident person(SettlementData.Entry e,net.minecraft.server.level.ServerLevel l,BlockPos at,Profession role,Settlement.Building work,long lastMeal){
  var s=e.settlement();var home=s.homes().stream().filter(h->h.id().equals(Settlement.childId(s.id(),"house/restaurant-probe"))).findFirst().orElseThrow();var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);
  s.admit(r,home.id());if(role!=null)s.assign(r.id(),role,work.id());r.ate(lastMeal);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);return r;}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=entry(server);var s=e.settlement();var p=server.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);l.setWeatherParameters(6000,0,false,false);l.setDayTime(1000);
  lawn=e.center().offset(-70,0,40);
  for(int x=-10;x<64;x++)for(int z=-16;z<24;z++){var ground=lawn.offset(x,0,z);l.setBlock(ground.below(),Blocks.DIRT.defaultBlockState(),2);l.setBlock(ground,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<24;y++)l.setBlock(ground.above(y),Blocks.AIR.defaultBlockState(),2);}
  var o=lawn.subtract(e.center());shop=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant-probe"),"restaurant",o.getX(),o.getY(),o.getZ(),0,1);s.addBuilding(shop);
  // A house for the diners: the village's own may be full.
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"house/restaurant-probe"),1,12,true));
  for(var cell:BuildingPlacement.layout(e,shop,"restaurant").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  LEVELS.add(BuildingTiers.level(l,e,shop));
  SettlementData.get(server).setDirty();
  p.setGameMode(GameType.SPECTATOR);
  LogUtils.getLogger().info("ASTRA_RESTAURANT fixture restaurant I on a lawn at {}",lawn.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** The diners: six adults due for their meal now (the village clock is wound on past one meal interval first). */
 private static void diners(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{var l=server.overworld();var e=entry(server);
  var clock=SettlementData.get(server).clock();while(clock.ticks()<Population.MEAL_INTERVAL+200)clock.advance(true,false);long now=clock.ticks();
  var chest=LogisticsRoutes.chest(l,e,kept(e));chest.setItem(0,new ItemStack(Items.BREAD,32));chest.setItem(1,new ItemStack(Items.COOKED_BEEF,16));
  DINERS.clear();for(int i=0;i<6;i++)DINERS.add(person(e,l,lawn.offset(2+i,1,-6-(i%2)),null,null,now-Population.MEAL_INTERVAL).id());
  SettlementData.get(server).setDirty();step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static void sampleHall(net.minecraft.server.MinecraftServer server){server.execute(()->{var l=server.overworld();var e=entry(server);int seated=0;var states=new ArrayList<String>();
  for(var id:DINERS)if(l.getEntity(id) instanceof ResidentEntity npc){if("dining".equals(npc.workStatus()))seated++;if(states.size()<6)states.add(npc.workStatus());}
  seatedNow=seated;seatedMax=Math.max(seatedMax,seated);var b=kept(e);served=Dining.served(l,e,b);seats=Dining.seats(l,e,b);
  var card=Dining.card(l,e,b);cardFacts="seats="+card.getInt("seats")+" taken="+card.getInt("taken")+" served="+card.getInt("served")+" state="+card.getString("state");
  progress="seated="+seated+" max="+seatedMax+" "+cardFacts+" diners="+states;});}
 /** Level IV and its courier; a miner at a mine 40 blocks east, his meal due in a moment. */
 private static void courierFixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{var l=server.overworld();var e=entry(server);var s=e.settlement();
  var o=lawn.offset(44,0,6).subtract(e.center());var mine=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant-probe-mine"),"mine",o.getX(),o.getY(),o.getZ(),0,1);s.addBuilding(mine);
  long now=SettlementData.get(server).clock().ticks();
  var m=person(e,l,lawn.offset(46,1,8),Profession.MINER,mine,now-Population.MEAL_INTERVAL+900);miner=m.id();servedBefore=Dining.served(l,e,kept(e));minerDue=m.lastMeal()+Population.MEAL_INTERVAL;
  courier=person(e,l,lawn.offset(5,1,-6),Profession.PORTER,kept(e),now).id();
  SettlementData.get(server).setDirty();step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static void sampleCourier(net.minecraft.server.MinecraftServer server){server.execute(()->{var l=server.overworld();var e=entry(server);
  var r=e.settlement().resident(miner);minerFed=r!=null&&r.lastMeal()>=minerDue&&Dining.served(l,e,kept(e))>servedBefore;
  if(l.getEntity(courier) instanceof ResidentEntity c){var at=LogisticsRoutes.position(e,kept(e));
   courierAway=c.workStatus().equals("courier_delivering")&&!c.displayedWorkItem().isEmpty()&&c.distanceToSqr(at.getX(),at.getY(),at.getZ())>16*16;
   progress="courier="+c.workStatus()+"@"+c.blockPosition().toShortString()+" carrying="+c.displayedWorkItem()+" minerFed="+minerFed+" "+Dining.card(l,e,kept(e)).getCompound("couriers");}});}
 private static void look(Minecraft mc,double x,double y,double z,float yaw,float pitch){var server=mc.getSingleplayerServer();
  server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),x,y,z,yaw,pitch);});}
 /** The street front of the restaurant from the north-west, above the lawn. */
 private static void front(Minecraft mc){look(mc,lawn.getX()-3.5,lawn.getY()+6,lawn.getZ()-9.5,-28,20);}
 private static void research(){var lines=ResearchEffects.describe("baking.4");var text=new StringBuilder();for(var c:lines)text.append(c.getString()).append(" | ");
  researchFacts="lines="+lines.size()+" labourWords="+(text.toString().contains("труд 80")||text.toString().contains("Labour 80"));
  LogUtils.getLogger().info("ASTRA_RESTAURANT research baking.4 {}",text);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>9000)throw new IllegalStateException("Restaurant probe timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){front(mc);mc.options.hideGui=true;phase=2;ticks=0;}
  else if(phase==2&&ticks>80){capture(mc,"level1");step=false;raise(server,2);phase=3;ticks=0;}
  else if(phase==3&&step&&ticks>20){step=false;diners(mc);phase=4;ticks=0;}
  // Inside the hall from the back row under the tie beams, looking along the tables to the door.
  else if(phase==4&&step&&ticks%20==0){sampleHall(server);
   if(ticks%400==0)LogUtils.getLogger().info("ASTRA_RESTAURANT hall {}",progress);
   if(seatedNow>=3){look(mc,lawn.getX()+5.5,lawn.getY()+1.5,lawn.getZ()+7.6,180,18);phase=5;ticks=0;}
   else if(ticks>5000)throw new IllegalStateException("Fewer than three diners sat down: "+progress);}
  else if(phase==5&&ticks>50){sampleHall(server);capture(mc,"hall");LogUtils.getLogger().info("ASTRA_RESTAURANT hall frame {}",progress);phase=6;ticks=0;}
  else if(phase==6&&ticks%20==0){sampleHall(server);if(served>=3){step=false;raise(server,4);phase=7;ticks=0;}else if(ticks>3000)throw new IllegalStateException("The hall served fewer than three: "+progress);}
  else if(phase==7&&step&&ticks>20){step=false;courierFixture(mc);phase=8;ticks=0;}
  else if(phase==8&&step&&ticks%10==0){sampleCourier(server);
   if(ticks%400==0)LogUtils.getLogger().info("ASTRA_RESTAURANT courier {}",progress);
   if(courierAway){var c=(net.minecraft.world.entity.Entity)server.overworld().getEntity(courier);if(c!=null)look(mc,c.getX()-5,c.getY()+6,c.getZ()-7,-35,35);phase=9;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("The courier never set out: "+progress);}
  else if(phase==9&&ticks>12){capture(mc,"courier");phase=10;ticks=0;}
  else if(phase==10&&ticks%20==0){sampleCourier(server);if(minerFed){front(mc);phase=11;ticks=0;}else if(ticks>4000)throw new IllegalStateException("The miner was never fed: "+progress);}
  else if(phase==11&&ticks>60){capture(mc,"level4");step=false;raise(server,6);phase=12;ticks=0;}
  else if(phase==12&&step&&ticks>60){capture(mc,"level6");sampleHall(server);research();phase=13;ticks=0;}
  else if(phase==13&&ticks>10){mc.options.hideGui=false;
   LogUtils.getLogger().info("ASTRA_RESTAURANT VERIFIED seated={} served={} courierFed={} levels={} {} {} dog={}; reload=false",seatedMax,served,minerFed,LEVELS,cardFacts,researchFacts,VillageDogs.provided()?"provided":"planned");
   mc.stop();phase=14;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_RESTAURANT FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
