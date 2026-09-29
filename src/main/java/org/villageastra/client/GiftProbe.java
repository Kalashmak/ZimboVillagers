package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-128: a real right click on a resident opens the card with the gift strip; «Подарить» is clicked twice (confirmation) through the
 *  screen, the resident puts the diamond chestplate on (screenshot), then a worn Efficiency V pickaxe goes to the hall chest. */
final class GiftProbe {
 private static int phase,ticks,guiScale=-1;private static boolean tall,small;private static volatile String failure;private static volatile UUID npc,village;private static volatile boolean ready,checked;private static volatile long before;
 static boolean enabled(){return Boolean.getBoolean("villageastra.giftSmoke");}
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-gift-"+suffix+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_GIFT screenshot {}",path);}
 private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer());}catch(Exception ex){failure=ex.toString();}});}
 private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s){return s.getPlayerList().getPlayers().get(0);}
 private static long score(net.minecraft.server.MinecraftServer s){return PropertyLedger.get(s).roll(village).account(player(s).getUUID()).score();}
 private static net.minecraft.world.Container hall(net.minecraft.server.MinecraftServer s){var e=SettlementData.get(s).entry(village);return LogisticsRoutes.chest(s.overworld(),e,Workshops.hall(e));}
 private static void fixture(net.minecraft.server.MinecraftServer s){
  var p=player(s);p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;var l=p.serverLevel();var e=SettlementData.get(s).entries().iterator().next();village=e.settlement().id();
  for(var r:e.settlement().residents())if(l.getEntity(r.id()) instanceof ResidentEntity x)x.setNoAi(true);
  var hall=hall(s);require(hall!=null,"No hall chest");for(int i=0;i<hall.getContainerSize();i++)if(hall.getItem(i).is(net.minecraft.tags.ItemTags.PICKAXES))hall.setItem(i,ItemStack.EMPTY);
  var r=e.settlement().residents().stream().filter(x->x.alive()&&x.life()==org.villageastra.domain.Resident.Life.ADULT&&x.profession()!=null&&l.getEntity(x.id()) instanceof ResidentEntity).findFirst().orElseThrow();
  var x=(ResidentEntity)l.getEntity(r.id());p.getAbilities().flying=false;p.onUpdateAbilities();
  x.teleportTo(p.getX()+2.5,p.getY(),p.getZ());x.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,p.getEyePosition());p.teleportTo(l,p.getX(),p.getY(),p.getZ(),-90,10);
  p.getInventory().setItem(0,new ItemStack(Items.DIAMOND_CHESTPLATE));npc=x.getUUID();before=score(s);ready=true;
  LogUtils.getLogger().info("ASTRA_GIFT fixture resident {} ({}) score {}",x.getDisplayName().getString(),r.profession(),before);
 }
 private static ResidentEntity found(Minecraft mc){for(var entity:mc.level.entitiesForRendering())if(entity.getUUID().equals(npc)&&entity instanceof ResidentEntity r)return r;return null;}
 private static void click(Minecraft mc,net.minecraft.client.gui.components.Button b){require(b!=null&&b.visible&&b.active,"Gift button inactive");int x=b.getX()+b.getWidth()/2,y=b.getY()+b.getHeight()/2;if(!mc.screen.mouseClicked(x,y,0))throw new IllegalStateException("Gift button missed at "+x+","+y);mc.screen.mouseReleased(x,y,0);}
 /** The strip lies below the last offer row and above the bottom buttons, inside the card and the window. */
 private static void layout(Minecraft mc,TradeScreen screen){
  int top=screen.top(),h=screen.cardHeight(),strip=top+h-TradeScreen.STRIP,rowsEnd=top+TradeScreen.ROW0+screen.rows()*TradeScreen.ROW_H+9;var b=screen.giftButton();
  require(rowsEnd<=strip-4,"Rows overlap the gift strip: "+rowsEnd+" > "+strip);require(b.getY()+b.getHeight()<=top+h-26,"Gift button overlaps the bottom buttons");
  require(top>=0&&top+h<=mc.getWindow().getGuiScaledHeight()&&screen.left()>=0,"Card outside the window "+mc.getWindow().getGuiScaledWidth()+"x"+mc.getWindow().getGuiScaledHeight());
  LogUtils.getLogger().info("ASTRA_GIFT layout window={}x{} card={} rows={} strip={} rowsEnd={}",mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),h,screen.rows(),strip,rowsEnd);
 }
 /** Sets the largest (tall card) or the first (short card) GUI scale that fits; false when this window has none. */
 private static boolean scale(Minecraft mc,boolean wantTall){if(guiScale<0)guiScale=mc.options.guiScale().get();int max=mc.getWindow().calculateScale(0,mc.isEnforceUnicode()),chosen=0;
  for(int s=1;s<=max;s++){int w=mc.getWindow().getWidth()/s,h=mc.getWindow().getHeight()/s;if(w<TradeScreen.WIDTH)continue;if(wantTall&&h>=TradeScreen.HEIGHT+8)chosen=s;if(!wantTall&&h<TradeScreen.HEIGHT+8&&chosen==0)chosen=s;}
  if(chosen==0)return false;mc.options.guiScale().set(chosen);mc.resizeDisplay();return true;}
 static void tick(Minecraft mc){try{
  // AD-146: a click on a resident opens the conversation first; the card is its «Торговать и дарить».
  if(mc.screen instanceof DialogScreen)DialogScreen.pick("resident/trade");

  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>3000)throw new IllegalStateException("Gift probe timeout phase="+phase);
  if(phase==0&&ticks>60){phase=1;ticks=0;server(mc,GiftProbe::fixture);}
  else if(phase==1&&ready&&ticks>40){var x=found(mc);if(x==null){require(ticks<400,"Resident not visible");return;}tall=scale(mc,true);mc.gameMode.interact(mc.player,x,InteractionHand.MAIN_HAND);phase=2;ticks=0;}
  else if(phase==2&&mc.screen instanceof TradeScreen screen&&ticks>30){var g=screen.view().getCompound("gift");
   require(g.getLong("reputation")==1024&&g.getString("destination").equals("wear")&&!g.getBoolean("blocked")&&g.getBoolean("confirm"),"Chestplate quote "+g);if(tall)require(screen.cardHeight()==TradeScreen.HEIGHT&&screen.rows()==TradeScreen.ROWS,"A tall window must give the tall card");layout(mc,screen);capture(mc,"card");
   click(mc,screen.giftButton());require(screen.giftButton().getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("trade.villageastra.gift.confirm").getString()),"First click must ask for confirmation");
   click(mc,screen.giftButton());phase=3;ticks=0;}
  else if(phase==2&&ticks>200)throw new IllegalStateException("Card did not open");
  else if(phase==3&&mc.screen instanceof TradeScreen screen&&screen.view().getString("result").equals("gift:ok")){
   require(screen.view().getCompound("gift").getLong("gained")==1024,"Gain shown "+screen.view().getCompound("gift"));checked=false;
   server(mc,s->{var p=player(s);var x=(ResidentEntity)s.overworld().getEntity(npc);require(p.getMainHandItem().isEmpty(),"Hand not emptied");require(x.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),"Resident does not wear it");
    require(score(s)-before==1024,"Score delta "+(score(s)-before));require(GiftLedger.get(s).deals()>=1&&GiftLedger.get(s).worn(npc)!=null,"Ledger lacks the deal");
    // Stand three blocks in front of the resident, looking at him.
    p.teleportTo(s.overworld(),x.getX()-3,x.getY(),x.getZ(),-90,5);checked=true;});
   mc.setScreen(null);phase=4;ticks=0;}
  else if(phase==3&&mc.screen instanceof TradeScreen screen&&!screen.view().getString("result").isEmpty()&&!screen.view().getString("result").equals("gift:ok"))throw new IllegalStateException("Gift result "+screen.view().getString("result"));
  else if(phase==4&&checked&&ticks>80){var x=found(mc);require(x!=null&&x.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),"Client does not see the chestplate");capture(mc,"worn");
   ready=false;server(mc,s->{var p=player(s);var pick=new ItemStack(Items.DIAMOND_PICKAXE);pick.enchant(Enchantments.BLOCK_EFFICIENCY,5);pick.setDamageValue(780);p.getInventory().setItem(0,pick);p.getInventory().selected=0;
    var body=(ResidentEntity)s.overworld().getEntity(npc);p.teleportTo(s.overworld(),body.getX()-2,body.getY(),body.getZ(),-90,10);ready=true;});phase=5;ticks=0;}
  else if(phase==5&&ready&&ticks>30){mc.gameMode.interact(mc.player,found(mc),InteractionHand.MAIN_HAND);phase=6;ticks=0;}
  else if(phase==6&&mc.screen instanceof TradeScreen screen&&ticks>20&&screen.view().getCompound("gift").getString("item").equals("minecraft:diamond_pickaxe")){var g=screen.view().getCompound("gift");
   require(g.getLong("reputation")==288&&g.getBoolean("enchanted")&&g.getBoolean("confirm")&&g.getString("destination").equals("hall_tools"),"Pickaxe quote "+g);
   // The short card: a GUI scale that leaves the window under 262 scaled pixels high.
   small=scale(mc,false);
   phase=7;ticks=0;}
  else if(phase==7&&mc.screen instanceof TradeScreen screen&&ticks>20){
   if(small)require(screen.cardHeight()==TradeScreen.SHORT&&screen.rows()==4,"Short window must give the short card");
   layout(mc,screen);capture(mc,"card-small");click(mc,screen.giftButton());click(mc,screen.giftButton());phase=8;ticks=0;}
  else if(phase==8&&mc.screen instanceof TradeScreen screen&&screen.view().getString("result").equals("gift:ok")){checked=false;
   server(mc,s->{var hall=hall(s);boolean pick=false;for(int i=0;i<hall.getContainerSize();i++){var it=hall.getItem(i);if(it.is(Items.DIAMOND_PICKAXE)&&it.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY)==5&&it.getDamageValue()==780)pick=true;}
    require(pick,"The worn enchanted pickaxe is not in the hall chest");long total=GiftLedger.get(s).total(village,player(s).getUUID());require(total==1312,"Gift total "+total);checked=true;});phase=9;ticks=0;}
  else if(phase==8&&mc.screen instanceof TradeScreen screen&&!screen.view().getString("result").isEmpty()&&!screen.view().getString("result").equals("gift:ok"))throw new IllegalStateException("Pickaxe result "+screen.view().getString("result"));
  else if(phase==9&&checked){if(guiScale>=0){mc.options.guiScale().set(guiScale);mc.resizeDisplay();}LogUtils.getLogger().info("ASTRA_GIFT VERIFIED card=1 worn=1 total=1312 tall={} short={}",tall,small);mc.setScreen(null);mc.stop();phase=10;}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_GIFT FAILED",ex);mc.stop();phase=10;}}
}
