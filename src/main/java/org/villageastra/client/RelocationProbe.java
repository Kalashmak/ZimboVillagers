package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-125: the mayor moves a building through the atlas — picks its card, presses «Move», clicks a free spot, turns it once and orders; the
 *  village's own builders take it apart into the project's cargo, build it again at the new place and carry its chest slot for slot. */
final class RelocationProbe {
 private static int phase,ticks,candidate;private static volatile String failure,progress="";private static volatile boolean ready,finished,verified;
 private static volatile UUID village,building;private static volatile long epoch;private static volatile BlockPos from,site;private static volatile int rotation,turns;
 private static volatile List<BlockPos> candidates=List.of();private static volatile int levelBefore,gradeBefore,itemsBefore,evictions,dropped,skipped,done,total;
 private static volatile String result="";private static final Set<UUID> seenDrops=new HashSet<>(),startDrops=new HashSet<>();
 private static final Map<Integer,ItemStack> KNOWN=new LinkedHashMap<>();private static Set<UUID> dwellers=Set.of();private static boolean workShot;
 static boolean enabled(){return Boolean.getBoolean("villageastra.relocateSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-relocate-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RELOCATE screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entry(village);}
 private static Settlement.Building moved(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(building)).findFirst().orElse(null);}
 private static int topUp(OwnedChestEntity hall,Map<Item,Integer> needs){int added=0;
  for(var need:needs.entrySet()){int have=LogisticsRoutes.count(hall,x->x.is(need.getKey()));int left=need.getValue()-have;
   for(int slot=0;slot<hall.getContainerSize()&&left>0;slot++)if(hall.getItem(slot).isEmpty()){int n=Math.min(need.getKey().getMaxStackSize(),left);hall.setItem(slot,new ItemStack(need.getKey(),n));left-=n;added+=n;}}
  return added;}
 private static Map<Item,Integer> items(CompoundTag cost,CompoundTag state){var out=new LinkedHashMap<Item,Integer>();
  for(var key:cost.getAllKeys()){int n=cost.getInt(key)-Relocations.held(state,key);if(n>0)out.put(BuiltInRegistries.ITEM.get(new ResourceLocation(key)),n);}return out;}
 private static int count(OwnedChestEntity c){int n=0;if(c!=null)for(int i=0;i<c.getContainerSize();i++)n+=c.getItem(i).getCount();return n;}
 /** Both lots with their three-block buffer and room for a roof: an item dropped there is a block the move let fall. */
 private static List<AABB> area(){var out=new ArrayList<AABB>();for(var p:site==null?List.of(from):List.of(from,site))out.add(new AABB(p.getX()-3,p.getY()-3,p.getZ()-3,p.getX()+16,p.getY()+20,p.getZ()+16));return out;}
 private static List<ItemEntity> drops(ServerLevel l){var out=new ArrayList<ItemEntity>();for(var box:area())out.addAll(l.getEntitiesOfClass(ItemEntity.class,box));return out;}
 /** Fixture: the player is mayor, the map is opened over the whole village, the crew is free, and a building with a chest is chosen and
  *  its chest given known stacks in fixed slots; then free spots twenty blocks and more away are found by the server's own plan. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=SettlementData.get(s).entries().iterator().next();var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  village=settlement.id();settlement.appointPlayerMayor(p.getUUID());epoch=settlement.governance().epoch();
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
  var office=new Settlement.Building(Settlement.childId(settlement.id(),"building/cartographer-relocate"),"cartographer",-44,0,-4);settlement.addBuilding(office);
  var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var officeChest=LogisticsRoutes.chest(l,e,office);if(officeChest==null)throw new IllegalStateException("Office chest missing");
  for(int slot=0;slot<3;slot++)officeChest.setItem(slot,new ItemStack(Items.PAPER,64));
  var home=new Settlement.Home(Settlement.childId(settlement.id(),"home/cartographer-relocate"),1,1,true);settlement.addHome(home);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);settlement.admit(r,home.id());settlement.assign(r.id(),Profession.CARTOGRAPHER,office.id());
  // The map is opened over the whole village at once: what is looked at here is the move, not the cartographer's walks.
  int opened=0;for(var c:Atlas.area(l,e)){l.getChunk(c.x,c.z);if(Atlas.survey(l,e,office,r.id(),c,l.getGameTime()))opened++;}
  if(HallUpgradeGoal.pending(l,village))HallUpgradeGoal.drop(l,village);
  var road=Roads.project(l,village);if(road!=null&&!road.getBoolean("complete")){road.putBoolean("complete",true);Roads.save(l,village,road);}
  var tried=new StringBuilder();Settlement.Building chosen=null;
  for(var type:List.of("carpentry","home","home_2"))for(var b:settlement.buildings()){if(chosen!=null||!b.type().equals(type))continue;
   var why=Relocations.refusal(l,e,b);tried.append(b.type()).append('=').append(why.isEmpty()?"ok":why).append(' ');
   if(why.isEmpty()&&LogisticsRoutes.chest(l,e,b)!=null)chosen=b;}
  if(chosen==null)throw new IllegalStateException("No building may move: "+tried);
  building=chosen.id();from=BuildingPlacement.origin(e,chosen);rotation=chosen.rotation();turns=(rotation+1)%4;
  levelBefore=BuildingTiers.level(l,e,chosen);gradeBefore=Relocations.grade(l,e,chosen);
  var chest=LogisticsRoutes.chest(l,e,chosen);for(int i=0;i<chest.getContainerSize();i++)chest.setItem(i,ItemStack.EMPTY);
  KNOWN.put(2,new ItemStack(Items.NAME_TAG,2));KNOWN.put(7,new ItemStack(Items.CLOCK,1));KNOWN.put(13,new ItemStack(Items.LAPIS_LAZULI,9));KNOWN.put(20,new ItemStack(Items.FLINT,5));
  for(var k:KNOWN.entrySet())chest.setItem(k.getKey(),k.getValue().copy());itemsBefore=count(chest);
  var ids=new HashSet<UUID>();for(var x:settlement.residents())if(x.alive()&&building.equals(x.home()))ids.add(x.id());dwellers=ids;
  // Free spots found by the server's own plan, ring after ring from twenty blocks out, at the turn the mayor will choose.
  var found=new ArrayList<BlockPos>();var ringLog=new StringBuilder();
  for(int radius=20;radius<=44&&found.size()<6;radius+=4)for(int a=0;a<16&&found.size()<6;a++){double angle=Math.PI*2*a/16;
   int x=from.getX()+(int)Math.round(Math.cos(angle)*radius),z=from.getZ()+(int)Math.round(Math.sin(angle)*radius);
   var to=new BlockPos(x,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1,z);
   var plan=Relocations.plan(l,e,chosen,to,turns);if(plan.ok())found.add(to);else if(a%4==0)ringLog.append(radius).append('/').append(a).append('=').append(plan.reason()).append(' ');}
  if(found.isEmpty())throw new IllegalStateException("No free spot for the move: "+ringLog);
  candidates=found;
  for(var x:drops(l))startDrops.add(x.getUUID());
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_RELOCATE fixture {} chunks opened; buildings {}; moving {} {} at {} turn {} level {} grade {}; chest {} items; dwellers {}; spots {}",
   opened,tried,chosen.type(),building,from.toShortString(),rotation,levelBefore,gradeBefore,itemsBefore,dwellers.size(),found.stream().map(BlockPos::toShortString).toList());
  ready=true;
 }catch(Exception ex){failure=ex.toString();LogUtils.getLogger().error("ASTRA_RELOCATE fixture",ex);}});}
 /** Samples the move on the server: the project, its phase, the building's record, drops near both lots and the dwellers' home. */
 private static void sample(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);if(e==null){failure="The village disappeared";return;}
  for(var x:drops(l))if(!startDrops.contains(x.getUUID()))seenDrops.add(x.getUUID());dropped=seenDrops.size();
  for(var id:dwellers){var r=e.settlement().resident(id);if(r==null||!building.equals(r.home())||e.settlement().homes().stream().anyMatch(h->h.id().equals(building)&&!h.usable()))evictions++;}
  var b=moved(e);if(b==null){failure="The moving building lost its record";return;}
  boolean pending=HallUpgradeGoal.pending(l,village);var state=pending?HallUpgradeGoal.inspect(l,village):new CompoundTag();boolean ours=pending&&state.getBoolean("relocate");
  if(ours){var ops=state.getList("ops",Tag.TAG_COMPOUND);int d=0,sk=0,phase=3;boolean first=true;for(var raw:ops){var t=(CompoundTag)raw;if(t.getBoolean("done")){d++;if(t.getBoolean("skipped"))sk++;}else if(first){phase=t.getInt("phase");first=false;}}
   done=d;total=ops.size();skipped=sk;
   if(!state.getBoolean("funded")){var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));if(hall!=null)topUp(hall,items(state.getCompound("cost"),state));}
   var crew=new StringBuilder();for(var x:e.settlement().residents())if(x.alive()&&x.profession()==Profession.BUILDER)crew.append(l.getEntity(x.id()) instanceof ResidentEntity n?n.workStatus()+"@"+n.blockPosition().toShortString()+"/"+n.runningGoals()+" on "+BuiltInRegistries.BLOCK.getKey(l.getBlockState(n.blockPosition().below()).getBlock()).getPath():"away").append(' ');
   var crewAt=e.settlement().residents().stream().filter(x->x.alive()&&x.profession()==Profession.BUILDER).map(x->l.getEntity(x.id())).filter(java.util.Objects::nonNull).map(x->x.blockPosition()).findFirst().orElse(null);
   if(crewAt!=null&&crewAt.equals(lastCrew))still++;else still=0;lastCrew=crewAt;
   if(dumps==0||still>=6&&still%6==0&&dumps<40){dumps++;
    if(dumps==1)LogUtils.getLogger().info("ASTRA_RELOCATE buildings {}",e.settlement().buildings().stream().map(bb->bb.type()+"@"+BuildingPlacement.origin(e,bb).toShortString()+"/"+bb.rotation()).toList());
    for(var x:e.settlement().residents())if(x.alive()&&x.profession()==Profession.BUILDER&&l.getEntity(x.id()) instanceof ResidentEntity n){
     var at=n.blockPosition();var cells=new StringBuilder();for(int dy=-1;dy<=2;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var q=at.offset(dx,dy,dz);var st=l.getBlockState(q);if(!st.isAir())cells.append(dx).append(',').append(dy).append(',').append(dz).append('=').append(st).append(' ');}
     var lot=new StringBuilder();for(var bb:e.settlement().buildings()){var o=BuildingPlacement.origin(e,bb);var sz=BuildingPlacement.size(bb.type(),bb.rotation());if(at.getX()>=o.getX()-1&&at.getX()<=o.getX()+sz[0]&&at.getZ()>=o.getZ()-1&&at.getZ()<=o.getZ()+sz[1])lot.append(bb.type()).append('@').append(o.toShortString()).append(" turn ").append(bb.rotation()).append(' ');}
     var path=n.getNavigation().getPath();
     var nextOps=new StringBuilder();int shown=0;for(var raw:state.getList("ops",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.getBoolean("done"))continue;var c=t.copy();c.remove("before");c.remove("after");
      nextOps.append(BlockPos.of(t.getLong("pos")).toShortString()).append(' ').append(t.contains("stand")?"stand "+BlockPos.of(t.getLong("stand")).toShortString():"").append(' ').append(t.getCompound("before").getString("Name")).append("->").append(t.getCompound("after").getString("Name")).append(' ').append(c).append(" | ");if(++shown>=3)break;}
     var column=new StringBuilder();for(int dy=3;dy<=6;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var q=at.offset(dx,dy,dz);var st=l.getBlockState(q);if(!st.isAir())column.append(dx).append(',').append(dy).append(',').append(dz).append('=').append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath()).append(' ');}
     LogUtils.getLogger().info("ASTRA_RELOCATE builder next ops: {} above: {}",nextOps,column);
     LogUtils.getLogger().info("ASTRA_RELOCATE builder at {} home={} lot=[{}] nav={} target={} sleeping={} op={} stand={} walk={} cells: {}",String.format(Locale.ROOT,"%.2f %.2f %.2f",n.getX(),n.getY(),n.getZ()),x.home(),lot,path==null?"none":path.isDone()?"done":"path "+path.getNextNodeIndex()+"/"+path.getNodeCount()+" reach="+path.canReach(),path==null?"-":path.getTarget().toShortString(),n.isSleeping(),HallUpgradeGoal.lastOp,HallUpgradeGoal.lastStand,HallUpgradeGoal.lastWalk,cells);}}
   progress="phase="+phase+" ops="+d+"/"+ops.size()+" skipped="+sk+" funded="+state.getBoolean("funded")+" moved="+state.getBoolean("moved")+" cargo="+state.getList("cargo",Tag.TAG_COMPOUND).size()+" crew=["+crew+"] drops="+dropped;
   workPhase=phase;return;}
  boolean there=BuildingPlacement.origin(e,b).equals(site)&&b.rotation()==turns;
  progress="no move pending; building at "+BuildingPlacement.origin(e,b).toShortString()+" turn "+b.rotation()+(pending?" other project "+state.getString("design"):"");
  if(!there)return;
  // The move is over: the same record at the new place, its level and core, the chest slot for slot, and a save/load round trip.
  var chest=LogisticsRoutes.chest(l,e,b);boolean same=chest!=null;
  if(chest!=null)for(int i=0;i<chest.getContainerSize();i++){var want=KNOWN.getOrDefault(i,ItemStack.EMPTY);if(!ItemStack.isSameItemSameTags(want,chest.getItem(i))||want.getCount()!=chest.getItem(i).getCount())same=false;}
  int levelAfter=BuildingTiers.level(l,e,b),gradeAfter=Relocations.grade(l,e,b),itemsAfter=count(chest);
  var reloaded=SettlementData.load(SettlementData.get(s).save(new CompoundTag()));var copy=reloaded.entry(village);
  boolean reload=copy!=null&&copy.settlement().buildings().stream().anyMatch(x->x.id().equals(building)&&x.x()==b.x()&&x.y()==b.y()&&x.z()==b.z()&&x.rotation()==b.rotation()&&x.level()==b.level());
  int lost=Math.max(0,itemsBefore-itemsAfter);
  result="building="+building+" ops="+done+"/"+total+" skipped="+skipped+" moved=true level="+levelBefore+"->"+levelAfter+" grade="+gradeBefore+"->"+gradeAfter
   +" chest="+(same?"same":"changed")+" items="+itemsBefore+"/"+itemsAfter+" lost="+lost+" dropped="+dropped+" home_evictions="+evictions+" reload="+reload+" from="+from.toShortString()+" to="+site.toShortString()+" turn="+rotation+"->"+turns;
  finished=true;verified=same&&lost==0&&dropped==0&&evictions==0&&reload&&levelBefore==levelAfter&&gradeBefore==gradeAfter&&done==total;
 }catch(Exception ex){failure=ex.toString();LogUtils.getLogger().error("ASTRA_RELOCATE sample",ex);}});}
 private static volatile int workPhase=-1;
 private static void look(Minecraft mc,BlockPos eye,BlockPos at){mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
  double dx=at.getX()-eye.getX(),dz=at.getZ()-eye.getZ(),dy=at.getY()-eye.getY()-1.6;
  p.teleportTo(p.serverLevel(),eye.getX()+.5,eye.getY(),eye.getZ()+.5,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));});}
 private static boolean parked;private static int dumps,still;private static BlockPos lastCrew;
 /** The pointer leaves the map before a capture, so no hover card covers the picture. */
 private static void park(Minecraft mc){org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),4,4);}
 private static boolean click(AtlasScreen screen,double x,double y){if(!screen.mouseClicked(x,y,0))return false;screen.mouseReleased(x,y,0);return true;}
 private static int groundY(Minecraft mc,int x,int z){var l=mc.getSingleplayerServer().overworld();return l.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;}
 /** The map's view over both lots, so the old building and the new place are in one picture. */
 private static void frame(AtlasScreen screen){var a=from;var b=site==null?candidates.get(candidate):site;var mid=new BlockPos((a.getX()+b.getX())/2+4,Math.max(a.getY(),b.getY()),(a.getZ()+b.getZ())/2+4);
  double dist=Math.sqrt(a.distSqr(b));screen.overview(mid,(float)Math.max(2.5,Math.min(9,(screen.width-200)/(dist+28))));}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>60000&&phase<9)throw new IllegalStateException("Relocate timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){mc.setScreen(new AtlasScreen());phase=2;ticks=0;}
  else if(phase==2&&mc.screen instanceof AtlasScreen screen&&ticks>60){
   // The mayor picks the building on the map by a click on its roof, as a player would.
   if(!AtlasScreen.live().contains("relocation")){if(ticks>600)throw new IllegalStateException("The atlas page carries no relocation");return;}
   frame(screen);var size=BuildingPlacement.size(AtlasScreen.live().getList("buildings",Tag.TAG_COMPOUND).stream().map(t->(CompoundTag)t).filter(t->t.hasUUID("id")&&t.getUUID("id").equals(building)).map(t->t.getString("type")).findFirst().orElse("home"),rotation);
   for(int dx=1;dx<size[0]-1&&(AtlasScreen.card()==null||!building.equals(AtlasScreen.card().getUUID("id")));dx++)for(int dz=1;dz<size[1]-1;dz++){
    int x=from.getX()+dx,z=from.getZ()+dz;var spot=screen.screenOf(new BlockPos(x,groundY(mc,x,z),z));
    if(!screen.pickable(spot[0],spot[1]))continue;click(screen,spot[0],spot[1]);var card=AtlasScreen.card();if(card!=null&&building.equals(card.getUUID("id")))break;}
   var card=AtlasScreen.card();if(card==null||!building.equals(card.getUUID("id"))){if(ticks>600)throw new IllegalStateException("The building's card could not be picked on the map");return;}
   if(!AtlasScreen.moveRefusal(card).isEmpty())throw new IllegalStateException("The card says the building cannot move: "+AtlasScreen.moveRefusal(card));
   var move=screen.buttonCenter("move");var widget=screen.widget("move");
   if(move==null||widget==null||!widget.visible||!widget.active||!click(screen,move[0],move[1])||AtlasScreen.tool()!=AtlasScreen.MOVE)throw new IllegalStateException("«Move» did not start the move tool: tool="+AtlasScreen.tool());
   LogUtils.getLogger().info("ASTRA_RELOCATE the mayor picked {} on the map and pressed «Move»",card.getString("type"));phase=3;ticks=0;}
  else if(phase==3&&mc.screen instanceof AtlasScreen screen&&ticks>20){
   // A click on the free spot, then R once: the building is turned a quarter.
   var spot=candidates.get(candidate);frame(screen);var at=screen.screenOf(spot);
   if(!screen.pickable(at[0],at[1])){candidate++;if(candidate>=candidates.size())throw new IllegalStateException("No free spot is pickable on the map");return;}
   click(screen,at[0],at[1]);if(AtlasScreen.moveTarget()==null)throw new IllegalStateException("The click chose no new place");
   if(AtlasScreen.turns()!=turns)screen.keyPressed(82,0,0);
   if(AtlasScreen.turns()!=turns)throw new IllegalStateException("R did not turn the building: "+AtlasScreen.turns());
   LogUtils.getLogger().info("ASTRA_RELOCATE clicked {} for spot {}, target {} turn {}",at[0]+","+at[1],spot.toShortString(),AtlasScreen.moveTarget().toShortString(),AtlasScreen.turns());phase=4;ticks=0;}
  else if(phase==4&&mc.screen instanceof AtlasScreen screen&&ticks%10==0){
   var p=AtlasScreen.preview();
   if(!p.contains("reason")){if(ticks>600)throw new IllegalStateException("No dry run came back");return;}
   if(!p.getBoolean("ok")||p.getInt("variant")!=turns){
    if(ticks<200)return;
    LogUtils.getLogger().info("ASTRA_RELOCATE dry run at {} refused: {}",AtlasScreen.moveTarget().toShortString(),p.getString("reason"));
    if(++candidate>=candidates.size())throw new IllegalStateException("No candidate spot gave a good dry run: "+p.getString("reason"));phase=3;ticks=0;return;}
   if(p.getInt("reused")<=0||p.getInt("dismantle")<=0||p.getInt("place")<=0||p.getLongArray("oldCells").length==0||p.getIntArray("future").length==0)throw new IllegalStateException("The dry run lacks its figures: "+p);
   site=AtlasScreen.moveTarget();park(mc);
   if(ticks<60)return;
   capture(mc,"plan");
   LogUtils.getLogger().info("ASTRA_RELOCATE dry run: dismantle={} returned={} place={} reused={} items={} shortage={} containers={} packed={} seconds={} downtime={}",
    p.getInt("dismantle"),p.getInt("returned"),p.getInt("place"),p.getInt("reused"),p.getInt("items"),p.getInt("shortage"),p.getInt("containers"),p.getInt("packed"),p.getInt("seconds"),p.getInt("downtime"));
   // The hall gets only what the move cannot take from the building itself.
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var hall=LogisticsRoutes.chest(l,e,Workshops.hall(e));var b=moved(e);
    var plan=Relocations.plan(l,e,b,site,turns);if(hall!=null&&plan.ok())LogUtils.getLogger().info("ASTRA_RELOCATE hall stocked with {} items for the net cost {}",topUp(hall,items(plan.state().getCompound("cost"),new CompoundTag())),plan.state().getCompound("cost"));});
   phase=5;ticks=0;}
  else if(phase==5&&mc.screen instanceof AtlasScreen screen&&ticks>40){
   var order=screen.buttonCenter("order");var w=screen.widget("order");
   if(order==null||w==null||!w.visible||!w.active)throw new IllegalStateException("«Order the move» is not offered");
   click(screen,order[0],order[1]);phase=6;ticks=0;}
  else if(phase==6&&ticks%10==0){
   var out=AtlasScreen.outcome();if(!out.contains("ordered")){if(ticks>600)throw new IllegalStateException("The order got no answer");return;}
   if(!out.getBoolean("ordered"))throw new IllegalStateException("The move was refused: "+out.getString("reason"));
   if(AtlasScreen.tool()!=AtlasScreen.VIEW)throw new IllegalStateException("The map stayed in the move tool after the order");
   LogUtils.getLogger().info("ASTRA_RELOCATE the mayor ordered the move to {} turn {}",site.toShortString(),turns);mc.setScreen(null);phase=7;ticks=0;}
  else if(phase==7&&ticks%100==0){sample(mc);LogUtils.getLogger().info("ASTRA_RELOCATE progress {}",progress);
   // The camera stands to the side of the line between both lots, once the copy is well under way and the old lot is nearly bare.
   if(workPhase==2&&!workShot&&done>=total*7/10){workShot=true;var a=from;var b=site;double mx=(a.getX()+b.getX())/2D+4,mz=(a.getZ()+b.getZ())/2D+4,dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),len=Math.max(1,Math.sqrt(dx*dx+dz*dz));
    look(mc,new BlockPos((int)Math.round(mx-dz/len*24),Math.max(a.getY(),b.getY())+14,(int)Math.round(mz+dx/len*24)),new BlockPos((int)mx,Math.max(a.getY(),b.getY())+2,(int)mz));phase=70;ticks=0;}
   if(finished){phase=8;ticks=0;}}
  else if(phase==70&&ticks==60){capture(mc,"work");phase=7;ticks=0;}
  else if(phase==8&&ticks==20){mc.setScreen(new AtlasScreen());}
  else if(phase==8&&ticks>100&&mc.screen instanceof AtlasScreen screen){
   // The card is picked again at its new place.
   frame(screen);
   for(int dx=1;dx<6&&(AtlasScreen.card()==null||!building.equals(AtlasScreen.card().getUUID("id")));dx++)for(int dz=1;dz<6;dz++){int x=site.getX()+dx,z=site.getZ()+dz;var spot=screen.screenOf(new BlockPos(x,groundY(mc,x,z),z));
    if(screen.pickable(spot[0],spot[1])){click(screen,spot[0],spot[1]);var card=AtlasScreen.card();if(card!=null&&building.equals(card.getUUID("id")))break;}}
   var card=AtlasScreen.card();boolean picked=card!=null&&building.equals(card.getUUID("id"))&&BlockPos.of(card.getLong("pos")).equals(site);
   if(!picked&&ticks<400)return;
   if(!parked){parked=true;park(mc);return;}
   capture(mc,"done");
   if(!picked)throw new IllegalStateException("The card was not found at the new place");
   if(!verified)throw new IllegalStateException("The move ended wrong: "+result);
   LogUtils.getLogger().info("ASTRA_RELOCATE VERIFIED {}",result);mc.setScreen(null);mc.stop();phase=9;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_RELOCATE FAILED",ex);mc.stop();}}
}
