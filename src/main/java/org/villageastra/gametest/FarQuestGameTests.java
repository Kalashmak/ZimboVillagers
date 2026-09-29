package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** QUEST-002: how a village comes to know of a place thousands of blocks away. The word reaches as far as its expedition house is good; the
 *  search goes out along eight roads of its own, never on the office's own sectors; a place is kept only when the world really holds one
 *  that far out and above the sea, and a world that holds none teaches the village nothing rather than a wrong coordinate; and a place that
 *  far still carries its cross on the chart the player is given. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class FarQuestGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-4;x<16;x++)for(int z=-4;z<16;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/expedition"),"expedition",8,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center);
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theFarReachFollowsTheBestExpeditionHouse(GameTestHelper h){
  var t=town(h);
  int reach=Far.reach(t.l,t.e);
  h.assertTrue(reach==CoreEffects.value("expedition","far_reach",1),"A village with a level-I office reaches its first level's word: "+reach);
  h.assertTrue(CoreEffects.value("expedition","far_reach",6)>CoreEffects.value("expedition","far_reach",1),"A better office hears of farther places");
  // The office's own people still walk only their hundred and some blocks: the far reach is word, not walking.
  h.assertTrue(reach>Expeditions.range(t.l,t.e)*8,"Word goes many times farther than a scout walks: "+reach+" against "+Expeditions.range(t.l,t.e));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void theRoadsOfWordAreTheVillagesOwnEight(GameTestHelper h){
  var t=town(h);
  h.assertTrue(Far.nextSector(t.l,t.s.id())==Far.FIRST_SECTOR,"The first road of word is its own, not the office's: "+Far.nextSector(t.l,t.s.id()));
  // A lead of the office itself does not use up a road of word, nor the other way about.
  Expeditions.addLead(t.l,t.s.id(),t.center.offset(60,0,0),Expeditions.CAMP,0,100);
  h.assertTrue(Far.nextSector(t.l,t.s.id())==Far.FIRST_SECTOR,"A scouted camp is not word of a far place");
  Expeditions.addLead(t.l,t.s.id(),t.center.offset(2000,0,0),Far.KIND,Far.FIRST_SECTOR,200);
  h.assertTrue(Far.nextSector(t.l,t.s.id())==Far.FIRST_SECTOR+1,"The next road is the next one: "+Far.nextSector(t.l,t.s.id()));
  for(int s=Far.FIRST_SECTOR+1;s<Far.FIRST_SECTOR+Far.SECTORS;s++)Expeditions.addLead(t.l,t.s.id(),t.center.offset(2000,0,s),Far.KIND,s,300);
  h.assertTrue(Far.nextSector(t.l,t.s.id())==-1,"With every road known there is nothing more to learn this way");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=400) public static void aWorldWithNoSuchPlaceTeachesTheVillageNothing(GameTestHelper h){
  var t=town(h);
  // Whatever this world holds along that road, what is written down is real: really far, really above the sea, really on its chart.
  for(var target:Far.TARGETS){
   var at=Far.probe(t.l,t.e,target,Far.FIRST_SECTOR);
   if(at==null)continue;
   int away=Far.away(t.e,at);
   h.assertTrue(away>=Quests.setting(Far.KIND,"least")&&away<=Quests.setting(Far.KIND,"most"),target.id()+" is really far: "+away);
   h.assertTrue(at.getY()>t.l.getSeaLevel(),target.id()+" stands above the sea: "+at.getY());
   h.assertTrue(Far.charted(t.e,at),target.id()+" keeps its cross on the chart it is given");
  }
  // The flat world of these tests holds no far country and no far structure: the village is told nothing rather than a wrong place.
  h.assertTrue(Far.known(t.l,t.s.id(),"")==null,"Nothing was written down that the world did not really hold");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void wordOfAFarPlaceIsWrittenDownWithWhatItIsAndHowFar(GameTestHelper h){
  var t=town(h);
  // What the world would have answered, written down the way Far.learn writes it: the kind, the distance, and found again by kind.
  var at=t.center.offset(2600,0,-1800);
  Expeditions.addLead(t.l,t.s.id(),at,Far.KIND,Far.FIRST_SECTOR,100);
  var record=Expeditions.record(t.l,t.s.id());
  for(var raw:record.getList("leads",Tag.TAG_COMPOUND)){var lead=(CompoundTag)raw;
   if(lead.getInt("sector")==Far.FIRST_SECTOR){lead.putString("far_kind","far_wood");lead.putInt("away",Far.away(t.e,at));}}
  Expeditions.save(t.l,t.s.id(),record);
  var found=Far.known(t.l,t.s.id(),"far_wood");
  h.assertTrue(found!=null&&BlockPos.of(found.getLong("pos")).equals(at)&&found.getInt("away")==Far.away(t.e,at),"The village knows of that wood and how far it lies: "+found);
  h.assertTrue(Far.known(t.l,t.s.id(),"far_sands")==null,"and knows of no sands");
  h.assertTrue(Far.charted(t.e,at),"A place that far still carries its cross on its chart");
  Expeditions.useLead(t.l,t.s.id(),at,true);
  h.assertTrue(Far.known(t.l,t.s.id(),"")==null,"Word already acted on is not offered twice");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=300) public static void theWordIsPaidWhenItIsBroughtHomeAndNotBefore(GameTestHelper h){
  var t=town(h);
  var p=net.minecraftforge.common.util.FakePlayerFactory.get(t.l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"Traveller"));
  p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();
  org.villageastra.server.PropertyLedger.get(t.l.getServer()).gift(t.s.id(),p.getUUID(),Quests.trust(Far.TIDINGS));
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+4.5);
  // A fake player of a test is in no player list: the seam the escort already uses lets the village find its traveller.
  EscortGoal.TEST_OWNERS.put(p.getUUID(),p);
  // The village has heard of a far place along one of its roads; from that word it asks for tidings.
  var at=t.center.offset(2600,0,-1800);
  Expeditions.addLead(t.l,t.s.id(),at,Far.KIND,Far.FIRST_SECTOR,100);
  var record=Expeditions.record(t.l,t.s.id());
  for(var raw:record.getList("leads",Tag.TAG_COMPOUND)){var lead=(CompoundTag)raw;
   if(lead.getInt("sector")==Far.FIRST_SECTOR){lead.putString("far_kind","far_wood");lead.putInt("away",Far.away(t.e,at));}}
  Expeditions.save(t.l,t.s.id(),record);
  var q=Far.post(t.l,t.e,200);
  h.assertTrue(q!=null&&q.getString("template").equals(Far.TIDINGS)&&BlockPos.of(q.getLong("site")).equals(at)&&q.getString("kind").equals("far_wood"),"The village asks for word of that far wood: "+q);
  h.assertTrue(Far.post(t.l,t.e,300)==null,"One such errand at a time");
  var id=q.getUUID("id");
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
  h.assertTrue(p.getInventory().items.stream().anyMatch(x->x.is(net.minecraft.world.item.Items.FILLED_MAP)),"A chart of the place comes with it");
  // Standing at home is no word; standing at the place is.
  Far.tick(t.l,t.e,320);
  h.assertTrue(Quests.quest(t.l,t.s.id(),id).getInt("progress")==0,"Nothing is seen from the village");
  p.setPos(at.getX()+3.5,t.center.getY()+1,at.getZ()+2.5);
  Far.tick(t.l,t.e,340);
  var seen=Quests.quest(t.l,t.s.id(),id);
  h.assertTrue(seen.getInt("progress")==1&&seen.getString("status").equals("seen")&&seen.getString("state").equals(Quests.TAKEN),"Seen with their own eyes, and not yet paid: "+seen.getString("state"));
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==0,"Word out there is worth nothing yet");
  // Halfway back is still not back.
  p.setPos(t.center.getX()+600.5,t.center.getY()+1,t.center.getZ()+600.5);
  Far.tick(t.l,t.e,360);
  h.assertTrue(Quests.quest(t.l,t.s.id(),id).getString("state").equals(Quests.TAKEN),"The road home is not the telling");
  p.setPos(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5);
  Far.tick(t.l,t.e,380);
  var done=Quests.quest(t.l,t.s.id(),id);
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&p.getInventory().countItem(VillageAstra.ZINDBO.get())==done.getLong("coins"),"The word is told at home and paid: "+done.getString("state")+" "+p.getInventory().countItem(VillageAstra.ZINDBO.get()));
  EscortGoal.TEST_OWNERS.remove(p.getUUID());
  h.succeed();
 }
}
