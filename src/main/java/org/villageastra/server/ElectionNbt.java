package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import org.villageastra.domain.ElectionRoll;
final class ElectionNbt {
 static ListTag list(CompoundTag t,String key){if(!(t.get(key) instanceof ListTag l)||(!l.isEmpty()&&l.getElementType()!=Tag.TAG_COMPOUND))throw new IllegalArgumentException("Invalid election list: "+key);return l;}
 static long number(CompoundTag t,String key){if(!t.contains(key,Tag.TAG_LONG))throw new IllegalArgumentException("Missing election number: "+key);return t.getLong(key);}
 static ElectionRoll read(CompoundTag tag){var rows=new ArrayList<ElectionRoll.Account>();for(var raw:list(tag,"accounts")){var t=(CompoundTag)raw;var bands=new ArrayList<ElectionRoll.Band>();for(var rawBand:list(t,"bands")){var b=(CompoundTag)rawBand;bands.add(new ElectionRoll.Band(number(b,"low"),number(b,"high"),number(b,"event")));}if(!t.contains("candidate",Tag.TAG_BYTE))throw new IllegalArgumentException("Missing candidacy");rows.add(new ElectionRoll.Account(t.getUUID("player"),number(t,"gifts"),number(t,"theft"),number(t,"decay"),t.getBoolean("candidate"),bands));}return ElectionRoll.restore(number(tag,"sequence"),number(tag,"lastDecay"),rows);}
 static CompoundTag write(ElectionRoll roll){var tag=new CompoundTag();tag.putLong("sequence",roll.sequence());tag.putLong("lastDecay",roll.lastDecay());var rows=new ListTag();for(var a:roll.accounts()){var t=new CompoundTag();t.putUUID("player",a.player());t.putLong("gifts",a.gifts());t.putLong("theft",a.theft());t.putLong("decay",a.decay());t.putBoolean("candidate",a.candidate());var bands=new ListTag();for(var b:a.bands()){var item=new CompoundTag();item.putLong("low",b.low());item.putLong("high",b.high());item.putLong("event",b.event());bands.add(item);}t.put("bands",bands);rows.add(t);}tag.put("accounts",rows);return tag;}
}
