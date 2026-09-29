package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** ISO-003/ISO-005: the mayor plans a design on the isometric atlas and reads its estimate — operations, materials, what the hall already holds and what is missing, plus kept drafts. */
final class PlanProbe {
 static final int NEEDED=4;
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile int surveyed;private static volatile boolean ready,stocked;
 private static volatile BlockPos site;private static volatile int firstMissing=-1,delivered;private static volatile String shortItem="",turned="";private static int[] straight=new int[0];
 static boolean enabled(){return Boolean.getBoolean("villageastra.planSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-plan-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_PLAN screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** Fixture: a cartographer with paper opens the map, the player is the mayor, and one flat patch inside the opened area is prepared as a site. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  var office=new Settlement.Building(Settlement.childId(settlement.id(),"building/cartographer-plan"),"cartographer",-44,0,-4);settlement.addBuilding(office);
  var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(l,e,office);if(chest==null)throw new IllegalStateException("Office chest missing");chest.setItem(0,new ItemStack(Items.PAPER,32));
  var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/cartographer-plan"),1,1,true);settlement.addHome(home);
  var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(settlement.id(),settlement.resident(r.id()));npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
  settlement.assign(r.id(),Profession.CARTOGRAPHER,office.id());SettlementData.get(s).setDirty();
  LogUtils.getLogger().info("ASTRA_PLAN fixture mayor appointed, cartographer at {} with 32 paper",chestPos.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 /** A flat patch inside the opened area, so the plan is about a real, orderable spot rather than a slope. */
 private static void prepare(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var design=BuildingBlueprints.design("home");
  var origin=Expeditions.surface(l,e.center().getX()-30,e.center().getZ()+8,e.center().getY()+8,4,16);
  for(int dx=-2;dx<design.width()+2;dx++)for(int dz=-2;dz<design.depth()+2;dz++){
   var cell=origin.offset(dx,0,dz);
   for(int dy=-3;dy<0;dy++)l.setBlock(cell.offset(0,dy,0),Blocks.STONE.defaultBlockState(),2);
   l.setBlock(cell,Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int dy=1;dy<=12;dy++)l.setBlock(cell.above(dy),Blocks.AIR.defaultBlockState(),2);}
  site=origin;
  LogUtils.getLogger().info("ASTRA_PLAN fixture flat site at {}",site.toShortString());
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Plan timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%100==0){
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);surveyed=Atlas.surveyed(Atlas.inspect(s.overworld(),e.settlement().id())).size();});
   progress="surveyed="+surveyed;
   if(surveyed>=NEEDED){prepare(mc);
    mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var c=entry(mc.getSingleplayerServer()).center();p.teleportTo(p.serverLevel(),c.getX()+.5,c.getY()+2,c.getZ()+.5,0,0);});
    mc.setScreen(new AtlasScreen());phase=2;ticks=0;}}
  else if(phase==2&&mc.screen instanceof AtlasScreen screen&&ticks>60){
   if(AtlasScreen.opened()<NEEDED){if(ticks>600)throw new IllegalStateException("Client received "+AtlasScreen.opened()+" chunks");return;}
   // The mayor takes the house tool and picks a cell on the map itself: the click goes through the same picking a player uses.
   var tab=screen.buttonCenter("tool_house");screen.mouseClicked(tab[0],tab[1],0);screen.mouseReleased(tab[0],tab[1],0);
   if(AtlasScreen.tool()!=AtlasScreen.HOUSE)throw new IllegalStateException("The house tool did not open");
   boolean picked=false;
   for(int radius=0;radius<=96&&!picked;radius+=6)for(int dx=-radius;dx<=radius&&!picked;dx+=6)for(int dz=-radius;dz<=radius&&!picked;dz+=6){
    double mx=screen.width/2D+dx,my=screen.height/2D+dz;
    if(!screen.pickable(mx,my))continue;
    if(screen.mouseClicked(mx,my,0)){screen.mouseReleased(mx,my,0);picked=true;}}
   if(!picked)throw new IllegalStateException("Map click hit no opened column");
   phase=3;ticks=0;}
  else if(phase==3&&ticks>40){
   if(AtlasScreen.plan().isEmpty())throw new IllegalStateException("No estimate came back for the picked cell");
   if(site==null)throw new IllegalStateException("Fixture site missing");
   ConstructionNetwork.sendPlan(new ConstructionNetwork.PlanRequest("home",0,site.asLong()));phase=4;ticks=0;}
  else if(phase==4&&ticks>40){
   var plan=AtlasScreen.plan();
   if(!BlockPos.of(plan.getLong("origin")).equals(site))throw new IllegalStateException("Estimate is for another spot: "+BlockPos.of(plan.getLong("origin")).toShortString());
   if(!plan.getBoolean("ok"))throw new IllegalStateException("Prepared site is not orderable: "+plan.getString("reason")+" conflicts="+plan.getInt("conflictCount"));
   if(plan.getInt("operations")<=0||plan.getInt("items")<=0||plan.getInt("place")<=0)throw new IllegalStateException("Estimate is empty: "+plan);
   firstMissing=plan.getInt("shortage");
   if(firstMissing<=0)throw new IllegalStateException("Empty hall stock must leave the estimate short");
   progress="operations="+plan.getInt("operations")+" cells="+plan.getInt("cells")+" items="+plan.getInt("items")+" shortage="+firstMissing;
   // A07-VIS-001: the plan carries its future volume block by block, and the layout button shows it in blue on the map.
   if(plan.getIntArray("future").length<4)throw new IllegalStateException("The estimate carries no future volume");
   progress+=" future="+plan.getIntArray("future").length/4+" demolish="+plan.getLongArray("demolish").length;
   LogUtils.getLogger().info("ASTRA_PLAN estimate {}",progress);
   phase=41;ticks=0;}
  else if(phase==41&&ticks>10){
   var plan=AtlasScreen.plan();
   capture(mc,"estimate");
   // The delivery is of a material the settlement is really short of, in an empty slot: the estimate must follow the real stock.
   for(var raw:plan.getList("materials",net.minecraft.nbt.Tag.TAG_COMPOUND)){var row=(net.minecraft.nbt.CompoundTag)raw;
    if(row.getInt("missing")>0){shortItem=row.getString("item");delivered=Math.min(64,row.getInt("missing"));break;}}
   if(shortItem.isEmpty())throw new IllegalStateException("No short material to deliver: "+plan);
   mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var e=entry(s);
    var chest=LogisticsRoutes.chest(s.overworld(),e,Workshops.hall(e));if(chest==null)throw new IllegalStateException("Hall chest missing");
    var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(shortItem));
    for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).isEmpty()){chest.setItem(i,new ItemStack(item,delivered));stocked=true;return;}
    throw new IllegalStateException("Hall chest is full");}catch(Exception ex){failure=ex.toString();}});
   phase=5;ticks=0;}
  else if(phase==5&&stocked&&ticks>20){ConstructionNetwork.sendPlan(new ConstructionNetwork.PlanRequest("home",0,site.asLong()));phase=6;ticks=0;}
  else if(phase==6&&ticks>40&&mc.screen instanceof AtlasScreen screen){
   var plan=AtlasScreen.plan();
   if(plan.getInt("shortage")!=firstMissing-delivered)throw new IllegalStateException("Delivered "+delivered+" "+shortItem+" must cut the shortage by exactly that: "+plan.getInt("shortage")+" after "+firstMissing);
   // AD-068: the turn button asks the same estimate turned a quarter clockwise; the future volume is the unturned one turned in its corner box.
   straight=plan.getIntArray("future");var turn=screen.buttonCenter("turn");
   if(turn==null||!screen.mouseClicked(turn[0],turn[1],0))throw new IllegalStateException("Turn button missed");screen.mouseReleased(turn[0],turn[1],0);
   phase=61;ticks=0;}
  else if(phase==61&&ticks>40&&mc.screen instanceof AtlasScreen screen){
   var plan=AtlasScreen.plan();
   if(plan.getInt("variant")!=1||AtlasScreen.turns()!=1){if(ticks>400)throw new IllegalStateException("No turned estimate came back: variant="+plan.getInt("variant")+" turns="+AtlasScreen.turns());return;}
   var expected=new HashSet<String>();var got=new HashSet<String>();int tw=plan.getInt("width"),td=plan.getInt("depth");
   for(int i=0;i+3<straight.length;i+=4){var q=BuildingPlacement.turn(straight[i],straight[i+1],straight[i+2],tw,td,1);
    expected.add(q.toShortString()+"="+net.minecraft.world.level.block.Block.getId(BuildingPlacement.state(net.minecraft.world.level.block.Block.stateById(straight[i+3]),1)));}
   var future=plan.getIntArray("future");for(int i=0;i+3<future.length;i+=4)got.add(future[i]+", "+future[i+1]+", "+future[i+2]+"="+future[i+3]);
   var only=new HashSet<>(expected);only.removeAll(got);
   if(only.size()>0)throw new IllegalStateException("The turned volume is not the straight one turned: "+only.size()+" of "+expected.size()+" missing, e.g. "+only.stream().limit(3).toList()+" got "+got.stream().limit(3).toList());
   turned="turned="+plan.getInt("variant")+" match="+expected.size()+"/"+got.size()+" ok="+plan.getBoolean("ok");
   LogUtils.getLogger().info("ASTRA_PLAN {}",turned);
   capture(mc,"turned");
   var draft=screen.buttonCenter("draft");int bx=draft[0],by=draft[1];
   var widget=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.isMouseOver(bx,by)).findFirst().orElse(null);
   if(widget==null)throw new IllegalStateException("No shown button under the draft spot "+bx+","+by+" tool="+AtlasScreen.tool());
   if(!screen.mouseClicked(bx,by,0))throw new IllegalStateException("Draft button missed");screen.mouseReleased(bx,by,0);
   if(AtlasScreen.drafts()!=1)throw new IllegalStateException("Draft was not kept: "+AtlasScreen.drafts()+" clicked "+((net.minecraft.client.gui.components.Button)widget).getMessage().getString()+" plan keys "+plan.getAllKeys());
   capture(mc,"draft");
   // OWNER_REQUEST 9.2: the office is lost while the map is open — the order tools go dark at once.
   mc.getSingleplayerServer().execute(()->{var e=entry(mc.getSingleplayerServer());e.settlement().appointNpcMayor();SettlementData.get(mc.getSingleplayerServer()).setDirty();});
   phase=62;ticks=0;}
  else if(phase==62&&ticks%10==0&&mc.screen instanceof AtlasScreen screen){
   if(AtlasScreen.commands()||AtlasScreen.tool()!=AtlasScreen.VIEW){if(ticks>600)throw new IllegalStateException("The open map kept the order tools after the office was lost: tool="+AtlasScreen.tool());return;}
   // An inactive button is never "under the mouse", so the tab is taken by its name.
   var tab=screen.widget("tool_house");if(tab==null)throw new IllegalStateException("The house tab is gone");
   if(tab.active)throw new IllegalStateException("The house tab is still active without the office");
   turned+="; office lost: tools off=true";
   var plan=AtlasScreen.plan();
   LogUtils.getLogger().info("ASTRA_PLAN VERIFIED mayor planned home on the atlas: {}; delivering {} {} cut the shortage to {}; {}; draft kept {} of {}; reload=false",progress,delivered,shortItem,firstMissing-delivered,turned,AtlasScreen.drafts(),Plans.DRAFTS);
   mc.setScreen(null);mc.stop();phase=7;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_PLAN FAILED",ex);mc.stop();}}
}
