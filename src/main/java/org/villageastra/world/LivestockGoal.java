package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;
/** AD-035: a livestock keeper keeps real animals. AD-138 (owner 2026-09-22): pen by pen — he fills each pen's feeder from the yard chest over
 *  the fence, breeds a pen below PEN_CAP on one step of its feeder (two real items), drives wild stock of the pen's kind in (no lead: it
 *  walks in itself, he runs behind it, owner 2026-09-23) when it holds fewer than KEEP adults, culls one adult of a pen above PEN_CAP (at most CULLS_PER_DAY a day), shears its sheep with real
 *  shears and carries the drops lying in the yard into the chest. He goes into a pen through its gate (Gates) and closes it behind him. */
public final class LivestockGoal extends Goal {
 public static final String OWNER="AstraLivestock";public static final int SEARCH=48;
 /** AD-140: how far from the yard an animal a quest brought is still the keeper's to drive in. */
 public static final int QUEST_REACH=32;
 private static Class<? extends Animal> type(String kind){return LivestockPens.kind(kind);}
 /** The pen of a yard of level I holds this many (core effect "herd" at I = PEN_CAP). */
 public static final int MAX=16;
 private final ResidentEntity keeper;private final boolean withoutPlayers;private Animal led;private LivestockPens.Pen ledTo,inPen;private int cooldown,entering,leading,turn;private boolean exiting;
 /** Culls of each pen of this yard on the village day they were made: pen -> {day, count}. */
 private static final Map<String,long[]> CULLS=new HashMap<>();
 public LivestockGoal(ResidentEntity keeper){this(keeper,false);}
 public LivestockGoal(ResidentEntity keeper,boolean withoutPlayers){this.keeper=keeper;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private record Duty(SettlementData.Entry entry,Settlement.Building yard){}
 private Duty duty(){
  if(!(keeper.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||keeper.settlementId()==null||keeper.escortPlayer()!=null||!keeper.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(keeper.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(keeper.getUUID());var b=e.settlement().workplace(keeper.getUUID());
  return r!=null&&r.alive()&&r.profession()==Profession.LIVESTOCK_FARMER&&b!=null&&b.type().equals("livestock")?new Duty(e,b):null;
 }
 public static boolean owned(Animal a,UUID settlement){return a.getPersistentData().hasUUID(OWNER)&&a.getPersistentData().getUUID(OWNER).equals(settlement);}
 /** AD-112/AD-138: the head a yard holds at this working level (PEN_CAP per pen: 16/32/64). */
 public static int max(int level){return CoreEffects.value("livestock","herd",level);}
 public static int max(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){return max(BuildingLevels.level(l,e,yard));}
 /** Every owned animal of a kind about a yard, pens or no pens (a siege's target, the yard's work in progress). */
 public static List<Animal> herd(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,Class<? extends Animal> kind){
  return l.getEntitiesOfClass(Animal.class,LivestockPens.yardBox(e,yard).inflate(16),a->kind.isInstance(a)&&a.isAlive()&&owned(a,e.settlement().id()));}
 /** The pens a yard works at its working level. */
 public static List<LivestockPens.Pen> pens(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){return LivestockPens.built(BuildingLevels.level(l,e,yard));}
 /** The kind a pen keeps (the mayor's choice, else the pen's own). */
 public static String species(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p){return org.villageastra.server.LivestockPolicies.get(l.getServer()).species(e.settlement().id(),yard.id(),p);}
 static Item feed(Animal a){return LivestockPens.feed(LivestockPens.species(a));}
 private static BlockPos feederAt(SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p){return LivestockPens.at(e,yard,p.feeder());}
 /** Fills one step of a pen's feeder: two real items of the pen's feed leave the yard chest and the trough's FEED rises by one. */
 public static boolean fill(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p,long now){
  var at=feederAt(e,yard,p);var state=l.getBlockState(at);if(!(state.getBlock() instanceof FeederBlock)||state.getValue(FeederBlock.FEED)>=FeederBlock.MAX)return false;
  var chest=LogisticsRoutes.chest(l,e,yard);if(chest==null)return false;var food=LivestockPens.feed(species(l,e,yard,p));
  var id=Settlement.childId(yard.id(),"feeder/"+p.index()+"/"+now);var got=WorldJournal.recoverAmount(l,id);
  if(got.isEmpty()&&!WorldJournal.exists(l,id))for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(food)&&chest.getItem(slot).getCount()>=FeederBlock.ITEMS_PER_STEP){got=WorldJournal.takeAmount(l,id,LogisticsRoutes.position(e,yard),slot,chest.getItem(slot).copy(),FeederBlock.ITEMS_PER_STEP);break;}
  if(got.getCount()<FeederBlock.ITEMS_PER_STEP)return false;
  return WorldJournal.place(l,Settlement.childId(id,"fill"),at,state,state.setValue(FeederBlock.FEED,state.getValue(FeederBlock.FEED)+1));
 }
 /** Two adults of a pen below PEN_CAP breed on one step of the pen's feeder; nothing is taken from the chest. */
 public static boolean breed(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p,long now){
  var herd=LivestockPens.herd(l,e,yard,p);if(herd.size()>=LivestockPens.PEN_CAP)return false;var kind=LivestockPens.kind(species(l,e,yard,p));
  var parents=herd.stream().filter(a->kind.isInstance(a)&&!a.isBaby()&&a.canFallInLove()&&a.getAge()==0).limit(2).toList();if(parents.size()<2)return false;
  var at=feederAt(e,yard,p);var state=l.getBlockState(at);if(!(state.getBlock() instanceof FeederBlock)||state.getValue(FeederBlock.FEED)<1)return false;
  if(!WorldJournal.place(l,Settlement.childId(yard.id(),"feeder/"+p.index()+"/eat/"+now),at,state,state.setValue(FeederBlock.FEED,state.getValue(FeederBlock.FEED)-1)))return false;
  for(var parent:parents)parent.setInLove(null);return true;
 }
 /** Culls left today in a pen (CULLS_PER_DAY a village day). */
 public static int cullsLeft(Settlement.Building yard,LivestockPens.Pen p,long now){var c=CULLS.get(yard.id()+"/"+p.index());long day=now/24000;return c==null||c[0]!=day?LivestockPens.CULLS_PER_DAY:(int)Math.max(0,LivestockPens.CULLS_PER_DAY-c[1]);}
 public static void culled(Settlement.Building yard,LivestockPens.Pen p,long now){long day=now/24000;CULLS.compute(yard.id()+"/"+p.index(),(k,c)->c==null||c[0]!=day?new long[]{day,1}:new long[]{day,c[1]+1});}
 /** Shearing uses real shears from the yard chest (one durability per sheep); wool goes into the chest through the journal. */
 public static int shear(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,Sheep sheep,long now){
  if(!owned(sheep,e.settlement().id())||!sheep.readyForShearing())return 0;var chest=LogisticsRoutes.chest(l,e,yard);if(chest==null)return 0;
  int shearsSlot=-1;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).getItem() instanceof ShearsItem){shearsSlot=slot;break;}if(shearsSlot<0)return 0;
  var pos=LogisticsRoutes.position(e,yard);var tool=chest.getItem(shearsSlot).copy();var worn=tool.copy();worn.setDamageValue(worn.getDamageValue()+1);
  var wearId=Settlement.childId(sheep.getUUID(),"shears/"+now);
  if(!WorldJournal.exists(l,wearId)){var taken=WorldJournal.takeAmount(l,wearId,pos,shearsSlot,tool,1);if(taken.isEmpty())return 0;
   if(worn.getDamageValue()<worn.getMaxDamage()&&!WorldJournal.deposit(l,Settlement.childId(wearId,"back"),pos,worn))return 0;}
  int wool=1+Math.floorMod(sheep.getUUID().hashCode()+(int)(now/20),3);
  sheep.setSheared(true);
  if(!WorldJournal.deposit(l,Settlement.childId(sheep.getUUID(),"wool/"+now),pos,new ItemStack(woolOf(sheep),wool)))return 0;
  return wool;
 }
 private static Item woolOf(Sheep sheep){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(sheep.getColor().getName()+"_wool"));}
 private static final Set<Item> DROPS=Set.of(Items.EGG,Items.FEATHER,Items.LEATHER,Items.BEEF,Items.MUTTON,Items.CHICKEN,Items.PORKCHOP,Items.RABBIT,Items.RABBIT_HIDE);
 /** Item drops (eggs, feathers, meat, leather, wool) lying in the yard are carried into the yard chest. */
 public static int gather(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,long now){
  var pos=LogisticsRoutes.position(e,yard);int moved=0;
  for(var item:l.getEntitiesOfClass(ItemEntity.class,LivestockPens.yardBox(e,yard).inflate(2),i->i.isAlive()&&!i.getItem().isEmpty()&&(DROPS.contains(i.getItem().getItem())||i.getItem().is(net.minecraft.tags.ItemTags.WOOL)))){
   var stack=item.getItem().copy();var id=Settlement.childId(item.getUUID(),"gather");
   if(WorldJournal.deposit(l,id,pos,stack)){item.discard();moved+=stack.getCount();}
  }
  return moved;
 }
 @Override public boolean canUse(){return duty()!=null&&CargoCustody.mayStartWork(keeper);}
 @Override public boolean canContinueToUse(){return duty()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 private boolean near(BlockPos p,double reach){return keeper.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)<=reach*reach;}
 /** To exactly this cell (a plain moveTo stops a block short, out of reach of a feeder over the fence). */
 private net.minecraft.world.phys.Vec3 lastPos;private int still,stepping;private boolean sideStepped;
 /** A cell a keeper can stand in: open with headroom over a solid floor, not a fence or a gate. */
 private boolean open(BlockPos c){var l=keeper.level();var at=l.getBlockState(c);var up=l.getBlockState(c.above());
  return at.getCollisionShape(l,c).isEmpty()&&up.getCollisionShape(l,c.above()).isEmpty()&&l.getBlockState(c.below()).isFaceSturdy(l,c.below(),net.minecraft.core.Direction.UP);}
 private void walk(BlockPos p,double speed){
  // Probe livestock-5: pressed against a fence post at the lane's mouth, his block was the fence's, the path began inside it and he stood
  // there for good. Still for two seconds, he steps to the middle of the free cell nearest the goal first.
  if(stepping>0){stepping--;return;}
  // Checked against where he stood two seconds ago: pushing into a post he trembles, but gets nowhere.
  if(lastPos==null||++still<40){if(lastPos==null)lastPos=keeper.position();}
  else if(keeper.position().distanceToSqr(lastPos)>.09){lastPos=keeper.position();still=0;sideStepped=false;}
  else{lastPos=keeper.position();var here=keeper.blockPosition();BlockPos best=null;
   // A side step first (a diagonal one runs into the same post), the diagonals only when no side is open.
   for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var c=here.relative(d);if(open(c)&&(best==null||c.distSqr(p)<best.distSqr(p)))best=c;}
   if(best==null)for(int dx=-1;dx<=1;dx+=2)for(int dz=-1;dz<=1;dz+=2){var c=here.offset(dx,0,dz);if(open(c)&&(best==null||c.distSqr(p)<best.distSqr(p)))best=c;}
   // Probe livestock-9: pushed a hair into the corner post of a pen (his box inside the fence's), no move is allowed him at all; then, and
   // only then, he is set in the middle of the free cell beside him - one block at most, the last resort AD-132 uses for a wedged builder.
   // Probe livestock-10: a side step asked of the move control left him where he stood as well - a second time stuck, he is set there.
   if(best!=null){keeper.getNavigation().stop();if(!keeper.level().noCollision(keeper)||sideStepped)keeper.moveTo(best.getX()+.5,best.getY(),best.getZ()+.5,keeper.getYRot(),keeper.getXRot());
    else keeper.getMoveControl().setWantedPosition(best.getX()+.5,best.getY(),best.getZ()+.5,speed);sideStepped=!sideStepped;stepping=10;}still=0;return;}
  if(keeper.tickCount%10!=0&&!keeper.getNavigation().isDone())return;var path=keeper.getNavigation().createPath(p,0);if(path!=null)keeper.getNavigation().moveTo(path,speed);else keeper.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,speed);}
 /** Into a pen: to the lane before its gate, open it, step in, and shut it behind him (probe livestock-13: a gate left open while he
  *  worked inside let the herd out). True once he stands inside; while he leads an animal the gate stays open until it is in. */
 private boolean enter(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p){
  if(LivestockPens.inside(e,yard,p,keeper.blockPosition())){inPen=p;var gate=LivestockPens.at(e,yard,p.gate());
   if(led==null&&Gates.isOpen(l.getBlockState(gate))&&!keeper.blockPosition().equals(gate)&&keeper.distanceToSqr(gate.getX()+.5,gate.getY(),gate.getZ()+.5)>1.2)Gates.close(l,gate,keeper);return true;}
  var gate=LivestockPens.at(e,yard,p.gate());
  if(!Gates.isOpen(l.getBlockState(gate))){if(Gates.reaches(keeper,gate)){Gates.open(l,gate,keeper);inPen=p;}else walk(LivestockPens.at(e,yard,p.outside()),.7);return false;}
  inPen=p;walk(LivestockPens.at(e,yard,p.inside()),.7);return false;
 }
 /** Out of the pen he is in and the gate shut behind him; true once that is done (or he was in none). */
 private boolean leave(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  if(inPen==null)return true;cooldown=0;var p=inPen;var gate=LivestockPens.at(e,yard,p.gate());var out=LivestockPens.at(e,yard,p.outside());
  if(LivestockPens.inside(e,yard,p,keeper.blockPosition())||keeper.blockPosition().equals(gate)){
   if(!Gates.isOpen(l.getBlockState(gate))){if(Gates.reaches(keeper,gate))Gates.open(l,gate,keeper);else walk(LivestockPens.at(e,yard,p.inside()),.7);return false;}
   walk(out,.7);return false;}
  if(keeper.distanceToSqr(gate.getX()+.5,gate.getY(),gate.getZ()+.5)<1.2){walk(out,.7);return false;}
  Gates.close(l,gate,keeper);inPen=null;return true;
 }
 @Override public void tick(){
  var d=duty();if(d==null)return;var l=(ServerLevel)keeper.level();var e=d.entry();var yard=d.yard();if(cooldown>0)cooldown--;
  long now=SettlementData.get(l.getServer()).clock().ticks();
  if(led!=null){if(drive(l,e,yard))return;}
  // A job inside a pen ends with him out and the gate shut; so does one he could not get to within ten seconds.
  if(inPen!=null&&!exiting&&++entering>200)exiting=true;
  if(exiting){if(!leave(l,e,yard))return;exiting=false;entering=0;}
  if(cooldown>0)return;cooldown=40;
  if(gather(l,e,yard,now)>0){keeper.workStatus("livestock_gathering");return;}
  var chest=LogisticsRoutes.chest(l,e,yard);
  // The next pen first once a job is done (livestock-probes-a: the first pen's work kept him from the fourth for good) - but not while
  // he is on his way to one (livestock-probes2: turning every round, he walked to and fro between feeders and reached none).
  var round=pens(l,e,yard);int first=Math.floorMod(turn,Math.max(1,round.size()));
  for(int k=0;k<round.size();k++){var p=round.get((first+k)%round.size());
   var kind=species(l,e,yard,p);var herd=LivestockPens.herd(l,e,yard,p);
   // AD-138 V: a pen out to graze has its gate open all day — the herd goes out and comes home through it (GrazeGoal); nothing else
   // is done with it meanwhile. Once it no longer grazes and the herd is home, the gate is shut again.
   var pgate=LivestockPens.at(e,yard,p.gate());
   if(LivestockGrazing.out(l,e,yard,p)){
    if(!Gates.isOpen(l.getBlockState(pgate))){if(Gates.reaches(keeper,pgate))Gates.open(l,pgate,keeper);else{walk(LivestockPens.at(e,yard,p.outside()),.7);cooldown=0;}keeper.workStatus("livestock_letting_out");return;}
    continue;}
   if(Gates.isOpen(l.getBlockState(pgate))&&led==null&&inPen!=p&&LivestockGrazing.allowed(l,e,yard)&&herd.stream().allMatch(a->LivestockPens.fenced(e,yard,p,a.blockPosition()))
     &&herd.stream().noneMatch(a->a.blockPosition().equals(pgate))){
    if(Gates.reaches(keeper,pgate)){Gates.close(l,pgate,keeper);keeper.workStatus("livestock_shutting");return;}
    walk(LivestockPens.at(e,yard,p.outside()),.7);cooldown=0;keeper.workStatus("livestock_shutting");return;}
   // The feeder, from the walk over the fence.
   var feeder=feederAt(e,yard,p);var fs=l.getBlockState(feeder);
   if(fs.getBlock() instanceof FeederBlock&&fs.getValue(FeederBlock.FEED)<FeederBlock.MAX&&chest!=null&&LogisticsRoutes.count(chest,s->s.is(LivestockPens.feed(kind)))>=FeederBlock.ITEMS_PER_STEP){
    if(!near(feeder,2.6)){walk(LivestockPens.at(e,yard,p.walk()),.7);cooldown=0;keeper.workStatus("livestock_feeding");return;}
    if(fill(l,e,yard,p,now)){turn++;keeper.workStatus("livestock_feeding");return;}}
   // One of the pen's that slipped out is driven back first.
   // (Probe livestock: one standing in the open gate is not out; only one beyond the fence is.)
   var stray=herd.stream().filter(a->!a.isBaby()&&!a.isLeashed()&&!LivestockPens.fenced(e,yard,p,a.blockPosition())).findFirst().orElse(null);
   if(stray!=null){
    if(keeper.distanceToSqr(stray)>36){keeper.getNavigation().moveTo(stray,.8);cooldown=0;keeper.workStatus("livestock_catching");return;}
    led=stray;ledTo=p;leading=0;return;}
   // AD-140 (owner 2026-09-24): an animal a quest brought to the yard is driven in like any other — the keeper walks it in on its own legs,
   // whether or not the village may keep that kind yet (these are the very animals that open it). One already taken into a pen is the
   // yard's own from then on (a stray of it is the stray branch's), never driven from one pen of its kind to another.
   var brought=l.getEntitiesOfClass(Animal.class,keeper.getBoundingBox().inflate(SEARCH),a->type(kind).isInstance(a)&&a.isAlive()&&!a.isLeashed()
     &&a.getPersistentData().hasUUID(AnimalSites.TAG)&&!a.getPersistentData().hasUUID(OWNER)&&!LivestockPens.fenced(e,yard,p,a.blockPosition())
     &&a.blockPosition().distSqr(LogisticsRoutes.position(e,yard))<=(long)QUEST_REACH*QUEST_REACH).stream().min(Comparator.comparingDouble(a->a.distanceToSqr(keeper))).orElse(null);
   if(brought!=null){
    if(keeper.distanceToSqr(brought)>36){keeper.getNavigation().moveTo(brought,.8);cooldown=0;keeper.workStatus("livestock_catching");return;}
    led=brought;ledTo=p;leading=0;return;}
   long adults=herd.stream().filter(a->!a.isBaby()&&LivestockPens.inside(e,yard,p,a.blockPosition())).count();
   if(adults<LivestockPens.KEEP){
    var wild=l.getEntitiesOfClass(Animal.class,keeper.getBoundingBox().inflate(SEARCH),a->type(kind).isInstance(a)&&LivestockPens.kept(a)&&a.isAlive()&&!a.isBaby()&&!a.getPersistentData().hasUUID(OWNER)&&!a.isLeashed()).stream().min(Comparator.comparingDouble(a->a.distanceToSqr(keeper))).orElse(null);
    if(wild==null)continue;
    if(keeper.distanceToSqr(wild)>36){keeper.getNavigation().moveTo(wild,.8);cooldown=0;keeper.workStatus("livestock_catching");return;}
    // Found: from here the animal is driven, not led (it belongs to the pen once it is in).
    led=wild;ledTo=p;leading=0;return;
   }
   // AD-138 VI: at a yard of VI with kennel wolves the machine sends a wolf to cull; the keeper leaves it to them.
   if(herd.size()>LivestockPens.PEN_CAP&&cullsLeft(yard,p,now)>0&&!(BuildingLevels.level(l,e,yard)>=6&&!VillageWolves.wolves(l,e).isEmpty())){var surplus=herd.stream().filter(a->!a.isBaby()).findFirst().orElse(null);
    if(surplus!=null){if(!act(l,e,yard,p,surplus)){keeper.workStatus("livestock_culling");return;}
     surplus.hurt(l.damageSources().mobAttack(keeper),1000F);keeper.swing(net.minecraft.world.InteractionHand.MAIN_HAND);culled(yard,p,now);exiting=true;turn++;keeper.workStatus("livestock_culling");return;}}
   if(breed(l,e,yard,p,now)){turn++;keeper.workStatus("livestock_breeding");return;}
   if(kind.equals("sheep")&&chest!=null&&chest.hasAnyMatching(s->s.getItem() instanceof ShearsItem))for(var a:herd)if(a instanceof Sheep sheep&&sheep.readyForShearing()){
    if(!act(l,e,yard,p,sheep)){keeper.workStatus("livestock_shearing");return;}
    exiting=true;turn++;if(shear(l,e,yard,sheep,now)>0){keeper.workStatus("livestock_shearing");return;}}
  }
  turn++;var home=LogisticsRoutes.position(e,yard);if(!near(home,6))walk(home,.6);
  keeper.workStatus("livestock_tending");
 }
 /** AD-138 (owner 2026-09-23: "the animals run into the pen themselves, the keeper runs beside them, behind"): the animal walks to the
  *  lane before the pen's gate, through it and to the middle of the pen on its own path; the keeper keeps two or three blocks behind it,
  *  on the far side from where it goes, opens the gate as it comes up and shuts it once it is in. No lead. Ten seconds balking at a gate
  *  or in a corner and the animal is set a block on (the last resort the keeper has himself). False when the drive is over. */
 private boolean drive(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  var a=led;var p=ledTo;
  if(a==null||p==null||!a.isAlive()||a.isLeashed()||keeper.distanceToSqr(a)>24*24){led=null;ledTo=null;leading=0;return false;}
  var gate=LivestockPens.at(e,yard,p.gate());var outside=LivestockPens.at(e,yard,p.outside());var middle=LivestockPens.at(e,yard,new BlockPos(p.x()+3,1,p.z()+3));
  if(LivestockPens.inside(e,yard,p,a.blockPosition())&&!a.blockPosition().equals(gate)){
   // In: the gate shut behind it once it has gone on a step, the animal given its pen.
   if(Gates.isOpen(l.getBlockState(gate))){if(a.distanceToSqr(gate.getX()+.5,gate.getY(),gate.getZ()+.5)<2.3){a.getNavigation().moveTo(middle.getX()+.5,middle.getY(),middle.getZ()+.5,1.0);keeper.workStatus("livestock_leading");return true;}
    if(!Gates.reaches(keeper,gate)){walk(outside,.8);keeper.workStatus("livestock_leading");return true;}Gates.close(l,gate,keeper);}
   LivestockPens.tag(a,e.settlement().id(),yard,p);a.getNavigation().stop();led=null;ledTo=null;leading=0;turn++;SettlementData.get(l.getServer()).setDirty();return false;}
  // Where it goes: to the lane before the gate, then through it to the middle.
  boolean atGate=a.distanceToSqr(outside.getX()+.5,outside.getY(),outside.getZ()+.5)<2.3||a.blockPosition().equals(gate);
  var goal=atGate?middle:outside;
  if(atGate&&!Gates.isOpen(l.getBlockState(gate))){if(keeper.distanceToSqr(gate.getX()+.5,gate.getY(),gate.getZ()+.5)<=36)Gates.open(l,gate,keeper);else walk(outside,.8);}
  if(a.getNavigation().isDone()||keeper.tickCount%10==0)a.getNavigation().moveTo(goal.getX()+.5,goal.getY(),goal.getZ()+.5,1.0);
  // The keeper behind it: two and a half blocks back along the way it goes.
  double dx=a.getX()-(goal.getX()+.5),dz=a.getZ()-(goal.getZ()+.5),n=Math.max(.001,Math.sqrt(dx*dx+dz*dz));
  var behind=BlockPos.containing(a.getX()+dx/n*2.5,a.getY(),a.getZ()+dz/n*2.5);
  if(keeper.distanceToSqr(behind.getX()+.5,behind.getY(),behind.getZ()+.5)>2.25)walk(behind,.9);else keeper.getNavigation().stop();
  keeper.getLookControl().setLookAt(a);
  if(++leading>200){var step=a.blockPosition().relative(net.minecraft.core.Direction.getNearest(goal.getX()+.5-a.getX(),0,goal.getZ()+.5-a.getZ()));
   var at=l.getBlockState(step);if(at.getCollisionShape(l,step).isEmpty()&&l.getBlockState(step.above()).getCollisionShape(l,step.above()).isEmpty())a.moveTo(step.getX()+.5,step.getY(),step.getZ()+.5,a.getYRot(),0);leading=0;}
  keeper.workStatus("livestock_leading");return true;
 }
 /** Within reach of an animal of a pen, going in through the gate first; true once he can touch it. */
 private boolean act(ServerLevel l,SettlementData.Entry e,Settlement.Building yard,LivestockPens.Pen p,Entity target){
  cooldown=0;if(!enter(l,e,yard,p))return false;
  if(keeper.distanceToSqr(target)>6.25){keeper.getNavigation().moveTo(target,.8);return false;}
  cooldown=40;return true;
 }
 @Override public void stop(){keeper.getNavigation().stop();if(led!=null)led.getNavigation().stop();led=null;ledTo=null;
  if(inPen!=null&&keeper.level() instanceof ServerLevel l){var d=duty();if(d!=null)Gates.close(l,LivestockPens.at(d.entry(),d.yard(),inPen.gate()),keeper);}inPen=null;}
}
