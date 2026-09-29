package org.villageastra.client;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-032: no player orders. Full housing makes the NPC mayor plan a house, walk to the site and approve it; the builder builds it. Fixture supplies materials only. */
final class AutonomyProbe {
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean approved,built;
 static boolean enabled(){return Boolean.getBoolean("villageastra.autonomySmoke");}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-autonomy-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_AUTONOMY screenshot {}",path);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>36000)throw new IllegalStateException("Autonomy timeout phase="+phase+" "+progress);
  if(phase==0){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
   var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);
   if(e.settlement().governance().playerMayor()!=null)throw new IllegalStateException("Probe requires an NPC mayor");
   if(MayorPlanner.need(e.settlement())==null)throw new IllegalStateException("Starter village should lack housing");
   var estimate=BuildingOrders.survey(l,e,"home",0,e.center().offset(-15,-1,-9));
   var cost=estimate.state().getCompound("cost");if(cost.isEmpty())throw new IllegalStateException("No reference estimate: "+estimate.reason());
   var chest=(Container)l.getBlockEntity(e.center().offset(1,1,4));int slot=8;
   for(var key:cost.getAllKeys()){int left=cost.getInt(key)+(key.endsWith("timber_scaffold")||key.equals("minecraft:cobblestone")?16:0);var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));while(left>0){if(slot>=chest.getContainerSize())throw new IllegalStateException("Fixture capacity");int n=Math.min(item.getMaxStackSize(),left);chest.setItem(slot++,new ItemStack(item,n));left-=n;}}
   // Frozen residents other than the mayor and builder are parked away from building sites.
   int parked=0;for(var r:e.settlement().residents())if(r.profession()!=Profession.MAYOR&&r.profession()!=Profession.BUILDER){var npc=(ResidentEntity)l.getEntity(r.id());npc.setNoAi(true);npc.teleportTo(e.center().getX()+40.5+2*parked++,e.center().getY(),e.center().getZ()-24.5);}
   var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(l,e.center().getX()-20.5,e.center().getY()+12,e.center().getZ()-20.5,-45,35);
   LogUtils.getLogger().info("ASTRA_AUTONOMY fixture: NPC mayor, full housing, materials for one house in hall chest");
  }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ticks%100==0){mc.getSingleplayerServer().execute(()->{try{
   var s=mc.getSingleplayerServer();var l=s.overworld();var e=entry(s);var proposal=MayorPlanner.proposal(e.settlement().id());
   String status="none";for(var r:e.settlement().residents())if(r.profession()==Profession.MAYOR&&l.getEntity(r.id()) instanceof ResidentEntity m)status=m.workStatus()+"@"+m.blockPosition().toShortString();
   boolean pending=HallUpgradeGoal.pending(l,e.settlement().id());
   progress="proposal="+(proposal==null?"none":proposal.design()+"@"+proposal.site().toShortString())+" mayor="+status+" pending="+pending;
   if(pending&&!approved){approved=true;LogUtils.getLogger().info("ASTRA_AUTONOMY mayor approved {} without player orders",HallUpgradeGoal.inspect(l,e.settlement().id()).getString("design"));}
   if(approved&&HallUpgradeGoal.exists(l,e.settlement().id())){var state=HallUpgradeGoal.inspect(l,e.settlement().id());progress+=" index="+state.getInt("index")+"/"+state.getList("ops",10).size();
    if(state.getBoolean("complete")){var id=BuildingOrders.buildingId(state);if(e.settlement().homes().stream().noneMatch(h->h.id().equals(id)&&h.usable()))failure="Completed house is not usable housing";else built=true;}}
   LogUtils.getLogger().info("ASTRA_AUTONOMY progress {}",progress);
  }catch(Exception ex){failure=ex.toString();}});
   if(ticks==3000)capture(mc,"planning");
   if(built){capture(mc,"house");LogUtils.getLogger().info("ASTRA_AUTONOMY VERIFIED NPC mayor planned a house from full housing, walked to the site, approved it; builder built it; new usable home registered; reload=false");mc.stop();phase=2;}
  }
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_AUTONOMY FAILED",ex);mc.stop();}}
}
