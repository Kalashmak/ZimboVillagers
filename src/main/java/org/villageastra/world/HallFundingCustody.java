package org.villageastra.world;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.WorldJournal;
import org.villageastra.server.SettlementData;

/** Unstarted funding belongs to the project, across a living worker's reassignment. */
final class HallFundingCustody {
 private HallFundingCustody(){}
 static boolean unstarted(CompoundTag p){
  if(p.getBoolean("complete")||p.getBoolean("funded")||p.getInt("index")!=0)return false;
  var ops=p.getList("ops",Tag.TAG_COMPOUND);
  return !ops.isEmpty()&&ops.stream().noneMatch(x->((CompoundTag)x).getBoolean("done"));
 }
 static boolean keep(ServerLevel l,Settlement s,CompoundTag p){
  if(s.governance().playerMayor()!=null||!unstarted(p))return false;
  // A receipt ahead of the funding checkpoint is still replayed by the next builder.
  // No block may have been placed ahead of its checkpoint, however.
  return !WorldJournal.exists(l,Settlement.childId(p.getUUID("id"),"block/0"));
 }
 /** Project checkpoint first, custody acknowledgement second. A retry only acknowledges. */
 static boolean rejoin(ServerLevel l,CompoundTag custody){
  if(custody.getBoolean("dead"))return false;
  var entry=SettlementData.get(l.getServer()).entry(custody.getUUID("settlement"));
  if(entry==null||entry.settlement().governance().playerMayor()!=null)return false;
  var resident=entry.settlement().resident(custody.getUUID("owner"));if(resident==null||!resident.alive())return false;
  var jobs=custody.getList("jobs",Tag.TAG_COMPOUND);
  if(jobs.size()!=1||!jobs.getCompound(0).getString("kind").equals("hall")||!jobs.getCompound(0).getUUID("id").equals(entry.settlement().id()))return false;
  if(!HallUpgradeGoal.exists(l,entry.settlement().id()))return false;
  var project=HallUpgradeGoal.inspect(l,entry.settlement().id());var reset=jobs.getCompound(0).getCompound("reset");
  if(!HallConstructionPlan.projectId(project).equals(HallConstructionPlan.projectId(reset)))return false;
  var id=custody.getUUID("id");
  if(project.hasUUID("fundingCustodyReturn")&&project.getUUID("fundingCustodyReturn").equals(id))return true;
  if(!keep(l,entry.settlement(),project)||!project.getList("ops",Tag.TAG_COMPOUND).equals(reset.getList("ops",Tag.TAG_COMPOUND)))return false;
  var items=custody.getList("items",Tag.TAG_COMPOUND);int index=custody.getInt("index");
  if(index<0||index>=items.size()||!items.equals(project.getList("cargo",Tag.TAG_COMPOUND)))return false;
  if(WorldJournal.exists(l,Settlement.childId(project.getUUID("id"),"fund/"+project.getInt("withdrawals"))))return false;
  for(int i=0;i<items.size();i++){
   var receiptId=Settlement.childId(id,"return/"+i);
   if(i>=index){if(WorldJournal.exists(l,receiptId))return false;continue;}
   var r=WorldJournal.inspectCommitted(l,receiptId);if(r==null||!r.getString("kind").equals("inventory")||r.getLong("pos")!=HallSite.stock(entry).asLong())return false;
   var expected=ItemStack.of(items.getCompound(i));var before=ItemStack.of(r.getCompound("before"));var after=ItemStack.of(r.getCompound("after"));
   if(expected.isEmpty()||!ItemStack.isSameItemSameTags(expected,after)||(!before.isEmpty()&&!ItemStack.isSameItemSameTags(before,after))||after.getCount()-before.getCount()!=expected.getCount())return false;
  }
  var remaining=new ListTag();for(int i=index;i<items.size();i++)remaining.add(items.getCompound(i).copy());
  project.put("cargo",remaining);project.putUUID("fundingCustodyReturn",id);
  HallUpgradeGoal.store(l,entry.settlement().id(),project);return true;
 }
}
