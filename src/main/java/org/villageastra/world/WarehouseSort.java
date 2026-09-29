package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-147 §4 (CF-N): the store of a level-VI warehouse sorts itself (balance/automation.json "warehouse": sort). No courier and no wolf is
 *  needed: every turn of the machine (Machines.period(6)) makes up to {@link #MOVES} moves inside the one master container - it tops up
 *  a part stack of an item from another part stack of it, else it takes a stack that is not on the page of its category there. A move is a
 *  withdrawal and a deposit of the same container in one journal batch (ids sort/&lt;game time&gt;/&lt;i&gt;, never repeated after a restart); nothing is
 *  made or lost, and a relic of a quest (QuestSites.RELIC in its NBT) is never moved. A courier's or a wolf's delivery to a sorting store goes
 *  straight onto the page of its category (WarehouseTrips.store). The pages of the store at VI (twelve, VillageStyle.WAREHOUSE_MARKS show
 *  them): 0-1 intake, 2-3 stone and earth, 4 wood, 5-6 food and crops, 7 ores, metals and fuel, 8 tools, weapons and armour, 9 animal goods,
 *  10-11 other goods and science. */
public final class WarehouseSort {
 private WarehouseSort(){}
 public static final int INTAKE=0,STONE=1,WOOD=2,FOOD=3,ORE=4,GEAR=5,ANIMAL=6,OTHER=7,CATEGORIES=8;
 public static final String[] NAMES={"intake","stone","wood","food","ore","gear","animal","other"};
 /** The pages of each category, in the master's order (page p holds slots p*54..p*54+53). */
 private static final int[][] PAGES={{0,1},{2,3},{4},{5,6},{7},{8},{9},{10,11}};
 public static final int MOVES=8;
 /** Whether this warehouse sorts now: its own machine at its working level (VI) and a store of every page. */
 public static boolean sorting(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(!WarehouseStore.is(b)||!Automation.at(l,e,b).sort())return false;var c=LogisticsRoutes.chest(l,e,b);return c!=null&&c.getContainerSize()>=WarehouseStore.slots(6);}
 /** The pages of a category. */
 public static int[] pages(int category){return PAGES[category].clone();}
 /** The category a page holds. */
 public static int categoryOfPage(int page){for(int c=0;c<PAGES.length;c++)for(int p:PAGES[c])if(p==page)return c;return OTHER;}
 /** A relic of a quest: never moved by the machine. */
 public static boolean relic(ItemStack s){return s.getTag()!=null&&s.getTag().contains(QuestSites.RELIC);}
 private static boolean named(Item item,String... parts){var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();for(var p:parts)if(id.contains(p))return true;return false;}
 /** The category of an item (never INTAKE). */
 public static int category(ItemStack s){
  if(s.isEmpty())return OTHER;var item=s.getItem();
  if(item instanceof TieredItem||item instanceof ArmorItem||item instanceof ShearsItem||item instanceof ProjectileWeaponItem||item instanceof ShieldItem||item instanceof FishingRodItem||item instanceof FlintAndSteelItem)return GEAR;
  if(s.is(ItemTags.LOGS)||s.is(ItemTags.PLANKS)||s.is(ItemTags.WOODEN_SLABS)||s.is(ItemTags.WOODEN_STAIRS)||s.is(ItemTags.WOODEN_FENCES)||s.is(ItemTags.WOODEN_DOORS)||s.is(ItemTags.WOODEN_TRAPDOORS)||s.is(ItemTags.SAPLINGS)||s.is(ItemTags.SIGNS)||s.is(ItemTags.BOATS)
   ||s.is(Items.STICK)||s.is(Items.CHEST)||s.is(Items.BARREL)||s.is(Items.CRAFTING_TABLE)||s.is(Items.BOWL)||s.is(Items.LADDER))return WOOD;
  if(s.is(ItemTags.COALS)||s.is(Items.REDSTONE)||s.is(Items.LAPIS_LAZULI)||s.is(Items.QUARTZ)||s.is(Items.AMETHYST_SHARD)||s.is(Items.EMERALD)||s.is(Items.DIAMOND)||s.is(Items.NETHERITE_SCRAP)
   ||named(item,"ingot","nugget","raw_","_ore","gem"))return ORE;
  if(item.getFoodProperties(s,null)!=null||s.is(Items.WHEAT)||s.is(Items.WHEAT_SEEDS)||s.is(Items.BEETROOT_SEEDS)||s.is(Items.MELON_SEEDS)||s.is(Items.PUMPKIN_SEEDS)||s.is(Items.SUGAR_CANE)||s.is(Items.SUGAR)
   ||s.is(Items.PUMPKIN)||s.is(Items.MELON)||s.is(Items.HAY_BLOCK)||s.is(Items.COCOA_BEANS)||s.is(Items.BONE_MEAL)||named(item,"flour","dough"))return FOOD;
  if(s.is(ItemTags.WOOL)||s.is(ItemTags.WOOL_CARPETS)||s.is(Items.LEATHER)||s.is(Items.FEATHER)||s.is(Items.EGG)||s.is(Items.STRING)||s.is(Items.BONE)||s.is(Items.RABBIT_HIDE)||s.is(Items.INK_SAC)||s.is(Items.HONEYCOMB)||s.is(Items.LEAD)||s.is(Items.SADDLE))return ANIMAL;
  if(item instanceof BlockItem block&&(s.is(ItemTags.STONE_BRICKS)||s.is(ItemTags.SAND)||s.is(ItemTags.DIRT)||s.is(ItemTags.STONE_CRAFTING_MATERIALS)||s.is(ItemTags.STONE_TOOL_MATERIALS)
   ||named(item,"stone","cobble","brick","andesite","diorite","granite","deepslate","tuff","calcite","gravel","sand","clay","glass","terracotta","concrete","mud","dirt")))return STONE;
  if(s.is(Items.CLAY_BALL)||s.is(Items.BRICK)||s.is(Items.FLINT))return STONE;
  return OTHER;
 }
 /** The slots of a category's pages. */
 private static List<Integer> slots(int category){var out=new ArrayList<Integer>();for(int p:PAGES[category])for(int i=0;i<WarehouseStore.PAGE;i++)out.add(p*WarehouseStore.PAGE+i);return out;}
 private static boolean fits(Container c,int slot,ItemStack item){var cur=c.getItem(slot);return cur.isEmpty()||ItemStack.isSameItemSameTags(cur,item)&&cur.getCount()+item.getCount()<=Math.min(cur.getMaxStackSize(),c.getMaxStackSize());}
 /** Where a delivery goes in a sorting store: a part stack of the item on its category's pages, else a free slot there, else the other goods,
  *  else the intake; -1 when none of them takes it whole (the caller deposits it anywhere). */
 public static int depositSlot(Container c,ItemStack item){
  if(c.getContainerSize()<WarehouseStore.slots(6)||item.isEmpty())return -1;
  int own=relic(item)?INTAKE:category(item);
  for(int cat:new int[]{own,OTHER,INTAKE}){
   for(int s:slots(cat))if(!c.getItem(s).isEmpty()&&fits(c,s,item))return s;
   for(int s:slots(cat))if(c.getItem(s).isEmpty())return s;}
  return -1;
 }
 /** One turn of the machine: up to MOVES moves; returns how many stacks moved. */
 public static int step(ServerLevel l,SettlementData.Entry e,Settlement.Building b,long now){
  if(!sorting(l,e,b))return 0;var c=LogisticsRoutes.chest(l,e,b);var at=LogisticsRoutes.position(e,b);if(c==null||!l.hasChunkAt(at))return 0;
  int moved=0;
  for(int i=0;i<MOVES;i++){var m=next(c);if(m==null)break;final int n=i;final int[] mv=m;
   boolean ok=WorldJournal.batch(l,()->move(l,b,at,c,mv,now,n));if(!ok)break;moved++;}
  return moved;
 }
 /** The next move {from, to, count}: first a part stack topped up from another part stack of the same item on the same category's pages,
  *  then a stack off its category's pages carried to them; null when the store is in order. */
 static int[] next(Container c){
  int size=Math.min(c.getContainerSize(),WarehouseStore.slots(6));
  // Top up: two part stacks of one item in the pages of its category.
  for(int cat=0;cat<CATEGORIES;cat++){var list=slots(cat);
   for(int a=0;a<list.size();a++){var sa=c.getItem(list.get(a));if(sa.isEmpty()||relic(sa)||sa.getCount()>=sa.getMaxStackSize())continue;
    for(int z=list.size()-1;z>a;z--){var sz=c.getItem(list.get(z));if(sz.isEmpty()||relic(sz)||!ItemStack.isSameItemSameTags(sa,sz))continue;
     return new int[]{list.get(z),list.get(a),Math.min(sz.getCount(),sa.getMaxStackSize()-sa.getCount())};}}}
  // Carry a stack to its category (the intake is emptied first, then the pages in order).
  for(int slot=0;slot<size;slot++){var s=c.getItem(slot);if(s.isEmpty()||relic(s))continue;int page=slot/WarehouseStore.PAGE,own=category(s),here=categoryOfPage(page);
   if(here==own)continue;
   // An item already on the other goods' pages stays when its own pages are full.
   int to=-1;for(int t:slots(own))if(!c.getItem(t).isEmpty()&&fits(c,t,s)){to=t;break;}
   if(to<0)for(int t:slots(own))if(c.getItem(t).isEmpty()){to=t;break;}
   if(to<0&&here!=OTHER&&here!=INTAKE)continue;
   if(to<0&&here==INTAKE)for(int t:slots(OTHER))if(c.getItem(t).isEmpty()){to=t;break;}
   if(to<0)continue;
   return new int[]{slot,to,s.getCount()};}
  return null;
 }
 private static boolean move(ServerLevel l,Settlement.Building b,net.minecraft.core.BlockPos at,Container c,int[] m,long now,int i){
  var before=c.getItem(m[0]).copy();if(before.isEmpty()||m[2]<=0||m[2]>before.getCount())return false;
  var id=Settlement.childId(b.id(),"sort/"+now+"/"+i);
  var taken=WorldJournal.takeAmount(l,Settlement.childId(id,"take"),at,m[0],before,m[2]);if(taken.isEmpty())return false;
  if(WorldJournal.putSlot(l,Settlement.childId(id,"put"),at,m[1],taken))return true;
  // The slot changed under it: the stack goes back into the store wherever it fits (it is never lost).
  if(!WorldJournal.deposit(l,Settlement.childId(id,"back"),at,taken))throw new IllegalStateException("A sorted stack found no slot in its own store");
  return false;
 }
 /** Stacks on the wrong page (for the card and the tests). */
 public static int unsorted(Container c){int n=0;int size=Math.min(c.getContainerSize(),WarehouseStore.slots(6));for(int s=0;s<size;s++){var st=c.getItem(s);if(!st.isEmpty()&&!relic(st)&&category(st)!=categoryOfPage(s/WarehouseStore.PAGE))n++;}return n;}
}
