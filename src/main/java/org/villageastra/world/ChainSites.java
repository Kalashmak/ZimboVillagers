package org.villageastra.world;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import java.util.UUID;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.villageastra.VillageAstra;
import static org.villageastra.world.QuestSites.*;
/** AD-099: the places at the end of the barrow's road, built the same checked way as every other site: a lost sanctuary on a broken dais
 *  whose altar keeps the tablet, and the crypt of an old king — a mausoleum over a vault where his guard still stands. */
public final class ChainSites {
 private ChainSites(){}
 // ---------------------------------------------------------------- the lost sanctuary
 static void sanctum(ServerLevel l,Frame f,Order order,CompoundTag t){
  // A paved court of old stone, worn and mossy, with a raised dais in the middle.
  for(int a=-4;a<=4;a++)for(int s=-4;s<=4;s++){
   int mix=Math.floorMod(a*3+s*5,4);
   put(l,f,a,-1,s,(mix==0?Blocks.MOSSY_STONE_BRICKS:mix==1?Blocks.CRACKED_STONE_BRICKS:Blocks.STONE_BRICKS).defaultBlockState());
   if(Math.abs(a)<=2&&Math.abs(s)<=2)put(l,f,a,0,s,Blocks.POLISHED_ANDESITE.defaultBlockState());
  }
  // Four pillars, two of them broken off, and what is left of the lintels between them.
  int[][] pillars={{-3,-3,4},{-3,3,4},{3,-3,2},{3,3,1}};
  for(var p:pillars)for(int up=0;up<=p[2];up++)put(l,f,p[0],up,p[1],(up==2?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS).defaultBlockState());
  for(int s=-3;s<=3;s++)put(l,f,-3,5,s,Blocks.STONE_BRICK_SLAB.defaultBlockState());
  put(l,f,3,0,0,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());put(l,f,2,0,3,Blocks.MOSSY_COBBLESTONE.defaultBlockState());
  for(int[] moss:new int[][]{{-2,-2},{2,-2},{-2,2},{1,2}})put(l,f,moss[0],1,moss[1],Blocks.MOSS_CARPET.defaultBlockState());
  // The altar: a carved stone, the chest of the tablet and three candles that somebody still keeps alight.
  put(l,f,-1,1,0,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
  put(l,f,0,1,0,facing(Blocks.CHEST,f.facing()));
  put(l,f,-1,2,0,Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES,3).setValue(BlockStateProperties.LIT,true));
  for(int s=-1;s<=1;s+=2)put(l,f,0,1,s,facing(Blocks.STONE_BRICK_STAIRS,f.facing().getOpposite()));
  var chest=container(l,f.at(0,1,0));
  if(chest!=null)fill(chest,order.relic(),new ItemStack(Items.GOLD_NUGGET,9),new ItemStack(VillageAstra.ZINDBO.get(),4));
  t.putLong("chest",f.at(0,1,0).asLong());
  // Its keepers: the dead the tablet would not let rest.
  int[][] spots={{2,0},{-2,-1},{0,2}};
  for(int i=0;i<order.fighters()&&i<spots.length;i++)fighter(l,i==1?EntityType.ZOMBIE:EntityType.SKELETON,f.at(spots[i][0],1,spots[i][1]),f.floor(),t);
 }
 // ---------------------------------------------------------------- the king's crypt
 static void crypt(ServerLevel l,Frame f,Order order,CompoundTag t){
  // The mausoleum: stone brick walls with a doorway toward the village, a slab roof and a shaft in its floor.
  for(int a=-2;a<=2;a++)for(int s=-2;s<=2;s++){
   put(l,f,a,-1,s,Blocks.STONE_BRICKS.defaultBlockState());
   boolean wall=Math.abs(a)==2||Math.abs(s)==2;
   if(wall)for(int up=0;up<=3;up++)if(!(a==2&&s==0&&up<=1))put(l,f,a,up,s,(up==3?Blocks.CHISELED_STONE_BRICKS:Blocks.STONE_BRICKS).defaultBlockState());
   put(l,f,a,4,s,Blocks.STONE_BRICK_SLAB.defaultBlockState());
  }
  put(l,f,-1,0,-1,Blocks.SOUL_LANTERN.defaultBlockState());put(l,f,-1,0,1,Blocks.SOUL_LANTERN.defaultBlockState());
  put(l,f,1,0,-1,Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,4));
  // The vault under it: five by five and three high, reached down a ladder against the rock.
  for(int a=-4;a<=0;a++)for(int s=-2;s<=2;s++){
   for(int up=-5;up<=-3;up++)carve(l,f.at(a,up,s));
   put(l,f,a,-6,s,(Math.floorMod(a+s,2)==0?Blocks.POLISHED_ANDESITE:Blocks.STONE_BRICKS).defaultBlockState());
  }
  for(int up=-5;up<=-1;up++){carve(l,f.at(0,up,0));put(l,f,0,up,0,facing(Blocks.LADDER,f.facing().getOpposite()));}
  if(!l.getBlockState(f.at(1,-3,0)).isSolid())put(l,f,1,-3,0,Blocks.STONE_BRICKS.defaultBlockState());
  for(int s=-2;s<=2;s+=4)for(int up=-5;up<=-3;up++)put(l,f,-2,up,s,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
  for(int s=-2;s<=2;s++)if(!l.getBlockState(f.at(-2,-2,s)).isSolid())put(l,f,-2,-2,s,Blocks.STONE_BRICKS.defaultBlockState());
  put(l,f,-2,-3,0,Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
  // The king's sarcophagus at the far end, and what was buried with him.
  put(l,f,-4,-5,0,facing(Blocks.CHEST,f.facing()));
  for(int s=-1;s<=1;s+=2)put(l,f,-4,-5,s,Blocks.STONE_BRICK_SLAB.defaultBlockState());
  var crown=new ItemStack(Items.GOLDEN_HELMET);crown.setHoverName(Component.translatable("quest.villageastra.crown"));
  var hoard=container(l,f.at(-4,-5,0));
  if(hoard!=null)fill(hoard,order.relic(),crown,new ItemStack(Items.GOLD_INGOT,8),new ItemStack(Items.EMERALD,4),new ItemStack(VillageAstra.ZINDBO.get(),12));
  t.putLong("chest",f.at(-4,-5,0).asLong());
  // His guard: a wither skeleton grown stronger than its kind, with two of the dead beside it.
  var guard=fighter(l,EntityType.WITHER_SKELETON,f.at(-3,-5,0),f.at(-2,-5,0),t);
  if(guard!=null){
   double health=Quests.setting(Chains.CRYPT,"health");
   var max=guard.getAttribute(Attributes.MAX_HEALTH);if(max!=null){max.setBaseValue(health);guard.setHealth((float)health);}
   guard.setCustomName(Component.translatable("quest.villageastra.crypt_guard"));guard.setCustomNameVisible(true);
   t.putUUID("chief",guard.getUUID());
  }
  for(int i=1;i<order.fighters();i++)fighter(l,EntityType.SKELETON,f.at(-1,-5,i%2==0?1:-1),f.at(-2,-5,0),t);
  t.putLong("vault",f.at(-2,-5,0).asLong());
 }
 // ---------------------------------------------------------------- the war camp of the band
 /** The stockade of the whole band: the same palisade, tower and tent, war banners at the gate, an empty cage — its prisoners were sold —
  *  and a warlord, an evoker grown stronger than its kind, with more of the band around him. */
 static void warcamp(ServerLevel l,Frame f,Order order,CompoundTag t,UUID quest){
  stockade(l,f,order,t,quest);
  for(int s=-2;s<=2;s+=4)put(l,f,4,0,s,Blocks.RED_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,4));
  put(l,f,-4,0,4,Blocks.RED_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,12));
  var chief=t.hasUUID("chief")?l.getEntity(t.getUUID("chief")):null;
  if(chief instanceof net.minecraft.world.entity.LivingEntity warlord){
   double health=Quests.setting(Chains.WARCAMP,"health");
   var max=warlord.getAttribute(Attributes.MAX_HEALTH);if(max!=null){max.setBaseValue(health);warlord.setHealth((float)health);}
   warlord.setCustomName(Component.translatable("quest.villageastra.warlord"));warlord.setCustomNameVisible(true);
  }
  // The rest of the band beyond the five of an ordinary stockade: by the fire and at the back wall.
  int[][] more={{-1,0},{-4,1},{3,-2}};
  for(int i=5;i<order.fighters()&&i-5<more.length;i++)fighter(l,EntityType.VINDICATOR,f.at(more[i-5][0],0,more[i-5][1]),f.floor(),t);
 }
}
