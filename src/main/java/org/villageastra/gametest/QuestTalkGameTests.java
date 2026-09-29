package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.dialog.*;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-149: an errand is given out and handed back by the person who asked for it. Only its asker speaks of it — the mayor for the village's
 *  own needs; taking it in the window is the same taking the board does, with the same reasons for a no; what is in hand is reported to the
 *  same person, handed over to them or given up to them; and a button about an errand that is gone answers that it is gone. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestTalkGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-4;x<12;x++)for(int z=-4;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  int dx=0;
  for(var type:List.of("town_hall","farm"))s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,dx+=3,0,0));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center);
 }
 /** A resident of this village with a body of their own, standing where the player can talk to them. */
 private static ResidentEntity person(Town t,Profession trade,int dx){
  var r=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);
  t.s.admit(r,t.s.homes().iterator().next().id());
  if(trade!=null){var place=t.s.buildings().stream().filter(b->b.type().equals(trade.workplace())).findFirst().orElseThrow();
   t.s.assign(r.id(),trade,place.id());}
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),t.s.resident(r.id()));npc.setNoAi(true);
  npc.moveTo(t.center.getX()+dx+.5,t.center.getY()+1,t.center.getZ()+.5,0,0);t.l.addFreshEntity(npc);return npc;
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  p.setPos(t.center.getX()+.5,t.center.getY()+1,t.center.getZ()+.5);return p;
 }
 /** A card of this village on the board, asked for by this resident (or by nobody, which makes it the village's own). */
 private static CompoundTag card(Town t,String template,UUID giver,long now){
  var q=Quests.blank(t.s.id(),template,now,now+Quests.deadline(template));
  q.putInt("target",4);q.putLong("coins",Quests.coins(template));q.putLong("reputation",Quests.reputation(template));
  if(giver!=null){q.putUUID("giver",giver);q.putString("giver_name","Asker");}
  Quests.store(t.l,t.s.id(),q);return q;
 }
 private static List<String> answers(ServerPlayer p){
  var page=Dialogs.page(p);return page==null?List.of():page.options().stream().map(DialogOption::id).toList();
 }
 private static boolean say(ServerPlayer p,String id){return Dialogs.choose(p,Dialogs.serial(p),id);}
 private static long now(Town t){return SettlementData.get(t.l.getServer()).clock().ticks();}

 @GameTest(template="empty",timeoutTicks=100) public static void onlyTheOneWhoAskedSpeaksOfTheErrand(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var other=person(t,Profession.FARMER,4);var mayor=person(t,Profession.MAYOR,6);
  var p=player(t,"TalkOfErrands");
  try{
   card(t,Quests.SUPPLY,asker.getUUID(),now(t));
   Dialogs.greet(p,asker);h.assertTrue(answers(p).contains("quest/offers"),"The one who asked has something to say: "+answers(p));
   Dialogs.greet(p,other);h.assertTrue(!answers(p).contains("quest/offers"),"A resident who asked for nothing offers nothing: "+answers(p));
   Dialogs.greet(p,mayor);h.assertTrue(!answers(p).contains("quest/offers"),"Another's errand is not the mayor's to give: "+answers(p));
   // A need of the village itself belongs to the mayor, and to no other resident.
   card(t,Quests.SUPPLY,null,now(t));
   Dialogs.greet(p,mayor);h.assertTrue(answers(p).contains("quest/offers"),"The village's own need is the mayor's: "+answers(p));
   Dialogs.greet(p,other);h.assertTrue(!answers(p).contains("quest/offers"),"and still not anybody's: "+answers(p));
   h.succeed();
  }finally{asker.discard();other.discard();mayor.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void theErrandIsTakenInTheWindowAndTheBoardKnowsIt(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var p=player(t,"TalkTaker");
  try{
   var q=card(t,Quests.SUPPLY,asker.getUUID(),now(t));var id=q.getUUID("id");
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/offers"),"The player asks for work");
   var page=Dialogs.page(p);
   h.assertTrue(page!=null&&page.lines().size()>=4&&page.speaker().equals(asker.getUUID()),"The asker tells it themselves: "+(page==null?"no page":page.lines().size()+" lines"));
   h.assertTrue(answers(p).contains("quest/take_0"),"and the player may take it: "+answers(p));
   h.assertTrue(say(p,"quest/take_0"),"Taken in the window");
   var taken=Quests.quest(t.l,t.s.id(),id);
   h.assertTrue(taken.getString("state").equals(Quests.TAKEN)&&taken.getUUID("owner").equals(p.getUUID()),"The board knows who took it: "+taken.getString("state"));
   h.assertTrue(Dialogs.page(p)!=null&&Dialogs.page(p).options().size()==1,"The asker answers with a word and «Понятно»");
   // What is taken is no longer offered, and is now what the same person hears about.
   Dialogs.greet(p,asker);
   h.assertTrue(!answers(p).contains("quest/offers")&&answers(p).contains("quest/mine"),"Offered no more, reported instead: "+answers(p));
   h.succeed();
  }finally{asker.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void anErrandBeyondTheTrustSaysWhyAndIsNotGivenAnyway(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var p=player(t,"TalkStranger");
  try{
   var template=org.villageastra.world.Far.TIDINGS;
   h.assertTrue(Quests.trust(template)>0,"This errand asks for trust the stranger has not got");
   var q=card(t,template,asker.getUUID(),now(t));
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/offers"),"The player asks for work");
   var take=Dialogs.page(p).options().stream().filter(o->o.id().equals("quest/take_0")).findFirst().orElse(null);
   h.assertTrue(take!=null&&!take.enabled()&&take.tooltip()!=null,"The answer is there but grey, with the reason on it: "+take);
   h.assertTrue(!say(p,"quest/take_0"),"and pressing it changes nothing");
   h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getString("state").equals(Quests.OPEN),"The errand is still nobody's");
   h.succeed();
  }finally{asker.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void whatIsInHandIsReportedToTheOneWhoAsked(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var p=player(t,"TalkReporter");
  try{
   // A supply errand is finished by trade, so there is nothing to hand over in the conversation; a cargo errand is brought by hand.
   var supply=card(t,Quests.SUPPLY,asker.getUUID(),now(t));
   h.assertTrue(Quests.take(p,t.s.id(),supply.getUUID("id")).equals("ok"),"Taken");
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/mine"),"The player reports");
   var page=Dialogs.page(p);
   h.assertTrue(page!=null&&page.lines().size()>=3,"How far along it is, in words: "+(page==null?"no page":page.lines().toString()));
   var ids=answers(p);
   h.assertTrue(!ids.contains("quest/hand_0")&&ids.contains("quest/drop_0"),"Nothing to hand over by hand, but it may be given up: "+ids);
   var cargo=card(t,Quests.CARGO,asker.getUUID(),now(t));cargo.putString("item","minecraft:bread");Quests.store(t.l,t.s.id(),cargo);
   h.assertTrue(Quests.take(p,t.s.id(),cargo.getUUID("id")).equals("ok"),"The cargo errand is taken too");
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/mine")&&answers(p).contains("quest/held_1"),"Two errands with one person: "+answers(p));
   h.assertTrue(say(p,"quest/held_1")&&answers(p).contains("quest/hand_1"),"Goods brought by hand are handed to the asker: "+answers(p));
   // The hand-over itself is the board's own: with nothing carried, the asker says so instead of taking anything.
   h.assertTrue(say(p,"quest/hand_1"),"The player offers what they carry");
   h.assertTrue(Quests.quest(t.l,t.s.id(),cargo.getUUID("id")).getInt("progress")==0,"and nothing is counted that was not brought");
   h.succeed();
  }finally{asker.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void anErrandGivenUpInTheWindowGoesBackToTheBoard(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var p=player(t,"TalkQuitter");
  try{
   var q=card(t,Quests.SUPPLY,asker.getUUID(),now(t));var id=q.getUUID("id");
   h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/mine")&&say(p,"quest/drop_0"),"Given up to the one who asked");
   h.assertTrue(Quests.quest(t.l,t.s.id(),id).getString("state").equals(Quests.ABANDONED),"The card is given up: "+Quests.quest(t.l,t.s.id(),id).getString("state"));
   Dialogs.greet(p,asker);
   h.assertTrue(answers(p).contains("quest/offers")&&!answers(p).contains("quest/mine"),"The need is asked for again, of somebody else: "+answers(p));
   // AD-105: the one who gave it up does not take it back — the asker says so on the grey button.
   h.assertTrue(say(p,"quest/offers"),"The player asks again");
   var take=Dialogs.page(p).options().stream().filter(o->o.id().equals("quest/take_0")).findFirst().orElse(null);
   h.assertTrue(take!=null&&!take.enabled(),"and hears that they gave this up: "+take);
   h.succeed();
  }finally{asker.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void aButtonAboutAnErrandThatIsGoneAnswersSo(GameTestHelper h){
  var t=town(h);var asker=person(t,Profession.FARMER,2);var p=player(t,"TalkLate");
  try{
   var q=card(t,Quests.SUPPLY,asker.getUUID(),now(t));
   Dialogs.greet(p,asker);h.assertTrue(say(p,"quest/offers")&&answers(p).contains("quest/take_0"),"The errand is offered");
   // While the window stands open the need passes: the card is cancelled, and the old button must not take it.
   q.putString("state",Quests.CANCELLED);Quests.store(t.l,t.s.id(),q);
   h.assertTrue(say(p,"quest/take_0"),"The old button is still answered");
   h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getString("state").equals(Quests.CANCELLED),"but it takes nothing: "+Quests.quest(t.l,t.s.id(),q.getUUID("id")).getString("state"));
   h.assertTrue(Dialogs.page(p)!=null&&Dialogs.page(p).options().size()==1,"and the asker says as much in the window");
   h.succeed();
  }finally{asker.discard();Dialogs.forget(p.getUUID());SettlementData.get(t.l.getServer()).remove(t.s.id());}
 }
}
