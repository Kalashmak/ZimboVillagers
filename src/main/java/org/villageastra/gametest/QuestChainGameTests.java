package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-097..AD-099: the board trusts a player step by step, a resident asks in person for what their own workshop lacks, the people of a
 *  rescue are somebody's kin and go home to that house, and a finished adventure leads on — kept for the player who found the trail. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestChainGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office,Settlement.Building clinic){}
 private static final int AWAY=26;
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-12;x<20;x++)for(int z=-6;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/laboratory"),"laboratory",16,0,0));
  var clinic=new Settlement.Building(Settlement.childId(s.id(),"building/clinic"),"clinic",-10,0,0);s.addBuilding(clinic);
  for(int x:new int[]{1,9,17,-9})l.setBlock(center.offset(x,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center,office,clinic);
 }
 private static ServerPlayer player(Town t,String name,int trust){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  if(trust>0)PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),trust);
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 /** A resident of the town with a house of their own. */
 private static Resident resident(Town t,String home,int beds){
  var house=new Settlement.Home(Settlement.childId(t.s.id(),"home/"+home),1,beds,true);t.s.addHome(house);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,house.id());return r;
 }
 /** A pocket of this test's own ground prepared for one design, never on top of a site already standing. */
 private static BlockPos pocket(Town t,String kind,boolean rock){
  int half=QuestSites.half(kind)+1,height=QuestSites.height(kind)+2;
  // The world of a test batch is shared: a pocket stays within this test's own reach, and ground another test holds is never touched.
  for(int[] offset:QuestPockets.offsets(AWAY)){
   var at=new BlockPos(t.center.getX()+offset[0],t.center.getY()+1,t.center.getZ()+offset[1]);
   if(QuestSites.taken(t.l,t.s.id()).stream().anyMatch(x->{var old=BlockPos.of(x.getLong("pos"));return Math.abs(old.getX()-at.getX())<14&&Math.abs(old.getZ()-at.getZ())<14;}))continue;
   boolean held=false;
   for(int dx=-half;dx<=half&&!held;dx++)for(int dz=-half;dz<=half&&!held;dz++)for(int dy=-10;dy<=height&&!held;dy++)
    if(OwnershipEvents.disallowedPlacement(t.l,at.offset(dx,dy,dz)))held=true;
   if(held)continue;
   for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++){
    t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-1,at.getZ()+dz),Blocks.GRASS_BLOCK.defaultBlockState(),2);
    for(int y=0;y<=height;y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()+y,at.getZ()+dz),Blocks.AIR.defaultBlockState(),2);
    for(int y=2;y<=(rock?9:5);y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-y,at.getZ()+dz),Blocks.STONE.defaultBlockState(),2);
   }
   if(QuestSites.place(t.l,t.e,at,kind)!=null)return at;
  }
  throw new GameTestAssertException("No pocket of this test's ground had room for the "+kind);
 }
 /** The same pocket, reported by the expeditioner as a real lead. */
 private static BlockPos lead(Town t,String kind,long now){
  var at=pocket(t,kind,kind.equals(QuestSites.COLLAPSE));
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,8));chest.setItem(1,new ItemStack(Items.BREAD,8));
  if(Expeditions.report(t.l,t.e,t.office,at,0,now)==null)throw new GameTestAssertException("The prepared clearing was not reported as a lead");
  return at;
 }
 private static int count(Town t,BlockPos origin,int half,int from,int to,Block block){
  int found=0;
  for(int dx=-half-1;dx<=half+1;dx++)for(int dz=-half-1;dz<=half+1;dz++)for(int dy=from;dy<=to;dy++)if(t.l.getBlockState(origin.offset(dx,dy,dz)).is(block))found++;
  return found;
 }
 private static CompoundTag row(ServerPlayer p,Town t,UUID id){
  var view=new CompoundTag();view.putUUID("village",t.s.id());Quests.addView(p,view);
  for(var raw:view.getList("quests",Tag.TAG_COMPOUND))if(((CompoundTag)raw).getUUID("id").equals(id))return (CompoundTag)raw;
  return null;
 }
 private static CompoundTag following(Town t,UUID done){
  for(var raw:Quests.board(t.l,t.s.id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(q.hasUUID("after")&&q.getUUID("after").equals(done))return q;}
  return null;
 }
 private static void standAt(ServerPlayer p,BlockPos chest){p.setPos(chest.getX()+.5,chest.getY(),chest.getZ()+1.5);}
 private static ItemStack take(Town t,CompoundTag site){
  var chest=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(site.getLong("chest")));
  for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(VillageAstra.RESEARCH_VOLUME.get())){var found=chest.getItem(slot).copy();chest.setItem(slot,ItemStack.EMPTY);return found;}
  return ItemStack.EMPTY;
 }
 // ---------------------------------------------------------------- a request with a face
 @GameTest(template="empty",timeoutTicks=200) public static void aResidentAsksForWhatTheirWorkshopLacksAndIsThankedAtIt(GameTestHelper h){
  var t=town(h);var doctor=resident(t,"doctor",2);t.s.assign(doctor.id(),Profession.DOCTOR,t.clinic.id());
  var p=player(t,"PleaHelper",0);
  var q=Pleas.post(t.l,t.e,6000);
  h.assertTrue(q!=null&&q.getString("item").equals("villageastra:bandage")&&q.getInt("target")==4,"The doctor asks for the bandages the clinic lacks: "+q);
  h.assertTrue(q.getUUID("giver").equals(doctor.id())&&q.getString("giver_name").equals(doctor.profile().name())&&q.getUUID("building").equals(t.clinic.id()),"The request carries the doctor's name and workshop");
  long plain=Quests.reputation(Pleas.PLEA);
  h.assertTrue(q.getLong("reputation")==plain*(100+(long)Quests.setting(Pleas.PLEA,"gratitude"))/100&&q.getLong("reputation")>plain,"A neighbour's own request earns gratitude on top: "+q.getLong("reputation")+" over "+plain);
  h.assertTrue(Pleas.post(t.l,t.e,12000)==null,"One request at a time");
  var supply=Quests.post(t.l,t.e,12000);
  h.assertTrue(supply==null||!supply.getString("item").equals("villageastra:bandage"),"The supply errand does not ask for the same thing again: "+supply);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken: everyday errands need no trust");
  var view=row(p,t,q.getUUID("id"));
  h.assertTrue(view!=null&&view.getString("giver").equals(doctor.profile().name())&&view.getBoolean("handover"),"The board names who asks and offers the hand-over: "+view);
  var hall=LogisticsRoutes.position(t.e,Workshops.hall(t.e));standAt(p,hall);
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("far_workshop"),"It is handed over at the doctor's own workshop, not at the stock");
  var clinic=LogisticsRoutes.position(t.e,t.clinic);standAt(p,clinic);
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("empty"),"Nothing is handed over with empty hands");
  p.getInventory().add(new ItemStack(VillageAstra.BANDAGE.get(),4));
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"Handed over at the clinic");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&LogisticsRoutes.chest(t.l,t.e,t.clinic).countItem(VillageAstra.BANDAGE.get())==4
   &&p.getInventory().countItem(VillageAstra.BANDAGE.get())==0,"The bandages really lie in the clinic");
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id"))
   &&PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score()==q.getLong("reputation"),"Paid exactly once, gratitude included");
  h.succeed();
 }
 // ---------------------------------------------------------------- trust and kin
 @GameTest(template="empty",timeoutTicks=200) public static void aRescueNeedsTrustAndBringsKinHomeToTheirRelative(GameTestHelper h){
  var t=town(h);
  // Another house stands first on the list: without kinship a newcomer would go there.
  t.s.addHome(new Settlement.Home(Settlement.childId(t.s.id(),"home/inn"),1,4,true));
  var relative=resident(t,"family",2);var family=relative.profile().name().substring(relative.profile().name().lastIndexOf(' ')+1);
  lead(t,QuestSites.COLLAPSE,1000);var p=player(t,"KinFinder",0);
  var q=Adventures.post(t.l,t.e,Adventures.COLLAPSE,2000);
  h.assertTrue(q!=null&&q.getInt("target")==2,"The fall holds two prospectors: "+q);
  h.assertTrue(q.getUUID("giver").equals(relative.id())&&q.getString("giver_name").equals(relative.profile().name()),"They are the kin of a resident: "+q.getString("giver_name"));
  var people=QuestSites.people(QuestSites.site(t.l,q.getUUID("id")));
  for(var id:people){var npc=(ResidentEntity)t.l.getEntity(id);
   h.assertTrue(npc.getCustomName()!=null&&npc.getCustomName().getString().endsWith(" "+family),"A prospector carries the family name "+family+": "+npc.getCustomName());}
  // AD-097: a life is not given to a stranger — the board waits for trust.
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("trust"),"A stranger is not trusted with lives");
  var view=row(p,t,q.getUUID("id"));
  h.assertTrue(view.getBoolean("locked")&&view.getInt("trust")==Quests.trust(Adventures.COLLAPSE)&&view.getLong("score")==0,"The board says how much trust it needs: "+view);
  PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Adventures.COLLAPSE));
  h.assertTrue(!row(p,t,q.getUUID("id")).getBoolean("locked")&&Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Trusted enough, the player takes it");
  // The first of them moves in with the relative; the house then has no bed, so the second goes where there is one.
  var first=(ResidentEntity)t.l.getEntity(people.get(0));var second=(ResidentEntity)t.l.getEntity(people.get(1));
  Camps.arrived(t.l,t.e,first,3000);Camps.arrived(t.l,t.e,second,3000);
  h.assertTrue(Camps.tickGuest(t.l,first,3010).equals("housed")&&Camps.tickGuest(t.l,second,3010).equals("housed"),"Both find a bed");
  var one=t.s.resident(first.getUUID());var two=t.s.resident(second.getUUID());
  h.assertTrue(one.home().equals(relative.home()),"The first moves in with the relative");
  h.assertTrue(!two.home().equals(relative.home()),"A full house takes nobody more: the second goes to the inn");
  h.assertTrue(one.profile().name().endsWith(" "+family)&&two.profile().name().endsWith(" "+family),"As residents they keep the family name: "+one.profile().name()+", "+two.profile().name());
  h.succeed();
 }
 // ---------------------------------------------------------------- the barrow's trail
 @GameTest(template="empty",timeoutTicks=400) public static void theBarrowLeadsToTheSanctuaryAndTheCryptKeptForItsFinder(GameTestHelper h){
  var t=town(h);t.s.civilization().begin("cartography");
  var p=player(t,"TrailFinder",Quests.trust(Adventures.RELIC));var stranger=player(t,"Stranger",Quests.trust(Adventures.RELIC));
  if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(Adventures.post(t.l,t.e,Adventures.RELIC,28800)==null,"A peaceful world gets no barrow");h.succeed();return;}
  lead(t,QuestSites.BARROW,2800);
  var relic=Adventures.post(t.l,t.e,Adventures.RELIC,28800);h.assertTrue(relic!=null,"The barrow is posted");
  var barrow=QuestSites.site(t.l,relic.getUUID("id"));
  h.assertTrue(Quests.take(p,t.s.id(),relic.getUUID("id")).equals("ok"),"Taken");
  var lab=LogisticsRoutes.position(t.e,t.s.buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElseThrow());
  p.getInventory().add(take(t,barrow));standAt(p,lab);
  h.assertTrue(Quests.handOver(p,t.s.id(),relic.getUUID("id")).equals("ok"),"The volume of the barrow is handed over");
  // The barrow names the next place: farther out along the same road, kept for this player, built when its land is in the world.
  var sanctuary=following(t,relic.getUUID("id"));
  h.assertTrue(sanctuary!=null&&sanctuary.getString("template").equals(Chains.SANCTUARY)&&sanctuary.getString("chain").equals("barrow")
   &&sanctuary.getInt("stage")==2&&sanctuary.getInt("stages")==3&&sanctuary.getBoolean("pending")&&sanctuary.getUUID("reserved").equals(p.getUUID()),"The trail leads on to the sanctuary: "+sanctuary);
  var anchor=BlockPos.of(sanctuary.getLong("site"));var from=QuestSites.origin(barrow);
  h.assertTrue(Math.abs(Math.sqrt(anchor.distSqr(from))-Quests.number("chain_step"))<=2,"The next place lies one step of the trail beyond the barrow: "+Math.sqrt(anchor.distSqr(from)));
  h.assertTrue(anchor.distSqr(t.center)>from.distSqr(t.center),"The next place lies farther out than the barrow: "+anchor.toShortString()+" after "+from.toShortString());
  h.assertTrue(Adventures.post(t.l,t.e,Chains.SANCTUARY,30000)==null&&!Adventures.ROTATION.contains(Chains.SANCTUARY),"A stage is never offered on its own");
  h.assertTrue(Quests.take(stranger,t.s.id(),sanctuary.getUUID("id")).equals("reserved")&&row(stranger,t,sanctuary.getUUID("id")).getBoolean("reserved"),"Another player cannot take it");
  h.assertTrue(Quests.take(p,t.s.id(),sanctuary.getUUID("id")).equals("ok"),"Its finder takes it");
  var marks=p.getInventory().items.stream().filter(x->x.is(Items.FILLED_MAP)&&x.hasTag()&&x.getTag().contains("Decorations")).toList();
  h.assertTrue(marks.stream().anyMatch(x->{var d=x.getTag().getList("Decorations",Tag.TAG_COMPOUND);return d.stream().map(m->(CompoundTag)m).anyMatch(m->m.getString("id").equals("astra_quest")&&Math.abs((int)m.getDouble("x")-anchor.getX())<=1&&Math.abs((int)m.getDouble("z")-anchor.getZ())<=1);}),
   "The chart already shows the way before the place stands: anchor "+anchor.toShortString()+" marks "+marks.stream().map(x->x.getTag().getList("Decorations",Tag.TAG_COMPOUND).toString()).toList());
  // The place stands where the land is: the test hands it ground of its own instead of waiting for far chunks.
  h.assertTrue(Chains.materialize(t.l,t.e,sanctuary.getUUID("id"),pocket(t,QuestSites.SANCTUM,false),31000),"The sanctuary is built");
  var built=Quests.quest(t.l,t.s.id(),sanctuary.getUUID("id"));var site=QuestSites.site(t.l,sanctuary.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(!built.getBoolean("pending")&&BlockPos.of(built.getLong("site")).equals(origin),"The quest now points at the place itself");
  h.assertTrue(count(t,origin,4,0,4,Blocks.STONE_BRICKS)>=8&&count(t,origin,4,1,1,Blocks.CHISELED_STONE_BRICKS)>=1&&count(t,origin,4,1,1,Blocks.CHEST)==1&&count(t,origin,4,2,2,Blocks.CANDLE)==1,
   "The court, the pillars, the altar and its candles stand");
  h.assertTrue(site.getList("mobs",Tag.TAG_INT_ARRAY).size()==3,"Three of the dead keep it");
  var tablet=take(t,site);
  h.assertTrue(tablet.hasTag()&&tablet.getTag().getString(QuestSites.RELIC).equals(sanctuary.getUUID("id").toString()),"The tablet belongs to this stage: "+tablet);
  var civ=t.s.civilization();long before=civ.progress();
  p.getInventory().add(tablet);standAt(p,lab);
  h.assertTrue(Quests.handOver(p,t.s.id(),sanctuary.getUUID("id")).equals("ok"),"The tablet is handed over to the scholars");
  h.assertTrue(civ.progress()>before||civ.completed().contains("cartography"),"The scholars really advanced: "+civ.progress());
  var crypt=following(t,sanctuary.getUUID("id"));
  h.assertTrue(crypt!=null&&crypt.getString("template").equals(Chains.CRYPT)&&crypt.getInt("stage")==3&&crypt.getUUID("reserved").equals(p.getUUID()),"The sanctuary leads on to the crypt: "+crypt);
  h.assertTrue(Chains.materialize(t.l,t.e,crypt.getUUID("id"),pocket(t,QuestSites.CRYPT,true),32000),"The crypt is built");
  var vault=QuestSites.site(t.l,crypt.getUUID("id"));var mouth=QuestSites.origin(vault);
  h.assertTrue(count(t,mouth,4,0,3,Blocks.STONE_BRICKS)>=20&&count(t,mouth,4,-5,-1,Blocks.LADDER)>=4&&count(t,mouth,4,-5,-5,Blocks.CHEST)==1,"The mausoleum stands over a vault reached by a ladder");
  var guard=t.l.getEntity(vault.getUUID("chief"));
  h.assertTrue(guard instanceof LivingEntity g&&guard.getType()==EntityType.WITHER_SKELETON&&g.getMaxHealth()==(float)Quests.setting(Chains.CRYPT,"health"),"The king's guard is stronger than its kind: "+guard);
  h.assertTrue(Quests.take(p,t.s.id(),crypt.getUUID("id")).equals("ok"),"Taken");
  Adventures.advance(t.l,t.e,crypt.getUUID("id"),p,32100);
  h.assertTrue(Quests.quest(t.l,t.s.id(),crypt.getUUID("id")).getString("status").equals("guard"),"The board knows the guard still stands");
  ((LivingEntity)guard).kill();
  var chronicle=take(t,vault);
  var hoard=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(vault.getLong("chest")));
  boolean crown=false;for(int slot=0;slot<hoard.getContainerSize();slot++)if(hoard.getItem(slot).is(Items.GOLDEN_HELMET))crown=true;
  h.assertTrue(crown&&chronicle.hasTag()&&chronicle.getTag().getString(QuestSites.RELIC).equals(crypt.getUUID("id").toString()),"The king lies with his crown and his chronicle");
  p.getInventory().add(chronicle);standAt(p,lab);
  h.assertTrue(Quests.handOver(p,t.s.id(),crypt.getUUID("id")).equals("ok")&&Quests.quest(t.l,t.s.id(),crypt.getUUID("id")).getString("state").equals(Quests.DONE),"The chronicle closes the trail");
  h.assertTrue(following(t,crypt.getUUID("id"))==null,"The crypt is the end of the barrow's trail");
  h.succeed();
 }
 // ---------------------------------------------------------------- the band's trail and the expedition's find
 @GameTest(template="empty",timeoutTicks=300) public static void theLairLeadsToAWarCampWhoseFallQuietsTheRaidsLonger(GameTestHelper h){
  var t=town(h);var p=player(t,"CampBreaker",Quests.trust(Adventures.LAIR));
  var lair=new CompoundTag();lair.putUUID("id",UUID.randomUUID());lair.putString("template",Adventures.LAIR);lair.putLong("site",t.center.offset(-AWAY,1,2).asLong());
  var camp=Chains.advance(p,t.s.id(),lair);
  if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(camp==null,"A peaceful world gets no war camp");h.succeed();return;}
  h.assertTrue(camp!=null&&camp.getString("template").equals(Chains.WARCAMP)&&camp.getString("kind").equals(QuestSites.WARCAMP)&&camp.getInt("stage")==2&&camp.getInt("stages")==2,"The lair's fall leads to the war camp: "+camp);
  h.assertTrue(Chains.advance(p,t.s.id(),lair)==null,"A finished stage leads on only once");
  h.assertTrue(Chains.materialize(t.l,t.e,camp.getUUID("id"),pocket(t,QuestSites.WARCAMP,false),5000),"The war camp is built");
  var site=QuestSites.site(t.l,camp.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(count(t,origin,5,0,2,Blocks.SPRUCE_LOG)>=60&&count(t,origin,5,0,0,Blocks.RED_BANNER)==3,"The palisade stands under war banners");
  h.assertTrue(QuestSites.people(site).isEmpty(),"Its cage is empty: the prisoners were sold");
  var band=site.getList("mobs",Tag.TAG_INT_ARRAY);
  h.assertTrue(band.size()==7,"Seven of the band hold it: "+band.size());
  var warlord=t.l.getEntity(site.getUUID("chief"));
  h.assertTrue(warlord!=null&&warlord.getType()==EntityType.EVOKER&&((LivingEntity)warlord).getMaxHealth()==(float)Quests.setting(Chains.WARCAMP,"health"),"The warlord is an evoker grown strong: "+warlord);
  h.assertTrue(Quests.take(p,t.s.id(),camp.getUUID("id")).equals("ok"),"Taken");
  p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+6.5);
  Adventures.advance(t.l,t.e,camp.getUUID("id"),p,5100);
  h.assertTrue(Quests.quest(t.l,t.s.id(),camp.getUUID("id")).getInt("progress")==0,"While the band stands the camp is not broken");
  for(var raw:band)if(t.l.getEntity(NbtUtils.loadUUID(raw)) instanceof LivingEntity raider)raider.kill();
  Adventures.advance(t.l,t.e,camp.getUUID("id"),p,5200);
  h.assertTrue(Quests.quest(t.l,t.s.id(),camp.getUUID("id")).getInt("progress")==1,"The band is broken");
  h.assertTrue(Raids.record(t.l,t.s.id()).getLong("nextAt")>=5200+(long)Quests.setting(Chains.WARCAMP,"calm")
   &&Quests.setting(Chains.WARCAMP,"calm")>Quests.setting(Adventures.LAIR,"calm"),"The raids stay away longer than after a lair: "+Raids.record(t.l,t.s.id()).getLong("nextAt"));
  h.assertTrue(Quests.complete(p,t.s.id(),camp.getUUID("id"))&&p.getInventory().countItem(VillageAstra.ZINDBO.get())==camp.getLong("coins"),"Paid");
  h.assertTrue(following(t,camp.getUUID("id"))==null,"The war camp is the end of the band's trail");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theExpeditionsFindIsARichGoldSeam(GameTestHelper h){
  var t=town(h);var p=player(t,"GoldDigger",0);
  var lost=new CompoundTag();lost.putUUID("id",UUID.randomUUID());lost.putString("template",Adventures.LOST);lost.putLong("site",t.center.offset(-AWAY,1,2).asLong());
  var lode=Chains.advance(p,t.s.id(),lost);
  h.assertTrue(lode!=null&&lode.getString("template").equals(Chains.RICHLODE)&&lode.getString("item").equals("minecraft:raw_gold")&&lode.getInt("target")==10,"The survivors told of a gold seam: "+lode);
  h.assertTrue(lode.getLong("coins")==Quests.coins(Chains.RICHLODE)+Quests.value(Items.RAW_GOLD,10),"It pays its time and the gold at the village's prices");
  h.assertTrue(Chains.materialize(t.l,t.e,lode.getUUID("id"),pocket(t,QuestSites.ADIT,true),4000),"The adit is built");
  var site=QuestSites.site(t.l,lode.getUUID("id"));var origin=QuestSites.origin(site);
  int gold=count(t,origin,3,-8,0,Blocks.GOLD_ORE)+count(t,origin,3,-8,0,Blocks.DEEPSLATE_GOLD_ORE);
  h.assertTrue(QuestSites.seamLeft(t.l,site)==12&&gold==12,"Twelve blocks of gold ore stand in the wall: "+gold);
  h.assertTrue(Quests.take(p,t.s.id(),lode.getUUID("id")).equals("ok"),"The finder takes it");
  // QUEST-002: the gold the village pays for is the gold of this seam, so ten blocks of it really come out of the wall first.
  for(int i=0;i<10;i++)t.l.destroyBlock(BlockPos.of(((net.minecraft.nbt.LongTag)site.getList("seam",net.minecraft.nbt.Tag.TAG_LONG).get(i)).getAsLong()),false);
  h.assertTrue(QuestSites.seamLeft(t.l,site)==2,"Ten of the twelve are out of the wall: "+QuestSites.seamLeft(t.l,site));
  p.getInventory().add(new ItemStack(Items.RAW_GOLD,10));standAt(p,LogisticsRoutes.position(t.e,Workshops.hall(t.e)));
  h.assertTrue(Quests.handOver(p,t.s.id(),lode.getUUID("id")).equals("ok"),"Handed over at the stock");
  h.assertTrue(Quests.quest(t.l,t.s.id(),lode.getUUID("id")).getString("state").equals(Quests.DONE)&&LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e)).countItem(Items.RAW_GOLD)==10,"The gold really lies in the stock");
  h.assertTrue(following(t,lode.getUUID("id"))==null,"The seam is the end of the expedition's find");
  h.succeed();
 }
}
