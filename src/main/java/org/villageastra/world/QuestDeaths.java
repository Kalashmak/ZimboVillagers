package org.villageastra.world;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
/** QUEST-004/QUEST-005 (AD-105): the death of a quest character is written where its quest can see it — a traveller lost on the way no longer
 *  holds the rescue open, an untaken rescue with nobody left is called off, a guest who dies waiting ends their admission — and whoever
 *  killed a resident of a village, a quest character or a housed one alike, pays for it there. The quest's own owner keeps the result of
 *  what they really did. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class QuestDeaths {
 private QuestDeaths(){}
 @SubscribeEvent public static void died(LivingDeathEvent event){
  if(!(event.getEntity() instanceof ResidentEntity npc)||!(npc.level() instanceof ServerLevel l))return;
  var server=l.getServer();var killer=event.getSource().getEntity() instanceof ServerPlayer player?player:null;
  // A housed resident is the village's own: killing them costs the killer there, like any quest character of that village.
  // A soldier of an army in the field against a village is fought in that village's defence, which is no murder.
  if(npc.settlementId()!=null){var army=Battle.armyOf(l,npc.getUUID());
   if(killer!=null&&(army==null||!Battle.inField(army)))Quests.murder(server,npc.settlementId(),killer.getUUID());return;}
  var data=npc.getPersistentData();long now=SettlementData.get(server).clock().ticks();
  UUID village=null;
  var guest=Camps.guest(l,npc.getUUID());
  if(guest!=null&&guest.hasUUID("village")){village=guest.getUUID("village");Camps.guestDied(l,npc.getUUID(),now);}
  if(data.hasUUID(Camps.QUEST_TAG)){
   var found=Quests.find(l,data.getUUID(Camps.QUEST_TAG));
   if(found!=null){var home=(UUID)found[0];var q=(CompoundTag)found[1];if(village==null)village=home;
    boolean arrived=q.getList("arrived",Tag.TAG_INT_ARRAY).stream().anyMatch(x->NbtUtils.loadUUID(x).equals(npc.getUUID()));
    if(!arrived&&guest==null){var lost=q.getList("lost",Tag.TAG_INT_ARRAY);lost.add(NbtUtils.createUUID(npc.getUUID()));q.put("lost",lost);Quests.store(l,home,q);
     // An errand nobody took yet whose people are all gone is called off at once, so nobody takes it only to fail.
     if(q.getString("state").equals(Quests.OPEN)&&Quests.outThere(l,q)==0)Quests.settle(l,home,q,Quests.CANCELLED,"people_lost",null);}}
   else if(village==null){var site=QuestSites.site(l,data.getUUID(Camps.QUEST_TAG));if(site!=null&&site.hasUUID("village"))village=site.getUUID("village");}}
  if(village==null&&data.hasUUID(Camps.CAMP_TAG)){var camp=Camps.camp(l,data.getUUID(Camps.CAMP_TAG));if(camp!=null&&camp.hasUUID("village"))village=camp.getUUID("village");}
  if(village!=null&&killer!=null)Quests.murder(server,village,killer.getUUID());
 }
}
