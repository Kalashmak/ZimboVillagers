package org.villageastra.client;
import java.util.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.villageastra.client.OfficeUi.*;
import org.villageastra.server.ConstructionNetwork;
import org.villageastra.world.FarmCrops;
/** AD-058: the card of one building, drawn the same on the map and in the office with the office kit: what it is, who works and lives there,
 *  what its next level takes and its own settings; numbers and icons on the card, the sentences behind them in tooltips. */
final class BuildingCard {
 private BuildingCard(){}
 static Component text(String key,Object... args){return Component.translatable("building.villageastra.card."+key,args);}
 static Component name(CompoundTag card){return Component.translatable("building.villageastra."+card.getString("type"));}
 private static Component office(String key,Object... args){return Component.translatable("office.villageastra."+key,args);}
 private static List<Component> people(ListTag list){
  var names=new ArrayList<Component>();
  for(var raw:list){var p=(CompoundTag)raw;var role=p.getString("profession").isEmpty()?"":Component.translatable("profession.villageastra."+p.getString("profession")).getString();
   names.add(Component.literal(p.getString("name").isEmpty()?role:role.isEmpty()?p.getString("name"):p.getString("name")+" ("+role+")"));}
  return names;
 }
 /** Names with an ordinal where a type repeats ('Жилой дом 2'), numbered in the order of the list; the Levels tab can use the same names. */
 static Map<UUID,Component> titles(List<CompoundTag> cards){
  var count=new HashMap<String,Integer>();for(var c:cards)count.merge(c.getString("type"),1,Integer::sum);
  var seen=new HashMap<String,Integer>();var out=new LinkedHashMap<UUID,Component>();
  for(var c:cards){if(!c.hasUUID("id"))continue;String type=c.getString("type");int n=seen.merge(type,1,Integer::sum);out.put(c.getUUID("id"),count.get(type)>1?name(c).copy().append(" "+n):name(c));}
  return out;
 }
 /** Where the town hall stands, for the direction and distance of the others. */
 static BlockPos hall(){for(var raw:ConstructionOverlay.snapshot().getList("cards",Tag.TAG_COMPOUND)){var c=(CompoundTag)raw;if(c.getString("type").equals("town_hall"))return BlockPos.of(c.getLong("pos"));}return null;}
 /** '24 blocks north-east of the hall', only in tooltips; the hall itself has none. */
 static Component where(CompoundTag card){var hall=hall();var at=BlockPos.of(card.getLong("pos"));if(hall==null||card.getString("type").equals("town_hall"))return null;
  int d=(int)Math.round(Math.sqrt(Math.pow(at.getX()-hall.getX(),2)+Math.pow(at.getZ()-hall.getZ(),2)));return office("buildings.where",OfficeUi.direction(hall,at),d);}
 /** A spot on the card that opens something (a research node, the construction tab); the office panel runs it on a click. */
 record Link(Rect rect,Runnable run){}
 static final List<Link> LINKS=new ArrayList<>();
 /** The state of a building in one tone: nobody at the station or the station stopped red, its inputs missing amber;
  *  an upgrade ready now blue (for the mayor only), an upgrade short only of materials amber. A missing research is grey, never amber. */
 static Tone tone(CompoundTag card,boolean manage){
  Tone t=switch(card.getString("status")){case "no_worker","blocked"->Tone.BAD;case "missing_input"->Tone.WAIT;default->null;};
  if(upgradable(card)){var u=card.getCompound("upgrade");String why=u.getString("refusal");boolean research=!u.getList("research",Tag.TAG_STRING).isEmpty();
   if(why.isEmpty()&&u.getInt("lack")==0&&!research&&manage)t=Tone.worst(t,Tone.ACTION);
   else if((why.isEmpty()||why.equals("materials"))&&!research&&u.getInt("lack")>0)t=Tone.worst(t,Tone.WAIT);}
  return t==null?Tone.INFO:t;
 }
 private static ItemStack cropIcon(String crop){return OfficeUi.icon("minecraft:"+(crop.isEmpty()?"wheat":crop));}
 /** Draws the card for the Atlas side panel and returns the line under it; that screen never shows the office's tooltips, so none are kept. */
 static int render(GuiGraphics g,Font font,CompoundTag card,int x,int y,int width){int bottom=render(g,font,card,name(card),x,y,width,-1,-1,0,false);OfficeUi.tips().clear();LINKS.clear();return bottom;}
 /** Draws the card with the office kit and returns the line under it: a header (icon, name, level pips), the people, the upgrade
  *  (what it takes, what stops it), then the building's own block (field, nursery, tower, quarry). {@code reserve} keeps the header's right end free for buttons. */
 static int render(GuiGraphics g,Font f,CompoundTag card,Component title,int x,int y,int w,int mx,int my,int reserve,boolean office){return render(g,f,card,title,x,y,w,mx,my,reserve,office,1);}
 /** AD-124: with cols=2 (a wide office) the people and the building's own work go on the left, the next level and the core on the right. */
 static int render(GuiGraphics g,Font f,CompoundTag card,Component title,int x,int y,int w,int mx,int my,int reserve,boolean office,int cols){
  var tips=OfficeUi.tips();var at=BlockPos.of(card.getLong("pos"));var u=card.getCompound("upgrade");boolean hasLevels=card.contains("upgrade");
  // Header: icon, name, pips. The coordinates and the way from the hall are in the name's tooltip.
  g.renderItem(OfficeUi.buildingIcon(card.getString("type")),x,y+1);
  int max=u.getInt("max"),pipsW=hasLevels?max*7-2:0,room=w-18-reserve-(pipsW>0?pipsW+6:0);
  int end=OfficeUi.label(g,f,title,x+18,y+5,room,OfficeUi.TEXT);
  if(office){var lines=new ArrayList<Component>();lines.add(title);var way=where(card);if(way!=null)lines.add(way);lines.add(text("where",at.getX(),at.getY(),at.getZ(),card.getInt("w"),card.getInt("d")));tips.add(x,y,Math.max(18,end-x),18,lines);}
  if(hasLevels){int px=x+w-reserve-pipsW;OfficeUi.pips(g,px,y+7,u.getInt("level"),u.getInt("kept"),max);
   if(office){var lines=new ArrayList<Component>();lines.add(office("buildings.level",OfficeUi.roman(u.getInt("kept")),OfficeUi.roman(max)));
    if(u.getInt("level")<u.getInt("kept"))lines.add(text("level_works",u.getInt("level")).copy().withStyle(s->s.withColor(Tone.WAIT.fg()&0xFFFFFF)));
    if(u.getBoolean("driven"))lines.add(text("driven"));tips.add(px-1,y+4,pipsW+2,11,lines);}}
  y+=20;
  int X=x,W=w,y0=y,lw=cols==2?(W-8)/2:W,yl;x=X;w=lw;
  // People: who works here out of the posts, or who lives here out of the beds; red when a station has nobody or stopped, amber when it lacks inputs.
  var workers=card.getList("workers",Tag.TAG_COMPOUND);var dwellers=card.getList("dwellers",Tag.TAG_COMPOUND);
  int staff=card.contains("staff")?card.getInt("staff"):workers.size(),slots=card.getInt("slots");String status=card.getString("status");
  Tone work=switch(status){case "no_worker","blocked"->Tone.BAD;case "missing_input"->Tone.WAIT;default->Tone.INFO;};
  if(slots>0||!workers.isEmpty()){var people=office("buildings.people",staff,Math.max(slots,staff));var lines=new ArrayList<Component>();lines.add(people);
   if(status.equals("no_worker"))lines.add(office("need.no_worker",title));else if(status.equals("missing_input"))lines.add(office("need.missing_input",title));else if(status.equals("blocked"))lines.add(office("tone.bad"));
   lines.addAll(people(workers));
   OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.PLAYER_HEAD),people,Component.empty(),work);if(office)tips.add(x,y,w,18,lines);y+=20;}
  if(card.contains("beds")||!dwellers.isEmpty()){int beds=card.contains("beds")?card.getInt("beds"):dwellers.size(),living=card.contains("bedsFree")?Math.max(0,beds-card.getInt("bedsFree")):dwellers.size();
   var lines=new ArrayList<Component>();lines.add(office("buildings.beds",living,beds));lines.addAll(people(dwellers));
   OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.RED_BED),office("buildings.beds",living,beds),Component.empty(),Tone.INFO);if(office)tips.add(x,y,w,18,lines);y+=20;}
  if(slots==0&&workers.isEmpty()&&dwellers.isEmpty()&&!card.contains("beds")){OfficeUi.chip(g,f,x,y,w,ItemStack.EMPTY,text("empty"),Component.empty(),Tone.OFF);if(office)tips.add(new Rect(x,y,w,18),text("empty"));y+=20;}
  yl=y;if(cols==2){x=X+lw+8;w=W-lw-8;y=y0;}
  // AD-073: the next level — the items it takes (only what is missing, the rest a check), then what stops it.
  if(hasLevels&&u.getInt("kept")<max&&u.contains("next")){
   y=OfficeUi.header(g,f,x,y+2,w,new ItemStack(Items.EXPERIENCE_BOTTLE),office("buildings.upgrade_block",OfficeUi.roman(u.getInt("kept")),OfficeUi.roman(u.getInt("next"))));
   var cost=u.getList("cost",Tag.TAG_COMPOUND);
   if(!cost.isEmpty()){int cx=x,shown=0;
    for(var raw:cost){var c=(CompoundTag)raw;int need=c.getInt("need"),have=c.getInt("have"),miss=Math.max(0,need-have);
     // The cell's real width (icon and '-N' or the check), so a long count never runs past the card; room is kept for '+N' while cells remain.
     int cell=Math.max(34,18+(miss>0?f.width("-"+miss):5));if(cx+cell>x+w-(shown<cost.size()-1?16:0))break;
     OfficeUi.itemCell(g,f,OfficeUi.icon(c.getString("item")),cx,y,miss,have,need,List.of(office("buildings.in_hall",Math.min(have,need),need)));cx+=cell+2;shown++;}
    if(shown<cost.size()){int more=cost.size()-shown;OfficeUi.drawText(g,f,"+"+more,cx,y+4,OfficeUi.MUTED);
     if(office){var lines=new ArrayList<Component>();for(int i=shown;i<cost.size();i++){var c=cost.getCompound(i);lines.add(OfficeUi.icon(c.getString("item")).getHoverName().copy().append(": "+Math.min(c.getInt("have"),c.getInt("need"))+" / "+c.getInt("need")));}tips.add(cx,y,f.width("+"+more)+2,16,lines);}}
    y+=22;}
   else if(u.getInt("items")>0){int items=u.getInt("items"),lack=u.getInt("lack");
    OfficeUi.bar(g,f,x,y,w,items-lack,items,lack>0?Tone.WAIT:Tone.OK,office("buildings.in_hall",items-lack,items));y+=15;}
   // A missing research is a grey lock with '›': no shortfall of the mayor's, and a click opens the node in the tree.
   var research=u.getList("research",Tag.TAG_STRING);
   if(!research.isEmpty()){String id=research.getString(0);var r=new Rect(x,y,w,18);boolean hover=office&&r.contains(mx,my);
    OfficeUi.chip(g,f,x,y,w,new ItemStack(Items.ENCHANTED_BOOK),office("reason.research",OfficeUi.research(id)),research.size()>1?Component.literal("+"+(research.size()-1)):Component.empty(),Tone.OFF,office,hover);
    if(office){var lines=new ArrayList<Component>();for(var raw:research)lines.add(office("reason.research",OfficeUi.research(raw.getAsString())));tips.add(r,lines.toArray(Component[]::new));LINKS.add(new Link(r,()->open(ConstructionScreen.RESEARCH,id)));}
    y+=20;}
   String why=u.getString("refusal");
   if(why.equals("hall_office")){var r=new Rect(x,y,w,18);boolean hover=office&&r.contains(mx,my);
    OfficeUi.chip(g,f,x,y,w,OfficeUi.tabIcon(ConstructionScreen.CONSTRUCTION),office("buildings.hall_office"),Component.empty(),Tone.INFO,office,hover);if(office){tips.add(r,office("buildings.hall_office"));LINKS.add(new Link(r,()->open(ConstructionScreen.CONSTRUCTION,null)));}y+=20;}
   else if(!why.isEmpty()&&!why.equals("research")&&!why.equals("materials")&&!why.equals("done")){var reason=Component.translatable("upgrade.villageastra.refused."+why);
    OfficeUi.tag(g,f,x,y,reason,refusalTone(why),w);if(office)tips.add(new Rect(x,y,w,11),reason);y+=14;}}
  else if(hasLevels&&u.getInt("kept")>=max){OfficeUi.tag(g,f,x,y+2,office("levels.done",OfficeUi.roman(max)),Tone.OK);y+=16;}
  // AD-112: the core — its grade as pips (filled up to the grade; none: the building works at I), then what its working effects give now.
  if(card.contains("core"))y=core(g,f,card,x,y,w,office);
  // AD-135: the annexes — built, being built, ready to order, or what stops them; an annex names the building it belongs to.
  if(card.contains("annexes")||card.contains("annexOf"))y=annexes(g,f,card,x,y,w,office);
  int yr=y;if(cols==2){x=X;w=lw;y=yl;}
  // AD-104: the field — its crop, then how many it feeds against the target of its level (amber when short); the numbers behind it in the tooltip.
  if(card.contains("crop")){var crop=card.getString("crop");var name=Component.translatable("farm.villageastra."+crop);
   var field=card.contains("fieldPlots")?text("field",card.getInt("fieldPlots"),card.getInt("sown"),card.getInt("hydrated"),text("field_yield."+card.getString("food"),Math.round(card.getDouble("foodPerDay")))):null;
   OfficeUi.chip(g,f,x,y+2,w,cropIcon(crop),name,Component.empty(),Tone.INFO);
   if(office){var lines=new ArrayList<Component>();lines.add(text("crop",name));if(field!=null)lines.add(field);tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));}
   y+=22;
   if(card.contains("fieldPlots")){int now=card.getInt("feedsNow"),upTo=card.getInt("feedsNowMax"),target=card.getInt("target");String mode=card.getString("mode");
    Tone t=mode.equals("none")||mode.isEmpty()?Tone.OFF:upTo>=target?Tone.OK:Tone.WAIT;
    OfficeUi.bar(g,f,x,y,w,now,Math.max(1,target),t,t==Tone.OFF?text("feeds_none"):office("buildings.feeds",now,target));
    if(office){var lines=new ArrayList<Component>();
     lines.add(switch(mode){case "hand"->text("feeds_hand",now,upTo,card.getInt("feedsChain"),card.getInt("feedsChainMax"));case "chain"->text("feeds_chain",now,upTo);case "raw"->text("feeds_raw",now,upTo);default->text("feeds_none");});
     lines.add(text("field_target",target));if(card.getInt("fieldUnseen")>0)lines.add(text("field_unseen",card.getInt("fieldUnseen")));tips.add(new Rect(x,y,w,11),lines.toArray(Component[]::new));}
    y+=14;
    // AD-112: plots laid beyond what the core allows are not worked: amber, with both counts.
    if(card.contains("fieldPlotsBuilt")&&card.getInt("fieldPlots")<card.getInt("fieldPlotsBuilt")){var short_=text("core_short",card.getInt("fieldPlots"),card.getInt("fieldPlotsBuilt"));
     OfficeUi.tag(g,f,x,y,short_,Tone.WAIT,w);if(office)tips.add(new Rect(x,y,w,11),short_,Component.translatable("core.villageastra.farm_built",card.getInt("fieldModulesBuilt"),card.getInt("fieldModules")));y+=14;}
    if(mode.equals("hand")){var chain=office("buildings.with_chain",card.getInt("feedsChain"));OfficeUi.tag(g,f,x,y,chain,Tone.INFO,w);if(office)tips.add(new Rect(x,y,w,11),text("feeds_hand",now,upTo,card.getInt("feedsChain"),card.getInt("feedsChainMax")));y+=14;}}}
  // AD-130: the grid of the farm's 18 fields, each with its crop, and "sow all".
  if(FarmFieldGrid.shown(card))y=FarmFieldGrid.render(g,f,card,x,y+2,w,office,mx,my);
  // AD-131: the forester's hut — reach, the day's felling and planting, the mix and its asks, the saw, the grove.
  if(ForestCard.shown(card))y=ForestCard.render(g,f,card,x,y,w,office);
  // AD-138: one line a pen: its kind, head of sixteen, the feeder; a click (the mayor's) switches an empty pen's kind.
  if(card.contains("pens"))y=pens(g,f,card,x,y,w,office,mx,my);
  // AD-139: the restaurant — its hall, the day's meals, the kitchen by level, the couriers and the cart.
  if(DiningCard.shown(card))y=DiningCard.render(g,f,card,x,y,w,office);
  // AD-147: the warehouse — its store, couriers, carts, wolves and sorting.
  if(WarehouseCard.shown(card))y=WarehouseCard.render(g,f,card,x,y,w,office);
  // AD-094: archers on a wall tower; amber while a place is free, with why nobody goes up in the tooltip.
  if(card.contains("towerArchers")){int archers=card.getInt("towerArchers"),places=card.getInt("towerPlaces");var lines=new ArrayList<Component>();var line=text("tower_archers",archers,places);lines.add(line);
   if(!card.getBoolean("towerCandidate")&&archers<places)lines.add(text("no_archer"));
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.BOW),line,Component.empty(),archers>=places?Tone.OK:Tone.WAIT);if(office)tips.add(new Rect(x,y+2,w,18),lines.toArray(Component[]::new));y+=22;}
  // The quarry: waiting for its miner, being dug, or worked out; red when the village has no miner.
  if(card.contains("quarry")){var q=card.getCompound("quarry");
   var line=q.getBoolean("worked")?text("quarry_worked",q.getInt("taken")):q.getBoolean("claimed")?text("quarry_working",q.getInt("taken"),q.getInt("layer"),q.getInt("floor")):text("quarry_waiting",q.getInt("floor"));
   Tone t=!q.getBoolean("miner")?Tone.BAD:q.getBoolean("worked")?Tone.OK:q.getBoolean("claimed")?Tone.INFO:Tone.WAIT;
   // Without a miner the chip says only that (short and red); the pit's state is in the tooltip.
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(Items.STONE_PICKAXE),q.getBoolean("miner")?line:text("quarry_no_miner"),Component.empty(),t);
   if(office)tips.add(new Rect(x,y+2,w,18),q.getBoolean("miner")?new Component[]{line}:new Component[]{line,text("quarry_no_miner")});y+=22;}
  return cols==2?Math.max(y,yr):y;
 }
 /** The core line of a card: the core's icon, 'Ядро: II' (amber when the building is kept higher than its core lets it work), six grade
  *  pips, and one line of what its active effects give at the working level; the whole I..VI table is in the tooltip. */
 private static int core(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office){
  var tips=OfficeUi.tips();var c=card.getCompound("core");int grade=c.getInt("grade"),kept=card.getCompound("upgrade").getInt("kept"),pipsW=6*7-2;
  Tone tone=Math.max(1,grade)<kept?Tone.WAIT:Tone.INFO;var label=grade>0?text("core",OfficeUi.roman(grade)):text("core_missing");
  g.renderItem(OfficeUi.icon(c.getString("item")),x,y+1);OfficeUi.label(g,f,label,x+20,y+5,w-20-pipsW-4,tone==Tone.WAIT?tone.fg():OfficeUi.TEXT);
  OfficeUi.pips(g,x+w-pipsW,y+7,grade,grade,6);
  var type=org.villageastra.domain.CoreCatalog.coreType(card.getString("type"));
  if(office){var lines=new ArrayList<Component>();lines.add(label);lines.add(OfficeUi.icon(c.getString("item")).getHoverName());if(type!=null)lines.addAll(org.villageastra.world.CoreItem.effectLines(type));tips.add(new Rect(x,y,w,18),lines.toArray(Component[]::new));}
  y+=20;var now=Component.empty();int active=0;
  for(var raw:c.getList("effects",Tag.TAG_COMPOUND)){var e=(CompoundTag)raw;if(!e.getBoolean("active"))continue;if(active++>0)now.append(" · ");now.append(Component.translatable("core.villageastra.effect."+e.getString("id"))).append(": "+e.getInt("now"));}
  var line=active>0?now:Component.translatable("core.villageastra.inactive");
  OfficeUi.label(g,f,line,x,y,w,active>0?OfficeUi.MUTED:Tone.OFF.fg());if(office)tips.add(new Rect(x,y-1,w,11),line);
  return y+12;
 }
 /** A refusal's tone, shared with the office's upgrade button: what stops the building red, a busy crew amber, the rest grey. */
 /** AD-135: one line per annex the building may take, green when built, blue while queued or ready, red when its site is taken, grey
  *  while its level or research is missing; what the annex gives in the tooltip. An annex's own card names its building instead. */
 static int annexes(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office){
  if(card.hasUUID("annexOf")){var of=Component.translatable("annex.villageastra.of",Component.translatable("building.villageastra."+card.getString("annexOfType")));
   OfficeUi.chip(g,f,x,y+2,w,OfficeUi.buildingIcon(card.getString("annexOfType")),of,Component.empty(),Tone.INFO);if(office)OfficeUi.tips().add(new Rect(x,y+2,w,18),of,Component.translatable("annex.villageastra."+card.getString("type")+".desc"));y+=22;}
  for(var raw:card.getList("annexes",Tag.TAG_COMPOUND)){var a=(CompoundTag)raw;String type=a.getString("type"),state=a.getString("state");
   // The short name on the line, "Annex: name" in the tooltip: the state after it must never push the name off the card.
   var name=Component.translatable("building.villageastra."+type);var label=Component.translatable("annex.villageastra.label",name);
   var value=annexState(a);
   Tone tone=switch(state){case "built"->Tone.OK;case "queued","ready"->Tone.INFO;case "busy"->Tone.WAIT;case "parent_level","research"->Tone.OFF;default->Tone.BAD;};
   // One label "name · state": the chip never cuts its value, so a long state would push the name off a narrow card.
   OfficeUi.chip(g,f,x,y+2,w,OfficeUi.buildingIcon(type),name.copy().append(" · ").append(value),Component.empty(),tone);
   if(office)OfficeUi.tips().add(new Rect(x,y+2,w,18),label,value,Component.translatable("annex.villageastra."+type+".desc"),Component.translatable("annex.villageastra.rule"));
   y+=22;}
  return y;
 }
 /** What an annex line says after its name. */
 static Component annexState(CompoundTag a){String state=a.getString("state");
  return switch(state){case "parent_level"->Component.translatable("annex.villageastra.state.parent_level",OfficeUi.roman(a.getInt("parentLevel")));
   case "research"->Component.translatable("annex.villageastra.state.research",OfficeUi.research(a.getString("research")));
   case "site"->Component.translatable("annex.villageastra.state.site",a.getInt("conflicts"));
   case "built","queued","ready","busy","besieged"->Component.translatable("annex.villageastra.state."+state);
   default->Component.translatable("annex.villageastra.refused."+state);};}
 /** AD-135: the first annex of the card that is not built nor queued (the footer button orders it), or null. */
 static CompoundTag annexToOrder(CompoundTag card){for(var raw:card.getList("annexes",Tag.TAG_COMPOUND)){var a=(CompoundTag)raw;String s=a.getString("state");if(!s.equals("built")&&!s.equals("queued"))return a;}return null;}
 static void orderAnnex(UUID village,long epoch,CompoundTag card,CompoundTag annex){ConstructionNetwork.sendAnnex(new ConstructionNetwork.AnnexOrder(village,epoch,card.getUUID("id"),annex.getString("type")));}
 /** AD-138: the pens of a livestock yard; amber while a feeder is empty, red above sixteen. */
 static int pens(GuiGraphics g,Font f,CompoundTag card,int x,int y,int w,boolean office,int mx,int my){
  var snap=ConstructionOverlay.snapshot();boolean manage=snap.getBoolean("canManage");
  for(var raw:card.getList("pens",Tag.TAG_COMPOUND)){var p=(CompoundTag)raw;int pen=p.getInt("pen"),head=p.getInt("head"),cap=p.getInt("cap"),feed=p.getInt("feed");String kind=p.getString("species");
   var label=Component.translatable("pen.villageastra.line",pen,Component.translatable("pen.villageastra.kind."+kind));
   var value=Component.literal(head+"/"+cap+(feed>=0?"  "+"■".repeat(feed)+"□".repeat(org.villageastra.world.FeederBlock.MAX-feed):""));
   Tone tone=head>cap?Tone.BAD:feed==0?Tone.WAIT:Tone.INFO;var r=new Rect(x,y+2,w,18);boolean hover=office&&manage&&r.contains(mx,my);
   OfficeUi.chip(g,f,x,y+2,w,new ItemStack(switch(kind){case "cow"->Items.LEATHER;case "chicken"->Items.FEATHER;case "pig"->Items.PORKCHOP;default->Items.WHITE_WOOL;}),label,value,tone,office&&manage,hover);
   if(office){OfficeUi.tips().add(r,label,Component.translatable("pen.villageastra.tip",head,cap,p.getInt("adults"),Math.max(0,feed)*org.villageastra.world.FeederBlock.ITEMS_PER_STEP,org.villageastra.world.FeederBlock.ITEMS_PER_STEP*org.villageastra.world.FeederBlock.MAX),Component.translatable(manage?"pen.villageastra.switch":"pen.villageastra.view"));
    if(manage&&card.hasUUID("id")&&snap.hasUUID("village")){var village=snap.getUUID("village");long epoch=snap.getLong("epoch"),revision=snap.getLong("revision");var id=card.getUUID("id");
     LINKS.add(new Link(r,()->ConstructionNetwork.sendPen(new ConstructionNetwork.PenOrder(village,id,pen,epoch,revision))));}}
   y+=22;}
  return y;
 }
 static Tone refusalTone(String why){return switch(why){case "besieged","equipment","conflicts","access"->Tone.BAD;case "busy"->Tone.WAIT;default->Tone.OFF;};}
 /** Switches the office to another tab from a card link. */
 private static void open(int section,String focus){if(net.minecraft.client.Minecraft.getInstance().screen instanceof ConstructionScreen cs)cs.open(section,focus);}
 /** AD-131: a forester's hut of level IV or more, whose sawmill the mayor may start or stop. */
 static boolean saw(CompoundTag card){return ForestCard.saw(card);}
 static boolean cartTrip(CompoundTag card){return card.hasUUID("cartTrip");}
 static void leaveCart(UUID village,long epoch,long revision,CompoundTag card){ConstructionNetwork.sendCart(new ConstructionNetwork.CartOrder(village,card.getUUID("cartTrip"),epoch,revision));}
 static void toggleSaw(UUID village,long epoch,long revision,CompoundTag card){ConstructionNetwork.sendSaw(new ConstructionNetwork.SawOrder(village,card.getUUID("id"),epoch,revision));}
 /** AD-094: a tower of the wall with a free place and a trained archer to send up. */
 static boolean archerPost(CompoundTag card){return card.contains("towerArchers")&&card.getInt("towerArchers")<card.getInt("towerPlaces")&&card.getBoolean("towerCandidate");}
 static void postArcher(UUID village,long epoch,long revision,CompoundTag card){ConstructionNetwork.sendArcher(new ConstructionNetwork.ArcherOrder(village,card.getUUID("id"),epoch,revision));}
 /** AD-093, AD-131: more than one kind of tree is opened, so the mayor may pick the hut's preferred one. */
 static boolean speciesChoice(CompoundTag card){return ForestCard.speciesChoice(card);}
 static void nextSpecies(UUID village,long epoch,long revision,CompoundTag card){ConstructionNetwork.sendSpecies(new ConstructionNetwork.SpeciesOrder(village,card.getUUID("id"),epoch,revision));}
 static boolean farm(CompoundTag card){return card.contains("crop");}
 static boolean upgradable(CompoundTag card){var u=card.getCompound("upgrade");return card.contains("upgrade")&&u.getInt("kept")<u.getInt("max");}
 /** The level to upgrade to as the pips count it (I..VI), so the button and the card name it the same way. */
 static Component upgradeLabel(CompoundTag card){return card.contains("upgrade")&&card.getCompound("upgrade").contains("next")?text("upgrade_to",OfficeUi.roman(card.getCompound("upgrade").getInt("next"))):text("upgrade_to",OfficeUi.roman(card.getCompound("upgrade").getInt("kept")+1));}
 /** Next crop for this one farm: the order names the building, so no other farm changes. */
 static void nextCrop(UUID village,long epoch,long revision,CompoundTag card){
  // AD-093: only a crop the village has opened comes next; a locked one waits for its quest.
  var open=new java.util.HashSet<String>();for(var raw:card.getList("openCrops",net.minecraft.nbt.Tag.TAG_STRING))open.add(raw.getAsString());
  var values=FarmCrops.values();var now=FarmCrops.from(card.getString("crop"));var next=now;
  for(int i=1;i<=values.length;i++){var c=values[(now.ordinal()+i)%values.length];if(open.isEmpty()||open.contains(c.id())){next=c;break;}}
  if(next==now)return;
  ConstructionNetwork.sendFarm(new ConstructionNetwork.FarmOrder(village,card.getUUID("id"),epoch,revision,next.id()));
 }
 static void upgrade(UUID village,long epoch,CompoundTag card){ConstructionNetwork.sendUpgrade(new ConstructionNetwork.UpgradeOrder(village,epoch,card.getUUID("id")));}
}
