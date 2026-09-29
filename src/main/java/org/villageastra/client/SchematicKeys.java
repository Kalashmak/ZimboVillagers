package org.villageastra.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.villageastra.VillageAstra;
/** AD-126: keys of the construction schematic, all rebindable — B shows or hides it (HUD too), N cycles the layer mode, PageUp/PageDown
 *  pick a layer by hand, Home drops the hand-picked layer. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SchematicKeys {
 private SchematicKeys(){}
 public static final String CATEGORY="key.categories.villageastra.play";
 private static KeyMapping key(String name,int code){return new KeyMapping(name,KeyConflictContext.IN_GAME,KeyModifier.NONE,InputConstants.Type.KEYSYM,code,CATEGORY);}
 // C10: B reuses the existing «key.villageastra.overlay».
 public static final KeyMapping TOGGLE=key("key.villageastra.overlay",GLFW.GLFW_KEY_B);
 public static final KeyMapping LAYERS=key("key.villageastra.schematic_layers",GLFW.GLFW_KEY_N);
 public static final KeyMapping UP=key("key.villageastra.schematic_up",GLFW.GLFW_KEY_PAGE_UP);
 public static final KeyMapping DOWN=key("key.villageastra.schematic_down",GLFW.GLFW_KEY_PAGE_DOWN);
 public static final KeyMapping RESET=key("key.villageastra.schematic_reset",GLFW.GLFW_KEY_HOME);
 @SubscribeEvent public static void register(RegisterKeyMappingsEvent e){e.register(TOGGLE);e.register(LAYERS);e.register(UP);e.register(DOWN);e.register(RESET);}

 @Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
 public static final class Handler{
  private Handler(){}
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
   if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.player==null)return;
   while(TOGGLE.consumeClick()){
    // Ctrl+B is the narrator's, Alt+B someone else's: only a plain B toggles the schematic.
    if(Screen.hasControlDown()||Screen.hasAltDown())continue;
    ConstructionOverlay.visible=!ConstructionOverlay.visible;say(mc,Component.translatable(ConstructionOverlay.visible?"schematic.villageastra.shown":"schematic.villageastra.hidden"));}
   while(LAYERS.consumeClick()){SchematicRenderer.mode=SchematicRenderer.mode.next();SchematicRenderer.manualY=SchematicModel.NONE;say(mc,modeName());}
   var model=SchematicRenderer.MODEL;var b=model.bounds();
   while(UP.consumeClick())if(!model.empty()){int y=SchematicRenderer.manualY==SchematicModel.NONE?start(model):Math.min(b[4],SchematicRenderer.manualY+1);SchematicRenderer.manualY=y;say(mc,Component.translatable("schematic.villageastra.layer",y));}
   while(DOWN.consumeClick())if(!model.empty()){int y=SchematicRenderer.manualY==SchematicModel.NONE?start(model):Math.max(b[1],SchematicRenderer.manualY-1);SchematicRenderer.manualY=y;say(mc,Component.translatable("schematic.villageastra.layer",y));}
   while(RESET.consumeClick()){SchematicRenderer.manualY=SchematicModel.NONE;say(mc,modeName());}
  }
 }
 private static int start(SchematicModel m){return m.hasTarget()?m.targetY():m.bounds()[4];}
 static Component modeName(){return SchematicRenderer.manualY!=SchematicModel.NONE?Component.translatable("schematic.villageastra.layer",SchematicRenderer.manualY):Component.translatable("schematic.villageastra.mode."+SchematicRenderer.mode.name().toLowerCase(java.util.Locale.ROOT));}
 private static void say(Minecraft mc,Component c){mc.player.displayClientMessage(c,true);}
}
