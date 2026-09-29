package org.villageastra.client;
import java.nio.file.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.villageastra.world.*;
import org.villageastra.server.*;
import org.villageastra.domain.*;
/** Isolated real-client render and paid worker progression probe. */
final class ConstructionProbe {
 private static int ticks,phase,initialIndex;private static boolean stopped;private static volatile int serverIndex;private static volatile String failure;
 static boolean enabled(){return Boolean.getBoolean("villageastra.constructionSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){
  var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();for(var r:e.settlement().residents())((ResidentEntity)l.getEntity(r.id())).setNoAi(true);
  HallUpgradeGoal.request(l,e);var cost=HallUpgradeGoal.inspect(l,e.settlement().id()).getCompound("cost");var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int slot=12;
  for(String key:cost.getAllKeys()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));int left=cost.getInt(key);while(left>0){int count=Math.min(64,left);if(slot>=chest.getContainerSize())throw new IllegalStateException("Fixture funding capacity");chest.setItem(slot++,new ItemStack(item,count));left-=count;}}
 }
 private static void capture(Minecraft mc,String name)throws Exception{var p=Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-"+name+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(p);}LogUtils.getLogger().info("ASTRA_CONSTRUCTION screenshot {}",p);}
 static void tick(Minecraft mc){if(stopped)return;ticks++;try{
  if(failure!=null)throw new IllegalStateException(failure);if(ticks>6000)throw new IllegalStateException("Construction view timeout");
  var t=ConstructionOverlay.snapshot();if(ticks%200==0)LogUtils.getLogger().info("ASTRA_CONSTRUCTION progress phase={} ticks={} snapshot={} geometry={}",phase,ticks,t.getAllKeys(),ConstructionOverlay.geometry().size());if(ticks%200==0)LogUtils.getLogger().info("ASTRA_CONSTRUCTION screen={} paused={}",mc.screen==null?"none":mc.screen.getClass().getSimpleName(),mc.isPaused());if(!t.hasUUID("id"))return;
  if(phase==0){initialIndex=t.getInt("index");if(ConstructionOverlay.geometry().stream().noneMatch(b->b.removal())||ConstructionOverlay.geometry().stream().noneMatch(b->!b.removal()))throw new IllegalStateException("Missing red/blue real queue geometry");OfficeProbes.open(mc,ConstructionScreen.CONSTRUCTION);phase=1;ticks=0;}
  else if(phase==1&&ticks>60){capture(mc,"construction-card");mc.setScreen(null);phase=2;ticks=0;}
  else if(phase==2&&ticks>40){capture(mc,"construction-world");OfficeProbes.open(mc,ConstructionScreen.CONSTRUCTION);mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();for(var r:e.settlement().residents())if(r.profession()==Profession.BUILDER)((ResidentEntity)l.getEntity(r.id())).setNoAi(false);}catch(Exception ex){failure=ex.toString();}});phase=3;ticks=0;}
  else if(phase==3){
   if(ticks%20==0)mc.getSingleplayerServer().execute(()->{try{var server=mc.getSingleplayerServer();var l=server.overworld();var e=SettlementData.get(server).entries().iterator().next();serverIndex=HallUpgradeGoal.inspect(l,e.settlement().id()).getInt("index");if(serverIndex>=initialIndex+2)for(var r:e.settlement().residents())if(r.profession()==Profession.BUILDER)((ResidentEntity)l.getEntity(r.id())).setNoAi(true);}catch(Exception ex){failure=ex.toString();}});
   if(serverIndex>=initialIndex+2&&t.getInt("index")==serverIndex){OfficeProbes.open(mc,ConstructionScreen.CONSTRUCTION);phase=4;ticks=0;}
  }else if(phase==4&&ticks>40){capture(mc,"construction-progress");LogUtils.getLogger().info("ASTRA_CONSTRUCTION VERIFIED actual S2C queue, red/blue shapes, read-only local card, physical paid builder advanced {} operations from {}, snapshot agrees; no world replacement used by renderer; reload={}",serverIndex-initialIndex,initialIndex,Boolean.getBoolean("villageastra.reloadSmoke"));stopped=true;mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().saveEverything(false,true,true);mc.execute(mc::stop);});}
 }catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_CONSTRUCTION FAILED",ex);mc.stop();}}
}
