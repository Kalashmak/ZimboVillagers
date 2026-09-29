package org.villageastra.client;
import java.util.*;
import java.util.function.Function;
import org.villageastra.domain.ResearchCatalog;
import org.villageastra.domain.ResearchCatalog.Node;
import org.villageastra.domain.ResearchRules;
/** AD-124, AD-136: the research tree as data, without Minecraft types: the catalogue's five groups of branches (side branches under their parent), the state of every node,
 *  filters by group, state and search, moving through the grid, the next node that can be studied, and who needs whom.
 *  The owner may move a branch between groups here only (GROUPS). */
final class ResearchTreeModel {
 enum State{DONE,TARGET,QUEUED,ORDERED,AVAILABLE,PARTIAL,HALL,LOCKED;
  boolean actionable(){return this==AVAILABLE||this==PARTIAL||this==TARGET||this==QUEUED||this==ORDERED;}
  boolean blocked(){return this==HALL||this==LOCKED;}
 }
 enum Filter{ALL,ACTIONABLE,DONE,BLOCKED;
  boolean accepts(State s){return switch(this){case ALL->true;case ACTIONABLE->s.actionable();case DONE->s==State.DONE;case BLOCKED->s.blocked();};}
  Filter next(){return values()[(ordinal()+1)%values().length];}
 }
 /** AD-136 (§3.3): the groups and their branches come from the catalogue (branch.group), in its order: food, resources, city, knowledge,
  *  power. A side branch (mill, masonry, carpentry) is a sub-row right under its parent. The owner moves a branch between groups in the data. */
 static final LinkedHashMap<String,List<String>> GROUPS=new LinkedHashMap<>();
 static{
  var groups=new LinkedHashMap<String,List<String>>();
  for(var b:ResearchCatalog.BRANCHES.values())if(!b.side())groups.computeIfAbsent(b.group(),g->new ArrayList<>()).add(b.id());
  for(var b:ResearchCatalog.BRANCHES.values())if(b.side()){var parent=ResearchCatalog.branch(b.sideOf());var list=groups.computeIfAbsent(parent.group(),g->new ArrayList<>());
   int at=list.indexOf(parent.id())+1;while(at<list.size()&&ResearchCatalog.branch(list.get(at)).side())at++;list.add(at,b.id());}
  groups.forEach((g,list)->GROUPS.put(g,List.copyOf(list)));
 }
 static final List<String> GROUP_IDS=List.copyOf(GROUPS.keySet());
 /** Every branch in row order (group by group). */
 static final List<String> BRANCHES;
 private static final Map<String,Node[]> GRID=new HashMap<>();
 private static final Map<String,List<String>> UNLOCKS=new HashMap<>();
 static{
  var all=new ArrayList<String>();for(var g:GROUPS.values())all.addAll(g);
  BRANCHES=List.copyOf(all);
  for(var n:ResearchCatalog.NODES.values()){GRID.computeIfAbsent(n.branch(),b->new Node[6])[n.tier()-1]=n;for(var r:n.requires())UNLOCKS.computeIfAbsent(r,k->new ArrayList<>()).add(n.id());}
 }
 /** A side branch: one node under its parent's row, in the column of the level it hangs from. */
 static boolean side(String branch){var b=ResearchCatalog.BRANCHES.get(branch);return b!=null&&b.side();}
 /** The parent of a side branch, or null. */
 static String parent(String branch){var b=ResearchCatalog.BRANCHES.get(branch);return b==null?null:b.sideOf();}
 /** The branch's levels, first and last (engineering I–V, the town hall II–VI, a side branch its one level). */
 static int from(String branch){var b=ResearchCatalog.BRANCHES.get(branch);return b==null?1:b.from();}
 static int to(String branch){var b=ResearchCatalog.BRANCHES.get(branch);return b==null?6:b.to();}
 /** How many levels the branch has. */
 static int size(String branch){return to(branch)-from(branch)+1;}
 /** A cell of a main branch that has no level there (engineering VI, the town hall I): drawn as an empty place with its reason. */
 static boolean noLevel(String branch,int tier){return !side(branch)&&(tier<from(branch)||tier>to(branch));}
 static String groupOf(String branch){for(var e:GROUPS.entrySet())if(e.getValue().contains(branch))return e.getKey();return GROUP_IDS.get(GROUP_IDS.size()-1);}
 static Node at(String branch,int tier){var row=GRID.get(branch);return row==null||tier<1||tier>6?null:row[tier-1];}
 static List<String> requires(String id){var n=ResearchCatalog.NODES.get(id);return n==null?List.of():n.requires();}
 /** The nodes that need this one, built once from the catalogue. */
 static List<String> unlocks(String id){return UNLOCKS.getOrDefault(id,List.of());}

