package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-059: a building of the settlement that lost blocks — blown up, burnt out, broken in a siege — is put back by the builders.
 *  The repair is an ordinary construction project of that building at its own place, paid from the hall stock like any building. */
public final class BuildingRepairs {
 private BuildingRepairs(){}
 /** The design a building stands in now: a town hall grows with the settlement. */
 public static String design(SettlementData.Entry e,Settlement.Building b){
  // AD-073: a building is repaired to the level it is kept at.
  return BuildingTiers.layoutId(e.settlement(),b.type(),BuildingTiers.built(e,b));
 }
 /** Part of the design that has to be standing for a building to count as damaged rather than never built or wiped out. */
 public static final double REMAINS=.15;
 /** Cells of the design that no longer hold their block; empty when nothing is missing, part of the building is not loaded,
  *  or so little of it stands that it is no damaged building at all. */
 public static List<BlockPos> damage(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  if(ArchitectureMigration.waiting(l,e,b))return List.of();
  var design=design(e,b);
  if(BuildingBlueprints.designs().stream().noneMatch(d->d.id().equals(design)))return List.of();
  var base=e.center().offset(b.x(),b.y(),b.z());var missing=new ArrayList<BlockPos>();int solid=0;
  // AD-122: the drive digs its flight on through the mouth floor of the built shaft; a cell the mine's drive has claimed is the drive's.
  var drive=b.type().equals("mine")?e.settlement().mineAreas().get(b.id()):null;
  for(var cell:BuildingPlacement.layout(e,b,design).entrySet()){
   // Saplings and crops grow and are harvested: they are the workers' business, not a loss for the builders.
   if(cell.getValue().isAir()||cell.getValue().is(net.minecraft.tags.BlockTags.SAPLINGS)||cell.getValue().getBlock() instanceof net.minecraft.world.level.block.CropBlock)continue;solid++;
   if(!l.hasChunkAt(cell.getKey()))return List.of();
   if(drive!=null){var local=BuildingPlacement.local(e,b,cell.getKey());if(local.getZ()>=7&&drive.contains(local.getX(),local.getY(),local.getZ(),0)){solid--;continue;}}
   // A block that could not stand there anyway is not a loss, or the builders would put it back for ever.
   if(!present(l.getBlockState(cell.getKey()),cell.getValue())&&cell.getValue().canSurvive(l,cell.getKey()))missing.add(cell.getKey());
  }
  return solid-missing.size()<solid*REMAINS?List.of():missing;
 }
 /** The design's block is there: the same block, or any flower pot where the design has a potted plant (the plant is not the builders' to replace). */
 public static boolean present(net.minecraft.world.level.block.state.BlockState now,net.minecraft.world.level.block.state.BlockState design){
  // AD-112: a core stands when it is this building's core at the design's grade or above; a lower grade still lacks its rings.
  if(design.getBlock() instanceof BuildingCoreBlock)return now.is(design.getBlock())&&now.getValue(BuildingCoreBlock.GRADE)>=design.getValue(BuildingCoreBlock.GRADE);
  if(now.getBlock()==design.getBlock())return true;
  // Soil lives: grass dies to dirt under a sapling, a hoe turns it to farmland, feet wear a path — it is still the yard's ground.
  if(soil(now)&&soil(design))return true;
  // AD-143: where the design has a chair or a table, any furniture stands — a chair or table of another wood, or the stair seat and fence
  // table a restaurant or a porch had before the furniture came (old halls keep working; nobody is sent to swap them).
  if(design.getBlock() instanceof ChairBlock||design.getBlock() instanceof TableBlock)
   return now.getBlock() instanceof ChairBlock||now.getBlock() instanceof TableBlock||now.getBlock() instanceof net.minecraft.world.level.block.StairBlock||now.getBlock() instanceof net.minecraft.world.level.block.FenceBlock;
  // AD-144: where the design has a framed window, the glass pane a building was glazed with before (or a window of another wood) stands —
  // nobody is sent to reglaze an old house, by a repair or a level's rebuild (the estimate of a level holds no windows either).
  if(design.getBlock() instanceof FramedWindowBlock)
   return now.getBlock() instanceof FramedWindowBlock||now.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock&&!now.is(net.minecraft.world.level.block.Blocks.IRON_BARS);
  return now.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock&&design.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock;
 }
 private static boolean soil(net.minecraft.world.level.block.state.BlockState s){
  return s.is(net.minecraft.tags.BlockTags.DIRT)||s.is(net.minecraft.world.level.block.Blocks.FARMLAND)||s.is(net.minecraft.world.level.block.Blocks.DIRT_PATH);
 }
 /** The repair project of one building, or null when it cannot be planned now. */
 public static CompoundTagOrReason plan(ServerLevel l,SettlementData.Entry e,Settlement.Building b){
  var survey=BuildingOrders.survey(l,e,design(e,b),b.rotation(),e.center().offset(b.x(),b.y(),b.z()),b);
  if(!survey.reason().isEmpty()&&!survey.reason().equals("conflicts"))return new CompoundTagOrReason(null,survey.reason());
  if(!survey.conflicts().isEmpty())return new CompoundTagOrReason(null,"conflicts");
  return new CompoundTagOrReason(survey.state(),"");
 }
 public record CompoundTagOrReason(net.minecraft.nbt.CompoundTag state,String reason){}
 /** Looks over one settlement: the first damaged building is queued for repair when the builders have nothing else queued. */
 public static String check(MinecraftServer server,SettlementData.Entry e){
  var l=server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(e.dimension())));
  if(l==null||HallUpgradeGoal.pending(l,e.settlement().id())||Sieges.besieged(server,e.settlement().id()))return "busy";
  for(var b:List.copyOf(e.settlement().buildings())){
   var missing=damage(l,e,b);if(missing.isEmpty())continue;
   var planned=plan(l,e,b);if(planned.state()==null)continue;
   if(planned.state().getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty())continue;
   HallUpgradeGoal.enqueue(l,e,planned.state());SettlementData.get(server).setDirty();
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_REPAIR queued {} at {}: {} blocks missing, first {} holds {}",b.type(),e.center().offset(b.x(),b.y(),b.z()).toShortString(),missing.size(),missing.get(0).toShortString(),l.getBlockState(missing.get(0)));
   return "queued";
  }
  return "";
 }
}
