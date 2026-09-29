package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.world.QuestSites.Frame;
import org.villageastra.world.QuestSites.Order;
import static org.villageastra.world.QuestSites.*;
/** AD-140: the places the animal quests lead to, and the animals standing in them. Each is built on a QuestSites pad like any other quest
 *  site; its animals are real, kept from despawning and marked with the quest's id, and their ids are listed in the site record. */
public final class AnimalSites {
 private AnimalSites(){}
 /** The rustlers' camp with the stolen flock, a cow's sinkhole and a henhouse at the edge of a fox wood. */
 public static final String RUSTLERS="rustlers",SINKHOLE="sinkhole",HENHOUSE="henhouse",WALLOW="wallow";
 public static final List<String> KINDS=List.of(RUSTLERS,SINKHOLE,HENHOUSE,WALLOW);
 /** Entity tags: the quest an animal belongs to and its part in it (bellwether, ewe, lamb, ram; cow, calf; hen, chick). */
 public static final String TAG="AstraAnimalQuest",ROLE="AstraAnimalRole";
 static int half(String kind){return switch(kind){case RUSTLERS->6;case HENHOUSE,WALLOW->4;default->3;};}
 static int height(String kind){return switch(kind){case HENHOUSE,WALLOW->5;default->4;};}
 /** How much rock a design needs under its pad: the sinkhole goes down eight blocks. */
 static int depth(String kind){return kind.equals(SINKHOLE)?Quests.spec(AnimalQuests.SINKHOLE_T).get("depth").getAsInt()+3:0;}
 static boolean build(ServerLevel l,Frame f,Order order,CompoundTag t,UUID quest){
  t.put("animals",new ListTag());
  switch(order.kind()){
   case RUSTLERS->rustlers(l,f,t,quest);
   case SINKHOLE->sinkhole(l,f,t,quest);
   case HENHOUSE->henhouse(l,f,t,quest);
   case WALLOW->wallow(l,f,t,quest);
   default->{return false;}
  }
  return true;
 }
 /** A real animal of the quest, standing on the ground at this spot: kept, marked and listed. */
 static <T extends Animal> T spawn(ServerLevel l,CompoundTag t,UUID quest,EntityType<T> type,BlockPos at,String role,Component name,boolean baby){
  var a=type.create(l);if(a==null)return null;
  a.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,l.random.nextFloat()*360,0);
  a.finalizeSpawn(l,l.getCurrentDifficultyAt(at),net.minecraft.world.entity.MobSpawnType.STRUCTURE,null,null);
  if(baby)a.setAge(-24000);
  a.setPersistenceRequired();a.getPersistentData().putUUID(TAG,quest);a.getPersistentData().putString(ROLE,role);
  if(name!=null){a.setCustomName(name);a.setCustomNameVisible(true);}
  if(!l.addFreshEntity(a))return null;
  var list=t.getList("animals",Tag.TAG_INT_ARRAY);list.add(NbtUtils.createUUID(a.getUUID()));t.put("animals",list);
  var roles=t.getCompound("roles");roles.putString(a.getUUID().toString(),role);t.put("roles",roles);return a;
 }
 /** The ground at a spot of the design, or null where an animal could not stand (water, a drop off the pad, leaves). */
 static BlockPos ground(ServerLevel l,BlockPos near){
  // Looked for within six blocks of the place's own level, from above: a height map would find a roof, a cliff or somebody's tower overhead.
  if(!l.hasChunkAt(near))return null;
  for(int dy=6;dy>=-6;dy--){var feet=near.above(dy);var under=l.getBlockState(feet.below());
   if(!under.isSolid()||under.is(net.minecraft.tags.BlockTags.LOGS)||under.is(net.minecraft.tags.BlockTags.LEAVES)||!l.getFluidState(feet.below()).isEmpty())continue;
   if(!l.getFluidState(feet).isEmpty()||!l.getBlockState(feet).getCollisionShape(l,feet).isEmpty()||!l.getBlockState(feet.above()).getCollisionShape(l,feet.above()).isEmpty())continue;
   return feet;}
  return null;
 }
 // ---------------------------------------------------------------- the rustlers' camp: the stolen flock in a pen, the band round the fire
 private static void rustlers(ServerLevel l,Frame f,CompoundTag t,UUID quest){
  // The pen of stolen sheep at the back of the camp: a fence ring with one gate toward the village, shut and tied.
  for(int a=-6;a<=0;a++)for(int s=-3;s<=3;s++){if(a!=-6&&a!=0&&Math.abs(s)!=3)continue;if(a==0&&s==0)continue;put(l,f,a,0,s,Blocks.OAK_FENCE.defaultBlockState());}
  put(l,f,0,0,0,Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,f.facing()).setValue(BlockStateProperties.OPEN,false));
  put(l,f,-5,0,-2,pillar(Blocks.HAY_BLOCK,Direction.Axis.Y));put(l,f,-5,0,2,Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL,3));
  t.putLong("gate",f.at(0,0,0).asLong());t.putLong("pen",f.at(-3,0,0).asLong());
  // Two tents of stolen wool, the fire between them, the spoils of the road and a rack of fleeces.
  for(int[] tent:new int[][]{{3,4},{3,-4}}){int ta=tent[0],ts=tent[1];
   for(int a=ta-1;a<=ta+1;a++)for(int s=ts-1;s<=ts+1;s++){if(Math.abs(a-ta)==1&&Math.abs(s-ts)==1)for(int up=0;up<=1;up++)put(l,f,a,up,s,Blocks.SPRUCE_FENCE.defaultBlockState());
    put(l,f,a,2,s,(ts>0?Blocks.BROWN_WOOL:Blocks.WHITE_WOOL).defaultBlockState());}
   put(l,f,ta,0,ts,Blocks.BROWN_CARPET.defaultBlockState());}
  // The fire stands aside, off the way out of the pen: the flock can be driven straight out of the camp.
  put(l,f,3,-1,-2,Blocks.GRAVEL.defaultBlockState());put(l,f,3,0,-2,Blocks.CAMPFIRE.defaultBlockState());
  put(l,f,5,0,-2,facing(Blocks.CHEST,f.facing().getOpposite()));
  var spoils=container(l,f.at(5,0,-2));
  if(spoils!=null)fill(spoils,new ItemStack(Items.WHEAT,12),new ItemStack(Items.LEAD,2),new ItemStack(Items.SHEARS),new ItemStack(org.villageastra.VillageAstra.ZINDBO.get(),4));
  for(int s=1;s<=2;s++)put(l,f,5,0,s,Blocks.WHITE_WOOL.defaultBlockState());put(l,f,5,1,1,Blocks.WHITE_CARPET.defaultBlockState());
  // The flock the band drove off: the black bellwether, the ewe with her lamb and a ram, all inside the pen.
  String[] roles={"bell","ewe","lamb","ram"};int[][] spots={{-2,-1},{-4,1},{-4,2},{-2,1}};
  for(int i=0;i<roles.length;i++){var sheep=spawn(l,t,quest,EntityType.SHEEP,f.at(spots[i][0],0,spots[i][1]),roles[i],roles[i].equals("bell")?Component.translatable("quest.villageastra.animal.bellwether"):null,roles[i].equals("lamb"));
   if(sheep!=null)sheep.setColor(roles[i].equals("bell")?DyeColor.BLACK:DyeColor.WHITE);}
  // The band: two with axes and their chief with a crossbow, keeping to their camp.
  var home=f.at(2,0,0);int n=Quests.spec(AnimalQuests.FLOCK).get("fighters").getAsInt();
  for(int i=0;i<n;i++){var mob=fighter(l,i==0?EntityType.PILLAGER:EntityType.VINDICATOR,f.at(2+i%2,0,i==0?0:(i%2==0?2:-2)),home,t);
   if(mob!=null&&i==0){mob.setCustomName(Component.translatable("quest.villageastra.animal.rustler_chief"));mob.setCustomNameVisible(true);t.putUUID("chief",mob.getUUID());}}
 }
 // ---------------------------------------------------------------- the sinkhole: a cow and her calf down a sheer pit
 private static void sinkhole(ServerLevel l,Frame f,CompoundTag t,UUID quest){
  int depth=Quests.spec(AnimalQuests.SINKHOLE_T).get("depth").getAsInt();
  // Sheer walls all round (whatever the ground held: a cave, loose sand or a spring is shut out), trodden mud at the bottom: a full block,
  // so the first step a player builds is one a cow can take.
  for(int a=-3;a<=3;a++)for(int s=-3;s<=3;s++){boolean wall=Math.abs(a)==3||Math.abs(s)==3;
   for(int up=-depth-2;up<=-1;up++){int mix=Math.floorMod(a*7+s*5+up*3,5);
    var state=up==-depth-2?Blocks.STONE.defaultBlockState():up==-depth-1?(wall||mix<2?Blocks.PACKED_MUD:Blocks.COARSE_DIRT).defaultBlockState()
     :wall?(up==-1?Blocks.GRASS_BLOCK:mix==0?Blocks.COARSE_DIRT:mix==1?Blocks.STONE:mix==2?Blocks.ROOTED_DIRT:Blocks.DIRT).defaultBlockState():Blocks.AIR.defaultBlockState();
    put(l,f,a,up,s,state);}}
  // The ladder the farmer began: two rungs at the top, the rest broken away — enough for a person to look down, never for a cow.
  var ladder=Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,f.facing().getOpposite());
  put(l,f,2,-1,0,ladder);put(l,f,2,-2,0,ladder);
  put(l,f,-3,0,-3,Blocks.OAK_FENCE.defaultBlockState());put(l,f,-3,0,-2,Blocks.OAK_FENCE.defaultBlockState());put(l,f,3,0,3,pillar(Blocks.HAY_BLOCK,f.sideAxis()));
  t.putLong("rim",f.at(3,0,0).asLong());t.putLong("pit",f.at(0,-depth,0).asLong());t.putInt("depth",depth);
  spawn(l,t,quest,EntityType.COW,f.at(0,-depth,-1),"cow",Component.translatable("quest.villageastra.animal.cow"),false);
  spawn(l,t,quest,EntityType.COW,f.at(-1,-depth,1),"calf",null,true);
 }
 /** Whether the spot lies down in the pit, below the ground round it. */
 public static boolean inPit(CompoundTag site,BlockPos p){
  // The wall ring too: a cow in a hollow dug into the wall is still down there.
  var pit=BlockPos.of(site.getLong("pit"));return Math.abs(p.getX()-pit.getX())<=3&&Math.abs(p.getZ()-pit.getZ())<=3&&p.getY()<pit.getY()+site.getInt("depth");}
 /** Whether the ground under a pad lets the sinkhole be dug: nothing of anybody's, no container, no water, only earth, stone or a cave. */
 static boolean diggable(ServerLevel l,BlockPos ground,String kind){
  if(!kind.equals(SINKHOLE))return true;int depth=Quests.spec(AnimalQuests.SINKHOLE_T).get("depth").getAsInt();
  for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int dy=-depth-2;dy<=0;dy++){var p=ground.offset(dx,dy,dz);
   if(l.getBlockEntity(p)!=null||!l.getFluidState(p).isEmpty()||org.villageastra.server.OwnershipEvents.disallowedPlacement(l,p))return false;
   var s=l.getBlockState(p);if(!s.isAir()&&!groundish(s)&&!s.canBeReplaced())return false;}
  return true;
 }
 // ---------------------------------------------------------------- the henhouse: the broody hen and her warm eggs, foxes in the wood
 private static void henhouse(ServerLevel l,Frame f,CompoundTag t,UUID quest){
  // A spruce coop with a holed roof, the door toward the village; feathers on the floor, the hay nest and the nest box of eggs.
  for(int a=-3;a<=0;a++)for(int s=-2;s<=2;s++)for(int up=0;up<=2;up++){boolean wall=a==-3||a==0||Math.abs(s)==2;if(!wall)continue;
   if(a==0&&s==0&&up<=1)continue;if(Math.floorMod(a*3+s*5+up*7,6)==0&&up==2)continue;
   put(l,f,a,up,s,(Math.abs(s)==2&&(a==-3||a==0)?pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y):Blocks.SPRUCE_PLANKS.defaultBlockState()));}
  for(int a=-3;a<=0;a++)for(int s=-2;s<=2;s++)if(Math.floorMod(a*5+s*3,4)!=0)put(l,f,a,3,s,Blocks.SPRUCE_SLAB.defaultBlockState());
  put(l,f,-2,0,-1,pillar(Blocks.HAY_BLOCK,Direction.Axis.Y));put(l,f,-1,0,0,Blocks.WHITE_CARPET.defaultBlockState());
  put(l,f,-2,0,1,Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,Direction.UP));
  var box=container(l,f.at(-2,0,1));
  if(box!=null)fill(box,AnimalQuests.warmEggs(quest,Quests.spec(AnimalQuests.BROOD).get("eggs").getAsInt()));
  put(l,f,2,0,-3,Blocks.OAK_FENCE.defaultBlockState());put(l,f,2,0,3,Blocks.OAK_FENCE.defaultBlockState());put(l,f,3,0,1,Blocks.WHITE_WOOL.defaultBlockState());
  // The foxes' earth lies in the wood behind the coop; they come out of it when somebody comes for their hen.
  t.putLong("den",f.at(-11,0,2).asLong());
  spawn(l,t,quest,EntityType.CHICKEN,f.at(-1,0,-1),"hen",Component.translatable("quest.villageastra.animal.hen"),false);
 }
 // ---------------------------------------------------------------- the swineherd's farmstead: the saddled sow ran off with her litter
 private static void wallow(ServerLevel l,Frame f,CompoundTag t,UUID quest){
  // A log hut with a mossy roof, an empty sty with a mud wallow, and the swineherd's chest by the door.
  for(int a=-4;a<=-1;a++)for(int s=-3;s<=0;s++)for(int up=0;up<=2;up++){boolean wall=a==-4||a==-1||s==-3||s==0;if(!wall)continue;if(a==-1&&s==-2&&up<=1)continue;
   put(l,f,a,up,s,(Math.abs(a+2.5)==1.5&&Math.abs(s+1.5)==1.5?pillar(Blocks.OAK_LOG,Direction.Axis.Y):Blocks.OAK_PLANKS.defaultBlockState()));}
  for(int a=-5;a<=0;a++)for(int s=-4;s<=1;s++)put(l,f,a,3,s,(Math.floorMod(a+s,3)==0?Blocks.MOSS_BLOCK:Blocks.SPRUCE_SLAB).defaultBlockState());
  put(l,f,0,0,-3,facing(Blocks.CHEST,f.facing()));
  var chest=container(l,f.at(0,0,-3));
  if(chest!=null)fill(chest,new ItemStack(Items.CARROT_ON_A_STICK),new ItemStack(Items.CARROT,3));
  // The sty: a fence ring with its gate hanging open, a muddy wallow, a trough.
  for(int a=0;a<=4;a++)for(int s=1;s<=4;s++){boolean edge=a==0||a==4||s==1||s==4;if(edge&&!(a==4&&s==2))put(l,f,a,0,s,Blocks.SPRUCE_FENCE.defaultBlockState());}
  put(l,f,4,0,2,Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,f.facing()).setValue(BlockStateProperties.OPEN,true));
  for(int a=1;a<=3;a++)for(int s=2;s<=3;s++)put(l,f,a,-1,s,(Math.floorMod(a*s,2)==0?Blocks.MUD:Blocks.COARSE_DIRT).defaultBlockState());
  put(l,f,2,0,3,Blocks.COMPOSTER.defaultBlockState());
  t.putLong("sty",f.at(2,0,2).asLong());
 }
 /** The sow and her litter where they wandered off to, and the trail of rooted earth from the sty to them: laid when somebody comes for them. */
 static boolean wander(ServerLevel l,CompoundTag t,UUID quest,int distance,int piglets){
  var sty=BlockPos.of(t.getLong("sty"));BlockPos end=null;var facing=Direction.byName(t.getString("facing"));
  for(int k=0;k<8&&end==null;k++){double angle=Math.PI*2*k/8+Math.atan2(-facing.getStepZ(),-facing.getStepX());end=ground(l,sty.offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance)));}
  if(end==null)return false;
  // Rooted earth every few blocks along the way: only grass or plain dirt on the surface turns, nothing else is touched.
  int steps=Math.max(3,distance/4);
  for(int i=1;i<steps;i++){int x=sty.getX()+(end.getX()-sty.getX())*i/steps,z=sty.getZ()+(end.getZ()-sty.getZ())*i/steps;var g=ground(l,new BlockPos(x,sty.getY(),z));if(g==null)continue;
   var under=g.below();var st=l.getBlockState(under);if(st.is(Blocks.GRASS_BLOCK)||st.is(Blocks.DIRT)||st.is(Blocks.PODZOL))put(l,under,(i%2==0?Blocks.ROOTED_DIRT:Blocks.COARSE_DIRT).defaultBlockState());}
  var sow=spawn(l,t,quest,EntityType.PIG,end,"sow",Component.translatable("quest.villageastra.animal.sow"),false);if(sow==null)return false;
  sow.equipSaddle(null);AnimalQuests.stubborn(sow);
  for(int i=0;i<piglets;i++){var at=ground(l,end.offset(i%2==0?1:-1,0,i<2?1:-1));spawn(l,t,quest,EntityType.PIG,at==null?end:at,"piglet",null,true);}
  t.putLong("wandered",end.asLong());return true;
 }
 /** Hunters of the wood coming out at somebody arriving: they stand on real ground round this spot, out of the site. */
 static void ambush(ServerLevel l,CompoundTag t,UUID quest,EntityType<? extends net.minecraft.world.entity.Mob> type,BlockPos around,int count,int distance){
  var home=around;
  for(int i=0;i<count;i++){BlockPos at=null;
   for(int k=0;k<8&&at==null;k++){double angle=Math.PI*2*(i*3+k)/8;at=ground(l,around.offset((int)Math.round(Math.cos(angle)*distance),0,(int)Math.round(Math.sin(angle)*distance)));}
   if(at==null)at=ground(l,around);if(at==null)at=around;
   // Foxes sleep by day of their own accord: somebody who comes by day and creeps up leaves them asleep; at night they hunt the hen.
   fighter(l,type,at,home,t);}
 }
}
