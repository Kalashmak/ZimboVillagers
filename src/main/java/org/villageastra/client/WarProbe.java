package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-049: the war desk in the office — the mayor sees the neighbours and musters a campaign from the screen, without operator commands. */
final class WarProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,supplied;
 static boolean enabled(){return Boolean.getBoolean("villageastra.warSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-war-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_WAR screenshot {}",path);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 /** Fixture: the player is mayor, a neighbouring settlement exists, and the barracks holds real supplies for one soldier. */
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var settlement=e.settlement();var p=s.getPlayerList().getPlayers().get(0);
  settlement.appointPlayerMayor(p.getUUID());
  var neighbour=new Settlement(java.util.UUID.randomUUID());
  var center=e.center().offset(96,0,0);
  neighbour.addBuilding(new Settlement.Building(Settlement.childId(neighbour.id(),"building/town_hall"),"town_hall",0,0,0));
  neighbour.addHome(new Settlement.Home(Settlement.childId(neighbour.id(),"home"),2,2,true));
  var r=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);neighbour.admit(r,neighbour.homes().iterator().next().id());
  SettlementData.get(s).add(new SettlementData.Entry(neighbour,e.dimension(),center));
  var barracks=new Settlement.Building(Settlement.childId(settlement.id(),"building/barracks-war"),"barracks",10,0,-6);settlement.addBuilding(barracks);
  var chestPos=LogisticsRoutes.position(e,barracks);l.setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(l,e,barracks);if(chest==null)throw new IllegalStateException("Barracks chest missing");
  for(int i=0;i<8;i++)chest.setItem(i,new ItemStack(Items.OAK_FENCE,64));
  chest.setItem(8,new ItemStack(Items.CAMPFIRE,64));chest.setItem(9,new ItemStack(Items.TNT,64));chest.setItem(10,new ItemStack(Items.BREAD,64));
  // A soldier needs a real bed: the fixture adds one home record of its own rather than evicting a resident.
  var quarters=new Settlement.Home(Settlement.childId(settlement.id(),"home/barracks-war"),1,1,true);settlement.addHome(quarters);
  var soldier=new Resident(java.util.UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);
  settlement.admit(soldier,quarters.id());soldier.trainMilitary();settlement.assign(soldier.id(),Profession.SOLDIER,barracks.id());
  SettlementData.get(s).setDirty();
  p.teleportTo(l,e.center().getX()+1.5,e.center().getY()+1,e.center().getZ()+1.5,0,0);
  LogUtils.getLogger().info("ASTRA_WAR fixture mayor, neighbour at {} and a barracks with real supplies",center.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>6000)throw new IllegalStateException("War timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks>40){OfficeProbes.open(mc,ConstructionScreen.WAR);phase=2;ticks=0;}
  else if(phase==2&&ticks>60&&mc.screen instanceof ConstructionScreen screen){
   var war=ConstructionOverlay.snapshot().getCompound("war");
   if(!war.getBoolean("mayor"))throw new IllegalStateException("The desk does not see the mayor: "+war);
   var rows=war.getList("neighbours",Tag.TAG_COMPOUND);
   if(rows.isEmpty())throw new IllegalStateException("No neighbour listed on the desk");
   if(war.getInt("soldiers")<1||!war.getBoolean("barracks"))throw new IllegalStateException("The desk does not see the force: "+war);
   progress="neighbours="+rows.size()+" soldiers="+war.getInt("soldiers")+" target="+BlockPos.of(rows.getCompound(0).getLong("center")).toShortString();
   LogUtils.getLogger().info("ASTRA_WAR desk {}",progress);
   capture(mc,"desk");
   // The neighbour's own campaign button, found through the panel rather than by coordinates.
   screen.tick();var panel=(WarPanel)screen.panel(ConstructionScreen.WAR);var muster=panel.musterButton(rows.getCompound(0).getUUID("village"));
   if(muster==null||!muster.active)throw new IllegalStateException("Campaign button not offered: "+(muster==null?"hidden":muster instanceof OfficeUi.OfficeButton ob&&ob.reason()!=null?ob.reason().getString():"inactive"));
   OfficeProbes.clickOrFail(screen,muster,"Campaign button");phase=3;ticks=0;}
  else if(phase==3&&ticks>60){
   var war=ConstructionOverlay.snapshot().getCompound("war");
   if(war.getString("state").equals("none"))throw new IllegalStateException("The campaign did not muster: "+war.getString("state"));
   if(war.getInt("fences")<=0||war.getInt("rations")<=0)throw new IllegalStateException("The campaign carries no real supplies: "+war);
   supplied=true;capture(mc,"campaign");
   LogUtils.getLogger().info("ASTRA_WAR VERIFIED mayor mustered a campaign from the office: {}; fences {} rations {} state {}; reload=false",progress,war.getInt("fences"),war.getInt("rations"),war.getString("state"));
   mc.setScreen(null);mc.stop();phase=4;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WAR FAILED",ex);mc.stop();}}
}
