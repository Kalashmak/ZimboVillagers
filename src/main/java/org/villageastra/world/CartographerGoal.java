package org.villageastra.world;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-037: an educated cartographer walks to the nearest unopened chunk, stands in it while surveying and spends one paper per chunk. */
public final class CartographerGoal extends Goal {
 public static final int SURVEY_TICKS=60,REACH=6;
 /** village/chunk → cartographer walking there, so several cartographers open the shared map faster instead of racing. */
 private static final Map<String,UUID> CLAIMS=new ConcurrentHashMap<>();
 private final ResidentEntity cartographer;private final boolean withoutPlayers;private ChunkPos target;private int surveying,repath;
 public CartographerGoal(ResidentEntity cartographer){this(cartographer,false);}
 public CartographerGoal(ResidentEntity cartographer,boolean withoutPlayers){this.cartographer=cartographer;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private record Duty(SettlementData.Entry entry,Settlement.Building office){}
 private Duty duty(){
  if(!(cartographer.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||cartographer.settlementId()==null||cartographer.escortPlayer()!=null||!cartographer.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(cartographer.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(cartographer.getUUID());var b=e.settlement().workplace(cartographer.getUUID());
  return r!=null&&r.alive()&&r.educated()&&r.profession()==Profession.CARTOGRAPHER&&b!=null&&b.type().equals("cartographer")?new Duty(e,b):null;
 }
 private static String key(UUID village,ChunkPos c){return village+"/"+c.toLong();}
 private void release(){if(target!=null&&cartographer.settlementId()!=null)CLAIMS.remove(key(cartographer.settlementId(),target),cartographer.getUUID());target=null;surveying=0;}
 public static void clear(){CLAIMS.clear();}
 @Override public boolean canUse(){var d=duty();if(d==null||!CargoCustody.mayStartWork(cartographer))return false;var l=(ServerLevel)cartographer.level();
  var chest=LogisticsRoutes.chest(l,d.entry(),d.office());if(chest==null||chest.countItem(Items.PAPER)==0){cartographer.workStatus("cartographer_missing_paper");return false;}
  var claimed=new HashSet<Long>();var prefix=d.entry().settlement().id()+"/";CLAIMS.forEach((k,v)->{if(k.startsWith(prefix)&&!v.equals(cartographer.getUUID()))claimed.add(Long.parseLong(k.substring(prefix.length())));});
  target=Atlas.next(l,d.entry(),cartographer.blockPosition(),claimed);if(target==null){cartographer.workStatus("cartographer_done");return false;}
  CLAIMS.put(key(d.entry().settlement().id(),target),cartographer.getUUID());return true;}
 @Override public boolean canContinueToUse(){return target!=null&&duty()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var d=duty();if(d==null||target==null)return;var l=(ServerLevel)cartographer.level();
  int x=target.getMiddleBlockX(),z=target.getMiddleBlockZ();
  if(!l.hasChunk(target.x,target.z)){cartographer.workStatus("cartographer_unloaded");release();return;}
  int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);double dx=cartographer.getX()-x-.5,dz=cartographer.getZ()-z-.5;
  if(new ChunkPos(cartographer.blockPosition()).equals(target)||dx*dx+dz*dz<=REACH*REACH){
   cartographer.getNavigation().stop();cartographer.displayWorkItem(new ItemStack(Items.MAP));cartographer.workStatus("cartographer_surveying");
   if(++surveying>=SURVEY_TICKS){var now=SettlementData.get(l.getServer()).clock().ticks();
    if(Atlas.survey(l,d.entry(),d.office(),cartographer.getUUID(),target,now))cartographer.swing(net.minecraft.world.InteractionHand.MAIN_HAND);else cartographer.workStatus("cartographer_missing_paper");release();}
   return;}
  surveying=0;if(--repath<=0){repath=40;if(!cartographer.getNavigation().moveTo(x+.5,y,z+.5,.7)&&cartographer.getNavigation().isDone()){cartographer.workStatus("cartographer_blocked");release();return;}}
  cartographer.workStatus("cartographer_walking");
 }
 @Override public void stop(){cartographer.getNavigation().stop();cartographer.displayWorkItem(ItemStack.EMPTY);release();}
}
