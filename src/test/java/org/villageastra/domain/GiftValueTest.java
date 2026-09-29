package org.villageastra.domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** AD-128 oracles: two fresh diamond sets reach the mayor's threshold, one does not; three fully enchanted pieces do; wear, curses and repeats count. */
class GiftValueTest {
 private static final GiftValue.Rules R=GiftValue.DEFAULT;
 private static final int[] ARMOR={80,128,112,64},TOOLS={32,48,48,16,32};
 private static long gift(int coins,int durability,int enchant,int remembered){return GiftValue.unit(R,coins*1000L,durability,enchant,GiftValue.repeat(R,remembered));}
 private static long sets(int n,int durability){long sum=0;for(int round=0;round<n;round++)for(var kinds:List.of(ARMOR,TOOLS))for(int c:kinds)sum+=gift(c,durability,1000,round);return sum;}
 private static GiftValue.Enchant e(int level,int max){return new GiftValue.Enchant(level,max,false,false);}
 private static GiftValue.Enchant treasure(){return new GiftValue.Enchant(1,1,true,false);}
 @Test void twoFreshDiamondSetsMakeAMayorOneDoesNot(){
  assertEquals(4480,sets(1,1000));assertTrue(sets(1,1000)<ElectionRoll.MINIMUM);
  assertEquals(8960,sets(2,1000));assertTrue(sets(2,1000)>=ElectionRoll.MINIMUM);
  assertEquals(7160,sets(2,800),"each unit is floored: 7168 before flooring");assertTrue(sets(2,800)<ElectionRoll.MINIMUM);
  assertEquals(8056,sets(2,900),"8064 before flooring");assertTrue(sets(2,900)>=ElectionRoll.MINIMUM);
 }
 @Test void threeFullyEnchantedPiecesMakeAMayor(){
  int chest=GiftValue.enchant(R,List.of(e(4,4),e(3,3),treasure(),e(3,3)));assertEquals(3500,chest);
  int helmet=GiftValue.enchant(R,List.of(e(4,4),e(3,3),treasure(),e(3,3),e(1,1),e(3,3)));assertEquals(4000,helmet,"capped");
  int legs=GiftValue.enchant(R,List.of(e(4,4),e(3,3),treasure(),e(3,3),new GiftValue.Enchant(3,3,true,false)));assertEquals(4000,legs);
  assertEquals(3584,gift(128,1000,chest,0));assertEquals(2560,gift(80,1000,helmet,0));assertEquals(3584,gift(112,1000,legs,0));
  assertEquals(9728,gift(128,1000,chest,0)+gift(80,1000,helmet,0)+gift(112,1000,legs,0));assertTrue(9728>=ElectionRoll.MINIMUM);
 }
 @Test void wearEnchantmentsAndCursesScaleTheValue(){
  assertEquals(288,gift(48,500,GiftValue.enchant(R,List.of(e(5,5))),0));
  assertEquals(500,GiftValue.durability(true,1561,780));assertEquals(1000,GiftValue.durability(false,0,0));assertEquals(0,GiftValue.durability(true,100,150));
  // Curse of Vanishing: a curse that vanilla also marks treasure-only lowers the value.
  assertEquals(192,gift(32,1000,GiftValue.enchant(R,List.of(new GiftValue.Enchant(1,1,true,true))),0));
  assertEquals(1500,GiftValue.enchant(R,List.of(e(9,5))),"level above max is clamped");
  assertEquals(500,GiftValue.enchant(R,List.of(new GiftValue.Enchant(1,1,true,true),new GiftValue.Enchant(1,1,true,true),new GiftValue.Enchant(1,1,true,true))),"floor");
 }
 @Test void repeatedKindsDiminishAndRecover(){
  var q=GiftValue.stack(R,8000,1000,1000,10,0,0);assertEquals(7,q.accepted(),"units worth nothing stay with the player");assertEquals(188,q.reputation());
  long[] m=GiftValue.recover(R,7,100,100+5*24000+10,24000);assertEquals(2,m[0]);assertEquals(100+5*24000,m[1]);
  assertEquals(32,GiftValue.stack(R,8000,1000,1000,1,0,2).reputation());assertEquals(0,GiftValue.recover(R,3,0,24000*9,24000)[0]);
  var bought=GiftValue.stack(R,1000,1000,1000,5,3,0);assertEquals(5,bought.accepted());assertEquals(3,bought.free());assertEquals(16,bought.reputation(),"bought-back units earn nothing");
  assertEquals(0,gift(0,1000,1000,0));assertEquals(0,GiftValue.unit(R,500,1000,1000,1000),"under one coin a unit is not a gift");
 }
 @Test void factorsPrintExactlyWithoutRoundingToZero(){
  assertEquals("×1,0",GiftValue.factor(1000));assertEquals("×3,5",GiftValue.factor(3500));assertEquals("×4,0",GiftValue.factor(4000));
  assertEquals("×0,75",GiftValue.factor(750),"a curse on a sword");assertEquals("×0,062",GiftValue.factor(62),"the sixth repeat still counts");assertEquals("×0,001",GiftValue.factor(1));assertEquals("×0,0",GiftValue.factor(0));
 }
 @Test void balanceFileMatchesTheRules()throws Exception{
  try(var s=GiftValueTest.class.getResourceAsStream("/data/villageastra/balance/gifts.json")){
   var o=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(s),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();var v=o.getAsJsonObject("values");
   int set=0;for(var k:List.of("helmet","chestplate","leggings","boots","sword","pickaxe","axe","shovel","hoe"))set+=v.get("minecraft:diamond_"+k).getAsInt();assertEquals(560,set);
   assertEquals(R.reputationPerCoin(),o.get("reputation_per_coin").getAsInt());assertEquals(R.fullRepeats(),o.get("full_repeats").getAsInt());assertEquals(R.repeatZeroAfter(),o.get("repeat_zero_after").getAsInt());
   var en=o.getAsJsonObject("enchant");assertEquals(R.maxPermille(),en.get("max_permille").getAsInt());assertEquals(R.curse(),en.get("curse_permille").getAsInt());
  }
 }
}
