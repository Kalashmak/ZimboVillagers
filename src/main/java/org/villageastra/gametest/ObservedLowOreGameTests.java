package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
import org.villageastra.domain.*;
import org.villageastra.persistence.*;
import org.villageastra.server.*;

/** Copied terrain, new body and prepared paid job; not an exact saved-entity replay. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ObservedLowOreGameTests {
 @GameTest(template="empty",batch="observed_low_ore",timeoutTicks=1200)
 public static void minerPhysicallyCentersBeforeWorkingDownwardPastItsSupportingLedge(GameTestHelper h){
  trip(h,false);
 }
 @GameTest(template="empty",batch="observed_corner_ore",timeoutTicks=1200)
 public static void minerFindsAnActualEyeRayAcrossTheObservedDiagonalCorner(GameTestHelper h){trip(h,true);}
 private static void trip(GameTestHelper h,boolean corner){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=corner?new BlockPos(-2363,60,-2506):new BlockPos(at.getX()+458752,100,at.getZ());var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-7)>>4;x<=(base.getX()+7)>>4;x++)for(int z=(base.getZ()-7)>>4;z<=(base.getZ()+7)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  // The exact negative coordinates are required for the corner ray, but an
  // unrelated randomly generated ocean must not flood the copied dry scene.
  // This shell is outside every copied cell and changes no working geometry.
  if(corner)for(var p:BlockPos.betweenClosed(base.offset(-8,-5,-8),base.offset(8,7,8))){
   var q=p.subtract(base);if(Math.abs(q.getX())==8||Math.abs(q.getZ())==8||q.getY()==-5||q.getY()==7)l.setBlock(p,Blocks.STONE.defaultBlockState(),2);
  }
  for(var p:BlockPos.betweenClosed(base.offset(-7,-4,-7),base.offset(7,6,7)))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
  try(var in=ObservedLowOreGameTests.class.getResourceAsStream("/data/villageastra/fixtures/"+(corner?"fresh3_corner_coal_20261006.json":"fresh3_low_iron_20261006.json"))){
   for(var raw:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray()){
    var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);
   }
  }catch(Exception ex){throw new IllegalStateException(ex);}
  var ore=base.offset(corner?2:1,corner?1:-2,corner?-2:0);var output=corner?Items.COAL:Items.RAW_IRON;var support=l.getBlockState(base.below());var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base.offset(-30,0,0));
  var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var mine=new Settlement.Building(UUID.randomUUID(),"mine",20,0,0);s.addBuilding(mine);
  var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,8,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.setItem(0,new ItemStack(Items.STONE_PICKAXE));
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);s.assign(person.id(),Profession.MINER,mine.id());npc.bind(s.id(),s.resident(person.id()));
  npc.moveTo(base.getX()+(corner?.5080884079257:.2),base.getY(),base.getZ()+(corner?.499879196691:.5));npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);l.addFreshEntity(npc);
  h.assertTrue(l.getBlockState(ore).is(corner?Blocks.COAL_ORE:Blocks.IRON_ORE)&&SurfaceQuarry.safe(l,ore)&&HarvestAccess.standing(l,base,ore),"Actual saved ore and platform pass unchanged safety checks");
  com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_LOW_ORE INITIAL corner={} base={} target={} pos={} plannedVisible={} actualVisible={}",corner,base,ore,npc.position(),HarvestAccess.visible(npc,base,ore),HarvestAccess.visible(npc,ore));
  h.assertTrue(HarvestAccess.visible(npc,base,ore)&&!HarvestAccess.visible(npc,ore),"The centered ray clears the ledge while a real body within the old stopping margin cannot see ore");
  var job=UUID.randomUUID();var pick=WorldJournal.takeAmount(l,Settlement.childId(job,"tool"),stock,0,chest.getItem(0).copy(),1);h.assertTrue(!pick.isEmpty(),"One real journalled pick loan");
  var t=new CompoundTag();t.putUUID("id",job);t.putBoolean("quarry",true);t.putLong("target",ore.asLong());t.putLong("stand",base.asLong());t.put("before",NbtUtils.writeBlockState(l.getBlockState(ore)));t.putString("stage","dig");NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),t);
  npc.goalSelector.addGoal(1,new NaturalSupplyGoal(npc,true));
  h.onEachTick(()->{if(npc.tickCount%100==0)com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_LOW_ORE TRACE ticks={} pos={} speed={} ground={} collision={} visible={} motion={} control={} state={}",npc.tickCount,npc.position(),npc.getSpeed(),npc.onGround(),npc.horizontalCollision,HarvestAccess.visible(npc,ore),npc.getDeltaMovement(),npc.getMoveControl().hasWanted(),NaturalSupplyGoal.inspect(l,npc.getUUID()));});
  h.succeedWhen(()->{
   var receipt=WorldJournal.recoverExisting(l,job);h.assertTrue(receipt!=null&&l.getBlockState(ore).isAir(),"The body must physically finish its approach and harvest the real ore");
   var current=NaturalSupplyGoal.inspect(l,npc.getUUID());int iron=0,picks=0,damage=-1;for(var raw:NaturalSupplyGoal.cargo(l,current)){var item=ItemStack.of((CompoundTag)raw);if(item.is(output))iron+=item.getCount();if(item.is(Items.STONE_PICKAXE)){picks+=item.getCount();damage=item.getDamageValue();}}
   h.assertTrue(iron==1&&picks==1&&damage==1&&npc.tickCount>=200,"Ordinary labor, one ore and exactly one wear of the loaned tool");
   h.assertTrue(npc.getHealth()==npc.getMaxHealth()&&l.getBlockState(base.below()).equals(support),"The supporting ledge remains intact and body survives");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_LOW_ORE VERIFIED ticks={} pos={} labor={}",npc.tickCount,npc.position(),current.getInt("labor"));
   npc.discard();SettlementData.get(l.getServer()).remove(s.id());for(var cp:held)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