 /** What the office snapshot says about research: studied, the hall's level, what the next hall level needs, the target, the queue, volumes paid. */
 /** AD-136: orders are the level-I nodes the mayor ordered paid in resources; stock is, per open level-I node, what the hall and warehouse
  *  hold of each line of its price (have, need); credit is works paid before the tree was reworked, spent before the laboratory chest. */
 record Facts(Set<String> done,int hall,Collection<String> hallNext,String target,List<String> queue,Map<String,Integer> paid,List<String> orders,Map<String,List<int[]>> stock,int credit){
  Facts(Set<String> done,int hall,Collection<String> hallNext,String target,List<String> queue,Map<String,Integer> paid){this(done,hall,hallNext,target,queue,paid,List.of(),Map.of(),0);}
  static Facts empty(){return new Facts(Set.of(),1,List.of(),"",List.of(),Map.of());}
  int paid(String id){return paid.getOrDefault(id,0);}
  /** A level-I price: items the stock holds (each line counted up to its need) and items it needs; {0,0} when not known. */
  int[] resources(String id){int have=0,need=0;for(var line:stock.getOrDefault(id,List.of())){have+=Math.min(line[0],line[1]);need+=line[1];}return new int[]{have,need};}
  /** Whether the stock holds the whole level-I price (false when not known). */
  boolean ready(String id){var r=resources(id);return r[1]>0&&r[0]>=r[1];}
 }
 static String reason(Facts f,Node n){return ResearchRules.reason(f.done(),f.hall(),n,f.hallNext());}
 static State state(Facts f,Node n){
  if(f.done().contains(n.id()))return State.DONE;if(n.id().equals(f.target()))return State.TARGET;if(f.queue().contains(n.id()))return State.QUEUED;
  if(f.orders().contains(n.id())&&reason(f,n).equals("available"))return State.ORDERED;
  return switch(reason(f,n)){case "available"->f.paid(n.id())>0?State.PARTIAL:State.AVAILABLE;case "hall_level"->State.HALL;case "completed"->State.DONE;default->State.LOCKED;};
 }
 /** Nodes whose id, title (as the player reads it), Russian or English name, or a title of before AD-123 holds the text, in row order.
  *  An exact id is that node alone. */
 static List<Node> search(String query,Function<Node,String> title){
  var q=query==null?"":query.trim().toLowerCase(Locale.ROOT);if(q.isEmpty())return List.of();
  var exact=ResearchCatalog.NODES.get(q);if(exact!=null)return List.of(exact);
  var out=new ArrayList<Node>();
  for(var b:BRANCHES)for(int t=1;t<=6;t++){var n=at(b,t);if(n==null)continue;
   var hay=(n.id()+" "+title.apply(n)+" "+n.ru()+" "+n.en()+" "+String.join(" ",ResearchCatalog.legacyTitles(n.id()))).toLowerCase(Locale.ROOT);if(hay.contains(q))out.add(n);}
  return out;
 }
 /** The rows shown: the branches of the group (null = all) with at least one node under the state filter and, when searching, one match. */
 static List<String> rows(String group,Filter filter,Collection<String> matches,Facts f){
  var out=new ArrayList<String>();
  for(var b:BRANCHES){if(group!=null&&!groupOf(b).equals(group))continue;boolean any=false;
   for(int t=1;t<=6&&!any;t++){var n=at(b,t);if(n!=null&&filter.accepts(state(f,n))&&(matches==null||matches.contains(n.id())))any=true;}
   if(any)out.add(b);}
  return out;
 }
 /** A node is drawn full when it passes the filter and the search; otherwise dimmed in its place, so the grid never jumps. */
 static boolean lit(Facts f,Node n,Filter filter,Collection<String> matches){return filter.accepts(state(f,n))&&(matches==null||matches.contains(n.id()));}
 /** One step through the grid of the rows shown: dx along the tiers, dy across the rows (clamped at the edges). */
 static String move(String id,int dx,int dy,List<String> rows){
  if(rows.isEmpty())return id;var n=ResearchCatalog.NODES.get(id);int row=n==null?-1:rows.indexOf(n.branch()),tier=n==null?1:n.tier();
  if(row<0)return nearest(rows.get(0),tier).id();
  row=Math.max(0,Math.min(rows.size()-1,row+dy));var b=rows.get(row);
  // Along a row the step stays within the branch's own levels; across rows it lands on the nearest level the other branch has.
  if(dy==0)tier=Math.max(from(b),Math.min(to(b),tier+dx));else tier=Math.max(1,Math.min(6,tier+dx));return nearest(b,tier).id();
 }
 /** The branch's node at this step, else the closest one it has (a side row has only one). */
 static Node nearest(String branch,int tier){int t=Math.max(from(branch),Math.min(to(branch),tier));var n=at(branch,t);if(n!=null)return n;
  for(int d=1;d<6;d++){var a=at(branch,t-d);if(a!=null)return a;a=at(branch,t+d);if(a!=null)return a;}return null;}
 /** The next (dir 1) or previous (dir -1) node that can be studied now, in row order, round the end; null when none is shown. */
 static String nextAvailable(String from,List<String> rows,Facts f,int dir){return nextAvailable(from,rows,f,dir,Filter.ALL,null);}
 /** The same, skipping the nodes the state filter or the search dims in the rows shown (they are not what the player looks for). */
 static String nextAvailable(String from,List<String> rows,Facts f,int dir,Filter filter,Collection<String> matches){
  var order=new ArrayList<Node>();for(var b:rows)for(int t=1;t<=6;t++){var n=at(b,t);if(n!=null)order.add(n);}
  if(order.isEmpty())return null;int start=-1;for(int i=0;i<order.size();i++)if(order.get(i).id().equals(from))start=i;
  int size=order.size();
  for(int k=1;k<=size;k++){int i=Math.floorMod((start<0?(dir>0?-1:0):start)+dir*k,size);var s=state(f,order.get(i));if((s==State.AVAILABLE||s==State.PARTIAL)&&lit(f,order.get(i),filter,matches))return order.get(i).id();}
  return null;
 }
 /** The first requirement not yet studied (for 'first: X'), or null. */
 static String firstMissing(Facts f,Node n){for(var r:n.requires())if(!f.done().contains(r))return r;return null;}
}
