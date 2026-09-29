package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.server.*;
import org.villageastra.domain.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ElectionGameTests {
 @GameTest(template="empty",timeoutTicks=200) public static void physicalDonationsCannotBeRecycledForProfit(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,3,2));var s=StarterVillage.create(l,origin);var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"Donor"));p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setPos(origin.getX()+1,origin.getY()+1,origin.getZ()+3);
  var chest=(OwnedChestEntity)l.getBlockEntity(origin.offset(1,1,4));chest.clearContent();var menu=new OwnedChestMenu(1,p.getInventory(),chest);p.getInventory().setItem(9,new ItemStack(Items.OAK_LOG,64));menu.clicked(27,0,ClickType.QUICK_MOVE,p);var ledger=PropertyLedger.get(l.getServer());var roll=ledger.roll(s.id());h.assertTrue(roll.account(p.getUUID()).score()==64&&chest.countItem(Items.OAK_LOG)==64&&p.getInventory().getItem(9).isEmpty(),"Actual donation debits player and fills real stock");
  menu.clicked(0,0,ClickType.PICKUP,p);h.assertTrue(roll.account(p.getUUID()).score()==64&&roll.account(p.getUUID()).theft()==0,"Returning one's deposit is not theft");menu.clicked(0,0,ClickType.PICKUP,p);h.assertTrue(roll.account(p.getUUID()).score()==64&&roll.account(p.getUUID()).theft()==0,"Take and replace cannot increase reputation");
  p.getInventory().setItem(9,new ItemStack(Items.OAK_LOG,64));menu.clicked(27,0,ClickType.QUICK_MOVE,p);h.assertTrue(roll.account(p.getUUID()).score()==64&&chest.countItem(Items.OAK_LOG)==128,"Surplus above reserve gives no points");
  p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().setItem(9,new ItemStack(Items.IRON_INGOT,64));menu.clicked(27,0,ClickType.QUICK_MOVE,p);h.assertTrue(roll.account(p.getUUID()).score()==64,"Creative supplies do not earn influence");
  h.assertTrue(PropertyLedger.load(ledger.save(new CompoundTag())).roll(s.id()).account(p.getUUID()).score()==64,"Exact net reputation persists");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void periodicElectionOfflineWinnerAndResignation(GameTestHelper h){
  var l=h.getLevel();var s=StarterVillage.create(l,h.absolutePos(new BlockPos(2,3,2)));var data=SettlementData.get(l.getServer());var e=data.entry(s.id());var ledger=PropertyLedger.get(l.getServer());var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"Candidate"));p.setPos(e.center().getX(),e.center().getY()+1,e.center().getZ());ledger.gift(s.id(),p.getUUID(),(int)(ElectionRoll.MINIMUM+72));var roll=ledger.roll(s.id());
  Elections.advance(data,ledger,e,0);long sequence=roll.sequence();h.assertTrue(Elections.order(p,s.id(),0,sequence,0),"Candidate registers using real eligibility");h.assertTrue(!Elections.order(p,s.id(),0,sequence,1),"Replayed old sequence rejected");Elections.advance(data,ledger,e,23999);h.assertTrue(s.governance().playerMayor()==null,"High score cannot oust current mayor between rounds");Elections.advance(data,ledger,e,24000);h.assertTrue(p.getUUID().equals(s.governance().playerMayor()),"Offline candidate elected at deadline");
  var g=s.governance();long epoch=g.epoch();var project=UUID.randomUUID();g.setPaused(p.getUUID(),epoch,0,project,true);var restored=SettlementData.load(data.save(new CompoundTag())).entry(s.id()).settlement();h.assertTrue(restored.governance().nextElection()==48000&&restored.governance().lastScore()==ElectionRoll.MINIMUM+71&&restored.governance().playerMayor().equals(p.getUUID())&&restored.governance().paused(project),"Result snapshot and commands survive reload");
  p.setPos(e.center().getX()+100,e.center().getY(),e.center().getZ());h.assertTrue(!Elections.order(p,s.id(),epoch,roll.sequence(),2),"Remote resignation denied");p.setPos(e.center().getX(),e.center().getY()+1,e.center().getZ());h.assertTrue(Elections.order(p,s.id(),epoch,roll.sequence(),2),"Mayor can resign");h.assertTrue(g.playerMayor()==null&&g.paused(project)&&s.residents().stream().filter(r->r.profession()==Profession.MAYOR).count()==1,"One NPC successor, orders retained");h.assertTrue(!Elections.order(p,s.id(),epoch,roll.sequence(),0),"Former authority epoch rejected");
  long deadline=g.nextElection(),event=roll.sequence(),clock=data.clock().ticks();Elections.tick(l.getServer());data.activeTick(false);h.assertTrue(g.nextElection()==deadline&&roll.sequence()==event&&data.clock().ticks()==clock,"Empty server cannot advance elections, reputation or active time");Elections.advance(data,ledger,e,48000);h.assertTrue(g.playerMayor()==null&&g.lastWinner()==null&&g.nextElection()==72000,"No eligible candidate keeps NPC authority at the next round");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void legacyTheftMigratesAndCorruptRollIsRejected(GameTestHelper h){
  var village=UUID.randomUUID();var player=UUID.randomUUID();var root=new CompoundTag();root.putInt("schema",1);var rows=new ListTag();var row=new CompoundTag();row.putUUID("village",village);row.putUUID("player",player);row.putLong("stolen",8);rows.add(row);root.put("entries",rows);var migrated=PropertyLedger.load(root);h.assertTrue(migrated.roll(village).account(player).score()==-8&&migrated.stolen(village,player)==8,"Old penalties remain in the common score");var saved=migrated.save(new CompoundTag());h.assertTrue(PropertyLedger.load(saved).roll(village).account(player).score()==-8,"Schema two roundtrip");saved.getList("villages",Tag.TAG_COMPOUND).getCompound(0).remove("sequence");boolean rejected=false;try{PropertyLedger.load(saved);}catch(IllegalArgumentException ex){rejected=true;}h.assertTrue(rejected,"Corrupt election history fails closed");h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void schemaSixKeepsOfficeAndOrdersBeforeFirstElection(GameTestHelper h){
  var l=h.getLevel();var s=StarterVillage.create(l,h.absolutePos(new BlockPos(2,3,2)));var player=UUID.randomUUID();var project=UUID.randomUUID();s.appointPlayerMayor(player);s.governance().setPaused(player,s.governance().epoch(),0,project,true);var root=SettlementData.get(l.getServer()).save(new CompoundTag());root.putInt("schema",6);for(var raw:root.getList("settlements",Tag.TAG_COMPOUND)){var g=((CompoundTag)raw).getCompound("governance");for(var key:List.of("nextElection","lastElection","lastWinner","lastScore","lastReached"))g.remove(key);}var restored=SettlementData.load(root).entry(s.id()).settlement();h.assertTrue(restored.governance().playerMayor().equals(player)&&restored.governance().paused(project)&&restored.governance().nextElection()==0&&restored.residents().size()==6,"Migration preserves office, orders and people without inventing retrospective elections");h.succeed();
 }
}
