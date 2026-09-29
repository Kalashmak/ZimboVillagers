package org.villageastra.server;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.world.*;
/** Bounded, viewer-specific, read-only snapshots; no inventory contents or other players' quest identities. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class ResidentMarkers {
 public static final int RANGE=32,MAX_RESIDENTS=128,MAX_ITEMS=24;
 private record Supply(List<Workshops.Want> wants,ListTag quests){}
 private ResidentMarkers(){}
 @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){if(event.phase!=TickEvent.Phase.END||event.getServer().getTickCount()%20!=0)return;var cache=new HashMap<UUID,Supply>();for(var p:event.getServer().getPlayerList().getPlayers())ResidentMarkerNetwork.send(p,view(p,cache));}
 public static CompoundTag view(ServerPlayer p){return view(p,new HashMap<>());}
 private static CompoundTag view(ServerPlayer p,Map<UUID,Supply> cache){
  var out=new CompoundTag();out.putString("dimension",p.level().dimension().location().toString());var rows=new ListTag();
  var nearby=p.serverLevel().getEntitiesOfClass(ResidentEntity.class,p.getBoundingBox().inflate(RANGE),r->r.isAlive()&&!r.isInvisibleTo(p)&&r.distanceToSqr(p)<=RANGE*RANGE);
  nearby.sort(Comparator.comparingDouble(r->r.distanceToSqr(p)));
  for(var npc:nearby.stream().limit(MAX_RESIDENTS).toList()){
   var row=new CompoundTag();row.putUUID("id",npc.getUUID());row.putString("profession",npc.child()?"child":"unemployed");var items=new LinkedHashSet<Item>();boolean quest=false;
   var e=npc.settlementId()==null?null:SettlementData.get(p.server).entry(npc.settlementId());
   if(e!=null&&e.dimension().equals(p.level().dimension().location().toString())){var r=e.settlement().resident(npc.getUUID());
    if(r!=null&&r.alive()){row.putString("profession",r.life()==Resident.Life.CHILD?"child":r.profession()==null?"unemployed":r.profession().id());
     var supply=cache.computeIfAbsent(e.settlement().id(),id->new Supply(Workshops.wants(p.serverLevel(),e),Quests.board(p.serverLevel(),id).getList("quests",Tag.TAG_COMPOUND)));
     var workplace=e.settlement().workplace(r.id());var hall=Workshops.hall(e);
     for(var want:supply.wants()){
      boolean own=r.life()==Resident.Life.ADULT&&r.profession()!=null&&workplace!=null&&workplace.id().equals(want.destination());
      boolean builder=r.profession()==Profession.BUILDER&&hall!=null&&hall.id().equals(want.destination())&&HallUpgradeGoal.pending(p.serverLevel(),e.settlement().id());
      if(want.count()>0&&(own||builder))for(var stack:want.ingredient().getItems()){if(!stack.isEmpty()){items.add(stack.getItem());break;}}
     }
     if(workplace!=null)for(var want:WorkerSupplies.wants(p.serverLevel(),e,workplace.id()))for(var stack:want.ingredient().getItems()){if(!stack.isEmpty()){items.add(stack.getItem());break;}}
     long now=SettlementData.get(p.server).clock().ticks();
     for(var raw:supply.quests()){var q=(CompoundTag)raw;boolean giver=q.hasUUID("giver")?q.getUUID("giver").equals(r.id()):r.profession()==Profession.MAYOR;
      if(giver&&q.getLong("deadline")>now&&Quests.takeRefusal(p,e.settlement().id(),q).isEmpty()){quest=true;break;}}
    }
   }
   row.putBoolean("quest",quest);var needs=new ListTag();items.stream().limit(MAX_ITEMS).forEach(item->needs.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString())));row.put("items",needs);rows.add(row);
  }out.put("residents",rows);return out;
 }
}
