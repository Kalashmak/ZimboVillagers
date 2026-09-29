package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-039: two real villages; the ordinary proposal cadence matches bread demand to surplus, the caravaneer materializes near the observer, walks and delivers once. Fixture: second village, caravan yard record, bread stock and one caravaneer assignment. */
final class CaravanProbe {
 static final BlockPos SECOND=new BlockPos(120,-60,0);
 private static volatile boolean tookCart,sawCart;private static boolean roadCaptured,roadPending;
 /** AD-085: with -PcartSmoke the yard has a cart; the trip must take it and it must walk behind the caravaneer on the road. */
 static boolean cart(){return Boolean.getBoolean("villageastra.cartSmoke")||CaravanDogProbe.enabled()||CaravanHorseProbe.enabled();}
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile UUID contract,destination;private static volatile boolean ready,walked,delivered;private static volatile int breadBefore=-1;
 static boolean enabled(){return Boolean.getBoolean("villageastra.caravanSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-caravan-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_CARAVAN screenshot {}",path);}
 private static int bread(net.minecraft.server.MinecraftServer s,SettlementData.Entry e){var c=(Container)s.overworld().getBlockEntity(LogisticsRoutes.position(e,Workshops.hall(e)));int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.BREAD))n+=c.getItem(i).getCount();return n;}
 static void tick(Minecraft mc){try{
  if(CaravanRestartProbe.stopping())return;
  if(CaravanDogProbe.enabled()||CaravanHorseProbe.enabled()){mc.options.hideGui=true;if(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)mc.setScreen(null);}if(failure!=null)throw new IllegalStateException(failure);if(++ticks>12000)throw new IllegalStateException("Caravan timeout phase="+phase+" "+progress);
  if(phase==0&&CaravanRestartProbe.reloading()){phase=11;mc.getSingleplayerServer().execute(()->{try{var t=CaravanRestartProbe.restore(mc.getSingleplayerServer());contract=t.getUUID("id");destination=t.getUUID("destination");ready=true;}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==11&&ready){phase=1;ticks=0;}
  else if(phase==0&&ticks>60){phase=10;ticks=0;mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().get(0);p.teleportTo(s.overworld(),60.5,-45,-20.5,0,40);});}
  else if(phase==10&&ticks>200){phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{
    var s=mc.getSingleplayerServer();var l=s.overworld();
    var home=SettlementData.get(s).entries().iterator().next();
    var second=StarterVillage.create(l,SECOND);destination=second.id();var dest=SettlementData.get(s).entry(destination);
    var yard=new Settlement.Building(Settlement.childId(home.settlement().id(),"building/caravan-probe"),"caravan",-40,0,10);home.settlement().addBuilding(yard);
    var r=home.settlement().residents().stream().filter(x->x.alive()&&x.life()==Resident.Life.ADULT&&x.profession()!=Profession.BUILDER&&x.profession()!=Profession.MAYOR).findFirst().orElseThrow();home.settlement().assign(r.id(),Profession.CARAVANEER,yard.id());
    var hallChest=(Container)l.getBlockEntity(LogisticsRoutes.position(home,Workshops.hall(home)));for(int i=0;i<hallChest.getContainerSize();i++)if(hallChest.getItem(i).isEmpty()){hallChest.setItem(i,new ItemStack(Items.BREAD,64));break;}
    var destChest=(Container)l.getBlockEntity(LogisticsRoutes.position(dest,Workshops.hall(dest)));for(int i=0;i<destChest.getContainerSize();i++)if(destChest.getItem(i).is(Items.BREAD))destChest.setItem(i,ItemStack.EMPTY);
    for(var e:SettlementData.get(s).entries())for(var x:e.settlement().residents())if(!x.id().equals(r.id())&&l.getEntity(x.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
    if(cart()&&!CaravanDogProbe.enabled()&&!CaravanHorseProbe.enabled()){var spot=CartHitch.parking(l,BuildingPlacement.origin(home,yard));if(spot==null)throw new IllegalStateException("No ground for the cart at the yard");
     l.addFreshEntity(new CartEntity(l,spot,home.settlement().id()));LogUtils.getLogger().info("ASTRA_CARAVAN fixture: cart at the yard {}",spot.toShortString());}
    CaravanEscortProbe.setup(l,home,yard,dest);CaravanDogProbe.setup(l,home,yard,dest);CaravanHorseProbe.setup(l,home,yard);
    SettlementData.get(s).setDirty();breadBefore=0;LogUtils.getLogger().info("ASTRA_CARAVAN fixture: second village at {}, caravan yard record, 64 bread at home, destination bread removed, caravaneer {}",SECOND.toShortString(),r.id());ready=true;
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ready&&ticks%40==0){mc.getSingleplayerServer().execute(()->{try{var s=mc.getSingleplayerServer();var l=s.overworld();
    for(var t:Caravans.contracts(s))if(t.getString("kind").equals("trade")&&t.getUUID("destination").equals(destination)){contract=t.getUUID("id");}
    if(contract==null){progress="waiting for proposal at tick "+SettlementData.get(s).clock().ticks();return;}
    var t=Caravans.contract(s,contract);var at=Caravans.position(l,t,t.getDouble("progress"));
    if(t.getBoolean("materialized")&&t.getDouble("progress")>8)walked=true;CaravanEscortProbe.observe(l,t);CaravanDogProbe.observe(l,t);CaravanHorseProbe.observe(l,t);
    if(CaravanRestartProbe.checkpoint(mc,l,t))return;
    if(t.contains("cart"))tookCart=true;
    if(cart()&&t.getBoolean("materialized")&&l.getEntity(t.getUUID("caravaneer")) instanceof net.minecraft.world.entity.LivingEntity driver
      &&!l.getEntitiesOfClass(CartEntity.class,driver.getBoundingBox().inflate(8),c->contract.equals(c.trip())&&driver.getUUID().equals(c.puller())).isEmpty())sawCart=true;
    var p=s.getPlayerList().getPlayers().get(0);if(t.getString("state").equals(Caravans.TRANSIT)||CaravanEscortProbe.enabled()||CaravanDogProbe.enabled()||CaravanHorseProbe.enabled())p.teleportTo(l,at.getX()+.5,at.getY()+10,at.getZ()-14.5,0,35);
    var body=l.getEntity(t.getUUID("caravaneer"));
    progress="state="+t.getString("state")+" progress="+String.format(java.util.Locale.ROOT,"%.1f/%.1f",t.getDouble("progress"),Caravans.length(t))+" materialized="+t.getBoolean("materialized")+" cargo="+t.getList("cargo",10)+" body="+(body instanceof ResidentEntity npc?npc.workStatus()+" at "+npc.blockPosition().toShortString()+" goals="+npc.runningGoals()+" noAi="+npc.isNoAi()+" nav="+(npc.getNavigation().getPath()==null?"none":npc.getNavigation().getPath().getTarget().toShortString()+"/"+npc.getNavigation().getPath().canReach())+" epoch="+npc.getPersistentData().getLong(Caravans.EPOCH)+"/"+Caravans.epoch(s,npc.getUUID()):body==null?"none":body.getType()+" at "+body.blockPosition());
    if(!delivered&&t.getInt("delivered")>0){var dest=SettlementData.get(s).entry(destination);int now=bread(s,dest);if(now!=t.getInt("delivered")+CaravanDogProbe.extraDelivered(l,t))throw new IllegalStateException("Destination bread "+now+" != delivered "+t.getInt("delivered"));if(t.getLong("paid")!=(long)t.getInt("delivered")*t.getInt("price")/t.getInt("per"))throw new IllegalStateException("Payment mismatch "+t);delivered=true;}
    LogUtils.getLogger().info("ASTRA_CARAVAN progress {}",progress);}catch(Exception ex){failure=ex.toString();}});
   if(walked&&!roadCaptured&&CaravanDogProbe.captureReady()&&CaravanHorseProbe.captureReady()){if((CaravanDogProbe.enabled()||CaravanHorseProbe.enabled())&&!roadPending)roadPending=true;else{capture(mc,"road");roadCaptured=true;}}
   if(delivered&&cart()&&!(tookCart&&sawCart))throw new IllegalStateException("The cart did not travel with the caravan: took="+tookCart+" walked behind="+sawCart);
   if(delivered&&CaravanEscortProbe.finished()&&CaravanDogProbe.finished()&&CaravanHorseProbe.finished()){if(!walked)throw new IllegalStateException("Delivered without a materialized walk: "+progress);mc.getSingleplayerServer().submit(()->CaravanRestartProbe.verify(mc.getSingleplayerServer(),Caravans.contract(mc.getSingleplayerServer(),contract))).join();capture(mc,"delivered");LogUtils.getLogger().info("ASTRA_CARAVAN VERIFIED proposal from real demand and surplus; caravaneer materialized near the observer, walked and delivered bread once with payment; {}; cart={}; reload={}",progress,cart()?"taken and walked behind the caravaneer":"none",CaravanRestartProbe.reloading());mc.stop();phase=2;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CARAVAN FAILED",ex);if(CaravanDogProbe.enabled()||CaravanHorseProbe.enabled())try{capture(mc,"failed");}catch(Exception captureError){LogUtils.getLogger().warn("Dog failure capture",captureError);}mc.stop();}}
}
