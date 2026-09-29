package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
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
/** AD-040: quests come from real needs, belong to one owner, advance only on real deeds and pay exactly once; giving up costs reputation. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class QuestGameTests {
 private record Town(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Town town(GameTestHelper h,String... buildings){
  return townAt(h,new BlockPos(8,3,8),buildings);
 }
 private static Town townAt(GameTestHelper h,BlockPos position,String... buildings){
  var l=h.getLevel();var center=h.absolutePos(position);var s=new Settlement(UUID.randomUUID());
  for(int x=-6;x<14;x++)for(int z=-6;z<14;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));int dx=0;
  for(var type:buildings){s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/"+type),type,dx,0,0));l.setBlock(center.offset(dx+1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);dx+=6;}
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,s,e,center);
 }
 private static ServerPlayer player(Town t,String name){var p=FakePlayerFactory.get(t.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(t.center.getX()+2,t.center.getY()+1,t.center.getZ()+2);return p;}
 private static long reputation(Town t,ServerPlayer p){return PropertyLedger.get(t.l.getServer()).roll(t.s.id()).account(p.getUUID()).score();}
 @GameTest(template="empty",timeoutTicks=100) public static void boardPostsRealNeedsAndOnlyOneOwnerTakesAQuest(GameTestHelper h){
  // Supply priority is tested away from hostile mobs spawned by unrelated world tests.
  var t=townAt(h,new BlockPos(4096,3,4096),"town_hall","clinic");var a=player(t,"QuestTaker");var b=player(t,"QuestRival");
  var q=Quests.post(t.l,t.e,1000);h.assertTrue(q!=null&&q.getString("template").equals(Quests.SUPPLY)&&q.getInt("target")>0&&q.getLong("coins")>0,"A quest is posted from a real want: "+q);
  h.assertTrue(Workshops.wants(t.l,t.e).stream().anyMatch(w->w.matches(new ItemStack(net.minecraft.world.item.Items.BREAD))||w.matches(new ItemStack(VillageAstra.BANDAGE.get()))),"The want behind it is real");
  var id=q.getUUID("id");
  h.assertTrue(Quests.take(a,t.s.id(),id).equals("ok")&&Quests.take(b,t.s.id(),id).equals("taken")&&Quests.take(a,t.s.id(),id).equals("yours"),"The first taker owns it");
  PropertyLedger.get(t.l.getServer()).theft(t.s.id(),b.getUUID(),4);
  var second=Quests.post(t.l,t.e,1100);h.assertTrue(second!=null&&!second.getUUID("id").equals(id)&&Quests.take(b,t.s.id(),second.getUUID("id")).equals("distrust"),"A distrusted player takes nothing");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void supplyQuestAdvancesThroughRealTradeAndPaysOnce(GameTestHelper h){
  var t=town(h,"clinic");var clinic=t.s.buildings().iterator().next();
  var doctor=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);t.s.admit(doctor,t.s.homes().iterator().next().id());t.s.assign(doctor.id(),Profession.DOCTOR,clinic.id());
  var npc=VillageAstra.RESIDENT.get().create(t.l);npc.bind(t.s.id(),t.s.resident(doctor.id()));npc.setNoAi(true);npc.moveTo(t.center.getX()+2.5,t.center.getY()+1,t.center.getZ()+2.5,0,0);t.l.addFreshEntity(npc);
  var p=player(t,"QuestSupplier");p.setPos(npc.getX()+1,npc.getY(),npc.getZ());p.getInventory().add(new ItemStack(VillageAstra.BANDAGE.get(),4));
  var q=Quests.post(t.l,t.e,2000);h.assertTrue(q!=null&&q.getString("item").equals("villageastra:bandage")&&q.getInt("target")==4,"Supply quest for the missing bandages: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  h.assertTrue(Trade.order(p,UUID.randomUUID(),npc.getUUID(),Trade.SELL,"villageastra:bandage",4).equals("ok"),"Bandages really sold to the doctor");
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&done.getInt("progress")==4,"The trade completed the quest: "+done);
  h.assertTrue(TradeLedger.get(t.l.getServer()).deal(Settlement.childId(q.getUUID("id"),"completion"))!=null&&!Quests.complete(p,t.s.id(),q.getUUID("id")),"Reward recorded once and never repeated");
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")+1&&reputation(t,p)>=q.getLong("reputation"),"Quest coins on top of the sale price: "+p.getInventory().countItem(VillageAstra.ZINDBO.get()));
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=140) public static void clearingCountsOnlyRealKillsNearTheVillage(GameTestHelper h){
  var t=town(h,"town_hall");var chest=LogisticsRoutes.chest(t.l,t.e,t.s.buildings().iterator().next());chest.setItem(0,new ItemStack(net.minecraft.world.item.Items.BREAD,64));
  var p=player(t,"QuestHunter");
  var near=new ArrayList<net.minecraft.world.entity.monster.Zombie>();
  for(int i=0;i<6;i++){var z=EntityType.ZOMBIE.create(t.l);z.setNoAi(true);z.moveTo(t.center.getX()+3+i,t.center.getY()+1,t.center.getZ()+3,0,0);t.l.addFreshEntity(z);near.add(z);}
  var q=Quests.post(t.l,t.e,3000);h.assertTrue(q!=null&&q.getString("template").equals(Quests.CLEARING)&&q.getInt("target")==5,"Monsters nearby post a clearing quest: "+q);
  h.assertTrue(Quests.take(p,t.s.id(),q.getUUID("id")).equals("ok"),"Taken");
  var far=EntityType.ZOMBIE.create(t.l);far.setNoAi(true);far.moveTo(t.center.getX()+200,t.center.getY()+1,t.center.getZ(),0,0);t.l.addFreshEntity(far);
  far.hurt(t.l.damageSources().playerAttack(p),1000F);
  h.assertTrue(Quests.quest(t.l,t.s.id(),q.getUUID("id")).getInt("progress")==0,"A kill far away does not count");
  for(var z:near)z.hurt(t.l.damageSources().playerAttack(p),1000F);
  var done=Quests.quest(t.l,t.s.id(),q.getUUID("id"));
  h.assertTrue(done.getString("state").equals(Quests.DONE)&&done.getInt("progress")>=5,"Cleared: "+done);
  h.assertTrue(p.getInventory().countItem(VillageAstra.ZINDBO.get())==q.getLong("coins")&&reputation(t,p)==q.getLong("reputation"),"Exactly one reward for the clearing");
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void givingUpAndMissingTheDeadlineCostReputation(GameTestHelper h){
  var t=town(h,"town_hall","clinic");var p=player(t,"QuestQuitter");
  var q=Quests.post(t.l,t.e,4000);var id=q.getUUID("id");h.assertTrue(Quests.take(p,t.s.id(),id).equals("ok"),"Taken");
  h.assertTrue(Quests.abandon(p,t.s.id(),id).equals("ok")&&reputation(t,p)==-Quests.ABANDON_REPUTATION,"Abandoning costs reputation: "+reputation(t,p));
  var reposted=Quests.board(t.l,t.s.id()).getList("quests",10).stream().map(x->(net.minecraft.nbt.CompoundTag)x).filter(x->x.getString("state").equals(Quests.OPEN)).findFirst().orElse(null);
  h.assertTrue(reposted!=null&&!reposted.getUUID("id").equals(id),"The need returns to the board as a fresh quest");
  var second=reposted.getUUID("id");PropertyLedger.get(t.l.getServer()).roll(t.s.id());
  var other=player(t,"QuestLate");h.assertTrue(Quests.take(other,t.s.id(),second).equals("ok"),"Another player takes the reposted quest");
  Quests.tick(t.l,t.e,Quests.quest(t.l,t.s.id(),second).getLong("deadline")+1);
  h.assertTrue(Quests.quest(t.l,t.s.id(),second).getString("state").equals(Quests.FAILED)&&reputation(t,other)==-Quests.FAIL_REPUTATION,"A missed deadline fails the quest and costs reputation: "+reputation(t,other));
  h.succeed();
 }
}
