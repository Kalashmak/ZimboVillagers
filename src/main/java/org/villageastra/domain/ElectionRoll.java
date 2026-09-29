package org.villageastra.domain;
import java.util.*;
/** Exact reputation and uninterrupted threshold seniority. Events are ordered on the server. */
public final class ElectionRoll {
 /** AD-128 (was AD-102's 640): the reputation the office takes. Two fresh diamond armour sets with two diamond tool sets given to residents
  *  (2 × 560 coins × 8 = 8960) reach it and one set (4480) does not; three fully enchanted diamond pieces do too. On the same per-coin scale
  *  that is ≈960 coins of useful donations or ≈3840 coins of useful sales; decay is unchanged (1 a period). Elections won under the old
  *  threshold of 128 stay valid in old saves (LEGACY_MINIMUM); scores are not rescaled. */
 public static final long MINIMUM=7680, LEGACY_MINIMUM=128, PERIOD=24000;
 public record Band(long low,long high,long event){}
 public record Account(UUID player,long gifts,long theft,long decay,boolean candidate,List<Band> bands){
  public Account{Objects.requireNonNull(player);bands=List.copyOf(bands);}
  public long score(){return Math.subtractExact(Math.subtractExact(gifts,theft),decay);}
  public long reached(long score){return bands.stream().filter(b->b.low<=score&&score<=b.high).findFirst().orElseThrow().event;}
 }
 private final Map<UUID,Account> accounts=new LinkedHashMap<>();private long sequence,lastDecay;
 public Collection<Account> accounts(){return Collections.unmodifiableCollection(accounts.values());}
 public long sequence(){return sequence;}public long lastDecay(){return lastDecay;}
 public Account account(UUID id){return accounts.getOrDefault(id,new Account(id,0,0,0,false,List.of()));}
 public void candidate(UUID id,boolean value){var a=account(id);if(a.candidate==value)return;long next=Math.addExact(sequence,1);accounts.put(id,new Account(id,a.gifts,a.theft,a.decay,value,a.bands));sequence=next;}
 public void gift(UUID id,long count){change(id,count,true);}
 public void theft(UUID id,long count){change(id,count,false);}
 private void change(UUID id,long count,boolean gift){
  if(count<=0)throw new IllegalArgumentException("Nonpositive reputation event");var a=account(id);
  long gifts=gift?Math.addExact(a.gifts,count):a.gifts,theft=gift?a.theft:Math.addExact(a.theft,count),next=Math.addExact(sequence,1);
  var updated=changed(a,gifts,theft,a.decay,next);accounts.put(id,updated);sequence=next;
 }
 private static Account changed(Account a,long gifts,long theft,long decay,long event){
  long score=Math.subtractExact(Math.subtractExact(gifts,theft),decay);var bands=new ArrayList<Band>();
  for(var b:a.bands)if(b.low<=score)bands.add(new Band(b.low,Math.min(score,b.high),b.event));
  if(score>Math.max(0,a.score()))bands.add(new Band(Math.max(0,a.score())+1,score,event));
  return new Account(a.player,gifts,theft,decay,a.candidate,bands);
 }
 public boolean decay(long activeTicks){
  if(activeTicks<lastDecay)throw new IllegalArgumentException("Clock moved backwards");long rounds=(activeTicks-lastDecay)/PERIOD;if(rounds==0)return false;
  for(var a:List.copyOf(accounts.values()))if(a.score()>0){long next=Math.addExact(sequence,1);accounts.put(a.player,changed(a,a.gifts,a.theft,Math.addExact(a.decay,Math.min(rounds,a.score())),next));sequence=next;}
  lastDecay+=rounds*PERIOD;return true;
 }
 public Optional<Account> winner(){return accounts.values().stream().filter(a->a.candidate&&a.score()>=MINIMUM).min((a,b)->{int score=Long.compare(b.score(),a.score());return score!=0?score:Long.compare(a.reached(a.score()),b.reached(b.score()));});}
 public static ElectionRoll restore(long sequence,long lastDecay,Collection<Account> accounts){
  if(sequence<0||lastDecay<0)throw new IllegalArgumentException("Invalid election clock");var roll=new ElectionRoll();roll.sequence=sequence;roll.lastDecay=lastDecay;
  var events=new HashSet<Long>();
  for(var a:accounts){
   if(a.gifts<0||a.theft<0||a.decay<0||a.decay>a.gifts||roll.accounts.putIfAbsent(a.player,a)!=null)throw new IllegalArgumentException("Invalid reputation account");
   long high=0,previousEvent=0;
   for(var b:a.bands){if(b.low<=0||b.low-1!=high||b.high<b.low||b.high>a.score()||b.event<=previousEvent||b.event>sequence||!events.add(b.event))throw new IllegalArgumentException("Invalid achievement history");high=b.high;previousEvent=b.event;}
   if(high!=Math.max(0,a.score()))throw new IllegalArgumentException("Incomplete achievement history");
  }
  return roll;
 }
}
