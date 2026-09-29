package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-105: the life of a quest from posting to its end. Nothing paid is undone by a later write, a card that comes back after an abandonment
 *  can still be finished, a rescue closes once everybody is accounted for and its killer pays, what was really delivered is paid even when
 *  the time runs out, the chart keeps its cross, a request follows its workshop, a lost record is broken rather than failed. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestLifecycleGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office,Settlement.Building clinic){}
 private static final int AWAY=26;
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-12;x<30;x++)for(int z=-5;z<24;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
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
 private static long score(Town t,ServerPlayer p){return PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score();}
 private static long owed(Town t,ServerPlayer p){return TradeLedger.get(t.l.getServer()).owed(p.getUUID());}
 private static int coins(ServerPlayer p){return p.getInventory().countItem(VillageAstra.ZINDBO.get());}
 private static CompoundTag quest(Town t,UUID id){return Quests.quest(t.l,t.s.id(),id);}
 private static Resident resident(Town t,String home,int beds){
  var house=new Settlement.Home(Settlement.childId(t.s.id(),"home/"+home),1,beds,true);t.s.addHome(house);
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(r,house.id());return r;
 }
 /** A pocket of this test's own ground for one design, never on a site already standing. */
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
 private static void report(Town t,BlockPos at,long now){
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,8));chest.setItem(1,new ItemStack(Items.BREAD,8));
  if(Expeditions.report(t.l,t.e,t.office,at,0,now)==null)throw new GameTestAssertException("The prepared ground was not reported as a lead");
 }
 /** The flat camp ground east of the village, reported as a camp lead. */
 private static void campLead(Town t,long now){report(t,new BlockPos(t.center.getX()+20,t.center.getY()+1,t.center.getZ()+16),now);}
 private static List<ResidentEntity> travellers(Town t,CompoundTag q){
  var out=new ArrayList<ResidentEntity>();
  for(var raw:Camps.camp(t.l,q.getUUID("camp")).getList("travellers",Tag.TAG_COMPOUND))if(t.l.getEntity(((CompoundTag)raw).getUUID("id")) instanceof ResidentEntity npc)out.add(npc);
  return out;
 }
 private static void home(Town t,ResidentEntity npc,int i){npc.moveTo(t.center.getX()+2.5+i,t.center.getY()+1,t.center.getZ()+2.5,0,0);}
 private static CompoundTag finished(String template,BlockPos site){var d=new CompoundTag();d.putUUID("id",UUID.randomUUID());d.putString("template",template);d.putLong("site",site.asLong());return d;}
 // ---------------------------------------------------------------- nothing paid is undone
 @GameTest(template="empty",timeoutTicks=300) public static void aPaidDeedIsNotUndoneByAnExpiryInTheSameTick(GameTestHelper h){
  var t=town(h);var p=player(t,"CampBreaker",Quests.trust(Adventures.LAIR));
  var camp=Chains.advance(p,t.s.id(),finished(Adventures.LAIR,t.center.offset(-AWAY,1,2)));
  if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(camp==null,"A peaceful world gets no war camp");h.succeed();return;}
  h.assertTrue(Chains.materialize(t.l,t.e,camp.getUUID("id"),pocket(t,QuestSites.WARCAMP,false),1000),"The war camp is built");
  var supply=Quests.post(t.l,t.e,1000);
  h.assertTrue(supply!=null&&supply.getLong("deadline")<camp.getLong("deadline"),"An errand that will expire first stands on the same board: "+supply);
  h.assertTrue(Quests.take(p,t.s.id(),camp.getUUID("id")).equals("ok"),"Taken");
  var site=QuestSites.site(t.l,camp.getUUID("id"));var origin=QuestSites.origin(site);p.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+6.5);
  for(var raw:site.getList("mobs",Tag.TAG_INT_ARRAY))if(t.l.getEntity(NbtUtils.loadUUID(raw)) instanceof LivingEntity raider)raider.kill();
  Adventures.advance(t.l,t.e,camp.getUUID("id"),p,1100);
  h.assertTrue(quest(t,camp.getUUID("id")).getInt("progress")==1,"The band is broken");
  // One pass of the board: the errand expires and the broken camp is paid, both in the same tick. The owner is away: coins are owed.
  long expiry=supply.getLong("deadline")+1;
  Quests.tick(t.l,t.e,expiry);Quests.tick(t.l,t.e,expiry+2);
  h.assertTrue(quest(t,supply.getUUID("id")).getString("state").equals(Quests.FAILED),"The errand expired: "+quest(t,supply.getUUID("id")).getString("state"));
  h.assertTrue(quest(t,camp.getUUID("id")).getString("state").equals(Quests.DONE),"The paid war camp stays done: "+quest(t,camp.getUUID("id")).getString("state"));
  h.assertTrue(owed(t,p)==camp.getLong("coins")&&score(t,p)==Quests.trust(Adventures.LAIR)+camp.getLong("reputation"),
   "Paid once while away: owed "+owed(t,p)+" of "+camp.getLong("coins")+", score "+score(t,p));
  h.succeed();
 }
 // ---------------------------------------------------------------- the card that comes back
 @GameTest(template="empty",timeoutTicks=300) public static void anAbandonedRescueComesBackAndTheNextTakerBringsThemHome(GameTestHelper h){
  var t=town(h);
  if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.succeed();return;}
  report(t,pocket(t,QuestSites.WRECK,false),1000);
  var first=player(t,"GaveUp",Quests.trust(Adventures.LOST));var second=player(t,"CameBack",Quests.trust(Adventures.LOST));
  var lost=Adventures.post(t.l,t.e,Adventures.LOST,2000);h.assertTrue(lost!=null,"The wreck is posted");
  h.assertTrue(Quests.take(first,t.s.id(),lost.getUUID("id")).equals("ok")&&Quests.abandon(first,t.s.id(),lost.getUUID("id")).equals("ok"),"Taken and given up");
  h.assertTrue(score(t,first)==Quests.trust(Adventures.LOST)-Quests.ABANDON_REPUTATION,"Giving up costs reputation");
  var fresh=Quests.board(t.l,t.s.id()).getList("quests",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).filter(x->x.getString("state").equals(Quests.OPEN)&&x.getString("template").equals(Adventures.LOST)).findFirst().orElse(null);
  h.assertTrue(fresh!=null&&!fresh.getUUID("id").equals(lost.getUUID("id"))&&fresh.getUUID("origin").equals(lost.getUUID("id"))&&!fresh.hasUUID("owner"),"The need comes back as a new card that the wreck still knows: "+fresh);
  Adventures.tick(t.l,t.e,2100);
  h.assertTrue(QuestSites.site(t.l,lost.getUUID("id")).getString("state").equals("open"),"The given-up card does not close the place the new one uses");
  h.assertTrue(Quests.take(first,t.s.id(),fresh.getUUID("id")).equals("given_up"),"Whoever gave the need up does not take it back with a fresh deadline");
  h.assertTrue(Quests.take(second,t.s.id(),fresh.getUUID("id")).equals("ok"),"The next player takes it");
  var people=QuestSites.people(QuestSites.site(t.l,lost.getUUID("id")));int i=0;
  for(var id:people){var npc=(ResidentEntity)t.l.getEntity(id);second.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),8));
   String care="";for(int n=0;n<6&&!care.equals("treated");n++)care=Adventures.treat(second,npc,InteractionHand.MAIN_HAND);
   h.assertTrue(care.equals("treated"),"The new taker can treat the survivors of the first card: "+care);home(t,npc,i++);}
  Quests.escorted(t.l,t.e,fresh.getUUID("id"),second,2200);
  var done=quest(t,fresh.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&coins(second)==fresh.getLong("coins"),"All three came home with the new taker, who is paid: "+done.getString("state")+" "+coins(second));
  Adventures.advance(t.l,t.e,fresh.getUUID("id"),second,2300);
  h.assertTrue(QuestSites.site(t.l,lost.getUUID("id")).getString("state").equals("closed"),"The place closes with the card that finished it");
  h.succeed();
 }
 // ---------------------------------------------------------------- everybody accounted for
 @GameTest(template="empty",timeoutTicks=200) public static void aRescueClosesWhenEveryoneIsAccountedForAndTheKillerPays(GameTestHelper h){
  var t=town(h);campLead(t,400);var owner=player(t,"Rescuer",Quests.trust(Quests.RESCUE));var killer=player(t,"Killer",0);
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,500);h.assertTrue(q!=null&&q.getInt("target")==3,"A rescue of three: "+q);
  h.assertTrue(Quests.take(owner,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var people=travellers(t,q);h.assertTrue(people.size()==3,"Three real travellers");
  home(t,people.get(0),0);home(t,people.get(1),1);
  Quests.escorted(t.l,t.e,q.getUUID("id"),owner,600);
  h.assertTrue(quest(t,q.getUUID("id")).getInt("progress")==2&&quest(t,q.getUUID("id")).getString("state").equals(Quests.TAKEN),"Two are home, one is still out there");
  people.get(2).hurt(t.l.damageSources().playerAttack(killer),1000f);
  h.assertTrue(!people.get(2).isAlive(),"The third is killed by another player");
  h.assertTrue(score(t,killer)==-Quests.KILL_REPUTATION,"The killer pays as for killing a resident: "+score(t,killer));
  Quests.escorted(t.l,t.e,q.getUUID("id"),owner,700);
  var done=quest(t,q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&coins(owner)==q.getLong("coins")*2/3,"Everybody is accounted for: the rescue closes for the two who came, while they still wait for a bed: "+done.getString("state")+" "+coins(owner));
  h.assertTrue(score(t,owner)==Quests.trust(Quests.RESCUE)+q.getLong("reputation")*2/3,"The owner keeps the result of what they really did: "+score(t,owner));
  h.assertTrue(Camps.guest(t.l,people.get(0).getUUID()).getString("state").equals("waiting"),"The guests were not made to wait for the pay");
  h.succeed();
 }
 // ---------------------------------------------------------------- what the clock cannot take
 @GameTest(template="empty",timeoutTicks=200) public static void goodsBroughtArePaidEvenWhenTheTimeRunsOut(GameTestHelper h){
  var t=town(h);campLead(t,400);var p=player(t,"HalfCarrier",0);
  var q=Quests.postDistant(t.l,t.e,Quests.CARGO,500);h.assertTrue(q!=null&&q.getInt("target")==16,"Cargo of sixteen: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var camp=Camps.camp(t.l,q.getUUID("camp"));var cargo=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(camp.getLong("chest")));
  var item=cargo.getItem(0).getItem();cargo.setItem(0,ItemStack.EMPTY);p.getInventory().add(new ItemStack(item,10));
  var hall=LogisticsRoutes.position(t.e,Workshops.hall(t.e));p.setPos(hall.getX()+.5,hall.getY(),hall.getZ()+1.5);
  h.assertTrue(Quests.deliver(p,t.s.id(),q.getUUID("id")).equals("ok")&&quest(t,q.getUUID("id")).getInt("progress")==10,"Ten of sixteen are brought");
  Quests.tick(t.l,t.e,q.getLong("deadline")+1);
  var closed=quest(t,q.getUUID("id"));
  h.assertTrue(closed.getString("state").equals(Quests.FAILED),"The time ran out: "+closed.getString("state"));
  h.assertTrue(owed(t,p)==q.getLong("coins")*10/16,"The ten brought are paid, owed while away: "+owed(t,p)+" of "+q.getLong("coins"));
  h.assertTrue(score(t,p)==q.getLong("reputation")*10/16-Quests.FAIL_REPUTATION*6/16,"The failure costs only for the part left undone: "+score(t,p));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void peopleWhoArrivedAreASuccessTheClockDoesNotTakeBack(GameTestHelper h){
  var t=town(h);campLead(t,400);var p=player(t,"SlowRescuer",Quests.trust(Quests.RESCUE));
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,500);h.assertTrue(q!=null,"A rescue");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  home(t,travellers(t,q).get(0),0);Quests.escorted(t.l,t.e,q.getUUID("id"),p,600);
  h.assertTrue(quest(t,q.getUUID("id")).getInt("progress")==1,"One came home");
  Quests.tick(t.l,t.e,q.getLong("deadline")+1);
  var closed=quest(t,q.getUUID("id"));
  h.assertTrue(closed.getString("state").equals(Quests.DONE)&&owed(t,p)==q.getLong("coins")/3&&score(t,p)==Quests.trust(Quests.RESCUE)+q.getLong("reputation")/3,
   "The one who came home is a success, paid with no penalty: "+closed.getString("state")+" owed "+owed(t,p)+" score "+score(t,p));
  h.succeed();
 }
 // ---------------------------------------------------------------- the chart
 @GameTest(template="empty",timeoutTicks=100) public static void theChartKeepsItsCrossWhereverThePlaceLies(GameTestHelper h){
  var t=town(h);var p=player(t,"MapReader",0);
  // The last place lies exactly on a line of the map grid, where vanilla would drop a mark at every scale.
  int edge=-64+2048*Math.floorDiv(t.center.getX(),2048)-t.center.getX();
  for(var offset:new int[][]{{92,0},{64,-70},{-40,30},{700,-200},{1500,300},{edge,0}}){
   var q=new CompoundTag();q.putString("template",Adventures.LOST);q.putString("kind",QuestSites.WRECK);q.putLong("site",t.center.offset(offset[0],0,offset[1]).asLong());
   var chart=Adventures.chart(t.l,t.e,q);var data=net.minecraft.world.item.MapItem.getSavedData(chart,t.l);
   h.assertTrue(data!=null,"The chart is a real map");data.tickCarriedBy(p,chart);
   var types=new ArrayList<MapDecoration.Type>();for(var d:data.getDecorations())types.add(d.getType());
   h.assertTrue(types.contains(MapDecoration.Type.RED_X),"The cross stays on the chart for a place "+offset[0]+","+offset[1]+" away: "+types);
   if(Math.abs(offset[0])<200&&offset[0]!=edge)h.assertTrue(types.contains(MapDecoration.Type.TARGET_POINT),"A near chart marks the village the people are brought back to: "+types);
  }
  h.succeed();
 }
 // ---------------------------------------------------------------- the request and its workshop
 @GameTest(template="empty",timeoutTicks=200) public static void aRequestFollowsTheWorkshopNotThePerson(GameTestHelper h){
  var t=town(h);var first=resident(t,"first",2);t.s.assign(first.id(),Profession.DOCTOR,t.clinic.id());
  var q=Pleas.post(t.l,t.e,6000);h.assertTrue(q!=null&&q.getUUID("giver").equals(first.id()),"The doctor asks: "+q);
  t.s.release(first.id());var second=resident(t,"second",2);t.s.assign(second.id(),Profession.DOCTOR,t.clinic.id());
  Quests.tick(t.l,t.e,6101);
  var moved=quest(t,q.getUUID("id"));
  h.assertTrue(moved.getString("state").equals(Quests.OPEN)&&moved.getUUID("giver").equals(second.id())&&moved.getString("giver_name").equals(second.profile().name()),"The clinic's new doctor speaks for its need: "+moved.getString("giver_name"));
  t.s.release(second.id());
  Quests.tick(t.l,t.e,6201);
  var gone=quest(t,q.getUUID("id"));
  h.assertTrue(gone.getString("state").equals(Quests.CANCELLED)&&gone.getString("reason").equals("giver_gone"),"Nobody works there any more: the request is called off: "+gone.getString("state"));
  h.succeed();
 }
 // ---------------------------------------------------------------- a stage its finder gave up
 @GameTest(template="empty",timeoutTicks=200) public static void aStageItsFinderGaveUpIsOpenToTheTrusted(GameTestHelper h){
  var t=town(h);var finder=player(t,"Finder",0);var stranger=player(t,"Stranger",0);var trusted=player(t,"Trusted",Quests.trust(Adventures.LOST));
  var lode=Chains.advance(finder,t.s.id(),finished(Adventures.LOST,t.center.offset(-AWAY,1,2)));
  h.assertTrue(lode!=null&&Quests.take(finder,t.s.id(),lode.getUUID("id")).equals("ok"),"The finder takes the gold seam kept for them");
  h.assertTrue(Quests.abandon(finder,t.s.id(),lode.getUUID("id")).equals("ok"),"And gives it up");
  var fresh=Quests.board(t.l,t.s.id()).getList("quests",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).filter(x->x.getString("state").equals(Quests.OPEN)&&x.getString("template").equals(Chains.RICHLODE)).findFirst().orElse(null);
  h.assertTrue(fresh!=null&&!fresh.hasUUID("reserved")&&fresh.getUUID("origin").equals(lode.getUUID("id"))&&fresh.getBoolean("pending"),"The stage comes back kept for nobody: "+fresh);
  h.assertTrue(Quests.trust(Chains.RICHLODE)==Quests.trust(Adventures.LOST)&&Quests.take(stranger,t.s.id(),fresh.getUUID("id")).equals("trust"),"It asks for the trust its trail began with");
  h.assertTrue(Quests.take(trusted,t.s.id(),fresh.getUUID("id")).equals("ok"),"A trusted player takes it");
  h.assertTrue(Chains.materialize(t.l,t.e,fresh.getUUID("id"),pocket(t,QuestSites.ADIT,true),3000),"Its place is built");
  h.assertTrue(QuestSites.site(t.l,lode.getUUID("id"))!=null,"Under the id the trail was found by");
  // QUEST-002: the stage asked for the gold of that seam, so the seam is worked before the hall will take any.
  var vein=QuestSites.site(t.l,lode.getUUID("id"));int need=fresh.getInt("target");
  var blocks=vein.getList("seam",Tag.TAG_LONG);
  for(int i=0;i<need&&i<blocks.size();i++)t.l.destroyBlock(BlockPos.of(((net.minecraft.nbt.LongTag)blocks.get(i)).getAsLong()),false);
  trusted.getInventory().add(new ItemStack(Items.RAW_GOLD,10));var hall=LogisticsRoutes.position(t.e,Workshops.hall(t.e));trusted.setPos(hall.getX()+.5,hall.getY(),hall.getZ()+1.5);
  h.assertTrue(Quests.handOver(trusted,t.s.id(),fresh.getUUID("id")).equals("ok")&&quest(t,fresh.getUUID("id")).getString("state").equals(Quests.DONE),"The gold of that seam is handed over and the stage is done");
  h.succeed();
 }
 // ---------------------------------------------------------------- a lost record
 @GameTest(template="empty",timeoutTicks=200) public static void aPlaceWhoseRecordIsLostIsBrokenNotFailed(GameTestHelper h){
  var t=town(h);report(t,pocket(t,QuestSites.ADIT,true),400);var p=player(t,"Unlucky",0);
  var q=Adventures.post(t.l,t.e,Adventures.LODE,7200);h.assertTrue(q!=null,"The seam is posted");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  try{java.nio.file.Files.delete(t.l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-quest-sites/"+q.getUUID("id")+".bin"));}
  catch(java.io.IOException ex){throw new GameTestAssertException("The fixture could not lose the record: "+ex);}
  Adventures.advance(t.l,t.e,q.getUUID("id"),p,7300);
  var broken=quest(t,q.getUUID("id"));
  h.assertTrue(broken.getString("state").equals(Quests.BROKEN)&&broken.getString("reason").equals("site_missing")&&score(t,p)==0,"A lost record is the mod's fault, not the player's: "+broken.getString("state")+" "+score(t,p));
  h.succeed();
 }

 // ---------------------------------------------------------------- errands the world made impossible
 @GameTest(template="empty",timeoutTicks=200) public static void aRescueWhosePeopleAreAllGoneIsCalledOffBeforeAnybodyTakesIt(GameTestHelper h){
  var t=town(h);campLead(t,400);var killer=player(t,"Griefer",0);var late=player(t,"TooLate",Quests.trust(Quests.RESCUE));
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,500);h.assertTrue(q!=null,"A rescue");
  for(var npc:travellers(t,q))npc.hurt(t.l.damageSources().playerAttack(killer),1000f);
  var closed=quest(t,q.getUUID("id"));
  h.assertTrue(closed.getString("state").equals(Quests.CANCELLED)&&closed.getString("reason").equals("people_lost"),"Nobody is left to bring home: the errand is called off: "+closed.getString("state"));
  h.assertTrue(!Quests.take(late,t.s.id(),q.getUUID("id")).equals("ok")&&score(t,late)==Quests.trust(Quests.RESCUE),"Nobody can take it only to fail");
  h.assertTrue(score(t,killer)==-3L*Quests.KILL_REPUTATION,"Each of the three killed costs the killer: "+score(t,killer));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void givingUpADeedAlreadyDoneSettlesItAsDone(GameTestHelper h){
  var t=town(h);campLead(t,400);var p=player(t,"NotQuitting",Quests.trust(Quests.RESCUE));var killer=player(t,"Bandit",0);
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,500);h.assertTrue(q!=null&&Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var people=travellers(t,q);home(t,people.get(0),0);Quests.escorted(t.l,t.e,q.getUUID("id"),p,600);
  people.get(1).hurt(t.l.damageSources().playerAttack(killer),1000f);people.get(2).hurt(t.l.damageSources().playerAttack(killer),1000f);
  h.assertTrue(Quests.abandon(p,t.s.id(),q.getUUID("id")).equals("done"),"Everybody is accounted for: there is nothing left to give up");
  h.assertTrue(quest(t,q.getUUID("id")).getString("state").equals(Quests.DONE)&&coins(p)==q.getLong("coins")/3&&score(t,p)==Quests.trust(Quests.RESCUE)+q.getLong("reputation")/3,
   "The one brought home is paid and no abandonment is charged: "+coins(p)+" "+score(t,p));
  h.assertTrue(Quests.board(t.l,t.s.id()).getList("quests",Tag.TAG_COMPOUND).stream().noneMatch(x->((CompoundTag)x).getString("state").equals(Quests.OPEN)&&((CompoundTag)x).getString("template").equals(Quests.RESCUE)),"No card comes back for people nobody can bring");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void aRelicGivenUpGoesBackToItsChest(GameTestHelper h){
  var t=town(h);
  if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.succeed();return;}
  report(t,pocket(t,QuestSites.BARROW,false),1000);var p=player(t,"Grave",Quests.trust(Adventures.RELIC));
  var q=Adventures.post(t.l,t.e,Adventures.RELIC,2000);h.assertTrue(q!=null&&Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"The barrow is taken");
  var site=QuestSites.site(t.l,q.getUUID("id"));var chest=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(site.getLong("chest")));
  ItemStack volume=ItemStack.EMPTY;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(VillageAstra.RESEARCH_VOLUME.get())){volume=chest.getItem(i).copy();chest.setItem(i,ItemStack.EMPTY);}
  h.assertTrue(!volume.isEmpty(),"The volume was in the barrow");p.getInventory().add(volume);
  h.assertTrue(Quests.abandon(p,t.s.id(),q.getUUID("id")).equals("ok"),"Given up with the volume in hand");
  int inChest=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(VillageAstra.RESEARCH_VOLUME.get()))inChest++;
  h.assertTrue(inChest==1&&p.getInventory().countItem(VillageAstra.RESEARCH_VOLUME.get())==0,"The volume goes back into the barrow's chest: "+inChest);
  h.assertTrue(Quests.board(t.l,t.s.id()).getList("quests",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).anyMatch(x->x.getString("state").equals(Quests.OPEN)&&x.hasUUID("origin")&&x.getUUID("origin").equals(q.getUUID("id"))),"And the barrow comes back to the board for somebody else");
  h.succeed();
 }
}
