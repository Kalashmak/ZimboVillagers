package org.villageastra.server;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** AD-036: NPC dialog card and zindbo trade against live settlement demand and unreserved surplus. */
public final class Trade {
 public static final int SELL=0,BUY=1,DONATE=2,CLAIM=3;
 public record Price(int coins,int per){}
 private record Profile(boolean valuables,Map<Item,Integer> buys,List<Predicate<ItemStack>> sells){boolean sells(ItemStack s){return sells.stream().anyMatch(m->m.test(s));}}
 /** A trade line: for buys count is the useful amount the village accepts, for sells the unreserved surplus. */
 public record Line(Item item,int coins,int per,int count){}
 public record Offer(String reason,SettlementData.Entry entry,Settlement.Building workplace,Container chest,List<Line> buys,List<Line> sells){}
 private static final JsonObject ROOT=root();
 public static final int DONATION_REPUTATION=ROOT.get("donation_reputation_per_coin").getAsInt(),SALE_DIVISOR=ROOT.get("sale_reputation_divisor").getAsInt(),MARKUP=ROOT.get("sell_markup_percent").getAsInt(),REACH=ROOT.get("reach").getAsInt();
 private static Map<Item,Price> prices;private static Map<Item,Integer> valuables;private static Map<Profession,Profile> profiles;
 private Trade(){}
 private static JsonObject root(){try(var s=Trade.class.getResourceAsStream("/data/villageastra/balance/trade.json")){if(s==null)throw new IllegalStateException("Missing trade balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 private static Item item(String id){var item=BuiltInRegistries.ITEM.get(new ResourceLocation(id));if(item==Items.AIR)throw new IllegalStateException("Unknown trade item "+id);return item;}
 /** Lazily resolved after registries are frozen; every profession must have an explicit assortment. */
 private static synchronized void load(){
  if(profiles!=null)return;if(DONATION_REPUTATION<SALE_DIVISOR||SALE_DIVISOR<2||MARKUP<=100||REACH<2)throw new IllegalStateException("Trade balance lets resale or sale reputation beat donation");
  var p=new LinkedHashMap<Item,Price>();for(var e:ROOT.getAsJsonObject("prices").entrySet()){var a=e.getValue().getAsJsonArray();var price=new Price(a.get(0).getAsInt(),a.get(1).getAsInt());if(price.coins<1||price.per<1||price.per>64)throw new IllegalStateException("Invalid price "+e.getKey());p.put(item(e.getKey()),price);}
  var v=new LinkedHashMap<Item,Integer>();for(var e:ROOT.getAsJsonObject("valuables").entrySet()){var i=item(e.getKey());if(!p.containsKey(i)||e.getValue().getAsInt()<1)throw new IllegalStateException("Unpriced valuable "+e.getKey());v.put(i,e.getValue().getAsInt());}
  var r=new EnumMap<Profession,Profile>(Profession.class);var raw=ROOT.getAsJsonObject("professions");
  for(var profession:Profession.values()){var o=raw.getAsJsonObject(profession.id());if(o==null)throw new IllegalStateException("No trade profile for "+profession.id());
   var buys=new LinkedHashMap<Item,Integer>();for(var e:o.getAsJsonObject("buys").entrySet()){var i=item(e.getKey());if(!p.containsKey(i)||e.getValue().getAsInt()<1)throw new IllegalStateException("Unpriced profession buy "+e.getKey());buys.put(i,e.getValue().getAsInt());}
   var sells=new ArrayList<Predicate<ItemStack>>();for(var s:o.getAsJsonArray("sells")){var id=s.getAsString();if(id.startsWith("#")){var tag=TagKey.create(net.minecraft.core.registries.Registries.ITEM,new ResourceLocation(id.substring(1)));sells.add(x->x.is(tag));}else{var i=item(id);sells.add(x->x.is(i));}}
   r.put(profession,new Profile(o.has("valuables")&&o.get("valuables").getAsBoolean(),Map.copyOf(buys),List.copyOf(sells)));}
  prices=Map.copyOf(p);valuables=Map.copyOf(v);profiles=Map.copyOf(r);
 }
 public static Price price(Item item){load();return prices.get(item);}
 /** Plain goods only: no enchantments, names or wear. A fresh tool carries just Damage:0 and still counts as plain. */
 public static boolean plain(ItemStack s){return !s.hasTag()||s.isDamageableItem()&&s.getDamageValue()==0&&s.getTag().size()==1&&s.getTag().contains("Damage");}
 private static Predicate<ItemStack> same(Item item){return s->s.is(item)&&plain(s);}
 private static int count(Container c,Predicate<ItemStack> m){int n=0;for(int i=0;i<c.getContainerSize();i++)if(m.test(c.getItem(i)))n+=c.getItem(i).getCount();return n;}
 private static int inventory(ServerPlayer p,Predicate<ItemStack> m){int n=0;for(var s:p.getInventory().items)if(m.test(s))n+=s.getCount();return n;}
 private static int room(Container c,Item item){int max=Math.min(c.getMaxStackSize(),item.getMaxStackSize()),n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.isEmpty())n+=max;else if(same(item).test(s))n+=Math.max(0,max-s.getCount());}return n;}
 private static int room(ServerPlayer p,Item item){int max=item.getMaxStackSize(),n=0;for(var s:p.getInventory().items){if(s.isEmpty())n+=max;else if(same(item).test(s))n+=Math.max(0,max-s.getCount());}return n;}
 private static int settlementCount(ServerLevel l,SettlementData.Entry e,Predicate<ItemStack> m){int n=0;for(var b:e.settlement().buildings()){var c=LogisticsRoutes.chest(l,e,b);if(c!=null)n+=count(c,m);}return n;}
 public static long cost(Line line,int count){return Math.max(1,-Math.floorDiv(-(long)count*line.coins*MARKUP,(long)line.per*100));}
 public static long payment(Line line,int count){return (long)count*line.coins/line.per;}
 public static long reputation(Line line,int useful,boolean donation){long value=(long)useful*line.coins*DONATION_REPUTATION;return useful<=0?0:donation?value/line.per:value/((long)line.per*SALE_DIVISOR);}
 public static Offer offer(ServerPlayer p,ResidentEntity npc){
  load();var none=new Offer("no_village",null,null,null,List.of(),List.of());var l=p.serverLevel();
  if(npc.settlementId()==null)return none;var e=SettlementData.get(p.server).entry(npc.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return none;
  var r=e.settlement().resident(npc.getUUID());if(r==null||!r.alive())return none;
  if(npc.getTarget()==p)return reason("hostile",e);
  if(PropertyLedger.get(p.server).roll(e.settlement().id()).account(p.getUUID()).score()<0)return reason("distrust",e);
  if(npc.child())return reason("child",e);if(r.profession()==null)return reason("unemployed",e);
  if(p.isSpectator())return reason("creative",e);
  var b=e.settlement().workplace(npc.getUUID());var chest=b==null?null:LogisticsRoutes.chest(l,e,b);if(chest==null)return reason("no_workplace",e);
  var profile=profiles.get(r.profession());var wants=Workshops.wants(l,e);
  var candidates=new LinkedHashSet<Item>();
  for(var w:wants)if(w.destination().equals(b.id()))for(var s:w.ingredient().getItems())if(prices.containsKey(s.getItem()))candidates.add(s.getItem());
  candidates.addAll(profile.buys.keySet());if(profile.valuables)candidates.addAll(valuables.keySet());
  var buys=new ArrayList<Line>();
  for(var item:candidates){var stack=new ItemStack(item);var m=same(item);int here=count(chest,m),total=settlementCount(l,e,m);
   // Construction and workshop wants already subtract this chest; stock elsewhere in the village would be carried by porters, so it is not bought twice.
   int wanted=0;for(var w:wants)if(w.destination().equals(b.id())&&w.matches(stack))wanted+=w.count();wanted=Math.max(0,wanted-(total-here));
   int accept=Math.max(wanted,profile.buys.getOrDefault(item,0)-here)-PorterWork.reserved(l,e,b.id(),m,true);
   if(profile.valuables&&valuables.containsKey(item))accept=Math.max(accept,valuables.get(item)-total);
   accept=Math.min(accept,room(chest,item));var price=prices.get(item);if(accept>0)buys.add(new Line(item,price.coins,price.per,accept));}
  var goods=new LinkedHashSet<Item>();for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);if(!s.isEmpty()&&plain(s)&&prices.containsKey(s.getItem())&&profile.sells(s))goods.add(s.getItem());}
  var sells=new ArrayList<Line>();
  for(var item:goods){var stack=new ItemStack(item);var m=same(item);int here=count(chest,m);
   int keep=LogisticsRoutes.reserve(b,stack)+LogisticsRoutes.constructionReserve(l,e,b,stack)+profile.buys.getOrDefault(item,0)+PorterWork.reserved(l,e,b.id(),m,false);
   for(var w:wants)if(w.matches(stack))keep+=w.count();
   if(valuables.containsKey(item))keep+=Math.max(0,valuables.get(item)-(settlementCount(l,e,m)-here));
   var price=prices.get(item);if(here-keep>0)sells.add(new Line(item,price.coins,price.per,here-keep));}
  return new Offer(buys.isEmpty()&&sells.isEmpty()?"no_goods":"",e,b,chest,List.copyOf(buys),List.copyOf(sells));
 }
 private static Offer reason(String reason,SettlementData.Entry e){return new Offer(reason,e,null,null,List.of(),List.of());}
 private static void remove(ServerPlayer p,Predicate<ItemStack> m,long count){for(var s:p.getInventory().items){if(count<=0)break;if(m.test(s)){int n=(int)Math.min(count,s.getCount());s.shrink(n);count-=n;}}if(count>0)throw new IllegalStateException("Inventory changed during trade");}
 private static void insert(Container c,Item item,int count){int max=Math.min(c.getMaxStackSize(),item.getMaxStackSize());
  for(int i=0;i<c.getContainerSize()&&count>0;i++){var s=c.getItem(i);if(same(item).test(s)&&s.getCount()<max){int n=Math.min(count,max-s.getCount());s.grow(n);count-=n;}}
  for(int i=0;i<c.getContainerSize()&&count>0;i++)if(c.getItem(i).isEmpty()){int n=Math.min(count,max);c.setItem(i,new ItemStack(item,n));count-=n;}
  if(count>0)throw new IllegalStateException("Chest changed during trade");c.setChanged();}
 private static void take(Container c,Item item,int count){for(int i=0;i<c.getContainerSize()&&count>0;i++){var s=c.getItem(i);if(same(item).test(s)){int n=Math.min(count,s.getCount());s.shrink(n);count-=n;}}if(count>0)throw new IllegalStateException("Chest changed during trade");c.setChanged();}
 /** Coins that do not fit the inventory stay owed in the ledger; nothing is dropped or lost. */
 private static long pay(ServerPlayer p,TradeLedger ledger,long coins){
  long left=coins;while(left>0){int chunk=(int)Math.min(64,left);var stack=new ItemStack(VillageAstra.ZINDBO.get(),chunk);p.getInventory().add(stack);int paid=chunk-stack.getCount();left-=paid;if(paid<chunk)break;}
  if(left>0)ledger.owe(p.getUUID(),left);return left;
 }
 /** Executes one confirmed order. The client sends the exact count it showed; any change in demand, stock or inventory makes the order stale instead of partial. */
 public static String order(ServerPlayer p,UUID token,UUID npcId,int side,String itemId,int count){
  load();var ledger=TradeLedger.get(p.server);if(ledger.deal(token)!=null)return "replay";if(!p.isAlive())return "far";
  long now=SettlementData.get(p.server).clock().ticks();var deal=new CompoundTag();deal.putUUID("token",token);deal.putLong("tick",now);deal.putUUID("player",p.getUUID());deal.putInt("side",side);
  if(side==CLAIM){long owed=ledger.owed(p.getUUID());if(owed==0)return "stale";ledger.owe(p.getUUID(),-owed);long left=pay(p,ledger,owed);deal.putLong("paid",owed-left);deal.putLong("owed",left);ledger.record(token,deal);return owed==left?"refused:no_room":"ok";}
  if(!(p.serverLevel().getEntity(npcId) instanceof ResidentEntity npc)||!npc.isAlive()||npc.distanceToSqr(p)>REACH*REACH)return "far";
  var offer=offer(p,npc);if(!offer.reason().isEmpty())return "refused:"+offer.reason();
  var id=ResourceLocation.tryParse(itemId);var item=id==null?Items.AIR:BuiltInRegistries.ITEM.get(id);if(item==Items.AIR||count<=0||count>64*36)return "stale";
  var e=offer.entry();var village=e.settlement().id();var m=same(item);String key=BuiltInRegistries.ITEM.getKey(item).toString();
  deal.putUUID("village",village);deal.putUUID("npc",npc.getUUID());deal.putUUID("building",offer.workplace().id());deal.putString("item",key);deal.putInt("count",count);
  if(side==SELL||side==DONATE){
   var line=offer.buys().stream().filter(x->x.item()==item).findFirst().orElse(null);if(line==null||count>line.count()||!p.isCreative()&&count>inventory(p,m))return "stale";
   long coins=side==SELL?payment(line,count):0;if(side==SELL&&coins==0)return "stale";
   if(!p.isCreative())remove(p,m,count);insert(offer.chest(),item,count);
   deal.putBoolean("creativeSource",p.isCreative());
   int useful=p.isCreative()?count:count-ledger.returned(village,p.getUUID(),key,count);boolean mayor=!p.isCreative()&&p.getUUID().equals(e.settlement().governance().playerMayor());
   long rep=mayor?0:reputation(line,useful,side==DONATE);
   if(rep>0){var property=PropertyLedger.get(p.server);property.roll(village).decay(now);property.gift(village,p.getUUID(),(int)Math.min(Integer.MAX_VALUE,rep));}
   long owed=pay(p,ledger,coins);
   deal.putInt("price",line.coins());deal.putInt("per",line.per());deal.putInt("quota",line.count());deal.putInt("stock",count);deal.putLong("paid",coins-owed);deal.putLong("owed",owed);deal.putLong("reputation",rep);deal.putInt("useful",useful);
   org.villageastra.world.Quests.onDeal(p,village,key,count,side==SELL?coins:(long)count*line.coins()/line.per(),side==DONATE);
  }else if(side==BUY){
   var line=offer.sells().stream().filter(x->x.item()==item).findFirst().orElse(null);if(line==null||count>line.count())return "stale";
   long cost=cost(line,count),owedCoins=ledger.owed(p.getUUID());var coin=same(VillageAstra.ZINDBO.get());
   if(inventory(p,coin)+owedCoins<cost)return "refused:no_coins";if(room(p,item)<count)return "refused:no_room";
   long fromOwed=Math.min(owedCoins,cost);if(fromOwed>0)ledger.owe(p.getUUID(),-fromOwed);remove(p,coin,cost-fromOwed);
   take(offer.chest(),item,count);var goods=new ItemStack(item,count);while(!goods.isEmpty()){var part=goods.split(item.getMaxStackSize());if(!p.getInventory().add(part))throw new IllegalStateException("Inventory room changed during trade");}
   ledger.addBought(village,p.getUUID(),key,count);ledger.addTreasury(village,cost);
   long rep=Math.max(1,cost);var property=PropertyLedger.get(p.server);property.roll(village).decay(now);property.gift(village,p.getUUID(),(int)Math.min(Integer.MAX_VALUE,rep));
   deal.putInt("price",line.coins());deal.putInt("per",line.per());deal.putInt("quota",line.count());deal.putInt("stock",-count);deal.putLong("cost",cost);deal.putLong("reputation",rep);
  }else return "stale";
  ledger.record(token,deal);p.containerMenu.broadcastChanges();
  if(Boolean.getBoolean("villageastra.tradeSmoke"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_TRADE deal {}",deal);
  return "ok";
 }
 /** Dialog card for every resident: identity, reputation, wallet and the live assortment or the exact reason it is empty. */
 public static CompoundTag view(ServerPlayer p,ResidentEntity npc){
  var tag=new CompoundTag();tag.putUUID("npc",npc.getUUID());tag.putString("name",npc.getDisplayName().getString());tag.putBoolean("child",npc.child());tag.putString("status",npc.workStatus());
  var offer=offer(p,npc);tag.putString("reason",offer.reason());var e=offer.entry();
  if(e!=null){var r=e.settlement().resident(npc.getUUID());tag.putString("profession",r==null||r.profession()==null?"":r.profession().id());tag.putInt("residents",(int)e.settlement().residents().stream().filter(Resident::alive).count());
   tag.putLong("reputation",PropertyLedger.get(p.server).roll(e.settlement().id()).account(p.getUUID()).score());tag.putBoolean("mayor",p.getUUID().equals(e.settlement().governance().playerMayor()));}
  tag.putBoolean("creativeSource",p.isCreative());tag.putInt("coins",inventory(p,same(VillageAstra.ZINDBO.get())));tag.putLong("owed",TradeLedger.get(p.server).owed(p.getUUID()));tag.putInt("markup",MARKUP);
  var buys=new ListTag();for(var line:offer.buys()){var t=line(line);t.putInt("have",inventory(p,same(line.item())));t.putBoolean("creativeSource",p.isCreative());buys.add(t);}tag.put("buys",buys);
  var sells=new ListTag();for(var line:offer.sells()){var t=line(line);t.putInt("room",room(p,line.item()));sells.add(t);}tag.put("sells",sells);
  var gift=Gifts.view(p,npc);if(gift!=null)tag.put("gift",gift);
  return tag;
 }
 private static CompoundTag line(Line line){var t=new CompoundTag();t.putString("item",BuiltInRegistries.ITEM.getKey(line.item()).toString());t.putInt("price",line.coins());t.putInt("per",line.per());t.putInt("count",line.count());return t;}
}
