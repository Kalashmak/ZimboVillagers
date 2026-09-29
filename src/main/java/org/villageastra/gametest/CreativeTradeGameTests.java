package org.villageastra.gametest;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-036: zindbo are minted once per confirmed useful purchase, donations beat sales, resale loops earn nothing, and empty cards say why. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CreativeTradeGameTests {
 private record Market(net.minecraft.server.level.ServerLevel l,Settlement s,SettlementData.Entry e,BlockPos center){}
 private static Market market(GameTestHelper h){
  var l=h.getLevel();var center=h.absolutePos(new BlockPos(8,3,8));var s=new Settlement(UUID.randomUUID());
  for(int x=-8;x<20;x++)for(int z=-8;z<12;z++){l.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),1,8,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Market(l,s,e,center);
 }
 /** A workplace at x offset dx with its owned chest, and one resident of the profession working there. */
 private static ResidentEntity worker(GameTestHelper h,Market m,String type,Profession role,int dx,Resident.Life life){
  var b=new Settlement.Building(Settlement.childId(m.s.id(),"building/"+type),type,dx,0,0);m.s.addBuilding(b);m.l.setBlock(m.center.offset(dx+1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var r=new Resident(UUID.randomUUID(),life,true,null,null,-1);m.s.admit(r,m.s.homes().iterator().next().id());if(role!=null)m.s.assign(r.id(),role,b.id());
  var npc=VillageAstra.RESIDENT.get().create(m.l);npc.bind(m.s.id(),m.s.resident(r.id()));npc.setNoAi(true);npc.moveTo(m.center.getX()+dx+2.5,m.center.getY()+1,m.center.getZ()+2.5,0,0);m.l.addFreshEntity(npc);return npc;
 }
 private static Container chest(Market m,ResidentEntity npc){return LogisticsRoutes.chest(m.l,m.e,m.s.workplace(npc.getUUID()));}
 private static ServerPlayer player(Market m,ResidentEntity npc,String name){var p=FakePlayerFactory.get(m.l,new GameProfile(UUID.randomUUID(),name));p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.setPos(npc.getX()+1,npc.getY(),npc.getZ());return p;}
 private static int coins(ServerPlayer p){return p.getInventory().countItem(VillageAstra.ZINDBO.get());}
 private static long reputation(Market m,ServerPlayer p){return PropertyLedger.get(m.l.getServer()).roll(m.s.id()).account(p.getUUID()).score();}
 private static int accepted(ServerPlayer p,ResidentEntity npc,Item item){return Trade.offer(p,npc).buys().stream().filter(x->x.item()==item).mapToInt(Trade.Line::count).sum();}

 @GameTest(template="empty",timeoutTicks=100) public static void creativeSaleCreatesStockAndReputationOnce(GameTestHelper h){
  var m=market(h);var n=worker(h,m,"mill",Profession.MILLER,0,Resident.Life.ADULT);var p=player(m,n,"CreativeSeller");p.setGameMode(GameType.CREATIVE);m.s.appointPlayerMayor(p.getUUID());
  var line=Trade.view(p,n).getList("buys",10);h.assertTrue(line.getCompound(0).getBoolean("creativeSource"),"Server advertises creative supply");
  var token=UUID.randomUUID();h.assertTrue(Trade.order(p,token,n.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("ok"),"Empty-handed creative mayor sells");
  long rep=reputation(m,p);h.assertTrue(rep>0&&chest(m,n).countItem(Items.WHEAT)==16&&p.getInventory().countItem(Items.WHEAT)==0&&coins(p)==1,"Real stock, payment and reputation without inventory wheat");
  h.assertTrue(TradeLedger.get(p.server).deal(token).getBoolean("creativeSource"),"Ledger records creative source");
  h.assertTrue(Trade.order(p,token,n.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("replay")&&reputation(m,p)==rep&&chest(m,n).countItem(Items.WHEAT)==16,"Replay awards nothing twice");
  p.getInventory().add(new ItemStack(Items.WHEAT,3));h.assertTrue(Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.DONATE,"minecraft:wheat",16).equals("ok")&&p.getInventory().countItem(Items.WHEAT)==3&&reputation(m,p)>rep,"Creative donation preserves held items and rewards mayor");
  rep=reputation(m,p);h.assertTrue(!Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.DONATE,"minecraft:wheat",1).equals("ok")&&reputation(m,p)==rep,"Filled demand grants no more rewards");n.discard();SettlementData.get(p.server).remove(m.s.id());h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=100) public static void creativePermissionIsCheckedAgainOnServer(GameTestHelper h){
  var m=market(h);var n=worker(h,m,"mill",Profession.MILLER,0,Resident.Life.ADULT);var p=player(m,n,"CreativeSwitch");p.setGameMode(GameType.CREATIVE);Trade.view(p,n);p.setGameMode(GameType.SURVIVAL);
  h.assertTrue(Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("stale")&&chest(m,n).countItem(Items.WHEAT)==0&&reputation(m,p)==0,"Stale creative card cannot create survival goods");
  p.getInventory().add(new ItemStack(Items.WHEAT,16));h.assertTrue(Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.SELL,"minecraft:wheat",16).equals("ok")&&p.getInventory().countItem(Items.WHEAT)==0,"Survival still consumes actual goods");
  p.setGameMode(GameType.SPECTATOR);h.assertTrue(!Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.DONATE,"minecraft:wheat",1).equals("ok"),"Spectator has no creative supply");
  p.setGameMode(GameType.CREATIVE);h.assertTrue(!Trade.order(p,UUID.randomUUID(),n.getUUID(),Trade.DONATE,"minecraft:bedrock",1).equals("ok"),"Creative does not forge a non-offered resource");n.discard();SettlementData.get(p.server).remove(m.s.id());h.succeed();
 }
}
