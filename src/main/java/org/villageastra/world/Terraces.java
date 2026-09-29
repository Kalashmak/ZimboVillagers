package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.domain.*;
import org.villageastra.server.*;
/** AD-045: the engineer designs the earthworks — cutting the rise and filling the hollow of a site — and ordinary builders carry them out with real blocks. */
public final class Terraces {
 public static final int CUT_HEIGHT=6,FILL_DEPTH=4;
 private Terraces(){}
 /** An educated engineer of this settlement; without one the earthworks are not designed at all. */
 public static Resident engineer(SettlementData.Entry e){
  for(var r:e.settlement().residents()){var b=e.settlement().workplace(r.id());
   if(r.alive()&&r.educated()&&r.profession()==Profession.ENGINEER&&b!=null&&b.type().equals("engineering"))return r;}
  return null;
 }
 /** AD-112: how high above a site the engineer's design cuts and how deep it fills, at this working level of the engineering office (CUT_HEIGHT/FILL_DEPTH at level I). */
 public static int cut(int level){return CoreEffects.value("engineering","cut",level);}
 public static int fill(int level){return CoreEffects.value("engineering","fill",level);}
 /** The working level of the office of this settlement's engineer (1 without one). */
 public static int level(ServerLevel l,SettlementData.Entry e){var r=engineer(e);var b=r==null?null:e.settlement().workplace(r.id());return b==null?1:BuildingLevels.level(l,e,b);}
 private static CompoundTag op(String kind,BlockPos pos,net.minecraft.world.level.block.state.BlockState before,net.minecraft.world.level.block.state.BlockState after,String item){
  var t=new CompoundTag();t.putString("kind",kind);t.putLong("pos",pos.asLong());t.put("before",NbtUtils.writeBlockState(before));t.put("after",NbtUtils.writeBlockState(after));t.putString("item",item);return t;}
 /** Levels the footprint of a design at the given origin: natural rise is removed, hollows are filled with real cobblestone. */
 public static CompoundTag plan(ServerLevel l,SettlementData.Entry e,String design,BlockPos origin){return plan(l,e,design,origin,0);}
 /** AD-068: the terrace under a turned design covers its turned footprint. */
 public static CompoundTag plan(ServerLevel l,SettlementData.Entry e,String design,BlockPos origin,int turns){
  if(engineer(e)==null)return null;if(BuildingBlueprints.design(design)==null)return null;var size=BuildingPlacement.size(design,turns);
  var ops=new ListTag();var cost=new CompoundTag();var air=Blocks.AIR.defaultBlockState();int cut=0,fill=0;int level=level(l,e),cutHeight=cut(level),fillDepth=fill(level);
  for(int x=0;x<size[0];x++)for(int z=0;z<size[1];z++){
   for(int y=0;y<cutHeight;y++){var pos=origin.offset(x,y,z);if(!l.hasChunkAt(pos))return null;
    var before=l.getBlockState(pos);
    if(before.isAir()||!before.getFluidState().isEmpty()||l.getBlockEntity(pos)!=null||OwnershipEvents.disallowedPlacement(l,pos))continue;
    if(before.is(net.minecraft.tags.BlockTags.LEAVES)||before.is(net.minecraft.tags.BlockTags.LOGS)||before.is(Blocks.GRASS)||before.is(Blocks.TALL_GRASS)||before.getBlock().defaultDestroyTime()<0)continue;
    ops.add(op("cut",pos,before,air,""));cut++;}
   boolean supported=false;
   for(int depth=1;depth<=fillDepth&&!supported;depth++){var pos=origin.offset(x,-depth,z);if(!l.hasChunkAt(pos))return null;
    var before=l.getBlockState(pos);
    if(before.isFaceSturdy(l,pos,net.minecraft.core.Direction.UP)&&before.getFluidState().isEmpty()){supported=true;break;}
    if(l.getBlockEntity(pos)!=null||OwnershipEvents.disallowedPlacement(l,pos))break;
    ops.add(op("fill",pos,before,Blocks.COBBLESTONE.defaultBlockState(),"minecraft:cobblestone"));fill++;
    cost.putInt("minecraft:cobblestone",cost.getInt("minecraft:cobblestone")+1);}
  }
  if(ops.isEmpty())return null;
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",Settlement.childId(e.settlement().id(),"terrace/"+origin.asLong()));t.putString("kind","terrace");t.putString("design",design);
  t.putLong("origin",origin.asLong());t.put("ops",ops);t.put("cost",cost);t.putInt("index",0);t.put("cargo",new ListTag());t.put("returns",new ListTag());t.putInt("withdrawals",0);t.putInt("deposits",0);
  t.putBoolean("complete",false);t.put("cells",new ListTag());t.putInt("cut",cut);t.putInt("fill",fill);return t;
 }
 public static String key(net.minecraft.world.item.Item item){return BuiltInRegistries.ITEM.getKey(item).toString();}
 public static boolean order(ServerLevel l,SettlementData.Entry e,CompoundTag project){return Roads.order(l,e,project);}
 /** Cobblestone the settlement still has to provide for the ordered earthworks. */
 public static int missing(ServerLevel l,SettlementData.Entry e,CompoundTag project){
  var hall=Workshops.hall(e);var chest=hall==null?null:LogisticsRoutes.chest(l,e,hall);if(chest==null)return Integer.MAX_VALUE;
  int need=Roads.needs(project).getOrDefault(Items.COBBLESTONE,0);
  return Math.max(0,need-HallReserve.count(l,e,hall,chest,s->s.is(Items.COBBLESTONE)));
 }
}
