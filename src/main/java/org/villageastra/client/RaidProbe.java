package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.Difficulty;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-064: a raid in the real client — the warning reaches the player's chat, real monsters come from one side, the residents run into the town hall,
 *  and when the raiders are dead the raid is recorded as beaten off and the player is told. Fixture: the test world is set to normal difficulty and night. */
final class RaidProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile int spawned,sheltering,raiders=-1;private static volatile boolean warned,ended,started;private static volatile String outcome="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.raidSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-raid-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RAID screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** The player's chat, as the client receives it: the raid messages are recognised by their translation keys. */
 private static void chat(ClientChatReceivedEvent event){
  if(event.getMessage().getContents() instanceof TranslatableContents t){
   if(t.getKey().startsWith("raid.villageastra.warning."))warned=true;
   if(t.getKey().startsWith("raid.villageastra.end."))ended=true;
  }
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Raid timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>80){phase=1;ticks=0;MinecraftForge.EVENT_BUS.addListener(RaidProbe::chat);
   server.execute(()->{try{var l=server.overworld();var e=entry(server);
    server.setDifficulty(Difficulty.NORMAL,true);l.setDayTime(18000);
    var p=server.getPlayerList().getPlayers().get(0);var c=e.center();p.teleportTo(l,c.getX()+.5,c.getY()+8,c.getZ()-10.5,0,35);
    String result=Raids.start(l,e,SettlementData.get(server).clock().ticks(),Raids.Kind.MONSTERS);
    if(!result.isEmpty())throw new IllegalStateException("The raid did not start: "+result);
    spawned=Raids.raiders(l,e.settlement().id()).size();started=true;
    LogUtils.getLogger().info("ASTRA_RAID fixture night, normal difficulty, raid of {} started",spawned);
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&started&&ticks%20==0){
   server.execute(()->{var l=server.overworld();var e=entry(server);int n=0;
    for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc&&npc.workStatus().equals("sheltering"))n++;
    sheltering=Math.max(sheltering,n);raiders=Raids.raiders(l,e.settlement().id()).size();});
   progress="warned="+warned+" sheltering="+sheltering+" raiders="+raiders;
   if(ticks%400==0)LogUtils.getLogger().info("ASTRA_RAID progress {}",progress);
   if(warned&&sheltering>0){capture(mc,"alarm");phase=2;ticks=0;}
   else if(ticks>3000)throw new IllegalStateException("No resident ran for cover: "+progress);}
  else if(phase==2&&ticks>40){
   // The guard's work, done by hand here: every raider falls.
   server.execute(()->{var l=server.overworld();var e=entry(server);
    for(var mob:Raids.raiders(l,e.settlement().id()))mob.kill();
    outcome=Raids.update(l,e,SettlementData.get(server).clock().ticks());});
   phase=3;ticks=0;}
  else if(phase==3&&ticks>40){
   if(!outcome.equals("repelled"))throw new IllegalStateException("The raid did not end as beaten off: "+outcome);
   if(!ended){if(ticks>400)throw new IllegalStateException("The end of the raid never reached the chat");return;}
   server.execute(()->{var l=server.overworld();var e=entry(server);var last=Raids.record(l,e.settlement().id()).getCompound("last");
    LogUtils.getLogger().info("ASTRA_RAID VERIFIED warning in chat={} spawned={} sheltering={} outcome={} recorded={} lost={} end in chat={}; reload=false",
     warned,spawned,sheltering,outcome,last.getString("outcome"),last.getInt("residentsLost"),ended);});
   phase=4;ticks=0;}
  else if(phase==4&&ticks>20){mc.stop();phase=5;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_RAID FAILED",ex);mc.stop();}}
}
