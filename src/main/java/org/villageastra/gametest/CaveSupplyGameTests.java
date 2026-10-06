package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.persistence.NbtRecord;
import org.villageastra.server.*;
import org.villageastra.world.*;

/** A real loaned pick, descent into a dry cave, exposed wall ore and physical return. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class CaveSupplyGameTests {
 @GameTest(template="empty",batch="cave_reach",timeoutTicks=200)
 public static void lowPlatformStillRejectsOreBeyondNormalEyeReach(GameTestHelper h){
  var l=h.getLevel();var target=h.absolutePos(new BlockPos(6,9,6));var feet=target.west().below(4);
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-5;y<=3;y++)l.setBlock(target.offset(x,y,z),y==-5?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(target,Blocks.IRON_ORE.defaultBlockState(),2);
  h.assertTrue(HarvestAccess.standing(l,feet,target),"Four cells below the ore still leaves the adult eye within four blocks");
  h.assertTrue(!HarvestAccess.standing(l,feet,target.above()),"An additional block exceeds unchanged tool reach");
  l.setBlock(feet.below(),Blocks.AIR.defaultBlockState(),2);h.assertTrue(!HarvestAccess.standing(l,feet,target),"Height never substitutes for an actual stable floor");h.succeed();
 }
 @GameTest(template="empty",batch="cave_supply",timeoutTicks=200)
 public static void coveredOreKeepsFluidAndFallingCeilingGuards(GameTestHelper h){
  var l=h.getLevel();var target=h.absolutePos(new BlockPos(6,7,6));
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=3;y++)l.setBlock(target.offset(x,y,z),y==-2?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(target,Blocks.IRON_ORE.defaultBlockState(),2);l.setBlock(target.above(),Blocks.DIORITE.defaultBlockState(),2);
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(target.getX()-1.5,target.getY()-1,target.getZ()+.5);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   h.assertTrue(SurfaceQuarry.safe(l,target)&&HarvestAccess.find(npc,target)!=null,"A covered ore wall has a genuine reachable stand one block below it");
   l.setBlock(target.east(),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!SurfaceQuarry.safe(l,target),"An adjacent fluid forbids the cave face");l.setBlock(target.east(),Blocks.AIR.defaultBlockState(),2);
   l.setBlock(target.above(),Blocks.GRAVEL.defaultBlockState(),2);h.assertTrue(!SurfaceQuarry.safe(l,target),"A falling ceiling is not opened by cave harvesting");
  }finally{npc.discard();}h.succeed();
 }
 @GameTest(template="empty",batch="cave_supply",timeoutTicks=12000)
 public static void exhaustedMinerHarvestsAnAccessibleCoveredOreFaceAndReturns(GameTestHelper h){
  trip(h,false);
 }
 @GameTest(template="empty",batch="cave_high_face",timeoutTicks=12000)
 public static void highCoveredOreUsesTheExistingLowerWorkFloorAndReturns(GameTestHelper h){trip(h,true);}
 @GameTest(template="empty",batch="ore_bulk",timeoutTicks=12000)
 public static void exposedVeinSharesOneToolAndOneReturnAcrossReload(GameTestHelper h){trip(h,true,3,false);}
 @GameTest(template="empty",batch="ore_bulk_break",timeoutTicks=12000)
 public static void lastDurabilityEndsTheVeinTripWithoutAnExtraOreOrTool(GameTestHelper h){trip(h,true,3,true);}
 @GameTest(template="empty",batch="quarry_face",timeoutTicks=12000)
 public static void minerPaysForTheBlockingFaceBeforeExtractingItsOrderedOre(GameTestHelper h){trip(h,true,1,false,true);}
 @GameTest(template="empty",batch="deep_building_stone",timeoutTicks=12000)
 public static void exhaustedMinerFindsDeepBuildingStoneWithoutAnOreOrderAndReturns(GameTestHelper h){trip(h,false,1,false,false,true);}
 @GameTest(template="empty",batch="building_stone_face",timeoutTicks=12000)
 public static void minerOpensOnePaidRockBeforeOrderedBuildingStone(GameTestHelper h){trip(h,true,1,false,true,true);}
 @GameTest(template="empty",batch="building_stone_bulk",timeoutTicks=12000)
 public static void exposedBuildingStoneSharesPaidToolAndPhysicalReturnAcrossGoalReload(GameTestHelper h){trip(h,true,3,false,false,true);}
 @GameTest(template="empty",batch="building_stone_bulk_break",timeoutTicks=12000)
 public static void lastDurabilityLeavesRemainingBuildingStoneUnmined(GameTestHelper h){trip(h,true,3,true,false,true);}
 private static void trip(GameTestHelper h,boolean high){
  trip(h,high,1,false);
 }
 private static void trip(GameTestHelper h,boolean high,int ores,boolean breaking){
  trip(h,high,ores,breaking,false);
 }
 private static void trip(GameTestHelper h,boolean high,int ores,boolean breaking,boolean face){
  trip(h,high,ores,breaking,face,false);
 }
 private static void trip(GameTestHelper h,boolean high,int ores,boolean breaking,boolean face,boolean buildingStone){
  var product=buildingStone?Items.ANDESITE:Items.RAW_IRON;
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(face?327680:ores>1?(breaking?262144:245760):(high?180224:114688)),120,at.getZ());
  var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-2)>>4;x<=(base.getX()+85)>>4;x++)for(int z=(base.getZ()-2)>>4;z<=(base.getZ()+4)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-2;x<=85;x++)for(int z=-2;z<=4;z++){
   int floor=120-Math.min(37,Math.max(0,(x-4)/2));
   for(int y=80;y<=140;y++)l.setBlock(new BlockPos(base.getX()+x,y,base.getZ()+z),y==floor?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  for(int x=76;x<=85;x++)for(int z=-2;z<=4;z++)l.setBlock(new BlockPos(base.getX()+x,95,base.getZ()+z),Blocks.STONE.defaultBlockState(),2);
  var target=base.offset(80,high?-33:-35,2);for(int i=0;i<ores;i++)l.setBlock(target.south(i),(buildingStone?Blocks.ANDESITE:Blocks.IRON_ORE).defaultBlockState(),2);
  if(face)for(var d:net.minecraft.core.Direction.values())l.setBlock(target.relative(d),Blocks.STONE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);
  var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);var loan=new ItemStack(Items.STONE_PICKAXE);if(breaking)loan.setDamageValue(loan.getMaxDamage()-1);chest.setItem(0,loan);
  var work=MineWork.read(l,mine);int limit=MineWork.floorStep(l,e,mine,work);s.noteMine(mine.id(),limit,3,5,7);
  work.putInt("step",limit+1);work.putInt("side",MineDrive.DONE);work.putInt("floorStep",limit);work.putString("stage","choose");work.putString("status","mine_floor");work.put("tool",new ItemStack(Items.STONE_PICKAXE).save(new CompoundTag()));
  work.putIntArray("surveyedFloors",java.util.stream.IntStream.rangeClosed(0,limit).toArray());MineWork.write(l,mine,work);
  var project=new CompoundTag();var job=UUID.randomUUID();project.putUUID("id",job);project.putUUID("project",job);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(40,0,20).asLong());var cost=new CompoundTag();cost.putInt(buildingStone?"minecraft:andesite":"minecraft:raw_iron",ores);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+2.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);var supply=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(1,supply);l.addFreshEntity(npc);
  // Resume the real bounded survey at this column, not a pre-created harvest job.
  int cursor=0;for(int x=-192;x<=192;x++)for(int z=-192;z<=192;z++){int d=x*x+z*z;if(d<6404||d==6404&&(x<80||x==80&&z<2))cursor++;}
  var scan=new CompoundTag();scan.putInt("surveyCursor",cursor);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),scan);
  if(buildingStone){
   h.assertTrue(l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,target.getX(),target.getZ())-target.getY()>8,"Building stone is deeper than the former surface window");
   h.assertTrue(SurfaceQuarry.safe(l,target),"Deep stone remains outside protection and fluid bounds");
   if(face){
    h.assertTrue(HarvestAccess.find(npc,target,320)==null&&HarvestAccess.find(npc,target.west(),320)!=null,"The stone is hidden behind one genuinely reachable safe rock face");
    var bound=new CompoundTag();bound.putLong("oreTarget",target.asLong());bound.putInt("faceDepth",QuarryFace.MAX_OBSTRUCTIONS);
    h.assertTrue(QuarryFace.next(npc,bound,Set.of())==null&&QuarryFace.find(npc,target,Set.of(target))==null,"The finite face budget and another worker's reservation still forbid excavation");
    for(var hazard:List.of(Blocks.WATER,Blocks.LAVA)){
     l.setBlock(target.east(),hazard.defaultBlockState(),2);h.assertTrue(QuarryFace.find(npc,target,Set.of())==null,"Building stone never opens a fluid boundary");l.setBlock(target.east(),Blocks.STONE.defaultBlockState(),2);
    }
    l.setBlock(target.above(),Blocks.GRAVEL.defaultBlockState(),2);h.assertTrue(QuarryFace.find(npc,target,Set.of())==null,"Building stone never releases a falling ceiling");l.setBlock(target.above(),Blocks.STONE.defaultBlockState(),2);
    var protectedSettlement=new Settlement(UUID.randomUUID());protectedSettlement.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));SettlementData.get(l.getServer()).add(new SettlementData.Entry(protectedSettlement,l.dimension().location().toString(),target));
    try{h.assertTrue(OwnershipEvents.protectedBlock(l,target)&&QuarryFace.find(npc,target,Set.of())==null,"A building plot cannot become a quarry face");}finally{SettlementData.get(l.getServer()).remove(protectedSettlement.id());}
   }
   else h.assertTrue(HarvestAccess.find(npc,target,320)!=null,"Deep stone has a real dry reversible native route");
   h.runAtTickTime(240,()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).hasUUID("id"),"A miner must find deep demanded building stone even without an ore demand"));
  }
  var reloaded=new boolean[]{false};var jobs=new HashSet<UUID>();var running=new NaturalSupplyGoal[]{supply};int expected=breaking?1:ores;
  if(ores>1)h.onEachTick(()->{
   var current=NaturalSupplyGoal.inspect(l,npc.getUUID());if(!current.hasUUID("id"))return;jobs.add(current.getUUID("id"));
   var bag=current.getList("bag",Tag.TAG_COMPOUND);if(!breaking&&!reloaded[0]&&bag.size()==2){
    var cargo=NaturalSupplyGoal.cargo(l,current);int iron=0,picks=0,damage=-1;for(var raw:cargo){var st=ItemStack.of((CompoundTag)raw);if(st.is(product))iron+=st.getCount();if(st.is(Items.STONE_PICKAXE)){picks+=st.getCount();damage=st.getDamageValue();}}
    h.assertTrue(iron==2&&picks==1&&damage==2,"Interrupted third block retains only two paid ore and the same twice-used tool");
    npc.goalSelector.removeGoal(running[0]);running[0]=new NaturalSupplyGoal(npc,true);npc.goalSelector.addGoal(1,running[0]);reloaded[0]=true;
   }
   if(current.getString("stage").equals("carry"))h.assertTrue(current.getList("cargo",Tag.TAG_COMPOUND).stream().mapToInt(raw->{var st=ItemStack.of((CompoundTag)raw);return st.is(product)?st.getCount():0;}).sum()==expected,"One return carries the paid vein; no return after each block: pos="+npc.position()+" bodyTicks="+npc.tickCount+" state="+current+" path="+(npc.getNavigation().getPath()==null?null:npc.getNavigation().getPath().getTarget()));
  });
  h.succeedWhen(()->{
   h.assertTrue(chest.countItem(product)==expected&&l.getBlockState(target).isAir(),"The covered wall ore must be mined and physically delivered: "+npc.position()+" ticks="+npc.tickCount);
   int returnedDamage=-1;for(int slot=0;slot<chest.getContainerSize();slot++)if(chest.getItem(slot).is(Items.STONE_PICKAXE))returnedDamage=chest.getItem(slot).getDamageValue();
   h.assertTrue(chest.countItem(Items.STONE_PICKAXE)==(breaking?0:1)&&(breaking||returnedDamage==(face?expected+1:expected)),"The same borrowed pick pays each actual block: count="+chest.countItem(Items.STONE_PICKAXE)+" damage="+returnedDamage);
   if(face)h.assertTrue(chest.countItem(Items.COBBLESTONE)==1&&npc.tickCount>=400,"One real obstruction was excavated, worked and physically delivered with the ore");
   if(ores>1){h.assertTrue(breaking||reloaded[0]&&jobs.size()==ores&&npc.tickCount>=200*ores,"Separate paid blocks survive goal reload");for(int i=1;i<ores;i++)h.assertTrue(l.getBlockState(target.south(i)).is(breaking?(buildingStone?Blocks.ANDESITE:Blocks.IRON_ORE):Blocks.AIR),"A broken pick cannot mine another block");}
   h.assertTrue(npc.getHealth()==npc.getMaxHealth()&&l.getBlockState(target.below(high?4:2)).is(Blocks.STONE)&&l.getBlockState(new BlockPos(target.getX(),95,target.getZ())).is(Blocks.STONE),"Body returns safely; working floor and cave roof stay intact");
   if(buildingStone&&ores>1)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_BUILDING_STONE_BULK VERIFIED bodyTicks={} andesite={} returnedWear={} reloaded={}",npc.tickCount,chest.countItem(product),returnedDamage,reloaded[0]);
   if(buildingStone&&face)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_BUILDING_STONE_FACE VERIFIED bodyTicks={} andesite={} cobblestone={} returnedWear={}",npc.tickCount,chest.countItem(product),chest.countItem(Items.COBBLESTONE),returnedDamage);
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
