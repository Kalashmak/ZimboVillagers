package org.villageastra.client;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
/** AD-062: the research tree, drawn as the office's Science tab — the same frame, title, role and tab row as ConstructionScreen,
 *  so another tab switches back to the office and Done closes both (there is no 'Back'). */
public final class ResearchScreen extends Screen {
 private Layout layout;private List<TabButton> tabs=List.of();private ResearchPanel panel;private OfficeButton atlas,help,done;
 public ResearchScreen(){super(Component.translatable("research.villageastra.tree_title"));}
 /** The office passes itself; nothing goes back to it any more, the tabs and Done replace 'Back'. */
 public ResearchScreen(Screen back){this();}
 /** Another tab asks for a technology: the next research tree selects it and brings it into view. */
 public static void focus(String nodeId){ResearchPanel.focus(nodeId);}
 ResearchPanel panel(){return panel;}
 Layout layout(){return layout;}
 List<TabButton> tabs(){return tabs;}
 OfficeButton doneButton(){return done;}
 OfficeButton helpButton(){return help;}
 OfficeButton atlasButton(){return atlas;}
 @Override protected void init(){
  layout=new Layout(width,height);
  tabs=ConstructionScreen.tabRow(layout,font,ConstructionScreen.RESEARCH,this::select);tabs.forEach(this::addRenderableWidget);
  panel=new ResearchPanel(font,layout,this::addRenderableWidget);
  atlas=addRenderableWidget(OfficeButton.of(Component.translatable("atlas.villageastra.open"),new ItemStack(Items.FILLED_MAP),Tone.INFO,b->minecraft.setScreen(new AtlasScreen())).at(layout.atlas()));
  help=addRenderableWidget(ConstructionScreen.helpButton(layout,ConstructionScreen.RESEARCH));
  done=addRenderableWidget(OfficeButton.of(Component.translatable("gui.done"),ItemStack.EMPTY,Tone.INFO,b->onClose()).at(layout.done()));
  OfficeUi.disarm(children());panel.tick(true);
 }
 private void select(int section){if(section!=ConstructionScreen.RESEARCH)minecraft.setScreen(new ConstructionScreen(section));}
 /** The canvas, details column and label column rules, found by name by OfficeProbes.layoutProblems. */
 List<String> layoutProblems(){return panel==null?List.of("research panel not built"):panel.layoutProblems();}
 @Override public void tick(){if(panel!=null)panel.tick(true);}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  ConstructionScreen.chrome(g,font,layout);panel.render(g,font,mx,my);super.render(g,mx,my,partial);
  ConstructionScreen.badges(g,font,tabs);ConstructionScreen.pulseHelp(g,help,mx,my);OfficeUi.tips().render(g,font,mx,my,this);
 }
 @Override public boolean mouseClicked(double x,double y,int button){
  if(super.mouseClicked(x,y,button))return true;
  // A click on the tree takes the keyboard from the search box, so the tree's keys work again.
  if(panel.click(x,y,button)){setFocused(null);panel.search().setFocused(false);return true;}return false;
 }
 @Override public boolean mouseReleased(double x,double y,int button){panel.release();return super.mouseReleased(x,y,button);}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return panel.drag(x,y,dx,dy)||super.mouseDragged(x,y,button,dx,dy);}
 /** AD-124: the wheel scrolls rows, Shift+wheel columns, Ctrl (Cmd on macOS) the density. */
 @Override public boolean mouseScrolled(double x,double y,double delta){return panel.scroll(x,y,delta,hasShiftDown(),hasControlDown());}
 private boolean swallowSlash;
 /** AD-124: keys go to the search while it is typed in (Esc clears it and lets go, Enter walks the matches); otherwise the tree's keys
  *  come before the screen's own (which would move focus with the arrows), and Esc still closes the office. */
 @Override public boolean keyPressed(int key,int scan,int mods){
  if(panel.searchFocused()){
   if(key==256){panel.leaveSearch();setFocused(null);return true;}
   if(key==257||key==335){panel.nextMatch();return true;}
   return super.keyPressed(key,scan,mods);}
  if(key==47||key==70&&hasControlDown()){setFocused(panel.search());panel.search().setFocused(true);swallowSlash=key==47;return true;}
  if(panel.key(key,mods))return true;
  return super.keyPressed(key,scan,mods);
 }
 @Override public boolean charTyped(char c,int mods){if(swallowSlash&&c=='/'){swallowSlash=false;return true;}swallowSlash=false;return super.charTyped(c,mods);}
 /** Done and Escape close the whole office, as they do on every other tab. */
 @Override public void onClose(){OfficeUi.disarm(children());minecraft.setScreen(null);}
 @Override public void removed(){OfficeUi.disarm(children());}
 @Override public boolean isPauseScreen(){return false;}
}
