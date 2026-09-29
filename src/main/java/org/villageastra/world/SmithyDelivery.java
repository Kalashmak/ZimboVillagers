package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-155 (owner's Metallurgy ladder III–V): what the smithy makes goes to whoever needs it. III–IV: the smithy posts a courier (a porter,
 *  staff.json smithy "porter") who carries what the workshops and workers want from the smithy's chest straight to them; a worker's tool goes
 *  into its workplace's own chest (ResourceWorkGoal takes it there, not at the hall) and the smith no longer leaves the anvil to carry.
 *  V–VI: no courier; a free wolf of the kennel (VillageDogs, AD-147 contract: send/near/release, only a source that pulls) carries one
 *  thing at a time. A wolf trip is one record per smithy (data/astra-smithy/&lt;smithy&gt;.bin); the take and the put go through the journal;
 *  a wolf lost with its load gives it back to the smithy's chest (nothing is lost or copied). */
public final class SmithyDelivery {
 private SmithyDelivery(){}
 public static final String TYPE="smithy";
 public static final int COURIER=3,WOLVES=5;
 /** Ticks a wolf may fail to reach its stop before the trip is given up. */
 static final int LOST_TICKS=1200;
 /** The village's smithy of the best working level, or null. */
 public static Settlement.Building smithy(ServerLevel l,SettlementData.Entry e){
  Settlement.Building best=null;int top=0;for(var b:e.settlement().buildings())if(post(b)){int lv=BuildingLevels.level(l,e,b);if(lv>top){top=lv;best=b;}}return best;
 }
 public static boolean post(Settlement.Building b){return b!=null&&TYPE.equals(CoreCatalog.canonical(b.type()));}
 /** Courier posts of a smithy at this level (the porters of its Staff row). */
 public static int posts(int level){return Staff.slots(TYPE,"porter",level);}
 /** A porter posted at a smithy. */
 public static boolean courier(SettlementData.Entry e,UUID resident){var r=e.settlement().resident(resident);return r!=null&&r.alive()&&r.profession()==Profession.PORTER&&post(e.settlement().workplace(resident));}
 private static boolean posted(SettlementData.Entry e,Settlement.Building b){return e.settlement().residents().stream().anyMatch(r->courier(e,r.id())&&b.equals(e.settlement().workplace(r.id())));}
 /** Whether the smithy carries to whoever needs it now: III–IV with its courier posted, V–VI with a wolf free or on a trip. */
 public static boolean delivers(ServerLevel l,SettlementData.Entry e){
  var b=smithy(l,e);if(b==null)return false;int lv=BuildingLevels.level(l,e,b);
  if(lv>=WOLVES)return VillageDogs.pulls()&&(active(inspect(l,b.id()))||!VillageDogs.available(l,e).isEmpty());
  return lv>=COURIER&&posted(e,b);
 }
 /** The next thing the smithy's courier (or wolf) carries: a want met from the smithy's chest only, at most {@code load}. */
 public static LogisticsRoutes.Route route(ServerLevel l,SettlementData.Entry e,Settlement.Building smithy,int load){return LogisticsRoutes.from(l,e,smithy,load);}

