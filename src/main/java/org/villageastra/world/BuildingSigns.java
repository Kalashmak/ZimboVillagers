package org.villageastra.world;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** Entrance labels belong to registered buildings, never to arbitrary player signs. */
public final class BuildingSigns {
 private BuildingSigns(){}
 public record Target(SettlementData.Entry entry,Settlement.Building building){}
 private static final Map<String,Optional<BlockPos>> SLOTS=new HashMap<>();
 /** Find a supported plaque near the street face; never replace a door, fitting or path. */
 public static BlockPos slot(String id,BlockPos origin,Map<BlockPos,BlockState> plan){
  int door=BuildingBlueprints.doorX(id);var design=BuildingBlueprints.design(id);
  return plan.entrySet().stream().filter(c->{var p=c.getKey().subtract(origin);return p.getY()>=1&&p.getY()<=2
   &&p.getX()>=0&&p.getX()<design.width()&&p.getZ()>=1&&p.getZ()<=design.depth()
   &&c.getValue().isSolidRender(EmptyBlockGetter.INSTANCE,c.getKey())
   &&plan.getOrDefault(c.getKey().north(),Blocks.AIR.defaultBlockState()).isAir();})
   .min(Comparator.<Map.Entry<BlockPos,BlockState>>comparingInt(c->c.getKey().getZ())
    .thenComparingInt(c->Math.abs(c.getKey().getX()-origin.getX()-door))
    .thenComparingInt(c->-c.getKey().getY())).map(c->c.getKey().north()).orElse(null);
 }
 public static Map<BlockPos,BlockState> label(String id,BlockPos origin,Map<BlockPos,BlockState> plan){
  var p=slot(id,origin,plan);if(p!=null)plan.put(p,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.NORTH));return plan;
 }
 public static BlockPos position(SettlementData.Entry e,Settlement.Building b){
  String id=BuildingTiers.layoutId(e.settlement(),b.type(),BuildingTiers.built(e,b));
  var local=SLOTS.computeIfAbsent(id,key->BuildingBlueprints.layout(key,BlockPos.ZERO).entrySet().stream()
   .filter(c->c.getValue().is(Blocks.OAK_WALL_SIGN)).map(Map.Entry::getKey).findFirst()).orElse(null);
  if(local==null)return null;
  // Castle dimensions differ from the old town hall; use the actual layout's footprint.
  var d=BuildingBlueprints.design(id);return BuildingPlacement.origin(e,b).offset(BuildingPlacement.turn(local.getX(),local.getY(),local.getZ(),d.width(),d.depth(),b.rotation()));
 }
 public static Target target(ServerLevel level,BlockPos pos){
  if(!level.hasChunkAt(pos)||!(level.getBlockState(pos).getBlock() instanceof WallSignBlock))return null;
  for(var e:SettlementData.get(level.getServer()).entries())if(e.dimension().equals(level.dimension().location().toString()))
   for(var b:e.settlement().buildings())if(pos.equals(position(e,b))&&level.getBlockState(pos).is(plaque(b).getBlock()))return new Target(e,b);
  return null;
 }
 public static boolean owned(ServerLevel level,SettlementData.Entry e,Settlement.Building b,BlockPos pos){
  return b!=null&&level.getBlockState(pos).is(plaque(b).getBlock())&&pos.equals(position(e,b));
 }
 private static BlockState plaque(Settlement.Building b){return BuildingWood.replace(BuildingPlacement.state(Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,Direction.NORTH),b.rotation()),b.wood());}
 /** Text is translatable on each client. Existing signs are labelled; occupied legacy slots are never replaced. */
 public static void refresh(ServerLevel level,SettlementData.Entry e){
  for(var b:e.settlement().buildings()){
   var p=position(e,b);if(p==null||!level.hasChunkAt(p))continue;
   var state=plaque(b);
   // A one-time civic label for old saves; occupied cells and active construction are left alone.
   if(level.getBlockState(p).isAir()&&state.canSurvive(level,p)&&!HallUpgradeGoal.pending(level,e.settlement().id()))level.setBlock(p,state,3);
   if(!level.getBlockState(p).is(state.getBlock())||!(level.getBlockEntity(p) instanceof SignBlockEntity sign))continue;
   boolean twoLines=b.type().equals("caravan")||b.type().equals("dog_academy_annex");
   var name=Component.translatable("building.villageastra."+b.type()+(twoLines?".sign.1":""));
   var text=sign.getFrontText().setMessage(0,name).setMessage(1,twoLines?Component.translatable("building.villageastra."+b.type()+".sign.2"):Component.empty()).setMessage(3,Component.translatable("building.villageastra.sign.open"));
   if(!sign.getFrontText().getMessage(0,false).equals(name)||!sign.getFrontText().getMessage(1,false).equals(text.getMessage(1,false))||!sign.getFrontText().getMessage(3,false).equals(text.getMessage(3,false))){sign.setText(text,true);sign.setText(text,false);}
   sign.setWaxed(true);
  }
 }
}
