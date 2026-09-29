package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
/** AD-038: registered road cells with a surface tier, real traffic wear and speed for every living user; builder projects that pave, repair and light roads with real materials. */
public final class Roads {
 public record Tier(Block block,Item item,double bonus,int samplesPerWear){}
 private static final JsonObject ROOT=root();
 public static final List<Tier> TIERS=tiers();
 public static final int REPAIR_WEAR=ROOT.get("repair_wear").getAsInt(),LIGHT_TARGET=ROOT.get("light_target").getAsInt(),LAMP_SPACING=ROOT.get("lamp_spacing").getAsInt(),CARRY=ROOT.get("carry").getAsInt(),MAX_WEAR=100;
 public static final int GATE_SPACING=ROOT.get("gate_spacing").getAsInt(),POST_SPACING=ROOT.get("post_spacing").getAsInt(),PATROL_RADIUS=ROOT.get("patrol_radius").getAsInt();
 /** AD-123: hostile mobs take at most this surface bonus (the pre-rework best, stone bricks ×1.35); residents, players and animals get the full surface. */
 public static final double HOSTILE_CAP=ROOT.has("hostile_bonus_cap")?ROOT.get("hostile_bonus_cap").getAsDouble():1;
 public static final UUID SPEED_ID=UUID.fromString("5b0c7a34-8f0e-4c1e-9a55-3f6d2a1b7e01");
 public static final class Cell{public final UUID village;public int tier;public long traffic;public int samples,wear;/** AD-123: a cell of an inter-village trail, repaired by the trail crew and never by village upkeep. */public boolean trail;Cell(UUID village,int tier){this.village=village;this.tier=tier;}}
 private static final Map<UUID,CompoundTag> INFRA=new HashMap<>();
 private static final Map<String,Map<Long,Cell>> INDEX=new HashMap<>();private static final Set<UUID> DIRTY=new HashSet<>();private static final Map<LivingEntity,Float> WALKED=new WeakHashMap<>();private static MinecraftServer loaded;
 private Roads(){}
 private static JsonObject root(){try(var s=Roads.class.getResourceAsStream("/data/villageastra/balance/roads.json")){if(s==null)throw new IllegalStateException("Missing roads balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static List<Tier> tiers(){var out=new ArrayList<Tier>();for(var raw:ROOT.getAsJsonArray("tiers")){var o=raw.getAsJsonObject();out.add(new Tier(BuiltInRegistries.BLOCK.get(new ResourceLocation(o.get("block").getAsString())),BuiltInRegistries.ITEM.get(new ResourceLocation(o.get("item").getAsString())),o.get("speed_bonus").getAsDouble(),o.get("samples_per_wear").getAsInt()));}
  for(int i=1;i<out.size();i++)if(out.get(i).bonus<=out.get(i-1).bonus||out.get(i).samplesPerWear<out.get(i-1).samplesPerWear)throw new IllegalStateException("Road tiers must improve");return List.copyOf(out);}
 public static int tierOf(BlockState s){for(int i=0;i<TIERS.size();i++)if(s.is(TIERS.get(i).block))return i;return -1;}
 // ---- registry --------------------------------------------------------------------------------
 public static Path cellsPath(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-roads/"+village+".bin");}
 public static Path projectPath(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-roads/"+village+"-project.bin");}
 public static void clear(){INDEX.clear();INFRA.clear();DIRTY.clear();WALKED.clear();loaded=null;}
 private static synchronized void ensure(MinecraftServer server){
  if(loaded==server)return;INDEX.clear();INFRA.clear();DIRTY.clear();loaded=server;
  for(var e:SettlementData.get(server).entries()){var p=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-roads/"+e.settlement().id()+".bin");if(!Files.exists(p))continue;
   var record=NbtRecord.read(p);var infra=new CompoundTag();infra.putLongArray("fences",record.getLongArray("fences"));infra.putLongArray("posts",record.getLongArray("posts"));infra.putLongArray("gates",record.getLongArray("gates"));INFRA.put(e.settlement().id(),infra);
   var map=INDEX.computeIfAbsent(e.dimension(),d->new HashMap<>());for(var raw:record.getList("cells",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;int tier=t.getInt("tier");if(tier<0||tier>=TIERS.size())throw new IllegalStateException("Invalid road tier");var c=new Cell(e.settlement().id(),tier);c.traffic=t.getLong("traffic");c.samples=t.getInt("samples");c.wear=Math.min(MAX_WEAR,Math.max(0,t.getInt("wear")));c.trail=t.getBoolean("trail");map.put(t.getLong("pos"),c);}}
 }
 private static Map<Long,Cell> cells(ServerLevel l){ensure(l.getServer());return INDEX.computeIfAbsent(l.dimension().location().toString(),d->new HashMap<>());}
 public static Cell cell(ServerLevel l,BlockPos pos){return cells(l).get(pos.asLong());}
 public static List<Map.Entry<BlockPos,Cell>> cells(ServerLevel l,UUID village){var out=new ArrayList<Map.Entry<BlockPos,Cell>>();for(var e:cells(l).entrySet())if(e.getValue().village.equals(village))out.add(Map.entry(BlockPos.of(e.getKey()),e.getValue()));out.sort(Comparator.comparingLong(x->x.getKey().asLong()));return out;}
 /** AD-123: a cell laid by the trail crew; trail cells may stand outside the atlas area and are skipped by village upkeep. */
 public static boolean registerTrail(ServerLevel l,UUID village,BlockPos pos){if(!register(l,village,pos))return false;cell(l,pos).trail=true;return true;}
 /** Registers or re-tiers a cell only when the world really has that surface block. */
 public static boolean register(ServerLevel l,UUID village,BlockPos pos){int tier=tierOf(l.getBlockState(pos));if(tier<0)return false;var map=cells(l);var c=map.get(pos.asLong());
  if(c!=null&&!c.village.equals(village))return false;if(c==null){c=new Cell(village,tier);map.put(pos.asLong(),c);}else if(c.tier!=tier){c.tier=tier;c.wear=0;c.samples=0;}DIRTY.add(village);return true;}
 public static void flush(MinecraftServer server){
  if(loaded!=server||DIRTY.isEmpty())return;
  for(var village:List.copyOf(DIRTY)){var e=SettlementData.get(server).entry(village);if(e==null)continue;var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(e.dimension())));if(l==null)continue;
   var list=new ListTag();for(var x:cells(l,village)){var t=new CompoundTag();t.putLong("pos",x.getKey().asLong());t.putInt("tier",x.getValue().tier);t.putLong("traffic",x.getValue().traffic);t.putInt("samples",x.getValue().samples);t.putInt("wear",x.getValue().wear);if(x.getValue().trail)t.putBoolean("trail",true);list.add(t);}
   var tag=new CompoundTag();tag.putInt("schema",1);tag.put("cells",list);var infra=INFRA.get(village);
   tag.putLongArray("fences",infra==null?new long[0]:infra.getLongArray("fences"));tag.putLongArray("posts",infra==null?new long[0]:infra.getLongArray("posts"));tag.putLongArray("gates",infra==null?new long[0]:infra.getLongArray("gates"));NbtRecord.write(cellsPath(l,village),tag);}
  DIRTY.clear();
 }
 // ---- movement and wear ---------------------------------------------------------------------------
 public static double bonus(Cell c){return c==null?0:TIERS.get(c.tier).bonus*(1-c.wear/200.0);}
 /** Applies the surface speed to any living user and counts real movement as traffic. */
 public static void living(LivingEntity entity){
  if(!(entity.level() instanceof ServerLevel l)||entity.isSpectator()||entity.isPassenger())return;var attribute=entity.getAttribute(Attributes.MOVEMENT_SPEED);if(attribute==null)return;
  // The supporting block is taken from the current position; Entity#getOnPos may still hold the block of the previous movement step.
  var below=BlockPos.containing(entity.getX(),entity.getY()-.2,entity.getZ());var c=entity.onGround()?cell(l,below):null;
  if(c!=null&&tierOf(l.getBlockState(below))!=c.tier)c=null;
  double bonus=bonus(c);if(entity instanceof net.minecraft.world.entity.monster.Enemy)bonus=Math.min(bonus,HOSTILE_CAP);var current=attribute.getModifier(SPEED_ID);
  if(bonus<=0){if(current!=null)attribute.removeModifier(SPEED_ID);}
  else if(current==null||Math.abs(current.getAmount()-bonus)>1e-4){if(current!=null)attribute.removeModifier(SPEED_ID);attribute.addTransientModifier(new AttributeModifier(SPEED_ID,"Astra road surface",bonus,AttributeModifier.Operation.MULTIPLY_BASE));}
  Float before=WALKED.put(entity,entity.walkDist);
  if(c!=null&&before!=null&&entity.walkDist-before>.05F)traffic(c,1);
 }
 public static void traffic(Cell c,int samples){for(int i=0;i<samples;i++){c.traffic++;if(++c.samples>=TIERS.get(c.tier).samplesPerWear){c.samples=0;if(c.wear<MAX_WEAR)c.wear++;}}DIRTY.add(c.village);}
 /** Corridor fences and guard posts the settlement really built. */
 public static long[] fences(MinecraftServer s,UUID village){ensure(s);var t=INFRA.get(village);return t==null?new long[0]:t.getLongArray("fences");}
 public static long[] posts(MinecraftServer s,UUID village){ensure(s);var t=INFRA.get(village);return t==null?new long[0]:t.getLongArray("posts");}
 /** The corridor fences that are gates (also listed in fences): maintenance puts a gate back as a gate. Older saves have none. */
 public static long[] gates(MinecraftServer s,UUID village){ensure(s);var t=INFRA.get(village);return t==null?new long[0]:t.getLongArray("gates");}
 private static void remember(MinecraftServer s,UUID village,String key,BlockPos pos){
  ensure(s);var t=INFRA.computeIfAbsent(village,id->new CompoundTag());var old=t.getLongArray(key);for(long v:old)if(v==pos.asLong())return;
  var next=java.util.Arrays.copyOf(old,old.length+1);next[old.length]=pos.asLong();t.putLongArray(key,next);DIRTY.add(village);
 }
 public static boolean lit(ServerLevel l,BlockPos cell){return l.getBrightness(LightLayer.BLOCK,cell.above())>=LIGHT_TARGET;}
 /** Initial and hand-made dirt paths of a settlement become registered tier-0 cells once their chunks are loaded. */
 public static int discover(ServerLevel l,SettlementData.Entry e,int radius){
  int added=0;var c=e.center();for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){var column=c.offset(x,0,z);if(!l.hasChunkAt(column))return -1;}
  for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){int wx=c.getX()+x,wz=c.getZ()+z,y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;var pos=new BlockPos(wx,y,wz);if(l.getBlockState(pos).is(Blocks.DIRT_PATH)&&cell(l,pos)==null&&register(l,e.settlement().id(),pos))added++;}
  return added;
 }
 // ---- projects ------------------------------------------------------------------------------------
 private static CompoundTag op(String kind,BlockPos pos,BlockState before,BlockState after,Item item){var t=new CompoundTag();t.putString("kind",kind);t.putLong("pos",pos.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));t.putString("item",BuiltInRegistries.ITEM.getKey(item).toString());return t;}
 static ItemStack drop(BlockState s){if(s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT_PATH)||s.is(Blocks.PODZOL)||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.ROOTED_DIRT))return new ItemStack(Items.DIRT);var item=s.getBlock().asItem();return item==Items.AIR?ItemStack.EMPTY:new ItemStack(item);}
 /** Plans paving of the marked cells to a surface tier and lantern posts beside dark stretches of the centre line. Nothing changes in the world. */
 public static CompoundTag plan(ServerLevel l,SettlementData.Entry e,List<BlockPos> line,Map<BlockPos,BlockState> cells,int surface,boolean light){return plan(l,e,line,cells,surface,light,false);}
 /** SAFE-001: a fenced corridor gets posts on both sides, gates for passage and a guard post with light every stretch. */
 public static CompoundTag plan(ServerLevel l,SettlementData.Entry e,List<BlockPos> line,Map<BlockPos,BlockState> cells,int surface,boolean light,boolean fence){
  if(surface<0||surface>=TIERS.size())throw new IllegalArgumentException("Unknown surface");var ops=new ListTag();var skipped=new ListTag();var cost=new CompoundTag();var after=TIERS.get(surface).block.defaultBlockState();
  // AD-057: lamps and fences stand one block outside the paved width, whatever width the road was given.
  int half=0;for(var cell:cells.keySet()){int nearest=Integer.MAX_VALUE;for(var at:line)nearest=Math.min(nearest,Math.max(Math.abs(cell.getX()-at.getX()),Math.abs(cell.getZ()-at.getZ())));half=Math.max(half,nearest);}
  final int edge=half+1;
  for(var pos:cells.keySet()){var before=l.getBlockState(pos);if(before.is(after.getBlock()))continue;ops.add(op("surface",pos,before,after,TIERS.get(surface).item));cost.putInt(key(TIERS.get(surface).item),cost.getInt(key(TIERS.get(surface).item))+1);}
  if(light)for(int i=0;i<line.size();i+=LAMP_SPACING){var at=line.get(i);var next=line.get(Math.min(line.size()-1,i+1));var prev=line.get(Math.max(0,i-1));int dx=Integer.signum(next.getX()-prev.getX()),dz=Integer.signum(next.getZ()-prev.getZ());
   int px=dz,pz=-dx;if(px==0&&pz==0)px=1;if(px!=0&&pz!=0)pz=0;int side=(i/LAMP_SPACING)%2==0?edge:-edge;
   int wx=at.getX()+px*side,wz=at.getZ()+pz*side;var column=new BlockPos(wx,0,wz);if(!l.hasChunkAt(column)){skipped.add(StringTag.valueOf("unloaded"));continue;}
   var centre=cells.keySet().stream().filter(c->c.getX()==at.getX()&&c.getZ()==at.getZ()).findFirst().orElse(null);if(centre!=null&&lit(l,centre)){skipped.add(StringTag.valueOf("lit"));continue;}
   // The post stands on the ground level of the adjacent road cell, not on whatever the heightmap sees above it.
   int y=centre!=null?centre.getY():l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;var ground=new BlockPos(wx,y,wz);
   String reason=cells.containsKey(ground)?"road":!l.getBlockState(ground).isFaceSturdy(l,ground,net.minecraft.core.Direction.UP)?"ground":!l.getBlockState(ground.above()).isAir()||!l.getBlockState(ground.above(2)).isAir()?"occupied":org.villageastra.server.MayorSurvey.underBuilding(l,ground.above())||org.villageastra.server.MayorSurvey.underBuilding(l,ground.above(2))?"protected":"";
   if(!reason.isEmpty()){skipped.add(StringTag.valueOf(reason));continue;}
   ops.add(op("post",ground.above(),Blocks.AIR.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));cost.putInt(key(Items.OAK_FENCE),cost.getInt(key(Items.OAK_FENCE))+1);
   ops.add(op("lamp",ground.above(2),Blocks.AIR.defaultBlockState(),Blocks.LANTERN.defaultBlockState(),Items.LANTERN));cost.putInt(key(Items.LANTERN),cost.getInt(key(Items.LANTERN))+1);}
  if(fence)for(int i=0;i<line.size();i++){
   var at=line.get(i);var next=line.get(Math.min(line.size()-1,i+1));var prev=line.get(Math.max(0,i-1));
   int dx=Integer.signum(next.getX()-prev.getX()),dz=Integer.signum(next.getZ()-prev.getZ());int px=dz,pz=-dx;if(px==0&&pz==0)px=1;if(px!=0&&pz!=0)pz=0;
   boolean gate=i%GATE_SPACING==GATE_SPACING/2;
   for(int side=-1;side<=1;side+=2){
    int wx=at.getX()+px*side*edge,wz=at.getZ()+pz*side*edge;var centre=cells.keySet().stream().filter(c->c.getX()==at.getX()&&c.getZ()==at.getZ()).findFirst().orElse(null);
    int y=centre!=null?centre.getY():l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;var ground=new BlockPos(wx,y,wz);
    if(cells.containsKey(ground)||!l.hasChunkAt(ground)||!l.getBlockState(ground).isFaceSturdy(l,ground,net.minecraft.core.Direction.UP)||!l.getBlockState(ground.above()).isAir()||org.villageastra.server.MayorSurvey.underBuilding(l,ground.above()))continue;
    if(gate){ops.add(op("gate",ground.above(),Blocks.AIR.defaultBlockState(),Blocks.OAK_FENCE_GATE.defaultBlockState(),Items.OAK_FENCE_GATE));cost.putInt(key(Items.OAK_FENCE_GATE),cost.getInt(key(Items.OAK_FENCE_GATE))+1);}
    else{ops.add(op("fence",ground.above(),Blocks.AIR.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));cost.putInt(key(Items.OAK_FENCE),cost.getInt(key(Items.OAK_FENCE))+1);}
    if(i%POST_SPACING==POST_SPACING/2&&side==1){
     var lamp=ground.above(2);
     if(l.getBlockState(lamp).isAir()&&!org.villageastra.server.MayorSurvey.underBuilding(l,lamp)){ops.add(op("post",lamp,Blocks.AIR.defaultBlockState(),Blocks.LANTERN.defaultBlockState(),Items.LANTERN));cost.putInt(key(Items.LANTERN),cost.getInt(key(Items.LANTERN))+1);}}
   }}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","build");t.putInt("surface",surface);t.putBoolean("light",light);t.putBoolean("fence",fence);t.put("ops",ops);t.put("cost",cost);t.putInt("index",0);t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putBoolean("complete",ops.isEmpty());t.put("lampSkips",skipped);
  var registered=new ListTag();for(var pos:cells.keySet())registered.add(LongTag.valueOf(pos.asLong()));t.put("cells",registered);return t;
 }
 private static String key(Item item){return BuiltInRegistries.ITEM.getKey(item).toString();}
 /** AD-061: lantern posts on the given columns, standing on the ground beside the paving; a column that cannot take a post is skipped. */
 public static void addLamps(ServerLevel l,CompoundTag project,List<BlockPos> columns,Set<BlockPos> paved){
  var ops=project.getList("ops",Tag.TAG_COMPOUND);var cost=project.getCompound("cost");var seen=new HashSet<Long>();
  for(var column:columns){
   if(!seen.add(BlockPos.asLong(column.getX(),0,column.getZ()))||!l.hasChunkAt(column))continue;
   int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())-1;var ground=new BlockPos(column.getX(),y,column.getZ());
   if(paved.contains(ground)||!l.getBlockState(ground).isFaceSturdy(l,ground,net.minecraft.core.Direction.UP)||!l.getBlockState(ground.above()).isAir()||!l.getBlockState(ground.above(2)).isAir()
    ||org.villageastra.server.MayorSurvey.underBuilding(l,ground.above()))continue;
   ops.add(op("post",ground.above(),Blocks.AIR.defaultBlockState(),Blocks.OAK_FENCE.defaultBlockState(),Items.OAK_FENCE));cost.putInt(key(Items.OAK_FENCE),cost.getInt(key(Items.OAK_FENCE))+1);
   ops.add(op("lamp",ground.above(2),Blocks.AIR.defaultBlockState(),Blocks.LANTERN.defaultBlockState(),Items.LANTERN));cost.putInt(key(Items.LANTERN),cost.getInt(key(Items.LANTERN))+1);
  }
  project.put("ops",ops);project.put("cost",cost);project.putBoolean("complete",ops.isEmpty());
 }
 /** AD-057: a clearing ordered on the map — the builders take the marked blocks away top first and bring what they yield to the hall; no material is spent. */
 public static CompoundTag clearing(ServerLevel l,SettlementData.Entry e,List<BlockPos> cells){
  var ops=new ListTag();var air=Blocks.AIR.defaultBlockState();
  for(var pos:cells){var t=new CompoundTag();t.putString("kind","clear");t.putLong("pos",pos.asLong());t.put("before",NbtUtils.writeBlockState(l.getBlockState(pos)));t.put("after",NbtUtils.writeBlockState(air));t.putString("item","");ops.add(t);}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","clearing");t.put("ops",ops);t.put("cost",new CompoundTag());t.putInt("index",0);
  t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putBoolean("complete",ops.isEmpty());t.put("cells",new ListTag());return t;
 }
 public static CompoundTag project(ServerLevel l,UUID village){var p=projectPath(l,village);return Files.exists(p)?NbtRecord.read(p):null;}
 public static boolean active(ServerLevel l,UUID village){var t=project(l,village);return t!=null&&!t.getBoolean("complete");}
 public static void save(ServerLevel l,UUID village,CompoundTag project){NbtRecord.write(projectPath(l,village),project);}
 /** One active road project per settlement; a finished one may be replaced. */
 public static boolean order(ServerLevel l,SettlementData.Entry e,CompoundTag project){if(active(l,e.settlement().id())||Sieges.besieged(l.getServer(),e.settlement().id()))return false;save(l,e.settlement().id(),project);return true;}
 /** Repair project for the most worn registered cells when nothing else is queued. Repairs spend one surface item and never add or remove blocks. */
 public static CompoundTag maintenance(ServerLevel l,SettlementData.Entry e){
  var worn=cells(l,e.settlement().id()).stream().filter(x->!x.getValue().trail&&x.getValue().wear>=REPAIR_WEAR&&l.hasChunkAt(x.getKey())&&tierOf(l.getBlockState(x.getKey()))==x.getValue().tier).sorted((a,b)->Integer.compare(b.getValue().wear,a.getValue().wear)).limit(16).toList();
  var broken=new ArrayList<BlockPos>();
  for(long raw:fences(l.getServer(),e.settlement().id())){var pos=BlockPos.of(raw);if(l.hasChunkAt(pos)&&l.getBlockState(pos).isAir()&&!OwnershipEvents.disallowedPlacement(l,pos))broken.add(pos);}
  if(worn.isEmpty()&&broken.isEmpty())return null;var ops=new ListTag();var cost=new CompoundTag();
  var gateCells=new HashSet<Long>();for(long raw:gates(l.getServer(),e.settlement().id()))gateCells.add(raw);
  for(var pos:broken.stream().limit(16).toList()){boolean gate=gateCells.contains(pos.asLong());var item=gate?Items.OAK_FENCE_GATE:Items.OAK_FENCE;
   ops.add(op(gate?"gate":"fence",pos,Blocks.AIR.defaultBlockState(),(gate?Blocks.OAK_FENCE_GATE:Blocks.OAK_FENCE).defaultBlockState(),item));cost.putInt(key(item),cost.getInt(key(item))+1);}
  for(var x:worn){var state=l.getBlockState(x.getKey());var item=TIERS.get(x.getValue().tier).item;ops.add(op("repair",x.getKey(),state,state,item));cost.putInt(key(item),cost.getInt(key(item))+1);}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind",ops.stream().allMatch(x->((CompoundTag)x).getString("kind").equals("repair"))?"repair":"build");t.put("ops",ops);t.put("cost",cost);t.putInt("index",0);t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);t.putBoolean("complete",false);t.put("cells",new ListTag());
  // A round with a broken fence is a "build"; the flag keeps it upkeep that never holds the builders when the hall lacks a piece (RoadWorkGoal).
  t.putBoolean("maintenance",true);return t;
 }
 private static int count(ListTag stacks,Item item){int n=0;for(var raw:stacks){var s=ItemStack.of((CompoundTag)raw);if(s.is(item))n+=s.getCount();}return n;}
 private static void add(ListTag stacks,ItemStack stack){for(int i=0;i<stacks.size();i++){var s=ItemStack.of(stacks.getCompound(i));if(ItemStack.isSameItemSameTags(s,stack)&&s.getCount()+stack.getCount()<=s.getMaxStackSize()){s.grow(stack.getCount());stacks.set(i,s.save(new CompoundTag()));return;}}stacks.add(stack.save(new CompoundTag()));}
 private static boolean consume(ListTag stacks,Item item){for(int i=0;i<stacks.size();i++){var s=ItemStack.of(stacks.getCompound(i));if(s.is(item)){s.shrink(1);if(s.isEmpty())stacks.remove(i);else stacks.set(i,s.save(new CompoundTag()));return true;}}return false;}
 /** Items still needed by the remaining operations beyond what the builder already carries. */
 public static Map<Item,Integer> needs(CompoundTag project){var out=new LinkedHashMap<Item,Integer>();if(project==null||project.getBoolean("complete"))return out;var ops=project.getList("ops",Tag.TAG_COMPOUND);
  // Operations that only remove a block (earthworks, clearing) carry no item and buy nothing.
  for(int i=project.getInt("index");i<ops.size();i++){var key=ops.getCompound(i).getString("item");if(key.isEmpty())continue;var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));if(item==Items.AIR)continue;out.merge(item,1,Integer::sum);}
  var cargo=project.getList("cargo",Tag.TAG_COMPOUND);out.replaceAll((item,n)->n-count(cargo,item));out.values().removeIf(n->n<=0);return out;}
 /** AD-094: what the next operations take, in their order, up to one load — so a builder carries the brick and the crenellation the
  *  next columns need, not a load of one kind while the other waits at the hall. Items already carried count for the first ops. */
 public static Map<Item,Integer> upcoming(CompoundTag project,int load){
  var out=new LinkedHashMap<Item,Integer>();if(project==null||project.getBoolean("complete"))return out;
  var have=new HashMap<Item,Integer>();int total=0;
  for(var raw:project.getList("cargo",Tag.TAG_COMPOUND)){var st=ItemStack.of((CompoundTag)raw);have.merge(st.getItem(),st.getCount(),Integer::sum);total+=st.getCount();}
  var ops=project.getList("ops",Tag.TAG_COMPOUND);
  for(int i=project.getInt("index");i<ops.size()&&total<load;i++){var key=ops.getCompound(i).getString("item");if(key.isEmpty())continue;
   var item=BuiltInRegistries.ITEM.get(new ResourceLocation(key));if(item==Items.AIR)continue;
   int carried=have.getOrDefault(item,0);if(carried>0){have.put(item,carried-1);continue;}
   out.merge(item,1,Integer::sum);total++;}
  return out;
 }
 /** At the hall: unloads returned blocks, then withdraws materials for the next operations (up to CARRY items), each transfer through the journal. */
 public static int load(ServerLevel l,SettlementData.Entry e,Settlement.Building hall,CompoundTag project){
  var chest=LogisticsRoutes.chest(l,e,hall);if(chest==null)return 0;var pos=LogisticsRoutes.position(e,hall);var id=project.getUUID("id");var returns=project.getList("returns",Tag.TAG_COMPOUND);
  while(!returns.isEmpty()){var s=ItemStack.of(returns.getCompound(0));var single=s.copyWithCount(1);if(!WorldJournal.deposit(l,Settlement.childId(id,"return/"+project.getInt("deposits")),pos,single))break;s.shrink(1);if(s.isEmpty())returns.remove(0);else returns.set(0,s.save(new CompoundTag()));project.putInt("deposits",project.getInt("deposits")+1);save(l,e.settlement().id(),project);}
  int moved=0;var cargo=project.getList("cargo",Tag.TAG_COMPOUND);int carried=0;for(var raw:cargo)carried+=ItemStack.of((CompoundTag)raw).getCount();
  // AD-153: the builder's bag (Construction II) carries three times the plain load.
  int carry=CARRY*ResearchKnobs.bag(l,e);
  for(var need:upcoming(project,carry).entrySet()){if(need.getKey()==Items.AIR)continue;int want=Math.min(need.getValue(),carry-carried);if(want<=0)break;
   var takeId=Settlement.childId(id,"take/"+project.getInt("withdrawals"));var got=WorldJournal.recoverAmount(l,takeId);
   if(got.isEmpty()&&!WorldJournal.exists(l,takeId))for(int slot=0;slot<chest.getContainerSize();slot++){var s=chest.getItem(slot);if(s.is(need.getKey())&&Trade.plain(s)){got=WorldJournal.takeAmount(l,takeId,pos,slot,s.copy(),Math.min(want,s.getCount()));break;}}
   if(got.isEmpty())continue;add(cargo,got);project.put("cargo",cargo);project.putInt("withdrawals",project.getInt("withdrawals")+1);save(l,e.settlement().id(),project);moved+=got.getCount();carried+=got.getCount();}
  return moved;
 }
 public static CompoundTag current(CompoundTag project){var ops=project.getList("ops",Tag.TAG_COMPOUND);int i=project.getInt("index");return i<ops.size()?ops.getCompound(i):null;}
 /** Applies the current operation exactly once. Returns "done", "skipped", "missing" (no carried item) or "complete". */
 public static String apply(ServerLevel l,SettlementData.Entry e,CompoundTag project){
  var op=current(project);var village=e.settlement().id();if(op==null){if(project.getList("returns",Tag.TAG_COMPOUND).isEmpty()){finish(l,e,project);return "complete";}return "unload";}
  var pos=BlockPos.of(op.getLong("pos"));var id=Settlement.childId(project.getUUID("id"),"op/"+project.getInt("index"));boolean free=op.getString("item").isEmpty();var item=free?Items.AIR:BuiltInRegistries.ITEM.get(new ResourceLocation(op.getString("item")));
  var before=NbtUtils.readBlockState(l.holderLookup(net.minecraft.core.registries.Registries.BLOCK),op.getCompound("before"));var after=NbtUtils.readBlockState(l.holderLookup(net.minecraft.core.registries.Registries.BLOCK),op.getCompound("after"));
  var cargo=project.getList("cargo",Tag.TAG_COMPOUND);var returns=project.getList("returns",Tag.TAG_COMPOUND);boolean repair=op.getString("kind").equals("repair");
  boolean recorded=WorldJournal.exists(l,id);
  if(!recorded){
   if(!l.hasChunkAt(pos))return "missing";
   // AD-127 (CF3): a loose operation (clearing for a wall, taking an old ring down) only needs the same block there, whatever its state.
   if(op.getBoolean("loose")&&l.getBlockState(pos).getBlock()==before.getBlock())before=l.getBlockState(pos);
   if(!l.getBlockState(pos).equals(before)){project.putInt("index",project.getInt("index")+1);project.putInt("conflicts",project.getInt("conflicts")+1);save(l,village,project);return "skipped";}
   if(!free&&count(cargo,item)==0)return "missing";
   if(repair){if(cell(l,pos)==null){project.putInt("index",project.getInt("index")+1);save(l,village,project);return "skipped";}}
   // A repair changes no block: the single project record write below (cargo, index) is its one-time commit; a lost wear reset only costs a later repair.
   else if(!WorldJournal.place(l,id,pos,before,after))return "missing";
  }
  if(!free)consume(cargo,item);project.put("cargo",cargo);
  if(!repair){var d=drop(before);if(!d.isEmpty())add(returns,d);project.put("returns",returns);
   var kind=op.getString("kind");if(kind.equals("surface"))register(l,village,pos);
   else if(kind.equals("fence")||kind.equals("gate")){remember(l.getServer(),village,"fences",pos);if(kind.equals("gate"))remember(l.getServer(),village,"gates",pos);}
   else if(kind.equals("post"))remember(l.getServer(),village,"posts",pos);}
  else{var c=cell(l,pos);if(c!=null){c.wear=0;c.samples=0;DIRTY.add(village);}}
  project.putInt("index",project.getInt("index")+1);save(l,village,project);return "done";
 }
 private static void finish(ServerLevel l,SettlementData.Entry e,CompoundTag project){for(var raw:project.getList("cells",Tag.TAG_LONG))register(l,e.settlement().id(),BlockPos.of(((LongTag)raw).getAsLong()));project.putBoolean("complete",true);save(l,e.settlement().id(),project);}
 /** Road material still missing at the hall for the active project; porters, workshops and trade see it as demand. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,Settlement.Building hall){
  var project=project(l,e.settlement().id());var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(project==null||chest==null)return List.of();var out=new ArrayList<Workshops.Want>();
  for(var need:needs(project).entrySet()){int missing=need.getValue()-HallReserve.count(l,e,hall,chest,s->s.is(need.getKey()));if(missing>0)out.add(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(need.getKey()),missing,hall.id()));}
  return out;
 }
 /** Lighting summary of registered cells in loaded chunks: {lit, dark}. */
 public static int[] light(ServerLevel l,UUID village){int lit=0,dark=0;for(var x:cells(l,village)){if(!l.hasChunkAt(x.getKey()))continue;if(lit(l,x.getKey()))lit++;else dark++;}return new int[]{lit,dark};}
}
