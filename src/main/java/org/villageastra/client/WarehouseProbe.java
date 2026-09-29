package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-147 in the real client (-PwarehouseProbe -PprobeWarp=0, verifier mechanics --mode warehouse): north of the village the warehouse of
 *  level I and III laid to look at, a warehouse of VI (the castle) that sorts its store, and a working warehouse of II with Logistics II,
 *  its courier and its cart, beside a mine whose chest is over half full. The courier takes the cart to the mine, loads its parcel and five
 *  stacks, and brings them into the store; the VI store sorts a mixed intake by category. Frames: level1, level3, level6 (outside, by day),
 *  cart (the courier pulling the loaded cart), sorted (a category page of the VI store open), card (the working warehouse's card).
 *  Facts: the trip's items all reached the store (exactly the planned count, the sum of mine, store and custody unchanged), the sorting
 *  moved stacks, the wolves planned (no livestock source yet). */
final class WarehouseProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,loaded,delivered;
 private static volatile UUID workId,castleId,courierId,tripId;private static volatile int planned,sumBefore,stockBefore,moved,sortedMoves,unsortedBefore;
 private static volatile String facts="",wolves="?";private static volatile boolean pulling;private static volatile CompoundTag tripTag;
 static boolean enabled(){return Boolean.getBoolean("villageastra.warehouseProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-warehouse-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WAREHOUSE screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void pad(ServerLevel l,BlockPos origin,int w,int d){for(int x=-4;x<w+4;x++)for(int z=-5;z<d+4;z++){var g=origin.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<24;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}}
 private static void lay(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){for(var cell:BuildingPlacement.layout(e,b,BuildingTiers.layoutId(b.type(),level)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 /** The mine's goods in a chest (the village's other deliveries into the store are not counted). */
 private static int total(net.minecraft.world.Container c){int n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.is(Items.COBBLESTONE)||s.is(Items.RAW_IRON)||s.is(Items.COAL))n+=s.getCount();}return n;}
 private static void research(ServerLevel l,SettlementData.Entry e,int upTo){var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  var have=new HashSet<String>();for(var t:done)have.add(t.getAsString());
  for(int level=1;level<=upTo;level++)for(var id:BuildingTiers.research(WarehouseStore.TYPE,level))if(have.add(id))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);ResearchKnobs.forget(e.settlement().id());}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
  // AD-136: no building above its hall; the hall of this village stands at VI (the warehouse of VI needs it).
  for(int n=st.civilization().level()+1;n<=6;n++)st.civilization().completedHallUpgrade(n);
  // Levels I and III to look at (not the village's: they are laid only); the working II and the castle VI are the village's.
  var one=new Settlement.Building(Settlement.childId(st.id(),"building/probe-store-1"),WarehouseStore.TYPE,-64,0,-84,0);
  var three=new Settlement.Building(Settlement.childId(st.id(),"building/probe-store-3"),WarehouseStore.TYPE,-36,0,-84,0);
  var work=new Settlement.Building(Settlement.childId(st.id(),"building/probe-store-2"),WarehouseStore.TYPE,24,0,-84,0);st.addBuilding(work);workId=work.id();
  var castle=new Settlement.Building(Settlement.childId(st.id(),"building/probe-store-6"),WarehouseStore.TYPE,-8,0,-84,0);st.addBuilding(castle);castleId=castle.id();
  var mine=new Settlement.Building(Settlement.childId(st.id(),"building/probe-mine"),"mine",52,0,-84,0);st.addBuilding(mine);
  for(var b:List.of(one,three,work,castle))pad(l,BuildingPlacement.origin(e,b),23,17);pad(l,BuildingPlacement.origin(e,mine),8,9);
  lay(l,e,one,1);lay(l,e,three,3);
  st.raiseBuildingLevel(work.id(),2);lay(l,e,building(e,work.id()),2);
  for(int n=2;n<=6;n++)st.raiseBuildingLevel(castle.id(),n);lay(l,e,building(e,castle.id()),6);
  research(l,e,6);
  BuildingLevels.forgetBest(st.id());
  int wl=BuildingLevels.level(l,e,building(e,work.id())),cl=BuildingLevels.level(l,e,building(e,castle.id()));
  if(wl!=2||cl!=6)throw new IllegalStateException("The warehouses work at "+wl+" and "+cl+", laid as II and VI");
  WarehouseStorage.ensure(l,e,building(e,work.id()));WarehouseStorage.ensure(l,e,building(e,castle.id()));
  // The mine's chest over half full: its output goes first.
  var pit=LogisticsRoutes.position(e,mine);l.setBlock(pit,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);var pc=LogisticsRoutes.chest(l,e,mine);
  for(int i=0;i<16;i++)pc.setItem(i,new ItemStack(i%4==0?Items.RAW_IRON:i%4==1?Items.COAL:Items.COBBLESTONE,64));
  // The courier: a new adult of the village posted at the working warehouse; its cart set down from the warehouse's own order.
  var room=new Settlement.Home(Settlement.childId(st.id(),"home/probe-courier"),1,2,true);st.addHome(room);
  var courier=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);st.admit(courier,room.id());st.assign(courier.id(),Profession.PORTER,work.id());courierId=courier.id();
  var stock=LogisticsRoutes.chest(l,e,building(e,work.id()));stock.setItem(0,new ItemStack(VillageAstra.CART_ITEM.get()));
  WarehouseCarts.ensure(l,e,building(e,work.id()));if(WarehouseCarts.carts(l,building(e,work.id())).isEmpty())throw new IllegalStateException("The warehouse set no cart down: "+WarehouseCarts.records(l,work.id()));
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),st.resident(courier.id()));var door=BuildingPlacement.at(e,work,6,1,-1);npc.moveTo(door.getX()+.5,door.getY(),door.getZ()+.5,0,0);l.addFreshEntity(npc);
  sumBefore=total(pc)+total(stock);stockBefore=total(stock);
  // The VI store: a mixed intake to sort.
  var cs=LogisticsRoutes.chest(l,e,building(e,castle.id()));
  // No bread or wheat: the village asks for those (the pantry, the hall), and the courier of II would walk here for them.
  var mix=List.of(new ItemStack(Items.COBBLESTONE,64),new ItemStack(Items.OAK_LOG,40),new ItemStack(Items.PUMPKIN,20),new ItemStack(Items.IRON_INGOT,12),new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.WHITE_WOOL,16),
   new ItemStack(Items.PAPER,24),new ItemStack(Items.MELON_SLICE,48),new ItemStack(Items.COAL,30),new ItemStack(Items.OAK_PLANKS,64),new ItemStack(Items.STONE_BRICKS,64),new ItemStack(Items.CARROT,33),new ItemStack(Items.LEATHER,9),new ItemStack(Items.GRAVEL,40),new ItemStack(Items.CARROT,7));
  for(int i=0;i<mix.size();i++)cs.setItem(i,mix.get(i).copy());unsortedBefore=WarehouseSort.unsorted(cs);
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_WAREHOUSE fixture stores I, III (laid), II (courier {}, cart) and VI (castle, {} stacks to sort) north of the village",courier.id(),unsortedBefore);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** The courier's trip: the moment its custody holds the parcel and five stacks from the mine, then its end with everything in the store. */
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);var work=building(e,workId);var mine=e.settlement().buildings().stream().filter(b->b.type().equals("mine")&&b.id().equals(Settlement.childId(e.settlement().id(),"building/probe-mine"))).findFirst().orElseThrow();
  var t=WarehouseTrips.inspect(l,courierId);var custody=WarehouseTrips.custody(l,t);int held=custody.stream().mapToInt(ItemStack::getCount).sum();
  var pc=LogisticsRoutes.chest(l,e,mine);var stock=LogisticsRoutes.chest(l,e,work);
  String status=l.getEntity(courierId) instanceof ResidentEntity npc?npc.workStatus()+"@"+npc.blockPosition().toShortString():"-";
  if(WarehouseTrips.active(t)&&tripId==null){boolean fromMine=true;var legs=t.getList("legs",Tag.TAG_COMPOUND);for(int i=0;i<legs.size();i++)fromMine&=legs.getCompound(i).getUUID("source").equals(mine.id());
   // The trip followed from here: its own record (the village's other porters may take from the mine or the store meanwhile).
   if(fromMine&&legs.size()==6&&!t.getString("stage").equals("return")){tripId=t.getUUID("id");tripTag=t;int n=0;for(int i=0;i<legs.size();i++)n+=ItemStack.of(legs.getCompound(i).getCompound("item")).getCount();planned=n;stockBefore=total(stock);}}
  if(tripId!=null&&WarehouseTrips.active(t)&&t.getUUID("id").equals(tripId)&&custody.size()==6&&!t.getString("stage").equals("work")){loaded=true;
   var a=WarehouseTrips.audit(l,t);if(a[0]!=planned||a[1]+a[2]!=a[0])failure="The load does not match its trip on the way: taken "+a[0]+" delivered "+a[1]+" held "+a[2]+" of "+planned;}
  // The end: every leg taken once and put once (the journal's receipts), nothing held; the store got at least that much of the mine's goods.
  if(tripId!=null&&loaded&&!delivered&&(!WarehouseTrips.active(t)||!t.getUUID("id").equals(tripId))){var a=WarehouseTrips.audit(l,tripTag);moved=a[1];
   if(a[0]!=planned||a[1]!=planned||a[2]!=0)failure="The trip did not deliver its load exactly once: taken "+a[0]+" delivered "+a[1]+" held "+a[2]+" of "+planned;
   sumBefore=total(stock)-stockBefore;delivered=true;}
  var cs=LogisticsRoutes.chest(l,e,building(e,castleId));sortedMoves=unsortedBefore-WarehouseSort.unsorted(cs);
  pulling=t.hasUUID("cart")&&l.getEntity(t.getUUID("cart")) instanceof CartEntity c&&courierId.equals(c.puller())&&c.shown()>0&&t.getString("stage").equals("go");
  wolves=CartWolves.refusal(l,e,building(e,castleId));
  var parcel=PorterWork.inspect(l,courierId);String hand=PorterWork.active(parcel)?building(e,parcel.getUUID("source")).type()+"->"+(parcel.getUUID("destination").equals(castleId)?"castle":parcel.getUUID("destination").equals(workId)?"work":building(e,parcel.getUUID("destination")).type())+" "+ItemStack.of(parcel.getCompound("item")):"-";
  String nav=l.getEntity(courierId) instanceof ResidentEntity npc&&npc.getNavigation().getTargetPos()!=null?npc.getNavigation().getTargetPos().toShortString():"-";
  progress="open="+WarehouseCarts.open(l,e,work)+" carts="+WarehouseCarts.carts(l,work).size()+" free="+(WarehouseCarts.free(l,e,work,null)!=null)+" parcel="+hand+" nav="+nav+" trip="+(tripId!=null)+" stage="+t.getString("stage")+" custody="+custody.size()+" planned="+planned+" loaded="+loaded+" delivered="+delivered+" courier="+status+" unsorted="+WarehouseSort.unsorted(cs)+"/"+unsortedBefore;
 }catch(Exception ex){failure=ex.toString();}});}
 /** The camera at an offset of a building's lot, looking at a point of it. */
 private static void look(Minecraft mc,UUID id,Settlement.Building laid,double ex,double ey,double ez,double tx,double ty,double tz){var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);var o=BuildingPlacement.origin(e,laid!=null?laid:building(e,id));
  double dx=tx-ex,dy=ty-ey,dz=tz-ez;float yaw=(float)(-Math.toDegrees(Math.atan2(dx,dz))),pitch=(float)(-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));
  s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),o.getX()+ex,o.getY()+ey,o.getZ()+ez,yaw,pitch);});}
 private static Settlement.Building laid(SettlementData.Entry e,String name,int x){return new Settlement.Building(Settlement.childId(e.settlement().id(),"building/"+name),WarehouseStore.TYPE,x,0,-84,0);}
 /** The courier's place, for the cart frame. */
 private static void follow(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{if(s.overworld().getEntity(courierId) instanceof ResidentEntity npc){var at=npc.position();
  s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),at.x-7,at.y+6,at.z-5,-55,32);}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>40000)throw new IllegalStateException("Warehouse timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%20==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_WAREHOUSE progress {}",progress);
   if(loaded&&!delivered&&tripId!=null){phase=2;ticks=0;mc.options.hideGui=true;follow(mc);}
   else if(ticks>16000)throw new IllegalStateException("The courier did not load its cart: "+progress);}
  else if(phase==2&&ticks%20==0){sample(mc);if(ticks==40)follow(mc);
   if(ticks>=80){if(pulling){capture(mc,"cart");phase=3;ticks=0;}else follow(mc);}
   if(ticks>6000)throw new IllegalStateException("No frame of the cart on its way: "+progress);}
  else if(phase==3&&ticks%20==0){sample(mc);if(delivered){phase=4;ticks=0;var e=entry(mc.getSingleplayerServer());look(mc,null,laid(e,"probe-store-1",-64),-7,13,-13,11.5,3,8.5);}
   else if(ticks>16000)throw new IllegalStateException("The trip did not end: "+progress);}
  else if(phase==4&&ticks>80){capture(mc,"level1");var e=entry(mc.getSingleplayerServer());look(mc,null,laid(e,"probe-store-3",-36),-7,15,-14,11.5,4,8.5);phase=5;ticks=0;}
  else if(phase==5&&ticks>80){capture(mc,"level3");look(mc,castleId,null,-8,19,-16,11.5,5,8.5);phase=6;ticks=0;}
  else if(phase==6&&ticks>80){capture(mc,"level6");sample(mc);phase=7;ticks=0;}
  // The sorted store: a stone page of the VI store opened after its machine has taken its turns.
  else if(phase==7&&ticks%20==0){sample(mc);if(sortedMoves>=3&&ticks>=200){mc.options.hideGui=false;var s=mc.getSingleplayerServer();s.execute(()->{var l=s.overworld();var e=entry(s);var b=building(e,castleId);
     var pos=WarehouseStore.at(e,b,WarehouseStore.chests(6).get(4));var menu=HallStorage.menu(l,pos);if(menu==null){failure="The stone page of the VI store does not open";return;}
     var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(l,pos.getX()+.5,pos.getY(),pos.getZ()-1.5,0,20);p.openMenu(menu);});phase=8;ticks=0;}
   else if(ticks>4000)throw new IllegalStateException("The VI store did not sort: "+progress);}
  else if(phase==8&&ticks>60){capture(mc,"sorted");mc.setScreen(null);var s=mc.getSingleplayerServer();s.execute(()->{var e=entry(s);s.getPlayerList().getPlayers().get(0).teleportTo(s.overworld(),e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);});
   BuildingsPanel.select(workId);phase=9;ticks=0;}
  else if(phase==9&&ticks>40){OfficeProbes.open(mc,ConstructionScreen.BUILDINGS);phase=10;ticks=0;}
  else if(phase==10&&ticks>80&&mc.screen instanceof ConstructionScreen){
   CompoundTag card=null;for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND))if(((CompoundTag)raw).hasUUID("id")&&((CompoundTag)raw).getUUID("id").equals(workId))card=(CompoundTag)raw;
   if(card==null||!card.contains("warehouse"))throw new IllegalStateException("The working warehouse's card has no warehouse rows: "+card);
   var w=card.getCompound("warehouse");capture(mc,"card");mc.setScreen(null);
   facts="planned="+planned+" moved="+moved+" sum="+sumBefore+" sorted="+sortedMoves+" slots="+w.getInt("slots")+" carts="+w.getString("carts")+" wolves="+wolves;
   if(moved!=planned||planned<=LogisticsRoutes.load(2)+4*64)throw new IllegalStateException("The store did not get exactly the trip's load: "+facts);
   LogUtils.getLogger().info("ASTRA_WAREHOUSE VERIFIED the courier of II pulled its cart with a parcel and five stacks from the mine into the store, the VI store sorted itself, card with store and couriers: {}; reload=false",facts);
   mc.stop();phase=11;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WAREHOUSE FAILED",ex);mc.options.hideGui=false;mc.stop();}}
}
