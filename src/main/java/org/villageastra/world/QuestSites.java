package org.villageastra.world;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.LevelResource;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Resident;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.OwnershipEvents;
import org.villageastra.server.SettlementData;
/** AD-075: the places an adventure really leads to. Four designs the mod builds at a scouted spot: a timbered adit over a real
 *  seam, a bandit stockade with a barred cage, a wrecked expedition camp and a sealed barrow. Every cell is checked before it is
 *  touched, so village claims, player work and water are never overwritten and a site that does not fit is not built at all. */
public final class QuestSites {
 public static final String ADIT="adit",STOCKADE="stockade",WRECK="wreck",BARROW="barrow";
 /** AD-089/090: the raiders' hideout, a mine collapse, a beast's den and a signal tower. */
 public static final String HIDEOUT="hideout",COLLAPSE="collapse",DEN="den",TOWER="tower";
 /** AD-099: the places a chain leads to — the lost sanctuary, the king's crypt and the war camp of the band. */
 public static final String SANCTUM="sanctum",CRYPT="crypt",WARCAMP="warcamp";
 public static final String TRAPPED="AstraTrapped",QUEST="AstraQuest";
 /** Entity tags: a captive behind bars, a wounded survivor and the care already given to them. */
 public static final String CAGED="AstraCaged",INJURED="AstraInjured",CARE="AstraCare",MOB="AstraQuestSite",RELIC="AstraRelic";
 /** A site never lands on the village: the claims of its buildings are respected on top of this plain distance rail. */
 public static final int MIN_FROM_VILLAGE=24,SEARCH=28,TOLERANCE=2,GUARD_RANGE=14;
 /** How deep the shaft of an adit goes, and the least rock it needs under the pad to be worth sinking at all. */
 public static final int SHAFT=7,LEAST_SHAFT=4;
 /** What one site is made of: its design, the people and fighters standing in it and the goods it holds. */
 public record Order(String kind,int people,int fighters,int seam,Block ore,ItemStack loot,ItemStack relic){}
 private QuestSites(){}
 public static int half(String kind){if(AnimalSites.KINDS.contains(kind))return AnimalSites.half(kind);if(kind.equals(WolfSites.KIND))return WolfSites.HALF;if(kind.startsWith("far_"))return FarSites.HALF;return switch(kind){case STOCKADE,HIDEOUT,WARCAMP->5;case WRECK,BARROW,COLLAPSE,DEN,SANCTUM,CRYPT->4;default->3;};}
 public static int height(String kind){if(AnimalSites.KINDS.contains(kind))return AnimalSites.height(kind);if(kind.equals(WolfSites.KIND))return WolfSites.HEIGHT;if(kind.startsWith("far_"))return FarSites.HEIGHT;return switch(kind){case TOWER->12;case BARROW,STOCKADE,COLLAPSE,SANCTUM,WARCAMP->6;case ADIT,HIDEOUT,DEN,CRYPT->5;default->4;};}
 private static Path dir(ServerLevel l){return l.getServer().getWorldPath(LevelResource.ROOT).resolve("data/astra-quest-sites");}
 private static Path path(ServerLevel l,UUID quest){return dir(l).resolve(quest+".bin");}
 private static Path indexPath(ServerLevel l,UUID village){return dir(l).resolve("village-"+village+".bin");}
 public static CompoundTag site(ServerLevel l,UUID quest){var p=path(l,quest);return Files.exists(p)?NbtRecord.read(p):null;}
 public static void save(ServerLevel l,UUID quest,CompoundTag t){NbtRecord.write(path(l,quest),t);}
 private static CompoundTag index(ServerLevel l,UUID village){var p=indexPath(l,village);if(Files.exists(p))return NbtRecord.read(p);var t=new CompoundTag();t.putInt("schema",1);t.put("sites",new ListTag());return t;}
 /** Every site this village has ever had built for it, so a new one never lands on an old one. */
 public static List<CompoundTag> taken(ServerLevel l,UUID village){var out=new ArrayList<CompoundTag>();for(var raw:index(l,village).getList("sites",Tag.TAG_COMPOUND))out.add((CompoundTag)raw);return out;}
 private static void remember(ServerLevel l,UUID village,UUID quest,String kind,BlockPos at){
  var t=index(l,village);var list=t.getList("sites",Tag.TAG_COMPOUND);var entry=new CompoundTag();
  entry.putUUID("quest",quest);entry.putString("kind",kind);entry.putLong("pos",at.asLong());entry.putInt("half",half(kind));
  list.add(entry);t.put("sites",list);NbtRecord.write(indexPath(l,village),t);
 }
 // ---------------------------------------------------------------- ground
 /** The terrain block under the trees: leaves and trunks are looked through, water and the floors of other builds are not ground. */
 private static BlockPos terrain(ServerLevel l,int x,int z,int nearY){
  for(int y=nearY+TOLERANCE;y>=nearY-TOLERANCE;y--){var pos=new BlockPos(x,y,z);
   if(groundish(l.getBlockState(pos))&&l.getBlockState(pos).getFluidState().isEmpty())return pos;}
  return null;
 }
 static boolean groundish(BlockState s){
  return s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)||s.is(BlockTags.TERRACOTTA)
   ||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.PACKED_ICE)||s.is(Blocks.SANDSTONE)
   ||s.is(BlockTags.COAL_ORES)||s.is(BlockTags.IRON_ORES)||s.is(BlockTags.COPPER_ORES)||s.is(BlockTags.GOLD_ORES)
   ||s.is(BlockTags.REDSTONE_ORES)||s.is(BlockTags.LAPIS_ORES)||s.is(BlockTags.DIAMOND_ORES)||s.is(BlockTags.EMERALD_ORES);
 }
 /** A cell the site may clear: air, grass and snow, a tree, or plain terrain that has to come down. Never water, never a build. */
 static boolean clearable(ServerLevel l,BlockPos p){
  if(l.getBlockEntity(p)!=null)return false;var s=l.getBlockState(p);
  if(!s.getFluidState().isEmpty())return false;
  return s.isAir()||s.canBeReplaced()||groundish(s)||s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.is(BlockTags.SAPLINGS)
   ||s.is(BlockTags.FLOWERS)||s.is(BlockTags.CROPS)||s.is(Blocks.VINE)||s.is(Blocks.BAMBOO)||s.is(Blocks.CACTUS)
   ||s.is(Blocks.SWEET_BERRY_BUSH)||s.is(Blocks.MOSS_CARPET)||s.is(Blocks.SNOW);
 }
 /** Checks, and with {@code apply} also cuts and fills, the flat pad one design stands on. */
 private static boolean pad(ServerLevel l,BlockPos base,int half,int height,boolean apply){
  for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++){
   var column=new BlockPos(base.getX()+dx,base.getY(),base.getZ()+dz);
   if(!l.hasChunkAt(column))return false;
   var ground=terrain(l,column.getX(),column.getZ(),base.getY());if(ground==null)return false;
   for(int y=Math.min(ground.getY(),base.getY());y<=base.getY()+height;y++){
    var pos=new BlockPos(column.getX(),y,column.getZ());
    if(OwnershipEvents.disallowedPlacement(l,pos))return false;
    if(y>base.getY()&&!clearable(l,pos))return false;
   }
   if(!apply)continue;
   var surface=l.getBlockState(ground);
   for(int y=ground.getY()+1;y<base.getY();y++)l.setBlock(new BlockPos(column.getX(),y,column.getZ()),Blocks.DIRT.defaultBlockState(),2);
   if(ground.getY()<base.getY())l.setBlock(new BlockPos(column.getX(),base.getY(),column.getZ()),surface,2);
   for(int y=base.getY()+1;y<=base.getY()+height;y++)l.setBlock(new BlockPos(column.getX(),y,column.getZ()),Blocks.AIR.defaultBlockState(),2);
  }
  if(apply)apron(l,base,half);
  return true;
 }
 /** A pad must not leave a wall around itself: two rings of single steps let people and animals walk off the site again — down to
  *  ground lower than the pad, or up to ground the pad was cut into (a ring stepping down there would dig a moat round the site). */
 private static void apron(ServerLevel l,BlockPos base,int half){
  for(int ring=1;ring<=2;ring++){
   for(int dx=-half-ring;dx<=half+ring;dx++)for(int dz=-half-ring;dz<=half+ring;dz++){
    if(Math.max(Math.abs(dx),Math.abs(dz))!=half+ring)continue;
    var column=new BlockPos(base.getX()+dx,base.getY(),base.getZ()+dz);
    if(!l.hasChunkAt(column)||OwnershipEvents.disallowedPlacement(l,column))continue;
    var ground=terrain(l,column.getX(),column.getZ(),base.getY());
    if(ground==null||ground.getY()==base.getY())continue;
    if(ground.getY()>base.getY()){
     // Cut into a slope: this ring stands a step higher than the one inside it; the earth above that step is taken away.
     int top=base.getY()+ring;if(ground.getY()<=top)continue;
     boolean free=true;
     for(int y=top+1;y<=ground.getY()+3&&free;y++)free=clearable(l,new BlockPos(column.getX(),y,column.getZ()));
     if(!free)continue;
     var surface=l.getBlockState(ground);
     for(int y=top+1;y<=ground.getY()+3;y++)l.setBlock(new BlockPos(column.getX(),y,column.getZ()),Blocks.AIR.defaultBlockState(),2);
     l.setBlock(new BlockPos(column.getX(),top,column.getZ()),surface,2);
     continue;}
    // Raised above lower ground: this ring stands a step lower than the one inside it, filled up to that step.
    int step=base.getY()-ring;var low=terrain(l,column.getX(),column.getZ(),step);
    if(low==null||low.getY()>=step)continue;
    boolean free=true;
    for(int y=low.getY()+1;y<=step&&free;y++)free=clearable(l,new BlockPos(column.getX(),y,column.getZ()));
    if(!free)continue;
    var surface=l.getBlockState(low);
    for(int y=low.getY()+1;y<step;y++)l.setBlock(new BlockPos(column.getX(),y,column.getZ()),Blocks.DIRT.defaultBlockState(),2);
    l.setBlock(new BlockPos(column.getX(),step,column.getZ()),surface,2);
    boolean open=true;
    for(int y=step+1;y<=step+3&&open;y++)open=clearable(l,new BlockPos(column.getX(),y,column.getZ()));
    if(open)for(int y=step+1;y<=step+3;y++)l.setBlock(new BlockPos(column.getX(),y,column.getZ()),Blocks.AIR.defaultBlockState(),2);
   }
  }
 }
 /** Looks outward from the scouted lead for a pad this design fits on: far enough from the village and from every earlier site. */
 public static BlockPos place(ServerLevel l,SettlementData.Entry e,BlockPos anchor,String kind){
  int half=half(kind),height=height(kind);var earlier=taken(l,e.settlement().id());
  for(int radius=0;radius<=SEARCH;radius+=half+2)for(int step=0;step<8;step++){
   if(radius==0&&step>0)break;
   double angle=Math.PI*2*step/8;
   int x=anchor.getX()+(int)Math.round(Math.cos(angle)*radius),z=anchor.getZ()+(int)Math.round(Math.sin(angle)*radius);
   if(!l.hasChunkAt(new BlockPos(x,0,z)))continue;
   var ground=terrain(l,x,z,anchor.getY());if(ground==null)continue;
   if(ground.distSqr(e.center())<(long)MIN_FROM_VILLAGE*MIN_FROM_VILLAGE)continue;
   // A mine needs rock under it: shallow ground over the floor of the world carries no seam.
   if(kind.equals(ADIT)&&ground.getY()-LEAST_SHAFT-2<l.getMinBuildHeight())continue;
   // The floor of the king's vault lies five blocks under the ground: it needs that much rock over the floor of the world.
   if(kind.equals(CRYPT)&&ground.getY()-7<l.getMinBuildHeight())continue;
   // AD-140: the cow's sinkhole goes down into the ground under its pad.
   if(AnimalSites.depth(kind)>0&&(ground.getY()-AnimalSites.depth(kind)<l.getMinBuildHeight()||!AnimalSites.diggable(l,ground,kind)))continue;
   boolean clash=false;
   for(var old:earlier){var at=BlockPos.of(old.getLong("pos"));int gap=half+old.getInt("half")+4;
    if(Math.abs(at.getX()-x)<gap&&Math.abs(at.getZ()-z)<gap)clash=true;}
   if(clash||!pad(l,ground,half,height,false))continue;
   return ground;
  }
  return null;
 }
 // ---------------------------------------------------------------- building
 record Frame(BlockPos floor,Direction facing){
  /** Local design coordinates: {@code along} runs toward the village, {@code side} to its right, {@code up} from the floor. */
  BlockPos at(int along,int up,int side){var r=facing.getClockWise();
   return floor.offset(facing.getStepX()*along+r.getStepX()*side,up,facing.getStepZ()*along+r.getStepZ()*side);}
  Direction.Axis alongAxis(){return facing.getAxis();}
  Direction.Axis sideAxis(){return facing.getClockWise().getAxis();}
 }
 private static Direction toward(BlockPos from,BlockPos to){
  int dx=to.getX()-from.getX(),dz=to.getZ()-from.getZ();
  return Math.abs(dx)>=Math.abs(dz)?(dx>=0?Direction.EAST:Direction.WEST):(dz>=0?Direction.SOUTH:Direction.NORTH);
 }
 static void put(ServerLevel l,BlockPos pos,BlockState state){if(!OwnershipEvents.disallowedPlacement(l,pos))l.setBlock(pos,state,3);}
 static void put(ServerLevel l,Frame f,int along,int up,int side,BlockState state){put(l,f.at(along,up,side),state);}
 static BlockState pillar(Block block,Direction.Axis axis){return block.defaultBlockState().setValue(BlockStateProperties.AXIS,axis);}
 static BlockState facing(Block block,Direction direction){return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,direction);}
 static void fill(Container container,ItemStack... stacks){
  int slot=0;
  for(var stack:stacks){
   if(stack==null||stack.isEmpty())continue;
   while(slot<container.getContainerSize()&&!container.getItem(slot).isEmpty())slot++;
   if(slot>=container.getContainerSize())return;
   container.setItem(slot++,stack.copy());
  }
 }
 static Container container(ServerLevel l,BlockPos pos){return l.getBlockEntity(pos) instanceof Container c?c:null;}
 /** Builds the whole site at the scouted lead, or returns null when the world there does not allow it. */
 public static CompoundTag build(ServerLevel l,SettlementData.Entry e,UUID quest,Order order,BlockPos anchor,long now){
  var village=e.settlement().id();var ground=place(l,e,anchor,order.kind());if(ground==null)return null;
  if(!pad(l,ground,half(order.kind()),height(order.kind()),true))return null;
  var frame=new Frame(ground.above(),toward(ground,e.center()));
  var t=new CompoundTag();t.putInt("schema",1);t.putUUID("id",UUID.randomUUID());t.putUUID("village",village);t.putUUID("quest",quest);
  t.putString("kind",order.kind());t.putLong("pos",ground.above().asLong());t.putLong("anchor",anchor.asLong());t.putString("facing",frame.facing().getName());
  t.put("cage",new ListTag());t.put("seal",new ListTag());t.put("seam",new ListTag());t.put("mobs",new ListTag());t.put("people",new ListTag());
  t.putLong("built",now);t.putString("state","open");
  switch(order.kind()){
   case ADIT->adit(l,frame,order,t);
   case STOCKADE->stockade(l,frame,order,t,quest);
   case WRECK->wreck(l,frame,order,t,quest);
   case BARROW->barrow(l,frame,order,t);
   case HIDEOUT->WildSites.hideout(l,frame,order,t);
   case COLLAPSE->WildSites.collapse(l,frame,order,t,quest);
   case DEN->WildSites.den(l,frame,order,t);
   case TOWER->WildSites.tower(l,frame,t);
   case SANCTUM->ChainSites.sanctum(l,frame,order,t);
   case CRYPT->ChainSites.crypt(l,frame,order,t);
   case WARCAMP->ChainSites.warcamp(l,frame,order,t,quest);
   case AnimalSites.RUSTLERS,AnimalSites.SINKHOLE,AnimalSites.HENHOUSE,AnimalSites.WALLOW->AnimalSites.build(l,frame,order,t,quest);
   // AD-150: the poachers' camp keeps its greys in cages; how many is the card's own count, kept on the order's people.
   case WolfSites.KIND->{if(!WolfSites.build(l,frame,t,quest,Math.max(1,order.people())))return null;}
   // QUEST-002: the stash of a caravan that never came home, its crates holding the load the card asks for.
   case FarSites.STASH->{if(!FarSites.build(l,frame,t,quest,order.loot(),order.people()))return null;}
   default->{return null;}
  }
  save(l,quest,t);remember(l,village,quest,order.kind(),ground.above());return t;
 }
 static void people(ServerLevel l,Frame f,CompoundTag t,UUID quest,int count,boolean injured,boolean caged,int[][] spots){
  var list=t.getList("people",Tag.TAG_INT_ARRAY);
  for(int i=0;i<count&&i<spots.length;i++){
   var npc=VillageAstra.RESIDENT.get().create(l);if(npc==null)return;
   var resident=new Resident(UUID.randomUUID(),Resident.Life.ADULT,true,null,null,-1);npc.setUUID(resident.id());npc.bind(null,resident);
   var data=npc.getPersistentData();data.putUUID(Camps.QUEST_TAG,quest);data.putBoolean("AstraEducated",true);
   if(injured){data.putBoolean(INJURED,true);data.putInt(CARE,0);npc.setNoAi(true);}
   if(caged)data.putBoolean(CAGED,true);
   npc.setPersistenceRequired();var at=f.at(spots[i][0],0,spots[i][1]);
   npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,f.facing().toYRot(),0);
   if(injured)npc.setHealth(6);
   if(l.addFreshEntity(npc))list.add(NbtUtils.createUUID(npc.getUUID()));
  }
  t.put("people",list);
 }
 static Mob fighter(ServerLevel l,EntityType<? extends Mob> type,BlockPos at,BlockPos home,CompoundTag t){
  var mob=type.create(l);if(mob==null)return null;
  mob.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
  mob.finalizeSpawn(l,l.getCurrentDifficultyAt(at),MobSpawnType.STRUCTURE,null,null);
  mob.setPersistenceRequired();mob.addTag(MOB);mob.restrictTo(home,GUARD_RANGE);
  if(t.hasUUID("quest"))mob.getPersistentData().putUUID(QUEST,t.getUUID("quest"));
  if(!l.addFreshEntity(mob))return null;
  var list=t.getList("mobs",Tag.TAG_INT_ARRAY);list.add(NbtUtils.createUUID(mob.getUUID()));t.put("mobs",list);return mob;
 }
 // ---------------------------------------------------------------- adit: a timbered shaft over a real seam
 private static void adit(ServerLevel l,Frame f,Order order,CompoundTag t){
  var side=f.sideAxis();var along=f.alongAxis();
  // The shaft goes as deep as the rock allows and never past the floor of the world.
  int depth=Math.max(LEAST_SHAFT,Math.min(SHAFT,f.floor().getY()-l.getMinBuildHeight()-2));
  // The mine yard: trodden cobble around the mouth and a spoil heap beside it.
  for(int a=-1;a<=1;a++)for(int s=-1;s<=2;s++)if(!(a==0&&(s==0||s==1)))put(l,f,a,-1,s,Blocks.COBBLESTONE.defaultBlockState());
  for(int a=2;a<=3;a++)for(int s=-1;s<=0;s++){put(l,f,a,0,s,Blocks.GRAVEL.defaultBlockState());if(a==3&&s==0)put(l,f,a,1,s,Blocks.COBBLESTONE.defaultBlockState());}
  // Headframe: four posts, top beams and the hoist chain over the second shaft cell.
  for(int a=-1;a<=1;a+=2)for(int s=-1;s<=2;s+=3)for(int up=0;up<=3;up++)put(l,f,a,up,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));
  for(int a=-1;a<=1;a+=2)for(int s=-1;s<=2;s++)put(l,f,a,4,s,pillar(Blocks.SPRUCE_LOG,side));
  for(int a=-1;a<=1;a++)put(l,f,a,4,1,pillar(Blocks.SPRUCE_LOG,along));
  put(l,f,0,3,1,Blocks.CHAIN.defaultBlockState());put(l,f,0,2,1,Blocks.CHAIN.defaultBlockState());
  put(l,f,1,3,1,Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
  // Lean-to with the prospector kit, and a fire to come back to.
  for(int a=-1;a<=1;a++)for(int s=-3;s<=-2;s++)put(l,f,a,-1,s,Blocks.SPRUCE_PLANKS.defaultBlockState());
  for(int a=-1;a<=1;a++){put(l,f,a,0,-3,Blocks.SPRUCE_PLANKS.defaultBlockState());put(l,f,a,1,-3,Blocks.SPRUCE_PLANKS.defaultBlockState());
   put(l,f,a,2,-3,facing(Blocks.SPRUCE_STAIRS,f.facing().getCounterClockWise()));put(l,f,a,1,-2,facing(Blocks.SPRUCE_STAIRS,f.facing().getCounterClockWise()));}
  put(l,f,-1,0,-2,Blocks.BARREL.defaultBlockState());
  var kit=container(l,f.at(-1,0,-2));
  if(kit!=null)fill(kit,new ItemStack(Items.STONE_PICKAXE),new ItemStack(Items.TORCH,8),new ItemStack(Items.BREAD,2));
  put(l,f,2,0,2,Blocks.CAMPFIRE.defaultBlockState());put(l,f,1,0,2,pillar(Blocks.SPRUCE_LOG,along));put(l,f,3,0,2,pillar(Blocks.SPRUCE_LOG,along));
  // The shaft itself: two cells down to the chamber, a ladder on the rock wall and light at the bottom.
  for(int d=0;d<=depth;d++)for(int s=0;s<=1;s++)carve(l,f.at(0,-d,s));
  for(int d=1;d<=depth;d++)put(l,f,0,-d,0,facing(Blocks.LADDER,f.facing()));
  // The chamber at the bottom and the seam exposed in its walls: real ore for a real pickaxe.
  // The rock column behind the ladder is left standing, as a real working does: without it the bottom rungs have nothing to hold on to.
  for(int a=-1;a<=1;a++)for(int s=-1;s<=2;s++)for(int d=depth-1;d<=depth;d++)if(!(a==0&&(s==0||s==1))&&!(a==-1&&s==0))carve(l,f.at(a,-d,s));
  put(l,f,1,-depth,1,Blocks.LANTERN.defaultBlockState());
  put(l,f,0,-3,1,facing(Blocks.WALL_TORCH,f.facing().getOpposite()));
  var seam=t.getList("seam",Tag.TAG_LONG);int placed=0;
  outer:
  for(int d=depth;d>=depth-1;d--)for(int a=-2;a<=2;a++)for(int s=-2;s<=3;s++){
   if(placed>=order.seam())break outer;
   if(Math.abs(a)!=2&&s!=-2&&s!=3)continue;
   var pos=f.at(a,-d,s);if(OwnershipEvents.disallowedPlacement(l,pos))continue;
   var state=l.getBlockState(pos);if(!groundish(state)&&!state.isAir())continue;
   put(l,pos,order.ore().defaultBlockState());seam.add(LongTag.valueOf(pos.asLong()));placed++;
  }
  t.put("seam",seam);t.putLong("chest",f.at(-1,0,-2).asLong());
 }
 /** Opens one cell of the shaft, but only where the mod is allowed to dig: rock, soil and air, never a build or a fluid. */
 static void carve(ServerLevel l,BlockPos pos){
  if(OwnershipEvents.disallowedPlacement(l,pos)||l.getBlockEntity(pos)!=null)return;
  var state=l.getBlockState(pos);
  if(state.isAir()||!state.getFluidState().isEmpty()&&!groundish(state))return;
  if(!groundish(state)&&!state.canBeReplaced()&&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES))return;
  l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
 }
 // ---------------------------------------------------------------- stockade: bandits, spoils and a barred cage
 static void stockade(ServerLevel l,Frame f,Order order,CompoundTag t,UUID quest){
  var side=f.sideAxis();
  // Palisade of sharpened logs with one gate on the side the village lies.
  for(int a=-5;a<=5;a++)for(int s=-5;s<=5;s++){
   if(Math.abs(a)!=5&&Math.abs(s)!=5)continue;
   if(a==5&&Math.abs(s)<=1)continue;
   put(l,f,a,0,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));put(l,f,a,1,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));
   put(l,f,a,2,s,Blocks.SPRUCE_FENCE.defaultBlockState());
  }
  for(int s=-1;s<=1;s++){put(l,f,5,0,s,facing(Blocks.SPRUCE_FENCE_GATE,f.facing()).setValue(BlockStateProperties.OPEN,true));put(l,f,5,2,s,pillar(Blocks.SPRUCE_LOG,side));}
  for(int s=-2;s<=2;s+=4)put(l,f,5,3,s,Blocks.LANTERN.defaultBlockState());
  // Watchtower in the far corner: ladder, platform, railing and the black flag of the band.
  for(int a=-4;a<=-2;a+=2)for(int s=-4;s<=-2;s+=2)for(int up=0;up<=3;up++)put(l,f,a,up,s,pillar(Blocks.SPRUCE_LOG,Direction.Axis.Y));
  for(int a=-4;a<=-2;a++)for(int s=-4;s<=-2;s++)put(l,f,a,4,s,Blocks.SPRUCE_PLANKS.defaultBlockState());
  for(int a=-4;a<=-2;a++)for(int s=-4;s<=-2;s++)if(Math.abs(a+3)==1||Math.abs(s+3)==1)put(l,f,a,5,s,Blocks.SPRUCE_FENCE.defaultBlockState());
  for(int up=0;up<=3;up++)put(l,f,-2,up,-3,Blocks.SPRUCE_PLANKS.defaultBlockState());
  for(int up=0;up<=4;up++)put(l,f,-1,up,-3,facing(Blocks.LADDER,f.facing()));
  put(l,f,-3,5,-3,Blocks.BLACK_BANNER.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,8));
  // The tent of the chief with its straw bed and the barrel of spoils taken from the road.
  for(int a=1;a<=3;a+=2)for(int s=1;s<=3;s+=2)for(int up=0;up<=1;up++)put(l,f,a,up,s,Blocks.SPRUCE_FENCE.defaultBlockState());
  for(int a=1;a<=3;a++)for(int s=1;s<=3;s++)put(l,f,a,2,s,Blocks.BLACK_WOOL.defaultBlockState());
  for(int a=1;a<=3;a++)put(l,f,a,1,3,Blocks.GRAY_WOOL.defaultBlockState());
  put(l,f,2,0,2,Blocks.HAY_BLOCK.defaultBlockState());put(l,f,1,0,2,Blocks.BROWN_CARPET.defaultBlockState());
  put(l,f,3,0,1,Blocks.BARREL.defaultBlockState());
  var spoils=container(l,f.at(3,0,1));
  if(spoils!=null)fill(spoils,order.loot(),new ItemStack(VillageAstra.ZINDBO.get(),6),new ItemStack(Items.BREAD,4));
  t.putLong("chest",f.at(3,0,1).asLong());
  // Fire in the middle of the yard and a stake with a skull at the back wall.
  put(l,f,0,-1,1,Blocks.GRAVEL.defaultBlockState());put(l,f,0,0,1,Blocks.CAMPFIRE.defaultBlockState());
  put(l,f,4,0,4,Blocks.SPRUCE_FENCE.defaultBlockState());put(l,f,4,1,4,Blocks.SPRUCE_FENCE.defaultBlockState());
  put(l,f,4,2,4,Blocks.SKELETON_SKULL.defaultBlockState().setValue(BlockStateProperties.ROTATION_16,4));
  // The cage: iron bars on a cobble floor, roofed over, with its columns written down so a broken wall really counts.
  // The cage stands clear of the tower and the tent: every wall of it opens onto the yard, so a broken side is a way out.
  var cage=t.getList("cage",Tag.TAG_LONG);
  for(int a=0;a<=2;a++)for(int s=-3;s<=-1;s++){
   put(l,f,a,-1,s,Blocks.COBBLESTONE.defaultBlockState());put(l,f,a,2,s,Blocks.IRON_BARS.defaultBlockState());
   if(a!=0&&a!=2&&s!=-3&&s!=-1)continue;
   put(l,f,a,0,s,Blocks.IRON_BARS.defaultBlockState());put(l,f,a,1,s,Blocks.IRON_BARS.defaultBlockState());
   if(a==1||s==-2)cage.add(LongTag.valueOf(f.at(a,0,s).asLong()));
  }
  t.put("cage",cage);
  people(l,f,t,quest,order.people(),false,true,new int[][]{{1,-2}});
  // The band itself: the chief in his tent, one lookout on the tower and the rest in the yard.
  var chief=fighter(l,order.kind().equals(WARCAMP)?EntityType.EVOKER:EntityType.VINDICATOR,f.at(2,0,3),f.floor(),t);
  if(chief!=null)t.putUUID("chief",chief.getUUID());
  int[][] posts={{-3,-3},{3,-4},{-3,3},{1,4}};
  for(int i=1;i<order.fighters()&&i-1<posts.length;i++){
   var spot=i==1?f.at(posts[0][0],5,posts[0][1]):f.at(posts[i-1][0],0,posts[i-1][1]);
   fighter(l,EntityType.PILLAGER,spot,f.floor(),t);
  }
 }
 // ---------------------------------------------------------------- wreck: an expedition that never came back
 private static void wreck(ServerLevel l,Frame f,Order order,CompoundTag t,UUID quest){
  var along=f.alongAxis();var side=f.sideAxis();
  // The fire went out and the cairn beside it still carries its lantern.
  put(l,f,0,0,0,Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false));
  for(int a=-1;a<=1;a++)for(int s=-1;s<=1;s++)if(a!=0||s!=0)put(l,f,a,-1,s,Blocks.COBBLESTONE.defaultBlockState());
  put(l,f,-3,0,0,Blocks.COBBLESTONE.defaultBlockState());put(l,f,-3,1,0,Blocks.MOSSY_COBBLESTONE.defaultBlockState());
  put(l,f,-3,2,0,Blocks.COBBLESTONE_WALL.defaultBlockState());put(l,f,-3,3,0,Blocks.LANTERN.defaultBlockState());
  // One tent still stands; the other came down and the cobwebs have had time to grow over it.
  for(int a=1;a<=3;a+=2)for(int s=-3;s<=-1;s+=2)for(int up=0;up<=1;up++)put(l,f,a,up,s,Blocks.SPRUCE_FENCE.defaultBlockState());
  for(int a=1;a<=3;a++)for(int s=-3;s<=-1;s++)put(l,f,a,2,s,Blocks.WHITE_WOOL.defaultBlockState());
  for(int a=1;a<=3;a++)put(l,f,a,1,-3,Blocks.BROWN_WOOL.defaultBlockState());
  int[][] fallen={{1,1},{2,1},{3,2},{2,3},{1,3}};
  for(var cell:fallen)put(l,f,cell[0],0,cell[1],(cell[0]+cell[1])%2==0?Blocks.WHITE_WOOL.defaultBlockState():Blocks.BROWN_WOOL.defaultBlockState());
  put(l,f,2,1,2,Blocks.COBWEB.defaultBlockState());put(l,f,3,0,3,Blocks.COBWEB.defaultBlockState());
  put(l,f,3,1,1,pillar(Blocks.SPRUCE_LOG,side));
  // The cart that broke an axle on the way out.
  for(int s=2;s<=3;s++){put(l,f,-2,0,s,pillar(Blocks.SPRUCE_LOG,side));put(l,f,-3,0,s,Blocks.SPRUCE_PLANKS.defaultBlockState());put(l,f,-1,0,s,Blocks.SPRUCE_FENCE.defaultBlockState());}
  put(l,f,-3,1,3,Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,f.facing()));
  // Their crates: field notes, the last of the rations and the bandages nobody could reach.
  put(l,f,2,0,0,Blocks.BARREL.defaultBlockState());put(l,f,-2,0,-2,Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING,f.facing()));
  var notes=container(l,f.at(2,0,0));
  if(notes!=null)fill(notes,new ItemStack(Items.PAPER,3),new ItemStack(Items.COMPASS),new ItemStack(VillageAstra.BANDAGE.get(),2),order.loot());
  var rations=container(l,f.at(-2,0,-2));
  if(rations!=null)fill(rations,new ItemStack(Items.BREAD,3),new ItemStack(VillageAstra.ZINDBO.get(),2));
  t.putLong("chest",f.at(2,0,0).asLong());
  // The survivors lie by the cairn and cannot walk until somebody treats them.
  people(l,f,t,quest,order.people(),true,false,new int[][]{{-2,-1},{-2,1},{-1,-2},{-1,2}});
  int[][] ambush={{4,-1},{-1,4}};
  for(int i=0;i<order.fighters()&&i<ambush.length;i++)
   fighter(l,i%2==0?EntityType.ZOMBIE:EntityType.SKELETON,f.at(ambush[i][0],0,ambush[i][1]),f.floor(),t);
  put(l,f,0,0,-2,pillar(Blocks.SPRUCE_LOG,along));
 }
 // ---------------------------------------------------------------- barrow: a sealed chamber and one ancient volume
 private static void barrow(ServerLevel l,Frame f,Order order,CompoundTag t){
  // Standing stones around the mound.
  for(int d=-4;d<=4;d+=8)for(int up=0;up<=1;up++){put(l,f,d,up,0,Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());put(l,f,0,up,d,Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());}
  // The mound itself, solid except for the chamber and the passage into it.
  for(int a=-3;a<=3;a++)for(int s=-3;s<=3;s++){
   boolean chamber=Math.abs(a)<=1&&Math.abs(s)<=1,passage=s==0&&a>=2;
   if(chamber||passage)continue;
   put(l,f,a,0,s,Blocks.MOSSY_COBBLESTONE.defaultBlockState());put(l,f,a,1,s,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
  }
  for(int a=-2;a<=2;a++)for(int s=-2;s<=2;s++)put(l,f,a,2,s,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
  for(int a=-1;a<=1;a++)for(int s=-1;s<=1;s++)put(l,f,a,3,s,Blocks.STONE_BRICKS.defaultBlockState());
  put(l,f,0,4,0,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());put(l,f,0,5,0,Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState());
  put(l,f,3,2,0,Blocks.STONE_BRICKS.defaultBlockState());
  // The chamber: a stone floor, corner pillars, the coffin chest and the eye of a soul lantern above it.
  for(int a=-1;a<=1;a++)for(int s=-1;s<=1;s++){
   put(l,f,a,-1,s,Blocks.POLISHED_ANDESITE.defaultBlockState());
   if(Math.abs(a)==1&&Math.abs(s)==1){put(l,f,a,0,s,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());put(l,f,a,1,s,Blocks.CHISELED_STONE_BRICKS.defaultBlockState());}
  }
  put(l,f,0,0,0,facing(Blocks.CHEST,f.facing()));
  put(l,f,0,1,0,Blocks.SOUL_LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true));
  put(l,f,-1,0,0,Blocks.STONE_BRICK_SLAB.defaultBlockState());
  put(l,f,-1,1,0,facing(Blocks.SKELETON_WALL_SKULL,f.facing()));
  put(l,f,0,1,1,Blocks.COBWEB.defaultBlockState());put(l,f,0,1,-1,Blocks.COBWEB.defaultBlockState());
  var chest=container(l,f.at(0,0,0));
  if(chest!=null)fill(chest,order.relic(),new ItemStack(Items.BONE,3),new ItemStack(VillageAstra.ZINDBO.get(),4));
  t.putLong("chest",f.at(0,0,0).asLong());
  // The passage is walled up: two cracked bricks are all that stands between the player and the chamber.
  var seal=t.getList("seal",Tag.TAG_LONG);
  for(int up=0;up<=1;up++){put(l,f,3,up,0,Blocks.CRACKED_STONE_BRICKS.defaultBlockState());seal.add(LongTag.valueOf(f.at(3,up,0).asLong()));}
  t.put("seal",seal);
  for(int up=0;up<=1;up++)for(int s=-1;s<=1;s+=2)put(l,f,4,up,s,Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
  put(l,f,4,2,0,facing(Blocks.STONE_BRICK_STAIRS,f.facing().getOpposite()));
  // The guards of the barrow wait in the dark and never see the sun until somebody breaks in.
  int[][] spots={{1,0},{0,1},{0,-1}};
  for(int i=0;i<order.fighters()&&i<spots.length;i++)
   fighter(l,i==2?EntityType.ZOMBIE:EntityType.SKELETON,f.at(spots[i][0],0,spots[i][1]),f.floor(),t);
 }
 // ---------------------------------------------------------------- what the world says about a site now
 /** True when a whole column of cage bars is really gone: a person can walk out through that gap. */
 public static boolean cageOpened(ServerLevel l,CompoundTag site){
  for(var raw:site.getList("cage",Tag.TAG_LONG)){var pos=BlockPos.of(((LongTag)raw).getAsLong());
   if(!l.getBlockState(pos).is(Blocks.IRON_BARS)&&!l.getBlockState(pos.above()).is(Blocks.IRON_BARS))return true;}
  return false;
 }
 /** True when the barrow has really been broken into. */
 public static boolean sealBroken(ServerLevel l,CompoundTag site){
  for(var raw:site.getList("seal",Tag.TAG_LONG))if(!l.getBlockState(BlockPos.of(((LongTag)raw).getAsLong())).is(Blocks.CRACKED_STONE_BRICKS))return true;
  return false;
 }
 /** The bandit chief: while he is on his feet nobody in his stockade dares to leave. */
 public static boolean chiefDown(ServerLevel l,CompoundTag site){
  if(!site.hasUUID("chief"))return true;var entity=l.getEntity(site.getUUID("chief"));return entity==null||!entity.isAlive();
 }
 /** Ore blocks of the seam still in the wall. */
 public static int seamLeft(ServerLevel l,CompoundTag site){
  int left=0;
  for(var raw:site.getList("seam",Tag.TAG_LONG)){var pos=BlockPos.of(((LongTag)raw).getAsLong());
   if(!l.hasChunkAt(pos))continue;
   var state=l.getBlockState(pos);
   if(state.is(BlockTags.COAL_ORES)||state.is(BlockTags.IRON_ORES)||state.is(BlockTags.COPPER_ORES)||state.is(BlockTags.GOLD_ORES)
    ||state.is(BlockTags.REDSTONE_ORES)||state.is(BlockTags.LAPIS_ORES))left++;}
  return left;
 }
 public static BlockPos origin(CompoundTag site){return BlockPos.of(site.getLong("pos"));}
 /** The people this site put into the world: a captive, the survivors of an expedition. */
 public static List<UUID> people(CompoundTag site){
  var out=new ArrayList<UUID>();for(var raw:site.getList("people",Tag.TAG_INT_ARRAY))out.add(NbtUtils.loadUUID(raw));return out;
 }
}
