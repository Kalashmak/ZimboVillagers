package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-041 and QUEST-002: the board asks only for what the village really needs, and pays only for what was really done.
 *  A card nobody took that the village no longer needs is taken down without blame, while a card somebody is already working on is left to
 *  them; a donation is counted from real coins given and a defence from real kills near the village; and the ore of a far seam is the ore
 *  that seam really gave — what was bought at a stall or dug at home stays the player's own. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestNeedGameTests {
 private record Town(ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Town town(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(6,3,6));var s=new Settlement(UUID.randomUUID());
  for(int x=-5;x<24;x++)for(int z=-5;z<24;z++){l.setBlock(center.offset(x,0,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);for(int y=1;y<5;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);
  return new Town(l,s,e,center);
 }
 private static ServerPlayer player(Town t,String name){
  var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  p.setPos(t.center.getX()+1.5,t.center.getY()+1,t.center.getZ()+5.5);return p;
 }
 private static CompoundTag quest(Town t,UUID id){return Quests.quest(t.l,t.s.id(),id);}
 /** A card of this template put on the board by hand, as the village would. */
 private static CompoundTag card(Town t,String template,String item,int target,long now){
  var q=Quests.blank(t.s.id(),template,now,Quests.deadline(template));
  q.putString("item",item);q.putInt("target",target);q.putLong("coins",Quests.coins(template));q.putLong("reputation",Quests.reputation(template));
  Quests.store(t.l,t.s.id(),q);return q;
 }

 // ---------------------------------------------------------------- the need that passed
 @GameTest(template="empty",timeoutTicks=200) public static void aSupplyNobodyWantsAnyMoreIsTakenDownButOneBeingWorkedOnIsNot(GameTestHelper h){
  var t=town(h);var p=player(t,"Supplier");
  // Nothing in this village asks for cobblestone: a card for it is a need that has passed.
  var stale=card(t,Quests.SUPPLY,"minecraft:cobblestone",16,100);
  h.assertTrue(Quests.stale(t.l,t.e,stale).equals("need_gone"),"Nobody wants it: "+Quests.stale(t.l,t.e,stale));
  Quests.tick(t.l,t.e,200);
  var gone=quest(t,stale.getUUID("id"));
  h.assertTrue(gone.getString("state").equals(Quests.CANCELLED)&&gone.getString("reason").equals("need_gone"),"It is taken down without blame: "+gone.getString("state")+" "+gone.getString("reason"));
  h.assertTrue(PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score()==0,"Nobody was blamed for it");
  // The same need, taken by somebody: their work is theirs, and the card stays.
  var taken=card(t,Quests.SUPPLY,"minecraft:cobblestone",16,300);
  h.assertTrue(Quests.take(p,t.s.id(),taken.getUUID("id")).equals("ok"),"Taken");
  Quests.tick(t.l,t.e,400);
  var kept=quest(t,taken.getUUID("id"));
  h.assertTrue(kept.getString("state").equals(Quests.TAKEN),"A card somebody is working on is left to them: "+kept.getString("state"));
  h.assertTrue(Quests.stale(t.l,t.e,kept).isEmpty(),"Only an untaken card is weighed against the need");
  h.succeed();
 }
 // ---------------------------------------------------------------- a donation counted from real coins
 @GameTest(template="empty",timeoutTicks=200) public static void aDonationCountsTheCoinsReallyGivenAndPaysOnce(GameTestHelper h){
  var t=town(h);var p=player(t,"Giver");
  int target=(int)Quests.setting(Quests.DONATION,"target_coins");
  var q=card(t,Quests.DONATION,"",target,100);var id=q.getUUID("id");
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
  // A sale is not a gift: only what was really donated counts.
  Quests.onDeal(p,t.s.id(),"minecraft:wheat",8,target,false);
  h.assertTrue(quest(t,id).getInt("progress")==0&&quest(t,id).getString("state").equals(Quests.TAKEN),"Selling to the village is no donation: "+quest(t,id).getInt("progress"));
  Quests.onDeal(p,t.s.id(),"minecraft:wheat",8,target/2,true);
  h.assertTrue(quest(t,id).getInt("progress")==target/2&&quest(t,id).getString("state").equals(Quests.TAKEN),"Half given is half done: "+quest(t,id).getInt("progress"));
  Quests.onDeal(p,t.s.id(),"minecraft:wheat",8,target-target/2,true);
  var done=quest(t,id);long score=PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score();
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&score==done.getLong("reputation"),"The whole gift is the whole deed: "+done.getString("state")+" "+score);
  Quests.onDeal(p,t.s.id(),"minecraft:wheat",8,target,true);
  h.assertTrue(PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score()==score,"Giving more afterwards is not paid twice: "+score);
  h.succeed();
 }
 // ---------------------------------------------------------------- a defence counted from real kills near the village
 @GameTest(template="empty",timeoutTicks=200) public static void aDefenceIsCalledByRealMonstersAndCountsOnlyKillsNearTheVillage(GameTestHelper h){
  var t=town(h);var p=player(t,"Defender");
  if(t.l.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL){h.succeed();return;}
  h.assertTrue(Quests.callDefence(t.l,t.e,100)==null,"No monsters, no call to arms");
  var mobs=new ArrayList<net.minecraft.world.entity.monster.Zombie>();
  for(int i=0;i<3;i++){var z=EntityType.ZOMBIE.create(t.l);z.setNoAi(true);z.setPersistenceRequired();
   z.moveTo(t.center.getX()+4+i,t.center.getY()+1,t.center.getZ()+4,0,0);t.l.addFreshEntity(z);mobs.add(z);}
  var q=Quests.callDefence(t.l,t.e,200);
  h.assertTrue(q!=null&&q.getInt("target")==Quests.setting(Quests.DEFENCE,"count"),"Monsters inside the village call the defence: "+q);
  h.assertTrue(Quests.callDefence(t.l,t.e,300)==null,"One call at a time");
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  // One killed far away does not count; the ones at the gate do.
  var far=EntityType.ZOMBIE.create(t.l);far.setNoAi(true);far.setPersistenceRequired();
  int radius=(int)Quests.setting(Quests.DEFENCE,"radius");
  far.moveTo(t.center.getX()+radius+20,t.center.getY()+1,t.center.getZ(),0,0);t.l.addFreshEntity(far);
  far.hurt(t.l.damageSources().playerAttack(p),1000f);Quests.onKill(p,far);
  h.assertTrue(quest(t,q.getUUID("id")).getInt("progress")==0,"A monster killed far away is another village's business: "+quest(t,q.getUUID("id")).getInt("progress"));
  for(var z:mobs){z.hurt(t.l.damageSources().playerAttack(p),1000f);Quests.onKill(p,z);}
  var done=quest(t,q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&done.getInt("progress")>=done.getInt("target"),"The village is cleared and the call is answered: "+done.getString("state")+" "+done.getInt("progress"));
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==done.getLong("coins"),"Paid what the call promised: "+p.getInventory().countItem(VillageAstra.ZINDBO.get()));
  h.succeed();
 }
 // ---------------------------------------------------------------- the ore of the far seam
 @GameTest(template="empty",timeoutTicks=200) public static void onlyTheOreTheFarSeamGaveIsTakenForTheLode(GameTestHelper h){
  var t=town(h);var p=player(t,"Miner");
  // The stock the ore is handed over at, and a card for a seam of twelve blocks of which none is mined yet.
  var warehouse=new Settlement.Building(Settlement.childId(t.s.id(),"building/warehouse"),"warehouse",8,0,0);t.s.addBuilding(warehouse);
  var stock=LogisticsRoutes.position(t.e,warehouse);t.l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  p.setPos(stock.getX()+1.5,stock.getY(),stock.getZ()+.5);
  var q=card(t,Adventures.LODE,"minecraft:raw_iron",4,100);var id=q.getUUID("id");var root=Quests.root(q);
  var site=new CompoundTag();site.putInt("schema",1);site.putUUID("id",UUID.randomUUID());site.putUUID("village",t.s.id());site.putUUID("quest",root);
  site.putString("kind",QuestSites.ADIT);site.putLong("pos",t.center.offset(-20,1,0).asLong());site.putString("state","open");
  var seam=new ListTag();var at=t.center.offset(-20,1,0);
  for(int i=0;i<12;i++){var pos=at.offset(0,0,i);seam.add(net.minecraft.nbt.LongTag.valueOf(pos.asLong()));t.l.setBlock(pos,Blocks.IRON_ORE.defaultBlockState(),2);}
  site.put("seam",seam);site.put("mobs",new ListTag());site.put("people",new ListTag());site.put("cage",new ListTag());site.put("seal",new ListTag());
  QuestSites.save(t.l,root,site);
  h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
  // Ore from a stall or from home: the village does not take it for this errand.
  p.getInventory().add(new ItemStack(Items.RAW_IRON,8));
  h.assertTrue(Adventures.handOver(p,t.s.id(),id).equals("not_from_seam"),"Ore that did not come out of that seam is refused: "+Adventures.handOver(p,t.s.id(),id));
  h.assertTrue(quest(t,id).getInt("progress")==0&&p.getInventory().countItem(Items.RAW_IRON)==8,"Nothing was taken and nothing counted");
  // Two blocks of the seam really mined, and the place is in the world: two of the eight are now its ore.
  t.l.setBlock(at,Blocks.STONE.defaultBlockState(),2);t.l.setBlock(at.offset(0,0,1),Blocks.STONE.defaultBlockState(),2);
  Adventures.advance(t.l,t.e,id,p,300);
  h.assertTrue(QuestSites.site(t.l,root).getInt("mined")==2,"The seam remembers what it gave: "+QuestSites.site(t.l,root).getInt("mined"));
  h.assertTrue(Adventures.handOver(p,t.s.id(),id).equals("ok"),"What the seam gave is taken");
  h.assertTrue(quest(t,id).getInt("progress")==2&&p.getInventory().countItem(Items.RAW_IRON)==6,"Two counted, the rest stays the player's: "+quest(t,id).getInt("progress"));
  h.assertTrue(Adventures.handOver(p,t.s.id(),id).equals("not_from_seam"),"And no more until the seam gives more");
  // The rest of the target mined out: the errand can be finished.
  t.l.setBlock(at.offset(0,0,2),Blocks.STONE.defaultBlockState(),2);t.l.setBlock(at.offset(0,0,3),Blocks.STONE.defaultBlockState(),2);
  Adventures.advance(t.l,t.e,id,p,400);
  h.assertTrue(Adventures.handOver(p,t.s.id(),id).equals("ok")&&quest(t,id).getString("state").equals(Quests.DONE),"The seam worked out, the errand is done: "+quest(t,id).getString("state"));
  h.succeed();
 }
}
