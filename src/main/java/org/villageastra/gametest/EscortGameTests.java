package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** QUEST-003 (AD-106): a companion walks, waits and rows by itself, never moved by a shortcut. It waits with a reason where there is no way,
 *  climbs where there is one, lands from a boat when its player is ashore, takes a seat in its player's boat or an empty one, refuses a swim too
 *  long and rows instead, swims a short one, follows its player through the portal they used and through no other, waits when its player was
 *  taken away without one, says it is wounded and is bandaged, waits when told to, keeps all of it through a save, and the board shows it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EscortGameTests {
 private static final BlockState STONE=Blocks.STONE.defaultBlockState(),AIR=Blocks.AIR.defaultBlockState(),WATER=Blocks.WATER.defaultBlockState();
 // ---------------------------------------------------------------- fixtures
 private static void fill(GameTestHelper h,int x0,int y0,int z0,int x1,int y1,int z1,BlockState s){
  for(int x=Math.min(x0,x1);x<=Math.max(x0,x1);x++)for(int y=Math.min(y0,y1);y<=Math.max(y0,y1);y++)for(int z=Math.min(z0,z1);z<=Math.max(z0,z1);z++)h.setBlock(new BlockPos(x,y,z),s);
 }
 /** A flat stone floor, feet at y4. */
 private static void pad(GameTestHelper h){fill(h,1,3,2,29,3,14,STONE);fill(h,1,4,2,29,13,14,AIR);}
 /** A walled lake: land to the west up to {@code west}, water two deep from there to {@code east}, land again beyond; feet on land at y5.
  *  The walls stand too high to walk round. */
 private static void lake(GameTestHelper h,int west,int east){
  fill(h,1,2,2,29,12,14,AIR);fill(h,1,2,2,29,2,14,STONE);
  for(int y=2;y<=6;y++){fill(h,1,y,2,29,y,2,STONE);fill(h,1,y,14,29,y,14,STONE);fill(h,1,y,2,1,y,14,STONE);fill(h,29,y,2,29,y,14,STONE);}
  if(west>=2)fill(h,2,3,3,west,4,13,STONE);
  fill(h,west+1,3,3,east,4,13,WATER);
  if(east+1<=28)fill(h,east+1,3,3,28,4,13,STONE);
 }
 private static ServerPlayer owner(GameTestHelper h,double x,double y,double z){
  var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"EscortOwner"));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  var a=h.absoluteVec(new Vec3(x,y,z));p.setPos(a.x,a.y,a.z);EscortGoal.TEST_OWNERS.put(p.getUUID(),p);return p;
 }
 /** The same player standing in another world, as the goal will find them after a crossing. */
 private static ServerPlayer elsewhere(ServerPlayer o,ServerLevel level,Vec3 at){
  var p=FakePlayerFactory.get(level,o.getGameProfile());p.setPos(at.x,at.y,at.z);EscortGoal.TEST_OWNERS.put(o.getUUID(),p);return p;
 }
 private static ResidentEntity companion(GameTestHelper h,ServerPlayer o,double x,double y,double z){
  var n=VillageAstra.RESIDENT.get().create(h.getLevel());var a=h.absoluteVec(new Vec3(x,y,z));n.moveTo(a.x,a.y,a.z,0,0);n.escort(o.getUUID());h.getLevel().addFreshEntity(n);return n;
 }
 private static Boat boat(GameTestHelper h,double x,double y,double z){var a=h.absoluteVec(new Vec3(x,y,z));var b=new Boat(h.getLevel(),a.x,a.y,a.z);h.getLevel().addFreshEntity(b);return b;}
 private static void done(ServerPlayer o,Entity... entities){EscortGoal.TEST_OWNERS.remove(o.getUUID());o.stopRiding();for(var e:entities)if(e!=null){e.stopRiding();e.discard();}}
 /** Every step the companion takes, tick by tick: a step bigger than a walk is a shortcut, not a walk. */
 private static final class Track{Vec3 last,lastHere;Entity vehicle;ResourceKey<Level> dim;double jump,mount,maxX=-1e9;boolean swam,waited,needsBoat;int sinceMount=99;}
 private static ResidentEntity find(GameTestHelper h,UUID id){
  if(h.getLevel().getEntity(id) instanceof ResidentEntity r)return r;var nether=h.getLevel().getServer().getLevel(Level.NETHER);
  return nether!=null&&nether.getEntity(id) instanceof ResidentEntity r2?r2:null;
 }
 private static Track track(GameTestHelper h,UUID id){
  var t=new Track();
  h.onEachTick(()->{var n=find(h,id);if(n==null)return;var pos=n.position();
   // A rider is set on its seat by the boat's own tick, one tick after it got in or out: those steps belong to the boarding, not to a walk.
   if(t.vehicle!=n.getVehicle())t.sinceMount=0;else t.sinceMount++;
   if(t.last!=null&&t.dim==n.level().dimension()){double step=pos.distanceTo(t.last);if(t.sinceMount>2)t.jump=Math.max(t.jump,step);else t.mount=Math.max(t.mount,step);}
   t.last=pos;t.vehicle=n.getVehicle();t.dim=n.level().dimension();
   t.swam|=n.isInWater()&&!n.isPassenger();t.waited|="waiting".equals(n.escortState());t.needsBoat|="needs_boat".equals(n.escortReason());
   t.maxX=Math.max(t.maxX,pos.x);if(n.level().dimension()==Level.OVERWORLD)t.lastHere=pos;});
  return t;
 }
 private static String state(ResidentEntity n){return n.escortState()+"/"+n.escortReason();}
 // ---------------------------------------------------------------- ways and dead ends
 @GameTest(template="empty",timeoutTicks=200) public static void anInitialApproachMustReachTheNearRadiusBeforeWaiting(GameTestHelper h){
  pad(h);var o=owner(h,10.5,4,6.5);var n=companion(h,o,6.5,4,6.5);
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.distanceTo(o)<3,"Starting four blocks away must approach before applying the idle margin"))
   .thenExecute(()->done(o,n)).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=700) public static void aCompanionOnAPillarWaitsThenWalksDownWhenThereIsAWay(GameTestHelper h){
  pad(h);fill(h,6,4,6,6,11,6,STONE);
  var o=owner(h,18.5,4,6.5);var n=companion(h,o,6.5,12,6.5);var t=track(h,n.getUUID());var top=h.absoluteVec(new Vec3(6.5,12,6.5));
  h.startSequence().thenExecuteAfter(200,()->{
    h.assertTrue(state(n).equals("waiting/no_path"),"With no way down it waits and says so: "+state(n));
    h.assertTrue(n.position().distanceTo(top)<0.6&&t.jump<0.6,"Nothing carried it down: "+n.position()+" largest step "+t.jump);
    for(int i=0;i<=6;i++)fill(h,7+i,4,6,7+i,10-i,6,STONE);})
   .thenWaitUntil(()->h.assertTrue(n.distanceTo(o)<3&&n.escortState().equals("following"),"Once there is a stair it walks down to its player: "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->{h.assertTrue(t.jump<1.2,"On its own legs: "+t.jump);done(o,n);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=500) public static void aClimbingCompanionNeverReportsWaiting(GameTestHelper h){
  pad(h);for(int i=0;i<=5;i++)fill(h,8+i,4,6,8+i,4+i,6,STONE);fill(h,14,4,5,17,9,7,STONE);
  var o=owner(h,16.5,10,6.5);var n=companion(h,o,3.5,4,6.5);var t=track(h,n.getUUID());
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.distanceTo(o)<3&&n.escortState().equals("following"),"It climbs the steps to its player: "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->{h.assertTrue(!t.waited,"A way that exists is never reported as waiting, not even in the air between two steps");h.assertTrue(t.jump<1.2,"On its own legs: "+t.jump);done(o,n);}).thenSucceed();
 }
 // ---------------------------------------------------------------- water and boats
 @GameTest(template="empty",timeoutTicks=500) public static void aCompanionInABoatLandsWhenTheOwnerIsAshore(GameTestHelper h){
  lake(h,1,17);var o=owner(h,24.5,5,8.5);var b=boat(h,8.5,4.5,8.5);var n=companion(h,o,8.5,5,8.5);
  h.assertTrue(n.startRiding(b,true),"The companion sits in the boat");var start=b.getX();var t=track(h,n.getUUID());
  h.startSequence().thenExecuteAfter(5,()->h.assertTrue(n.isPassenger()&&n.escortState().equals("boat"),"It rows: "+state(n)))
   .thenWaitUntil(()->h.assertTrue(!n.isPassenger()&&n.distanceTo(o)<3&&n.escortState().equals("following"),"It lands and walks to its player: "+n.isPassenger()+" "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->{h.assertTrue(n.getY()>=h.absoluteVec(new Vec3(0,5,0)).y-0.05&&b.getPassengers().isEmpty()&&b.getX()-start>=5,"It rowed across and stepped onto the bank: "+n.getY()+" boat moved "+(b.getX()-start));
    h.assertTrue(t.jump<1.2&&t.mount<2.0,"No shortcut: "+t.jump+" "+t.mount);done(o,n,b);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aCompanionTakesTheFreeSeatInItsOwnersBoat(GameTestHelper h){
  lake(h,1,17);var o=owner(h,15.5,5,8.5);var b=boat(h,15.5,4.5,8.5);h.assertTrue(o.startRiding(b,true),"The player sits in the boat");
  var n=companion(h,o,23.5,5,8.5);var at=new Vec3[1];
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.getVehicle()==b&&n.escortState().equals("boat"),"It takes the free seat: "+state(n)+" riding "+n.getVehicle()))
   .thenExecute(()->{h.assertTrue(b.getPassengers().size()==2&&b.getFirstPassenger()==o,"The player keeps the oars");at[0]=b.position();})
   .thenExecuteAfter(40,()->{h.assertTrue(n.getVehicle()==b&&b.position().distanceTo(at[0])<0.3,"It never steers its player's boat: moved "+b.position().distanceTo(at[0]));done(o,n,b);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aCompanionBoardsAnEmptyBoatAndRowsToItsOwnerOnTheWater(GameTestHelper h){
  lake(h,1,17);var o=owner(h,5.5,3.5,8.5);var b=boat(h,16.6,4.5,8.5);var n=companion(h,o,19.6,5,8.5);var t=track(h,n.getUUID());var start=b.getX();
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.getVehicle()==b&&n.escortState().equals("boat"),"It boards the empty boat: "+state(n)))
   .thenWaitUntil(()->h.assertTrue(start-b.getX()>=6,"And rows toward its player on the water: "+(start-b.getX())))
   .thenExecute(()->{h.assertTrue(!t.swam&&t.jump<1.2&&t.mount<3.5,"Dry and on its own: swam "+t.swam+" "+t.jump+" "+t.mount);done(o,n,b);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aCompanionTakesASeparateBoatWhenItsOwnersIsFull(GameTestHelper h){
  lake(h,1,17);var o=owner(h,8.5,5,8.5);var full=boat(h,8.5,4.5,8.5);h.assertTrue(o.startRiding(full,true),"The player sits in the boat");
  var other=VillageAstra.RESIDENT.get().create(h.getLevel());var at=h.absoluteVec(new Vec3(8.5,5,8.5));other.moveTo(at.x,at.y,at.z,0,0);other.setNoAi(true);h.getLevel().addFreshEntity(other);
  h.assertTrue(other.startRiding(full,true)&&full.getPassengers().size()==2,"And somebody else takes the second seat");
  var spare=boat(h,16.6,4.5,8.5);var n=companion(h,o,19.6,5,8.5);var t=track(h,n.getUUID());var start=spare.getX();
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.getVehicle()==spare&&n.escortState().equals("boat"),"The player's boat is full: it takes the other one: "+state(n)+" riding "+n.getVehicle()))
   .thenWaitUntil(()->h.assertTrue(start-spare.getX()>=3,"And really rows after its player: "+(start-spare.getX())))
   .thenExecute(()->{h.assertTrue(!t.swam&&n.getVehicle()!=full,"Never in the water, never in the full boat");done(o,n,spare,full,other);}).thenSucceed();
 }
 // A nearby full-village fixture in defaultBatch laid a gravel road through the far end of this lake (full run 2026-09-27).
 @GameTest(template="empty",batch="escort_long_swim",timeoutTicks=800) public static void aLongSwimIsRefusedUntilABoatIsProvided(GameTestHelper h){
  lake(h,5,24);var o=owner(h,27.5,5,8.5);var n=companion(h,o,3.5,5,8.5);var t=track(h,n.getUUID());var bank=h.absoluteVec(new Vec3(6,5,8.5)).x;
  var boat=new Boat[1];var start=new double[1];
  var furthest=new double[]{-Double.MAX_VALUE};h.onEachTick(()->{if(boat[0]!=null)furthest[0]=Math.max(furthest[0],boat[0].getX());});
  h.startSequence().thenExecuteAfter(60,()->{
    h.assertTrue(state(n).equals("waiting/needs_boat"),"Nineteen blocks of water is no swim: "+state(n));
    h.assertTrue(!t.swam&&t.maxX<bank-0.2,"It does not step into the water: swam "+t.swam+" x "+t.maxX);
    boat[0]=boat(h,6.7,4.5,8.5);start[0]=boat[0].getX();})
   .thenWaitUntil(()->h.assertTrue(n.getVehicle()==boat[0]&&n.escortState().equals("boat")&&n.escortReason().isEmpty(),"Given a boat it takes it: "+state(n)))
   .thenWaitUntil(()->h.assertTrue(!n.isPassenger()&&n.distanceTo(o)<3&&n.escortState().equals("following"),"It rows across, lands and walks to its player: "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->{for(int x=6;x<=24;x++)h.assertTrue(h.getLevel().getFluidState(h.absolutePos(new BlockPos(x,4,8))).is(net.minecraft.tags.FluidTags.WATER),"The lake fixture was not overwritten by another test at x="+x);
    com.mojang.logging.LogUtils.getLogger().info("ASTRA_ESCORT_LONG_SWIM final={} max={} bank={} rider={} mount={} jump={}",boat[0].getX()-start[0],furthest[0]-start[0],bank-start[0],n.getX()-start[0],t.mount,t.jump);h.assertTrue(boat[0].getX()-start[0]>=15&&t.jump<1.2,"The boat really crossed: "+(boat[0].getX()-start[0])+" largest step "+t.jump);done(o,n,boat[0]);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=500) public static void aShortSwimIsStillSwum(GameTestHelper h){
  lake(h,9,12);var o=owner(h,22.5,5,8.5);var n=companion(h,o,4.5,5,8.5);var t=track(h,n.getUUID());
  h.startSequence().thenWaitUntil(()->h.assertTrue(n.distanceTo(o)<3&&n.escortState().equals("following"),"Three blocks of water are swum: "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->{h.assertTrue(!t.needsBoat&&t.jump<1.2,"No boat was asked for a short swim: "+t.needsBoat+" "+t.jump);done(o,n);}).thenSucceed();
 }
 // ---------------------------------------------------------------- portals
 @GameTest(template="empty",timeoutTicks=800) public static void anOwnerWhoWentThroughAPortalIsFollowedThroughIt(GameTestHelper h){
  pad(h);var l=h.getLevel();var nether=l.getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"The test world has a Nether");
  SettlementGameTests.portal(l,h.absolutePos(new BlockPos(18,3,8)));var cell=h.absolutePos(new BlockPos(19,4,8));
  var exit=new BlockPos(Math.floorDiv(cell.getX(),8),70,Math.floorDiv(cell.getZ(),8));nether.getChunk(exit);
  for(int dx=-3;dx<=4;dx++)for(int dy=-1;dy<=5;dy++)for(int dz=-3;dz<=6;dz++)nether.setBlockAndUpdate(exit.offset(dx,dy,dz),Blocks.OBSIDIAN.defaultBlockState());
  for(int dx=-2;dx<=3;dx++)for(int dy=0;dy<=4;dy++)for(int dz=-2;dz<=5;dz++)nether.setBlockAndUpdate(exit.offset(dx,dy,dz),Blocks.AIR.defaultBlockState());
  SettlementGameTests.portal(nether,exit.offset(-1,-1,0));
  var o=owner(h,19.5,4,5.5);var n=companion(h,o,4.5,4,8.5);var id=n.getUUID();var t=track(h,id);var away=new ServerPlayer[1];var start=new double[1];
  h.startSequence().thenExecuteAfter(10,()->{
    o.setPos(cell.getX()+.5,cell.getY(),cell.getZ()+.5);MinecraftForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(o,Level.NETHER));
    h.assertTrue(n.portalCell()!=null&&n.portalCell().pos().equals(cell)&&n.escortState().equals("dimension"),"The companion knows which portal its player took: "+n.portalCell()+" "+state(n));
    away[0]=elsewhere(o,nether,Vec3.atBottomCenterOf(exit).add(0,0,5));start[0]=n.position().distanceTo(Vec3.atCenterOf(cell));})
   .thenWaitUntil(()->{var c=find(h,id);h.assertTrue(c!=null&&c.level()==nether&&l.getEntity(id)==null,"It went through the portal after its player: "+(c==null?"gone":c.level().dimension().location()));})
   .thenWaitUntil(()->{var c=find(h,id);h.assertTrue(c!=null&&c.level()==nether,"The companion is present in the destination while it approaches its player");h.assertTrue(c.distanceTo(away[0])<3&&c.escortState().equals("following")&&c.portalCell()==null&&o.getUUID().equals(c.escortPlayer()),"And follows its player there: "+c.distanceTo(away[0])+" "+state(c));})
   .thenExecute(()->{h.assertTrue(t.lastHere!=null&&t.lastHere.distanceTo(Vec3.atBottomCenterOf(cell))<1.6&&t.jump<1.2,"It walked into the portal itself: last seen here "+t.lastHere+" largest step "+t.jump);
    var c=find(h,id);done(o,c);done(away[0]);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void aCompanionCrossesNoPortalWhileItsOwnerIsHere(GameTestHelper h){
  pad(h);var l=h.getLevel();SettlementGameTests.portal(l,h.absolutePos(new BlockPos(10,3,8)));
  var o=owner(h,13.5,4,8.5);var n=companion(h,o,11.5,4,8.5);var id=n.getUUID();
  h.startSequence().thenExecuteAfter(40,()->{
    h.assertTrue(l.getEntity(id)!=null&&!n.isOnPortalCooldown(),"Standing in a portal beside its player it stays, and is not left on a long cooldown: cooldown "+n.isOnPortalCooldown());
    done(o,n);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void anOwnerTakenAwayWithoutAPortalIsWaitedFor(GameTestHelper h){
  pad(h);var l=h.getLevel();var nether=l.getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"The test world has a Nether");
  var o=owner(h,10.5,4,8.5);var n=companion(h,o,6.5,4,8.5);var t=track(h,n.getUUID());var at=new Vec3[1];var away=new ServerPlayer[1];
  h.startSequence().thenExecuteAfter(20,()->{MinecraftForge.EVENT_BUS.post(new EntityTravelToDimensionEvent(o,Level.NETHER));
    away[0]=elsewhere(o,nether,new Vec3(o.getX()/8,100,o.getZ()/8));at[0]=n.position();t.jump=0;})
   .thenExecuteAfter(60,()->{h.assertTrue(state(n).equals("waiting/other_dimension")&&n.portalCell()==null&&n.level()==l,"A player taken away without a portal is waited for: "+state(n));
    h.assertTrue(n.position().distanceTo(at[0])<1.0&&t.jump<0.6,"It stays where it was: moved "+n.position().distanceTo(at[0]));done(o,n);done(away[0]);}).thenSucceed();
 }
 // ---------------------------------------------------------------- wounds, orders, saves
 @GameTest(template="empty",timeoutTicks=300) public static void aWoundedCompanionSaysSoAndItsOwnersBandageHealsIt(GameTestHelper h){
  pad(h);var o=owner(h,10.5,4,8.5);var n=companion(h,o,8.5,4,8.5);var before=new float[1];
  h.startSequence().thenExecuteAfter(10,()->n.hurt(h.getLevel().damageSources().generic(),14))
   .thenExecuteAfter(30,()->{
    h.assertTrue(n.escortState().equals("wounded")&&n.escortWarned()&&n.distanceTo(o)<4,"Hurt, it says so and keeps to its player: "+state(n)+" "+n.distanceTo(o));
    h.assertTrue(n.saveWithoutId(new CompoundTag()).getString("AstraEscortState").equals("wounded"),"The wound is kept in the save");
    var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Stranger"));stranger.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),1));
    h.assertTrue(Adventures.treatWound(stranger,n,InteractionHand.MAIN_HAND).isEmpty()&&stranger.getMainHandItem().getCount()==1,"Only its own player treats it");
    before[0]=n.getHealth();o.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),2));
    h.assertTrue(n.interact(o,InteractionHand.MAIN_HAND).consumesAction()&&o.getMainHandItem().getCount()==1,"Its player's bandage is used on it");
    h.assertTrue(n.getHealth()>before[0]&&n.getHealth()>n.getMaxHealth()/2,"The bandage heals it: "+n.getHealth());})
   .thenExecuteAfter(20,()->{h.assertTrue(n.escortState().equals("following")&&!n.escortWarned()&&Adventures.treatWound(o,n,InteractionHand.MAIN_HAND).isEmpty()&&o.getMainHandItem().getCount()==1,
     "Healed, it follows again and needs no more bandages: "+state(n));done(o,n);}).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void anOrderedCompanionStaysUntilCalled(GameTestHelper h){
  pad(h);var o=owner(h,6.5,4,8.5);var n=companion(h,o,8.5,4,8.5);var at=new Vec3[1];
  h.startSequence().thenExecuteAfter(5,()->{n.order(true);var far=h.absoluteVec(new Vec3(22.5,4,8.5));o.setPos(far.x,far.y,far.z);at[0]=n.position();})
   .thenExecuteAfter(60,()->{h.assertTrue(state(n).equals("waiting/ordered")&&n.position().distanceTo(at[0])<0.5&&o.getUUID().equals(n.escortPlayer()),"Told to wait, it stays and keeps its player: "+state(n));n.order(false);})
   .thenWaitUntil(()->h.assertTrue(n.distanceTo(o)<3&&n.escortState().equals("following"),"Called, it comes: "+n.distanceTo(o)+" "+state(n)))
   .thenExecute(()->done(o,n)).thenSucceed();
 }
 @GameTest(template="empty",timeoutTicks=40) public static void escortStateReasonAndPortalSurviveASave(GameTestHelper h){
  var l=h.getLevel();var owner=UUID.randomUUID();
  for(var s:EscortStates.STATES)for(var reason:List.of("","no_path","ordered")){
   var n=VillageAstra.RESIDENT.get().create(l);n.escort(owner);n.setEscort(s,reason);n.recordPortal(new BlockPos(12,70,-5),Level.NETHER,Level.OVERWORLD,1234);n.escortWarned(true);
   var tag=n.saveWithoutId(new CompoundTag());var copy=VillageAstra.RESIDENT.get().create(l);copy.load(tag);
   h.assertTrue(copy.escortState().equals(s)&&copy.escortReason().equals(reason)&&copy.escortWarned()&&owner.equals(copy.escortPlayer()),"Kept through a save: "+s+"/"+reason+" -> "+copy.escortState()+"/"+copy.escortReason());
   h.assertTrue(copy.portalCell()!=null&&copy.portalCell().pos().equals(new BlockPos(12,70,-5))&&copy.portalCell().dimension().equals(Level.NETHER)&&copy.portalUntil()==1234,"The portal note too: "+copy.portalCell());
   tag.remove("AstraEscortReason");tag.remove("AstraEscortPortal");tag.remove("AstraEscortWarned");var old=VillageAstra.RESIDENT.get().create(l);old.load(tag);
   h.assertTrue(old.escortState().equals(s)&&old.escortReason().isEmpty()&&old.portalCell()==null&&!old.escortWarned(),"An older save loads cleanly: "+old.escortState());}
  var odd=VillageAstra.RESIDENT.get().create(l);odd.escort(owner);var tag=odd.saveWithoutId(new CompoundTag());tag.putString("AstraEscortState","flying");
  var copy=VillageAstra.RESIDENT.get().create(l);copy.load(tag);h.assertTrue(copy.escortState().equals("waiting"),"An unknown state is a wait: "+copy.escortState());
  h.succeed();
 }
 // ---------------------------------------------------------------- the board
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-5;x<25;x++)for(int z=-5;z<24;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,center,office);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theBoardShowsEachCompanionWithItsStateDangerAndBoat(GameTestHelper h){
  var t=town(h);var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),"BoardReader"));PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Quests.RESCUE));
  p.setPos(t.center.getX()+2,t.center.getY()+1,t.center.getZ()+2);
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,4));chest.setItem(1,new ItemStack(Items.BREAD,4));
  h.assertTrue(Expeditions.report(t.l,t.e,t.office,new BlockPos(t.center.getX()+18,t.center.getY()+1,t.center.getZ()+16),0,600)!=null,"A camp lead is reported");
  var q=Quests.postDistant(t.l,t.e,Quests.RESCUE,700);h.assertTrue(q!=null&&Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"A rescue is taken");
  var people=new ArrayList<ResidentEntity>();
  for(var raw:Camps.camp(t.l,q.getUUID("camp")).getList("travellers",Tag.TAG_COMPOUND))if(t.l.getEntity(((CompoundTag)raw).getUUID("id")) instanceof ResidentEntity npc){npc.setNoAi(true);npc.escort(p.getUUID());people.add(npc);}
  h.assertTrue(people.size()==3,"Three travellers");
  var t0=people.get(0);t0.setEscort("waiting","needs_boat");var pool=t0.blockPosition().offset(-6,-1,1);t.l.setBlock(pool,Blocks.WATER.defaultBlockState(),2);
  var free=new Boat(t.l,pool.getX()+.5,pool.getY()+.5,pool.getZ()+.5);t.l.addFreshEntity(free);
  var t1=people.get(1);var ride=new Boat(t.l,t1.getX(),t1.getY(),t1.getZ());t.l.addFreshEntity(ride);h.assertTrue(t1.startRiding(ride,true),"One is in a boat");t1.setEscort("boat","");
  var t2=people.get(2);t2.setHealth(6);t2.setEscort("wounded","");var spider=EntityType.SPIDER.create(t.l);spider.setNoAi(true);spider.moveTo(t2.getX()+3,t2.getY(),t2.getZ(),0,0);t.l.addFreshEntity(spider);
  var view=new CompoundTag();view.putUUID("village",t.s.id());Quests.addView(p,view);
  var row=view.getList("quests",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).filter(x->x.getUUID("id").equals(q.getUUID("id"))).findFirst().orElseThrow();
  var list=row.getList("people",Tag.TAG_COMPOUND);h.assertTrue(list.size()==3,"The board lists the three: "+list);
  var byId=new HashMap<UUID,CompoundTag>();for(var raw:list)byId.put(((CompoundTag)raw).getUUID("id"),(CompoundTag)raw);
  var c0=byId.get(t0.getUUID());h.assertTrue(c0.getString("state").equals("waiting")&&c0.getString("reason").equals("needs_boat")&&c0.getString("boat").equals("near")
   &&c0.getInt("away")==Adventures.away(p.blockPosition(),t0.blockPosition())&&!c0.getString("dir").isEmpty(),"The first waits for a boat, one is near, and the board says where: "+c0);
  h.assertTrue(byId.get(t1.getUUID()).getString("boat").equals("riding"),"The second is in a boat: "+byId.get(t1.getUUID()));
  var c2=byId.get(t2.getUUID());h.assertTrue(c2.getString("state").equals("wounded")&&c2.getInt("danger")>=1&&c2.getFloat("hp")==6,"The third is hurt with danger near: "+c2);
  var stranger=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),"Onlooker"));var other=new CompoundTag();other.putUUID("village",t.s.id());Quests.addView(stranger,other);
  h.assertTrue(other.getList("quests",Tag.TAG_COMPOUND).stream().noneMatch(x->((CompoundTag)x).contains("people")),"Nobody else sees them");
  Quests.companions(t.l,t.e,800);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getCompound("roster").getCompound(t1.getUUID().toString()).getString("state").equals("boat"),"The board keeps where each was last seen");
  t2.discard();var after=new CompoundTag();after.putUUID("village",t.s.id());Quests.addView(p,after);
  var unseen=after.getList("quests",Tag.TAG_COMPOUND).getCompound(0).getList("people",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).filter(x->x.getUUID("id").equals(t2.getUUID())).findFirst().orElseThrow();
  h.assertTrue(!unseen.getBoolean("live")&&unseen.getString("state").equals("wounded"),"One whose ground sleeps is shown as last seen: "+unseen);
  t0.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);Quests.companions(t.l,t.e,900);
  h.assertTrue(t0.escortState().equals("arrived")&&t0.escortPlayer()==null,"The first is home: "+state(t0));
  var home=new CompoundTag();home.putUUID("village",t.s.id());Quests.addView(p,home);
  var arrived=home.getList("quests",Tag.TAG_COMPOUND).getCompound(0).getList("people",Tag.TAG_COMPOUND).stream().map(x->(CompoundTag)x).filter(x->x.getUUID("id").equals(t0.getUUID())).findFirst().orElseThrow();
  h.assertTrue(arrived.getString("state").equals("arrived"),"And the board says so: "+arrived);
  // Its goal runs on: a goal ticked every tick must not turn a companion who is home into one who waits.
  t0.setNoAi(false);
  h.runAfterDelay(10,()->{h.assertTrue(t0.escortState().equals("arrived"),"Home stays home: "+state(t0));free.discard();ride.discard();spider.discard();h.succeed();});
 }
}
