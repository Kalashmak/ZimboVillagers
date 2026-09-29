package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
/** AD-041: a real camp built by the mod at a scouted lead — tents, a campfire, a cargo chest and stranded travellers who can be escorted home. */
public final class Camps {
 public static final String QUEST_TAG="AstraCampQuest",CAMP_TAG="AstraCamp";
 public static final int GUEST_WAIT=72000;
 /** QUEST-005: the zone around the village centre a companion must really reach; the chart marks the centre (AD-105). */
 public static final int ARRIVE=Quests.number("arrive_radius");
 private Camps(){}
 private static Path path(ServerLevel l,UUID camp){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-camps/"+camp+".bin");}
 public static CompoundTag camp(ServerLevel l,UUID camp){var p=path(l,camp);return Files.exists(p)?NbtRecord.read(p):null;}
 private static void save(ServerLevel l,UUID id,CompoundTag t){NbtRecord.write(path(l,id),t);}
 private static boolean free(ServerLevel l,BlockPos pos){var state=l.getBlockState(pos);return (state.isAir()||state.canBeReplaced())&&!OwnershipEvents.disallowedPlacement(l,pos);}
 /** Builds the camp only where the world really allows it; returns the record or null. */
 public static CompoundTag build(ServerLevel l,UUID village,UUID quest,BlockPos lead,ItemStack cargo,int travellers,long now){
  var ground=Expeditions.surface(l,lead.getX(),lead.getZ(),lead.getY());if(ground==null)return null;
  var cells=new ArrayList<BlockPos>();cells.add(ground.above());
  for(int side=-1;side<=1;side+=2)for(int dz=-1;dz<=1;dz++)cells.add(new BlockPos(ground.getX()+side*2,ground.getY()+1,ground.getZ()+dz));
  cells.add(new BlockPos(ground.getX(),ground.getY()+1,ground.getZ()+3));
  for(var pos:cells)if(!free(l,pos)||!l.getBlockState(pos.below()).isFaceSturdy(l,pos.below(),net.minecraft.core.Direction.UP))return null;
  l.setBlock(ground.above(),Blocks.CAMPFIRE.defaultBlockState(),3);
  for(int side=-1;side<=1;side+=2)for(int dz=-1;dz<=1;dz++)l.setBlock(new BlockPos(ground.getX()+side*2,ground.getY()+1,ground.getZ()+dz),(dz==0?Blocks.WHITE_WOOL:Blocks.BROWN_WOOL).defaultBlockState(),3);
  var chestPos=new BlockPos(ground.getX(),ground.getY()+1,ground.getZ()+3);l.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),3);
  if(!cargo.isEmpty()&&l.getBlockEntity(chestPos) instanceof Container chest)chest.setItem(0,cargo.copy());
  var id=UUID.randomUUID();var people=new ListTag();
  for(int i=0;i<travellers;i++){
   var npc=VillageAstra.RESIDENT.get().create(l);if(npc==null)break;
   var resident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,i==0,null,null,-1);npc.setUUID(resident.id());npc.bind(null,resident);
   npc.getPersistentData().putUUID(QUEST_TAG,quest);npc.getPersistentData().putUUID(CAMP_TAG,id);npc.getPersistentData().putBoolean("AstraEducated",resident.educated());npc.setPersistenceRequired();
   npc.moveTo(ground.getX()+.5+(i-1),ground.getY()+1,ground.getZ()+1.5,0,0);if(!l.addFreshEntity(npc))break;
   var entry=new CompoundTag();entry.putUUID("id",resident.id());entry.putBoolean("educated",resident.educated());people.add(entry);}
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",id);t.putUUID("village",village);t.putUUID("quest",quest);t.putLong("pos",ground.asLong());t.putLong("chest",chestPos.asLong());
  t.put("cargo",cargo.save(new CompoundTag()));t.put("travellers",people);t.putLong("built",now);save(l,id,t);return t;
 }
 /** Companions follow the owner of their quest and nobody else. */
 public static boolean companion(ResidentEntity npc,UUID quest){return npc.settlementId()==null&&npc.getPersistentData().hasUUID(QUEST_TAG)&&npc.getPersistentData().getUUID(QUEST_TAG).equals(quest);}
 public static Path guestPath(ServerLevel l,UUID guest){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-guests/"+guest+".bin");}
 public static CompoundTag guest(ServerLevel l,UUID guest){var p=guestPath(l,guest);return Files.exists(p)?NbtRecord.read(p):null;}
 private static void saveGuest(ServerLevel l,UUID guest,CompoundTag t){NbtRecord.write(guestPath(l,guest),t);}
 /** QUEST-005: arrival is the completed escort; housing is a separate, later outcome with its own hour of waiting. */
 public static void arrived(ServerLevel l,SettlementData.Entry e,ResidentEntity npc,long now){
  if(guest(l,npc.getUUID())!=null)return;
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("guest",npc.getUUID());t.putUUID("village",e.settlement().id());t.putLong("arrived",now);t.putLong("deadline",now+GUEST_WAIT);t.putString("state","waiting");
  saveGuest(l,npc.getUUID(),t);npc.arrive();npc.workStatus("guest_waiting");
 }
 /** A guest who died while waiting for a bed is gone: the admission ends there, and the delivery it followed stays as it was. */
 static void guestDied(ServerLevel l,UUID guest,long now){
  var t=guest(l,guest);if(t==null||!t.getString("state").equals("waiting"))return;
  t.putString("state","departed");t.putBoolean("died",true);t.putLong("departed",now);saveGuest(l,guest,t);
 }
 /** A waiting guest becomes a real resident as soon as a bed is free; after the hour without one they leave alive. */
 public static String tickGuest(ServerLevel l,ResidentEntity npc,long now){
  var t=guest(l,npc.getUUID());if(t==null||!t.getString("state").equals("waiting"))return "";
  var e=SettlementData.get(l.getServer()).entry(t.getUUID("village"));if(e==null)return "";
  // AD-098: kin brought home move in with their relative while that house has a bed; a guest keeps the family name the rescue gave them.
  var kin=Pleas.kinHome(e,npc);
  var home=kin!=null?kin:e.settlement().homes().stream().filter(h->h.usable()&&e.settlement().occupancy(h.id())<h.capacity()).findFirst().orElse(null);
  if(home!=null){
   var resident=new Resident(npc.getUUID(),Resident.Life.ADULT,npc.getPersistentData().getBoolean("AstraEducated"),null,null,-1,Pleas.profile(npc));
   e.settlement().admit(resident,home.id());SettlementData.get(l.getServer()).setDirty();
   npc.bind(e.settlement().id(),resident);npc.getPersistentData().remove(QUEST_TAG);npc.workStatus("guest_housed");
   t.putString("state","housed");t.putLong("housed",now);saveGuest(l,npc.getUUID(),t);return "housed";}
  if(now>=t.getLong("deadline")){t.putString("state","departed");t.putLong("departed",now);saveGuest(l,npc.getUUID(),t);npc.discard();return "departed";}
  npc.workStatus("guest_waiting");return "waiting";
 }
 /** QUEST-005: the guests of this village still waiting for a bed — who they are, how long of their hour is left and where they wait.
  *  The mayor sees them in the office and knows to give them a house before the hour is out. */
 public static ListTag waiting(ServerLevel l,SettlementData.Entry e,long now){
  var out=new ListTag();var dir=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-guests");
  if(!Files.isDirectory(dir))return out;
  try(var files=Files.list(dir)){
   for(var file:files.filter(f->f.getFileName().toString().endsWith(".bin")).toList()){
    var t=NbtRecord.read(file);
    if(!t.getString("state").equals("waiting")||!t.hasUUID("village")||!t.getUUID("village").equals(e.settlement().id()))continue;
    var row=new CompoundTag();row.putUUID("id",t.getUUID("guest"));row.putLong("left",Math.max(0,t.getLong("deadline")-now));
    var npc=l.getEntity(t.getUUID("guest"));
    row.putString("name",npc==null?"":npc.getName().getString());row.putBoolean("here",npc!=null);
    if(npc!=null){row.putString("dir",Adventures.bearing(e.center(),npc.blockPosition()));row.putInt("away",Adventures.away(e.center(),npc.blockPosition()));}
    out.add(row);}
  }catch(java.io.IOException ex){return out;}
  return out;
 }
 /** Whether the village has a bed free for one more guest now (a home standing, usable and not full). */
 public static boolean bedFree(SettlementData.Entry e){
  return e.settlement().homes().stream().anyMatch(home->home.usable()&&e.settlement().occupancy(home.id())<home.capacity());
 }
 /** Cargo really carried by the player, counted from the camp chest contents. */
 public static ItemStack cargo(CompoundTag camp){return ItemStack.of(camp.getCompound("cargo"));}
 public static String key(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
 public static ItemStack stack(String item,int count){return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(item)),count);}
}
