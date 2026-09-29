package org.villageastra.gametest;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** Design review (owner 2026-09-24): every design at every level, and the castle shells, written as voxel.py plans so each building can be
 *  audited and drawn from all four sides, from above and floor by floor. Runs only with
 *  -PlayoutDump=<dir> (`-PgtOnly=layout_dump`); without it the test passes at once and writes nothing. Mod blocks carry their map colour and
 *  whether they are a full cube, so the preview can draw them. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class LayoutDumpGameTests {
 @GameTest(template="empty",batch="layout_dump") public static void dumpEveryDesignAtEveryLevel(GameTestHelper h){
  String dir=System.getProperty("villageastra.layoutDump");
  if(dir==null||dir.isBlank()){h.succeed();return;}
  try{var out=Path.of(dir);Files.createDirectories(out);var index=new JsonArray();
   for(var d:BuildingBlueprints.designs()){int max=LevelArchitecture.hasLevels(d.id())||VillageStyle.LEVELLED.contains(d.id())?Math.max(1,BuildingTiers.max(d.id())):1;
    for(int level=1;level<=max;level++){String id=BuildingTiers.layoutId(d.id(),level);
     var layout=BuildingBlueprints.layout(id,BlockPos.ZERO);
     index.add(write(out,d.id()+"-"+level,id,layout));}}
   for(int level=1;level<=6;level++)index.add(write(out,"castle-"+level,CastleArchitecture.PREVIEW_PREFIX+level,CastleArchitecture.shell(level,BlockPos.ZERO)));
   Files.writeString(out.resolve("index.json"),new GsonBuilder().setPrettyPrinting().create().toJson(index),StandardCharsets.UTF_8);
   // Every block with every property and all its values, straight from the game: voxel.py checks states against it.
   var registry=new JsonObject();
   for(var block:BuiltInRegistries.BLOCK){var props=new JsonObject();
    for(var prop:block.getStateDefinition().getProperties()){var values=new JsonArray();for(var v:prop.getPossibleValues())values.add(propName(prop,v));props.add(prop.getName(),values);}
    registry.add(BuiltInRegistries.BLOCK.getKey(block).toString(),props);}
   Files.writeString(out.resolve("registry.json"),new Gson().toJson(registry),StandardCharsets.UTF_8);
   h.succeed();
  }catch(Exception ex){throw new GameTestAssertException("Layout dump failed: "+ex);}
 }
 @SuppressWarnings({"unchecked","rawtypes"})
 private static String propName(net.minecraft.world.level.block.state.properties.Property prop,Comparable value){return prop.getName(value);}
 private static JsonObject write(Path out,String file,String id,Map<BlockPos,BlockState> layout)throws Exception{
  int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,y1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
  for(var p:layout.keySet()){x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());}
  String pool="#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&'()*+,-/:;<=>?@[]^_`{|}~";
  var legend=new LinkedHashMap<String,Character>();var mod=new JsonObject();int extra=0;
  int w=x1-x0+1,hgt=y1-y0+1,d=z1-z0+1;char[][][] grid=new char[hgt][d][w];for(var a:grid)for(var r:a)Arrays.fill(r,' ');
  for(var e:layout.entrySet()){var s=e.getValue();String spec=BlockStateParser.serialize(s);
   Character ch=legend.get(spec);
   if(ch==null){ch=legend.size()<pool.length()?pool.charAt(legend.size()):(char)(0x100+extra++);legend.put(spec,ch);
    if(!BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("minecraft")){var m=new JsonObject();
     m.addProperty("color",s.getMapColor(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).col);
     m.addProperty("full",s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));mod.add(spec,m);}}
   var p=e.getKey();grid[p.getY()-y0][p.getZ()-z0][p.getX()-x0]=ch;}
  var plan=new JsonObject();plan.addProperty("name",file);plan.addProperty("design",id);
  var origin=new JsonArray();origin.add(x0);origin.add(y0);origin.add(z0);plan.add("origin",origin);
  var leg=new JsonObject();for(var e:legend.entrySet())leg.addProperty(String.valueOf(e.getValue()),e.getKey());plan.add("legend",leg);
  plan.add("mod",mod);
  var layers=new JsonArray();for(var a:grid){var rows=new JsonArray();for(var r:a)rows.add(new String(r));layers.add(rows);}plan.add("layers",layers);
  Files.writeString(out.resolve(file+".json"),new Gson().toJson(plan),StandardCharsets.UTF_8);
  var row=new JsonObject();row.addProperty("file",file+".json");row.addProperty("design",id);row.addProperty("size",w+"x"+hgt+"x"+d);return row;
 }
}
