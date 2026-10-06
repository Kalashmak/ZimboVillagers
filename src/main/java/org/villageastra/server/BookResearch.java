package org.villageastra.server;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.world.*;
/** Settlement-owned paid knowledge. Switching targets retains invested works at each node.
 *  AD-136 (schema 2): level I of a branch is paid once in resources from the hall and warehouse stock (resourceDone, one journal batch); levels
 *  II–VI take scientific works from the laboratory chest, one at a time, credit first (works paid before the tree was reworked). A level-I node is
 *  never a laboratory target (CF3): the mayor orders it paid (resourceOrders), the porters bring what is missing and it is paid by itself when all
 *  of it lies in the stock. A schema-1 record is migrated once, after its pending payment is recovered by the schema-1 rules (CF4). */
public final class BookResearch {
 private BookResearch(){}
 public static final int SCHEMA=2;
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-research/"+village+".bin");}
 /** CF10: a validated record per file, kept while the file's time and size stay the same; save() refreshes it. Callers get a copy. */
 private record Cached(long time,long size,CompoundTag tag){}
 private static final Map<Path,Cached> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
 public static void clearCache(){CACHE.clear();}
 private static Cached stamp(Path p,CompoundTag t){try{return new Cached(Files.getLastModifiedTime(p).toMillis(),Files.size(p),t.copy());}catch(java.io.IOException ex){return null;}}
 public static CompoundTag inspect(ServerLevel l,SettlementData.Entry e){var path=path(l,e.settlement().id());
  if(!Files.exists(path)){var t=new CompoundTag();t.putInt("schema",SCHEMA);t.putUUID("village",e.settlement().id());t.putString("selected","");t.put("paid",new CompoundTag());t.put("queue",new ListTag());var legacy=new ListTag();ResearchCatalog.migrated(e.settlement().civilization()).forEach(id->legacy.add(StringTag.valueOf(id)));t.put("legacyDone",legacy);t.putString("legacyActive",e.settlement().civilization().active());t.putInt("credit",0);t.put("resourceDone",new ListTag());t.put("resourceOrders",new ListTag());write(path,t);return t;}
  var cached=CACHE.get(path);
  if(cached!=null){try{if(Files.getLastModifiedTime(path).toMillis()==cached.time()&&Files.size(path)==cached.size())return cached.tag().copy();}catch(java.io.IOException ignored){}}
  var t=NbtRecord.read(path);
  if(t.getInt("schema")==1){t=migrate(l,e,t);write(path,t);return t;}
  validate(e,t);var stamp=stamp(path,t);if(stamp!=null)CACHE.put(path,stamp);return t;}
 private static void write(Path path,CompoundTag t){NbtRecord.write(path,t);var stamp=stamp(path,t);if(stamp!=null)CACHE.put(path,stamp);else CACHE.remove(path);}
 private static List<String> strings(CompoundTag t,String key){var out=new ArrayList<String>();for(var raw:t.getList(key,Tag.TAG_STRING))out.add(raw.getAsString());return out;}
 private static ListTag list(Collection<String> ids){var l=new ListTag();for(var id:ids)l.add(StringTag.valueOf(id));return l;}
 /** Schema 2, strict: an unknown id, a level-I node in paid, the target or the queue, a payment over the price — the file is refused. */
 private static void validate(SettlementData.Entry e,CompoundTag t){
  if(t.getInt("schema")!=SCHEMA||!t.hasUUID("village")||!t.getUUID("village").equals(e.settlement().id())||!t.contains("selected",Tag.TAG_STRING)||!t.contains("paid",Tag.TAG_COMPOUND))throw new IllegalStateException("Invalid book research save");
  if(!t.contains("legacyDone",Tag.TAG_LIST)||!t.contains("legacyActive",Tag.TAG_STRING))throw new IllegalStateException("Missing research migration state");
  if(!t.contains("credit",Tag.TAG_INT)||t.getInt("credit")<0)throw new IllegalStateException("Invalid research credit");
  for(var id:strings(t,"legacyDone"))ResearchCatalog.get(id);if(!t.getString("legacyActive").isEmpty())ResearchCatalog.legacyId(t.getString("legacyActive"));
  if(!t.getString("selected").isEmpty()&&ResearchCatalog.get(t.getString("selected")).resourcePaid())throw new IllegalStateException("A level-I node is never a laboratory target");
  var paid=t.getCompound("paid");for(var id:paid.getAllKeys()){var node=ResearchCatalog.get(id);if(node.resourcePaid()||!paid.contains(id,Tag.TAG_INT)||paid.getInt(id)<0||paid.getInt(id)>node.works())throw new IllegalStateException("Invalid research payment");}
  if(t.contains("pending")){var node=ResearchCatalog.get(t.getString("pending"));if(node.resourcePaid()||paid.getInt(node.id())>=node.works()||!t.hasUUID("operation")||!t.getUUID("operation").equals(operation(e,node.id(),paid.getInt(node.id()))))throw new IllegalStateException("Invalid pending research payment");}
  if(t.contains("queue")&&(!(t.get("queue") instanceof ListTag queue)||(!queue.isEmpty()&&queue.getElementType()!=Tag.TAG_STRING)))throw new IllegalStateException("Invalid research queue");
  var seen=new HashSet<String>();for(var id:strings(t,"queue")){if(ResearchCatalog.get(id).resourcePaid()||!seen.add(id))throw new IllegalStateException("Invalid research queue entry");}
  for(var id:strings(t,"resourceDone"))if(!ResearchCatalog.get(id).resourcePaid())throw new IllegalStateException("Invalid resource research");
  var orders=strings(t,"resourceOrders");if(orders.size()>ScienceBalance.RESOURCE_ORDERS||new HashSet<>(orders).size()!=orders.size())throw new IllegalStateException("Invalid resource orders");
  for(var id:orders)if(!ResearchCatalog.get(id).resourcePaid())throw new IllegalStateException("Invalid resource order");
  if(t.contains("pay")){var pay=t.getCompound("pay");if(!ResearchCatalog.get(pay.getString("node")).resourcePaid())throw new IllegalStateException("Invalid resource payment");}
 }
 /** AD-136 (§3.4, CF4): schema 1 → 2. A pending book payment is settled first by the schema-1 rules (its receipt is in the journal); the
  *  numbers are changed only after that, so no pending payment ever points at a changed count. Unknown and removed ids are tolerated here. */
 private static CompoundTag migrate(ServerLevel l,SettlementData.Entry e,CompoundTag old){
  if(!old.hasUUID("village")||!old.getUUID("village").equals(e.settlement().id()))throw new IllegalStateException("Invalid book research save");
  var paid=new LinkedHashMap<String,Integer>();var p=old.getCompound("paid");for(var id:p.getAllKeys())paid.put(id,Math.max(0,p.getInt(id)));
  if(old.contains("pending")&&old.hasUUID("operation")){var item=WorldJournal.recoverAmount(l,old.getUUID("operation"));if(!item.isEmpty())paid.merge(old.getString("pending"),1,Integer::sum);}
  var migrated=ResearchMigration.migrate(new LinkedHashSet<>(strings(old,"legacyDone")),old.getString("selected"),strings(old,"queue"),paid);
  var t=new CompoundTag();t.putInt("schema",SCHEMA);t.putUUID("village",e.settlement().id());t.putString("selected",migrated.selected());
  var np=new CompoundTag();migrated.paid().forEach(np::putInt);t.put("paid",np);t.put("queue",list(migrated.queue()));t.put("legacyDone",list(migrated.legacyDone()));
  String active=old.getString("legacyActive");try{if(!active.isEmpty())ResearchCatalog.legacyId(active);}catch(IllegalArgumentException ex){active="";}t.putString("legacyActive",active);
  t.putInt("credit",migrated.credit());t.put("resourceDone",list(migrated.resourceDone()));t.put("resourceOrders",new ListTag());t.putInt("migratedFrom",1);
  validate(e,t);return t;
 }
 public static boolean legacyAllowed(ServerLevel l,SettlementData.Entry e,String node){return !node.isEmpty()&&inspect(l,e).getString("legacyActive").equals(node);}
 private static UUID operation(SettlementData.Entry e,String node,int paid){return Settlement.childId(e.settlement().id(),"book-research/"+node+"/"+paid);}
 /** CF1: the done list, the old civilization's own, the level-I nodes paid in resources and every level II–VI node paid in full. A level-I
  *  node never follows from its payment count. */
 public static Set<String> completed(SettlementData.Entry e,CompoundTag t){var result=new LinkedHashSet<String>(strings(t,"legacyDone"));String legacy=t.getString("legacyActive");
  if(!legacy.isEmpty()&&e.settlement().civilization().completed().contains(legacy))result.add(ResearchCatalog.legacyId(legacy));
  result.addAll(strings(t,"resourceDone"));
  var paid=t.getCompound("paid");for(var id:paid.getAllKeys()){var n=ResearchCatalog.NODES.get(id);if(n!=null&&!n.resourcePaid()&&paid.getInt(id)>=n.works())result.add(id);}
  return result;}
 public static String reason(SettlementData.Entry e,CompoundTag t,String id){var node=ResearchCatalog.get(id);int hall=e.settlement().civilization().level();
  // AD-073, AD-124: one rule for the server, the office tree and the Overview (ResearchRules): the next hall level's research opens one hall tier early.
  return ResearchRules.reason(completed(e,t),hall,node,org.villageastra.world.BuildingTiers.hallResearch(hall+1));}
 /** The same rule with the stock looked at: a level-I node whose price is not in the hall and warehouse chests is "resources". */
 public static String reason(ServerLevel l,SettlementData.Entry e,CompoundTag t,String id){var node=ResearchCatalog.get(id);var base=reason(e,t,id);
  if(!base.equals("available")||!node.resourcePaid())return base;return plan(l,e,node)==null?"resources":"available";}
 /** AD-073: the research record as it is, for tests and probes that set a settlement up with knowledge it has already paid for. */
 public static void store(ServerLevel l,SettlementData.Entry e,CompoundTag t){save(l,e,t);}
 /** AD-101, AD-136: a village without a player mayor pays the first level-I node of its priority list whose whole price lies in the stock, then
  *  any other one; its laboratory target is the level II–VI node open to it that takes the fewest works. Returns what it did ("" for nothing). */
 public static String autoSelect(ServerLevel l,SettlementData.Entry e){
  if(e.settlement().governance().playerMayor()!=null)return "";
  var t=recover(l,e);
  var first=new ArrayList<String>(ScienceBalance.MAYOR_PRIORITY);for(var n:ResearchCatalog.NODES.values())if(n.resourcePaid()&&!first.contains(n.id()))first.add(n.id());
  for(var id:first)if(ResearchCatalog.NODES.containsKey(id)&&reason(e,t,id).equals("available")&&plan(l,e,ResearchCatalog.get(id))!=null&&payResources(l,e,id))return id;
  // An NPC needs the same material orders as a player. Waiting for a full price without publishing
  // its shortage left nobody making the chests, torches and tables that unlock school and science.
  t=recover(l,e);var orders=t.getList("resourceOrders",Tag.TAG_STRING);boolean queued=false;
  for(var id:first){if(orders.size()>=ScienceBalance.RESOURCE_ORDERS)break;
   if(!ResearchCatalog.NODES.containsKey(id)||!reason(e,t,id).equals("available")||orders.stream().anyMatch(raw->raw.getAsString().equals(id)))continue;
   orders.add(StringTag.valueOf(id));queued=true;}
  if(queued){t.put("resourceOrders",orders);save(l,e,t);}
  t=recover(l,e);if(!t.getString("selected").isEmpty())return "";
  String best=null;int works=Integer.MAX_VALUE;
  for(var n:ResearchCatalog.NODES.values())if(!n.resourcePaid()&&reason(e,t,n.id()).equals("available")&&n.works()<works){best=n.id();works=n.works();}
  if(best==null)return "";t.putString("selected",best);save(l,e,t);return best;
 }
 private static void save(ServerLevel l,SettlementData.Entry e,CompoundTag t){write(path(l,e.settlement().id()),t);}
 public static CompoundTag recover(ServerLevel l,SettlementData.Entry e){var t=inspect(l,e);
  if(t.contains("pay")&&settlePayment(l,e,t))save(l,e,t);
  if(!t.contains("pending"))return t;String node=t.getString("pending");var item=WorldJournal.recoverAmount(l,t.getUUID("operation"));
  if(!item.isEmpty()){if(!item.is(VillageAstra.RESEARCH_VOLUME.get())||item.getCount()!=1)throw new IllegalStateException("Wrong research receipt");var paid=t.getCompound("paid");paid.putInt(node,paid.getInt(node)+1);advance(e,t,node);}
  t.remove("pending");t.remove("operation");save(l,e,t);return t;}
 /** A node paid in full leaves the target and the next open node of the queue takes its place. */
 private static void advance(SettlementData.Entry e,CompoundTag t,String node){
  if(t.getCompound("paid").getInt(node)<ResearchCatalog.get(node).works()||!t.getString("selected").equals(node))return;
  t.putString("selected","");var queue=t.getList("queue",Tag.TAG_STRING);while(!queue.isEmpty()){String next=queue.remove(0).getAsString();if(reason(e,t,next).equals("available")){t.putString("selected",next);break;}}}
 public static boolean order(ServerPlayer p,UUID village,long epoch,long revision,String node){return order(p,village,epoch,revision,node,0);}
 /** Actions: 0 target, 1 queue (Science I), 2 out of the queue, 3 pay a level-I node in resources (now, or once the stock holds it), 4 cancel that order. */
 public static boolean order(ServerPlayer p,UUID village,long epoch,long revision,String node,int action){if(action<0||action>4)return false;var e=SettlementData.get(p.server).entry(village);if(e==null||!ManagementOrders.allowedContext(p,e)||!e.settlement().governance().canManage(p.getUUID(),epoch)||e.settlement().governance().revision()!=revision||!node.isEmpty()&&!ResearchCatalog.NODES.containsKey(node))return false;var t=recover(p.serverLevel(),e);var l=p.serverLevel();
  boolean tier1=!node.isEmpty()&&ResearchCatalog.get(node).resourcePaid();
  // AD-136 (CF3): a level-I node is paid in resources, never studied: it is refused as a target or queue entry before the order spends its revision.
  if(action==3){var orders=strings(t,"resourceOrders");if(!tier1||!reason(e,t,node).equals("available")||orders.contains(node)&&plan(l,e,ResearchCatalog.get(node))==null||orders.size()>=ScienceBalance.RESOURCE_ORDERS&&!orders.contains(node)&&plan(l,e,ResearchCatalog.get(node))==null)return false;
   if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;
   if(!payResources(l,e,node)){t=recover(l,e);var list=t.getList("resourceOrders",Tag.TAG_STRING);if(!orders.contains(node))list.add(StringTag.valueOf(node));t.put("resourceOrders",list);save(l,e,t);}
   SettlementData.get(p.server).setDirty();return true;}
  if(action==4){if(!strings(t,"resourceOrders").contains(node))return false;if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;var list=t.getList("resourceOrders",Tag.TAG_STRING);list.removeIf(raw->raw.getAsString().equals(node));t.put("resourceOrders",list);save(l,e,t);SettlementData.get(p.server).setDirty();return true;}
  if(tier1)return false;
  // AD-124: action 2 takes a node out of the queue; refused before recordOrder when there is nothing to take, so a refusal never spends the order's revision. Paid works and the target stay.
  if(action==2){if(node.isEmpty()||t.getList("queue",Tag.TAG_STRING).stream().noneMatch(raw->raw.getAsString().equals(node)))return false;if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;var queue=t.getList("queue",Tag.TAG_STRING);queue.removeIf(raw->raw.getAsString().equals(node));t.put("queue",queue);save(l,e,t);SettlementData.get(p.server).setDirty();return true;}
  if(action==1){if(node.isEmpty()||!completed(e,t).contains("research.1")||!reason(e,t,node).equals("available")||t.getString("selected").equals(node)||t.getList("queue",Tag.TAG_STRING).stream().anyMatch(raw->raw.getAsString().equals(node)))return false;if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;if(t.getString("selected").isEmpty())t.putString("selected",node);else{var queue=t.getList("queue",Tag.TAG_STRING);queue.add(StringTag.valueOf(node));t.put("queue",queue);}save(l,e,t);SettlementData.get(p.server).setDirty();return true;}if(t.getString("selected").equals(node)||!node.isEmpty()&&!reason(e,t,node).equals("available"))return false;if(!e.settlement().governance().recordOrder(p.getUUID(),epoch,revision))return false;t.putString("selected",node);var queue=t.getList("queue",Tag.TAG_STRING);queue.removeIf(raw->raw.getAsString().equals(node));save(l,e,t);SettlementData.get(p.server).setDirty();return true;}
 public static boolean selected(ServerLevel l,SettlementData.Entry e){return !inspect(l,e).getString("selected").isEmpty();}
 /** CF11: a scientific work the laboratory may spend — a quest's relic volume is not one while its quest holds it. */
 public static boolean work(ItemStack s){return s.is(VillageAstra.RESEARCH_VOLUME.get())&&(s.getTag()==null||!s.getTag().contains(QuestSites.RELIC));}
 /** Trusted service at the scientist's physical desk; not a network operation. One work a step: the credit first, then the laboratory chest. */
 public static boolean consume(ServerLevel l,SettlementData.Entry e,Settlement.Building lab){var t=recover(l,e);String node=t.getString("selected");if(node.isEmpty()||!reason(e,t,node).equals("available")||!lab.type().equals("laboratory"))return false;
  if(ResearchCatalog.get(node).resourcePaid())return false;
  if(t.getInt("credit")>0){t.putInt("credit",t.getInt("credit")-1);var paid=t.getCompound("paid");paid.putInt(node,paid.getInt(node)+1);advance(e,t,node);save(l,e,t);return true;}
  var c=LogisticsRoutes.chest(l,e,lab);if(c==null)return false;for(int slot=0;slot<c.getContainerSize();slot++)if(work(c.getItem(slot))){var op=operation(e,node,t.getCompound("paid").getInt(node));t.putString("pending",node);t.putUUID("operation",op);save(l,e,t);WorldJournal.takeAmount(l,op,c.getBlockPos(),slot,c.getItem(slot).copy(),1);recover(l,e);return true;}return false;}
 // ---- level I: resources ---------------------------------------------------------------------
 /** Whether a stack pays this line of a level-I price. */
 public static boolean matches(ResearchCatalog.Cost cost,ItemStack s){if(s.isEmpty())return false;
  if(cost.tag()!=null)return s.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(cost.tag())));
  return s.is(BuiltInRegistries.ITEM.get(new ResourceLocation(cost.item())));}
 /** The chests a level-I price is paid from: the hall's, then every warehouse's. */
 public static List<Settlement.Building> stock(SettlementData.Entry e){var out=new ArrayList<Settlement.Building>();var hall=Workshops.hall(e);if(hall!=null)out.add(hall);for(var b:e.settlement().buildings())if(b.type().equals("warehouse"))out.add(b);return out;}
 private record Take(BlockPos pos,int slot,ItemStack before,int amount){}
 /** How many of this line the stock holds, not counting what an approved construction project keeps (CF3). */
 public static int have(ServerLevel l,SettlementData.Entry e,ResearchCatalog.Cost cost){int n=0;var maintenance=new HashMap<Item,Integer>();
  for(var b:stock(e)){var c=LogisticsRoutes.chest(l,e,b);if(c==null)continue;var kept=new HashMap<Item,Integer>();
   for(int slot=0;slot<c.getContainerSize();slot++){var s=c.getItem(slot);if(!matches(cost,s))continue;int reserve=kept.computeIfAbsent(s.getItem(),i->LogisticsRoutes.constructionReserve(l,e,b,s));int use=Math.max(0,s.getCount()-reserve);kept.put(s.getItem(),Math.max(0,reserve-s.getCount()));int buffer=maintenance.computeIfAbsent(s.getItem(),i->ToolSupplyReserve.researchQuantity(l,e,i));int cut=Math.min(use,buffer);maintenance.put(s.getItem(),buffer-cut);n+=use-cut;}}
  return n;}
 /** The exact withdrawals that pay a level-I price now, or null when the stock does not hold all of it (no partial payment). */
 private static List<Take> plan(ServerLevel l,SettlementData.Entry e,ResearchCatalog.Node node){
  var out=new ArrayList<Take>();var used=new HashSet<Long>();
  for(var cost:node.resources()){int need=cost.count();var maintenance=new HashMap<Item,Integer>();
   for(var b:stock(e)){if(need<=0)break;var c=LogisticsRoutes.chest(l,e,b);if(c==null)continue;var kept=new HashMap<Item,Integer>();
    for(int slot=0;slot<c.getContainerSize()&&need>0;slot++){var s=c.getItem(slot);if(!matches(cost,s)||!used.add(c.getBlockPos().asLong()*64+slot))continue;
     int reserve=kept.computeIfAbsent(s.getItem(),i->LogisticsRoutes.constructionReserve(l,e,b,s));int free=Math.max(0,s.getCount()-reserve);kept.put(s.getItem(),Math.max(0,reserve-s.getCount()));
     int buffer=maintenance.computeIfAbsent(s.getItem(),i->ToolSupplyReserve.researchQuantity(l,e,i));int cut=Math.min(free,buffer);maintenance.put(s.getItem(),buffer-cut);free-=cut;int take=Math.min(need,free);if(take<=0)continue;out.add(new Take(c.getBlockPos(),slot,s.copy(),take));need-=take;}}
   if(need>0)return null;}
  return out;}
 /** AD-136 (§1.3): pays a level-I node from the stock in one journal batch (ids research/&lt;node&gt;/pay/i) and records it done; false when the
  *  node is not open or the stock lacks any of its price. The withdrawals are written into the record first, so a stop in the middle is
  *  settled once on the next recover: all of them taken — done; some — they are returned to the hall. */
 public static boolean payResources(ServerLevel l,SettlementData.Entry e,String id){
  var t=recover(l,e);var node=ResearchCatalog.get(id);if(!node.resourcePaid()||!reason(e,t,id).equals("available")||t.contains("pay"))return false;
  var takes=plan(l,e,node);if(takes==null)return false;
  var pay=new CompoundTag();pay.putString("node",id);var ops=new ListTag();int i=0;long round=t.getLong("payRound");
  for(var take:takes){var op=new CompoundTag();op.putUUID("id",Settlement.childId(e.settlement().id(),"research/"+id+"/pay/"+round+"/"+(i++)));op.putLong("pos",take.pos().asLong());op.putInt("slot",take.slot());op.put("before",take.before().save(new CompoundTag()));op.putInt("amount",take.amount());ops.add(op);}
  pay.put("ops",ops);t.put("pay",pay);t.putLong("payRound",round+1);save(l,e,t);
  WorldJournal.batch(l,()->{for(var raw:ops){var op=(CompoundTag)raw;WorldJournal.takeAmount(l,op.getUUID("id"),BlockPos.of(op.getLong("pos")),op.getInt("slot"),ItemStack.of(op.getCompound("before")),op.getInt("amount"));}return null;});
  t=recover(l,e);return completed(e,t).contains(id);
 }
 /** Settles a written level-I payment (see payResources); false while one of its chests is unloaded. */
 private static boolean settlePayment(ServerLevel l,SettlementData.Entry e,CompoundTag t){
  var pay=t.getCompound("pay");var ops=pay.getList("ops",Tag.TAG_COMPOUND);
  for(var raw:ops)if(!l.hasChunkAt(BlockPos.of(((CompoundTag)raw).getLong("pos"))))return false;
  var taken=new ArrayList<ItemStack>();boolean all=true;
  for(var raw:ops){var op=(CompoundTag)raw;var id=op.getUUID("id");
   var got=WorldJournal.exists(l,id)?WorldJournal.recoverAmount(l,id):ItemStack.EMPTY;if(got.isEmpty())all=false;else taken.add(got);}
  String node=pay.getString("node");
  if(all){var done=t.getList("resourceDone",Tag.TAG_STRING);if(done.stream().noneMatch(x->x.getAsString().equals(node)))done.add(StringTag.valueOf(node));t.put("resourceDone",done);}
  else{var hall=Workshops.hall(e);var to=hall==null?null:LogisticsRoutes.position(e,hall);int k=0;
   for(var item:taken){var back=Settlement.childId(e.settlement().id(),"research/"+node+"/refund/"+t.getLong("payRound")+"/"+(k++));if(to!=null)WorldJournal.deposit(l,back,to,item);}}
  var orders=t.getList("resourceOrders",Tag.TAG_STRING);if(all)orders.removeIf(x->x.getAsString().equals(node));t.put("resourceOrders",orders);
  t.remove("pay");return true;
 }
 /** Every 40 ticks: the ordered level-I nodes whose whole price now lies in the stock are paid (CF3). */
 public static int payOrders(ServerLevel l,SettlementData.Entry e){int paid=0;
  for(var id:strings(recover(l,e),"resourceOrders")){var t=recover(l,e);if(!reason(e,t,id).equals("available")){var drop=t.getList("resourceOrders",Tag.TAG_STRING);if(completed(e,t).contains(id)){drop.removeIf(x->x.getAsString().equals(id));t.put("resourceOrders",drop);save(l,e,t);}continue;}
   if(payResources(l,e,id))paid++;}
  return paid;}
 /** What the porters bring to the hall for the ordered level-I nodes: each line's shortfall against the stock. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e){return wants(l,e,id->true);}
 /** Selected existing orders only; planning never creates or pays research. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,java.util.function.Predicate<String> include){
  var hall=Workshops.hall(e);if(hall==null||!Files.exists(path(l,e.settlement().id())))return List.of();var out=new ArrayList<Workshops.Want>();
  for(var id:strings(inspect(l,e),"resourceOrders")){if(!include.test(id))continue;var n=ResearchCatalog.NODES.get(id);if(n==null)continue;
   for(var cost:n.resources()){int missing=cost.count()-have(l,e,cost);if(missing<=0)continue;
    var ingredient=cost.tag()!=null?net.minecraft.world.item.crafting.Ingredient.of(TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(cost.tag()))):net.minecraft.world.item.crafting.Ingredient.of(BuiltInRegistries.ITEM.get(new ResourceLocation(cost.item())));
    out.add(new Workshops.Want(ingredient,missing,hall.id()));}}
  return out;}
 public static void addView(ServerPlayer p,CompoundTag tag){if(!tag.hasUUID("village"))return;var e=SettlementData.get(p.server).entry(tag.getUUID("village"));var l=p.serverLevel();var state=recover(l,e);var view=new CompoundTag();view.putString("selected",state.getString("selected"));view.put("paid",state.getCompound("paid").copy());view.put("queue",state.getList("queue",Tag.TAG_STRING).copy());var done=new ListTag();var completed=completed(e,state);completed.forEach(id->done.add(StringTag.valueOf(id)));view.put("completed",done);view.putInt("hall",e.settlement().civilization().level());view.putString("legacyActive",e.settlement().civilization().active());view.putLong("legacyProgress",e.settlement().civilization().progress());
  // AD-136: works credited before the rework, the level-I nodes ordered paid, and what the stock holds of every open level-I price.
  view.putInt("credit",state.getInt("credit"));view.put("resourceOrders",state.getList("resourceOrders",Tag.TAG_STRING).copy());
  var stock=new CompoundTag();for(var n:ResearchCatalog.NODES.values()){if(!n.resourcePaid()||completed.contains(n.id()))continue;var rows=new ListTag();
   for(var cost:n.resources()){var row=new CompoundTag();row.putString("key",cost.key());row.putInt("need",cost.count());row.putInt("have",Math.min(cost.count(),have(l,e,cost)));rows.add(row);}stock.put(n.id(),rows);}
  view.put("stock",stock);
  // AD-124: works in the laboratories (-1 while a lab chest is unloaded), living scientists, and works a day (-1 without history), from the Overview's 40-tick reading.
  var o=OfficeOverview.cached(p.server,e);var trend=o.getCompound("trend");view.putInt("labs",o.getInt("labs"));view.putInt("labVolumes",o.getInt("labVolumes"));view.putInt("scientists",o.getInt("scientists"));
  // AD-136: the scientists writing now (seated) and the works an hour they write together (2 each: one per 30 minutes of the village clock).
  int seated=ScienceWorks.seated(l,e);view.putInt("seated",seated);view.putDouble("worksPerHour",seated*72000.0/ScienceBalance.WORK_TICKS);
  view.putDouble("rate",trend.getInt("span")>0?trend.getInt("paid")/(double)trend.getInt("span"):-1);tag.put("research",view);}
}
