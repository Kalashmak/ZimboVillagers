package org.villageastra.gametest;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** AD-148 (dev tool): writes the table of frozen level equipment — for every design with levels that is drawn once (not a design drawn anew for
 *  each level, not an annex, not the warehouse of AD-141) its equipment of levels II..VI, its core cell and the one or two lamps level II hangs,
 *  exactly as LevelArchitecture computes them from the design in this build. Run on the build whose buildings stand in the worlds:
 *  {@code runGameTestServer -PgtOnly=equipment_table -PequipmentTable=<file>}; without the flag it passes at once and writes nothing. It uses only
 *  what LevelArchitecture had before the freeze, so the same file runs on an older build. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class EquipmentTableGameTests {
 static boolean freezable(String id){return LevelArchitecture.hasLevels(id)&&!VillageStyle.LEVELLED.contains(id)&&!id.endsWith("_annex")&&!id.equals("warehouse")&&!id.equals(ForesterHut.TYPE);}
 private static JsonArray cell(BlockPos p){var a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
 private static final BlockState AIR=Blocks.AIR.defaultBlockState();
 static boolean full(BlockState s){return !s.isAir()&&s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO);}
 /** A resident walks through air, a door, and anything no higher than a carpet (a rug, a doormat, a pressure plate). */
 private static boolean open(BlockState s){return s.isAir()||s.getBlock() instanceof DoorBlock||s.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).max(net.minecraft.core.Direction.Axis.Y)<=0.0625;}
 /** Floor cells a resident walks to from the doors of a level-I design, storey by storey (as LevelArchitecture reads it). */
 static Set<BlockPos> reach(Map<BlockPos,BlockState> raw,int w,int d){
  java.util.function.Function<BlockPos,BlockState> at=p->raw.getOrDefault(p,AIR);
  var doors=new ArrayList<BlockPos>();for(var e:raw.entrySet())if(e.getValue().getBlock() instanceof DoorBlock&&e.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER)doors.add(e.getKey());
  var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();
  java.util.function.Predicate<BlockPos> walk=p->p.getX()>=0&&p.getX()<w&&p.getZ()>=0&&p.getZ()<d&&open(at.apply(p))&&open(at.apply(p.above()))&&full(at.apply(p.below()));
  for(var door:doors)if(walk.test(door)&&seen.add(door))queue.add(door);
  while(!queue.isEmpty()){var p=queue.poll();for(var dir:Direction.Plane.HORIZONTAL){var q=p.relative(dir);if(walk.test(q)&&seen.add(q))queue.add(q);}}
  return seen;
 }
 /** Whether a floor piece of equipment in cell p stands where a resident reaches it: the cell or one beside it is walked to from a door. */
 static boolean near(Set<BlockPos> reach,BlockPos p){if(reach.contains(p))return true;for(var dir:Direction.Plane.HORIZONTAL)if(reach.contains(p.relative(dir)))return true;return false;}
 @SuppressWarnings("unchecked")
 private static List<BlockPos> lamps(String type)throws Exception{
  // The lamps LevelArchitecture.apply hangs: the first and the last of the analysis' lamp cells (one when there is one).
  var m=LevelArchitecture.class.getDeclaredMethod("analysis",String.class);m.setAccessible(true);var a=m.invoke(null,type);
  var acc=a.getClass().getDeclaredMethod("lamps");acc.setAccessible(true);var all=(List<BlockPos>)acc.invoke(a);
  var out=new ArrayList<BlockPos>();for(int i=0;i<Math.min(2,all.size());i++)out.add(all.get(i==0?0:all.size()-1));return out;
 }
 @GameTest(template="empty",batch="equipment_table") public static void writeTable(GameTestHelper h){
  String file=System.getProperty("villageastra.equipmentTable");
  if(file==null||file.isBlank()){h.succeed();return;}
  try{var types=new JsonObject();
   for(var d:BuildingBlueprints.designs()){String id=d.id();if(!freezable(id))continue;var o=new JsonObject();
    var eq=new JsonArray();var raw=BuildingBlueprints.raw(id,BlockPos.ZERO);var reach=reach(raw,d.width(),d.depth());
    for(var p:LevelArchitecture.equipment(id)){var e=new JsonObject();e.addProperty("x",p.local().getX());e.addProperty("y",p.local().getY());e.addProperty("z",p.local().getZ());
     e.addProperty("state",BlockStateParser.serialize(p.state()));e.addProperty("level",p.level());
     // Whether a resident reaches it in the design it was frozen from: such a cell must stay reachable (FrozenEquipmentGameTests).
     if(near(reach,p.local()))e.addProperty("reach",true);eq.add(e);}
    o.add("equipment",eq);
    try{o.add("core",cell(LevelArchitecture.core(id)));}catch(IllegalStateException none){}
    var ls=new JsonArray();for(var p:lamps(id))ls.add(cell(p));o.add("lamps",ls);
    types.add(id,o);}
   var root=new JsonObject();root.addProperty("schema",1);
   root.addProperty("note","AD-148: level equipment, core and lamps frozen from the build whose buildings stand in the worlds; written by EquipmentTableGameTests, checked by FrozenEquipmentGameTests. Never edit by hand.");
   root.add("types",types);
   var path=Path.of(file);if(path.getParent()!=null)Files.createDirectories(path.getParent());
   Files.writeString(path,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)+"\n",StandardCharsets.UTF_8);
   h.succeed();
  }catch(Exception ex){throw new GameTestAssertException("Equipment table failed: "+ex);}
 }
}
