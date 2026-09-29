package org.villageastra.world;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
/** AD-140 (owner 2026-09-23: "every new animal in the livestock farms must appear through a unique and interesting quest"): the quests that
 *  bring each kind of animal to the yard. The village asks for the first kind its yard keeps and does not have yet; the card leads to a real
 *  place with real animals, and the kind opens (AnimalUnlocks) the moment a founding pair of them stands in a pen of that kind.
 *  <ul><li>{@code flock} — sheep: a band of rustlers drove a flock off and keeps it penned at its camp; the band is fought off, the pen's
 *  gate opened, and the sheep follow the wheat in the player's hand home to the yard.</li>
 *  <li>{@code sinkhole} — cow: a cow and her calf down a sheer pit; she will not be pulled up on a rope — you build her a way out, which
 *  her own pathfinding has to find — and the calf follows its mother home.</li>
 *  <li>{@code sow} — pig: the swineherd's saddled sow ran off into the woods with her litter; a trail of rooted earth leads to her; she will
 *  not go on a rope nor after a carrot in a hand, so she is ridden home with a carrot on a stick, and her piglets follow their mother.</li>
 *  <li>{@code brood} — chicken: the broody hen of a henhouse the foxes raided and her warm eggs; every blow you take on the way cracks one,
 *  and at home the eggs laid on hay in the hen's pen hatch into chicks while she is there. Sparing the foxes is rewarded.</li></ul>
 *  The animals are counted only in a pen of their kind, let go (not on a lead, not ridden), each once; what was really brought is paid even
 *  if the rest is lost. The state that belongs to the place — who was admitted, who fell, the gift, the nest — lives in the site record, so
 *  an abandoned card comes back asking only for the rest. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class AnimalQuests {
 private AnimalQuests(){}
 public static final String ANIMALS="animals",FLOCK="flock",SINKHOLE_T="sinkhole",BROOD="brood",SOW="sow";
 public static final List<String> TEMPLATES=List.of(FLOCK,SINKHOLE_T,BROOD,SOW);
 private static final Map<String,String> KIND=Map.of(FLOCK,"sheep",SINKHOLE_T,"cow",BROOD,"chicken",SOW,"pig");
 private static final Map<String,String> SITE=Map.of(FLOCK,AnimalSites.RUSTLERS,SINKHOLE_T,AnimalSites.SINKHOLE,BROOD,AnimalSites.HENHOUSE,SOW,AnimalSites.WALLOW);
 private static final Map<String,EntityType<?>> HABITAT=Map.of(FLOCK,EntityType.SHEEP,SINKHOLE_T,EntityType.COW,BROOD,EntityType.FOX,SOW,EntityType.PIG);
 /** A warm egg of a brood carries the id of its quest. */
 public static final String EGG="AstraQuestEgg";
 public static final int EVERY=Quests.spec(ANIMALS).get("every").getAsInt();
 private static final int NEAR=48;
 public static boolean is(String template){return TEMPLATES.contains(template);}
 public static String kind(String template){return KIND.getOrDefault(template,"");}
 public static String template(String kind){for(var e:KIND.entrySet())if(e.getValue().equals(kind))return e.getKey();return null;}
 static int number(String template,String key){return Quests.spec(template).get(key).getAsInt();}
 // ---------------------------------------------------------------- posting
 /** Posts the card for the first kind the yard keeps that the village has not got, one card at a time within Quests.MAX_OPEN. */
 public static CompoundTag post(ServerLevel l,SettlementData.Entry e,long now){
  AnimalUnlocks.migrateWorld(l.getServer());
  if(AnimalYard.yard(e)==null||AnimalUnlocks.legacyPending(l,e))return null;
  var village=e.settlement().id();int open=0;
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;var state=q.getString("state");
   if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;open++;if(is(q.getString("template")))return null;}
  if(open>=Quests.MAX_OPEN)return null;
  String template=null;
  for(var k:AnimalYard.kinds(l,e))if(!AnimalUnlocks.unlocked(l,e,k)&&now>=AnimalUnlocks.retryAt(l,e,k)&&template(k)!=null){template=template(k);break;}
  if(template==null)return null;
  var q=Quests.blank(village,template,now,Quests.deadline(template));
  q.putInt("target",number(template,"count"));q.putLong("coins",Quests.coins(template));q.putLong("reputation",Quests.reputation(template));
  q.putString("animal",kind(template));
  var anchor=habitat(l,e,template,now);
  q.putLong("site",anchor.asLong());q.putBoolean("pending",true);q.putString("kind",SITE.get(template));q.putString("status","pending");
  Quests.store(l,village,q);
  for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)
   player.sendSystemMessage(Component.translatable("quest.villageastra.animal.posted",Component.translatable("quest.villageastra.kind."+kind(template)),Component.translatable("quest.villageastra.template."+template)));
  return q;
 }
 /** Where the animals of this quest live by nature: the nearest country whose own animals include them, out along one of eight roads. */
 static BlockPos habitat(ServerLevel l,SettlementData.Entry e,String template,long now){
  int distance=number(template,"distance");double angle=Math.PI/4*Math.floorMod(now/EVERY,8L);
  var from=e.center().offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance));
  var type=HABITAT.get(template);
  Predicate<Holder<Biome>> home=h->h.value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap().stream().anyMatch(d->d.type==type);
  var found=l.findClosestBiome3d(home,from,number(template,"biome_radius"),32,64);
  if(found==null)return from;var at=found.getFirst();
  // The cross never falls on the village itself: a country that begins at its gate is looked for farther out.
  if(at.distSqr(e.center())<(long)(distance/2)*(distance/2))return from;
  for(int r=0;r<=16;r+=4)for(int dx=-r;dx<=r;dx+=4)for(int dz=-r;dz<=r;dz+=4){if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;var p=at.offset(2+dx,0,2+dz);if(home.test(l.getBiome(p)))return p;}
  return at;
 }
 /** What the site of a waiting card is made of. */
 static QuestSites.Order order(String template){return new QuestSites.Order(SITE.get(template),0,0,0,null,ItemStack.EMPTY,ItemStack.EMPTY);}
 /** A place that came out without its animals is scenery: it is tried again a little farther on. */
 static boolean sound(ServerLevel l,String template,CompoundTag built){
  if(template.equals(SOW))return built.contains("sty");
  int need=switch(template){case FLOCK->number(FLOCK,"flock");case SINKHOLE_T->2;default->1;};
  return built.getList("animals",Tag.TAG_INT_ARRAY).size()>=need;
 }
 // ---------------------------------------------------------------- warm eggs
 public static ItemStack[] warmEggs(UUID quest,int count){
  var egg=new ItemStack(Items.EGG,count);egg.getOrCreateTag().putUUID(EGG,quest);egg.setHoverName(Component.translatable("quest.villageastra.animal.warm_egg"));
  return new ItemStack[]{egg};
 }
 static UUID eggOf(ItemStack s){return s.is(Items.EGG)&&s.hasTag()&&s.getTag().hasUUID(EGG)?s.getTag().getUUID(EGG):null;}
 // ---------------------------------------------------------------- the pass
 private static List<UUID> list(CompoundTag t,String key){var out=new ArrayList<UUID>();for(var x:t.getList(key,Tag.TAG_INT_ARRAY))out.add(NbtUtils.loadUUID(x));return out;}
 private static void add(CompoundTag t,String key,UUID id){var l=t.getList(key,Tag.TAG_INT_ARRAY);l.add(NbtUtils.createUUID(id));t.put(key,l);}
 private static String role(Entity a){return a.getPersistentData().getString(AnimalSites.ROLE);}
 /** The animals of the place that may still be brought home by this card's deed: listed, not yet admitted, not fallen, and of a part that counts. */
 private static List<UUID> candidates(ServerLevel l,CompoundTag site,String template){
  var admitted=list(site,"admitted");var fallen=list(site,"fallen");var roles=site.getCompound("roles");var out=new ArrayList<UUID>();
  for(var id:list(site,"animals")){if(admitted.contains(id)||fallen.contains(id))continue;var role=roles.getString(id.toString());
   if(template.equals(BROOD)&&!role.equals("hen"))continue;
   out.add(id);}
  return out;
 }
 /** Every live animal card of the village, stepped: the place's own events, the herd counted into the pens, and the losses. */
 public static void tick(ServerLevel l,SettlementData.Entry e,long now){
  if(Math.floorMod(now,20L)!=0)return;
  AnimalUnlocks.migrateWorld(l.getServer());
  if(AnimalUnlocks.legacyPending(l,e)){var present=AnimalYard.present(l,e);if(present!=null)AnimalUnlocks.settleLegacy(l,e,present);}
  var village=e.settlement().id();
  for(var raw:Quests.board(l,village).getList("quests",Tag.TAG_COMPOUND)){var old=(CompoundTag)raw;
   if(!is(old.getString("template")))continue;var state=old.getString("state");if(!state.equals(Quests.OPEN)&&!state.equals(Quests.TAKEN))continue;
   var q=Quests.quest(l,village,old.getUUID("id"));if(q==null)continue;
   var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
   step(l,e,q,owner,now);}
 }
 /** One pass of one card with its owner given: the tick passes the online player, a test passes its own. */
 public static void step(ServerLevel l,SettlementData.Entry e,CompoundTag q,ServerPlayer owner,long now){
  var village=e.settlement().id();var template=q.getString("template");var root=Quests.root(q);
  // A place still being looked for is not stepped: an unsound attempt left behind is scenery, not this card's place.
  if(q.getBoolean("pending"))return;
  var site=QuestSites.site(l,root);
  // A built card whose place the mod lost is broken, blaming nobody (AD-105).
  if(site==null){Quests.settle(l,village,Quests.quest(l,village,q.getUUID("id")),Quests.BROKEN,"site_missing",owner);return;}
  if(site.getString("state").equals("closed"))return;
  var was=q.copy();
  // A place built before its roles were written down is read once from the animals themselves.
  boolean siteChanged=learnRoles(l,site);
  String status=q.getString("status");int left=q.getInt("status_left");
  boolean near=owner!=null&&owner.level()==l&&owner.blockPosition().distSqr(QuestSites.origin(site))<=NEAR*NEAR;
  boolean taken=q.getString("state").equals(Quests.TAKEN);
  switch(template){
   case FLOCK->{var camp=QuestSites.origin(site);
    // The band keeps to its camp; while any of it stands, the flock is theirs.
    var band=l.hasChunkAt(camp)?Adventures.band(l,site,q):List.<Mob>of();
    if(!band.isEmpty()){status="bandits";left=band.size();}
    else if(l.hasChunkAt(camp)||site.getBoolean("band_down")){if(!site.getBoolean("band_down")){site.putBoolean("band_down",true);siteChanged=true;
      if(owner!=null)owner.displayClientMessage(Component.translatable("quest.villageastra.animal.band_down"),false);}
     var gate=BlockPos.of(site.getLong("gate"));boolean open=l.hasChunkAt(gate)&&l.getBlockState(gate).hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN)&&l.getBlockState(gate).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
     status=open||site.getBoolean("gate_opened")?"drive":"gate";left=0;if(open&&!site.getBoolean("gate_opened")){site.putBoolean("gate_opened",true);siteChanged=true;}}}
   case SINKHOLE_T->{var cow=animal(l,site,"cow");var calf=animal(l,site,"calf");
    if(cow!=null&&near){boolean stuck=stuck(site,cow);
     if(!stuck&&site.getBoolean("stuck_seen")&&!site.getBoolean("freed")){site.putBoolean("freed",true);siteChanged=true;
      l.playSound(null,cow.blockPosition(),SoundEvents.COW_AMBIENT,SoundSource.NEUTRAL,1.5f,1f);
      l.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,cow.getX(),cow.getY()+1,cow.getZ(),12,.6,.6,.6,0);
      owner.displayClientMessage(Component.translatable("quest.villageastra.animal.cow_free"),false);}
     if(stuck&&!site.getBoolean("stuck_seen")){site.putBoolean("stuck_seen",true);siteChanged=true;}
     status=stuck?"stuck":"free";left=0;}
    if(cow!=null&&calf!=null&&cow.distanceTo(calf)>16&&!AnimalSites.inPit(site,cow.blockPosition())){status="calf_behind";left=(int)cow.distanceTo(calf);}}
   case BROOD->{var den=BlockPos.of(site.getLong("den"));
    if(taken&&near&&!site.getBoolean("ambushed")){AnimalSites.ambush(l,site,root,EntityType.FOX,den,number(BROOD,"foxes"),2);site.putBoolean("ambushed",true);siteChanged=true;
     owner.displayClientMessage(Component.translatable("quest.villageastra.animal.foxes_out"),false);}
    siteChanged|=hatch(l,e,q,site,now);
    if(!site.contains("eggs_out")){site.putInt("eggs_out",number(BROOD,"eggs"));siteChanged=true;}
    var hen=henPenned(site);var nest=site.getList("nest",Tag.TAG_COMPOUND).size();
    if(!hen){status="hen";left=site.getInt("eggs_out");}else if(nest>0){status="hatching";left=nest;}else{status="eggs";left=site.getInt("eggs_out");}}
   case SOW->{
    // She wandered off with her litter: when somebody comes for her, she is out there at the end of a trail of rooted earth.
    if(taken&&near&&!site.contains("wandered")){if(AnimalSites.wander(l,site,root,number(SOW,"wander"),number(SOW,"piglets"))){siteChanged=true;
      owner.displayClientMessage(Component.translatable("quest.villageastra.animal.sow_trail"),false);}}
    var sow=animal(l,site,"sow");
    if(!site.contains("wandered")){status="trail";left=0;}
    else if(sow!=null&&sow.isVehicle()){status="ride";left=0;int behind=0;for(var id:list(site,"animals"))if(l.getEntity(id) instanceof Pig pg&&pg.isBaby()&&pg.isAlive()&&pg.getPersistentData().hasUUID(AnimalSites.TAG)&&pg.distanceTo(sow)>16)behind++;if(behind>0){status="piglets_behind";left=behind;}}
    else if(sow!=null){status="found";left=0;}
    // A storm is a danger to them: lightning turns a pig into something else.
    if(l.isThundering()&&!"piglets_behind".equals(status))status="storm";}
   default->{}
  }
  // The herd counted into the pens: each animal once, let go, in a pen of its own kind.
  int before=q.getInt("progress");
  if(taken)for(var id:candidates(l,site,template)){
   if(!(l.getEntity(id) instanceof Animal a)||!a.isAlive())continue;
   if(a.getLeashHolder() instanceof Player||a.isPassenger()||a.isVehicle()||!AnimalYard.inPen(l,e,a,kind(template)))continue;
   admit(l,e,q,site,a);siteChanged=true;}
  if(siteChanged)QuestSites.save(l,root,site);
  int penned=q.getList("penned",Tag.TAG_INT_ARRAY).size();q.putInt("progress",Math.min(penned,q.getInt("target")));
  if(site.getList("admitted",Tag.TAG_INT_ARRAY).size()>=Quests.spec(ANIMALS).get("pair").getAsInt()&&AnimalUnlocks.unlock(l,e,kind(template),"quest",root))
   for(var player:l.players())if(player.blockPosition().distSqr(e.center())<=256*256)player.sendSystemMessage(Component.translatable("quest.villageastra.animal.unlocked",Component.translatable("quest.villageastra.kind."+kind(template))));
  // Foxes left alive by the one who took their hen are worth something to the village.
  if(template.equals(BROOD)&&before<q.getInt("target")&&q.getInt("progress")>=q.getInt("target")&&!q.getBoolean("foxes_killed")){
   q.putLong("reputation",q.getLong("reputation")+number(BROOD,"spare_bonus"));if(owner!=null)owner.displayClientMessage(Component.translatable("quest.villageastra.animal.foxes_spared",number(BROOD,"spare_bonus")),false);}
  q.putString("status",status);q.putInt("status_left",left);
  if(!q.equals(was))Quests.store(l,village,q);
  var lost=lost(l,q,site);
  if(!lost.isEmpty()&&q.getInt("progress")<q.getInt("target")){var fresh=Quests.quest(l,village,q.getUUID("id"));
   Quests.settle(l,village,fresh,taken?Quests.FAILED:Quests.CANCELLED,lost,owner);}
 }
 private static boolean learnRoles(ServerLevel l,CompoundTag site){
  var roles=site.getCompound("roles");boolean changed=false;
  for(var id:list(site,"animals"))if(!roles.contains(id.toString())&&l.getEntity(id) instanceof Animal a&&!role(a).isEmpty()){roles.putString(id.toString(),role(a));changed=true;}
  if(changed)site.put("roles",roles);return changed;
 }
 private static Animal animal(ServerLevel l,CompoundTag site,String role){
  var roles=site.getCompound("roles");for(var id:list(site,"animals"))if(roles.getString(id.toString()).equals(role)&&l.getEntity(id) instanceof Animal a&&a.isAlive())return a;return null;
 }
 /** The cow is stuck while she is down in the pit and no way a cow can walk leads up out of it. */
 public static boolean stuck(CompoundTag site,Animal cow){
  if(!AnimalSites.inPit(site,cow.blockPosition()))return false;
  return !escape((ServerLevel)cow.level(),site,cow.blockPosition());
 }
 /** Whether a cow standing here can walk out of the pit on her own: from standing place to standing place, up at most a block and a bit
  *  (a full block, a slab, a stair) or down at most three, with room for her above, or swimming up a water column — until she stands on
  *  the ground at the rim or beyond the pit's walls. What the player built counts as it stands, whatever it is made of. */
 static boolean escape(ServerLevel l,CompoundTag site,BlockPos from){
  var pit=BlockPos.of(site.getLong("pit"));int depth=site.getInt("depth");double rim=pit.getY()+depth-0.01;int reach=3+depth+4;
  var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();var start=stand(l,from);if(start==null)start=stand(l,from.above());if(start==null)return false;
  queue.add(start);seen.add(start);
  while(!queue.isEmpty()&&seen.size()<2000){var at=queue.poll();double floor=floor(l,at);
   boolean outside=Math.abs(at.getX()-pit.getX())>=3||Math.abs(at.getZ()-pit.getZ())>=3;
   // Out at ground level beyond the walls, or anywhere past them (a tunnel dug out through the wall counts as she walks it).
   if(!Double.isNaN(floor)&&(floor>=rim&&outside||Math.abs(at.getX()-pit.getX())>=reach||Math.abs(at.getZ()-pit.getZ())>=reach))return true;
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int dy=1;dy>=-3;dy--){var next=at.relative(d).above(dy);
    if(Math.abs(next.getX()-pit.getX())>reach||Math.abs(next.getZ()-pit.getZ())>reach||seen.contains(next))continue;
    var standing=stand(l,next);if(standing==null||!standing.equals(next))continue;
    double up=floor(l,next)-(Double.isNaN(floor)?at.getY():floor);if(Double.isNaN(up)||up>1.125||up<-3.5)continue;
    if(up>0.6&&!clear(l,at.above(),2))continue;
    seen.add(next);queue.add(next);break;}
   // Only still water or a bubble column carries her up; water poured from the rim runs down, it does not lift her.
   var fluid=l.getFluidState(at);
   if((fluid.is(net.minecraft.tags.FluidTags.WATER)&&fluid.isSource()||l.getBlockState(at).is(Blocks.BUBBLE_COLUMN))&&clear(l,at.above(),1)&&!seen.contains(at.above())
    &&(l.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER)&&l.getFluidState(at.above()).isSource()||l.getBlockState(at.above()).is(Blocks.BUBBLE_COLUMN)||!Double.isNaN(floor(l,at.above()))&&floor(l,at.above())<=at.getY()+1.125)){seen.add(at.above());queue.add(at.above());}}
  return false;
 }
 /** The cell itself when a cow can stand in it (room for her, something under her feet or water), else null. */
 private static BlockPos stand(ServerLevel l,BlockPos p){
  if(!clear(l,p,2)&&!low(l,p))return null;
  if(l.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER))return p;
  return Double.isNaN(floor(l,p))?null:p;
 }
 /** Where her feet rest in this cell: on a low block in it (slab, carpet), or on top of the block under it; NaN when nothing holds her. */
 private static double floor(ServerLevel l,BlockPos p){
  if(low(l,p))return p.getY()+l.getBlockState(p).getCollisionShape(l,p).max(net.minecraft.core.Direction.Axis.Y);
  var below=l.getBlockState(p.below()).getCollisionShape(l,p.below());if(below.isEmpty())return l.getFluidState(p).isEmpty()?Double.NaN:p.getY();
  return p.getY()-1+below.max(net.minecraft.core.Direction.Axis.Y);
 }
 private static boolean low(ServerLevel l,BlockPos p){var shape=l.getBlockState(p).getCollisionShape(l,p);return !shape.isEmpty()&&shape.max(net.minecraft.core.Direction.Axis.Y)<=0.5&&clear(l,p.above(),2);}
 private static boolean clear(ServerLevel l,BlockPos p,int cells){for(int i=0;i<cells;i++)if(!l.getBlockState(p.above(i)).getCollisionShape(l,p.above(i)).isEmpty())return false;return true;}
 /** The hen is home now: admitted, alive, in the chicken pen. */
 private static boolean henHome(ServerLevel l,SettlementData.Entry e,CompoundTag site){
  var roles=site.getCompound("roles");for(var id:list(site,"admitted"))if(roles.getString(id.toString()).equals("hen"))return l.getEntity(id) instanceof Animal a&&a.isAlive()&&AnimalYard.inPen(l,e,a,"chicken");
  return false;
 }
 private static boolean henPenned(CompoundTag site){var roles=site.getCompound("roles");for(var id:list(site,"admitted"))if(roles.getString(id.toString()).equals("hen"))return true;return false;}
 private static void admit(ServerLevel l,SettlementData.Entry e,CompoundTag q,CompoundTag site,Animal a){
  a.getPersistentData().remove(AnimalSites.TAG);a.getPersistentData().remove(AnimalSites.ROLE);
  // The ewe's lamb brought home with her is worth a little more to the village.
  if(site.getCompound("roles").getString(a.getUUID().toString()).equals("lamb")&&q.getString("template").equals(FLOCK))q.putLong("reputation",q.getLong("reputation")+number(FLOCK,"lamb_bonus"));
  AnimalYard.admit(l,e,a,AnimalYard.kind(a));add(site,"admitted",a.getUUID());add(q,"penned",a.getUUID());
 }
 /** Eggs laid on hay in the hen's pen hatch into chicks once the hen herself is penned there; an egg whose hay is gone rolls back out. */
 private static boolean hatch(ServerLevel l,SettlementData.Entry e,CompoundTag q,CompoundTag site,long now){
  var nest=site.getList("nest",Tag.TAG_COMPOUND);if(nest.isEmpty())return false;boolean changed=false;var keep=new ListTag();
  boolean hen=henHome(l,e,site);long ticks=number(BROOD,"hatch_ticks");
  for(var raw:nest){var n=(CompoundTag)raw;var pos=BlockPos.of(n.getLong("pos"));
   if(!l.hasChunkAt(pos)){keep.add(n);continue;}
   // Hay taken away: the egg goes back to the one who laid it (never left lying in the yard, where it would be swept into the chest).
   if(!l.getBlockState(pos).is(Blocks.HAY_BLOCK)){var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;if(owner==null){keep.add(n);continue;}
    var egg=warmEggs(Quests.root(q),1)[0];if(!owner.getInventory().add(egg))owner.drop(egg,false);
    site.putInt("eggs_out",site.getInt("eggs_out")+1);changed=true;continue;}
   if(!hen||now-n.getLong("laid")<ticks||!q.getString("state").equals(Quests.TAKEN)){keep.add(n);continue;}
   var chick=EntityType.CHICKEN.create(l);if(chick==null){keep.add(n);continue;}
   chick.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,l.random.nextFloat()*360,0);chick.setAge(-24000);chick.setPersistenceRequired();
   if(!l.addFreshEntity(chick)){keep.add(n);continue;}
   add(site,"animals",chick.getUUID());var roles=site.getCompound("roles");roles.putString(chick.getUUID().toString(),"chick");site.put("roles",roles);
   admit(l,e,q,site,chick);changed=true;
   l.playSound(null,pos,SoundEvents.CHICKEN_EGG,SoundSource.NEUTRAL,1f,1.2f);
   var owner=q.hasUUID("owner")?l.getServer().getPlayerList().getPlayer(q.getUUID("owner")):null;
   if(owner!=null)owner.displayClientMessage(Component.translatable("quest.villageastra.animal.egg_hatched"),false);}
  if(changed)site.put("nest",keep);return changed;
 }
 /** Why this card can no longer be done ("" while it can): too few of its animals are left, or too few eggs. */
 static String lost(ServerLevel l,CompoundTag q,CompoundTag site){
  var template=q.getString("template");int target=q.getInt("target"),penned=q.getList("penned",Tag.TAG_INT_ARRAY).size(),need=target-penned;
  if(need<=0)return "";
  var fallen=list(site,"fallen");var roles=site.getCompound("roles");
  switch(template){
   case BROOD->{
    boolean henAlive=henPenned(site)||!candidates(l,site,BROOD).isEmpty();
    int possible=(henPenned(site)?0:henAlive?1:0)+(henAlive?site.getList("nest",Tag.TAG_COMPOUND).size()+site.getInt("eggs_out"):0);
    if(!site.contains("eggs_out"))possible+=number(BROOD,"eggs");
    return possible<need?(henAlive?"eggs_broken":"animals_lost"):"";}
   case SOW->{if(!site.contains("wandered"))return "";return candidates(l,site,template).size()<need?"animals_lost":"";}
   default->{return candidates(l,site,template).size()<need?"animals_lost":"";}
  }
 }
 /** For the board: why nobody can take this card any more, or "". */
 static String impossible(ServerLevel l,CompoundTag q){
  if(!is(q.getString("template"))||q.getBoolean("pending"))return "";var site=QuestSites.site(l,Quests.root(q));return site==null||site.getString("state").equals("closed")?"":lost(l,q,site);
 }
 /** A closed card: a failed kind waits before it is asked for again; an NPC village gets it on its own after cards nobody took; the place is finished with. */
 static void closed(ServerLevel l,UUID village,CompoundTag q,String state,String reason){
  var template=q.getString("template");if(!is(template))return;var e=SettlementData.get(l.getServer()).entry(village);if(e==null)return;
  long now=SettlementData.get(l.getServer()).clock().ticks();var kind=kind(template);var spec=Quests.spec(ANIMALS);
  if((state.equals(Quests.FAILED)||state.equals(Quests.CANCELLED))&&!AnimalUnlocks.unlocked(l,e,kind)){
   AnimalUnlocks.retry(l,e,kind,now+spec.get("retry").getAsLong());
   if(state.equals(Quests.FAILED)&&reason.equals("deadline")&&!q.contains("taken")&&spec.get("npc_fallback").getAsBoolean()
    &&AnimalUnlocks.expired(l,e,kind)>=spec.get("fallback_expiries").getAsInt()&&e.settlement().governance().playerMayor()==null)
    AnimalUnlocks.unlock(l,e,kind,"keeper",null);}
  if(state.equals(Quests.ABANDONED))return;
  var site=QuestSites.site(l,Quests.root(q));if(site==null||site.getString("state").equals("closed"))return;
  if(l.hasChunkAt(QuestSites.origin(site)))for(var mob:Adventures.band(l,site,q))mob.discard();
  site.putString("state","closed");site.putLong("closed",now);QuestSites.save(l,Quests.root(q),site);
 }
 /** Test seam: a waiting card's site built on the test's own ground. */
 public static boolean materialize(ServerLevel l,SettlementData.Entry e,UUID id,BlockPos at,long now){return Chains.materialize(l,e,id,at,now);}
 // ---------------------------------------------------------------- the world's side
 /** A live card of this id: {village, record}, or null. */
 private static Object[] card(ServerLevel l,UUID root){return Quests.find(l,root);}
 @SubscribeEvent public static void died(LivingDeathEvent event){
  var dead=event.getEntity();if(!(dead.level() instanceof ServerLevel l))return;var data=dead.getPersistentData();
  var killer=event.getSource().getEntity() instanceof ServerPlayer p?p:null;
  if(data.hasUUID(AnimalSites.TAG)){var root=data.getUUID(AnimalSites.TAG);var found=card(l,root);if(found==null)return;var q=(CompoundTag)found[1];var village=(UUID)found[0];
   var site=QuestSites.site(l,root);
if(site!=null&&!list(site,"fallen").contains(dead.getUUID())){add(site,"fallen",dead.getUUID());QuestSites.save(l,root,site);}
   // Whoever kills an animal somebody is bringing home pays for it there, but not the one bringing it: that loss is their own.
   if(killer!=null&&!(q.hasUUID("owner")&&q.getUUID("owner").equals(killer.getUUID())))Quests.penalize(l.getServer(),village,killer.getUUID(),Quests.spec(ANIMALS).get("kill_reputation").getAsInt());
   return;}
  // The foxes of a brood killed by the one who came for their hen.
  if(dead instanceof Fox&&data.hasUUID(QuestSites.QUEST)&&killer!=null){var found=card(l,data.getUUID(QuestSites.QUEST));
   if(found!=null){var q=(CompoundTag)found[1];if(q.getString("template").equals(BROOD)&&q.hasUUID("owner")&&q.getUUID("owner").equals(killer.getUUID())&&!q.getBoolean("foxes_killed")){q.putBoolean("foxes_killed",true);Quests.store(l,(UUID)found[0],q);}}}
 }
 /** The sow of the farmstead: saddled, stubborn, and deaf to carrots in a hand — she is ridden home with a carrot on a stick, or not at all. */
 static void stubborn(Pig sow){sow.goalSelector.removeAllGoals(g->g instanceof net.minecraft.world.entity.ai.goal.TemptGoal);}
 @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
  if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Pig pig))return;var t=pig.getPersistentData();
  if(t.hasUUID(AnimalSites.TAG)&&t.getString(AnimalSites.ROLE).equals("sow"))stubborn(pig);
 }
 /** A quest pig struck by lightning is no pig any more: it is lost to the card. */
 @SubscribeEvent public static void converted(LivingConversionEvent.Post event){
  var was=event.getEntity();if(!(was.level() instanceof ServerLevel l)||!was.getPersistentData().hasUUID(AnimalSites.TAG))return;var root=was.getPersistentData().getUUID(AnimalSites.TAG);
  if(card(l,root)==null)return;var site=QuestSites.site(l,root);if(site!=null&&!list(site,"fallen").contains(was.getUUID())){add(site,"fallen",was.getUUID());QuestSites.save(l,root,site);}
 }
 /** She will not be pulled up a sheer wall on a rope: somebody has to make her a way out first. The sow will not go on a rope at all. */
 @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract event){
  if(!(event.getLevel() instanceof ServerLevel l)||!event.getItemStack().is(Items.LEAD)||!(event.getTarget() instanceof Animal a))return;
  if(a.getPersistentData().hasUUID(AnimalSites.TAG)&&role(a).equals("sow")&&card(l,a.getPersistentData().getUUID(AnimalSites.TAG))!=null){
   event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
   if(event.getEntity() instanceof ServerPlayer p){p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(a,null));p.inventoryMenu.sendAllDataToRemote();}
   event.getEntity().displayClientMessage(Component.translatable("quest.villageastra.animal.refuse_lead_sow"),true);return;}
  if(!a.getPersistentData().hasUUID(AnimalSites.TAG)||!role(a).equals("cow"))return;var root=a.getPersistentData().getUUID(AnimalSites.TAG);
  if(card(l,root)==null)return;var site=QuestSites.site(l,root);if(site==null||!stuck(site,a))return;
  event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
  // The client guessed the lead would hold: it is told the cow is free and the lead still in hand.
  if(event.getEntity() instanceof ServerPlayer p){p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket(a,null));p.inventoryMenu.sendAllDataToRemote();}
  event.getEntity().displayClientMessage(Component.translatable("quest.villageastra.animal.refuse_lead_stuck"),true);
 }
 /** Every blow a player takes cracks one warm egg they carry. */
 @SubscribeEvent public static void hurt(LivingHurtEvent event){
  if(!(event.getEntity() instanceof ServerPlayer p)||event.getAmount()<=0)return;var l=p.serverLevel();
  // A blow is a hit, a fall or a blast: not every tick of fire, poison or a berry bush, and at most one egg a second.
  var src=event.getSource();boolean blow=src.getEntity()!=null||src.getDirectEntity()!=null||src.is(net.minecraft.tags.DamageTypeTags.IS_FALL)||src.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
  if(!blow||p.getPersistentData().getLong("AstraEggCrack")>l.getGameTime())return;
  var carried=new ArrayList<ItemStack>(p.getInventory().items);carried.addAll(p.getInventory().offhand);
  for(var s:carried){var root=eggOf(s);if(root==null||card(l,root)==null)continue;
   var site=QuestSites.site(l,root);if(site==null)continue;
   s.shrink(1);p.getPersistentData().putLong("AstraEggCrack",l.getGameTime()+20);if(!site.contains("eggs_out"))site.putInt("eggs_out",number(BROOD,"eggs"));site.putInt("eggs_out",Math.max(0,site.getInt("eggs_out")-1));QuestSites.save(l,root,site);
   l.playSound(null,p.blockPosition(),SoundEvents.TURTLE_EGG_CRACK,SoundSource.PLAYERS,1f,1.3f);
   p.displayClientMessage(Component.translatable("quest.villageastra.animal.egg_cracked",site.getInt("eggs_out")),true);return;}
 }
 /** A warm egg is carried, never thrown. */
 @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event){
  if(!(event.getLevel() instanceof ServerLevel l)||eggOf(event.getItemStack())==null||card(l,eggOf(event.getItemStack()))==null)return;
  event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
  if(event.getEntity() instanceof ServerPlayer p)p.inventoryMenu.sendAllDataToRemote();
  event.getEntity().displayClientMessage(Component.translatable("quest.villageastra.animal.refuse_egg_throw"),true);
 }
 /** A warm egg laid on hay in a pen of the hen's kind, to hatch there under her. */
 @SubscribeEvent public static void lay(PlayerInteractEvent.RightClickBlock event){
  if(!(event.getLevel() instanceof ServerLevel l))return;var stack=event.getItemStack();var root=eggOf(stack);if(root==null)return;
  var pos=event.getPos();if(!l.getBlockState(pos).is(Blocks.HAY_BLOCK))return;
  var found=card(l,root);if(found==null)return;event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
  var player=event.getEntity();
  var e=SettlementData.get(l.getServer()).entry((UUID)found[0]);
  if(e==null||!AnimalYard.inPen(l,e,pos,"chicken")){player.displayClientMessage(Component.translatable("quest.villageastra.animal.egg_not_pen"),true);return;}
  var site=QuestSites.site(l,root);if(site==null)return;
  var nest=site.getList("nest",Tag.TAG_COMPOUND);var n=new CompoundTag();n.putLong("pos",pos.asLong());n.putLong("laid",SettlementData.get(l.getServer()).clock().ticks());nest.add(n);site.put("nest",nest);
  if(!site.contains("eggs_out"))site.putInt("eggs_out",number(BROOD,"eggs"));site.putInt("eggs_out",Math.max(0,site.getInt("eggs_out")-1));QuestSites.save(l,root,site);
  stack.shrink(1);l.playSound(null,pos,SoundEvents.CHICKEN_EGG,SoundSource.NEUTRAL,1f,.8f);
  player.displayClientMessage(Component.translatable("quest.villageastra.animal.egg_laid"),true);
 }
}
