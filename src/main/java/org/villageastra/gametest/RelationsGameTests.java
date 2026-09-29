package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-100: how two villages stand with each other — one score per pair, moved by real deeds and softened by time. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class RelationsGameTests {
 @GameTest(template="empty",timeoutTicks=100) public static void aRelationIsOnePairMovedByDeedsAndSoftenedByTime(GameTestHelper h){
  var s=h.getLevel().getServer();var a=UUID.randomUUID();var b=UUID.randomUUID();
  h.assertTrue(Relations.score(s,a,b)==0&&Relations.standing(0).equals("neutral"),"Strangers are neutral");
  Relations.change(s,a,b,25,"embassy");
  h.assertTrue(Relations.score(s,a,b)==25&&Relations.score(s,b,a)==25,"The same score from either side");
  h.assertTrue(Relations.reasons(s,b,a).getCompound(0).getString("reason").equals("embassy"),"And it remembers why");
  Relations.change(s,a,b,-500,"conquest");
  h.assertTrue(Relations.score(s,a,b)==Relations.MIN&&Relations.standing(Relations.MIN).equals("hostile"),"Scores stay within -100..100");
  // Time softens every relation a point a day, toward neutral and never past it.
  long now=SettlementData.get(s).clock().ticks();Relations.tick(s,now);
  int before=Relations.score(s,a,b);Relations.tick(s,now+24000*3);
  h.assertTrue(Relations.score(s,a,b)==before+3,"Three days soften hostility by three: "+before+" -> "+Relations.score(s,a,b));
  var c=UUID.randomUUID();Relations.change(s,a,c,2,"caravan_delivered");Relations.tick(s,now+24000*10);
  h.assertTrue(Relations.score(s,a,c)==0,"A small warmth fades to neutral, not past it: "+Relations.score(s,a,c));
  h.succeed();
 }
 private record Town(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement s){}
 private static Town town(GameTestHelper h,BlockPos at){
  var l=h.getLevel();var center=h.absolutePos(at);var s=new Settlement(UUID.randomUUID());
  s.addBuilding(new Settlement.Building(Settlement.childId(s.id(),"building/town_hall"),"town_hall",0,0,0));
  for(int x=-2;x<8;x++)for(int z=-2;z<8;z++){l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)l.setBlock(center.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  l.setBlock(center.offset(1,1,4),VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);
  var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);SettlementData.get(l.getServer()).add(e);return new Town(l,e,s);
 }
 @GameTest(template="empty",timeoutTicks=100) public static void goodsThatArriveWarmTheTwoVillages(GameTestHelper h){
  var from=town(h,new BlockPos(4,3,4));var to=town(h,new BlockPos(20,3,4));
  try{
   var s=from.l.getServer();int before=Relations.score(s,from.s.id(),to.s.id());
   var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putString("kind","trade");t.putUUID("source",from.s.id());t.putUUID("destination",to.s.id());
   t.putString("dimension",from.l.dimension().location().toString());t.putString("item","minecraft:wheat");t.putInt("count",10);t.putInt("price",1);t.putInt("per",1);
   t.putUUID("caravaneer",UUID.randomUUID());var cargo=new ListTag();cargo.add(new ItemStack(Items.WHEAT,10).save(new CompoundTag()));t.put("cargo",cargo);t.put("stamps",new CompoundTag());
   t.putInt("takes",1);t.putInt("drops",0);t.putInt("delivered",0);t.putInt("lost",0);t.putLong("from",from.e.center().asLong());t.putLong("to",to.e.center().asLong());
   t.putDouble("progress",0);t.putBoolean("materialized",false);t.putString("state",Caravans.TRANSIT);Caravans.update(s,t);
   Caravans.arrive(from.l,t,1);
   h.assertTrue(t.getInt("delivered")==10,"The wheat really arrived: "+t.getInt("delivered"));
   h.assertTrue(Relations.score(s,from.s.id(),to.s.id())==before+2,"And the two villages warm to each other: "+Relations.score(s,from.s.id(),to.s.id()));
   t.putString("state",Caravans.CANCELLED);Caravans.update(s,t);
  }finally{SettlementData.get(from.l.getServer()).remove(from.s.id());SettlementData.get(to.l.getServer()).remove(to.s.id());}
  h.succeed();
 }
}
