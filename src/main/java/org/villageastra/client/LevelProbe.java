package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-073: the mayor orders the next level of a workshop in the office and the village builder really rebuilds it — plinth, lamps and the equipment
 *  of that level — from the hall stock, and the building is then kept and works at level two. */
final class LevelProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready;
 private static volatile java.util.UUID shopId;
 static boolean enabled(){return Boolean.getBoolean("villageastra.levelSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-level-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_LEVEL screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** What the container at the hall really holds of every cost item, counted the way the builder counts it. */
 private static String raw(net.minecraft.world.Container box,CompoundTag cost){var out=new StringBuilder("{");
  for(var key:cost.getAllKeys()){int have=0;
   for(int slot=0;slot<box.getContainerSize();slot++)if(BuiltInRegistries.ITEM.getKey(box.getItem(slot).getItem()).toString().equals(key))have+=box.getItem(slot).getCount();
   out.append(key.replace("minecraft:","")).append('=').append(have).append('/').append(cost.getInt(key)).append(' ');}
  return out.append('}').toString();}
 /** The mayor's own order from the office: take off the queue what nobody has started building. */
 private static void callOff(){var t=ConstructionOverlay.snapshot();if(!t.hasUUID("village")||!t.hasUUID("id"))return;
  ConstructionNetwork.sendCancel(new ConstructionNetwork.CancelOrder(t.getUUID("village"),t.getUUID("id"),t.getLong("epoch"),t.getLong("revision")));
  LogUtils.getLogger().info("ASTRA_LEVEL the mayor called off the project the village had queued");}
 /** Fixture: the player is mayor, a bakery stands beside the hall, its research is done and the hall holds exactly what the second level takes. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
  // North of the houses, clear of every lot: its levelled pad (2 blocks round) must not cut into a house, or the house is marked damaged
  // and its people shelter in the hall doorway the builder has to pass.
  var shop=new Settlement.Building(Settlement.childId(settlement.id(),"building/restaurant-level"),"restaurant",14,0,-18);settlement.addBuilding(shop);shopId=shop.id();
  var origin=BuildingPlacement.origin(e,shop);
  for(int x=-2;x<11;x++)for(int z=-2;z<11;z++){l.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
   for(int y=0;y<12;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
  for(var cell:BuildingPlacement.layout(e,shop,shop.type()).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
  // The research the catalogue asks for that level is already paid for.
  var research=BookResearch.inspect(l,e);var done=research.getList("legacyDone",Tag.TAG_STRING);
  for(var id:BuildingTiers.research("restaurant",2))done.add(StringTag.valueOf(id));
  research.put("legacyDone",done);BookResearch.store(l,e,research);
  // The village may already be building something of its own; this probe wants the crew free.
  if(HallUpgradeGoal.pending(l,settlement.id())){HallUpgradeGoal.drop(l,settlement.id());LogUtils.getLogger().info("ASTRA_LEVEL fixture cleared the project the village had queued");}
  // AD-136: no building rises above the hall, so a level-two order needs the hall at II first; the mending below then
  // stands the hall's second-level design, as a finished hall project would have left it.
  if(settlement.civilization().level()<2){settlement.civilization().completedHallUpgrade(2);SettlementData.get(s).setDirty();LogUtils.getLogger().info("ASTRA_LEVEL fixture raised the hall to II");}
  // A damaged building would keep the crew busy and eat the stock: the fixture puts back what the village had lost.
  int mended=0;
  for(var b:java.util.List.copyOf(settlement.buildings())){
   var missing=BuildingRepairs.damage(l,e,b);if(missing.isEmpty())continue;
   var design=BuildingPlacement.layout(BuildingRepairs.design(e,b),e.center().offset(b.x(),b.y(),b.z()),b.rotation());
   for(var pos:missing){var block=design.get(pos);if(block!=null)l.setBlock(pos,block,3);}
   mended+=missing.size();}
  if(mended>0)LogUtils.getLogger().info("ASTRA_LEVEL fixture made {} cells of the village whole again",mended);
  var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall==null)throw new IllegalStateException("Hall chest missing");
  // Exactly what the builder's own survey asks for, scaffolds included.
  var estimate=BuildingTiers.survey(l,e,shop).state().getCompound("cost");
  int slot=0;
  for(var key:estimate.getAllKeys()){
   var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=estimate.getInt(key);
   while(left>0){while(slot<hall.getContainerSize()&&!hall.getItem(slot).isEmpty())slot++;
    if(slot>=hall.getContainerSize())throw new IllegalStateException("Hall chest is full");
    int n=Math.min(item.getMaxStackSize(),left);hall.setItem(slot,new ItemStack(item,n));left-=n;}}
  // AD-137: exactly the estimate, no share for the miner's lights: once ordered, the reserve keeps it for the builder.
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_LEVEL fixture bakery at {}, its research done and {} item kinds for level two in the hall",origin.toShortString(),estimate.getAllKeys().size());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>20000)throw new IllegalStateException("Level timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){OfficeProbes.open(mc,ConstructionScreen.LEVELS);phase=2;ticks=0;}
  else if(phase==2&&ticks>60&&mc.screen instanceof ConstructionScreen screen){
   var upgrades=ConstructionOverlay.snapshot().getCompound("upgrades");
   var rows=upgrades.getList("buildings",Tag.TAG_COMPOUND);
   if(rows.isEmpty())throw new IllegalStateException("The levels tab lists no building");
   int row=-1;for(int i=0;i<rows.size();i++)if(rows.getCompound(i).getUUID("id").equals(shopId))row=i;
   if(row<0)throw new IllegalStateException("The new bakery is not listed");
   var bakery=rows.getCompound(row);
   if(bakery.getInt("kept")!=1||bakery.getInt("next")!=2||bakery.getInt("max")!=6)throw new IllegalStateException("The bakery should start at level one of six: "+bakery);
   // The village keeps its own crew busy with repairs; the order waits its turn like any mayor's would.
   if(bakery.getString("refusal").equals("busy")){
    // AD-078: the crew is on a project of its own; the mayor calls off whatever nobody has started yet.
    progress="the village had its own project; the mayor calls it off";
    if(ticks%100==0)callOff();
    if(ticks>6000)throw new IllegalStateException("The crew never became free: "+bakery);return;}
   if(bakery.getInt("lack")!=0||!bakery.getString("refusal").isEmpty())throw new IllegalStateException("The hall stock and the research should cover the level: "+bakery);
   progress="rows="+rows.size()+" items="+bakery.getInt("items")+" worth="+bakery.getLong("worth")+" row="+row;
   capture(mc,"tab");
   // The list scrolls instead of paging: bring the bakery's row into view and press its own button, as a player does.
   UpgradePanel.reveal(shopId);var order=UpgradePanel.buttonFor(shopId);
   if(order==null)throw new IllegalStateException("The bakery's row shows no upgrade button: row "+row+" of "+rows.size());
   if(!order.active)throw new IllegalStateException("The bakery's upgrade button is closed: "+(order instanceof OfficeUi.OfficeButton b&&b.reason()!=null?b.reason().getString():"")+" "+bakery);
   if(!screen.layout().content().containsRect(OfficeUi.Rect.of(order)))throw new IllegalStateException("The bakery's upgrade button leaves the content: "+OfficeUi.Rect.of(order));
   OfficeProbes.clickOrFail(screen,order,"The bakery's upgrade button");phase=3;ticks=0;}
  else if(phase==3&&ticks%100==0){
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);
    var shop=e.settlement().buildings().stream().filter(b->b.id().equals(shopId)).findFirst().orElse(null);
    if(shop==null){failure="The bakery record disappeared";return;}
    int level=BuildingLevels.level(s.overworld(),e,shop);
    var project=HallUpgradeGoal.inspect(s.overworld(),e.settlement().id());
    var crew=new StringBuilder();
    for(var x:e.settlement().residents())if(x.profession()==Profession.BUILDER&&x.alive()){
     var npc=s.overworld().getEntity(x.id());
     crew.append(npc instanceof ResidentEntity r?(project.hasUUID("worker")&&project.getUUID("worker").equals(x.id())?"*":"")+r.workStatus()+" at "+r.blockPosition().toShortString()+"; ":"away; ");}
    // Everyone else too: who stands in a doorway the crew waits at, and on which goal.
    for(var x:e.settlement().residents())if(x.profession()!=Profession.BUILDER&&x.alive()&&s.overworld().getEntity(x.id()) instanceof ResidentEntity r)
     crew.append(x.profession()==null?"idle":x.profession().name()).append(':').append(r.workStatus()).append(" at ").append(r.blockPosition().toShortString()).append(' ').append(r.runningGoals()).append("; ");
    var hall=LogisticsRoutes.chest(s.overworld(),e,Workshops.hall(e));var short_=new StringBuilder();
    var cost=project.getCompound("cost");
    for(var key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));
     int have=hall==null?0:LogisticsRoutes.count(hall,x->x.is(item));
     if(have<cost.getInt(key))short_.append(key).append(' ').append(have).append('/').append(cost.getInt(key)).append(' ');}
    var source=e.center().offset(1,1,4);
    progress="level="+level+" kept="+shop.level()+" short=["+short_+"]"
      +" project="+project.getInt("index")+"/"+project.getList("ops",Tag.TAG_COMPOUND).size()+" complete="+project.getBoolean("complete")
      +" funded="+project.getBoolean("funded")+" withdrawals="+project.getInt("withdrawals")+" cargo="+project.getList("cargo",Tag.TAG_COMPOUND).size()
      +" stock="+(hall==null?"none":LogisticsRoutes.position(e,Workshops.hall(e)).toShortString())+" fund_source="+source.toShortString()
      +" container="+(s.overworld().getBlockEntity(source) instanceof net.minecraft.world.Container box?box.getClass().getSimpleName()+raw(box,cost):s.overworld().getBlockState(source).getBlock().toString())
      +" crew=["+crew+"]";
    if(shop.level()>=2&&level>=2)phase=4;
    // The village keeps putting its own repair on the queue; if ours is not the project being built, it is ordered again.
    else if(project.getInt("upgradeLevel")!=2){callOff();phase=2;ticks=0;}});
   LogUtils.getLogger().info("ASTRA_LEVEL progress {}",progress);
   if(ticks>18000)throw new IllegalStateException("The builder never raised the level: "+progress);}
  else if(phase==4&&ticks>20){
   capture(mc,"built");
   LogUtils.getLogger().info("ASTRA_LEVEL VERIFIED mayor ordered level two in the office and the builder rebuilt it for real: {}; reload=false",progress);
   mc.setScreen(null);mc.stop();phase=5;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_LEVEL FAILED",ex);mc.stop();}}
}
