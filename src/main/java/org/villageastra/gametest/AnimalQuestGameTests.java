package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-140: every kind of animal comes to the yard through its own quest. A card asks for the first kind the yard keeps and has not got, one at a
 *  time; a village that kept a kind before the rule keeps it. The stolen flock is freed once the rustlers are beaten and the pen opened; a stuck cow refuses
 *  the rope until a way out exists that her own pathfinding finds; every blow cracks one warm egg, a warm egg is never thrown, and a laid egg
 *  hatches only while the hen is penned. Animals count once each, let go, in a pen of their kind; the pair opens the kind; losses fail the card. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AnimalQuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building yard){
  BlockPos pen(String kind){var at=AnimalYard.middle(l,e,kind);if(at==null)throw new GameTestAssertException("No working pen of "+kind);return at;}}
 private static final int AWAY=40;
 private static Town town(GameTestHelper h,int level){
  var l=h.getLevel();
  // The world is marked once, before this village exists: a new village is never one of those that kept animals before the rule.
  AnimalUnlocks.migrateWorld(l.getServer());
  var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-12;x<34;x++)for(int z=-5;z<30;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var yard=new Settlement.Building(Settlement.childId(s.id(),"building/livestock"),"livestock",12,0,0);s.addBuilding(yard);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  var chest=LogisticsRoutes.position(e,yard);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  AnimalYard.TEST_LEVEL.put(s.id(),level);
  return new Town(l,s,e,center,yard);
 }
 private static ServerPlayer player(Town t,String name,int trust){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  if(trust>0)PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),trust);
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 private static CompoundTag quest(Town t,UUID id){return Quests.quest(t.l,t.s.id(),id);}
 /** A raised mound of this test's own ground, far enough from the village, where the card's place is built; never on ground another test holds. */
 private static BlockPos mound(Town t){return mound(t,16);}
 private static BlockPos mound(Town t,int r){
  for(int[] o:QuestPockets.offsets(AWAY)){
   var at=new BlockPos(t.center.getX()+o[0],t.center.getY()+8,t.center.getZ()+o[1]);boolean held=false;
   for(int dx=-r;dx<=r&&!held;dx+=2)for(int dz=-r;dz<=r&&!held;dz+=2)for(int dy=-9;dy<=6&&!held;dy+=3)if(OwnershipEvents.disallowedPlacement(t.l,at.offset(dx,dy,dz)))held=true;
   if(held)continue;
   for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
    for(int y=-8;y<0;y++)t.l.setBlock(at.offset(dx,y,dz),(y==-1?Blocks.GRASS_BLOCK:y==-2?Blocks.DIRT:Blocks.STONE).defaultBlockState(),2);
    for(int y=0;y<=7;y++)t.l.setBlock(at.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
   return at.below();
  }
  throw new GameTestAssertException("No free ground for the animal quest's place");
 }
 /** Posts the card for the next kind and builds its place on the test's own mound. */
 private static CompoundTag card(GameTestHelper h,Town t,String template,long now){return card(h,t,template,now,16);}
 private static CompoundTag card(GameTestHelper h,Town t,String template,long now,int r){
  var q=AnimalQuests.post(t.l,t.e,now);
  h.assertTrue(q!=null&&q.getString("template").equals(template)&&q.getBoolean("pending"),"The yard asks for "+template+": "+q);
  h.assertTrue(AnimalQuests.materialize(t.l,t.e,q.getUUID("id"),mound(t,r),now),"The "+template+" place is built");
  return quest(t,q.getUUID("id"));
 }
 private static CompoundTag site(Town t,CompoundTag q){return QuestSites.site(t.l,Quests.root(q));}
 private static List<Animal> animals(Town t,CompoundTag q,String role){
  var s=site(t,q);var out=new ArrayList<Animal>();
  for(var x:s.getList("animals",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(x);if(s.getCompound("roles").getString(id.toString()).equals(role)&&t.l.getEntity(id) instanceof Animal a)out.add(a);}
  return out;
 }
 private static void step(Town t,UUID id,ServerPlayer p,long now){AnimalQuests.step(t.l,t.e,quest(t,id),p,now);}
 private static void to(Animal a,BlockPos at){a.setNoAi(true);a.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);}
 private static void clean(Town t,CompoundTag q){var s=site(t,q);if(s==null)return;
  for(var x:s.getList("animals",Tag.TAG_INT_ARRAY)){var e=t.l.getEntity(NbtUtils.loadUUID(x));if(e!=null)e.discard();}
  for(var x:s.getList("mobs",Tag.TAG_INT_ARRAY)){var e=t.l.getEntity(NbtUtils.loadUUID(x));if(e!=null)e.discard();}}

 @GameTest(template="empty",timeoutTicks=200) public static void theYardAsksForItsFirstKindOneCardAtATime(GameTestHelper h){
  var l=h.getLevel();AnimalUnlocks.migrateWorld(l.getServer());
  var bare=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(bare,l.dimension().location().toString(),h.absolutePos(new BlockPos(1,2,1)));SettlementData.get(l.getServer()).add(e);
  h.assertTrue(AnimalQuests.post(l,e,1200)==null,"A village without a yard asks for no animal");
  var t=town(h,1);
  var q=AnimalQuests.post(t.l,t.e,1200);
  h.assertTrue(q!=null&&q.getString("template").equals(AnimalQuests.FLOCK)&&q.getString("animal").equals("sheep")&&q.contains("site"),"A level-I yard asks for sheep first, with a place on the chart: "+q);
  h.assertTrue(AnimalQuests.post(t.l,t.e,2400)==null,"One animal card at a time");
  h.assertTrue(!AnimalUnlocks.unlocked(t.l,t.e,"sheep")&&!AnimalYard.kinds(t.l,t.e).contains("cow"),"Sheep are locked and a level-I yard keeps no cows");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aVillageThatKeptAnimalsBeforeTheRuleKeepsThem(GameTestHelper h){
  var t=town(h,3);AnimalUnlocks.markLegacy(t.l,t.e);
  var sheep=EntityType.SHEEP.create(t.l);to(sheep,t.pen("sheep"));sheep.getPersistentData().putUUID(LivestockGoal.OWNER,t.s.id());t.l.addFreshEntity(sheep);
  h.assertTrue(AnimalUnlocks.unlocked(t.l,t.e,"cow")&&AnimalQuests.post(t.l,t.e,1200)==null,"Nothing is locked or asked for before the yard's own ground was looked at");
  // The yard's whole ground and its animals must be in the world before its kinds are read: the test keeps those chunks loaded.
  var box=LivestockPens.yardBox(t.e,t.yard).inflate(2);var chunks=new ArrayList<long[]>();
  for(int x=(int)Math.floor(box.minX)>>4;x<=(int)Math.floor(box.maxX)>>4;x++)for(int z=(int)Math.floor(box.minZ)>>4;z<=(int)Math.floor(box.maxZ)>>4;z++){t.l.setChunkForced(x,z,true);chunks.add(new long[]{x,z});}
  h.succeedWhen(()->{
  AnimalQuests.tick(t.l,t.e,1220);
   h.assertTrue(AnimalUnlocks.unlocked(t.l,t.e,"sheep")&&AnimalUnlocks.source(t.l,t.e,"sheep").equals("legacy")&&!AnimalUnlocks.unlocked(t.l,t.e,"cow"),"The sheep it kept stay its own; the rest is locked: sheep="+AnimalUnlocks.source(t.l,t.e,"sheep")+" cow="+AnimalUnlocks.unlocked(t.l,t.e,"cow")+" present="+AnimalYard.present(t.l,t.e)+" alive="+sheep.isAlive());
   var q=AnimalQuests.post(t.l,t.e,2400);
   h.assertTrue(q!=null&&q.getString("template").equals(AnimalQuests.SINKHOLE_T),"The next card asks for cows: "+q);
   for(var c:chunks)t.l.setChunkForced((int)c[0],(int)c[1],false);
  sheep.discard();});
 }
 @GameTest(template="empty",timeoutTicks=400) public static void theStolenFlockIsFreedFromTheRustlersAndCountsInThePen(GameTestHelper h){
  var t=town(h,1);var q=card(h,t,AnimalQuests.FLOCK,1200);var id=q.getUUID("id");var p=player(t,"Drover",0);
  var s=site(t,q);var gate=BlockPos.of(s.getLong("gate"));
  h.assertTrue(s.getList("animals",Tag.TAG_INT_ARRAY).size()==Quests.setting(AnimalQuests.FLOCK,"flock")&&animals(t,q,"bell").size()==1&&animals(t,q,"lamb").get(0).isBaby(),"The camp's pen holds the stolen flock: the bellwether, the ewe, her lamb and a ram");
  h.assertTrue(s.getList("mobs",Tag.TAG_INT_ARRAY).size()==Quests.setting(AnimalQuests.FLOCK,"fighters"),"The band of rustlers stands in its camp");
  h.assertTrue(!t.l.getBlockState(gate).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN),"The pen's gate is shut");
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"The card is taken");
  p.setPos(gate.getX()+.5,gate.getY(),gate.getZ()+4.5);
  step(t,id,p,1300);
  h.assertTrue(quest(t,id).getString("status").equals("bandits")&&quest(t,id).getInt("status_left")==Quests.setting(AnimalQuests.FLOCK,"fighters"),"The board counts the band: "+quest(t,id).getString("status"));
  for(var x:site(t,q).getList("mobs",Tag.TAG_INT_ARRAY)){var w=t.l.getEntity(NbtUtils.loadUUID(x));if(w!=null)w.discard();}
  step(t,id,p,1320);
  h.assertTrue(site(t,q).getBoolean("band_down")&&quest(t,id).getString("status").equals("gate"),"The band beaten, the pen waits to be opened: "+quest(t,id).getString("status"));
  t.l.setBlock(gate,t.l.getBlockState(gate).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN,true),3);
  step(t,id,p,1340);
  h.assertTrue(quest(t,id).getString("status").equals("drive"),"The gate open, the flock is free");
  // Only a quest sheep let go in a sheep pen counts: a wild one does not, nor one still on a lead.
  var bell=animals(t,q,"bell").get(0);var ewe=animals(t,q,"ewe").get(0);var lamb=animals(t,q,"lamb").get(0);
  var wild=EntityType.SHEEP.create(t.l);to(wild,t.pen("sheep"));t.l.addFreshEntity(wild);
  to(bell,t.pen("sheep"));bell.setLeashedTo(p,true);to(ewe,t.pen("sheep").east());
  step(t,id,p,1360);
  h.assertTrue(quest(t,id).getInt("progress")==1&&!AnimalUnlocks.unlocked(t.l,t.e,"sheep"),"The ewe counts; the wild sheep and the bellwether on a lead do not: "+quest(t,id).getInt("progress"));
  long reward=quest(t,id).getLong("reputation");to(lamb,t.pen("sheep").north());
  step(t,id,p,1370);
  h.assertTrue(quest(t,id).getInt("progress")==2&&quest(t,id).getLong("reputation")==reward+Quests.setting(AnimalQuests.FLOCK,"lamb_bonus"),"The lamb that came with its mother counts and adds to the reward: progress "+quest(t,id).getInt("progress")+" reputation "+quest(t,id).getLong("reputation")+" was "+reward+" lamb at "+lamb.blockPosition().toShortString()+" in pen "+AnimalYard.inPen(t.l,t.e,lamb,"sheep"));
  h.assertTrue(AnimalUnlocks.unlocked(t.l,t.e,"sheep")&&AnimalUnlocks.source(t.l,t.e,"sheep").equals("quest"),"The pair opens sheep for the village");
  bell.dropLeash(true,false);
  h.assertTrue(LivestockGoal.owned(ewe,t.s.id())&&!ewe.getPersistentData().hasUUID(AnimalSites.TAG),"The ewe is the keeper's now");
  h.assertTrue(Quests.complete(p,t.s.id(),id)&&quest(t,id).getString("state").equals(Quests.DONE),"The card is done and paid");
  wild.discard();clean(t,q);h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aStuckCowRefusesTheRopeUntilAWayOutExists(GameTestHelper h){
  var t=town(h,2);AnimalUnlocks.unlock(t.l,t.e,"sheep","quest",null);
  var q=card(h,t,AnimalQuests.SINKHOLE_T,1200);var id=q.getUUID("id");var p=player(t,"Digger",0);
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"The card is taken");
  var s=site(t,q);var pit=BlockPos.of(s.getLong("pit"));var cow=animals(t,q,"cow").get(0);var calf=animals(t,q,"calf").get(0);
  p.setPos(pit.getX()+.5,pit.getY()+6,pit.getZ()+4.5);
  // The pair land on the mud first: only then does her pathfinding plan.
  h.runAfterDelay(30,()->{
   h.assertTrue(AnimalSites.inPit(site(t,q),cow.blockPosition())&&AnimalQuests.stuck(site(t,q),cow)&&calf.isBaby(),"The cow and her calf are down the pit and she has no way up");
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.LEAD,2));
   h.assertTrue(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,cow))&&!cow.isLeashed()&&p.getMainHandItem().getCount()==2,"A rope is refused while she is stuck");
   step(t,id,p,1300);h.assertTrue(quest(t,id).getString("status").equals("stuck"),"The board says she is stuck: "+quest(t,id).getString("status"));
   // A staircase of single steps up one side of the pit: a way her own pathfinding can walk.
   // She stands in the middle of the pit while the steps go up its side (a block set where she stands would bury her).
   cow.moveTo(pit.getX()+.5,pit.getY(),pit.getZ()+.5,cow.getYRot(),0);cow.getNavigation().stop();
   // It runs along the side away from the farmer's broken ladder, however the place is turned.
   var facing=net.minecraft.core.Direction.byName(site(t,q).getString("facing"));var right=facing.getClockWise();
   for(int i=0;i<5;i++)for(int y=0;y<=i;y++){int a=i-2,side=-2;
    t.l.setBlock(pit.offset(facing.getStepX()*a+right.getStepX()*side,y,facing.getStepZ()*a+right.getStepZ()*side),Blocks.DIRT.defaultBlockState(),3);}});
  h.runAfterDelay(60,()->{
   var rows=new StringBuilder();
   for(int dz=-3;dz<=3;dz++){rows.append(" |");for(int dx=-3;dx<=3;dx++){int top=-99;for(int y=6;y>=-1;y--)if(!t.l.getBlockState(pit.offset(dx,y,dz)).getCollisionShape(t.l,pit.offset(dx,y,dz)).isEmpty()){top=y;break;}rows.append(top).append(',');}}
   h.assertTrue(!AnimalQuests.stuck(site(t,q),cow),"With the steps built she has a way up (cow at "+cow.blockPosition().toShortString()+" pit "+pit.toShortString()+" facing "+site(t,q).getString("facing")+" tops"+rows+")");
   step(t,id,p,1320);h.assertTrue(quest(t,id).getString("status").equals("free")&&site(t,q).getBoolean("freed"),"The board says she is free: "+quest(t,id).getString("status"));
   h.assertTrue(!MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,cow)),"Now the rope is not refused");
   // The cow reaches the pen; the calf is lost before it does: what was brought is paid, the kind stays locked.
   to(cow,t.pen("cow"));step(t,id,p,1340);
   h.assertTrue(quest(t,id).getInt("progress")==1&&!AnimalUnlocks.unlocked(t.l,t.e,"cow"),"One of the pair is home: "+quest(t,id).getInt("progress"));
   calf.kill();step(t,id,p,1360);
   var closed=quest(t,id);
   h.assertTrue(closed.getString("state").equals(Quests.FAILED)&&closed.getString("reason").equals("animals_lost"),"With the calf dead the pair cannot be made: "+closed);
   h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())>0,"The cow really brought home is paid");
   h.assertTrue(AnimalQuests.post(t.l,t.e,1400)==null,"A failed kind waits before it is asked for again");
   clean(t,q);h.succeed();});
 }
 @GameTest(template="empty",timeoutTicks=400) public static void warmEggsCrackAtABlowAndHatchUnderThePennedHen(GameTestHelper h){
  var t=town(h,3);AnimalUnlocks.unlock(t.l,t.e,"sheep","quest",null);AnimalUnlocks.unlock(t.l,t.e,"cow","quest",null);
  var q=card(h,t,AnimalQuests.BROOD,1200);var id=q.getUUID("id");var p=player(t,"Henwife",(int)Quests.setting(AnimalQuests.BROOD,"trust"));
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"The card is taken");
  // The henwife is still far from the henhouse: the foxes come out only when somebody comes for their hen.
  p.setPos(t.center.getX()+40.5,t.center.getY()+1,t.center.getZ()+5.5);
  step(t,id,p,1300);var root=Quests.root(q);
  h.assertTrue(animals(t,q,"hen").size()==1,"The broody hen sits in the coop: "+site(t,q));
  h.assertTrue(site(t,q).getInt("eggs_out")==Quests.setting(AnimalQuests.BROOD,"eggs"),"Every warm egg is still whole");
  p.getInventory().add(AnimalQuests.warmEggs(root,3)[0]);
  // A fake player takes no damage of its own: the blow is the event the game raises for a real one.
  MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(p,t.l.damageSources().onFire(),1));
  h.assertTrue(p.getInventory().countItem(Items.EGG)==3,"A tick of burning is no blow: no egg cracks");
  MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(p,t.l.damageSources().fall(),1));
  MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingHurtEvent(p,t.l.damageSources().fall(),1));
  h.assertTrue(p.getInventory().countItem(Items.EGG)==2&&site(t,q).getInt("eggs_out")==Quests.setting(AnimalQuests.BROOD,"eggs")-1,"A fall cracks exactly one warm egg, and a second blow in the same second none: "+p.getInventory().countItem(Items.EGG));
  var egg=p.getInventory().items.stream().filter(x->x.is(Items.EGG)).findFirst().orElseThrow();p.setItemInHand(InteractionHand.MAIN_HAND,egg.copy());egg.setCount(0);
  h.assertTrue(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickItem(p,InteractionHand.MAIN_HAND))&&p.getMainHandItem().getCount()==2,"A warm egg is never thrown");
  // Laid on hay in the chicken pen, it waits for the hen.
  var hay=t.pen("chicken");t.l.setBlock(hay,Blocks.HAY_BLOCK.defaultBlockState(),3);
  var lay=new PlayerInteractEvent.RightClickBlock(p,InteractionHand.MAIN_HAND,hay,new BlockHitResult(Vec3.atCenterOf(hay),net.minecraft.core.Direction.UP,hay,false));
  MinecraftForge.EVENT_BUS.post(lay);
  h.assertTrue(site(t,q).getList("nest",Tag.TAG_COMPOUND).size()==1&&p.getMainHandItem().getCount()==1,"The egg lies in the nest");
  long hatch=(long)Quests.setting(AnimalQuests.BROOD,"hatch_ticks");
  step(t,id,p,1400+hatch);
  h.assertTrue(site(t,q).getList("nest",Tag.TAG_COMPOUND).size()==1&&quest(t,id).getInt("progress")==0,"Without the hen in the pen nothing hatches");
  var hen=animals(t,q,"hen").get(0);to(hen,t.pen("chicken").east(2));
  step(t,id,p,1420+hatch);
  h.assertTrue(quest(t,id).getInt("progress")==1&&!AnimalUnlocks.unlocked(t.l,t.e,"chicken"),"The hen is penned: one of four, not yet a pair");
  step(t,id,p,1440+hatch);
  h.assertTrue(quest(t,id).getInt("progress")==2&&site(t,q).getList("nest",Tag.TAG_COMPOUND).isEmpty(),"The hen is home and her egg hatches under her: "+quest(t,id).getInt("progress"));
  h.assertTrue(AnimalUnlocks.unlocked(t.l,t.e,"chicken"),"The hen and her first chick open chickens for the village");
  var chick=t.l.getEntitiesOfClass(Chicken.class,new AABB(hay).inflate(3),c->c.isBaby()&&LivestockGoal.owned(c,t.s.id()));
  h.assertTrue(chick.size()==1,"A real chick of the village stands on the nest");
  chick.forEach(c->c.discard());clean(t,q);h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void theSowIsRiddenHomeAndHerPigletsFollow(GameTestHelper h){
  var t=town(h,3);for(var k:List.of("sheep","cow","chicken"))AnimalUnlocks.unlock(t.l,t.e,k,"quest",null);
  var q=card(h,t,AnimalQuests.SOW,1200,32);var id=q.getUUID("id");var p=player(t,"Swineherd",(int)Quests.setting(AnimalQuests.SOW,"trust"));
  h.assertTrue(animals(t,q,"sow").isEmpty(),"The farmstead's sty is empty: she ran off");
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"The card is taken");
  var sty=BlockPos.of(site(t,q).getLong("sty"));p.setPos(sty.getX()+.5,sty.getY(),sty.getZ()+.5);
  step(t,id,p,1300);
  h.assertTrue(site(t,q).contains("wandered")&&animals(t,q,"piglet").size()==Quests.setting(AnimalQuests.SOW,"piglets"),"She is out there with her litter at the end of a trail: "+site(t,q)+" chunks "+t.l.hasChunkAt(sty.offset(24,0,0))+t.l.hasChunkAt(sty.offset(-24,0,0))+t.l.hasChunkAt(sty.offset(0,0,24))+t.l.hasChunkAt(sty.offset(0,0,-24))+" ground y "+t.l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,sty.getX()-24,sty.getZ())+" sty y "+sty.getY()+" card "+quest(t,id).getString("state")+" near "+p.blockPosition().distSqr(QuestSites.origin(site(t,q))));
  var sow=(net.minecraft.world.entity.animal.Pig)animals(t,q,"sow").get(0);
  h.assertTrue(sow.isSaddled()&&sow.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal() instanceof net.minecraft.world.entity.ai.goal.TemptGoal),"She is saddled and deaf to carrots in a hand");
  p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.LEAD,2));
  h.assertTrue(MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteract(p,InteractionHand.MAIN_HAND,sow))&&!sow.isLeashed(),"She will not go on a rope");
  // Ridden into the pig pen she is not counted yet; once her rider gets off, she is.
  to(sow,t.pen("pig"));p.setPos(sow.getX(),sow.getY(),sow.getZ());h.assertTrue(p.startRiding(sow,true),"The player gets on her");
  step(t,id,p,1320);
  // Ridden off without them, the board says her piglets are left behind (they stand where she left them).
  h.assertTrue(quest(t,id).getInt("progress")==0&&quest(t,id).getString("status").equals("piglets_behind")&&quest(t,id).getInt("status_left")==Quests.setting(AnimalQuests.SOW,"piglets"),"Ridden, she does not count, and her piglets are behind: "+quest(t,id).getString("status"));
  p.stopRiding();
  var piglets=animals(t,q,"piglet");to(piglets.get(0),t.pen("pig").east());to(piglets.get(1),t.pen("pig").west());
  step(t,id,p,1340);
  h.assertTrue(quest(t,id).getInt("progress")==3&&AnimalUnlocks.unlocked(t.l,t.e,"pig"),"The sow and two piglets in the pig pen open pigs for the village: "+quest(t,id).getInt("progress"));
  h.assertTrue(Quests.complete(p,t.s.id(),id)&&quest(t,id).getString("state").equals(Quests.DONE),"The card is done and paid");
  clean(t,q);h.succeed();
 }
}
