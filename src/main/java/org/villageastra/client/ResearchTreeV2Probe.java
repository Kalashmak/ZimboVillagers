package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-136 (spec 6.3) in the real client: the research tree v2 in the office — 22 branches, the side rows under their parents and the empty
 *  place of engineering VI; the card of a level-I node lists its price in resources and «Оплатить ресурсами» pays it from the hall's chest
 *  (one journal batch, through the real GUI packet); a level-II card counts works (0/7), a PLANNED card shows only «Будет: ...»; the
 *  laboratory's seated scientist writes one work per 36 000 ticks of the village clock (advanced explicitly, as ScienceProbe); and a
 *  building is refused above the town hall's level (reason «hall»). Frames: the tree, the paid level-I card, the hall's own branch card. */
final class ResearchTreeV2Probe {
 private static int phase,ticks;private static volatile String failure;private static volatile boolean ready,served,served2,worked;private static boolean stopped;
 private static volatile String serverFacts="";private static final List<String> SHOTS=new ArrayList<>();
 static final String PAY="engineering.1",TIER2="education.2";
 static boolean enabled(){return Boolean.getBoolean("villageastra.researchTreeV2Probe");}
 private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-tree-v2-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}SHOTS.add(suffix);LogUtils.getLogger().info("ASTRA_TREE_V2 screenshot {}",path);}
 private static void server(Minecraft mc,java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var s=mc.getSingleplayerServer();s.execute(()->{try{action.accept(s);}catch(Exception ex){failure=ex.toString();}});}
 /** The laboratory with its scientist (ScienceProbe's fixture), the player as mayor, Science I and II studied, and the engineering I price
  *  (32 logs, a crafting table) in the town hall's chest. The hall stays at level I. */
 private static void fixture(net.minecraft.server.MinecraftServer s){
  ScienceProbe.setup(s);var l=s.overworld();var e=entry(s);e.settlement().appointPlayerMayor(s.getPlayerList().getPlayers().get(0).getUUID());
  var record=BookResearch.inspect(l,e);var done=record.getList("legacyDone",Tag.TAG_STRING);for(var id:List.of("research.1","research.2"))done.add(net.minecraft.nbt.StringTag.valueOf(id));
  record.put("legacyDone",done);BookResearch.store(l,e,record);
  var hall=Workshops.hall(e);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();chest.setItem(0,new ItemStack(Items.OAK_LOG,32));chest.setItem(1,new ItemStack(Items.CRAFTING_TABLE,1));chest.setChanged();
  SettlementData.get(s).setDirty();ready=true;
  LogUtils.getLogger().info("ASTRA_TREE_V2 fixture lab+scientist, hall level {}, {} price in the hall chest",e.settlement().civilization().level(),PAY);
 }
 private static ResearchPanel open(Minecraft mc){OfficeProbes.open(mc,ConstructionScreen.RESEARCH);mc.screen.tick();require(mc.screen instanceof ResearchScreen,"The science tab did not open the research tree");return ((ResearchScreen)mc.screen).panel();}
 private static void select(Minecraft mc,ResearchPanel tree,String id){tree.resetView();var frame=tree.frameCenter(id);mc.screen.mouseClicked(frame[0],frame[1],0);mc.screen.mouseReleased(frame[0],frame[1],0);
  require(tree.selected().equals(id),"The tree frame did not select "+id+" but "+tree.selected());mc.screen.tick();}
 private static ResearchPanel panel(Minecraft mc){require(mc.screen instanceof ResearchScreen,"The tree closed");return ((ResearchScreen)mc.screen).panel();}
 static void tick(Minecraft mc){if(stopped)return;try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>6000)throw new IllegalStateException("Tree v2 timeout phase="+phase);
  var snap=ConstructionOverlay.snapshot();var research=snap.getCompound("research");
  if(phase==0&&ticks>60){phase=1;ticks=0;server(mc,ResearchTreeV2Probe::fixture);}
  // 1. The tree: every branch, the side rows right under their parents, engineering scrolled into view with its empty level VI.
  else if(phase==1&&ready&&snap.getBoolean("canManage")&&research.getList("completed",Tag.TAG_STRING).size()>=2&&research.getCompound("stock").contains(PAY)){
   var tree=open(mc);tree.resetView();var rows=tree.visibleBranches();
   require(rows.size()==22&&rows.size()==ResearchCatalog.BRANCHES.size(),"22 branches in the tree: "+rows);
   for(var side:List.of("milling","masonry","carpentry"))require(rows.indexOf(side)==rows.indexOf(ResearchTreeModel.parent(side))+1,side+" under its parent: "+rows);
   tree.frameCenter("engineering.5");tree.scrollToLastColumn();phase=2;ticks=0;}
  else if(phase==2&&ticks>20){var tree=panel(mc);
   require(tree.noLevelCells().contains("engineering.6"),"Engineering VI drawn as an empty place: "+tree.noLevelCells()+" engineering.5 visible="+tree.cellVisible("engineering.5")+" density="+tree.density());
   // The tree panel's own layout rules (the office-wide tab badges are the office-ui probe's, and they settle a few frames later).
   var issues=tree.layoutProblems();require(issues.isEmpty(),"Layout: "+issues);
   mc.options.hideGui=false;capture(mc,"tree");
   // 2. The level-I card: its price line by line, and the first button pays it in resources.
   select(mc,tree,PAY);phase=3;ticks=0;}
  else if(phase==3&&ticks>20){var tree=panel(mc);var n=ResearchCatalog.get(PAY);
   require(tree.resourceLines()==n.resources().size(),"The card lists the price of "+PAY+": "+tree.resourceLines()+" of "+n.resources().size());
   require(tree.choose().getMessage().getString().equals(net.minecraft.network.chat.Component.translatable("office.villageastra.v2.research.pay").getString()),"The first button pays in resources: "+tree.choose().getMessage().getString());
   require(tree.choose().active,"Paying is allowed: "+(tree.choose().reason()==null?"":tree.choose().reason().getString()));
   require(!tree.queue().active,"A level-I node never joins the laboratory queue");
   capture(mc,"pay-card");OfficeProbes.clickOrFail(mc.screen,tree.choose(),"Pay in resources");phase=4;ticks=0;}
  else if(phase==4){
   if(research.getList("completed",Tag.TAG_STRING).stream().anyMatch(t->t.getAsString().equals(PAY))&&!served){served=true;
    server(mc,s->{var l=s.overworld();var e=entry(s);var chest=LogisticsRoutes.chest(l,e,Workshops.hall(e));var record=BookResearch.inspect(l,e);
     require(chest.countItem(Items.OAK_LOG)==0&&chest.countItem(Items.CRAFTING_TABLE)==0,"The price left the hall chest: logs "+chest.countItem(Items.OAK_LOG)+" table "+chest.countItem(Items.CRAFTING_TABLE));
     require(record.getList("resourceDone",Tag.TAG_STRING).stream().anyMatch(t->t.getAsString().equals(PAY))&&!record.contains("pay"),"Recorded paid in resources, no payment left open");
     serverFacts="paid="+PAY+" logs=0 table=0";});}
   if(served&&ticks>20){var tree=panel(mc);
    // 3. A level-II card counts works; a PLANNED card shows only the owner's words.
    select(mc,tree,TIER2);require(ResearchCatalog.get(TIER2).works()==7&&research.getCompound("paid").getInt(TIER2)==0,"Works 0/7 on "+TIER2);
    var planned=ResearchCatalog.NODES.values().stream().filter(x->x.status().equals("PLANNED")).findFirst().orElseThrow();
    var lines=ResearchEffects.describe(planned.id());
    require(lines.stream().anyMatch(c->c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("research.villageastra.fact.planned")),"A PLANNED card says «в разработке»");
    require(ResearchCard.rows(planned.id()).stream().allMatch(r->ResearchCard.future(r)||r.to()==null),"No number on a PLANNED card: "+planned.id());
    select(mc,tree,planned.id());phase=5;ticks=0;}}
  else if(phase==5&&ticks>20){var tree=panel(mc);require(tree.cardFuture(),"The grey «Будет» row is on the PLANNED card");
   select(mc,tree,"town_hall.2");phase=6;ticks=0;}
  else if(phase==6&&ticks>20){capture(mc,"hall-card");phase=7;ticks=0;}
  // 4. The laboratory writes a work by the village clock (advanced explicitly); 5. no building above the hall.
  else if(phase==7){if(worked){phase=8;ticks=0;}else if(ticks%40==0&&!served2){served2=true;server(mc,s->{try{var l=s.overworld();var e=entry(s);if(ScienceWorks.seated(l,e)<1)return;
    long clock=SettlementData.get(s).clock().ticks();var lab=ScienceWorks.lab(e);var c=LogisticsRoutes.chest(l,e,lab);int before=c.countItem(VillageAstra.RESEARCH_VOLUME.get());
    // The place was taken a few seconds ago (the server's 40-tick turn), so one work's time from now holds exactly one work;
    // the exact tick of it is ScienceWorksGameTests' to check.
    int written=ScienceWorks.advance(l,e,clock+ScienceBalance.WORK_TICKS),again=ScienceWorks.advance(l,e,clock+ScienceBalance.WORK_TICKS);
    require(written==1&&again==0&&c.countItem(VillageAstra.RESEARCH_VOLUME.get())==before+1,"One work per 36000 clock ticks: "+written+"/"+again);
    String why=BuildingTiers.refusal(l,e,lab);int hall=BuildingTiers.hallLevel(e);
    require(why.equals("hall"),"The laboratory II (Science II studied) is refused above the hall "+hall+": "+why);
    serverFacts+=" work="+written+" hall="+hall+" refusal="+why;worked=true;}finally{served2=false;}});}}
  else if(phase==8){stopped=true;
   require(SHOTS.size()==3,"Three frames: "+SHOTS);
   LogUtils.getLogger().info("ASTRA_TREE_V2 VERIFIED branches=22 side_rows=true empty_vi=true pay_card=true tier2_works=true planned=true {} reload=false",serverFacts);mc.stop();}
 }catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_TREE_V2 FAILED",ex);mc.stop();}}
}
