package org.villageastra.server;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** AD-128: a player gives a resident the item in the main hand. The gift is final and earns reputation in that resident's village in
 *  proportion to the item's value (gifts.json, else trade.json prices) × durability × enchantments × a repeat factor per kind; the village
 *  really gets it: armour is worn, tools and stock go to the hall chest, swords and bows to the guard posts. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class Gifts {
 private Gifts(){}
 public static final String WEAR="wear",HALL_TOOLS="hall_tools",GUARD_POST="guard_post",ARCHER_POST="archer_post",HALL="hall";
 /** Reasons that keep the item in the player's hand. */
 public static final Set<String> BLOCKING=Set.of("empty","container","not_a_gift","worn","tired","no_room");
 private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
 private static final JsonObject ROOT=root();
 private static final GiftValue.Rules RULES=rules(ROOT);
 private static Map<Item,Integer> values;
 private static final Map<UUID,Long> GAINED=new HashMap<>();
 private static JsonObject root(){try(var s=Gifts.class.getResourceAsStream("/data/villageastra/balance/gifts.json")){if(s==null)throw new IllegalStateException("Missing gift balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static GiftValue.Rules rules(JsonObject o){
  if(o.get("schema").getAsInt()!=1)throw new IllegalStateException("Unknown gift balance schema");var e=o.getAsJsonObject("enchant");
  return new GiftValue.Rules(o.get("reputation_per_coin").getAsInt(),o.get("minimum_unit_coins").getAsInt(),o.get("full_repeats").getAsInt(),o.get("repeat_zero_after").getAsInt(),o.get("memory_recovery_per_period").getAsInt(),o.get("confirm_at").getAsInt(),
   e.get("normal_permille").getAsInt(),e.get("treasure_permille").getAsInt(),e.get("curse_permille").getAsInt(),e.get("max_permille").getAsInt(),e.get("min_permille").getAsInt());
 }
 public static GiftValue.Rules rules(){return RULES;}
 /** Resolved after registries are frozen; gifts and trade donations must share one reputation per coin. */
 private static synchronized Map<Item,Integer> values(){
  if(values!=null)return values;if(RULES.reputationPerCoin()!=Trade.DONATION_REPUTATION)throw new IllegalStateException("Gift reputation per coin differs from trade donations");
  var v=new LinkedHashMap<Item,Integer>();for(var e:ROOT.getAsJsonObject("values").entrySet()){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(e.getKey()));int coins=e.getValue().getAsInt();
   if(item==Items.AIR||coins<1||v.putIfAbsent(item,coins)!=null)throw new IllegalStateException("Invalid gift value "+e.getKey());}
  return values=Map.copyOf(v);
 }
 /** Coins per unit in permille; 0 when the item is not a gift (unlisted, the zindbo itself, or under one coin a unit). */
 public static long basePermille(ItemStack s){
  if(s.isEmpty()||s.is(VillageAstra.ZINDBO.get()))return 0;var own=values().get(s.getItem());long base;
  if(own!=null)base=own*1000L;else{var price=Trade.price(s.getItem());base=price==null?0:price.coins()*1000L/price.per();}
  return base<RULES.minimumUnitCoins()*1000L?0:base;
 }
 /** Enchantments that belong on this item (a book carries its stored ones); a command-enchanted block gets nothing. */
 public static List<GiftValue.Enchant> enchants(ItemStack s){
  var list=new ArrayList<GiftValue.Enchant>();boolean book=s.is(Items.ENCHANTED_BOOK);
  for(var e:EnchantmentHelper.getEnchantments(s).entrySet())if(book||e.getKey().canEnchant(s))list.add(new GiftValue.Enchant(e.getValue(),e.getKey().getMaxLevel(),e.getKey().isTreasureOnly(),e.getKey().isCurse()));
  return list;
 }
 public static int durability(ItemStack s){return GiftValue.durability(s.isDamageableItem(),s.getMaxDamage(),s.getDamageValue());}
 /** What one unit is worth as a first gift of its kind: also the theft weight of taking it out of a village chest (CF-1). */
 public static long unitValue(ItemStack s){long base=basePermille(s);return base==0?0:GiftValue.unit(RULES,base,durability(s),GiftValue.enchant(RULES,enchants(s)),1000);}
 public static long theftWeight(ItemStack s){return Math.max(1,unitValue(s));}
 private static boolean container(ItemStack s){
  var t=s.getTag();if(t==null)return false;
  return !t.getList("Items",Tag.TAG_COMPOUND).isEmpty()||t.contains("BlockEntityTag",Tag.TAG_COMPOUND)&&!t.getCompound("BlockEntityTag").getList("Items",Tag.TAG_COMPOUND).isEmpty();
 }
 private record Place(SettlementData.Entry entry,Resident resident,ServerLevel level){}
 /** The village of this resident, or null with the card's reason: gifts ignore trade refusals except hostility. */
 private static Place place(ServerPlayer p,ResidentEntity npc){
  if(npc.settlementId()==null||!(npc.level() instanceof ServerLevel l))return null;var e=SettlementData.get(p.server).entry(npc.settlementId());
  if(e==null||!e.dimension().equals(l.dimension().location().toString())||npc.getTarget()==p)return null;var r=e.settlement().resident(npc.getUUID());
  return r==null||!r.alive()?null:new Place(e,r,l);
 }
 private record Target(String destination,Container chest,BlockPos pos,EquipmentSlot slot,boolean room){}
 private static Container hallChest(ServerLevel l,SettlementData.Entry e){var hall=Workshops.hall(e);return hall==null?null:LogisticsRoutes.chest(l,e,hall);}
 private static float armour(ItemStack s){return s.getItem() instanceof ArmorItem a?a.getDefense()+a.getToughness():0;}
 private static boolean wearable(ItemStack s){return s.getItem() instanceof ArmorItem&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FROST_WALKER,s)==0&&EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SOUL_SPEED,s)==0;}
 private static Target post(ServerLevel l,SettlementData.Entry e,ResidentEntity npc,ItemStack gift,boolean archer){
  Target best=null;double distance=Double.MAX_VALUE;
  for(var b:e.settlement().buildings()){if(!(archer?b.type().equals("archery")||b.type().equals(Walls.TOWER):b.type().equals("guard_house")))continue;var c=LogisticsRoutes.chest(l,e,b);if(c==null||!fits(c,gift))continue;
   var pos=LogisticsRoutes.position(e,b);double d=pos.distSqr(npc.blockPosition());if(d<distance){distance=d;best=new Target(archer?ARCHER_POST:GUARD_POST,c,pos,null,true);}}
  return best;
 }
 /** Where the gift goes; a null chest means there is no room (the destination is still named for the card). */
 private static Target target(Place at,ResidentEntity npc,ItemStack gift){
  var l=at.level;var e=at.entry;var hall=hallChest(l,e);var hallPos=Workshops.hall(e)==null?null:LogisticsRoutes.position(e,Workshops.hall(e));
  if(gift.getItem() instanceof ArmorItem armour&&at.resident.life()==Resident.Life.ADULT&&wearable(gift)){
   var slot=armour.getEquipmentSlot();var w=GiftLedger.get(l.getServer()).worn(npc.getUUID());var old=w==null?ItemStack.EMPTY:w.slots().getOrDefault(slot,ItemStack.EMPTY);
   boolean better=old.isEmpty()||armour(gift)>armour(old)||armour(gift)==armour(old)&&GiftValue.enchant(RULES,enchants(gift))>GiftValue.enchant(RULES,enchants(old));
   if(better)return new Target(WEAR,hall,hallPos,slot,old.isEmpty()||hall!=null&&fits(hall,old));
  }
  if(gift.getItem() instanceof SwordItem||gift.getItem() instanceof BowItem){var t=post(l,e,npc,gift,gift.getItem() instanceof BowItem);if(t!=null)return t;}
  boolean tool=gift.is(net.minecraft.tags.ItemTags.PICKAXES)||gift.is(net.minecraft.tags.ItemTags.AXES)||gift.is(net.minecraft.tags.ItemTags.HOES);
  return new Target(tool?HALL_TOOLS:HALL,hall,hallPos,null,hall!=null&&fits(hall,gift));
 }
 public record Quote(String reason,String item,int count,int accepted,int free,long base,int durability,int enchant,long remembered,int firstRepeat,int lastRepeat,long reputation,String destination,boolean enchanted){
  public boolean blocked(){return BLOCKING.contains(reason);}
  public boolean confirm(){return reputation>=RULES.confirmAt()||enchanted;}
 }
 private static Quote refused(String reason,ItemStack s){return new Quote(reason,s.isEmpty()?"":BuiltInRegistries.ITEM.getKey(s.getItem()).toString(),s.getCount(),0,0,0,1000,1000,0,1000,1000,0,"",false);}
 /** The exact quote the card shows and the order re-checks; reads the ledgers without changing them. */
 public static Quote quote(ServerPlayer p,ResidentEntity npc,ItemStack s,long now){
  var at=place(p,npc);if(at==null)return null;
  if(s.isEmpty())return refused("empty",s);if(container(s))return refused("container",s);long base=basePermille(s);if(base==0)return refused("not_a_gift",s);
  var village=at.entry.settlement().id();var key=BuiltInRegistries.ITEM.getKey(s.getItem()).toString();var ench=enchants(s);int durability=durability(s),enchant=GiftValue.enchant(RULES,ench);
  long remembered=GiftLedger.get(p.server).remembered(village,p.getUUID(),key,now);int bought=TradeLedger.get(p.server).bought(village,p.getUUID(),key);
  var q=GiftValue.stack(RULES,base,durability,enchant,s.getCount(),bought,(int)Math.min(Integer.MAX_VALUE,remembered));
  boolean mayor=p.getUUID().equals(at.entry.settlement().governance().playerMayor());
  // Nothing is worth a unit: "worn" when even a first gift of the kind is worth 0 (durability/curses), "tired" only when repeats made it so.
  String reason=q.accepted()>0?"":GiftValue.unit(RULES,base,durability,enchant,1000)==0?"worn":"tired";var t=target(at,npc,s.copyWithCount(Math.max(1,q.accepted())));
  if(reason.isEmpty()&&!t.room)reason="no_room";
  if(reason.isEmpty()&&mayor)reason="mayor";if(reason.isEmpty()&&q.free()==q.accepted())reason="bought";
  long rep=mayor?0:q.reputation();
  return new Quote(reason,key,s.getCount(),q.accepted(),q.free(),base,durability,enchant,remembered,q.firstRepeat(),q.lastRepeat(),rep,t.destination,!ench.isEmpty());
 }
 /** The card's gift strip: the item in the main hand, what it is worth and where it goes; null when the resident takes no gifts from this player. */
 public static CompoundTag view(ServerPlayer p,ResidentEntity npc){
  long now=SettlementData.get(p.server).clock().ticks();var q=quote(p,npc,p.getMainHandItem(),now);if(q==null)return null;var t=new CompoundTag();
  t.putString("reason",q.reason);t.putString("item",q.item);t.putInt("count",q.count);t.putInt("accepted",q.accepted);t.putInt("free",q.free);t.putLong("base",q.base);t.putInt("durability",q.durability);t.putInt("enchant",q.enchant);
  t.putLong("remembered",q.remembered);t.putInt("repeatFirst",q.firstRepeat);t.putInt("repeatLast",q.lastRepeat);t.putLong("reputation",q.reputation);t.putString("destination",q.destination);
  t.putBoolean("confirm",q.confirm());t.putBoolean("blocked",q.blocked());t.putInt("slot",p.getInventory().selected);t.putBoolean("enchanted",q.enchanted);
  if(!p.getMainHandItem().isEmpty())t.put("stack",p.getMainHandItem().copyWithCount(1).save(new CompoundTag()));
  t.putLong("total",GiftLedger.get(p.server).total(npc.settlementId(),p.getUUID()));var gained=GAINED.remove(p.getUUID());if(gained!=null)t.putLong("gained",gained);
  return t;
 }
 public static String order(ServerPlayer p,UUID token,UUID npcId,int slot,String itemId,int count,long expected){return order(p,token,npcId,slot,itemId,count,expected,SettlementData.get(p.server).clock().ticks());}
 /** Executes one confirmed gift: the held stack must be exactly what the card quoted, or the order is stale instead of partial. */
 public static String order(ServerPlayer p,UUID token,UUID npcId,int slot,String itemId,int count,long expected,long now){
  var ledger=GiftLedger.get(p.server);if(ledger.deal(token)!=null)return "replay";if(!p.isAlive())return "far";
  if(!(p.serverLevel().getEntity(npcId) instanceof ResidentEntity npc)||!npc.isAlive()||npc.distanceToSqr(p)>Trade.REACH*Trade.REACH)return "far";
  var at=place(p,npc);if(at==null)return npc.getTarget()==p?"refused:hostile":"refused:no_village";
  var inventory=p.getInventory();if(slot!=inventory.selected||slot<0||slot>=9)return "stale";var held=inventory.getItem(slot);
  if(held.isEmpty()||!BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(itemId)||held.getCount()!=count)return "stale";
  var q=quote(p,npc,held,now);if(q.blocked())return "refused:"+q.reason;if(q.reputation!=expected)return "stale";
  // The destination is re-checked before the item leaves the hand, so a failed check can never destroy it.
  var village=at.entry.settlement().id();var t=target(at,npc,held.copyWithCount(q.accepted));if(!t.room)return "refused:no_room";
  var gift=held.split(q.accepted);inventory.setChanged();
  if(t.destination.equals(WEAR)){var old=ledger.wear(npc.getUUID(),village,t.slot,gift);npc.setItemSlot(t.slot,gift.copy());npc.setDropChance(t.slot,0F);if(!old.isEmpty())insertExact(t.chest,old);}
  else insertExact(t.chest,gift);
  int free=TradeLedger.get(p.server).returned(village,p.getUUID(),q.item,q.accepted);if(free!=q.free)throw new IllegalStateException("Purchase counter changed within the tick");
  ledger.remember(village,p.getUUID(),q.item,now,q.accepted-q.free);
  var property=PropertyLedger.get(p.server);property.roll(village).decay(now);if(q.reputation>0)property.gift(village,p.getUUID(),(int)Math.min(Integer.MAX_VALUE,q.reputation));
  var deal=new CompoundTag();deal.putUUID("token",token);deal.putLong("tick",now);deal.putUUID("village",village);deal.putUUID("player",p.getUUID());deal.putUUID("npc",npc.getUUID());deal.putString("resident",npc.getDisplayName().getString());
  deal.put("stack",gift.save(new CompoundTag()));deal.putInt("count",q.accepted);deal.putInt("free",q.free);deal.putLong("base",q.base);deal.putInt("durability",q.durability);deal.putInt("enchant",q.enchant);deal.putLong("remembered",q.remembered);
  deal.putInt("repeatFirst",q.firstRepeat);deal.putInt("repeatLast",q.lastRepeat);deal.putLong("reputation",q.reputation);deal.putString("destination",t.destination);if(t.pos!=null)deal.putLong("pos",t.pos.asLong());deal.putBoolean("creativeSource",p.isCreative());deal.putString("reason",q.reason);
  ledger.record(token,deal);GAINED.put(p.getUUID(),q.reputation);p.containerMenu.broadcastChanges();
  at.level.playSound(null,npc.getX(),npc.getY(),npc.getZ(),net.minecraft.sounds.SoundEvents.VILLAGER_YES,net.minecraft.sounds.SoundSource.NEUTRAL,1F,1F);
  at.level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,npc.getX(),npc.getY()+1.8,npc.getZ(),8,.4,.4,.4,0);
  p.displayClientMessage(Component.translatable("message.villageastra.gift_thanks",npc.getDisplayName(),q.reputation),true);
  if(Boolean.getBoolean("villageastra.giftSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_GIFT deal {}",deal);
  return "gift:ok";
 }
 private static int max(Container c,ItemStack s){return Math.min(c.getMaxStackSize(),s.getMaxStackSize());}
 /** Whether the whole stack fits, merging only into identical stacks (tags included). */
 public static boolean fits(Container c,ItemStack s){
  int left=s.getCount(),max=max(c,s);
  for(int i=0;i<c.getContainerSize()&&left>0;i++){var x=c.getItem(i);if(x.isEmpty())left-=max;else if(ItemStack.isSameItemSameTags(x,s))left-=Math.max(0,max-x.getCount());}
  return left<=0;
 }
 /** NBT-preserving insert (enchantments, wear and names stay); the caller checked fits. */
 public static void insertExact(Container c,ItemStack s){
  if(c==null||!fits(c,s))throw new IllegalStateException("Chest changed during gift");int left=s.getCount(),max=max(c,s);
  for(int i=0;i<c.getContainerSize()&&left>0;i++){var x=c.getItem(i);if(!x.isEmpty()&&ItemStack.isSameItemSameTags(x,s)&&x.getCount()<max){int n=Math.min(left,max-x.getCount());x.grow(n);left-=n;}}
  for(int i=0;i<c.getContainerSize()&&left>0;i++)if(c.getItem(i).isEmpty()){int n=Math.min(left,max);c.setItem(i,s.copyWithCount(n));left-=n;}
  c.setChanged();
 }
 /** The body shows exactly the armour the ledger says this resident wears; nothing is ever dropped from it. */
 public static void mirror(ResidentEntity npc){
  if(!(npc.level() instanceof ServerLevel l))return;var w=GiftLedger.get(l.getServer()).worn(npc.getUUID());
  for(var slot:ARMOR){var want=w==null?ItemStack.EMPTY:w.slots().getOrDefault(slot,ItemStack.EMPTY);if(!ItemStack.matches(npc.getItemBySlot(slot),want))npc.setItemSlot(slot,want.copy());npc.setDropChance(slot,0F);}
 }
 /** CF-5: armour of residents who died or left their village goes back into that village's hall; if it is full the pieces wait in the ledger. */
 public static void returnWorn(MinecraftServer server){
  var data=SettlementData.get(server);var ledger=GiftLedger.get(server);
  for(var id:ledger.wearers()){var w=ledger.worn(id);var e=data.entry(w.village());var r=e==null?null:e.settlement().resident(id);if(r!=null&&r.alive())continue;
   ledger.strip(id);for(var s:w.slots().values())ledger.addPending(w.village(),s);
   for(var level:server.getAllLevels())if(level.getEntity(id) instanceof ResidentEntity body)mirror(body);}
  for(var pending:ledger.pending()){var e=data.entry(pending.village());if(e==null)continue;
   var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new ResourceLocation(e.dimension())));if(level==null)continue;
   var hall=hallChest(level,e);if(hall!=null&&fits(hall,pending.stack())){insertExact(hall,pending.stack().copy());ledger.removePending(pending);}}
  ledger.prune(data.clock().ticks());
 }
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||event.getServer().getPlayerCount()==0||event.getServer().getTickCount()%200!=0)return;returnWorn(event.getServer());}
}
