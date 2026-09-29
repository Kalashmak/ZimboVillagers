package org.villageastra.domain;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
/** AD-136: the research tree v2 (the owner's ladders of 2026-09-23), packaged as data/villageastra/catalog/research_tree.json schema 2 and never
 *  kept as a second Java list. Branches and their tiers come from the data: a main branch runs over its tiers (engineering I–V, the town hall II–VI),
 *  a side branch (mill, masonry, carpentry) is one node at the level of its parent it hangs from. Level I is paid in resources, II–VI in
 *  scientific works ({@link ScienceBalance#works}). */
public final class ResearchCatalog {
 /** One line of a level-I price: an item or an item tag ("minecraft:logs") and a count. */
 public record Cost(String item,String tag,int count){
  /** "minecraft:wheat" or "#minecraft:logs". */
  public String key(){return tag!=null?"#"+tag:item;}
 }
 public record Branch(String id,String ru,String en,String group,int from,int to,String sideOf){
  public boolean side(){return sideOf!=null;}
  public int size(){return to-from+1;}
 }
 /** status: ACTIVE, INTERIM, PLANNED or PREREQUISITE_ONLY (AD-133/AD-136 honest cards); phase: T or the building phase B1..B15 that makes it
  *  ACTIVE; owner: the owner's words; futureRu/futureEn: the «Будет: …» line of an INTERIM or PLANNED card (empty for ACTIVE). */
 public record Node(String id,String branch,int tier,int works,List<Cost> resources,String ru,String en,List<String> requires,String status,String phase,String owner,String futureRu,String futureEn){
  /** Level I is paid in resources and never in works (CF1). */
  public boolean resourcePaid(){return tier==1;}
 }
 public static final Set<String> STATUSES=Set.of("ACTIVE","INTERIM","PLANNED","PREREQUISITE_ONLY");
 public static final Map<String,Branch> BRANCHES;
 public static final Map<String,Node> NODES;
 private static final Map<String,List<String>> LEGACY;
 static{
  var root=readRoot();var branches=new LinkedHashMap<String,Branch>();
  NODES=parse(root,branches);BRANCHES=Collections.unmodifiableMap(branches);LEGACY=legacy(root);
 }
 private static JsonObject readRoot(){try(var stream=ResearchCatalog.class.getResourceAsStream("/data/villageastra/catalog/research_tree.json")){if(stream==null)throw new IllegalStateException("Missing research catalog");return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException ex){throw new IllegalStateException(ex);}}
 /** AD-123 (C2), AD-136: the titles a node had before (v0.7 and v1), kept searchable. */
 private static Map<String,List<String>> legacy(JsonObject root){var out=new HashMap<String,List<String>>();
  for(var raw:root.getAsJsonArray("nodes")){var j=raw.getAsJsonObject();var t=j.getAsJsonObject("legacy_titles");if(t==null)continue;
   var list=new ArrayList<String>();for(var e:t.entrySet())list.add(e.getValue().getAsString());out.put(j.get("id").getAsString(),List.copyOf(list));}
  return Map.copyOf(out);}
 public static List<String> legacyTitles(String id){return LEGACY.getOrDefault(id,List.of());}
 private ResearchCatalog(){}
 public static Map<String,Node> parse(Reader reader){return parse(JsonParser.parseReader(reader).getAsJsonObject(),new LinkedHashMap<>());}
 private static String str(JsonObject o,String key){var v=o.get(key);return v==null||v.isJsonNull()?"":v.getAsString();}
 private static Map<String,Node> parse(JsonObject root,Map<String,Branch> branches){
  if(!root.has("schema_version")||root.get("schema_version").getAsInt()!=2)throw new IllegalArgumentException("Research catalog schema 2 expected");
  for(var raw:root.getAsJsonArray("branches")){var b=raw.getAsJsonObject();var t=b.getAsJsonArray("tiers");int from=t.get(0).getAsInt(),to=t.get(1).getAsInt();
   var branch=new Branch(b.get("id").getAsString(),b.get("title_ru").getAsString(),b.get("title_en").getAsString(),b.get("group").getAsString(),from,to,b.has("side_of")?b.get("side_of").getAsString():null);
   // CF12: a side branch is exactly one node, at the level of its parent it hangs from.
   if(from<1||to>6||from>to||branch.side()&&from!=to||branches.putIfAbsent(branch.id(),branch)!=null)throw new IllegalArgumentException("Invalid research branch "+branch.id());}
  for(var b:branches.values())if(b.side()&&(!branches.containsKey(b.sideOf())||branches.get(b.sideOf()).side()))throw new IllegalArgumentException("Invalid side branch "+b.id());
  var nodes=new LinkedHashMap<String,Node>();
  for(var raw:root.getAsJsonArray("nodes")){var j=raw.getAsJsonObject();String id=j.get("id").getAsString(),branch=j.get("branch").getAsString();int tier=j.get("tier").getAsInt();
   var b=branches.get(branch);if(b==null)throw new IllegalArgumentException("Unknown branch of "+id);
   var prerequisites=new ArrayList<String>();for(var p:j.getAsJsonArray("requires_all"))prerequisites.add(p.getAsString());
   var resources=new ArrayList<Cost>();
   if(j.has("cost")&&j.getAsJsonObject("cost").has("resources"))for(var c:j.getAsJsonObject("cost").getAsJsonArray("resources")){var o=c.getAsJsonObject();int n=o.get("count").getAsInt();
    if(n<1||n>256||o.has("item")==o.has("tag"))throw new IllegalArgumentException("Invalid resource cost of "+id);resources.add(new Cost(o.has("item")?o.get("item").getAsString():null,o.has("tag")?o.get("tag").getAsString():null,n));}
   int works=ScienceBalance.works(tier);var title=j.getAsJsonObject("title");var future=j.getAsJsonObject("future");String status=str(j,"status");
   // CF1: works==0 exactly at level I, and only level I has (and must have) resources.
   if(tier<b.from()||tier>b.to()||!id.equals(branch+"."+tier)||(works==0)!=(tier==1)||resources.isEmpty()!=(tier!=1)||!STATUSES.contains(status)
    ||!status.equals("ACTIVE")&&!status.equals("PREREQUISITE_ONLY")&&future==null
    ||nodes.putIfAbsent(id,new Node(id,branch,tier,works,List.copyOf(resources),title.get("ru_ru").getAsString(),title.get("en_us").getAsString(),List.copyOf(prerequisites),status,str(j,"phase"),str(j,"owner_en"),future==null?"":str(future,"ru_ru"),future==null?"":str(future,"en_us")))!=null)
    throw new IllegalArgumentException("Invalid research node "+id);}
  int expected=0;for(var b:branches.values())expected+=b.size();
  if(nodes.size()!=expected||nodes.values().stream().map(Node::branch).distinct().count()!=branches.size())throw new IllegalArgumentException("Incomplete research catalog");
  for(var n:nodes.values()){for(var p:n.requires())if(!nodes.containsKey(p))throw new IllegalArgumentException("Invalid dependency "+n.id()+" / "+p);
   var b=branches.get(n.branch());
   // X.n needs X.(n-1) on a main branch; a side node needs its parent's node of its level.
   if(!b.side()&&n.tier()>b.from()&&!n.requires().contains(n.branch()+"."+(n.tier()-1)))throw new IllegalArgumentException("Missing previous level of "+n.id());
   if(b.side()&&!n.requires().contains(b.sideOf()+"."+n.tier()))throw new IllegalArgumentException("Side node without its parent "+n.id());
   // D8: engineering V is a condition of every level VI.
   if(n.tier()==6&&nodes.containsKey(GATE_VI)&&!n.requires().contains(GATE_VI))throw new IllegalArgumentException("Level VI without "+GATE_VI+": "+n.id());}
  var done=new HashSet<String>();while(done.size()<nodes.size()){int before=done.size();for(var n:nodes.values())if(done.containsAll(n.requires()))done.add(n.id());if(done.size()==before)throw new IllegalArgumentException("Cyclic research dependencies");}
  return Collections.unmodifiableMap(nodes);
 }
 /** D8: the research every level VI needs. */
 public static final String GATE_VI="engineering.5";
 public static Node get(String id){var n=NODES.get(id);if(n==null)throw new IllegalArgumentException("Unknown research "+id);return n;}
 public static Branch branch(String id){var b=BRANCHES.get(id);if(b==null)throw new IllegalArgumentException("Unknown research branch "+id);return b;}
 public static String legacyId(String id){var map=Map.of("cartography","cartography.1","ironworking","metallurgy.1","housing_2","housing.1","milling","milling.2","medicine","medicine.1","caravans","caravans.1","engineering","engineering.1","fortification","defense.1","deep_mining","mining.1","siege","military.1");var result=map.get(id);if(result==null)throw new IllegalArgumentException("Unknown legacy research");return result;}
 public static Set<String> migrated(Civilization old){var result=new LinkedHashSet<String>();for(var id:old.completed())result.add(legacyId(id));return result;}
 /** AD-136 (§3.4): the node a v1 id became — a side branch's every old level is its one side node now; null when the id was removed
  *  (civic, exploration, mechanics, engineering VI) or never existed. */
 public static String renamed(String id){
  int dot=id.lastIndexOf('.');if(dot<0)return null;String branch=id.substring(0,dot);
  var b=BRANCHES.get(branch);if(b!=null&&b.side())return branch+"."+b.from();
  return NODES.containsKey(id)?id:null;
 }
}
