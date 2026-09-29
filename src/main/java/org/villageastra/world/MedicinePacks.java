package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Resident;
import org.villageastra.persistence.NbtRecord;
/** One paid bandage carried by a resident. The delivery receipt survives consumption and reload. */
public final class MedicinePacks {
 private MedicinePacks(){}
 private static Path path(ServerLevel l,UUID resident){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-medicine/packs/"+resident+".bin");}
 private static CompoundTag read(ServerLevel l,UUID resident){return Files.exists(path(l,resident))?NbtRecord.read(path(l,resident)):new CompoundTag();}
 public static boolean carried(ServerLevel l,UUID resident){return read(l,resident).getBoolean("held");}
 public static boolean received(ServerLevel l,UUID resident,UUID delivery){var t=read(l,resident);return t.hasUUID("delivery")&&t.getUUID("delivery").equals(delivery);}
 public static boolean give(ServerLevel l,UUID resident,UUID delivery){var t=read(l,resident);if(received(l,resident,delivery))return true;if(t.getBoolean("held"))return false;
  t.putUUID("delivery",delivery);t.putBoolean("held",true);NbtRecord.write(path(l,resident),t);return true;}
 public static boolean use(ServerLevel l,Resident resident){if(!resident.alive())return false;var body=l.getEntity(resident.id());var npc=body instanceof ResidentEntity n&&n.isAlive()?n:null;boolean injured=npc!=null&&npc.getHealth()<=npc.getMaxHealth()-DoctorGoal.INJURED;if(!resident.sick()&&!injured)return false;var t=read(l,resident.id());if(!t.getBoolean("held"))return false;
  // At-most-once: persist consumption first, as DoctorGoal does for a treatment. A crash can lose the cure, never copy medicine.
  t.putBoolean("held",false);NbtRecord.write(path(l,resident.id()),t);resident.cure();if(injured)npc.heal(DoctorGoal.HEAL);return true;}
}
