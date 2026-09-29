package org.villageastra.client;
import java.util.*;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
/** The town hall office: one frame, one tab row and one grid for every tab; management commands still require the current server office.
 *  It opens on the Overview, which lists only what needs the player now. */
public final class ConstructionScreen extends Screen {
 public static final int CONSTRUCTION=0,ELECTIONS=1,BUILDINGS=2,RESEARCH=3,QUESTS=4,WAR=5,LEVELS=6,OVERVIEW=7;
 /** Display order of the tabs; the section numbers 0..6 stay as they were for the workbench and the probes. */
 public static final int[] TAB_ORDER={OVERVIEW,CONSTRUCTION,BUILDINGS,LEVELS,RESEARCH,ELECTIONS,QUESTS,WAR};
 /** The '?' pulses until the player has looked at it once in this game session. */
 private static boolean helpSeen;
 private int section,returnSection=OVERVIEW;private boolean ready;private Layout layout;private List<TabButton> tabs=List.of();private OfficeButton atlas,help,done;
 private OverviewPanel overview;private ConstructionPanel construction;
 private ElectionPanel electionPanel;private BuildingsPanel farmPanel;private QuestPanel questPanel;private WarPanel warPanel;private UpgradePanel upgradePanel;
 public ConstructionScreen(){this(OVERVIEW);}
 public ConstructionScreen(int section){super(Component.translatable("screen.villageastra.construction"));this.section=section<0||section>OVERVIEW?OVERVIEW:section;}
 int section(){return section;}
 UUID selectedBuilding(){return farmPanel==null?null:farmPanel.selected();}
 Layout layout(){return layout;}
 List<TabButton> tabs(){return tabs;}
 OverviewPanel overview(){return overview;}
 Button pauseButton(){return construction.pauseButton();}
 Button callOffButton(){return construction.callOffButton();}
 Button atlasButton(){return atlas;}
 Button doneButton(){return done;}
 Button helpButton(){return help;}
 /** The panel object of a section, for probes that reach a panel's own accessors. */
 Object panel(int s){return switch(s){case CONSTRUCTION->construction;case ELECTIONS->electionPanel;case BUILDINGS->farmPanel;case QUESTS->questPanel;case WAR->warPanel;case LEVELS->upgradePanel;case OVERVIEW->overview;default->null;};}

