package org.villageastra.world;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
/** AD-091: how a quest between two villages touches their relations. The deltas live with the quest balance ("relations" of a template);
 *  the relations themselves are the other side's model (AD-100). */
final class QuestRelations {
 private QuestRelations(){}
 static int delta(String template){var spec=Quests.spec(template);return spec.has("relations")?spec.get("relations").getAsInt():0;}
 static void change(MinecraftServer s,UUID a,UUID b,String template){
  int delta=delta(template);if(delta==0||a.equals(b))return;
  Relations.change(s,a,b,delta,"quest_"+template);
 }
}
