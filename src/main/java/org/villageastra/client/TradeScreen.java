package org.villageastra.client;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.ElectionRoll;
import org.villageastra.server.ConstructionNetwork;
/** AD-036/AD-050: dialog card of one resident — live portrait, standing and purse on the left, what the village buys and sells in two panels. Counts and prices on the buttons are exactly what the order confirms.
 *  AD-128: a gift strip above the bottom buttons quotes the item in the main hand; the card is 254 high on screens that fit it and 228 (four rows) otherwise, so nothing overlaps. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class TradeScreen extends Screen {
 public static final int WIDTH=416,HEIGHT=254,SHORT=228,ROWS=5,ROW0=66,ROW_H=23,PANEL=188,STRIP=54;
 /** Button geometry of an offer row, shared with the probe so a click in a test lands exactly where a player clicks. */
 public static final int SELL_X=104,SELL_W=48,DONATE_X=156,DONATE_W=32,BUY_X=WIDTH-58,BUY_W=50,BUTTON_H=18,GIFT_X=WIDTH-84,GIFT_W=76;
 private CompoundTag tag;private int scrollBuys,scrollSells;private LivingEntity portrait;private int portraitAge;private int h=HEIGHT,rows=ROWS;private boolean confirming;private Button giftButton;
 /** A sent gift waits for the server's answer (or 2 s): a second click must not send the same quote again and come back «stale». */
 private boolean sent;private int sentTicks;
 TradeScreen(CompoundTag tag){super(Component.translatable("trade.villageastra.card"));this.tag=tag;}
 @SubscribeEvent public static void opened(ConstructionNetwork.TradeOpened event){var mc=Minecraft.getInstance();if(mc.player==null)return;
  if(mc.screen instanceof TradeScreen s&&s.tag.getUUID("npc").equals(event.tag.getUUID("npc"))){s.tag=event.tag;s.confirming=false;s.sent=false;s.rebuildWidgets();}else mc.setScreen(new TradeScreen(event.tag));}
 CompoundTag view(){return tag;}
 /** Card geometry for the current window, shared with the probes. */
 int left(){return (width-WIDTH)/2;}
 int top(){return Math.max(4,(height-h)/2);}
 int rows(){return rows;}
 int cardHeight(){return h;}
 Button giftButton(){return giftButton;}
 private void layout(){h=height>=HEIGHT+8?HEIGHT:SHORT;rows=h==HEIGHT?ROWS:4;}
 private static net.minecraft.network.chat.MutableComponent text(String key,Object... args){return Component.translatable("trade.villageastra."+key,args);}
 private static ItemStack stack(CompoundTag line){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(line.getString("item"))));}
 private static int gcd(int a,int b){return b==0?a:gcd(b,a%b);}
 /** Largest amount the village accepts and the player holds that converts to whole coins without loss. */
 static int saleCount(CompoundTag line){int n=(line.getBoolean("creativeSource")?Math.min(64*36,line.getInt("count")):Math.min(line.getInt("count"),line.getInt("have"))),step=line.getInt("per")/gcd(line.getInt("price"),line.getInt("per"));return n-n%step;}
 static int donationCount(CompoundTag line){return line.getBoolean("creativeSource")?Math.min(64*36,line.getInt("count")):Math.min(line.getInt("count"),line.getInt("have"));}
 static int lot(CompoundTag line){return Math.min(line.getInt("per"),line.getInt("count"));}
 static long cost(CompoundTag line,int count,int markup){return Math.max(1,-Math.floorDiv(-(long)count*line.getInt("price")*markup,(long)line.getInt("per")*100));}
 private void order(int side,String item,int count){ConstructionNetwork.sendTrade(new ConstructionNetwork.TradeOrder(UUID.randomUUID(),tag.getUUID("npc"),side,item,count));}
 private CompoundTag gift(){return tag.getCompound("gift");}
 private boolean hasGift(){return tag.contains("gift",Tag.TAG_COMPOUND);}
 /** First click on a valuable or enchanted gift asks again; the second sends the quote exactly as shown. */
 private void giveClicked(){var g=gift();if(g.getBoolean("blocked")||sent)return;if(g.getBoolean("confirm")&&!confirming){confirming=true;rebuildWidgets();return;}
  confirming=false;sent=true;sentTicks=0;if(giftButton!=null)giftButton.active=false;ConstructionNetwork.sendGift(new ConstructionNetwork.GiftOrder(UUID.randomUUID(),tag.getUUID("npc"),g.getInt("slot"),g.getString("item"),g.getInt("count"),g.getLong("reputation")));}
 /** The resident standing in front of the player, looked up once a second: the card shows the actual villager, not a stock figure. */
 private LivingEntity portrait(){
  if(portrait!=null&&portrait.isAlive()&&portraitAge-->0)return portrait;
  portraitAge=20;portrait=null;
  if(minecraft==null||minecraft.level==null)return null;
  for(var entity:minecraft.level.entitiesForRendering())
   if(entity instanceof LivingEntity living&&living.getUUID().equals(tag.getUUID("npc"))){portrait=living;break;}
  return portrait;
 }
 private ListTag buys(){return tag.getList("buys",Tag.TAG_COMPOUND);}
 private ListTag sells(){return tag.getList("sells",Tag.TAG_COMPOUND);}
 private int scroll(boolean buying){return Math.max(0,Math.min((buying?buys():sells()).size()-rows,buying?scrollBuys:scrollSells));}
 @Override protected void init(){layout();int x=left(),y=top();giftButton=null;
  if(tag.getString("reason").isEmpty()){
   var buys=buys();int from=scroll(true);
   for(int i=0;i<Math.min(rows,buys.size()-from);i++){var line=buys.getCompound(from+i);int sale=saleCount(line),gift=donationCount(line),ry=y+ROW0+i*ROW_H;
    var sell=addRenderableWidget(Button.builder(text("sell"),b->order(0,line.getString("item"),sale)).bounds(x+SELL_X,ry,SELL_W,BUTTON_H)
     .tooltip(net.minecraft.client.gui.components.Tooltip.create(text("need",line.getInt("count"),line.getBoolean("creativeSource")?"∞":line.getInt("have")))).build());sell.active=sale>0;
    var donate=addRenderableWidget(Button.builder(text("donate"),b->order(2,line.getString("item"),gift)).bounds(x+DONATE_X,ry,DONATE_W,BUTTON_H)
     .tooltip(net.minecraft.client.gui.components.Tooltip.create(text("gift",gift))).build());donate.active=gift>0;}
   var sells=sells();int fromSells=scroll(false);long wallet=tag.getInt("coins")+tag.getLong("owed");
   for(int i=0;i<Math.min(rows,sells.size()-fromSells);i++){var line=sells.getCompound(fromSells+i);int lot=lot(line),ry=y+ROW0+i*ROW_H;
    var buy=addRenderableWidget(Button.builder(text("buy"),b->order(1,line.getString("item"),lot)).bounds(x+BUY_X,ry,BUY_W,BUTTON_H)
     .tooltip(net.minecraft.client.gui.components.Tooltip.create(text("stock",line.getInt("count")))).build());
    buy.active=lot>0&&line.getInt("room")>=lot&&wallet>=cost(line,lot,tag.getInt("markup"));}
  }
  if(hasGift()){var g=gift();var label=confirming?text("gift.confirm").withStyle(ChatFormatting.RED):text("gift.give");
   giftButton=addRenderableWidget(Button.builder(label,b->giveClicked()).bounds(x+GIFT_X,y+h-STRIP+3,GIFT_W,BUTTON_H).build());giftButton.active=!g.getBoolean("blocked")&&!sent;}
  if(tag.getLong("owed")>0)addRenderableWidget(Button.builder(text("claim",tag.getLong("owed")),b->order(3,"minecraft:air",0)).bounds(x+8,y+h-26,128,18).build());
  addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+WIDTH-70,y+h-26,62,18).build());
 }
 /** Text cut to a width with an ellipsis, so nothing is ever drawn under a neighbour. */
 private String clip(String s,int w){return font.width(s)<=w?s:font.plainSubstrByWidth(s,Math.max(0,w-font.width("…")))+"…";}
 /** One offer row: icon, name, the exact amount and its price, on a striped background that lights up under the cursor. */
 private void row(GuiGraphics g,CompoundTag line,int x,int y,int w,int mx,int my,Component label,boolean dim){
  boolean hover=mx>=x&&mx<x+w&&my>=y&&my<y+ROW_H-2;
  g.fill(x,y,x+w,y+ROW_H-2,hover?0x30FFFFFF:0x18FFFFFF);
  g.renderItem(stack(line),x+3,y+1);
  // The name is clipped to the space before the buttons, so nothing is ever drawn under them.
  int room=Math.max(24,SELL_X-27);
  g.drawString(font,clip(stack(line).getHoverName().getString(),room),x+23,y+1,dim?0xFF8A948F:0xFFE7EDE9);
  g.drawString(font,clip(label.getString(),room),x+23,y+11,dim?0xFF6E7873:0xFFA9C0B4);
 }
 private void bar(GuiGraphics g,int x,int y,int w,int h,double fill,int back,int front){
  g.fill(x,y,x+w,y+h,back);g.fill(x,y,x+(int)Math.round(w*Math.max(0,Math.min(1,fill))),y+h,front);
 }
 static String factor(int permille){return org.villageastra.domain.GiftValue.factor(permille);}
 private static String coins(long permille){return permille%1000==0?Long.toString(permille/1000):permille/1000+","+String.format("%03d",permille%1000).replaceAll("0+$","");}
 /** AD-128: the gift strip — the item in hand, its worth in reputation, each factor and where it goes, or why it is not a gift. */
 private void giftStrip(GuiGraphics g,int x,int y,int mx,int my){
  var gift=gift();int sy=y+h-STRIP,tx=x+30,w=GIFT_X-30-6;
  g.fill(x+6,sy,x+WIDTH-6,sy+24,0x1EE9CD92);g.fill(x+6,sy,x+WIDTH-6,sy+1,0x60B89B65);
  var stack=ItemStack.of(gift.getCompound("stack"));String reason=gift.getString("reason");
  if(!stack.isEmpty())g.renderItem(stack,x+10,sy+4);
  if(stack.isEmpty()){g.drawString(font,clip(text("gift.reason.empty").getString(),w+20),x+12,sy+8,0xFF8A948F);return;}
  var name=stack.getHoverName().getString();int count=gift.getInt("count"),accepted=gift.getInt("accepted");
  if(gift.getBoolean("blocked")){
   g.drawString(font,clip(name,w),tx,sy+3,0xFF8A948F);g.drawString(font,clip(text("gift.reason."+reason).getString(),w),tx,sy+13,0xFFDD9C66);return;}
  var amount=accepted<count?text("gift.part",accepted,count).getString():"×"+count;
  g.drawString(font,clip(text("gift.line",name,amount,gift.getLong("reputation")).getString(),w),tx,sy+3,0xFFE9CD92);
  String second=reason.isEmpty()?text("gift.factors",gift.getInt("durability")/10,factor(gift.getInt("enchant")),factor(gift.getInt("repeatFirst"))).getString()+" · "+text("gift.dest."+gift.getString("destination")).getString():text("gift.reason."+reason).getString();
  g.drawString(font,clip(second,w),tx,sy+13,reason.isEmpty()?0xFFA9C0B4:0xFFDD9C66);
 }
 private List<Component> giftTooltip(){var gift=gift();var lines=new ArrayList<Component>();if(gift.getString("item").isEmpty())return lines;
  lines.add(text("gift.tip.base",coins(gift.getLong("base")),gift.getInt("accepted")-gift.getInt("free")));
  lines.add(text("gift.tip.durability",gift.getInt("durability")/10));lines.add(text("gift.tip.enchant",factor(gift.getInt("enchant"))));
  lines.add(text("gift.tip.repeat",gift.getLong("remembered"),factor(gift.getInt("repeatFirst")),factor(gift.getInt("repeatLast"))));
  if(gift.getInt("free")>0)lines.add(text("gift.tip.bought",gift.getInt("free")));
  lines.add(text("gift.tip.dest",text("gift.dest."+gift.getString("destination"))));
  lines.add(text("gift.tip.total",gift.getLong("total")));lines.add(text("gift.tip.final").withStyle(ChatFormatting.GRAY));return lines;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){int x=left(),y=top();
  renderBackground(g);
  g.fill(x-1,y-1,x+WIDTH+1,y+h+1,0xFFB89B65);g.fill(x,y,x+WIDTH,y+h,0xF4161E23);
  g.fill(x,y,x+WIDTH,y+46,0xFF24352F);g.fill(x,y+46,x+WIDTH,y+47,0xFF3C5A4E);
  // Left: who this is. The portrait is the resident itself, so the card matches the villager standing in front of the player.
  g.fill(x+6,y+6,x+46,y+40,0xFF10181C);
  var npc=portrait();
  if(npc!=null)InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+26,y+40,16,x+26-mx,y+10-my,npc);
  var role=tag.getBoolean("child")?text("child"):tag.getString("profession").isEmpty()?text("no_profession"):Component.translatable("profession.villageastra."+tag.getString("profession"));
  int metrics=x+WIDTH-150,nameRoom=WIDTH-150-52-6;
  g.drawString(font,clip(tag.getString("name"),nameRoom),x+52,y+8,0xFFE9CD92);
  g.drawString(font,clip(role.getString(),nameRoom),x+52,y+19,0xFFA9C0B4);
  if(!tag.getString("status").isEmpty())g.drawString(font,clip(Component.translatable("work.villageastra."+tag.getString("status")).getString(),nameRoom),x+52,y+30,0xFF7F9188);
  // Right of the header: standing with the village against the mayor's threshold, purse and what the village still owes.
  g.drawString(font,text("reputation",tag.getLong("reputation")),metrics,y+8,0xFFC4CAC7);
  bar(g,metrics,y+18,142,4,tag.getLong("reputation")/(double)ElectionRoll.MINIMUM,0xFF2B3A33,tag.getLong("reputation")>=ElectionRoll.MINIMUM?0xFF8FD6A0:0xFF5E9E7A);
  g.renderItem(new ItemStack(VillageAstra.ZINDBO.get()),metrics,y+24);
  g.drawString(font,tag.getLong("owed")>0?text("purse_owed",tag.getInt("coins"),tag.getLong("owed")):text("purse",tag.getInt("coins")),metrics+20,y+28,0xFFE9CD92);
  ListTag buys=buys(),sells=sells();int from=scroll(true),fromSells=scroll(false);
  if(!tag.getString("reason").isEmpty())g.drawWordWrap(font,text("reason."+tag.getString("reason")),x+10,y+60,WIDTH-20,0xFFDD9C66);
  else{
   // Two panels: what the village is short of, and what it can spare. They end above the gift strip.
   int bottom=y+h-STRIP-4;
   g.fill(x+6,y+50,x+6+PANEL,bottom,0x14FFFFFF);g.fill(x+WIDTH-6-PANEL,y+50,x+WIDTH-6,bottom,0x14FFFFFF);
   g.drawString(font,text("buys"),x+10,y+54,0xFFE9CD92);g.drawString(font,text("sells"),x+WIDTH-2-PANEL,y+54,0xFFE9CD92);
   var markup=text("markup",tag.getInt("markup"));g.drawString(font,markup,x+WIDTH-10-font.width(markup),y+54,0xFF7F9188);
   if(buys.isEmpty())g.drawString(font,text("buys_empty"),x+10,y+ROW0+4,0xFF8A948F);
   for(int i=0;i<Math.min(rows,buys.size()-from);i++){var line=buys.getCompound(from+i);int ry=y+ROW0+i*ROW_H,sale=saleCount(line);
    row(g,line,x+8,ry,PANEL-4,mx,my,text("buy_line",sale>0?sale:donationCount(line),sale*(long)line.getInt("price")/Math.max(1,line.getInt("per"))),sale==0);}
   if(sells.isEmpty())g.drawString(font,text("sells_empty"),x+WIDTH-2-PANEL,y+ROW0+4,0xFF8A948F);
   for(int i=0;i<Math.min(rows,sells.size()-fromSells);i++){var line=sells.getCompound(fromSells+i);int ry=y+ROW0+i*ROW_H,lot=lot(line);
    row(g,line,x+WIDTH-4-PANEL,ry,PANEL-4,mx,my,text("sell_line",lot,cost(line,lot,tag.getInt("markup"))),lot==0||line.getInt("room")<lot);}
   // Rows hidden below: the hint sits under the last row, inside its panel.
   int hint=y+ROW0+rows*ROW_H;
   if(buys.size()-rows-from>0)g.drawString(font,clip(text("more",buys.size()-rows-from).getString(),PANEL-8),x+10,hint,0xFF7F9188);
   if(sells.size()-rows-fromSells>0)g.drawString(font,clip(text("more",sells.size()-rows-fromSells).getString(),PANEL-8),x+WIDTH-2-PANEL,hint,0xFF7F9188);
  }
  if(hasGift())giftStrip(g,x,y,mx,my);
  // Bottom line between the claim and done buttons: the last result, or the mayor's note.
  int noteX=x+142,noteW=WIDTH-70-142-6;var result=tag.getString("result");
  if(tag.getBoolean("mayor")&&!tag.getBoolean("creativeSource"))g.drawString(font,clip(text("mayor_note").getString(),noteW),noteX,y+h-24,0xFF7F9188);
  if(!result.isEmpty()){var msg=result.equals("gift:ok")?text("result.gift.ok",gift().getLong("gained")):text("result."+result.replace(':','.'));
   g.drawString(font,clip(msg.getString(),noteW),noteX,y+h-13,result.equals("ok")||result.equals("gift:ok")?0xFF9FD39A:0xFFDD9C66);}
  super.render(g,mx,my,dt);
  if(tag.getString("reason").isEmpty())for(boolean buying:List.of(true,false)){var lines=buying?buys:sells;int ix=buying?x+8:x+WIDTH-4-PANEL,start=buying?from:fromSells;
   for(int i=0;i<Math.min(rows,lines.size()-start);i++){int ry=y+ROW0+i*ROW_H;
    if(mx>=ix&&mx<ix+20&&my>=ry&&my<ry+ROW_H-2)g.renderTooltip(font,stack(lines.getCompound(start+i)),mx,my);}}
  int sy=y+h-STRIP;if(hasGift()&&mx>=x+6&&mx<x+WIDTH-6&&my>=sy&&my<sy+24){var tip=giftTooltip();if(!tip.isEmpty())g.renderComponentTooltip(font,tip,mx,my);}
 }
 @Override public boolean mouseScrolled(double mx,double my,double delta){
  int x=left();
  if(mx<x+WIDTH/2.0)scrollBuys=Math.max(0,Math.min(Math.max(0,buys().size()-rows),scrollBuys-(int)Math.signum(delta)));
  else scrollSells=Math.max(0,Math.min(Math.max(0,sells().size()-rows),scrollSells-(int)Math.signum(delta)));
  rebuildWidgets();return true;
 }
 @Override public void tick(){super.tick();if(sent&&++sentTicks>40){sent=false;rebuildWidgets();}}
 @Override public boolean isPauseScreen(){return false;}
}
