package org.villageastra.client;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Diagnostic recipe reachability, explicitly not proof of deposits or physical long-distance production. */
final class SupplyAudit {
 static void write(MinecraftServer server)throws Exception{
  var l=server.overworld();var materials=new TreeMap<String,Set<String>>();var s=new Settlement(UUID.randomUUID());
  for(var d:BuildingBlueprints.designs()){for(var raw:BuildingBlueprints.layout(d.id(),net.minecraft.core.BlockPos.ZERO).values()){var state=BuildingOrders.payable(raw);var item=state.is(org.villageastra.VillageAstra.OWNED_CHEST.get())?net.minecraft.world.item.Items.CHEST:state.getBlock().asItem();if(item!=net.minecraft.world.item.Items.AIR)materials.computeIfAbsent(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString(),k->new TreeSet<>()).add(d.id());}if(d.id().equals(BuildingBlueprints.base(d.id())))s.addBuilding(new Settlement.Building(UUID.randomUUID(),d.id(),10000,0,10000));}
  for(var b:s.buildings())if(BuildingTiers.upgradable(b.type()))for(int level=2;level<=6;level++){String design=b.type()+"@"+level;BuildingTiers.cost(b.type(),level).keySet().forEach(item->materials.computeIfAbsent(item,k->new TreeSet<>()).add(design));}
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));s.admit(new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1),home);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),new net.minecraft.core.BlockPos(20000,80,20000));var rows=new JsonArray();var missing=new ArrayList<String>();
  for(var pair:materials.entrySet()){boolean possible=Workshops.makeable(l,e,pair.getKey());var row=new JsonObject();row.addProperty("item",pair.getKey());row.addProperty("recipe_or_source_capability",possible);row.add("buildings",new Gson().toJsonTree(pair.getValue()));rows.add(row);if(!possible)missing.add(pair.getKey());}
  var out=new JsonObject();out.addProperty("scope","Recipe/source capability with every building type, empty inventory; does not prove local availability, travel, tools, or dimension access");out.add("materials",rows);
  var path=Path.of("../docs/runs/supply-material-audit.json");Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(out));com.mojang.logging.LogUtils.getLogger().info("ASTRA_SUPPLY audit materials={} unsupported={} file={}",materials.size(),missing,path);
 }
}
