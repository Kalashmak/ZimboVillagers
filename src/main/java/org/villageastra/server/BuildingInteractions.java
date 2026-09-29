package org.villageastra.server;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.BuildingBlueprints;
/** Physical entrances to management. Ordinary blocks outside registered buildings retain vanilla use. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class BuildingInteractions {
 private BuildingInteractions(){}
 public static void ensureHallDesk(ServerLevel level){
  for(var e:SettlementData.get(level.getServer()).entries()){
   // AD-121: the castle hall has its crafting table in its kit.
   if(!e.dimension().equals(level.dimension().location().toString())||org.villageastra.world.HallSite.castle(e.settlement()))continue;
   var pos=e.center().offset(4,1,4);
   if(level.hasChunkAt(pos)&&level.getBlockState(pos).isAir()&&level.getBlockEntity(org.villageastra.world.HallSite.stock(e)) instanceof org.villageastra.world.OwnedChestEntity&&!org.villageastra.world.HallUpgradeGoal.pending(level,e.settlement().id()))level.setBlock(pos,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
  }
 }
 public static int station(ServerLevel level,BlockPos pos){
  for(var e:SettlementData.get(level.getServer()).entries()){
   if(!e.dimension().equals(level.dimension().location().toString()))continue;
   for(var b:e.settlement().buildings()){
    var d=BuildingBlueprints.design(b.type());var p=org.villageastra.world.BuildingPlacement.local(e,b,pos);
    if(p.getX()<0||p.getX()>=d.width()||p.getZ()<0||p.getZ()>=d.depth()||p.getY()<1||p.getY()>16)continue;
    var state=level.getBlockState(pos);
    if(b.type().equals("town_hall")&&state.is(Blocks.LECTERN))return 0;
    if(b.type().equals("town_hall")&&state.is(Blocks.CRAFTING_TABLE))return 4;
    if(b.type().equals("farm")&&state.is(Blocks.COMPOSTER))return 2;
    if(b.type().equals("laboratory")&&state.is(Blocks.LECTERN))return 3;
    if(b.type().equals("cartographer")&&state.is(Blocks.CARTOGRAPHY_TABLE))return 7;
   }
  }return -1;
 }
 @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event){
  if(event.getHand()!=InteractionHand.MAIN_HAND||!(event.getEntity() instanceof ServerPlayer p))return;
  var sign=org.villageastra.world.BuildingSigns.target(p.serverLevel(),event.getPos());
  if(sign!=null){event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);if(!p.isSpectator()&&p.distanceToSqr(event.getPos().getCenter())<=64)ConstructionNetwork.openBuilding(p,sign);return;}
  int section=station(p.serverLevel(),event.getPos());if(section<0)return;
  event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
  if(p.isSpectator()||p.distanceToSqr(event.getPos().getX()+.5,event.getPos().getY()+.5,event.getPos().getZ()+.5)>64)return;
  if(section==4||section==0&&p.isShiftKeyDown()){
   var e=SettlementData.get(p.server).entries().stream().filter(v->v.dimension().equals(p.serverLevel().dimension().location().toString())&&v.center().distSqr(event.getPos())<20*20).min(java.util.Comparator.comparingDouble(v->v.center().distSqr(event.getPos()))).orElse(null);
   if(e==null)return;var g=e.settlement().governance();var view=ConstructionDrafts.view(p,new net.minecraft.nbt.CompoundTag());
   int action=section==4?(p.isShiftKeyDown()?2:0):1;
   boolean accepted=ConstructionDrafts.order(p,e.settlement().id(),g.epoch(),g.revision(),action,view.hasUUID("id")?view.getUUID("id"):new java.util.UUID(0,0));
   p.displayClientMessage(net.minecraft.network.chat.Component.translatable(accepted?"interaction.villageastra.action_"+action:"interaction.villageastra.denied"),false);
   ConstructionNetwork.send(p);return;
  }
  ConstructionNetwork.open(p,section);
 }
}
