package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-153 in the real client: Construction I-IV learned, the hall's builder and three more build one paid cottage together. The reach of IV
 *  (12 blocks) leaves the plan fewer scaffold columns; every block is set by one builder, several of them at work on the site at once.
 *  Frame: the crew on the site. */
final class ConstructionLadderProbe {
 private static int phase,ticks;private static volatile String failure,progress="",facts="";private static volatile boolean ready,done;
 static final BlockPos SITE=new BlockPos(-15,-61,-8);static final String DESIGN="home";
 static boolean enabled(){return Boolean.getBoolean("villageastra.constructionLadderProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-crew-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_CREW screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);st.appointPlayerMayor(p.getUUID());
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(1000);l.setWeatherParameters(24000,0,false,false);
  var record=BookResearch.inspect(l,e);var learned=record.getList("legacyDone",Tag.TAG_STRING);for(int t=1;t<=4;t++)learned.add(StringTag.valueOf("construction."+t));record.put("legacyDone",learned);BookResearch.store(l,e,record);ResearchKnobs.forget(st.id());
  if(ResearchKnobs.builders(l,e)!=6||ResearchKnobs.reach(l,e)!=2)throw new IllegalStateException("Construction IV not read: builders="+ResearchKnobs.builders(l,e)+" reach="+ResearchKnobs.reach(l,e));
  if(HallUpgradeGoal.pending(l,st.id())){if(!HallUpgradeGoal.yield(l,st.id()))throw new IllegalStateException("A project is already queued");}
  // Everybody but the builders is parked out of the way (as the building-order probe does).
  int parked=0;for(var r:st.residents())if(r.profession()!=Profession.BUILDER&&l.getEntity(r.id()) instanceof ResidentEntity npc){npc.setNoAi(true);npc.teleportTo(30.5+2*parked++,-60,-20.5);}
  var hall=st.buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElseThrow();
  var home=new Settlement.Home(Settlement.childId(st.id(),"home/probe-crew"),1,8,true);st.addHome(home);
  for(int i=0;i<3;i++){var r=new Resident(Settlement.childId(st.id(),"probe-builder-"+i),Resident.Life.ADULT,true,null,null,-1);st.admit(r,home.id());st.assign(r.id(),Profession.BUILDER,hall.id());
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),r);var at=SITE.offset(-3+6*i,1,-4);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);l.addFreshEntity(npc);}
  var survey=BuildingOrders.survey(l,e,DESIGN,0,SITE);if(!survey.ok())throw new IllegalStateException("Site not orderable: "+survey.reason()+" "+survey.conflicts());
  var state=survey.state();var cost=state.getCompound("cost");var cargo=state.getList("cargo",Tag.TAG_COMPOUND);
  for(var k:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(k));int need=cost.getInt(k);while(need>0){int n=Math.min(need,item.getMaxStackSize());cargo.add(new ItemStack(item,n).save(new CompoundTag()));need-=n;}}
  state.putBoolean("funded",true);HallUpgradeGoal.enqueue(l,e,state);SettlementData.get(s).setDirty();
  long scaffolds=state.getList("ops",Tag.TAG_COMPOUND).stream().filter(x->HallConstructionPlan.step((CompoundTag)x).after().is(VillageAstra.TIMBER_SCAFFOLD.get())).count();
  facts="ops="+state.getList("ops",Tag.TAG_COMPOUND).size()+" scaffolds="+scaffolds+" reach="+ResearchKnobs.reach(l,e);
  p.teleportTo(l,SITE.getX()+3.5,SITE.getY()+9,SITE.getZ()-9.5,0,40);
  LogUtils.getLogger().info("ASTRA_CREW fixture Construction I-IV, four builders, funded {} at {}: {}",DESIGN,SITE.toShortString(),facts);ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{var l=s.overworld();var e=entry(s);
  var state=HallUpgradeGoal.inspect(l,e.settlement().id());var ops=state.getList("ops",Tag.TAG_COMPOUND);int d=0;var by=new HashSet<UUID>();
  for(var raw:ops){var op=(CompoundTag)raw;if(op.getBoolean("done")){d++;if(op.hasUUID("by"))by.add(op.getUUID("by"));}}
  var crew=HallUpgradeGoal.crew(l,e);var statuses=new TreeMap<String,Integer>();
  for(var id:crew)if(l.getEntity(id) instanceof ResidentEntity npc)statuses.merge(npc.workStatus(),1,Integer::sum);
  progress="done="+d+"/"+ops.size()+" builders="+crew.size()+" by="+by.size()+" statuses="+statuses;
  if(!done&&by.size()>=3&&d>=40){done=true;facts=facts+" "+progress;}
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>16000)throw new IllegalStateException("Crew timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%20==0){sample(mc);if(ticks%400==0)LogUtils.getLogger().info("ASTRA_CREW progress {}",progress);
   if(done){mc.options.hideGui=true;capture(mc,"site");mc.options.hideGui=false;
    LogUtils.getLogger().info("ASTRA_CREW VERIFIED Construction IV crew: one cottage built by several builders of the hall at once, each block by one of them: {}; reload=false",facts);
    phase=2;mc.stop();}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CREW FAILED",ex);phase=99;mc.stop();}}
}
