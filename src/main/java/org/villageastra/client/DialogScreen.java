package org.villageastra.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.dialog.DialogNetwork;
/** AD-146: the window of a conversation with a resident — the resident itself as its portrait, its name and trade, what it says, and the
 *  player's answers as buttons, one under another. The server decides everything: an answer is sent as its id, the next page comes back.
 *  Esc or «До свидания» ends the conversation. Drawn in the colours of the resident's card (TradeScreen). */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class DialogScreen extends Screen {
 private static final int WIDTH=300,HEAD=46,PAD=8,BUTTON=18,GAP=3;
 private CompoundTag page;private boolean answered;
 private LivingEntity portrait;private int portraitAge;
 DialogScreen(CompoundTag page){super(Component.translatable("dialog.villageastra.title"));this.page=page;}
 @SubscribeEvent public static void shown(DialogNetwork.Shown event){var mc=Minecraft.getInstance();if(mc.player==null)return;
  if(mc.screen instanceof DialogScreen s){s.page=event.page;s.answered=false;s.portrait=null;s.rebuildWidgets();}else mc.setScreen(new DialogScreen(event.page));}
 @SubscribeEvent public static void ended(DialogNetwork.Ended event){var mc=Minecraft.getInstance();if(mc.screen instanceof DialogScreen s){s.answered=true;s.onClose();}}
 /** The page shown now (probes). */
 static CompoundTag current(){return Minecraft.getInstance().screen instanceof DialogScreen s?s.page:null;}
 private long serial(){return page.getLong("serial");}
 /** Probes: answers the open conversation with this option, as a click on its button; false when no such window or answer. */
 static boolean pick(String id){if(!(Minecraft.getInstance().screen instanceof DialogScreen s)||s.answered)return false;
  for(var raw:s.options())if(((CompoundTag)raw).getString("id").equals(id)&&((CompoundTag)raw).getBoolean("enabled")){s.answer(id);return true;}return false;}
 private static Component json(String s){var c=s==null||s.isEmpty()?null:Component.Serializer.fromJson(s);return c==null?Component.empty():c;}
 private List<FormattedCharSequence> lines(){var out=new ArrayList<FormattedCharSequence>();int room=WIDTH-2*PAD;
  for(var raw:page.getList("lines",Tag.TAG_STRING)){if(!out.isEmpty())out.add(FormattedCharSequence.EMPTY);out.addAll(font.split(json(raw.getAsString()),room));}return out;}
 private ListTag options(){return page.getList("options",Tag.TAG_COMPOUND);}
 private int height(){return HEAD+PAD+lines().size()*(font.lineHeight+1)+PAD+options().size()*(BUTTON+GAP)+PAD;}
 private int left(){return (width-WIDTH)/2;}
 private int top(){return Math.max(8,(height-height())/2);}
 @Override protected void init(){
  int x=left(),y=top()+HEAD+PAD+lines().size()*(font.lineHeight+1)+PAD;
  for(var raw:options()){var o=(CompoundTag)raw;var id=o.getString("id");
   var b=Button.builder(json(o.getString("label")),btn->answer(id)).bounds(x+PAD,y,WIDTH-2*PAD,BUTTON).build();
   b.active=o.getBoolean("enabled");if(o.contains("tooltip"))b.setTooltip(Tooltip.create(json(o.getString("tooltip"))));
   addRenderableWidget(b);y+=BUTTON+GAP;}
 }
 private void answer(String id){if(answered)return;answered=true;DialogNetwork.choose(serial(),id);if(id.startsWith("close/"))onClose();}
 private LivingEntity portrait(){
  if(portrait!=null&&portrait.isAlive()&&portraitAge-->0)return portrait;portraitAge=20;portrait=null;
  if(!page.hasUUID("speaker")||minecraft==null||minecraft.level==null)return null;var id=page.getUUID("speaker");
  for(var entity:minecraft.level.entitiesForRendering())if(entity instanceof LivingEntity living&&living.getUUID().equals(id)){portrait=living;break;}
  return portrait;
 }
 @Override public void render(GuiGraphics g,int mx,int my,float dt){
  renderBackground(g);int x=left(),y=top(),h=height();
  g.fill(x-1,y-1,x+WIDTH+1,y+h+1,0xFFB89B65);g.fill(x,y,x+WIDTH,y+h,0xF4161E23);
  g.fill(x,y,x+WIDTH,y+HEAD,0xFF24352F);g.fill(x,y+HEAD,x+WIDTH,y+HEAD+1,0xFF3C5A4E);
  int textX=x+PAD;
  if(page.hasUUID("speaker")){g.fill(x+6,y+6,x+46,y+40,0xFF10181C);var npc=portrait();
   if(npc!=null)InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+26,y+40,16,x+26-mx,y+10-my,npc);textX=x+52;}
  var name=page.contains("name")?json(page.getString("name")):Component.translatable("dialog.villageastra.title");
  g.drawString(font,name,textX,y+10,0xFFE9CD92,false);
  if(page.contains("role"))g.drawString(font,json(page.getString("role")),textX,y+24,0xFFB7C4BE,false);
  int ly=y+HEAD+PAD;for(var line:lines()){g.drawString(font,line,x+PAD,ly,0xFFEDE6D6,false);ly+=font.lineHeight+1;}
  super.render(g,mx,my,dt);
 }
 @Override public void onClose(){if(!answered)DialogNetwork.closed(serial());answered=true;super.onClose();}
 @Override public boolean isPauseScreen(){return false;}
}
