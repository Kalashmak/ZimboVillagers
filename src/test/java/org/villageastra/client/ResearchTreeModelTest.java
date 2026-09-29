package org.villageastra.client;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.domain.ResearchCatalog.Node;
import org.villageastra.domain.ResearchRules;
import org.villageastra.client.ResearchTreeModel.*;
import static org.junit.jupiter.api.Assertions.*;
/** AD-124: the research tree as data — groups, the shared availability rule, search, moving through the grid, the next node to study. */
class ResearchTreeModelTest {
 private static final Map<String,String> RU=lang("ru_ru");
 private static Map<String,String> lang(String code){try(var in=ResearchTreeModelTest.class.getResourceAsStream("/assets/villageastra/lang/"+code+".json")){var out=new HashMap<String,String>();
   for(var e:JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().entrySet())out.put(e.getKey(),e.getValue().getAsString());return out;}catch(Exception ex){throw new IllegalStateException(ex);}}
 private static final String[] ROMAN={"I","II","III","IV","V","VI"};
 /** The title as the office shows it in Russian: '<branch> <step> — <name>'. */
 private static String title(Node n){return RU.get("research.villageastra.branch."+n.branch())+" "+ROMAN[n.tier()-1]+" — "+n.ru();}
 /** The rule as BookResearch had it before AD-124, to hold the shared one to it. */
 private static String before(Set<String> done,int hall,Node n,Collection<String> next){if(done.contains(n.id()))return "completed";if(hall<n.tier()&&!(n.tier()==hall+1&&next.contains(n.id())))return "hall_level";if(!done.containsAll(n.requires()))return "dependencies";return "available";}
 /** AD-136: the catalogue's five groups, every branch once, each with exactly its own levels; a side branch right under its parent. */
 @Test void groupsCoverEveryBranchOnceAndEveryBranchHasItsOwnLevels(){
  var seen=new HashSet<String>();for(var g:ResearchTreeModel.GROUPS.values())for(var b:g)assertTrue(seen.add(b),"branch twice: "+b);
  var branches=new HashSet<String>();for(var n:ResearchCatalog.NODES.values())branches.add(n.branch());
  assertEquals(ResearchCatalog.BRANCHES.size(),seen.size());assertEquals(branches,seen);assertEquals(22,ResearchTreeModel.BRANCHES.size());
  assertEquals(List.of("food","resources","city","knowledge","power"),ResearchTreeModel.GROUP_IDS);
  for(var b:branches)for(int t=1;t<=6;t++){var n=ResearchTreeModel.at(b,t);boolean has=t>=ResearchTreeModel.from(b)&&t<=ResearchTreeModel.to(b);
   assertEquals(has,n!=null,b+" step "+t);if(n!=null)assertEquals(t,n.tier());}
  assertEquals(5,ResearchTreeModel.size("engineering"));assertEquals(5,ResearchTreeModel.size("town_hall"));assertEquals(2,ResearchTreeModel.from("town_hall"));
  for(var b:List.of("milling","masonry","carpentry")){assertTrue(ResearchTreeModel.side(b));assertEquals(1,ResearchTreeModel.size(b));
   int at=ResearchTreeModel.BRANCHES.indexOf(b);assertEquals(ResearchTreeModel.parent(b),ResearchTreeModel.BRANCHES.get(at-1),b+" sits right under its parent");}
  assertEquals("baking",ResearchTreeModel.parent("milling"));assertEquals("mining",ResearchTreeModel.parent("masonry"));assertEquals("forestry",ResearchTreeModel.parent("carpentry"));
  for(var g:ResearchTreeModel.GROUP_IDS)assertTrue(RU.containsKey("research.villageastra.group."+g),"group name "+g);
  for(var b:branches)assertTrue(RU.containsKey("research.villageastra.branch."+b),"branch name "+b);
 }
 /** AD-136 (spec 3.3): the empty places of the grid are engineering VI (no level) and the starting town hall I; side rows have none. */
 @Test void theOnlyEmptyPlacesAreEngineeringSixAndTheHallOne(){
  var empty=new ArrayList<String>();for(var b:ResearchTreeModel.BRANCHES)for(int t=1;t<=6;t++)if(ResearchTreeModel.noLevel(b,t))empty.add(b+"."+t);
  assertEquals(List.of("town_hall.1","engineering.6"),empty);
  assertFalse(ResearchTreeModel.noLevel("milling",1),"a side row is one node, not a row of empty places");
 }
 @Test void theSharedRuleIsTheServersRule(){
  var any=ResearchCatalog.NODES.values().stream().filter(n->n.tier()==1&&n.requires().isEmpty()).findFirst().orElseThrow();
  assertEquals("available",ResearchRules.reason(Set.of(),1,any,List.of()));
  var tier2=ResearchCatalog.NODES.values().stream().filter(n->n.tier()==2).findFirst().orElseThrow();
  assertEquals("hall_level",ResearchRules.reason(new HashSet<>(tier2.requires()),1,tier2,List.of()));
  // AD-073: a node the next hall level needs opens one hall step early, once its own requirements are studied.
  assertEquals("available",ResearchRules.reason(new HashSet<>(tier2.requires()),1,tier2,List.of(tier2.id())));
  assertEquals(tier2.requires().isEmpty()?"available":"dependencies",ResearchRules.reason(Set.of(),1,tier2,List.of(tier2.id())));
  assertEquals("completed",ResearchRules.reason(Set.of("research.1"),1,ResearchCatalog.get("research.1"),List.of()));
  var random=new Random(124);var ids=new ArrayList<>(ResearchCatalog.NODES.keySet());
  for(int round=0;round<40;round++){var done=new HashSet<String>();for(var id:ids)if(random.nextInt(3)==0)done.add(id);int hall=1+random.nextInt(6);var next=new ArrayList<String>();for(int i=0;i<4;i++)next.add(ids.get(random.nextInt(ids.size())));
   for(var n:ResearchCatalog.NODES.values())assertEquals(before(done,hall,n,next),ResearchRules.reason(done,hall,n,next),n.id()+" hall "+hall);}
 }
 @Test void stateCombinesTheRuleWithTargetQueueAndPayment(){
  var f=new Facts(Set.of("research.1","agriculture.1","livestock.1"),2,List.of(),"agriculture.2",List.of("forestry.2"),Map.of("livestock.2",3),List.of("mining.1"),Map.of("mining.1",List.of(new int[]{10,64},new int[]{1,1})),4);
  assertEquals(State.DONE,ResearchTreeModel.state(f,ResearchCatalog.get("research.1")));
  assertEquals(State.TARGET,ResearchTreeModel.state(f,ResearchCatalog.get("agriculture.2")));
  assertEquals(State.QUEUED,ResearchTreeModel.state(f,ResearchCatalog.get("forestry.2")));
  assertEquals(State.PARTIAL,ResearchTreeModel.state(f,ResearchCatalog.get("livestock.2")));
  assertEquals(State.HALL,ResearchTreeModel.state(f,ResearchCatalog.get("research.6")));
  // AD-136: a level-I node ordered paid in resources waits for the porters; its price in the stock counts each line up to its need.
  assertEquals(State.ORDERED,ResearchTreeModel.state(f,ResearchCatalog.get("mining.1")));assertTrue(State.ORDERED.actionable());
  assertArrayEquals(new int[]{11,65},f.resources("mining.1"));assertFalse(f.ready("mining.1"));assertFalse(f.ready("forestry.1"),"not known is not ready");
  assertEquals(State.AVAILABLE,ResearchTreeModel.state(f,ResearchCatalog.get("forestry.1")));
 }
 @Test void searchFindsByTitleIdAndOldNameAndCountsInRowOrder(){
  var roads=ResearchTreeModel.search("дорог",ResearchTreeModelTest::title);var ids=roads.stream().map(Node::id).toList();
  for(int t=1;t<=6;t++)assertTrue(ids.contains("roads."+t),"roads."+t+" in "+ids);assertTrue(ids.contains("defense.3"),"Protected roads: "+ids);assertEquals(7,roads.size(),"matches: "+ids);
  assertEquals("roads.1",ids.get(0));assertEquals("roads.2",ids.get(1));assertEquals("defense.3",ids.get(6),"row order: the city before power");
  assertEquals(7,ResearchTreeModel.search("roads",ResearchTreeModelTest::title).size());
  assertEquals(List.of(ResearchCatalog.get("mining.3")),ResearchTreeModel.search("mining.3",ResearchTreeModelTest::title),"an exact id is that node alone");
  var old=ResearchCatalog.NODES.values().stream().filter(n->!ResearchCatalog.legacyTitles(n.id()).isEmpty()).findFirst().orElseThrow();
  assertTrue(ResearchTreeModel.search(ResearchCatalog.legacyTitles(old.id()).get(0),n->"").contains(old),"a title of before AD-123 still finds "+old.id());
  assertTrue(ResearchTreeModel.search("   ",ResearchTreeModelTest::title).isEmpty());
 }
 @Test void rowsFollowGroupFilterAndSearch(){
  var f=Facts.empty();
  assertEquals(ResearchTreeModel.GROUPS.get("city"),ResearchTreeModel.rows("city",Filter.ALL,null,f));
  assertEquals(List.of("town_hall","construction","housing","roads","logistics","engineering"),ResearchTreeModel.GROUPS.get("city"));
  assertEquals(22,ResearchTreeModel.rows(null,Filter.ALL,null,f).size());
  assertEquals(List.of("roads","defense"),ResearchTreeModel.rows(null,Filter.ALL,Set.of("roads.2","defense.3"),f));
  assertTrue(ResearchTreeModel.rows(null,Filter.DONE,null,f).isEmpty(),"nothing studied yet");
  var some=new Facts(Set.of("research.1","agriculture.1"),1,List.of(),"",List.of(),Map.of());
  assertEquals(List.of("agriculture","research"),ResearchTreeModel.rows(null,Filter.DONE,null,some));
 }
 @Test void movingAndTheNextNodeToStudy(){
  // AD-136: the city rows are town hall (II-VI), construction, housing, roads, logistics, engineering (I-V).
  var rows=ResearchTreeModel.GROUPS.get("city");
  assertEquals("housing.2",ResearchTreeModel.move("housing.1",1,0,rows));assertEquals("roads.1",ResearchTreeModel.move("housing.1",0,1,rows));
  assertEquals("construction.1",ResearchTreeModel.move("housing.1",-1,-1,rows),"up a row and left a step");
  assertEquals("town_hall.2",ResearchTreeModel.move("town_hall.2",-1,-1,rows),"clamped at the corner: the hall has no level I");
  assertEquals("engineering.5",ResearchTreeModel.move("roads.4",9,99,rows),"the last row's last level: engineering stops at V");
  assertEquals("engineering.5",ResearchTreeModel.move("engineering.5",1,0,rows),"no step past engineering V");
  assertEquals("town_hall.3",ResearchTreeModel.move("mining.3",0,0,rows),"a node outside the rows goes to the first row, same step");
  var food=ResearchTreeModel.GROUPS.get("food");assertEquals(List.of("agriculture","livestock","baking","milling"),food);
  assertEquals("milling.2",ResearchTreeModel.move("baking.4",0,1,food),"down into a side row lands on its one node");
  assertEquals("milling.2",ResearchTreeModel.move("milling.2",1,0,food),"along a side row there is nowhere to go");
  assertEquals("baking.2",ResearchTreeModel.move("milling.2",0,-1,food),"up from a side node is its parent's node of that level");
  var f=Facts.empty();var all=ResearchTreeModel.BRANCHES;String first=ResearchTreeModel.nextAvailable(null,all,f,1);assertNotNull(first);
  assertEquals(State.AVAILABLE,ResearchTreeModel.state(f,ResearchCatalog.get(first)));
  // Round the end, and only through the rows shown.
  String again=first;var seen=new HashSet<String>();do{seen.add(again);again=ResearchTreeModel.nextAvailable(again,all,f,1);}while(!again.equals(first)&&seen.size()<200);
  assertEquals(first,again,"cycles back");for(var id:seen)assertEquals(State.AVAILABLE,ResearchTreeModel.state(f,ResearchCatalog.get(id)));
  assertEquals(ResearchTreeModel.nextAvailable(first,all,f,1)==null?null:first,ResearchTreeModel.nextAvailable(ResearchTreeModel.nextAvailable(first,all,f,1),all,f,-1),"back is the reverse");
  var knowledge=ResearchTreeModel.GROUPS.get("knowledge");assertEquals(List.of("research","education","cartography","medicine"),knowledge);var inGroup=ResearchTreeModel.nextAvailable(null,knowledge,f,1);if(inGroup!=null)assertTrue(knowledge.contains(ResearchCatalog.get(inGroup).branch()));
 }
 /** N skips what the state filter or the search dims: those nodes stay in their cells, but they are not what the player is looking at. */
 @Test void theNextNodeToStudySkipsNodesTheFilterDims(){
  var f=Facts.empty();var all=ResearchTreeModel.BRANCHES;
  var open=ResearchCatalog.NODES.values().stream().filter(n->ResearchTreeModel.state(f,n)==State.AVAILABLE).map(Node::id).toList();
  assertTrue(open.size()>=2,"at least two nodes open at the start: "+open);
  String second=open.get(open.size()-1);
  assertEquals(second,ResearchTreeModel.nextAvailable(null,all,f,1,Filter.ALL,Set.of(second)),"only the match is lit");
  assertEquals(second,ResearchTreeModel.nextAvailable(second,all,f,1,Filter.ALL,Set.of(second)),"round the end back to the only lit one");
  assertNull(ResearchTreeModel.nextAvailable(null,all,f,1,Filter.DONE,null),"the 'studied' filter dims every node that can be studied");
  assertNull(ResearchTreeModel.nextAvailable(null,all,f,1,Filter.ALL,Set.of()),"a search with no match lights nothing");
  assertEquals(ResearchTreeModel.nextAvailable(null,all,f,1),ResearchTreeModel.nextAvailable(null,all,f,1,Filter.ACTIONABLE,null),"'can be acted on' keeps them all");
 }
 @Test void unlocksIsTheReverseOfRequires(){
  int links=0;for(var n:ResearchCatalog.NODES.values())for(var r:n.requires()){assertTrue(ResearchTreeModel.unlocks(r).contains(n.id()),r+" opens "+n.id());links++;}
  int back=0;for(var n:ResearchCatalog.NODES.values())for(var u:ResearchTreeModel.unlocks(n.id())){assertTrue(ResearchCatalog.get(u).requires().contains(n.id()));back++;}
  assertEquals(links,back);
 }
}
