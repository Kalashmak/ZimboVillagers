package org.villageastra.world;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.*;
/** Reconcile a missing paid temporary block; an unreceipted loss returns no item. */
public final class ScaffoldLoss {
 private ScaffoldLoss(){}
 private static boolean matches(CompoundTag receipt,UUID id,ServerLevel l,BlockPos p,BlockState before,BlockState after){
  return receipt!=null&&receipt.getInt("schema")==1&&receipt.hasUUID("id")&&receipt.getUUID("id").equals(id)
   &&receipt.getString("dimension").equals(l.dimension().location().toString())&&receipt.getString("kind").equals("block")
   &&receipt.getLong("pos")==p.asLong()&&!receipt.contains("loot")
   &&receipt.getCompound("before").equals(NbtUtils.writeBlockState(before))&&receipt.getCompound("after").equals(NbtUtils.writeBlockState(after));
 }
 /** Called only for a selected removal whose expected temporary block is already absent. */
 public static boolean finish(ServerLevel l,CompoundTag state,int index,UUID worker){
  if(!state.hasUUID("id")||state.getBoolean("complete")||!state.getBoolean("funded")||state.getBoolean("relocate"))return false;
  var ops=state.getList("ops",Tag.TAG_COMPOUND);if(index<0||index>=ops.size())return false;
  var op=ops.getCompound(index);if(op.getBoolean("done")||op.getBoolean("dismantle")||op.contains("item")||!op.getString("return").equals("villageastra:timber_scaffold"))return false;
  var step=HallConstructionPlan.step(op);var scaffold=VillageAstra.TIMBER_SCAFFOLD.get().defaultBlockState();var air=Blocks.AIR.defaultBlockState();
  if(!step.before().equals(scaffold)||!step.after().equals(air)||!l.hasChunkAt(step.pos())||!l.getBlockState(step.pos()).isAir()||l.getBlockEntity(step.pos())!=null)return false;
  int placed=-1;for(int i=0;i<index;i++)if(ops.getCompound(i).getLong("pos")==op.getLong("pos"))placed=i;
  if(placed<0)return false;var paid=ops.getCompound(placed);var installation=HallConstructionPlan.step(paid);
  if(!paid.getBoolean("done")||!paid.getString("item").equals("villageastra:timber_scaffold")||!installation.before().equals(air)||!installation.after().equals(scaffold))return false;
  var id=state.getUUID("id");var paidId=Settlement.childId(id,"block/"+placed);
  var receipt=WorldJournal.inspectCommitted(l,paidId);
  if(!matches(receipt,paidId,l,step.pos(),air,scaffold)||!ChunkReceipts.of(l.getChunkAt(step.pos())).contains(paidId))return false;
  var removalId=Settlement.childId(id,"block/"+index);
  var file=l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-journal/"+removalId+".bin");
  var existing=Files.exists(file)?NbtRecord.read(file):null;
  boolean returned=matches(existing,removalId,l,step.pos(),scaffold,air);
  if(existing!=null&&!returned)return false;
  var lossFile=l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-scaffold-loss/"+removalId+".bin");
  if(returned){
   if(Files.exists(lossFile)||!WorldJournal.place(l,removalId,step.pos(),scaffold,air))return false;
   state.getList("cargo",Tag.TAG_COMPOUND).add(new ItemStack(VillageAstra.TIMBER_SCAFFOLD.get().asItem()).save(new CompoundTag()));
  }else{
   // There is no world mutation to replay. Record only the verified loss;
   // never manufacture a removal receipt or return a block that was absent.
   var loss=new CompoundTag();loss.putInt("schema",1);loss.putUUID("id",removalId);loss.putUUID("placement",paidId);loss.putLong("pos",step.pos().asLong());loss.putString("dimension",l.dimension().location().toString());loss.putString("item","villageastra:timber_scaffold");
   if(Files.exists(lossFile)){if(!NbtRecord.read(lossFile).equals(loss))throw new IllegalStateException("Scaffold loss identity changed");}
   else NbtRecord.write(lossFile,loss);
   op.putBoolean("lostScaffold",true);state.putInt("lostScaffolds",state.getInt("lostScaffolds")+1);
  }
  op.putBoolean("done",true);op.putUUID("by",worker);state.putInt("progress",state.getInt("progress")+1);
  return true;
 }
}