 // ---------------------------------------------------------------- the wolves of V–VI
 private static Path path(ServerLevel l,UUID smithy){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-smithy/"+smithy+".bin");}
 public static CompoundTag inspect(ServerLevel l,UUID smithy){var p=path(l,smithy);if(!Files.exists(p))return new CompoundTag();var t=NbtRecord.read(p);return t.getInt("schema")==1&&t.hasUUID("id")&&t.hasUUID("wolf")&&t.hasUUID("destination")?t:new CompoundTag();}
 private static boolean active(CompoundTag t){return !t.isEmpty()&&!t.getString("stage").equals("complete");}
 private static void save(ServerLevel l,UUID smithy,CompoundTag t){NbtRecord.write(path(l,smithy),t);}
 private static UUID op(CompoundTag t,String kind){return Settlement.childId(t.getUUID("id"),kind);}
 /** What a wolf trip holds: the item taken and not yet put down. */
 private static ItemStack held(ServerLevel l,CompoundTag t){if(WorldJournal.recoverExisting(l,op(t,"put"))!=null||WorldJournal.recoverExisting(l,op(t,"back"))!=null)return ItemStack.EMPTY;return WorldJournal.recoverAmount(l,op(t,"take"));}
 /** PorterWork.reserved: the wolf trips' things coming into ({@code in}) or not yet taken from a building. */
 public static int reserved(ServerLevel l,SettlementData.Entry e,UUID building,Predicate<ItemStack> matches,boolean in){int n=0;
  for(var b:e.settlement().buildings()){if(!post(b))continue;var t=inspect(l,b.id());if(!active(t))continue;var item=ItemStack.of(t.getCompound("item"));if(!matches.test(item))continue;
   if(in&&t.getUUID("destination").equals(building)&&!t.getString("stage").equals("back"))n+=item.getCount();
   else if(!in&&b.id().equals(building)&&t.getString("stage").equals("fetch"))n+=item.getCount();}
  return n;
 }
 /** Population.tick (every 20 ticks): the smithy's wolf trip, and a new one for a free wolf when something is wanted. */
 public static void tick(ServerLevel l,SettlementData.Entry e){
  var b=smithy(l,e);if(b==null)return;var t=inspect(l,b.id());
  if(active(t)){wolf(l,e,b,t);return;}
  if(BuildingLevels.level(l,e,b)<WOLVES||!VillageDogs.pulls())return;
  var free=VillageDogs.available(l,e);if(free.isEmpty())return;
  var route=route(l,e,b,1);if(route==null)return;
  var wolf=free.get(0);if(!VillageDogs.send(l,e,wolf,LogisticsRoutes.position(e,b)))return;
  t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("wolf",wolf);t.putUUID("destination",route.destination().id());
  t.put("item",route.item().copyWithCount(1).save(new CompoundTag()));t.putString("stage","fetch");save(l,b.id(),t);
 }
 private static void wolf(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var wolf=t.getUUID("wolf");var dest=e.settlement().buildings().stream().filter(x->x.id().equals(t.getUUID("destination"))).findFirst().orElse(null);
  var body=l.getEntity(wolf);boolean dead=body!=null&&!body.isAlive()||body instanceof net.minecraft.world.entity.LivingEntity le&&le.isDeadOrDying();
  var stage=t.getString("stage");var home=LogisticsRoutes.position(e,b);
  // A wolf dead, or away from its stop too long, or a destination gone: what it holds goes back to the smithy's chest.
  if(dead||dest==null&&stage.equals("go")||t.getInt("away")>=LOST_TICKS){if(!held(l,t).isEmpty()){t.putString("stage","back");}else{finish(l,e,b,t);return;}}
  stage=t.getString("stage");
  if(stage.equals("back")){var item=held(l,t);if(item.isEmpty()||WorldJournal.deposit(l,op(t,"back"),home,item))finish(l,e,b,t);else save(l,b.id(),t);return;}
  if(stage.equals("fetch")){
   if(!VillageDogs.near(l,e,wolf,home,3)){VillageDogs.send(l,e,wolf,home);t.putInt("away",t.getInt("away")+20);save(l,b.id(),t);return;}
   var want=ItemStack.of(t.getCompound("item"));var got=WorldJournal.recoverAmount(l,op(t,"take"));
   if(got.isEmpty()&&!WorldJournal.exists(l,op(t,"take"))){var c=LogisticsRoutes.chest(l,e,b);
    if(c!=null)for(int slot=0;slot<c.getContainerSize();slot++)if(ItemStack.isSameItemSameTags(c.getItem(slot),want)){got=WorldJournal.takeAmount(l,op(t,"take"),home,slot,c.getItem(slot).copy(),1);break;}}
   if(got.isEmpty()){finish(l,e,b,t);return;}
   t.putString("stage","go");t.putInt("away",0);save(l,b.id(),t);return;}
  if(stage.equals("go")){var at=LogisticsRoutes.position(e,dest);
   if(!VillageDogs.near(l,e,wolf,at,3)){VillageDogs.send(l,e,wolf,at);t.putInt("away",t.getInt("away")+20);save(l,b.id(),t);return;}
   var item=held(l,t);
   // A full chest at the stop: the wolf brings it back.
   if(item.isEmpty()||WorldJournal.deposit(l,op(t,"put"),at,item)){finish(l,e,b,t);return;}
   t.putString("stage","back");t.putInt("away",0);save(l,b.id(),t);}
 }
 private static void finish(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){VillageDogs.release(l,e,t.getUUID("wolf"));t.putString("stage","complete");save(l,b.id(),t);}
}
