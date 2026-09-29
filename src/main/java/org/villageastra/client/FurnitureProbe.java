package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-143 in the real client (-PfurnitureProbe, verifier mechanics --mode furniture): on a lawn beside the harness village — a showroom of
 *  chairs of all eleven woods and joined tables of several (a row of three, a square of four with a lantern, single tables with a candle),
 *  the player seated on a chair (third person, from the front); a restaurant raised I → IV → VI whose hungry residents sit on its chairs
 *  (counted by their seats, not by pixels: at least 3, 5 and 6 at once); a big house at level IV whose resident rests on a chair of the upper
 *  storey in the evening, and the cottage's porch table. Every block and item model is real. Frames: showroom, showroom-angle, seated,
 *  restaurant-1, restaurant-4, restaurant-6, house, porch, hotbar. */
final class FurnitureProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,step;private static volatile BlockPos lawn;
 private static volatile Settlement.Building shop,house,cottage;private static final List<UUID> PEOPLE=new ArrayList<>();private static final List<String> SHOTS=new ArrayList<>();
 private static volatile int seatedNow,seatedMax1,seatedMax4,seatedMax6,placed,homeSeated;private static volatile boolean playerSeated,rested;private static final List<Integer> LEVELS=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.furnitureProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-furniture-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}SHOTS.add(suffix);LogUtils.getLogger().info("ASTRA_FURNITURE screenshot {}",path);}
 private static SettlementData.Entry entry(MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building kept(SettlementData.Entry e,Settlement.Building b){return e.settlement().buildings().stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow();}
 private static BlockState chair(String wood,Direction facing){return VillageAstra.CHAIRS.get(wood).get().defaultBlockState().setValue(ChairBlock.FACING,facing);}
 private static void table(ServerLevel l,BlockPos p,String wood){l.setBlock(p,VillageAstra.TABLES.get(wood).get().joined(l,p),3);}
 private static void fixture(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{
  var l=server.overworld();var e=entry(server);var s=e.settlement();var p=server.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);l.setWeatherParameters(6000,0,false,false);l.setDayTime(1000);
  lawn=e.center().offset(-70,0,-110);
  for(int x=-10;x<60;x++)for(int z=-16;z<40;z++){var g=lawn.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),2);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=1;y<24;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),2);}
  // The showroom on a spruce floor: a row of chairs of every wood facing north (to the camera), and behind it the tables.
  for(int x=-1;x<=23;x++)for(int z=-1;z<=9;z++)l.setBlock(lawn.offset(x,0,z),Blocks.SPRUCE_PLANKS.defaultBlockState(),2);
  for(int i=0;i<Furniture.WOODS.size();i++)l.setBlock(lawn.offset(1+2*i,1,0),chair(Furniture.WOODS.get(i),Direction.NORTH),3);
  // A row of three joined tables of three woods, chairs round it.
  for(int i=0;i<3;i++)table(l,lawn.offset(2+i,1,4),List.of("oak","spruce","dark_oak").get(i));
  for(int i=0;i<3;i++)l.setBlock(lawn.offset(2+i,1,5),chair(List.of("oak","spruce","dark_oak").get(i),Direction.NORTH),3);
  l.setBlock(lawn.offset(1,1,4),chair("oak",Direction.EAST),3);l.setBlock(lawn.offset(5,1,4),chair("dark_oak",Direction.WEST),3);
  // A square of four (birch, cherry, jungle, acacia) with a lantern, a chair on every side.
  table(l,lawn.offset(9,1,4),"birch");table(l,lawn.offset(10,1,4),"cherry");table(l,lawn.offset(9,1,5),"jungle");table(l,lawn.offset(10,1,5),"acacia");
  l.setBlock(lawn.offset(9,2,4),Blocks.LANTERN.defaultBlockState(),3);
  l.setBlock(lawn.offset(8,1,5),chair("birch",Direction.EAST),3);l.setBlock(lawn.offset(11,1,4),chair("cherry",Direction.WEST),3);l.setBlock(lawn.offset(10,1,6),chair("acacia",Direction.NORTH),3);l.setBlock(lawn.offset(9,1,3),chair("jungle",Direction.SOUTH),3);
  // Single tables: mangrove with a candle, bamboo with a lantern, crimson and warped side by side (they join); a chair for the player.
  table(l,lawn.offset(14,1,4),"mangrove");l.setBlock(lawn.offset(14,2,4),Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT,true),3);l.setBlock(lawn.offset(14,1,5),chair("mangrove",Direction.NORTH),3);
  table(l,lawn.offset(17,1,4),"bamboo");l.setBlock(lawn.offset(17,2,4),Blocks.LANTERN.defaultBlockState(),3);l.setBlock(lawn.offset(17,1,5),chair("bamboo",Direction.NORTH),3);
  table(l,lawn.offset(20,1,4),"crimson");table(l,lawn.offset(21,1,4),"warped");l.setBlock(lawn.offset(20,1,5),chair("crimson",Direction.NORTH),3);l.setBlock(lawn.offset(21,1,5),chair("warped",Direction.NORTH),3);
  // The player's chair: facing north on the grass west of the showroom.
  l.setBlock(PLAYER_CHAIR(),chair("spruce",Direction.NORTH),3);
  // A resident seated on the dark oak chair of the row (the probe renews its lease), seen in the showroom frame.
  var npc=VillageAstra.RESIDENT.get().create(l);var seat=lawn.offset(11,1,0);npc.moveTo(seat.getX()+.5,seat.getY(),seat.getZ()-1.5,180,0);npc.setNoAi(true);l.addFreshEntity(npc);
  if(SeatEntity.sit(l,seat,npc)==null)throw new IllegalStateException("The showroom resident could not sit");shown=npc.getUUID();
  int n=0;for(int x=-6;x<=23;x++)for(int z=-5;z<=9;z++){var b=l.getBlockState(lawn.offset(x,1,z)).getBlock();if(b instanceof ChairBlock||b instanceof TableBlock)n++;}placed=n;
  // The restaurant south of the showroom, the big house and the cottage east of it.
  var o=lawn.offset(0,0,16).subtract(e.center());shop=new Settlement.Building(Settlement.childId(s.id(),"building/furniture-probe-restaurant"),"restaurant",o.getX(),o.getY(),o.getZ(),0,1);s.addBuilding(shop);
  for(var cell:BuildingPlacement.layout(e,shop,"restaurant").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);LEVELS.add(BuildingTiers.level(l,e,shop));
  var h=lawn.offset(28,0,16).subtract(e.center());house=new Settlement.Building(Settlement.childId(s.id(),"building/furniture-probe-house"),"home_2",h.getX(),h.getY(),h.getZ(),0,4);s.addBuilding(house);
  s.addHome(new Settlement.Home(house.id(),4,HousingLadder.capacity("home_2",4),true));
  for(var cell:BuildingPlacement.layout(e,house,BuildingTiers.layoutId("home_2",4)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  var c=lawn.offset(44,0,16).subtract(e.center());cottage=new Settlement.Building(Settlement.childId(s.id(),"building/furniture-probe-cottage"),"home",c.getX(),c.getY(),c.getZ(),0,1);s.addBuilding(cottage);
  for(var cell:BuildingPlacement.layout(e,cottage,"home").entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"house/furniture-probe-diners"),1,40,true));
  SettlementData.get(server).setDirty();p.setGameMode(GameType.SPECTATOR);ready=true;
  LogUtils.getLogger().info("ASTRA_FURNITURE fixture at {}: placed={}",lawn.toShortString(),placed);
 }catch(Exception ex){failure=ex.toString();}});}
 /** Every chair's and table's block model (every facing, a lone and a joined table) and item model are real. */
 private static String models(Minecraft mc){var missing=mc.getModelManager().getMissingModel();var bad=new ArrayList<String>();
  for(var wood:Furniture.WOODS){for(var d:Direction.Plane.HORIZONTAL)if(mc.getBlockRenderer().getBlockModel(chair(wood,d))==missing)bad.add(wood+"/chair/"+d);
   var t=VillageAstra.TABLES.get(wood).get().defaultBlockState();if(mc.getBlockRenderer().getBlockModel(t)==missing||mc.getBlockRenderer().getBlockModel(t.setValue(TableBlock.EAST,true))==missing)bad.add(wood+"/table");
   for(var item:List.of(VillageAstra.CHAIR_ITEMS.get(wood).get(),VillageAstra.TABLE_ITEMS.get(wood).get()))if(mc.getItemRenderer().getModel(new ItemStack(item),null,null,0)==missing)bad.add(wood+"/item/"+item);}
  return bad.isEmpty()?"":bad.toString();}
 private static void look(Minecraft mc,double x,double y,double z,float yaw,float pitch){var server=mc.getSingleplayerServer();if(mc.screen!=null)mc.setScreen(null);
  server.execute(()->server.getPlayerList().getPlayers().get(0).teleportTo(server.overworld(),lawn.getX()+x,lawn.getY()+y,lawn.getZ()+z,yaw,pitch));}
 private static volatile UUID shown;private static volatile boolean chairCleared;
 private static BlockPos PLAYER_CHAIR(){return lawn.offset(-4,1,2);}
 private static void seatPlayer(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{try{var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);
  var at=PLAYER_CHAIR();p.teleportTo(server.overworld(),at.getX()+.5,at.getY(),at.getZ()-1.5,0,20);
  if(SeatEntity.sit(server.overworld(),at,p)==null)throw new IllegalStateException("The player could not sit");playerSeated=SeatEntity.seated(p);step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static void standPlayer(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.stopRiding();
  p.setGameMode(GameType.SPECTATOR);step=true;});}
 private static void checkStood(Minecraft mc){var server=mc.getSingleplayerServer();server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);
  chairCleared=!SeatEntity.seated(p)&&server.overworld().getEntitiesOfClass(SeatEntity.class,new net.minecraft.world.phys.AABB(PLAYER_CHAIR()).inflate(1)).isEmpty();playerSeated&=chairCleared;step=true;});}
 /** The restaurant raised to a level (its design laid) and hungry diners due now, spawned at its door, with food in its chest. */
 private static void raise(MinecraftServer server,int level,int diners){server.execute(()->{try{var l=server.overworld();var e=entry(server);var s=e.settlement();
  for(int n=kept(e,shop).level()+1;n<=level;n++)s.raiseBuildingLevel(shop.id(),n);var b=kept(e,shop);
  for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId("restaurant",level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  BuildingLevels.forgetBest(s.id());Dining.forgetSeats();int read=BuildingTiers.level(l,e,b);if(read!=level)throw new IllegalStateException("The restaurant laid at "+level+" reads as "+read);LEVELS.add(level);
  var clock=SettlementData.get(server).clock();while(clock.ticks()<Population.MEAL_INTERVAL+200)clock.advance(true,false);long now=clock.ticks();
  var chest=LogisticsRoutes.chest(l,e,b);chest.setItem(0,new ItemStack(Items.BREAD,64));chest.setItem(1,new ItemStack(Items.COOKED_BEEF,64));chest.setItem(2,new ItemStack(Items.BAKED_POTATO,64));
  var home=s.homes().stream().filter(x->x.id().equals(Settlement.childId(s.id(),"house/furniture-probe-diners"))).findFirst().orElseThrow();
  PEOPLE.clear();for(int i=0;i<diners;i++){var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());r.ate(now-Population.MEAL_INTERVAL);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));var at=BuildingPlacement.at(e,b,2+(i%7),1,-2-(i/7));npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);PEOPLE.add(r.id());}
  SettlementData.get(server).setDirty();step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static void sampleHall(MinecraftServer server){server.execute(()->{var l=server.overworld();int seated=0;var states=new ArrayList<String>();
  for(var id:PEOPLE)if(l.getEntity(id) instanceof ResidentEntity npc){if(npc.getVehicle() instanceof SeatEntity seat&&l.getBlockState(seat.chair()).getBlock() instanceof ChairBlock&&"dining".equals(npc.workStatus()))seated++;if(states.size()<8)states.add(npc.workStatus());}
  seatedNow=seated;progress="seated="+seated+" diners="+states+" seats="+Dining.seats(l,entry(server),kept(entry(server),shop));});}
 /** Four residents of the big house, fed, in the evening — two adults (the village may send them to work at once: work comes first) and two
  *  children: one of them rests on a chair of the upper storey. */
 private static void homeFixture(MinecraftServer server){server.execute(()->{try{var l=server.overworld();var e=entry(server);var s=e.settlement();
  l.setDayTime(11000);long now=SettlementData.get(server).clock().ticks();PEOPLE.clear();
  for(int i=0;i<4;i++){var r=new Resident(UUID.randomUUID(),i<2?Resident.Life.ADULT:Resident.Life.CHILD,false,null,null,-1);s.admit(r,house.id());r.ate(now);
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));var at=BuildingPlacement.at(e,kept(e,house),3+2*(i%3),5,i<3?5:3);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);PEOPLE.add(r.id());}
  SettlementData.get(server).setDirty();step=true;}catch(Exception ex){failure=ex.toString();}});}
 private static void sampleHome(MinecraftServer server){server.execute(()->{var l=server.overworld();int seated=0;var states=new ArrayList<String>();
  for(var id:PEOPLE)if(l.getEntity(id) instanceof ResidentEntity npc){if(npc.getVehicle() instanceof SeatEntity&&"resting".equals(npc.workStatus()))seated++;states.add(npc.workStatus()+"@"+npc.blockPosition().toShortString());}
  homeSeated=seated;rested|=seated>0;progress="home seated="+seated+" "+states;});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>9000)throw new IllegalStateException("Furniture probe timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(shown!=null&&phase<=6){var id=shown;server.execute(()->{if(server.overworld().getEntity(id) instanceof ResidentEntity npc)SeatEntity.keep(npc);});}
  if(phase==0&&ticks>80){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){var bad=models(mc);if(!bad.isEmpty())throw new IllegalStateException("Missing models: "+bad);
   mc.options.hideGui=true;look(mc,11.5,2.4,-4.5,0,24);phase=2;ticks=0;}
  else if(phase==2&&ticks>100){capture(mc,"showroom");look(mc,17.5,3.8,-1.5,45,30);phase=3;ticks=0;}
  else if(phase==3&&ticks>60){capture(mc,"showroom-angle");step=false;seatPlayer(mc);phase=4;ticks=0;}
  else if(phase==4&&step&&ticks>10){mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);phase=5;ticks=0;}
  else if(phase==5&&ticks>60){var me=mc.player;var v=me==null?null:me.getVehicle();
   LogUtils.getLogger().info("ASTRA_FURNITURE seated client: vehicle={} vehicleYaw={} yaw={} body={} head={}",v==null?null:v.getType(),v==null?0:v.getYRot(),me==null?0:me.getYRot(),me==null?0:me.yBodyRot,me==null?0:me.getYHeadRot());
   capture(mc,"seated");mc.options.setCameraType(CameraType.FIRST_PERSON);step=false;standPlayer(mc);phase=55;ticks=0;}
  else if(phase==55&&step&&ticks>20){step=false;checkStood(mc);phase=6;ticks=0;}
  else if(phase==6&&step&&ticks>2){if(!playerSeated)throw new IllegalStateException("The player did not sit and stand cleanly");step=false;raise(server,1,6);phase=7;ticks=0;}
  // Inside the hall from the back row under the tie beams, looking along the tables to the door.
  else if(phase==7&&step&&ticks%20==0){sampleHall(server);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_FURNITURE hall I {}",progress);
   seatedMax1=Math.max(seatedMax1,seatedNow);if(seatedNow>=3){look(mc,5.5,2.0,23.4,180,28);phase=8;ticks=0;}else if(ticks>4000)throw new IllegalStateException("Fewer than three sat down at I: "+progress);}
  else if(phase==8&&ticks>40){sampleHall(server);capture(mc,"restaurant-1");step=false;raise(server,4,10);phase=9;ticks=0;}
  else if(phase==9&&step&&ticks%20==0){sampleHall(server);seatedMax4=Math.max(seatedMax4,seatedNow);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_FURNITURE hall IV {}",progress);
   if(seatedNow>=5){look(mc,5.5,2.0,23.4,180,28);phase=10;ticks=0;}else if(ticks>4000)throw new IllegalStateException("Fewer than five sat down at IV: "+progress);}
  else if(phase==10&&ticks>40){sampleHall(server);capture(mc,"restaurant-4");step=false;raise(server,6,14);phase=11;ticks=0;}
  else if(phase==11&&step&&ticks%20==0){sampleHall(server);seatedMax6=Math.max(seatedMax6,seatedNow);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_FURNITURE hall VI {}",progress);
   if(seatedNow>=6){look(mc,5.5,2.0,23.4,180,28);phase=12;ticks=0;}else if(ticks>4000)throw new IllegalStateException("Fewer than six sat down at VI: "+progress);}
  else if(phase==12&&ticks>40){sampleHall(server);capture(mc,"restaurant-6");step=false;homeFixture(server);phase=13;ticks=0;}
  // The big house's upper storey from its east end, looking west at the table; waits for a resident to rest on a chair.
  else if(phase==13&&step&&ticks%20==0){sampleHome(server);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_FURNITURE {}",progress);
   if(homeSeated>0){look(mc,28+5.5,5.3,16+6.6,180,24);phase=14;ticks=0;}else if(ticks>3000)throw new IllegalStateException("Nobody rested at home: "+progress);}
  else if(phase==14&&ticks>40){sampleHome(server);capture(mc,"house");look(mc,44-3.0,2.9,16-3.8,-38,22);phase=15;ticks=0;}
  else if(phase==15&&ticks>60){capture(mc,"porch");
   server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);
    for(int i=0;i<9;i++)p.getInventory().setItem(i,new ItemStack(i%2==0?VillageAstra.CHAIR_ITEMS.get(Furniture.WOODS.get(i)).get():VillageAstra.TABLE_ITEMS.get(Furniture.WOODS.get(i)).get()));p.getInventory().selected=4;});
   mc.options.hideGui=false;look(mc,11.5,2.2,-7.5,0,22);phase=16;ticks=0;}
  else if(phase==16&&ticks>60){capture(mc,"hotbar");
   LogUtils.getLogger().info("ASTRA_FURNITURE VERIFIED placed={} models=22 playerSeated={} seated1={} seated4={} seated6={} rested={} levels={} frames={}; reload=false",
    placed,playerSeated,seatedMax1,seatedMax4,seatedMax6,rested,LEVELS,String.join(",",SHOTS));
   mc.stop();phase=17;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_FURNITURE FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
