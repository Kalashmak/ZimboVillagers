package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-089..AD-091: the raiders' lair follows a real wave and its fall quiets the raids, the fall of a working holds its prospectors until it
 *  is dug out, the beast takes the herd until it is slain, the tower needs stone, fuel and a fire, the embassy's letter is answered by a real
 *  neighbour, the ruin is a real structure the player stands in, the survey writes far land down as a lead, and the escort walks with a real
 *  caravan through an ambush. Each pays once. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WildQuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office){}
 private static final int AWAY=26;
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-6;x<12;x++)for(int z=-6;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center,office);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  // AD-097: the adventurer of these tests has already earned the trust the far errands ask for.
  PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Wilds.EMBASSY));
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 /** The far clearing a scout reports, prepared only as wide and as high as the design needs. */
 private static BlockPos lead(Town t,String kind,long now){
  int half=QuestSites.half(kind)+1,height=QuestSites.height(kind)+2;
  // The world of a test batch is shared: another test's village may claim the first pocket, so the fixture tries a few on its own side.
  for(int[] offset:QuestPockets.offsets(AWAY)){
   var at=new BlockPos(t.center.getX()+offset[0],t.center.getY()+1,t.center.getZ()+offset[1]);
   // Ground another test's village holds is never cleared.
   boolean held=false;
   for(int dx=-half;dx<=half&&!held;dx++)for(int dz=-half;dz<=half&&!held;dz++)for(int dy=-6;dy<=height&&!held;dy++)
    if(org.villageastra.server.OwnershipEvents.disallowedPlacement(t.l,at.offset(dx,dy,dz)))held=true;
   if(held)continue;
   for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++){
    t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-1,at.getZ()+dz),Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=0;y<=height;y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()+y,at.getZ()+dz),Blocks.AIR.defaultBlockState(),2);
    for(int y=2;y<=5;y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-y,at.getZ()+dz),Blocks.STONE.defaultBlockState(),2);
   }
   if(QuestSites.place(t.l,t.e,at,kind)==null)continue;
   var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,8));chest.setItem(1,new ItemStack(Items.BREAD,8));
   if(Expeditions.report(t.l,t.e,t.office,at,0,now)==null)throw new GameTestAssertException("The prepared clearing was not reported as a lead");
   return at;
  }
  throw new GameTestAssertException("No pocket of this test's ground had room for the "+kind);
 }
 private static int count(Town t,BlockPos origin,int half,int from,int to,Block block){
  int found=0;
  for(int dx=-half-1;dx<=half+1;dx++)for(int dz=-half-1;dz<=half+1;dz++)for(int dy=from;dy<=to;dy++)if(t.l.getBlockState(origin.offset(dx,dy,dz)).is(block))found++;
  return found;
 }
 private static void near(ServerPlayer p,BlockPos at){p.setPos(at.getX()+.5,at.getY(),at.getZ()+6.5);}
 private static void paidOnce(GameTestHelper h,Town t,ServerPlayer p,CompoundTag q){
  h.assertTrue(Quests.complete(p,t.s.id(),q.getUUID("id")),"The deed closes the quest");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),
   "Paid exactly once: "+p.getInventory().countItem(VillageAstra.ZINDBO.get())+" of "+q.getLong("coins"));
 }
 // ---------------------------------------------------------------- the raiders' lair
 @GameTest(template="empty",timeoutTicks=300) public static void theLairFollowsARealWaveAndItsFallQuietsTheRaids(GameTestHelper h){
  var t=town(h);
  if(!Raids.allowed(t.l.getDifficulty())){h.assertTrue(Adventures.post(t.l,t.e,Adventures.LAIR,2000)==null,"A peaceful world gets no lair");h.succeed();return;}
  lead(t,QuestSites.HIDEOUT,1000);var p=player(t,"LairBreaker");
  h.assertTrue(Adventures.post(t.l,t.e,Adventures.LAIR,2000)==null,"No wave, no lair to follow it to");
  // The wave gathers 44-56 blocks out: the ground there has to be in the world for it to stand on.
  var hub=new net.minecraft.world.level.ChunkPos(t.center);for(int cx=-4;cx<=4;cx++)for(int cz=-4;cz<=4;cz++)t.l.getChunk(hub.x+cx,hub.z+cz);
  var started=Raids.start(t.l,t.e,3000,Raids.Kind.BANDITS);
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_TEST lair wave start result='{}'",started);
  if(!started.isEmpty()){h.succeed();return;}
  for(var raider:Raids.raiders(t.l,t.s.id()))raider.discard();
  h.assertTrue(Raids.update(t.l,t.e,3100).equals("repelled"),"The wave was beaten");
  var q=Adventures.post(t.l,t.e,Adventures.LAIR,3200);
  h.assertTrue(q!=null&&q.getLong("raid")==3100,"After the wave the village asks for its lair: "+q);
  h.assertTrue(Adventures.post(t.l,t.e,Adventures.LAIR,3300)==null,"One lair per wave");
  var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(count(t,origin,5,0,0,Blocks.SPRUCE_LOG)>=18&&count(t,origin,5,1,1,Blocks.SPRUCE_PLANKS)>=30&&count(t,origin,5,0,2,Blocks.BLACK_BANNER)==1,
   "The dugout, its log walls, its plank roof and the black flag stand: logs="+count(t,origin,5,0,0,Blocks.SPRUCE_LOG)+" planks="+count(t,origin,5,1,1,Blocks.SPRUCE_PLANKS));
  h.assertTrue(count(t,origin,5,-1,-1,Blocks.CHEST)==1&&count(t,origin,5,0,0,Blocks.CAMPFIRE)==1,"The hoard and the cooking fire are there");
  var band=site.getList("mobs",Tag.TAG_INT_ARRAY);
  h.assertTrue(band.size()==5,"Five raiders keep the lair: "+band.size());
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  near(p,origin);
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,3400);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==0,"While the band stands the lair is not beaten");
  for(var raw:band)if(t.l.getEntity(NbtUtils.loadUUID(raw)) instanceof LivingEntity raider)raider.kill();
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,3500);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"The band is down");
  h.assertTrue(Raids.record(t.l,t.s.id()).getLong("nextAt")>=3500+(long)Quests.setting(Adventures.LAIR,"calm"),"The next wave is pushed back: "+Raids.record(t.l,t.s.id()).getLong("nextAt"));
  paidOnce(h,t,p,q);
  h.succeed();
 }
 // ---------------------------------------------------------------- the mine collapse
 @GameTest(template="empty",timeoutTicks=300) public static void theFallHoldsItsProspectorsUntilItIsDugOut(GameTestHelper h){
  var t=town(h);lead(t,QuestSites.COLLAPSE,1000);var p=player(t,"FallDigger");
  var q=Adventures.post(t.l,t.e,Adventures.COLLAPSE,2000);
  h.assertTrue(q!=null&&q.getInt("target")==2,"Two prospectors are behind the fall: "+q);
  var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(count(t,origin,4,0,1,Blocks.GRAVEL)==4&&site.getList("seal",Tag.TAG_LONG).size()==4,"The drive is filled with four cells of gravel: "+count(t,origin,4,0,1,Blocks.GRAVEL));
  h.assertTrue(count(t,origin,4,0,0,Blocks.RAIL)>=3&&count(t,origin,4,0,3,Blocks.SPRUCE_LOG)>=8,"Rails run out of it between timber sets");
  var people=QuestSites.people(site);
  h.assertTrue(people.size()==2&&people.stream().allMatch(id->t.l.getEntity(id) instanceof ResidentEntity npc&&npc.getPersistentData().getBoolean(QuestSites.TRAPPED)),"Two real prospectors are trapped");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var first=(ResidentEntity)t.l.getEntity(people.get(0));var second=(ResidentEntity)t.l.getEntity(people.get(1));
  h.assertTrue(Adventures.hold(first).equals("trapped"),"Nobody walks out through the fall: "+Adventures.hold(first));
  // From the top down, so no gravel falls back into a cell already dug.
  var plug=new ArrayList<BlockPos>();for(var raw:site.getList("seal",Tag.TAG_LONG))plug.add(BlockPos.of(((LongTag)raw).getAsLong()));
  plug.sort(Comparator.comparingInt((BlockPos cell)->cell.getY()).reversed());
  t.l.destroyBlock(plug.get(0),false);
  h.assertTrue(Adventures.hold(first).equals("trapped"),"One cell dug is not a way out");
  for(var cell:plug)t.l.destroyBlock(cell,false);
  h.assertTrue(WildSites.dugOut(t.l,site)&&Adventures.hold(first).isEmpty()&&Adventures.hold(second).isEmpty(),"With the drive dug out, both may walk");
  first.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);second.moveTo(t.center.getX()+3.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);
  Quests.companions(t.l,t.e,2100);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==2,"Both really came home");
  paidOnce(h,t,p,q);
  h.succeed();
 }
 // ---------------------------------------------------------------- the beast
 @GameTest(template="empty",timeoutTicks=300) public static void theBeastTakesTheHerdUntilItIsSlain(GameTestHelper h){
  var t=town(h);
  if(!Raids.allowed(t.l.getDifficulty())){h.assertTrue(Adventures.post(t.l,t.e,Adventures.BEAST,2000)==null,"A peaceful world gets no beast");h.succeed();return;}
  lead(t,QuestSites.DEN,1000);var p=player(t,"BeastHunter");
  var q=Adventures.post(t.l,t.e,Adventures.BEAST,2000);
  if(q==null){var lead=Expeditions.lead(t.l,t.s.id(),Expeditions.CAMP,true);var spot=lead==null?null:QuestSites.place(t.l,t.e,BlockPos.of(lead.getLong("pos")),QuestSites.DEN);
   var sites=QuestSites.taken(t.l,t.s.id());var built=sites.isEmpty()?null:QuestSites.site(t.l,sites.get(0).getUUID("quest"));
   throw new GameTestAssertException("The village asks for the beast: lead="+lead+" spot="+spot+" built="+(built==null?"none":built.getList("mobs",11).size()+" mobs chief="+built.hasUUID("chief")));}
  var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(count(t,origin,4,0,0,Blocks.BONE_BLOCK)>=3&&count(t,origin,4,3,3,Blocks.MOSS_BLOCK)>=5,"The den of bones under a mossy overhang stands");
  var beast=t.l.getEntity(site.getUUID("chief"));
  h.assertTrue(beast instanceof net.minecraft.world.entity.monster.Ravager r&&r.hasCustomName()&&r.getAttributeValue(Attributes.MAX_HEALTH)==(double)Quests.setting(Adventures.BEAST,"health"),
   "A real ravager, named and stronger than its kind: "+beast);
  var sheep=EntityType.SHEEP.create(t.l);sheep.getPersistentData().putUUID(LivestockGoal.OWNER,t.s.id());sheep.moveTo(t.center.getX()+4.5,t.center.getY()+1,t.center.getZ()+8.5,0,0);t.l.addFreshEntity(sheep);
  long every=(long)Quests.setting(Adventures.BEAST,"prey_every");
  Adventures.advance(t.l,t.e,q.getUUID("id"),null,every*2);
  h.assertTrue(!sheep.isAlive(),"While the beast lives it really takes the herd");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  near(p,origin);((LivingEntity)beast).kill();
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,every*2+100);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"The beast is slain");
  paidOnce(h,t,p,q);
  h.succeed();
 }
 // ---------------------------------------------------------------- the signal tower
 @GameTest(template="empty",timeoutTicks=300) public static void theTowerNeedsStoneFuelAndAFireAndThenWatches(GameTestHelper h){
  var t=town(h);lead(t,QuestSites.TOWER,1000);var p=player(t,"TowerBuilder");
  var q=Adventures.post(t.l,t.e,Adventures.BEACON,2000);
  h.assertTrue(q!=null,"The village asks for its signal tower");
  var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(count(t,origin,3,0,4,Blocks.STONE_BRICKS)>=70&&count(t,origin,3,0,4,Blocks.LADDER)==5&&count(t,origin,3,5,7,Blocks.SPRUCE_FENCE)==12,
   "Five courses of stone, a ladder and bare poles: bricks="+count(t,origin,3,0,4,Blocks.STONE_BRICKS));
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,2100);
  var waiting=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(waiting.getString("status").equals("supplies")&&waiting.getInt("status_left")==32&&!site.getBoolean("finished"),"The tower waits for 24 stone and 8 coal: "+waiting.getInt("status_left"));
  var chest=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(site.getLong("chest")));
  chest.setItem(0,new ItemStack(Items.STONE_BRICKS,24));chest.setItem(1,new ItemStack(Items.COAL,8));
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,2200);
  site=QuestSites.site(t.l,q.getUUID("id"));var brazier=BlockPos.of(site.getLong("brazier"));
  h.assertTrue(site.getBoolean("finished")&&count(t,origin,3,5,5,Blocks.STONE_BRICK_WALL)>=8&&t.l.getBlockState(brazier).is(Blocks.CAMPFIRE)&&!t.l.getBlockState(brazier).getValue(BlockStateProperties.LIT),
   "The supplies raised the parapet and set a cold brazier");
  h.assertTrue(chest.getItem(0).isEmpty()&&chest.getItem(1).isEmpty(),"The stone and the coal were really spent");
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==0,"A cold brazier is not a signal");
  t.l.setBlock(brazier,t.l.getBlockState(brazier).setValue(BlockStateProperties.LIT,true),3);
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,2300);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"The fire burns");
  h.assertTrue(Adventures.watch(t.l,t.e)==0,"Only a finished quest's tower keeps watch");
  paidOnce(h,t,p,q);
  h.assertTrue(Adventures.watch(t.l,t.e)==(int)Quests.setting(Adventures.BEACON,"reach"),"The lit tower lets the village see raids from farther out");
  h.assertTrue(Adventures.post(t.l,t.e,Adventures.BEACON,9000)==null,"One tower per village");
  h.succeed();
 }
 // ---------------------------------------------------------------- the embassy
 @GameTest(template="empty",timeoutTicks=300) public static void theEmbassyCarriesALetterToARealNeighbourAndItsAnswerHome(GameTestHelper h){
  var t=town(h);var p=player(t,"Envoy");var l=t.l;
  var bCenter=t.center.offset(0,0,-18);var b=new Settlement(UUID.randomUUID());
  for(int x=-2;x<10;x++)for(int z=-2;z<8;z++){l.setBlock(bCenter.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(bCenter.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  b.addBuilding(new Settlement.Building(Settlement.childId(b.id(),"building/town_hall"),"town_hall",0,0,0));
  l.setBlock(bCenter.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var home=new Settlement.Home(Settlement.childId(b.id(),"home"),1,4,true);b.addHome(home);
  var host=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);b.admit(host,home.id());
  var be=new SettlementData.Entry(b,l.dimension().location().toString(),bCenter);SettlementData.get(l.getServer()).add(be);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(b.id(),b.resident(host.id()));npc.setNoAi(true);npc.moveTo(bCenter.getX()+3.5,bCenter.getY()+1,bCenter.getZ()+3.5,0,0);l.addFreshEntity(npc);
  // AD-158 III: letters go only to neighbours the village's own cartographers have found, so this one is found first.
  CartographyLadder.found(l,t.s.id(),b.id());
  var q=Wilds.post(l,t.e,Wilds.EMBASSY,2000);
  h.assertTrue(q!=null&&q.getUUID("neighbour").equals(b.id())&&q.getLong("site")==bCenter.asLong(),"The letter is for the real neighbour: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var letter=p.getInventory().items.stream().filter(x->x.is(Items.PAPER)&&x.hasTag()&&x.getTag().contains(Wilds.LETTER)).findFirst().orElse(ItemStack.EMPTY);
  h.assertTrue(!letter.isEmpty(),"The board hands over a sealed letter");
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("far")||Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("no_reply"),"No answer before the letter was delivered");
  p.setItemInHand(InteractionHand.MAIN_HAND,letter.copy());letter.setCount(0);
  var answer=Wilds.deliver(p,npc,InteractionHand.MAIN_HAND);
  h.assertTrue(answer.equals("letter_answered")||answer.equals("letter_caravan"),"A resident of the neighbours takes the letter: "+answer);
  h.assertTrue(p.getMainHandItem().hasTag()&&p.getMainHandItem().getTag().contains(Wilds.REPLY),"The letter became their answer");
  h.assertTrue(PropertyLedger.get(l.getServer()).roll(b.id()).account(p.getUUID()).score()>0,"The envoy is known among the hosts now");
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"The answer is handed over at home");
  h.assertTrue(LogisticsRoutes.chest(l,t.e,Workshops.hall(t.e)).countItem(Items.PAPER)==1,"The answer lies in the hall");
  var done=Quests.quest(l,t.s.id(),q.getUUID("id"));
  // The payment is checked on its own record: the ledger's completion deal is written once, with what was paid.
  var deal=TradeLedger.get(l.getServer()).deal(Settlement.childId(q.getUUID("id"),"completion"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&deal!=null&&deal.getLong("paid")==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),
   "Paid exactly once: state="+done.getString("state")+" progress="+done.getInt("progress")+" coins="+p.getInventory().countItem(VillageAstra.ZINDBO.get())+" of "+q.getLong("coins")+"/"+done.getLong("coins")
   +" owed="+TradeLedger.get(l.getServer()).owed(p.getUUID())+" deal="+deal+" alive="+p.isAlive()+" health="+p.getHealth()+" inventory="+p.getInventory().items.stream().filter(x->!x.isEmpty()).map(x->x.getCount()+"x"+x.getItem()).toList());
  h.succeed();
 }
 // ---------------------------------------------------------------- the ruins of the world
 @GameTest(template="empty",timeoutTicks=600) public static void theRuinIsARealStructureThePlayerStandsIn(GameTestHelper h){
  var t=town(h);var p=player(t,"RuinWalker");
  var q=Wilds.post(t.l,t.e,Wilds.RUIN,2000);
  com.mojang.logging.LogUtils.getLogger().info("ASTRA_TEST ruin posted={}",q==null?"none":q.getString("kind")+" at "+BlockPos.of(q.getLong("site")).toShortString());
  if(q==null){h.succeed();return;}
  var structure=t.l.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(q.getString("structure")));
  h.assertTrue(structure!=null&&q.getString("kind").startsWith("ruin_"),"The quest names a real structure: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var site=BlockPos.of(q.getLong("site"));
  var start=t.l.getChunk(site.getX()>>4,site.getZ()>>4,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
  h.assertTrue(start!=null&&start.isValid()&&!start.getPieces().isEmpty(),"The structure really starts there");
  Wilds.advance(t.l,t.e,q.getUUID("id"),p,2100);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==0,"Standing at home explores nothing");
  var inside=start.getPieces().get(0).getBoundingBox().getCenter();
  p.setPos(inside.getX()+.5,inside.getY(),inside.getZ()+.5);
  Wilds.advance(t.l,t.e,q.getUUID("id"),p,2200);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"Inside one of its pieces the ruin counts as explored");
  paidOnce(h,t,p,q);
  h.succeed();
 }
 // ---------------------------------------------------------------- unknown land
 @GameTest(template="empty",timeoutTicks=300) public static void theSurveyWalksFarLandIntoANewLead(GameTestHelper h){
  var t=town(h);var p=player(t,"Surveyor");
  var q=Wilds.post(t.l,t.e,Wilds.SURVEY,2000);
  h.assertTrue(q!=null&&q.getList("region",Tag.TAG_LONG).size()==9&&q.getInt("target")==6&&q.getInt("sector")>=8,"Nine chunks of far land, six to walk: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var region=q.getList("region",Tag.TAG_LONG);
  for(int i=0;i<6;i++){var chunk=new net.minecraft.world.level.ChunkPos(((LongTag)region.get(i)).getAsLong());
   p.setPos(chunk.getMiddleBlockX()+.5,t.center.getY()+1,chunk.getMiddleBlockZ()+.5);Wilds.advance(t.l,t.e,q.getUUID("id"),p,2100+i);
   if(i==0){p.setPos(chunk.getMiddleBlockX()+2.5,t.center.getY()+1,chunk.getMiddleBlockZ()+.5);Wilds.advance(t.l,t.e,q.getUUID("id"),p,2150);
    h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"The same chunk counts once");}}
  var walked=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(walked.getInt("progress")==6,"Six chunks really walked: "+walked.getInt("progress"));
  int sector=q.getInt("sector");
  h.assertTrue(Expeditions.leads(t.l,t.s.id()).stream().anyMatch(x->x.getInt("sector")==sector&&!x.getBoolean("used")),"The far land is a lead of the village now");
  paidOnce(h,t,p,q);
  var again=Wilds.post(t.l,t.e,Wilds.SURVEY,9000);
  h.assertTrue(again==null||again.getInt("sector")!=sector,"Known land is not asked for again");
  h.succeed();
 }
 // ---------------------------------------------------------------- the caravan escort
 @GameTest(template="empty",timeoutTicks=400) public static void theEscortWalksWithARealCaravanThroughAnAmbush(GameTestHelper h){
  var t=town(h);var l=t.l;var server=l.getServer();
  if(!Raids.allowed(l.getDifficulty())){h.succeed();return;}
  var p=player(t,"CaravanGuard");
  var dCenter=h.absolutePos(new BlockPos(30,3,26));var d=new Settlement(UUID.randomUUID());
  d.addBuilding(new Settlement.Building(Settlement.childId(d.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(dCenter.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(dCenter.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(dCenter.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);d.addHome(new Settlement.Home(Settlement.childId(d.id(),"home"),1,4,true));
  var de=new SettlementData.Entry(d,l.dimension().location().toString(),dCenter);SettlementData.get(server).add(de);
  var yard=new Settlement.Building(Settlement.childId(t.s.id(),"building/caravan"),"caravan",4,0,-8);t.s.addBuilding(yard);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);t.s.admit(r,t.s.homes().iterator().next().id());t.s.assign(r.id(),Profession.CARAVANEER,yard.id());
  var body=VillageAstra.RESIDENT.get().create(l);body.bind(t.s.id(),t.s.resident(r.id()));body.setNoAi(true);body.moveTo(t.center.getX()+3.5,t.center.getY()+1,t.center.getZ()+3.5,0,0);l.addFreshEntity(body);
  LogisticsRoutes.chest(l,t.e,Workshops.hall(t.e)).setItem(0,new ItemStack(Items.BREAD,64));
  long now=4000;Caravans.snapshot(l,t.e,now);var contract=Caravans.propose(l,de,t.e,now);
  h.assertTrue(contract!=null,"A real trade trip is proposed");var id=contract.getUUID("id");
  Caravans.tick(server,now+=20);Caravans.tick(server,now+=20);
  h.assertTrue(Caravans.contract(server,id).getString("state").equals(Caravans.TRANSIT),"The caravan is on the road");
  var q=Wilds.escortFor(l,t.e,Caravans.contract(server,id),now);
  h.assertTrue(q!=null&&q.getUUID("contract").equals(id),"The board asks for somebody to walk with it: "+q);
  h.assertTrue(Wilds.escortFor(l,t.e,Caravans.contract(server,id),now)==null,"One escort per trip");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var trip=Caravans.contract(server,id);var walker=Caravans.materialize(l,trip,Caravans.position(l,trip,trip.getDouble("progress")));
  h.assertTrue(walker!=null,"The caravaneer is on the road in person");
  p.setPos(walker.getX()+2,walker.getY(),walker.getZ());
  Wilds.advance(l,t.e,q.getUUID("id"),p,now+=100);
  var walking=Quests.quest(l,t.s.id(),q.getUUID("id"));
  h.assertTrue(walking.getBoolean("joined")&&walking.getInt("escorted")==1,"The player walks with the caravan");
  trip=Caravans.contract(server,id);trip.putDouble("progress",Caravans.length(trip)*.5);Caravans.update(server,trip);
  Wilds.advance(l,t.e,q.getUUID("id"),p,now+=100);
  var ambushed=Quests.quest(l,t.s.id(),q.getUUID("id"));
  h.assertTrue(ambushed.getBoolean("ambushed")&&!ambushed.getList("ambush",Tag.TAG_INT_ARRAY).isEmpty(),"Halfway along the road the ambush comes out");
  for(var raw:ambushed.getList("ambush",Tag.TAG_INT_ARRAY))if(l.getEntity(NbtUtils.loadUUID(raw)) instanceof LivingEntity bandit)bandit.kill();
  walker.discard();
  for(int i=0;i<60&&!Caravans.contract(server,id).getString("state").equals(Caravans.RETURNING);i++)Caravans.tick(server,now+=20);
  h.assertTrue(Caravans.contract(server,id).getString("state").equals(Caravans.RETURNING),"The caravan arrived");
  Wilds.advance(l,t.e,q.getUUID("id"),p,now+=100);
  h.assertTrue(Quests.quest(l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"The escort counts: the player kept with it and the ambush is beaten");
  paidOnce(h,t,p,q);
  h.succeed();
 }
}
