package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-131: the forester's hut I..VI. Each level is a plan of its own on the 15x21 lot (VillageStyle "forester@N"), with the door, the stock
 *  chest and the core in the same cells at every level, and the stations of level N in cells free in every plan from N on. Pure geometry and
 *  the hut's cells; the work is ForestWork (the forester) and ForestryMachines (the saw from IV, the courtyard grove at VI). */
public final class ForesterHut {
 private ForesterHut(){}
 public static final String TYPE="forester";
 public static final BlockPos DOOR=new BlockPos(4,1,0),CHEST=new BlockPos(1,1,4),CORE=new BlockPos(5,1,3);
 /** The saw of the sawmill (IV), its plank barrel, and the automatic saw of the courtyard (VI). */
 public static final BlockPos SAW=new BlockPos(11,1,4),PLANK_BARREL=new BlockPos(13,1,2),AUTO_SAW=new BlockPos(13,1,14);
 static{
  // One source for the grove: the plan draws the cells balance/forester.json names.
  if(ForestBalance.GROVE.size()!=VillageStyle.FOREST_GROVE.length)throw new IllegalStateException("Grove cells differ from the hut plan");
  for(int i=0;i<ForestBalance.GROVE.size();i++)if(!Arrays.equals(ForestBalance.GROVE.get(i),VillageStyle.FOREST_GROVE[i]))throw new IllegalStateException("Grove cell "+i+" differs from the hut plan");
 }
 /** The courtyard of level VI (x2..12, z10..18): nothing of the plan stands in it above its ground. */
 public static boolean courtyard(int x,int z){return VillageStyle.forestCourtyard(x,z);}
 public static boolean courtyard(BlockPos local){return courtyard(local.getX(),local.getZ());}
 public static boolean groveCell(int x,int z){return VillageStyle.forestGrove(x,z);}
 /** The six grove cells, hut-local, at the height a sapling stands (y1). */
 public static List<BlockPos> grove(){var out=new ArrayList<BlockPos>();for(var c:ForestBalance.GROVE)out.add(new BlockPos(c[0],1,c[1]));return List.copyOf(out);}
 private static volatile List<LevelArchitecture.Placed> KIT;
 /** Every station of levels II..VI with its level, in local cells (the states of VillageStyle.FORESTER_KIT_STATES). */
 public static List<LevelArchitecture.Placed> kits(){
  var k=KIT;if(k!=null)return k;var out=new ArrayList<LevelArchitecture.Placed>();
  for(int i=0;i<VillageStyle.FORESTER_KIT.length;i++){var c=VillageStyle.FORESTER_KIT[i];out.add(new LevelArchitecture.Placed(new BlockPos(c[1],c[2],c[3]),DistinctArchitecture.state(VillageStyle.FORESTER_KIT_STATES[i]),c[0]));}
  return KIT=List.copyOf(out);
 }
 /** The stations of levels II..level. */
 public static List<LevelArchitecture.Placed> kit(int level){return kits().stream().filter(p->p.level()<=level).toList();}
 /** The equipment LevelArchitecture checks a level by: the stations, and the core of each level at its grade in CORE. */
 public static List<LevelArchitecture.Placed> equipment(){
  var out=new ArrayList<LevelArchitecture.Placed>();var core=Cores.block(TYPE);
  for(int level=2;level<=LevelArchitecture.MAX;level++){final int at=level;kits().stream().filter(p->p.level()==at).forEach(out::add);
   if(core!=null)out.add(new LevelArchitecture.Placed(CORE,core.defaultBlockState().setValue(BuildingCoreBlock.GRADE,level),level));}
  return List.copyOf(out);
 }
 /** The block ids of level N's stations (the kits.forester of levels.json must list the same). */
 public static List<String> kitItems(int level){return kits().stream().filter(p->p.level()==level).map(p->BuiltInRegistries.BLOCK.getKey(p.state().getBlock()).toString()).sorted().toList();}
 public static BlockPos at(SettlementData.Entry e,Settlement.Building hut,BlockPos local){return BuildingPlacement.at(e,hut,local.getX(),local.getY(),local.getZ());}
 /** The hut's own door in the world: where the radius of the forester's felling is measured from. */
 public static BlockPos door(SettlementData.Entry e,Settlement.Building hut){return at(e,hut,DOOR);}
 static boolean stationStands(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,Settlement.Building hut,int level){
  for(var p:kit(level)){if(p.level()!=level)continue;var at=at(e,hut,p.local());if(!l.hasChunkAt(at)||l.getBlockState(at).getBlock()!=p.state().getBlock())return false;}return true;}
 static Block block(BlockState s){return s==null?Blocks.AIR:s.getBlock();}
}
