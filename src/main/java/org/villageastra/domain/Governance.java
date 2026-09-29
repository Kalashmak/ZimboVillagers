package org.villageastra.domain;
import java.util.*;
/** Office and saved orders are distinct from online presence. Election ranking is a separate caller. */
public final class Governance {
 private UUID playerMayor;private long epoch,revision;private final Set<UUID> paused=new LinkedHashSet<>();
 public UUID playerMayor(){return playerMayor;} public long epoch(){return epoch;}public long revision(){return revision;}
 public boolean canManage(UUID player,long expectedEpoch){return player!=null&&player.equals(playerMayor)&&epoch==expectedEpoch;}
 public boolean paused(UUID project){return paused.contains(project);}public Set<UUID> pausedProjects(){return Collections.unmodifiableSet(paused);}
 public boolean recordOrder(UUID actor,long expectedEpoch,long expectedRevision){if(!canManage(actor,expectedEpoch)||revision!=expectedRevision)return false;revision=Math.addExact(revision,1);return true;}
 public void appointPlayer(UUID player){Objects.requireNonNull(player);if(Objects.equals(playerMayor,player))return;epoch=Math.addExact(epoch,1);playerMayor=player;}
 public void appointNpc(){if(playerMayor!=null){epoch=Math.addExact(epoch,1);playerMayor=null;}}
 private long nextElection,lastElection,lastScore,lastReached;private UUID lastWinner;
 public long nextElection(){return nextElection;}public long lastElection(){return lastElection;}public long lastScore(){return lastScore;}public long lastReached(){return lastReached;}public UUID lastWinner(){return lastWinner;}
 public boolean startSchedule(long now){if(nextElection!=0)return false;nextElection=Math.addExact(now,ElectionRoll.PERIOD);return true;}
 public void recordElection(long now,ElectionRoll.Account winner){if(nextElection==0||now<nextElection)throw new IllegalStateException("Election is not due");lastElection=now;nextElection=Math.addExact(now,ElectionRoll.PERIOD);lastWinner=winner==null?null:winner.player();lastScore=winner==null?0:winner.score();lastReached=winner==null?0:winner.reached(winner.score());}
 public void restoreElection(long next,long last,UUID winner,long score,long reached){if(next<0||last<0||(next==0&&last!=0)||(next!=0&&next<=last)||(winner==null&&(score!=0||reached!=0))||(winner!=null&&(last==0||score<ElectionRoll.LEGACY_MINIMUM||reached<=0)))throw new IllegalArgumentException("Invalid saved election");nextElection=next;lastElection=last;lastWinner=winner;lastScore=score;lastReached=reached;}
 public boolean setPaused(UUID actor,long expectedEpoch,long expectedRevision,UUID project,boolean value){
  if(!canManage(actor,expectedEpoch)||expectedRevision!=revision)return false;
  long next=Math.addExact(revision,1);if(value)paused.add(Objects.requireNonNull(project));else paused.remove(Objects.requireNonNull(project));revision=next;return true;
 }
 public static Governance restore(UUID player,long epoch,long revision,Collection<UUID> paused){
  if(epoch<0||revision<0||(player!=null&&epoch==0)||new HashSet<>(paused).size()!=paused.size())throw new IllegalArgumentException("Invalid governance record");
  var g=new Governance();g.playerMayor=player;g.epoch=epoch;g.revision=revision;g.paused.addAll(paused);return g;
 }
}
