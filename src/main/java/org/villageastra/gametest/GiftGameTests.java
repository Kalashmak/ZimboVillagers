package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-128: gifts to residents earn reputation by value, wear, enchantments and repeats; two diamond sets make a candidate, one does not;
 *  the village keeps and uses what it gets, and a gift cannot be taken back for free. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class GiftGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Container hall,Container post,ResidentEntity miller,ResidentEntity miner,ResidentEntity guard,ResidentEntity child){}
 private static final List<Item> SET=List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS,Items.DIAMOND_SWORD,Items.DIAMOND_PICKAXE,Items.DIAMOND_AXE,Items.DIAMOND_SHOVEL,Items.DIAMOND_HOE);
 private static Settlement.Building building(ServerLevel l,Settlement s,BlockPos center,String type,int dx){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,dx,0,0);s.addBuilding(b);l.setBlock(center.offset(dx+1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);return b;}
 private static ResidentEntity resident(ServerLevel l,Settlement s,BlockPos center,Settlement.Building b,Profession role,Resident.Life life,int dx){
  // A soldier is made as one (military flag), as founding watches are.
  var r=new Resident(UUID.randomUUID(),life,life==Resident.Life.ADULT,role,null,-1);s.admit(r,s.homes().iterator().next().id());if(role!=null)s.assign(r.id(),role,b.id());
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),s.resident(r.id()));npc.setNoAi(true);npc.moveTo(center.getX()+dx+2.5,center.getY()+1,center.getZ()+2.5,0,0);l.addFreshEntity(npc);return npc;
 }
 private static Town town(GameTestHelper h,boolean post){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  for(int x=-8;x<40;x++)for(int z=-8;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var hallB=building(l,s,center,"town_hall",0);var mill=building(l,s,center,"mill",8);var mine=building(l,s,center,"mine",24);Settlement.Building guardB=post?building(l,s,center,"guard_house",16):null;
  var miller=resident(l,s,center,mill,Profession.MILLER,Resident.Life.ADULT,8);var miner=resident(l,s,center,mine,Profession.MINER,Resident.Life.ADULT,24);
  var guard=post?resident(l,s,center,guardB,Profession.GUARD,Resident.Life.ADULT,16):null;var child=resident(l,s,center,null,null,Resident.Life.CHILD,32);
  var hall=LogisticsRoutes.chest(l,e,hallB);hall.expandHall();
  return new Town(l,s,e,center,hall,post?LogisticsRoutes.chest(l,e,guardB):null,miller,miner,guard,child);
 }
 private static ServerPlayer player(Town t,String name){var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().selected=0;p.setPos(t.e.center().getX()+2,t.e.center().getY()+1,t.e.center().getZ()+2);return p;}
 private static long now(Town t){return SettlementData.get(t.l.getServer()).clock().ticks();}
 private static long score(Town t,ServerPlayer p){return PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score();}
 private static String key(ItemStack s){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
 private static Gifts.Quote quote(Town t,ServerPlayer p,ResidentEntity npc,ItemStack s,long now){p.getInventory().setItem(0,s);p.setPos(npc.getX()+1,npc.getY(),npc.getZ());return Gifts.quote(p,npc,p.getInventory().getItem(0),now);}
 private static String give(Town t,ServerPlayer p,ResidentEntity npc,ItemStack s){return give(t,p,npc,s,now(t),UUID.randomUUID());}
 private static String give(Town t,ServerPlayer p,ResidentEntity npc,ItemStack s,long now,UUID token){var q=quote(t,p,npc,s,now);return Gifts.order(p,token,npc.getUUID(),0,key(s),s.getCount(),q==null?-1:q.reputation(),now);}
 private static ItemStack worn(Item item,double left){var s=new ItemStack(item);int max=s.getMaxDamage();s.setDamageValue(max-(int)Math.ceil(max*left));return s;}
 private static int count(Container c,Item item){int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))n+=c.getItem(i).getCount();return n;}
 private static void ok(GameTestHelper h,String result,String what){h.assertTrue(result.equals("gift:ok"),what+": "+result);}
 private static ItemStack enchanted(Item item,Object... pairs){var s=new ItemStack(item);for(int i=0;i<pairs.length;i+=2)s.enchant((net.minecraft.world.item.enchantment.Enchantment)pairs[i],(Integer)pairs[i+1]);return s;}

 @GameTest(template="empty",timeoutTicks=200) public static void twoDiamondSetsMakeACandidate(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftTwoSets");
  for(int round=0;round<2;round++)for(var item:SET)ok(h,give(t,p,t.miller,new ItemStack(item)),"gift "+item+" round "+round);
  h.assertTrue(score(t,p)==8960&&score(t,p)>=ElectionRoll.MINIMUM,"Two fresh diamond sets reach the threshold: "+score(t,p)+" / "+ElectionRoll.MINIMUM);
  p.setPos(t.e.center().getX(),t.e.center().getY()+1,t.e.center().getZ());var roll=PropertyLedger.get(t.l.getServer()).roll(t.s.id());
  h.assertTrue(Elections.order(p,t.s.id(),t.s.governance().epoch(),roll.sequence(),0),"The giver registers as a candidate");
  h.assertTrue(GiftLedger.get(t.l.getServer()).total(t.s.id(),p.getUUID())==8960,"Gift total explains the score");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void oneDiamondSetIsNotEnough(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftOneSet");for(var item:SET)ok(h,give(t,p,t.miller,new ItemStack(item)),"gift "+item);
  h.assertTrue(score(t,p)==4480,"One set is 4480: "+score(t,p));p.setPos(t.e.center().getX(),t.e.center().getY()+1,t.e.center().getZ());var roll=PropertyLedger.get(t.l.getServer()).roll(t.s.id());
  h.assertTrue(!Elections.order(p,t.s.id(),t.s.governance().epoch(),roll.sequence(),0),"One set does not make a candidate");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void wornItemsCountLess(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftWorn");var fresh=quote(t,p,t.miller,new ItemStack(Items.DIAMOND_PICKAXE),now(t)).reputation();var half=new ItemStack(Items.DIAMOND_PICKAXE);half.setDamageValue(780);
  h.assertTrue(fresh==384&&quote(t,p,t.miller,half,now(t)).reputation()==192,"Half-worn pickaxe is worth half: "+fresh);
  for(var left:List.of(0.8,0.9)){var giver=player(t,"GiftWorn"+left);var npc=left==0.8?t.miller:t.miner;long expected=0;
   for(int round=0;round<2;round++)for(var item:SET){var s=worn(item,left);expected+=GiftValue.unit(Gifts.rules(),Gifts.basePermille(s),Gifts.durability(s),1000,1000);ok(h,give(t,giver,npc,s),"worn gift "+item);}
   h.assertTrue(score(t,giver)==expected,"Score is the exact sum of per-item durability: "+score(t,giver)+" vs "+expected);
   h.assertTrue(left==0.8?score(t,giver)<ElectionRoll.MINIMUM:score(t,giver)>=ElectionRoll.MINIMUM,"At "+left+" two sets give "+score(t,giver));}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void enchantedItemsCountMore(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftEnchanted");
  var chest=enchanted(Items.DIAMOND_CHESTPLATE,Enchantments.ALL_DAMAGE_PROTECTION,4,Enchantments.UNBREAKING,3,Enchantments.MENDING,1,Enchantments.THORNS,3);
  var q=quote(t,p,t.miller,chest,now(t));h.assertTrue(q.reputation()==3584&&q.enchant()==3500&&q.confirm(),"Fully enchanted chestplate: "+q);ok(h,give(t,p,t.miller,chest),"chestplate");
  ok(h,give(t,p,t.miller,enchanted(Items.DIAMOND_HELMET,Enchantments.ALL_DAMAGE_PROTECTION,4,Enchantments.UNBREAKING,3,Enchantments.MENDING,1,Enchantments.RESPIRATION,3,Enchantments.AQUA_AFFINITY,1,Enchantments.THORNS,3)),"helmet");
  ok(h,give(t,p,t.miller,enchanted(Items.DIAMOND_LEGGINGS,Enchantments.ALL_DAMAGE_PROTECTION,4,Enchantments.UNBREAKING,3,Enchantments.MENDING,1,Enchantments.THORNS,3,Enchantments.SWIFT_SNEAK,3)),"leggings");
  h.assertTrue(score(t,p)==9728&&score(t,p)>=ElectionRoll.MINIMUM,"Three fully enchanted pieces reach the threshold: "+score(t,p));
  var cursed=quote(t,player(t,"GiftCursed"),t.miller,enchanted(Items.DIAMOND_SWORD,Enchantments.VANISHING_CURSE,1),now(t));h.assertTrue(cursed.reputation()==192,"A curse lowers the value: "+cursed.reputation());
  var block=quote(t,player(t,"GiftBlock"),t.miller,enchanted(Items.DIAMOND_BLOCK,Enchantments.ALL_DAMAGE_PROTECTION,4),now(t));h.assertTrue(block.enchant()==1000&&block.reputation()==1152,"Enchantments that do not belong on the item count nothing: "+block);
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void repeatedKindsDiminishAndRecover(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftRepeat");long now=now(t),before=0;
  for(int i=0;i<7;i++)ok(h,give(t,p,t.miller,new ItemStack(Items.IRON_CHESTPLATE),now,UUID.randomUUID()),"iron chestplate "+i);
  h.assertTrue(score(t,p)==188,"Seven iron chestplates in a day earn 64+64+32+16+8+3+1: "+score(t,p));
  var tired=give(t,p,t.miller,new ItemStack(Items.IRON_CHESTPLATE),now,UUID.randomUUID());h.assertTrue(tired.equals("refused:tired")&&p.getInventory().getItem(0).is(Items.IRON_CHESTPLATE),"An eighth is worth nothing and stays in hand: "+tired);
  before=score(t,p);ok(h,give(t,p,t.miller,new ItemStack(Items.IRON_CHESTPLATE),now+5*ElectionRoll.PERIOD,UUID.randomUUID()),"after five days");
  long gained=score(t,p)-before+5;h.assertTrue(gained==32,"After five periods memory 7->2 and the next counts 32 (decay 5 added back): "+gained);
  before=score(t,p);ok(h,give(t,p,t.miller,new ItemStack(Items.IRON_CHESTPLATE),now+7*ElectionRoll.PERIOD,UUID.randomUUID()),"after seven days");
  gained=score(t,p)-before+2;h.assertTrue(gained==64,"Two periods later memory 3->1 and it counts whole: "+gained);
  var ingots=new ItemStack(Items.IRON_INGOT,64);var q=quote(t,p,t.miller,ingots,now+7*ElectionRoll.PERIOD);h.assertTrue(q.accepted()==5&&q.reputation()==23,"Only the units worth something are taken from a stack (8+8+4+2+1): "+q);
  ok(h,give(t,p,t.miller,ingots,now+7*ElectionRoll.PERIOD,UUID.randomUUID()),"ingots");h.assertTrue(p.getInventory().getItem(0).getCount()==59,"The rest of the stack stays in hand: "+p.getInventory().getItem(0));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void cheapAndContainerGiftsAreRefused(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftCheap");var shulker=new ItemStack(Items.SHULKER_BOX);var items=new ListTag();var d=new ItemStack(Items.DIAMOND).save(new CompoundTag());d.putByte("Slot",(byte)0);items.add(d);shulker.getOrCreateTagElement("BlockEntityTag").put("Items",items);
  for(var s:List.of(new ItemStack(Items.DIRT,64),new ItemStack(Items.STONE_PICKAXE),new ItemStack(VillageAstra.ZINDBO.get(),10),new ItemStack(Items.WOODEN_SWORD),shulker)){
   var copy=s.copy();var result=give(t,p,t.miller,s);
   h.assertTrue(result.equals(copy.is(Items.SHULKER_BOX)?"refused:container":"refused:not_a_gift"),"Refused "+copy+": "+result);
   h.assertTrue(ItemStack.matches(p.getInventory().getItem(0),copy)&&score(t,p)==0,"The item stays and the score is unchanged: "+copy);}
  p.getInventory().setItem(0,ItemStack.EMPTY);h.assertTrue(Gifts.quote(p,t.miller,ItemStack.EMPTY,now(t)).reason().equals("empty"),"Empty hand explains itself");h.succeed();
 }
 /** Review: an item worn to nothing is refused as worn, not as «enough gifts today»; the refusal keeps it in hand and nothing is remembered. */
 @GameTest(template="empty",timeoutTicks=200) public static void wornOutGiftIsRefusedAsWornNotTired(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftWornOut");var hoe=new ItemStack(Items.DIAMOND_HOE);hoe.setDamageValue(hoe.getMaxDamage()-1);var copy=hoe.copy();
  var q=quote(t,p,t.miller,hoe,now(t));h.assertTrue(q.reason().equals("worn")&&q.blocked()&&q.accepted()==0&&q.remembered()==0,"A worn-out hoe is refused as worn: "+q);
  var result=give(t,p,t.miller,hoe);h.assertTrue(result.equals("refused:worn")&&ItemStack.matches(p.getInventory().getItem(0),copy)&&score(t,p)==0,"It stays in hand: "+result);
  h.assertTrue(GiftLedger.get(t.l.getServer()).remembered(t.s.id(),p.getUUID(),"minecraft:diamond_hoe",now(t))==0&&count(t.hall,Items.DIAMOND_HOE)==0,"Nothing is remembered or delivered");
  var fresh=quote(t,p,t.miller,new ItemStack(Items.DIAMOND_HOE),now(t));h.assertTrue(fresh.reason().isEmpty()&&fresh.reputation()==256,"A fresh hoe of the same kind still counts whole: "+fresh);h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void giftsGoWhereTheyAreUsed(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftDestinations");
  ok(h,give(t,p,t.miller,new ItemStack(Items.IRON_CHESTPLATE)),"iron chestplate");h.assertTrue(t.miller.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),"First armour is worn");
  ok(h,give(t,p,t.miller,new ItemStack(Items.DIAMOND_CHESTPLATE)),"diamond chestplate");
  h.assertTrue(t.miller.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)&&count(t.hall,Items.IRON_CHESTPLATE)==1,"Better armour replaces the worn piece, which goes to the hall");
  var pickWanted=WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.ingredient().test(new ItemStack(Items.STONE_PICKAXE)));
  ok(h,give(t,p,t.miner,new ItemStack(Items.DIAMOND_PICKAXE)),"pickaxe");var stillWanted=WorkerSupplies.wants(t.l,t.e).stream().anyMatch(w->w.ingredient().test(new ItemStack(Items.STONE_PICKAXE)));
  h.assertTrue(pickWanted&&!stillWanted&&count(t.hall,Items.DIAMOND_PICKAXE)==1,"The pickaxe goes to the hall chest and the mine no longer asks for one");
  ok(h,give(t,p,t.miller,new ItemStack(Items.DIAMOND_SWORD)),"sword");h.assertTrue(count(t.post,Items.DIAMOND_SWORD)==1,"A sword goes to the guard post");
  ok(h,give(t,p,t.child,new ItemStack(Items.IRON_HELMET)),"child helmet");h.assertTrue(t.child.getItemBySlot(EquipmentSlot.HEAD).isEmpty()&&count(t.hall,Items.IRON_HELMET)==1,"A child does not wear armour: it goes to the hall");
  var frost=enchanted(Items.DIAMOND_BOOTS,Enchantments.FROST_WALKER,2);ok(h,give(t,p,t.miner,frost),"frost boots");h.assertTrue(t.miner.getItemBySlot(EquipmentSlot.FEET).isEmpty()&&count(t.hall,Items.DIAMOND_BOOTS)==1,"Frost Walker boots are never worn");
  h.succeedWhen(()->h.assertTrue(t.miller.getArmorValue()==8,"Worn armour protects: "+t.miller.getArmorValue()));
 }
 @GameTest(template="empty",timeoutTicks=200) public static void swordWithoutPostGoesToHallAndFullHallRefuses(GameTestHelper h){
  var bare=town(h,false);var q=player(bare,"GiftNoPost");ok(h,give(bare,q,bare.miller,new ItemStack(Items.DIAMOND_SWORD)),"sword without post");h.assertTrue(count(bare.hall,Items.DIAMOND_SWORD)==1,"Without a guard post the sword goes to the hall");
  for(int i=0;i<bare.hall.getContainerSize();i++)if(bare.hall.getItem(i).isEmpty())bare.hall.setItem(i,new ItemStack(Items.COBBLESTONE,64));
  var full=give(bare,q,bare.miller,new ItemStack(Items.DIAMOND_AXE));h.assertTrue(full.equals("refused:no_room")&&q.getInventory().getItem(0).is(Items.DIAMOND_AXE)&&score(bare,q)==256,"A full hall refuses and the axe stays in hand: "+full);
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void replayStaleAndMayor(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftReplay");var token=UUID.randomUUID();long now=now(t);
  ok(h,give(t,p,t.miller,new ItemStack(Items.DIAMOND),now,token),"first");long after=score(t,p);
  var replay=give(t,p,t.miller,new ItemStack(Items.DIAMOND),now,token);h.assertTrue(replay.equals("replay")&&score(t,p)==after&&p.getInventory().getItem(0).is(Items.DIAMOND),"Replayed token changes nothing");
  var q=Gifts.quote(p,t.miller,p.getInventory().getItem(0),now);
  h.assertTrue(Gifts.order(p,UUID.randomUUID(),t.miller.getUUID(),0,"minecraft:diamond",1,q.reputation()+1,now).equals("stale"),"A different expected value is stale");
  h.assertTrue(Gifts.order(p,UUID.randomUUID(),t.miller.getUUID(),0,"minecraft:diamond",2,q.reputation(),now).equals("stale"),"A different count is stale");
  h.assertTrue(Gifts.order(p,UUID.randomUUID(),t.miller.getUUID(),1,"minecraft:diamond",1,q.reputation(),now).equals("stale"),"Another slot is stale");
  var mayor=player(t,"GiftMayor");t.s.appointPlayerMayor(mayor.getUUID());var mq=quote(t,mayor,t.miller,new ItemStack(Items.DIAMOND_SWORD),now);
  h.assertTrue(mq.reason().equals("mayor")&&mq.reputation()==0&&!mq.blocked(),"The mayor's gift is accepted without reputation: "+mq);ok(h,give(t,mayor,t.miller,new ItemStack(Items.DIAMOND_SWORD),now,UUID.randomUUID()),"mayor gift");h.assertTrue(score(t,mayor)==0,"Mayor earns nothing");
  var buyer=player(t,"GiftBuyer");TradeLedger.get(t.l.getServer()).addBought(t.s.id(),buyer.getUUID(),"minecraft:iron_ingot",2);
  var bq=quote(t,buyer,t.miller,new ItemStack(Items.IRON_INGOT,3),now);h.assertTrue(bq.free()==2&&bq.accepted()==3&&bq.reputation()==8,"Bought units earn nothing, the third does: "+bq);
  h.assertTrue(TradeLedger.get(t.l.getServer()).bought(t.s.id(),buyer.getUUID(),"minecraft:iron_ingot")==2,"The quote does not consume the purchase counter");
  ok(h,give(t,buyer,t.miller,new ItemStack(Items.IRON_INGOT,3),now,UUID.randomUUID()),"buyer");h.assertTrue(score(t,buyer)==8&&TradeLedger.get(t.l.getServer()).bought(t.s.id(),buyer.getUUID(),"minecraft:iron_ingot")==0,"The order consumes it");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void giftLedgerRoundTripAndCorruption(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftLedger");var token=UUID.randomUUID();ok(h,give(t,p,t.miller,new ItemStack(Items.DIAMOND_CHESTPLATE),now(t),token),"gift");
  var ledger=GiftLedger.get(t.l.getServer());var copy=GiftLedger.load(ledger.save(new CompoundTag()));
  h.assertTrue(copy.total(t.s.id(),p.getUUID())==1024&&copy.deal(token).getLong("reputation")==1024&&copy.worn(t.miller.getUUID()).slots().get(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)&&copy.remembered(t.s.id(),p.getUUID(),"minecraft:diamond_chestplate",now(t))==1,"Ledger round trip keeps deals, totals, memory and worn armour");
  var bad=ledger.save(new CompoundTag());bad.putInt("schema",9);boolean rejected=false;try{GiftLedger.load(bad);}catch(IllegalArgumentException ex){rejected=true;}
  var dup=ledger.save(new CompoundTag());dup.getList("deals",Tag.TAG_COMPOUND).add(dup.getList("deals",Tag.TAG_COMPOUND).getCompound(0).copy());boolean duplicate=false;try{GiftLedger.load(dup);}catch(IllegalArgumentException ex){duplicate=true;}
  h.assertTrue(rejected&&duplicate,"A corrupt gift ledger fails closed");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void wornArmorReturnsToHallOnDeath(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftDeath");ok(h,give(t,p,t.miner,new ItemStack(Items.DIAMOND_CHESTPLATE)),"gift");h.assertTrue(t.miner.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),"Worn");
  var at=t.miner.blockPosition();t.miner.kill();h.assertTrue(!t.s.resident(t.miner.getUUID()).alive(),"The registry records the death");
  Gifts.returnWorn(t.l.getServer());
  h.assertTrue(count(t.hall,Items.DIAMOND_CHESTPLATE)==1&&GiftLedger.get(t.l.getServer()).worn(t.miner.getUUID())==null,"Worn armour goes back to the hall");
  h.assertTrue(t.l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(at).inflate(4)).stream().noneMatch(i->i.getItem().is(Items.DIAMOND_CHESTPLATE)),"Nothing is dropped on the ground");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void takingAGiftBackCostsItsValue(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftTaker");for(int i=0;i<2;i++)ok(h,give(t,p,t.child,new ItemStack(Items.DIAMOND_CHESTPLATE)),"gift "+i);
  h.assertTrue(score(t,p)==2048&&count(t.hall,Items.DIAMOND_CHESTPLATE)==2,"Two chestplates in the hall: "+score(t,p));
  int slot=-1;for(int i=0;i<27&&slot<0;i++)if(t.hall.getItem(i).is(Items.DIAMOND_CHESTPLATE))slot=i;
  var menu=new OwnedChestMenu(1,p.getInventory(),(OwnedChestEntity)t.hall);menu.clicked(slot,0,ClickType.QUICK_MOVE,p);
  h.assertTrue(score(t,p)==1024&&PropertyLedger.get(t.l.getServer()).stolen(t.s.id(),p.getUUID())==1024,"Taking a gift back costs what it earned: "+score(t,p));
  var other=player(t,"GiftSecondTaker");slot=-1;for(int i=0;i<27&&slot<0;i++)if(t.hall.getItem(i).is(Items.DIAMOND_CHESTPLATE))slot=i;
  new OwnedChestMenu(2,other.getInventory(),(OwnedChestEntity)t.hall).clicked(slot,0,ClickType.QUICK_MOVE,other);
  h.assertTrue(PropertyLedger.get(t.l.getServer()).stolen(t.s.id(),other.getUUID())==1024&&score(t,other)==-1024,"Another player taking it pays the same");
  t.hall.setItem(0,new ItemStack(Items.BREAD,4));new OwnedChestMenu(3,other.getInventory(),(OwnedChestEntity)t.hall).clicked(0,0,ClickType.QUICK_MOVE,other);
  h.assertTrue(PropertyLedger.get(t.l.getServer()).stolen(t.s.id(),other.getUUID())==1028,"Cheap goods still weigh one a unit");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void wornArmorSurvivesCaravanBody(GameTestHelper h){
  var t=town(h,true);var p=player(t,"GiftBody");ok(h,give(t,p,t.miller,new ItemStack(Items.DIAMOND_HELMET)),"gift");
  var id=t.miller.getUUID();t.miller.discard();var body=VillageAstra.RESIDENT.get().create(t.l);body.bind(t.s.id(),t.s.resident(id));body.setNoAi(true);body.moveTo(t.miller.getX(),t.miller.getY(),t.miller.getZ(),0,0);h.assertTrue(t.l.addFreshEntity(body),"New body joins the level");
  h.assertTrue(body.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"A fresh body starts bare");
  h.succeedWhen(()->h.assertTrue(body.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"The ledger dresses the new body"));
 }
}