 // ---- shared chrome, also used by ResearchScreen so the research tree looks like a tab of the office
 public static Component title(CompoundTag t){int level=t.getCompound("overview").contains("hallLevel")?t.getCompound("overview").getInt("hallLevel"):t.getCompound("research").getInt("hall");
  if(level<=0)for(var raw:t.getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.getString("type").equals("town_hall"))level=c.getCompound("upgrade").getInt("kept");}
  // Owner 2026-09-24: the village by its name.
  if(!t.getString("name").isEmpty())return Component.translatable("office.villageastra.title_named",VillageNameText.shown(t.getString("name")),OfficeUi.roman(Math.max(1,level)));
  return Component.translatable("office.villageastra.title",OfficeUi.roman(Math.max(1,level)));}
 public static Component role(CompoundTag t){if(!t.hasUUID("village"))return Component.empty();String why=t.getString("lockReason"),holder=t.getCompound("election").getString("holder");
  return Component.translatable(why.equals("manage")?"office.villageastra.role.mayor":why.equals("unsafe")?"office.villageastra.role.unsafe":holder.isEmpty()?"office.villageastra.role.no_mayor":"office.villageastra.role.viewer",holder);}
 /** Unsafe is red everywhere: orders are closed because of danger. */
 public static Tone roleTone(CompoundTag t){String why=t.getString("lockReason");return why.equals("manage")?Tone.OK:why.equals("unsafe")?Tone.BAD:Tone.OFF;}
 public static void chrome(GuiGraphics g,Font f,Layout l){var t=ConstructionOverlay.snapshot();int end=OfficeUi.frame(g,f,l,new ItemStack(Items.BELL),title(t),role(t),roleTone(t));
  if(l.size().atLeast(OfficeGrid.Size.M))summary(g,f,t,end+10,l.frame().y()+13,l.atlas().x()-8);}
 /** AD-124: at M and L the header carries the village at a glance after the role: residents, days of food, coins — icons and numbers, words in tooltips. */
 private static void summary(GuiGraphics g,Font f,CompoundTag t,int x,int y,int right){
  if(!t.contains("overview"))return;var o=t.getCompound("overview");double days=OfficeAttention.foodDays(o);
  Object[][] parts={{new ItemStack(Items.PLAYER_HEAD),String.valueOf(o.getInt("residents")),Component.translatable("office.villageastra.overview.residents")},
   {new ItemStack(Items.BREAD),days<0?"–":OfficeUi.days(days).getString(),Component.translatable("office.villageastra.overview.food_label")},
   {new ItemStack(Items.GOLD_INGOT),String.valueOf(o.getLong("coins")),Component.translatable("office.villageastra.overview.treasury")}};
  for(var p:parts){String v=(String)p[1];int w=13+f.width(v);if(x+w>right)return;OfficeUi.icon12(g,(ItemStack)p[0],x,y-1);int end=OfficeUi.drawText(g,f,v,x+13,y+1,OfficeUi.MUTED);
   OfficeUi.tips().add(x,y-1,end-x,11,List.of(((Component)p[2]).copy().append(": "+v)));x=end+8;}
 }
 public static List<TabButton> tabRow(Layout l,Font f,int active,IntConsumer select){
  var out=new ArrayList<TabButton>();for(int s:TAB_ORDER){final int to=s;out.add(new TabButton(s,Component.translatable("hall.villageastra.tab_"+s),OfficeUi.tabIcon(s),b->select.accept(to)));}
  OfficeUi.layoutTabs(out,f,l.tabs(),active);return out;
 }
 public static void badges(GuiGraphics g,Font f,List<TabButton> tabs){var by=OfficeAttention.byTab(OfficeAttention.needs(ConstructionOverlay.snapshot()));for(var tab:tabs){var b=by.get(tab.section());if(b!=null)OfficeUi.badge(g,f,tab,b.count(),b.tone());}}
 /** The rules of a tab, moved out of its body into the '?' tooltip. */
 public static Component help(int s){
  List<Component> lines=switch(s){case CONSTRUCTION->ConstructionPanel.help();case ELECTIONS->List.of(Component.translatable("election.villageastra.rules"));
   case BUILDINGS->List.of(Component.translatable("office.villageastra.buildings.help"));case RESEARCH->List.of(Component.translatable("office.villageastra.research.help"),Component.translatable("research.villageastra.legend"),Component.translatable("research.villageastra.keys"));
   case QUESTS->List.of(Component.translatable("quest.villageastra.rules"));case WAR->List.of(Component.translatable("war.villageastra.rules"));case LEVELS->List.of(Component.translatable("upgrade.villageastra.rules"));
   default->legend();};
  var out=Component.empty();for(int i=0;i<lines.size();i++){if(i>0)out.append("\n");out.append(lines.get(i));}return out;
 }
 /** The Overview's '?': what the list is, and what each colour means. */
 private static List<Component> legend(){var out=new ArrayList<Component>();out.add(Component.translatable("office.villageastra.overview.help"));out.add(Component.translatable("office.villageastra.tone.title"));
  for(var tone:Tone.values())if(tone!=Tone.INFO)out.add(Component.translatable("office.villageastra.tone."+tone.name().toLowerCase(Locale.ROOT)).withStyle(x->x.withColor(tone.fg()&0xFFFFFF)));return out;}
 public static OfficeButton helpButton(Layout l,int s){var b=OfficeButton.of(Component.literal("?"),ItemStack.EMPTY,helpSeen?Tone.INFO:Tone.ACTION,x->{});b.hint(help(s)).at(l.help());b.setTooltip(Tooltip.create(help(s)));return b;}
 /** Draws the pulsing outline of an unseen '?', and remembers once it has been looked at. */
 public static void pulseHelp(GuiGraphics g,OfficeButton help,int mx,int my){
  if(help==null||!help.visible)return;if(help.isHovered()){helpSeen=true;help.tone(Tone.INFO);return;}if(helpSeen)return;
  int a=(int)(128+127*Math.sin(net.minecraft.Util.getMillis()/250.0));g.renderOutline(help.getX()-1,help.getY()-1,help.getWidth()+2,help.getHeight()+2,(a<<24)|(Tone.ACTION.fg()&0xFFFFFF));
 }

 @Override protected void init(){
  layout=new Layout(width,height);
  tabs=tabRow(layout,font,section,s->open(s,null));tabs.forEach(this::addRenderableWidget);
  overview=new OverviewPanel(layout,this::open);
  construction=new ConstructionPanel(layout,this::addRenderableWidget,()->minecraft.setScreen(new AtlasScreen()));
  electionPanel=elections();farmPanel=buildings();questPanel=new QuestPanel(layout,this::addRenderableWidget);warPanel=war();upgradePanel=levels();
  atlas=addRenderableWidget(OfficeButton.of(Component.translatable("atlas.villageastra.open"),new ItemStack(Items.FILLED_MAP),Tone.INFO,b->minecraft.setScreen(new AtlasScreen())).at(layout.atlas()));
  help=addRenderableWidget(helpButton(layout,section));
  done=addRenderableWidget(OfficeButton.of(Component.translatable("gui.done"),ItemStack.EMPTY,Tone.INFO,b->onClose()).at(layout.done()));
  OfficeUi.disarm(children());applySection();ready=true;
 }
 // ---- adapters: each panel of another group is created, ticked, drawn and clicked here only, so moving it to the kit is one line each.
 // Expected kit signatures: new XPanel(Layout,Consumer<Button>), tick(boolean), render(GuiGraphics,Font,Layout,int,int),
 // boolean click(double,double), boolean scroll(double,double,double).
 private ElectionPanel elections(){return new ElectionPanel(layout,this::addRenderableWidget);}
 private BuildingsPanel buildings(){return new BuildingsPanel(layout,this::addRenderableWidget);}
 private WarPanel war(){return new WarPanel(layout,this::addRenderableWidget);}
 private UpgradePanel levels(){return new UpgradePanel(layout,this::addRenderableWidget);}
 private void renderPanel(GuiGraphics g,int mx,int my){switch(section){
  case OVERVIEW->overview.render(g,font,layout,mx,my);case CONSTRUCTION->construction.render(g,font,layout,mx,my);
  case ELECTIONS->electionPanel.render(g,font,layout,mx,my);case BUILDINGS->farmPanel.render(g,font,layout,mx,my);
  case QUESTS->questPanel.render(g,font,layout,mx,my);case WAR->warPanel.render(g,font,layout,mx,my);case LEVELS->upgradePanel.render(g,font,layout,mx,my);default->{}}}
 private boolean clickPanel(double x,double y){return switch(section){case OVERVIEW->overview.click(x,y);case CONSTRUCTION->construction.click(x,y);case ELECTIONS->electionPanel.click(x,y);case BUILDINGS->farmPanel.click(x,y);case QUESTS->questPanel.click(x,y);case WAR->warPanel.click(x,y);case LEVELS->upgradePanel.click(x,y);default->false;};}
 private boolean scrollPanel(double x,double y,double delta){return switch(section){case OVERVIEW->overview.scroll(x,y,delta);case CONSTRUCTION->construction.scroll(x,y,delta);case ELECTIONS->electionPanel.scroll(x,y,delta);case BUILDINGS->farmPanel.scroll(x,y,delta);case QUESTS->questPanel.scroll(x,y,delta);case WAR->warPanel.scroll(x,y,delta);case LEVELS->upgradePanel.scroll(x,y,delta);default->false;};}
 /** Levels' row buttons follow the list before anything is drawn or clicked, so a click never lands on a stale row. */
 private void beforePanel(){if(upgradePanel!=null&&section==LEVELS)upgradePanel.reposition();}
 /** Keys for the active panel (Buildings: up/down through the list, once BuildingsPanel.key(int) exists). */
 private boolean keyPanel(int key){return section==BUILDINGS&&farmPanel.key(key);}
 /** Focus a building on the Buildings or Levels tab (BuildingsPanel.select(UUID) / UpgradePanel.reveal(UUID) once they exist). */
 private void focusBuilding(int s,String id){try{var uuid=java.util.UUID.fromString(id);if(s==BUILDINGS)BuildingsPanel.select(uuid);else if(s==LEVELS)UpgradePanel.reveal(uuid);}catch(IllegalArgumentException ex){}}
 /** Focus a research node (ResearchScreen.focus(id) once it exists). */
 private void focusResearch(String id){if(id!=null&&!id.isEmpty())ResearchScreen.focus(id);}
 private net.minecraft.client.gui.screens.Screen researchScreen(){return new ResearchScreen(this);}

 /** Switches the tab, optionally focusing a building or a research node on it. */
 public void open(int to,String focus){
  if(to==RESEARCH){focusResearch(focus);returnSection=section;section=RESEARCH;openResearch();return;}
  section=to;if(focus!=null&&!focus.isEmpty())focusBuilding(to,focus);OfficeUi.disarm(children());applySection();
 }
 private void applySection(){
  OfficeUi.layoutTabs(tabs,font,layout.tabs(),section);
  if(help!=null){help.hint(help(section));help.setTooltip(Tooltip.create(help(section)));}
  if(done!=null){done.at(layout.done());help.at(layout.help());done.visible=help.visible=true;}
  tick();
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  beforePanel();chrome(g,font,layout);renderPanel(g,mx,my);super.render(g,mx,my,partial);badges(g,font,tabs);pulseHelp(g,help,mx,my);OfficeUi.tips().render(g,font,mx,my,this);
 }
 @Override public void tick(){
  if(overview==null)return;
  overview.tick(section==OVERVIEW);construction.tick(section==CONSTRUCTION);electionPanel.tick(section==ELECTIONS);farmPanel.tick(section==BUILDINGS);questPanel.tick(section==QUESTS);warPanel.tick(section==WAR);upgradePanel.tick(section==LEVELS);
  // Opened straight on the science tab: the tree replaces this screen on the first tick, never from inside init().
  if(section==RESEARCH&&ready&&minecraft!=null&&minecraft.screen==this)openResearch();
 }
 /** AD-062: the science tab is the research tree; with the current ResearchScreen its back button returns to the tab the player came from. */
 private void openResearch(){section=returnSection==RESEARCH?OVERVIEW:returnSection;applySection();minecraft.setScreen(researchScreen());}
 @Override public boolean mouseClicked(double x,double y,int button){if(super.mouseClicked(x,y,button))return true;return button==0&&clickPanel(x,y)||button==1&&section==BUILDINGS&&farmPanel.clickRight(x,y);}
 @Override public boolean keyPressed(int key,int scan,int mods){return super.keyPressed(key,scan,mods)||keyPanel(key);}
 @Override public boolean mouseScrolled(double x,double y,double delta){beforePanel();return scrollPanel(x,y,delta);}
 @Override public void removed(){OfficeUi.disarm(children());}
 @Override public boolean isPauseScreen(){return false;}
}
