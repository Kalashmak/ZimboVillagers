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

/** A real dry bank trip; the worker never swims or removes its support. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ClayBankGameTests {
 @GameTest(template="empty",batch="clay_bank",timeoutTicks=200)
 public static void clayBankKeepsDepthFloodSupportLavaRootsAndOwnershipBounds(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(6,4,6));
  for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-1;y<=4;y++)l.setBlock(p.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  l.setBlock(p,Blocks.CLAY.defaultBlockState(),2);l.setBlock(p.above(),Blocks.WATER.defaultBlockState(),2);
  h.assertTrue(ClayBankHarvest.shallow(l,p)&&NaturalSupplyGoal.safe(l,p),"One-water-layer clay can be surveyed from a safe bank");
  var low=p.east(2).above();h.assertTrue(l.getFluidState(low).isEmpty()&&!HarvestAccess.standing(l,low,p),"A currently dry platform at the possible flood height is rejected");
  var high=p.east().above(2);l.setBlock(high.below(),Blocks.STONE.defaultBlockState(),2);
  h.assertTrue(HarvestAccess.standing(l,high,p),"Higher independent supported bank remains usable");
  l.setBlock(high.below(),Blocks.AIR.defaultBlockState(),2);h.assertTrue(!HarvestAccess.standing(l,high,p),"Height never replaces bank support");l.setBlock(high.below(),Blocks.STONE.defaultBlockState(),2);
  l.setBlock(p.above(2),Blocks.WATER.defaultBlockState(),2);h.assertTrue(!ClayBankHarvest.shallow(l,p)&&!NaturalSupplyGoal.safe(l,p),"Deeper underwater clay remains unavailable");l.setBlock(p.above(2),Blocks.AIR.defaultBlockState(),2);
  l.setBlock(p.west(),Blocks.LAVA.defaultBlockState(),2);h.assertTrue(!NaturalSupplyGoal.safe(l,p),"Lava next to clay remains forbidden");l.setBlock(p.west(),Blocks.AIR.defaultBlockState(),2);
  for(var material:List.of(Blocks.SAND,Blocks.GRAVEL)){l.setBlock(p,material.defaultBlockState(),2);h.assertTrue(!NaturalSupplyGoal.safe(l,p),"Wet sand/gravel are not enabled by the clay policy");}
  l.setBlock(p,Blocks.IRON_ORE.defaultBlockState(),2);h.assertTrue(!SurfaceQuarry.safe(l,p),"Underwater ore remains forbidden");l.setBlock(p,Blocks.CLAY.defaultBlockState(),2);
  l.setBlock(p.above(),Blocks.GRAVEL.defaultBlockState(),2);h.assertTrue(!NaturalSupplyGoal.safe(l,p),"A falling ceiling is not released");
  l.setBlock(p.above(),Blocks.SUGAR_CANE.defaultBlockState(),2);h.assertTrue(!NaturalSupplyGoal.safe(l,p),"Growing roots are not uprooted");l.setBlock(p.above(),Blocks.WATER.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0));var e=new SettlementData.Entry(s,l.dimension().location().toString(),p);SettlementData.get(l.getServer()).add(e);
  try{h.assertTrue(OwnershipEvents.protectedBlock(l,p)&&!NaturalSupplyGoal.safe(l,p),"Building ownership remains authoritative for bank clay");}finally{SettlementData.get(l.getServer()).remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",batch="clay_bank",timeoutTicks=4000)
 public static void shallowClayIsDugFromDryBankAndPhysicallyReturned(GameTestHelper h){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+589824,120,at.getZ());var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-4)>>4;x<=(base.getX()+43)>>4;x++)for(int z=(base.getZ()-5)>>4;z<=(base.getZ()+7)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
  }
  for(int x=-4;x<=43;x++)for(int z=-5;z<=7;z++)for(int y=-4;y<=8;y++)l.setBlock(base.offset(x,y,z),y<=0?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  var target=base.offset(35,0,0);var stand=target.south().above(2);
  l.setBlock(target,Blocks.CLAY.defaultBlockState(),2);l.setBlock(target.above(),Blocks.WATER.defaultBlockState(),2);
  for(int x=32;x<=38;x++)for(int z=-2;z<=2;z++)if(x!=35||z!=0)l.setBlock(base.offset(x,1,z),Blocks.STONE.defaultBlockState(),2);
  var s=new Settlement(UUID.randomUUID());var e=new SettlementData.Entry(s,l.dimension().location().toString(),base);var hall=new Settlement.Building(UUID.randomUUID(),"town_hall",0,0,0);s.addBuilding(hall);var home=UUID.randomUUID();s.addHome(new Settlement.Home(home,1,2,true));SettlementData.get(l.getServer()).add(e);
  var stock=LogisticsRoutes.position(e,hall);l.setBlock(stock,VillageAstra.OWNED_CHEST.get().defaultBlockState(),2);var chest=LogisticsRoutes.chest(l,e,hall);chest.clearContent();
  var project=new CompoundTag();var id=UUID.randomUUID();project.putUUID("id",id);project.putUUID("project",id);project.putString("kind","building");project.putString("design","home");project.putLong("origin",base.offset(70,0,20).asLong());var cost=new CompoundTag();cost.putInt("minecraft:clay_ball",4);project.put("cost",cost);project.put("cargo",new ListTag());project.put("ops",new ListTag());HallUpgradeGoal.store(l,s.id(),project);
  var npc=VillageAstra.RESIDENT.get().create(l);var person=new Resident(npc.getUUID(),Resident.Life.ADULT,true,null,null,-1);s.admit(person,home);npc.bind(s.id(),s.resident(person.id()));npc.moveTo(base.getX()+2.5,121,base.getZ()+4.5);npc.setOnGround(true);npc.goalSelector.removeAllGoals(g->true);npc.targetSelector.removeAllGoals(g->true);npc.goalSelector.addGoal(1,new NaturalSupplyGoal(npc,true));l.addFreshEntity(npc);
  int cursor=0;for(int x=-192;x<=192;x++)for(int z=-192;z<=192;z++){int d=x*x+z*z;if(d<1225||d==1225&&(x<35||x==35&&z<0))cursor++;}
  var scan=new CompoundTag();scan.putInt("surveyCursor",cursor);NbtRecord.write(NaturalSupplyGoal.path(l,npc.getUUID()),scan);
  h.assertTrue(HarvestAccess.standing(l,stand,target)&&HarvestAccess.visible(npc,stand,target)&&HarvestAccess.find(npc,target,320)!=null,"Shallow clay has an actual dry supported visible reversible native route");
  h.runAtTickTime(240,()->h.assertTrue(NaturalSupplyGoal.inspect(l,npc.getUUID()).hasUUID("id"),"The dry bank must permit a normal demanded clay trip: ticks="+npc.tickCount+" state="+NaturalSupplyGoal.inspect(l,npc.getUUID())+" position="+npc.position()+" ticking="+l.isPositionEntityTicking(npc.blockPosition())));
  h.onEachTick(()->{
   h.assertTrue(!npc.isInWater()&&!npc.isUnderWater()&&npc.getHealth()==npc.getMaxHealth(),"The gathering body never enters water or loses health");
   var t=NaturalSupplyGoal.inspect(l,npc.getUUID());
   if(t.getInt("labor")>0&&!t.getBoolean("complete")&&t.getString("stage").equals("dig"))h.assertTrue(npc.getY()>target.getY()+1&&HarvestAccess.visible(npc,target),"Actual labor occurs above the water level with line of sight");
  });
  h.succeedWhen(()->{
   var t=NaturalSupplyGoal.inspect(l,npc.getUUID());h.assertTrue(t.getBoolean("complete")&&chest.countItem(Items.CLAY_BALL)==4,"Four real clay balls must reach the actual hall chest");
   h.assertTrue(t.getInt("labor")>=200&&l.getBlockState(target.above()).is(Blocks.WATER)&&l.getBlockState(stand.below()).is(Blocks.STONE),"Normal labor, source water and independent bank support remain");
   com.mojang.logging.LogUtils.getLogger().info("ZIMBOVILLAGERS_CLAY_BANK VERIFIED bodyTicks={} labor={} clay={} pos={}",npc.tickCount,t.getInt("labor"),chest.countItem(Items.CLAY_BALL),npc.position());
   npc.discard();HallUpgradeGoal.drop(l,s.id());SettlementData.get(l.getServer()).remove(s.id());for(var cp:forced)l.setChunkForced(cp.x,cp.z,false);h.succeed();
  });
 }
}
