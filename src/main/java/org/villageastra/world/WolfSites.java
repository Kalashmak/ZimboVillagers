package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.villageastra.world.QuestSites.Frame;
import static org.villageastra.world.QuestSites.*;
/** AD-150: the poachers' camp the rescued wolves come out of. Cages of iron bars in a row, each with a grey inside; the catchers' fire,
 *  their skinning rack and the chest with the bones and the meat they baited the wood with. Built on a QuestSites pad like any other
 *  quest site; the wolves are real, kept from despawning and marked with the quest's id. */
public final class WolfSites {
 private WolfSites(){}
 public static final String KIND="poachers";
 public static final int HALF=6,HEIGHT=5;
 /** Where the cages stand, in the order they were built: the middle of each, where its wolf sits. */
 public static List<BlockPos> cages(CompoundTag site){
  var out=new ArrayList<BlockPos>();for(var raw:site.getList("cages",Tag.TAG_LONG))out.add(BlockPos.of(((LongTag)raw).getAsLong()));return out;
 }
 /** Whether a cage stands open: any bar of its wall broken is a way out for the wolf inside. */
 public static boolean opened(ServerLevel l,BlockPos middle){
  if(!l.hasChunkAt(middle))return false;
  for(int a=-1;a<=1;a++)for(int s=-1;s<=1;s++){if(a==0&&s==0)continue;
   for(int up=0;up<=1;up++)if(!l.getBlockState(middle.offset(a,up,s)).is(Blocks.IRON_BARS))return true;}
  return false;
 }
 /** The greys still sitting in this camp's cages: alive, of this quest, and not yet let out. */
 public static List<Wolf> caged(ServerLevel l,CompoundTag site){
  var out=new ArrayList<Wolf>();
  for(var middle:cages(site)){if(opened(l,middle))continue;
   for(var wolf:l.getEntitiesOfClass(Wolf.class,new net.minecraft.world.phys.AABB(middle).inflate(1)))if(wolf.isAlive())out.add(wolf);}
  return out;
 }
 static boolean build(ServerLevel l,Frame f,CompoundTag t,UUID quest,int wolves){
  var cages=new ListTag();
  // The cages stand in a row along the back of the camp, each on its own stone floor with a lantern over the door.
  for(int i=0;i<wolves;i++){int side=(i-(wolves-1)/2)*3;
   for(int a=-1;a<=1;a++)for(int s=-1;s<=1;s++){
    put(l,f,-4+a,-1,side+s,Blocks.STONE_BRICKS.defaultBlockState());
    boolean wall=a!=0||s!=0;
    for(int up=0;up<=1;up++)put(l,f,-4+a,up,side+s,wall?Blocks.IRON_BARS.defaultBlockState():Blocks.AIR.defaultBlockState());
    put(l,f,-4+a,2,side+s,wall?Blocks.IRON_BARS.defaultBlockState():Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF,net.minecraft.world.level.block.state.properties.Half.TOP));}
   // A lantern on a post beside the door, so the catchers can see their catch at night.
   put(l,f,-2,0,side,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));put(l,f,-2,1,side,Blocks.LANTERN.defaultBlockState());
   var middle=f.at(-4,0,side);cages.add(LongTag.valueOf(middle.asLong()));
   var wolf=AnimalSites.spawn(l,t,quest,EntityType.WOLF,middle,"grey",i==0?Component.translatable("quest.villageastra.wolf.grey"):null,false);
   if(wolf!=null){wolf.setHealth(Math.max(6,wolf.getMaxHealth()/2));wolf.getPersistentData().putUUID(WolfRescue.CAUGHT,quest);}}
  t.put("cages",cages);
  // The catchers' own corner: the fire, the rack of skins, and the chest with their bones and bait.
  put(l,f,2,-1,0,Blocks.GRAVEL.defaultBlockState());put(l,f,2,0,0,Blocks.CAMPFIRE.defaultBlockState());
  for(int up=0;up<=1;up++){put(l,f,3,up,-3,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));put(l,f,3,up,-1,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));}
  put(l,f,3,2,-3,pillar(Blocks.SPRUCE_LOG,f.sideAxis()));put(l,f,3,2,-2,pillar(Blocks.SPRUCE_LOG,f.sideAxis()));put(l,f,3,2,-1,pillar(Blocks.SPRUCE_LOG,f.sideAxis()));
  put(l,f,3,1,-2,Blocks.WHITE_WOOL.defaultBlockState());
  // Trodden ground between the cages and the fire, and the bones of what they have already taken.
  for(int a=-2;a<=3;a++)for(int d=-3;d<=3;d++)if(Math.floorMod(a*5+d*3,4)<2){var g=f.at(a,-1,d);
   if(l.getBlockState(g).is(Blocks.GRASS_BLOCK)||l.getBlockState(g).is(Blocks.DIRT))put(l,g,Blocks.COARSE_DIRT.defaultBlockState());}
  put(l,f,1,0,-3,pillar(Blocks.BONE_BLOCK,f.sideAxis()));put(l,f,2,0,-3,pillar(Blocks.BONE_BLOCK,f.sideAxis()));
  put(l,f,4,0,2,facing(Blocks.CHEST,f.facing().getOpposite()));
  var kit=container(l,f.at(4,0,2));
  if(kit!=null)fill(kit,new ItemStack(Items.BONE,6),new ItemStack(Items.MUTTON,4),new ItemStack(Items.BEEF,2),new ItemStack(Items.LEAD,2));
  for(int s=1;s<=2;s++)put(l,f,4,0,-s,Blocks.HAY_BLOCK.defaultBlockState());
  // The catchers: two with axes and the trapper himself with a crossbow, keeping to their fire.
  var home=f.at(2,0,0);int n=(int)Quests.setting(WolfRescue.CAGES,"catchers");
  for(int i=0;i<n;i++){var mob=fighter(l,i==0?EntityType.PILLAGER:EntityType.VINDICATOR,f.at(1+i%2,0,i==0?2:(i%2==0?-2:3)),home,t);
   if(mob!=null&&i==0){mob.setCustomName(Component.translatable("quest.villageastra.wolf.trapper"));mob.setCustomNameVisible(true);t.putUUID("chief",mob.getUUID());}}
  return !cages.isEmpty();
 }
}
