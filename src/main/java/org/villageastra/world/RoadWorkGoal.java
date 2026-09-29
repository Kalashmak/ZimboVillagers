package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
/** AD-038: without a building project the builder paves, lights and repairs roads, carrying real materials from the hall chest. */
public final class RoadWorkGoal extends Goal {
 public static final double REACH_SQ=20.25;public static final int LABOR=20,WALL_WAIT=600;
 private final ResidentEntity builder;private final boolean withoutPlayers;private int labor,repath,idle,waited;private long waitCell;
 public RoadWorkGoal(ResidentEntity builder){this(builder,false);}
 public RoadWorkGoal(ResidentEntity builder,boolean withoutPlayers){this.builder=builder;this.withoutPlayers=withoutPlayers;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
 private record Duty(SettlementData.Entry entry,Settlement.Building hall){}
 private Duty duty(){
  if(!(builder.level() instanceof ServerLevel l)||l.getServer().getPlayerCount()==0&&!withoutPlayers||builder.settlementId()==null||builder.escortPlayer()!=null||!builder.isAlive())return null;
  var e=SettlementData.get(l.getServer()).entry(builder.settlementId());if(e==null||!e.dimension().equals(l.dimension().location().toString())||HallUpgradeGoal.pending(l,e.settlement().id())&&!HallUpgradeGoal.waiting(l,e))return null;
  if(Sieges.besieged(l.getServer(),e.settlement().id())){builder.workStatus("besieged");return null;}
  // AD-123: the builder out with the trail crew takes no road work.
  if(Trails.onTrail(l,e,builder.getUUID())){builder.workStatus("on_trail");return null;}
  var r=e.settlement().resident(builder.getUUID());var b=e.settlement().workplace(builder.getUUID());
  if(!(r!=null&&r.alive()&&r.profession()==Profession.BUILDER&&b!=null&&b.type().equals("town_hall")))return null;
  // AD-153: one road project is worked by one builder — the first of the hall's builders not out on a trail; the others help on buildings.
  return roadBuilder(l,e,builder.getUUID())?new Duty(e,b):null;
 }
 /** AD-153: whether this builder is the one who takes the village's road and wall work (the first by id of the builders not on a trail). */
 public static boolean roadBuilder(ServerLevel l,SettlementData.Entry e,java.util.UUID me){
  var crew=e.settlement().residents().stream().filter(x->x.alive()&&x.profession()==Profession.BUILDER).map(Resident::id).sorted().toList();
  if(crew.size()<2)return true;
  for(var id:crew)if(!Trails.onTrail(l,e,id))return id.equals(me);
  return false;
 }
 @Override public boolean canUse(){
  if(--idle>0)return false;idle=100;var d=duty();if(d==null||!CargoCustody.mayStartWork(builder))return false;var l=(ServerLevel)builder.level();var id=d.entry().settlement().id();
  if(!Roads.active(l,id)){var repair=Roads.maintenance(l,d.entry());if(repair==null)return false;Roads.order(l,d.entry(),repair);}
  idle=0;return true;
 }
 @Override public boolean canContinueToUse(){var d=duty();return d!=null&&Roads.active((ServerLevel)builder.level(),d.entry().settlement().id());}
 @Override public boolean requiresUpdateEveryTick(){return true;}
 private boolean walk(BlockPos target,double reachSq){
  if(builder.getEyePosition().distanceToSqr(Vec3.atCenterOf(target))<=reachSq){builder.getNavigation().stop();return true;}
  if(--repath<=0){repath=20;builder.getNavigation().moveTo(target.getX()+.5,target.getY()+1,target.getZ()+.5,.8);}return false;
 }
 @Override public void tick(){
  var d=duty();if(d==null)return;var l=(ServerLevel)builder.level();var e=d.entry();var project=Roads.project(l,e.settlement().id());if(project==null||project.getBoolean("complete"))return;
  var op=Roads.current(project);var chestPos=LogisticsRoutes.position(e,d.hall());
  boolean needsHall=op==null?!project.getList("returns",10).isEmpty():!Roads.needs(project).isEmpty()&&project.getList("cargo",10).stream().noneMatch(raw->ItemStack.of((net.minecraft.nbt.CompoundTag)raw).is(BuiltInRegistries.ITEM.get(new ResourceLocation(op.getString("item")))));
  // AD-153 (Construction V): a kennel wolf fetches the load from the hall chest; the builder waits where he works until it brings it.
  var errand=BuilderWolf.step(l,e,d.hall(),project,builder.getUUID(),builder.blockPosition(),op!=null&&needsHall);
  if(!errand.isEmpty()){builder.getNavigation().stop();builder.workStatus(errand);return;}
  if(needsHall){
   if(!walk(chestPos,9)){builder.workStatus("road_fetching");return;}
   if(builder.tickCount%20!=0)return;int moved=Roads.load(l,e,d.hall(),project);
   if(op!=null&&moved==0){builder.workStatus("road_missing_materials");
    // Maintenance never blocks the builder: without repair material the repair round is dropped and planned again later, and a piece
    // the hall cannot supply (a broken fence makes the round a "build") is left to the next round. A player's road waits for its material.
    boolean upkeep=project.getString("kind").equals("repair")||project.getBoolean("maintenance");
    if(upkeep&&project.getList("cargo",10).isEmpty()){project.putBoolean("complete",true);Roads.save(l,e.settlement().id(),project);}
    else if(upkeep){project.putInt("index",project.getInt("index")+1);Roads.save(l,e.settlement().id(),project);}}
   else if(op==null){var r=Roads.apply(l,e,project);builder.workStatus(r.equals("complete")?"road_complete":"road_unloading");}
   return;
  }
  if(op==null){var r=Roads.apply(l,e,project);builder.workStatus(r.equals("complete")?"road_complete":"road_unloading");return;}
  var target=BlockPos.of(op.getLong("pos"));builder.displayWorkItem(new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(op.getString("item")))));
  if(!walk(target,REACH_SQ)){labor=0;builder.workStatus("road_walking");return;}
  // AD-094: a wall block is not laid on anybody. The builder standing in the cell steps inside the ring; anybody else is waited for.
  if(op.getString("kind").equals("wall")){var inside=occupant(l,target);
   if(inside==builder){var c=e.center();var in=target.offset(2*Integer.signum(c.getX()-target.getX()),0,2*Integer.signum(c.getZ()-target.getZ()));
    builder.getNavigation().moveTo(in.getX()+.5,in.getY(),in.getZ()+.5,.8);labor=0;builder.workStatus("road_walking");return;}
   // Something that never leaves the cell does not stop the ring: after WALL_WAIT ticks at this cell it is skipped like a changed cell in Roads.apply.
   if(inside!=null){labor=0;if(waitCell!=op.getLong("pos")){waitCell=op.getLong("pos");waited=0;}
    if(++waited<WALL_WAIT){builder.workStatus("wall_waiting");return;}
    waited=0;project.putInt("index",project.getInt("index")+1);project.putInt("conflicts",project.getInt("conflicts")+1);Roads.save(l,e.settlement().id(),project);builder.workStatus("road_changed");return;}
   waited=0;}
  builder.getLookControl().setLookAt(Vec3.atCenterOf(target));if(++labor<LABOR)return;labor=0;
  var r=Roads.apply(l,e,project);if(r.equals("done"))builder.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
  builder.workStatus(op.getString("kind").equals("repair")?"road_repairing":op.getString("kind").equals("clear")||op.getString("kind").equals("wall_clear")?"clearing":r.equals("skipped")?"road_changed":"road_paving");
 }
 /** Whoever stands in a cell: the first living thing whose body is in it, or null. */
 public static net.minecraft.world.entity.LivingEntity occupant(ServerLevel l,BlockPos cell){
  var in=l.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(cell),x->x.isAlive()&&!x.isSpectator());return in.isEmpty()?null:in.get(0);}
 @Override public void stop(){if(builder.level() instanceof ServerLevel l)BuilderWolf.drop(l,builder.getUUID());builder.getNavigation().stop();builder.displayWorkItem(ItemStack.EMPTY);labor=0;waited=0;}
}
