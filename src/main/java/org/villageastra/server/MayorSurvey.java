package org.villageastra.server;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.BuildingBlueprints;
import org.villageastra.world.BuildingOrders;
/** Ephemeral server-owned survey. Only a live mayor may mark terrain; previews never alter blocks. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class MayorSurvey {
 public record Selection(UUID token,UUID village,long epoch,String dimension,BlockPos first,BlockPos second,boolean road,String design,int variant){}
 private static final Map<UUID,Selection> SELECTIONS=new HashMap<>();
 /** OWNER_REQUEST 9.2: once a second the open field palettes are checked against the office — a selection whose holder is no longer the mayor
  *  (an election, a resignation, a transfer) is dropped and a fresh snapshot is sent, so the order buttons of an open window go dark at once. */
 public static void revalidate(net.minecraft.server.MinecraftServer server){
  for(var entry:List.copyOf(SELECTIONS.entrySet())){
   var p=server.getPlayerList().getPlayer(entry.getKey());var s=entry.getValue();var e=SettlementData.get(server).entry(s.village);
   if(p!=null&&e!=null&&e.settlement().governance().canManage(p.getUUID(),s.epoch)&&e.settlement().governance().epoch()==s.epoch)continue;
   SELECTIONS.remove(entry.getKey());if(p!=null)ConstructionNetwork.send(p);
  }
 }
 /** Whether this player holds a field selection now. */
 public static boolean selecting(UUID player){return SELECTIONS.containsKey(player);}
 private MayorSurvey(){}
 public static boolean holding(net.minecraft.world.entity.player.Player p){return p.getMainHandItem().is(VillageAstra.MAYOR_SHOVEL.get());}
 public static void clear(){SELECTIONS.clear();}
 private static SettlementData.Entry office(ServerPlayer p){return SettlementData.get(p.server).entries().stream().filter(e->e.dimension().equals(p.serverLevel().dimension().location().toString())&&e.center().distSqr(p.blockPosition())<=256*256&&e.settlement().governance().canManage(p.getUUID(),e.settlement().governance().epoch())).min(Comparator.comparingDouble(e->e.center().distSqr(p.blockPosition()))).orElse(null);}
 private static boolean safe(ServerPlayer p){return holding(p)&&p.isAlive()&&!p.isSpectator()&&!(p.getLastHurtByMobTimestamp()>0&&p.tickCount-p.getLastHurtByMobTimestamp()<100);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void left(PlayerInteractEvent.LeftClickBlock e){
  if(!holding(e.getEntity()))return;
  // Let the client's attack packet reach the server; deny vanilla damage on both sides.
  e.setUseBlock(Event.Result.DENY);e.setUseItem(Event.Result.DENY);
  if(e.getEntity() instanceof ServerPlayer p){e.setCanceled(true);if(e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START)mark(p,e.getPos(),false);}
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void right(PlayerInteractEvent.RightClickBlock e){
  if(!holding(e.getEntity())||e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
  e.setCanceled(true);e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
  if(e.getEntity() instanceof ServerPlayer p)mark(p,e.getPos(),true);
 }
 public static boolean mark(ServerPlayer p,BlockPos pos,boolean road){
  var office=office(p);if(!safe(p)||office==null||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>64){message(p,"denied");return false;}
  if(p.isShiftKeyDown()){SELECTIONS.remove(p.getUUID());ConstructionNetwork.send(p);message(p,"cancelled");return true;}
  var old=SELECTIONS.get(p.getUUID());BlockPos first=pos.immutable(),second=null;
  if(road&&old!=null&&old.road&&old.second==null&&old.village.equals(office.settlement().id())&&old.dimension.equals(office.dimension())){first=old.first;second=pos.immutable();if(first.distSqr(second)>96*96){message(p,"length");return false;}}
  var selection=new Selection(UUID.randomUUID(),office.settlement().id(),office.settlement().governance().epoch(),office.dimension(),first,second,road,"home",0);
  SELECTIONS.put(p.getUUID(),selection);ConstructionNetwork.send(p);
  if(!road||second!=null)ConstructionNetwork.open(p,road?6:5);else message(p,"first");return true;
 }
 public static boolean choose(ServerPlayer p,UUID token,String design,int variant,int action){
  var s=SELECTIONS.get(p.getUUID());var e=office(p);
  if(s==null||!s.token.equals(token)||!safe(p)||e==null||!e.settlement().id().equals(s.village)||e.settlement().governance().epoch()!=s.epoch)return false;
  if(action==2){SELECTIONS.remove(p.getUUID());return true;}
  if(action<0||action>3||(action==3&&s.road)||variant<0||(!s.road&&variant>3)||(s.road&&(variant>=64||(variant&3)>2))||BuildingBlueprints.designs().stream().noneMatch(d->d.id().equals(design)))return false;
  // AD-046: a design the builder does not finish is never put on the queue, so one unfinished site cannot block the settlement for good.
  if(action==3&&BuildingOrders.WITHHELD.contains(design))return false;
  s=new Selection(s.token,s.village,s.epoch,s.dimension,s.first,s.second,s.road,design,variant);SELECTIONS.put(p.getUUID(),s);
  if(action==3){
   // The order is re-surveyed on the server; only design, rotation and the marked site come from the client.
   if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.first))>BuildingOrders.SITE_DISTANCE*BuildingOrders.SITE_DISTANCE){message(p,"order_far");return false;}
   String reason=BuildingOrders.approve(p.serverLevel(),e,s.design,s.variant,s.first);
   if(!reason.isEmpty()){p.displayClientMessage(Component.translatable("order.villageastra."+reason),true);return false;}
   SELECTIONS.remove(p.getUUID());message(p,"ordered");return true;
  }
  // AD-070: under a siege the mayor's shovel lays no path and orders no road either.
  if(action==1&&s.road&&org.villageastra.world.Sieges.besieged(p.server,e.settlement().id())){message(p,"besieged");return false;}
  if(action==1&&s.road){var research=org.villageastra.world.ResearchGate.roadRefusal(p.serverLevel(),e,s.variant);
   if(!research.isEmpty()){message(p,"research");return false;}
   var cells=geometry(p,s);if(cells.isEmpty()||cells.entrySet().stream().anyMatch(c->blocked(p,c.getKey(),true)))return false;
   int surface=(s.variant>>2)&3;boolean light=((s.variant>>4)&1)==1;boolean fence=((s.variant>>5)&1)==1;
   if(surface>0||light||fence){
    // AD-038: paving and lamps are a builder project paid with real hall materials, not placed by the shovel.
    var project=org.villageastra.world.Roads.plan(p.serverLevel(),e,line(s.first,s.second,s.variant&3),cells,surface,light,fence);
    if(!org.villageastra.world.Roads.order(p.serverLevel(),e,project)){message(p,"road_busy");return false;}
    SELECTIONS.remove(p.getUUID());message(p,"road_ordered");return true;
   }
   // A dirt path is made by the shovel itself: no placed materials and no free inventory blocks.
   int wear=(int)cells.keySet().stream().filter(pos->!p.serverLevel().getBlockState(pos).is(Blocks.DIRT_PATH)).count();
   var tool=p.getMainHandItem();if(!p.isCreative()&&tool.getMaxDamage()-tool.getDamageValue()<wear){message(p,"worn");return false;}
   for(var cell:cells.entrySet())p.serverLevel().setBlock(cell.getKey(),cell.getValue(),3);
   if(!p.isCreative())tool.hurtAndBreak(wear,p,who->who.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
   for(var cell:cells.keySet())org.villageastra.world.Roads.register(p.serverLevel(),e.settlement().id(),cell);
   SELECTIONS.remove(p.getUUID());message(p,"made");
  }return true;
 }
 public static Map<BlockPos,BlockState> geometry(ServerPlayer p,Selection s){
  if(s.road){if(s.second==null)return Map.of();return road(s.first,s.second,s.variant&3,pos->{if(!p.serverLevel().hasChunkAt(pos))return Integer.MIN_VALUE;return p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())-1;});}
  var entry=office(p);String design=s.design.equals(org.villageastra.world.Walls.TOWER)&&entry!=null?org.villageastra.world.TowerStages.design(p.serverLevel(),entry):s.design;
  return new LinkedHashMap<>(org.villageastra.world.BuildingPlacement.layout(design,s.first,s.variant));
 }
 /** Centre line of a road: straight, X first or Z first; empty when longer than 96. */
 public static List<BlockPos> line(BlockPos a,BlockPos b,int variant){
  var line=new LinkedHashSet<BlockPos>();int x=a.getX(),z=a.getZ(),dx=b.getX()-x,dz=b.getZ()-z;
  int count=Math.max(Math.abs(dx),Math.abs(dz));if(count>96)return List.of();
  if(variant==0){for(int i=0;i<=count;i++)line.add(new BlockPos(x+(int)Math.round(dx*(double)i/Math.max(1,count)),0,z+(int)Math.round(dz*(double)i/Math.max(1,count))));}
  else{while(x!=b.getX()||z!=b.getZ()){line.add(new BlockPos(x,0,z));if((variant==1&&x!=b.getX())||z==b.getZ())x+=Integer.signum(b.getX()-x);else z+=Integer.signum(b.getZ()-z);}line.add(new BlockPos(x,0,z));}
  return List.copyOf(line);
 }
 public static Map<BlockPos,BlockState> road(BlockPos a,BlockPos b,int variant,java.util.function.ToIntFunction<BlockPos> height){return road(a,b,variant,3,height);}
 /** AD-057: a road of the given width, one to five blocks, around its centre line. */
 public static Map<BlockPos,BlockState> road(BlockPos a,BlockPos b,int variant,int width,java.util.function.ToIntFunction<BlockPos> height){
  // AD-061: route 3 paves the whole rectangle between the two corners, a square or a yard rather than a line.
  if(variant==AREA){
   int x0=Math.min(a.getX(),b.getX()),x1=Math.max(a.getX(),b.getX()),z0=Math.min(a.getZ(),b.getZ()),z1=Math.max(a.getZ(),b.getZ());
   if(x1-x0+1>AREA_SPAN||z1-z0+1>AREA_SPAN)return Map.of();
   var out=new LinkedHashMap<BlockPos,BlockState>();
   for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++){int y=height.applyAsInt(new BlockPos(x,0,z));if(y==Integer.MIN_VALUE)return Map.of();out.put(new BlockPos(x,y,z),Blocks.DIRT_PATH.defaultBlockState());}
   return out;
  }
  var line=line(a,b,variant);if(line.isEmpty()||width<1||width>5)return Map.of();
  int low=-(width-1)/2,high=width/2;
  var out=new LinkedHashMap<BlockPos,BlockState>();for(var pos:line)for(int ox=low;ox<=high;ox++)for(int oz=low;oz<=high;oz++){var q=pos.offset(ox,0,oz);int y=height.applyAsInt(q);if(y==Integer.MIN_VALUE)return Map.of();out.put(new BlockPos(q.getX(),y,q.getZ()),Blocks.DIRT_PATH.defaultBlockState());}return out;
 }
 /** The route that paves a whole rectangle, and the widest side such a yard may have. */
 public static final int AREA=3,AREA_SPAN=32;
 /** Width of a road variant: the bits above the route, surface, lamp and fence bits; zero keeps the old three-block road. */
 public static int width(int variant){int code=(variant>>6)&7;return code==0?3:code;}
 private static boolean blocked(ServerPlayer p,BlockPos pos,boolean road){return blocked(p.serverLevel(),pos,road);}
 /** A cell a road or a house cannot take as it stands: unloaded, protected, occupied, or too steep beside it. */
 public static boolean blocked(net.minecraft.server.level.ServerLevel l,BlockPos pos,boolean road){
  if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos)||l.isOutsideBuildHeight(pos))return true;
  // AD-061: a road may run through the strip around a building where nobody else may build, but never over the building's own blocks or under it.
  if(road?underBuilding(l,pos)||farmReserve(l,pos):OwnershipEvents.disallowedPlacement(l,pos))return true;
  var state=l.getBlockState(pos);if(!road)return !state.isAir()&&!state.canBeReplaced()&&!state.is(Blocks.GRASS_BLOCK)&&!state.is(Blocks.DIRT);
  boolean paved=org.villageastra.world.Roads.tierOf(state)>0&&org.villageastra.world.Roads.cell(l,pos)!=null;
  if(!(paved||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT)||state.is(Blocks.COARSE_DIRT)||state.is(Blocks.PODZOL)||state.is(Blocks.ROOTED_DIRT)||state.is(Blocks.DIRT_PATH))||!l.getBlockState(pos.above()).isAir()||!l.getBlockState(pos.above(2)).isAir())return true;
  for(var d:List.of(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH,net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST))if(!l.hasChunkAt(pos.relative(d))||Math.abs(l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX()+d.getStepX(),pos.getZ()+d.getStepZ())-1-pos.getY())>1)return true;
  return false;
 }
 /** AD-130: a column of a farm's final footprint (barn, eaves, stair tower, on its side) in a settlement of layout 6, where no new road is
  *  planned; older settlements keep their roads. Only road planning asks: the farm's own field and barn work may use its footprint. */
 public static boolean farmReserve(net.minecraft.server.level.ServerLevel l,BlockPos pos){
  for(var entry:SettlementData.get(l.getServer()).entries()){
   if(!entry.dimension().equals(l.dimension().location().toString())||entry.settlement().lotLayout()<org.villageastra.domain.OrganicLots.BARN_LOTS)continue;
   for(var b:entry.settlement().buildings()){if(!b.type().equals("farm"))continue;
    var local=org.villageastra.world.BuildingPlacement.local(entry,b,pos);var reserve=org.villageastra.domain.OrganicLots.farmReserve(entry.settlement().westField(b.id()));
    if(local.getX()>=reserve[0]&&local.getX()<=reserve[2]&&local.getZ()>=reserve[1]&&local.getZ()<=reserve[3])return true;}
  }
  return false;
 }
 /** A column of a settlement building's footprint (at any height), a structural block of one, or a mine's working area. */
 public static boolean underBuilding(net.minecraft.server.level.ServerLevel l,BlockPos pos){
  if(OwnershipEvents.protectedBlock(l,pos))return true;
  for(var entry:SettlementData.get(l.getServer()).entries()){
   if(!entry.dimension().equals(l.dimension().location().toString()))continue;
   for(var b:entry.settlement().buildings()){
    var size=org.villageastra.world.BuildingPlacement.size(b.type(),b.rotation());
    int w=size[0],d=size[1];
    var at=entry.center().offset(b.x(),b.y(),b.z());
    if(pos.getX()>=at.getX()&&pos.getX()<at.getX()+w&&pos.getZ()>=at.getZ()&&pos.getZ()<at.getZ()+d)return true;
    var area=entry.settlement().mineAreas().get(b.id());var local=org.villageastra.world.BuildingPlacement.local(entry,b,pos);
    // AD-104: a farm also covers the ground behind its farmhouse to the far edge of its field box (the lane between them too, as the old
    // depth 16 did and as its generated lot does), turned with the farm.
    if(b.type().equals("farm")){var box=org.villageastra.world.FarmField.box(org.villageastra.world.FarmField.modules(entry,b));int wide=org.villageastra.world.BuildingPlacement.size(b.type(),0)[0];
     if(local.getX()>=Math.min(0,box[0])&&local.getX()<=Math.max(wide-1,box[2])&&local.getZ()>=0&&local.getZ()<=box[3])return true;
     // AD-130: a farm with its barn laid covers the barn's whole footprint (walls, eaves, stair tower).
     if(org.villageastra.world.FarmBarn.laid(entry.settlement(),b)>=org.villageastra.world.FarmField.BARN_FROM){var r=org.villageastra.domain.OrganicLots.farmReserve(entry.settlement().westField(b.id()));
      if(local.getX()>=r[0]&&local.getX()<=r[2]&&local.getZ()>=r[1]&&local.getZ()<=r[3])return true;}}
    if(area!=null&&area.contains(local.getX(),local.getY(),local.getZ(),0))return true;
   }
  }
  return false;
 }
 public static void addView(ServerPlayer p,CompoundTag tag){
  var s=SELECTIONS.get(p.getUUID());if(s==null)return;var e=office(p);
  if(!safe(p)||e==null||!e.settlement().id().equals(s.village)||e.settlement().governance().epoch()!=s.epoch||!s.dimension.equals(e.dimension())||p.blockPosition().distSqr(s.first)>256*256){SELECTIONS.remove(p.getUUID());return;}
  tag.putBoolean("survey",true);tag.putBoolean("road",s.road);tag.putBoolean("twoPoints",s.second!=null);tag.putUUID("id",s.token);tag.putString("design",s.design);tag.putInt("variant",s.variant);tag.putString("dimension",s.dimension);tag.putLong("first",s.first.asLong());if(s.second!=null)tag.putLong("second",s.second.asLong());
  var cells=new ListTag();int conflicts=0;var geometry=geometry(p,s);
  var survey=s.road?null:BuildingOrders.survey(p.serverLevel(),e,s.design,s.variant,s.first);
  if(survey!=null&&!survey.state().isEmpty())geometry=org.villageastra.world.BuildingWood.apply(geometry,survey.state().getString("wood"));
  // A full site survey (orderable design, no early refusal) decides red cells; otherwise the light preview check is used.
  boolean surveyed=survey!=null&&(survey.reason().isEmpty()||survey.reason().equals("conflicts"));
  for(var cell:geometry.entrySet()){if(cell.getValue().isAir())continue;var n=new CompoundTag();n.putLong("pos",cell.getKey().asLong());n.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));n.put("after",NbtUtils.writeBlockState(cell.getValue()));boolean blocked=surveyed?survey.conflicts().contains(cell.getKey()):blocked(p,cell.getKey(),s.road);n.putBoolean("blocked",blocked);if(blocked)conflicts++;cells.add(n);}
  if(surveyed)for(var pos:survey.conflicts())if(!geometry.containsKey(pos)){var n=new CompoundTag();n.putLong("pos",pos.asLong());n.put("before",NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));n.put("after",NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));n.putBoolean("blocked",true);conflicts++;cells.add(n);}
  if(survey!=null){tag.putBoolean("orderable",!survey.reason().equals("design")&&!BuildingOrders.WITHHELD.contains(s.design));tag.putBoolean("canOrder",survey.ok()&&!BuildingOrders.WITHHELD.contains(s.design));tag.putString("orderReason",survey.reason());int items=0;var cost=survey.state().getCompound("cost");for(var key:cost.getAllKeys())items+=cost.getInt(key);tag.putInt("orderItems",items);}
  if(s.road&&s.second!=null&&(((s.variant>>2)&3)>0||((s.variant>>4)&3)!=0)&&conflicts==0){var plan=org.villageastra.world.Roads.plan(p.serverLevel(),e,line(s.first,s.second,s.variant&3),geometry,(s.variant>>2)&3,((s.variant>>4)&1)==1,((s.variant>>5)&1)==1);int items=0;for(var key:plan.getCompound("cost").getAllKeys())items+=plan.getCompound("cost").getInt(key);tag.putInt("roadItems",items);tag.putBoolean("roadBusy",org.villageastra.world.Roads.active(p.serverLevel(),e.settlement().id()));}
  tag.put("cells",cells);tag.put("materials",new ListTag());tag.putInt("conflicts",conflicts);tag.putInt("surveyCells",cells.size());tag.putBoolean("canLay",s.road&&s.second!=null&&!cells.isEmpty()&&conflicts==0);
 }
 private static void message(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("survey.villageastra."+key),true);}
}
