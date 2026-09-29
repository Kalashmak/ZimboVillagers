package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-103: a trade trip leaves with its cart and 128 sheaves; on the mayor's order it leaves the stuck cart on the road and goes on with one
 *  load; once it is home, a free caravaneer of the village walks out to the cart and brings it and what its hold still carries back to the
 *  stock. Everything is counted: the stock loses exactly what the neighbour got, and the cart stands empty at its yard again. */
final class CartRecoveryProbe {
 static final BlockPos SECOND=new BlockPos(120,-60,0);
 private static int phase,ticks;private static volatile String failure,progress="";private static volatile boolean ready,left,homeAgain,fetched,sawReturn,homeward,roadShot;
 private static volatile UUID village,destination,trip,recovery;private static volatile int wheatBefore=-1,destBefore=-1,leftInCart;private static volatile long cartAt;private static volatile String done="";
 static boolean enabled(){return Boolean.getBoolean("villageastra.cartRecoverySmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-recover-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RECOVER screenshot {}",path);}
 private static int count(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Item item){var c=(Container)l.getBlockEntity(LogisticsRoutes.position(e,Workshops.hall(e)));int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 private static void fixture(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var p=s.getPlayerList().getPlayers().get(0);
  var home=SettlementData.get(s).entries().iterator().next();village=home.settlement().id();home.settlement().appointPlayerMayor(p.getUUID());
  var second=StarterVillage.create(l,SECOND);destination=second.id();var dest=SettlementData.get(s).entry(destination);
  var yard=new Settlement.Building(Settlement.childId(village,"building/caravan-recover"),"caravan",-40,0,10);home.settlement().addBuilding(yard);
  var r=home.settlement().residents().stream().filter(x->x.alive()&&x.life()==Resident.Life.ADULT&&x.profession()!=Profession.BUILDER&&x.profession()!=Profession.MAYOR).findFirst().orElseThrow();
  home.settlement().assign(r.id(),Profession.CARAVANEER,yard.id());
  for(var e:SettlementData.get(s).entries())for(var x:e.settlement().residents())if(!x.id().equals(r.id())&&l.getEntity(x.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
  var hall=(Container)l.getBlockEntity(LogisticsRoutes.position(home,Workshops.hall(home)));int put=0;
  for(int i=0;i<hall.getContainerSize()&&put<2;i++)if(hall.getItem(i).isEmpty()){hall.setItem(i,new ItemStack(Items.WHEAT,64));put++;}
  if(put<2)throw new IllegalStateException("No room for the wheat at the hall");
  var spot=CartHitch.parking(l,BuildingPlacement.origin(home,yard));if(spot==null)throw new IllegalStateException("No ground for the cart at the yard");
  l.addFreshEntity(new CartEntity(l,spot,village));
  wheatBefore=count(l,home,Items.WHEAT);destBefore=count(l,dest,Items.WHEAT);
  // The trip itself, as a mayor's contract would make it: 128 sheaves, which only the cart can carry.
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","trade");t.putUUID("source",village);t.putUUID("destination",destination);
  t.putString("dimension",l.dimension().location().toString());t.putString("item","minecraft:wheat");t.putInt("count",128);
  var price=Trade.price(Items.WHEAT);t.putInt("price",price==null?1:price.coins());t.putInt("per",price==null?1:Math.max(1,price.per()));
  t.putUUID("caravaneer",r.id());t.put("cargo",new ListTag());t.put("stamps",new CompoundTag());t.putInt("takes",0);t.putInt("drops",0);t.putInt("delivered",0);t.putInt("lost",0);
  t.putLong("from",Caravans.gate(l,home,dest.center()).asLong());t.putLong("to",Caravans.gate(l,dest,home.center()).asLong());t.putDouble("progress",0);t.putBoolean("materialized",false);
  t.putString("state",Caravans.ACCEPTED);Caravans.update(s,t);trip=t.getUUID("id");
  var from=BlockPos.of(t.getLong("from"));p.teleportTo(l,from.getX()+12.5,from.getY()+12,from.getZ()-12.5,20,35);
  LogUtils.getLogger().info("ASTRA_RECOVER fixture: cart at the yard {}, {} wheat at the hall, trip of 128 from the gate {}",spot.toShortString(),wheatBefore,from.toShortString());ready=true;
 }catch(Exception ex){failure=ex.toString();}});}
 private static void sample(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
  var s=mc.getSingleplayerServer();var l=s.overworld();var p=s.getPlayerList().getPlayers().get(0);var home=SettlementData.get(s).entry(village);
  var t=Caravans.contract(s,trip);if(t==null){failure="The trip is gone";return;}
  // The cart gets stuck on the road: the mayor, standing near, orders it left and the trip goes on with one load.
  if(!left&&t.getString("state").equals(Caravans.TRANSIT)&&t.getBoolean("materialized")&&t.getDouble("progress")>6&&t.contains("cart")){
   var g=home.settlement().governance();var reason=ManagementOrders.leaveCart(p,village,trip,g.epoch(),g.revision());
   if(!reason.isEmpty()){failure="The mayor could not have the cart left: "+reason;return;}
   left=true;leftInCart=t.getInt("leftInCart");cartAt=t.getLong("cartLeftAt");
   LogUtils.getLogger().info("ASTRA_RECOVER the mayor had the cart left at {} with {} sheaves in its hold",BlockPos.of(cartAt).toShortString(),leftInCart);
   // Away from the road the rest of the trip runs as a record.
   p.teleportTo(l,-200.5,-40,-200.5,0,0);}
  if(left&&!homeAgain&&t.getString("state").equals(Caravans.CLOSED)){homeAgain=true;var at=BlockPos.of(cartAt);p.teleportTo(l,at.getX()+10.5,at.getY()+8,at.getZ()-10.5,45,30);
   LogUtils.getLogger().info("ASTRA_RECOVER the trip is home with {} delivered; the observer waits by the cart",t.getInt("delivered"));}
  if(recovery==null)for(var c:Caravans.contracts(s))if(c.getString("kind").equals(Caravans.RECOVER)&&c.hasUUID("trip")&&c.getUUID("trip").equals(trip))recovery=c.getUUID("id");
  var fetch=recovery==null?null:Caravans.contract(s,recovery);
  if(fetch!=null&&fetch.getString("state").equals(Caravans.RETURNING)&&fetch.getBoolean("materialized")&&l.getEntity(fetch.getUUID("caravaneer")) instanceof ResidentEntity driver
    &&!l.getEntitiesOfClass(CartEntity.class,driver.getBoundingBox().inflate(8),c->recovery.equals(c.trip())).isEmpty())sawReturn=true;
  if(fetch!=null&&fetch.getString("state").equals(Caravans.RETURNING)&&fetch.contains("cart"))homeward=true;
  if(fetch!=null&&fetch.getString("state").equals(Caravans.RETURNING)&&fetch.getBoolean("materialized")){var body=l.getEntity(fetch.getUUID("caravaneer"));if(body!=null)p.teleportTo(l,body.getX()+8.5,body.getY()+8,body.getZ()-8.5,30,30);}
  var body=fetch==null?null:l.getEntity(fetch.getUUID("caravaneer"));
  var driver0=l.getEntity(t.getUUID("caravaneer"));
  progress="trip="+t.getString("state")+String.format(Locale.ROOT," %.1f/%.1f",t.getDouble("progress"),Caravans.length(t))+" materialized="+t.getBoolean("materialized")
   +" driver="+(driver0 instanceof ResidentEntity d0?d0.workStatus()+" at "+d0.blockPosition().toShortString()+" goals="+d0.runningGoals()+" nav="+(d0.getNavigation().getPath()==null?"none":d0.getNavigation().getPath().getTarget().toShortString()+"/"+d0.getNavigation().getPath().canReach()):"none")
   +" delivered="+t.getInt("delivered")+" left="+leftInCart+" fetch="+(fetch==null?"none":fetch.getString("state")+String.format(Locale.ROOT," %.1f/%.1f",fetch.getDouble("progress"),Caravans.length(fetch))+" cart="+fetch.contains("cart")+" materialized="+fetch.getBoolean("materialized"))
   +" body="+(body instanceof ResidentEntity npc?npc.workStatus()+" at "+npc.blockPosition().toShortString():"none")+" wheat="+count(l,home,Items.WHEAT)+"/"+wheatBefore;
  if(fetch!=null&&fetch.getString("state").equals(Caravans.CLOSED)&&!fetched){
   var dest=SettlementData.get(s).entry(destination);int there=count(l,dest,Items.WHEAT),here=count(l,home,Items.WHEAT);
   var yard=home.settlement().buildings().stream().filter(b->b.type().equals("caravan")).findFirst().orElseThrow();
   var carts=l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(BuildingPlacement.origin(home,yard)).inflate(16),c->village.equals(c.settlement())&&!c.travelling());
   if(!Caravans.contract(s,trip).getBoolean("cartFetched")){failure="The first trip does not know its cart is home";return;}
   if(carts.size()!=1||!carts.get(0).isEmpty()){failure="The cart does not stand empty at its yard: "+carts.size();return;}
   if(here!=wheatBefore-(there-destBefore)){failure="Sheaves made or lost: hall "+here+", neighbour "+there+" (had "+destBefore+"), before "+wheatBefore;return;}
   done=" neighbour=+"+(there-destBefore)+" hall="+here+" cartAtYard=empty";fetched=true;}
  if(fetched)progress+=done;
 }catch(Exception ex){failure=ex.toString();}});}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>30000)throw new IllegalStateException("Recover timeout phase="+phase+" "+progress);
  if(phase==0&&ticks>60){phase=1;ticks=0;fixture(mc);}
  else if(phase==1&&ready&&ticks%20==0){sample(mc);if(ticks%200==0)LogUtils.getLogger().info("ASTRA_RECOVER progress {}",progress);
   // The fetch trip back is short: the frame is taken as soon as it turns home with the cart.
   if(homeward&&!roadShot){roadShot=true;capture(mc,"road");}
   if(fetched){if(!roadShot){roadShot=true;capture(mc,"road");}capture(mc,"home");
    LogUtils.getLogger().info("ASTRA_RECOVER VERIFIED the cart left on the road was fetched home by a caravaneer and its hold went to the stock: {}; cartBehindSeen={}; reload=false",progress,sawReturn);
    mc.setScreen(null);mc.stop();phase=3;}}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_RECOVER FAILED",ex);mc.stop();}}
}
