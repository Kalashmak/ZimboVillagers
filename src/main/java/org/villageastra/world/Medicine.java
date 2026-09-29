package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-152 (owner's Medicine ladder): residents sometimes fall ill and are healed only by the village's medicine. I–III: the hospital (the
 *  clinic) — the sick walk there (PatientGoal) and lie while a medic is at work, as many at once as it has beds (1/2/4/6/8/12), each cured
 *  after cure_ticks with one real bandage from the clinic chest; IV: the two medics also walk to the sick at home (DoctorGoal); V: the residents
 *  carry a paid dose collected at the counter or delivered by a kennel wolf (MedicineDelivery); VI: everyone is healed without medics — the sick at
 *  once, the hurt a little every pass. Illness comes to every village; only the cure needs the clinic. balance/medicine.json. */
public final class Medicine {
 private Medicine(){}
 public static final int SICK_PERMILLE,CURE_TICKS,VISITS,MEDICINE,AUTOMATIC;
 static{
  JsonObject o;try(var s=Medicine.class.getResourceAsStream("/data/villageastra/balance/medicine.json")){if(s==null)throw new IllegalStateException("Missing medicine balance");o=JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
  SICK_PERMILLE=o.get("sick_permille_per_day").getAsInt();CURE_TICKS=o.get("cure_ticks").getAsInt();VISITS=o.get("visits_level").getAsInt();MEDICINE=o.get("medicine_level").getAsInt();AUTOMATIC=o.get("automatic_level").getAsInt();
  if(SICK_PERMILLE<0||SICK_PERMILLE>1000||CURE_TICKS<20||!(VISITS<=MEDICINE&&MEDICINE<=AUTOMATIC))throw new IllegalStateException("Inconsistent medicine balance");
 }
 /** Patients a hospital lies at once at this working level (core effect beds). */
 public static int beds(int level){return CoreEffects.value("clinic","beds",level);}
 /** Blocks from the clinic's station a patient counts as lying in it. */
 public static final int WARD=6;
 private static final Map<UUID,Long> DAY=new HashMap<>();
 private static final Map<UUID,long[]> LYING=new HashMap<>();
 /** The village's clinic of the best working level, or null. */
 public static Settlement.Building clinic(ServerLevel l,SettlementData.Entry e){
  Settlement.Building best=null;int top=0;for(var b:e.settlement().buildings())if(b.type().equals("clinic")){int lv=BuildingLevels.level(l,e,b);if(lv>top){top=lv;best=b;}}return best;
 }
 /** The ill fall ill: whether this resident falls ill on this village day (a pure roll of its id and the day). */
 public static boolean fallsIll(UUID resident,long day){return Math.floorMod(Objects.hash(resident,day),1000)<SICK_PERMILLE;}
 /** A medic of this clinic at work in it now. */
 static boolean medicAt(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic){
  var at=LogisticsRoutes.position(e,clinic);
  for(var r:e.settlement().residents())if(r.alive()&&r.profession()==Profession.DOCTOR&&Population.mayWork(r)&&clinic.equals(e.settlement().workplace(r.id()))&&l.getEntity(r.id()) instanceof ResidentEntity npc
    &&npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=WARD*WARD*4)return true;
  return false;
 }
 /** One real bandage from the clinic chest for this cure (a journal id of the patient and the time: never twice). */
 public static boolean bandage(ServerLevel l,SettlementData.Entry e,Settlement.Building clinic,UUID patient,String what){
  var chest=LogisticsRoutes.chest(l,e,clinic);if(chest==null)return false;var id=Settlement.childId(patient,"medicine/"+what);
  var got=WorldJournal.recoverAmount(l,id);
  if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(VillageAstra.BANDAGE.get())){got=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,clinic),slot,chest.getItem(slot).copy(),1);break;}
  return !got.isEmpty();
 }
 /** Where the sick of this village lie, or null (no clinic, or its medicine works without a hospital bed: V and VI). */
 public static BlockPos ward(ServerLevel l,SettlementData.Entry e){
  var c=clinic(l,e);if(c==null||BuildingLevels.level(l,e,c)>=MEDICINE)return null;return LogisticsRoutes.position(e,c);
 }
 /** One pass (every 20 ticks of the village clock): the day's illness, then the cure the clinic's level gives. True when something changed. */
 public static boolean tick(ServerLevel l,SettlementData.Entry e,long now){
  boolean changed=false;long day=now/24000L;var s=e.settlement();
  var last=DAY.get(s.id());
  if(last==null||last!=day){DAY.put(s.id(),day);if(last!=null)for(var r:s.residents())if(r.alive()&&fallsIll(r.id(),day)&&r.fallIll())changed=true;}
  MedicineDelivery.tick(l,e);
  var clinic=clinic(l,e);int level=clinic==null?0:BuildingLevels.level(l,e,clinic);
  if(level>=AUTOMATIC){
   for(var r:s.residents())if(r.sick()&&r.cure())changed=true;
   for(var r:s.residents())if(r.alive()&&l.getEntity(r.id()) instanceof ResidentEntity npc&&npc.getHealth()<npc.getMaxHealth())npc.heal(1F);
   return changed;}
  for(var r:s.residents())if(MedicinePacks.use(l,r))changed=true;
  if(clinic==null)return changed;
  if(level>=MEDICINE){
   return changed;}
  // The hospital: the sick lying in its ward while a medic works there, as many as it has beds, in a fixed order (by id).
  // A sick sole medic may treat only themself in the ward, at the usual bed time and cost.
  boolean staffed=medicAt(l,e,clinic);
  var at=LogisticsRoutes.position(e,clinic);var lying=new ArrayList<Resident>();
  for(var r:s.residents())if(r.alive()&&r.sick()&&(staffed||r.profession()==Profession.DOCTOR&&clinic.equals(s.workplace(r.id())))&&l.getEntity(r.id()) instanceof ResidentEntity npc&&npc.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=WARD*WARD)lying.add(r);
  lying.sort(Comparator.comparing(Resident::id));
  for(var r:lying.subList(0,Math.min(beds(level),lying.size()))){
   var t=LYING.computeIfAbsent(r.id(),k->new long[]{now,0});long step=Math.max(0,Math.min(200,now-t[0]));t[0]=now;t[1]+=step;
   if(t[1]>=CURE_TICKS&&bandage(l,e,clinic,r.id(),"ward/"+now)){r.cure();LYING.remove(r.id());changed=true;}}
  return changed;
 }
 /** Tests: forget the day counter and the ward's time of a village. */
 public static void forget(UUID village){DAY.remove(village);}
}
