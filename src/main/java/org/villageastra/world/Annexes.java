package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-135 (owner 2026-09-23): an annex is extra equipment beside a building that gives it an effect. Unlike a level, no room is kept for it:
 *  it can be built only while its site beside the building is free. Every building type names that site in advance (balance/annexes.json),
 *  in its own lot cells, and the annex turns with its parent. The annex is a building of its own — its own lot, blocks, protection, repair
 *  and workers (it works as {@link AnnexTypes#workplace}) — linked to its parent; it keeps no level and suits every level of its parent. */
public final class Annexes {
 private Annexes(){}
 /** One annex a building type may take: its design, its parent type, the parent's kept level and research that open it, and its site. */
 public record Kind(String type,String parent,int parentLevel,String research,BlockPos site){
  public int width(){return BuildingBlueprints.design(type).width();}
  public int depth(){return BuildingBlueprints.design(type).depth();}
 }
 private static final Map<String,Kind> KINDS=read();
 private static Map<String,Kind> read(){
  try(var s=Annexes.class.getResourceAsStream("/data/villageastra/balance/annexes.json")){if(s==null)throw new IllegalStateException("Missing annexes balance");
   var root=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();var out=new LinkedHashMap<String,Kind>();
   for(var e:root.getAsJsonObject("annexes").entrySet()){var o=e.getValue().getAsJsonObject();var site=o.getAsJsonArray("site");
    if(!AnnexTypes.annex(e.getKey()))throw new IllegalStateException("Annex type unknown to AnnexTypes: "+e.getKey());
    out.put(e.getKey(),new Kind(e.getKey(),o.get("parent").getAsString(),o.get("parent_level").getAsInt(),o.has("research")?o.get("research").getAsString():null,
     new BlockPos(site.get(0).getAsInt(),site.get(1).getAsInt(),site.get(2).getAsInt())));}
   return Collections.unmodifiableMap(out);
  }catch(IOException ex){throw new IllegalStateException(ex);}
 }
 public static Collection<Kind> kinds(){return KINDS.values();}
 public static Kind kind(String type){return KINDS.get(type);}
 /** The annexes a building type may take. */
 public static List<Kind> of(String parentType){return KINDS.values().stream().filter(k->k.parent().equals(parentType)).toList();}
 /** The annex's cells in its parent's own (unturned) lot cells: outside the parent's lot, touching it. */
 public static boolean besideLot(Kind k){var d=BuildingBlueprints.design(k.parent());int w=d.width(),dp=d.depth();
  int x0=k.site().getX(),z0=k.site().getZ(),x1=x0+k.width()-1,z1=z0+k.depth()-1;
  boolean overlaps=x1>=0&&x0<w&&z1>=0&&z0<dp;boolean touches=(x1==-1||x0==w)&&z1>=0&&z0<dp||(z1==-1||z0==dp)&&x1>=0&&x0<w;
  return !overlaps&&touches;}
 /** The world's north-west corner of the annex's footprint beside its parent, as the parent stands turned. */
 public static BlockPos origin(SettlementData.Entry e,Settlement.Building parent,Kind k){
  int x0=k.site().getX(),z0=k.site().getZ(),x1=x0+k.width()-1,z1=z0+k.depth()-1;int y=k.site().getY();
  int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
  for(int[] c:new int[][]{{x0,z0},{x1,z0},{x0,z1},{x1,z1}}){var p=BuildingPlacement.at(e,parent,c[0],y,c[1]);minX=Math.min(minX,p.getX());minZ=Math.min(minZ,p.getZ());}
  return new BlockPos(minX,BuildingPlacement.origin(e,parent).getY()+y,minZ);
 }
 /** The annex a building has of this kind, or null. */
 public static Settlement.Building existing(Settlement s,Settlement.Building parent,Kind k){return s.annexes(parent.id()).stream().filter(a->a.type().equals(k.type())).findFirst().orElse(null);}
 /** Why this annex cannot be ordered for the building now (before looking at its site), or empty. */
 public static String refusal(ServerLevel l,SettlementData.Entry e,Settlement.Building parent,Kind k){
  if(!parent.type().equals(k.parent()))return "parent";
  if(existing(e.settlement(),parent,k)!=null)return "exists";
  if(parent.level()<k.parentLevel())return "parent_level";
  if(k.research()!=null&&ResearchCatalog.NODES.containsKey(k.research())&&!ResearchKnobs.done(l,e).contains(k.research()))return "research";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  if(HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.yields(l,e.settlement().id()))return "busy";
  return "";
 }
 /** The survey of the annex at its site: a new building there, the parent's own buffer left out. "conflicts" = the site is not free. */
 public static BuildingOrders.Survey survey(ServerLevel l,SettlementData.Entry e,Settlement.Building parent,Kind k){
  var survey=BuildingOrders.survey(l,e,k.type(),parent.rotation(),origin(e,parent,k),null,-1,true,parent.id());
  if(survey.ok()){survey.state().putUUID("annexOf",parent.id());survey.state().putString("annex",k.type());}
  return survey;
 }
 /** The crew's queued project is this annex of this building. */
 public static boolean queued(ServerLevel l,SettlementData.Entry e,Settlement.Building parent,Kind k){
  if(!HallUpgradeGoal.pending(l,e.settlement().id()))return false;var t=HallUpgradeGoal.inspect(l,e.settlement().id());
  return t.hasUUID("annexOf")&&t.getUUID("annexOf").equals(parent.id())&&t.getString("annex").equals(k.type());}
 /** The office's line for each annex a building may take: built, queued, ready (its site is free and it may be ordered), or why not —
  *  "site" with the count of cells in the way when something stands on its site. */
 public static net.minecraft.nbt.ListTag view(ServerLevel l,SettlementData.Entry e,Settlement.Building parent){
  var out=new net.minecraft.nbt.ListTag();
  for(var k:of(parent.type())){var t=new net.minecraft.nbt.CompoundTag();t.putString("type",k.type());t.putInt("parentLevel",k.parentLevel());
   if(k.research()!=null&&ResearchCatalog.NODES.containsKey(k.research()))t.putString("research",k.research());
   String state;
   if(existing(e.settlement(),parent,k)!=null)state="built";
   else if(queued(l,e,parent,k))state="queued";
   else{state=refusal(l,e,parent,k);
    if(state.isEmpty()){var survey=survey(l,e,parent,k);state=survey.ok()?"ready":survey.reason().equals("conflicts")?"site":survey.reason();t.putInt("conflicts",survey.conflicts().size());}}
   t.putString("state",state);out.add(t);}
  return out;
 }
 /** The mayor's order from the office: the same office checks as a level order, then {@link #order}. */
 public static String order(net.minecraft.server.level.ServerPlayer p,UUID village,long epoch,UUID building,String type){
  var e=SettlementData.get(p.server).entry(village);if(e==null)return "village";
  if(!e.settlement().governance().canManage(p.getUUID(),epoch)||!org.villageastra.server.ManagementOrders.allowedContext(p,e))return "mayor";
  var b=e.settlement().buildings().stream().filter(x->x.id().equals(building)).findFirst().orElse(null);if(b==null)return "building";
  var k=kind(type);if(k==null)return "type";
  return order(p.serverLevel(),e,b,k);
 }
 /** Orders the annex: the same checks as any building order, then the crew's queue. Empty = queued, else the reason. */
 public static String order(ServerLevel l,SettlementData.Entry e,Settlement.Building parent,Kind k){
  var refusal=refusal(l,e,parent,k);if(!refusal.isEmpty())return refusal;
  var survey=survey(l,e,parent,k);if(!survey.ok())return survey.reason();
  HallUpgradeGoal.yield(l,e.settlement().id());HallUpgradeGoal.enqueue(l,e,survey.state());SettlementData.get(l.getServer()).setDirty();return "";
 }
}
