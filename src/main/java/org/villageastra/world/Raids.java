package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
/** AD-064: raids on a settlement (WAR-001). While somebody plays, the settlement's own clock brings a wave now and then: at night monsters, by day
 *  bandits. The wave grows with the population, comes from one side, is announced in chat, goes for the residents, and ends when it is beaten
 *  or gives up. Peaceful difficulty brings none. */
public final class Raids {
 /** Active ticks before the first raid and between raids, the wave size, where it gathers and how long it keeps attacking. */
 public static final long FIRST=36000,INTERVAL_MIN=48000,INTERVAL_MAX=72000,GIVE_UP=6000;
 public static final int BASE=2,PER_RESIDENTS=3,MAX=12,NEAR=44,FAR=56,HUNT=32;
 public enum Kind{MONSTERS,BANDITS}
 private Raids(){}
 private static Path path(ServerLevel l,UUID village){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-raids/"+village+".bin");}
 /** Records are read every tick by sheltering residents: they are kept in memory, keyed by their file, and written through. */
 private static final Map<Path,CompoundTag> CACHE=new HashMap<>();
 public static CompoundTag record(ServerLevel l,UUID village){
  var p=path(l,village);var cached=CACHE.get(p);if(cached!=null)return cached.copy();
  CompoundTag t;if(Files.exists(p))t=NbtRecord.read(p);else{t=new CompoundTag();t.putInt("schema",1);}
  CACHE.put(p,t.copy());return t;
 }
 private static void save(ServerLevel l,UUID village,CompoundTag t){var p=path(l,village);NbtRecord.write(p,t);CACHE.put(p,t.copy());}
 public static void clear(){CACHE.clear();RaidRoster.clear();}
 public static boolean allowed(Difficulty d){return d!=Difficulty.PEACEFUL;}
 /** Raiders in a wave for a settlement of this many living residents. */
 public static int size(int residents){return Math.min(MAX,BASE+Math.max(0,residents)/PER_RESIDENTS);}
 public static boolean active(ServerLevel l,UUID village){return record(l,village).contains("active");}
 /** AD-089: a village whose raiders' lair was burnt out gets a quiet spell: the next wave cannot come before {@code until}. */
 public static void calm(ServerLevel l,UUID village,long until){var t=record(l,village);if(t.contains("active"))return;t.putLong("nextAt",Math.max(t.getLong("nextAt"),until));save(l,village,t);}
 private static long interval(UUID village,int wave){var r=new Random(village.getMostSignificantBits()^(31L*wave));return INTERVAL_MIN+(long)(r.nextDouble()*(INTERVAL_MAX-INTERVAL_MIN));}
 /** The settlement clock: schedules the first raid, starts one when it is due, keeps the active one going. */
 public static void tick(MinecraftServer server,long now){
  for(var e:List.copyOf(SettlementData.get(server).entries()))tick(server,e,now);
 }
 /** One village's raid clock; public so a test drives its own village without touching the raids of every other village in the world. */
 public static void tick(MinecraftServer server,SettlementData.Entry e,long now){
  var l=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation(e.dimension())));
  // AD-111: a touched hall chunk has no raiders loaded; a raid is kept, started or scheduled only where the village ticks.
  if(l==null||!l.hasChunkAt(e.center())||!TouchLoad.ticking(l,e.center()))return;
  var t=record(l,e.settlement().id());
  if(t.contains("active")){update(l,e,now);return;}
  if(!t.contains("nextAt")){t.putLong("nextAt",now+FIRST);save(l,e.settlement().id(),t);return;}
  if(now<t.getLong("nextAt"))return;
  // WAR-005: peaceful difficulty brings no raid; the next one is simply put off.
  if(!allowed(l.getDifficulty())){t.putLong("nextAt",now+INTERVAL_MIN);save(l,e.settlement().id(),t);return;}
  start(l,e,now,SleepGoal.night(l)?Kind.MONSTERS:Kind.BANDITS);
 }
 private static EntityType<? extends Mob> type(Kind kind,int index,int size){
  if(kind==Kind.BANDITS)return index%3==2?EntityType.VINDICATOR:EntityType.PILLAGER;
  if(size>=6&&index==size-1)return EntityType.CREEPER;
  return switch(index%3){case 0->EntityType.ZOMBIE;case 1->EntityType.SKELETON;default->EntityType.SPIDER;};
 }
 /** Starts a wave now: real mobs on the ground at the edge of the settlement, all from one side, and a warning to the players nearby. */
 public static String start(ServerLevel l,SettlementData.Entry e,long now,Kind kind){
  if(!allowed(l.getDifficulty()))return "peaceful";
  var village=e.settlement().id();var t=record(l,village);if(t.contains("active"))return "active";
  int wave=t.getInt("waves")+1;int living=(int)e.settlement().residents().stream().filter(Resident::alive).count();int size=size(living);
  var random=new Random(village.getLeastSignificantBits()^(wave*7919L));double side=random.nextDouble()*Math.PI*2;
  var mobs=new ListTag();var c=e.center();
  // A side of sea or forest gives no footing: the wave then comes from the next side round.
  for(int turn=0;turn<4&&mobs.isEmpty();turn++,side+=Math.PI/2)
  for(int i=0;i<size;i++){
   BlockPos spot=null;
   for(int attempt=0;attempt<12&&spot==null;attempt++){
    double angle=side+(random.nextDouble()-.5)*.7,distance=NEAR+random.nextDouble()*(FAR-NEAR)+Adventures.watch(l,e);
    int x=c.getX()+(int)Math.round(Math.cos(angle)*distance),z=c.getZ()+(int)Math.round(Math.sin(angle)*distance);
    if(!l.hasChunkAt(new BlockPos(x,0,z)))continue;
    int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);var feet=new BlockPos(x,y,z);
    // A readable border chunk can accept an entity without making it visible to the world lookup.
    // Defer this candidate instead of recording a phantom wave that update() immediately calls repelled.
    if(!TouchLoad.ticking(l,feet))continue;
    if(!l.getBlockState(feet.below()).getFluidState().isEmpty()||!l.getBlockState(feet).isAir()||!l.getBlockState(feet.above()).isAir())continue;
    spot=feet;
   }
   if(spot==null)continue;
   var mob=type(kind,i,size).create(l);if(mob==null)continue;
   mob.moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,random.nextFloat()*360,0);
   mob.finalizeSpawn(l,l.getCurrentDifficultyAt(spot),MobSpawnType.EVENT,null,null);
   mob.setPersistenceRequired();mob.addTag("AstraRaid");
   if(l.addFreshEntity(mob))mobs.add(NbtUtils.createUUID(mob.getUUID()));
  }
  if(mobs.isEmpty()){t.putLong("nextAt",now+INTERVAL_MIN);save(l,village,t);return "nowhere";}
  side-=Math.PI/2;
  var active=new CompoundTag();active.putString("kind",kind.name());active.putLong("started",now);active.put("mobs",mobs);active.putInt("spawned",mobs.size());
  active.putInt("residents",living);active.putDouble("side",side);
  t.put("active",active);t.putInt("waves",wave);save(l,village,t);RaidRoster.remember(l,village,active);
  // WAR-001: the warning in chat names what comes and from where.
  String direction=direction(side);
  for(var player:l.players())if(player.blockPosition().distSqr(c)<=256*256)
   player.sendSystemMessage(Component.translatable("raid.villageastra.warning."+kind.name().toLowerCase(Locale.ROOT),mobs.size(),Component.translatable("raid.villageastra.side."+direction),c.getX(),c.getZ()));
  return "";
 }
 static String direction(double angle){
  double a=Math.floorMod((long)Math.round(Math.toDegrees(angle)),360);
  // Minecraft: +x is east, +z is south.
  if(a<45||a>=315)return "east";if(a<135)return "south";if(a<225)return "west";return "north";
 }
 /** Raiders still alive in the active wave. */
 public static List<Mob> raiders(ServerLevel l,UUID village){
  var t=record(l,village);var out=new ArrayList<Mob>();if(!t.contains("active"))return out;
  for(var raw:t.getCompound("active").getList("mobs",Tag.TAG_INT_ARRAY))if(l.getEntity(NbtUtils.loadUUID(raw)) instanceof Mob mob&&mob.isAlive())out.add(mob);
  return out;
 }
 /** A confirmed entity removal is distinct from a chunk unload. Keep the fact across saves and later waves. */
 public static void removed(ServerLevel l,net.minecraft.world.entity.Entity mob){var village=RaidRoster.village(l,mob);if(village==null)return;var t=record(l,village);var active=t.getCompound("active");
  if(!RaidRoster.contains(active,mob.getUUID()))return;var gone=active.getCompound("gone");String key=mob.getUUID().toString();if(gone.getBoolean(key))return;gone.putBoolean(key,true);active.put("gone",gone);save(l,village,t);
 }
 /** Keeps the wave on the residents and closes it when it is beaten or gives up. */
 public static String update(ServerLevel l,SettlementData.Entry e,long now){
  var village=e.settlement().id();var t=record(l,village);if(!t.contains("active"))return "";
  var active=t.getCompound("active");RaidRoster.remember(l,village,active);var alive=raiders(l,village);var c=e.center();
  var gone=active.getCompound("gone");boolean changed=false;int remaining=0;
  for(var raw:active.getList("mobs",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(raw);var body=l.getEntity(id);
   if(body!=null&&!body.isAlive()&&!gone.getBoolean(id.toString())){gone.putBoolean(id.toString(),true);changed=true;}
   if(!gone.getBoolean(id.toString()))remaining++;
  }
  if(changed){active.put("gone",gone);save(l,village,t);}
  for(var mob:alive){
   if(mob.getTarget()!=null&&mob.getTarget().isAlive())continue;
   ResidentEntity prey=null;double best=HUNT*HUNT;
   for(var npc:l.getEntitiesOfClass(ResidentEntity.class,mob.getBoundingBox().inflate(HUNT),r->r.isAlive()&&village.equals(r.settlementId()))){
    double d=npc.distanceToSqr(mob);if(d<best){best=d;prey=npc;}}
   if(prey!=null)mob.setTarget(prey);
   else if(mob.getNavigation().isDone())mob.getNavigation().moveTo(c.getX()+.5,c.getY(),c.getZ()+.5,1);
  }
  String outcome=remaining==0?"repelled":now-active.getLong("started")>GIVE_UP?"withdrew":"";
  if(outcome.isEmpty())return "";
  if(outcome.equals("withdrew"))for(var mob:alive)mob.discard();
  int living=(int)e.settlement().residents().stream().filter(Resident::alive).count();
  var last=new CompoundTag();last.putString("kind",active.getString("kind"));last.putString("outcome",outcome);last.putInt("spawned",active.getInt("spawned"));
  last.putInt("residentsLost",Math.max(0,active.getInt("residents")-living));last.putLong("ended",now);
  t.remove("active");t.put("last",last);t.putLong("nextAt",now+interval(village,t.getInt("waves")));save(l,village,t);
  for(var player:l.players())if(player.blockPosition().distSqr(c)<=256*256)
   player.sendSystemMessage(Component.translatable("raid.villageastra.end."+outcome,last.getInt("residentsLost")));
  return outcome;
 }
}
