package org.villageastra.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.client.OfficeUi.OfficeButton;
import org.villageastra.client.OfficeUi.Rect;
import org.villageastra.client.OfficeUi.Tone;
/** The way into the player's own errands: a tab over the survival inventory, beside its own tabs, with the number of errands on it.
 *  It is a tab of the game's own screen, so it opens wherever the player is — no hall, no board, no standing in the right place. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID,value=Dist.CLIENT)
public final class QuestTab {
 private QuestTab(){}
 @SubscribeEvent public static void opened(ScreenEvent.Init.Post event){
  if(!(event.getScreen() instanceof InventoryScreen screen))return;
  QuestLogScreen.ask();
  // Over the inventory's own frame, on its left top corner, where the recipe book's button is not.
  int x=screen.getGuiLeft(),y=screen.getGuiTop()-21;
  var button=OfficeButton.of(Component.translatable("quest.villageastra.log_tab"),new ItemStack(Items.WRITABLE_BOOK),Tone.INFO,
    b->Minecraft.getInstance().setScreen(new QuestLogScreen(screen)))
   .hint(Component.translatable("quest.villageastra.log_tab_tip"));
  int w=Math.max(60,button.prefWidth(Minecraft.getInstance().font));
  if(y<2){y=screen.getGuiTop()+2;x=screen.getGuiLeft()-w-4;if(x<2){x=2;y=2;}}
  event.addListener(button.at(new Rect(x,y,w,18)));
 }
}
