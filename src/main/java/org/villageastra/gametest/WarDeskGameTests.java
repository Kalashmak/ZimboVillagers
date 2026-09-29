package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-049: campaigns, sieges and annexations are given from the office by the mayor, with the same rules the operator commands use. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class WarDeskGameTests {
 private static SettlementData.Entry village(GameTestHelper h,BlockPos local){
  var l=h.getLevel();var center=h.absolutePos(local);var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<10;x++)for(int z=-2;z<10;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  s.addHome(new Settlement.Home(Settlement.childId(s.id(),"home"),4,4,true));
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return e;
 }
 private static net.minecraft.server.level.ServerPlayer mayor(GameTestHelper h,SettlementData.Entry e){
  var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WarMayor"));
  p.setPos(e.center().getX()+1,e.center().getY(),e.center().getZ()+1);e.settlement().appointPlayerMayor(p.getUUID());return p;
 }
 @GameTest(template="empty",timeoutTicks=200) public static void officeGivesCampaignsOnlyToTheMayorWithRealSoldiers(GameTestHelper h){
  var home=village(h,new BlockPos(2,3,2));var other=village(h,new BlockPos(34,3,28));
  var p=mayor(h,home);long epoch=home.settlement().governance().epoch();
  var visitor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WarVisitor"));visitor.setPos(home.center().getX()+1,home.center().getY(),home.center().getZ()+1);
  h.assertTrue(Warfare.order(visitor,home.settlement().id(),epoch,0,other.settlement().id()).equals("mayor"),"A visitor gives no war orders");
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,0,home.settlement().id()).equals("target"),"A settlement cannot march on itself");
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,0,other.settlement().id()).equals("no_army"),"Without barracks and soldiers there is no campaign");
  var barracks=new Settlement.Building(Settlement.childId(home.settlement().id(),"building/barracks"),"barracks",6,0,0);home.settlement().addBuilding(barracks);
  var chestPos=LogisticsRoutes.position(home,barracks);h.getLevel().setBlock(chestPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
  h.getLevel().setBlock(chestPos,VillageAstra.OWNED_CHEST.get().defaultBlockState(),3);
  var chest=LogisticsRoutes.chest(h.getLevel(),home,barracks);
  for(int i=0;i<6;i++)chest.setItem(i,new ItemStack(Items.OAK_FENCE,64));
  chest.setItem(6,new ItemStack(Items.CAMPFIRE,64));chest.setItem(7,new ItemStack(Items.TNT,64));chest.setItem(8,new ItemStack(Items.BREAD,64));
  var soldier=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);
  home.settlement().admit(soldier,home.settlement().homes().iterator().next().id());soldier.trainMilitary();home.settlement().assign(soldier.id(),Profession.SOLDIER,barracks.id());
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,0,other.settlement().id()).isEmpty(),"With soldiers and real supplies the campaign is mustered");
  var army=Sieges.armies(h.getLevel().getServer()).stream().filter(a->a.getUUID("attacker").equals(home.settlement().id())).findFirst().orElse(null);
  h.assertTrue(army!=null&&Sieges.supply(army,"fences")>0,"The campaign really carries supplies from the barracks chest");
  h.assertTrue(!Warfare.order(p,home.settlement().id(),epoch,1,other.settlement().id()).isEmpty(),"A siege without a closed ring is refused with a reason");
  Sieges.clear();SettlementData.get(h.getLevel().getServer()).remove(home.settlement().id());SettlementData.get(h.getLevel().getServer()).remove(other.settlement().id());
  h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void officeShowsNeighboursAndRefusesAnnexationWithoutSupplies(GameTestHelper h){
  var home=village(h,new BlockPos(2,3,2));var other=village(h,new BlockPos(34,3,28));
  var p=mayor(h,home);long epoch=home.settlement().governance().epoch();
  var view=new net.minecraft.nbt.CompoundTag();view.putUUID("village",home.settlement().id());
  Warfare.addView(p,view);var war=view.getCompound("war");
  h.assertTrue(war.getBoolean("mayor")&&war.getString("state").equals("none"),"The desk knows the mayor and that no campaign is under way");
  var rows=war.getList("neighbours",Tag.TAG_COMPOUND);var row=(net.minecraft.nbt.CompoundTag)null;
  for(var raw:rows){var candidate=(net.minecraft.nbt.CompoundTag)raw;if(candidate.getUUID("village").equals(other.settlement().id()))row=candidate;}
  h.assertTrue(row!=null&&rows.size()<=Warfare.NEIGHBOURS,"The neighbour is listed among the nearest: "+rows.size());
  h.assertTrue(row.getLong("price")>0&&row.getLong("supplied")==0&&row.getInt("distance")>0,"Price and distance are shown, nothing is supplied yet");
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,2,other.settlement().id()).equals("supplies"),"Annexation without caravan supplies is refused");
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,3,other.settlement().id()).equals("none"),"There is nothing to accept");
  h.assertTrue(Warfare.order(p,home.settlement().id(),epoch+1,0,other.settlement().id()).equals("mayor"),"A stale office epoch gives no orders");
  SettlementData.get(h.getLevel().getServer()).remove(home.settlement().id());SettlementData.get(h.getLevel().getServer()).remove(other.settlement().id());
  h.succeed();
 }

 /** AD-159 VI: the army sent to take a town on its own needs Military VI, and turns back from defenders stronger than itself. */
 @GameTest(template="empty",timeoutTicks=200) public static void aCaptureArmyTurnsBackFromStrongerDefenders(GameTestHelper h){
  var home=village(h,new BlockPos(2,3,2));var other=village(h,new BlockPos(34,3,28));var p=mayor(h,home);long epoch=home.settlement().governance().epoch();var l=h.getLevel();
  var bodies=new ArrayList<ResidentEntity>();
  try{
   h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,5,other.settlement().id()).equals("research"),"Without Military VI no army goes on its own");
   var view=new net.minecraft.nbt.CompoundTag();view.putUUID("village",home.settlement().id());Warfare.addView(p,view);h.assertTrue(!view.getCompound("war").getBoolean("capture"),"The desk offers no capture before Military VI");h.assertTrue(!Trails.view(l,home).getBoolean("capture"),"Nor the atlas");
   var record=BookResearch.inspect(l,home);var done=record.getList("legacyDone",Tag.TAG_STRING);done.add(net.minecraft.nbt.StringTag.valueOf(Army.CAPTURE));record.put("legacyDone",done);BookResearch.store(l,home,record);ResearchKnobs.forget(home.settlement().id());
   view=new net.minecraft.nbt.CompoundTag();view.putUUID("village",home.settlement().id());Warfare.addView(p,view);h.assertTrue(view.getCompound("war").getBoolean("capture"),"With Military VI the campaign button captures");h.assertTrue(Trails.view(l,home).getBoolean("capture"),"And the atlas offers the capture of the neighbour picked there");
   h.assertTrue(Warfare.order(p,home.settlement().id(),epoch,5,other.settlement().id()).equals("no_army"),"With Military VI the order needs only an army");
   var soldier=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);home.settlement().admit(soldier,home.settlement().homes().iterator().next().id());soldier.trainMilitary();
   for(int i=0;i<3;i++){var g=new Resident(UUID.randomUUID(),Resident.Life.ADULT,false,null,null,-1);other.settlement().admit(g,other.settlement().homes().iterator().next().id());g.trainMilitary();}
   var npc=VillageAstra.RESIDENT.get().create(l);npc.bind(home.settlement().id(),soldier);npc.setNoAi(true);npc.moveTo(home.center().getX()+.5,home.center().getY()+1,home.center().getZ()+.5,0,0);l.addFreshEntity(npc);bodies.add(npc);
   var army=new net.minecraft.nbt.CompoundTag();army.putInt("schema",1);army.putUUID("id",UUID.randomUUID());army.putUUID("attacker",home.settlement().id());army.putUUID("target",other.settlement().id());
   army.putString("dimension",home.dimension());army.put("stamps",new net.minecraft.nbt.CompoundTag());army.put("supply",new net.minecraft.nbt.CompoundTag());army.putString("state",Sieges.READY);army.putBoolean("auto",true);
   var list=new net.minecraft.nbt.ListTag();list.add(net.minecraft.nbt.NbtUtils.createUUID(soldier.id()));army.put("soldiers",list);
   // The defenders: three guards of the target (weight one each, no weapon), and no Defence.
   for(var r:other.settlement().residents())if(r.military()&&r.profession()==null){var b=new Settlement.Building(Settlement.childId(other.settlement().id(),"building/guard_house/"+r.id()),"guard_house",0,0,0);other.settlement().addBuilding(b);other.settlement().assign(r.id(),Profession.GUARD,b.id());}
   h.assertTrue(Army.strength(l,army)==1&&Army.defence(l,other)==3,"One bare soldier against three bare guards: "+Army.strength(l,army)+" / "+Army.defence(l,other));
   var state=Sieges.tick(l,army,SettlementData.get(l.getServer()).clock().ticks());
   h.assertTrue(state.equals(Sieges.WITHDRAWN)&&army.getString("reason").equals("defenders_stronger"),"The army turns back: "+state+" "+army.getString("reason"));
  }finally{for(var b:bodies)b.discard();Sieges.clear();SettlementData.get(l.getServer()).remove(home.settlement().id());SettlementData.get(l.getServer()).remove(other.settlement().id());}
  h.succeed();
 }
}
