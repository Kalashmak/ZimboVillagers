package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import org.villageastra.server.*;
import org.villageastra.world.*;
import org.villageastra.domain.*;
/** Real survival inventory packets, ordinary GUI buttons, then two scheduled elections and reload. */
final class ElectionProbe {
 /** AD-128: the mayor's threshold is ElectionRoll.MINIMUM (7680, two diamond sets as gifts; was 640 under AD-102). A donation counts at most 64 of each supply kind in the
  *  chest, so the fixture credits what the player earned before (EARNED) and the probe donates three stacks through the survival menu. */
 private static final int GIFT_STACKS=3,EARNED=(int)ElectionRoll.MINIMUM-GIFT_STACKS*64,GIFTS=(int)ElectionRoll.MINIMUM;private static int given;
 private static int phase,ticks;private static boolean stopped;private static volatile String failure;private static volatile boolean ready;
 static boolean enabled(){return Boolean.getBoolean("villageastra.electionSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){var e=SettlementData.get(server).entries().iterator().next();for(var r:e.settlement().residents())((ResidentEntity)server.overworld().getEntity(r.id())).setNoAi(true);((OwnedChestEntity)server.overworld().getBlockEntity(e.center().offset(1,1,4))).clearContent();}
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer());}catch(Exception ex){failure=ex.toString();}});}
 private static void open(Minecraft mc){OfficeProbes.open(mc,ConstructionScreen.ELECTIONS);}
 /** Clicks one of the elections tab's own buttons through the screen, as a player does; resign asks twice (confirm). */
 private static void click(Minecraft mc,boolean resign,int times){require(mc.screen instanceof ConstructionScreen,"Missing UI: "+mc.screen);var screen=(ConstructionScreen)mc.screen;screen.tick();var panel=(ElectionPanel)screen.panel(ConstructionScreen.ELECTIONS);var b=resign?panel.resignButton():panel.candidateButton();
  for(int i=0;i<times;i++){require(b.visible&&b.active,"Inactive office button: phase="+phase+" "+b.getMessage().getString()+" view="+ElectionPanel.view());OfficeProbes.clickOrFail(screen,b,b.getMessage().getString());}}
 private static void dueSoon(Minecraft mc){server(mc,s->{var d=SettlementData.get(s);var g=d.entries().iterator().next().settlement().governance();g.restoreElection(d.clock().ticks()+80,g.lastElection(),g.lastWinner(),g.lastScore(),g.lastReached());d.setDirty();});}
 private static void finish(Minecraft mc,boolean reload)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-election.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ELECTION screenshot {}",path);
  stopped=true;server(mc,s->{var e=SettlementData.get(s).entries().iterator().next();var p=s.getPlayerList().getPlayers().get(0);var g=e.settlement().governance();var a=PropertyLedger.get(s).roll(e.settlement().id()).account(p.getUUID());var chest=(OwnedChestEntity)s.overworld().getBlockEntity(e.center().offset(1,1,4));require(g.epoch()==3&&g.lastElection()>0&&g.nextElection()==g.lastElection()+ElectionRoll.PERIOD,"Both elections and resignation persist");require(g.playerMayor().equals(p.getUUID())&&a.candidate()&&a.gifts()==GIFTS&&a.theft()==0,"Saved elected office and physical donations");require(chest.countItem(Items.OAK_LOG)==64&&chest.countItem(Items.COBBLESTONE)==64&&chest.countItem(Items.WHEAT)==64,"Deposited stock remains exact");require(java.util.stream.IntStream.range(9,9+GIFT_STACKS).allMatch(k->p.getInventory().getItem(k).isEmpty()),"Player debit survives");require(e.settlement().residents().size()==6&&e.settlement().residents().stream().noneMatch(r->r.profession()==Profession.MAYOR),"No second mayor or free resident");s.saveEverything(false,true,true);LogUtils.getLogger().info("ASTRA_ELECTION VERIFIED survival deposits={}, actual candidate/resign C2S and scheduled office, exact stock and debit, saved authority; reload={}",GIFTS,reload);mc.execute(mc::stop);});
 }
 static void tick(Minecraft mc){if(stopped){if(failure!=null){LogUtils.getLogger().error("ASTRA_ELECTION FAILED {}",failure);mc.stop();}return;}ticks++;try{
  if(failure!=null)throw new IllegalStateException(failure);if(ticks>2400)throw new IllegalStateException("Election probe timeout at phase "+phase);var v=ElectionPanel.view();boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");
  if(phase==0){phase=1;ticks=0;server(mc,s->{var e=SettlementData.get(s).entries().iterator().next();var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);p.teleportTo(s.overworld(),e.center().getX()+2,e.center().getY()+1,e.center().getZ()+3,0,0);p.getAbilities().flying=false;p.onUpdateAbilities();if(!reload){for(int k=0;k<GIFT_STACKS;k++)p.getInventory().setItem(9+k,new ItemStack(k==0?Items.OAK_LOG:k==1?Items.COBBLESTONE:Items.WHEAT,64));PropertyLedger.get(s).gift(e.settlement().id(),p.getUUID(),EARNED);p.openMenu((OwnedChestEntity)s.overworld().getBlockEntity(e.center().offset(1,1,4)));}ready=true;});}
  else if(phase==1&&ready&&ticks>40){if(reload){require(v.getBoolean("mayor")&&v.getBoolean("candidate")&&v.getLong("gifts")==GIFTS,"Reload changed election or score");open(mc);phase=9;ticks=0;}else if(mc.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu){phase=2;ticks=0;given=0;}}
  // The donation, one stack at a time, through the survival chest menu as a player does it. The hall chest opens as a 9x6 page since
  // HallStorage (108 slots), so the player's inventory starts after the chest slots: slots-36 (was a fixed 27).
  else if(phase==2&&ticks>15){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,mc.player.containerMenu.slots.size()-36+given,0,ClickType.QUICK_MOVE,mc.player);ticks=0;if(++given==GIFT_STACKS)phase=4;}
  else if(phase==4&&v.getLong("gifts")==GIFTS){mc.player.closeContainer();open(mc);phase=5;ticks=0;}
  else if(phase==5&&ticks>40){click(mc,false,1);phase=6;ticks=0;}
  else if(phase==6&&v.getBoolean("candidate")){require(!v.getBoolean("mayor"),"Registration must not appoint immediately");dueSoon(mc);phase=7;ticks=0;}
  else if(phase==7&&v.getBoolean("mayor")){open(mc);click(mc,true,2);phase=8;ticks=0;}
  else if(phase==8&&ticks>10&&!v.getBoolean("mayor")&&!v.getBoolean("candidate")){server(mc,s->{var e=SettlementData.get(s).entries().iterator().next();require(e.settlement().residents().stream().filter(r->r.profession()==Profession.MAYOR).count()==1,"Resignation must appoint one NPC");});open(mc);click(mc,false,1);phase=10;ticks=0;}
  else if(phase==10&&v.getBoolean("candidate")){dueSoon(mc);phase=11;ticks=0;}
  else if(phase==11&&v.getBoolean("mayor")){phase=9;ticks=0;}
  else if(phase==9&&ticks>60)finish(mc,reload);
 }catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_ELECTION FAILED",ex);mc.stop();}}
}
