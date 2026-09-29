package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.villageastra.world.FarmCrops;
import org.villageastra.world.FarmField;
/** Policy belongs to a registered building, independently of its current worker or GUI.
 *  AD-130 (owner 2026-09-22): a crop for each of the farm's 18 fields — the farm's crop ("all fields") and, per field, the crop chosen for it
 *  instead; a field not yet built keeps its choice until it is. A change is taken up at the field's next sowing: nothing growing is torn out.
 *  Schema 2 (villageastra_farms.dat); a schema-1 file's crop is read as the farm's crop with no field of its own. */
public final class FarmPolicies extends SavedData {
 private record Key(UUID village,UUID building){}
 private final Map<Key,FarmCrops> policies=new LinkedHashMap<>();
 private final Map<Key,FarmCrops[]> fields=new LinkedHashMap<>();
 public static FarmPolicies get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FarmPolicies::load,()->{var p=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra_farms.dat");if(java.nio.file.Files.exists(p)||java.nio.file.Files.exists(p.resolveSibling("villageastra_farms.dat_old")))throw new IllegalStateException("Refusing to replace corrupt farm policies");return new FarmPolicies();},"villageastra_farms");}
 /** The farm's crop: what every field without a choice of its own grows. */
 public FarmCrops crop(UUID village,UUID building){return policies.getOrDefault(new Key(village,building),FarmCrops.WHEAT);}
 /** AD-130: the crop of one field (1..18): its own choice, else the farm's. */
 public FarmCrops crop(UUID village,UUID building,int field){var own=fields.get(new Key(village,building));var c=own==null||field<1||field>FarmField.FIELDS?null:own[field-1];return c==null?crop(village,building):c;}
 /** AD-130: whether a field has a crop of its own. */
 public boolean own(UUID village,UUID building,int field){var own=fields.get(new Key(village,building));return own!=null&&field>=1&&field<=FarmField.FIELDS&&own[field-1]!=null;}
 // AD-104: a plot of the farm's modules (FarmField), wherever the farm is turned; AD-130: the crop of the field it lies in.
 public FarmCrops at(SettlementData.Entry e,BlockPos pos){
  for(var b:e.settlement().buildings())if(b.type().equals("farm")){var m=FarmField.moduleAt(e,b,pos);if(m!=null)return crop(e.settlement().id(),b.id(),FarmField.number(m,FarmField.legacy(e.settlement())));}
  throw new IllegalArgumentException("Not a registered field cell");
 }
 /** The farm's crop for all its fields (clears every field's own choice). */
 public static boolean order(ServerPlayer p,UUID village,UUID building,long epoch,long revision,String crop){return order(p,village,building,epoch,revision,0,crop);}
 /** AD-130: field 0 sets the crop of all the farm's fields and clears their own choices; 1..18 one field's crop. The crop must be opened
  *  (CropUnlocks), the order is the mayor's and costs a governance revision; an order that changes nothing is refused. */
 public static boolean order(ServerPlayer p,UUID village,UUID building,long epoch,long revision,int field,String crop){
  var data=SettlementData.get(p.server);var e=data.entry(village);
  // AD-130: only a farm of a layout-6 village has 18 fields of its own; one keeping the AD-104 table takes the farm-wide order alone.
  if(field<0||field>FarmField.FIELDS||!ManagementOrders.allowedContext(p,e)||field>0&&FarmField.legacy(e.settlement())||!e.settlement().governance().canManage(p.getUUID(),epoch)||e.settlement().governance().revision()!=revision||e.settlement().buildings().stream().noneMatch(b->b.id().equals(building)&&b.type().equals("farm")))return false;
  FarmCrops value;try{value=FarmCrops.from(crop);}catch(IllegalArgumentException ex){return false;}
  if(!org.villageastra.world.CropUnlocks.unlocked(p.serverLevel(),e,org.villageastra.world.CropUnlocks.of(value)))return false;
  var policies=get(p.server);var key=new Key(village,building);
  if(field==0?policies.crop(village,building)==value&&!policies.fields.containsKey(key):policies.crop(village,building,field)==value)return false;
  if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;
  policies.set(key,field,value);policies.setDirty();data.setDirty();return true;
 }
 private void set(Key key,int field,FarmCrops value){
  if(field==0){policies.put(key,value);fields.remove(key);return;}
  var own=fields.computeIfAbsent(key,k->new FarmCrops[FarmField.FIELDS]);own[field-1]=value;
 }
 /** AD-130: the crops of a farm's 18 fields as the card shows them ("" = the farm's crop). */
 public ListTag view(UUID village,UUID building){var out=new ListTag();var own=fields.get(new Key(village,building));for(int i=0;i<FarmField.FIELDS;i++)out.add(StringTag.valueOf(own==null||own[i]==null?"":own[i].id()));return out;}
 public static void addView(ServerPlayer p,CompoundTag tag){if(!tag.hasUUID("village"))return;var e=SettlementData.get(p.server).entry(tag.getUUID("village"));var rows=new ListTag();var data=get(p.server);for(var b:e.settlement().buildings())if(b.type().equals("farm")&&rows.size()<64){var t=new CompoundTag();t.putUUID("building",b.id());t.putString("crop",data.crop(e.settlement().id(),b.id()).id());t.put("fields",data.view(e.settlement().id(),b.id()));t.putLong("pos",e.center().offset(b.x(),b.y(),b.z()).asLong());rows.add(t);}tag.put("farms",rows);}
 public static FarmPolicies load(CompoundTag tag){
  if(!tag.contains("schema",Tag.TAG_INT)||tag.getInt("schema")<1||tag.getInt("schema")>2)throw new IllegalArgumentException("Invalid farm policies");var data=new FarmPolicies();
  for(var raw:ElectionNbt.list(tag,"policies")){var t=(CompoundTag)raw;var key=new Key(t.getUUID("village"),t.getUUID("building"));if(data.policies.putIfAbsent(key,FarmCrops.from(t.getString("crop")))!=null)throw new IllegalArgumentException("Duplicate farm policy");
   if(t.contains("fields",Tag.TAG_LIST)){var list=t.getList("fields",Tag.TAG_STRING);if(list.size()!=FarmField.FIELDS)throw new IllegalArgumentException("A farm policy names "+FarmField.FIELDS+" fields");
    var own=new FarmCrops[FarmField.FIELDS];boolean any=false;for(int i=0;i<list.size();i++){var id=list.getString(i);if(!id.isEmpty()){own[i]=FarmCrops.from(id);any=true;}}if(any)data.fields.put(key,own);}}
  return data;
 }
 @Override public CompoundTag save(CompoundTag tag){tag.putInt("schema",2);var rows=new ListTag();
  var keys=new LinkedHashSet<Key>(policies.keySet());keys.addAll(fields.keySet());
  for(var key:keys){var t=new CompoundTag();t.putUUID("village",key.village);t.putUUID("building",key.building);t.putString("crop",crop(key.village,key.building).id());if(fields.containsKey(key))t.put("fields",view(key.village,key.building));rows.add(t);}
  tag.put("policies",rows);return tag;}
}
