package org.villageastra.world;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import net.minecraft.core.BlockPos;
import org.villageastra.domain.CoreEffects;
import org.villageastra.domain.MineArea;
import org.villageastra.domain.MineDrive;
import org.villageastra.domain.Settlement;
import org.villageastra.persistence.AtomicRecord;
import org.villageastra.server.SettlementData;
/** AD-076: the drive record of one mine, shared by the miner and the machine — the same file, the same geometry, so either may carry the adit on. */
public final class MineWork {
 private MineWork(){}
 /** AD-122 (owner, 2026-09-21): a drive opened now is 5 high, one block taller than the 4 before, with stairs on its steps; a drive begun earlier keeps its height. */
 public static final int HEIGHT=5;
 /** AD-122 (owner): a drive opened now starts one block below the last tread of the built shaft (its mouth floor at z 7-8 is dug through), so
  *  the built stair and the dug one are one flight. A drive begun earlier keeps SHAFT_DESCENT. */
 public static final int DRIVE_DESCENT=BuildingBlueprints.SHAFT_DESCENT+1;
 public static Path path(ServerLevel l,UUID building){return l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+building+".bin");}
 /** The record as it stands, or a fresh drive at the bottom of the built shaft. */
 public static CompoundTag read(ServerLevel l,Settlement.Building b){
  var file=path(l,b.id());
  if(Files.exists(file)){
   try{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(file))));}
   catch(IOException e){throw new IllegalStateException(e);}
  }
  var t=new CompoundTag();t.putInt("schema",1);t.putInt("width",3);t.putInt("height",HEIGHT);t.putInt("descent",DRIVE_DESCENT);
  t.putString("stage","choose");t.putUUID("operation",UUID.randomUUID());return t;
 }
 public static void write(ServerLevel l,Settlement.Building b,CompoundTag t){
  try{var bytes=new ByteArrayOutputStream();NbtIo.write(t,new DataOutputStream(bytes));AtomicRecord.write(path(l,b.id()),bytes.toByteArray());}
  catch(IOException e){throw new IllegalStateException(e);}
 }
 /** AD-112: a drive record read as MineDrive sees it — its shape and where it stands. */
 public static MineDrive.Shape shape(CompoundTag t){
  var base=MineDrive.Shape.of(t.getInt("width")==3?3:1,Math.max(3,Math.min(5,t.getInt("height"))),Math.max(0,t.getInt("descent")));
  int length=Math.max(base.galleryLength(),Math.min(MineArea.maxGalleryLength(),t.getInt("prospectLength")));
  return new MineDrive.Shape(base.width(),base.height(),base.descent(),length,base.galleryHeight(),base.beamEvery());
 }
 public static MineDrive.Drive drive(CompoundTag t){return new MineDrive.Drive(Math.max(0,t.getInt("step")),Math.max(0,t.getInt("cell")),Math.max(MineDrive.EAST,Math.min(MineDrive.DONE,t.getInt("side"))),Math.max(0,t.getInt("run")));}
 /** A record from before AD-112 (no floor noted) and the stair view below: a floor no drive reaches, so only the stair is read. */
 private static final int STAIR_ONLY=4095;
 private static int floorOf(CompoundTag t){return t.contains("floorStep")?t.getInt("floorStep"):STAIR_ONLY;}
 private static void put(CompoundTag t,MineDrive.Drive d,int floor){t.putInt("step",d.step());t.putInt("cell",d.cell());t.putInt("side",d.side());t.putInt("run",d.run());
  // Past the stair the drive stands in the galleries of this floor; a later, deeper floor sends it back to the stair (next()).
  if(d.step()>floor)t.putInt("galleryOf",floor);else t.remove("galleryOf");}
 /** AD-112: the Y of the floor this mine's drive stops at for a level — the table's floor_y, or deeper for the minimum steps a level, never
  *  near the bottom of the world (what the card shows, not the bare table number). */
 public static int floorY(ServerLevel l,SettlementData.Entry e,Settlement.Building b,int level){int mouth=BuildingPlacement.origin(e,b).getY()-Math.max(0,read(l,b).getInt("descent"));return mouth-MineDrive.floorStep(level,mouth,l.getMinBuildHeight());}
 /** F of this mine's working level (MineDrive.floorStep): how many steps its core lets the stair go below the bottom of its mouth. */
 public static int floorStep(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){return MineDrive.floorStep(BuildingTiers.level(l,e,b),BuildingPlacement.origin(e,b).getY()-Math.max(0,t.getInt("descent")),l.getMinBuildHeight());}
 /** The drive's next target under the working level, which is noted in the record ("floorStep") so the advance and a crash recovery
  *  (JobCargo) step on under the same floor. A drive standing in a gallery or at the floor whose level has since been raised goes back
  *  to the first cell of its next stair step; the gallery it left stays claimed. */
 public static MineDrive.Target next(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  int floor=MineProspecting.activeFloor(t,floorStep(l,e,b,t));var d=drive(t);
  if(t.contains("galleryOf")&&MineDrive.stage(d,floor)==MineDrive.Stage.STAIR){d=new MineDrive.Drive(d.step(),0,MineDrive.EAST,0);put(t,d,floor);}
  t.putInt("floorStep",floor);return MineDrive.next(d,floor,shape(t));
 }
 /** A local cell of the drive, in the world (null stays null). */
 public static BlockPos at(SettlementData.Entry e,Settlement.Building b,MineDrive.Cell c){return c==null?null:BuildingPlacement.at(e,b,c.x(),c.y(),c.z());}
 /** The next cell of the drive in the world, or null at the floor of its level. */
 public static BlockPos target(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){return at(e,b,next(l,e,b,t).cell());}
 /** The stair cell the record stands at, whatever the level (a probe's report). */
 public static BlockPos target(SettlementData.Entry e,Settlement.Building b,CompoundTag t){return at(e,b,MineDrive.next(drive(t),STAIR_ONLY,shape(t)).cell());}
 /** One cell done (dug or found open), under the floor its target was chosen with. */
 public static void step(CompoundTag t){int floor=floorOf(t);var d=drive(t);var s=shape(t);var n=MineDrive.advance(d,floor,s);
  if(t.getBoolean("prospectExtension")&&MineDrive.stage(d,floor)!=MineDrive.Stage.STAIR&&n.side()!=d.side())n=new MineDrive.Drive(d.step(),0,MineDrive.DONE,0);
  // AD-122: a stair step finished — its stairs are due ("stairStep"), set by the miner from the stone he carries before he goes on.
  if(MineDrive.stage(d,floor)==MineDrive.Stage.STAIR&&n.step()>d.step()&&!MineDrive.stairs(d.step(),s).isEmpty())t.putInt("stairStep",d.step());
  // AD-122: a finished stair step or gallery column may be due a light ("lightsDue": cell, stand, wall — 8 numbers each).
  var light=MineDrive.stage(d,floor)==MineDrive.Stage.STAIR?(n.step()>d.step()?MineDrive.stairLight(d.step(),s,ResourceWorkGoal.LIGHT_STEPS):null)
   :d.cell()==s.galleryHeight()-1?MineDrive.galleryLight(d,floor,s,ResourceWorkGoal.LIGHT_COLUMNS):null;
  if(light!=null){var due=t.getIntArray("lightsDue");if(due.length<8*16){var more=java.util.Arrays.copyOf(due,due.length+8);int i=due.length;
   more[i]=light.cell().x();more[i+1]=light.cell().y();more[i+2]=light.cell().z();more[i+3]=light.stand().x();more[i+4]=light.stand().y();more[i+5]=light.stand().z();more[i+6]=light.wallX();more[i+7]=light.wallZ();t.putIntArray("lightsDue",more);}}
  put(t,n,floor);}
 /** The gallery cell is unsafe (not ground, or a fluid in or beside it): that gallery ends, the drive turns to the other side or to the floor. */
 public static void blocked(CompoundTag t){int floor=floorOf(t);var d=drive(t);put(t,t.getBoolean("prospectExtension")&&MineDrive.stage(d,floor)!=MineDrive.Stage.STAIR?new MineDrive.Drive(d.step(),0,MineDrive.DONE,0):MineDrive.blocked(d,floor),floor);}
 /** One cell done: the drive moves on (a finished stair row steps deeper, a finished gallery turns) and the record is written. */
 public static void advance(ServerLevel l,Settlement.Building b,CompoundTag t){step(t);write(l,b,t);}
 /** Whether the cell the record chose is one of a gallery (its stage is noted with the choice), not of the stair. */
 public static boolean gallery(CompoundTag t){var s=t.getString("mineStage");return s.equals("EAST")||s.equals("WEST");}
 /** Notes the choice of a target in the record: its stage, its stand and the beam that follows it (none: no "beam"). */
 public static void chose(CompoundTag t,MineDrive.Target target){
  t.putString("mineStage",target.stage().name());t.remove("beam");t.remove("access");if(target.cell()==null)return;
  t.putIntArray("access",new int[]{target.stand().x(),target.stand().y(),target.stand().z()});
  var beam=target.beam();if(beam==null)return;
  // A stair beam is set from the tread of the next step, as before AD-112; a gallery's from where its column was dug.
  var stand=target.stage()==MineDrive.Stage.STAIR?MineDrive.next(new MineDrive.Drive(drive(t).step()+1,0,MineDrive.EAST,0),STAIR_ONLY,shape(t)).stand():target.stand();
  var cells=new int[beam.count()*3];var ids=new ListTag();
  for(int i=0;i<beam.count();i++){var c=beam.cells().get(i);cells[3*i]=c.x();cells[3*i+1]=c.y();cells[3*i+2]=c.z();ids.add(StringTag.valueOf(beam.ids().get(i)));}
  var tag=new CompoundTag();tag.putIntArray("cells",cells);tag.put("ids",ids);tag.putIntArray("stand",new int[]{stand.x(),stand.y(),stand.z()});t.put("beam",tag);
 }
 /** Where the worker stands to dig the chosen cell, or null for a record from before AD-112. */
 public static MineDrive.Cell access(CompoundTag t){var a=t.getIntArray("access");return a.length==3?new MineDrive.Cell(a[0],a[1],a[2]):null;}
 /** The beam the support stage sets: the one noted with the chosen cell, or — a record from before AD-112 — the stair beam over the row
  *  above its step, as MineDrive gives it (none when that row takes no beam). */
 public static MineDrive.Beam beam(CompoundTag t){
  if(t.contains("beam")){var tag=t.getCompound("beam");var raw=tag.getIntArray("cells");var ids=tag.getList("ids",Tag.TAG_STRING);var cells=new ArrayList<MineDrive.Cell>();var names=new ArrayList<String>();
   for(int i=0;i<ids.size()&&3*i+2<raw.length;i++){cells.add(new MineDrive.Cell(raw[3*i],raw[3*i+1],raw[3*i+2]));names.add(ids.getString(i));}return new MineDrive.Beam(List.copyOf(cells),List.copyOf(names));}
  var s=shape(t);int step=Math.max(1,t.getInt("step"));var beam=MineDrive.next(new MineDrive.Drive(step-1,s.width()*s.height()-1,MineDrive.EAST,0),STAIR_ONLY,s).beam();
  return beam==null?new MineDrive.Beam(List.of(),List.of()):beam;
 }
 /** Where the worker stands to set the beam. */
 public static MineDrive.Cell beamStand(CompoundTag t){
  if(t.contains("beam")){var a=t.getCompound("beam").getIntArray("stand");if(a.length==3)return new MineDrive.Cell(a[0],a[1],a[2]);}
  return MineDrive.next(new MineDrive.Drive(Math.max(0,t.getInt("step")),0,MineDrive.EAST,0),STAIR_ONLY,shape(t)).stand();
 }
 /** The journal id of the i-th log fetched for the beam: a narrow stair's one log keeps the operation's own id, as before AD-112. */
 public static UUID timberId(CompoundTag t,UUID operation,int i){return t.getInt("width")==1&&!gallery(t)?operation:Settlement.childId(operation,"timber/"+i);}
 /** The journal id of the i-th log set: the beam's own id (a gallery's carries its side, floor and column, never a stair's). */
 public static UUID beamId(CompoundTag t,UUID operation,int i){return Settlement.childId(operation,beam(t).ids().get(i));}
 /** Whether the cell just done asks for a beam: the stair's every fourth row, a gallery's every fourth column (MineDrive). A record
  *  from before AD-112 (no stage noted) keeps its old rule. */
 public static boolean needsBeam(CompoundTag t){return t.contains("mineStage")?t.contains("beam"):t.getInt("cell")==0&&t.getInt("step")>0&&t.getInt("step")%CoreEffects.mine().beamEvery()==0;}
 /** The claim of the cell the record chose, before its drive moves on: its stair step, or its gallery as far as this column. */
 public static void claim(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t){
  var data=SettlementData.get(l.getServer());var s=e.settlement();
  if(gallery(t)){var g=gallery(t,s.mineAreas().containsKey(b.id()));if(g!=null&&s.noteMine(b.id(),g))data.setDirty();return;}
  if(s.noteMine(b.id(),Math.max(0,t.getInt("step")),Math.max(1,t.getInt("width")),Math.max(3,t.getInt("height")),t.getInt("descent")))data.setDirty();
 }
 /** The gallery claim of the chosen cell (null on the stair, or when the stair itself is not claimed yet). */
 public static MineArea.Gallery gallery(CompoundTag t,boolean stairClaimed){return stairClaimed&&gallery(t)?MineDrive.dug(drive(t),floorOf(t)):null;}
 /** Keep the already built stair and mouth lining: the excavation cursor must pass these cells without mining its own floor. */
 public static boolean builtLining(ServerLevel l,SettlementData.Entry e,Settlement.Building b,BlockPos pos){var local=BuildingPlacement.local(e,b,pos);if(local.getY()>0||local.getZ()<0||local.getZ()>8)return false;var expected=BuildingPlacement.layout("mine",e.center().offset(b.x(),b.y(),b.z()),b.rotation()).get(pos);return expected!=null&&(expected.is(Blocks.STONE_BRICKS)||expected.is(Blocks.STONE_BRICK_STAIRS))&&l.getBlockState(pos).getBlock()==expected.getBlock();}
 /** AD-122: the built lining for this drive — a drive of DRIVE_DESCENT digs through the mouth floor of the built shaft (z 7-8) to carry its flight on. */
 public static boolean builtLining(ServerLevel l,SettlementData.Entry e,Settlement.Building b,BlockPos pos,CompoundTag t){
  if(t.getInt("descent")>BuildingBlueprints.SHAFT_DESCENT&&BuildingPlacement.local(e,b,pos).getZ()>=7)return false;return builtLining(l,e,b,pos);}
 /** Ground this drive may take: soil, stone and ore, and for a drive of DRIVE_DESCENT the built mouth floor it digs through. */
 public static boolean diggable(ServerLevel l,SettlementData.Entry e,Settlement.Building b,BlockPos pos,CompoundTag t){
  return diggable(l.getBlockState(pos))||MinePlugs.owns(l,b,pos)||t.getInt("descent")>BuildingBlueprints.SHAFT_DESCENT&&BuildingPlacement.local(e,b,pos).getZ()>=7&&builtLining(l,e,b,pos);}
 /** AD-112: a gallery cell the drive may not take — not open and not ground, a block entity, or a fluid in it or beside it; the gallery
  *  ends there and the drive turns (the stair instead stops at unsafe_ground, and a fluid beside it keeps the miner off it). */
 public static boolean unsafeGallery(ServerLevel l,BlockPos pos){
  var s=l.getBlockState(pos);if(!s.getFluidState().isEmpty()||l.getBlockEntity(pos)!=null||!s.isAir()&&!diggable(s))return true;
  for(var d:net.minecraft.core.Direction.values())if(!l.getFluidState(pos.relative(d)).isEmpty())return true;
  return false;
 }
 /** A dry gallery also needs a floor: an air column over a natural cave is not a safe passage. */
 public static boolean supported(ServerLevel l,BlockPos feet){var p=feet.below();return l.hasChunkAt(p)&&l.getFluidState(p).isEmpty()&&l.getBlockState(p).isFaceSturdy(l,p,net.minecraft.core.Direction.UP)&&!l.getBlockState(p).is(Blocks.MAGMA_BLOCK);}
 public static boolean galleryFloor(ServerLevel l,SettlementData.Entry e,Settlement.Building b,CompoundTag t,MineDrive.Cell cell){return supported(l,BuildingPlacement.at(e,b,cell.x(),-floorOf(t)-t.getInt("descent"),cell.z()));}
 /** Ground a drive may take: soil, stone and ore — never a container, timber or fluid. */
 public static boolean diggable(BlockState s){
  if(s.isAir()||s.is(Blocks.BEDROCK)||!s.getFluidState().isEmpty())return false;
  return s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)
   ||s.is(BlockTags.REDSTONE_ORES)||s.is(BlockTags.LAPIS_ORES)||s.is(BlockTags.DIAMOND_ORES)||s.is(BlockTags.DIRT)||s.is(Blocks.GRAVEL)||s.is(BlockTags.SAND)||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.CLAY)||s.is(Blocks.TUFF)||s.is(Blocks.CALCITE)||s.is(Blocks.DRIPSTONE_BLOCK);
 }
}
