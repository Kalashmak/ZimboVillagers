package org.villageastra.gametest;
import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-124: the office's server data — daily samples and their trend (never the global clock: GameTests run side by side), a broken history
 *  set aside, the laboratory's volumes and scientists in the research view, the 'remove from the queue' order, and the one availability rule. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OfficeHistoryGameTests {
 private record Bench(ServerLevel l,Settlement s,SettlementData.Entry e,Map<String,Settlement.Building> buildings){
  OwnedChestEntity chest(String type){return LogisticsRoutes.chest(l,e,buildings.get(type));}
 }
 /** OfficeOverviewGameTests' bench: each station on its own lot with its real chest, one house of two beds. */
 private static Bench bench(GameTestHelper h,String... types){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(2,3,2));var s=new Settlement(UUID.randomUUID());var map=new LinkedHashMap<String,Settlement.Building>();int i=0;
  for(var type:types){var b=new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,(i%3)*14,0,(i/3)*12);s.addBuilding(b);map.put(type,b);
   var chest=center.offset(b.x()+1,b.y()+1,b.z()+4);l.setBlock(chest.below(),Blocks.STONE.defaultBlockState(),2);l.setBlock(chest,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);i++;}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"house/office"),1,2,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);OfficeOverview.clear();
  return new Bench(l,s,e,map);
 }
 private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}}
 private static void done(Bench b){
  delete(BookResearch.path(b.l,b.s.id()));var h=OfficeHistory.path(b.l.getServer(),b.s.id());delete(h);delete(h.resolveSibling(h.getFileName()+".bad"));
  SettlementData.get(b.l.getServer()).remove(b.s.id());OfficeOverview.clear();
 }
 private static OfficeHistory.Sample sample(long day,int residents,long coins,long paid){return new OfficeHistory.Sample(day,residents,4,30,10,coins,paid,0);}
 /** One sample a day: a second reading on the same day adds none, the trend of the next day is the difference over a span of one,
  *  and ten days keep the newest eight. The office's own reading on the current day starts a history with no trend yet. */
 @GameTest(template="empty",timeoutTicks=100) public static void historySamplesOncePerDayAndGivesTrend(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   long d=1000;var history=OfficeHistory.empty(b.s.id());
   h.assertTrue(OfficeHistory.record(history,d,sample(d,3,100,0)),"The first sample of day D is kept");
   h.assertTrue(!OfficeHistory.record(history,d,sample(d,5,500,9)),"A second reading on day D adds nothing");
   var now=sample(d+1,4,160,7);h.assertTrue(OfficeHistory.record(history,d+1,now),"Day D+1 is kept");
   var trend=OfficeHistory.trend(history,d+1,now);
   h.assertTrue(trend.getInt("span")==1&&trend.getLong("coins")==60&&trend.getInt("residents")==1&&trend.getLong("paid")==7,"One day: +60 coins, +1 resident, +7 volumes: "+trend);
   h.assertTrue(OfficeHistory.trend(history,d,sample(d,3,100,0)).getInt("span")==0,"On day D nothing is a day old yet");
   for(long day=d+2;day<=d+10;day++)OfficeHistory.record(history,day,sample(day,4,160+day-d,7));
   var list=history.getList("samples",Tag.TAG_COMPOUND);
   h.assertTrue(list.size()==OfficeHistory.KEEP&&list.getCompound(0).getLong("day")==d+3&&list.getCompound(list.size()-1).getLong("day")==d+10,"The newest eight days are kept: "+list.size()+" from "+list.getCompound(0).getLong("day"));
   h.assertTrue(OfficeHistory.trend(history,d+10,sample(d+10,4,170,7)).getInt("span")==7,"The trend runs from the oldest kept day at least a day old");
   var o=OfficeOverview.read(b.l.getServer(),b.e);
   h.assertTrue(o.contains("trend")&&o.getCompound("trend").getInt("span")==0,"A new village has no trend yet: "+o.getCompound("trend"));
   h.assertTrue(Files.exists(OfficeHistory.path(b.l.getServer(),b.s.id())),"The first reading starts the history file");
   var file=NbtRecord.read(OfficeHistory.path(b.l.getServer(),b.s.id()));h.assertTrue(file.getInt("schema")==1&&file.getList("samples",Tag.TAG_COMPOUND).size()==1,"One sample for today: "+file);
   OfficeOverview.clear();OfficeOverview.read(b.l.getServer(),b.e);
   h.assertTrue(NbtRecord.read(OfficeHistory.path(b.l.getServer(),b.s.id())).getList("samples",Tag.TAG_COMPOUND).size()==1,"A second reading today (from the file) adds none");
  }finally{done(b);}
  h.succeed();
 }
 /** A history that cannot be read (garbage, or another village's) is set aside as .bad and a new one begins; the office still opens. */
 @GameTest(template="empty",timeoutTicks=100) public static void corruptHistoryIsReplacedNotFatal(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   var path=OfficeHistory.path(b.l.getServer(),b.s.id());Files.createDirectories(path.getParent());Files.write(path,new byte[]{1,2,3,4,5,6,7,8,9});
   var o=OfficeOverview.read(b.l.getServer(),b.e);
   h.assertTrue(o.getCompound("trend").getInt("span")==0,"No trend from a broken file");
   h.assertTrue(Files.exists(path.resolveSibling(path.getFileName()+".bad")),"The broken file is kept as .bad");
   var fresh=NbtRecord.read(path);h.assertTrue(fresh.getInt("schema")==1&&fresh.getUUID("village").equals(b.s.id()),"A new history was written: "+fresh);
   // Another village's history is foreign: set aside the same way.
   var foreign=OfficeHistory.empty(UUID.randomUUID());OfficeHistory.record(foreign,1,sample(1,9,9,9));NbtRecord.write(path,foreign);OfficeOverview.clear();
   var again=OfficeOverview.read(b.l.getServer(),b.e);
   h.assertTrue(again.getCompound("trend").getInt("span")==0&&NbtRecord.read(path).getUUID("village").equals(b.s.id()),"A foreign history is replaced");
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}finally{done(b);}
  h.succeed();
 }
 /** A research record that cannot be read gives no sample (its volumes would count as 0 and show a false jump later) and no pace. */
 @GameTest(template="empty",timeoutTicks=100) public static void unreadableResearchGivesNoSample(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   var research=BookResearch.path(b.l,b.s.id());Files.createDirectories(research.getParent());Files.write(research,new byte[]{9,8,7,6,5,4,3,2,1});
   var o=OfficeOverview.read(b.l.getServer(),b.e);var history=OfficeHistory.path(b.l.getServer(),b.s.id());
   h.assertTrue(!Files.exists(history)||NbtRecord.read(history).getList("samples",Tag.TAG_COMPOUND).isEmpty(),"No sample while the research cannot be read");
   h.assertTrue(o.getCompound("trend").getInt("span")==0&&!o.getCompound("trend").contains("paid"),"No change of volumes: "+o.getCompound("trend"));
   delete(research);OfficeOverview.clear();OfficeOverview.read(b.l.getServer(),b.e);
   h.assertTrue(NbtRecord.read(history).getList("samples",Tag.TAG_COMPOUND).size()==1,"Once readable, today's sample is taken");
  }catch(java.io.IOException ex){throw new IllegalStateException(ex);}finally{done(b);}
  h.succeed();
 }
 /** The research view names the volumes waiting in the laboratory and the scientists at work; without history the pace is unknown (-1). */
 @GameTest(template="empty",timeoutTicks=100) public static void researchViewCarriesLabStockAndScientists(GameTestHelper h){
  var b=bench(h,"town_hall","laboratory");
  try{
   var lab=b.chest("laboratory");for(int i=0;i<lab.getContainerSize();i++)lab.setItem(i,ItemStack.EMPTY);lab.setItem(0,new ItemStack(VillageAstra.RESEARCH_VOLUME.get(),5));lab.setItem(3,new ItemStack(VillageAstra.RESEARCH_VOLUME.get(),4));
   var r=new Resident(Settlement.childId(b.s.id(),"resident/scientist"),Resident.Life.ADULT,true,null,null,-1);b.s.admit(r,Settlement.childId(b.s.id(),"house/office"));b.s.assign(r.id(),Profession.SCIENTIST,b.buildings.get("laboratory").id());
   var p=FakePlayerFactory.get(b.l,new GameProfile(UUID.randomUUID(),"LabViewer"));var tag=new CompoundTag();tag.putUUID("village",b.s.id());BookResearch.addView(p,tag);var v=tag.getCompound("research");
   h.assertTrue(v.getInt("labVolumes")==9&&v.getInt("labs")==1,"Nine volumes in the one laboratory: "+v);
   h.assertTrue(v.getInt("scientists")==1,"One scientist: "+v.getInt("scientists"));
   h.assertTrue(v.getDouble("rate")==-1,"No history, no pace: "+v.getDouble("rate"));
  }finally{done(b);}
  h.succeed();
 }
 /** Action 2 takes a node out of the queue for the mayor only; the volumes paid and the target stay; a second try is refused and a refusal spends no revision. */
 @GameTest(template="empty",timeoutTicks=100) public static void dequeueRemovesOnlyForTheMayor(GameTestHelper h){
  // AD-136: level-I nodes are paid in resources, never queued: the queue holds level-II nodes.
  var b=bench(h,"town_hall");
  try{
   var mayor=FakePlayerFactory.get(b.l,new GameProfile(UUID.randomUUID(),"QueueMayor"));var other=FakePlayerFactory.get(b.l,new GameProfile(UUID.randomUUID(),"QueueVisitor"));
   for(var p:List.of(mayor,other))p.setPos(b.e.center().getX(),b.e.center().getY()+1,b.e.center().getZ());
   b.s.appointPlayerMayor(mayor.getUUID());var g=b.s.governance();
   var t=BookResearch.inspect(b.l,b.e);t.putString("selected","research.2");var queue=new ListTag();queue.add(StringTag.valueOf("agriculture.2"));queue.add(StringTag.valueOf("forestry.2"));t.put("queue",queue);
   t.getCompound("paid").putInt("agriculture.2",2);BookResearch.store(b.l,b.e,t);
   long revision=g.revision();
   h.assertTrue(!BookResearch.order(other,b.s.id(),g.epoch(),g.revision(),"agriculture.2",2),"A visitor cannot take a node out of the queue");
   h.assertTrue(BookResearch.inspect(b.l,b.e).getList("queue",Tag.TAG_STRING).size()==2&&g.revision()==revision,"Nothing changed, no revision spent");
   h.assertTrue(!BookResearch.order(mayor,b.s.id(),g.epoch(),g.revision(),"mining.2",2)&&g.revision()==revision,"A node not in the queue is refused before the order is recorded");
   h.assertTrue(BookResearch.order(mayor,b.s.id(),g.epoch(),g.revision(),"agriculture.2",2),"The mayor takes it out");
   var after=BookResearch.inspect(b.l,b.e);var left=after.getList("queue",Tag.TAG_STRING);
   h.assertTrue(left.size()==1&&left.getString(0).equals("forestry.2"),"Only the other node stays queued: "+left);
   h.assertTrue(after.getCompound("paid").getInt("agriculture.2")==2&&after.getString("selected").equals("research.2"),"Volumes paid and the target stay: "+after);
   long now=g.revision();h.assertTrue(!BookResearch.order(mayor,b.s.id(),g.epoch(),g.revision(),"agriculture.2",2)&&g.revision()==now,"A second removal is refused without spending a revision");
   h.assertTrue(!BookResearch.order(mayor,b.s.id(),g.epoch(),g.revision(),"forestry.2",3),"An unknown action is refused");
  }finally{done(b);}
  h.succeed();
 }
 /** The rule the office tree and the Overview use (ResearchRules, fed with the next hall level's research) is the server's, at every hall level. */
 @GameTest(template="empty",timeoutTicks=200) public static void clientRuleMatchesServerRule(GameTestHelper h){
  var b=bench(h,"town_hall");
  try{
   var random=new Random(124);var ids=new ArrayList<>(ResearchCatalog.NODES.keySet());int checked=0;
   for(int hall=1;hall<=6;hall++){b.s.restoreCivilization(Civilization.restore(hall,List.of(),"",0));
    var t=BookResearch.inspect(b.l,b.e);var paid=new CompoundTag();for(var id:ids)if(random.nextInt(3)==0)paid.putInt(id,ResearchCatalog.get(id).works());t.put("paid",paid);t.putString("selected","");t.put("queue",new ListTag());BookResearch.store(b.l,b.e,t);
    var done=BookResearch.completed(b.e,t);var next=BuildingTiers.hallResearch(hall+1);
    for(var n:ResearchCatalog.NODES.values()){String server=BookResearch.reason(b.e,t,n.id()),shared=ResearchRules.reason(done,hall,n,next);
     // The rule as it stood before AD-124 (BookResearch's own code), so the move to one function changed nothing.
     String before=done.contains(n.id())?"completed":hall<n.tier()&&!(n.tier()==hall+1&&next.contains(n.id()))?"hall_level":!done.containsAll(n.requires())?"dependencies":"available";
     h.assertTrue(server.equals(shared)&&shared.equals(before),n.id()+" at hall "+hall+": server "+server+", shared "+shared+", before "+before);checked++;}}
   h.assertTrue(checked==6*ResearchCatalog.NODES.size(),"Every node at every hall level: "+checked);
  }finally{done(b);}
  h.succeed();
 }
}
