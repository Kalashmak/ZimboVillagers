package org.villageastra.world;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.villageastra.world.QuestSites.Frame;
import static org.villageastra.world.QuestSites.*;
/** QUEST-002 (freight): what stands at a far place the village has only heard of — the stash of a caravan that never came home.
 *  A broken cart, its crates still full, a cold fire and a torn tent, with whoever took it over living there now. The goods in the
 *  crates are the goods the village asks for, and they are really there: nothing is counted that was not carried home. */
public final class FarSites {
 private FarSites(){}
 public static final String STASH="stash";
 public static final int HALF=5,HEIGHT=5;
 /** Builds the stash: the crates hold exactly what the card asks for, and a little of the road's own gear. */
 static boolean build(ServerLevel l,Frame f,CompoundTag t,UUID quest,ItemStack goods,int guards){
  // The cart that stopped here for good: two wheels sunk in the ground, its bed broken open.
  put(l,f,0,0,-1,pillar(Blocks.STRIPPED_OAK_LOG,f.sideAxis()));put(l,f,0,0,1,pillar(Blocks.STRIPPED_OAK_LOG,f.sideAxis()));
  put(l,f,1,0,-1,Blocks.OAK_FENCE.defaultBlockState());put(l,f,1,0,1,Blocks.OAK_FENCE.defaultBlockState());
  // A barrel opens upward (its own FACING is the six-way one), so the crates stand as crates, not as a wall.
  var crate=Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP);
  put(l,f,-1,0,0,crate);put(l,f,0,0,0,crate);
  put(l,f,-1,1,0,Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF,net.minecraft.world.level.block.state.properties.Half.TOP));
  // The crates: what the village asked for, split between them, so one broken lock is not the whole load.
  int half=Math.max(1,goods.getCount()/2);
  var first=container(l,f.at(-1,0,0));if(first!=null)fill(first,new ItemStack(goods.getItem(),half));
  var second=container(l,f.at(0,0,0));if(second!=null)fill(second,new ItemStack(goods.getItem(),goods.getCount()-half),new ItemStack(net.minecraft.world.item.Items.STRING,3));
  if(first==null&&second==null)return false;
  t.putString("goods",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(goods.getItem()).toString());
  t.putInt("load",goods.getCount());
  // The camp that grew round it: a cold fire, a torn tent of two wools, a lantern on a post that still burns.
  put(l,f,2,-1,0,Blocks.GRAVEL.defaultBlockState());put(l,f,2,0,0,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
  for(int s=-1;s<=1;s++)put(l,f,3,1,s,(s==0?Blocks.BROWN_WOOL:Blocks.WHITE_WOOL).defaultBlockState());
  put(l,f,3,0,-1,Blocks.OAK_FENCE.defaultBlockState());put(l,f,3,0,1,Blocks.OAK_FENCE.defaultBlockState());
  put(l,f,-2,0,2,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));put(l,f,-2,1,2,Blocks.LANTERN.defaultBlockState());
  // Whoever holds it now: they keep to the cart and no farther.
  var home=f.at(0,0,0);
  for(int i=0;i<guards;i++){var mob=fighter(l,i==0?EntityType.PILLAGER:EntityType.HUSK,f.at(1+i%2,0,i%2==0?2:-2),home,t);
   if(mob!=null&&i==0){mob.setCustomName(Component.translatable("quest.villageastra.far.keeper"));mob.setCustomNameVisible(true);t.putUUID("chief",mob.getUUID());}}
  return true;
 }
}
