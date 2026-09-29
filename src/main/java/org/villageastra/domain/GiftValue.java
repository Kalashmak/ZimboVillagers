package org.villageastra.domain;
import java.util.*;
/** AD-128: the reputation a gift to a resident is worth. Pure integer arithmetic in permille, floored per unit, so the dialog quote and the
 *  server order are the same number. unit = coins‰ × reputation per coin × durability‰ × enchantments‰ × repeat‰ / 10^12. */
public final class GiftValue {
 private GiftValue(){}
 /** One enchantment on the item: its level (clamped to max), whether it is a treasure or a curse. Curses are also treasure in vanilla, so the curse is checked first. */
 public record Enchant(int level,int max,boolean treasure,boolean curse){}
 public record Rules(int reputationPerCoin,int minimumUnitCoins,int fullRepeats,int repeatZeroAfter,int recoveryPerPeriod,int confirmAt,int normal,int treasure,int curse,int maxPermille,int minPermille){
  public Rules{if(reputationPerCoin<1||minimumUnitCoins<1||fullRepeats<1||repeatZeroAfter<=fullRepeats||recoveryPerPeriod<1||confirmAt<1||normal<0||treasure<normal||curse>0||minPermille<1||minPermille>1000||maxPermille<1000)throw new IllegalArgumentException("Invalid gift rules");}
 }
 public static final Rules DEFAULT=new Rules(8,1,2,12,1,512,500,1000,-250,4000,500);
 /** Remaining durability in permille (floor); an item that cannot wear out counts whole. */
 public static int durability(boolean damageable,int maxDamage,int damage){
  if(!damageable||maxDamage<=0)return 1000;int left=Math.max(0,Math.min(maxDamage,maxDamage-damage));return (int)((long)left*1000/maxDamage);
 }
 public static int enchant(Rules r,Collection<Enchant> enchants){
  long sum=1000;for(var e:enchants){if(e.max<=0||e.level<=0)continue;int w=e.curse?r.curse:e.treasure?r.treasure:r.normal;sum+=(long)w*Math.min(e.level,e.max)/e.max;}
  return (int)Math.max(r.minPermille,Math.min(r.maxPermille,sum));
 }
 /** Factor for the next unit of a kind when n units of it are remembered: whole for the first ones, then halving, then nothing. */
 public static int repeat(Rules r,int n){if(n<0)throw new IllegalArgumentException("Negative memory");if(n<r.fullRepeats)return 1000;if(n>=r.repeatZeroAfter)return 0;return 1000>>(n-r.fullRepeats+1);}
 public static long unit(Rules r,long basePermilleCoins,int durability,int enchant,int repeat){
  if(basePermilleCoins<(long)r.minimumUnitCoins*1000)return 0;
  long v=Math.multiplyExact(Math.multiplyExact(basePermilleCoins,r.reputationPerCoin),(long)durability);v=Math.multiplyExact(v,(long)enchant);v=Math.multiplyExact(v,(long)repeat);
  return v/1_000_000_000_000L;
 }
 /** Remembered count and its start after the active clock moved on: one unit is forgotten per period. */
 public static long[] recover(Rules r,long count,long since,long now,long period){
  if(count<=0||now<=since)return new long[]{Math.max(0,count),since};long rounds=(now-since)/period;
  return new long[]{Math.max(0,count-rounds*r.recoveryPerPeriod),since+rounds*period};
 }
 /** A permille factor as the card prints it, exactly: 1000 -> «×1,0», 750 -> «×0,75», 62 -> «×0,062» (never «×0,0» while it still counts). */
 public static String factor(int permille){int whole=permille/1000,part=Math.abs(permille%1000);return "×"+whole+","+(part==0?"0":String.format("%03d",part).replaceAll("0+$",""));}
 public record Quote(int accepted,int free,long reputation,int firstRepeat,int lastRepeat){}
 /** Units of one stack: the first `bought` units are returns of goods bought from the village (accepted, worth nothing, not remembered);
  *  then fresh units are accepted while each is still worth something. */
 public static Quote stack(Rules r,long basePermilleCoins,int durability,int enchant,int count,int bought,int remembered){
  int free=Math.min(Math.max(0,bought),count),accepted=free,first=-1,last=-1;long total=0;int n=remembered;
  for(int i=free;i<count;i++){int rep=repeat(r,n);long u=unit(r,basePermilleCoins,durability,enchant,rep);if(u<=0)break;if(first<0)first=rep;last=rep;total=Math.addExact(total,u);accepted++;n++;}
  return new Quote(accepted,free,total,first<0?repeat(r,remembered):first,last<0?repeat(r,remembered):last);
 }
}
