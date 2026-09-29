package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-075: the adventures are real. The site is built out in the world, the seam is real ore, the cage really holds the captive
 *  until its bars fall and the chief is down, the wounded walk only after real care, the barrow stays sealed until it is broken
 *  into, and every one of them pays its coin and its reputation exactly once. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class AdventureQuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center,Settlement.Building office){}
 private static final int AWAY=26;
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-6;x<20;x++)for(int z=-6;z<20;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var hall=new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0);s.addBuilding(hall);
  var office=new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0);s.addBuilding(office);
  var lab=new Settlement.Building(Settlement.childId(s.id(),"building/laboratory"),"laboratory",16,0,0);s.addBuilding(lab);
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  l.setBlock(center.offset(9,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  l.setBlock(center.offset(17,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center,office);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  // AD-097: the adventurer of these tests has already earned the trust the far errands ask for.
  PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Adventures.CAPTIVE));
  p.setPos(t.center.getX()+2,t.center.getY()+1,t.center.getZ()+2);return p;
 }
 /** Prepares the far clearing the expeditioner will report, and reports it: a real lead at a real place.
  *  Only the cells one design needs are touched, so a test never writes into the plot of its neighbour. */
 private static BlockPos lead(Town t,String kind,long now){return lead(t,kind,now,0,0);}
 private static BlockPos lead(Town t,String kind,long now,int sector,int shift){
  // The clearing is prepared on ground nobody else holds: the shared test world has other settlements, and a site is never built on their land.
  var at=new BlockPos(t.center.getX()-AWAY+shift,t.center.getY()+1,t.center.getZ()+2);
  for(int step=0;step<16;step++){var candidate=at.offset(-16*step,0,0);boolean free=true;
   for(int dx=-8;dx<=8&&free;dx++)for(int dz=-8;dz<=8&&free;dz++)for(int dy=-10;dy<=8&&free;dy++)
    if(OwnershipEvents.disallowedPlacement(t.l,candidate.offset(dx,dy,dz)))free=false;
   if(free){at=candidate;break;}}
  clearing(t,at,QuestSites.half(kind)+1,QuestSites.height(kind)+2,kind.equals(QuestSites.ADIT));
  var chest=LogisticsRoutes.chest(t.l,t.e,t.office);chest.setItem(0,new ItemStack(Items.PAPER,8));chest.setItem(1,new ItemStack(Items.BREAD,8));
  var report=Expeditions.report(t.l,t.e,t.office,at,sector,now);
  if(report==null)throw new GameTestAssertException("The prepared clearing was not reported as a lead");
  return at;
 }
 private static void clearing(Town t,BlockPos at,int half,int height,boolean rock){
  for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++){
   t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-1,at.getZ()+dz),Blocks.GRASS_BLOCK.defaultBlockState(),2);
   for(int y=0;y<=height;y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()+y,at.getZ()+dz),Blocks.AIR.defaultBlockState(),2);
   if(rock)for(int y=2;y<=9;y++)t.l.setBlock(new BlockPos(at.getX()+dx,at.getY()-y,at.getZ()+dz),Blocks.STONE.defaultBlockState(),2);
  }
 }
 private static int count(Town t,BlockPos origin,int half,int from,int to,Block block){
  int found=0;
  for(int dx=-half-1;dx<=half+1;dx++)for(int dz=-half-1;dz<=half+1;dz++)for(int dy=from;dy<=to;dy++)
   if(t.l.getBlockState(origin.offset(dx,dy,dz)).is(block))found++;
  return found;
 }
 private static BlockPos stockChest(Town t){return LogisticsRoutes.position(t.e,Workshops.hall(t.e));}
 // ---------------------------------------------------------------- a far seam
 @GameTest(template="empty",timeoutTicks=200) public static void aditExposesARealSeamAndTheOreIsHandedOverOnce(GameTestHelper h){
  var t=town(h);lead(t,QuestSites.ADIT,400);var p=player(t,"LodeMiner");
  var q=Adventures.post(t.l,t.e,Adventures.LODE,7200);
  h.assertTrue(q!=null&&q.getInt("target")==12&&q.contains("site"),"The village posts the seam it needs: "+q);
  var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
  h.assertTrue(origin.distSqr(t.center)>=(long)QuestSites.MIN_FROM_VILLAGE*QuestSites.MIN_FROM_VILLAGE,"The site keeps its distance from the village: "+origin.distSqr(t.center));
  h.assertTrue(count(t,origin,3,0,4,Blocks.SPRUCE_LOG)>=12,"The headframe really stands: logs="+count(t,origin,3,0,4,Blocks.SPRUCE_LOG));
  h.assertTrue(count(t,origin,3,-7,0,Blocks.LADDER)>=6&&count(t,origin,3,-7,4,Blocks.LANTERN)>=1,
    "A ladder and light go down the shaft: ladders="+count(t,origin,3,-7,0,Blocks.LADDER)+" lanterns="+count(t,origin,3,-7,4,Blocks.LANTERN)+" at "+origin.toShortString()+" stone="+count(t,origin,3,-7,0,Blocks.STONE));
  boolean open=false;
  for(int d=2;d<=5;d++)for(var around:net.minecraft.core.Direction.Plane.HORIZONTAL)if(t.l.getBlockState(origin.below(d).relative(around)).isAir())open=true;
  h.assertTrue(open,"The shaft beside the ladder is really dug out");
  h.assertTrue(count(t,origin,3,-1,1,Blocks.BARREL)==1&&count(t,origin,3,0,1,Blocks.CAMPFIRE)==1,"The lean-to has its kit and its fire");
  int seam=QuestSites.seamLeft(t.l,site);
  h.assertTrue(seam==14,"Fourteen ore blocks are exposed in the wall: "+seam);
  var first=BlockPos.of(((net.minecraft.nbt.LongTag)site.getList("seam",net.minecraft.nbt.Tag.TAG_LONG).get(0)).getAsLong());
  t.l.destroyBlock(first,false);
  h.assertTrue(QuestSites.seamLeft(t.l,site)==13,"A mined block really leaves the seam");
  var ore=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(q.getString("item"))),12);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("empty"),"Nothing is handed over with empty hands");
  p.getInventory().add(ore.copy());
  // QUEST-002: the village asked for the ore of this seam. With one block of it mined, one is what it takes, however much is carried.
  p.setPos(stockChest(t).getX()+.5,stockChest(t).getY(),stockChest(t).getZ()+1.5);
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"What the seam gave is taken");
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==1,"One block mined, one ore counted: "+Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress"));
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("not_from_seam"),"and no more until the wall gives more");
  // The rest of the errand's count really dug out of the wall.
  for(int i=1;i<12;i++)t.l.destroyBlock(BlockPos.of(((net.minecraft.nbt.LongTag)site.getList("seam",net.minecraft.nbt.Tag.TAG_LONG).get(i)).getAsLong()),false);
  h.assertTrue(QuestSites.seamLeft(t.l,site)==2,"Twelve of the fourteen are out of the wall: "+QuestSites.seamLeft(t.l,site));
  p.setPos(t.center.getX()-AWAY,t.center.getY()+1,t.center.getZ());
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("far"),"The ore is handed over at the village, not at the mine");
  p.setPos(stockChest(t).getX()+.5,stockChest(t).getY(),stockChest(t).getZ()+1.5);
  h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"Handed over");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));var stock=LogisticsRoutes.chest(t.l,t.e,Workshops.hall(t.e));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&stock.countItem(ore.getItem())==12&&p.getInventory().countItem(ore.getItem())==0,
   "The ore really moved into the village stock: "+stock.countItem(ore.getItem()));
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),"Paid exactly once");
  h.assertTrue(PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score()>=q.getLong("reputation"),"The reputation is credited");
  // AD-080: the board tells the player which way and how far, and never the exact numbers.
  var view=new net.minecraft.nbt.CompoundTag();view.putUUID("village",t.s.id());Quests.addView(p,view);
  var rows=view.getList("quests",net.minecraft.nbt.Tag.TAG_COMPOUND);
  h.assertTrue(rows.stream().noneMatch(raw->((net.minecraft.nbt.CompoundTag)raw).contains("site")),"No coordinates are sent to the board: "+rows);
  h.succeed();
 }
 // ---------------------------------------------------------------- the captive of the stockade
 @GameTest(template="empty",timeoutTicks=200) public static void stockadeHoldsTheCaptiveUntilTheBarsFallAndTheChiefIsDown(GameTestHelper h){
  var t=town(h);
  {
   lead(t,QuestSites.STOCKADE,1200);var p=player(t,"CaptiveRescuer");
   if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(Adventures.post(t.l,t.e,Adventures.CAPTIVE,14400)==null,"A peaceful world gets no such site");h.succeed();return;}
   var q=Adventures.post(t.l,t.e,Adventures.CAPTIVE,14400);
   h.assertTrue(q!=null&&q.getInt("target")==1,"The village asks for the captive back: "+q);
   var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
   h.assertTrue(count(t,origin,5,0,2,Blocks.SPRUCE_LOG)>=60,"The palisade really stands: "+count(t,origin,5,0,2,Blocks.SPRUCE_LOG));
   h.assertTrue(count(t,origin,5,0,0,Blocks.SPRUCE_FENCE_GATE)==3,"One gate, on the side of the village");
   h.assertTrue(count(t,origin,5,-1,2,Blocks.IRON_BARS)>=20,"The cage is made of real bars: "+count(t,origin,5,-1,2,Blocks.IRON_BARS));
   h.assertTrue(count(t,origin,5,4,5,Blocks.SPRUCE_PLANKS)>=9&&count(t,origin,5,5,5,Blocks.BLACK_BANNER)==1,"The watchtower carries the black flag");
   h.assertTrue(count(t,origin,5,0,2,Blocks.BLACK_WOOL)>=9&&count(t,origin,5,0,0,Blocks.BARREL)==1,"The tent of the chief holds the spoils");
   var people=QuestSites.people(site);
   h.assertTrue(people.size()==1&&t.l.getEntity(people.get(0)) instanceof ResidentEntity,"One real captive waits in the cage");
   var captive=(ResidentEntity)t.l.getEntity(people.get(0));
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   // AD-080: taking a quest hands out a real chart, and the cross on it stands on the place itself.
   var chart=p.getInventory().items.stream().filter(x->x.is(Items.FILLED_MAP)).findFirst().orElse(ItemStack.EMPTY);
   h.assertTrue(!chart.isEmpty()&&chart.hasTag()&&chart.getTag().contains("Decorations"),"The board draws a chart for the quest: "+chart);
   var marks=chart.getTag().getList("Decorations",net.minecraft.nbt.Tag.TAG_COMPOUND);
   // AD-105: the cross is the quest's own mark; the village may carry a second, smaller one.
   var cross=marks.stream().map(x->(net.minecraft.nbt.CompoundTag)x).filter(x->x.getString("id").equals("astra_quest")).findFirst().orElse(null);
   h.assertTrue(cross!=null&&cross.getByte("type")==net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X.getIcon()
    &&(int)cross.getDouble("x")==origin.getX()&&(int)cross.getDouble("z")==origin.getZ(),
    "The cross stands on the place: "+marks+" for "+origin.toShortString());
   h.assertTrue(Adventures.hold(captive).equals("caged"),"Behind the bars nobody walks out: "+Adventures.hold(captive));
   var column=BlockPos.of(((net.minecraft.nbt.LongTag)site.getList("cage",net.minecraft.nbt.Tag.TAG_LONG).get(0)).getAsLong());
   t.l.destroyBlock(column,false);
   h.assertTrue(Adventures.hold(captive).equals("caged"),"One bar is not a gap");
   t.l.destroyBlock(column.above(),false);
   h.assertTrue(QuestSites.cageOpened(t.l,site)&&Adventures.hold(captive).equals("chief"),"With the gap open the chief is what holds them: "+Adventures.hold(captive));
   // A gap is only a way out when there is somewhere to step on both sides of it.
   int walkable=0;
   for(var around:net.minecraft.core.Direction.Plane.HORIZONTAL){var next=column.relative(around);
    if(t.l.getBlockState(next).isAir()&&t.l.getBlockState(next.above()).isAir()&&t.l.getBlockState(next.below()).isFaceSturdy(t.l,next.below(),net.minecraft.core.Direction.UP))walkable++;}
   h.assertTrue(walkable>=2,"The broken column really joins the cage to the yard: walkable neighbours "+walkable);
   var chief=t.l.getEntity(site.getUUID("chief"));
   h.assertTrue(chief instanceof LivingEntity&&chief.isAlive(),"The chief of the band is a real mob");
   ((LivingEntity)chief).kill();
   h.assertTrue(QuestSites.chiefDown(t.l,site)&&Adventures.hold(captive).isEmpty(),"Cage broken and chief down: the captive can walk");
   captive.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);
   Quests.companions(t.l,t.e,14500);
   var arrived=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
   h.assertTrue(arrived.getInt("progress")==1,"The captive really arrived: "+arrived);
   h.assertTrue(Quests.complete(p,t.s.id(),q.getUUID("id")),"The escort closes the quest");
   var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
   h.assertTrue(done.getString("state").equals(Quests.DONE),"Done: "+done.getString("state"));
   h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")
    &&PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score()>=q.getLong("reputation"),"Coin and reputation paid");
   h.assertTrue(Camps.guest(t.l,captive.getUUID())!=null,"The freed captive waits for a bed like any other newcomer");
   h.succeed();
  }
 }
 // ---------------------------------------------------------------- the wrecked expedition
 @GameTest(template="empty",timeoutTicks=200) public static void wreckedExpeditionWalksHomeOnlyAfterRealCare(GameTestHelper h){
  var t=town(h);
  {
   lead(t,QuestSites.WRECK,2000);var p=player(t,"LostFinder");
   if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(Adventures.post(t.l,t.e,Adventures.LOST,21600)==null,"A peaceful world gets no such site");h.succeed();return;}
   var q=Adventures.post(t.l,t.e,Adventures.LOST,21600);
   h.assertTrue(q!=null&&q.getInt("target")==3,"Three survivors are asked for: "+q);
   var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
   h.assertTrue(count(t,origin,4,0,0,Blocks.CAMPFIRE)==1&&!t.l.getBlockState(origin).getValue(BlockStateProperties.LIT),"Their fire went out");
   h.assertTrue(count(t,origin,4,0,3,Blocks.LANTERN)==1&&count(t,origin,4,0,2,Blocks.COBBLESTONE_WALL)==1,"The cairn still carries its lantern");
   h.assertTrue(count(t,origin,4,0,2,Blocks.WHITE_WOOL)>=9&&count(t,origin,4,0,1,Blocks.COBWEB)>=1,"One tent stands, the other came down");
   h.assertTrue(count(t,origin,4,0,1,Blocks.BARREL)>=3,"Their crates are still there");
   var people=QuestSites.people(site);
   h.assertTrue(people.size()==3,"Three real survivors: "+people.size());
   var first=(ResidentEntity)t.l.getEntity(people.get(0));var second=(ResidentEntity)t.l.getEntity(people.get(1));
   h.assertTrue(first.isNoAi()&&first.getHealth()<first.getMaxHealth(),"A wounded survivor really cannot walk");
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   h.assertTrue(Adventures.hold(first).equals("injured"),"Untreated, they stay where they are");
   p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
   h.assertTrue(Adventures.treat(p,first,InteractionHand.MAIN_HAND).equals("treat_needed"),"Empty hands treat nobody");
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,2));
   h.assertTrue(Adventures.treat(p,first,InteractionHand.MAIN_HAND).equals("treat_more")&&p.getMainHandItem().getCount()==1,"Food helps, and it is really spent");
   p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(VillageAstra.BANDAGE.get(),2));
   h.assertTrue(Adventures.treat(p,first,InteractionHand.MAIN_HAND).equals("treated")&&p.getMainHandItem().getCount()==1,"A bandage puts one of them back on their feet");
   h.assertTrue(!first.isNoAi()&&first.getHealth()==first.getMaxHealth()&&Adventures.hold(first).isEmpty(),"The treated survivor can walk");
   h.assertTrue(Adventures.treat(p,second,InteractionHand.MAIN_HAND).equals("treated"),"The second one is treated too");
   first.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);
   second.moveTo(t.center.getX()+3.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);
   Quests.companions(t.l,t.e,21700);
   var partial=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
   h.assertTrue(partial.getInt("progress")==2&&partial.getString("state").equals(Quests.TAKEN),"Only those who really arrived count: "+partial.getInt("progress"));
   h.assertTrue(Quests.complete(p,t.s.id(),q.getUUID("id")),"A rescue with survivors can be closed");
   long coins=q.getLong("coins")*2/3;
   h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==coins,"The pay is for the two who came: "+p.getInventory().countItem(VillageAstra.ZINDBO.get())+" of "+q.getLong("coins"));
   h.assertTrue(!Quests.complete(p,t.s.id(),q.getUUID("id")),"Paid exactly once");
   h.succeed();
  }
 }
 // ---------------------------------------------------------------- the barrow and its volume
 @GameTest(template="empty",timeoutTicks=200) public static void barrowStaysSealedAndOnlyItsOwnVolumeIsHandedOver(GameTestHelper h){
  var t=town(h);
  {
   lead(t,QuestSites.BARROW,2800);var p=player(t,"BarrowOpener");
   t.s.civilization().begin("cartography");
   if(t.l.getDifficulty()==Difficulty.PEACEFUL){h.assertTrue(Adventures.post(t.l,t.e,Adventures.RELIC,28800)==null,"A peaceful world gets no such site");h.succeed();return;}
   var q=Adventures.post(t.l,t.e,Adventures.RELIC,28800);
   h.assertTrue(q!=null&&q.getInt("target")==1,"The scholars ask for the volume: "+q);
   var site=QuestSites.site(t.l,q.getUUID("id"));var origin=QuestSites.origin(site);
   h.assertTrue(count(t,origin,4,0,1,Blocks.MOSSY_COBBLESTONE)>=20&&count(t,origin,4,2,3,Blocks.MOSSY_STONE_BRICKS)>=20,"The mound is really raised");
   h.assertTrue(count(t,origin,4,0,1,Blocks.CRACKED_STONE_BRICKS)==2&&!QuestSites.sealBroken(t.l,site),"The passage is walled up");
   h.assertTrue(count(t,origin,4,0,1,Blocks.CHEST)==1&&count(t,origin,4,0,1,Blocks.SOUL_LANTERN)==1,"The chamber holds the coffin chest under a soul lantern");
   var chest=(net.minecraft.world.Container)t.l.getBlockEntity(BlockPos.of(site.getLong("chest")));
   var volume=chest.getItem(0);
   h.assertTrue(volume.is(VillageAstra.RESEARCH_VOLUME.get())&&volume.hasTag()&&volume.getTag().getString(QuestSites.RELIC).equals(q.getUUID("id").toString()),
    "The volume in the barrow carries the quest it belongs to: "+volume);
   h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
   p.setPos(t.center.getX()+17,t.center.getY()+1,t.center.getZ()+3);
   p.getInventory().add(new ItemStack(VillageAstra.RESEARCH_VOLUME.get(),1));
   h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("no_relic"),"A volume from the village is not the volume of the barrow");
   var seal=BlockPos.of(((net.minecraft.nbt.LongTag)site.getList("seal",net.minecraft.nbt.Tag.TAG_LONG).get(0)).getAsLong());
   t.l.destroyBlock(seal,false);
   h.assertTrue(QuestSites.sealBroken(t.l,site),"The barrow is really broken into");
   p.getInventory().add(volume.copy());chest.setItem(0,ItemStack.EMPTY);
   long before=t.s.civilization().progress();
   h.assertTrue(Quests.handOver(p,t.s.id(),q.getUUID("id")).equals("ok"),"Handed over to the scholars");
   var lab=LogisticsRoutes.chest(t.l,t.e,t.s.buildings().stream().filter(b->b.type().equals("laboratory")).findFirst().orElseThrow());
   h.assertTrue(lab.countItem(VillageAstra.RESEARCH_VOLUME.get())==1,"The volume really lies in the laboratory");
   h.assertTrue(t.s.civilization().progress()==before+Adventures.RESEARCH_TICKS,"The scholars really advanced: "+t.s.civilization().progress());
   h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getString("state").equals(Quests.DONE)
    &&p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&!Quests.complete(p,t.s.id(),q.getUUID("id")),"Paid exactly once");
   h.succeed();
  }
 }
 // ---------------------------------------------------------------- how the board offers them
 @GameTest(template="empty",timeoutTicks=300) public static void adventuresKeepToTheirLimitAndGiveTheLeadBackWhenDoneWith(GameTestHelper h){
  var t=town(h);
  {
   var anchor=lead(t,QuestSites.ADIT,3600);
   clearing(t,anchor.offset(-10,0,0),QuestSites.half(QuestSites.ADIT)+1,QuestSites.height(QuestSites.ADIT)+2,true);
   h.assertTrue(Adventures.needsFighters(Adventures.CAPTIVE)&&Adventures.needsFighters(Adventures.LOST)&&!Adventures.needsFighters(Adventures.LODE),
    "Only the adventures that stand on fighting ask for monsters");
   if(t.l.getDifficulty()==Difficulty.PEACEFUL)h.assertTrue(Adventures.post(t.l,t.e,Adventures.CAPTIVE,36000)==null,"A world without monsters gets no bandit stockade");
   var lode=Adventures.post(t.l,t.e,Adventures.LODE,36000);
   h.assertTrue(lode!=null,"A seam needs no monsters");
   h.assertTrue(Adventures.post(t.l,t.e,Adventures.LODE,36000)==null,"The same adventure is never posted twice at once");
   var used=Expeditions.leads(t.l,t.s.id()).stream().filter(x->BlockPos.of(x.getLong("pos")).equals(anchor)).findFirst().orElseThrow();
   h.assertTrue(used.getBoolean("used"),"The lead the site stands on is spent");
   // The world of a test batch is shared, so the fixture looks for a pocket that really has room for the second site.
   BlockPos second=null;
   for(var offset:new int[][]{{0,12},{0,-16},{-12,0}}){
    var candidate=anchor.offset(offset[0],0,offset[1]);
    clearing(t,candidate,QuestSites.half(QuestSites.WRECK)+1,QuestSites.height(QuestSites.WRECK)+2,false);
    if(QuestSites.place(t.l,t.e,candidate,QuestSites.WRECK)!=null){second=candidate;break;}
   }
   h.assertTrue(second!=null,"The fixture found room for a second site");
   h.assertTrue(Expeditions.report(t.l,t.e,t.office,second,1,36000)!=null,"A second sector is scouted");
   var free=Expeditions.lead(t.l,t.s.id(),Expeditions.CAMP,true);
   var spot=free==null?null:QuestSites.place(t.l,t.e,BlockPos.of(free.getLong("pos")),QuestSites.WRECK);
   h.assertTrue(Adventures.post(t.l,t.e,Adventures.LOST,36000)!=null,
    "A second adventure fits on the board: lead="+free+" spot="+spot+" difficulty="+t.l.getDifficulty());
   h.assertTrue(Adventures.post(t.l,t.e,Adventures.RELIC,36000)==null,"No more than two adventures are open at once");
   // A missed deadline closes the site, and closing it really gives the lead back: a village never runs out of places.
   Quests.tick(t.l,t.e,36000+Quests.deadline(Adventures.LODE)+1);
   var failed=Quests.quest(t.l,t.s.id(),lode.getUUID("id"));
   h.assertTrue(failed.getString("state").equals(Quests.FAILED),"The unclaimed seam quest expires: "+failed.getString("state"));
   var site=QuestSites.site(t.l,lode.getUUID("id"));
   h.assertTrue(site.getString("state").equals("closed"),"The finished site is closed: "+site.getString("state"));
   var back=Expeditions.leads(t.l,t.s.id()).stream().filter(x->BlockPos.of(x.getLong("pos")).equals(anchor)).findFirst().orElseThrow();
   h.assertTrue(!back.getBoolean("used"),"The lead is free for the next adventure");
   h.assertTrue(QuestSites.taken(t.l,t.s.id()).size()==2,"Every site ever built stays on the map of taken places");
   h.succeed();
  }
 }
}
