package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Two separate JVMs, same isolated harness world; the second never re-seeds the fixture. */
final class CaravanRestartProbe {
 private static volatile boolean stopping;
 private static CompoundTag saved;
 private CaravanRestartProbe(){}
 static boolean enabled(){return Boolean.getBoolean("villageastra.caravanRestartSmoke");}
 static boolean reloading(){return enabled()&&Boolean.getBoolean("villageastra.reloadSmoke");}
 static boolean stopping(){return stopping;}
 private static Path path(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("astra-caravan-restart.bin");}
 private static int stock(MinecraftServer s,CompoundTag t){var e=SettlementData.get(s).entry(t.getUUID("source"));var c=(Container)s.overworld().getBlockEntity(LogisticsRoutes.position(e,Workshops.hall(e)));int n=0;for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).is(Items.BREAD))n+=c.getItem(i).getCount();return n;}
 static boolean checkpoint(Minecraft mc,ServerLevel l,CompoundTag t){
  if(!enabled()||reloading()||stopping)return stopping;
  if(!t.getString("state").equals(Caravans.TRANSIT)||t.getDouble("progress")<22||t.getDouble("progress")>Caravans.length(t)-20||!t.getBoolean("materialized")||!CaravanHorseProbe.captureReady()||!CaravanDogProbe.captureReady())return false;
  if(t.getInt("delivered")!=0||t.getLong("paid")!=0||!t.contains("cart"))throw new IllegalStateException("Checkpoint must be loaded and undelivered");
  saved=new CompoundTag();saved.putUUID("trip",t.getUUID("id"));saved.putUUID("driver",t.getUUID("caravaneer"));saved.putInt("secured",t.getInt("secured"));saved.putInt("stock",stock(l.getServer(),t));saved.putDouble("progress",t.getDouble("progress"));saved.putBoolean("horse",CaravanHorseProbe.enabled());saved.putLong("process",ProcessHandle.current().pid());
  var child=CaravanDogs.child(l.getServer(),t);if(CaravanDogProbe.enabled()){if(child==null||child.getInt("secured")==0||child.getInt("delivered")!=0)throw new IllegalStateException("Dog not loaded");saved.putUUID("dog",child.getUUID("caravaneer"));saved.putInt("dogSecured",child.getInt("secured"));}
  saved.put("animal",animal(l,t.getUUID("caravaneer")));if(child!=null)saved.put("dogAnimal",animal(l,child.getUUID("caravaneer")));
  stopping=true;NbtRecord.write(path(l.getServer()),saved);l.getServer().saveEverything(false,true,true);
  LogUtils.getLogger().info("ASTRA_CARAVAN_RESTART VERIFIED stage=saved transit=true cargo=true paid=0 process={} progress={}",ProcessHandle.current().pid(),t.getDouble("progress"));mc.execute(mc::stop);return true;
 }
 static CompoundTag restore(MinecraftServer s){
  saved=NbtRecord.read(path(s));if(saved.getLong("process")==ProcessHandle.current().pid())throw new IllegalStateException("Expected a new Minecraft process");
  if(saved.getBoolean("horse")!=CaravanHorseProbe.enabled())throw new IllegalStateException("Wrong caravan fixture");
  var t=Caravans.contract(s,saved.getUUID("trip"));if(t==null||!saved.getUUID("driver").equals(t.getUUID("caravaneer"))||t.getInt("secured")!=saved.getInt("secured"))throw new IllegalStateException("Lost saved caravan");
  if(!t.getString("state").equals(Caravans.TRANSIT)||t.getInt("delivered")!=0||t.getLong("paid")!=0)throw new IllegalStateException("Trip already delivered before restart observation");
  if(CaravanHorseProbe.enabled())CaravanHorseProbe.restore(t);
  if(CaravanDogProbe.enabled()){var child=CaravanDogs.child(s,t);if(child==null||!saved.getUUID("dog").equals(child.getUUID("caravaneer"))||saved.getInt("dogSecured")!=child.getInt("secured"))throw new IllegalStateException("Lost dog cargo");CaravanDogProbe.restore(child);}
  LogUtils.getLogger().info("ASTRA_CARAVAN_RESTART restored process={}->{} trip={} progress={}",saved.getLong("process"),ProcessHandle.current().pid(),t.getUUID("id"),t.getDouble("progress"));return t;
 }
 private static CompoundTag animal(ServerLevel l,java.util.UUID id){var tag=new CompoundTag();var body=l.getEntity(id);if(body instanceof net.minecraft.world.entity.OwnableEntity owned){tag.putUUID("id",id);tag.putUUID("owner",owned.getOwnerUUID());tag.putFloat("health",((net.minecraft.world.entity.LivingEntity)body).getHealth());}return tag;}
 private static void identity(ServerLevel l,CompoundTag tag){if(tag.isEmpty())return;var actual=animal(l,tag.getUUID("id"));if(!tag.equals(actual))throw new IllegalStateException("Animal identity, owner or health changed: "+tag+" -> "+actual);}
 static void verify(MinecraftServer s,CompoundTag t){
  if(!reloading())return;if(saved==null||stock(s,t)!=saved.getInt("stock")||t.getInt("delivered")!=saved.getInt("secured"))throw new IllegalStateException("Duplicate source debit or incomplete delivery");
  identity(s.overworld(),saved.getCompound("animal"));identity(s.overworld(),saved.getCompound("dogAnimal"));
  int carts=0;for(var body:s.overworld().getAllEntities())if(body instanceof CartEntity cart&&t.getUUID("source").equals(cart.settlement()))carts++;if(carts!=(saved.hasUUID("dog")?2:1))throw new IllegalStateException("Duplicated or lost carts: "+carts);
  LogUtils.getLogger().info("ASTRA_CARAVAN_RESTART VERIFIED stage=returned newProcess=true identity=true sourceUnchanged=true reload=true");
 }
}
