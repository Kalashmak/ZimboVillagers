package org.villageastra.world;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-158 (level II of the cartography ladder): with the whole village on his map, the cartographer goes round its dark corners and puts
 *  a torch in each, out of his own chest. He does it only when there is nothing left to survey — the map comes first — and only where
 *  nothing is burning already, so a village lit once is not lit again. */
public final class CartographerLightGoal extends Goal {
 public static final int REACH=3,PLACE_TICKS=40,LOOK_EVERY=60;
 /** village/spot → the cartographer walking there, so two of them do not carry a torch to one corner. */
 private static final Map<String,UUID> CLAIMS=new ConcurrentHashMap<>();
 private final ResidentEntity cartographer;private BlockPos spot;private int placing,repath,rest;
 public CartographerLightGoal(ResidentEntity cartographer){this.cartographer=cartographer;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 public static void clear(){CLAIMS.clear();}
 private record Duty(SettlementData.Entry entry,Settlement.Building office){}
 private Duty duty(){
  if(!(cartographer.level() instanceof ServerLevel l)||cartographer.settlementId()==null||cartographer.escortPlayer()!=null||!cartographer.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(cartographer.settlementId());
  if(e==null||!e.dimension().equals(l.dimension().location().toString()))return null;
  var r=e.settlement().resident(cartographer.getUUID());var b=e.settlement().workplace(cartographer.getUUID());
  if(r==null||!r.alive()||!r.educated()||r.profession()!=Profession.CARTOGRAPHER||b==null||!b.type().equals(CartographyLadder.HOUSE))return null;
  return CartographyLadder.lights(l,e)?new Duty(e,b):null;
 }
 private static String key(UUID village,BlockPos spot){return village+"/"+spot.asLong();}
 private void release(){if(spot!=null&&cartographer.settlementId()!=null)CLAIMS.remove(key(cartographer.settlementId(),spot),cartographer.getUUID());spot=null;placing=0;}
 @Override public boolean canUse(){
  if(--rest>0)return false;
  rest=LOOK_EVERY;
  var d=duty();if(d==null||!CargoCustody.mayStartWork(cartographer))return false;
  var l=(ServerLevel)cartographer.level();
  // The map first: while a chunk of the area is still unopened, the cartographer is out surveying it.
  if(!CartographyLadder.mapped(l,d.entry()))return false;
  var chest=LogisticsRoutes.chest(l,d.entry(),d.office());
  if(chest==null||chest.countItem(Items.TORCH)==0){cartographer.workStatus("cartographer_missing_torch");return false;}
  var found=CartographyLadder.dark(l,d.entry(),cartographer.blockPosition());
  if(found==null){cartographer.workStatus("cartographer_lit");return false;}
  var village=d.entry().settlement().id();
  if(CLAIMS.putIfAbsent(key(village,found),cartographer.getUUID())!=null)return false;
  spot=found;return true;
 }
 @Override public boolean canContinueToUse(){return spot!=null&&duty()!=null;}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 @Override public void tick(){
  var d=duty();if(d==null||spot==null)return;var l=(ServerLevel)cartographer.level();
  if(!l.hasChunkAt(spot)){cartographer.workStatus("cartographer_unloaded");release();return;}
  double dx=cartographer.getX()-spot.getX()-.5,dz=cartographer.getZ()-spot.getZ()-.5;
  if(dx*dx+dz*dz<=REACH*REACH){
   cartographer.getNavigation().stop();cartographer.displayWorkItem(new ItemStack(Items.TORCH));cartographer.workStatus("cartographer_lighting");
   if(++placing>=PLACE_TICKS){
    if(CartographyLadder.light(l,d.entry(),d.office(),spot))cartographer.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    else cartographer.workStatus("cartographer_missing_torch");
    release();}
   return;}
  placing=0;
  if(--repath<=0){repath=40;
   if(!cartographer.getNavigation().moveTo(spot.getX()+.5,spot.getY(),spot.getZ()+.5,.7)&&cartographer.getNavigation().isDone()){
    cartographer.workStatus("cartographer_blocked");release();return;}}
  cartographer.workStatus("cartographer_walking");
 }
 @Override public void stop(){cartographer.getNavigation().stop();cartographer.displayWorkItem(ItemStack.EMPTY);release();}
}
