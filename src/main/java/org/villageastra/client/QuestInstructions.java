package org.villageastra.client;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
/** Visible action instructions, in addition to the numeric objective and route. */
final class QuestInstructions {
 static Component describe(CompoundTag q){String type=q.getString("template");String key=switch(type){case "supply"->"supply";case "donation"->"donation";case "clearing","defence","lair","beast"->"combat";case "bring","rescue"->"escort";case "captive","lost","collapse","beacon","embassy","ruin","survey","relic","sanctuary","crypt","warcamp"->type;case "escort"->"caravan";case "flock","sinkhole","brood","sow","tidings"->type;case "plea"->"plea";default->q.getBoolean("handover")?"handover":"explore";};return Component.translatable("quest.villageastra.instructions."+key,q.getInt("towerStone"),q.getInt("towerCoal"));}
}
