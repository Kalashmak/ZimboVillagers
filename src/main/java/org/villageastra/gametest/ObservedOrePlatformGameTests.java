package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;

/** Copied ore pocket, with a new real body on the previously used work platform. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class ObservedOrePlatformGameTests {
 @GameTest(template="empty",batch="observed_ore_platform",timeoutTicks=200)
 public static void savedUpperIronFaceHasAVisibleReversiblePlatform(GameTestHelper h){
  check(h,false);
 }
 @GameTest(template="empty",batch="observed_diagonal_ore",timeoutTicks=200)
 public static void savedSideIronUsesAPlatformWithinUnchangedEyeReach(GameTestHelper h){check(h,true);}
 private static void check(GameTestHelper h,boolean diagonal){
  var l=h.getLevel();var at=h.absolutePos(BlockPos.ZERO);var base=new BlockPos(at.getX()+(diagonal?278528:229376),100,at.getZ());
  var held=new ArrayList<net.minecraft.world.level.ChunkPos>();
  for(int x=(base.getX()-7)>>4;x<=(base.getX()+7)>>4;x++)for(int z=(base.getZ()-7)>>4;z<=(base.getZ()+7)>>4;z++){
   var cp=new net.minecraft.world.level.ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);held.add(cp);}l.getChunk(x,z);
  }
  for(var p:BlockPos.betweenClosed(base.offset(-7,-3,-7),base.offset(7,9,7)))l.setBlock(p,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
  try(var in=ObservedOrePlatformGameTests.class.getResourceAsStream("/data/villageastra/fixtures/"+(diagonal?"fresh3_diagonal_iron_20261006.json":"fresh3_high_iron_20261006.json"))){
   var rows=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
   for(var raw:rows){var c=raw.getAsJsonArray();var state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),c.get(3).getAsString(),false).blockState();l.setBlock(base.offset(c.get(0).getAsInt(),c.get(1).getAsInt(),c.get(2).getAsInt()),state,2);}
  }catch(Exception ex){throw new IllegalStateException(ex);}
  var npc=VillageAstra.RESIDENT.get().create(l);npc.moveTo(base.getX()+.5,base.getY(),base.getZ()+.5);npc.setOnGround(true);l.addFreshEntity(npc);
  try{
   var ore=base.offset(diagonal?-2:-1,diagonal?4:3,diagonal?-1:0);
   h.assertTrue(l.getBlockState(ore).is(net.minecraft.world.level.block.Blocks.IRON_ORE),"Copied target remains actual iron ore");
   h.assertTrue(SurfaceQuarry.safe(l,ore),"Copied ore passes unchanged fluid and falling-roof guards");
   var stand=HarvestAccess.find(npc,ore,NaturalSupplyGoal.ROUTE_RANGE);
   var alternatives=new ArrayList<BlockPos>();var diagnostic=new ArrayList<String>();
   if(diagonal)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(x*x+z*z<=9&&(x!=0||z!=0))for(int y=-4;y<=2;y++){
    var feet=ore.offset(x,y,z);if(!HarvestAccess.standing(l,feet,ore))continue;
    var eye=net.minecraft.world.phys.Vec3.atBottomCenterOf(feet).add(0,npc.getEyeHeight(),0);
    var hit=l.clip(new net.minecraft.world.level.ClipContext(eye,net.minecraft.world.phys.Vec3.atCenterOf(ore),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,npc));
    boolean visible=hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(ore);boolean reachable=HarvestAccess.survivesExtraction(npc.routeTo(feet,0,NaturalSupplyGoal.ROUTE_RANGE),ore);
    diagnostic.add(feet.subtract(base)+":hit="+(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getBlockPos().subtract(base):hit.getType())+" reachable="+reachable);
    if(visible&&reachable)alternatives.add(feet.subtract(base));
   }
   if(diagonal){
    h.assertTrue(stand==null&&alternatives.isEmpty(),"The copied buried ore still rejects a direct harvest; platforms="+diagnostic);
    var plan=QuarryFace.find(npc,ore,Set.of());h.assertTrue(plan!=null,"A real exposed blocking face is reachable without inventing direct access to the ore");
    var limit=new net.minecraft.nbt.CompoundTag();limit.putLong("oreTarget",ore.asLong());limit.putInt("faceDepth",QuarryFace.MAX_OBSTRUCTIONS);h.assertTrue(QuarryFace.next(npc,limit,Set.of())==null,"Blocked ore cannot extend beyond the finite working-face budget");
    int removed=0;var pick=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE);
    while(plan!=null&&!plan.target().equals(ore)&&removed<QuarryFace.MAX_OBSTRUCTIONS){
     h.assertTrue(l.getBlockState(plan.target()).is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)&&HarvestAccess.standing(l,plan.stand(),plan.target())&&HarvestAccess.visible(npc,plan.stand(),plan.target()),"Only an actual reachable exposed stone face is excavated");
     var receipt=org.villageastra.persistence.WorldJournal.harvest(l,UUID.randomUUID(),plan.target(),l.getBlockState(plan.target()),pick);h.assertTrue(receipt!=null,"Stone is removed through the ordinary world journal");removed++;pick.setDamageValue(removed);limit.putInt("faceDepth",removed);plan=QuarryFace.next(npc,limit,Set.of());
    }
    h.assertTrue(plan!=null&&plan.target().equals(ore)&&removed>0&&removed<=QuarryFace.MAX_OBSTRUCTIONS,"At most three actual stone removals reveal a genuine ore ray and reversible platform");
    stand=plan.stand();
   }else h.assertTrue(stand!=null,"Saved ore has a genuine eye ray and a reversible route from its used platform");
   h.assertTrue(HarvestAccess.standing(l,stand,ore)&&HarvestAccess.visible(npc,stand,ore),"Chosen platform retains support and unchanged eye reach");
  }finally{npc.discard();for(var cp:held)l.setChunkForced(cp.x,cp.z,false);}h.succeed();
 }
}
