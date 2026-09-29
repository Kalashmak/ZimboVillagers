package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
/** AD-043: a real offensive campaign — soldiers of a barracks march out, build camps and a fence ring, blow up the working farms and hold a siege while they really cover it. */
public final class Sieges {
 public static final String GATHERING="gathering",MARCHING="marching",ENCIRCLING="encircling",READY="ready",BESIEGING="besieging",WITHDRAWN="withdrawn",SURRENDERED="surrendered",CLOSED="closed";
 private static final JsonObject ROOT=root();
 public static final int RING=ROOT.get("ring_radius").getAsInt(),CAMP_SPACING=ROOT.get("camp_spacing").getAsInt(),CARRY_FENCES=ROOT.get("carry_fences").getAsInt(),RATIONS=ROOT.get("rations").getAsInt(),
  SECTOR_SOLDIERS=ROOT.get("sector_soldiers").getAsInt(),BREACH_TOLERANCE=ROOT.get("breach_tolerance").getAsInt(),STARVATION=ROOT.get("starvation_nutrition").getAsInt(),SURRENDER_TICKS=ROOT.get("surrender_ticks").getAsInt();
 private static final Map<UUID,CompoundTag> ARMIES=new LinkedHashMap<>();private static MinecraftServer loaded;
 private Sieges(){}
 private static JsonObject root(){try(var s=Sieges.class.getResourceAsStream("/data/villageastra/balance/sieges.json")){if(s==null)throw new IllegalStateException("Missing siege balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static Path dir(MinecraftServer s){return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-armies");}
 public static void clear(){ARMIES.clear();loaded=null;}
 private static synchronized void ensure(MinecraftServer s){
  if(loaded==s)return;ARMIES.clear();loaded=s;var d=dir(s);if(!Files.isDirectory(d))return;
  try(var files=Files.list(d)){for(var f:files.sorted().toList())if(f.getFileName().toString().endsWith(".bin")){var t=NbtRecord.read(f);ARMIES.put(t.getUUID("id"),t);}}catch(IOException e){throw new IllegalStateException(e);}
 }
 public static Collection<CompoundTag> armies(MinecraftServer s){ensure(s);return Collections.unmodifiableCollection(ARMIES.values());}
 public static CompoundTag army(MinecraftServer s,UUID id){ensure(s);return ARMIES.get(id);}
 public static void save(MinecraftServer s,CompoundTag t){ensure(s);ARMIES.put(t.getUUID("id"),t);NbtRecord.write(dir(s).resolve(t.getUUID("id")+".bin"),t);}
 private static void stamp(CompoundTag t,String state,long now){t.putString("state",state);t.getCompound("stamps").putLong(state,now);t.put("stamps",t.getCompound("stamps"));}
 /** A settlement is besieged while some army really holds a closed ring around it. */
 public static boolean besieged(MinecraftServer s,UUID village){
  for(var t:armies(s))if(t.getString("state").equals(BESIEGING)&&t.getUUID("target").equals(village))return true;return false;}
 public static CompoundTag siegeOf(MinecraftServer s,UUID village){
  for(var t:armies(s))if(t.getUUID("target").equals(village)&&!t.getString("state").equals(CLOSED)&&!t.getString("state").equals(WITHDRAWN))return t;return null;}
 /** Soldiers really assigned to the barracks of the attacking settlement; the barracks never spawns anyone. */
 public static List<Resident> soldiers(SettlementData.Entry e){
  var out=new ArrayList<Resident>();for(var r:e.settlement().residents()){var b=e.settlement().workplace(r.id());
   if(r.alive()&&r.profession()==Profession.SOLDIER&&b!=null&&b.type().equals("barracks"))out.add(r);}
  return out;
 }
 /** Muster: every soldier takes real fences, a campfire share and rations from the barracks chest. */
 public static CompoundTag muster(ServerLevel l,SettlementData.Entry attacker,SettlementData.Entry target,long now){
  var s=l.getServer();if(siegeOf(s,target.settlement().id())!=null)return null;
  var barracks=attacker.settlement().buildings().stream().filter(b->b.type().equals("barracks")).findFirst().orElse(null);if(barracks==null)return null;
  var soldiers=soldiers(attacker).stream().filter(r->!CaravanEscorts.reserved(s,r.id())).toList();if(soldiers.isEmpty())return null;var chest=LogisticsRoutes.chest(l,attacker,barracks);if(chest==null)return null;
  int fences=LogisticsRoutes.count(chest,x->x.is(Items.OAK_FENCE)),campfires=LogisticsRoutes.count(chest,x->x.is(Items.CAMPFIRE)),food=LogisticsRoutes.count(chest,x->x.is(Items.BREAD)),tnt=LogisticsRoutes.count(chest,x->x.is(Items.TNT));
  var perimeter=perimeter(l,target);int camps=Math.max(1,perimeter.size()/CAMP_SPACING);
  // AD-104: a field takes tnt_per_module charges per module; a yard has no field and takes one module's worth, as it always did.
  var farms=workingFarms(l,target);int charges=0;for(var f:farms)charges+=f.type().equals("farm")?FarmField.charges(l,target,f):FarmField.TNT_PER_MODULE;
  if(fences<perimeter.size()||campfires<camps||food<soldiers.size()*RATIONS||tnt<charges)return null;
  var supply=new CompoundTag();supply.putInt("fences",perimeter.size());supply.putInt("campfires",camps);supply.putInt("tnt",charges);supply.putInt("bread",soldiers.size()*RATIONS);
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(attacker.settlement().id(),"army/"+target.settlement().id()+"/"+now));t.put("supply",supply);
  t.putUUID("attacker",attacker.settlement().id());t.putUUID("target",target.settlement().id());t.putString("dimension",attacker.dimension());t.put("stamps",new CompoundTag());
  var list=new ListTag();for(var r:soldiers)list.add(NbtUtils.createUUID(r.id()));t.put("soldiers",list);
  var pos=LogisticsRoutes.position(attacker,barracks);
  for(var take:List.of(new ItemStack(Items.OAK_FENCE,perimeter.size()),new ItemStack(Items.CAMPFIRE,camps),new ItemStack(Items.TNT,charges),new ItemStack(Items.BREAD,soldiers.size()*RATIONS))){
   int left=take.getCount(),guard=0;
   while(left>0&&guard++<64){
    var id=Settlement.childId(t.getUUID("id"),"muster/"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(take.getItem())+"/"+left);
    int slot=-1;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(take.getItem())){slot=i;break;}
    if(slot<0)break;
    var got=org.villageastra.persistence.WorldJournal.takeAmount(l,id,pos,slot,chest.getItem(slot).copy(),Math.min(left,chest.getItem(slot).getCount()));
    if(got.isEmpty())break;left-=got.getCount();}
   if(left>0)return null;}
  t.putInt("fences",perimeter.size());t.putInt("camps",camps);t.putInt("placed",0);t.putInt("builtCamps",0);t.putInt("farms",farms.size());t.putInt("destroyed",0);t.putLong("breach",0);t.putLong("hunger",0);
  // AD-065: once the supplies are taken, each soldier takes a weapon from the barracks if one lies there; without one they fight with bare hands.
  for(var r:soldiers)GuardGoal.equip(l,attacker,barracks,r.id(),false);
  stamp(t,GATHERING,now);save(s,t);return t;
 }
 /** Square perimeter on the real surface around the target centre. */
 public static List<BlockPos> perimeter(ServerLevel l,SettlementData.Entry target){
  var out=new ArrayList<BlockPos>();var c=target.center();
  for(int i=-RING;i<=RING;i++)for(int side=0;side<4;side++){
   int x=switch(side){case 0->c.getX()+i;case 1->c.getX()+i;case 2->c.getX()-RING;default->c.getX()+RING;};
   int z=switch(side){case 0->c.getZ()-RING;case 1->c.getZ()+RING;case 2->c.getZ()+i;default->c.getZ()+i;};
   var ground=Expeditions.surface(l,x,z,c.getY());if(ground!=null&&!out.contains(ground.above()))out.add(ground.above());
  }
  return out;
 }
 /** SIEGE-102: a farm works while its field really grows food and a yard while it really holds animals; mills and bakeries are stores, not fields. */
 public static List<Settlement.Building> workingFarms(ServerLevel l,SettlementData.Entry target){
  var out=new ArrayList<Settlement.Building>();
  for(var b:target.settlement().buildings()){
   // AD-104/AD-112: the field is the modules the farm works (its core's level within its land); it works while module_soil plots per worked module still hold soil or a crop.
   if(b.type().equals("farm")){int soil=0;
    for(var cell:FarmField.workedCells(l,target,b)){if(!l.hasChunkAt(cell))continue;
     if(l.getBlockState(cell.below()).is(Blocks.FARMLAND)||l.getBlockState(cell).getBlock() instanceof net.minecraft.world.level.block.CropBlock)soil++;}
    if(soil>=FarmField.workingSoil(l,target,b))out.add(b);}
   else if(b.type().equals("livestock")&&!LivestockGoal.herd(l,target,b,net.minecraft.world.entity.animal.Animal.class).isEmpty())out.add(b);}
  return out;
 }
 /** The army carries its field stock in the campaign record: one place for the goods, spent block by block. */
 public static int supply(CompoundTag army,String kind){return army.getCompound("supply").getInt(kind);}
 private static boolean spend(CompoundTag army,String kind){var supply=army.getCompound("supply");int have=supply.getInt(kind);if(have<=0)return false;supply.putInt(kind,have-1);army.put("supply",supply);return true;}
 /** One soldier standing at the line places one real fence section from the carried stock. */
 public static boolean placeSection(ServerLevel l,CompoundTag army,ResidentEntity soldier,BlockPos at){
  if(soldier!=null&&soldier.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>36)return false;
  if(!l.getBlockState(at).isAir()||OwnershipEvents.disallowedPlacement(l,at))return false;
  var below=at.below();if(!l.getBlockState(below).isFaceSturdy(l,below,net.minecraft.core.Direction.UP))return false;
  if(supply(army,"fences")<=0||!l.setBlock(at,Blocks.OAK_FENCE.defaultBlockState(),3))return false;
  spend(army,"fences");army.putInt("placed",army.getInt("placed")+1);
  var ring=army.getList("ringPositions",Tag.TAG_LONG);ring.add(LongTag.valueOf(at.asLong()));army.put("ringPositions",ring);
  if(soldier!=null)soldier.swing(net.minecraft.world.InteractionHand.MAIN_HAND);save(l.getServer(),army);return true;
 }
 /** A field camp: a campfire of this army at the ring; it never becomes civilian territory. */
 public static boolean placeCamp(ServerLevel l,CompoundTag army,ResidentEntity soldier,BlockPos at){
  if(soldier!=null&&soldier.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>36)return false;
  if(!l.getBlockState(at).isAir()||OwnershipEvents.disallowedPlacement(l,at)||!l.getBlockState(at.below()).isFaceSturdy(l,at.below(),net.minecraft.core.Direction.UP))return false;
  if(supply(army,"campfires")<=0||!l.setBlock(at,Blocks.CAMPFIRE.defaultBlockState(),3))return false;
  spend(army,"campfires");army.putInt("builtCamps",army.getInt("builtCamps")+1);
  var camps=army.getList("campPositions",Tag.TAG_LONG);camps.add(LongTag.valueOf(at.asLong()));army.put("campPositions",camps);save(l.getServer(),army);return true;
 }
 /** AD-104: where a campaign sets its charges on a working farm: the aims of its field, never on or over its water (TNT in water breaks nothing); a yard has no field, so where its herd stands. */
 public static List<BlockPos> aims(ServerLevel l,SettlementData.Entry target,Settlement.Building farm){
  if(farm.type().equals("farm"))return FarmField.aims(l,target,farm);
  var out=new ArrayList<BlockPos>();for(var a:LivestockGoal.herd(l,target,farm,net.minecraft.world.entity.animal.Animal.class))out.add(a.blockPosition());
  if(out.isEmpty())out.add(LogisticsRoutes.position(target,farm));return out;
 }
 /** Explosive destruction of one working farm: real TNT from the carried stock and a real explosion. AD-104: each charge goes on the next aim, counted per farm in the campaign record. */
 public static boolean blowUpFarm(ServerLevel l,CompoundTag army,SettlementData.Entry target,Settlement.Building farm){
  if(supply(army,"tnt")<=0)return false;
  var set=army.getCompound("aimed");var key=farm.id().toString();var aims=aims(l,target,farm);
  var at=aims.get(Math.floorMod(set.getInt(key),aims.size()));if(!l.hasChunkAt(at))return false;
  var tnt=new net.minecraft.world.entity.item.PrimedTnt(l,at.getX()+.5,at.getY(),at.getZ()+.5,null);tnt.setFuse(20);
  if(!l.addFreshEntity(tnt))return false;spend(army,"tnt");set.putInt(key,set.getInt(key)+1);army.put("aimed",set);army.putInt("charges",army.getInt("charges")+1);save(l.getServer(),army);return true;
 }
 /** Where the last closure check escaped the ring, for the interface and for tests. */
 public static volatile String lastEscape="";
 /** Closure by a real surface search: from the target centre nothing may walk past the ring. */
 public static boolean closed(ServerLevel l,SettlementData.Entry target){
  var start=Expeditions.surface(l,target.center().getX(),target.center().getZ(),target.center().getY());if(start==null){lastEscape="no ground at the centre";return false;}
  lastEscape="";var seen=new HashSet<Long>();var parents=new HashMap<Long,Long>();var queue=new ArrayDeque<BlockPos>();queue.add(start.above());seen.add(start.above().asLong());
  while(!queue.isEmpty()){var at=queue.poll();
   if(Math.abs(at.getX()-target.center().getX())>RING||Math.abs(at.getZ()-target.center().getZ())>RING){var trail=new StringBuilder(lastEscape+" | ");var step=at.asLong();for(int i=0;i<8&&parents.containsKey(step);i++){step=parents.get(step);trail.append(BlockPos.of(step).toShortString()).append(" <- ");}
    lastEscape="escaped at "+at.toShortString()+" after "+seen.size()+" cells from "+start.toShortString()+" trail "+trail;return false;}
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){
    // A walking step: one block up, at most three down; nothing may slip under the line.
    int x=at.getX()+d.getStepX(),z=at.getZ()+d.getStepZ();var ground=Expeditions.surface(l,x,z,at.getY()-1,1,3);
    if(ground==null)continue;var cell=ground.above();
    if(!seen.add(cell.asLong()))continue;parents.put(cell.asLong(),at.asLong());
    if(lastEscape.isEmpty()&&(Math.abs(cell.getX()-target.center().getX())>6||Math.abs(cell.getZ()-target.center().getZ())>6))lastEscape="first outside "+cell.toShortString()+" from "+at.toShortString()+" step "+d;
    if(seen.size()>16384){lastEscape="area too large from "+at.toShortString();return false;}
    queue.add(cell);}
  }
  lastEscape="";return true;
 }
 /** Sector cover: living soldiers standing near their part of the ring, otherwise the siege does not hold. */
 public static int cover(ServerLevel l,CompoundTag army,SettlementData.Entry target){
  int covered=0;
  for(int sector=0;sector<4;sector++){int have=0;
   for(var raw:army.getList("soldiers",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(raw);
    if(!(l.getEntity(id) instanceof ResidentEntity npc)||!npc.isAlive())continue;
    double dx=npc.getX()-target.center().getX(),dz=npc.getZ()-target.center().getZ();double angle=Math.atan2(dz,dx);int s=(int)Math.floor((angle+Math.PI)/(Math.PI/2))%4;
    if(s==sector&&Math.max(Math.abs(dx),Math.abs(dz))>=RING-8&&Math.max(Math.abs(dx),Math.abs(dz))<=RING+8)have++;}
   if(have>=SECTOR_SOLDIERS)covered++;}
  return covered;
 }
 /** Entry conditions of Q77: a closed ring, no working farms left and real cover. */
 public static String ready(ServerLevel l,CompoundTag army){
  var data=SettlementData.get(l.getServer());var target=data.entry(army.getUUID("target"));if(target==null)return "no_target";
  if(l.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return "peaceful";
  if(!closed(l,target))return "ring_open";
  if(!workingFarms(l,target).isEmpty())return "farms_working";
  if(cover(l,army,target)<4)return "no_cover";
  return "";
 }
 public static boolean begin(ServerLevel l,CompoundTag army,long now){
  if(!ready(l,army).isEmpty())return false;stamp(army,BESIEGING,now);army.putLong("breach",0);save(l.getServer(),army);
  // AD-100: a siege laid is the worst thing one village can do to another short of taking it.
  Relations.change(l.getServer(),army.getUUID("attacker"),army.getUUID("target"),-30,"siege");return true;}
 /** Held while the ring and the cover are real; a breach beyond the tolerance lifts the siege. */
 /** AD-111: whether the besieged village's stored food is under the starvation line, read from its real pantries touch-loaded now (relevance is ignored: surrender cannot be undone);
  *  null when the touch is deferred by the budget or failed, so the caller waits instead of reading an unloaded pantry as empty. */
 public static Boolean starving(ServerLevel l,SettlementData.Entry target){
  if(TouchLoad.ensureAll(l,Population.pantryPositions(target))!=TouchLoad.Touch.OK)return null;
  long alive=target.settlement().residents().stream().filter(Resident::alive).count();return alive>0&&Population.storedNutrition(l,target)<alive*STARVATION;
 }
 public static String tick(ServerLevel l,CompoundTag army,long now){
  var data=SettlementData.get(l.getServer());var target=data.entry(army.getUUID("target"));if(target==null){stamp(army,CLOSED,now);save(l.getServer(),army);return CLOSED;}
  var state=army.getString("state");
  // AD-065: an army that has lost every soldier goes nowhere — it withdraws instead of hanging on for ever.
  if(!state.equals(CLOSED)&&!state.equals(WITHDRAWN)&&!state.equals(SURRENDERED)&&Battle.living(l,army)==0){stamp(army,WITHDRAWN,now);army.putString("reason","no_soldiers");save(l.getServer(),army);return WITHDRAWN;}
  // The campaign advances on real work: muster, march, sections in the world, then the mayor order (an NPC mayor uses the same server check).
  if(state.equals(GATHERING)){stamp(army,MARCHING,now);save(l.getServer(),army);return MARCHING;}
  if(state.equals(MARCHING)&&army.getInt("placed")>0){stamp(army,ENCIRCLING,now);save(l.getServer(),army);return ENCIRCLING;}
  if(state.equals(ENCIRCLING)&&ready(l,army).isEmpty()){stamp(army,READY,now);save(l.getServer(),army);return READY;}
  if(state.equals(READY)){
   var attacker=data.entry(army.getUUID("attacker"));
   // AD-159 VI: an army sent to take a town on its own lays the siege itself - or turns back when the defenders are stronger.
   if(army.getBoolean("auto")&&Army.defence(l,target)>Army.strength(l,army)){stamp(army,WITHDRAWN,now);army.putString("reason","defenders_stronger");save(l.getServer(),army);return WITHDRAWN;}
   if(attacker!=null&&(attacker.settlement().governance().playerMayor()==null||army.getBoolean("auto"))&&begin(l,army,now))return BESIEGING;
   return READY;}
  if(!state.equals(BESIEGING))return state;
  String reason=ready(l,army);
  if(!reason.isEmpty()&&!reason.equals("farms_working")){
   long breach=army.getLong("breach")==0?now:army.getLong("breach");army.putLong("breach",breach);
   if(now-breach>=BREACH_TOLERANCE){stamp(army,WITHDRAWN,now);army.putString("reason",reason);save(l.getServer(),army);return WITHDRAWN;}
   save(l.getServer(),army);return BESIEGING;}
  army.putLong("breach",0);
  // AD-111: the target's pantries are touch-loaded first; a touch that cannot happen this tick neither starts nor clears the hunger.
  var hungry=starving(l,target);if(hungry==null){save(l.getServer(),army);return BESIEGING;}
  if(hungry){
   long hunger=army.getLong("hunger")==0?now:army.getLong("hunger");army.putLong("hunger",hunger);
   if(now-hunger>=SURRENDER_TICKS){stamp(army,SURRENDERED,now);save(l.getServer(),army);Annexation.conquer(l.getServer(),target,army.getUUID("attacker"),now);return SURRENDERED;}}
  else army.putLong("hunger",0);
  save(l.getServer(),army);return BESIEGING;
 }
}
