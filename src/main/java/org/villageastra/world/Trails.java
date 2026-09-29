package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** AD-123 (Roads II and V, check fix C10/C11): a trail from this village's gate to a neighbour's gate, built by an abstract crew of one builder.
 *  <ul><li>The trail has its own record ({@code data/astra-roads/<village>-trail.bin}) and never takes the village's road slot ({@link Roads#order}),
 *  so walls, terraces, clearings and upkeep go on while it is built.</li>
 *  <li>It is laid in segments of at most {@link #SEGMENT} cells: each segment is surveyed on real, touch-loaded ground, its whole material is taken
 *  from the hall through the journal into the crew's cargo (a short hall waits: {@code trail_missing_materials}), then the crew lays one cell every
 *  {@link #CELL_TICKS} active ticks, each block through the journal. A changed or protected cell is skipped and counted.</li>
 *  <li>Water, lava, a drop or a rise of more than one block refuses a segment ({@code needs_bridge}, {@code needs_tunnel}, {@code lava}) until
 *  Roads V: then the crew lays a plank bridge with rails (span ≤ 32) or bores a 3×3 tunnel lit every 8 cells (length ≤ 32; any fluid in its shell
 *  refuses it, {@code needs_dry_ground}; sand and gravel over the bore become cobblestone). Up to eight waypoints route a trail around an obstacle.</li>
 *  <li>Trail cells are registered to the village with the trail mark: they speed walkers like any road, village upkeep and machines skip them,
 *  and a caravan on the gate-to-gate line gains the mean surface bonus of that line ({@link #routeBonus}).</li>
 *  <li>One builder is on the trail while the crew is out; neither road work nor a hall project takes him. With a player near the crew cell the
 *  builder himself is brought there, and nothing is placed out of his reach.</li></ul> */
public final class Trails {
 private Trails(){}
 private static final JsonObject ROOT=root();
 public static final int CELL_TICKS=positive("trail_cell_ticks"),SEGMENT=positive("trail_segment"),MAX_DISTANCE=positive("trail_max_distance");
 public static final int MAX_WAYPOINTS=8,SPAN=32,TUNNEL=32,BAND=3,REACH=6,LAMP_EVERY=8,SUPPORT_EVERY=8,SUPPORT_HEIGHT=4;
 public static final String SURVEY="survey",SUPPLY="supply",BUILD="build",RETURN="return",COMPLETE="complete",BLOCKED="blocked";
 private static final ItemStack PICK=new ItemStack(Items.IRON_PICKAXE);
 private static JsonObject root(){try(var s=Trails.class.getResourceAsStream("/data/villageastra/balance/roads.json")){if(s==null)throw new IllegalStateException("Missing roads balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static int positive(String key){int v=ROOT.get(key).getAsInt();if(v<1)throw new IllegalStateException("Invalid "+key);return v;}
 public static void clear(){}
 // ---- record ------------------------------------------------------------------------------------
 public static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-roads/"+village+"-trail.bin");}
 public static CompoundTag record(ServerLevel l,UUID village){var p=path(l,village);if(!Files.exists(p))return null;var t=NbtRecord.read(p);if(t.getInt("schema")!=1)throw new IllegalStateException("Unknown trail schema "+t.getInt("schema"));return t;}
 public static void save(ServerLevel l,UUID village,CompoundTag t){NbtRecord.write(path(l,village),t);}
 private static boolean open(CompoundTag t){var s=t.getString("state");return s.equals(SURVEY)||s.equals(SUPPLY)||s.equals(BUILD)||s.equals(RETURN);}
 public static boolean active(ServerLevel l,UUID village){var t=record(l,village);return t!=null&&open(t);}
 /** The builder the crew took: busy on the trail, so neither RoadWorkGoal nor HallUpgradeGoal gives him other work. */
 public static boolean onTrail(ServerLevel l,SettlementData.Entry e,UUID resident){var t=record(l,e.settlement().id());return t!=null&&open(t)&&t.hasUUID("builder")&&t.getUUID("builder").equals(resident);}
 // ---- ordering ----------------------------------------------------------------------------------
 /** Why this village cannot lay a trail to that one now; empty when it can. */
 public static String refusal(ServerLevel l,SettlementData.Entry e,UUID target,List<BlockPos> waypoints){
  if(!ResearchGate.has(l,e,"roads.2"))return "research";
  var d=SettlementData.get(l.getServer()).entry(target);
  if(d==null||target.equals(e.settlement().id()))return "target";
  if(!d.dimension().equals(e.dimension()))return "dimension";
  if(Relations.score(l.getServer(),e.settlement().id(),target)<=Relations.HOSTILE)return "hostile";
  if(waypoints.size()>MAX_WAYPOINTS)return "waypoints";
  if(Sieges.besieged(l.getServer(),e.settlement().id()))return "besieged";
  var a=Caravans.gate(l,e,d.center());var b=Caravans.gate(l,d,e.center());
  if(Math.sqrt(Math.pow(a.getX()-b.getX(),2)+Math.pow(a.getZ()-b.getZ(),2))>MAX_DISTANCE)return "distance";
  var old=record(l,e.settlement().id());if(old!=null&&open(old))return "busy";
  if(crew(l,e)==null)return "no_builder";
  return "";
 }
 /** A builder for the crew: one not holding the hall project if the village has another. */
 static UUID crew(ServerLevel l,SettlementData.Entry e){
  var builders=e.settlement().residents().stream().filter(r->r.alive()&&r.profession()==Profession.BUILDER).map(Resident::id).sorted().toList();
  if(builders.isEmpty())return null;var hall=HallUpgradeGoal.pending(l,e.settlement().id())?HallUpgradeGoal.inspect(l,e.settlement().id()):null;
  UUID busy=hall!=null&&hall.hasUUID("worker")?hall.getUUID("worker"):builders.get(0);
  for(int i=builders.size()-1;i>=0;i--)if(!builders.get(i).equals(busy))return builders.get(i);
  return builders.get(0);
 }
 /** The centre line of a trail: straight runs from the gate through the waypoints to the neighbour's gate, one column per step. */
 public static List<BlockPos> route(BlockPos from,List<BlockPos> waypoints,BlockPos to){
  var points=new ArrayList<BlockPos>();points.add(from);points.addAll(waypoints);points.add(to);var out=new ArrayList<BlockPos>();
  for(int i=0;i+1<points.size();i++){var a=points.get(i);var b=points.get(i+1);int dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ(),n=Math.max(Math.abs(dx),Math.abs(dz));
   for(int k=0;k<=n;k++){var p=new BlockPos(a.getX()+(int)Math.round(dx*(double)k/Math.max(1,n)),0,a.getZ()+(int)Math.round(dz*(double)k/Math.max(1,n)));if(out.isEmpty()||!out.get(out.size()-1).equals(p))out.add(p);}}
  return out;
 }
 /** Orders a trail (surface 0 dirt path or 1 gravel, lamps with Roads III). Returns the refusal, or empty when the crew sets out. */
 public static String order(ServerLevel l,SettlementData.Entry e,UUID target,int surface,boolean light,List<BlockPos> waypoints){
  var why=refusal(l,e,target,waypoints);if(!why.isEmpty())return why;
  if(surface<0||surface>1)return "surface";if(surface==1&&!ResearchGate.has(l,e,"roads.1"))return "research";if(light&&!ResearchGate.has(l,e,"roads.3"))return "research";
  var d=SettlementData.get(l.getServer()).entry(target);var toward=waypoints.isEmpty()?d.center():waypoints.get(0);var back=waypoints.isEmpty()?e.center():waypoints.get(waypoints.size()-1);
  var a=Caravans.gate(l,e,toward);var b=Caravans.gate(l,d,back);
  // Waypoints come from the client: the whole polyline is bounded before a column list is built (at most twice the gate distance limit).
  long span=0;var at=a;for(var w:waypoints){span+=Math.max(Math.abs(w.getX()-at.getX()),Math.abs(w.getZ()-at.getZ()));at=w;}span+=Math.max(Math.abs(b.getX()-at.getX()),Math.abs(b.getZ()-at.getZ()));
  if(span>2L*MAX_DISTANCE)return "distance";
  var line=route(a,waypoints,b);
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(e.settlement().id(),"trail/"+l.getGameTime()));t.putUUID("target",target);t.putInt("surface",surface);t.putBoolean("light",light);
  t.putBoolean("bridges",ResearchGate.has(l,e,"roads.5"));var cols=new long[line.size()];for(int i=0;i<cols.length;i++)cols[i]=line.get(i).asLong();t.putLongArray("route",cols);
  var wp=new long[waypoints.size()];for(int i=0;i<wp.length;i++)wp[i]=waypoints.get(i).asLong();t.putLongArray("waypoints",wp);
  t.putInt("segment",0);t.putInt("walk",a.getY()-1);t.putString("state",SURVEY);t.putString("reason","");t.putUUID("builder",crew(l,e));
  t.put("ops",new ListTag());t.putInt("index",0);t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putInt("laid",0);t.putInt("skipped",0);t.putInt("bridge",0);t.putInt("tunnel",0);
  save(l,e.settlement().id(),t);SettlementData.get(l.getServer()).setDirty();return "";
 }
 /** A crew pass that renews the worn cells of the finished trail, paid like any repair; village upkeep never does it. */
 public static String repair(ServerLevel l,SettlementData.Entry e){
  var old=record(l,e.settlement().id());if(old==null||!old.getString("state").equals(COMPLETE))return "no_trail";if(crew(l,e)==null)return "no_builder";
  var ops=new ListTag();int col=0;
  for(var x:Roads.cells(l,e.settlement().id())){var c=x.getValue();if(!c.trail||c.wear<Roads.REPAIR_WEAR)continue;var item=Roads.TIERS.get(c.tier).item();
   var o=op("repair",x.getKey(),Blocks.AIR.defaultBlockState(),Blocks.AIR.defaultBlockState(),item);o.putInt("col",col++);ops.add(o);}
  if(ops.isEmpty())return "nothing";
  var t=old.copy();t.putString("state",SUPPLY);t.putString("reason","");t.put("ops",ops);t.putInt("index",0);t.putBoolean("repairPass",true);t.putUUID("builder",crew(l,e));save(l,e.settlement().id(),t);return "";
 }
 // ---- the crew ------------------------------------------------------------------------------------
 /** Advances every open trail of the server once per second of active time. */
 public static void tick(MinecraftServer s,long now){
  for(var e:List.copyOf(SettlementData.get(s).entries())){var l=s.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(e.dimension())));if(l==null)continue;
   var p=path(l,e.settlement().id());if(!Files.exists(p))continue;var t=record(l,e.settlement().id());if(t==null||!open(t))continue;
   step(l,e,Math.max(1,20/CELL_TICKS));}
 }
 /** One crew step: survey, supply, lay up to {@code cells} cells, or return; returns the state afterwards. */
 public static String step(ServerLevel l,SettlementData.Entry e,int cells){
  var village=e.settlement().id();var t=record(l,village);if(t==null)return "";
  if(Sieges.besieged(l.getServer(),village)){t.putString("reason","besieged");save(l,village,t);return t.getString("state");}
  switch(t.getString("state")){
   case SURVEY->survey(l,e,t);
   case SUPPLY->supply(l,e,t);
   case BUILD->{for(int i=0;i<cells&&t.getString("state").equals(BUILD);i++)if(!build(l,e,t))break;}
   case RETURN->{if(unload(l,e,t)){t.putString("state",t.getBoolean("blockedAfter")?BLOCKED:COMPLETE);t.remove("blockedAfter");home(l,e,t);}}
   default->{}
  }
  save(l,village,t);return t.getString("state");
 }
 private static long[] route(CompoundTag t){return t.getLongArray("route");}
 private static BlockPos column(CompoundTag t,int i){var r=route(t);return BlockPos.of(r[Math.max(0,Math.min(r.length-1,i))]);}
 /** The side of the line at column i (a unit step at right angles to the way the trail goes). */
 private static int[] side(CompoundTag t,int i){var r=route(t);var a=BlockPos.of(r[Math.max(0,i-1)]);var b=BlockPos.of(r[Math.min(r.length-1,i+1)]);
  int dx=Integer.signum(b.getX()-a.getX()),dz=Integer.signum(b.getZ()-a.getZ());int px=dz,pz=-dx;if(px==0&&pz==0)px=1;if(px!=0&&pz!=0)pz=0;return new int[]{px,pz};}
 private static CompoundTag op(String kind,BlockPos pos,BlockState before,BlockState after,Item item){var t=new CompoundTag();t.putString("kind",kind);t.putLong("pos",pos.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));t.putString("item",item==null||item==Items.AIR?"":BuiltInRegistries.ITEM.getKey(item).toString());return t;}
 private static void block(CompoundTag t,String reason,BlockPos at){t.putString("reason",reason);t.putLong("blockedAt",at.asLong());t.putBoolean("blockedAfter",true);t.putString("state",RETURN);}
 /** Blocks the ground search passes over: trees, plants and snow above the real ground. */
 private static boolean cover(BlockState s){return s.isAir()||s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.canBeReplaced()&&s.getFluidState().isEmpty()||s.is(Blocks.BAMBOO)||s.is(Blocks.CACTUS)||s.is(Blocks.SUGAR_CANE);}
 /** Natural ground a trail may pave or a tunnel may cut; anything built by someone is left alone. */
 static boolean natural(BlockState s){return s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.TERRACOTTA)||s.is(Blocks.CLAY)||s.is(Blocks.SNOW_BLOCK)
  ||s.is(Blocks.DIRT_PATH)||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK)||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(Blocks.PACKED_ICE)||s.is(Blocks.ICE);}
 private static boolean clearable(BlockState s){return natural(s)||cover(s);}
 private record Ground(int y,int kind,int fluidTop){} // kind 0 solid, 1 water, 2 lava
 private static Ground ground(ServerLevel l,int x,int z){
  int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1;int fluidTop=Integer.MIN_VALUE;
  for(;y>l.getMinBuildHeight();y--){var s=l.getBlockState(new BlockPos(x,y,z));
   if(s.getFluidState().is(Fluids.LAVA)||s.getFluidState().is(Fluids.FLOWING_LAVA))return new Ground(y,2,y);
   if(!s.getFluidState().isEmpty()){if(fluidTop==Integer.MIN_VALUE)fluidTop=y;continue;}
   if(cover(s))continue;
   return fluidTop!=Integer.MIN_VALUE?new Ground(y,1,fluidTop):new Ground(y,0,Integer.MIN_VALUE);}
  return new Ground(y,0,Integer.MIN_VALUE);
 }
 private static boolean protectedCell(ServerLevel l,SettlementData.Entry e,BlockPos pos){
  if(OwnershipEvents.protectedBlock(l,pos))return true;var c=Roads.cell(l,pos);return c!=null&&!c.village.equals(e.settlement().id());}
 /** Surveys the current segment on real ground (its chunks touch-loaded, DEFERRED waits) and plans its operations. */
 static void survey(ServerLevel l,SettlementData.Entry e,CompoundTag t){
  int from=t.getInt("segment"),to=Math.min(route(t).length,from+SEGMENT);
  if(from>=to){t.putString("state",RETURN);return;}
  var touch=new ArrayList<BlockPos>();for(int i=from;i<to;i++){var c=column(t,i);var sd=side(t,i);for(int k=-2;k<=2;k++)touch.add(c.offset(sd[0]*k,0,sd[1]*k));}
  var loaded=TouchLoad.ensureAll(l,touch);if(loaded!=TouchLoad.Touch.OK){t.putString("reason","missing_chunk");return;}
  boolean bridges=t.getBoolean("bridges")||ResearchGate.has(l,e,"roads.5");t.putBoolean("bridges",bridges);
  var surface=Roads.TIERS.get(t.getInt("surface"));var top=surface.block().defaultBlockState();var ops=new ListTag();
  int walk=t.getInt("walk"),bridge=t.getInt("bridge"),tunnel=t.getInt("tunnel");var air=Blocks.AIR.defaultBlockState();t.putInt("surveyed",from);
  for(int i=from;i<to;i++){var c=column(t,i);var sd=side(t,i);var g=ground(l,c.getX(),c.getZ());var col=new ArrayList<CompoundTag>();
   if(g.kind==2){block(t,"lava",new BlockPos(c.getX(),g.y,c.getZ()));break;}
   int rise=g.y-walk;boolean water=g.kind==1;
   if(water||rise<-1){ // a bridge deck at the walking level (over water at least one above the surface)
    if(!bridges){block(t,"needs_bridge",new BlockPos(c.getX(),g.y,c.getZ()));break;}
    int deck=water?Math.max(walk,g.fluidTop+1):walk;if(deck-walk>1){block(t,"needs_bridge",new BlockPos(c.getX(),deck,c.getZ()));break;}
    if(++bridge>SPAN){block(t,"span",new BlockPos(c.getX(),deck,c.getZ()));break;}tunnel=0;
    var at=new BlockPos(c.getX(),deck,c.getZ());var now=l.getBlockState(at);
    if(protectedCell(l,e,at)||!(now.isAir()||now.canBeReplaced()&&now.getFluidState().isEmpty())){block(t,"obstacle",at);break;}
    col.add(op("deck",at,now,Blocks.OAK_PLANKS.defaultBlockState(),Items.OAK_PLANKS));
    for(int k=-1;k<=1;k+=2){var rail=at.offset(sd[0]*k,1,sd[1]*k);if(l.getBlockState(rail).isAir()&&!protectedCell(l,e,rail))col.add(op("rail",rail,air,Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));}
    if(!water&&deck-g.y>SUPPORT_HEIGHT&&bridge%SUPPORT_EVERY==1){var post=at.below();if(l.getBlockState(post).isAir())col.add(op("support",post,air,Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));}
    walk=deck;
   }else if(rise>1){ // a tunnel through the rise, at the level of its entry
    if(!bridges){block(t,"needs_tunnel",new BlockPos(c.getX(),g.y,c.getZ()));break;}
    if(++tunnel>TUNNEL){block(t,"tunnel_too_long",new BlockPos(c.getX(),walk,c.getZ()));break;}bridge=0;
    String bad="";
    for(int k=-2;k<=2&&bad.isEmpty();k++)for(int dy=0;dy<=4;dy++){var p=c.offset(sd[0]*k,0,sd[1]*k).atY(walk+dy);var s=l.getBlockState(p);
     if(!s.getFluidState().isEmpty()){bad="needs_dry_ground";break;}
     boolean bore=Math.abs(k)<=1&&dy>=1&&dy<=3;if(bore&&!s.isAir()&&(!clearable(s)||protectedCell(l,e,p)||l.getBlockEntity(p)!=null)){bad="obstacle";break;}}
    if(!bad.isEmpty()){block(t,bad,new BlockPos(c.getX(),walk,c.getZ()));break;}
    for(int k=-1;k<=1;k++)for(int dy=3;dy>=1;dy--){var p=c.offset(sd[0]*k,0,sd[1]*k).atY(walk+dy);var s=l.getBlockState(p);if(!s.isAir())col.add(op("bore",p,s,air,null));}
    for(int k=-1;k<=1;k++){var p=c.offset(sd[0]*k,0,sd[1]*k).atY(walk+4);var s=l.getBlockState(p);if(s.getBlock() instanceof FallingBlock)col.add(op("ceiling",p,s,Blocks.COBBLESTONE.defaultBlockState(),Items.COBBLESTONE));}
    var floor=c.atY(walk);var fs=l.getBlockState(floor);if(!fs.is(top.getBlock())&&natural(fs))col.add(op("surface",floor,fs,top,surface.item()));
    if(tunnel%LAMP_EVERY==1){var lamp=c.offset(sd[0],0,sd[1]).atY(walk+1);col.add(op("lamp",lamp,air,Blocks.LANTERN.defaultBlockState(),Items.LANTERN));}
   }else{ // open ground: pave the ground cell and clear the head room
    bridge=0;tunnel=0;walk=g.y;var at=c.atY(g.y);var now=l.getBlockState(at);
    for(int dy=2;dy>=1;dy--){var p=at.above(dy);var s=l.getBlockState(p);if(!s.isAir()&&cover(s)&&!protectedCell(l,e,p))col.add(op("clear",p,s,air,null));}
    boolean own=Roads.cell(l,at)!=null&&Roads.cell(l,at).village.equals(e.settlement().id());int tier=Roads.tierOf(now);
    if(protectedCell(l,e,at)||!natural(now)&&tier<0){t.putInt("skipped",t.getInt("skipped")+1);}
    else if(tier>=t.getInt("surface")){if(!own)col.add(op("keep",at,now,now,null));}
    else col.add(op("surface",at,now,top,surface.item()));
    if(t.getBoolean("light")&&(i%Roads.LAMP_SPACING)==0){var post=at.offset(sd[0],1,sd[1]);var base=post.below();
     if(l.getBlockState(post).isAir()&&l.getBlockState(post.above()).isAir()&&l.getBlockState(base).isFaceSturdy(l,base,Direction.UP)&&!protectedCell(l,e,post)){
      col.add(op("post",post,air,Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));col.add(op("lamp",post.above(),air,Blocks.LANTERN.defaultBlockState(),Items.LANTERN));}}
   }
   for(var o:col){o.putInt("col",i);ops.add(o);}
   t.putInt("surveyed",i+1);
  }
  t.putInt("walk",walk);t.putInt("bridge",bridge);t.putInt("tunnel",tunnel);
  if(t.getString("state").equals(RETURN)){ // refused inside the segment: what was planned before the obstacle is still laid
   int stop=t.getInt("surveyed");t.putInt("segmentEnd",stop);}
  else t.putInt("segmentEnd",to);
  t.put("ops",ops);t.putInt("index",0);
  if(!ops.isEmpty()){t.putBoolean("refusedAfter",t.getString("state").equals(RETURN));t.putString("state",SUPPLY);if(!t.getBoolean("refusedAfter"))t.putString("reason","");}
  else if(!t.getString("state").equals(RETURN)){ // nothing to lay here (the way is paved already): on to the next segment
   t.putInt("segment",to);t.putString("reason","");if(to>=route(t).length)t.putString("state",RETURN);}
 }
 /** What the remaining operations of the segment take beyond the cargo the crew already carries. */
 public static Map<Item,Integer> needs(CompoundTag t){var out=new LinkedHashMap<Item,Integer>();if(t==null)return out;var ops=t.getList("ops",Tag.TAG_COMPOUND);
  for(int i=t.getInt("index");i<ops.size();i++){var key=ops.getCompound(i).getString("item");if(key.isEmpty())continue;var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));if(item!=Items.AIR)out.merge(item,1,Integer::sum);}
  for(var raw:t.getList("cargo",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);out.computeIfPresent(s.getItem(),(k,v)->v-s.getCount());}
  out.values().removeIf(n->n<=0);return out;}
 /** The crew at the hall: returns the spoil, then takes the whole material of the segment at once, or waits. */
 static void supply(ServerLevel l,SettlementData.Entry e,CompoundTag t){
  if(!unload(l,e,t))return;
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null){t.putString("reason","trail_missing_materials");return;}
  var needs=needs(t);for(var n:needs.entrySet())if(HallReserve.count(l,e,hall,chest,s->s.is(n.getKey())&&Trade.plain(s))<n.getValue()){t.putString("reason","trail_missing_materials");return;}
  var pos=LogisticsRoutes.position(e,hall);var cargo=t.getList("cargo",Tag.TAG_COMPOUND);
  for(var n:needs.entrySet()){int want=n.getValue();
   while(want>0){var id=Settlement.childId(t.getUUID("id"),"take/"+t.getInt("withdrawals"));var got=WorldJournal.recoverAmount(l,id);
    if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(s.is(n.getKey())&&Trade.plain(s)){got=WorldJournal.takeAmount(l,id,pos,slot,s.copy(),Math.min(want,s.getCount()));break;}}
    if(got.isEmpty()){t.put("cargo",cargo);t.putString("reason","trail_missing_materials");return;}
    cargo.add(got.save(new CompoundTag()));t.putInt("withdrawals",t.getInt("withdrawals")+1);want-=got.getCount();t.put("cargo",cargo);save(l,e.settlement().id(),t);}}
  t.put("cargo",cargo);t.putString("reason",t.getBoolean("refusedAfter")?t.getString("reason"):"");t.putString("state",BUILD);
 }
 private static int count(ListTag stacks,Item item){int n=0;for(var raw:stacks){var s=ItemStack.of((CompoundTag)raw);if(s.is(item))n+=s.getCount();}return n;}
 private static boolean consume(ListTag stacks,Item item){for(int i=0;i<stacks.size();i++){var s=ItemStack.of(stacks.getCompound(i));if(s.is(item)){s.shrink(1);if(s.isEmpty())stacks.remove(i);else stacks.set(i,s.save(new CompoundTag()));return true;}}return false;}
 private static void add(ListTag stacks,ItemStack stack){if(stack.isEmpty())return;for(int i=0;i<stacks.size();i++){var s=ItemStack.of(stacks.getCompound(i));if(ItemStack.isSameItemSameTags(s,stack)&&s.getCount()+stack.getCount()<=s.getMaxStackSize()){s.grow(stack.getCount());stacks.set(i,s.save(new CompoundTag()));return;}}stacks.add(stack.save(new CompoundTag()));}
 /** The builder's body, when it is loaded: brought to the crew cell while a player watches (C10), left alone otherwise. */
 private static ResidentEntity body(ServerLevel l,CompoundTag t){return t.hasUUID("builder")&&l.getEntity(t.getUUID("builder")) instanceof ResidentEntity r?r:null;}
 private static boolean watched(ServerLevel l,BlockPos at){return !l.getPlayers(p->p.blockPosition().distSqr(at)<(double)Caravans.MATERIALIZE_RADIUS*Caravans.MATERIALIZE_RADIUS).isEmpty();}
 /** Lays the operations of the next column. False when the crew has to wait (an unloaded cell, the builder still walking). */
 static boolean build(ServerLevel l,SettlementData.Entry e,CompoundTag t){
  var ops=t.getList("ops",Tag.TAG_COMPOUND);int i=t.getInt("index");var village=e.settlement().id();
  if(i>=ops.size()){t.putInt("segment",t.getInt("segmentEnd"));t.put("ops",new ListTag());t.putInt("index",0);
   if(t.getBoolean("repairPass")){t.remove("repairPass");t.putString("state",RETURN);return false;}
   t.putString("state",t.getBoolean("refusedAfter")||t.getInt("segment")>=route(t).length?RETURN:SURVEY);if(t.getBoolean("refusedAfter"))t.putBoolean("blockedAfter",true);t.remove("refusedAfter");return false;}
  int col=ops.getCompound(i).getInt("col");var at=BlockPos.of(ops.getCompound(i).getLong("pos"));
  var ensure=TouchLoad.ensure(l,at);if(ensure!=TouchLoad.Touch.OK){t.putString("reason","missing_chunk");return false;}
  var npc=body(l,t);
  if(npc!=null&&l.hasChunkAt(at)&&watched(l,at)){
   t.putBoolean("materialized",true);npc.workStatus("on_trail");
   if(npc.blockPosition().distSqr(at)>16*16){var stand=Caravans.ground(l,at.above());npc.teleportTo(stand.getX()+.5,stand.getY(),stand.getZ()+.5);}
   if(npc.blockPosition().distSqr(at)>REACH*REACH){npc.getNavigation().moveTo(at.getX()+.5,at.getY()+1,at.getZ()+.5,.7);return false;}
  }else t.putBoolean("materialized",false);
  var cargo=t.getList("cargo",Tag.TAG_COMPOUND);var returns=t.getList("returns",Tag.TAG_COMPOUND);var blocks=l.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
  for(;i<ops.size()&&ops.getCompound(i).getInt("col")==col;i++){var o=ops.getCompound(i);var pos=BlockPos.of(o.getLong("pos"));var kind=o.getString("kind");
   if(TouchLoad.ensure(l,pos)!=TouchLoad.Touch.OK){t.putInt("index",i);t.put("cargo",cargo);t.putString("reason","missing_chunk");return false;}
   var id=Settlement.childId(t.getUUID("id"),"op/"+t.getInt("segment")+"/"+i);boolean recorded=WorldJournal.exists(l,id);
   var before=NbtUtils.readBlockState(blocks,o.getCompound("before"));var after=NbtUtils.readBlockState(blocks,o.getCompound("after"));
   var itemKey=o.getString("item");var item=itemKey.isEmpty()?Items.AIR:BuiltInRegistries.ITEM.get(new ResourceLocation(itemKey));
   if(kind.equals("repair")){var c=Roads.cell(l,pos);if(c!=null&&c.trail&&consume(cargo,item)){c.wear=0;c.samples=0;Roads.register(l,village,pos);t.putInt("laid",t.getInt("laid")+1);}continue;}
   if(kind.equals("keep")){if(Roads.tierOf(l.getBlockState(pos))>=0&&Roads.cell(l,pos)==null)Roads.registerTrail(l,village,pos);continue;}
   if(!recorded){
    if(!l.getBlockState(pos).equals(before)||protectedCell(l,e,pos)){t.putInt("skipped",t.getInt("skipped")+1);continue;}
    if(item!=Items.AIR&&count(cargo,item)==0){t.putInt("skipped",t.getInt("skipped")+1);continue;}
   }
   if(kind.equals("bore")||kind.equals("clear")){var loot=WorldJournal.harvest(l,id,pos,before,PICK);if(loot==null){t.putInt("skipped",t.getInt("skipped")+1);continue;}for(var s:loot)add(returns,s);}
   else{if(!WorldJournal.place(l,id,pos,before,after)){t.putInt("skipped",t.getInt("skipped")+1);continue;}
    if(item!=Items.AIR)consume(cargo,item);
    if(kind.equals("ceiling")||kind.equals("surface")){var d=before.getBlock().asItem();if(d!=Items.AIR&&!before.isAir())add(returns,new ItemStack(before.is(BlockTags.DIRT)?Items.DIRT:before.is(Blocks.STONE)?Items.COBBLESTONE:d));}
    if(kind.equals("surface")){Roads.registerTrail(l,village,pos);t.putInt("laid",t.getInt("laid")+1);}}
  }
  t.putInt("index",i);t.put("cargo",cargo);t.put("returns",returns);t.putInt("crew",col);if(!t.getBoolean("refusedAfter"))t.putString("reason","");return true;
 }
 /** The crew back at the hall: the spoil and any unused material go into the hall chest through the journal. True when nothing is left. */
 static boolean unload(ServerLevel l,SettlementData.Entry e,CompoundTag t){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return false;var pos=LogisticsRoutes.position(e,hall);
  var returns=t.getList("returns",Tag.TAG_COMPOUND);
  // At the end of a trail the material the crew still carries goes back too.
  if(t.getString("state").equals(RETURN)){for(var raw:t.getList("cargo",Tag.TAG_COMPOUND))add(returns,ItemStack.of((CompoundTag)raw));t.put("cargo",new ListTag());}
  while(!returns.isEmpty()){var s=ItemStack.of(returns.getCompound(0));var id=Settlement.childId(t.getUUID("id"),"return/"+t.getInt("deposits"));
   ItemStack part=WorldJournal.exists(l,id)?s:s.copy();
   boolean ok=WorldJournal.deposit(l,id,pos,part);if(!ok){part=s.copyWithCount(1);ok=WorldJournal.deposit(l,id,pos,part);}
   if(!ok){t.put("returns",returns);t.putString("reason","hall_full");return false;}
   s.shrink(part.getCount());if(s.isEmpty())returns.remove(0);else returns.set(0,s.save(new CompoundTag()));t.putInt("deposits",t.getInt("deposits")+1);t.put("returns",returns);save(l,e.settlement().id(),t);}
  t.put("returns",returns);return true;
 }
 /** The builder is free again; a body left far out is brought home when nobody sees it. */
 private static void home(ServerLevel l,SettlementData.Entry e,CompoundTag t){var npc=body(l,t);t.putBoolean("materialized",false);
  if(npc!=null&&npc.blockPosition().distSqr(e.center())>64*64&&!watched(l,npc.blockPosition())){var g=Caravans.gate(l,e,npc.blockPosition());npc.teleportTo(g.getX()+.5,g.getY(),g.getZ()+.5);}
  if(npc!=null)npc.workStatus("idle");}
 /** Trail material still missing at the hall; porters and trade see it as demand. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,Settlement.Building hall){
  var t=record(l,e.settlement().id());if(t==null||!t.getString("state").equals(SUPPLY)||hall==null)return List.of();var chest=LogisticsRoutes.chest(l,e,hall);if(chest==null)return List.of();
  var out=new ArrayList<Workshops.Want>();for(var n:needs(t).entrySet()){int missing=n.getValue()-HallReserve.count(l,e,hall,chest,s->s.is(n.getKey()));if(missing>0)out.add(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(n.getKey()),missing,hall.id()));}
  return out;
 }
 // ---- caravans -------------------------------------------------------------------------------------
 /** Mean surface bonus along the straight gate-to-gate line of a caravan: road cells of either village within {@link #BAND} blocks of the line,
  *  counted once per departure (C10 d). A detour through waypoints therefore counts only where it meets that line. */
 public static double routeBonus(ServerLevel l,BlockPos from,BlockPos to,UUID... villages){
  var best=new HashMap<Long,Double>();
  for(var v:villages)if(v!=null)for(var x:Roads.cells(l,v)){var p=x.getKey();best.merge(BlockPos.asLong(p.getX(),0,p.getZ()),Roads.bonus(x.getValue()),Math::max);}
  if(best.isEmpty())return 0;
  int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ(),n=Math.max(Math.abs(dx),Math.abs(dz));if(n==0)return 0;double sum=0;
  for(int k=0;k<=n;k++){int x=from.getX()+(int)Math.round(dx*(double)k/n),z=from.getZ()+(int)Math.round(dz*(double)k/n);double b=0;
   for(int ox=-BAND;ox<=BAND;ox++)for(int oz=-BAND;oz<=BAND;oz++){var v=best.get(BlockPos.asLong(x+ox,0,z+oz));if(v!=null&&v>b)b=v;}
   sum+=b;}
  return sum/(n+1);
 }
 // ---- the view -------------------------------------------------------------------------------------
 /** The trail part of the atlas page: the neighbours a trail may go to and the state of the village's own trail. */
 public static CompoundTag view(ServerLevel l,SettlementData.Entry e){
  var out=new CompoundTag();var list=new ListTag();
  for(var d:SettlementData.get(l.getServer()).entries()){if(d==e||d.settlement().id().equals(e.settlement().id())||!d.dimension().equals(e.dimension()))continue;
   int dist=(int)Math.sqrt(Math.pow(d.center().getX()-e.center().getX(),2)+Math.pow(d.center().getZ()-e.center().getZ(),2));if(dist>MAX_DISTANCE+2*Caravans.GATE+48)continue;
   var row=new CompoundTag();row.putUUID("id",d.settlement().id());row.putString("name",d.settlement().name());row.putInt("distance",dist);row.putLong("center",d.center().asLong());
   row.putString("standing",Relations.standing(Relations.score(l.getServer(),e.settlement().id(),d.settlement().id())));list.add(row);}
  var done=ResearchKnobs.done(l,e);out.put("neighbours",list);out.putBoolean("research",done.contains("roads.2"));out.putBoolean("bridges",done.contains("roads.5"));
  // AD-159 VI: with Military VI the atlas sends the army to take the neighbour picked here (Warfare order 5).
  out.putBoolean("capture",done.contains(Army.CAPTURE));
  out.putBoolean("gravel",done.contains("roads.1"));out.putBoolean("lamps",done.contains("roads.3"));
  var t=record(l,e.settlement().id());
  if(t!=null){var s=new CompoundTag();s.putString("state",t.getString("state"));s.putString("reason",t.getString("reason"));s.putInt("length",route(t).length);
   s.putInt("done",t.getString("state").equals(COMPLETE)?route(t).length:t.getInt("segment"));s.putInt("laid",t.getInt("laid"));s.putInt("skipped",t.getInt("skipped"));
   if(t.contains("blockedAt"))s.putLong("blockedAt",t.getLong("blockedAt"));s.putUUID("target",t.getUUID("target"));out.put("trail",s);}
  return out;
 }
 private static Component text(String key,Object... args){return Component.translatable("research.villageastra.fact.trail."+key,args);}
 /** Card lines of the trail rungs (Roads II and V) and the cross reference on Caravans II. */
 public static void facts(String id,List<Component> out){
  switch(id){
   case "roads.2"->{out.add(ResearchEffects.tagged(text("order",MAX_DISTANCE,SEGMENT),"new"));
    out.add(ResearchEffects.tagged(text("surface",Roads.TIERS.get(0).block().getName(),Roads.TIERS.get(1).block().getName()),"new"));
    out.add(ResearchEffects.tagged(text("caravan",String.format(Locale.ROOT,"%.2f",1+Roads.TIERS.get(1).bonus())),"existing"));
    out.add(text("refused"));}
   case "roads.5"->{out.add(ResearchEffects.tagged(text("bridge",SPAN),"new"));out.add(ResearchEffects.tagged(text("tunnel",TUNNEL,LAMP_EVERY),"new"));out.add(text("dry"));}
   case "caravans.2"->out.add(text("see_roads"));
   default->{}
  }
 }
}
