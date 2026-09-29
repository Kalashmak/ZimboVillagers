package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Profession;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-036: real right click on a resident opens the dialog card; its Sell and Buy buttons perform server-confirmed zindbo deals. Fixture: creative mode, no sale resource in inventory; genuine server trade and reputation. */
final class CreativeTradeProbe {
 private static int phase,ticks;private static volatile String failure,buyItem="",sellItem="";private static volatile UUID npc;private static volatile int coinsBefore,coinsAfterSale,deals;private static volatile boolean ready;
 static boolean enabled(){return Boolean.getBoolean("villageastra.creativeTradeSmoke");}
 private static final Map<Profession,ItemStack> GOODS=Map.of(Profession.MINER,new ItemStack(Items.COBBLESTONE,64),Profession.FARMER,new ItemStack(Items.WHEAT,64),Profession.FORESTER,new ItemStack(Items.OAK_LOG,32),Profession.CARPENTER,new ItemStack(Items.OAK_PLANKS,64),Profession.MASON,new ItemStack(Items.STONE_BRICKS,32));
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-creative-trade-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_CREATIVE_TRADE screenshot {}",path);}
 private static int gcd(int a,int b){return b==0?a:gcd(b,a%b);}
 private static void fixture(net.minecraft.server.MinecraftServer s){
  var p=s.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.getInventory().clearContent();var l=p.serverLevel();var e=SettlementData.get(s).entries().iterator().next();
  for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity x)x.setNoAi(true);
  for(var r:e.settlement().residents()){
   if(!(l.getEntity(r.id()) instanceof ResidentEntity x)||r.profession()==null||!GOODS.containsKey(r.profession()))continue;var b=e.settlement().workplace(r.id());var chest=b==null?null:LogisticsRoutes.chest(l,e,b);if(chest==null)continue;
   x.teleportTo(p.getX()+2.5,p.getY(),p.getZ());
   var goods=GOODS.get(r.profession());for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).isEmpty()){chest.setItem(i,goods.copy());break;}
   var offer=Trade.offer(p,x);if(!offer.reason().isEmpty())throw new IllegalStateException("Card reason "+offer.reason()+" for "+r.profession());
   var sale=offer.buys().stream().filter(line->line.count()>=line.per()/gcd(line.coins(),line.per())).findFirst().orElse(null);var buy=offer.sells().stream().filter(line->line.count()>0).findFirst().orElse(null);
   if(sale==null||buy==null){LogUtils.getLogger().info("ASTRA_CREATIVE_TRADE skip {} buys={} sells={}",r.profession(),offer.buys(),offer.sells());continue;}
   int step=sale.per()/gcd(sale.coins(),sale.per()),give=Math.min(sale.count(),64)/step*step;
   p.getInventory().clearContent();p.getInventory().add(new ItemStack(VillageAstra.ZINDBO.get(),10));p.getInventory().selected=8;
   buyItem=BuiltInRegistries.ITEM.getKey(sale.item()).toString();sellItem=BuiltInRegistries.ITEM.getKey(buy.item()).toString();npc=x.getUUID();coinsBefore=10;
   x.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,p.getEyePosition());p.teleportTo(l,p.getX(),p.getY(),p.getZ(),-90,10);
   LogUtils.getLogger().info("ASTRA_CREATIVE_TRADE fixture {} at {} sells to village {}x{} and buys {}",r.profession(),b.type(),give,buyItem,sellItem);ready=true;return;
  }
  throw new IllegalStateException("No resident with both a useful purchase and surplus");
 }
 private static int row(TradeScreen screen,String list,String item){var lines=screen.view().getList(list,10);for(int i=0;i<lines.size();i++)if(lines.getCompound(i).getString("item").equals(item))return i<screen.rows()?i:-1;return -1;}
 private static void click(Minecraft mc,int x,int y){if(!mc.screen.mouseClicked(x,y,0))throw new IllegalStateException("Button missed at "+x+","+y);mc.screen.mouseReleased(x,y,0);}
 static void tick(Minecraft mc){try{
  // AD-146: a click on a resident opens the conversation first; the card is its «Торговать и дарить».
  if(mc.screen instanceof DialogScreen)DialogScreen.pick("resident/trade");

  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>4000)throw new IllegalStateException("Trade timeout phase="+phase);
  // The card sizes itself to the window (AD-128): clicks use its own geometry.
  int x=mc.screen instanceof TradeScreen card?card.left():0,y=mc.screen instanceof TradeScreen cardTop?cardTop.top():0;
  if(phase==0&&ticks>60){var limit=new net.minecraft.nbt.CompoundTag();limit.putBoolean("creativeSource",true);limit.putInt("count",3000);limit.putInt("price",1);limit.putInt("per",1);if(TradeScreen.saleCount(limit)!=2304||TradeScreen.donationCount(limit)!=2304)throw new IllegalStateException("Creative button quantity exceeds server bound");phase=1;ticks=0;mc.getSingleplayerServer().execute(()->{try{fixture(mc.getSingleplayerServer());}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==1&&ready&&ticks>40){ResidentEntity found=null;for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(npc))found=(ResidentEntity)entity;
   if(found==null){if(ticks>400)throw new IllegalStateException("Resident not visible on client");return;}
   mc.gameMode.interact(mc.player,found,InteractionHand.MAIN_HAND);phase=2;ticks=0;}
  else if(phase==2&&mc.screen instanceof TradeScreen screen&&ticks>20){
   if(!screen.view().getString("reason").isEmpty())throw new IllegalStateException("Card refused: "+screen.view().getString("reason"));
   int r=row(screen,"buys",buyItem);if(r<0)throw new IllegalStateException("Sale row missing for "+buyItem+" in "+screen.view().getList("buys",10));
   if(!screen.view().getBoolean("creativeSource"))throw new IllegalStateException("Creative source not shown");
   if(screen.view().getList("buys",10).getCompound(r).getInt("have")!=0)throw new IllegalStateException("Fixture must have no sale resource");
   capture(mc,"card");click(mc,x+TradeScreen.SELL_X+TradeScreen.SELL_W/2,y+TradeScreen.ROW0+r*TradeScreen.ROW_H+TradeScreen.BUTTON_H/2);phase=3;ticks=0;}
  else if(phase==2&&ticks>200)throw new IllegalStateException("Dialog card did not open");
  else if(phase==3&&mc.screen instanceof TradeScreen screen&&!screen.view().getString("result").isEmpty()&&ticks>10){
   if(!screen.view().getString("result").equals("ok"))throw new IllegalStateException("Sale result "+screen.view().getString("result"));
   mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);coinsAfterSale=p.getInventory().countItem(VillageAstra.ZINDBO.get())+(int)TradeLedger.get(p.server).owed(p.getUUID());});phase=4;ticks=0;}
  else if(phase==4&&ticks>30&&mc.screen instanceof TradeScreen screen){
   if(screen.view().getLong("reputation")<=0)throw new IllegalStateException("Creative sale did not increase reputation");
   if(coinsAfterSale<=coinsBefore)throw new IllegalStateException("Sale did not pay zindbo: "+coinsAfterSale);int r=row(screen,"sells",sellItem);if(r<0)throw new IllegalStateException("Buy row missing for "+sellItem);
   screen.view().putString("result","");click(mc,x+TradeScreen.BUY_X+TradeScreen.BUY_W/2,y+TradeScreen.ROW0+r*TradeScreen.ROW_H+TradeScreen.BUTTON_H/2);phase=5;ticks=0;}
  else if(phase==5&&mc.screen instanceof TradeScreen screen&&!screen.view().getString("result").isEmpty()&&ticks>10){
   if(!screen.view().getString("result").equals("ok"))throw new IllegalStateException("Purchase result "+screen.view().getString("result"));
   capture(mc,"deal");
   mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var item=BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(sellItem));
    int coins=p.getInventory().countItem(VillageAstra.ZINDBO.get())+(int)TradeLedger.get(p.server).owed(p.getUUID());if(p.getInventory().countItem(item)<=0||coins>=coinsAfterSale)throw new IllegalStateException("Purchase not applied: coins="+coins+" item="+p.getInventory().countItem(item));deals=2;}catch(Exception ex){failure=ex.toString();}});phase=6;ticks=0;}
  else if(phase==6&&deals==2){LogUtils.getLogger().info("ASTRA_CREATIVE_TRADE VERIFIED emptyInventory=true reputation=true limit=2304; right click opened the resident card; Sell paid zindbo {}->{} and Buy delivered {} for coins; reload=false",coinsBefore,coinsAfterSale,sellItem);mc.setScreen(null);mc.stop();phase=7;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_CREATIVE_TRADE FAILED",ex);mc.stop();}}
}
