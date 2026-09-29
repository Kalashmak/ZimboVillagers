package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.SettlementData;
import org.villageastra.server.TradeLedger;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaravanHorseGameTests {
 private record Fixture(TradeLadderGameTests.Town home,TradeLadderGameTests.Town far,Horse horse,CartEntity cart,Container stock,BlockPos start){}
 private static Fixture fixture(GameTestHelper h,int level){
  var a=TradeLadderGameTests.town(h,new BlockPos(6,3,6),level);var b=TradeLadderGameTests.town(h,new BlockPos(70,3,6),2);var l=a.l();
  var at=a.e().center().offset(-8,1,-8);
  for(int x=-5;x<=6;x++)for(int z=-5;z<=6;z++){var p=at.offset(x,0,z);int top=Math.max(p.getY()+4,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ()));l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);for(int y=p.getY();y<=top;y++)l.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);}
  for(var v:List.of(a,b))l.setBlock(LogisticsRoutes.position(v.e(),Workshops.hall(v.e())),VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var stock=(Container)l.getBlockEntity(LogisticsRoutes.position(a.e(),Workshops.hall(a.e())));for(int i=0;i<22;i++)stock.setItem(i,new ItemStack(Items.BREAD,64));
  var horse=EntityType.HORSE.create(l);horse.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);horse.setHealth(20);horse.setTamed(true);horse.setOwnerUUID(UUID.randomUUID());horse.moveTo(at.getX()+2.5,at.getY(),at.getZ()+2.5,0,0);l.addFreshEntity(horse);
  if(level==6)h.assertTrue(VillageHorses.enlist(l,a.e(),horse,horse.getOwnerUUID()),"Owner lends real horse to level VI yard");
  var cart=new CartEntity(l,at.offset(2,0,2),a.s().id());l.addFreshEntity(cart);return new Fixture(a,b,horse,cart,stock,at);
 }
 private static CompoundTag propose(Fixture f){Caravans.snapshot(f.home.l(),f.home.e(),1000);return Caravans.propose(f.home.l(),f.far.e(),f.home.e(),1000);}
 private static CompoundTag launch(Fixture f){var t=propose(f);if(t==null)throw new IllegalStateException("No horse proposal without worker");t.putString("state",Caravans.ACCEPTED);if(Caravans.secure(f.home.l(),t,1020)<=0)throw new IllegalStateException("No horse cargo");Caravans.dispatch(f.home.l(),t,1020);return t;}
 private static void clean(Fixture f){var l=f.home.l();for(var t:Caravans.contracts(l.getServer()))if(t.getUUID("source").equals(f.home.s().id())){t.putString("state",Caravans.CLOSED);t.putBoolean("needsEntity",false);Caravans.update(l.getServer(),t);}
  if(l.getEntity(f.horse.getUUID())!=null)l.getEntity(f.horse.getUUID()).discard();for(var c:l.getEntitiesOfClass(CartEntity.class,new net.minecraft.world.phys.AABB(f.home.e().center()).inflate(150),c->f.home.s().id().equals(c.settlement())))c.discard();
  for(var mob:l.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(f.home.e().center()).inflate(150),m->m instanceof ResidentEntity r&&f.home.s().id().equals(r.settlementId())||m instanceof net.minecraft.world.entity.animal.Wolf w&&f.home.s().id().equals(VillageWolves.village(w))))mob.discard();
  for(var v:List.of(f.home,f.far)){SettlementData.get(l.getServer()).remove(v.s().id());BuildingLevels.forgetBest(v.s().id());}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseSixthLevelTradesWithoutAnyResidentAndPaysOnce(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();h.assertTrue(f.home.s().residents().isEmpty(),"No worker exists");var t=launch(f);int n=t.getInt("secured");
   h.assertTrue(CaravanHorses.isHorse(t)&&t.getUUID("caravaneer").equals(f.horse.getUUID())&&n>0&&f.stock.countItem(Items.BREAD)==1408-n,"Real stock debited and real horse reserved");
   for(int i=0;i<150&&!t.getString("state").equals(Caravans.CLOSED);i++)Caravans.tick(l.getServer(),1040+i*20);
   h.assertTrue(t.getString("state").equals(Caravans.CLOSED)&&t.getInt("delivered")==n&&LogisticsRoutes.chest(l,f.far.e(),Workshops.hall(f.far.e())).countItem(Items.BREAD)==n,"Background delivery and return completed");
   long paid=TradeLedger.get(l.getServer()).treasury(f.home.s().id());Caravans.tick(l.getServer(),5000);h.assertTrue(paid>0&&TradeLedger.get(l.getServer()).treasury(f.home.s().id())==paid,"Paid only once");
   h.assertTrue(CaravanHorses.reserved(l.getServer(),f.horse.getUUID())&&t.getBoolean("needsEntity"),"Horse reserved until physically home");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseRequiresSixthLevelOwnerAdultAndRealFreeCart(GameTestHelper h){
  var f=fixture(h,5);try{var l=f.home.l();h.assertTrue(!VillageHorses.enlist(l,f.home.e(),f.horse,f.horse.getOwnerUUID())&&propose(f)==null,"Level V cannot replace worker with horse");h.succeed();}finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseOwnerCanReleaseOnlyAnIdleHealthyAdult(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();h.assertTrue(!VillageHorses.release(f.horse,UUID.randomUUID()),"Other player cannot reclaim");
   f.horse.setAge(-100);h.assertTrue(VillageHorses.free(l,f.home.e())==null,"Foal cannot pull");f.horse.setAge(0);f.horse.setHealth(1);h.assertTrue(VillageHorses.free(l,f.home.e())==null,"Badly wounded horse rests");f.horse.setHealth(f.horse.getMaxHealth());
   var t=propose(f);h.assertTrue(t!=null&&!VillageHorses.release(f.horse,f.horse.getOwnerUUID()),"Reservation protects the proposed trip");t.putString("state",Caravans.CANCELLED);Caravans.update(l.getServer(),t);
   h.assertTrue(VillageHorses.release(f.horse,f.horse.getOwnerUUID())&&VillageHorses.free(l,f.home.e())==null,"Idle horse can be reclaimed");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseMissingCartCancelsBeforeTakingGoods(GameTestHelper h){
  var f=fixture(h,6);try{var t=propose(f);h.assertTrue(t!=null,"Horse proposal");f.cart.discard();t.putString("state",Caravans.ACCEPTED);
   h.assertTrue(Caravans.secure(f.home.l(),t,1020)==0&&f.stock.countItem(Items.BREAD)==1408&&!CaravanHorses.reserved(f.home.l().getServer(),f.horse.getUUID()),"No cart means no debit or stranded reservation");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseReloadPreservesOwnerWoundsInventoryAndRejectsOldBody(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();f.horse.setHealth(12);f.horse.equipSaddle(null);var owner=f.horse.getOwnerUUID();var t=launch(f);var id=t.getUUID("id");Caravans.clear();t=Caravans.contract(l.getServer(),id);
   var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null&&horse.getUUID().equals(f.horse.getUUID())&&owner.equals(horse.getOwnerUUID())&&horse.getHealth()==12&&horse.isSaddled(),"Identity, wounds and saddle survived disk reload");
   horse.setHealth(9);var old=new CompoundTag();horse.saveWithoutId(old);CaravanHorses.unload(horse);Caravans.clear();t=Caravans.contract(l.getServer(),id);
   var stale=EntityType.HORSE.create(l);stale.load(old);h.assertTrue(CaravanHorses.stale(stale)&&!l.addFreshEntity(stale),"Old body rejected by actual join event");
   var restored=CaravanHorses.materialize(l,t,f.start);h.assertTrue(restored!=null&&restored.getHealth()==9,"Latest wound survives another load");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseDeathDropsOnlyOnceAndLeavesRecoverableCart(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();var t=launch(f);int n=t.getInt("secured");var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null,"Horse materialized");
   CaravanHorses.died(horse,2000);CaravanHorses.died(horse,2001);h.assertTrue(t.getInt("lost")==n&&t.hasUUID("leftCart")&&!t.contains("horse")&&!CaravanHorses.reserved(l.getServer(),horse.getUUID()),"One cargo loss, recoverable cart, no resurrection");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorseKeepsDogCartAndArmedEscortsWithoutAWorker(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();var s=f.home.s();
   var livestock=new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"livestock",0,0,25);var kennel=new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),VillageWolves.TYPE,0,0,18);s.addBuilding(livestock);s.addBuilding(kennel);s.linkAnnex(kennel.id(),livestock.id());
   var dog=EntityType.WOLF.create(l);dog.setTame(true);dog.setOwnerUUID(f.horse.getOwnerUUID());dog.moveTo(f.start.getX()+.5,f.start.getY(),f.start.getZ()+.5);l.addFreshEntity(dog);h.assertTrue(VillageWolves.enlist(l,f.home.e(),dog,kennel),"Real kennel wolf");dog.getPersistentData().putLong(VillageWolves.FED,VillageWolves.day(l));
   l.addFreshEntity(new CartEntity(l,f.start.offset(4,0,2),s.id()));
   var barracks=new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"barracks",0,0,30);s.addBuilding(barracks);var house=UUID.randomUUID();s.addHome(new org.villageastra.domain.Settlement.Home(house,1,4,true));
   for(int i=0;i<2;i++){var r=new org.villageastra.domain.Resident(UUID.randomUUID(),org.villageastra.domain.Resident.Life.ADULT,false,null,null,-1);s.admit(r,house);r.trainMilitary();s.assign(r.id(),org.villageastra.domain.Profession.SOLDIER,barracks.id());var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(s.id(),r);npc.moveTo(f.start.getX()+.5,f.start.getY(),f.start.getZ()+3.5);npc.setNoAi(true);l.addFreshEntity(npc);var gear=new CompoundTag();gear.put("weapon",new ItemStack(Items.IRON_SWORD).save(new CompoundTag()));GuardGoal.write(l,r.id(),gear);}
   var t=propose(f);h.assertTrue(t!=null&&CaravanHorses.isHorse(t),"Horse selected without any caravaneer");t.putInt("count",1200);t.putString("state",Caravans.ACCEPTED);Caravans.secure(l,t,1020);Caravans.dispatch(l,t,1020);
   var child=CaravanDogs.child(l.getServer(),t);h.assertTrue(child!=null&&child.getInt("secured")==176&&t.getInt("secured")==1024,"Second dog load preserved at VI");
   h.assertTrue(Caravans.contracts(l.getServer()).stream().filter(x->CaravanEscorts.escort(x)&&x.getUUID("leaderTrip").equals(t.getUUID("id"))).count()==2,"Two equipped guards accompany horse");
   for(int i=0;i<160;i++)Caravans.tick(l.getServer(),1040+i*20);
   h.assertTrue(t.getString("state").equals(Caravans.CLOSED)&&child.getString("state").equals(Caravans.CLOSED)&&t.getInt("delivered")+child.getInt("delivered")==1200&&f.stock.countItem(Items.BREAD)==208,"Whole convoy delivers exact cargo and returns");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void caravanHorsePlayerEscortCountsRoadAndAmbush(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();var s=f.home.s();s.addBuilding(new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"expedition",0,0,30));
   var p=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"HorseGuard"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(f.home.e().center().getX()+1.5,f.home.e().center().getY()+1,f.home.e().center().getZ()+5.5);
   org.villageastra.server.PropertyLedger.get(l.getServer()).gift(s.id(),p.getUUID(),Quests.trust(Wilds.ESCORT));var t=launch(f);var q=Wilds.escortFor(l,f.home.e(),t,1100);h.assertTrue(q!=null&&Quests.take(p,s.id(),q.getUUID("id")).equals("ok"),"Player takes horse caravan escort");
   var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null,"Horse physically on road");p.setPos(horse.getX()+2,horse.getY(),horse.getZ());Wilds.advance(l,f.home.e(),q.getUUID("id"),p,1200);
   var walking=Quests.quest(l,s.id(),q.getUUID("id"));h.assertTrue(walking.getBoolean("joined")&&walking.getInt("escorted")==1,"Player near the horse earns escort progress");
   t.putDouble("progress",Caravans.length(t)*.5);Caravans.update(l.getServer(),t);Wilds.advance(l,f.home.e(),q.getUUID("id"),p,1300);var ambushed=Quests.quest(l,s.id(),q.getUUID("id"));h.assertTrue(ambushed.getBoolean("ambushed")&&!ambushed.getList("ambush",11).isEmpty(),"Horse convoy triggers real ambush");
   for(var raw:ambushed.getList("ambush",11))if(l.getEntity(NbtUtils.loadUUID(raw)) instanceof net.minecraft.world.entity.Mob bandit){h.assertTrue(bandit.getTarget()==horse,"Bandits target actual horse carrier");bandit.discard();}
   Caravans.arrive(l,t,1400);Wilds.advance(l,f.home.e(),q.getUUID("id"),p,1500);h.assertTrue(Quests.quest(l,s.id(),q.getUUID("id")).getInt("progress")==1,"Escort can be completed after arrival");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=300) public static void caravanHorseKilledBeforeArrivalCannotCompletePlayerEscort(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();var s=f.home.s();s.addBuilding(new org.villageastra.domain.Settlement.Building(UUID.randomUUID(),"expedition",0,0,30));
   var p=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LostHorseGuard"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(f.home.e().center().getX()+1.5,f.home.e().center().getY()+1,f.home.e().center().getZ()+5.5);
   org.villageastra.server.PropertyLedger.get(l.getServer()).gift(s.id(),p.getUUID(),Quests.trust(Wilds.ESCORT));var t=launch(f);var q=Wilds.escortFor(l,f.home.e(),t,1100);h.assertTrue(q!=null&&Quests.take(p,s.id(),q.getUUID("id")).equals("ok"),"Escort taken");
   var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null,"Horse appears");p.setPos(horse.getX()+2,horse.getY(),horse.getZ());Wilds.advance(l,f.home.e(),q.getUUID("id"),p,1200);
   CaravanHorses.died(horse,1300);Wilds.advance(l,f.home.e(),q.getUUID("id"),p,1400);var failed=Quests.quest(l,s.id(),q.getUUID("id"));h.assertTrue(failed.getString("state").equals(Quests.FAILED)&&failed.getInt("progress")==0,"Lost cargo must fail escort, not count as arrival: "+failed);h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",timeoutTicks=200) public static void caravanHorsePreparedLoadResumesWithoutDiscardingJournalledCargo(GameTestHelper h){
  var f=fixture(h,6);try{var l=f.home.l();var t=propose(f);t.putString("state",Caravans.ACCEPTED);h.assertTrue(Caravans.secure(l,t,1020)>0,"Prepared cargo");int n=t.getInt("secured");
   t.putString("state",Caravans.ACCEPTED);Caravans.update(l.getServer(),t);f.horse.discard();var id=t.getUUID("id");Caravans.clear();t=Caravans.contract(l.getServer(),id);
   h.assertTrue(Caravans.secure(l,t,1040)==n&&f.stock.countItem(Items.BREAD)==1408-n,"Replay owns snapshot and cart, no new debit");Caravans.dispatch(l,t,1040);
   var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null&&horse.getUUID().equals(f.horse.getUUID()),"Prepared horse survives replay");h.succeed();
  }finally{clean(f);}
 }
 @GameTest(template="empty",batch="trade_horse_verge",timeoutTicks=500) public static void caravanHorseBypassesRaisedFoundationWithItsCart(GameTestHelper h){
  var f=fixture(h,6);var l=f.home.l();var t=launch(f);t.putLong("from",f.start.asLong());t.putLong("to",f.start.offset(40,0,0).asLong());Caravans.update(l.getServer(),t);
  for(int x=-2;x<=45;x++)for(int z=-7;z<=7;z++){var p=f.start.offset(x,0,z);int top=Math.max(p.getY()+5,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ()));l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);for(int y=p.getY();y<=top;y++)l.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);}
  for(int x=4;x<=18;x++)for(int z=-1;z<=1;z++)l.setBlock(f.start.offset(x,0,z),Blocks.COBBLESTONE.defaultBlockState(),2);
  var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null,"Horse appears beside foundation");
  h.runAfterDelay(220,()->{try{h.assertTrue(horse.getX()>f.start.getX()+15&&Math.abs(horse.getY()-f.start.getY())<.6,"Horse uses flat verge: "+horse.position());h.assertTrue(!l.getEntitiesOfClass(CartEntity.class,horse.getBoundingBox().inflate(8),c->t.getUUID("id").equals(c.trip())&&!c.blocked()).isEmpty(),"Cart also cleared foundation");h.succeed();}finally{clean(f);}});
 }
 @GameTest(template="empty",batch="trade_horse_walk",timeoutTicks=500) public static void caravanHorseActuallyWalksWithItsCart(GameTestHelper h){
  var f=fixture(h,6);var l=f.home.l();var t=launch(f);t.putLong("from",f.start.asLong());t.putLong("to",f.start.offset(6,0,0).asLong());Caravans.update(l.getServer(),t);
  // A destination farther than the arrival radius gives the horse a real stretch to walk.
  t.putLong("to",f.start.offset(40,0,0).asLong());for(int x=0;x<=45;x++)for(int z=-3;z<=3;z++){var p=f.start.offset(x,0,z);l.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);int top=Math.max(p.getY()+4,l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,p.getX(),p.getZ()));for(int y=p.getY();y<=top;y++)l.setBlock(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState(),2);}
  var horse=CaravanHorses.materialize(l,t,f.start);h.assertTrue(horse!=null,"Horse on road");double start=horse.getX();
  h.runAfterDelay(100,()->{try{h.assertTrue(horse.getX()>start+2&&t.getDouble("progress")>2,"Horse navigated: start="+start+" now="+horse.position()+" state="+t.getString("state")+" progress="+t.getDouble("progress")+" goals="+horse.goalSelector.getAvailableGoals().stream().filter(g->g.isRunning()).map(g->g.getGoal().getClass().getSimpleName()).toList()+" nav="+(horse.getNavigation().getPath()==null?"none":horse.getNavigation().getPath().getTarget())+" carts="+l.getEntitiesOfClass(CartEntity.class,horse.getBoundingBox().inflate(20),c->t.getUUID("id").equals(c.trip())).stream().map(c->c.position()+" blocked="+c.blocked()+" dist="+horse.distanceToSqr(c)).toList());h.assertTrue(!l.getEntitiesOfClass(CartEntity.class,horse.getBoundingBox().inflate(8),c->horse.getUUID().equals(c.puller())).isEmpty(),"Physical cart follows horse");h.succeed();}finally{clean(f);}});
 }
}
