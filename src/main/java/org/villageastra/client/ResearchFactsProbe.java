package org.villageastra.client;
import java.util.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.ResearchEffects;
/** Actual tree selection, scrolling and small-window layout for the facts displayed to the player. */
final class ResearchFactsProbe {
 // AD-123, AD-136: the big house of Housing II, the trails of Roads II, cobblestone ×1.5 of Roads IV, the hall crafts of Engineering I and the hall cap of Town hall II.
 private static final String[] IDS={"housing.2","roads.2","roads.4","engineering.1","town_hall.2"};
 /** Whether a card line carries its application tag (AD-123 R3). */
 private static boolean tagged(net.minecraft.network.chat.Component c){if(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().startsWith("research.villageastra.applies."))return true;for(var s:c.getSiblings())if(tagged(s))return true;return false;}
 private static int index,ticks;private static boolean open,stopped;
 static boolean enabled(){return Boolean.getBoolean("villageastra.researchFactsSmoke");}
 static void setup(net.minecraft.server.MinecraftServer s){var e=SettlementData.get(s).entries().iterator().next();e.settlement().appointPlayerMayor(s.getPlayerList().getPlayers().get(0).getUUID());SettlementData.get(s).setDirty();}
 static void tick(Minecraft mc){if(stopped)return;try{if(++ticks>2400)throw new IllegalStateException("Research facts timeout");if(!ConstructionOverlay.snapshot().getBoolean("canManage"))return;
  if(!open){OfficeProbes.open(mc,ConstructionScreen.RESEARCH);mc.screen.tick();if(!(mc.screen instanceof ResearchScreen screen))throw new IllegalStateException("No tree");var panel=screen.panel();panel.search().setValue(IDS[index]);var point=panel.frameCenter(IDS[index]);mc.screen.mouseClicked(point[0],point[1],0);mc.screen.mouseReleased(point[0],point[1],0);if(!panel.selected().equals(IDS[index]))throw new IllegalStateException("Selection failed");open=true;ticks=0;}
  else if(ticks>50){var issues=OfficeProbes.layoutProblems(mc.screen);if(!issues.isEmpty())throw new IllegalStateException("Layout: "+issues);
   var rows=ResearchEffects.describe(IDS[index]);for(var row:rows)if(row.getString().contains("villageastra."))throw new IllegalStateException("Untranslated fact: "+row.getString());
   if(rows.stream().noneMatch(ResearchFactsProbe::tagged))throw new IllegalStateException("No application tag on "+IDS[index]);
   LogUtils.getLogger().info("ASTRA_RESEARCH_FACTS card {} title '{}' rows {}",IDS[index],ResearchEffects.title(IDS[index]).getString(),rows.stream().map(r->r.getString()).toList());
   // AD-133: the same facts, read at a glance. A card with several lines must offer at least one short row, and never more than four.
   var brief=ResearchCard.rows(IDS[index]);
   if(brief.isEmpty()&&rows.size()>1)throw new IllegalStateException("No short row for "+IDS[index]+" with "+rows.size()+" fact lines");
   LogUtils.getLogger().info("ASTRA_RESEARCH_FACTS brief {} {}",IDS[index],brief.stream().map(b->b.label().getString()+" "+(b.from()==null?"":b.from()+" -> ")+(b.to()==null?"-":b.to())).toList());
   var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-research-facts-"+IDS[index]+".png");try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_RESEARCH_FACTS screenshot {}",path);
   var area=((ResearchScreen)mc.screen).panel().details();mc.screen.mouseScrolled(area.x()+5,area.bottom()-5,-3);mc.screen.tick();
   if(++index==IDS.length){audit();LogUtils.getLogger().info("ASTRA_RESEARCH_FACTS VERIFIED cards=5 selection=true translations=true layout=true");stopped=true;mc.stop();}else{open=false;ticks=0;mc.setScreen(null);}
  }
 }catch(Exception ex){stopped=true;LogUtils.getLogger().error("ASTRA_RESEARCH_FACTS FAILED",ex);mc.stop();}}
 private static boolean keyed(net.minecraft.network.chat.Component c,String suffix){return c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t&&t.getKey().equals("research.villageastra.fact."+suffix);}
 /** The owner's question of 2026-09-23: which technologies still carry no live effect of their own ('prerequisite_only'),
  *  and which carry effects but not one number a card can show. Reported, not changed: the numbers are the owner's to set. */
 private static void audit(){
  var none=new TreeSet<String>();var noNumber=new TreeSet<String>();
  for(var id:org.villageastra.domain.ResearchCatalog.NODES.keySet()){
   var rows=ResearchEffects.describe(id);
   if(rows.stream().anyMatch(c->keyed(c,"prerequisite_only"))){none.add(id);continue;}
   if(ResearchCard.rows(id).stream().noneMatch(r->r.to()!=null))noNumber.add(id);
  }
  LogUtils.getLogger().info("ASTRA_RESEARCH_AUDIT nodes={} no_effect={} {}",org.villageastra.domain.ResearchCatalog.NODES.size(),none.size(),none);
  LogUtils.getLogger().info("ASTRA_RESEARCH_AUDIT no_number={} {}",noNumber.size(),noNumber);
 }
}
