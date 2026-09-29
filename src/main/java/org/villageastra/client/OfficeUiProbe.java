package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.server.*;
/** The town hall office as the mayor sees it: every tab in display order is opened, held to the layout rules and captured at five sizes
 *  (AD-124) — 427x240 (854x480, scale 2), 320x240 (960x720, scale 3; at 854x480 Minecraft would cap scale 3 down to 2), 480x270,
 *  640x360 and 1280x720 (scale 1: the frame capped at 1000x600 and centred). At 427x240 and 1280x720 the research tree is driven through
 *  seven scenarios (wheel, density, keys, next to study, search, group, cut labels). The player is the mayor of the starter village;
 *  a sample of 'yesterday' is written so the treasury tile has a trend. */
final class OfficeUiProbe {
 private static final int SETTLE=60;
 private static final int[][] PASSES={{854,480,2,427,240},{960,720,3,320,240},{960,540,2,480,270},{1280,720,2,640,360},{1280,720,1,1280,720}};
 private static final String[] SCENARIOS={"wheel","density","keys","next","search","group","cuts"};
 private static final int COINS_BACK=25;
 /** AD-133: the card of this node is captured at every size; it must read at a glance — a few short rows, no wall of text,
  *  and no scrolling in the collapsed card wherever the column is at least 96 px tall. */
 private static final String CARD_NODE="housing.2";private static final int MAX_CARD_ROWS=4,MAX_CARD_LINES=10;
 private static int pass,index,step,ticks,scenario,researchOk,cardOk,before,beforeDensity;private static String beforeBranch;
 private static boolean stopped,ready;private static long fixtureCoins=Long.MIN_VALUE;private static final List<String> failures=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.officeUiSmoke");}
 static void setup(net.minecraft.server.MinecraftServer server){
  var e=SettlementData.get(server).entries().iterator().next();e.settlement().appointPlayerMayor(server.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(server).setDirty();
  var board=new CompoundTag();board.putInt("schema",1);var quests=new ListTag();
  // AD-141: the board keeps no request for goods the village does not want any more, so the fixture asks for something it really wants.
  var wanted=org.villageastra.world.Workshops.wants(server.overworld(),e).stream()
   .flatMap(w->java.util.Arrays.stream(w.ingredient().getItems())).filter(x->!x.isEmpty())
   .map(x->net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(x.getItem()).toString()).findFirst().orElse("");
  if(wanted.isEmpty())LogUtils.getLogger().warn("ASTRA_OFFICE_UI the village wants nothing: the supply instruction stands as a donation");
  for(String template:List.of("supply","cargo")){var q=new CompoundTag();q.putUUID("id",UUID.randomUUID());q.putUUID("village",e.settlement().id());q.putString("template",template.equals("supply")&&wanted.isEmpty()?"donation":template);q.putString("state","open");
   q.putString("item",template.equals("supply")&&!wanted.isEmpty()?wanted:"minecraft:glass");q.putInt("target",16);q.putLong("deadline",SettlementData.get(server).clock().ticks()+24000);q.putLong("coins",20);q.putLong("reputation",10);quests.add(q);}board.put("quests",quests);
  org.villageastra.persistence.NbtRecord.write(org.villageastra.world.Quests.path(server.overworld(),e.settlement().id()),board);
  // AD-124: a sample of yesterday, 25 coins poorer, so the treasury tile shows a trend of the difference.
  long day=SettlementData.get(server).clock().ticks()/OfficeHistory.DAY;long coins=TradeLedger.get(server).treasury(e.settlement().id());fixtureCoins=coins-COINS_BACK;
  var history=OfficeHistory.empty(e.settlement().id());OfficeHistory.record(history,day-1,new OfficeHistory.Sample(day-1,0,0,0,0,fixtureCoins,0,0));
  OfficeOverview.clear();OfficeHistory.save(server,e.settlement().id(),history);
  ready=true;
  LogUtils.getLogger().info("ASTRA_OFFICE_UI fixture: the player is the mayor of the starter village, with two quest instructions and a history sample of day {} with {} coins",day-1,fixtureCoins);
 }
 private static String label(){var p=PASSES[pass];return "g"+p[3]+"x"+p[4];}
 private static void fail(String problem){failures.add("tab "+index+" "+label()+": "+problem);LogUtils.getLogger().warn("ASTRA_OFFICE_UI problem tab {} {}: {}",index,label(),problem);}
 private static boolean researchPass(){return pass==0||pass==PASSES.length-1;}
 private static java.nio.file.Path shot(Minecraft mc,String name){
  var world=mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName();
  var path=java.nio.file.Path.of("../docs/runs/"+world+"-"+name+"-"+label()+".png");
  try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
  return path;
 }
 static void tick(Minecraft mc){if(stopped||!ready)return;try{
  var p=PASSES[pass];ticks++;
  if(step==0){var sample=new CompoundTag();var materials=new ListTag();for(int i=0;i<8;i++){var line=new CompoundTag();line.putString("item",i==7?"minecraft:glass":"minecraft:stone");line.putInt("missing",i==7?4:0);materials.add(line);}sample.put("materials",materials);sample.putString("design","home");if(ConstructionOverlay.missingMaterials(sample).size()!=1||!ConstructionOverlay.missingMaterials(sample).get(0).getString("item").equals("minecraft:glass"))throw new IllegalStateException("Supplied materials hide the remaining shortage");if(!ConstructionOverlay.projectName(sample).getString().equals(Component.translatable("building.villageastra.home").getString()))throw new IllegalStateException("HUD does not name the actual building");
   mc.getWindow().setWindowed(p[0],p[1]);mc.options.guiScale().set(p[2]);mc.resizeDisplay();step=1;ticks=0;return;}
  if(step==1){if(ticks<20)return;var w=mc.getWindow();
   if((int)w.getGuiScale()!=p[2]||w.getGuiScaledWidth()!=p[3]||w.getGuiScaledHeight()!=p[4])throw new IllegalStateException("GUI is "+w.getGuiScaledWidth()+"x"+w.getGuiScaledHeight()+" at scale "+w.getGuiScale()+" in a "+w.getWidth()+"x"+w.getHeight()+" window, expected "+p[3]+"x"+p[4]+" at scale "+p[2]);
   LogUtils.getLogger().info("ASTRA_OFFICE_UI pass {}: {}x{} window, GUI {}x{} at scale {}",label(),w.getWidth(),w.getHeight(),w.getGuiScaledWidth(),w.getGuiScaledHeight(),(int)w.getGuiScale());step=2;ticks=0;return;}
  if(step>=10){research(mc);return;}
  int section=ConstructionScreen.TAB_ORDER[index];
  if(step==2){OfficeUi.frames=0;if(section==ConstructionScreen.RESEARCH){ResearchPanel.focus(CARD_NODE);ResearchPanel.collapseCard();}OfficeProbes.open(mc,section);org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);step=3;ticks=0;return;}
  if(ticks<SETTLE)return;
  // The layout rules, from the frame just drawn. Findings on another group's tab (Quests) are logged as known-foreign, never hidden.
  var screen=mc.screen;if(screen==null)throw new IllegalStateException("No screen on tab "+index);
  var all=OfficeProbes.layoutProblems(screen);var own=new ArrayList<String>();var foreign=new ArrayList<String>();for(var x:all)(x.startsWith("known-foreign: ")?foreign:own).add(x);
  if(section==ConstructionScreen.QUESTS&&ConstructionOverlay.snapshot().getList("quests",Tag.TAG_COMPOUND).size()<2)fail("Quest instruction fixture is missing");
  LogUtils.getLogger().info("ASTRA_OFFICE_UI layout tab {} {} section={} screen={} problems={}",index,label(),section,screen.getClass().getSimpleName(),own);
  if(!foreign.isEmpty())LogUtils.getLogger().warn("ASTRA_OFFICE_UI foreign tab {} {} ({} findings, QuestPanel is not ours): {}",index,label(),foreign.size(),foreign.subList(0,Math.min(5,foreign.size())));
  for(var note:OfficeProbes.NOTES)LogUtils.getLogger().info("ASTRA_OFFICE_UI note tab {} {}: {}",index,label(),note);
  for(var problem:own)fail(problem);
  var path=shot(mc,"office-tab"+index);
  LogUtils.getLogger().info("ASTRA_OFFICE_UI screenshot tab {} {} screen={}",index,path,screen.getClass().getSimpleName());
  // Interactions after the capture, so the frame shows the tab as it opened.
  if(screen instanceof ConstructionScreen cs){
   if(section==ConstructionScreen.OVERVIEW)overview(mc,cs);
   else if(section==ConstructionScreen.BUILDINGS)buildings(cs);
   else if(section==ConstructionScreen.LEVELS)levels(cs);}
  if(screen instanceof ResearchScreen rs)card(mc,rs);
  if(screen instanceof ResearchScreen&&researchPass()){step=10;scenario=0;ticks=0;return;}
  advance(mc);
 }catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_OFFICE_UI FAILED tab "+index+" "+label()+": "+ex,ex);mc.stop();}}
 private static void advance(Minecraft mc){
  step=2;ticks=0;index++;
  if(index==ConstructionScreen.TAB_ORDER.length){index=0;pass++;step=0;
   if(pass==PASSES.length){stopped=true;
    if(failures.isEmpty()&&researchOk==2*SCENARIOS.length&&cardOk==PASSES.length)LogUtils.getLogger().info("ASTRA_OFFICE_UI VERIFIED {} frames research={}x2 cards={}",PASSES.length*ConstructionScreen.TAB_ORDER.length,SCENARIOS.length,cardOk);
    else LogUtils.getLogger().error("ASTRA_OFFICE_UI FAILED {} (and {} more problems; research scenarios ok {}, cards ok {})",failures.isEmpty()?"-":failures.get(0),Math.max(0,failures.size()-1),researchOk,cardOk);
    mc.setScreen(null);mc.stop();}}
 }
 /** AD-133: the card of the node looked at, at this size — the short rows the panel drew, the lines of text inside the details column,
  *  and whether the collapsed card fits it. The full text lives behind «Подробнее», so a body longer than MAX_CARD_LINES is a failure. */
 private static void card(Minecraft mc,ResearchScreen rs){
  var panel=rs.panel();var area=panel.cardArea();
  if(!CARD_NODE.equals(panel.selected()))fail("the card shows "+panel.selected()+", not "+CARD_NODE);
  if(panel.cardExpanded())fail("the card opened already expanded");
  int lines=(int)OfficeUi.DRAWN_TEXT.stream().filter(area::containsRect).map(Rect::y).distinct().count();
  if(panel.cardRows()==0)fail("the card has no effect row for "+CARD_NODE);
  if(panel.cardRows()>MAX_CARD_ROWS)fail("the card drew "+panel.cardRows()+" effect rows, at most "+MAX_CARD_ROWS+" are readable");
  if(lines>MAX_CARD_LINES)fail("the card body is "+lines+" lines of text, at most "+MAX_CARD_LINES+" are readable");
  if(area.h()>=96&&panel.cardOverflow())fail("the collapsed card overflows its column: "+panel.cardHeight()+" px in "+area.h());
  var path=shot(mc,"research-card");
  LogUtils.getLogger().info("ASTRA_OFFICE_UI research card {} rows={} lines={} hidden={} height={}/{} shot {}",label(),panel.cardRows(),lines,panel.cardHidden(),panel.cardHeight(),area.h(),path);
  cardOk++;
 }
 /** The research scenarios: an action (step 10), then after ten frames its check, the layout rules and a capture (step 11). */
 private static void research(Minecraft mc){
  if(!(mc.screen instanceof ResearchScreen rs))throw new IllegalStateException("The research tree closed during scenario "+SCENARIOS[scenario]);
  var panel=rs.panel();var grid=panel.grid();double gx=grid.x()+grid.w()/2.0,gy=grid.y()+grid.h()/2.0;String name=SCENARIOS[scenario];
  if(step==10){
   switch(name){
    case "wheel"->{panel.resetView();before=panel.firstRow();beforeDensity=panel.density();for(int i=0;i<3;i++)rs.mouseScrolled(gx,gy,-1);}
    case "density"->{beforeDensity=panel.density();panel.scroll(gx,gy,beforeDensity<2?1:-1,false,true);}
    case "keys"->{panel.resetView();panel.frameCenter(panel.visibleBranches().get(1)+".2");beforeBranch=ResearchCatalog.get(panel.selected()).branch();rs.keyPressed(264,0,0);}
    case "next"->rs.keyPressed(78,0,0);
    case "search"->{panel.search().setValue("дорог");rs.setFocused(panel.search());panel.search().setFocused(true);rs.keyPressed(257,0,0);}
    case "group"->{panel.leaveSearch();rs.setFocused(null);rs.keyPressed(51,0,0);}
    default->{panel.resetView();}
   }
   step=11;ticks=0;return;}
  if(ticks<12)return;
  var problems=new ArrayList<String>();
  switch(name){
   case "wheel"->{if(panel.firstRow()<=before)problems.add("three wheel steps did not move the rows: first row "+before+" -> "+panel.firstRow());
    if(panel.density()!=beforeDensity)problems.add("the wheel changed the density");
    if(pass==0&&panel.rowsShown()<4)problems.add("only "+panel.rowsShown()+" rows shown at 427x240");}
   case "density"->{if(panel.density()==beforeDensity)problems.add("Ctrl+wheel kept the density "+beforeDensity);}
   case "keys"->{var rows=panel.visibleBranches();var now=ResearchCatalog.get(panel.selected()).branch();
    if(rows.indexOf(now)!=rows.indexOf(beforeBranch)+1)problems.add("the down key went from "+beforeBranch+" to "+now);if(!panel.cellVisible(panel.selected()))problems.add("the selected cell is not on screen");}
   case "next"->{var s=panel.stateOf(panel.selected());if(s!=ResearchTreeModel.State.AVAILABLE&&s!=ResearchTreeModel.State.PARTIAL)problems.add("N selected "+panel.selected()+" in state "+s);if(!panel.cellVisible(panel.selected()))problems.add("the node N chose is not on screen");}
   case "search"->{var expected=ResearchTreeModel.search("дорог",n->OfficeUi.research(n.id()).getString());var list=panel.matchList();
    if(panel.matchCount()!=expected.size()||panel.matchCount()<7)problems.add("search counted "+panel.matchCount()+", the model "+expected.size());
    for(int t=1;t<=6;t++)if(!list.contains("roads."+t))problems.add("search misses roads."+t);if(!list.contains("defense.3"))problems.add("search misses defense.3");
    if(panel.matchIndex()!=1||list.size()<2||!panel.selected().equals(list.get(1)))problems.add("Enter did not go to the second match: index "+panel.matchIndex()+" selected "+panel.selected());
    LogUtils.getLogger().info("ASTRA_OFFICE_UI research search count={} second={}",panel.matchCount(),list.size()>1?list.get(1):"-");}
   case "group"->{var rows=panel.visibleBranches();if(!rows.equals(ResearchTreeModel.GROUPS.get("city")))problems.add("the city group (key 3, AD-136) shows "+rows);}
   default->{int cuts=OfficeUi.CUT.size();LogUtils.getLogger().info("ASTRA_OFFICE_UI research cuts={} tips={}",cuts,OfficeUi.LAST_TIPS.size());}
  }
  problems.addAll(OfficeProbes.layoutProblems(mc.screen));
  var path=shot(mc,"research-"+name);
  LogUtils.getLogger().info("ASTRA_OFFICE_UI screenshot research {} {}",name,path);
  if(problems.isEmpty()){researchOk++;LogUtils.getLogger().info("ASTRA_OFFICE_UI research {} ok {}",name,label());}else for(var x:problems)fail("research "+name+": "+x);
  scenario++;step=10;ticks=0;
  if(scenario==SCENARIOS.length){panel.resetView();advance(mc);}
 }
 /** The fixture's mayor has chosen no research: the list must say so, and its first line must lead to its tab; the treasury trend is the fixture's difference. */
 private static void overview(Minecraft mc,ConstructionScreen cs){
  var snapshot=ConstructionOverlay.snapshot();var needs=cs.overview().needs();
  if(needs.isEmpty())fail("the attention list is empty");
  String research=Component.translatable("office.villageastra.need.research").getString();
  if(needs.stream().noneMatch(n->n.text().getString().equals(research)))fail("no 'choose a research' line: "+needs.stream().map(n->n.text().getString()).toList());
  if(!snapshot.contains("overview"))fail("the snapshot has no overview{} (server data missing)");
  else if(snapshot.getCompound("overview").getLong("need")<=0)fail("overview{} has no daily need");
  String noData=Component.translatable("office.villageastra.no_data").getString();if(OfficeUi.DRAWN_STRINGS.contains(noData))fail("the food tile shows 'no data'");
  if(researchPass()){var o=snapshot.getCompound("overview");var trend=o.getCompound("trend");long want=o.getLong("coins")-fixtureCoins;String shown=(want>0?"+":want<0?"-":"")+Math.abs(want);
   if(trend.getInt("span")<1||trend.getLong("coins")!=want)fail("the treasury trend is "+trend+", expected "+want+" over a day or more");
   else if(OfficeUi.DRAWN_STRINGS.stream().noneMatch(s->s.contains(shown)))fail("the treasury tile does not show its trend "+shown);
   else LogUtils.getLogger().info("ASTRA_OFFICE_UI overview trend coins={} span={} shown",want,trend.getInt("span"));}
  if(needs.isEmpty())return;
  var row=cs.overview().rowRect(0);if(row==null){fail("the first attention line is not shown");return;}
  int target=needs.get(0).section();boolean handled=cs.mouseClicked(row.x()+row.w()/2.0,row.y()+9,0);cs.mouseReleased(row.x()+row.w()/2.0,row.y()+9,0);
  int now=mc.screen==cs?cs.section():mc.screen instanceof ResearchScreen?ConstructionScreen.RESEARCH:-1;
  LogUtils.getLogger().info("ASTRA_OFFICE_UI overview jump {}->{}",ConstructionScreen.OVERVIEW,now);
  if(!handled||now!=target)fail("the first attention line leads to "+now+", not "+target);
 }
 /** Clicking the third row of the list selects that building; the upgrade button stays in the frame and names the next level. */
 private static void buildings(ConstructionScreen cs){
  var panel=cs.panel(ConstructionScreen.BUILDINGS);
  try{Object before=OfficeProbes.call(panel,"selected");var row=(Rect)OfficeProbes.call(panel,"rowRect",2);
   if(row==null){fail("the third building row is not shown");return;}
   cs.mouseClicked(row.x()+row.w()/2.0,row.y()+row.h()/2.0,0);cs.mouseReleased(row.x()+row.w()/2.0,row.y()+row.h()/2.0,0);
   Object after=OfficeProbes.call(panel,"selected");if(Objects.equals(before,after))fail("clicking the third row did not change the selection");
   var up=(Button)OfficeProbes.call(panel,"upgradeButton");
   if(up!=null&&up.visible){if(!new Layout(cs.width,cs.height).frame().inset(1).containsRect(Rect.of(up)))fail("the upgrade button leaves the frame");
    if(!up.getMessage().getString().matches(".*\\b[IVX]+\\b.*"))fail("the upgrade button does not name the next level: "+up.getMessage().getString());}
  }catch(ReflectiveOperationException ex){fail("BuildingsPanel accessors missing (selected(), rowRect(int), upgradeButton()): "+ex.getMessage());}
 }
 /** Row buttons of visible rows lie in the content; the wheel reaches the last row (the town hall) without any page button. */
 private static void levels(ConstructionScreen cs){
  var l=cs.layout();var content=l.content();var rows=ConstructionOverlay.snapshot().getCompound("upgrades").getList("buildings",Tag.TAG_COMPOUND);
  try{
   for(var raw:rows){var r=(CompoundTag)raw;if(!r.hasUUID("id"))continue;var b=(Button)staticCall(UpgradePanel.class,"buttonFor",r.getUUID("id"));
    if(b!=null&&b.visible&&!content.containsRect(Rect.of(b)))fail("a row button leaves the content: "+b.getMessage().getString());}
   for(int i=0;i<rows.size()+2;i++)cs.mouseScrolled(content.x()+content.w()/2.0,content.y()+content.h()/2.0,-1);
   // The screen repositions the row buttons inside mouseScrolled, so no frame has to pass before they are checked.
   for(var raw:rows){var r=(CompoundTag)raw;if(!r.getString("type").equals("town_hall")||!r.hasUUID("id"))continue;var b=(Button)staticCall(UpgradePanel.class,"buttonFor",r.getUUID("id"));
    if(r.getInt("kept")<r.getInt("max")&&(b==null||!b.visible))fail("the wheel did not bring the town hall row into view");}
  }catch(ReflectiveOperationException ex){fail("UpgradePanel.buttonFor(UUID) missing: "+ex.getMessage());}
 }
 private static Object staticCall(Class<?> c,String method,Object... args)throws ReflectiveOperationException{
  for(var m:c.getDeclaredMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length&&java.lang.reflect.Modifier.isStatic(m.getModifiers())){m.setAccessible(true);return m.invoke(null,args);}
  throw new NoSuchMethodException(c.getSimpleName()+"."+method);
 }
}
