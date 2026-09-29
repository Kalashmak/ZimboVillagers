package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.villageastra.world.LivestockPens;
/** AD-138 (owner 2026-09-22: "different animals allowed per pen"): the kind each pen of a livestock yard keeps — the mayor's choice, else the
 *  pen's own (balance/livestock.json: sheep, cows, chickens, pigs). A pen changes kind only when it is empty of the old one ("occupied"):
 *  nothing is slaughtered by a change. Schema 1 (villageastra_pens.dat). */
public final class LivestockPolicies extends SavedData {
 public static final List<String> KINDS=List.of("sheep","cow","chicken","pig");
 private record Key(UUID village,UUID yard,int pen){}
 private final Map<Key,String> species=new LinkedHashMap<>();
 public static LivestockPolicies get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(LivestockPolicies::load,()->{var p=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra_pens.dat");if(java.nio.file.Files.exists(p)||java.nio.file.Files.exists(p.resolveSibling("villageastra_pens.dat_old")))throw new IllegalStateException("Refusing to replace corrupt pen policies");return new LivestockPolicies();},"villageastra_pens");}
 /** The kind a pen keeps. */
 public String species(UUID village,UUID yard,LivestockPens.Pen p){return species.getOrDefault(new Key(village,yard,p.index()),p.species());}
 /** The next kind for one pen, or why not: "" = changed; village, mayor, building, pen, kind, besieged, occupied. */
 public static String order(ServerPlayer p,UUID village,UUID yard,int pen,long epoch,long revision){
  var data=SettlementData.get(p.server);var e=data.entry(village);if(e==null)return "village";
  if(!ManagementOrders.allowedContext(p,e)||!e.settlement().governance().canManage(p.getUUID(),epoch)||e.settlement().governance().revision()!=revision)return "mayor";
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(yard)&&x.type().equals("livestock")).findFirst().orElse(null);if(b==null)return "building";
  var pp=LivestockPens.pen(pen);if(pp==null||pp.index()>LivestockPens.pens(org.villageastra.world.BuildingLevels.level(p.serverLevel(),e,b)))return "pen";
  if(org.villageastra.world.Sieges.besieged(p.server,village))return "besieged";
  var policies=get(p.server);var now=policies.species(village,yard,pp);
  if(!LivestockPens.herd(p.serverLevel(),e,b,pp).isEmpty())return "occupied";
  var next=KINDS.get((KINDS.indexOf(now)+1)%KINDS.size());
  if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return "mayor";
  policies.species.put(new Key(village,yard,pen),next);policies.setDirty();data.setDirty();return "";
 }
 public static LivestockPolicies load(CompoundTag tag){
  if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")!=1)throw new IllegalArgumentException("Invalid pen policies");var data=new LivestockPolicies();
  for(var raw:ElectionNbt.list(tag,"pens")){var t=(CompoundTag)raw;var kind=t.getString("species");if(!KINDS.contains(kind))throw new IllegalArgumentException("Unknown pen kind "+kind);
   if(data.species.putIfAbsent(new Key(t.getUUID("village"),t.getUUID("yard"),t.getInt("pen")),kind)!=null)throw new IllegalArgumentException("Duplicate pen policy");}
  return data;
 }
 @Override public CompoundTag save(CompoundTag tag){tag.putInt("schema",1);var rows=new ListTag();
  species.forEach((k,v)->{var t=new CompoundTag();t.putUUID("village",k.village());t.putUUID("yard",k.yard());t.putInt("pen",k.pen());t.putString("species",v);rows.add(t);});
  tag.put("pens",rows);return tag;}
}
