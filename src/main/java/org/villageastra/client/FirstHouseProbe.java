package org.villageastra.client;

import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.GameType;
import org.villageastra.server.*;
import org.villageastra.world.*;

/** A natural starter village, observed without material, recipe, worker or terrain grants. */
final class FirstHouseProbe {
 private static UUID village;private static Set<UUID> initial;
 private static long since,last=-1200,detail,trace;private static int frames;private static volatile boolean busy,done;
 private static volatile String failure;
 static boolean enabled(){return Boolean.getBoolean("villageastra.firstHouseSmoke");}
 static void tick(Minecraft mc){
  if(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);LogUtils.getLogger().info("ASTRA_FIRST_HOUSE resumed observation from pause screen");}
  if(failure!=null){LogUtils.getLogger().error("ASTRA_FIRST_HOUSE INCOMPLETE {}",failure);mc.stop();return;}
  if(done){LogUtils.getLogger().info("ASTRA_FIRST_HOUSE VERIFIED newHome=true geometry=true noPlayerSupplies=true");mc.stop();return;}
  if(++frames%10!=0||busy)return;busy=true;
  mc.getSingleplayerServer().execute(()->{try{sample(mc);}catch(Exception ex){failure=ex.toString();}finally{busy=false;}});
 }
 private static void sample(Minecraft mc){
  var server=mc.getSingleplayerServer();var l=server.overworld();var data=SettlementData.get(server);
  if(village==null){
   if(data.entries().isEmpty())return;var player=server.getPlayerList().getPlayers().get(0);
   var e=data.entries().stream().min(Comparator.comparingDouble(x->player.distanceToSqr(x.center().getCenter()))).orElseThrow();
   boolean resumed=Boolean.getBoolean("villageastra.reloadSmoke");
   initial=org.villageastra.domain.FirstHouseBaseline.buildings(e.settlement(),resumed,!BookResearch.completed(e,BookResearch.inspect(l,e)).isEmpty());
   village=e.settlement().id();since=data.clock().ticks();
   player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
   LogUtils.getLogger().info("ASTRA_FIRST_HOUSE START village={} center={} seed={} initialResidents=6 initialBuildings=7 observerOnly=true resumed={} observationStart={}",village,e.center(),l.getSeed(),resumed,since);
  }
  var e=data.entry(village);long elapsed=data.clock().ticks()-since;
  FirstHouseBuilderSurvey.sample(l,e);
  if(e.settlement().residents().stream().noneMatch(r->r.alive()))throw new IllegalStateException("Settlement lost all residents; preserve failed world and restart from an untouched baseline");
  if(e.settlement().residents().stream().anyMatch(r->!r.alive()&&r.missedMeals()>=Population.DEATH))throw new IllegalStateException("A resident starved; preserve failed world and restart from an untouched living checkpoint");
  if(elapsed-trace>=(elapsed<2400?40:1200)){trace=elapsed;for(var r:e.settlement().residents())if((r.profession()!=null)&&l.getEntity(r.id()) instanceof ResidentEntity npc){
   var path=npc.getNavigation().getPath();var nodes=new ArrayList<Object>();if(path!=null)for(int i=path.getNextNodeIndex();i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+4);i++)nodes.add(path.getNodePos(i));
   LogUtils.getLogger().info("ASTRA_FIRST_HOUSE trace worker={} tick={} npcTick={} entityTicking={} pos={} motion={} ground={} goals={} reached={} target={} next={}",r.profession(),elapsed,npc.tickCount,l.isPositionEntityTicking(npc.blockPosition()),npc.position(),npc.getDeltaMovement(),npc.onGround(),npc.runningGoals(),path!=null&&path.canReach(),path==null?null:path.getTarget(),nodes);
  }}
  if(elapsed-last<1200)return;last=elapsed;
  if(e.settlement().governance().playerMayor()!=null)throw new IllegalStateException("Player became mayor");
  var project=HallUpgradeGoal.exists(l,village)?HallUpgradeGoal.inspect(l,village):new net.minecraft.nbt.CompoundTag();var missing=ConstructionFunding.missing(project);int cargo=0;
  for(var raw:project.getList("cargo",Tag.TAG_COMPOUND))cargo+=net.minecraft.world.item.ItemStack.of((net.minecraft.nbt.CompoundTag)raw).getCount();
  var statuses=new TreeMap<String,String>();for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)statuses.put(r.profession()==null?r.id().toString():r.profession().name(),npc.workStatus());
  var job=Workshops.inspect(l,Workshops.hall(e).id());
  LogUtils.getLogger().info("ASTRA_FIRST_HOUSE progress active={} design={} funded={} cargo={} missing={} workshop={}/{} workers={}",elapsed,project.getString("design"),project.getBoolean("funded"),cargo,missing,job.getString("recipe"),job.getString("stage"),statuses);
  FirstHousePlantSurvey.sample(l,e);
  if(elapsed-detail>=12000){detail=elapsed;
   FirstHouseQuarrySurvey.sample(l,e);
   if(job.hasUUID("worker")&&l.getEntity(job.getUUID("worker")) instanceof ResidentEntity npc){var nav=npc.getNavigation();var path=nav.getPath();LogUtils.getLogger().info("ASTRA_FIRST_HOUSE stationWorker={} pos={} target={} reachable={} navigationDone={} pitExit={}",npc.getUUID(),npc.position(),nav.getTargetPos(),path==null?"none":path.canReach(),nav.isDone(),PitEscapeGoal.escape(npc));}
   LogUtils.getLogger().info("ASTRA_FIRST_HOUSE health={}",e.settlement().residents().stream().map(r->r.id()+":"+r.profession()+":"+r.life()+":sick="+r.sick()+":missed="+r.missedMeals()).toList());
   LogUtils.getLogger().info("ASTRA_FIRST_HOUSE workshopLabor={}/{} needs={}",job.getLong("labor"),job.getLong("needLabor"),job.getList("needs",Tag.TAG_COMPOUND));
   var stock=LogisticsRoutes.chest(l,e,Workshops.hall(e));var goods=new TreeMap<String,Integer>();if(stock!=null)for(int i=0;i<stock.getContainerSize();i++){var item=stock.getItem(i);if(!item.isEmpty())goods.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getCount(),Integer::sum);}LogUtils.getLogger().info("ASTRA_FIRST_HOUSE stock={}",goods);
   for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc&&NaturalSupplyGoal.eligible(r)){
    var workplace=e.settlement().workplace(r.id());if(workplace!=null&&workplace.type().equals("mine")){var work=MineWork.read(l,workplace);LogUtils.getLogger().info("ASTRA_FIRST_HOUSE mine step={} cell={} stage={} status={} stairStep={} stairPlaced={} cargo={} surfaceQuarry={}",work.getInt("step"),work.getInt("cell"),work.getString("stage"),work.getString("status"),work.contains("stairStep")?work.getInt("stairStep"):-1,work.getInt("stairPlaced"),work.getList("cargo",Tag.TAG_COMPOUND),SurfaceQuarry.mayStart(l,e,npc));}
    var trip=NaturalSupplyGoal.inspect(l,r.id());LogUtils.getLogger().info("ASTRA_FIRST_HOUSE supplier={} pos={} stage={} target={} stand={} delivered={} complete={} surveyCursor={}",r.profession(),npc.blockPosition(),trip.getString("stage"),net.minecraft.core.BlockPos.of(trip.getLong("target")),trip.contains("stand")?net.minecraft.core.BlockPos.of(trip.getLong("stand")):"legacy",trip.getInt("delivered"),trip.getBoolean("complete"),trip.getInt("surveyCursor"));
    if(NaturalSupplyGoal.active(trip))LogUtils.getLogger().info("ASTRA_FIRST_HOUSE route supplier={} exact={} ground={} pitExit={} goals={}",r.profession(),npc.position(),npc.onGround(),PitEscapeGoal.escape(npc),npc.runningGoals());
   }
  }
  for(var b:e.settlement().buildings())if(!initial.contains(b.id())&&e.settlement().homes().stream().anyMatch(h->h.id().equals(b.id())&&h.usable())){
   var layout=BuildingPlacement.layout(e,b,b.level()==1?b.type():b.type()+"@"+b.level());
   if(!layout.isEmpty()&&layout.entrySet().stream().filter(cell->!cell.getValue().isAir()).allMatch(cell->l.hasChunkAt(cell.getKey())&&BuildingRepairs.present(l.getBlockState(cell.getKey()),cell.getValue()))){done=true;return;}
  }
  if(elapsed>=Long.getLong("villageastra.firstHouseTicks",48000L))failure="First new home not completed at active="+elapsed+"; missing="+missing;
 }
}
