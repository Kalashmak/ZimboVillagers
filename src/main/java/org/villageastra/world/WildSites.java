package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.villageastra.VillageAstra;
import static org.villageastra.world.QuestSites.*;
/** AD-089/AD-090: four more places the village sends the player to, built the same checked way as the first four (AD-075):
 *  the raiders' hideout, a collapsed working with prospectors behind the fall, a beast's den and an unfinished signal tower. */
public final class WildSites {
 private WildSites(){}
 // ---------------------------------------------------------------- hideout: where the raiders of the last wave live
 static void hideout(ServerLevel l,Frame f,Order order,CompoundTag t){
  var side=f.sideAxis();
  // A dugout: a pit one block deep under a low log hut, its door facing the village it raids.
  for(int a=-2;a<=2;a++)for(int s=-1;s<=1;s++){carve(l,f.at(a,-1,s));if(l.getBlockState(f.at(a,-2,s)).isSolid())put(l,f,a,-2,s,Blocks.COARSE_DIRT.defaultBlockState());}
  for(int a=-3;a<=3;a++)for(int s=-2;s<=2;s++){
   if(Math.abs(a)==3||Math.abs(s)==2){if(!(a==3&&s==0))put(l,f,a,0,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));}
   put(l,f,a,1,s,Blocks.SPRUCE_PLANKS.defaultBlockState());
   if((a+s)%3==0)put(l,f,a,2,s,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true));
  }
  put(l,f,-2,-1,-1,Blocks.BROWN_CARPET.defaultBlockState());put(l,f,0,-1,-1,Blocks.BROWN_CARPET.defaultBlockState());put(l,f,1,-1,1,Blocks.BROWN_CARPET.defaultBlockState());
  put(l,f,-2,-1,1,Blocks.LANTERN.defaultBlockState());
  put(l,f,-2,-1,0,facing(Blocks.CHEST,f.facing()));
  var hoard=container(l,f.at(-2,-1,0));
  if(hoard!=null)fill(hoard,order.loot(),new ItemStack(VillageAstra.ZINDBO.get(),8),new ItemStack(Items.ARROW,12));
  t.putLong("chest",f.at(-2,-1,0).asLong());
  // Outside: the fire they cook on, the barrels of what they took on the road and the black flag of the band.
  put(l,f,-4,0,3,Blocks.CAMPFIRE.defaultBlockState());put(l,f,-4,0,2,pillar(Blocks.SPRUCE_LOG,side));put(l,f,-3,0,4,pillar(Blocks.SPRUCE_LOG,f.alongAxis()));
  put(l,f,-4,0,-3,Blocks.BARREL.defaultBlockState());put(l,f,-4,0,-4,Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,f.facing()));
  var stolen=container(l,f.at(-4,0,-3));if(stolen!=null)fill(stolen,new ItemStack(Items.BREAD,6),new ItemStack(Items.WHEAT,12));
  put(l,f,4,0,4,Blocks.SPRUCE_FENCE.defaultBlockState());put(l,f,4,1,4,Blocks.SPRUCE_FENCE.defaultBlockState());
  put(l,f,4,2,4,Blocks.BLACK_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,4));
  // The band: its leader sits in the dugout, the rest keep watch around it.
  fighter(l,EntityType.VINDICATOR,f.at(0,-1,0),f.floor(),t);
  int[][] posts={{4,-2},{-4,1},{2,4},{-1,-4},{4,2}};
  for(int i=1;i<order.fighters()&&i-1<posts.length;i++)fighter(l,i%3==0?EntityType.VINDICATOR:EntityType.PILLAGER,f.at(posts[i-1][0],0,posts[i-1][1]),f.floor(),t);
 }
 // ---------------------------------------------------------------- collapse: a working that came down on its prospectors
 static void collapse(ServerLevel l,Frame f,Order order,CompoundTag t,UUID quest){
  // The hill of the working: rock, and the loose stuff of the fall on top of it.
  for(int a=-4;a<=3;a++)for(int s=-2;s<=2;s++)for(int up=0;up<=3;up++){
   if(up==3&&(Math.abs(s)==2||a==-4||a==3))continue;
   int mix=Math.floorMod(a*7+s*3+up*5,4);
   BlockState rock=up==3?(mix==0?Blocks.MOSSY_COBBLESTONE:Blocks.GRAVEL).defaultBlockState():(mix==0?Blocks.ANDESITE:mix==1?Blocks.COBBLESTONE:Blocks.STONE).defaultBlockState();
   put(l,f,a,up,s,rock);
  }
  // The adit: a drive along the middle with timber sets, rails running out of it.
  for(int a=1;a<=3;a++)for(int up=0;up<=1;up++)put(l,f,a,up,0,Blocks.AIR.defaultBlockState());
  for(int a=-3;a<=3;a+=2){
   if(a==-1)continue;
   for(int up=0;up<=1;up++)for(int s=-1;s<=1;s+=2)put(l,f,a,up,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));
   for(int s=-1;s<=1;s++)put(l,f,a,2,s,pillar(Blocks.SPRUCE_LOG,f.sideAxis()));
  }
  for(int a=2;a<=4;a++)put(l,f,a,0,0,Blocks.RAIL.defaultBlockState());
  put(l,f,4,0,1,Blocks.LANTERN.defaultBlockState());
  // The fall: gravel filling the drive, two cells deep and two high. Every cell of it has to be dug out.
  var plug=t.getList("seal",Tag.TAG_LONG);
  for(int a=-1;a<=0;a++)for(int up=0;up<=1;up++){put(l,f,a,up,0,Blocks.GRAVEL.defaultBlockState());plug.add(LongTag.valueOf(f.at(a,up,0).asLong()));}
  t.put("seal",plug);
  // The chamber behind the fall, and the two who were working it.
  for(int a=-3;a<=-2;a++)for(int s=-1;s<=1;s++)for(int up=0;up<=1;up++)put(l,f,a,up,s,Blocks.AIR.defaultBlockState());
  put(l,f,-3,0,-1,Blocks.LANTERN.defaultBlockState());put(l,f,-2,0,1,Blocks.BARREL.defaultBlockState());
  var tools=container(l,f.at(-2,0,1));if(tools!=null)fill(tools,new ItemStack(Items.IRON_PICKAXE),new ItemStack(Items.RAW_IRON,6),new ItemStack(Items.BREAD,1));
  t.putLong("chest",f.at(-2,0,1).asLong());
  var before=t.getList("people",Tag.TAG_INT_ARRAY).size();
  people(l,f,t,quest,order.people(),false,false,new int[][]{{-2,-1},{-3,1},{-3,0}});
  var list=t.getList("people",Tag.TAG_INT_ARRAY);
  for(int i=before;i<list.size();i++)if(l.getEntity(NbtUtils.loadUUID(list.get(i))) instanceof ResidentEntity npc)npc.getPersistentData().putBoolean(TRAPPED,true);
 }
 /** True when every cell of the fall is really dug out: nothing solid is left where the gravel stood. */
 public static boolean dugOut(ServerLevel l,CompoundTag site){
  var plug=site.getList("seal",Tag.TAG_LONG);if(plug.isEmpty())return true;
  for(var raw:plug){var pos=BlockPos.of(((LongTag)raw).getAsLong());var state=l.getBlockState(pos);if(!state.isAir()&&state.isSolid())return false;}
  return true;
 }
 // ---------------------------------------------------------------- den: where the beast that takes the village's animals lies up
 static void den(ServerLevel l,Frame f,Order order,CompoundTag t){
  // A hollow in a mossy outcrop, open toward the village, with the bones of what it carried off.
  for(int a=-3;a<=1;a++)for(int s=-3;s<=3;s++)for(int up=0;up<=2;up++){
   boolean wall=a==-3||Math.abs(s)==3;if(!wall)continue;
   int mix=Math.floorMod(a*5+s*3+up*7,3);
   put(l,f,a,up,s,(mix==0?Blocks.MOSSY_COBBLESTONE:mix==1?Blocks.COBBLESTONE:Blocks.MOSSY_STONE_BRICKS).defaultBlockState());
  }
  for(int a=-3;a<=-1;a++)for(int s=-3;s<=3;s++){put(l,f,a,3,s,(Math.floorMod(a+s,2)==0?Blocks.STONE:Blocks.MOSS_BLOCK).defaultBlockState());
   if(Math.floorMod(a*3+s,4)==0)put(l,f,a,4,s,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true));}
  for(int a=-2;a<=1;a++)for(int s=-2;s<=2;s++)put(l,f,a,-1,s,(Math.floorMod(a+s,3)==0?Blocks.PODZOL:Blocks.COARSE_DIRT).defaultBlockState());
  put(l,f,-2,0,-2,pillar(Blocks.BONE_BLOCK,Direction.Axis.Y));put(l,f,-2,0,2,pillar(Blocks.BONE_BLOCK,f.sideAxis()));
  put(l,f,-2,0,0,Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,6));
  put(l,f,0,0,-2,Blocks.BROWN_WOOL.defaultBlockState());put(l,f,0,0,2,pillar(Blocks.BONE_BLOCK,f.alongAxis()));
  put(l,f,-2,1,-2,Blocks.COBWEB.defaultBlockState());put(l,f,-2,1,2,Blocks.COBWEB.defaultBlockState());
  for(int[] bush:new int[][]{{2,-2},{3,1},{2,3},{3,-3}}){if(l.getBlockState(f.at(bush[0],-1,bush[1])).isSolid())put(l,f,bush[0],-1,bush[1],Blocks.COARSE_DIRT.defaultBlockState());put(l,f,bush[0],0,bush[1],Blocks.DEAD_BUSH.defaultBlockState());}
  // The beast itself: a ravager grown old and fat on the village's herd, stronger than any of its kind.
  var beast=fighter(l,EntityType.RAVAGER,f.at(0,0,0),f.floor(),t);
  if(beast!=null){
   double health=Quests.spec(Adventures.BEAST).get("health").getAsDouble();
   var max=beast.getAttribute(Attributes.MAX_HEALTH);if(max!=null){max.setBaseValue(health);beast.setHealth((float)health);}
   beast.setCustomName(Component.translatable("quest.villageastra.beast_name"));beast.setCustomNameVisible(true);
   t.putUUID("chief",beast.getUUID());
  }
 }
 // ---------------------------------------------------------------- tower: a watchtower the village began and could not finish
 static void tower(ServerLevel l,Frame f,CompoundTag t){
  // Five courses of stone brick with an arch toward the village, a ladder inside and a plank floor on top.
  for(int a=-2;a<=2;a++)for(int s=-2;s<=2;s++){
   put(l,f,a,-1,s,Blocks.COBBLESTONE.defaultBlockState());
   boolean wall=Math.abs(a)==2||Math.abs(s)==2;
   for(int up=0;up<=4;up++){
    if(wall&&!(a==2&&s==0&&up<=1))put(l,f,a,up,s,Blocks.STONE_BRICKS.defaultBlockState());
    else if(!wall&&up==4&&!(a==-1&&s==0))put(l,f,a,up,s,Blocks.SPRUCE_PLANKS.defaultBlockState());
   }
  }
  for(int up=0;up<=4;up++)put(l,f,-1,up,0,facing(Blocks.LADDER,f.facing()));
  // Unfinished: bare poles where the parapet should stand, and a chest at the door for what it still needs.
  for(int a=-2;a<=2;a+=4)for(int s=-2;s<=2;s+=4)for(int up=5;up<=7;up++)put(l,f,a,up,s,Blocks.SPRUCE_FENCE.defaultBlockState());
  put(l,f,3,0,1,facing(Blocks.CHEST,f.facing()));
  t.putLong("chest",f.at(3,0,1).asLong());t.putLong("brazier",f.at(0,5,0).asLong());t.putBoolean("finished",false);
 }
 /** What the tower still lacks, read from its chest: stone for the parapet and fuel for the fire. */
 public static int[] towerSupplies(ServerLevel l,CompoundTag site){
  if(!(l.getBlockEntity(BlockPos.of(site.getLong("chest"))) instanceof Container chest))return new int[]{0,0};
  int stone=0,fuel=0;
  for(int i=0;i<chest.getContainerSize();i++){var s=chest.getItem(i);
   if(s.is(Items.STONE_BRICKS)||s.is(Items.COBBLESTONE))stone+=s.getCount();
   if(s.is(Items.COAL)||s.is(Items.CHARCOAL))fuel+=s.getCount();}
  return new int[]{stone,fuel};
 }
 /** Once the chest holds the stone and the fuel, the parapet goes up and the brazier stands ready — the fire is for the player to light. */
 public static boolean finishTower(ServerLevel l,CompoundTag site,int stone,int coal){
  if(site.getBoolean("finished"))return true;
  if(!(l.getBlockEntity(BlockPos.of(site.getLong("chest"))) instanceof Container chest))return false;
  var have=towerSupplies(l,site);if(have[0]<stone||have[1]<coal)return false;
  int needStone=stone,needFuel=coal;
  for(int i=0;i<chest.getContainerSize()&&(needStone>0||needFuel>0);i++){var s=chest.getItem(i);
   if(needStone>0&&(s.is(Items.STONE_BRICKS)||s.is(Items.COBBLESTONE))){int take=Math.min(needStone,s.getCount());s.shrink(take);needStone-=take;}
   else if(needFuel>0&&(s.is(Items.COAL)||s.is(Items.CHARCOAL))){int take=Math.min(needFuel,s.getCount());s.shrink(take);needFuel-=take;}}
  chest.setChanged();
  var f=frame(site);
  for(int a=-2;a<=2;a+=4)for(int s=-2;s<=2;s+=4)for(int up=5;up<=7;up++)put(l,f,a,up,s,Blocks.AIR.defaultBlockState());
  for(int a=-2;a<=2;a++)for(int s=-2;s<=2;s++){
   if(Math.abs(a)!=2&&Math.abs(s)!=2)continue;
   boolean corner=Math.abs(a)==2&&Math.abs(s)==2;
   put(l,f,a,5,s,corner?Blocks.STONE_BRICKS.defaultBlockState():Blocks.STONE_BRICK_WALL.defaultBlockState());
   if(corner)put(l,f,a,6,s,Blocks.STONE_BRICK_SLAB.defaultBlockState());
  }
  put(l,f,0,4,0,Blocks.STONE_BRICKS.defaultBlockState());
  put(l,f,0,5,0,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
  site.putBoolean("finished",true);return true;
 }
 /** True while the brazier on top of the tower is really burning. */
 public static boolean lit(ServerLevel l,CompoundTag site){
  if(!site.getBoolean("finished"))return false;var pos=BlockPos.of(site.getLong("brazier"));
  var state=l.getBlockState(pos);return state.is(Blocks.CAMPFIRE)&&state.getValue(BlockStateProperties.LIT);
 }
 /** The local frame of a site written down earlier: its floor and the side it faces. */
 static Frame frame(CompoundTag site){return new Frame(BlockPos.of(site.getLong("pos")),Direction.byName(site.getString("facing")));}
}
