package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.persistence.NbtRecord;
/** Durable identity index: an old chunk may join long after its raid has ended or another wave has started. */
public final class RaidRoster {
 private RaidRoster(){}
 private static final Map<Path,CompoundTag> CACHE=new HashMap<>();
 private static Path path(ServerLevel l){return l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-raid-roster.bin");}
 private static boolean include(CompoundTag index,UUID village,CompoundTag active){boolean changed=false;
  for(var raw:active.getList("mobs",Tag.TAG_INT_ARRAY)){var key=NbtUtils.loadUUID(raw).toString();if(!index.hasUUID(key)){index.putUUID(key,village);changed=true;}}return changed;
 }
 private static CompoundTag index(ServerLevel l){var p=path(l);var found=CACHE.get(p);if(found!=null)return found;
  var t=Files.exists(p)?NbtRecord.read(p):new CompoundTag();boolean changed=false;var folder=p.getParent().resolve("astra-raids");
  // Import active schema-1 waves. Their saved entities only have the old AstraRaid tag, not a village tag.
  if(Files.isDirectory(folder))try(var files=Files.newDirectoryStream(folder,"*.bin")){for(var file:files){UUID village;
   try{village=UUID.fromString(file.getFileName().toString().replace(".bin",""));}catch(IllegalArgumentException ignored){continue;}
   changed|=include(t,village,NbtRecord.read(file).getCompound("active"));
  }}catch(java.io.IOException ex){throw new IllegalStateException("Cannot migrate raid roster",ex);}
  if(changed)NbtRecord.write(p,t);CACHE.put(p,t);return t;
 }
 public static void remember(ServerLevel l,UUID village,CompoundTag active){var t=index(l);if(include(t,village,active))NbtRecord.write(path(l),t);}
 public static UUID village(ServerLevel l,Entity mob){if(!mob.getTags().contains("AstraRaid"))return null;var t=index(l);return t.hasUUID(mob.getUUID().toString())?t.getUUID(mob.getUUID().toString()):null;}
 public static boolean contains(CompoundTag active,UUID id){return active.getList("mobs",Tag.TAG_INT_ARRAY).stream().anyMatch(raw->id.equals(NbtUtils.loadUUID(raw)));}
 public static boolean mayJoin(ServerLevel l,Entity mob){var village=village(l,mob);if(village==null)return true;
  var active=Raids.record(l,village).getCompound("active");return contains(active,mob.getUUID())&&!active.getCompound("gone").getBoolean(mob.getUUID().toString());
 }
 public static void clear(){CACHE.clear();}
}
