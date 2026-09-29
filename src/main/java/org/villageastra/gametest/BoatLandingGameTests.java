package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BoatLandingGameTests {
 @GameTest(template="empty",batch="boat_landing",timeoutTicks=100) public static void dryLandingCannotMeanAirAboveWater(GameTestHelper h){
  var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,5,2));
  for(int x=0;x<=29;x++)for(int z=0;z<=12;z++){
   l.setBlock(origin.offset(x,-3,z),Blocks.STONE.defaultBlockState(),2);
   for(int y=-2;y<=2;y++)l.setBlock(origin.offset(x,y,z),y<0?(x>=25?Blocks.STONE:Blocks.WATER).defaultBlockState():Blocks.AIR.defaultBlockState(),2);
  }
  var boat=new Boat(l,origin.getX()+21.3125,origin.getY()-.5,origin.getZ()+6.5);l.addFreshEntity(boat);
  var r=VillageAstra.RESIDENT.get().create(l);r.setNoAi(true);r.moveTo(boat.position());l.addFreshEntity(r);r.startRiding(boat,true);
  h.runAfterDelay(5,()->{try{
   boolean landed=EscortGoal.dryDismount(r,boat,Vec3.atBottomCenterOf(origin.offset(27,0,6)));
   var foot=r.blockPosition();var floor=BlockPos.containing(r.position().add(0,-.05,0));
   h.assertTrue(!landed||l.getFluidState(foot).isEmpty()&&l.getFluidState(floor).isEmpty()&&!l.getBlockState(floor).getCollisionShape(l,floor).isEmpty(),"Dry dismount must have solid ground, not air above the lake: "+r.position()+" floor="+l.getBlockState(floor));
  }finally{r.discard();boat.discard();}h.succeed();});
 }
}
