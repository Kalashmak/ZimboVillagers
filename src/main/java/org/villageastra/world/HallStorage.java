package org.villageastra.world;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.villageastra.VillageAstra;
import org.villageastra.server.SettlementData;
/** Two double chests, one persistent 108-slot stock at the original journal address. AD-147: the same pages serve the warehouse's store
 *  (WarehouseStorage): a part reads its master while the master holds its offset's slots, a page opens while the master holds it. */
public final class HallStorage {
 private HallStorage(){}
 public static void ensure(ServerLevel l,SettlementData.Entry e){
  var hall=e.settlement().buildings().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null);if(hall==null||HallUpgradeGoal.pending(l,e.settlement().id()))return;
  boolean castle=HallSite.castle(e.settlement());var stock=HallSite.stock(e);
  var positions=castle?java.util.List.of(stock,stock.south(),stock.south(3),stock.south(4)):java.util.List.of(BuildingPlacement.at(e,hall,1,1,4),BuildingPlacement.at(e,hall,1,1,3),BuildingPlacement.at(e,hall,5,1,2),BuildingPlacement.at(e,hall,5,1,3));
  var anchor=positions.get(0);if(!l.hasChunkAt(anchor)||!(l.getBlockEntity(anchor) instanceof OwnedChestEntity master))return;
  for(var pos:positions){if(!l.hasChunkAt(pos))return;var state=l.getBlockState(pos);if(castle&&!(l.getBlockEntity(pos) instanceof OwnedChestEntity))return;if(!state.isAir()&&!state.is(VillageAstra.OWNED_CHEST.get()))return;if(!pos.equals(anchor)&&l.getBlockEntity(pos) instanceof OwnedChestEntity other&&!other.isEmpty())return;}
  master.expand(108);
  for(int i=0;i<positions.size();i++){var pos=positions.get(i);var state=VillageAstra.OWNED_CHEST.get().defaultBlockState().setValue(ChestBlock.FACING,i<2?Direction.EAST:Direction.WEST).setValue(ChestBlock.TYPE,i%2==0?ChestType.RIGHT:ChestType.LEFT);state=BuildingPlacement.state(state,hall.rotation());
   if(!castle&&!l.getBlockState(pos).equals(state))l.setBlock(pos,state,2);
   if(l.getBlockEntity(pos) instanceof OwnedChestEntity chest){var data=chest.getPersistentData();if(!data.contains("AstraHallMaster")){data.putLong("AstraHallMaster",anchor.asLong());data.putInt("AstraHallPage",i/2);data.putInt("AstraHallOffset",i*27);data.putUUID("AstraSettlement",e.settlement().id());chest.setChanged();}}
  }
 }
 static OwnedChestEntity master(OwnedChestEntity part){var l=part.getLevel();var data=part.getPersistentData();if(l==null||!data.contains("AstraHallMaster"))return null;var pos=BlockPos.of(data.getLong("AstraHallMaster"));if(pos.equals(part.getBlockPos())||!l.hasChunkAt(pos))return null;return l.getBlockEntity(pos) instanceof OwnedChestEntity master&&master.getContainerSize()>=data.getInt("AstraHallOffset")+27?master:null;}
 public static boolean partAt(ServerLevel l,BlockPos pos){return l.getBlockEntity(pos) instanceof OwnedChestEntity chest&&chest.getPersistentData().contains("AstraHallMaster");}
 public static MenuProvider menu(net.minecraft.world.level.Level l,BlockPos pos){
  if(!(l.getBlockEntity(pos) instanceof OwnedChestEntity part)||!part.getPersistentData().contains("AstraHallMaster"))return null;
  var base=BlockPos.of(part.getPersistentData().getLong("AstraHallMaster"));if(!l.hasChunkAt(base)||!(l.getBlockEntity(base) instanceof OwnedChestEntity master))return null;
  // CF-B: a page opens while the master holds all of it (the hall's two, a warehouse's up to twelve).
  int page=part.getPersistentData().getInt("AstraHallPage");if(page<0||(page+1)*54>master.getContainerSize())return null;
  return new SimpleMenuProvider((id,inv,p)->new OwnedChestMenu(id,inv,master,page),net.minecraft.network.chat.Component.translatable("container.chestDouble"));
 }
 static Container page(OwnedChestEntity chest,int page){return new Container(){
  private int slot(int i){if(i<0||i>=54)throw new IndexOutOfBoundsException(i);return page*54+i;}
  public int getContainerSize(){return 54;}public boolean isEmpty(){for(int i=0;i<54;i++)if(!getItem(i).isEmpty())return false;return true;}
  public ItemStack getItem(int i){return chest.getItem(slot(i));}public ItemStack removeItem(int i,int n){return chest.removeItem(slot(i),n);}public ItemStack removeItemNoUpdate(int i){return chest.removeItemNoUpdate(slot(i));}public void setItem(int i,ItemStack s){chest.setItem(slot(i),s);}public void setChanged(){chest.setChanged();}public boolean stillValid(Player p){return chest.stillValid(p);}public void clearContent(){for(int i=0;i<54;i++)setItem(i,ItemStack.EMPTY);}public void startOpen(Player p){chest.startOpen(p);}public void stopOpen(Player p){chest.stopOpen(p);}
 };}
}
