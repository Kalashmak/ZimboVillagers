package org.villageastra.world;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-138 (owner 2026-09-22): the pens of the livestock yard — 1/2/4 of them at levels I/II/III (core effect "pens"), 5x5 inside a 7x7
 *  fence, each with its own feeder and trough; the keeper breeds a pen while it holds fewer than PEN_CAP and culls one above it. The pens'
 *  cells come from balance/livestock.json and agree with the yard's plan (VillageStyle.YARD_PENS). An animal belongs to a pen by its tags
 *  (AstraYard, AstraPen), not by where it stands; an owned animal without a pen tag standing inside a pen is given that pen. */
public final class LivestockPens {
 private LivestockPens(){}
 public static final String YARD_TAG="AstraYard",PEN_TAG="AstraPen";
 private static final JsonObject ROOT=read();
 private static JsonObject read(){try(var s=LivestockPens.class.getResourceAsStream("/data/villageastra/balance/livestock.json")){if(s==null)throw new IllegalStateException("Missing livestock balance");return JsonParser.parseReader(new InputStreamReader(s,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
 /** More than this in a pen and one adult is culled. */
 public static final int PEN_CAP=ROOT.get("pen_cap").getAsInt();
 /** Fewer adults than this and the keeper brings in wild stock of the pen's kind. */
 public static final int KEEP=ROOT.get("keep").getAsInt();
 /** A pen's culls per village day at most (a surplus waits for the next day). */
 public static final int CULLS_PER_DAY=ROOT.get("culls_per_day").getAsInt();
 /** Feed of each kind its pens keep that the yard chest holds; porters bring it, and it is not taken away for anything else. */
 public static final int FEED_STOCK=ROOT.get("feed_stock").getAsInt();
 /** Every kind of feed the pens can ask for. */
 public static final Set<Item> FEEDS=feeds();
 private static Set<Item> feeds(){var out=new HashSet<Item>();for(var en:ROOT.getAsJsonObject("feed").entrySet())out.add(BuiltInRegistries.ITEM.get(new ResourceLocation(en.getValue().getAsString())));return Set.copyOf(out);}
 private static final Set<Item> PRODUCTS=Set.of(Items.LEATHER,Items.BEEF,Items.MUTTON,Items.CHICKEN,Items.PORKCHOP,Items.EGG,Items.FEATHER,Items.RABBIT,Items.RABBIT_HIDE);
 /** What a yard gives the village (spec F6): wool, leather, meat, eggs, feathers - swept to the warehouse like the farm's harvest. */
 public static boolean product(net.minecraft.world.item.ItemStack s){return PRODUCTS.contains(s.getItem())||s.is(net.minecraft.tags.ItemTags.WOOL);}
 /** AD-138 (spec §10.2): what a working yard asks the porters for, into its own chest: the feed of every pen's kind up to FEED_STOCK,
  *  shears while a pen keeps sheep (no lead: the keeper drives new stock in, owner 2026-09-23). */
 public static List<Workshops.Want> wants(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){var out=new ArrayList<Workshops.Want>();var chest=LogisticsRoutes.chest(l,e,yard);if(chest==null)return out;
  var feeds=new LinkedHashSet<Item>();boolean sheep=false;
  for(var p:LivestockGoal.pens(l,e,yard)){var kind=LivestockGoal.species(l,e,yard,p);feeds.add(feed(kind));sheep|=kind.equals("sheep");
  }
  for(var f:feeds){int missing=FEED_STOCK-LogisticsRoutes.count(chest,s->s.is(f));if(missing>0)out.add(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(f),missing,yard.id()));}
  if(sheep&&LogisticsRoutes.count(chest,s->s.getItem() instanceof net.minecraft.world.item.ShearsItem)==0)out.add(new Workshops.Want(net.minecraft.world.item.crafting.Ingredient.of(Items.SHEARS),1,yard.id()));
  return out;}
 /** One pen, in yard cells: its fence's north-west corner, the side its gate opens to, the level that builds it and its default kind. */
 public record Pen(int index,int x,int z,boolean gateEast,int level,String species){
  public BlockPos feeder(){return new BlockPos(x+2,1,z+1);}
  public List<BlockPos> trough(){return List.of(new BlockPos(x+4,0,z+1),new BlockPos(x+5,0,z+1));}
  public BlockPos gate(){return new BlockPos(gateEast?x+6:x,1,z+3);}
  /** The lane cell before the gate and the pen cell behind it. */
  public BlockPos outside(){return gate().offset(gateEast?1:-1,0,0);}
  public BlockPos inside(){return gate().offset(gateEast?-1:1,0,0);}
  /** The walk cell on the other side of the fence from the feeder, where the keeper stands to fill it. */
  public BlockPos walk(){return new BlockPos(x+2,1,z-1);}
  public boolean interior(int lx,int lz){return lx>x&&lx<x+6&&lz>z&&lz<z+6;}
  /** Within the fence, gate included. */
  public boolean fenced(int lx,int lz){return lx>=x&&lx<=x+6&&lz>=z&&lz<=z+6;}
 }
 private static final List<Pen> PENS=pens();
 private static List<Pen> pens(){var out=new ArrayList<Pen>();int i=1;
  for(var raw:ROOT.getAsJsonArray("pens")){var o=raw.getAsJsonObject();out.add(new Pen(i++,o.get("x").getAsInt(),o.get("z").getAsInt(),o.get("gate").getAsString().equals("east"),o.get("level").getAsInt(),o.get("species").getAsString()));}
  if(out.size()!=VillageStyle.YARD_PENS.length)throw new IllegalStateException("Pens differ from the yard plan");
  for(int k=0;k<out.size();k++){var p=out.get(k);var v=VillageStyle.YARD_PENS[k];if(p.x()!=v[0]||p.z()!=v[1]||(p.gateEast()?1:0)!=v[2]||p.level()!=v[3])throw new IllegalStateException("Pen "+p.index()+" differs from the yard plan");}
  return List.copyOf(out);}
 public static List<Pen> all(){return PENS;}
 public static Pen pen(int index){return index>=1&&index<=PENS.size()?PENS.get(index-1):null;}
 /** Pens a yard works at this level (core effect "pens": 1/2/4/4/4/4). */
 public static int pens(int level){return CoreEffects.value("livestock","pens",level);}
 /** Head the yard holds at this level (core effect "herd": PEN_CAP per pen). */
 public static int capacity(int level){return CoreEffects.value("livestock","herd",level);}
 public static List<Pen> built(int level){int n=pens(level);return PENS.stream().filter(p->p.index()<=n).toList();}
 /** What a kind of animal eats (balance/livestock.json feed). */
 public static Item feed(String species){var f=ROOT.getAsJsonObject("feed");return f.has(species)?BuiltInRegistries.ITEM.get(new ResourceLocation(f.get(species).getAsString())):Items.WHEAT;}
 public static String species(Animal a){return a instanceof Sheep?"sheep":a instanceof Cow?"cow":a instanceof Chicken?"chicken":a instanceof Pig?"pig":"";}
 public static Class<? extends Animal> kind(String species){return switch(species){case "cow"->Cow.class;case "chicken"->Chicken.class;case "pig"->Pig.class;default->Sheep.class;};}
 public static boolean kept(Animal a){return !species(a).isEmpty()&&!(a instanceof MushroomCow);}
 public static BlockPos at(SettlementData.Entry e,Settlement.Building yard,BlockPos local){return BuildingPlacement.at(e,yard,local.getX(),local.getY(),local.getZ());}
 /** The pen's inside in the world (the yard turned), a block up and three high. */
 public static AABB box(SettlementData.Entry e,Settlement.Building yard,Pen p){
  var a=at(e,yard,new BlockPos(p.x()+1,1,p.z()+1));var b=at(e,yard,new BlockPos(p.x()+5,1,p.z()+5));
  return new AABB(Math.min(a.getX(),b.getX()),a.getY(),Math.min(a.getZ(),b.getZ()),Math.max(a.getX(),b.getX())+1,a.getY()+3,Math.max(a.getZ(),b.getZ())+1);
 }
 /** The whole yard's pens and lane, for gathering drops and finding the herd. */
 public static AABB yardBox(SettlementData.Entry e,Settlement.Building yard){var o=BuildingPlacement.origin(e,yard);var s=BuildingPlacement.size(yard.type(),yard.rotation());return new AABB(o.getX(),o.getY(),o.getZ(),o.getX()+s[0],o.getY()+4,o.getZ()+s[1]);}
 /** The pen whose inside holds this world position, or null. */
 public static Pen penAt(SettlementData.Entry e,Settlement.Building yard,BlockPos world){var l=BuildingPlacement.local(e,yard,world);for(var p:PENS)if(p.interior(l.getX(),l.getZ()))return p;return null;}
 public static boolean inside(SettlementData.Entry e,Settlement.Building yard,Pen p,BlockPos world){var l=BuildingPlacement.local(e,yard,world);return p.interior(l.getX(),l.getZ());}
 public static boolean fenced(SettlementData.Entry e,Settlement.Building yard,Pen p,BlockPos world){var l=BuildingPlacement.local(e,yard,world);return p.fenced(l.getX(),l.getZ());}
 /** The animals of one pen: owned by the village, of this yard and pen by their tags — an owned one with no pen tag standing inside the pen
  *  (a newborn, or a herd kept before the pens) is given this pen. Looked for over the whole yard and a margin, so one that slipped out
  *  through an open gate still counts. */
 public static List<Animal> herd(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,Pen p){
  var village=e.settlement().id();var out=new ArrayList<Animal>();
  for(var a:l.getEntitiesOfClass(Animal.class,yardBox(e,yard).inflate(16),x->x.isAlive()&&LivestockGoal.owned(x,village))){
   var t=a.getPersistentData();
   if(!t.contains(PEN_TAG)){if(!inside(e,yard,p,a.blockPosition()))continue;t.putUUID(YARD_TAG,yard.id());t.putInt(PEN_TAG,p.index());}
   if(t.getInt(PEN_TAG)==p.index()&&(!t.hasUUID(YARD_TAG)||t.getUUID(YARD_TAG).equals(yard.id())))out.add(a);}
  return out;
 }
 /** AD-138: the yard card's line for each pen it works: its kind, head (of PEN_CAP), adults, and how full its feeder is. */
 public static net.minecraft.nbt.ListTag view(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){var out=new net.minecraft.nbt.ListTag();
  for(var p:LivestockGoal.pens(l,e,yard)){var t=new net.minecraft.nbt.CompoundTag();var herd=herd(l,e,yard,p);var f=l.getBlockState(at(e,yard,p.feeder()));
   t.putInt("pen",p.index());t.putString("species",LivestockGoal.species(l,e,yard,p));t.putInt("head",herd.size());t.putInt("adults",(int)herd.stream().filter(a->!a.isBaby()).count());
   t.putInt("cap",PEN_CAP);t.putInt("feed",f.getBlock() instanceof FeederBlock?f.getValue(FeederBlock.FEED):-1);out.add(t);}
  return out;}
 /** Tags an animal as this pen's. */
 public static void tag(Animal a,UUID village,Settlement.Building yard,Pen p){var t=a.getPersistentData();t.putUUID(LivestockGoal.OWNER,village);t.putUUID(YARD_TAG,yard.id());t.putInt(PEN_TAG,p.index());a.setPersistenceRequired();LivestockGrazing.attach(a);}
}
