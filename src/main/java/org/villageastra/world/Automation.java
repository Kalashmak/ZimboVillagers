package org.villageastra.world;
import net.minecraft.server.MinecraftServer;
import org.villageastra.server.SettlementData;
import java.util.*;
/** AD-055: the single automaton — a driven station keeps working without a worker, slower than a pair of hands and on exactly the same materials. */
public final class Automation {
 /** A driven station takes a turn this often; a worker at the bench takes one every 20 ticks. */
 public static final int PERIOD=60;
 private Automation(){}
 /** AD-136 (D9): what the top of a building's own ladder mechanizes — bench (the station turns without a worker), cycle (its field),
  *  dig (adit or quarry), haul (one delivery a turn), sort (AD-147: the warehouse keeps its store sorted, WarehouseSort) — from
  *  balance/automation.json; the level itself needs the branch's research. */
 public record Grant(boolean bench,boolean cycle,boolean dig,boolean haul,boolean sort){public boolean any(){return bench||cycle||dig||haul||sort;}}
 public static final Grant NONE=new Grant(false,false,false,false,false);
 private record Row(int level,int legacyLevel,Grant grant){}
 private static final Map<String,Row> TABLE=table();
 private static Map<String,Row> table(){
  try(var s=Automation.class.getResourceAsStream("/data/villageastra/balance/automation.json")){if(s==null)throw new IllegalStateException("Missing automation balance");
   var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var out=new HashMap<String,Row>();
   for(var en:root.getAsJsonObject("buildings").entrySet()){var o=en.getValue().getAsJsonObject();int level=o.get("level").getAsInt();
    if(level<1||level>6)throw new IllegalStateException("Invalid automation level of "+en.getKey());
    java.util.function.Predicate<String> f=k->o.has(k)&&o.get(k).getAsBoolean();
    out.put(en.getKey(),new Row(level,o.has("legacy_level")?o.get("legacy_level").getAsInt():level,new Grant(f.test("bench"),f.test("cycle"),f.test("dig"),f.test("haul"),f.test("sort"))));}
   return Map.copyOf(out);
  }catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 /** The level from which a building type runs its machine (0: never); a farm that keeps the AD-104 field table at its legacy level. */
 public static int level(String type,boolean legacyFarm){var r=TABLE.get(org.villageastra.domain.CoreCatalog.canonical(type));return r==null?0:legacyFarm?r.legacyLevel():r.level();}
 /** What the table grants a type at its level (without looking at the world). */
 public static Grant grant(String type){var r=TABLE.get(org.villageastra.domain.CoreCatalog.canonical(type));return r==null?NONE:r.grant();}
 /** What this building's own machinery does now: its working level reached the table's level. */
 public static Grant at(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,org.villageastra.domain.Settlement.Building b){
  var r=TABLE.get(org.villageastra.domain.CoreCatalog.canonical(b.type()));if(r==null)return NONE;
  int from=b.type().equals("farm")&&FarmField.legacy(e.settlement())?r.legacyLevel():r.level();int level=BuildingLevels.level(l,e,b);
  if(level<from)return NONE;
  // An older village's farm cycles its field from V (AD-104), but hauls by itself only at VI, the top of every ladder (spec 3.1).
  var g=r.grant();return level>=r.level()?g:new Grant(g.bench(),g.cycle(),g.dig(),false,false);
 }
 /** Advances every driven station of every settlement. Nothing is produced that the station's own chest did not pay for. */
 public static int tick(MinecraftServer server,long now){
  int worked=0;
  for(var e:SettlementData.get(server).entries()){
   var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));
   // AD-111: a touched chunk is loaded but asleep; stations turn only where a player keeps the village ticking.
   if(level==null||!level.hasChunkAt(e.center())||!TouchLoad.ticking(level,e.center()))continue;
   worked+=tick(level,e,now);
  }
  return worked;
 }
 /** One settlement's turn: every machine of it — the drive of AD-055 and the levels IV…VI of AD-073 (AD-076). */
 public static int tick(net.minecraft.server.level.ServerLevel level,SettlementData.Entry e,long now){var wants=Workshops.wants(level,e);
  // AD-131: the forester's saw (IV+) and courtyard grove (VI) are the hut's own, outside the mechanics of Machines.
  return Machines.tick(level,e,now,wants)+ForestryMachines.tick(level,e,now,wants);}
 private static boolean busy(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,org.villageastra.domain.Settlement.Building station){
  var at=Workshops.station(e,station);
  for(var npc:l.getEntitiesOfClass(ResidentEntity.class,new net.minecraft.world.phys.AABB(at).inflate(4))){
   var r=e.settlement().resident(npc.getUUID());
   if(r!=null&&r.profession()!=null&&station.id().equals(idOf(e,r)))return true;
  }
  return false;
 }
 private static java.util.UUID idOf(SettlementData.Entry e,org.villageastra.domain.Resident r){
  var b=e.settlement().workplace(r.id());return b==null?null:b.id();
 }
}
