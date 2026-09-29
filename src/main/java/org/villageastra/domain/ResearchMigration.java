package org.villageastra.domain;
import java.util.*;
/** AD-136 (§3.4, CF1, CF4): the one-time move of a village's research record from schema 1 (v0.7/v1 tree: every node paid in books, 7×tier) to
 *  schema 2 (level I in resources, II–VI 7×(tier−1) works). Pure data, no Minecraft types; the server runs it once, after recovering a pending
 *  schema-1 payment by the schema-1 rules.
 *  <ul><li>A side branch's every old level (milling.1…6, masonry.*, carpentry.*) is its one side node: done when any of them was done.</li>
 *  <li>Removed nodes (civic.*, exploration.*, mechanics.*, engineering.6) leave the done list, the target and the queue; their books become credit.</li>
 *  <li>A level-I node's books become credit; it is done (resourceDone) only if it was paid in full under the old price.</li>
 *  <li>A level II–VI node keeps min(paid, new price); the rest becomes credit; it is done when that reaches the new price.</li>
 *  <li>Level-I nodes never stay a laboratory target or in its queue (CF3).</li></ul>
 *  Knowledge done before the new conditions (X.6 without engineering V) stays done: the rules only judge what may be studied next. */
public final class ResearchMigration {
 private ResearchMigration(){}
 public record Record(Set<String> legacyDone,String selected,List<String> queue,Map<String,Integer> paid,int credit,Set<String> resourceDone){}
 /** The v1 price of a node: 7 books per tier (base_books × tier). */
 public static int oldPrice(String id){int dot=id.lastIndexOf('.');try{return 7*Integer.parseInt(id.substring(dot+1));}catch(RuntimeException ex){return Integer.MAX_VALUE;}}
 public static Record migrate(Set<String> legacyDone,String selected,List<String> queue,Map<String,Integer> paid){
  var done=new LinkedHashSet<String>();var resourceDone=new LinkedHashSet<String>();var newPaid=new TreeMap<String,Integer>();long credit=0;
  // Done under schema 1: the migrated civilization list and every node paid to its old price.
  var oldDone=new LinkedHashSet<String>(legacyDone);for(var e:paid.entrySet())if(e.getValue()>=oldPrice(e.getKey()))oldDone.add(e.getKey());
  for(var id:oldDone){var now=ResearchCatalog.renamed(id);if(now==null)continue;var n=ResearchCatalog.get(now);
   if(legacyDone.contains(id))done.add(now);else if(n.resourcePaid())resourceDone.add(now);else newPaid.put(now,n.works());}
  var complete=new HashSet<String>(done);complete.addAll(resourceDone);complete.addAll(newPaid.keySet());
  for(var e:paid.entrySet()){String id=e.getKey();int books=Math.max(0,e.getValue());if(books==0)continue;var now=ResearchCatalog.renamed(id);
   // Books of a removed node, of an old side level and of a level-I node are credit.
   if(now==null||!now.equals(id)||ResearchCatalog.get(now).resourcePaid()){credit+=books;continue;}
   var n=ResearchCatalog.get(now);
   // Done already: by its own old payment the books over the new price are credit, by any other way all of them.
   if(complete.contains(now)){credit+=oldDone.contains(id)&&!legacyDone.contains(id)?Math.max(0,books-n.works()):books;continue;}
   int keep=Math.min(books,n.works());credit+=books-keep;newPaid.put(now,keep);}
  // A node done in the done list needs no payment record.
  for(var id:done)newPaid.remove(id);
  String target=keep(selected,done,resourceDone,newPaid);
  var q=new ArrayList<String>();for(var id:queue){var t=keep(id,done,resourceDone,newPaid);if(!t.isEmpty()&&!t.equals(target)&&!q.contains(t))q.add(t);}
  if(target.isEmpty()&&!q.isEmpty())target=q.remove(0);
  return new Record(Collections.unmodifiableSet(done),target,List.copyOf(q),Collections.unmodifiableMap(newPaid),(int)Math.min(Integer.MAX_VALUE,credit),Collections.unmodifiableSet(resourceDone));
 }
 /** A target or queue entry that still means something to the laboratory: an existing, undone node of level II–VI; otherwise "". */
 private static String keep(String id,Set<String> done,Set<String> resourceDone,Map<String,Integer> paid){
  if(id==null||id.isEmpty())return "";var n=ResearchCatalog.NODES.get(id);
  if(n==null||n.resourcePaid()||done.contains(id)||resourceDone.contains(id)||paid.getOrDefault(id,0)>=n.works())return "";
  return id;
 }
}
