package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-104 P2, "a level-I farm feeds its village in play": the farmer reaps a plot and sows it again from the seed it gave, and brings the batch
 *  home in one visit, leaving the seed the farm chest does not need in the soil; the mill and the bakery work in batches and keep the burn time
 *  they paid for; an idle adult bakes bread by hand at the hall (5 wheat for 2, no fuel) while the chain does not work and the pantry runs low,
 *  or once a meal was missed; a village meal counts rations; the farm card says which way its field feeds. Every change goes through the
 *  journal, so the tests count real items in real chests. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class StarterFoodGameTests {
 private static Path root(ServerLevel l){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);}
 private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
 private static int slot(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))return i;throw new IllegalStateException("Missing fixture item "+item);}
 private static void clear(Container c,Item item){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(item))c.setItem(i,ItemStack.EMPTY);}
 private static void put(Container c,ItemStack stack){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,stack);return;}throw new IllegalStateException("No room for "+stack);}
 private static int count(List<ItemStack> stacks,Item item){return stacks.stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
 private static ItemStack stack(Item item,int n){return new ItemStack(item,n);}
 // ---- a starter village ---------------------------------------------------------------------------
 private record Village(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos origin){
  Settlement.Building building(String type){return s.buildings().stream().filter(b->b.type().equals(type)).findFirst().orElseThrow();}
  OwnedChestEntity chest(String type){return LogisticsRoutes.chest(l,e,building(type));}
  Resident resident(Profession p){return s.residents().stream().filter(r->r.profession()==p).findFirst().orElseThrow();}
  ResidentEntity npc(Profession p){return (ResidentEntity)l.getEntity(resident(p).id());}
  Path work(){return root(l).resolve("data/astra-work/"+building("farm").id()+".bin");}
 }
 private static Village village(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);
  // Nobody walks off: a test drives the one goal it looks at and stands its worker where that goal works.
  for(var r:s.residents())if(l.getEntity(r.id()) instanceof ResidentEntity npc)npc.setNoAi(true);
  return new Village(l,s,SettlementData.get(l.getServer()).entry(s.id()),origin);
 }
 private static void done(Village v){
  for(var r:v.s.residents()){HandBread.release(v.l,v.s.id(),r.id());var npc=v.l.getEntity(r.id());if(npc!=null)npc.discard();}
  delete(HandBread.path(v.l,v.s.id()));delete(root(v.l).resolve("data/astra-unlocks/"+v.s.id()+".bin"));
  for(var b:v.s.buildings()){delete(root(v.l).resolve("data/astra-work/"+b.id()+".bin"));delete(Workshops.path(v.l,b.id()));}
  SettlementData.get(v.l.getServer()).remove(v.s.id());
 }
 /** A crop stands only in light (8 or more) and the test area lies deep in the test world's ground: the field is lit from above first and gets
  *  a second for the light to spread before the village is laid (as FarmGenerationGameTests does), so no reaped or sown plot pops its neighbours.
  *  The lot stands empty for that second, and in the shared default batch a neighbour's fixture (a quest site looking for free ground, a
  *  grown lot) took it and StarterVillage refused the village as "occupied": these tests run in a batch of their own ({@link #FIELD_BATCH}). */
 static final String FIELD_BATCH="farmer_lit_field";
 private static void onLitField(GameTestHelper h,java.util.function.Consumer<Village> test){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var field=origin.offset(StarterVillage.BUILDINGS[4][0],0,StarterVillage.BUILDINGS[4][1]);var box=FarmField.box(FarmField.modules(1));
  lotIsFree(h,origin,"before the field is lit");
  for(int x=box[0];x<=box[2];x++)for(int z=box[1];z<=box[3];z++)l.setBlock(field.offset(x,FarmField.HEADROOM+2,z),Blocks.LIGHT.defaultBlockState(),2);
  h.runAfterDelay(20,()->{lotIsFree(h,origin,"when the village is laid");var v=village(h);try{test.accept(v);}finally{done(v);}h.succeed();});
 }
 /** Names the first cell of the starter village's lot that StarterVillage would refuse as "occupied", and what stands there. */
 private static void lotIsFree(GameTestHelper h,BlockPos origin,String when){
  var l=h.getLevel();
  for(var p:StarterVillage.layout(origin).keySet()){if(p.getY()<origin.getY())continue;var s=l.getBlockState(p);
   if(!s.isAir()&&!s.is(Blocks.GRASS)&&!s.is(Blocks.TALL_GRASS)||l.getBlockEntity(p)!=null||!s.getFluidState().isEmpty())
    throw new GameTestAssertException("The starter village's lot is occupied "+when+" at "+h.relativePos(p)+" by "+s);}
 }
 /** The field's plots, all wheat just sown but for the first 'ripe', which are ripe (the first plots of the module, side by side). */
 private static List<BlockPos> field(Village v,int ripe){
  var cells=FarmField.cells(v.e,v.building("farm"));
  for(int i=0;i<cells.size();i++)v.l.setBlock(cells.get(i),Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,i<ripe?7:0),2);
  return cells;
 }
 // ---- the farmer ------------------------------------------------------------------------------------
 /** Where the farmer works the stage his record is at: beside the hall chest for the hoe, beside the farm chest to deliver, on the plot to reap or sow. */
 private static BlockPos stand(Village v,CompoundTag t){
  return switch(t.getString("stage")){
   case "tool"->LogisticsRoutes.position(v.e,Workshops.hall(v.e)).east();
   case "deliver"->LogisticsRoutes.position(v.e,v.building("farm")).east();
   case "dig","replant"->BlockPos.of(t.getLong("target"));
   default->null;};
 }
 private record Batch(int deliveries,List<UUID> harvests,CompoundTag record){}
 /** The farmer's own goal driven call by call (no players, no one-second cadence), the farmer stood where each stage works — the walks are the
  *  client probe's — until his batch is home. The goal keeps a noon clock of its own, so the batch goes home by the minute's wait, not the evening. */
 private static Batch reap(GameTestHelper h,Village v){
  var farmer=v.npc(Profession.FARMER);var goal=new ResourceWorkGoal(farmer,true,()->6000L);
  h.assertTrue(goal.canUse(),"The farmer takes up his work");
  var harvests=new ArrayList<UUID>();int deliveries=0;String was="";var t=NbtRecord.read(v.work());
  for(int call=0;call<1000;call++){
   var at=stand(v,t);if(at!=null)farmer.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
   goal.tick();t=NbtRecord.read(v.work());var stage=t.getString("stage");
   if(t.hasUUID("lastHarvest")&&!harvests.contains(t.getUUID("lastHarvest")))harvests.add(t.getUUID("lastHarvest"));
   if(stage.equals("deliver")&&!was.equals("deliver"))deliveries++;
   if(deliveries>0&&stage.equals("choose")&&t.getList("cargo",Tag.TAG_COMPOUND).isEmpty())return new Batch(deliveries,harvests,t);
   was=stage;
  }
  throw new IllegalStateException("The batch never came home: "+t);
 }
 /** What a reaping really gave, from its journal receipt: the whole vanilla loot, whatever the farmer carried home of it. */
 private static int loot(ServerLevel l,UUID harvest,Item item){
  var receipt=WorldJournal.recoverExisting(l,harvest);if(receipt==null)throw new IllegalStateException("Missing harvest receipt "+harvest);
  int n=0;for(var raw:receipt.getList("loot",Tag.TAG_COMPOUND)){var s=ItemStack.of((CompoundTag)raw);if(s.is(item))n+=s.getCount();}return n;
 }
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void farmerReapsAndResowsTenPlotsInOneBatch(GameTestHelper h){onLitField(h,v->{
  var cells=field(v,10);var farm=v.chest("farm");var hall=v.chest("town_hall");
  var batch=reap(h,v);
  for(int i=0;i<cells.size();i++){var crop=v.l.getBlockState(cells.get(i));h.assertTrue(crop.is(Blocks.WHEAT)&&crop.getValue(CropBlock.AGE)==0,"Plot "+i+" holds wheat just sown, reaped or not: "+crop);}
  int seeds=0;for(var id:batch.harvests())seeds+=loot(v.l,id,Items.WHEAT_SEEDS);
  h.assertTrue(batch.harvests().size()==10&&batch.record().getInt("reaped")==10,"Ten plots reaped: "+batch.harvests().size()+", reaped="+batch.record().getInt("reaped"));
  h.assertTrue(batch.deliveries()==1,"The ten reapings went home as one batch: "+batch.deliveries()+" deliveries");
  h.assertTrue(farm.countItem(Items.WHEAT)==10&&farm.countItem(Items.WHEAT_SEEDS)==seeds-10,"The farm chest holds the ten wheat and every seed but the ten sown again: wheat="+farm.countItem(Items.WHEAT)+" seeds="+farm.countItem(Items.WHEAT_SEEDS)+" of "+seeds+" reaped");
  h.assertTrue(hall.countItem(Items.WHEAT_SEEDS)==16,"No seed was fetched from the hall: "+hall.countItem(Items.WHEAT_SEEDS));
  var hoe=ItemStack.of(batch.record().getCompound("tool"));
  h.assertTrue(hoe.is(Items.STONE_HOE)&&hoe.getDamageValue()==0,"Reaping a crop wears no hoe: "+hoe+" damage "+hoe.getDamageValue());
 });}
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void farmerLeavesSurplusSeedsInTheSoil(GameTestHelper h){onLitField(h,v->{
  field(v,3);var farm=v.chest("farm");int before=ResourceWorkGoal.SEED_KEEP-1;farm.setItem(0,stack(Items.WHEAT_SEEDS,before));
  var batch=reap(h,v);int seeds=0;boolean spare=false;
  for(var id:batch.harvests()){int got=loot(v.l,id,Items.WHEAT_SEEDS);seeds+=got;spare|=got>=2;
   h.assertTrue(loot(v.l,id,Items.WHEAT)==1&&got>=1&&got<=4,"The receipt keeps the reaping's whole vanilla loot: 1 wheat and "+got+" seeds");}
  // He carries home only the seed that fills the chest to SEED_KEEP, and he has one to carry once a reaping gave more than the one he sows
  // again; the expectation is read from the receipts, so the 1-in-2000 run of three single seeds is no flake.
  int kept=before+(spare?1:0);
  h.assertTrue(batch.harvests().size()==3&&farm.countItem(Items.WHEAT)==3,"Three plots reaped and their wheat delivered: "+batch.harvests().size()+"/"+farm.countItem(Items.WHEAT));
  h.assertTrue(farm.countItem(Items.WHEAT_SEEDS)==kept,"The farm chest keeps "+ResourceWorkGoal.SEED_KEEP+" seeds and the rest stays in the soil: "+farm.countItem(Items.WHEAT_SEEDS)+", expected "+kept+"; "+seeds+" reaped, 3 sown again, "+(seeds-3-(kept-before))+" left in the soil");
 });}
 /** Bread margin (0.9.1): the hall bakes by hand, its pantry is short of a day and no store holds a job's wheat, so the farmer brings the first
  *  whole unit of wheat home at once instead of carrying the morning's harvest until the batch is full or the evening. */
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void aHungryHallGetsTheFirstUnitOfWheatAtOnce(GameTestHelper h){onLitField(h,v->{
  field(v,10);var farm=v.chest("farm");var hall=v.chest("town_hall");clear(hall,Items.BREAD);clear(hall,Items.WHEAT);clear(farm,Items.WHEAT);
  h.assertTrue(HandBread.open(v.l,v.e)&&HandBread.wheatAvailable(v.l,v.e)==0,"An empty pantry and no wheat in store: the hall would bake by hand");
  var batch=reap(h,v);
  h.assertTrue(batch.deliveries()==1&&batch.record().getInt("reaped")==HandBread.WHEAT_PER_UNIT&&farm.countItem(Items.WHEAT)==HandBread.WHEAT_PER_UNIT,
   "The first "+HandBread.WHEAT_PER_UNIT+" wheat went home at once: reaped="+batch.record().getInt("reaped")+" farm wheat="+farm.countItem(Items.WHEAT));
  h.assertTrue(HandBread.actionable(v.l,v.e),"The baker has a whole unit to fetch now");
 });}
 /** A farmer killed with a batch in hand drops it once, in one pile where he fell, with his one unworn hoe: a sowing that took place has spent
  *  its seed, a reaping that took place adds its loot — of its seed only what he would have carried home. The hall is neither refunded nor debited. */
 private static void killedMidBatch(GameTestHelper h,String variant){onLitField(h,v->{
  var l=v.l;var hall=v.chest("town_hall");var farmer=v.npc(Profession.FARMER);var op=UUID.randomUUID();
  var hoe=WorldJournal.take(l,UUID.randomUUID(),hall.getBlockPos(),slot(hall,Items.STONE_HOE),hall.getItem(slot(hall,Items.STONE_HOE)).copy());
  var target=field(v,0).get(40);var ripe=Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7);List<ItemStack> loot=List.of();
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("worker",farmer.getUUID());t.putUUID("operation",op);t.put("tool",hoe.save(new CompoundTag()));
  t.putLong("target",target.asLong());t.putString("crop","wheat");
  var cargo=new ListTag();cargo.add(stack(Items.WHEAT,3).save(new CompoundTag()));cargo.add(stack(Items.WHEAT_SEEDS,5).save(new CompoundTag()));t.put("cargo",cargo);
  if(variant.equals("reaped")){
   l.setBlock(target,ripe,3);t.putString("stage","dig");t.put("before",NbtUtils.writeBlockState(ripe));t.putInt("labor",80);t.putInt("seedAllowance",1);
   loot=WorldJournal.harvest(l,op,target,ripe,hoe);h.assertTrue(loot!=null&&count(loot,Items.WHEAT)==1&&count(loot,Items.WHEAT_SEEDS)>=1,"The reaping took place ahead of the record: "+loot);
  }else{
   l.setBlock(target,Blocks.AIR.defaultBlockState(),3);t.putString("stage","replant");
   if(variant.equals("sown"))h.assertTrue(WorldJournal.place(l,Settlement.childId(op,"replant"),target,Blocks.AIR.defaultBlockState(),Blocks.WHEAT.defaultBlockState()),"The sowing took place ahead of the record");
  }
  NbtRecord.write(v.work(),t);
  var at=v.origin.offset(8,1,8);farmer.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
  farmer.hurt(l.damageSources().genericKill(),1000);CargoCustody.tick(l.getServer());CargoCustody.tick(l.getServer());
  var drops=CargoCustody.inspect(l.getServer(),farmer.getUUID()).getList("drops",Tag.TAG_COMPOUND);
  h.assertTrue(drops.size()==1,"One pile for the whole batch, however often custody runs: "+drops.size());
  var pile=(Container)l.getBlockEntity(BlockPos.of(drops.getCompound(0).getLong("pos")));
  int wheat=3+count(loot,Items.WHEAT),seeds=5-(variant.equals("sown")?1:0)+Math.min(1,count(loot,Items.WHEAT_SEEDS));
  h.assertTrue(pile.countItem(Items.STONE_HOE)==1&&pile.getItem(slot(pile,Items.STONE_HOE)).getDamageValue()==0,"The one real hoe lies in the pile, unworn by reaping");
  h.assertTrue(pile.countItem(Items.WHEAT)==wheat&&pile.countItem(Items.WHEAT_SEEDS)==seeds,variant+": the pile holds "+wheat+" wheat and "+seeds+" seeds: "+pile.countItem(Items.WHEAT)+"/"+pile.countItem(Items.WHEAT_SEEDS));
  h.assertTrue(hall.countItem(Items.WHEAT_SEEDS)==16&&hall.countItem(Items.STONE_HOE)==0,"The hall is neither refunded nor debited: seeds="+hall.countItem(Items.WHEAT_SEEDS));
  var reset=NbtRecord.read(v.work());
  h.assertTrue(reset.getString("stage").equals("tool")&&!reset.hasUUID("worker")&&reset.getList("cargo",Tag.TAG_COMPOUND).isEmpty()&&reset.getInt("reaped")==(variant.equals("reaped")?1:0),"The next farmer starts afresh and the reaping is counted once: "+reset);
  var plot=l.getBlockState(target);
  h.assertTrue(variant.equals("sown")?plot.is(Blocks.WHEAT)&&plot.getValue(CropBlock.AGE)==0:plot.isAir(),"The plot stays as the journal left it: "+plot);
 });}
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void farmerKilledMidBatchDropsCargoOnceAfterTheSowing(GameTestHelper h){killedMidBatch(h,"sown");}
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void farmerKilledMidBatchDropsCargoOnceBeforeTheSowing(GameTestHelper h){killedMidBatch(h,"unsown");}
 @GameTest(template="empty",batch=FIELD_BATCH,timeoutTicks=200) public static void farmerKilledMidBatchDropsCargoOnceAfterTheReaping(GameTestHelper h){killedMidBatch(h,"reaped");}
 // ---- the mill and the bakery ------------------------------------------------------------------------
 private record Bench(ServerLevel l,Settlement s,SettlementData.Entry e,Map<String,Settlement.Building> buildings){
  Settlement.Building building(String type){return buildings.get(type);}
  OwnedChestEntity chest(String type){return LogisticsRoutes.chest(l,e,buildings.get(type));}
 }
 /** WorkshopGameTests' bench: each station on its own lot with its real chest. */
 private static Bench bench(GameTestHelper h,String... types){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var map=new LinkedHashMap<String,Settlement.Building>();int i=0;
  for(var type:types){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,(i%3)*14,0,(i/3)*12);s.addBuilding(b);map.put(type,b);
   var chest=center.offset(b.x()+1,b.y()+1,b.z()+4);l.setBlock(chest.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);i++;}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Bench(l,s,e,map);
 }
 private static void done(Bench b){for(var x:b.buildings.values())delete(Workshops.path(b.l,x.id()));SettlementData.get(b.l.getServer()).remove(b.s.id());}
 private static List<Workshops.Want> want(Item item,int count,Settlement.Building b){return List.of(new Workshops.Want(Ingredient.of(item),count,b.id()));}
 private static boolean asksForCoal(Bench b,String type){return Workshops.published(b.l,b.building(type).id()).stream().anyMatch(in->in.matches(new ItemStack(Items.COAL))||in.matches(new ItemStack(Items.CHARCOAL)));}
 /** Turns of one station 20 ticks apart until its job completes (or 'max' turns); returns the completing turn's status. */
 private static String work(Bench b,String type,List<Workshops.Want> wants,long[] clock,int max,Runnable check){
  String last="";for(int i=0;i<max&&!last.equals("workshop_complete");i++){last=Workshops.advance(b.l,b.e,b.building(type),clock[0],wants);clock[0]+=20;check.run();}return last;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void millGrindsSixteenWheatInOneJob(GameTestHelper h){
  var b=bench(h,"mill");
  try{
   var mill=b.chest("mill");mill.setItem(0,stack(Items.WHEAT,16));var wants=want(VillageAstra.FLOUR.get(),16,b.building("mill"));long[] clock={1000};
   h.assertTrue(work(b,"mill",wants,clock,200,()->{}).equals("workshop_complete"),"The job completes");
   var job=Workshops.inspect(b.l,b.building("mill").id());
   h.assertTrue(mill.countItem(VillageAstra.FLOUR.get())==16&&mill.countItem(Items.WHEAT)==0&&job.getInt("withdrawals")==1,"One job ground all sixteen wheat, taken in one withdrawal: flour="+mill.countItem(VillageAstra.FLOUR.get())+" wheat="+mill.countItem(Items.WHEAT)+" withdrawals="+job.getInt("withdrawals"));
   int more=0;for(int i=0;i<30;i++){if(Workshops.advance(b.l,b.e,b.building("mill"),clock[0],wants).equals("workshop_complete"))more++;clock[0]+=20;}
   h.assertTrue(more==0&&mill.countItem(VillageAstra.FLOUR.get())==16,"With the wheat gone no other job completes: "+more);
  }finally{done(b);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void bakeryBakesEightBreadForOneCoal(GameTestHelper h){
  var b=bench(h,"restaurant");
  try{
   var bakery=b.chest("restaurant");bakery.setItem(0,stack(VillageAstra.FLOUR.get(),16));bakery.setItem(1,stack(Items.COAL,1));long[] clock={1000};
   h.assertTrue(work(b,"restaurant",want(Items.BREAD,8,b.building("restaurant")),clock,200,()->{}).equals("workshop_complete"),"The job completes");
   var job=Workshops.inspect(b.l,b.building("restaurant").id());
   h.assertTrue(bakery.countItem(Items.BREAD)==8&&bakery.countItem(VillageAstra.FLOUR.get())==0&&bakery.countItem(Items.COAL)==0,"Sixteen flour and one coal became eight bread: bread="+bakery.countItem(Items.BREAD)+" flour="+bakery.countItem(VillageAstra.FLOUR.get())+" coal="+bakery.countItem(Items.COAL));
   h.assertTrue(job.getInt("fuelBank")==0&&job.getInt("withdrawals")==2,"The coal burnt exactly eight bakes: bank="+job.getInt("fuelBank")+" withdrawals="+job.getInt("withdrawals"));
  }finally{done(b);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void fuelBankKeepsUnusedBurnTime(GameTestHelper h){
  var b=bench(h,"restaurant");
  try{
   var bakery=b.chest("restaurant");bakery.setItem(0,stack(VillageAstra.FLOUR.get(),4));bakery.setItem(1,stack(Items.COAL,1));var wants=want(Items.BREAD,2,b.building("restaurant"));long[] clock={1000};
   boolean[] coal={false};Runnable watch=()->coal[0]|=asksForCoal(b,"restaurant");
   h.assertTrue(work(b,"restaurant",wants,clock,200,watch).equals("workshop_complete"),"The first job completes");
   h.assertTrue(bakery.countItem(Items.BREAD)==2&&bakery.countItem(Items.COAL)==0&&Workshops.inspect(b.l,b.building("restaurant").id()).getInt("fuelBank")==1200,"Four flour and one coal became two bread, and the coal's other 1200 burn ticks stay in the bank: "+Workshops.inspect(b.l,b.building("restaurant").id()));
   // Idle without flour, the station asks for flour only: its bank covers the next bake.
   h.assertTrue(Workshops.advance(b.l,b.e,b.building("restaurant"),clock[0],wants).equals("workshop_missing_inputs")&&Workshops.inspect(b.l,b.building("restaurant").id()).getInt("fuelBank")==1200,"The idle record keeps the bank");clock[0]+=20;watch.run();
   // Into a free slot: slot 0 now holds the first two loaves.
   int free=0;while(!bakery.getItem(free).isEmpty())free++;bakery.setItem(free,stack(VillageAstra.FLOUR.get(),4));
   h.assertTrue(work(b,"restaurant",wants,clock,200,watch).equals("workshop_complete"),"The second job completes with no fuel item at all");
   h.assertTrue(bakery.countItem(Items.BREAD)==4&&bakery.countItem(VillageAstra.FLOUR.get())==0&&Workshops.inspect(b.l,b.building("restaurant").id()).getInt("fuelBank")==800,"Four more flour baked 2 more bread (4 in total) from the bank, which keeps 800: "+Workshops.inspect(b.l,b.building("restaurant").id()));
   h.assertTrue(!coal[0],"No coal was ever asked for");
  }finally{done(b);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void batchShrinksToTheWheatAtHand(GameTestHelper h){
  var b=bench(h,"mill");
  try{
   var mill=b.chest("mill");mill.setItem(0,stack(Items.WHEAT,5));var wants=want(VillageAstra.FLOUR.get(),16,b.building("mill"));long[] clock={1000};
   h.assertTrue(Workshops.advance(b.l,b.e,b.building("mill"),clock[0],wants).equals("workshop_funding"),"A job starts on five wheat, not waiting for sixteen");clock[0]+=20;
   var job=Workshops.inspect(b.l,b.building("mill").id());var out=ItemStack.of(job.getList("outputs",Tag.TAG_COMPOUND).getCompound(0));
   h.assertTrue(out.is(VillageAstra.FLOUR.get())&&out.getCount()==5&&job.getList("inputs",Tag.TAG_COMPOUND).getCompound(0).getInt("count")==5,"The job is five wheat into five flour: "+job);
   h.assertTrue(work(b,"mill",wants,clock,200,()->{}).equals("workshop_complete")&&mill.countItem(VillageAstra.FLOUR.get())==5&&mill.countItem(Items.WHEAT)==0,"Five flour ground: "+mill.countItem(VillageAstra.FLOUR.get()));
   h.assertTrue(Workshops.advance(b.l,b.e,b.building("mill"),clock[0],wants).equals("workshop_missing_inputs"),"Then it waits for wheat");
   var needs=Workshops.published(b.l,b.building("mill").id());
   h.assertTrue(needs.size()==1&&needs.get(0).matches(new ItemStack(Items.WHEAT))&&needs.get(0).count()==16,"It asks the porters for the whole job the want calls for, sixteen wheat: "+needs.stream().map(Workshops.Input::count).toList());
  }finally{done(b);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void bakeryOffersNoBakedPotato(GameTestHelper h){
  var b=bench(h,"restaurant");
  try{
   var bakery=b.chest("restaurant");bakery.setItem(0,stack(Items.POTATO,8));bakery.setItem(1,stack(Items.COAL,1));var wants=want(Items.BAKED_POTATO,4,b.building("restaurant"));
   // Owner decision 2 (the same food per plot for every crop): a raw potato already feeds at parity, so the village bakery bakes no potatoes.
   h.assertTrue(Workshops.plan(b.l,Workshops.spec("restaurant"),bakery,wants)==null,"The bakery has no job for a baked potato");
   h.assertTrue(Workshops.advance(b.l,b.e,b.building("restaurant"),1000,wants).equals("workshop_idle")&&Workshops.published(b.l,b.building("restaurant").id()).isEmpty(),"It stays idle and asks nobody for potatoes");
   h.assertTrue(bakery.countItem(Items.POTATO)==8&&bakery.countItem(Items.COAL)==1&&bakery.countItem(Items.BAKED_POTATO)==0,"Nothing was taken or made");
   h.assertTrue(Population.nutrition(stack(Items.BAKED_POTATO,1))==5&&Population.nutrition(stack(Items.POTATO,1))==1,"A baked potato a player brings still feeds 5, a raw one 1");
  }finally{done(b);}
  h.succeed();
 }
 // ---- bread by hand at the hall ------------------------------------------------------------------------
 /** The hall of the starter village with no bread and this much wheat: the pantry is empty, so bread by hand may start. */
 private static OwnedChestEntity hungryHall(Village v,int wheat){var hall=v.chest("town_hall");clear(hall,Items.BREAD);if(wheat>0)put(hall,stack(Items.WHEAT,wheat));return hall;}
 /** Turns of the village's hand bread 20 ticks apart until its job completes (or 400 turns): the statuses in order. */
 private static List<String> bake(ServerLevel l,SettlementData.Entry e,long[] clock,UUID baker){
  var out=new ArrayList<String>();for(int turn=0;turn<400;turn++){var status=HandBread.advance(l,e,baker,clock[0]);clock[0]+=HandBread.TURN;out.add(status);if(status.equals("hand_bread_complete"))break;}return out;
 }
 private static String last(List<String> statuses){return statuses.isEmpty()?"":statuses.get(statuses.size()-1);}
 /** A mill and a bakery, each with its own worker: the village makes its bread the efficient way. The lots are registry entries only. */
 private static Resident[] staffChain(Settlement s){
  var mill=new Settlement.Building(Settlement.childId(s.id(),"building/test-mill"),"mill",40,0,0);var bakery=new Settlement.Building(Settlement.childId(s.id(),"building/test-restaurant"),"restaurant",40,0,12);
  s.addBuilding(mill);s.addBuilding(bakery);var home=new Settlement.Home(Settlement.childId(s.id(),"house/test-chain"),1,2,true);s.addHome(home);
  var miller=new Resident(Settlement.childId(s.id(),"resident/test-miller"),Resident.Life.ADULT,false,null,null,-1);var baker=new Resident(Settlement.childId(s.id(),"resident/test-baker"),Resident.Life.ADULT,false,null,null,-1);
  s.admit(miller,home.id());s.admit(baker,home.id());s.assign(miller.id(),Profession.MILLER,mill.id());s.assign(baker.id(),Profession.BAKER,bakery.id());
  return new Resident[]{miller,baker};
 }
 /** AD-104 P2: a staffed bakery with an empty chest has nothing to bake, and still tells the porters what it lacks. Its needs used to be
  *  written only by a job turn, and a turn only started when there was something to make: a new bakery waited for flour it never asked for. */
 @GameTest(template="empty",timeoutTicks=100) public static void anIdleBakeryAsksForTheFlourItLacks(GameTestHelper h){
  var v=village(h);
  try{
   var staff=staffChain(v.s);hungryHall(v,0);
   var bakery=v.s.buildings().stream().filter(b->b.type().equals("restaurant")).findFirst().orElseThrow();var at=LogisticsRoutes.position(v.e,bakery);
   v.l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);v.l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
   StarterVillage.stockChest((net.minecraft.world.level.block.entity.ChestBlockEntity)v.l.getBlockEntity(at),v.s.id(),false);
   var worker=VillageAstra.RESIDENT.get().create(v.l);worker.bind(v.s.id(),staff[1]);worker.moveTo(at.getX()+1.5,at.getY(),at.getZ()+.5,0,0);v.l.addFreshEntity(worker);
   try{
    var goal=new WorkshopGoal(worker,true);
    h.assertTrue(Workshops.published(v.l,bakery.id()).isEmpty(),"Nothing is asked for before the baker looks at his station");
    h.assertTrue(!goal.canUse(),"An empty bakery has nothing to bake, so its baker does not stand at it");
    var needs=Workshops.published(v.l,bakery.id());
    h.assertTrue(needs.stream().anyMatch(in->in.matches(new ItemStack(VillageAstra.FLOUR.get()))),"But it tells the porters it needs flour for the village's bread: "+needs.stream().map(Workshops.Input::count).toList());
   }finally{worker.discard();}
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadBakesFourFromTenWheatWithoutFuel(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,12);int logs=hall.countItem(Items.OAK_LOG),saplings=hall.countItem(Items.OAK_SAPLING);long[] clock={1000};var mayor=v.resident(Profession.MAYOR).id();
   h.assertTrue(HandBread.open(v.l,v.e)&&HandBread.wheatAvailable(v.l,v.e)==12,"An empty pantry and no mill or bakery open the hall's hand bread: wheat="+HandBread.wheatAvailable(v.l,v.e));
   var statuses=bake(v.l,v.e,clock,mayor);var job=HandBread.inspect(v.l,v.s.id());
   h.assertTrue(last(statuses).equals("hand_bread_complete"),"The job is done: "+last(statuses));
   h.assertTrue(hall.countItem(Items.BREAD)==4&&hall.countItem(Items.WHEAT)==2,"Two units: ten wheat became four bread, two wheat wait: bread="+hall.countItem(Items.BREAD)+" wheat="+hall.countItem(Items.WHEAT));
   h.assertTrue(hall.countItem(Items.OAK_LOG)==logs&&hall.countItem(Items.OAK_SAPLING)==saplings,"No fuel is burnt: logs="+hall.countItem(Items.OAK_LOG)+" saplings="+hall.countItem(Items.OAK_SAPLING));
   h.assertTrue(statuses.size()>=4*HandBread.LABOR_PER_BREAD/HandBread.TURN,"Thirty seconds of labour a bread: "+statuses.size()+" turns");
   h.assertTrue(job.getString("stage").equals("idle")&&job.getInt("baked")==4&&job.getUUID("lastBaker").equals(mayor),"The job ends idle and counts its bread and its baker: "+job);
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_missing_wheat")&&hall.countItem(Items.BREAD)==4,"Two wheat make no whole unit, so nothing more starts");
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadWaitsForTheChainAndAFullPantry(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,10);put(hall,stack(Items.BREAD,12));long[] clock={1000};var mayor=v.resident(Profession.MAYOR).id();
   h.assertTrue(HandBread.reserveRations(v.e)==60&&!HandBread.open(v.l,v.e)&&HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_idle"),"A day of meals for six (12 bread, 60 rations) keeps bread by hand shut");clock[0]+=20;
   hall.setItem(slot(hall,Items.BREAD),stack(Items.BREAD,11));
   h.assertTrue(HandBread.lowOnFood(v.l,v.e)&&HandBread.open(v.l,v.e),"Eleven bread are less than a day of meals: the gate opens");
   var chain=staffChain(v.s);var baker=chain[1];
   h.assertTrue(HandBread.chainStaffed(v.l,v.e)&&!HandBread.open(v.l,v.e)&&HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_idle"),"A staffed mill and bakery keep it shut, the pantry low or not");clock[0]+=20;
   baker.missedMeal(clock[0]);baker.missedMeal(clock[0]);
   h.assertTrue(!Population.mayWork(baker)&&!HandBread.chainStaffed(v.l,v.e)&&HandBread.open(v.l,v.e),"A baker with two missed meals works no bakery, and the gate opens again");
   baker.ate(clock[0]);h.assertTrue(!HandBread.open(v.l,v.e),"Fed again, he bakes at the bakery and the gate shuts");
   // Override 10: one missed meal anywhere is an emergency, whatever the staffing.
   var forester=v.resident(Profession.FORESTER);forester.missedMeal(clock[0]);
   h.assertTrue(HandBread.chainStaffed(v.l,v.e)&&HandBread.missedMeal(v.e)&&HandBread.open(v.l,v.e),"One missed meal opens it with the chain staffed");
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_funding"),"A job starts");clock[0]+=20;
   HandBread.advance(v.l,v.e,mayor,clock[0]);clock[0]+=20;
   h.assertTrue(HandBread.inspect(v.l,v.s.id()).getInt("paid")==10&&hall.countItem(Items.WHEAT)==0,"It is paid for: "+HandBread.inspect(v.l,v.s.id()));
   forester.ate(clock[0]);
   h.assertTrue(!HandBread.open(v.l,v.e)&&HandBread.busy(v.l,v.e),"The gate shuts again, but the paid job is under way");
   var statuses=bake(v.l,v.e,clock,mayor);
   h.assertTrue(last(statuses).equals("hand_bread_complete")&&hall.countItem(Items.BREAD)==15,"A job already paid for always finishes: "+last(statuses)+" bread="+hall.countItem(Items.BREAD));
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_idle"),"Then no new job starts");
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadFetchesWheatFromTheFarmChest(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,0);var farm=v.chest("farm");put(farm,stack(Items.WHEAT,9));long[] clock={1000};var mayor=v.resident(Profession.MAYOR).id();
   h.assertTrue(HandBread.wheatAvailable(v.l,v.e)==9,"The farm chest's wheat counts: "+HandBread.wheatAvailable(v.l,v.e));
   var first=HandBread.advance(v.l,v.e,mayor,clock[0]);clock[0]+=20;var second=HandBread.advance(v.l,v.e,mayor,clock[0]);clock[0]+=20;var job=HandBread.inspect(v.l,v.s.id());
   h.assertTrue(first.equals("hand_bread_funding")&&second.equals("hand_bread_fetching"),"The idle turn starts a job; with no wheat at the hall its first withdrawal sends the baker to the farm chest: "+first+", "+second);
   h.assertTrue(job.getInt("wheat")==5&&job.getInt("bread")==2&&HandBread.where(v.l,v.e).equals(LogisticsRoutes.position(v.e,v.building("farm"))),"One whole unit of the nine, fetched at the farm chest: "+job);
   var statuses=bake(v.l,v.e,clock,mayor);
   h.assertTrue(last(statuses).equals("hand_bread_complete"),"The job is done: "+last(statuses));
   h.assertTrue(hall.countItem(Items.BREAD)==2&&farm.countItem(Items.WHEAT)==4&&hall.countItem(Items.WHEAT)==0,"Five wheat left the farm chest and two bread are in the hall: bread="+hall.countItem(Items.BREAD)+" farm wheat="+farm.countItem(Items.WHEAT));
   h.assertTrue(HandBread.where(v.l,v.e).equals(LogisticsRoutes.position(v.e,Workshops.hall(v.e))),"The baker is back at the hall");
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadDepositReplaysOnce(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,10);long[] clock={1000};var mayor=v.resident(Profession.MAYOR).id();CompoundTag before=null;
   for(int turn=0;turn<400&&before==null;turn++){HandBread.advance(v.l,v.e,mayor,clock[0]);clock[0]+=20;var t=HandBread.inspect(v.l,v.s.id());if(t.getString("stage").equals("output")&&t.getInt("output")==0)before=t;}
   h.assertTrue(before!=null,"The job reached its output");
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_output")&&hall.countItem(Items.BREAD)==4,"The bread is put away: "+hall.countItem(Items.BREAD));clock[0]+=20;
   // A crash before the record was written: it comes back one step behind its deposit.
   NbtRecord.write(HandBread.path(v.l,v.s.id()),before);
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_output")&&hall.countItem(Items.BREAD)==4,"The replayed deposit finds its receipt and puts nothing in twice: "+hall.countItem(Items.BREAD));clock[0]+=20;
   var statuses=bake(v.l,v.e,clock,mayor);
   h.assertTrue(last(statuses).equals("hand_bread_complete")&&hall.countItem(Items.BREAD)==4&&HandBread.inspect(v.l,v.s.id()).getInt("baked")==4,"Four bread, baked and counted once: "+hall.countItem(Items.BREAD)+" "+HandBread.inspect(v.l,v.s.id()));
  }finally{done(v);}
  h.succeed();
 }
 /** Override 11: a job whose wheat went before any was taken simply ends; wheat paid that makes no whole unit, the rest gone from every store,
  *  goes back to the hall through the job's own deposit and the job ends with no bread. */
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadGivesBackWheatThatMakesNoWholeUnit(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,5);long[] clock={1000};var mayor=v.resident(Profession.MAYOR).id();
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_funding"),"A job of one unit starts");clock[0]+=20;
   clear(hall,Items.WHEAT);
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_missing_wheat")&&!HandBread.busy(v.l,v.e),"Its wheat gone before any was taken, the job simply ends");clock[0]+=20;
   put(hall,stack(Items.WHEAT,3));put(hall,stack(Items.WHEAT,2));
   h.assertTrue(HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_funding"),"A new job of one unit starts");clock[0]+=20;
   HandBread.advance(v.l,v.e,mayor,clock[0]);clock[0]+=20;
   h.assertTrue(HandBread.inspect(v.l,v.s.id()).getInt("paid")==3&&hall.countItem(Items.WHEAT)==2,"The first stack is paid: "+HandBread.inspect(v.l,v.s.id()));
   hall.setItem(slot(hall,Items.WHEAT),ItemStack.EMPTY);
   var statuses=bake(v.l,v.e,clock,mayor);
   h.assertTrue(last(statuses).equals("hand_bread_complete")&&hall.countItem(Items.WHEAT)==3&&hall.countItem(Items.BREAD)==0,"The three paid wheat make no bread and come back to the hall: wheat="+hall.countItem(Items.WHEAT)+" "+statuses);
   h.assertTrue(HandBread.inspect(v.l,v.s.id()).getInt("baked")==0&&HandBread.advance(v.l,v.e,mayor,clock[0]).equals("hand_bread_missing_wheat"),"Nothing was baked, and three wheat start nothing");
  }finally{done(v);}
  h.succeed();
 }
 /** Override 11: at a hall chest with no room for the bread the baker lets the job go, so other work of his is not held up; the bread waits for room. */
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadGoalLetsGoAtAFullHallChest(GameTestHelper h){
  var v=village(h);
  try{
   var hall=hungryHall(v,10);var mayor=v.npc(Profession.MAYOR);
   // The job runs on a clock of its own far below the world's, so the goal's turn on the game time never falls within a turn of it.
   long[] clock={-1_000_000L};
   for(int turn=0;turn<400&&!HandBread.inspect(v.l,v.s.id()).getString("stage").equals("output");turn++){HandBread.advance(v.l,v.e,mayor.getUUID(),clock[0]);clock[0]+=20;}
   for(int i=0;i<hall.getContainerSize();i++)if(hall.getItem(i).isEmpty())hall.setItem(i,stack(Items.COBBLESTONE,64));
   var goal=new HandBreadGoal(mayor,true,()->1000L);
   h.assertTrue(goal.canUse(),"A job under way takes an idle adult");goal.start();
   var at=LogisticsRoutes.position(v.e,Workshops.hall(v.e));mayor.teleportTo(at.getX()+1.5,at.getY(),at.getZ()+.5);goal.tick();
   h.assertTrue("hand_bread_output_full".equals(mayor.workStatus())&&!goal.canContinueToUse()&&hall.countItem(Items.BREAD)==0,"No room for the bread: the baker lets go: "+mayor.workStatus());
   goal.stop();hall.setItem(slot(hall,Items.COBBLESTONE),ItemStack.EMPTY);
   var statuses=bake(v.l,v.e,clock,mayor.getUUID());
   h.assertTrue(last(statuses).equals("hand_bread_complete")&&hall.countItem(Items.BREAD)==4,"With room again the bread is put away, once: "+hall.countItem(Items.BREAD));
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void oneBakerAtATime(GameTestHelper h){
  var v=village(h);
  try{
   var id=v.s.id();var mayor=v.resident(Profession.MAYOR).id();var builder=v.resident(Profession.BUILDER).id();long t=1000;
   h.assertTrue(HandBread.claim(v.l,id,mayor,t)&&HandBread.holds(v.l,id,mayor,t),"The first adult takes the village's hand bread");
   h.assertTrue(!HandBread.mayClaim(v.l,id,builder,t)&&!HandBread.claim(v.l,id,builder,t),"A second may not while it is held");
   h.assertTrue(!HandBread.mayClaim(v.l,id,builder,t+HandBread.LOCK_TICKS)&&HandBread.mayClaim(v.l,id,builder,t+HandBread.LOCK_TICKS+1),"A claim nobody refreshed for "+HandBread.LOCK_TICKS+" ticks is free again");
   h.assertTrue(HandBread.claim(v.l,id,mayor,t+100)&&!HandBread.mayClaim(v.l,id,builder,t+HandBread.LOCK_TICKS+1),"Refreshed, it is held on");
   HandBread.release(v.l,id,builder);h.assertTrue(HandBread.holds(v.l,id,mayor,t+100),"Only its holder lets it go");
   HandBread.release(v.l,id,mayor);
   h.assertTrue(HandBread.claim(v.l,id,builder,t+100)&&HandBread.holds(v.l,id,builder,t+100)&&!HandBread.holds(v.l,id,mayor,t+100),"Let go, the next adult takes it");
   v.npc(Profession.BUILDER).discard();
   h.assertTrue(!HandBread.holds(v.l,id,builder,t+100)&&HandBread.claim(v.l,id,mayor,t+100),"A holder gone from the world holds nothing");
  }finally{done(v);}
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadGoalTakesAnIdleAdultByDayOnly(GameTestHelper h){
  var v=village(h);
  try{
   hungryHall(v,10);var mayor=v.npc(Profession.MAYOR);var builder=v.npc(Profession.BUILDER);var goal=new HandBreadGoal(mayor,true,()->1000L);
   h.assertTrue(!new HandBreadGoal(mayor,true,()->13000L).canUse(),"Nobody bakes at night");
   h.assertTrue(!new HandBreadGoal(v.npc(Profession.FARMER),true,()->1000L).canUse(),"The farmer keeps to his field");
   h.assertTrue(goal.canUse(),"By day an idle adult takes up the village's hand bread");
   goal.start();
   h.assertTrue(!new HandBreadGoal(builder,true,()->1000L).canUse(),"A second adult does not while the first holds it");
   var at=LogisticsRoutes.position(v.e,Workshops.hall(v.e));mayor.teleportTo(at.getX()+1.5,at.getY(),at.getZ()+.5);
   goal.tick();
   h.assertTrue("hand_bread_funding".equals(mayor.workStatus())&&HandBread.busy(v.l,v.e)&&mayor.displayedWorkItem().is(Items.WHEAT)&&goal.canContinueToUse(),"At the hall chest the baker starts a job and is seen with wheat: "+mayor.workStatus());
   goal.stop();
   h.assertTrue(!HandBread.holds(v.l,v.s.id(),mayor.getUUID(),v.l.getGameTime())&&new HandBreadGoal(builder,true,()->1000L).canUse(),"Let go, the job waits for the next idle adult");
  }finally{done(v);}
  h.succeed();
 }
 /** probe-fix-01: the hall's simple crafting (the mayor's, the builder's) gives way to the village's hand bread by day while it wants an adult —
  *  in the starter village both kept crafting for the style's projects and a paid job waited in the hall until a meal was missed. */
 @GameTest(template="empty",timeoutTicks=100) public static void hallCraftingGivesWayToHandBread(GameTestHelper h){
  var v=village(h);
  try{
   var mayor=v.npc(Profession.MAYOR);var builder=v.npc(Profession.BUILDER);
   h.assertTrue(!HandBreadGoal.calls(mayor,true,1000L),"A fed village with no wheat to bake calls nobody from the hall's work");
   hungryHall(v,10);
   h.assertTrue(HandBreadGoal.calls(mayor,true,1000L)&&HandBreadGoal.calls(builder,true,1000L),"By day a hungry hall with wheat calls its crafting adults");
   h.assertTrue(!HandBreadGoal.calls(mayor,true,13000L),"Not at night");
   h.assertTrue(!HandBreadGoal.calls(v.npc(Profession.FARMER),true,1000L),"Never the farmer");
   HandBread.claim(v.l,v.s.id(),mayor.getUUID(),v.l.getGameTime());
   h.assertTrue(HandBreadGoal.calls(mayor,true,1000L)&&!HandBreadGoal.calls(builder,true,1000L),"Once the mayor bakes, the builder keeps to his crafting");
   HandBread.release(v.l,v.s.id(),mayor.getUUID());
  }finally{done(v);}
  h.succeed();
 }
 /** Override 18: nobody walking out with a player, no companion let go and still rowing ashore, and no guest — no village, a village that is
  *  gone, or a stranger who is nobody of this village — bakes or holds the claim. */
 @GameTest(template="empty",timeoutTicks=100) public static void handBreadExcludesCompanionsAndGuests(GameTestHelper h){
  var v=village(h);
  try{
   hungryHall(v,10);var id=v.s.id();var builder=v.npc(Profession.BUILDER);var mayor=v.npc(Profession.MAYOR);long t=v.l.getGameTime();
   java.util.function.BooleanSupplier takes=()->new HandBreadGoal(builder,true,()->1000L).canUse();
   h.assertTrue(takes.getAsBoolean()&&HandBread.mayBake(v.l,id,builder.getUUID()),"An idle adult of the village may bake");
   builder.escort(UUID.randomUUID());
   h.assertTrue(!takes.getAsBoolean()&&!HandBread.mayBake(v.l,id,builder.getUUID())&&!HandBread.claim(v.l,id,builder.getUUID(),t),"Nobody walking out with a player bakes");
   builder.escort(null);builder.releasing(true);
   h.assertTrue(!takes.getAsBoolean()&&!HandBread.mayBake(v.l,id,builder.getUUID())&&!HandBread.claim(v.l,id,builder.getUUID(),t),"Nor a companion let go in a boat, still rowing ashore");
   builder.releasing(false);h.assertTrue(takes.getAsBoolean(),"Back on land the same adult may bake again");
   h.assertTrue(HandBread.claim(v.l,id,mayor.getUUID(),t)&&!HandBread.mayClaim(v.l,id,builder.getUUID(),t),"The mayor holds the claim");
   mayor.escort(UUID.randomUUID());
   h.assertTrue(!HandBread.holds(v.l,id,mayor.getUUID(),t)&&HandBread.mayClaim(v.l,id,builder.getUUID(),t),"A holder who walks out with a player holds it no more");
   mayor.escort(null);
   var guest=VillageAstra.RESIDENT.get().create(v.l);
   var gone=VillageAstra.RESIDENT.get().create(v.l);gone.bind(UUID.randomUUID(),new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1));
   var stranger=VillageAstra.RESIDENT.get().create(v.l);stranger.bind(id,new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1));
   for(var npc:List.of(guest,gone,stranger))
    h.assertTrue(!new HandBreadGoal(npc,true,()->1000L).canUse()&&!HandBread.mayBake(v.l,id,npc.getUUID())&&!HandBread.claim(v.l,id,npc.getUUID(),t),"A guest never bakes: village "+npc.settlementId());
  }finally{done(v);}
  h.succeed();
 }
 // ---- a driven mill and bakery ------------------------------------------------------------------------
 private record Works(ServerLevel l,Settlement s,SettlementData.Entry e,Settlement.Building hall,Settlement.Building mill,Settlement.Building bakery){
  OwnedChestEntity chest(Settlement.Building b){return LogisticsRoutes.chest(l,e,b);}
 }
 /** DriveGameTests' works — a hall, a mill whose wheel stands in water and a bakery on its chain shaft — and two adults of no trade to feed. */
 private static Works works(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(4,3,4));var s=new Settlement(UUID.randomUUID());
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var mill=new Settlement.Building(Settlement.childId(s.id(),"building/mill"),"mill",8,0,0);s.addBuilding(mill);
  var bakery=new Settlement.Building(Settlement.childId(s.id(),"building/restaurant"),"restaurant",18,0,0);s.addBuilding(bakery);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,2,true);s.addHome(home);
  for(int i=0;i<2;i++)s.admit(new Resident(Settlement.childId(s.id(),"adult/"+i),Resident.Life.ADULT,false,null,null,-1),home.id());
  for(int x=-2;x<32;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  for(var b:List.of(hall,mill,bakery)){var chest=LogisticsRoutes.position(e,b);l.setBlock(chest.below(),Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);}
  var wheel=Drive.postOf(e,mill);var post=Drive.postOf(e,bakery);l.setBlock(wheel.below(),Blocks.WATER.defaultBlockState(),3);
  for(int x=wheel.getX();x<=post.getX();x++)l.setBlock(new BlockPos(x,wheel.getY(),wheel.getZ()),Blocks.CHAIN.defaultBlockState(),3);
  return new Works(l,s,e,hall,mill,bakery);
 }
 private static void done(Works t){delete(HandBread.path(t.l,t.s.id()));for(var b:t.s.buildings())delete(Workshops.path(t.l,b.id()));SettlementData.get(t.l.getServer()).remove(t.s.id());}
 /** Override 10 and 14: a driven mill and bakery count as the chain, yet with no coal the bakery bakes nothing — a missed meal then opens bread
  *  by hand whatever the staffing, so a mechanized chain without fuel never starves the village. */
 @GameTest(template="empty",timeoutTicks=100) public static void aDrivenChainWithoutCoalLetsAMissedMealBakeByHand(GameTestHelper h){
  var t=works(h);
  try{
   var hall=t.chest(t.hall);var bakery=t.chest(t.bakery);hall.setItem(0,stack(Items.WHEAT,10));bakery.setItem(0,stack(VillageAstra.FLOUR.get(),4));var r=t.s.residents().iterator().next();
   h.assertTrue(Machines.mechanized(t.l,t.e,t.mill)&&Machines.mechanized(t.l,t.e,t.bakery)&&HandBread.chainStaffed(t.l,t.e),"The wheel drives the mill and the bakery with nobody at them");
   h.assertTrue(HandBread.lowOnFood(t.l,t.e)&&!HandBread.open(t.l,t.e)&&HandBread.advance(t.l,t.e,r.id(),1000).equals("hand_bread_idle"),"An empty pantry alone starts no bread by hand while the chain is driven");
   for(long now=Automation.PERIOD;now<=Automation.PERIOD*10;now+=Automation.PERIOD)Machines.tick(t.l,t.e,now,Workshops.wants(t.l,t.e));
   h.assertTrue(bakery.countItem(Items.BREAD)==0&&bakery.countItem(VillageAstra.FLOUR.get())==4,"Without coal the driven bakery bakes nothing: bread="+bakery.countItem(Items.BREAD)+" flour="+bakery.countItem(VillageAstra.FLOUR.get()));
   h.assertTrue(Workshops.published(t.l,t.bakery.id()).stream().anyMatch(in->in.matches(new ItemStack(Items.COAL))),"It asks for coal");
   long due=5000;Population.meal(t.l,t.e,r,due);Population.meal(t.l,t.e,r,due+Population.MEAL_INTERVAL);
   h.assertTrue(r.missedMeals()==1,"With nothing to eat a meal is missed: "+r.missedMeals());
   h.assertTrue(HandBread.chainStaffed(t.l,t.e)&&HandBread.open(t.l,t.e),"The missed meal opens bread by hand, driven chain or not");
   long[] clock={2000};var statuses=bake(t.l,t.e,clock,r.id());
   h.assertTrue(last(statuses).equals("hand_bread_complete")&&hall.countItem(Items.BREAD)==4&&hall.countItem(Items.WHEAT)==0,"The hall's ten wheat became four bread by hand: "+last(statuses)+" bread="+hall.countItem(Items.BREAD));
   Population.meal(t.l,t.e,r,due+2*Population.MEAL_INTERVAL);
   h.assertTrue(r.missedMeals()==0&&hall.countItem(Items.BREAD)==3,"The next meal is eaten: bread="+hall.countItem(Items.BREAD));
   h.assertTrue(!HandBread.open(t.l,t.e)&&HandBread.advance(t.l,t.e,r.id(),clock[0]).equals("hand_bread_idle"),"Fed, the village leaves its bread to the chain again");
  }finally{done(t);}
  h.succeed();
 }
 // ---- village meals --------------------------------------------------------------------------------------
 @GameTest(template="empty",timeoutTicks=100) public static void villageMealsEatRations(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());
  var hallLot=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hallLot);
  var home=new Settlement.Home(Settlement.childId(s.id(),"home"),1,1,true);s.addHome(home);var r=new Resident(Settlement.childId(s.id(),"adult/0"),Resident.Life.ADULT,false,null,null,-1);s.admit(r,home.id());
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  try{
   var at=LogisticsRoutes.position(e,hallLot);l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(at,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var c=LogisticsRoutes.chest(l,e,hallLot);
   h.assertTrue(Population.nutrition(stack(Items.CARROT,1))==1&&Population.nutrition(stack(Items.POTATO,1))==1&&Population.nutrition(stack(Items.BEETROOT,1))==2&&Population.nutrition(stack(Items.BREAD,1))==5&&Population.nutrition(stack(Items.BAKED_POTATO,1))==5,"A village meal counts the owner's rations");
   long t=1000;Population.meal(l,e,r,t);
   c.setItem(0,stack(Items.CARROT,4));t+=Population.MEAL_INTERVAL;Population.meal(l,e,r,t);
   h.assertTrue(r.missedMeals()==1&&c.countItem(Items.CARROT)==4,"Four carrots are no meal: it is missed and the carrots stay: "+c.countItem(Items.CARROT));
   c.setItem(0,stack(Items.CARROT,5));t+=Population.MEAL_INTERVAL;Population.meal(l,e,r,t);
   h.assertTrue(r.missedMeals()==0&&c.countItem(Items.CARROT)==0,"Five carrots make one meal, and it takes all five: "+c.countItem(Items.CARROT));
   c.setItem(0,stack(Items.BEETROOT,4));t+=Population.MEAL_INTERVAL;Population.meal(l,e,r,t);
   h.assertTrue(r.missedMeals()==0&&c.countItem(Items.BEETROOT)==1,"Three beetroots make one meal: "+c.countItem(Items.BEETROOT)+" of 4 left");
   c.setItem(0,stack(Items.CARROT,10));c.setItem(1,stack(Items.BREAD,2));
   h.assertTrue(Population.storedNutrition(l,e)==20,"Ten carrots and two bread are 20 rations: "+Population.storedNutrition(l,e));
   // The player's own hunger stays vanilla: FoodProperties are only read.
   var hunger=new FoodData();hunger.setFoodLevel(10);hunger.eat(Items.CARROT,stack(Items.CARROT,1),null);
   h.assertTrue(hunger.getFoodLevel()==13&&Items.CARROT.getFoodProperties(stack(Items.CARROT,1),null).getNutrition()==3,"A player still gets 3 from a carrot: "+hunger.getFoodLevel());
  }finally{SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
 // ---- the farm card ----------------------------------------------------------------------------------------
 /** The card's expectations come from FoodYield at this world's own random tick speed (the rule is shared by every test running at once). */
 @GameTest(template="empty",timeoutTicks=100) public static void farmCardSaysHowTheFieldFeeds(GameTestHelper h){
  var v=village(h);
  try{
   var farm=v.building("farm");int speed=v.l.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
   var plots=new ArrayList<int[]>();for(var p:FarmField.localCells(FarmField.modules(v.e,farm)))plots.add(new int[]{p.getX(),p.getZ()});
   double wheat=FarmYield.wheatPerDay(plots,speed);var meals=BuildingCards.meals();int bread=Population.nutrition(stack(Items.BREAD,1));
   var hand=meals.feeds(FoodYield.bread(FoodYield.itemsPerDay(wheat,FoodYield.Crop.WHEAT),HandBread.WHEAT_PER_UNIT,HandBread.BREAD_PER_UNIT),bread);
   var chain=meals.feeds(FoodYield.bread(FoodYield.itemsPerDay(wheat,FoodYield.Crop.WHEAT),FoodYield.CHAIN_WHEAT,FoodYield.CHAIN_BREAD),bread);
   var card=BuildingCards.card(v.l,v.e,farm);
   h.assertTrue(card.getString("food").equals("wheat")&&card.getString("mode").equals("hand"),"A new village bakes its wheat by hand: "+card);
   h.assertTrue(card.getInt("feedsNow")==hand.rated()&&card.getInt("feedsNowMax")==hand.upTo()&&card.getInt("feedsChain")==chain.rated()&&card.getInt("feedsChainMax")==chain.upTo(),
    "By hand it feeds "+hand.rated()+" (up to "+hand.upTo()+"), with a mill and bakery "+chain.rated()+" (up to "+chain.upTo()+") at randomTickSpeed "+speed+": "+card);
   h.assertTrue(card.getInt("feeds")==FarmYield.ratedResidents(wheat,FarmField.RATING)&&card.getInt("target")==FarmField.target(1),"The layout's own rating and the level's target stay: "+card);
   h.assertTrue(hand.rated()<=chain.rated()&&hand.upTo()<=chain.upTo(),"By hand the same field never feeds more than through the mill and bakery");
   staffChain(v.s);card=BuildingCards.card(v.l,v.e,farm);
   h.assertTrue(card.getString("mode").equals("chain")&&card.getInt("feedsNow")==chain.rated()&&card.getInt("feedsNowMax")==chain.upTo()&&!card.contains("feedsChain"),"With a staffed mill and bakery the card counts their bread: "+card);
   // The mayor turns the farm to carrots, which the village eats as they come off the field.
   CropUnlocks.unlock(v.l,v.e,"minecraft:carrot","quest");var p=FakePlayerFactory.get(v.l,new GameProfile(UUID.randomUUID(),"StarterFoodCard"));p.setPos(v.e.center().getX(),v.e.center().getY()+1,v.e.center().getZ());
   v.s.appointPlayerMayor(p.getUUID());
   h.assertTrue(FarmPolicies.order(p,v.s.id(),farm.id(),v.s.governance().epoch(),v.s.governance().revision(),"carrot"),"The mayor turns the farm to carrots");
   var carrot=meals.feeds(FoodYield.itemsPerDay(wheat,FoodYield.Crop.CARROT),Population.nutrition(stack(Items.CARROT,1)));card=BuildingCards.card(v.l,v.e,farm);
   h.assertTrue(card.getString("food").equals("carrot")&&card.getString("mode").equals("raw")&&card.getInt("feedsNow")==carrot.rated()&&card.getInt("feedsNowMax")==carrot.upTo()&&!card.contains("feedsChain"),
    "Carrots feed "+carrot.rated()+" (up to "+carrot.upTo()+") eaten as harvested: "+card);
  }finally{done(v);}
  h.succeed();
 }
}
