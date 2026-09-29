package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** First launch saves a parcel in transit; reload completes it. AD-136: the parcel is six scientific works carried to the laboratory (no paper chain). */
final class LogisticsProbe {
 private static int ticks;private static volatile boolean ready;private static volatile String failure;private static boolean stopped;
 static boolean enabled(){return Boolean.getBoolean("villageastra.logisticsSmoke");}
 private static ResidentEntity porter(MinecraftServer s,SettlementData.Entry e){return (ResidentEntity)s.overworld().getEntity(e.settlement().residents().stream().filter(r->r.profession()==Profession.PORTER).findFirst().orElseThrow().id());}
 static void setup(MinecraftServer s){ScienceProbe.setup(s);var e=SettlementData.get(s).entries().iterator().next();var l=s.overworld();var lab=e.settlement().buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElseThrow();var c=LogisticsRoutes.chest(l,e,lab);var farm=e.settlement().buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();LogisticsRoutes.chest(l,e,farm).setItem(0,new ItemStack(VillageAstra.RESEARCH_VOLUME.get(),6));c.clearContent();porter(s,e).setNoAi(false);}
 private static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
 static void tick(Minecraft mc){if(stopped)return;try{if(failure!=null)throw new IllegalStateException(failure);boolean reload=Boolean.getBoolean("villageastra.reloadSmoke");if(++ticks>14000)throw new IllegalStateException("Logistics timeout");if(ticks%20==0&&!ready)mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();var e=SettlementData.get(s).entries().iterator().next();var p=porter(s,e);var state=PorterWork.inspect(l,p.getUUID());var farm=e.settlement().buildings().stream().filter(b->b.type().equals("farm")).findFirst().orElseThrow();var lab=e.settlement().buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElseThrow();var c=LogisticsRoutes.chest(l,e,lab);if(ticks%200==0)com.mojang.logging.LogUtils.getLogger().info("ASTRA_LOGISTICS ticks={} parcel={} source={} labWorks={} books={}",ticks,state.getString("stage"),LogisticsRoutes.chest(l,e,farm).countItem(VillageAstra.RESEARCH_VOLUME.get()),c.countItem(VillageAstra.RESEARCH_VOLUME.get()),c.countItem(VillageAstra.RESEARCH_VOLUME.get()));
   if(!reload&&state.getString("stage").equals("deliver")&&ItemStack.of(state.getCompound("item")).is(VillageAstra.RESEARCH_VOLUME.get())){require(PorterWork.cargo(l,state).getCount()==6&&LogisticsRoutes.chest(l,e,farm).countItem(VillageAstra.RESEARCH_VOLUME.get())==0&&c.isEmpty(),"Finite parcel of works must be in transit");p.setNoAi(true);s.saveEverything(false,true,true);ready=true;}
   if(reload){if(ticks==20){require(state.getString("stage").equals("deliver")&&PorterWork.cargo(l,state).getCount()==6,"Lost saved in-transit parcel");p.setNoAi(false);}if(c.countItem(VillageAstra.RESEARCH_VOLUME.get())==6&&!state.getString("stage").equals("deliver")){require(LogisticsRoutes.chest(l,e,farm).countItem(VillageAstra.RESEARCH_VOLUME.get())==0,"The six works were carried once, none twice");ready=true;}}
  }catch(Exception ex){failure=ex.toString();}});
  if(ready){stopped=true;var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-logistics.png");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}com.mojang.logging.LogUtils.getLogger().info("ASTRA_LOGISTICS screenshot {}",path);mc.getSingleplayerServer().execute(()->{mc.getSingleplayerServer().saveEverything(false,true,true);com.mojang.logging.LogUtils.getLogger().info("ASTRA_LOGISTICS VERIFIED physical parcel of works to the laboratory; reload={} delivered={}",reload,reload);mc.execute(mc::stop);});}
 }catch(Exception ex){stopped=true;com.mojang.logging.LogUtils.getLogger().error("ASTRA_LOGISTICS FAILED",ex);mc.stop();}}
}
