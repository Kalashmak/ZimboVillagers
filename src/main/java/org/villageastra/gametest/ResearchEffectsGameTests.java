package org.villageastra.gametest;
import java.util.*;
import java.io.*;
import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ResearchEffectsGameTests {
 private static void keys(GameTestHelper h,Component c,JsonObject lang){if(c.getContents() instanceof TranslatableContents t){if(t.getKey().contains("villageastra"))h.assertTrue(lang.has(t.getKey()),"Missing player-facing translation: "+t.getKey());for(var arg:t.getArgs())if(arg instanceof Component nested)keys(h,nested,lang);}for(var child:c.getSiblings())keys(h,child,lang);}
 @GameTest(template="empty",timeoutTicks=100) public static void allResearchCardsHaveFactsInBothLanguages(GameTestHelper h)throws Exception {
  for(var locale:List.of("ru_ru","en_us"))try(var stream=ResearchEffectsGameTests.class.getResourceAsStream("/assets/villageastra/lang/"+locale+".json")){
   var lang=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
   for(var id:ResearchCatalog.NODES.keySet()){keys(h,ResearchEffects.title(id),lang);var rows=ResearchEffects.describe(id);h.assertTrue(!rows.isEmpty(),"No empty card: "+id);for(var row:rows)keys(h,row,lang);}
  }
  h.assertTrue(ResearchEffects.unlocks("housing.1").contains(new ResearchEffects.Unlock("home",2)),"Describe the actual current house upgrade, not a promised larger blueprint");
  h.assertTrue(BuildingOrders.capacity("home@2")==2&&BuildingOrders.capacity("home_2")==4,"Current housing capacity belongs to the actual blueprint");
  // AD-123: roads.4 now opens cobblestone (×1.5), a real surface: its card shows that surface and no longer says prerequisite only.
  h.assertTrue(ResearchEffects.describe("roads.4").stream().noneMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("prerequisite_only"))
    &&ResearchEffects.describe("roads.4").stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("fact.surface")),"Roads IV shows its live cobblestone surface");
  // AD-123 R4: only the LATER nodes keep the prerequisite-only line; Agriculture I shows its live bone meal feeding.
  var only=new TreeSet<String>();for(var id:ResearchCatalog.NODES.keySet())if(ResearchEffects.describe(id).stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("prerequisite_only")))only.add(id);
  // AD-136 (§4.3): the one prerequisite-only node of tree v2 is Forestry I; every PLANNED node says so on its card, and no planned line
  // is drawn as «было → стало».
  h.assertTrue(only.equals(new TreeSet<>(List.of("forestry.1"))),"Prerequisite-only cards: "+only);
  for(var n:ResearchCatalog.NODES.values()){var rows=ResearchEffects.describe(n.id());
   boolean planned=rows.stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("fact.planned"));
   boolean future=rows.stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("fact.future"));
   if(n.status().equals("PLANNED"))h.assertTrue(planned&&rows.stream().noneMatch(c->c.getContents() instanceof TranslatableContents t&&(t.getKey().endsWith("fact.number")||t.getKey().endsWith("fact.value"))),n.id()+": a PLANNED card shows only its planned line");
   if(n.status().equals("INTERIM"))h.assertTrue(future,n.id()+": an INTERIM card carries its grey future line");
   if(n.status().equals("ACTIVE"))h.assertTrue(!planned&&!future,n.id()+": an ACTIVE card promises nothing");}
  h.assertTrue(ResearchEffects.describe("agriculture.1").stream().anyMatch(c->c.getContents() instanceof TranslatableContents t&&t.getKey().endsWith("fact.knob.bone_meal")),"Agriculture I shows bone meal feeding");
  h.assertTrue(ResearchEffects.unlocks("housing.1").contains(new ResearchEffects.Unlock("home",2))&&ResearchGate.forDesign("home_2").equals(List.of("housing.2")),"Housing II opens the big house for new projects");
  h.succeed();
 }
}
