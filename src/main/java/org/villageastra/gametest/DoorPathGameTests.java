package org.villageastra.gametest;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.world.*;
/** A path node may already be beyond a shut doorway while the resident has not physically crossed it. */
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class DoorPathGameTests {
 @GameTest(template="empty",batch="door_path",timeoutTicks=100) public static void residentOpensDoorBehindNextPathNode(GameTestHelper h){check(h,false);}
 @GameTest(template="empty",batch="door_path",timeoutTicks=100) public static void residentDoesNotOpenIronDoorBehindNextPathNode(GameTestHelper h){check(h,true);}
 @GameTest(template="empty",batch="door_partial_path",timeoutTicks=100) public static void residentOpensDoorAtEndOfPartialPath(GameTestHelper h){check(h,false,true);}
 private static void check(GameTestHelper h,boolean iron){
  check(h,iron,false);
 }
 private static void check(GameTestHelper h,boolean iron,boolean partial){
  var l=h.getLevel();var at=h.absolutePos(new BlockPos(4,3,4));
  for(int x=-2;x<=2;x++)for(int z=-2;z<=5;z++){l.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<=2;y++)l.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var block=(DoorBlock)(iron?Blocks.IRON_DOOR:Blocks.OAK_DOOR);var door=block.defaultBlockState().setValue(DoorBlock.FACING,Direction.SOUTH);
  l.setBlock(at,door.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);l.setBlock(at.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);
  var r=VillageAstra.RESIDENT.get().create(l);r.setNoAi(true);r.moveTo(at.getX()+.5,at.getY(),at.getZ()-.5,0,0);l.addFreshEntity(r);
  try{var nodes=List.of(new Node(at.getX(),at.getY(),at.getZ()+1),new Node(at.getX(),at.getY(),at.getZ()+2),new Node(at.getX(),at.getY(),at.getZ()+4));
   if(partial)nodes=List.of(new Node(at.getX(),at.getY(),at.getZ()-2),new Node(at.getX(),at.getY(),at.getZ()-1));
   var path=new Path(nodes,at.south(4),!partial);h.assertTrue(r.getNavigation().moveTo(path,.8),"Fixture has a route");r.getNavigation().getPath().setNextNodeIndex(1);
   var goal=new ResidentDoorGoal(r);h.assertTrue(goal.canUse()!=iron,"Only a nearby hand-openable door on the remaining path is opened");
   if(!iron){goal.start();h.assertTrue(l.getBlockState(at).getValue(DoorBlock.OPEN),"The real door opens");}
  }finally{r.discard();}h.succeed();
 }
}
