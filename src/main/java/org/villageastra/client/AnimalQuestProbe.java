package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
import org.villageastra.world.BuildingPlacement;
/** AD-140: the sheep of the yard come by "the stolen flock" in the live game. A real yard of level I is laid next to the village; the village
 *  posts the card by itself; its chart carries the cross; at the rustlers' camp the band is beaten and the pen's gate opened; the bellwether and
 *  the ewe are led on leads out of the camp and home to the lane before the sheep pen. There the player lets them go, and the yard's own
 *  keeper drives them in as he drives any beast — walking two blocks behind while the sheep runs in by itself. Only then are sheep open for
 *  the village and the card paid. The player walks every block of the way, the sheep on their own legs. */
final class AnimalQuestProbe {
 private static int phase,ticks,index,wolves,waited;private static volatile String failure,progress="";
 private static volatile UUID quest,yardId,keeperId;private static volatile boolean ready,flag,shot;
 private static final List<Vec3> ROUTE=new ArrayList<>();private static final List<UUID> LED=new ArrayList<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.animalSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-animal-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}LogUtils.getLogger().info("ASTRA_ANIMAL screenshot {}",path);}
 private static SettlementData.Entry entry(MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static ServerPlayer player(MinecraftServer s){return s.getPlayerList().getPlayers().get(0);}
 private static CompoundTag card(MinecraftServer s){return quest==null?null:Quests.quest(s.overworld(),entry(s).settlement().id(),quest);}
 private static CompoundTag site(MinecraftServer s){var q=card(s);return q==null?null:QuestSites.site(s.overworld(),Quests.root(q));}
 private static Settlement.Building yard(SettlementData.Entry e){return e.settlement().buildings().stream().filter(b->b.id().equals(yardId)).findFirst().orElseThrow();}
 private static Vec3 ground(ServerLevel l,double x,double z){return ground(l,x,z,Integer.MIN_VALUE);}
 /** The ground a player walks on here: never the top of a fence, a wall or a gate, which the height map counts as ground. */
 private static Vec3 ground(ServerLevel l,double x,double z,int near){
  int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(x),(int)Math.floor(z));
  // The level nearest the one the player walks at (up a step at most): a tent roof or a fence above is not the way on.
  int[] order=near==Integer.MIN_VALUE?java.util.stream.IntStream.rangeClosed(0,8).map(i->top-i).toArray():new int[]{near,near+1,near-1,near-2,near-3,near-4};
  for(int y:order){var feet=BlockPos.containing(x,y,z);var under=l.getBlockState(feet.below());
   if(under.getCollisionShape(l,feet.below()).isEmpty()||under.is(net.minecraft.tags.BlockTags.FENCES)||under.is(net.minecraft.tags.BlockTags.WALLS)||under.is(net.minecraft.tags.BlockTags.FENCE_GATES))continue;
   if(!l.getBlockState(feet).getCollisionShape(l,feet).isEmpty()&&!(l.getBlockState(feet).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock))continue;
   return new Vec3(x,y,z);}
  return near==Integer.MIN_VALUE?new Vec3(x,top,z):null;
 }
 private static Sheep sheep(ServerLevel l,CompoundTag site,String role){
  var roles=site.getCompound("roles");for(var x:site.getList("animals",Tag.TAG_INT_ARRAY)){var id=NbtUtils.loadUUID(x);if(roles.getString(id.toString()).equals(role)&&l.getEntity(id) instanceof Sheep s&&s.isAlive())return s;}return null;
 }
 private static List<Sheep> flock(ServerLevel l,CompoundTag site){
  var out=new ArrayList<Sheep>();for(var x:site.getList("animals",Tag.TAG_INT_ARRAY))if(l.getEntity(NbtUtils.loadUUID(x)) instanceof Sheep s&&s.isAlive()&&s.getPersistentData().hasUUID(AnimalSites.TAG))out.add(s);return out;
 }
 /** One step of the player's walk along the route, a fifth of a block a tick, the led animals on their leads behind. True once it is walked. */
 private static boolean walk(ServerLevel l,ServerPlayer p){
  if(ROUTE.isEmpty())return true;
  // A player leading animals waits for the one that fell behind instead of dragging it until the lead snaps.
  // A led sheep walks to its player on its own legs while the lead is slack (within six blocks); the player never lets it grow taut.
  // A led animal walks after its player on its own legs while the lead is slack; the player keeps it slack and waits when one falls behind.
  Animal behind=null;
  for(var id:LED)if(l.getEntity(id) instanceof Animal a){if(a.distanceTo(p)>2&&a.getNavigation().isDone())a.getNavigation().moveTo(p,1.15);if(a.distanceTo(p)>4.5)behind=a;}
  // One that stays behind for five seconds: the player walks back to it with the wheat (a few blocks) and leads on from there.
  if(behind!=null){if(++waited>100){waited=0;var back=behind.position().add(p.position().subtract(behind.position()).normalize().scale(2));var g=ground(l,back.x,back.z,(int)Math.floor(behind.getY()));
    if(g!=null){p.teleportTo(l,g.x,g.y,g.z,p.getYRot(),10);LogUtils.getLogger().info("ASTRA_ANIMAL the player walks back for a sheep that stayed behind at {}",behind.blockPosition().toShortString());}}
   return false;}
  waited=0;
  var to=ROUTE.get(0);var at=p.position();var d=new Vec3(to.x-at.x,0,to.z-at.z);
  if(d.length()<.3){ROUTE.remove(0);return ROUTE.isEmpty();}
  var step=d.normalize().scale(Math.min(.18,d.length()));var next=ground(l,at.x+step.x,at.z+step.z,(int)Math.floor(at.y));if(next==null){var c=BlockPos.containing(at.x+step.x,at.y,at.z+step.z);var cells=new StringBuilder();for(int dy=2;dy>=-4;dy--)cells.append(dy).append(':').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(l.getBlockState(c.above(dy)).getBlock()).getPath()).append(' ');
   failure="The way home is blocked at "+p.blockPosition().toShortString()+" toward "+to+" cells "+cells+" route "+ROUTE;return false;}
  p.teleportTo(l,next.x,next.y,next.z,(float)(Math.atan2(-step.x,step.z)*180/Math.PI),10);return false;
 }
 /** Every led animal is still on the lead (a lead that broke fails the probe with where it happened). */
 private static void held(ServerLevel l,ServerPlayer p){for(var id:LED)if(l.getEntity(id) instanceof Animal a&&a.getLeashHolder()!=p)failure="A lead came off at "+a.blockPosition().toShortString()+", player at "+p.blockPosition().toShortString();}
 private static Vec3 local(SettlementData.Entry e,Settlement.Building yard,double x,double z){var b=LivestockPens.at(e,yard,new BlockPos((int)Math.floor(x),1,(int)Math.floor(z)));return new Vec3(b.getX()+.5,b.getY(),b.getZ()+.5);}
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);if(++ticks>24000)throw new IllegalStateException("Animal timeout phase="+phase+" "+progress);
  var s=mc.getSingleplayerServer();
  // A real livestock yard of level I beside the village; sheep, cows and hens are all still to be earned.
  if(phase==0&&ticks>60){phase=1;ticks=0;s.execute(()->{try{
    var l=s.overworld();AnimalUnlocks.migrateWorld(s);var e=entry(s);var st=e.settlement();
    // The smoke world is peaceful; the rustlers need a world where bands exist.
    s.setDifficulty(net.minecraft.world.Difficulty.EASY,true);
    l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,s);l.setDayTime(6000);l.setWeatherParameters(24000,0,false,false);
    var yard=new Settlement.Building(Settlement.childId(st.id(),"building/probe-yard"),"livestock",4,0,-80);st.addBuilding(yard);yardId=yard.id();
    var o=BuildingPlacement.origin(e,yard);
    for(int x=-4;x<21;x++)for(int z=-6;z<29;z++){var g=o.offset(x,0,z);l.setBlock(g.below(),Blocks.DIRT.defaultBlockState(),3);l.setBlock(g,Blocks.GRASS_BLOCK.defaultBlockState(),3);for(int y=1;y<12;y++)l.setBlock(g.above(y),Blocks.AIR.defaultBlockState(),3);}
    for(var cell:BuildingPlacement.layout(e,yard,BuildingTiers.layoutId("livestock",1)).entrySet())l.setBlock(cell.getKey(),cell.getValue(),3);
    // The keeper of the yard: it is he who takes the animals in at the pens.
    var chest=LogisticsRoutes.chest(l,e,yard);if(chest!=null){chest.setItem(0,new net.minecraft.world.item.ItemStack(Items.WHEAT,64));}
    var room=new Settlement.Home(Settlement.childId(st.id(),"home/probe-keeper"),1,2,true);st.addHome(room);
    var keeper=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);st.admit(keeper,room.id());st.assign(keeper.id(),Profession.LIVESTOCK_FARMER,yard.id());
    var npc=org.villageastra.VillageAstra.RESIDENT.get().create(l);npc.bind(st.id(),st.resident(keeper.id()));
    var door=BuildingPlacement.at(e,yard,5,1,-1);npc.moveTo(door.getX()+.5,door.getY(),door.getZ()+.5,0,0);l.addFreshEntity(npc);keeperId=keeper.id();
    SettlementData.get(s).setDirty();
    LogUtils.getLogger().info("ASTRA_ANIMAL fixture: a livestock yard of level I, no kind of animal open (sheep {}, cow {}, chicken {})",AnimalUnlocks.unlocked(l,e,"sheep"),AnimalUnlocks.unlocked(l,e,"cow"),AnimalUnlocks.unlocked(l,e,"chicken"));ready=true;
   }catch(Exception ex){failure=ex.toString();}});}
  // The village posts the card on its own; the player takes it and gets a chart with the cross.
  else if(phase==1&&ready&&ticks%20==0){s.execute(()->{var l=s.overworld();var e=entry(s);if(phase!=1||card(s)!=null&&card(s).getString("state").equals(Quests.TAKEN))return;
    for(var raw:Quests.board(l,e.settlement().id()).getList("quests",Tag.TAG_COMPOUND)){var q=(CompoundTag)raw;if(q.getString("template").equals(AnimalQuests.FLOCK)&&q.getString("state").equals(Quests.OPEN))quest=q.getUUID("id");}
    progress="clock="+SettlementData.get(s).clock().ticks()+" card="+quest;
    if(quest!=null){var p=player(s);var took=Quests.take(p,e.settlement().id(),quest);if(!took.equals("ok")){failure="The card was not taken: "+took;return;}
     boolean cross=p.getInventory().items.stream().anyMatch(x->x.is(Items.FILLED_MAP)&&x.hasTag()&&x.getTag().getList("Decorations",Tag.TAG_COMPOUND).stream().anyMatch(d->((CompoundTag)d).getString("id").equals("astra_quest")));
     if(!cross){failure="The chart has no cross";return;}
     // The land there may not be in the world yet: the player arrives a little above the village's own ground level and lands.
     var site=BlockPos.of(card(s).getLong("site"));p.teleportTo(l,site.getX()+8.5,e.center().getY()+3,site.getZ()+8.5,135,20);flag=true;
     LogUtils.getLogger().info("ASTRA_ANIMAL the village posted the flock card by itself; taken, the chart carries the cross; the player goes to it");}});
   if(flag){phase=2;ticks=0;flag=false;}else if(ticks>3000)throw new IllegalStateException("No flock card: "+progress);}
  // The camp is built as its land comes into the world: the band stands at its fire, the flock in the pen. The player beats the band.
  else if(phase==2&&ticks%10==0){s.execute(()->{var l=s.overworld();var site=site(s);var p=player(s);if(site==null||phase!=2){progress="waiting for the camp";return;}
    var band=new ArrayList<net.minecraft.world.entity.Mob>();for(var x:site.getList("mobs",Tag.TAG_INT_ARRAY))if(l.getEntity(NbtUtils.loadUUID(x)) instanceof net.minecraft.world.entity.Mob m&&m.isAlive())band.add(m);
    progress="camp built, band="+band.size()+" flock="+flock(l,site).size()+" status="+card(s).getString("status");
    if(!band.isEmpty()&&!flag){flag=true;AnimalQuestProbe.wolves=band.size();LogUtils.getLogger().info("ASTRA_ANIMAL the rustlers' camp: {} of the band at the fire, {} sheep in the pen",band.size(),flock(l,site).size());}
    if(flag&&shot)for(var m:band){p.teleportTo(l,m.getX()+1.5,m.getY(),m.getZ(),90,10);m.hurt(l.damageSources().playerAttack(p),100);}});
   if(flag&&!shot&&ticks>40){shot=true;capture(mc,"camp");}
   if(flag&&shot&&progress.contains("band=0")){phase=3;ticks=0;flag=false;LogUtils.getLogger().info("ASTRA_ANIMAL the band is beaten: {}",progress);}
   else if(ticks>2400)throw new IllegalStateException("The camp or its band did not come: "+progress);}
  // The pen's gate is opened as a player opens it; the board says the flock is free.
  else if(phase==3&&ticks%10==0){s.execute(()->{var l=s.overworld();var site=site(s);var p=player(s);var gate=BlockPos.of(site.getLong("gate"));
    if(!l.getBlockState(gate).getValue(BlockStateProperties.OPEN)){p.teleportTo(l,gate.getX()+.5,gate.getY(),gate.getZ()+.5,0,10);l.setBlock(gate,l.getBlockState(gate).setValue(BlockStateProperties.OPEN,true),3);}
    progress="status="+card(s).getString("status");if(card(s).getString("status").equals("drive"))flag=true;});
   if(flag){capture(mc,"freed");phase=4;ticks=0;flag=false;LogUtils.getLogger().info("ASTRA_ANIMAL the pen is open, the flock free: {}",progress);}
   else if(ticks>1200)throw new IllegalStateException("The board did not free the flock: "+progress);}
  // The bellwether and the ewe on leads, home to the yard, through the sheep pen's gate, let go inside.
  else if(phase==4&&ticks==5){s.execute(()->{var l=s.overworld();var site=site(s);var p=player(s);var e=entry(s);var yard=yard(e);
    var pen=LivestockPens.pen(1);var gate=LivestockPens.at(e,yard,pen.gate());
    // The player steps into the pen with wheat: the bellwether and the ewe follow it out through the gate and home.
    var penAt=BlockPos.of(site.getLong("pen"));p.teleportTo(l,penAt.getX()+.5,penAt.getY(),penAt.getZ()+.5,0,10);
    LED.clear();
    // The leads from the rustlers' chest go on the bellwether and the ewe, and wheat from it stays in the player's hand.
    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(Items.WHEAT,16));
    for(var role:List.of("bell","ewe")){var a=sheep(l,site,role);if(a==null){failure="The "+role+" is gone";return;}a.setLeashedTo(p,true);LED.add(a.getUUID());}
    int out=pen.gateEast()?1:-1;var g=pen.gate();
    // Out of the camp's pen by its gate, then home round the village, not through it: along an arc well outside it to the yard's side, then down the lane.
    ROUTE.clear();var c=e.center();var campGate=BlockPos.of(site.getLong("gate"));var facing=Direction.byName(site.getString("facing"));
    var mouth=Vec3.atBottomCenterOf(campGate).add(facing.getStepX()*3,0,facing.getStepZ()*3);ROUTE.add(Vec3.atBottomCenterOf(campGate).add(-facing.getStepX()*1.5,0,-facing.getStepZ()*1.5));ROUTE.add(Vec3.atBottomCenterOf(campGate));ROUTE.add(mouth);
    // Straight on down the camp's middle, past its tents, before turning for home.
    var clear=Vec3.atBottomCenterOf(campGate).add(facing.getStepX()*10,0,facing.getStepZ()*10);ROUTE.add(clear);var from=clear;var behind=local(e,yard,8,28);double r=70,a0=Math.atan2(from.z-c.getZ(),from.x-c.getX()),a1=Math.atan2(behind.z-c.getZ(),behind.x-c.getX());
    double da=Math.atan2(Math.sin(a1-a0),Math.cos(a1-a0));int n=Math.max(1,(int)Math.ceil(Math.abs(da)/(Math.PI/12)));
    for(int i=0;i<=n;i++){double a=a0+da*i/n;ROUTE.add(ground(l,c.getX()+Math.cos(a)*r,c.getZ()+Math.sin(a)*r));}
    // Into the yard from its open back, down the lane between the pens to the sheep pen's gate (the byre stands across the front).
    ROUTE.add(behind);ROUTE.add(local(e,yard,8,g.getZ()));ROUTE.add(local(e,yard,g.getX()+out*2,g.getZ()));
    // The gate stays shut: the sheep are let go in the lane and it is the keeper who takes them in.
    });}
  else if(phase==4&&ticks>5){s.execute(()->{var l=s.overworld();var p=player(s);var e=entry(s);var q=card(s);
    if(!LED.isEmpty()){held(l,p);if(walk(l,p)){
      // In the lane before the pen the leads are let go: from here the keeper takes them in, and the player only steps aside.
      boolean here=true;
      for(var id:LED)if(l.getEntity(id) instanceof Animal a&&a.distanceTo(p)>3){here=false;if(a.getNavigation().isDone())a.getNavigation().moveTo(p,1.1);}
      if(!here)return;
      for(var id:LED)if(l.getEntity(id) instanceof Animal a){LogUtils.getLogger().info("ASTRA_ANIMAL let go in the lane at {}",a.blockPosition().toShortString());a.dropLeash(true,false);}
      LED.clear();p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
      // The player steps out of the pen and shuts the gate: the sheep, no longer lured, stay in.
      var g2=LivestockPens.pen(1).gate();int o2=LivestockPens.pen(1).gateEast()?1:-1;var aside=local(e,yard(e),g2.getX()+o2*5,g2.getZ()+4);p.teleportTo(l,aside.x,aside.y,aside.z,0,10);
      LogUtils.getLogger().info("ASTRA_ANIMAL the pair stands in the lane; the keeper takes them in");}}
    var led=new StringBuilder();for(var id:LED)if(l.getEntity(id) instanceof Animal a)led.append(a.blockPosition().toShortString()).append(" d=").append(String.format(java.util.Locale.ROOT,"%.1f",a.distanceTo(p))).append(a.getLeashHolder()==null?" free":" tied").append("; ");
    String work="";var npc=keeperId==null?null:l.getEntity(keeperId);if(npc instanceof ResidentEntity r)work=r.workStatus()+" at "+r.blockPosition().toShortString();
    var pen=new StringBuilder();var site2=site(s);if(site2!=null)for(var role:List.of("bell","ewe")){var a=sheep(l,site2,role);if(a!=null)pen.append(role).append(a.blockPosition().toShortString()).append(AnimalYard.inPen(l,e,a,"sheep")?" in; ":" out; ");}
    progress="card="+(q==null?"none":q.getString("state")+" "+q.getInt("progress")+"/"+q.getInt("target"))+" sheep open="+AnimalUnlocks.unlocked(l,e,"sheep")+" keeper="+work+" sheep=["+pen+"] route="+ROUTE.size()+" led=["+led+"]";
    if(q!=null&&q.getString("state").equals(Quests.DONE)&&AnimalUnlocks.unlocked(l,e,"sheep"))flag=true;});
   if(flag){capture(mc,"pen");
    LogUtils.getLogger().info("ASTRA_ANIMAL VERIFIED posted by the village, chart with the cross, {} rustlers beaten, the pen opened, the pair led home on leads and taken in by the keeper: sheep open, card done; {}; reload=false",wolves,progress);
    mc.stop();phase=5;}
   else if(ticks>12000)throw new IllegalStateException("The pair did not make it home: "+progress);}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_ANIMAL FAILED",ex);Minecraft.getInstance().stop();}}
}
