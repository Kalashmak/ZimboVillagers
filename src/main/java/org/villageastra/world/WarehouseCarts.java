package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ChunkPos;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.SettlementData;
/** AD-147 §2.2 (CF-I, CF-K, CF-O): the carts of a warehouse. Every courier of a level-II warehouse with Logistics II has a cart of the warehouse
 *  (V: a second one for its wolf; VI: one for every wolf team). A cart comes only from the warehouse's order: it asks for the carts it lacks
 *  (Workshops.wants, need class 5) - the carpentry or the hall with Engineering II makes one, a porter brings it - and a cart item in its
 *  chest is set down on a bay once: the record carts-&lt;warehouse&gt;.bin names it (placing), the journal takes the item, the entity is made
 *  with that UUID (home = the warehouse), all in one journal batch. A crash between them is finished from the record, never repeated: the
 *  entity is looked for only where the entities of its chunk are loaded (in 1.20 they load apart from the blocks). A player's own cart is
 *  never taken on. A cart is lost only when it was broken (CartEntity.remove) or its chunk's entities are loaded and it is not there; a cart
 *  left somewhere (its courier died, the way was blocked) is fetched back by the next courier, never replaced. */
public final class WarehouseCarts {
 private WarehouseCarts(){}
 public static final String CARTS_RESEARCH="logistics.2",HALL_CARTS="engineering.2";
 /** A cart this far from the warehouse, standing, is fetched back before any trip. */
 public static final double HOME=24;
 static Path path(ServerLevel l,UUID warehouse){return WarehouseTrips.dir(l).resolve("carts-"+warehouse+".bin");}
 static CompoundTag read(ServerLevel l,UUID warehouse){var p=path(l,warehouse);if(!Files.exists(p)){var t=new CompoundTag();t.putInt("schema",1);t.put("carts",new ListTag());return t;}
  var t=NbtRecord.read(p);if(t.getInt("schema")!=1)throw new IllegalStateException("Invalid warehouse cart record");return t;}
 static void write(ServerLevel l,UUID warehouse,CompoundTag t){NbtRecord.write(path(l,warehouse),t);}
 /** Whether the village makes carts: a carpentry (or its annex) or the hall with Engineering II (CF-O). */
 public static boolean maker(ServerLevel l,SettlementData.Entry e){
  return e.settlement().buildings().stream().anyMatch(b->b.type().equals("carpentry")||b.type().equals("carpentry_annex"))||Workshops.hall(e)!=null&&ResearchKnobs.done(l,e).contains(HALL_CARTS);}
 /** Whether the warehouse's couriers take carts: level II and Logistics II. */
 public static boolean open(ServerLevel l,SettlementData.Entry e,Settlement.Building b){return BuildingLevels.level(l,e,b)>=2&&ResearchKnobs.done(l,e).contains(CARTS_RESEARCH);}
 /** Carts the warehouse needs: one a courier posted (a second one at V while it has a wolf), one a wolf team at VI. */
 public static int needed(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(!open(l,e,b))return 0;int couriers=WarehouseTrips.posted(e,b);int n=couriers;
  if(BuildingLevels.level(l,e,b)==5)n+=Math.min(couriers,CartWolves.available(l,e,b).size()+WarehouseTrips.wolves(l,e,b));
  n+=CartWolves.teams(l,e,b);return n;}
 /** The cart records of the warehouse: {id, pos, state (placing, placed, lost)}. */
 public static ListTag records(ServerLevel l,UUID warehouse){return read(l,warehouse).getList("carts",Tag.TAG_COMPOUND);}
 /** Carts the warehouse has: placed or being placed. */
 public static int have(ServerLevel l,UUID warehouse){int n=0;for(var raw:records(l,warehouse))if(!((CompoundTag)raw).getString("state").equals("lost"))n++;return n;}
 /** The warehouse's own carts loaded in the world now. */
 public static List<CartEntity> carts(ServerLevel l,Settlement.Building b){var out=new ArrayList<CartEntity>();
  for(var raw:records(l,b.id())){var r=(CompoundTag)raw;if(r.getString("state").equals("lost"))continue;if(l.getEntity(r.getUUID("id")) instanceof CartEntity c&&c.isAlive())out.add(c);}return out;}
 /** A cart of the warehouse standing (not travelling, nobody pulling) far from it: fetched back before any other trip (CF-K). */
 public static CartEntity stranded(ServerLevel l,SettlementData.Entry e,Settlement.Building b){var at=LogisticsRoutes.position(e,b);
  for(var c:carts(l,b))if(!c.travelling()&&c.puller()==null&&c.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)>HOME*HOME)return c;return null;}
 /** A free cart of the warehouse at home: not travelling, nobody pulling it, other than {@code not}; the nearest to its first bay. */
 public static CartEntity free(ServerLevel l,SettlementData.Entry e,Settlement.Building b,UUID not){var at=WarehouseStore.at(e,b,WarehouseStore.B1);
  return carts(l,b).stream().filter(c->!c.travelling()&&c.puller()==null&&!c.getUUID().equals(not)&&c.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5)<=HOME*HOME)
   .min(Comparator.comparingDouble(c->c.distanceToSqr(at.getX()+.5,at.getY(),at.getZ()+.5))).orElse(null);}
 /** CartEntity.remove: a cart of a warehouse broken or killed is lost (its record says so; a new one is ordered). */
 static void lost(ServerLevel l,CartEntity cart){var t=read(l,cart.home());boolean changed=false;
  for(var raw:t.getList("carts",Tag.TAG_COMPOUND)){var r=(CompoundTag)raw;if(r.getUUID("id").equals(cart.getUUID())&&!r.getString("state").equals("lost")){r.putString("state","lost");changed=true;}}
  if(changed)write(l,cart.home(),t);}
 /** Warehouses.tick (every 100 ticks): keeps the records true and sets down a cart the warehouse lacks from a cart item in its chest. */
 public static void ensure(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var t=read(l,b.id());var list=t.getList("carts",Tag.TAG_COMPOUND);boolean changed=false;
  for(var raw:list){var r=(CompoundTag)raw;var id=r.getUUID("id");String state=r.getString("state");if(state.equals("lost"))continue;var pos=BlockPos.of(r.getLong("pos"));
   var entity=l.getEntity(id);
   if(entity instanceof CartEntity c&&c.isAlive()){if(!c.blockPosition().equals(pos)){r.putLong("pos",c.blockPosition().asLong());changed=true;}if(state.equals("placing")){r.putString("state","placed");changed=true;}continue;}
   if(!l.hasChunkAt(pos)||!l.areEntitiesLoaded(ChunkPos.asLong(pos)))continue;
   // The entities where it stood are loaded and it is not there: a cart set down before a crash is set down now (its take is replayed,
   // never repeated); a placed one is gone (CF-I b).
   if(state.equals("placing")){if(place(l,e,b,r))changed=true;}
   else{r.putString("state","lost");changed=true;}}
  if(changed)write(l,b.id(),t);
  if(have(l,b.id())>=needed(l,e,b))return;
  var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return;int slot=-1;
  for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(VillageAstra.CART_ITEM.get())){slot=i;break;}
  if(slot<0)return;
  BlockPos bay=null;for(var local:WarehouseStore.bays(BuildingLevels.level(l,e,b))){var at=WarehouseStore.at(e,b,local);if(l.hasChunkAt(at)&&l.areEntitiesLoaded(ChunkPos.asLong(at))&&CartHitch.near(l,e,at,1.5,c->true)==null){bay=at;break;}}
  if(bay==null)return;
  var r=new CompoundTag();var id=UUID.randomUUID();r.putUUID("id",id);r.putLong("pos",bay.asLong());r.putString("state","placing");r.putInt("slot",slot);
  var fresh=read(l,b.id());fresh.getList("carts",Tag.TAG_COMPOUND).add(r);write(l,b.id(),fresh);
  var record=read(l,b.id());for(var raw:record.getList("carts",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getUUID("id").equals(id)){if(place(l,e,b,(CompoundTag)raw))write(l,b.id(),record);break;}
 }
 /** Takes the cart item through the journal and sets the cart down with the record's UUID, in one batch; true when it stands. */
 private static boolean place(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag r){
  var id=r.getUUID("id");var pos=BlockPos.of(r.getLong("pos"));var op=Settlement.childId(b.id(),"cart/"+id);var chestAt=LogisticsRoutes.position(e,b);
  return WorldJournal.batch(l,()->{
   ItemStack taken;
   if(WorldJournal.exists(l,op))taken=WorldJournal.recoverAmount(l,op);
   else{var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)return false;int slot=r.getInt("slot");if(slot<0||slot>=chest.getContainerSize()||!chest.getItem(slot).is(VillageAstra.CART_ITEM.get())){slot=-1;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(VillageAstra.CART_ITEM.get())){slot=i;break;}}
    if(slot<0){r.putString("state","lost");return true;}
    taken=WorldJournal.takeAmount(l,op,chestAt,slot,chest.getItem(slot).copy(),1);}
   if(taken.isEmpty()){r.putString("state","lost");return true;}
   if(l.getEntity(id) instanceof CartEntity){r.putString("state","placed");return true;}
   var cart=new CartEntity(l,pos,e.settlement().id());cart.setUUID(id);cart.home(b.id());
   if(!l.addFreshEntity(cart))return false;
   r.putString("state","placed");return true;});
 }
 /** The carts the warehouses of a village lack, as its demand (need class 5): one a missing cart, less the cart items already in its chest. */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e){var out=new ArrayList<Workshops.Want>();
  for(var b:e.settlement().buildings()){if(!WarehouseStore.is(b))continue;int need=needed(l,e,b);if(need<=0)continue;int missing=need-have(l,b.id());if(missing<=0)continue;
   var chest=LogisticsRoutes.chest(l,e,b);if(chest==null)continue;missing-=LogisticsRoutes.count(chest,s->s.is(VillageAstra.CART_ITEM.get()));
   if(missing>0)out.add(new Workshops.Want(Ingredient.of(VillageAstra.CART_ITEM.get()),missing,b.id(),LogisticsRoutes.NEED_WAYS));}
  return out;}
}
