package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-150 in the real client: the poachers' camp really stands in the world, the greys really sit behind its bars, meat really brings one
 *  out after the player, and the kennel really takes them in. The band and the broken bar are the harness's doing (a probe fights
 *  nobody); everything else goes through the mod's own paths — the card, the interact event, {@link WolfRescue#step}.
 *  Frames: the camp with its cages, the greys following the player, the kennel with the village's new wolves. */
final class WolfRescueProbe {
 private static int phase,ticks;private static volatile String failure;private static volatile boolean ready;
 private static volatile UUID quest;private static volatile int cages,freed,enlisted,target;
 private static final List<UUID> GREYS=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.wolfRescueProbe");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).getParent().getFileName()+"-wolfrescue-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WOLFRESCUE screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static Settlement.Building building(SettlementData.Entry e,UUID id){return e.settlement().buildings().stream().filter(b->b.id().equals(id)).findFirst().orElseThrow();}
 private static void lay(ServerLevel l,SettlementData.Entry e,Settlement.Building b,String design){
  for(var cell:BuildingPlacement.layout(e,b,design).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);}
 private static UUID yardId,kennelId;

 /** A yard IV with its kennel, and the camp of the card standing on cleared ground away from the village. */
 private static void fixture(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var st=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
  for(int n=st.civilization().level()+1;n<=4;n++)st.civilization().completedHallUpgrade(n);
  var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-rescue-yard"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
  for(int n=2;n<=4;n++)st.raiseBuildingLevel(yard.id(),n);yard=building(e,yard.id());
  var o=BuildingPlacement.origin(e,yard);
  for(int x=-10;x<27;x++)for(int z=-8;z<31;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);
   for(int up=1;up<5;up++)l.setBlock(g.above(up),Blocks.AIR.defaultBlockState(),3);}
  lay(l,e,yard,BuildingTiers.layoutId("livestock",4));
  var kind=Annexes.kind(VillageWolves.TYPE);var ko=Annexes.origin(e,yard,kind).subtract(e.center());
  var kennel=new Settlement.Building(Settlement.childId(st.id(),"building/probe-rescue-kennel"),VillageWolves.TYPE,ko.getX(),ko.getY(),ko.getZ(),yard.rotation());
  st.addBuilding(kennel);st.linkAnnex(kennel.id(),yard.id());kennelId=kennel.id();
  lay(l,e,kennel,VillageWolves.TYPE);
  // Somebody of the yard has to ask for the greys: the keeper of this village, or the first grown resident it has.
  if(st.residents().stream().noneMatch(r->r.alive()&&r.profession()==Profession.LIVESTOCK_FARMER)){
   var r=st.residents().stream().filter(x->x.alive()&&x.life()==Resident.Life.ADULT&&x.profession()==null).findFirst().orElse(null);
   if(r!=null)st.assign(r.id(),Profession.LIVESTOCK_FARMER,yardId);}
  SettlementData.get(s).setDirty();
  long now=SettlementData.get(s).clock().ticks();
  var q=WolfRescue.post(l,e,now);if(q==null)throw new IllegalStateException("The kennel asked for no greys");
  quest=q.getUUID("id");target=q.getInt("target");
  // The camp stands on ground of its own, cleared as a probe may: what is being looked at is the camp, not the landscape.
  var at=e.center().offset(96,0,96);
  var ground=new BlockPos(at.getX(),l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ()),at.getZ());
  for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++){var g=ground.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);
   for(int up=1;up<6;up++)l.setBlock(g.above(up),Blocks.AIR.defaultBlockState(),3);}
  if(!Chains.materialize(l,e,quest,ground,now))throw new IllegalStateException("The camp was not built");
  org.villageastra.server.PropertyLedger.get(s).gift(st.id(),p.getUUID(),Quests.trust(WolfRescue.CAGES));
  var taken=Quests.take(p,st.id(),quest);if(!taken.equals("ok"))throw new IllegalStateException("The card was not taken: "+taken);
  var site=QuestSites.site(l,Quests.root(Quests.quest(l,st.id(),quest)));
  cages=WolfSites.cages(site).size();
  var camp=QuestSites.origin(site);
  p.teleportTo(l,camp.getX()+12.5,camp.getY()+4,camp.getZ()+.5,90,18);
  ready=true;
  LogUtils.getLogger().info("ASTRA_WOLFRESCUE camp at {} with {} cages, asked for {}",camp.toShortString(),cages,target);
 }catch(Exception ex){failure=ex.toString();}});}

 /** The harness puts the catchers down and breaks one bar of every cage — a probe fights nobody and mines nothing. */
 private static void openCages(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var q=Quests.quest(l,e.settlement().id(),quest);
  var site=QuestSites.site(l,Quests.root(q));var camp=QuestSites.origin(site);
  for(var mob:l.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(camp).inflate(24),m->m.getTags().contains(QuestSites.MOB)))mob.discard();
  for(var middle:WolfSites.cages(site))l.setBlock(middle.offset(1,0,0),Blocks.AIR.defaultBlockState(),3);
  GREYS.clear();for(var wolf:WolfRescue.free(l,q))GREYS.add(wolf.getUUID());
  freed=GREYS.size();
 }catch(Exception ex){failure=ex.toString();}});}
 /** Raw meat from the player's own hand, through the mod's own event — the same path a right click takes. */
 private static void feed(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var p=s.getPlayerList().getPlayers().get(0);
  // The meat has to be in the hand the click uses, whatever the smoke world left the player holding.
  p.getInventory().selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BEEF,GREYS.size()+2));
  int following=0;
  for(var id:GREYS){if(!(l.getEntity(id) instanceof Wolf wolf))continue;
   p.teleportTo(l,wolf.getX(),wolf.getY()+1,wolf.getZ()+4.5,180,20);
   MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,wolf));
   if(p.getUUID().equals(wolf.getOwnerUUID()))following++;}
  if(following<GREYS.size())throw new IllegalStateException("Only "+following+" of "+GREYS.size()+" greys took the meat (hand="
   +p.getItemInHand(InteractionHand.MAIN_HAND)+", card="+Quests.quest(l,entry(s).settlement().id(),quest).getString("state")+")");
 }catch(Exception ex){failure=ex.toString();}});}
 /** Home to the kennel: the greys walk behind their player, and the yard takes them in. */
 private static void home(Minecraft mc){var s=mc.getSingleplayerServer();s.execute(()->{try{
  var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);var kennel=building(e,kennelId);
  var at=LogisticsRoutes.position(e,kennel);
  p.teleportTo(l,at.getX()+8.5,at.getY()+4,at.getZ()+8.5,135,20);
  for(var id:GREYS)if(l.getEntity(id) instanceof Wolf wolf)wolf.teleportTo(at.getX()+1.5+l.random.nextDouble(),at.getY()+1,at.getZ()+1.5+l.random.nextDouble());
  WolfRescue.step(l,e,Quests.quest(l,e.settlement().id(),quest),p,SettlementData.get(s).clock().ticks());
  var pack=VillageWolves.wolves(l,e);enlisted=(int)GREYS.stream().filter(pack::contains).count();
  for(var id:GREYS)if(l.getEntity(id) instanceof Wolf wolf&&(wolf.isTame()||wolf.getOwnerUUID()!=null))
   throw new IllegalStateException("A wolf of the kennel is still somebody's pet");
 }catch(Exception ex){failure=ex.toString();}});}

 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>6000)throw new IllegalStateException("Wolf rescue timeout phase="+phase);
  if(phase==0&&ticks>100&&mc.player!=null){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){capture(mc,"camp");phase=2;ticks=0;openCages(mc);}
  else if(phase==2&&ticks>40){feed(mc);phase=3;ticks=0;}
  else if(phase==3&&ticks>40){capture(mc,"freed");phase=4;ticks=0;home(mc);}
  else if(phase==4&&ticks>60){capture(mc,"kennel");
   if(cages<1||freed!=cages||enlisted!=freed||enlisted<target)
    throw new IllegalStateException("cages="+cages+" freed="+freed+" enlisted="+enlisted+" asked="+target);
   LogUtils.getLogger().info("ASTRA_WOLFRESCUE VERIFIED cages={} freed={} enlisted={} asked={}",cages,freed,enlisted,target);
   phase=5;mc.stop();}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WOLFRESCUE FAILED",ex);phase=99;mc.stop();}}
}
