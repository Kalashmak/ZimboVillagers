package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-041: an expeditioner really walks out and reports a lead, the mod builds the camp there, and a companion escorted home completes the quest and waits for a bed. Fixture: office record, supplies and one educated arrival. */
final class CampProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile UUID quest,companion;private static volatile boolean ready,posted,following,arrived,housed;private static volatile EscortLeader leader;
 static boolean enabled(){return Boolean.getBoolean("villageastra.campSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-camp-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_CAMP screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>18000)throw new IllegalStateException("Camp timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    var office=new Settlement.Building(Settlement.childId(e.settlement().id(),"building/expedition-probe"),"expedition",-30,0,12);e.settlement().addBuilding(office);
    var chestPos=LogisticsRoutes.position(e,office);l.setBlock(chestPos.below(),net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
    var chest=(Container)l.getBlockEntity(chestPos);chest.setItem(0,new ItemStack(Items.PAPER,16));chest.setItem(1,new ItemStack(Items.BREAD,16));
    var home=new Settlement.Home(Settlement.childId(e.settlement().id(),"home/expedition-probe"),1,2,true);e.settlement().addHome(home);
    var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);e.settlement().admit(r,home.id());e.settlement().assign(r.id(),Profession.EXPEDITIONER,office.id());
    var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(e.settlement().id(),e.settlement().resident(r.id()));npc.moveTo(chestPos.getX()+1.5,chestPos.getY(),chestPos.getZ()+1.5,0,0);l.addFreshEntity(npc);
    for(var x:e.settlement().residents())if(!x.id().equals(r.id())&&l.getEntity(x.id()) instanceof ResidentEntity other)other.setNoAi(true);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();
    SettlementData.get(s).setDirty();LogUtils.getLogger().info("ASTRA_CAMP fixture: expedition office with 16 paper and 16 rations, one educated expeditioner");ready=true;}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ready&&ticks%40==0){mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);
    var scout=e.settlement().residents().stream().filter(x->x.profession()==Profession.EXPEDITIONER).findFirst().map(x->l.getEntity(x.id())).orElse(null);
    var leads=Expeditions.leads(l,e.settlement().id());
    if(scout instanceof ResidentEntity npc){var p=s.getPlayerList().getPlayers().get(0);if(p.distanceToSqr(npc)>900)p.teleportTo(l,npc.getX()+6,npc.getY()+6,npc.getZ()+6,0,30);}
    progress="leads="+leads.size()+" scout="+(scout instanceof ResidentEntity npc?npc.workStatus()+" at "+npc.blockPosition().toShortString():"none");
    if(!leads.isEmpty())posted=true;}catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_CAMP expedition {}",progress);
   if(posted){LogUtils.getLogger().info("ASTRA_CAMP the expeditioner reported a real lead: {}",progress);phase=2;ticks=0;}}
  else if(phase==2&&ticks%40==0){mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);
    if(quest==null){var made=Quests.postDistant(l,e,Quests.BRING,SettlementData.get(s).clock().ticks());if(made!=null)quest=made.getUUID("id");}
    if(quest==null){progress="waiting for the distant quest";return;}
    var q=Quests.quest(l,e.settlement().id(),quest);var camp=Camps.camp(l,q.getUUID("camp"));
    var traveller=l.getEntitiesOfClass(ResidentEntity.class,new net.minecraft.world.phys.AABB(BlockPos.of(camp.getLong("pos"))).inflate(12),x->Camps.companion(x,quest)).stream().findFirst().orElse(null);
    if(traveller==null){progress="no traveller at the camp";return;}companion=traveller.getUUID();
    var p=s.getPlayerList().getPlayers().get(0);if(Quests.take(p,e.settlement().id(),quest).equals("ok")||Quests.owned(p,quest)){var site=BlockPos.of(camp.getLong("pos"));p.teleportTo(l,site.getX()+1.5,site.getY()+1,site.getZ()+3.5,0,0);}
   }catch(Exception ex){failure=ex.toString();}});
   if(companion!=null){phase=3;ticks=0;}
   else if(ticks>1200)throw new IllegalStateException("No camp quest: "+progress);}
  else if(phase==3&&ticks>40){
   var npc=mc.level==null?null:java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(x->x.getUUID().equals(companion)).findFirst().orElse(null);
   if(npc==null){if(ticks>400)throw new IllegalStateException("The traveller is not visible");return;}
   capture(mc,"camp");mc.gameMode.interact(mc.player,npc,InteractionHand.MAIN_HAND);phase=4;ticks=0;}
  else if(phase==4&&ticks>20){mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();if(s.overworld().getEntity(companion) instanceof ResidentEntity npc&&npc.escortPlayer()!=null)following=true;});
   if(following){LogUtils.getLogger().info("ASTRA_CAMP the companion follows after a real right click");phase=5;ticks=0;leader=new EscortLeader(0);}
   else if(ticks>200)throw new IllegalStateException("The companion did not join");}
  else if(phase==5&&ticks%20==0){final int sample=ticks;mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var p=s.getPlayerList().getPlayers().get(0);
    if(!(l.getEntity(companion) instanceof ResidentEntity npc)){failure="The companion vanished";return;}
    double distance=Math.sqrt(p.distanceToSqr(e.center().getX()+.5,p.getY(),e.center().getZ()+.5));
    // AD-106: the player leads along the companion's own route home; the companion keeps up on its own feet.
    if(distance>6){var why=leader.step(l,p,npc,e.center(),sample);if(why!=null){failure=why;return;}}
    Quests.companions(l,e,SettlementData.get(s).clock().ticks());
    var q=Quests.quest(l,e.settlement().id(),quest);var guest=Camps.guest(l,companion);
    progress=(leader==null?"":leader.summary()+" ")+"quest="+(q==null?"none":q.getString("state")+" "+q.getInt("progress")+"/"+q.getInt("target"))+" companion="+npc.blockPosition().toShortString()+" state="+npc.escortState()+" distance="+String.format(java.util.Locale.ROOT,"%.1f",distance)+" guest="+(guest==null?"none":guest.getString("state"));
    if(q!=null&&q.getString("state").equals(Quests.DONE)&&guest!=null)arrived=true;
    if(guest!=null&&guest.getString("state").equals("waiting")&&arrived){var result=Camps.tickGuest(l,npc,SettlementData.get(s).clock().ticks());if(result.equals("housed"))housed=true;}
   }catch(Exception ex){failure=ex.toString();}});
   if(ticks%200==0)LogUtils.getLogger().info("ASTRA_CAMP escort {}",progress);
   if(ticks>12000&&!housed)throw new IllegalStateException("The walk home took over ten minutes: "+progress);
   if(housed){capture(mc,"arrived");LogUtils.getLogger().info("ASTRA_CAMP VERIFIED expedition lead, camp built by the mod, companion escorted home on foot, quest completed on arrival and the guest took a free bed; {}; reload=false",progress);mc.stop();phase=6;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CAMP FAILED",ex);mc.stop();}}
}
