package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-028: real shovel mark and palette order, then the ordinary builder funds and builds a house. Fixture grants office and stock only. */
final class BuildingOrderProbe {
 static final BlockPos SITE=new BlockPos(-15,-61,-8);
 /** Estimate stacks that do not fit the hall chest are topped up as the builder withdraws, like a player or porter refilling it. */
 private static final java.util.List<ItemStack> PENDING=new java.util.concurrent.CopyOnWriteArrayList<>();
 private static void topUp(net.minecraft.server.MinecraftServer s){if(PENDING.isEmpty())return;var chest=(Container)s.overworld().getBlockEntity(entry(s).center().offset(1,1,4));for(int i=0;i<chest.getContainerSize()&&!PENDING.isEmpty();i++)if(chest.getItem(i).isEmpty())chest.setItem(i,PENDING.remove(0));}
 private static int phase,ticks,lastIndex=-1,stalled;private static volatile String failure;private static volatile boolean reloaded,queued,built;private static volatile String progress="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.buildingOrderSmoke");}
 static String design(){return System.getProperty("villageastra.buildDesign","home");}
 static int skip(){return Integer.getInteger("villageastra.buildSkip",0);}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-build-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_BUILD_ORDER screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** Registered house, usable housing, protection, no temporary ladder left and tools back in stock. */
 private static String verify(net.minecraft.server.MinecraftServer s){
  var l=s.overworld();var e=entry(s);var state=HallUpgradeGoal.inspect(l,e.settlement().id());var id=BuildingOrders.buildingId(state);
  if(!state.getBoolean("complete"))return "project not complete";
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);
  if(b==null||!b.type().equals(design())||!e.center().offset(b.x(),b.y(),b.z()).equals(SITE))return "building not registered at site";
  if(e.settlement().homes().stream().noneMatch(h->h.id().equals(id)&&h.capacity()==BuildingOrders.capacity(design())&&h.usable()))return "home not registered";
  if(BuildingIntegrity.home(l,SITE,design())!=BuildingIntegrity.Result.USABLE)return "house integrity "+BuildingIntegrity.home(l,SITE,design());
  for(int x=-2;x<16;x++)for(int z=-2;z<14;z++)for(int y=0;y<18;y++)if(l.getBlockState(SITE.offset(x,y,z)).is(org.villageastra.VillageAstra.TIMBER_SCAFFOLD.get()))return "scaffold left at "+x+","+y+","+z;
  if(!OwnershipEvents.disallowedPlacement(l,SITE.offset(3,1,3)))return "house not protected";
  var hatch=BlockPos.of(state.getLong("hatch"));for(int y=1;y<=4;y++)if(l.getBlockState(hatch.above(y)).is(net.minecraft.world.level.block.Blocks.LADDER))return "ladder left at "+y;
  for(var raw:state.getList("cargo",10))if(!ItemStack.of((net.minecraft.nbt.CompoundTag)raw).isEmpty())return "cargo not returned";
  return "";
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);var view=ConstructionOverlay.snapshot();boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");
  // A large house is hundreds of blocks of real work: the budget follows the design, not a fixed minute count.
  if(++ticks>(design().equals("home")?24000:120000))throw new IllegalStateException("Building order timeout phase="+phase+" "+progress);
  int x=(mc.getWindow().getGuiScaledWidth()-MayorSurveyScreen.WIDTH)/2,y=(mc.getWindow().getGuiScaledHeight()-MayorSurveyScreen.HEIGHT)/2;
  if(reload&&phase==0){phase=20;mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();String problem=verify(s);if(!problem.isEmpty())throw new IllegalStateException(problem);if(HallUpgradeGoal.pending(s.overworld(),entry(s).settlement().id()))throw new IllegalStateException("Completed project reopened");reloaded=true;}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==20&&reloaded){LogUtils.getLogger().info("ASTRA_BUILD_ORDER VERIFIED registered home, usable housing, protection and returned ladder survive reload; reload=true");mc.stop();phase=21;}
  else if(phase==0){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
   var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
   e.settlement().appointPlayerMayor(p.getUUID());
   // AD-060: residents sleep at night; a construction run that lasts longer than a day is kept in daylight so a sleeping builder is not a stall.
   l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);
   // Frozen residents are parked north of the village so they cannot stand in the builder's route.
   int parked=0;for(var r:e.settlement().residents())if(r.profession()!=org.villageastra.domain.Profession.BUILDER){var npc=(ResidentEntity)l.getEntity(r.id());npc.setNoAi(true);npc.teleportTo(30.5+2*parked++,-60,-20.5);}
   var survey=BuildingOrders.survey(l,e,design(),0,SITE);if(!survey.ok())throw new IllegalStateException("Fixture site not orderable: "+survey.reason()+" "+survey.conflicts());
   var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int slot=8;var cost=survey.state().getCompound("cost");
   for(var key:cost.getAllKeys()){int left=cost.getInt(key);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));while(left>0){int n=Math.min(item.getMaxStackSize(),left);if(slot>=chest.getContainerSize())PENDING.add(new ItemStack(item,n));else chest.setItem(slot++,new ItemStack(item,n));left-=n;}}
   SettlementData.get(s).setDirty();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(org.villageastra.VillageAstra.MAYOR_SHOVEL.get()));p.teleportTo(l,SITE.getX()+3.5,SITE.getY()+1,SITE.getZ()-4.5,0,40);
   LogUtils.getLogger().info("ASTRA_BUILD_ORDER fixture: mayor office and exact estimate stock ({} item types) in hall chest",cost.getAllKeys().size());
  }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ticks>40){mc.gameMode.startDestroyBlock(SITE,Direction.UP);phase=2;ticks=0;}
  else if(phase==2&&ticks>30&&mc.screen instanceof MayorSurveyScreen&&!view.getString("design").equals(design())){var screen=mc.screen;int index=BuildingBlueprints.designs().stream().map(BuildingBlueprints.Design::id).toList().indexOf(design());if(index<0||index>=8)throw new IllegalStateException("Design not on first palette page");int bx=x+16+(index%2)*153+10,by=y+51+(index/2)*25+6;if(!screen.mouseClicked(bx,by,0))throw new IllegalStateException("Design button missed");screen.mouseReleased(bx,by,0);ticks=0;}
  else if(phase==2&&ticks>60&&mc.screen instanceof MayorSurveyScreen&&BuildingOrders.WITHHELD.contains(design())&&view.getString("design").equals(design())){
   // A withheld design is refused by the palette on purpose (AD-046): to test whether a live builder now finishes it, the fixture queues the same server survey.
   mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();String reason=BuildingOrders.approve(s.overworld(),entry(s),design(),0,SITE);
    if(!reason.isEmpty())failure="Withheld design not queued: "+reason;else LogUtils.getLogger().info("ASTRA_BUILD_ORDER withheld design {} queued by the fixture, the palette refuses it",design());});
   mc.setScreen(null);phase=3;ticks=0;}
  else if(phase==2&&ticks>30&&mc.screen instanceof MayorSurveyScreen&&view.getBoolean("canOrder")){
   if(!BlockPos.of(view.getLong("first")).equals(SITE))throw new IllegalStateException("Marked a different origin "+BlockPos.of(view.getLong("first")));
   capture(mc,"palette");var screen=mc.screen;if(!screen.mouseClicked(x+30,y+205,0))throw new IllegalStateException("Order button missed");screen.mouseReleased(x+30,y+205,0);phase=3;ticks=0;
  }
  else if(phase==2&&ticks>30&&mc.screen instanceof MayorSurveyScreen&&!view.getBoolean("canOrder")&&ticks>200)throw new IllegalStateException("Palette cannot order: "+view.getString("orderReason")+" conflicts="+view.getInt("conflicts"));
  else if(phase==3&&ticks%20==0){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();if(HallUpgradeGoal.pending(s.overworld(),entry(s).settlement().id()))queued=true;});
   if(queued&&skip()>0){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var e=entry(s);int applied=HallUpgradeGoal.advanceForProbe(s.overworld(),e.settlement().id(),skip());LogUtils.getLogger().info("ASTRA_BUILD_ORDER fixture skipped {} operations of the queued project",applied);});}
   if(queued){LogUtils.getLogger().info("ASTRA_BUILD_ORDER ordered through the palette; builder must fund and build");mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),SITE.getX()-9.5,SITE.getY()+7,SITE.getZ()-7.5,-45,30);});phase=4;ticks=0;}
   else if(ticks>400)throw new IllegalStateException("Order was not queued");}
  else if(phase==4&&ticks%100==0){mc.getSingleplayerServer().execute(()->{try{
   var s=mc.getSingleplayerServer();topUp(s);var l=s.overworld();var e=entry(s);var state=HallUpgradeGoal.inspect(l,e.settlement().id());int index=state.getInt("index"),total=state.getList("ops",10).size();
   var worker=state.hasUUID("worker")&&l.getEntity(state.getUUID("worker")) instanceof ResidentEntity r?r:null;
   var currentOp=index<total?state.getList("ops",10).getCompound(index):new net.minecraft.nbt.CompoundTag();progress="op="+(index<total?BlockPos.of(currentOp.getLong("pos")).toShortString()+">"+currentOp.getCompound("after").getString("Name")+(currentOp.contains("stand")?" stand="+BlockPos.of(currentOp.getLong("stand")).toShortString():" nostand"):"-")+" index="+index+"/"+total+" funded="+state.getBoolean("funded")+" withdrawals="+state.getInt("withdrawals")+" deferrals="+state.getInt("deferrals")+" done="+state.getInt("progress")+" status="+(worker==null?"none":worker.workStatus())+" worker="+(worker==null?"none":worker.blockPosition().toShortString()+" goals="+worker.runningGoals()+" noAi="+worker.isNoAi()+" nav="+(worker.getNavigation().getPath()==null?"none":worker.getNavigation().getPath().getTarget().toShortString()+"/"+worker.getNavigation().getPath().canReach())+" exact="+String.format(java.util.Locale.ROOT,"%.2f,%.2f,%.2f",worker.getX(),worker.getY(),worker.getZ())+" ground="+worker.onGround()+" hcol="+worker.horizontalCollision+" speed="+worker.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)+" node="+(worker.getNavigation().getPath()==null?"none":worker.getNavigation().getPath().getNextNodeIndex()+"/"+worker.getNavigation().getPath().getNodeCount()+"@"+(worker.getNavigation().getPath().isDone()?"done":worker.getNavigation().getPath().getNextNodePos().toShortString()))+" custody="+CargoCustody.pending(s,worker.getUUID())+" mayWork="+CargoCustody.mayStartWork(worker)+" work["+HallUpgradeGoal.lastOp+"] stand["+HallUpgradeGoal.lastStand+"]");
   LogUtils.getLogger().info("ASTRA_BUILD_ORDER progress {} gameTime={} paused={} screen={}",progress,l.getGameTime(),mc.isPaused(),mc.screen==null?"none":mc.screen.getClass().getSimpleName());
   // Only finished operations count as progress: deferrals alone must not keep a stuck site alive.
   int marker=state.getBoolean("funded")?state.getInt("progress"):-1000+state.getInt("withdrawals");if(marker==lastIndex)stalled+=100;else{lastIndex=marker;stalled=0;}
   if(stalled>=2400&&worker!=null){var around=new StringBuilder();var at=worker.blockPosition();for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var q=at.offset(dx,dy,dz);var st=l.getBlockState(q);if(!st.isAir())around.append(q.toShortString()).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath()).append(' ');}
    var path=worker.getNavigation().getPath();var nodes=new StringBuilder();if(path!=null)for(int i=0;i<path.getNodeCount();i++)nodes.append(path.getNode(i).asBlockPos().toShortString()).append(';');
    LogUtils.getLogger().info("ASTRA_BUILD_ORDER stall blocks {} path {}",around,nodes);}
   // A large site walks a lap around the house between operations; only areal stop counts as a stall.
   int patience=design().equals("home")?2400:4800;
   if(stalled>=patience)failure="Builder stalled for "+(patience/1200)+" minutes: "+progress;
   // A dead builder is a failure of the mod, not a slow site: the site must never bury or drown its own worker.
   if(worker==null&&state.hasUUID("worker")&&state.getInt("progress")>0)failure="Builder is gone from the site: "+progress;
   if(state.getBoolean("complete")){String problem=verify(s);if(!problem.isEmpty())failure=problem;else built=true;}
  }catch(Exception ex){failure=ex.toString();}});
   if(ticks==2000)capture(mc,"progress");
   if(built){capture(mc,"house");LogUtils.getLogger().info("ASTRA_BUILD_ORDER VERIFIED design={} palette order; builder funded from hall chest, built it from ground and scaffold columns, returned scaffold, registered usable home and protection; reload=false",design());mc.stop();phase=5;}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_BUILD_ORDER FAILED",ex);mc.stop();}}
}
