package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.dialog.*;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** AD-146: the conversation window's server side — a resident's greeting with its own answers and those of other work, only an answer the
 *  open page offered (enabled, under its serial) is taken, a topic's handler gets it, and a resident gone away ends the talk. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DialogGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void aConversationTakesOnlyTheAnswersItOffered(GameTestHelper h){
  var l=h.getLevel();var s=StarterVillage.create(l,h.absolutePos(new BlockPos(2,3,2)));var e=SettlementData.get(l.getServer()).entry(s.id());
  // The starter village's own body of its first resident (a second body with its id would not join the level).
  var r=s.residents().iterator().next();var at=h.absolutePos(new BlockPos(4,2,4));
  var npc=l.getEntity(r.id()) instanceof ResidentEntity body?body:VillageAstra.RESIDENT.get().create(l);
  if(npc.level()!=l||l.getEntity(r.id())==null){npc.bind(s.id(),r);l.addFreshEntity(npc);}
  npc.setNoAi(true);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  var p=FakePlayerFactory.getMinecraft(l);p.moveTo(at.getX()+2.5,at.getY(),at.getZ()+.5,0,0);
  var chosen=new ArrayList<String>();
  Dialogs.register("probe",(pl,n,action,context)->chosen.add(action+":"+context.getString("x")));
  Dialogs.addTopic((pl,n)->n==npc?List.of(DialogOption.of("probe/ping",Component.literal("ping"))):List.of());
  try{
   Dialogs.greet(p,npc);var page=Dialogs.page(p);long serial=Dialogs.serial(p);
   var ids=page==null?List.<String>of():page.options().stream().map(DialogOption::id).toList();
   h.assertTrue(ids.containsAll(List.of("resident/trade","resident/news","probe/ping","close/bye"))&&ids.get(ids.size()-1).equals("close/bye"),"The greeting: trade, news, the probe's topic, goodbye last: "+ids);
   h.assertTrue(page.speaker().equals(npc.getUUID())&&!page.lines().isEmpty(),"The resident speaks first");
   h.assertTrue(!Dialogs.choose(p,serial+1,"resident/news"),"A stale serial is refused");
   h.assertTrue(!Dialogs.choose(p,serial,"resident/unknown"),"An answer the page did not offer is refused");
   boolean took=Dialogs.choose(p,serial,"probe/ping");
   h.assertTrue(took&&chosen.equals(List.of("ping:")),"A topic's handler gets its answer: took="+took+" "+chosen+" page="+Dialogs.page(p)+" npcAt="+npc.blockPosition()+" p="+p.blockPosition()+" level="+(p.level()==l));
   Dialogs.greet(p,npc);h.assertTrue(Dialogs.choose(p,Dialogs.serial(p),"resident/news")&&Dialogs.page(p).lines().size()>=2,"«Как дела?» tells how things are");
   var ctx=new net.minecraft.nbt.CompoundTag();ctx.putString("x","kept");
   Dialogs.open(p,new DialogPage(npc.getUUID(),List.of(Component.literal("?")),List.of(new DialogOption("probe/off",Component.literal("off"),false,null),DialogOption.of("probe/on",Component.literal("on"))),ctx));
   long s2=Dialogs.serial(p);
   h.assertTrue(!Dialogs.choose(p,s2,"probe/off"),"A disabled answer is refused");
   h.assertTrue(Dialogs.choose(p,s2,"probe/on")&&chosen.get(chosen.size()-1).equals("on:kept"),"The page's context comes back to the handler: "+chosen);
   Dialogs.greet(p,npc);long s3=Dialogs.serial(p);npc.moveTo(at.getX()+20.5,at.getY(),at.getZ()+.5,0,0);
   h.assertTrue(!Dialogs.choose(p,s3,"resident/news")&&Dialogs.page(p)==null,"A resident gone away ends the talk");
   Dialogs.say(p,npc,Component.literal("line"));h.assertTrue(Dialogs.page(p).options().size()==1&&Dialogs.page(p).options().get(0).id().equals("close/ok"),"A word in the window, «Понятно»");
  }finally{npc.discard();Dialogs.forget(p.getUUID());SettlementData.get(l.getServer()).remove(s.id());}
  h.succeed();
 }
}
